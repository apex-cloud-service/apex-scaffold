from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHECK = Path("scripts/ci/check-doc-links.sh")


class DocLinksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="doc links ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "project space"
        for relative in (CHECK, Path("scripts/lib/common.sh")):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        self.write("pom.xml", "<project/>")
        (self.root / "docs").mkdir()

    def write(self, relative, text=""):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def run_check(self, status=0, env=None):
        result = subprocess.run(["bash", str(self.root / CHECK)], cwd=self.temp.name,
                                capture_output=True, text=True, timeout=15, env=env)
        output = result.stdout + result.stderr
        self.assertEqual(status, result.returncode, output)
        self.assertIn("扫描完成", output)
        return output

    def test_empty_documents(self):
        self.write("docs/empty.md", "没有链接\n")
        self.assertIn("总链接数: 0", self.run_check())

    def test_empty_document_does_not_hide_later_broken_links(self):
        self.write("docs/00-empty.md", "没有链接\n")
        self.write("docs/99-links.md", "[missing](absent/target.md)\n[other](other.md)\n")
        output = self.run_check(status=1)
        self.assertIn("断裂链接: 2", output)
        self.assertIn("99-links.md:1", output)
        self.assertIn("absent/target.md", output)
        self.assertIn("other.md", output)

    def test_common_markdown_destinations(self):
        self.write("docs/guide space/目标 (v1).md")
        self.write("docs/guide space/a+b.md")
        self.write("docs/guide space/read me.md", "\n".join((
            '[plain](目标%20(v1).md#part)',
            '[escaped](目标\\ \\(v1\\).md)',
            '[angle](<目标 (v1).md> "title")',
            "[single](a+b.md 'title')",
            '[title](a+b.md (title))',
            '![image](a+b.md?raw=1#part)',
            '[nested [label]](a+b.md)',
        )))
        self.assertIn("总链接数: 7", self.run_check())

    def test_reference_links(self):
        self.write("docs/target.md")
        self.write("docs/read.md", '[full][Ref]\n[Ref][]\n[Ref]\n[broken][bad]\n'
                   '[Ref]: <target.md> "title"\n[bad]: missing.md\n')
        output = self.run_check(status=1)
        self.assertIn("总链接数: 4", output)
        self.assertIn("断裂链接: 1", output)
        self.assertIn("read.md:4", output)

    def test_file_links_and_absolute_paths(self):
        target = self.write("docs/目标 space.md")
        self.write("docs/read.md", "\n".join((
            f'[uri]({target.as_uri()}#L1)',
            f'[local](file://localhost{target.as_uri()[7:]}?raw=1)',
            '[relative](file:目标%20space.md)',
            f'[absolute](<{target}>)',
            '[missing](file:missing%20file.md#L2)',
        )))
        output = self.run_check(status=1)
        self.assertIn("总链接数: 5", output)
        self.assertIn("断裂链接: 1", output)
        self.assertIn("missing file.md", output)

    def test_non_file_links_and_code_are_ignored(self):
        self.write("docs/read.md", '\n'.join((
            '[web](https://example.org)', '[mail](mailto:a@example.org)',
            '[ftp](ftp://example.org/a)', '[anchor](#part)', '[empty]()',
            '[network](//example.org/a)', '`[code](missing.md)`',
            '```md', '[sample](missing.md)', '```',
            '~~~', '[sample](missing.md)', '~~~',
        )))
        self.assertIn("总链接数: 0", self.run_check())

    def test_library_exclusions(self):
        for directory in ("target", ".git", "node_modules", ".trae"):
            self.write(f"docs/{directory}/bad.md", "[bad](missing.md)")
        self.assertIn("总链接数: 0", self.run_check())

    def test_find_failure_is_not_reported_as_success(self):
        command = self.write("bin/find", "#!/bin/bash\nexit 37\n")
        command.chmod(0o755)
        env = dict(os.environ, PATH=str(command.parent) + os.pathsep + os.environ["PATH"])
        result = subprocess.run(["bash", str(self.root / CHECK)], cwd=self.temp.name,
                                capture_output=True, text=True, timeout=15, env=env)
        self.assertEqual(37, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("扫描完成", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
