Next session
============

Written 2026-08-26 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

The job: the electric network, and the small power pole
-------------------------------------------------------

`nauvis_power:small_electric_pole` — 2 copper cable, 2 oak planks — and the thing behind it, which
is the actual work: **moving electricity from generators to machines without paying for it every
tick.**

This is the second of the two architectural decisions that are expensive to reverse. The other is
belts, and PLAN.md's belt note already says the thing that matters here too:

> A transport line is one object; items are positions on it. That is how Factorio does it too.

The same sentence with the nouns changed is the whole design. **An electric network is one object;
poles are members of it.** Get that wrong and it is a rewrite, not a patch.

### Why this is not a normal block

Everything built so far sleeps because a machine can answer "have I got work?" by looking at
itself. A pole cannot. It is not a machine that does something — it is one node in a graph whose
job is to make a hundred other blocks reachable from each other, and the graph is the thing that
has to tick, not the node.

Three shapes, and the first two are traps:

**Naive: every pole ticks and pushes to its neighbours.** N poles × 20 ticks a second, and a
packet of energy takes N ticks to cross N poles. This is what a base of ten thousand poles cannot
afford, and it is what non-negotiable #5 exists to prevent.

**Naive: every pole rescans its supply area.** A 5×5×N block scan per pole per tick. Worse.

**What to build: one object per connected network.** The network holds its member poles, the
producers it can pull from and the consumers it can push to. It ticks **once**, not once per pole.
The cost is one iteration per network plus the machines that actually want energy — a big base has
a handful of networks, not thousands. A network with no producer, or with every consumer full,
does not tick at all.

### Read this first, and then do it differently

**`reference/mods/energizedpower-3.0.0+26.2.x-neoforge.jar` is the one FE mod on our exact
Minecraft version, and it is MIT.** It is worth twenty minutes with `javap` before writing
anything, because it is a working answer to this exact problem and it is a working answer of the
shape this pack cannot use:

- `CableBlock` has a `getTicker`, so **every cable ticks every tick**;
- every `CableBlockEntity` holds its *own* `Map<Pair<BlockPos, Direction>, EnergyHandler>` of
  producers and consumers, plus its own `Deque<BlockPos>` of the component it discovered — so the
  whole network's endpoint table is duplicated once per cable;
- `updateConnections` re-floods on change, per cable.

That is a perfectly reasonable mod and a bad fit for a pack whose first non-negotiable about
performance is that idle machines cost zero. Read it for what the endpoints look like and how it
handles connection changes; do not copy its tick model. MIT means adapting is allowed *with
attribution* — check `CLAUDE.md`'s licensing section before lifting a line.

### The shape to build

- **`PowerNetwork`** — a plain object, not a block entity. Holds member pole positions, and
  `BlockCapabilityCache` handles on producers and consumers so a transfer is never a lookup.
  One `tick()`: pull from producers into a budget, push the budget to consumers.
- **A per-level manager** driven by `ServerTickEvent.Post` (or `LevelTickEvent.Post` — both have
  `Pre`/`Post` subclasses). It iterates *active* networks only. One object ticking, not thousands.
- **Poles join and leave**, they do not tick. `onLoad` registers, `setRemoved` and
  `onChunkUnloaded` deregister, `neighborChanged` re-links.
- **Machines find poles, not the other way round.** A machine scans a small radius for poles when
  it is placed or loads, and registers with whatever network it finds. A pole placed later scans
  once for machines in range. Both are O(r³) but only on placement, which is rare — the thing that
  must never be per-tick is the scan.
- **Do not persist the graph.** It is derivable from block positions, so saving it is caching, and
  invalidating that cache across chunk loads is where the bugs would live. Rebuild on load.

### Numbers

Factorio's small electric pole: **wire reach 7.5** (pole to pole) and a **5×5 supply area** (two
blocks either side). Both are behaviour rather than identity, so they are yours to tune — the id,
the 2 copper cable, the 2 oak planks and the 0.5s are not, and those are generated. Two radii, not
one, is worth keeping: it is what makes a Factorio base look like a Factorio base.

