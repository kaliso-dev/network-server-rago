import localFont from 'next/font/local';

/*
 * The three fonts of the design system (all under the OFL), served by the site itself from the
 * Fontsource packages: no call to Google Fonts, so no visitor's address goes to a third party.
 */

export const chakraPetch = localFont({
  src: [
    { path: '../../node_modules/@fontsource/chakra-petch/files/chakra-petch-latin-600-normal.woff2', weight: '600' },
    { path: '../../node_modules/@fontsource/chakra-petch/files/chakra-petch-latin-700-normal.woff2', weight: '700' },
  ],
  variable: '--font-chakra-petch',
  display: 'swap',
  fallback: ['system-ui', 'sans-serif'],
});

export const inter = localFont({
  src: '../../node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2',
  weight: '100 900',
  variable: '--font-inter',
  display: 'swap',
  fallback: ['system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
});

export const jetbrainsMono = localFont({
  src: '../../node_modules/@fontsource-variable/jetbrains-mono/files/jetbrains-mono-latin-wght-normal.woff2',
  weight: '100 800',
  variable: '--font-jetbrains-mono',
  display: 'swap',
  preload: false,
  fallback: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
});
