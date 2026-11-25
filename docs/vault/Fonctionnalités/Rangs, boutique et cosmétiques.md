---
type: fonctionnalité
tags: [rangs, vip, premium, boutique, cosmétiques, monétisation]
maj: 2026-11-25
code: bukkit/rago-lobby (Ranks, RankDef, NetworkRanks, ShopMenu, StoreMenu, StarterPack, GiftRequests, Cosmetic, CosmeticEffects, NameStyle, NameLooks, StyleMenu, XpBoosters, Nicks, LobbyGadgets)
---
# Rangs, boutique et cosmétiques

Retour à [[Lobby]]. Détail : `bukkit/rago-lobby/README.md`. **Rien n'est vendu pour de vrai** : la boutique en ligne n'existe pas encore, les prix sont ceux du `config.yml` ([[Plan de sortie publique]]).

## Les rangs (`rangs:`)
Neuf rangs, du plus haut au plus bas : **OWNER, ADMIN, DEV, MOD, HELPER, BUILDER, MEDIA, PREMIUM, VIP**, chacun avec préfixe, couleurs, permissions et héritage. On montre le plus haut. Staff donné avec `/rank set`. Permissions : `rago.admin` > `rago.mod` > `rago.staff` > avantages `rago.perk.*` ([[Commandes et permissions]]).

| | **VIP** | **PREMIUM+** (id `premium`) |
|---|---|---|
| Prix en ligne (`prix-en-ligne`) | 4,99 € | 9,99 € |
| Prix en cadeau (Rago Points, `prix-cadeau`) | 25 000 | 50 000 |
| Boost d'XP | ×1,25 | ×1,5 |
| Priorité de file (hors classé) | 1 | 2 |
| Pseudo | bleu clair, `[VIP]` | dégradé rose, badge `✦`, `[PREMIUM+]` avec un + coloriable |
| Avantages | `/fly`, vitesse au lobby, arrivée avec feu d'artifice, chat sans délai, 3 cosmétiques (traînée Clouds, kill Heart burst, victoire Love) | tout VIP + `/nick`, `/display message`, 3 gadgets (bat blaster, mouton explosif, boule disco), effets rares, booster d'XP gratuit par jour, `/host` ([[Events host]]), `/gift` |

La boutique en ligne donnera les rangs depuis la console : `/rank give <joueur> vip|premium`. Le staff et MEDIA héritent de PREMIUM.

## Boutique du jeu (`/shop`) et PNJ Shop
- **18 cosmétiques** (traînées de projectiles, effets de kill, effets de victoire), chacun débloqué à un niveau, achetés en Rago Points ; ils ne jouent qu'en partie.
- **Couleurs de pseudo** (`/style`, `styles:`) : dégradés animés (Pink, Fire, Ocean, Forest, Sunset, Ice, Galaxy, Rainbow, Shadow ; Aqua, Gold, Legend par niveau), couleurs fixes à 3 000 points, dégradés fixes Cherry et Mint à 6 000 ; badges (`✦`, RAGO, BETTER, PVP, YT, `✫100`, couronne ♛, badges de saison) ; le + de PREMIUM+ (noir à 50 000 points).
- **Boosters** (`/booster`) : 30 minutes de jeu ; booster d'XP ×3 (2 500 points ou en ligne), booster du jour PREMIUM ×2, booster de points ×3 (en ligne, pack). `/booster give` depuis la console.
- **Pack de départ** (`/pack`, `pack-depart:`) : **3,99 €**, une fois : 20 000 Rago Points, 3 boosters d'XP, 2 boosters de points, 3 cosmétiques (trail-magic, kill-firework, win-fireworks), lien `rago-optimizer.com`. `/pack give` depuis la console. `prix-normal:` ne montre un prix barré que si le pack s'est vraiment vendu à ce prix.
- **Offrir un rang** (`/gift`, PREMIUM et plus) avec ses Rago Points ; l'autre accepte dans un livre YES/NO.
- **Gadgets du lobby** (4e case) : bâton de téléportation, grappin, pistolet à paintball, feux d'artifice, party popper, plume de saut ; fireball jump (5e case).
- `/nick` (PREMIUM) : autre pseudo, skin par défaut, niveau 1 ; `/realname` pour le staff.

## La boutique en ligne (pas faite)
Prévue sur le [[Site]] avec **Tebex (API Headless)**, étapes J3 et J4 du site (`site/README.md`, `site/web/README.md`) : **à venir**. Le lien affiché en jeu aujourd'hui est `rago-optimizer.com` (choisi le 22/11 comme lien officiel avec `discord.gg/ragooptimizer`). Les commandes console (`/rank give`, `/pack give`, `/booster give`, `/style give`) attendent qu'elle existe. Les pages légales doivent être relues avant son ouverture.
