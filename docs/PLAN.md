The plan
========

Recreate Factorio as a Minecraft modpack: mine, smelt, automate, research, defend, launch a rocket.
Minecraft 26.2 / NeoForge 26.2.0.59 / Java 25. First-party by default; the pack must stay small and
load fast, and the tech ecosystem has not reached 26.2.

Settled decisions, not to be re-litigated
-----------------------------------------

- **Scope is the whole game, rocket included**, minus the trains and the circuit network.
- **No trains, no vehicles, no pump** (2026-09-06). Rail, signals, stop, locomotive, wagons, car
  and the pump are `skip` in `data/mapping.json`. `nauvis_trains` is never built.
- **No circuit network; vanilla redstone stays with its vanilla recipes** (2026-09-07). The
  combinators, wires, lamp and speaker are `skip`; `nauvis_circuits` is never built; no redstone
  recipe is ever removed. Wiring a machine to redstone, if wanted, is a feature on the machine.
- **The rocket before the robots** (2026-09-07). Milestone 6 (roboport, robots, logistic chests)
  is a later update; `nauvis_logistics` owns them and the ids are in the mapping.
- **No underground belt, no pipe-to-ground.** The pack has a Y axis; a belt crosses another by
  changing level. All four are `skip`.
- **Power is Minecraft FE, buffered per machine.** Poles are FE cables with a wide connection
  radius. A machine that cannot refill stops (Factorio's runs slower; see `GAPS.md`).
- **Vanilla progression is replaced** from the `nauvis` mod or the pack datapack, never a
  subsystem mod. Twenty recipes are removed; see `MAPPING.md`.
- **Shortcuts are wanted.** Something playable that is close enough beats waiting.
- **First-party by default; adopt a current 26.2 mod rather than rewrite it** when one does the
  job, overriding its recipes through the generator. Only Jade qualifies today (`runtimeOnly` in
  the pack, `compileOnly` in each mod). Applied Energistics 2 and KubeJS are on 26.1.2; Create,
  Mekanism, Immersive Engineering and the rest stopped at 1.21.1 and are read, never shipped.
  Redo the survey (last 2026-08-25) when a milestone needs it, not before.
- **Factorio is the 2.0 base game, from its own data.raw.** `factorio --dump-data` writes it;
  `tools/factorio_data.py` names the version it reads. Space Age is not in the dump and not in
  the pack. A number from another version is a stale file, not a choice.
- **A footprint is identity; height is ours.** `size` in `data/mapping.json`, checked against
  each `*Shape` by `check_models.py`.
- **Ranges and tunables are balance, not identity.** The guns reach twice Factorio's range, a
  drill wants a pickaxe, a solar panel loses output in rain: kept on purpose, listed in `GAPS.md`.

The mods
--------

| Mod id | Owns |
|---|---|
| `facrafting` | the timed crafting model and the crafting UI (sibling repo) |
| `crumblingore` | ore depletion (sibling repo, released) |
| `nauvis_lib` | the framework: multi-blocks, transfer views, the machine screen, bench packs, stack-size mixins, `GameTests` |
| `nauvis` | pack policy, vanilla replacement, raw resources, ore patches, `StandInStacks` |
| `nauvis_materials` | intermediate products |
| `nauvis_logistics` | belts, inserters, splitters, chests, robots |
| `nauvis_machines` | assemblers, furnaces, modules, beacon, radar, repair pack |
| `nauvis_power` | boiler, steam engine, solar, accumulator, poles |
| `nauvis_research` | labs, science packs, the technology tree |
| `nauvis_mining` | the two mining drills |
| `nauvis_fluids` | pipes, natural water, oil, chemistry, nuclear |
| `nauvis_military` | weapons, armour, turrets, walls, pollution, biters |
| `nauvis_rocket` | silo, rocket parts, satellite, space science |

Milestones
----------

All built except 6: **0** foundation (workspace, generators, gametest harness, datagen);
**1** first factory (chest, inserter, assembler, boiler, engine, pole, pipe, iron chest);
**2** belts (transport belt, fast belt, splitter, long-handed inserter, fast-replace);
**3** research (lab, red and green science, assembling machine 2, the three furnaces, solar,
accumulator, medium pole, steel, battery, sulfur, the whole 216-technology tree, vanilla
removals); **4** oil (well, pumpjack, offshore pump, natural water, refinery, chemical plant,
barrels, storage tank, plastic, advanced circuit, engine unit, blue science, electric drill);
**5** military (pistol, SMG, magazines, turret, wall, armour, grenade, military science,
pollution and attacks, machine health); **6** robots, a later update; **7** rocket (silo, rocket
part, satellite, space science, low density structure, rocket fuel, control unit, production and
high tech science, radar, module tiers 2 and 3).

Not built and not scheduled: nuclear, artillery, modular armour, the beacon, the shotgun, the
infinite research that space science pays for. All are breadth: cheap once their tier 1 exists.

What testing looks like
-----------------------

`./gradlew :nauvis:runGameTestServer` runs every gametest headless and is how behaviour is
verified. Write the test with the block, not after it. What a test cannot see is the client:
textures, screens, whether it is fun. `tools/render_model.py` catches a hole or a wrong texture
before a boot; the boot is still owed.
