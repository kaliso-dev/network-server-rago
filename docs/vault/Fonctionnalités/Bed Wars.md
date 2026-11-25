---
type: fonctionnalité
tags: [bedwars, ragobedwars, rbw]
maj: 2026-11-25
code: bukkit/rago-bedwars (BedwarsGame, BedwarsHost, Instances…)
---
# Bed Wars

Les modes Bed Wars sont joués par **RagoBedwars** (`bukkit/rago-bedwars`, 59 classes, environ 21 000 lignes) : Ranked Bedwars 1v1 à 4v4 et toutes les variantes de la catégorie **Bedwars Modes** (s'appelait « RBW Modes »), plus les Bed Fight, Top Bridge, CTF, 2017 Bedwars et Bedwars Normal. Chaque partie se joue sur **une copie de la map** chargée comme un monde à part (`bw_<map>_<n>`, `Instance`), refaite après chaque partie. Règles visées : **celles d'Hypixel** (valeurs mesurées sur Hypixel ou reprises de BedWars1058, la copie ouverte du Bed Wars d'Hypixel). Détail complet : `bukkit/rago-bedwars/README.md` (808 lignes). Code : [[Code de rago-bedwars]]. Maps : [[Maps et mondes]]. Retour à [[Catalogue des modes]].

## La partie classique (Ranked Bedwars, Sky Rise, First Rush, Bedwars Normal)
- Chaque équipe sur son île, épée en bois, cuir à sa couleur ; décompte 3 s puis « Fight! ». Sur une map à plus d'îles que d'équipes, des îles éloignées (sauf First Rush : **îles voisines**) et les lits des îles vides sont enlevés.
- **Forges** (`forge:`) : un nœud de fer donne 30 lingots/min (2 à la fois) ; 1 nœud sur les maps lentes, 2 moyennes, 3 rapides, +1 sur les maps à 4 équipes ; or 8/min (12 sur les maps de 4 îles ou moins) ; au plus 48 fers et 16 ors par terre ; deux fois plus vite les 30 premières secondes. Toutes rapides sauf Lighthouse et Lotus (lentes en solo, moyennes en équipe). La file RBW est plus lente (`forge.modes`) : 60 fers et 8 ors/min en 1v1, 90 et 12 en 2v2 à 4v4. **Split de la forge** (PR #42) : un coéquipier dans les 5×5 blocs autour reçoit autant (`partage-rayon: 2.5`), fer et or seulement.
- **Générateurs** : diamant toutes les 30 / 23 / 12 s (tiers I à III, max 4), émeraude 65 / 50 / 35 s (max 2). **Événements** : Diamond II à 6 min, Emerald II à 12, Diamond III à 18, Emerald III à 24, **plus de lits à 30**, fin (égalité) à 45.
- **Shop** (villageois « ITEM SHOP ») : Quick Buy + 7 catégories (Blocks, Melee, Armor, Tools, Ranged, Potions, Utility), 3 rangées de 7, prix d'Hypixel en solo et duo ; le Quick Buy et le hotbar manager sont ceux que le joueur a copiés d'Hypixel ([[Import Hypixel]]), sinon un défaut. Armure gardée, pioche et hache qui perdent un tier à la mort, cisailles gardées. **Améliorations et pièges** au PNJ des upgrades (Sharpened Swords, Reinforced Armor, etc., file de 3 pièges) ; **objets spéciaux** d'Hypixel (`Utilities`) ; coffres d'île et ender chest.
- **Boules de feu** au clic droit (valeurs de BedWars1058 : vitesse 1, rayon 3, recul 1,0 et 0,65, 1 cœur aux autres et à soi, rien aux coéquipiers) ([[Boules de feu et TNT]]).
- **Morts** : le kill revient au dernier qui a frappé dans les 10 s, avec ses ressources ; retour au bout de **5 s** tant que le lit tient (spectateur en attendant), sinon **final kill** ; « X was Y's final #41,000. FINAL KILL! » (total à vie) ; protection au respawn 1,5 s dans tous les modes où l'on revient.
- **Lits** : on ne casse pas le sien ; l'annonce donne le total de lits cassés du joueur. **Blocs** : on ne casse que ce qui a été posé ; pas de bloc à moins de 3 blocs d'un spawn, 2 d'un générateur, ni au-dessus de `hauteur-max` ; explosions : jamais le verre blast-proof, l'obsidienne ni les lits ; l'end stone résiste aux boules de feu, pas à la TNT.
- **Fin** : la dernière équipe gagne ; récap (1st/2nd/3rd Killer) ; 3 s dans l'arène avec Play again et Leave ; `/spawn` en partie = défaite. Un éliminé reçoit [PLAY AGAIN] tout de suite.
- **Déconnexion** (23/11) : la partie continue ; tant qu'un coéquipier vit, on peut revenir ; sinon 3 minutes, puis éliminé (un abandon : files classées fermées un moment). De retour : 10 s en spectateur puis sur son île ; sans lit, éliminé. Vaut pour Ranked Bedwars, Sky Rise, First Rush et Bedwars Normal, pas pour les duels ni 2017 Bedwars.
- En partie classée, la barre d'XP montre l'ELO du joueur dans le mode.

## Les modes
| Mode | Ids | En bref |
|---|---|---|
| **Ranked Bedwars** | `ranked-1v1` à `ranked-4v4` | les 20 maps de la file RBW (Antenna, Aquarium, Archway, Artemis, Boletum, Carapace, Catalyst, Chained, Eastwood, Extinction, Invasion, Katsu, Lectus, Obelisk, Paladin, Planet98, Rise, Stonekeep, Swashbuckle, Treenan), chacune avec sa hauteur max ; ELO par mode ; sur les maps à 4 îles, les îles des côtés gardent forge, shop et upgrades (`forges-partout:`, `shops-partout:`) |
| RBW Sky Rise | `skyrise-1v1`, `skyrise-2v2` | une vraie partie RBW sur SkyRise |
| RBW Sky Rise Resources | `skyrise-resources-1v1/2v2` | Sky Rise avec forges et shops des 6 îles vides à prendre, forges lentes (40 fers, 5 ors/min) |
| First Rush | `first-rush`, `first-rush-duo` | partie réelle sur deux **îles voisines** ; maps duo au choix : Acropolis, Ambush, Aqil, Cascade, Crogorm, Frosted, Gelato, Lighthouse, Lotus, Nebuc ; spawns corrigés sur Nebuc et Gelato (21/11), les autres attendent le point Hypixel |
| First Rush full map | `first-rush-full`, `first-rush-full-duo` | la même avec toute la map (diamants, émeraudes, forges et shops des îles vides) |
| Invis practice | `invis-practice-1v1` à `1v4` | 20 maps RBW, manches de 3 min : un attaquant invisible (potion) depuis le mid contre les défenseurs du lit, chacun attaque à son tour (2 fois de 1v1 à 1v2, 1 fois en 1v3 et 1v4) ; armure cachée comme sur Hypixel ; un départ avant la fin = défaite |
| Bed Break Simulator | `bed-break-simulator-1v1` à `1v4` | Archway ou Invasion : l'attaquant part de la **plateforme du Top Bridge** (en haut de l'escalier rouge relevé sur la map, 65 laines posées par le jeu à chaque manche) avec un de 3 kits ; bridge droit d'un bloc, un virage au plus ; défense RBW (end stone + verre), forges pleines (corrigées sur Invasion, TESTÉ EN JEU), cisailles et hache ; manches de 3 min ; plateforme à tester en jeu |
| Clutch Scenario | `clutch-scenario-1v1` à `1v4` | une vraie partie RBW sur **Sky Rise ou Archway** « à 18 minutes » : bridges déjà posés (vers diamants, mid, et le rush jusqu'à la base ennemie, `Bridges`), la défense du lit perdu cassée ; le joueur seul (tiré au sort, une party reste ensemble) n'a pas de lit et une vie, les autres gardent le leur |
| Attack and Defense | `attack-defense-1v1/2v2` | sur **BioHazard** : les attaquants depuis le pont rose (une vie, fer, kit d'attaque au choix : Rusher…), les défenseurs avec forge, shop et kit de défense ; Protection II des deux côtés ; premier à **3 manches**, rôles échangés à chaque manche ; `/kit` ; Sofiene doit expliquer « sa vision » (23/11) |
| Bedwars Normal | `bedwars-normal` (+ `-4s`, `-2s` cachés) | partie privée d'une party : [[Parties privées]] |

Les duels de RagoBedwars (Bed Fight, Top BedFight, Top Bridge, Lobby Fight 1v1, Red Rover Top Bridge) sont dans [[Duels]] et [[Red Rover et 3 Players]] ; les FFA dans [[Arènes ouvertes]] ; CTF et 2017 Bedwars dans [[Fun Practice]] ; Fast Shop dans [[Entraînements]].

## Réglage des maps
`/bw` (permission `rago.bedwars.admin`) pose les points d'une map dans son `bedwars.yml` ; l'outil `tools/bedwars-maps` les trouve tout seul sur une map d'Hypixel ([[Maps et mondes]]). `parties-max: 8` copies en même temps ; une map de démo (`carte-demo: true`) quand aucune map n'est prête.
