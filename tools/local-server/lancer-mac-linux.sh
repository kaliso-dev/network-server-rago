#!/usr/bin/env bash
# Local test server of Rago Practice (macOS and Linux): PandaSpigot 1.8.8 with RagoCombatEngine,
# WorldEdit, PacketEvents and the Rago plugins. How to use it: LISEZ-MOI.txt. Start: bash lancer-mac-linux.sh
#
# Optional settings (environment variables):
#   RAGO_JAVA          the java program to use (default: $JAVA_HOME/bin/java, else java)
#   RAGO_ACCEPTE_CLUF  "yes" (or "oui") to accept the Minecraft EULA without a question (automatic start)
#   RAGO_MEMOIRE       the server's maximum memory (default 2G)
#   RAGO_DONNEES       the folder of the worlds, the schematics and the backups (default: ~/Rago)
#   RAGO_SAUVEGARDES   how many backups of the worlds are kept (default 10; 0: no backup)
# Works with the bash 3.2 of macOS.
set -euo pipefail

# The PandaSpigot build the engine control test passes on.
PANDASPIGOT_URL=https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip
PANDASPIGOT_SHA256=ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b
SERVER_JAR=pandaspigot-226.jar
# WorldEdit 6.1, the last version for Bukkit 1.8 (https://dev.bukkit.org/projects/worldedit/files/880435).
WORLDEDIT_URL=https://mediafilez.forgecdn.net/files/880/435/worldedit-bukkit-6.1.jar
WORLDEDIT_SHA256=b9d3f0648e47424d66d94081b21a26504de0fbe6843f3d977c023aec066d4496
WORLDEDIT_JAR=plugins/worldedit-bukkit-6.1.jar
# PacketEvents 2.14.0, which RagoLobby needs for its NPCs (https://github.com/retrooper/packetevents).
PACKETEVENTS_URL=https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
PACKETEVENTS_SHA256=060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7
PACKETEVENTS_JAR=plugins/packetevents-spigot-2.14.0.jar

cd "$(dirname "$0")"

say() { printf '[Rago] %s\n' "$*"; }
stop_with() { printf '[Rago] ERROR: %s\n' "$*" >&2; exit 1; }

# --- Java ---------------------------------------------------------------------------------------
if [ -n "${RAGO_JAVA:-}" ]; then
  JAVA=$RAGO_JAVA
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA=$JAVA_HOME/bin/java
else
  JAVA=java
fi
# On macOS, /usr/bin/java exists even without Java installed: only "java -version" tells if it works.
if ! java_version=$("$JAVA" -version 2>&1); then
  stop_with "Java was not found. Install Java 21 (https://adoptium.net/temurin/releases/?version=21)" \
    "or Java 8, then run this script again."
fi
java_version=$(printf '%s\n' "$java_version" | grep -m1 -i 'version' || true)
say "Java: ${java_version:-unknown version}"
major=$(printf '%s\n' "$java_version" | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1 \3/')
case "$major" in
  "1 "*) major=${major#1 } ;;
  *) major=${major%% *} ;;
esac
case "$major" in
  ''|*[!0-9]*) ;;
  *) if [ "$major" -lt 8 ]; then stop_with "Java 8 or newer is needed."; fi ;;
esac

# --- Minecraft EULA -----------------------------------------------------------------------------
if ! grep -qs '^eula=true' eula.txt; then
  say "To run a Minecraft server, you must accept the Minecraft EULA:"
  say "https://aka.ms/MinecraftEULA"
  answer=${RAGO_ACCEPTE_CLUF:-}
  if [ -z "$answer" ]; then
    printf '[Rago] Do you accept the Minecraft EULA? (yes/no) '
    read -r answer || answer=
  fi
  case "$answer" in
    yes|Yes|YES|y|Y|oui|Oui|OUI|o|O) printf 'eula=true\n' > eula.txt ;;
    *) stop_with "EULA not accepted: the server does not start." ;;
  esac
fi

