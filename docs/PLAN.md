The plan
========

Recreate Factorio as a Minecraft modpack: mine, smelt, automate, research, defend, launch a
rocket. Minecraft 26.2 / NeoForge 26.2.0.59. First-party by default — the pack must stay small
and load fast, and the tech ecosystem has not reached 26.2. See the third-party policy below.

Read `../CLAUDE.md` first. The five non-negotiables there govern everything below.

Settled decisions — do not re-litigate
--------------------------------------

**Scope is the whole game, rocket included.** Oil, trains, circuit network, pollution and
biters, rocket launch. Chosen knowingly as a multi-year plan.

**Power is Minecraft FE, buffered per machine.** Explicitly chosen over a first-party grid
with a global satisfaction ratio, so third-party cables keep working. Brownouts are
approximated per machine: one whose buffer cannot refill runs slower. Power poles survive as
FE cables with a wide connection radius, so a base still looks like Factorio.

**Vanilla progression is largely replaced.** Vanilla crafting is stripped back so the
Factorio tree is the only road forward. This lives in the `nauvis` mod or the pack datapack,
never inside a subsystem mod — every mod must stay useful standalone.

**Shortcuts are wanted.** Something playable that is only close enough beats waiting for the
authentic version. See the shortcut table below.

**First-party by default, with two openings.** Build it here unless a third-party mod on a
current 26.2 build already does the job — then take it rather than rewrite it. The bar is
*adopting beats rewriting*, not *identical to Factorio*: a mod whose behaviour is close and
whose recipes are wrong is still a candidate, because recipes are the cheap part to fix. And
read outdated mods freely for architecture, the way Create is already read for belts. A mod
stuck on 1.21.1 is still the best documentation of how a problem was solved; it just cannot
be shipped.

**Adopting a mod means overriding its recipes.** Any mod taken in brings its own progression,
tiered off vanilla materials and balanced by hand — exactly what non-negotiable #1 forbids.
The fix is mechanical and the generator already does most of it: `data/mapping.json` gains
entries pointing at the third-party item ids, `gen_recipes.py` writes Factorio-correct recipes
into `data/<their_ns>/recipe/`, and the pack datapack loads above the mod so those win. A
recipe whose `neoforge:conditions` cannot be met never loads, which is how the ones with no
Factorio equivalent get removed. Budget this per mod — it is not free, but it is a script and
a datapack, not a rewrite.

Third-party mods
----------------

Surveyed 2026-08-25 against Modrinth. Redo it when a milestone comes up, not before — the
answer changes only when someone ports something.

**Nothing on 26.2 does Factorio's oil, or anything else in the chemistry chain.** Searching
26.2 for `oil` returns one worldgen structure pack. Storage and transport do exist — Fluid
Tank has both, see below — but refining, cracking and the chemical plant have no third-party
answer, and those are the part milestone 4 is actually about. So the barrels shortcut stands:
it deletes a subsystem nobody has built for this version. This resolves the fluids open
question in `MAPPING.md`.

The whole tech ecosystem stopped at 1.21.1: Create, Mekanism, PneumaticCraft, Modern
Industrialization, Industrial Foregoing. Immersive Engineering has no current releases at all.
These are the ones to **read**, not to depend on.

**Applied Energistics 2 is the one to watch.** It is on 26.1.2 and still shipping betas, one
Minecraft version behind us. Nothing else serious is close.

What does exist on 26.2 is generic plumbing rather than chemistry: `pipez` and `tesseract`
(transport), `classic-pipes`, `modular-routers`, `tiny-pipes`, `enhancedquarries`,
`energized-power` (FE machines) and `large-fluid-tank` (tanks and fluid pipes).

**Licence is not the blocker it looks like.** The pack ships on CurseForge as a manifest —
CurseForge serves each jar from its own project page, so the pack never redistributes anything
and an All-Rights-Reserved mod is as includable as an MIT one. Two things this does *not*
cover: bundling jars into this repo (never — `reference/` is gitignored for exactly this
reason), and adapting someone's code, which still follows their licence.

Two mods are accepted candidates, both with current 26.2 builds:

