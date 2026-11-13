#!/usr/bin/env node
/**
 * Builds the site twice for the browser tests, side by side: the "soon" state (the defaults) in
 * .next-soon and the "online" state, with an example address, in .next-online. playwright.config.ts
 * serves each build on its own port.
 */
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

export const BUILDS = {
  soon: { RAGO_DIST_DIR: '.next-soon', RAGO_SITE_STATE: 'soon' },
  online: { RAGO_DIST_DIR: '.next-online', RAGO_SITE_STATE: 'online', RAGO_SERVER_ADDRESS: 'play.example.com' },
};

// next build adds the types of each build folder to tsconfig.json: they are put back as they were
const tsconfig = readFileSync('tsconfig.json', 'utf8');
process.on('exit', () => writeFileSync('tsconfig.json', tsconfig));

for (const [name, env] of Object.entries(BUILDS)) {
  console.log(`\n> next build (${name})`);
  const result = spawnSync('npx', ['next', 'build'], {
    stdio: 'inherit',
    env: { ...process.env, RAGO_SERVER_ADDRESS: '', ...env, NEXT_TELEMETRY_DISABLED: '1' },
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}
