# 首次源码公开验证

日期：2026-09-30。对应应用版本：0.5.5（versionCode 30）。

## 文件范围

从本地工程按白名单复制构建配置、Wrapper、Manifest、Kotlin、资源与发布清单工具，共 245 个 Android 工程文件。没有复制环境配置、构建缓存、APK、签名私钥、用户数据、外部主题卡、历史网页原型、本地测试与夹具。

在复制后的独立目录执行构建，未使用原工程 `build/` 产物。已安装 SDK、JDK 和全局依赖缓存仍被复用，因此这是干净源码目录验证，不是完全空缓存联网环境验证。

## 构建结果

```text
:app:assembleDebug :app:assembleRelease :app:lintDebug
BUILD SUCCESSFUL in 56s
96 actionable tasks: 96 executed
```

Lint：0 错误，69 条现有警告：

| 检查项 | 数量 |
| --- | ---: |
| ApplySharedPref | 1 |
| ImplicitSamInstance | 1 |
| UnusedAttribute | 1 |
| GradleDependency | 7 |
| DataExtractionRules | 1 |
| UnusedResources | 28 |
| UseKtx | 28 |
| ClickableViewAccessibility | 1 |
| RtlHardcoded | 1 |

这些警告没有被隐藏或全局禁用。本轮以整理并公开现有源码为范围，没有把接口弃用、依赖升级或无障碍改进混入发布快照；后续应按影响逐项处理。

## 授权与资源

57 个运行时组件的版本与许可声明已记录；无缺失许可的待核实项。字体内容匹配上游 Git blob。六个内置主题文案库键已检查，额外主题卡与图像不在发布目录。

## 与已有测试的关系

此前 0.5.5 已做 154 项单元测试、13 项模拟器更新测试、9 项发布工具测试及真实在线覆盖升级。这些是已有版本的本地证据；按此次文件范围，本地测试源码与主题卡夹具不上传，因此不称作公众可从当前仓库直接复现的完整测试套件。

本轮未重新生成或替换公开 APK，未声称源码构建与官方签名包逐字节相同。不同签名及更新地址配置会导致结果不同。构建说明见 [BUILDING.md](../BUILDING.md)。
