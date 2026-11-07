# Serveur de test Rago Practice sur ton PC

Ce dossier lance sur ton ordinateur un serveur Minecraft 1.8.8 (PandaSpigot) avec le moteur de
combat RagoCombatEngine, pour essayer le knockback en jeu avec ton client 1.8.9 (Rago Client ou
le client normal).

Le moteur applique pour l'instant le profil vanilla : le knockback exact de Minecraft 1.8.8. Il
n'y a pas encore de profil Hypixel, il viendra des mesures.

C'est un serveur de test, pour toi et quelques amis : il n'est pas fait pour être ouvert au public.


## Ce qu'il faut

- Java 21 ou Java 8. Si tu n'as pas Java, installe Java 21 :
  https://adoptium.net/fr/temurin/releases/?version=21
  (sur Windows, prends le fichier .msi et garde les options par défaut).
- Minecraft 1.8.9, et une connexion internet pour le premier lancement.


## Lancer le serveur

Windows :

1. Décompresse le dossier (clic droit, Extraire tout), où tu veux.
2. Double-clique sur lancer-windows.bat.
   Si Windows affiche « Windows a protégé votre ordinateur », clique sur « Informations
   complémentaires » puis « Exécuter quand même ». S'il demande si tu veux exécuter un fichier
   dont l'éditeur n'est pas vérifié, clique sur « Exécuter ».
3. Lis le CLUF de Minecraft (le lien s'affiche) et réponds oui pour l'accepter.
4. Si le pare-feu Windows demande l'accès réseau pour Java : coche seulement « Réseaux privés »
   si des amis sur le même Wi-Fi doivent se connecter, sinon clique sur Annuler (localhost
   marche quand même).

Mac :

1. Décompresse le dossier (double-clic sur le fichier .zip).
2. Ouvre Terminal, tape « bash » suivi d'un espace, fais glisser le fichier lancer-mac-linux.sh
   dans la fenêtre du Terminal, puis appuie sur Entrée.
3. Lis le CLUF de Minecraft (le lien s'affiche) et réponds oui pour l'accepter.

Le premier lancement télécharge PandaSpigot puis le serveur Minecraft 1.8.8 de Mojang : il prend
une minute ou deux. Le serveur est prêt quand la fenêtre affiche une ligne qui commence par
« Done ». Les lancements suivants sont plus rapides.


## Se connecter

Dans Minecraft 1.8.9 : Multijoueur, Connexion directe, adresse localhost.

Pour utiliser les commandes du moteur, deviens opérateur : dans la fenêtre du serveur, tape
« op TonPseudo » puis Entrée.


## Trouver un adversaire

Le knockback se sent quand quelqu'un te frappe. Il te faut donc un deuxième joueur :

- un ami sur le même réseau (même Wi-Fi) : il se connecte avec l'adresse IP locale de ton PC
  (sur Windows, tape ipconfig dans l'invite de commandes et prends « Adresse IPv4 », par exemple
  192.168.1.20) ;
- ou ton deuxième compte Minecraft, dans une deuxième fenêtre du jeu.

Le serveur vérifie les comptes comme un vrai serveur (mode en ligne) : chaque joueur doit avoir
un compte Minecraft.


## Commandes du moteur

- /kb : mode, profil et réglages actuels.
- /kb profiles : les profils et leurs valeurs.
- /kb debug : affiche dans ton chat chaque coup et la vitesse envoyée.
- /kb mode observe : le serveur garde son propre knockback, le moteur ne fait qu'observer.
- /kb mode override : le moteur applique son profil (réglage par défaut).

Pour ajouter un profil de test, modifie plugins/RagoCombatEngine/config.yml puis tape /kb reload.
Les réglages sont expliqués dans la documentation du moteur
(combat/rago-combat-engine/README.md dans le dépôt).


## Arrêter, recommencer

- Pour arrêter : tape « stop » dans la fenêtre du serveur, puis Entrée. Le monde est enregistré.
- Pour relancer : relance le même fichier.
- Pour repartir d'un monde neuf : arrête le serveur et supprime le dossier world.


## Limites

- Sur ton propre PC, le ping est nul : sur un vrai serveur, le ping change la sensation des
  combats, même avec le même knockback.
- Le serveur doit rester ouvert pendant que vous jouez, et il utilise jusqu'à 2 Go de mémoire.


## Pour les développeurs

La CI assemble ce dossier à chaque push (artefact « rago-serveur-de-test ») : les fichiers de
tools/local-server, ce mode d'emploi renommé LISEZ-MOI.txt, et le jar du plugin dans plugins/.
Elle vérifie que le serveur démarre et que le moteur s'active, sur Linux et Windows, avec Java 8
et Java 21.

Les deux scripts téléchargent le build 226 de PandaSpigot (celui sur lequel passe le test de
contrôle) et vérifient sa somme SHA-256. Réglages facultatifs : RAGO_JAVA (programme java),
RAGO_ACCEPTE_CLUF=oui (accepter le CLUF sans question, pour un lancement automatique) et
RAGO_MEMOIRE (mémoire maximale, 2G par défaut).
