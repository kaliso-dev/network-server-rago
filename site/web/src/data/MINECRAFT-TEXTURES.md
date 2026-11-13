# The textures of Minecraft on the site

Everything in `public/mc` comes from Minecraft 1.8.8. Nothing there was drawn by hand. This note sits outside `public` so that the site does not serve it.

| Folder | What it holds |
| --- | --- |
| `public/mc/items/` | The 16 × 16 textures of the items the site shows: the compass, the swords, the bed and the rest. |
| `public/mc/blocks/` | The faces of the blocks that the inventory draws as cubes: wool, end stone, TNT and the rest. |
| `public/mc/gui/` | The hotbar and its selection frame, cut from `gui/widgets.png`. |

`mc-items.json`, next to this note, says how the site draws each item, by its name in the game.

Two kinds of picture come from elsewhere:

- The player head is cut from a skin of the lobby (`bukkit/rago-lobby/src/main/resources/skins`).
- The skeleton skull is cut from the skeleton skin of the game.

## Where they come from

The textures are © Mojang. They were copied from the npm package `minecraft-assets` 1.19.0 (PrismarineJS), version `1.8.8`. That package's own code is under the MIT licence; the textures in it remain Mojang's.

To copy them again:

```sh
npm install --no-save minecraft-assets@1.19.0
npm run mc
```

The script is `scripts/import-mc-textures.mjs`. It rewrites `public/mc/items`, `public/mc/blocks`, `public/mc/gui` and `mc-items.json`.

## To settle before the opening

**Whether the site may show these textures has not been checked.** Mojang's rules for servers and for the use of its assets decide it, and nobody has read them for this site yet. Have someone read them before the site goes public.

Everything that comes from the game is in `public/mc` and in `mc-items.json`. If the answer is no, three steps remove it:

1. Delete `public/mc`.
2. Replace the items in `mc-items.json` with the site's own drawings.
3. Run `npm run build`.

`McItem` and the 3D viewers read only `mc-items.json`, so the pages themselves don't change.
