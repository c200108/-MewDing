package com.mengding.app.pet

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.mengding.app.R
import com.mengding.app.data.SoundMode
import com.mengding.app.data.Store
import com.mengding.app.logic.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「叮」：打卡、领萌点、完成任务、解锁徽章统一响一声铃铛（res/raw/ding.wav），
 * 同时让首页宠物项圈上的铃铛闪一下光（LiveScene 读 [flash]）。
 * 提示音选了「静音」、或者手机在静音 / 震动模式时只闪光不出声。
 */
object Ding {
    private val _flash = MutableStateFlow(0L)
    /** 最近一次叮的时间，铃铛闪光从这里开始算 */
    val flash: StateFlow<Long> = _flash
    private val main = Handler(Looper.getMainLooper())

    fun muted(ctx: Context): Boolean {
        if (Store.cfg.settings.sound == SoundMode.SILENT) return true
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return am.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    /** times = 3 时叮叮叮连响一串（小任务全勤） */
    fun ring(ctx: Context, times: Int = 1) {
        _flash.value = Time.now()
        if (muted(ctx)) return
        val app = ctx.applicationContext
        repeat(times.coerceIn(1, 5)) { i -> main.postDelayed({ playOnce(app) }, i * 170L) }
    }

    private fun playOnce(ctx: Context) {
        runCatching {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            MediaPlayer.create(ctx, R.raw.ding, attrs, ctx.getSystemService(AudioManager::class.java).generateAudioSessionId())?.apply {
                setVolume(.7f, .7f)
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }
}
