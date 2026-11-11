#!/usr/bin/env bash
# Lobby and sumo test on a real PandaSpigot 1.8.8 server, on this machine.
#
# 1. downloads PandaSpigot (cached, the same build as the KB control test) and PacketEvents, and
#    sets up an offline-mode server bound to 127.0.0.1 with RagoCombatEngine, RagoLobby,
#    RagoPractice and RagoBedwars;
# 2. lets two bots (tools/kb-control/bots/lobby-bots.mjs) go through the lobby, the selector, the
#    queue, a sumo duel won 3 - 0, the return to the lobby, a forfeit, and a game of Ranked
#    Bedwars 1v1 on the demo map of RagoBedwars;
# 3. fails if the bots fail, or if the server log has an error or an exception.
#
# Run from anywhere, after the build:
#   ./gradlew build
#   (cd tools/kb-control/bots && npm ci)
#   KB_ACCEPT_EULA=true tools/lobby-check/run-lobby-test.sh
#
# Running a Minecraft server means accepting the Minecraft EULA (https://aka.ms/MinecraftEULA):
# the script refuses to start unless KB_ACCEPT_EULA=true says you do.
#
# Settings (environment variables): KB_SERVER_JAVA (java binary for the server, default java),
# KB_PORT (default 25565), LOBBY_RUN_DIR (default lobby-run at the repository root).
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${LOBBY_RUN_DIR:-$ROOT/lobby-run}
SERVER_DIR=$RUN_DIR/server
CACHE_DIR=$RUN_DIR/cache
SERVER_JAVA=${KB_SERVER_JAVA:-java}
PANDASPIGOT_URL=https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip
PANDASPIGOT_SHA256=ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b
# The same PacketEvents as the local test server's launchers (RagoLobby depends on it).
PACKETEVENTS_URL=https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
PACKETEVENTS_SHA256=060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7
PORT=${KB_PORT:-25565}

log() { printf '[lobby-test] %s\n' "$*"; }
fail() { printf '[lobby-test] FAILED: %s\n' "$*" >&2; exit 1; }

if [[ "${KB_ACCEPT_EULA:-}" != "true" ]]; then
  fail "set KB_ACCEPT_EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA) for this test server"
fi
for tool in curl node; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done
"$SERVER_JAVA" -version >/dev/null 2>&1 || fail "cannot run $SERVER_JAVA"

