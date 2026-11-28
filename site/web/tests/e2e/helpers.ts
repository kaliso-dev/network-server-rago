import type { Page } from '@playwright/test';

export const SOON = 'http://127.0.0.1:3100';
export const ONLINE = 'http://127.0.0.1:3101';

/** The pages of the six modes the site puts first (src/lib/flagship.ts). */
export const FLAGSHIP_PAGES = [
  '/modes/block-placement',
  '/modes/block-clutch',
  '/modes/block-extend',
  '/modes/block-edit',
  '/modes/clutch-scenario',
  '/modes/attack-and-defense',
];

/** The pages of the site, each built in both states. */
export const PAGES = [
  '/',
  '/modes',
  ...FLAGSHIP_PAGES,
  '/legal/notice',
  '/legal/terms',
  '/legal/sales',
  '/legal/privacy',
  '/legal/cookies',
];

/**
 * Records what a page does that it should not: errors in the console, uncaught exceptions, and
 * requests to any host but the site itself (no font, analytics or tracker from elsewhere).
 */
export function watch(page: Page) {
  const errors: string[] = [];
  const foreign: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      errors.push(`${message.type()}: ${message.text()}`);
    }
  });
  page.on('pageerror', (error) => errors.push(`exception: ${error.message}`));
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (!['127.0.0.1', 'localhost'].includes(url.hostname) && !url.protocol.startsWith('data')) {
      foreign.push(request.url());
    }
  });
  return { errors, foreign };
}

/**
 * Scrolls to the end and back, so that every lazy image of the page loads. The page scrolls
 * smoothly (globals.css): a smooth scroll every 30 ms would never get far, and the checks that
 * follow would measure the page on its way back up, under the sticky header. So the steps go at
 * once, and the page is back at its top before the checks.
 */
export async function scrollThrough(page: Page) {
  await page.evaluate(async () => {
    const root = document.documentElement;
    const behaviour = root.style.scrollBehavior;
    root.style.scrollBehavior = 'auto';
    const step = window.innerHeight / 2;
    for (let y = 0; y < document.body.scrollHeight; y += step) {
      window.scrollTo(0, y);
      await new Promise((resolve) => setTimeout(resolve, 30));
    }
    window.scrollTo(0, 0);
    await new Promise((resolve) => requestAnimationFrame(resolve));
    root.style.scrollBehavior = behaviour;
  });
  // what loaded on the way may move the page a little: back to the top until it stays there
  await page.waitForFunction(() => {
    if (window.scrollY !== 0) {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
    return window.scrollY === 0;
  });
}
