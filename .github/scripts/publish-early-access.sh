#!/usr/bin/env bash
set -euo pipefail

for variable in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY R2_ACCOUNT_ID R2_BUCKET APK_PATH APK_NAME GITHUB_REF_NAME GITHUB_SHA; do
  if [[ -z "${!variable:-}" ]]; then
    printf 'Required environment variable not configured: %s\n' "$variable" >&2
    exit 1
  fi
done

for executable in aws sha256sum; do
  if ! command -v "$executable" >/dev/null 2>&1; then
    printf 'Required executable unavailable: %s\n' "$executable" >&2
    exit 1
  fi
done

case "$APK_NAME" in
  app-gms-mobile-arm64-release.apk|app-gms-mobile-armeabi-release.apk|app-gms-mobile-x86-release.apk|app-gms-mobile-x86_64-release.apk|app-gms-mobile-universal-release.apk|app-gms-tv-universal-release.apk|app-foss-mobile-universal-release.apk) ;;
  *) echo 'APK_NAME is not an approved Release variant' >&2; exit 1 ;;
esac

case "$GITHUB_REF_NAME" in
  main|dev) ;;
  *) echo 'Only main and dev builds can be distributed' >&2; exit 1 ;;
esac

if [[ ! "$GITHUB_SHA" =~ ^[a-f0-9]{40}$ ]]; then
  echo 'Invalid Git commit hash' >&2
  exit 1
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo 'Signed Release APK was not found' >&2
  exit 1
fi

sha256="$(sha256sum "$APK_PATH" | awk '{print $1}')"
object_key="early-access/${GITHUB_REF_NAME}/${GITHUB_SHA}/${APK_NAME}"
endpoint="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"

aws s3 cp "$APK_PATH" "s3://${R2_BUCKET}/${object_key}" \
  --endpoint-url "$endpoint" \
  --content-type 'application/vnd.android.package-archive' \
  --content-disposition "attachment; filename=\"${APK_NAME}\"" \
  --metadata "sha256=${sha256}" \
  --only-show-errors \
  --no-progress

printf 'Published %s to private R2 storage.\n' "$APK_NAME"
