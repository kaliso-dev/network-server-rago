import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { ONLINE, PAGES, SOON, scrollThrough } from './helpers';

test.describe('accessibility', () => {
  for (const [state, base] of [
    ['soon', SOON],
    ['online', ONLINE],
  ] as const) {
    for (const path of PAGES) {
      test(`axe finds nothing on ${path} (${state})`, async ({ page }) => {
        await page.goto(base + path);
        await scrollThrough(page);
        const results = await new AxeBuilder({ page })
          .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'])
          .analyze();
        const found = results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
        expect(found).toEqual([]);
      });
    }
  }

  test('the first Tab reaches the skip link, which leads to the content', async ({ page }) => {
    await page.goto(SOON);
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: 'Skip to the content' });
    await expect(skip).toBeFocused();
    await expect(skip).toBeInViewport();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/#main$/);
    await expect(page.locator('main')).toBeFocused();
  });

  test('every link and button is reached with Tab and shows its focus', async ({ page }) => {
    await page.goto(ONLINE);
    const reachable = await page
      .locator('a[href]:visible, button:visible, summary:visible')
      .evaluateAll((els) => els.filter((el) => el.getAttribute('tabindex') !== '-1').length);
    const seen = new Set<string>();
    for (let i = 0; i < reachable + 5; i++) {
      await page.keyboard.press('Tab');
      const focused = await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el || el === document.body) {
          return null;
        }
        const style = getComputedStyle(el);
        const ring = style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0;
        const path = [...document.querySelectorAll('a[href], button, summary')].indexOf(el);
        return { key: `${path} ${el.outerHTML.slice(0, 80)}`, ring: ring || style.boxShadow !== 'none' };
      });
      if (focused) {
        expect(focused.ring, `no focus ring on ${focused.key}`).toBe(true);
        seen.add(focused.key);
      }
    }
    expect(seen.size).toBeGreaterThanOrEqual(reachable);
  });

  test('the hotbar leads to the parts of the site, with the keys 1 to 9 once it has the focus', async ({ page }) => {
    await page.goto(SOON);
    const hotbar = page.getByRole('navigation', { name: 'Main' });
    await expect(hotbar.getByRole('link')).toHaveCount(7);
    // the keys do nothing while the focus is elsewhere: a player typing on the page is not sent away
    await page.keyboard.press('4');
    await expect(page).toHaveURL(`${SOON}/`);
    await hotbar.getByRole('link', { name: 'How to join' }).focus();
    await page.keyboard.press('1');
    await expect(page).toHaveURL(/\/modes$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Game modes');
    await expect(hotbar.getByRole('link', { name: 'Game modes' })).toHaveAttribute('aria-current', 'page');
  });

  for (const path of ['/', '/modes', '/modes/combo-bot']) {
    test(`nothing moves on ${path} when reduced motion is asked for`, async ({ browser }) => {
      const context = await browser.newContext({ reducedMotion: 'reduce' });
      const page = await context.newPage();
      await page.goto(SOON + path);
      const moving = await page.evaluate(() => {
        const scroll = getComputedStyle(document.documentElement).scrollBehavior;
        const animated = [...document.querySelectorAll<HTMLElement>('*')].filter((el) => {
          const style = getComputedStyle(el);
          return (
            (style.animationName !== 'none' && parseFloat(style.animationDuration) > 0) ||
            parseFloat(style.transitionDuration) > 0.01
          );
        });
        return { scroll, animated: animated.map((el) => el.className).slice(0, 5) };
      });
      expect(moving.scroll).toBe('auto');
      expect(moving.animated).toEqual([]);
      await context.close();
    });
  }
});
