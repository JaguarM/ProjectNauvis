"""Generate the pack's intermediate-product item textures from ASCII maps plus a palette.

The same file shape as `make_belt_textures.py` and Neo Progressive Automation's
`make_miner_textures.py`, for the same reason: a texture is a map plus a palette, so a
change to the map moves every item drawn from it and they cannot drift apart.

    python texture-workshop/make_material_textures.py            # write the PNGs
    python texture-workshop/make_material_textures.py --preview  # also write material-preview.png

Most of these go into `nauvis_materials` and two into other mods, because an item lives in the mod the
mapping gives it: solid fuel is the pack mod's and explosives are the fluids mod's. Only the
items with a map below are written; the iron gear wheel was drawn by hand before this file
existed and is left alone rather than redrawn from a guess at its map. The three circuits are
one board map in Factorio's green, red and blue, so they read as one family.

Each map is Factorio's icon at sixteen pixels: the coil of copper cable, the crystal of sulfur,
the two-terminal battery, the wrench crossed with a screwdriver, the ring and propeller of the
robot frame, the honeycomb of the low density structure.

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

# Where each mod keeps its item textures.
OUT = {
    "nauvis_materials": os.path.join(
        REPO, "nauvis_materials", "src", "main", "resources", "assets", "nauvis_materials", "textures", "item"),
    "nauvis": os.path.join(REPO, "nauvis", "src", "main", "resources", "assets", "nauvis", "textures", "item"),
    "nauvis_fluids": os.path.join(
        REPO, "nauvis_fluids", "src", "main", "resources", "assets", "nauvis_fluids", "textures", "item"),
    "nauvis_machines": os.path.join(
        REPO, "nauvis_machines", "src", "main", "resources", "assets", "nauvis_machines", "textures", "item"),
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

# A cluster of three crystals radiating from one root, each lit down its left face: the way
# vanilla's amethyst cluster reads as crystals and one lump does not.
SULFUR = """
................
................
........d.......
.......dhd...d..
.......dhld.dhd.
...d...dhlddhmd.
..dhd..dhlddhmd.
..dhmd.dhldhmmd.
...dhmddhlhmmd..
....dhmdhlmmd...
.....dhmhlmd....
......dddmd.....
.......ddd......
................
................
................
"""

# A coil of wire lying flat: two rings and the hole in the middle, the end of the wire trailing
# out at the bottom right.
COPPER_CABLE = """
................
................
.....dddddd.....
...ddhhhhhhdd...
..dhhmddddmhhd..
.dhmdd....ddmhd.
.dhd..dddd..dmd.
.dmd.dhhhhd.dmd.
.dmd.dmddmd.dmd.
.dmd.dddddd.dmd.
.dmdd......ddmd.
..dmmdd..ddmmd..
...ddmmmmmmddd..
.....dddddd..d..
..............d.
................
"""

# A cylinder standing up with a red and a blue terminal on top: the two tones down the left are
# the round of it.
BATTERY = """
................
.....rr..bb.....
....drrddbbd....
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

# A board with three gold traces running in from the left edge and stepping down, pins along
# the bottom edge: one map for all three circuits.
CIRCUIT_BOARD = """
................
..dddddddddddd..
..dmmmmmmmmmmd..
..dwwwwwmmmmmd..
..dmmmmmwwwwmd..
..dmmmmmmmmmmd..
..dwwwwwwwmmmd..
..dmmmmmmmwwmd..
..dmmmmmmmmmmd..
..dwwwmmmmmmmd..
..dmmmwwwwwwmd..
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

# A motor lying on its side: red bands at both ends of the can, the shaft out of the left, feet
# under it.
ELECTRIC_ENGINE_UNIT = """
................
................
................
....dddddddddd..
...drhhhhhhhhrd.
...drllllllllrd.
.dddrllllllllrd.
.dmmrmmmmmmmmrd.
.dddrmmmmmmmmrd.
...drmmmmmmmmrd.
...drddddddddrd.
....dddddddddd..
......dd..dd....
.....dddddddd...
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

