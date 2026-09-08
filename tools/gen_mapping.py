"""Seed data/mapping.json from Factorio's recipe dump.

Run once. After that mapping.json is hand-maintained -- it is the review surface for
every naming and stand-in decision in the pack, and rerunning this would discard them.
"""
import json, re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
dump = json.load(open(ROOT / "reference/factorio/recipes.json", encoding="utf-8"))

# --- ownership -------------------------------------------------------------------------
# Explicit ids win; otherwise (category, type) decides. Keep this list short: it exists to
# seed the file, not to be the permanent answer.
BY_ID = {
    # power
    "boiler": "power", "steam-engine": "power", "solar-panel": "power",
    "accumulator": "power", "steam-turbine": "power", "nuclear-reactor": "power",
    "heat-exchanger": "power", "heat-pipe": "power",
    "small-electric-pole": "power", "medium-electric-pole": "power",
    "big-electric-pole": "power", "substation": "power", "power-switch": "power",
    # already-shipped mods
    "burner-mining-drill": "nauvis_mining",
    "electric-mining-drill": "nauvis_mining",
    # research
    "lab": "research",
    # fluids / oil / nuclear chemistry
    "pipe": "fluids", "pipe-to-ground": "fluids", "pump": "fluids",
    "storage-tank": "fluids", "offshore-pump": "fluids", "pumpjack": "fluids",
    "oil-refinery": "fluids", "chemical-plant": "fluids", "centrifuge": "fluids",
    "uranium-fuel-cell": "fluids", "used-up-uranium-fuel-cell": "fluids",
    "nuclear-fuel": "fluids", "explosives": "fluids",
    # trains
    "rail": "trains", "locomotive": "trains", "cargo-wagon": "trains",
    "fluid-wagon": "trains", "artillery-wagon": "trains", "train-stop": "trains",
    "rail-signal": "trains", "rail-chain-signal": "trains", "car": "trains",
    "tank": "military",
    # rocket
    "rocket-silo": "rocket", "rocket-part": "rocket", "satellite": "rocket",
    "space-science-pack": "rocket",
    # logistics robots
    "roboport": "logistics", "construction-robot": "logistics",
    "logistic-robot": "logistics", "flying-robot-frame": "logistics",
    # machines
    "beacon": "machines", "radar": "machines",
    # terrain / pack-level
    "landfill": "nauvis", "concrete": "nauvis", "hazard-concrete": "nauvis",
    "refined-concrete": "nauvis", "refined-hazard-concrete": "nauvis",
    "cliff-explosives": "nauvis", "stone-brick": "nauvis",
}
BY_GROUP = {
    ("Combat", None): "military",
    ("Intermediate product", "Intermediate product"): "nauvis_materials",
    ("Intermediate product", "Liquid"): "fluids",
    ("Intermediate product", "Process"): "fluids",
    ("Intermediate product", "Resource"): "nauvis",
    ("Intermediate product", "Science pack"): "research",
    ("Intermediate product", None): "fluids",          # the barrels
    ("Logistics", "Logic"): "circuits",
    ("Logistics", "Machinery"): "logistics",
    ("Logistics", None): "logistics",
    ("Production", "Machinery"): "machines",
    ("Production", "Item"): "machines",                # modules
    ("Production", "Tool"): "machines",
}
MODID = {
    "nauvis": "nauvis", "logistics": "nauvis_logistics", "machines": "nauvis_machines",
    "power": "nauvis_power", "research": "nauvis_research", "fluids": "nauvis_fluids",
    "trains": "nauvis_trains", "circuits": "nauvis_circuits",
    "military": "nauvis_military", "rocket": "nauvis_rocket",
    "nauvis_materials": "nauvis_materials",
    "nauvis_mining": "nauvis_mining",
}

# --- stand-ins: a Factorio item that an existing Minecraft item already covers ----------
VANILLA = {
    "iron-ore": "minecraft:raw_iron", "copper-ore": "minecraft:raw_copper",
    "coal": "minecraft:coal", "stone": "minecraft:cobblestone",
    "raw-wood": "minecraft:oak_log", "raw-fish": "minecraft:cod",
    "wood": "minecraft:oak_planks",
    "iron-plate": "minecraft:iron_ingot", "copper-plate": "minecraft:copper_ingot",
    "stone-brick": "minecraft:stone_bricks", "stone-wall": "minecraft:cobblestone_wall",
    "wooden-chest": "minecraft:chest", "stone-furnace": "minecraft:furnace",
    "lamp": "minecraft:redstone_lamp", "rail": "minecraft:rail",
    "concrete": "minecraft:gray_concrete", "hazard-concrete": "minecraft:yellow_concrete",
    "landfill": "minecraft:dirt", "water": "minecraft:water",
    "gate": "minecraft:iron_door",
}
# Factorio UI tools with no Minecraft analogue. Never registered, never generated.
SKIP = {"blueprint", "blueprint-book", "deconstruction-planner",
        "iron-axe", "steel-axe"}

snake = lambda s: re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")

def owner(e):
    if e["id"] in BY_ID: return BY_ID[e["id"]]
    c, t = e.get("category"), e.get("type")
    return BY_GROUP.get((c, t)) or BY_GROUP.get((c, None)) or "nauvis"

raw = {e["id"] for e in dump if not (e.get("recipe") or {}).get("ingredients")}
items = {}
for e in sorted(dump, key=lambda e: e["id"]):
    fid = e["id"]
    if fid in SKIP:
        items[fid] = {"skip": True, "why": "Factorio UI tool, no Minecraft analogue"}
        continue
    o = owner(e)
    entry = {
        "name": e["name"],
        "owner": MODID[o],
        "item": VANILLA.get(fid, f"{MODID[o]}:{snake(fid)}"),
    }
    if fid in VANILLA: entry["stand_in"] = True
    if fid in raw: entry["raw"] = True
    r = e.get("recipe") or {}
    if r.get("ingredients"):
        entry["craft"] = {"time": r["time"], "yield": r.get("yield", 1)}
    items[fid] = entry

out = {
    "_": "Factorio id -> Minecraft item. Hand-maintained; see docs/MAPPING.md.",
    "_generated_from": "reference/factorio/recipes.json (214 entries)",
    "items": items,
}
(ROOT / "data").mkdir(exist_ok=True)
p = ROOT / "data/mapping.json"
if p.exists() and "--force" not in sys.argv:
    sys.exit(f"{p} exists; refusing to overwrite hand edits. Pass --force if you mean it.")
p.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"wrote {p} - {len(items)} entries")
