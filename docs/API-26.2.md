Minecraft 26.2 API notes
========================

Minecraft 26.x is past the model's training cutoff and renamed a great deal. **Check the real
sources before writing against any API.** Guessing has produced wrong code repeatedly, and
some mistakes fail silently rather than failing to compile.

Where the sources are
---------------------

**Minecraft** — `~/.gradle/caches/neoformruntime/intermediate_results/mergeWithSources_*_output.jar`

Several exist, one per MC version. Identify which is which by reading the sibling
`mergeWithSources_*.txt`, or by mtime against when that version was first built. As of
2026-08-22, `mergeWithSources_0fead73...` is 26.2 and `mergeWithSources_25805a5...` is 26.1.2.

**NeoForge** — `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/*/neoforge-<version>-sources.jar`

**FML / `EventBusSubscriber` is not in the NeoForge sources jar** — it ships separately. For
FML annotations, copy the usage from working sibling-mod source instead.

Extract with `unzip` or `jar xf` and read the class.

Confirmed renames and signature changes
---------------------------------------

| Was | Is |
|---|---|
| `ResourceLocation` | **`Identifier`** |
| `ResourceKey#location()` | `identifier()` |
| `getMinBuildHeight()` | `LevelHeightAccessor.getMinY()` — the old name does not exist |
| `saveAdditional(CompoundTag)` | `BlockEntity.saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` |
| `PacketDistributor` (serverbound) | **`ClientPacketDistributor.sendToServer`** |
| `Screen.hasShiftDown()` | `minecraft.hasShiftDown()` |
| `Minecraft.screen` | `minecraft.gui.screen()` / `gui.setScreen(...)` |
| `IItemHandler` | `Capabilities.Item.BLOCK` → `ResourceHandler<ItemResource>`, driven by `Transaction`. `IItemHandler` is deprecated for removal |
| `Inventory.items` | private — use `getNonEquipmentItems()`; offhand is equipment and fetched apart |
| `Player.displayClientMessage(Component, boolean)` | **gone.** `sendOverlayMessage(Component)` for the action bar, `sendSystemMessage(Component)` for chat |
| `net.minecraft.world.ticks.ScheduledTickAccess` | **`net.minecraft.world.level.ScheduledTickAccess`** — the interface moved, the package `world.ticks` kept the rest |
| `DirectionProperty` | **gone.** `BlockStateProperties.HORIZONTAL_FACING` is an `EnumProperty<Direction>` |

Other confirmed details:

- **Screens** override `extractRenderState` / `extractBackground(GuiGraphicsExtractor, ...)`,
  not `render` / `renderBg`. `imageWidth` / `imageHeight` are final, set via the 5-arg super
  constructor.
- `ItemStack.getBurnTime(RecipeType, FuelValues)` needs `level.fuelValues()`.
- Crafting remainders left `ItemStack`: `stack.getItem().getCraftingRemainder(stack)` returns
  a nullable `ItemStackTemplate`.
- `RecipeSerializer` is a **record**. `Recipe` lost `getResultItem` / `canCraftInDimensions`
  and gained `placementInfo()` / `recipeBookCategory()` / `group()` / `showNotification()`.
  Results are `ItemStackTemplate` (`.create()`). `RecipeManager` is keyed by
  `ResourceKey<Recipe<?>>`.
- `AttachmentType.Builder#serialize` takes a **`MapCodec`**, and `.sync(...)` exists — prefer
  it over a hand-rolled S2C packet.
- `@EventBusSubscriber` has **no `bus` parameter**; the bus is inferred from the event type.
- `KeyMapping` takes a `KeyMapping.Category`, not a string.
- The client cannot enumerate recipes (since 1.21.4). Use `OnDatapackSyncEvent#sendRecipes`
  plus `RecipesReceivedEvent`, not a hand-rolled catalogue.
- `BlockEntityType` has a **public constructor**, `(BlockEntitySupplier, Block...)`. There is no
  `Builder` and no `.build(null)` any more.
- `RecipeManager` lives on the server, not the level: `level.recipeAccess()` is a much smaller
  interface (property sets and stonecutter recipes only). Use
  `serverLevel.getServer().getRecipeManager()` — `byKey(ResourceKey<Recipe<?>>)` for one, and
  `recipeMap().byType(type)` to walk a type.

Inventories: `ResourceHandler`, and the class that already implements it
-----------------------------------------------------------------------

