"""Draws a block model to a PNG, so that a machine can be looked at without booting the game.

Nothing in this repo could see a model until now: `check_models.py` proves every reference resolves,
the gametests prove every collision box, and whether the thing *looks* right was a client boot and a
person. That is still true for lighting and for how a machine sits in a world - but a model with a
hole in it, a face on the wrong side, or a texture that is not what it was meant to be shows up in
a rendering of the model alone, and this is that rendering.

    python tools/render_model.py nauvis_fluids:block/pumpjack_inventory out.png
    python tools/render_model.py nauvis_power:block/boiler_inventory out.png --size 512
    python tools/render_model.py nauvis_fluids:block/pumpjack_corner out.png --view side

It reads the model the way the game does - the generated assets first, then the vanilla client jar
for parents and textures - and draws its elements as textured boxes from the item slot's angle:
vanilla's `block/block` GUI display, rotated 30 degrees down and 225 round, which is the angle the
screenshot that found the anvil-top holes was taken at. Every face is drawn with a depth buffer and
Minecraft's own directional shading, and a texture pixel with an alpha under half is a hole, which is
what the game shows for a cutout and what a player sees either way.

`--view side` looks straight at the north face, for checking one cell's geometry, and `--view top`
straight down.

Needs Pillow and numpy, which the build does not, so this is a tool for a person and not a check.
"""
import argparse
import json
import math
import sys
import zipfile
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent

MODS = ['nauvis', 'nauvis_materials', 'nauvis_machines', 'nauvis_logistics', 'nauvis_fluids', 'nauvis_power',
        'nauvis_research', 'nauvis_mining', 'nauvis_military', 'nauvis_rocket']
ASSET_ROOTS = ['src/generated/client/assets', 'src/main/resources/assets']

# Minecraft's directional shading, from its block renderer: the top is full, the bottom half.
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}


class Assets:
    def __init__(self):
        self.files = {}
        for mod in MODS:
            for root in ASSET_ROOTS:
                base = ROOT / mod / root
                if not base.is_dir():
                    continue
                for path in base.rglob('*'):
                    if path.is_file():
                        namespace = path.relative_to(base).parts[0]
                        rest = path.relative_to(base / namespace).as_posix()
                        self.files[f'{namespace}:{rest}'] = path
        version = None
        for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines():
            if line.startswith('minecraft_version='):
                version = line.split('=', 1)[1].strip()
        jar = Path.home() / '.gradle' / 'caches' / 'neoformruntime' / 'artifacts' / f'minecraft_{version}_client.jar'
        self.jar = zipfile.ZipFile(jar) if jar.is_file() else None
        self.names = set(self.jar.namelist()) if self.jar else set()

    def read(self, namespace, path):
        key = f'{namespace}:{path}'
        if key in self.files:
            return self.files[key].read_bytes()
        entry = f'assets/{namespace}/{path}'
        if self.jar and entry in self.names:
            return self.jar.read(entry)
        raise FileNotFoundError(key)

    def model(self, identifier):
        namespace, path = split(identifier)
        return json.loads(self.read(namespace, f'models/{path}.json').decode('utf-8'))

    def texture(self, identifier):
        namespace, path = split(identifier)
        image = Image.open(BytesIO(self.read(namespace, f'textures/{path}.png'))).convert('RGBA')
        # Animated textures are a vertical strip; the first frame is the one to draw.
        if image.height > image.width:
            image = image.crop((0, 0, image.width, image.width))
        return np.asarray(image)


def split(identifier):
    return tuple(identifier.split(':', 1)) if ':' in identifier else ('minecraft', identifier)


def resolve(assets, identifier):
    """The model with its parent chain folded in: the first elements found, and every texture."""
    textures = {}
    elements = None
    display = None
    seen = []
    while identifier and identifier not in seen:
        seen.append(identifier)
        model = assets.model(identifier)
        for slot, value in model.get('textures', {}).items():
            textures.setdefault(slot, value)
        if elements is None and 'elements' in model:
            elements = model['elements']
        if display is None and 'display' in model:
            display = model['display']
        identifier = model.get('parent')
        if identifier and identifier.startswith('builtin/'):
            break

    def texture(ref):
        for _ in range(16):
            if not ref.startswith('#'):
                return ref
            ref = textures.get(ref[1:], ref)
            if ref.startswith('#') and ref[1:] not in textures:
                raise KeyError(f'texture slot {ref} is unbound')
        raise KeyError('texture reference loops')

    return elements or [], texture


def default_uv(box, face):
    """Minecraft's rule for a face with no uv: the box's own footprint on that face."""
    x1, y1, z1, x2, y2, z2 = box
    return {
        'up': (x1, z1, x2, z2),
        'down': (x1, 16 - z2, x2, 16 - z1),
        'north': (16 - x2, 16 - y2, 16 - x1, 16 - y1),
        'south': (x1, 16 - y2, x2, 16 - y1),
        'west': (z1, 16 - y2, z2, 16 - y1),
        'east': (16 - z2, 16 - y2, 16 - z1, 16 - y1),
    }[face]


