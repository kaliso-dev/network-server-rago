#!/usr/bin/env bash
# Billboard test with a real Minecraft 1.8.9 client, on this machine: does the client draw the
# billboards of the lobby (panneaux: of RagoLobby), walls of item frames that exist only in its
# packets, each frame holding a map?
#
# 1. sets up an offline-mode PandaSpigot 1.8.8 server bound to 127.0.0.1 with RagoLobby and
#    PacketEvents (the builds of the lobby test), on a new empty lobby world (a platform of 9 x 9
#    blocks around the spawn), with two boards facing the spawn: a red one 13 blocks in front of
#    it, and a yellow one 76 blocks away, in a chunk without a single block;
# 2. downloads the Minecraft 1.8.9 client from Mojang (tools/lobby-check/minecraft-client.py),
#    and starts it: it joins the server, and arrives on the spawn looking at the red board;
# 3. takes a screenshot there (spawn.png), then the console puts the player in spectator mode 76
#    blocks behind the spawn, looking at the red board from that far, and a second screenshot is
#    taken (far.png); billboard-shot.py reads both.
#
# The client needs a display: on a machine without a screen, run the script under xvfb-run, with a
# screen of the client's size (854 x 480). Run from anywhere, after:
#   ./gradlew :rago-lobby:assemble
#   KB_ACCEPT_EULA=true xvfb-run -a -s "-screen 0 854x480x24" tools/lobby-check/run-billboard-test.sh
# It needs python3 and ImageMagick (import and convert, for the screenshots).
#
# Running a Minecraft server means accepting the Minecraft EULA (https://aka.ms/MinecraftEULA):
# the script refuses to start unless KB_ACCEPT_EULA=true says you do.
#
# Settings (environment variables): KB_SERVER_JAVA (java binary for the server, default java),
# BILLBOARD_CLIENT_JAVA (a Java 8 binary for the client, default java), KB_PORT (default 25565),
# BILLBOARD_RUN_DIR (default billboard-run at the repository root: the screenshots, the logs of
# the server and of the client, and the downloads kept for the next run).
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${BILLBOARD_RUN_DIR:-$ROOT/billboard-run}
SERVER_DIR=$RUN_DIR/server
CLIENT_DIR=$RUN_DIR/client
CACHE_DIR=$RUN_DIR/cache
SERVER_JAVA=${KB_SERVER_JAVA:-java}
CLIENT_JAVA=${BILLBOARD_CLIENT_JAVA:-java}
PANDASPIGOT_URL=https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip
PANDASPIGOT_SHA256=ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b
PACKETEVENTS_URL=https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
PACKETEVENTS_SHA256=060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7
PORT=${KB_PORT:-25565}
VIEWER=RagoViewer
# Pixels of a board in a screenshot below which it is not seen: the red board covers about 13000
# pixels from the spawn and 350 from 76 blocks away, the yellow one about 500 from the spawn.
SEEN=200

log() { printf '[billboard-test] %s\n' "$*"; }
fail() { printf '[billboard-test] FAILED: %s\n' "$*" >&2; exit 1; }

if [[ "${KB_ACCEPT_EULA:-}" != "true" ]]; then
  fail "set KB_ACCEPT_EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA) for this test server"
