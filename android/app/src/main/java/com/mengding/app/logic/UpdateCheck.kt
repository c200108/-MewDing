package com.mengding.app.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 在线更新：解析 GitHub Releases（c200108/MewDing）列表、挑出最新的安装包、和当前版本比较。
 * 只处理文本，不碰网络和 Android 接口，方便单元测试（UpdateCheckTest）；下载安装在 Updater.kt。
 * 约定：附件名 MewDing-<版本>.apk；说明里可写「versionCode: 34」「sha256: <64 位>」，有就优先用。
 */
data class UpdateRelease(
    val tag: String,
    val title: String,
    val versionName: String,
    /** 说明里写了才有；老的 Release 没写 */
    val versionCode: Int?,
    val publishedAt: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val size: Long,
    val sha256: String?,
)

object UpdateCheck {
    const val REPO = "c200108/MewDing"
    /** 包括预发布（/releases/latest 会跳过预发布，测试版都是预发布） */
    const val API = "https://api.github.com/repos/$REPO/releases?per_page=40"
    const val PAGE = "https://github.com/$REPO/releases"

    private val json = Json { ignoreUnknownKeys = true }

    /** 版本号里的数字段：v4.0.7-test → [4, 0, 7]，0.3.22-beta02 → [0, 3, 22] */
    fun numbers(v: String): List<Int> =
        Regex("""(\d+)\.(\d+)(?:\.(\d+))?""").find(v)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: 0 } ?: emptyList()

    fun compareNames(a: String, b: String): Int {
        val x = numbers(a); val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun versionCodeIn(text: String) =
        Regex("""versionCode\s*[:：=]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun sha256In(text: String) =
        Regex("""sha-?256\s*[:：=]?\s*`?([0-9a-fA-F]{64})""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.lowercase()

    /** 只认正式包：MewDing-*.apk，排除 Unity 测试包、debug、模拟器 x86 包 */
    fun isAppApk(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("mewding-") && n.endsWith(".apk") && "unity" !in n && "debug" !in n && "x86" !in n
    }

    /** 解析 /releases 的返回；草稿和没有安装包附件的跳过 */
    fun parse(body: String): List<UpdateRelease> {
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrElse { return emptyList() }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (o["draft"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val notes = o["body"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content ?: ""
            val asset = (o["assets"] as? JsonArray)?.map { it.jsonObject }
                ?.firstOrNull { isAppApk(it["name"]?.jsonPrimitive?.content ?: "") } ?: return@mapNotNull null
            val apkName = asset["name"]!!.jsonPrimitive.content
            UpdateRelease(
                tag = tag,
                title = o["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: tag,
                versionName = tag.removePrefix("v"),
                versionCode = versionCodeIn(notes),
                publishedAt = o["published_at"]?.jsonPrimitive?.content ?: "",
                notes = notes,
                apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                apkName = apkName,
                size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                sha256 = sha256In(notes),
            )
        }
    }

    /** 两个 Release 谁更新：都写了 versionCode 就比它，否则比版本号数字 */
    fun newer(a: UpdateRelease, b: UpdateRelease): Int {
        if (a.versionCode != null && b.versionCode != null) return a.versionCode.compareTo(b.versionCode)
        return compareNames(a.versionName, b.versionName)
    }

    fun latest(list: List<UpdateRelease>): UpdateRelease? = list.maxWithOrNull(::newer)

    /** 比当前装的新吗 */
    fun isNewer(r: UpdateRelease, currentName: String, currentCode: Int): Boolean =
        r.versionCode?.let { it > currentCode } ?: (compareNames(r.versionName, currentName) > 0)

    /** 说明太长时界面只显示前面一段；去掉 sha256 / versionCode 这类给机器看的行 */
    fun readableNotes(notes: String, max: Int = 600): String {
        val lines = notes.lines().filterNot { versionCodeIn(it) != null || sha256In(it) != null }
        val t = lines.joinToString("\n").trim()
        return if (t.length <= max) t else t.take(max).trimEnd() + "…"
    }
}
