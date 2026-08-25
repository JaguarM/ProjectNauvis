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

**Energized Power** (`energized-power`, 3.0.0+26.2.x, MIT) — FE machines and generators.
Overlaps `nauvis_machines` and `nauvis_power` heavily, so the question is whether it replaces
those milestones or duplicates them. Not yet evaluated in the way Fluid Tank has been.

Note the licence conflict on Fluid Tank if its code is ever adapted rather than shipped: the
GitHub repo is MIT, the shipped jar's `neoforge.mods.toml` says `All rights reserved`. Ask the
author before copying a line of it. Reading it is fine either way.

The mods
--------

Four already exist and are released; their ids are permanent. Ten are new.

| Mod id | Owns | Items |
|---|---|---|
| `facrafting` | the timed crafting model | — |
| `neoprogressivematerials` | intermediate products | 18 |
| `neoprogressiveautomation` | the two mining drills | 2 |
| `crumblingore` | ore depletion | — |
| `nauvis` | pack policy, vanilla replacement, raw resources, terrain | 16 |
| `nauvis_logistics` | belts, inserters, splitters, chests, robots | 28 |
| `nauvis_machines` | assemblers, furnaces, modules, beacon, radar | 18 |
| `nauvis_power` | boiler, steam engine, solar, accumulator, poles | 13 |
| `nauvis_research` | labs, science packs, tech gating | 7 |
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
still produces its own jar with its own mod id and is publishable standalone. The four
released mods stay in their own repos and are pulled in with
`includeBuild("../NeoProgressiveAutomation")` and friends, so one `./gradlew runServer` puts
the whole pack on the classpath and cross-mod edits are possible in one pass.

If ModDevGradle fights composite builds, fall back to dropping their built jars into
`run/mods`. Prove which works before building on it — budget 20 minutes, not a day.

Milestones
----------

Each milestone is a playable state, not a checklist. Counts are *new* items, transitive over
the recipe graph, with vanilla stand-ins excluded from the registration cost.

### 0 — Foundation

Gradle workspace, composite build proven, the `nauvis` pack mod, and the **recipe generator**:
`data/mapping.json` + `reference/factorio/recipes.json` → recipe JSON per owning mod. Nothing
else starts until recipes generate, because everything after this depends on it.

### 1 — First factory · 11 new registrations

`assembling-machine-1`, `burner-inserter`, `inserter`, `iron-chest`, `boiler`, `steam-engine`,
`small-electric-pole`, `pipe`, plus intermediates already owned by
`neoprogressivematerials`. `stone-furnace` and `wooden-chest` are vanilla.

Chest → inserter → assembler → inserter → chest, burning coal. **This is the whole point of
the pack and it costs eleven items.** Get here fast.

### 2 — Belts · 4 new

`transport-belt`, `underground-belt`, `splitter`, `long-handed-inserter`. Small item count,
by far the largest engineering lift. See the belt note below.

### 3 — Research · 15 new

`lab`, `science-pack-1`, `science-pack-2`, `assembling-machine-2`, `steel-furnace`,
`solar-panel`, `accumulator`, `medium-electric-pole`, and `steel-plate` / `battery` /
`sulfur` in materials. **The vanilla-replacement datapack lands here** — once research gates
progression there is somewhere for stripped vanilla recipes to go.

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
| Research | lab consumes packs, grants vanilla advancements; recipes gate on them | real tech tree screen with costs and prerequisites |
| Oil | **barrels as items, no pipes at all** | fluid network, pipes, tanks, pumps |
| Trains | vanilla minecarts and chest minecarts | locomotives, wagons, signals, schedules |
| Biters | vanilla hostiles + per-chunk pollution raising spawn rate near the factory | nests, expansion, evolution factor |
| Rocket | silo consumes 100 rocket parts, plays a launch, grants the advancement | satellite, cargo, space science loop |

The belt note
-------------

Belts are the one architectural decision that is expensive to reverse, which is why they get
their own mod: the rewrite must be containable.

The shortcut — a BlockEntity per belt block — is fine for a few hundred belts and lets
milestone 2 ship. It will not survive a real base. The endgame is Create's architecture, and
`reference/create-src/src/main/java/com/simibubi/create/content/kinetics/belt/transport/` is
the reference: `TransportedItemStack` carries a position *along* the belt and `BeltInventory`
owns the whole run, with segment blocks delegating to one controller. A transport line is one
object; items are positions on it. That is how Factorio does it too.

Read it for the architecture and reimplement — Create's version is welded to its kinetics
framework, and its assets are All Rights Reserved regardless.

What testing looks like
-----------------------

Claude runs `./gradlew runServer --nogui` headless: does the pack load, do registries populate,
does `/datapack list` show what it should, does a recipe resolve. That catches broken recipes,
missing loot tables and load failures before the game is ever launched.

Claude cannot see the game. Textures, GUI layout, whether the assembler screen is usable,
whether the factory is *fun* — that is Yannic's half, and it is the half that decides whether
a milestone is actually done.
