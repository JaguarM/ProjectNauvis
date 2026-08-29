Next session
============

Rewritten 2026-08-30. This file is only what to pick up now and how to run things; it is meant to
stay short and to be edited down as jobs finish. The durable material lives beside it:

| | |
|---|---|
| `PLAN.md` | the milestones and what the pack is for |
| `MAPPING.md` | what stands in for what |
| `API-26.2.md` | the 26.x renames, confirmed against decompiled sources |
| `ARCHITECTURE.md` | the rules a machine is built to, and the patterns worth copying |
| `PITFALLS.md` | things that compile, pass tests, and are still wrong. **Read before writing** |
| `GAPS.md` | what is deliberately missing, so a hole is not mistaken for a bug |

Where the pack stands
---------------------

A hundred and four gametests pass, `./gradlew build` is clean, and the client boots into a world.
Milestone 2 is done bar fast-replace; milestone 3 has its research half in, and four of its nine
items — the steel line and green science, which between them give `steel-processing` and
`science-pack-2` teeth and unblock everything else on the list.

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | 3×3, ten blocks; recipe selector, six slots, timed craft, screen, 10 FE/t |
| `nauvis_logistics:transport_belt` | half a block, walked over; a run is one object however long, two lanes, visible items, carries you |
| `nauvis_logistics:splitter` | 2×1 and directional; 50/50 per lane, overflows to the open side, sleeps when empty |
| `nauvis_logistics:burner_inserter` | takes behind, gives in front, 30-tick swing, fuel slot. Fuels itself off the belt it unloads |
| `nauvis_logistics:inserter` | the same on 2 FE/t and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:long_handed_inserter` | the same arm reaching two blocks. 3 FE/t, 17-tick swing, the only block that does not sleep perfectly |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen. A `ChestBlock`, so it has vanilla's lid and opens like one |
| `nauvis_logistics:steel_chest` | the same again at 54 slots and six rows, in lighter metal. Neither pairs |
| `nauvis_fluids:pipe` / `steam` | a run is one object however long; steam is a real fluid, so pipes and machines meet at the capability |
| `nauvis_power:boiler` | 3×2, seven blocks; burns fuel, steam out under the chimney |
| `nauvis_power:steam_engine` | 5×3, seventeen blocks; steam in at the open ends of its spine, 120 FE/t out |
| `nauvis_power:small_electric_pole` | four blocks, climbable, wires itself to whatever it reaches |
| `nauvis_research:lab` | 3×3, ten blocks, 8 FE/t; eats one of each pack the world's research asks for |
| `nauvis_research:science_pack_1` | red science — a copper plate and an iron gear wheel |
| `nauvis_research:science_pack_2` | green science — an inserter and a belt. The gate in front of the rest of milestone 3 |
| `neoprogressivematerials:steel_plate` | five iron plates and thirty-five seconds |
| `nauvis_research:technology` | 216 technologies, a synced datapack registry, generated. 25 are in the tree |
| `neoprogressiveautomation:burner_drill` | 2×2, five blocks |
| `neoprogressiveautomation:electric_drill` | 3×3, nine blocks; a half-block deck you walk over |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve assemblers, one
boiler runs twenty-four. None of that is identity; ids, ingredients and craft times are.

The crafting panel is Factorio's crafting menu — four tabs in Factorio's order, items interleaved
across mods, no "everything" tab, no grouping button. Research gates the boiler, the engine, the
pipe, the circuits, the lab, the inserter, the poles, red science, both drills and the assembler;
a new world smelts its way to a boiler on triggered technologies, then builds a lab. Four vanilla
recipes are removed — chest, furnace, hopper, hopper minecart — under the rule that a vanilla recipe
goes only when the pack can already do that job, which the build enforces.

The jobs
--------

### 1. Fast-replace by tier — the rest of milestone 2

A belt in hand already points the belt you click on the way you are facing, which is half of
Factorio's belt-laying gesture. The other half is that a *faster* belt replaces a slower one, and it
cannot be written until there is a second tier to hold. It is `BeltBlock.useItemOn`, and what
changes is: swap the *block* rather than a property — which remakes the block entity, so
`beltPlaced`/`beltRemoved` fire and `beltTurned` is not wanted on that path; carry the items across
the block entity being remade, which will not happen for free; hand the old belt back and pay for
the new one unless the player is in creative; and refuse to *downgrade*, or a stray click wrecks a
bus. The run needs no thought — a run never spans two tiers, so the line splits and rejoins itself.

**There is no underground belt and there will not be a `pipe-to-ground`.** Factorio needs both
because it is flat; this pack is the same game with a Y axis, so a belt crosses another by changing
level. All four ids are marked `skip` in `data/mapping.json` with the reason, nothing else in the
recipe graph uses them, and `gen_recipes.py --check` counts them as skipped rather than missing.

### 2. The rest of the milestone 3 items

`steel-plate`, `steel-chest` and `science-pack-2` are in, so `steel-processing` and
`science-pack-2` now unlock something and everything below is reachable. **Adding an item gives its
technology teeth with no change to the research code** — that held exactly as written, and none of
`nauvis_research` was touched.

What is left, in the order the tree wants them:

- **`medium-electric-pole`** (`electric-energy-distribution-1`). Two copper plates and two steel
  plates, and the only one that needs the grid to change: `PowerNetworkManager.WIRE_REACH` is a
  single constant and a medium pole reaches 9 rather than 7.5. Two poles are wired when the
  distance is within the *longer* of the two reaches, which is Factorio's rule. **`CELL_BITS` has
  to grow with it** — the pole index buckets into 8-block cubes and the 27-cell neighbourhood is
  only complete while every reach is under 8, so a reach of 9 silently misses a pole standing at a
  cell edge. Sixteen-block cells keep the neighbourhood and the invariant.
- **`assembling-machine-2`** (`automation-2`). An assembler that crafts faster; a tier is numbers
  on the block the way the long-handed inserter is.
- **`solar-panel`** (`solar-energy`). 3×3 and half a block high, so it is walked over. A generator
  like the steam engine as far as `PowerNetwork` is concerned — the new part is daylight.
- **`steel-furnace`** (`advanced-material-processing`). The biggest of the four, because the pack
  has no furnace machine at all: `stone-furnace` stands in as `minecraft:furnace`. A steel furnace
  that is only a faster vanilla furnace is a different job from one that is a machine of ours, and
  that decision comes before the block.

**`sulfur`, `battery` and the `accumulator` cannot be built yet, and it is not the code.** Sulfur
is thirty petroleum gas and thirty water, a battery is a plate each plus twenty sulfuric acid, and
an accumulator is five batteries — so the whole branch is behind oil processing, which is
milestone 5. The accumulator also wants `PowerNetwork`'s third case; see `GAPS.md`. Nothing is
gained by shipping recipes for them before the fluids exist.

### 3. More removals follow the items

The conflict half of `data/removals.json` is already waiting: the moment a mod ships a recipe for
`minecraft:redstone_lamp`, `cobblestone_wall`, `rail` or `iron_door`, the build fails until
vanilla's is removed — and three of the four are gated behind a technology in Factorio, so each is a
real research unlock. None can be done yet: the lamp needs an iron stick, the rail and the gate need
steel, and the wall belongs to `nauvis_military`, which does not exist.

Loose ends — small enough to finish in an afternoon
---------------------------------------------------

- **`/research` command.** `ResearchState.forget` already exists and is used only by gametests. A
  command to grant, forget and list would make the whole tree testable by hand.
- **"No effect yet" on a research row.** 128 of the 216 technologies unlock nothing here. The row
  can say so; it is true, cheap, and the only mitigation for the one real trap in the tree.
- **Tree polish, all optional.** No zoom (at 6×11 it fits a window), no search, no highlight of the
  path to a hovered node, and columns are top-aligned rather than centred. Centring is a drawing
  decision and belongs in `ResearchScreen`, not in `TechnologyLayout`.
- **`MachineParts` wants refinement** so the machines read as one family. Yannic has said so, and
  it is one file — but it is duplicated, so edit the original and run `check_duplicated.py --sync`.
- **The long-handed inserter's model** is a smoker-coloured cube — the third furnace body on a belt
  line. Telling the three inserters apart at a glance is the thing to fix.
- **The gametest datapack hole.** `GameTestServer` force-enables the `crafting_table` packs, so
  sixteen of nineteen timed recipes are shapeless bench recipes while tests run (see `PITFALLS.md`).
  A system property the run config sets and `ModPacks` reads would close it; the alternative changes
  a released mod's permanent ids and should not be taken.

The playtest, which is still owed
---------------------------------

Every job ends with `./gradlew build`, `:nauvis:runGameTestServer`, **and a client boot**. Three of
the last four bugs in this pack were found by a person looking at the game; one passed sixty-three
tests while being visibly wrong from three sides. Nothing in this repo can see a tab strip, a tech
tree or a corner readout, and nothing ever will — that is a permanent hole, not a gap in the suite.

**The first ten minutes, which is the biggest risk.** There is no hopper, and the furnace and chest
are Facrafting recipes now at Factorio's prices.

- gather five cobblestone and see whether making a furnace is **obvious**. If the panel is not the
  first place a Minecraft player looks, the answer is probably a message, not putting the recipe back;
- build a burner inserter and check it does everything a hopper did — chest into furnace is the case
  every Minecraft player builds. It is behind Automation, so the honest test is the whole opening:
  hand-mine, hand-craft a lab and a boiler, research Automation, *then* automate anything;
- and time it. The lab alone is ten circuits, ten gears and four belts by hand. If it drags, the
  lever is a trigger's count in `data/technologies.json`, not the machinery under it.

**The two chests**, which nobody has seen since they became real chests.

- put an iron chest down and open it. The lid should swing, the sound should be the copper chest's,
  and the box should read as metal rather than as a grey cube;
- put a steel chest beside it and check they are two chests and not one long one. `chests_hold_items`
  asserts the block state; **whether the two models sit side by side without looking like a mistake
  is only visible to a person**;
- and tell the two apart across a room. Iron is dark neutral grey and steel is light blue-grey,
  which is a decision made in `make_chest_textures.py` at 8x on a dark background and not in a
  world at midday.

**The research screen and the tree**, neither of which anyone has looked at.

- open a lab, press **Tech**, and see whether the row — a name, a cost, the items it hands over —
  reads as a technology list in 320 pixels;
- Automation should be the obvious first click, saying `10 x 10s` with a red science pack;
- feed a lab, power it, pick Automation, and watch the lab's bar and the research row move together;
- **leave the crafting panel open while the last unit finishes.** The assembler and the long-handed
  inserter should appear without the screen being closed. `RecipeLock.revision` is the mechanism and
  a gametest asserts the number moves; **whether the panel redraws is only visible to a person**;
- press **G**. Whether eleven nodes in a column read as a fan or a wall, whether the elbows are
  followable where three converge on `automation_2`, whether the state colours are distinguishable,
  and whether dragging to pan is discoverable. The grid underneath is proven; none of that is;
- the corner readout, for a long technology name; and the toast, which should fire once per
  technology and **never on relog** — if it fires on every login the advancement is not being saved.

**The older debt, which no boot has covered.**

- stand on a belt. Nothing in the suite tests that a belt carries anything — see `GAPS.md`;
- lay a coal belt with a burner inserter beside it and watch it pick its own fuel off the line;
- put an inserter on each side of one belt and check they fill two lanes rather than fighting;
- watch a long-handed inserter reach over a belt, and how long the pause feels once it runs dry.
  `IDLE_RECHECK_TICKS` is one second, chosen on arithmetic and never on somebody's eye. **If it
  reads as a jam rather than an arm, halve it** — one constant, linear cost;
- tell the three inserters apart at a glance on the same line;
- **whether alphabetical order *within* a tab reads wrong** to somebody who knows where Factorio
  puts things. It is the last part of the panel nobody has judged.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack. **Boot it after any model, fluid or plugin change** |
| `./gradlew :<mod>:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` / `runServerData` | models and language / loot and tags |
| `./gradlew build` | everything, including the seven checks |
| `python tools/gen_recipes.py --check` | the recipe diff, on its own |
| `python tools/gen_technologies.py --check` | the same for the tree. `--write` to regenerate |
| `python tools/gen_removals.py --check` | the vanilla recipes taken away. `--write` to regenerate |
| `python tools/check_models.py` | every model, texture and blockstate reference resolved — and footprints and belt speeds against `data/mapping.json` |
| `python tools/check_duplicated.py` | the copied packages, against each other. `--sync` to fix |
| `python tools/check_gametests.py` | every gametest, for a type registered as well as an instance |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python texture-workshop/make_belt_textures.py` | the belt's art, from ASCII maps. `--preview` for a sheet |
| `python texture-workshop/make_chest_textures.py` | the two chests' art. Not a map — see that file on why |
| `python texture-workshop/make_material_textures.py` | Neo Progressive Materials' item art |
| `./gradlew :nauvis:packConfig` | the pack's config over `run/config`. Every run task depends on it |

