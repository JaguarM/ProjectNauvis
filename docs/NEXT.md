Next session
============

Rewritten 2026-09-06. This file is only what to pick up now and how to run things; it is meant to
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

A hundred and fifty-six gametests pass, `./gradlew build` is clean, and the client boots into a
world. **Milestone 2 is closed.** Milestone 3 has its research half and every item but the
accumulator — the steel line, green science, the four poles, assembling machine 2, the solar panel
and the three furnaces; the substation's and the electric furnace's recipes wait on the advanced
circuit, and the accumulator waits on the refinery. **Milestone 4 has begun**: oil wells in the
ground, the pumpjack, `oil-gathering` and `oil-processing` in the tree, and the five technologies
from plastics to the substation behind blue science. Every block the pack registers is pickaxe work.
How each of these is built is `ARCHITECTURE.md`; what is deliberately missing from each is `GAPS.md`.

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
| `nauvis_power:boiler` | 3×2, seven blocks; burns fuel, steam out under the chimney |
| `nauvis_power:steam_engine` | 5×3, seventeen blocks; steam in at the open ends of its spine, 120 FE/t out |
| `nauvis_power:small_electric_pole` | 1×1×4, wood. Reaches 7.5, supplies 5×5 |
| `nauvis_power:medium_electric_pole` | 1×1×5, anvil-grey. Reaches 9, supplies 5×5 |
| `nauvis_power:big_electric_pole` | 2×2×6, iron. Reaches 30, supplies 4×4. Twenty-four blocks, one item |
| `nauvis_power:substation` | 2×2×5, deepslate. Reaches 18, supplies 18×18. Recipe and technology in, both waiting on the advanced circuit |
| `nauvis_power:solar_panel` | 3×3, half a block, walked over; 8 FE/t at noon scaled by the sky, nothing at night or under a roof |
| `nauvis_research:lab` | 3×3, ten blocks, 8 FE/t; eats one of each pack the world's research asks for |
| `nauvis_research:science_pack_1` | red science — a copper plate and an iron gear wheel |
| `nauvis_research:science_pack_2` | green science — an inserter and a belt. The gate in front of the rest of milestone 3 |
| `neoprogressivematerials:steel_plate` | five iron plates and thirty-five seconds |
| `nauvis_research:technology` | 216 technologies, a synced datapack registry, generated. 36 are in the tree; two are finished by a trigger a machine fires |
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
  it is one file — but it is duplicated, so edit the original and run `check_duplicated.py --sync`.
- **The long-handed inserter's model** is a smoker-coloured cube — the third furnace body on a belt
  line. Telling the three inserters apart at a glance is the thing to fix.
- **The pumpjack's pump** is a nodding donkey inside one block, and reads as a small box from the
  item slot. Bigger geometry means a taller head cell; it is boxes in `PumpjackShape`, and
  `render_model.py` shows the result without a boot.
- **The furnace's fire is a lava texture on the stack's mouth**, chosen because it is opaque and
  animated and nothing else vanilla ships is both. Whether a brazier on a corner reads as a furnace
  is a judgement for eyes; the boxes are in `FurnaceShape` and `ElectricFurnaceShape`.
- **The painted screens want vanilla's look.** Yannic has said vanilla's interface fits
  Minecraft's art far better than the flat panels, and the furnace is on vanilla's furnace texture
  now. The assembler, the boiler, the lab and the burner inserter are still painted, and so is
  Facrafting's panel itself; they want the same treatment as one piece, on vanilla's nine-slice
  sprites where no vanilla screen has the right shape.

The playtest, which is still owed
---------------------------------

Every job ends with `./gradlew build`, `:nauvis:runGameTestServer`, **and a client boot**. Three of
the last four bugs in this pack were found by a person looking at the game. Nothing in this repo
can see a tab strip, a tech tree or a corner readout, and nothing ever will — that is a permanent
hole, not a gap in the suite. **A model can be looked at, though**: `python tools/render_model.py
<model id> out.png` draws any block model from the item slot's angle, holes and all. Render every
new machine's `_inventory` model before asking anybody to boot a client; the boot is still owed, for
lighting and for how the thing sits in a world.

Seen so far: the solar panel's output follows the sun, oil fields are found at the new density, and
the pumpjack's top faces were slits until the anvil sprite went. Not yet seen: everything below.

**`/research`, first**, because it is what makes everything below reachable without playing
forwards to it. `/research grant nauvis_research:solar_energy` puts the tree where you need it;
`/research all` opens everything the pack can currently reach; `/research reset` puts it back.
`/oil field` puts oil where you stand.

