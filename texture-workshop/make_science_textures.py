"""Generate the seven science pack item textures from one flask map plus a colour per pack.

    python texture-workshop/make_science_textures.py            # write the PNGs
    python texture-workshop/make_science_textures.py --preview  # also write science-preview.png

The same file shape as `make_barrel_textures.py`, for the same reason: Factorio's science packs
are one Erlenmeyer flask in seven colours, and the colour is the whole of what a player reads off
a lab's slot. So the flask is one map and each pack is a palette, drawn the way vanilla draws a
potion - glass edges, a highlight down the left, the liquid in three tones - so it sits beside a
water bottle as the same kind of thing. Six packs go to `nauvis_research` and the space science
pack to `nauvis_rocket`, because an item lives in the mod the mapping gives it.

Legend for the map:
    .  transparent                   g  glass edge
    w  glass highlight               e  glass edge, shadowed
    k  the neck's ring, dark         m  the ring, lit
    c  the liquid                    b  the liquid, lit
    d  the liquid, shadowed
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, os.pardir)
OUT = {
    "nauvis_research": os.path.join(REPO, "nauvis_research", "src", "main", "resources", "assets",
                                    "nauvis_research", "textures", "item"),
    "nauvis_rocket": os.path.join(REPO, "nauvis_rocket", "src", "main", "resources", "assets",
                                  "nauvis_rocket", "textures", "item"),
}

SIZE = 16

# A conical flask: a ringed neck, clear glass down to the shoulder, the liquid filling the cone
# with a highlight streak down its left and the shadow pooled right and bottom.
FLASK = """
................
.....kmmmmk.....
.....kkkkkk.....
......w..e......
......w..e......
......g..e......
.....g....e.....
....g......e....
...gcbcccccde...
..gcbccccccdde..
.gcbccccccccdde.
.gccccccccccdde.
.gdccccccccddde.
.gdddddddddddde.
..eeeeeeeeeeee..
................
"""

GLASS = {
    ".": (0, 0, 0, 0),
    "g": (204, 216, 226, 255),
    "w": (244, 250, 255, 255),
    "e": (140, 156, 172, 255),
    "k": (52, 56, 64, 255),
    "m": (112, 118, 130, 255),
}


def palette(liquid, lit, shadowed):
    return dict(GLASS, c=liquid + (255,), b=lit + (255,), d=shadowed + (255,))


# Factorio's colours: red, green, blue, grey, purple, yellow, white.
ITEMS = {
    "automation_science_pack": (palette((204, 40, 40), (246, 112, 100), (128, 20, 26)), "nauvis_research"),
    "logistic_science_pack": (palette((62, 172, 62), (142, 232, 132), (32, 106, 42)), "nauvis_research"),
    "chemical_science_pack": (palette((52, 150, 222), (142, 216, 250), (26, 86, 152)), "nauvis_research"),
    "military_science_pack": (palette((104, 110, 124), (170, 176, 190), (56, 60, 72)), "nauvis_research"),
    "production_science_pack": (palette((150, 62, 202), (206, 142, 246), (90, 30, 132)), "nauvis_research"),
    "utility_science_pack": (palette((226, 186, 40), (255, 236, 132), (150, 116, 20)), "nauvis_research"),
    "space_science_pack": (palette((232, 236, 240), (255, 255, 255), (172, 180, 192)), "nauvis_rocket"),
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
    drawn = []
    for name, (colours, mod) in ITEMS.items():
        out = OUT[mod]
        if not os.path.isdir(out):
            os.makedirs(out)
        image = draw(FLASK, colours)
        image.save(os.path.join(out, f"{name}.png"))
        print(f"wrote {mod}/{name}.png")
        drawn.append(image)
    if "--preview" in sys.argv:
        path = os.path.join(HERE, "science-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
