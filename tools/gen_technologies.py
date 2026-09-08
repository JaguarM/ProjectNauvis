#!/usr/bin/env python3
"""
Generate the technology tree from Factorio's tree plus the mapping table (CLAUDE.md, rule 2).

    reference/factorio/data-raw-<version>.json + data/technologies.json + data/mapping.json
        -> nauvis_research technology JSON

`data/technologies.json` is a selection: the Factorio technology names the pack has, in the order
the research screen lists them. Everything about each - prerequisites, cost or trigger, unlocks,
modifiers - is read from data.raw. A prerequisite outside the selection is dropped and named in
the summary, which is what curating the tree means: the pack has no robots, so utility science
does not wait for them.

Two files per technology: the technology, in the datapack registry `nauvis_research:technology`
at `nauvis_research/src/main/resources/data/nauvis_research/nauvis_research/technology/<name>.json`
(the doubled namespace is NeoForge's layout for a modded datapack registry), and a vanilla
advancement at `.../data/nauvis_research/advancement/<name>.json` with an `impossible` criterion
that the server awards when the world completes the technology, so the toast and sound are
vanilla's. The icon is the first unlocked item that is vanilla, else the lab, because an icon is
registry-validated and most unlocks are unregistered.

A technology is paid for by exactly one of a cost (units, packs, seconds a unit) or a trigger
(`craft-item` with an item and a count, or `mine-entity` with a resource id). Modifiers go
through whole, with `ammo_category` / `turret_id` folded into `target`; the summary counts them by
type. An unlock of a recipe the pack does not generate is named in the summary. The tree must be
bootstrappable from an empty world, or the generator fails; and a recipe Factorio gates that is
on disk and unlocked by nothing here fails `--check`, because it would be free from the first tick.

Usage:
    python tools/gen_technologies.py                summary only
    python tools/gen_technologies.py --check        semantic diff against what is on disk
    python tools/gen_technologies.py --out DIR      write the tree to a staging directory
    python tools/gen_technologies.py --write        write into nauvis_research's resources
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import factorio_data  # noqa: E402
from factorio_data import GenError, recipe_key, resolve_item, slug  # noqa: E402

REPO = factorio_data.REPO
TECHNOLOGIES = REPO / "data" / "technologies.json"

TICKS_PER_SECOND = 20

# The mod that owns research. One mod per subsystem, and the tree is research.
OWNER = "nauvis_research"

# Where a datapack registry's entries live: `data/<supplier>/<registry namespace>/<registry
# path>/`. Both halves are this mod, which reads oddly and is right.
REGISTRY_PATH = Path("data") / OWNER / OWNER / "technology"

# Vanilla advancements, in the ordinary place for them.
ADVANCEMENT_PATH = Path("data") / OWNER / "advancement"

# The advancement every technology's hangs off, so the pack gets one tab rather than twenty-five.
ROOT = f"{OWNER}:root"

# Awarded by the server; nothing a player does can earn it. One name for every advancement here,
# including the root, so the granting code needs no table.
CRITERION = "researched"

# Always registered by the mod that ships these files, so it is the safe icon.
FALLBACK_ICON = f"{OWNER}:lab"

# `order` is zero-padded so a plain string sort is a numeric one, and it is simply the selection's
# own order - the research screen then lists technologies the way Factorio lists them.
ORDER_DIGITS = 4


def display_name(factorio_id: str) -> str:
    """
    An English name for a technology, which is the one thing the tree does not carry.

    Derived by a rule stated here rather than copied in from memory: hyphens become spaces and the
    first letter is capitalised. That gives "Steel processing", "Automation 2" and "Solar energy",
    which is Factorio's own convention for almost all of them.

    It ships as a *fallback*, not as the string the screen draws. The screen asks for
    `technology.nauvis_research.<id>` first, so anything this rule gets wrong is one lang entry
    away from being right, in any language, without regenerating anything.
    """
    words = factorio_id.replace("-", " ")
    return words[:1].upper() + words[1:]


def load_inputs() -> tuple[list[str], factorio_data.Data, dict]:
    if not TECHNOLOGIES.exists():
        raise GenError(f"{TECHNOLOGIES} is missing.")
    data = factorio_data.load()
    recipes, _ = factorio_data.entries(data)
    selection = json.loads(TECHNOLOGIES.read_text(encoding="utf-8"))["technologies"]
    if len(set(selection)) != len(selection):
        raise GenError("data/technologies.json names a technology twice.")
    for name in selection:
        if name not in data.raw["technology"]:
            raise GenError(f"'{name}' is in data/technologies.json but not a technology in data.raw.")
    return selection, data, recipes


def unlocks_of(technology: dict, recipes: dict, data: factorio_data.Data, report: dict) -> list[str]:
    """The recipe keys this technology hands the player, in the order the tree lists them."""
    keys: list[str] = []
    for effect in technology.get("effects", []):
        if effect.get("type") != "unlock-recipe":
            continue
        name = effect["recipe"]
        entry = recipes.get(name)
        if entry is None:
            if name not in data.raw["recipe"] and name not in data.recipe_rows:
                raise GenError(f"'{technology['name']}' unlocks '{name}', which is not a recipe in data.raw.")
            # Skipped, hidden or unmapped: the pack does not model it, and says so in the summary.
            report["not_generated"].setdefault(name, []).append(technology["name"])
            continue
        key = recipe_key(entry)
        if key not in keys:
            keys.append(key)
    return keys


def cost_of(technology: dict, data: factorio_data.Data) -> dict:
    """
    What one technology asks for: a trigger, or units of science.

    Exactly one of the two in data.raw. A technology with a `count_formula` is one of Factorio's
    infinite ones and has no place in a tree that ends.
    """
    trigger = technology.get("research_trigger")
    unit = technology.get("unit")
    name = technology["name"]

    if trigger:
        kind = trigger.get("type")
        if kind == "craft-item":
            return {"trigger": {"item": resolve_item(trigger["item"], data), "count": trigger.get("count", 1)}}
        if kind == "mine-entity":
            # The resource's id in the mapping is what the machine reports having mined - for
            # crude oil the well block, which shares its id with the fluid.
            return {"trigger": {"mine": resolve_item(trigger["entity"], data), "count": trigger.get("count", 1)}}
        raise GenError(
            f"'{name}' has a {kind!r} trigger; the ones this pack can watch for are craft-item and mine-entity."
        )
    if not unit:
        raise GenError(f"'{name}' has neither a unit cost nor a research trigger.")
    if "count" not in unit:
        raise GenError(f"'{name}' has a count formula: it is an infinite technology, and the tree here ends.")

    seconds = unit["time"]
    ticks = seconds * TICKS_PER_SECOND
    if ticks != int(ticks):
        raise GenError(f"'{name}' takes {seconds}s a unit, which is not a whole tick.")

    packs = []
    for pack, count in unit["ingredients"]:
        if count != 1:
            # Factorio's rule is one of each pack per unit. A two would mean the lab's model is
            # wrong, not that this line needs a multiplier.
            raise GenError(f"'{name}' wants {count} of '{pack}' per unit; the lab consumes one of each.")
        packs.append(resolve_item(pack, data))
    return {"units": unit["count"], "ticks_per_unit": int(ticks), "packs": packs}


def modifier_of(effect: dict, technology_id: str) -> dict:
    """
    One effect that is not a recipe, as the research mod reads it: a type, a number, and for the
    two kinds Factorio qualifies - a damage bonus is per ammo category, a turret bonus per turret -
    the qualifier as `target`. Anything else on the effect is dropped here on purpose: the type
    names are Wube's, and a machine that reads one names the same string.
    """
    kind = effect.get("type")
    amount = effect.get("modifier")
    if not isinstance(amount, (int, float)) or isinstance(amount, bool):
        raise GenError(f"'{technology_id}' has a {kind!r} effect with no number on it.")
    out = {"type": kind, "modifier": amount}
    target = effect.get("ammo_category") or effect.get("turret_id")
    if target:
        out["target"] = target
    return out


def reachable(selection: list[str], techs: dict, recipes: dict, data: factorio_data.Data) -> set[str]:
    """
    Every technology a new world could eventually get to, walked from an empty one.

    A trigger whose item is not gated by anything is where a run starts; a technology is reached
    once its prerequisites are and, if it is triggered, once something has unlocked the item it
    watches for. Anything left over is unreachable, which is the one way this tree can be broken
    beyond repair - it looks perfectly normal in the list and can never be started.
    """
    selected = set(selection)
    gated_by: dict[str, str] = {}
    for name in selection:
        for effect in techs[name].get("effects", []):
            entry = recipes.get(effect.get("recipe", "")) if effect.get("type") == "unlock-recipe" else None
            if entry and entry["product"]:
                gated_by.setdefault(resolve_item(entry["product"], data), name)

    found: set[str] = set()
    changed = True
    while changed:
        changed = False
        for name in selection:
            if name in found:
                continue
            if any(p not in found for p in techs[name].get("prerequisites", []) if p in selected):
                continue
            trigger = techs[name].get("research_trigger")
            if trigger and trigger.get("type") == "craft-item":
                owner = gated_by.get(resolve_item(trigger["item"], data))
                if owner is not None and owner not in found:
                    continue
            found.add(name)
            changed = True
    return found


def plan(selection: list[str], data: factorio_data.Data, recipes: dict) -> tuple[list, dict]:
    techs = data.raw["technology"]
    selected = set(selection)
    report = {
        "dropped_prerequisites": {},  # technology -> the prerequisites outside the selection
        "modifiers": {},
        "not_generated": {},          # a recipe unlock the pack does not generate
        "no_unlocks": [],
        "free": [],                   # gated by Factorio, unlocked by nothing here, and on disk
    }

    unlocked: set[str] = set()
    files = []
    for index, name in enumerate(selection):
        technology = techs[name]
        prerequisites = [p for p in technology.get("prerequisites", []) if p in selected]
        dropped = [p for p in technology.get("prerequisites", []) if p not in selected]
        if dropped:
            report["dropped_prerequisites"][name] = dropped

        keys = unlocks_of(technology, recipes, data, report)
        unlocked.update(keys)
        if not keys:
            report["no_unlocks"].append(name)

        entry = {
            "name": display_name(name),
            "order": str(index).zfill(ORDER_DIGITS),
            "prerequisites": [f"{OWNER}:{slug(p)}" for p in prerequisites],
        }
        entry.update(cost_of(technology, data))
        entry["unlocks"] = keys
        modifiers = [modifier_of(e, name) for e in technology.get("effects", []) if e.get("type") != "unlock-recipe"]
        if modifiers:
            entry["modifiers"] = modifiers
            for m in modifiers:
                report["modifiers"][m["type"]] = report["modifiers"].get(m["type"], 0) + 1
        files.append((REGISTRY_PATH / f"{slug(name)}.json", entry))
        files.append((ADVANCEMENT_PATH / f"{slug(name)}.json", advancement(name, entry, recipes, data)))

    files.append((ADVANCEMENT_PATH / "root.json", root_advancement()))

    unreached = sorted(selected - reachable(selection, techs, recipes, data))
    if unreached:
        raise GenError(
            "these technologies cannot be reached from an empty world, so a save could never "
            f"research them: {unreached}. Every run starts at a trigger whose item nothing gates."
        )

    for entry in recipes.values():
        if entry["enabled"] or recipe_key(entry) in unlocked:
            continue
        on_disk = REPO / entry["owner"] / "src" / "main" / "resources" / "data" / entry["owner"] / "recipe" / f"{entry['file']}.json"
        if on_disk.exists():
            report["free"].append(entry["id"])
    return files, report


def advancement(name: str, entry: dict, recipes: dict, data: factorio_data.Data) -> dict:
    """One technology's advancement: a toast, a line in a log, and nothing else."""
    return {
        "parent": ROOT,
        "display": {
            "icon": {"id": icon_for(entry, recipes, data)},
            "title": {"translate": f"technology.{OWNER}.{slug(name)}", "fallback": entry["name"]},
            "description": {"translate": f"advancements.{OWNER}.researched"},
            "frame": "task",
            "show_toast": True,
            "announce_to_chat": False,
            # Hidden until earned, so the tab fills in as a record of what has been researched
            # rather than showing the whole tree twice - badly, since an advancement has one
            # parent and a third of these technologies have more than one prerequisite.
            "hidden": True,
        },
        "criteria": {CRITERION: {"trigger": "minecraft:impossible"}},
        "requirements": [[CRITERION]],
    }


