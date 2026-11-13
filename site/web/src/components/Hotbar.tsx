'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { gui } from '@/lib/mc';
import { McItem } from './McItem';
import styles from './Hotbar.module.css';

interface Slot {
  item: string;
  label: string;
  href: string;
  /** The sections of the home page that select this slot while they are on the screen. */
  sections: string[];
}

/**
 * The menu of the site is the hotbar of the lobby, at the bottom of the screen: the compass opens
 * the modes and the book tells how to join, in the slots they have in the game, the emerald is the
 * Rago Shop and the head the community. The slot of the part on the screen is selected, and its
 * name shows above the hotbar for two seconds, as in the game. With the focus in it, 1 to 9 pick a
 * slot and the arrows move between them.
 */
export const SLOTS: (Slot | null)[] = [
  { item: 'COMPASS', label: 'Game modes', href: '/modes', sections: ['modes', 'play'] },
  { item: 'BOOK', label: 'How to join', href: '/#join', sections: ['join'] },
  null,
  { item: 'DIAMOND_SWORD', label: 'Combat', href: '/#combat', sections: ['combat'] },
  { item: 'BED', label: 'Ranked', href: '/#ranked', sections: ['ranked'] },
  { item: 'FIREWORK', label: 'Cosmetics', href: '/#cosmetics', sections: ['cosmetics'] },
  null,
  { item: 'EMERALD', label: 'Support the server', href: '/#support', sections: ['support'] },
  { item: 'PLAYER_HEAD', label: 'Community', href: '/#community', sections: ['community'] },
];

export function Hotbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [spied, setSpied] = useState(0);
  // the slot whose name shows above the hotbar, for two seconds after it was selected
  const [flash, setFlash] = useState<{ index: number; at: number } | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const current = useRef(0);
  const links = useRef<(HTMLAnchorElement | null)[]>([]);
  const selected = pathname.startsWith('/modes') ? 0 : spied;

  const select = (index: number) => {
    if (index !== current.current) {
      current.current = index;
      setSpied(index);
      setFlash({ index, at: Date.now() });
    }
  };

  // the slot of the part of the page on the screen
  useEffect(() => {
    if (pathname !== '/') {
      return;
    }
    const sections = SLOTS.flatMap((slot, index) => (slot ? slot.sections.map((id) => ({ id, index })) : []));
    const targets = sections
      .map(({ id, index }) => ({ element: document.querySelector(`[aria-labelledby="${id}"]`), index }))
      .filter((t): t is { element: Element; index: number } => Boolean(t.element));
    const onScreen = new Map<Element, number>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            onScreen.set(entry.target, entry.boundingClientRect.top);
          } else {
            onScreen.delete(entry.target);
          }
        }
        const top = [...onScreen.entries()].sort((a, b) => a[1] - b[1])[0];
        const found = top ? targets.find((t) => t.element === top[0]) : undefined;
        select(found ? found.index : 0);
      },
      { rootMargin: '-45% 0px -50% 0px' },
    );
    for (const target of targets) {
      observer.observe(target.element);
    }
    return () => observer.disconnect();
  }, [pathname]);

  useEffect(() => {
    if (!flash) {
      return;
    }
    const timer = setTimeout(() => setFlash(null), 2000);
    return () => clearTimeout(timer);
  }, [flash]);

  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    const filled = SLOTS.map((slot, index) => (slot ? index : -1)).filter((index) => index >= 0);
    const at = links.current.findIndex((link) => link === document.activeElement);
    if (/^[1-9]$/.test(event.key)) {
      const slot = SLOTS[Number(event.key) - 1];
      if (slot) {
        event.preventDefault();
        links.current[Number(event.key) - 1]?.focus();
        router.push(slot.href);
      }
    } else if ((event.key === 'ArrowRight' || event.key === 'ArrowLeft') && at >= 0) {
      event.preventDefault();
      const position = filled.indexOf(at);
      const next = filled[(position + (event.key === 'ArrowRight' ? 1 : filled.length - 1)) % filled.length];
      if (next !== undefined) {
        links.current[next]?.focus();
      }
    }
  };

  const label = hovered !== null ? SLOTS[hovered]?.label : flash ? SLOTS[flash.index]?.label : null;
  return (
    <nav className={styles.hotbar} aria-label="Main" onKeyDown={onKeyDown}>
      <p className={styles.name} data-shown={label ? true : undefined} aria-hidden="true">
        {label ?? ' '}
      </p>
      <div className={styles.bar} style={{ backgroundImage: `url(${gui.hotbar})` } as CSSProperties}>
        <ol>
          {SLOTS.map((slot, index) =>
            slot ? (
              <li key={slot.item}>
                <Link
                  ref={(link) => {
                    links.current[index] = link;
                  }}
                  href={slot.href}
                  className={styles.slot}
                  aria-label={slot.label}
                  aria-current={slot.href === '/modes' && pathname.startsWith('/modes') ? 'page' : undefined}
                  onPointerEnter={() => setHovered(index)}
                  onPointerLeave={() => setHovered(null)}
                  onFocus={() => setHovered(index)}
                  onBlur={() => setHovered(null)}
                  onClick={() => select(index)}
                >
                  <McItem material={slot.item} size={32} />
                  <span className={styles.key} aria-hidden="true">
                    {index + 1}
                  </span>
                </Link>
              </li>
            ) : (
              <li key={`empty-${index}`} aria-hidden="true" />
            ),
          )}
        </ol>
        <span
          className={styles.selection}
          style={{ backgroundImage: `url(${gui.selection})`, '--at': selected } as CSSProperties}
          aria-hidden="true"
        />
      </div>
    </nav>
  );
}
