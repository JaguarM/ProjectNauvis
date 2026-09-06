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
| `Level.isClientSide` (field) | private — `level.isClientSide()` |
| `Blocks.YELLOW_TERRACOTTA` and every other dyed block | **gone.** They are `ColorCollection`s: `Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)`. Same for wool, concrete, glass and the rest |
| `new ChunkPos(BlockPos)` | **gone.** `ChunkPos` is a record of two ints: `new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)` |
| `Commands` permission levels | **`requires(source -> source.hasPermission(2))` is gone.** It is `requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` — the `LEVEL_*` constants are `PermissionCheck` objects rather than ints, and `CommandSourceStack` has no `hasPermission`. `LEVEL_GAMEMASTERS` is what `/time` and `/gamemode` use |
| Datapack registry command arguments | `ResourceKeyArgument.key(REGISTRY)` parses and suggests one, for a synced datapack registry as well as a built-in; `ResourceKeyArgument.getRegistryKey` gets it back out. It parses **any** identifier, so a key nothing registered reaches the command and has to be rejected there |
| `TextureMapping#put(TextureSlot, ResourceLocation)` | takes a **`Material`** — `net.minecraft.client.resources.model.sprite.Material`, not the `resources.model` one. `TextureMapping.getBlockTexture` returns one; for a texture of your own that no block is named after, `new Material(Identifier.fromNamespaceAndPath(modid, "block/<name>"))` |

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
  `nauvis_lib/.../transfer/MachineAccess.java`.
- `getCapacityAsLong` must return **0** for anything `isValid` rejects. Hoppers use it to
  decide whether to keep trying.

`Transaction.openRoot()` in a try-with-resources, `commit()` to keep the changes, and falling
out of the block without committing rolls everything back. That is what makes "spend the
ingredients and bank the result, or neither" one method — and passing `commit = false` turns
the same code into a simulation, so there is no second copy that can drift.

Moving an entity that is standing on your block
-----------------------------------------------

There is no conveyor in vanilla, so there is no obvious hook, and the obvious wrong one is
`entityInside` — which never fires for something standing *on* a block that is less than a full
cube, because the entity is then inside the block above.

**`Block#stepOn(Level, BlockPos, BlockState, Entity)` is the one.** `Entity.applyEffectsFromBlocks`
calls it for `getOnPosLegacy()` on every tick the entity is on the ground, moving or not — which is
exactly a conveyor's question. It reaches players (`LivingEntity.aiStep`), items, experience orbs,
falling blocks, primed TNT and arrows. On a client it runs only for that client's own player, so
the two sides agree and being carried is not laggy.

Two things to know once you are in there:

- **Push by moving, not by adding to the velocity.** A velocity decays against friction every tick,
  so an entity nudged by *v* is carried at some fraction of *v* rather than at *v*. `stepOn` is
  called from `aiStep` *after* `travel`, so calling `entity.move(MoverType.SELF, ...)` from inside
  it is not re-entrant, and it collides properly.
- **`Entity.move` with no vertical component clears `onGround`.** It only decides what the entity
  is standing on when the movement had a `y`, so a purely horizontal push leaves the entity
  believing it is falling — which stops the *next* tick's `stepOn`, and for a player also breaks
  fall damage and step sounds. Read `onGround()` before and `setOnGround(...)` after.

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
to arrive from whatever filled the buffer. `nauvis_lib`'s `MachinePower` is a three-line
subclass that exists only to carry that callback.

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

### Sending the signal yourself, without dirtying a chunk

The free ride above only works for a source whose items live in a block entity. **A subsystem that
moves items without one — the belt run does — is silent, and everything watching it sleeps through
its work.** `BeltRun.announceArrivals` is the fix and the API is:

```java
level.updateNeighbourForOutputSignal(pos, block);   // public on Level, all six sides
```

That is precisely the half of `setChanged` that carries the news. The other half —
`level.blockEntityChanged(pos)` — marks the chunk unsaved, which is right when something crosses
the boundary between your subsystem and the world and wrong when it is just an item shuffling
forward twenty times a second. Verified against the patched `Level.java`: the Neo comment above the
loop reads *"send update to vertical directions as well, after horizontal ones"*, and it already
checks `hasChunkAt` per neighbour, so it will not drag an unloaded chunk in.

