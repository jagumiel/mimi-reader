import base64
from contextlib import nullcontext
import hashlib
import io
import re
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import android_webview as helper


class WebViewPreparationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / "webview.apk"
        self.content = b"a reviewed test APK"
        digest = hashlib.sha256(self.content).hexdigest()
        self.hash_patch = patch.object(helper, "SHA256", digest)
        self.hash_patch.start()
        self.addCleanup(self.hash_patch.stop)

    def test_download_decodes_and_checks_before_publish(self):
        with patch.object(helper.urllib.request, "urlopen",
                          return_value=io.BytesIO(base64.b64encode(self.content))):
            helper.download(self.apk)
        self.assertEqual(self.content, self.apk.read_bytes())
        self.assertFalse(self.apk.with_suffix(".part").exists())

    def test_wrong_hash_never_installs_or_publishes(self):
        with patch.object(helper.urllib.request, "urlopen",
                          return_value=io.BytesIO(base64.b64encode(b"tampered"))):
            with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
                helper.download(self.apk)
        self.assertFalse(self.apk.exists())
        self.assertFalse(self.apk.with_suffix(".part").exists())

    def test_incomplete_download_is_rejected(self):
        with patch.object(helper.urllib.request, "urlopen", return_value=io.BytesIO(b"UEs")):
            with self.assertRaisesRegex(RuntimeError, "Incomplete base64"):
                helper.download(self.apk)
        self.assertFalse(self.apk.exists())

    def test_cached_bytes_are_verified_without_network(self):
        self.apk.write_bytes(self.content)
        with patch.object(helper.urllib.request, "urlopen") as request:
            helper.download(self.apk)
        request.assert_not_called()

    def test_transient_network_failure_is_retried(self):
        with patch.object(helper.urllib.request, "urlopen",
                          side_effect=[OSError("interrupted"), io.BytesIO(base64.b64encode(self.content))]) as request, \
                patch.object(helper.time, "sleep"):
            helper.download(self.apk)
        self.assertEqual(2, request.call_count)
        self.assertEqual(self.content, self.apk.read_bytes())

    def test_network_failure_is_not_hidden_after_third_attempt(self):
        with patch.object(helper.urllib.request, "urlopen", side_effect=OSError("offline")) as request, \
                patch.object(helper.time, "sleep"):
            with self.assertRaisesRegex(OSError, "offline"):
                helper.download(self.apk)
        self.assertEqual(3, request.call_count)
        self.assertFalse(self.apk.with_suffix(".part").exists())

    def test_corrupt_cache_is_not_accepted(self):
        self.apk.write_bytes(b"tampered")
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            helper.download(self.apk)

    def test_physical_serial_is_refused_before_adb(self):
        with patch.object(helper, "adb") as adb:
            with self.assertRaisesRegex(RuntimeError, "only on an emulator"):
                helper.validate_emulator("ZY22G33JMT")
        adb.assert_not_called()

    def test_device_identity_and_api_are_verified(self):
        for responses, message in [(["0"], "non-emulator"),
                                   (["1", "31"], "only for the API 29"),
                                   (["1", "29", "0"], "debuggable"),
                                   (["1", "29", "1", "package:com.android.webview"], "conflicting provider")]:
            with self.subTest(responses=responses), patch.object(helper, "adb", side_effect=responses):
                with self.assertRaisesRegex(RuntimeError, message):
                    helper.validate_emulator("emulator-5554")

    def test_legacy_failure_prevents_provider_installation_and_full_suite(self):
        self.apk.write_bytes(self.content)
        with patch.object(helper, "validate_emulator"), patch.object(helper, "adb") as adb, \
                patch.object(helper, "gradle", side_effect=subprocess.CalledProcessError(1, "gradle")):
            with self.assertRaises(subprocess.CalledProcessError):
                helper.run_tests(self.apk, "emulator-5554")
        adb.assert_not_called()

    def test_wrong_provider_is_not_treated_as_playback_success(self):
        self.apk.write_bytes(self.content)
        with patch.object(helper, "validate_emulator"), patch.object(helper, "gradle") as gradle, \
                patch("shutil.copytree"), patch.object(helper, "adb", side_effect=[
                    "Success", "Failed", "Current WebView package (name, version): (com.google.android.webview, 74.0.0.0)"]):
            with self.assertRaisesRegex(RuntimeError, "does not match"):
                helper.run_tests(self.apk, "emulator-5554")
        self.assertEqual(1, gradle.call_count)

    def test_full_playback_suite_runs_only_after_exact_provider_confirmation(self):
        self.apk.write_bytes(self.content)
        responses = ["Success", f"Success. Current WebView package is {helper.PACKAGE}",
                     f"Current WebView package (name, version): ({helper.PACKAGE}, {helper.VERSION})"]
        with patch.object(helper, "validate_emulator"), patch.object(helper, "gradle") as gradle, \
                patch("shutil.copytree"), patch.object(helper, "adb", side_effect=responses), \
                patch.object(helper, "pinned_runtime", return_value=nullcontext()) as runtime, \
                patch.object(helper, "verify_pinned_command_line") as verify_runtime:
            helper.run_tests(self.apk, "emulator-5554")
        runtime.assert_called_once_with("emulator-5554")
        verify_runtime.assert_called_once_with("emulator-5554")
        self.assertEqual(3, gradle.call_count)
        self.assertIn("originalWebViewHandlesFlashOrExplainsItsRequirement", gradle.call_args_list[0].args[0])
        self.assertIn("flashStartupErrorsAreReportedWithoutWaitingForTheDeadline", gradle.call_args_list[1].args[0])
        self.assertEqual((), gradle.call_args_list[2].args)
        self.assertEqual({"serial": "emulator-5554"}, gradle.call_args_list[2].kwargs)

    def test_android_10_short_success_response_allows_verified_playback(self):
        # Actual API 29 output in workflow run 14: the command prints only Success.
        self.apk.write_bytes(self.content)
        responses = ["Performing Streamed Install\nSuccess", "Success",
                     f"Current WebView Update Service state\n  Current WebView package (name, version): ({helper.PACKAGE}, {helper.VERSION})\n"]
        with patch.object(helper, "validate_emulator"), patch.object(helper, "gradle") as gradle, \
                patch("shutil.copytree"), patch.object(helper, "adb", side_effect=responses), \
                patch.object(helper, "pinned_runtime", return_value=nullcontext()), \
                patch.object(helper, "verify_pinned_command_line"):
            helper.run_tests(self.apk, "emulator-5554")
        self.assertEqual(3, gradle.call_count)
        self.assertEqual((), gradle.call_args_list[2].args)

    def test_success_text_does_not_bypass_active_provider_verification(self):
        self.apk.write_bytes(self.content)
        for current in (f"Current WebView package (name, version): ({helper.PACKAGE}, 127.0.0.0)",
                        "Current WebView package (name, version): (com.google.android.webview, 128.0.6613.88)",
                        "Current WebView package is null"):
            with self.subTest(current=current), patch.object(helper, "validate_emulator"), \
                    patch.object(helper, "gradle") as gradle, patch("shutil.copytree"), \
                    patch.object(helper, "adb", side_effect=["Success", "Success", current]):
                with self.assertRaisesRegex(RuntimeError, "does not match"):
                    helper.run_tests(self.apk, "emulator-5554")
                self.assertEqual(1, gradle.call_count)

    def test_pinned_startup_failure_prevents_full_suite_without_retry(self):
        self.apk.write_bytes(self.content)
        responses = ["Success", "Success",
                     f"Current WebView package (name, version): ({helper.PACKAGE}, {helper.VERSION})"]
        with patch.object(helper, "validate_emulator"), patch.object(helper, "adb", side_effect=responses), \
                patch("shutil.copytree"), patch.object(helper, "pinned_runtime", return_value=nullcontext()), \
                patch.object(helper, "verify_pinned_command_line") as verify_runtime, \
                patch.object(helper, "gradle", side_effect=[None, subprocess.CalledProcessError(1, "startup")]) as gradle:
            with self.assertRaises(subprocess.CalledProcessError):
                helper.run_tests(self.apk, "emulator-5554")
            self.assertEqual(2, gradle.call_count)
            verify_runtime.assert_not_called()

    def test_missing_native_flag_proof_prevents_the_full_suite(self):
        self.apk.write_bytes(self.content)
        responses = ["Success", "Success",
                     f"Current WebView package (name, version): ({helper.PACKAGE}, {helper.VERSION})"]
        with patch.object(helper, "validate_emulator"), patch.object(helper, "adb", side_effect=responses), \
                patch("shutil.copytree"), patch.object(helper, "pinned_runtime", return_value=nullcontext()), \
                patch.object(helper, "verify_pinned_command_line", side_effect=RuntimeError("no native proof")), \
                patch.object(helper, "gradle") as gradle:
            with self.assertRaisesRegex(RuntimeError, "no native proof"):
                helper.run_tests(self.apk, "emulator-5554")
            self.assertEqual(2, gradle.call_count)

    def test_eligible_provider_listing_is_not_active_provider_confirmation(self):
        self.apk.write_bytes(self.content)
        # The expected tuple elsewhere in the dump must not override its current-provider line.
        current = ("Current WebView package (name, version): (com.google.android.webview, 74.0.0.0)\n"
                   f"  Eligible provider: ({helper.PACKAGE}, {helper.VERSION})")
        responses = ["Success", f"Success. Current WebView package is {helper.PACKAGE}", current]
        with patch.object(helper, "validate_emulator"), patch.object(helper, "gradle") as gradle, \
                patch("shutil.copytree"), patch.object(helper, "adb", side_effect=responses):
            with self.assertRaisesRegex(RuntimeError, "does not match"):
                helper.run_tests(self.apk, "emulator-5554")
        self.assertEqual(1, gradle.call_count)

    def test_gradle_is_scoped_to_the_verified_emulator(self):
        with patch.object(helper.subprocess, "run") as run:
            helper.gradle(serial="emulator-5556")
        self.assertEqual("emulator-5556", run.call_args.kwargs["env"]["ANDROID_SERIAL"])
        self.assertTrue(run.call_args.kwargs["check"])
        wrapper = "gradlew.bat" if helper.os.name == "nt" else "./gradlew"
        self.assertEqual([wrapper, "--no-daemon", "--stacktrace",
                          ":mimi-app:connectedDebugAndroidTest"], run.call_args.args[0])

    def test_newer_apis_keep_their_provider_and_run_mandatory_playback(self):
        for api in (31, 34, 36):
            with self.subTest(api=api), patch.object(helper, "validate_emulator") as validate, \
                    patch.object(helper, "gradle") as gradle, patch.object(helper, "adb") as adb, \
                    patch.object(helper, "pinned_runtime") as runtime:
                helper.run_tests(self.apk, "emulator-5554", api=api)
            validate.assert_called_once_with("emulator-5554", api)
            adb.assert_called_once_with("emulator-5554", "shell", "dumpsys", "webviewupdate")
            gradle.assert_called_once_with(serial="emulator-5554")
            runtime.assert_not_called()

    def test_wrapper_selection_keeps_linux_and_windows_launchers_explicit(self):
        for platform, wrapper in (("nt", "gradlew.bat"), ("posix", "./gradlew")):
            with self.subTest(platform=platform), patch.object(helper.os, "name", platform), \
                    patch.object(helper.subprocess, "run") as run:
                helper.gradle(serial="emulator-5554")
                self.assertEqual(wrapper, run.call_args.args[0][0])


