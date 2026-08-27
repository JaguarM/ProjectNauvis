Next session
============

Written 2026-08-27 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

**Milestone 1 is closed.** Chest → inserter → assembler → inserter → chest, on a grid, burning
coal at one end. Thirty-five gametests pass. The job below is milestone 2: belts.

The job: transport belts
------------------------

`nauvis_logistics:transport_belt` — 1 iron gear wheel, 1 iron plate, yields 2, 0.5s — and the
thing behind it, which is the actual work.

**This is the last of the three decisions that are expensive to reverse**, and PLAN.md has said
the same sentence about all three:

> A transport line is one object; items are positions on it. That is how Factorio does it too.

Poles and networks were the same sentence with the nouns changed, and the result is in
`nauvis_power/.../grid/` — read it before starting, not for the code but for the shape. One object
per connected thing, members that join and leave rather than tick, and a manager that iterates the
objects rather than the members. A belt run is the same problem with an ordering on it.

### The shortcut PLAN.md licenses, and what it costs

PLAN.md explicitly permits **a BlockEntity per belt block passing items along**, and says the
rewrite must be containable, which is why belts get their own mod. That shortcut is fine for a few
hundred belts and lets milestone 2 ship. It will not survive a real base, for exactly the reason
the naive pole did: N block entities is N ticks a second, and an item takes N ticks to cross N
belts.

**Decide deliberately which one you are building**, and say so in the commit. The grid took the
harder road because "one object per network" was cheap to build once the indexes existed; belts may
not be, because a belt run has an order, a direction, splitters that fork it and undergrounds that
skip part of it. A `BeltInventory` that owns a whole run and `TransportedItemStack`s that carry a
position along it is the endgame either way.

### Read this first, and then do it differently

`reference/create-src/src/main/java/com/simibubi/create/content/kinetics/belt/transport/` is the
reference. `TransportedItemStack` carries a position *along* the belt; `BeltInventory` owns the
whole run; segment blocks delegate to one controller. That is the architecture.

**Create's code is MIT and adapting it with attribution is permitted. Its assets are All Rights
Reserved.** And its belt code is welded to the kinetics framework — stress, rotation, contraptions —
which is the exact weight this pack exists to avoid. Read it and reimplement.

### How you will know it works

Assert the sleeping, not just the moving, and delete the guard to watch the right test go red —
that check has caught two things this session that only looked correct. `power_network_sleeps` and
`electric_inserter_needs_power` are the models for how it is written.

For belts the assertions are: an item put on one end comes off the other in the right number of
ticks; a belt with nothing on it is not scheduled; breaking a belt in the middle of a run splits it
and neither half loses an item; an inserter can take from a belt and put onto one.

Where the pack stands
---------------------

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | recipe selector, six ingredient slots, timed craft, a screen, **runs on 10 FE a tick** |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, burns coal, 30-tick swing |
| `nauvis_logistics:inserter` | the same on 2 FE a tick, 24-tick swing |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | an ingredient that happens to be placeable |
| `nauvis_power:boiler` | burns fuel, makes steam |
| `nauvis_power:steam_engine` | steam in, 120 FE a tick out |
| `nauvis_power:small_electric_pole` | four blocks tall, climbable, wires itself to whatever it can reach |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve assemblers, one
boiler runs twenty-four, an inserter costs almost nothing. None of that is identity; the ids, the
ingredients and the craft times are, and those are generated.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack, six mods |
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
- **electricity arrived** — `MachinePower` / `InserterPower` exist only to carry that callback. A
  machine that ran dry has stopped scheduling ticks, so nothing it does can restart it: the wake
  has to come from whatever filled the buffer. Deleting either callback fails exactly one test;
- a *neighbour's* block entity changed — `onNeighborChange`, which every `setChanged()` reaches on
  all six sides. This is how an inserter hears a chest gain an item. Filter on the `neighbor`
  position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, then delete the sleep logic and watch the test go red before
trusting it.

**A renderer that draws outside its own block has to say so.** `getRenderBoundingBox` defaults to
the one block the block entity sits in, and geometry reaching past it is frustum-culled away with no
error and nothing in the log. The wires between poles hit this exactly. See `API-26.2.md`; the
bounds are computed on the block entity so `pole_wire_bounds_reach_both_ends` can assert them
without a client.

**Multi-blocks are vanilla's job.** `SmallElectricPoleBlock` is four blocks on one `PolePart`
property, the way a door is two: refuse placement without headroom, place the rest from
`setPlacedBy`, and let one `updateShape` rule — a part whose vertical neighbour is wrong turns to
air — be the whole teardown. That rule covers being broken, exploded, `/setblock`ed and moved by
another mod, rather than only the cases somebody thought to handle, and an air result routed
through `Block.updateOrDestroy` is what drops the item. A belt run that wants a visual "this is one
run" state can lean on the same property-plus-`updateShape` shape.

**One object per connected thing.** `nauvis_power/.../grid/` is the worked example and the one to
copy the shape of for belts. `PowerNetwork` holds member poles and machine handles and ticks once;
`PowerNetworkManager` iterates networks, of which a base has a handful, rather than poles, of which
it has thousands; a network that moved nothing leaves the active set. Poles never tick.

