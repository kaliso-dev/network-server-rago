import { expect, test } from '@playwright/test';
import { FLAGSHIP_PAGES, ONLINE, SOON, scrollThrough } from './helpers';

/** The widths of the brief: from the smallest phone to a large screen. */
const WIDTHS = [360, 414, 768, 1024, 1280, 1920];

test.describe('the layout', () => {
  // one browser is enough: the widths are set by hand
  test.skip(({ isMobile }) => isMobile, 'the widths are tested from the desktop project');

  const cases = [
    ...WIDTHS.flatMap((width) => [
      { path: '/', width, state: 'soon', base: SOON },
      { path: '/', width, state: 'online', base: ONLINE },
    ]),
    // the pages of the modes, at the smallest phone, a tablet and a laptop
    ...['/modes', ...FLAGSHIP_PAGES].flatMap((path) =>
      [360, 768, 1280].map((width) => ({ path, width, state: 'soon', base: SOON })),
    ),
  ];
  for (const { path, width, state, base } of cases) {
    test(`nothing overflows on ${path} at ${width} px (${state})`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.goto(base + path);
      await scrollThrough(page);
      const overflow = await page.evaluate(() => {
        const limit = document.documentElement.clientWidth;
        const wide = [...document.querySelectorAll<HTMLElement>('body *')]
          .filter((el) => {
            const box = el.getBoundingClientRect();
            return box.width > 0 && (box.right > limit + 1 || box.left < -1);
          })
          .map((el) => `${el.tagName.toLowerCase()}.${el.className}`);
        return { scroll: document.documentElement.scrollWidth - limit, wide: wide.slice(0, 10) };
      });
      expect(overflow.scroll, 'the page scrolls sideways').toBeLessThanOrEqual(0);
      expect(overflow.wide, 'elements past the edge').toEqual([]);
      // the header bar keeps one line, and no button breaks its label on two
      expect((await page.locator('body > header').boundingBox())?.height).toBeLessThanOrEqual(72);
      for (const button of await page.locator('.btn:visible').all()) {
        expect((await button.boundingBox())?.height, await button.innerText()).toBeLessThanOrEqual(60);
      }
    });
  }

  test('the hotbar stays at the foot of the screen and hides nothing at the end of the page', async ({ page }) => {
    await page.goto(SOON);
    const hotbar = page.getByRole('navigation', { name: 'Main' });
    const box = await hotbar.boundingBox();
    expect(box && box.y + box.height).toBeGreaterThan(800);
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    const notice = page.getByText('Rago Practice is not affiliated with Hypixel.', { exact: true });
    const [text, bar] = [await notice.boundingBox(), await hotbar.locator('ol').boundingBox()];
    expect(text && bar && text.y + text.height).toBeLessThanOrEqual(bar!.y);
  });

  test('the skins of the lobby characters load and keep their pixels', async ({ page }) => {
    await page.goto(SOON);
    await scrollThrough(page);
    // the four characters of the ways to play, the one of the private games, and the one of the hero
    // before its 3D loads
    const skins = page.locator('img[src^="/skins/"]');
    await expect(skins).toHaveCount(6);
    for (const skin of await skins.all()) {
      await skin.scrollIntoViewIfNeeded();
      await expect.poll(() => skin.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBe(192);
      expect(await skin.evaluate((img) => getComputedStyle(img).imageRendering)).toBe('pixelated');
    }
  });
});
