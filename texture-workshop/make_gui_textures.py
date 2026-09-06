"""Generate the GUI sprites the machine screens draw that vanilla does not ship.

    python texture-workshop/make_gui_textures.py            # write the PNGs
    python texture-workshop/make_gui_textures.py --preview  # also write gui-preview.png

Three sprites, and they are the machine screens' three meters: a flame for fuel, a bolt for
electricity, and an arrow for a smelt. Each is drawn the way vanilla's furnace draws its flame -
the whole sprite tinted dark as the empty meter, and then the bright sprite over it from the
bottom up (or the left, for the arrow) as far as the meter is full - so any screen that draws
one draws the others with the same three lines.

Why vanilla's own flame is not used
-----------------------------------

It was, for a day. `container/furnace/lit_progress` and `burn_progress` are opaque: the panel's
grey is baked in around the shape, because vanilla only ever blits them over a panel of that grey,
where the background is invisible. Tinted dark on a dark panel they are boxes. So the fire and the
arrow are drawn here, in vanilla's pixel idiom and its flame palette, with air where vanilla has
grey; `container/slot` is the one vanilla sprite the screens still use, because a slot is a box.

Two ways a map is coloured. The bolt and the arrow are a silhouette with the shading derived: a
filled pixel with nothing to its right or below is the dark edge, one with nothing above or to
its left is the highlight, and the rest is the body - which is how vanilla shades its own small
sprites. The flame names its colours, because a flame is a gradient and not a bevel.

Every PNG is written into `nauvis_lib`, the library every machine screen is drawn from, and the
sprite ids are `nauvis_lib:<name>`. They were once copied into each mod that drew them; the
library is the one copy now.
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, os.pardir)


# Where a GUI sprite lives. The gui atlas takes `textures/gui/sprites/<name>.png` from any
# namespace, so the sprite id is `nauvis_lib:<name>`.
OUT = os.path.join(ROOT, "nauvis_lib", "src", "main", "resources", "assets", "nauvis_lib",
                   "textures", "gui", "sprites")


AIR = (0, 0, 0, 0)

# A fat bolt, pointing down and left, the way it is drawn on a fuse box.
BOLT = """
........####..
.......####...
......####....
.....####.....
....#########.
...#########..
..#########...
......####....
.....####.....
....####......
...####.......
..####........
.####.........
.###..........
"""

# The assembler's electricity colour, with an edge and a highlight either side of it.
BOLT_TONES = ((0xFF, 0xD2, 0x4A, 0xFF), (0xC4, 0x8A, 0x12, 0xFF), (0xFF, 0xF0, 0xA8, 0xFF))

# A flame with a tongue off its right side, in the furnace's own three colours: orange round
# the outside, yellow within, white at the heart. Fourteen by fourteen, like vanilla's.
FLAME = """
......o.......
.....oo.......
.....oo...o...
....ooo..oo...
....ooo..oo...
...oooo.ooo...
...ooyoooooo..
..ooyyyooooo..
..ooyyyyoooo..
..oyywwyyooo..
..oyywwyyooo..
...oyyyyyoo...
....oyyyoo....
.....oooo.....
"""

FLAME_PALETTE = {
    "o": (0xFF, 0xB6, 0x00, 0xFF),
    "y": (0xFF, 0xFF, 0x1F, 0xFF),
    "w": (0xFF, 0xFF, 0xFF, 0xFF),
}

# An arrow pointing right, twenty-four by sixteen like vanilla's: a shaft four deep and a head
# that fills the height. It fills from the left, so the head is the last thing to light.
ARROW = """
........................
..............#.........
..............##........
..............###.......
..............####......
..............#####.....
####################....
#####################...
#####################...
####################....
..............#####.....
..............####......
..............###.......
..............##........
..............#.........
........................
"""

# White, with a grey edge; the highlight is white too, so the arrow reads as a flat shape.
ARROW_TONES = ((0xFF, 0xFF, 0xFF, 0xFF), (0xA8, 0xA8, 0xA8, 0xFF), (0xFF, 0xFF, 0xFF, 0xFF))


def parse(text):
    rows = text.strip("\n").splitlines()
    width = len(rows[0])
    assert all(len(row) == width for row in rows), "every row of a map is the same width"
    return rows


def filled(rows, x, y):
    return 0 <= y < len(rows) and 0 <= x < len(rows[y]) and rows[y][x] != "."


def render_shaded(rows, tones):
    """A silhouette, shaded: edge where the light does not reach, highlight where it does."""
    body, edge, light = tones
    img = Image.new("RGBA", (len(rows[0]), len(rows)), AIR)
    for y, row in enumerate(rows):
        for x, _ in enumerate(row):
            if not filled(rows, x, y):
                continue
            if not filled(rows, x + 1, y) or not filled(rows, x, y + 1):
                colour = edge
            elif not filled(rows, x - 1, y) or not filled(rows, x, y - 1):
                colour = light
            else:
                colour = body
            img.putpixel((x, y), colour)
    return img


def render_palette(rows, palette):
    """A map that names its own colours, letter by letter."""
    img = Image.new("RGBA", (len(rows[0]), len(rows)), AIR)
    for y, row in enumerate(rows):
        for x, letter in enumerate(row):
            if letter != ".":
                img.putpixel((x, y), palette[letter])
    return img


SPRITES = {
    "charge_bolt": render_shaded(parse(BOLT), BOLT_TONES),
    "meter_flame": render_palette(parse(FLAME), FLAME_PALETTE),
    "meter_arrow": render_shaded(parse(ARROW), ARROW_TONES),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, img in SPRITES.items():
        path = os.path.join(OUT, name + ".png")
        img.save(path)
        print("wrote", os.path.relpath(path, ROOT))

    if "--preview" in sys.argv:
        scale = 8
        pad = 8
        images = list(SPRITES.values())
        width = sum(img.width * scale for img in images) + pad * (len(images) + 1)
        height = max(img.height * scale for img in images) + 2 * pad
        sheet = Image.new("RGBA", (width, height), (20, 20, 20, 255))
        x = pad
        for img in images:
            big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
            sheet.paste(big, (x, pad), big)
            x += big.width + pad
        path = os.path.join(HERE, "gui-preview.png")
        sheet.save(path)
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