def corners(box, face):
    """The face's four corners in model space, anticlockwise seen from outside, with uv corners."""
    x1, y1, z1, x2, y2, z2 = box
    if face == 'up':
        return [(x1, y2, z1), (x1, y2, z2), (x2, y2, z2), (x2, y2, z1)], [(0, 0), (0, 1), (1, 1), (1, 0)]
    if face == 'down':
        return [(x1, y1, z2), (x1, y1, z1), (x2, y1, z1), (x2, y1, z2)], [(0, 0), (0, 1), (1, 1), (1, 0)]
    if face == 'north':
        return [(x2, y2, z1), (x2, y1, z1), (x1, y1, z1), (x1, y2, z1)], [(0, 0), (0, 1), (1, 1), (1, 0)]
    if face == 'south':
        return [(x1, y2, z2), (x1, y1, z2), (x2, y1, z2), (x2, y2, z2)], [(0, 0), (0, 1), (1, 1), (1, 0)]
    if face == 'west':
        return [(x1, y2, z1), (x1, y1, z1), (x1, y1, z2), (x1, y2, z2)], [(0, 0), (0, 1), (1, 1), (1, 0)]
    if face == 'east':
        return [(x2, y2, z2), (x2, y1, z2), (x2, y1, z1), (x2, y2, z1)], [(0, 0), (0, 1), (1, 1), (1, 0)]
    raise ValueError(face)


def rotation(view):
    """World to camera. The item slot's angle, or straight on for checking one cell."""
    if view == 'gui':
        pitch, yaw = math.radians(30), math.radians(225)
    elif view == 'side':
        pitch, yaw = 0.0, math.radians(180)
    elif view == 'top':
        pitch, yaw = math.radians(90), math.radians(180)
    else:
        raise ValueError(view)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    return rx @ ry


def render(assets, identifier, size, view):
    elements, texture_of = resolve(assets, identifier)
    if not elements:
        sys.exit(f'{identifier} has no elements to draw')

    rot = rotation(view)
    # Model space is 0..16 about 8,8,8; the camera looks down -z after rotation. Scale so a full
    # block's diagonal fits with a margin.
    scale = size / (16 * math.sqrt(3) * 1.15)
    centre = np.array([8.0, 8.0, 8.0])

    colour = np.zeros((size, size, 4), dtype=np.float32)
    depth = np.full((size, size), -np.inf, dtype=np.float32)
    textures = {}

    for element in elements:
        box = [*element['from'], *element['to']]
        for face, spec in element.get('faces', {}).items():
            ref = texture_of(spec['texture'])
            if ref not in textures:
                textures[ref] = assets.texture(ref)
            image = textures[ref]
            pts, uvc = corners(box, face)
            uv = spec.get('uv') or default_uv(box, face)
            # Vertices to camera space, then to pixels. Camera x right, y up, z towards viewer.
            cam = np.array([rot @ (np.array(p, dtype=float) - centre) for p in pts])
            # Back faces: skip when the face points away from the camera.
            n = np.cross(cam[1] - cam[0], cam[2] - cam[0])
            if n[2] <= 0:
                continue
            sx = size / 2 + cam[:, 0] * scale
            sy = size / 2 - cam[:, 1] * scale
            sz = cam[:, 2]
            shade = SHADE[face]
            # Two triangles per quad.
            for tri in ((0, 1, 2), (0, 2, 3)):
                raster(colour, depth, image, uv, shade,
                       [(sx[i], sy[i], sz[i]) for i in tri], [uvc[i] for i in tri])

    out = np.clip(colour, 0, 255).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def raster(colour, depth, image, uv, shade, verts, uvs):
    """One textured triangle, depth tested, nearest-neighbour sampled."""
    size = colour.shape[0]
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = verts
    minx, maxx = max(int(min(x0, x1, x2)), 0), min(int(max(x0, x1, x2)) + 1, size - 1)
    miny, maxy = max(int(min(y0, y1, y2)), 0), min(int(max(y0, y1, y2)) + 1, size - 1)
    if minx > maxx or miny > maxy:
        return
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
    w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
    w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
    w2 = 1 - w0 - w1
    inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
    if not inside.any():
        return
    z = w0 * z0 + w1 * z1 + w2 * z2
    (u0, v0), (u1, v1), (u2, v2) = uvs
    fu = w0 * u0 + w1 * u1 + w2 * u2
    fv = w0 * v0 + w1 * v1 + w2 * v2
    tu = uv[0] + fu * (uv[2] - uv[0])
    tv = uv[1] + fv * (uv[3] - uv[1])
    th, tw = image.shape[0], image.shape[1]
    px = np.clip((tu / 16 * tw).astype(int), 0, tw - 1)
    py = np.clip((tv / 16 * th).astype(int), 0, th - 1)
    sample = image[py, px].astype(np.float32)
    # A texture pixel with an alpha under half is a hole - the thing this tool exists to show.
    inside &= sample[..., 3] >= 128
    current = depth[miny:maxy + 1, minx:maxx + 1]
    win = inside & (z > current)
    if not win.any():
        return
    shaded = sample.copy()
    shaded[..., :3] *= shade
    shaded[..., 3] = 255
    target = colour[miny:maxy + 1, minx:maxx + 1]
    target[win] = shaded[win]
    current[win] = z[win]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('model', help='a model id, e.g. nauvis_fluids:block/pumpjack_inventory')
    parser.add_argument('out', help='where to write the PNG')
    parser.add_argument('--size', type=int, default=256)
    parser.add_argument('--view', choices=['gui', 'side', 'top'], default='gui')
    args = parser.parse_args()

    assets = Assets()
    if assets.jar is None:
        print('no vanilla client jar found; vanilla textures and parents will be missing', file=sys.stderr)
    image = render(assets, args.model, args.size, args.view)
    # On a light grey, so a hole reads as a hole rather than as black.
    background = Image.new('RGBA', image.size, (200, 200, 200, 255))
    background.alpha_composite(image)
    background.save(args.out)
    print(f'wrote {args.out}')


if __name__ == '__main__':
    main()
