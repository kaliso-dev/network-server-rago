/**
 * The six modes the site puts first: each puts the player in one precise moment of a real game.
 * Every line here was read in the code of the plugins (the class named in `source`); the numbers
 * come from the game data, so that a change in the game changes the site. tests/unit/flagship.test.ts
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
const levels = game.comboBot.levels;
const firstLevel = levels[0];
const lastLevel = levels[levels.length - 1];
const kbTarget = game.pointsToWin['kb-displace'];
const editTarget = game.pointsToWin['block-edit'];

export const FLAGSHIPS: Flagship[] = [
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
  {
    slug: 'invis-practice',
    name: 'Invis practice',
    modes: ['invis-practice'],
    icon: 'POTION',
    skin: 'invis',
    category: 'ranked',
    where: 'Compass, RBW Modes, Invis practice',
    hook: 'Find the invisible attacker before your bed falls.',
    summary:
      'Rounds of three minutes on the RBW maps. One player attacks from mid, invisible, without a name above the head. The others defend the bed and read the footprints and the swirls of the potion: a hit makes the attacker visible for the rest of the round.',
    steps: [
      {
        title: 'Attack, unseen',
        text: 'From mid, facing the bed, with an invisibility potion for the whole round, Jump V, a fireball, Bridge Eggs and wool.',
      },
      {
        title: 'Defend, by sight',
        text: 'Footprints on the ground and the swirls of the potion give the attacker away. One hit, and they are visible.',
      },
      {
        title: 'One life to break it',
        text: 'The attacker wins by breaking the bed. The defenders, by killing the attacker or holding three minutes; a defender is back three seconds after a death.',
      },
      {
        title: 'Everyone attacks',
        text: 'Each player attacks in turn. A bed broken or an attacker killed is a point: the most points win.',
      },
    ],
    facts: [
      { label: 'Players', value: '2 to 6' },
      { label: 'A round', value: '3 minutes' },
      { label: 'Maps', value: 'The 20 RBW maps' },
      { label: 'Defenders', value: 'Four at most' },
    ],
    source: 'InvisGame',
  },
  {
    slug: 'combo-bot',
    name: 'Combo bot',
    modes: ['combo-bot'],
    icon: 'SKULL_ITEM',
    skin: null,
    category: 'entrainement',
    where: 'Compass, Practice, Combo bot',
    hook: 'A bot that fights like a 1.8 player.',
    summary:
      'It sprints at you, strafes, w-taps and jump resets, with a player’s physics and a player’s knockback. The hits hurt no one, only their knockback counts: your score is your longest combo.',
    steps: [
      {
        title: 'Pick a level',
        text: firstLevel && lastLevel ? `${levels.map((level) => level.name).join(', ')}: from ${firstLevel.cps} to ${lastLevel.cps} clicks a second.` : '',
      },
      {
        title: 'It plays as a player',
        text: 'It sprints at you, strafes once close and steers towards where you were a few ticks ago: its reaction time.',
      },
      {
        title: 'Combo it',
        text: 'A combo is your hits in a row before it hits back, or two seconds without a hit.',
      },
      {
        title: 'Beat your best',
        text: 'Your longest combo is your score.',
      },
    ],
    facts: [
      { label: 'Players', value: 'Solo' },
      { label: 'Levels', value: String(levels.length) },
      { label: 'Damage', value: 'None, only knockback' },
      { label: 'Movement', value: 'A 1.8 player’s, tick by tick' },
    ],
    source: 'ComboBot, ComboAi',
  },
  {
    slug: 'kb-displace',
    name: 'KB displace',
    modes: ['kb-displace', 'kb-displace-bot'],
    icon: 'STICK',
    skin: null,
    category: 'duels',
    where: 'Compass, Duels, KB displace (the bot is in Practice)',
    hook: 'Turn your head in the tick of the hit.',
    summary:
      'A real Bed Fight top fight: a bridge one block wide, the defender on top of the defense. Run along the bridge and, in the tick of the hit, turn your head to one side: the knockback of the sprint sends them off sideways.',
    steps: [
      {
        title: 'Run the bridge',
        text: 'Invisible walls keep you in the middle of the bridge: the hit comes from the front, never from a side.',
      },
      {
        title: 'Turn in the tick',
        text: 'Turn your head to one side as you hit: the sprint pushes the defender off the top.',
      },
      {
        title: 'Only a fair hit counts',
        text: 'A hit with the head turned away from the defender, which only a cheat lands, does not count.',
      },
      {
        title: 'Swap',
        text: kbTarget
          ? `Each hit is one attempt and the roles swap, or after 15 seconds without one. First to ${kbTarget}.`
          : 'Each hit is one attempt and the roles swap, or after 15 seconds without one.',
      },
    ],
    facts: [
      { label: 'Formats', value: '1v1, or solo against a bot' },
      { label: 'An attempt', value: '15 seconds at most' },
      { label: 'Against the bot', value: 'Your time, and your tries' },
      { label: 'Damage', value: 'None, no blocks' },
    ],
    source: 'KbDisplace, KbDisplaceBot',
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
];

export const flagshipOf = (slug: string): Flagship | undefined => FLAGSHIPS.find((f) => f.slug === slug);

/** The colour of the category a flagship mode is in. */
export const flagshipColour = (flagship: Flagship): string => categoryOf(flagship.category).colour ?? '#FFFFFF';

/** The modes of the game it covers. */
export const flagshipModes = (flagship: Flagship): Mode[] => flagship.modes.map(modeOf);

/** Whether a mode of the game is one of the six. */
export const isFlagshipMode = (id: string): boolean => FLAGSHIPS.some((f) => f.modes.includes(id));
