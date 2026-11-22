"""Paints 64x64 Minecraft skins from rows of characters, with a soft light from above and a fabric grain."""
import random
from PIL import Image

# box origins (base, overlay) and sizes (w, h, d)
PARTS = {
    'head': ((0, 0), (32, 0), (8, 8, 8)),
    'body': ((16, 16), (16, 32), (8, 12, 4)),
    'rarm': ((40, 16), (40, 32), (4, 12, 4)),
    'larm': ((32, 48), (48, 48), (4, 12, 4)),
    'rleg': ((0, 16), (0, 32), (4, 12, 4)),
    'lleg': ((16, 48), (0, 48), (4, 12, 4)),
}


def faces(origin, size):
    (ox, oy), (w, h, d) = origin, size
    return {
        'top': (ox + d, oy, w, d), 'bottom': (ox + d + w, oy, w, d),
        'right': (ox, oy + d, d, h), 'front': (ox + d, oy + d, w, h),
        'left': (ox + d + w, oy + d, d, h), 'back': (ox + 2 * d + w, oy + d, w, h),
    }


def hexc(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def scale(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


class Skin:
    def __init__(self, palette, seed=1, grain=0.045, shade=0.10):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.palette = {k: (hexc(v) if isinstance(v, str) else v) for k, v in palette.items()}
        self.rng = random.Random(seed)
        self.grain = grain
        self.shade = shade
        self.smooth = set('WKEeo')  # eyes, pure colours: no grain

    def colour(self, ch, v, h):
        c = self.palette[ch]
        f = 1.0
        if ch not in self.smooth:
            f += self.rng.uniform(-self.grain, self.grain)
            # light from above: a soft gradient over the face
            if h > 1:
                f += self.shade * (0.5 - v / (h - 1)) * 0.6
        return scale(c, f) + (255,)

    def put(self, part, face, rows, overlay=False):
        """Paints one face from rows of characters ('.' leaves it transparent, '_' keeps it)."""
        origin, over, size = PARTS[part]
        x, y, w, h = faces(over if overlay else origin, size)[face]
        assert len(rows) == h and all(len(r) == w for r in rows), (part, face, overlay, [len(r) for r in rows])
        for v, row in enumerate(rows):
            for u, ch in enumerate(row):
                if ch == '_':
                    continue
                if ch == '.':
                    self.img.putpixel((x + u, y + v), (0, 0, 0, 0))
                else:
                    self.img.putpixel((x + u, y + v), self.colour(ch, v, h))

    def fill(self, part, face, ch, overlay=False):
        origin, over, size = PARTS[part]
        _, _, w, h = faces(over if overlay else origin, size)[face]
        self.put(part, face, [ch * w] * h, overlay)

    def profile(self, part, rows, overlay=False, skip=()):
        """The same row of characters around a limb or the body: rows[v] is a 1-char or full-width row."""
        origin, over, size = PARTS[part]
        for face, (x, y, w, h) in faces(over if overlay else origin, size).items():
            if face in ('top', 'bottom') or face in skip:
                continue
            self.put(part, face, [(r * w if len(r) == 1 else r[:w].ljust(w, r[-1])) for r in rows], overlay)

    def save(self, path):
        self.img.save(path)


def front_view(img, back=False):
    """16x32 front (or back) of a 64x64 skin, base then overlay."""
    out = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    def part(name, at, flip=False):
        for i, origin in enumerate(PARTS[name][:2]):
            f = faces(origin, PARTS[name][2])['back' if back else 'front']
            piece = img.crop((f[0], f[1], f[0] + f[2], f[1] + f[3]))
            out.alpha_composite(piece, at)
    if back:
        part('head', (4, 0)); part('body', (4, 8)); part('larm', (0, 8)); part('rarm', (12, 8))
        part('lleg', (4, 20)); part('rleg', (8, 20))
    else:
        part('head', (4, 0)); part('body', (4, 8)); part('rarm', (0, 8)); part('larm', (12, 8))
        part('rleg', (4, 20)); part('lleg', (8, 20))
    return out


def side_view(img):
    """8x32 right side: head right, right arm right, right leg right."""
    out = Image.new('RGBA', (8, 32), (0, 0, 0, 0))
    for name, at in (('head', (0, 0)), ('rleg', (2, 20)), ('rarm', (2, 8))):
        for origin in PARTS[name][:2]:
            f = faces(origin, PARTS[name][2])['right']
            out.alpha_composite(img.crop((f[0], f[1], f[0] + f[2], f[1] + f[3])), at)
    return out


def sheet(paths, out_path, zoom=7):
    imgs = [Image.open(p).convert('RGBA') for p in paths]
    cell = (16 + 16 + 8 + 6) * zoom
    out = Image.new('RGBA', (cell * len(imgs), 34 * zoom), (176, 196, 222, 255))
    for i, img in enumerate(imgs):
        x = i * cell + zoom
        out.alpha_composite(front_view(img).resize((16 * zoom, 32 * zoom), Image.NEAREST), (x, zoom))
        out.alpha_composite(front_view(img, back=True).resize((16 * zoom, 32 * zoom), Image.NEAREST), (x + 17 * zoom, zoom))
        out.alpha_composite(side_view(img).resize((8 * zoom, 32 * zoom), Image.NEAREST), (x + 34 * zoom, zoom))
    out.save(out_path)
