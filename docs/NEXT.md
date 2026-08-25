Next session
============

Written 2026-08-25 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

The job: give the assembler something to feed it
------------------------------------------------

`nauvis_machines:assembling_machine_1` is finished as far as milestone 1 needs it. It holds
ingredients, it is told what to make, it makes it in Factorio's own time, it hands the result
to whatever asks, and it sleeps when there is nothing to do. Eight gametests hold it to that.

Milestone 1 is *chest → inserter → assembler → inserter → chest*. Two of those four exist. The
next thing to build is **the inserter**, in a new `nauvis_logistics` subproject — and with it
`nauvis_logistics:iron_chest`, since a wooden chest is vanilla and an iron one is not.

`PLAN.md` fixes the shortcut and it is deliberately crude:

> directional block moving stacks between neighbours on a timer

No swing animation, no per-item hand, no filter or stack or long variants. It takes from the
block behind it and gives to the block in front, both through `Capabilities.Item.BLOCK`, which
the assembler already publishes and vanilla chests already have.

**Non-negotiable #5 applies from the first commit**, and there is now a worked pattern for it —
see "how the assembler sleeps" below. An inserter with nothing on either side must cost nothing.

What already works, and how to run it
-------------------------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runServer` | dev server, the whole pack |
| `./gradlew :nauvis_machines:runGameTestServer` | that mod alone, to prove it still stands alone |
| `./gradlew :nauvis_machines:runClientData` | blockstates, models, language |
| `./gradlew :nauvis_machines:runServerData` | loot tables, tags |
| `./gradlew build` | everything, including `checkRecipes` |
| `python tools/gen_recipes.py --check` | the same recipe diff, on its own |

Nine gametests pass. Eight of them are `nauvis_machines`'; the ninth is the pack mod asserting
that every mod actually loaded.

Adding a subsystem mod is now a known quantity: `nauvis_machines/` is the template. It is a
subproject in `settings.gradle`, a `build.gradle` copied from that one with the ids changed, a
`src/main/templates/META-INF/neoforge.mods.toml`, and two lines in `nauvis/build.gradle` — the
`runtimeOnly project(':...')` that puts it in the pack, and its namespace added to
`pack_gametest_namespaces` so `:nauvis:runGameTestServer` runs its tests too.

Where the assembler ended up, and why
-------------------------------------

It started as `nauvis:assembling_machine_1` in the pack mod. `data/mapping.json` — the file that
decides ids — calls it `nauvis_machines:assembling_machine_1`, and non-negotiable #1 makes an id
permanent from the first commit, so it moved. The pack mod is now empty of content and waiting
for its own sixteen raw resources at milestone 3.

**`nauvis_machines` has a compile-time dependency on Facrafting**, the only one in the pack. An
assembler *is* a machine that runs a `FacraftRecipe`: it reads the ingredients and the craft
time off one, and pays for a craft with `CraftPlanner.plan`. That cannot be done through recipe
conditions. The arrow points one way, there is no cycle, and the pack mod and the four released
siblings still have no compile dependency on anything. Facrafting is `required` in the mod's
`neoforge.mods.toml` for the same reason — without it the machine would be a decorative block.

How the assembler sleeps, and why the inserter should copy it
--------------------------------------------------------------

Not a `BlockEntityTicker`. A registered ticker runs every tick whether or not there is work,
which is exactly what non-negotiable #5 forbids at Factorio scale.

Instead the block entity **schedules a block tick for itself** — `level.scheduleTick(pos, block,
1)` — while a craft is running, and simply stops scheduling when there is nothing to make. An
unscheduled position is never visited. Scheduled ticks are saved with the chunk, so a craft
survives a reload. It wakes from three places: the inventory changing (`onContentsChanged`), the
recipe being set, and `neighborChanged`.

`level.getBlockTicks().hasScheduledTick(pos, block)` is both the guard against queueing two
ticks and the way a gametest asserts that a machine really is asleep, which
`assembler_sleeps` and `assembler_stalls_when_full` both do. Assert it for the inserter too — a
machine that ticks and does nothing looks identical from outside and costs exactly as much.

**One trap to copy the fix for**, if the inserter ever holds a stack of its own: a machine spills
its inventory from `BlockEntity#preRemoveSideEffects`, *not* from
`Block#affectNeighborsAfterRemoval`. The base implementation only drops contents for a
`Container`, so a `ResourceHandler` inventory that does not override it eats everything on every
break — silently, with code that reads correctly. It was written the wrong way here first and
`assembler_spills_when_broken` is what caught it. `docs/API-26.2.md` has the detail.

