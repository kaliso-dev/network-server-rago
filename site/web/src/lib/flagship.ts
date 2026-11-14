/**
 * The six modes the site puts first: the block placement duel and trainings, and the two moments of
 * a real Ranked Bedwars game that the server made into modes.
 * Every line here was read in the code of the plugins (the class named in `source`); the numbers
 * come from the game data, so that a change in the game changes the site. tests/unit/mc-art.test.ts
 * checks that every mode named exists and that the classes are still there.
 */
import { categoryOf, game, modeOf, type Mode } from './game';

export interface Flagship {
  /** The address of its page: /modes/<slug>. */
  slug: string;
  name: string;
  /** The modes of the game it covers, by id (game.json). */
  modes: string[];
  /** The item of the mode in the compass. */
  icon: string;
  /** The skin of its NPC in the lobby (public/skins), when it has one of its own. */
  skin: string | null;
  /** The category of the compass it is in, and the way there. */
  category: string;
  where: string;
  /** One line that says what the mode is. */
  hook: string;
  summary: string;
  /** How a game goes, step by step. */
  steps: { title: string; text: string }[];
  facts: { label: string; value: string }[];
  /** The class of the plugin it was read in. */
  source: string;
}

const formats = (ids: string[]): string => {
  const list = [...new Set(ids.map((id) => modeOf(id).format))];
  return list.length <= 1 ? (list[0] ?? '') : `${list.slice(0, -1).join(', ')} or ${list[list.length - 1]}`;
};

const ad = game.attackDefense;
const bp = game.blockPlacement;
const blockTarget = game.pointsToWin['block-fight'];
const editTarget = game.pointsToWin['block-edit'];
const { knockback, hits, presets } = bp.clutch;

const list = (names: string[]): string =>
  names.length <= 1 ? (names[0] ?? '') : `${names.slice(0, -1).join(', ')} or ${names[names.length - 1]}`;

