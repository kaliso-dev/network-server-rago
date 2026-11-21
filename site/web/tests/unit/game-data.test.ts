import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { buildGameData, REPO, SOURCES } from '../../scripts/import-game-data.mjs';
import { categoryOf, game, modeOf, modesShownIn } from '@/lib/game';

/**
 * What the site shows of the game must be what the game does. These tests read the files of the
 * plugins again, on their own, and compare them with src/data/game.json.
 */
const read = (file: string) => readFileSync(path.join(REPO, file), 'utf8');
const lobby = parse(read(SOURCES.lobbyConfig));

describe('the game data', () => {
  it('is up to date with the files of the plugins (npm run data)', () => {
    expect(game).toEqual(JSON.parse(JSON.stringify(buildGameData())));
  });

  it('shows only the ranks sold on the web store, at the price of the config', () => {
    const sold = Object.entries<Record<string, unknown>>(lobby.rangs).filter(
      ([, rank]) => rank['boutique-en-ligne'] === true,
    );
    expect(game.ranks.map((rank) => rank.id).sort()).toEqual(sold.map(([id]) => id).sort());
    for (const rank of game.ranks) {
      const config = lobby.rangs[rank.id];
      expect(config.staff, `${rank.id} is a staff rank`).not.toBe(true);
      expect(rank.price).toBe(config['prix-en-ligne']);
      expect(String(config['prix-en-ligne'])).toContain(rank.priceAmount.toFixed(2));
    }
  });

  it('never shows a staff rank', () => {
    const staff = Object.entries<Record<string, unknown>>(lobby.rangs)
      .filter(([, rank]) => rank.staff === true)
      .map(([id]) => id);
    expect(staff.length).toBeGreaterThan(0);
    for (const id of staff) {
      expect(game.ranks.some((rank) => rank.id === id)).toBe(false);
    }
  });

  it('shows the starter pack as the config sells it', () => {
    const pack = lobby['pack-depart'];
    expect(game.starterPack.price).toBe(pack.prix);
    expect(game.starterPack.ragoPoints).toBe(pack['rago-points']);
    expect(game.starterPack.xpBoosters).toBe(pack.boosters);
    expect(game.starterPack.pointsBoosters).toBe(pack['boosters-pieces']);
    expect(game.starterPack.cosmetics.map((c) => c.id)).toEqual(pack.cosmetiques);
    // no fake discount: the regular price is the price unless the config says otherwise
    expect(game.starterPack.regularPrice).toBe(pack['prix-normal'] ?? pack.prix);
  });

  it('gives each cosmetic a real entry of Cosmetic.java', () => {
    const declared = new Set([...read(SOURCES.cosmetics).matchAll(/new Cosmetic\("([a-z0-9-]+)"/g)].map((m) => m[1]));
    expect(declared.size).toBe(game.cosmetics.length);
    const shown = [...game.starterPack.cosmetics, ...game.ranks.flatMap((rank) => rank.cosmetics)];
    for (const cosmetic of shown) {
      expect(declared.has(cosmetic.id), cosmetic.id).toBe(true);
    }
  });

  it('has the 11 divisions of the config, each with its threshold and its gains', () => {
    const divisions = Object.entries<Record<string, number>>(lobby.elo.divisions);
    expect(game.ranked.divisions).toHaveLength(11);
    expect(game.ranked.divisions.map((d) => d.name)).toEqual(divisions.map(([name]) => name));
    game.ranked.divisions.forEach((division, i) => {
      const config = divisions[i]![1];
      expect(division.elo).toBe(config.elo);
      expect(division.win).toBe(config.victoire);
      expect(division.loss).toBe(config.defaite);
      expect(division.win).toBeGreaterThan(0);
      expect(division.loss).toBeGreaterThan(0);
      expect(division.colour).toMatch(/^#[0-9A-F]{6}$/i);
      if (i > 0) {
        expect(division.elo).toBeGreaterThan(game.ranked.divisions[i - 1]!.elo);
      }
    });
    expect(game.ranked.divisions[0]!.elo).toBe(0);
  });

  it('runs each mode shown in exactly one plugin, which really has it', () => {
    const bedwars = new Set(Object.keys(parse(read(SOURCES.bedwarsConfig)).modes));
    const practice = read(SOURCES.practicePlugin);
    const sumo = read(SOURCES.sumoHost);
    const events = read(SOURCES.eventGame);
    const hosts = (plugin: string, id: string): boolean =>
      plugin === 'bedwars'
        ? bedwars.has(id)
        : practice.includes(`register("${id}"`) ||
          sumo.includes(`MODE_ID = "${id}"`) ||
          (id.startsWith('event-') && events.includes(`("${id.slice('event-'.length)}",`));
    // a private game plays the modes of its list, with the party
    const privateGames: Record<string, string[]> = lobby['parties-privees'] ?? {};
    // a hidden mode whose kit others play (kit-de:) only holds that kit: not a mode of the site;
    // one in the selector (Lobby Fight FFA, whose kit the 1v1 plays) is a mode of its own
    const configModes: Record<string, Record<string, unknown>> = lobby.modes;
    const kitHolders = new Set(
      Object.values(configModes)
        .map((mode) => mode['kit-de'])
        .filter((id) => id && configModes[String(id)]?.cache === true),
    );
    expect(kitHolders.has('invis-practice') && kitHolders.has('bed-break-simulator')).toBe(true);
    expect(kitHolders.has('lobby-fight-ffa'), 'Lobby Fight FFA is played from the selector').toBe(false);
    const shown = Object.keys(configModes).filter((id) => !kitHolders.has(id));
    expect(game.modes.map((mode) => mode.id).sort()).toEqual(shown.sort());
    for (const mode of game.modes) {
      expect(mode.plugins, mode.id).toHaveLength(1);
      const plugin = mode.plugins[0]!;
      for (const id of privateGames[mode.id] ?? [mode.id]) {
        expect(hosts(plugin, id), `${mode.id}: ${id} in ${plugin}`).toBe(true);
      }
    }
  });

  it('counts the modes of each category as the compass shows them', () => {
    for (const category of game.categories) {
      const modes = modesShownIn(category);
      expect(category.modes).toBe(modes.length);
      expect(category.modes).toBeGreaterThan(0);
      for (const id of category.also) {
        // the modes it shows too keep their own category
        expect(modeOf(id).category, id).not.toBe(category.id);
      }
    }
    // 3 Players, in the compass only: Red Rover of the Duels and the 1v2 of the RBW modes
    const three = categoryOf('trois-joueurs');
    expect(three.also).toEqual(['red-rover-sumo', 'red-rover-block-edit', 'red-rover-block-placement', 'red-rover-top-bridge',
      'bed-break-simulator-1v2', 'invis-practice-1v2', 'clutch-scenario-1v2']);
    const events = game.modes.filter((mode) => mode.event);
    expect(events.every((mode) => lobby.modes[mode.id].cache === true)).toBe(true);
  });

  it('gives each category the skin of its NPC in the lobby, and the skin is rendered', () => {
    // FunMod2, the NPC of FUN PRACTICE, has no skin of its own yet (Steve in the lobby): no picture
    // 3 Players is in the compass only, without NPC (the owner's choice, 21/11)
    const withoutSkinYet = ['fun', 'trois-joueurs'];
    for (const category of game.categories) {
      if (withoutSkinYet.includes(category.id)) {
        expect(category.skin, category.id).toBeNull();
        continue;
      }
      expect(category.skin, category.id).not.toBeNull();
      const skin = readFileSync(path.join(REPO, 'site/web/public/skins', `${category.skin}.png`));
      expect(skin.subarray(1, 4).toString()).toBe('PNG');
    }
  });
});
