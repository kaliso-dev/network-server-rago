#!/usr/bin/env bash
# End-to-end test of kb-recorder, on this machine: a real Minecraft 1.8.9 client with the mod is
# hit on a local PandaSpigot 1.8.8 server, and what the mod recorded must match the combat
# engine's hit log.
#
# 1. sets up the control test's server (tools/kb-control/test-server.sh): offline mode, bound to
#    127.0.0.1, RagoCombatEngine with its hit log;
# 2. starts the mod in the Forge 1.8.9 dev client (task runE2eClient: the launch of Loom's
#    runClient), which joins the server and starts recording at the first connection (property
#    rago.kbrecorder.autostart);
# 3. an attacker bot (tools/kb-control/bots/recorder-attacker.mjs) hits the client, with the
#    server's own knockback and then with the engine's control-alt test profile;
# 4. stops the client with SIGTERM, which makes the mod close its file in its shutdown hook, and
#    checks the recording with RecorderCheck (tools/kb-control).
#
# The client needs a display: on a machine without a screen, run the script under xvfb-run. Run
# from anywhere, after:
#   ./gradlew :rago-combat-engine:assemble :kb-control:installDist
#   (cd tools/kb-control/bots && npm ci)
#   KB_ACCEPT_EULA=true xvfb-run -a tools/kb-recorder/run-e2e-test.sh
#
# Running a Minecraft server means accepting the Minecraft EULA (https://aka.ms/MinecraftEULA):
# the script refuses to start unless KB_ACCEPT_EULA=true says you do.
#
# Settings (environment variables):
#   KB_SERVER_JAVA      java binary for the server (default: java on PATH)
#   KB_CHECK_JAVA       java binary for RecorderCheck (default: java on PATH)
#   KB_GRADLE_JAVA_HOME JDK that runs the mod's Gradle build (default: JAVA_HOME); Java 17 in CI
#   KB_GRADLE_ARGS      extra arguments for the mod's Gradle build, for example where Java 8 is
#   PANDASPIGOT_URL, PANDASPIGOT_SHA256, KB_PORT: as for the control test
#   KB_CLIENT_TIMEOUT   seconds to wait for the client to join (default 900: the first run
#                       downloads Minecraft's assets)
#   KB_HITS_PER_PHASE   hits per phase (default 12)
#   KB_RUN_DIR          working directory (default recorder-run at the repository root)
#
# Output, in KB_RUN_DIR: report.md, recording.jsonl (the mod's file), attacks.jsonl, client.log,
# server.log and server/ with the hit log. Exit status 0 only if the check passed.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${KB_RUN_DIR:-$ROOT/recorder-run}
SERVER_JAVA=${KB_SERVER_JAVA:-java}
CHECK_JAVA=${KB_CHECK_JAVA:-java}
GRADLE_JAVA_HOME=${KB_GRADLE_JAVA_HOME:-${JAVA_HOME:-}}
read -r -a GRADLE_ARGS <<<"${KB_GRADLE_ARGS:-}"
# The PandaSpigot build the control test passed on.
PANDASPIGOT_URL=${PANDASPIGOT_URL:-https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip}
PANDASPIGOT_SHA256=${PANDASPIGOT_SHA256-ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b}
PORT=${KB_PORT:-25565}
CLIENT_TIMEOUT=${KB_CLIENT_TIMEOUT:-900}
HITS_PER_PHASE=${KB_HITS_PER_PHASE:-12}
PHASES=2
ATTACKER=RagoAttacker
LABEL=rago-e2e
MOD_DIR=$ROOT/tools/kb-recorder
# Loom's run directory for the test client (runDir in build.gradle.kts): Minecraft's game directory.
CLIENT_DIR=$MOD_DIR/build/e2e-client
CLIENT_LOG=$RUN_DIR/client.log
# The client JVM is the only process with this argument (Loom passes the property with -D).
CLIENT_MATCH="-Drago.kbrecorder.autostart=$LABEL"

log() { printf '[recorder-test] %s\n' "$*"; }
fail() { printf '[recorder-test] FAILED: %s\n' "$*" >&2; exit 1; }

if [[ "${KB_ACCEPT_EULA:-}" != "true" ]]; then
  fail "set KB_ACCEPT_EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA) for this test server"
fi
command -v node >/dev/null || fail "node is required"
[[ -n "${DISPLAY:-}" ]] || fail "the Minecraft client needs a display: run the script under xvfb-run"
"$CHECK_JAVA" -version >/dev/null 2>&1 || fail "cannot run $CHECK_JAVA"
CHECKER_LIB=$ROOT/tools/kb-control/build/install/kb-control/lib
[[ -d "$CHECKER_LIB" ]] || fail "RecorderCheck is not installed (run ./gradlew :kb-control:installDist)"
[[ -d "$ROOT/tools/kb-control/bots/node_modules/mineflayer" ]] || fail "bot dependencies missing (run npm ci in tools/kb-control/bots)"
if pgrep -f -- "$CLIENT_MATCH" >/dev/null; then
  fail "a test client from an earlier run is still running"
fi

# shellcheck source=../kb-control/test-server.sh
source "$ROOT/tools/kb-control/test-server.sh"
rm -f "$RUN_DIR/report.md" "$RUN_DIR/recording.jsonl" "$RUN_DIR/attacks.jsonl" "$CLIENT_LOG"
prepare_test_server "$ATTACKER"

