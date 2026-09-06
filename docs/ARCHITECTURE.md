Architecture
============

The rules a machine is built to, and the patterns worth copying. These outlive whatever is in
`NEXT.md`. `PITFALLS.md` is the companion: the ways of getting them wrong that still compile.

Footprint is identity. Height is ours.
--------------------------------------

Non-negotiable #1 makes ids, ingredients and craft times Factorio-correct from the first commit,
because they live in world saves and in the player's head. **A footprint is the same kind of fact**
— 3×3 is why an assembler column spaces the way it does, 5×3 is why a boiler feeds a *row* of
engines. Getting it wrong invalidates every blueprint a player carries in their head, and fixing it
later moves every machine in every world.

Height is the opposite: Factorio is two-dimensional and has no opinion, so height is ours, free to
change, and where to spend effort making a machine look like something.

Footprints live in `data/mapping.json` as `size` and `tools/check_models.py` holds every machine's
cells to them. Heights live in the shape class beside the models.

One tile is one block
---------------------

Not really a choice: the belt, the inserter and the pipe are one tile in Factorio and one block
here, and everything else has to agree. What 1:1 buys — layouts transfer; Factorio's own one-tile
walkways become one-block corridors a 0.6-wide player walks down; ratios stay legible.

### Walkability, in numbers

| | |
|---|---|
| player height | 1.8 — a corridor needs 2 of headroom |
| player width | 0.6 — a 1-block gap is walkable, diagonal gaps are not |
| step height | 0.6 — **anything colliding at 0.5 or below is walked straight over** |
| jump height | ~1.25 — a 1-block machine can be jumped onto, a 2-block one cannot |

**A machine you can walk across beats a machine you walk around.** A Factorio player tiles machines
with no gaps because in Factorio you can always walk round the far end of the field; nine 3×3
machines two solid blocks tall is a wall that seals the player out of their own base.

| height | used for |
|---|---|
| **1.0** | the wall around the outside. One jump, and the only climb in a field of any size |
| **0.75** | the floor inside the wall. Under the 0.6 step, so crossing is walking |
| **2.0** | whatever the machine puts in the middle — the one thing you walk around |
| **0.5** | crossed without even a jump: the electric drill, solar panels, and **belts** |

Two consequences. The upper storey is mostly air, so a 3×3 machine two blocks tall is ten blocks
and not eighteen — which is why `MachineShape` takes a set of cells rather than a box. And the tall
part goes in the middle, so tiled machines stand their obstacles apart and leave lanes.
`assemblers_tile_walkably` and `power_machines_tile_walkably` walk those lanes.

**Collision and silhouette are allowed to disagree** — a pole's crossarm has an outline and no
collision — but for something meant to be walked across, the two agreeing is the point.

The multiblock mechanism
------------------------

`nauvis_lib/.../multiblock/` — `MachineCell`, `MachineShape`, `MachineParts`, `Boxes`,
`Multiblock` — is one mechanism for every machine *and every pole*, in the library every
subsystem mod depends on. It was copied into each mod and held identical by a checker until the
copies outnumbered the reasons; the library is the one copy now. Read `MachineShape` and
`Multiblock` and you have it.

- one block id, one item, an `IntegerProperty part` on every block, the anchor found by arithmetic
  rather than a lookup — no block entity on the other cells. A big electric pole is twenty-four
  blocks and one of them holds anything;
- one `updateShape` rule is the whole teardown, which is why a shape's cells must be orthogonally
  connected, which the constructor enforces;
- capabilities are registered against the **block**, not the block entity, so any cell answers —
  that is what lets a pole supply a machine whose middle is out of range, and why `PowerNetwork`
  reduces endpoints to distinct handlers;
- **ports** name a cell and a face, so a boiler's steam leaves one block and an engine takes it at
  the open ends of its spine;
- geometry is stated once per machine and read by the model provider, the `VoxelShape` and the item
  model. `MachineParts` is the shared shell so the machines read as one family — Yannic has said
  that still wants refinement, and it is one file.

The belt, if you have to touch it
---------------------------------

