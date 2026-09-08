#!/usr/bin/env python3
"""
Factorio's data.raw as the generators read it (CLAUDE.md, rule 2).

    reference/factorio/data-raw-<version>.json + data/mapping.json -> one entry per recipe the pack has

The dump is Factorio's own: `factorio.exe --dump-data` writes every prototype as JSON, and
reference/README.md says how. Nothing here invents a number the dump answers; the mapping holds
the decisions (which Minecraft item, which mod, what is skipped) and this module joins the two.

An entry is one recipe the pack generates. Most make the item they are named after and are
filed under that item; the rest - the refinery's, the cracking and solid-fuel routes, emptying a
barrel - are filed under the recipe's own name and need a row in the mapping's `recipes` table.
A launch is not a recipe in data.raw and is put together from the silo and the satellite.
"""

from __future__ import annotations

import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FACTORIO_VERSION = "2.0.77"
DATA_RAW = REPO / "reference" / "factorio" / f"data-raw-{FACTORIO_VERSION}.json"
MAPPING = REPO / "data" / "mapping.json"

# What the hand crafts, and what only a machine here runs. Every other category in data.raw
# (centrifuging, recycling, parameters) has no machine and reaches nothing unless a row in the
# mapping skips it or a machine arrives and joins the set.
HAND_CATEGORIES = {"crafting", "basic-crafting"}
MACHINE_CATEGORIES = {
    "smelting", "chemistry", "oil-processing", "crafting-with-fluid", "advanced-crafting", "rocket-building",
}

# Factorio's crafting-menu tabs by data.raw's item-group name, and the Facrafting group each is.
# The order is Factorio's own, taken from the groups' `order`. Fluids share the intermediates'
# tab: a fifth tab is a lang key and a decision, and the barrels are intermediates to a player.
TAB_BY_GROUP = {
    "logistics": "logistics",
    "production": "production",
    "intermediate-products": "intermediate",
    "combat": "combat",
    "fluids": "intermediate",
}

# A launch takes no time in Factorio; here it is a recipe, and a recipe has a craft time. Five
# minutes is the pack's own number and nothing reads it: the silo counts parts, not ticks.
LAUNCH_SECONDS = 300

# `order` is zero-padded so a plain string sort is a numeric one; four digits leaves room.
ORDER_DIGITS = 4


class GenError(Exception):
    """A fault in the inputs. Always fatal: a wrong recipe is worse than no recipe."""


def slug(factorio_id: str) -> str:
    """`iron-gear-wheel` -> `iron_gear_wheel`. Ids derive mechanically, never by hand."""
    return factorio_id.replace("-", "_")


class Data:
    """The dump and the mapping, loaded once."""

    def __init__(self, raw: dict, mapping: dict):
        self.raw = raw
        self.items: dict = mapping["items"]
        self.recipe_rows: dict = mapping.get("recipes", {})
        # Every prototype that stacks is an item, whatever its type: tools, modules, ammo, guns...
        self.protos: dict = {}
        for kind, protos in raw.items():
            if isinstance(protos, dict):
                for name, proto in protos.items():
                    if isinstance(proto, dict) and "stack_size" in proto:
                        self.protos[name] = proto
        self.fluids: dict = raw.get("fluid", {})
        self.subgroups: dict = raw["item-subgroup"]
        self.groups: dict = raw["item-group"]

    def is_fluid(self, name: str) -> bool:
        return name in self.fluids

    def mapped(self, name: str) -> dict | None:
        return self.items.get(name)


def load() -> Data:
    if not DATA_RAW.exists():
        raise GenError(
            f"{DATA_RAW} is missing. It is Wube's data, gitignored on purpose; reference/factorio/README.md says how to make it."
        )
    if not MAPPING.exists():
        raise GenError(f"{MAPPING} is missing.")
    raw = json.loads(DATA_RAW.read_text(encoding="utf-8"))
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    return Data(raw, mapping)


def resolve_item(name: str, data: Data) -> str:
    """Factorio item or fluid -> the Minecraft id standing in for it."""
    entry = data.mapped(name)
    if entry is None:
        raise GenError(f"'{name}' appears in a recipe but is not in the mapping table.")
    if entry.get("skip"):
        raise GenError(f"'{name}' is marked skip in the mapping but is needed by a recipe.")
    item = entry.get("item")
    if not item:
        raise GenError(f"'{name}' has no `item` in the mapping table.")
    return item


