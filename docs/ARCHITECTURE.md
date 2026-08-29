Architecture
============

The rules a machine is built to, and the patterns worth copying. These outlive whatever is in
`NEXT.md`. `PITFALLS.md` is the companion: the ways of getting them wrong that still compile.

Footprint is identity. Height is ours.
--------------------------------------

Non-negotiable #1 makes ids, ingredients and craft times Factorio-correct from the first commit,
because they live in world saves and in the player's head. **A footprint is the same kind of fact**
— 3×3 is why an assembler column spaces the way it does, 5×3 is why a boiler feeds a *row* of
engines. Getting it wrong invalidates every blueprint a player carries in their head, and fixing it
later moves every machine in every world.

Height is the opposite: Factorio is two-dimensional and has no opinion, so height is ours, free to
change, and where to spend effort making a machine look like something.

Footprints live in `data/mapping.json` as `size` and `tools/check_models.py` holds every machine's
cells to them. Heights live in the shape class beside the models.

One tile is one block
---------------------

Not really a choice: the belt, the inserter and the pipe are one tile in Factorio and one block
here, and everything else has to agree. What 1:1 buys — layouts transfer; Factorio's own one-tile
walkways become one-block corridors a 0.6-wide player walks down; ratios stay legible.

### Walkability, in numbers

| | |
|---|---|
| player height | 1.8 — a corridor needs 2 of headroom |
| player width | 0.6 — a 1-block gap is walkable, diagonal gaps are not |
| step height | 0.6 — **anything colliding at 0.5 or below is walked straight over** |
| jump height | ~1.25 — a 1-block machine can be jumped onto, a 2-block one cannot |

**A machine you can walk across beats a machine you walk around.** A Factorio player tiles machines
with no gaps because in Factorio you can always walk round the far end of the field; nine 3×3
machines two solid blocks tall is a wall that seals the player out of their own base.

| height | used for |
|---|---|
| **1.0** | the wall around the outside. One jump, and the only climb in a field of any size |
| **0.75** | the floor inside the wall. Under the 0.6 step, so crossing is walking |
| **2.0** | whatever the machine puts in the middle — the one thing you walk around |
| **0.5** | crossed without even a jump: the electric drill, solar panels, and **belts** |

Two consequences. The upper storey is mostly air, so a 3×3 machine two blocks tall is ten blocks
and not eighteen — which is why `MachineShape` takes a set of cells rather than a box. And the tall
part goes in the middle, so tiled machines stand their obstacles apart and leave lanes.
`assemblers_tile_walkably` and `power_machines_tile_walkably` walk those lanes.

**Collision and silhouette are allowed to disagree** — `PolePart` gives the crossarm an outline and
no collision — but for something meant to be walked across, the two agreeing is the point.

The multiblock mechanism
------------------------

`multiblock/` — `MachineCell`, `MachineShape`, `MachineParts`, `Boxes`, `Multiblock` — is copied
into the subsystem mods and `NeoProgressiveAutomation`, and `tools/check_duplicated.py` holds the
copies byte-identical (`--sync` pushes the original out; the copies are never edited). It is
`SmallElectricPoleBlock` with two more axes. Read `MachineShape` and `Multiblock` and you have it.

- one block id, one item, an `IntegerProperty part` on every block, the anchor found by arithmetic
  rather than a lookup — no block entity on the other cells;
- one `updateShape` rule is the whole teardown, which is why a shape's cells must be orthogonally
  connected, which the constructor enforces;
- capabilities are registered against the **block**, not the block entity, so any cell answers —
  that is what lets a pole supply a machine whose middle is out of range, and why `PowerNetwork`
  reduces endpoints to distinct handlers;
- **ports** name a cell and a face, so a boiler's steam leaves one block and an engine takes it at
  the open ends of its spine;
- geometry is stated once per machine and read by the model provider, the `VoxelShape` and the item
  model. `MachineParts` is the shared shell so the machines read as one family — Yannic has said
  that still wants refinement, and it is one file.

The belt, if you have to touch it
---------------------------------

Read `nauvis_logistics/.../belt/` in this order and the whole thing falls out:

