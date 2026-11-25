---
type: fonctionnalité
tags: [hypixel, quick-buy, hotbar, api]
maj: 2026-11-25
code: bukkit/rago-lobby (HypixelProfiles, HypixelProfile, HypixelMenu), LobbyService.hypixelQuickBuy / hypixelHotbar
---
# Import Hypixel

Chaque joueur **copie son shop Bed Wars d'Hypixel** : le **Quick Buy** (21 cases, 3 rangées de 7) et le **hotbar manager** (le genre d'objet de chaque case). Utilisé par Ranked Bedwars, First Rush, Bedwars Normal, les autres modes sur une map de Bed Wars et Fast Shop. Retour à [[Bed Wars]].

- En un clic avec l'émeraude en bas du livre (éditeur de kit, son propre compte), ou `/hypixel import [pseudo]` (n'importe quel compte). `/hypixel shop` ou le coffre à côté montre ce qui a été copié.
- Gardé par joueur dans `plugins/RagoLobby/hypixel/<uuid>.yml` ; les plugins de jeu le lisent par `LobbyService.hypixelQuickBuy(player)` (ids Hypixel, `null` = case vide) et `hypixelHotbar(player)` (`Melee`, `Blocks`, `Tools`, `Ranged`, `Potions`, `Utility`, `Compass`).
- **Il faut une clé d'API Hypixel** dans `hypixel.cle-api` du `config.yml` du lobby (créée sur developer.hypixel.net, une application pour le serveur). **Elle ne se met jamais dans le dépôt ni dans ce vault** ([[Clés et secrets]]). Sans clé, l'import dit qu'il n'est pas encore en place, et l'étape « importer son quick buy » de Getting started n'apparaît pas. Dans le dépôt : `cle-api: ""`.
- Limites : un import par minute et par joueur, cent en cinq minutes pour tout le serveur (sous la limite d'une clé : 300 requêtes / 5 min). Hypixel n'envoie le Quick Buy que si le joueur l'a modifié au moins une fois.
- Sans import, un Quick Buy et une hotbar par défaut.
- **BLOQUÉ (plan 2.8)** : la clé essayée sur le serveur de test a été **refusée par Hypixel (« Invalid API key »)** lors de l'essai du 21/11 ; Sofiene refait une demande de clé (22/11). Tant qu'il n'y a pas de clé valide, l'import ne marche pas (le Fast Shop et les modes Bed Wars prennent le Quick Buy par défaut).
