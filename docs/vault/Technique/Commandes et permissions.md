---
type: technique
tags: [commandes, permissions]
maj: 2026-11-25
---
# Commandes et permissions

Tout est en anglais en jeu ; les anciens mots français (`quitter`, `accepter`, `refuser`, `retirer`, `/file`, `/monde creer`…) marchent encore. Source : les `plugin.yml` et le tableau « Commandes » de `bukkit/rago-lobby/README.md`. Retour à [[Reprendre le développement]].

## Permissions
- `rago.admin` > `rago.mod` > `rago.staff` > avantages `rago.perk.*` (`chat`, `fly`, `join`, `nick`, `message`, `speed`, `spectate` (plus utilisé), `host`, `gift`, `gadgets`, `booster`), cosmétiques `rago.cosmetic.<id>`.
- `rago.lobby.admin` (lobby : spawn, points, build), `rago.combat.admin` (`/kb`), `rago.bedwars.admin` (`/bw`), `rago.build.admin` et `rago.build.construire` (outils de build).
- `/owner` : seulement le compte de `outils-owner.compte` avec `rago.admin` ([[Outils Owner]]).

## RagoLobby
| Commande (alias) | Effet | Qui |
|---|---|---|
| `/spawn` (`/hub`, `/l`) | retour au spawn ; en partie = abandon | tous |
| `/playagain` (`/again`) | rejouer le même mode | tous |
| `/queue <mode> [map]`, `/queue leave [mode]` (`/file`, `/q`) | files | tous |
| `/play [catégorie]` (`/modes`, `/games`, `/jouer`), `/ranked` | la boussole, une catégorie, le menu Ranked | tous |
| `/duel <joueur> [mode]`, `/duel` (chef de party), `/duel accept|deny <joueur>` | défis, duels entre parties | tous |
| `/party …` (`/p`), `/pc`, `/chat party|all` | party | tous |
| `/privategame` (`/pg`) `team`, `settings` | parties privées | chef de party |
| `/hypixel import [pseudo]`, `/hypixel shop` | [[Import Hypixel]] | tous |
| `/replay [code|leave]`, `/spectate <joueur>` (`/spec`) | [[Replays et spectateurs]] | tous |
| `/lobby modes`, `/lobby setspawn`, `/lobby point <nom>|remove|tp`, `/lobby points`, `/lobby build` | réglage du lobby | `rago.lobby.admin` (sauf `modes`) |
| `/shop`, `/profile` (`/me`), `/daily`, `/settings`, `/season`, `/level`, `/quests`, `/titles`, `/display`, `/style` (`/badge`), `/booster`, `/pack`, `/stats`, `/top`, `/ping` | profil et progression | tous |
| `/gift <joueur>` | offrir un rang | PREMIUM+ |
| `/friend` (`/f`), `/msg` (`/tell`, `/w`), `/reply` (`/r`) | social | tous |
| `/fly`, `/nick`, `/display message` | avantages | VIP / PREMIUM |
| `/report` (`/wdr`), `/discord`, `/rules`, `/site`, `/store` | signaler, liens | tous |
| `/staffchat` (`/sc`), `/vanish` (`/v`), `/freeze`, `/clearchat`, `/reports`, `/staff`, `/realname`, `/mute`, `/unmute`, `/kick`, `/warn`, `/history` | modération | `rago.staff` |
| `/ban`, `/unban`, `/mutechat` | modération | `rago.mod` |
| `/rank`, `/points`, `/booster give`, `/pack give`, `/style give|take` | administration (aussi console) | `rago.admin` |
| `/owner reach|scaffold|off` | [[Outils Owner]] | compte Owner |

## RagoPractice
`/challenge` (`/defi`, `/dc` : défi du jour), `/aimtop`, `/speed` (Boxing : couper ou rendre la Speed), `/center` (`/centre` : Arena Free), `/records [joueur]` (`/pb`), `/host [sumo|brackets|lms]` (`rago.perk.host`), `/event [leave|cancel]`, `/join`, `/matchinv <code>` (inventaire de fin d'un duel).

Attention : `/speed` existe deux fois, dans RagoPractice et comme alias de `/vitesse` de RagoBuildTools ; sur le serveur de test, c'est l'ordre de chargement de Bukkit qui décide (À VÉRIFIER en jeu, `/ragopractice:speed` et `/ragobuildtools:vitesse` lèvent le doute).

## RagoBedwars
`/bw …` (`/bedwars`, `rago.bedwars.admin` : `edit`, `spawn`, `bed`, `generator`, `shop`, `upgrades`, `diamond`, `emerald`, `spectators`, `height`, `void`, `floor`, `lobbyfight`, `info`, `enable`, `disable`, `maps`, `remove`, `help`), `/ffaspawn` (choix d'apparition du FFA), `/kit` (kit d'attaque du Bed Break Simulator et d'Attack and Defense).

## RagoCombatEngine
`/kb [status|profiles|profile|world|mode|hitdelay|debug|sparring|reload]` (`rago.combat.admin`) : [[Knockback et combat]].

## RagoBuildTools
`/monde [create|tp|import|spawn|save]` (`/world`), `/vitesse` (`/speed`), `/nv` (`rago.build.admin`) : [[Serveur de test]].
