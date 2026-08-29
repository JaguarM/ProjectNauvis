#!/usr/bin/env python3
"""
Generate the vanilla-replacement datapack: the recipes Project Nauvis takes away.

    data/removals.json  ->  nauvis/.../vanilla_replacement/data/minecraft/recipe/<name>.json

PLAN.md puts this at milestone 3, and the reason is the one in its own sentence: once research
gates progression there is somewhere for stripped vanilla recipes to go. A pack that adds
Factorio's tree beside Minecraft's own recipes has not replaced anything - the player simply
takes whichever road is shorter, and it is never the one with the belts on it.

**Pack policy, so it lives in the `nauvis` mod** - non-negotiable #3. A subsystem mod may never
delete another mod's recipe, and least of all Minecraft's.

How a removal works
-------------------

A recipe file whose only content is a condition that is never true. NeoForge's
`ConditionalOps.ConditionalDecoder` reads the conditions first and returns an empty result
**without decoding the rest**, so no recipe body is needed, and the file at
`data/minecraft/recipe/<name>.json` shadows the one it is written over.

The condition is **`neoforge:never`**. There is no `neoforge:false`; the registered names are
`never` and `always`, and a typo here does not fail - it throws while parsing one recipe, which
is a line in a log and a recipe that is still craftable.

**And the file has to outrank NeoForge, not just vanilla.** NeoForge ships its own copy of about
three hundred and eighty of Minecraft's recipe files - retagged so that other mods' metals and
woods work in them - and its resources are applied after any mod's. A removal written into the
pack mod's plain resources therefore works for most recipes and silently does nothing for those.
The hopper is one of them, which is how this was found: four byte-identical files, three of which
took effect. So the removals ship as a built-in datapack at `Pack.Position.TOP`, which sits above
every mod's resources. See `ModPacks` in the nauvis mod.

The rule, and why it is enforced rather than remembered
-------------------------------------------------------

**A vanilla recipe is removed only when the pack can already do that job.** Every entry names a
`replaced_by` recipe and this tool fails if that recipe is not shipped, so the pack cannot take
something away and leave nothing in its place. That is the failure that would be found by a
player unable to craft a furnace, three hours in, on a world they had already built on.

Two kinds of entry, and the difference is worth keeping:

- a **conflict** is a vanilla recipe making an item `data/mapping.json` maps a Factorio item
  onto, so two recipes with different ingredient lists make the same thing and one of them is
  wrong. Non-negotiable #1 says which one. **This half is checked, not listed**: given
  Minecraft's own recipe list, any pack recipe producing a vanilla item whose vanilla recipe is
  not removed is an error, so the table cannot fall behind the recipes.
- a **bypass** is vanilla doing a job Factorio has a machine for. This half is a judgement and
  is only ever a list.

Minecraft's own recipes are read out of the client jar in the Gradle cache to check both. It is
not always there, and the check skips itself when it is absent rather than failing - the same
arrangement `checkRecipes` has with the Factorio dump.

Usage:
    python tools/gen_removals.py               summary only, writes nothing
    python tools/gen_removals.py --check       diff against what is on disk
    python tools/gen_removals.py --write       write into the nauvis pack mod
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REMOVALS = REPO / "data" / "removals.json"
MAPPING = REPO / "data" / "mapping.json"

# Pack policy lives in the pack mod - non-negotiable #3 - and inside it, in a built-in datapack
# rather than in the mod's own resources. See ModPacks in the nauvis mod: NeoForge ships its own
# copy of ~380 of Minecraft's recipe files, hopper.json among them, and a mod's plain resources
# do not outrank it.
OUT_ROOT = REPO / "nauvis" / "src" / "main" / "resources" / "vanilla_replacement"

# Minecraft's own recipes, for the two checks. Machine-specific and often absent; both checks
# say so and skip rather than failing, because a fresh clone must still build.
VANILLA_JAR = str(Path.home() / ".gradle" / "caches" / "neoformruntime" / "artifacts"
                  / "minecraft_*_client.jar")

# NeoForge's own universal jar, for the summary line about which removals it also ships. Not a
# check - the built-in datapack outranks it either way - but the number is worth seeing, because
# a maintainer who moves these files back into plain resources needs to know what it costs.
NEOFORGE_JAR = str(Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
                   / "net.neoforged" / "neoforge" / "*" / "*" / "neoforge-*-universal.jar")

# Where each mod's recipes are, the same list gen_recipes.py resolves.
SIBLING_REPOS = {
    "facrafting": "Facrafting",
    "neoprogressivematerials": "NeoProgressiveMaterials",
    "neoprogressiveautomation": "NeoProgressiveAutomation",
    "crumblingore": "CrumblingOre",
}


class GenError(Exception):
    """A fault in the inputs. Always fatal: a wrong removal is a recipe nobody can craft."""


def mod_resource_root(mod_id: str) -> Path:
    if mod_id in SIBLING_REPOS:
        return REPO.parent / SIBLING_REPOS[mod_id] / "src" / "main" / "resources"
    return REPO / mod_id / "src" / "main" / "resources"


def load_removals() -> dict:
    if not REMOVALS.exists():
        raise GenError(f"{REMOVALS} is missing.")
    entries = json.loads(REMOVALS.read_text(encoding="utf-8"))["recipes"]
    for key, entry in entries.items():
        if ":" not in key:
            raise GenError(f"'{key}' is not a namespaced recipe id.")
        if not entry.get("replaced_by"):
            raise GenError(
                f"'{key}' has no `replaced_by`. Nothing is removed without naming what does "
                "the job instead - see the rule in data/removals.json."
            )
        if entry.get("kind") not in ("conflict", "bypass"):
            raise GenError(f"'{key}' has kind {entry.get('kind')!r}; it is a conflict or a bypass.")
    return entries


def load_mirrors() -> dict[str, str]:
    """
    Vanilla recipes a pack recipe reproduces rather than replaces.

    The conflict check asks "does the pack make this vanilla item, and is Minecraft's recipe for it
    still there" - the right question when the two disagree about what it costs, and the wrong one
    when the pack recipe *is* vanilla's, put in the crafting panel so a whole sequence can be done
    there. Planks are that: one log to four, both ways. Demanding vanilla's removal would take the
    2x2 inventory route away for nothing.
    """
    if not REMOVALS.exists():
        return {}
    return json.loads(REMOVALS.read_text(encoding="utf-8")).get("mirrors", {})


def shipped_recipes() -> set[str]:
    """
    Every facraft recipe the pack actually ships, as `<namespace>:<name>`.

    Read off the disk rather than from the generator, because what matters here is what is in
    the jar: a recipe the generator would produce but nobody copied across is not a replacement
    for anything.
    """
    found = set()
    roots = [REPO / name for name in os.listdir(REPO) if (REPO / name / "src").is_dir()]
    roots += [REPO.parent / name for name in SIBLING_REPOS.values()]

    for root in roots:
        for path in glob.glob(str(root / "src" / "main" / "resources" / "data" / "*" / "recipe" / "*.json")):
            name = Path(path).stem
            if name.endswith("_standalone"):
                continue
            namespace = Path(path).parts[-3]
            found.add(f"{namespace}:{name}")
    return found


def vanilla_recipes() -> dict[str, str] | None:
    """Minecraft's own recipe ids mapped to the item each makes, or None when the jar is absent."""
    jars = sorted(glob.glob(VANILLA_JAR))
    if not jars:
        return None

    recipes = {}
    with zipfile.ZipFile(jars[-1]) as jar:
        for name in jar.namelist():
            if not name.startswith("data/minecraft/recipe/") or not name.endswith(".json"):
                continue
            body = json.loads(jar.read(name))
            result = body.get("result")
            item = result.get("id") if isinstance(result, dict) else result
            if item:
                recipes["minecraft:" + Path(name).stem] = item
    return recipes


