import { expect, test } from '@playwright/test';
import game from '../../src/data/game.json' with { type: 'json' };
import { BLOCK_TRAININGS } from '../../src/lib/block-trainings';
import { ONLINE, PAGES, SOON, scrollThrough, watch } from './helpers';

test.describe('the home page, before the opening', () => {
  test('renders its sections, with Discord first and no address', async ({ page }) => {
    await page.goto(SOON);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^Rago Practice\s*Master your skills\.$/);
    // practice first: the six modes, the block trainings, every other way to play, the private games,
    // the combat, and Ranked after
    const order = ['hero-title', 'modes', 'blocks', 'play', 'private', 'combat', 'ranked', 'cosmetics', 'support', 'community', 'join'];
    for (const id of order) {
      await expect(page.locator(`section[aria-labelledby="${id}"]`), id).toBeVisible();
    }
    const tops = await Promise.all(
      order.map((id) => page.locator(`section[aria-labelledby="${id}"]`).evaluate((el) => el.getBoundingClientRect().top)),
    );
    expect([...tops].sort((a, b) => a - b)).toEqual(tops);
    await expect(page.getByText('The server address comes with the opening.')).toBeVisible();
    await expect(page.getByRole('button', { name: /^Copy IP/ })).toHaveCount(0);
    await expect(page.getByText('Opening soon').first()).toBeVisible();
    await expect(page.getByText('The web store opens with the server.')).toBeVisible();
    // the store shows the ranks, without a buy button
    await expect(page.getByRole('heading', { name: 'VIP', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'PREMIUM+', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: /buy/i })).toHaveCount(0);
    await expect(page.getByRole('link', { name: /buy/i })).toHaveCount(0);
    // a rank supports the server: the page says so, in the words of the brief
    await expect(page.locator('section[aria-labelledby="support"]')).toContainText(
      'Purchasing a rank is an optional way to support the server',
    );
  });

  test('picks a mode of the six with its item, by click or with the arrows', async ({ page }) => {
    await page.goto(SOON);
    const tabs = page.getByRole('tablist', { name: 'The modes' });
    const panel = page.getByRole('tabpanel');
    await expect(tabs.getByRole('tab')).toHaveCount(6);
    await expect(tabs.getByRole('tab', { selected: true })).toHaveAccessibleName(/^Block placement/);
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText('Block placement');
    await tabs.getByRole('tab', { name: 'Block extend' }).click();
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText('Block extend');
    await expect(panel.getByRole('link', { name: 'Read more about Block extend' })).toHaveAttribute('href', '/modes/block-extend');
    await page.keyboard.press('ArrowDown');
    await expect(tabs.getByRole('tab', { selected: true })).toBeFocused();
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText('Block edit');
    await page.keyboard.press('Home');
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText('Block placement');
    await page.keyboard.press('ArrowUp');
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText('Attack and defense');
  });

  test('lists every training of block placement, then the duel that uses them', async ({ page }) => {
    await page.goto(SOON);
    const blocks = page.locator('section[aria-labelledby="blocks"]');
    await expect(blocks.locator('ul > li')).toHaveCount(BLOCK_TRAININGS.length);
    for (const id of BLOCK_TRAININGS) {
      const mode = game.modes.find((m) => m.id === id)!;
      await expect(blocks.getByRole('heading', { name: mode.name, exact: true })).toBeVisible();
    }
    await expect(blocks.getByRole('link', { name: /^How it plays\s?: Block placement$/ })).toHaveAttribute('href', '/modes/block-placement');
    await expect(blocks).toContainText(`the void is worth ${game.blockPlacement.voidBonus}`);
  });

  test('tells what a party plays in a private game, and that nothing counts', async ({ page }) => {
    await page.goto(SOON);
    const games = page.locator('section[aria-labelledby="private"]');
    for (const id of ['partie-privee', 'top-bridge-prive']) {
      const mode = game.modes.find((m) => m.id === id)!;
      await expect(games.getByRole('heading', { name: mode.name, exact: true })).toBeVisible();
    }
    await expect(games).toContainText(`Up to ${game.privateGames.partyMax} players`);
    await expect(games).toContainText('1v1, 2v2, 3v3 or 4v4');
    await expect(games).toContainText('A private game moves no ELO, gives no XP and counts in no stats.');
  });

  test('shows the tooltip of a cosmetic and its detail once picked', async ({ page }) => {
    await page.goto(SOON);
    const menus = page.locator('section[aria-labelledby="cosmetics"] .gui');
    await expect(menus).toHaveCount(3);
    const slot = menus.first().locator('button.slot').nth(1);
    await slot.hover();
    await expect(page.getByRole('tooltip')).toBeVisible();
    await slot.click();
    await expect(slot).toHaveAttribute('aria-pressed', 'true');
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