**Fluid Tank** (`large-fluid-tank`, `fluidtank`, 26.20.1) — the jar is in `reference/mods/`.
The one mod on 26.2 doing real fluid handling, so it is worth reading for the NeoForge 26.2
fluid API whatever is decided about shipping it. Two costs to weigh, neither about licence:
it declares `modLoader = "kotori_scala"` and so drags in a Scala loader, and it registers 37
blocks and 16 recipes of wood/stone/iron/gold/diamond tiering against Factorio's single
storage tank. Adopting it means overriding all 16 and hiding most of the 37.

**KubeJS** (`kubejs`, LGPL-3.0) is **on 26.1.2 in beta, not 26.2** — one version behind, like
AE2, and `rhino` with it. Worth watching, because scripting is a real alternative to writing a
mod *for pack policy*: stripping vanilla recipes at milestone 3 is one script against hundreds
of condition-false JSON files, and it subsumes the Item Obliterator idea. It cannot touch the
subsystem mods — a script is not a mod, and non-negotiable #3 requires each to stand alone, so
block entities, ticking and capabilities stay Java.

**Energized Power** (`energized-power`, 3.0.0+26.2.x, MIT) — FE machines and generators.
Overlaps `nauvis_machines` and `nauvis_power` heavily, so the question is whether it replaces
those milestones or duplicates them. Not yet evaluated in the way Fluid Tank has been.

Note the licence conflict on Fluid Tank if its code is ever adapted rather than shipped: the
GitHub repo is MIT, the shipped jar's `neoforge.mods.toml` says `All rights reserved`. Ask the
author before copying a line of it. Reading it is fine either way.

The mods
--------

Three already exist in their own repos; Crumbling Ore is released and its ids are permanent.
Eleven are built here.

| Mod id | Owns | Items |
|---|---|---|
| `facrafting` | the timed crafting model | — |
| `neoprogressivematerials` | intermediate products | 18 |
| `crumblingore` | ore depletion | — |
| `nauvis` | pack policy, vanilla replacement, raw resources, terrain | 16 |
| `nauvis_logistics` | belts, inserters, splitters, chests, robots | 28 |
| `nauvis_machines` | assemblers, furnaces, modules, beacon, radar | 18 |
| `nauvis_power` | boiler, steam engine, solar, accumulator, poles | 13 |
| `nauvis_research` | labs, science packs, tech gating | 7 |
| `nauvis_mining` | the two mining drills | 2 |
| `nauvis_fluids` | barrels then pipes, oil, chemistry, nuclear | 33 |
| `nauvis_trains` | rails, locomotives, wagons, signals | 9 |
| `nauvis_circuits` | combinators, wires, lamps, speakers | 7 |
| `nauvis_military` | weapons, armour, turrets, walls, pollution, biters | 54 |
| `nauvis_rocket` | silo, rocket parts, satellite, space science | 4 |

Five entries are skipped outright — blueprints, the deconstruction planner and the axes have
no Minecraft analogue.

The workspace
-------------

Project Nauvis is a **Gradle multi-project build**. Every new mod is a subproject here; each
still produces its own jar with its own mod id and is publishable standalone. The three
sibling mods stay in their own repos and are pulled in with
`includeBuild("../NeoProgressiveMaterials")` and friends, so one `./gradlew runServer` puts
the whole pack on the classpath and cross-mod edits are possible in one pass.

**Settled: ModDevGradle does not fight composite builds**, so the `run/mods` fallback is not
needed. One thing has to be spelled out — Gradle matches an included build by its project name,
which defaults to the directory, while each jar is named after its mod id, so `settings.gradle`
carries an explicit `dependencySubstitution` per sibling. The siblings are `runtimeOnly`: there
is no compile-time dependency on any of them and there must not be one.

Configuration cache is off here, though all four siblings enable it. Composite build plus
ModDevGradle plus configuration cache is the untested corner and a milestone is the wrong time
to find out.

Milestones
----------

Each milestone is a playable state, not a checklist. Counts are *new* items, transitive over
the recipe graph, with vanilla stand-ins excluded from the registration cost.

### 0 — Foundation · **done**

Gradle workspace, composite build proven, the `nauvis` pack mod, and the recipe generator.
A dev server loads all five mods; `tools/gen_recipes.py` turns the mapping plus Factorio's dump
into 341 files across 12 mods, and `:nauvis:check` fails the build if a timed recipe on disk
disagrees with it.

