#!/bin/bash
# ═══════════════════════════════════════════════════════════
# WexowRAT - Prerequisites Check
# ═══════════════════════════════════════════════════════════

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Counters
PASS=0
FAIL=0
WARN=0

# ═══════════════════════════════════════════════════════════
# Helper functions
# ═══════════════════════════════════════════════════════════
check() {
    local name="$1"
    local cmd="$2"
    local expected="$3"

    printf "  %-25s" "$name"

    if eval "$cmd" &>/dev/null; then
        local output=$(eval "$cmd" 2>&1 | head -1)
        echo -e "${GREEN}✓${NC} $output"
        ((PASS++))
        return 0
    else
        if [ -n "$expected" ]; then
            echo -e "${RED}✗${NC} Missing (need $expected)"
        else
            echo -e "${RED}✗${NC} Missing"
        fi
        ((FAIL++))
        return 1
    fi
}

check_optional() {
    local name="$1"
    local cmd="$2"

    printf "  %-25s" "$name"

    if eval "$cmd" &>/dev/null; then
        local output=$(eval "$cmd" 2>&1 | head -1)
        echo -e "${GREEN}✓${NC} $output"
        ((PASS++))
    else
        echo -e "${YELLOW}○${NC} Not installed (optional)"
        ((WARN++))
    fi
}

check_dir() {
    local name="$1"
    local dir="$2"

    printf "  %-25s" "$name"

    if [ -d "$dir" ]; then
        echo -e "${GREEN}✓${NC} $dir"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} Missing: $dir"
        ((FAIL++))
    fi
}

check_file() {
    local name="$1"
    local file="$2"

    printf "  %-25s" "$name"

    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $file"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} Missing: $file"
        ((FAIL++))
    fi
}

check_env() {
    local name="$1"
    local var="$2"

    printf "  %-25s" "$name"

    if [ -n "${!var}" ]; then
        echo -e "${GREEN}✓${NC} ${!var}"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} Not set"
        ((FAIL++))
    fi
}

