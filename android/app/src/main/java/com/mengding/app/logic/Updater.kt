package com.mengding.app.logic

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mengding.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 在线更新（关于萌叮 → 检查更新）：
 * 查 GitHub Releases（含预发布）→ 有新版就下载到 App 私有缓存（断点续传、可取消）→ 校验大小、sha256（说明里写了的话）、
 * 包名、版本号和签名证书都与当前一致 → 用 PackageInstaller 安装。
 * 安卓不允许普通 App 完全静默安装：第一次会弹一次系统确认；安卓 12 起，由萌叮自己装过一次之后，后续更新可以不再确认。
 * 离开页面下载也继续（跑在这里自己的协程里）。
 */
object Updater {
    sealed class State {
        object Idle : State()
        object Checking : State()
        data class UpToDate(val latest: String?) : State()
        data class Available(val r: UpdateRelease) : State()
        data class Downloading(val r: UpdateRelease, val progress: Float, val viaMirror: Boolean = false) : State()
        data class Ready(val r: UpdateRelease, val file: File) : State()
        /** 需要用户在系统设置里允许「安装未知应用」 */
        data class NeedPermission(val r: UpdateRelease, val file: File) : State()
        data class Installing(val r: UpdateRelease) : State()
        data class Failed(val msg: String, val r: UpdateRelease? = null) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var cancel = false

    /**
     * 国内网络常连不上 GitHub：直连失败时依次试这些公共加速镜像（拼在原下载地址前面）。
     * 不管从哪下，装之前都校验 sha256 和签名证书（必须和当前萌叮一致），被改过的包装不上。
     */
    val mirrorPrefixes = listOf("https://ghfast.top/", "https://gh-proxy.com/", "https://ghproxy.net/")

    /**
     * 查更新的备用地址：仓库里的 update.json（和 /releases 返回一样的格式，只放最新一版），
     * 走 jsDelivr CDN 和加速镜像，国内一般能连上。发版时同步更新这个文件。
     */
    val checkFallbacks = listOf(
        "https://cdn.jsdelivr.net/gh/${UpdateCheck.REPO}@main/update.json",
        "https://fastly.jsdelivr.net/gh/${UpdateCheck.REPO}@main/update.json",
    ) + mirrorPrefixes.map { it + "https://raw.githubusercontent.com/${UpdateCheck.REPO}/main/update.json" }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "update").also { it.mkdirs() }

