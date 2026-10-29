import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 正式签名：android/keystore.properties（不要外传，丢了以后的新版就装不上旧版之上）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    // 音效、背景音乐要用 openFd 直接读，不能压缩
    androidResources { noCompress += listOf("wav", "ogg", "mp3") }
    namespace = "com.mengding.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mengding.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 34
        versionName = "4.0.7-test"
        // 首页 3D 小屋的 Unity 运行库只带 64 位 ARM（真机）；调试包另外带 x86_64 方便电脑模拟器
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += listOf("x86_64") }
        }
        // gradlew assembleRelease -PwithX86 ：正式签名但带 x86_64，只用来在电脑模拟器上覆盖安装测试，不发给用户
        if (project.hasProperty("withX86")) getByName("release") { ndk { abiFilters += listOf("x86_64") } }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // Unity 运行库要求安装时解压 .so；场景数据文件不压缩
    packaging { jniLibs { useLegacyPackaging = true } }
    androidResources { noCompress += listOf(".unity3d", ".ress", ".resource", ".obb", ".bundle", ".unityexp") }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // 首页 3D 小屋：tools/unity/build_unity_library.ps1 从 unity/ 编出来的库
    implementation(files("libs/unityLibrary.aar"))
    implementation("androidx.games:games-frame-pacing:2.1.2")
    // 便签视频播放：只用播放内核，不带 media3-ui，控制条自己画（跟随界面风格）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    testImplementation("junit:junit:4.13.2")
}