Two things in there were not obvious and are written up in PLAN.md's electric network note: how a
machine two blocks from a pole is discovered at all without `nauvis_machines` learning what a pole
is, and why poles are bucketed into 8-block cells.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back as though nothing happened. Passing `commit = false` to the same method turns it into
the simulation, so "can I?" and "do it" cannot drift apart. The network's tick uses a whole
uncommitted transaction as its demand survey for the same reason.

**One interface.** Facrafting owns the crafting UI. Its panel attaches to any container screen; a
menu implementing `RecipeSelector` makes a left-click there point that machine instead of queueing
a personal craft, and right-click still queues. Machine screens grow out of that rather than sit
beside it — see `AssemblerScreen`, which has slots, a progress bar, a charge bar and deliberately
no recipe list.

Traps that have already cost time
---------------------------------

- **A machine spills its inventory from `BlockEntity#preRemoveSideEffects`**, not from
  `Block#affectNeighborsAfterRemoval`. The wrong one compiles, reads correctly, and drops nothing.
  `MinerBlock` in Neo Progressive Automation has exactly that override and only works because its
  entity is a `WorldlyContainer`; do not copy it.
- **A modded `Container` must register its own item capability.** NeoForge wraps vanilla's, but
  only for a hard-coded list of vanilla block entity types.
- **A built-in datapack needs a `pack.mcmeta`**, or `AddPackFindersEvent` throws a bare NPE naming
  neither the mod nor the directory.
- **Asking for a capability in an unloaded chunk loads it.** `PowerNetwork#addEndpoint` checks
  `level.isLoaded` first, and not as an optimisation — without it a pole at the edge of the loaded
  world drags its neighbours in. The chunk loading later is itself the trigger to look again.
- **Screen geometry is arithmetic and can be checked without eyes.** `tools/check_gui_layout.py`
  reads the constants back out of the source and would have failed on the first assembler screen
  three times over.

What is deliberately missing
----------------------------

**An accumulator cannot discharge.** A network collects supply by asking every endpoint that did
*not* want energy, so a battery would charge and never feed the grid. `PowerNetwork` says so in its
own comment; it wants a third case, and there is no accumulator until milestone 3.

**A network that moved nothing is re-checked every ten ticks rather than woken exactly.** It hears
about poles and machines appearing the moment they do, but "a generator elsewhere filled up" and "a
machine got hungry again" are facts about handlers in other mods that owe us no signal. Half a
second of latency, paid by a handful of objects rather than by every pole.

**No brownout.** PLAN.md wants a machine whose buffer cannot refill to run *slower*; ours stops.
That is the refinement the per-machine buffer was designed to allow.

**Personal crafts pay at the end, not the start.** Factorio takes a craft's ingredients the moment
you queue it. Facrafting's `CraftTicker` checks affordability every tick and only consumes on
completion, so moving the ingredients away mid-craft stalls the job instead. The job is kept and
resumes — it is not lost — but it looks like a queue that stopped for no reason, and it has been
mistaken for a bug once. Consuming up front needs somewhere to hold ingredients that are spent but
not yet delivered.

**Factorio's recipe picker is a modal**, anchored to the machine, with category tabs and a tick to
confirm. Ours is a persistent JEI-style column beside the screen. Same information, different
shape, and the modal is the more faithful one. That is a Facrafting change now that its panel is the
shared interface, and it wants Yannic's eye rather than a guess.

**Smaller.** Nothing tests that inventories survive a save and reload, and nothing tests that a
network is rebuilt correctly after a chunk cycle — both paths exist and both are only reasoned
about. The assembler's input slots are unfiltered. An inserter at a chunk border whose source chunk
cycles while it stays loaded can sleep through items appearing.

Textures
--------

Every model points at *vanilla* textures on purpose — a blast furnace body for the assembler, a
furnace with a front face for the burner inserter and a blast furnace for the electric one so the
two are told apart, bricks for the boiler, iron for the engine and the pipe, a stripped oak fence
post for the pole. A model naming a texture the mod does not ship renders as the magenta
checkerboard, which reads as a broken model rather than as art nobody has drawn yet.

`../NeoProgressiveAutomation/texture-workshop/` is the approach that produced the drills, and its
README is the best writing in these repos on why vanilla textures look the way they do — six colours
for cobblestone, three ideas for a furnace face, never pure black. `make_miner_textures.py` renders
sixteen textures from three 16x16 ASCII maps plus one five-tone palette per tier, using Pillow.
Editing a map changes every tier together, so a family cannot drift apart.

A note on KubeJS
----------------

Still on 26.1.2, in beta, not on 26.2 — one Minecraft version behind us, same as AE2, and `rhino`
with it. LGPL-3.0, so no obstacle to shipping once it ports.

Where it would help is **pack policy**: stripping vanilla recipes so the Factorio tree is the only
road forward is milestone 3, and it is one script against hundreds of condition-false JSON files. It
also subsumes the Item Obliterator idea.

Where it cannot help is everything above. A script is not a mod, and non-negotiable #3 requires each
subsystem mod to stand alone — so block entities, ticking, sleeping and capability handlers stay
Java.
