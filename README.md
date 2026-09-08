Project Nauvis
==============

A Minecraft modpack that recreates Factorio.

| | |
|---|---|
| Minecraft | 26.2 |
| NeoForge | 26.2.0.59 |
| Java | 25 |
| Status | milestones 0–2 done, 3 all but the accumulator, 4 begun. See `docs/NEXT.md` |

Mine, smelt, automate, research, defend, launch a rocket — Factorio's loop with Factorio's
own numbers, built as a small set of first-party mods rather than a three-hundred-mod pack.
Every recipe's ingredients and craft times come from Factorio's own recipe data, so nothing
here is balanced by hand.

Where things are
----------------

| | |
|---|---|
| `CLAUDE.md` | the five non-negotiables, and which doc is for what. Read first |
| `docs/NEXT.md` | what the next session should pick up, and how to run everything |
| `data/mapping.json` | what stands in for what. Hand-maintained |
| `data/technologies.json` | the technology tree, from Factorio's own data |
| `data/removals.json` | the vanilla recipes the pack takes away, and the rule for it |
| `tools/` | the generators — recipes, technologies, removals — and the checks the build runs |
| `texture-workshop/` | the scripts that draw the pack's own art |
| `nauvis_lib/` | the framework: multi-blocks, transfer views, the machine screen |
| `nauvis/` | the pack mod: policy, vanilla replacement, raw resources, terrain |
| `nauvis_machines/` | assemblers, furnaces, modules, beacon, radar |
| `nauvis_logistics/` | belts, inserters, splitters, chests, robots |
| `nauvis_power/` | boiler, steam engine, solar, accumulator, poles |
| `nauvis_fluids/` | pipes, oil, chemistry, nuclear |
| `nauvis_research/` | labs, science packs, the technology tree |
| `nauvis_mining/` | the two mining drills |
| `nauvis_materials/` | Factorio's intermediate products: cable, gears, circuits, steel, plastic, batteries |
| `reference/` | Factorio's data and other people's source. Gitignored, not ours |

Two mods live in their own repos alongside this one: [Facrafting][fc] and [Crumbling Ore][co].
Two more used to: [Neo Progressive Automation][npa] is where the mining drills came from, and
`nauvis_mining/` is a fork of it carrying Factorio's ids, while Neo Progressive Materials was
folded in whole as `nauvis_materials/`. The originals are still their own mods.

The shape of it
---------------

Fifteen mods, each a separate jar with its own permanent id, each usable standalone, glued
together by `neoforge:mod_loaded` recipe conditions with the dependency arrows pointing one
way. Twelve of them are new to this pack, seven of those exist so far, and the rest arrive with
their milestones.

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
[npa]: https://github.com/JaguarM/NeoProgressiveAutomation
[co]: https://github.com/JaguarM/CrumblingOre
