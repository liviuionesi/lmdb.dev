#!/bin/bash

# Filmpire Microservices - Environment Setup Script
# This script verifies and installs the required versions

set -e

echo "========================================="
echo "Filmpire Microservices Environment Setup"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Required versions
REQUIRED_JAVA_VERSION=25
REQUIRED_GRADLE_VERSION="9.2.0"
REQUIRED_NODE_VERSION="24.11.1"
REQUIRED_NPM_VERSION="11.6.2"

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check Java
echo "Checking Java version..."
if command_exists java; then
    CURRENT_JAVA=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$CURRENT_JAVA" == "$REQUIRED_JAVA_VERSION" ]; then
        echo -e "${GREEN}✓${NC} Java $REQUIRED_JAVA_VERSION is installed"
    else
        echo -e "${YELLOW}!${NC} Java version mismatch: found $CURRENT_JAVA, required $REQUIRED_JAVA_VERSION"
        echo "  Install Java 25 with: sdk install java 25-open"
    fi
else
    echo -e "${RED}✗${NC} Java is not installed"
    echo "  Install SDKMAN: curl -s 'https://get.sdkman.io' | bash"
    echo "  Then install Java: sdk install java 25-open"
fi

# Check Gradle
echo "Checking Gradle version..."
if command_exists gradle; then
    CURRENT_GRADLE=$(gradle -version 2>&1 | grep "Gradle" | head -1 | awk '{print $2}')
    if [ "$CURRENT_GRADLE" == "$REQUIRED_GRADLE_VERSION" ]; then
        echo -e "${GREEN}✓${NC} Gradle $REQUIRED_GRADLE_VERSION is installed"
    else
        echo -e "${YELLOW}!${NC} Gradle version mismatch: found $CURRENT_GRADLE, required $REQUIRED_GRADLE_VERSION"
        echo "  Install Gradle with: sdk install gradle 9.2.0"
    fi
else
    echo -e "${RED}✗${NC} Gradle is not installed"
    echo "  Install with SDKMAN: sdk install gradle 9.2.0"
fi

# Check Node.js
echo "Checking Node.js version..."
if command_exists node; then
    CURRENT_NODE=$(node -v | sed 's/v//')
    if [ "$CURRENT_NODE" == "$REQUIRED_NODE_VERSION" ]; then
        echo -e "${GREEN}✓${NC} Node.js $REQUIRED_NODE_VERSION is installed"
    else
        echo -e "${YELLOW}!${NC} Node.js version mismatch: found $CURRENT_NODE, required $REQUIRED_NODE_VERSION"
        echo "  Install with NVM: nvm install 24.11.1 && nvm use 24.11.1 && nvm alias default 24.11.1"
    fi
else
    echo -e "${RED}✗${NC} Node.js is not installed"
    echo "  Install NVM: curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash"
    echo "  Then install Node: nvm install 24.11.1"
fi

# Check npm
echo "Checking npm version..."
if command_exists npm; then
    CURRENT_NPM=$(npm -v)
    echo -e "${GREEN}✓${NC} npm $CURRENT_NPM is installed (required: $REQUIRED_NPM_VERSION)"
else
    echo -e "${RED}✗${NC} npm is not installed (comes with Node.js)"
fi

# Check Docker/Podman
echo "Checking container runtime..."
if command_exists podman; then
    echo -e "${GREEN}✓${NC} Podman is installed"
elif command_exists docker; then
    echo -e "${GREEN}✓${NC} Docker is installed"
else
    echo -e "${RED}✗${NC} Neither Docker nor Podman is installed"
    echo "  Install Podman: sudo dnf install podman podman-compose"
fi

echo ""
echo "========================================="
echo "Setup Summary"
echo "========================================="
echo ""
echo "Required versions:"
echo "  - Java 25"
echo "  - Gradle 9.2.0"
echo "  - Node.js 24.11.1 LTS"
echo "  - npm 11.6.2"
echo "  - PostgreSQL 18 (via Docker/Podman)"
echo "  - MongoDB 8.2 (via Docker/Podman)"
echo "  - Redis 8.2 (via Docker/Podman)"
echo ""
echo "Next steps:"
echo "1. Install any missing tools from above"
echo "2. Start infrastructure: cd infrastructure/docker && docker-compose up -d"
echo "3. Build backend: ./gradlew clean build"
echo "4. Install frontend deps: cd frontend/web-nextjs && npm install"
echo ""


