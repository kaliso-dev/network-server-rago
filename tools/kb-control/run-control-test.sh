#!/usr/bin/env bash
# RagoCombatEngine control test on a real PandaSpigot 1.8.8 server, on this machine.
#
# 1. downloads PandaSpigot (cached in control-run/cache) and sets up a flat, offline-mode test
#    server bound to 127.0.0.1 with the engine plugin and its hit log turned on;
# 2. lets two bots (bots/control-bots.mjs) hit each other in observe and override modes;
# 3. checks every velocity packet the bots received with ControlCheck and rago-combat-model.
#
# Run from anywhere, after the build:
#   ./gradlew build :kb-control:installDist
#   (cd tools/kb-control/bots && npm ci)
#   KB_ACCEPT_EULA=true tools/kb-control/run-control-test.sh
#
# Running a Minecraft server means accepting the Minecraft EULA (https://aka.ms/MinecraftEULA):
# the script refuses to start unless KB_ACCEPT_EULA=true says you do.
#
# Settings (environment variables):
#   KB_SERVER_JAVA      java binary for the server (default: java on PATH); 1.8.8 servers run on Java 8
#   PANDASPIGOT_URL     server download (default: the latest 1.8.8 build from downloads.hpfxd.com)
#   PANDASPIGOT_SHA256  expected SHA-256 of the download; checked when set
#   KB_PORT             server port on 127.0.0.1 (default 25565)
#   KB_HITS_PER_PHASE   hits per phase (default 36)
#   KB_RUN_DIR          working directory (default control-run at the repository root)
#
# Output, in KB_RUN_DIR: report.md, received.jsonl, server.log and server/ with the hit log in
# server/plugins/RagoCombatEngine/hits/. Exit status 0 only if the control test passed.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${KB_RUN_DIR:-$ROOT/control-run}
SERVER_DIR=$RUN_DIR/server
CACHE_DIR=$RUN_DIR/cache
SERVER_JAVA=${KB_SERVER_JAVA:-java}
PANDASPIGOT_URL=${PANDASPIGOT_URL:-https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/latest/downloads/paperclip}
PANDASPIGOT_SHA256=${PANDASPIGOT_SHA256:-}
PORT=${KB_PORT:-25565}
HITS_PER_PHASE=${KB_HITS_PER_PHASE:-36}
PHASES=3
ATTACKER=RagoAttacker

log() { printf '[control-test] %s\n' "$*"; }
fail() { printf '[control-test] FAILED: %s\n' "$*" >&2; exit 1; }

if [[ "${KB_ACCEPT_EULA:-}" != "true" ]]; then
  fail "set KB_ACCEPT_EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA) for this test server"
fi

for tool in curl python3 node; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done
"$SERVER_JAVA" -version >/dev/null 2>&1 || fail "cannot run $SERVER_JAVA"

