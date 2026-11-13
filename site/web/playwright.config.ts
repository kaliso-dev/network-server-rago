import { defineConfig, devices } from '@playwright/test';

/**
 * The browser tests run against the two production builds of `npm run build:e2e`: the "soon"
 * state on port 3100 and the "online" state on port 3101.
 */
export const SOON = 'http://127.0.0.1:3100';
export const ONLINE = 'http://127.0.0.1:3101';

const serve = (distDir: string, port: number) => ({
  command: `npx next start -H 127.0.0.1 -p ${port}`,
  url: `http://127.0.0.1:${port}`,
  env: { RAGO_DIST_DIR: distDir, NEXT_TELEMETRY_DISABLED: '1' },
  reuseExistingServer: !process.env.CI,
  timeout: 60_000,
});

export default defineConfig({
  testDir: 'tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['list']] : 'list',
  use: {
    baseURL: SOON,
    trace: 'off',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'phone', use: { ...devices['Pixel 7'] } },
  ],
  webServer: [serve('.next-soon', 3100), serve('.next-online', 3101)],
});
