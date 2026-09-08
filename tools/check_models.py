"""Resolves every model, texture and blockstate the pack ships, without starting the game.

Datagen reports nothing when the reference *between* two files is wrong, so this walks the
generated assets as the model manager would - blockstate -> model -> parent -> texture -> PNG -
into the vanilla client jar too when it is in the Gradle cache. It also checks: belt speeds
against `data/mapping.json`; stack sizes (`Stacks.of(n)` and `StandInStacks`) against `stack`;
footprints (cells read out of each `*Shape.java` naming a `FACTORIO_ID`) against `size`; boxes
outside `-16..32` (fail to parse) and overhanging boxes without explicit `uv` (smear); each
blockstate variant's `y` against the shape's own cell-plus-facing arithmetic; and that every
texture a first-party model names is opaque unless listed in `TRANSPARENT_TEXTURES_ALLOWED`. The
PNG is read with the standard library, because the build runs this and cannot assume Pillow.

    python tools/check_models.py
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The subprojects in settings.gradle. The three siblings in ../ are their own repos and their own
# builds; a checker in this repo that failed on their assets would fail a clean clone.
#
# `nauvis_mining` was the exception that made this list two lists: the drills lived in a sibling
# repo, and a Factorio entity's footprint is worth checking wherever it is registered. Forking it
# in put them under the same rule as everything else, and none of the three mods left over there
# registers a machine.
MODS = ['nauvis', 'nauvis_materials', 'nauvis_machines', 'nauvis_logistics', 'nauvis_fluids', 'nauvis_power',
        'nauvis_research', 'nauvis_mining', 'nauvis_military', 'nauvis_rocket']

# Where datagen writes, and where anything hand-written lives. Both are shipped, so both count.
ASSET_ROOTS = ['src/generated/client/assets', 'src/main/resources/assets']

MIN_EXTENT, MAX_EXTENT = -16.0, 32.0

# For belt speeds: the game's tick rate, and the units a belt measures distance in.
# Both are stated in nauvis_logistics/.../belt/Belts.java.
TICKS_PER_SECOND = 20
BELT_UNITS_PER_BLOCK = 64

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

    def read_bytes(self, namespace, path):
        key = f'{namespace}:{path}'
        if key in self.files:
            return self.files[key].read_bytes()
        entry = f'assets/{namespace}/{path}'
        if self.jar and entry in self.vanilla:
            return self.jar.read(entry)
        return None


# Textures that are meant to be see-through, by id. Empty today: nothing this pack draws is glass,
# leaves or a grate. Add to it when something is, rather than relaxing the rule.
TRANSPARENT_TEXTURES_ALLOWED = set()


def png_has_transparency(data):
    """Whether any pixel of a PNG is less than fully opaque, using only the standard library.

    Enough of a decoder for Minecraft's sprites: 8-bit greyscale, RGB, palette, greyscale+alpha
    and RGBA, non-interlaced. Anything else is reported as opaque with a note, rather than failing a
    build on a texture this cannot read.
    """
    import struct
    import zlib

    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return False
    pos = 8
    width = height = depth = colour = interlace = None
    palette_alpha = None
    idat = []
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            width, height, depth, colour, _, _, interlace = struct.unpack('>IIBBBBB', body)
        elif kind == b'tRNS':
            palette_alpha = body
        elif kind == b'IDAT':
            idat.append(body)
        elif kind == b'IEND':
            break
    if colour in (0, 2) and palette_alpha is None:
        return False  # greyscale or RGB with no transparent colour: opaque by construction
    if colour == 3 and palette_alpha is None:
        return False  # a palette with no tRNS is opaque
    if depth != 8 or interlace != 0:
        notes.append(f'a texture with bit depth {depth} / interlace {interlace} was not checked for opacity')
        return False

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    stride = width * channels
    raw = zlib.decompress(b''.join(idat))
    previous = bytearray(stride)
    offset = 0
    for _ in range(height):
        method = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = previous[i]
            c = previous[i - channels] if i >= channels else 0
            if method == 1:
                line[i] = (line[i] + a) & 0xFF
            elif method == 2:
                line[i] = (line[i] + b) & 0xFF
            elif method == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif method == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        if colour == 3:
            for i in range(width):
                index = line[i]
                if index < len(palette_alpha) and palette_alpha[index] < 255:
                    return True
        elif colour in (4, 6):
            for i in range(channels - 1, stride, channels):
                if line[i] < 255:
                    return True
        elif colour in (0, 2) and palette_alpha is not None:
            # A single transparent colour. Compare each pixel against it.
            key = struct.unpack('>' + 'H' * (len(palette_alpha) // 2), palette_alpha)
            for i in range(0, stride, channels):
                if tuple(line[i:i + channels]) == tuple(k & 0xFF for k in key):
                    return True
        previous = line
    return False


def split(identifier):
    """'ns:path' or bare 'path', which means minecraft - the same rule Identifier uses."""
    return tuple(identifier.split(':', 1)) if ':' in identifier else ('minecraft', identifier)


def model_refs(node):
    """Every model identifier reachable in a blockstate or item definition.

    Deliberately structural rather than schema-aware: variants, weighted lists, multipart applies
    and 26.2's item definitions (composite, condition, select, range_dispatch) all end at a
    {"model": "..."} somewhere, and walking for that key survives a format the next version adds.

    `base` is the second key that names one. A `minecraft:special` item model puts its *renderer*
    under "model" - as an object, not a string - and the model that carries the display transforms
    under "base", so an item drawn by a special renderer names no model at all by the first rule.
    A chest is one of those, and so is a banner, a bed, a shield and a decorated pot.
    """
    found = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ('model', 'base') and isinstance(value, str):
                found.append(value)
            else:
                found.extend(model_refs(value))
    elif isinstance(node, list):
        for item in node:
            found.extend(model_refs(item))
    return found


# A special model renderer names its texture directly rather than through a model, and the
# renderer decides which atlas and which directory that name means. Only the ones this pack
# actually uses are listed: an unknown type is skipped rather than guessed at, because guessing
# a directory wrong would report a missing PNG that is not missing.
SPECIAL_TEXTURE_DIRS = {
    'minecraft:chest': 'entity/chest',
}


def special_texture_refs(node):
    """Every (type, texture) a special model renderer names, wherever it appears."""
    found = []
    if isinstance(node, dict):
        kind = node.get('type')
        texture = node.get('texture')
        if isinstance(kind, str) and isinstance(texture, str):
            found.append((kind, texture))
        for value in node.values():
            found.extend(special_texture_refs(value))
    elif isinstance(node, list):
        for item in node:
            found.extend(special_texture_refs(item))
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

    # builtin/* parents have no file: the game synthesises them, and generated flat items all
    # inherit from minecraft:builtin/generated. Not an absence, so not a failure.
    if namespace == 'minecraft' and name.startswith('builtin/'):
        return {}

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
                continue
            # A flat item icon is transparent round its shape by nature, and its slots are the
            # layers of builtin/generated; only geometry has faces to put a hole in.
            if value in TRANSPARENT_TEXTURES_ALLOWED or slot == 'particle' or re.fullmatch(r'layer\d+', slot):
                continue
            png = assets.read_bytes(texture_ns, f'textures/{texture_name}.png')
            if png is not None and png_has_transparency(png):
                fail(where, f'texture slot "{slot}" names {value}, which has transparent pixels - '
                            f'a solid model drawn with it has holes. anvil_top did this to the '
                            f'pumpjack. Pick an opaque sprite, or list it in '
                            f'TRANSPARENT_TEXTURES_ALLOWED if it is meant to be see-through')

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

        # A special renderer's texture is not a model's texture slot, so nothing above sees it.
        # It is exactly the same failure though - a name with no PNG behind it - and for a chest
        # it is invisible rather than magenta, because the atlas simply has no such sprite.
        for kind, texture in special_texture_refs(content):
            directory = SPECIAL_TEXTURE_DIRS.get(kind)
            if directory is None:
                continue
            texture_ns, texture_name = split(texture)
            if assets.exists(texture_ns, f'textures/{directory}/{texture_name}.png') is False:
                fail(origin, f'{kind} names texture {texture}, and there is no '
                             f'{texture_ns}:textures/{directory}/{texture_name}.png')


def check_registrations(assets):
    """Every registered block has a blockstate, and every one with an item has an item definition.

    The bug this is for: a block registered and never given a model is not a missing file anyone
    notices, it is a magenta cube in a world - and an item registered for it and never given a
    model is the same cube in a hand.

    Whether a block has an item is read from ModItems.java: a block whose constant is never named
    there has none, and is held to the blockstate only. Natural water is that block - a liquid's
    item is a bucket, and its bucket is vanilla's - and it is the one case, so a block that
    cannot be matched to a constant is still held to both files rather than let off.
    """
    for mod in MODS:
        source = ROOT / mod / 'src' / 'main' / 'java'
        if not source.is_dir():
            continue
        items = ''.join(path.read_text(encoding='utf-8') for path in source.rglob('ModItems.java'))
        for path in source.rglob('ModBlocks.java'):
            text = path.read_text(encoding='utf-8')
            names = re.findall(r'registerBlock\(\s*"([a-z0-9_]+)"', text)
            constants = dict(re.findall(
                r'(\w+)\s*=\s*BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"', text))
            constant_of = {name: constant for constant, name in constants.items()}
            if not names:
                notes.append(f'{mod}: no block ids found in {path.name} - ids are not literals '
                             f'there, so its blocks are unchecked')
            for name in names:
                constant = constant_of.get(name)
                has_item = constant is None or re.search(rf'ModBlocks\.{constant}\b', items)
                if not has_item:
                    notes.append(f'{mod}:{name} has no item, so only its blockstate is checked')
                for kind in (('blockstates', 'items') if has_item else ('blockstates',)):
                    if assets.exists(mod, f'{kind}/{name}.json') is not True:
                        fail(f'{mod}:{name}', f'is registered and has no {kind} file')


def check_footprints():
    """Every machine's cells, against the footprint Factorio gave that entity.

    A footprint is identity - see docs/ARCHITECTURE.md - so it is checked rather than
    remembered, the way recipes are. `data/mapping.json` holds the number beside the item id;
    a `*Shape.java` holds the cells; this reads the cells back out of the source and compares.
    Constants read out of source is `check_gui_layout.py`'s trick, and it needs no game.

    A file with no cells is the framework rather than a machine, and is passed over. A file that
    does build cells but names no FACTORIO_ID is reported as unchecked rather than failed - a
    block need not be a Factorio entity - but it is said out loud, so nobody assumes otherwise.
    """
    mapping = json.loads((ROOT / 'data' / 'mapping.json').read_text(encoding='utf-8'))['items']

    for mod in MODS:
        source = ROOT / mod / 'src' / 'main' / 'java'
        if not source.is_dir():
            continue
        for path in sorted(source.rglob('*Shape.java')):
            text = path.read_text(encoding='utf-8')
            constructions = len(re.findall(r'new MachineCell\(', text))
            cell_calls = re.findall(
                r'new MachineCell\(\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*[\w.]+,\s*(-?\d+),', text)
            if not constructions:
                continue  # the framework itself, not a machine

            # Every cell has to be written out. A shape that builds cells in a loop reads as
            # whatever its literal cells happen to span, which is smaller than the machine and
            # perfectly quiet about it - the electric drill read as 1x2 when eight of its nine
            # cells came out of a nested loop. Refusing to guess is the whole value here.
            #
            # Writing them out is worth doing anyway: a cell's position in the list is its `part`
            # value, and `part` values are in world saves.
            if len(cell_calls) != constructions:
                fail(path.name, f'builds {constructions - len(cell_calls)} of its {constructions} '
                                f'cells from something other than plain numbers, so its footprint '
                                f'cannot be read here. Write every cell out.')
                continue

            id_match = re.search(r'FACTORIO_ID\s*=\s*"([a-z0-9-]+)"', text)
            if not id_match:
                notes.append(f'{path.name}: no FACTORIO_ID, so its footprint is unchecked')
                continue

            factorio_id = id_match.group(1)
            cells = [(int(x), int(z)) for x, _, z, _turns in cell_calls]
            width = max(x for x, _ in cells) - min(x for x, _ in cells) + 1
            depth = max(z for _, z in cells) - min(z for _, z in cells) + 1

            entry = mapping.get(factorio_id)
            if entry is None:
                fail(path.name, f'names {factorio_id}, which is not in data/mapping.json')
                continue

            wanted = entry.get('size', [1, 1])
            # Either way round: Factorio quotes a boiler as 3x2 and it is the same boiler laid
            # the other way. What must not differ is the pair of numbers.
            if sorted([width, depth]) != sorted(wanted):
                fail(path.name, f'is {width}x{depth}, and Factorio\'s {factorio_id} is '
                                f'{wanted[0]}x{wanted[1]} - see data/mapping.json')

            check_rotations(path, mod, entry, [int(turns) for _x, _y, _z, turns in cell_calls])


def check_stacks():
    """Every item's stack size, against the `stack` recorded for it in data/mapping.json.

    A stack size is a fact a Factorio player carries in their head - plates in hundreds, machines
    in fifties, one satellite - so it is written in the mapping and the code is held to it, the
    way footprints are. The code says `Stacks.of(n)` with Factorio's own n; a vanilla stand-in
    says it as `stack(Items.X, n)` in the pack mod's StandInStacks.java, and that list is held
    to the mapping's stand-in entries the same way.

    Registrations are read out of each mod's ModItems.java (and the pack mod's ModContent.java)
    one statement at a time: `registerSimpleItem("id", Stacks.of(n))` - or vanilla's own
    `stacksTo(n)` in the pack mod, which compiles against nothing of ours - `registerItem("id", ...,
    () -> Stacks.of(n))`, and `registerSimpleBlockItem(ModBlocks.CONST, () -> Stacks.of(n))` with
    the constant resolved through ModBlocks.java. A registration in a loop names no id and is
    reported as unchecked rather than guessed at. An item with a `stack` in the mapping and none
    in the code fails, and so does the reverse.
    """
    mapping = json.loads((ROOT / 'data' / 'mapping.json').read_text(encoding='utf-8'))['items']
    wanted = {}
    for entry in mapping.values():
        if 'stack' in entry and entry.get('item'):
            wanted[entry['item']] = entry['stack']

    seen = set()
    for mod in MODS:
        source = ROOT / mod / 'src' / 'main' / 'java'
        if not source.is_dir():
            continue
        constants = {}
        for path in source.rglob('ModBlocks.java'):
            constants.update(dict(re.findall(r'(\w+)\s*=\s*BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"',
                                             path.read_text(encoding='utf-8'))))
        for path in list(source.rglob('ModItems.java')) + list(source.rglob('ModContent.java')):
            text = path.read_text(encoding='utf-8')
            for statement in text.split(';'):
                if 'register' not in statement:
                    continue
                stack = re.search(r'(?:Stacks\.of|stacksTo)\((\d+)\)', statement)
                named = re.search(r'register(?:Simple)?Item\(\s*"([a-z0-9_]+)"', statement)
                by_block = re.search(r'registerSimpleBlockItem\(\s*ModBlocks\.(\w+)', statement)
                if named:
                    item_id = f'{mod}:{named.group(1)}'
                elif by_block:
                    if by_block.group(1) not in constants:
                        fail(path.name, f'registers a block item for ModBlocks.{by_block.group(1)}, which '
                                        f'ModBlocks.java does not register by a literal id')
                        continue
                    item_id = f'{mod}:{constants[by_block.group(1)]}'
                elif 'registerSimpleBlockItem(' in statement:
                    notes.append(f'{path.name}: a block item registered without naming its block - a loop - '
                                 f'so its stack size is unchecked')
                    continue
                else:
                    continue
                seen.add(item_id)
                if stack and item_id not in wanted:
                    fail(path.name, f'{item_id} says Stacks.of({stack.group(1)}) and data/mapping.json records '
                                    f'no stack for it')
                elif not stack and item_id in wanted:
                    fail(path.name, f'{item_id} has a stack of {wanted[item_id]} in data/mapping.json and none '
                                    f'in the code - register it with Stacks.of({wanted[item_id]})')
                elif stack and int(stack.group(1)) != wanted[item_id]:
                    fail(path.name, f'{item_id} says Stacks.of({stack.group(1)}), and data/mapping.json says '
                                    f'{wanted[item_id]}')

    # The vanilla stand-ins, sized by the pack mod: one `stack(Items.X, n)` line each.
    stand_ins = {entry['item']: entry['stack'] for entry in mapping.values()
                 if entry.get('stand_in') and 'stack' in entry}
    path = ROOT / 'nauvis' / 'src' / 'main' / 'java' / 'com' / 'jaguarm' / 'nauvis' / 'StandInStacks.java'
    text = path.read_text(encoding='utf-8')
    coded = {f'minecraft:{name.lower()}': int(n) for name, n in re.findall(r'stack\(Items\.(\w+),\s*(\d+)\)', text)}
    # A coloured block's item is picked out of a colour collection: Items.CONCRETE.pick(DyeColor.GRAY).
    coded.update({f'minecraft:{color.lower()}_{name.lower()}': int(n)
                  for name, color, n in re.findall(r'stack\(Items\.(\w+)\.pick\(DyeColor\.(\w+)\),\s*(\d+)\)', text)})
    for item_id, stack in stand_ins.items():
        if item_id not in coded:
            fail(path.name, f'{item_id} has a stack of {stack} in data/mapping.json and no line here')
        elif coded[item_id] != stack:
            fail(path.name, f'{item_id} says {coded[item_id]}, and data/mapping.json says {stack}')
    for item_id, stack in coded.items():
        if item_id not in stand_ins:
            fail(path.name, f'{item_id} is sized {stack} here and data/mapping.json records no stand-in stack for it')


# North, east, south, west, in quarter turns clockwise - the same order Boxes uses.
QUARTER_TURNS = {'north': 0, 'east': 1, 'south': 2, 'west': 3}


def check_rotations(path, mod, entry, turns):
    """Every variant's `y` against the cell's own turn plus the machine's facing.

    The bug this exists for shipped, and was visible in three directions out of four. The models
    were generated with `.with(cellDispatch).with(ROTATION_HORIZONTAL_FACING)`, and a
    `VariantMutator` **sets** `y` rather than adding to it - so the facing overwrote each cell's
    own turn and every corner of an east-facing boiler pointed the same way.

    Nothing caught it. The collision boxes were right the whole time, because `MachineCell` adds
    the two rotations before building its `VoxelShape`; so the machine you saw and the machine you
    walked into were different objects, and every test passed because tests look at collision.
    That is the failure `Boxes` warns about in as many words, and it happened anyway.

    So it is arithmetic now: `y` must be `90 * (turns + facing) mod 360`, from the same two
    numbers the shape uses.
    """
    namespace, _, name = entry.get('item', '').partition(':')
    if not namespace or not name:
        return

    for root in ASSET_ROOTS:
        blockstate = ROOT / mod / root / namespace / 'blockstates' / f'{name}.json'
        if blockstate.is_file():
            break
    else:
        return  # no blockstate here; check_references already reports a block with none

    content = json.loads(blockstate.read_text(encoding='utf-8'))
    variants = content.get('variants')
    if not variants:
        return  # multipart, which no machine shape uses

    for key, variant in variants.items():
        if isinstance(variant, list):
            continue  # a weighted list, which no machine uses
        properties = dict(pair.split('=', 1) for pair in key.split(',') if '=' in pair)
        if 'part' not in properties:
            continue

        part = int(properties['part'])
        if part >= len(turns):
            fail(f'{namespace}:{name}', f'has a variant for part={part} and its shape has '
                                        f'{len(turns)} cells')
            continue

        facing = QUARTER_TURNS.get(properties.get('facing', 'north'), 0)
        wanted = 90 * ((turns[part] + facing) % 4)
        found = variant.get('y', 0)
        if found != wanted:
            fail(f'{namespace}:{name}', f'draws {key} turned {found} degrees, and its shape turns '
                                        f'that cell {wanted} - the model and the collision box '
                                        f'disagree, so it is see-through on one side and solid on '
                                        f'the other')


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


def check_belt_speeds():
    """Every belt and splitter block's speed constant, against the tiles per second Factorio publishes.

    A belt's speed is identity in the sense non-negotiable #1 means: it is what a player's mental
    picture of a factory is built on - how many machines one belt feeds, how far apart to space a
    bus - and getting it wrong makes every ratio in the game subtly wrong while everything still
    works. So it is written down once in `data/mapping.json`, beside the id, and checked here
    rather than remembered, exactly as footprints are.

    The code holds it as whole units a tick - see Belts.java for why sixty-four - and this does the
    conversion, so the number in the source and the number on the wiki can be compared without
    either of them being the other's translation.

    Splitters are checked with the belts and for the same reason: a splitter runs at its tier's
    belt speed, so a red splitter with a yellow splitter's constant would throttle every red line
    that split - quietly, and everywhere. `data/mapping.json` gives each one a speed of its own.
    """
    mapping = json.loads((ROOT / 'data' / 'mapping.json').read_text(encoding='utf-8'))['items']

    for mod in MODS:
        source = ROOT / mod / 'src' / 'main' / 'java'
        if not source.is_dir():
            continue
        tiered = sorted(set(source.rglob('*BeltBlock.java'))
                        | set(source.rglob('*SplitterBlock.java')))
        for path in tiered:
            text = path.read_text(encoding='utf-8')
            speed = re.search(r'int SPEED\s*=\s*(\d+)\s*;', text)
            factorio_id = re.search(r'FACTORIO_ID\s*=\s*"([a-z0-9-]+)"', text)
            if not speed:
                # The abstract base, which has no speed of its own.
                continue
            if not factorio_id:
                fail(path.name, 'has a SPEED but no FACTORIO_ID, so there is nothing to check it '
                                'against - see data/mapping.json')
                continue

            entry = mapping.get(factorio_id.group(1))
            if entry is None:
                fail(path.name, f'names {factorio_id.group(1)}, which is not in data/mapping.json')
                continue
            wanted = entry.get('speed')
            if wanted is None:
                fail(path.name, f'{factorio_id.group(1)} has no "speed" in data/mapping.json - the '
                                f'number is identity and belongs there, not only in the source')
                continue

            tiles = int(speed.group(1)) * TICKS_PER_SECOND / BELT_UNITS_PER_BLOCK
            if abs(tiles - wanted) > 1e-9:
                fail(path.name, f'moves {tiles} tiles a second, and Factorio gives '
                                f'{factorio_id.group(1)} {wanted} - see data/mapping.json')


assets = Assets()
check_references(assets)
check_registrations(assets)
check_stacks()
check_fluid_models(assets)
check_footprints()
check_belt_speeds()

print(f'{len(assets.files)} first-party asset files across {len(MODS)} mods')
for note in notes:
    print(f'  {note}')

if failures:
    print('\nUNRESOLVED:')
    print('\n'.join(sorted(set(failures))))
    sys.exit(1)
print('\neverything resolves')
