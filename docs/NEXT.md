Next session
============

Written 2026-08-26 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the task below is done — it describes one job, not the project.

The job: close milestone 1
--------------------------

*Chest → inserter → assembler → inserter → chest, burning coal.* Three of those five exist: the
assembling machine, the burner inserter, and the wooden chest, which is vanilla. What is left is
small and mostly known work.

**1. `nauvis_logistics:iron_chest`** — 8 iron, and the only thing between the pack and a
milestone 1 that does not lean on a vanilla chest. Factorio's is 32 slots; a vanilla
`MenuType.GENERIC_9x4` is 36 and needs no new screen, which makes this the one container in the
pack that can ship a working GUI for free. Slot count is behaviour, not identity, so 36 is a
legitimate shortcut — the id, the 8 iron and the 0.5s are not.

**2. Then power**: `nauvis_power:boiler`, `steam_engine`, `small_electric_pole`. Note the boiler
and the steam engine are both paid for in `nauvis_fluids:pipe`, so `nauvis_fluids` has to exist
far enough to register one item before either can be crafted. That is the ordering surprise in
milestone 1, and it is worth reading the mapping before planning around it.

**3. Then the electric `inserter`**, which is deliberately not registered yet: it costs an
electronic circuit and runs on the grid, and shipping it before there is a grid would mean an
item that works without the power it is supposed to need.

What already works, and how to run it
-------------------------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runServer` | dev server, the whole pack |
| `./gradlew :nauvis_logistics:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` | blockstates, models, language |
| `./gradlew :<mod>:runServerData` | loot tables, tags |
| `./gradlew build` | everything, including `checkRecipes` |
| `python tools/gen_recipes.py --check` | the same recipe diff, on its own |

Fourteen gametests pass: eight in `nauvis_machines`, five in `nauvis_logistics`, and one in the
pack mod asserting that every mod actually loaded.

Adding a subsystem mod is routine now, and `nauvis_logistics/` is the better template of the two
because it also ships a switched-off `crafting_table` datapack. It is a subproject in
`settings.gradle`, a `build.gradle` copied with the ids changed, a
`src/main/templates/META-INF/neoforge.mods.toml`, and two lines in `nauvis/build.gradle` — the
`runtimeOnly project(':...')` that puts it in the pack, and its namespace added to
`pack_gametest_namespaces` so `:nauvis:runGameTestServer` runs its tests too.

Recipes: generate into a staging directory with
`python tools/gen_recipes.py --only <modid> --out <tmp>`, then copy across only the files for
items that actually exist. `--write` would write all of that mod's recipes, and a recipe naming
an unregistered item is a load error.

How machines sleep, and how pullers do
--------------------------------------

Non-negotiable #5 has a worked pattern now, in two halves.

**A machine that owns its work** — the assembler — schedules its own block tick while it has a
craft running and stops when it does not. No `BlockEntityTicker`: a registered ticker runs
whether or not there is work. An unscheduled position is never visited, and scheduled ticks are
saved with the chunk, so work in progress survives a reload.

**A machine that watches a neighbour** — the inserter — has the harder problem, because its work
arrives in somebody else's inventory and a chest does not know it exists. The answer was already
in the game: every `BlockEntity.setChanged()` reaches all six neighbours as `onNeighborChange`,
so a chest gaining an item tells the inserter beside it exactly and immediately, for free. Filter
on the `neighbor` position before looking anything up. `docs/API-26.2.md` has the section, and it
also says why `BlockCapabilityCache`'s invalidation listener is the wrong place to wake from.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really
is asleep. Assert it for anything new — a machine that ticks and does nothing looks identical
from the outside and costs exactly as much. Both wake mechanisms were checked by deleting them
and watching the right test go red; do the same before trusting a new one. That check is worth
more than it sounds: with the inserter's wake deleted, the test that only watches items move
still passed.

Two traps that cost time here
-----------------------------

- **A machine spills its inventory from `BlockEntity#preRemoveSideEffects`**, not from
  `Block#affectNeighborsAfterRemoval`. The wrong one compiles, reads correctly, and drops
  nothing. Note `MinerBlock` in Neo Progressive Automation has exactly that override and only
  works because its entity is a `WorldlyContainer`; do not copy it.
