#!/bin/sh
# Minimal Maven distribution launcher. No preinstalled Maven required.
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.9
CACHE="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists/ledger-guard-$MAVEN_VERSION"
MAVEN_HOME="$CACHE/apache-maven-$MAVEN_VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  command -v curl >/dev/null || { echo 'curl is required' >&2; exit 1; }
  command -v unzip >/dev/null || { echo 'unzip is required' >&2; exit 1; }
  command -v sha512sum >/dev/null || { echo 'sha512sum is required' >&2; exit 1; }
  URL=$(sed -n 's/^distributionUrl=//p' "$BASE_DIR/.mvn/wrapper/maven-wrapper.properties")
  mkdir -p "$CACHE"
  TEMP_DIR=$(mktemp -d "$CACHE/download.XXXXXX")
  trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM
  curl --fail --location --connect-timeout 15 --max-time 180 "$URL" -o "$TEMP_DIR/maven.zip"
  curl --fail --location --connect-timeout 15 --max-time 30 "$URL.sha512" -o "$TEMP_DIR/checksum"
  HASH=$(awk '{print $1}' "$TEMP_DIR/checksum")
  (cd "$TEMP_DIR" && printf '%s  maven.zip\n' "$HASH" | sha512sum -c -)
  unzip -q "$TEMP_DIR/maven.zip" -d "$TEMP_DIR"
  mv "$TEMP_DIR/apache-maven-$MAVEN_VERSION" "$MAVEN_HOME"
fi
exec "$MAVEN_HOME/bin/mvn" -f "$BASE_DIR/pom.xml" "$@"
