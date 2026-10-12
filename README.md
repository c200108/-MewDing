# MewDing（萌叮）

一款多功能的移动端桌宠 APP：会动的像素宠物陪你喝水、吃饭、睡觉，记录经期，还有便签、解压小游戏、换装商店和随时间变化的动态场景。

- 安装包：见 [Releases](https://github.com/c200108/MewDing/releases)（每个版本附 APK 和更新内容）
- 更新记录：[更新日志.md](更新日志.md)
- 需求与设计：[需求汇总.md](需求汇总.md)

## 目录

| 目录 | 内容 |
|---|---|
| `android/` | 安卓工程（Kotlin + Jetpack Compose，minSdk 29），说明见 `android/README.md` |
| `美术/` | 像素宠物、食物服装、小游戏、动态背景等美术的生成脚本和素材 |
| `参考图标/` | 图标参考图 |

## 构建

用 Android Studio 打开 `android/`，或在 `android/` 下运行 `gradlew assembleDebug`。
正式签名用的 `keystore.properties` 和签名文件不在仓库里，自己构建 release 需要准备自己的签名。
