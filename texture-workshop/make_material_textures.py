"""Generate the pack's intermediate-product item textures from ASCII maps plus a palette.

The same file shape as `make_belt_textures.py` and Neo Progressive Automation's
`make_miner_textures.py`, for the same reason: a texture is a map plus a palette, so a
change to the map moves every item drawn from it and they cannot drift apart.

    python texture-workshop/make_material_textures.py            # write the PNGs
    python texture-workshop/make_material_textures.py --preview  # also write material-preview.png

Most of these go into `../NeoProgressiveMaterials`, which is a sibling repo rather than a
subproject -- see CLAUDE.md -- and two go into mods here, because an item lives in the mod the
mapping gives it: solid fuel is the pack mod's and explosives are the fluids mod's. Only the
items with a map below are written; the copper cable, the iron gear wheel and the electronic
circuit were drawn by hand before this file existed and are left alone rather than redrawn from
a guess at their maps.

Legend for the maps:
    .  transparent                   m  mid material
    d  dark material / outline       l  light material
    h  highlight                     anything else is the item's own, see its palette

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

The oil chain's items are drawn to be told apart at a glance in a chest of them: a white bar,
a yellow lump, a black cylinder with a red cap, a red board, a grey block with a piston, a
black brick, and three red sticks with fuses.
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, os.pardir)

# Where each mod keeps its item textures. The sibling repo is beside this one.
OUT = {
    "neoprogressivematerials": os.path.join(
        REPO, os.pardir, "NeoProgressiveMaterials", "src", "main", "resources",
        "assets", "neoprogressivematerials", "textures", "item"),
    "nauvis": os.path.join(REPO, "nauvis", "src", "main", "resources", "assets", "nauvis", "textures", "item"),
    "nauvis_fluids": os.path.join(
        REPO, "nauvis_fluids", "src", "main", "resources", "assets", "nauvis_fluids", "textures", "item"),
}

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

# One bar, bevelled the same way, in white: Factorio's plastic bar is a white bar and nothing
# else in an inventory is.
PLASTIC_BAR = """
................
................
................
................
....dddddddddd..
...dhhhhhhhhhhd.
...dlllllllllld.
...dlllllllllld.
...dmmmmmmmmmmd.
....dddddddddd..
................
................
................
................
................
................
"""

# A lump, lit from the top left.
SULFUR = """
................
................
................
......ddd.......
.....dlhhd......
....dlhhlld.....
...dllllmmmd....
...dlmmmmmmd....
...dmmmmmmmd....
....dmmmmmd.....
.....ddddd......
................
................
................
................
................
"""

# A cylinder standing up, with a red cap: the two tones down the left are the round of it.
BATTERY = """
................
.....drrrrd.....
....drrrrrrd....
....dmmmmmmd....
....dllmmmmd....
....dllmmmmd....
....dllmmmmd....
....dllhhmmd....
....dllhhmmd....
....dllmmmmd....
....dllmmmmd....
....dllmmmmd....
....dmmmmmmd....
.....dddddd.....
................
................
"""

# A red board with pale traces and a chip in the middle, pins along the bottom edge: the
# electronic circuit's shape in the advanced circuit's colour.
ADVANCED_CIRCUIT = """
................
..dddddddddddd..
..dmmmmmmmmmmd..
..dmwwmmmwwmmd..
..dmwmmmmmwmmd..
..dmmmmhmmmmmd..
..dmmmhhhmmmmd..
..dmmmmhmmmmmd..
..dmwmmmmmwmmd..
..dmwwmmmwwmmd..
..dmmmmmmmmmmd..
..dddddddddddd..
...d..d..d..d...
................
................
................
"""

# An engine block with a piston standing out of the top and two bright ports on its face.
ENGINE_UNIT = """
................
......dddd......
.....dllhhd.....
.....dllmmd.....
....ddmmmmdd....
...dmmmmmmmmd...
...dmllmmllmd...
...dmllmmllmd...
...dmmmmmmmmd...
...ddddddddddd..
..dmmmmmmmmmmd..
..dmhhmmmmhhmd..
..dmmmmmmmmmmd..
..dddddddddddd..
................
................
"""

# A brick of it, the plastic bar's shape and taller, nearly black.
SOLID_FUEL = """
................
................
................
....dddddddddd..
...dhhhhhhhhhhd.
...dlllllllllld.
...dlllllllllld.
...dmmmmmmmmmmd.
...dmmmmmmmmmmd.
...dmmmmmmmmmmd.
...dmmmmmmmmmmd.
....dddddddddd..
................
................
................
................
"""

# Three sticks bound side by side, a fuse out of the top of each.
EXPLOSIVES = """
.....f..f..f....
.....f..f..f....
....dddddddddd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dhmdhmdhmd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dlmdlmdlmd..
....dddddddddd..
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