| | |
|---|---|
| `Belts.java` | the numbers, and why distances are integer sixty-fourths of a block |
| `BeltLane.java` | **the idea.** Items are stored as the gaps between them, so a flowing belt writes one number a tick and a jammed one writes none |
| `BeltRun.java` | one line: its blocks, its two lanes, its tick, how it hands to the next line, and — `announceArrivals` — how anything beside it hears an item turn up |
| `BeltLines.java` | every run in a level, and how lines are cut and joined |
| `BeltAccess.java` | how everything else meets a belt, and why giving and taking are two rules |
| `BeltShape.java` | how a corner knows it is one, and why there are two rather than eight |
| `BeltBlock.stepOn` | why standing on a belt carries you, and why that is not `entityInside` |
| `BeltBlock.useItemOn` | a belt in hand points the belt you click on the way you are facing |
| `client/BeltRenderer.java` | the items you can see |
| `texture-workshop/make_belt_textures.py` | the art, and why the tread scrolls at 1.875 tiles a second |

Four things are load-bearing for anything built on top:

- **Items are pinned to a block and an offset into it**, never to a distance along a run. That is
  what makes cutting, joining, lengthening and turning safe; `belt_survives_being_cut` asserts it.
- **The client runs the same simulation**, so a belt full of items costs no network traffic. Only
  two things are ever sent — an item put on from outside and one taken off — because they are the
  only two a client cannot work out. **Anything added to belts must keep that property.**
- **A run is awake while it has items, not while it is moving**, because `BeltLane` makes a jammed
  belt cost the same as an empty one.
- **A belt is the one subsystem that has to announce arrivals**, and the reason is not "it has no
  block entity". `FluidNetwork` and `PowerNetwork` move things without one and are audible because
  they **push**, and the buffer they fill fires its own callback. A belt deliberately never pushes —
  a belt running into a chest backs up, which is Factorio's rule and the reason inserters exist —
  so `BeltRun.announceArrivals` is the only thing that can speak for it. Generally: *a subsystem
  that moves things and never pushes them into the consumer has to announce, because nothing else
  will.*
- A closed ring of belts is one run that wraps, and a block state change does not touch the graph —
  turning a belt leaves its block entity alone, so `BeltLines.beltTurned` is a third way in.

`BeltLines` also owns the tick of anything that carries items *along* a belt line — the splitter is
ticked there rather than by a scheduled block tick, because the client simulates belts and a
scheduled tick is server-only. A machine *beside* a belt schedules ticks like any other machine.

The patterns worth copying
--------------------------

**Sleeping.** No `BlockEntityTicker` anywhere: a registered ticker runs whether or not there is
work. Every machine schedules its own block tick while it has something to do and stops when it
does not. Four ways a machine learns it has work again, and one usually needs more than one:

- its own inventory changed (`onContentsChanged`);
- **electricity or steam arrived** — `MachinePower`, `InserterPower` and `SteamTank` exist only to
  carry that callback. A machine that ran dry has stopped scheduling ticks, so nothing it does can
  restart it; the wake must come from whatever filled the buffer;
- a *neighbour's* block entity changed (`onNeighborChange`, which every `setChanged()` reaches on
  all six sides). Filter on the `neighbor` position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, then delete the sleep logic and watch the test go red.

**One object per connected thing, three times over.** `PowerNetwork` for the grid, `FluidNetwork`
for pipe runs, `BeltRun` for belt lines. Members join and leave; the network ticks once however
many members it has; one that moved nothing drops out of the active set. Read
`nauvis_fluids/.../pipe/` first — a pipe connects to the six blocks it touches, which
`neighborChanged` already reports, while a pole reaches 7.5 blocks and needs a spatial index.

**Capabilities are how mods meet.** No subsystem mod compiles against another. FE through
`Capabilities.Energy.BLOCK`, steam through `Capabilities.Fluid.BLOCK`, items through
`Capabilities.Item.BLOCK`. `SteamTank` looks its fluid up by registry id rather than importing it,
so `nauvis_power` still loads with `nauvis_fluids` absent.

**Sided capabilities carry meaning.** A steam engine offers steam only on the two faces along its
axis, which is what makes its facing matter and a pipe refuse its flank. *Which faces answer* and
*which way the machine looks* are two registrations with two tests — breaking one leaves the
other's test passing.

