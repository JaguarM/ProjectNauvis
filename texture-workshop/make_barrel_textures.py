"""Generate the eight barrel item textures from one ASCII map plus a band colour per fluid.

    python texture-workshop/make_barrel_textures.py            # write the PNGs
    python texture-workshop/make_barrel_textures.py --preview  # also write barrel-preview.png

The same file shape as `make_material_textures.py`. Factorio's barrels are one steel drum with a
band and a lid in the fluid's colour, and that is what a chest of them has to say at a glance: the
drum is the same, the colour is the fluid. The empty barrel has no band at all, so it reads as the
odd one out. The fluid colours are the ones the fluids are drawn with in a pipe or a tank.

Legend for the map:
    .  transparent                   d  dark steel / outline
    m  mid steel                     l  light steel
    h  steel highlight               c  the fluid's colour, on the band and the lid
    b  the fluid's colour, brighter
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, os.pardir)
OUT = os.path.join(REPO, "nauvis_fluids", "src", "main", "resources", "assets", "nauvis_fluids",
                   "textures", "item")

SIZE = 16

# A drum standing up, seen a little from above: a lid, a band round the middle, and the two tones
# down the left that make it round.
BARREL = """
................
.....dddddd.....
....dbbccccd....
....dccccccd....
....dmmmmmmd....
....dlmmmmmd....
....dlmmmmmd....
....dccccccd....
....dbcccccd....
....dccccccd....
....dlmmmmmd....
....dlmmmmmd....
....dmmmmmmd....
....dhhmmmmd....
.....dddddd.....
................
"""

STEEL = {
    ".": (0, 0, 0, 0),
    "d": (52, 56, 62, 255),
    "m": (112, 118, 128, 255),
    "l": (150, 156, 166, 255),
    "h": (188, 194, 204, 255),
}


def palette(colour, bright):
    return dict(STEEL, c=colour, b=bright)


# The empty barrel's band is steel too: the drum and nothing else.
EMPTY = dict(STEEL, c=STEEL["m"], b=STEEL["l"])

# name -> palette. Water's blue, crude's black, heavy oil's red-brown, light oil's amber,
# lubricant's green, petroleum gas's mauve, sulfuric acid's yellow: the colours the fluids
# are drawn with, so a barrel reads as the pipe it came from.
ITEMS = {
    "barrel": EMPTY,
    "water_barrel": palette((48, 108, 196, 255), (110, 170, 240, 255)),
    "crude_oil_barrel": palette((24, 22, 26, 255), (70, 66, 74, 255)),
    "heavy_oil_barrel": palette((150, 62, 30, 255), (210, 110, 60, 255)),
    "light_oil_barrel": palette((214, 150, 40, 255), (250, 200, 90, 255)),
    "lubricant_barrel": palette((50, 140, 70, 255), (110, 210, 130, 255)),
    "petroleum_gas_barrel": palette((140, 80, 170, 255), (200, 150, 230, 255)),
    "sulfuric_acid_barrel": palette((200, 190, 40, 255), (240, 235, 120, 255)),
}


def rows(text):
    lines = text.strip("\n").split("\n")
    if len(lines) != SIZE or any(len(line) != SIZE for line in lines):
        raise ValueError(f"a map must be {SIZE}x{SIZE}; got {len(lines)} rows of "
                         f"{sorted({len(line) for line in lines})}")
    return lines


def draw(text, colours):
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    for y, line in enumerate(rows(text)):
        for x, char in enumerate(line):
            if char not in colours:
                raise ValueError(f"no palette entry for {char!r} at ({x}, {y})")
            pixels[x, y] = colours[char]
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
    for name, colours in ITEMS.items():
        image = draw(BARREL, colours)
        image.save(os.path.join(OUT, f"{name}.png"))
        print(f"wrote nauvis_fluids/{name}.png")
        drawn.append(image)
    if "--preview" in sys.argv:
        path = os.path.join(HERE, "barrel-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
