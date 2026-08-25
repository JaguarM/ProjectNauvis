The mapping table
=================

`data/mapping.json` answers one question for each of Factorio's 214 items: **what is this in
Minecraft, and which mod owns it?** Every recipe in the pack is generated from it plus
`reference/factorio/recipes.json`. It is the single review surface for the pack's naming and
stand-in decisions — one file instead of two hundred recipe JSONs.

It was seeded once by `tools/gen_mapping.py` and is **hand-maintained from here on**. The
script refuses to overwrite it without `--force`, because rerunning would discard every
decision recorded in it.

Why this works at all
---------------------

The Factorio dump is a **closed graph**. All 214 entries resolve: every ingredient is either
produced by another entry or is one of 23 genuine raw inputs. Nothing dangles.

So the mapping only has to make real decisions about the raw inputs and about which items
vanilla already covers. The other ~170 derive mechanically — kebab-case id to snake_case,
namespaced to the owning mod, `iron-gear-wheel` becoming
`neoprogressivematerials:iron_gear_wheel`.

Entry shape
-----------

```json
"electronic-circuit": {
  "name": "Electronic circuit",
  "owner": "neoprogressivematerials",
  "item": "neoprogressivematerials:electronic_circuit",
  "craft": { "time": 0.5, "yield": 1 }
}
```

| Field | Meaning |
|---|---|
| `name` | Factorio's display name, straight from the dump |
| `owner` | which mod registers it |
| `item` | the Minecraft item id it resolves to, in recipes and in the world |
| `craft` | `time` in seconds and `yield`, from the dump. Never edit these — they are the spec |
| `stand_in` | true when an existing Minecraft item covers it and nothing new is registered |
| `raw` | true when the dump gives it no recipe: an ore, a fluid, a filled barrel |
| `skip` | true when it is never registered at all |

Naming policy
-------------

Ids derive mechanically from the Factorio id, so no one has to decide them one at a time.
Display names come from the dump's `name` field.

Note the dump uses **pre-1.0 Factorio names** — `science-pack-1` rather than
`automation-science-pack`, `logistic-chest-storage` rather than `storage-chest`. Keeping them
costs nothing and keeps ids consistent with the spec. If modern names are wanted later, that
is a change to display strings only, not to ids, and stays cheap forever.

Stand-ins, for review
---------------------

These twenty Factorio items resolve to something vanilla already has, so nothing new gets
registered. This is where judgement was applied and where disagreement is most likely — every
row is a one-word change in `mapping.json`.

| Factorio | Minecraft | |
|---|---|---|
| `iron-ore` | `minecraft:raw_iron` | |
| `copper-ore` | `minecraft:raw_copper` | |
| `coal` | `minecraft:coal` | |
| `stone` | `minecraft:cobblestone` | what mining stone actually yields |
| `iron-plate` | `minecraft:iron_ingot` | already shipped in `neoprogressivematerials` |
| `copper-plate` | `minecraft:copper_ingot` | already shipped |
| `raw-wood` | `minecraft:oak_log` | consider the `#minecraft:logs` tag on the input side |
| `wood` | `minecraft:oak_planks` | |
| `stone-brick` | `minecraft:stone_bricks` | ratios differ; Factorio smelts 2 stone, vanilla crafts 4 |
| `stone-wall` | `minecraft:cobblestone_wall` | |
| `wooden-chest` | `minecraft:chest` | |
| `stone-furnace` | `minecraft:furnace` | |
| `lamp` | `minecraft:redstone_lamp` | |
| `rail` | `minecraft:rail` | the trains shortcut leans on this |
| `landfill` | `minecraft:dirt` | |
| `concrete` | `minecraft:gray_concrete` | |
| `hazard-concrete` | `minecraft:yellow_concrete` | |
| `gate` | `minecraft:iron_door` | weakest of the twenty; a real wall gate may be worth building |
| `water` | `minecraft:water` | a fluid, not an item — barrels come first anyway |
| `raw-fish` | `minecraft:cod` | |

Skipped outright
----------------

`blueprint`, `blueprint-book`, `deconstruction-planner`, `iron-axe`, `steel-axe`.

The first three are Factorio UI affordances with no Minecraft analogue. The axes are covered
by vanilla tools and would only duplicate them.

Open questions
--------------

- **`uranium-ore`** has no vanilla equivalent and needs a real ore block, worldgen and all.
  It is milestone-8-adjacent, so it can wait, but it is the one raw input that is genuinely
  new content rather than a mapping decision.
- **Fluids** (`crude-oil`, `heavy-oil`, `light-oil`, `petroleum-gas`, `steam`, `lubricant`,
  `sulfuric-acid`) map to barrel *items* during the shortcut phase and to real fluids after.
  The mapping needs a second field for that when milestone 4 lands, rather than being edited
  twice.
- **`solid-fuel`, `uranium-235`, `uranium-238`** appear as raw in the dump but are products of
  chemistry and centrifuging in the real game. They belong to `nauvis_fluids` and their
  recipes have to be written by hand — the only place in the pack where that is true.
