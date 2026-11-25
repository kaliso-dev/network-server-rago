---
type: historique
tags: [architecture, plan, roadmap]
maj: 2026-11-25
---
# Plan d'architecture v1

Résumé du document **« Rago Practice — Architecture technique v1 »** (Claude Doc du 06/11/2026, https://claude.ai/artifact/JHXQTtaRJU7REysX4xrvxo, privé sur le compte de Sofiene). **C'est un plan, pas l'état du projet** : la plupart des modules prévus n'existent pas, et la réalité a pris un autre chemin (tout dans RagoLobby, un seul serveur, pas de base). État réel : [[Architecture]]. Retour à [[Chronologie du projet]].

## Coût et machine
Budget **30 €/mois** : une machine en Europe (Allemagne, France, Pays-Bas), 4 cœurs à forte fréquence, 16 Go, NVMe, 20 à 28 €/mois ; TCPShield gratuit ; sauvegardes hors machine 3-5 € ; domaine ~1 € (« rago.gg ou équivalent ») ; total 24 à 34 €. 100 à 200 joueurs simultanés plausibles, à mesurer. Le risque : la machine unique.

## Réseau
| Processus | Rôle | Logiciel | RAM |
|---|---|---|---|
| proxy-01 | entrée unique, routage, **matchmaker**, files | Velocity 3.x (forwarding legacy) | 512 Mo |
| lobby-01 | lobby, menus, PNJ, classements | PandaSpigot 1.8.8 | 1,5-2 Go |
| games-01 | parties, N slots d'arène | PandaSpigot 1.8.8 + RagoCombatEngine | 6-8 Go |
| MariaDB 11 LTS | données persistantes | | 1 Go |
| Redis 7 | files, heartbeats, messages entre serveurs | | 64-128 Mo |
| rago-api (phase 5) | API pour le site, Discord, Rago Client | Java 21 | 256 Mo |
Principes : backends sur 127.0.0.1, seul le proxy public derrière TCPShield, **BungeeGuard** ; une partie = un objet Match + un slot d'arène, pas d'état global ; le serveur de jeu écrit le résultat (ELO) en une transaction MariaDB puis publie dans Redis ; le site ne touche jamais la base de jeu ; **clients 1.8.x seulement** au lancement (vanilla, Lunar, Badlion, Rago Client), ViaVersion repoussé.

## Modules prévus
`rago-combat-model`, `rago-combat-engine` (**faits**) ; `rago-core`, `rago-arena`, `rago-bedwars` (**fait**, autrement), `rago-ranked`, `rago-practice` (**fait**), `rago-stats`, `rago-lobby` (**fait**, il porte aussi stats, ranked, cosmétiques), `rago-cosmetics`, `rago-proxy` (Velocity), `rago-messaging`, `rago-data` (migrations MariaDB), `rago-api` ; outils `kb-recorder`, `kb-analyzer` (**faits**), `load-bot` ; `deploy/` (systemd, backups, nftables). Schéma MariaDB prévu : classement par joueur, saison et file ; historique de match immuable avec le profil de KB ; UUID en BINARY(16) ; utilisateurs `rago_game`, `rago_api` (lecture seule), `rago_backup`.

## Défis listés
1 KB proche d'Hypixel (mesurer, pas deviner) · 2 liquidité des files ranked (fenêtre d'ELO qui s'élargit) · 3 hit registration (20 TPS stables, rien de lourd sur le thread principal) · 4 anticheat compatible avec un KB modifié · 5 remise à zéro des arènes.

## Feuille de route
| Phase | Semaines | Contenu | État au 25/11 |
|---|---|---|---|
| **M1 Labo KB** | 1-3 | recorder, analyzer, modèle, premier profil | **fait** (07-11/11) |
| P1 Infrastructure | 2-4 | machine, Velocity, TCPShield, BungeeGuard, MariaDB, Redis, backups, rago-core, rago-data | **pas commencée** |
| P2 Combat complet | 3-6 | tous les scénarios, paliers de ping, anticheat | en partie (mêlée mesurée ; explosions, anticheat non) |
| P3 Bedwars 1v1 | 5-9 | arènes, rago-bedwars, première map | fait et bien au-delà |
| P4 Ranked 1v1 | 8-10 | matchmaker dans le proxy, ELO, divisions, classements | fait dans le lobby (sans proxy) |
| P5 Lobby et bêta fermée | 10-12 | lobby, menus, stats, 30-50 testeurs, test à l'aveugle du KB | lobby fait ; bêta pas faite |
| P6 Lancement public | après 12 | Rago Client, Discord, saison 1 | préparé par le [[Plan de sortie publique]] |

## Questions du plan (restées ouvertes)
Quel domaine (il sert aussi de package Java ; le code utilise `gg.rago`) → q127 · compte Hypixel MVP++ pour les parties privées ? · accepter un jour les clients 1.9+ ?
Intégration Rago Client prévue : entrée serveur ajoutée par défaut mais supprimable, configuration distante en JSON signé Ed25519.
