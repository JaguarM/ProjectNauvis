Making block textures
=====================

This workshop started in Neo Progressive Automation, whose own
`texture-workshop/README.md` — in that repo, which the pack no longer builds — is still the
fuller document: it covers how a block gets its look, what the file actually is, and the one
lesson from vanilla (six colours for an entire rocky surface; if you are picking a twelfth
shade of grey you have left the style). None of that is repeated here.

`make_miner_textures.py` came across with the drills when `nauvis_mining` was forked out of
that mod, so every block texture in the pack is now made here.

What is here is one script per subsystem that needs art, writing into that mod's
`assets/<modid>/textures/block/`. The rule is the same as over there: **a texture is an
ASCII map plus a palette**, so a change to the map moves every tier at once and they cannot
drift apart.

    python texture-workshop/make_belt_textures.py            # write the PNGs
    python texture-workshop/make_belt_textures.py --preview  # also write belt-preview.png
    python texture-workshop/make_belt_textures.py --all      # the unregistered tiers too
    python texture-workshop/make_chest_textures.py --preview # the two chests
    python texture-workshop/make_material_textures.py        # Neo Progressive Materials' items

`make_material_textures.py` writes into `../NeoProgressiveMaterials`, which is a sibling repo
rather than a subproject. That is deliberate - the workshop is one place, not five - and it
only writes the items it has a map for, so the two that were drawn by hand before it existed -
the copper cable and the iron gear wheel - are left alone. The three circuits are one board map
in three colours, so they cannot drift apart again.

The chests
----------

The one thing in here that is not an ASCII map, and the reason is worth knowing: a chest
texture is not a picture of anything. It is six rectangles per box, unwrapped, and *where
each rectangle lands is decided by `ModelPart.Cube`* rather than by us - so the map is
replaced by the same arithmetic vanilla does, and the drawing is a few panel rules applied to
whichever rectangle a face turns out to be. `make_chest_textures.py` writes that arithmetic
out in full at the top; read it before moving a pixel.

Two things bite. **The front of a chest is south** - the lock cube sits at the +Z face and the
renderer turns the whole model to the block's facing afterwards, so a latch drawn on the north
faces comes out on the back. And **the lock shares the lid's `texOffs`**, fitting in the corner
of the lid's block that the lid's own six faces do not use; that is vanilla's trick and not a
coincidence.

The preview is not a render. It pastes the faces you can actually see on a closed chest into
the arrangement you would see them in, which is enough to catch a face drawn into the wrong
rectangle - the one mistake that file can plausibly make.

The belt
--------

Three maps — top, side, bottom — and one palette per tier. Factorio's three belts are
yellow, red and blue and that is identity, so all three palettes are written down even
though only the yellow one is registered; `--all` draws the others.

Two things about the belt are worth knowing before editing its maps.

**The side is drawn at 16×16 and cropped to its bottom half.** A belt is half a block high,
and vanilla's `block/slab` model takes the side texture's rows 8–15. Rows 0–7 are still
drawn, plainly, so the file is a sensible texture if the model ever changes — but nothing
you put there will be seen.

**The top scrolls, and it scrolls at exactly the speed the belt carries things at.** That
is worth the trouble it takes. A tread that crawls while the items on it race is the sort
of thing you cannot un-see, and it makes a working belt look broken.

Minecraft animates a texture by cutting a tall strip into frames and showing each for a
whole number of ticks, so the speeds it can express directly are whole pixels a tick. A
transport belt moves 1.875 tiles a second, which at sixteen pixels a tile and twenty ticks
a second is **one and a half pixels a tick**, and there is no such frame.

The way round it is the `frames` list in the `.mcmeta`, which may name the same frame more
than once and in any order. Eight frames are written, one per pixel of the tread's
eight-pixel repeat, and the list walks them `0, 1, 3, 4, 6, 7, 1, 2 …` — one pixel, then
two, then one — averaging exactly three pixels every two ticks and returning to where it
started after sixteen ticks, so it loops without a jump. `frame_schedule` works that out
from the speed in `data/mapping.json`, so a fast belt at twice the speed needs no thought.

**Corners are the straight top bent, not a second map.** `bend` warps it through a quarter turn,
pixel for pixel: each destination pixel becomes a distance *along* the belt and a distance *across*
it, and those two are the straight map's row and column. So the rails become arcs and the chevrons
follow them round without being drawn again, edits to one move both, and a corner meets a straight
at a seam because they are the same picture. The along coordinate is stretched to a full sixteen
pixels — a quarter arc of radius eight is only 12.6 long, and a tile that is not a whole number of
tread repeats puts the chevrons out of phase at every seam. The inside of the turn collapses to a
point, because a belt is as wide as the radius it turns through; that is what a tight corner is,
and Factorio's own does the same.

Two rules hold the top map together, and both bite if you ignore them:

- **the rails are constant down each column.** The whole image is rolled to animate it, so
  a rail that changed from row to row would slide along with the tread.
- **the tread repeats every eight rows**, and rows 8–15 are a copy of rows 0–7. That is
  what lets the roll wrap without a seam, and eight divides sixteen so the chevrons also
  run unbroken from one belt block into the next. Tile the texture two-by-two and look at
  it before trusting an edit.

And one that is shared with the drills: **the yellow wraps the corner.** Row 8 of the side
is the same yellow as the top map's outer columns, so the frame reads as one object rather
than as a top that stops at its own edge. Check that in three dimensions after any edit to
those rows — it is the thing a flat preview hides.
