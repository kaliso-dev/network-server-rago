#!/usr/bin/env node
/**
 * Copies the Minecraft 1.8.8 textures the site shows into public/mc, and writes
 * src/data/mc-items.json: for each item the game names (COMPASS, WOOL, SKULL_ITEM...), how to draw
 * it. It also turns the game's font (font/ascii.png) into the site's pixel font, public/mc/font.
 * Nothing is drawn by hand: every pixel comes from the textures of the game.
 *
 * The textures are Mojang's. They come from the package minecraft-assets (PrismarineJS, its code
 * under the MIT licence), which is too big to be a dependency of the site (365 MB):
 *
 *   npm install --no-save minecraft-assets@1.19.0
 *   npm run mc
 *
 * Whether the site may show them is to be settled before the opening (src/data/MINECRAFT-TEXTURES.md):
 * everything that comes from the game is in public/mc, so that it can go at once.
 */
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import wawoff2 from 'wawoff2';
import { buildFont } from './mc-font.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const WEB = path.resolve(HERE, '..');
const OUT = path.join(WEB, 'public/mc');
const MAP = path.join(WEB, 'src/data/mc-items.json');
const FROM = path.resolve(
  process.env.MC_ASSETS ?? path.join(WEB, 'node_modules/minecraft-assets/minecraft-assets/data/1.8.8'),
);
const SKINS = path.resolve(WEB, '../../bukkit/rago-lobby/src/main/resources/skins');

/*
 * The items, by their name in the game (org.bukkit.Material of 1.8). An item is drawn flat, from
 * its 16 x 16 texture; a block as the inventory draws it, a cube with its top and its sides; a
 * head as a cube with the faces of a skin's head.
 */
const ITEMS = {
  ARROW: { item: 'items/arrow.png' },
  BED: { item: 'items/bed.png' },
  BLAZE_POWDER: { item: 'items/blaze_powder.png' },
  BLAZE_ROD: { item: 'items/blaze_rod.png' },
  BOOK: { item: 'items/book_normal.png' },
  BOW: { item: 'items/bow_standby.png' },
  COMPASS: { item: 'items/compass.png', frame: 2 },
  DIAMOND_BLOCK: { block: { top: 'blocks/diamond_block.png', side: 'blocks/diamond_block.png' } },
  DIAMOND_CHESTPLATE: { item: 'items/diamond_chestplate.png' },
  DIAMOND_PICKAXE: { item: 'items/diamond_pickaxe.png' },
  DIAMOND_SWORD: { item: 'items/diamond_sword.png' },
  EMERALD: { item: 'items/emerald.png' },
  ENDER_PEARL: { item: 'items/ender_pearl.png' },
  ENDER_STONE: { block: { top: 'blocks/end_stone.png', side: 'blocks/end_stone.png' } },
  EYE_OF_ENDER: { item: 'items/ender_eye.png' },
  FIREBALL: { item: 'items/fireball.png' },
  FIREWORK: { item: 'items/fireworks.png' },
  GLOWSTONE_DUST: { item: 'items/glowstone_dust.png' },
  GOLD_AXE: { item: 'items/gold_axe.png' },
  GOLD_INGOT: { item: 'items/gold_ingot.png' },
  GOLD_SWORD: { item: 'items/gold_sword.png' },
  INK_SACK: { item: 'items/dye_powder_black.png' },
  IRON_CHESTPLATE: { item: 'items/iron_chestplate.png' },
  IRON_PICKAXE: { item: 'items/iron_pickaxe.png' },
  IRON_SWORD: { item: 'items/iron_sword.png' },
  JUKEBOX: { block: { top: 'blocks/jukebox_top.png', side: 'blocks/jukebox_side.png' } },
  LADDER: { item: 'blocks/ladder.png' },
  MAGMA_CREAM: { item: 'items/magma_cream.png' },
  NAME_TAG: { item: 'items/name_tag.png' },
  NOTE_BLOCK: { block: { top: 'blocks/noteblock.png', side: 'blocks/noteblock.png' } },
  OBSIDIAN: { block: { top: 'blocks/obsidian.png', side: 'blocks/obsidian.png' } },
  // a potion without data is the water bottle: its overlay tinted with the colour of water
  POTION: { item: 'items/potion_bottle_drinkable.png', under: 'items/potion_overlay.png', tint: 0x385dc6 },
  QUARTZ_BLOCK: { block: { top: 'blocks/quartz_block_top.png', side: 'blocks/quartz_block_side.png' } },
  RED_ROSE: { item: 'blocks/flower_rose.png' },
  REDSTONE: { item: 'items/redstone_dust.png' },
  REDSTONE_BLOCK: { block: { top: 'blocks/redstone_block.png', side: 'blocks/redstone_block.png' } },
  SANDSTONE: { block: { top: 'blocks/sandstone_top.png', side: 'blocks/sandstone_normal.png' } },
  SHEARS: { item: 'items/shears.png' },
  SKULL_ITEM: { head: 'entity/skeleton/skeleton.png' },
  SLIME_BALL: { item: 'items/slimeball.png' },
  STICK: { item: 'items/stick.png' },
  STONE_SWORD: { item: 'items/stone_sword.png' },
  TNT: { block: { top: 'blocks/tnt_top.png', side: 'blocks/tnt_side.png' } },
  WOOL: { block: { top: 'blocks/wool_colored_white.png', side: 'blocks/wool_colored_white.png' } },
};

