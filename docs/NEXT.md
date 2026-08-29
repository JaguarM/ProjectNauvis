Next session
============

Written 2026-08-29 for whoever picks this up cold. Read `../CLAUDE.md` first, then this.
Delete or rewrite it when the jobs below are done — it describes the work in front of us, not the
project.

**Ninety-two gametests pass, the pack builds clean, and the client boots into a world.** Every
machine is the size Factorio made it, six checks run in `./gradlew build`, the transport belt
works, the burner inserter fuels itself off the belt it is unloading, the long-handed inserter
reaches two blocks, and **the crafting panel is Factorio's crafting menu** — four tabs, in
Factorio's order, with no "everything" tab in front of them and no grouping button beside them.

**And there is research.** All 216 of Factorio's finite technologies, generated from Wube's own
prototype data; a lab that works on whatever the world is researching and eats one of each pack
that technology names; unlocks that belong to the world rather than to a player; and a gate
Facrafting asks before it queues a craft, points a machine at a recipe, resolves an intermediate
or draws the grid. **It is playable end to end today**: red science is craftable, `automation`
costs ten units of it at ten seconds each, and it is what hands over the assembling machine and
the long-handed inserter — which is exactly where Factorio starts.

**And the vanilla road round it is closing.** `data/removals.json` is the pack's policy list and
`tools/gen_removals.py` turns it into a built-in datapack that is on by default. Four recipes so
far: the chest and the furnace, because the pack now prices them Factorio's way and two recipes
for one item means one of them is wrong; and the **hopper** and its minecart, because a hopper is
an inserter that costs nothing to run and makes the pack's three inserters pointless.

**Nobody has looked at the research screen in game, and nobody has played the first ten minutes
with no hopper and no bench recipe for a furnace.** Both are in the playtest list below, and the
second one is the bigger risk of the two.

What the tabs cost
------------------

Almost nothing in the panel and almost everything in the data, and it took three passes because
each one fixed something real and left the thing next to it wrong. That is the useful part of this
section: **a layout is four decisions — which buckets, what order inside one, what order the
buckets go in, and what else is on screen — and fixing one of them changes nothing a player can
see.**

**The buckets.** `tools/gen_recipes.py` was reading the dump's `type` field — eleven values,
Factorio's *item taxonomy* — where it wanted `category`, whose four values are the crafting menu's
actual tabs. Swapping the table is six lines. It now throws on an unknown category rather than
falling back to `""`, because `""` is what a recipe with no opinion sets, so a stale table was
indistinguishable from an author choosing not to group something.

**The order inside a tab, which was what the complaint was actually about.** The grid was sorted on
the recipe id; ids are namespaced; so every tab was *still* clustered by mod, and fixing only the
buckets would have changed the labels and nothing else. Factorio sorts on a per-item `order`
string, so `FacraftRecipe` has one — optional, defaulting to `""`, with ordered recipes first and
the rest following by id, so a pack that sets none is unchanged and a pack that sets some is not
half-sorted.

**The order of the tabs, which is the same field and deliberately not a second one.** Factorio's
strip reads Logistics, Production, Intermediate products, Combat, and the first attempt sorted tabs
by title — alphabetical, so Intermediate products came first while every recipe underneath it was
right. Factorio's own `order` strings are *one* global sequence that runs group by group, so a
group's place is simply where its first item falls. `stamp_order` numbers the dump that way — tab
rank first, display name second — and `RecipeTabs.buildByKey` sorts each tab by its earliest
recipe. The strip and the grid are one ordering read at two depths and cannot disagree. **The strip
order lives in `GROUP_BY_CATEGORY`, whose *key order* is the fact being asserted**, so Facrafting
still knows nothing about Factorio.

**What else was on screen.** The strip opened with an "everything" tab holding every recipe at
once, which is not a crafting menu anyone laid out and was the tab a player lands on — so the
pack's own layout was the second thing they saw. It is gone. Nothing became unreachable: every
recipe is in exactly one tab, uncategorised and ungrouped buckets included, and the search box
already reaches past the current tab, which is the job "everything" was really doing.

**Facrafting's default grouping is adaptive, and that was the only real decision in the mod.**
Flipping the constant from `CATEGORY` to `GROUP` is one line and wrong for every pack that is not
this one: with no groups set, `GROUP` opens on a single "Ungrouped" tab. So `RecipeTabs.
suggestedMode` counts — half the recipes carrying a `group` means the author laid the strip out and
their layout wins. It is a *suggestion*: `FacraftPanel.modeChosen` records that the player pressed
the grouping button and outranks it until logout. That flag is also why the panel compares
`builtFor` against `mode` when it renders — a datapack reload sends the recipes again with a panel
open, and a strip built for one grouping and labelled with another looks exactly like a rendering
bug.

**And the button itself is now a pack decision.** `Config.SHOW_GROUPING_BUTTON` is a Facrafting
client config, on by default, and this pack turns it off: the other two groupings are by creative
category and by mod, and since every subsystem mod registers its own creative tab so it can be
played standalone, both come out as *one tab per mod*. Neither is a view of this game, and offering
them makes the pack's own strip look like one arbitrary choice of three.

**`nauvis/pack/config/` is new and is where that setting lives.** A modpack is configuration as
much as it is a mod list, and `run/` is gitignored — so a setting edited in place there is a change
nobody else ever sees. `:nauvis:packConfig` copies this directory over `run/config` before every
run task. Its README carries the reasoning for each file, because **NeoForge rewrites a config that
does not match the mod's spec, comments included**, so prose written into a toml does not survive a
boot.

**What is still a stand-in is the order within a tab.** Factorio sorts inside a tab by subgroup and
then by a per-item order string, and `reference/factorio/recipes.json` carries neither — so ours
falls back to the display name, which is alphabetical and is not what Factorio does. **Nobody has
said whether that reads wrong**, and it is the one part of the panel that has not been judged by
somebody who knows where Factorio puts things. If it does read wrong, the fix is a dump with
Factorio's item order in it, and then `stamp_order` is the only function that changes; nothing
downstream of it knows where the number came from.

**A tier is a block, not a block entity.** Reach, swing time and draw are three numbers on
`ElectricInserterBlock`; `LongHandedInserterBlock` overrides them and a codec; one block entity
type is registered against both blocks. The fast and filter arms arrive the same way.

What the science tree cost
--------------------------

Milestone 3's biggest piece, and the whole of it is now in: 216 technologies generated from Wube's
own data, a `SavedData` per world, a lab that works on what the world is researching, four gates,
and a list to pick from. **The two decisions written up here last session were both right and both
survived contact**, so this section is what changed under them rather than a re-argument.

### The dump existed after all, and it was not the calculator

Last session's blocker was "the costs come from a `technologies.json` that does not exist yet",
with `KirkMcDonald/factorio-tools` as the lead. That lead was a dead end in the useful way: the
tools are a **Go loader that runs the game's own Lua data stage**, so they need an installed copy
of Factorio and produce nothing on their own. The calculator's shipped data
(`kirkmcdonald.github.io/data/vanilla-1.1.110.json`) has items, recipes, belts, machines — and no
technologies at all.

**`wube/factorio-data` is the answer, and it is Wube's own repository**, published for mod authors,
tagged for every version back to 0.5. `base/prototypes/technology/technology.lua` plus
`inserter.lua` is the entire tree, in Lua table literals.

**Which version is not a preference, and this is the part worth remembering.** `recipes.json`
names `science-pack-1`, `science-pack-2`, `science-pack-3`, `high-tech-science-pack`, `iron-axe`
and `logistic-chest-active-provider` — every one renamed or removed in 0.17. It is a **0.16 dump**,
which `MAPPING.md` already said in passing and nobody had had a reason to act on. So the tree is
0.16.51, `VERSION` in `fetch_technologies.py` says why, and the two files move together or not at
all. A 0.17 tree against a 0.16 recipe dump would have asked for science packs that do not exist
and unlocked recipes under names nothing in the pack uses — and it would have *loaded*, because a
`ResourceKey` validates nothing.

