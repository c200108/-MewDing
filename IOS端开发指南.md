# 萌叮 iOS 端开发指南

## 1. 最终技术方案

萌叮 iOS 版采用以下架构：

```text
共享 Kotlin 领域层
        +
Android Jetpack Compose
        +
iOS SwiftUI
        +
Swift WidgetKit / ActivityKit 扩展
```

共享 Kotlin 领域层负责数据模型、宠物模拟、萌点经济、经期预测、提醒排期和小游戏规则。Android 继续使用现有 Jetpack Compose 页面；iOS 使用 SwiftUI 构建主界面，并用 Swift 原生实现系统能力。

Compose Multiplatform 可以作为补充，用于部分普通页面、宠物展示组件或小游戏画布。不要把所有 Android 页面直接搬到 Compose Multiplatform，再用 Swift 补系统功能。当前项目的 Android 依赖已经深入页面、游戏绘制、声音和媒体功能，完整共享 UI 会增加 iOS 输入、键盘、辅助功能和系统交互的适配成本。

Windows 可以完成共享 Kotlin 代码、Android 代码和大部分架构整理。iOS Framework 编译、Xcode 集成、签名、模拟器测试和真机安装必须在 macOS + Xcode 或 macOS CI 环境完成。

## 2. 目标工程结构

```text
shared/
├─ model/          数据结构、序列化模型
├─ domain/         宠物模拟、萌点、经期、提醒排期
├─ game/           小游戏规则和状态机
├─ repository/     统一的数据存储接口
└─ platform/       expect/actual 平台接口

androidApp/
├─ Compose 页面
├─ Android 通知
├─ 前台服务
├─ 悬浮宠物
├─ Android Widget
└─ Android 音频和权限

iosApp/
├─ SwiftUI 页面
├─ UNUserNotificationCenter
├─ WidgetKit
├─ ActivityKit
├─ Keychain
├─ AVFoundation
└─ App Intents
```

共享层不能直接依赖 `android.content.Context`、`android.graphics.Bitmap`、`android.media.MediaPlayer`、`android.net.Uri`、`java.time` 或 Android Compose 类型。

建议抽象以下接口：

```kotlin
interface PetStorage {
    suspend fun load(): AppState
    suspend fun save(state: AppState)
}

interface ReminderScheduler {
    suspend fun schedule(reminders: List<Reminder>)
    suspend fun cancelAll()
}

interface AudioPlayer {
    fun playPetVoice(species: String)
}

interface MediaStore {
    suspend fun importImage(source: ByteArray, extension: String): String
}
```

Android 和 iOS 分别提供平台实现，领域层只处理业务意图，不处理系统 API。

## 3. 可复用程度

| 模块 | 预计复用程度 | 处理方式 |
|---|---:|---|
| 数据模型 | 80%～90% | 继续使用 Kotlin Serialization，移除 Android 依赖 |
| 萌点经济系统 | 90% | 抽到 shared/domain |
| 宠物状态模拟 | 90% | 复用按时间补算逻辑 |
| 经期预测 | 70%～85% | 用 `kotlinx-datetime` 替代 `java.time` |
| 提醒排期计算 | 70%～80% | 共享“下一条提醒”计算，平台分别注册通知 |
| 小游戏规则 | 85%～95% | 共享状态机，分别实现绘制和资源读取 |
| Compose 页面 | 30%～60% | iOS 主要使用 SwiftUI |
| 游戏绘制 | 30%～50% | Android Canvas 和 iOS Canvas 分开适配 |
| 音频 | 20%～40% | Android 使用现有实现，iOS 使用 AVFoundation |
| 便签媒体 | 20%～40% | 图片、录音、播放和文件选择分别实现 |
| 桌面悬浮宠物 | 0% | iOS 改为 Widget、Live Activity 和 App 内宠物 |
| 常驻通知宠物 | 0% | iOS 改为 Live Activity |
| 小组件 | 10%～30% | iOS 使用 WidgetKit 扩展 |
| 桌面图标切换 | 60%～80% | iOS 使用备用图标 API |

## 4. iOS 功能取舍

### 4.1 桌面悬浮宠物

iOS 不支持普通 App 跨应用显示悬浮窗，因此无法复刻 Android 的拖动、贴边、躲边和环形菜单。

替代方案：

- 主屏幕 Widget
- 锁屏 Widget
- Live Activity
- Dynamic Island
- App 内宠物房间
- 通知按钮和 App Intents

用户体验从“宠物一直在屏幕上走”改为“宠物在 App、锁屏、灵动岛和小组件中陪伴”。

### 4.2 常驻通知宠物

iOS 没有 Android 前台服务和常驻通知的等价物。Live Activity 适合表示一段持续中的陪伴或提醒状态：

