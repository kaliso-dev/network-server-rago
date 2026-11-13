/**
 * The data of the game, imported from the files of the plugins by scripts/import-game-data.mjs
 * (src/data/game.json). Everything the site says about modes, divisions, ranks and prices comes
 * from here.
 */
import data from '@/data/game.json';

export interface Segment {
  text: string;
  colour: string | null;
  bold?: boolean;
}

export interface Category {
  id: string;
  name: string;
  colour: string | null;
  slogan: Segment[];
  description: string;
  skin: string | null;
  modes: number;
}

export interface Mode {
  id: string;
  name: string;
  category: string;
  group: string | null;
  format: string;
  ranked: boolean;
  startHere: boolean;
  event: boolean;
  description: string;
  plugins: string[];
}

export interface Division {
  name: string;
  colour: string | null;
  elo: number;
  win: number;
  loss: number;
  seasonReward: number;
}

export interface CosmeticRef {
  id: string;
  type: string;
  name: string;
}

export interface Rank {
  id: string;
  name: string;
  prefix: Segment[];
  colour: string | null;
  price: string;
  priceAmount: number;
  inherits: string | null;
  xpBoost: number;
  perks: string[];
  cosmetics: CosmeticRef[];
}

export interface GameData {
  server: { title: string; slogan: string };
  categories: Category[];
  groups: { id: string; name: string; description: string; modes: string[] }[];
  modes: Mode[];
  ranked: {
    queue: { startGap: number; gapStep: number; gapEverySeconds: number; maxGap: number; leavePenaltyMinutes: number };
    mvpBonus: number;
    divisions: Division[];
  };
  season: { number: number; name: string; end: string | null };
  ranks: Rank[];
  starterPack: {
    name: string;
    price: number;
    regularPrice: number;
    currency: string;
    ragoPoints: number;
    xpBoosters: number;
    pointsBoosters: number;
    cosmetics: CosmeticRef[];
  };
  cosmetics: { id: string; type: string; name: string; price: number; level: number; premium: boolean }[];
  ragoPoints: { win: number; loss: number; kill: number; daily: number };
  events: { automatic: boolean; everyMinutes: number | null; lobbyPlayers: number | null; maxPlayers: number | null };
  replays: { active: boolean; days: number | null };
  rules: string[];
}

export const game = data as GameData;

export const categoryOf = (id: string): Category => {
  const found = game.categories.find((category) => category.id === id);
  if (!found) {
    throw new Error(`no category ${id} in the game data`);
  }
  return found;
};

/** The modes of the selector: every mode but the events of /host, which have no queue. */
export const playableModes = (): Mode[] => game.modes.filter((mode) => !mode.event);

export const eventModes = (): Mode[] => game.modes.filter((mode) => mode.event);

/** 4.99 gives "4.99 €" and 5 gives "5.00 €", with a no-break space so the sign never wraps alone. */
export const formatEuros = (amount: number): string => `${amount.toFixed(2)}\u00a0€`;

/** 20000 gives "20,000". */
export const formatNumber = (value: number): string => value.toLocaleString('en-GB');
