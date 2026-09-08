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
- A superflat world with the default preset runs no features: no oil, trees or ores. `/oil field`
  and `/oil well` place them; gamemaster only, like `/research`.
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

- Ore is in patches thirty to forty blocks across and two or three layers thick, underground at
  a random height, and nowhere else: vanilla's scattered iron, copper and coal are gone. Gold,
  redstone, lapis, diamonds and emeralds still generate as vanilla does.
- No stone patch: stone is every block of ground. No uranium patch: no uranium ore exists yet.
- A patch is found with a drill in hand, which outlines the ore in loaded chunks through the
  ground; there is no map. Starting patches sit between y 16 and 48, random ones between -40
  and 48, so some are a dig and none breach the surface on purpose (a hill or a ravine may).
- Richness by distance is Crumbling Ore's harvests per block, doubling at 3900 blocks and capped
  at sixty-four; the footprint does not grow. A world made before the patches keeps its vanilla
  veins in the chunks it has generated.
- A patch cut by a cave or water has a hole in it, and a patch under an ocean is under the sea bed.
- The drill is placed blind and the status line says "No minable resources" after its first walk.
- A drill wants a pickaxe: tier decides ores, Fortune multiplies, Efficiency shortens the cycle a
  tenth a level, Silk Touch is stripped, every ore costs a point. *Kept.*
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
- Magazines stack to two hundred; rounds live on the gun or turret as a component.
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
- Stacks are Factorio's; Minecraft's ninety-nine is lifted to ten thousand by `nauvis_lib`'s
  mixins and a count of three or four digits is scaled to fit. Not lifted: minecart chests,
  bundles, another mod's container that answers ninety-nine itself. Cobblestone, coal and raw ore
  go down to fifty; one line each in `StandInStacks`.
- Nothing tests that inventories survive a save and reload, or that a network rebuilds after a
  chunk cycle. An inserter at a chunk border whose source chunk cycles can sleep through items.
