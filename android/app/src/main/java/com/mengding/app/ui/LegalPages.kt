package com.mengding.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 我的 → 关于萌叮 → 开源许可：安装包里实际用到的开源项目、素材和它们的许可证 */
@Composable
fun LicensesPage() {
    PageScaffold("开源许可") {
        Text("萌叮用到了下面这些开源项目和开源素材，感谢作者们。许可证全文可在各项目主页查看。", fontSize = 13.sp, color = P.sub)
        Gap()
        LicenseGroup("程序库", listOf(
            Lic("Kotlin 标准库", "JetBrains", "Apache License 2.0", "kotlinlang.org"),
            Lic("kotlinx.serialization", "JetBrains", "Apache License 2.0", "github.com/Kotlin/kotlinx.serialization"),
            Lic("Jetpack Compose（UI、Foundation、Material 3）", "Google / Android 开源项目", "Apache License 2.0", "developer.android.com/jetpack/compose"),
            Lic("AndroidX Core、Activity、Lifecycle", "Google / Android 开源项目", "Apache License 2.0", "developer.android.com/jetpack/androidx"),
            Lic("Android Games Frame Pacing", "Google", "Apache License 2.0", "developer.android.com/games/sdk/frame-pacing"),
            Lic("AndroidX Media3 ExoPlayer（便签视频播放）", "Google / Android 开源项目", "Apache License 2.0", "github.com/androidx/media"),
        ))
        LicenseGroup("小游戏音乐与音效（CC0 公共领域）", listOf(
            Lic("The Essential Retro Video Game Sound Effects Collection", "Juhani Junkala", "CC0 1.0", "opengameart.org"),
            Lic("Cunning plan（萌宠吃豆背景音乐）", "congusbongus", "CC0 1.0", "opengameart.org"),
            Lic("The Arplands（萌叮大冒险背景音乐）", "LordZintick", "CC0 1.0", "opengameart.org"),
            Lic("8 Bit Concentration Loop（萌宠连连看背景音乐）", "cosmac", "CC0 1.0", "opengameart.org"),
        ))
        LicenseGroup("可选下载的显示字体（用户选择后才联网下载，不在安装包内）", listOf(
            Lic("站酷快乐体 ZCOOL KuaiLe", "站酷", "SIL Open Font License 1.1", "github.com/googlefonts/zcool-kuaile"),
            Lic("目哉像素体 MuzaiPixel", "DWNfonts", "SIL Open Font License 1.1", "github.com/DWNfonts/MuzaiPixel"),
            Lic("得意黑 Smiley Sans", "atelier-anchor", "SIL Open Font License 1.1", "github.com/atelier-anchor/smiley-sans"),
            Lic("霞鹜文楷 LXGW WenKai", "LXGW", "SIL Open Font License 1.1", "github.com/lxgw/LxgwWenKai"),
        ))
        LicenseGroup("其他", listOf(
            Lic("首页场景引擎 Unity", "Unity Technologies", "Unity 软件条款（商业引擎，非开源）", "unity.com/legal"),
        ))
    }
}

private data class Lic(val name: String, val author: String, val license: String, val site: String)

@Composable
private fun LicenseGroup(title: String, items: List<Lic>) {
    SectionTitle(title)
    MCard {
        items.forEachIndexed { i, l ->
            if (i > 0) Gap(10.dp)
            Text(l.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = P.ink)
            Text("${l.author} · ${l.license}", fontSize = 12.sp, color = P.sub)
            Text(l.site, fontSize = 12.sp, color = P.mintDeep)
        }
    }
}

/** 我的 → 关于萌叮 → 用户协议 */
@Composable
fun TermsPage() {
    PageScaffold("用户协议") {
        Text("更新日期：2026 年 10 月 21 日", fontSize = 12.sp, color = P.sub)
        Gap()
        TermsSection("一、关于本软件", listOf(
            "萌叮是一款陪伴你按时喝水、吃饭、睡觉的桌面宠物软件。使用本软件即表示你同意本协议。",
            "本软件目前为给亲友试用的早期版本，功能和内容可能随版本调整。",
        ))
        TermsSection("二、原创素材的版权", listOf(
            "本项目中的原创素材，包括但不限于宠物形象与动画、场景地图与美术、界面图标、服装道具、背景音乐、文案与软件代码，版权归萌叮项目作者所有。",
            "未经作者书面许可，任何人不得复制、修改、提取、传播或用于商业用途，也不得将这些素材用于训练模型或二次发布。",
            "如需使用，请先通过「关于萌叮 → 联系作者」取得许可，并在使用时注明出处。",
            "本软件中使用的第三方开源项目和开源素材，按其各自的开源许可使用，详见「开源许可」页面。",
        ))
        TermsSection("三、你的数据", listOf(
            "你的记录（喝水、吃饭、经期、便签等）只保存在你的手机上，不会上传到服务器。卸载软件或清除数据后将无法恢复，请按需使用「备份」功能。",
            "本地天气、计步等功能只在你授权后使用对应权限。",
        ))
        TermsSection("四、免责说明", listOf(
            "经期预测、健康提醒仅供参考，不构成医疗建议；身体不适请及时就医。",
            "因手机系统省电、权限关闭等原因导致的提醒延迟或失效，我们会尽力改进，但不承担由此产生的责任。",
        ))
        TermsSection("五、协议的变更", listOf(
            "本协议可能随版本更新而调整，更新后会在本页显示新的日期。继续使用即视为同意更新后的协议。",
        ))
    }
}

@Composable
private fun TermsSection(title: String, lines: List<String>) {
    SectionTitle(title)
    MCard {
        lines.forEachIndexed { i, t ->
            if (i > 0) Gap(8.dp)
            Text(t, fontSize = 13.sp, color = P.ink, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

/** 我的 → 关于萌叮 → 联系作者（联系方式先占位，作者填好后替换 AUTHOR_CONTACTS） */
private val AUTHOR_CONTACTS = listOf(
    "作者" to "（待填写）",
    "邮箱" to "（待填写）",
    "微信 / QQ" to "（待填写）",
    "小红书 / 微博" to "（待填写）",
)

@Composable
fun ContactPage() {
    PageScaffold("联系作者") {
        Text("有问题反馈、合作或想使用本项目原创素材，都可以通过下面的方式联系作者。", fontSize = 13.sp, color = P.sub)
        Gap()
        MCard {
            AUTHOR_CONTACTS.forEachIndexed { i, (k, v) ->
                if (i > 0) Gap(10.dp)
                Text(k, fontSize = 12.sp, color = P.sub)
                Text(v, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
            }
        }
        Gap()
        Text("申请使用原创素材时，请说明用途和使用范围；未经许可请勿使用，详见「用户协议」。", fontSize = 12.sp, color = P.sub)
    }
}
