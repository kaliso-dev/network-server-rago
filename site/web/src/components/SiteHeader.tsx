import Image from 'next/image';
import Link from 'next/link';
import { settings } from '@/lib/settings';
import { ButtonLink } from './ui';
import styles from './SiteHeader.module.css';

/** The parts of the site, in the order of the hotbar (Hotbar.tsx), for the footer and the header. */
export const NAV = [
  { href: '/modes', label: 'Game modes' },
  { href: '/#join', label: 'How to join' },
  { href: '/#combat', label: 'Combat' },
  { href: '/#ranked', label: 'Ranked' },
  { href: '/#cosmetics', label: 'Cosmetics' },
  { href: '/#support', label: 'Support' },
  { href: '/#community', label: 'Community' },
];

/**
 * The bar at the top: the name of the server, the way to the modes and the main action. The menu
 * itself is the hotbar at the bottom of the screen.
 */
export function SiteHeader() {
  const action =
    settings.state === 'online' ? (
      <ButtonLink href="/#join" variant="primary" small>
        Play now
      </ButtonLink>
    ) : (
      <ButtonLink href={settings.discordUrl} variant="primary" small>
        Discord
      </ButtonLink>
    );
  return (
    <header className={styles.header}>
      <a className="skip-link" href="#main">
        Skip to the content
      </a>
      <div className={`container ${styles.bar}`}>
        <Link href="/" className={styles.brand} aria-label="Rago Practice, home">
          <Image src="/brand/rago-logo-flat.svg" alt="" width={32} height={32} priority />
          <span className={styles.wordmark}>
            Rago <span>Practice</span>
          </span>
        </Link>
        <nav className={styles.nav} aria-label="Shortcuts">
          <Link href="/modes">Game modes</Link>
        </nav>
        <div className={styles.actions}>{action}</div>
      </div>
    </header>
  );
}
