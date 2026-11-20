#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:-gen-lang-client-0224561583}"
KEYSTORE_FILE="${KEYSTORE_FILE:-release-upload.jks}"
KEY_ALIAS="${KEY_ALIAS:-upload}"

echo "==> 1. Mengaktifkan Google Drive API di proyek: ${PROJECT_ID}"
if command -v gcloud >/dev/null 2>&1; then
  gcloud config set project "${PROJECT_ID}" >/dev/null
  gcloud services enable drive.googleapis.com --project="${PROJECT_ID}"
  echo "    [OK] drive.googleapis.com aktif."
else
  echo "    [INFO] gcloud CLI tidak ditemukan."
  echo "    Aktifkan lewat browser: https://console.cloud.google.com/apis/library/drive.googleapis.com?project=${PROJECT_ID}"
fi

echo ""
echo "==> 2. Memeriksa keystore release: ${KEYSTORE_FILE}"
if [ ! -f "${KEYSTORE_FILE}" ]; then
  echo "    Keystore belum ada. Membuat keystore baru (${KEYSTORE_FILE})..."
  keytool -genkeypair -v \
    -keystore "${KEYSTORE_FILE}" \
    -alias "${KEY_ALIAS}" \
    -keyalg RSA -keysize 2048 -validity 10000
fi

echo ""
echo "==> 3. Sidik Jari SHA-1 & SHA-256 (untuk OAuth Client Android com.aistudio.lotura)"
keytool -list -v -keystore "${KEYSTORE_FILE}" -alias "${KEY_ALIAS}" | grep -E "Alias name:|SHA1:|SHA256:"

echo ""
echo "==> 4. Encode Base64 untuk GitHub Secret RELEASE_KEYSTORE_BASE64"
if base64 -w 0 "${KEYSTORE_FILE}" >/dev/null 2>&1; then
  base64 -w 0 "${KEYSTORE_FILE}" > "${KEYSTORE_FILE}.b64"
else
  base64 "${KEYSTORE_FILE}" | tr -d '\n' > "${KEYSTORE_FILE}.b64"
fi
echo "    Tersimpan di ${KEYSTORE_FILE}.b64 (jangan commit ke Git; tempel isinya ke GitHub Secret RELEASE_KEYSTORE_BASE64)."
echo "    Daftarkan SHA-1 Release di: https://console.cloud.google.com/apis/credentials/oauthclient?project=${PROJECT_ID}"