`tools/fetch_technologies.py` fetches and transcribes it. It contains a small Lua reader, and the
thing to know about that reader is how it is built to fail: anything outside table literals raises
rather than being skipped. It lost that bet exactly once, for
`create_follower_upgrade(1, 1, 1, 0, ...)` — a real Lua function that builds six technologies in a
loop — and the six are **printed by name** rather than silently missing. Reading them properly
would mean running Lua, which is what factorio-tools is for.

### Four dropped things, each a decision rather than an omission

`gen_technologies.py` prints all four every time it runs, which is the point:

| | |
|---|---|
| **18 technologies priced by a formula** | `count_formula = "2^(L-6)*1000"` — the fourteen infinite ones and the four levelled mining-productivity steps. "How many units" has no answer until a technology can have a level. **Not one of them unlocks a recipe.** |
| **169 effects with no mechanic here** | ammo damage, gun speed, robot speed, braking force, laboratory speed. The technology is written *without* them rather than not written, so the day a mechanic lands its technologies are already there |
| **8 recipes the pack does not model** | the oil-processing recipes, whose products `recipes.json` carries as raw inputs with no recipe of their own |
| **4 items the mapping skips** | the steel axe and the three underground belts. `PLAN.md`'s belt note is why |

The rest — all 216 — are written, **including the 128 that unlock nothing today**. That is the
same rule as recipes and it is deliberate: a technology's cost, prerequisites and unlocks are
identity, they go into world saves, and a technology that appeared later would move under a player
who had already researched past it.

### The mismatch nobody predicted: a technology unlocks a *recipe*, not an item

`optics` unlocks `small-lamp`, which makes a `lamp`. `solar-energy` unlocks
`solar-panel-equipment`, which makes a `portable-solar-panel`. Fifteen of Factorio's recipe names
are not its item names, and this pack's recipe files are named after **items**. So
`mapping.json` grew an `unlocks` table: fifteen aliases and eight nulls for the oil recipes that
have no counterpart here. A name in neither table is a `GenError` — same rule as an unmapped
ingredient, and for the same reason.

### What shipped, in the order it is worth reading

| | |
|---|---|
| `Technology` | the record. **Its three id fields are loose keys, not registry objects, and that is load-bearing** — a registry codec throws on an id nothing registered, and most of the tree names science packs no mod registers yet |
| `ModTechnologies` | the datapack registry, synced, entries in `data/nauvis_research/nauvis_research/technology/` |
| `ResearchState` | the `SavedData`. Per world, on the overworld's storage, whatever level asks |
| `Unlocks` | the one rule: **a recipe is locked when some technology unlocks it and none of those is finished.** A recipe no technology mentions is not locked, which is how belts and furnaces are craftable in the first minute |
| `Research` | the server's façade. Everything that moves the state goes through it, because every change has a second half — telling the clients |
| `LabBlockEntity` | asks what the world is researching, takes one of each pack *that technology* names, reports a unit |
| `RecipeLock` / `RecipeLocks` | **in Facrafting**, and it knows nothing about technologies |
| `compat/facrafting/FacraftingLock` | fills the hook in. Loaded behind a `ModList` check, so Facrafting stays `optional` |
| `ResearchScreen` | the list |

### Three things about it that are easy to get wrong later

**The lock's `revision()` is not decoration.** Research completes *while the crafting panel is
open* — that is what research is — and there is no event the panel could subscribe to that would
not amount to Facrafting knowing what a technology is. So the lock reports a number that moves,
the panel reads it every frame, and a strip built before a technology finished is rebuilt on the
frame after. `research_unlocks_a_recipe` asserts the number moves, which is the only half of that
a headless test can see; **the other half is a playtest**, and it is in the list below.

**Research is per world, which means it is shared between every gametest in a run.** This cost a
red test: `the_crafting_gate_is_installed` completed `automation`, which is `lab_researches`'s
current research, and `ResearchState.complete` clears `current` — so the failure reported *a lab
having done no work* and said nothing about research. Every test here now resets what it is about
to use, uses a technology nobody else does, and asserts one lab's own counters rather than the
world's.

**A fed, powered lab with no research selected cannot be woken.** Choosing a technology happens on
a screen, possibly in another dimension, and reaches no block — so it is outside the one-block
radius a `setChanged` covers, exactly like the long-handed inserter's problem. It rechecks once a
second (`LabBlockEntity.IDLE_RECHECK_TICKS`) and only while it actually has packs and power, so
the cost is bounded by the number of labs a player has loaded while researching nothing.

### What is left of milestone 3

**The items.** PLAN.md's list for this milestone is fifteen — `science-pack-2`,
`assembling-machine-2`, `steel-furnace`, `solar-panel`, `accumulator`, `medium-electric-pole`,
`steel-plate`, `battery`, `sulfur` — and none of them exist yet. The tree is already waiting for
every one of them: `steel-processing` unlocks the steel plate and the steel chest, `logistics`
unlocks the splitter and the underground belt, `solar-energy` unlocks the panel. **Adding an item
gives its technology teeth with no change to anything here.**

**The vanilla-replacement datapack**, which PLAN.md also puts at milestone 3, because once
research gates progression there is somewhere for stripped vanilla recipes to go. Separate job,
probably a KubeJS one once KubeJS ports; see the note at the bottom.

**The tree drawn as a tree.** The screen is a list on purpose — the registry, the `SavedData`, the
hook and the four gates are the same underneath, so replacing it touches `ResearchScreen` and
nothing else. Yannic wants the real thing eventually.

**A key of its own.** Factorio opens the technology screen with T; here it is a button on the lab.
One `KeyMapping` and one `ClientTickEvent`, and the default key is a decision — vanilla's T is
chat.

What the vanilla-replacement datapack cost
------------------------------------------

PLAN.md puts this at milestone 3 and gives the reason in one sentence: once research gates
progression there is somewhere for stripped vanilla recipes to go. It arrived because of a
playtest note - *"researching works but the facrafting are always visible and not linked to
science"* - and the first job was to find out whether that was a bug.

**It was not.** The gate was working: the save had `automation` and `steel_processing` already
researched, and `automation` is the only technology that gates anything the pack can currently
craft. A fresh world shows 15 of 17 recipes; that one showed 17. What the note was really
reporting is that **of the twenty recipes the pack ships, Factorio gates exactly two** - and the
other eighteen are craftable from the first minute in Factorio too. Research is linked to
crafting; there is nearly nothing yet for it to hold back.

The way to make progression mean something today is therefore not more gating. It is taking away
the road that goes round the whole tree, which is what this datapack is for.

### The rule, and why it is enforced

**A vanilla recipe is removed only when the pack can already do that job.** Every entry in
`data/removals.json` names a `replaced_by` recipe and the build fails if that recipe is not
shipped, so the pack can never take something away and leave nothing in its place. That is why
the list is four long and not forty: most of Factorio is not built yet, and the check says so
rather than letting somebody strip vanilla down to a pack you cannot play.

Two kinds of entry, and only one of them is a judgement:

- a **conflict** is a vanilla recipe making an item the mapping maps a Factorio item onto - two
  recipes, different ingredient lists, same item, and non-negotiable #1 says which is wrong.
  **This half is not a list, it is a check**: given Minecraft's own recipes, any pack recipe
  producing a vanilla item whose vanilla recipe is still there fails the build. Ship a recipe for
  a vanilla stand-in and its removal is compulsory in the same commit.
- a **bypass** is vanilla doing a job Factorio has a machine for. That is a judgement and is only
  ever a list. Today it is the hopper.

The line for bypasses is the same rule: **remove a vanilla system only once the pack ships its
replacement.** Inserters exist, so the hopper goes. The circuit network does not, so redstone
repeaters, comparators, observers and pistons all stay - taking those away now would delete a
Minecraft system and give nothing back for six milestones. The dropper stays too, and there is a
test asserting it, because it needs a clock to move anything: that is a build rather than a free
ride.

### The trap, which cost an hour and is now the best entry in the silent-failures list

