Silent failures
===============

Each of these compiled, passed the tests and was still wrong. Read before writing.

Models, datagen and rendering
-----------------------------

- Overlapping elements that share a face plane z-fight; a rotated element cannot avoid
  overlapping. Shrink the overlapping piece a tenth of a pixel, never the pieces it overlaps, and
  never wider than the block. Geometry that merely interpenetrates is fine.
- A vanilla sprite is not always an opaque square (`anvil_top` has transparent columns).
  `check_models.py` refuses a transparent texture on a first-party model; `render_model.py` shows
  the hole.
- A template that adds a suffix (`CUBE_COLUMN_HORIZONTAL` writes `block/<name>_horizontal`) leaves
  the item model pointing at `block/<name>`: call `registerSimpleItemModel(block, modelId)`.
- Every fluid needs a `FluidModel` via `RegisterFluidModelsEvent`, even one never placed; the
  only symptom is `Missing FluidModel for fluid` in the log.
- A `VariantMutator` sets `y`, it does not add. A multiblock turns twice (cell and facing);
  dispatch over both properties and add the turns by hand. `check_models.py` checks each variant.
- A renderer drawing outside its block is frustum-culled with no error: override
  `getRenderBoundingBox`, and `shouldRenderOffScreen` if its section may be culled.
- A `minecraft:special` item model keeps its renderer under `model` and its display model under
  `base`; `check_models.py` knows. Relaxing a checker on a false failure hides the real one.

Blocks and multiblocks
----------------------

- `createBlockStateDefinition` runs inside `Block`'s constructor, before any subclass field
  exists. Answer with a constant on a subclass.
- A shape that builds its cells in a loop reads as smaller than it is to `check_models.py`; write
  cells out.
- A multiblock is several grid endpoints and one machine: reduce by identity or it is charged
  several times a tick.
- A capability asked for with a `null` side answers for every side (`MachineShape.hasPort` says
  yes on purpose, for hoppers). Anything resolving a sided capability must pass the side.
- A full sink looks like a source to an insert probe. An extract-only view must answer `isValid`
  false and capacity zero (`FluidOutputAccess`), not delegate to the tank behind it.
- A capability inventory spills from `BlockEntity#preRemoveSideEffects`, not
  `Block#affectNeighborsAfterRemoval` (the block entity is gone by then).
- A modded `Container` must register its own item capability; NeoForge wraps only vanilla's.
- `ChestBlockEntity` sizes its list in a field initialiser; a subclass with more slots must call
  `setItems` in its constructor or a placed chest is short until reloaded.
- Extending a vanilla block brings its whole behaviour: `ChestBlock` pairs, so
  `chestCanConnectTo` must answer false. List what came with the parent and decide about each.
- Replacing one block with another spills the old block entity (`setBlockState` calls
  `preRemoveSideEffects`). Take the load off first; give it back through the chunk-load route,
  because a block entity built by `setBlock` runs no `onLoad` until the next `tickBlockEntities`.
- A block state change fires no lifecycle hook, and on a client nothing at all;
  `BlockEntity#setBlockState` is the one hook that fires on both sides. Anything derived from block
  states and simulated on both sides needs it.
- A block placed by anything but a player never runs `getStateForPlacement`; a block that reads
  its neighbours re-reads them when it joins its graph (`BeltBlock`).
- Asking for a capability in an unloaded chunk loads it: check `level.isLoaded` first.
- `stillValid(access, player, block)` compares against one block; a menu shared by tiers must use
  `instanceof` on the base class or the second tier's screen closes a tick after opening.

Waking, ticking and belts
-------------------------

- `maxUpStep` is zero on `Entity`; only `LivingEntity` raises it. Items, minecarts and orbs stop
  dead at a riser unless lifted. Testing a slope with a player proves nothing.
- `stepOn` fires for the block an entity is supported by, `entityInside` for one it overlaps; a
  slope needs both, and only one may move the entity.
- A block whose collision reaches above its block must say `collisionExtendsVertically`, or
  `getOnPosLegacy` answers with the air above and it stops carrying at the top.
- The wake signal (`updateNeighbourForOutputSignal`) has a radius of exactly one block. Anything
  reaching further (the long-handed inserter) must answer this before it is written.
- A subsystem that moves items without a block entity is inaudible and every test still passes.
  The first test to write for a new mover is one where work arrives from a distance.
- A scheduled block tick is server-only; nothing inside the belt simulation may use one
  (`BeltLines` ticks the splitter). A gametest is a server, so it cannot catch this.
- A horizontal `Entity.move` clears `onGround`; read it before and put it back after.
- Bank a finished craft before asking for power.
- Spending is not a supply change. A buffer that wakes its machine on every change wakes it from
  its own tick, and a machine that spends without progressing re-ticks for ever (the electric
  inserter holding an item over a full chest).
- A belt announces a block gaining its first item and nothing else. Room appearing as items move
  on is announced only at a block something failed to insert at (`BeltRun.waiting`).