**The first ten minutes, which is the biggest risk.** There is no hopper, the chest is a Facrafting
recipe at Factorio's price, and **the stone furnace is a two-by-two machine of the pack's own** —
five stone in the panel, and the only thing that smelts.

- gather five cobblestone and see whether making a furnace is **obvious**. If the panel is not the
  first place a Minecraft player looks, the answer is probably a message, not putting the recipe back;
- place it, open it, and put raw iron and coal in. A plate should come out in three and a half
  seconds, the stack's mouth should be fire while it works, and the hover should say *Smelting
  Iron Ingot*. Put a stick in and it should refuse it. Then take the ore out mid-smelt and watch it
  go dark and keep its coal — a furnace here burns only while it works, which is Factorio's rule;
- hand it five iron ingots before steel processing is researched: it should say it cannot smelt
  that yet, and start the moment the technology finishes;
- build a burner inserter and check it does everything a hopper did — chest into furnace is the case
  every Minecraft player builds. It is behind Automation, so the honest test is the whole opening:
  hand-mine, hand-craft a lab and a boiler, research Automation, *then* automate anything;
- and time it. The lab alone is ten circuits, ten gears and four belts by hand. If it drags, the
  lever is a trigger's count in `data/technologies.json`, not the machinery under it.

**Oil.**

- **look at a field.** Three to eight black-topped blocks four apart on levelled pads, the grass and
  trees cleared over each. Whether the pads read as a field or as a scar is a judgement no test
  makes; `levelAround` in `CrudeOilFieldFeature` is the lever, and on flat ground it does nothing;
- **hover a well.** Jade should say `Yield: 143%` or thereabouts — between 90 and 200 near the start,
  climbing with distance. Place a well in creative and hover it: it should read a plausible yield,
  because a well works out its own richness from where it is;
- **hold a pumpjack over a field and walk about.** The nearest well's 3×3 footprint should appear in
  blue as you aim near it and move from well to well as you aim, and go when you look away. Click
  one block off a well: the machine should land centred on it anyway, outlet on the far side.
  **Whether the footprint reads as a placement ghost or as a stray box is the thing to judge**;
- **look at the pumpjack in a world**, now that its decks are solid. Whether a nodding donkey inside
  one block reads as a pumpjack at all is a judgement for eyes — see the loose end above;
- **pipe it and power it.** A pipe at the outlet corner should reach into the machine; one on a
  flank should not. With a pole in range Jade's energy bar fills, the status says *Pumping*, and the
  pipe's readout shows crude oil arriving at ten a second. Nothing consumes it yet, so the run fills
  and the machine says *Full*, which is not a fault;
- **mine a boiler with an iron pickaxe.** It should take under a second and hand you a boiler.
  Before this a boiler took fifteen seconds and dropped nothing. Then mine a pumpjack from a corner
  block and check one pumpjack comes back;
- and **pump oil for the first time with `oil-gathering` researched.** The moment the pumpjack
  finishes its first cycle, the toast for Oil processing should fire and the tree should show the
  refinery and the chemical plant unlocked — with no lab involved. The tooltip on the node says
  *Mine 1 x Crude oil - 0 done* until then, which is Factorio's own wording for a `mine-entity`
  trigger, and **whether "mine" reads right for pumping oil is a judgement no test makes**.

**The furnaces**, which nobody has seen.

- stand the three in a row. Stone is a vanilla furnace's sides, steel is iron and electric is
  polished deepslate with an iron hood — **whether they read as three tiers of one machine is the
  question**, and it was decided in three texture lines;
- build a furnace column: six stone furnaces in a line with a belt down each side. It should be
  walkable along the hearths with the stacks two apart, and the lit ones should be obvious from
  the far end at night — every cell gives light, as vanilla's furnace does;
- look at the electric furnace's hood glowing. It is the top of a stepped block in lava; whether
  that reads as a furnace or as a lamp is a judgement no test makes;
- and open one of each. It is vanilla's furnace screen, texture and all, which is what Yannic
  asked for; the electric one paints over the fuel slot and puts a charge bar where the flame
  goes, and **whether the patch is invisible is the thing to look at**. Hover the arrow for the
  status. The panel beside them is the ordinary hand panel, and **the smelting recipes are in it,
  dimmed, under Intermediate products**: the tooltip should say *Cannot be crafted by hand* and
  list the three furnaces under *Made in:*, and a click should say so on the action bar and queue
  nothing. Then put sand in a furnace and expect glass.

**The two new machines.**

