"""Offline release identity and Markdown/in-app changelog consistency checks."""

from html.parser import HTMLParser
from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


def normalize(text):
    return " ".join(text.split())


def markdown_blocks(document, version):
    entries = re.findall(
        rf"^## \[{re.escape(version)}\] - [^\n]+\n(.*?)(?=^## |\Z)",
        document, re.MULTILINE | re.DOTALL)
    if len(entries) != 1:
        raise ValueError("Expected exactly one version entry")
    blocks = []
    for line in entries[0].splitlines():
        if not line.strip():
            continue
        kind, text = ("h3", line[4:]) if line.startswith("### ") else (
            ("li", line[2:]) if line.startswith("- ") else ("p", line))
        text = re.sub(r"\[([^\]]+)\]\(https://[^)]+\)", r"\1", text)
        blocks.append((kind, normalize(text.replace("`", ""))))
    if not blocks:
        raise ValueError("Empty version entry")
    return blocks


class ReleaseSection(HTMLParser):
    def __init__(self, version):
        super().__init__(convert_charrefs=True)
        self.identifier = "release-" + version.replace(".", "-")
        self.matches = 0
        self.inside = False
        self.kind = None
        self.text = []
        self.blocks = []

    def handle_starttag(self, tag, attrs):
        if tag == "section" and dict(attrs).get("id") == self.identifier:
            self.matches += 1
            self.inside = True
        if not self.inside:
            return
        if tag in {"script", "iframe", "object", "embed", "img", "link"}:
            raise ValueError("Release notes must remain local static text")
        if tag in {"h3", "p", "li"}:
            if self.kind is not None:
                raise ValueError("Unexpected nested release block")
            self.kind, self.text = tag, []

    def handle_data(self, data):
        if self.inside and self.kind:
            self.text.append(data)

    def handle_endtag(self, tag):
        if self.inside and tag == self.kind:
            self.blocks.append((tag, normalize("".join(self.text))))
            self.kind = None
        if tag == "section":
            self.inside = False


def html_blocks(document, version):
    parser = ReleaseSection(version)
    parser.feed(document)
    parser.close()
    if parser.matches != 1 or parser.inside or parser.kind or not parser.blocks:
        raise ValueError("Expected one complete nonempty release section")
    return parser.blocks


class ReleaseNotesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.markdown = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
        cls.html = (ROOT / "mimi-app/src/main/res/raw/changelog.html").read_text(encoding="utf-8")
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        cls.version = re.search(r"^mimiVersionName=(\S+)$", properties, re.MULTILINE)[1]
        cls.code = re.search(r"^mimiVersionCode=(\d+)$", properties, re.MULTILINE)[1]

    def test_current_markdown_and_in_app_blocks_match_in_order(self):
        self.assertEqual(markdown_blocks(self.markdown, self.version),
                         html_blocks(self.html, self.version))
        self.assertIn(f"Mimi {self.version}", self.html)
        self.assertIn(f"Version code: `{self.code}`.", self.markdown)

    def test_shared_version_properties_drive_app_and_sbom_without_old_fallbacks(self):
        for path in ("mimi-app/build.gradle", "gradle/sbom.gradle"):
            text = (ROOT / path).read_text(encoding="utf-8")
            identity_lines = [line for line in text.splitlines()
                              if "providers.gradleProperty('mimiVersion" in line]
            self.assertGreaterEqual(len(identity_lines), 2)
            for line in identity_lines:
                self.assertIn(".get()", line)
                self.assertNotIn("getOrElse", line)
        self.assertGreater(int(self.code), 230)

    def test_tracked_sbom_uses_the_same_application_identity(self):
        bom = json.loads((ROOT / "sbom/mimi-reader-release.cdx.json").read_text(encoding="utf-8"))
        app = bom["metadata"]["component"]
        self.assertEqual(self.version, app["version"])
        self.assertEqual(f"pkg:generic/mimi-reader@{self.version}?variant=release", app["bom-ref"])
        self.assertEqual(self.code, next(p["value"] for p in app["properties"]
                                        if p["name"] == "mimi:versionCode"))

    def test_historical_notes_and_copyright_are_retained(self):
        self.assertIn("Copyright (c) 2016. Eli Connelly", self.html)
        self.assertIn("Licensed under the Apache License, Version 2.0", self.html)
        history = self.html.split("</section>", 1)[1]
        for text in ("Mimi 7.0.1 ChangeLog:", "Mimi 7.0.0 ChangeLog:",
                     "Fix slowdown after using the app for a while", "Fix custom board ordering",
                     "Require Android 6 or above", "Major refactor to thread watcher"):
            self.assertIn(text, history)
        self.assertNotIn('href="https://mimireader.com"', self.html)

    def test_fork_install_identity_is_separate_without_renaming_code_namespace(self):
        build = (ROOT / "mimi-app/build.gradle").read_text(encoding="utf-8")
        manifest = (ROOT / "mimi-app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('applicationId "io.github.jagumiel.mimireader"', build)
        self.assertIn("namespace 'com.emogoth.android.phone.mimi'", build)
        self.assertIn("debug: 'io.github.jagumiel.mimireader.beta'", build)
        self.assertIn("release: 'io.github.jagumiel.mimireader'", build)
        self.assertIn('android:authorities="${applicationId}.fileprovider"', manifest)

    def test_markdown_requires_one_nonempty_version_entry(self):
        valid = "## [8.0] - Unreleased\n- Example\n"
        for document in ("", "## [8.0] - Unreleased\n", valid + valid):
            with self.subTest(document=document), self.assertRaises(ValueError):
                markdown_blocks(document, "8.0")

    def test_html_requires_one_complete_nonempty_section(self):
        valid = '<section id="release-8-0"><p>Example</p></section>'
        for document in ("", '<section id="release-8-0"></section>',
                         '<section id="release-8-0"><p>Example', valid + valid):
            with self.subTest(document=document), self.assertRaises(ValueError):
                html_blocks(document, "8.0")

    def test_inline_markup_and_entities_preserve_visible_text(self):
        md = "## [8.0] - Unreleased\n### Added\n- `MP4` & [WebM](https://example.invalid)\n"
        html = '<section id="release-8-0"><h3>Added</h3><ul><li><code>MP4</code> &amp; <a href="https://example.invalid">WebM</a></li></ul></section>'
        self.assertEqual(markdown_blocks(md, "8.0"), html_blocks(html, "8.0"))

    def test_active_or_embedded_resources_are_rejected(self):
        for tag in ("script", "iframe", "object", "embed", "img", "link"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                html_blocks(f'<section id="release-8-0"><{tag}></{tag}><p>Example</p></section>', "8.0")


if __name__ == "__main__":
    unittest.main()