Three of four byte-identical removals took effect. **The hopper did not.** See the entry below;
the short version is that NeoForge ships its own copy of about 380 of Minecraft's recipe files
and a mod's plain resources do not outrank it. The removals are a built-in datapack at
`Pack.Position.TOP` because of it.

What found it was `nauvis:vanilla_recipes_are_replaced`, and *how* it found it is the point: the
test asks a running recipe manager rather than reading the files off disk. A test that had
checked the files would have been green and wrong.

### What is left

**More items, and then more removals.** The conflict half is already waiting: the moment a mod
ships a recipe for `minecraft:redstone_lamp`, `minecraft:cobblestone_wall`, `minecraft:rail` or
`minecraft:iron_door`, the build will fail until vanilla's is removed - and three of those four
are gated behind a technology in Factorio, so each one is a real research unlock. None of them can
be done yet: the lamp needs an iron stick, the rail and the gate need steel, and the wall belongs
to `nauvis_military`, which does not exist.

The jobs, in the order Yannic asked for them
--------------------------------------------

The tabs were the first of these and the science tree was the second; both are done. The two
below are what is left of milestone 2 and are untouched by either, so they can be done in any
order.

### 1. The splitter

2×1 and directional — the first multi-block that is not square. `multiblock/` is the framework and
is copied into four mods already. The belt side of it is a run that ends at the splitter and two
that start after it, with the splitter alternating between them.

### 2. Fast-replace by tier

A belt in hand already points the belt you click on the way you are facing, which is half of
Factorio's belt-laying gesture. The other half is that a *faster* belt replaces a slower one, and
it cannot be written until there is a second tier to hold. It is `BeltBlock.useItemOn`, and what
changes is: swap the *block* rather than set a property — which does remake the block entity, so
`beltPlaced`/`beltRemoved` fire and `beltTurned` is not wanted on that path; carry the items on
that block across the block entity being remade, which will not happen for free; hand the old belt
back and pay for the new one unless the player is in creative; and refuse to *downgrade*, or a
stray click wrecks a bus. The run needs no thought — a run never spans two tiers, so the line
splits and rejoins by itself.

**There is no underground belt on any of this, and there will not be a `pipe-to-ground` either.**
Factorio needs both because it is flat — two belts that must cross have nowhere to go but under.
This pack is the same game with a Y axis, so a belt crosses another by changing level, which a
Minecraft player already knows how to build and needs no item for. All four ids
(`underground-belt` and its two upper tiers, and `pipe-to-ground`) are marked `skip` in
`data/mapping.json` with the reason; nothing else in the recipe graph uses any of them, so the
graph stays closed and `gen_recipes.py --check` counts them as skipped rather than missing.

The playtest, which is still owed
--------------------------------

Each job ends with `./gradlew build`, `:nauvis:runGameTestServer`, and a client boot. The client
boot is not optional: three of the last four bugs found in this pack were found by a person looking
at the game, and one of them — see the rotation entry in the silent-failures list — passed sixty-
three tests while being visibly wrong from three sides.

**The tab work is the argument making itself, twice over.** The complaint that started it came out
of a playtest. Then the fix went in, and *the tab strip came out alphabetical* — Intermediate
products, Logistics, Production — which every test passed, every check passed, and one screenshot
ended. A screenshot. **Nothing in this repo can see a tab strip**, and nothing will be able to;
that is a permanent hole, not a gap in the suite, and the only instrument for it is somebody
looking. The panel is now confirmed in game, including the config that hides the grouping button.

One question about it is still open and is in the tabs section above: **whether alphabetical
*within* a tab reads wrong** to somebody who knows where Factorio puts things. It is the last part
of the panel nobody has judged.

**The research screen has never been looked at**, and it is the newest thing here. Four things to
watch, and the last one is the one no test can reach:

- open a lab, press **Tech**, and see whether the list reads as a technology list at all — the row
  is a name, a cost, and the items it hands over, and nothing in this repo can say whether 320
  pixels is enough for that;
- **Automation should be the obvious first click** and it should say `10 x 10s` and show a red
  science pack. Everything else available on a fresh world is either priced in packs that do not
  exist yet — those say so instead of pricing themselves — or unlocks nothing;
- feed a lab, power it, pick Automation, and watch the bar on the lab and the bar on the research
  row move together. The lab's line reads `Automation - N units from this lab`, and a lab with no
  research picked says so rather than looking broken;
- **and the one this pack has been bitten by twice: leave the crafting panel open while the last
  unit finishes.** The assembling machine and the long-handed inserter should appear in it without
  the screen being closed and reopened. `RecipeLock.revision` is the mechanism and a gametest
  asserts the number moves; **whether the panel redraws is only visible to a person.**

**And the first ten minutes have changed, which is the biggest risk in this session's work.**
There is no hopper recipe, and the furnace and the chest can no longer be made at a bench - they
are Facrafting recipes now, at Factorio's prices, and Facrafting's panel is on the inventory
screen. That is the intended experience and it is also exactly how a new player gets stuck:

- start a fresh world, gather five cobblestone, and see whether making a furnace is **obvious**.
  If the panel is not the first place a Minecraft player looks, the pack has a first-five-minutes
  problem and the answer is probably a message rather than putting the recipe back;
- then build a burner inserter and check it does everything a hopper did - pulling from a chest
  into a furnace is the case worth trying, because it is the one every Minecraft player builds.

The rest of what is owed is older, and no boot has covered it. The long-handed inserter added a
model — a smoker-coloured cube, so it is the third furnace body on a belt line and wants a proper
look — but no session since has added a fluid or a plugin, and `check_gametests.py` and
`check_models.py` cover the registrations and the references between files. What is actually owed
is *watching four things no test can look at*:

- lay a coal belt, put a burner inserter beside it with an empty slot, and see it pick its own fuel
  off the line and keep running;
- put an inserter on each side of one belt and check they fill two lanes rather than fighting over
  one;
- put a long-handed inserter across a belt from a machine and watch it reach over — and, once it
  runs dry, watch how long the pause feels. `IDLE_RECHECK_TICKS` is one second, chosen on
  arithmetic and never on somebody's eye. **If it reads as a jam rather than as an arm, halve it**;
  it is one constant and the cost is linear in it;
- and tell the three of them apart at a glance on the same line, which is what the borrowed furnace
  textures are being asked to do and may well not do.

### One test was deleted, and what it was guarding is now guarded by nobody

`belt_carries_what_stands_on_it` is gone. It asserted that a dropped item entity moved more than
two blocks along a belt in sixty ticks, and it failed roughly one run in five — twice in the tab
session alone, on work that touched a Python generator and a client-side sort. Read the reason
before writing anything that tests a belt against an entity:

`ItemEntity.tick` only calls `move()` — and so only reaches `stepOn` — when the item is airborne,
has horizontal momentum, or `(tickCount + getId()) % 4 == 0`. A dropped item **resting** on a belt
has none of the first two, so **it is carried only on the ticks that arithmetic allows, at a phase
that depends on its entity id** — which depends on how many entities the tests before it happened
to spawn. The failing runs reported 0.5625 and 1.5 blocks; 0.5625 is exactly six belt steps. So the
distance a resting item travels is not a property of the belt and cannot be asserted. Adding a test
anywhere in that file moves the phase of every test after it.

**What went with it.** A belt is a bottom slab, so anything standing on one is inside the block
*above* it and vanilla's obvious `entityInside` hook never fires — `BlockBehaviour.stepOn` is the
only thing that works, and that test was the only thing pinning it fires at all. **Nothing now
fails if a belt stops carrying the player.** It is in the deliberately-missing list; the honest
version of that gap is that it belongs in the playtest, where standing on a belt takes two seconds
to check and a suite has never been able to check it at all.

### The belt, if you have to touch it

Read `nauvis_logistics/.../belt/` in this order and the whole thing falls out:

