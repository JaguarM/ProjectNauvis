Silent failures
===============

Every one of these compiled, passed the tests, and was still wrong — most of them shipped. This is
the list to read before writing, not after something looks odd in game. `ARCHITECTURE.md` is the
companion: the rules these are the ways of breaking.

Models, datagen and rendering
-----------------------------

- **Two overlapping elements that share a face plane z-fight, and a rotated element cannot avoid
  overlapping.** A square box cannot end flush against a 45-degree face without either overlapping
  it or leaving a gap, and a tilted end cannot stop square at a block boundary — so a sloped belt's
  slab necessarily runs through the square box under it *and* into the flat belt at the top of the
  climb. While all three spanned the full width their side faces sat on the same two planes and
  flickered. **Shrink the overlapping piece, not the pieces it overlaps**: a tenth of a pixel off
  each side of the slab clears every pair at once and leaves the flat joints exactly as wide as the
  belts they meet, where insetting the square boxes instead would have narrowed those joints. Never
  the other direction — a box wider than its block reaches into whatever is placed beside it.
  **And geometry that merely interpenetrates is usually fine**: the first fix for this clipped the
  slab back inside its block and rebuilt the missing corner out of six little boxes, which was more
  code, looked worse, and was solving the flicker by removing an overlap that was never the
  problem.

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

- **`maxUpStep` is zero on `Entity`, and only `LivingEntity` raises it.** So a slope that a player
  and every mob walks up without noticing stops an item, a minecart, a boat and an experience orb
  dead against its first riser — which is most of what a belt carries. Anything relying on entities
  climbing has to either lift them itself or keep every step under what it can lift them by in a
  tick. **Testing it with a player proves nothing about the case that breaks.**
- **`stepOn` is called for the block an entity is *supported by*, and `entityInside` for a block it
  merely overlaps — a slope needs both.** An item arriving off a flat belt is still supported by
  that flat belt while its nose is against the ramp, so the ramp is never asked to lift it, and it
  cannot become the supporting block until it has been lifted: a circle, and the item sits at the
  seam forever. Create and Immersive Engineering both drive their conveyors from `entityInside` for
  this reason. Where both hooks can fire for one block, only one may move the entity, or it travels
  at double speed over the seam.
- **A block whose collision reaches above its own block must say `collisionExtendsVertically`**, or
  it stops carrying at the top. `Entity.getOnPosLegacy` — which is what `stepOn` is dispatched on —
  otherwise answers with the block a fifth of a block under the entity's feet, and for something
  standing on the tall part of a ramp that is the *air above it*. The belt asked the air to do the
  carrying and the air declined. It is the switch fences and walls use.

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
  `crafting_table` pack was on during `runGameTestServer`, and those packs ship a shapeless copy of
  each recipe *under the same id* — so of nineteen timed recipes, **sixteen were not timed recipes
  while the tests ran**. Nothing failed; what it cost was fidelity, and a real world was never
  affected. **Fixed by not offering the pack at all**: every `ModPacks` returns early when
  `-Djaguarm.benchRecipePacks=false`, which each `gameTestServer` run sets and nothing else does.
  Off is no defence when the thing enabling it never asks whether it was off; the only state vanilla
  cannot override is *absent*. Separate ids for the bench copies was the other fix and would have
  changed a released mod's permanent ids. `nauvis:timed_recipes_are_timed` asks the running recipe
  manager what type each of seven recipes is, so this cannot come back quietly.

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

- **Every elbow that turns down the middle of the gap between two columns is the same line.** The
  technology tree drew each arrow as out-across-in with the across at the midpoint, which is what
  vanilla's advancement screen does — and vanilla's is a *tree*, where one parent owns a column's
  worth of children. A graph has several parents per column, and all of their verticals landed on
  the same x: five parents fanning out to eleven children came out as one vertical bar with stubs
  off both sides, saying nothing about which technology needed which. Nothing was wrong with the
  layout, every test passed, and a person saw it in a second. **When several sources route through
  one channel, the channel is part of the layout** — `TechnologyLayout.Edge` carries a lane, and
  `technology_layout_is_sound` fails if two arrows share one where their runs could overlap.

