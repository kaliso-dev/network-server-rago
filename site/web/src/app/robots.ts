import type { MetadataRoute } from 'next';
import { settings } from '@/lib/settings';

export const dynamic = 'force-static';

/** Search engines may come in only when RAGO_SITE_INDEX=yes: a temporary copy stays out of searches. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [settings.index ? { userAgent: '*', allow: '/' } : { userAgent: '*', disallow: '/' }],
    sitemap: new URL('/sitemap.xml', settings.siteUrl).toString(),
  };
}
