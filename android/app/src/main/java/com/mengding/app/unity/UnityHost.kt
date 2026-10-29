package com.mengding.app.unity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mengding.app.data.Store
import com.unity3d.player.IUnityPlayerLifecycleEvents
import com.unity3d.player.UnityPlayer
import com.unity3d.player.UnityPlayerForActivityOrService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 首页 3D 小屋（Unity as a Library）。
 *
 * Unity 运行时一个进程只能起一次，销毁就会结束进程，所以这里全程只建一个播放器，
 * 用 Application Context 创建（MainActivity 重建、退出再进都不受影响），首页显示时挂上、离开时摘下并暂停。
 * 也因为这样，场景风格（微体素 / HD-2D）只在进程启动后第一次进首页时读一次，换风格要重启 App。
 *
 * Kotlin 管数据，Unity 管场景。两边只传 JSON，协议见 unity/Assets/Scripts/AndroidBridge.cs：
 *   发过去：pet / stats / env / feed / drink / wardrobe_open / wardrobe_close / goto / coins / spin / sleep / pet_tap / say / expand / collapse
 *   收回来：ready / ack / done / petted / prop_tap / wardrobe_opened / woken / expanded / collapsed / coins_landed …
 * 带操作编号（id）的指令 Unity 只演一次；扣金币、记打卡都在 Kotlin 这边先做完，所以重发也不会重复扣。
 */
object UnityHost {
    private const val TAG = "MengDingUnity"
    private const val BRIDGE = "AndroidBridge"

    private var player: UnityPlayerForActivityOrService? = null
    private val main = Handler(Looper.getMainLooper())
    private var visibleCount = 0
    private var activityResumed = false
    private var lastPet: String? = null
    private var lastStats: String? = null
    private var lastEnv: String? = null
    private var seq = 0L

    /** Unity 场景已经起来、能收指令了 */
    var ready by mutableStateOf(false)
        private set

    /** 全屋里走进了室内宠物屋（顶上的标题跟着变） */
    var inPetRoom by mutableStateOf(false)
        private set

