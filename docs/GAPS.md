What is deliberately missing
============================

Known holes, each a decision rather than an oversight. If something looks broken, look here before
treating it as a bug. `PITFALLS.md` is the other list: things that *are* wrong.

Power and fluids
----------------

- **An accumulator's rates are per tick because the network asks once a tick.** Forty FE in and
  forty out are the handler's per-call limits, and `PowerNetwork` makes one call each way at most;
  a second network cannot reach the same accumulator, since poles that see each other merge. A mod
  that drove the handler itself every tick could move more, and nothing here stops it.
- **An accumulator covers the whole shortfall, up to its rate, at any charge level.** Factorio's
  does the same. There is no reserve, no low-charge cut-off, and no circuit-network reading of it;
  the readout on a pole says how full the network's accumulators are between them, and that is the
  only place the number is shown.
- **A network that moved nothing is re-checked every ten ticks** rather than woken exactly. It hears
  about members and machines the moment they appear, but "a generator elsewhere filled up" is a fact
  about a handler in another mod that owes us no signal.
- **No brownout.** PLAN.md wants a machine whose buffer cannot refill to run *slower*; ours stops.
- **A dark or roofed solar panel looks up every ten seconds.** The sun has no event, so a panel
  with nothing to make schedules one tick two hundred ticks out rather than waiting for a signal
  that will never come. It is the one bounded poll in `nauvis_power`, and it is the reason a panel
  starts making power within ten seconds of dawn rather than on the tick.
- **A solar panel loses output in rain, and Factorio's does not.** The panel reads the sky's
  darkening, which weather is part of. A panel that ignored the rain over it would read as broken
  in Minecraft, so this is a divergence the pack keeps; it is worth a third of the output while it
  rains.
- **No pipeline length limit.** Factorio caps a segment at 320 pipes and its tooltip says `6/320`;
  ours says `6 pipes`. Adding the cap is a real gameplay change — refusal to connect, not a number.
- **A row of boilers levels its water rather than flowing it.** Factorio's boilers pass water
  through end to end by its flow model; here each boiler draws from the one beside it until the
  two hold the same, half the difference at a time, which converges in a step and lets the row
  sleep. It means the far end of a long row fills more slowly than the near end, and a row fed at
  one end only runs as fast as that levelling carries water along it. Pipe the row at both ends,
  or along its front, and it does not matter.
- **A boiler holds a tick of water short of full and burns nothing for it.** Fuel is spent per
  tick of running, and a tick makes as much steam as there is room and water for; a boiler that
  is one water short makes one steam for a tick of coal. Factorio's does the same in its own
  units.
- **A long-handed inserter with power and nothing to do costs one look a second**, because the wake
  signal has a radius of one block and its ends are two away. Every other machine sleeps for free.
  Two things would remove it and neither exists: a hook that fires when the block entity at a
  watched position changes, or a reason to believe every source a long arm reaches is one of ours.

Oil
---

- **An oil well is not finite, and that is Factorio's rule, not a softening of it.** A well is pumped
  down ten a cycle to a floor — 60000, or a fifth of what it started with, whichever is more — and
  pumps at that rate for ever. Two a second from a well that began at 100%. A player who runs their
  only field dry would have no way to plastic, and Factorio never lets that happen. The readout
  says *At its floor* so the falling number is not mistaken for a countdown to nothing.
- **Where fields go is an approximation of Factorio's autoplace, and how rich they are is half
  Wube's numbers.** The prototype states `additional_richness = 220000` and a distance factor of
  `max((1300 + d) / 2600, 1)`, and both are used as written. The density term on top — the spread
  between 90% and 200% at the edge of the starting area — is this pack's, chosen to match what a
  Factorio player finds there, because the real one lives in a noise program this pack has no copy
  of. **Fields are four times as common as Factorio's**: one per 300 chunks against the one per 1200
  that would be its 1.8 per square kilometre, and none within 150 blocks of the origin rather than
  Factorio's wider starting area. Factorio's oil is rare and easy to find because the map shows it;
  here the only map is a pumpjack in hand outlining wells within render distance, so the density
  does the map's job and puts the nearest field typically inside a hundred and fifty blocks. Wells
  per field, three to eight; spacing, a 4-block grid. All of that is placement behaviour, and none
  of it is identity — the yield percentages and what a pumpjack does with them are.
