#!/usr/bin/env bash
# Serveur de test local Rago Practice (macOS et Linux) : PandaSpigot 1.8.8 avec RagoCombatEngine,
# WorldEdit, PacketEvents et les plugins Rago. Mode d'emploi : LISEZ-MOI.txt. Lancement : bash lancer-mac-linux.sh
#
# Réglages facultatifs (variables d'environnement) :
#   RAGO_JAVA          programme java à utiliser (par défaut : $JAVA_HOME/bin/java, sinon java)
#   RAGO_ACCEPTE_CLUF  "oui" pour accepter le CLUF de Minecraft sans question (lancement automatique)
#   RAGO_MEMOIRE       mémoire maximale du serveur (par défaut 2G)
#   RAGO_DONNEES       dossier des mondes, des schematics et des sauvegardes (par défaut : ~/Rago)
#   RAGO_SAUVEGARDES   nombre de sauvegardes des mondes gardées (par défaut 10 ; 0 : pas de sauvegarde)
# Compatible avec le bash 3.2 de macOS.
set -euo pipefail

# Le build de PandaSpigot sur lequel le test de contrôle du moteur passe.
PANDASPIGOT_URL=https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip
PANDASPIGOT_SHA256=ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b
SERVER_JAR=pandaspigot-226.jar
# WorldEdit 6.1, la dernière version pour Bukkit 1.8 (https://dev.bukkit.org/projects/worldedit/files/880435).
WORLDEDIT_URL=https://mediafilez.forgecdn.net/files/880/435/worldedit-bukkit-6.1.jar
WORLDEDIT_SHA256=b9d3f0648e47424d66d94081b21a26504de0fbe6843f3d977c023aec066d4496
WORLDEDIT_JAR=plugins/worldedit-bukkit-6.1.jar
# PacketEvents 2.14.0, dont RagoLobby a besoin pour ses PNJ (https://github.com/retrooper/packetevents).
PACKETEVENTS_URL=https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
PACKETEVENTS_SHA256=0000000000000000000000000000000000000000000000000000000000000000
PACKETEVENTS_JAR=plugins/packetevents-spigot-2.14.0.jar

cd "$(dirname "$0")"

say() { printf '[Rago] %s\n' "$*"; }
stop_with() { printf '[Rago] ERREUR : %s\n' "$*" >&2; exit 1; }

# --- Java ---------------------------------------------------------------------------------------
if [ -n "${RAGO_JAVA:-}" ]; then
  JAVA=$RAGO_JAVA
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA=$JAVA_HOME/bin/java
else
  JAVA=java
fi
# Sur macOS, /usr/bin/java existe même sans Java installé : seul "java -version" dit s'il marche.
if ! java_version=$("$JAVA" -version 2>&1); then
  stop_with "Java est introuvable. Installe Java 21 (https://adoptium.net/fr/temurin/releases/?version=21)" \
    "ou Java 8, puis relance ce script."
fi
java_version=$(printf '%s\n' "$java_version" | grep -m1 -i 'version' || true)
say "Java : ${java_version:-version inconnue}"
major=$(printf '%s\n' "$java_version" | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/')
case "$major" in
  "1 "*) major=${major#1 } ;;
  *) major=${major%% *} ;;
esac
case "$major" in
  ''|*[!0-9]*) ;;
  *) if [ "$major" -lt 8 ]; then stop_with "il faut Java 8 ou plus récent."; fi ;;
esac

# --- CLUF de Minecraft --------------------------------------------------------------------------
if ! grep -qs '^eula=true' eula.txt; then
  say "Pour lancer un serveur Minecraft, il faut accepter le CLUF de Minecraft :"
  say "https://aka.ms/MinecraftEULA"
  answer=${RAGO_ACCEPTE_CLUF:-}
  if [ -z "$answer" ]; then
    printf '[Rago] Tu acceptes le CLUF de Minecraft ? (oui/non) '
    read -r answer || answer=
  fi
  case "$answer" in
    oui|Oui|OUI|o|O) printf 'eula=true\n' > eula.txt ;;
    *) stop_with "CLUF non accepté : le serveur ne démarre pas." ;;
  esac
fi