- **A one-pixel line inside a scaled transform rounds away to nothing.** The technology screen
  drew its arrows as one-unit `fill` calls inside a `pose().scale(zoom, zoom)`, which is correct at
  1x and silently loses lines below it — the arrows a player reported missing were being drawn, at
  a width that rounded to zero coverage. **Thickness is a screen-pixel quantity, not a canvas one**:
  `max(1, ceil(1 / zoom))` in canvas units. Anything one pixel wide inside a scale has this bug, and
  it appears the day zoom is added rather than the day the drawing is written.

- **The obvious fix for a tangled graph is a better ordering, and it was worth nothing here.**
  Told the tech tree needed the ordering step of a layered layout — barycentre sweeps both
  directions, dummy nodes, keep the pass that crosses least — the tempting move is to write it and
  declare the tangle fixed. Measured first instead: the shipped single-sweep ordering already sat at
  twenty-two crossings, and sixty randomised restarts with adjacent transposition never beat it. No
  ordering could, because two technologies were each a prerequisite of most of the next column.
  **What fixed it was drawing seventeen fewer arrows** — a transitive reduction, and treating a
  science pack as a gate on the node rather than a parent with a wire — which took crossings from
  twenty-two to one. The ordering machinery is in anyway, for a tree of two hundred; it just was not
  the bug. **Measure the objective before implementing the fix for it**, and prefer removing the
  thing being drawn to arranging it better.

  The end of that road is that **the whole tree was the wrong picture**. Drawing less of it - one
  technology's ancestors and two levels of what it leads to, with a list and a search box for
  getting anywhere else - is what Factorio does, and it made the layout problem small enough that
  the clever parts stopped mattering. The gate rule went with it: a science pack is worth an arrow
  again once a view holds a dozen nodes instead of the tree.

- **A view's depth is a longest path, and a walk of N steps does not find it.** The research
  screen shows descendants two deep, placing each by the longest path from the selection so that a
  technology which is both a child and a grandchild sits in the grandchild's column, where its
  other arrow can reach it. The walk that found them stopped at two steps, so a technology reached
  in two steps one way and three another was placed at two - beside the very thing it needs, and
  an arrow cannot run within a column. Factorio's tree had no such diamond until oil processing
  brought five technologies with it, and `technology_layout_is_sound` failed the same day. The
  depths are relaxed to true longest paths now, and a node the relaxation pushes past the view's
  reach is left out rather than drawn where the picture would lie. **A bound on a search is not a
  bound on the answer**; compute the answer, then apply the bound.
- **A comment that says "which is Factorio's rule too" is a claim, and this one was false.**
  Research progress was thrown away on switching, with a paragraph explaining that Factorio does the
  same and that keeping it would cost a per-technology map. Factorio keeps it; the map is six lines;
  and the paragraph is why nobody questioned it for as long as it stood. **Identity claims about the
  game being copied are checkable facts** — non-negotiable #1 — and one written into a doc comment
  reads as settled long after anybody remembers checking it.

- **A Jade provider with no config translation is a crash, not a blank line.** Jade's settings
  screen asserts on a provider with no name, from `ScreenEvent.Init` — so a missing
  `config.jade.plugin_<modid>.<uid>` key crashes the moment any screen opens.
- **A Jade provider must not be both halves.** Jade throws at registration if one object implements
  both `IServerDataProvider` and `IComponentProvider`. Outer data class, nested `Client`, shared uid.
- **On a multiblock, Jade reads the block you point at, and only one of them has anything to say.**
  A machine here is four, nine or seventeen blocks with a single block entity, so an electric mining
  drill showed its stored FE on the middle block of nine and nothing on the other eight — the middle
  being the one block you can least easily look at. Ours were registered against the *block* class
  and still failed, because they fetched `accessor.getBlockEntity()`, which is the entity at the
  hovered position. **Jade's own universal providers are worse**: the energy bar and the item
  contents are registered against `BlockEntity`, so no amount of fixing our providers would have
  moved them. The answer is not per-provider — it is `IWailaClientRegistration.addRayTraceCallback`,
  returning a `BlockAccessor` rebuilt at the anchor, which every provider then sees and whose
  position is what gets sent to the server for `appendServerData`. Verified against the bytecode
  rather than assumed: `WailaTickHandler` runs the callbacks and *then* calls
  `ObjectDataCenter.set`, so the redirected accessor is the one the server data is fetched for. See
  any mod's `compat/jade/MultiblockRedirect`.
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