Two things arrived alongside and were not in the original plan, both of which pay for
themselves immediately: a **headless gametest harness** (`:nauvis:runGameTestServer`), which is
how anything about behaviour gets verified without a person watching, and **datagen** for
models, language and loot tables.

### 1 — First factory · 11 new registrations · *done*

`assembling-machine-1`, `burner-inserter`, `inserter`, `iron-chest`, `boiler`, `steam-engine`,
`small-electric-pole`, `pipe`, plus intermediates already owned by
`neoprogressivematerials`. `stone-furnace` and `wooden-chest` are vanilla.

Chest → inserter → assembler → inserter → chest, burning coal. **This is the whole point of
the pack and it costs eleven items.** Get here fast.

**`assembling-machine-1` is done**, in a new `nauvis_machines` subproject: a recipe selector
over one input inventory, running Facrafting's timed recipes, published to automation as a
`ResourceHandler<ItemResource>` that takes ingredients in and gives results out. It sleeps
when idle and wakes on a change to its inventory, its recipe or a neighbour. It can be pointed
at a recipe, fed and emptied entirely by hand, so milestone 1 is playable before inserters
exist. Eight gametests cover it, including that the craft takes Factorio's exact ten ticks,
that a full output slot stalls the machine rather than voiding the ingredients, and that
breaking it gives everything back. **`nauvis_machines` takes a
compile-time dependency on Facrafting** — the one in the pack — because a machine that runs a
`FacraftRecipe` has to be able to name the type. The arrow points one way and there is no
cycle. Still missing: a screen, which is the half that needs eyes.

**`burner-inserter` is done**, in a new `nauvis_logistics` subproject: a directional block that
takes one item from behind and gives it to whatever is in front, both through
`Capabilities.Item.BLOCK`, burning coal to do it. It works with a vanilla chest, a furnace, an
assembling machine or another mod's machine, because it asks the block rather than knowing what
it is. Six gametests cover it.

The interesting half is that **an idle inserter costs nothing**, with no polling and no ticker.
Its work arrives in somebody else's inventory, and the answer turned out to be already in the
game: every `BlockEntity.setChanged()` reaches all six neighbours as `onNeighborChange`, so a
chest gaining an item tells the inserter beside it for free. See the section in `API-26.2.md`;
it is asserted by a test, and by a second test that the wake is filtered down to the two
neighbours an inserter can actually use.

**The electric `inserter` is done too**, now that there is a grid to plug it into. It shares
everything with the burner except what pays for a swing — the two block entities differ by two
methods — and it swings in 24 ticks against 30, draws two FE a tick, and is a paperweight without
a pole in range. That last part is asserted, because an electric inserter that ran on nothing
would be strictly better than the burner for free.

**`iron-chest`, `pipe`, `boiler` and `steam-engine` are done.** The chest is a plain vanilla
`Container` on vanilla's four-row screen, which is the one place in this pack where being a
Container buys more than a capability handler would. The pipe is three milestones early because
the boiler and the engine are both paid for in pipes and an ingredient with the wrong id is not a
shortcut this pack takes. Coal now goes into a boiler, the boiler makes steam, and a steam engine
turns steam into FE — and the whole chain sleeps from the far end, which is asserted rather than
hoped for.

**`small-electric-pole` is done, and with it the electric network** — the second of the two
decisions that are expensive to reverse. A `PowerNetwork` is one object holding its member poles
and `BlockCapabilityCache` handles on the machines they reach; it ticks once however many poles it
has, and drops out of the manager's active set the moment it moves no energy. Poles join and leave
it and never tick at all. See the electric network note below, which now describes something that
exists.

**The assembler and the electric inserter both spend it.** Coal goes into a boiler at one end and
an assembling machine eight blocks away runs, across four mods that do not compile against each
other — `power_reaches_a_machine`, in the pack mod, is that claim in one test. Milestone 1 is
closed: chest → inserter → assembler → inserter → chest, on a grid.

### 2 — Belts · 3 new · *done*

`transport-belt`, `splitter`, `long-handed-inserter`. Small item count, by far the largest
engineering lift. See the belt note below — it describes something that exists now.

**`fast-transport-belt` came with it and closed the milestone.** A tier was always the test of
whether the belt's architecture was right, because a belt line is the one thing in this pack a
player edits constantly: a run is one object keyed on the *block*, so two tiers are two runs that
hand off at the seam without a line of code saying so, and speed is a constant on a subclass, so a
tier is a class, a palette and an entry in `data/mapping.json`. One block entity type covers every
tier there will ever be.

