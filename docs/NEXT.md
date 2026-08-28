Next session
============

Written 2026-08-28 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the job below is done — it describes one job, not the project.

**Eighty-one gametests pass and the pack builds clean.** Every machine is the size Factorio made
it, the lab exists, five checks run in `./gradlew build`, and the transport belt works — items you
can watch move along it, corners that look like corners, and a belt that carries you. What is left
is not a missing block. It is that **the inserter beside the belt does not behave the way a
Factorio player expects**, and both of the ways it is wrong are ones you only notice once there is
a belt to notice them against.

The job: polish the burner inserter
----------------------------------

Two behaviours, no new blocks, no new architecture. Both are *identity* in the sense
non-negotiable #1 means — they are what a player's hands already know how to do — and both are
small. Do them in either order; the second is the smaller.

### 1. A burner inserter should fuel itself

Today it burns what is in its slot and, when that runs out, stops and sleeps until somebody puts
coal in it. In Factorio it takes fuel from **what it is picking up**: a burner inserter working a
coal belt keeps itself going, which is the whole reason the burner tier is playable before there is
a grid. Right now the first thing a new player builds is an inserter that dies quietly.

Where it goes: `BurnerInserterBlockEntity.readyToSwing`, which is already the one place that asks
"can I afford a tick?" and already returns `burnTime > 0 || refuel(level)`. It wants a third
clause — take a fuel item out of the source and put it in the fuel slot — and the pieces are all
in hand:

- **the source is already cached.** `InserterBlockEntity` holds a `BlockCapabilityCache` for the
  block behind it. It is `private`; either widen it or add a `protected` accessor. Do not build a
  second cache.
- **the filter is already written.** `InserterFuel.isValid` rejects anything that will not burn, so
  "is this fuel?" is "will my own slot take it?" and needs no second copy of the rule.
- **one transaction**, extract-then-insert, exactly as `InserterBlockEntity.move` does it. Half a
  transfer would be an item destroyed.

Three things to decide, and the first wants a pass against the wiki because it is from memory:

- **Does it top up, or only when empty?** Factorio's fuel slot holds a stack, and I believe a
  burner inserter fills it rather than taking one lump at a time — but that is memory, and the
  difference is visible: an inserter that hoards takes coal out of a line that is feeding a
  furnace. Check `https://wiki.factorio.com/Burner_inserter` before choosing.
- **Does taking the fuel cost a swing?** If it does, an inserter that runs dry pauses visibly and
  the player can see what happened. If it does not, it simply never stops. The second is kinder and
  I think it is Factorio's; the wiki will say.
- **What if the source is the thing it is meant to be feeding?** A burner inserter taking from a
  coal belt and feeding a furnace will eat a lump now and then, and that is correct — it is what
  the fuel is for. Do not add a rule against it.

**The sleeping already works and must keep working.** An inserter with no fuel schedules no ticks,
so nothing it does can restart it: the wake has to arrive from outside. It already does, twice
over — `InserterFuel.onContentsChanged` for coal put in by hand, and `InserterBlock.onNeighborChange`
for the source's contents changing, which is filtered to the two positions an inserter can use.
That second one is what will wake it when coal reaches the belt behind it. **Assert it**, the way
`inserter_wakes_when_source_fills` asserts the existing case: put coal on the belt behind a dry
inserter and check it is scheduled *in the same tick*.

### 2. An inserter should load only the far lane of a belt

A Factorio inserter puts things on the **far** lane and, when that lane is full, waits. It does not
switch to the near one. That is not a detail — it is why one belt can feed two rows of machines,
and why a player puts inserters on both sides of a bus.

Ours drops onto the far lane and then falls back to the near one, so a single inserter fills a whole
belt. The fallback is five lines in `BeltAccess.insert`, which walks the array `lanePreference`
returns.

The fix is to tell insertion and extraction apart, because they do not want the same rule:

- **inserting** is far lane only, when the asker is on a side at all;
- **extracting** keeps its preference — far lane first, near lane second. That one is *also* from
  memory and also wants the wiki: an inserter certainly picks from the far side, but whether it
  will take from the near lane when the far one is empty is the part I am unsure of.

`lanePreference` is used by both `insert` and `here()`, so it has to become two methods rather than
one with a flag — a flag would let a later edit quietly re-couple them.

Two cases to keep in mind and one thing not to break:

- **an asker with no side** — a hopper above or below, or a capability looked up with `null` — has
  no far lane. Let it use both. Factorio has no hoppers, so nothing is being contradicted, and a
  hopper that could not fill a belt would just be broken.
