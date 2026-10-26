"""Prepare a pinned, CI-only AOSP WebView for the API 29 Google APIs emulator.

Never use this historical test provider on a personal device. Newer APIs retain
their image's provider; only the API 29 image needs its unsupported JS engine replaced.
"""
import argparse
import base64
from contextlib import contextmanager
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request


REVISION = "27ac2bdb9a1a4e55dc65bb6cdc752dbfdea3b9f4"
VERSION = "128.0.6613.88"
URL = ("https://android.googlesource.com/platform/external/chromium-webview/+/"
       f"{REVISION}/{VERSION}/x86_64/webview.apk?format=TEXT")
SHA256 = "deb1987501d40c4c326697be8fdd7733abdf2e0ab7fb6e028205b0ff786876e5"
PACKAGE = "com.android.webview"
TEST_CLASS = "com.emogoth.android.phone.mimi.view.gallery.GalleryPlaybackTest"
FLAG_FILE = "/data/local/tmp/webview-command-line"
PINNED_FLAGS = "_ --disable-features=MojoUseBinder --webview-verbose-logging\n"
TEST_APP = "io.github.jagumiel.mimireader.beta"


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
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    subprocess.run([wrapper, "--no-daemon", "--stacktrace",
                    ":mimi-app:connectedDebugAndroidTest", *args], check=True,
                   env=dict(os.environ, ANDROID_SERIAL=serial))


@contextmanager
def pinned_runtime(serial):
    """Keep the historical test provider off its experimental, broken Q Binder path.

    Never disable fdsan, sandboxing, rendering or assertions. Refuse existing
    developer flags instead of overwriting them. This is only for the verified
    API 29 disposable emulator with the pinned provider, never a user's phone.
    """
    if not re.fullmatch(r"emulator-\d+", serial):
        raise RuntimeError("Pinned WebView flags are allowed only on an emulator")
    for prop, expected in (("ro.kernel.qemu", "1"), ("ro.build.version.sdk", "29"),
                           ("ro.debuggable", "1")):
        if adb(serial, "shell", "getprop", prop) != expected:
            raise RuntimeError("Pinned flags require a debuggable API 29 emulator")
    provider = adb(serial, "shell", "dumpsys", "webviewupdate")
    active = (rf"^[ \t]*Current WebView package \(name, version\):[ \t]*"
              rf"\({re.escape(PACKAGE)},[ \t]*{re.escape(VERSION)}\)[ \t]*\r?$")
    if not re.search(active, provider, re.MULTILINE):
        raise RuntimeError("Refusing flags for a provider other than the pinned WebView")
    try:
        adb(serial, "shell", "test", "-e", FLAG_FILE)
    except subprocess.CalledProcessError as error:
        if error.returncode != 1:
            raise
    else:
        raise RuntimeError("Refusing to overwrite existing WebView command-line flags")

    with tempfile.TemporaryDirectory(prefix="ci-webview-flags-") as directory:
        flags = Path(directory) / "webview-command-line"
        flags.write_bytes(PINNED_FLAGS.encode("ascii"))
        try:
            adb(serial, "push", str(flags), FLAG_FILE)
            adb(serial, "shell", "chmod", "644", FLAG_FILE)
            if adb(serial, "shell", "cat", FLAG_FILE) != PINNED_FLAGS.strip():
                raise RuntimeError("Pinned WebView command-line verification failed")
            adb(serial, "shell", "am", "force-stop", TEST_APP)
            yield
        finally:
            try:
                adb(serial, "shell", "am", "force-stop", TEST_APP)
            finally:
                adb(serial, "shell", "rm", "-f", FLAG_FILE)


def verify_pinned_command_line(serial):
    command_line = adb(serial, "logcat", "-d", "-s", "chromium:V",
                       "cr_WebViewCommandLine:V", "WebViewCommandLine:V")
    disabled = re.findall(r"WebViewCommandLine 'disable-features': '([^']*)'", command_line)
    if not any("MojoUseBinder" in entry.split(",") for entry in disabled):
        raise RuntimeError("The pinned WebView did not report its required runtime flags")


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
    # Android 10 prints only "Success"; newer releases can use a longer message.
    # adb() already rejects nonzero exits. Verify actual state, not wording or an
    # eligible-provider listing elsewhere in the service dump.
    provider = adb(serial, "shell", "dumpsys", "webviewupdate")
    active = (rf"^[ \t]*Current WebView package \(name, version\):[ \t]*"
              rf"\({re.escape(PACKAGE)},[ \t]*{re.escape(VERSION)}\)[ \t]*\r?$")
    if not re.search(active, provider, re.MULTILINE):
        raise RuntimeError(f"The active WebView does not match the pinned test version (selection response: {selected!r})")
    with pinned_runtime(serial):
        # Verify native startup before the long suite can rotate the logcat buffer.
        gradle(f"-Pandroid.testInstrumentationRunnerArguments.class={TEST_CLASS}#flashStartupErrorsAreReportedWithoutWaitingForTheDeadline",
               serial=serial)
        verify_pinned_command_line(serial)
        shutil.copytree(results, "build/reports/legacy-webview/pinned-runtime", dirs_exist_ok=True)
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
