Next session
============

Written 2026-08-29 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the jobs below are done — it describes the work in front of us, not the
project.

**Eighty-nine gametests pass and the pack builds clean.** Every machine is the size Factorio made
it, the lab exists, five checks run in `./gradlew build`, the transport belt works, the burner
inserter fuels itself off the belt it is unloading, the long-handed inserter reaches two blocks —
over a belt, over a walkway, over a row of machines — and **the crafting panel opens on Factorio's
four tabs.**

What the tabs cost, in one paragraph
------------------------------------

Almost nothing in the panel and almost everything in the data. `tools/gen_recipes.py` was reading
the dump's `type` field — eleven values, Factorio's *item taxonomy* — where it wanted `category`,
whose four values are the crafting menu's actual tabs. Swapping the table is six lines, and it now
throws on an unknown category rather than quietly emitting an empty group, because "" lands in the
panel's "Ungrouped" tab where it reads as a layout choice instead of a stale table. Every timed
recipe regenerated; `--check` went from seventeen wrong to zero.

**The half that was actually the complaint was the sort.** Within a tab the grid was ordered by
recipe id, ids are namespaced, so every tab was *still* clustered by mod — the tab strip had been
fixed and nothing would have looked different. Factorio sorts within a tab on a per-item `order`
string, so `FacraftRecipe` now has one: optional, defaulting to "", generated from the dump's own
file order, which is alphabetical by Factorio's display name. Recipes carrying an `order` come
first in that order and anything without follows by id, so a pack that sets none is unchanged and a
pack that sets some is not half-sorted. **If a dump carrying Factorio's real `order` strings ever
arrives, one line in `load_inputs` is what changes.**

**Facrafting's default grouping is adaptive, and that was the only real decision.** Flipping the
constant from `CATEGORY` to `GROUP` is one line and wrong for every pack that is not this one: with
no groups set, `GROUP` opens on a single "Ungrouped" tab with the badge button as the only way out.
So `RecipeTabs.suggestedMode` counts — half the recipes carrying a `group` means the pack author
laid the strip out and their layout wins. It is a *suggestion*: `FacraftPanel.modeChosen` records
that the player pressed the badge, and a suggestion never overrides that until logout. That flag is
also why the panel compares `builtFor` against `mode` when it renders — a datapack reload sends the
recipes again with a panel open, and a strip built for one grouping and labelled with another looks
exactly like a rendering bug.

**One thing was deliberately not done: the tab strip's own order.** `RecipeTabs.buildByKey` sorts
tabs alphabetically by title, so the strip reads Intermediate products, Logistics, Production where
Factorio reads Logistics, Production, Intermediate products, Combat. Fixing it needs somewhere for a
*group's* order to come from, and the recipe dump has no such field — the obvious cheap answer, the
lowest `order` in each group, is meaningless here because ours is global and alphabetical. It wants
either a group-order field in the pack's data or Yannic saying the four names by hand.

**A tier is a block, not a block entity.** Reach, swing time and draw are three numbers on
`ElectricInserterBlock`; `LongHandedInserterBlock` overrides them and a codec; one block entity
type is registered against both blocks. The fast and filter arms arrive the same way.

The jobs, in the order Yannic asked for them
--------------------------------------------

The tabs were the first of these and are done. The science tree is milestone 3 and the biggest
thing here; the last two are what is left of milestone 2 and are untouched by it, so they can be
done in any order after.

### 1. A science tree that unlocks recipes

This is milestone 3 and the biggest thing in the pack that is not a machine. **Do not start
writing until the two decisions at the bottom of this section are made** — one of them decides
whether the job is "write a generator" or "write eight JSON files".

**What already exists and was built for this.** `LabBlockEntity` counts cycles and says so in its
own comment: Factorio's lab does not know what it is researching either, it is told which packs a
technology wants, consumes one of each, and reports a cycle done. `cycles()` is the hand-off point
and the handing over is the only part of the lab that changes. `TICKS_PER_CYCLE` is explicitly a
stand-in, because in Factorio the time comes from the technology.

**The model, which is Factorio's and should not be invented afresh.** A technology has an id,
prerequisites, a cost of *N units*, a set of science packs consumed one of each per unit, seconds
per unit, and effects — of which the only one that matters now is "unlock recipe X". A lab works on
the level's current research; each cycle consumes one of each pack and reports a unit; at N units
the technology completes and its recipes unlock.

**Four decisions that are already made by the pack's own rules:**

