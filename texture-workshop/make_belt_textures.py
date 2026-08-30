"""Generate the transport belt's textures from ASCII maps plus a palette per tier.

One silhouette, one palette per tier -- the same family rule Neo Progressive Automation's
`make_miner_textures.py` uses, and deliberately the same file shape, so that whoever has
read one has read both.

    python texture-workshop/make_belt_textures.py            # write the PNGs
    python texture-workshop/make_belt_textures.py --preview  # also write preview.png

Legend for the maps:
    .  darkest recess / outline      a  frame accent, shadowed
    d  dark material                 b  frame accent, lit
    m  mid material                  l  light material
    h  highlight

Corners are the straight tile bent
----------------------------------

There is no map for a corner. `bend` warps the straight top through a quarter turn, pixel for
pixel, so the rails become arcs and the chevrons follow them round without being drawn a second
time. Edit the chevrons and the corner turns with them; they meet at a seam because they are the
same picture.

The moving part
---------------

The top scrolls, and it scrolls at exactly the speed the belt carries things at. That is
worth the trouble it takes: a tread that crawls while the items on it race is the sort of
thing you cannot un-see, and it makes the belt look broken when it is working perfectly.

Minecraft animates a texture by cutting a tall strip into frames and showing them in turn,
a whole number of ticks each -- so the only speeds it can express directly are a whole
number of pixels a tick. A transport belt moves 1.875 tiles a second, which at sixteen
pixels a tile and twenty ticks a second is **one and a half pixels a tick**, and there is
no such frame.

The way round it is the `frames` list in the `.mcmeta`, which may name the same frame more
than once and in any order. Eight frames are written, one per pixel of the tread's
eight-pixel pattern, and the list walks them 0, 1, 3, 4, 6, 7... -- one pixel, then two,
then one -- which averages exactly three pixels every two ticks. It comes back to where it
started after sixteen ticks, so it loops without a jump. `frame_schedule` below works that
out from the speed rather than from a table, so a fast belt at twice the speed needs no
thought at all.
"""

import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(
    HERE, os.pardir, "nauvis_logistics", "src", "main", "resources", "assets",
    "nauvis_logistics", "textures", "block",
)

TICKS_PER_SECOND = 20
PIXELS_PER_BLOCK = 16

# --- maps ------------------------------------------------------------------
# The top: the tread you look down on, and the frame rails either side of it.
#
# Two rules hold this map together and both matter if you edit it:
#
#   - **the rails are constant down each column.** The whole image is rolled to animate it,
#     and a rail that changed from row to row would visibly slide along with the tread.
#   - **the tread repeats every eight rows.** Rows 8-15 are a copy of rows 0-7. That is
#     what lets the roll wrap without a seam, and eight divides sixteen so the pattern also
#     runs unbroken from one belt block into the next.
#
# The chevron points at the top of the image, which is north, which is the way a belt with
# `facing=north` carries things. Every other facing is the same model turned.
TOP = """
ab.ddddlldddd.ba
ab.dddlddlddd.ba
ab.ddlddddldd.ba
ab.dlddddddld.ba
ab.lddddddddl.ba
ab.dmdddddmdd.ba
ab.dddmdddddd.ba
ab.dmdddmdddd.ba
ab.ddddlldddd.ba
ab.dddlddlddd.ba
ab.ddlddddldd.ba
ab.dlddddddld.ba
ab.lddddddddl.ba
ab.dmdddddmdd.ba
ab.dddmdddddd.ba
ab.dmdddmdddd.ba
"""

# The side, and only its bottom half is ever seen: a belt is half a block high, and
# vanilla's `block/slab` model crops the side texture to rows 8-15. The top half is drawn
# anyway, plainly, so the file is still a sensible 16x16 if the model ever changes.
#
# Row 8 is the belt's top edge and is the same yellow as the top map's rails, so the colour
# wraps the corner instead of stopping at it. That is the drills' trim-band rule, and it is
# the thing to re-check in three dimensions after any edit to these rows.
SIDE = """
dmdd.mdddmd.mdmd
mddmdddm.dmddmdm
dmd.mdmmdmdddmmd
ddmdmdd.ddmdmddd
dmddmd.dmddmddmd
mdd.mdmmdmd.dmdd
dmdmdd.d.ddmdmmd
ddmddmdmmddmddmd
abbbbbbbbbbbbbba
a..............a
a.lm.d.lm.d.lm.a
admmdddmmdddmmda
a.dm.d.dm.d.dm.a
a..............a
abbbbbbbbbbbbbba
aaaaaaaaaaaaaaaa
"""

