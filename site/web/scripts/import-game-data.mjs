#!/usr/bin/env node
/**
 * Imports what the website shows of the game: the categories and modes, the ranked divisions and
 * season, the ranks sold on the web store, the starter pack, the cosmetics, the events and the
 * rules. The sources are the files of the plugins, the lobby's config.yml first, so that the site
 * never says something the server does not do. The result, src/data/game.json, is committed: a
 * change of the game shows in the review of the site. `--check` fails when it is not up to date.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const REPO = path.resolve(HERE, '../../..');
export const OUTPUT = path.resolve(HERE, '../src/data/game.json');

/** The files read, from the root of the repository. */
export const SOURCES = {
  lobbyConfig: 'bukkit/rago-lobby/src/main/resources/config.yml',
  cosmetics: 'bukkit/rago-lobby/src/main/java/gg/rago/lobby/Cosmetic.java',
  bedwarsConfig: 'bukkit/rago-bedwars/src/main/resources/config.yml',
  practicePlugin: 'bukkit/rago-practice/src/main/java/gg/rago/practice/RagoPractice.java',
  practiceConfig: 'bukkit/rago-practice/src/main/resources/config.yml',
  sumoHost: 'bukkit/rago-practice/src/main/java/gg/rago/practice/SumoHost.java',
  eventGame: 'bukkit/rago-practice/src/main/java/gg/rago/practice/EventGame.java',
  gadgets: 'bukkit/rago-lobby/src/main/java/gg/rago/lobby/LobbyGadgets.java',
  blockFight: 'bukkit/rago-practice/src/main/java/gg/rago/practice/BlockFight.java',
  blockArena: 'bukkit/rago-practice/src/main/java/gg/rago/practice/BlockArena.java',
  blockClutch: 'bukkit/rago-practice/src/main/java/gg/rago/practice/ClutchTraining.java',
  timedBridge: 'bukkit/rago-practice/src/main/java/gg/rago/practice/BridgeTraining.java',
  parties: 'bukkit/rago-lobby/src/main/java/gg/rago/lobby/PartyBook.java',
  topBridge: 'bukkit/rago-bedwars/src/main/java/gg/rago/bedwars/TopBridgeGame.java',
};

/** The 16 colours of Minecraft 1.8, by their code (&0 to &f). */
export const COLOURS = {
  0: '#000000', 1: '#0000AA', 2: '#00AA00', 3: '#00AAAA', 4: '#AA0000', 5: '#AA00AA', 6: '#FFAA00', 7: '#AAAAAA',
  8: '#555555', 9: '#5555FF', a: '#55FF55', b: '#55FFFF', c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF',
};

/**
 * A text of the game cut where its colour changes: "&bTrain alone&7, at your &bown pace" gives
 * three segments. As in 1.8, a colour code also ends the bold.
 */
