#!/usr/bin/env python3
"""
Generate the technology tree from Factorio's technology dump plus the mapping table.

    reference/factorio/technologies.json  +  data/mapping.json  ->  nauvis_research technology JSON

Non-negotiable #2 in the other half of the pack: recipes are generated, never hand-written, and
a research cost is the same kind of fact as an ingredient list. It lives in world saves and in
the player's head, so it comes from Wube's own data - see `fetch_technologies.py` for where the
dump comes from and why it is pinned to 0.16.51 - and never from anybody's memory of the game.

One file comes out per technology, in the datapack registry `nauvis_research:technology`:

  nauvis_research/src/main/resources/data/nauvis_research/nauvis_research/technology/<name>.json

The doubled namespace is NeoForge's layout for a modded datapack registry, not a mistake: the
first is the datapack supplying the entry and the second is the registry's own namespace.

What is dropped, and why each is a decision rather than an omission
------------------------------------------------------------------

**Effects that are not `unlock-recipe`.** Sixty-one ammo damage bonuses, six laboratory speed
multipliers, an artillery range. Every one of them modifies a mechanic this pack does not have
yet, so the technology is written without them rather than not written; the summary counts them
by type, so the day a mechanic lands the technologies that feed it are already there.

**Technologies priced by a formula.** Eighteen of them - the fourteen infinite ones and the four
levelled mining-productivity steps - give `unit` a `count_formula` such as `2^(L-6)*1000` instead
of a count, so "how many units" has no answer until a technology can have a level. Not one of
them unlocks a recipe, which is why the tree loses nothing by leaving them out; they are named in
the summary rather than dropped quietly.

**Recipes the pack does not model.** A technology effect names a *recipe*, and Factorio's recipe
names are not always its item names: `small-lamp` makes a lamp, `solar-panel-equipment` makes a
portable solar panel, and the five oil-processing recipes make fluids this pack's dump has no
recipe for at all. `mapping.json`'s `unlocks` table is where that is written down, one line per
name, and an unknown name is a `GenError` rather than a guess.

**Nothing else.** In particular a technology whose every unlock is an item no mod registers yet
is still written. The tree is identity: its shape, its costs and its prerequisites are the same
whether or not the pack has caught up with it, and a technology that appears later would move
under a player who had already researched past it.

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
TECHNOLOGIES = REPO / "reference" / "factorio" / "technologies.json"
RECIPES = REPO / "reference" / "factorio" / "recipes.json"
MAPPING = REPO / "data" / "mapping.json"

TICKS_PER_SECOND = 20

# The mod that owns research. One mod per subsystem, and the tree is research.
OWNER = "nauvis_research"

# Where a datapack registry's entries live: `data/<supplier>/<registry namespace>/<registry
# path>/`. Both halves are this mod, which reads oddly and is right.
REGISTRY_PATH = Path("data") / OWNER / OWNER / "technology"


class GenError(Exception):
    """A fault in the inputs. Always fatal: a wrong tree is worse than no tree."""


def slug(factorio_id: str) -> str:
    """`steel-processing` -> `steel_processing`. Ids derive mechanically, never by hand."""
    return factorio_id.replace("-", "_")


def display_name(factorio_id: str) -> str:
    """
    An English name for a technology, which is the one thing the source does not carry.

    Wube's data repository ships no locale files at any tag, so `technology-name.steel-processing`
    resolves to nothing outside the game. Rather than copy names in from memory - which is exactly
    what the dumps exist to prevent - the name is derived by a rule stated here: hyphens become
    spaces and the first letter is capitalised. That gives "Steel processing", "Logistics 2" and
    "Solar energy", which is Factorio's own convention for almost all of them.

    It ships as a *fallback*, not as the string the screen draws. The screen asks for
    `technology.nauvis_research.<id>` first, so anything this rule gets wrong is one lang entry
    away from being right, in any language, without regenerating anything.
    """
    words = factorio_id.replace("-", " ")
    return words[:1].upper() + words[1:]


def load_inputs() -> tuple[list, dict, dict, dict]:
    for path in (TECHNOLOGIES, RECIPES):
        if not path.exists():
            raise GenError(
                f"{path} is missing. It is Wube's data, gitignored on purpose - see "
                "reference/README.md. `python tools/fetch_technologies.py` writes the technology half."
            )
    if not MAPPING.exists():
        raise GenError(f"{MAPPING} is missing.")

    technologies = json.loads(TECHNOLOGIES.read_text(encoding="utf-8"))
    recipes = {e["id"]: e for e in json.loads(RECIPES.read_text(encoding="utf-8"))}
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    items = mapping["items"]
    unlocks = mapping.get("unlocks")
    if unlocks is None:
        raise GenError(
            "data/mapping.json has no `unlocks` table. It maps a Factorio recipe name to the "
            "item whose recipe file it is - see docs/MAPPING.md."
        )
    return technologies, recipes, items, unlocks


def recipe_key(factorio_item: str, items: dict) -> str | None:
    """
    The Minecraft recipe key `gen_recipes.py` writes for this Factorio item, or None.

    The two generators have to agree about this or a technology unlocks nothing, so the rule is
    stated the same way in both: the file is `data/<owner>/recipe/<item path>.json`, named after
    the *item* rather than the Factorio id, because a released mod's item name is permanent and
    the dump's is not - Neo Progressive Automation ships `burner_drill` for `burner-mining-drill`.

    None when the pack deliberately has no recipe for it: an entry the mapping marks `skip`, or
    one the dump gives no recipe at all (an ore, a fluid, a filled barrel).
    """
    entry = items.get(factorio_item)
    if entry is None:
        raise GenError(f"'{factorio_item}' is named by a technology but is not in the mapping table.")
    if entry.get("skip") or entry.get("raw"):
        return None
    owner = entry.get("owner")
    item = entry.get("item")
    if not owner or not item:
        raise GenError(f"'{factorio_item}' has no `owner` or no `item` in the mapping table.")
    return f"{owner}:{item.split(':', 1)[1]}"


def unlocks_of(technology: dict, items: dict, unlocks: dict, report: dict) -> list[str]:
    """The recipe keys this technology hands the player, in the order Factorio lists them."""
    keys: list[str] = []
    for effect in technology.get("effects", []):
        kind = effect.get("type")
        if kind != "unlock-recipe":
            report["other_effects"][kind] = report["other_effects"].get(kind, 0) + 1
            continue

        name = effect["recipe"]
        if name in unlocks:
            # An alias, because Factorio's recipe name is not its item name here. A null says
            # the pack does not model this recipe at all - the oil-processing recipes, whose
            # products the recipe dump carries as raw fluids with no recipe of their own.
            target = unlocks[name]
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


def packs_of(technology: dict, items: dict) -> list[str]:
    """The science packs one unit of this research consumes, one of each."""
    packs = []
    for ingredient in technology["unit"]["ingredients"]:
        if ingredient["amount"] != 1:
            # Factorio's rule is one of each pack per unit, and every one of the 798 unit
            # ingredients in 0.16 says so. A two would mean the lab's model is wrong, not that
            # this line needs a multiplier.
            raise GenError(
                f"'{technology['id']}' wants {ingredient['amount']} of '{ingredient['id']}' per "
                "unit; the lab consumes one of each and nothing here can express more."
            )
        entry = items.get(ingredient["id"])
        if entry is None:
            raise GenError(f"'{technology['id']}' wants the pack '{ingredient['id']}', which is not mapped.")
        packs.append(entry["item"])
    return packs


def ticks_per_unit(technology: dict) -> int:
    seconds = technology["unit"]["time"]
    ticks = seconds * TICKS_PER_SECOND
    if ticks != int(ticks):
        raise GenError(f"'{technology['id']}' takes {seconds}s a unit, which is not a whole tick.")
    return int(ticks)


def plan(technologies: list, items: dict, unlocks: dict) -> tuple[list, dict]:
    report = {
        "formula_priced": [],
        "other_effects": {},
        "unmodelled": {},      # a recipe the pack has no counterpart for
        "not_registered": {},  # an item the mapping marks skip or raw
        "no_unlocks": [],
    }

    # A technology is priced in units, and one priced by a formula over its own level is not
    # something this tree can hold - see the module docstring. The test is the absence of
    # `count` rather than `max_level == "infinite"`, because the four levelled
    # mining-productivity steps are finite and priced the same way.
    kept = {t["id"] for t in technologies if "count" in t["unit"]}
    files = []

    for technology in sorted(technologies, key=lambda t: t["id"]):
        if "count" not in technology["unit"]:
            report["formula_priced"].append(technology["id"])
            continue

        prerequisites = technology.get("prerequisites", [])
        missing = [p for p in prerequisites if p not in kept]
        if missing:
            # A prerequisite that is not being written would make this technology unreachable,
            # which is worse than not having it: it shows in the list and can never be started.
            raise GenError(
                f"'{technology['id']}' needs {missing}, which is not in the generated tree. "
                "Either that one is priced by a formula and this one should be too, or the dump "
                "is incomplete - `fetch_technologies.py` names what it could not read."
            )

        recipes = unlocks_of(technology, items, unlocks, report)
        if not recipes:
            report["no_unlocks"].append(technology["id"])

        entry = {
            "name": display_name(technology["id"]),
            "order": technology["order"],
            "prerequisites": [f"{OWNER}:{slug(p)}" for p in prerequisites],
            "units": technology["unit"]["count"],
            "ticks_per_unit": ticks_per_unit(technology),
            "packs": packs_of(technology, items),
            "unlocks": recipes,
        }
        files.append((REGISTRY_PATH / f"{slug(technology['id'])}.json", entry))

    return files, report


def render(obj: dict) -> str:
    return json.dumps(obj, indent=2) + "\n"


def resource_root() -> Path:
    return REPO / OWNER / "src" / "main" / "resources"


def do_write(files: list, root: Path | None) -> int:
    base = root if root else resource_root()
    for rel, obj in files:
        path = base / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(render(obj), encoding="utf-8", newline="\n")
    print(f"  {OWNER}: {len(files)} files -> {base / REGISTRY_PATH}")
    return len(files)


def do_check(files: list) -> int:
    """
    Compare against what is on disk, semantically rather than byte for byte.

    Unlike the recipe check there is no hand-authored half to allow for: every technology here
    is generated, so any disagreement at all is a defect.
    """
    base = resource_root()
    missing, wrong, matching = [], [], 0
    expected = {rel.name for rel, _ in files}

    for rel, obj in files:
        path = base / rel
        if not path.exists():
            missing.append(path)
            continue
        on_disk = json.loads(path.read_text(encoding="utf-8"))
        if on_disk == obj:
            matching += 1
        else:
            wrong.append((path, on_disk, obj))

    # A file the generator no longer produces is as wrong as one it produces differently: it is
    # a technology that will still load, still show in the list, and answer to nothing.
    stale = []
    directory = base / REGISTRY_PATH
    if directory.exists():
        stale = sorted(p for p in directory.glob("*.json") if p.name not in expected)

    print(f"  matched  {matching}")
    print(f"  missing  {len(missing)}")
    print(f"  wrong    {len(wrong)}")
    print(f"  stale    {len(stale)}")

    for path in missing[:10]:
        print(f"    missing: {path.name}")
    for path in stale:
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
        technologies, _recipes, items, unlocks = load_inputs()
        files, report = plan(technologies, items, unlocks)
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    total_unlocks = sum(len(obj["unlocks"]) for _, obj in files)
    print(f"{len(files)} technologies -> {total_unlocks} recipe unlocks")
    print(f"  priced by a formula, not written : {len(report['formula_priced'])}")
    print(f"  unlocking nothing yet            : {len(report['no_unlocks'])}")
    if report["other_effects"]:
        kinds = ", ".join(f"{k} x{v}" for k, v in sorted(report["other_effects"].items()))
        print(f"  effects this pack has no mechanic for: {kinds}")
    if report["unmodelled"]:
        print(f"  recipes the pack does not model      : {len(report['unmodelled'])}")
        for name in sorted(report["unmodelled"]):
            print(f"    - {name}")
    if report["not_registered"]:
        print(f"  items the mapping skips or calls raw : {len(report['not_registered'])}")
        for name in sorted(report["not_registered"]):
            print(f"    - {name}")

    if args.check:
        print("\nchecking against files on disk:")
        return do_check(files)

    if args.write or args.out:
        print()
        written = do_write(files, args.out)
        print(f"\n{written} files written")

    return 0


if __name__ == "__main__":
    sys.exit(main())
