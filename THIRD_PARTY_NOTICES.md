# 第三方组件、素材与服务

项目自有代码按 [GPL-3.0-only](LICENSE) 发布，第三方内容保持各自的许可证。本文件与 [NOTICE](NOTICE) 一起说明范围，GPL 不替代字体、依赖或服务的原有条款。

## 应用运行时依赖

从此次构建的 `releaseRuntimeClasspath` 导出 **57 个实际解析的组件**，核对各版本 Maven POM 的许可声明；`com.google.guava:listenablefuture:1.0` 的声明继承自 `guava-parent:26.0-android`。这些组件的上游许可声明均为 Apache-2.0。

主要组件包括 AndroidX Activity、Compose UI／Material 3、Lifecycle、SavedState、Core、Kotlin 标准库、kotlinx.coroutines、kotlinx.serialization，以及其传递依赖。完整版本、POM 来源和文件哈希见：

- [逐项依赖授权清单](docs/DEPENDENCIES.md)
- [机器可读版本与 SHA-256 清单](docs/DEPENDENCIES.json)
- [Apache-2.0 全文](licenses/Apache-2.0.txt)

源码仓库不包含这些依赖的缓存副本，由 Gradle 从 Google Maven／Maven Central 下载。重新分发二进制或修改依赖时，应继续保留适用的上游许可和 NOTICE，不只复制项目 GPL 文本。清单记录本次已解析版本，未来升级依赖应重新核对。

## 构建工具

Gradle Wrapper 8.11.1、Android Gradle Plugin 8.9.2、Kotlin／Compose 插件 2.1.20 使用各自上游许可；项目不重新授权这些工具。Wrapper 脚本保留原版权头，Gradle 原分发版的 [LICENSE](licenses/Gradle-LICENSE.txt) 与 [NOTICE](licenses/Gradle-NOTICE.txt) 一并提供，分发版中包含的其他组件以其原文为准。

构建文件保留测试依赖声明，但本次不分发本地测试源码／夹具；JUnit 等测试组件不属于上述运行时 APK 依赖清单。

## 字体、场景与文字

Noto Serif CJK SC Regular 2.003：Copyright © 2017–2024 Adobe，SIL Open Font License 1.1。完整许可见 [字体许可](licenses/Noto-Serif-CJK-OFL.txt)，原文件未修改，不将字体本身改为 GPL。

六个内置主题的项目场景与短句随 GPL 发布；古代诗词原文保留作者与篇名，不主张对公有领域原文的新版权。没有外部主题卡或动漫图片、台词包随此次发布。详细来源、哈希与排除范围见 [素材审查](docs/ASSET_AUDIT.md)。

## 在线服务与数据

天气来自 [Open-Meteo](https://open-meteo.com/)。其 [数据许可](https://open-meteo.com/en/licence) 为 CC BY 4.0，使用需署名并链接许可；其免费托管 API 另有 [非商业服务条款](https://open-meteo.com/en/terms)。代码允许商用不等于免费天气接口也允许任何商业调用，商用衍生版本须自行取得适合的服务方案或替换数据源。

城市名称还可能使用 Android Geocoder 的设备服务。GitHub 用于源码、发布文件与更新下载，服务按其条款运行。它们不是被项目许可证重新授权的代码或资源。数据流见 [隐私说明](PRIVACY.md)。

## 审查边界

本次完成构建文件、实际运行时依赖、字体、内置素材与发布文件清单的来源检查；未声称完成所有司法辖区的法律审查或第三方依赖的安全审计。发现具体来源或许可问题，请提供文件路径与依据，通过 [安全反馈](SECURITY.md) 或相应 Issue 联系维护者。