The seven checks — `checkRecipes`, `checkTechnologies`, `checkRemovals`, `checkModels`,
`checkDuplicated`, `checkGameTests`, `checkGuiLayout` — are in the root `build.gradle` and hang off
`:nauvis:check`. They read files and start nothing, so they cost a second between them.
`checkTechnologies` is the strict one: it fails on a file the generator **no longer produces**, not
only on one that differs, because a stale technology would still load and answer to nothing.

**Adding a subsystem mod** is routine: a subproject in `settings.gradle`, a `build.gradle` with the
ids changed, a `src/main/templates/META-INF/neoforge.mods.toml`, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` and its namespace in
`pack_gametest_namespaces`. `nauvis_power/` is the fullest template; `nauvis_research/` is the
newest and was made by following exactly that list, so its diff is what adding a mod costs.

**Recipes** generate into a staging directory —
`python tools/gen_recipes.py --only <modid> --out <tmp>` — then copy across only the files for items
that exist. `--write` writes all of that mod's recipes, and a recipe naming an unregistered item is
a load error.

**Pack settings** for a mod the pack ships go in `nauvis/pack/config/`, not `run/config`: `run/` is
gitignored, so a setting edited there is a change nobody else sees. Keep each file in the form
NeoForge writes it and put the reasoning in that directory's README.