def neoforge_overrides() -> set[str]:
    """Which of Minecraft's recipe files NeoForge ships its own copy of."""
    jars = sorted(glob.glob(NEOFORGE_JAR))
    if not jars:
        return set()
    with zipfile.ZipFile(jars[-1]) as jar:
        return {"minecraft:" + Path(name).stem for name in jar.namelist()
                if name.startswith("data/minecraft/recipe/") and name.endswith(".json")}


def plan(entries: dict) -> list[tuple[Path, dict]]:
    files = []
    for key in sorted(entries):
        namespace, path = key.split(":", 1)
        files.append((
            Path("data") / namespace / "recipe" / f"{path}.json",
            # No body. The conditional decoder never reaches one - see the module docstring.
            {"neoforge:conditions": [{"type": "neoforge:never"}]},
        ))
    return files


def audit(entries: dict, report: dict) -> None:
    """The two invariants, in the order they are worth failing on."""
    shipped = shipped_recipes()

    # 1. Nothing is removed without a replacement that actually ships.
    for key, entry in sorted(entries.items()):
        if entry["replaced_by"] not in shipped:
            raise GenError(
                f"'{key}' is removed and replaced by '{entry['replaced_by']}', which the pack "
                "does not ship. Either ship that recipe or drop the removal - taking something "
                "away and leaving nothing is the failure this check exists for."
            )

    vanilla = vanilla_recipes()
    if vanilla is None:
        report["vanilla"] = None
        return
    report["vanilla"] = len(vanilla)

    # 2. Every id names a recipe Minecraft actually has. A typo removes nothing, silently.
    for key in sorted(entries):
        if key not in vanilla:
            raise GenError(
                f"'{key}' is not one of Minecraft's {len(vanilla)} recipes. A removal that names "
                "nothing removes nothing and says nothing about it."
            )

    # 3. Every conflict is listed. This is the half that keeps the table from falling behind:
    #    ship a pack recipe for a vanilla item and the vanilla one has to go in the same commit.
    made_by_pack = set()
    for root in {mod_resource_root(m) for m in SIBLING_REPOS} | {
            REPO / name / "src" / "main" / "resources" for name in os.listdir(REPO)
            if (REPO / name / "src").is_dir()}:
        for path in glob.glob(str(root / "data" / "*" / "recipe" / "*.json")):
            if Path(path).stem.endswith("_standalone"):
                continue
            body = json.loads(Path(path).read_text(encoding="utf-8"))
            if body.get("type") != "facrafting:facraft":
                continue
            result = body.get("result", {}).get("id")
            if result and result.startswith("minecraft:"):
                made_by_pack.add(result)

    mirrors = load_mirrors()
    for recipe in sorted(mirrors):
        if recipe not in vanilla:
            raise GenError(f"'{recipe}' is listed as a mirror but Minecraft has no such recipe.")
        if recipe in entries:
            raise GenError(
                f"'{recipe}' is both removed and mirrored. A recipe the pack reproduces is one it "
                "is not replacing; pick one."
            )
    report["mirrors"] = len(mirrors)

    missing = []
    for recipe, item in sorted(vanilla.items()):
        if item in made_by_pack and recipe not in entries and recipe not in mirrors:
            missing.append((recipe, item))
    if missing:
        raise GenError(
            "the pack ships its own recipe for these, so Minecraft's has to go too - or, when the "
            "pack recipe reproduces vanilla's rather than replacing it, add it to `mirrors`:\n    "
            + "\n    ".join(f"{recipe}  (makes {item})" for recipe, item in missing)
        )
    report["conflicts_checked"] = len(made_by_pack)


