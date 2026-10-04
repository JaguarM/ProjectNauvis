What is deliberately missing
============================

Each a decision. If something looks broken, look here before treating it as a bug. Entries marked
*kept* are divergences from Factorio chosen on purpose and are not debt.

Power and fluids
----------------

- Accumulator rates (forty FE in, forty out) are per handler call; the network calls once a tick.
- An accumulator covers the whole shortfall at any charge, as Factorio's does. No reserve, no
  cut-off; the pole readout is the only place its charge shows.
- A network that moved nothing is re-checked every ten ticks rather than woken exactly.
- No brownout: a machine short of power stops. Factorio's runs slower.
- A dark or roofed solar panel looks up every ten seconds; the sun has no event.
- A solar panel loses two thirds of its output in rain. *Kept.*
- No pipeline length limit; the readout says `6 pipes`, not `6/320`.
- A row of boilers levels its water rather than flowing it; pipe a long row at both ends.
- A boiler one water short of full makes one steam for a tick of coal, as Factorio's does.
- A long-handed inserter with power and nothing to do looks once a second; every other machine
  sleeps for free.

Oil and water
-------------

- A well is never exhausted: it falls ten a cycle to a floor of 60000 or a fifth of its start.
- Field placement approximates Factorio's autoplace: richness uses Wube's `additional_richness`
  220000 and distance factor; density is four times Factorio's (one per 300 chunks, none within
  150 blocks of the origin) because there is no map. Three to eight wells a field on a 4-block grid.
- A superflat world with the default preset runs no features, so it has no oil: `/oil field` and
  `/oil well` place it; gamemaster only, like `/research`.
- Distance is measured from the world origin, not spawn.
- There is no map, so a pumpjack in hand x-rays every well in render distance.
- The pumpjack's screen exists for its module slots.
- A pipe run is one tank; no flow model, no throughput on ports.
- Fluid ports are a table on the machine, not `fluidbox_index` on the recipe (see `MAPPING.md`).
- Changing a recipe throws away fluids the new recipe has no port for, without a confirmation.
- The storage tank has no window; nothing empties a tank on purpose (no pump, by decision).
- A tank on two runs is levelled by each in turn.
- No coal liquefaction: its technology needs production science, which the tree does not reach,
  and an unnamed recipe would be free.
- An assembling machine 2's fluid boxes are two fixed faces, set by placement, not rotatable.
- Explosives do nothing.
- A mine trigger is heard only with Facrafting installed, which the pack always ships.
- Every lake and sea is `nauvis_fluids:water`, bucketed as vanilla water, never a new source.
  Chunks generated before this stay vanilla. Natural water waterlogs nothing; kelp, seagrass,
  coral and shipwrecks hold vanilla water. Fish, squid, dolphins, glow squid and the nautilus
  spawn by a rule of ours; kelp growth, bone meal, frost walker, refreezing and turtles do not
  apply in natural water. A feature reaching over a chunk border may write a little vanilla water.