**And a belt in hand puts that belt there, pointing the way you are facing**, whichever tier it is —
Factorio's fast-replace, and the last piece of milestone 2. One rule with two implementations: the
same belt is a state change, which keeps the block entity and so keeps its load for free, while
another tier is a new block, which the graph hooks already handle. What does not come free is the
load, which is lifted before the swap because replacing a block spills it, and the payment — one
belt off the stack, the old one handed back.

**No `underground-belt`, and no `pipe-to-ground` in milestone 4.** Factorio needs them because it
is flat: two belts that must cross have nowhere to go but under. This pack is the same game with a
Y axis, so a belt crosses another by changing level, which is a thing a Minecraft player already
knows how to build and does not need an item for. Both are marked `skip` in `data/mapping.json`
with the reason, so `gen_recipes.py` leaves them alone rather than reporting them missing forever.
The three tiers of underground belt go with them — nothing else in the recipe graph uses any of the
four, so dropping them leaves it closed.

### 3 — Research · 15 new

`lab`, `science-pack-1`, `science-pack-2`, `assembling-machine-2`, `steel-furnace`,
`solar-panel`, `accumulator`, `medium-electric-pole`, and `steel-plate` / `battery` /
`sulfur` in materials. **The vanilla-replacement datapack lands here** — once research gates
progression there is somewhere for stripped vanilla recipes to go. It has: `data/removals.json`
and `tools/gen_removals.py`, shipped as a built-in datapack that is on by default. It is four
recipes long and the rule is why — *a vanilla recipe is removed only when the pack can already do
that job*, enforced by the build rather than remembered — so it grows with the items rather than
ahead of them.

**The tree itself is done and is all 216 finite technologies**, not the fifteen items above.
That is deliberate and is the same rule as recipes: a technology's cost, its prerequisites and
its unlocks are identity, they live in world saves, and generating the whole graph from Wube's
own data costs no more than generating a tenth of it. Most of them unlock items no mod registers
yet, and simply do nothing until one does. What is left of this milestone is the items.

### 4 — Oil, in barrels · 11 new

`pumpjack`, `oil-refinery`, `chemical-plant`, `empty-barrel`, `storage-tank`,
`plastic-bar`, `advanced-circuit`, `engine-unit`, `science-pack-3`, `electric-mining-drill`.
No pipe network yet — oil moves as barrelled items, which is canon in Factorio and deletes an
entire fluid subsystem from the critical path. Blue science becomes reachable without a pipe.

### 5 — Military and pollution · 8 new
### 6 — Trains · 6 new
### 7 — Circuit network and robots · 12 new
### 8 — Rocket · 14 new

Those eight milestones reach **89 of 214 items** — the critical path. The remaining 125 are
breadth: tier-2 and tier-3 variants, nuclear, armour, artillery, logistic chests. They are
cheap once their tier-1 exists and get added alongside whichever milestone owns them.

Shortcuts, and what they defer
------------------------------

| Subsystem | Ship this | Rewrite to this later |
|---|---|---|
| Assembler | recipe selector + one input inventory, running Facrafting's timed recipes | fixed recipe, per-ingredient buffers, module slots, tiers |
| Inserter | directional block moving stacks between neighbours on a timer | swing animation, per-item hand, filter/stack/long variants |
| Belt | BlockEntity per block passing items along | transport lines, items at positions |
| Power | FE per machine — burner generator, solar, FE-battery accumulator | unchanged; FE *is* the chosen model |
| Poles | FE cables with a wide connection radius | unchanged |
| Research | ~~lab consumes packs, grants vanilla advancements~~ **rejected — advancements are per player and research belongs to the world.** Shipped instead: the real model, on a `SavedData`, with a list for a screen | the tech tree drawn as a tree, with a layout, pan and zoom |
| Oil | **barrels as items, no pipes at all** | fluid network, pipes, tanks, pumps |
| Trains | vanilla minecarts and chest minecarts | locomotives, wagons, signals, schedules |
| Biters | vanilla hostiles + per-chunk pollution raising spawn rate near the factory | nests, expansion, evolution factor |
| Rocket | silo consumes 100 rocket parts, plays a launch, grants the advancement | satellite, cargo, space science loop |

