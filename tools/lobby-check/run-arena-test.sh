#!/usr/bin/env bash
# Arena reset test on a real PandaSpigot 1.8.8 server, on this machine: the same server as the
# lobby and sumo test (run-lobby-test.sh), with the block modes ending at once (Block placement
# first to 10, so that a fall into the void ends it; Block edit first to 2). Two bots
# (tools/kb-control/bots/arena-bots.mjs) play Block placement 1v1, Block edit 1v1 and the Block edit
# training several times in a row, ending the games in every way, and check at the start of each
# game that its zone is exactly as the mode built it. Then two other bots
# (tools/kb-control/bots/visibility-bots.mjs) check that a player who joins the server is seen by the
# one waiting in the lobby, and sees them (the first time, back from the lobby, from a training,
# from a duel). Then a bot (tools/kb-control/bots/blockin-bots.mjs) blocks itself in in the Block in
# training and checks the enemy: it breaks its way in at one side, with the cracks, hits only
# through the hole, and breaks again a block put back. Then a bot plays the Block clutch training
# while ten others watch it at once with /spectate (tools/kb-control/bots/spectate-bots.mjs). Last,
# two bots play the two modes of FUN PRACTICE (tools/kb-control/bots/fun-bots.mjs): the Snowball
# Spleef (the shovel, the snowballs, a fall under the snow) and the Fireball Fight (the wind gust,
# a fireball that pushes without hurting, a fall off the platform), and three bots play Red Rover
# Sumo (tools/kb-control/bots/rover-bots.mjs): three 1v1 in turn, the winner staying on. Then two
# bots play CTF The Bridge 1v1 (tools/kb-control/bots/ctf-bots.mjs) on the map the plugin makes:
# the kit, the bridge that breaks, the golden apple, the arrow that comes back, a flag taken and
# back to its base from the void, three captures and the win; and four bots CTF Castle 2v2, on the
# other map the plugin makes: the start of Bed Wars in the castles, then one team leaves. Last, two
# bots play Fast Bedwars 1v1 (tools/kb-control/bots/fast-bots.mjs) on the demo map: its welcome, its
# events (no Diamond II), no diamond generator nor upgrades NPC, its fast forge, its smaller shop.
# Then a bot invites RagoBot into its party (tools/kb-control/bots/ragobot-bots.mjs): RagoBot refuses
# ranked and the modes it does not play, wins a Sumo duel and a round of Top Bridge 1v1 against the
# bot who stands still, plays Top Bridge 2v2 in the party's team against two others, and leaves the
# server once kicked from the party; none of these games is counted as a private game. Last, the
# Owner tools (tools/kb-control/bots/owner-bots.mjs): /owner for qRago only, Reach that lands from
# 6 blocks in a sumo and does nothing in a ranked sumo, Scaffold in a Block placement 1v1; the games
# with a tool on are counted for nobody, the ranked one is.
# Fails if the bots fail, or if the server log has an error or an exception.
#
# Run from anywhere, after the build:
#   ./gradlew build
#   (cd tools/kb-control/bots && npm ci)
#   KB_ACCEPT_EULA=true tools/lobby-check/run-arena-test.sh
#
# Running a Minecraft server means accepting the Minecraft EULA (https://aka.ms/MinecraftEULA):
# the script refuses to start unless KB_ACCEPT_EULA=true says you do.
#
# Settings (environment variables): KB_SERVER_JAVA (java binary for the server, default java),
# KB_PORT (default 25565), ARENA_RUN_DIR (default arena-run at the repository root).
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${ARENA_RUN_DIR:-$ROOT/arena-run}
SERVER_DIR=$RUN_DIR/server
CACHE_DIR=$RUN_DIR/cache
SERVER_JAVA=${KB_SERVER_JAVA:-java}
PANDASPIGOT_URL=https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip
PANDASPIGOT_SHA256=ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b
# The same PacketEvents as the local test server's launchers (RagoLobby depends on it).
PACKETEVENTS_URL=https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
PACKETEVENTS_SHA256=060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7
PORT=${KB_PORT:-25565}

log() { printf '[arena-test] %s\n' "$*"; }
fail() { printf '[arena-test] FAILED: %s\n' "$*" >&2; exit 1; }

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

# The settings of the local test server (tools/local-server), in offline mode for the bots. Its 50
# places are what the spectator test needs: a player and ten spectators at once.
sed -e 's/^online-mode=.*/online-mode=false/' -e 's/^server-ip=.*/server-ip=127.0.0.1/' \
  -e "s/^server-port=.*/server-port=$PORT/" \
  "$ROOT/tools/local-server/server.properties" >"$SERVER_DIR/server.properties"