- **Research is per-world, not per-player.** Factorio's research belongs to a force, and two
  players in one base with different unlocks is the wrong game. A `SavedData` on the server,
  synced to clients for display.
- **The tree is datapack data**, a datapack registry through
  `DataPackRegistryEvent.NewRegistry`, so a technology is a JSON file and not a Java constant. Same
  argument as recipes: it is data, and data is edited without a compile.
- **It belongs to `nauvis_research`.** One mod per subsystem.
- **Facrafting must not learn what a technology is.** Arrows point one way: a subsystem mod may
  depend on Facrafting, never the reverse. So the lock is a *hook* Facrafting owns and
  `nauvis_research` fills in — a predicate over a recipe id, defaulting to "everything is
  unlocked", installed by whoever wants to gate.

**Where the hook has to be consulted — four places, and missing one is the whole feature:**

| | |
|---|---|
| `ModNetwork.handleQueue` | a client can send any recipe key; this is the real gate |
| `ModNetwork.handleSelectRecipe` | pointing a *machine* at a locked recipe is the same bypass |
| `CraftResolver` | intermediates are queued for you, so it must not resolve through a locked step |
| the panel | display only, and it needs the unlock set on the client to hide what is locked |

The first three are server-side and are the enforcement; the fourth is presentation and must never
be the only check. Note the shape: `OnDatapackSyncEvent#sendRecipes` sends every facraft recipe to
every client, so hiding is a client-side filter over a synced unlock set rather than a smaller
list — which is right, since the set changes while the player is logged in.

**Two smaller things that fall out:**

- `nauvis_research` declares `facrafting` **optional** today. Installing a hook against a
  Facrafting type makes it required and the toml has to say so — *or* the installer goes in a
  `compat/facrafting/` package loaded only when the mod is present, which is exactly the trick the
  Jade plugins already use. The second keeps the mod standalone and is probably right, because the
  standalone bench recipes exist precisely for Facrafting's absence.
- **The `crafting_table` datapack is a hole in any gate**, and it already is one — it is off by
  default and its whole purpose is to let a pack author trade the timed crafts away. Either the
  gate covers those recipes too, or the datapack's description says out loud that it skips
  research.

**The two decisions, and they are Yannic's:**

1. **Where does the tree's data come from?** `reference/factorio/recipes.json` has no
   technologies, so unlike every recipe in this pack a technology's cost cannot be generated from
   anything we hold. Either a `technologies.json` dump arrives beside it and
   `tools/gen_technologies.py` gets written — which is what non-negotiable #2's argument implies,
   because a research cost is the same kind of fact as a craft time and lives in the same place, a
   player's memory — or the first tree is hand-written for the handful of technologies the pack can
   currently reach, and generated later. **Ask before assuming.** The costs must not come from the
   model's memory of Factorio either way; that is exactly what the recipe dump exists to prevent.
2. **How much tree screen?** PLAN.md's shortcut for research is "lab consumes packs, grants
   vanilla advancements; recipes gate on them", with "a real tech tree screen with costs and
   prerequisites" as the rewrite. The minimum that is playable is a *list* of technologies whose
   prerequisites are met, click one to make it the current research — no graph, no layout. That is
   a screen in `nauvis_research`, and it should grow out of Facrafting's panel the way every other
   screen here does rather than sit beside it.

**And the thing to be careful about, because this pack has been bitten by it twice:** an unlock
that is only ever tested by handing a recipe to a player who already has it is not tested. The test
to write first is the one where a technology completes *while the panel is open* and the locked
recipe appears — the work arriving from a distance, again.

**PLAN.md wants one more thing here**: the vanilla-replacement datapack lands at milestone 3,
because once research gates progression there is somewhere for stripped vanilla recipes to go. It
is a separate job and probably a KubeJS one once KubeJS ports; see the note at the bottom.

### 2. The splitter

2×1 and directional — the first multi-block that is not square. `multiblock/` is the framework and
is copied into four mods already. The belt side of it is a run that ends at the splitter and two
that start after it, with the splitter alternating between them.

### 3. Fast-replace by tier

A belt in hand already points the belt you click on the way you are facing, which is half of
Factorio's belt-laying gesture. The other half is that a *faster* belt replaces a slower one, and
it cannot be written until there is a second tier to hold. It is `BeltBlock.useItemOn`, and what
changes is: swap the *block* rather than set a property — which does remake the block entity, so
`beltPlaced`/`beltRemoved` fire and `beltTurned` is not wanted on that path; carry the items on
that block across the block entity being remade, which will not happen for free; hand the old belt
back and pay for the new one unless the player is in creative; and refuse to *downgrade*, or a
stray click wrecks a bus. The run needs no thought — a run never spans two tiers, so the line
splits and rejoins by itself.

