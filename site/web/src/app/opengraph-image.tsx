import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { ImageResponse } from 'next/og';
import { settings } from '@/lib/settings';

export const alt = 'Rago Practice: master your skills';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const dynamic = 'force-static';

/** The image shown when a link to the site is shared: the logo and the title of the hero. */
export default async function OpenGraphImage() {
  const root = process.cwd();
  const [display, logo] = await Promise.all([
    readFile(path.join(root, 'public/mc/font/minecraft-bold.ttf')),
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
          fontFamily: 'Minecraft',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 640 }}>
          <div style={{ fontSize: 32, color: '#ff8080', textShadow: '4px 4px 0 #3f1515' }}>RAGO PRACTICE</div>
          <div style={{ fontSize: 88, lineHeight: 1.125, textTransform: 'uppercase', textShadow: '11px 11px 0 #3f3f3f' }}>Master your skills.</div>
          <div style={{ fontSize: 32, color: '#a3a8b2', textShadow: '4px 4px 0 #292a2c' }}>
            {`Minecraft ${settings.minecraftVersion} PvP practice · Europe`}
          </div>
        </div>
        <img src={logoSrc} width={340} height={340} alt="" />
      </div>
    ),
    { ...size, fonts: [{ name: 'Minecraft', data: display, weight: 700, style: 'normal' }] },
  );
}
