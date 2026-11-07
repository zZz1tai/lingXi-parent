"""总初始化 SQL 的离线完整性检查，不连接或修改数据库。"""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SqlInitTest(unittest.TestCase):
    def test_ai_menu_tree_is_separate_from_business(self):
        sql = (ROOT / "dkd-parent/sql/lingxi_all.sql").read_text(encoding="utf-8-sig")
        business = sql[sql.index("-- Clean business menu tree"):sql.index("CREATE TABLE IF NOT EXISTS tb_order_detail")]
        ai = sql[sql.index("-- AI video menus and permissions"):sql.index("-- AI 聊天会话附件元数据")]
        # 在内存库执行真实菜单种子 SQL；仅适配 MySQL 的变量和 UPSERT 语法。
        with sqlite3.connect(":memory:") as db:
            db.execute("""CREATE TABLE sys_menu (
                menu_id INTEGER PRIMARY KEY AUTOINCREMENT, menu_name TEXT, parent_id INTEGER,
                order_num INTEGER, path TEXT, component TEXT, query TEXT, is_frame INTEGER,
                is_cache INTEGER, menu_type TEXT, visible TEXT, status TEXT, perms TEXT,
                icon TEXT, create_by TEXT, create_time TEXT, update_by TEXT,
                update_time TEXT, remark TEXT)""")
            db.execute("CREATE TABLE sys_role_menu (role_id INTEGER, menu_id INTEGER, PRIMARY KEY(role_id, menu_id))")
            db.create_function("NOW", 0, lambda: "2026-11-07")
            db.create_function("CONCAT", -1, lambda *args: "".join(map(str, args)))
            db.create_function("SUBSTRING_INDEX", 3, lambda text, sep, count: sep.join(text.split(sep)[:count]))
            variables = {}
            text = re.sub(r"--[^\n]*", "", business + ai)
            for statement in text.split(";"):
                statement = statement.strip()
                if not statement:
                    continue
                assignment = re.match(r"SET\s+(@\w+)\s*:=\s*\((.*)\)$", statement, re.I | re.S)
                if assignment:
                    query = re.sub(r"@\w+", lambda m: str(variables.get(m[0], "NULL")), assignment[2])
                    result = db.execute(query).fetchone()
                    variables[assignment[1]] = result[0] if result else "NULL"
                    continue
                statement = re.sub(r"@\w+", lambda m: str(variables.get(m[0], "NULL")), statement)
                statement = statement.replace(
                    "ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id)",
                    "ON CONFLICT(role_id, menu_id) DO UPDATE SET menu_id = excluded.menu_id",
                )
                db.execute(statement)
            menus = {row[0]: row[1:] for row in db.execute("SELECT menu_id, parent_id, component, perms, path FROM sys_menu")}
            for menu_id, (parent, component, perms, path) in menus.items():
                if (component or "").startswith(("ai/", "aiVedio/", "novel/")):
                    ancestors = {menu_id}
                    while parent:
                        self.assertNotIn(parent, ancestors, "菜单树不能形成环")
                        ancestors.add(parent)
                        parent = menus[parent][0]
                    self.assertNotIn(2000, ancestors, "AI 页面不能挂到业务目录")
            self.assertEqual(menus[2015], (0, "ai/qwen-chat", "ai:chat:list", "ai"))
            self.assertEqual(db.execute("SELECT count(*) FROM sys_menu WHERE perms LIKE 'ai:chat:%'").fetchone()[0], 1)
            self.assertIsNotNone(db.execute("SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=2015").fetchone())
            self.assertEqual(db.execute("SELECT count(*) FROM sys_menu WHERE path='aiVedio' AND parent_id=0").fetchone()[0], 1)

    def test_only_one_initialization_entry(self):
        self.assertEqual(
            sorted(p.name for p in (ROOT / "dkd-parent/sql").glob("*.sql")),
            ["lingxi_all.sql"],
        )

    def test_tables_and_migrated_fields_are_preserved(self):
        sql = (ROOT / "dkd-parent/sql/lingxi_all.sql").read_text(encoding="utf-8-sig")
        tables = dict(re.findall(
            r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s*\((.*?)\)\s*ENGINE\s*=",
            sql, re.I | re.S,
        ))
        self.assertEqual(len(tables), 80)
        for table, column in [
            ("ai_novel_work", "pacing_level"),
            ("ai_novel_outline", "chapter_no"),
            ("ai_video_generation_task", "recover_count"),
        ]:
            self.assertRegex(tables[table], rf"\b{column}\b")
        self.assertIn("ai_novel_context_task", tables)
        self.assertRegex(tables["ai_novel_chapter"], r"del_flag\s+CHAR\(1\)\s+NULL")
        for column in ("content", "ui_json"):
            self.assertRegex(tables["tb_model_history"], rf"\b{column}\s+MEDIUMTEXT")
        self.assertTrue(sql.rstrip().endswith("SELECT 'dkd database initialized successfully' AS message;"))


if __name__ == "__main__":
    unittest.main()