**A transport line is one object; items are positions on it.** That is how Factorio does it, it is
where Create arrived —
`reference/create-src/src/main/java/com/simibubi/create/content/kinetics/belt/transport/`, read for
the architecture and reimplemented, because Create's is welded to its kinetics framework and its
assets are All Rights Reserved regardless — and it is what `BeltRun` is. A run ticks once however
long it is, an item crosses it at the belt's speed rather than at one block a tick, and a belt block
holds nothing at all. Runs with something on them are ticked and the rest are not visited, so an
empty base costs nothing and a jammed one costs nearly nothing.

Read `nauvis_logistics/.../belt/` in this order and the whole thing falls out:

| | |
|---|---|
| `Belts.java` | the numbers, and why distances are integer sixty-fourths of a block |
| `BeltLane.java` | **the idea.** Items are stored as the gaps between them, so a flowing belt writes one number a tick and a jammed one writes none |
| `BeltRun.java` | one line: its blocks, its two lanes, its tick, how it hands to the next line, and — `announceArrivals` — how anything beside it hears an item turn up |
| `BeltLines.java` | every run in a level, and how lines are cut and joined |
| `BeltAccess.java` | how everything else meets a belt, and why giving and taking are two rules |
| `BeltShape.java` | how a corner knows it is one, how a slope does, and why there are two of each rather than eight |
| `BeltBlock.successorOf` | the three places a belt hands to - level, a step up, a step down - which is vanilla's rail probe |
| `BeltBlock.stepOn` | why standing on a belt carries you, and why that is not `entityInside` |
| `BeltBlock.useItemOn` | a belt in hand puts **that** belt on the block you click, facing the way you are — Factorio's fast-replace |
| `client/BeltRenderer.java` | the items you can see |
| `texture-workshop/make_belt_textures.py` | the art, and why the tread scrolls at 1.875 tiles a second |

Four things are load-bearing for anything built on top:

- **Items are pinned to a block and an offset into it**, never to a distance along a run. That is
  what makes cutting, joining, lengthening and turning safe; `belt_survives_being_cut` asserts it.
- **The client runs the same simulation**, so a belt full of items costs no network traffic. Only
  two things are ever sent — an item put on from outside and one taken off — because they are the
  only two a client cannot work out. **Anything added to belts must keep that property.**
- **A run is awake while it has items, not while it is moving**, because `BeltLane` makes a jammed
  belt cost the same as an empty one.
- **A belt is the one subsystem that has to announce arrivals**, and the reason is not "it has no
  block entity". `FluidNetwork` and `PowerNetwork` move things without one and are audible because
  they **push**, and the buffer they fill fires its own callback. A belt deliberately never pushes —
  a belt running into a chest backs up, which is Factorio's rule and the reason inserters exist —
  so `BeltRun.announceArrivals` is the only thing that can speak for it. Generally: *a subsystem
  that moves things and never pushes them into the consumer has to announce, because nothing else
  will.*
- A closed ring of belts is one run that wraps, and a block state change does not touch the graph —
  turning a belt, or its becoming a ramp, leaves its block entity alone. `BeltBlockEntity`
  overrides **`setBlockState`** to catch that, which is the only hook for it that fires on both
  sides; `BeltLines.beltTurned` is the third way in.
- **A line changes level by being built that way**, the way rails do: a belt hands to the first belt
  of its own tier straight ahead, one above, or one below, level winning over either. The ramp is
  always drawn in the *lower* of the two blocks. What the simulation needs from a slope is one
  number — how far the surface climbs across a block — and even that is only consulted at the ends
  of a run: **the height of a seam between two blocks comes from the two blocks, not from either
  one's shape**, so two neighbours cannot disagree about it and an item cannot step through the gap.
