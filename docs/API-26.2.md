Minecraft 26.2 API notes
========================

26.x postdates training. Check the sources before writing against any API; some mistakes fail
silently.

Where the sources are
---------------------

- Minecraft: `~/.gradle/caches/neoformruntime/intermediate_results/mergeWithSources_*_output.jar`,
  one per version; `mergeWithSources_0fead73...` is 26.2. `unzip -p <jar> net/minecraft/.../X.java`.
- NeoForge: `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/*/neoforge-<version>-sources.jar`.
  FML's annotations (`EventBusSubscriber`) ship separately; copy usage from working code.
- Toolchain: Java 25, NeoForge 26.2.0.59, ModDevGradle 2.0.143.

Renames
-------

| Was | Is |
|---|---|
| `ResourceLocation` | `Identifier` (`fromNamespaceAndPath`, `withDefaultNamespace`, `parse`) |
| `ResourceKey#location()` | `identifier()` |
| `getMinBuildHeight()` | `LevelHeightAccessor.getMinY()` |
| `saveAdditional(CompoundTag)` | `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` |
| `PacketDistributor` (serverbound) | `ClientPacketDistributor.sendToServer` |
| `Screen.hasShiftDown()` | `minecraft.hasShiftDown()` |
| `Minecraft.screen` | `minecraft.gui.screen()` / `gui.setScreen(...)` |
| `IItemHandler` | `Capabilities.Item.BLOCK` → `ResourceHandler<ItemResource>` with `Transaction` |
| `Inventory.items` | `getNonEquipmentItems()`; offhand is equipment |
| `Player.displayClientMessage` | `sendOverlayMessage` (action bar), `sendSystemMessage` (chat) |
| `world.ticks.ScheduledTickAccess` | `world.level.ScheduledTickAccess` |
| `DirectionProperty` | gone; `BlockStateProperties.HORIZONTAL_FACING` is `EnumProperty<Direction>` |
| `Level.isClientSide` field | `level.isClientSide()` |
| `Blocks.YELLOW_TERRACOTTA` etc. | `Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)`; wool, concrete, glass alike. `Blocks.COPPER_BLOCK` is a `WeatheringCopperCollection`; name its texture as an `Identifier` |
| `new ChunkPos(BlockPos)` | `ChunkPos.containing(pos)`; a record with `x()`, `z()`, `pack()`, `unpack(long)` |
| `hasPermission(2)` | `requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`; `LEVEL_*` are `PermissionCheck`s |
| datapack registry command args | `ResourceKeyArgument.key(REGISTRY)` / `getRegistryKey`; parses any id, reject unknown ones yourself |
| `TextureMapping#put(slot, ResourceLocation)` | takes `client.resources.model.sprite.Material`; `new Material(Identifier...)` for your own |
| `EntityType.ZOMBIE` | `EntityTypes.ZOMBIE`; `BlockEntityTypes` alike |
| `monster.Husk` | `monster.zombie.Husk`, `monster.skeleton.Skeleton`; `Creeper`, `Monster` stayed |
| `Snowball` | `entity.projectile.throwableitemprojectile.*` |
| `Level.random` | `level.getRandom()` |
| `Entity.moveTo(x, y, z, yRot, xRot)` | `snapTo(...)` |
| `IMenuTypeExtension` | `neoforge.common.extensions.IMenuTypeExtension` |
| `Level.getDayTime` / `setDayTime` | world clocks: `server.clockManager().moveToTimeMarker(level.dimensionTypeRegistration().value().defaultClock(), ClockTimeMarkers.NOON)` |
| `DeferredRegister.Items.registerSimpleItem(String, Properties)` | takes a `Supplier`/`UnaryOperator` of properties; a bare `Properties` is a compile error |

Facts
-----

- Screens override `extractRenderState` / `extractBackground(GuiGraphicsExtractor, ...)`;
  `imageWidth`/`imageHeight` are final, set via the 5-arg super. `mouseClicked` takes
  `(MouseButtonEvent, boolean)`; the event has `x()`, `y()`.