```text
小猫陪伴中
今日喝水 3 / 8 杯
下一次提醒 15:30
[喝一杯] [打开萌叮]
```

Live Activity 最多活跃 8 小时，结束后锁屏上最多还可保留约 4 小时。它不能作为永久运行的宠物进程，也不能持续播放完整帧动画。

### 4.3 小组件

iOS WidgetKit 使用时间线模型，由系统决定刷新时机。典型 Widget 每天的刷新预算约为 40～70 次，不适合每秒更新动画。

Widget 适合显示：

- 宠物状态
- 喝水进度
- 下一次提醒
- 当前服装
- 今日萌点
- “喝一杯”和“摸摸宠物”按钮

Widget 的宠物应使用静态图或状态切换图，不设计成持续播放的动画窗口。

### 4.4 本地提醒

iOS 使用 `UNUserNotificationCenter`。本地通知可以在 App 没有运行时展示，但通知到点不保证唤醒 App 执行下一轮 Kotlin 代码。

建议采用以下排期方式：

1. App 前台打开时计算未来 2～3 天的提醒。
2. 只保留最近 64 条通知。
3. App 启动、进入前台、修改设置和修改作息时重新排期。
4. 每条通知通过 `userInfo` 携带 `kind`、餐次或便签 ID。
5. 用户点击通知或按钮后，在 App 内完成实际打卡。

提醒计算仍然放在 shared 层，通知注册和点击处理放在 iOS 平台层。

### 4.5 声音

App 内操作音使用 AVAudioPlayer 或 AVAudioEngine；通知声音使用 `UNNotificationSound`。自定义通知声音必须短于 30 秒，建议准备 `caf`、`aiff` 或短 `wav` 文件。

不能保证宠物叫声绕过 iPhone 静音模式，也不能像 Android 前台服务一样长期控制系统音量。

### 4.6 宠物状态模拟

保留现有 `PetSim` 的按时间补算机制，不尝试维持 iOS 后台每分钟心跳：

```text
保存 lastSimAt
App 打开时：
    根据当前时间和 lastSimAt 补算
    更新体力、饱食和水分
    更新 Widget / Live Activity
```

这样可以保留 Android 和 iOS 的状态一致性，也不依赖后台常驻服务。

### 4.7 陪我走路和步数

iOS 使用 Core Motion 的 `CMPedometer`，也可以在后续版本接入 HealthKit。第一版建议：

- App 打开时读取今日步数。
- App 活跃时接收步数更新。
- 将陪伴奖励绑定到步数或步数目标。
- 不承诺桌面宠物根据每一步实时移动。

如果接入 HealthKit，需要增加健康数据授权、隐私说明和审核准备。

### 4.8 电量彩蛋

iOS 不适合依靠后台电池广播持续驱动宠物动作。建议改为：

- App 打开时检查电量。
- App 活跃时显示充电动作。
- 低电量时显示困倦状态。
- Widget 和 Live Activity 不承诺立即反映电量变化。

### 4.9 便签、照片和录音

| Android | iOS |
|---|---|
| 系统照片选择器 | PhotosPicker |
| MediaRecorder | AVAudioRecorder |
| MediaPlayer | AVAudioPlayer |
| 私有文件目录 | Application Support |
| Android URI | security-scoped URL 后复制到私有目录 |
| Android 权限 | `NSPhotoLibraryUsageDescription`、`NSMicrophoneUsageDescription` |

图片选择、录音、音频播放和文件导入都保留，但界面使用 iOS 系统 Sheet、系统文件选择器和原生录音控件。

### 4.10 数据加密和备份

Android 的 Keystore 实现不能直接搬到 iOS。iOS 使用：

- Keychain 保存加密密钥或密钥材料。
- Application Support 保存加密数据库或文件。
- `NSFileProtectionComplete` 保护锁屏时的文件。
- App Group 让主 App、Widget 和 Live Activity 共享必要的非敏感状态。

备份格式应加入 `schemaVersion`，保持 JSON 兼容，支持 Android 和 iOS 互相导入导出。经期、睡眠、体重等数据默认只保存在本机；如果未来接入 HealthKit，需要单独处理授权和隐私审核。

## 5. iOS 页面和外观设计

### 5.1 一级导航

当前 Android 页面最多有七个底部标签。iOS 版建议压缩为五个：

```text
今日 / 部屋 / 便签 / 解压 / 我的
```

- 经期放在“今日”中的健康卡片，或放在“我的 → 健康记录”。
- 商店放在“部屋”顶部入口，或放在“我的 → 商店”。
- “关心 TA”继续放在便签页顶部固定入口。

这样更适合 iPhone 的 TabView 和单手操作。

### 5.2 页面交互

iOS 版采用：

