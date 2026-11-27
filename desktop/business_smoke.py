"""仅对桌面自检的临时环境验证登录、区域筛选和真实 MySQL 分页。"""

import json
import base64
from pathlib import Path
import re
import socket
import urllib.parse
import urllib.request
import urllib.error
import uuid as uuid_module

from launcher import PORTS


def api_json(path: str, *, token: str | None = None, payload: dict | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(
        f'http://127.0.0.1:{PORTS["java"]}{path}', headers=headers,
        data=json.dumps(payload).encode("utf-8") if payload is not None else None,
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=15) as response:
        return json.load(response)


def captcha_answer(password: str, uuid: str) -> str:
    # 只读取自检私有 Redis 中刚生成的验证码，不修改正式登录配置。
    with socket.create_connection(("127.0.0.1", PORTS["redis"]), timeout=5) as connection:
        with connection.makefile("rb") as stream:
            def command(*arguments):
                parts = [str(value).encode("utf-8") for value in arguments]
                connection.sendall(b"*" + str(len(parts)).encode() + b"\r\n" + b"".join(
                    b"$" + str(len(part)).encode() + b"\r\n" + part + b"\r\n" for part in parts))
                line = stream.readline()
                if line.startswith(b"+"):
                    return line[1:-2]
                if line.startswith(b"$") and int(line[1:-2]) >= 0:
                    value = stream.read(int(line[1:-2]))
                    if stream.read(2) == b"\r\n":
                        return value
                raise RuntimeError("读取自检验证码失败。")

            command("AUTH", password)
            return str(json.loads(command("GET", "captcha_codes:" + uuid)))


def verify_region_queries(database) -> None:
    # 使用仓库登录页已有的初始化值，不新增、硬编码或输出账号凭据。
    login_source = (Path(__file__).resolve().parents[1] / "lingXi-vue/src/views/login.vue").read_text(encoding="utf-8")
    credentials = {}
    for field in ("username", "password"):
        match = re.search(r'\b' + field + r'\s*:\s*"([^"\n]+)"', login_source)
        if not match:
            raise RuntimeError("缺少桌面初始化登录测试所需的默认值。")
        credentials[field] = match.group(1)
    captcha = api_json("/captchaImage")
    if captcha.get("captchaEnabled", True):
        credentials.update(uuid=captcha["uuid"], code=captcha_answer(database.secrets["redis"], captcha["uuid"]))
    login = api_json("/login", payload=credentials)
    if login.get("code") != 200 or not login.get("token"):
        raise RuntimeError("桌面临时环境登录验证失败。")
    verify_desktop_storage(database, login["token"])
    # 临时库中创建两个包含关键词的名称，防止空结果掩盖搜索失效。
    database.mysql("INSERT INTO dkd.tb_region (region_name, create_by) VALUES "
                   "('桌面验收北京区域一', 'desktop-smoke'), ('桌面验收北京区域二', 'desktop-smoke');")
    cases = [
        ({"pageNum": 1, "pageSize": 1}, "SELECT COUNT(*) FROM dkd.tb_region;"),
        ({"pageNum": 1, "pageSize": 10, "regionName": "北京"},
         "SELECT COUNT(*) FROM dkd.tb_region WHERE region_name LIKE '%北京%';"),
        ({"pageNum": 2, "pageSize": 1, "regionName": "桌面验收北京"},
         "SELECT COUNT(*) FROM dkd.tb_region WHERE region_name LIKE '%桌面验收北京%';"),
        ({"pageNum": 1, "pageSize": 10, "regionName": "桌面验收不存在的区域"},
         "SELECT COUNT(*) FROM dkd.tb_region WHERE region_name LIKE '%桌面验收不存在的区域%';"),
    ]
    for parameters, count_sql in cases:
        expected = int(database.mysql(count_sql))
        result = api_json("/manage/region/list?" + urllib.parse.urlencode(parameters), token=login["token"])
        # 项目把 Long 序列化为字符串以避免 JavaScript 丢失精度。
        if result.get("code") != 200 or int(result.get("total", -1)) != expected:
            raise RuntimeError(f"区域列表筛选或分页统计与 MySQL 数据不一致：{parameters}，"
                               f"预期 {expected}，实际 code={result.get('code')!r} total={result.get('total')!r}。")
        rows = result.get("rows", [])
        remaining = max(0, expected - (parameters["pageNum"] - 1) * parameters["pageSize"])
        if len(rows) != min(remaining, parameters["pageSize"]):
            raise RuntimeError("区域列表分页条数不正确。")
        for row in rows:
            if parameters.get("regionName", "") not in row["regionName"] or int(row.get("nodeCount", -1)) < 0:
                raise RuntimeError("区域列表返回的筛选结果或点位统计不正确。")
    database.mysql("DELETE FROM dkd.tb_region WHERE create_by = 'desktop-smoke' "
                   "AND region_name IN ('桌面验收北京区域一', '桌面验收北京区域二');")


def verify_desktop_storage(database, token: str) -> None:
    settings = api_json("/desktop/settings", token=token)["data"]
    if settings["mode"] != "local":
        raise RuntimeError("桌面版未启用本地存储。")
    marker = database.data.parent / "storage-smoke.json"
    image = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def verify(url):
        with opener.open(url, timeout=10) as response:
            if response.read() != image:
                raise RuntimeError("本地文件内容不一致。")
        try:
            opener.open(url + "invalid", timeout=10)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
        else:
            raise RuntimeError("本地文件地址未校验签名。")

    def upload():
        boundary = uuid_module.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="local.png"\r\n'
                'Content-Type: image/png\r\n\r\n').encode() + image + f'\r\n--{boundary}--\r\n'.encode()
        request = urllib.request.Request(f'http://127.0.0.1:{PORTS["java"]}/common/upload', data=body,
            headers={"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary})
        with opener.open(request, timeout=15) as response:
            result = json.load(response)
        if result.get("code") != 200 or not result.get("url", "").startswith(f'http://127.0.0.1:{PORTS["java"]}/desktop/files/'):
            raise RuntimeError("桌面上传未写入本机存储平台。")
        verify(result["url"])
        return result["url"]

    # 搜索密钥可选，桌面设置不再回传、要求或列出旧 OSS 配置。
    security = api_json("/system/security-config", token=token)
    if security.get("code") != 200 or security["data"].get("ossAccessKeyConfigured"):
        raise RuntimeError("读取桌面服务设置失败。")
    request = urllib.request.Request(f'http://127.0.0.1:{PORTS["java"]}/system/security-config', method="PUT",
        data=b"{}", headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    with opener.open(request, timeout=15) as response:
        if json.load(response).get("code") != 200:
            raise RuntimeError("桌面服务设置仍要求填写 OSS。")
    listed = api_json("/system/config/list?configKey=security.oss&pageNum=1&pageSize=100", token=token)
    if listed.get("code") != 200 or int(listed.get("total", -1)) != 0:
        raise RuntimeError("桌面参数列表仍显示旧 OSS 配置。")

    # 私有文本附件不触发 OCR 或真实模型调用，预览必须使用限时签名。
    user_id = str(api_json("/getInfo", token=token)["user"]["userId"])
    session = api_json("/api/ai/sessions?userId=" + user_id, token=token, payload={})["data"]["sessionId"]
    boundary = uuid_module.uuid4().hex
    content = b"desktop private attachment"
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="sessionId"\r\n\r\n{session}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="private.txt"\r\n'
            'Content-Type: text/plain\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(f'http://127.0.0.1:{PORTS["java"]}/api/ai/attachments', data=body,
        headers={"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary})
    with opener.open(request, timeout=15) as response:
        attachment = json.load(response)["data"]
    if int(urllib.parse.parse_qs(urllib.parse.urlparse(attachment["previewUrl"]).query)["expires"][0]) <= 1:
        raise RuntimeError("私有附件未生成临时访问地址。")
    with opener.open(attachment["previewUrl"], timeout=10) as response:
        if response.read() != content:
            raise RuntimeError("私有附件内容不正确。")
    request = urllib.request.Request(f'http://127.0.0.1:{PORTS["java"]}/api/ai/attachments/{attachment["attachmentId"]}?'
        + urllib.parse.urlencode({"sessionId": session}), method="DELETE", headers={"Authorization": "Bearer " + token})
    with opener.open(request, timeout=10) as response:
        if json.load(response).get("code") != 200:
            raise RuntimeError("私有待发送附件删除失败。")
    if marker.exists():
        saved = json.loads(marker.read_text(encoding="utf-8"))
        if settings["storagePath"] != saved["storagePath"]:
            raise RuntimeError("重启后未保留本地存储目录。")
        for url in saved["urls"]:
            verify(url)
    else:
        old_url = upload()
        target = database.data.parent / "新的文件保存目录 with spaces"
        request = urllib.request.Request(f'http://127.0.0.1:{PORTS["java"]}/desktop/settings', method="PUT",
            data=json.dumps({"storagePath": str(target)}).encode(),
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
        with opener.open(request, timeout=15) as response:
            changed = json.load(response)
        if changed.get("code") != 200:
            raise RuntimeError("修改本地文件目录失败。")
        new_url = upload()
        verify(old_url)
        if not any(target.rglob("*.png")):
            raise RuntimeError("新文件未写入修改后的目录。")
        marker.write_text(json.dumps({"storagePath": str(target), "urls": [old_url, new_url]}), encoding="utf-8")
