import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { ImageResponse } from 'next/og';
import { settings } from '@/lib/settings';

export const alt = 'Rago Practice: Ranked Bedwars and practice';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const dynamic = 'force-static';

/** The image shown when a link to the site is shared: the logo and the line of the hero. */
export default async function OpenGraphImage() {
  const root = process.cwd();
  const [display, logo] = await Promise.all([
    readFile(path.join(root, 'node_modules/@fontsource/chakra-petch/files/chakra-petch-latin-700-normal.woff')),
    readFile(path.join(root, 'public/brand/icon-512.png')),
  ]);
  const logoSrc = `data:image/png;base64,${logo.toString('base64')}`;
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '72px 88px',
          background: 'radial-gradient(circle at 80% 40%, #5a0004 0%, #1a0203 38%, #0c0d10 70%)',
          color: '#ffffff',
          fontFamily: 'Chakra Petch',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 640 }}>
          <div style={{ fontSize: 28, letterSpacing: 6, color: '#ff8080' }}>RAGO PRACTICE</div>
          <div style={{ fontSize: 84, lineHeight: 0.95, textTransform: 'uppercase' }}>Master Ranked Bedwars.</div>
          <div style={{ fontSize: 30, color: '#a3a8b2' }}>
            {`Minecraft ${settings.minecraftVersion} · Ranked, duels and practice`}
          </div>
        </div>
        <img src={logoSrc} width={340} height={340} alt="" />
      </div>
    ),
    { ...size, fonts: [{ name: 'Chakra Petch', data: display, weight: 700, style: 'normal' }] },
  );
}