cat >"$SERVER_DIR/bukkit.yml" <<'YAML'
settings:
  allow-end: false
YAML
# The bundled config.yml of the lobby, with the id of qRago for the Owner tools (outils-owner.uuid):
# this server is in offline mode, where a name alone is not enough. Its UUID is the one of an
# offline-mode player: the version 3 UUID of "OfflinePlayer:qRago". qRago and RagoOp are operators
# (rago.admin): /owner is for qRago only, even among the operators.
offline_uuid() {
  node -e 'const h = require("crypto").createHash("md5").update("OfflinePlayer:" + process.argv[1]).digest()
h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80
const x = h.toString("hex"); console.log([x.slice(0, 8), x.slice(8, 12), x.slice(12, 16), x.slice(16, 20), x.slice(20)].join("-"))' "$1"
}
mkdir -p "$SERVER_DIR/plugins/RagoLobby"
QRAGO_UUID=$(offline_uuid qRago)
sed -e "/^outils-owner:$/,/^[a-z]/s/^  uuid: \"\"$/  uuid: \"$QRAGO_UUID\"/" "$ROOT/bukkit/rago-lobby/src/main/resources/config.yml" \
  >"$SERVER_DIR/plugins/RagoLobby/config.yml"
grep -q "^  uuid: \"$QRAGO_UUID\"$" "$SERVER_DIR/plugins/RagoLobby/config.yml" || fail "could not give the Owner tools the id of qRago"
printf '[{"uuid":"%s","name":"qRago","level":4},{"uuid":"%s","name":"RagoOp","level":4}]\n' "$QRAGO_UUID" "$(offline_uuid RagoOp)" \
  >"$SERVER_DIR/ops.json"
# The bundled config.yml of RagoPractice, with the block modes ending at once: Block placement
# first to 10 (a fall into the void gives 10), Block edit first to 2.
mkdir -p "$SERVER_DIR/plugins/RagoPractice"
sed -e 's/^  block-fight: 100$/  block-fight: 10/' -e 's/^  block-edit: 5$/  block-edit: 2/' \
  "$ROOT/bukkit/rago-practice/src/main/resources/config.yml" >"$SERVER_DIR/plugins/RagoPractice/config.yml"
grep -q '^  block-fight: 10$' "$SERVER_DIR/plugins/RagoPractice/config.yml" || fail "could not end the block placement at 10"
grep -q '^  block-edit: 2$' "$SERVER_DIR/plugins/RagoPractice/config.yml" || fail "could not end the block edit at 2"
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
# bundled config, duels), a stats NPC (profil), a wall of leaderboards (leaderboards), the main
# leaderboard (top, within click range of the spawn) and the kills leaderboard (joueurs).
mkdir -p "$SERVER_DIR/lobby"
cat >"$SERVER_DIR/lobby/points.yml" <<'YAML'
duels:
  x: 3.5
  y: 65.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
profil:
  x: -3.5
  y: 65.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
leaderboards:
  x: 0.5
  y: 65.0
  z: -6.5
  yaw: 0.0
  pitch: 0.0
top:
  x: 0.5
  y: 65.0
  z: 5.5
  yaw: 180.0
  pitch: 0.0
joueurs:
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
for expected in '[RagoLobby] Lobby in the world lobby' '[RagoPractice] Arenas in the world arenes' \
    'Mode block-fight-1v1 hosted by RagoPractice' 'Mode block-edit-duel hosted by RagoPractice' \
    'Mode block-edit hosted by RagoPractice' 'Mode snowball-spleef hosted by RagoPractice' \
    'Mode parkour-duel hosted by RagoPractice' \
    'Mode fireball-knockback hosted by RagoPractice' 'Mode red-rover-sumo hosted by RagoPractice' \
    'Mode red-rover-block-edit hosted by RagoPractice' 'Mode red-rover-block-placement hosted by RagoPractice' \
    'Mode red-rover-top-bridge hosted by RagoBedwars' 'Mode ctf-bridge-1v1 hosted by RagoBedwars' \
    'Mode ctf-castle-2v2 hosted by RagoBedwars' '[RagoBedwars] Capture the Flag map CtfBridge created' \
    '[RagoBedwars] Capture the Flag map CtfCastle created' 'Mode fast-bedwars-1v1 hosted by RagoBedwars' \
    'Mode sumo hosted by RagoPractice' 'Mode top-bridge-1v1 hosted by RagoBedwars' \
    'Mode top-bridge-2v2 hosted by RagoBedwars'; do
  server_log_has "$expected" || { grep -n 'Rago' "$SERVER_LOG" >&2 || true; fail "missing in the server log: $expected"; }
