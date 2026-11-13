/**
 * The colours of the game on the dark grounds of the site. A colour of the game keeps its hue, and
 * is lightened just enough to be read (4.5:1, WCAG AA) where it is text, as design/README.md asks:
 * the dark grey of 1.8 (&8) and its black (&0) would not be read on charcoal.
 */

const PAGE_GROUND = '#0c0d10';

const channels = (hex: string): [number, number, number] => {
  const value = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16)) as [number, number, number];
};

const toHex = (rgb: number[]): string => '#' + rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');

/** Mixes a colour with another: 0 keeps the first, 1 gives the second. */
export const mix = (hex: string, other: string, amount: number): string => {
  const a = channels(hex);
  const b = channels(other);
  return toHex(a.map((v, i) => v + ((b[i] ?? 0) - v) * amount));
};

const luminance = (hex: string): number => {
  const [r, g, b] = channels(hex).map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

export const contrast = (a: string, b: string): number => {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (light + 0.05) / (dark + 0.05);
};

/** The colour, lightened until it reads on the ground (4.5:1 by default). */
export function readable(hex: string, ground: string = PAGE_GROUND, ratio = 4.5): string {
  let colour = hex;
  for (let step = 1; contrast(colour, ground) < ratio && step <= 20; step++) {
    colour = mix(hex, '#ffffff', step * 0.05);
  }
  return colour;
}
