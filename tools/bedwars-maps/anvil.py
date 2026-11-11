"""Minimal reader for Minecraft 1.8 Anvil worlds: NBT, level.dat, region files, chunk sections."""
import gzip
import os
import struct
import zlib

import numpy as np


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        b = self.d[self.i:self.i + n]
        self.i += n
        return b

    def u(self, fmt):
        size = struct.calcsize(fmt)
        v = struct.unpack('>' + fmt, self.d[self.i:self.i + size])[0]
        self.i += size
        return v


def _payload(r, t):
    if t == 1:
        return r.u('b')
    if t == 2:
        return r.u('h')
    if t == 3:
        return r.u('i')
    if t == 4:
        return r.u('q')
    if t == 5:
        return r.u('f')
    if t == 6:
        return r.u('d')
    if t == 7:
        n = r.u('i')
        return r.take(n)
    if t == 8:
        n = r.u('H')
        return r.take(n).decode('utf-8', 'replace')
    if t == 9:
        et = r.u('b')
        n = r.u('i')
        return [_payload(r, et) for _ in range(n)]
    if t == 10:
        out = {}
        while True:
            ct = r.u('b')
            if ct == 0:
                return out
            name = r.take(r.u('H')).decode('utf-8', 'replace')
            out[name] = _payload(r, ct)
    if t == 11:
        n = r.u('i')
        return list(struct.unpack('>%di' % n, r.take(4 * n)))
    raise ValueError('tag type %d' % t)


def nbt(data):
    r = Reader(data)
    t = r.u('b')
    r.take(r.u('H'))
    return _payload(r, t)


def level(world):
    with gzip.open(os.path.join(world, 'level.dat')) as f:
        return nbt(f.read())['Data']


def chunks(world):
    """Every chunk of the world: its Level compound."""
    folder = os.path.join(world, 'region')
    for name in sorted(os.listdir(folder)):
        if not name.endswith('.mca'):
            continue
        with open(os.path.join(folder, name), 'rb') as f:
            data = f.read()
        if len(data) < 8192:
            continue
        for k in range(1024):
            off = struct.unpack('>I', data[4 * k:4 * k + 4])[0]
            sector = off >> 8
            if sector == 0:
                continue
            start = sector * 4096
            length = struct.unpack('>I', data[start:start + 4])[0]
            comp = data[start + 4]
            raw = data[start + 5:start + 4 + length]
            if comp == 2:
                raw = zlib.decompress(raw)
            elif comp == 1:
                raw = gzip.decompress(raw)
            yield nbt(raw)['Level']


def sections(chunk):
    """(base x, base y, base z, ids[y,z,x], data[y,z,x]) for each section of the chunk."""
    cx, cz = chunk['xPos'], chunk['zPos']
    for s in chunk.get('Sections', []):
        ids = np.frombuffer(s['Blocks'], dtype=np.uint8).astype(np.int32)
        if 'Add' in s:
            add = np.frombuffer(s['Add'], dtype=np.uint8)
            hi = np.empty(4096, dtype=np.int32)
            hi[0::2] = add & 0x0F
            hi[1::2] = add >> 4
            ids = ids | (hi << 8)
        dat = np.frombuffer(s['Data'], dtype=np.uint8)
        d = np.empty(4096, dtype=np.int32)
        d[0::2] = dat & 0x0F
        d[1::2] = dat >> 4
        yield cx * 16, s['Y'] * 16, cz * 16, ids.reshape(16, 16, 16), d.reshape(16, 16, 16)
