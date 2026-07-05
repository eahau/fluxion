#!/bin/bash
set -euo pipefail

# Load the Fluxion rate-limit Lua script into Redis.
#
# Usage:
#   ./load-rate-limit-script.sh
#   ./load-rate-limit-script.sh --host 127.0.0.1 --port 6379
#   ./load-rate-limit-script.sh --host 127.0.0.1 --port 6379 --cluster
#   ./load-rate-limit-script.sh --host 127.0.0.1 --port 6379 --password secret

HOST="127.0.0.1"
PORT="6379"
PASSWORD=""
CLUSTER=false
LUA_FILE="$(dirname "$0")/../fluxion-redis/core/src/main/resources/rate-limit.lua"

usage() {
  echo "Usage: $0 [--host HOST] [--port PORT] [--password PASS] [--cluster]"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)     HOST="$2"; shift 2 ;;
    --port)     PORT="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --cluster)  CLUSTER=true; shift ;;
    -h|--help)  usage ;;
    *)          usage ;;
  esac
done

AUTH_ARGS=()
if [[ -n "$PASSWORD" ]]; then
  AUTH_ARGS=(-a "$PASSWORD")
fi

load_standalone() {
  echo "Loading script to $HOST:$PORT ..."
  redis-cli -h "$HOST" -p "$PORT" "${AUTH_ARGS[@]}" -x SCRIPT LOAD < "$LUA_FILE"
}

load_cluster() {
  local nodes
  nodes=$(redis-cli -h "$HOST" -p "$PORT" "${AUTH_ARGS[@]}" --raw CLUSTER NODES \
    | awk '/master/ {print $2}' \
    | cut -d'@' -f1)

  if [[ -z "$nodes" ]]; then
    echo "ERROR: no master nodes found in cluster" >&2
    exit 1
  fi

  for node in $nodes; do
    local node_host="${node%:*}"
    local node_port="${node##*:}"
    echo "Loading script to $node_host:$node_port ..."
    redis-cli -h "$node_host" -p "$node_port" "${AUTH_ARGS[@]}" -x SCRIPT LOAD < "$LUA_FILE"
  done
}

if [[ ! -f "$LUA_FILE" ]]; then
  echo "ERROR: Lua file not found: $LUA_FILE" >&2
  exit 1
fi

if $CLUSTER; then
  load_cluster
else
  load_standalone
fi