### How you will know it works

Assert the sleeping, not just the flowing. `power_chain_sleeps` in `nauvis_power` is the model, and
so is the way it was checked: delete the guard and watch the right test go red. When that was done
to the boiler it failed on the *engine*, because a boiler that keeps burning keeps calling
`setChanged`, which wakes the engine through `onNeighborChange` forever. Cascades unravel from
either end, so test from both.

For a network the assertions are: a network with a full consumer is not in the manager's active
set; breaking a pole splits one network into two; placing one merges them; and a machine two poles
away from a generator receives energy.

Then: spend it
--------------

The pole is only worth having if something drains it.

**Make the assembler consume FE.** Factorio's assembling machine 1 is electric and ours runs on
nothing. It wants a `SimpleEnergyHandler` buffer, an insert-only capability so a network can fill
it, and a per-tick cost while crafting. PLAN.md's brownout shortcut — "a machine whose buffer
cannot refill runs slower" — is a later refinement; stopping when empty is enough first.

**Watch the sleep rules when you do.** The assembler currently wakes on inventory, recipe and
neighbour changes. Add "energy arrived", or a machine that ran dry will sleep through the grid
coming back. That is the same class of bug the power chain's test was written to catch.

**Then the electric `inserter`**, which is deliberately unregistered: it costs an electronic
circuit and runs on the grid, and shipping it before there is a grid would mean an item that works
without the power it is supposed to need. That closes milestone 1.

Where milestone 1 stands
------------------------

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | recipe selector, six ingredient slots, timed craft, a screen |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, burns coal |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | an ingredient that happens to be placeable |
| `nauvis_power:boiler` | burns fuel, makes steam |
| `nauvis_power:steam_engine` | steam in, FE out |

Twenty gametests pass. Nothing consumes power yet, so the boiler and the engine are a demo rather
than part of the factory — which is what the job above is for.

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
| `python tools/check_gui_layout.py` | the assembler screen's boxes, for overlaps |

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

Three ways a machine learns it has work again, and one usually needs more than one:

- its own inventory changed (`onContentsChanged`);
- a *neighbour's* block entity changed — `onNeighborChange`, which every `setChanged()` reaches on
  all six sides. This is how an inserter hears a chest gain an item and how an engine hears a
  boiler make steam. Filter on the `neighbor` position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, then delete the sleep logic and watch the test go red before
trusting it — every wake mechanism here was checked that way and two only looked correct.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back as though nothing happened. Passing `commit = false` to the same method turns it into
the simulation, so "can I?" and "do it" cannot drift apart.

**One interface.** Facrafting owns the crafting UI. Its panel attaches to any container screen; a
menu implementing `RecipeSelector` makes a left-click there point that machine instead of queueing
a personal craft, and right-click still queues. Machine screens grow out of that rather than sit
beside it — see `AssemblerScreen`, which has slots and a progress bar and deliberately no recipe
list.

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
- **Screen geometry is arithmetic and can be checked without eyes.** The first assembler screen drew
  its progress bar through a column of slots and two labels through each other.
  `tools/check_gui_layout.py` reads the constants back out of the source and would have failed on
  all three.

What is deliberately missing
----------------------------

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

**Smaller.** Nothing tests that inventories survive a save and reload. The assembler's input slots
are unfiltered. The inserter's 30-tick swing is the one number in the pack not from Factorio's dump
(its burner inserter is about 0.6 items a second). An inserter at a chunk border whose source chunk
cycles while it stays loaded can sleep through items appearing.

Textures
--------

Every model points at *vanilla* textures on purpose — a blast furnace body for the assembler, a
furnace with a front face for the inserter so its facing is visible, bricks for the boiler, iron for
the engine and the pipe. A model naming a texture the mod does not ship renders as the magenta
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