export const FLAGSHIPS: Flagship[] = [
  {
    slug: 'block-placement',
    name: 'Block placement',
    modes: ['block-fight-1v1', 'block-fight-2v2', 'block-fight-arena'],
    icon: 'SANDSTONE',
    skin: 'blocs',
    category: 'duels',
    where: 'Compass, Duels, Block placement (the arena is in Arenas)',
    hook: `Every hit is a point. The void is worth ${bp.voidBonus}.`,
    summary: `Build, clutch, break their blocks and knock them off, on the block placement maps of the server. A hit does no damage, only its knockback: each one you land is a point, and each player you send into the void gives your side ${bp.voidBonus}.`,
    steps: [
      {
        title: 'The kit',
        text: 'A wooden sword, a pickaxe and an axe, shears, two stacks of wool, end stone and ladders, in Protection IV armour. Arrange it once in the kit editor.',
      },
      {
        title: 'Every hit is a point',
        text: 'A hit does no damage, only its knockback: each one you land on the other side scores. Your hearts show their score: full at 0, fewer as they near the win, and none comes back by itself.',
      },
      {
        title: 'The void',
        text: `Knock a player off and your side scores ${bp.voidBonus}. They are back on their gold block at once, with their kit whole again; the others fight on, and the blocks stay.`,
      },
      {
        title: blockTarget ? `First to ${blockTarget}` : 'The arena',
        text: `${blockTarget ? `The first side to ${blockTarget} points wins. ` : ''}In the arena, come and go when you like, with everyone: wool that never runs out, and blocks that crack and break after ${bp.arenaBlockSeconds} seconds.`,
      },
    ],
    facts: [
      { label: 'Formats', value: formats(['block-fight-1v1', 'block-fight-2v2', 'block-fight-arena']) },
      { label: 'A hit', value: 'A point, no damage' },
      { label: 'A fall', value: `${bp.voidBonus} points to the other side` },
      { label: 'To win', value: blockTarget ? `${blockTarget} points` : 'The most points' },
    ],
    source: 'BlockFight, BlockArena',
  },
  {
    slug: 'block-clutch',
    name: 'Block clutch',
    modes: ['clutch', 'clutch-top-fight'],
    icon: 'LADDER',
    skin: null,
    category: 'entrainement',
    where: 'Compass, Practice, Block clutch',
    hook: 'A hit throws you off. Your wool saves you.',
    summary:
      'You stand at the edge of a Bed Wars island, its bed and defense beside you, and a hit sends you over the void: place your wool and catch yourself. Each clutch is measured in blocks, and the biggest of the week are on a board everyone sees.',
    steps: [
      {
        title: 'Set the hit',
        text: `From the hotbar: knockback ${knockback.min} to ${knockback.max}, ${hits.min} to ${hits.max} hits in a row, a knockback stick, a random side. Or a preset: ${list(presets)}.`,
      },
      {
        title: 'Clutch',
        text: 'Place your wool before the void takes you. Saved, you are back at the start in under a second, and the next hit comes.',
      },
      {
        title: 'Measured',
        text: 'Each clutch has a size: how far the hit threw you before you caught yourself, in blocks.',
      },
      {
        title: 'The biggest of the week',
        text: 'The sidebar and a board above each island show the biggest clutches of the week. On Monday, the top 3 win Rago Points.',
      },
    ],
    facts: [
      { label: 'Players', value: 'Solo, the others clutching beside you' },
      { label: 'The hit', value: `Knockback ${knockback.min} to ${knockback.max}, ${hits.min} to ${hits.max} hits` },
      { label: 'Score', value: 'Your streak, and your biggest clutch' },
      { label: 'Where', value: 'A Bed Wars island, or a top fight' },
    ],
    source: 'ClutchTraining',
  },
  {
    slug: 'block-extend',
    name: 'Block extend',
    modes: ['block-extend'],
    icon: 'WOOL',
    skin: null,
    category: 'entrainement',
    where: 'Compass, Practice, Block extend',
    hook: 'Take the hit on the bridge. Turn, and save yourself.',
    summary:
      'As in a real bed fight: you stand at an exact place of a bridge, frozen until the hit lands, then turn and save yourself with your wool. Three levels, each with its own hit and its own way back.',
    steps: [
      {
        title: 'Frozen until the hit',
        text: 'You can neither move nor turn before it lands, and it always comes along the same line: as in a fight, you turn first.',
      },
      { title: 'Gold', text: 'A big, high hit from the front throws you straight back: clutch.' },
      { title: 'Diamond', text: 'A sprint hit from the right: turn to the bridge and extend along it.' },
      {
        title: 'Emerald',
        text: 'From the end of the bridge, a long, low hit from the front: clutch on the blocks below.',
      },
    ],
    facts: [
      { label: 'Players', value: 'Solo' },
      { label: 'Levels', value: 'Gold, Diamond, Emerald' },
      { label: 'Score', value: 'A streak for each level' },
      { label: 'The kit', value: 'Wool, and ladders if you like' },
    ],
    source: 'BlockExtendTraining',
  },
  {
    slug: 'block-edit',
    name: 'Block edit',
    modes: ['block-edit', 'block-edit-duel'],
    icon: 'SHEARS',
    skin: null,
    category: 'entrainement',
    where: 'Compass, Practice, Block edit (the 1v1 is in Duels)',
    hook: 'Open, hit, close. Before they hit back.',
    summary:
      'A wall of wool between you and the other side. Break a block, hit through the hole, and put the block back before the hit comes back.',
    steps: [
      { title: 'Open', text: 'Break a block of the wall of wool.' },
      { title: 'Hit', text: 'Hit through the hole: the bot, or the other player.' },
      { title: 'Close', text: 'Put a block back in the wall before they hit you.' },
      {
        title: 'Score',
        text: editTarget
          ? `Against the bot, each clean edit adds to your streak. In the 1v1, a clean edit is a point, and a hit back first takes it away: first to ${editTarget}.`
          : 'Against the bot, each clean edit adds to your streak. In the 1v1, a clean edit is a point, and a hit back first takes it away.',
      },
    ],
    facts: [
      { label: 'Formats', value: 'Solo against a bot, or 1v1' },
      { label: 'The bot', value: 'Hits from three blocks, once it sees the hole' },
      { label: 'Its speed', value: 'Set from the hotbar' },
      { label: 'Damage', value: 'None, only knockback' },
    ],
    source: 'BlockEditTraining, BlockEditDuel',
  },
  {
    slug: 'clutch-scenario',
    name: 'Clutch scenario',
    modes: ['clutch-scenario-1v1', 'clutch-scenario-2v2', 'clutch-scenario-3v3', 'clutch-scenario-4v4'],
    icon: 'IRON_CHESTPLATE',
    skin: 'clutch',
    category: 'ranked',
    where: 'Compass, RBW Modes, Clutch scenario',
    hook: 'A Ranked game at 18 minutes. One team has no bed left.',
    summary:
      'A real Ranked Bedwars game, already half played. One team, drawn at random, has lost its bed and each of its players has one life. The other keeps its bed under a defense already built. Clutch your way back, or close it out.',
    steps: [
      {
        title: 'The draw',
        text: 'The two teams start on opposite islands. One of them, drawn at random, has no bed: one life each.',
      },
      {
        title: 'A mid-game kit',
        text: 'Everyone in iron armour with a stone sword, no enchantment, no emerald, and every forge of the map full of iron and gold, the empty islands’ too.',
      },
      {
        title: 'A late-game clock',
        text: 'Diamond generators at Tier III, emeralds at Tier II. Emerald III comes next, then the end of the beds.',
      },
      {
        title: 'Then, a real game',
        text: 'The shop, the upgrades, the diamonds and emeralds, until one team is left.',
      },
    ],
    facts: [
      { label: 'Formats', value: formats(['clutch-scenario-1v1', 'clutch-scenario-2v2', 'clutch-scenario-3v3', 'clutch-scenario-4v4']) },
      { label: 'Maps', value: 'Any RBW map' },
      { label: 'Without a bed', value: 'One life each' },
      { label: 'With a bed', value: 'End stone, then wool' },
    ],
    source: 'ClutchScenarioGame',
  },
  {
    slug: 'attack-and-defense',
    name: 'Attack and defense',
    modes: ['attack-defense-1v1', 'attack-defense-2v2'],
    icon: 'IRON_SWORD',
    skin: 'attaque',
    category: 'ranked',
    where: 'Compass, RBW Modes, Attack and defense',
    hook: 'One bridge, one bed, one life to break it.',
    summary:
      'On Bio Hazard, the attackers come by the pink bridge with one life. The defenders hold their base with its forge and its item shop, and come back while their bed stands. The roles swap every round.',
    steps: [
      {
        title: 'Pick a kit',
        text: `Before each round, a menu shows the ${ad.attack.length} kits of your side. Choose, and the round starts once everyone has.`,
      },
      {
        title: 'Attack',
        text: 'From the end of the bridge, one life, iron armour and your kit: place blocks and throw fireballs as in a game.',
      },
      {
        title: 'Defend',
        text: 'The kit of a game’s start with your own, the forge and the shop, no upgrade. You come back while the bed stands.',
      },
      {
        title: 'Win the round',
        text: 'The attackers break the bed and kill every defender. The defenders kill every attacker, or hold five minutes.',
      },
    ],
    facts: [
      { label: 'Formats', value: formats(['attack-defense-1v1', 'attack-defense-2v2']) },
      { label: 'Rounds', value: 'First to 3, the roles swap' },
      { label: 'A round', value: '5 minutes at most' },
      { label: 'Armour', value: `Protection ${['0', 'I', 'II', 'III', 'IV'][ad.protection] ?? ad.protection} on both sides` },
    ],
    source: 'AttackDefenseGame',
  },
];

export const flagshipOf = (slug: string): Flagship | undefined => FLAGSHIPS.find((f) => f.slug === slug);

/** The colour of the category a flagship mode is in. */
export const flagshipColour = (flagship: Flagship): string => categoryOf(flagship.category).colour ?? '#FFFFFF';

/** The modes of the game it covers. */
export const flagshipModes = (flagship: Flagship): Mode[] => flagship.modes.map(modeOf);

/** Whether a mode of the game is one of the six. */
export const isFlagshipMode = (id: string): boolean => FLAGSHIPS.some((f) => f.modes.includes(id));
