Architecture
============

The rules a machine is built to. `PITFALLS.md` is the ways of getting them wrong that still
compile; `API-26.2.md` the API each rule stands on.

Footprint and height
--------------------

A footprint is identity: `size` in `data/mapping.json`, and `check_models.py` holds each
`*Shape` to it. Height is ours. One Factorio tile is one block. A player is 1.8 tall, 0.6 wide,
steps 0.6 and jumps about 1.25, so: the wall around a machine is 1.0 (one jump), the floor inside
0.75 (walked over), the tall part in the middle 2.0 (walked around), and belts, solar panels and
the electric drill 0.5. A 3×3 machine two blocks tall is ten blocks, not eighteen, which is why
`MachineShape` takes a set of cells. Collision and silhouette may disagree (a pole's crossarm has
no collision) except on things meant to be walked across.

The multiblock mechanism
------------------------

`nauvis_lib/.../multiblock/`: `MachineCell`, `MachineShape`, `MachineParts`, `Boxes`, `Multiblock`.
One block id, one item, an `IntegerProperty part` on every cell, the anchor found by arithmetic;
only the anchor has a block entity. Cells must be orthogonally connected, because one
`updateShape` rule (a cell whose neighbours are not its machine's other cells turns to air) is the
whole teardown. Capabilities are registered against the block, so any cell answers; a network must
reduce endpoints by identity, not position. Ports name a cell and a face. Geometry is stated once
per machine and read by the model provider, the `VoxelShape` and the item model; `MachineParts` is
the shared shell. A cell's place in the list is its `part` value and is in world saves; write cells
out, never in a loop. Poles are multiblocks too (four, five, and twenty-four blocks), climbable,
crossarm without collision, only the foot drops the item. `Multiblock.place` builds one in a test.

Every multiblock in hand draws a ghost, blue or red, from `nauvis_lib`'s `MachineGhost`: it builds
the click a right-click would and asks `placementFacing`, `placementPart` and
`getStateForPlacement` through `Multiblock.ghost`, so a machine that decides its own placement
(the pumpjack snapping to a well, the offshore pump turning to water) answers those hooks and uses
them itself. `placementMarks` names what it stands on. A refused placement says why on the action
bar in a handful of words.

A machine announces an inventory change from every cell: `Multiblock.announce(level, anchor,
state)` sends `onNeighborChange` to every block outside the machine that touches a cell, called
from the inventory's change hook and from `setRecipe`, never from `setChanged` (a craft calls that
every tick).

Sleeping
--------

No server-side `BlockEntityTicker` anywhere. A machine schedules its own block tick while it has
work and stops when it does not. It learns it has work again from: its own inventory
(`onContentsChanged`); electricity or steam arriving (`nauvis_lib`'s `MachinePower` and the power
mod's `SteamTank` exist to carry that callback, because a machine that ran dry is scheduled for
nothing); a neighbour's block entity changing (`onNeighborChange`, filter on the `neighbor`
position first); a neighbouring block changing (`neighborChanged`) and `onLoad`. Bank a finished
craft before asking for power, or the last tick of a craft that empties the buffer stalls with the
product in hand. `level.getBlockTicks().hasScheduledTick(pos, block)` is the test assertion.

One object per connected thing: `PowerNetwork` (grid), `FluidNetwork` (pipes), `BeltRun` (belts).
Members join and leave; the network ticks once however many members; one that moved nothing
drops out of the active set. A subsystem that moves things without pushing them into the consumer
has to announce arrivals itself (`BeltRun.announceArrivals`), because nothing else will.

Capabilities are how mods meet
------------------------------

FE through `Capabilities.Energy.BLOCK`, steam through `Capabilities.Fluid.BLOCK`, items through
`Capabilities.Item.BLOCK`. `SteamTank` looks its fluid up by id so `nauvis_power` loads without
`nauvis_fluids`. Sided capabilities carry meaning: an engine offers steam only along its axis, and
which faces answer and which way it looks are two registrations with two tests. Anything resolving
a sided capability must say which side; `null` answers for every side.

Spending and receiving happen in one `Transaction`, so a result that will not fit rolls back;
`commit = false` is the simulation, so "can I" and "do it" cannot drift.

