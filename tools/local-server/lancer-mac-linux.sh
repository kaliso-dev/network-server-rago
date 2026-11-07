#!/usr/bin/env bash
# Serveur de test local Rago Practice (macOS et Linux) : PandaSpigot 1.8.8 avec RagoCombatEngine.
# Mode d'emploi : LISEZ-MOI.txt. Lancement : bash lancer-mac-linux.sh
#
# Réglages facultatifs (variables d'environnement) :
#   RAGO_JAVA          programme java à utiliser (par défaut : $JAVA_HOME/bin/java, sinon java)
#   RAGO_ACCEPTE_CLUF  "oui" pour accepter le CLUF de Minecraft sans question (lancement automatique)
#   RAGO_MEMOIRE       mémoire maximale du serveur (par défaut 2G)
# Compatible avec le bash 3.2 de macOS.
set -euo pipefail

# Le build de PandaSpigot sur lequel le test de contrôle du moteur passe.
PANDASPIGOT_URL=https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip
PANDASPIGOT_SHA256=ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b
SERVER_JAR=pandaspigot-226.jar

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

# --- PandaSpigot --------------------------------------------------------------------------------
sha256_of() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1"; else sha256sum "$1"; fi | cut -d' ' -f1
}
if [ ! -s "$SERVER_JAR" ]; then
  say "Téléchargement de PandaSpigot 1.8.8 (build 226)..."
  curl -fL --retry 3 --retry-delay 5 -o "$SERVER_JAR.part" "$PANDASPIGOT_URL" \
    || stop_with "le téléchargement de PandaSpigot a échoué. Vérifie ta connexion et relance."
  mv "$SERVER_JAR.part" "$SERVER_JAR"
fi
actual_sha256=$(sha256_of "$SERVER_JAR")
if [ "$actual_sha256" != "$PANDASPIGOT_SHA256" ]; then
  rm -f "$SERVER_JAR"
  stop_with "le fichier PandaSpigot téléchargé n'est pas le bon (somme $actual_sha256). Il a été supprimé :" \
    "relance le script pour le télécharger à nouveau."
fi

# --- Démarrage ----------------------------------------------------------------------------------
say "Démarrage du serveur. Dans Minecraft 1.8.9 : Multijoueur, Connexion directe, adresse localhost."
say "Pour l'arrêter, tape stop ici puis Entrée."
exec "$JAVA" "-Xmx${RAGO_MEMOIRE:-2G}" -DIReallyKnowWhatIAmDoingISwear=true -jar "$SERVER_JAR" nogui
