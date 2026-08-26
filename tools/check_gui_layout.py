"""Overlap check for the assembler screen, read out of the source rather than retyped.

The bug the screenshot showed - a progress bar drawn through a column of slots, and two
labels drawn through each other - is arithmetic, and arithmetic can be checked without eyes.
"""
import re
import sys
from pathlib import Path

ROOT = Path(r'C:\Users\yanni\IdeaProjects\ProjectNauvis\nauvis_machines\src\main\java\com\jaguarm\nauvismachines\machine\assembler')

def constant(path, name):
    text = (ROOT / path).read_text(encoding='utf-8')
    match = re.search(rf'\b{name}\s*=\s*(\d+)\s*;', text)
    if not match:
        sys.exit(f'could not find {name} in {path}')
    return int(match.group(1))

INPUT_X = constant('AssemblerMenu.java', 'INPUT_X')
INPUT_Y = constant('AssemblerMenu.java', 'INPUT_Y')
OUTPUT_X = constant('AssemblerMenu.java', 'OUTPUT_X')
OUTPUT_Y = constant('AssemblerMenu.java', 'OUTPUT_Y')

ARROW_X = constant('AssemblerScreen.java', 'ARROW_X')
ARROW_Y = constant('AssemblerScreen.java', 'ARROW_Y')
ARROW_W = constant('AssemblerScreen.java', 'ARROW_WIDTH')
ARROW_H = constant('AssemblerScreen.java', 'ARROW_HEIGHT')
STATUS_Y = constant('AssemblerScreen.java', 'STATUS_Y')
PANEL_W = constant('AssemblerScreen.java', 'PANEL_WIDTH')
PANEL_H = constant('AssemblerScreen.java', 'PANEL_HEIGHT')

FONT = 9                      # Minecraft's line height
TITLE_Y = 6                   # AbstractContainerScreen's default titleLabelY
INVENTORY_Y = PANEL_H - 94    # ... and its default inventoryLabelY

boxes = []

def well(name, x, y):
    """A slot's painted well: one pixel of edge around a 16x16 interior."""
    boxes.append((name, x - 1, y - 1, x + 17, y + 17))

for i in range(6):
    well(f'input{i}', INPUT_X + (i % 3) * 18, INPUT_Y + (i // 3) * 18)
well('output', OUTPUT_X, OUTPUT_Y)

for row in range(3):
    for col in range(9):
        well(f'inv{row}{col}', 8 + col * 18, 84 + row * 18)
for col in range(9):
    well(f'hotbar{col}', 8 + col * 18, 142)

boxes.append(('progress bar', ARROW_X, ARROW_Y, ARROW_X + ARROW_W, ARROW_Y + ARROW_H))
boxes.append(('title', 8, TITLE_Y, PANEL_W - 8, TITLE_Y + FONT))
boxes.append(('status line', 8, STATUS_Y, PANEL_W - 8, STATUS_Y + FONT))
boxes.append(('"Inventory"', 8, INVENTORY_Y, PANEL_W - 8, INVENTORY_Y + FONT))

def overlaps(a, b):
    return a[1] < b[3] and b[1] < a[3] and a[2] < b[4] and b[2] < a[4]

failures = []
for i, a in enumerate(boxes):
    for b in boxes[i + 1:]:
        # Slot wells are meant to tile edge to edge; everything else must stay clear.
        wells = a[0][:3] in ('inp', 'out', 'inv', 'hot') and b[0][:3] in ('inp', 'out', 'inv', 'hot')
        if wells:
            continue
        if overlaps(a, b):
            failures.append(f'  {a[0]} {a[1:]} overlaps {b[0]} {b[1:]}')

    if a[1] < 0 or a[2] < 0 or a[3] > PANEL_W or a[4] > PANEL_H:
        failures.append(f'  {a[0]} {a[1:]} falls outside the {PANEL_W}x{PANEL_H} panel')

print(f'panel {PANEL_W}x{PANEL_H}, {len(boxes)} boxes')
print(f'  ingredients  x {INPUT_X - 1}..{INPUT_X + 2 * 18 + 17}   y {INPUT_Y - 1}..{INPUT_Y + 18 + 17}')
print(f'  progress bar x {ARROW_X}..{ARROW_X + ARROW_W}   y {ARROW_Y}..{ARROW_Y + ARROW_H}')
print(f'  output well  x {OUTPUT_X - 1}..{OUTPUT_X + 17}  y {OUTPUT_Y - 1}..{OUTPUT_Y + 17}')
print(f'  status line  y {STATUS_Y}..{STATUS_Y + FONT}')
print(f'  "Inventory"  y {INVENTORY_Y}..{INVENTORY_Y + FONT}')

if failures:
    print('\nOVERLAPS:')
    print('\n'.join(failures))
    sys.exit(1)
print('\nno overlaps')