- The offshore pump's intake is one and two below and its three open sides, still water only,
  and it floats on a lake (Factorio 2.0's rule). Forty a tick and a tank of 200, so one pump is
  twenty boilers. It gives `minecraft:water`; `#nauvis_fluids:offshore_pumpable` is the switch.

Belts
-----

- A belt does not load a chest; inserters do. Crouching stops a belt carrying you. *Kept.*
- A dropped item entity is carried erratically and sometimes stalls (`stepOn` needs `onGround`,
  and an item bounces). Nothing tests that a belt carries anything; a player on one is the only
  instrument. Anything asserting how far an item travelled will be flaky.
- A belt does not turn you on a corner; items do curve.
- A belt in hand re-points the belt it replaces, so a bus is upgraded by walking along it.
  Fast-replace does not extend to a splitter. One belt per click.
- Items cross a ramp 41% faster than flat ground, because distances are integer sixty-fourths;
  the tread texture is stretched to match. *Kept.*
- A slope that also turns is carried but not drawn; build the turn and the climb as two blocks.
- A ramp's collision is sixteen one-pixel steps under a smooth model; a ramp reaches into the
  block above and a third of a block along, so a solid block placed directly above clips it.
- Client and server runs can differ at a chunk edge.

Mining
------

- Ore is Nauvis Terrain's: Factorio's iron, copper, coal and stone patches at the surface, one
  block deep, in a Nauvis world. A world of another type has vanilla's ores and no patches.
  Underground, a Nauvis world keeps vanilla's gold, redstone, lapis and diamonds.
- A stone patch is plain stone, which the drill leaves to a pickaxe like every other block of
  ground. No uranium patch: no uranium ore exists yet.
- Richness by distance is Crumbling Ore's harvests per block, one more times eight in each
  2600-block ring around the origin and capped at sixty-four, in place of Factorio's amount on a
  tile.
- The drill is placed blind and the status line says "No minable resources" after its first walk.
- A drill wants a pickaxe: tier decides ores, Fortune multiplies, Efficiency shortens the cycle a
  tenth a level, Silk Touch is stripped, every ore costs a point. *Kept.*
- A drill takes only the ores in `#nauvis_mining:factorio_ores` (iron, copper, coal) until its F
  toggle is pressed, so one set on a patch can be forgotten; pressed, it takes anything in
  `#c:ores`. A pack adds an ore by tagging it.
- A drill with nowhere to put its ore polls once a second; a chest in front wakes it at once. It
  never drops ore on the ground.
- A depleted drill stays depleted until its pickaxe changes.
- Only `mining-productivity-1`; the rest is infinite research.

Military and pollution
----------------------

- The biters are zombies, skeletons and creepers in caps. No nests, expansion or evolution.
- A hostile forgets the factory over a reload.
- Health is a hundred times hardness except where a machine implements `Damageable`; only the
  turret does (four hundred).
- A repair pack is spent whole on a click, mends up to three hundred, kept when nothing needed it.
- Absorption reads the biome at the chunk's middle, once a minute; an unloaded chunk takes five.
- Nothing comes for an empty base: attacks need a player within ninety-six blocks.
- A turret does not turn, shoots from its middle, and looks around twice a second while loaded.
- Bullets are lines: no spread, no travel time, cannot miss, never hit a player.
- The guns reach twice Factorio's range (thirty and thirty-six); the turret keeps eighteen. *Kept.*
- Magazines stack to a hundred; rounds live on the gun or turret as a component.
- Armour is a chestplate on vanilla's models (chainmail's five, netherite's eight). No modular
  armour, no equipment grid, no shotgun.
- Only `physical-projectile-damage-1`; `weapon-shooting-speed` is not transcribed.

Modules and the rocket
----------------------

- No beacon; `effect-transmission` is not in the tree. A refused recipe (productivity module in a
  machine that may not have one) says nothing. A module's effect is held for the craft it started.
- The rocket is drawn whether or not one is built; the launch is a firework and the rocket stays.
  The advancement goes to every player.
- Automatic launch (2.0) and the Launch button (1.1) both exist; an empty rocket sent by hand
  brings nothing back. Space science buys nothing yet.
- A launch's thousand packs are owed to the output slot and paid as it clears; breaking the silo
  spills what is owed.
- A silo with no recipe logs one warning and looks again every ten seconds.
- No cargo landing pad: the silo's output slot is where a launch's science comes back.
- The flying robot frame exists for utility science; the robots that are built around it do not.
- Only the silo applies Factorio's insertion limit to automation; an assembler's view fills an
  ingredient slot to its capacity.
- The radar keeps a seven-by-seven of chunks loaded while powered and charts nothing.

Smelting, crafting and research
-------------------------------

- A furnace runs vanilla's smelting recipes after Factorio's four, at 200 ticks over the tier's
  speed, no experience. Blasting and smoking are not run. A silk-touched ore block smelts into
  nothing. Fuel is vanilla's burn time spent only while working. Steel needs five plates in the
  slot at once. A locked recipe is refused at the slot. The fire is `lava_still`.
- A rocket silo cannot be crafted by hand (forty-two stacks); an assembling machine 2 makes it.
- Personal crafts pay at the end; moving ingredients away mid-craft stalls the job.
- Facrafting's recipe picker is a column, not Factorio's modal. Within a tab, recipes are
  alphabetical; the dump has no order string. The grouping button is hidden by config.
- The tree is the early game plus what the pack reaches; 128 of 216 technologies unlock no
  recipe and say *no effect yet*. Only `laboratory-speed`, `inserter-stack-size-bonus`,
  `bulk-inserter-capacity-bonus`, `mining-drill-productivity-bonus`, `ammo-damage@bullet` and
  `turret-attack@gun-turret` are read; the rest say *does nothing yet*.
- Nothing gates a bench recipe; the `crafting_table` packs are off and labelled "(skips research)".
- The opening's recipes can never be gated (a lab needs circuits, gears, belts, a boiler, an
  engine, a pole), so the first technologies are triggered; the generator refuses a tree that
  cannot bootstrap and `research_gates_the_early_machines` asserts the free set. A mining drill is
  not in it: the electric drill is the first thing a lab is paid for.
- A lab with packs, power and no research picked rechecks once a second.
- Research progress is sent whole to every client on every unit.
- Only `/research` un-researches; grant and forget cascade.

Vanilla, and what is left alone
-------------------------------

- Twenty recipes are removed, fourteen of them vanilla smelting of iron and copper. Redstone is
  vanilla's for good, the dropper included.
- One tool: four planks is a stone pickaxe, the wooden one has no recipe, `mineable/pickaxe`
  absorbs axe, shovel and hoe. Tiers are untouched.
- Rails exist, as vanilla's rail at Factorio's price, because production science asks for thirty;
  trains do not. Coal liquefaction is left out: the refinery has no third input port. The pistol
  is craftable though 2.0 hides its recipe, since a new player here carries nothing. Fluids share
  the intermediates tab in the crafting panel; Factorio gives them a fifth.
- Stacks are Factorio's; Minecraft's ninety-nine is lifted to ten thousand by `nauvis_lib`'s
  mixins and a count of three or four digits is scaled to fit. Not lifted: minecart chests,
  bundles, another mod's container that answers ninety-nine itself. Cobblestone, coal and raw ore
  go down to fifty; one line each in `StandInStacks`.
- Only the silo's slots are tested through a save and reload. Nothing tests that a network
  rebuilds after a chunk cycle; an inserter at a chunk border whose source chunk cycles can sleep
  through items.