The belt note
-------------

Belts are the one architectural decision that is expensive to reverse, which is why they get
their own mod: the rewrite must be containable. **The shortcut this note used to allow — a
BlockEntity per belt block — was not taken, and this now describes what shipped.**

**A transport line is one object; items are positions on it.** That is how Factorio does it, it is
where Create arrived —
`reference/create-src/src/main/java/com/simibubi/create/content/kinetics/belt/transport/`, read for
the architecture and reimplemented, because Create's is welded to its kinetics framework and its
assets are All Rights Reserved regardless — and it is what `BeltRun` is. A run ticks once however
long it is, an item crosses it at the belt's speed rather than at one block a tick, and a belt block
holds nothing at all. Runs with something on them are ticked and the rest are not visited, so an
empty base costs nothing and a jammed one costs nearly nothing.

Two things followed from it that were not the point and turned out to matter more than the tick
count. **The client builds the same runs from the same block states and advances them with the same
code**, so visibly moving items cost no packets — only the boundary with the rest of the world, an
inserter putting something on or taking something off, has to travel. And **a tier is a class**:
because a run is keyed on the block and speed is a constant on a subclass, the fast belt was one
file, one palette and one line in a registry, and two tiers meeting is two runs that hand off at the
seam without a line of code saying so.

The electric network note
-------------------------

Poles are the other decision that is expensive to reverse, for the same reason belts are, and the
belt note's sentence is the whole design with the nouns changed: **an electric network is one
object; poles are members of it.**

The trap is that a pole looks like a block and is not. Every machine in this pack can sleep because
it can answer "have I got work?" by looking at itself. A pole cannot — it is one node in a graph
whose job is to make other blocks reachable — so the thing that ticks has to be the network, once,
rather than each pole. A pole that ticks is N ticks a second for N poles and an energy packet that
takes N ticks to cross them; a pole that rescans its supply area is worse.

So: one object per connected network, holding its member poles and `BlockCapabilityCache` handles
on the producers and consumers at its edges. It ticks once, driven by a per-level manager, and a
network with no producer or no hungry consumer does not tick at all. Poles join and leave it rather
than driving it. Machines find poles when they are placed rather than poles scanning for machines,
because a scan must never be per-tick. The graph is derivable from block positions, so it is not
saved — rebuilding on chunk load is cheaper than invalidating a cache that spans one.

**`reference/mods/energizedpower-*.jar` is the reference, and the counter-example.** It is the one
FE mod on 26.2, it is MIT, and it does the opposite: `CableBlock` registers a ticker so every cable
ticks, and every cable holds its own copy of the network's producer and consumer maps. Read it for
what the endpoints look like and how connection changes propagate. Do not copy its tick model —
non-negotiable #5 is exactly the constraint it does not have.

**Built, in `nauvis_power/.../grid/`.** `PowerNetwork` is the object; `PowerNetworkManager` owns
one per level and is driven by a single `LevelTickEvent.Post`.

**A pole is a true multi-block**, on the same `MachineShape` a boiler is: four blocks for the small
one, five for the medium, and two-by-two by six — twenty-four blocks — for the big one and the
substation. Placement refuses unless the whole thing fits, `setPlacedBy` puts the rest in, and one
`updateShape` rule — a cell whose neighbours are not its machine's other cells turns to air — is the
entire teardown. Only the foot carries the block entity and only the foot drops the item, so
breaking any part gives back exactly one pole. A one-block pole read as a fence post; the other way
to get height, a `VoxelShape` four blocks tall on a single block, has the renderer cull the whole
thing the moment its one real block leaves the screen.

It is **climbable** (through `minecraft:climbable`, so a datapack can say otherwise) and its
crossarm has **no collision** — an arm you cannot see, three blocks over your head, that catches you
as you walk past is worse than no arm at all. And **wires draw themselves**: the network already
knows which poles can see each other, so it pushes that set to each pole and the client renders a
sagging line between the heads. No coil, no connectors, nothing for the player to say twice.
**`reference/ImmersiveEngineering-src`'s `wooden_post` is the reference** for that shape — base
block holds the logic, dummies above, break one and the whole thing goes. Read and reimplemented,
not copied; IE's licence permits drawing on it with credit and requires visible source, which this
is. Its assets were not touched.

