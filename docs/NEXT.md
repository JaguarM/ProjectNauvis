Next session
============

Only what to pick up now and how to run things. Edit it down as jobs finish.

Where the pack stands
---------------------

Every gametest passes, `./gradlew build` is clean, the client boots and the rocket launches.
Identity is Factorio 2.0.77's base game since 2026-09-08, read from its own `data.raw`; the ids
follow 2.0's names (automation science pack, bulk inserter, efficiency module, barrel).
Milestones 0 to 5 and 7 are built (`PLAN.md` lists them); milestone 6, the robots, is a later
update by decision. Every machine's numbers are on the machine in its `*Block` and `*Shape`
classes; the mapping is `data/mapping.json`.

The jobs
--------

### 1. Play it

Four builds have passed their tests and nobody's eyes. Each wants a client boot and an evening:

- **Nauvis.** A new world is Nauvis Terrain's, its patches at the surface: whether a first drill on
  iron one block deep, eight harvests a block, lasts long enough, whether the F toggle on the
  drill's screen reads as "Factorio's ores only", and how the oil fields and the offshore pump sit
  on its ground and lakes. The knob is the ring width in
  `nauvis/pack/config/crumblingore-common.toml`.
- **The rocket.** The *A* toggle and the Launch button on the silo's screen, the radar's dish, a
  launch watched from the ground. Not identity, so free to move: the five-second countdown, the
  firework's height, the rocket's colours, whether a nine-by-nine pad reads as a silo.
- **The icons.** Every item is drawn from Factorio's icon since 2026-09-08: the seven flasks, the
  guns level with the muzzle left, the curved magazines, the wrench and screwdriver, the modules'
  three lamps, the coil, the crystal, the ring and propeller, the rocket part and the satellite.
  Whether each reads in a slot, on a belt and in the lab's window. The guns point forward in the
  hand through `models/item/gun.json`; the angles in it were worked out from the renderer's code
  and never seen: first person is ten degrees in and ten up, third person follows the sword.
- **The stacks.** Two hundred circuits in a slot, and how three- and four-digit counts read in a
  chest and on the hotbar. Cobblestone, coal and raw ore stack to fifty now; that is the one number
  to feel out, one line each in the pack mod's `StandInStacks`.
- **The attack.** What pollution sends walks at the machine that made it, chews through the wall,
  hits the turret, and turns on a player only within six blocks. Every machine has health and the
  repair pack mends it. The knobs in `Attacks`, `Absorption` and `PollutionState` have never been
  played: how often a group comes, how big, how far away, how hard a zombie hits a wall.

### 2. Later updates

The robots (milestone 6) and what space science buys (infinite research). `PLAN.md`'s to schedule.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack. Boot it after any model, fluid or plugin change |
| `./gradlew :<mod>:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` / `runServerData` | models and language / loot and tags |
| `./gradlew build` | everything, including the five checks below |
| `python tools/gen_recipes.py --check` | recipes against the generator; `--write` to update disk |
| `python tools/gen_technologies.py --check` | the tree; `--write` to regenerate. Fails on a stale file too |
| `python tools/gen_removals.py --check` | the vanilla removals; `--write` to regenerate |
| `factorio.exe --dump-data` | Factorio writes its `data.raw` as JSON to its script-output folder; copy it to `reference/factorio/data-raw-<version>.json` and name the version in `tools/factorio_data.py` |
| `python tools/check_models.py` | every model, texture and blockstate reference, plus footprints, belt speeds, stack sizes, opacity, rotation |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python tools/render_model.py <model> out.png` | draws a model from the item slot's angle; `--view side` or `top`. Needs Pillow and numpy |
| `python texture-workshop/make_*_textures.py` | the pack's art, from ASCII maps. `--preview` for a sheet |
| `./gradlew :nauvis:packConfig` | the pack's config over `run/config`; every run task depends on it |

Every job ends with `./gradlew build`, `:nauvis:runGameTestServer` and a client boot. Render every
new machine's `_inventory` model before asking for a boot.

`gameTestServer` runs pass `-Djaguarm.benchRecipePacks=false`, because `GameTestServer`
force-enables every datapack it can see; `nauvis:timed_recipes_are_timed` holds it there.

**Recipes**: `gen_recipes.py --write` rewrites the timed recipes already on disk and their
fallbacks, and leaves alone a recipe with no file yet, since its item is usually unregistered and
would be a load error. For a new item: register it, then `--write --all --only <modid>`.

**Pack settings** go in `nauvis/pack/config/`, not `run/config`; reasoning in that directory's
README, because NeoForge rewrites the toml.

**Adding a subsystem mod**: a subproject in `settings.gradle`; a `build.gradle` copied from
`nauvis_rocket/` with the ids changed; a `neoforge.mods.toml` declaring `nauvis_lib` required;
`runtimeOnly project(':...')` and the namespace in `pack_gametest_namespaces` in
`nauvis/build.gradle`; its name in the `MODS` lists of `tools/check_models.py` and
`tools/render_model.py`; a `crafting_table/pack.mcmeta` even with no bench recipe. Every item
takes `Stacks.of(n)` and a matching `stack` in `data/mapping.json`. Tests go through
`nauvis_lib`'s `GameTests`.