- `setChanged()` reaches the anchor's six neighbours only; use `Multiblock.announce` from the
  inventory hook, never from `setChanged`.

Gametests
---------

- A test is two registrations, type and instance; a missing type passes and breaks a client.
  `nauvis_lib`'s `GameTests.add` does both.
- `TestData.padding` defaults to zero and machines sprawl; every test that builds one asks for
  room.
- A per-world `SavedData` is shared by every test in a run. A test that must own world state goes
  in a batch of its own (`tests.batch("alone")`), resets what it uses and names technologies no
  other test names; `/research grant` cascades through `steam-power` and `automation`.
- A test's ground is its structure (a point), and only the structure's chunk ticks entities. A
  test that moves a mob or drops items lays its own floor and calls `setChunkForced`; an entity in
  a non-ticking chunk is not even returned by `getEntities`.
- `GameTestHelper.spawnItem(Item, BlockPos)` spawns at the block's corner; use the float overload.
- `GameTestServer` force-enables every datapack, so `gameTestServer` runs pass
  `-Djaguarm.benchRecipePacks=false` and `BenchRecipePacks` offers no pack; only absent is safe.
- `StacksResourceHandler.deserialize` replaces the slot list with the saved one; a handler that
  gained a slot comes up short. `StacksResourceHandlerMixin` pads or cuts.
- A slot rule that filters by ingredient means a test must set the recipe before feeding a
  machine. A slot's capacity is the item's stack size unless something says otherwise.
- `gen_recipes.py --write` writes only recipes already on disk; `--all` writes every recipe and
  produces load errors for unregistered items.
- The sun in a `GameTestServer` world follows the world clock, which runs through the whole run,
  so a test that depends on daylight is a different test in the evening. Pin the clock
  (`clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON)`) and run it in a batch of its
  own; the formula is tested on its own. `canSeeSky` lags `setBlock` by a tick or two.
- `makeMockServerPlayer` is not in the player list; broadcasts never reach it.

Data, recipes and registries
----------------------------

- NeoForge ships its own copy of 394 vanilla recipes and its resources beat a mod's. A removal in
  plain resources silently does nothing for those (`hopper`, `rail`, `piston`, `torch`...). Ship
  removals as a built-in datapack at `Pack.Position.TOP` (`ModPacks`), and test through a running
  recipe manager, not files.
- `ItemStack.CODEC`, `ItemStackTemplate.MAP_CODEC` and the stack-size component cap counts at 99
  while parsing, and `ItemStackTemplate.create()` returns an empty stack over the item's size.
  Anything holding more than a stack names its own codec; the mixins in `nauvis_lib` lift the
  rest. `Item.ABSOLUTE_MAX_STACK_SIZE` is a compile-time constant, inlined everywhere; mixin the
  methods that read it. Mixin cannot inject into an interface (`Container.getMaxStackSize`).
- The condition is `neoforge:never`; `neoforge:false` throws while parsing that one recipe and
  leaves it craftable.
- A registry codec throws on an id nothing registered, while loading the file. Data that ships
  ahead of the things it names keeps names, not lookups (`Technology`).
- A built-in datapack needs a `pack.mcmeta` or `AddPackFindersEvent` throws a bare NPE.
- `incorrect_for_<material>_tool` is the tier, `mineable/<tool>` the kind and speed. Emptying a
  tag needs `"replace": true`, adding needs `false`; either wrong is silent.
- Where "no opinion" and "could not work it out" share a representation (an empty group string),
  the second must throw.
- A bench copy under a timed recipe's id replaces it, machines included; copies are `<name>_bench`.
- `new ItemStackTemplate(Items.AIR)` throws from a static initialiser as an
  `ExceptionInInitializerError` naming nothing. Use `Optional<ItemStackTemplate>`.
- A recipe result over one stack: Facrafting's own `RESULT_CODEC`, not vanilla's.

Screens and config
------------------

- Several arrows through one channel need lanes (`TechnologyLayout.Edge`); a one-pixel line inside
  a scaled transform rounds to nothing below 1x (`max(1, ceil(1 / zoom))`); a bounded walk does not
  find a longest path (compute, then bound); prefer drawing fewer arrows to ordering better.
- A comment claiming "which is Factorio's rule too" is a checkable claim; one was false for months.
- A Jade provider with no config translation crashes the settings screen; one implementing both
  `IServerDataProvider` and `IComponentProvider` throws at registration; Jade reads the hovered
  block, so `MultiblockRedirect` rebuilds the accessor at the anchor.
- Vanilla's furnace progress sprites are opaque with the panel's grey baked in; use the pack's.
- A clickable box drawn through a label reads the click; `check_gui_layout.py` catches it only for
  boxes in its table.
- NeoForge rewrites a config that does not match the spec, comments included; reasoning goes in
  `nauvis/pack/config/README.md`.
- A list layout is four decisions: buckets, order within, order of buckets, what else is on
  screen. Name all four before changing one.