`nauvis_lib` seams, none of which name a mod:

- `Bonuses`: research installs the one `Source`; a machine asks `Bonuses.count(level, "<factorio
  modifier type>")` and gets zero without research. Names are typed where used, never in the lib.
- `Pollution`: a machine calls `Pollution.emitTick(level, pos, perMinute, factor)` on every tick it
  works, with `POLLUTION_PER_MINUTE` beside `ENERGY_PER_TICK`; `nauvis_military` installs the one
  `Sink`.
- `Health`: `hurt` and `repair` at a position. A `Damageable` machine says Factorio's number
  (`MachineHealth`, saved with the block entity, found through any cell); anything else is a hundred
  times its hardness in `BlockHealth`, a `SavedData` that remembers which block was wounded. Hurt to
  nothing, the anchor is destroyed with no drops and the teardown rule takes the rest.
- `ModuleEffect` and `Module`: speed, energy and productivity as fractions summed over a machine's
  `ModuleSlots`, with Factorio's floor of a fifth on speed and power; read once as a craft starts.
  `Productivity` is a bar that pays a free craft's worth when it fills. Slot counts are identity.
- `EnergyBuffer` marks an accumulator: `PowerNetwork` draws generators for demand, accumulators
  for the shortfall, and only on a tick they were not needed fills them from what generators have
  left. Two batteries never trade. `FluidBuffer` marks a storage tank: left out of a pipe run's
  sink and source halves and levelled instead, run and tanks at one fraction full in one step.
- `FluidOutputAccess`: an extract-only view answers `isValid` false and capacity zero, so a run
  can tell a full sink from a source.
- `Stacks.of(n)`: item properties with Factorio's stack size; `stack` in `data/mapping.json` and
  `check_models.py` hold the two together. Vanilla stand-ins are sized in the pack mod's
  `StandInStacks`. Minecraft's ninety-nine is lifted by the eight mixins in
  `com.jaguarm.nauvislib.mixin`; the package javadoc says why each is where it is.
- `GameTests` and `PackGameTest`: a test is `tests.add(name, ticks, padding, helper -> ...)`, or a
  `PackGameTest` subclass when it needs fields; type and instance registered from one call.

Machines pull from neighbours by levelling, never draining: an engine pulls from the engine
before it only while that holds more, a boiler takes half the difference from the boiler at its
end. Taking the lot wakes both for ever. A pipe run needs no such rule.

Belts
-----

