"""Overlap check for the pack's machine screens, read out of the source rather than retyped.

The bug the first screenshot showed - a progress bar drawn through a column of slots, and two
labels drawn through each other - is arithmetic, and arithmetic can be checked without eyes.

Every screen in the pack is listed here. They share a shape: a 176x166 panel, the player's
inventory in the usual place, a title at the top and a status line above it. What differs is the
machine's own slots and bars, so that is all each entry has to describe.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

FONT = 9  # Minecraft's line height


class Screen:
    """One machine screen: where its constants live, and what boxes it draws."""

    def __init__(self, name, source_dir, menu, screen, slots, bars):
        self.name = name
        self.dir = ROOT / source_dir
        self.menu = menu
        self.screen = screen
        # (label, x-constant, y-constant, count, columns) - a slot is always 18x18 with its well.
        self.slots = slots
        # (label, x, y, width, height) constant names in the screen class.
        self.bars = bars

    def constant(self, path, name):
        text = (self.dir / path).read_text(encoding='utf-8')
        match = re.search(rf'\b{name}\s*=\s*(\d+)\s*;', text)
        if not match:
            sys.exit(f'{self.name}: could not find {name} in {path}')
        return int(match.group(1))

    def boxes(self):
        panel_w = self.constant(self.screen, 'PANEL_WIDTH')
        panel_h = self.constant(self.screen, 'PANEL_HEIGHT')
        status_y = self.constant(self.screen, 'STATUS_Y')

        boxes = []

        def well(label, x, y):
            """A slot's painted well: one pixel of edge around a 16x16 interior."""
            boxes.append((f'slot:{label}', x - 1, y - 1, x + 17, y + 17))

        for label, x_name, y_name, count, columns in self.slots:
            x0 = self.constant(self.menu, x_name)
            y0 = self.constant(self.menu, y_name)
            for i in range(count):
                well(f'{label}{i}', x0 + (i % columns) * 18, y0 + (i // columns) * 18)

        # The player's inventory and hotbar, in vanilla's places. Every screen has them.
        for row in range(3):
            for col in range(9):
                well(f'inv{row}{col}', 8 + col * 18, 84 + row * 18)
        for col in range(9):
            well(f'hotbar{col}', 8 + col * 18, 142)

        for label, x_name, y_name, w_name, h_name in self.bars:
            x = self.constant(self.screen, x_name)
            y = self.constant(self.screen, y_name)
            w = self.constant(self.screen, w_name)
            h = self.constant(self.screen, h_name)
            boxes.append((label, x, y, x + w, y + h))

        boxes.append(('title', 8, 6, panel_w - 8, 6 + FONT))
        boxes.append(('status line', 8, status_y, panel_w - 8, status_y + FONT))
        # AbstractContainerScreen's default inventoryLabelY.
        inventory_y = panel_h - 94
        boxes.append(('"Inventory"', 8, inventory_y, panel_w - 8, inventory_y + FONT))

        return boxes, panel_w, panel_h


SCREENS = [
    Screen(
        'assembler',
        'nauvis_machines/src/main/java/com/jaguarm/nauvismachines/machine/assembler',
        'AssemblerMenu.java', 'AssemblerScreen.java',
        slots=[('input', 'INPUT_X', 'INPUT_Y', 6, 3), ('output', 'OUTPUT_X', 'OUTPUT_Y', 1, 1)],
        bars=[('progress bar', 'ARROW_X', 'ARROW_Y', 'ARROW_WIDTH', 'ARROW_HEIGHT'),
              ('charge bar', 'CHARGE_X', 'CHARGE_Y', 'CHARGE_WIDTH', 'CHARGE_HEIGHT')],
    ),
    Screen(
        'boiler',
        'nauvis_power/src/main/java/com/jaguarm/nauvispower/generator',
        'BoilerMenu.java', 'BoilerScreen.java',
        slots=[('fuel', 'FUEL_X', 'FUEL_Y', 1, 1)],
        bars=[('flame', 'FLAME_X', 'FLAME_Y', 'FLAME_WIDTH', 'FLAME_HEIGHT'),
              ('steam bar', 'STEAM_X', 'STEAM_Y', 'STEAM_WIDTH', 'STEAM_HEIGHT')],
    ),
    Screen(
        'lab',
        'nauvis_research/src/main/java/com/jaguarm/nauvisresearch/lab',
        'LabMenu.java', 'LabScreen.java',
        slots=[('pack', 'PACKS_X', 'PACKS_Y', 6, 6)],
        bars=[('cycle bar', 'PROGRESS_X', 'PROGRESS_Y', 'PROGRESS_WIDTH', 'PROGRESS_HEIGHT'),
              ('charge bar', 'CHARGE_X', 'CHARGE_Y', 'CHARGE_WIDTH', 'CHARGE_HEIGHT')],
    ),
    Screen(
        'burner inserter',
        'nauvis_logistics/src/main/java/com/jaguarm/nauvislogistics/transport',
        'BurnerInserterMenu.java', 'BurnerInserterScreen.java',
        slots=[('fuel', 'FUEL_X', 'FUEL_Y', 1, 1)],
        bars=[('flame', 'FLAME_X', 'FLAME_Y', 'FLAME_WIDTH', 'FLAME_HEIGHT'),
              ('swing bar', 'SWING_X', 'SWING_Y', 'SWING_WIDTH', 'SWING_HEIGHT')],
    ),
]


def overlaps(a, b):
    return a[1] < b[3] and b[1] < a[3] and a[2] < b[4] and b[2] < a[4]


failures = []

for screen in SCREENS:
    boxes, panel_w, panel_h = screen.boxes()
    print(f'{screen.name}: panel {panel_w}x{panel_h}, {len(boxes)} boxes')

    for i, a in enumerate(boxes):
        for b in boxes[i + 1:]:
            # Slot wells are meant to tile edge to edge; everything else must stay clear.
            if a[0].startswith('slot:') and b[0].startswith('slot:'):
                continue
            if overlaps(a, b):
                failures.append(f'  {screen.name}: {a[0]} {a[1:]} overlaps {b[0]} {b[1:]}')

        if a[1] < 0 or a[2] < 0 or a[3] > panel_w or a[4] > panel_h:
            failures.append(
                f'  {screen.name}: {a[0]} {a[1:]} falls outside the {panel_w}x{panel_h} panel')

if failures:
    print('\nOVERLAPS:')
    print('\n'.join(failures))
    sys.exit(1)
print('\nno overlaps')
