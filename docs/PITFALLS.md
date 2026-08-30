Silent failures
===============

Every one of these compiled, passed the tests, and was still wrong — most of them shipped. This is
the list to read before writing, not after something looks odd in game. `ARCHITECTURE.md` is the
companion: the rules these are the ways of breaking.

Models, datagen and rendering
-----------------------------

- **Two overlapping elements that share a face plane z-fight, and rotation makes the overlap
  unavoidable.** A square box cannot end flush against a 45-degree face without either overlapping
  it or leaving a gap, so a sloped belt's square end caps have to overlap its rotated slab - and
  while both spanned the block's full width, their side faces sat on the same two planes and
  flickered. Holding the square boxes a tenth of a pixel back from the sides is the whole fix; the
  tempting other direction, making them *wider* than the block, puts them inside the belt line laid
  beside it. **The same trap across blocks**: the same ramp's underside reached a third of a block
  past its own block and through the neighbouring belt's slab, which both z-fought *and* left the
  ramp's surface climbing visibly through the belt it was supposed to meet. Mirroring the fix onto
  the descending ramp was forgotten, and it looked perfect from every angle except the one that
  showed it. `NauvisLogisticsModels.withinItsBlock` refuses it at datagen time now.

- **A model file renamed out from under its item.** `CUBE_COLUMN_HORIZONTAL` writes to
  `block/<name>_horizontal`; the item model defaults to `block/<name>`. The block rendered and the
  item was a checkerboard, and datagen reported nothing — both files were written exactly as asked.
  Call `registerSimpleItemModel(block, modelId)` whenever a template adds a suffix.
- **A fluid with no `FluidModel`.** Every registered fluid needs one via `RegisterFluidModelsEvent`
  in 26.2; `getStillTexture` on `IClientFluidTypeExtensions` is gone. Not skippable for a fluid
  never placed in the world — it is drawn wherever a tank is shown. NeoForge logs
  `Missing FluidModel for fluid` and nothing else complains.
- **A `VariantMutator` sets `y`, it does not add to it.** A multi-block turns twice: each cell has
  its own quarter turn, and the machine has a facing. `.with(cellDispatch).with(ROTATION_...)`
  looks right and is wrong in three directions of four — the facing *overwrites* each cell's turn.
  Dispatch over both properties at once and add the two turns by hand. It survived so long because
  `MachineCell` rotates before building its `VoxelShape`, so **the machine you saw and the machine
  you walked into were different objects**; a person turning a boiler round found it.
  `check_models.py` now checks each variant's `y` against the shape's own arithmetic.
- **A renderer drawing outside its own block is frustum-culled with no error.** See
  `getRenderBoundingBox` in `ARCHITECTURE.md` and `API-26.2.md`.

Blocks and multi-blocks
-----------------------

- **`createBlockStateDefinition` runs inside `Block`'s constructor**, before any field of your
  subclass exists, so a block that picks its properties from a field reads null. One `MinerBlock`
  with a tier field gave the electric drill a five-value `part` property; it threw at registration,
  which was luck — the same mistake between two shapes of equal size would have been silent. Answer
  with a constant on a subclass.
- **A shape that builds its cells in a loop reads as smaller than it is.** `check_models.py` reads
  `new MachineCell(...)` calls out of the source, and a loop is not a number: the electric drill
  reported itself as 1×2 and passed. The checker now refuses to guess, and every shape writes its
  cells out — worth doing anyway, since a cell's place in the list is its `part` value and `part`
  values are in world saves.
- **A machine with a footprint is several endpoints on the grid, and they are the same machine.**
  Every block publishes the energy capability so a pole can supply a machine whose middle is out of
  range, so a network must reduce endpoints by *identity* rather than position, or one engine is
  charged several times a tick.
- **A machine spills its inventory from `BlockEntity#preRemoveSideEffects`**, not from
  `Block#affectNeighborsAfterRemoval`. The wrong one compiles, reads correctly, and drops nothing.
- **A modded `Container` must register its own item capability.** NeoForge wraps vanilla's, but
  only for a hard-coded list of vanilla block entity types.