A transport line is one object; items are positions on it. Read `nauvis_logistics/.../belt/` in
this order: `Belts` (the numbers; distances are integer sixty-fourths of a block), `BeltLane`
(items stored as the gaps between them, so a jammed belt writes nothing), `BeltRun` (blocks, two
lanes, tick, hand-off, `announceArrivals`), `BeltLines` (every run in a level; cutting and joining;
also ticks the splitter, because a scheduled tick is server-only and the client simulates belts),
`BeltAccess` (giving and taking), `BeltShape` (corners and slopes), `BeltBlock.successorOf` (level,
up, down, vanilla's rail probe), `BeltBlock.stepOn`, `BeltBlock.useItemOn` (fast-replace),
`client/BeltRenderer`, `texture-workshop/make_belt_textures.py`.

Load-bearing: items are pinned to a block and an offset, never a distance along a run; the client
runs the same simulation and only an item put on or taken off is ever sent, and anything added
must keep that; a run is awake while it has items, not while it moves; the height of a seam comes
from the two blocks, never one's shape; a tier is a `BeltBlock` subclass with a speed constant and
a run is keyed on the block, so two tiers hand off at the seam with nothing written. A state change
does not touch the graph; `BeltBlockEntity.setBlockState` catches a turn on both sides.
Fast-replace lifts the load (`takeCargo`) before the swap because `preRemoveSideEffects` would
spill it, and gives it back through the chunk-load route.

The grid
--------

`nauvis_power/.../grid/`: `PowerNetwork` per connected network holding its poles and
`BlockCapabilityCache` handles on producers and consumers; `PowerNetworkManager` per level,
driven by one `LevelTickEvent.Post`. Poles never tick. Machines cannot find poles (non-negotiable
3), so poles find machines through `Capabilities.Energy.BLOCK`; the trigger for a machine built
later is `BlockEvent.NeighborNotifyEvent`, pre-filtered by a map of which poles reach which chunk
(a listener per supply position would be a million weak references at ten thousand poles). Poles
are bucketed in 8-block cells, above the 7.5 wire reach, so a split walks a constant per pole. The
graph is derived from block positions and never saved. Wires draw themselves: the network pushes
the visible set to each pole and `PoleWireRenderer` draws a sagging line.
`reference/mods/energizedpower-*.jar` is the counter-example: every cable ticks.

Fluids and resources
--------------------

`FluidNetwork` per pipe run: fills sinks, drains sources, levels tanks; asks endpoints through the
face the pipe lies on. A machine fixes its fluid ports: `ProcessingBlockEntity` gives a recipe's
fluids ports in recipe order except where `preferredInput`/`preferredOutput` keeps a place (the
refinery's water, crude, heavy, light, petroleum; the chemical plant's water), and the `PortTank`
behind a port takes only that fluid. One base class runs the refinery and the chemical plant; a
`ProcessingLayout` is the difference. Assembling machine 2 grows two fluid boxes and so a
`FACING`; `AssemblerBlock.fluidBoxes()` is a constant on the subclass read from `Block`'s
constructor. `AssemblerBlock.accepts` decides which recipes a tier runs, for menu and machine
alike.

A resource is a block holding a number: `nauvis_fluids:crude_oil` is unbreakable ground with a
block entity, so nothing about fluids in the world was written. Its starting value is a function
of seed and position worked out on first ask (`CrudeOilField.initialAmount`), so worldgen only
places blocks and a creative-placed well is as rich as a generated one. A drill reaches down its columns to `mineFloor` for whatever ore is in them.

**Ore is in patches, and a patch is arithmetic on the seed.** `nauvis/.../ore/`: `OrePatch` is a
rounded rectangle (a superellipse with a rippled edge) of solid ore two or three layers thick on a
floor that tilts and rolls a few blocks (`Relief`);
`OrePatches` says where they are, three starting patches of iron, copper and coal between 48 and
120 blocks of the origin and a random patch in six cells of ten on a 128-block grid beyond the
160-block starting area; `OrePatchFeature` runs once per chunk in the ore step and draws that
chunk's share of every patch that reaches into it, so a patch spanning chunks needs no
coordination. Only ground (`stone_ore_replaceables`, `deepslate_ore_replaceables`) becomes ore, so
a cave leaves a hole. Vanilla's scattered iron, copper and coal features are removed by
`no_vanilla_ore_veins.json`, and its noise ore veins, which are not features, by the pack mod's
one mixin, `NoiseGeneratorSettingsMixin`. Footprint is Factorio's and stays with distance; richness by distance
is Crumbling Ore's `crumbling.richness.doubleDistance`, set in the pack config. There is no map,
so `nauvis_mining`'s `OreXray` outlines the ore in loaded chunks while a drill is in hand, one box
per chunk section per ore. What counts as Factorio's ore is the block tag
`nauvis_mining:factorio_ores`, read by the x-ray and by the drill, which takes only those until
its screen's toggle (a menu button, like the silo's) says otherwise. `/ore patch <kind>` and `/ore starting` place patches by hand.
Natural water is `nauvis_fluids:water` (vanilla's block on a fluid of ours, in the water tag,
bucketed as vanilla water, never a new source), swapped in by `NaturalWaterFeature` in the last
decoration step; the offshore pump asks `#nauvis_fluids:offshore_pumpable`. The wells' x-ray is a
block entity renderer with the depth test off and `shouldRenderOffScreen`.

Crafting, recipes and screens
-----------------------------

A machine with a fixed recipe reads its numbers off the recipe: the silo finds `rocket-part` by
product in its category and reads the launch recipe for parts per rocket, cargo and what comes
back; `RocketSiloBlockEntity` holds none of those facts. Facrafting's `RESULT_CODEC` has no
ceiling; a result of a thousand is the machine's business. An input slot is one ingredient's and
holds the larger of a stack and twice the recipe's count (`AssemblerInventory`,
`RocketSiloInventory`); a machine with no recipe takes nothing; the client asks its copy of the
recipe through `ClientSlotRules`/`ClientSiloRules`. Slots over ninety-nine are saved as a resource
and an amount.

