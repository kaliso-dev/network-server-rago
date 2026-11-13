import { expect, test } from '@playwright/test';
import game from '../../src/data/game.json' with { type: 'json' };
import { FLAGSHIP_PAGES, ONLINE, SOON } from './helpers';

const playable = game.modes.filter((mode) => !mode.event);

test.describe('the game modes page', () => {
  test('puts the six modes first, then lists every mode of each way to play', async ({ page }) => {
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
      // every mode of the category that can be played, by its name, as the compass of the lobby lists them
      const modes = playable.filter((mode) => mode.category === category.id);
      const list = section.getByRole('list', { name: `The modes of ${category.name}` });
      await expect(list.getByRole('listitem')).toHaveCount(modes.length);
      await expect(list.getByRole('heading', { level: 3 })).toHaveText(modes.map((mode) => mode.name));
    }
  });

  test('lists the events too, and leads from a mode of the six to its page', async ({ page }) => {
    await page.goto(`${SOON}/modes`);
    const events = page.locator('section[aria-labelledby="events"]');
    await expect(events.getByRole('heading', { level: 3 })).toHaveText(game.modes.filter((mode) => mode.event).map((mode) => mode.name));
    const duels = page.locator('section[aria-labelledby="duels"]');
    await expect(duels.getByRole('link', { name: /^How it plays\s?: Block placement$/ }).first()).toHaveAttribute('href', '/modes/block-placement');
    // every mode of the game is listed, once, under its way to play or in the events
    const lists = page.locator('section[aria-labelledby]:not([aria-labelledby="flagships"])');
    for (const mode of game.modes) {
      await expect(lists.getByRole('heading', { level: 3, name: mode.name, exact: true }), mode.id).toHaveCount(1);
    }
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

  test('the page of block placement gives the points of the game', async ({ page }) => {
    await page.goto(`${SOON}/modes/block-placement`);
    const facts = page.locator('section[aria-labelledby="mode-title"] dl');
    await expect(facts).toContainText(`${game.blockPlacement.voidBonus} points to the other side`);
    await expect(facts).toContainText(`${game.pointsToWin['block-fight']} points`);
    const compass = page.locator('section[aria-labelledby="in-game"]');
    await expect(compass.getByRole('heading', { level: 3 })).toHaveText(['Block placement 1v1', 'Block placement 2v2', 'Block placement arena']);
  });

  test('the page of the block clutch gives the settings of the hit', async ({ page }) => {
    await page.goto(`${SOON}/modes/block-clutch`);
    const { knockback, hits, presets } = game.blockPlacement.clutch;
    const how = page.locator('section[aria-labelledby="how"]');
    await expect(how).toContainText(`knockback ${knockback.min} to ${knockback.max}, ${hits.min} to ${hits.max} hits in a row`);
    for (const preset of presets) {
      await expect(how).toContainText(preset);
    }
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
    await page.goto(`${ONLINE}/modes/block-placement`);
    const play = page.locator('section[aria-labelledby="in-game"]');
    await expect(play.getByRole('link', { name: 'Play now' })).toBeVisible();
    await expect(play.getByText('play.example.com')).toBeVisible();
  });

  test('an unknown mode answers 404', async ({ page }) => {
    const response = await page.goto(`${SOON}/modes/no-such-mode`);
    expect(response?.status()).toBe(404);
  });
});
