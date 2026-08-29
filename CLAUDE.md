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

**2. Recipes and technologies are generated, never hand-written.**
`reference/factorio/recipes.json` is the spec: 214 entries, a closed graph, `time` already in
seconds. `data/mapping.json` says what stands in for what. `tools/gen_recipes.py` turns the
two into recipe JSON. If you are about to type a recipe by hand, you are doing it wrong.

**A research cost is the same kind of fact as an ingredient list** — units, packs, seconds,
prerequisites — so the technology tree is generated too, from `data/technologies.json` by
`tools/gen_technologies.py`. A technology is paid for in science, or finished by a trigger —
*craft fifty iron plates* — and the first ones are triggered, which is what lets a new world
research its way to a boiler and a lab before it has any science at all.

Run `python tools/gen_recipes.py --check` and `python tools/gen_technologies.py --check` before
trusting anything on disk. They diff the generated output against the committed files, and the
recipe one has already caught two wrong recipes in a released mod. Seven items genuinely cannot
be generated; the tool names them rather than letting you discover it in-game. Both are wired
into `./gradlew build`.

**3. One mod per subsystem, arrows pointing one way.**
Each mod is a separate jar with its own permanent mod id, usable standalone, glued to the
others by `neoforge:mod_loaded` recipe conditions. No cycles. Pack policy — vanilla recipe
removal — lives in the `nauvis` mod or the pack datapack, never inside a subsystem mod. It is
`data/removals.json` plus `tools/gen_removals.py`, and its rule is enforced by the build: **a
vanilla recipe is removed only when the pack can already do that job**, so nothing is ever taken
away and left with nothing in its place.

**Facrafting is the exception, and the foundation.** It owns the timed crafting model and the
crafting interface, and a subsystem mod may depend on it at compile time: a machine that runs a
`FacraftRecipe` has to be able to name the type, and a machine screen should be an extension of
Facrafting's panel rather than a second one beside it. Declare it `required` in
`neoforge.mods.toml` when you do — a mod that cannot work without another must say so.

Everything else stays coupled by data. No subsystem mod depends on another subsystem mod, and
the pack mod depends on nothing at compile time; if two subsystems need the same code, either it
belongs in Facrafting or it gets duplicated. No cycles, ever.

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

They live in their own repos at `../` and Project Nauvis consumes them via `includeBuild`, which
means an edit to one is picked up here immediately — no publishing step. **Two of them are
released and two are not, and that is the whole difference:**

| Repo | Mod id | Owns | |
|---|---|---|---|
| `../Facrafting` | `facrafting` | The timed crafting model, and the crafting UI. | **ours to change** |
| `../NeoProgressiveMaterials` | `neoprogressivematerials` | Intermediate products. | **ours to change** |
| `../NeoProgressiveAutomation` | `neoprogressiveautomation` | The two mining drills. | released |
| `../CrumblingOre` | `crumblingore` | Ore depletion. | released |

**Facrafting and Neo Progressive Materials are not published** — no remote, no tags, and NPM is
not even a git repository. Change them freely: they are part of this project, and Facrafting in
particular is the foundation the rest builds on. Its crafting panel is the interface every
machine screen should grow out of rather than sit beside.

**Neo Progressive Automation and Crumbling Ore are on GitHub and in players' worlds.** Their ids
are permanent and their behaviour should not change under an existing save.

Three ids are frozen anyway, whatever the above says, because the released NPA names them in its
own shipped recipes: **`facrafting:facraft`**, **`neoprogressivematerials:electronic_circuit`**
and **`neoprogressivematerials:iron_gear_wheel`**. Renaming any of those breaks a mod that is
already out. Check `grep -rho "neoprogressivematerials:[a-z_]*\|facrafting:[a-z_]*"
../NeoProgressiveAutomation/src/main/resources` before assuming an id in those two mods is free.

The four repos are still four repos. Folding the two unreleased ones into this one is a
reasonable thing to want and a separate job from changing them — ask before doing it, because
their git history is not this repo's to rewrite.
