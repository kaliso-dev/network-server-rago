'use client';

import { useEffect, useState, type RefObject } from 'react';

/**
 * When a 3D view may start. three.js is the heaviest part of the site: it loads only when the
 * view is on the screen, once the page is idle on a computer, and on a phone or a tablet only
 * after the visitor has touched or scrolled the page, so that the first screen stays fast. Without
 * WebGL the picture stays as it is.
 */
export function useLazy3D(ref: RefObject<HTMLElement | null>): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const element = ref.current;
    if (!element || !webgl()) {
      return;
    }
    let visible = false;
    let allowed = false;
    const check = () => {
      if (visible && allowed) {
        setReady(true);
        cleanup();
      }
    };
    const observer = new IntersectionObserver(
      (entries) => {
        visible = entries.some((entry) => entry.isIntersecting);
        check();
      },
      { rootMargin: '200px' },
    );
    observer.observe(element);
    const events = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;
    const allow = () => {
      allowed = true;
      check();
    };
    let idle: number | undefined;
    const computer = window.matchMedia('(pointer: fine) and (min-width: 900px)').matches;
    if (computer) {
      const later = () => {
        idle = window.requestIdleCallback ? window.requestIdleCallback(allow, { timeout: 2500 }) : window.setTimeout(allow, 1200);
      };
      if (document.readyState === 'complete') {
        later();
      } else {
        window.addEventListener('load', later, { once: true });
      }
    }
    for (const name of events) {
      window.addEventListener(name, allow, { once: true, passive: true });
    }
    function cleanup() {
      observer.disconnect();
      for (const name of events) {
        window.removeEventListener(name, allow);
      }
      if (idle !== undefined) {
        (window.cancelIdleCallback ?? window.clearTimeout)(idle);
      }
    }
    return cleanup;
  }, [ref]);
  return ready;
}

let support: boolean | undefined;

/** Whether the browser can draw WebGL at all. */
export function webgl(): boolean {
  if (support === undefined) {
    try {
      const canvas = document.createElement('canvas');
      support = Boolean(canvas.getContext('webgl2') ?? canvas.getContext('webgl'));
    } catch {
      support = false;
    }
  }
  return support;
}

/** Whether the visitor asked for less motion: the 3D views then stand still. */
export const reducedMotion = (): boolean => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Runs while the element is on the screen and the tab is shown; returns a function that stops it. */
export function whileVisible(element: Element, onChange: (visible: boolean) => void): () => void {
  let onScreen = false;
  const update = () => onChange(onScreen && document.visibilityState === 'visible');
  const observer = new IntersectionObserver((entries) => {
    onScreen = entries.some((entry) => entry.isIntersecting);
    update();
  });
  observer.observe(element);
  document.addEventListener('visibilitychange', update);
  return () => {
    observer.disconnect();
    document.removeEventListener('visibilitychange', update);
  };
}
