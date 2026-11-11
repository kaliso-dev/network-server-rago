"""Finds the Bed Wars points of a Hypixel-style map: the team beds, their colours, spawns, generators,
shops and upgrades, the diamond and emerald generators, and writes them as RagoBedwars' bedwars.yml.

    python3 bwsetup.py <world folder> <map name> [bedwars.yml] [preview.png]

It only reads the world. See README.md for how each point is found."""
import collections
import json
import math
import sys

import numpy as np
from scipy import ndimage
from scipy.optimize import linear_sum_assignment

from world import World

# Blocks a player walks through.
PASS = {0, 6, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 66, 68, 69, 70, 72, 75, 76, 77, 78, 83, 90, 93, 94, 104,
        105, 106, 111, 115, 119, 131, 132, 141, 142, 143, 147, 148, 149, 150, 157, 171, 175, 176, 177}
LIQUID = {8, 9, 10, 11}
# Blocks not to stand on: fences, walls, panes, barriers, leaves, beds, chests, ladders, liquids...
NOT_FLOOR = PASS | LIQUID | {26, 54, 130, 146, 85, 113, 188, 189, 190, 191, 192, 107, 183, 184, 185, 186, 187, 139, 101,
                             102, 160, 166, 18, 161, 65, 81, 92, 120, 127, 140, 144, 64, 71, 193, 194, 195, 196, 197, 96,
                             167, 117, 118, 116, 145}
SLABS = {44, 126, 182}
# How tall a villager is: an NPC needs this much room under whatever is above its head.
NPC_HEIGHT = 1.95

# The teams in RagoBedwars' order, with the data values of the wool/clay/glass that mark them.
TEAMS = [('red', [14]), ('blue', [11]), ('green', [5, 13]), ('yellow', [4]), ('aqua', [9, 3]), ('white', [0]),
         ('pink', [6, 2]), ('gray', [7, 8])]
COLOURED = [35, 159, 95, 160, 171]


def yaw_towards(dx, dz, step=45.0):
    """Minecraft yaw looking along (dx, dz), rounded to 45 degrees (or another step)."""
    yaw = math.degrees(math.atan2(-dx, dz))
    return float((round(yaw / step) * step) % 360)