export function segments(text) {
  const out = [];
  let colour = null;
  let bold = false;
  let buffer = '';
  const flush = () => {
    if (buffer) {
      out.push(bold ? { text: buffer, colour, bold: true } : { text: buffer, colour });
      buffer = '';
    }
  };
  for (let i = 0; i < text.length; i++) {
    const code = text[i + 1]?.toLowerCase();
    if ((text[i] === '&' || text[i] === '§') && code && /[0-9a-fk-or]/.test(code)) {
      flush();
      if (COLOURS[code]) {
        colour = COLOURS[code];
        bold = false;
      } else if (code === 'l') {
        bold = true;
      } else if (code === 'r') {
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

/** The text without its colour codes. */
export const plain = (text) => segments(text).map((s) => s.text).join('');

/** The lines of an item's lore, as one paragraph. */
const paragraph = (lines = []) => lines.map(plain).join(' ').replace(/\s+/g, ' ').trim();

/** The perks of a rank: the lore cuts a long one in two lines, the first ending with a comma. */
function perks(lines = []) {
  const out = [];
  for (const line of lines.map(plain)) {
    if (out.length > 0 && out[out.length - 1].endsWith(',')) {
      out[out.length - 1] += ' ' + line;
    } else {
      out.push(line);
    }
  }
  return out;
}

/** The first colour of a text of the game ("&b", "&6&lRBW Modes"), or null. */
const colourOf = (text) => {
  const code = /[&§]([0-9a-f])/i.exec(String(text ?? ''));
  return code ? COLOURS[code[1].toLowerCase()] : null;
};

const read = (file) => readFileSync(path.join(REPO, file), 'utf8');

/** The first group of a pattern in a file of the game; throws if the code no longer has it. */
function found(source, pattern) {
  const match = pattern.exec(read(SOURCES[source]));
  if (!match) {
    throw new Error(`no ${pattern} in ${SOURCES[source]}`);
  }
  return match[1];
}

const number = (source, pattern) => Number(found(source, pattern));

/** A setting of a training, setting("Knockback", 1, 5, 3): its lowest and highest values. */
function settingRange(source, name) {
  const match = new RegExp(`setting\\("${name}", (\\d+), (\\d+), \\d+\\)`).exec(read(SOURCES[source]));
  if (!match) {
    throw new Error(`no setting ${name} in ${SOURCES[source]}`);
  }
  return { min: Number(match[1]), max: Number(match[2]) };
}

/** "4.99€" gives 4.99. */
function amount(price) {
  const match = /(\d+(?:[.,]\d+)?)/.exec(String(price));
  if (!match) {
    throw new Error(`no amount in the price "${price}"`);
  }
  return Number(match[1].replace(',', '.'));
}

/** The modes each plugin runs: the Bed Wars plugin by its config, the practice plugin by its code. */
function plugins(lobby) {
  const bedwars = new Set(Object.keys(parse(read(SOURCES.bedwarsConfig)).modes ?? {}));
  const practice = new Set([...read(SOURCES.practicePlugin).matchAll(/register\("([a-z0-9-]+)"/g)].map((m) => m[1]));
  const sumo = /MODE_ID\s*=\s*"([a-z0-9-]+)"/.exec(read(SOURCES.sumoHost));
  if (sumo) {
    practice.add(sumo[1]);
  }
  for (const kind of read(SOURCES.eventGame).matchAll(/^\s*[A-Z_]+\("([a-z0-9-]+)",/gm)) {
    practice.add('event-' + kind[1]);
  }
  const of = (id) => {
    const found = [];
    if (bedwars.has(id)) found.push('bedwars');
    if (practice.has(id)) found.push('practice');
    return found;
  };
  // a private game plays the modes of its list (parties-privees), with the party
  const privateGames = lobby['parties-privees'] ?? {};
  return (id) => {
    if (privateGames[id]) {
      return [...new Set(privateGames[id].flatMap(of))];
    }
    return of(id);
  };
}

/**
 * The format shown for a mode: its own (format:, 1v3 for one attacker against three), else 1v1 to
 * 4v4 for two teams; else solo, or free for all in the arenas.
 */
function format(mode) {
  if (mode.format) {
    return String(mode.format);
  }
  const teams = mode.equipes ?? 1;
  const size = mode['joueurs-par-equipe'] ?? 1;
  if (teams === 2) {
    return `${size}v${size}`;
  }
  if (teams > 2) {
    return Array(teams).fill(size).join('v');
  }
  return mode.categorie === 'arenes' || mode.cache ? 'FFA' : 'Solo';
}

function cosmetics(lobby) {
  const overrides = lobby.boutique ?? {};
  const found = [];
  const pattern =
    /new Cosmetic\("([a-z0-9-]+)", Type\.(TRAIL|KILL|WIN), "([^"]+)", Material\.(\w+), Effect\.\w+, ([\d_]+), (\d+)(, true)?\)/g;
  for (const [, id, type, name, icon, price, level, premium] of read(SOURCES.cosmetics).matchAll(pattern)) {
    const override = overrides[id] ?? {};
    found.push({
      id,
      type: type.toLowerCase(),
      name,
      icon,
      price: override.prix ?? Number(price.replace(/_/g, '')),
      level: override.niveau ?? Number(level),
      premium: Boolean(premium),
    });
  }
  if (found.length === 0) {
    throw new Error(`no cosmetic found in ${SOURCES.cosmetics}`);
  }
  return found;
}

export function buildGameData() {
  const lobby = parse(read(SOURCES.lobbyConfig));
  const practiceConfig = parse(read(SOURCES.practiceConfig));
  const pluginsOf = plugins(lobby);
  const catalog = cosmetics(lobby);
  const cosmetic = (id) => {
    const found = catalog.find((c) => c.id === id);
    if (!found) {
      throw new Error(`no cosmetic ${id} in ${SOURCES.cosmetics}`);
    }
    return { id: found.id, type: found.type, name: found.name };
  };

  // a mode whose kit others play (kit-de:) only holds it: not a mode of its own on the site
  const kitHolders = new Set(Object.values(lobby.modes).map((mode) => mode['kit-de']).filter(Boolean));
  const modes = Object.entries(lobby.modes).filter(([id]) => !kitHolders.has(id)).map(([id, mode]) => ({
    id,
    name: plain(mode.nom),
    colour: colourOf(mode.nom),
    icon: mode.icone ?? null,
    category: mode.categorie,
    group: mode.groupe ?? null,
    format: format(mode),
    ranked: mode.classe === true,
    startHere: mode['commencer-ici'] === true,
    event: mode.cache === true,
    description: paragraph(mode.description),
    plugins: pluginsOf(id),
  }));

  // the NPCs of the ways to play wear the skin of their category
  const npcs = Object.values(lobby.pnj ?? {});
  const categories = Object.entries(lobby.categories).map(([id, category]) => ({
    id,
    name: plain(category.nom),
    colour: colourOf(category.nom),
    icon: category.icone ?? null,
    slogan: segments(category.slogan),
    description: paragraph(category.description),
    skin: npcs.find((npc) => npc.categorie === id && !npc.type)?.skin ?? null,
    modes: modes.filter((mode) => mode.category === id && !mode.event).length,
  }));

  const groups = Object.entries(lobby.groupes).map(([id, group]) => ({
    id,
    name: plain(group.nom),
    description: paragraph(group.description),
    modes: modes.filter((mode) => mode.group === id).map((mode) => mode.id),
  }));

  const rewards = lobby.saison?.recompenses ?? {};
  const divisions = Object.entries(lobby.elo.divisions).map(([name, division]) => ({
    name,
    colour: colourOf(division.couleur),
    elo: division.elo,
    win: division.victoire,
    loss: division.defaite,
    seasonReward: rewards[name] ?? 0,
  }));

  // The cosmetics a rank comes with: those of its permissions (rago.cosmetic.<id>), the rarest
  // (premium in Cosmetic.java) with the permission of LobbyGadgets, and those of the ranks it inherits.
  const premiumPermission = /PREMIUM_PERMISSION\s*=\s*"([^"]+)"/.exec(read(SOURCES.gadgets))?.[1];
  const rankCosmetics = (id, seen = new Set()) => {
    const rank = lobby.rangs[id];
    if (!rank || seen.has(id)) {
      return [];
    }
    seen.add(id);
    const permissions = rank.permissions ?? [];
    const own = permissions
      .filter((permission) => permission.startsWith('rago.cosmetic.'))
      .map((permission) => permission.slice('rago.cosmetic.'.length));
    const rarest = permissions.includes(premiumPermission) ? catalog.filter((c) => c.premium).map((c) => c.id) : [];
    return [...new Set([...rarest, ...own, ...(rank.herite ? rankCosmetics(rank.herite, seen) : [])])];
  };

  // the ranks of the web store, the lowest first (VIP, then PREMIUM+), as the shop shows them
  const ranks = Object.entries(lobby.rangs)
    .filter(([, rank]) => rank['boutique-en-ligne'] === true && rank.staff !== true)
    .reverse()
    .map(([id, rank]) => {
      const prefix = String(rank.prefixe).replace('{+}', '+');
      return {
        id,
        name: plain(prefix).trim().replace(/^\[(.*)\]$/, '$1'),
        prefix: segments(prefix.trim()),
        colour: colourOf(rank.couleur),
        price: rank['prix-en-ligne'],
        priceAmount: amount(rank['prix-en-ligne']),
        inherits: rank.herite ?? null,
        xpBoost: rank['boost-xp'] ?? 1,
        perks: perks(rank.avantages),
        cosmetics: rankCosmetics(id).map(cosmetic),
      };
    });

  const pack = lobby['pack-depart'];
  const starterPack = {
    name: plain(pack.nom),
    price: pack.prix,
    regularPrice: pack['prix-normal'] ?? pack.prix,
    currency: pack.devise,
    ragoPoints: pack['rago-points'] ?? 0,
    xpBoosters: pack.boosters ?? 0,
    pointsBoosters: pack['boosters-pieces'] ?? 0,
    cosmetics: (pack.cosmetiques ?? []).map(cosmetic),
  };

  // the kits of Attack and Defense, as the menu before each round shows them
  const attackDefense = parse(read(SOURCES.bedwarsConfig))['attaque-defense'] ?? {};
  const roundKit = (kit) => ({ name: plain(kit.nom), icon: kit.icone, description: paragraph(kit.description) });

  const events = practiceConfig.evenements ?? {};
  const automatic = events.automatiques ?? {};

  // the block trainings and duels, by the constants of their classes
  const presets = [...read(SOURCES.blockClutch).matchAll(/preset\("([^"]+)"/g)].map((m) => m[1]);
  const distances = found('timedBridge', /DISTANCES = \{([\d, ]+)\}/).split(',').map(Number);
  const blockPlacement = {
    voidBonus: number('blockFight', /VOID_BONUS = (\d+)/),
    arenaBlockSeconds: number('blockArena', /DECAY_TICKS = (\d+)/) / 20,
    clutch: {
      knockback: settingRange('blockClutch', 'Knockback'),
      hits: settingRange('blockClutch', 'Hits'),
      presets,
    },
    timedBridge: {
      distances,
      // straight or diagonal, flat or uphill
      courses: distances.length * 4,
      secondsPerBlock: number('timedBridge', /LIMIT_MS_PER_BLOCK = (\d+)L/) / 1000,
    },
  };

  // the parties and their private games
  const privateGames = {
    partyMax: number('parties', /MAX_SIZE = (\d+)/),
    topBridgeMinutes: number('topBridge', /PRACTICE_MINUTES = (\d+)/),
    topBridgeBlockSeconds: number('topBridge', /DECAY_SECONDS = (\d+)/),
    modes: Object.fromEntries(Object.entries(lobby['parties-privees'] ?? {})),
  };

  return {
    _generated: 'by scripts/import-game-data.mjs from the files of the plugins: do not edit, run npm run data',
    sources: Object.values(SOURCES),
    server: {
      title: plain(lobby.titre),
      slogan: plain(lobby.tab?.haut?.[1] ?? ''),
    },
    categories,
    groups,
    modes,
    ranked: {
      queue: {
        startGap: lobby.file['ecart-elo-initial'],
        gapStep: lobby.file['ecart-elo-pas'],
        gapEverySeconds: lobby.file['ecart-elo-secondes'],
        maxGap: lobby.file['ecart-elo-max'],
        leavePenaltyMinutes: lobby.file['abandon-classe-minutes'],
      },
      mvpBonus: lobby.elo['bonus-mvp'] ?? 0,
      divisions,
    },
    season: {
      number: lobby.saison.numero,
      name: lobby.saison.nom,
      end: lobby.saison.fin ? String(lobby.saison.fin) : null,
    },
    ranks,
    starterPack,
    cosmetics: catalog,
    ragoPoints: {
      win: lobby['rago-points'].victoire,
      loss: lobby['rago-points'].defaite,
      kill: lobby['rago-points'].kill,
      daily: lobby['rago-points'].quotidien,
    },
    events: {
      automatic: automatic.actif === true,
      everyMinutes: automatic.minutes ?? null,
      lobbyPlayers: automatic['joueurs-lobby'] ?? null,
      maxPlayers: events['joueurs-max'] ?? null,
    },
    attackDefense: {
      protection: attackDefense.protection ?? 2,
      attack: (attackDefense['kits-attaque'] ?? []).map(roundKit),
      defense: (attackDefense['kits-defense'] ?? []).map(roundKit),
    },
    blockPlacement,
    privateGames,
    pointsToWin: practiceConfig['points-pour-gagner'] ?? {},
    replays: {
      active: lobby.replays?.actif === true,
      days: lobby.replays?.jours ?? null,
    },
    rules: (lobby.liens?.regles ?? []).slice(1).map(plain).map((rule) => rule.replace(/^\d+\.\s*/, '')),
  };
}

const serialise = (data) => JSON.stringify(data, null, 2) + '\n';

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const fresh = serialise(buildGameData());
  if (process.argv.includes('--check')) {
    let current = '';
    try {
      current = readFileSync(OUTPUT, 'utf8');
    } catch {
      // missing: not up to date
    }
    if (current !== fresh) {
      console.error('src/data/game.json is not up to date with the files of the game: run npm run data in site/web.');
      process.exit(1);
    }
    console.log('src/data/game.json is up to date.');
  } else {
    writeFileSync(OUTPUT, fresh);
    console.log(`Wrote ${path.relative(process.cwd(), OUTPUT)}.`);
  }
}