`IItemHandler` is deprecated for removal. The replacement is
`Capabilities.Item.BLOCK` → `ResourceHandler<ItemResource>`, and the important thing is that
**you almost never implement `ResourceHandler` from scratch**:

- **`ItemStacksResourceHandler(int size)`** is a working slot inventory. Override
  `onContentsChanged(index, previousStack)` to hook `setChanged`, `isValid` to filter, and
  `getCapacity` to change stack limits. `set(index, resource, amount)` writes directly.
- It extends `StacksResourceHandler`, which **implements `ValueIOSerializable`** —
  `serialize(ValueOutput)` / `deserialize(ValueInput)` — so saving an inventory is
  `inventory.serialize(output.child("Inventory"))` and
  `input.child("Inventory").ifPresent(inventory::deserialize)`. No `ContainerHelper`.
- `ResourceHandler`'s whole-handler `insert(resource, amount, tx)` and `extract(...)` are
  **default methods that walk every index and call the index-addressed overloads**. A wrapper
  that restricts insertion or extraction per slot therefore only has to override the six
  index-addressed methods; the rest inherits the rule. See
  `nauvis_machines/.../machine/MachineAccess.java`.
- `getCapacityAsLong` must return **0** for anything `isValid` rejects. Hoppers use it to
  decide whether to keep trying.

`Transaction.openRoot()` in a try-with-resources, `commit()` to keep the changes, and falling
out of the block without committing rolls everything back. That is what makes "spend the
ingredients and bank the result, or neither" one method — and passing `commit = false` turns
the same code into a simulation, so there is no second copy that can drift.

Ticking without a ticker
------------------------

Non-negotiable #5 wants an idle machine to cost zero ticks, and a `BlockEntityTicker` cannot do
that — a registered ticker runs every tick whether or not there is work.

Scheduled block ticks can. `level.scheduleTick(pos, block, delay)` queues one visit;
`Block.tick(BlockState, ServerLevel, BlockPos, RandomSource)` receives it; an unscheduled
position is never visited at all. They are saved with the chunk, so work in progress survives a
reload, and `level.getBlockTicks().hasScheduledTick(pos, block)` is both the guard against
queueing twice and — usefully — a **gametest assertion that a machine really is asleep**.

`nauvis_machines/.../assembler/AssemblerBlockEntity.java` is the worked example: it reschedules
itself while a craft is running and simply stops when there is nothing to make, waking from
`onContentsChanged`, from the recipe being set, from `neighborChanged` — and from energy arriving,
which is the one with no obvious hook.

**`SimpleEnergyHandler.onEnergyChanged(int previousAmount)` is that hook.** It is called
immediately for `set()` and at the end of the transaction for `insert`/`extract`, which is exactly
what a machine that stopped for want of power needs: it is scheduled for nothing, so the wake has
to arrive from whatever filled the buffer. `MachinePower` and `InserterPower` are both three-line
subclasses that exist only to carry that callback.

Hearing that a *neighbour's* inventory changed
----------------------------------------------

A machine can sleep perfectly because everything that gives it work touches its own inventory. A
machine that watches a neighbour — an inserter, a hopper, anything that pulls — cannot, and the
obvious answers are a permanent ticker or a poll. Neither survives at Factorio scale.

**`IBlockExtension#onNeighborChange(BlockState, LevelReader, BlockPos pos, BlockPos neighbor)` is
the signal, and it is free.** Every `BlockEntity.setChanged()` calls
`Level.updateNeighbourForOutputSignal`, which NeoForge widened from vanilla's horizontal
comparator check to call `onNeighborChange` on **all six neighbours, unconditionally**. So a
vanilla chest gaining an item, a furnace finishing a smelt and an assembler banking a craft all
reach the block beside them already — exactly, immediately, and with nobody subscribing.

Three things to know:

- `pos` is **you**; `neighbor` is the block entity that changed. Filter on `neighbor` before
  looking anything up: the notification arrives from all six sides and most of them are
  irrelevant. `nauvis_logistics/.../InserterBlock.java` compares it against the two positions an
  inserter can use, which costs two `BlockPos.equals` against a state already in hand and skips
  the block entity lookup entirely.
- The parameter is a `LevelReader`, so cast to `ServerLevel` before scheduling anything.
- It is **not** a substitute for `BlockCapabilityCache`, and the reverse is also true — its own
  javadoc says so. The cache reports the capability being *replaced* (block placed, broken,
  chunk cycled) and holds the handler so a transfer is not a lookup; `onNeighborChange` reports
  the *contents* changing. A puller wants both.

