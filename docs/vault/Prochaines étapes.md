---
type: pilotage
tags: [pilotage, à-faire]
maj: 2026-11-26
---
# Prochaines étapes

Ce qui reste à faire avant la sortie publique, dans l'ordre du plan de Sofiene. Le détail tâche par tâche : [[Plan de sortie publique]]. Ce qui attend Abel et Sofiene : [[État actuel]]. Retour à [[Projet]].

## Tout de suite
1. **Abel, en jeu** : poser `salle-attente:` sur chaque map de Bedwars Normal (`/bw edit <map>`, debout sur le sol voulu, `/bw lobbyfight`) pour que la party attende au-dessus de sa map ([[Parties privées]]) ; poser les spawns de First Rush (pas encore faits, seules Nebuc et Gelato en ont) ([[Bed Wars]]) ; faire la séance de mesure des boules de feu et de la TNT ([[Boules de feu et TNT]]).
2. **Recevoir la liste d'Abel** (maps, modes et features à ajouter, annoncée le 26/11), puis la traiter comme les listes d'avant : un commit par point, CI, installation après l'accord de Sofiene.
3. Essai en jeu de la version installée le 25/11 (`2de1bca`) : **À VÉRIFIER** s'il est encore à faire (pas dans la liste d'Abel du 26/11). Les essais en jeu sont faits par Abel.
4. Valider les propositions en attente : RagoBot qui suit la map et pose des blocs, build du lobby, fireballs de Minemen (23/11) ; duels entre parties dans tous les modes (25/11) ; étape 8 ([[Questions ouvertes]]).
5. Fermer les PR #48 et #10 sans fusion (décidé le 26/11, en attente du « GO » de Sofiene) ; décider de fusionner la PR #47 dans `main` ([[Branches et PR]]).

## Étapes du plan encore ouvertes
| Étape | Reste à faire | Statut |
|---|---|---|
| 2.6 / 2.7 Fireballs et TNT | séance de mesure d'Abel sur Hypixel (ou Minemen) avec kb-recorder 0.2.0, puis régler | BLOQUÉ ([[Boules de feu et TNT]]) |
| 2.8 Fast Shop et API Hypixel | une clé API Hypixel valide ; Sofiene refait une demande | BLOQUÉ ([[Import Hypixel]]) |
| 1.7 Spawns First Rush | fait sur Nebuc et Gelato ; sur les autres maps, les spawns sont **à poser en jeu par Abel** (pas encore faits au 26/11 ; le point Hypixel de référence par `/bw spawn red` sur une île, recopié sur les 7 autres) | À FAIRE ([[Bed Wars]]) |
| 2.3 Bed Break | plateforme de départ à tester en jeu | À TESTER |
| 5.2 Build monumental « RAGO PRACTICE » du lobby | proposition du 23/11 à valider (image de référence reçue) | BLOQUÉ ([[Lobby]]) |
| Top Bed Fight 2v2 | la map à envoyer (demande du 23/11) ; encore attendue ? Abel dit le 26/11 que toutes les maps et tous les modes dont il avait parlé sont finis | À VÉRIFIER |
| Attack and Defense | la vision à expliquer (23/11) ; encore attendue ? (même réponse d'Abel du 26/11) | À VÉRIFIER |
| 6.5.3 RagoBot | étapes 4 à 6 de l'ordre (déplacement sur la map, blocs, combo) | BLOQUÉ ([[RagoBot]]) |
| 7.2 Rejoindre une partie en cours (admin) | proposition du 22/11 : `/join <joueur>`, partie non comptée | BLOQUÉ |
| 7.3 Anticheat | proposition du 22/11 : Grim (gratuit, Java 17+), alertes seules pendant un essai | BLOQUÉ |
| 7.4 FPS benchmark | PNJ `fpsbenchmark`, faux joueurs par paquets | BLOQUÉ |
| 8.1 Hébergeur européen ≤ 30 €/mois | comparatif d'offres réelles ; **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) | DÉCIDÉ : mis de côté ([[Hébergement et production]]) |
| 8.2 Site | vérifier, liens (invitation Discord), Netlify, domaine ; **mis de côté jusqu'à ce que le serveur soit fini** (Abel, 26/11) | DÉCIDÉ : mis de côté ([[Site]]) |
| 9 Sécurité, tests finaux | modes, stats, lobby, social, performances | À FAIRE |

## Plus tard (prévu dans le code ou l'architecture, pas commencé)
- Proxy Velocity, base MariaDB et Redis, plusieurs serveurs : les stats passeront des fichiers YAML à la base (`rago-data`) ([[Plan d'architecture v1]]).
- Boutique en ligne (Tebex, API Headless) sur le site ; aujourd'hui `/store` et le pack renvoient à `rago-optimizer.com` ([[Rangs, boutique et cosmétiques]]).
- Cosmétiques de lobby (chapeaux, familiers).
- Records « Daily » des entraînements (Lifetime et Weekly faits).