- `NavigationStack` 管理页面层级。
- 系统返回手势。
- Sheet 展示购买、编辑和选择器。
- 分组列表展示设置。
- 大标题导航栏。
- Safe Area 适配刘海、灵动岛和底部 Home Indicator。

### 5.3 保留的品牌元素

可以完整保留：

- 薄荷色主色
- 粉色关怀卡片
- 像素宠物
- 宠物项圈和铃铛
- 食物、饮料和服装插画
- 萌点经济系统
- 宠物状态动画
- 多套主题

需要适配 iOS 的部分：

- 字体支持 Dynamic Type。
- 页面支持 VoiceOver。
- 支持深色模式和 Reduce Motion。
- App 图标使用完整背景，不能直接使用透明像素图作为最终图标。
- Widget 和 Live Activity 使用静态状态图或有限转场。

## 6. 迁移步骤

### 阶段一：抽取共享层

迁移以下模块：

- `Models.kt`
- `Economy.kt`
- `PetSim.kt`
- `Period.kt`
- `GameItems.kt`
- 小游戏状态机
- 提醒计算部分
- 数据迁移和备份格式

将现有 `Actions` 拆成：

```text
shared/domain/usecase/
androidApp/platform/
iosApp/platform/
```

### 阶段二：建立 iOS MVP

优先完成：

- 首次引导
- 今日页
- 宠物状态
- 喝水打卡
- 经期记录
- 本地存储
- 本地通知
- 一个小游戏

先验证数据读写、通知和真机签名流程，再扩展系统外设功能。

### 阶段三：接入 iOS 原生能力

实现：

- Keychain
- `UNUserNotificationCenter`
- AVFoundation
- PhotosPicker
- AVAudioRecorder
- WidgetKit
- ActivityKit
- App Intents
- Core Motion

### 阶段四：补齐功能和视觉

实现：

- 主题迁移
- 商店和换装
- 富文本便签
- 三款小游戏
- 备份导入导出
- 备用图标
- VoiceOver 和 Dynamic Type

### 阶段五：双平台验收

重点验证：

- Android 导出到 iOS，再从 iOS 导回 Android。
- 跨午夜睡眠时间。
- 时区变化。
- 通知权限关闭后恢复。
- 设备重启和 App 被系统清理后再次打开。
- 深色模式和动态字体。
- iPhone 小屏、普通屏和灵动岛设备。
- iPad 基本布局。

## 7. 关键风险

1. **系统能力差异**：悬浮窗、前台常驻服务和后台心跳不能在 iOS 复刻。
2. **UI 复用率低于预期**：当前 Android 页面和游戏绘制直接依赖 Android 类型。
3. **提醒可靠性不同**：iOS 本地通知数量、后台唤醒和系统专注模式都会影响体验。
4. **健康数据审核**：经期、睡眠和体重数据需要明确隐私说明。
5. **版本工具链**：当前 Kotlin 2.0.21 需要升级到与 Compose Multiplatform、Gradle 和 Xcode 匹配的版本。
6. **多目标测试成本**：Android、iPhone、iPad、Widget 和 Live Activity 都要分别验收。

## 8. 最终判断

采用“共享 Kotlin 领域层 + Android Jetpack Compose + iOS SwiftUI + Swift WidgetKit/ActivityKit 扩展”的方案，功能可以保留约 80%～90%，同时获得较自然的 iOS 交互体验。

第一版 iOS MVP 的重构量约为现有 Android 代码的 40%～60%。如果要求完整复刻 Android 的悬浮宠物、通知栏动画和所有页面视觉，工作量会接近重新开发一套 iOS 客户端。

优先保证以下闭环：

```text
本地数据 → 宠物状态 → 喝水/吃饭/睡觉打卡 → 萌点 → 领养和换装
         ↘ 本地提醒 / Widget / Live Activity
```

## 9. 官方参考

- [Kotlin Multiplatform 兼容性与版本](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Kotlin Multiplatform iOS 集成](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html)
- [Compose Multiplatform 与 SwiftUI 集成](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html)
- [Apple Live Activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities)
- [Widget 和 Live Activity 交互](https://developer.apple.com/documentation/widgetkit/adding-interactivity-to-widgets-and-live-activities)
- [Widget 刷新策略](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date)
- [本地通知限制](https://developer.apple.com/documentation/uikit/uilocalnotification)
- [通知声音格式和时长](https://developer.apple.com/documentation/usernotifications/unnotificationsound)
- [Core Motion 步数查询](https://developer.apple.com/documentation/coremotion/cmpedometer/querypedometerdata(from:to:withhandler:))
- [备用 App 图标](https://developer.apple.com/documentation/uikit/uiapplication/setalternateiconname(_:completionhandler:))
