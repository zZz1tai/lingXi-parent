"""桌面打包关键边界的离线测试，不连接用户数据库。"""

import importlib.util
import json
from pathlib import Path
import socket
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("desktop_launcher", Path(__file__).with_name("launcher.py"))
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class DesktopTests(unittest.TestCase):
    def test_user_sid_does_not_depend_on_console_encoding(self):
        identity = '"电脑\\张三","S-1-5-21-123-456-789-1001"'.encode("gbk")
        self.assertEqual("S-1-5-21-123-456-789-1001", launcher.user_sid(identity))

    def test_mysql_path_compatibility_respects_system_code_page(self):
        self.assertTrue(launcher.needs_path_alias(Path("C:/中文目录"), "cp1252"))
        self.assertFalse(launcher.needs_path_alias(Path("C:/中文目录"), "gbk"))
        self.assertFalse(launcher.needs_path_alias(Path("C:/Users/local"), "cp1252"))

    def test_secrets_survive_relaunch(self):
        with tempfile.TemporaryDirectory() as root:
            data = Path(root)
            first = launcher.load_secrets(data)
            self.assertEqual(first, launcher.load_secrets(data))
            self.assertEqual(5, len(set(first.values())))

    def test_missing_credentials_never_reinitializes_existing_database(self):
        with tempfile.TemporaryDirectory() as root:
            data = Path(root)
            (data / "mysql").mkdir()
            with self.assertRaisesRegex(RuntimeError, "不会重建"):
                launcher.load_secrets(data)
            self.assertFalse((data / "secrets.json").exists())

    def test_schema_change_requires_migration(self):
        self.assertTrue(launcher.verify_schema(None, "new"))
        self.assertFalse(launcher.verify_schema({"schema_sha256": "same"}, "same"))
        with self.assertRaisesRegex(RuntimeError, "已保留"):
            launcher.verify_schema({"schema_sha256": "old"}, "new")

    def test_busy_port_does_not_reuse_existing_service(self):
        with socket.socket() as occupied:
            occupied.bind(("127.0.0.1", 0))
            occupied.listen()
            port = occupied.getsockname()[1]
            with patch.dict(launcher.PORTS, {"mysql": port}, clear=True):
                with self.assertRaisesRegex(RuntimeError, "已被占用"):
                    launcher.ensure_ports_available()

    def test_migration_plan_follows_exact_versions(self):
        steps = [{"from": "old", "to": "middle"}, {"from": "middle", "to": "new"}]
        self.assertEqual(steps, launcher.migration_plan("old", "new", steps))

    def test_unknown_or_ambiguous_migrations_fail_closed(self):
        for steps in ([], [{"from": "old", "to": "old"}],
                      [{"from": "old", "to": "new"}, {"from": "old", "to": "other"}]):
            with self.assertRaisesRegex(RuntimeError, "已保留"):
                launcher.migration_plan("old", "new", steps)

    def test_migration_outside_resource_directory_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            service = launcher.Services(Path(root), Path(root))
            (Path(root) / "migrations").mkdir()
            with self.assertRaisesRegex(RuntimeError, "校验失败"):
                service.migrate([{"file": "../outside.sql", "sha256": "fake"}], "new")

    def test_partial_seed_is_not_imported_again(self):
        with tempfile.TemporaryDirectory() as root:
            service = launcher.Services(Path(root), Path(root))
            service.secrets = {"root": "fake", "database": "fake"}
            (service.data / "mysql").mkdir()
            with patch.object(service, "start"), patch.object(service, "wait_for"), \
                    patch.object(service, "mysql", side_effect=["1", "17"]) as mysql:
                with self.assertRaisesRegex(RuntimeError, "已停止初始化"):
                    service.bootstrap_mysql("new", True)
                self.assertEqual(mysql.call_count, 2)
            self.assertFalse((service.data / "database.json").exists())

    def test_marked_database_skips_seed_and_accounts(self):
        with tempfile.TemporaryDirectory() as root:
            service = launcher.Services(Path(root), Path(root))
            service.secrets = {"root": "fake"}
            (service.data / "mysql").mkdir()
            with patch.object(service, "start"), patch.object(service, "wait_for"), \
                    patch.object(service, "mysql", return_value="1") as mysql:
                service.bootstrap_mysql("same", False)
                mysql.assert_called_once_with("SELECT 1;")

    def test_status_has_no_secret_fields(self):
        with tempfile.TemporaryDirectory() as root:
            service = launcher.Services(Path(root), Path(root))
            service.secrets = {"database": "must-not-leak"}
            service.status("starting", "正在启动")
            text = (Path(root) / "status.json").read_text(encoding="utf-8")
            self.assertNotIn("must-not-leak", text)
            self.assertEqual("starting", json.loads(text)["phase"])


if __name__ == "__main__":
    unittest.main()
