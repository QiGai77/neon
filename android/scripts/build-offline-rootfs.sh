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

docker create --name "$TMP_CONTAINER" --platform linux/arm64 alpine:3.22 /bin/sh -c "
  apk add --no-cache \
    bash coreutils findutils grep sed gawk diffutils patch procps less file tree nano \
    curl wget ca-certificates tzdata openssh-client jq ripgrep fd unzip zip xz tar \
    git sqlite \
    python3 py3-pip uv \
    nodejs npm pnpm \
    build-base cmake pkgconf linux-headers &&
  npm config set update-notifier false --global &&
  mkdir -p /workspace /root/.cache &&
  rm -rf /var/cache/apk/* /root/.npm /tmp/*
"

docker start -a "$TMP_CONTAINER"
docker export "$TMP_CONTAINER" | gzip -9 > "$ASSETS_DIR/rootfs-arm64.tar.gz"
docker rm -f "$TMP_CONTAINER" >/dev/null 2>&1 || true

sha256sum "$ASSETS_DIR/rootfs-arm64.tar.gz" | cut -c1-16 > "$ASSETS_DIR/rootfs-arm64.version"
ls -lh "$ASSETS_DIR/rootfs-arm64.tar.gz"
