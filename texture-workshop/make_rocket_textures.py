"""Generate the rocket part and satellite item textures from ASCII maps plus palettes.

    python texture-workshop/make_rocket_textures.py            # write the PNGs
    python texture-workshop/make_rocket_textures.py --preview  # also write rocket-preview.png

The same file shape as `make_material_textures.py`. The space science pack is the rocket mod's
third item and comes off the flask map in `make_science_textures.py` with the other six.

Legend for the maps:
    .  transparent                   d  dark / outline
    m  mid                           l  light
    h  highlight                     a  the item's accent
    b  the accent, lit
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, os.pardir)
OUT = os.path.join(REPO, "nauvis_rocket", "src", "main", "resources", "assets", "nauvis_rocket",
                   "textures", "item")

SIZE = 16

# A section of rocket standing up: a domed cap, a banded cream hull, and the green control panel
# set into the lower half, the way Factorio's icon has it.
ROCKET_PART = """
................
......dddd......
.....dlhhld.....
....dllhhlld....
....dddddddd....
....dmllllmd....
....dmllllmd....
....dddddddd....
....dmllllmd....
....dmabbamd....
....dmabbamd....
....dmaaaamd....
....dmllllmd....
....dddddddd....
.....dmmmmd.....
......dddd......
"""

# A satellite: a grey body with a dish under it and a solar panel out to each side.
SATELLITE = """
................
.......dd.......
......dmmd......
.dddd.dllddddd..
.dabadhllhdabad.
.dbabdhllhdbabd.
.dabadhllhdabad.
.dbabdmllmdbabd.
.dddd.dmmd.dddd.
......dddd......
.......dd.......
.....ddlldd.....
....dlhhhhld....
....dllhhlld....
.....ddlldd.....
.......dd.......
"""

# Cream hull plates with a green panel.
HULL = {
    ".": (0, 0, 0, 0),
    "d": (96, 82, 48, 255),
    "m": (176, 156, 96, 255),
    "l": (222, 206, 150, 255),
    "h": (246, 238, 200, 255),
    "a": (46, 120, 50, 255),
    "b": (110, 210, 110, 255),
}

# Steel with blue solar cells.
STEEL_AND_BLUE = {
    ".": (0, 0, 0, 0),
    "d": (52, 56, 64, 255),
    "m": (112, 118, 130, 255),
    "l": (164, 170, 182, 255),
    "h": (214, 220, 230, 255),
    "a": (40, 84, 176, 255),
    "b": (96, 150, 236, 255),
}

# name -> (map, palette)
ITEMS = {
    "rocket_part": (ROCKET_PART, HULL),
    "satellite": (SATELLITE, STEEL_AND_BLUE),
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
        print(f"wrote nauvis_rocket/{name}.png")
        drawn.append(image)
    if "--preview" in sys.argv:
        path = os.path.join(HERE, "rocket-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