Send it only on a real transition — the belt sends on empty-becomes-occupied, per block — or it is
a poll with extra steps.

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

Rendering an item stack from a block entity renderer
----------------------------------------------------

`BlockEntityRendererProvider.Context#itemModelResolver()` in the constructor, then in
`extractRenderState`: `resolver.updateForTopItem(state, stack, ItemDisplayContext.GROUND, level,
null, seed)` fills an `ItemStackRenderState`, and in `submit` that state's
`submit(poseStack, collector, light, overlay, outline)` draws it. `CampfireRenderer` is the vanilla
example.

`updateForTopItem` calls `clear()` on the state first, so the states can be **pooled** rather than
allocated per item per frame — which vanilla does not bother with for a campfire's four items and
which matters for a belt's eight per block across a whole base. See
`nauvis_logistics/.../client/BeltRenderState.java`.

Block entity renderers: extract, submit, and the box that decides visibility
---------------------------------------------------------------------------

26.2 splits a `BlockEntityRenderer` in two. `extractRenderState(be, state, partialTicks,
cameraPos, breakProgress)` reads the world into a reusable state object; `submit(state, poseStack,
collector, camera)` turns that into geometry and may not touch the level. Register with
`EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer`.

`RenderType` moved to **`net.minecraft.client.renderer.rendertype.RenderTypes`** (static factories)
and `net.minecraft.client.renderer.rendertype.RenderType` (the type). For arbitrary geometry,
`SubmitNodeCollector#submitCustomGeometry(poseStack, renderType, (pose, buffer) -> ...)` hands you a
`VertexConsumer`; `BeaconRenderer` is the worked example of the vertex calls
(`addVertex(pose, x, y, z).setColor(...).setUv(...).setOverlay(...).setLight(...).setNormal(...)`).
`RenderTypes.entityCutout(texture)` is quads and **does not cull**, unlike `entityCutoutCull`.

**A renderer that draws outside its own block must override
`getRenderBoundingBox(T blockEntity)`.** This is the box the frustum test uses
(`BlockEntityRenderDispatcher#tryExtractRenderState`), and it defaults to the *unit cube at the
block entity*. Geometry that reaches further — a beam, a cable, anything spanning to another block
— disappears the moment that one block leaves the screen, while the geometry itself is still in
plain view. There is no exception and nothing in the log; it just stops drawing.

`shouldRenderOffScreen()` is a **different** switch and both are usually needed. It moves the block
entity out of the per-section pass into a level-wide one, and that is the only pass that visits a
block entity whose own chunk section was culled. Both passes then frustum-test against
`getRenderBoundingBox`. The cost is one frustum test per *loaded* such block entity per frame
rather than per visible one.

`nauvis_power/.../client/PoleWireRenderer.java` is the worked example, and
`pole_wire_bounds_reach_both_ends` asserts the box headlessly — the bounds are computed on the
block entity precisely so a gametest can reach them, because the failure mode is invisible geometry
rather than a crash.

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

Note also that a helper named `run` on a `GameTestInstance` subclass's enclosing class will not
resolve from inside the test — `GameTestInstance.run(GameTestHelper)` shadows it, and the error
names the wrong thing.

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

Worldgen: a Feature, and where its JSON goes
--------------------------------------------

A feature is a class and three JSON files. `Feature<FC extends FeatureConfiguration>` takes its
config codec in the constructor and overrides `place(FeaturePlaceContext<FC>)`, which offers
`level()` (a `WorldGenLevel`), `origin()`, `random()`, `config()` and `chunkGenerator()`. Register
it with a `DeferredRegister<Feature<?>>` on `Registries.FEATURE`; `NoneFeatureConfiguration.CODEC`
is a `MapCodec.unitCodec`, so `"config": {}` is the whole configuration.

- `data/<ns>/worldgen/configured_feature/<name>.json` — `{"type": "<ns>:<feature>", "config": {}}`
- `data/<ns>/worldgen/placed_feature/<name>.json` — `feature` plus a `placement` list. Vanilla's
  modifiers are `minecraft:rarity_filter` (`chance`), `in_square`, `heightmap` (`WORLD_SURFACE_WG`
  etc.), `count`, `height_range`, `biome`. Read `data/minecraft/worldgen/placed_feature/` in the
  client jar for the shapes.
