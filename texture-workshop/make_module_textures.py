"""Generate the three module item textures from one ASCII map plus three palettes.

    python texture-workshop/make_module_textures.py            # write the PNGs
    python texture-workshop/make_module_textures.py --preview  # also write module-preview.png

The same file shape as `make_material_textures.py`, and the same reason: a module is Factorio's
square unit with three lamps along its top, and the three tiers-one modules are the *same* unit
in three colours, so they come off one map and cannot drift apart. Factorio's colours, because a
player reads a module by its colour before its name - blue for speed, green for efficiency, red
for productivity.

Legend for the map:
    .  transparent                   d  dark board / outline
    m  mid board                     l  light board edge
    c  the module's colour           b  the colour, brighter
    p  a gold trace                  t  a tier stripe, gold
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, os.pardir)
OUT = os.path.join(REPO, "nauvis_machines", "src", "main", "resources", "assets", "nauvis_machines",
                   "textures", "item")

SIZE = 16

# A square unit seen from above: a dark board lit along its top and left, three domed lamps in
# the module's colour along the top, and two gold traces across the middle.
MODULE = """
................
................
.dddddddddddddd.
.dlllllllllllld.
.dlbbbmbbbmbbbd.
.dlcbcmcbcmcbcd.
.dlmcmmmcmmmcmd.
.dlmmmmmmmmmmmd.
.dlmpppmmmpppmd.
.dlmmmpmmmmpmmd.
.dlmmmmmmmmmmmd.
.dlmmmmmmmmmmmd.
.dlmmmmmmmmmmmd.
.dddddddddddddd.
................
................
"""

BOARD = {
    ".": (0, 0, 0, 0),
    "d": (40, 44, 52, 255),
    "m": (78, 84, 96, 255),
    "l": (118, 126, 140, 255),
    "p": (196, 176, 96, 255),
    "t": (228, 196, 84, 255),
}

BLUE = dict(BOARD, c=(48, 108, 196, 255), b=(120, 176, 240, 255))
GREEN = dict(BOARD, c=(56, 150, 72, 255), b=(130, 220, 140, 255))
RED = dict(BOARD, c=(178, 48, 48, 255), b=(240, 120, 110, 255))

# The second and third tiers are the same chip with a gold stripe across the board for each
# tier above the first: one stripe for a 2, two for a 3. A tier reads as a count before it
# reads as a name, which is what a player sorting a chest of them wants.
def striped(text, row):
    """The map with the board row at {@code row} turned to a gold stripe."""
    lines = text.split("\n")
    assert lines[row] == ".dlmmmmmmmmmmmd.", f"row {row} is not a plain board row: {lines[row]!r}"
    lines[row] = ".dltttttttttttd."
    return "\n".join(lines)


MODULE_2 = striped(MODULE, 13)
MODULE_3 = striped(MODULE_2, 11)

for tiered in (MODULE_2, MODULE_3):
    assert tiered != MODULE, "a tier's stripe did not land on the map"

# name -> (map, palette)
ITEMS = {
    "speed_module": (MODULE, BLUE),
    "efficiency_module": (MODULE, GREEN),
    "productivity_module": (MODULE, RED),
    "speed_module_2": (MODULE_2, BLUE),
    "efficiency_module_2": (MODULE_2, GREEN),
    "productivity_module_2": (MODULE_2, RED),
    "speed_module_3": (MODULE_3, BLUE),
    "efficiency_module_3": (MODULE_3, GREEN),
    "productivity_module_3": (MODULE_3, RED),
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
    for name, (text, palette) in ITEMS.items():
        image = draw(text, palette)
        image.save(os.path.join(OUT, f"{name}.png"))
        print(f"wrote nauvis_machines/{name}.png")
        drawn.append(image)
    if "--preview" in sys.argv:
        path = os.path.join(HERE, "module-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
