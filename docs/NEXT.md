Next session
============

Rewritten 2026-09-07. This file is only what to pick up now and how to run things; it is meant to
stay short and to be edited down as jobs finish. What exists and how it is built is
`ARCHITECTURE.md`, what is deliberately missing is `GAPS.md`, and `CLAUDE.md` says which file is
for what. **Read `PITFALLS.md` before writing.**

Where the pack stands
---------------------

Every gametest passes, `./gradlew build` is clean, and the client boots into a world. **The rocket
launches.** Milestones 0 to 5 and 7 are built: the first factory, belts, research, oil, the
military, and the silo - `PLAN.md`'s milestone list says what each holds, every machine's numbers
are on the machine in its `*Block` and `*Shape` classes, and the mapping is `data/mapping.json`.
Milestone 6, the robots, is a later update by decision.

**The rocket has had one look and one round of polish.** Yannic's first look, 2026-09-08, found
the silo filling every slot with one ingredient, no way to launch by hand, and stacks of
sixty-four; now a slot is one ingredient's, the screen has an automatic-launch toggle and a Launch
button, and every item stacks to Factorio's size or Minecraft's ninety-nine. The second look is
owed: the two buttons on the silo's screen, the radar's dish, the nine module chips and the five
new item icons, and a launch watched from the ground.

The jobs
--------

### 1. Play it

Two builds have passed their tests and nobody's eyes, and both want a client boot and an evening:

- **The rocket.** The two launch controls on the silo's screen - the *A* toggle and Launch, drawn
  as the lab's button is - the radar's dish, the nine module chips and the five new item icons,
  and a launch watched from the ground. The whole road is playable in survival - the tree reaches
  `rocket-silo`, every ingredient exists, and the silo is made in an assembling machine 2, whose
  slots hold the thousand concrete and thousand steel it costs. The things most likely to want
  moving, none of them identity: the five-second countdown, the firework's height, the rocket's
  colours, the placeholder textures, and whether a nine-by-nine pad reads as a silo at all.
- **The attack.** Since 2026-09-07 what pollution sends walks at the machine that made it, chews
  through the wall in its way, hits the turret when it gets there, and turns on a player only within
  six blocks. Every machine has health - a hundred times its hardness, the turret Factorio's four
  hundred - and the repair pack mends it. The numbers in `Attacks`, `Absorption` and
  `PollutionState` have still never been played: how often a group comes, how big, how far away it
  appears, how hard a zombie hits a wall and how far it notices a player. Play a polluted evening
  behind a wall with two turrets, then move them.

### 2. Later updates

The robots (milestone 6), and what space science buys - the infinite research the tree does not
transcribe. Neither is on the road to the rocket and both are `PLAN.md`'s to schedule.

The playtest
------------

Every job ends with `./gradlew build`, `:nauvis:runGameTestServer`, **and a client boot**.
**A model can be looked at** without one: `python tools/render_model.py <model id> out.png` draws
any block model from the item slot's angle, holes and all. Render every new machine's `_inventory`
model before asking anybody to boot a client; the boot is still owed, for lighting and for how the
thing sits in a world.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack. **Boot it after any model, fluid or plugin change** |
| `./gradlew :<mod>:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` / `runServerData` | models and language / loot and tags |
| `./gradlew build` | everything, including the six checks |
| `python tools/gen_recipes.py --check` | the recipe diff, on its own |
| `python tools/gen_technologies.py --check` | the same for the tree. `--write` to regenerate |
| `python tools/gen_removals.py --check` | the vanilla recipes taken away. `--write` to regenerate |
| `python tools/check_models.py` | every model, texture and blockstate reference resolved — and footprints, belt speeds and texture opacity |
| `python tools/render_model.py <model> out.png` | **draws a model to a PNG from the item slot's angle**, so a machine can be looked at without a boot. `--view side` or `top` for one cell. Needs Pillow and numpy |
| `python tools/check_gametests.py` | every gametest, for a type registered as well as an instance |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python texture-workshop/make_*_textures.py` | the pack's item and block art, from ASCII maps. `--preview` for a sheet |
| `./gradlew :nauvis:packConfig` | the pack's config over `run/config`. Every run task depends on it |

The six checks — `checkRecipes`, `checkTechnologies`, `checkRemovals`, `checkModels`,
`checkGameTests`, `checkGuiLayout` — are in the root `build.gradle` and hang off
`:nauvis:check`. They read files and start nothing, so they cost a second between them.
`checkTechnologies` is the strict one: it fails on a file the generator **no longer produces**, not
only on one that differs, because a stale technology would still load and answer to nothing.

**A gametest run sees the recipes the pack ships.** `GameTestServer` force-enables every datapack
it can see, bench-recipe packs included, so each `gameTestServer` run passes
`-Djaguarm.benchRecipePacks=false` and `nauvis_lib`'s `BenchRecipePacks` offers no pack under it — the
one state vanilla cannot override is absent. `nauvis:timed_recipes_are_timed` holds it there.

**Adding a subsystem mod** is routine: a subproject in `settings.gradle`, a `build.gradle` with the
ids changed and `implementation project(':nauvis_lib')` in it, a
`src/main/templates/META-INF/neoforge.mods.toml` declaring `nauvis_lib` required, two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` and its namespace in
`pack_gametest_namespaces` — and its name in the `MODS` lists of `tools/check_models.py`,
`tools/check_gametests.py` and `tools/render_model.py`, plus a `crafting_table/pack.mcmeta` even
if the mod has no bench recipe, since the finder throws without one. Every item it registers takes
`Stacks.of(n)` with Factorio's stack size and a matching `stack` in `data/mapping.json`. `nauvis_rocket/` is the
newest written from scratch by following exactly that list, so its first commit is what adding a
mod costs; `nauvis_materials/` is the newest folded in from a sibling repo, and the commit that did
it is what a rename across the pack costs - the ids in every recipe, test and mods.toml, and the
generators run again.

**Recipes** generate into a staging directory —
`python tools/gen_recipes.py --only <modid> --out <tmp>` — then copy across only the files for items
that exist. `--write` writes all of that mod's recipes, and a recipe naming an unregistered item is
a load error.

**Pack settings** for a mod the pack ships go in `nauvis/pack/config/`, not `run/config`: `run/` is
gitignored, so a setting edited there is a change nobody else sees. Keep each file in the form
NeoForge writes it and put the reasoning in that directory's README.
