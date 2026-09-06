Next session
============

Rewritten 2026-09-06. This file is only what to pick up now and how to run things; it is meant to
stay short and to be edited down as jobs finish. The durable material lives beside it, and
`CLAUDE.md` says which file is for what. **Read `PITFALLS.md` before writing.**

Where the pack stands
---------------------

Every gametest passes, `./gradlew build` is clean, and the client boots into a world. **Milestone 2
is closed.** Milestone 3 has its research half and every item but the accumulator — the steel line,
green science, the four poles, assembling machine 2, the solar panel and the three furnaces; the
substation's and the electric furnace's recipes wait on the advanced circuit, and the accumulator
waits on the refinery. **Milestone 4 has begun**: oil wells in the ground, the pumpjack,
`oil-gathering` and `oil-processing` in the tree, and the five technologies from plastics to the
substation behind blue science. **The world's water is the pack's own now**, the offshore pump
draws from it and from nothing a bucket poured, and the boiler boils it: a new world's first
factory starts at a shoreline. Every block the pack registers is pickaxe work, but for the water. How each of these is built is `ARCHITECTURE.md`; what is deliberately missing
from each is `GAPS.md`.

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | 3×3, ten blocks; recipe selector, six slots, timed craft at **crafting speed 0.5**, screen, 10 FE/t |
| `nauvis_machines:assembling_machine_2` | the same at speed 0.75 and 20 FE/t, in blue. Behind `automation-2` |
| `nauvis_machines:stone_furnace` | 2×2, five blocks; a hearth you walk over and a stack whose mouth is fire while it runs. Smelts Factorio's four recipes at speed 1, burning what a vanilla furnace burns — only while working. **A machine of ours now**, not `minecraft:furnace` |
| `nauvis_machines:steel_furnace` | the same at speed 2, in iron. Behind `advanced-material-processing` |
| `nauvis_machines:electric_furnace` | 3×3, ten blocks, speed 2, 24 FE/t, a hood that glows. Recipe in, waiting on the advanced circuit |
| smelting | iron plate, copper plate, steel plate and stone brick are `smelting` recipes: a furnace runs them and the panel never offers them. A furnace asks the tree before smelting steel, and reports every plate it makes, which is what finishes `steam-power` |
| `nauvis_logistics:transport_belt` | half a block, walked over; a run is one object however long, two lanes, visible items, carries you |
| a belt line | climbs and descends a step at a time, like rails. The ramp is drawn in the lower block; four quarter-block stairs under a 45° slab, so you walk up it |
| `nauvis_logistics:fast_transport_belt` | the same at 3.75 tiles a second, in red. Five gears and a belt, behind `logistics-2` |
| a belt in hand | **puts that belt there, pointing the way you face** — another tier swaps the block, keeps the load and pays for it. Factorio's fast-replace, either way up |
| `nauvis_logistics:splitter` | 2×1 and directional; 50/50 per lane, overflows to the open side, sleeps when empty |
| `nauvis_logistics:fast_splitter` | the same at the red belt's speed, so a red line is not throttled where it splits |
| `nauvis_logistics:burner_inserter` | takes behind, gives in front, 30-tick swing, fuel slot. Fuels itself off the belt it unloads |
| `nauvis_logistics:inserter` | the same on 2 FE/t and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:long_handed_inserter` | the same arm reaching two blocks. 3 FE/t, 17-tick swing, the only block that does not sleep perfectly |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen. A `ChestBlock`, so it has vanilla's lid and opens like one |
| `nauvis_logistics:steel_chest` | the same again at 54 slots and six rows, in lighter metal. Neither pairs |
| `nauvis_fluids:pipe` / `steam` / `crude_oil` | a run is one object however long; steam and crude oil are real fluids, so pipes and machines meet at the capability |
| `nauvis_fluids:crude_oil` (block) | an oil well: unbreakable ground with a number in it. 300000 is 100%, 10 off a cycle, floors at 60000 or a fifth of its start. Fields of 3–8, four apart, one per 300 chunks, none within 150 of the origin |
| `nauvis_fluids:pumpjack` | 3×3, ten blocks, centred on a well or nowhere; 12 FE/t; 10 × yield a second into a 1000 tank, a cycle capped at the tank; oil leaves the north-east corner's north face and turns with the machine. Snaps to the well, x-rays every well while in hand |
| `nauvis_fluids:water` | **the water of every lake and sea the world generates**, in place of `minecraft:water`: swum in, boated on and scooped exactly as before, but a bucket of it is a water bucket, a poured bucket is vanilla's water, and two sources never make a third. A lake is as big as the world made it |
| `nauvis_fluids:offshore_pump` | 1×2, two blocks; body on the shore, intake ahead of it over the water. 40 water a tick into a 200 tank, no power, `minecraft:water` out of the body's back. **Stands only at natural water** - not a puddle, not a flow - and says which when it will not |
| `nauvis_power:boiler` | 3×2, seven blocks; **boils water**: water in at both ends of the front row, two a tick, two steam out under the chimney, and nothing at all without water. A row of boilers passes water along itself, end to end |
| `nauvis_power:steam_engine` | 5×3, seventeen blocks; steam in at the open ends of its spine, 120 FE/t out |
| `nauvis_power:small_electric_pole` | 1×1×4, wood. Reaches 7.5, supplies 5×5 |
| `nauvis_power:medium_electric_pole` | 1×1×5, anvil-grey. Reaches 9, supplies 5×5 |
| `nauvis_power:big_electric_pole` | 2×2×6, iron. Reaches 30, supplies 4×4. Twenty-four blocks, one item |
| `nauvis_power:substation` | 2×2×5, deepslate. Reaches 18, supplies 18×18. Recipe and technology in, both waiting on the advanced circuit |
| `nauvis_power:solar_panel` | 3×3, half a block, walked over; 8 FE/t at noon scaled by the sky, nothing at night or under a roof |
| `nauvis_research:lab` | 3×3, ten blocks, 8 FE/t; eats one of each pack the world's research asks for |
| `nauvis_research:science_pack_1` | red science — a copper plate and an iron gear wheel |
| `nauvis_research:science_pack_2` | green science — an inserter and a belt. The gate in front of the rest of milestone 3 |
| `neoprogressivematerials:steel_plate` | five iron plates and sixteen seconds, in a furnace |
| `nauvis_research:technology` | a synced datapack registry, generated from `data/technologies.json`: the early game and the branches the pack can reach, out of Factorio's 216. Five are finished by a trigger a furnace, a bench or a pumpjack fires, not by a lab |
| the tech screen | **a list and a search box on the left, one technology's neighbourhood in the middle, its cost and unlocks on the right.** Factorio's shape. Clicking a node re-centres the picture on it; the button on the right is what starts a research |
| a view | the selection, **every** ancestor, and descendants two deep by longest path. A node that needs technologies the picture does not show says so with `+n` in its corner |
| the list | sorted into three blocks and coloured by them — ready in yellow-brown, unreachable in red, researched in green at the bottom. The current research sits above the rest of the first block |
| research progress | **kept per technology.** Switching away from one and back finds it where it was left, which is Factorio's rule |
| `/research` | grant, forget, start, stop, list, info, all, reset. Gamemaster only. **Grant and forget cascade** — grant brings the prerequisites, forget takes the dependants |
| `/oil` | `field` puts an oil field where you stand, as worldgen would; `well` puts one well under your feet. Gamemaster only. The tool for a superflat world, which runs no features |
| `nauvis_mining:burner_mining_drill` | 2×2, five blocks; mines the 2×2 it stands on. Digs down, not across — job 1 |
| `nauvis_mining:electric_mining_drill` | 3×3, nine blocks, mining its own 3×3; a half-block deck you walk over |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve first-tier
assemblers, one boiler runs twenty-four. None of that is identity; ids, ingredients, craft times,
footprints and machine speeds are.

The crafting panel is Factorio's crafting menu — four tabs in Factorio's order, items interleaved
across mods, no "everything" tab, no grouping button. A new world smelts its way to a boiler on
triggered technologies, then builds a lab. Twenty vanilla recipes are removed — the chest, the
furnace, the hopper and its minecart, the two pickaxes, and the fourteen ways vanilla smelts or crafts
iron, copper and stone brick — under the rule that a vanilla recipe goes only when the pack can
already do that job, which the build enforces. Blocks and nuggets coming back apart into ingots are
`kept`, and say why.

The jobs
--------

### 1. Make the drills Factorio's drills

**`nauvis_mining` is a fork of Neo Progressive Automation**, taken so the drills could have
Factorio's ids: `nauvis_mining:burner_mining_drill` and `nauvis_mining:electric_mining_drill`,
where a released mod was stuck with `burner_drill`. The fork is the ids and the wiring — the
behaviour that came across is still Progressive Automation's, and **that is the job**. NPA is
untouched in its own repo and stays released; nothing here changes it.

What a Factorio drill does that this one does not:

- **It sits on an ore patch and eats it.** Ours digs *downwards*, one block at a time — a quarry
  rather than a drill. Factorio's takes the resource out of the tiles under it and leaves the
  terrain alone. Crumbling Ore is already the pack's answer to a patch that runs out, and the oil
  well is the pack's shape for a resource that is a block with a number in it — `ARCHITECTURE.md`
  says an ore patch wants the same shape — so the three want designing together. **The area is
  right now, and only the direction is wrong**: a drill covers exactly the ground it stands on, 2×2
  under a burner and 3×3 under an electric. `DigArea` is where that lives and the hover outline
  reads it, so whatever replaces the downward digging inherits both.
- **It has no dig modes.** Ours has three on a button — ore only, clear and fill, clear — and a
  fill slot to pay for the middle one. Both are answers to being a quarry and both go when the
  digging does. The shovel slot is already gone.
- **It outputs to the front, onto a belt.** Ours pushes into any container beside it. Factorio's
  has one output tile it drops onto — which is what makes a drill-and-belt line a thing you lay
  out rather than a chest you place.
- **Its modules are Factorio's modules.** Ours are `speed`, `efficiency` and `range`, invented
  for NPA. Factorio has speed, efficiency and productivity in three tiers each, they are
  `nauvis_machines`' to own per `PLAN.md`, and `range` has no counterpart at all — a drill's area
  is the entity's. **The three module items in `nauvis_mining` are the first thing to resolve**,
  because they are ids in a namespace that is now the pack's.

**The pickaxe slot is not on this list, and that is a decision rather than an oversight.** A
Factorio drill carries no tools; ours wants a pickaxe and will keep wanting one, because Yannic
likes it — a drill you have to hand a tool to reads as a Minecraft machine, and the pack is
Factorio in Minecraft rather than Factorio ported to it. Do not put this back on the list.

None of the rest is a small change, and none of it is urgent: the drills work, their tests pass,
and their recipes and footprints are already Factorio-correct. Do it as one designed piece rather
than four.

### 2. Oil, the rest of it

`sulfur`, `battery` and the `accumulator` cannot be built yet, and it is not the code: sulfur is
petroleum gas and water, a battery is sulfuric acid, and an accumulator is five batteries — the
whole branch waits on the refinery. The accumulator also wants `PowerNetwork`'s third case; see
`GAPS.md`.

- **The refinery, which is the hard part.** Three outputs that block each other — heavy, light,
  petroleum — into separate tanks, and a full tank stalls the machine. That is the puzzle Factorio's
  oil *is*, and it needs real capacity and back-pressure, not barrels. `FluidNetwork` already
  carries any fluid, one per run; what it lacks is a tank block and a machine with several fluid
  ports. The `storage-tank` is 3×3 and mapped. `oil-processing` already unlocks both machines.
- **The oil recipes have no source.** `reference/factorio/recipes.json` carries crude, heavy, light
  and petroleum as raw inputs with null recipes, and `data/mapping.json` maps the processing recipes
  to null. Non-negotiable #2 says recipes are generated, so the refinery's and the chemical plant's
  recipes need a second data source — Wube's `recipe.lua` — and a fluid-aware `gen_recipes.py`.
  That is the real gap; the machines are the usual work.
- **Plastics, sulfur and flammables** hang off oil processing and are in the tree; they mean
  nothing until the refinery makes petroleum gas.

### 3. More removals follow the items

The conflict half of `data/removals.json` is already waiting: the moment a mod ships a recipe for
`minecraft:redstone_lamp`, `cobblestone_wall`, `rail` or `iron_door`, the build fails until
vanilla's is removed — and three of the four are gated behind a technology in Factorio, so each is a
real research unlock. None can be done yet: the lamp needs an iron stick, the rail and the gate need
steel, and the wall belongs to `nauvis_military`, which does not exist.

Loose ends — small enough to finish in an afternoon
---------------------------------------------------

- **`MachineParts` wants refinement** so the machines read as one family. Yannic has said so, and
  it is one file now, in `nauvis_lib`.
- **The long-handed inserter's model** is a smoker-coloured cube — the third furnace body on a belt
  line. Telling the three inserters apart at a glance is the thing to fix.
- **The pumpjack's pump** is a nodding donkey inside one block, and reads as a small box from the
  item slot. Bigger geometry means a taller head cell; it is boxes in `PumpjackShape`, and
  `render_model.py` shows the result without a boot.
- **The furnace's fire is a lava texture on the stack's mouth**, chosen because it is opaque and
  animated and nothing else vanilla ships is both. Whether a brazier on a corner reads as a furnace
  is a judgement for eyes; the boxes are in `FurnaceShape` and `ElectricFurnaceShape`.
- **Facrafting's panel still paints its recipe grid.** The machine screens are settled: the dark
  panel Yannic likes, with vanilla's slot sprite where there is a slot, vanilla's furnace flame
  where there is a fire, and the charge bolt where there is electricity, all drawn the way vanilla
  draws its flame. The panel's grid of recipe squares is the one painted thing left, and whether
  it should be vanilla's slot sprite too is a judgement for eyes.

The playtest
------------

Every job ends with `./gradlew build`, `:nauvis:runGameTestServer`, **and a client boot**.
**A model can be looked at** without one: `python tools/render_model.py <model id> out.png` draws
any block model from the item slot's angle, holes and all. Render every new machine's `_inventory`
model before asking anybody to boot a client; the boot is still owed, for lighting and for how the
thing sits in a world.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack. **Boot it after any model, fluid or plugin change** |
| `./gradlew :<mod>:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` / `runServerData` | models and language / loot and tags |
| `./gradlew build` | everything, including the six checks |
| `python tools/gen_recipes.py --check` | the recipe diff, on its own |
| `python tools/gen_technologies.py --check` | the same for the tree. `--write` to regenerate |
| `python tools/gen_removals.py --check` | the vanilla recipes taken away. `--write` to regenerate |
| `python tools/check_models.py` | every model, texture and blockstate reference resolved — and footprints, belt speeds and texture opacity |
| `python tools/render_model.py <model> out.png` | **draws a model to a PNG from the item slot's angle**, so a machine can be looked at without a boot. `--view side` or `top` for one cell. Needs Pillow and numpy |
| `python tools/check_gametests.py` | every gametest, for a type registered as well as an instance |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python texture-workshop/make_belt_textures.py` | the belt's art, from ASCII maps. `--preview` for a sheet |
| `python texture-workshop/make_chest_textures.py` | the two chests' art. Not a map — see that file on why |
| `python texture-workshop/make_miner_textures.py` | the two drills' art, from ASCII maps. Came across with the fork |
| `python texture-workshop/make_material_textures.py` | Neo Progressive Materials' item art |
| `./gradlew :nauvis:packConfig` | the pack's config over `run/config`. Every run task depends on it |