- `data/<ns>/neoforge/biome_modifier/<name>.json` — `{"type": "neoforge:add_features", "biomes":
  "#minecraft:is_overworld", "features": "<ns>:<placed>", "step": "top_layer_modification"}`. The
  steps are `GenerationStep.Decoration`'s names; `top_layer_modification` runs after trees and
  grass, `underground_ores` before.

**A superflat world runs no features unless its preset says so.** The default "Classic Flat"
preset - and Bottomless Pit, Redstone Ready, Snowy Kingdom, Water World - has `features: false` in
`data/minecraft/worldgen/flat_level_generator_preset/`, so no biome feature runs there, vanilla's or
a biome modifier's: no trees, no ores, no oil. "Overworld", "Desert", "Tunnelers' Dream" and "The
Void" have it on. A feature that must exist in a test world needs a command as well.

`WorldGenLevel.getHeight(Heightmap.Types, x, z)` primes a missing heightmap on demand, so a `_WG`
type works on a live `ServerLevel` too — with an `Unprimed heightmap` error logged in a dev run.
`MOTION_BLOCKING` is kept live and available to worldgen, and stops at leaves and water, which is
what a feature that wants the earth under a tree has to walk down through.

`Mth.getSeed(Vec3i)` is the per-position hash for a deterministic `RandomSource.create(seed ^ ...)`.

Rendering through walls: a pipeline of your own
------------------------------------------------

Every vanilla line pipeline tests depth — `RenderPipelines.LINES`, `LINES_TRANSLUCENT`,
`SECONDARY_BLOCK_OUTLINE` all carry a `DepthStencilState` with `GREATER_THAN_OR_EQUAL` — so an
outline that must show through terrain needs one built from the snippet:

```java
RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/xray"))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();
```

registered in **`RegisterRenderPipelinesEvent`** (mod bus) so it is compiled with vanilla's, then
wrapped as `RenderType.create(name, RenderSetup.builder(pipeline).setLayeringTransform(
VIEW_OFFSET_Z_LAYERING).setOutputTarget(ITEM_ENTITY_TARGET).createRenderSetup())` — the same setup
`RenderTypes.LINES` uses. `DepthStencilState` and `CompareOp` are `com.mojang.blaze3d.pipeline` and
`.platform`; `withLocation(String)` puts the id in the `minecraft` namespace, so use the
`Identifier` overload.

`SubmitNodeCollector.submitShapeOutline(poseStack, VoxelShape, RenderType, int argb, float width,
boolean afterTerrain)` draws a box outline from a renderer's `submit`, in the block's own frame.
`nauvis_fluids/.../client/CrudeOilRenderer.java` is the worked example, including the
`shouldRenderOffScreen` that a see-through outline also needs — a culled chunk section's block
entities are not visited by the per-section pass, hill or no hill.

Other confirmed details, second batch
-------------------------------------

- **`BaseEntityBlock` no longer forces `RenderShape.INVISIBLE`.** In 26.2 it is `codec()`,
  `triggerEvent`, `getMenuProvider` and `createTickerHelper` and nothing about rendering, so a
  block entity block renders its model with no override. Every machine here relies on it.
- `Block.getLootTable()` is `Optional<ResourceKey<LootTable>>`; `noLootTable()` makes it empty,
  and `BlockLootSubProvider` skips such a block rather than demanding a table for it.
- `BlockState.getDestroySpeed(BlockGetter, BlockPos)` returns the `strength` you gave; `-1` is
  unbreakable. `getPistonPushReaction()` reads the `pushReaction` property.
- `BlockPlaceContext(Level, @Nullable Player, InteractionHand, ItemStack, BlockHitResult)` — a null
  player is allowed and `getHorizontalDirection()` then answers north, which is how a gametest
  asks a block what it would place as.
- **`BlockItem.updatePlacementContext(BlockPlaceContext)`** is the hook for moving where a block
  item places, and `BlockPlaceContext.at(context, pos, face)` builds the moved context; the
  block's `getStateForPlacement` then sees the new `getClickedPos()`. The offshore pump lifts a
  click on a lake bed to the air over the surface with it. A block in hand raycasts with
  `ClipContext.Fluid.NONE`, so a click on water lands on whatever is under the water.
