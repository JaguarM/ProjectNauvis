The plan
========

Recreate Factorio as a Minecraft modpack: mine, smelt, automate, research, defend, launch a
rocket. Minecraft 26.2 / NeoForge 26.2.0.59. First-party by default — the pack must stay small
and load fast, and the tech ecosystem has not reached 26.2. See the third-party policy below.

Read `../CLAUDE.md` first. The five non-negotiables there govern everything below.

Settled decisions — do not re-litigate
--------------------------------------

**Scope is the whole game, rocket included.** Oil, pollution and biters, robots, rocket
launch. Chosen knowingly as a multi-year plan. Two things are out, below: the trains and the
circuit network.

**No trains, no vehicles, and no pump.** Yannic's call after the oil playtest, 2026-09-06: a
railway is a great deal of work for something this pack is not about. `nauvis_trains` is never
built. The rail, the two signals, the stop, the locomotive, the three wagons, the car, and the
pump - which exists to load and unload fluid wagons - are `skip` in `data/mapping.json` with the
reason, so the generator never reports them missing. Optional things that are fun to build -
modules, the beacon, the tiers - are in.

**No circuit network. Vanilla redstone stays, with its vanilla recipes.** Yannic's call after
the military playtest, 2026-09-07: Factorio's required progression is already enough work, and
Minecraft has a signal system of its own that players know. So the combinators, the two wires,
the lamp and the programmable speaker are `skip` in `data/mapping.json`, `nauvis_circuits` is
never built, and every redstone recipe - repeater, comparator, observer, piston, lamp - is
vanilla's and is never removed. Wiring a Factorio machine to redstone, if it is ever wanted, is
a feature on the machine and not a milestone.

**The rocket before the robots.** Yannic's call after the military playtest, 2026-09-07: finish
the game before widening it. Milestone 7 is built ahead of milestone 6, and the roboport, the two
robots and the logistic chests are a later update - `nauvis_logistics` still owns them, their ids
are in the mapping, and nothing about the rocket waits on them. The one item of milestone 6 the
rocket does need, the flying robot frame, is not needed after all: the pack's science packs are
the dump's, and the dump's high tech science pack is a battery, cable, processing units and a
speed module.

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
answer, and those are the part milestone 4 is actually about. So oil is first-party: the well and
the pumpjack are built, and the refinery is next. This resolves the fluids open question in
`MAPPING.md`.

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
The other eleven are subprojects here — a library and ten subsystem mods, seven of which exist
so far.

| Mod id | Owns | Items |
|---|---|---|
| `facrafting` | the timed crafting model | — |
| `neoprogressivematerials` | intermediate products | 18 |
| `crumblingore` | ore depletion | — |
| `nauvis_lib` | the framework: multi-blocks, transfer views, the machine screen, bench packs | — |
| `nauvis` | pack policy, vanilla replacement, raw resources, terrain | 16 |
| `nauvis_logistics` | belts, inserters, splitters, chests, robots | 28 |
| `nauvis_machines` | assemblers, furnaces, modules, beacon, radar | 18 |
| `nauvis_power` | boiler, steam engine, solar, accumulator, poles | 13 |
| `nauvis_research` | labs, science packs, tech gating | 7 |
| `nauvis_mining` | the two mining drills | 2 |
| `nauvis_fluids` | pipes, oil, chemistry, nuclear | 33 |
| ~~`nauvis_circuits`~~ | ~~combinators, wires, lamps, speakers~~ never built: vanilla redstone stays, see the settled decisions | 0 |
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
carries an explicit `dependencySubstitution` per sibling. The siblings are `runtimeOnly`, with the
one exception CLAUDE.md allows: Facrafting is `implementation` in `nauvis_machines`, which runs its
recipes and has to name their type, and `compileOnly` behind a `ModList` check in `nauvis_research`
and `nauvis_fluids`, which install a hook and fire a listener and still load without it.

Configuration cache is off here, though all three siblings enable it. Composite build plus
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
`neoprogressivematerials`. `wooden-chest` is vanilla; `stone-furnace` was, until milestone 3.

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

### 3 — Research · 15 new · *done*

`lab`, `science-pack-1`, `science-pack-2`, `assembling-machine-2`, `steel-furnace`,
`solar-panel`, `accumulator`, `medium-electric-pole`, and `steel-plate` / `battery` /
`sulfur` in materials. **The vanilla-replacement datapack lands here** — once research gates
progression there is somewhere for stripped vanilla recipes to go. It has: `data/removals.json`
and `tools/gen_removals.py`, shipped as a built-in datapack that is on by default. It is four
recipes long and the rule is why — *a vanilla recipe is removed only when the pack can already do
that job*, enforced by the build rather than remembered — so it grows with the items rather than
ahead of them.

