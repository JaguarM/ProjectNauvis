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

A hundred and thirteen gametests pass, `./gradlew build` is clean, and the client boots into a
world. **Milestone 2 is closed**: the fast belt is in and a faster belt in hand replaces the one it
is clicked on. Milestone 3 has its research half in, and five of its nine items — the steel line,
green science and the medium pole, which between them give `steel-processing`, `science-pack-2` and
`electric-energy-distribution-1` teeth. The big pole came with the medium one and the substation
came with the big one, because four pole tiers is one piece of work rather than four.

**A belt tier turned out to cost a file and a palette**, which is what the three decisions behind
the belt were for: speed is a constant on a subclass rather than a field, so a tier is a class; the
run is keyed on the *block*, so two tiers are two runs with nothing written to make that true; and
`make_belt_textures.py` renders a tier from a palette and derives the tread's animation from the
speed in `data/mapping.json`. One block entity type still covers every tier there will ever be. The
express belt is the same file again, behind `logistics-3`, which wants blue science.

**All four poles are climbable multi-blocks on the same `MachineShape` a boiler is.** The pole used
to have a mechanism of its own — a `PolePart` enum with `Multiblock`'s four rules written out again
— and the tiers ended it: five blocks tall needs a five-value enum and two-by-two needs two more
axes, both of which `MachineShape` had all along. `PolePart` is gone, and a pole's `part` property
is an int like every other machine's.

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | 3×3, ten blocks; recipe selector, six slots, timed craft, screen, 10 FE/t |
| `nauvis_logistics:transport_belt` | half a block, walked over; a run is one object however long, two lanes, visible items, carries you |
| `nauvis_logistics:fast_transport_belt` | the same at 3.75 tiles a second, in red. Five gears and a belt, behind `logistics-2` |
| a belt in hand | **puts that belt there, pointing the way you face** — another tier swaps the block, keeps the load and pays for it. Factorio's fast-replace, either way up |
| `nauvis_logistics:splitter` | 2×1 and directional; 50/50 per lane, overflows to the open side, sleeps when empty |
| `nauvis_logistics:burner_inserter` | takes behind, gives in front, 30-tick swing, fuel slot. Fuels itself off the belt it unloads |
| `nauvis_logistics:inserter` | the same on 2 FE/t and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:long_handed_inserter` | the same arm reaching two blocks. 3 FE/t, 17-tick swing, the only block that does not sleep perfectly |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen. A `ChestBlock`, so it has vanilla's lid and opens like one |
| `nauvis_logistics:steel_chest` | the same again at 54 slots and six rows, in lighter metal. Neither pairs |
| `nauvis_fluids:pipe` / `steam` | a run is one object however long; steam is a real fluid, so pipes and machines meet at the capability |
| `nauvis_power:boiler` | 3×2, seven blocks; burns fuel, steam out under the chimney |
| `nauvis_power:steam_engine` | 5×3, seventeen blocks; steam in at the open ends of its spine, 120 FE/t out |
| `nauvis_power:small_electric_pole` | 1×1×4, wood. Reaches 7.5, supplies 5×5 |
| `nauvis_power:medium_electric_pole` | 1×1×5, anvil-grey. Reaches 9, supplies 5×5 |
| `nauvis_power:big_electric_pole` | 2×2×6, iron. Reaches 30, supplies 4×4. Twenty-four blocks, one item |
| `nauvis_power:substation` | 2×2×5, deepslate. Reaches 18, supplies 18×18. **No recipe or technology yet** |
| `nauvis_research:lab` | 3×3, ten blocks, 8 FE/t; eats one of each pack the world's research asks for |
| `nauvis_research:science_pack_1` | red science — a copper plate and an iron gear wheel |
| `nauvis_research:science_pack_2` | green science — an inserter and a belt. The gate in front of the rest of milestone 3 |
| `neoprogressivematerials:steel_plate` | five iron plates and thirty-five seconds |
| `nauvis_research:technology` | 216 technologies, a synced datapack registry, generated. 26 are in the tree |
| `/research` | grant, forget, start, stop, list, info, all, reset. Gamemaster only. **Grant and forget cascade** — grant brings the prerequisites, forget takes the dependants |
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

### 1. The fast splitter, which is the belt tier's other half

`logistics-2` unlocks three things and the pack models two of them — the fast belt, which is in, and
the fast splitter, which is not. Its recipe is ten circuits, ten gears and a splitter; the
technology already ships naming `nauvis_logistics:fast_splitter`, so the recipe file and the block
are the whole of it.

**It is not the one-file job the belt was, and the reason is worth knowing before starting.** The
belt paid for its tiers in advance: `speed()` is abstract on `BeltBlock` and every tier is a
subclass with a constant, because `createBlockStateDefinition` runs before any field of a subclass
exists. `SplitterBlock` did not — `SPEED` is a `public static final` on the concrete class, and
`SplitterBlockEntity.tick` and `SplitterBlock.stepOn` both read it *statically*. So a second
splitter that differed only in its constant would run at the first one's speed and pass every test
in the file. The work is: make `SplitterBlock` abstract with `speed()` and `factorioId()` on it,
exactly as `BeltBlock` has them; register `fast_splitter` against the same block entity type, which
already holds no speed of its own; and one gametest that a fast splitter moves items at its own
speed, which is the test that would have caught the static read.

Fast-replace does **not** extend to it, and that is a decision rather than an omission: a splitter
is two blocks and a multiblock anchor, so swapping one in place is a different problem from swapping
a belt, and clicking a splitter with a splitter in hand does nothing today.

### 2. The rest of the milestone 3 items

`steel-plate`, `steel-chest`, `science-pack-2` and `medium-electric-pole` are in. **Adding an item
gives its technology teeth with no change to the research code** — that held exactly as written for
all four, and none of `nauvis_research` was touched.

Wire reach is per pole now rather than one constant, and two poles are wired when the distance is
within the **longer** of the two reaches, which is Factorio's rule and the only one that makes a
medium pole useful at the end of an ordinary run. `CELL_BITS` went from 8-block cells to 16-block
ones with it: the pole index's 27-cell neighbourhood is only complete while every reach is under
the cell size, and a static block in `PowerNetworkManager` now throws if a tier is ever added that
breaks that.

What is left, in the order the tree wants them:

- **The substation's recipe and technology.** The block is built and creative-only. Factorio's
  recipe is five advanced circuits, which is plastic, which is oil — milestone 5 — but it need not
  wait that long: mark `advanced-circuit` `pending` in `data/mapping.json` and the recipe ships with
  a `neoforge:registered` condition that regenerates away when the circuit lands. The technology is
  `electric-energy-distribution-2`, which is not in the tree and whose prerequisites
  (`advanced-electronics`) are not either, so that is two JSON entries and the generator will say
  if they do not bootstrap.
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

**Oil blocks less than it looks like.** Sixty-five mapped items have no oil anywhere in their tree,
and the ones with a technology already waiting for them are: `fast-inserter`, `assembling-machine-2`,
`solar-panel`, `steel-furnace`, `radar`, `repair-pack` and `iron-stick`, plus the whole of
`nauvis_military`, which has no mod yet. Of those, **the solar panel is the only one that adds a
mechanic rather than a tier** — and it needs no accumulator to be worth building, because solar plus
a boiler for the night is what Factorio itself does before accumulators exist. The steel furnace is
the one to leave: it is oil-free but it forces a decision that comes before the block, which is
whether this pack has a smelting machine of its own at all — `stone-furnace` stands in as
`minecraft:furnace` today.

### 3. More removals follow the items

The conflict half of `data/removals.json` is already waiting: the moment a mod ships a recipe for
`minecraft:redstone_lamp`, `cobblestone_wall`, `rail` or `iron_door`, the build fails until
vanilla's is removed — and three of the four are gated behind a technology in Factorio, so each is a
real research unlock. None can be done yet: the lamp needs an iron stick, the rail and the gate need
steel, and the wall belongs to `nauvis_military`, which does not exist.

Loose ends — small enough to finish in an afternoon
---------------------------------------------------

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

**`/research`, first**, because it is what makes everything below reachable without playing
forwards to it. `/research grant nauvis_research:solar_energy` puts the tree where you need it;
`/research all` opens everything the pack can currently reach; `/research reset` puts it back.

**The four poles**, which nobody has seen and which are the biggest visual change here.

- stand all four in a row. They go 4, 5, 6, 5 blocks tall and 1×1, 1×1, 2×2, 2×2 wide, in wood,
  anvil-grey, iron and deepslate. **Whether that reads as a ladder is the whole question** — it was
  decided in four shape files and a texture line, and nothing in this repo can see it;
- climb a big pole. All four are in `minecraft:climbable` and the legs collide while the ring on
  top does not, so going up one and standing on the ring is a thing that either works or is
  maddening;
- look at a big pole's top from below and from above. The four heads' arms are supposed to meet
  across the seams as one closed square; if a leg's arm stops short or two of them overlap, that is
  `PoleBoxes.LEG_HEAD` and it will be obvious;
- run a wire between a small pole and a big one. The two ends attach at different heights and the
  big end comes off the middle of a 2×2 footprint rather than off one of its legs, which is new and
  is drawn rather than tested;
- put a small pole down, walk eight blocks, put a medium one down, and see the wire appear. Eight
  is past the small pole's reach and inside the medium one's, so that is the tier working;
- and put a substation in the middle of a field of machines. Eighteen by eighteen is most of a
  chunk, and whether that feels generous or absurd at Minecraft's scale is a judgement no test
  makes.

**The red belt and fast-replace**, which are new and which nothing in this repo can judge.

- lay a yellow line, then walk *along* it with red belts in hand and click each one. **Whether that
  reads as upgrading a line rather than as breaking it** is the whole question — the belt under you
  changes colour, points where you are walking and keeps what was on it, and you get the yellow one
  back;
- click a belt that is turning a corner, from the side. It will point where you are looking, because
  a belt in hand places a belt — so upgrading a corner is done from along the line, not across it.
  **Whether that reads as Factorio or as a trap is the thing to judge**;
- run a yellow line into a red one and watch the join. The two are two runs and items cross at the
  seam; whether the tread's change of pace reads as intended or as a stutter is a thing to look at;
- stand on a red belt. It carries at twice the speed, and half a block is still half a block;
- and tell red from yellow across a base, which is the thing the palette was chosen for at 8x on a
  dark background rather than in a world at midday.

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
