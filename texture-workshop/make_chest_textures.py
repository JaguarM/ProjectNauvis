"""Generate the iron and steel chests' entity textures - one palette per tier.

    python texture-workshop/make_chest_textures.py            # write the PNGs
    python texture-workshop/make_chest_textures.py --preview  # also write chest-preview.png

Unlike the belt and the materials there is no ASCII map here, because a chest texture is not
a picture of anything: it is six rectangles per box, unwrapped, and where each one lands is
decided by `ModelPart.Cube` rather than by us. So the maps are replaced by `faces` - the
arithmetic vanilla itself does - and the drawing is a handful of panel rules applied to
whichever rectangle a face turns out to be.

The layout, which is not ours to choose
---------------------------------------

`ChestModel.createSingleBodyLayer` builds three boxes on a 64x64 sheet:

    bottom  texOffs(0, 19)  14 x 10 x 14
    lid     texOffs(0,  0)  14 x  5 x 14
    lock    texOffs(0,  0)   2 x  4 x  1

and `ModelPart.Cube` unwraps each one as

        u0 = U          v0 = V
        u1 = U + d      v1 = V + d
        u2 = U + d + w  v2 = V + d + h

    DOWN  (u1,v0)-(u2,v1)     UP    (u2,v0)-(u2+w,v1)
    WEST  (u0,v1)-(u1,v2)     NORTH (u1,v1)-(u2,v2)
    EAST  (u2,v1)-(u3,v2)     SOUTH (u3,v1)-(u4,v2)

The lock shares the lid's origin, and fits in the corner of the lid's block that the lid's
own six faces do not use. That is vanilla's trick, not a coincidence, and it is why the two
may both say `texOffs(0, 0)`.

**The front of a chest is SOUTH.** The lock cube sits at z = 14..15, which is the +Z face, and
`ChestRenderer` turns the whole model to the block's facing afterwards. Draw the latch plate
on the south faces or it comes out on the back.

Ours are ours
-------------

Nothing here is traced from vanilla's `normal.png`. The rectangles are vanilla's because the
model decides them; the pixels inside are a plate, a border, a rivet at each corner and a
latch, which is the same vocabulary the belt and the drills are drawn with.
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(
    HERE, os.pardir, "nauvis_logistics", "src", "main", "resources", "assets",
    "nauvis_logistics", "textures", "entity", "chest",
)

SHEET = 64

# --- the layout ------------------------------------------------------------


def faces(u, v, width, height, depth):
    """The six rectangles a box at `texOffs(u, v)` occupies, as `{name: (x, y, w, h)}`."""
    u1, u2, u3 = u + depth, u + depth + width, u + depth + width + depth
    v1 = v + depth
    return {
        "down": (u1, v, width, depth),
        "up": (u2, v, width, depth),
        "west": (u, v1, depth, height),
        "north": (u1, v1, width, height),
        "east": (u2, v1, depth, height),
        "south": (u3, v1, width, height),
    }


LID = faces(0, 0, 14, 5, 14)
BOTTOM = faces(0, 19, 14, 10, 14)
LOCK = faces(0, 0, 2, 4, 1)

# --- palettes --------------------------------------------------------------
# Iron is neutral and dark, steel is cool and light. They are going to stand next to each
# other in the same base, so the difference has to be readable at the distance a player
# actually looks at a chest from, which is across the room and not in an inventory slot.
IRON = {
    "dark": (58, 58, 62, 255),
    "mid": (102, 102, 108, 255),
    "light": (140, 140, 148, 255),
    "high": (176, 176, 184, 255),
}

STEEL = {
    "dark": (82, 88, 99, 255),
    "mid": (136, 144, 157, 255),
    "light": (183, 191, 203, 255),
    "high": (219, 225, 233, 255),
}

TIERS = {"iron": IRON, "steel": STEEL}


# --- the drawing -----------------------------------------------------------


def rect(pixels, x, y, w, h, colour):
    for j in range(y, y + h):
        for i in range(x, x + w):
            pixels[i, j] = colour


def border(pixels, x, y, w, h, colour):
    rect(pixels, x, y, w, 1, colour)
    rect(pixels, x, y + h - 1, w, 1, colour)
    rect(pixels, x, y, 1, h, colour)
    rect(pixels, x + w - 1, y, 1, h, colour)


def rivets(pixels, x, y, w, h, palette, inset=2):
    """A bright pixel at each corner, inset from the border. What makes it read as riveted."""
    if w < 2 * inset + 2 or h < 2 * inset + 2:
        return
    for i in (x + inset, x + w - 1 - inset):
        for j in (y + inset, y + h - 1 - inset):
            pixels[i, j] = palette["high"]


def panel(pixels, box, palette, lit=True):
    """A plate: mid ground, dark border, one highlight row under the top edge, four rivets."""
    x, y, w, h = box
    rect(pixels, x, y, w, h, palette["mid"])
    border(pixels, x, y, w, h, palette["dark"])
    if lit and h > 2:
        rect(pixels, x + 1, y + 1, w - 2, 1, palette["light"])
    rivets(pixels, x, y, w, h, palette)


def latch(pixels, box, palette):
    """The plate the lock sits against, three pixels wide down the middle of a south face."""
    x, y, w, h = box
    rect(pixels, x + w // 2 - 2, y + 1, 4, h - 2, palette["light"])
    rect(pixels, x + w // 2 - 1, y + 1, 2, h - 2, palette["high"])


def draw(palette):
    image = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    pixels = image.load()

    for name, box in BOTTOM.items():
        panel(pixels, box, palette, lit=name not in ("up", "down"))
    for name, box in LID.items():
        panel(pixels, box, palette, lit=name not in ("up", "down"))

    # The lid's top is the face a player looks down on from a walkway, so it gets the one
    # thing the other five do not: a raised centre panel.
    x, y, w, h = LID["up"]
    rect(pixels, x + 3, y + 3, w - 6, h - 6, palette["light"])
    border(pixels, x + 3, y + 3, w - 6, h - 6, palette["dark"])

    latch(pixels, BOTTOM["south"], palette)
    latch(pixels, LID["south"], palette)

    # The lock itself, a small solid block of the brightest tone with a dark outline, so it
    # stands off the latch plate rather than melting into it.
    for name, box in LOCK.items():
        x, y, w, h = box
        rect(pixels, x, y, w, h, palette["high"])
        if w > 1 and h > 1:
            border(pixels, x, y, w, h, palette["dark"])

    return image


def preview(images):
    """What the front and the top of each chest look like, at 8x, without launching the game.

    Not a render - it pastes the faces that are actually visible on a closed chest into the
    arrangement you would see them in. Enough to catch a face drawn in the wrong rectangle,
    which is the mistake this file can plausibly make.
    """
    scale = 8
    cell = 18
    sheet = Image.new("RGBA", (cell * scale * len(images) * 2, cell * scale), (40, 40, 44, 255))
    for index, image in enumerate(images):
        for half, name in enumerate(("south", "up")):
            panelled = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
            lid = image.crop(box_to_tuple(LID[name]))
            panelled.paste(lid, (2, 1))
            # Looking down, the lid is the whole chest; from the front, the body is under it.
            if name != "up":
                panelled.paste(image.crop(box_to_tuple(BOTTOM[name])), (2, 1 + lid.height))
            sheet.alpha_composite(
                panelled.resize((cell * scale, cell * scale), Image.NEAREST),
                ((index * 2 + half) * cell * scale, 0))
    return sheet


def box_to_tuple(box):
    x, y, w, h = box
    return (x, y, x + w, y + h)


def main():
    os.makedirs(OUT, exist_ok=True)
    drawn = []
    for name, palette in TIERS.items():
        image = draw(palette)
        image.save(os.path.join(OUT, f"{name}.png"))
        print(f"wrote {name}.png ({len(set(image.get_flattened_data())) - 1} colours plus transparent)")
        drawn.append(image)

    if "--preview" in sys.argv:
        path = os.path.join(HERE, "chest-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, os.path.join(HERE, os.pardir))}")


if __name__ == "__main__":
    main()
