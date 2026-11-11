#!/usr/bin/env bash
# RagoCombatEngine control test on a real PandaSpigot 1.8.8 server, on this machine.
#
# 1. downloads PandaSpigot (cached in control-run/cache) and sets up a flat, offline-mode test
#    server bound to 127.0.0.1 with the engine plugin and its hit log turned on (test-server.sh,
#    shared with the kb-recorder end-to-end test);
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
#   PANDASPIGOT_URL     server download (default: PandaSpigot build 226, the build this test passed on)
#   PANDASPIGOT_SHA256  expected SHA-256 of the download (default: that of build 226; empty skips the check)
#   KB_PORT             server port on 127.0.0.1 (default 25565)
#   KB_HITS_PER_PHASE   hits per phase (default 36)
#   KB_RUN_DIR          working directory (default control-run at the repository root)
#
# Output, in KB_RUN_DIR: report.md, received.jsonl, server.log and server/ with the hit log in
# server/plugins/RagoCombatEngine/hits/. Exit status 0 only if the control test passed.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUN_DIR=${KB_RUN_DIR:-$ROOT/control-run}
SERVER_JAVA=${KB_SERVER_JAVA:-java}
# The PandaSpigot build the control test passed on. To try another build, give its URL and SHA-256.
PANDASPIGOT_URL=${PANDASPIGOT_URL:-https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip}
PANDASPIGOT_SHA256=${PANDASPIGOT_SHA256-ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b}
PORT=${KB_PORT:-25565}
HITS_PER_PHASE=${KB_HITS_PER_PHASE:-36}
PHASES=3
ATTACKER=RagoAttacker
VICTIM=RagoVictim

log() { printf '[control-test] %s\n' "$*"; }
fail() { printf '[control-test] FAILED: %s\n' "$*" >&2; exit 1; }

if [[ "${KB_ACCEPT_EULA:-}" != "true" ]]; then
  fail "set KB_ACCEPT_EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA) for this test server"
fi

command -v node >/dev/null || fail "node is required"
CHECKER=$ROOT/tools/kb-control/build/install/kb-control/bin/kb-control
[[ -x "$CHECKER" ]] || fail "ControlCheck is not installed (run ./gradlew :kb-control:installDist)"
[[ -d "$ROOT/tools/kb-control/bots/node_modules/mineflayer" ]] || fail "bot dependencies missing (run npm ci in tools/kb-control/bots)"

# shellcheck source=test-server.sh
source "$ROOT/tools/kb-control/test-server.sh"
rm -f "$RUN_DIR/received.jsonl" "$RUN_DIR/report.md"
prepare_test_server "$ATTACKER" "$VICTIM"
trap stop_test_server EXIT
start_test_server

# --- Bots --------------------------------------------------------------------------------------
log "running the bots ($PHASES phases, $HITS_PER_PHASE hits each)"
bots_status=0
KB_HOST=127.0.0.1 KB_PORT=$PORT KB_RECEIVED=$RUN_DIR/received.jsonl KB_HITS_PER_PHASE=$HITS_PER_PHASE \
  node "$ROOT/tools/kb-control/bots/control-bots.mjs" || bots_status=$?
stop_test_server

if [[ $bots_status -ne 0 ]]; then
  grep -n -E 'WARN|ERROR|moved|RagoCombatEngine' "$SERVER_LOG" | tail -n 40 >&2 || true
  fail "the bots stopped with status $bots_status"
fi

# --- Check -------------------------------------------------------------------------------------
log "checking the hits"
"$CHECKER" --hits "$SERVER_DIR/plugins/RagoCombatEngine/hits" --received "$RUN_DIR/received.jsonl" \
  --server-knockback pandaspigot --min-hits $((PHASES * HITS_PER_PHASE)) --expect-sparring --report "$RUN_DIR/report.md"
