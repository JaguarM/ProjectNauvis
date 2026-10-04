Project Nauvis
==============

A Minecraft 26.2 / NeoForge modpack that recreates Factorio. `docs/NEXT.md` says what to pick up
and how to run everything. Read `docs/PITFALLS.md` before writing code and `docs/API-26.2.md`
before writing against any Minecraft API. `docs/ARCHITECTURE.md` is the rules a machine is built
to, `docs/GAPS.md` what is deliberately missing, `docs/PLAN.md` the settled decisions and the mod
list, `docs/MAPPING.md` the data files and their generators.

Non-negotiables
---------------

1. **Identity is Factorio's; implementation is free.** Item and block ids, ingredient lists, craft
   times, footprints and stack sizes are Factorio's from the first commit, because they live in
   world saves and in the player's head. Factorio means the 2.0 base game, read from its own
   `data.raw`. Everything behind them may be crude and rewritten later.
2. **Recipes, technologies and vanilla removals are generated, never typed.** `tools/gen_recipes.py`
   turns Factorio's own `data.raw` (`reference/factorio/data-raw-<version>.json`, written by
   `factorio --dump-data`) plus `data/mapping.json` into recipe JSON; `tools/gen_technologies.py`
   turns the selection in `data/technologies.json` and the same dump into the tree;
   `tools/gen_removals.py` turns `data/removals.json` into the vanilla-replacement datapack. Each
   has `--check`, which diffs against disk, and `./gradlew build` runs all three. A vanilla recipe
   is removed only when the pack already does that job.
3. **One mod per subsystem, arrows one way.** Subsystem mods never compile against each other;
   they meet through capabilities, `nauvis_lib`, Facrafting's hooks and `neoforge:mod_loaded`
   recipe conditions. The only compile-time dependencies allowed are `nauvis_lib` (the framework;
   it registers nothing a player can hold and depends on nothing of ours) and `facrafting` (the
   timed crafting model and the crafting UI), each declared `required` in `neoforge.mods.toml`.
   Pack policy lives in the `nauvis` mod, which compiles against `nauvis_lib` and nothing else.
4. **Verify every 26.x API against the decompiled sources.** Minecraft 26.2 postdates training
   and renamed a great deal; guessed names have failed silently. `docs/API-26.2.md` says where
   the sources are and lists the confirmed renames.
5. **Machines sleep.** No server-side `BlockEntityTicker`. A machine with nothing to do costs
   zero ticks and is woken by a change; `hasScheduledTick` is how a test asserts it.

Writing
-------

A javadoc is its first paragraph: what the thing is. Why it is that way is written once, in
`docs/ARCHITECTURE.md` for a rule and `docs/PITFALLS.md` for a way of getting it wrong, and is
not repeated in code. No history anywhere: a doc says what is true now, and git says how it got
there. A gametest is a lambda in `GameTests.add`, and a class only when it needs fields.

Licensing
---------

`reference/` is gitignored in full: Wube's recipe dump, Create, Immersive Engineering and other
people's source. Read it for architecture and reimplement. MIT code wants attribution; assets are
always reserved. Depending on a mod is never a licence question; copying from one always is.

The siblings
------------

`../Facrafting` (`facrafting`) is unpublished and ours to change freely; folding it into this repo
is a separate decision to ask about. `../CrumblingOre` (`crumblingore`) is released, so its ids and
behaviour are frozen. `../NauvisTerrain` (`nauvis_terrain`) is the world: Factorio's Nauvis as a
world type a new world starts on, its ores at the surface; the pack changes what it needs of it
through data and never compiles against it. All three are `includeBuild`s, so an edit is picked up
here at once.
`nauvis_mining` is a fork of the released Neo Progressive Automation under Factorio's ids;
`nauvis_materials` is Neo Progressive Materials folded in under the pack's ids. The originals at
`../NeoProgressiveAutomation` and `../NeoProgressiveMaterials` are not built and not to be
changed. `facrafting:facraft` is frozen because released NPA names it in its recipes.