# A panel drilled through: Factorio's low density structure is a honeycomb, and a grid of dark
# holes in a bright plate is how a honeycomb reads at sixteen pixels.
LOW_DENSITY_STRUCTURE = """
................
.dddddddddddddd.
.dhhhhhhhhhhhhd.
.dhlllllllllllld
.dhldmldmldmlld.
.dhlmmlmmlmmlld.
.dhllllllllllld.
.dhldmldmldmlld.
.dhlmmlmmlmmlld.
.dhllllllllllld.
.dhldmldmldmlld.
.dhlmmlmmlmmlld.
.dhllllllllllld.
.dhmmmmmmmmmmmd.
.dddddddddddddd.
................
"""

# A canister standing up: a dark cap and a bright band round the middle, in the orange that
# nothing else in an inventory is.
ROCKET_FUEL = """
................
......dddd......
.....dmmmmd.....
....ddddddd.....
....dllmmmd.....
....dllmmmd.....
....dllmmmd.....
....dhhhhhd.....
....dhhhhhd.....
....dllmmmd.....
....dllmmmd.....
....dllmmmd.....
....dllmmmd.....
....dddddd......
................
................
"""

# A square unit with a window in it and one bright lamp: the thing that steers a rocket, and
# not another circuit board.

# Two iron rods, leaning the way a stick does, one lit along its length.
IRON_STICK = """
................
.............dd.
............dhd.
...........dhmd.
..........dhmd..
.........dhmd...
........dhmd....
.......dhmd.dd..
......dhmd.dhd..
.....dhmd.dhmd..
....dhmd.dhmd...
...dhmd.dhmd....
..dhmd.dhmd.....
..dmd.dhmd......
..dd.dhmd.......
.....ddd........
"""

