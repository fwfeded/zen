# 从源码构建禅

[项目首页](README.md) · [代码结构](docs/ARCHITECTURE.md) · [许可与来源](THIRD_PARTY_NOTICES.md)

此指南适用于本次公开的 0.5.5 源码。不要使用历史 `v0.5.5` 标签的自动源码压缩包，它是在源码公开前生成的文档快照；使用当前仓库或 Release 说明中标出的完整源码提交。

## 环境

| 工具 | 本次配置 |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | Android 15 / API 35 |
| Android SDK Build-Tools | 35.0.0 |
| Gradle | Wrapper 固定 8.11.1，包含分发文件 SHA-256 校验 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose 编译插件 | 2.1.20 |
| Python | 仅发布清单工具需要，建议 3.10+；构建 APK 不需要 |

Android Studio 可安装 SDK 和选择 JDK 17，也可通过 Android SDK 命令行工具配置。让 `JAVA_HOME` 指向 JDK 17，`ANDROID_HOME` 指向自己的 SDK；或者由 Android Studio 在 `android/local.properties` 中生成本机 SDK 路径。不要将该文件提交。

首次构建需要能访问 Google Maven、Maven Central、Gradle 分发服务和插件仓库。没有私有 Maven、账号、天气密钥或远程主题卡作为构建前提。

## 获取源码

```sh
git clone https://github.com/fwfeded/zen.git
cd zen/android
```

也可从完整源码提交下载 ZIP 解压，并用 Android Studio 打开 `android/` 目录。所有必要的内置主题资源、字体与 Wrapper JAR 已包含，不需要从历史 HTML 原型重新导出。

## 编译与静态检查

Windows PowerShell，在 `android/` 中执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:lintDebug
```

输出位置（相对 `android/`）：

- Debug 安装包：`app/build/outputs/apk/debug/app-debug.apk`
- Lint 报告：`app/build/reports/lint-results-debug.html`

Release 构建：

```powershell
.\gradlew.bat :app:assembleRelease
```

对应输出 `app/build/outputs/apk/release/app-release.apk`，开启代码压缩和资源收缩。Linux / macOS 将命令中的 `.\gradlew.bat` 换成 `./gradlew`。

## 安装与签名

当前个人测试构建配置中，Debug 和 Release 都使用**构建者本机生成的 debug 签名**。仓库没有维护者私钥。相同包名并不意味着可以覆盖官方 APK；签名不同时 Android 会拒绝更新。

建议用没有安装正式数据的独立模拟器测试自己的构建。不要为安装自编译包而直接卸载日常使用中的禅，卸载会删除本地记录。需要自己长期分发时，在个人分支配置自己的 release 密钥并维护稳定签名，密钥和密码不得提交。

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.zen.launcher/.MainActivity
```

目标设备需要 Android 10 / API 29 或以上。

## 更新地址与衍生版本

源码默认 `UPDATE_MANIFEST_URL` 为空，不自动连到维护者的更新频道。复现官方地址配置时可执行：

```powershell
.\gradlew.bat :app:assembleRelease '-PzenUpdateManifestUrl=https://github.com/fwfeded/zen/releases/latest/download/latest.json'
```

即使填入官方地址，自签名 APK 也不能绕过签名验证安装官方更新。分发自己的修改版时，应使用自己的包名／签名与更新服务，并明确标识来源；许可证不会授予维护者的账号或私钥。

`tools/prepare_github_release.py` 和 `tools/prepare_update_site.py` 可通过 `--help` 查看参数，输入真实 APK、说明、`aapt` 与 `apksigner` 路径后生成发布文件。工具默认校验官方个人渠道的**公开证书指纹**；自建渠道须传入自己的预期指纹（`--expected-cert`），并检查工具中的包名与频道约束。指纹不是签名私钥。

## 验证范围

本次已在 Windows 的干净源码目录构建 Debug、Release 并运行 Lint，96 项 Gradle 任务成功。使用了已安装 SDK 与依赖缓存，未宣称验证完全空缓存的首次联网构建，也未验证 Linux／macOS 实机。

Lint 为 0 错误、69 条警告，详见 [验证记录](docs/SOURCE_VERIFICATION.md)。按当前发布范围，本地单元测试、仪器测试及含主题卡的测试夹具没有上传；运行空测试任务不能视为回归测试通过。

## 常见构建问题

| 现象 | 检查 |
| --- | --- |
| SDK location not found | 设置自己的 `ANDROID_HOME` 或 `local.properties` |
| Java 版本不兼容 | 检查 `java -version` 与 Android Studio 的 Gradle JDK 是否为 17 |
| 依赖下载超时 | 检查上述仓库的网络连通性；不要替换成来历不明的依赖包 |
| 安装签名不一致 | 改用独立模拟器，或使用与已安装包一致的自有密钥 |
| 更新页未配置地址 | 默认行为；按需要设置自己的 HTTPS 清单 |
| 旧的源码压缩包里没有 android 目录 | 使用当前仓库或 Release 中明确标出的完整源码提交 |