- put an assembling machine 1 and a 2 side by side and set both to gears. The second should
  finish noticeably sooner and its bar should fill faster; the first now takes a full second per
  gear where it took half, which is Factorio's number and **whether it reads as right or as a
  regression is the thing to judge**. The blue livery is `light_blue_terracotta` on the sides and
  is the only thing telling the tiers apart across a base;
- stand on a solar field and walk across it — half a block, so it should be a floor — and decide
  **whether nine framed panels read as a solar field or as a blue slab**. Put a block over the
  middle of one: *Under a roof*. A dark panel looks up every ten seconds, so it should be making
  power within ten seconds of dawn without being touched.

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

**The hover readout on every machine**, which no test can see.

- look at a boiler, a steam engine, an assembler, a lab and both drills **from a corner rather than
  from the middle**. Jade should say exactly what it says from the middle — the same name, the same
  energy bar, the same lines;
- and check the two-block machines the same way: a splitter from either half, a pole from any
  height. **If a readout is missing on one block of a machine, the redirect is not seeing that
  block** and `MultiblockRedirect` is where to look.

**The drills**, whose dig area is now the machine itself.

- put a burner drill and an electric one down and look at each. The blue outline should sit
  exactly on the machine — 2×2 and 3×3 — and appear from any block of it rather than only from the
  one holding the block entity. **That the area *is* the machine is the whole idea**;
- put a range module in an electric drill and watch the outline grow by a ring;
- and let one run dry mid-area. It works its own footprint first and rings outward from there, so
  what it leaves behind should look like a tidy square rather than a strip.

**Slopes**, which nothing in this repo can see at all.

- build a belt line up a three-block hill and look at it from the side. **Whether the ramps read as
  one continuous belt, or as three slabs at an angle with gaps between them, is the whole
  question** - the joint at the bottom of each ramp is closed by an adapter box that was reasoned
  about rather than looked at, and the joint at the top is closed by nothing at all. If there is a
  wedge of daylight at either end, that is `beltRamp` in `NauvisLogisticsModels`;
- walk up it. Sixteen steps of a pixel under a smooth ramp: whether that feels like walking up a
  slope or like walking up stairs is a judgement no test makes. Then ride it - stand still and let
  it carry you up;
- **drop items on it and watch them go up.** A dropped item is carried at a rate that wanders and
  stalls about one run in six, on the flat as much as on a slope (`GAPS.md`). Whether that reads
  as a belt or as a fault is a judgement for eyes. Try a minecart on one too;
- watch items go up and come down. They cross a ramp about 41% faster than flat ground, which is
  deliberate and written down in `GAPS.md`. **Whether it reads as a speed-up or as a glitch** is the
  thing to decide; the tread's texture is stretched by the same amount so that the two agree;
- put a corner at the top of a climb and a corner at the bottom. A block that both turns and changes
  level is carried correctly and drawn flat - see `GAPS.md` - so the question is whether that is
  invisible in practice or obvious;
- and turn a belt in the middle of a working line **on a client**. The items should follow the new
  line immediately.

**The red belt and fast-replace**, which nothing in this repo can judge.

- lay a yellow line, then walk *along* it with red belts in hand and click each one. **Whether that
  reads as upgrading a line rather than as breaking it** is the whole question — the belt under you
  changes colour, points where you are walking and keeps what was on it, and you get the yellow one
  back;
- click a belt that is turning a corner, from the side. It will point where you are looking, because
  a belt in hand places a belt — so upgrading a corner is done from along the line, not across it.
  **Whether that reads as Factorio or as a trap is the thing to judge**;
- run a yellow line into a red one and watch the join. The two are two runs and items cross at the
  seam; whether the tread's change of pace reads as intended or as a stutter is a thing to look at;
- put a fast splitter in a red line and a yellow splitter in the same line beside it. The red one
  keeps the line's throughput and the yellow one halves it, which is the reason it exists; **whether
  the two read as a pair with the belts they belong to is the question** — the housing is the same
  iron and only the top changes, gold for yellow and redstone for red;
- and tell red from yellow across a base, which is the thing the palette was chosen for at 8x on a
  dark background rather than in a world at midday.

**The two chests**, which nobody has seen since they became real chests.

- put an iron chest down and open it. The lid should swing, the sound should be the copper chest's,
  and the box should read as metal rather than as a grey cube;
- put a steel chest beside it and check they are two chests and not one long one;
- and tell the two apart across a room. Iron is dark neutral grey and steel is light blue-grey,
  which is a decision made in `make_chest_textures.py` at 8x on a dark background.

