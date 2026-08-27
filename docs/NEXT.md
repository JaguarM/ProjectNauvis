Next session
============

Written 2026-08-27 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the job below is done — it describes one job, not the project.

**Forty-seven gametests pass and the pack builds clean.** Milestone 1 is closed, steam runs through
pipes, and the machines say what they are doing. What is wrong now is that every one of them is a
single cube.

The job: machines the size Factorio made them
---------------------------------------------

Yannic's words: *the current one block machines are a joke.* They are. An assembling machine is one
cube. So is a boiler that should be six tiles, and so is a steam engine that should be fifteen. The
power poles are the one thing in the pack that reads correctly, and they read correctly for exactly
one reason: a pole is four real blocks rather than one block pretending to be four.

So every machine gets its Factorio footprint, in real blocks. **The model work queued by the last
session happens inside this change, not before it** — modelling a one-block assembler and then
modelling it again at 3×3 is doing the job twice, and the second time is the only one that counts.

### Footprint is identity. Height is ours.

Non-negotiable #1 says ids, ingredients and craft times are Factorio-correct from the first commit,
because they live in world saves and in the player's head. **A footprint is the same kind of fact.**
Three-by-three is why an assembler column spaces the way it does; five-by-three is why a boiler
feeds a *row* of engines. Get it wrong and every blueprint a player carries in their head is wrong,
and fixing it later moves every machine in every world.

Height is the opposite. Factorio is two-dimensional and has no opinion, so height is ours to choose,
free to change, and the place to spend effort on making a machine look like something. Treat the two
differently: footprints go in `data/mapping.json` beside the ids and are checked; heights live in
the shape class next to the models and are tuned by looking at them.

### The sizes

Footprints in tiles. The recipe dump carries no sizes, but every entry has a `wiki_link` — that is
the citation. **The table below is from knowledge of the game, not from a dump: check it once
against the wiki, then record it in `data/mapping.json` and never type it again.** The four marked
`?` are the ones worth checking first.

| Entity | Footprint | Proposed height | |
|---|---|---|---|
| assembling machine 1/2/3 | 3×3 | 2 | in the pack |
| boiler | 3×2 | 2, chimney to 2.5 | in the pack |
| steam engine | 5×3 | 2, flywheel to 2.5 | in the pack — **not 3×4** |
| burner mining drill | 2×2 | 2 | released, see below |
| electric mining drill | 3×3 | 0.5 solid, head to 1.5 | released, see below |
| stone / steel furnace | 2×2 | 2 | vanilla stand-in today |
| electric furnace | 3×3 | 2 | milestone 3 |
| solar panel | 3×3 | 0.5 | milestone 3 |
| accumulator | 2×2 | 2 | milestone 3 |
| lab | 3×3 | 2 | milestone 3 |
| radar, beacon | 3×3 | 2 | milestone 3 |
| big electric pole, substation | 2×2 | 5, 3 | milestone 3 |
| storage tank, pumpjack, chemical plant, centrifuge | 3×3 | 3, 2, 2, 2 | milestone 4 |
| oil refinery | 5×5 | 3 | milestone 4 |
| nuclear reactor | 5×5 | 3 | milestone 4 |
| heat exchanger | 3×2 | 2 | milestone 4 |
| steam turbine | 5×3 | 2 | milestone 4 |
| pump | 1×2 ? | 1 | milestone 4 |
| offshore pump | 1×2 ? | 1 | milestone 4 |
| roboport | 4×4 | 3 | milestone 5 |
| gun / laser turret | 2×2 | 2 | milestone 5 |
| flamethrower turret | 2×3 ? | 2 | milestone 5 |
| artillery turret | 3×3 | 3 | milestone 5 |
| train stop | 2×2 ? | 3 | milestone 5 |
| rocket silo | 9×9 | 8 | milestone 6 |
| splitter | 2×1 | 0.25 | milestone 2 |
| **belt, inserter, pipe, small pole, chest, underground belt** | **1×1** | — | **already correct** |