- **A tier is a class, and a run is keyed on the block.** Speed is a constant on a `BeltBlock`
  subclass rather than a field, because `createBlockStateDefinition` runs before any field of a
  subclass exists; `BeltLines` follows a line only through belts of the same block, so two tiers are
  two runs that hand off at the seam with nothing written to make it so. Adding the fast belt was a
  file, a palette and a line in each registry, and one block entity type still covers every tier.
  **Swapping one belt block for another** — fast-replace — is therefore not a state change but a
  new block entity: the graph hooks fire on their own, and the only thing that needs carrying by
  hand is the load, which `BeltBlockEntity.takeCargo` lifts *before* the swap because
  `preRemoveSideEffects` would otherwise spill it on the floor. The player-facing rule is one rule —
  *a belt in hand puts that belt here, pointing the way you face* — and the two tiers are only two
  implementations of it, which is the shape to keep if a third arrives.

`BeltLines` also owns the tick of anything that carries items *along* a belt line — the splitter is
ticked there rather than by a scheduled block tick, because the client simulates belts and a
scheduled tick is server-only. A machine *beside* a belt schedules ticks like any other machine.

The grid, if you have to touch it
---------------------------------

`nauvis_power/.../grid/`: `PowerNetwork` is one object per connected network, holding its member
poles and `BlockCapabilityCache` handles on the producers and consumers at its edges;
`PowerNetworkManager` owns one per level and is driven by a single `LevelTickEvent.Post`. A network
ticks once however many poles it has, and one with no producer or no hungry consumer does not tick
at all. Poles join and leave it rather than driving it. Machines find poles when they are placed
rather than poles scanning for machines, because a scan must never be per-tick. The graph is
derivable from block positions, so it is not saved — rebuilding on chunk load is cheaper than
invalidating a cache that spans one.

**`reference/mods/energizedpower-*.jar` is the reference, and the counter-example.** It is the one
FE mod on 26.2, it is MIT, and it does the opposite: `CableBlock` registers a ticker so every cable
ticks, and every cable holds its own copy of the network's producer and consumer maps. Read it for
what the endpoints look like and how connection changes propagate. Do not copy its tick model —
non-negotiable #5 is exactly the constraint it does not have.

Two things about the network were not obvious in advance and are worth knowing before changing it:

- **A machine cannot find a pole, so the pole finds the machine.** Non-negotiable #3 forbids
  `nauvis_machines` from knowing what a pole is, so discovery goes the other way, through
  `Capabilities.Energy.BLOCK`. The hard part is the trigger for a machine built *later*, two blocks
  from a pole and in nobody's neighbourhood: `BlockEvent.NeighborNotifyEvent`, which fires for any
  block placed or broken by any means, pre-filtered by a map of which poles reach into which chunk.
  A capability listener on all 125 supply positions of every pole is the exact alternative and
  would cost over a million weak references in a base of ten thousand poles. `API-26.2.md` has both.
- **Poles are bucketed into 8-block cells**, which is more than the 7.5 wire reach, so two poles
  that can see each other are always within one cell of each other on every axis. That turns the
  graph walk a split needs into a constant per pole instead of a 15×15×15 scan, and it is the
  difference between breaking a pole in a big network being free and being a visible stutter.

**A pole is a true multi-block**, on the same `MachineShape` a boiler is: four blocks for the small
one, five for the medium, and two-by-two by six — twenty-four blocks — for the big one and the
substation. Placement refuses unless the whole thing fits, `setPlacedBy` puts the rest in, and one
`updateShape` rule — a cell whose neighbours are not its machine's other cells turns to air — is the
entire teardown. Only the foot carries the block entity and only the foot drops the item, so
breaking any part gives back exactly one pole. A one-block pole read as a fence post; the other way
to get height, a `VoxelShape` four blocks tall on a single block, has the renderer cull the whole
thing the moment its one real block leaves the screen.

It is **climbable** (through `minecraft:climbable`, so a datapack can say otherwise) and its
crossarm has **no collision** — an arm you cannot see, three blocks over your head, that catches you
as you walk past is worse than no arm at all. And **wires draw themselves**: the network already
knows which poles can see each other, so it pushes that set to each pole and the client renders a
sagging line between the heads. No coil, no connectors, nothing for the player to say twice.
**`reference/ImmersiveEngineering-src`'s `wooden_post` is the reference** for that shape — base
block holds the logic, dummies above, break one and the whole thing goes. Read and reimplemented,
not copied; IE's licence permits drawing on it with credit and requires visible source, which this
is. Its assets were not touched.

