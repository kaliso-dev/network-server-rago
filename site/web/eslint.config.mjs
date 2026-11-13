import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';

const config = [
  ...nextVitals,
  ...nextTypescript,
  {
    ignores: [
      '.next/**',
      '.next-*/**',
      'node_modules/**',
      'public/**',
      'test-results/**',
      'playwright-report/**',
      '.lighthouseci/**',
      'next-env.d.ts',
    ],
  },
];

export default config;
