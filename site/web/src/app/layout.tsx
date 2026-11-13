import type { Metadata, Viewport } from 'next';
import type { ReactNode } from 'react';
import { Hotbar } from '@/components/Hotbar';
import { SiteFooter } from '@/components/SiteFooter';
import { SiteHeader } from '@/components/SiteHeader';
import { game } from '@/lib/game';
import { settings } from '@/lib/settings';
import { inter, minecraft } from './fonts';
import './globals.css';

const description = `A Minecraft ${settings.minecraftVersion} PvP practice network in Europe, built around block placement: ${
  game.categories.find((c) => c.id === 'entrainement')?.modes ?? 0
} trainings, modes made from real game situations, duels, arenas and Ranked Bedwars. Every mode is free.`;

export const metadata: Metadata = {
  metadataBase: settings.siteUrl,
  // the pages that may be indexed say so themselves; RAGO_SITE_INDEX keeps a temporary copy out
  robots: settings.index ? undefined : { index: false, follow: false },
  title: {
    default: 'Rago Practice: master your skills',
    template: '%s · Rago Practice',
  },
  description,
  applicationName: 'Rago Practice',
  openGraph: {
    type: 'website',
    siteName: 'Rago Practice',
    title: 'Rago Practice: master your skills',
    description,
    locale: 'en_GB',
  },
  twitter: { card: 'summary_large_image' },
  icons: {
    icon: [
      { url: '/brand/favicon-32.png', sizes: '32x32', type: 'image/png' },
      { url: '/brand/rago-logo-flat.svg', type: 'image/svg+xml' },
    ],
    apple: '/brand/apple-touch-icon.png',
  },
};

export const viewport: Viewport = {
  themeColor: '#0c0d10',
  colorScheme: 'dark',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" className={`${minecraft.variable} ${inter.variable}`}>
      <body>
        <SiteHeader />
        <main id="main" tabIndex={-1}>
          {children}
        </main>
        <SiteFooter />
        <Hotbar />
      </body>
    </html>
  );
}
