"""Resolves every model, texture and blockstate the pack ships, without starting the game.

Three visual failures shipped in one day and not one of them failed a compile, a test or a
datagen run: a model renamed out from under its item, a fluid with no FluidModel, and a texture
nobody looked at. Datagen reports nothing in any of those cases, because in every case both files
were written exactly as asked - it is the *reference between* them that is wrong.

That is what this checks. It walks the generated assets the way the game's model manager would,
resolving each reference to a file that has to exist:

    blockstate -> model -> parent -> ... -> texture -> a real PNG

and it resolves into the vanilla client jar too, because almost every model in the pack points at
a vanilla texture on purpose (see docs/NEXT.md). Without the jar it still checks everything
first-party and says so; with it, `minecraft:block/bricks` is a file that either exists or does
not.

Two of the rules are here for the multiblock work rather than for anything on disk today, and
they are cheap to carry until it lands. A machine cell may draw geometry that overhangs into its
neighbours, and both ways that goes wrong are silent:

  - **`-16..32`.** `CuboidModelElement` in 26.2 holds MIN_EXTENT = -16 and MAX_EXTENT = 32. A box
    outside that fails to parse and the block is a checkerboard.
  - **explicit `uv` once a box leaves `0..16`.** An absent `uv` is derived from the box position,
    so an overhanging box gets coordinates off the end of its texture and smears. Nothing warns.

Run it as `python tools/check_models.py`; `./gradlew build` runs it too. It reads only files, so
it is safe to run at any time and needs no client.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The subprojects in settings.gradle. The four siblings in ../ are their own repos and their own
# builds; a checker in this repo that failed on their assets would fail a clean clone.
MODS = ['nauvis', 'nauvis_machines', 'nauvis_logistics', 'nauvis_fluids', 'nauvis_power']

# Where datagen writes, and where anything hand-written lives. Both are shipped, so both count.
ASSET_ROOTS = ['src/generated/client/assets', 'src/main/resources/assets']

MIN_EXTENT, MAX_EXTENT = -16.0, 32.0

failures = []
notes = []


def fail(where, message):
    failures.append(f'  {where}: {message}')


def minecraft_version():
    text = (ROOT / 'gradle.properties').read_text(encoding='utf-8')
    match = re.search(r'^minecraft_version=(\S+)', text, re.MULTILINE)
    return match.group(1) if match else None


def vanilla_jar():
    """The client jar NeoForm already downloaded, or None.

    Not an error when it is missing - a fresh clone that has never run the game has no copy, and
    every first-party reference is still worth checking. What is lost is only the answer to "does
    minecraft:block/bricks exist", so the run says which half it did.
    """
    version = minecraft_version()
    artifacts = Path.home() / '.gradle' / 'caches' / 'neoformruntime' / 'artifacts'
    jar = artifacts / f'minecraft_{version}_client.jar'
    return jar if jar.is_file() else None


class Assets:
    """Every asset the game would see, indexed by the identifier that names it."""

    def __init__(self):
        self.files = {}      # 'ns:models/block/x.json' -> Path, for first-party files
        self.vanilla = set()  # 'assets/minecraft/...' entry names in the client jar
        self.jar = None

        for mod in MODS:
            for root in ASSET_ROOTS:
                base = ROOT / mod / root
                if not base.is_dir():
                    continue
                for path in base.rglob('*'):
                    if path.is_file():
                        namespace = path.relative_to(base).parts[0]
                        rest = path.relative_to(base / namespace).as_posix()
                        self.files[f'{namespace}:{rest}'] = path

        jar = vanilla_jar()
        if jar:
            self.jar = zipfile.ZipFile(jar)
            self.vanilla = set(self.jar.namelist())
            notes.append(f'vanilla assets from {jar.name}')
        else:
            notes.append('vanilla assets NOT checked - no client jar downloaded yet')

    def exists(self, namespace, path):
        if f'{namespace}:{path}' in self.files:
            return True
        if namespace == 'minecraft' and not self.jar:
            return None  # unknowable, not absent
        return f'assets/{namespace}/{path}' in self.vanilla

    def read_json(self, namespace, path):
        key = f'{namespace}:{path}'
        if key in self.files:
            return json.loads(self.files[key].read_text(encoding='utf-8'))
        entry = f'assets/{namespace}/{path}'
        if self.jar and entry in self.vanilla:
            return json.loads(self.jar.read(entry).decode('utf-8'))
        return None


def split(identifier):
    """'ns:path' or bare 'path', which means minecraft - the same rule Identifier uses."""
    return tuple(identifier.split(':', 1)) if ':' in identifier else ('minecraft', identifier)


def model_refs(node):
    """Every model identifier reachable in a blockstate or item definition.

    Deliberately structural rather than schema-aware: variants, weighted lists, multipart applies
    and 26.2's item definitions (composite, condition, select, range_dispatch) all end at a
    {"model": "..."} somewhere, and walking for that key survives a format the next version adds.
    """
    found = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key == 'model' and isinstance(value, str):
                found.append(value)
            else:
                found.extend(model_refs(value))
    elif isinstance(node, list):
        for item in node:
            found.extend(model_refs(item))
    return found


def check_geometry(where, model):
    """The two overhang rules. See the module docstring for why each one is silent."""
    for index, element in enumerate(model.get('elements', [])):
        corners = list(element.get('from', [])) + list(element.get('to', []))
        if any(not (MIN_EXTENT <= float(c) <= MAX_EXTENT) for c in corners):
            fail(where, f'element {index} at {corners} leaves the {MIN_EXTENT:g}..{MAX_EXTENT:g} '
                        f'window a model may occupy - it will not parse')
        if any(not (0.0 <= float(c) <= 16.0) for c in corners):
            for direction, face in element.get('faces', {}).items():
                if 'uv' not in face:
                    fail(where, f'element {index} overhangs its block and its {direction} face has '
                                f'no explicit uv - the derived one runs off the texture')


def check_model(assets, identifier, seen, missing, origin):
    """Resolve one model: its parents, then every texture the chain ends up naming.

    A model is inspected once however many things point at it, but a *missing* one is reported
    once per thing pointing at it - the blockstate and the item definition are two separate bugs
    and fixing one does not fix the other. That is exactly the failure this tool exists for.
    """
    namespace, name = split(identifier)
    path = f'models/{name}.json'
    where = f'{namespace}:{name}'

    if where in missing:
        fail(origin, f'names model {where}, which does not exist')
        return {}
    if where in seen:
        return seen[where]
    seen[where] = {}

    model = assets.read_json(namespace, path)
    if model is None:
        if namespace == 'minecraft' and not assets.jar:
            return {}
        missing.add(where)
        fail(origin, f'names model {where}, which does not exist')
        return {}

    textures = {}
    if 'parent' in model:
        textures.update(check_model(assets, model['parent'], seen, missing, where))
    textures.update(model.get('textures', {}))
    seen[where] = textures

    check_geometry(where, model)

    # Only first-party models are held to their textures resolving. A vanilla parent's slots are
    # filled in by whoever inherits it, and half-filled is normal there.
    if namespace != 'minecraft':
        for slot, value in model.get('textures', {}).items():
            if value.startswith('#'):
                if value[1:] not in textures:
                    fail(where, f'texture slot "{slot}" points at {value}, which nothing defines')
                continue
            texture_ns, texture_name = split(value)
            if assets.exists(texture_ns, f'textures/{texture_name}.png') is False:
                fail(where, f'texture slot "{slot}" names {value}, and there is no such PNG')

    return textures


def check_references(assets):
    """Every blockstate and every item definition, followed to the models they name."""
    seen, missing = {}, set()
    for key, path in sorted(assets.files.items()):
        namespace, rest = key.split(':', 1)
        if not (rest.startswith('blockstates/') or rest.startswith('items/')):
            continue
        if not rest.endswith('.json'):
            continue

        origin = f'{namespace}:{rest}'
        content = json.loads(path.read_text(encoding='utf-8'))
        refs = model_refs(content)
        if not refs:
            fail(origin, 'names no model at all')
        for ref in refs:
            check_model(assets, ref, seen, missing, origin)


def check_registrations(assets):
    """Every registered block has a blockstate, and every one of those has an item definition.

    The bug this is for: a block registered and never given a model is not a missing file anyone
    notices, it is a magenta cube in a world.
    """
    for mod in MODS:
        source = ROOT / mod / 'src' / 'main' / 'java'
        if not source.is_dir():
            continue
        for path in source.rglob('ModBlocks.java'):
            text = path.read_text(encoding='utf-8')
            names = re.findall(r'registerBlock\(\s*"([a-z0-9_]+)"', text)
            if not names:
                notes.append(f'{mod}: no block ids found in {path.name} - ids are not literals '
                             f'there, so its blocks are unchecked')
            for name in names:
                for kind in ('blockstates', 'items'):
                    if assets.exists(mod, f'{kind}/{name}.json') is not True:
                        fail(f'{mod}:{name}', f'is registered and has no {kind} file')


def check_fluid_models(assets):
    """Every registered fluid needs a FluidModel, and nothing but the log says otherwise.

    NeoForge prints `Missing FluidModel for fluid` and carries on, so a fluid drawn in a tank is
    the missing texture wherever a tank is shown - including for a fluid never placed in a world.
    """
    for mod in MODS:
        source = ROOT / mod / 'src' / 'main' / 'java'
        if not source.is_dir():
            continue
        registered = {}
        for path in source.rglob('ModFluids.java'):
            text = path.read_text(encoding='utf-8')
            for constant, fluid in re.findall(
                    r'(\w+)\s*=\s*\n?\s*FLUIDS\.register\(\s*"([a-z0-9_]+)"', text):
                registered[constant] = fluid
        if not registered:
            continue

        modelled = ''.join(path.read_text(encoding='utf-8')
                           for path in source.rglob('*.java')
                           if 'RegisterFluidModelsEvent' in path.read_text(encoding='utf-8'))
        for constant, fluid in registered.items():
            if constant not in modelled:
                fail(f'{mod}:{fluid}',
                     'is a registered fluid with no FluidModel - see RegisterFluidModelsEvent')


assets = Assets()
check_references(assets)
check_registrations(assets)
check_fluid_models(assets)

print(f'{len(assets.files)} first-party asset files across {len(MODS)} mods')
for note in notes:
    print(f'  {note}')

if failures:
    print('\nUNRESOLVED:')
    print('\n'.join(sorted(set(failures))))
    sys.exit(1)
print('\neverything resolves')