done
log "server started with the lobby and the practice modes"

bots_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/arena-bots.mjs" || bots_status=$?
# The same server for the lobby visibility test: two other bots, arriving while the other waits.
visibility_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/visibility-bots.mjs" || visibility_status=$?
# The same server for the Block in training: one bot, against the enemy.
blockin_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/blockin-bots.mjs" || blockin_status=$?
# The same server for the spectators: one bot in the Block clutch, ten watching it at once.
spectate_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/spectate-bots.mjs" || spectate_status=$?
# The same server for FUN PRACTICE: two bots, a Snowball Spleef then a Fireball Fight.
fun_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/fun-bots.mjs" || fun_status=$?
# The same server for Red Rover: three bots, the sumo (three 1v1 in turn), the block placement, the
# block edit, and the top bridge (RagoBedwars, on its demo map).
rover_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/rover-bots.mjs" || rover_status=$?
# The same server for Capture the Flag: two bots, CTF The Bridge 1v1 (RagoBedwars, on CtfBridge),
# then four, CTF Castle 2v2 (on CtfCastle).
ctf_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/ctf-bots.mjs" || ctf_status=$?
# The same server for Fast Bedwars: two bots, Fast Bedwars 1v1 (RagoBedwars, on its demo map).
fast_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/fast-bots.mjs" || fast_status=$?
# The same server for RagoBot: a bot invites it into its party, plays the Sumo duel and Top Bridge
# 1v1 against it, then Top Bridge 2v2 with it against two others, and sends it away.
ragobot_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/ragobot-bots.mjs" || ragobot_status=$?
# The same server for the Owner tools: qRago (the account of outils-owner) and two others; Reach in
# a sumo and in a ranked sumo, Scaffold in a Block placement 1v1.
owner_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT node "$ROOT/tools/kb-control/bots/owner-bots.mjs" || owner_status=$?
stop_server

# Any error or exception logged by the server fails the test, even if the bots passed. One is
# left out: the PacketEvents plugin always asks GitHub for its latest version, and GitHub often
# answers 403 to the CI machines; it only logs that failure and carries on.
if grep -n -E 'ERROR\]|SEVERE|Exception' "$SERVER_LOG" \
    | grep -v -F -e 'Failed to parse packetevents version' \
        -e 'https://api.github.com/repos/retrooper/packetevents/releases/latest' >&2; then
  fail "the server log has errors (above)"
fi
[[ $bots_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the arena bots stopped with status $bots_status"; }
[[ $visibility_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the visibility bots stopped with status $visibility_status"; }
[[ $blockin_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the Block in bot stopped with status $blockin_status"; }
[[ $spectate_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the spectator bots stopped with status $spectate_status"; }
[[ $fun_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the FUN PRACTICE bots stopped with status $fun_status"; }
[[ $rover_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the Red Rover bots stopped with status $rover_status"; }
[[ $ctf_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the Capture the Flag bots stopped with status $ctf_status"; }
[[ $fast_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the Fast Bedwars bots stopped with status $fast_status"; }
[[ $ragobot_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the RagoBot bots stopped with status $ragobot_status"; }
server_log_has '[RagoLobby] RagoBot joined for the party of RagoLeader' || fail "RagoBot did not join for the party of RagoLeader"
# A game with RagoBot counts in the players' stats: none is a private game of the party.
if grep -n -E 'Private game of a party in (sumo|top-bridge-1v1|top-bridge-2v2):' "$SERVER_LOG" >&2; then
  fail "a game with RagoBot was counted as a private game (above)"
fi
[[ $owner_status -eq 0 ]] || { tail -n 60 "$SERVER_LOG" >&2; fail "the Owner tool bots stopped with status $owner_status"; }
# A game with an Owner tool on counts for nobody; never one in a ranked game, where they do nothing.
for mode in sumo block-fight-1v1; do
  server_log_has "[RagoLobby] Game with an Owner tool in $mode: not counted in the stats" \
    || fail "the $mode game of qRago with an Owner tool on was counted"
done
if grep -n -F 'Game with an Owner tool in ranked-' "$SERVER_LOG" >&2; then
  fail "a ranked game was taken for a game with an Owner tool (above)"
fi
log "PASSED"