- **A built-in datapack needs a `pack.mcmeta`**, or `AddPackFindersEvent` throws a bare
  `NullPointerException` naming neither the mod nor the directory.

What is still missing
---------------------

**The assembler has no screen.** Until it does, the block itself is the interface: right-click
holding an ingredient of what it is making to load it, holding anything else to re-point it,
empty-handed for a status line, sneak + empty hand to clear it. Enough to play, not what Factorio
does. The real thing needs a `MenuType`, an `AbstractContainerMenu` over `ResourceHandlerSlot`s,
a `Screen`, a serverbound packet — and a synced recipe list, because the client cannot enumerate
recipes since 1.21.4 (`OnDatapackSyncEvent#sendRecipes` + `RecipesReceivedEvent`; Facrafting's
`client/ClientRecipes` already does this and is worth reading first).

This is the half that needs eyes and it is Yannic's to judge, which is why it keeps being left
rather than guessed at. It is also getting more expensive to defer: the iron chest above wants a
screen too, and building both while one person looks at them beats building them apart.

**Personal crafts pay at the end, not the start.** Factorio takes a craft's ingredients out of
your inventory the moment you queue it, and they are gone. Facrafting's `CraftTicker` checks
affordability every tick and only calls `CraftPlanner.consume` when the craft finishes, so moving
the ingredients somewhere else mid-craft stalls the job instead. The job is kept and resumes when
they come back - it is not lost - but it looks like a queue that stopped for no reason, and it
was mistaken for a bug once already. Consuming up front and holding the result is the Factorio
behaviour; it needs a place to put ingredients that are spent but not yet delivered. Deliberately
left for now.

**Smaller, deliberate gaps.** Nothing tests that inventories survive a save and reload. The
assembler's input slots are unfiltered, so anything can go in any slot. The inserter's swing
speed — 30 ticks — is the one number in the pack not taken from Factorio's dump, because the dump
is recipes; Factorio's burner inserter is about 0.6 items a second and 30 ticks is that rounded.
And an inserter at a chunk border whose source chunk cycles while it stays loaded can sleep
through items appearing: `onLoad` covers its own chunk reloading, but not a neighbour's.

Textures, when it comes to that
-------------------------------

Both machines point at *vanilla* textures on purpose — a blast furnace body for the assembler, a
furnace body with a front face for the inserter, which is the one thing an inserter's model
genuinely has to communicate. A model naming a texture the mod does not ship renders as the
magenta checkerboard, which reads as a broken model rather than as art nobody has drawn yet.

`../NeoProgressiveAutomation/texture-workshop/` is the approach that produced the drills, and its
README is the best writing in these repos on why vanilla textures look the way they do — six
colours for cobblestone, three ideas for a furnace face, never pure black.
`make_miner_textures.py` renders sixteen textures from three 16x16 ASCII maps plus one five-tone
palette per tier, using Pillow. Editing a map changes every tier together, so a family cannot
drift apart. The same trick will work here.

A note on KubeJS
----------------

Worth knowing about, not usable yet, and narrower than it first looks.

**It is on 26.1.2, in beta, and not on 26.2.** Last 26.x build 2026-07-23; active development
continues on 1.21.1. Same position as AE2 — one Minecraft version behind us. `rhino`, which it
needs, is also 26.1.2. LGPL-3.0, so no obstacle to shipping once it ports.

Where it would genuinely help is **pack policy**: stripping vanilla recipes so the Factorio tree
is the only road forward is milestone 3, and it is one script against hundreds of condition-false
JSON files. It also overlaps almost entirely with the Item Obliterator idea, and would do that
job better.

Where it cannot help is everything in this document. A KubeJS script is not a mod, and
non-negotiable #3 requires each subsystem mod to stand alone — so block entities, ticking,
sleeping and capability handlers stay Java.
