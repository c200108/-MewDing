package com.mengding.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 本地天气（Open-Meteo 公开天气接口，不需要账号和密钥）。
 *
 * 位置只在用户**主动同意**之后才读：首页点"看本地天气" → 先看一页说明 → 再弹系统的位置权限框。
 * 只申请"大致位置"（ACCESS_COARSE_LOCATION），够查天气用；坐标保留两位小数（约 1 公里）后只发给 Open-Meteo，
 * 不保存、不上传到别处。用户随时可以在"我的 → 本地天气"里关掉。
 */
object Weather {
    data class Now(val text: String, val at: Long)

    @Volatile private var cache: Now? = null

    /** 最近一次查到的天气代码（WMO），首页动态背景用它判断下雨 / 下雪；没开本地天气就是 null */
    var code by androidx.compose.runtime.mutableStateOf<Int?>(null)
        private set

    fun rainy(c: Int? = code) = c != null && (c in 51..67 || c in 80..82 || c in 95..99)
    fun snowy(c: Int? = code) = c != null && (c in 71..77 || c in 85..86)

    fun permitted(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 读当前位置并查天气；30 分钟内重复调用直接用上次的结果。拿不到返回说明文字 */
    suspend fun local(ctx: Context, force: Boolean = false): String {
        cache?.let { if (!force && System.currentTimeMillis() - it.at < 30 * 60_000L) return it.text }
        if (!permitted(ctx)) return "没有位置权限"
        val loc = location(ctx) ?: return "暂时拿不到位置"
        val text = withContext(Dispatchers.IO) {
            runCatching {
                // 坐标只保留两位小数（约 1 公里），够查天气，也不暴露精确位置
                val lat = String.format(Locale.US, "%.2f", loc.latitude)
                val lon = String.format(Locale.US, "%.2f", loc.longitude)
                val json = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=auto"))
                val cur = json.getJSONObject("current")
                val code = cur.getInt("weather_code")
                this@Weather.code = code
                "${icon(code)} ${name(code)} ${Math.round(cur.getDouble("temperature_2m"))}℃"
            }.getOrNull()
        } ?: return "天气暂不可用"
        cache = Now(text, System.currentTimeMillis())
        return text
    }

    fun clear() { cache = null; code = null }

    /** 先用 30 分钟内的上次位置，没有就现场取一次（最多等 10 秒） */
    private suspend fun location(ctx: Context): Location? = withContext(Dispatchers.Main) {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER); add(LocationManager.GPS_PROVIDER); add(LocationManager.PASSIVE_PROVIDER)
        }.filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000L) return@withContext last
        val fresh = withTimeoutOrNull(10_000) {
            var got: Location? = null
            for (p in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
                got = runCatching { current(lm, p) }.getOrNull()
                if (got != null) break
            }
            got
        }
        fresh ?: last
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun current(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, { it.run() }) { loc -> if (cont.isActive) cont.resume(loc) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) { if (cont.isActive) cont.resume(location) }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) { if (cont.isActive) cont.resume(null) }
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000; c.requestMethod = "GET"
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun icon(code: Int) = when (code) {
        0 -> "☀"; 1, 2 -> "⛅"; 3 -> "☁"; 45, 48 -> "〰"; in 51..67, in 80..82 -> "☂"; in 71..77, in 85..86 -> "❄"; in 95..99 -> "⚡"; else -> "☁"
    }

    private fun name(code: Int) = when (code) {
        0 -> "晴"; 1 -> "晴间多云"; 2 -> "多云"; 3 -> "阴"; 45, 48 -> "雾"; in 51..57 -> "毛毛雨"; in 61..67 -> "雨"
        in 71..77 -> "雪"; in 80..82 -> "阵雨"; in 85..86 -> "阵雪"; in 95..99 -> "雷雨"; else -> ""
    }
}
