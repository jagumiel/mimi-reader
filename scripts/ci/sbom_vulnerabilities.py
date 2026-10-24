"""Scan the committed release SBOM, verify coverage, and enforce an expiring policy.

Standard library only. Exit 1 means policy findings; exit 2 means invalid/incomplete
evidence or execution failure. Neither a DB/network failure nor missing packages
is a clean scan. No application code or dependency snapshot is modified.
"""
import argparse
from datetime import date, datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
TRIVY_VERSION = "0.76.1"
SEVERITIES = ("UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL")
EXCEPTIONS = ROOT / "sbom/vulnerability-exceptions.json"
SBOM = ROOT / "sbom/mimi-reader-release.cdx.json"
OUTPUT = ROOT / "build/reports/vulnerabilities"


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def components(sbom):
    if (not isinstance(sbom, dict) or sbom.get("bomFormat") != "CycloneDX"
            or sbom.get("specVersion") != "1.6" or not isinstance(sbom.get("components"), list)):
        raise ValueError("Expected the verified CycloneDX 1.6 release SBOM")
    expected, gaps, seen = {}, [], set()
    for item in sbom["components"]:
        purl = item.get("purl", "")
        if not purl or not item.get("version") or purl in seen:
            raise ValueError("Missing/duplicate component identity or version")
        seen.add(purl)
        if purl.startswith("pkg:maven/"):
            expected[purl] = item["version"]
        else:
            gaps.append({"purl": purl, "reason": "Not a Maven package; no advisory coverage asserted"})
    if not expected:
        raise ValueError("SBOM contains no Maven packages to scan")
    return expected, gaps


