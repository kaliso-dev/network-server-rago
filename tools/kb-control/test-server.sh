# Local PandaSpigot 1.8.8 test server for the automated knockback tests, sourced by
# run-control-test.sh and tools/kb-recorder/run-e2e-test.sh. Not a script to run on its own.
#
# The caller defines log and fail, and sets:
#   ROOT                repository root
#   RUN_DIR             working directory of the test; the server goes in $RUN_DIR/server
#   SERVER_JAVA         java binary for the server
#   PORT                server port on 127.0.0.1
#   PANDASPIGOT_URL     server download
#   PANDASPIGOT_SHA256  expected SHA-256 of the download (empty skips the check)
#
# Then: prepare_test_server <operator name>..., start_test_server, and stop_test_server (also safe in
# an EXIT trap). The server runs RagoCombatEngine in observe mode with its hit log turned on, in
# $SERVER_DIR/plugins/RagoCombatEngine/hits.

SERVER_DIR=$RUN_DIR/server
CACHE_DIR=$RUN_DIR/cache
SERVER_LOG=$RUN_DIR/server.log
SERVER_PID=

# The server prints to stdout and to logs/latest.log; look in both.
server_log_has() { grep -qsF -- "$1" "$SERVER_LOG" "$SERVER_DIR/logs/latest.log"; }

# Checks the tools, downloads PandaSpigot (cached in $CACHE_DIR) and writes a flat, offline-mode
# server bound to 127.0.0.1 with the engine plugin. The named players are operators.
prepare_test_server() {
  for tool in curl python3; do
    command -v "$tool" >/dev/null || fail "$tool is required"
  done
  "$SERVER_JAVA" -version >/dev/null 2>&1 || fail "cannot run $SERVER_JAVA"

  local plugin_jars
  shopt -s nullglob
  plugin_jars=("$ROOT"/combat/rago-combat-engine/build/libs/rago-combat-engine-*.jar)
  shopt -u nullglob
  [[ ${#plugin_jars[@]} -eq 1 ]] || fail "expected one plugin jar in combat/rago-combat-engine/build/libs (run ./gradlew build)"

  # A server left over from an earlier run must not answer instead of ours.
  if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
    fail "port $PORT is already in use on 127.0.0.1"
  fi

  mkdir -p "$CACHE_DIR"
  rm -rf "$SERVER_DIR" "$SERVER_LOG"
  mkdir -p "$SERVER_DIR/plugins/RagoCombatEngine"

  # One cached jar per download URL, so that changing the URL downloads again.
  local server_jar actual_sha256
  server_jar=$CACHE_DIR/pandaspigot-$(printf '%s' "$PANDASPIGOT_URL" | sha256sum | cut -c1-16).jar
  if [[ ! -s "$server_jar" ]]; then
    log "downloading PandaSpigot from $PANDASPIGOT_URL"
    curl -fsSL --retry 3 --retry-delay 5 -o "$server_jar.part" "$PANDASPIGOT_URL"
    mv "$server_jar.part" "$server_jar"
  fi
  actual_sha256=$(sha256sum "$server_jar" | cut -d' ' -f1)
  log "PandaSpigot jar SHA-256: $actual_sha256"
  if [[ -n "$PANDASPIGOT_SHA256" && "$actual_sha256" != "$PANDASPIGOT_SHA256" ]]; then
    fail "PandaSpigot SHA-256 is $actual_sha256, expected $PANDASPIGOT_SHA256" \
      "(for another build, set PANDASPIGOT_SHA256 to its SHA-256, or to an empty value to skip the check)"
  fi

  cp "$server_jar" "$SERVER_DIR/server.jar"
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
motd=RagoCombatEngine test server
EOF

  # Only the overworld: the tests never leave it.
  cat >"$SERVER_DIR/bukkit.yml" <<'EOF'
settings:
  allow-end: false
EOF

  # The operators run the test commands (the victim of the control test runs /kb sparring on
  # itself). On an offline-mode server a UUID is derived from the name, the way the server does it
  # (UUID.nameUUIDFromBytes("OfflinePlayer:" + name)).
  local operators='' operator operator_uuid
  for operator in "$@"; do
    operator_uuid=$(python3 -c 'import hashlib, sys, uuid; print(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + sys.argv[1]).encode("utf-8")).digest(), version=3))' "$operator")
    operators="$operators${operators:+, }{\"uuid\": \"$operator_uuid\", \"name\": \"$operator\", \"level\": 4, \"bypassesPlayerLimit\": false}"
  done
  printf '[%s]\n' "$operators" >"$SERVER_DIR/ops.json"

  # Engine settings for the tests. control-alt is an arbitrary test profile, far enough from
  # vanilla to change every packet; it is not a tuning proposal. still pushes by nothing: the hit
  # delay bots of the control test use it to keep the victim in reach.
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
  still:
    friction: 1.0
    horizontal: 0.0
    vertical: 0.0
    vertical-limit: 0.0
    extra-horizontal: 0.0
    extra-vertical: 0.0
EOF
}

# Starts the server in the background and waits until it is ready and the engine is enabled.
start_test_server() {
  log "starting PandaSpigot with $("$SERVER_JAVA" -version 2>&1 | head -n 1)"
  # --noconsole: the server reads no commands from stdin (the bots send them). The system property
  # skips the 20 s pause Spigot adds for builds older than a few weeks.
  (cd "$SERVER_DIR" && exec "$SERVER_JAVA" -Xms512M -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true \
    -jar server.jar --noconsole nogui </dev/null >"$SERVER_LOG" 2>&1) &
  SERVER_PID=$!

  local started=false
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
}

stop_test_server() {
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