# The client log without Java stack frames, which hide the error lines in a CI log.
show_client_log() {
  grep -v -E '^[[:space:]]+(at |\.\.\. [0-9]+ more)' "$CLIENT_LOG" | tail -n 150 >&2 || true
}

GRADLE_PID=
stop_client() {
  local pid
  pid=$(pgrep -f -- "$CLIENT_MATCH" | head -n 1 || true)
  if [[ -n "$pid" ]]; then
    log "stopping the client (SIGTERM, pid $pid)"
    kill -TERM "$pid" 2>/dev/null || true
    for _ in $(seq 1 60); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -KILL "$pid" 2>/dev/null || true
      log "the client did not exit within 60 s and was killed"
    fi
  fi
  if [[ -n "$GRADLE_PID" ]]; then
    # Gradle reports the client's exit status, which SIGTERM makes non-zero.
    wait "$GRADLE_PID" 2>/dev/null || true
    GRADLE_PID=
  fi
}
cleanup() {
  stop_client
  stop_test_server
}
trap cleanup EXIT

start_test_server

# --- Client ------------------------------------------------------------------------------------
# A light game directory: smallest view distance, no sound, no pause when the window has no focus.
rm -rf "$CLIENT_DIR"
mkdir -p "$CLIENT_DIR"
cat >"$CLIENT_DIR/options.txt" <<'EOF'
renderDistance:2
maxFps:30
fancyGraphics:false
ao:0
clouds:false
particles:2
enableVsync:false
mipmapLevels:0
pauseOnLostFocus:false
snooperEnabled:false
soundCategory_master:0.0
EOF

log "starting the Minecraft 1.8.9 client with the mod (Gradle runE2eClient)"
(
  cd "$MOD_DIR"
  if [[ -n "$GRADLE_JAVA_HOME" ]]; then
    export JAVA_HOME=$GRADLE_JAVA_HOME
  fi
  exec ./gradlew runE2eClient --console=plain ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} \
    "-Prago.e2e.server=127.0.0.1:$PORT" "-Prago.e2e.label=$LABEL"
) >"$CLIENT_LOG" 2>&1 &
GRADLE_PID=$!

# The server logs "<name>[/127.0.0.1:<port>] logged in with entity id <id> at ...". The client
# is the first player: the bot joins later.
victim=
for _ in $(seq 1 "$CLIENT_TIMEOUT"); do
  victim=$(grep -h -o '[A-Za-z0-9_]*\[/127\.0\.0\.1:[0-9]*\] logged in with entity id' \
    "$SERVER_LOG" "$SERVER_DIR/logs/latest.log" 2>/dev/null | head -n 1 | sed 's/\[.*//' || true)
  [[ -n "$victim" ]] && break
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    show_client_log
    fail "the client stopped before joining the server"
  fi
  sleep 1
done
if [[ -z "$victim" ]]; then
  show_client_log
  fail "the client did not join the server within $CLIENT_TIMEOUT s"
fi
log "the client joined as $victim"

# The game logs to the console (client.log) and to its logs folder.
client_log_line() { grep -h -o -- "$1.*" "$CLIENT_LOG" "$CLIENT_DIR"/logs/*.log 2>/dev/null | head -n 1 || true; }
started=
for _ in $(seq 1 60); do
  started=$(client_log_line 'Recording started automatically in')
  [[ -n "$started" ]] && break
  sleep 1
done
if [[ -z "$started" ]]; then
  grep -n -i 'kbrecorder\|KB Recorder\|exception' "$CLIENT_LOG" | tail -n 40 >&2 || true
  fail "the mod did not start recording"
fi
log "$started"

# --- Attacker ----------------------------------------------------------------------------------
log "running the attacker ($PHASES phases, $HITS_PER_PHASE hits each)"
bot_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT KB_VICTIM=$victim KB_ATTACKS=$RUN_DIR/attacks.jsonl \
  KB_HITS_PER_PHASE=$HITS_PER_PHASE node "$ROOT/tools/kb-control/bots/recorder-attacker.mjs" || bot_status=$?

stop_client
stop_test_server
if [[ $bot_status -ne 0 ]]; then
  grep -n -E 'WARN|ERROR|moved|RagoCombatEngine' "$SERVER_LOG" | tail -n 40 >&2 || true
  fail "the attacker stopped with status $bot_status"
fi

# --- Check -------------------------------------------------------------------------------------
shopt -s nullglob
recordings=("$CLIENT_DIR"/rago-kb-recorder/kb-*-"$LABEL".jsonl)
shopt -u nullglob
[[ ${#recordings[@]} -eq 1 ]] || fail "expected one recording in $CLIENT_DIR/rago-kb-recorder, found ${#recordings[@]}"
cp "${recordings[0]}" "$RUN_DIR/recording.jsonl"
log "recording: $(wc -l <"$RUN_DIR/recording.jsonl") lines"

log "checking the recording"
"$CHECK_JAVA" -cp "$CHECKER_LIB/*" gg.rago.tools.kbcontrol.RecorderCheck \
  --recording "$RUN_DIR/recording.jsonl" --hits "$SERVER_DIR/plugins/RagoCombatEngine/hits" \
  --attacks "$RUN_DIR/attacks.jsonl" --label "$LABEL" --attacker "$ATTACKER" \
  --min-hits $((PHASES * HITS_PER_PHASE)) --report "$RUN_DIR/report.md"