class PinnedRuntimeTest(unittest.TestCase):
    serial = "emulator-5554"

    def respond(self, serial, *args):
        self.assertEqual(self.serial, serial)
        if args[:2] == ("shell", "getprop"):
            return {"ro.kernel.qemu": "1", "ro.build.version.sdk": "29", "ro.debuggable": "1"}[args[2]]
        if args == ("shell", "dumpsys", "webviewupdate"):
            return f"Current WebView package (name, version): ({helper.PACKAGE}, {helper.VERSION})"
        if args[:3] == ("shell", "test", "-e"):
            raise subprocess.CalledProcessError(1, "test -e")
        if args[0] == "push":
            self.assertEqual(helper.PINNED_FLAGS.encode("ascii"), Path(args[1]).read_bytes())
            self.assertEqual(helper.FLAG_FILE, args[2])
        if args[:2] == ("shell", "cat"):
            return helper.PINNED_FLAGS.strip()
        if args[0] == "logcat":
            return "I chromium: WebViewCommandLine 'disable-features': 'MojoUseBinder,DefaultANGLEVulkan'"
        return ""

    def test_physical_serial_is_refused_without_adb(self):
        with patch.object(helper, "adb") as adb:
            with self.assertRaisesRegex(RuntimeError, "only on an emulator"):
                with helper.pinned_runtime("ZY22G33JMT"):
                    self.fail("Must not modify a phone")
        adb.assert_not_called()

    def test_wrong_image_identity_is_refused_before_mutation(self):
        for values in (["0"], ["1", "31"], ["1", "29", "0"]):
            with self.subTest(values=values), patch.object(helper, "adb", side_effect=values) as adb:
                with self.assertRaisesRegex(RuntimeError, "debuggable API 29"):
                    with helper.pinned_runtime(self.serial):
                        self.fail("Invalid image accepted")
                self.assertTrue(all(call.args[1:3] == ("shell", "getprop") for call in adb.call_args_list))

    def test_wrong_active_provider_is_refused_before_file_access(self):
        with patch.object(helper, "adb", side_effect=["1", "29", "1", "wrong provider"]) as adb:
            with self.assertRaisesRegex(RuntimeError, "other than the pinned"):
                with helper.pinned_runtime(self.serial):
                    self.fail("Wrong provider accepted")
            self.assertEqual(4, adb.call_count)

    def test_existing_developer_flags_are_never_overwritten_or_removed(self):
        def respond(serial, *args):
            return "" if args[:3] == ("shell", "test", "-e") else self.respond(serial, *args)
        with patch.object(helper, "adb", side_effect=respond) as adb:
            with self.assertRaisesRegex(RuntimeError, "overwrite existing"):
                with helper.pinned_runtime(self.serial):
                    self.fail("Existing flags overwritten")
            self.assertFalse(any(call.args[1] == "push" or "rm" in call.args for call in adb.call_args_list))

    def test_unexpected_file_probe_failure_is_not_treated_as_absence(self):
        def respond(serial, *args):
            if args[:3] == ("shell", "test", "-e"):
                raise subprocess.CalledProcessError(2, "adb")
            return self.respond(serial, *args)
        with patch.object(helper, "adb", side_effect=respond) as adb:
            with self.assertRaises(subprocess.CalledProcessError):
                with helper.pinned_runtime(self.serial):
                    self.fail("Broken probe accepted")
            self.assertFalse(any(call.args[1] == "push" for call in adb.call_args_list))

    def test_flags_and_native_startup_are_verified_then_the_owned_file_is_removed(self):
        with patch.object(helper, "adb", side_effect=self.respond) as adb:
            with helper.pinned_runtime(self.serial):
                helper.verify_pinned_command_line(self.serial)
            adb.assert_any_call(self.serial, "shell", "chmod", "644", helper.FLAG_FILE)
            self.assertEqual((self.serial, "shell", "rm", "-f", helper.FLAG_FILE), adb.call_args.args)
            self.assertEqual(2, sum(call.args[1:] == ("shell", "am", "force-stop", helper.TEST_APP)
                                    for call in adb.call_args_list))

    def test_failed_suite_is_not_retried_and_still_cleans_up(self):
        entered = 0
        with patch.object(helper, "adb", side_effect=self.respond) as adb:
            with self.assertRaisesRegex(RuntimeError, "fixture suite failure"):
                with helper.pinned_runtime(self.serial):
                    entered += 1
                    raise RuntimeError("fixture suite failure")
            self.assertEqual(1, entered)
            self.assertEqual((self.serial, "shell", "rm", "-f", helper.FLAG_FILE), adb.call_args.args)
            self.assertFalse(any(call.args[1] == "logcat" for call in adb.call_args_list))

    def test_written_flag_mismatch_prevents_the_suite_and_cleans_up(self):
        def respond(serial, *args):
            return "wrong flags" if args[:2] == ("shell", "cat") else self.respond(serial, *args)
        with patch.object(helper, "adb", side_effect=respond) as adb:
            with self.assertRaisesRegex(RuntimeError, "verification failed"):
                with helper.pinned_runtime(self.serial):
                    self.fail("Incorrect flags accepted")
            self.assertEqual((self.serial, "shell", "rm", "-f", helper.FLAG_FILE), adb.call_args.args)

    def test_silent_or_ignored_flags_do_not_certify_the_environment(self):
        def respond(serial, *args):
            return "" if args[0] == "logcat" else self.respond(serial, *args)
        with patch.object(helper, "adb", side_effect=respond) as adb:
            with self.assertRaisesRegex(RuntimeError, "did not report"):
                with helper.pinned_runtime(self.serial):
                    helper.verify_pinned_command_line(self.serial)
            self.assertEqual((self.serial, "shell", "rm", "-f", helper.FLAG_FILE), adb.call_args.args)

    def test_enabled_or_similarly_named_native_flags_do_not_count_as_disabled(self):
        for log in ("WebViewCommandLine 'enable-features': 'MojoUseBinder'",
                    "WebViewCommandLine 'disable-features': 'MojoUseBinderExtra'",
                    "--disable-features=MojoUseBinder"):
            def respond(serial, *args):
                return log if args[0] == "logcat" else self.respond(serial, *args)
            with self.subTest(log=log), patch.object(helper, "adb", side_effect=respond):
                with self.assertRaisesRegex(RuntimeError, "did not report"):
                    with helper.pinned_runtime(self.serial):
                        helper.verify_pinned_command_line(self.serial)

    def test_flag_cleanup_is_attempted_even_if_stopping_the_app_fails(self):
        stops = 0
        def respond(serial, *args):
            nonlocal stops
            if args[:3] == ("shell", "am", "force-stop"):
                stops += 1
                if stops == 2:
                    raise subprocess.CalledProcessError(1, "force-stop")
            return self.respond(serial, *args)
        with patch.object(helper, "adb", side_effect=respond) as adb:
            with self.assertRaises(subprocess.CalledProcessError):
                with helper.pinned_runtime(self.serial):
                    pass
            self.assertEqual((self.serial, "shell", "rm", "-f", helper.FLAG_FILE), adb.call_args.args)


