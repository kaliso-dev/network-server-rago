import { readFileSync } from 'node:fs';
import path from 'node:path';
import opentype from 'opentype.js';
import { describe, expect, it } from 'vitest';
import wawoff2 from 'wawoff2';
import { REPO } from '../../scripts/import-game-data.mjs';
import { ALIASES, CHARACTERS, PIXEL, bolden, contoursOf, glyphsOf } from '../../scripts/mc-font.mjs';

/**
 * The font of the site is the game's: traced from font/ascii.png of Minecraft 1.8.8
 * (scripts/mc-font.mjs, by npm run mc), as wide as the game draws each letter.
 */
const FONT = path.join(REPO, 'site/web/public/mc/font');

/** The signed area of a contour: below 0 when it turns clockwise, with y up. */
const area = (contour: number[][]) =>
  contour.reduce((sum, [x, y], i) => {
    const [nx, ny] = contour[(i + 1) % contour.length]!;
    return sum + (x! * ny! - nx! * y!);
  }, 0) / 2;

/** A blank ascii.png, with the given pixels lit in the cell of a character. */
const sheet = (lit: Record<string, number[][]>) => {
  const data = new Uint8Array(128 * 128 * 4);
  for (const [char, pixels] of Object.entries(lit)) {
    const index = [...CHARACTERS].indexOf(char);
    for (const [x, y] of pixels) {
      const at = ((Math.floor(index / 16) * 8 + y!) * 128 + (index % 16) * 8 + x!) * 4;
      data[at + 3] = 255;
    }
  }
  return { width: 128, height: 128, data };
};

const load = async (file: string) => {
  const ttf = await wawoff2.decompress(readFileSync(path.join(FONT, file)));
  return opentype.parse(new Uint8Array(ttf).buffer);
};

describe('the tracing of the font', () => {
  it('draws a pixel as one square, turning clockwise', () => {
    const contours = contoursOf([[0, 0]]);
    expect(contours).toHaveLength(1);
    expect(contours[0]).toHaveLength(4);
    expect(area(contours[0]!)).toBe(-PIXEL * PIXEL);
  });

  it('joins pixels side by side into one shape, with no seam', () => {
    const contours = contoursOf([
      [0, 0],
      [1, 0],
      [1, 1],
    ]);
    expect(contours).toHaveLength(1);
    expect(contours[0]).toHaveLength(6);
  });

  it('keeps two pixels that touch by a corner apart, and the hole of an o', () => {
    expect(contoursOf([[0, 0], [1, 1]])).toHaveLength(2);
    const ring = [0, 1, 2].flatMap((x) => [0, 1, 2].map((y) => [x, y])).filter(([x, y]) => x !== 1 || y !== 1);
    const [outer, hole] = contoursOf(ring);
    expect(area(outer!)).toBeLessThan(0);
    expect(area(hole!)).toBeGreaterThan(0);
  });

  it('measures each letter as the game does', () => {
    const glyphs = glyphsOf(sheet({ A: [[0, 0], [4, 6]], i: [[0, 2]] }));
    expect(glyphs.get('A')!.width).toBe(6); // its last lit column, and a pixel before the next
    expect(glyphs.get('i')!.width).toBe(2);
    expect(glyphs.get(' ')!.width).toBe(4);
    expect(glyphs.has('B')).toBe(false); // a cell with nothing lit has no glyph
    expect(bolden(glyphs.get('A')!).width).toBe(7);
    expect(bolden(glyphs.get(' ')!).width).toBe(5);
  });
});

describe('the font npm run mc built', () => {
  const printable = Array.from({ length: 0x7f - 0x20 }, (_, i) => String.fromCharCode(0x20 + i));
  const written = [...printable, '·', 'é', ...Object.keys(ALIASES)];

  it('has every letter the site writes, in the regular and the bold, in whole pixels of the game', async () => {
    // one after the other: the decoder of woff2 shares its memory between two calls
    const regular = await load('minecraft.woff2');
    const bold = await load('minecraft-bold.woff2');
    for (const font of [regular, bold]) {
      expect(font.unitsPerEm).toBe(8 * PIXEL);
      for (const char of written) {
        const glyph = font.charToGlyph(char);
        expect(glyph.index, `${JSON.stringify(char)} in ${font.names.fontSubfamily?.en}`).not.toBe(0);
        expect(glyph.advanceWidth! % PIXEL, char).toBe(0);
      }
    }
    for (const char of written) {
      expect(bold.charToGlyph(char).advanceWidth, char).toBe(regular.charToGlyph(char).advanceWidth! + PIXEL);
    }
    expect(regular.charToGlyph(' ').advanceWidth).toBe(4 * PIXEL);
    expect(regular.charToGlyph('A').advanceWidth).toBe(6 * PIXEL);
  });

  it('says whose glyphs they are', async () => {
    const font = await load('minecraft.woff2');
    expect(font.names.designer?.en).toContain('Mojang');
    expect(font.names.copyright?.en).toContain('Mojang');
  });
});