The patterns worth copying
--------------------------

**Sleeping.** No `BlockEntityTicker` on the server anywhere: a registered ticker runs whether or
not there is work. (The two chests have one, inherited from `ChestBlock`, and it is client-only -
`getTicker` returns null on a server - and all it does is advance the lid.)

Every machine schedules its own block tick while it has something to do and stops when it
does not. Four ways a machine learns it has work again, and one usually needs more than one:

- its own inventory changed (`onContentsChanged`);
- **electricity or steam arrived** — `nauvis_lib`'s `MachinePower` and the power mod's `SteamTank`
  exist only to carry that callback. A machine that ran dry has stopped scheduling ticks, so nothing it does can
  restart it; the wake must come from whatever filled the buffer;
- a *neighbour's* block entity changed (`onNeighborChange`, which every `setChanged()` reaches on
  all six sides). Filter on the `neighbor` position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, then delete the sleep logic and watch the test go red.

**One object per connected thing, three times over.** `PowerNetwork` for the grid, `FluidNetwork`
for pipe runs, `BeltRun` for belt lines. Members join and leave; the network ticks once however
many members it has; one that moved nothing drops out of the active set. Read
`nauvis_fluids/.../pipe/` first — a pipe connects to the six blocks it touches, which
`neighborChanged` already reports, while a pole reaches 7.5 blocks and needs a spatial index.

**Capabilities are how mods meet.** No subsystem mod compiles against another. FE through
`Capabilities.Energy.BLOCK`, steam through `Capabilities.Fluid.BLOCK`, items through
`Capabilities.Item.BLOCK`. `SteamTank` looks its fluid up by registry id rather than importing it,
so `nauvis_power` still loads with `nauvis_fluids` absent.

**Sided capabilities carry meaning.** A steam engine offers steam only on the two faces along its
axis, which is what makes its facing matter and a pipe refuse its flank. *Which faces answer* and
*which way the machine looks* are two registrations with two tests — breaking one leaves the
other's test passing.

**A renderer that draws outside its own block has to say so.** `getRenderBoundingBox` defaults to
the one block, and geometry past it is frustum-culled with no error and nothing in the log. The
wires between poles hit this exactly. See `API-26.2.md`.

**Multi-blocks are vanilla's job**, and there is one of them. A pole is four blocks on one `part`
property the way a door is two: refuse placement unless the whole thing fits, place the rest from
`setPlacedBy`, and let one `updateShape` rule be the whole teardown. That *was* a second mechanism
beside `Multiblock` — a `PolePart` enum with the four rules written out again — and the pole tiers
ended it: five blocks tall needs a five-value enum and two-by-two needs two more axes, both of
which `MachineShape` had all along. **Two implementations of one idea survive only while the
smaller one never has to grow.**

**A resource is a block that holds a number.** Factorio's oil is a resource entity, not a fluid,
and the oil well copies that: `nauvis_fluids:crude_oil` is ordinary unbreakable ground with a block
entity holding Factorio's resource amount, and nothing about fluids in the world had to be written
to make it unmovable — a solid block is not bucketed, does not flow and is not picked up. The fluid
of the same name exists only in tanks and pipes. Ore patches, when the drills become Factorio's
drills, want the same shape: a block that is the ground and knows how much is under it.

**A resource's starting value is a function of where it is**, seed and position, worked out the
first time anything asks. Worldgen then only places blocks — a block entity in a proto-chunk is a
thing to avoid having opinions about — and a resource placed by hand in creative is exactly as rich
as a generated one there would have been. `CrudeOilField.initialAmount` is the pattern.

