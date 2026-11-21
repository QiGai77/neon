#!/usr/bin/env bash
set -euo pipefail

# 构建 Neon 全离线 Alpine Linux (arm64-v8a) rootfs 归档到 app/src/main/assets/rootfs-arm64.tar.gz
# 需要本机已安装 docker（支持 --platform linux/arm64）或运行在 arm64 Linux 环境。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSETS_DIR="$ANDROID_DIR/app/src/main/assets"
mkdir -p "$ASSETS_DIR"

TMP_CONTAINER="neon-rootfs-builder-$$"
docker rm -f "$TMP_CONTAINER" >/dev/null 2>&1 || true

docker create --name "$TMP_CONTAINER" --platform linux/arm64 alpine:3.20 /bin/sh -c "
  apk add --no-cache bash python3 py3-pip curl jq git sqlite ca-certificates tzdata &&
  mkdir -p /workspace &&
  rm -rf /var/cache/apk/*
"

docker start -a "$TMP_CONTAINER"
docker export "$TMP_CONTAINER" | gzip -9 > "$ASSETS_DIR/rootfs-arm64.tar.gz"
docker rm -f "$TMP_CONTAINER" >/dev/null 2>&1 || true

ls -lh "$ASSETS_DIR/rootfs-arm64.tar.gz"