- **`belt_holds_four_items_a_tile` asks from `null`** and expects eight. That stays true and should
  stay in. The new test is the sided one: fill from the north, and the fifth item is refused while
  the left lane is empty and visible.
- **the inserter already waits properly.** `InserterBlockEntity.move` spans both halves in one
  transaction and rolls back, so a full far lane holds the swing rather than dropping the item.
  Nothing there needs touching, and it would be easy to "fix" it into losing items.

### After this: the rest of milestone 2

Three items and a job, all still open, in the order they get easier:

1. **`long-handed-inserter`.** The existing inserter with a reach of two. No new architecture; note
   it must reach *over* the block between, which the current one never had to think about. Doing
   this straight after the fuel work is cheap, because it is the same two classes again.
2. **The underground belt.** Two blocks that find each other and a gap the run treats as
   continuous. The natural fit is a third rule in `BeltLines.successor` — an underground entrance's
   successor is its matching exit rather than the block in front — after which the run needs to know
   nothing else. Factorio's maximum distance between the two ends is **5** for the yellow tier, 7
   fast, 9 express; that is identity and belongs in `data/mapping.json` beside `speed`, with a check
   in `tools/check_models.py` like the one `speed` now has.
3. **The splitter**, 2×1 and directional — the first multi-block that is not square. `multiblock/`
   is the framework and is copied into four mods already. The belt side of it is a run that ends at
   the splitter and two that start after it, with the splitter alternating between them.
4. **Fast-replace by tier.** A belt in hand already points the belt you click on the way you are
   facing, which is half of Factorio's belt-laying gesture. The other half is that a *faster* belt
   replaces a slower one, and it cannot be written until there is a second tier to hold. It is
   `BeltBlock.useItemOn`, and what changes is: swap the *block* rather than set a property — which
   does remake the block entity, so `beltPlaced`/`beltRemoved` fire and `beltTurned` is not wanted
   on that path; carry the items on that block across the block entity being remade, which will not
   happen for free; hand the old belt back and pay for the new one unless the player is in creative;
   and refuse to *downgrade*, or a stray click wrecks a bus. The run needs no thought — a run never
   spans two tiers, so the line splits and rejoins by itself.

Each step ends with `./gradlew build`, `:nauvis:runGameTestServer`, and a client boot. The client
boot is not optional: three of the last four bugs found in this pack were found by a person looking
at the game, and one of them — see the rotation entry in the silent-failures list — passed sixty-
three tests while being visibly wrong from three sides.

### The belt, if you have to touch it

Read `nauvis_logistics/.../belt/` in this order and the whole thing falls out:

| | |
|---|---|
| `Belts.java` | the numbers, and why distances are integer sixty-fourths of a block |
| `BeltLane.java` | **the idea.** Items are stored as the gaps between them, not as positions, so a flowing belt writes one number a tick and a jammed one writes none |
| `BeltRun.java` | one line: its blocks, its two lanes, its tick, and how it hands to the next line |
| `BeltLines.java` | every run in a level, and how lines are cut and joined when a belt is placed |
| `BeltAccess.java` | **where job 2 lives** — how everything else in the game meets a belt |
| `BeltShape.java` | how a corner knows it is one, and why there are two of them rather than eight |
| `BeltBlock.stepOn` | why standing on a belt carries you, and why that is not `entityInside` |
| `BeltBlock.useItemOn` | a belt in hand points the belt you click on the way you are facing |
| `client/BeltRenderer.java` | the items you can see |
| `texture-workshop/make_belt_textures.py` | the art, and why the tread scrolls at exactly 1.875 tiles a second |

Four things about it are load-bearing for anything built on top:

- **Items are pinned to a block and an offset into it**, never to a distance along a run. That is
  what makes cutting, joining, lengthening and turning a line safe, and it is asserted by
  `belt_survives_being_cut`.
- **The client runs the same simulation.** It builds the same runs out of the same block states and
  moves the items itself, so a belt full of items costs no network traffic. Only two things are ever
  sent — an item put on the belt from outside, and one taken off — because those are the only two a
  client cannot work out. **Anything added to belts must keep that property**, or the reason belts
  are affordable goes away. Job 2 does not touch it: refusing a lane is a decision made before the
  message that reports the insert.
- **A run is awake while it has items, not while it is moving.** No dormant sweep and no wake-up
  plumbing, because `BeltLane` makes a jammed belt cost the same as an empty one.
