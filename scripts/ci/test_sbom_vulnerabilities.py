import copy
from datetime import date, datetime, timedelta, timezone
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import sbom_vulnerabilities as security


PURL = "pkg:maven/example/library@1.0?type=jar"
OTHER = "pkg:maven/example/other@1.0?type=jar"
TODAY = date(2026, 10, 24)
NOW = datetime(2026, 10, 24, 20, tzinfo=timezone.utc)


def bom():
    return {"bomFormat": "CycloneDX", "specVersion": "1.6", "components": [
        {"purl": PURL, "version": "1.0"},
        {"purl": "pkg:github/example/runtime@v1.0", "version": "1.0"}]}


def report(severity="HIGH", identifier="CVE-2026-12345"):
    result = {"Class": "lang-pkgs", "Type": "jar", "Packages": [
        {"Identifier": {"PURL": PURL}, "Version": "1.0"}], "Vulnerabilities": []}
    if severity:
        result["Vulnerabilities"].append({"PkgIdentifier": {"PURL": PURL},
            "PkgName": "example:library",
            "VulnerabilityID": identifier, "InstalledVersion": "1.0", "Severity": severity,
            "VendorIDs": ["GHSA-abcd-efgh-2345"], "FixedVersion": "", "SeveritySource": "ghsa"})
    return {"SchemaVersion": 2, "ArtifactType": "cyclonedx", "Results": [result]}


def exception():
    return {"vulnerability": "CVE-2026-12345", "purl": PURL,
            "reason": "Synthetic scoped review for an offline unit-test fixture only.",
            "reviewedBy": "https://github.com/example", "reviewedOn": "2026-10-24",
            "expires": "2026-11-23", "trackingIssue": "https://github.com/example/project/issues/1"}


def document(*entries):
    return {"schemaVersion": 1, "exceptions": list(entries)}