The six checks — `checkRecipes`, `checkTechnologies`, `checkRemovals`, `checkModels`,
`checkGameTests`, `checkGuiLayout` — are in the root `build.gradle` and hang off
`:nauvis:check`. They read files and start nothing, so they cost a second between them.
`checkTechnologies` is the strict one: it fails on a file the generator **no longer produces**, not
only on one that differs, because a stale technology would still load and answer to nothing.

**A gametest run sees the recipes the pack ships.** `GameTestServer` force-enables every datapack
it can see, bench-recipe packs included, so each `gameTestServer` run passes
`-Djaguarm.benchRecipePacks=false` and `nauvis_lib`'s `BenchRecipePacks` offers no pack under it — the
one state vanilla cannot override is absent. `nauvis:timed_recipes_are_timed` holds it there.

**Adding a subsystem mod** is routine: a subproject in `settings.gradle`, a `build.gradle` with the
ids changed and `implementation project(':nauvis_lib')` in it, a
`src/main/templates/META-INF/neoforge.mods.toml` declaring `nauvis_lib` required, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` and its namespace in
`pack_gametest_namespaces`. `nauvis_power/` is the fullest template; `nauvis_research/` is the
newest written from scratch and was made by following exactly that list, so its diff is what
adding a mod costs.

**Bringing a sibling repo in as a subproject** costs that list plus six more places, which
`nauvis_mining` is the worked example of: the `includeBuild` and its `dependencySubstitution` go
from `settings.gradle`, its version from `gradle.properties`, and it stops being a special case
in `check_models.py`'s `MODS`, `check_gametests.py`'s namespaces and
`gen_recipes.py`/`gen_removals.py`'s `SIBLING_REPOS` — that last one silently resolves
to `../<mod_id>` if you leave the entry in, and the recipe check reports the mod's files as
*missing* rather than failing.

**Recipes** generate into a staging directory —
`python tools/gen_recipes.py --only <modid> --out <tmp>` — then copy across only the files for items
that exist. `--write` writes all of that mod's recipes, and a recipe naming an unregistered item is
a load error.

**Pack settings** for a mod the pack ships go in `nauvis/pack/config/`, not `run/config`: `run/` is
gitignored, so a setting edited there is a change nobody else sees. Keep each file in the form
NeoForge writes it and put the reasoning in that directory's README.