- **A closed ring of belts is one run that wraps**, and **a block state change does not touch the
  graph** — turning a belt leaves its block entity alone, so `BeltLines.beltTurned` is a third way
  in that anything editing a belt in place will need.

The rules a machine is built to
-------------------------------

These outlive the job above and are the answer to "how big, how tall, and can you walk on it".

### Footprint is identity. Height is ours.

Non-negotiable #1 says ids, ingredients and craft times are Factorio-correct from the first commit,
because they live in world saves and in the player's head. **A footprint is the same kind of fact.**
Three-by-three is why an assembler column spaces the way it does; five-by-three is why a boiler
feeds a *row* of engines. Get it wrong and every blueprint a player carries in their head is wrong,
and fixing it later moves every machine in every world.

Height is the opposite. Factorio is two-dimensional and has no opinion, so height is ours to choose,
free to change, and the place to spend effort on making a machine look like something.

Footprints live in `data/mapping.json` as a `size` field and `tools/check_models.py` holds every
machine's cells to them. Heights live in the shape class next to the models and are tuned by
looking at them.

### One tile is one block

The scale is 1:1 and it is not really a choice: the belt, the inserter and the pipe are one tile in
Factorio and one block here, and everything else has to agree with them or nothing lines up. What
1:1 buys —

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

**A machine you can walk across beats a machine you walk around**, and it decides the silhouette.
A Factorio player tiles machines with no gaps, because in Factorio you can always walk round the far
end of the field. Nine 3×3 machines two solid blocks tall is a wall with no way over it, and the
player is sealed out of their own base.

The answer every machine in the pack now uses:

| | |
|---|---|
| **1.0** | the wall around the outside. One jump, and the only climb in a field of any size |
| **0.75** | the floor inside the wall. A quarter-block dip, under the 0.6 step, so crossing is walking |
| **2.0** | whatever the machine puts in the middle — the one thing you walk around |
| **0.5** | for anything meant to be crossed without even a jump: the electric drill, solar panels, and **belts** |

Two consequences. **The upper storey is mostly air**, so a 3×3 machine two blocks tall is ten blocks
and not eighteen — which is why `MachineShape` takes a set of cells rather than a box. And **the
tall part goes in the middle**, so tiled machines stand their obstacles apart and leave lanes.
`assemblers_tile_walkably` and `power_machines_tile_walkably` walk those lanes and assert every step.

**Collision and silhouette are allowed to disagree** — `PolePart` gives the crossarm a full outline
and no collision, because a shape three blocks over your head that you cannot see should not catch
you. But for something you are meant to walk across, the two agreeing is the point.

The multiblock mechanism, briefly
---------------------------------

`multiblock/` — `MachineCell`, `MachineShape`, `MachineParts`, `Boxes`, `Multiblock` — is copied
into `nauvis_machines`, `nauvis_power`, `nauvis_research` and `NeoProgressiveAutomation`, and
`tools/check_duplicated.py` holds the copies byte-identical (`--sync` pushes the original out; the
copies are never edited). It is `SmallElectricPoleBlock` with two more axes, and every class in it
says why it is the way it is. Read `MachineShape` and `Multiblock` and you have all of it.

The parts worth knowing before touching a machine:

- one block id, one item, an `IntegerProperty part` on every block, and the anchor found by
  arithmetic rather than a lookup — no block entity on the other cells;
- one `updateShape` rule is the whole teardown, which is why a shape's cells must be orthogonally
  connected, which the constructor enforces;
- capabilities are registered against the **block**, not the block entity, so any cell answers —
  that is what lets a pole supply a machine whose middle is out of range, and it is why
  `PowerNetwork` reduces endpoints to distinct handlers;
- **ports** name a cell and a face, so a boiler's steam leaves one block and an engine takes it at
  the open ends of its spine;
- geometry is stated once per machine and read by the model provider, the `VoxelShape` and the
  item model. `MachineParts` is the shared shell so the machines read as one family — **Yannic has
  said that still wants refinement**, and it is one file.

