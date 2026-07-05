#!/usr/bin/env bash
# ============================================================
# Fluxion 构建与部署脚本
# ============================================================
#
# 位置：deploy/build.sh（在项目根目录下执行）
#
# 用法：
#   ./deploy/build.sh native       # 构建 Native Image Docker 镜像（零 JRE）
#   ./deploy/build.sh jvm          # 构建传统 Fat Jar Docker 镜像
#   ./deploy/build.sh native-local # 本地构建 Native Image 二进制（需 GraalVM）
#   ./deploy/build.sh run          # 启动 Native Image 容器
#   ./deploy/build.sh run-jvm      # 启动 Fat Jar 容器
#   ./deploy/build.sh stop         # 停止并移除容器
#   ./deploy/build.sh clean        # 清理构建产物和容器
#

set -euo pipefail

# ── 配置 ──────────────────────────────────────────────────────
APP_NAME="fluxion-admin"
IMAGE_NATIVE="${APP_NAME}:native"
IMAGE_JVM="${APP_NAME}:jvm"
CONTAINER_NAME="${APP_NAME}"
PORT="${PORT:-8080}"
PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ── 颜色输出 ──────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[fluxion]${NC} $*"; }
ok()   { echo -e "${GREEN}[fluxion]${NC} $*"; }
warn() { echo -e "${YELLOW}[fluxion]${NC} $*"; }
err()  { echo -e "${RED}[fluxion]${NC} $*" >&2; }

# ── 命令实现 ──────────────────────────────────────────────────

cmd_native() {
    log "构建 Native Image Docker 镜像（零 JRE，静态链接）..."
    docker build -t "$IMAGE_NATIVE" -f "$SCRIPT_DIR/Dockerfile" --target native "$PROJECT_DIR"
    ok "构建完成: $IMAGE_NATIVE"
    docker images "$IMAGE_NATIVE" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
}

cmd_jvm() {
    log "构建传统 Fat Jar Docker 镜像（需 JRE 21）..."
    docker build -t "$IMAGE_JVM" -f "$SCRIPT_DIR/Dockerfile" --target jvm "$PROJECT_DIR"
    ok "构建完成: $IMAGE_JVM"
    docker images "$IMAGE_JVM" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
}

cmd_native_local() {
    log "本地构建 Native Image 二进制（需要 GraalVM 21 + native-image）..."

    # 检查 GraalVM
    if ! command -v native-image &>/dev/null; then
        err "native-image 未找到，请确保已安装 GraalVM 并运行: gu install native-image"
        exit 1
    fi

    log "GraalVM 版本: $(native-image --version 2>/dev/null | head -1)"

    cd "$PROJECT_DIR"
    ./gradlew :fluxion-admin:nativeCompile --no-daemon

    local binary="$PROJECT_DIR/fluxion-admin/build/native/nativeCompile/$APP_NAME"
    if [ -f "$binary" ]; then
        ok "构建成功: $binary"
        ls -lh "$binary"
        echo ""
        log "启动命令:"
        echo "  SPRING_PROFILES_ACTIVE=$PROFILE $binary"
    else
        err "构建失败，二进制文件未找到"
        exit 1
    fi
}

cmd_run() {
    log "启动 Native Image 容器..."

    # 先停止已有容器
    docker rm -f "$CONTAINER_NAME" 2>/dev/null || true

    docker run -d \
        --name "$CONTAINER_NAME" \
        -p "$PORT:8080" \
        -e "SPRING_PROFILES_ACTIVE=$PROFILE" \
        "$IMAGE_NATIVE"

    ok "容器已启动: $CONTAINER_NAME (端口 $PORT, profile=$PROFILE)"
    log "日志: docker logs -f $CONTAINER_NAME"
    log "停止: ./build.sh stop"
}

cmd_run_jvm() {
    log "启动 Fat Jar 容器..."

    docker rm -f "$CONTAINER_NAME" 2>/dev/null || true

    docker run -d \
        --name "$CONTAINER_NAME" \
        -p "$PORT:8080" \
        -e "SPRING_PROFILES_ACTIVE=$PROFILE" \
        "$IMAGE_JVM"

    ok "容器已启动: $CONTAINER_NAME (端口 $PORT, profile=$PROFILE)"
    log "日志: docker logs -f $CONTAINER_NAME"
    log "停止: ./build.sh stop"
}

cmd_stop() {
    log "停止容器 $CONTAINER_NAME..."
    docker rm -f "$CONTAINER_NAME" 2>/dev/null && ok "已停止" || warn "容器未运行"
}

cmd_clean() {
    log "清理构建产物..."
    cd "$PROJECT_DIR"
    ./gradlew clean --no-daemon 2>/dev/null || true
    docker rmi "$IMAGE_NATIVE" "$IMAGE_JVM" 2>/dev/null || true
    ok "清理完成"
}

cmd_compose_native() {
    log "Compose 启动完整环境（Native Image + MySQL + Redis）..."
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" --profile native up -d --build
    ok "环境已启动"
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" ps
}

cmd_compose_jvm() {
    log "Compose 启动完整环境（JVM + MySQL + Redis）..."
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" --profile jvm up -d --build
    ok "环境已启动"
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" ps
}

cmd_compose_dev() {
    log "Compose 启动开发环境（JVM + MySQL + Redis）..."
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" --profile dev up -d --build
    ok "开发环境已启动"
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" ps
}

cmd_compose_down() {
    log "停止 Compose 环境..."
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" down
    ok "已停止"
}

cmd_compose_logs() {
    docker compose -f "$SCRIPT_DIR/docker-compose.yaml" logs -f "${2:-fluxion-admin}"
}

cmd_help() {
    echo "用法: ./build.sh <命令>"
    echo ""
    echo "构建命令:"
    echo "  native        构建 Native Image Docker 镜像（零 JRE，~100MB）"
    echo "  jvm           构建传统 Fat Jar Docker 镜像（需 JRE，~200MB）"
    echo "  native-local  本地构建 Native Image 二进制（需 GraalVM 环境）"
    echo ""
    echo "运行命令:"
    echo "  run           启动 Native Image 容器"
    echo "  run-jvm       启动 Fat Jar 容器"
    echo "  stop          停止容器"
    echo ""
    echo "Compose 命令:"
    echo "  up-native     Compose 启动完整环境（Native + MySQL + Redis）"
    echo "  up-jvm        Compose 启动完整环境（JVM + MySQL + Redis）"
    echo "  up-dev        Compose 启动开发环境"
    echo "  down          Compose 停止环境"
    echo "  logs          Compose 查看日志"
    echo ""
    echo "其他:"
    echo "  clean         清理构建产物和镜像"
    echo "  help          显示此帮助"
    echo ""
    echo "环境变量:"
    echo "  PORT=8080                    宿主机映射端口"
    echo "  SPRING_PROFILES_ACTIVE=prod  Spring 配置"
}

# ── 入口 ──────────────────────────────────────────────────────
case "${1:-help}" in
    native)       cmd_native ;;
    jvm)          cmd_jvm ;;
    native-local) cmd_native_local ;;
    run)          cmd_run ;;
    run-jvm)      cmd_run_jvm ;;
    stop)         cmd_stop ;;
    up-native)    cmd_compose_native ;;
    up-jvm)       cmd_compose_jvm ;;
    up-dev)       cmd_compose_dev ;;
    down)         cmd_compose_down ;;
    logs)         cmd_compose_logs "$@" ;;
    clean)        cmd_clean ;;
    help|--help|-h) cmd_help ;;
    *)            err "未知命令: $1"; cmd_help; exit 1 ;;
esac
