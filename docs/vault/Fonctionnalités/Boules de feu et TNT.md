---
type: fonctionnalité
tags: [fireball, tnt, explosions]
maj: 2026-11-28
code: bukkit/rago-bedwars (FireballBlast, BedwarsGame), bukkit/rago-practice (Fireballs, PracticeGame.tntBlast), bukkit/rago-lobby (api/FireballAim)
---
# Boules de feu et TNT

Retour à [[Knockback et combat]]. Plan 2.6 (Fireballs) et 2.7 (TNT) du suivi.

## Ce qui est en place (IMPLÉMENTÉ, valeurs de BedWars1058)
- Clic droit : la boule part là où on regarde ; **vitesse 1,0, rayon 3, recul 1,0 horizontal et 0,65 vertical, 2 dégâts (1 cœur) à soi et aux autres, 0 aux coéquipiers** (`boule-de-feu:` du `config.yml` de RagoBedwars). BedWars1058 est la copie ouverte du Bed Wars d'Hypixel.
- Blocs : jamais le verre blast-proof, l'obsidienne ni les lits ; l'end stone résiste aux boules de feu, pas à la TNT ([[Bed Wars]]).
- Le même code sert au practice (`Fireballs`), au lobby (fireball jump de la 5e case, une par demi-seconde, sans dégât ni bloc cassé) et au Lobby Fight. Fireball Fight de Fun Practice : recul 1,5× et 1,2× ([[Fun Practice]]).

## Les plugins de fireball proposés par Abel : avis du 28/11, ne pas installer
Abel cherchait une piste provisoire (26/11 : « ça peut nous dépanner pour l'instant, on verra pour la suite ») et a demandé un avis sur deux plugins. **Aucun n'est installé.**
- **« BedWars1058 DirectionalFireballs »** (spigotmc, ressource 130703, auteur Hypnr) : gratuit, versions annoncées 1.7 à 1.21, Java 8+, sorti le 10/12/2025 ; mais il **dépend de BedWars1058 v22.2+**, que Rago n'utilise pas (RagoBedwars est notre propre plugin) ; ses valeurs de knockback sont des **réglages par défaut, pas des mesures**.
- **« Fireball Launcher »** (builtbybit, ressource 64851) : page illisible (Cloudflare, erreur 403) : prix et versions **non vérifiés**.
- Les deux feraient **doublon ou conflit** avec les boules de feu de RagoBedwars (`FireballBlast`) et du practice.

**Avis : ne pas les installer.** La vraie solution reste la séance de mesure avec kb-recorder (ci-dessous). La règle « ne jamais inventer une valeur de knockback » vaut toujours ([[Règles de travail]]). Un plugin ajouté un jour se note dans `docs/compatibility.md` ([[Dépendances et compatibilité]]).

## Ce qui manque : la mesure (BLOQUÉ, il faut une séance d'Abel)
Les valeurs n'ont **jamais été mesurées sur Hypixel**. La séance de mesure est **à faire par Abel** (confirmé le 26/11, [[État actuel]]). Le protocole est écrit : `docs/combat/protocole-explosions.md`. Il demande le mod **kb-recorder 0.2.0** (artefact `rago-kb-recorder` de la CI ; le 0.1.0 n'enregistre pas les explosions), deux comptes (qRago, le compte d'Abel, et godemy) et une partie de Bed Wars : fireball jump (immobile, sprint, en l'air), boule de feu sur un autre joueur à 1, 2, 3 blocs et direct, blocs (laine, bois, argile, end stone, verre, obsidienne), TNT (jump et blocs), trajet. `/kbrec start explosions`, `/kbrec mark <texte>`, `/kbrec stop`, puis `kb-analyzer --explosions`. **Rien ne change dans le jeu sans ces mesures** ; elles iront dans `mesures-hypixel.md` comme mesure 3.
