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
- **No pipeline length limit.** Factorio caps a segment at 320 pipes and its tooltip says `6/320`;
  ours says `6 pipes`. Adding the cap is a real gameplay change — refusal to connect, not a number.
- **A long-handed inserter with power and nothing to do costs one look a second**, because the wake
  signal has a radius of one block and its ends are two away. Every other machine sleeps for free.
  Two things would remove it and neither exists: a hook that fires when the block entity at a
  watched position changes, or a reason to believe every source a long arm reaches is one of ours.

Belts
-----

- **A belt does not load a chest.** Factorio-faithful: a belt running into a container backs up, and
  taking things off is what inserters are for. `belt_does_not_load_a_chest` pins it.
- **Crouching stops a belt carrying you**, which Factorio does not do. It is in for the Minecraft
  reflex — without it, placing a machine beside a working belt means being carried off mid-click.
- **A dropped item entity is carried erratically.** `ItemEntity.tick` only calls `move` — and so
  only reaches `stepOn` — when the item is airborne, has horizontal momentum, or `(tickCount +
  getId()) % 4 == 0`, so something *resting* on a belt is pushed on a fraction of ticks at a phase
  that depends on its entity id. A player is carried properly; a thrown item is not.
- **And so nothing tests that a belt carries anything at all.** `belt_carries_what_stands_on_it`
  asserted a distance, which for a resting item is not a fact about the belt — it failed one run in
  five, and was deleted rather than weakened. **A belt that stopped carrying the player would pass
  every test in this repo.** Stand on one during a playtest; it is the only instrument there is.
- **A belt does not turn you as it carries you.** An entity on a corner is pushed the way that block
  faces, so a bend is two straight shoves rather than an arc. Items do curve.
- **A belt upgraded in place keeps its facing, and Factorio's would take the one in your hand.**
  Deliberate: turning and upgrading are the same button here, and a red belt run along a line with
  a corner in it would silently straighten the corner. Downgrading is refused for the same reason.
- **An upgrade is one belt per click.** Factorio has an upgrade planner that does a whole line at
  once; here you walk the line with a belt in hand, which is the same gesture laying one takes.
- **Client and server runs can differ at a chunk edge**, because a client only has the belts in its
  loaded chunks. It costs a belt at the very edge of the loaded world appearing to back up when it
  is not; a chunk arriving re-seeds that block's items from the block entity.

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
- **A technology that unlocks nothing is still offered.** 128 of the 216 unlock no recipe, because
  their only effects are mechanics this pack lacks. They were kept because a technology's cost and
  place in the graph are identity and go into world saves; a tree that grew later would move under a
  player who had researched past it. The mitigation is presentation and is not built: the research
  row could say *no effect yet*, which is true and cheap.
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
- **Research cannot be un-researched in game**, and there is no command for any of it.
  `ResearchState.forget` exists and is used only by the gametests. A `/research` command is an
  afternoon and would make the tree testable by hand.

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