def owner_of_item(name: str, data: Data) -> str:
    owner = data.items[name].get("owner")
    if not owner:
        raise GenError(f"'{name}' has no `owner` in the mapping table.")
    return owner


def recipe_key(entry: dict) -> str:
    """
    The Minecraft recipe key a generated recipe has, which both generators must agree on.

    A recipe filed under its item is `<owner>:<item path>`, named after the *item* rather than
    the Factorio id because a released mod's item name is permanent and the dump's is not. One
    filed under its own name is `<owner>:<recipe with underscores>`.
    """
    return f"{entry['owner']}:{entry['file']}"


def _amount(part: dict) -> int | float:
    if "amount" in part:
        return part["amount"]
    return (part["amount_min"] + part["amount_max"]) / 2


def _parts(parts: list, data: Data) -> list[dict]:
    return [{
        "id": p["name"],
        "amount": _amount(p),
        "fluid": p.get("type") == "fluid" or data.is_fluid(p["name"]),
        "probability": p.get("probability"),
    } for p in parts]


def _main_product(recipe: dict, results: list[dict]) -> dict | None:
    """Factorio's rule: `main_product` if named, else the only result, else the one named like the recipe."""
    named = recipe.get("main_product")
    if named:
        return next((r for r in results if r["id"] == named), None)
    if len(results) == 1:
        return results[0]
    return next((r for r in results if r["id"] == recipe["name"]), None)


def _tab_and_sort(recipe: dict, main: dict | None, data: Data) -> tuple[str, tuple]:
    """Where the crafting panel shows this, and Factorio's own place for it within that tab."""
    proto = None
    if main is not None:
        proto = data.fluids.get(main["id"]) if main["fluid"] else data.protos.get(main["id"])
    subgroup = recipe.get("subgroup") or (proto or {}).get("subgroup")
    order = recipe.get("order") or (proto or {}).get("order") or ""
    if subgroup is None:
        raise GenError(f"'{recipe['name']}' has no subgroup, on itself or on what it makes.")
    group = data.subgroups[subgroup]["group"]
    tab = TAB_BY_GROUP.get(group)
    if tab is None:
        raise GenError(
            f"'{recipe['name']}' is in item group {group!r}, which is not one of Factorio's crafting-menu tabs "
            f"({', '.join(TAB_BY_GROUP)}). A new tab is a lang key and a decision."
        )
    key = (data.groups[group]["order"], data.subgroups[subgroup]["order"], order, recipe["name"])
    return tab, key


def _category(recipe: dict) -> str | None:
    category = recipe.get("category", "crafting")
    if category in HAND_CATEGORIES:
        return None
    if category not in MACHINE_CATEGORIES:
        raise GenError(
            f"'{recipe['name']}' is in category {category!r}, which no machine here runs; skip it in the "
            f"mapping's recipes table, or add the machine and the category to MACHINE_CATEGORIES."
        )
    return category


def _launch(name: str, row: dict, data: Data) -> dict:
    """A launch as a recipe: the silo's parts and the cargo in, the cargo's launch products out."""
    cargo = row.get("launch")
    if not isinstance(cargo, str):
        raise GenError(f"'{name}' is a launch and must name its cargo item: \"launch\": \"satellite\".")
    silos = data.raw.get("rocket-silo", {})
    if len(silos) != 1:
        raise GenError(f"data.raw has {len(silos)} rocket silos; a launch needs exactly one.")
    silo = next(iter(silos.values()))
    part = silo["fixed_recipe"]
    products = data.protos.get(cargo, {}).get("rocket_launch_products")
    if not products:
        raise GenError(f"'{cargo}' has no rocket_launch_products in data.raw, so a launch of it returns nothing.")
    results = _parts(products, data)
    if len(results) != 1 or results[0]["id"] != name:
        raise GenError(f"'{name}' is a launch of '{cargo}', which sends back {results} rather than one of it.")
    ingredients = [
        {"id": part, "amount": silo["rocket_parts_required"], "fluid": False, "probability": None},
        {"id": cargo, "amount": 1, "fluid": False, "probability": None},
    ]
    proto = data.protos[name]
    subgroup = proto["subgroup"]
    group = data.subgroups[subgroup]["group"]
    return {
        "id": name,
        "product": name,
        "file": resolve_item(name, data).split(":", 1)[1],
        "owner": owner_of_item(name, data),
        "time": LAUNCH_SECONDS,
        "ingredients": ingredients,
        "results": results,
        "category": "rocket-building",
        "tab": TAB_BY_GROUP[group],
        "sort": (data.groups[group]["order"], data.subgroups[subgroup]["order"], proto.get("order", ""), name),
        # Nothing gates a launch but the silo, which its technology gates.
        "enabled": True,
    }


