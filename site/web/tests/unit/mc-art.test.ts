import { existsSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { REPO } from '../../scripts/import-game-data.mjs';
import art from '@/data/mc-items.json';
import { FLAGSHIPS, flagshipModes } from '@/lib/flagship';
import { game, modeOf, playableModes } from '@/lib/game';
import { itemArt } from '@/lib/mc';

/**
 * Every item the site draws is a real texture of Minecraft 1.8.8 (scripts/import-mc-textures.mjs),
 * and every one of the six modes the site puts first is a mode of the game.
 */
const PUBLIC = path.join(REPO, 'site/web/public');
const SRC = path.join(REPO, 'site/web/src');

const files = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((entry) =>
    entry.name === 'build' || entry.name === 'node_modules'
      ? []
      : entry.isDirectory()
        ? files(path.join(dir, entry.name))
        : [path.join(dir, entry.name)],
  );

/** The items the pages name in their code: material="COMPASS", item: 'BED', icon: 'STICK'. */
const named = files(SRC)
  .filter((file) => /\.(tsx?|mjs)$/.test(file))
  .flatMap((file) => [
    ...readFileSync(file, 'utf8').matchAll(/(?:material=["']|item: '|icon: ')([A-Z][A-Z_]+)['"]/g),
  ])
  .map((match) => match[1] as string);

describe('the items of the game', () => {
  it('has a texture for every item the game data and the pages show', () => {
    const used = new Set([
      ...named,
      ...game.modes.flatMap((mode) => (mode.icon ? [mode.icon] : [])),
      ...game.categories.flatMap((category) => (category.icon ? [category.icon] : [])),
      ...game.cosmetics.flatMap((cosmetic) => (cosmetic.icon ? [cosmetic.icon] : [])),
      ...game.attackDefense.attack.map((kit) => kit.icon),
      ...game.attackDefense.defense.map((kit) => kit.icon),
      ...FLAGSHIPS.map((flagship) => flagship.icon),
    ]);
    expect(used.size).toBeGreaterThan(20);
    for (const material of used) {
      expect(() => itemArt(material), material).not.toThrow();
    }
  });

  it('points every texture at a file of public/mc', () => {
    const paths = [
      ...Object.keys(art.items).flatMap((material) => {
        const item = itemArt(material);
        return item.kind === 'block' ? [item.top, item.side] : [item.src];
      }),
      ...art.decor,
      art.gui.hotbar,
      art.gui.selection,
    ] as string[];
    for (const src of paths) {
      expect(src.startsWith('/mc/'), src).toBe(true);
      expect(existsSync(path.join(PUBLIC, src)), src).toBe(true);
    }
  });

  it('says where the textures come from, next to them', () => {
    const readme = readFileSync(path.join(SRC, 'data/MINECRAFT-TEXTURES.md'), 'utf8');
    expect(readme).toContain('minecraft-assets');
    expect(readme).toContain('Mojang');
  });
});

describe('the six modes the site puts first', () => {
  it('are six, each with its page', () => {
    expect(FLAGSHIPS).toHaveLength(6);
    expect(new Set(FLAGSHIPS.map((flagship) => flagship.slug)).size).toBe(6);
  });

  it('are modes of the game that can be played, in their category', () => {
    const playable = new Set(playableModes().map((mode) => mode.id));
    for (const flagship of FLAGSHIPS) {
      expect(flagship.modes.length, flagship.slug).toBeGreaterThan(0);
      for (const id of flagship.modes) {
        expect(() => modeOf(id), id).not.toThrow();
        expect(playable.has(id), `${id} cannot be played`).toBe(true);
      }
      expect(game.categories.some((category) => category.id === flagship.category), flagship.slug).toBe(true);
      expect(flagshipModes(flagship).length).toBe(flagship.modes.length);
    }
  });

  it('show the skin of their character of the lobby, when it has one', () => {
    for (const flagship of FLAGSHIPS.filter((f) => f.skin)) {
      expect(existsSync(path.join(PUBLIC, 'skins/3d', `${flagship.skin}.png`)), flagship.slug).toBe(true);
      expect(existsSync(path.join(PUBLIC, 'skins', `${flagship.skin}.png`)), flagship.slug).toBe(true);
    }
  });

  it('name the classes of the plugins they were read from, and those classes exist', () => {
    const classes = new Set(
      files(path.join(REPO, 'bukkit'))
        .filter((file) => file.endsWith('.java'))
        .map((file) => path.basename(file, '.java')),
    );
    for (const flagship of FLAGSHIPS) {
      for (const name of flagship.source.split(/,\s*/)) {
        expect(classes.has(name), `${flagship.slug}: ${name}`).toBe(true);
      }
    }
  });
});

describe('what only one mode has', () => {
  it('gives each side of Attack and Defense its kits', () => {
    expect(game.attackDefense.attack.length).toBeGreaterThan(0);
    expect(game.attackDefense.defense.length).toBeGreaterThan(0);
  });

  it('gives the combo bot its four levels, harder each time', () => {
    const levels = game.comboBot.levels;
    expect(levels.map((level) => level.id)).toHaveLength(4);
    for (let i = 1; i < levels.length; i++) {
      const [before, after] = [levels[i - 1]!, levels[i]!];
      expect(after.cps).toBeGreaterThan(before.cps);
      expect(after.accuracy).toBeGreaterThan(before.accuracy);
      expect(after.reactionTicks).toBeLessThan(before.reactionTicks);
    }
  });
});
