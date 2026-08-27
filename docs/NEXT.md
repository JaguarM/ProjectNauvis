Next session
============

Written 2026-08-27 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

**Forty-seven gametests pass and the pack builds clean.** Milestone 1 is closed, steam runs through
pipes, and the machines say what they are doing. What is weakest now is how all of it *looks*.

The job: how this pack makes things look, decided properly
----------------------------------------------------------

**This section is a suggestion, not a specification.** Yannic asked for two concrete things — a
model checker, then a models pass — and both are below. But the thing actually worth producing is a
**process for visual work that survives past this session**, and the plan below is one guess at it
by somebody who could not see the game. If a better shape becomes obvious once you are inside it,
take it and rewrite this file to say what you chose.

The reason the process matters more than the models: three separate visual failures shipped in one
day, and **not one of them failed a compile, a test, or a datagen run.** They were found by a human
looking at the game, or by a client boot that happened for another reason. Any process that ends
with "and then look at it" will keep letting them through.

### First, a net: assert models and textures exist

`tools/check_gui_layout.py` reads screen geometry back out of the source and checks the boxes do
not overlap. There is no equivalent for models, and there should be — the same script shape,
walking every registered block and item:

- does the blockstate name a model file that exists, for every variant and every multipart case?
- does that model, and every parent it inherits from, exist?
- does every `#texture` reference resolve to a real PNG, vanilla ones included?
- does every registered fluid have a `FluidModel`?

This would have caught the steam engine's checkerboard in a second. **And the surface for that class
of bug just grew a lot**: the pipe now has three hand-written models plus a six-way multipart
blockstate, and the pole has three more built from raw JSON. Those are written by hand, from a
`Supplier<JsonElement>`, with nothing checking them.

Whether it belongs in Python beside `check_gui_layout.py`, or as a gametest that walks
`BuiltInRegistries.BLOCK` inside a running server, is an open question worth ten minutes of thought
— the gametest version can ask the real model manager what resolved, which is stronger, but only
the client has one. Do not assume the Python answer just because the precedent is Python.

### Then the models themselves

The visible complaints, in Yannic's words: *you can't see where they point*, and *they should be
more impressive*. What is directional today and reads poorly: both inserters, which are a furnace
cube with a front face. What has no facing at all and probably should: the boiler and the assembler.
The steam engine got a horizontal column this session so its two steam ends are visible — that is
the bar to clear, and it is a low bar.

Every model currently points at **vanilla** textures on purpose: a model naming a texture the mod
does not ship renders as the magenta checkerboard, which reads as broken rather than as unfinished.
Any move to first-party textures has to land art and models together.

How to work on this, from Yannic
--------------------------------

> If I can give a design session just a few files and reference mods it's faster and more accurate.

Take that seriously — it is a statement about how to get good work out of a session, and it cuts
against the instinct to read the whole repo first. **Open the few files that matter, and one
reference, and start.** The list below is what "the few files" means for visual work, so a fresh
session does not have to go looking for them.

### The files a model session actually needs

| | |
|---|---|
| `nauvis_power/.../data/NauvisPowerModels.java` | boiler, engine, pole. Has both idioms: `ModelTemplate` and raw JSON |
| `nauvis_fluids/.../data/NauvisFluidsModels.java` | the pipe: raw JSON plus a six-way multipart, the newest and least proven |
| `nauvis_logistics/.../data/NauvisLogisticsModels.java` | the two inserters — the ones that read worst |
| `nauvis_machines/.../data/NauvisMachinesModels.java` | the assembler |
| `nauvis_power/.../grid/PolePart.java` | **the pattern worth copying**: one list of boxes, read by both the `VoxelShape` and the model, so what you see and what you hit cannot drift |
| `tools/check_gui_layout.py` | the shape a checker takes here — constants read back out of source, no game required |

### The references, and what each is good for

- **`../NeoProgressiveAutomation/texture-workshop/`** — ours, and the best writing in these repos on
  *why* vanilla textures look the way they do: six colours for cobblestone, three ideas for a
  furnace face, never pure black. `make_miner_textures.py` renders sixteen textures from three 16x16
  ASCII maps and a five-tone palette per tier, with Pillow. Editing one map moves every tier
  together, so a family cannot drift apart. **Start here** — it is the closest thing to an existing
  process, and it is already ours to extend.
- **`reference/ImmersiveEngineering-src`** — the best model work on the reference shelf, and the
  source of the pole's shape. Read and reimplement, credit in the commit; never copy assets.
- **`reference/create-src`** — code MIT with attribution, **assets All Rights Reserved**. Read for
  architecture only.
- **Vanilla** — `net.minecraft.client.data.models.model.ModelTemplates` is the list of parents you
  get for free, and `BlockModelGenerators` has the rotation dispatches (`ROTATION_HORIZONTAL_FACING`
  and friends). Worth a skim before hand-writing geometry.

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