def icon_for(entry: dict, recipes: dict, data: factorio_data.Data) -> str:
    """
    The first vanilla item this technology unlocks, or the lab.

    Deliberately not simply the first unlock. An advancement's icon is looked up in the item
    registry while the file loads, so naming something no mod has registered yet - which is most
    of what this tree unlocks - would fail the advancement rather than fall back to anything.
    A `minecraft:` id is the one kind that is certain.
    """
    by_key = {recipe_key(r): r for r in recipes.values()}
    for key in entry["unlocks"]:
        recipe = by_key.get(key)
        if recipe and recipe["product"]:
            item = resolve_item(recipe["product"], data)
            if item.startswith("minecraft:"):
                return item
    return FALLBACK_ICON


def root_advancement() -> dict:
    return {
        "display": {
            "icon": {"id": FALLBACK_ICON},
            "title": {"translate": f"advancements.{OWNER}.root.title"},
            "description": {"translate": f"advancements.{OWNER}.root.description"},
            "background": "minecraft:gui/advancements/backgrounds/stone",
            "frame": "task",
            "show_toast": False,
            "announce_to_chat": False,
        },
        "criteria": {CRITERION: {"trigger": "minecraft:impossible"}},
        "requirements": [[CRITERION]],
    }


def render(obj: dict) -> str:
    return json.dumps(obj, indent=2) + "\n"


