/**
 * Lighthouse on a phone, as doc 03 asks: at least 90 in performance and 95 in accessibility, on
 * the home page, the game modes, the page of a mode with its 3D character and a legal page. Run
 * after `npm run build:e2e`; CHROME_PATH names the browser.
 */
module.exports = {
  ci: {
    collect: {
      startServerCommand: 'RAGO_DIST_DIR=.next-soon npx next start -H 127.0.0.1 -p 3200',
      startServerReadyPattern: 'Ready',
      url: [
        'http://127.0.0.1:3200/',
        'http://127.0.0.1:3200/modes',
        'http://127.0.0.1:3200/modes/clutch-scenario',
        'http://127.0.0.1:3200/legal/privacy',
      ],
      numberOfRuns: 3,
      settings: {
        // the default of Lighthouse: a mid-range phone on a slow 4G network
        formFactor: 'mobile',
        chromeFlags: '--headless=new --no-sandbox',
      },
    },
    assert: {
      assertions: {
        'categories:performance': ['error', { minScore: 0.9, aggregationMethod: 'median-run' }],
        'categories:accessibility': ['error', { minScore: 0.95, aggregationMethod: 'median-run' }],
        'categories:best-practices': ['warn', { minScore: 0.9, aggregationMethod: 'median-run' }],
      },
    },
    upload: { target: 'filesystem', outputDir: '.lighthouseci/reports' },
  },
};