# A wrench crossed with a screwdriver: the wrench's open jaw at the top left and its shaft to the
# bottom right, the screwdriver's blade at the top right and its yellow handle at the bottom left,
# in front.
REPAIR_PACK = """
.ddd............
dlhld.........dd
dl.dld.......dhd
ddd.dld.....dhd.
..dd.dld...dhd..
...dddlld.dhd...
.....dllldhd....
......ddkkd.....
.....dkyykdd....
....dkyykdlld...
...dkyyykddlld..
..dkyyykd..dlld.
.dkyyykd....dlld
.dkyykd......ddd
.ddkkd..........
..ddd...........
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

# The battery's body is near black, so its red and blue terminals are what the eye finds.
BLACK_AND_RED = {
    ".": (0, 0, 0, 0),
    "d": (38, 38, 43, 255),
    "m": (68, 68, 76, 255),
    "l": (108, 108, 118, 255),
    "h": (148, 148, 158, 255),
    "r": (198, 50, 40, 255),
    "b": (60, 110, 210, 255),
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
# The electronic circuit: the same board in Factorio's green. It was drawn by hand before this
# file existed and looked like a different family from the two circuits above it.
GREEN_BOARD = {
    ".": (0, 0, 0, 0),
    "d": (26, 78, 34, 255),
    "m": (46, 128, 58, 255),
    "l": (84, 176, 92, 255),
    "h": (150, 224, 150, 255),
    "w": (226, 198, 150, 255),
}

# The processing unit: the advanced circuit's board in Factorio's blue.
BLUE_BOARD = {
    ".": (0, 0, 0, 0),
    "d": (22, 40, 92, 255),
    "m": (36, 70, 152, 255),
    "l": (62, 112, 202, 255),
    "h": (120, 170, 240, 255),
    "w": (226, 198, 150, 255),
}

IRON = {
    ".": (0, 0, 0, 0),
    "d": (56, 56, 61, 255),
    "m": (96, 96, 106, 255),
    "l": (142, 142, 152, 255),
    "h": (190, 190, 200, 255),
}

# The electric engine's blue-grey steel, with the red of its end bands.
ELECTRIC_IRON = {
    ".": (0, 0, 0, 0),
    "d": (44, 52, 70, 255),
    "m": (78, 96, 128, 255),
    "l": (122, 148, 186, 255),
    "h": (176, 200, 232, 255),
    "r": (198, 50, 40, 255),
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

# Copper's oranges, off the copper block.
COPPER = {
    ".": (0, 0, 0, 0),
    "d": (112, 60, 36, 255),
    "m": (168, 96, 62, 255),
    "l": (204, 128, 84, 255),
    "h": (236, 170, 118, 255),
}

# A canister of something that burns hard: orange, with a near-black cap.
ROCKET_ORANGE = {
    ".": (0, 0, 0, 0),
    "d": (60, 36, 24, 255),
    "m": (206, 98, 30, 255),
    "l": (236, 138, 52, 255),
    "h": (255, 200, 110, 255),
}

# The warm grey of the low density structure's plates, a shade browner than steel.
PLATE_GREY = {
    ".": (0, 0, 0, 0),
    "d": (58, 54, 50, 255),
    "m": (118, 110, 100, 255),
    "l": (168, 158, 146, 255),
    "h": (208, 200, 188, 255),
}

# Steel tools with a yellow handle.
TOOLS = {
    ".": (0, 0, 0, 0),
    "d": (56, 60, 68, 255),
    "m": (120, 126, 138, 255),
    "l": (176, 182, 194, 255),
    "h": (226, 230, 238, 255),
    "y": (226, 180, 40, 255),
    "k": (150, 112, 20, 255),
}

# A grey unit with a blue window and one green lamp.
CONTROL_UNIT = {
    ".": (0, 0, 0, 0),
    "d": (48, 52, 60, 255),
    "m": (110, 116, 128, 255),
    "l": (70, 120, 200, 255),
    "h": (130, 190, 250, 255),
    "g": (110, 230, 110, 255),
}

# A ring with a three-bladed propeller in it and two legs below: the robot before it is a robot.
FLYING_ROBOT_FRAME = """
................
.....dddddd.....
...ddlbbbbldd...
..dlb..kk..bld..
.dlb...kk...bld.
.dl....kk....ld.
.dl...kkkk...ld.
.dl..kkkkkk..ld.
.dm.kkk..kkk.md.
.dm.kk....kk.md.
..dm........md..
...ddmmmmmmdd...
.....dddddd.....
....dm....md....
....dm....md....
....dd....dd....
"""

# Grey steel, the ring lit cyan along its top, near-black blades.
FRAME = {
    ".": (0, 0, 0, 0),
    "d": (48, 52, 60, 255),
    "m": (110, 116, 128, 255),
    "l": (160, 166, 176, 255),
    "b": (120, 200, 240, 255),
    "k": (30, 32, 38, 255),
}

# name -> (map, palette, the mod whose item it is)
ITEMS = {
    "steel_plate": (STEEL_PLATE, STEEL, "nauvis_materials"),
    "plastic_bar": (PLASTIC_BAR, PLASTIC, "nauvis_materials"),
    "sulfur": (SULFUR, YELLOW, "nauvis_materials"),
    "battery": (BATTERY, BLACK_AND_RED, "nauvis_materials"),
    "electronic_circuit": (CIRCUIT_BOARD, GREEN_BOARD, "nauvis_materials"),
    "advanced_circuit": (CIRCUIT_BOARD, RED_BOARD, "nauvis_materials"),
    "processing_unit": (CIRCUIT_BOARD, BLUE_BOARD, "nauvis_materials"),
    "engine_unit": (ENGINE_UNIT, IRON, "nauvis_materials"),
    "electric_engine_unit": (ELECTRIC_ENGINE_UNIT, ELECTRIC_IRON, "nauvis_materials"),
    "copper_cable": (COPPER_CABLE, COPPER, "nauvis_materials"),
    "low_density_structure": (LOW_DENSITY_STRUCTURE, PLATE_GREY, "nauvis_materials"),
    "rocket_fuel": (ROCKET_FUEL, ROCKET_ORANGE, "nauvis_materials"),
    "iron_stick": (IRON_STICK, IRON, "nauvis_materials"),
    "flying_robot_frame": (FLYING_ROBOT_FRAME, FRAME, "nauvis_materials"),
    "solid_fuel": (SOLID_FUEL, COAL_BLACK, "nauvis"),
    "repair_pack": (REPAIR_PACK, TOOLS, "nauvis_machines"),
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