- **A superflat world has no oil, and no trees or ores either.** The default "Classic Flat" preset
  runs no biome features at all (`features: false`); only the "Overworld" flat preset does. Nothing
  in a biome modifier can change that. `/oil field` puts a field where you stand, placed by the same
  code worldgen uses, and `/oil well` puts one well under your feet. Gamemaster only, like
  `/research`.
- **Distance is measured from the world origin, not the world spawn.** Factorio measures from the
  starting position, which is its origin. Minecraft's spawn is near 0,0 and can move; a rule that
  read the spawn would make new chunks richer or poorer after `/setworldspawn`.
- **There is no map, so there is an x-ray.** Factorio's map view is how oil is found. Holding a
  pumpjack outlines every well within render distance through the terrain; further than that,
  nothing. Whether that finds a field at this density is a judgement only a player makes.
- **No mining productivity, no pollution, and no module slots on the pumpjack.** Factorio's has
  two and 10/min of pollution, and mining productivity research raises its output. Pollution and
  the research are mechanics this pack does not have yet; the slots wait on a screen to put a
  module in, which the pumpjack does not have. The machine is the plain 90 kW one.
- **No brownout, again.** A pumpjack short of 12 FE stops; Factorio's runs slower.
- **The tank is one number and the pipe run is one tank**, as for steam. Factorio 2.0's flow
  model — segments, throughput falling with length — is not modelled, and a pipeline here carries
  whatever is put in at once.
- **Where each fluid enters and leaves a machine is a table on the machine, not a field on the
  recipe.** Factorio fixes it per recipe with `fluidbox_index`, which `data/fluid_recipes.json`
  does not carry. The refinery keeps water and crude at its first and second inputs and heavy,
  light and petroleum at its three outputs; the chemical plant keeps water at its first input;
  everything else takes the next free port in recipe order. That reproduces Factorio for every
  recipe the pack ships - basic oil processing's crude and gas are where advanced processing's
  are, so the upgrade adds pipes rather than moving them - and a recipe from another pack with a
  fluid no table names gets the next free port, which is Factorio's default too.
- **Changing a machine's recipe throws away what the new recipe has no port for.** Factorio does
  the same, behind a confirmation; there is no confirmation here. A refinery switched from
  advanced to basic processing loses its heavy and light oil.
- **A machine's ports have no throughput.** A port takes whatever its run offers up to its tank,
  and a craft's worth leaves an output in one tick; Factorio's fluid boxes fill and drain through
  its flow model. The same gap as the pipes' - see above - seen from the machine.
- **No pollution and no brownout on the refinery and the chemical plant**, as for the
  pumpjack and the assemblers. Both take three modules, which is Factorio's number.
- **The storage tank has no window.** Factorio's shows a bar; the hover readout says the same
  line. And nothing empties a tank on purpose: there is no pump, by decision - see `PLAN.md` - so
  what leaves a tank is what its run's sinks draw, and a tank of the wrong fluid on a run is
  emptied by breaking it.
- **No trains, no vehicles, and no pump**, by decision rather than by schedule: `PLAN.md` has it.
- **A tank on two runs is levelled by each in turn**, one tick apart, rather than as one segment.
  It converges within a few ticks and then sleeps, which is close enough to be indistinguishable at
  the readout, and not Factorio's single fluid segment.
- **No coal liquefaction.** Its technology needs production science, which the tree does not
  reach, and a recipe no technology names is free from the first tick, so the recipe is left out
  of `data/fluid_recipes.json` rather than shipped unlocked.
- **An assembler's fluid boxes are two fixed faces.** Factorio's assembling machine 2 has its
  input box on one side and its output on the opposite one and is rotated to move them; here
  the machine faces the way the player stood when placing it, input towards them, and is not
  turned afterwards - break it and place it again. The first machine has no fluid box and no
  facing, as in Factorio.
- **No processing unit yet.** It is the other recipe with a fluid in it - two advanced circuits,
  twenty circuits and five sulfuric acid - and its technology is not in the tree; the item is
  not registered until it is.
- **Explosives do nothing.** The item exists because the chemical plant makes it and the mapping
  owns it; cliff explosives and artillery are milestones away.
- **A mine trigger is only heard with Facrafting installed.** The pumpjack reports through
  Facrafting's `MiningListeners`, the one seam two subsystem mods may share, and `nauvis_fluids`
  now requires Facrafting outright, since its machines run Facrafting's recipes. A standalone run
  of `nauvis_research` without it can complete craft triggers from vanilla's own events and mine
  triggers from nothing. The pack always ships Facrafting, so this is a fact about the seam rather
  than a hole a player meets.

