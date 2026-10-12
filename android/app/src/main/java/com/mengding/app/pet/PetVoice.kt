package com.mengding.app.pet

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mengding.app.R
import com.mengding.app.data.DEFAULT_PHRASES
import com.mengding.app.data.Store
import com.mengding.app.logic.Time

/**
 * 宠物叫声：每种宠物一段合成音效（res/raw/cry_<宠物>.wav），点击或按住宠物时播放。
 * 走"媒体音量"（USAGE_GAME）：0.2.0 用的是系统提示音通道，很多手机上默认静音或跟着"触摸提示音"开关，听不到。
 */
object PetVoice {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private var pending: Int? = null
    private var lastAt = 0L

    fun raw(species: String): Int = when (species) {
        "dog" -> R.raw.cry_dog
        "guinea_pig" -> R.raw.cry_guinea_pig
        "sugar_glider" -> R.raw.cry_sugar_glider
        "snake" -> R.raw.cry_snake
        "lizard" -> R.raw.cry_lizard
        "chinchilla" -> R.raw.cry_chinchilla
        "hamster" -> R.raw.cry_hamster
        "turtle" -> R.raw.cry_turtle
        "bunny" -> R.raw.cry_bunny
        "clownfish" -> R.raw.cry_clownfish
        "hedgehog" -> R.raw.cry_hedgehog
        "swan" -> R.raw.cry_swan
        "pig" -> R.raw.cry_pig
        "tarantula" -> R.raw.cry_tarantula
        else -> R.raw.cry_cat
    }

    @Synchronized
    private fun ensure(ctx: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        p.setOnLoadCompleteListener { sp, id, status ->
            if (status == 0) {
                loaded += id
                if (pending == id) { pending = null; sp.play(id, 1f, 1f, 1, 0, 1f) }
            }
        }
        listOf("cat", "dog", "guinea_pig", "sugar_glider", "snake", "lizard").forEach {
            ids[it] = p.load(ctx.applicationContext, raw(it), 1)
        }
        pool = p
        return p
    }

    /** App 启动时先把叫声加载好，第一次点宠物就能立刻出声 */
    fun warmUp(ctx: Context) { runCatching { ensure(ctx) } }

    /** force = true 时忽略"叫声"开关（便签的"气泡 + 叫声"提醒用） */
    fun play(ctx: Context, species: String, force: Boolean = false) {
        if (!force && !Store.cfg.settings.petSoundOn) return
        val now = Time.now()
        if (now - lastAt < 450) return
        lastAt = now
        val p = ensure(ctx)
        val id = ids[species] ?: return
        if (id in loaded) {
            if (p.play(id, 1f, 1f, 1, 0, 1f) == 0) fallback(ctx, species)
        } else {
            // 第一次还没加载好：先用 MediaPlayer 直接放，SoundPool 留着下次用
            pending = null
            fallback(ctx, species)
        }
    }

    private fun fallback(ctx: Context, species: String) {
        runCatching {
            android.media.MediaPlayer.create(ctx.applicationContext, raw(species))?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    fun vibrate(ctx: Context) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 140, 90, 200), -1)) }
    }
}

/** 宠物说的话 */
object Speech {
    private var last = ""

    fun phrase(): String {
        val list = Store.cfg.settings.phrases.filter { it.isNotBlank() }.ifEmpty { DEFAULT_PHRASES }
        val pick = list.filter { it != last }.ifEmpty { list }.random()
        last = pick
        return pick
    }
}
