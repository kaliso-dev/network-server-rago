/**
 * The font of Minecraft 1.8.8, as a web font: read from the game's own font/ascii.png, a grid of
 * 16 x 16 cells of 8 x 8 pixels, and measured as the game measures it (FontRenderer#readFontTexture):
 * a glyph is as wide as its last lit column, plus one pixel between two glyphs; the space is 4.
 * The bold is the game's too: the glyph drawn twice, one pixel apart, one pixel wider.
 *
 * Each pixel of a glyph is a square of the outline; the squares are joined into one contour per
 * shape, so that no seam shows between two pixels.
 */
import opentype from 'opentype.js';

/** The characters of ascii.png, cell by cell, as FontRenderer of 1.8.8 lists them (\0: none). */
export const CHARACTERS =
  'ÀÁÂÈÊËÍÓÔÕÚßãõğİ' +
  'ıŒœŞşŴŵžȇ\0\0\0\0\0\0\0' +
  ' !"#$%&\'()*+,-./' +
  '0123456789:;<=>?' +
  '@ABCDEFGHIJKLMNO' +
  'PQRSTUVWXYZ[\\]^_' +
  '`abcdefghijklmno' +
  'pqrstuvwxyz{|}~\0' +
  'ÇüéâäàåçêëèïîìÄÅ' +
  'ÉæÆôöòûùÿÖÜø£Ø×ƒ' +
  'áíóúñÑªº¿®¬½¼¡«»' +
  '░▒▓│┤╡╢╖╕╣║╗╝╜╛┐' +
  '└┴┬├─┼╞╟╚╔╩╦╠═╬╧' +
  '╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀' +
  'αβΓπΣσμτΦΘΩδ∞∅∈∩' +
  '≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\0';

/**
 * Characters the site writes that the grid lacks, drawn with the glyph the game's text would
 * use for them: the no-break space before a euro sign, the curly quotes of English, the dash.
 */
export const ALIASES = { ' ': ' ', '‘': "'", '’': "'", '“': '"', '”': '"', '–': '-' };

/** One pixel of the game, in font units: 8 pixels to the em. */
export const PIXEL = 128;
const CELL = 8;
/** The baseline lies under the seventh row; the eighth is for the tails of g, j, p, q, y. */
const BASELINE_ROW = 7;

/**
 * The glyphs of ascii.png (a decoded PNG: width, height, data in RGBA): for each character, its
 * lit pixels (x from the left, y from the top of its cell) and its width in pixels, spacing
 * included.
 */
export function glyphsOf(png) {
  if (png.width !== 128 || png.height !== 128) {
    throw new Error(`font/ascii.png should be 128 x 128, not ${png.width} x ${png.height}`);
  }
  const lit = (x, y) => png.data[(y * png.width + x) * 4 + 3] !== 0;
  const glyphs = new Map();
  [...CHARACTERS].forEach((char, index) => {
    if (char === '\0') {
      return;
    }
    const left = (index % 16) * CELL;
    const top = Math.floor(index / 16) * CELL;
    const pixels = [];
    let last = -1;
    for (let y = 0; y < CELL; y++) {
      for (let x = 0; x < CELL; x++) {
        if (lit(left + x, top + y)) {
          pixels.push([x, y]);
          last = Math.max(last, x);
        }
      }
    }
    // FontRenderer: the space is 3 + 1 wide; any other glyph, its last lit column + 1, + 1
    const width = char === ' ' ? 4 : last + 2;
    if (char === ' ' || pixels.length) {
      glyphs.set(char, { pixels, width });
    }
  });
  return glyphs;
}

/**
 * The game's bold: the glyph again, one pixel to the right, and one pixel more of width; the
 * space too is a pixel wider (FontRenderer#getStringWidth).
 */
export function bolden({ pixels, width }) {
  const set = new Set(pixels.map(([x, y]) => `${x},${y}`));
  for (const [x, y] of pixels) {
    set.add(`${x + 1},${y}`);
  }
  return { pixels: [...set].map((key) => key.split(',').map(Number)), width: width + 1 };
}

/**
 * The outline of a set of pixels: their outer edges, joined into closed contours that turn
 * clockwise around the ink (TrueType's direction), in font units with y up.
 */
