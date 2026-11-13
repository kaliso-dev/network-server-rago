import path from 'node:path';
import type { NextConfig } from 'next';

// The design system (site/design) sits next to the app: the root of the build is site/.
const root = path.resolve(process.cwd(), '..');

const config: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // the end-to-end tests build the two states of the site side by side (scripts/build-e2e.mjs)
  distDir: process.env.RAGO_DIST_DIR || '.next',
  turbopack: { root },
  outputFileTracingRoot: root,
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
          { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
          {
            key: 'Content-Security-Policy',
            value: "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'",
          },
        ],
      },
    ];
  },
};

export default config;