shopt -s nullglob
plugin_jars=("$ROOT"/combat/rago-combat-engine/build/libs/rago-combat-engine-*.jar)
shopt -u nullglob
[[ ${#plugin_jars[@]} -eq 1 ]] || fail "expected one plugin jar in combat/rago-combat-engine/build/libs (run ./gradlew build)"
CHECKER=$ROOT/tools/kb-control/build/install/kb-control/bin/kb-control
[[ -x "$CHECKER" ]] || fail "ControlCheck is not installed (run ./gradlew :kb-control:installDist)"
[[ -d "$ROOT/tools/kb-control/bots/node_modules/mineflayer" ]] || fail "bot dependencies missing (run npm ci in tools/kb-control/bots)"

# A server left over from an earlier run must not answer instead of ours.
if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  fail "port $PORT is already in use on 127.0.0.1"
fi

mkdir -p "$CACHE_DIR"
rm -rf "$SERVER_DIR" "$RUN_DIR/received.jsonl" "$RUN_DIR/report.md" "$RUN_DIR/server.log"
mkdir -p "$SERVER_DIR/plugins/RagoCombatEngine"

# --- PandaSpigot -------------------------------------------------------------------------------
SERVER_JAR=$CACHE_DIR/pandaspigot-paperclip.jar
if [[ ! -s "$SERVER_JAR" ]]; then
  log "downloading PandaSpigot from $PANDASPIGOT_URL"
  curl -fsSL --retry 3 --retry-delay 5 -o "$SERVER_JAR.part" "$PANDASPIGOT_URL"
  mv "$SERVER_JAR.part" "$SERVER_JAR"
fi
actual_sha256=$(sha256sum "$SERVER_JAR" | cut -d' ' -f1)
log "PandaSpigot jar SHA-256: $actual_sha256"
if [[ -n "$PANDASPIGOT_SHA256" && "$actual_sha256" != "$PANDASPIGOT_SHA256" ]]; then
  fail "PandaSpigot SHA-256 is $actual_sha256, expected $PANDASPIGOT_SHA256"
fi

# --- Test server -------------------------------------------------------------------------------
cp "$SERVER_JAR" "$SERVER_DIR/server.jar"
cp "${plugin_jars[0]}" "$SERVER_DIR/plugins/RagoCombatEngine.jar"
echo "eula=true" >"$SERVER_DIR/eula.txt"

cat >"$SERVER_DIR/server.properties" <<EOF
server-ip=127.0.0.1
server-port=$PORT
online-mode=false
level-name=kb-control
level-type=FLAT
generate-structures=false
allow-nether=false
difficulty=0
gamemode=0
pvp=true
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
spawn-protection=0
allow-flight=true
view-distance=4
max-players=4
snooper-enabled=false
announce-player-achievements=false
motd=RagoCombatEngine control test
EOF

# Only the overworld: the test never leaves it.
cat >"$SERVER_DIR/bukkit.yml" <<'EOF'
settings:
  allow-end: false
EOF

# The attacker bot runs the test commands. On an offline-mode server its UUID is derived from
# its name, the way the server does it (UUID.nameUUIDFromBytes("OfflinePlayer:" + name)).
attacker_uuid=$(python3 -c 'import hashlib, sys, uuid; print(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + sys.argv[1]).encode("utf-8")).digest(), version=3))' "$ATTACKER")
cat >"$SERVER_DIR/ops.json" <<EOF
[{"uuid": "$attacker_uuid", "name": "$ATTACKER", "level": 4, "bypassesPlayerLimit": false}]
EOF

# Engine settings for the test. control-alt is an arbitrary test profile, far enough from vanilla
# to change every packet; it is not a tuning proposal.
cat >"$SERVER_DIR/plugins/RagoCombatEngine/config.yml" <<'EOF'
mode: observe
active-profile: vanilla
world-profiles: {}
record-hits: true
profiles:
  control-alt:
    friction: 1.7
    horizontal: 0.36
    vertical: 0.37
    vertical-limit: 0.39
    extra-horizontal: 0.45
    extra-vertical: 0.085
EOF

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
# --noconsole: the server reads no commands from stdin (the bots send them). The system property
# skips the 20 s pause Spigot adds for builds older than a few weeks.
(cd "$SERVER_DIR" && exec "$SERVER_JAVA" -Xms512M -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true \
  -jar server.jar --noconsole nogui </dev/null >"$SERVER_LOG" 2>&1) &
SERVER_PID=$!

# The server prints to stdout and to logs/latest.log; look in both.
server_log_has() { grep -qsF -- "$1" "$SERVER_LOG" "$SERVER_DIR/logs/latest.log"; }

started=false
for _ in $(seq 1 300); do
  if server_log_has 'Done ('; then
    started=true
    break
  fi
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done
if [[ "$started" != true ]]; then
  tail -n 60 "$SERVER_LOG" >&2 || true
  fail "the server did not start"
fi
server_log_has '[RagoCombatEngine] Mode observe' || {
  grep -n 'RagoCombatEngine' "$SERVER_LOG" >&2 || true
  fail "RagoCombatEngine did not enable"
}
log "server started: $(grep -m1 -o 'This server is running [^)]*)' "$SERVER_LOG" || echo 'version line not found')"

# --- Bots --------------------------------------------------------------------------------------
log "running the bots ($PHASES phases, $HITS_PER_PHASE hits each)"
bots_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT KB_RECEIVED=$RUN_DIR/received.jsonl KB_HITS_PER_PHASE=$HITS_PER_PHASE \
  node "$ROOT/tools/kb-control/bots/control-bots.mjs" || bots_status=$?
stop_server

if [[ $bots_status -ne 0 ]]; then
  grep -n -E 'WARN|ERROR|moved|RagoCombatEngine' "$SERVER_LOG" | tail -n 40 >&2 || true
  fail "the bots stopped with status $bots_status"
fi

# --- Check -------------------------------------------------------------------------------------
log "checking the hits"
"$CHECKER" --hits "$SERVER_DIR/plugins/RagoCombatEngine/hits" --received "$RUN_DIR/received.jsonl" \
  --server-knockback pandaspigot --min-hits $((PHASES * HITS_PER_PHASE)) --report "$RUN_DIR/report.md"