fi
for tool in curl python3 import convert; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done
[[ -n "${DISPLAY:-}" ]] || fail "the Minecraft client needs a display: run the script under xvfb-run"
"$SERVER_JAVA" -version >/dev/null 2>&1 || fail "cannot run $SERVER_JAVA"
"$CLIENT_JAVA" -version 2>&1 | grep -q 'version "1\.8\.' || fail "the client needs Java 8 (BILLBOARD_CLIENT_JAVA)"
shopt -s nullglob
jars=("$ROOT"/bukkit/rago-lobby/build/libs/*.jar)
shopt -u nullglob
[[ ${#jars[@]} -eq 1 ]] || fail "expected one jar in bukkit/rago-lobby/build/libs (run ./gradlew :rago-lobby:assemble)"
LOBBY_JAR=${jars[0]}
if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  fail "port $PORT is already in use on 127.0.0.1"
fi

mkdir -p "$CACHE_DIR"
rm -rf "$SERVER_DIR" "$CLIENT_DIR" "$RUN_DIR"/*.log "$RUN_DIR"/*.png "$RUN_DIR"/*.ppm
mkdir -p "$SERVER_DIR/plugins/RagoLobby/panneaux" "$SERVER_DIR/lobby" "$CLIENT_DIR"

cached() { # url sha256 file
  if [[ ! -s "$3" ]]; then
    log "downloading $1"
    curl -fsSL --retry 3 --retry-delay 5 -o "$3.part" "$1"
    mv "$3.part" "$3"
  fi
  local actual
  actual=$(sha256sum "$3" | cut -d' ' -f1)
  [[ "$actual" == "$2" ]] || fail "SHA-256 of $3 is $actual, expected $2"
}
cached "$PANDASPIGOT_URL" "$PANDASPIGOT_SHA256" "$CACHE_DIR/pandaspigot-226.jar"
cached "$PACKETEVENTS_URL" "$PACKETEVENTS_SHA256" "$CACHE_DIR/packetevents-spigot-2.14.0.jar"
log "downloading the Minecraft 1.8.9 client (kept in $CACHE_DIR/minecraft)"
CLIENT_CLASSPATH=$(python3 "$ROOT/tools/lobby-check/minecraft-client.py" "$CACHE_DIR/minecraft" | tail -n 1)

cp "$CACHE_DIR/pandaspigot-226.jar" "$SERVER_DIR/server.jar"
cp "$CACHE_DIR/packetevents-spigot-2.14.0.jar" "$SERVER_DIR/plugins/packetevents.jar"
cp "$LOBBY_JAR" "$SERVER_DIR/plugins/RagoLobby.jar"
echo "eula=true" >"$SERVER_DIR/eula.txt"
sed -e 's/^online-mode=.*/online-mode=false/' -e 's/^server-ip=.*/server-ip=127.0.0.1/' \
  -e "s/^server-port=.*/server-port=$PORT/" "$ROOT/tools/local-server/server.properties" >"$SERVER_DIR/server.properties"
cat >"$SERVER_DIR/bukkit.yml" <<'YAML'
settings:
  allow-end: false
YAML

# The bundled config.yml of the lobby without what covers the screen at a first arrival (the menu
# "What do you want to do?" and the title), and with the two boards of the test.
awk '
  /^premiere-venue:/ { print "premiere-venue: {}"; skip = 1; next }
  skip && /^[^ #]/ { skip = 0 }
  skip { next }
  /^accueil: true$/ { print "accueil: false"; next }
  /^panneaux: \{\}$/ {
    print "panneaux:"
    print "  rouge: {image: rouge.png, largeur: 6, hauteur: 4, lieu: \"0 66 14\"}"
    print "  jaune: {image: jaune.png, largeur: 6, hauteur: 4, lieu: \"24 68 72\"}"
    next
  }
  { print }
' "$ROOT/bukkit/rago-lobby/src/main/resources/config.yml" >"$SERVER_DIR/plugins/RagoLobby/config.yml"
for expected in '^premiere-venue: {}$' '^accueil: false$' '^  rouge: ' '^  jaune: '; do
  grep -q -- "$expected" "$SERVER_DIR/plugins/RagoLobby/config.yml" || fail "could not write the test config ($expected)"
done
# The images: plain colours of the map palette, the red of TNT and the yellow of hay.
python3 - "$SERVER_DIR/plugins/RagoLobby/panneaux" <<'PYTHON'
import struct, sys, zlib
def png(path, rgb, width=96, height=64):
    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    with open(path, "wb") as file:
        file.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                   + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
png(sys.argv[1] + "/rouge.png", (255, 0, 0))
png(sys.argv[1] + "/jaune.png", (229, 229, 51))
PYTHON
# The spawn of the new lobby world, on its platform, looking south at the red board.
printf 'x: 0.5\ny: 65.0\nz: 0.5\nyaw: 0.0\npitch: 0.0\n' >"$SERVER_DIR/lobby/spawn.yml"

# The client's settings: 8 chunks of render distance, no clouds, the chat hidden, no pause when
# its window is not in front.
cat >"$CLIENT_DIR/options.txt" <<'OPTIONS'
renderDistance:8
fancyGraphics:true
renderClouds:false
ao:0
fov:0.0
gamma:0.5
guiScale:2
maxFps:30
enableVsync:false
pauseOnLostFocus:false
chatVisibility:2
snooperEnabled:false
lang:en_US
OPTIONS

SERVER_LOG=$RUN_DIR/server.log
CLIENT_LOG=$RUN_DIR/client.log
CONSOLE=$RUN_DIR/console.fifo
SERVER_PID=
CLIENT_PID=
stop_all() {
  if [[ -n "$CLIENT_PID" ]] && kill -0 "$CLIENT_PID" 2>/dev/null; then
    kill -TERM "$CLIENT_PID" 2>/dev/null || true
    sleep 3
    kill -KILL "$CLIENT_PID" 2>/dev/null || true
  fi
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    log "stopping the server"
    printf 'stop\n' >&3 2>/dev/null || kill -TERM "$SERVER_PID" 2>/dev/null || true
    for _ in $(seq 1 60); do
      kill -0 "$SERVER_PID" 2>/dev/null || break
      sleep 1
    done
    kill -KILL "$SERVER_PID" 2>/dev/null || true
  fi
  rm -f "$CONSOLE"
}
trap stop_all EXIT

rm -f "$CONSOLE"
mkfifo "$CONSOLE"
log "starting PandaSpigot with $("$SERVER_JAVA" -version 2>&1 | head -n 1)"
(cd "$SERVER_DIR" && exec "$SERVER_JAVA" -Xms512M -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true \
  -jar server.jar nogui <"$CONSOLE" >"$SERVER_LOG" 2>&1) &
SERVER_PID=$!
# The console stays open for the commands of the test.
exec 3>"$CONSOLE"
console() { printf '%s\n' "$1" >&3; }

server_log_has() { grep -qsF -- "$1" "$SERVER_LOG"; }
wait_for() { # seconds text
  for _ in $(seq 1 "$1"); do
    if server_log_has "$2"; then return 0; fi
    kill -0 "$SERVER_PID" 2>/dev/null || return 1
    sleep 1
  done
  return 1
}
wait_for 300 'Done (' || { tail -n 80 "$SERVER_LOG" >&2 || true; fail "the server did not start"; }
for board in rouge jaune; do
  wait_for 60 "Billboard $board: $board.png" || { grep -n 'RagoLobby' "$SERVER_LOG" >&2 || true; fail "the board $board was not drawn"; }
done
log "server started with the two boards"

log "starting the Minecraft 1.8.9 client with $("$CLIENT_JAVA" -version 2>&1 | head -n 1)"
(cd "$CLIENT_DIR" && exec "$CLIENT_JAVA" -Xmx1G -Djava.library.path="$CACHE_DIR/minecraft/natives" \
  -cp "$CLIENT_CLASSPATH" net.minecraft.client.main.Main --username "$VIEWER" --version 1.8.9 \
  --gameDir "$CLIENT_DIR" --assetsDir "$CLIENT_DIR/assets" --assetIndex 1.8 \
  --uuid 00000000000000000000000000000000 --accessToken 0 --userProperties '{}' --userType legacy \
  --width 854 --height 480 --server 127.0.0.1 --port "$PORT" >"$CLIENT_LOG" 2>&1) &
CLIENT_PID=$!
wait_for 300 "$VIEWER[/" || { tail -n 40 "$CLIENT_LOG" >&2 || true; fail "the client did not join the server"; }
log "the client joined"

shot() { # name
  import -window root "$RUN_DIR/$1.png"
  convert "$RUN_DIR/$1.png" "$RUN_DIR/$1.ppm"
  log "screenshot $1.png:"
  python3 "$ROOT/tools/lobby-check/billboard-shot.py" "$RUN_DIR/$1.ppm" | tee "$RUN_DIR/$1.txt"
}
pixels() { # name colour
  tail -n 1 "$RUN_DIR/$1.txt" | tr ' ' '\n' | sed -n "s/^$2=//p"
}

# The chunks and the maps (4 a tick) have time to come, and the client to draw them.
sleep 30
shot spawn
console "gamemode 3 $VIEWER"
console "minecraft:tp $VIEWER 0.5 66 -62.5 0 0"
sleep 20
shot far

failed=()
[[ $(pixels spawn red) -ge $SEEN ]] || failed+=("the red board, 13 blocks in front of the spawn")
[[ $(pixels spawn yellow) -ge $SEEN ]] || failed+=("the yellow board, alone in its chunk 76 blocks away")
[[ $(pixels far red) -ge $SEEN ]] || failed+=("the red board, 76 blocks away")
if [[ ${#failed[@]} -gt 0 ]]; then
  grep -n -E 'ERROR\]|SEVERE|Exception' "$SERVER_LOG" >&2 || true
  for missing in "${failed[@]}"; do
    printf '[billboard-test] not seen: %s\n' "$missing" >&2
  done
  fail "the client does not draw the billboards (see the screenshots)"
fi
log "PASSED"
