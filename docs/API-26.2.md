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

GameTest is registry-driven now, and needs a structure
------------------------------------------------------

Every tutorial shows `@GameTest` on a static method. **That is the old API.** On 26.2 a test is
a `GameTestInstance` in `Registries.TEST_INSTANCE`, registered through NeoForge's
`RegisterGameTestsEvent` on the mod bus, plus a structure to run in. `TestData` carries
`environment`, `structure`, `max_ticks`, `setup_ticks`, `rotation`, `padding`, `sky_access`,
`max_attempts` and `required_successes`.

`nauvis/src/main/java/com/jaguarm/nauvis/NauvisGameTests.java` is a working example of all of
the below.

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