Water
-----

- **Every lake and sea is `nauvis_fluids:water`, and a bucket turns it into `minecraft:water`.**
  That is the design, not a hole: Factorio's water is where the map put it, and Minecraft's two
  buckets make a spring anywhere. Natural water is scooped as a water bucket, poured back as
  vanilla water, and never makes a new source - and an offshore pump draws from natural water and
  nothing else. What follows is what that costs.
- **Chunks generated before this stay vanilla.** The swap runs as the last step of a chunk's
  generation; a world made earlier keeps the water it has, and a pump finds nothing in it.
- **Natural water waterlogs nothing.** Waterlogging is hard-coded to vanilla's fluid, block by
  block: a slab, a stair or a fence placed in a lake goes in dry, and natural water will not flow
  into one. The water inside kelp, seagrass, coral and a shipwreck's stairs is vanilla's for the
  same reason, and breaking one leaves a block of vanilla water standing in the sea. It is scooped
  as before and pumped by nothing, so the rule holds; it just looks like a block of water.
- **Fish, squid, dolphins, glow squid and the nautilus spawn by a rule of ours.** Vanilla's asks
  for the water block by name; `NaturalWaterSpawns` adds the same rule reading ours, alongside
  vanilla's rather than in place of it. Anything else that names the block - kelp and seagrass
  growing, bone meal on water, frost walker, a fishing bobber's splash particles, a frozen lake
  refreezing, ice melting back to vanilla water, a turtle's way to the sea - does not apply in
  natural water, and is left alone until somebody misses it.
- **A neighbouring chunk can put a little vanilla water back.** A feature that reaches over a
  chunk border - a lush cave's clay pool, an iceberg - may write vanilla water into a chunk that
  was already made natural. Rare, at the edges, and harmless: it is not pumpable and it cannot
  spread into natural water.
- **An offshore pump's intake reaches five blocks: one and two under it, and its three open
  sides.** Two down so that a bank a block above the water is a place to stand; not the block the
  intake itself replaced, so a pump set down in a one-deep puddle of natural water finds the
  puddle gone. Still water only: the flowing skirt where a lake spills into a dug channel is not
  a lake, and a channel does not bring the sea inland. A pump clicked onto the lake floats on it,
  which is Factorio 2.0's rule and not 1.1's.
- **Its numbers are the pack's ratio, not Factorio's units.** 1200 a second and a fluid box of
  200 in Factorio; forty a tick and a tank of 200 here, so that one pump is twenty boilers as it
  is there. The footprint, the recipe, the technology and needing no power are identity.
- **What the pump gives is `minecraft:water`**, which is what `data/mapping.json` maps Factorio's
  water to, what any mod's tank understands, and what the boiler boils. Natural water is the
  thing in the world; water is the thing in the pipe.
- **`#nauvis_fluids:offshore_pumpable` is the switch.** A pack that wants Minecraft's infinite
  water back adds `minecraft:water` to it and changes no code.

Belts
-----

- **A belt does not load a chest.** Factorio-faithful: a belt running into a container backs up, and
  taking things off is what inserters are for. `belt_does_not_load_a_chest` pins it.
- **Crouching stops a belt carrying you**, which Factorio does not do. It is in for the Minecraft
  reflex — without it, placing a machine beside a working belt means being carried off mid-click.
- **A dropped item entity is carried erratically, and sometimes stalls outright.** Measured over
  six runs of the same setup it travelled between 0.5 and 6.4 blocks in the same time, and one run
  in six it stopped on a flat belt and stayed there. `stepOn` is only reached while `onGround` is
  true, and an item bounces every time it lands — `ItemEntity.tick` inverts and halves its downward
  speed — so it spends much of its life just off the belt. `BeltBlock.hold` cancels that bounce,
  which helps and does not cure it. A player is carried properly; a thrown item is not, and
  **anything asserting how far one travelled will be flaky** — see `belt_slope_risers_are_climbable`
  for the shape this has to be tested in instead.
- **And so nothing tests that a belt carries anything at all.** `belt_carries_what_stands_on_it`
  asserted a distance, which for a resting item is not a fact about the belt — it failed one run in
  five, and was deleted rather than weakened. **A belt that stopped carrying the player would pass
  every test in this repo.** Standing on one is the only instrument there is.
- **A belt does not turn you as it carries you.** An entity on a corner is pushed the way that block
  faces, so a bend is two straight shoves rather than an arc. Items do curve.
