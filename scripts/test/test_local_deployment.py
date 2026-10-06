import base64
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

TOOL = Path(__file__).resolve().parents[1] / "utils" / "local_deployment.py"


class LocalDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TOOL.exists(), "local deployment tool is missing")
        spec = importlib.util.spec_from_file_location("local_deployment", TOOL)
        self.tool = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.tool)
        self.key = os.urandom(32)
        self.env = {
            name: base64.b64encode(os.urandom(32)).decode()
            for name in self.tool.BASE64_KEYS
        }
        self.env["CONFIG_ENCRYPTION_KEY"] = "c" * 32
        self.env["JWT_SECRET"] = "j" * 48
        self.env["APEX_DATA_ENCRYPTION_KEY"] = base64.b64encode(self.key).decode()
        self.row = {
            "id": 1, "tenant_id": "tenant-a", "user_id": 2, "version": 3,
            "secret": "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
            "backup_codes": "12345678,87654321", "used_backup_codes": "",
            "deleted": 0,
        }

    def test_keys_validate_actual_formats_without_values(self):
        self.tool.validate_keys(self.env)
        for name in self.tool.BASE64_KEYS:
            for value in ("", "bad!", base64.b64encode(b"short").decode()):
                invalid = dict(self.env, **{name: value})
                with self.assertRaises(ValueError) as caught:
                    self.tool.validate_keys(invalid)
                self.assertNotIn(value or "sentinel", str(caught.exception))
        for value in (" ") * 32, "é" * 32, base64.b64encode(self.key).decode():
            with self.assertRaises(ValueError):
                self.tool.validate_keys(dict(self.env, CONFIG_ENCRYPTION_KEY=value))
        self.tool.validate_keys(dict(self.env, CONFIG_ENCRYPTION_KEY="é" * 16))

    def test_snapshot_repeat_backup_and_rollback(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.json"
            original = json.dumps([self.row], indent=2).encode()
            source.write_bytes(original)
            bundle = root / "bundle"
            report = self.tool.prepare(source, bundle, self.key)
            self.assertEqual(source.read_bytes(), original)
            self.assertEqual((bundle / "backup.json").read_bytes(), original)
            self.assertEqual(report["changed_fields"], 3)
            migrated = json.loads((bundle / "candidate.json").read_text())
            for field in self.tool.FIELDS:
                self.assertEqual(self.tool.decrypt(migrated[0][field], self.key), self.row[field])
            self.assertEqual(migrated[0]["version"], 3)
            self.assertNotIn(self.row["secret"], (bundle / "review.json").read_text())
            repeat = root / "repeat"
            report2 = self.tool.prepare(bundle / "candidate.json", repeat, self.key)
            self.assertEqual(report2["changed_fields"], 0)
            self.assertEqual(json.loads((repeat / "candidate.json").read_text()), migrated)
            restored = root / "restored.json"
            self.tool.rollback(bundle, restored)
            self.assertEqual(restored.read_bytes(), original)
            self.assertEqual(bundle.stat().st_mode & 0o777, 0o700)
            for file in bundle.iterdir():
                self.assertEqual(file.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                self.tool.prepare(source, bundle, self.key)
            with self.assertRaises(FileExistsError):
                self.tool.rollback(bundle, restored)
            (bundle / "candidate.json").write_text("[]")
            with self.assertRaises(ValueError):
                self.tool.rollback(bundle, root / "tampered.json")

    def test_rotation_null_and_deleted_rows(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            old_key = os.urandom(32)
            row = dict(self.row, deleted=1, backup_codes=None, used_backup_codes=None)
            row["secret"] = self.tool.encrypt(row["secret"], old_key)
            source = root / "source.json"
            source.write_text(json.dumps([row]))
            report = self.tool.prepare(source, root / "bundle", self.key, old_key)
            self.assertEqual(report["changed_fields"], 1)
            result = json.loads((root / "bundle/candidate.json").read_text())[0]
            self.assertIsNone(result["backup_codes"])
            self.assertEqual(self.tool.decrypt(result["secret"], self.key), self.row["secret"])

    def test_invalid_or_wrong_key_aborts_before_artifacts(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source = root / "source.json"
            values = ["ENC(invalid)", "unknown", None,
                      self.tool.encrypt(self.row["secret"], os.urandom(32))]
            for index, value in enumerate(values):
                source.write_text(json.dumps([dict(self.row, secret=value)]))
                output = root / str(index)
                with self.assertRaises(ValueError):
                    self.tool.prepare(source, output, self.key)
                self.assertFalse(output.exists())
            source.write_text(json.dumps([self.row, self.row]))
            with self.assertRaises(ValueError):
                self.tool.prepare(source, root / "duplicate", self.key)

    def test_cli_does_not_echo_bad_secret_or_input(self):
        sentinel = "PRIVATE-SENTINEL-DO-NOT-LOG"
        result = subprocess.run(
            ["python3", str(TOOL), "validate"],
            env=dict(os.environ, APEX_DATA_ENCRYPTION_KEY=sentinel),
            capture_output=True, text=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(sentinel, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)


if __name__ == "__main__":
    unittest.main()