def entries(data: Data) -> tuple[dict[str, dict], dict]:
    """
    Every recipe the pack generates, by Factorio recipe name, and a report of what was left out.

    A recipe that makes the item it is named after is filed under that item and needs nothing but
    the item's mapping entry. Any other recipe needs a row in the mapping's `recipes` table with
    an owner, or a skip and a reason; a new one in a new dump is a GenError until it has one.
    Hidden recipes are Factorio's own leftovers and are ignored unless a row says `show`.
    """
    report = {
        "skipped": [],       # a skip in the mapping, item or recipe
        "hidden": [],        # hidden in data.raw and not shown by a row
        "unmapped": [],      # makes an item the mapping has no entry for
        "hand_written": [],  # probabilistic; cannot be a crafting recipe
    }
    out: dict[str, dict] = {}
    made: set[str] = set()

    for name, recipe in data.raw["recipe"].items():
        if recipe.get("category") == "parameters" or name == "recipe-unknown":
            continue
        row = data.recipe_rows.get(name)
        if recipe.get("hidden") and not (row and row.get("show")):
            report["hidden"].append(name)
            continue

        results = _parts(recipe.get("results", []), data)
        ingredients = _parts(recipe.get("ingredients", []), data)
        main = _main_product(recipe, results)
        # Named after what it makes, item or fluid: filed under that entry of the mapping.
        filed_under_item = main is not None and main["id"] == name

        if filed_under_item:
            mapped = data.mapped(name)
            if mapped is None:
                report["unmapped"].append(name)
                continue
            if mapped.get("skip") or (row and row.get("skip")):
                report["skipped"].append(name)
                made.add(name)
                continue
            owner = owner_of_item(name, data)
            file = resolve_item(name, data).split(":", 1)[1]
            made.add(name)
        else:
            if row is None:
                raise GenError(
                    f"'{name}' makes {[r['id'] for r in results]} rather than itself, so it is filed under its "
                    "own name: add it to the mapping's `recipes` table with an owner, or skip it with a reason."
                )
            if row.get("skip"):
                report["skipped"].append(name)
                continue
            owner = row.get("owner")
            if not owner:
                raise GenError(f"recipe '{name}' has no `owner` in the mapping's recipes table.")
            file = slug(name)

        if any(p["probability"] is not None for p in results):
            report["hand_written"].append(name)
            continue
        if not results:
            raise GenError(f"'{name}' makes nothing.")
        if sum(1 for r in results if not r["fluid"]) > 1:
            raise GenError(
                f"'{name}' makes {[r['id'] for r in results if not r['fluid']]}, and a recipe here makes one kind of item."
            )

        tab, sort = _tab_and_sort(recipe, main, data)
        out[name] = {
            "id": name,
            "product": name if filed_under_item else None,
            "file": file,
            "owner": owner,
            "time": recipe.get("energy_required", 0.5),
            "ingredients": ingredients,
            "results": results,
            "category": _category(recipe),
            "tab": tab,
            "sort": sort,
            # A recipe data.raw hides is Factorio's starting kit (the pistol); shown here, it is free.
            "enabled": recipe.get("enabled", True) or bool(row and row.get("show")),
        }

    for name, row in data.recipe_rows.items():
        if row.get("launch"):
            if name in out:
                raise GenError(f"'{name}' is both a recipe in data.raw and a launch in the mapping.")
            out[name] = _launch(name, row, data)
            made.add(name)
        elif name not in data.raw["recipe"]:
            raise GenError(f"the mapping's recipes table names '{name}', which is not a recipe in data.raw.")

    for name, mapped in data.items.items():
        if mapped.get("skip"):
            continue
        if name in made and mapped.get("raw"):
            raise GenError(f"'{name}' is marked raw in the mapping, but '{name}' is a recipe in data.raw that makes it.")
        if name not in made and not mapped.get("raw"):
            raise GenError(
                f"'{name}' has no recipe of its own in data.raw, but is not marked raw in the mapping. "
                "Ores, fluids, wood and what several recipes make are raw; a renamed item is a stale key."
            )

    tabs = list(dict.fromkeys(TAB_BY_GROUP.values()))
    for index, entry in enumerate(sorted(out.values(), key=lambda e: e["sort"])):
        entry["order"] = str(index).zfill(ORDER_DIGITS)
        assert entry["tab"] in tabs
    return out, report
