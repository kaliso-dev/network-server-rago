'use client';

import { useId, useState, type CSSProperties, type ReactNode } from 'react';
import { McItem } from './McItem';
import styles from './ItemMenu.module.css';

export interface MenuItem {
  id: string;
  icon: string;
  name: string;
  /** The colour of its name, as the game writes it. */
  colour?: string | null;
  /** The lines under the name in its tooltip. */
  lore: string[];
  /** What shows under the menu once it is picked. */
  detail?: ReactNode;
}

/**
 * A menu of the game: the light grey inventory, its title and its slots. Hovering a slot shows the
 * item's tooltip, as in the game; picking one (a click, or Enter) shows its detail under the menu.
 */
export function ItemMenu({
  title,
  items,
  columns = 9,
  initial,
  label,
}: {
  title: string;
  items: MenuItem[];
  columns?: number;
  /** The item picked at first; none if left out. */
  initial?: string;
  /** What a screen reader hears of the slots: "Pick a cosmetic". */
  label: string;
}) {
  const [picked, setPicked] = useState<string | undefined>(initial);
  const [hovered, setHovered] = useState<string | null>(null);
  const id = useId();
  const current = items.find((item) => item.id === picked);
  const rows = Math.ceil(items.length / columns);
  const cells = rows * columns;

  return (
    <div className={styles.menu}>
      <div className="gui" style={{ '--columns': columns } as CSSProperties}>
        <p className="gui__title">{title}</p>
        <ul className="gui__slots" aria-label={label}>
          {Array.from({ length: cells }, (_, index) => {
            const item = items[index];
            if (!item) {
              return <li key={`empty-${index}`} className="slot" aria-hidden="true" />;
            }
            const tip = hovered === item.id;
            return (
              <li key={item.id} className={styles.cell} data-end={index % columns >= columns / 2 || undefined}>
                <button
                  type="button"
                  className="slot"
                  aria-pressed={item.id === picked}
                  aria-controls={item.detail ? `${id}-detail` : undefined}
                  aria-describedby={tip ? `${id}-tip-${item.id}` : undefined}
                  onClick={() => setPicked(item.id)}
                  onPointerEnter={() => setHovered(item.id)}
                  onPointerLeave={() => setHovered((h) => (h === item.id ? null : h))}
                  onFocus={() => setHovered(item.id)}
                  onBlur={() => setHovered((h) => (h === item.id ? null : h))}
                >
                  <McItem material={item.icon} size={32} />
                  <span className="visually-hidden">{item.name}</span>
                </button>
                {tip ? (
                  <div className={`mc-tooltip ${styles.tooltip}`} id={`${id}-tip-${item.id}`} role="tooltip">
                    <p className="mc-tooltip__name" style={{ color: item.colour ?? undefined }}>
                      {item.name}
                    </p>
                    {item.lore.map((line) => (
                      <p key={line}>{line}</p>
                    ))}
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      </div>
      {items.some((item) => item.detail) ? (
        <div className={styles.detail} id={`${id}-detail`} aria-live="polite">
          {current?.detail ?? null}
        </div>
      ) : null}
    </div>
  );
}
