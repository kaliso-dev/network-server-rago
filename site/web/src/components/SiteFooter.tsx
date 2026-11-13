import Image from 'next/image';
import Link from 'next/link';
import { LEGAL_PAGES } from '@/lib/legal';
import { settings } from '@/lib/settings';
import { NAV } from './SiteHeader';
import styles from './SiteFooter.module.css';

/**
 * The foot of every page: the links, the legal pages, and the notices. The Minecraft one is the
 * wording of the Minecraft Usage Guidelines, word for word.
 */
export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={`container ${styles.grid}`}>
        <div className={styles.brand}>
          <Image src="/brand/rago-logo-flat.svg" alt="" width={40} height={40} />
          <p>
            <strong>Rago Practice</strong>
            <br />
            <span className="muted">Ranked Bedwars and practice, Minecraft {settings.minecraftVersion}.</span>
          </p>
        </div>
        <nav aria-label="The site">
          <h2 className={styles.title}>The site</h2>
          <ul>
            {NAV.map((item) => (
              <li key={item.href}>
                <a href={item.href}>{item.label}</a>
              </li>
            ))}
            <li>
              <a href={settings.discordUrl} target="_blank" rel="noopener noreferrer">
                Discord<span className="visually-hidden"> (opens in a new tab)</span>
              </a>
            </li>
          </ul>
        </nav>
        <nav aria-label="Legal">
          <h2 className={styles.title}>Legal</h2>
          <ul>
            {LEGAL_PAGES.map((page) => (
              <li key={page.slug}>
                <Link href={`/legal/${page.slug}`}>{page.title}</Link>
              </li>
            ))}
          </ul>
        </nav>
      </div>
      <div className={`container ${styles.notices}`}>
        <p className="notice">
          NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
        </p>
        <p className="notice">Rago Practice is not affiliated with Hypixel.</p>
      </div>
    </footer>
  );
}
