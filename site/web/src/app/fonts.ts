import localFont from 'next/font/local';

/*
 * The three fonts of the site, all under the SIL Open Font License 1.1, served by the site itself
 * from the Fontsource packages: no call to Google Fonts, so no visitor's address goes to a third
 * party. Pixelify Sans and Tiny5 are pixel fonts of their own, not the font of Minecraft (Mojang
 * counts its fonts in its brand).
 */

/** The titles and the buttons. */
export const pixelify = localFont({
  src: '../../node_modules/@fontsource-variable/pixelify-sans/files/pixelify-sans-latin-wght-normal.woff2',
  weight: '400 700',
  variable: '--font-pixelify',
  display: 'swap',
  fallback: ['system-ui', 'sans-serif'],
});

/** The labels of the game: chat lines, the sidebar, tooltips, the server address. */
export const tiny5 = localFont({
  src: '../../node_modules/@fontsource/tiny5/files/tiny5-latin-400-normal.woff2',
  weight: '400',
  variable: '--font-tiny5',
  display: 'swap',
  fallback: ['ui-monospace', 'monospace'],
});

/** The text. */
export const inter = localFont({
  src: '../../node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2',
  weight: '100 900',
  variable: '--font-inter',
  display: 'swap',
  fallback: ['system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
});