The last row is the important one. Most of Factorio is one tile, and the pack already has all of it
right. This job touches machines, and machines are the minority.

### One tile is one block

The scale is 1:1 and it is not really a choice: the belt, the inserter and the pipe are one tile in
Factorio and one block here, and everything else has to agree with them or nothing lines up. What
1:1 buys is worth naming, because it is the whole reason the footprints matter —

- **layouts transfer.** A player who knows that a boiler feeds two engines, or that assemblers sit
  three apart with a belt down the middle, builds the same thing here and it fits;
- **the walkways are already there.** Factorio's own layouts leave one-tile gaps to walk in, and a
  one-block gap is a corridor a Minecraft player walks down comfortably — the player is 0.6 wide;
- **ratios stay legible.** Twelve assemblers per engine is a number you can pace out.

### Walkability, in numbers

The metrics that decide this, all vanilla:

| | |
|---|---|
| player height | 1.8 blocks — a corridor needs 2 of headroom |
| player width | 0.6 blocks — a 1-block gap is walkable, and diagonal gaps are not |
| step height | 0.6 blocks — **anything colliding at 0.5 or below is walked straight over** |
| jump height | ~1.25 blocks — a 1-block machine can be jumped onto, a 2-block one cannot |

That gives three collision heights and a house rule for picking between them:

- **0.5 — walked over.** The electric drill, solar panels, splitters, belts. Yannic asked for the
  drill at "like 0.5" and that is right: a drill field you have to climb over is miserable, and
  Factorio's drill is visually low anyway.
- **2.0 — walked around.** The default. Assembler, boiler, engine, furnaces.
- **nothing collides above 2.0.** A machine may *look* taller — see the chimney and the flywheel in
  the table — but the part above 2 blocks is scenery you can walk through.

**Collision and silhouette are allowed to disagree, and the pole already does this.** `PolePart`
gives the crossarm a full outline and no collision, because a shape three blocks over your head that
you cannot see should not catch you as you walk past. The same split is what lets the electric drill
be impressive and 0.5 blocks tall at once: a body at 0.5, and a head and output chute up to 1.5 that
you walk through. Copy the pattern, do not invent a second one.

The mechanism
-------------

The pole is the precedent and it scales: one block id, one item, one property saying which piece
this is, placed and broken as a unit, block entity on one piece only. What changes is that the
pieces are a grid rather than a column, so the property carries an index instead of an enum.

### `MachineShape`, and one description of the geometry

**One class per machine describes it once**, the way `PolePart` does, and everything else reads that
description rather than restating it:

- the **cells** — the local offsets the machine occupies, in its north-facing frame. A *set*, not a
  box, so a boiler can be 3×2 at two blocks tall with a chimney cell on one tile at y=2. The set
  must be orthogonally connected; the checker asserts it, because the teardown rule below depends
  on it;
- the **anchor cell**, which carries the block entity, the loot table and the menu;
- the **placement cell**, which is the cell that lands where the player clicked — the middle for odd
  footprints, so a 3×3 centres on the cursor like Factorio does, and a defined near corner for even
  ones;
- the **boxes**, in machine pixels, each naming the cell that draws it. Read by the `VoxelShape` and
  by the model provider, exactly as `PolePart.boxes()` is today;
- the **collision boxes**, separately, per the split above;
- the **ports** — which cell and which face offers which capability.

That last one is a real gain and not just tidiness. A boiler takes water at two specific tiles and
gives steam at one; an assembler is fed anywhere on its twelve perimeter faces. The pack already has
"sided capabilities carry meaning" as a pattern — the steam engine offers steam on two faces and
that is what makes its facing matter — and a footprint is what makes that pattern expressive
instead of cramped.

### One property, and no lookups to find the anchor

