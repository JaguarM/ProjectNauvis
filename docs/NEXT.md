Next session
============

Written 2026-08-26 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

Where milestone 1 stands
------------------------

*Chest → inserter → assembler → inserter → chest, burning coal.* All of it exists except the
burning-coal half being connected to anything.

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | recipe selector, six ingredient slots, timed craft, a screen |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, burns coal |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | an ingredient that happens to be placeable |
| `nauvis_power:boiler` | burns fuel, makes steam |
| `nauvis_power:steam_engine` | steam in, FE out |

Twenty gametests pass. `./gradlew :nauvis:runClient` starts the whole pack.

The job: spend the electricity
------------------------------

Nothing consumes power yet, so the boiler and the engine are a demo rather than a part of the
factory. Three things close milestone 1, in this order.

**1. `nauvis_power:small_electric_pole`** — 2 copper cable, 2 oak planks. This is the one piece of
real design left. A pole is an FE cable with a wide connection radius, which makes it a graph
problem rather than a block: poles link to other poles within range, and machines draw from any
pole that reaches them. It was left out of the power commit on purpose rather than rushed
alongside two generators.

Non-negotiable #5 is the hard part. A naive pole rescans its radius every tick; a good one builds
its neighbour set once, keeps it with `BlockCapabilityCache`, and rebuilds only on
`neighborChanged`. Whatever it does, assert `hasScheduledTick` is false for an idle one.

**2. Make the assembler consume FE.** Factorio's assembling machine 1 is electric, and ours runs
on nothing. It wants a `SimpleEnergyHandler` buffer, an insert-only capability so cables can fill
it, and a per-tick cost while crafting. PLAN.md's brownout shortcut is "a machine whose buffer
cannot refill runs slower", which is a later refinement — stopping when empty is enough first.

Careful with the sleep rules: the assembler currently wakes on inventory, recipe and neighbour
changes. Add "energy arrived", or a machine that ran dry will sleep through the grid coming back.
That is exactly the bug the power chain's own test was written to catch.

**3. Then the electric `inserter`**, which is deliberately unregistered: it costs an electronic
circuit and runs on the grid, and shipping it before there is a grid would mean an item that works
without the power it is supposed to need.

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

Three ways a machine learns it has work again, and a machine usually needs more than one:

- its own inventory changed (`onContentsChanged`);
- a *neighbour's* block entity changed — `onNeighborChange`, which every `setChanged()` reaches on
  all six sides. This is how an inserter hears a chest gain an item and how an engine hears a
  boiler make steam. Filter on the `neighbor` position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, and then delete the sleep logic and watch the test go red
before trusting it — every wake mechanism here was checked that way, and two of them only looked
correct. `power_chain_sleeps` is the best example: three blocks each deciding to do nothing, in
order, and deleting one guard at the far end breaks it at the near end.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back as though nothing happened. Passing `commit = false` to the same method turns it
into the simulation, so "can I?" and "do it" cannot drift apart.

**One interface.** Facrafting owns the crafting UI. Its panel attaches to any container screen; a
menu implementing `RecipeSelector` makes a left-click there point that machine instead of queueing
a personal craft, and right-click still queues. Machine screens should grow out of that rather than
sit beside it — see `AssemblerScreen`, which has slots and a progress bar and deliberately no
recipe list.

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
- **Screen geometry is arithmetic and can be checked without eyes.** The first assembler screen
  drew its progress bar through a column of slots and two labels through each other.
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
shape, and the modal is the more faithful one. That is a Facrafting change now that its panel is
the shared interface, and it wants Yannic's eye rather than a guess.

**Smaller.** Nothing tests that inventories survive a save and reload. The assembler's input slots
are unfiltered. The inserter's 30-tick swing is the one number in the pack not from Factorio's dump
(its burner inserter is about 0.6 items a second). An inserter at a chunk border whose source chunk
cycles while it stays loaded can sleep through items appearing.

Textures
--------

Every model points at *vanilla* textures on purpose — a blast furnace body for the assembler, a
furnace with a front face for the inserter so its facing is visible, bricks for the boiler, iron
for the engine and the pipe. A model naming a texture the mod does not ship renders as the magenta
checkerboard, which reads as a broken model rather than as art nobody has drawn yet.

`../NeoProgressiveAutomation/texture-workshop/` is the approach that produced the drills, and its
README is the best writing in these repos on why vanilla textures look the way they do — six
colours for cobblestone, three ideas for a furnace face, never pure black.
`make_miner_textures.py` renders sixteen textures from three 16x16 ASCII maps plus one five-tone
palette per tier, using Pillow. Editing a map changes every tier together, so a family cannot drift
apart. The same trick will work here.

A note on KubeJS
----------------

Still on 26.1.2, in beta, not on 26.2 — one Minecraft version behind us, same as AE2, and `rhino`
with it. LGPL-3.0, so no obstacle to shipping once it ports.

Where it would help is **pack policy**: stripping vanilla recipes so the Factorio tree is the only
road forward is milestone 3, and it is one script against hundreds of condition-false JSON files.
It also subsumes the Item Obliterator idea.

Where it cannot help is everything above. A script is not a mod, and non-negotiable #3 requires
each subsystem mod to stand alone — so block entities, ticking, sleeping and capability handlers
stay Java.