# ═══════════════════════════════════════════════════════════
# Banner
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   WexowRAT - Prerequisites Check${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""

# ═══════════════════════════════════════════════════════════
# 1. Core tools
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[1] Core Tools${NC}"
check "Node.js 18+"    "node --version | grep -E 'v18|v19|v20|v21|v22'" "Node.js 18+"
check "npm 9+"         "npm --version | grep -E '^(9|10|11)'"                  "npm 9+"
check "Git"            "git --version"                                          "git"
check "curl"           "curl --version"                                         "curl"
check "wget"           "wget --version"                                         "wget"
check "unzip"          "unzip -v"                                               "unzip"

echo ""

# ═══════════════════════════════════════════════════════════
# 2. Java
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[2] Java${NC}"
check "Java 21 (apktool)" "java --version 2>&1 | head -1 | grep '21'"          "Java 21"

if [ -d "/usr/lib/jvm/temurin-11-jdk-amd64" ]; then
    echo -e "  $(printf '%-25s' 'Java 11 (Gradle)')${GREEN}✓${NC} /usr/lib/jvm/temurin-11-jdk-amd64"
    ((PASS++))
elif [ -d "/usr/lib/jvm/java-11-openjdk-amd64" ]; then
    echo -e "  $(printf '%-25s' 'Java 11 (Gradle)')${GREEN}✓${NC} /usr/lib/jvm/java-11-openjdk-amd64"
    ((PASS++))
else
    echo -e "  $(printf '%-25s' 'Java 11 (Gradle)')${RED}✗${NC} Missing"
    ((FAIL++))
fi

echo ""

# ═══════════════════════════════════════════════════════════
# 3. APK Build Tools
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[3] APK Build Tools${NC}"
check "apktool 3.0.2"  "apktool --version 2>/dev/null | grep '3.0.2'"          "apktool 3.0.2"
check "zipalign"       "zipalign -h"                                            "zipalign"
check "apksigner"      "apksigner --version"                                    "apksigner"
check "keytool"        "keytool -help"                                          "keytool"

echo ""

# ═══════════════════════════════════════════════════════════
# 4. Android SDK
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[4] Android SDK${NC}"
check_env "ANDROID_HOME"       "ANDROID_HOME"
check "ADB"                    "adb --version"
check "sdkmanager"             "sdkmanager --version"

if [ -n "$ANDROID_HOME" ]; then
    check_dir "Platform 34"        "$ANDROID_HOME/platforms/android-34"
    check_dir "Build Tools 34"     "$ANDROID_HOME/build-tools/34.0.0"
fi

echo ""

# ═══════════════════════════════════════════════════════════
# 5. Docker (optional)
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[5] Docker (Optional)${NC}"
check_optional "Docker"          "docker --version"
check_optional "Docker Compose"  "docker compose version"

echo ""

# ═══════════════════════════════════════════════════════════
# 6. Tailscale (optional)
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[6] Tailscale (Optional)${NC}"
check_optional "Tailscale"       "tailscale --version"

echo ""

# ═══════════════════════════════════════════════════════════
# 7. Environment Variables
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[7] Environment Variables${NC}"
check_env "JAVA_HOME"            "JAVA_HOME"
check_env "ANDROID_HOME"         "ANDROID_HOME"

echo ""

# ═══════════════════════════════════════════════════════════
# 8. Project Files
# ═══════════════════════════════════════════════════════════
echo -e "${BLUE}[8] Project Files${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

cd "$PROJECT_DIR" 2>/dev/null || true

# ⭐ Root files
check_file "Root build.sh"           "build.sh"
check_file "Root README"             "README.md"
check_file "docker-compose.yml"      "docker-compose.yml"
check_file ".env.example"            ".env.example"

# ⭐ Server files
check_file "Server: server.js"       "server/server.js"
check_file "Server: builder.js"      "server/builder.js"
check_file "Server: Dockerfile"      "server/Dockerfile"
check_file "Server: package.json"    "server/package.json"

# ⭐ Client files
check_file "Client: build.sh"        "client/build.sh"
check_file "Client: gradlew"         "client/gradlew"
check_file "Client: build.gradle"    "client/build.gradle"

# ⭐ Frontend files
check_file "Frontend: package.json"  "frontend/package.json"

# ═══════════════════════════════════════════════════════════
# 9. Optional files
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${BLUE}[9] Optional Files${NC}"

if [ -f "server/base.apk" ]; then
    echo -e "  $(printf '%-25s' 'server/base.apk')${GREEN}✓${NC} Exists"
    ((PASS++))
else
    echo -e "  $(printf '%-25s' 'server/base.apk')${YELLOW}○${NC} Will be created by client/build.sh"
    ((WARN++))
fi

if [ -f "server/.env" ]; then
    echo -e "  $(printf '%-25s' 'server/.env')${GREEN}✓${NC} Exists"
    ((PASS++))
else
    echo -e "  $(printf '%-25s' 'server/.env')${YELLOW}○${NC} Copy from .env.example"
    ((WARN++))
fi

if [ -d "frontend/node_modules" ]; then
    echo -e "  $(printf '%-25s' 'frontend/node_modules')${GREEN}✓${NC} Installed"
    ((PASS++))
else
    echo -e "  $(printf '%-25s' 'frontend/node_modules')${YELLOW}○${NC} Run: cd frontend && npm install"
    ((WARN++))
fi

if [ -d "server/node_modules" ]; then
    echo -e "  $(printf '%-25s' 'server/node_modules')${GREEN}✓${NC} Installed"
    ((PASS++))
else
    echo -e "  $(printf '%-25s' 'server/node_modules')${YELLOW}○${NC} Will be installed in Docker"
    ((WARN++))
fi

# ═══════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   Summary${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "   ${GREEN}Passed:${NC}   $PASS"
echo -e "   ${YELLOW}Warnings:${NC} $WARN"
echo -e "   ${RED}Failed:${NC}   $FAIL"
echo ""

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}   ✅ All prerequisites met!${NC}"
    echo ""
    echo -e "   ${CYAN}Next steps:${NC}"
    echo "   1. cp .env.example .env && nano .env"
    echo "   2. ./build.sh"
    exit 0
else
    echo -e "${RED}   ❌ $FAIL prerequisite(s) missing!${NC}"
    echo ""
    echo -e "   ${CYAN}Run setup:${NC}"
    echo "   ./setup.sh"
    exit 1
fi