- **Overriding `getContainerSize` does not resize the list it counts.** `ChestBlockEntity` builds
  its `NonNullList` at 27 in a field initialiser, and only `loadAdditional` rebuilds it at
  `getContainerSize()`. So a subclass with more slots is correct for every chest that was *loaded*
  and wrong for every chest that was *placed*: it reports thirty-six slots while holding
  twenty-seven, and the screen reads past the end of the list the first time somebody opens it. A
  save and a reload hides it, which is why it would have survived a play session. Call `setItems`
  in the constructor. Generally: **a size that a subclass may change has to be read where it is
  used, not baked into a field initialiser that runs first.**
- **Extending a vanilla block brings its whole behaviour, wanted or not.** `ChestBlock` is
  designed for extension in 26.2 - the copper chests are subclasses - so a metal chest gets the
  lid, the sounds and the openers counter for free. It also gets *pairing*, and a pair combines
  the two containers into one menu: two 54-slot chests placed side by side would ask for a
  108-slot screen that does not exist. `chestCanConnectTo` answering false is the whole fix, and
  nothing about the class hints that it is needed. **After inheriting from vanilla, list what came
  with it and decide about each one.**
- **Replacing one block with another empties the old block entity onto the floor.**
  `LevelChunk.setBlockState` calls `preRemoveSideEffects` whenever the block itself changes, so an
  in-place replacement - a belt swapped for another tier - drops what the belt was carrying as items,
  which reads as correct because nothing is *lost*. It is still wrong: the load ends up on the
  ground instead of on the belt. Take it off before the swap and give it back after. And the giving
  back has to go through the same route a chunk load uses, because **a block entity built by
  `setBlock` does not run `onLoad` until the next `tickBlockEntities`** - so it is not in any graph
  yet, and anything handed to it directly would be handed to nothing.
- **A block state change is the one edit no lifecycle hook reports, and on a client nothing
  reports it at all.** `onLoad`, `setRemoved` and `onChunkUnloaded` cover a block entity arriving
  and leaving; `onPlace` and `affectNeighborsAfterRemoval` are server-only. A belt that is *turned*
  is none of those - the block entity is never touched - so the server said so by hand and the
  client was never told, and since the client keeps its own copy of every belt run and draws from
  it, a turned belt went on carrying items along its old line on every client until the chunk was
  reloaded. Nothing logged, and every gametest passed, because a gametest is a server.
  **`BlockEntity#setBlockState` is the hook**: `LevelChunk.setBlockState` calls it on both sides
  whenever the state changes and the block entity survives. Generally: *if a subsystem is derived
  from block states and simulated on both sides, every property it reads needs a hook that fires on
  both sides.*
- **A block put down by anything but a player never runs `getStateForPlacement`.** A command, a
  structure, another mod or `GameTestHelper.setBlock` write the state you hand them, so a block
  that works out its look from its neighbours is drawn wrong and stays wrong — nothing changes
  beside it afterwards, so no `updateShape` comes. `BeltBlock` re-reads its own shape and its
  neighbours' when it joins the graph, which also covers a corner in a chunk that had not loaded.
- **Asking for a capability in an unloaded chunk loads it.** Check `level.isLoaded` first — not as
  an optimisation, but so a network at the edge of the loaded world does not drag chunks in.

Waking, ticking and belts
-------------------------

- **The wake signal has a radius of exactly one block, and nothing says so.**
  `updateNeighbourForOutputSignal` walks the six positions touching the block entity that changed.
  Every machine here happened to have its work land next door, so "a `setChanged` reaches whoever
  cares" reads like a general fact. The long-handed inserter is the first thing with both ends
  outside that radius and it fails *silently and late* — it works while items keep arriving and
  stops dead at the first gap. **Anything that reaches past its own neighbours has to answer this
  before it is written**; the answer is in `InserterBlockEntity`.
- **A machine that moves items without a block entity is inaudible, and every test still passes.**
  The belt touches no block entity, so an inserter beside one was woken only by items put *onto*
  its own tile — which is exactly what every test did — and never by items *travelling* to it.
  Eighty-one tests passed while any inserter unloading any belt stalled at the first gap in the
  flow. **When a new subsystem moves things, the test to write first is the one where the work
  arrives from a distance rather than being handed over.**
