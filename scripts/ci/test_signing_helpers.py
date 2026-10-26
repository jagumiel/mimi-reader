"""Signing-helper guard tests. Never create or access a real private key."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / "scripts/release/New-MimiSigningKey.ps1"


class SigningHelperSourceTest(unittest.TestCase):
    def test_passwords_are_only_entered_in_native_interactive_prompts(self):
        text = HELPER.read_text(encoding="utf-8")
        self.assertNotIn("-storepass", text)
        self.assertNotIn("-keypass", text)
        self.assertNotIn("Start-Transcript", text)
        self.assertIn("-storetype PKCS12", text)
        self.assertIn("-keysize 4096", text)
        self.assertIn("[IO.File]::Move($temporaryKey, $keyFile)", text)

    def test_signing_verifies_identity_signature_and_alignment_before_finalizing(self):
        text = (ROOT / "scripts/release/Sign-MimiApk.ps1").read_text(encoding="utf-8")
        self.assertNotIn("--ks-pass", text)
        self.assertNotIn("--key-pass", text)
        self.assertNotIn("Start-Transcript", text)
        self.assertIn("ExpectedCertificateSha256", text)
        self.assertIn("ExpectedVersionCode", text)
        self.assertIn("application-debuggable", text)
        self.assertIn("@(29, 31)", text)
        self.assertLess(text.index("$signer verify"), text.index("[IO.File]::Move($temporary, $destination)"))
        self.assertIn("$zipalign -c -P 16 4 $temporary", text)


@unittest.skipUnless(os.name == "nt" and shutil.which("powershell.exe"),
                     "Windows-only PowerShell preflight guards")
class SigningHelperGuardTest(unittest.TestCase):
    def run_guard(self, path):
        # Python is a harmless dummy executable: none of these invalid requests
        # may reach key generation, and the tests never invoke the real keytool.
        return subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive",
                               "-ExecutionPolicy", "RemoteSigned", "-File", str(HELPER),
                               "-KeyStorePath", str(path), "-KeytoolPath", sys.executable,
                               "-NoPause"], capture_output=True, text=True, errors="replace", timeout=15)

    def test_existing_keystore_is_never_changed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "existing.p12"
            path.write_bytes(b"not a key; must stay unchanged")
            result = self.run_guard(path)
            self.assertEqual(1, result.returncode)
            self.assertIn("Refusing to replace existing file", result.stdout)
            self.assertEqual(b"not a key; must stay unchanged", path.read_bytes())
            self.assertEqual([path], list(Path(directory).iterdir()))

    def test_repository_is_not_a_private_key_destination(self):
        path = ROOT / "build/forbidden-signing-key.p12"
        result = self.run_guard(path)
        self.assertEqual(1, result.returncode)
        self.assertIn("Private keys must stay outside this repository", result.stdout)
        self.assertFalse(path.exists())

    def test_relative_paths_are_rejected(self):
        result = self.run_guard("relative-key.p12")
        self.assertEqual(1, result.returncode)
        self.assertIn("Use an absolute keystore path", result.stdout)


if __name__ == "__main__":
    unittest.main()