**A renderer that draws outside its own block has to say so.** `getRenderBoundingBox` defaults to
the one block, and geometry past it is frustum-culled with no error and nothing in the log. The
wires between poles hit this exactly. See `API-26.2.md`.

**Multi-blocks are vanilla's job.** `SmallElectricPoleBlock` is four blocks on one `PolePart`
property the way a door is two: refuse placement without headroom, place the rest from
`setPlacedBy`, and let one `updateShape` rule be the whole teardown.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back. `commit = false` turns the same method into the simulation, so "can I?" and "do it"
cannot drift apart.

**One interface.** Facrafting owns the crafting UI and its panel attaches to any container screen;
machine screens grow out of it rather than sit beside it. Palettes are duplicated per mod rather
than shared, because a shared base in Facrafting would make these mods require it and kill the
`*_standalone` recipes that exist for its absence.

Facrafting learns rules, not facts
----------------------------------

Facrafting is a general mod this pack happens to be built on, so nothing in it may know what
Factorio is. Four forms of the seam, in increasing strength:

1. **A rule plus data.** The rule is "a tab's place is the place of the first recipe in it", true of
   any pack; the fact is that Factorio's strip reads Logistics, Production, Intermediate products,
   Combat, and it lives as the *key order* of `GROUP_BY_CATEGORY` in this repo's generator.
2. **A suggestion Facrafting derives.** `RecipeTabs.suggestedMode` counts how many recipes carry a
   `group` rather than being told which mode this pack wants; `FacraftPanel.modeChosen` lets the
   player outrank it until logout.
3. **A pack config**, for a fact that is neither a rule nor in the recipes — see
   `nauvis/pack/config/`. NeoForge rewrites a config that does not match the mod's spec, comments
   included, so the reasoning lives in that directory's README rather than in the toml.
4. **A hook, when the rule cannot be expressed as data at all.** "Unlocked" is not a property a
   recipe has, it is a question somebody else answers — so Facrafting gained an interface
   (`RecipeLock`), a place to install one (`RecipeLocks`) and four calls to it. With nothing
   installed everything is unlocked, which is what every pack had before. **A hook's default is
   always the old behaviour.**

**When a change to Facrafting needs a fact about Factorio, the change is in the wrong repo.**

The mirror on this side: `nauvis_research` declares Facrafting **optional** and still installs the
hook, because the installer lives in `compat/facrafting/` behind a `ModList` check and the JVM
resolves the reference only when that branch runs. Same trick as the Jade plugins, same reason —
the standalone bench recipes exist for Facrafting's absence.

Two more seams worth knowing
----------------------------

**The lab does not know what a technology is.** It asks the world what is being researched, is
handed a list of packs, takes one of each and reports a unit. Adding the tech tree changed two
handovers in `LabBlockEntity` — which packs, and how long a unit takes — and not a rewrite.

**The research screen paints and does not decide.** `ResearchScreen` is handed cells and arrows;
everything checkable — every node right of every prerequisite, no two in one cell, every technology
placed once, every arrow drawn, and the same tree laid out the same way twice — lives in
`TechnologyLayout`, which is common code asserted by `technology_layout_is_sound`. Columns are the
longest path from a root, so a node sits right of *every* prerequisite; rows are settled by
barycentre with the tree's own order as the tiebreak. Nothing is authored — there are no
coordinates in the data files and there must not be.

Dependencies
------------

The look-at readout is Jade — `maven.modrinth:jade:${jade_version}`, `compileOnly` in the subsystem
mods and `runtimeOnly` in the pack. Its plugin classes load only when it is present, so nothing has
to declare it required. PLAN.md's section covers the rest.

**Licences are not a decision point for including or depending on a mod here.** The pack is not
monetised and ships the way thousands of CurseForge packs do; weigh version support, API shape and
maintenance instead. This does not extend to *copying* — CLAUDE.md's rule stands and is separate.

**KubeJS** is still on 26.1.2 and in beta, one Minecraft version behind us. LGPL-3.0, so no
obstacle once it ports. Where it would help is pack policy — stripping vanilla recipes is one
script against hundreds of condition-false JSON files. Where it cannot help is machines:
non-negotiable #3 requires each subsystem mod to stand alone, so block entities, ticking and
capability handlers stay Java.
