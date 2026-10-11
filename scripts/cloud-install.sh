#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Cloud-only bootstrap for Linux x86_64; installation stays outside the checkout.
source scripts/cloud-env.sh
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  [[ "$(uname -m)" == x86_64 ]] || { echo 'This bootstrap requires x86_64' >&2; exit 1; }
  mkdir -p /workspace/toolchains
  archive=$(mktemp /tmp/mtg-jdk.XXXXXX.tar.gz)
  trap 'rm -f "$archive"' EXIT
  curl -fsSL 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' -o "$archive"
  printf '%s  %s\n' 'f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219' "$archive" | sha256sum -c -
  tar -xzf "$archive" -C /workspace/toolchains
fi
"$JAVA_HOME/bin/javac" -version
python3 scripts/cloud-prepare.py
docker pull mysql:8.4.8
./mvnw -s /workspace/.m2/settings.xml -Dmaven.repo.local=/workspace/.m2/repository -B verify