- **A scheduled block tick is server-only, so nothing inside the belt simulation may use one.** The
  splitter arrived as a `BaseEntityBlock` with `scheduleTick` — right for every other machine and
  exactly wrong here, because the *client* simulates belts. The client's `BeltRun` handed items into
  the client's splitter, which never advanced them: items vanished at the splitter and the belt
  behind it appeared to jam while the server routed them perfectly. All six of its gametests passed,
  because a gametest is a server. `BeltLines` owns that tick now, and `SplitterSleepsWhenEmptyTest`
  asserts membership of the active set rather than `hasScheduledTick`.
- **A horizontal `Entity.move` tells the entity it is falling.** `Entity.move` only decides whether
  something is standing on anything when the movement had a vertical component, so a push along a
  belt with `y = 0` clears `onGround` — and the next tick's `stepOn` does not run, so the belt
  carries in stutters. For a player it also breaks fall damage and step sounds.
  `BeltBlock.stepOn` reads `onGround()` before the move and puts it back after.

Gametests
---------

- **A gametest whose type was never registered passes anyway, and breaks a client.** A test is two
  registrations: the instance, which runs, and the `MapCodec` type in `Registries.TEST_INSTANCE_TYPE`.
  The instance registry is synced to clients, and an entry with no codec throws
  `Failed to serialize ResourceKey[...]` on a client boot while `runGameTestServer` stays green.
  The invariant is per *class*, not per name; `tools/check_gametests.py` enforces it.
- **Gametests have no padding by default, and machines now sprawl.** `TestData`'s last field is
  `padding` and it defaults to 0, so a seventeen-block engine lands in the next test along, where
  it is broken by its blocks or joined to its network — and the failure appears in whichever test
  ran second. Every test that builds a machine now asks for room.
- **A per-world `SavedData` is shared by every gametest in a run.** `the_crafting_gate_is_installed`
  completed `automation` to check the gate; `ResearchState.complete` clears the current research;
  `lab_researches` was researching `automation`. The red test said *a lab had done no work* and
  mentioned research nowhere. Padding separates blocks and nothing separates world state — so every
  test that touches research resets what it uses and names a technology no other test names.
- **Naming technologies nobody else names stops working the moment something cascades.** The rule
  above holds for a test that completes one technology. `/research grant` completes the
  prerequisites too, and every costed technology's chain runs back through `steam-power` and
  `automation`, which four other tests are researching — so `lab_researches` failed saying a lab
  had done no work, mentioning commands nowhere, exactly as it had before. **A second
  `TestEnvironmentDefinition` is a second batch, and batches run one after another**, so a test
  that must own the world's state goes in one of its own. `nauvis_research:alone` holds one test.
  Restoring the tree afterwards is still needed, for whatever batch runs next; snapshot the
  completed set and put it back, because a test cannot clean up what it did not know it changed.
- **`GameTestHelper.spawnItem(Item, BlockPos)` spawns at the block's corner, not its middle**, so an
  item over a one-block-wide thing hangs half off it. Use the `(float, float, float)` overload.
- **`GameTestServer` force-enables every datapack, including ones shipped switched off.** Vanilla
  selects `getAvailableIds()`, so `alwaysActive = false` means nothing there. Every mod's
  `crafting_table` pack is on during `runGameTestServer`, and those packs ship a shapeless copy of
  each recipe *under the same id* — so of nineteen timed recipes, **sixteen are not timed recipes
  while the tests run**. Nothing fails; what it costs is fidelity. A real world is unaffected. The
  fix is a system property or separate ids for the bench copies, and the second would change a
  released mod's permanent ids, so it is written down rather than done.

Data, recipes and registries
----------------------------

- **NeoForge ships its own copy of 394 of Minecraft's recipes, and its resources beat yours.**
  Removing a vanilla recipe is a `data/minecraft/recipe/<name>.json` holding `neoforge:never`, and
  from a mod's plain resources that silently does nothing for those 394. Four byte-identical
  removals; `chest`, `furnace` and `hopper_minecart` went and **`hopper` did not** — no error, no
  log line, three quarters of the change working. `rail`, `iron_door`, `dropper`, `dispenser`,
  `observer`, `piston`, `minecart` and `torch` are on that list too. The answer is
  `AddPackFindersEvent` with `alwaysActive` and `Pack.Position.TOP` — see `ModPacks.java`. **And it
  was only caught because the test asks a running recipe manager**, not the files on disk.
