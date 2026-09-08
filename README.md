Project Nauvis
==============

A Minecraft modpack that recreates Factorio: mine, smelt, automate, research, defend, launch a
rocket, with Factorio's own ids, recipes, craft times and footprints. Minecraft 26.2, NeoForge
26.2.0.59, Java 25. The rocket launches; the robots are a later update.

`CLAUDE.md` is the rules and says which doc is for what. `docs/NEXT.md` is what to pick up and how
to run everything. `data/` holds the mapping, the technology tree and the vanilla removals;
`tools/` the generators and the build's checks; `texture-workshop/` the scripts that draw the art;
`nauvis_lib/` the framework and `nauvis/` the pack mod, with one `nauvis_*` subproject per
subsystem. `reference/` is other people's work and is gitignored.

Two mods live in their own repos beside this one: [Facrafting][fc] and [Crumbling Ore][co].
`nauvis_mining/` is a fork of [Neo Progressive Automation][npa] under Factorio's ids.

The mods are MIT. Factorio's recipe data is Wube's and never committed; only recipes derived from
it under our own ids are.

[fc]: https://github.com/JaguarM/Facrafting
[npa]: https://github.com/JaguarM/NeoProgressiveAutomation
[co]: https://github.com/JaguarM/CrumblingOre
