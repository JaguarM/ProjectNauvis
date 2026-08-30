"""Generate the mining drill block textures from ASCII maps plus a palette per tier.

One silhouette, one palette per drill -- the family rule from README.md. Edit a map
below, re-run, and both drills update together.

Came across from Neo Progressive Automation with the drills when nauvis_mining was forked
out of it; the maps and the palettes are unchanged, the ids and the output path are not.

    python texture-workshop/make_miner_textures.py           # write the PNGs
    python texture-workshop/make_miner_textures.py --preview  # also write preview.png

Legend for the maps:
    .  darkest recess / outline      a  accent, shadowed
    d  dark material                 b  accent, lit      (the burner trim band)
    m  mid material (the base tone)  #  firebox, outer   (glows when lit)
    l  light material                *  firebox, core    (glows brighter)
    h  highlight
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(
    HERE, os.pardir, "nauvis_mining", "src", "main", "resources", "assets",
    "nauvis_mining", "textures", "block",
)

# --- maps ------------------------------------------------------------------
# The front: a burner firebox up top, one bright plate seam, a drill bit below.
# Three ideas, the way furnace_front gets by on three.
FRONT = """
dmddmdddmddmddmd
dhlllhllllhlllhd
dl............ld
dl.#********#.ld
dl............ld
dl.##******##.ld
dl............ld
dmllllllllllllmd
dabbbbbbbbbbbbad
ddhllmmmmmmll.dd
dmd.hlmmmmd..dmd
ddd.h.....d..ddd
dddd.hlmmd..dddd
dmdd.h......ddmd
ddddd.hlmd.ddddd
dddddd.hl.dddddd
"""

# The side: riveted plate, louvred vent, and the same trim band at the same row
# as the front's, so the yellow wraps the block instead of stopping at a corner.
SIDE = """
dmddmdddmddmddmd
dhlllhllllhlllhd
dlm.mlmmmlmm.mld
dlmlmmmdmmmlmmld
dlm..........mld
dlmmlmmmmmmlmmld
dlm..........mld
dmllllllllllllmd
dabbbbbbbbbbbbad
dlmmlmmmdmmmmmld
dlm.mmmlmmmm.mld
dlmmmdmmmmlmmmld
dlmlmmmmdmmmmmld
dlmmmmlmmmmdmmld
ddmmdmmmmmlmmmdd
dmddmdddmddmddmd
"""

# The top: the fuel hatch you would drop coal into, bolted down.
TOP = """
dmddmdddmddmddmd
dlmmlmmmdmmmmmld
dlm.mmmlmmmm.mld
dlmmmdmmmmlmmmld
dlmm........mmld
dlm.abbbbbba.mld
dlm.ab....ba.mld
dlm.ab....ba.mld
dlm.abbbbbba.mld
dlmm........mmld
dlmlmmmmdmmmmmld
dlm.mmmlmmmm.mld
dlmmmmlmmmdmmmld
dlmmdmmmmmlmmmld
ddmmmmlmmmmmmmdd
dmddmdddmddmddmd
"""

# --- palettes --------------------------------------------------------------
# Five material tones per drill, sampled off the vanilla block it is made of
# (cobblestone, iron_block) and extended one step darker where vanilla has no tone
# dark enough to read as a recess.
#
# Two drills, not four tiers: the burner you start with and the electric you graduate
# to, as in Factorio. The old wood and diamond palettes are gone with their tiers.
TIERS = {
    "burner_mining_drill":   dict(K="#2f2f2f", D="#525252", M="#6e6d6d", L="#888788", H="#b5b5b5"),
    "electric_mining_drill": dict(K="#3c3b3b", D="#7d7d7d", M="#b1b0b0", L="#d6d6d6", H="#f2f2f2"),
}

# Shared across both drills -- this is what makes them one machine family.
ACCENT_DARK = "#9c6a16"
ACCENT_LIT = "#e8ae2b"
FIREBOX_OUTER_COLD = "#191919"
FIREBOX_CORE_COLD = "#141414"
FIREBOX_OUTER_HOT = "#ff8f00"   # vanilla furnace_front_on's own two fire tones
FIREBOX_CORE_HOT = "#ffd800"


def rgba(hex_colour):
    h = hex_colour.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def parse(text):
    rows = [r for r in text.strip().splitlines()]
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError("maps must be 16x16, got %s" % [len(r) for r in rows])
    return rows


def render(rows, tone, lit):
    colours = {
        ".": tone["K"], "d": tone["D"], "m": tone["M"],
        "l": tone["L"], "h": tone["H"],
        "a": ACCENT_DARK, "b": ACCENT_LIT,
        "#": FIREBOX_OUTER_HOT if lit else FIREBOX_OUTER_COLD,
        "*": FIREBOX_CORE_HOT if lit else FIREBOX_CORE_COLD,
    }
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            px[x, y] = rgba(colours[ch])
    return img


def main():
    front, side, top = parse(FRONT), parse(SIDE), parse(TOP)
    os.makedirs(OUT, exist_ok=True)

    written = []
    for tier, tone in TIERS.items():
        for name, img in (
            ("front", render(front, tone, lit=False)),
            ("front_on", render(front, tone, lit=True)),
            ("side", render(side, tone, lit=False)),
            ("top", render(top, tone, lit=False)),
        ):
            path = os.path.join(OUT, "%s_%s.png" % (tier, name))
            img.save(path)
            written.append((tier, name, img))

    print("wrote %d textures to %s" % (len(written), os.path.normpath(OUT)))

    if "--preview" in sys.argv:
        scale, pad, cols = 8, 6, 4
        cell = 16 * scale + pad
        sheet = Image.new("RGBA", (cols * cell + pad, len(TIERS) * cell + pad),
                          (32, 32, 32, 255))
        for i, (tier, name, img) in enumerate(written):
            r, c = divmod(i, cols)
            sheet.paste(img.resize((16 * scale,) * 2, Image.NEAREST),
                        (pad + c * cell, pad + r * cell))
        path = os.path.join(HERE, "preview.png")
        sheet.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
