import { expect, test } from '@playwright/test';
import { FLAGSHIP_PAGES, SOON } from './helpers';

test.describe('search engines and shared links', () => {
  test.skip(({ isMobile }) => isMobile, 'the same files for every screen');

  test('the home page has its title, description, share image and structured data', async ({ page }) => {
    await page.goto(SOON);
    await expect(page).toHaveTitle('Rago Practice: master your skills');
    await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /PvP practice/);
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, nofollow');
    const image = await page.locator('meta[property="og:image"]').getAttribute('content');
    expect(image).toBeTruthy();
    const types = await page
      .locator('script[type="application/ld+json"]')
      .evaluateAll((scripts) => scripts.flatMap((s) => [JSON.parse(s.textContent ?? '')].flat().map((d) => d['@type'])));
    expect(types).toEqual(expect.arrayContaining(['Organization', 'WebSite']));
  });

  test('each page of a mode has its own title, description and canonical address', async ({ page }) => {
    await page.goto(`${SOON}/modes`);
    await expect(page).toHaveTitle('Game modes · Rago Practice');
    for (const path of FLAGSHIP_PAGES) {
      await page.goto(SOON + path);
      const title = await page.title();
      expect(title, path).toMatch(/ · Rago Practice$/);
      await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /.{40,}/);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', new RegExp(`${path}$`));
    }
  });

  test('the share image is a 1200 x 630 PNG', async ({ request }) => {
    const response = await request.get(`${SOON}/opengraph-image`);
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toBe('image/png');
    const png = await response.body();
    expect(png.readUInt32BE(16)).toBe(1200);
    expect(png.readUInt32BE(20)).toBe(630);
  });

  test('robots.txt and the sitemap list the home page and keep the legal drafts out', async ({ request }) => {
    const robots = await (await request.get(`${SOON}/robots.txt`)).text();
    expect(robots).toContain('Sitemap: ');
    // the test builds do not set RAGO_SITE_INDEX: like any copy that is not the real site, they stay out
    expect(robots).toContain('Disallow: /');
    const sitemap = await (await request.get(`${SOON}/sitemap.xml`)).text();
    expect(sitemap).toContain('<loc>');
    for (const path of ['/modes', ...FLAGSHIP_PAGES]) {
      expect(sitemap).toContain(`${path}</loc>`);
    }
    expect(sitemap).not.toContain('/legal/');
  });

  test('the site sends its security headers', async ({ request }) => {
    const headers = (await request.get(SOON)).headers();
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
    expect(headers['x-powered-by']).toBeUndefined();
  });
});
