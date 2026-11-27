"""桌面版私有服务管理器：只使用安装包内的运行环境，不连接现有数据库。"""

from __future__ import annotations

import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import threading
import time
import urllib.request


PORTS = {"mysql": 13306, "redis": 16379, "agent": 15000, "java": 18080}
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def write_json(path: Path, value: dict) -> None:
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    temporary.replace(path)


def ensure_ports_available() -> None:
    # 不复用或终止其它程序，防止误连用户原有的数据库。
    for name, port in PORTS.items():
        with socket.socket() as probe:
            if os.name == "nt":
                probe.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            try:
                probe.bind(("127.0.0.1", port))
            except OSError:
                raise RuntimeError(f"{name} 所需的本地端口 {port} 已被占用，请关闭占用程序后重新打开。") from None


def private_directory(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    if os.name != "nt":
        directory.chmod(0o700)
        return
    identity = subprocess.check_output(
        ["whoami", "/user", "/fo", "csv", "/nh"], creationflags=NO_WINDOW
    )
    # whoami 使用系统代码页输出用户名；只提取 ASCII SID，兼容中文用户名。
    sid = user_sid(identity)
    subprocess.run(
        ["icacls", str(directory), "/inheritance:r", "/grant:r",
         f"*{sid}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, creationflags=NO_WINDOW,
    )


def user_sid(identity: bytes) -> str:
    match = re.search(rb"S-\d+(?:-\d+)+", identity)
    if not match:
        raise RuntimeError("无法识别当前 Windows 用户，未创建本机数据库凭据。")
    return match.group().decode("ascii")


def needs_path_alias(path: Path, encoding: str = "mbcs") -> bool:
    try:
        str(path).encode(encoding)
        return False
    except UnicodeEncodeError:
        return True


def normal_windows_path(value: str) -> str:
    # Tauri 返回的规范路径可能带 Win32 扩展前缀；JRE 17 无法据此定位 jimage。
    if value.startswith("\\\\?\\UNC\\"):
        return "\\\\" + value[8:]
    if value.startswith("\\\\?\\"):
        return value[4:]
    return value


def load_secrets(directory: Path) -> dict:
    path = directory / "secrets.json"
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    # 已有数据但丢失凭据时不能生成新密钥覆盖现场。
    if (directory / "mysql").exists():
        raise RuntimeError("本机数据库凭据缺失，请恢复 secrets.json 备份；程序不会重建已有数据库。")
    value = {name: secrets.token_hex(32) for name in ("root", "database", "redis", "agent", "token")}
    write_json(path, value)
    return value


def verify_schema(previous: dict | None, current_hash: str) -> bool:
    if previous is None:
        return True
    if previous.get("schema_sha256") != current_hash:
        raise RuntimeError("本版本数据库结构与本机数据不兼容，需要经过验证的数据迁移版本；已保留原数据库。")
    return False


def migration_plan(previous_hash: str, target_hash: str, steps: list[dict]) -> list[dict]:
    """只沿发布包声明的精确结构版本迁移，拒绝未知来源和循环。"""
    plan = []
    visited = set()
    while previous_hash != target_hash:
        candidates = [step for step in steps if step.get("from") == previous_hash]
        if previous_hash in visited or len(candidates) != 1:
            raise RuntimeError("没有适用于本机数据库的唯一迁移路径，已保留原库。")
        visited.add(previous_hash)
        step = candidates[0]
        plan.append(step)
        previous_hash = step["to"]
    return plan


class Services:
    def __init__(self, runtime: Path, data: Path):
        self.runtime = Path(normal_windows_path(str(runtime.resolve())))
        self.data = Path(normal_windows_path(str(data.resolve())))
        self.children: dict[str, subprocess.Popen] = {}
        self.logs = []
        self.stop = threading.Event()
        self.secrets: dict = {}
        self.mysql_ready = False
        self.ready = False
        self.aliases: list[tuple[str, str]] = []
        self.mysql_home = self.data
        self.mysql_root = self.runtime / "mysql"

    def mysql_path(self, path: Path) -> Path:
        """MySQL 限制路径必须兼容 ANSI 代码页；必要时分配当前登录会话的临时路径。"""
        if os.name != "nt" or not needs_path_alias(path):
            return path
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.DefineDosDeviceW.argtypes = (ctypes.c_uint32, ctypes.c_wchar_p, ctypes.c_wchar_p)
        kernel.DefineDosDeviceW.restype = ctypes.c_int
        kernel.QueryDosDeviceW.argtypes = (ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_uint32)
        kernel.QueryDosDeviceW.restype = ctypes.c_uint32
        kernel.GetLogicalDrives.restype = ctypes.c_uint32
        occupied = kernel.GetLogicalDrives()
        for code in range(ord("Z"), ord("D") - 1, -1):
            if occupied & (1 << (code - ord("A"))):
                continue
            name = chr(code) + ":"
            buffer = ctypes.create_unicode_buffer(32768)
            if kernel.QueryDosDeviceW(name, buffer, len(buffer)):
                continue
            target = "\\??\\" + str(path.resolve())
            # RAW_TARGET_PATH | NO_BROADCAST_SYSTEM；不创建磁盘、服务或系统配置。
            if kernel.DefineDosDeviceW(0x1 | 0x8, name, target):
                self.aliases.append((name, target))
                return Path(name + "\\")
        raise RuntimeError("当前系统无法为数据库准备兼容中文目录的临时路径，请释放一个未使用的盘符后重试。")

    def release_aliases(self) -> None:
        if not self.aliases:
            return
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.DefineDosDeviceW.argtypes = (ctypes.c_uint32, ctypes.c_wchar_p, ctypes.c_wchar_p)
        kernel.DefineDosDeviceW.restype = ctypes.c_int
        for name, target in reversed(self.aliases):
            # 精确移除本次创建的映射，绝不移除其它程序的盘符。
            kernel.DefineDosDeviceW(0x1 | 0x2 | 0x4 | 0x8, name, target)
        self.aliases.clear()

    def status(self, phase: str, message: str) -> None:
        write_json(self.data / "status.json", {"phase": phase, "message": message})

    def start(self, name: str, args: list, *, cwd: Path | None = None, env: dict | None = None):
        log = (self.data / "logs" / f"{name}.log").open("ab")
        self.logs.append(log)
        child = subprocess.Popen(
            [str(arg) for arg in args], cwd=cwd or self.data, env=env,
            stdin=subprocess.DEVNULL, stdout=log, stderr=log, creationflags=NO_WINDOW,
        )
        self.children[name] = child
        return child

    def wait_for(self, name: str, probe, timeout: int = 120) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.stop.is_set():
                raise RuntimeError("启动已取消。")
            if self.children[name].poll() is not None:
                raise RuntimeError(f"{name} 服务启动失败（退出码 {self.children[name].returncode}），请查看 {self.data / 'logs' / (name + '.log')}。")
            try:
                if probe():
                    return
            except (OSError, ValueError, subprocess.SubprocessError):
                pass
            self.stop.wait(1)
        raise RuntimeError(f"等待 {name} 就绪超时，请查看 {self.data / 'logs'}。")

    def mysql(self, sql: str, *, authenticated: bool = True, timeout: int = 120) -> str:
        args = [str(self.mysql_root / "bin/mysql.exe")]
        if authenticated:
            args.append("--defaults-extra-file=mysql-client.cnf")
        else:
            args += ["--no-defaults", "--user=root", "--skip-password"]
        args += ["--protocol=TCP", "--host=127.0.0.1", f"--port={PORTS['mysql']}",
                 "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
        result = subprocess.run(args, input=sql.encode("utf-8"), capture_output=True,
                                cwd=self.mysql_home, timeout=timeout, creationflags=NO_WINDOW)
        if result.returncode:
            # SQL 错误可能回显含凭据的语句，不能写入日志或 UI。
            raise RuntimeError("本机数据库操作失败；未覆盖已有数据。")
        return result.stdout.decode("utf-8", errors="replace").strip()

    def mysql_probe(self) -> bool:
        try:
            return self.mysql("SELECT 1;", timeout=3) == "1"
        except RuntimeError:
            try:
                return self.mysql("SELECT 1;", authenticated=False, timeout=3) == "1"
            except RuntimeError:
                return False

    @staticmethod
    def http_ready(port: int, route: str, *, java: bool = False) -> bool:
        # 环回请求不经过用户的 HTTP 代理。
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(f"http://127.0.0.1:{port}{route}", timeout=2) as response:
            body = json.load(response)
            return body.get("code") == 200 if java else response.status == 200

    def bootstrap_mysql(self, schema_hash: str, first: bool) -> None:
        cfg = self.data / "mysql-client.cnf"
        cfg.write_text(f"[client]\nuser=root\npassword={self.secrets['root']}\n", encoding="utf-8")
        mysql_data = self.data / "mysql"
        mysql_cwd = self.mysql_home / "mysql"
        try:
            basedir = os.path.relpath(self.mysql_root, mysql_cwd)
        except ValueError:
            basedir = str(self.mysql_root)
        if not mysql_data.exists():
            self.status("starting", "首次运行：正在初始化本机数据库…")
            mysql_data.mkdir()
            result = subprocess.run(
                [str(self.mysql_root / "bin/mysqld.exe"), "--no-defaults", "--no-monitor", "--initialize-insecure",
                 f"--basedir={basedir}", "--datadir=."],
                cwd=mysql_cwd, capture_output=True, timeout=180, creationflags=NO_WINDOW,
            )
            if result.returncode:
                (self.data / "logs/mysql-initialize.log").write_bytes(result.stdout + result.stderr)
                raise RuntimeError(f"本机数据库初始化失败（退出码 {result.returncode}），请查看 logs/mysql-initialize.log；已保留现场数据。")
        # 由桌面管理器负责生命周期，禁用 Windows MySQL 自带的第二个监控进程。
        self.start("mysql", [self.mysql_root / "bin/mysqld.exe", "--no-defaults", "--no-monitor",
                             f"--basedir={basedir}", "--datadir=.",
                             "--bind-address=127.0.0.1", f"--port={PORTS['mysql']}", "--mysqlx=OFF",
                             "--local-infile=OFF", "--character-set-server=utf8mb4",
                             "--collation-server=utf8mb4_unicode_ci", "--console"], cwd=mysql_cwd)
        self.wait_for("mysql", self.mysql_probe)
        try:
            self.mysql("SELECT 1;")
        except RuntimeError:
            self.mysql(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{self.secrets['root']}';",
                       authenticated=False)
        self.mysql_ready = True
        if first:
            count = self.mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dkd';")
            if count != "0":
                raise RuntimeError("发现未完成初始化或缺少版本标记的本机数据库。为保护数据，已停止初始化，请备份后恢复。")
            self.mysql((self.runtime / "schema.sql").read_text(encoding="utf-8-sig"), timeout=180)
            for host in ("localhost", "127.0.0.1"):
                self.mysql(f"CREATE USER IF NOT EXISTS 'lingxi'@'{host}' IDENTIFIED BY '{self.secrets['database']}';"
                           f"GRANT ALL ON dkd.* TO 'lingxi'@'{host}';")
            write_json(self.data / "database.json", {"schema_sha256": schema_hash})

    def run(self) -> None:
        private_directory(self.data)
        (self.data / "logs").mkdir(exist_ok=True)
        self.status("starting", "正在检查本机运行环境…")
        ensure_ports_available()
        self.secrets = load_secrets(self.data)
        self.mysql_home = self.mysql_path(self.data)
        self.mysql_root = self.mysql_path(self.runtime / "mysql")
        # Git 在不同 Windows 构建机上可能转换换行符，不应被当作结构迁移。
        schema_text = (self.runtime / "schema.sql").read_text(encoding="utf-8-sig")
        schema_hash = hashlib.sha256(schema_text.encode("utf-8")).hexdigest()
        marker = self.data / "database.json"
        previous = json.loads(marker.read_text(encoding="utf-8")) if marker.exists() else None
        if (self.data / "migration-in-progress.json").exists():
            raise RuntimeError("上次数据库迁移未完成，请使用 backups 中的备份恢复；为保护数据不会重复执行迁移。")
        plan = []
        if previous and previous.get("schema_sha256") != schema_hash:
            manifest = json.loads((self.runtime / "migrations/manifest.json").read_text(encoding="utf-8-sig"))
            plan = migration_plan(previous["schema_sha256"], schema_hash, manifest["steps"])
            first = False
        else:
            first = verify_schema(previous, schema_hash)
        self.bootstrap_mysql(schema_hash, first)
        if plan:
            self.migrate(plan, schema_hash)

        self.status("starting", "正在启动本机缓存服务…")
        redis_data = self.data / "redis"
        redis_data.mkdir(exist_ok=True)
        redis_config = self.data / "redis.conf"
        redis_config.write_text(
            f'bind 127.0.0.1\nport {PORTS["redis"]}\nprotected-mode yes\n'
            f'requirepass {self.secrets["redis"]}\ndir "redis"\n'
            'save ""\nappendonly no\n', encoding="utf-8",
        )
        # MSYS 版 Redis 使用 POSIX 路径；相对路径同时兼容盘符、空格及中文目录。
        self.start("redis", [self.runtime / "redis/redis-server.exe", "redis.conf"])
        self.wait_for("redis", self.redis_probe)

        self.status("starting", "正在启动 AI 服务…")
        # 安装包不复制开发机 .env；本机可选配置保存在数据目录。
        agent_env = os.environ.copy()
        custom = self.data / "agent-settings.json"
        if custom.exists():
            agent_env.update({str(k): str(v) for k, v in json.loads(custom.read_text(encoding="utf-8")).items()})
        agent_env.update(AGENT_SERVICE_API_KEY=self.secrets["agent"], HOST="127.0.0.1",
                         PORT=str(PORTS["agent"]), DEBUG="false", PYTHONUTF8="1",
                         AGENT_TOOL_BASE_URL=f'http://127.0.0.1:{PORTS["java"]}')
        self.start("agent", [self.runtime / "python/python.exe", "-X", "utf8", "-m", "app.run"],
                   cwd=self.runtime / "agent", env=agent_env)
        self.wait_for("agent", lambda: self.http_ready(PORTS["agent"], "/readyz"), timeout=180)

        self.status("starting", "正在启动业务服务，首次运行可能需要几分钟…")
        java_env = os.environ.copy()
        # 敏感值通过环境传递，不出现在进程命令行中。
        java_env.update(DB_PASSWORD=self.secrets["database"], REDIS_PASSWORD=self.secrets["redis"],
                        AGENT_SERVICE_API_KEY=self.secrets["agent"], TOKEN_SECRET=self.secrets["token"],
                        RUOYI_PROFILE=str(self.data / "uploads"))
        config = {
            "lingxi.desktop.enabled": True,
            "lingxi.desktop.data-dir": str(self.data),
            "lingxi.desktop.public-base-url": f'http://127.0.0.1:{PORTS["java"]}',
            "server.address": "127.0.0.1", "server.port": PORTS["java"],
            "spring.data.redis.host": "127.0.0.1", "spring.data.redis.port": PORTS["redis"],
            "spring.datasource.druid.master.url": (
                f'jdbc:mysql://127.0.0.1:{PORTS["mysql"]}/dkd?useUnicode=true&characterEncoding=utf8'
                '&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8&allowMultiQueries=true'),
            "spring.datasource.druid.master.username": "lingxi",
            "spring.datasource.druid.stat-view-servlet.enabled": False,
            "agent.base-url": f'http://127.0.0.1:{PORTS["agent"]}',
            "video.base-url": f'http://127.0.0.1:{PORTS["agent"]}',
            "spring.devtools.restart.enabled": False,
            "logging.config": (self.runtime / "logback.xml").as_uri(),
        }
        # JSON 在命令行属性之后、application.yml 之前生效，覆盖开发机连接地址。
        java_env["SPRING_APPLICATION_JSON"] = json.dumps(config)
        self.start("java", [self.runtime / "java/bin/java.exe", "-Xms128m", "-Xmx1024m",
                            "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                            "-jar", self.runtime / "backend/lingXi-admin.jar"], env=java_env)
        self.wait_for("java", lambda: self.http_ready(PORTS["java"], "/captchaImage", java=True), timeout=240)
        self.ready = True
        self.status("ready", "本机服务已就绪")
        while not self.stop.wait(2):
            for name, child in self.children.items():
                if child.poll() is not None:
                    raise RuntimeError(f"{name} 服务意外退出，请重新打开应用；日志位于 {self.data / 'logs'}。")

    def redis_probe(self) -> bool:
        # RESP 鉴权检查，不把密码交给 redis-cli 的进程参数。
        password = self.secrets["redis"].encode("ascii")
        with socket.create_connection(("127.0.0.1", PORTS["redis"]), timeout=2) as connection:
            connection.sendall(b"*2\r\n$4\r\nAUTH\r\n$" + str(len(password)).encode() + b"\r\n" + password + b"\r\n")
            return connection.recv(1024).startswith(b"+OK")

    def migrate(self, plan: list[dict], target_hash: str) -> None:
        root = (self.runtime / "migrations").resolve()
        scripts = []
        for step in plan:
            path = (root / step["file"]).resolve()
            if not path.is_relative_to(root) or hashlib.sha256(path.read_bytes()).hexdigest() != step["sha256"]:
                raise RuntimeError("数据库迁移脚本校验失败，已保留原库。")
            scripts.append(path.read_text(encoding="utf-8-sig"))
        self.status("starting", "正在备份并升级本机数据库，请勿关闭应用…")
        backup_dir = self.data / "backups"
        backup_dir.mkdir(exist_ok=True)
        backup = backup_dir / f"before-upgrade-{time.time_ns()}.sql"
        with backup.open("wb") as output:
            result = subprocess.run(
                [str(self.mysql_root / "bin/mysqldump.exe"),
                 "--defaults-extra-file=mysql-client.cnf", "--protocol=TCP",
                 "--host=127.0.0.1", f"--port={PORTS['mysql']}", "--single-transaction",
                 "--routines", "--events", "--set-gtid-purged=OFF", "--databases", "dkd"],
                cwd=self.mysql_home, stdout=output, stderr=subprocess.DEVNULL, timeout=600, creationflags=NO_WINDOW,
            )
        if result.returncode or backup.stat().st_size == 0:
            raise RuntimeError("升级前的数据库备份失败，未执行任何迁移。")
        pending = self.data / "migration-in-progress.json"
        write_json(pending, {"backup": backup.name, "target": target_hash})
        # MySQL DDL 不能整体回滚。失败时保留备份与标记，禁止自动重试部分执行的脚本。
        for sql in scripts:
            self.mysql("USE dkd;\n" + sql, timeout=600)
        write_json(self.data / "database.json", {"schema_sha256": target_hash})
        pending.unlink()

    def shutdown(self) -> None:
        self.stop.set()
        # 先停止业务进程，最后正常关闭 MySQL，保证数据库刷盘完成。
        for name in ("java", "agent", "redis"):
            child = self.children.get(name)
            if child and child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=5)
        mysql = self.children.get("mysql")
        if mysql and mysql.poll() is None:
            try:
                self.mysql("SHUTDOWN;", timeout=15)
            except (RuntimeError, subprocess.SubprocessError):
                # 用户可能在首次初始化完成、设置 root 密码之前关闭窗口。
                if not self.mysql_ready:
                    try:
                        self.mysql("SHUTDOWN;", authenticated=False, timeout=5)
                    except (RuntimeError, subprocess.SubprocessError):
                        pass
            try:
                mysql.wait(timeout=30)
            except subprocess.TimeoutExpired:
                mysql.kill()
                mysql.wait(timeout=5)
        for log in self.logs:
            log.close()
        self.release_aliases()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--smoke-test", action="store_true")
    args = parser.parse_args()
    services = Services(args.runtime, args.data)

    # 桌面进程退出（包括崩溃）会关闭管道，管理器随即清理所有私有服务。
    def watch_parent():
        # 直接读取 OS 管道，避免解释器退出时 daemon 持有 stdin 缓冲锁。
        os.read(sys.stdin.fileno(), 1)
        services.stop.set()

    threading.Thread(target=watch_parent, daemon=True).start()
    if args.smoke_test:
        def finish_smoke():
            while not services.stop.wait(1):
                if services.ready:
                    services.stop.set()
        threading.Thread(target=finish_smoke, daemon=True).start()
    try:
        services.run()
        return 0
    except Exception as error:
        services.data.mkdir(parents=True, exist_ok=True)
        services.status("error", str(error) if isinstance(error, RuntimeError) else "本机服务启动失败，请检查数据目录和运行日志。")
        return 1
    finally:
        services.shutdown()


if __name__ == "__main__":
    sys.exit(main())
