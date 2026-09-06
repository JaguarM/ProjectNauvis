"""Generate the three module item textures from one ASCII map plus three palettes.

    python texture-workshop/make_module_textures.py            # write the PNGs
    python texture-workshop/make_module_textures.py --preview  # also write module-preview.png

The same file shape as `make_material_textures.py`, and the same reason: a module is a chip, and
the three tiers-one modules are the *same* chip in three colours, so they come off one map and
cannot drift apart. Factorio's colours, because a player reads a module by its colour before its
name - blue for speed, green for efficiency, red for productivity.

Legend for the map:
    .  transparent                   d  dark board / outline
    m  mid board                     l  light board edge
    c  the module's colour           b  the colour, brighter
    p  a pin
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, os.pardir)
OUT = os.path.join(REPO, "nauvis_machines", "src", "main", "resources", "assets", "nauvis_machines",
                   "textures", "item")

SIZE = 16

# A square chip seen from above: a dark board with a lighter top edge, a coloured window across
# the middle with a bright bar in it, and four pins down each side.
MODULE = """
................
..p..dddddddd...
..pp.dlllllld...
..p..dmmmmmmd...
..pp.dmccccmd...
..p..dmcbbcmd...
..pp.dmcbbcmd...
..p..dmccccmd...
..pp.dmmmmmmd...
..p..dmccccmd...
..pp.dmcbbcmd...
..p..dmccccmd...
..pp.dmmmmmmd...
..p..dddddddd...
................
................
"""

BOARD = {
    ".": (0, 0, 0, 0),
    "d": (40, 44, 52, 255),
    "m": (78, 84, 96, 255),
    "l": (118, 126, 140, 255),
    "p": (196, 176, 96, 255),
}

BLUE = dict(BOARD, c=(48, 108, 196, 255), b=(120, 176, 240, 255))
GREEN = dict(BOARD, c=(56, 150, 72, 255), b=(130, 220, 140, 255))
RED = dict(BOARD, c=(178, 48, 48, 255), b=(240, 120, 110, 255))

# name -> palette
ITEMS = {
    "speed_module": BLUE,
    "effectivity_module": GREEN,
    "productivity_module": RED,
}


def rows(text):
    lines = text.strip("\n").split("\n")
    if len(lines) != SIZE or any(len(line) != SIZE for line in lines):
        raise ValueError(f"a map must be {SIZE}x{SIZE}; got {len(lines)} rows of "
                         f"{sorted({len(line) for line in lines})}")
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
    scale = 8
    sheet = Image.new("RGBA", (SIZE * scale * len(images), SIZE * scale), (0, 0, 0, 0))
    for index, image in enumerate(images):
        sheet.paste(image.resize((SIZE * scale, SIZE * scale), Image.NEAREST),
                    (index * SIZE * scale, 0))
    return sheet


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    drawn = []
    for name, palette in ITEMS.items():
        image = draw(MODULE, palette)
        image.save(os.path.join(OUT, f"{name}.png"))
        print(f"wrote nauvis_machines/{name}.png")
        drawn.append(image)
    if "--preview" in sys.argv:
        path = os.path.join(HERE, "module-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
