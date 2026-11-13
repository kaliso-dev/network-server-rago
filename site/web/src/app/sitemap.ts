import type { MetadataRoute } from 'next';
import { FLAGSHIPS } from '@/lib/flagship';
import { settings } from '@/lib/settings';

export const dynamic = 'force-static';

/** The pages search engines should know. The legal pages wait for their texts. */
export default function sitemap(): MetadataRoute.Sitemap {
  const url = (path: string) => new URL(path, settings.siteUrl).toString();
  return [
    { url: url('/'), changeFrequency: 'weekly', priority: 1 },
    { url: url('/modes'), changeFrequency: 'weekly', priority: 0.9 },
    ...FLAGSHIPS.map((flagship) => ({ url: url(`/modes/${flagship.slug}`), changeFrequency: 'monthly' as const, priority: 0.8 })),
  ];
}
