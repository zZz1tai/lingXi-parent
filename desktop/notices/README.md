# 桌面运行环境来源

运行环境版本、下载地址与 SHA-256 见安装目录 `runtime/runtime-lock.json`。Python、Temurin Java 和 MySQL 原包中的许可证及第三方声明随原目录保留；Python 依赖的许可证位于各自的 `.dist-info` 目录。

Redis Windows 便携包另包含 MSYS2、OpenSSL 和 GCC 运行库，本目录补充其上游许可文本。对应源代码与构建配方：

- Redis 7.2.12：<https://github.com/redis/redis/tree/7.2.12>，BSD 许可见 `Redis-COPYING.txt`。
- Redis Windows 构建配方：<https://github.com/redis-windows/redis-windows/tree/7.2.12>。
- MSYS2 运行库源码及构建配方：<https://github.com/msys2/msys2-runtime>、<https://github.com/msys2/MSYS2-packages>；许可见 `MSYS2-COPYING.txt` 和 `GPL-3.0.txt`。
- GCC 运行库：<https://gcc.gnu.org/git.html>、<https://github.com/msys2/MINGW-packages>；许可及运行库例外见 `GPL-3.0.txt` 和 `GCC-Runtime-Library-Exception.txt`。
- OpenSSL：<https://github.com/openssl/openssl>，许可见 `Apache-2.0.txt`。
- Microsoft VC 运行库采用应用私有部署；来源为构建机 Visual C++ Redistributable，说明见 <https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files>。

这些组件保留各自许可证，项目本身的许可证不替代第三方条款。
