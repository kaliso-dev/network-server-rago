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
  /** The item of the category in the compass (a Minecraft 1.8 item name). */
  icon: string | null;
  slogan: Segment[];
  description: string;
  skin: string | null;
  modes: number;
}

export interface Mode {
  id: string;
  name: string;
  colour: string | null;
  /** The item of the mode in the compass (a Minecraft 1.8 item name). */
  icon: string | null;
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

export interface Cosmetic {
  id: string;
  type: string;
  name: string;
  /** Its item in the menu of the cosmetics. */
  icon: string;
  price: number;
  level: number;
  premium: boolean;
}

/** A kit of Attack and Defense, chosen before each round. */
export interface RoundKit {
  name: string;
  icon: string;
  description: string;
}

/** A level of the combo bot (ComboAi.LEVELS). */
export interface BotLevel {
  id: string;
  name: string;
  cps: number;
  /** The chance that a click in reach lands, 0 to 1. */
  accuracy: number;
  /** How far it hits, in blocks. */
  reach: number;
  sprints: boolean;
  /** How much it strafes once close, 0 to 1. */
  strafe: number;
  /** The chance of a w-tap after one of its hits. */
  wTap: number;
  /** The chance of a jump reset when it is hit on the ground. */
  jumpReset: number;
  /** How many ticks behind the player it steers (a tick is 50 ms). */
  reactionTicks: number;
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
  cosmetics: Cosmetic[];
  ragoPoints: { win: number; loss: number; kill: number; daily: number };
  events: { automatic: boolean; everyMinutes: number | null; lobbyPlayers: number | null; maxPlayers: number | null };
  attackDefense: { protection: number; attack: RoundKit[]; defense: RoundKit[] };
  comboBot: { levels: BotLevel[] };
  /** The points (or hits) that win a duel, by kind of duel. */
  pointsToWin: Record<string, number>;
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

/** A mode by its id; throws if the game has no such mode, so that the site never names one. */
export const modeOf = (id: string): Mode => {
  const found = game.modes.find((mode) => mode.id === id);
  if (!found) {
    throw new Error(`no mode ${id} in the game data`);
  }
  return found;
};
