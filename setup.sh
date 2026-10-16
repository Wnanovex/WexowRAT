#!/bin/bash
# ═══════════════════════════════════════════════════════════
# WexowRAT - Setup Script
# ═══════════════════════════════════════════════════════════
# Installs all host dependencies:
#   • Node.js 18
#   • Java 21 (for apktool)
#   • Java 11 (for Gradle)
#   • Android SDK (platform 34 + build tools)
#   • apktool 3.0.2
#   • zipalign + apksigner
#   • Docker (optional)
#   • Tailscale (optional)
# ═══════════════════════════════════════════════════════════

set -e
set -o pipefail

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

check_command() {
    command -v "$1" &> /dev/null
}

# ═══════════════════════════════════════════════════════════
# Configuration
# ═══════════════════════════════════════════════════════════
INSTALL_DOCKER="${INSTALL_DOCKER:-auto}"
INSTALL_TAILSCALE="${INSTALL_TAILSCALE:-auto}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
JAVA_11_HOME="/usr/lib/jvm/temurin-11-jdk-amd64"
JAVA_21_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

# ═══════════════════════════════════════════════════════════
# Detect OS
# ═══════════════════════════════════════════════════════════
detect_os() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        if [ -f /etc/debian_version ]; then
            OS="debian"
        elif [ -f /etc/redhat-release ]; then
            OS="redhat"
        else
            OS="linux"
        fi
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
    else
        OS="unknown"
    fi

    if [ "$OS" == "unknown" ]; then
        error "Unsupported OS. Please install dependencies manually."
    fi

    log "Detected OS: $OS"
}

# ═══════════════════════════════════════════════════════════
# Banner
# ═══════════════════════════════════════════════════════════
clear
echo -e "${CYAN}"
cat << "EOF"
╔═══════════════════════════════════════════════════════════╗
║                                                           ║
║          🎯  WexowRAT - Setup Wizard                      ║
║                                                           ║
║          Advanced Android RAT                             ║
║          Complete Environment Setup                       ║
║                                                           ║
╚═══════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

detect_os

# ═══════════════════════════════════════════════════════════
# Confirm
# ═══════════════════════════════════════════════════════════
echo ""
warn "This will install multiple packages on your system."
read -p "Continue? [y/N] " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    warn "Aborted."
    exit 0
fi

# ═══════════════════════════════════════════════════════════
# STEP 1: Update package lists
# ═══════════════════════════════════════════════════════════
log "[1/9] Updating package lists..."

case $OS in
    debian)
        sudo apt-get update -qq
        ;;
    redhat)
        sudo yum check-update -q || true
        ;;
    macos)
        if ! check_command brew; then
            error "Homebrew not installed. Install from https://brew.sh"
        fi
        ;;
esac
success "Package lists updated"

# ═══════════════════════════════════════════════════════════
# STEP 2: Install basic tools
# ═══════════════════════════════════════════════════════════
log "[2/9] Installing basic tools..."

case $OS in
    debian)
        sudo apt-get install -y \
            curl wget git unzip \
            ca-certificates gnupg \
            openjdk-21-jdk-headless \
            zipalign apksigner \
            > /dev/null
        ;;
    redhat)
        sudo yum install -y \
            curl wget git unzip \
            java-21-openjdk-devel
        ;;
    macos)
        brew install curl wget git unzip
        brew install openjdk@21
    ;;
esac
success "Basic tools installed"

# ═══════════════════════════════════════════════════════════
# STEP 3: Install Node.js 18
# ═══════════════════════════════════════════════════════════
log "[3/9] Installing Node.js 18..."

if check_command node && [[ "$(node --version)" == v18* ]]; then
    success "Node.js 18 already installed: $(node --version)"
else
    case $OS in
        debian)
            curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash - > /dev/null 2>&1
            sudo apt-get install -y nodejs > /dev/null
            ;;
        redhat)
            curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash - > /dev/null 2>&1
            sudo yum install -y nodejs > /dev/null
            ;;
        macos)
            brew install node@18
            brew link --overwrite --force node@18
            ;;
    esac
    success "Node.js installed: $(node --version)"
fi

# ═══════════════════════════════════════════════════════════
# STEP 4: Install Java 11 (for Gradle)
# ═══════════════════════════════════════════════════════════
log "[4/9] Installing Java 11 (for Gradle)..."

if [ -d "$JAVA_11_HOME" ]; then
    success "Java 11 already installed"
else
    case $OS in
        debian)
            sudo apt-get install -y temurin-11-jdk > /dev/null 2>&1 || {
                sudo apt-get install -y openjdk-11-jdk-headless > /dev/null
                JAVA_11_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
            }
            ;;
        redhat)
            sudo yum install -y java-11-openjdk-devel > /dev/null
            JAVA_11_HOME="/usr/lib/jvm/java-11-openjdk"
            ;;
        macos)
            brew install --cask temurin@11
            JAVA_11_HOME=$(/usr/libexec/java_home -v 11 2>/dev/null || echo "")
            ;;
    esac
    success "Java 11 installed"
fi

# ═══════════════════════════════════════════════════════════
# STEP 5: Install apktool 3.0.2
# ═══════════════════════════════════════════════════════════
log "[5/9] Installing apktool 3.0.2..."

if check_command apktool && [[ "$(apktool --version 2>/dev/null)" == "3.0.2" ]]; then
    success "apktool 3.0.2 already installed"