# The underside. Nobody looks at it often, so it is a plate with rivets and nothing else.
BOTTOM = """
dmddmdddmddmddmd
mddmd.dmd.dmddmd
dmd.mdmmdmd.dmmd
ddmdmdd.ddmdmddd
dmddmdddmddmddmd
mdd.mdmmdmd.dmdd
dmdmdd.d.ddmdmmd
ddmddmdmmddmddmd
dmddmdddmddmddmd
mddmd.dmd.dmddmd
dmd.mdmmdmd.dmmd
ddmdmdd.ddmdmddd
dmddmdddmddmddmd
mdd.mdmmdmd.dmdd
dmdmdd.d.ddmdmmd
ddmddmdmmddmddmd
"""

# --- tiers -----------------------------------------------------------------
# Factorio's three belts are yellow, red and blue, and that is identity - it is how a
# player reads a bus at a glance. Only the transport belt is registered so far; the other
# two are written down here because they cost nothing to keep beside it and because the
# whole point of a map-plus-palette workshop is that a tier is a palette.
#
# `speed` is the same tiles per second `data/mapping.json` records, and is what decides how
# fast the tread scrolls. It is not a free number: `tools/check_models.py` holds the belt
# block's own constant to the mapping, and this reads the mapping too.
TIERS = {
    "transport_belt": dict(
        K="#1a1a1a", D="#2f3033", M="#3d3e42", L="#8e9095", H="#b9bcc0",
        A="#9a7412", B="#e3b229",
    ),
    "fast_transport_belt": dict(
        K="#1a1a1a", D="#2f3033", M="#3d3e42", L="#8e9095", H="#b9bcc0",
        A="#8d2a20", B="#d9483a",
    ),
    "express_transport_belt": dict(
        K="#1a1a1a", D="#2f3033", M="#3d3e42", L="#8e9095", H="#b9bcc0",
        A="#1f5f8d", B="#3ea3d9",
    ),
}

# The tiers the mod actually registers, and so the ones written by default. The express belt
# is drawn on request; see --all.
REGISTERED = ["transport_belt", "fast_transport_belt"]

# The tread's own repeat, in pixels. Must divide 16.
TREAD_PERIOD = 8


def rgba(hex_colour):
    h = hex_colour.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def parse(text):
    rows = [r for r in text.strip().splitlines()]
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError("maps must be 16x16, got %s" % [len(r) for r in rows])
    return rows


def render(rows, tone, roll=0):
    """One 16x16 frame, with the tread scrolled `roll` pixels towards the top."""
    colours = {
        ".": tone["K"], "d": tone["D"], "m": tone["M"],
        "l": tone["L"], "h": tone["H"],
        "a": tone["A"], "b": tone["B"],
    }
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        row = rows[(y + roll) % 16]
        for x, ch in enumerate(row):
            px[x, y] = rgba(colours[ch])
    return img



def bend(rows, plate, tone, roll=0):
    """The straight top, bent through a quarter turn: in at the west edge, out at the north.

    **Nothing is drawn twice.** A corner is the straight tile warped, pixel for pixel, rather than
    a second map that has to be kept in step with the first: edit the chevrons and the corner
    turns with them, and the two meet at a seam because they are the same picture.

    The bend is a quarter circle about the north-west corner of the tile. Each destination pixel
    is turned into a distance *along* the belt and a distance *across* it, and those two are the
    straight tile's row and column - so the rails bend into arcs and the chevrons bend into the
    turn without either being described again.

    Two details worth knowing:

    - **The along coordinate is stretched to sixteen pixels.** A quarter arc of radius eight is
      only 12.6 pixels long, and a tile that was not a whole number of tread repeats would put the
      chevrons out of phase at every seam between a corner and a straight.
    - **The inside of the turn collapses to a point**, because a belt is as wide as the radius it
      turns through. That is what a tight corner is, and Factorio's own corner does the same.
      Anything falling outside the belt - the far corner of the tile - is plate.
    """
    colours = {
        ".": tone["K"], "d": tone["D"], "m": tone["M"],
        "l": tone["L"], "h": tone["H"],
        "a": tone["A"], "b": tone["B"],
    }
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5, y + 0.5
            radius = math.hypot(dx, dy)
            angle = math.atan2(dy, dx)

            column = int(math.floor(radius - 8.0 + 8.0))
            row = int(math.floor(angle / (math.pi / 2) * 16))
            if 0 <= column < 16 and 0 <= row < 16:
                px[x, y] = rgba(colours[rows[(row + roll) % 16][column]])
            else:
                px[x, y] = rgba(colours[plate[y][x]])
    return img