# --- Téléchargements -----------------------------------------------------------------------------
sha256_of() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1"; else sha256sum "$1"; fi | cut -d' ' -f1
}
# fetch_verified <nom> <adresse> <somme SHA-256> <fichier> : télécharge le fichier s'il manque, puis
# vérifie sa somme. Un fichier qui n'a pas la bonne somme est supprimé.
fetch_verified() {
  if [ ! -s "$4" ]; then
    say "Téléchargement de $1..."
    curl -fL --retry 3 --retry-delay 5 -o "$4.part" "$2" \
      || stop_with "le téléchargement de $1 a échoué. Vérifie ta connexion et relance."
    mv "$4.part" "$4"
  fi
  actual_sha256=$(sha256_of "$4")
  if [ "$actual_sha256" != "$3" ]; then
    rm -f "$4"
    stop_with "le fichier $1 téléchargé n'est pas le bon (somme $actual_sha256). Il a été supprimé :" \
      "relance le script pour le télécharger à nouveau."
  fi
}
mkdir -p plugins
fetch_verified "PandaSpigot 1.8.8 (build 226)" "$PANDASPIGOT_URL" "$PANDASPIGOT_SHA256" "$SERVER_JAR"
fetch_verified "WorldEdit 6.1" "$WORLDEDIT_URL" "$WORLDEDIT_SHA256" "$WORLDEDIT_JAR"
fetch_verified "PacketEvents 2.14.0" "$PACKETEVENTS_URL" "$PACKETEVENTS_SHA256" "$PACKETEVENTS_JAR"

# --- Mondes, schematics et sauvegardes ------------------------------------------------------------
# Ils sont rangés hors de ce dossier : une nouvelle version du serveur de test les retrouve.
DATA=${RAGO_DONNEES:-$HOME/Rago}
WORLDS=$DATA/mondes
SCHEMATICS=$DATA/schematics
BACKUPS=$DATA/sauvegardes
mkdir -p "$WORLDS" "$SCHEMATICS" "$BACKUPS" || stop_with "impossible de créer le dossier $DATA."
say "Mondes et schematics : $DATA"

keep=${RAGO_SAUVEGARDES:-10}
case "$keep" in
  ''|*[!0-9]*) stop_with "RAGO_SAUVEGARDES doit être un nombre (par exemple 10), pas « $keep »." ;;
esac
if [ "$keep" -gt 0 ] && { [ -n "$(ls -A "$WORLDS")" ] || [ -n "$(ls -A "$SCHEMATICS")" ]; }; then
  archive=$BACKUPS/mondes-$(date +%Y-%m-%d_%H-%M-%S).tar.gz
  say "Sauvegarde des mondes et des schematics : $archive"
  if ! tar -czf "$archive.part" -C "$DATA" mondes schematics; then
    rm -f "$archive.part"
    stop_with "la sauvegarde des mondes a échoué (disque plein ?). Le serveur ne démarre pas, pour ne" \
      "rien abîmer. Libère de la place, ou lance avec RAGO_SAUVEGARDES=0 pour démarrer sans sauvegarde."
  fi
  mv "$archive.part" "$archive"
  # Garde les $keep plus récentes : les noms sont datés, l'ordre alphabétique est l'ordre des dates.
  ls -1 "$BACKUPS" | { grep '^mondes-.*\.tar\.gz$' || true; } | sort -r | tail -n "+$((keep + 1))" |
    while IFS= read -r old; do rm -f "$BACKUPS/$old"; done
fi

# WorldEdit range ses schematics avec les mondes. Ce fichier n'est écrit que s'il n'existe pas : les
# autres réglages de WorldEdit gardent leur valeur par défaut.
if [ ! -f plugins/WorldEdit/config.yml ]; then
  mkdir -p plugins/WorldEdit
  {
    printf '# Écrit par le lanceur Rago : les schematics sont rangés avec les mondes (%s).\n' "$DATA"
    printf 'saving:\n'
    printf "  dir: '%s'\n" "$(printf '%s' "$SCHEMATICS" | sed "s/'/''/g")"
  } > plugins/WorldEdit/config.yml
fi

# --- Démarrage ----------------------------------------------------------------------------------
say "Démarrage du serveur. Dans Minecraft 1.8.9 : Multijoueur, Connexion directe, adresse localhost."
say "Pour l'arrêter, tape stop ici puis Entrée."
exec "$JAVA" "-Xmx${RAGO_MEMOIRE:-2G}" -DIReallyKnowWhatIAmDoingISwear=true -jar "$SERVER_JAR" \
  --world-container "$WORLDS" nogui
