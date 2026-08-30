Project Nauvis
==============

A Minecraft modpack that recreates Factorio.

| | |
|---|---|
| Minecraft | 26.2 |
| NeoForge | 26.2.0.59 |
| Java | 25 |
| Status | milestone 0 done; assembler, inserter, chest and power work. See `docs/NEXT.md` |

Mine, smelt, automate, research, defend, launch a rocket — Factorio's loop with Factorio's
own numbers, built as a small set of first-party mods rather than a three-hundred-mod pack.
Every recipe's ingredients and craft times come from Factorio's own recipe data, so nothing
here is balanced by hand.

Where things are
----------------

| | |
|---|---|
| `CLAUDE.md` | the five non-negotiables. Read first |
| `docs/NEXT.md` | what the next session should pick up, and how to run everything |
| `docs/PLAN.md` | mod map, milestones, shortcuts, what testing means |
| `docs/MAPPING.md` | how the 214 Factorio items resolve to Minecraft ones |
| `docs/API-26.2.md` | confirmed 26.2 renames, checked against decompiled sources |
| `docs/ARCHITECTURE.md` | the rules a machine is built to, and the patterns worth copying |
| `docs/PITFALLS.md` | things that compile, pass tests, and are still wrong |
| `docs/GAPS.md` | what is deliberately missing |
| `data/mapping.json` | the mapping itself. Hand-maintained |
| `tools/gen_mapping.py` | seeded it once; refuses to overwrite |
| `tools/gen_recipes.py` | turns the mapping plus Factorio's dump into recipe JSON |
| `nauvis/` | the pack mod: policy, vanilla replacement, raw resources, terrain |
| `nauvis_machines/` | assemblers, furnaces, modules, beacon, radar |
| `nauvis_logistics/` | belts, inserters, splitters, chests, robots |
| `nauvis_power/` | boiler, steam engine, solar, accumulator, poles |
| `nauvis_fluids/` | barrels then pipes, oil, chemistry, nuclear |
| `reference/` | Factorio's data and Create's source. Gitignored, not ours |

Three mods live in their own repos alongside this one: [Facrafting][fc],
[Neo Progressive Materials][npm] and [Crumbling Ore][co]. A fourth,
[Neo Progressive Automation][npa], is where the mining drills came from; `nauvis_mining/` is a
fork of it carrying Factorio's ids, and the original is still its own released mod.

The shape of it
---------------

Fourteen mods, each a separate jar with its own permanent id, each usable standalone, glued
together by `neoforge:mod_loaded` recipe conditions with the dependency arrows pointing one
way. Ten of them are new.

The first milestone is a chest feeding an inserter feeding an assembling machine feeding an
inserter feeding a chest, burning coal for power. It costs eleven new items. Everything after
that is more of the same, at greater scale, until a rocket leaves the ground.

Licensing
---------

The mods are MIT. `reference/` is not ours and is never committed: Factorio's recipe data
belongs to Wube, and Create is MIT for code but All Rights Reserved for assets. Nothing in
this repo copies either — Create is read for architecture and reimplemented, and only derived
recipe data under our own item ids is committed.

[fc]: https://github.com/JaguarM/Facrafting
[npm]: https://github.com/JaguarM/NeoProgressiveMaterials
[npa]: https://github.com/JaguarM/NeoProgressiveAutomation
[co]: https://github.com/JaguarM/CrumblingOre
