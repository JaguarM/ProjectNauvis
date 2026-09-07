#!/usr/bin/env python3
"""
Generate recipe JSON from Factorio's recipe dump plus the mapping table.

    reference/factorio/recipes.json  +  data/mapping.json  ->  recipe JSON per owning mod

Non-negotiable #2: recipes are generated, never hand-written. Ingredient lists, counts and
craft times come straight from the dump, so nothing in the pack is balanced by hand.

Up to three files come out per craftable item, matching the shape Neo Progressive Materials
already ships. `<ns>` is the owning mod and `<name>` is the item the recipe produces:

  data/<ns>/recipe/<name>.json                 facrafting:facraft, timed, the real recipe.
                                               Always generated.
  data/<ns>/recipe/<name>_standalone.json      minecraft:crafting_shapeless, for playing the
                                               mod without Facrafting. Only when the
                                               ingredients fit nine grid slots.
  crafting_table/data/<ns>/recipe/<name>.json  The same shapeless recipe, in the optional
                                               built-in datapack that ships disabled, so a
                                               player can put bench crafting back if they
                                               want it.

Conditions are derived, not fixed: every recipe requires `facrafting`, plus `mod_loaded` for
any other mod supplying an ingredient. The standalone copy negates only the Facrafting
clause, so it appears when Facrafting is absent but still requires the mods its ingredients
come from.

A recipe the mapping marks with a `category` is a machine's, not the hand's: Factorio's four
smelting recipes - the plates, steel and stone brick - are `smelting`, and the character cannot
craft them. The facraft recipe carries the category and Facrafting keeps it out of the hand
panel; a furnace runs it. Its bench fallback is a vanilla furnace recipe where one can express it
- one ingredient, one at a time - and the ordinary shapeless copy where it cannot, because a bench
is the fallback for a world with no Facrafting and steel has to come from somewhere there.

Craft times need no rounding: every time in the dump is a whole number of ticks once
multiplied by 20, from 5 (0.25s) to 6000 (300s), and Facrafting accepts 1..12000.

Four kinds of entry produce nothing, and the summary counts each: items the mapping marks
`skip`, items the dump gives no recipe (ores, fluids, filled barrels), recipes too large for
a bench (the facraft recipe is still written, only the fallback is skipped), and
`uranium-processing`, whose probabilistic output no crafting recipe can express.

Fluids
------

The dump names fluids as ingredients and as products - plastic is coal and petroleum gas,
lubricant is heavy oil - and `data/fluid_recipes.json` carries the recipes the dump cannot,
the ones that make several fluids at once or one thing several ways. An ingredient whose dump
entry is a `Liquid` becomes a `fluid_ingredient`, a product that is one becomes a
`fluid_result`, and a recipe with any fluid in it gets no bench fallback, because a bench has
no pipes. The fluid recipes are named after the recipe rather than the product, which is why
they exist as a separate file: `solid-fuel-from-heavy-oil` is not an item.

Usage:
    python tools/gen_recipes.py                     summary only, writes nothing
    python tools/gen_recipes.py --check             semantic diff against what is on disk
    python tools/gen_recipes.py --out DIR           write the tree to a staging directory
    python tools/gen_recipes.py --write             write into each owning mod's resources
    python tools/gen_recipes.py --only MODID        restrict to one mod (repeatable)
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RECIPES = REPO / "reference" / "factorio" / "recipes.json"
MAPPING = REPO / "data" / "mapping.json"
FLUID_RECIPES = REPO / "data" / "fluid_recipes.json"

TICKS_PER_SECOND = 20

# A vanilla crafting grid is three by three. Anything asking for more than nine items total
# cannot be expressed as a shapeless recipe at any count, so it gets no standalone fallback.
GRID_SLOTS = 9

# Facrafting's `group` is a free-form field the recipe panel uses to lay out its tab strip,
# so it should carry Factorio's *crafting menu* tabs and nothing else. The menu has four,
# and the dump names them in `category`. Deriving the group from `type` instead - which this
# once did - produces eleven groups that are Factorio's item taxonomy rather than anything a
# player has ever clicked on.
#
# Every entry in the dump carries one of these four. The 214th, `deconstruction-planner`, is
# `category_TODO` in the dump and marked `skip` in the mapping, so it never reaches here.
#
# **The order of this table is Factorio's tab strip, and it is load-bearing.** Factorio's menu
# reads Logistics, Production, Intermediate products, Combat - not alphabetically, and not in
# the order the dump happens to list categories. It is written down here rather than in
# Facrafting because it is a fact about Factorio, and Facrafting knows nothing about Factorio.
GROUP_BY_CATEGORY = {
    "Logistics": "logistics",
    "Production": "production",
    "Intermediate product": "intermediate",
    "Combat": "combat",
}

# `order` is zero-padded so a plain string sort is a numeric one, and four digits leaves room
# for a dump several times this size.
ORDER_DIGITS = 4

# The machine categories the mapping and the fluid recipes may name. Factorio's list is longer
# - centrifuging, crafting-with-fluid - and each joins this set the day a machine runs it; an
# unknown one is a typo until then, and a typo here is a recipe that quietly vanishes from every
# panel.
CATEGORIES = {"smelting", "chemistry", "oil-processing", "crafting-with-fluid", "rocket-building"}

# The three released mods live in their own repos beside this one; everything else is a
# subproject here. Both are resolved to a `src/main/resources` root.
SIBLING_REPOS = {
    "facrafting": "Facrafting",
    "neoprogressivematerials": "NeoProgressiveMaterials",
    "crumblingore": "CrumblingOre",
}


class GenError(Exception):
    """A fault in the inputs. Always fatal: a wrong recipe is worse than no recipe."""


def slug(factorio_id: str) -> str:
    """`iron-gear-wheel` -> `iron_gear_wheel`. Ids derive mechanically, never by hand."""
    return factorio_id.replace("-", "_")


def mod_resource_root(mod_id: str) -> Path:
    if mod_id in SIBLING_REPOS:
        return REPO.parent / SIBLING_REPOS[mod_id] / "src" / "main" / "resources"
    return REPO / mod_id / "src" / "main" / "resources"


def load_inputs() -> tuple[dict, list, dict]:
    if not RECIPES.exists():
        raise GenError(
            f"{RECIPES} is missing. It is Wube's data, gitignored on purpose - see reference/README.md."
        )
    if not MAPPING.exists():
        raise GenError(f"{MAPPING} is missing.")
    if not FLUID_RECIPES.exists():
        raise GenError(f"{FLUID_RECIPES} is missing.")

    entries = json.loads(RECIPES.read_text(encoding="utf-8"))
    fluid_recipes = fluid_entries(json.loads(FLUID_RECIPES.read_text(encoding="utf-8")))
    # Numbered together, so the refinery's recipes sit among the intermediates in the panel.
    stamp_order(entries + fluid_recipes)

    dump = {e["id"]: e for e in entries}
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))["items"]
    return dump, fluid_recipes, mapping


def fluid_entries(raw: dict) -> list[dict]:
    """
    The fluid recipes in the dump's shape, so that everything downstream reads one kind of entry.

    A dump entry is a product with a recipe; one of these is a recipe with products. The
    difference is `results`, which a dump entry does not have, and `owner` and `machine`, which
    it gets from the mapping instead.
    """
    entries = []
    for recipe in raw["recipes"]:
        for field in ("id", "name", "owner", "category", "time", "ingredients", "results"):
            if field not in recipe:
                raise GenError(f"fluid recipe {recipe.get('id', '?')!r} has no {field!r}.")
        entries.append({
            "id": recipe["id"],
            "name": recipe["name"],
            "type": "Recipe",
            "category": "Intermediate product",
            "recipe": {"time": recipe["time"], "yield": None, "ingredients": recipe["ingredients"]},
            "results": recipe["results"],
            "owner": recipe["owner"],
            "machine": recipe["category"],
        })
    return entries


def is_fluid(factorio_id: str, dump: dict) -> bool:
    """Whether the dump calls this a liquid: water, steam, and everything the refinery makes."""
    return dump.get(factorio_id, {}).get("type") == "Liquid"


def products_of(entry: dict) -> list[dict]:
    """What a recipe makes: a dump entry makes itself, a fluid recipe says."""
    if "results" in entry:
        return entry["results"]
    return [{"id": entry["id"], "amount": entry["recipe"]["yield"]}]


def owner_of(entry: dict, mapping: dict) -> str:
    """The mod whose resources the recipe file goes in."""
    if "owner" in entry:
        return entry["owner"]
    owner = mapping[entry["id"]].get("owner")
    if not owner:
        raise GenError(f"'{entry['id']}' has no `owner` in the mapping table.")
    return owner


def machine_category(entry: dict, mapping: dict) -> str | None:
    """The machine category the recipe belongs to, or None for one the hand crafts."""
    if "machine" in entry:
        category = entry["machine"]
        if category not in CATEGORIES:
            raise GenError(
                f"'{entry['id']}' has category {category!r}; the ones a machine here runs are "
                f"{', '.join(sorted(CATEGORIES))}."
            )
        return category
    return category_of(mapping[entry["id"]], entry["id"])


def stamp_order(entries: list[dict]) -> None:
    """
    Give every entry the sort key the crafting panel lays itself out with.

    One number decides two things, which is the point. Factorio's own `order` strings are a
    single global sequence that runs group by group, so the tab strip and the grid inside a tab
    are the same ordering read at two depths - a group's place is simply where its first item
    falls. Ours does the same: sort every entry by its tab, then within a tab, and number the
    result. Facrafting then needs no notion of "which tab comes first"; it sorts tabs by the
    earliest recipe in each and gets Factorio's strip for free.

    Within a tab the tiebreak is the display name, because that is genuinely all the dump has.
    Factorio orders within a tab by subgroup and then by a per-item order string, and the dump
    carries neither - so this half is a stand-in and is the thing a `technologies.json`-style
    dump of item order would replace. **If such a dump arrives, this function is what changes,
    and nothing downstream of it does.**
    """
    tabs = list(GROUP_BY_CATEGORY)
    # An unknown category sorts last rather than raising here: `plan` has not yet had the
    # chance to skip it, and `group_of` is where a category that actually reaches a recipe is
    # refused. Today that is `deconstruction-planner`, which the mapping marks skip.
    for index, entry in enumerate(sorted(
            entries,
            key=lambda e: (tabs.index(e["category"]) if e.get("category") in tabs else len(tabs),
                           e["name"]))):
        entry["order"] = str(index).zfill(ORDER_DIGITS)


def resolve_item(factorio_id: str, mapping: dict) -> str:
    """Factorio ingredient id -> the Minecraft item id standing in for it."""
    entry = mapping.get(factorio_id)
    if entry is None:
        raise GenError(f"'{factorio_id}' appears as an ingredient but is not in the mapping table.")
    if entry.get("skip"):
        raise GenError(
            f"'{factorio_id}' is marked skip in the mapping but is needed as an ingredient."
        )
    item = entry.get("item")
    if not item:
        raise GenError(f"'{factorio_id}' has no `item` in the mapping table.")
    return item


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


def category_of(mapped: dict, factorio_id: str) -> str | None:
    """The machine category the mapping gives this recipe, or None for one the hand crafts."""
    category = mapped.get("category")
    if category is None:
        return None
    if category not in CATEGORIES:
        raise GenError(
            f"'{factorio_id}' has category {category!r}; the ones a machine here runs are "
            f"{', '.join(sorted(CATEGORIES))}."
        )
    return category


def group_of(entry: dict) -> str:
    """The crafting-menu tab this recipe belongs on."""
    category = entry.get("category")
    group = GROUP_BY_CATEGORY.get(category)
    if group is None:
        # Quietly emitting "" here would put the item in the panel's "Ungrouped" tab, which
        # looks like a layout choice rather than a stale table. A new Factorio category is a
        # fifth tab and a lang key, and both are decisions.
        raise GenError(
            f"'{entry['id']}' has category {category!r}, which is not one of Factorio's four "
            f"crafting-menu tabs: {', '.join(sorted(GROUP_BY_CATEGORY))}."
        )
    return group


def required_mods(entry: dict, mapping: dict) -> list[str]:
    """
    Which mods have to be present for this recipe to mean anything.

    Facrafting always, plus any mod other than this one supplying an ingredient or taking a
    product - a recipe naming `neoprogressivematerials:iron_gear_wheel` is nonsense without that
    mod loaded, and so is one making `nauvis:solid_fuel`. Arrows point one way, so the file's
    own namespace is never listed.
    """
    own = owner_of(entry, mapping)
    foreign = {
        resolve_item(i["id"], mapping).split(":")[0]
        for i in entry["recipe"]["ingredients"] + products_of(entry)
    }
    foreign.discard("minecraft")
    foreign.discard(own)
    return ["facrafting"] + sorted(foreign)


def mod_loaded(mod_id: str) -> dict:
    return {"type": "neoforge:mod_loaded", "modid": mod_id}


def registered(item_id: str) -> dict:
    """A condition that holds once something registers this item, and not before."""
    return {"type": "neoforge:registered", "value": item_id}


def pending_ingredients(entry: dict, mapping: dict) -> list[dict]:
    """
    Conditions for ingredients whose mod is here but whose item is not written yet.

    A recipe naming an unregistered item is a load error, which is why the pack copies across
    only the recipes whose items exist. That rule alone would hold back the *whole* recipe for
    an item that is finished and craftable except for one ingredient that is not - the lab is
    three tiles by three, done, and paid for in four transport belts that belong to a milestone
    which has not happened.

    `neoforge:registered` is the way out. The recipe ships, correct and complete, and simply does
    not load until whatever it names exists. Nothing has to be edited when belts arrive: the
    `pending` flag comes off the mapping entry, the recipe regenerates without the condition, and
    `checkRecipes` fails until it has been.

    Mark an entry `"pending": true` when the mapping names an id nothing registers yet.
    """
    conditions = []
    for ingredient in entry["recipe"]["ingredients"]:
        target = mapping.get(ingredient["id"], {})
        if target.get("pending"):
            conditions.append(registered(resolve_item(ingredient["id"], mapping)))
    return conditions


def facraft_recipe(entry: dict, mapping: dict, dump: dict) -> dict:
    """The real recipe: timed, sized ingredients, fluids where the dump says so, no grid."""
    recipe = entry["recipe"]
    out = {
        "neoforge:conditions": [mod_loaded(m) for m in required_mods(entry, mapping)]
        + pending_ingredients(entry, mapping),
        "type": "facrafting:facraft",
        "craft_ticks": craft_ticks(recipe["time"], entry["id"]),
        "group": group_of(entry),
        "order": entry["order"],
    }
    category = machine_category(entry, mapping)
    if category:
        out["category"] = category

    out["ingredients"] = [
        {"ingredient": resolve_item(i["id"], mapping), "count": i["amount"]}
        for i in recipe["ingredients"] if not is_fluid(i["id"], dump)
    ]
    fluids_in = [
        {"ingredient": resolve_item(i["id"], mapping), "amount": i["amount"]}
        for i in recipe["ingredients"] if is_fluid(i["id"], dump)
    ]
    if fluids_in:
        out["fluid_ingredients"] = fluids_in

    products = products_of(entry)
    item_results = [r for r in products if not is_fluid(r["id"], dump)]
    fluid_results = [r for r in products if is_fluid(r["id"], dump)]
    if len(item_results) > 1:
        raise GenError(
            f"'{entry['id']}' makes {len(item_results)} kinds of item, and a recipe here makes one."
        )
    if not products:
        raise GenError(f"'{entry['id']}' makes nothing.")
    if item_results:
        out["result"] = {"id": resolve_item(item_results[0]["id"], mapping), "count": item_results[0]["amount"]}
    if fluid_results:
        out["fluid_results"] = [
            {"id": resolve_item(r["id"], mapping), "amount": r["amount"]} for r in fluid_results
        ]
    return out


def touches_fluid(entry: dict, dump: dict) -> bool:
    """Whether any fluid goes in or comes out, which is what rules a bench out."""
    return any(is_fluid(i["id"], dump) for i in entry["recipe"]["ingredients"]) \
        or any(is_fluid(r["id"], dump) for r in products_of(entry))


def shapeless_ingredients(entry: dict, mapping: dict) -> list[str] | None:
    """
    Flatten to one grid slot per item, the way a shapeless recipe wants them.

    Returns None when the recipe cannot fit a crafting grid. Scaling the counts down to make
    it fit would break non-negotiable #1, so the fallback is simply not offered.
    """
    flat: list[str] = []
    for i in entry["recipe"]["ingredients"]:
        flat.extend([resolve_item(i["id"], mapping)] * i["amount"])
    return flat if len(flat) <= GRID_SLOTS else None


def fallback_conditions(entry: dict, mapping: dict, *, without_facrafting: bool) -> list[dict]:
    """
    Two callers want the same fallback body under opposite conditions: the standalone copy
    applies when Facrafting is absent, the datapack copy when it is present but the player has
    chosen to re-enable bench crafting.
    """
    mods = required_mods(entry, mapping)

    if without_facrafting:
        # Only the Facrafting clause is negated. A mod supplying an ingredient is still
        # required: without it the recipe would name an item that does not exist. Neo
        # Progressive Automation negates the whole conjunction instead and substitutes
        # ingredients by hand, which a generator cannot do and non-negotiable #1 forbids.
        conditions = [{"type": "neoforge:not", "value": mod_loaded("facrafting")}]
        conditions += [mod_loaded(m) for m in mods if m != "facrafting"]
    else:
        conditions = [mod_loaded(m) for m in mods]
    return conditions + pending_ingredients(entry, mapping)


def shapeless_recipe(entry: dict, mapping: dict, ingredients: list[str], *, without_facrafting: bool) -> dict:
    """The bench fallback: the same ingredients, one grid slot each, in no time at all."""
    return {
        "neoforge:conditions": fallback_conditions(entry, mapping, without_facrafting=without_facrafting),
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ingredients,
        "result": {
            "id": resolve_item(entry["id"], mapping),
            "count": entry["recipe"]["yield"],
        },
    }


def smelting_recipe(entry: dict, mapping: dict, *, without_facrafting: bool) -> dict | None:
    """
    A vanilla furnace recipe, for a smelting recipe a vanilla furnace can express.

    That is one ingredient, one at a time, one result: iron and copper. Steel is five plates
    and stone brick is two stone, and a vanilla furnace takes one item, so those keep the
    shapeless fallback - a bench is the fallback for a world without Facrafting, and steel has to
    come from somewhere there. The time is Factorio's, so a vanilla furnace in such a world smelts
    at the rate the pack's own furnace does.
    """
    recipe = entry["recipe"]
    ingredients = recipe["ingredients"]
    if len(ingredients) != 1 or ingredients[0]["amount"] != 1 or recipe["yield"] != 1:
        return None
    return {
        "neoforge:conditions": fallback_conditions(entry, mapping, without_facrafting=without_facrafting),
        "type": "minecraft:smelting",
        "category": "misc",
        "ingredient": resolve_item(ingredients[0]["id"], mapping),
        "result": {"id": resolve_item(entry["id"], mapping)},
        "cookingtime": craft_ticks(recipe["time"], entry["id"]),
    }


def plan(dump: dict, fluid_recipes: list, mapping: dict, only: set[str] | None) -> tuple[dict[str, list], dict]:
    """
    Work out every file that should exist, without touching the disk.

    Returns the files keyed by owning mod, plus a report of what was left out and why.
    """
    files: dict[str, list[tuple[Path, dict]]] = {}
    names_seen: dict[str, dict[str, str]] = {}
    report = {
        "generated": 0,
        "skipped": [],       # explicitly marked skip in the mapping
        "raw": [],           # no recipe in the dump: ores, fluids, filled barrels
        "no_fallback": [],   # real recipe, too big for a crafting grid
        "hand_written": [],  # probabilistic, cannot be a crafting recipe at all
        "unmapped": [],
    }

    for factorio_id, entry in sorted(dump.items()):
        mapped = mapping.get(factorio_id)
        if mapped is None:
            report["unmapped"].append(factorio_id)
            continue
        if mapped.get("skip"):
            report["skipped"].append(factorio_id)
            continue

        recipe = entry.get("recipe") or {}
        if recipe.get("time") is None or not recipe.get("ingredients"):
            # The dump gives these no recipe at all. `raw` in the mapping says the same thing;
            # disagreeing means one of the two files is stale, so say so rather than guessing.
            if not mapped.get("raw"):
                raise GenError(
                    f"'{factorio_id}' has no recipe in the dump but is not marked raw in the mapping."
                )
            report["raw"].append(factorio_id)
            continue

        # Factorio's centrifuging is probabilistic - uranium processing yields 0.007 U-235 and
        # 0.993 U-238 per craft - and the dump models it as a recipe whose ingredients carry
        # those fractions. A crafting recipe cannot express a chance, so this is one of the
        # hand-written cases MAPPING.md already calls out rather than something to round.
        fractional = [i for i in recipe["ingredients"] if not isinstance(i["amount"], int)]
        if fractional:
            report["hand_written"].append(factorio_id)
            continue

        owner = mapped.get("owner")
        if not owner:
            raise GenError(f"'{factorio_id}' has no `owner` in the mapping table.")
        if only and owner not in only:
            continue

        # The file is named after the item it produces, and lives in the owning mod's own
        # namespace. Those two are usually the same word, but not always: Neo Progressive
        # Automation shipped `burner_drill` for Factorio's `burner-mining-drill`, and a
        # released id is permanent, so the file follows the item rather than the dump.
        name = resolve_item(factorio_id, mapping).split(":", 1)[1]
        namespace = owner
        seen = names_seen.setdefault(owner, {})
        if name in seen:
            raise GenError(
                f"'{factorio_id}' and '{seen[name]}' both resolve to '{name}' in {owner}; "
                "one of the two needs a distinct item in the mapping table."
            )
        seen[name] = factorio_id
        out = files.setdefault(owner, [])

        out.append((Path("data") / namespace / "recipe" / f"{name}.json",
                    facraft_recipe(entry, mapping, dump)))

        standalone = Path("data") / namespace / "recipe" / f"{name}_standalone.json"
        bench = Path("crafting_table") / "data" / namespace / "recipe" / f"{name}.json"
        smelted = (category_of(mapped, factorio_id) == "smelting"
                   and smelting_recipe(entry, mapping, without_facrafting=True))
        flat = None if touches_fluid(entry, dump) else shapeless_ingredients(entry, mapping)
        if smelted:
            out.append((standalone, smelted))
            out.append((bench, smelting_recipe(entry, mapping, without_facrafting=False)))
        elif flat is None:
            report["no_fallback"].append(factorio_id)
        else:
            out.append((standalone, shapeless_recipe(entry, mapping, flat, without_facrafting=True)))
            out.append((bench, shapeless_recipe(entry, mapping, flat, without_facrafting=False)))

        report["generated"] += 1

    # The fluid recipes: one file each, named after the recipe, no fallback ever. Their
    # products are fluids or an item several of them make, so nothing else can name them.
    for entry in fluid_recipes:
        owner = entry["owner"]
        if only and owner not in only:
            continue
        name = slug(entry["id"])
        seen = names_seen.setdefault(owner, {})
        if name in seen:
            raise GenError(f"'{entry['id']}' and '{seen[name]}' both resolve to '{name}' in {owner}.")
        seen[name] = entry["id"]
        files.setdefault(owner, []).append((
            Path("data") / owner / "recipe" / f"{name}.json", facraft_recipe(entry, mapping, dump)))
        report["no_fallback"].append(entry["id"])
        report["generated"] += 1

    return files, report


def render(obj: dict) -> str:
    return json.dumps(obj, indent=2) + "\n"


def do_write(files: dict, root: Path | None) -> int:
    written = 0
    for mod_id, entries in sorted(files.items()):
        base = (root / mod_id) if root else mod_resource_root(mod_id)
        if root is None and not base.parent.parent.parent.exists():
            print(f"  ! {mod_id}: {base} does not exist yet, skipped", file=sys.stderr)
            continue
        for rel, obj in entries:
            path = base / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(render(obj), encoding="utf-8", newline="\n")
            written += 1
        print(f"  {mod_id}: {len(entries)} files -> {base}")
    return written


def do_check(files: dict) -> int:
    """
    Compare against what is on disk, semantically rather than byte for byte.

    Neo Progressive Materials ships three recipes written by hand before this script existed.
    They are the acceptance test: if the generator reproduces them, it is reading the dump
    the same way a person did.
    """
    missing, matching = [], 0
    wrong, diverged = [], []

    for mod_id, entries in sorted(files.items()):
        base = mod_resource_root(mod_id)
        for rel, obj in entries:
            path = base / rel
            if not path.exists():
                missing.append(path)
                continue
            on_disk = json.loads(path.read_text(encoding="utf-8"))
            if on_disk == obj:
                matching += 1
            elif obj["type"] == "facrafting:facraft":
                # The real recipe: Factorio's own ingredients and craft time. Any disagreement
                # is a defect, and catching it is the entire point of generating these.
                wrong.append((path, on_disk, obj))
            else:
                # A bench fallback. These are sometimes hand-authored on purpose - the
                # drills substitute a burner drill and a redstone block for ingredients a
                # missing mod would have supplied, which no generator can invent. Listed so
                # they stay visible, but not a failure.
                diverged.append(path)

    print(f"  matched                {matching}")
    print(f"  missing                {len(missing)}")
    print(f"  wrong, timed recipes   {len(wrong)}")
    print(f"  hand-authored fallback {len(diverged)}, allowed to differ")

    for path in diverged:
        print(f"    - {path.name}")

    for path, on_disk, generated in wrong:
        print(f"\n  {path}")
        for key in sorted(set(on_disk) | set(generated)):
            if on_disk.get(key) != generated.get(key):
                print(f"    {key}:")
                print(f"      on disk:   {json.dumps(on_disk.get(key))}")
                print(f"      generated: {json.dumps(generated.get(key))}")

    return 1 if wrong else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="diff against what is on disk, write nothing")
    ap.add_argument("--out", type=Path, help="write the tree into a staging directory")
    ap.add_argument("--write", action="store_true", help="write into each owning mod's resources")
    ap.add_argument("--only", action="append", default=[], metavar="MODID", help="restrict to one mod")
    args = ap.parse_args()

    try:
        dump, fluid_recipes, mapping = load_inputs()
        files, report = plan(dump, fluid_recipes, mapping, set(args.only) or None)
    except GenError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    total = sum(len(v) for v in files.values())
    print(f"{report['generated']} recipes -> {total} files across {len(files)} mods")
    print(f"  raw, no recipe in the dump : {len(report['raw'])}")
    print(f"  skipped by the mapping     : {len(report['skipped'])}")
    print(f"  no bench fallback possible : {len(report['no_fallback'])}")
    if report["hand_written"]:
        print(f"  must be hand-written       : {report['hand_written']}")
    if report["unmapped"]:
        print(f"  UNMAPPED                   : {report['unmapped']}")

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