**There is no underground belt on any of this, and there will not be a `pipe-to-ground` either.**
Factorio needs both because it is flat — two belts that must cross have nowhere to go but under.
This pack is the same game with a Y axis, so a belt crosses another by changing level, which a
Minecraft player already knows how to build and needs no item for. All four ids
(`underground-belt` and its two upper tiers, and `pipe-to-ground`) are marked `skip` in
`data/mapping.json` with the reason; nothing else in the recipe graph uses any of them, so the
graph stays closed and `gen_recipes.py --check` counts them as skipped rather than missing.

The playtest, which is happening before any of the above
--------------------------------------------------------

Each job ends with `./gradlew build`, `:nauvis:runGameTestServer`, and a client boot. The client
boot is not optional: three of the last four bugs found in this pack were found by a person looking
at the game, and one of them — see the rotation entry in the silent-failures list — passed sixty-
three tests while being visibly wrong from three sides. **The tab complaint that became the tab job
came out of exactly this**, which is the argument making itself.

**The tab work has had a boot and nothing else has.** That boot proves the panel does not crash and
that the widened stream codec round-trips — the log says `Sending 17 recipes` and the client
decoded them — and it proves nothing about what the strip *looks* like, because no test and no log
line can see a tab. **Two things want an eye on them there**: that the four tabs read as Factorio's
four, and that the items inside one are no longer in mod order. Both are visible the moment the
panel opens, and both are the kind of thing a person confirms in five seconds and a suite never
does.

The rest of what is owed is older. The long-handed inserter added a model — a smoker-coloured cube,
so it is the third furnace body on a belt line and wants a proper look — but no session since has
added a fluid or a plugin, and `check_gametests.py` and `check_models.py` cover the registrations
and the references between files. What is actually owed is *watching four things no test can look
at*:

- lay a coal belt, put a burner inserter beside it with an empty slot, and see it pick its own fuel
  off the line and keep running;
- put an inserter on each side of one belt and check they fill two lanes rather than fighting over
  one;
- put a long-handed inserter across a belt from a machine and watch it reach over — and, once it
  runs dry, watch how long the pause feels. `IDLE_RECHECK_TICKS` is one second, chosen on
  arithmetic and never on somebody's eye. **If it reads as a jam rather than as an arm, halve it**;
  it is one constant and the cost is linear in it;
- and tell the three of them apart at a glance on the same line, which is what the borrowed furnace
  textures are being asked to do and may well not do.

### One test is marginal, and it is not the code's fault

`belt_carries_what_stands_on_it` failed once in about six runs while the belt-wake work was going
in, then passed five times running, including three consecutive full-suite runs afterwards, and
every run since. It is not a regression from anything — it went red purely because *adding tests
to the file* moved it, and here is why that is enough:

`ItemEntity.tick` only calls `move()` when the item is off the ground, has horizontal momentum, or
`(tickCount + getId()) % 4 == 0`. A dropped item **resting** on a belt has none of the first two, so
it is only carried on the ticks that arithmetic allows — and the phase of it depends on the entity
**id**, which depends on how many entities the tests before it happened to spawn. The failing run
reported 0.5625 blocks in 60 ticks, which is exactly six belt steps; the test asks for more than
two blocks.

So the belt carries a *player* properly — `aiStep` moves every tick — and carries a dropped item
entity erratically and at some fraction of belt speed. Factorio has no dropped items on belts so
nothing about this is identity, but a Minecraft player will absolutely throw something onto a belt.
Two honest options and neither is this session's to pick: loosen the test to what a resting item
entity can actually do, or give `stepOn` a way to keep an item entity moving so it stops resting.
Leaving it as is means a red test roughly one run in six, which is the worst of the three. It has
survived two sessions of tests being added around it since, so it has not got worse — but nothing
about it has got better either, and the entity-id arithmetic that decides it is still there.

**It went red again during the tab work**, on a session that did not touch a belt, reporting
0.5625 blocks — the same six steps, to the digit — and passed on the immediate re-run. So the
diagnosis above is confirmed rather than merely plausible, and the cost is now measured: it failed
a `runGameTestServer` for a change in a Python generator and a client-side sort. **That is the
argument for picking one of the two options rather than leaving it**, because the next person to
see this red will not know it is not theirs.