PLASTIC = {
    ".": (0, 0, 0, 0),
    "d": (166, 166, 156, 255),
    "m": (222, 222, 212, 255),
    "l": (243, 243, 238, 255),
    "h": (255, 255, 255, 255),
}

YELLOW = {
    ".": (0, 0, 0, 0),
    "d": (148, 118, 22, 255),
    "m": (210, 178, 40, 255),
    "l": (238, 213, 72, 255),
    "h": (255, 240, 132, 255),
}

# The battery's body is near black, so its red cap is what the eye finds.
BLACK_AND_RED = {
    ".": (0, 0, 0, 0),
    "d": (38, 38, 43, 255),
    "m": (68, 68, 76, 255),
    "l": (108, 108, 118, 255),
    "h": (148, 148, 158, 255),
    "r": (198, 50, 40, 255),
}

RED_BOARD = {
    ".": (0, 0, 0, 0),
    "d": (92, 22, 22, 255),
    "m": (152, 36, 36, 255),
    "l": (202, 62, 56, 255),
    "h": (240, 122, 110, 255),
    "w": (226, 198, 150, 255),
}

# Iron's neutral greys, so the engine sits beside the gear wheel as the same metal.
IRON = {
    ".": (0, 0, 0, 0),
    "d": (56, 56, 61, 255),
    "m": (96, 96, 106, 255),
    "l": (142, 142, 152, 255),
    "h": (190, 190, 200, 255),
}

ELECTRIC_IRON = {
    ".": (0, 0, 0, 0),
    "d": (44, 52, 70, 255),
    "m": (78, 96, 128, 255),
    "l": (122, 148, 186, 255),
    "h": (176, 200, 232, 255),
}

COAL_BLACK = {
    ".": (0, 0, 0, 0),
    "d": (18, 18, 18, 255),
    "m": (44, 41, 39, 255),
    "l": (70, 66, 62, 255),
    "h": (102, 96, 90, 255),
}

DYNAMITE = {
    ".": (0, 0, 0, 0),
    "d": (108, 22, 22, 255),
    "m": (170, 40, 40, 255),
    "l": (210, 70, 60, 255),
    "h": (240, 140, 120, 255),
    "f": (214, 194, 130, 255),
}

# name -> (map, palette, the mod whose item it is)
ITEMS = {
    "steel_plate": (STEEL_PLATE, STEEL, "neoprogressivematerials"),
    "plastic_bar": (PLASTIC_BAR, PLASTIC, "neoprogressivematerials"),
    "sulfur": (SULFUR, YELLOW, "neoprogressivematerials"),
    "battery": (BATTERY, BLACK_AND_RED, "neoprogressivematerials"),
    "advanced_circuit": (ADVANCED_CIRCUIT, RED_BOARD, "neoprogressivematerials"),
    "engine_unit": (ENGINE_UNIT, IRON, "neoprogressivematerials"),
    # The same engine in the blue-grey of Factorio's electric one: one map, so the two read as
    # the same part with a different drive, which they are.
    "electric_engine_unit": (ENGINE_UNIT, ELECTRIC_IRON, "neoprogressivematerials"),
    "solid_fuel": (SOLID_FUEL, COAL_BLACK, "nauvis"),
    "explosives": (EXPLOSIVES, DYNAMITE, "nauvis_fluids"),
}


def rows(text):
    """The map as a list of rows, with the leading and trailing blank lines dropped."""
    lines = [line for line in text.strip("\n").split("\n")]
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
    """Every item side by side at 8x, for looking at without launching the game."""
    scale = 8
    sheet = Image.new("RGBA", (SIZE * scale * len(images), SIZE * scale), (0, 0, 0, 0))
    for index, image in enumerate(images):
        sheet.paste(image.resize((SIZE * scale, SIZE * scale), Image.NEAREST),
                    (index * SIZE * scale, 0))
    return sheet


def main():
    drawn = []
    for name, (text, palette, mod) in ITEMS.items():
        out = OUT[mod]
        if not os.path.isdir(out):
            os.makedirs(out)
        image = draw(text, palette)
        image.save(os.path.join(out, f"{name}.png"))
        print(f"wrote {mod}/{name}.png ({len(set(image.get_flattened_data())) - 1} colours plus transparent)")
        drawn.append(image)

    if "--preview" in sys.argv:
        path = os.path.join(HERE, "material-preview.png")
        preview(drawn).save(path)
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