export function contoursOf(pixels) {
  const filled = new Set(pixels.map(([x, y]) => `${x},${y}`));
  const has = (x, y) => filled.has(`${x},${y}`);
  // edges between a lit pixel and an unlit one, with the ink on their right; in grid units, y up
  const edges = new Map();
  const add = (from, to) => {
    const key = from.join(',');
    edges.set(key, [...(edges.get(key) ?? []), to]);
  };
  for (const [px, py] of pixels) {
    const x = px;
    const y = BASELINE_ROW - 1 - py; // the row above the baseline sits on y = 0
    if (!has(px, py - 1)) add([x, y + 1], [x + 1, y + 1]); // top
    if (!has(px + 1, py)) add([x + 1, y + 1], [x + 1, y]); // right
    if (!has(px, py + 1)) add([x + 1, y], [x, y]); // bottom
    if (!has(px - 1, py)) add([x, y], [x, y + 1]); // left
  }
  const contours = [];
  const take = (key, direction) => {
    const ends = edges.get(key);
    if (!ends?.length) {
      return undefined;
    }
    // where two pixels touch by a corner, turn right: each stays its own shape
    let pick = 0;
    if (ends.length > 1 && direction) {
      const [fx, fy] = key.split(',').map(Number);
      const right = [direction[1], -direction[0]];
      pick = Math.max(0, ends.findIndex(([tx, ty]) => tx - fx === right[0] && ty - fy === right[1]));
    }
    const [end] = ends.splice(pick, 1);
    if (!ends.length) {
      edges.delete(key);
    }
    return end;
  };
  while (edges.size) {
    const startKey = edges.keys().next().value;
    const start = startKey.split(',').map(Number);
    const points = [start];
    let key = startKey;
    let direction;
    for (;;) {
      const from = key.split(',').map(Number);
      const to = take(key, direction);
      if (!to) {
        break;
      }
      direction = [to[0] - from[0], to[1] - from[1]];
      key = to.join(',');
      if (key === startKey) {
        break;
      }
      points.push(to);
    }
    // only the corners: the points in the middle of a straight run go
    const corners = points.filter((point, i) => {
      const prev = points[(i - 1 + points.length) % points.length];
      const next = points[(i + 1) % points.length];
      return (prev[0] - point[0]) * (next[1] - point[1]) - (prev[1] - point[1]) * (next[0] - point[0]) !== 0;
    });
    contours.push(corners.map(([x, y]) => [x * PIXEL, y * PIXEL]));
  }
  return contours;
}

/** A TrueType font (an ArrayBuffer) of the glyphs of ascii.png, in the game's regular or bold. */
export function buildFont(png, { bold = false, familyName = 'Minecraft 1.8.8' } = {}) {
  const source = glyphsOf(png);
  const glyphs = [
    new opentype.Glyph({ name: '.notdef', unicode: undefined, advanceWidth: 6 * PIXEL, path: new opentype.Path() }),
  ];
  const draw = (char, glyph, unicode) => {
    const shape = bold ? bolden(glyph) : glyph;
    const outline = new opentype.Path();
    for (const contour of contoursOf(shape.pixels)) {
      contour.forEach(([x, y], i) => (i === 0 ? outline.moveTo(x, y) : outline.lineTo(x, y)));
      outline.close();
    }
    glyphs.push(
      new opentype.Glyph({
        name: `uni${unicode.toString(16).toUpperCase().padStart(4, '0')}`,
        unicode,
        advanceWidth: shape.width * PIXEL,
        path: outline,
      }),
    );
  };
  for (const [char, glyph] of source) {
    draw(char, glyph, char.codePointAt(0));
  }
  for (const [char, like] of Object.entries(ALIASES)) {
    const glyph = source.get(like);
    if (glyph && !source.has(char)) {
      draw(like, glyph, char.codePointAt(0));
    }
  }
  const font = new opentype.Font({
    familyName,
    styleName: bold ? 'Bold' : 'Regular',
    unitsPerEm: CELL * PIXEL,
    // a line of the game is 9 pixels: the 8 of the cell above the baseline's tail row, and 1
    ascender: CELL * PIXEL,
    descender: -PIXEL,
    designer: 'Mojang Studios (the glyphs of Minecraft 1.8.8)',
    description: 'The glyphs of font/ascii.png of Minecraft 1.8.8, traced pixel for pixel.',
    copyright: 'The glyphs are Mojang Studios’. Shown on the Rago Practice site pending review.',
    weightClass: bold ? 700 : 400,
    glyphs,
  });
  return font.toArrayBuffer();
}