    private fun open(url: String, range: Long = 0): HttpURLConnection {
        // 自己处理跳转：GitHub 附件会 302 到另一个域名，自动跳转时 Range 头不一定带过去
        var u = url
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 12_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "MengDing/${BuildConfig.VERSION_NAME}")
            c.setRequestProperty("Accept", if (u.contains("api.github.com")) "application/vnd.github+json" else "*/*")
            if (range > 0) c.setRequestProperty("Range", "bytes=$range-")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("跳转没有地址")
                c.disconnect(); u = URL(URL(u), loc).toString(); return@repeat
            }
            return c
        }
        throw IOException("跳转太多次")
    }

    private fun netError(e: Throwable) = if (e is IOException && e.message?.startsWith("!") == true) e.message!!.drop(1) else when (e) {
        is java.net.UnknownHostException -> "连不上网络，检查一下网络再试"
        is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "连不上 GitHub（国内网络有时访问不到），可以换个网络或稍后再试"
        else -> "网络出错了：${e.message ?: e.javaClass.simpleName}"
    }

    fun check() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        job = scope.launch {
            var lastErr: Throwable? = null
            var body: String? = null
            // 先直连 GitHub API，不行再试备用地址
            for (url in listOf(UpdateCheck.API) + checkFallbacks) {
                body = runCatching {
                    val c = open(url)
                    if (c.responseCode !in 200..299) throw IOException("!检查更新失败（${c.responseCode}）")
                    c.inputStream.bufferedReader().use { it.readText() }.takeIf { UpdateCheck.parse(it).isNotEmpty() }
                        ?: throw IOException("!没读到版本信息")
                }.onFailure { lastErr = it }.getOrNull()
                if (body != null) break
            }
            _state.value = if (body == null) State.Failed(lastErr?.let(::netError) ?: "检查更新失败")
            else {
                val latest = UpdateCheck.latest(UpdateCheck.parse(body))
                when {
                    latest == null -> State.UpToDate(null)
                    UpdateCheck.isNewer(latest, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) -> State.Available(latest)
                    else -> State.UpToDate(latest.versionName)
                }
            }
        }
    }

    fun download(ctx: Context, r: UpdateRelease) {
        if (_state.value is State.Downloading) return
        cancel = false
        val app = ctx.applicationContext
        _state.value = State.Downloading(r, 0f)
        job = scope.launch {
            val out = File(dir(app), r.apkName)
            val part = File(out.path + ".part")
            if (out.exists() && verify(app, out, r) == null) { _state.value = State.Ready(r, out); return@launch }
            out.delete()
            var lastErr: Throwable? = null
            for (url in listOf(r.apkUrl) + mirrorPrefixes.map { it + r.apkUrl }) {
                try {
                    fetch(url, part, r, url != r.apkUrl)
                    if (cancel) { _state.value = State.Available(r); return@launch }
                    if (!part.renameTo(out)) throw IOException("保存安装包失败")
                    val bad = verify(app, out, r)
                    if (bad != null) { out.delete(); _state.value = State.Failed(bad, r); return@launch }
                    autoInstall = out.path
                    _state.value = State.Ready(r, out)
                    return@launch
                } catch (e: Throwable) {
                    if (cancel) { _state.value = State.Available(r); return@launch }
                    lastErr = e
                }
            }
            _state.value = State.Failed(lastErr?.let(::netError) ?: "下载失败", r)
        }
    }

    fun cancelDownload() { cancel = true }

    /** 刚下载完的那一个包自动接着装一次（用户在系统确认框里点了取消就不再自动弹） */
    @Volatile private var autoInstall: String? = null
    fun consumeAutoInstall(f: File) = (autoInstall == f.path).also { if (it) autoInstall = null }

    /** 不去开「安装未知应用」：回到「已下载」状态，之后可以再点安装 */
    fun dismissPermission(s: State.NeedPermission) { _state.value = State.Ready(s.r, s.file) }

    /** 断点续传：.part 已有多少就从多少接着下 */
    private fun fetch(url: String, part: File, r: UpdateRelease, viaMirror: Boolean) {
        var have = if (part.exists()) part.length() else 0L
        if (r.size > 0 && have >= r.size) { part.delete(); have = 0 }
        val c = open(url, have)
        val code = c.responseCode
        if (code == 416) { part.delete(); throw IOException("续传位置不对，重新下载") }
        if (code !in 200..299) throw IOException("!下载失败（$code）")
        if (code == 200 && have > 0) { part.delete(); have = 0 }   // 服务器不支持续传，从头来
        val total = if (r.size > 0) r.size else have + c.contentLengthLong
        RandomAccessFile(part, "rw").use { f ->
            f.seek(have)
            c.inputStream.use { input ->
                val buf = ByteArray(128 * 1024)
                var done = have
                var lastPct = -1
                while (true) {
                    if (cancel) return
                    val n = input.read(buf)
                    if (n < 0) break
                    f.write(buf, 0, n); done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    if (pct != lastPct) { lastPct = pct; _state.value = State.Downloading(r, if (total > 0) done.toFloat() / total else -1f, viaMirror) }
                }
            }
        }
    }

    /** 返回 null 表示没问题，否则是给用户看的原因 */
    private fun verify(ctx: Context, f: File, r: UpdateRelease): String? {
        if (r.size > 0 && f.length() != r.size) return "安装包大小不对，可能没下完整，请重试"
        r.sha256?.let { want ->
            val got = MessageDigest.getInstance("SHA-256").let { md ->
                f.inputStream().use { s -> val b = ByteArray(256 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            if (got != want) return "安装包校验没通过（sha256 不一致），已删除"
        }
        val pm = ctx.packageManager
        val info = archiveInfo(pm, f) ?: return "安装包打不开，已删除"
        if (info.packageName != ctx.packageName) return "这不是萌叮的安装包，已删除"
        val vc = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (vc <= BuildConfig.VERSION_CODE) return "下载到的版本不比现在新，不用装"
        if (!sameSigner(pm, ctx.packageName, info)) return "安装包签名和当前萌叮不一致，为了安全不安装"
        return null
    }

    private fun archiveInfo(pm: PackageManager, f: File): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(f.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(f.path, PackageManager.GET_SIGNING_CERTIFICATES)
    }.getOrNull()

    private fun sameSigner(pm: PackageManager, pkg: String, apk: PackageInfo): Boolean = runCatching {
        val mine = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val a = mine.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        val b = apk.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        a.isNotEmpty() && a == b
    }.getOrDefault(false)

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** 去系统设置打开「允许安装未知应用」 */
    fun permissionIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(ctx: Context, r: UpdateRelease, f: File) {
        val app = ctx.applicationContext
        if (!canInstall(app)) { _state.value = State.NeedPermission(r, f); return }
        _state.value = State.Installing(r)
        scope.launch {
            runCatching {
                val pi = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(f.length())
                    // 安卓 12 起：萌叮自己装过之后再更新可以不弹确认
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    s.openWrite("base.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                    val intent = Intent(app, UpdateReceiver::class.java).setAction(ACTION_RESULT)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    s.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
                }
            }.onFailure { _state.value = State.Failed("安装没能开始：${it.message ?: it.javaClass.simpleName}", r) }
        }
    }

    internal fun onInstallResult(ctx: Context, status: Int, msg: String?, confirm: Intent?) {
        val cur = _state.value
        val r = (cur as? State.Installing)?.r
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.Idle   // 一般走不到：装好后进程会被系统换掉
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = r?.let { rr -> readyFile(ctx, rr)?.let { State.Ready(rr, it) } } ?: State.Idle
            else -> _state.value = State.Failed("安装失败：${msg ?: "系统没有说明原因"}", r)
        }
    }

    private fun readyFile(ctx: Context, r: UpdateRelease) = File(dir(ctx), r.apkName).takeIf { it.exists() }

    /** 启动时清掉已经装上（或更旧）的安装包；半截的 .part 留着续传 */
    fun cleanup(ctx: Context) = scope.launch {
        runCatching {
            dir(ctx).listFiles()?.forEach { f ->
                if (f.name.endsWith(".apk")) {
                    val info = archiveInfo(ctx.packageManager, f)
                    val vc = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: 0L
                    if (vc <= BuildConfig.VERSION_CODE) f.delete()
                }
            }
        }
    }

    const val ACTION_RESULT = "com.mengding.app.UPDATE_INSTALL_RESULT"
}

/** PackageInstaller 的安装结果回调 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        Updater.onInstallResult(ctx, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), confirm)
    }
}
