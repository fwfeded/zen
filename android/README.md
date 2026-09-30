# 禅 Android 源码

原生 Kotlin + Jetpack Compose，Android 10+。版本 0.5.5；六个内置主题及构建资源均在此目录中。

- [构建与签名指南](../BUILDING.md)
- [代码结构](../docs/ARCHITECTURE.md)
- [运行时依赖授权](../docs/DEPENDENCIES.md)
- [素材来源与审查](../docs/ASSET_AUDIT.md)
- [GPL-3.0-only](../LICENSE) 与 [第三方例外](../NOTICE)

使用 JDK 17 与 Android SDK 35，在本目录运行 `./gradlew :app:assembleDebug :app:lintDebug`；Windows 使用 `.\gradlew.bat`。

本目录不包含个人环境、私钥、设备记录、额外主题卡或历史网页原型。构建不需要运行旧原型导出工具。