def report_findings(report, sbom):
    expected, gaps = components(sbom)
    if (not isinstance(report, dict) or report.get("SchemaVersion") != 2
            or report.get("ArtifactType") != "cyclonedx" or not isinstance(report.get("Results"), list)):
        raise ValueError("Invalid Trivy SBOM report identity/schema")
    seen, findings = {}, {}
    for result in report["Results"]:
        if result.get("Class") != "lang-pkgs" or result.get("Type") != "jar":
            raise ValueError("Unexpected scanner result type")
        for package in result["Packages"]:
            purl = package["Identifier"]["PURL"]
            if purl not in expected or package["Version"] != expected[purl]:
                raise ValueError("Scanner package identity/version differs from SBOM")
            seen[purl] = package["Version"]
        vulnerabilities = result.get("Vulnerabilities", [])
        if vulnerabilities is None:
            vulnerabilities = []
        if not isinstance(vulnerabilities, list):
            raise ValueError("Malformed vulnerabilities list")
        for item in vulnerabilities:
            purl = item["PkgIdentifier"]["PURL"]
            severity = item["Severity"]
            identifier = item["VulnerabilityID"]
            if (purl not in expected or item["InstalledVersion"] != expected[purl]
                    or severity not in SEVERITIES
                    or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{1,127}", identifier)):
                raise ValueError("Malformed vulnerability identity, version or severity")
            key = (identifier, purl)
            finding = {"id": identifier, "purl": purl, "severity": severity,
                       "packageName": item["PkgName"],
                       "installedVersion": item["InstalledVersion"],
                       "fixedVersion": item.get("FixedVersion", ""),
                       "aliases": item.get("VendorIDs", []),
                       "severitySource": item.get("SeveritySource", ""),
                       "url": item.get("PrimaryURL", "")}
            # Duplicate reports cannot downgrade the same finding's blocking severity.
            if key not in findings or SEVERITIES.index(severity) > SEVERITIES.index(findings[key]["severity"]):
                findings[key] = finding
    if set(seen) != set(expected):
        raise ValueError("Incomplete scanner coverage: missing Maven packages: " +
                         ", ".join(sorted(set(expected) - set(seen))))
    return sorted(findings.values(), key=lambda x: (x["purl"], x["id"])), {
        "expectedMavenPackages": len(expected), "scannedMavenPackages": len(seen),
        "unassessedComponents": gaps,
        "limits": ["Native dependencies inside AARs and Ruffle WASM build dependencies are not reconstructed",
                   "Build/test tools and Android platform libraries are outside the release SBOM",
                   "Package matching is not proof of exploitability or complete advisory coverage"]}


def exceptions(document, sbom, today):
    if (not isinstance(document, dict) or set(document) != {"schemaVersion", "exceptions"}
            or type(document["schemaVersion"]) is not int or document["schemaVersion"] != 1):
        raise ValueError("Invalid exception document")
    if not isinstance(document["exceptions"], list):
        raise ValueError("Exceptions must be an explicit list")
    expected, _ = components(sbom)
    accepted = {}
    fields = {"vulnerability", "purl", "reason", "reviewedBy", "reviewedOn", "expires", "trackingIssue"}
    for entry in document["exceptions"]:
        if set(entry) != fields or not all(isinstance(x, str) and x.strip() == x and x for x in entry.values()):
            raise ValueError("Exception requires exact scope, review, reason, tracking issue and expiry")
        key = (entry["vulnerability"], entry["purl"])
        if (key in accepted or entry["purl"] not in expected
                or not re.fullmatch(r"(?:CVE-\d{4}-\d{4,}|GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4})", key[0])
                or len(entry["reason"]) < 20
                or not re.fullmatch(r"https://github.com/[A-Za-z0-9-]+", entry["reviewedBy"])
                or not re.fullmatch(r"https://github.com/[^/\s]+/[^/\s]+/issues/\d+", entry["trackingIssue"])):
            raise ValueError("Unreviewed, duplicate, wildcard or invalid exception scope")
        if not all(re.fullmatch(r"\d{4}-\d{2}-\d{2}", entry[key]) for key in ("reviewedOn", "expires")):
            raise ValueError("Exception dates must use YYYY-MM-DD")
        reviewed = date.fromisoformat(entry["reviewedOn"])
        expiry = date.fromisoformat(entry["expires"])
        if reviewed > today or expiry <= today or not reviewed < expiry <= reviewed + timedelta(days=90):
            raise ValueError("Expired, future-reviewed or over-90-day exception")
        accepted[key] = entry
    return accepted


def evaluate(report, sbom, document, today):
    findings, coverage = report_findings(report, sbom)
    accepted = exceptions(document, sbom, today)
    used = set()
    for finding in findings:
        key = (finding["id"], finding["purl"])
        finding["exception"] = accepted.get(key)
        if finding["exception"]:
            used.add(key)
        finding["blocking"] = finding["severity"] in ("HIGH", "CRITICAL") and key not in accepted
    return {"schemaVersion": 1, "scanner": "Trivy " + TRIVY_VERSION,
            "evaluatedOn": today.isoformat(), "coverage": coverage,
            "findings": findings, "blockingCount": sum(x["blocking"] for x in findings),
            "unusedExceptions": [accepted[key] for key in sorted(set(accepted) - used)]}


def markdown(policy):
    # Treat package/advisory data as text, not Markdown/HTML supplied by a dependency.
    def escape(value):
        return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(
            ">", "&gt;").replace("|", "&#124;").replace("`", "&#96;").replace("\n", " ").replace("\r", " ")
    coverage = policy["coverage"]
    lines = ["## Release SBOM vulnerability policy", "",
             f"Trivy {TRIVY_VERSION}; {coverage['scannedMavenPackages']}/{coverage['expectedMavenPackages']} Maven packages recognized.",
             f"Blocking HIGH/CRITICAL findings: **{policy['blockingCount']}**. Unfixed findings are included.", "",
             "| Advisory | Package/version | Severity | Fixed version | Policy |",
             "| --- | --- | --- | --- | --- |"]
    for finding in policy["findings"]:
        state = "BLOCK" if finding["blocking"] else "report"
        if finding["exception"]:
            state = "exception until " + finding["exception"]["expires"]
        lines.append("| " + " | ".join(escape(x) for x in (finding["id"], finding["purl"],
                     finding["severity"], finding["fixedVersion"] or "not available", state)) + " |")
    if not policy["findings"]:
        lines.extend(["", "No known findings returned; this is not a security certification."])
    lines.extend(["", "Unassessed components (not declared clean):"])
    lines.extend("- " + escape(x["purl"]) + ": " + x["reason"] for x in coverage["unassessedComponents"])
    lines.extend(["", *["- " + x for x in coverage["limits"]],
                  "- Unused exceptions: " + str(len(policy["unusedExceptions"])), ""])
    return "\n".join(lines)


def check_database(metadata, now):
    # Go emits RFC3339 nanoseconds; Python 3.10 accepts only microseconds.
    timestamp = re.sub(r"(\.\d{6})\d+(?=Z|[+-])", r"\1", metadata["UpdatedAt"])
    updated = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    if (metadata.get("Version") != 2 or updated.tzinfo is None
            or not timedelta(minutes=-5) <= now - updated <= timedelta(hours=48)):
        raise ValueError("Missing, future-dated or stale vulnerability database (maximum 48 hours)")


def run_trivy(binary, arguments, env, log):
    result = subprocess.run([str(binary), *arguments], cwd=ROOT, env=env,
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=420)
    with log.open("a", encoding="utf-8") as stream:
        stream.write(result.stdout + result.stderr)
    if result.returncode:
        raise ValueError("Trivy execution failed; see " + str(log))
    return result.stdout


def anchor_sarif(data, findings, sbom_text, target):
    """Trivy labels SBOM results 'Java'; point alerts to real SBOM components."""
    if data.get("version") != "2.1.0" or not data.get("runs"):
        raise ValueError("Invalid/missing SARIF report")
    seen = set()
    for run in data["runs"]:
        for result in run.get("results", []):
            message = result["message"]["text"]
            matches = [item for item in findings if item["id"] == result["ruleId"] and
                       message.startswith("Package: " + item["packageName"] +
                                          "\nInstalled Version: " + item["installedVersion"] + "\n")]
            if len(matches) != 1:
                raise ValueError("SARIF finding cannot be mapped exactly to its SBOM package")
            finding = matches[0]
            key = (finding["id"], finding["purl"])
            seen.add(key)
            line = next((index for index, text in enumerate(sbom_text.splitlines(), 1)
                         if '"purl": ' + json.dumps(finding["purl"]) in text), 1)
            result["locations"] = [{"physicalLocation": {
                "artifactLocation": {"uri": target}, "region": {"startLine": line}}}]
            result.setdefault("partialFingerprints", {})["mimiSbomPackage/v1"] = hashlib.sha256(
                (key[0] + "\n" + key[1]).encode("utf-8")).hexdigest()
    if seen != {(item["id"], item["purl"]) for item in findings}:
        raise ValueError("SARIF lost or introduced findings")
    return data


def scan(binary, sbom_path, output, cache):
    output.mkdir(parents=True, exist_ok=True)
    status = output / "scan-status.json"
    write_json(status, {"complete": False})
    env = {key: value for key, value in os.environ.items() if not key.startswith("TRIVY_")}
    log = output / "scanner.log"
    log.write_text("", encoding="utf-8")
    version = json.loads(run_trivy(binary, ["version", "--format", "json"], env, log))
    if version.get("Version") != TRIVY_VERSION:
        raise ValueError("Use the reviewed Trivy " + TRIVY_VERSION)
    # Explicit empty config/ignore inputs defeat ambient config and ignore files.
    config, ignore = output / "empty-config.yaml", output / "empty-ignore"
    config.write_text("{}\n", encoding="utf-8")
    ignore.write_text("", encoding="utf-8")
    common = ["--config", str(config), "--ignorefile", str(ignore),
              "--severity", ",".join(SEVERITIES), "--exit-code", "0"]
    before = hashlib.sha256(sbom_path.read_bytes()).hexdigest()
    sbom = read_json(sbom_path)
    components(sbom)
    target = sbom_path.relative_to(ROOT).as_posix()
    raw, sarif = output / "trivy.json", output / "trivy.sarif"
    run_trivy(binary, ["sbom", *common, "--disable-telemetry", "--scanners", "vuln", "--ignore-unfixed=false",
                      "--list-all-pkgs", "--format", "json", "--output", str(raw),
                      "--cache-dir", str(cache), "--no-progress", "--timeout", "5m", target], env, log)
    findings, coverage = report_findings(read_json(raw), sbom)
    metadata = read_json(cache / "db/metadata.json")
    check_database(metadata, datetime.now(timezone.utc))
    original_sarif = output / "trivy-original.sarif"
    run_trivy(binary, ["convert", *common, "--format", "sarif", "--output", str(original_sarif), str(raw)], env, log)
    write_json(sarif, anchor_sarif(read_json(original_sarif), findings,
                                 sbom_path.read_text(encoding="utf-8"), target))
    if hashlib.sha256(sbom_path.read_bytes()).hexdigest() != before:
        raise ValueError("SBOM changed during scan")
    write_json(output / "evidence.json", {"scanner": version, "database": metadata,
               "sbomSha256": before, "coverage": coverage,
               "trivyReportSha256": hashlib.sha256(raw.read_bytes()).hexdigest()})
    write_json(status, {"complete": True})


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("scan", "evaluate"))
    parser.add_argument("--trivy", type=Path)
    parser.add_argument("--sbom", type=Path, default=SBOM)
    parser.add_argument("--exceptions", type=Path, default=EXCEPTIONS)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--cache", type=Path, default=ROOT / "build/trivy-cache")
    args = parser.parse_args(argv)
    try:
        output = args.output.resolve()
        if args.command == "scan":
            if not args.trivy:
                raise ValueError("--trivy is required for scanning")
            scan(args.trivy.resolve(), args.sbom.resolve(), output, args.cache.resolve())
            print("SBOM scan/coverage/SARIF validated; evaluate the policy separately")
            return 0
        if read_json(output / "scan-status.json") != {"complete": True}:
            raise ValueError("Scan is incomplete; policy cannot pass")
        evidence = read_json(output / "evidence.json")
        if (evidence["sbomSha256"] != hashlib.sha256(args.sbom.read_bytes()).hexdigest()
                or evidence["trivyReportSha256"] != hashlib.sha256((output / "trivy.json").read_bytes()).hexdigest()):
            raise ValueError("Scan evidence no longer matches SBOM/report")
        check_database(evidence["database"], datetime.now(timezone.utc))
        policy = evaluate(read_json(output / "trivy.json"), read_json(args.sbom),
                          read_json(args.exceptions), datetime.now(timezone.utc).date())
        write_json(output / "policy.json", policy)
        summary = markdown(policy)
        (output / "summary.md").write_text(summary, encoding="utf-8")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
                stream.write(summary)
        print(summary)
        return 1 if policy["blockingCount"] else 0
    except (ValueError, KeyError, TypeError, AttributeError, OSError, subprocess.SubprocessError) as error:
        print("SBOM vulnerability verification failed: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