# --- Downloads ----------------------------------------------------------------------------------
sha256_of() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1"; else sha256sum "$1"; fi | cut -d' ' -f1
}
# fetch_verified <name> <address> <SHA-256 sum> <file>: downloads the file if it is missing, then
# checks its sum. A file without the right sum is deleted.
fetch_verified() {
  if [ ! -s "$4" ]; then
    say "Downloading $1..."
    curl -fL --retry 3 --retry-delay 5 -o "$4.part" "$2" \
      || stop_with "the download of $1 failed. Check your connection and run again."
    mv "$4.part" "$4"
  fi
  actual_sha256=$(sha256_of "$4")
  if [ "$actual_sha256" != "$3" ]; then
    rm -f "$4"
    stop_with "the downloaded file $1 is not the right one (sum $actual_sha256). It was deleted:" \
      "run the script again to download it again."
  fi
}
mkdir -p plugins
fetch_verified "PandaSpigot 1.8.8 (build 226)" "$PANDASPIGOT_URL" "$PANDASPIGOT_SHA256" "$SERVER_JAR"
fetch_verified "WorldEdit 6.1" "$WORLDEDIT_URL" "$WORLDEDIT_SHA256" "$WORLDEDIT_JAR"
fetch_verified "PacketEvents 2.14.0" "$PACKETEVENTS_URL" "$PACKETEVENTS_SHA256" "$PACKETEVENTS_JAR"

# --- Worlds, schematics and backups -------------------------------------------------------------
# They are kept outside this folder: a new version of the test server finds them again.
DATA=${RAGO_DONNEES:-$HOME/Rago}
WORLDS=$DATA/mondes
SCHEMATICS=$DATA/schematics
BACKUPS=$DATA/sauvegardes
mkdir -p "$WORLDS" "$SCHEMATICS" "$BACKUPS" || stop_with "cannot create the folder $DATA."
say "Worlds and schematics: $DATA"

keep=${RAGO_SAUVEGARDES:-10}
case "$keep" in
  ''|*[!0-9]*) stop_with "RAGO_SAUVEGARDES must be a number (for example 10), not \"$keep\"." ;;
esac
if [ "$keep" -gt 0 ] && { [ -n "$(ls -A "$WORLDS")" ] || [ -n "$(ls -A "$SCHEMATICS")" ]; }; then
  archive=$BACKUPS/mondes-$(date +%Y-%m-%d_%H-%M-%S).tar.gz
  say "Backup of the worlds and the schematics: $archive"
  if ! tar -czf "$archive.part" -C "$DATA" mondes schematics; then
    rm -f "$archive.part"
    stop_with "the backup of the worlds failed (disk full?). The server does not start, so as not to" \
      "damage anything. Free some space, or run with RAGO_SAUVEGARDES=0 to start without a backup."
  fi
  mv "$archive.part" "$archive"
  # Keeps the $keep most recent: the names are dated, the alphabetical order is the order of the dates.
  ls -1 "$BACKUPS" | { grep '^mondes-.*\.tar\.gz$' || true; } | sort -r | tail -n "+$((keep + 1))" |
    while IFS= read -r old; do rm -f "$BACKUPS/$old"; done
fi

# WorldEdit keeps its schematics with the worlds. This file is only written if it does not exist:
# the other settings of WorldEdit keep their default value.
if [ ! -f plugins/WorldEdit/config.yml ]; then
  mkdir -p plugins/WorldEdit
  {
    printf '# Written by the Rago launcher: the schematics are kept with the worlds (%s).\n' "$DATA"
    printf 'saving:\n'
    printf "  dir: '%s'\n" "$(printf '%s' "$SCHEMATICS" | sed "s/'/''/g")"
  } > plugins/WorldEdit/config.yml
fi

# --- Start --------------------------------------------------------------------------------------
say "Starting the server. In Minecraft 1.8.9: Multiplayer, Direct Connection, address localhost."
say "To stop it, type stop here, then Enter."
exec "$JAVA" "-Xmx${RAGO_MEMOIRE:-2G}" -DIReallyKnowWhatIAmDoingISwear=true -jar "$SERVER_JAR" \
  --world-container "$WORLDS" nogui
