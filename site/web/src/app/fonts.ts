import localFont from 'next/font/local';

/*
 * The two fonts of the site, served by the site itself: no call to Google Fonts, so no visitor's
 * address goes to a third party.
 *
 * - The font of Minecraft 1.8.8, for everything the game would write: titles, buttons, menus,
 *   tooltips, the sidebar, the server address. scripts/mc-font.mjs traces it from the game's own
 *   font/ascii.png (npm run mc). Its glyphs are Mojang's, like the textures: whether the site may
 *   use them is to be settled before the opening (src/data/MINECRAFT-TEXTURES.md).
 * - Inter (SIL Open Font License 1.1, from Fontsource), for the paragraphs, to be read quickly.
 */

/** The font of the game: one pixel of it is an eighth of the font size. */
export const minecraft = localFont({
  src: [
    { path: '../../public/mc/font/minecraft.woff2', weight: '400', style: 'normal' },
    { path: '../../public/mc/font/minecraft-bold.woff2', weight: '700', style: 'normal' },
  ],
  variable: '--font-minecraft',
  display: 'swap',
  fallback: ['ui-monospace', 'monospace'],
  // the game's glyphs are its pixels: no size adjustment of the fallback, which would blur them
  adjustFontFallback: false,
});

/** The text. */
export const inter = localFont({
  src: '../../node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2',
  weight: '100 900',
  variable: '--font-inter',
  display: 'swap',
  fallback: ['system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
});