- **A belt in hand re-points the belt it replaces**, which is Factorio's fast-replace and is worth
  knowing before you walk a line with one: upgrading a corner points it wherever you were looking,
  so a bus is upgraded by walking *along* it. Turning and replacing are one gesture on purpose.
- **Fast-replace does not extend to a splitter.** Clicking a splitter with a splitter in hand does
  nothing, whichever tier is in the hand. A belt is one block and one block state, so swapping it is
  a swap; a splitter is two blocks, a multiblock anchor and a deck with items on both tracks of it,
  and re-anchoring that in place is a different problem from re-pointing a belt. Break it and place
  the other one — the deck drops what it was carrying, as breaking any belt does.
- **A replacement is one belt per click.** Factorio has an upgrade planner that does a whole line at
  once; here you walk the line with a belt in hand, which is the same gesture laying one takes.
- **Items cross a ramp faster than they cross flat ground**, by the diagonal of a square: about 41%.
  A run measures itself in 64ths of a *block* and every block is worth 64 of them, which is what
  makes `frontEdge` and `blockAt` arithmetic rather than a scan and what lets a lane's positions add
  exactly for ever. A ramp is 16 root 2 pixels long and would be 90.5 units - not a whole number,
  and the exactness is the property the whole lane model is built on. Factorio has no slopes, so
  non-negotiable #1 has nothing to say about which number is right here; this is the one that costs
  nothing. The tread's texture is stretched over the ramp by the same 41%, so what you see and what
  the items do agree.
- **A slope that also turns is carried but not drawn.** A belt hands to the first belt straight
  ahead of it, level or one step either way - so a line can change level and change direction in the
  same block, and there is no shape for that. Items still cross at the right height, because the
  height of a seam comes from the two blocks sharing it rather than from either one's shape; they
  simply glide over a belt with no ramp drawn under them. Build the turn and the climb as two
  blocks and it looks right.
- **A ramp's collision is sixteen steps and its model is a smooth 45 degrees.** They are meant to
  disagree here, and the step count is set by physics rather than by looks: a riser has to be
  smaller than one tick of the slowest belt's lift, because an item, a minecart or an orb has a step
  height of *zero* and is carried up a slope only by `BeltBlock.stepOn` lifting it. One pixel is
  under a transport belt's 1.5 a tick with room to spare, and it hugs the drawn ramp within a pixel.
- **A ramp reaches out of its own block at both ends**, and is meant to. Up into the block above,
  which is where the surface has to be to meet the belt at the top of the climb - vanilla's raised
  rail does the same - and, because a rotated box's ends tilt with it, a third of a block *along*
  the belt into the block that belt is in. There the ramp's material lies inside the belt or along
  the top of it. It does mean a solid block placed directly above a ramp will clip it.
- **The slanted slab is a tenth of a pixel narrower than its block**, which is the whole of the
  z-fighting fix: it overlaps the square box under its low end and the flat belt at the top of the
  climb, neither of which can be avoided, and while all three spanned the full width they sat their
  side faces on the same planes. Pulling in the slanted piece - rather than the square ones - leaves
  every flat joint exactly as wide as the belt it meets.
- **Client and server runs can differ at a chunk edge**, because a client only has the belts in its
  loaded chunks. It costs a belt at the very edge of the loaded world appearing to back up when it
  is not; a chunk arriving re-seeds that block's items from the block entity.

Mining
------

- **The ore patch is Minecraft's veins, not a painted number.** Factorio's drill sits on a patch
  it can see; ours reaches down through the ground for whatever ore blocks are in its columns, down
  to `mineFloor`, and a player places it blind. The hover outline shows the volume and the status
  line says "No minable resources" after the first walk, which is the whole of the feedback; a
  count of what is under a drill before it is placed would be the Factorio thing and is not built.
  How much a vein yields is Crumbling Ore's `crumbleHarvests`, eight a block, and that is the
  pack's resource number rather than a block entity of the pack's own.
- **A drill wants a pickaxe, and Factorio's does not. That one is kept.** It is the pack's
  clearest deliberate divergence: a machine you hand a tool to reads as a Minecraft machine, and
  the tool's tier, its enchantments and its durability all do something - the tier decides which
  ores it can take, Fortune multiplies the yield, Efficiency shortens the cycle a tenth a level,
  Silk Touch is stripped, and every ore costs a point. Going 1:1 with Factorio is what keeps the
  *balance* legible, which is why ids, ingredients, craft times and footprints are held to it
  without exception - a mechanic that is simply more fun here is a different question, and this
  one was answered. **Do not read it as debt.**