`BlockCapabilityCache.create` also takes an invalidation listener, and it is tempting to wake
from it. Don't: the contract forbids level access inside it, and every case it reports is one
`neighborChanged` already reports from a context where scheduling is safe.

The claim above is asserted, not assumed: `inserter_wakes_when_source_fills` puts an item in a
vanilla chest and checks the inserter is scheduled **in the same tick**. Deleting the
`onNeighborChange` override makes exactly that test fail — and, tellingly, leaves
`inserter_moves_items` passing, because a test that only checks items move never notices that the
wake is broken.

Hearing that *any* block changed, anywhere
------------------------------------------

`onNeighborChange` reaches one block. A power pole has to notice a machine built **two** blocks
away, in nobody's neighbourhood, in another mod that must not know what a pole is. There is no
"block changed" event in NeoForge by that name; there are two things that work, and the choice
between them is a memory decision rather than a correctness one.

**`BlockEvent.NeighborNotifyEvent` is the level-wide signal.** `ServerLevel.updateNeighborsAt`
fires it (verified in the patched sources, `ServerLevel:1201` and `:1215`), and `Level.setBlock`
reaches that for any block placed or broken by any means — player, piston, machine, worldgen. It
is cancellable, which makes it look like a redstone hook, but subscribing without cancelling is
fine. It fires *very* often, so a subscriber needs a one-lookup pre-filter; `PowerNetworkManager`
keeps a map of which poles reach into which chunk and rejects on that.

**`ServerLevel.registerCapabilityListener(pos, listener)` is the exact alternative.** It fires
when `invalidateCapabilities(pos)` is called there, and — the useful part —
**`BlockEntity.clearRemoved()` calls it, so a block entity *appearing* at a position invalidates
that position.** `setRemoved()` does the same on the way out, and `ChunkEvent.Load`/`Unload`
invalidate a whole chunk. So a listener per position is exact and needs no filter. What it costs is
one weak reference per watched position: 125 per pole for a 5×5×5 supply area, which is over a
million in a base of ten thousand poles. That is why the grid uses the event instead.

Two details that matter either way:

- **`Level.getCapability` on an unloaded position loads the chunk**, because `getBlockState` does.
  Guard with `level.isLoaded(pos)`. This is not an optimisation — without it, anything that scans a
  radius drags in the world around it.
- `ChunkEvent.Load`'s own javadoc forbids touching the level from inside it, on pain of deadlock.
  Record the chunk position and act on the next tick.

`LevelTickEvent.Post` vs `ServerTickEvent.Post`
-----------------------------------------------

Both have `Pre` and `Post` subclasses. `LevelTickEvent` hands you the level, which is what a
per-level manager wants — but it **also fires for client levels**, so `instanceof ServerLevel` is
not optional. `Post` rather than `Pre` so a generator that made energy this tick can spend it this
tick rather than next.

`BlockEntity.onLoad()` is deferred by one tick. It is called from `Level.tickBlockEntities` for
everything in `freshBlockEntities`, not from `LevelChunk.addAndRegisterBlockEntity` — so a block
entity placed on tick N registers itself during tick N+1, before that tick's `Post`. Gametests that
place a block and then inspect derived state have to wait at least one tick.

GameTest is registry-driven now, and needs a structure
------------------------------------------------------

Every tutorial shows `@GameTest` on a static method. **That is the old API.** On 26.2 a test is
a `GameTestInstance` in `Registries.TEST_INSTANCE`, registered through NeoForge's
`RegisterGameTestsEvent` on the mod bus, plus a structure to run in. `TestData` carries
`environment`, `structure`, `max_ticks`, `setup_ticks`, `rotation`, `padding`, `sky_access`,
`max_attempts` and `required_successes`.

`nauvis_machines/src/main/java/com/jaguarm/nauvismachines/NauvisMachinesGameTests.java` is a
working example of all of the below, and `nauvis/.../NauvisGameTests.java` is a smaller one.

**`FunctionGameTestInstance` is not available to mods**, whatever the vanilla code suggests by
using it for `minecraft:always_pass`. Its bodies live in
`Registries.TEST_FUNCTION`, which `BuiltInRegistries` bootstraps through
`BuiltinTestFunctions::bootstrap` during *static initialisation* — that calls
`TestFunctionLoader.runLoaders` once, long before any mod constructor runs, and NeoForge adds
no hook. Registering a loader from a mod produces `Trying to access missing test function`
at run time, having compiled perfectly.