**A resource the player must go to is a fluid of the world's own.** Factorio's water is a tile, and
the offshore pump is the only thing that does anything with it; Minecraft's water is a bucket away
from anywhere. So worldgen's water is `nauvis_fluids:water` - vanilla's block on a fluid of ours,
in the water tag, drawn with water's sprites - with the two rules that make a lake a place: a
bucket of it is vanilla's water bucket, so what you carry away is ordinary water and what you pour
out is ordinary water, and it never makes a new source, so a lake is as big as the world made it.
The pump asks a tag rather than a fluid, so a pack can put vanilla water back. The swap is a
feature in the last decoration step that reads the chunk back and swaps every water block in its
sections - `NaturalWaterFeature`, whose javadoc says why it touches neither heightmaps nor light.
The two things that stopped working because vanilla names the block rather than the tag - fish
spawning, and the pump's own rule - are answered in `water/`.

**A machine that must stand on something snaps to it.** `Multiblock.getStateForPlacement` takes the
cell that lands on the click, and `PumpjackBlock.snapPart` chooses it so the centre lands over the
well nearest the click. The client's outline renderer asks the same method the same question, so
the footprint drawn is the footprint placed and there is no second copy of the rule.

**An x-ray is a block entity renderer with the depth test off.** The wells light up while a pumpjack
is in hand because every well has a block entity, so every loaded well has a renderer visited for
it — no scan. `shouldRenderOffScreen` is what makes it see through hills, because the per-section
pass never visits a section the visibility graph has culled; and the lines are a pipeline of our
own with `CompareOp.ALWAYS_PASS`, since every vanilla line pipeline tests depth. See `API-26.2.md`.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back. `commit = false` turns the same method into the simulation, so "can I?" and "do it"
cannot drift apart.

**One interface.** Facrafting owns the crafting UI and its panel attaches to any container screen;
machine screens grow out of it rather than sit beside it. Every machine screen extends
`nauvis_lib`'s `MachineScreen`, which is where the panel, the slots and the meters are drawn once;
a screen of its own keeps only its positions and its status line. **The look is the dark panel
with vanilla's pixels on it**: Yannic likes the dark painted panel and wants the parts a Minecraft player has
looked at for years to stay vanilla's, so a slot is vanilla's slot sprite, and a fire, an arrow
and a bolt are sprites of the pack's own in vanilla's pixel idiom - fourteen-pixel meters drawn
the way vanilla draws its flame, the sprite tinted dark and then lit from the bottom as far as it
is full. Vanilla's own flame and arrow could not be used: they are opaque, with the panel's grey
baked in around the shape. `texture-workshop/make_gui_textures.py` writes the sprites into
`nauvis_lib`.

Facrafting learns rules, not facts
----------------------------------

Facrafting is a general mod this pack happens to be built on. It may name Factorio as the model
for a convention — its menu dims a smelting recipe the way Factorio's does, and a comment may say
so — and it may not hold a fact about Factorio's items, recipes or tree. Five forms of the seam, in
increasing strength:

1. **A rule plus data.** The rule is "a tab's place is the place of the first recipe in it", true of
   any pack; the fact is that Factorio's strip reads Logistics, Production, Intermediate products,
   Combat, and it lives as the *key order* of `GROUP_BY_CATEGORY` in this repo's generator. The
   recipe `category` is the same shape: the rule is "a recipe with no category is the hand's, and
   one with a category belongs to whichever machine names it", and the fact that iron plates are
   `smelting` lives in `data/mapping.json`.
2. **A suggestion Facrafting derives.** `RecipeTabs.suggestedMode` counts how many recipes carry a
   `group` rather than being told which mode this pack wants; `FacraftPanel.modeChosen` lets the
   player outrank it until logout.
3. **A pack config**, for a fact that is neither a rule nor in the recipes — see
   `nauvis/pack/config/`. NeoForge rewrites a config that does not match the mod's spec, comments
   included, so the reasoning lives in that directory's README rather than in the toml.
4. **A hook, when the rule cannot be expressed as data at all.** "Unlocked" is not a property a
   recipe has, it is a question somebody else answers — so Facrafting gained an interface
   (`RecipeLock`), a place to install one (`RecipeLocks`) and four calls to it. With nothing
   installed everything is unlocked, which is what every pack had before. **A hook's default is
   always the old behaviour.**
