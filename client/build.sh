#!/bin/bash
# ═══════════════════════════════════════════════════════════
# WexowRAT - Build Client APK
# ═══════════════════════════════════════════════════════════

set -e

# ═══════════════════════════════════════════════════════════
# Colors
# ═══════════════════════════════════════════════════════════
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()     { echo -e "${BLUE}[*]${NC} $1"; }
success() { echo -e "${GREEN}[✓]${NC} $1"; }
warn()    { echo -e "${YELLOW}[!]${NC} $1"; }
error()   { echo -e "${RED}[✗]${NC} $1"; exit 1; }

# ═══════════════════════════════════════════════════════════
# Paths
# ═══════════════════════════════════════════════════════════
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVER_DIR="$PROJECT_DIR/server"

cd "$SCRIPT_DIR"

# ═══════════════════════════════════════════════════════════
# Banner
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   Building Wexow Client APK${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

# ═══════════════════════════════════════════════════════════
# Check prerequisites
# ═══════════════════════════════════════════════════════════
log "[1/6] Checking prerequisites..."

# ⭐ Java 11
JAVA_11="/usr/lib/jvm/temurin-11-jdk-amd64"
if [ ! -d "$JAVA_11" ]; then
    JAVA_11="/usr/lib/jvm/java-11-openjdk-amd64"
fi

if [ ! -d "$JAVA_11" ]; then
    error "Java 11 not found. Run ./setup.sh"
fi

export JAVA_HOME="$JAVA_11"
export PATH="$JAVA_HOME/bin:$PATH"

log "Using Java: $JAVA_HOME"

# ⭐ ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    log "ANDROID_HOME set to: $ANDROID_HOME"
fi

if [ ! -d "$ANDROID_HOME" ]; then
    error "Android SDK not found at: $ANDROID_HOME"
fi

# ⭐ Build tools
if [ -d "$ANDROID_HOME/build-tools/34.0.0" ]; then
    export PATH="$PATH:$ANDROID_HOME/build-tools/34.0.0"
fi

# ⭐ gradlew
if [ ! -f "gradlew" ]; then
    error "gradlew not found in $SCRIPT_DIR"
fi

chmod +x gradlew 2>/dev/null || true

success "Prerequisites OK"
echo ""

# ═══════════════════════════════════════════════════════════
# Check local.properties
# ═══════════════════════════════════════════════════════════
log "[2/6] Checking local.properties..."

if [ ! -f "local.properties" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    success "Created local.properties"
else
    success "local.properties exists"
fi

# ═══════════════════════════════════════════════════════════
# Clean
# ═══════════════════════════════════════════════════════════
log "[3/6] Cleaning previous build..."

if [ "${1:-}" != "--no-clean" ]; then
    ./gradlew clean --quiet 2>&1 | tail -1
    success "Cleaned"
else
    warn "Skipping clean (--no-clean)"
fi

# ═══════════════════════════════════════════════════════════
# Build
# ═══════════════════════════════════════════════════════════
log "[4/6] Building debug APK..."
echo ""

START_TIME=$(date +%s)

./gradlew assembleDebug --stacktrace

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
success "Build completed in ${DURATION}s"

# ═══════════════════════════════════════════════════════════
# Verify APK
# ═══════════════════════════════════════════════════════════
log "[5/6] Verifying APK..."

APK_SOURCE="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_SOURCE" ]; then
    error "APK not found at: $APK_SOURCE"
fi

APK_SIZE=$(du -h "$APK_SOURCE" | awk '{print $1}')

APK_SIZE_BYTES=$(stat -c%s "$APK_SOURCE" 2>/dev/null || stat -f%z "$APK_SOURCE")

success "APK built: $APK_SIZE ($APK_SIZE_BYTES bytes)"

# ═══════════════════════════════════════════════════════════
# Copy to server
# ═══════════════════════════════════════════════════════════
log "[6/6] Copying to server..."

if [ -d "$SERVER_DIR" ]; then
    if [ -f "$SERVER_DIR/base.apk" ]; then
        cp "$SERVER_DIR/base.apk" "$SERVER_DIR/base.apk.bak"
        warn "Backup created: base.apk.bak"
    fi

    cp "$APK_SOURCE" "$SERVER_DIR/base.apk"
    success "Copied to: $SERVER_DIR/base.apk"
else
    warn "Server directory not found: $SERVER_DIR"
    warn "APK remains at: $APK_SOURCE"
fi

# ═══════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}   ✅ CLIENT BUILD COMPLETE${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "   ${CYAN}APK source:${NC}  $APK_SOURCE"
echo -e "   ${CYAN}APK size:${NC}    $APK_SIZE ($APK_SIZE_BYTES bytes)"
echo -e "   ${CYAN}Copied to:${NC}   $SERVER_DIR/base.apk"
echo ""
echo -e "   ${CYAN}Next steps:${NC}"
echo "   1. Build custom APK:"
echo "      cd $SERVER_DIR && node builder.js --config build_config.json"
echo "   2. Install on device:"
echo ""
echo -e "      ${GREEN}adb install -r $SERVER_DIR/output/Wexow.apk${NC}"
echo ""