The other pattern worth copying is the craft itself. Spending ingredients and banking the result
happen inside one `Transaction`, so a result that will not fit rolls the ingredients back as
though nothing happened — and passing `commit = false` to the same method turns it into a
simulation, so the "can I?" and the "do it" cannot drift apart. `docs/API-26.2.md` now has a
section on `ResourceHandler` and one on ticking without a ticker; both were written from the
sources while building this.

What the assembler still does not have
--------------------------------------

**A screen.** Until there is one, the controls are the block:

- right-click holding an ingredient of what it is making — load it;
- right-click holding anything else — make that instead;
- right-click empty-handed — say what it is making;
- sneak + right-click empty-handed — forget the recipe.

That is enough to play milestone 1 — a machine can be pointed at a recipe, fed by hand and
emptied by hand, with no inserters and no screen — but it is not what Factorio does. The real
thing is a recipe list you click. It needs a `MenuType`, an `AbstractContainerMenu` over
`ResourceHandlerSlot`s, a `Screen`, and a serverbound packet to set the recipe — plus the fact
that **the client cannot enumerate recipes** since 1.21.4, so the recipe list has to be synced
(`OnDatapackSyncEvent#sendRecipes` + `RecipesReceivedEvent`; Facrafting's `client/ClientRecipes`
already does exactly this and is worth reading first).

This is the half that needs eyes, and it is Yannic's to judge — which is why it was left rather
than guessed at.

Two smaller gaps, both deliberate: nothing checks that ingredients survive a save and reload
(the code is symmetric and the miner's is the same shape, but no test proves it), and inputs are
unfiltered, so anything can be put in any input slot. Per-ingredient buffers with recipe-aware
filters are the later version of the whole class.

Textures, when it comes to that
-------------------------------

`../NeoProgressiveAutomation/texture-workshop/` is the approach that produced the drills, and
its README is the best writing in these repos on why vanilla textures look the way they do —
six colours for cobblestone, three ideas for a furnace face, never pure black.

`make_miner_textures.py` renders sixteen textures from three 16x16 ASCII maps plus one
five-tone palette per tier, using Pillow. Editing a map changes every tier together, so a family
cannot drift apart. The same trick will work for the machines here.

Until there is art, models point at vanilla textures on purpose — a *missing* texture renders as
the magenta checkerboard and reads as a broken model rather than as work not yet done.

A note on KubeJS
----------------

Worth knowing about, not usable yet, and narrower than it first looks.

**It is on 26.1.2, in beta, and not on 26.2.** Last 26.x build 2026-07-23; active development
continues on 1.21.1. Same position as AE2 — one Minecraft version behind us. `rhino`, which it
needs, is also 26.1.2. LGPL-3.0, so no obstacle to shipping once it ports.

Where it would genuinely help is **pack policy**: stripping vanilla recipes so the Factorio tree
is the only road forward is milestone 3, and it is one script against hundreds of
condition-false JSON files. It also overlaps almost entirely with the Item Obliterator idea, and
would do that job better.

Where it cannot help is everything in this document. A KubeJS script is not a mod, and
non-negotiable #3 requires each subsystem mod to stand alone — so block entities, ticking,
sleeping and capability handlers stay Java. The inserter is not a scripting problem.
