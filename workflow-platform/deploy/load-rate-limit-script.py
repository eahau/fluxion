#!/usr/bin/env python3
"""
Load the Fluxion rate-limit Lua script into Redis.

Usage:
  # Standalone
  python3 load-rate-limit-script.py --host 127.0.0.1 --port 6379

  # Cluster
  python3 load-rate-limit-script.py --host 127.0.0.1 --port 6379 --cluster

  # Just print the SHA1 of the Lua script (no Redis needed)
  python3 load-rate-limit-script.py --print-sha

Requires:
  pip install redis
"""

import argparse
import hashlib
import sys
from pathlib import Path
from typing import Optional

# Optional redis import; only needed when actually loading.
try:
    import redis
except ImportError:
    redis = None


DEFAULT_LUA_FILE = (
    Path(__file__).parent.parent
    / "fluxion-redis/core/src/main/resources/rate-limit.lua"
)


def sha1(script: str) -> str:
    return hashlib.sha1(script.encode("utf-8")).hexdigest()


def load_standalone(script: str, host: str, port: int, password: Optional[str]):
    r = redis.Redis(host=host, port=port, password=password, decode_responses=True)
    loaded_sha = r.script_load(script)
    expected_sha = sha1(script)
    if loaded_sha != expected_sha:
        raise RuntimeError(f"SHA mismatch: expected {expected_sha}, got {loaded_sha}")
    print(f"[OK] Loaded on {host}:{port}, SHA: {loaded_sha}")
    return loaded_sha


def load_cluster(script: str, host: str, port: int, password: Optional[str]):
    rc = redis.RedisCluster(
        host=host,
        port=port,
        password=password,
        decode_responses=True,
        skip_full_coverage_check=True,
    )
    # redis-py Cluster.script_load broadcasts to all nodes in recent versions.
    loaded_sha = rc.script_load(script)
    expected_sha = sha1(script)
    if loaded_sha != expected_sha:
        raise RuntimeError(f"SHA mismatch: expected {expected_sha}, got {loaded_sha}")
    print(f"[OK] Loaded on cluster {host}:{port}, SHA: {loaded_sha}")
    return loaded_sha


def main():
    parser = argparse.ArgumentParser(description="Load Fluxion rate-limit Lua script into Redis")
    parser.add_argument("--lua-file", type=Path, default=DEFAULT_LUA_FILE,
                        help="Path to rate-limit.lua")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=6379)
    parser.add_argument("--password", default=None)
    parser.add_argument("--cluster", action="store_true", help="Load into Redis Cluster")
    parser.add_argument("--print-sha", action="store_true",
                        help="Print script SHA1 and exit (no Redis needed)")
    args = parser.parse_args()

    if not args.lua_file.exists():
        print(f"ERROR: Lua file not found: {args.lua_file}", file=sys.stderr)
        sys.exit(1)

    script = args.lua_file.read_text(encoding="utf-8")
    expected_sha = sha1(script)

    if args.print_sha:
        print(expected_sha)
        return

    print(f"Script length: {len(script)} bytes")
    print(f"Expected SHA1: {expected_sha}")

    if redis is None:
        print("ERROR: redis package not installed. Run: pip install redis", file=sys.stderr)
        sys.exit(1)

    try:
        if args.cluster:
            load_cluster(script, args.host, args.port, args.password)
        else:
            load_standalone(script, args.host, args.port, args.password)
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