| | |
|---|---|
| `Belts.java` | the numbers, and why distances are integer sixty-fourths of a block |
| `BeltLane.java` | **the idea.** Items are stored as the gaps between them, not as positions, so a flowing belt writes one number a tick and a jammed one writes none |
| `BeltRun.java` | one line: its blocks, its two lanes, its tick, how it hands to the next line, and — `announceArrivals` — how anything beside it hears that an item turned up |
| `BeltLines.java` | every run in a level, and how lines are cut and joined when a belt is placed |
| `BeltAccess.java` | how everything else in the game meets a belt, and why giving and taking are two rules |
| `BeltShape.java` | how a corner knows it is one, and why there are two of them rather than eight |
| `BeltBlock.stepOn` | why standing on a belt carries you, and why that is not `entityInside` |
| `BeltBlock.useItemOn` | a belt in hand points the belt you click on the way you are facing |
| `client/BeltRenderer.java` | the items you can see |
| `texture-workshop/make_belt_textures.py` | the art, and why the tread scrolls at exactly 1.875 tiles a second |

Four things about it are load-bearing for anything built on top:

- **Items are pinned to a block and an offset into it**, never to a distance along a run. That is
  what makes cutting, joining, lengthening and turning a line safe, and it is asserted by
  `belt_survives_being_cut`.
- **The client runs the same simulation.** It builds the same runs out of the same block states and
  moves the items itself, so a belt full of items costs no network traffic. Only two things are ever
  sent — an item put on the belt from outside, and one taken off — because those are the only two a
  client cannot work out. **Anything added to belts must keep that property**, or the reason belts
  are affordable goes away. Neither of this session's changes touches it: refusing a lane is a
  decision made before the message that reports the insert, and `announceArrivals` is a signal to
  the blocks beside a belt rather than to a client.
- **A run is awake while it has items, not while it is moving.** No dormant sweep and no wake-up
  plumbing, because `BeltLane` makes a jammed belt cost the same as an empty one.
- **A belt is the one source in the pack that has to say out loud that something arrived**, and
  the reason is worth stating exactly, because the loose version of it is wrong. It is not "it has
  no block entity". `FluidNetwork` and `PowerNetwork` move things without one too and are perfectly
  audible — because they **push**: `FluidNetwork.push` calls `handler.insert` on the endpoint, and
  the `SteamTank` or `MachinePower` it lands in fires its callback. The consumer is woken by being
  filled. **The belt is the only subsystem that deliberately never pushes** — a belt running into a
  chest backs up, which is Factorio's rule and the reason inserters exist — so nothing it does ever
  touches the machine beside it, and `BeltRun.announceArrivals` is the only thing that can speak
  for it. The rule to carry forward: *a subsystem that moves things and never pushes them into the
  consumer has to announce, because nothing else will.*
- **A closed ring of belts is one run that wraps**, and **a block state change does not touch the
  graph** — turning a belt leaves its block entity alone, so `BeltLines.beltTurned` is a third way
  in that anything editing a belt in place will need.

The rules a machine is built to
-------------------------------

These outlive the jobs above and are the answer to "how big, how tall, and can you walk on it".

### Footprint is identity. Height is ours.

Non-negotiable #1 says ids, ingredients and craft times are Factorio-correct from the first commit,
because they live in world saves and in the player's head. **A footprint is the same kind of fact.**
Three-by-three is why an assembler column spaces the way it does; five-by-three is why a boiler
feeds a *row* of engines. Get it wrong and every blueprint a player carries in their head is wrong,
and fixing it later moves every machine in every world.

Height is the opposite. Factorio is two-dimensional and has no opinion, so height is ours to choose,
free to change, and the place to spend effort on making a machine look like something.

Footprints live in `data/mapping.json` as a `size` field and `tools/check_models.py` holds every
machine's cells to them. Heights live in the shape class next to the models and are tuned by
looking at them.

### One tile is one block

The scale is 1:1 and it is not really a choice: the belt, the inserter and the pipe are one tile in
Factorio and one block here, and everything else has to agree with them or nothing lines up. What
1:1 buys —

- **layouts transfer.** A player who knows that a boiler feeds two engines, or that assemblers sit
  three apart with a belt down the middle, builds the same thing here and it fits;
- **the walkways are already there.** Factorio's own layouts leave one-tile gaps to walk in, and a
  one-block gap is a corridor a Minecraft player walks down comfortably — the player is 0.6 wide;
- **ratios stay legible.** Twelve assemblers per engine is a number you can pace out.

### Walkability, in numbers

The metrics that decide this, all vanilla:

| | |
|---|---|
| player height | 1.8 blocks — a corridor needs 2 of headroom |
| player width | 0.6 blocks — a 1-block gap is walkable, and diagonal gaps are not |
| step height | 0.6 blocks — **anything colliding at 0.5 or below is walked straight over** |
| jump height | ~1.25 blocks — a 1-block machine can be jumped onto, a 2-block one cannot |

**A machine you can walk across beats a machine you walk around**, and it decides the silhouette.
A Factorio player tiles machines with no gaps, because in Factorio you can always walk round the far
end of the field. Nine 3×3 machines two solid blocks tall is a wall with no way over it, and the
player is sealed out of their own base.

The answer every machine in the pack now uses:

| | |
|---|---|
| **1.0** | the wall around the outside. One jump, and the only climb in a field of any size |
| **0.75** | the floor inside the wall. A quarter-block dip, under the 0.6 step, so crossing is walking |
| **2.0** | whatever the machine puts in the middle — the one thing you walk around |
| **0.5** | for anything meant to be crossed without even a jump: the electric drill, solar panels, and **belts** |

Two consequences. **The upper storey is mostly air**, so a 3×3 machine two blocks tall is ten blocks
and not eighteen — which is why `MachineShape` takes a set of cells rather than a box. And **the
tall part goes in the middle**, so tiled machines stand their obstacles apart and leave lanes.
`assemblers_tile_walkably` and `power_machines_tile_walkably` walk those lanes and assert every step.

**Collision and silhouette are allowed to disagree** — `PolePart` gives the crossarm a full outline
and no collision, because a shape three blocks over your head that you cannot see should not catch
you. But for something you are meant to walk across, the two agreeing is the point.

The multiblock mechanism, briefly
---------------------------------

`multiblock/` — `MachineCell`, `MachineShape`, `MachineParts`, `Boxes`, `Multiblock` — is copied
into `nauvis_machines`, `nauvis_power`, `nauvis_research` and `NeoProgressiveAutomation`, and
`tools/check_duplicated.py` holds the copies byte-identical (`--sync` pushes the original out; the
copies are never edited). It is `SmallElectricPoleBlock` with two more axes, and every class in it
says why it is the way it is. Read `MachineShape` and `Multiblock` and you have all of it.

The parts worth knowing before touching a machine:

- one block id, one item, an `IntegerProperty part` on every block, and the anchor found by
  arithmetic rather than a lookup — no block entity on the other cells;
- one `updateShape` rule is the whole teardown, which is why a shape's cells must be orthogonally
  connected, which the constructor enforces;
- capabilities are registered against the **block**, not the block entity, so any cell answers —
  that is what lets a pole supply a machine whose middle is out of range, and it is why
  `PowerNetwork` reduces endpoints to distinct handlers;
- **ports** name a cell and a face, so a boiler's steam leaves one block and an engine takes it at
  the open ends of its spine;
- geometry is stated once per machine and read by the model provider, the `VoxelShape` and the
  item model. `MachineParts` is the shared shell so the machines read as one family — **Yannic has
  said that still wants refinement**, and it is one file.

Where the pack stands
---------------------

