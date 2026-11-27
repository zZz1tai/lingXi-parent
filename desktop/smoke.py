"""安装包资源的端到端自检；两次启动验证数据库不会重复初始化。"""

import json
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import time

from launcher import Services, load_secrets
from business_smoke import verify_region_queries


runtime = Path(sys.argv[1]).resolve()
temporary = Path(tempfile.mkdtemp(prefix="lingxi-desktop-")).resolve()
passed = False
try:
    data = Path(temporary) / "独立数据目录 with spaces"
    for attempt in range(2):
        # 不得把上一次退出前留下的 ready 当作这一次已经启动完成。
        (data / "status.json").unlink(missing_ok=True)
        child = subprocess.Popen(
            [str(runtime / "python/python.exe"), "-X", "utf8", str(runtime / "launcher.py"),
             "--runtime", str(runtime), "--data", str(data)],
            stdin=subprocess.PIPE,
        )
        try:
            deadline = time.monotonic() + 600
            state = {"phase": "starting", "message": "尚无状态文件"}
            while time.monotonic() < deadline:
                status_file = data / "status.json"
                if status_file.exists():
                    state = json.loads(status_file.read_text(encoding="utf-8"))
                if state["phase"] in ("ready", "error") or child.poll() is not None:
                    break
                time.sleep(1)
            if child.returncode or state["phase"] != "ready":
                # 不输出数据库凭据和配置文件。业务日志供 CI 排障。
                for name in ("mysql-initialize.log", "mysql.log", "agent.log", "java.log"):
                    log = data / "logs" / name
                    if log.exists():
                        print(log.read_text(encoding="utf-8", errors="replace")[-18000:])
                for log in (data / "mysql").glob("*.err"):
                    print(log.read_text(encoding="utf-8", errors="replace")[-12000:])
                raise RuntimeError(f"第 {attempt + 1} 次桌面启动失败：{state['message']}")
            database = Services(runtime, data)
            database.secrets = load_secrets(data)
            try:
                database.mysql_home = database.mysql_path(data)
                database.mysql_root = database.mysql_path(runtime / "mysql")
                verify_region_queries(database)
                if attempt == 0:
                    database.mysql("CREATE TABLE dkd.desktop_smoke (value VARCHAR(64)); INSERT INTO dkd.desktop_smoke VALUES ('data-survives-relaunch');")
                else:
                    assert database.mysql("SELECT value FROM dkd.desktop_smoke;") == "data-survives-relaunch"
                    database.mysql("DROP TABLE dkd.desktop_smoke;")
            finally:
                database.release_aliases()
        finally:
            if child.poll() is None:
                child.stdin.close()
                child.wait(timeout=90)
        # 数据库内部写入持久记录，再次启动后必须存在。
        credentials = load_secrets(data)
        if attempt == 0:
            original = credentials
            marker = (data / "database.json").read_bytes()
        else:
            assert credentials == original
            assert (data / "database.json").read_bytes() == marker
    print("完整桌面资源首次启动、再次启动、登录、区域筛选分页、本地上传、目录切换、旧文件与重启保留、签名校验和正常关闭检查通过。")
    passed = True
finally:
    if passed and temporary.parent == Path(tempfile.gettempdir()).resolve() and temporary.name.startswith("lingxi-desktop-"):
        shutil.rmtree(temporary)
    elif not passed:
        print(f"启动诊断数据已保留：{temporary}（包含本机凭据，请勿公开上传）。")
