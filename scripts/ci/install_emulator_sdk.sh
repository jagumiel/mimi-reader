#!/usr/bin/env bash
set -euo pipefail

api="${1:?Expected an Android API level}"
[[ "$api" =~ ^(29|31|34|36)$ ]] || { echo "Unsupported CI API: $api" >&2; exit 2; }
image="system-images;android-${api};google_apis;x86_64"

# Retry SDK preparation only, never failed application tests. A truncated image
# archive previously prevented API 34 from even launching its emulator.
for attempt in 1 2 3; do
  if sdkmanager --install "platforms;android-36" "build-tools;36.0.0" emulator "$image"; then
    exit 0
  fi
  if [[ "$attempt" == 3 ]]; then
    echo "Android SDK installation failed after $attempt attempts: $image" >&2
    exit 1
  fi
  echo "Retrying SDK image installation ($attempt/3): $image" >&2
  sdkmanager --uninstall "$image" || true
  sleep 5
done