/** Blocks the pages are built with, beyond the items: the bridge of red wool under the hero. */
const DECOR = ['blocks/wool_colored_red.png'];

/** The pieces of the interface: [file, x, y, width, height]. */
const GUI = {
  hotbar: ['gui/widgets.png', 0, 0, 182, 22],
  selection: ['gui/widgets.png', 0, 22, 24, 24],
};

const readPng = (file) => PNG.sync.read(readFileSync(file));

function crop(png, x, y, width, height) {
  const out = new PNG({ width, height });
  PNG.bitblt(png, out, x, y, width, height, 0, 0);
  return out;
}

/** Draws the overlay tinted (multiplied by the colour), with the item on top, as the game does. */
function tinted(overlay, item, tint) {
  const out = new PNG({ width: item.width, height: item.height });
  const rgb = [(tint >> 16) & 255, (tint >> 8) & 255, tint & 255];
  for (let i = 0; i < out.data.length; i += 4) {
    const top = item.data[i + 3] / 255;
    for (let c = 0; c < 3; c++) {
      const under = (overlay.data[i + c] * rgb[c]) / 255;
      out.data[i + c] = Math.round(item.data[i + c] * top + under * (1 - top));
    }
    out.data[i + 3] = Math.max(item.data[i + 3], overlay.data[i + 3]);
  }
  return out;
}

async function main() {
  if (!existsSync(path.join(FROM, 'items/compass.png'))) {
    console.error(`No Minecraft 1.8.8 textures in ${FROM}.\nnpm install --no-save minecraft-assets@1.19.0, or set MC_ASSETS.`);
    process.exit(1);
  }
  rmSync(path.join(OUT, 'items'), { recursive: true, force: true });
  rmSync(path.join(OUT, 'blocks'), { recursive: true, force: true });
  rmSync(path.join(OUT, 'gui'), { recursive: true, force: true });
  rmSync(path.join(OUT, 'font'), { recursive: true, force: true });
  for (const dir of ['items', 'blocks', 'gui', 'font']) {
    mkdirSync(path.join(OUT, dir), { recursive: true });
  }

  const map = {};
  const write = (png, name) => {
    writeFileSync(path.join(OUT, name), PNG.sync.write(png));
    return `/mc/${name}`;
  };
  const copyBlock = (file) => {
    const name = `blocks/${path.basename(file)}`;
    copyFileSync(path.join(FROM, file), path.join(OUT, name));
    return `/mc/${name}`;
  };

  for (const [material, spec] of Object.entries(ITEMS)) {
    const name = material.toLowerCase();
    if (spec.item) {
      let png = readPng(path.join(FROM, spec.item));
      if (spec.frame !== undefined) {
        png = crop(png, 0, 16 * spec.frame, 16, 16);
      }
      if (spec.under) {
        png = tinted(readPng(path.join(FROM, spec.under)), png, spec.tint);
      }
      map[material] = { kind: 'item', src: write(png, `items/${name}.png`) };
    } else if (spec.block) {
      map[material] = { kind: 'block', top: copyBlock(spec.block.top), side: copyBlock(spec.block.side) };
    } else if (spec.head) {
      // the head of a 64 x 32 mob skin: its six faces and those of the hat, in the first 64 x 16 pixels
      map[material] = { kind: 'head', src: write(crop(readPng(path.join(FROM, spec.head)), 0, 0, 64, 16), `items/${name}.png`) };
    }
  }

  // the head of the Your Rago profile NPC, for the community: the owner's own skin, not Mojang's
  map.PLAYER_HEAD = { kind: 'head', src: '/mc/items/player_head.png' };
  writeFileSync(path.join(OUT, 'items/player_head.png'), PNG.sync.write(crop(readPng(path.join(SKINS, 'stats.png')), 0, 0, 64, 16)));

  const decor = DECOR.map(copyBlock);

  const gui = {};
  for (const [name, [file, x, y, width, height]] of Object.entries(GUI)) {
    gui[name] = write(crop(readPng(path.join(FROM, file)), x, y, width, height), `gui/${name}.png`);
  }

  // the font of the game, from its own font/ascii.png (scripts/mc-font.mjs): woff2 for the pages,
  // and the bold in TrueType for the share image, which cannot read woff2
  const ascii = readPng(path.join(FROM, 'font/ascii.png'));
  for (const bold of [false, true]) {
    const ttf = new Uint8Array(buildFont(ascii, { bold }));
    const name = bold ? 'minecraft-bold' : 'minecraft';
    writeFileSync(path.join(OUT, `font/${name}.woff2`), await wawoff2.compress(ttf));
    if (bold) {
      writeFileSync(path.join(OUT, `font/${name}.ttf`), ttf);
    }
  }

  writeFileSync(
    MAP,
    `${JSON.stringify(
      {
        _generated: 'by scripts/import-mc-textures.mjs from the textures of Minecraft 1.8.8: do not edit, run npm run mc',
        items: map,
        decor,
        gui,
      },
      null,
      2,
    )}\n`,
  );
  console.log(
    `Wrote ${Object.keys(map).length} items, ${Object.keys(gui).length} pieces of the interface and the font of the game to public/mc.`,
  );
}

await main();
