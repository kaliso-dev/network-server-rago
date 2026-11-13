/** What tests/unit/mc-font.test.ts reads of the two font packages, which ship no types. */
declare module 'opentype.js' {
  interface Glyph {
    index: number;
    advanceWidth?: number;
  }
  interface Font {
    unitsPerEm: number;
    names: Record<string, { en?: string } | undefined>;
    charToGlyph(char: string): Glyph;
  }
  const opentype: { parse(buffer: ArrayBuffer): Font };
  export default opentype;
}

declare module 'wawoff2' {
  const wawoff2: { decompress(woff2: Uint8Array): Promise<Uint8Array> };
  export default wawoff2;
}