- **A drill with nowhere to put its ore polls.** The output goes to the block in front of the
  head; a belt clearing is not a block change and wakes nothing, so a drill that stopped on a
  full output looks again once a second rather than sleeping. A chest placed in front wakes it at
  once. And it never drops ore on the ground the way Factorio's does when the tile is bare - the
  ore waits in the drill's one output slot instead.
- **A depleted drill stays depleted until its pickaxe changes.** Walking three thousand blocks
  again on every neighbour change was the alternative. Ore does not grow back, so the only thing
  that makes new ore minable is a better pickaxe, and that is the one change that restarts the
  walk. A drill loaded from disk walks once regardless.
- **Only the first mining productivity.** `mining-productivity-1` is in the tree at Factorio's
  cost and the drills read it; the rest of the ladder is infinite research on higher science.

Modules
-------

- **Tier one only.** Speed, efficiency and productivity modules 2 and 3 cost production and
  utility science, which the pack does not make. The mapping has their ids; nothing registers
  them.
- **No beacon.** `effect-transmission` costs production science. A module works in the machine
  it sits in and nowhere else.
- **A refused recipe says nothing.** Choosing a recipe that may not have productivity modules
  while one sits in the machine is refused, as Factorio refuses it - but Factorio says so and
  this pack's panel does not, so the click looks like nothing happened. Take the module out
  first, or read the tooltip on the slot.
- **The drills still take Neo Progressive Automation's modules.** `nauvis_mining:speed_module`,
  `efficiency_module` and `range_module` are the fork's, not Factorio's, and its slots refuse
  ours. Job 4 in `NEXT.md` replaces them.
- **A module's effect is read as a craft starts and held for the craft.** Pull a speed module
  out mid-craft and that craft finishes at the speed it began at, which is Factorio's rule.

Smelting
--------

- **A furnace runs vanilla's furnace recipes too, at vanilla's times.** Factorio's four are asked
  first and win where they exist; sand, food, cobblestone and the rest fall through to vanilla's
  `minecraft:smelting` list at two hundred ticks over the tier's speed. No experience is given for
  them - Factorio has none - and blasting and smoking recipes are not run, because those are other
  machines'. A vanilla furnace found in a village is a decoration; its recipe went in milestone 3.
- **A silk-touched ore block smelts into nothing.** Factorio's iron ore is the raw item and the pack
  maps it there; the ore-block recipes were conflicts and are gone.
- **Fuel is vanilla's burn time, spent only while working.** A coal is 1600 ticks in a furnace as
  in a boiler, and a stone furnace takes 64 of them per plate, so a coal is twenty-five
  plates where Factorio's is thirteen. The ratio between the tiers is Factorio's - a steel furnace
  smelts twice as much with the same coal - and the absolute number is not identity.
- **Steel needs five plates in the slot at once.** A furnace holding three says so and waits,
  which is what Factorio's does.
- **A locked recipe is refused at the slot.** Iron plates will not go into a furnace before steel
  processing is researched, because nothing in it can smelt them yet; the inserter holding them
  waits. Plates already in when a gamemaster forgets the technology stay there with the furnace
  saying it cannot smelt them.
- **The fire is a lava texture.** A furnace's stack, or the electric furnace's hood, draws its top
  in `lava_still` while lit, because that is the one opaque, animated, fire-coloured sprite vanilla
  ships. Real art is the same job it is for every other machine.

Crafting and the panel
----------------------

- **Personal crafts pay at the end, not the start.** `CraftTicker` checks affordability every tick
  and consumes on completion, so moving ingredients away mid-craft stalls the job rather than losing
  it. It looks like a queue that stopped for no reason, and has been mistaken for a bug.
- **Factorio's recipe picker is a modal** anchored to the machine; ours is a persistent column
  beside the screen. The modal is the more faithful one. A Facrafting change, and it wants an eye.
- **Within a tab, recipes are in alphabetical order, and Factorio's are not.** Factorio sorts by
  subgroup and then a per-item order string; `reference/factorio/recipes.json` has neither, so
  `stamp_order` falls back to the display name. The fix is a dump with Factorio's item order in it,
  after which `stamp_order` is the only thing that changes.
