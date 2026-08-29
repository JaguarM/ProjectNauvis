#!/usr/bin/env python3
"""
Generate the technology tree from Factorio's tree plus the mapping table.

    data/technologies.json  +  data/mapping.json  ->  nauvis_research technology JSON

Non-negotiable #2, for the other generated half of the pack: a research cost is the same kind of
fact as an ingredient list. It lives in world saves and in the player's head, so it comes from
Factorio's own data and never from anybody's memory of the game.

Two files come out per technology. The first is the technology itself, in the datapack registry
`nauvis_research:technology`:

  nauvis_research/src/main/resources/data/nauvis_research/nauvis_research/technology/<name>.json

The doubled namespace is NeoForge's layout for a modded datapack registry, not a mistake: the
first is the datapack supplying the entry and the second is the registry's own namespace.

The second is a **vanilla advancement**, purely so that finishing a technology pops the toast and
plays the sound Minecraft already has for exactly this feeling:

  nauvis_research/src/main/resources/data/nauvis_research/advancement/<name>.json

It is never earned by anything the player does - its criterion is `minecraft:impossible` and the
server awards it when the *world* completes the technology. Research is per world and advancements
are per player, so these are a per-player *record* of a world fact rather than the fact itself.
They are generated here rather than written because there is one per technology and the tree
grows; a hand-kept list would be one commit behind for ever.

**The icon is the one thing that has to be careful.** An advancement's icon is registry-validated
at load, so naming an item no mod has registered yet fails the file - and most of what this tree
unlocks does not exist yet. So the icon is the first unlocked item that is *vanilla*, where there
is one, and the lab otherwise: both are certain to exist.

Two ways a technology is paid for
---------------------------------

**A cost** - N units, one of each science pack per unit, so many seconds a unit. A lab works
through it.

**Or a trigger** - *craft fifty iron plates*, *craft a lab* - and it completes the moment that
happens, with no lab and no packs at all. That is what makes the opening work: the first
technologies are triggered, so a new world researches its way to the boiler and the lab with
nothing but a pickaxe and a furnace, and only then does science become a thing you build for.

Exactly one of the two, and the generator refuses anything with both or neither.

What is dropped, and why each is a decision rather than an omission
------------------------------------------------------------------

**Effects that are not `unlock_recipes`.** Damage bonuses, laboratory speed, mining speed. Every
one of them modifies a mechanic this pack does not have yet, so the technology is written without
them rather than not written; the summary counts them by type, so the day a mechanic lands the
technologies that feed it are already there.

**Technologies whose prerequisites are not in the tree.** The tree is a graph and a dangling edge
is a technology nobody can ever start - it would sit in the list looking perfectly normal. They
are dropped transitively and named in the summary.

**Recipes the pack does not model.** A technology effect names a *recipe*, and Factorio's recipe
names are not always its item names: `small-lamp` makes a lamp, `solar-panel-equipment` makes a
portable solar panel. `mapping.json`'s `unlocks` table is where that is written down, one line per
name, and an unknown name is a `GenError` rather than a guess.

The one guard
-------------

**The tree has to be bootstrappable from an empty world.** A trigger that asks for an item no
technology has unlocked yet is where a run begins; everything else is reached from there. This
walks that graph and fails if any technology cannot be reached, which is the difference between a
tree with an awkward corner and a save file that can never research anything.

Usage:
    python tools/gen_technologies.py                summary only, writes nothing
    python tools/gen_technologies.py --check        semantic diff against what is on disk
    python tools/gen_technologies.py --out DIR      write the tree to a staging directory
    python tools/gen_technologies.py --write        write into nauvis_research's resources
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TECHNOLOGIES = REPO / "data" / "technologies.json"
MAPPING = REPO / "data" / "mapping.json"

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

# `order` is zero-padded so a plain string sort is a numeric one, and it is simply the tree's own
# order - the research screen then lists technologies the way Factorio lists them.
ORDER_DIGITS = 4


class GenError(Exception):
    """A fault in the inputs. Always fatal: a wrong tree is worse than no tree."""


def slug(factorio_id: str) -> str:
    """`steel-processing` -> `steel_processing`. Ids derive mechanically, never by hand."""
    return factorio_id.replace("-", "_")


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


def load_inputs() -> tuple[list, dict, dict]:
    for path in (TECHNOLOGIES, MAPPING):
        if not path.exists():
            raise GenError(f"{path} is missing.")

    tree = json.loads(TECHNOLOGIES.read_text(encoding="utf-8"))
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    unlocks = mapping.get("unlocks")
    if unlocks is None:
        raise GenError(
            "data/mapping.json has no `unlocks` table. It maps a Factorio recipe name to the "
            "item whose recipe file it is - see docs/MAPPING.md."
        )
    return tree, mapping["items"], unlocks


def item_id(factorio_item: str, items: dict) -> str:
    """The Minecraft item a Factorio id stands for."""
    entry = items.get(factorio_item)
    if entry is None:
        raise GenError(f"'{factorio_item}' is named by a technology but is not in the mapping table.")
    item = entry.get("item")
    if not item:
        raise GenError(f"'{factorio_item}' has no `item` in the mapping table.")
    return item


def recipe_key(factorio_item: str, items: dict) -> str | None:
    """
    The Minecraft recipe key `gen_recipes.py` writes for this Factorio item, or None.

    The two generators have to agree about this or a technology unlocks nothing, so the rule is
    stated the same way in both: the file is `data/<owner>/recipe/<item path>.json`, named after
    the *item* rather than the Factorio id, because a released mod's item name is permanent and
    the dump's is not - Neo Progressive Automation ships `burner_drill` for `burner-mining-drill`.

    None when the pack deliberately has no recipe for it: an entry the mapping marks `skip`, or
    one with no recipe at all (an ore, a fluid, a filled barrel).
    """
    entry = items.get(factorio_item)
    if entry is None:
        raise GenError(f"'{factorio_item}' is named by a technology but is not in the mapping table.")
    if entry.get("skip") or entry.get("raw"):
        return None
    owner = entry.get("owner")
    if not owner:
        raise GenError(f"'{factorio_item}' has no `owner` in the mapping table.")
    return f"{owner}:{item_id(factorio_item, items).split(':', 1)[1]}"


def unlocks_of(technology: dict, items: dict, aliases: dict, report: dict) -> list[str]:
    """The recipe keys this technology hands the player, in the order the tree lists them."""
    keys: list[str] = []
    for name in technology.get("unlock_recipes", []):
        if name in aliases:
            # An alias, because Factorio's recipe name is not its item name here. A null says the
            # pack does not model this recipe at all.
            target = aliases[name]
            if target is None:
                report["unmodelled"].setdefault(name, []).append(technology["id"])
                continue
        elif name in items:
            target = name
        else:
            raise GenError(
                f"'{technology['id']}' unlocks the recipe '{name}', which is neither an item in "
                f"the mapping table nor a line in its `unlocks` table. Add it there - with null "
                f"if the pack does not model that recipe - rather than letting it vanish."
            )

        key = recipe_key(target, items)
        if key is None:
            report["not_registered"].setdefault(name, []).append(technology["id"])
            continue
        if key not in keys:
            keys.append(key)
    return keys


def cost_of(technology: dict, items: dict) -> dict:
    """
    What one technology asks for: a trigger, or units of science.

    Exactly one of the two. A technology with both would have two answers to "am I done", and one
    with neither could never be finished at all.
    """
    trigger = technology.get("research_trigger")
    cost = technology.get("cost")

    if trigger and cost:
        raise GenError(f"'{technology['id']}' has both a cost and a research trigger.")
    if not trigger and not cost:
        raise GenError(f"'{technology['id']}' has neither a cost nor a research trigger.")

    if trigger:
        if trigger.get("type") != "craft-item":
            raise GenError(
                f"'{technology['id']}' has a {trigger.get('type')!r} trigger; the only one this "
                "pack can watch for is craft-item."
            )
        return {"trigger": {
            "item": item_id(trigger["item"], items),
            "count": trigger.get("count", 1),
        }}

    seconds = cost["time_seconds"]
    ticks = seconds * TICKS_PER_SECOND
    if ticks != int(ticks):
        raise GenError(f"'{technology['id']}' takes {seconds}s a unit, which is not a whole tick.")

    packs = []
    for ingredient in cost["ingredients"]:
        if ingredient["count"] != 1:
            # Factorio's rule is one of each pack per unit. A two would mean the lab's model is
            # wrong, not that this line needs a multiplier.
            raise GenError(
                f"'{technology['id']}' wants {ingredient['count']} of "
                f"'{ingredient['science_pack']}' per unit; the lab consumes one of each and "
                "nothing here can express more."
            )
        packs.append(item_id(ingredient["science_pack"], items))

    return {"units": cost["count"], "ticks_per_unit": int(ticks), "packs": packs}


def reachable(tree: list, items: dict, aliases: dict) -> set[str]:
    """
    Every technology a new world could eventually get to, walked from an empty one.

    A trigger whose item is not gated by anything is where a run starts; a technology is reached
    once its prerequisites are and, if it is triggered, once something has unlocked the item it
    watches for. Anything left over is unreachable, which is the one way this tree can be broken
    beyond repair - it looks perfectly normal in the list and can never be started.
    """
    gated_by: dict[str, str] = {}
    for technology in tree:
        for name in technology.get("unlock_recipes", []):
            target = aliases.get(name, name)
            # A skipped entry has no item at all - those are the ones the pack never registers.
            if target is not None and items.get(target, {}).get("item"):
                gated_by.setdefault(items[target]["item"], technology["id"])

    found: set[str] = set()
    changed = True
    while changed:
        changed = False
        for technology in tree:
            if technology["id"] in found:
                continue
            if any(p not in found for p in technology.get("prerequisites", [])):
                continue
            trigger = technology.get("research_trigger")
            if trigger:
                item = items.get(trigger["item"], {}).get("item")
                owner = gated_by.get(item)
                if owner is not None and owner not in found:
                    continue
            found.add(technology["id"])
            changed = True
    return found


def plan(tree: list, items: dict, aliases: dict) -> tuple[list, dict]:
    report = {
        "dropped": [],
        "modifiers": {},
        "unmodelled": {},      # a recipe the pack has no counterpart for
        "not_registered": {},  # an item the mapping marks skip or raw
        "no_unlocks": [],
    }

    for technology in tree:
        for modifier in technology.get("modifiers", []):
            kind = modifier.get("type")
            report["modifiers"][kind] = report["modifiers"].get(kind, 0) + 1

    # Drop anything whose prerequisites are not all present, transitively. A dangling edge is a
    # technology nobody can ever start.
    kept = {t["id"]: t for t in tree}
    while True:
        dangling = [i for i, t in kept.items()
                    if any(p not in kept for p in t.get("prerequisites", []))]
        if not dangling:
            break
        for i in dangling:
            report["dropped"].append(i)
            del kept[i]

    order = {t["id"]: i for i, t in enumerate(tree)}
    files = []
    for technology in sorted(kept.values(), key=lambda t: order[t["id"]]):
        recipes = unlocks_of(technology, items, aliases, report)
        if not recipes:
            report["no_unlocks"].append(technology["id"])

        entry = {
            "name": display_name(technology["id"]),
            "order": str(order[technology["id"]]).zfill(ORDER_DIGITS),
            "prerequisites": [f"{OWNER}:{slug(p)}" for p in technology.get("prerequisites", [])],
        }
        entry.update(cost_of(technology, items))
        entry["unlocks"] = recipes
        files.append((REGISTRY_PATH / f"{slug(technology['id'])}.json", entry))
        files.append((ADVANCEMENT_PATH / f"{slug(technology['id'])}.json",
                      advancement(technology["id"], entry, items, tree)))

    files.append((ADVANCEMENT_PATH / "root.json", root_advancement()))

    unreached = sorted(set(kept) - reachable(list(kept.values()), items, aliases))
    if unreached:
        raise GenError(
            "these technologies cannot be reached from an empty world, so a save could never "
            f"research them: {unreached}. Every run starts at a trigger whose item nothing gates."
        )

    return files, report


def advancement(name: str, entry: dict, items: dict, tree: list) -> dict:
    """One technology's advancement: a toast, a line in a log, and nothing else."""
    return {
        "parent": ROOT,
        "display": {
            "icon": {"id": icon_for(entry, items)},
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


def icon_for(entry: dict, items: dict) -> str:
    """
    The first vanilla item this technology unlocks, or the lab.

    Deliberately not simply the first unlock. An advancement's icon is looked up in the item
    registry while the file loads, so naming something no mod has registered yet - which is most
    of what this tree unlocks - would fail the advancement rather than fall back to anything.
    A `minecraft:` id is the one kind that is certain.
    """
    for key in entry["unlocks"]:
        for factorio_id, mapped in items.items():
            item = mapped.get("item", "")
            if item.startswith("minecraft:") and key == recipe_key(factorio_id, items):
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


def do_check(files: list) -> int:
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

    return 1 if (missing or wrong or stale) else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="diff against what is on disk, write nothing")
    ap.add_argument("--out", type=Path, help="write the tree into a staging directory")
    ap.add_argument("--write", action="store_true", help="write into nauvis_research's resources")
    args = ap.parse_args()

    try:
        tree, items, aliases = load_inputs()
        files, report = plan(tree, items, aliases)
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    technologies = [obj for rel, obj in files if rel.parent == REGISTRY_PATH]
    triggered = sum(1 for obj in technologies if "trigger" in obj)
    total_unlocks = sum(len(obj["unlocks"]) for obj in technologies)
    print(f"{len(technologies)} technologies -> {total_unlocks} recipe unlocks, "
          f"{len(files) - len(technologies)} advancements")
    print(f"  finished by a trigger rather than by science : {triggered}")
    print(f"  unlocking nothing yet                        : {len(report['no_unlocks'])}")
    report["no_unlocks"] = [x for x in report["no_unlocks"]]
    if report["dropped"]:
        print(f"  dropped, prerequisites not in the tree       : {report['dropped']}")
    if report["modifiers"]:
        kinds = ", ".join(f"{k} x{v}" for k, v in sorted(report["modifiers"].items()))
        print(f"  effects this pack has no mechanic for: {kinds}")
    for label, bucket in (("recipes the pack does not model", report["unmodelled"]),
                          ("items the mapping skips or calls raw", report["not_registered"])):
        if bucket:
            print(f"  {label}: {', '.join(sorted(bucket))}")

    if args.check:
        print("\nchecking against files on disk:")
        return do_check(files)

    if args.write or args.out:
        print()
        do_write(files, args.out)

    return 0


if __name__ == "__main__":
    sys.exit(main())
