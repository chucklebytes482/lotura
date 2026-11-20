#!/usr/bin/env bash
set -euo pipefail
umask 077
PROJECT_ID="${GCP_PROJECT_ID:-gen-lang-client-0224561583}"
if [[ "${1:-}" == --help ]]; then
  echo 'Usage: bash setup_google_cloud.sh [--enable-api] [keystore.jks]'
  echo 'Existing key only. Enable mode requires authenticated gcloud.'
  exit 0
fi
if [[ "${1:-}" == --enable-api ]]; then
  command -v gcloud >/dev/null || { echo 'Install gcloud and run gcloud auth login' >&2; exit 1; }
  gcloud services enable drive.googleapis.com --project="$PROJECT_ID"
  shift
fi
FILE="${1:-${HOME}/.lotura/release-upload.jks}"
[[ -f "$FILE" ]] || { echo 'Keystore missing; create it once using create_keystore.sh' >&2; exit 1; }
if [[ -z "${STORE_PASSWORD:-}" ]]; then
  read -r -s -p 'Keystore password: ' STORE_PASSWORD; echo
fi
export STORE_PASSWORD
LC_ALL=C keytool -list -v -keystore "$FILE" -alias upload -storepass:env STORE_PASSWORD
echo "Drive API: https://console.cloud.google.com/apis/library/drive.googleapis.com?project=$PROJECT_ID"
echo "Test users: https://console.cloud.google.com/auth/audience?project=$PROJECT_ID"
echo "OAuth Android: https://console.cloud.google.com/apis/credentials/oauthclient?project=$PROJECT_ID"
echo 'Register package com.aistudio.lotura + release SHA-1 above. This does not configure test users/OAuth automatically.'