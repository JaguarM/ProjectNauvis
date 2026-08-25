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

Every tutorial shows `@GameTest` on a static method. **That is the old API.** On 26.2 a test
is two registry entries and a structure file:

1. The body is a `Consumer<GameTestHelper>` in `Registries.TEST_FUNCTION`, registered through
   `TestFunctionLoader.registerLoader(...)`.
2. The test itself is a `GameTestInstance` — `FunctionGameTestInstance` points at the function
   key — in `Registries.TEST_INSTANCE`. NeoForge exposes both through
   `RegisterGameTestsEvent` on the mod bus, which also registers `TestEnvironmentDefinition`s.
3. `TestData` carries `environment`, `structure`, `max_ticks`, `setup_ticks`, `rotation`,
   `padding`, `sky_access`, `max_attempts`, `required_successes`.

**`structure` is mandatory and there is no empty default.**
`TestInstanceBlockEntity.placeStructure()` resolves it through
`level.getStructureManager().get(...)` and simply `return false` when it is missing — the test
does not run and nothing says why. Ship an NBT at `data/<ns>/structure/<name>.nbt`; a floor
the test can stand on has to come from that file, because `clearSpaceForStructure` clears the
volume first. The current structure `DataVersion` is **4903** (`SharedConstants.WORLD_VERSION`).

`GameTestEnvironments.DEFAULT_KEY` is `minecraft:default`, an `AllOf(List.of())` — use it
rather than registering an environment for a test that needs no special conditions.

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
