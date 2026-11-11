# Serveur de test Rago Practice sur ton PC

Ce dossier lance sur ton ordinateur un serveur Minecraft 1.8.8 (PandaSpigot) avec le moteur de
combat RagoCombatEngine, pour essayer le knockback en jeu avec ton client 1.8.9 (Rago Client ou
le client normal). On y arrive dans le lobby de Rago Practice, d'où on lance un duel sumo contre
un autre joueur. Il sert aussi à construire les maps et les lobbys : WorldEdit et les outils de
build Rago sont installés, et tes mondes sont sauvegardés à chaque lancement.

Le moteur applique pour l'instant le profil vanilla : le knockback exact de Minecraft 1.8.8. Il
n'y a pas encore de profil Hypixel, il viendra des mesures.

C'est un serveur de test, pour toi et quelques amis : il n'est pas fait pour être ouvert au public.

En jeu, tout est en anglais (menus, messages, PNJ, commandes). Les anciennes commandes en français
(/file, /duel accepter, /monde creer...) marchent toujours. La fenêtre du serveur, elle, reste en
français.


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
3. Lis le CLUF de Minecraft (le lien s'affiche) et réponds yes (ou oui) pour l'accepter.
4. Si le pare-feu Windows demande l'accès réseau pour Java : coche seulement « Réseaux privés »
   si des amis sur le même Wi-Fi doivent se connecter, sinon clique sur Annuler (localhost
   marche quand même).

Mac :

1. Décompresse le dossier (double-clic sur le fichier .zip).
2. Ouvre Terminal, tape « bash » suivi d'un espace, fais glisser le fichier lancer-mac-linux.sh
   dans la fenêtre du Terminal, puis appuie sur Entrée.
3. Lis le CLUF de Minecraft (le lien s'affiche) et réponds yes (ou oui) pour l'accepter.

Le premier lancement télécharge PandaSpigot, WorldEdit, PacketEvents (pour les PNJ du lobby) puis
le serveur Minecraft 1.8.8 de Mojang :
il prend une minute ou deux. Le serveur est prêt quand la fenêtre affiche une ligne qui commence par
« Done ». Les lancements suivants sont plus rapides.


## Se connecter

Dans Minecraft 1.8.9 : Multijoueur, Connexion directe, adresse localhost.

Pour utiliser les commandes du moteur, deviens opérateur : dans la fenêtre du serveur, tape
« op TonPseudo » puis Entrée.


## Le lobby et le duel sumo

En te connectant, tu arrives dans le lobby (le monde lobby, une plateforme tant que ta map n'est
pas installée). Dans ta barre d'objets :

- la boussole (clic droit) : le sélecteur des modes de jeu. Clique sur un mode pour entrer dans sa
  file d'attente, clique dessus à nouveau pour la quitter. Pour l'instant le duel sumo et les
  Ranked Bedwars sont ouverts ; les autres modes affichent « Bientôt disponible » ;
- le colorant rouge, quand tu es dans une file : quitter la file ;
- le colorant vert ou gris : cacher ou revoir les autres joueurs du lobby.

À droite, le scoreboard montre les joueurs connectés, ta file et ton temps d'attente.

Le duel sumo : dès que deux joueurs sont dans la file, ils partent sur une plateforme ronde. Le
premier qui tombe donne un point à l'autre, le premier à 3 points gagne. Il n'y a pas de dégâts,
seulement le knockback du moteur : c'est le mode pour tester le knockback à deux. Chaque manche
commence par un compte à rebours de 3 secondes, immobile, affiché en grand à l'écran (3, 2, 1,
« Fight! »). À la fin, les deux joueurs reviennent
au lobby tout de suite.

Commandes :

- /spawn : retour au spawn du lobby. Pendant un duel, c'est un abandon.
- /queue sumo pour entrer dans la file du duel, /queue leave pour en sortir (/file marche aussi).
- /duel Pseudo : défier un ami dans un mode 1v1 (un menu s'ouvre) ; il répond avec
  /duel accept TonPseudo ou /duel deny TonPseudo (ou en cliquant sur [Accept] ou [Deny]).
- /lobby modes : les modes du lobby et leur file.
- /lobby setspawn (opérateurs) : place le spawn du lobby là où tu es.
- /lobby build (opérateurs) : mode construction dans le lobby (créatif, tu peux modifier la map) ;
  retape-le pour en sortir.
- /lobby point nom (opérateurs) : marque un endroit du lobby, où se mettent les PNJ et les
  classements de plugins/RagoLobby/config.yml (le nom du point est leur clé). Retape-le ailleurs
  pour le déplacer ; /lobby points pour la liste, /lobby point tp nom pour y aller,
  /lobby point remove nom pour l'enlever. Les PNJ de stats sont sur statsjoueur1 et
  statsjoueur2, le mur de classements du jour, de la semaine et à vie sur leaderboardstats, le
  classement principal sur leaderboardprincipal (clic droit : Elo, puis Kills, puis niveau
  Network ; clic gauche : la période ou le mode classé), les kills du jour, de la semaine et à vie
  sur leaderboardplayer, avec les séries de victoires à gauche et le niveau Network à droite.
- /monde tp world (opérateurs) : aller au monde plat du test de knockback, comme avant.

Le plus simple pour construire le lobby : construis directement dans le monde lobby. Tape
/lobby build pour passer en créatif (WorldEdit marche aussi), construis, puis place le spawn avec
/lobby setspawn et les emplacements avec /lobby point. Le spawn et les points sont gardés dans le
dossier du monde lobby (dans mondes) : ils restent quand tu mets à jour le serveur de test, et
partent avec la map quand tu me l'envoies.

Si ta map est déjà faite ailleurs : importe-la avec /monde import (voir « Construire »), puis dans
plugins/RagoLobby/config.yml remplace « monde: lobby » par le nom de ton monde, relance le
serveur, va dans ton lobby et tape /lobby setspawn là où les joueurs doivent apparaître. La liste
des modes, leurs noms, leurs icônes et le texte du scoreboard se règlent dans le même fichier.


## Bed Wars

Les modes Ranked Bedwars (1v1 à 4v4) sont ouverts dans le sélecteur, ou avec /queue ranked-1v1.
La partie suit les règles d'Hypixel : lits, générateurs, shop (le villageois de ton île), final
kills. Si tu as copié ton shop d'Hypixel (/hypixel import), ton Quick Buy et ton hotbar manager
sont ceux de la partie.

Au premier lancement, le plugin crée sa propre petite map, bw-demo, pour jouer tout de suite ; elle
ne sert plus dès qu'une vraie map peut accueillir le mode. Une map dont le dossier contient déjà son
bedwars.yml (fait par tools/bedwars-maps) se joue dès qu'elle est dans mondes, au lancement suivant.
Pour installer une autre map : mets le dossier du monde dans mondes, /monde import NomDeLaMap,
/bw edit NomDeLaMap, puis place ses points avec /bw spawn red, /bw bed red, /bw generator red,
/bw shop red (la même chose pour chaque équipe), /bw diamond et /bw emerald sur chaque générateur,
/bw spectators, et /bw enable. /bw info dit ce qui manque, /bw help donne toutes les commandes. Le
détail est dans le README de bukkit/rago-bedwars.


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


## Construire

Deviens opérateur (« op TonPseudo » dans la fenêtre du serveur), puis dans le jeu :

- /monde create mon-lobby : crée un monde vide nommé mon-lobby et t'y emmène, en créatif, sur un
  bloc de bedrock. /monde create mon-lobby flat crée un monde plat à la place. (Le nom lobby est
  déjà pris par le lobby de Rago.)
- /monde tp mon-lobby : y retourner. /monde tp world : revenir au monde du test de knockback (en
  survie). /spawn : revenir au lobby.
- /monde : la liste des mondes.
- /monde spawn : place le point d'apparition du monde là où tu es.
- /speed 3 : vole plus vite (de 1, la vitesse normale, à 10). /nv : vision nocturne.
- WorldEdit : l'os (//wand le donne ; pas la hache en bois, qui est dans les kits des modes) pour
  sélectionner, puis //set, //replace, //copy,
  //paste, //undo... Le guide de WorldEdit est sur https://worldedit.enginehub.org/en/latest/ ;
  il est écrit pour WorldEdit 7, mais les commandes de base sont les mêmes en 6.1.

Dans un monde de build, il ne pleut pas, il fait toujours jour, le feu ne se propage pas, les
feuilles ne disparaissent pas et aucun monstre n'apparaît. Seuls les opérateurs peuvent y poser
ou casser des blocs : un ami qui vient tester le knockback peut visiter sans rien abîmer.

Les noms de monde ne prennent que des minuscules sans accent, des chiffres, - et _.

Pour importer une map téléchargée :

- si c'est un dossier de monde (il contient level.dat et un dossier region) : copie-le dans le
  dossier mondes (voir plus bas), puis tape /monde import nom-du-dossier. La map doit venir de
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

- mondes : un dossier par monde (world est le monde du test de knockback, lobby le lobby,
  arenes les plateformes des duels et bw-demo la map de démo de Bed Wars ; les dossiers bw_...
  sont les copies où se jouent les parties de Bed Wars, supprimées à l'arrêt du serveur ou au
  lancement suivant) ;
- schematics : les schematics de WorldEdit ;
- sauvegardes : une copie des mondes et des schematics faite à chaque lancement du serveur, avant
  qu'il démarre. Les 10 plus récentes sont gardées. Sur Windows ce sont des fichiers .zip, sur Mac
  des .tar.gz.

Pendant que le serveur tourne, tous les mondes sont aussi écrits sur le disque toutes les
2 minutes, et /monde save le fait tout de suite. Si quelque chose casse un build, //undo de
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
tools/local-server, ce mode d'emploi renommé LISEZ-MOI.txt, et les jars de RagoCombatEngine, de
RagoBuildTools (tools/build-tools), de RagoLobby (bukkit/rago-lobby), de RagoPractice
(bukkit/rago-practice) et de RagoBedwars (bukkit/rago-bedwars) dans plugins/. Elle démarre le serveur deux fois, sur Linux et Windows,
avec Java 8 et Java 21 : elle vérifie que les plugins s'activent, qu'un monde créé au premier
lancement est retrouvé au second, et que le second lancement le sauvegarde. Le lobby et le duel
sumo sont joués par deux bots dans un autre test (tools/lobby-check).

Les deux scripts téléchargent le build 226 de PandaSpigot (celui sur lequel passe le test de
contrôle), WorldEdit 6.1 et PacketEvents 2.14.0, et vérifient leur somme SHA-256. La CI vérifie
que les deux scripts fixent les mêmes sommes, que celle de WorldEdit correspond au MD5 affiché par
dev.bukkit.org, et que celle de PacketEvents est celle que GitHub donne pour le fichier de sa
release. WorldEdit et PacketEvents ne sont pas dans le dossier assemblé : ils sont téléchargés
chez leur éditeur au lancement.

Le serveur range ses mondes dans RAGO_DONNEES/mondes (option --world-container), les schematics de
WorldEdit dans RAGO_DONNEES/schematics (le script écrit plugins/WorldEdit/config.yml s'il n'existe
pas) et les sauvegardes dans RAGO_DONNEES/sauvegardes.

Réglages facultatifs : RAGO_JAVA (programme java), RAGO_ACCEPTE_CLUF=oui (accepter le CLUF sans
question, pour un lancement automatique), RAGO_MEMOIRE (mémoire maximale, 2G par défaut),
RAGO_DONNEES (dossier des mondes, ~/Rago ou %USERPROFILE%\Rago par défaut) et RAGO_SAUVEGARDES
(nombre de sauvegardes gardées, 10 par défaut, 0 pour démarrer sans sauvegarde).