- `ItemStack.getBurnTime(RecipeType, FuelValues)` with `level.fuelValues()`. Fuel is a data map:
  `data/neoforge/data_maps/item/furnace_fuels.json`, `{"values": {"<item>": {"burn_time": n}}}`.
- `Recipe` lost `getResultItem`/`canCraftInDimensions`; results are `ItemStackTemplate`
  (`.create()`); `RecipeSerializer` is a record; `RecipeManager` lives on the server
  (`serverLevel.getServer().getRecipeManager()`, `byKey`, `recipeMap().byType`). Clients cannot
  enumerate recipes: `OnDatapackSyncEvent#sendRecipes` plus `RecipesReceivedEvent`.
- `AttachmentType.Builder#serialize` takes a `MapCodec` and `.sync(...)` exists.
- `@EventBusSubscriber` has no `bus` parameter. `KeyMapping` takes a `KeyMapping.Category`.
- `BlockEntityType` has a public constructor `(BlockEntitySupplier, Block...)`; no builder.
- `BaseEntityBlock` no longer forces `RenderShape.INVISIBLE`.
- `Block.getLootTable()` is `Optional`; `noLootTable()` makes it empty and datagen skips it.
- `BlockPlaceContext(Level, @Nullable Player, InteractionHand, ItemStack, BlockHitResult)`; a
  null player answers north. `BlockItem.updatePlacementContext` moves where an item places;
  `BlockPlaceContext.at(context, pos, face)` builds the moved one. A block in hand raycasts with
  `ClipContext.Fluid.NONE`.
- `new ItemStackTemplate(Items.AIR)` throws; use `Optional<ItemStackTemplate>` and
  `ByteBufCodecs.optional`. `ItemStackTemplate` is `(Holder<Item>, int, DataComponentPatch)`;
  `MAP_CODEC` caps at 99 and `create()` refuses over the stack size; `ItemStack(Holder, int,
  DataComponentPatch)` builds any count; `ItemResource.CODEC` is item and components, no count;
  `Codec.withAlternative` reads either form; `ExtraCodecs.optionalEmptyMap` encodes empty as `{}`.
- Stack size is `DataComponents.MAX_STACK_SIZE` via `stacksTo(int)`; its codec, `ItemStack.MAP_CODEC`
  and `ItemStackTemplate.MAP_CODEC` are the only callers of `ExtraCodecs.intRange(1, 99)`, each
  inside a lambda; the stream codec is an uncapped VarInt. `Container.getMaxStackSize()` defaults
  99; `ItemStacksResourceHandler.getCapacity` is `min(stack size, ABSOLUTE_MAX_STACK_SIZE)`.
  `ModifyDefaultComponentsEvent.modify(item, (components, context, item) -> ...)` changes defaults.
- `SizedFluidIngredient` is `{"ingredient": "<fluid>", "amount": n}`; a fluid result is a
  `FluidStackTemplate` `{"id", "amount"}`. Not `FluidStack.CODEC`: it validates bound components,
  which happen after the reload.
- `RegisterCapabilitiesEvent.registerBlock` may be called more than once per capability and
  block; providers are asked in turn.
- `Registry.getValue(Identifier)` (nullable) / `getOptional`; `getId`/`byId` numeric ids are
  synced in the server's order.
- A nested `protected record`'s canonical constructor is protected; make it `public` for subclasses.
- `GameTestHelper.onEachTick(Runnable)`; `makeMockServerPlayer(GameType)` is not in the player
  list but has `getAdvancements()`.
- Menu buttons: `AbstractContainerMenu.clickMenuButton(Player, int)` on the server,
  `minecraft.gameMode.handleInventoryButtonClick(containerId, id)` on the client. No payload.
- Titles: `ClientboundSetTitleTextPacket`, `ClientboundSetSubtitleTextPacket`,
  `ClientboundSetTitlesAnimationPacket` via `player.connection.send`;
  `PlayerList.broadcastSystemMessage(Component, overlay)`.
- `ServerLevel.sendParticles(particle, x, y, z, count, dx, dy, dz, speed)`; a firework is
  `new FireworkRocketEntity(level, x, y, z, stack)` with `DataComponents.FIREWORKS` set to
  `new Fireworks(flight, List.of(new FireworkExplosion(shape, colors, fadeColors, trail, twinkle)))`.
