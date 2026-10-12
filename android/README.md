# 萌叮安卓 App（0.1.0）

Kotlin + Jetpack Compose，单模块，最低 Android 10（API 29），目标 Android 15（API 35）。
需求以 [../需求汇总.md](../需求汇总.md) 为准。

## 目录

```
app/src/main/
├─ java/com/mengding/app/
│  ├─ MengDingApp.kt / MainActivity.kt / ShortcutActivity.kt
│  ├─ data/      Models.kt（三层数据：配置 / 个人敏感数据 / 公共资源）、Store.kt（本地存储，敏感数据用系统密钥加密）
│  ├─ logic/     Time（按手机时区）、PetSim（体力饱食水分）、Economy（萌点）、Period（经期预测）、
│  │             Actions（所有用户操作的唯一入口）、Species（图鉴、等级）、System（换图标、权限、备份）
│  ├─ notify/    Reminders（提醒引擎：只排最近一个精确闹钟）、Notifier（通知和按钮）、Receivers
│  ├─ pet/       Sprites（读精灵图）、PetFx（该播哪个动作）、PetService（常驻服务）、FloatingPet（悬浮宠物）
│  ├─ widget/    PetWidget（桌面小组件）
│  └─ ui/        各个界面（Compose），Icons.kt 是自绘线性图标
├─ assets/pets/  像素宠物精灵图 + meta.json —— 由 美术/像素宠物/export_android.py 生成，不要手改
└─ res/          图标、通知布局、小组件、提示音 ding.wav
```

## 改了宠物美术之后

```bat
cd ..\美术\像素宠物
python export_android.py
```

会重新生成 `assets/pets/`、状态栏剪影、主题图标和换图标用的宠物图标。

## 打包

需要 JDK 17 和 Android SDK（`local.properties` 里的 `sdk.dir`）。国内镜像已配好（腾讯云 Gradle、阿里云 Maven）。

```bat
gradlew.bat assembleRelease
```

安装包在 `app/build/outputs/apk/release/app-release.apk`。

## 签名

正式签名文件（keystore.properties 和签名密钥）不在仓库里，是作者自己保管的，**一定要备份好**：
以后每一版都必须用同一把钥匙签名，丢了的话新版就没法覆盖安装，只能先卸载（会清空用户数据）。
这两个文件不要发给别人、不要传到网上。