### The belt, if you have to touch it

Read `nauvis_logistics/.../belt/` in this order and the whole thing falls out:

| | |
|---|---|
| `Belts.java` | the numbers, and why distances are integer sixty-fourths of a block |
| `BeltLane.java` | **the idea.** Items are stored as the gaps between them, not as positions, so a flowing belt writes one number a tick and a jammed one writes none |
| `BeltRun.java` | one line: its blocks, its two lanes, its tick, how it hands to the next line, and — `announceArrivals` — how anything beside it hears that an item turned up |
| `BeltLines.java` | every run in a level, and how lines are cut and joined when a belt is placed |
| `BeltAccess.java` | how everything else in the game meets a belt, and why giving and taking are two rules |
| `BeltShape.java` | how a corner knows it is one, and why there are two of them rather than eight |
| `BeltBlock.stepOn` | why standing on a belt carries you, and why that is not `entityInside` |
| `BeltBlock.useItemOn` | a belt in hand points the belt you click on the way you are facing |
| `client/BeltRenderer.java` | the items you can see |
| `texture-workshop/make_belt_textures.py` | the art, and why the tread scrolls at exactly 1.875 tiles a second |

Four things about it are load-bearing for anything built on top:

- **Items are pinned to a block and an offset into it**, never to a distance along a run. That is
  what makes cutting, joining, lengthening and turning a line safe, and it is asserted by
  `belt_survives_being_cut`.
- **The client runs the same simulation.** It builds the same runs out of the same block states and
  moves the items itself, so a belt full of items costs no network traffic. Only two things are ever
  sent — an item put on the belt from outside, and one taken off — because those are the only two a
  client cannot work out. **Anything added to belts must keep that property**, or the reason belts
  are affordable goes away. Neither of this session's changes touches it: refusing a lane is a
  decision made before the message that reports the insert, and `announceArrivals` is a signal to
  the blocks beside a belt rather than to a client.
- **A run is awake while it has items, not while it is moving.** No dormant sweep and no wake-up
  plumbing, because `BeltLane` makes a jammed belt cost the same as an empty one.
- **A belt is the one source in the pack that has to say out loud that something arrived**, and
  the reason is worth stating exactly, because the loose version of it is wrong. It is not "it has
  no block entity". `FluidNetwork` and `PowerNetwork` move things without one too and are perfectly
  audible — because they **push**: `FluidNetwork.push` calls `handler.insert` on the endpoint, and
  the `SteamTank` or `MachinePower` it lands in fires its callback. The consumer is woken by being
  filled. **The belt is the only subsystem that deliberately never pushes** — a belt running into a
  chest backs up, which is Factorio's rule and the reason inserters exist — so nothing it does ever
  touches the machine beside it, and `BeltRun.announceArrivals` is the only thing that can speak
  for it. The rule to carry forward: *a subsystem that moves things and never pushes them into the
  consumer has to announce, because nothing else will.*
- **A closed ring of belts is one run that wraps**, and **a block state change does not touch the
  graph** — turning a belt leaves its block entity alone, so `BeltLines.beltTurned` is a third way
  in that anything editing a belt in place will need.

The rules a machine is built to
-------------------------------

These outlive the jobs above and are the answer to "how big, how tall, and can you walk on it".

### Footprint is identity. Height is ours.

Non-negotiable #1 says ids, ingredients and craft times are Factorio-correct from the first commit,
because they live in world saves and in the player's head. **A footprint is the same kind of fact.**
Three-by-three is why an assembler column spaces the way it does; five-by-three is why a boiler
feeds a *row* of engines. Get it wrong and every blueprint a player carries in their head is wrong,
and fixing it later moves every machine in every world.

Height is the opposite. Factorio is two-dimensional and has no opinion, so height is ours to choose,
free to change, and the place to spend effort on making a machine look like something.

Footprints live in `data/mapping.json` as a `size` field and `tools/check_models.py` holds every
machine's cells to them. Heights live in the shape class next to the models and are tuned by
looking at them.

### One tile is one block

The scale is 1:1 and it is not really a choice: the belt, the inserter and the pipe are one tile in
Factorio and one block here, and everything else has to agree with them or nothing lines up. What
1:1 buys —

- **layouts transfer.** A player who knows that a boiler feeds two engines, or that assemblers sit
  three apart with a belt down the middle, builds the same thing here and it fits;
