import type { Division } from '@/lib/game';
import { readable } from '@/lib/colour';
import { gemPalette, PixelIcon } from './PixelIcon';

/**
 * The 11 ranked divisions of the game (config.yml, elo:), from Coal to Obsidian, with their ELO.
 * Each name keeps its colour, lightened as needed to read on the ground it is drawn on.
 */
export function DivisionLadder({
  divisions,
  showGains = false,
  ground = '#0c0d10',
}: {
  divisions: Division[];
  showGains?: boolean;
  ground?: string;
}) {
  return (
    <ol className="divisions" aria-label="The ranked divisions, from the lowest">
      {divisions.map((division) => {
        const colour = division.colour ?? '#AAAAAA';
        const label = readable(division.name === 'Obsidian' ? '#7A68A0' : colour, ground);
        return (
          <li className="division" key={division.name}>
            <PixelIcon icon="gem" palette={gemPalette(division.name, colour)} className="division__icon" />
            <span className="division__name" style={{ color: label }}>
              {division.name}
            </span>
            <span className="division__elo">{division.elo}+ ELO</span>
            {showGains ? (
              <span className="division__gains">
                <span className="division__win">+{division.win}</span>{' '}
                <span className="division__loss">−{division.loss}</span>
              </span>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
