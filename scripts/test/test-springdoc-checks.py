import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHECK = Path("scripts/dev/check/check-springdoc-annotations.sh")
V2 = Path("scripts/dev/check/check-springdoc-annotations-v2.sh")
CI = Path("scripts/ci/check-springdoc-coverage.sh")
INTERFACES = Path("apex-interfaces/src/main/java/com/apex/scaffold/interfaces")
APPLICATION = Path("apex-application/src/main/java/com/apex/scaffold/application/dto")


class SpringdocChecksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="springdoc tests ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "project space"
        self.root.mkdir()
        for relative in (CHECK, V2, CI, Path("scripts/lib/common.sh")):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        self.write("pom.xml", "<project/>")
        for directory in (INTERFACES / "router", INTERFACES / "handler", INTERFACES / "dto", APPLICATION):
            (self.root / directory).mkdir(parents=True)
        self.write(INTERFACES / "router/GoodRouter.java", "@RouterOperations({})\n")
        self.write(INTERFACES / "handler/GoodHandler.java", "/** description */\n@Operation(summary = \"ok\")\npublic Mono<Response> handle() {}\n")
        self.write(INTERFACES / "dto/Good.java", "@Schema\nclass Good {}\n")
        self.env = dict(os.environ, LOG_LEVEL="3")

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def run_script(self, script=CHECK, *args, status=0):
        result = subprocess.run(["bash", str(self.root / script), *args], cwd=self.temp.name,
                                env=self.env, capture_output=True, text=True, timeout=15)
        self.assertEqual(status, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("integer expression expected", result.stderr)
        return result.stdout + result.stderr

    def report(self, status=0):
        self.run_script(CHECK, "-v", "-r", "report space.txt", status=status)
        return (self.root / "report space.txt").read_text()

    def stub(self, body, script=CHECK):
        self.write(script, "#!/bin/bash\n" + body)

    def fake_command(self, name, code):
        path = self.write("bin/" + name, "#!/bin/bash\nexit " + str(code) + "\n")
        path.chmod(0o755)
        self.env["PATH"] = str(path.parent) + os.pathsep + os.environ["PATH"]

    def test_complete_report_and_help(self):
        text = self.report()
        for field in ("Router @RouterOperations: 100%", "Handler @Operation: 100%", "DTO @Schema: 100%", "检查总结"):
            self.assertIn(field, text)
        for script in (CHECK, CI):
            for flag in ("-h", "--help"):
                self.assertIn("使用方法", self.run_script(script, flag))
        self.run_script(CI)

    def test_missing_annotations_and_zero_counts(self):
        self.write(INTERFACES / "router/GoodRouter.java", "class GoodRouter {}\n")
        self.write(INTERFACES / "handler/GoodHandler.java", "public Mono<Response> handle() {}\n")
        self.write(INTERFACES / "dto/Good.java", "class Good {}\n")
        text = self.report(status=1)
        self.assertIn("Handler @Operation: 0%", text)
        self.assertIn("Handler @Operation: 0%", text)
        self.run_script(CI, status=1)
        self.run_script(CI, "--min-router", "0", "--min-handler", "0", "--min-dto", "0", status=1)

    def test_infrastructure_router_excluded_from_denominator(self):
        self.write(INTERFACES / "router/MethodNotAllowedRouter.java", "class MethodNotAllowedRouter {}\n")
        self.assertIn("Router @RouterOperations: 100%", self.report())

    def test_library_exclusions_and_space_names(self):
        for excluded in (".trae", "target", ".git", ".idea", ".vscode", "node_modules", ".settings", "build"):
            for directory, name in (("router", "BadRouter.java"), ("handler", "BadHandler.java"), ("dto", "Bad.java")):
                self.write(INTERFACES / directory / excluded / name, "public Mono<X> bad() {}\n")
        for name in ("IgnorePO.java", "IgnoreEntity.java", "IgnoreDO.java"):
            self.write(INTERFACES / "dto" / name, "class Ignore {}\n")
        self.assertIn("总统计: DTO总数=1", self.report())

    def test_empty_directories(self):
        for directory in ("router", "handler", "dto"):
            for path in (self.root / INTERFACES / directory).iterdir():
                path.unlink()
        text = self.report()
        self.assertIn("Handler @Operation: 0%", text)
        self.run_script(CI, "--min-router", "0", "--min-handler", "0", "--min-dto", "0")

    def test_missing_required_directories(self):
        shutil.rmtree(self.root / INTERFACES / "router")
        self.run_script(CHECK, status=1)
        self.run_script(CI, "--min-router", "0", "--min-handler", "0", "--min-dto", "0", status=1)

    def test_optional_dto_directory(self):
        shutil.rmtree(self.root / APPLICATION)
        self.report()

    def test_report_argument_validation(self):
        for args in (("--report",), ("-r", ""), ("--report", "--verbose"), ("--unknown",)):
            with self.subTest(args=args):
                self.run_script(CHECK, *args, status=1)
        self.assertFalse((self.root / "--verbose").exists())

    def test_threshold_validation(self):
        for flag in ("--min-router", "--min-handler", "--min-dto"):
            for value in (None, "", "abc", "-1", "101", "1.5", "--help", "99999999999999999999999999999"):
                with self.subTest(flag=flag, value=value):
                    args = (flag,) if value is None else (flag, value)
                    self.run_script(CI, *args, status=1)
        self.run_script(CI, "--min-router", "000100", "--min-handler", "095", "--min-dto", "0")

    def test_io_failures(self):
        self.run_script(CHECK, "--report", str(self.root), status=1)
        self.fake_command("find", 37)
        self.run_script(CHECK, status=37)
        self.run_script(CI, status=37)

    def test_count_failures(self):
        self.fake_command("grep", 38)
        self.run_script(CHECK, status=38)
        self.run_script(CI, status=38)

    def test_ci_invalid_reports(self):
        valid = "  Router @RouterOperations: 100%\n  Handler @Operation: 100%\n  DTO @Schema: 100%\n"
        for text in ("", valid.replace("100%", "oops%", 1), valid + "  DTO @Schema: 100%\n", valid.replace("100%", "101%", 1)):
            with self.subTest(text=text):
                self.stub("cat > \"$2\" <<'REPORT'\n" + text + "REPORT\nexit 0\n", V2)
                self.run_script(CI, "--min-router", "0", "--min-handler", "0", "--min-dto", "0", status=1)

    def test_ci_child_failure_with_valid_report(self):
        self.stub(
            "printf '  Router @RouterOperations: 100%%\\n  Handler @Operation: 100%%\\n  DTO @Schema: 100%%\\n' > \"$2\"\nexit 42\n",
            V2,
        )
        self.run_script(CI, status=42)

    def test_excess_operations_do_not_create_negative_counts(self):
        self.write(INTERFACES / "handler/GoodHandler.java", "@Operation(summary = \"class\")\n/** description */\n@Operation(summary = \"method\")\npublic Mono<Response> handle() {}\n")
        text = self.report()
        self.assertIn("Handler @Operation: 100%", text)
        self.assertNotIn("缺失:-", text)


if __name__ == "__main__":
    unittest.main()
