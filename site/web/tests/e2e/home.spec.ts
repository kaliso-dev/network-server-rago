import { expect, test } from '@playwright/test';
import { ONLINE, PAGES, SOON, scrollThrough, watch } from './helpers';

test.describe('the home page, before the opening', () => {
  test('renders its sections, with Discord first and no address', async ({ page }) => {
    await page.goto(SOON);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Master Ranked Bedwars.');
    for (const id of ['join', 'train', 'modes', 'ranked', 'knockback', 'tools', 'store', 'community', 'finale']) {
      await expect(page.locator(`[aria-labelledby="${id}"]`), id).toBeVisible();
    }
    await expect(page.getByText('The server address comes with the opening.')).toBeVisible();
    await expect(page.getByRole('button', { name: /^Copy IP/ })).toHaveCount(0);
    await expect(page.getByText('Opening soon').first()).toBeVisible();
    await expect(page.getByText('The web store opens with the server.')).toBeVisible();
    // the store shows the ranks, without a buy button
    await expect(page.getByRole('heading', { name: 'VIP', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'PREMIUM+', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: /buy/i })).toHaveCount(0);
    await expect(page.getByRole('link', { name: /buy/i })).toHaveCount(0);
  });

  test('opens the Discord invite in a new tab', async ({ page }) => {
    await page.goto(SOON);
    const discord = page.locator('section[aria-labelledby="hero-title"]').getByRole('link', { name: /Discord/ });
    await expect(discord).toHaveAttribute('href', /^https:\/\/discord\.gg\//);
    await expect(discord).toHaveAttribute('target', '_blank');
    await expect(discord).toHaveAttribute('rel', /noopener/);
  });

  test('carries the Minecraft notice and says it is not affiliated with Hypixel', async ({ page }) => {
    await page.goto(SOON);
    const footer = page.locator('footer');
    await expect(footer).toContainText(
      'NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.',
    );
    await expect(footer).toContainText('Rago Practice is not affiliated with Hypixel.');
  });
});

test.describe('the home page, once the server is open', () => {
  test('shows the address and Play now', async ({ page }) => {
    await page.goto(ONLINE);
    await expect(page.getByText('Open now').first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'Play now' }).first()).toBeVisible();
    await expect(page.getByText('play.example.com').first()).toBeVisible();
    await expect(page.getByText('The server address comes with the opening.')).toHaveCount(0);
  });

  test('copies the address with the Copy IP button', async ({ page, context, browserName }) => {
    test.skip(browserName !== 'chromium', 'the clipboard permissions are those of Chromium');
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: ONLINE });
    await page.goto(ONLINE);
    await expect(page.getByRole('button', { name: 'Copy IP, play.example.com' }).first()).toBeVisible();
    // the name changes with the state: the button is held by its place
    const button = page.locator('.ip-chip__copy').first();
    await button.click();
    await expect(button).toHaveText(/^Copied!/);
    await expect(page.getByRole('status').first()).toHaveText('play.example.com copied');
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('play.example.com');
    // the button comes back
    await expect(button).toHaveText(/^Copy IP/, { timeout: 5000 });
  });
});

test.describe('every page', () => {
  for (const [state, base] of [
    ['soon', SOON],
    ['online', ONLINE],
  ] as const) {
    for (const path of PAGES) {
      test(`${path} (${state}) logs no error, asks nothing of another host, sets no cookie`, async ({
        page,
        context,
      }) => {
        const seen = watch(page);
        await page.goto(base + path);
        await scrollThrough(page);
        await page.waitForLoadState('networkidle');
        expect(seen.errors).toEqual([]);
        expect(seen.foreign).toEqual([]);
        expect(await context.cookies()).toEqual([]);
      });
    }
  }

  test('the legal pages say they are being reviewed and stay out of search engines', async ({ page }) => {
    for (const path of PAGES.filter((p) => p.startsWith('/legal/'))) {
      await page.goto(SOON + path);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expect(page.getByText('Being reviewed')).toBeVisible();
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/);
    }
  });

  test('an unknown page answers 404', async ({ page }) => {
    const response = await page.goto(`${SOON}/no-such-page`);
    expect(response?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});
