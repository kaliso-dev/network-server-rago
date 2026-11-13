'use client';

import { useEffect, useRef, useState } from 'react';
import { McItem } from '@/components/McItem';
import { itemArt } from '@/lib/mc';
import { reducedMotion, useLazy3D, whileVisible } from './lazy';
import styles from './three.module.css';

/**
 * An item of the game in 3D, as it lies on the ground in the game: it turns one radian a second
 * and bobs every 3.14 seconds (RenderEntityItem of 1.8). With less motion asked for, it stands
 * still; until the view is ready, and without WebGL, the flat item is shown.
 */
export function ItemViewer({ material, label, className }: { material: string; label: string; className?: string }) {
  const frame = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const ready = useLazy3D(frame);
  const [shown, setShown] = useState(false);
  const scene = useRef<{ show: (material: string) => Promise<void> } | null>(null);

  // the scene, once
  useEffect(() => {
    if (!ready || !canvas.current || !frame.current) {
      return;
    }
    let disposed = false;
    let cleanup = () => {};
    (async () => {
      try {
        const [THREE, model] = await Promise.all([import('three'), import('./item-model')]);
        if (disposed || !canvas.current || !frame.current) {
          return;
        }
        const element = frame.current;
        const renderer = new THREE.WebGLRenderer({ canvas: canvas.current, alpha: true, antialias: true });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        const world = new THREE.Scene();
        // physical lights (three.js r155+): the intensities of the old ones, times pi
        world.add(new THREE.AmbientLight(0xffffff, 0.62 * Math.PI));
        const sun = new THREE.DirectionalLight(0xffffff, 0.55 * Math.PI);
        sun.position.set(0.6, 1, 1.2);
        world.add(sun);
        const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 50);
        camera.position.set(0, 0.32, 2.55);
        camera.lookAt(0, 0, 0);
        const holder = new THREE.Group();
        world.add(holder);
        let current: import('three').Object3D | undefined;
        const still = reducedMotion();

        const size = () => {
          const { width, height } = element.getBoundingClientRect();
          renderer.setSize(Math.max(1, width), Math.max(1, height), false);
          camera.aspect = Math.max(1, width) / Math.max(1, height);
          camera.updateProjectionMatrix();
        };
        size();
        const resize = new ResizeObserver(size);
        resize.observe(element);

        let running = false;
        let frameId = 0;
        const start = performance.now();
        const draw = () => {
          const t = (performance.now() - start) / 1000;
          holder.rotation.y = still ? -0.6 : t - 0.6;
          holder.position.y = still ? 0 : Math.sin(t * 2) * 0.06;
          renderer.render(world, camera);
          if (running && !still) {
            frameId = requestAnimationFrame(draw);
          }
        };
        const stop = whileVisible(element, (visible) => {
          running = visible;
          cancelAnimationFrame(frameId);
          if (visible) {
            draw();
          }
        });

        scene.current = {
          show: async (next: string) => {
            const object = await model.buildItem(THREE, itemArt(next));
            if (disposed) {
              model.disposeItem(object);
              return;
            }
            if (current) {
              holder.remove(current);
              model.disposeItem(current);
            }
            current = object;
            holder.add(object);
            draw();
          },
        };
        await scene.current.show(material);
        setShown(true);
        cleanup = () => {
          stop();
          resize.disconnect();
          cancelAnimationFrame(frameId);
          if (current) {
            model.disposeItem(current);
          }
          renderer.dispose();
        };
      } catch {
        setShown(false);
      }
    })();
    return () => {
      disposed = true;
      scene.current = null;
      cleanup();
    };
    // the item changes below, without a new scene
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  // a new item in the same scene
  useEffect(() => {
    scene.current?.show(material).catch(() => setShown(false));
  }, [material]);

  return (
    <div className={[styles.item, className].filter(Boolean).join(' ')} role="img" aria-label={label}>
      <div ref={frame} className={styles.stage}>
        <span className={styles.flat} data-hidden={shown || undefined}>
          <McItem material={material} size={128} />
        </span>
        <canvas ref={canvas} className={styles.canvas} data-shown={shown || undefined} aria-hidden="true" />
      </div>
    </div>
  );
}
