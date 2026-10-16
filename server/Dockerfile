# =====================================================
# WexowRAT C2 Server - Dockerfile (Debian 13 - Trixie)
# =====================================================
FROM debian:trixie-slim

# =====================================================
# 1. Installing Prerequisites
# =====================================================
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    wget \
    ca-certificates \
    gnupg \
    openjdk-21-jdk-headless \
    zipalign \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# =====================================================
# 2. Installing Node.js 18
# =====================================================
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# =====================================================
# Installing apktool 3.0.2 from the official release
# =====================================================
RUN wget -q https://github.com/iBotPeaches/Apktool/releases/download/v3.0.2/apktool_3.0.2.jar -O /opt/apktool.jar \
    && printf '#!/bin/sh\nexec java -jar /opt/apktool.jar "$@"\n' > /usr/local/bin/apktool \
    && chmod +x /usr/local/bin/apktool \
    && apktool --version

# =====================================================
# 4. Preparing the working folder
# =====================================================
WORKDIR /app

# =====================================================
# 5. Copying package.json files and installing libraries
# =====================================================
COPY package*.json ./
RUN npm install --production

# =====================================================
# 6. Copy the rest of the server code
# =====================================================
COPY . .

# =====================================================
# 7. Creating the output folder
# =====================================================
RUN mkdir -p /app/output

# =====================================================
# 8. Opening the port
# =====================================================
EXPOSE 3000

# =====================================================
# 9. Running the server
# =====================================================
CMD ["node", "server.js"]