class CoverageTest(unittest.TestCase):
    def test_all_maven_packages_required_and_manual_gap_is_visible(self):
        findings, coverage = security.report_findings(report(), bom())
        self.assertEqual(1, coverage["scannedMavenPackages"])
        self.assertEqual(1, len(coverage["unassessedComponents"]))
        self.assertEqual(["GHSA-abcd-efgh-2345"], findings[0]["aliases"])

    def test_empty_report_cannot_claim_clean(self):
        for malformed in ({}, [], {"SchemaVersion": 2, "ArtifactType": "cyclonedx", "Results": []}):
            with self.subTest(malformed=malformed), self.assertRaises(ValueError):
                security.report_findings(malformed, bom())

    def test_missing_package_fails_even_without_findings(self):
        value = report(None)
        value["Results"][0]["Packages"] = []
        with self.assertRaisesRegex(ValueError, "coverage"):
            security.report_findings(value, bom())

    def test_package_identity_or_version_drift_fails(self):
        for field, value in (("Version", "2.0"), ("Identifier", {"PURL": OTHER})):
            changed = report()
            changed["Results"][0]["Packages"][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                security.report_findings(changed, bom())

    def test_unsupported_result_type_fails(self):
        value = report()
        value["Results"][0]["Type"] = "unknown"
        with self.assertRaises(ValueError):
            security.report_findings(value, bom())

    def test_duplicate_or_absent_sbom_identity_fails(self):
        changed = bom()
        changed["components"].append(copy.deepcopy(changed["components"][-1]))
        with self.assertRaises(ValueError):
            security.components(changed)
        changed = bom()
        del changed["components"][0]["version"]
        with self.assertRaises(ValueError):
            security.components(changed)
        with self.assertRaises(ValueError):
            security.components({"bomFormat": "CycloneDX", "specVersion": "1.6", "components": []})

    def test_duplicate_finding_uses_highest_severity(self):
        value = report("CRITICAL")
        value["Results"][0]["Vulnerabilities"] += report("LOW")["Results"][0]["Vulnerabilities"]
        findings, _ = security.report_findings(value, bom())
        self.assertEqual(1, len(findings))
        self.assertEqual("CRITICAL", findings[0]["severity"])

    def test_malformed_finding_never_becomes_clean(self):
        for field, value in (("Severity", "high"), ("InstalledVersion", "2.0"),
                             ("PkgIdentifier", {"PURL": OTHER}), ("VulnerabilityID", "*")):
            changed = report()
            changed["Results"][0]["Vulnerabilities"][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                security.report_findings(changed, bom())


class PolicyTest(unittest.TestCase):
    def test_high_and_critical_block_even_when_unfixed(self):
        for severity in ("HIGH", "CRITICAL"):
            with self.subTest(severity=severity):
                result = security.evaluate(report(severity), bom(), document(), TODAY)
                self.assertEqual(1, result["blockingCount"])
                self.assertEqual("", result["findings"][0]["fixedVersion"])

    def test_lower_and_unknown_severities_are_visible_without_blocking(self):
        for severity in ("LOW", "MEDIUM", "UNKNOWN"):
            with self.subTest(severity=severity):
                result = security.evaluate(report(severity), bom(), document(), TODAY)
                self.assertEqual(0, result["blockingCount"])
                self.assertEqual(severity, result["findings"][0]["severity"])

    def test_empty_findings_pass_only_with_complete_package_coverage(self):
        result = security.evaluate(report(None), bom(), document(), TODAY)
        self.assertEqual([], result["findings"])
        self.assertIn("not a security certification", security.markdown(result))

    def test_exact_scoped_review_waives_gate_but_not_report(self):
        result = security.evaluate(report(), bom(), document(exception()), TODAY)
        self.assertEqual(0, result["blockingCount"])
        self.assertEqual(1, len(result["findings"]))
        self.assertIn("exception until 2026-11-23", security.markdown(result))

    def test_exception_does_not_hide_other_advisory(self):
        result = security.evaluate(report(identifier="CVE-2026-99999"), bom(), document(exception()), TODAY)
        self.assertEqual(1, result["blockingCount"])
        self.assertEqual(1, len(result["unusedExceptions"]))

    def test_exception_does_not_hide_other_package(self):
        changed_bom = bom()
        changed_bom["components"].append({"purl": OTHER, "version": "1.0"})
        changed_report = report()
        changed_report["Results"][0]["Packages"].append({"Identifier": {"PURL": OTHER}, "Version": "1.0"})
        changed_report["Results"][0]["Vulnerabilities"][0]["PkgIdentifier"]["PURL"] = OTHER
        self.assertEqual(1, security.evaluate(changed_report, changed_bom,
                                            document(exception()), TODAY)["blockingCount"])

    def test_ghsa_only_findings_are_enforced(self):
        self.assertEqual(1, security.evaluate(report(identifier="GHSA-abcd-efgh-2345"), bom(),
                                            document(), TODAY)["blockingCount"])

    def test_expired_future_and_overlong_reviews_fail(self):
        for changes in ({"expires": "2026-10-24"}, {"expires": "2026-10-23"},
                        {"reviewedOn": "2026-10-25"}, {"expires": "2027-03-01"},
                        {"reviewedOn": "not-a-date"}):
            entry = exception()
            entry.update(changes)
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                security.evaluate(report(), bom(), document(entry), TODAY)

    def test_invalid_review_and_wildcard_scopes_fail(self):
        for field, value in (("purl", "*"), ("vulnerability", "CVE-*"), ("reason", "skip"),
                             ("reviewedBy", "anonymous"), ("trackingIssue", "not-a-url")):
            entry = exception()
            entry[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                security.exceptions(document(entry), bom(), TODAY)

    def test_duplicate_or_missing_review_fields_fail(self):
        with self.assertRaises(ValueError):
            security.exceptions(document(exception(), exception()), bom(), TODAY)
        entry = exception()
        del entry["reviewedBy"]
        with self.assertRaises(ValueError):
            security.exceptions(document(entry), bom(), TODAY)

    def test_summary_escapes_dependency_supplied_markdown(self):
        result = security.evaluate(report(), bom(), document(), TODAY)
        result["findings"][0]["purl"] = "<script>|`\n"
        text = security.markdown(result)
        self.assertNotIn("<script>", text)
        self.assertIn("&lt;script&gt;&#124;&#96;", text)


class DatabaseAndExecutionTest(unittest.TestCase):
    def test_nanosecond_db_metadata_is_supported_on_python310(self):
        security.check_database({"Version": 2, "UpdatedAt": "2026-10-24T19:00:00.123456789Z"}, NOW)

    def test_stale_future_wrong_schema_or_naive_database_fails(self):
        for metadata in ({"Version": 2, "UpdatedAt": (NOW - timedelta(hours=49)).isoformat()},
                         {"Version": 2, "UpdatedAt": (NOW + timedelta(hours=1)).isoformat()},
                         {"Version": 1, "UpdatedAt": NOW.isoformat()},
                         {"Version": 2, "UpdatedAt": "2026-10-24T19:00:00"}):
            with self.subTest(metadata=metadata), self.assertRaises(ValueError):
                security.check_database(metadata, NOW)

    def test_scanner_failure_is_not_interpreted_as_no_findings(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "scanner.log"
            with mock.patch.object(security.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "DB download failed")):
                with self.assertRaisesRegex(ValueError, "execution failed"):
                    security.run_trivy(Path("trivy"), [], {}, log)
            self.assertIn("DB download failed", log.read_text())

    def test_timeout_propagates_to_failure(self):
        with mock.patch.object(security.subprocess, "run", side_effect=subprocess.TimeoutExpired("trivy", 420)):
            with self.assertRaises(subprocess.TimeoutExpired):
                security.run_trivy(Path("trivy"), [], {}, Path("unused.log"))

    def test_version_mismatch_leaves_scan_incomplete(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with mock.patch.object(security, "run_trivy", return_value='{"Version":"0.0.1"}'):
                with self.assertRaisesRegex(ValueError, "reviewed Trivy"):
                    security.scan(Path("trivy"), security.SBOM, output, output / "cache")
            self.assertEqual({"complete": False}, security.read_json(output / "scan-status.json"))

    def test_incomplete_or_missing_evidence_cannot_pass_cli(self):
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch("sys.stderr"):
                self.assertEqual(2, security.main(["evaluate", "--output", directory]))
                security.write_json(Path(directory) / "scan-status.json", {"complete": False})
                self.assertEqual(2, security.main(["evaluate", "--output", directory]))


class ScanOrchestrationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        self.output = self.root / "output"
        self.cache = self.root / "cache"
        self.sbom = self.root / "release.json"
        self.exceptions = self.root / "exceptions.json"
        security.write_json(self.sbom, bom())
        security.write_json(self.exceptions, document())
        self.arguments = []
        self.severity = "HIGH"
        self.sarif_ids = ["CVE-2026-12345"]
        self.failure = None

    def fake_trivy(self, binary, arguments, env, log):
        self.arguments.append(arguments)
        self.assertFalse(any(key.startswith("TRIVY_") for key in env))
        if self.failure == arguments[0]:
            raise ValueError("Synthetic database/conversion failure")
        if arguments[0] == "version":
            return '{"Version":"' + security.TRIVY_VERSION + '"}'
        if arguments[0] == "sbom":
            security.write_json(self.output / "trivy.json", report(self.severity))
            (self.cache / "db").mkdir(parents=True, exist_ok=True)
            security.write_json(self.cache / "db/metadata.json", {"Version": 2,
                                "UpdatedAt": datetime.now(timezone.utc).isoformat()})
        if arguments[0] == "convert":
            security.write_json(self.output / "trivy-original.sarif", {"version": "2.1.0", "runs": [
                {"results": [{"ruleId": identifier,
                              "message": {"text": "Package: example:library\nInstalled Version: 1.0\nSynthetic"}}
                             for identifier in self.sarif_ids]}]})
        return ""

    def scan(self):
        with mock.patch.object(security, "ROOT", self.root), mock.patch.object(
                security, "run_trivy", side_effect=self.fake_trivy):
            security.scan(Path("trivy"), self.sbom, self.output, self.cache)

    def evaluate(self):
        with mock.patch("sys.stdout"), mock.patch("sys.stderr"), mock.patch.dict(os.environ, {}, clear=True):
            return security.main(["evaluate", "--sbom", str(self.sbom), "--exceptions", str(self.exceptions),
                                  "--output", str(self.output)])

    def test_scan_ignores_ambient_filters_and_reports_all_severities(self):
        with mock.patch.dict(os.environ, {"TRIVY_SEVERITY": "LOW", "TRIVY_IGNORE_UNFIXED": "true",
                                         "TRIVY_SKIP_DB_UPDATE": "true", "TRIVY_VEX": "attacker.json"}):
            self.scan()
        self.assertIn("--ignore-unfixed=false", self.arguments[1])
        self.assertIn("--list-all-pkgs", self.arguments[1])
        self.assertIn(",".join(security.SEVERITIES), self.arguments[1])
        self.assertNotIn("--skip-db-update", self.arguments[1])
        self.assertNotIn("--disable-telemetry", self.arguments[2])  # convert has no such flag
        self.assertEqual("{}\n", (self.output / "empty-config.yaml").read_text())
        self.assertEqual("", (self.output / "empty-ignore").read_text())

    def test_cli_blocks_high_after_writing_reports(self):
        self.scan()
        self.assertEqual(1, self.evaluate())
        self.assertTrue((self.output / "summary.md").is_file())
        self.assertEqual(1, security.read_json(self.output / "policy.json")["blockingCount"])

    def test_cli_medium_reports_without_blocking(self):
        self.severity = "MEDIUM"
        self.scan()
        self.assertEqual(0, self.evaluate())

    def test_scan_or_conversion_failure_never_marks_evidence_complete(self):
        for failure in ("sbom", "convert"):
            self.failure = failure
            with self.subTest(failure=failure), self.assertRaises(ValueError):
                self.scan()
            self.assertEqual({"complete": False}, security.read_json(self.output / "scan-status.json"))
            self.assertEqual(2, self.evaluate())

    def test_sarif_cannot_drop_a_json_finding(self):
        self.sarif_ids = []
        with self.assertRaisesRegex(ValueError, "SARIF lost"):
            self.scan()
        self.assertEqual(2, self.evaluate())

    def test_sarif_locations_anchor_to_real_component_and_fingerprint_is_repeatable(self):
        self.scan()
        result = security.read_json(self.output / "trivy.sarif")["runs"][0]["results"][0]
        location = result["locations"][0]["physicalLocation"]
        self.assertEqual("release.json", location["artifactLocation"]["uri"])
        line = location["region"]["startLine"]
        self.assertIn(PURL, self.sbom.read_text().splitlines()[line - 1])
        fingerprint = result["partialFingerprints"]
        self.scan()
        self.assertEqual(fingerprint, security.read_json(self.output / "trivy.sarif")["runs"][0]["results"][0]["partialFingerprints"])

    def test_sarif_wrong_package_cannot_be_misattributed(self):
        findings, _ = security.report_findings(report(), bom())
        data = {"version": "2.1.0", "runs": [{"results": [{"ruleId": "CVE-2026-12345",
                "message": {"text": "Package: example:other\nInstalled Version: 1.0\n"}}]}]}
        with self.assertRaisesRegex(ValueError, "mapped exactly"):
            security.anchor_sarif(data, findings, self.sbom.read_text(), "release.json")

    def test_changed_report_cannot_reuse_evidence(self):
        self.scan()
        security.write_json(self.output / "trivy.json", report(None))
        self.assertEqual(2, self.evaluate())

    def test_changed_sbom_cannot_reuse_evidence(self):
        self.scan()
        with self.sbom.open("a", encoding="utf-8") as stream:
            stream.write("\n")
        self.assertEqual(2, self.evaluate())


class WorkflowContractTest(unittest.TestCase):
    def test_security_events_are_separated_from_readonly_scanner(self):
        text = (security.ROOT / ".github/workflows/sbom-security.yml").read_text(encoding="utf-8")
        scanner, upload = text.split("  upload-sarif:", 1)
        self.assertNotIn("security-events: write", scanner)
        self.assertIn("security-events: write", upload)
        self.assertIn("needs.scan.outputs.report-ready == 'true'", upload)
        self.assertIn("github.event.pull_request.head.repo.full_name == github.repository", upload)
        self.assertIn("github.actor != 'dependabot[bot]'", upload)
        self.assertNotIn("pull_request_target:", text)
        self.assertNotIn("continue-on-error", text)

    def test_triggers_pins_and_report_retention_are_present(self):
        text = (security.ROOT / ".github/workflows/sbom-security.yml").read_text(encoding="utf-8")
        for value in ("  push:", "  pull_request:", "  schedule:", "  workflow_dispatch:",
                      "TRIVY_VERSION: '" + security.TRIVY_VERSION + "'", "sha256sum --check --strict",
                      "retention-days: 14", "if: always()", "scripts/ci/sbom_vulnerabilities.py evaluate",
                      "evidence/build/reports/vulnerabilities/trivy.sarif"):
            self.assertIn(value, text)
        import re
        for action in re.findall(r"uses: (\S+)", text):
            self.assertRegex(action, r"@[0-9a-f]{40}$")

    def test_real_release_inventory_and_empty_exceptions_validate(self):
        expected, gaps = security.components(security.read_json(security.SBOM))
        self.assertTrue(expected)
        self.assertTrue(any(item["purl"].startswith("pkg:github/ruffle-rs/") for item in gaps))
        self.assertEqual({}, security.exceptions(security.read_json(security.EXCEPTIONS),
                                                security.read_json(security.SBOM), TODAY))


if __name__ == "__main__":
    unittest.main()
