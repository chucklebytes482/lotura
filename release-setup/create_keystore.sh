#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ "${1:-}" == --help ]]; then
  echo 'Usage: bash create_keystore.sh [private-path.jks]; alias upload; STORE_PASSWORD optional'
  exit 0
fi
FILE="${1:-${HOME}/.lotura/release-upload.jks}"
command -v keytool >/dev/null || { echo 'Install JDK 21 (keytool required)' >&2; exit 1; }
[[ ! -e "$FILE" ]] || { echo 'Refusing to overwrite existing keystore' >&2; exit 1; }
if [[ -z "${STORE_PASSWORD:-}" ]]; then
  read -r -s -p 'Keystore password (minimum 16 characters): ' STORE_PASSWORD; echo
  read -r -s -p 'Repeat password: ' CONFIRM; echo
  [[ "$STORE_PASSWORD" == "$CONFIRM" ]] || { echo 'Passwords differ' >&2; exit 1; }
fi
[[ ${#STORE_PASSWORD} -ge 16 ]] || { echo 'Password too short' >&2; exit 1; }
export STORE_PASSWORD
mkdir -p "$(dirname "$FILE")"
keytool -genkeypair -storetype JKS -keystore "$FILE" -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass:env STORE_PASSWORD -keypass:env STORE_PASSWORD \
  -dname 'CN=Lotura, OU=Mobile, O=Lotura, L=Jakarta, ST=Jakarta, C=ID'
chmod 600 "$FILE"
echo "Created: $FILE. Back up this permanent key; never commit it."