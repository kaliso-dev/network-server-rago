import Image from 'next/image';
import Link from 'next/link';
import { settings } from '@/lib/settings';
import { ButtonLink } from './ui';
import styles from './SiteHeader.module.css';

/** The sections of the home page; they become pages of their own later (doc 03, § 5.1). */
export const NAV = [
  { href: '/#join', label: 'Join' },
  { href: '/#modes', label: 'Modes' },
  { href: '/#ranked', label: 'Ranked' },
  { href: '/#store', label: 'Store' },
  { href: '/#community', label: 'Community' },
];

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
        <nav className={styles.nav} aria-label="Main">
          <ul>
            {NAV.map((item) => (
              <li key={item.href}>
                <a href={item.href}>{item.label}</a>
              </li>
            ))}
          </ul>
        </nav>
        <div className={styles.actions}>
          {action}
          <details className={styles.menu}>
            <summary aria-label="Menu">
              <span aria-hidden="true" className={styles.burger} />
            </summary>
            <nav aria-label="Main, on small screens">
              <ul>
                {NAV.map((item) => (
                  <li key={item.href}>
                    <a href={item.href}>{item.label}</a>
                  </li>
                ))}
              </ul>
            </nav>
          </details>
        </div>
      </div>
    </header>
  );
}