def speeds():
    """Belt speeds in tiles per second, read from the mapping rather than typed here."""
    path = os.path.join(HERE, os.pardir, "data", "mapping.json")
    with open(path, encoding="utf-8") as handle:
        items = json.load(handle)["items"]
    return {
        "transport_belt": items["transport-belt"]["speed"],
        "fast_transport_belt": items["fast-transport-belt"]["speed"],
        "express_transport_belt": items["express-transport-belt"]["speed"],
    }


def frame_schedule(tiles_per_second, period=TREAD_PERIOD):
    """Which frame to show on each tick, so the tread scrolls at exactly the belt's speed.

    Frames are whole pixels and ticks are whole ticks, so a speed that is not a whole
    number of pixels a tick cannot be one frame per tick. It can still be exact on
    average: `frames` may name the same frame twice and in any order, so the schedule
    below advances by one pixel on some ticks and two on others.

    Returns the list of frame indices, one per tick, ending where it began so the loop is
    seamless.
    """
    # Pixels a tick, as an exact fraction.
    numerator = int(round(tiles_per_second * PIXELS_PER_BLOCK * 100))
    denominator = TICKS_PER_SECOND * 100
    common = math.gcd(numerator, denominator)
    numerator //= common
    denominator //= common

    # The loop closes on the first tick that is both a whole number of pixels along and a
    # whole number of tread repeats along.
    ticks = denominator
    while (ticks * numerator // denominator) % period != 0:
        ticks += denominator

    return [(tick * numerator // denominator) % period for tick in range(ticks)]


def main():
    top, side, bottom = parse(TOP), parse(SIDE), parse(BOTTOM)
    os.makedirs(OUT, exist_ok=True)

    tiers = list(TIERS) if "--all" in sys.argv else REGISTERED
    tile_speeds = speeds()

    written = []
    for tier in tiers:
        tone = TIERS[tier]
        schedule = frame_schedule(tile_speeds[tier])

        # Three tops, all animated: straight, and the two hands of a quarter turn. The right-hand
        # corner is the left one mirrored, which also swaps its lanes over - which is right, since
        # the far lane of a belt turning one way is the near lane of one turning the other.
        tops = {
            "top": lambda roll: render(top, tone, roll=roll),
            "top_left": lambda roll: bend(top, bottom, tone, roll=roll),
            "top_right": lambda roll: bend(top, bottom, tone, roll=roll)
                .transpose(Image.FLIP_LEFT_RIGHT),
        }
        for name, frame in tops.items():
            strip = Image.new("RGBA", (16, 16 * TREAD_PERIOD))
            for roll in range(TREAD_PERIOD):
                strip.paste(frame(roll), (0, 16 * roll))
            path = os.path.join(OUT, "%s_%s.png" % (tier, name))
            strip.save(path)
            with open(path + ".mcmeta", "w", encoding="utf-8") as handle:
                json.dump({"animation": {"frametime": 1, "frames": schedule}}, handle, indent=2)
                handle.write("\n")
            written.append((tier, name, frame(0)))

        for name, img in (("side", render(side, tone)), ("bottom", render(bottom, tone))):
            img.save(os.path.join(OUT, "%s_%s.png" % (tier, name)))
            written.append((tier, name, img))

        print("%s: %d frames a top, %d ticks to a loop, %s tiles a second"
              % (tier, TREAD_PERIOD, len(schedule), tile_speeds[tier]))

    print("wrote %d textures to %s" % (len(written), os.path.normpath(OUT)))

    if "--preview" in sys.argv:
        scale, pad, cols = 8, 6, 3
        cell = 16 * scale + pad
        rows = (len(written) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell + pad, rows * cell + pad), (32, 32, 32, 255))
        for i, (_tier, _name, img) in enumerate(written):
            r, c = divmod(i, cols)
            sheet.paste(img.resize((16 * scale,) * 2, Image.NEAREST),
                        (pad + c * cell, pad + r * cell))
        path = os.path.join(HERE, "belt-preview.png")
        sheet.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
