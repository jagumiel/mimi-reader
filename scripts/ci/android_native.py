#!/usr/bin/env python3
"""Check shipped ELF/ZIP alignment without an NDK; never infer runtime support from API level."""
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess
import zipfile

PAGE = 16384
ABIS = {"arm64-v8a": (2, 183), "x86_64": (2, 62), "armeabi-v7a": (1, 40), "x86": (1, 3)}


def adb(serial, *args):
    return subprocess.check_output(["adb", "-s", serial, *args], text=True, timeout=30).strip()


def run_16k(serial):
    # Never change compatibility properties on a physical phone or an ordinary 4 KB emulator.
    if not serial.startswith("emulator-"):
        raise ValueError("16 KB tests require an explicitly selected emulator")
    if (adb(serial, "shell", "getprop", "ro.kernel.qemu") != "1" or
            adb(serial, "shell", "getprop", "ro.build.version.sdk") != "36" or
            adb(serial, "shell", "getconf", "PAGE_SIZE") != str(PAGE)):
        raise ValueError("Expected an API 36 emulator actually running with 16384-byte pages")
    adb(serial, "root")
    adb(serial, "wait-for-device")
    for key, value in (("bionic.linker.16kb.app_compat.enabled", "false"),
                       ("pm.16kb.app_compat.disabled", "true")):
        adb(serial, "shell", "setprop", key, value)
        if adb(serial, "shell", "getprop", key) != value:
            raise ValueError(f"Could not disable emulator compatibility mode: {key}")
    directory = Path("build/reports/native-16k")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "device.txt").write_text(f"{serial}\nAPI=36\nPAGE_SIZE={PAGE}\nbackcompat=false\n", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    subprocess.run([wrapper, "--no-daemon", "--stacktrace", ":mimi-app:connectedDebugAndroidTest",
                    "-Pandroid.testInstrumentationRunnerArguments.class=com.emogoth.android.phone.mimi.view.gallery.NativeMediaCompatibilityTest",
                    "-Pandroid.testInstrumentationRunnerArguments.expectedPageSize=16384"],
                   env={**os.environ, "ANDROID_SERIAL": serial}, check=True)


def elf_loads(data, abi):
    if len(data) < 52 or data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Not a supported little-endian ELF")
    bits, machine = ABIS[abi]
    if data[4] != bits or struct.unpack_from("<H", data, 18)[0] != machine:
        raise ValueError("ELF class/machine does not match ABI")
    if bits == 2:
        if len(data) < 64:
            raise ValueError("Truncated ELF64 header")
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        fmt = "<IIQQQQQQ"
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        fmt = "<IIIIIIII"
    if not count or size < struct.calcsize(fmt) or offset + size * count > len(data):
        raise ValueError("Missing or truncated ELF program headers")
    loads = []
    for index in range(count):
        values = struct.unpack_from(fmt, data, offset + index * size)
        if values[0] != 1:  # PT_LOAD
            continue
        if bits == 2:
            _, _, file_offset, address, _, file_size, memory_size, alignment = values
        else:
            _, file_offset, address, _, file_size, memory_size, _, alignment = values
        if file_size > memory_size or file_offset + file_size > len(data):
            raise ValueError("Invalid ELF load segment extent")
        if bits == 2 and (alignment < PAGE or alignment & (alignment - 1) or
                          file_offset % PAGE != address % PAGE):
            raise ValueError("ELF64 PT_LOAD is not 16 KB aligned")
        loads.append(alignment)
    if not loads:
        raise ValueError("ELF has no load segments")
    return loads


def verify_apk(path):
    rows = []
    seen = set()
    with zipfile.ZipFile(path) as apk, open(path, "rb") as raw:
        for entry in apk.infolist():
            parts = entry.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or not parts[2].endswith(".so"):
                continue
            abi = parts[1]
            if abi not in ABIS or entry.filename in seen:
                raise ValueError(f"Unsupported ABI or duplicate native entry: {entry.filename}")
            seen.add(entry.filename)
            try:
                loads = elf_loads(apk.read(entry), abi)
                if ABIS[abi][0] == 2 and entry.compress_type == zipfile.ZIP_STORED:
                    raw.seek(entry.header_offset)
                    header = raw.read(30)
                    if len(header) != 30 or header[:4] != b"PK\x03\x04":
                        raise ValueError("Invalid ZIP local header")
                    name_size, extra_size = struct.unpack_from("<HH", header, 26)
                    if (entry.header_offset + 30 + name_size + extra_size) % PAGE:
                        raise ValueError("Uncompressed ELF64 ZIP entry is not 16 KB aligned")
            except (ValueError, struct.error) as error:
                raise ValueError(f"{entry.filename}: {error}") from error
            rows.append({"file": entry.filename, "loadAlignments": loads,
                         "compressed": entry.compress_type != zipfile.ZIP_STORED})
    if {row["file"].split("/")[1] for row in rows} != set(ABIS):
        raise ValueError("Native ABI coverage must preserve arm64-v8a, x86_64, armeabi-v7a and x86")
    return {"pageSize": PAGE, "libraries": rows,
            "scope": "ELF PT_LOAD and APK ZIP alignment, not runtime or vulnerability certification"}


def verify_jni_seeds(path):
    lines = {line.replace(" ", "") for line in Path(path).read_text(encoding="utf-8").splitlines()}
    required = {
        "com.shockwave.pdfium.PdfPasswordException",
        "com.shockwave.pdfium.PdfPasswordException:PdfPasswordException(java.lang.String)",
        "com.shockwave.pdfium.util.Size",
        "com.shockwave.pdfium.util.Size:Size(int,int)",
    }
    if not required.issubset(lines):
        raise ValueError(f"Missing JNI-resolved R8 seeds: {sorted(required - lines)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, nargs="?")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--r8-seeds", type=Path)
    parser.add_argument("--test-16k", metavar="EMULATOR_SERIAL")
    args = parser.parse_args()
    try:
        if args.test_16k:
            if args.apk or args.report or args.r8_seeds:
                parser.error("Device tests do not take an APK/report argument")
            run_16k(args.test_16k)
            return
        if args.apk is None:
            parser.error("Provide an APK or --test-16k EMULATOR_SERIAL")
        report = verify_apk(args.apk)
        if args.r8_seeds:
            verify_jni_seeds(args.r8_seeds)
            report["jniConstructorsVerified"] = True
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        parser.exit(1, f"Native alignment verification failed: {error}\n")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Native alignment verified: {len(report['libraries'])} libraries, four ABIs; runtime testing still required")


if __name__ == "__main__":
    main()
