/**
 * The items of the game the site draws, from src/data/mc-items.json (scripts/import-mc-textures.mjs):
 * the item a mode shows in the compass, the icon of a cosmetic, the items of the hotbar.
 */
import data from '@/data/mc-items.json';

export type ItemArt =
  | { kind: 'item'; src: string }
  | { kind: 'block'; top: string; side: string }
  | { kind: 'head'; src: string };

const items = data.items as Record<string, ItemArt>;

/** The textures of the interface: the hotbar and its selection frame. */
export const gui = data.gui as { hotbar: string; selection: string };

/** How to draw an item of the game, by its name in the game (COMPASS, WOOL, SKULL_ITEM...). */
export function itemArt(material: string): ItemArt {
  const art = items[material];
  if (!art) {
    throw new Error(`no texture for the item ${material}: add it to scripts/import-mc-textures.mjs`);
  }
  return art;
}

export const hasItemArt = (material: string): boolean => material in items;
