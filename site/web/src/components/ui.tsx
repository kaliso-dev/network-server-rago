import Link from 'next/link';
import type { ReactNode } from 'react';
import type { SiteState } from '@/lib/settings';

type Variant = 'primary' | 'secondary' | 'ghost';

/**
 * A button that leads somewhere: "#modes" on the page, "/legal/..." on the site, or an address
 * outside it (the Discord invite), opened in a new tab.
 */
export function ButtonLink({
  href,
  children,
  variant = 'secondary',
  small = false,
  cut = false,
}: {
  href: string;
  children: ReactNode;
  variant?: Variant;
  small?: boolean;
  /** The cut corner of the hexagon: only on the main action of a screen. */
  cut?: boolean;
}) {
  const className = ['btn', `btn--${variant}`, small ? 'btn--sm' : '', cut ? 'cut' : ''].filter(Boolean).join(' ');
  if (/^https?:\/\//.test(href)) {
    return (
      <a className={className} href={href} target="_blank" rel="noopener noreferrer">
        {children}
        <span className="visually-hidden"> (opens in a new tab)</span>
      </a>
    );
  }
  if (href.startsWith('#')) {
    return (
      <a className={className} href={href}>
        {children}
      </a>
    );
  }
  return (
    <Link className={className} href={href}>
      {children}
    </Link>
  );
}

type TagVariant = 'red' | 'practice' | 'rbw' | 'duels' | 'arenas' | 'start';

export function Tag({ children, variant }: { children: ReactNode; variant?: TagVariant }) {
  return <span className={variant ? `tag tag--${variant}` : 'tag'}>{children}</span>;
}

/** The tag of a category of the game, in its colour (the compass of the lobby). */
export const CATEGORY_TAG: Record<string, TagVariant> = {
  entrainement: 'practice',
  ranked: 'rbw',
  duels: 'duels',
  arenes: 'arenas',
};

/** The state of the server. A player count is shown only once it comes from the server (later). */
export function ServerStatus({ state }: { state: SiteState }) {
  return state === 'online' ? (
    <span className="status status--online">Open now</span>
  ) : (
    <span className="status status--soon">Opening soon</span>
  );
}

/** The heading of a section: a small label above, the title, and a lead under it. */
export function SectionHeading({
  id,
  eyebrow,
  title,
  lead,
}: {
  id: string;
  eyebrow: string;
  title: ReactNode;
  lead?: ReactNode;
}) {
  return (
    <header className="section-heading">
      <p className="eyebrow">{eyebrow}</p>
      <h2 id={id}>{title}</h2>
      {lead ? <p className="lead">{lead}</p> : null}
    </header>
  );
}
