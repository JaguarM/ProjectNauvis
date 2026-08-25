Next session
============

Written 2026-08-25 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

The job: give the assembler a block entity
------------------------------------------

`nauvis:assembling_machine_1` is registered, placeable, drops itself, and has a creative tab.
It is a block and nothing else: no inventory, no recipe selector, no ticking.

Milestone 1 is a chest feeding an inserter feeding this, and a chest taking what comes out.
The assembler is the piece that makes that sentence mean anything.

`PLAN.md` fixes the shortcut, and it is deliberately crude:

> recipe selector + one input inventory, running Facrafting's timed recipes

Fixed recipes, per-ingredient buffers, module slots and tiers are the *later* version. Do not
build them now.

**Non-negotiable #5 applies from the first commit.** A machine with no work and no power costs
zero ticks and wakes on neighbour change. It is cheap now and a horrible retrofit later.

What already works, and how to run it
------------------------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | runs every gametest headless, exits non-zero on failure |
| `./gradlew :nauvis:runServer` | dev server, all five mods, reaches Done in about 12s |
| `./gradlew :nauvis:runClientData` | blockstates, models, language |
| `./gradlew :nauvis:runServerData` | loot tables, tags |
| `./gradlew :nauvis:build` | includes `checkRecipes` |
| `python tools/gen_recipes.py --check` | the same recipe diff, on its own |

Three gametests pass. `NauvisGameTests.java` is the working example of the 26.2 API — copy its
shape rather than anything found online, and see the GameTest section of `API-26.2.md` for why.

The assembler's recipe already generates and is already correct: 3 electronic circuits, 5 iron
gear wheels, 9 iron plates, 10 ticks. Nothing about recipes needs doing here.

Where to look before writing code
---------------------------------

Non-negotiable #4 is not advice. Two entries in `API-26.2.md` decide the shape of this work:

- **Inventories.** `IItemHandler` is deprecated for removal. It is
  `Capabilities.Item.BLOCK` → `ResourceHandler<ItemResource>`, driven by a `Transaction`.
  This is the single biggest unknown in the task and worth reading the sources for first.
- **Saving.** `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`, not `CompoundTag`.

Facrafting's own source is the other reference: `FacraftRecipe` is a
`List<SizedIngredient>` plus an `ItemStackTemplate` result and `craftTicks`, and
`CraftPlanner.plan(items, recipe)` already answers "can this inventory make this?". Reuse it
rather than reimplementing matching — `../Facrafting/src/main/java/com/jaguarm/facrafting/`.

Suggested order
---------------

1. Block entity with an inventory, saving and loading. A gametest that puts a stack in and
   reads it back proves it before any crafting exists.
2. Recipe selection — a `facrafting:facraft` recipe chosen and stored on the block entity.
3. Ticking, sleeping when idle, and the craft itself. **The milestone-1 test:** place the
   assembler, insert 3 circuits + 5 gears + 9 iron, tick 10 times, assert one
   `assembling_machine_1` came out and the ingredients are gone.
4. Only then a screen, which is the half that needs eyes and is Yannic's to judge.

Textures, when it comes to that
-------------------------------

`../NeoProgressiveAutomation/texture-workshop/` is the approach that produced the drills, and
its README is the best writing in these repos on why vanilla textures look the way they do —
six colours for cobblestone, three ideas for a furnace face, never pure black.

`make_miner_textures.py` renders sixteen textures from three 16x16 ASCII maps plus one
five-tone palette per tier, using Pillow. Editing a map changes every tier together, so a
family cannot drift apart. The same trick will work for the machines here.

Until there is art, models point at vanilla textures on purpose — a *missing* texture renders
as the magenta checkerboard and reads as a broken model rather than as work not yet done.

A note on KubeJS
----------------

Worth knowing about, not usable yet, and narrower than it first looks.

**It is on 26.1.2, in beta, and not on 26.2.** Last 26.x build 2026-07-23; active development
continues on 1.21.1. Same position as AE2 — one Minecraft version behind us. `rhino`, which it
needs, is also 26.1.2. LGPL-3.0, so no obstacle to shipping once it ports.

Where it would genuinely help is **pack policy**: stripping vanilla recipes so the Factorio
tree is the only road forward is milestone 3, and it is one script against hundreds of
condition-false JSON files. It also overlaps almost entirely with the Item Obliterator idea,
and would do that job better.

Where it cannot help is everything in this document. A KubeJS script is not a mod, and
non-negotiable #3 requires each subsystem mod to stand alone — so block entities, ticking,
sleeping and capability handlers stay Java. The assembler is not a scripting problem.
