#!/bin/bash
# ═══════════════════════════════════════════════════════════
# WexowRAT - Build Frontend
# ═══════════════════════════════════════════════════════════

set -e

# Colors
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
SERVER_APP_DIR="$PROJECT_DIR/server/public/app"

cd "$SCRIPT_DIR"

# ═══════════════════════════════════════════════════════════
# Banner
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   Building Wexow Frontend${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

# ═══════════════════════════════════════════════════════════
# Check prerequisites
# ═══════════════════════════════════════════════════════════
log "[1/5] Checking prerequisites..."

if ! command -v node &> /dev/null; then
    error "Node.js not found. Run ./setup.sh"
fi

if ! command -v npm &> /dev/null; then
    error "npm not found. Run ./setup.sh"
fi

if [ ! -f "package.json" ]; then
    error "package.json not found in $SCRIPT_DIR"
fi

success "Prerequisites OK"
echo ""

# ═══════════════════════════════════════════════════════════
# Install dependencies
# ═══════════════════════════════════════════════════════════
log "[2/5] Checking npm dependencies..."

if [ ! -d "node_modules" ]; then
    log "Installing dependencies (this may take a while)..."
    npm install
    success "Dependencies installed"
else
    success "node_modules exists"
fi

# ═══════════════════════════════════════════════════════════
# Clean
# ═══════════════════════════════════════════════════════════
log "[3/5] Cleaning previous build..."

if [ -d "dist" ]; then
    rm -rf dist/
    success "Removed dist/"
else
    warn "dist/ not found (first build)"
fi

if [ -d "$SERVER_APP_DIR" ] && [ "$(ls -A "$SERVER_APP_DIR" 2>/dev/null)" ]; then
    rm -rf "$SERVER_APP_DIR"/*
    success "Cleared server/public/app/"
else
    warn "server/public/app/ already empty"
fi

# ═══════════════════════════════════════════════════════════
# Build
# ═══════════════════════════════════════════════════════════
log "[4/5] Building frontend..."
echo ""

START_TIME=$(date +%s)

npm run build

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
success "Build completed in ${DURATION}s"

# ═══════════════════════════════════════════════════════════
# Copy to server
# ═══════════════════════════════════════════════════════════
log "[5/5] Copying to server..."

if [ ! -d "dist" ]; then
    error "dist/ not found after build"
fi

if [ ! -d "$SERVER_APP_DIR" ]; then
    mkdir -p "$SERVER_APP_DIR"
    warn "Created server/public/app/"
fi

cp -rf dist/* "$SERVER_APP_DIR/"

FILE_COUNT=$(find "$SERVER_APP_DIR" -type f | wc -l)
TOTAL_SIZE=$(du -sh "$SERVER_APP_DIR" | cut -f1)

success "Copied $FILE_COUNT files ($TOTAL_SIZE) to server/public/app/"

# ═══════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}   ✅ FRONTEND BUILD COMPLETE${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "   ${CYAN}Output:${NC}     $SERVER_APP_DIR"
echo -e "   ${CYAN}Files:${NC}      $FILE_COUNT"
echo -e "   ${CYAN}Size:${NC}       $TOTAL_SIZE"
echo ""
echo -e "   ${CYAN}Next steps:${NC}"
echo "   1. Rebuild Docker:  cd $PROJECT_DIR && docker compose up -d --build"
echo "   2. Access dashboard: http://localhost:3000/app"
echo ""