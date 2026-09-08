The data files
==============

`reference/factorio/data-raw-<version>.json`
--------------------------------------------

Factorio's `data.raw`, written by `factorio --dump-data` (`reference/factorio/README.md` says how):
every prototype keyed by name. It is the spec for every recipe, stack size, footprint and
technology in the pack, and nothing here invents a number it answers. `tools/factorio_data.py`
names the version and reads it for both generators. Gitignored: Wube's data is read, never shipped.

`data/mapping.json`
-------------------

One entry per Factorio item or fluid: what it is in Minecraft and which mod owns it.
Hand-maintained, and only the decisions. The dump is a closed graph, so the raw inputs and the
vanilla stand-ins are choices and the rest derives mechanically, `iron-gear-wheel` becoming
`nauvis_materials:iron_gear_wheel`.

```json
"electronic-circuit": {
  "name": "Electronic circuit", "owner": "nauvis_materials",
  "item": "nauvis_materials:electronic_circuit", "stack": 200
}
```

| Field | Meaning |
|---|---|
| `name` | Factorio's display name, from its locale |
| `owner` | the mod that registers it and holds its recipe file |
| `item` | the Minecraft id it resolves to; for a fluid, the fluid id |
| `size` | Factorio's footprint `[width, depth]`, hand-entered; absent means one tile. `check_models.py` holds each `*Shape` naming a `FACTORIO_ID` to it |
| `stack` | Factorio's stack size, on every registered item and every stand-in; `check_models.py` holds `Stacks.of(n)` or the pack's `StandInStacks` line to it |
| `stand_in` | an existing Minecraft item covers it; nothing is registered |
| `raw` | no recipe of its own in the dump: ores, fluids, wood, and what several recipes make (solid fuel). The generator refuses a `raw` that has one and a missing `raw` that has none |
| `pending` | named by recipes before anything registers it; those recipes wait on `neoforge:registered` |
| `skip` | never registered, with a reason |

A recipe's craft time, ingredients, products and category come from the dump, never from here.
The category decides the machine: `smelting` the furnaces, `chemistry` the chemical plant,
`oil-processing` the refinery, `crafting-with-fluid` an assembler with a fluid box,
`advanced-crafting` any assembler and no hand, `rocket-building` the silo; `crafting` is the hand.
Facrafting keeps a categorised recipe out of the hand panel. The panel's tabs and the order within
them are Factorio's own item groups and orders; fluids share the intermediates tab.

Stand-ins: `raw_iron`, `raw_copper`, `coal`, `cobblestone` (stone), `iron_ingot` and
`copper_ingot` (plates), `oak_planks` (wood), `stone_bricks`, `cobblestone_wall`, `chest`, `rail`,
`dirt` (landfill), `gray_concrete`, `yellow_concrete` (hazard), `iron_door` (gate, the weakest),
`water`, `cod`. Skipped: blueprints, the deconstruction planner, the axes, the underground belts,
pipe-to-ground, the trains and vehicles, the pump, the circuit network, the cargo landing pad, the
remotes 2.0 hands out through shortcuts. `gen_technologies.py` reports a technology that unlocks a
skipped item.

The `recipes` table
-------------------

A recipe that makes the item or fluid it is named after is filed under that entry and needs
nothing else. Any other recipe is filed under its own name, `<owner>:<name with underscores>`, and
needs a row here: an `owner`, or a `skip` and a reason. Today that is the refinery's two, the two
cracking routes, solid fuel's three and emptying the seven barrels, all `nauvis_fluids`; skipped
are coal liquefaction (the refinery has no third input port) and the four centrifuge recipes
(uranium is unscheduled). Two rows are neither: `"launch": "satellite"` makes the launch a recipe
out of the silo's parts count and the satellite's launch products, since data.raw has none, and
`"show": true` generates the pistol, which 2.0 hides because a new character carries one. A recipe
in a new dump with neither an item entry nor a row is a `GenError`, never a dropped recipe.

`data/technologies.json`
------------------------

The technologies the pack has, by Factorio's name, in the order the research screen lists them:
a selection, not a dump. Everything about each - prerequisites, cost or trigger, unlocks,
modifiers - is read from data.raw by `gen_technologies.py`, which writes
`nauvis_research/.../nauvis_research/technology/<name>.json` plus a vanilla advancement per
technology (criterion `minecraft:impossible`, awarded by the server when the world completes it;
icon is the first vanilla unlocked item or the lab, since an icon is registry-validated). A
technology is paid in exactly one of two ways: a cost (units, one of each pack per unit, seconds
a unit) or a trigger, `craft-item` (an item and a count, heard from bench, furnace and panel) or
`mine-entity` (a resource id, heard through `MiningListeners`). Other effects go through whole as
`{"type": ..., "modifier": n}` with an `ammo_category` or `turret_id` folded into `target`, and
the research mod sums the earned ones by `type` or `type@target` for `Bonuses`. A prerequisite
outside the selection is dropped and named in the summary, which is what curating means: utility
science does not wait for robots. The generator fails on a tree that cannot be bootstrapped from
an empty world and on a recipe the dump gates that is on disk and unlocked by nothing here, since
that recipe would be free from the first tick.

`data/removals.json`
--------------------

Which vanilla recipes stop existing, generated by `gen_removals.py` into a built-in datapack in
the `nauvis` mod at `Pack.Position.TOP`, each file a `neoforge:never` condition. Every entry names
a `replaced_by` recipe and the build fails if that recipe is not shipped. Two kinds: a **conflict**
(a vanilla recipe making an item the mapping puts a Factorio item on; checked rather than listed,
so shipping a pack recipe for a stand-in fails the build until vanilla's is removed) and a
**bypass** (vanilla doing a job Factorio has a machine for; a judgement, only ever a list).
`mirrors` names vanilla recipes a pack recipe reproduces (planks), and `kept` names conflicts that
are iron changing shape (block and nuggets); both stand the check down. Minecraft's own recipes are
read from the client jar in the Gradle cache; the check skips itself when it is absent.

The opening: four planks is a stone pickaxe and the wooden one has no recipe (a stone recipe cannot
be the first pickaxe). Both are the pack's own timed recipes, the two `gen_recipes.py` knows as
`PACK_OWN`. `mineable/pickaxe` gains the axe, shovel and hoe tags; tiers are untouched.
