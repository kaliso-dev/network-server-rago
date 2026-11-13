import { mix } from '@/lib/colour';

/** The 8 x 8 icons drawn for the site (design/styleguide.html): a gem per division, a chest. */
const GRIDS = {
  gem: ['..XXXX..', '.XLLXXX.', 'XLLXXXXX', 'XLXXXXXD', 'XXXXXXXD', 'XXXXXDDD', '.XXXDDD.', '..DDDD..'],
  chest: ['.DDDDDD.', 'DLLLLLLD', 'DLMMMMLD', 'DDDGGDDD', 'DMMGGMMD', 'DMMMMMMD', 'DMMMMMMD', '.DDDDDD.'],
} as const;

const CHEST = { D: '#4A2F16', L: '#C08A4E', M: '#8B5A2B', G: '#FFD84D' };

/** The palette of a division's gem: its colour, a light side and a dark side. */
export function gemPalette(name: string, colour: string): Record<string, string> {
  // black and white do not show as gems on charcoal: Obsidian is a dark violet, Iron a light grey
  if (name === 'Obsidian') {
    return { X: '#2A2140', L: '#7A68A0', D: '#120D1C' };
  }
  const base = name === 'Iron' ? '#E6E6E6' : colour;
  return { X: base, L: mix(base, '#ffffff', 0.45), D: mix(base, '#000000', 0.35) };
}

export function PixelIcon({
  icon,
  palette,
  className,
  size = 40,
}: {
  icon: keyof typeof GRIDS;
  palette?: Record<string, string>;
  className?: string;
  size?: number;
}) {
  const colours: Record<string, string> = palette ?? (icon === 'chest' ? CHEST : {});
  // one path per colour, each row of the same colour drawn as one run: a few hundred bytes an icon
  const paths = new Map<string, string>();
  GRIDS[icon].forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      const cell = row[x] ?? '.';
      let end = x + 1;
      while (end < row.length && row[end] === cell) {
        end++;
      }
      const fill = colours[cell];
      if (fill) {
        paths.set(fill, `${paths.get(fill) ?? ''}M${x} ${y}h${end - x}v1h-${end - x}z`);
      }
      x = end;
    }
  });
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 8 8"
      shapeRendering="crispEdges"
      aria-hidden="true"
      focusable="false"
    >
      {[...paths].map(([fill, d]) => (
        <path key={fill} d={d} fill={fill} />
      ))}
    </svg>
  );
}