- **The condition is `neoforge:never`, and there is no `neoforge:false`.** A wrong name does not
  fail the build or the load — it throws while parsing that one recipe, which is a line in a log
  and a recipe that is still craftable.
- **A registry codec throws on an id nothing registered, while loading the file.** The technology
  tree names science packs and recipes no mod registers yet, because the whole tree ships from the
  first commit and the items catch up. With `BuiltInRegistries.ITEM.byNameCodec()`, `automation`
  would load and `advanced-electronics` would not, and the difference would be one log line and a
  research list that was quietly short. `Technology` keeps names, not lookups. **Whenever data ships
  ahead of the things it names, the reference has to be a name and not a lookup.**
- **A built-in datapack needs a `pack.mcmeta`**, or `AddPackFindersEvent` throws a bare NPE naming
  neither the mod nor the directory.
- **Two block tags decide two different things about a tool.** `incorrect_for_<material>_tool` is
  the **tier** — whether the block drops at all. `mineable/<tool>` is the **kind**, and carries
  **speed**. Asked for a pickaxe that does everything, this pack first emptied the seven tier tags:
  a wooden pickaxe could mine obsidian, and the pickaxe still dug dirt at bare-hand speed, which was
  the actual complaint. The fix is three tag references added to `mineable/pickaxe`. **When a tool
  change does not feel like anything, the tag is probably the tier one.** Note the flag flips with
  the intent: emptying a tag needs `"replace": true`, adding to one needs `"replace": false`, and
  either wrong is a silent no-op or a silent deletion.
- **An empty string is a valid group, so a stale lookup table is a layout choice.** `GROUP_BY_TYPE`
  fell back to `""` for anything unrecognised, and `""` is exactly what a recipe with no opinion
  sets — so an unknown category landed in the "Ungrouped" tab, indistinguishable from an author
  choosing not to group it. **Wherever "no opinion" and "I could not work it out" have the same
  representation, the second one has to throw.**

Screens and config
------------------

- **A Jade provider with no config translation is a crash, not a blank line.** Jade's settings
  screen asserts on a provider with no name, from `ScreenEvent.Init` — so a missing
  `config.jade.plugin_<modid>.<uid>` key crashes the moment any screen opens.
- **A Jade provider must not be both halves.** Jade throws at registration if one object implements
  both `IServerDataProvider` and `IComponentProvider`. Outer data class, nested `Client`, shared uid.
- **A special item model names no model, and the checker said so for a year.**
  A `minecraft:special` item definition - a chest, a bed, a banner, a shield - puts its *renderer*
  under `"model"` as an object and the model carrying the display transforms under `"base"`, so
  `check_models.py`'s "every `model` key that is a string" rule found nothing in one and reported
  `names no model at all`. That is the checker being wrong rather than the asset, and the danger is
  the shape of it: a checker that reports a false failure on a correct file gets its rule relaxed,
  and the relaxation is what hides the real one. `base` is now a model reference, and the
  renderer's `texture` is checked against the directory that renderer reads - which is not a
  model's texture slot and so was invisible to everything.
- **A clickable box drawn through a label reads the click anyway.** The research button was first at
  (116, 16), inside the lab screen's full-width status band. Nothing looked wrong, because the
  status text is usually shorter than that. `check_gui_layout.py` catches this exactly — **but only
  for boxes listed in its table**, and a button is not a bar, so it had to be added.
- **A config file's comments do not survive a boot.** NeoForge compares every config against the
  mod's `ModConfigSpec` on load and rewrites anything that does not match, comments included. So
  reasoning written into a toml reads correctly in the repo and is gone from the only copy anyone
  looks at. `nauvis/pack/config/README.md` exists for exactly this.
- **A layout is four decisions, and fixing one changes nothing you can see.** Which buckets, what
  order inside a bucket, what order the buckets go in, and what else is on screen. The crafting
  panel's tabs were wrong in all four: correct tabs whose contents were still sorted by namespaced
  id read exactly as "grouped by mod"; correct contents under an alphabetical strip read as a
  jumbled menu; and both sat behind an "everything" tab a player landed on first. **When a list
  reads wrong, name all four before changing one.**
