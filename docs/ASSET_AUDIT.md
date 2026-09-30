# 素材来源与发布审查

审查对象：本次公开的 0.5.5 源码目录。此文件记录来源与范围，不是独立律师出具的权利保证。

| 类别 | 发布内容与来源 | 授权处理 |
| --- | --- | --- |
| 六个内置主题 | `assets/scenes/*.svg`、`motifs.json`，项目开发过程中创建的程序化场景与图形 | 随项目采用 GPL-3.0-only；SVG/JSON 可直接编辑 |
| 原创短句 | `assets/copy.json`；检查键仅有 water、forest、dawn、dusk、paper、night | 随项目采用 GPL-3.0-only，不将其标为动漫原台词 |
| 传统诗词 | `ThemeQuotes.kt`、`PoetryLibrary.kt` 中保留的古代诗句与作者／篇名／来源链接 | 古代原文不主张新增版权；不包含现代译文或现代注释 |
| 中文字体 | Noto Serif CJK SC Regular 2.003，Adobe 2017–2024 | 原文件未修改，独立遵守 SIL OFL 1.1 |
| 项目截图 | README 与主题页的六张实际应用截图，使用示例天气 | 不含私人通知、联系人或真实待办；字体仍依原许可 |
| Gradle Wrapper | Gradle 8.11.1 的 Wrapper 脚本与 JAR | 保留上游 Apache-2.0 许可和声明 |

## 字体验证

字体重命名为 `zen_serif.otf`，文件内容与 [Noto 上游 Regular 文件](https://github.com/notofonts/noto-cjk/blob/main/Serif/OTF/SimplifiedChinese/NotoSerifCJKsc-Regular.otf) 的 Git blob 完全一致；这只是资源文件名改变，字体内部名称未修改。

- 版本：2.003；字体内部版权：© 2017–2024 Adobe。
- 大小：24,543,080 字节。
- Git blob SHA-1：`cba8a4783cc38574ac7cda52cae7d9b4241c07a5`。
- SHA-256：`2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca`。
- 完整上游许可：[Noto-Serif-CJK-OFL.txt](../licenses/Noto-Serif-CJK-OFL.txt)。嵌入应用的字体许可文本也保留。

## 未发布的内容

没有上传外部 `.zentheme` 文件、主题卡 ZIP、江南／山水额外底画、夏目主题图片与台词库，或含这些内容的测试夹具。源码保留主题卡解析与兼容逻辑，不代表附带相应主题包。

文案结构与全部拟公开文本已扫描私人路径、常见凭据格式及非公开主题标识；上传使用明确文件清单。不能将自动扫描视为对所有未知权利问题的保证；若收到具体权利线索，应核对相应文件并处理。