5. **A meeting point, when two subsystem mods share a fact and may not share a class.** The
   pumpjack mines oil and research wants to know; neither may depend on the other, and a listener
   list cannot be duplicated — two copies are two lists. So Facrafting has `MiningListeners`
   beside `CraftListeners`: the machine fires it, whoever cares listens, and Facrafting itself
   never calls it and has no opinion about what a resource id means. Each side reaches it from a
   `compat/facrafting/` class behind a `ModList` check, so Facrafting stays optional to both.

**When a change to Facrafting needs a fact about Factorio, the change is in the wrong repo.**

The mirror on this side: `nauvis_research` declares Facrafting **optional** and still installs the
hook, because the installer lives in `compat/facrafting/` behind a `ModList` check and the JVM
resolves the reference only when that branch runs. Same trick as the Jade plugins, same reason —
the standalone bench recipes exist for Facrafting's absence.

Two more seams worth knowing
----------------------------

**The lab does not know what a technology is.** It asks the world what is being researched, is
handed a list of packs, takes one of each and reports a unit. Adding the tech tree changed two
handovers in `LabBlockEntity` — which packs, and how long a unit takes — and not a rewrite.

**Neither does the furnace, and it is the first machine that chooses its own recipe.** It asks
Facrafting's `RecipeLocks` with the level rather than a player, because nobody clicked anything,
and reports every plate through `CraftListeners.fireMachine` — the same seam as `MiningListeners`,
for the other verb. The assembler reports the same way. Research hears both without either mod
naming the other, which is what lets "craft fifty iron plates" be finished by a furnace.

**The research screen paints and does not decide.** `ResearchScreen` is handed cells and arrows;
everything checkable — every node right of every prerequisite, no two in one cell, every technology
placed once, every arrow drawn, and the same tree laid out the same way twice — lives in
`TechnologyLayout`, which is common code asserted by `technology_layout_is_sound`. Columns are the
longest path from a root, so a node sits right of *every* prerequisite; rows are settled by
barycentre with the tree's own order as the tiebreak. Nothing is authored — there are no
coordinates in the data files and there must not be.

The hover readout
-----------------

Jade — `maven.modrinth:jade:${jade_version}` from `https://api.modrinth.com/maven`, `compileOnly`
in each subsystem mod and `runtimeOnly` in the pack. A `@WailaPlugin` class is loaded only when
Jade is present, so nothing declares it required. Each mod's readouts live in its `compat/jade/`,
one class per machine, and `nauvis_lib`'s `MultiblockRedirect` is registered once for every
machine in every mod, so pointing at any cell of one reads as pointing at its anchor.

Two things about writing a provider, both of which cost a client boot to discover:

- **Everything needs server data.** A boiler's steam and an engine's charge change every tick, and
  a machine that pushed a block update every tick to animate a bar would be sending packets to
  everyone in render distance. Jade asks the server only while somebody is looking, which is the
  right amount. The pole needs it absolutely: a network is a server-side object and the client has
  no `PowerNetworkManager` at all.
- **A provider may not be both halves, and needs a config translation.** Both crash rather than
  degrade — `PITFALLS.md` has each. The shape, theirs and now ours, is an outer data class with a
  nested `Client`, sharing one uid so a player toggling the readout off turns off both.

The pipe's readout is modelled on Factorio's own — what is in the run and how far it reaches — but
stops at the extent rather than printing Factorio's `6/320`, because the 320 is its cap on one
fluid segment and this pack has none. A tooltip is not the place to invent a rule nothing enforces.

Dependencies
------------

Jade is above; `PLAN.md`'s third-party section covers what else was surveyed and why nothing else
was taken.

**Licences are not a decision point for including or depending on a mod here.** The pack is not
monetised and ships the way thousands of CurseForge packs do; weigh version support, API shape and
maintenance instead. This does not extend to *copying* — CLAUDE.md's rule stands and is separate.

**KubeJS** is still on 26.1.2 and in beta, one Minecraft version behind us. LGPL-3.0, so no
obstacle once it ports. Where it would help is pack policy — stripping vanilla recipes is one
script against hundreds of condition-false JSON files. Where it cannot help is machines:
non-negotiable #3 requires each subsystem mod to stand alone, so block entities, ticking and
capability handlers stay Java.