Where the pack stands
---------------------

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | 3×3 and ten blocks; recipe selector, six slots, timed craft, screen, 10 FE a tick |
| `nauvis_logistics:transport_belt` | half a block high and walked over; a run is one object however long, two lanes, items you can watch, and it carries you |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, 30-tick swing, screen with a fuel slot |
| `nauvis_logistics:inserter` | the same on 2 FE a tick and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | carries steam; a run is one object however long, with visible connections |
| `nauvis_fluids:steam` | a real fluid, so pipes and machines meet at NeoForge's capability |
| `nauvis_power:boiler` | 3×2 and seven blocks; burns fuel, steam out of the block under the chimney |
| `nauvis_power:steam_engine` | 5×3 and seventeen blocks; steam in at the open ends of its spine, 120 FE a tick out |
| `nauvis_power:small_electric_pole` | four blocks tall, climbable, wires itself to whatever it can reach |
| `nauvis_research:lab` | 3×3 and ten blocks; eats science packs on 8 FE a tick and counts research cycles |
| `nauvis_research:science_pack_1` | red science. Craftable now — copper plate and an iron gear wheel |
| `neoprogressiveautomation:burner_drill` | 2×2 and five blocks; a full block with a chimney over the firebox |
| `neoprogressiveautomation:electric_drill` | 3×3 and nine blocks; a half-block deck you walk over, output head at the front |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve assemblers, one
boiler runs twenty-four. None of that is identity; the ids, ingredients and craft times are, and
those are generated.

**The lab has no technology tree**, and that line is deliberate: Factorio's lab does not know what
it is researching either, so the machine could be built without one. It counts cycles and consumes
one of every kind of pack it holds, which is already the rule a technology will impose. Milestone 3.

**The lab's recipe woke up.** It cost four transport belts and shipped with a
`neoforge:registered` condition so it would start working the day belts existed; that day was this
session, the `pending` flag came off `transport-belt` in `data/mapping.json`, and the condition
regenerated away. `science-pack-2` costs an inserter and a belt and is unblocked the same way when
it is written.

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
| `python tools/check_models.py` | every model, texture and blockstate reference, resolved — and footprints and belt speeds, against `data/mapping.json` |
| `python tools/check_duplicated.py` | the copied packages, against each other. `--sync` to fix |
| `python tools/check_gametests.py` | every gametest, for a type registered as well as an instance |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python texture-workshop/make_belt_textures.py` | the belt's art, from ASCII maps. `--preview` for a sheet |

The last five are `checkRecipes`, `checkModels`, `checkDuplicated`, `checkGameTests` and
`checkGuiLayout` in the root `build.gradle`, and all of them hang off `:nauvis:check`. They read
files and start nothing, so they cost a second between them.

Adding a subsystem mod is routine: a subproject in `settings.gradle`, a `build.gradle` copied with
the ids changed, a `src/main/templates/META-INF/neoforge.mods.toml`, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` that puts it in the pack, and its
namespace added to `pack_gametest_namespaces`. `nauvis_power/` is the fullest template and
`nauvis_research/` is the newest — it was made by following exactly that list, so its diff is what
adding a mod costs.

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
for pipe runs, and `BeltRun` for belt lines. Members join and leave; the network ticks once however many members
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
- **A machine with a footprint is several endpoints on the grid, and they are the same machine.**
  Every block of a machine publishes the energy capability, so that a pole supplies a machine whose
  middle is out of range - Factorio's rule, and the reason footprints were worth having. One engine
  is then up to five entries in a pole's supply area: five shares of a shortfall, a wrong count in
  the readout a player reads, and eventually a machine sold energy it had just asked for through
  two of its own blocks. `PowerNetwork` reduces endpoints to distinct handlers by object identity
  each tick. Identity rather than position, so any mod's multi-block gets it for free.
- **Gametests have no padding by default, and machines now sprawl.** `TestData`'s last field is
  `padding` and it defaults to 0. With one-block machines that was survivable; with a seventeen-
  block engine and a chain of two reaching ten blocks, the machines of one test land in the next
  test along - where they are broken by its blocks or joined to its network. The failure then
  appears in whichever test ran second, which is the worst kind: real, silent, and blamed on the
  wrong code. Every test that builds a machine now asks for room.
- **A shape that builds its cells in a loop reads as smaller than it is.** `check_models.py` gets
  a machine's footprint by reading `new MachineCell(...)` calls out of the source, and a loop is
  not a number: the electric drill, written as a nested loop, reported itself as one tile by two
  and passed. The checker now refuses to guess when it cannot read every cell, and every shape
  writes its cells out. That is worth doing anyway — a cell's place in the list is its `part`
  value, and `part` values are in world saves.
- **`createBlockStateDefinition` runs inside `Block`'s constructor**, before any field of your
  subclass exists. A block that picks its blockstate properties from a field reads null and picks
  the wrong ones. The drills hit this: one `MinerBlock` with a tier field gave the electric drill a
  five-value `part` property and a nine-value default state. It threw at registration, which was
  luck — the same mistake between two shapes of equal size would have been silent. Answer with a
  constant on a subclass, which exists long before any block does.