- **Drawing in the world without a block entity** is two events on the game bus:
  `ExtractLevelRenderStateEvent` (once a frame, with the `ClientLevel` and the `Camera`, where the
  world may be read) and `SubmitCustomGeometryEvent` (with the `SubmitNodeCollector` and a
  `PoseStack` at the camera's origin, where it may not). Translate by
  `pos - levelRenderState.cameraRenderState.pos` and `submitShapeOutline` as a renderer would.
  `RenderLevelStageEvent`'s own javadoc points here for anything that goes through the collector.
  `nauvis_lib/.../client/MachineGhost.java` is the worked example, and it builds the click a
  right-click would build - `new BlockPlaceContext(player, hand, stack, hit)` from
  `Minecraft.hitResult`, through the item's `updatePlacementContext` - so the block's own
  `getStateForPlacement` can be asked on the client what it would do.
- **`new ItemStackTemplate(Items.AIR)` throws `Item must be non-empty`**, and from a static
  initialiser that is an `ExceptionInInitializerError` in the middle of registry events, naming
  neither the class nor the field. There is no empty template; a recipe that may make no item
  carries an `Optional<ItemStackTemplate>`, with `ByteBufCodecs.optional` for the stream codec.
- `SizedFluidIngredient` is NeoForge's sized fluid ingredient - `{"ingredient": "<fluid id>",
  "amount": n}` in JSON, a `HolderSet` string form like an item ingredient's - and a fluid result
  is a **`FluidStackTemplate`**, `{"id": ..., "amount": ...}`, with `.create()` for the stack.
  Not `FluidStack.CODEC`: that one validates `areComponentsBound()` on the holder, and
  `ReloadableServerResources` binds components only *after* the reload, so during recipe parsing
  it refuses every fluid in the game with `Fluid x does not have components yet`. Same reason
  vanilla's recipe results are `ItemStackTemplate`. Facrafting's recipe carries both.
- **Fuel is a data map, not code**: `data/neoforge/data_maps/item/furnace_fuels.json` with
  `{"values": {"<item>": {"burn_time": <ticks>}}}`. The folder is the map's namespace, whichever
  mod ships the file. Solid fuel is 4800, three coals, as Factorio's 12 MJ is three of coal's 4.
- `GameTestHelper.onEachTick(Runnable)` runs something every tick of the test, which is how a
  test in one mod stands in for a machine from another: the power mod's boiler tests top the
  water tank up each tick the way a pipe from an offshore pump would, and never name the pump.
- `RegisterCapabilitiesEvent.registerBlock` may be called more than once for one capability and
  one block; the providers are asked in turn until one answers. A boiler offers steam at one face
  and water at two others through two registrations of `Capabilities.Fluid.BLOCK`.

A fluid of your own, and what still asks for vanilla's by name
--------------------------------------------------------------

`BaseFlowingFluid.Source` and `.Flowing` over one `BaseFlowingFluid.Properties(type, still,
flowing)` - `.bucket(...)` and `.block(...)` are suppliers, so the bucket can be vanilla's and the
block can be `new LiquidBlock(fluid, props)` registered later. Registries fire in vanilla's order,
fluids before blocks, so `ModFluids.WATER.get()` is safe inside a block's factory. Neither half
answers "can this make a source" itself: that is `FluidType.Properties.canConvertToSource`, and
NeoForge's own water type is `NeoForgeMod.WATER_TYPE` - copy its properties line for line.
**`isWaterLike(true)` is load-bearing**: `Entity.wasEyeInWater` reads `FluidType.getIsWaterLike`,
so a fluid without it never drowns anybody and never darkens the screen.

Three things a flowing fluid does that the base class does not: `canBeReplacedWith` is "replaced
from above by anything that is not me", where water's is "not `#minecraft:water`" - the difference
is whether a poured bucket bores a hole through a lake; `animateTick` and `getDripParticle` are
water's bubbles and drips; `entityInside` applies `InsideBlockEffectType.EXTINGUISH`.
`nauvis_fluids/.../fluid/NaturalWaterFluid.java` carries all three.

The model is `FluidModel.Unbaked(still, flowing, overlay, FluidTintSources.water())`, registered
for both fluids with the two-fluid overload of `RegisterFluidModelsEvent.register`; the overlay is
`block/water_overlay`, drawn against glass. `FluidStateModelSet` is vanilla's registration to
copy. The layer is chosen from the sprites' transparency, so water's sprites make it translucent
with nothing said.