The furnace chooses its own recipe: Factorio's four `smelting` recipes first, through
`RecipeLocks` with the level, then vanilla's smelting list at 200 ticks over the tier's speed. Every
craft is reported through `CraftListeners.fireMachine`; the pumpjack reports through
`MiningListeners`; research hears both. The lab asks the world what is researched and is handed a
list of packs.

Every machine screen extends `nauvis_lib`'s `MachineScreen` (panel, slots, meters drawn once) and
keeps only its positions and status line; Facrafting's panel is the recipe selector. The look is
the dark painted panel with vanilla's pixels: slots are vanilla's sprite; fire, arrow and bolt are
the pack's fourteen-pixel sprites in vanilla's idiom, tinted dark and lit from the bottom, from
`texture-workshop/make_gui_textures.py` (vanilla's own are opaque with grey baked in). A screen
button is vanilla's menu button: `clickMenuButton(player, id)` on the menu,
`handleInventoryButtonClick` from the screen, no payload of ours.

The research screen paints and does not decide: `TechnologyLayout` is common code asserted by
`technology_layout_is_sound`; it draws one technology's ancestors and two levels of descendants
with a list and a search box, not the whole tree. Columns are longest path from a root, rows by
barycentre. No coordinates in data.

The radar keeps its seven-by-seven chunks loaded through one `TicketController` with the radar's
position as owner; tickets are saved facts, released from `preRemoveSideEffects` and never from
`setRemoved`, and the validation callback drops tickets with no radar at the owner.

Facrafting learns rules, not facts
----------------------------------

Facrafting is a general mod that may name Factorio as the model for a convention and may not hold
a fact about Factorio's items, recipes or tree. The seams, in increasing strength: a rule plus
data (tab order is the key order of `GROUP_BY_CATEGORY` in the generator; `category` in the
mapping says which machine a recipe belongs to); a suggestion Facrafting derives
(`RecipeTabs.suggestedMode`); a pack config (`nauvis/pack/config/`); a hook whose default is the
old behaviour (`RecipeLock`/`RecipeLocks`); a meeting point two subsystem mods may share
(`MiningListeners`, `CraftListeners`). When a change to Facrafting needs a fact about Factorio, the
change is in the wrong repo. `nauvis_research` declares Facrafting optional and installs the hook
from `compat/facrafting/` behind a `ModList` check; `nauvis_machines`, `nauvis_fluids` and
`nauvis_rocket` require it because they run its recipes.

The hover readout
-----------------

Jade: `maven.modrinth:jade:${jade_version}` from `https://api.modrinth.com/maven`, `compileOnly`
per mod, `runtimeOnly` in the pack; a `@WailaPlugin` class loads only when Jade is present.
Readouts live in each mod's `compat/jade/`, one class per machine: an outer server-data class with
a nested `Client`, one uid, a `config.jade.plugin_<modid>.<uid>` lang key. Everything needs server
data (Jade asks only while somebody looks). `nauvis_lib`'s `MultiblockRedirect`
(`addRayTraceCallback`) rebuilds the accessor at the anchor for every machine in every mod. A
tooltip does not invent a rule nothing enforces (the pipe says `6 pipes`, not `6/320`).

Pollution and attacks
---------------------

`PollutionState`, one per level: a number per chunk and the position of the last polluter. Once a
minute each chunk gives two percent to each neighbour and the ground absorbs by the biome at the
chunk's middle (`Absorption`: forest three times the flat five, beach a fifth, water half again).
Once a minute a chunk holding fifty or more has a chance proportional to its load, certain at five
hundred, of spending fifty a head on a group of up to six, only with a player within ninety-six
blocks; the group appears twenty-four to forty blocks from the polluter and walks at it
(`AttackFactoryGoal`), chewing through what is in the way, fighting a player only within six
blocks or when hit. Zombies; skeletons past twenty thousand lifetime pollution, creepers past
sixty thousand; capped against the sun. Every knob is in `Attacks`, `Absorption` and
`PollutionState`, and none is identity. Bullets are lines that hit on the tick they fire and
ignore invulnerability frames; a magazine's rounds live on the gun or turret as a component.
