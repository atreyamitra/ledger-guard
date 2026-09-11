#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -d "$OUT" "$ROOT/src/main/java/com/atreyamitra/ledgerguard/crypto/WebhookCrypto.java" "$ROOT/scripts/OfflineCryptoCheck.java"
java -cp "$OUT" OfflineCryptoCheck
