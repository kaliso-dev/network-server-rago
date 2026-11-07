# Serveur de test Rago Practice sur ton PC

Ce dossier lance sur ton ordinateur un serveur Minecraft 1.8.8 (PandaSpigot) avec le moteur de
combat RagoCombatEngine, pour essayer le knockback en jeu avec ton client 1.8.9 (Rago Client ou
le client normal). Il sert aussi à construire les maps et les lobbys : WorldEdit et les outils de
build Rago sont installés, et tes mondes sont sauvegardés à chaque lancement.

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

Le premier lancement télécharge PandaSpigot, WorldEdit puis le serveur Minecraft 1.8.8 de Mojang :
il prend une minute ou deux. Le serveur est prêt quand la fenêtre affiche une ligne qui commence par
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
- /kb sparring : un attaquant invisible te frappe toutes les secondes, sans te faire de dégâts,
  pour sentir le knockback seul. Regarde dans une direction : il est devant toi et te pousse en
  arrière. /kb sparring normal pour des coups sans sprint, /kb sparring alterne pour un coup
  sprint puis un coup normal, /kb sparring sprint 10 pour un coup toutes les 10 ticks (le plus
  rapide d'un vrai combat). /kb sparring stop pour arrêter.
- /kb mode observe : le serveur garde son propre knockback, le moteur ne fait qu'observer.
- /kb mode override : le moteur applique son profil (réglage par défaut).

Pour ajouter un profil de test, modifie plugins/RagoCombatEngine/config.yml puis tape /kb reload.
Les réglages sont expliqués dans la documentation du moteur
(combat/rago-combat-engine/README.md dans le dépôt).


## Construire

Deviens opérateur (« op TonPseudo » dans la fenêtre du serveur), puis dans le jeu :

- /monde creer lobby : crée un monde vide nommé lobby et t'y emmène, en créatif, sur un bloc de
  bedrock. /monde creer lobby plat crée un monde plat à la place.
- /monde tp lobby : y retourner. /monde tp world : revenir au monde du test de knockback (en
  survie).
- /monde : la liste des mondes.
- /monde spawn : place le point d'apparition du monde là où tu es.
- /vitesse 3 : vole plus vite (de 1, la vitesse normale, à 10). /nv : vision nocturne.
- WorldEdit : la hache en bois (//wand) pour sélectionner, puis //set, //replace, //copy,
  //paste, //undo... Le guide de WorldEdit est sur https://worldedit.enginehub.org/en/latest/ ;
  il est écrit pour WorldEdit 7, mais les commandes de base sont les mêmes en 6.1.

Dans un monde de build, il ne pleut pas, il fait toujours jour, le feu ne se propage pas, les
feuilles ne disparaissent pas et aucun monstre n'apparaît. Seuls les opérateurs peuvent y poser
ou casser des blocs : un ami qui vient tester le knockback peut visiter sans rien abîmer.

Les noms de monde ne prennent que des minuscules sans accent, des chiffres, - et _.

Pour importer une map téléchargée :

- si c'est un dossier de monde (il contient level.dat et un dossier region) : copie-le dans le
  dossier mondes (voir plus bas), puis tape /monde importer nom-du-dossier. La map doit venir de
  Minecraft 1.8 à 1.12 : une map enregistrée en 1.13 ou plus récent est refusée, parce que le
  serveur 1.8 l'abîmerait ;
- si c'est un schematic (.schematic) : copie-le dans le dossier schematics, crée un monde vide,
  place-toi où la map doit aller, puis tape //schematic load nom-du-fichier et //paste. Les
  fichiers .schem (format de WorldEdit 7, pour 1.13 et plus) ne se chargent pas en 1.8.

Pour garder un build en schematic : sélectionne-le avec la hache, tape //copy, puis
//schematic save mcedit nom.


## Tes mondes et tes sauvegardes

Tes mondes ne sont pas dans ce dossier, mais dans le dossier Rago de ton dossier utilisateur :

- Windows : C:\Users\TonNom\Rago (tape %USERPROFILE%\Rago dans la barre d'adresse de l'explorateur) ;
- Mac : le dossier Rago de ton dossier personnel.

Il contient :

- mondes : un dossier par monde (world est le monde du test de knockback) ;
- schematics : les schematics de WorldEdit ;
- sauvegardes : une copie des mondes et des schematics faite à chaque lancement du serveur, avant
  qu'il démarre. Les 10 plus récentes sont gardées. Sur Windows ce sont des fichiers .zip, sur Mac
  des .tar.gz.

Pendant que le serveur tourne, tous les mondes sont aussi écrits sur le disque toutes les
2 minutes, et /monde sauver le fait tout de suite. Si quelque chose casse un build, //undo de
WorldEdit annule les dernières modifications.

Pour revenir à une sauvegarde : arrête le serveur, renomme le dossier mondes (par exemple en
mondes-avant), puis décompresse la sauvegarde dans le dossier Rago (sur Windows : clic droit,
Extraire tout, en choisissant le dossier Rago). Elle recrée les dossiers mondes et schematics.

Pour envoyer une map : arrête le serveur, puis compresse le dossier du monde (dans mondes) ou
envoie la dernière sauvegarde.


## Mettre à jour le serveur de test

Télécharge la nouvelle version, décompresse-la où tu veux et lance-la : elle retrouve tes mondes,
tes schematics et tes sauvegardes dans le dossier Rago. Il faut seulement redevenir opérateur
(« op TonPseudo »). Tu peux supprimer l'ancien dossier.


## Arrêter, recommencer

- Pour arrêter : tape « stop » dans la fenêtre du serveur, puis Entrée. Les mondes sont
  enregistrés.
- Pour relancer : relance le même fichier.
- Pour repartir d'un monde de test neuf : arrête le serveur et supprime le dossier world dans
  Rago\mondes. Tes mondes de build ne sont pas touchés.


## Limites

- Sur ton propre PC, le ping est nul : sur un vrai serveur, le ping change la sensation des
  combats, même avec le même knockback.
- Le serveur doit rester ouvert pendant que vous jouez, et il utilise jusqu'à 2 Go de mémoire.


## Pour les développeurs

La CI assemble ce dossier à chaque push (artefact « rago-serveur-de-test ») : les fichiers de
tools/local-server, ce mode d'emploi renommé LISEZ-MOI.txt, et les jars de RagoCombatEngine et de
RagoBuildTools (tools/build-tools) dans plugins/. Elle démarre le serveur deux fois, sur Linux et
Windows, avec Java 8 et Java 21 : elle vérifie que les plugins s'activent, qu'un monde créé au
premier lancement est retrouvé au second, et que le second lancement le sauvegarde.

Les deux scripts téléchargent le build 226 de PandaSpigot (celui sur lequel passe le test de
contrôle) et WorldEdit 6.1, et vérifient leur somme SHA-256. La CI vérifie que les deux scripts
fixent les mêmes sommes, et que celle de WorldEdit correspond au MD5 affiché par dev.bukkit.org.
WorldEdit n'est pas dans le dossier assemblé : il est téléchargé chez son éditeur au lancement.

Le serveur range ses mondes dans RAGO_DONNEES/mondes (option --world-container), les schematics de
WorldEdit dans RAGO_DONNEES/schematics (le script écrit plugins/WorldEdit/config.yml s'il n'existe
pas) et les sauvegardes dans RAGO_DONNEES/sauvegardes.

Réglages facultatifs : RAGO_JAVA (programme java), RAGO_ACCEPTE_CLUF=oui (accepter le CLUF sans
question, pour un lancement automatique), RAGO_MEMOIRE (mémoire maximale, 2G par défaut),
RAGO_DONNEES (dossier des mondes, ~/Rago ou %USERPROFILE%\Rago par défaut) et RAGO_SAUVEGARDES
(nombre de sauvegardes gardées, 10 par défaut, 0 pour démarrer sans sauvegarde).
