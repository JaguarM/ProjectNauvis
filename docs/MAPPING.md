The mapping table
=================

`data/mapping.json` answers one question for each of Factorio's 214 items: **what is this in
Minecraft, and which mod owns it?** Every recipe in the pack is generated from it plus
`reference/factorio/recipes.json`, and every technology from it plus `data/technologies.json`.
It is the single review surface for the pack's naming
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
| `category` | Factorio's recipe category, for a recipe only a machine runs: `smelting` on the four the furnaces do. Absent means the hand crafts it. Not in the dump, entered by hand like `size` |
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

`category` is the other field the dump does not carry. Factorio's recipes belong to categories -
`crafting`, `smelting`, `chemistry`, `oil-processing` - and the character crafts only the first, so
iron plate, copper plate, steel plate and stone brick are `smelting` here. `gen_recipes.py` writes
that into the recipe as Facrafting's `category`, Facrafting keeps such a recipe out of the hand
panel and the hand queue, and a furnace is the machine that names the category it runs. The other
categories join the generator's list the day a machine runs them.

A fluid is a row like any other, `raw` where the dump gives it no recipe, and its `item` is the
fluid's id — `nauvis_fluids:steam`, `nauvis_fluids:crude_oil` — because a pipe and a machine meet
at the fluid capability and a barrel is an item with a row of its own. The barrel shortcut
`PLAN.md` once prescribed went when the pipe came with steam.

`data/fluid_recipes.json`, the recipes the dump cannot carry
-------------------------------------------------------------

The dump is keyed by product, one recipe per item, and it names fluids freely as ingredients and
as products: plastic is coal and petroleum gas, lubricant is heavy oil. What it cannot hold is a
recipe that is not one item's - the refinery's, which makes three fluids at once, and solid fuel's,
which three recipes make - and so those are transcribed from Wube's `recipe.lua` into
`data/fluid_recipes.json`, in the dump's units, with the machine category each belongs to. The
generator reads both files as one list: an ingredient the dump calls a `Liquid` becomes a
`fluid_ingredient`, a product that is one becomes a `fluid_result`, and a recipe with any fluid in
it gets no bench fallback, because a bench has no pipes. The fluid recipes are named after the
recipe rather than the product - `nauvis_fluids:solid_fuel_from_heavy_oil` - and a technology
that unlocks one resolves to that key without a line in the `unlocks` table.

`category` grew two values with them: `chemistry` on plastic, sulfur, sulfuric acid, lubricant,
battery and explosives, which the chemical plant runs, and `oil-processing` on the refinery's.
Coal liquefaction is left out of the file on purpose: a recipe no technology names is free from
the first tick, and its technology needs production science, which the tree does not reach.

One thing the file does not carry is Factorio's `fluidbox_index` - which port each fluid uses. The
machine keeps that instead: the refinery holds water and crude at its two inputs and heavy, light
and petroleum at its three outputs in that order, the chemical plant holds water at its first
input, and any other fluid takes the next free port in recipe order. Every recipe here lands where
Factorio puts it, including basic oil processing's crude at the second input and gas at the third
output; see `GAPS.md` for what a recipe from elsewhere would get.

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

The barrels need no rows: their fourteen recipes are in `data/fluid_recipes.json`, and a name in
that file resolves to its own key - `fill-water-barrel` to `nauvis_fluids:fill_water_barrel` -
so `fluid-handling` unlocks them by naming them. The filled barrels stay `raw` in the items
table, because the dump gives them no recipe of their own; the fluid recipes file is where
both halves of each pair live.

**A recipe name in neither table is a `GenError`, not a dropped unlock.** That is the same rule
the recipe generator applies to an unmapped ingredient, and for the same reason: "no opinion" and
"I could not work it out" must not have the same representation, or a stale table looks like a
design decision.

Triggers in `data/technologies.json`
------------------------------------

A technology is paid for in science or finished by a trigger, and the trigger is one of Factorio's
two. `craft-item` names an item in the `items` table and a count, and is heard from a bench, a
furnace and Facrafting's panel. `mine-entity` names a resource — `crude-oil`, whose mapped id
`nauvis_fluids:crude_oil` is the well block a pumpjack reports having mined — and is heard from the
machine that took it, through Facrafting's `MiningListeners`. The generator writes the first as
`"trigger": {"item": ...}` and the second as `"trigger": {"mine": ...}`, and refuses any other
type by name rather than dropping it.

A technology's `modifiers` go through whole - `{"type": "laboratory-speed", "modifier": 0.2}` -
with an `ammo_category` or `turret_id` folded into a `target`. The research mod sums the earned
ones by type and answers any machine through `nauvis_lib`'s `Bonuses`; the generator's summary
counts them by type so it is visible which types nothing reads yet. Read so far: the lab reads
`laboratory-speed`, the inserters `inserter-stack-size-bonus` and `bulk-inserter-capacity-bonus`,
the drills `mining-drill-productivity-bonus`.

`data/removals.json`, and the rule it enforces
---------------------------------------------

A second file beside this one, and the other direction: `mapping.json` says what Factorio's items
*are* here, `removals.json` says which of Minecraft's recipes stop existing because of it.
`tools/gen_removals.py` turns it into a built-in datapack in the `nauvis` mod - pack policy in the
pack mod, non-negotiable #3.

**A vanilla recipe is removed only when the pack can already do that job.** Every entry names a
`replaced_by` recipe and the build fails if that recipe is not shipped, so nothing is ever taken
away and left with nothing in its place.

Entries are one of two kinds:

- a **conflict** - a vanilla recipe making an item this table maps a Factorio item onto. Two
  recipes with different ingredient lists making the same item means one is wrong, and
  non-negotiable #1 says which. **This half is checked rather than listed**: ship a pack recipe
  for a vanilla stand-in and the build fails until vanilla's is removed too.
- a **bypass** - vanilla doing a job Factorio has a machine for. A judgement, and only ever a
  list.

And a third thing that is not a removal at all. **`mirrors`** names vanilla recipes a pack recipe
*reproduces* rather than replaces - same ingredients, same count, reachable both ways - and the
conflict check stands down for those. Planks are the one: the pack makes them in the crafting
panel at vanilla's own rate so the whole opening can be done there, and vanilla's recipe stays so
a log still becomes planks in the 2x2 inventory grid. Listing it as a removal would take that
away for nothing; leaving it out would fail the build, which is the check doing its job.

**`kept`** is the fourth, and it exists because the pack smelts iron. Once a pack recipe makes
`minecraft:iron_ingot`, the conflict check also catches the iron block coming back apart into nine
ingots and nine nuggets becoming one. Neither is a second way of making a plate - they are iron
changing shape, which Factorio has no blocks or nuggets to have an opinion about - so they are
listed with a reason each and the check stands down. What did go is every way vanilla smelts or
blasts ore into an ingot, raw or as a silk-touched block: those are the recipe, with the wrong
input or the wrong time.

The opening, and the tool ladder
---------------------------------

Minecraft's first five minutes are wood, planks, sticks, a wooden pickaxe, cobblestone, a stone
pickaxe - five steps to reach the point where this pack's own progression starts. The pack makes
it one: **four planks is a stone pickaxe**, and the wooden one is gone.

It is deliberately not a stone recipe. A stone pickaxe made of stone cannot be the first pickaxe,
because the first pickaxe is how you get stone.

**And a pickaxe is the axe, the shovel and the hoe as well.** `mineable/pickaxe` gains the other
three tags, so one tool does every job - at pickaxe speed, not at bare-hand speed. Four tools in
the hotbar is a Minecraft habit that has nothing to do with this pack, and a factory built one
block at a time does not want three of the four slots spent on which verb you are doing.

**The tiers are untouched**, and that is a correction rather than a decision left alone: an
earlier version emptied the `incorrect_for_<material>_tool` tags instead, which let a wooden
pickaxe mine obsidian and did nothing at all about the thing that was actually wrong. Which tool
does which job and how good that tool has to be are two different questions, and only the first
one is being merged.

`data/technologies.json` — the tree
-----------------------------------

Factorio's technology tree, and the second thing this pack generates rather than writes.
`tools/gen_technologies.py` turns it plus the table above into one JSON per technology in
`nauvis_research`.

A technology is paid for in one of two ways, and exactly one:

- **a cost** — N units, one of each science pack per unit, so many seconds a unit. A lab works
  through it.
- **a trigger** — *craft fifty iron plates*, *craft a lab* — and it finishes the moment that
  happens, with no lab and no science at all.

The triggers are what make the opening work. A new world mines by hand, smelts fifty iron plates
and has a boiler; smelts ten copper and has circuits, a lab, an inserter and poles; builds the lab
and has red science. Only then does research become a thing you build for, and the electric mining
drill is the first thing you pay for.

**The one guard is that the tree can be bootstrapped.** A trigger whose item nothing gates is
where a run begins, and everything else is reached from there; the generator walks that graph and
fails on any technology nothing could ever get to. That is the difference between an awkward
corner and a save that can never research anything.

Two smaller rules it enforces: a technology whose prerequisites are not in the tree is dropped
transitively, because a dangling edge is a technology nobody can start; and an
`unlock_recipes` name that is in neither table above is a `GenError` rather than a silently
dropped unlock.

Naming policy
-------------

Ids derive mechanically from the Factorio id, so no one has to decide them one at a time.
Display names come from the dump's `name` field.

Where two Factorio releases spell the same thing differently, the recipe dump's spelling wins and
everything else is renamed to match — `science-pack-1` rather than `automation-science-pack`,
`logistic-chest-storage` rather than `storage-chest`. One vocabulary throughout costs nothing and
keeps ids consistent with the spec; display strings are free to say whatever a player will be
looking for.

Stand-ins, for review
---------------------

These eighteen Factorio items resolve to something vanilla already has, so nothing new gets
registered. This is where judgement was applied and where disagreement is most likely — every
row is a one-word change in `mapping.json`. The stone furnace was the twentieth and is a machine
of the pack's own now, because a furnace that ran vanilla's recipes at vanilla's pace was not
Factorio's furnace.

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
| `lamp` | `minecraft:redstone_lamp` | |
| `landfill` | `minecraft:dirt` | |
| `concrete` | `minecraft:gray_concrete` | |
| `hazard-concrete` | `minecraft:yellow_concrete` | |
| `gate` | `minecraft:iron_door` | weakest of the eighteen; a real wall gate may be worth building |
| `water` | `minecraft:water` | a fluid, not an item; the one fluid vanilla already has. In the world it is `nauvis_fluids:water`, which a bucket and an offshore pump both turn into this |
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
- **`solid-fuel`, `uranium-235`, `uranium-238`** appear as raw in the dump but are products of
  chemistry and centrifuging in the real game. They belong to `nauvis_fluids` and their
  recipes have to be written by hand — the only place in the pack where that is true.

  `gen_recipes.py` confirms the hand-written set independently, by reporting every ingredient
  no generated recipe produces. It is those three plus `heavy-oil`, `light-oil` and
  `petroleum-gas` — and `uranium-processing`, whose 0.007 / 0.993 output is a probability that
  no crafting recipe can express. Seven items, and the list is derived rather than remembered,
  so it stays honest as the mapping changes.
