---
type: fonctionnalité
tags: [lobby, ragolobby]
maj: 2026-11-28
code: bukkit/rago-lobby
---
# Lobby

Le lobby principal de Rago Practice, joué par le plugin **RagoLobby** (`bukkit/rago-lobby`, le plus gros module : 128 classes, environ 42 000 lignes sur la branche de la PR #47). Tout ce qui n'est pas une partie passe par lui : spawn, PNJ, boussole, files, party, stats, boutique, staff. Les plugins de jeu (RagoPractice, RagoBedwars) lui demandent leurs joueurs et lui rendent les résultats ([[Architecture]]). Détail complet : `bukkit/rago-lobby/README.md` (1 055 lignes). Code : [[Code de rago-lobby]]. Retour à [[Projet]].

## Arrivée
- Au spawn du lobby (monde `lobby`, spawn dans `spawn.yml` du monde, posé par `/lobby setspawn`), en **aventure**, vie et faim pleines. Un titre resté d'un autre serveur est effacé.
- **Accueil** à chaque arrivée : le nom et le slogan « Train. Clutch. Climb. », puis les façons de jouer, une ligne par catégorie, cliquables (`accueil: true`).
- **Première venue** : deux secondes après, le menu « What do you want to do? » (`premiere-venue:`), quelques choix à un clic de jouer, et le livre « Getting started » ([[Progression et récompenses]]).
- L'arrivée de chacun s'annonce au lobby ; les rangs payants l'annoncent avec un feu d'artifice (`rago.perk.join`).

## Règles du lobby
Pas de dégâts, pas de faim, rien à poser ou casser (sauf en mode build du staff, `/lobby build`) ; ciel clair à midi dans tous les mondes du serveur (chacun garde l'heure de ses réglages, « Time of day ») ; chute dans le vide = retour au spawn (`hauteur-vide`). Les **slimes** sont des tremplins (`slime: {avant: 3.5, haut: 1.3}`, environ 30 blocs loin et 9 de haut). Vitesse du lobby Speed I à III pour PREMIUM et VIP (réglages).

## La barre d'objets
Dans l'ordre : **boussole** (sélecteur des modes, [[Boussole et menus]]), **livre** (éditeur de kit), une case libre, **gadget** (bâton de téléportation à 20 blocs par défaut ; aussi grappin, pistolet à chauve-souris, mouton explosif, boule disco, plume de saut…), **fireball** (fireball jump, ne blesse personne, une par demi-seconde), une case libre, **colorant rouge** (quitter la file) ou **papier Play again** en 7e case, **émeraude** (boutique) et **tête** (profil, niveau, quêtes). Classe : `HotbarItem`, `LobbyGadgets`.

## L'éditeur de kit
Le livre range les modes qui ont un `kit:` comme la boussole (22/11, 19h51) : les catégories d'abord, puis leurs kits triés par nom, « Also for » quand un kit sert à plusieurs modes (`kit-de:`), Save et Cancel ramènent à la liste. Le rangement de chaque joueur est gardé dans `plugins/RagoLobby/kits/<uuid>.yml` et rendu à chaque partie ; en Bed Fight, le kit est rendu tel que le joueur l'avait rangé **dans la partie** au moment de sa mort (bug 1.5 du 21/11). En bas du livre, l'émeraude et le coffre de l'[[Import Hypixel]].

## PNJ, points et décor
- Les **points** du lobby sont posés par `/lobby point <nom>` et gardés dans `mondes/lobby/points.yml` ; chaque PNJ de `pnj:` (config.yml) se tient sur un point. Les corps des PNJ sont de faux joueurs envoyés par paquets (PacketEvents), mains vides, skin de leur catégorie (`NpcSkins`, `skins.yml`, signés par MineSkin), sur un **bloc** : andésite polie par défaut, **quartz** sous les 5 PNJ pour jouer, **fer** sous les PNJ de stats, **émeraude** sous le Shop, **diamant** sous le Daily Chest et les produits, **obsidienne** un bloc plus haut sous les personnes.
- PNJ de modes (ouvrent leur catégorie de la boussole) dont **Ranked** (sur le point du PNJ Fun Practice depuis le 23/11, nom rouge gras comme Arenas, skin `ranked` à confirmer) ; le PNJ **Fun Practice** se pose avec `/lobby point funpractice`.
- **Stats** (le profil du joueur au-dessus de la tête, [[Statistiques et classements]]), **Shop** (point `newshop`), **quêtes** (point `newquest`, `point-pnj-quetes:`), **Daily Chest** (point `newdailychest`, `point-coffre-du-jour:`), **sélecteur de leaderboard** (point `newleaderboardselector`).
- **Où regardent les PNJ** : vers le spawn par défaut ; avec `regard: point` (27/11, liste d'Abel, PR #54), dans le sens où leur point a été posé (le regard de celui qui l'a posé). Le **Shop** et les **Leaderboards** l'ont : ils regardent le centre du lobby. Le **coffre du jour** se tourne aussi dans le sens de son point (`DailyChest`).
- **OUR OTHER PRODUCTS** : PNJ « Rago Client » et « Rago Optimizer » (`type: produit`, points `ragoclient` et `ragooptimizer`, skin de qRago), un clic écrit `rago-optimizer.com` et `discord.gg/ragooptimizer` dans le chat ; derrière eux, les panneaux `rago-client` et `rago-optimizer`.
- **Personnes** : « [OWNER] qRago » et « [DEV] kaliso_dev » (`type: personne`, points `qrago` et `kalisodev`, skin lu chez Mojang par `skin-joueur:`, pas encore vu en jeu). Les modérateurs jncl et nativ ont été retirés le 22/11.
- **Salle des remerciements** (point `textremerciement`, `textes:`) : le texte de remerciement en anglais (validé le 22/11 : « Le texte est parfait »), avec qRago et kaliso_dev ; sur les côtés, deux blocs « INFORMATION : » de quatre lignes. Celui de gauche (`textremerciement`) a un **nouveau texte depuis le 27/11** (liste d'Abel) : « A server to train and have fun with friends. Practice modes found nowhere else, made by us for real game situations, to help you improve. » ; l'autre (`textremerciement2` : Hypixel KB, low ping, Discord) ne change pas.
- **Texte de RagoBot** (27/11, `textes.ragobot`, sur le point `ragobot`, posé en -3 65 10 sur le serveur de test d'après le `config.yml`) : « /p invite RagoBot · No friends online? RagoBot plays with you, any time. · Every mode, like a real player, 40 ms ping. · Warm up before Ranked, or learn with no pressure. » ([[RagoBot]]).
- **Podiums** et murs de classement (`classements:`), mur des records des entraînements (point `records`, [[Records des entraînements]]).
- **Panneaux publicitaires** (`panneaux:`, PR #41) : des images du dossier `plugins/RagoLobby/panneaux/` dessinées sur des murs de cartes (cadres), comme sur Hypixel ; le serveur de test a 46 lignes de `panneaux:`. Testé avec un vrai client 1.8.9 en CI (PR #45).
- Points prévus sans PNJ relié : `funmod2`, `fpsbenchmark` ([[Plan de sortie publique]]).

## Couleurs et textes
Couleurs du logo : rouge, blanc, gris (`Brand`). Depuis le 22/11 (« moins de rouge ») : **rouge clair** `&c` pour la signature (RAGO PRACTICE, préfixe `&c&lRAGO &8» `), une erreur ou l'alerte d'un spectateur ; **jaune** `&e` pour ce qu'on peut faire (clics, commandes, liens, l'adresse `rago.gg` en bas du sidebar et du Tab) ; **or** `&6` pour les titres, les Rago Points ; **bleu** `&9` pour la party et les amis ; catégories : Practice `&b`, Bedwars Modes `&6`, Duels `&a`, Arenas `&c`, Fun Practice `&d`. Rangs : VIP `&b`, PREMIUM+ `&d`, Mod `&e`. Liste des serveurs (`motd:`) : « RAGO PRACTICE • Bed Wars Practice, Ranked & Duels » puis « NEW » et la nouveauté (aujourd'hui « Hypixel knockback, prestiges & season badges »), 270 pixels au plus par ligne. Icône : `server-icon.png` 64×64, le R blanc sur hexagone rouge.

## Sidebar, Tab, chat
- Sidebar (`LobbyScoreboard`) : peu de mots, joueurs en ligne, niveau Network et rang, la file où l'on attend et depuis combien de temps (en jaune), l'adresse en bas.
- Tab (`ServerInfo`) : en-tête et pied (joueurs, ping), couleur du rôle dans le Tab seulement, rangs du plus haut au plus bas.
- Au-dessus de la tête : le **Network Level** par défaut (plan 3.5), ou ce que le joueur choisit (`/display` : ELO, niveau, victoires, kills, série, titre, message PREMIUM) ; l'ELO en partie classée ; la vie sous le nom.
- Chat : sans rang, 2 secondes entre deux messages et pas deux fois le même ; nom en couleur du rang ; `NetworkRanks` : « [12✫] [Sumo Gold III] [ADMIN] Nom: message ». Annonces du lobby (`annonces:`).

## Liens
`/discord` → `discord.gg/ragooptimizer`, `/site` et `/store` → `rago-optimizer.com` (`liens:`, choix de Sofiene du 22/11) ; `/rules` : quatre règles (pas de triche, pas d'insultes, pas de teaming, respect du staff).

## Voir aussi
[[Party, amis et messages]] · [[Rangs, boutique et cosmétiques]] · [[Progression et récompenses]] · [[Staff et modération]] · [[Replays et spectateurs]] · [[Configuration]] · [[Commandes et permissions]]