| | |
|---|---|
| `nauvis_machines:assembling_machine_1` | 3×3 and ten blocks; recipe selector, six slots, timed craft, screen, 10 FE a tick |
| `nauvis_logistics:transport_belt` | half a block high and walked over; a run is one object however long, two lanes, items you can watch, and it carries you |
| `nauvis_logistics:burner_inserter` | takes from behind, gives in front, 30-tick swing, screen with a fuel slot. Fuels itself from what it picks up, so a coal belt keeps it alive |
| `nauvis_logistics:inserter` | the same on 2 FE a tick and a 24-tick swing. No slot, so no screen |
| `nauvis_logistics:long_handed_inserter` | the same arm reaching two blocks, over whatever is between. 3 FE a tick, a 17-tick swing, and the only block in the pack that does not sleep perfectly |
| `nauvis_logistics:iron_chest` | 36 slots on vanilla's four-row screen |
| `nauvis_fluids:pipe` | carries steam; a run is one object however long, with visible connections |
| `nauvis_fluids:steam` | a real fluid, so pipes and machines meet at NeoForge's capability |
| `nauvis_power:boiler` | 3×2 and seven blocks; burns fuel, steam out of the block under the chimney |
| `nauvis_power:steam_engine` | 5×3 and seventeen blocks; steam in at the open ends of its spine, 120 FE a tick out |
| `nauvis_power:small_electric_pole` | four blocks tall, climbable, wires itself to whatever it can reach |
| `nauvis_research:lab` | 3×3 and ten blocks; on 8 FE a tick, eats one of each pack the world's current research asks for and reports a unit |
| `nauvis_research:science_pack_1` | red science. Craftable now — copper plate and an iron gear wheel |
| `nauvis_research:technology` | 216 technologies, a synced datapack registry, generated. One of them — `automation` — gates something that exists |
| `neoprogressiveautomation:burner_drill` | 2×2 and five blocks; a full block with a chimney over the firebox |
| `neoprogressiveautomation:electric_drill` | 3×3 and nine blocks; a half-block deck you walk over, output head at the front |

Power numbers keep Factorio's ratios rather than its units: one engine runs twelve assemblers, one
boiler runs twenty-four. None of that is identity; the ids, ingredients and craft times are, and
those are generated.

**The crafting panel is Factorio's crafting menu.** Four tabs in Factorio's order — Logistics,
Production, Intermediate products, Combat — with the items inside one interleaved across mods
rather than clustered by them, no "everything" tab in front, and no grouping button beside the
search box. Combat is empty until there is a weapon. Every part of that comes from the recipes on
disk, so it is right for whatever subset of the pack is installed, and none of it is Facrafting
knowing anything about Factorio.

**The lab has a technology tree now**, and the seam the last session drew held exactly: the lab
still does not know what a technology is. It asks the world what is being researched, is handed a
list of packs, takes one of each and reports a unit. What changed in `LabBlockEntity` was two
handovers — which packs, and how long a unit takes — and not a rewrite.

**The lab's recipe woke up when belts landed.** It cost four transport belts and shipped with a
`neoforge:registered` condition so it would start working the day belts existed; when that day came
the `pending` flag came off `transport-belt` in `data/mapping.json` and the condition regenerated
away. `science-pack-2` costs an inserter and a belt and is unblocked the same way when it is
written.

How to run everything
---------------------

| | |
|---|---|
| `./gradlew :nauvis:runGameTestServer` | every gametest in every mod, headless, non-zero on failure |
| `./gradlew :nauvis:runClient` | the whole pack. **Boot it after any model, fluid or plugin change** |
| `./gradlew :<mod>:runGameTestServer` | one mod alone, to prove it still stands alone |
| `./gradlew :<mod>:runClientData` / `runServerData` | models and language / loot and tags |
| `./gradlew build` | everything, including `checkRecipes` |
| `python tools/gen_recipes.py --check` | the same recipe diff, on its own |
| `python tools/gen_technologies.py --check` | the same for the technology tree. `--write` to regenerate it |
| `python tools/gen_removals.py --check` | the vanilla recipes the pack takes away. `--write` to regenerate them |
| `python tools/fetch_technologies.py` | writes `reference/factorio/technologies.json` from Wube's data. Run once |
| `python tools/check_models.py` | every model, texture and blockstate reference, resolved — and footprints and belt speeds, against `data/mapping.json` |
| `python tools/check_duplicated.py` | the copied packages, against each other. `--sync` to fix |
| `python tools/check_gametests.py` | every gametest, for a type registered as well as an instance |
| `python tools/check_gui_layout.py` | every machine screen's boxes, for overlaps |
| `python texture-workshop/make_belt_textures.py` | the belt's art, from ASCII maps. `--preview` for a sheet |
| `./gradlew :nauvis:packConfig` | the pack's own config over `run/config`. Every run task already depends on it |

The seven checks are `checkRecipes`, `checkTechnologies`, `checkRemovals`, `checkModels`,
`checkDuplicated`, `checkGameTests` and `checkGuiLayout` in the root `build.gradle`, and all of them hang off
`:nauvis:check`. They read files and start nothing, so they cost a second between them.

`checkTechnologies` is stricter than `checkRecipes` in one way worth knowing: it fails on a file
the generator **no longer produces**, not only on one that differs. A stale technology would still
load, still show in the research list, and answer to nothing.

Adding a subsystem mod is routine: a subproject in `settings.gradle`, a `build.gradle` copied with
the ids changed, a `src/main/templates/META-INF/neoforge.mods.toml`, and two lines in
`nauvis/build.gradle` — the `runtimeOnly project(':...')` that puts it in the pack, and its
namespace added to `pack_gametest_namespaces`. `nauvis_power/` is the fullest template and
`nauvis_research/` is the newest — it was made by following exactly that list, so its diff is what
adding a mod costs.

Recipes: generate into a staging directory with
`python tools/gen_recipes.py --only <modid> --out <tmp>`, then copy across only the files for items
that actually exist. `--write` would write all of that mod's recipes, and a recipe naming an
unregistered item is a load error.

Pack settings for a mod the pack ships go in `nauvis/pack/config/`, not in `run/config` — `run/` is
gitignored, so a setting edited there is a change nobody else ever sees. Keep each file in the form
NeoForge writes it and put the reasoning in that directory's README; NeoForge rewrites a config
that does not match the mod's spec, comments included.

The patterns worth copying
--------------------------

**Sleeping.** No `BlockEntityTicker` anywhere: a registered ticker runs whether or not there is
work. Every machine schedules its own block tick while it has something to do and stops when it
does not. An unscheduled position is never visited, and scheduled ticks are saved with the chunk.

Four ways a machine learns it has work again, and one usually needs more than one:

- its own inventory changed (`onContentsChanged`);
- **electricity or steam arrived** — `MachinePower`, `InserterPower` and `SteamTank` exist only to
  carry that callback. A machine that ran dry has stopped scheduling ticks, so nothing it does can
  restart it: the wake has to come from whatever filled the buffer. Deleting one fails exactly one
  test;
- a *neighbour's* block entity changed — `onNeighborChange`, which every `setChanged()` reaches on
  all six sides. This is how an inserter hears a chest gain an item. Filter on the `neighbor`
  position before looking anything up;
- a neighbouring *block* changed (`neighborChanged`), plus `onLoad` for its own chunk reloading.

`level.getBlockTicks().hasScheduledTick(pos, block)` is how a gametest asserts a machine really is
asleep. **Assert it for anything new**, then delete the sleep logic and watch the test go red before
trusting it.

**One object per connected thing, three times over.** `PowerNetwork` for the grid, `FluidNetwork`
for pipe runs, and `BeltRun` for belt lines. Members join and leave; the network ticks once however many members
it has; one that moved nothing drops out of the active set. Read `nauvis_fluids/.../pipe/` first —
it is the smaller of the two, and its header says exactly why it is smaller: a pipe connects to the
six blocks it touches, which is what `neighborChanged` already reports, while a pole reaches 7.5
blocks and needs a spatial index and a level-wide hook to match.

**Capabilities are how mods meet.** No subsystem mod compiles against another. The grid moves FE
through `Capabilities.Energy.BLOCK`, steam moves through `Capabilities.Fluid.BLOCK`, and items
through `Capabilities.Item.BLOCK`. `SteamTank` even looks its fluid up by registry id rather than
importing it, so `nauvis_power` still loads with `nauvis_fluids` absent.

**Sided capabilities carry meaning.** A steam engine offers steam only on the two faces along its
axis, which is what makes its facing matter and what makes a pipe refuse its flank. Note that
*which faces answer* and *which way the machine looks* are two separate registrations with two
separate tests — breaking one leaves the other's test passing.

**A renderer that draws outside its own block has to say so.** `getRenderBoundingBox` defaults to
the one block the block entity sits in, and geometry reaching past it is frustum-culled away with no
error and nothing in the log. The wires between poles hit this exactly. See `API-26.2.md`.