def render(obj: dict) -> str:
    return json.dumps(obj, indent=2) + "\n"


def do_write(files: list) -> int:
    for rel, obj in files:
        path = OUT_ROOT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(render(obj), encoding="utf-8", newline="\n")
    print(f"  nauvis: {len(files)} removals -> {OUT_ROOT / 'data' / 'minecraft' / 'recipe'}")
    return len(files)


def do_check(files: list) -> int:
    missing, wrong, matching = [], [], 0
    expected = {rel.name for rel, _ in files}

    for rel, obj in files:
        path = OUT_ROOT / rel
        if not path.exists():
            missing.append(path)
        elif json.loads(path.read_text(encoding="utf-8")) == obj:
            matching += 1
        else:
            wrong.append(path)

    # A file left behind is a recipe the pack is still deleting for a reason nobody wrote down.
    directory = OUT_ROOT / "data" / "minecraft" / "recipe"
    stale = sorted(p for p in directory.glob("*.json") if p.name not in expected) \
        if directory.exists() else []

    print(f"  matched  {matching}")
    print(f"  missing  {len(missing)}")
    print(f"  wrong    {len(wrong)}")
    print(f"  stale    {len(stale)}")
    for path in missing + wrong + stale:
        print(f"    - {path.name}")

    return 1 if (missing or wrong or stale) else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="diff against what is on disk, write nothing")
    ap.add_argument("--write", action="store_true", help="write into the nauvis pack mod")
    args = ap.parse_args()

    report = {}
    try:
        entries = load_removals()
        audit(entries, report)
        files = plan(entries)
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    kinds = {}
    for entry in entries.values():
        kinds[entry["kind"]] = kinds.get(entry["kind"], 0) + 1
    print(f"{len(entries)} vanilla recipes removed: "
          + ", ".join(f"{n} {kind}" for kind, n in sorted(kinds.items())))
    for key, entry in sorted(entries.items()):
        print(f"  {key:32} -> {entry['replaced_by']}")
    if report.get("mirrors"):
        print("  and mirrored rather than removed: " + ", ".join(sorted(load_mirrors())))

    overridden = neoforge_overrides()
    if overridden:
        also = sorted(set(entries) & overridden)
        print(f"\n  NeoForge ships its own copy of {len(overridden)} of Minecraft's recipes, "
              f"{len(also)} of them here: {', '.join(also) if also else 'none'}")
        print("  Those are the ones a removal in plain mod resources would silently miss; the "
              "built-in datapack at Pack.Position.TOP is what makes them work.")

    if report.get("vanilla") is None:
        print("\n  (Minecraft's own recipes were not found in the Gradle cache, so the id and "
              "conflict checks were skipped)")
    else:
        print(f"\n  checked against Minecraft's {report['vanilla']} recipes; "
              f"{report.get('conflicts_checked', 0)} vanilla items are made by the pack")

    if args.check:
        print("\nchecking against files on disk:")
        return do_check(files)

    if args.write:
        print()
        do_write(files)

    return 0


if __name__ == "__main__":
    sys.exit(main())
