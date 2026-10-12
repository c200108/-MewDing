package com.mengding.app.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 显示字体（我的 → 显示字体）。字体**不打进安装包**：用户选了哪个才联网下载，存在 App 私有目录 files/fonts/，
 * 卸载萌叮时系统会一起删掉。全部是可以随软件分发的开源字体（SIL OFL 1.1），下载地址优先用国内能访问的 jsDelivr 镜像，
 * 不通再试作者的 GitHub。镜像上的文件钉死版本并核对 SHA-256；最后再确认能当字体打开，不行就删掉。
 * 得意黑、霞鹜文楷在 GitHub 之外没有官方镜像，用的是 npm 上 @fontpkg 重新打包的同一字体（OFL 允许再分发），固定版本 + 校验。
 */
object AppFonts {
    /** 一个下载地址；sha256 不为空时，下下来的文件必须和它一致（镜像上的文件钉死版本 + 校验，防止被换掉） */
    data class Src(val url: String, val sha256: String? = null) {
        val zipped get() = url.endsWith(".zip")
    }

    data class Option(
        val key: String, val name: String, val desc: String, val sizeMb: Double,
        val srcs: List<Src>, val author: String = "",
    )

    // 地址顺序：先走国内能访问的 jsDelivr 镜像（钉死版本 + SHA-256），不通再试作者的 GitHub 官方发布
    val all = listOf(
        Option("system", "系统默认", "手机自带的字体，不用下载", 0.0, emptyList()),
        Option("kuaile", "站酷快乐体", "圆乎乎、很可爱的字", 1.5, listOf(
            Src("https://cdn.jsdelivr.net/gh/googlefonts/zcool-kuaile@577cd45e035fa0cd27f74579de6ff47db62fde8c/fonts/ttf/ZCOOLKuaiLe-Regular.ttf",
                "812a6fc1fe54b6d73a419245c32dfeba8aa33104d5be90d1cf6af082007cb71d"),
            Src("https://raw.githubusercontent.com/googlefonts/zcool-kuaile/577cd45e035fa0cd27f74579de6ff47db62fde8c/fonts/ttf/ZCOOLKuaiLe-Regular.ttf",
                "812a6fc1fe54b6d73a419245c32dfeba8aa33104d5be90d1cf6af082007cb71d"),
        ), "站酷 · ZCOOL KuaiLe Project Authors"),
        Option("muzai", "目哉像素体", "和像素宠物一个风格的像素字", 4.0, listOf(
            Src("https://cdn.jsdelivr.net/gh/DWNfonts/MuzaiPixel@2.0.20260705/MZPXorig.ttf",
                "e07ef0f7b26991436f600e414ee5286164bd5ea1d779eb8914975d69af23e6f8"),
            Src("https://raw.githubusercontent.com/DWNfonts/MuzaiPixel/main/MZPXorig.ttf"),
        ), "目哉 · MuzaiPixel"),
        Option("smiley", "得意黑", "有个性的斜体黑体，适合喜欢标题风的人", 2.6, listOf(
            Src("https://cdn.jsdelivr.net/npm/@fontpkg/smiley-sans@2.0.4/SmileySans-Oblique.ttf",
                "b447d7e781f08bc95c4c9f23ba71ed2b8ebb639aa7184485c71c4ca5afcd25c4"),
            Src("https://github.com/atelier-anchor/smiley-sans/releases/download/v2.0.1/smiley-sans-v2.0.1.zip"),
        ), "atelier-anchor · Smiley Sans"),
        Option("wenkai", "霞鹜文楷", "清秀的手写楷书，字最全，文件也最大", 24.7, listOf(
            Src("https://cdn.jsdelivr.net/npm/@fontpkg/lxgw-wen-kai@1.520.0/LXGWWenKai-Regular.ttf",
                "8d6ba638ac9553413354cfaab97637c1cd778444e259441ea1e5f8fb2c697fba"),
            Src("https://github.com/lxgw/LxgwWenKai-Lite/releases/download/v1.522/LXGWWenKaiLite-Regular.ttf"),
        ), "落霞孤鹜 · LXGW WenKai"),
    )

    fun of(key: String) = all.firstOrNull { it.key == key } ?: all[0]

    private fun dir(ctx: Context) = File(ctx.filesDir, "fonts").also { it.mkdirs() }
    fun file(ctx: Context, key: String) = File(dir(ctx), "$key.ttf")
    fun has(ctx: Context, key: String) = key == "system" || file(ctx, key).let { it.exists() && it.length() > 10_000 }

    /** 当前用的字体（Compose）；null = 系统默认 */
    val family = MutableStateFlow<FontFamily?>(null)
    /** 当前用的字体（原生画布：桌面宠物气泡、游戏飘字）；null = 系统默认 */
    @Volatile var typeface: Typeface? = null
        private set
    private var loadedKey: String? = null

    /** 按设置加载字体；文件不在了就退回系统默认 */
    @Synchronized
    fun load(ctx: Context) {
        val key = Store.cfg.settings.fontKey
        if (key == loadedKey) return
        loadedKey = key
        if (key == "system" || !has(ctx, key)) { family.value = null; typeface = null; return }
        val f = file(ctx, key)
        runCatching {
            typeface = Typeface.createFromFile(f)
            family.value = FontFamily(Font(f))
        }.onFailure { family.value = null; typeface = null }
    }

