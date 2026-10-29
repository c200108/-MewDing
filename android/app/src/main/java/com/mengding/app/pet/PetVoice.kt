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
import com.mengding.app.data.Pet
import com.mengding.app.data.Store
import com.mengding.app.logic.BubbleText
import com.mengding.app.logic.LineDeck
import com.mengding.app.logic.Mood
import com.mengding.app.logic.PetMood
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import kotlin.random.Random

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

    /**
     * 震一下。带上「提醒」用途：安卓 13 起，后台 App 不带用途的震动会被系统直接忽略（小米、华为更严），
     * 标成通知 / 提醒类之后，系统按用户的「通知震动」设置放行
     */
    fun vibrate(ctx: Context) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 140, 90, 200), -1)
        runCatching {
            when {
                Build.VERSION.SDK_INT >= 33 -> v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_NOTIFICATION))
                else -> @Suppress("DEPRECATION") v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
        }
    }

    /** 提醒用的叫声：走「通知」音量（不是媒体音量），后台也能放；再震一下 */
    fun alert(ctx: Context, species: String) {
        runCatching {
            android.media.MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                val afd = ctx.resources.openRawResourceFd(raw(species))
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
                setOnCompletionListener { it.release() }
                prepare(); start()
            }
        }
        vibrate(ctx)
    }
}

/**
 * 宠物说的话。看出门那只宠物的心情（PetMood）：饿、渴、困、难过、难受时说对应的话；
 * 高兴 / 平淡时一半说心情话，一半说用户在「我的」里设的话（用户改过的话照样会出现）。
 */
object Speech {
    private var last = ""
    private val deck = LineDeck()

    private fun custom(): String {
        val list = Store.cfg.settings.phrases.filter { it.isNotBlank() }.ifEmpty { DEFAULT_PHRASES }
        val pick = list.filter { it != last }.ifEmpty { list }.random()
        last = pick
        return pick
    }

    private fun useCustom(p: Pet?) = p == null || PetMood.of(p) in setOf(Mood.HAPPY, Mood.CALM) && Random.nextBoolean()

    /** 纯文字（悬浮窗、3D 小屋、通知用） */
    fun phrase(p: Pet? = PetSim.outing(Store.cfg)): String =
        if (useCustom(p)) custom() else deck.next(PetMood.of(p!!)).also { last = it }

    /**
     * 首页 2D 场景的气泡：可能带像素表情（见 BubbleText）。hasEmotes = 这个物种有没有表情图，
     * 没有就只说话；只发表情却画不出来时退回文字。
     */
    fun bubble(p: Pet? = PetSim.outing(Store.cfg), hasEmotes: Boolean): String {
        if (p == null) return custom()
        val c = BubbleText.compose(p, deck)
        val text = BubbleText.parse(c.bubble).second
        // 高兴 / 平淡时正文有一半换成用户设的话，表情照旧
        val withCustom = if (text.isNotEmpty() && useCustom(p)) c.copy(bubble = c.bubble.removeSuffix(text) + custom()) else c
        return BubbleText.forSpecies(withCustom, hasEmotes).also { last = BubbleText.plain(it) }
    }
}
