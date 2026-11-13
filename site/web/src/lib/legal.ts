/**
 * The legal pages. Their texts are not written yet: each must be checked before the store opens
 * (doc 03, § 10), and the site says so instead of inventing one.
 */
export const LEGAL_PAGES = [
  { slug: 'notice', title: 'Legal notice', about: 'who publishes this site and who hosts it' },
  { slug: 'terms', title: 'Terms of use', about: 'the rules for using this site and the server' },
  { slug: 'sales', title: 'Terms of sale', about: 'what the store sells, how it is delivered and refunded' },
  { slug: 'privacy', title: 'Privacy', about: 'what data is kept, why, for how long, and your rights' },
  { slug: 'cookies', title: 'Cookies', about: 'what this site stores in your browser' },
] as const;

export type LegalSlug = (typeof LEGAL_PAGES)[number]['slug'];
