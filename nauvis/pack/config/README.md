The pack's config
=================

`run/` is gitignored, so a setting edited there is seen by nobody. Files here are committed and
`:nauvis:packConfig` copies them over `run/config` before every run; only files that exist here
are touched. Keep each in the form NeoForge writes it: NeoForge rewrites a config that does not
match the spec, comments included, so reasoning goes here.

`crumblingore-common.toml`: `crumbling.richness.ringWidth = 2600`. A Nauvis world's patches are
one block deep, so the amount Factorio puts on a tile is Crumbling Ore's harvests per block here;
the pack turns on its rings at the width over which Factorio's richness grows by one more: eight
harvests within 2600 blocks of the origin, sixteen in the next ring, twenty-four in the one after,
capped at sixty-four from 18,200 blocks.

`facrafting-client.toml`: `panel.showGroupingButton = false`. The button cycles recipe grouping
(by `group`, by creative tab, by mod); this pack's recipes are already grouped into Factorio's
four tabs by the generator, and the other two groupings both come out as one tab per mod.
