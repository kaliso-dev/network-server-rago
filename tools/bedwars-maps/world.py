"""A whole world in memory: block ids and data in dense arrays, tile entities and entities."""
import numpy as np

import anvil


class World:
    def __init__(self, path):
        self.path = path
        self.level = anvil.level(path)
        # only the chunks that have blocks: a ripped map keeps thousands of empty ones around it
        chunks = [c for c in anvil.chunks(path)
                  if any(np.frombuffer(sec['Blocks'], dtype=np.uint8).any() for sec in c.get('Sections', []))]
        xs = [c['xPos'] for c in chunks]
        zs = [c['zPos'] for c in chunks]
        self.x0, self.z0 = min(xs) * 16, min(zs) * 16
        self.sx = (max(xs) - min(xs) + 1) * 16
        self.sz = (max(zs) - min(zs) + 1) * 16
        self.ids = np.zeros((self.sx, 256, self.sz), dtype=np.int16)
        self.data = np.zeros((self.sx, 256, self.sz), dtype=np.int8)
        self.tiles = []
        self.entities = []
        for c in chunks:
            self.tiles.extend(c.get('TileEntities', []))
            self.entities.extend(c.get('Entities', []))
            for bx, by, bz, ids, data in anvil.sections(c):
                ox, oz = bx - self.x0, bz - self.z0
                # ids[y, z, x] -> [x, y, z]
                self.ids[ox:ox + 16, by:by + 16, oz:oz + 16] = ids.transpose(2, 0, 1)
                self.data[ox:ox + 16, by:by + 16, oz:oz + 16] = data.transpose(2, 0, 1)
        nz = np.argwhere(self.ids != 0)
        self.lo = nz.min(axis=0)
        self.hi = nz.max(axis=0)

    def id(self, x, y, z):
        i, k = x - self.x0, z - self.z0
        if 0 <= i < self.sx and 0 <= k < self.sz and 0 <= y < 256:
            return int(self.ids[i, y, k])
        return 0

    def dat(self, x, y, z):
        i, k = x - self.x0, z - self.z0
        if 0 <= i < self.sx and 0 <= k < self.sz and 0 <= y < 256:
            return int(self.data[i, y, k])
        return 0

    def world_xz(self, i, k):
        return i + self.x0, k + self.z0