**Multi-blocks are vanilla's job.** `SmallElectricPoleBlock` is four blocks on one `PolePart`
property, the way a door is two: refuse placement without headroom, place the rest from
`setPlacedBy`, and let one `updateShape` rule — a part whose vertical neighbour is wrong turns to
air — be the whole teardown.

**Transactions.** Spending and receiving happen inside one `Transaction`, so a result that will not
fit rolls back as though nothing happened. Passing `commit = false` turns the same method into the
simulation, so "can I?" and "do it" cannot drift apart.

**One interface.** Facrafting owns the crafting UI and its panel attaches itself to any container
screen. Machine screens grow out of that rather than sit beside it. Palettes are duplicated per mod
rather than shared, because a shared base in Facrafting would make these mods require it and kill
the `*_standalone` recipes that exist for its absence.

**Facrafting learns rules, not facts.** It is a general mod that this pack happens to be built on,
so nothing in it may know what Factorio is. The tab work is the worked example: the *rule* is "a
tab's place is the place of the first recipe in it", which is true of any pack; the *fact* is that
Factorio's strip reads Logistics, Production, Intermediate products, Combat, and it lives in
`GROUP_BY_CATEGORY` in this repo's generator, as the key order of a four-entry table. Same shape
for the default grouping — Facrafting counts how many recipes carry a `group` and decides, rather
than being told which mode this pack wants. **When a change to Facrafting needs a fact about
Factorio, the change is in the wrong repo**; find the rule that makes the fact expressible as data,
and put the data here. A pack config is the third form of this, for a fact that is neither a rule
nor in the recipes — see `nauvis/pack/config/`.

**The research gate is the fourth form, and the strongest one: a hook.** Facrafting had to stop
letting a player craft things, and there is no version of that which is a rule about recipes —
"unlocked" is not a property a recipe has, it is a question somebody else answers. So Facrafting
gained an *interface* (`RecipeLock`), a place to install one (`RecipeLocks`), and four calls to
it; with nothing installed everything is unlocked, which is what every pack had before. It does
not know what a technology is, has no dependency on `nauvis_research`, and would work the same for
a pack gating on advancements or on a quest book. **A rule Facrafting cannot express as data
becomes a hook, and the hook's default is the old behaviour.**

The mirror of that is on this side: `nauvis_research` declares Facrafting **optional** and still
installs the hook, because the installer lives in `compat/facrafting/` behind a `ModList` check
and the JVM resolves the reference only when that branch runs. Same trick as the Jade plugins,
same reason — the mod's standalone bench recipes exist for Facrafting's absence and would be
pointless if its absence were fatal.

Silent failures — these compile, pass tests, and are still wrong
----------------------------------------------------------------

**This is the section the next session most needs.** Every one of these shipped.

- **A model file renamed out from under its item.** `CUBE_COLUMN_HORIZONTAL` writes to
  `block/<name>_horizontal`; the item model defaults to `block/<name>`. The block rendered and the
  item was a checkerboard. Datagen reported nothing — both files were written exactly as asked. Call
  `registerSimpleItemModel(block, modelId)` explicitly whenever a template adds a suffix.
- **A fluid with no `FluidModel`.** Every registered fluid needs one via `RegisterFluidModelsEvent`
  in 26.2; `getStillTexture` on `IClientFluidTypeExtensions` is gone. Not skippable for a fluid
  never placed in the world — it is drawn wherever a tank is shown. NeoForge logs
  `Missing FluidModel for fluid` and nothing else complains.
- **A Jade provider with no config translation.** Jade's settings screen lists every provider and
  asserts if one has no name, and that assert fires from `ScreenEvent.Init` — so a missing
  `config.jade.plugin_<modid>.<uid>` key is not a blank line in a menu, it is a crash the moment any
  screen opens. Add the keys with the provider.
- **A Jade provider that is both halves.** Jade throws at registration if one object implements both
  `IServerDataProvider` and `IComponentProvider`. Outer data class, nested `Client`, shared uid.
- **A machine spills its inventory from `BlockEntity#preRemoveSideEffects`**, not from
  `Block#affectNeighborsAfterRemoval`. The wrong one compiles, reads correctly, and drops nothing.
- **A modded `Container` must register its own item capability.** NeoForge wraps vanilla's, but only
  for a hard-coded list of vanilla block entity types.
- **A built-in datapack needs a `pack.mcmeta`**, or `AddPackFindersEvent` throws a bare NPE naming
  neither the mod nor the directory.
- **A machine with a footprint is several endpoints on the grid, and they are the same machine.**
  Every block of a machine publishes the energy capability, so that a pole supplies a machine whose
  middle is out of range - Factorio's rule, and the reason footprints were worth having. One engine
  is then up to five entries in a pole's supply area: five shares of a shortfall, a wrong count in
  the readout a player reads, and eventually a machine sold energy it had just asked for through
  two of its own blocks. `PowerNetwork` reduces endpoints to distinct handlers by object identity
  each tick. Identity rather than position, so any mod's multi-block gets it for free.
- **Gametests have no padding by default, and machines now sprawl.** `TestData`'s last field is
  `padding` and it defaults to 0. With one-block machines that was survivable; with a seventeen-
  block engine and a chain of two reaching ten blocks, the machines of one test land in the next
  test along - where they are broken by its blocks or joined to its network. The failure then
  appears in whichever test ran second, which is the worst kind: real, silent, and blamed on the
  wrong code. Every test that builds a machine now asks for room.
- **A shape that builds its cells in a loop reads as smaller than it is.** `check_models.py` gets
  a machine's footprint by reading `new MachineCell(...)` calls out of the source, and a loop is
  not a number: the electric drill, written as a nested loop, reported itself as one tile by two
  and passed. The checker now refuses to guess when it cannot read every cell, and every shape
  writes its cells out. That is worth doing anyway — a cell's place in the list is its `part`
  value, and `part` values are in world saves.
- **`createBlockStateDefinition` runs inside `Block`'s constructor**, before any field of your
  subclass exists. A block that picks its blockstate properties from a field reads null and picks
  the wrong ones. The drills hit this: one `MinerBlock` with a tier field gave the electric drill a
  five-value `part` property and a nine-value default state. It threw at registration, which was
  luck — the same mistake between two shapes of equal size would have been silent. Answer with a
  constant on a subclass, which exists long before any block does.
- **A `VariantMutator` sets `y`, it does not add to it.** A multi-block turns twice over: each
  cell has its own quarter turn, and the machine has a facing. Generating that as
  `.with(cellDispatch).with(ROTATION_HORIZONTAL_FACING)` looks exactly right and is wrong in three
  directions out of four - the facing *overwrites* each cell's turn, so every corner of an
  east-facing boiler points the same way. Dispatch over both properties at once and add the two
  turns by hand.

  What makes it worth its own entry is how long it survived. `MachineCell` adds the two rotations
  before building its `VoxelShape`, so the collision was right the whole time and **the machine
  you saw and the machine you walked into were different objects** - which is the failure `Boxes`
  warns about in as many words, in a file written to prevent it. Every test passed, because tests
  look at collision and nobody can see a model from a gametest. It was found by a person turning a
  boiler round. `check_models.py` now checks each variant's `y` against the shape's own arithmetic.
- **A gametest whose type was never registered passes anyway, and breaks a client.** A test is
  two registrations: the instance, which runs, and the `MapCodec` type in
  `Registries.TEST_INSTANCE_TYPE`, which exists so a test *could* come from a datapack. Ours never
  do, so the type reads as dead paperwork - but the instance registry is synced to clients, and an
  entry with no codec throws `Failed to serialize ResourceKey[minecraft:test_instance / ...]` on a
  client boot while `runGameTestServer` stays green. Two went missing here for a day. The
  invariant is per *class*, not per name - a test id and a type id are different registries, and
  `nauvis:pack_loads` registered under type `nauvis:registry_presence` is fine - so
  `tools/check_gametests.py` matches every `GameTestInstance` that is registered to run against
  the codecs handed to `TEST_TYPES`.
