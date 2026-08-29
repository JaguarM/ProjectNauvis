The pack's config
=================

A modpack is configuration as much as it is a mod list, and `nauvis/run/` is gitignored — so a
setting edited in place there is a change nobody else ever sees, including the next person to
clone this repo. Everything in this directory is committed, and `:nauvis:packConfig` copies it
over the run directory's copies before every run. **Edit these, not the ones under `run/config`.**

Only files that exist here are touched. Everything else NeoForge writes into `run/config` is left
alone, so this is a small deliberate list rather than a mirror of the config folder.

Keep the files in the form NeoForge writes them. It rewrites a config that does not match the
mod's spec — comments included — so prose explaining a choice does not survive a boot. That is
what this file is for.

`facrafting-client.toml`
------------------------

**`panel.showGroupingButton = false`.** Facrafting's panel has a button that cycles how recipes
are grouped into tabs: by the recipe's own `group`, by creative category, or by mod. This pack's
recipes are already grouped the way it wants them read — Factorio's four crafting-menu tabs,
generated into every recipe by `tools/gen_recipes.py` — so the button can only make the panel
worse. The other two groupings both come out as *one tab per mod* here, because every subsystem
mod registers its own creative tab so it can be played standalone. Neither is a view of this game,
and offering them makes the pack's own strip look like one arbitrary choice of three.

Facrafting still defaults it on, which is right for a pack that has not laid its recipes out.
