"""Generate Neo Progressive Materials' item textures from ASCII maps plus a palette.

The same file shape as `make_belt_textures.py` and Neo Progressive Automation's
`make_miner_textures.py`, for the same reason: a texture is a map plus a palette, so a
change to the map moves every item drawn from it and they cannot drift apart.

    python texture-workshop/make_material_textures.py            # write the PNGs
    python texture-workshop/make_material_textures.py --preview  # also write material-preview.png

This writes into `../NeoProgressiveMaterials`, which is a sibling repo rather than a
subproject -- see CLAUDE.md. Only the items with a map below are written; the copper cable,
the iron gear wheel and the electronic circuit were drawn by hand before this file existed
and are left alone rather than redrawn from a guess at their maps.

Legend for the maps:
    .  transparent                   m  mid material
    d  dark material / outline       l  light material
    h  highlight

The plate, and why it is a stack
--------------------------------

Factorio's plate icons are a *stack* of plates, and that is the whole reason a plate reads
as a plate and not as an ingot: one flat slab is a bar, three of them is a material you
count in units. So the map draws the top face of the topmost plate and then the front edge
of three, separated by the dark line between them.

Four tones and transparency, which is the vanilla budget -- `iron_block` gets by on eleven
and `cobblestone` on six. Steel is the same neutral grey as iron, one step cooler and one
step lighter, because those two are going to sit beside each other in every inventory the
player owns and the difference has to survive being 16 pixels wide.
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(
    HERE, os.pardir, os.pardir, "NeoProgressiveMaterials", "src", "main", "resources",
    "assets", "neoprogressivematerials", "textures", "item",
)

SIZE = 16

# --- maps ------------------------------------------------------------------
# Three plates seen slightly from above: the top face of the topmost, then the front edge
# of the two under it with the dark line between them. Fourteen wide and seven tall, which
# is the whole trick -- a stack this shape reads as flat plates, and the same drawing at
# eleven tall reads as a crate.
#
# The narrow row at the top is the back edge, one pixel in on each side. That single row of
# bevel is what puts the viewer above the stack rather than level with it.
STEEL_PLATE = """
................
................
................
..dddddddddddd..
.dhhhhhhhhhhhhd.
.dlllllllllllld.
.dlllllllllllld.
.dmmmmmmmmmmmmd.
.dddddddddddddd.
.dmmmmmmmmmmmmd.
.dddddddddddddd.
.dmmmmmmmmmmmmd.
.dddddddddddddd.
................
................
................
"""

# --- palettes --------------------------------------------------------------
# Cooler and lighter than iron: iron_block's greys are neutral, so a blue cast plus a
# brighter top face is what tells the two apart at a glance.
STEEL = {
    ".": (0, 0, 0, 0),
    "d": (77, 83, 93, 255),
    "m": (121, 129, 141, 255),
    "l": (168, 176, 187, 255),
    "h": (213, 219, 226, 255),
}

ITEMS = {
    "steel_plate": (STEEL_PLATE, STEEL),
}


def rows(text):
    """The map as a list of rows, with the leading and trailing blank lines dropped."""
    lines = [line for line in text.strip("\n").split("\n")]
    if len(lines) != SIZE or any(len(line) != SIZE for line in lines):
        raise ValueError(f"a map must be {SIZE}x{SIZE}; got {len(lines)} rows")
    return lines


def draw(text, palette):
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    for y, line in enumerate(rows(text)):
        for x, char in enumerate(line):
            if char not in palette:
                raise ValueError(f"no palette entry for {char!r} at ({x}, {y})")
            pixels[x, y] = palette[char]
    return image


def preview(images):
    """Every item side by side at 8x, for looking at without launching the game."""
    scale = 8
    sheet = Image.new("RGBA", (SIZE * scale * len(images), SIZE * scale), (0, 0, 0, 0))
    for index, image in enumerate(images):
        sheet.paste(image.resize((SIZE * scale, SIZE * scale), Image.NEAREST),
                    (index * SIZE * scale, 0))
    return sheet


def main():
    if not os.path.isdir(OUT):
        raise SystemExit(f"{OUT} does not exist - is NeoProgressiveMaterials checked out beside this repo?")

    drawn = []
    for name, (text, palette) in ITEMS.items():
        image = draw(text, palette)
        image.save(os.path.join(OUT, f"{name}.png"))
        print(f"wrote {name}.png ({len(set(image.get_flattened_data())) - 1} colours plus transparent)")
        drawn.append(image)

    if "--preview" in sys.argv:
        path = os.path.join(HERE, "material-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, os.path.join(HERE, os.pardir))}")


if __name__ == "__main__":
    main()
