Project Nauvis
==============

A Minecraft modpack that recreates Factorio. Read `docs/PLAN.md` before starting work;
`docs/MAPPING.md` and `docs/API-26.2.md` are the other two you will need. **`docs/NEXT.md`
says what to pick up now** and how to run the build, the server and the tests.

Non-negotiables
---------------

**1. Shortcuts on implementation, never on identity.**
Item ids, block ids, recipe ingredient lists and craft times must be Factorio-correct from
the first commit — they live in world saves and in the player's head. Everything behind them
may be crude and rewritten later. A belt that is a BlockEntity per block is acceptable; a
belt that costs the wrong ingredients is not.

**2. Recipes are generated, never hand-written.**
`reference/factorio/recipes.json` is the spec: 214 entries, a closed graph, `time` already in
seconds. `data/mapping.json` says what stands in for what. `tools/gen_recipes.py` turns the
two into recipe JSON. If you are about to type a recipe by hand, you are doing it wrong.

Run `python tools/gen_recipes.py --check` before trusting any recipe on disk. It diffs the
generated output against the committed files and has already caught two wrong recipes in a
released mod. Seven items genuinely cannot be generated; the tool names them rather than
letting you discover it in-game.

**3. One mod per subsystem, arrows pointing one way.**
Each mod is a separate jar with its own permanent mod id, usable standalone, glued to the
others by `neoforge:mod_loaded` recipe conditions. No cycles. Pack policy — vanilla recipe
removal — lives in the `nauvis` mod or the pack datapack, never inside a subsystem mod.

**4. Verify every 26.x API against decompiled sources.**
Minecraft 26.2 is past the model's training cutoff and renamed a great deal. Guessing has
produced wrong code repeatedly. `docs/API-26.2.md` lists the confirmed renames and where the
sources are. Check before writing, not after it fails to compile.

**5. Machines sleep.**
A machine with no work and no power must cost zero ticks, waking on neighbour change. Cheap
now, a horrible retrofit later. Factorio bases are thousands of machines.

Licensing
---------

`reference/` is gitignored in full. It holds other people's work:

- **`reference/factorio/recipes.json`** — Wube's data. Read it, generate from it, never commit it.
- **`reference/mods/*.jar`, `reference/create-src/`** — Create, by the Create Team.
  **Code is MIT** (adapting with attribution is permitted). **Assets are All Rights Reserved** —
  textures, models and sounds must never be copied. Ours are ours.

Read Create for architecture and reimplement. Its belt code is welded to the kinetics
framework — stress, rotation, contraptions — and lifting it drags in the exact weight this
pack exists to avoid.

The same applies to any mod worth learning from. Most of the tech ecosystem is stranded on
1.21.1 and will never be shipped here, which makes it free to read and nothing more. Check the
licence before adapting a line of it — MIT wants attribution, and assets are almost always
reserved regardless of what the code says. Ours are ours.

The four sibling mods
---------------------

These are already released, live in their own repos at `../`, and their ids are permanent:

| Repo | Mod id | Owns |
|---|---|---|
| `../Facrafting` | `facrafting` | The timed crafting model. Depends on nothing. |
| `../NeoProgressiveMaterials` | `neoprogressivematerials` | Intermediate products. Depends on Facrafting. |
| `../NeoProgressiveAutomation` | `neoprogressiveautomation` | The two mining drills. Depends on both. |
| `../CrumblingOre` | `crumblingore` | Ore depletion. Standalone. |

Do not move or rename them. Project Nauvis consumes them via `includeBuild`.
