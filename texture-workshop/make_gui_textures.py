"""Generate the GUI sprites the machine screens draw that vanilla does not ship.

    python texture-workshop/make_gui_textures.py            # write the PNGs
    python texture-workshop/make_gui_textures.py --preview  # also write gui-preview.png

One sprite today: the charge bolt, which is the machine screens' picture of electricity. It is
drawn the way vanilla's furnace flame is - the whole sprite tinted dark as the empty meter, and
then the bright sprite over it from the bottom up, as far as the buffer is full - so it is the
same shape and the same size as `container/furnace/lit_progress`, fourteen by fourteen, and any
screen that draws a flame draws a bolt with the same three lines.

The map is the silhouette; the shading is derived. A filled pixel with nothing to its right or
below is the dark edge, one with nothing above or to its left is the highlight, and the rest is
the body - which is how vanilla's own flame is shaded, and what makes a fourteen-pixel bolt read
as a Minecraft sprite rather than a yellow blob.

The PNG is written into every mod that draws it. A subsystem mod may not depend on another and a
sprite is not code, so it cannot live in one place; generating both copies from this one map is
what keeps them from drifting, the same argument that keeps the belt tiers on one map.
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, os.pardir)

# Every mod with a screen that shows a charge, and where a GUI sprite lives in it. The gui atlas
# takes `textures/gui/sprites/<name>.png` from any namespace, so the sprite id is `<mod>:charge_bolt`.
OUT = {
    "nauvis_machines": os.path.join(
        ROOT, "nauvis_machines", "src", "main", "resources", "assets", "nauvis_machines",
        "textures", "gui", "sprites"),
    "nauvis_research": os.path.join(
        ROOT, "nauvis_research", "src", "main", "resources", "assets", "nauvis_research",
        "textures", "gui", "sprites"),
}

# A fat bolt, pointing down and left, the way it is drawn on a fuse box. `#` is bolt, `.` is air.
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
BODY = (0xFF, 0xD2, 0x4A, 0xFF)
EDGE = (0xC4, 0x8A, 0x12, 0xFF)
LIGHT = (0xFF, 0xF0, 0xA8, 0xFF)
AIR = (0, 0, 0, 0)


def parse(text):
    rows = [line for line in text.strip("\n").splitlines()]
    width = len(rows[0])
    assert all(len(row) == width for row in rows), "every row of a map is the same width"
    return rows


def filled(rows, x, y):
    return 0 <= y < len(rows) and 0 <= x < len(rows[y]) and rows[y][x] == "#"


def render(rows):
    """The silhouette, shaded: edge where the light does not reach, highlight where it does."""
    height = len(rows)
    width = len(rows[0])
    img = Image.new("RGBA", (width, height), AIR)
    for y in range(height):
        for x in range(width):
            if not filled(rows, x, y):
                continue
            if not filled(rows, x + 1, y) or not filled(rows, x, y + 1):
                colour = EDGE
            elif not filled(rows, x - 1, y) or not filled(rows, x, y - 1):
                colour = LIGHT
            else:
                colour = BODY
            img.putpixel((x, y), colour)
    return img


def main():
    bolt = render(parse(BOLT))
    for mod, directory in OUT.items():
        os.makedirs(directory, exist_ok=True)
        path = os.path.join(directory, "charge_bolt.png")
        bolt.save(path)
        print("wrote", os.path.relpath(path, ROOT))

    if "--preview" in sys.argv:
        scale = 8
        sheet = Image.new("RGBA", (bolt.width * scale + 16, bolt.height * scale + 16), (20, 20, 20, 255))
        sheet.paste(bolt.resize((bolt.width * scale, bolt.height * scale), Image.NEAREST), (8, 8), bolt.resize(
            (bolt.width * scale, bolt.height * scale), Image.NEAREST))
        path = os.path.join(HERE, "gui-preview.png")
        sheet.save(path)
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