**The three furnaces are done, and the stone furnace is a machine of the pack's own.** It stood in
as `minecraft:furnace` through two milestones, and the steel furnace forced the question: a faster
vanilla furnace would have smelted vanilla's recipes at vanilla's ratios, and a furnace is where
every plate in the game comes from. So Factorio's four smelting recipes are Facrafting recipes with
a `smelting` category - a rule Facrafting learned, and a fact the generator writes - the hand panel
never offers them, and a furnace chooses among them by what is put into it. Two by two for the
burner tiers and three by three for the electric one, on the same shell as everything else, and
the plates a furnace makes are what finish `steam-power`.

**The tree itself is done and is all 216 finite technologies**, not the fifteen items above.
That is deliberate and is the same rule as recipes: a technology's cost, its prerequisites and
its unlocks are identity, they live in world saves, and generating the whole graph from Wube's
own data costs no more than generating a tenth of it. Most of them unlock items no mod registers
yet, and simply do nothing until one does. **The accumulator closed the milestone**, once the
chemical plant could make a battery: it is the grid's third case - Factorio's rule that a battery
takes only what the generators leave over and gives only what they cannot cover - and a solar
field carries a factory through the night with it. The substation and the electric furnace are
built and gated behind blue science.

### 4 — Oil · 11 new · *all built*

`pumpjack`, `oil-refinery`, `chemical-plant`, `empty-barrel`, `storage-tank`,
`plastic-bar`, `advanced-circuit`, `engine-unit`, `science-pack-3`, `electric-mining-drill`.
Every one of them exists, the assembling machine 2 grew the fluid box that the barrels and the
electric engine unit needed, and the drills are Factorio's drills: they take the ore out of the
ground under them and put it down in front of themselves.

**The pumpjack and the oil well are done**, and the well is the piece nobody had a shape for: a
resource *block* — unbreakable ground holding Factorio's resource amount — rather than a fluid in
the world, exactly as Factorio's `crude-oil` is an entity you stand a pumpjack on and not a puddle.
The pumpjack spends 90 kW's worth of FE and banks ten units times the yield a second, the well loses
ten a cycle and floors at 20%, and a pipe at the outlet corner carries the oil away. Worldgen puts
fields down at Factorio's density and none in the starting area.

The barrels shortcut this section used to prescribe — *oil moves as barrelled items, no pipes at
all* — predates the pipe. Steam brought the fluid network forward in milestone 1, so oil already
flows; what barrels would still spare is the refinery's three blocking outputs and the tanks under
them, and that is the part the milestone is actually about, so it is done properly. `oil-processing`
is in the tree as Factorio 2.0's trigger — the first crude oil pumped — and fires; what remains is
the refinery and the chemical plant it unlocks, and a fluid-aware recipe generator with a data
source for the oil recipes.

### 5 — Military and pollution · 8 new · *all built*

`pistol`, `submachine-gun`, `firearm-magazine`, `piercing-rounds-magazine`, `gun-turret`,
`stone-wall`, `light-armor`, `military-science-pack` - and the grenade and heavy armour, which
military science and its technology needed. `nauvis_military` exists, the machines pollute,
and the pollution brings something; the model is the military note below.
### 6 — Robots · 5 new · *a later update*

The circuit network's seven were this milestone's other half and are out - the settled
decisions say why - so it is the roboport, the two robots, the flying robot frame and the
logistic chests. Built after the rocket, by decision; see above.

### 7 — Rocket · 14 new

Those seven milestones reach **89 of 214 items** — the critical path. Ten more — the trains, the
car and the pump — are out; see the settled decisions. The remaining 115 are breadth: tier-2 and
tier-3 variants, modules, nuclear, armour, artillery, logistic chests. They are cheap once their
tier-1 exists and get added alongside whichever milestone owns them.

Shortcuts, and what they defer
------------------------------

| Subsystem | Ship this | Rewrite to this later |
|---|---|---|
| Assembler | recipe selector + one input inventory, running Facrafting's timed recipes | fixed recipe, per-ingredient buffers, module slots, tiers |
| Inserter | directional block moving stacks between neighbours on a timer | swing animation, per-item hand, filter/stack/long variants |
| Belt | BlockEntity per block passing items along | transport lines, items at positions |
| Power | FE per machine — burner generator, solar, FE-battery accumulator | unchanged; FE *is* the chosen model |
| Poles | FE cables with a wide connection radius | unchanged |
| Research | ~~lab consumes packs, grants vanilla advancements~~ **rejected — advancements are per player and research belongs to the world.** Shipped instead: the real model, on a `SavedData`, drawn as Factorio's own screen — a list, one technology's neighbourhood, pan and zoom | the damage modifiers, transcribed and not yet read; research speed, hand sizes and mining productivity reach their machines through `nauvis_lib`'s `Bonuses` |
| Oil | ~~barrels as items, no pipes at all~~ **superseded — the pipe network came with steam, and the well and pumpjack are the real ones.** One tank per run, no flow model | segments and throughput, if ever |
| Biters | vanilla hostiles + per-chunk pollution raising spawn rate near the factory | nests, expansion, evolution factor |
| Rocket | silo consumes 100 rocket parts, plays a launch, grants the advancement | satellite, cargo, space science loop |

