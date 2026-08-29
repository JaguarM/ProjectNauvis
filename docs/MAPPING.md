The mapping table
=================

`data/mapping.json` answers one question for each of Factorio's 214 items: **what is this in
Minecraft, and which mod owns it?** Every recipe in the pack is generated from it plus
`reference/factorio/recipes.json`, and every technology from it plus
`reference/factorio/technologies.json`. It is the single review surface for the pack's naming
and stand-in decisions — one file instead of two hundred recipe JSONs and two hundred more
technologies.

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
| `size` | Factorio's tile footprint, `[width, depth]`. Absent means one tile |
| `stand_in` | true when an existing Minecraft item covers it and nothing new is registered |
| `raw` | true when the dump gives it no recipe: an ore, a fluid, a filled barrel |
| `skip` | true when it is never registered at all |

`size` is the one field here that did not come out of the dump — the dump carries recipes and
nothing else — so it was entered by hand against the `wiki_link` each entry already has. It is
here rather than in the Java because **a footprint is identity in the same sense an ingredient
list is**: three tiles by three is why an assembler line spaces the way it does, it lives in
world saves and in the player's head, and changing it later moves every machine in every world.
One Factorio tile is one Minecraft block, which is forced anyway — a belt, an inserter and a pipe
are one tile there and one block here.

`tools/check_models.py` reads each machine's cells back out of its `*Shape.java` and fails the
build if they disagree with the number here, the way `checkRecipes` does for craft times. A shape
opts into that by naming its entry in a `FACTORIO_ID` constant.

The `unlocks` table
-------------------

A second, much smaller table beside `items`, and it exists because of one mismatch: a technology
effect names a **recipe**, and Factorio's recipe names are not always its item names. `optics`
unlocks `small-lamp`, which makes a `lamp`; `solar-energy` unlocks `solar-panel-equipment`, which
makes a `portable-solar-panel`. Fifteen rows are that, and they map the recipe name to the item
whose recipe file `gen_recipes.py` writes.

The other eight rows are `null`, which means **the pack has no counterpart at all**. They are
the oil recipes — `basic-oil-processing`, the two crackings, the three solid-fuel routes and
`coal-liquefaction` — whose products this pack's recipe dump carries as raw inputs with no recipe
of their own. A technology that unlocks only those unlocks nothing here.

**A recipe name in neither table is a `GenError`, not a dropped unlock.** That is the same rule
the recipe generator applies to an unmapped ingredient, and for the same reason: "no opinion" and
"I could not work it out" must not have the same representation, or a stale table looks like a
design decision.

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

Plus the four in `PLAN.md`'s belt note — `underground-belt` and its two upper tiers, and
`pipe-to-ground` — which this pack does not need because it has a Y axis.

`gen_technologies.py` reports these when a technology unlocks one, rather than passing on a
recipe key nothing will ever answer to. Four of them come up: `steel-processing` unlocks the
steel axe, and the three logistics tiers each unlock an underground belt.

Open questions
--------------

- **`uranium-ore`** has no vanilla equivalent and needs a real ore block, worldgen and all.
  It is milestone-8-adjacent, so it can wait, but it is the one raw input that is genuinely
  new content rather than a mapping decision.
- **Fluids** (`crude-oil`, `heavy-oil`, `light-oil`, `petroleum-gas`, `steam`, `lubricant`,
  `sulfuric-acid`) map to barrel *items* during the shortcut phase and to real fluids after.
  The mapping needs a second field for that when milestone 4 lands, rather than being edited
  twice. **Settled on the third-party question:** refining and the chemical plant have no
  answer on 26.2, so barrels stand. Tanks and fluid pipes do exist, in Fluid Tank — a
  candidate for the real-fluids phase, not for milestone 4. See `PLAN.md`.
- **`solid-fuel`, `uranium-235`, `uranium-238`** appear as raw in the dump but are products of
  chemistry and centrifuging in the real game. They belong to `nauvis_fluids` and their
  recipes have to be written by hand — the only place in the pack where that is true.

  `gen_recipes.py` confirms the hand-written set independently, by reporting every ingredient
  no generated recipe produces. It is those three plus `heavy-oil`, `light-oil` and
  `petroleum-gas` — and `uranium-processing`, whose 0.007 / 0.993 output is a probability that
  no crafting recipe can express. Seven items, and the list is derived rather than remembered,
  so it stays honest as the mapping changes.
