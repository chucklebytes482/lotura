#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ "${1:-}" == --help ]]; then
  echo 'Usage: bash configure_github_secrets.sh private-key.jks [owner/repo]'
  echo 'Requires gh auth; uploads four signing secrets without printing their values.'
  exit 0
fi
FILE="${1:?Supply private keystore path}"
REPO="${2:-chucklebytes482/lotura}"
command -v gh >/dev/null || { echo 'Install GitHub CLI and run gh auth login' >&2; exit 1; }
[[ -f "$FILE" ]] || { echo 'Keystore not found' >&2; exit 1; }
if [[ -z "${STORE_PASSWORD:-}" ]]; then
  read -r -s -p 'Keystore password: ' STORE_PASSWORD; echo
fi
export STORE_PASSWORD
LC_ALL=C keytool -list -keystore "$FILE" -alias upload -storepass:env STORE_PASSWORD >/dev/null
base64 < "$FILE" | tr -d '\r\n' | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$STORE_PASSWORD" | gh secret set STORE_PASSWORD --repo "$REPO"
printf '%s' "$STORE_PASSWORD" | gh secret set KEY_PASSWORD --repo "$REPO"
printf '%s' upload | gh secret set KEY_ALIAS --repo "$REPO"
gh secret list --repo "$REPO"
echo 'Only secret names can be checked; values cannot be read back. Never run with bash -x.'