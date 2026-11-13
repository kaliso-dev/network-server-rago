'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import { reducedMotion, useLazy3D, whileVisible } from './lazy';
import styles from './three.module.css';

/**
 * A player of the lobby in 3D, in their real skin (bukkit/rago-lobby/src/main/resources/skins):
 * it breathes as an idle player does, and the visitor turns it with the mouse or a finger. Until
 * the 3D view is ready, and without WebGL, the front of the same skin is shown, pixel for pixel.
 */
export function SkinViewer({
  skin,
  label,
  nametag,
  className,
  angle = -0.45,
}: {
  /** The name of the skin: public/skins/3d/<skin>.png, and public/skins/<skin>.png for its front. */
  skin: string;
  /** What the picture shows, for screen readers. */
  label: string;
  /** The lines above its head, as the NPC shows them in the lobby. */
  nametag?: ReactNode;
  className?: string;
  /** The turn of the body at the start, in radians: towards the text of the page. */
  angle?: number;
}) {
  const frame = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const ready = useLazy3D(frame);
  const [shown, setShown] = useState(false);

  useEffect(() => {
    if (!ready || !canvas.current || !frame.current) {
      return;
    }
    let disposed = false;
    let stop = () => {};
    let resize: ResizeObserver | undefined;
    let viewer: import('skinview3d').SkinViewer | undefined;
    (async () => {
      try {
        const { SkinViewer: Viewer, IdleAnimation } = await import('skinview3d');
        if (disposed || !canvas.current || !frame.current) {
          return;
        }
        const box = frame.current.getBoundingClientRect();
        viewer = new Viewer({
          canvas: canvas.current,
          width: Math.max(1, Math.round(box.width)),
          height: Math.max(1, Math.round(box.height)),
          fov: 38,
          zoom: 0.86,
          renderPaused: true,
        });
        await viewer.loadSkin(`/skins/3d/${skin}.png`);
        if (disposed) {
          viewer.dispose();
          return;
        }
        viewer.controls.enableZoom = false;
        viewer.controls.enablePan = false;
        viewer.playerWrapper.rotation.y = angle;
        if (!reducedMotion()) {
          viewer.animation = new IdleAnimation();
        }
        const current = viewer;
        resize = new ResizeObserver(([entry]) => {
          if (entry) {
            current.setSize(Math.max(1, Math.round(entry.contentRect.width)), Math.max(1, Math.round(entry.contentRect.height)));
          }
        });
        resize.observe(frame.current);
        stop = whileVisible(frame.current, (visible) => {
          current.renderPaused = !visible;
        });
        setShown(true);
      } catch {
        // no 3D: the front of the skin stays
        setShown(false);
      }
    })();
    return () => {
      disposed = true;
      stop();
      resize?.disconnect();
      viewer?.dispose();
    };
  }, [ready, skin, angle]);

  return (
    <div className={[styles.skin, className].filter(Boolean).join(' ')} role="img" aria-label={label}>
      {nametag ? <p className={styles.nametag} aria-hidden="true">{nametag}</p> : null}
      <div ref={frame} className={styles.stage}>
        {/* eslint-disable-next-line @next/next/no-img-element -- the front of the skin, drawn pixel for pixel */}
        <img
          className={styles.front}
          data-hidden={shown || undefined}
          src={`/skins/${skin}.png`}
          alt=""
          width={192}
          height={384}
          decoding="async"
        />
        <canvas ref={canvas} className={styles.canvas} data-shown={shown || undefined} aria-hidden="true" />
      </div>
    </div>
  );
}