**Subclass `GameTestInstance` instead.** Three abstract members: `run(GameTestHelper)`,
`codec()`, and `typeDescription()`. The codec must be registered into
`Registries.TEST_INSTANCE_TYPE` with a `DeferredRegister` even when tests are registered in
code and never serialised — the type has to exist for the instance to be legal. Note
`RecordCodecBuilder.mapCodec` needs an explicit type witness and a typed lambda parameter here,
or inference fails on `O`.

**`structure` is mandatory.** `TestInstanceBlockEntity.placeStructure()` resolves it through
`level.getStructureManager().get(...)` and simply `return false` when missing — the test does
not run and nothing says why. Minecraft ships `data/minecraft/structure/empty.nbt`, so
`Identifier.withDefaultNamespace("empty")` covers any test needing no terrain. For one that
needs a floor, ship an NBT at `data/<ns>/structure/<name>.nbt` and put the floor in it —
`clearSpaceForStructure` empties the volume first. Structure `DataVersion` is **4903**
(`SharedConstants.WORLD_VERSION`).

`RegisterGameTestsEvent` exposes no getter for an existing environment, so register your own:
`event.registerEnvironment(id, new TestEnvironmentDefinition.AllOf(List.of()))` imposes no
conditions, which is what `minecraft:default` is.

`TestFunctionLoader` is an abstract class, not a functional interface — it cannot be a lambda.

Datagen is two runs, and they delete each other's work
------------------------------------------------------

Client and server datagen are separate since 1.21.4 — `clientData()` and `serverData()` in
ModDevGradle, not one `data()`. Models, blockstates and language come out of the first; loot
tables and tags out of the second. Running only one silently generates half of what was asked
for.

**Point them at different output directories.** Each run deletes output it does not recognise,
so with a shared `--output` whichever ran last wipes the other's files — and it looks exactly
like a provider that failed to register. `src/generated/client` and `src/generated/server`, both
added as resource `srcDir`s, avoids it.

If a generated file has a hand-written twin, `processResources` fails with *"Entry ... is a
duplicate but no duplicate handling strategy has been set"*. Delete the hand-written one;
datagen owns it now.

Silent failures — these compile and then do nothing
---------------------------------------------------

- **A machine spills its inventory from `BlockEntity#preRemoveSideEffects(BlockPos,
  BlockState)`**, not from `Block#affectNeighborsAfterRemoval`. The base implementation drops
  contents *only for a `Container`*, so a capability inventory — a `ResourceHandler` — that
  does not override this eats everything in it on every break. Overriding
  `affectNeighborsAfterRemoval` instead compiles, reads correctly, and drops nothing, because
  the block entity is already gone by then. Note `MinerBlock` in Neo Progressive Automation has
  exactly that override and only works because its entity is a `WorldlyContainer`; do not copy
  it. `assembler_spills_when_broken` is the gametest that catches this.
- **A built-in datapack needs a `pack.mcmeta`.** `AddPackFindersEvent#addPackFinders` pointed at
  a resource directory without one fails with a bare
  `NullPointerException: ... because "pack" is null` from `PackRepository.discoverAvailable`,
  naming neither the mod nor the directory. See `nauvis_logistics/src/main/resources/crafting_table/`.
- **`data/<ns>/recipe/` and `data/<ns>/loot_table/` are singular.** Plural folder names do not
  error; the block just silently drops nothing.
- **Recipes use `result.id`**, not `result.item`.
- **Flat item icons need both** `assets/<ns>/items/<name>.json` and `models/item/<name>.json`.

Gradle
------

The NeoForged maven mirror intermittently serves **zero-byte files with HTTP 200** for
third-party libraries. Symptoms: `Content is not allowed in prolog` (an empty `.pom` parsed as
XML) or `zip file is empty` / `zip END header not found` (an empty `.jar`). The giveaway is a
cache directory named `da39a3ee5e6b4b0d3255bfef95601890afd80709` — the SHA-1 of the empty
string.

Fix: add the group to the `exclusiveContent { forRepository { mavenCentral() } }` block so
Central serves it exclusively, delete the offending directory under
`~/.gradle/caches/modules-2/files-2.1`, and rebuild with `--refresh-dependencies`.
`../NeoProgressiveAutomation/build.gradle` carries a working example.

Toolchain: **Java 25**. Mojang ships 25 to end users. NeoForge 26.2.0.59, ModDevGradle 2.0.143.
