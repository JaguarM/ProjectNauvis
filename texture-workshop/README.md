Making textures
===============

One script per subsystem that needs art, writing into that mod's `assets/<modid>/textures/`.
A texture is an ASCII map plus a palette, so a change to the map moves every tier at once.
Six colours is enough for a rocky surface; a twelfth shade of grey means the style is lost.

    python texture-workshop/make_belt_textures.py            # write the PNGs
    python texture-workshop/make_belt_textures.py --preview  # also write a preview sheet
    python texture-workshop/make_belt_textures.py --all      # the unregistered tiers too

Every script takes `--preview`; the previews are not committed.

Every item map is Factorio's icon at sixteen pixels, in vanilla's idiom: the science packs are
one flask in seven colours, the guns lie level with the muzzle to the left. A gun's model,
`nauvis_military/.../models/item/gun.json`, is the display block that turns that to point
forward in the hand, because vanilla's handheld display is built for a sword drawn corner to
corner and would aim a level gun at the player.

The chests are not an ASCII map: a chest texture is six rectangles per box, unwrapped, and where
each lands is decided by `ModelPart.Cube`. `make_chest_textures.py` writes that arithmetic out at
the top. The front of a chest is south, and the lock shares the lid's `texOffs`.

The belt's side is drawn at 16×16 and cropped to rows 8 to 15 by `block/slab`. Its top scrolls at
the belt's own speed: a transport belt moves 1.5 pixels a tick, which no whole-tick frame rate
gives, so `frame_schedule` writes eight frames and a `frames` list in the `.mcmeta` that walks
them one pixel, then two, averaging exactly the speed from `data/mapping.json`. Corners are the
straight top bent by `bend`.