- **the walkways are already there.** Factorio's own layouts leave one-tile gaps to walk in, and a
  one-block gap is a corridor a Minecraft player walks down comfortably — the player is 0.6 wide;
- **ratios stay legible.** Twelve assemblers per engine is a number you can pace out.

### Walkability, in numbers

The metrics that decide this, all vanilla:

| | |
|---|---|
| player height | 1.8 blocks — a corridor needs 2 of headroom |
| player width | 0.6 blocks — a 1-block gap is walkable, and diagonal gaps are not |
| step height | 0.6 blocks — **anything colliding at 0.5 or below is walked straight over** |
| jump height | ~1.25 blocks — a 1-block machine can be jumped onto, a 2-block one cannot |

**A machine you can walk across beats a machine you walk around**, and it decides the silhouette.
A Factorio player tiles machines with no gaps, because in Factorio you can always walk round the far
end of the field. Nine 3×3 machines two solid blocks tall is a wall with no way over it, and the
player is sealed out of their own base.

The answer every machine in the pack now uses:

| | |
|---|---|
| **1.0** | the wall around the outside. One jump, and the only climb in a field of any size |
| **0.75** | the floor inside the wall. A quarter-block dip, under the 0.6 step, so crossing is walking |
| **2.0** | whatever the machine puts in the middle — the one thing you walk around |
| **0.5** | for anything meant to be crossed without even a jump: the electric drill, solar panels, and **belts** |

Two consequences. **The upper storey is mostly air**, so a 3×3 machine two blocks tall is ten blocks
and not eighteen — which is why `MachineShape` takes a set of cells rather than a box. And **the
tall part goes in the middle**, so tiled machines stand their obstacles apart and leave lanes.
`assemblers_tile_walkably` and `power_machines_tile_walkably` walk those lanes and assert every step.

**Collision and silhouette are allowed to disagree** — `PolePart` gives the crossarm a full outline
and no collision, because a shape three blocks over your head that you cannot see should not catch
you. But for something you are meant to walk across, the two agreeing is the point.

The multiblock mechanism, briefly
---------------------------------

`multiblock/` — `MachineCell`, `MachineShape`, `MachineParts`, `Boxes`, `Multiblock` — is copied
into `nauvis_machines`, `nauvis_power`, `nauvis_research` and `NeoProgressiveAutomation`, and
`tools/check_duplicated.py` holds the copies byte-identical (`--sync` pushes the original out; the
copies are never edited). It is `SmallElectricPoleBlock` with two more axes, and every class in it
says why it is the way it is. Read `MachineShape` and `Multiblock` and you have all of it.

The parts worth knowing before touching a machine:

- one block id, one item, an `IntegerProperty part` on every block, and the anchor found by
  arithmetic rather than a lookup — no block entity on the other cells;
- one `updateShape` rule is the whole teardown, which is why a shape's cells must be orthogonally
  connected, which the constructor enforces;
- capabilities are registered against the **block**, not the block entity, so any cell answers —
  that is what lets a pole supply a machine whose middle is out of range, and it is why
  `PowerNetwork` reduces endpoints to distinct handlers;
- **ports** name a cell and a face, so a boiler's steam leaves one block and an engine takes it at
  the open ends of its spine;
- geometry is stated once per machine and read by the model provider, the `VoxelShape` and the
  item model. `MachineParts` is the shared shell so the machines read as one family — **Yannic has
  said that still wants refinement**, and it is one file.

