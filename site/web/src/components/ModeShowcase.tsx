'use client';

import Link from 'next/link';
import { useId, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { readable } from '@/lib/colour';
import { McItem } from './McItem';
import { ItemViewer } from './three/ItemViewer';
import styles from './ModeShowcase.module.css';

export interface ShowcaseMode {
  slug: string;
  name: string;
  icon: string;
  colour: string;
  category: string;
  formats: string;
  hook: string;
  summary: string;
  steps: { title: string; text: string }[];
  where: string;
}

/**
 * The six modes the site puts first, one at a time: pick one by its item, as in the compass, and
 * it turns in 3D beside how a game goes. Tabs: the arrows move from one to the next.
 */
export function ModeShowcase({ modes }: { modes: ShowcaseMode[] }) {
  const [active, setActive] = useState(0);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const id = useId();
  const mode = modes[active] ?? modes[0];
  if (!mode) {
    return null;
  }

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, number> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
    let next: number | undefined;
    if (event.key in moves) {
      next = (active + (moves[event.key] ?? 0) + modes.length) % modes.length;
    } else if (event.key === 'Home') {
      next = 0;
    } else if (event.key === 'End') {
      next = modes.length - 1;
    }
    if (next !== undefined) {
      event.preventDefault();
      setActive(next);
      tabs.current[next]?.focus();
    }
  };

  const colour = readable(mode.colour, '#121418');
  return (
    <div className={styles.showcase} style={{ '--accent': colour } as CSSProperties}>
      <div className={styles.tabs} role="tablist" aria-label="The modes" aria-orientation="vertical" onKeyDown={onKeyDown}>
        {modes.map((item, index) => (
          <button
            key={item.slug}
            ref={(button) => {
              tabs.current[index] = button;
            }}
            type="button"
            role="tab"
            id={`${id}-tab-${item.slug}`}
            aria-selected={index === active}
            aria-controls={`${id}-panel`}
            tabIndex={index === active ? 0 : -1}
            className={styles.tab}
            onClick={() => setActive(index)}
            style={{ '--tab': readable(item.colour, '#121418') } as CSSProperties}
          >
            <span className="slot" aria-hidden="true">
              <McItem material={item.icon} size={32} />
            </span>
            <span className={styles.tabText}>
              <span className={styles.tabName}>{item.name}</span>{' '}
              <span className={styles.tabFormats}>{item.formats}</span>
            </span>
          </button>
        ))}
      </div>
      <div
        className={styles.panel}
        role="tabpanel"
        id={`${id}-panel`}
        aria-labelledby={`${id}-tab-${mode.slug}`}
        tabIndex={0}
      >
        <div className={styles.stage}>
          <ItemViewer material={mode.icon} label={`The item of ${mode.name} in the compass of the lobby`} className={styles.item} />
          <p className={styles.path}>
            <span className="mc-grey">{mode.where}</span>
          </p>
        </div>
        <div className={styles.copy}>
          <p className={styles.kicker}>
            <span>{mode.category}</span> · <span>{mode.formats}</span>
          </p>
          <h3 className={styles.name}>{mode.name}</h3>
          <p className={styles.hook}>{mode.hook}</p>
          <p className={styles.summary}>{mode.summary}</p>
          <ol className={styles.steps}>
            {mode.steps.map((step, index) => (
              <li key={step.title}>
                <span className={styles.stepNumber} aria-hidden="true">
                  {index + 1}
                </span>
                <h4>{step.title}</h4>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
          <Link className="btn btn--secondary btn--sm" href={`/modes/${mode.slug}`}>
            Read more<span className="visually-hidden"> about {mode.name}</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