else
    sudo wget -q https://github.com/iBotPeaches/Apktool/releases/download/v3.0.2/apktool_3.0.2.jar -O /opt/apktool.jar

    sudo bash -c 'printf "#!/bin/sh\nexec java -jar /opt/apktool.jar \"\$@\"\n" > /usr/local/bin/apktool'
    sudo chmod +x /usr/local/bin/apktool

    success "apktool installed: $(apktool --version)"
fi

# ═══════════════════════════════════════════════════════════
# STEP 6: Install Android SDK
# ═══════════════════════════════════════════════════════════
log "[6/9] Installing Android SDK..."

if [ -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    success "Android SDK already installed"
else
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    cd /tmp

    if [ ! -f "commandlinetools.zip" ]; then
        wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O commandlinetools.zip
    fi

    unzip -q commandlinetools.zip -d "$ANDROID_HOME/cmdline-tools/"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" 2>/dev/null || true

    rm -f commandlinetools.zip
    success "Android SDK command-line tools installed"
fi

# ═══════════════════════════════════════════════════════════
# STEP 7: Install SDK packages
# ═══════════════════════════════════════════════════════════
log "[7/9] Installing SDK packages (this may take a while)..."

export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
export PATH="$PATH:$ANDROID_HOME/platform-tools"

yes | sdkmanager --licenses > /dev/null 2>&1 || true

sdkmanager --install \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    > /dev/null 2>&1 || warn "Some SDK packages may have failed"

success "SDK packages installed"

# ═══════════════════════════════════════════════════════════
# STEP 8: Optional - Docker
# ═══════════════════════════════════════════════════════════
log "[8/9] Docker (optional)..."

if [ "$INSTALL_DOCKER" == "auto" ]; then
    read -p "Install Docker? [y/N] " -n 1 -r
    echo ""
    INSTALL_DOCKER=$([[ $REPLY =~ ^[Yy]$ ]] && echo "yes" || echo "no")
fi

if [ "$INSTALL_DOCKER" == "yes" ]; then
    if check_command docker; then
        success "Docker already installed: $(docker --version)"
    else
        case $OS in
            debian)
                curl -fsSL https://get.docker.com | sh
                sudo usermod -aG docker "$USER"
                success "Docker installed (re-login to use without sudo)"
                ;;
            macos)
                brew install --cask docker
                success "Docker installed (launch Docker.app)"
                ;;
            *)
                warn "Docker install not supported on this OS"
                ;;
        esac
    fi
else
    warn "Skipped Docker"
fi

# ═══════════════════════════════════════════════════════════
# STEP 9: Optional - Tailscale
# ═══════════════════════════════════════════════════════════
log "[9/9] Tailscale (optional)..."

if [ "$INSTALL_TAILSCALE" == "auto" ]; then
    read -p "Install Tailscale? [y/N] " -n 1 -r
    echo ""
    INSTALL_TAILSCALE=$([[ $REPLY =~ ^[Yy]$ ]] && echo "yes" || echo "no")
fi

if [ "$INSTALL_TAILSCALE" == "yes" ]; then
    if check_command tailscale; then
        success "Tailscale already installed"
    else
        case $OS in
            debian)
                curl -fsSL https://tailscale.com/install.sh | sh
                success "Tailscale installed"
                ;;
            macos)
                brew install --cask tailscale
                success "Tailscale installed"
                ;;
            *)
                warn "Tailscale install not supported on this OS"
                ;;
        esac
    fi
else
    warn "Skipped Tailscale"
fi

# ═══════════════════════════════════════════════════════════
# Update .bashrc
# ═══════════════════════════════════════════════════════════
log "Updating ~/.bashrc..."

cp ~/.bashrc ~/.bashrc.bak 2>/dev/null || true

# Remove old block
sed -i '/# ⭐ WexowRAT/,/# ⭐ End WexowRAT/d' ~/.bashrc 2>/dev/null || true

# Add new block
cat >> ~/.bashrc << EOF

# ⭐ WexowRAT
export JAVA_HOME="$JAVA_21_HOME"
export ANDROID_HOME="$ANDROID_HOME"
export PATH="\$PATH:$ANDROID_HOME/platform-tools"
export PATH="\$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
export PATH="\$PATH:$ANDROID_HOME/build-tools/34.0.0"
# ⭐ End WexowRAT
EOF

success "~/.bashrc updated"

# ═══════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}   ✅ SETUP COMPLETE${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${CYAN}   Installed:${NC}"
echo "   • Node.js:      $(node --version 2>/dev/null || echo 'MISSING')"
echo "   • npm:          $(npm --version 2>/dev/null || echo 'MISSING')"
echo "   • Java 21:      $(java --version 2>/dev/null | head -1 || echo 'MISSING')"
echo "   • Java 11:      $(ls $JAVA_11_HOME/bin/java 2>/dev/null && echo 'OK' || echo 'MISSING')"
echo "   • apktool:      $(apktool --version 2>/dev/null || echo 'MISSING')"
echo "   • Android SDK:  $ANDROID_HOME"
echo "   • ADB:          $(adb --version 2>/dev/null | head -1 || echo 'MISSING')"
echo "   • Docker:       $(docker --version 2>/dev/null || echo 'NOT INSTALLED')"
echo "   • Tailscale:    $(tailscale --version 2>/dev/null | head -1 || echo 'NOT INSTALLED')"
echo ""
echo -e "${CYAN}   Next steps:${NC}"
echo "   1. source ~/.bashrc"
echo "   2. cd server && npm install"
echo "   3. cp .env.example .env && nano .env"
echo "   4. ./check-prereqs.sh"
echo ""