`IntegerProperty PART`, `0` to `cells - 1`, indexing into the shape's cell list in the *local* frame;
plus `FACING` on every part, not only the anchor. The anchor position is then
`pos.subtract(rotate(shape.cell(part), facing))` — arithmetic, no block reads, no block entity on
the parts. A 3×3×2 machine is 18 cells × 4 facings = 72 states, which is nothing; the rocket silo
would be 324 and that is still nothing.

Do not be tempted to put a block entity on each part to hold a `BlockPos` back to the anchor. Nine
to eighty-one block entities per machine, in a base of thousands of machines, to store a number that
is already in the blockstate.

### Teardown is still one rule

The pole's rule generalises without changing shape: **a part whose in-machine orthogonal neighbours
are not the parts they should be turns to air.** Break any cell and its neighbours notice, turn to
air, and the cascade crosses the whole footprint — which is why the cell set has to be connected.
Turning to air rather than calling `removeBlock` is what routes it through `Block.updateOrDestroy`
with drops enabled, so the anchor's loot table hands the machine back whichever cell was hit. The
creative special case is `SmallElectricPoleBlock#playerWillDestroy`, unchanged but pointed at the
anchor instead of the foot.

Placement is the pole's too: return null from `getStateForPlacement` unless every cell is
replaceable, and place the rest from `setPlacedBy`. Two additions a column did not need — check
`level.isUnobstructed` so a player cannot seal themselves inside a 3×3, and refuse placement that
would cross a world height limit.

### What every part has to forward

This is the list to work through per machine, and the place bugs will hide:

