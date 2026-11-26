# Rago Practice (serveur Minecraft) : à lire avant tout travail

La mémoire du projet est le vault Obsidian `docs/vault/`. **Règle n°1 de Sofiene : le vault doit correspondre en tout point à l'état actuel du projet.** Chaque Claude qui travaille sur le projet en est responsable.

1. **Avant** : lire `docs/vault/Projet.md`, `docs/vault/État actuel.md`, `docs/vault/Règles de travail.md` et les notes du sujet. Pour coder ou dépanner : `docs/vault/Technique/Reprendre le développement.md`. Comparer avec le code, les PR et le serveur de test ; corriger tout écart. Lire les réponses de Sofiene pas encore traitées sur la page « Questions du vault Rago » (voir `docs/vault/Questions ouvertes.md`).
2. **Pendant** : repérer toute info qui change le projet (fonctionnalité ajoutée, changée ou abandonnée, version, décision, bug, chiffre).
3. **À la fin de chaque tâche, avant de dire que c'est fini** : mettre le vault à jour en suivant `docs/vault/Mettre à jour le vault.md`. Ce qui est abandonné est supprimé (une ligne reste dans « Décisions » > « Abandonné »).
4. **En cas de doute** : ajouter soi-même la question sur la page « Questions du vault Rago », écrire « À VÉRIFIER », prévenir Sofiene en une ligne. Ne jamais deviner.

Le travail le plus récent sur le jeu est sur la **PR #47** (`claude/project-thread-wi9w0x`), pas sur `main`. Le vault, ce fichier et `.claude/` (hook Rago Hub) sont sur `main` seulement : une mise à jour du vault part d'une branche de `main` et passe par une PR vers `main` (voir `docs/vault/Technique/Branches et PR.md`).

Règles qui ne bougent jamais : rien sur GitHub ni sur le serveur de test sans demande explicite de Sofiene ou d'Abel (**depuis le 26/11, l'accord d'Abel suffit, au même titre que celui de Sofiene, pour GitHub et pour le serveur de test**) ; staging d'abord, production après validation ; **aucun secret écrit nulle part (le dépôt est public)** ; ne jamais inventer une valeur de knockback (elles viennent de mesures) ; tout ce qui tourne dans le serveur reste compatible Java 8. Répondre à Sofiene et à Abel en français ; le jeu et les commits sont en anglais.
