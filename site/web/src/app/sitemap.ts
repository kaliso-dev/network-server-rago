import type { MetadataRoute } from 'next';
import { settings } from '@/lib/settings';

export const dynamic = 'force-static';

/** The pages search engines should know. The legal pages wait for their texts. */
export default function sitemap(): MetadataRoute.Sitemap {
  return [{ url: new URL('/', settings.siteUrl).toString(), changeFrequency: 'weekly', priority: 1 }];
}
