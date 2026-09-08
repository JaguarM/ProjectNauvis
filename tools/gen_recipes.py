#!/usr/bin/env python3
"""
Generate recipe JSON from Factorio's data.raw plus the mapping table (CLAUDE.md, rule 2).

    reference/factorio/data-raw-<version>.json + data/mapping.json -> recipe JSON per mod

Per recipe, `<ns>` the owning mod and `<name>` the product - or the recipe itself, for one that
makes several things or a fluid (factorio_data.py says which is which):
  data/<ns>/recipe/<name>.json                   facrafting:facraft, timed. Always written.
  data/<ns>/recipe/<name>_standalone.json        minecraft:crafting_shapeless, for a world without
                                                 Facrafting; only when the ingredients fit nine slots.
                                                 A `smelting` recipe's is a vanilla furnace recipe.
  crafting_table/data/<ns>/recipe/<name>_bench.json  the same shapeless copy in the optional bench
                                                 datapack, under its own id. Not for machine-only
                                                 or fluid recipes.

Conditions are derived: `facrafting`, plus `mod_loaded` for every mod supplying an ingredient; the
standalone copy negates only the Facrafting clause. A machine category in data.raw goes into the
recipe as Facrafting's `category`, which keeps it out of the hand panel. Fluid ingredients become
`fluid_ingredients`, fluid products `fluid_results`. Craft times are whole ticks. The tab and the
order are Factorio's own, from the item groups and orders in the dump. The summary counts what
produced nothing: skips, hidden recipes, recipes too large for a bench, and the probabilistic ones.

Usage:
    python tools/gen_recipes.py                     summary only
    python tools/gen_recipes.py --check             semantic diff against what is on disk
    python tools/gen_recipes.py --out DIR           write the tree to a staging directory
    python tools/gen_recipes.py --write             update the recipes already on disk
    python tools/gen_recipes.py --write --all       write every recipe, on disk or not
    python tools/gen_recipes.py --only MODID        restrict to one mod (repeatable)

`--write` rewrites the timed recipes that exist and adds their fallbacks; a timed recipe with no
file yet is left alone (its item is usually unregistered, and that is a load error), and so is a
fallback that differs from what it would write (the drills' were written by hand on purpose).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import factorio_data  # noqa: E402
from factorio_data import GenError, resolve_item  # noqa: E402

REPO = factorio_data.REPO

TICKS_PER_SECOND = 20

# A vanilla crafting grid is three by three. Anything asking for more than nine items total
# cannot be expressed as a shapeless recipe at any count, so it gets no standalone fallback.
GRID_SLOTS = 9

# Timed recipes the pack writes by hand, as policy rather than as Factorio's: the opening, four
# planks to a stone pickaxe (docs/MAPPING.md). Everything else in a recipe folder is generated.
PACK_OWN = {("nauvis", "oak_planks"), ("nauvis", "stone_pickaxe")}

# Products whose bench fallbacks were written by hand and are left alone: the burner drill's
# substitutes a burner drill and a redstone block for what a missing mod would supply, which no
# generator can invent. Every other fallback is generated and checked like the timed recipe.
HAND_FALLBACKS = {"burner_mining_drill"}

# The two sibling mods live in their own repos beside this one; everything else is a
# subproject here. Both are resolved to a `src/main/resources` root.
SIBLING_REPOS = {
    "facrafting": "Facrafting",
    "crumblingore": "CrumblingOre",
}


def mod_resource_root(mod_id: str) -> Path:
    if mod_id in SIBLING_REPOS:
        return REPO.parent / SIBLING_REPOS[mod_id] / "src" / "main" / "resources"
    return REPO / mod_id / "src" / "main" / "resources"


def whole(amount: int | float, factorio_id: str) -> int:
    if float(amount) != int(amount):
        raise GenError(f"'{factorio_id}' names an amount of {amount}, and a recipe here counts whole items.")
    return int(amount)


def craft_ticks(seconds: float, factorio_id: str) -> int:
    ticks = seconds * TICKS_PER_SECOND
    if ticks != int(ticks):
        raise GenError(
            f"'{factorio_id}' has a craft time of {seconds}s, which is {ticks} ticks - not a whole tick."
        )
    ticks = int(ticks)
    if not 1 <= ticks <= 12000:
        raise GenError(
            f"'{factorio_id}' wants {ticks} craft_ticks; Facrafting's codec accepts 1..12000."
        )
    return ticks


def required_mods(entry: dict, data: factorio_data.Data) -> list[str]:
    """
    Which mods have to be present for this recipe to mean anything.

    Facrafting always, plus any mod other than this one supplying an ingredient or taking a
    product - a recipe naming `nauvis_materials:iron_gear_wheel` is nonsense without that
    mod loaded, and so is one making `nauvis:solid_fuel`. Arrows point one way, so the file's
    own namespace is never listed.
    """
    foreign = {
        resolve_item(part["id"], data).split(":")[0]
        for part in entry["ingredients"] + entry["results"]
    }
    foreign.discard("minecraft")
    foreign.discard(entry["owner"])
    return ["facrafting"] + sorted(foreign)


def mod_loaded(mod_id: str) -> dict:
    return {"type": "neoforge:mod_loaded", "modid": mod_id}


def registered(item_id: str) -> dict:
    """A condition that holds once something registers this item, and not before."""
    return {"type": "neoforge:registered", "value": item_id}


def pending_ingredients(entry: dict, data: factorio_data.Data) -> list[dict]:
    """
    Conditions for ingredients whose mod is here but whose item is not written yet.

    A recipe naming an unregistered item is a load error, which is why the pack copies across
    only the recipes whose items exist. That rule alone would hold back the *whole* recipe for
    an item that is finished and craftable except for one ingredient that is not.
    `neoforge:registered` is the way out: the recipe ships, correct and complete, and simply does
    not load until whatever it names exists. Mark an entry `"pending": true` in the mapping when
    it names an id nothing registers yet; the flag comes off when the item arrives.
    """
    conditions = []
    for part in entry["ingredients"]:
        if data.mapped(part["id"]).get("pending"):
            conditions.append(registered(resolve_item(part["id"], data)))
    return conditions


def item_result(entry: dict) -> dict | None:
    return next((r for r in entry["results"] if not r["fluid"]), None)


def facraft_recipe(entry: dict, data: factorio_data.Data) -> dict:
    """The real recipe: timed, sized ingredients, fluids where the dump says so, no grid."""
    out = {
        "neoforge:conditions": [mod_loaded(m) for m in required_mods(entry, data)] + pending_ingredients(entry, data),
        "type": "facrafting:facraft",
        "craft_ticks": craft_ticks(entry["time"], entry["id"]),
        "group": entry["tab"],
        "order": entry["order"],
    }
    if entry["category"]:
        out["category"] = entry["category"]

    out["ingredients"] = [
        {"ingredient": resolve_item(i["id"], data), "count": whole(i["amount"], entry["id"])}
        for i in entry["ingredients"] if not i["fluid"]
    ]
    fluids_in = [
        {"ingredient": resolve_item(i["id"], data), "amount": whole(i["amount"], entry["id"])}
        for i in entry["ingredients"] if i["fluid"]
    ]
    if fluids_in:
        out["fluid_ingredients"] = fluids_in

    result = item_result(entry)
    if result:
        out["result"] = {"id": resolve_item(result["id"], data), "count": whole(result["amount"], entry["id"])}
    fluid_results = [r for r in entry["results"] if r["fluid"]]
    if fluid_results:
        out["fluid_results"] = [
            {"id": resolve_item(r["id"], data), "amount": whole(r["amount"], entry["id"])} for r in fluid_results
        ]
    return out


def touches_fluid(entry: dict) -> bool:
    """Whether any fluid goes in or comes out, which is what rules a bench out."""
    return any(p["fluid"] for p in entry["ingredients"] + entry["results"])


def shapeless_ingredients(entry: dict, data: factorio_data.Data) -> list[str] | None:
    """
    Flatten to one grid slot per item, the way a shapeless recipe wants them.

    Returns None when the recipe cannot fit a crafting grid. Scaling the counts down to make
    it fit would break non-negotiable #1, so the fallback is simply not offered.
    """
    flat: list[str] = []
    for i in entry["ingredients"]:
        flat.extend([resolve_item(i["id"], data)] * whole(i["amount"], entry["id"]))
    return flat if len(flat) <= GRID_SLOTS else None


def fallback_conditions(entry: dict, data: factorio_data.Data, *, without_facrafting: bool) -> list[dict]:
    """
    Two callers want the same fallback body under opposite conditions: the standalone copy
    applies when Facrafting is absent, the datapack copy when it is present but the player has
    chosen to re-enable bench crafting.
    """
    mods = required_mods(entry, data)

    if without_facrafting:
        # Only the Facrafting clause is negated. A mod supplying an ingredient is still
        # required: without it the recipe would name an item that does not exist.
        conditions = [{"type": "neoforge:not", "value": mod_loaded("facrafting")}]
        conditions += [mod_loaded(m) for m in mods if m != "facrafting"]
    else:
        conditions = [mod_loaded(m) for m in mods]
    return conditions + pending_ingredients(entry, data)


def shapeless_recipe(entry: dict, data: factorio_data.Data, ingredients: list[str], *, without_facrafting: bool) -> dict:
    """The bench fallback: the same ingredients, one grid slot each, in no time at all."""
    result = item_result(entry)
    return {
        "neoforge:conditions": fallback_conditions(entry, data, without_facrafting=without_facrafting),
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ingredients,
        "result": {
            "id": resolve_item(result["id"], data),
            "count": whole(result["amount"], entry["id"]),
        },
    }


def smelting_recipe(entry: dict, data: factorio_data.Data, *, without_facrafting: bool) -> dict | None:
    """
    A vanilla furnace recipe, for a smelting recipe a vanilla furnace can express.

    That is one ingredient, one at a time, one result: iron and copper. Steel is five plates
    and stone brick is two stone, and a vanilla furnace takes one item, so those keep the
    shapeless fallback - a bench is the fallback for a world without Facrafting, and steel has to
    come from somewhere there. The time is Factorio's, so a vanilla furnace in such a world smelts
    at the rate the pack's own furnace does.
    """
    ingredients = entry["ingredients"]
    result = item_result(entry)
    if len(ingredients) != 1 or ingredients[0]["amount"] != 1 or result["amount"] != 1:
        return None
    return {
        "neoforge:conditions": fallback_conditions(entry, data, without_facrafting=without_facrafting),
        "type": "minecraft:smelting",
        "category": "misc",
        "ingredient": resolve_item(ingredients[0]["id"], data),
        "result": {"id": resolve_item(result["id"], data)},
        "cookingtime": craft_ticks(entry["time"], entry["id"]),
    }


def plan(entries: dict, data: factorio_data.Data, only: set[str] | None) -> tuple[dict[str, list], dict]:
    """
    Work out every file that should exist, without touching the disk.

    Returns the files keyed by owning mod, plus a report of what got no bench fallback.
    """
    files: dict[str, list[tuple[Path, dict]]] = {}
    names_seen: dict[str, dict[str, str]] = {}
    report = {"generated": 0, "no_fallback": []}

    for factorio_id, entry in sorted(entries.items()):
        owner = entry["owner"]
        if only and owner not in only:
            continue
        # The file is named after the item it produces (or the recipe, for one that makes
        # several things), in the owning mod's own namespace.
        name = entry["file"]
        seen = names_seen.setdefault(owner, {})
        if name in seen:
            raise GenError(
                f"'{factorio_id}' and '{seen[name]}' both resolve to '{name}' in {owner}; "
                "one of the two needs a distinct item in the mapping table."
            )
        seen[name] = factorio_id
        out = files.setdefault(owner, [])
        out.append((Path("data") / owner / "recipe" / f"{name}.json", facraft_recipe(entry, data)))
        report["generated"] += 1

        if entry["product"] is None:
            # Filed under its own name: a fluid or several things come out, and a bench makes one.
            report["no_fallback"].append(factorio_id)
            continue

        standalone = Path("data") / owner / "recipe" / f"{name}_standalone.json"
        bench = Path("crafting_table") / "data" / owner / "recipe" / f"{name}_bench.json"
        category = entry["category"]
        smelted = category == "smelting" and smelting_recipe(entry, data, without_facrafting=True)
        flat = None if touches_fluid(entry) else shapeless_ingredients(entry, data)
        if smelted:
            out.append((standalone, smelted))
        elif flat is None:
            report["no_fallback"].append(factorio_id)
        else:
            out.append((standalone, shapeless_recipe(entry, data, flat, without_facrafting=True)))
            # A bench copy only for what the hand crafts: the pack is for skipping craft times,
            # and a machine's recipe on a bench would be a machine nobody needs.
            if category is None:
                out.append((bench, shapeless_recipe(entry, data, flat, without_facrafting=False)))

    return files, report


def render(obj: dict) -> str:
    return json.dumps(obj, indent=2) + "\n"


def timed_twin(rel: Path) -> Path:
    """The timed recipe a fallback belongs to: `x_standalone` and `crafting_table/.../x_bench` -> `data/.../x`."""
    name = rel.stem.removesuffix("_standalone").removesuffix("_bench")
    parts = rel.parts[1:] if rel.parts[0] == "crafting_table" else rel.parts
    return Path(*parts[:-1]) / f"{name}.json"


def do_write(files: dict, root: Path | None, everything: bool) -> int:
    """
    Into a staging directory, everything. Into the mods, only what already has a timed recipe on
    disk - a missing one is usually an item nobody has registered, and a recipe naming an
    unregistered item is a load error - and never over a fallback that was written by hand.
    """
    written = 0
    unregistered, kept = [], []
    for mod_id, entries in sorted(files.items()):
        base = (root / mod_id) if root else mod_resource_root(mod_id)
        if root is None and not base.parent.parent.parent.exists():
            print(f"  ! {mod_id}: {base} does not exist yet, skipped", file=sys.stderr)
            continue
        count = 0
        for rel, obj in entries:
            path = base / rel
            if root is None and not everything:
                if not (base / timed_twin(rel)).exists():
                    unregistered.append(path)
                    continue
                if obj["type"] != "facrafting:facraft" and timed_twin(rel).stem in HAND_FALLBACKS \
                        and path.exists() and json.loads(path.read_text(encoding="utf-8")) != obj:
                    kept.append(path)
                    continue
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(render(obj), encoding="utf-8", newline="\n")
            written += 1
            count += 1
        print(f"  {mod_id}: {count} files -> {base}")
    if unregistered:
        names = sorted({timed_twin(Path(p.name)).stem for p in unregistered})
        print(f"\n  {len(unregistered)} files left unwritten: no timed recipe on disk for "
              f"{', '.join(names[:8])}{', ...' if len(names) > 8 else ''} - register the item, then --all")
    for path in kept:
        print(f"  kept as written by hand: {path.name}")
    return written


def do_check(files: dict) -> int:
    """
    Compare against what is on disk, semantically rather than byte for byte.

    A timed recipe on disk that the generator no longer produces is stale and fails the check:
    it would load, and be Factorio's recipe from some other year.
    """
    missing, matching = [], 0
    wrong, diverged, stale = [], [], []

    expected: dict[Path, set[str]] = {}
    for mod_id, entries in sorted(files.items()):
        base = mod_resource_root(mod_id)
        for rel, obj in entries:
            path = base / rel
            expected.setdefault(path.parent, set()).add(path.name)
            if not path.exists():
                missing.append(path)
                continue
            on_disk = json.loads(path.read_text(encoding="utf-8"))
            if on_disk == obj:
                matching += 1
            elif obj["type"] != "facrafting:facraft" and timed_twin(rel).stem in HAND_FALLBACKS:
                # A fallback written by hand on purpose. Listed so it stays visible, not a failure.
                diverged.append(path)
            else:
                # Factorio's own ingredients and craft time, or a fallback derived from them. Any
                # disagreement is a defect, and catching it is the entire point of generating these.
                wrong.append((path, on_disk, obj))

    for directory, names in expected.items():
        if directory.exists():
            for path in sorted(directory.glob("*.json")):
                if path.name not in names and "facrafting:facraft" in path.read_text(encoding="utf-8") \
                        and (path.parent.parent.name, path.stem) not in PACK_OWN:
                    stale.append(path)

    print(f"  matched                {matching}")
    print(f"  missing                {len(missing)}")
    print(f"  wrong                  {len(wrong)}")
    print(f"  stale, timed recipes   {len(stale)}")
    print(f"  hand-authored fallback {len(diverged)}, allowed to differ")

    for path in diverged:
        print(f"    - {path.name}")
    for path in stale:
        print(f"    stale: {path}")

    for path, on_disk, generated in wrong:
        print(f"\n  {path}")
        for key in sorted(set(on_disk) | set(generated)):
            if on_disk.get(key) != generated.get(key):
                print(f"    {key}:")
                print(f"      on disk:   {json.dumps(on_disk.get(key))}")
                print(f"      generated: {json.dumps(generated.get(key))}")

    return 1 if wrong or stale else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="diff against what is on disk, write nothing")
    ap.add_argument("--out", type=Path, help="write the tree into a staging directory")
    ap.add_argument("--write", action="store_true", help="write into each owning mod's resources")
    ap.add_argument("--only", action="append", default=[], metavar="MODID", help="restrict to one mod")
    ap.add_argument("--all", action="store_true", help="with --write: recipes with no file on disk too")
    args = ap.parse_args()

    try:
        data = factorio_data.load()
        entries, left_out = factorio_data.entries(data)
        files, report = plan(entries, data, set(args.only) or None)
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    total = sum(len(v) for v in files.values())
    print(f"{report['generated']} recipes -> {total} files across {len(files)} mods")
    print(f"  skipped by the mapping     : {len(left_out['skipped'])}")
    print(f"  hidden in data.raw         : {len(left_out['hidden'])}")
    print(f"  no bench fallback possible : {len(report['no_fallback'])}")
    if left_out["hand_written"]:
        print(f"  probabilistic, not made    : {left_out['hand_written']}")
    if left_out["unmapped"]:
        print(f"  UNMAPPED                   : {left_out['unmapped']}")

    if args.check:
        print("\nchecking against files on disk:")
        return do_check(files)

    if args.write or args.out:
        print()
        written = do_write(files, args.out, args.all)
        print(f"\n{written} files written")

    return 0


if __name__ == "__main__":
    sys.exit(main())
