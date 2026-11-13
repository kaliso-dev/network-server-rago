import { expect, test } from '@playwright/test';
import game from '../../src/data/game.json' with { type: 'json' };
import { FLAGSHIP_PAGES, ONLINE, SOON } from './helpers';

const playable = game.modes.filter((mode) => !mode.event);

test.describe('the game modes page', () => {
  test('puts the six modes first, then each way to play in its inventory', async ({ page }) => {
    await page.goto(`${SOON}/modes`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Game modes');
    const six = page.locator('section[aria-labelledby="flagships"] li');
    await expect(six).toHaveCount(6);
    for (const path of FLAGSHIP_PAGES) {
      await expect(page.locator(`section[aria-labelledby="flagships"] a[href="${path}"]`)).toHaveCount(1);
    }
    for (const category of game.categories) {
      const section = page.locator(`section[aria-labelledby="${category.id}"]`);
      await expect(section.getByRole('heading', { level: 2 })).toHaveText(category.name);
      // one slot for each mode of the category that can be played, as the compass of the lobby lists them
      const count = playable.filter((mode) => mode.category === category.id).length;
      await expect(section.locator('button.slot')).toHaveCount(count);
    }
  });

  test('shows a mode once its item is picked', async ({ page }) => {
    await page.goto(`${SOON}/modes`);
    const duels = page.locator('section[aria-labelledby="duels"]');
    const modes = playable.filter((mode) => mode.category === 'duels');
    const [first, other] = [modes[0]!, modes[3]!];
    await expect(duels.getByRole('heading', { level: 3 })).toHaveText(first.name);
    const slot = duels.locator('button.slot').nth(3);
    await slot.click();
    await expect(slot).toHaveAttribute('aria-pressed', 'true');
    await expect(duels.getByRole('heading', { level: 3 })).toHaveText(other.name);
    await expect(duels).toContainText(other.description);
  });
});

test.describe('the page of each of the six', () => {
  for (const path of FLAGSHIP_PAGES) {
    test(`${path} says how a game goes and leads to the other five`, async ({ page }) => {
      await page.goto(SOON + path);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: 'Game modes' })).toBeVisible();
      await expect(page.locator('section[aria-labelledby="how"] li')).toHaveCount(4);
      const others = page.locator('section[aria-labelledby="others"] a');
      await expect(others).toHaveCount(5);
      await expect(page.locator(`section[aria-labelledby="others"] a[href="${path}"]`)).toHaveCount(0);
      // before the opening, the way in is the Discord; no address is made up
      await expect(page.locator('section[aria-labelledby="in-game"]').getByRole('link', { name: 'Join the Discord' })).toBeVisible();
    });
  }

  test('the page of the combo bot shows the levels the bot plays', async ({ page }) => {
    await page.goto(`${SOON}/modes/combo-bot`);
    const table = page.locator('section[aria-labelledby="levels"] table');
    const levels = game.comboBot.levels;
    await expect(table.locator('thead th')).toHaveText(levels.map((level) => level.name));
    await expect(table.getByRole('row', { name: /Clicks a second/ }).locator('td')).toHaveText(
      levels.map((level) => String(level.cps)),
    );
    await expect(table.getByRole('row', { name: /Reaction/ }).locator('td')).toHaveText(
      levels.map((level) => `${level.reactionTicks * 50} ms`),
    );
  });

  test('the page of Attack and Defense shows the kits of each side', async ({ page }) => {
    await page.goto(`${SOON}/modes/attack-and-defense`);
    const kits = page.locator('section[aria-labelledby="kits"]');
    await expect(kits.locator('.gui')).toHaveCount(2);
    const slots = game.attackDefense.attack.length + game.attackDefense.defense.length;
    await expect(kits.locator('button.slot')).toHaveCount(slots);
    const kit = game.attackDefense.defense[0]!;
    await expect(kits).toContainText(`${kit.name}: ${kit.description}`);
  });

  test('once the server is open, a page of a mode leads to the game', async ({ page }) => {
    await page.goto(`${ONLINE}/modes/kb-displace`);
    const play = page.locator('section[aria-labelledby="in-game"]');
    await expect(play.getByRole('link', { name: 'Play now' })).toBeVisible();
    await expect(play.getByText('play.example.com')).toBeVisible();
  });

  test('an unknown mode answers 404', async ({ page }) => {
    const response = await page.goto(`${SOON}/modes/no-such-mode`);
    expect(response?.status()).toBe(404);
  });
});
