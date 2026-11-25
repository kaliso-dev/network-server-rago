---
type: code
tags: [code, site, nextjs]
maj: 2026-11-25
code: site/web, site/design
---
# Code du site

Next.js 16 (App Router), TypeScript, ~5 400 lignes de TS/TSX/MJS (sans les CSS). Fonction et réglages : [[Site]]. Retour à [[Architecture]].

## `site/design` (système de design v0.1)
`tokens.css` (couleurs, typo, espacements), `components.css` (composants de base), `styleguide.html` (la page de style), `assets/` (logo). Le site les importe **sans copie**.

## `site/web/src/app` (les pages)
| Fichier | Rôle |
|---|---|
| `page.tsx` (615 l.) | l'accueil : hero et PNJ 3D, six modes phares, practice, parties privées, combat, Ranked, cosmétiques, communauté |
| `modes/page.tsx`, `modes/[slug]/page.tsx` | la liste des modes par catégorie, la page d'un mode phare |
| `legal/[slug]/page.tsx` | pages légales (en relecture) |
| `layout.tsx`, `fonts.ts`, `not-found.tsx`, `opengraph-image.tsx`, `robots.ts`, `sitemap.ts` | gabarit, police, 404, image de partage, référencement |

## `src/components`
`Hotbar` (la barre d'objets, navigation principale), `SiteHeader`, `SiteFooter`, `IpChip` (copie de l'adresse), `ModeShowcase`, `CategoryCard`, `ItemMenu` (menu coffre façon Minecraft), `McItem`, `McText` (texte avec codes couleur), `PixelIcon`, `RankCard`, `DivisionLadder`, `ui.tsx` ; `three/` : `SkinViewer` (skinview3d), `ItemViewer`, `item-model.ts`, `lazy.ts` (chargement différé).

## `src/lib` et `src/data`
`game.ts` (lecture de `game.json`), `flagship.ts` (les six modes phares, écrits à la main, chaque phrase lue dans le code et nommant ses classes), `settings.ts` (les variables `RAGO_*`), `colour.ts`, `mc.ts`, `legal.ts`, `block-trainings.ts`. `data/game.json` (**généré**), `data/mc-items.json`, `data/MINECRAFT-TEXTURES.md`.

## `scripts`
`import-game-data.mjs` (498 l. : lit le `config.yml` du lobby, `Cosmetic.java`, la config de RagoBedwars et le code de RagoPractice → `game.json` ; `--check` en CI), `import-mc-textures.mjs`, `mc-font.mjs` (trace la police du jeu), `render-skins.mjs` (skins des PNJ), `build-e2e.mjs` (construit les états `soon` et `online`).

## `tests`
Unitaires (vitest) : `game-data.test.ts` (rangs vendus au prix de la config, aucun rang du staff, 11 divisions, chaque mode dans un seul plugin), `mc-art.test.ts`, `mc-font.test.ts`, `settings.test.ts`. E2E (Playwright) : `home`, `modes`, `layout`, `seo`, `a11y` (axe).
