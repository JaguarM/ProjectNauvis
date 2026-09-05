What is deliberately missing
============================

Known holes, each a decision rather than an oversight. If something looks broken, look here before
treating it as a bug. `PITFALLS.md` is the other list: things that *are* wrong.

Power and fluids
----------------

- **An accumulator cannot discharge.** `PowerNetwork` collects supply from endpoints that did not
  want energy, so a battery would charge and never feed the grid. It wants a third case.
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
- **No accumulator yet**, so solar power is daytime power. Factorio's answer to the night is the
  accumulator, which is behind sulfur and the battery - oil, again - and wants `PowerNetwork`'s
  third case above. Solar plus a boiler for the night is what Factorio itself does before then.
- **No pipeline length limit.** Factorio caps a segment at 320 pipes and its tooltip says `6/320`;
  ours says `6 pipes`. Adding the cap is a real gameplay change — refusal to connect, not a number.
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
  of. Fields per chunk is a rarity of 1200, which is Factorio's 1.8 per square kilometre; wells per
  field, three to eight; spacing, a 4-block grid. All of that is placement behaviour, and none of it
  is identity — the yield percentages and what a pumpjack does with them are.
- **Distance is measured from the world origin, not the world spawn.** Factorio measures from the
  starting position, which is its origin. Minecraft's spawn is near 0,0 and can move; a rule that
  read the spawn would make new chunks richer or poorer after `/setworldspawn`.
- **There is no map, so there is an x-ray.** Factorio's map view is how oil is found. Holding a
  pumpjack outlines every well within render distance through the terrain; further than that,
  nothing. Whether that finds a field at Factorio's density is the open playtest question.
- **No mining productivity, no pollution, no modules.** The pumpjack has two module slots and
  10/min of pollution in Factorio, and mining productivity research raises its output. All three
  are mechanics this pack does not have yet; the machine is the plain 90 kW one.
- **No brownout, again.** A pumpjack short of 12 FE stops; Factorio's runs slower.
- **The tank is one number and the pipe run is one tank**, as for steam. Factorio 2.0's flow
  model — segments, throughput falling with length — is not modelled, and a pipeline here carries
  whatever is put in at once.
- **Crude oil goes nowhere yet.** No refinery, no storage tank, no barrelling. A pumpjack fills the
  pipe run it is attached to and stops, and says *Full*, which is correct and not a fault.
- **`oil-processing` unlocks two machines that do not exist.** It is in the tree as Factorio 2.0's
  trigger — pump crude oil once — and finishes correctly; the refinery and the chemical plant it
  hands over are mapped and not built, so finishing it changes nothing a player can do yet.
- **A mine trigger is only heard with Facrafting installed.** The pumpjack reports through
  Facrafting's `MiningListeners`, the one seam two subsystem mods may share; a standalone run of
  `nauvis_research` without it can complete craft triggers from vanilla's own events and mine
  triggers from nothing. The pack always ships Facrafting, so this is a fact about the seam rather
  than a hole a player meets.

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
  every test in this repo.** Stand on one during a playtest; it is the only instrument there is.
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

- **The drills are quarries, not Factorio drills.** `nauvis_mining` is a fork of Neo Progressive
  Automation and carries its behaviour: a drill digs *downwards*, has three dig modes on a button,
  wants a pickaxe and fill material, and pushes what it finds into any container beside it. What is
  no longer wrong is the area — it covers the ground it stands on, 2×2 or 3×3, rather than a radius
  out of the config with the machine's own size playing no part. Factorio's sits on an ore patch, takes the resource out of the tiles it
  covers, needs nothing but fuel or power, and drops onto one output tile in front. The fork
  bought the ids; the behaviour is `NEXT.md`'s job 1 — all of it except the pickaxe, below.
- **A block that insists on a shovel for its drops is skipped.** The shovel slot is gone — a
  Factorio drill carries no tools, and vanilla's shovel blocks all drop by hand, so the slot only
  ever bought speed on dirt. Dirt, sand and gravel are still dug, by hand at vanilla's wrong-tool
  rate; a modded block that *requires* a shovel is left standing, the way anything else the drill
  cannot take is left standing, and the area still reports itself cleared.
- **A drill wants a pickaxe, and Factorio's does not. That one is kept.** It is the pack's
  clearest deliberate divergence: a machine you hand a tool to reads as a Minecraft machine, and
  the tool's tier, its enchantments and its durability all do something. Going 1:1 with Factorio is
  what keeps the *balance* legible, which is why ids, ingredients, craft times and footprints are
  held to it without exception — a mechanic that is simply more fun here is a different question,
  and this one was answered. **Do not read it as debt**; `NEXT.md`'s job 1 excludes it on purpose.
- **Its three modules are not Factorio's.** `speed`, `efficiency` and `range` were invented for
  NPA. Factorio has speed, efficiency and productivity in three tiers, they belong to
  `nauvis_machines` per `PLAN.md`, and a range module has no counterpart at all — a drill's area
  is a property of the entity. The three items are live ids in the pack's namespace, which is why
  they are the first thing job 1 resolves.

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

- **The tree is 25 technologies, not Factorio's whole one** — the early game plus the branches the
  pack can reach. Adding one is a JSON entry, and the build reports missing prerequisites.
- **A technology that unlocks nothing is still offered**, and now says so. 128 of the 216 unlock no
  recipe, because their only effects are mechanics this pack lacks; four of those are in the tree
  today. They were kept because a technology's cost and place in the graph are identity and go into
  world saves; a tree that grew later would move under a player who had researched past it. The
  mitigation is presentation and is built: a node whose unlock list comes out empty says **no effect
  yet** in its tooltip where the others list what they hand over.
- **A technology's modifiers do nothing** — damage, laboratory speed, mining speed are transcribed
  and dropped. The generator counts them by type so it is visible how much is waiting. Research
  speed is the one that would be felt first: `research-speed-1` and `-2` change nothing.
- **Nothing gates a vanilla bench recipe.** The `crafting_table` datapacks ship a shapeless copy of
  every recipe, off by default, and a vanilla crafting recipe never goes near Facrafting, where the
  gate lives. There is no hook that would let it, so the labels read **"(skips research)"** — not a
  caveat but the point, and the one line a player reads before turning one on.
- **Eleven of the nineteen recipes can never be gated, and that is arithmetic rather than policy.**
  A lab costs circuits, gears and belts; red science costs a plate and a gear; running a lab needs a
  boiler, an engine and a pole; a boiler needs a furnace and pipes. Gate any of those and a new
  world can never reach its own first research. The tree solves it the other way: the first
  technologies are *triggered*. The generator fails on a tree it cannot bootstrap, and
  `research_gates_the_early_machines` asserts the free set again at run time, because the
  generator's answer and the server's could drift and only one is what a player meets.
  **Note what is not in that set: a mining drill.** In Minecraft you mine ore with a pickaxe, so a
  drill is a convenience — which is why the electric drill could become the second research.
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

- **Vanilla's own progression is barely touched.** Four recipes are removed. The rule is that
  nothing is taken away before the pack can do that job, and the pack does twenty jobs. The conflict
  half of `data/removals.json` is a check rather than a list and will force the rest as items land;
  the bypass half is a judgement and grows one line at a time.
- **The circuit network's vanilla equivalent is deliberately left alone.** Repeaters, comparators,
  observers and pistons are Minecraft's answer to combinators; removing them now would take away a
  system and offer nothing until milestone 7. The dropper stays too, and a test asserts it, because
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