The belt note
-------------

Belts are the one architectural decision that is expensive to reverse, which is why they get
their own mod: the rewrite must be containable. **The shortcut this note used to allow — a
BlockEntity per belt block — was not taken.** A transport line is one object and items are
positions on it, which is how Factorio does it and where Create arrived; the client builds the
same runs from the same block states and advances them with the same code, so a belt full of
visibly moving items costs no packets; and a tier is a class, so the fast belt was one file, one
palette and one line in a registry. How it is built, and what is load-bearing for anything on
top of it, is `ARCHITECTURE.md`'s belt section.

The electric network note
-------------------------

Poles are the other decision that is expensive to reverse, for the same reason belts are, and the
belt note's sentence is the whole design with the nouns changed: **an electric network is one
object; poles are members of it.** A pole looks like a block and is not — it is one node in a
graph whose job is to make other blocks reachable — so the thing that ticks is the network, once,
and a pole never ticks at all. A pole that ticks is N ticks a second for N poles and an energy
packet that takes N ticks to cross them; a pole that rescans its supply area is worse.

Two radii, not one. Factorio's small pole reaches 7.5 blocks to another pole and supplies a 5x5
area, and keeping both is what makes a base look like a Factorio base rather than a line of cables.
Neither number is identity, so both are yours to tune; the id and the recipe are not. How the
network is built — how a pole finds a machine built later, why poles are bucketed into cells, why a
pole is a multi-block and why its wires draw themselves — is `ARCHITECTURE.md`'s grid section, and
what it cannot do yet is `GAPS.md`'s.

The military note
-----------------

**Pollution is a number over each chunk, and the biters are Minecraft's own hostiles.** That is
the shortcut in the table above, taken in full, and this is what it is made of.

Every working machine emits Factorio's figure for it - a stone furnace two a minute, a boiler
thirty, a burner drill twelve - on every tick it works, scaled by its modules the way its draw
is. The machines are in five mods that may not name a sixth, so the number goes through
`nauvis_lib`'s `Pollution`, the same seam as `Bonuses` with the arrow the other way: a machine
emits and never asks who is listening, and `nauvis_military` installs the one `Sink`. Without
the military mod the number goes nowhere, which is what every machine did before.

The clouds are Factorio's at chunk size: `PollutionState`, one per level, a number over each
16-by-16. Once a minute every cloud gives two percent of itself to each of its four neighbours
and the ground takes five off every chunk - a flat figure where Factorio's depends on the
tiles, so a forest absorbs nothing more here. A cloud grows until its edges thin to nothing,
which is the shape a Factorio cloud has. `/pollution` reads the chunk you stand in and the
level's lifetime total; the hover readout says the same for any block.

There are no spawners, so the cloud itself sends the attack. Once a minute a chunk holding at
least fifty has a chance proportional to what it holds - certain at five hundred - of spending
fifty a head on a group of up to six, which appears twenty-four to forty blocks from the
chunk's middle with the nearest player as its target, and only if a player is within
ninety-six blocks. What comes is the level's lifetime pollution standing in for evolution:
zombies, skeletons among them past twenty thousand, creepers past sixty. They wear a cap so
the sun does not do the turrets' job. None of those numbers is identity; they are the knobs,
in `Attacks` and `PollutionState`, and the first playtest will move them.

The weapons are Factorio's numbers on Minecraft's hits. A bullet is a line rather than an
entity - the pistol, the submachine gun and the turret hit what they aim at on the tick they
fire, and they ignore invulnerability frames, since ten rounds a second at one hit in ten is
not a submachine gun. A magazine is durability, ten rounds, found in the off hand or the
inventory the way a bow finds arrows. The turret is the family's 2x2 shell with the front open
and two barrels, takes one magazine, looks around twice a second while loaded and sleeps
when empty, and shoots anything Minecraft calls an enemy within eighteen blocks. Physical
projectile damage research reaches all of them through `Bonuses`, by target. Armour is a
chestplate on vanilla's chainmail and netherite models; the grenade is a snowball that goes
off without breaking blocks; the stone wall is vanilla's cobblestone wall at Factorio's price.

The look-at readout
-------------------

**Jade, not a first-party overlay.** It is the one mod in `reference/mods/` actually on 26.2 —
KubeJS is on 26.1.2 and Create on 1.21.1 — which is the whole reason it clears a bar the rest of
the ecosystem does not. `compileOnly` in each subsystem mod and `runtimeOnly` in the pack, so every
mod still stands alone and nothing declares it required. Jade's universal providers already draw
item contents and a generic energy bar with no help from anyone; ours add the half a generic
provider cannot know — whether the machine is doing anything, and why not when it is not. Writing
one is `ARCHITECTURE.md`'s hover readout section.

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
a milestone is actually done. What it can see since the pumpjack is a *model*:
`tools/render_model.py` draws one to a PNG from the item slot's angle, which is enough to catch a
hole, a missing face or a wrong texture before a boot, and not enough to judge how a machine sits in
a world.
