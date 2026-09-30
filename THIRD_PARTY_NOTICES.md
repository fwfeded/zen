# 第三方组件与数据

本页用于说明 0.5.5 的主要组件、数据来源与相应上游入口，不表示这些项目为禅背书，也不替代各依赖的完整许可证。

| 组件／服务 | 使用方式 | 上游与说明 |
| --- | --- | --- |
| AndroidX / Jetpack Compose | 原生界面、生命周期与平台兼容组件 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) · Apache-2.0 |
| Kotlin、协程、序列化 | 应用语言、后台任务与本地结构化数据 | [Kotlin](https://github.com/JetBrains/kotlin) · [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) · [Serialization](https://github.com/Kotlin/kotlinx.serialization) · Apache-2.0 |
| Noto Serif CJK SC | 中文衬线字体 | [Noto CJK](https://github.com/notofonts/noto-cjk) · SIL Open Font License 1.1；应用“关于禅”内包含许可证正文 |
| Open-Meteo | 天气、日出日落与城市搜索数据 | [Open-Meteo](https://open-meteo.com/) · 数据署名 CC BY 4.0；本项目使用非商业测试接口 |
| GitHub Releases | 安装包、版本清单与校验文件分发 | [GitHub](https://github.com/)；不是用户记录存储服务 |

内置字体的许可证副本见 [licenses/OFL-1.1.txt](licenses/OFL-1.1.txt)。天气数据的更新时间和联网边界见 [隐私说明](PRIVACY.md)。

本次只发布最初六个原生主题。外部主题卡、额外主题画作及其合集不在仓库和 Release 中。
