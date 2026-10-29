# 萌叮 MewDing

一只像素小宠物陪你按时喝水、吃饭、睡觉的安卓 App（测试版，给亲友试用）。

## 这个仓库是做什么的

- **App 在线更新的下载源**：萌叮「我的 › 关于萌叮 › 在线更新」从这里的 [Releases](https://github.com/c200108/MewDing/releases) 检查并下载新版。每个 Release 附带 `MewDing-<版本>.apk`，说明里写有 `versionCode` 和 `sha256`，App 安装前会校验 sha256 和签名证书。
- `update.json`：最新一版的信息（格式与 GitHub Releases 接口相同），国内网络连不上 GitHub 时 App 通过 jsDelivr 等 CDN 读它。
- 源码和文档（`android/`、`更新日志.md`、`需求汇总.md`）供参考。

## 注意

- **原创美术素材没有公开**：宠物精灵图、表情、场景、插画、音效、音乐等图片和音频都不在仓库里（`android/app/src/main/assets` 和 `res` 里的二进制素材已排除），所以源码**不能直接编译出完整可用的 App**。需要安装请到 Releases 下载 APK。
- 正式签名文件不在仓库里，自己构建的包无法覆盖安装官方发布的版本。
- 素材与代码的使用许可见 App 内「关于萌叮 › 用户协议 / 开源许可」。