- Mob AI: `goalSelector`/`targetSelector` are public; `Goal` is `canUse`, `canContinueToUse`,
  `start`, `stop`, `tick`, `requiresUpdateEveryTick`, `setFlags`; a goal ticks every other tick
  unless it says otherwise. Zombie melee is priority 3, stroll 7. `PathNavigation.moveTo` returns a
  partial path or nothing while the mob is not on the ground (re-path on a timer);
  `MoveControl.setWantedPosition` walks straight at a point. `Level.destroyBlockProgress(id, pos,
  0..9|-1)` draws cracks; `CommonHooks.canEntityDestroy` is the griefing rule.
  `NearestAttackableTargetGoal(mob, Player.class, mustSee, (target, level) -> ...)`.
- `Level.getSkyDarken()`: 0 at noon to 11 at midnight, rain included; in a `GameTestServer` world
  it stays 0. `canSeeSky` lags `setBlock` a tick or two.
- `Mth.getSeed(Vec3i)` is the per-position hash for a deterministic `RandomSource`.

Inventories
-----------

`ItemStacksResourceHandler(int size)` is a working slot inventory: override `onContentsChanged`
to hook `setChanged`, `isValid` to filter, `getCapacity` for limits; `set(index, resource,
amount)` writes. It implements `ValueIOSerializable`: `inventory.serialize(output.child("Inventory"))`
and `input.child("Inventory").ifPresent(inventory::deserialize)`. Whole-handler `insert`/`extract`
are defaults walking every index, so a restricting wrapper overrides the six index-addressed
methods (`nauvis_lib/.../transfer/MachineAccess`). `getCapacityAsLong` must return 0 for anything
`isValid` rejects. `Transaction.openRoot()` in try-with-resources, `commit()` to keep.

Ticking without a ticker
------------------------

`level.scheduleTick(pos, block, delay)` queues one visit of `Block.tick(state, level, pos,
random)`; saved with the chunk; `level.getBlockTicks().hasScheduledTick(pos, block)` guards
double-queueing and is the sleep assertion. `AssemblerBlockEntity` is the worked example.
`SimpleEnergyHandler.onEnergyChanged(int previous)` is the wake for energy arriving, called at
once for `set()` and at the end of the transaction for `insert`/`extract`; `nauvis_lib`'s
`MachinePower` carries it. `BlockEntity.onLoad()` runs from the next `tickBlockEntities`, not
from placement; a gametest that places then inspects waits a tick.

Hearing neighbours and the level
--------------------------------

- `IBlockExtension#onNeighborChange(state, level, pos, neighbor)`: every `BlockEntity.setChanged()`
  reaches all six neighbours through `Level.updateNeighbourForOutputSignal`, which NeoForge
  widened. `pos` is you, `neighbor` the one that changed; filter on `neighbor` first; cast the
  `LevelReader` to `ServerLevel` before scheduling. Not a substitute for `BlockCapabilityCache`
  (which reports the capability being replaced and holds the handler); a puller wants both. Do
  not wake from the cache's invalidation listener (no level access allowed there).
- To send the signal without dirtying a chunk: `level.updateNeighbourForOutputSignal(pos,
  block)`, only on a real transition (`BeltRun.announceArrivals`).
- Level-wide: `BlockEvent.NeighborNotifyEvent` fires from `ServerLevel.updateNeighborsAt` for any
  block placed or broken by any means; pre-filter with one lookup. The exact alternative,
  `ServerLevel.registerCapabilityListener(pos, listener)` (fired by `invalidateCapabilities`,
  which `BlockEntity.clearRemoved()`/`setRemoved()` and chunk load/unload call), costs a weak
  reference per position.
- `Level.getCapability` on an unloaded position loads the chunk: guard with `level.isLoaded`.
  `ChunkEvent.Load` forbids touching the level inside it; record and act next tick.
- `LevelTickEvent.Post` fires for client levels too (`instanceof ServerLevel`); `Post` so a
  generator's energy is spent the same tick.

