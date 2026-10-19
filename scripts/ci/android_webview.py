"""Prepare a pinned, CI-only AOSP WebView for the API 29 Google APIs emulator.

Never use this historical test provider on a personal device. Newer APIs retain
their image's provider; only the API 29 image needs its unsupported JS engine replaced.
"""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request


REVISION = "27ac2bdb9a1a4e55dc65bb6cdc752dbfdea3b9f4"
VERSION = "128.0.6613.88"
URL = ("https://android.googlesource.com/platform/external/chromium-webview/+/"
       f"{REVISION}/{VERSION}/x86_64/webview.apk?format=TEXT")
SHA256 = "deb1987501d40c4c326697be8fdd7733abdf2e0ab7fb6e028205b0ff786876e5"
PACKAGE = "com.android.webview"
TEST_CLASS = "com.emogoth.android.phone.mimi.view.gallery.GalleryPlaybackTest"


def verify(apk):
    with apk.open("rb") as stream:
        hasher = hashlib.sha256()
        while chunk := stream.read(1024 * 1024):
            hasher.update(chunk)
        digest = hasher.hexdigest()
    if digest != SHA256:
        raise RuntimeError(f"CI WebView SHA-256 mismatch: {digest}")


def download(apk):
    for attempt in range(3):
        try:
            download_once(apk)
            return
        except (OSError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(5)


def download_once(apk):
    apk.parent.mkdir(parents=True, exist_ok=True)
    if apk.is_file():
        verify(apk)
        return
    temporary = apk.with_suffix(".part")
    try:
        # Gitiles returns base64. Decode complete quads without keeping the APK
        # and its base64 representation in memory at the same time.
        with urllib.request.urlopen(URL, timeout=120) as response, temporary.open("wb") as output:
            pending = b""
            while chunk := response.read(1024 * 1024):
                pending += chunk
                count = len(pending) // 4 * 4
                output.write(base64.b64decode(pending[:count], validate=True))
                pending = pending[count:]
            if pending:
                raise RuntimeError("Incomplete base64 WebView download")
        verify(temporary)
        temporary.replace(apk)
    finally:
        temporary.unlink(missing_ok=True)


def adb(serial, *args):
    result = subprocess.run(["adb", "-s", serial, *args], check=True,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, flush=True)
    return result.stdout.strip()


def validate_emulator(serial, api=29):
    if not re.fullmatch(r"emulator-\d+", serial):
        raise RuntimeError("CI WebView installation is allowed only on an emulator serial")
    if adb(serial, "shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Refusing to modify a non-emulator device")
    if adb(serial, "shell", "getprop", "ro.build.version.sdk") != str(api):
        raise RuntimeError(f"The selected emulator must be API {api}; the pinned provider is only for the API 29 CI image")
    if adb(serial, "shell", "getprop", "ro.debuggable") != "1":
        raise RuntimeError("A debuggable Google APIs image is required")
    if api == 29 and f"package:{PACKAGE}" in adb(serial, "shell", "pm", "list", "packages", PACKAGE).splitlines():
        raise RuntimeError("Use the Google APIs image, not an AOSP image with a conflicting provider")


def gradle(*args, serial):
    subprocess.run(["./gradlew", "--no-daemon", "--stacktrace",
                    ":mimi-app:connectedFreeDebugAndroidTest", *args], check=True,
                   env=dict(os.environ, ANDROID_SERIAL=serial))


def run_tests(apk, serial, api=29):
    if api not in (29, 31, 34, 36):
        raise RuntimeError(f"Unsupported CI API: {api}")
    validate_emulator(serial, api)
    if api != 29:
        adb(serial, "shell", "dumpsys", "webviewupdate")
        gradle(serial=serial)
        return
    # Verify bytes and image safety before running any installation commands.
    verify(apk)
    gradle(f"-Pandroid.testInstrumentationRunnerArguments.class={TEST_CLASS}#originalWebViewHandlesFlashOrExplainsItsRequirement",
           "-Pandroid.testInstrumentationRunnerArguments.legacyFlashWebView=true", serial=serial)
    # Preserve the original-provider assertion report: the full suite reuses AGP's directory.
    import shutil
    results = Path("mimi-app/build/outputs/androidTest-results/connected")
    shutil.copytree(results, "build/reports/legacy-webview", dirs_exist_ok=True)
    installed = adb(serial, "install", "-r", str(apk))
    if "Success" not in installed:
        raise RuntimeError("CI WebView installation failed")
    selected = adb(serial, "shell", "cmd", "webviewupdate", "set-webview-implementation", PACKAGE)
    if f"Success. Current WebView package is {PACKAGE}" not in selected:
        raise RuntimeError(f"The pinned WebView was not selected: {selected}")
    provider = adb(serial, "shell", "dumpsys", "webviewupdate")
    if f"({PACKAGE}, {VERSION})" not in provider:
        raise RuntimeError("The active WebView does not match the pinned test version")
    gradle(serial=serial)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["download", "test"])
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--api", type=int, choices=[29, 31, 34, 36], default=29)
    args = parser.parse_args()
    if args.operation == "download":
        download(args.apk)
    else:
        run_tests(args.apk, args.serial, args.api)


if __name__ == "__main__":
    main()
