"""Generate the military mod's textures from ASCII maps plus palettes.

The seven items - two guns, two magazines, the grenade, two armours - and the gun turret's two
block faces. The same rule as every other script here: a texture is a map plus a palette, so a
change to the map moves every texture drawn from it.

    python texture-workshop/make_military_textures.py            # write the PNGs
    python texture-workshop/make_military_textures.py --preview  # also write military-preview.png

The guns are drawn level with the muzzle to the left, the way Factorio's icons are, and
`models/item/gun.json` is the display that turns that to point forward in the hand. The
magazines are Factorio's curved ones, told apart by the stripe along the outer edge: yellow
for firearm rounds, red for piercing.

Legend, shared by the maps:
    .  transparent (items) / darkest recess (blocks)
    d  dark      m  mid      l  light     h  highlight
    a  accent, shadowed      b  accent, lit
    w  wood
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.join(HERE, os.pardir, "nauvis_military", "src", "main", "resources", "assets", "nauvis_military")
ITEMS = os.path.join(MOD, "textures", "item")
BLOCKS = os.path.join(MOD, "textures", "block")

# --- palettes ---------------------------------------------------------------

GUNMETAL = {
    "d": (38, 40, 44), "m": (74, 78, 84), "l": (112, 118, 126), "h": (160, 166, 174),
    "a": (96, 62, 30), "b": (150, 104, 56), "w": (120, 82, 44),
}
FIREARM = dict(GUNMETAL, a=(150, 118, 20), b=(232, 200, 44))
PIERCING = dict(GUNMETAL, a=(140, 30, 30), b=(222, 52, 48))
GRENADE = {
    "d": (34, 52, 30), "m": (72, 104, 60), "l": (110, 150, 92), "h": (156, 196, 136),
    "a": (60, 60, 64), "b": (140, 140, 148), "w": (120, 82, 44),
}
LIGHT_ARMOR = {
    "d": (64, 70, 80), "m": (120, 130, 144), "l": (168, 178, 192), "h": (214, 222, 232),
    "a": (96, 62, 30), "b": (150, 104, 56), "w": (120, 82, 44),
}
HEAVY_ARMOR = {
    "d": (30, 32, 38), "m": (62, 66, 76), "l": (98, 104, 116), "h": (140, 148, 160),
    "a": (120, 90, 30), "b": (190, 150, 60), "w": (120, 82, 44),
}
TURRET = {
    ".": (26, 28, 32), "d": (52, 56, 62), "m": (88, 94, 102), "l": (124, 132, 140), "h": (168, 176, 184),
    "a": (110, 78, 30), "b": (176, 130, 56), "w": (120, 82, 44),
}

# --- maps -------------------------------------------------------------------

# A slide over a frame, the barrel a step proud at the left, a raked grip with panels at the right,
# the trigger in its guard between; a front sight and the hammer spur on top.
PISTOL = """
................
................
..d...........d.
..dddddddddddd..
.dlhhhhhhhhhhhd.
dmlllllllllllmd.
dmmmmmmmmmmmmmd.
.ddddddmmmmmmmd.
.....d.d.dmaabd.
.....d.d.dmaabd.
.....dddddmaabd.
.........dmaabd.
..........dmaabd
..........dmaabd
..........dddddd
................
"""

# A barrel shroud at the left, a boxy receiver with the sights on top, the magazine hanging under
# the front half and the grip under the rear.
SUBMACHINE_GUN = """
................
................
.....d.......dd.
....dddddddddddd
dddmlhhhhhhhhhmd
dmmmmlmmmmmmmmmd
.ddddmmmmmmmmmmd
.....ddddddddddd
......dmmd.dmabd
......dmmd.dmabd
......dmmd.dmabd
......dlmd..dabd
......dlmd..dddd
......dlmd......
......dddd......
................
"""

# A curved magazine, feed lips at the top right, the stripe down the outer edge of the curve.
MAGAZINE = """
................
.........dddd...
........dlllld..
........dlmmmd..
.......dlmmmmd..
.......dlmmmmd..
......dlmmmmmd..
.....dlmmmmmd...
....dbmmmmmmd...
...dbbmmmmmd....
..dbbmmmmmd.....
.dabbmmmmd......
.daabbmmd.......
.daaabbd........
..dddddd........
................
"""

GRENADE_MAP = """
................
......aaa.......
.....abbba......
......aaa.......
......dmd.......
....ddmlmdd.....
...dmmlhlmmd....
...dmlhhhlmd....
...dmlhlhlmd....
...dmmlhlmmd....
...dmmmmmmmd....
...dmdmdmdmd....
....dmmmmmd.....
.....ddddd......
................
................
"""

ARMOR = """
................
..dddd....dddd..
.dmmmmdddmmmmd..
.dmlllmmmlllmd..
.dmlhlmmmlhlmd..
.dmmlmmmmmlmmd..
..ddmmmmmmmdd...
...dmmlllmmd....
...dmlhhhlmd....
...dmlhahlmd....
...dmlhhhlmd....
...dmmlllmmd....
...dmmmmmmmd....
...dddddddd.....
................
................
"""

TURRET_SIDE = """
dmddmdddmddmddmd
dhlllhllllhlllhd
dlm.mlmmmlmm.mld
dlmlmmmdmmmlmmld
dlm..........mld
dlmmmmmmmmmmmmld
dl.mmmmmmmmmm.ld
dlmmmmmmmmmmmmld
dl.mmmmmmmmmm.ld
dlmmmmmmmmmmmmld
dl.mmmmmmmmmm.ld
dlmmmmmmmmmmmmld
dlmmmmmmmmmmmmld
dabbbbbbbbbbbbad
ddhllmmmmmmlldd.
dddddddddddddddd
"""

TURRET_TOP = """
dddddddddddddddd
dmmmmmmmmmmmmmmd
dmllllllllllllmd
dml.ll.ll.ll.lmd
dmllllllllllllmd
dmlmmmmmmmmmmlmd
dmlmhhhhhhhhmlmd
dmlmhaaaaaahmlmd
dmlmhaaaaaahmlmd
dmlmhhhhhhhhmlmd
dmlmmmmmmmmmmlmd
dmllllllllllllmd
dml.ll.ll.ll.lmd
dmllllllllllllmd
dmmmmmmmmmmmmmmd
dddddddddddddddd
"""

ITEM_TEXTURES = {
    "pistol": (PISTOL, GUNMETAL),
    "submachine_gun": (SUBMACHINE_GUN, GUNMETAL),
    "firearm_magazine": (MAGAZINE, FIREARM),
    "piercing_rounds_magazine": (MAGAZINE, PIERCING),
    "grenade": (GRENADE_MAP, GRENADE),
    "light_armor": (ARMOR, LIGHT_ARMOR),
    "heavy_armor": (ARMOR, HEAVY_ARMOR),
}

BLOCK_TEXTURES = {
    "gun_turret_side": (TURRET_SIDE, TURRET),
    "gun_turret_top": (TURRET_TOP, TURRET),
}


def rows(text):
    lines = [line for line in text.strip("\n").splitlines()]
    assert len(lines) == 16, f"{len(lines)} rows"
    for line in lines:
        assert len(line) == 16, f"{len(line)} columns in {line!r}"
    return lines


def draw(text, palette, transparent):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    for y, line in enumerate(rows(text)):
        for x, char in enumerate(line):
            if char == "." and transparent:
                continue
            r, g, b = palette[char]
            pixels[x, y] = (r, g, b, 255)
    return image


def main():
    preview = "--preview" in sys.argv
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(BLOCKS, exist_ok=True)
    images = []
    for name, (text, palette) in ITEM_TEXTURES.items():
        image = draw(text, palette, transparent=True)
        image.save(os.path.join(ITEMS, f"{name}.png"))
        images.append(image)
    for name, (text, palette) in BLOCK_TEXTURES.items():
        image = draw(text, palette, transparent=False)
        image.save(os.path.join(BLOCKS, f"{name}.png"))
        images.append(image)
    print(f"wrote {len(ITEM_TEXTURES)} item and {len(BLOCK_TEXTURES)} block textures")

    if preview:
        sheet = Image.new("RGBA", (len(images) * 20 * 4, 20 * 4), (40, 40, 40, 255))
        for i, image in enumerate(images):
            sheet.paste(image.resize((64, 64), Image.NEAREST), (i * 80 + 8, 8))
        sheet.save(os.path.join(HERE, "military-preview.png"))
        print("wrote military-preview.png")


if __name__ == "__main__":
    main()