- **A block put down by anything but a player never runs `getStateForPlacement`.** A command, a
  structure, another mod or `GameTestHelper.setBlock` all write the state you hand them, so a block
  that works out how it looks from its neighbours is drawn wrong and stays wrong: nothing changes
  beside it afterwards, so no `updateShape` ever comes. Belt corners were straight lines for
  exactly this reason, and only in a gametest, which is the lucky version of it. `BeltBlock`
  re-reads its own shape and its neighbours' when it joins the graph, which also covers the belt
  whose corner is in a chunk that had not loaded yet.
- **A horizontal `Entity.move` tells the entity it is falling.** `Entity.move` only decides
  whether something is standing on anything when the movement had a vertical component, so a push
  along a belt with `y = 0` clears `onGround`. Nothing looks wrong for a tick — and then the next
  tick's `stepOn` does not run, because that hook only fires for something on the ground, so the
  belt carries in stutters. For a player it also breaks fall damage and step sounds, both of which
  are worked out from the same flag. `BeltBlock.stepOn` reads `onGround()` before the move and puts
  it back after.
- **`GameTestHelper.spawnItem(Item, BlockPos)` spawns at the block's corner, not its middle.** An
  item dropped over a one-block-wide thing therefore hangs half off it and behaves like something
  standing beside it rather than on it. Use the `(float, float, float)` overload and add the half.
  This cost an hour of reading `ItemEntity` for a bug that was in the test.
- **A machine that moves items without a block entity is inaudible, and every test still passes.**
  The whole sleeping design rests on `setChanged` reaching all six neighbours, and every source in
  this pack got that for free — until the belt, which moves items along a run and touches no block
  entity at all. An inserter beside a belt was therefore woken only by items *put onto* its own
  tile, which is exactly what every test did, and never by items *travelling* to it, which is what
  actually happens in a factory. Eighty-one tests passed while any inserter unloading any belt
  stalled at the first gap in the flow. It was only found by deliberately placing the item four
  tiles away and making it walk. **When a new subsystem moves things, the test to write first is
  the one where the work arrives from a distance rather than being handed over.**

- **The wake signal has a radius of exactly one block, and nothing says so.**
  `updateNeighbourForOutputSignal` walks the six positions touching the block entity that changed.
  Every machine here is woken by that, and every machine here happened to have its work land next
  door — so "a `setChanged` reaches whoever cares" reads like a general fact right up until
  something reaches further than it does. The long-handed inserter is the first thing in the pack
  to have both of its ends outside that radius, and it fails *silently and late*: it works while
  items keep arriving and stops dead at the first gap, which is the same shape as the belt bug
  above and was found the same way, by making the work arrive from a distance instead of handing it
  over. **Anything that reaches past its own neighbours has to answer this question before it is
  written**, and the answer is in `InserterBlockEntity`.

- **Asking for a capability in an unloaded chunk loads it.** Check `level.isLoaded` first — not as
  an optimisation, but so a network at the edge of the loaded world does not drag chunks in.

- **A config file's comments do not survive a boot, so reasoning written into one is lost.**
  NeoForge compares every config against the mod's `ModConfigSpec` on load and rewrites anything
  that does not match — including replacing the file's comments with the spec's. A pack config
  explaining *why* a setting is what it is therefore reads correctly in the repo, is silently
  replaced the first time the game starts, and the explanation is gone from the only copy anyone
  will look at. `nauvis/pack/config/README.md` exists for exactly this: the tomls are kept in the
  form NeoForge writes them, and the prose lives beside them.

- **A layout is four decisions, and fixing one of them changes nothing you can see.** Which
  buckets, what order inside a bucket, what order the buckets go in, and what else is on screen.
  The crafting panel's tabs were wrong in all four, and each fix on its own would have looked like
  no change at all: correct tabs whose contents were still sorted by namespaced id read exactly as
  "grouped by mod"; correct contents under a strip sorted alphabetically read as a jumbled menu;
  and both of those sat behind an "everything" tab that a player landed on first, so the layout was
  never the thing they saw. **When a list reads wrong, name all four before changing one**, and
  expect to have fixed nothing visible until the last of them is right.

- **NeoForge ships its own copy of 394 of Minecraft's recipes, and its resources beat yours.**
  Removing a vanilla recipe is a file at `data/minecraft/recipe/<name>.json` holding nothing but
  `neoforge:never`, and in a mod's plain resources that works for most recipes and **silently does
  nothing for those 394** - NeoForge retags them so other mods' metals and woods work in vanilla
  recipes, and its datapack is applied after any mod's. The pack removed four recipes with four
  byte-identical files; `chest`, `furnace` and `hopper_minecart` went away and **`hopper` did
  not**, which is the shape of this failure exactly: no error, no log line, and three quarters of
  the change working. `rail`, `iron_door`, `dropper`, `dispenser`, `observer`, `piston`,
  `minecart` and `torch` are all on NeoForge's list too, so this would have come back. The answer
  is `AddPackFindersEvent` with `alwaysActive` true and `Pack.Position.TOP`, which sits above
  every mod's resources - see `nauvis/.../ModPacks.java`. **And the reason it was caught is that
  the test asks a running recipe manager**, not the files: a test that read the datapack would
  have been green.

- **The condition is `neoforge:never`, and there is no `neoforge:false`.** The registered names
  are `never` and `always`. A wrong name does not fail the build or the load - it throws while
  parsing that one recipe, which is a line in a log and a recipe that is still craftable.

- **A registry codec throws on an id nothing registered, and it throws while loading the file.**
  Most of the technology tree names science packs and recipes no mod in this pack registers yet -
  green science, the splitter, the steel plate - because the whole tree ships from the first
  commit and the items catch up with it. Written with `BuiltInRegistries.ITEM.byNameCodec()`,
  `automation` would have loaded and `advanced-electronics` would not, and the difference would
  have been one line in a log and a research list that was quietly short. `Technology` keeps its
  packs as `Identifier` and its prerequisites and unlocks as `ResourceKey`, all three of which
  are names that validate nothing, and a technology whose packs do not all exist reports itself
  unresearchable instead of not existing. **Whenever data ships ahead of the things it names, the
  reference has to be a name and not a lookup.**

- **A per-world `SavedData` is shared by every gametest in a run, and the failure surfaces
  somewhere else.** `the_crafting_gate_is_installed` completed `automation` to check the gate;
  `ResearchState.complete` clears the current research when it is the one completed;
  `lab_researches` was researching `automation` at the time. The red test said *a lab had done no
  work in 202 ticks* and mentioned research nowhere. Gametests get padding so their **blocks** do
  not collide - see the entry above - and nothing gives them separate **world state**. Every test
  that touches research now resets what it is about to use and uses a technology no other test
  names.

- **A clickable box drawn through a label reads the click anyway.** The research button was first
  put at (116, 16), which is inside the lab screen's status line - a full-width band from x=8 to
  x=168. Nothing rendered wrong at a glance, because the status text is usually shorter than that;
  what would have happened is a click landing on the button while the player was reading a status
  line that ran under it. `check_gui_layout.py` models both the title and the status line as
  full-width boxes and catches this exactly - **but only for boxes listed in its table**, and a
  button is not a bar, so it had to be added. It was: the lab's entry now names the button, the
  checker rejected the first position, and the button moved to the only free block on a 176-wide
  panel (right of the pack row).

- **An empty string is a valid group, so a stale lookup table is a layout choice.** `GROUP_BY_TYPE`
  fell back to `""` for anything it did not recognise, and `""` is exactly what a recipe with no
  opinion sets — so a category the table had never heard of landed silently in the panel's
  "Ungrouped" tab, indistinguishable from a pack author choosing not to group it. The generator now
  raises rather than defaulting. The shape generalises: **wherever "no opinion" and "I could not
  work it out" have the same representation, the second one has to throw.**

What is deliberately missing
----------------------------

**An accumulator cannot discharge.** `PowerNetwork` collects supply from endpoints that did not want
energy, so a battery would charge and never feed the grid. It wants a third case; there is no
accumulator until milestone 3.