Moving what stands on a block
-----------------------------

`Block#stepOn(level, pos, state, entity)` is called from `applyEffectsFromBlocks` for
`getOnPosLegacy()` every tick the entity is on the ground, for players, items, orbs, falling
blocks, TNT and arrows; on a client only for its own player. Push by `entity.move(MoverType.SELF,
...)`, not velocity (friction decays it); it is called after `travel` so it is not re-entrant.
`entityInside` never fires for something standing on a block under a full cube. See the belt
pitfalls for `maxUpStep`, `onGround` and `collisionExtendsVertically`.

Rendering
---------

- A `BlockEntityRenderer` is `extractRenderState(be, state, partialTicks, cameraPos,
  breakProgress)` (reads the world into a reusable state) and `submit(state, poseStack, collector,
  camera)` (may not touch the level); register with
  `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer`. Override
  `getRenderBoundingBox` for geometry outside the block and `shouldRenderOffScreen` to be visited
  when the section is culled (`PoleWireRenderer`, `pole_wire_bounds_reach_both_ends`).
- `RenderTypes` (factories) and `rendertype.RenderType`; `submitCustomGeometry(poseStack,
  renderType, (pose, buffer) -> ...)` for arbitrary quads, `BeaconRenderer` for the vertex calls;
  `RenderTypes.entityCutout` does not cull.
- Items from a renderer: `context.itemModelResolver()`, then
  `resolver.updateForTopItem(state, stack, ItemDisplayContext.GROUND, level, null, seed)` and
  `state.submit(poseStack, collector, light, overlay, outline)`; states can be pooled
  (`BeltRenderState`).
- Through walls: `RenderPipeline.builder(RenderPipelines.LINES_SNIPPET).withLocation(Identifier)
  .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false)).build()` registered
  in `RegisterRenderPipelinesEvent`, wrapped with `RenderType.create(name,
  RenderSetup.builder(pipeline).setLayeringTransform(VIEW_OFFSET_Z_LAYERING)
  .setOutputTarget(ITEM_ENTITY_TARGET).createRenderSetup())`;
  `submitShapeOutline(poseStack, shape, renderType, argb, width, afterTerrain)`. `CrudeOilRenderer`.
- Without a block entity: `ExtractLevelRenderStateEvent` (once a frame, level readable) and
  `SubmitCustomGeometryEvent` (collector and a `PoseStack` at the camera; translate by
  `pos - levelRenderState.cameraRenderState.pos`). `MachineGhost`.
- A `FluidModel.Unbaked(still, flowing, overlay, FluidTintSources.water())` per fluid, registered
  in `RegisterFluidModelsEvent` (the two-fluid overload); layer is chosen from the sprites.

Gametests
---------

A test is a `GameTestInstance` in `Registries.TEST_INSTANCE` (`RegisterGameTestsEvent`) plus a
`MapCodec` type in `Registries.TEST_INSTANCE_TYPE`; `nauvis_lib`'s `GameTests` does both.
`@GameTest` on a static method and `FunctionGameTestInstance` are unavailable to mods. `structure`
is mandatory (`minecraft:empty` is a point; a missing one silently does not run). Structure
`DataVersion` is 4903. `TestData` carries `environment, structure, maxTicks, setupTicks,
required, rotation, manualOnly, maxAttempts, requiredSuccesses, skyAccess, padding`. A test helper
named `run` on the enclosing class is shadowed by `GameTestInstance.run`.
`ChunkMap.FORCED_TICKET_LEVEL` is entity-ticking; `helper.getLevel().setChunkForced(x, z, true)`
makes a chunk tick mobs; the runner unforces at batch end.

Worldgen
--------

