package com.mengding.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mengding.app.data.Store

/**
 * 小游戏的音效和背景音乐（开源 CC0 素材，来源见 美术/音乐音效/来源与许可.md）。
 *   assets/sfx/<名字>.wav   音效，SoundPool 播放
 *   assets/music/<名字>     背景音乐，循环播放；App 切到后台自动暂停
 * 走媒体音量；设置里 gameSound 关掉就全部静音。
 */
object GameAudio {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var bgm: MediaPlayer? = null
    private var bgmName: String? = null
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    @Synchronized
    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        pool = p
        ctx.assets.list("sfx")?.forEach { f ->
            runCatching { ctx.assets.openFd("sfx/$f").use { ids[f.substringBeforeLast('.')] = p.load(it, 1) } }
        }
    }

    private val on get() = Store.cfg.settings.gameSound

    fun play(name: String, volume: Float = 0.9f) {
        if (!on) return
        val id = ids[name] ?: return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    /** enabled：小游戏用「游戏声音」开关（默认），首页小镇用自己的背景音乐开关 */
    fun startMusic(ctx: Context, name: String, enabled: Boolean = on) {
        if (bgmName == name && bgm != null) { if (enabled) runCatching { bgm?.start() }; return }
        stopMusic()
        if (!enabled) return
        runCatching {
            val afd = ctx.assets.openFd("music/$name")
            bgm = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0.45f, 0.45f)
                prepare()
                start()
            }
            bgmName = name
        }
    }

    fun pauseMusic() { runCatching { if (bgm?.isPlaying == true) bgm?.pause() } }

    fun stopMusic() {
        runCatching { bgm?.stop(); bgm?.release() }
        bgm = null; bgmName = null
    }
}

/** 页面在前台、游戏在进行时放背景音乐；暂停、结束、离开页面、App 切后台就停 */
@Composable
fun GameMusic(name: String, playing: Boolean) {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { GameAudio.init(ctx) }
    LaunchedEffect(playing, cfg.settings.gameSound) {
        if (playing && cfg.settings.gameSound) GameAudio.startMusic(ctx, name) else GameAudio.pauseMusic()
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) GameAudio.pauseMusic()
            if (e == Lifecycle.Event.ON_RESUME && playing && Store.cfg.settings.gameSound) GameAudio.startMusic(ctx, name)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); GameAudio.stopMusic() }
    }
}

/**
 * 首页 HD-2D 小镇的背景音乐：在首页、打开了「首页背景音乐」时循环播放；切到后台、关掉开关时暂停，离开首页停掉
 * （小游戏页面有自己的背景音乐，进去时会换掉这首，回到首页再重新放）
 */
@Composable
fun HomeMusic(enabled: Boolean) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(enabled) {
        if (enabled) GameAudio.startMusic(ctx, "home_bgm.ogg", enabled = true) else GameAudio.pauseMusic()
    }
    DisposableEffect(owner, enabled) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) GameAudio.pauseMusic()
            if (e == Lifecycle.Event.ON_RESUME && enabled) GameAudio.startMusic(ctx, "home_bgm.ogg", enabled = true)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) { onDispose { GameAudio.stopMusic() } }
}

/** 游戏页右上角的震动开关（方向键、操作键按下时震一下） */
@Composable
fun VibrateToggle() {
    val cfg by Store.config.collectAsState()
    val on = cfg.settings.gameVibrate
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Icon(Ic.Vibrate, if (on) "关掉按键震动" else "打开按键震动",
        Modifier.size(26.dp).clickable {
            Store.update { it.copy(settings = it.settings.copy(gameVibrate = !on)) }
            // 打开时立刻震一下，用户马上知道这台手机能不能震
            if (!on) GameHaptics.buzz(ctx)
        },
        tint = if (on) P.mintDeep else P.line)
}

/** 游戏页右上角的声音开关 */
@Composable
fun SoundToggle() {
    val cfg by Store.config.collectAsState()
    val on = cfg.settings.gameSound
    Icon(if (on) Ic.Sound else Ic.Mute, if (on) "关掉声音" else "打开声音",
        Modifier.size(26.dp).clickable { Store.update { it.copy(settings = it.settings.copy(gameSound = !on)) } },
        tint = if (on) P.mintDeep else P.sub)
}
