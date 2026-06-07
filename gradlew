#!/usr/bin/env sh
set -e
GRADLE_VERSION=9.2.0
WRAPPER_DIR="$(dirname "$0")/.gradle/wrapper"
GRADLE_HOME="$WRAPPER_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Gradle $GRADLE_VERSION directly..."
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$WRAPPER_DIR/gradle-$GRADLE_VERSION-bin.zip"
  else
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$WRAPPER_DIR/gradle-$GRADLE_VERSION-bin.zip"
  fi
  unzip -o "$WRAPPER_DIR/gradle-$GRADLE_VERSION-bin.zip" -d "$WRAPPER_DIR"
fi
exec "$GRADLE_BIN" "$@"