- **Facrafting's grouping button is hidden in this pack, not removed.**
  `Config.SHOW_GROUPING_BUTTON` defaults on, because a pack that has not laid its recipes out
  genuinely wants the choice. Turning it off is `nauvis/pack/config/facrafting-client.toml`.

Research
--------

- **The tree is the early game plus the branches the pack can reach, not Factorio's whole one.**
  `data/technologies.json` is the list; adding one is a JSON entry, and the build reports missing
  prerequisites.
- **A technology that unlocks nothing is still offered**, and now says so. 128 of the 216 unlock no
  recipe, because their only effects are mechanics this pack lacks; four of those are in the tree
  today. They were kept because a technology's cost and place in the graph are identity and go into
  world saves; a tree that grew later would move under a player who had researched past it. The
  mitigation is presentation and is built: a node whose unlock list comes out empty says **no effect
  yet** in its tooltip where the others list what they hand over.
- **Most of a technology's modifiers still do nothing, and the screen says which.** Every
  modifier is in the tree now - a type and a number, summed over what is researched and
  answered through `nauvis_lib`'s `Bonuses` - and three types are read: `laboratory-speed` by
  the lab, `inserter-stack-size-bonus` by every inserter, `bulk-inserter-capacity-bonus` by the
  stack inserter. Damage bonuses and mining speed are summed and asked for by nothing, and their
  lines on the technology screen say *does nothing yet*. Wire a mechanic to one and the lang
  line is what changes.
- **Nothing gates a vanilla bench recipe.** The `crafting_table` datapacks ship a shapeless copy of
  every recipe, off by default, and a vanilla crafting recipe never goes near Facrafting, where the
  gate lives. There is no hook that would let it, so the labels read **"(skips research)"** — not a
  caveat but the point, and the one line a player reads before turning one on.
- **The opening's recipes can never be gated, and that is arithmetic rather than policy.**
  A lab costs circuits, gears and belts; red science costs a plate and a gear; running a lab needs a
  boiler, an engine and a pole; a boiler needs a furnace and pipes. Gate any of those and a new
  world can never reach its own first research. The tree solves it the other way: the first
  technologies are *triggered*. The generator fails on a tree it cannot bootstrap, and
  `research_gates_the_early_machines` asserts the free set again at run time, because the
  generator's answer and the server's could drift and only one is what a player meets.
  **Note what is not in that set: a mining drill.** In Minecraft you mine ore with a pickaxe, so a
  drill is a convenience — which is why the electric drill is the first technology a lab is paid
  for.
- **A lab that has never had a technology picked rechecks once a second**, because choosing research
  happens on a screen and reaches no block. Bounded: only a lab with packs *and* power *and* no
  research pays it.
- **Research progress is sent whole to every client on every unit** — a list of finished keys, one
  optional key and an int, at best once every five seconds of one lab's work. It buys the property
  that a client is either exactly up to date or exactly one message behind.
- **Research is un-researched only by a gamemaster.** `/research` grants, forgets, starts, stops and
  resets, and both grant and forget cascade — a grant brings the prerequisites, a forget takes the
  dependants. There is nothing a player can do about a finished technology, which is Factorio's
  rule; the command exists so the tree can be reached by hand during a playtest.

Vanilla, and what is left alone
-------------------------------

- **Vanilla's own progression is barely touched.** Twenty recipes are removed, and fourteen of
  them are one thing - vanilla smelting iron and copper, which the pack's furnaces do at Factorio's
  price. The rule is that nothing is taken away before the pack can do that job. The conflict half
  of `data/removals.json` is a check rather than a list and will force the rest as items land; the
  bypass half is a judgement and grows one line at a time.
- **The circuit network's vanilla equivalent is deliberately left alone.** Repeaters, comparators,
  observers and pistons are Minecraft's answer to combinators; removing them now would take away a
  system and offer nothing until milestone 6. The dropper stays too, and a test asserts it, because
  it needs a clock to move anything — that is a build rather than a free ride.
- **One tool, and the ladder kept.** Four planks is a stone pickaxe, the wooden one has no recipe,
  and `mineable/pickaxe` absorbs the axe, shovel and hoe tags. Material tiers are untouched, and the
  other tools still exist — they are simply never necessary.

Smaller
-------

Nothing tests that inventories survive a save and reload, or that a network rebuilds after a chunk
cycle — both paths exist and are only reasoned about. The assembler's input slots are unfiltered.
An inserter at a chunk border whose source chunk cycles while it stays loaded can sleep through
items appearing.