Tags: `FluidTagsProvider(output, lookup, modId)` and `tag(...).add(ResourceKey...)` -
`DeferredHolder.getKey()`. `#minecraft:water` covers swimming, drowning, boats, fire, farmland,
sugar cane, sponges, guardians, drowned and axolotls; `supports_lily_pad`, `supports_frogspawn`
and `bubble_column_can_occupy` name the source alone.

**What asks for the block by name, and so does not see a fluid of yours in the tag** - verified by
grepping the 26.2 sources for `Blocks.WATER` and `Fluids.WATER`: every water animal's spawn rule
(`WaterAnimal`, `AgeableWaterCreature`, `TropicalFish`, `GlowSquid` and `AbstractNautilus` all
check `getBlockState(pos.above()).is(Blocks.WATER)`); kelp, seagrass and sea pickle growth; bone
meal on water; the fishing bobber's splash particles (the catch itself is by tag);
`IceBlock.meltsInto`; frost walker; `Biome.shouldFreeze`; and **every waterloggable block**, whose
`getStateForPlacement` sets `WATERLOGGED` from `fluidState.is(Fluids.WATER)` and whose
`SimpleWaterloggedBlock.canPlaceLiquid` is `type == Fluids.WATER`. Spawning is answered with
`RegisterSpawnPlacementsEvent.register(type, predicate)` - the two-argument form ORs your predicate
with vanilla's; `EntityTypes.COD` is where `EntityType.COD` went, and
`SpawnPlacements.SpawnPredicate` takes a `ServerLevelAccessor`. The rest is left, and listed in
`GAPS.md`.

Worldgen, in bulk: a placed feature with `"placement": []` lands once per chunk at the chunk's
origin, and `context.level().getChunk(x, z)` hands over the generating `ChunkAccess`.
`LevelChunkSection.maybeHas(predicate)` is a palette check, and
`section.setBlockState(x, y, z, state)` writes without heightmaps or light - right when the swap
changes neither, which two liquids with the same properties do not.
`LevelChunk.postProcessGeneration` ticks every fluid the generator marked, reading the state that
is there by then, so a swapped fluid still flows.

Time is a world clock, and the gametest world's sky ignores it
--------------------------------------------------------------

`Level.getDayTime()` and `ServerLevel.setDayTime()` are gone. Time lives on **world clocks**:
`server.clockManager()` is a `ServerClockManager` with `moveToTimeMarker(Holder<WorldClock>,
ResourceKey<ClockTimeMarker>)`, `setTotalTicks`, `addTicks` and `getTotalTicks`; the overworld's
clock is `level.dimensionTypeRegistration().value().defaultClock()`, and the markers are
`ClockTimeMarkers.NOON`, `MIDNIGHT`, `DAY`, `NIGHT`. `/time set noon` is the marker form.

What a machine reads is `Level.getSkyDarken()`, an int from 0 at noon to 11 at midnight - rain
included - recomputed every server tick from the dimension's `SKY_LIGHT_LEVEL` environment
attribute. **In a `GameTestServer` world it stays at 0 whatever the clock is moved to**: a panel
placed at "midnight" made its full noon output. Test the formula, not the world.

`LevelReader.canSeeSky(BlockPos)` is the light engine's answer and **lags a tick or two behind
`setBlock`**: a roof placed on the same tick as the thing under it shades nothing until the light
settles. Place the roof, wait a few ticks, then place the thing.

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

Data layout that fails silently
-------------------------------

The behavioural ones — the spill hook, the missing `pack.mcmeta`, the item spawned at a corner —
are in `PITFALLS.md`. These are layout:

- **`data/<ns>/recipe/` and `data/<ns>/loot_table/` are singular.** Plural folder names do not
  error; the block just silently drops nothing.
- **Recipes use `result.id`**, not `result.item`.
- **Flat item icons need both** `assets/<ns>/items/<name>.json` and `models/item/<name>.json`.
- **A texture animates from a vertical strip plus a `.mcmeta`**, and the `frames` list may name
  the same frame more than once and in any order. That is how a scroll lands on a speed that is
  not a whole number of pixels a tick: see `texture-workshop/make_belt_textures.py`.

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
The `repositories` block of `nauvis/build.gradle` carries a working example.

Toolchain: **Java 25**. Mojang ships 25 to end users. NeoForge 26.2.0.59, ModDevGradle 2.0.143.