**The research screen and the tree.** Most of what follows was rebuilt after the first look at it
and has not been looked at since.

- open a lab, press **Tech**, and hover a node. Whether the tooltip reads as a row of its own, a
  name over a cost over the items it hands over, is the question;
- **hover `research_speed_1`, which unlocks nothing.** It should say *no effect yet* where the
  others list what they hand over. Four nodes in the tree are in that state: it, `research_speed_2`,
  `steel_axe` and `physical_projectile_damage_1`;
- Automation should be the obvious first click, saying `10 x 10s` with a red science pack;
- feed a lab, power it, pick Automation, and watch the lab's bar and the research row move together;
- **leave the crafting panel open while the last unit finishes.** The assembler and the long-handed
  inserter should appear without the screen being closed;
- **click through five or six technologies in the list.** Every click redraws the picture around
  what was clicked, and **whether that reads as navigating or as the screen jumping about** is the
  whole question. Look at `science_pack_1` specifically, the widest view, and decide whether it
  needs a cap; and look at `oil_processing`, whose view is the one that found the layout bug;
- **select `automation` and read the `+2` on `automation_2`.** It means "this also needs two
  technologies you cannot see from here". Whether a badge says that is a judgement no test makes;
- **the pack pips**, twelve-pixel science packs under each node's icon. Whether red and green are
  distinguishable there — the levers are `PIP` and `NODE` in `ResearchScreen`;
- **read the list top to bottom.** It is sorted into what it is coloured by — ready, then locked,
  then done. **Whether the first block really is "what to do next"** is the thing to check; if the
  fourth colour for the current research is one too many, `rank` and `colourOf` are the two methods;
- **type an item name into the search**, not a technology name — "assembling machine", "belt",
  "steel". The list matches what a technology unlocks;
- **tick "hide researched"** with a few technologies done. What is left should be the work
  outstanding, with the arrows through the hidden ones kept;
- **hover `automation_2` and look at what lights up.** Its prerequisites and everything behind them
  go amber. Whether amber on a green-and-grey tree is one colour too many is a judgement no test
  makes;
- **roll the wheel.** It zooms about the cursor and no longer scrolls. Whether losing scroll-to-pan
  is missed is a judgement for whoever uses it;
- **start Automation, let it get a few units in, then click something else and click back.** The
  bar under the node should still be there;
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
| `python tools/check_models.py` | every model, texture and blockstate reference resolved — and footprints, belt speeds and texture opacity |
| `python tools/render_model.py <model> out.png` | **draws a model to a PNG from the item slot's angle**, so a machine can be looked at without a boot. `--view side` or `top` for one cell. Needs Pillow and numpy |
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

**A gametest run sees the recipes the pack ships.** `GameTestServer` force-enables every datapack
it can see, bench-recipe packs included, so each `gameTestServer` run passes
`-Djaguarm.benchRecipePacks=false` and every `ModPacks` declines to offer its pack under it — the
one state vanilla cannot override is absent. `nauvis:timed_recipes_are_timed` holds it there.

**Adding a subsystem mod** is routine: a subproject in `settings.gradle`, a `build.gradle` with the
ids changed, a `src/main/templates/META-INF/neoforge.mods.toml`, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` and its namespace in
`pack_gametest_namespaces`. `nauvis_power/` is the fullest template; `nauvis_research/` is the
newest written from scratch and was made by following exactly that list, so its diff is what
adding a mod costs.

**Bringing a sibling repo in as a subproject** costs that list plus six more places, which
`nauvis_mining` is the worked example of: the `includeBuild` and its `dependencySubstitution` go
from `settings.gradle`, its version from `gradle.properties`, and it stops being a special case
in `check_models.py`'s `MODS`, `check_gametests.py`'s namespaces, `check_duplicated.py`'s copy
list and `gen_recipes.py`/`gen_removals.py`'s `SIBLING_REPOS` — that last one silently resolves
to `../<mod_id>` if you leave the entry in, and the recipe check reports the mod's files as
*missing* rather than failing.

**Recipes** generate into a staging directory —
`python tools/gen_recipes.py --only <modid> --out <tmp>` — then copy across only the files for items
that exist. `--write` writes all of that mod's recipes, and a recipe naming an unregistered item is
a load error.

**Pack settings** for a mod the pack ships go in `nauvis/pack/config/`, not `run/config`: `run/` is
gitignored, so a setting edited there is a change nobody else sees. Keep each file in the form
NeoForge writes it and put the reasoning in that directory's README.