    /** 下载一个字体；progress 0~1。返回错误说明，成功返回 null */
    suspend fun download(ctx: Context, o: Option, progress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        val target = file(ctx, o.key)
        val tmp = File(dir(ctx), "${o.key}.part")
        var lastErr = "网络不通"
        for (src in o.srcs) {
            try {
                val c = (URL(src.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 30_000; instanceFollowRedirects = true
                }
                if (c.responseCode !in 200..299) { lastErr = "服务器返回 ${c.responseCode}"; c.disconnect(); continue }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: (o.sizeMb * 1_000_000).toLong()
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); done += n
                            progress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                c.disconnect()
                if (src.zipped) {
                    // 压缩包里挑第一个 .ttf
                    var ok = false
                    ZipInputStream(tmp.inputStream()).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            if (!e.isDirectory && e.name.lowercase().endsWith(".ttf")) {
                                target.outputStream().use { z.copyTo(it) }
                                ok = true; break
                            }
                        }
                    }
                    tmp.delete()
                    if (!ok) { lastErr = "压缩包里没有字体"; continue }
                } else {
                    target.delete(); tmp.renameTo(target)
                }
                // 校验：镜像上的文件要和记下的 SHA-256 一致
                val want = src.sha256
                if (want != null && sha256(target) != want) { target.delete(); lastErr = "下载的文件校验不对"; continue }
                // 校验：能当字体打开才算下好
                val good = runCatching { Typeface.createFromFile(target) }.isSuccess && target.length() > 10_000
                if (!good) { target.delete(); lastErr = "下载的文件不是可用的字体"; continue }
                return@withContext null
            } catch (e: Exception) {
                lastErr = "下载失败：${e.javaClass.simpleName}"
                tmp.delete()
            }
        }
        lastErr
    }

    private fun sha256(f: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val b = ByteArray(64 * 1024)
            while (true) { val n = ins.read(b); if (n < 0) break; md.update(b, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun delete(ctx: Context, key: String) {
        if (key == "system") return
        file(ctx, key).delete()
        if (Store.cfg.settings.fontKey == key) Store.update { it.copy(settings = it.settings.copy(fontKey = "system")) }
        loadedKey = null
        load(ctx)
    }

    fun use(ctx: Context, key: String) {
        Store.update { it.copy(settings = it.settings.copy(fontKey = key)) }
        loadedKey = null
        load(ctx)
    }
}

/** 我的 → 显示字体：选一种字体，没下载的点一下联网下载 */
@Composable
fun FontPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val scope = rememberCoroutineScope()
    val progress = remember { mutableStateMapOf<String, Float>() }
    // 删除字体后让列表刷新
    var ver by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    PageScaffold("显示字体") {
        Text("全 App 的文字都会换成选中的字体。字体不在安装包里：选了哪个才联网下载，存在萌叮自己的目录里，卸载萌叮会一起删掉。",
            fontSize = 13.sp, color = P.sub)
        Gap()
        AppFonts.all.forEach { o ->
            @Suppress("UNUSED_EXPRESSION") ver
            val has = AppFonts.has(ctx, o.key)
            val using = cfg.settings.fontKey == o.key || (o.key == "system" && !AppFonts.has(ctx, cfg.settings.fontKey))
            val prev = remember(o.key, has) {
                if (o.key == "system" || !has) null else runCatching { FontFamily(Font(AppFonts.file(ctx, o.key))) }.getOrNull()
            }
            Column(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(P.card)
                    .border(if (using) 2.dp else 1.dp, if (using) P.mint else P.line, RoundedCornerShape(20.dp)).padding(14.dp),
            ) {
                HRow {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                        Text(o.desc + if (o.sizeMb > 0) " · ${o.sizeMb}MB" else "", fontSize = 12.sp, color = P.sub)
                    }
                    val p = progress[o.key]
                    when {
                        p != null -> Text("下载中 ${(p * 100).toInt()}%", fontSize = 13.sp, color = P.mintDeep)
                        using -> Text("正在使用", fontSize = 13.sp, color = P.mintDeep, fontWeight = FontWeight.SemiBold)
                        has -> PrimaryButton("使用") { AppFonts.use(ctx, o.key); toast("已换成「${o.name}」") }
                        else -> GhostButton("下载") {
                            progress[o.key] = 0f
                            scope.launch {
                                val err = AppFonts.download(ctx, o) { v -> progress[o.key] = v }
                                progress.remove(o.key)
                                if (err == null) { AppFonts.use(ctx, o.key); toast("「${o.name}」下载好了，已经换上") }
                                else toast(err + "，可以换个网络再试")
                            }
                        }
                    }
                }
                progress[o.key]?.let { v ->
                    Gap(6.dp)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(P.line)) {
                        Box(Modifier.fillMaxWidth(v).height(6.dp).clip(RoundedCornerShape(3.dp)).background(P.mint))
                    }
                }
                Gap(8.dp)
                Text("萌叮 · 今天也要好好喝水呀 123", fontSize = 18.sp, color = if (has) P.ink else P.sub,
                    fontFamily = prev ?: FontFamily.Default)
                if (!has && o.key != "system") Text("（下载后显示这款字体的样子）", fontSize = 11.sp, color = P.sub)
                if (has && o.key != "system") {
                    Gap(4.dp)
                    HRow {
                        Text(o.author, Modifier.weight(1f), fontSize = 11.sp, color = P.sub)
                        Text("删除", Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { AppFonts.delete(ctx, o.key); ver++; toast("已删除「${o.name}」") }
                            .padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = P.danger)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("以上字体都是开源字体（SIL Open Font License 1.1），可以免费使用；下载地址是作者的 GitHub 或它的国内镜像 jsDelivr。",
            fontSize = 11.sp, color = P.sub)
    }
}