jar_of() {
  shopt -s nullglob
  local jars=("$ROOT/$1"/build/libs/*.jar)
  shopt -u nullglob
  [[ ${#jars[@]} -eq 1 ]] || fail "expected one jar in $1/build/libs (run ./gradlew build)"
  printf '%s' "${jars[0]}"
}
ENGINE_JAR=$(jar_of combat/rago-combat-engine)
LOBBY_JAR=$(jar_of bukkit/rago-lobby)
PRACTICE_JAR=$(jar_of bukkit/rago-practice)
BEDWARS_JAR=$(jar_of bukkit/rago-bedwars)
[[ -d "$ROOT/tools/kb-control/bots/node_modules/mineflayer" ]] || fail "bot dependencies missing (run npm ci in tools/kb-control/bots)"

if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  fail "port $PORT is already in use on 127.0.0.1"
fi

mkdir -p "$CACHE_DIR"
rm -rf "$SERVER_DIR" "$RUN_DIR/server.log"
mkdir -p "$SERVER_DIR/plugins"

SERVER_JAR=$CACHE_DIR/pandaspigot-226.jar
if [[ ! -s "$SERVER_JAR" ]]; then
  log "downloading PandaSpigot from $PANDASPIGOT_URL"
  curl -fsSL --retry 3 --retry-delay 5 -o "$SERVER_JAR.part" "$PANDASPIGOT_URL"
  mv "$SERVER_JAR.part" "$SERVER_JAR"
fi
actual_sha256=$(sha256sum "$SERVER_JAR" | cut -d' ' -f1)
[[ "$actual_sha256" == "$PANDASPIGOT_SHA256" ]] || fail "PandaSpigot SHA-256 is $actual_sha256, expected $PANDASPIGOT_SHA256"

PACKETEVENTS_JAR=$CACHE_DIR/packetevents-spigot-2.14.0.jar
if [[ ! -s "$PACKETEVENTS_JAR" ]]; then
  log "downloading PacketEvents from $PACKETEVENTS_URL"
  curl -fsSL --retry 3 --retry-delay 5 -o "$PACKETEVENTS_JAR.part" "$PACKETEVENTS_URL"
  mv "$PACKETEVENTS_JAR.part" "$PACKETEVENTS_JAR"
fi
actual_sha256=$(sha256sum "$PACKETEVENTS_JAR" | cut -d' ' -f1)
[[ "$actual_sha256" == "$PACKETEVENTS_SHA256" ]] || fail "PacketEvents SHA-256 is $actual_sha256, expected $PACKETEVENTS_SHA256"

cp "$SERVER_JAR" "$SERVER_DIR/server.jar"
cp "$PACKETEVENTS_JAR" "$SERVER_DIR/plugins/packetevents.jar"
cp "$ENGINE_JAR" "$SERVER_DIR/plugins/RagoCombatEngine.jar"
cp "$LOBBY_JAR" "$SERVER_DIR/plugins/RagoLobby.jar"
cp "$PRACTICE_JAR" "$SERVER_DIR/plugins/RagoPractice.jar"
cp "$BEDWARS_JAR" "$SERVER_DIR/plugins/RagoBedwars.jar"
echo "eula=true" >"$SERVER_DIR/eula.txt"

# The settings of the local test server (tools/local-server), in offline mode for the bots.
sed -e 's/^online-mode=.*/online-mode=false/' -e 's/^server-ip=.*/server-ip=127.0.0.1/' \
  -e "s/^server-port=.*/server-port=$PORT/" "$ROOT/tools/local-server/server.properties" >"$SERVER_DIR/server.properties"
cat >"$SERVER_DIR/bukkit.yml" <<'YAML'
settings:
  allow-end: false
YAML
# The bundled config.yml of the lobby, with a kit for the sumo (it gives none by default): a stick,
# which the bots move in the kit editor and find where they put it in the duel.
mkdir -p "$SERVER_DIR/plugins/RagoLobby"
sed '/^  sumo:$/a\    kit: [STICK]' "$ROOT/bukkit/rago-lobby/src/main/resources/config.yml" \
  >"$SERVER_DIR/plugins/RagoLobby/config.yml"
grep -q '^    kit: \[STICK\]$' "$SERVER_DIR/plugins/RagoLobby/config.yml" || fail "could not give the sumo a kit"
# Every bundled skin of the NPCs counts as signed already (skins.yml: the hash of its PNG and empty
# textures, so the NPCs look like Steve): the server sends nothing to MineSkin, and does not place
# the NPCs again after each skin signed (they vanish for a moment) while the bots look at them.
sha1_of() { if command -v sha1sum >/dev/null 2>&1; then sha1sum "$1"; else shasum -a 1 "$1"; fi | cut -d ' ' -f 1; }
for png in "$ROOT"/bukkit/rago-lobby/src/main/resources/skins/*.png; do
  printf '%s:\n  png: %s\n  value: eyJ0ZXh0dXJlcyI6e319\n  signature: none\n' "$(basename "$png" .png)" "$(sha1_of "$png")"
done >"$SERVER_DIR/plugins/RagoLobby/skins.yml"
grep -q '^quetes:$' "$SERVER_DIR/plugins/RagoLobby/skins.yml" || fail "could not write skins.yml"
# The bundled config.yml of RagoBedwars, with an ingot a second on each iron node, whatever the
# bundled speed (the fireball, 40 iron, comes in a few seconds).
mkdir -p "$SERVER_DIR/plugins/RagoBedwars"
sed -e 's/^  fer-secondes-par-noeud: [0-9.]*$/  fer-secondes-par-noeud: 1/' \
  "$ROOT/bukkit/rago-bedwars/src/main/resources/config.yml" >"$SERVER_DIR/plugins/RagoBedwars/config.yml"
grep -q '^  fer-secondes-par-noeud: 1$' "$SERVER_DIR/plugins/RagoBedwars/config.yml" || fail "could not speed up the iron"
# Around the spawn of the new lobby world: the NPC of the Duels category (on its point of the
# bundled config, rbwffa), a stats NPC, a wall of leaderboards, the main leaderboard (within click
# range of the spawn) and the kills leaderboard.
mkdir -p "$SERVER_DIR/lobby"
cat >"$SERVER_DIR/lobby/points.yml" <<'YAML'
rbwffa:
  x: 3.5
  y: 65.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
statsjoueur1:
  x: -3.5
  y: 65.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
leaderboardstats:
  x: 0.5
  y: 65.0
  z: -6.5
  yaw: 0.0
  pitch: 0.0
leaderboardprincipal:
  x: 0.5
  y: 65.0
  z: 5.5
  yaw: 180.0
  pitch: 0.0
leaderboardplayer:
  x: 12.5
  y: 65.0
  z: 5.5
  yaw: 180.0
  pitch: 0.0
YAML

SERVER_LOG=$RUN_DIR/server.log
SERVER_PID=
stop_server() {
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    log "stopping the server"
    kill -TERM "$SERVER_PID" 2>/dev/null || true
    for _ in $(seq 1 60); do
      kill -0 "$SERVER_PID" 2>/dev/null || break
      sleep 1
    done
    kill -KILL "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  SERVER_PID=
}
trap stop_server EXIT

log "starting PandaSpigot with $("$SERVER_JAVA" -version 2>&1 | head -n 1)"
(cd "$SERVER_DIR" && exec "$SERVER_JAVA" -Xms512M -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true \
  -jar server.jar --noconsole nogui </dev/null >"$SERVER_LOG" 2>&1) &
SERVER_PID=$!

server_log_has() { grep -qsF -- "$1" "$SERVER_LOG"; }
started=false
for _ in $(seq 1 300); do
  if server_log_has 'Done ('; then started=true; break; fi
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done
if [[ "$started" != true ]]; then
  tail -n 80 "$SERVER_LOG" >&2 || true
  fail "the server did not start"
fi
for expected in '[RagoLobby] Lobby in the world lobby' 'Mode sumo hosted by RagoPractice' \
    '[RagoPractice] Arenas in the world arenes' '[RagoBedwars] Demo map bw-demo created' \
    'Mode ranked-1v1 hosted by RagoBedwars' '[RagoBedwars] Bed Wars maps ready: bw-demo;'; do
  server_log_has "$expected" || { grep -n 'Rago' "$SERVER_LOG" >&2 || true; fail "missing in the server log: $expected"; }
done
log "server started with the lobby, the sumo duel and Bed Wars"

bots_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/lobby-bots.mjs" || bots_status=$?
stop_server

# Any error or exception logged by the server fails the test, even if the bots passed. One is
# left out: the PacketEvents plugin always asks GitHub for its latest version, and GitHub often
# answers 403 to the CI machines; it only logs that failure and carries on.
if grep -n -E 'ERROR\]|SEVERE|Exception' "$SERVER_LOG" \
    | grep -v -F -e 'Failed to parse packetevents version' \
        -e 'https://api.github.com/repos/retrooper/packetevents/releases/latest' >&2; then
  fail "the server log has errors (above)"
fi
[[ $bots_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the bots stopped with status $bots_status"; }
# The Bed Wars game was played in a copy of the demo map, made when the server started.
server_log_has '[RagoBedwars] Game world bw_bw-demo_1 ready (map bw-demo)' \
  || { grep -n 'RagoBedwars' "$SERVER_LOG" >&2 || true; fail "no copy of the demo map was made"; }
[[ -f "$SERVER_DIR/bw-demo/bedwars.yml" ]] || fail "the demo map has no bedwars.yml"
log "PASSED"