class SdkPreparationTest(unittest.TestCase):
    def run_script(self, failures, api="34", target="google_apis"):
        shell = shutil.which("bash")
        self.assertIsNotNone(shell, "SDK preparation tests require Bash")
        script = Path(__file__).with_name("install_emulator_sdk.sh").resolve().as_posix()
        # Mock only external commands; execute the actual retry/exit logic in Bash.
        harness = '''
            attempts=0
            sdkmanager() {
              if [[ "$1" == --install ]]; then
                attempts=$((attempts + 1))
                echo "install-$attempts:$*"
                [[ "$attempts" -gt "$3_FAILS" ]]
              else
                echo "cleanup:$*"
              fi
            }
            sleep() { :; }
            source "$1" "$2" "$3"
        '''.replace('"$3_FAILS"', str(failures))
        return subprocess.run([shell, "-c", harness, "test-sdk", script, api, target],
                              text=True, capture_output=True)

    def test_retry_only_sdk_preparation_then_succeed(self):
        result = self.run_script(failures=1)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, result.stdout.count("install-"))
        self.assertIn("cleanup:--uninstall system-images;android-34;google_apis;x86_64", result.stdout)

    def test_third_failed_download_is_not_hidden(self):
        result = self.run_script(failures=3)
        self.assertEqual(1, result.returncode)
        self.assertEqual(3, result.stdout.count("install-"))
        self.assertIn("failed after 3 attempts", result.stderr)

    def test_unknown_api_never_installs_or_cleans_anything(self):
        result = self.run_script(failures=0, api="../../")
        self.assertEqual(2, result.returncode)
        self.assertNotIn("install-", result.stdout)
        self.assertNotIn("cleanup:", result.stdout)

    def test_16kb_target_installs_the_distinct_api36_image(self):
        result = self.run_script(failures=0, api="36", target="google_apis_ps16k")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("system-images;android-36;google_apis_ps16k;x86_64", result.stdout)

    def test_invalid_image_target_never_installs_or_cleans_anything(self):
        result = self.run_script(failures=0, api="36", target="../../")
        self.assertEqual(2, result.returncode)
        self.assertNotIn("install-", result.stdout)
        self.assertNotIn("cleanup:", result.stdout)