Two things about the network were not obvious in advance and are worth knowing before changing it:

- **A machine cannot find a pole, so the pole finds the machine.** Non-negotiable #3 forbids
  `nauvis_machines` from knowing what a pole is, so discovery goes the other way, through
  `Capabilities.Energy.BLOCK`. The hard part is the trigger for a machine built *later*, two blocks
  from a pole and in nobody's neighbourhood: `BlockEvent.NeighborNotifyEvent`, which fires for any
  block placed or broken by any means, pre-filtered by a map of which poles reach into which chunk.
  A capability listener on all 125 supply positions of every pole is the exact alternative and
  would cost over a million weak references in a base of ten thousand poles.
- **Poles are bucketed into 8-block cells**, which is more than the 7.5 wire reach, so two poles
  that can see each other are always within one cell of each other on every axis. That turns the
  graph walk a split needs into a constant per pole instead of a 15×15×15 scan, and it is the
  difference between breaking a pole in a big network being free and being a visible stutter.

What it cannot do yet is discharge a battery: a network collects supply by asking every endpoint
that did *not* want energy, so an accumulator will charge and never feed the grid until that grows
a third case. And a network that has moved nothing is re-checked every ten ticks rather than woken
exactly, because "a generator elsewhere filled up" and "a machine got hungry" are facts about
handlers in other mods that owe us no signal.

Two radii, not one. Factorio's small pole reaches 7.5 blocks to another pole and supplies a 5x5
area, and keeping both is what makes a base look like a Factorio base rather than a line of cables.
Neither number is identity, so both are yours to tune; the id and the recipe are not.

The look-at readout
-------------------

**Jade, not a first-party overlay.** `maven.modrinth:jade:26.2.9+neoforge`, from
`https://api.modrinth.com/maven`. It is the one mod in `reference/mods/` actually on 26.2 — KubeJS
is on 26.1.2 and Create on 1.21.1 — which is the whole reason it clears a bar the rest of the
ecosystem does not.

`compileOnly` in each subsystem mod and `runtimeOnly` in the pack. A `@WailaPlugin` class is only
loaded when Jade is present, so every mod still stands alone and nothing is declared `required`.

Two things about writing providers, both of which cost a client boot to discover:

- **A provider may not be both halves.** Jade throws at registration if one object implements
  `IServerDataProvider` and `IComponentProvider`, and has since 1.21.6. The pattern — theirs and
  now ours — is an outer data class with a nested `Client`, sharing one uid so a player toggling
  the readout off turns off both.
- **Everything needs server data.** A boiler's steam and an engine's charge change every tick, and
  a machine that pushed a block update every tick to animate a bar would be sending packets to
  everyone in render distance. Jade asks the server only while somebody is looking, which is the
  right amount. The pole needs it absolutely: a network is a server-side object and the client has
  no `PowerNetworkManager` at all.

Jade's *universal* providers already draw item contents and a generic energy bar with no help from
anyone — which is why an assembler showed a red full bar before any of this existed. Ours add the
half a generic provider cannot know: whether the machine is doing anything, and why not when it is
not.

Written so far: the boiler, the steam engine and the pole in {@code nauvis_power}, and the pipe in
`nauvis_fluids`. The pipe's is modelled on Factorio's own - what is in the run and how far it
reaches - but stops at the extent rather than printing Factorio's `6/320`, because the 320 is its
cap on one fluid segment and this pack has none. A tooltip is not the place to invent a rule
nothing enforces.

What stays Jade's is the position and the frame. Factorio's readout is an anchored panel with alert
icons floating over stalled machines; that is an overlay on the world rather than a tooltip, and a
separate feature if it is ever wanted.

What testing looks like
-----------------------

Claude runs `./gradlew :nauvis:runGameTestServer` headless: it starts a server, runs every
gametest and exits non-zero if any fails. Behaviour is testable this way — a machine consuming
its ingredients, an inserter moving a stack, a recipe resolving — so most of a milestone can be
verified without anyone watching. `:nauvis:runServer` still answers the coarser question of
whether the pack loads at all, and `:nauvis:check` refuses to build on a wrong recipe.

Write the test with the block, not after it.

Claude cannot see the game. Textures, GUI layout, whether the assembler screen is usable,
whether the factory is *fun* — that is Yannic's half, and it is the half that decides whether
a milestone is actually done.
