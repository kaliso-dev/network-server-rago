#!/usr/bin/env node
/**
 * Renders the skins of the lobby's NPCs (bukkit/rago-lobby/src/main/resources/skins, the owner's
 * own skins) as the front of the character, the way the game draws it: head, body, arms and legs,
 * with their second layer on top. Each one is written to public/skins/<name>.png, 12 times its
 * 16 x 32 pixels, so that a phone screen shows it sharp, and the skin itself to
 * public/skins/3d/<name>.png for the 3D view. Nothing is drawn by hand: every pixel comes from the
 * skin file.
 */
import { copyFileSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SKINS = path.resolve(HERE, '../../../bukkit/rago-lobby/src/main/resources/skins');
const OUT = path.resolve(HERE, '../public/skins');
const SCALE = 12;

/** The front faces of a 64 x 64 skin: [x, y, width, height] in the skin, [x, y] in the render. */
function parts(slim, legacy) {
  const arm = slim ? 3 : 4;
  const base = [
    { from: [8, 8, 8, 8], to: [4, 0] }, // head
    { from: [20, 20, 8, 12], to: [4, 8] }, // body
    { from: [44, 20, arm, 12], to: [4 - arm, 8] }, // right arm, on the left of the picture
    { from: legacy ? [44, 20, arm, 12] : [36, 52, arm, 12], to: [12, 8], mirror: legacy }, // left arm
    { from: [4, 20, 4, 12], to: [4, 20] }, // right leg
    { from: legacy ? [4, 20, 4, 12] : [20, 52, 4, 12], to: [8, 20], mirror: legacy }, // left leg
  ];
  const overlay = [{ from: [40, 8, 8, 8], to: [4, 0] }]; // hat
  if (!legacy) {
    overlay.push(
      { from: [20, 36, 8, 12], to: [4, 8] }, // jacket
      { from: [44, 36, arm, 12], to: [4 - arm, 8] }, // right sleeve
      { from: [52, 52, arm, 12], to: [12, 8] }, // left sleeve
      { from: [4, 36, 4, 12], to: [4, 20] }, // right trouser
      { from: [4, 52, 4, 12], to: [8, 20] }, // left trouser
    );
  }
  return { base, overlay };
}

const pixel = (png, x, y) => {
  const i = (png.width * y + x) << 2;
  return [png.data[i], png.data[i + 1], png.data[i + 2], png.data[i + 3]];
};

/** A slim (Alex) skin leaves the fourth column of the front of its arm empty. */
function isSlim(skin) {
  for (let y = 20; y < 32; y++) {
    if (pixel(skin, 47, y)[3] !== 0) {
      return false;
    }
  }
  return true;
}

export function render(file) {
  const skin = PNG.sync.read(readFileSync(file));
  const legacy = skin.height === 32;
  const slim = !legacy && isSlim(skin);
  const front = new PNG({ width: 16, height: 32 });
  const draw = ({ from: [fx, fy, w, h], to: [tx, ty], mirror }, blend) => {
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const [r, g, b, a] = pixel(skin, fx + (mirror ? w - 1 - x : x), fy + y);
        // the first layer is opaque in the game; the second shows only where it is drawn
        if (blend && a < 128) {
          continue;
        }
        const i = (16 * (ty + y) + tx + x) << 2;
        front.data[i] = r;
        front.data[i + 1] = g;
        front.data[i + 2] = b;
        front.data[i + 3] = blend ? 255 : a === 0 ? 0 : 255;
      }
    }
  };
  const { base, overlay } = parts(slim, legacy);
  base.forEach((part) => draw(part, false));
  overlay.forEach((part) => draw(part, true));
  const big = new PNG({ width: 16 * SCALE, height: 32 * SCALE });
  for (let y = 0; y < big.height; y++) {
    for (let x = 0; x < big.width; x++) {
      const from = (16 * Math.floor(y / SCALE) + Math.floor(x / SCALE)) << 2;
      const to = (big.width * y + x) << 2;
      front.data.copy(big.data, to, from, from + 4);
    }
  }
  return PNG.sync.write(big, { colorType: 6 });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  mkdirSync(path.join(OUT, '3d'), { recursive: true });
  const files = readdirSync(SKINS).filter((name) => name.endsWith('.png')).sort();
  for (const name of files) {
    writeFileSync(path.join(OUT, name), render(path.join(SKINS, name)));
    copyFileSync(path.join(SKINS, name), path.join(OUT, '3d', name));
  }
  console.log(`Rendered ${files.length} skins into ${path.relative(process.cwd(), OUT)}.`);
}