Where the pack stands
---------------------

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | 3×3 and ten blocks; recipe selector, six slots, timed craft, screen, 10 FE a tick |
| `nauvis_logistics:transport_belt` | half a block high and walked over; a run is one object however long, two lanes, items you can watch, and it carries you |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, 30-tick swing, screen with a fuel slot. Fuels itself from what it picks up, so a coal belt keeps it alive |
| `nauvis_logistics:inserter` | the same on 2 FE a tick and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:long_handed_inserter` | the same arm reaching two blocks, over whatever is between. 3 FE a tick, a 17-tick swing, and the only block in the pack that does not sleep perfectly |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | carries steam; a run is one object however long, with visible connections |
| `nauvis_fluids:steam` | a real fluid, so pipes and machines meet at NeoForge's capability |
| `nauvis_power:boiler` | 3×2 and seven blocks; burns fuel, steam out of the block under the chimney |
| `nauvis_power:steam_engine` | 5×3 and seventeen blocks; steam in at the open ends of its spine, 120 FE a tick out |
| `nauvis_power:small_electric_pole` | four blocks tall, climbable, wires itself to whatever it can reach |
| `nauvis_research:lab` | 3×3 and ten blocks; eats science packs on 8 FE a tick and counts research cycles |
| `nauvis_research:science_pack_1` | red science. Craftable now — copper plate and an iron gear wheel |
| `neoprogressiveautomation:burner_drill` | 2×2 and five blocks; a full block with a chimney over the firebox |
| `neoprogressiveautomation:electric_drill` | 3×3 and nine blocks; a half-block deck you walk over, output head at the front |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve assemblers, one
boiler runs twenty-four. None of that is identity; the ids, ingredients and craft times are, and
those are generated.

**The lab has no technology tree**, and that line is deliberate: Factorio's lab does not know what
it is researching either, so the machine could be built without one. It counts cycles and consumes
one of every kind of pack it holds, which is already the rule a technology will impose. Milestone 3.

**The lab's recipe woke up.** It cost four transport belts and shipped with a
`neoforge:registered` condition so it would start working the day belts existed; that day was this
session, the `pending` flag came off `transport-belt` in `data/mapping.json`, and the condition
regenerated away. `science-pack-2` costs an inserter and a belt and is unblocked the same way when
it is written.

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
| `python tools/check_models.py` | every model, texture and blockstate reference, resolved — and footprints and belt speeds, against `data/mapping.json` |
| `python tools/check_duplicated.py` | the copied packages, against each other. `--sync` to fix |
| `python tools/check_gametests.py` | every gametest, for a type registered as well as an instance |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python texture-workshop/make_belt_textures.py` | the belt's art, from ASCII maps. `--preview` for a sheet |

The last five are `checkRecipes`, `checkModels`, `checkDuplicated`, `checkGameTests` and
`checkGuiLayout` in the root `build.gradle`, and all of them hang off `:nauvis:check`. They read
files and start nothing, so they cost a second between them.