A `Feature<FC>` takes its config codec and overrides `place(FeaturePlaceContext)`; registered on
`Registries.FEATURE`; `NoneFeatureConfiguration.CODEC` means `"config": {}`. Files:
`worldgen/configured_feature/<name>.json`, `worldgen/placed_feature/<name>.json` (`placement`
list: `rarity_filter`, `in_square`, `heightmap`, `count`, `height_range`, `biome`), and
`neoforge/biome_modifier/<name>.json` (`neoforge:add_features`, a `step` from
`GenerationStep.Decoration`; `top_layer_modification` runs last). A superflat world's default
preset has `features: false`. `WorldGenLevel.getHeight(Heightmap.Types, x, z)` primes a missing
heightmap; `MOTION_BLOCKING` stops at leaves and water. `"placement": []` lands once per chunk at
its origin; `context.level().getChunk(x, z)` is the generating chunk;
`LevelChunkSection.maybeHas(predicate)` is a palette check and `section.setBlockState(x, y, z,
state)` writes without heightmaps or light; `postProcessGeneration` ticks marked fluids.

A fluid of your own
-------------------

`BaseFlowingFluid.Source`/`.Flowing` over `BaseFlowingFluid.Properties(type, still, flowing)`
with `.bucket(...)` and `.block(...)` suppliers; fluids register before blocks. Source conversion
is `FluidType.Properties.canConvertToSource`; copy `NeoForgeMod.WATER_TYPE`'s properties;
`isWaterLike(true)` is what drowns and darkens. `canBeReplacedWith`, `animateTick`,
`getDripParticle` and `entityInside` (`InsideBlockEffectType.EXTINGUISH`) are `NaturalWaterFluid`'s.
Tags: `FluidTagsProvider(output, lookup, modId)`. What asks for `Blocks.WATER`/`Fluids.WATER` by
name and so ignores a tagged fluid: every water animal's spawn rule (answer with
`RegisterSpawnPlacementsEvent.register(type, predicate)`, which ORs with vanilla's), kelp, seagrass
and sea pickle growth, bone meal on water, the bobber's splash, `IceBlock.meltsInto`, frost
walker, `Biome.shouldFreeze`, and every waterloggable block.

Chunk tickets
-------------

`TicketController(Identifier, LoadingValidationCallback)` registered from
`RegisterTicketControllersEvent`; an unregistered controller's tickets are dropped on load.
`controller.forceChunk(level, BlockPos owner, x, z, add, forceNaturalSpawning)`; tickets are
saved; the callback's `TicketHelper` has `getBlockTickets()` and `removeAllTickets(owner)`;
`ForcedChunkManager.hasForcedChunks(level)` is the query. `RadarChunks`.

Mixins
------

NeoForge 26.2 runs sponge-mixin 0.17.3 with MixinExtras on official names: no refmap, no
annotation processor; a config JSON in resources, `[[mixins]] config="<mod_id>.mixins.json"` in
`neoforge.mods.toml`, `"compatibilityLevel": "JAVA_25"`. A call inside a lambda is in a synthetic
method, so catch the callee instead (`@Inject` at `HEAD` of `ExtraCodecs.intRange(II)`,
cancellable). `@ModifyArg` with `index` may take every argument of the call. `@Shadow @Final` for
a private final field. An interface mixin may add default methods but not `@Inject`; a class mixin
may add a method overriding an inherited interface default with no annotation. Handler prefix
`modid$name`.

Datagen and data layout
-----------------------

Client and server datagen are separate runs (`clientData()`, `serverData()`); each deletes output
it does not recognise, so they write to `src/generated/client` and `src/generated/server`. A
generated file with a hand-written twin fails `processResources` as a duplicate; delete the
hand-written one. `data/<ns>/recipe/` and `loot_table/` are singular. Recipes use `result.id`.
A flat item icon needs both `assets/<ns>/items/<name>.json` and `models/item/<name>.json`. A
texture animates from a vertical strip plus a `.mcmeta` whose `frames` may repeat frames in any
order (`make_belt_textures.py`).

Gradle
------

The NeoForged mirror intermittently serves zero-byte files with HTTP 200 (`Content is not allowed
in prolog`, `zip file is empty`; a cache directory named `da39a3ee...`). The `exclusiveContent`
block in every `build.gradle` makes Central the only source for the affected groups; delete the
directory under `~/.gradle/caches/modules-2/files-2.1` and rebuild with `--refresh-dependencies`.
