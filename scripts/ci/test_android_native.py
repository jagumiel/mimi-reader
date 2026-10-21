import struct
import tempfile
from pathlib import Path
import unittest
from unittest import mock
import zipfile

import android_native as native


def elf(abi, alignment=16384, file_offset=0, address=0, loads=True):
    bits, machine = native.ABIS[abi]
    data = bytearray(120)
    data[:6] = b"\x7fELF" + bytes((bits, 1))
    struct.pack_into("<H", data, 18, machine)
    if bits == 2:
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1 if loads else 0, 5,
                         file_offset, address, 0, 0, 16384, alignment)
    else:
        struct.pack_into("<I", data, 28, 52)
        struct.pack_into("<HH", data, 42, 32, 1)
        struct.pack_into("<IIIIIIII", data, 52, 1, 0, 0, 0, 0, 4096, 5, 4096)
    return data


class ElfAlignmentTest(unittest.TestCase):
    def test_both_64_bit_abis_accept_aligned_loads(self):
        for abi in ("arm64-v8a", "x86_64"):
            self.assertEqual([16384], native.elf_loads(elf(abi), abi))

    def test_32_bit_4kb_compatibility_is_preserved(self):
        for abi in ("armeabi-v7a", "x86"):
            self.assertEqual([4096], native.elf_loads(elf(abi), abi))

    def test_old_4kb_64_bit_loads_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "16 KB"):
            native.elf_loads(elf("arm64-v8a", alignment=4096), "arm64-v8a")

    def test_non_power_of_two_alignment_is_rejected(self):
        with self.assertRaises(ValueError):
            native.elf_loads(elf("arm64-v8a", alignment=20000), "arm64-v8a")

    def test_noncongruent_addresses_are_rejected(self):
        with self.assertRaises(ValueError):
            native.elf_loads(elf("arm64-v8a", address=4096), "arm64-v8a")

    def test_wrong_abi_and_missing_loads_are_rejected(self):
        with self.assertRaises(ValueError):
            native.elf_loads(elf("arm64-v8a"), "x86_64")
        with self.assertRaises(ValueError):
            native.elf_loads(elf("arm64-v8a", loads=False), "arm64-v8a")

    def test_invalid_and_truncated_headers_are_rejected(self):
        for data in (b"not ELF", elf("arm64-v8a")[:63], elf("arm64-v8a")[:119]):
            with self.assertRaises(ValueError):
                native.elf_loads(data, "arm64-v8a")


class ApkAlignmentTest(unittest.TestCase):
    def build(self, path, abis=None, compression=zipfile.ZIP_DEFLATED, aligned=False, bad=False):
        with zipfile.ZipFile(path, "w") as apk:
            for abi in (native.ABIS if abis is None else abis):
                name = f"lib/{abi}/libfixture.so"
                entry = zipfile.ZipInfo(name)
                entry.compress_type = compression
                if aligned:
                    pad = (-apk.fp.tell() - 30 - len(name)) % native.PAGE
                    if pad:
                        entry.extra = struct.pack("<HH", 0xCAFE, pad - 4) + bytes(pad - 4)
                apk.writestr(entry, elf(abi, alignment=4096 if bad else 16384))

    def test_compressed_and_aligned_uncompressed_apks_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            for compression, aligned in ((zipfile.ZIP_DEFLATED, False), (zipfile.ZIP_STORED, True)):
                self.build(apk, compression=compression, aligned=aligned)
                self.assertEqual(4, len(native.verify_apk(apk)["libraries"]))

    def test_unaligned_uncompressed_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            self.build(apk, compression=zipfile.ZIP_STORED)
            with self.assertRaisesRegex(ValueError, "ZIP entry"):
                native.verify_apk(apk)

    def test_compression_does_not_hide_old_elf_alignment(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            self.build(apk, bad=True)
            with self.assertRaisesRegex(ValueError, "PT_LOAD"):
                native.verify_apk(apk)

    def test_missing_32_bit_or_all_libraries_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            for abis in ([], ["arm64-v8a", "x86_64"]):
                self.build(apk, abis=abis)
                with self.assertRaisesRegex(ValueError, "ABI coverage"):
                    native.verify_apk(apk)


class RuntimeSafetyTest(unittest.TestCase):
    def test_r8_constructor_contract_rejects_missing_seed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "seeds.txt"
            content = ("com.shockwave.pdfium.PdfPasswordException\n"
                       "com.shockwave.pdfium.PdfPasswordException: PdfPasswordException(java.lang.String)\n"
                       "com.shockwave.pdfium.util.Size\n"
                       "com.shockwave.pdfium.util.Size: Size(int,int)\n")
            path.write_text(content)
            native.verify_jni_seeds(path)
            path.write_text(content.replace("Size(int,int)", "Size()"))
            with self.assertRaisesRegex(ValueError, "R8 seeds"):
                native.verify_jni_seeds(path)

    def test_physical_phone_is_refused_before_adb(self):
        with mock.patch.object(native, "adb") as adb:
            with self.assertRaises(ValueError):
                native.run_16k("phone-123")
            adb.assert_not_called()

    def test_4kb_emulator_is_refused_before_any_setting_write(self):
        with mock.patch.object(native, "adb", side_effect=["1", "36", "4096"]) as adb:
            with self.assertRaises(ValueError):
                native.run_16k("emulator-5554")
            self.assertFalse(any("setprop" in call.args for call in adb.call_args_list))

    def test_16kb_run_verifies_properties_and_scopes_gradle(self):
        responses = ["1", "36", "16384", "", "", "", "false", "", "true"]
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(native, "adb", side_effect=responses), \
                mock.patch.object(native.subprocess, "run") as run, \
                mock.patch.object(native, "Path", return_value=Path(directory)):
            native.run_16k("emulator-5554")
            command = run.call_args.args[0]
            self.assertIn("-Pandroid.testInstrumentationRunnerArguments.expectedPageSize=16384", command)
            self.assertIn(":mimi-app:connectedDebugAndroidTest", command)
            self.assertEqual("emulator-5554", run.call_args.kwargs["env"]["ANDROID_SERIAL"])
            self.assertTrue(run.call_args.kwargs["check"])

    def test_workflow_keeps_release_uploads_in_build_and_adds_dedicated_16k_job(self):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/android-ci.yml").read_text()
        build = workflow.split("  build:", 1)[1].split("  instrumented-tests:", 1)[0]
        self.assertIn("name: Upload unsigned release artifacts", build)
        self.assertIn("name: Verify native ELF and APK alignment", build)
        native_job = workflow.split("  native-16k-tests:", 1)[1]
        self.assertIn("target: google_apis_ps16k", native_job)
        self.assertIn("--test-16k emulator-5554", native_job)
        self.assertNotIn("Upload unsigned release artifacts", native_job)


if __name__ == "__main__":
    unittest.main()