class Setup:
    def __init__(self, path):
        self.w = w = World(path)
        ids = w.ids
        passable = np.isin(ids, list(PASS))
        floor = ~np.isin(ids, list(NOT_FLOOR))
        stand = np.zeros_like(passable)
        stand[:, 1:254, :] = floor[:, 0:253, :] & passable[:, 1:254, :] & passable[:, 2:255, :]
        self.stand = stand
        slab_below = np.zeros_like(passable)
        slab_below[:, 1:, :] = np.isin(ids[:, :-1, :], list(SLABS)) & ((w.data[:, :-1, :] & 8) == 0)
        self.slab_below = slab_below
        self.notes = []

    # --- cells -----------------------------------------------------------------------------------
    def h(self, i, y, k):
        return y - 0.5 if self.slab_below[i, y, k] else float(y)

    def column(self, i, k, ylo, yhi):
        if not (0 <= i < self.w.sx and 0 <= k < self.w.sz):
            return []
        ys = np.nonzero(self.stand[i, max(1, ylo):min(254, yhi) + 1, k])[0] + max(1, ylo)
        return [int(y) for y in ys]

    def walk(self, seeds, limit=60000):
        """The cells reachable on foot from the seeds: steps of one block, up 1 at most, down 4 at most."""
        seen = set(seeds)
        queue = collections.deque(seeds)
        while queue and len(seen) < limit:
            i, y, k = queue.popleft()
            h = self.h(i, y, k)
            for di, dk in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ni, nk = i + di, k + dk
                for ny in self.column(ni, nk, y - 4, y + 1):
                    if (ni, ny, nk) in seen:
                        continue
                    nh = self.h(ni, ny, nk)
                    if nh - h > 1.0 or h - nh > 4.0:
                        continue
                    # headroom to jump up
                    if nh - h > 0.5 and not ids_pass(self.w, i, y + 2, k):
                        continue
                    seen.add((ni, ny, nk))
                    queue.append((ni, ny, nk))
        return seen

    # --- beds ------------------------------------------------------------------------------------
    def beds(self):
        w = self.w
        found = []
        used = set()
        for i, y, k in np.argwhere(w.ids == 26):
            i, y, k = int(i), int(y), int(k)
            if (i, y, k) in used:
                continue
            d = int(w.data[i, y, k]) & 0xF
            facing = d & 3
            step = [(0, 1), (-1, 0), (0, -1), (1, 0)][facing]  # towards the head: south, west, north, east
            other = (i - step[0], y, k - step[1]) if d & 8 else (i + step[0], y, k + step[1])
            used.add((i, y, k))
            used.add(other)
            foot = (i, y, k) if not d & 8 else other
            head = other if not d & 8 else (i, y, k)
            found.append({'foot': foot, 'head': head})
        return found

    def team_beds(self):
        w = self.w
        beds = self.beds()
        # by their blocks: a ripped map can lose the tile entity of a chest
        chests = sorted({(t['x'] - w.x0, t['y'], t['z'] - w.z0) for t in w.tiles if t.get('id') == 'Chest'}
                        | {tuple(int(v) for v in p) for p in np.argwhere(w.ids == 54)})
        enders = [tuple(int(v) for v in p) for p in np.argwhere(w.ids == 130)]
        boxes = chests + enders
        team = []
        for b in beds:
            fi, fy, fk = b['foot']
            near = [c for c in boxes if abs(c[0] - fi) <= 20 and abs(c[2] - fk) <= 20 and abs(c[1] - fy) <= 6]
            b['boxes'] = near
            b['chests'] = [c for c in chests if c in near]
            b['enders'] = [c for c in enders if c in near]
            if near:
                team.append(b)
        if len(team) < 2:
            team = beds
            self.notes.append('no team chests: every bed is taken as a team bed')
        return team

    # --- islands ---------------------------------------------------------------------------------
    def islands(self):
        cols = (self.w.ids != 0).any(axis=1)
        lab, n = ndimage.label(cols, structure=np.ones((3, 3)))
        return lab, n

    def run(self):
        beds = self.team_beds()
        lab, n = self.islands()
        # one bed per island
        by_island = {}
        for b in beds:
            isl = int(lab[b['foot'][0], b['foot'][2]])
            if isl in by_island:
                prev = by_island[isl]
                if len(b['chests']) + len(b['enders']) > len(prev['chests']) + len(prev['enders']):
                    by_island[isl] = b
                self.notes.append('two beds on one island: one kept')
            else:
                by_island[isl] = b
        beds = list(by_island.values())
        self.kept_beds = beds
        for isl, b in by_island.items():
            b['island'] = isl
        cx = np.mean([b['foot'][0] for b in beds])
        cz = np.mean([b['foot'][2] for b in beds])
        self.centre = (cx, cz)
        teams = []
        for b in beds:
            t = self.team(b, lab)
            if t:
                teams.append(t)
        self.colours(teams, lab)
        teams.sort(key=lambda t: [c for c, _ in TEAMS].index(t['colour']))
        diamonds, emeralds = self.resources(lab, n, set(b['island'] for b in beds), len(teams))
        return teams, diamonds, emeralds

    def team(self, b, lab):
        fi, fy, fk = b['foot']
        hi_, _, hk = b['head']
        seeds = []
        for i in range(min(fi, hi_) - 2, max(fi, hi_) + 3):
            for k in range(min(fk, hk) - 2, max(fk, hk) + 3):
                for y in self.column(i, k, fy - 1, fy + 1):
                    seeds.append((i, y, k))
        if not seeds:
            self.notes.append('bed at %s: nowhere to stand around it' % (self.wc(b['foot']),))
            return None
        cells = self.walk(seeds)
        level = [c for c in cells if abs(self.h(*c) - fy) <= 2.0]
        if len(level) < 10:
            self.notes.append('bed at %s: too small an island around it' % (self.wc(b['foot']),))
            return None
        P = np.array([[c[0], c[2]] for c in level], dtype=float) + 0.5
        cxz = P.mean(axis=0)
        bed_xz = np.array([(fi + hi_) / 2 + 0.5, (fk + hk) / 2 + 0.5])
        if b['chests'] and b['enders']:
            ch, en = b['chests'][0], b['enders'][0]
            back = np.array([(ch[0] + en[0]) / 2 + 0.5, (ch[2] + en[2]) / 2 + 0.5])
        else:
            back = cxz
        u = back - bed_xz
        if np.linalg.norm(u) < 3:
            u = cxz - np.array(self.centre)
        # islands face a cardinal or diagonal direction
        ang = math.atan2(u[1], u[0])
        ang = round(ang / (math.pi / 4)) * (math.pi / 4)
        u = np.array([math.cos(ang), math.sin(ang)])
        u[np.abs(u) < 1e-9] = 0.0
        v = np.array([-u[1], u[0]])
        a = (P - bed_xz) @ u
        l = (P - bed_xz) @ v
        cellset = {(c[0], c[2]): c for c in level}

        def snap(target, pool=None, avoid=()):
            pool = level if pool is None else pool
            best, bd = None, 1e9
            for c in pool:
                d = (c[0] + 0.5 - target[0]) ** 2 + (c[2] + 0.5 - target[1]) ** 2 + 0.5 * (self.h(*c) - fy) ** 2
                if any((c[0] - q[0]) ** 2 + (c[2] - q[2]) ** 2 < 9 for q in avoid):
                    d += 50
                if d < bd:
                    best, bd = c, d
            return best

        # generator: a pit of slabs half a block under the floor, where the items fall; else a small
        # pit a block under it; else the back of the island, on its axis
        floor_h = collections.Counter(self.h(*c) for c in level).most_common(1)[0][0]
        # the NPCs' places, under their barriers: never the generator
        marked = self.barrier_spots(b['foot'], radius=20)
        npc_places = [(n['x'], n['z']) for n in marked]

        def find_pits(sunk, most, sunk_by=None):
            pits = []
            # no generator where an NPC stands
            sunk = {q: c for q, c in sunk.items()
                    if all((q[0] + 0.5 - px) ** 2 + (q[1] + 0.5 - pz) ** 2 >= 2.5 ** 2 for px, pz in npc_places)}
            left = set(sunk)
            while left:
                start = left.pop()
                group, todo = [start], [start]
                while todo:
                    x, z = todo.pop()
                    for nxz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                        if nxz in left:
                            left.remove(nxz)
                            group.append(nxz)
                            todo.append(nxz)
                # a pit, not a step down to a lower floor: nothing around it is lower
                inside = set(group)
                hg = max(self.h(*sunk[q]) for q in group)
                step = any((x + dx, z + dz) not in inside and (x + dx, z + dz) in cellset
                           and self.h(*cellset[(x + dx, z + dz)]) < hg - 0.1
                           for x, z in group for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                chests = any(abs(q[0] - x) + abs(q[2] - z) <= 1 for q in b['boxes'] for x, z in group)
                # sunk into the floor around it: the cells next to it mostly half a block higher
                around = collections.Counter(self.h(*cellset[(x + dx, z + dz)]) - hg for x, z in group
                                             for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                                             if (x + dx, z + dz) not in inside and (x + dx, z + dz) in cellset)
                if sunk_by is not None and (not around or abs(around.most_common(1)[0][0] - sunk_by) > 0.01):
                    continue
                if 3 <= len(group) <= most and not step and not chests:
                    pits.append([sunk[q] for q in group])
            return pits

        pits = find_pits({(c[0], c[2]): c for c in level if self.slab_below[c] and self.h(*c) == floor_h - 0.5}, 40)
        if not pits:
            # the floor of the island is not the floor of its base: slabs half a block under theirs
            pits = find_pits({(c[0], c[2]): c for c in level if self.slab_below[c]}, 40, sunk_by=0.5)
        how_g = 'slab pit'
        if not pits:
            pits = find_pits({(c[0], c[2]): c for c in level if floor_h - 1.5 <= self.h(*c) <= floor_h - 0.9}, 100)
            how_g = 'pit'
        if pits:
            def pit_rank(p):
                centre = np.array([np.mean([c[0] for c in p]) + 0.5, np.mean([c[2] for c in p]) + 0.5])
                return (min(len(p), 9), float((centre - bed_xz) @ u))
            pit = max(pits, key=pit_rank)
            mx = np.mean([c[0] for c in pit])
            mz = np.mean([c[2] for c in pit])
            g = min(pit, key=lambda c: (c[0] - mx) ** 2 + (c[2] - mz) ** 2)
        else:
            band = np.abs(l) <= 1.5
            if band.sum() < 3:
                band = np.abs(l) <= 3
            idx = np.nonzero(band)[0]
            g = level[int(idx[np.argmax(a[idx])])]
            how_g = 'back of the island'
        ga = float((np.array([g[0] + 0.5, g[2] + 0.5]) - bed_xz) @ u)
        # spawn: between the chest and the ender chest, else between the bed and the generator
        if b['chests'] and b['enders']:
            ch, en = b['chests'][0], b['enders'][0]
            target = ((ch[0] + en[0]) / 2 + 0.5, (ch[2] + en[2]) / 2 + 0.5)
            how = 'between the chests'
        else:
            target = ((fi + g[0]) / 2 + 0.5, (fk + g[2]) / 2 + 0.5)
            how = 'between bed and generator'
        s = snap(target, avoid=[g])
        sa = float((np.array([s[0] + 0.5, s[2] + 0.5]) - bed_xz) @ u)
        # shop and upgrades: in the niche on each side of the spawn where the map puts its NPCs; else
        # on each side, from the spawn to the generator
        sxz = np.array([s[0] + 0.5, s[2] + 0.5])
        niches = self.niches(level, cellset, b['boxes'])
        # Hypixel's maps keep barriers over the NPCs' heads: where there are some, there they stand
        under_barriers = {}
        for sign in (1, -1):
            best = None
            for n in marked:
                nxz = np.array([n['x'], n['z']])
                sd = float(((nxz - sxz) ** 2).sum())
                if sign * float((nxz - sxz) @ v) < 1.5 or abs(n['h'] - self.h(*s)) > 2.5 or sd > 16 ** 2:
                    continue
                if best is None or sd < best[0]:
                    best = (sd, n)
            if best is not None:
                under_barriers[sign] = best[1]
        side = []
        how_n = []
        for sign in (1, -1):
            if sign in under_barriers:
                side.append(under_barriers[sign])
                how_n.append('barriers')
                continue
            if -sign in under_barriers:
                # the islands are symmetric: the mirror image of the other NPC's place
                n = under_barriers[-sign]
                d = np.array([n['x'], n['z']]) - bed_xz
                m = bed_xz + (d @ u) * u - (d @ v) * v
                near = [c for c in level if abs(c[0] + 0.5 - m[0]) <= 1.5 and abs(c[2] + 0.5 - m[1]) <= 1.5]
                if near and (m[0] - g[0] - 0.5) ** 2 + (m[1] - g[2] - 0.5) ** 2 >= 9:
                    c0 = min(near, key=lambda c: (c[0] + 0.5 - m[0]) ** 2 + (c[2] + 0.5 - m[1]) ** 2)
                    same = [c for c in near if self.h(*c) == self.h(*c0)]
                    side.append({'x': float(np.mean([c[0] for c in same])) + 0.5,
                                 'z': float(np.mean([c[2] for c in same])) + 0.5, 'h': self.h(*c0), 'way': None})
                    how_n.append('mirror')
                    continue
            best = None
            for n in niches:
                nxz = np.array([n['x'], n['z']])
                na = float((nxz - bed_xz) @ u)
                nl = float((nxz - bed_xz) @ v)
                sl = float((sxz - bed_xz) @ v)
                if sign * (nl - sl) < 1.5 or abs(n['h'] - self.h(*s)) > 2.0:
                    continue
                if na < min(sa, ga) - 6 or na > max(sa, ga) + 2:
                    continue
                gd = (n['x'] - g[0] - 0.5) ** 2 + (n['z'] - g[2] - 0.5) ** 2
                sd = float(((nxz - sxz) ** 2).sum())
                if gd < 9 or sd > 14 ** 2:
                    continue
                # the floor of the spawn first: a nook a step up is not where the NPC stands
                rank = (abs(n['h'] - self.h(*s)) > 0.5, sd)
                if best is None or rank < best[0]:
                    best = (rank, n)
            if best is not None:
                side.append(best[1])
                how_n.append('niche')
                continue
            pick = None
            best = -1e9
            for c, ai, li in zip(level, a, l):
                if ai < min(sa, ga) - 3 or ai > max(sa, ga) + 1 or abs(self.h(*c) - self.h(*s)) > 1.5:
                    continue
                if (c[0] - g[0]) ** 2 + (c[2] - g[2]) ** 2 < 9 or (c[0] - s[0]) ** 2 + (c[2] - s[2]) ** 2 < 6:
                    continue
                if any((c[0] - q[0]) ** 2 + (c[2] - q[2]) ** 2 < 3 for q in b['boxes']):
                    continue
                score = sign * li
                if score > best:
                    best, pick = score, c
            if pick is not None:
                # one block in from the edge, if there is floor there
                step = -sign * v
                inner = cellset.get((int(math.floor(pick[0] + 0.5 + step[0])), int(math.floor(pick[2] + 0.5 + step[1]))))
                if inner is not None and abs(self.h(*inner) - self.h(*pick)) <= 1:
                    pick = inner
            side.append(pick)
            how_n.append('side')
        shop, upgrades = [self.npc(c, sxz) for c in side]
        team = {
            'bed': b, 'island': b['island'], 'axis': u, 'cells': len(level),
            'spawn': self.point(s, face=(fi + 0.5 - (s[0] + 0.5), fk + 0.5 - (s[2] + 0.5))),
            'generator': self.point(g, centre=True),
            'shop': shop,
            'upgrades': upgrades,
            'how': how + ', generator: ' + how_g + ', NPCs: ' + '/'.join(how_n),
        }
        return team

    def barrier_spots(self, s, radius=16):
        """Where NPCs stood near this cell: under a group of barrier blocks, on the floor below."""
        w = self.w
        if not hasattr(self, 'barriers'):
            self.barriers = np.argwhere(w.ids == 166)
        sy = s[1]
        bar = self.barriers
        near = bar[(np.abs(bar[:, 0] - s[0]) <= radius) & (np.abs(bar[:, 2] - s[2]) <= radius)
                   & (bar[:, 1] >= sy - 2) & (bar[:, 1] <= sy + 8)]
        left = {tuple(int(v) for v in p) for p in near}
        spots = []
        while left:
            start = left.pop()
            group, todo = [start], [start]
            while todo:
                i, y, k = todo.pop()
                for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    n = (i + d[0], y + d[1], k + d[2])
                    if n in left:
                        left.remove(n)
                        group.append(n)
                        todo.append(n)
            if len(group) > 60:
                continue
            lowest = {}
            for i, y, k in group:
                lowest[(i, k)] = min(y, lowest.get((i, k), 999))
            floors = []
            for (i, k), y in lowest.items():
                # some air under the barrier, then a floor
                f = y - 1
                while f > y - 4 and ids_pass(w, i, f, k):
                    f -= 1
                if f == y - 1 or f <= y - 4:
                    continue
                top = f + 0.5 if int(w.ids[i, f, k]) in SLABS and not int(w.data[i, f, k]) & 8 else f + 1.0
                if y - top >= 1.0:
                    floors.append((i, k, top))
            if not floors:
                continue
            h = collections.Counter(f[2] for f in floors).most_common(1)[0][0]
            floors = [f for f in floors if f[2] == h]
            way = None
            if min(lowest[(f[0], f[1])] for f in floors) - h < NPC_HEIGHT:
                # too low for a villager: the barriers guard the front of its niche (a slab under them,
                # as on Sky Rise or Extinction), and the NPC stands in the niche behind, facing out
                behind = self.behind_guard(floors, s)
                if behind is None:
                    continue
                floors, h, way = behind
            spots.append({'x': float(np.mean([f[0] for f in floors])) + 0.5, 'z': float(np.mean([f[1] for f in floors])) + 0.5,
                          'h': h, 'way': way})
        return spots

    def floor_top(self, i, k, near):
        """The top of the floor of this column at about this height (a slab's half included), or None."""
        w = self.w
        for f in range(int(math.ceil(near)), int(math.floor(near)) - 3, -1):
            if not ids_pass(w, i, f, k):
                if not (0 <= f < 256 and 0 <= i < w.sx and 0 <= k < w.sz) or int(w.ids[i, f, k]) in NOT_FLOOR:
                    return None
                return f + 0.5 if int(w.ids[i, f, k]) in SLABS and not int(w.data[i, f, k]) & 8 else f + 1.0
        return None

    def behind_guard(self, guard, s):
        """The cells next to these guarded ones where a villager stands: room for its height, walled in
        on the sides (a niche), the farther from the bed the better. With the way out, towards the guard."""
        w = self.w
        guard_cells = {(g[0], g[1]) for g in guard}
        best = None
        for di, dk in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cells = []
            for i, k, top in guard:
                ni, nk = i + di, k + dk
                if (ni, nk) in guard_cells:
                    cells = None
                    break
                ntop = self.floor_top(ni, nk, top)
                if ntop is None or abs(ntop - top) > 1.0:
                    cells = None
                    break
                base = int(math.floor(ntop + 0.01))
                if not all(ids_pass(w, ni, y, nk) for y in range(base if ntop == base else base + 1, int(ntop + NPC_HEIGHT) + 1)):
                    cells = None
                    break
                cells.append((ni, nk, ntop))
            if not cells:
                continue
            tops = collections.Counter(c[2] for c in cells).most_common(1)[0][0]
            cells = [c for c in cells if c[2] == tops]
            inside = {(c[0], c[1]) for c in cells}
            walls = 0
            for ni, nk, ntop in cells:
                for ei, ek in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (ni + ei, nk + ek)
                    if q in inside or q in guard_cells:
                        continue
                    if not ids_pass(w, q[0], int(math.floor(ntop + 0.01)) + 1, q[1]):
                        walls += 1
            mi = float(np.mean([c[0] for c in cells]))
            mk = float(np.mean([c[1] for c in cells]))
            score = (walls, (mi - s[0]) ** 2 + (mk - s[2]) ** 2)
            if best is None or score > best[0]:
                best = (score, cells, tops, (-di, -dk))
        if best is None:
            return None
        return best[1], best[2], best[3]

    def niches(self, level, cellset, boxes):
        """The NPC niches: small groups of cells walled in all round but on one side, where they open."""
        w = self.w

        def blocked(i, y, k):
            return not (ids_pass(w, i, y, k) and ids_pass(w, i, y + 1, k))

        walled = set()
        for c in level:
            i, y, k = c
            around = sum(blocked(i + di, y, k + dk) for di in (-1, 0, 1) for dk in (-1, 0, 1) if di or dk)
            if around >= 6 and not any(max(abs(q[0] - i), abs(q[2] - k)) <= 1 and abs(q[1] - y) <= 2 for q in boxes):
                walled.add(c)
        found = []
        left = set(walled)
        while left:
            start = left.pop()
            group, todo = [start], [start]
            while todo:
                i, y, k = todo.pop()
                for di, dk in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = (i + di, y, k + dk)
                    if n in left:
                        left.remove(n)
                        group.append(n)
                        todo.append(n)
            if len(group) > 4:
                continue
            inside = {(c[0], c[2]) for c in group}
            ways = set()
            for i, y, k in group:
                for di, dk in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if (i + di, k + dk) in inside:
                        continue
                    n = cellset.get((i + di, k + dk))
                    if not blocked(i + di, y, k + dk) or (n is not None and abs(self.h(*n) - self.h(i, y, k)) <= 1):
                        ways.add((di, dk))
            if len(ways) != 1:
                continue
            way = ways.pop()
            found.append({'x': float(np.mean([c[0] for c in group])) + 0.5, 'z': float(np.mean([c[2] for c in group])) + 0.5,
                          'h': min(self.h(*c) for c in group), 'way': way, 'cells': group})
        return found

    def npc(self, spot, towards):
        """A shop or upgrades point: in its niche facing out of it, or on a cell facing the spawn."""
        if spot is None:
            return None
        if isinstance(spot, dict):
            x, z = self.w.world_xz(spot['x'], spot['z'])
            way = spot['way'] or (towards[0] - spot['x'], towards[1] - spot['z'])
            # a quarter turn, as Hypixel's NPCs stand: straight out of their niche
            return {'x': float(x), 'y': spot['h'], 'z': float(z), 'yaw': yaw_towards(*way, step=90.0)}
        p = self.point(spot)
        p['yaw'] = yaw_towards(towards[0] - spot[0] - 0.5, towards[1] - spot[2] - 0.5, step=90.0)
        return p

    def point(self, c, face=None, centre=False):
        i, y, k = c
        x, z = self.w.world_xz(i, k)
        p = {'x': x + 0.5, 'y': self.h(i, y, k), 'z': z + 0.5}
        if face is not None and (face[0] or face[1]):
            p['yaw'] = yaw_towards(face[0], face[1])
        return p

    def wc(self, c):
        x, z = self.w.world_xz(c[0], c[2])
        return (x, c[1], z)

    # --- colours ---------------------------------------------------------------------------------
    def colours(self, teams, lab):
        w = self.w
        n = len(teams)
        names = [c for c, _ in TEAMS][:max(2, min(8, n if n in (2, 4, 8) else 8))]
        counts = np.zeros((n, 16))
        for ti, t in enumerate(teams):
            cols = np.argwhere(lab == t['island'])
            sub_ids = w.ids[cols[:, 0], :, cols[:, 1]]
            sub_dat = w.data[cols[:, 0], :, cols[:, 1]] & 15
            mask = np.isin(sub_ids, COLOURED)
            vals, cnt = np.unique(sub_dat[mask], return_counts=True)
            for vv, cc in zip(vals, cnt):
                counts[ti, int(vv)] = cc
        median = np.median(counts, axis=0)
        excess = counts - median
        score = np.zeros((n, len(names)))
        for j, name in enumerate(names):
            datas = dict(TEAMS)[name]
            score[:, j] = np.max(excess[:, datas], axis=1)
        if len(names) < n:
            names = [c for c, _ in TEAMS]
        rows, cols_ = linear_sum_assignment(-score)
        for r, c in zip(rows, cols_):
            teams[r]['colour'] = names[c]
            teams[r]['colour_score'] = float(score[r, c])
        for t in teams:
            if 'colour' not in t:
                free = [c for c, _ in TEAMS if c not in [x.get('colour') for x in teams]]
                t['colour'] = free[0]
                t['colour_score'] = 0.0

    # --- diamonds and emeralds -------------------------------------------------------------------
    def markers(self, block, lab, team_islands, bed_y, total_cap):
        """Generators marked by lone diamond or emerald blocks: the place to stand above each one."""
        w = self.w
        pts = np.argwhere(w.ids == block)
        if len(pts) == 0:
            return None
        if len(pts) > total_cap:
            pts = np.array([p for p in pts if abs(int(p[1]) - bed_y) <= 4])
        if len(pts) == 0 or len(pts) > 16:
            return None
        # clusters of touching blocks (26-neighbourhood)
        pts = [tuple(int(v) for v in p) for p in pts]
        groups = []
        for p in pts:
            joined = [g for g in groups if any(max(abs(p[0] - q[0]), abs(p[1] - q[1]), abs(p[2] - q[2])) <= 1 for q in g)]
            merged = [p]
            for g in joined:
                merged.extend(g)
                groups.remove(g)
            groups.append(merged)
        found = []
        for g in groups:
            cp = np.array(g)
            if len(cp) > 2 or len(set((int(q[0]), int(q[2])) for q in cp)) > 1:
                return None
            i, k = int(cp[0][0]), int(cp[0][2])
            if int(lab[i, k]) in team_islands:
                return None
            top = int(cp[:, 1].max())
            ys = self.column(i, k, top + 1, top + 12)
            if not ys:
                return None
            x, z = w.world_xz(i, k)
            found.append({'x': x + 0.5, 'y': self.h(i, ys[0], k), 'z': z + 0.5,
                          'r': math.hypot(i + 0.5 - self.centre[0], k + 0.5 - self.centre[1])})
        unique = []
        for f in found:
            if not any(f['x'] == q['x'] and f['y'] == q['y'] and f['z'] == q['z'] for q in unique):
                unique.append(f)
        found = unique
        if len(found) not in (2, 4, 8):
            return None
        if len(found) == 8:
            # two rings of four: the one nearer to the middle
            found.sort(key=lambda f: f['r'])
            if found[3]['r'] + 2 < found[4]['r']:
                found = found[:4]
        for f in found:
            del f['r']
        return found

    def resources(self, lab, n, team_islands, nteams):
        bed_y = int(np.median([b['foot'][1] for b in self.kept_beds]))
        marked_d = self.markers(57, lab, team_islands, bed_y, 8)
        marked_e = self.markers(133, lab, team_islands, bed_y, 8)
        pads_d = self.pads(57, lab, team_islands, 4 if nteams >= 4 else 2)
        pads_e = self.pads(133, lab, team_islands, 4 if nteams >= 8 else 2)
        diamonds, emeralds = self.resources_by_shape(lab, n, team_islands, nteams)
        how_d = how_e = 'by shape'
        if pads_d:
            marked_d = pads_d
        if pads_e:
            marked_e = pads_e
        if marked_d:
            diamonds, how_d = marked_d, 'marked'
        else:
            decor = self.decorated(57, lab, team_islands, bed_y, 4 if nteams >= 4 else 2)
            if decor:
                diamonds, how_d = decor, 'by decoration'
        if marked_e:
            emeralds, how_e = marked_e, 'marked'
        else:
            decor = self.decorated(133, lab, team_islands, bed_y, 4 if nteams >= 8 else 2, centre_only=True)
            if decor:
                emeralds, how_e = decor, 'by decoration'
        self.how_resources = 'diamonds: %s, emeralds: %s' % (how_d, how_e)
        return diamonds, emeralds

    RING = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]

    def pads(self, block, lab, team_islands, count):
        """Generators on their pad, as Hypixel builds them: a diamond or emerald block open to the
        sky, in a ring of eight blocks, six of them at least the same (stairs, slabs...), at its level or one above. The
        place to stand is on the block. A pad in the very middle goes when there are more than
        expected (a decoration under the middle), and so do the pads with a mixed ring when some
        have a ring all the same. None if no pad is found."""
        w = self.w
        cx, cz = self.centre
        found = []
        for p in np.argwhere(w.ids == block):
            i, y, k = int(p[0]), int(p[1]), int(p[2])
            if y + 2 >= w.ids.shape[1] or w.ids[i, y + 1, k] != 0 or w.ids[i, y + 2, k] != 0:
                continue
            if int(lab[i, k]) in team_islands:
                continue
            ringed = strict = False
            for level in (y, y + 1):
                ids = []
                for di, dk in self.RING:
                    a, b = i + di, k + dk
                    inside = 0 <= a < w.ids.shape[0] and 0 <= b < w.ids.shape[2]
                    ids.append(int(w.ids[a, level, b]) if inside else 0)
                common, times = collections.Counter(ids).most_common(1)[0]
                if all(ids) and times >= 6 and common != block:
                    ringed, strict = True, times == 8
                    break
            if ringed:
                x, z = w.world_xz(i, k)
                found.append({'x': x + 0.5, 'y': float(y + 1), 'z': z + 0.5, 'strict': strict,
                              'r': math.hypot(i + 0.5 - cx, k + 0.5 - cz)})
        if len(found) > count and any(f['strict'] for f in found):
            found = [f for f in found if f['strict']]
        if len(found) > count:
            found = [f for f in found if f['r'] > 2]
        if not 1 <= len(found) <= 8:
            return None
        for f in found:
            del f['r']
            del f['strict']
        return found

    def decorated(self, block, lab, team_islands, bed_y, count, centre_only=False):
        """Generators by the gem blocks that decorate their islands: the biggest groups of them,
        out of the team islands, as many as expected and set out evenly around the middle."""
        w = self.w
        cx, cz = self.centre
        pts = np.argwhere(w.ids == block)
        pts = [p for p in pts if int(lab[p[0], p[2]]) not in team_islands and abs(int(p[1]) - bed_y) <= 30]
        if len(pts) < count:
            return None
        cells = collections.Counter((int(p[0]) // 6, int(p[2]) // 6) for p in pts)
        # groups of touching 6x6 cells
        left = set(cells)
        groups = []
        while left:
            start = left.pop()
            group, todo = [start], [start]
            while todo:
                a, b = todo.pop()
                for da in (-1, 0, 1):
                    for db in (-1, 0, 1):
                        q = (a + da, b + db)
                        if q in left:
                            left.remove(q)
                            group.append(q)
                            todo.append(q)
            n = sum(cells[q] for q in group)
            gx = sum((q[0] * 6 + 3) * cells[q] for q in group) / n
            gz = sum((q[1] * 6 + 3) * cells[q] for q in group) / n
            groups.append({'n': n, 'x': gx, 'z': gz, 'r': math.hypot(gx - cx, gz - cz)})
        groups.sort(key=lambda g: -g['n'])
        if centre_only:
            team_r = np.median([math.hypot(b['foot'][0] - cx, b['foot'][2] - cz) for b in self.kept_beds])
            groups = [g for g in groups if g['r'] < 0.5 * team_r]
        if len(groups) < count:
            return None
        chosen = groups[:count]
        # set out evenly: the chosen groups at about the same distance from the middle
        rs = [g['r'] for g in chosen]
        if max(rs) - min(rs) > 12 or chosen[-1]['n'] < 0.3 * chosen[0]['n']:
            return None
        found = []
        for g in chosen:
            p = self.near_point((g['x'], g['z']), bed_y, 10)
            if p:
                found.append(p)
        return found if len(found) == count else None

    def near_point(self, target, bed_y, radius):
        """A flat standing place on the floor of the game, near this point (array coordinates)."""
        tx, tz = target
        best, bd = None, 1e9
        for i in range(int(tx - radius), int(tx + radius) + 1):
            for k in range(int(tz - radius), int(tz + radius) + 1):
                if (i + 0.5 - tx) ** 2 + (k + 0.5 - tz) ** 2 > radius ** 2:
                    continue
                for y in self.column(i, k, bed_y - 12, bed_y + 25):
                    flat = sum(1 for di in (-1, 0, 1) for dk in (-1, 0, 1)
                               if 0 <= i + di < self.w.sx and 0 <= k + dk < self.w.sz and self.stand[i + di, y, k + dk])
                    d = (i + 0.5 - tx) ** 2 + (k + 0.5 - tz) ** 2 + (9 - flat) * 8 + 0.3 * abs(y - bed_y) ** 2
                    if d < bd:
                        best, bd = (i, y, k), d
        return self.point(best) if best else None

    def resources_by_shape(self, lab, n, team_islands, nteams):
        w = self.w
        cx, cz = self.centre
        others = []
        for isl in range(1, n + 1):
            if isl in team_islands:
                continue
            cols = np.argwhere(lab == isl)
            if len(cols) < 25:
                continue
            xs = cols[:, 0] + 0.5
            zs = cols[:, 1] + 0.5
            mx, mz = xs.mean(), zs.mean()
            others.append({'island': isl, 'cols': cols, 'x': mx, 'z': mz,
                           'dist': math.hypot(mx - cx, mz - cz), 'size': len(cols)})
        if not others:
            return [], []
        others.sort(key=lambda o: o['dist'])
        middle = [o for o in others if o['dist'] < 12]
        rest = [o for o in others if o['dist'] >= 12]
        # diamond islands: the outermost ring of islands that are not the teams'
        diamonds = []
        if rest:
            far = max(o['dist'] for o in rest)
            ring = [o for o in rest if o['dist'] > far - 10]
            # four diamond islands (two on a map of two teams): the biggest of the ring
            ring.sort(key=lambda o: -o['size'])
            ring = ring[:4 if nteams >= 4 else 2]
            for o in ring:
                p = self.top_point(o, (o['x'] + w.x0, o['z'] + w.z0))
                if p:
                    diamonds.append(p)
            mids = [o for o in rest if o not in ring]
        else:
            mids = []
        if not middle and mids:
            middle = [mids[0]]
        emeralds = []
        count = 4 if nteams >= 8 else 2
        if middle:
            o = middle[0]
            xs = o['cols'][:, 0] + 0.5
            zs = o['cols'][:, 1] + 0.5
            radius = np.percentile(np.hypot(xs - cx, zs - cz), 60) * 0.55
            wcx, wcz = cx + w.x0, cz + w.z0
            base = math.atan2(diamonds[0]['z'] - wcz, diamonds[0]['x'] - wcx) if diamonds else math.pi / 4
            for k in range(count):
                ang = base + 2 * math.pi * k / count
                target = (wcx + radius * math.cos(ang), wcz + radius * math.sin(ang))
                p = self.top_point(o, target)
                if p and all((p['x'] - q['x']) ** 2 + (p['z'] - q['z']) ** 2 > 9 for q in emeralds):
                    emeralds.append(p)
        return diamonds, emeralds

    def top_point(self, o, target):
        """The standing place nearest to the target on this island, on a flat spot of its main level."""
        w = self.w
        cells = []
        for i, k in o['cols']:
            ys = self.column(int(i), int(k), 1, 253)
            if ys:
                y = ys[-1]
                cells.append((int(i), y, int(k)))
        if not cells:
            return None
        hs = collections.Counter(int(self.h(*c)) for c in cells)
        main = hs.most_common(1)[0][0]
        cellset = {(c[0], c[2]): c for c in cells}
        best, bd = None, 1e9
        for c in cells:
            if abs(self.h(*c) - main) > 3:
                continue
            flat = sum(1 for di in (-1, 0, 1) for dk in (-1, 0, 1)
                       if (c[0] + di, c[2] + dk) in cellset and abs(self.h(*cellset[(c[0] + di, c[2] + dk)]) - self.h(*c)) < 0.6)
            x, z = w.world_xz(c[0], c[2])
            d = (x + 0.5 - target[0]) ** 2 + (z + 0.5 - target[1]) ** 2 + (9 - flat) * 6
            if d < bd:
                best, bd = c, d
        return self.point(best)


def ids_pass(w, i, y, k):
    if 0 <= y < 256 and 0 <= i < w.sx and 0 <= k < w.sz:
        return int(w.ids[i, y, k]) in PASS
    return True


def yaml_point(p, indent):
    pad = ' ' * indent
    out = ['%sx: %s' % (pad, p['x']), '%sy: %s' % (pad, p['y']), '%sz: %s' % (pad, p['z'])]
    if 'yaw' in p:
        out.append('%syaw: %s' % (pad, p['yaw']))
    if 'pitch' in p:
        out.append('%spitch: %s' % (pad, p['pitch']))
    return out


def to_yaml(name, teams, diamonds, emeralds, spectators):
    lines = ['# The Bed Wars points of this map, found by the map tool from its beds and islands.',
             '# Check them in game with /bw edit %s, and move any of them with /bw (see /bw info %s).' % (name, name),
             'actif: true', 'equipes:']
    for t in teams:
        lines.append('  %s:' % t['colour'])
        for key, field in (('spawn', 'spawn'), ('lit', 'bed_point'), ('generateur', 'generator'), ('shop', 'shop'),
                           ('ameliorations', 'upgrades')):
            p = t.get(field)
            if p:
                lines.append('    %s:' % key)
                lines.extend(yaml_point(p, 6))
    lines.append('diamants:')
    for p in diamonds:
        pts = yaml_point(p, 2)
        lines.append('- ' + pts[0].strip())
        lines.extend(pts[1:])
    lines.append('emeraudes:')
    for p in emeralds:
        pts = yaml_point(p, 2)
        lines.append('- ' + pts[0].strip())
        lines.extend(pts[1:])
    lines.append('spectateurs:')
    lines.extend(yaml_point(spectators, 2))
    return '\n'.join(lines) + '\n'


def main():
    path, name = sys.argv[1], sys.argv[2]
    out_yml = sys.argv[3] if len(sys.argv) > 3 else None
    out_png = sys.argv[4] if len(sys.argv) > 4 else None
    s = Setup(path)
    teams, diamonds, emeralds = s.run()
    w = s.w
    for t in teams:
        fx, fy, fz = s.wc(t['bed']['foot'])
        t['bed_point'] = {'x': fx, 'y': fy, 'z': fz}
    spawn_y = max(t['spawn']['y'] for t in teams) if teams else 80
    spectators = {'x': round(s.centre[0] + w.x0 + 0.5, 1), 'y': float(int(spawn_y) + 25),
                  'z': round(s.centre[1] + w.z0 + 0.5, 1), 'yaw': 0.0, 'pitch': 90.0}
    text = to_yaml(name, teams, diamonds, emeralds, spectators)
    if out_yml:
        with open(out_yml, 'w') as f:
            f.write(text)
    summary = {
        'map': name, 'teams': len(teams), 'colours': [t['colour'] for t in teams],
        'colour_scores': [round(t['colour_score'], 1) for t in teams],
        'missing': [t['colour'] + ':' + ','.join(k for k in ('shop', 'upgrades') if not t.get(k)) for t in teams
                    if not t.get('shop') or not t.get('upgrades')],
        'diamonds': len(diamonds), 'emeralds': len(emeralds), 'notes': sorted(set(s.notes)),
        'spawn_how': collections.Counter(t['how'] for t in teams), 'resources': s.how_resources,
    }
    print(json.dumps(summary, default=str))
    if out_png:
        from render import render
        rgb = {'red': (255, 60, 60), 'blue': (60, 90, 255), 'green': (90, 230, 60), 'yellow': (255, 230, 40),
               'aqua': (40, 220, 220), 'white': (255, 255, 255), 'pink': (255, 130, 200), 'gray': (120, 120, 120)}
        marks = []
        for t in teams:
            c = rgb[t['colour']]
            marks.append((t['spawn']['x'], t['spawn']['z'], c, 'S'))
            marks.append((t['generator']['x'], t['generator']['z'], (230, 230, 230), 'G'))
            if t.get('shop'):
                marks.append((t['shop']['x'], t['shop']['z'], (255, 200, 0), 'I'))
            if t.get('upgrades'):
                marks.append((t['upgrades']['x'], t['upgrades']['z'], (200, 120, 255), 'U'))
            marks.append((t['bed_point']['x'] + 0.5, t['bed_point']['z'] + 0.5, c, t['colour'][:1].upper()))
        for p in diamonds:
            marks.append((p['x'], p['z'], (90, 220, 255), 'D'))
        for p in emeralds:
            marks.append((p['x'], p['z'], (30, 200, 80), 'E'))
        floor = int(np.median([t['bed_point']['y'] for t in teams])) + 2 if teams else 255
        render(w, marks, scale=4, ymax=floor, path=out_png, title=name + ' (y <= %d)' % floor)


if __name__ == '__main__':
    main()