**A network that moved nothing is re-checked every ten ticks rather than woken exactly.** It hears
about members and machines appearing the moment they do, but "a generator elsewhere filled up" is a
fact about a handler in another mod that owes us no signal.

**A long-handed inserter with power and nothing to do costs one look a second**, for the reason at
the top of this file: the wake signal has a radius of one block and its ends are two away. Every
other machine in the pack sleeps for free. Two things would remove it and neither exists — a hook
that fires when the block entity at a watched position changes, or a reason to believe every source
a long arm reaches is one of ours and can be made to announce.

**No brownout.** PLAN.md wants a machine whose buffer cannot refill to run *slower*; ours stops.

**No pipeline length limit.** Factorio caps a fluid segment at 320 pipes and its tooltip says
`6/320`; ours says `6 pipes` because we enforce nothing. Adding the cap is a real gameplay change —
refusal to connect, not just a number — if it is ever wanted.

**Personal crafts pay at the end, not the start.** Facrafting's `CraftTicker` checks affordability
every tick and consumes on completion, so moving ingredients away mid-craft stalls the job rather
than losing it. It looks like a queue that stopped for no reason, and has been mistaken for a bug.

**Factorio's recipe picker is a modal** anchored to the machine; ours is a persistent column beside
the screen. The modal is the more faithful one. A Facrafting change, and it wants Yannic's eye.

**Within a tab, recipes are in alphabetical order, and Factorio's are not.** Factorio sorts by
subgroup and then a per-item order string; the dump has neither, so `stamp_order` falls back to the
display name. It is the last part of the panel nobody has judged — see the tabs section. The fix is
a dump with Factorio's item order in it, and then `stamp_order` is the only thing that changes.

**Facrafting's grouping button is hidden in this pack, not removed.** `Config.SHOW_GROUPING_BUTTON`
defaults on, because a pack that has not laid its recipes out genuinely wants the choice. Turning
it off is `nauvis/pack/config/facrafting-client.toml`, and the reasoning is in that directory's
README.

**A belt does not load a chest.** Deliberate and Factorio-faithful: a belt running into a container
backs up, and taking things off a belt is what inserters are for. It is one method — `BeltRun`'s
hand-off — if it is ever wanted the other way. `belt_does_not_load_a_chest` pins it.

**Crouching stops a belt carrying you**, which Factorio does not do — there a belt has you whatever
you do. It is in for the Minecraft reflex: without it, placing a machine beside a working belt means
being carried off mid-click. One line in `BeltBlock.stepOn` if it is ever unwanted.

**A dropped item entity is carried erratically.** `ItemEntity.tick` only calls `move` — and so
only reaches `stepOn` — when the item is airborne, has horizontal momentum, or the tick count plus
its entity id is divisible by four, so something *resting* on a belt is pushed on a fraction of
ticks and at a phase that depends on its id. A player is carried properly; a thrown item is not.

**And so nothing tests that a belt carries anything at all.** The one test that did asserted a
distance, which for a resting item is not a fact about the belt, and it was deleted rather than
weakened — see the section in the job list. The hook it covered is `stepOn`, which a belt needs
because it is a bottom slab and `entityInside` therefore never fires for something standing on it.
**A belt that stopped carrying the player would pass every test in this repo.** Stand on one during
a playtest; it is two seconds, and it is the only instrument there is.

**A belt does not turn you as it carries you.** An entity on a corner is pushed the way that block
faces, so going round a bend on a belt is two straight shoves rather than an arc. Items do curve.

**Two belt tiers meeting is two runs, not one.** Correct — Factorio's transport lines split at a
tier change too — but there is only one tier so far, so it has never been looked at.

**Client and server belt runs can differ at a chunk edge**, because a client only has the belts in
its loaded chunks and a run is built from whatever belts are there. What that costs is a belt at the
very edge of the loaded world appearing to back up when it is not. Nothing is out of step where a
player can see it, and a chunk arriving re-seeds that block's items from the block entity.

**A technology that unlocks nothing is still offered.** 128 of the 216 unlock no recipe, because
their only effects are mechanics this pack does not have - ammo damage, gun speed, robot speed,
laboratory speed. They cost science and give nothing until the mechanic exists, which is a trap
from the player's side and fidelity from the tree's. They were kept because a technology's cost
and place in the graph are identity and go into world saves; a tree that grew technologies later
would move under a player who had already researched past them. The mitigation is presentation and
is not built: the research row could say *no effect yet*, which is true and cheap.

**Nothing gates a vanilla bench recipe.** The `crafting_table` datapacks each ship a shapeless
copy of every recipe, off by default, so a pack author can trade the timed crafts away - and a
vanilla crafting recipe never goes near Facrafting, which is where the gate lives. There is no
hook that would let it. So the datapacks' labels now read **"(skips research)"**, which is not a
caveat but the point, and is the one line a player reads before turning one on.

**Vanilla's own progression is barely touched.** Four recipes are removed. Everything else
Minecraft can build - redstone logic, pistons, minecart automation, brewing, the whole of it - is
untouched, because the rule is that nothing is taken away before the pack can do that job, and the
pack does twenty jobs. The conflict half of `data/removals.json` is a check rather than a list and
will force the rest as items land; the bypass half is a judgement and grows one line at a time.

**The circuit network's vanilla equivalent is deliberately left alone.** Redstone repeaters,
comparators, observers and pistons are Minecraft's answer to Factorio's combinators, and removing
them now would take away a system and offer nothing until milestone 7.

**A lab that has never had a technology picked rechecks once a second.** Choosing research happens
on a screen and reaches no block, so it is outside the one-block radius `setChanged` covers - the
long-handed inserter's problem exactly. Bounded: only a lab that has packs *and* power *and* no
research pays it, which is a state that lasts as long as it takes to open a screen. Every other
lab still sleeps for free.

**Research progress is sent to every client on every unit.** The whole state, not a delta: a list
of finished keys, one optional key and an int, on a message that fires at best once every five
seconds of one lab's work. It buys the property that a client is either exactly up to date or
exactly one message behind. It would want revisiting long before it hurt.

**Six technologies are missing and they are the follower-robot counts.** `technology.lua` builds
them with a Lua function, and reading that means running Lua, which needs the game installed.
`fetch_technologies.py` prints them by name. All six are `maximum-following-robots-count` and
unlock nothing.

**Eighteen more are missing because they are priced by a formula.** The fourteen infinite
technologies and the four levelled mining-productivity steps set `count_formula` instead of a
count, so "how many units" has no answer until a technology can have a level. None of them unlocks
a recipe either.

**Research cannot be un-researched in game**, and there is no command for any of it.
`ResearchState.forget` exists and is used only by the gametests. A `/research` command is an
afternoon and would make the tree testable by hand.

**Smaller.** Nothing tests that inventories survive a save and reload, and nothing tests that a
network rebuilds after a chunk cycle — both paths exist and are only reasoned about. The assembler's
input slots are unfiltered. An inserter at a chunk border whose source chunk cycles while it stays
loaded can sleep through items appearing.

Jade, and a note on dependencies
--------------------------------

The look-at readout is Jade — `maven.modrinth:jade:${jade_version}`, `compileOnly` in the subsystem
mods and `runtimeOnly` in the pack. Its plugin classes load only when it is present, so nothing has
to declare it required. PLAN.md's section covers the rest.

**Licences are not a decision point for including or depending on a mod here.** The pack is not
monetised and ships the way thousands of CurseForge packs do. Weigh version support, API shape and
maintenance instead. This does not extend to *copying*: CLAUDE.md's rule that code is read and
reimplemented with attribution, and that assets are never copied, still stands and is a separate
matter.

A note on KubeJS
----------------

Still on 26.1.2 and in beta — one Minecraft version behind us. LGPL-3.0, so no obstacle once it
ports. Where it would help is **pack policy**: stripping vanilla recipes so the Factorio tree is the
only road forward is milestone 3, and it is one script against hundreds of condition-false JSON
files. Where it cannot help is machines — non-negotiable #3 requires each subsystem mod to stand
alone, so block entities, ticking and capability handlers stay Java.
