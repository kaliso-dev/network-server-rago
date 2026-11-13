import type { Segment } from '@/lib/game';
import { readable } from '@/lib/colour';

/**
 * A text of the game in its colours (a slogan, a rank tag, a line of chat), each colour made
 * readable on the dark ground of the site.
 */
export function McText({ segments, ground }: { segments: Segment[]; ground?: string }) {
  return (
    <>
      {segments.map((segment, index) => (
        <span
          key={index}
          style={{
            color: segment.colour ? readable(segment.colour, ground) : undefined,
            fontWeight: segment.bold ? 700 : undefined,
          }}
        >
          {segment.text}
        </span>
      ))}
    </>
  );
}

/** A segment of the game's colours, written as in the plugins: "&a" green, "&7" grey. */
const CODES: Record<string, string> = {
  '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA', '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00',
  '7': '#AAAAAA', '8': '#555555', '9': '#5555FF', a: '#55FF55', b: '#55FFFF', c: '#FF5555', d: '#FF55FF',
  e: '#FFFF55', f: '#FFFFFF',
};

/** "&aClutch! &7Streak:" gives the segments of a line of chat, as the plugins write it. */
export function mc(text: string): Segment[] {
  const out: Segment[] = [];
  let colour: string | null = null;
  let bold = false;
  let buffer = '';
  const flush = () => {
    if (buffer) {
      out.push(bold ? { text: buffer, colour, bold } : { text: buffer, colour });
      buffer = '';
    }
  };
  for (let i = 0; i < text.length; i++) {
    const code = text[i + 1]?.toLowerCase();
    if (text[i] === '&' && code && /[0-9a-flr]/.test(code)) {
      flush();
      if (CODES[code]) {
        colour = CODES[code] ?? null;
        bold = false;
      } else if (code === 'l') {
        bold = true;
      } else {
        colour = null;
        bold = false;
      }
      i++;
      continue;
    }
    buffer += text[i];
  }
  flush();
  return out;
}