| | |
|---|---|
| capabilities | `event.registerBlock(...)` on the part block, resolving the anchor and delegating. `registerBlockEntity` cannot serve a block with no block entity. **Call `level.invalidateCapabilities` for every cell** when the machine appears or goes, or a neighbour caches a stale miss |
| `onNeighborChange` | the wake. A 3×3 has twelve perimeter faces and an inserter may be on any of them; a part that swallows the wake puts the machine to sleep for good (non-negotiable #5) |
| `neighborChanged` | the same, for a block rather than a block entity |
| `useWithoutItem` | opens the anchor's menu from any cell |
| Jade | `BoilerReadout` and friends resolve part → anchor before reading |
| `getCloneItemStack` | middle-click on any cell gives the machine |
| loot | one table, condition on `part=0`, like the pole's |
| spawning | a flat 3×3 roof is a mob platform. `.isValidSpawn((state, level, pos, type) -> false)` |

Block models are the bulk of this
---------------------------------

**This is where the time goes, and it is the half worth getting right** — the pack's problem is that
it looks unfinished, and a 3×3 assembler that is nine untextured cubes is no better than one.

### Verified: a model may reach one block in every direction

`net.minecraft.client.resources.model.cuboid.CuboidModelElement` in 26.2 holds
`MIN_EXTENT = -16.0F` and `MAX_EXTENT = 32.0F`. So one model can occupy a 3×3×3 volume centred on
its own block, and no more. That number decides the whole approach: it is enough for an overhanging
chimney and nowhere near enough for a five-tile engine.

So: **every cell draws its own model, and geometry may overhang into its neighbours.** Not one giant
model on the anchor with the rest invisible, and not a block entity renderer. Per-cell models get
correct per-block lighting and ambient occlusion, go through ordinary chunk rendering, need no
`getRenderBoundingBox`, cost nothing at runtime, and have no size limit — the rocket silo works the
same way the boiler does. The overhang allowance is the escape hatch for the shapes that genuinely
straddle a block plane.

Two rules the checker has to enforce, because both fail silently:

- **a box, translated into its owner cell's frame, must land inside `-16..32`.** Outside that the
  model fails to parse and the block is a checkerboard;
- **a box that leaves `0..16` must declare explicit `uv` on every face.** Absent `uv` is derived
  from the box position, so an overhanging box gets UVs outside the texture and wraps or smears.
  Nothing warns.

An overhanging box also takes its light from the cell that owns it, and must not declare `cullface`
— a face culled against a neighbour that is another part of the same machine disappears.

### One frame, four facings, no extra models

Author the machine north-facing only. A 90° rotation of the whole machine is the same thing as
rotating the *lattice* of cells and rotating each cell's model about its own centre, which is
exactly what a blockstate `y` rotation does. So the blockstate dispatches over `PART × FACING` and
picks a model plus a `Y_ROT_*` mutator — `NauvisFluidsModels` already does this for the pipe's six
arms from one downward model. Models scale with cells, not with cells × 4.

### The item model comes free

A 3×3×2 machine in your hand cannot be its anchor cell — that is a corner of a machine, and it reads
as rubble. It also cannot be the whole machine at full size.

But the geometry is described once, in machine pixels, so **the inventory model is the same
description scaled by `16 / (16 × max(width, height, depth))`** — a miniature of the entire machine,
generated, always in step with the block. `PipeBlock` already has a hand-written `pipe_inventory` for
the same reason; this is that idea, automated. Remember `registerSimpleItemModel(block, modelId)`
explicitly — a model written under a name the item model does not default to is the exact silent
failure listed below.

### Textures still come last

Every model points at **vanilla** textures on purpose: a model naming a texture the mod does not
ship is the magenta checkerboard, which reads as broken rather than unfinished. Land art and models
together or not at all. When that day comes, start at
`../NeoProgressiveAutomation/texture-workshop/` — ours, already a process, and the best writing in
these repos on why vanilla textures look the way they do. `reference/ImmersiveEngineering-src` is
the best model work on the shelf and the source of the pole's shape: read and reimplement, credit in
the commit, never copy an asset.

First, a net: the model checker
-------------------------------

The last session queued this and the multiblock work makes it urgent rather than nice: datagen is
about to emit *hundreds* of models from generated names, and three separate visual failures have
already shipped without failing a compile, a test or a datagen run.

`tools/check_gui_layout.py` is the shape to copy — constants read back out of source, no game
required. The rules, in the order they will catch things:

- every blockstate variant names a model file that exists, for every value of `PART` and `FACING`;
- every model, and every parent it inherits, exists;
- every `#texture` reference resolves to a real PNG, vanilla ones included;
- every registered fluid has a `FluidModel`;
- **and the multiblock rules**: cells orthogonally connected, every box owned by a cell that exists,
  every translated box inside `-16..32`, explicit `uv` on every box that leaves `0..16`, and the
  `VoxelShape` union covering every cell so no part of the machine is a hole you fall through.

Python beside `check_gui_layout.py` is the recommendation, not a gametest. The gametest version can
ask the real model manager what resolved, which is stronger — but only a client has a model manager,
and a check that does not run in `./gradlew build` is a check nobody runs.

The order to do it in
---------------------

Each step ends with a client boot, because that is the only thing that has ever caught this class of
bug.

1. **The checker first**, against the models that exist today. It should pass on a clean tree — if
   it does not, that is a bug it just found.
2. **Sizes into `data/mapping.json`**, checked against the wiki links already in the dump.
3. **The assembler, 3×3×2, as the pilot.** One mod, one machine, the whole mechanism: `MachineShape`,
   the `PART` property, placement, teardown, capabilities from all twelve perimeter faces, the menu
   from any cell, the item model, the gametests. Everything after this is repetition, so spend the
   time here and let the shape of the code settle before copying it.
4. **The boiler (3×2) and the steam engine (5×3).** The engine is the one that proves the mechanism:
   five tiles is past what one model can draw, its two steam ends are specific cells rather than
   faces, and a row of engines off one boiler is the arrangement the whole subsystem exists for.
5. **The drills**, if Yannic says so — see the decisions below.
6. **Rewrite this file** with what the mechanism actually turned out to be, and move the belts note
   up: milestone 2 is next.

Gametests to write with the pilot, not after it: a machine places whole or not at all; breaking any
cell drops exactly one machine and leaves no orphan blocks; a hopper on a far corner still reaches
the inventory; the machine still sleeps, asserted with
`level.getBlockTicks().hasScheduledTick(...)`; and a machine placed against a world edge or an
occupied block places nothing rather than something broken.

Two decisions I cannot make
---------------------------

**Where the multiblock code lives.** `nauvis_machines`, `nauvis_power`, `nauvis_fluids` and later
`nauvis_logistics` all need it, and non-negotiable #3 gives exactly two answers: it goes in
Facrafting, or it gets duplicated. The precedent points at duplication — the GUI palettes are
duplicated per mod for precisely this reason, "because a shared base in Facrafting would make these
mods require it and kill the `*_standalone` recipes that exist for its absence". The cost is a
`multiblock/` package copied verbatim into four mods. **Recommendation: duplicate**, and add a
`tools/check_duplicated.py` that diffs the copies, in the spirit of `gen_recipes.py --check`, so
they cannot drift. The alternative — Facrafting owning it — is one copy and a wider remit for a mod
whose remit is currently "the timed crafting model and the crafting interface".

**The two mining drills belong to a released mod.** `neoprogressiveautomation:burner_drill` and
`electric_drill` are what `data/mapping.json` points the Factorio drills at, and CLAUDE.md says
NPA's behaviour should not change under an existing save. Turning them into 2×2 and 3×3 multiblocks
is about as large a behaviour change as exists. Three ways out: change them anyway on a major
version bump and say so in the changelog; leave them 1×1 and accept that two Factorio entities are
the wrong size; or make the footprint a config, which is a permanent tax on every drill code path
for one release's worth of politeness. **Recommendation: change them on a major version**, because
the drills are Factorio entities in a Factorio pack and the pack is the point — but it is Yannic's
released mod and Yannic's call.

What this breaks
----------------

**Existing worlds lose their machines, and get the items back.** A machine saved as one block loads
as a lone anchor with no neighbouring parts, the teardown rule fires on the next block update, and
the anchor's loot table drops the machine into the world. That is the good outcome and it comes free
from the rule above — no data fixer, no migration code, nothing to maintain. Say it in the changelog
and do not build anything to avoid it: the pack is pre-1.0 and the alternative is a data fixer that
has to synthesise eight blocks that may have no room to exist.

Where the pack stands
---------------------

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | recipe selector, six slots, timed craft, screen, runs on 10 FE a tick |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, 30-tick swing, screen with a fuel slot |
| `nauvis_logistics:inserter` | the same on 2 FE a tick and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | carries steam; a run is one object however long, with visible connections |
| `nauvis_fluids:steam` | a real fluid, so pipes and machines meet at NeoForge's capability |
| `nauvis_power:boiler` | burns fuel, makes steam, screen with a fuel slot |
| `nauvis_power:steam_engine` | directional and chainable, steam in through its two ends, 120 FE a tick out |
| `nauvis_power:small_electric_pole` | four blocks tall, climbable, wires itself to whatever it can reach |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve assemblers, one
boiler runs twenty-four. None of that is identity; the ids, ingredients and craft times are, and
those are generated.

**After the visual work, the next real feature is belts** — milestone 2, and the last of the three
decisions that are expensive to reverse. PLAN.md's belt note and the shape in
`nauvis_power/.../grid/` are where to start; `reference/create-src/.../kinetics/belt/transport/` is
the architecture to read and reimplement.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack. **Boot it after any model, fluid or plugin change** |
| `./gradlew :<mod>:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` / `runServerData` | models and language / loot and tags |
| `./gradlew build` | everything, including `checkRecipes` |
| `python tools/gen_recipes.py --check` | the same recipe diff, on its own |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |

Adding a subsystem mod is routine: a subproject in `settings.gradle`, a `build.gradle` copied with
the ids changed, a `src/main/templates/META-INF/neoforge.mods.toml`, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` that puts it in the pack, and its
namespace added to `pack_gametest_namespaces`. `nauvis_power/` is the fullest template.

Recipes: generate into a staging directory with
`python tools/gen_recipes.py --only <modid> --out <tmp>`, then copy across only the files for items
that actually exist. `--write` would write all of that mod's recipes, and a recipe naming an
unregistered item is a load error.

The patterns worth copying
--------------------------

**Sleeping.** No `BlockEntityTicker` anywhere: a registered ticker runs whether or not there is
work. Every machine schedules its own block tick while it has something to do and stops when it
does not. An unscheduled position is never visited, and scheduled ticks are saved with the chunk.

Four ways a machine learns it has work again, and one usually needs more than one:

- its own inventory changed (`onContentsChanged`);
- **electricity or steam arrived** — `MachinePower`, `InserterPower` and `SteamTank` exist only to
  carry that callback. A machine that ran dry has stopped scheduling ticks, so nothing it does can
  restart it: the wake has to come from whatever filled the buffer. Deleting one fails exactly one
  test;
- a *neighbour's* block entity changed — `onNeighborChange`, which every `setChanged()` reaches on
  all six sides. This is how an inserter hears a chest gain an item. Filter on the `neighbor`
  position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, then delete the sleep logic and watch the test go red before
trusting it.

**One object per connected thing, three times over.** `PowerNetwork` for the grid, `FluidNetwork`
for pipe runs, and belts next. Members join and leave; the network ticks once however many members
it has; one that moved nothing drops out of the active set. Read `nauvis_fluids/.../pipe/` first —
it is the smaller of the two, and its header says exactly why it is smaller: a pipe connects to the
six blocks it touches, which is what `neighborChanged` already reports, while a pole reaches 7.5
blocks and needs a spatial index and a level-wide hook to match.

**Capabilities are how mods meet.** No subsystem mod compiles against another. The grid moves FE
through `Capabilities.Energy.BLOCK`, steam moves through `Capabilities.Fluid.BLOCK`, and items
through `Capabilities.Item.BLOCK`. `SteamTank` even looks its fluid up by registry id rather than
importing it, so `nauvis_power` still loads with `nauvis_fluids` absent.

**Sided capabilities carry meaning.** A steam engine offers steam only on the two faces along its
axis, which is what makes its facing matter and what makes a pipe refuse its flank. Note that
*which faces answer* and *which way the machine looks* are two separate registrations with two
separate tests — breaking one leaves the other's test passing.

**A renderer that draws outside its own block has to say so.** `getRenderBoundingBox` defaults to
the one block the block entity sits in, and geometry reaching past it is frustum-culled away with no
error and nothing in the log. The wires between poles hit this exactly. See `API-26.2.md`.

**Multi-blocks are vanilla's job.** `SmallElectricPoleBlock` is four blocks on one `PolePart`
property, the way a door is two: refuse placement without headroom, place the rest from
`setPlacedBy`, and let one `updateShape` rule — a part whose vertical neighbour is wrong turns to
air — be the whole teardown.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back as though nothing happened. Passing `commit = false` turns the same method into the
simulation, so "can I?" and "do it" cannot drift apart.

**One interface.** Facrafting owns the crafting UI and its panel attaches itself to any container
screen. Machine screens grow out of that rather than sit beside it. Palettes are duplicated per mod
rather than shared, because a shared base in Facrafting would make these mods require it and kill
the `*_standalone` recipes that exist for its absence.

Silent failures — these compile, pass tests, and are still wrong
----------------------------------------------------------------

**This is the section the next session most needs.** Every one of these shipped.

- **A model file renamed out from under its item.** `CUBE_COLUMN_HORIZONTAL` writes to
  `block/<name>_horizontal`; the item model defaults to `block/<name>`. The block rendered and the
  item was a checkerboard. Datagen reported nothing — both files were written exactly as asked. Call
  `registerSimpleItemModel(block, modelId)` explicitly whenever a template adds a suffix.
- **A fluid with no `FluidModel`.** Every registered fluid needs one via `RegisterFluidModelsEvent`
  in 26.2; `getStillTexture` on `IClientFluidTypeExtensions` is gone. Not skippable for a fluid
  never placed in the world — it is drawn wherever a tank is shown. NeoForge logs
  `Missing FluidModel for fluid` and nothing else complains.
- **A Jade provider with no config translation.** Jade's settings screen lists every provider and
  asserts if one has no name, and that assert fires from `ScreenEvent.Init` — so a missing
  `config.jade.plugin_<modid>.<uid>` key is not a blank line in a menu, it is a crash the moment any
  screen opens. Add the keys with the provider.
- **A Jade provider that is both halves.** Jade throws at registration if one object implements both
  `IServerDataProvider` and `IComponentProvider`. Outer data class, nested `Client`, shared uid.
- **A machine spills its inventory from `BlockEntity#preRemoveSideEffects`**, not from
  `Block#affectNeighborsAfterRemoval`. The wrong one compiles, reads correctly, and drops nothing.
- **A modded `Container` must register its own item capability.** NeoForge wraps vanilla's, but only
  for a hard-coded list of vanilla block entity types.
- **A built-in datapack needs a `pack.mcmeta`**, or `AddPackFindersEvent` throws a bare NPE naming
  neither the mod nor the directory.
- **Asking for a capability in an unloaded chunk loads it.** Check `level.isLoaded` first — not as
  an optimisation, but so a network at the edge of the loaded world does not drag chunks in.

What is deliberately missing
----------------------------

**An accumulator cannot discharge.** `PowerNetwork` collects supply from endpoints that did not want
energy, so a battery would charge and never feed the grid. It wants a third case; there is no
accumulator until milestone 3.

**A network that moved nothing is re-checked every ten ticks rather than woken exactly.** It hears
about members and machines appearing the moment they do, but "a generator elsewhere filled up" is a
fact about a handler in another mod that owes us no signal.

**No brownout.** PLAN.md wants a machine whose buffer cannot refill to run *slower*; ours stops.

**No pipeline length limit.** Factorio caps a fluid segment at 320 pipes and its tooltip says
`6/320`; ours says `6 pipes` because we enforce nothing. Adding the cap is a real gameplay change —
refusal to connect, not just a number — if it is ever wanted.

**Personal crafts pay at the end, not the start.** Facrafting's `CraftTicker` checks affordability
every tick and consumes on completion, so moving ingredients away mid-craft stalls the job rather
than losing it. It looks like a queue that stopped for no reason, and has been mistaken for a bug.

**Factorio's recipe picker is a modal** anchored to the machine; ours is a persistent column beside
the screen. The modal is the more faithful one. A Facrafting change, and it wants Yannic's eye.

**Smaller.** Nothing tests that inventories survive a save and reload, and nothing tests that a
network rebuilds after a chunk cycle — both paths exist and are only reasoned about. The assembler's
input slots are unfiltered. An inserter at a chunk border whose source chunk cycles while it stays
loaded can sleep through items appearing.

Jade, and a note on dependencies
--------------------------------

The look-at readout is Jade — `maven.modrinth:jade:${jade_version}`, `compileOnly` in the subsystem
mods and `runtimeOnly` in the pack. Its plugin classes load only when it is present, so nothing has
to declare it required. PLAN.md's section covers the rest.

**Licences are not a decision point for including or depending on a mod here.** The pack is not
monetised and ships the way thousands of CurseForge packs do. Weigh version support, API shape and
maintenance instead. This does not extend to *copying*: CLAUDE.md's rule that code is read and
reimplemented with attribution, and that assets are never copied, still stands and is a separate
matter.

A note on KubeJS
----------------

Still on 26.1.2 and in beta — one Minecraft version behind us. LGPL-3.0, so no obstacle once it
ports. Where it would help is **pack policy**: stripping vanilla recipes so the Factorio tree is the
only road forward is milestone 3, and it is one script against hundreds of condition-false JSON
files. Where it cannot help is machines — non-negotiable #3 requires each subsystem mod to stand
alone, so block entities, ticking and capability handlers stay Java.
