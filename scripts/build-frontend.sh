#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$(cd -- "$BACKEND_DIR/frontend" && pwd)"
SITE_DIR="$BACKEND_DIR/src/main/resources/site"

echo "==> 清理前端输出目录"
rm -rf -- "$SITE_DIR"
mkdir -p -- "$SITE_DIR"

echo "==> 前端目录: $FRONTEND_DIR"
cd -- "$FRONTEND_DIR"

if [ ! -d node_modules ]; then
    echo "==> node_modules 不存在，执行 npm ci"
    npm ci
fi

echo "==> 执行 Vite 构建"
npm run build

echo "==> 前端构建完成"