class WorkflowContractTest(unittest.TestCase):
    def test_workflow_builds_and_uploads_the_single_application(self):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/android-ci.yml").read_text()
        for task in ("verifyApplicationVariants", "testDebugUnitTest", "lintDebug",
                     "assembleDebug", "assembleRelease", "bundleRelease"):
            self.assertIn(f":mimi-app:{task}", workflow)
        self.assertIn(":chanlib:testDebugUnitTest", workflow)
        for path in ("apk/debug/mimi-app-debug.apk", "apk/release/*.apk",
                     "bundle/release/*.aab", "mapping/release/mapping.txt"):
            self.assertIn(f"mimi-app/build/outputs/{path}", workflow)
        self.assertNotRegex(workflow, r"(?i)(?:free|paid)(?:debug|release)|outputs/apk/(?:free|paid)/")

    def test_emulator_action_receives_one_complete_shell_command(self):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/android-ci.yml").read_text()
        runner = workflow.split("uses: reactivecircus/android-emulator-runner@v2.38.0", 1)[1]
        script = re.search(r"^\s+script: (.+)$", runner, re.MULTILINE).group(1)
        # This action splits multiline input and executes each line in a new sh.
        # A YAML literal containing an if/then block would therefore fail in CI.
        self.assertTrue(script.startswith("python3 scripts/ci/android_webview.py test "))
        self.assertIn("--api '${{ matrix.api_level }}'", script)
        self.assertIn('"$RUNNER_TEMP/ci-webview.apk"', script)
        self.assertIn("target: google_apis", runner)


if __name__ == "__main__":
    unittest.main()