def resource_root() -> Path:
    return REPO / OWNER / "src" / "main" / "resources"


def do_write(files: list, root: Path | None) -> int:
    base = root if root else resource_root()
    for directory in (REGISTRY_PATH, ADVANCEMENT_PATH):
        (base / directory).mkdir(parents=True, exist_ok=True)
        expected = {rel.name for rel, _ in files if rel.parent == directory}
        for stale in (base / directory).glob("*.json"):
            if stale.name not in expected:
                stale.unlink()
    for rel, obj in files:
        (base / rel).write_text(render(obj), encoding="utf-8", newline="\n")
    print(f"  {OWNER}: {len(files)} files -> {base / REGISTRY_PATH.parent}")
    return len(files)


def do_check(files: list, free: list) -> int:
    """
    Compare against what is on disk, semantically rather than byte for byte.

    Also fails on a file the generator no longer produces: a stale technology would still load,
    still show in the research list, and answer to nothing.
    """
    base = resource_root()
    missing, wrong, matching = [], [], 0
    expected = {rel.name for rel, _ in files}
    for rel, obj in files:
        path = base / rel
        if not path.exists():
            missing.append(path)
        elif json.loads(path.read_text(encoding="utf-8")) == obj:
            matching += 1
        else:
            wrong.append((path, json.loads(path.read_text(encoding="utf-8")), obj))
    stale = []
    for directory in (base / REGISTRY_PATH, base / ADVANCEMENT_PATH):
        if directory.exists():
            stale += sorted(p for p in directory.glob("*.json") if p.name not in expected)

    print(f"  matched  {matching}")
    print(f"  missing  {len(missing)}")
    print(f"  wrong    {len(wrong)}")
    print(f"  stale    {len(stale)}")
    print(f"  free     {len(free)}")
    for path in missing[:10]:
        print(f"    missing: {path.name}")
    for path in stale[:10]:
        print(f"    stale:   {path.name}")
    for path, on_disk, generated in wrong[:10]:
        print(f"\n  {path}")
        for key in sorted(set(on_disk) | set(generated)):
            if on_disk.get(key) != generated.get(key):
                print(f"    {key}:")
                print(f"      on disk:   {json.dumps(on_disk.get(key))}")
                print(f"      generated: {json.dumps(generated.get(key))}")
    return 1 if (missing or wrong or stale or free) else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="diff against what is on disk, write nothing")
    ap.add_argument("--out", type=Path, help="write the tree into a staging directory")
    ap.add_argument("--write", action="store_true", help="write into nauvis_research's resources")
    args = ap.parse_args()

    try:
        selection, data, recipes = load_inputs()
        files, report = plan(selection, data, recipes)
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    technologies = [obj for rel, obj in files if rel.parent == REGISTRY_PATH]
    triggered = sum(1 for obj in technologies if "trigger" in obj)
    total_unlocks = sum(len(obj["unlocks"]) for obj in technologies)
    print(f"{len(technologies)} technologies -> {total_unlocks} recipe unlocks, "
          f"{len(files) - len(technologies)} advancements")
    print(f"  finished by a trigger rather than by science : {triggered}")
    print(f"  unlocking nothing                            : {len(report['no_unlocks'])}")
    if report["dropped_prerequisites"]:
        dropped = ", ".join(f"{k} without {'/'.join(v)}" for k, v in sorted(report["dropped_prerequisites"].items()))
        print(f"  prerequisites outside the selection, dropped : {dropped}")
    if report["modifiers"]:
        kinds = ", ".join(f"{k} x{v}" for k, v in sorted(report["modifiers"].items()))
        print(f"  modifiers, by type, for whatever machine reads them: {kinds}")
    if report["not_generated"]:
        print(f"  recipe unlocks the pack does not generate: {', '.join(sorted(report['not_generated']))}")
    if report["free"]:
        print(f"  GATED BY FACTORIO, ON DISK, UNLOCKED BY NOTHING HERE: {report['free']}")

    if args.check:
        print("\nchecking against files on disk:")
        return do_check(files, report["free"])

    if args.write or args.out:
        print()
        do_write(files, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
