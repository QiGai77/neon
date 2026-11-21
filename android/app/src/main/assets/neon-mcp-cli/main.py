#!/usr/bin/env python3
"""
neon-mcp-cli: Lightweight JSON-RPC 2.0 stdio MCP bridge running inside Neon's offline Alpine sandbox.
Usage:
  python3 /usr/local/bin/neon-mcp-cli list --cmd "python3 /workspace/server.py"
  python3 /usr/local/bin/neon-mcp-cli call --cmd "python3 /workspace/server.py" --tool my_tool --args '{"x":1}'
"""
import argparse
import json
import shlex
import subprocess
import sys


def rpc_exchange(cmd: str, method: str, params: dict) -> dict:
    proc = subprocess.Popen(
        shlex.split(cmd),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    assert proc.stdin and proc.stdout

    def send(obj: dict):
        proc.stdin.write(json.dumps(obj) + "\n")
        proc.stdin.flush()

    def recv() -> dict:
        while True:
            line = proc.stdout.readline()
            if not line:
                err = proc.stderr.read() if proc.stderr else ""
                raise RuntimeError(f"stdio MCP EOF: {err.strip()}")
            line = line.strip()
            if line.startswith("{"):
                return json.loads(line)

    try:
        send({
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "neon-mcp-cli", "version": "0.3.0"},
            },
        })
        _ = recv()
        send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        send({"jsonrpc": "2.0", "id": 2, "method": method, "params": params})
        return recv()
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proc.kill()


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="action", required=True)
    p_list = sub.add_parser("list")
    p_list.add_argument("--cmd", required=True)

    p_call = sub.add_parser("call")
    p_call.add_argument("--cmd", required=True)
    p_call.add_argument("--tool", required=True)
    p_call.add_argument("--args", default="{}")

    args = ap.parse_args()
    if args.action == "list":
        res = rpc_exchange(args.cmd, "tools/list", {})
    else:
        res = rpc_exchange(args.cmd, "tools/call", {"name": args.tool, "arguments": json.loads(args.args)})
    print(json.dumps(res, ensure_ascii=False))


if __name__ == "__main__":
    main()