- **A `VariantMutator` sets `y`, it does not add to it.** A multi-block turns twice over: each
  cell has its own quarter turn, and the machine has a facing. Generating that as
  `.with(cellDispatch).with(ROTATION_HORIZONTAL_FACING)` looks exactly right and is wrong in three
  directions out of four - the facing *overwrites* each cell's turn, so every corner of an
  east-facing boiler points the same way. Dispatch over both properties at once and add the two
  turns by hand.

  What makes it worth its own entry is how long it survived. `MachineCell` adds the two rotations
  before building its `VoxelShape`, so the collision was right the whole time and **the machine
  you saw and the machine you walked into were different objects** - which is the failure `Boxes`
  warns about in as many words, in a file written to prevent it. Every test passed, because tests
  look at collision and nobody can see a model from a gametest. It was found by a person turning a
  boiler round. `check_models.py` now checks each variant's `y` against the shape's own arithmetic.
- **A gametest whose type was never registered passes anyway, and breaks a client.** A test is
  two registrations: the instance, which runs, and the `MapCodec` type in
  `Registries.TEST_INSTANCE_TYPE`, which exists so a test *could* come from a datapack. Ours never
  do, so the type reads as dead paperwork - but the instance registry is synced to clients, and an
  entry with no codec throws `Failed to serialize ResourceKey[minecraft:test_instance / ...]` on a
  client boot while `runGameTestServer` stays green. Two went missing here for a day. The
  invariant is per *class*, not per name - a test id and a type id are different registries, and
  `nauvis:pack_loads` registered under type `nauvis:registry_presence` is fine - so
  `tools/check_gametests.py` matches every `GameTestInstance` that is registered to run against
  the codecs handed to `TEST_TYPES`.
- **A block put down by anything but a player never runs `getStateForPlacement`.** A command, a
  structure, another mod or `GameTestHelper.setBlock` all write the state you hand them, so a block
  that works out how it looks from its neighbours is drawn wrong and stays wrong: nothing changes
  beside it afterwards, so no `updateShape` ever comes. Belt corners were straight lines for
  exactly this reason, and only in a gametest, which is the lucky version of it. `BeltBlock`
  re-reads its own shape and its neighbours' when it joins the graph, which also covers the belt
  whose corner is in a chunk that had not loaded yet.
- **A horizontal `Entity.move` tells the entity it is falling.** `Entity.move` only decides
  whether something is standing on anything when the movement had a vertical component, so a push
  along a belt with `y = 0` clears `onGround`. Nothing looks wrong for a tick — and then the next
  tick's `stepOn` does not run, because that hook only fires for something on the ground, so the
  belt carries in stutters. For a player it also breaks fall damage and step sounds, both of which
  are worked out from the same flag. `BeltBlock.stepOn` reads `onGround()` before the move and puts
  it back after.
- **`GameTestHelper.spawnItem(Item, BlockPos)` spawns at the block's corner, not its middle.** An
  item dropped over a one-block-wide thing therefore hangs half off it and behaves like something
  standing beside it rather than on it. Use the `(float, float, float)` overload and add the half.
  This cost an hour of reading `ItemEntity` for a bug that was in the test.
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

**A belt does not load a chest.** Deliberate and Factorio-faithful: a belt running into a container
backs up, and taking things off a belt is what inserters are for. It is one method — `BeltRun`'s
hand-off — if it is ever wanted the other way. `belt_does_not_load_a_chest` pins it.

**Crouching stops a belt carrying you**, which Factorio does not do — there a belt has you whatever
you do. It is in for the Minecraft reflex: without it, placing a machine beside a working belt means
being carried off mid-click. One line in `BeltBlock.stepOn` if it is ever unwanted.

**A belt does not turn you as it carries you.** An entity on a corner is pushed the way that block
faces, so going round a bend on a belt is two straight shoves rather than an arc. Items do curve.

**Two belt tiers meeting is two runs, not one.** Correct — Factorio's transport lines split at a
tier change too — but there is only one tier so far, so it has never been looked at.

**Client and server belt runs can differ at a chunk edge**, because a client only has the belts in
its loaded chunks and a run is built from whatever belts are there. What that costs is a belt at the
very edge of the loaded world appearing to back up when it is not. Nothing is out of step where a
player can see it, and a chunk arriving re-seeds that block's items from the block entity.

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