Adding a subsystem mod is routine: a subproject in `settings.gradle`, a `build.gradle` copied with
the ids changed, a `src/main/templates/META-INF/neoforge.mods.toml`, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` that puts it in the pack, and its
namespace added to `pack_gametest_namespaces`. `nauvis_power/` is the fullest template and
`nauvis_research/` is the newest — it was made by following exactly that list, so its diff is what
adding a mod costs.

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
for pipe runs, and `BeltRun` for belt lines. Members join and leave; the network ticks once however many members
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
- **A machine with a footprint is several endpoints on the grid, and they are the same machine.**
  Every block of a machine publishes the energy capability, so that a pole supplies a machine whose
  middle is out of range - Factorio's rule, and the reason footprints were worth having. One engine
  is then up to five entries in a pole's supply area: five shares of a shortfall, a wrong count in
  the readout a player reads, and eventually a machine sold energy it had just asked for through
  two of its own blocks. `PowerNetwork` reduces endpoints to distinct handlers by object identity
  each tick. Identity rather than position, so any mod's multi-block gets it for free.
- **Gametests have no padding by default, and machines now sprawl.** `TestData`'s last field is
  `padding` and it defaults to 0. With one-block machines that was survivable; with a seventeen-
  block engine and a chain of two reaching ten blocks, the machines of one test land in the next
  test along - where they are broken by its blocks or joined to its network. The failure then
  appears in whichever test ran second, which is the worst kind: real, silent, and blamed on the
  wrong code. Every test that builds a machine now asks for room.
- **A shape that builds its cells in a loop reads as smaller than it is.** `check_models.py` gets
  a machine's footprint by reading `new MachineCell(...)` calls out of the source, and a loop is
  not a number: the electric drill, written as a nested loop, reported itself as one tile by two
  and passed. The checker now refuses to guess when it cannot read every cell, and every shape
  writes its cells out. That is worth doing anyway — a cell's place in the list is its `part`
  value, and `part` values are in world saves.
- **`createBlockStateDefinition` runs inside `Block`'s constructor**, before any field of your
  subclass exists. A block that picks its blockstate properties from a field reads null and picks
  the wrong ones. The drills hit this: one `MinerBlock` with a tier field gave the electric drill a
  five-value `part` property and a nine-value default state. It threw at registration, which was
  luck — the same mistake between two shapes of equal size would have been silent. Answer with a
  constant on a subclass, which exists long before any block does.
- **A `VariantMutator` sets `y`, it does not add to it.** A multi-block turns twice over: each
  cell has its own quarter turn, and the machine has a facing. Generating that as
  `.with(cellDispatch).with(ROTATION_HORIZONTAL_FACING)` looks exactly right and is wrong in three
  directions out of four - the facing *overwrites* each cell's turn, so every corner of an
  east-facing boiler points the same way. Dispatch over both properties at once and add the two
  turns by hand.

  What makes it worth its own entry is how long it survived. `MachineCell` adds the two rotations
  before building its `VoxelShape`, so the collision was right the whole time and **the machine
  you saw and the machine you walked into were different objects** - which is the failure `Boxes`
  warns about in as many words, in a file written to prevent it. Every test passed, because tests
  look at collision and nobody can see a model from a gametest. It was found by a person turning a
  boiler round. `check_models.py` now checks each variant's `y` against the shape's own arithmetic.
- **A gametest whose type was never registered passes anyway, and breaks a client.** A test is
  two registrations: the instance, which runs, and the `MapCodec` type in
  `Registries.TEST_INSTANCE_TYPE`, which exists so a test *could* come from a datapack. Ours never
  do, so the type reads as dead paperwork - but the instance registry is synced to clients, and an
  entry with no codec throws `Failed to serialize ResourceKey[minecraft:test_instance / ...]` on a
  client boot while `runGameTestServer` stays green. Two went missing here for a day. The
  invariant is per *class*, not per name - a test id and a type id are different registries, and
  `nauvis:pack_loads` registered under type `nauvis:registry_presence` is fine - so
  `tools/check_gametests.py` matches every `GameTestInstance` that is registered to run against
  the codecs handed to `TEST_TYPES`.
- **A block put down by anything but a player never runs `getStateForPlacement`.** A command, a
  structure, another mod or `GameTestHelper.setBlock` all write the state you hand them, so a block
  that works out how it looks from its neighbours is drawn wrong and stays wrong: nothing changes
  beside it afterwards, so no `updateShape` ever comes. Belt corners were straight lines for
  exactly this reason, and only in a gametest, which is the lucky version of it. `BeltBlock`
  re-reads its own shape and its neighbours' when it joins the graph, which also covers the belt
  whose corner is in a chunk that had not loaded yet.
- **A horizontal `Entity.move` tells the entity it is falling.** `Entity.move` only decides
  whether something is standing on anything when the movement had a vertical component, so a push
  along a belt with `y = 0` clears `onGround`. Nothing looks wrong for a tick — and then the next
  tick's `stepOn` does not run, because that hook only fires for something on the ground, so the
  belt carries in stutters. For a player it also breaks fall damage and step sounds, both of which
  are worked out from the same flag. `BeltBlock.stepOn` reads `onGround()` before the move and puts
  it back after.
- **`GameTestHelper.spawnItem(Item, BlockPos)` spawns at the block's corner, not its middle.** An
  item dropped over a one-block-wide thing therefore hangs half off it and behaves like something
  standing beside it rather than on it. Use the `(float, float, float)` overload and add the half.
  This cost an hour of reading `ItemEntity` for a bug that was in the test.
- **A machine that moves items without a block entity is inaudible, and every test still passes.**
  The whole sleeping design rests on `setChanged` reaching all six neighbours, and every source in
  this pack got that for free — until the belt, which moves items along a run and touches no block
  entity at all. An inserter beside a belt was therefore woken only by items *put onto* its own
  tile, which is exactly what every test did, and never by items *travelling* to it, which is what
  actually happens in a factory. Eighty-one tests passed while any inserter unloading any belt
  stalled at the first gap in the flow. It was only found by deliberately placing the item four
  tiles away and making it walk. **When a new subsystem moves things, the test to write first is
  the one where the work arrives from a distance rather than being handed over.**

- **The wake signal has a radius of exactly one block, and nothing says so.**
  `updateNeighbourForOutputSignal` walks the six positions touching the block entity that changed.
  Every machine here is woken by that, and every machine here happened to have its work land next
  door — so "a `setChanged` reaches whoever cares" reads like a general fact right up until
  something reaches further than it does. The long-handed inserter is the first thing in the pack
  to have both of its ends outside that radius, and it fails *silently and late*: it works while
  items keep arriving and stops dead at the first gap, which is the same shape as the belt bug
  above and was found the same way, by making the work arrive from a distance instead of handing it
  over. **Anything that reaches past its own neighbours has to answer this question before it is
  written**, and the answer is in `InserterBlockEntity`.

- **Asking for a capability in an unloaded chunk loads it.** Check `level.isLoaded` first — not as
  an optimisation, but so a network at the edge of the loaded world does not drag chunks in.

- **Grouping a list correctly and sorting it wrongly looks exactly like not grouping it.** The
  crafting panel's tabs were one per mod, and the fix everyone could see was the tab strip: derive
  `group` from Factorio's four crafting-menu categories instead of its eleven item types. Doing
  only that would have changed the tab labels and *nothing a player would notice*, because inside
  each tab `ClientRecipes` sorted on the recipe id — and ids are namespaced, so every tab would
  still have arrived in mod order. A grouping is two decisions, the buckets and the order within
  one, and only the first of them is visible in the code that does the bucketing. **When a list
  reads as grouped by the wrong thing, check the sort before believing the grouping is the bug.**

- **An empty string is a valid group, so a stale lookup table is a layout choice.** `GROUP_BY_TYPE`
  fell back to `""` for anything it did not recognise, and `""` is exactly what a recipe with no
  opinion sets — so a category the table had never heard of landed silently in the panel's
  "Ungrouped" tab, indistinguishable from a pack author choosing not to group it. The generator now
  raises rather than defaulting. The shape generalises: **wherever "no opinion" and "I could not
  work it out" have the same representation, the second one has to throw.**

What is deliberately missing
----------------------------

**An accumulator cannot discharge.** `PowerNetwork` collects supply from endpoints that did not want
energy, so a battery would charge and never feed the grid. It wants a third case; there is no
accumulator until milestone 3.

**A network that moved nothing is re-checked every ten ticks rather than woken exactly.** It hears
about members and machines appearing the moment they do, but "a generator elsewhere filled up" is a
fact about a handler in another mod that owes us no signal.

**A long-handed inserter with power and nothing to do costs one look a second**, for the reason at
the top of this file: the wake signal has a radius of one block and its ends are two away. Every
other machine in the pack sleeps for free. Two things would remove it and neither exists — a hook
that fires when the block entity at a watched position changes, or a reason to believe every source
a long arm reaches is one of ours and can be made to announce.

**No brownout.** PLAN.md wants a machine whose buffer cannot refill to run *slower*; ours stops.

**No pipeline length limit.** Factorio caps a fluid segment at 320 pipes and its tooltip says
`6/320`; ours says `6 pipes` because we enforce nothing. Adding the cap is a real gameplay change —
refusal to connect, not just a number — if it is ever wanted.

**Personal crafts pay at the end, not the start.** Facrafting's `CraftTicker` checks affordability
every tick and consumes on completion, so moving ingredients away mid-craft stalls the job rather
than losing it. It looks like a queue that stopped for no reason, and has been mistaken for a bug.

**Factorio's recipe picker is a modal** anchored to the machine; ours is a persistent column beside
the screen. The modal is the more faithful one. A Facrafting change, and it wants Yannic's eye.

**A belt does not load a chest.** Deliberate and Factorio-faithful: a belt running into a container
backs up, and taking things off a belt is what inserters are for. It is one method — `BeltRun`'s
hand-off — if it is ever wanted the other way. `belt_does_not_load_a_chest` pins it.

**Crouching stops a belt carrying you**, which Factorio does not do — there a belt has you whatever
you do. It is in for the Minecraft reflex: without it, placing a machine beside a working belt means
being carried off mid-click. One line in `BeltBlock.stepOn` if it is ever unwanted.

**A dropped item entity is carried erratically.** `ItemEntity.tick` only calls `move` — and so
only reaches `stepOn` — when the item is airborne, has horizontal momentum, or the tick count plus
its entity id is divisible by four, so something *resting* on a belt is pushed on a fraction of
ticks and at a phase that depends on its id. A player is carried properly; a thrown item is not.
See the marginal-test note in the job section, which is the same fact wearing a red X.

**A belt does not turn you as it carries you.** An entity on a corner is pushed the way that block
faces, so going round a bend on a belt is two straight shoves rather than an arc. Items do curve.

**Two belt tiers meeting is two runs, not one.** Correct — Factorio's transport lines split at a
tier change too — but there is only one tier so far, so it has never been looked at.

**Client and server belt runs can differ at a chunk edge**, because a client only has the belts in
its loaded chunks and a run is built from whatever belts are there. What that costs is a belt at the
very edge of the loaded world appearing to back up when it is not. Nothing is out of step where a
player can see it, and a chunk arriving re-seeds that block's items from the block entity.

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
