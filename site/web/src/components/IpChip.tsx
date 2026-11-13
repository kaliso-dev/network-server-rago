'use client';

import { useEffect, useRef, useState } from 'react';

/**
 * The server address with its copy button. If the browser refuses the copy, the address is
 * selected so that the player copies it themselves: it stays readable either way.
 */
export function IpChip({ address }: { address: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'select'>('idle');
  const text = useRef<HTMLSpanElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(timer.current), []);

  const copy = async () => {
    clearTimeout(timer.current);
    try {
      await navigator.clipboard.writeText(address);
      setState('copied');
    } catch {
      const range = document.createRange();
      if (text.current) {
        range.selectNodeContents(text.current);
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
      }
      setState('select');
    }
    timer.current = setTimeout(() => setState('idle'), 2200);
  };

  const label = state === 'copied' ? 'Copied!' : state === 'select' ? 'Press Ctrl+C' : 'Copy IP';
  return (
    <div className="ip-chip">
      <span className="ip-chip__address" ref={text} translate="no">
        {address}
      </span>
      <button
        type="button"
        className={`ip-chip__copy${state === 'copied' ? ' is-copied' : ''}`}
        onClick={copy}
        // the name read out starts with the visible text, then says which address
        aria-label={`${label}, ${address}`}
      >
        {label}
      </button>
      <span className="visually-hidden" role="status" aria-live="polite">
        {state === 'copied' ? `${address} copied` : state === 'select' ? `${address} selected: copy it` : ''}
      </span>
    </div>
  );
}