    /** 全场景展开中（首页把 3D 视口铺满全屏） */
    var expanded by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 32)
    /** Unity 发回来的事件（JSON，字段 evt 是事件名） */
    val events: SharedFlow<JSONObject> = _events

    /** 只打包了 64 位 ARM 和 x86_64 的 Unity 运行库；其他架构的手机退回原来的 2D 动态场景 */
    val supported: Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    /** 这次进程里 Unity 用的风格：第一次被问到时定下来，之后设置里改了也要重启才换 */
    val launchStyle: String by lazy { Store.config.value.settings.sceneStyle.takeIf { it == "hd2d" } ?: "voxel" }

    /** Unity 已经启动过（再换风格就得重启 App） */
    val started get() = player != null

    fun view(ctx: Context): FrameLayout {
        val p = player ?: UnityPlayerForActivityOrService(ctx.applicationContext, object : IUnityPlayerLifecycleEvents {
            override fun onUnityPlayerUnloaded() {}
            override fun onUnityPlayerQuitted() {}
        }).also { player = it }
        val frame = p.frameLayout
        (frame.parent as? ViewGroup)?.removeView(frame)
        return frame
    }

    /** 只从指定的容器上摘下（视口在相框和全屏之间换位置时，旧容器释放不能把新容器里的画面摘掉） */
    fun detach(from: ViewGroup) {
        val frame = player?.frameLayout ?: return
        if (frame.parent === from) from.removeView(frame)
    }

    /** 首页的 3D 区域出现 / 消失（相框和全屏可能短暂同时存在，所以计数） */
    fun setVisible(v: Boolean) {
        visibleCount = (visibleCount + if (v) 1 else -1).coerceAtLeast(0)
        sync()
    }

    fun onActivityResume() { activityResumed = true; sync() }
    fun onActivityPause() { activityResumed = false; sync() }
    fun onWindowFocus(focused: Boolean) { player?.windowFocusChanged(focused) }
    fun onConfiguration(cfg: android.content.res.Configuration) { player?.configurationChanged(cfg) }
    fun onTrimMemory(level: Int) {
        val p = player ?: return
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            p.onTrimMemory(UnityPlayerForActivityOrService.MemoryUsage.Critical)
    }

    private var running = false
    private fun sync() {
        val p = player ?: return
        val want = visibleCount > 0 && activityResumed
        if (want == running) return
        running = want
        if (want) { p.onStart(); p.onResume(); p.windowFocusChanged(true) } else { p.onPause(); p.onStop() }
    }

    // ---------- 发给 Unity ----------

    private fun send(json: JSONObject) {
        if (ready) UnityPlayer.UnitySendMessage(BRIDGE, "OnMessage", json.toString())
    }

    private fun op(name: String) = JSONObject().put("op", name)

    /** 新的操作编号；同一件事（比如同一次喂食动画）用同一个编号，Unity 只演一次 */
    fun nextId(prefix: String = "op"): String = "$prefix-${System.currentTimeMillis()}-${++seq}"

    fun setPet(species: String, action: String, outfits: List<String>, owned: List<String> = listOf(species), outing: Boolean = true) {
        val json = op("pet").put("species", species).put("action", action).put("outfits", JSONArray(outfits))
            .put("owned", JSONArray(owned)).put("outing", outing)
        val s = json.toString()
        if (s == lastPet) return
        lastPet = s
        send(json)
    }

    fun stats(stamina: Int, satiety: Int, hydration: Int, coins: Int) {
        val json = op("stats").put("stamina", stamina).put("satiety", satiety).put("hydration", hydration).put("coins", coins)
        val s = json.toString()
        if (s == lastStats) return
        lastStats = s
        send(json)
    }

    fun env(hour: Int, minute: Int, night: Boolean, weather: String, tempC: Int?, rain: Boolean, snow: Boolean) {
        val json = op("env").put("hour", hour).put("minute", minute).put("night", night).put("weather", weather)
            .put("tempC", tempC ?: 20).put("rain", rain).put("snow", snow)
        val s = json.toString()
        if (s == lastEnv) return
        lastEnv = s
        send(json)
    }

    /** feed / drink / wardrobe / spin / sleep / pet_tap；id 为空时自动生成 */
    fun command(name: String, id: String? = null) = send(op(name).put("id", id ?: nextId(name)))

    /** 让宠物走到某件家具（热区编号，比如 food_bowl）旁边等着 */
    fun goTo(hotspot: String) = send(op("goto").put("text", hotspot).put("id", nextId("goto")))

    /** 衣柜面板关上：Unity 关衣柜，宠物出来转一圈 */
    fun wardrobeClose() = send(op("wardrobe_close"))

    fun say(text: String) = send(op("say").put("text", text))

    fun coins(delta: Int, id: String) { if (delta > 0) send(op("coins").put("id", id).put("delta", delta)) }

    /** 展开全场景：「更多 ›」、「宠物部屋」、卧室房门都走这里 */
    fun expand(source: String) {
        if (expanded) return
        expanded = true
        send(op("expand").put("source", source))
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        send(op("collapse"))
    }

    // ---------- Unity 发回来 ----------

    internal fun onMessage(raw: String) = main.post {
        Log.d(TAG, "← $raw")
        val evt = runCatching { JSONObject(raw) }.getOrNull() ?: return@post
        when (evt.optString("evt")) {
            "ready" -> {
                ready = true
                // Unity 刚起来（或场景重载）：把缓存的常态、数值、天气补发一遍；展开状态也同步过去
                listOf(lastPet, lastStats, lastEnv).forEach { s -> s?.let { UnityPlayer.UnitySendMessage(BRIDGE, "OnMessage", it) } }
                if (expanded) send(op("expand").put("source", "resume"))
            }
            "request_expand" -> expand(evt.optString("source", "door"))
            "pet_room" -> inPetRoom = evt.optString("source") == "enter"
            "collapsed" -> inPetRoom = false
        }
        _events.tryEmit(evt)
    }

    /** 换风格后重启 App：重新拉起首页，结束当前进程（Unity 运行时只能在新进程里换场景） */
    fun restartApp(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}

/** Unity 那边用 AndroidJavaClass 按名字调用这里，类名和方法名都不能改（proguard 里已保留）。 */
object UnityBridge {
    @JvmStatic fun isEmbedded(): Boolean = true
    @JvmStatic fun sceneStyle(): String = UnityHost.launchStyle
    @JvmStatic fun onUnityMessage(json: String) { UnityHost.onMessage(json) }
}
