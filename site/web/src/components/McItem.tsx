import type { CSSProperties } from 'react';
import { itemArt } from '@/lib/mc';

/** The faces of a head in the 64 x 16 strip of a skin, as background positions (8 pixels a face). */
const HEAD = {
  top: '14.2857% 0%', // x 8, y 0
  front: '14.2857% 100%', // x 8, y 8: the face
  side: '0% 100%', // x 0, y 8: the right of the head, on the left of the picture
};
const HAT = { top: '71.4286% 0%', front: '71.4286% 100%', side: '57.1429% 100%' };

/**
 * An item of the game as its inventory draws it: a sprite of 16 x 16 pixels, or a block (a cube
 * with its top and its sides, the sides darker) or a head. Decorative unless it is given a label.
 */
export function McItem({
  material,
  size = 32,
  label,
  className,
}: {
  material: string;
  /** In CSS pixels: 32 is two pixels of the screen for one of the texture. */
  size?: number;
  label?: string;
  className?: string;
}) {
  const art = itemArt(material);
  const style = { '--size': `${size}px` } as CSSProperties;
  const a11y = label ? { role: 'img', 'aria-label': label } : { 'aria-hidden': true as const };
  if (art.kind === 'item') {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- 16 x 16 sprites, drawn pixel for pixel
      <img
        src={art.src}
        alt={label ?? ''}
        width={size}
        height={size}
        className={['mc-item', className].filter(Boolean).join(' ')}
        style={style}
        loading="lazy"
        decoding="async"
      />
    );
  }
  const faces =
    art.kind === 'block'
      ? [
          { face: 'top', style: { backgroundImage: `url(${art.top})` } },
          { face: 'front', style: { backgroundImage: `url(${art.side})` } },
          { face: 'side', style: { backgroundImage: `url(${art.side})` } },
        ]
      : (['top', 'front', 'side'] as const).flatMap((face) => [
          { face, style: { backgroundImage: `url(${art.src})`, backgroundPosition: HEAD[face] } },
          { face, style: { backgroundImage: `url(${art.src})`, backgroundPosition: HAT[face], scale: '1.125' } },
        ]);
  return (
    <span
      className={['mc-item', 'mc-item--cube', art.kind === 'head' ? 'mc-item--head' : '', className].filter(Boolean).join(' ')}
      style={style}
      {...a11y}
    >
      <span className="mc-item__cube">
        {faces.map((f, i) => (
          <span key={i} className={`mc-item__face mc-item__face--${f.face}`} style={f.style as CSSProperties} />
        ))}
      </span>
    </span>
  );
}
