package com.mengding.app.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.mengding.app.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/** 便签里的图片、录音、音频。文件都复制到 App 私有目录，原文件删了也不影响 */
object NoteMedia {
    fun file(name: String) = File(Store.mediaDir, name)

    /** 把相册图片或选中的音频复制进来，返回文件名 */
    fun import(ctx: Context, uri: Uri, ext: String): String? = runCatching {
        val name = "${UUID.randomUUID()}.$ext"
        ctx.contentResolver.openInputStream(uri)!!.use { input -> file(name).outputStream().use { input.copyTo(it) } }
        name
    }.getOrNull()

    fun extOf(ctx: Context, uri: Uri, fallback: String): String {
        val type = ctx.contentResolver.getType(uri) ?: return fallback
        return when {
            type.endsWith("png") -> "png"; type.endsWith("webp") -> "webp"; type.endsWith("gif") -> "gif"
            type.startsWith("image/") -> "jpg"
            type.endsWith("mpeg") || type.endsWith("mp3") -> "mp3"; type.endsWith("wav") -> "wav"
            type.endsWith("ogg") -> "ogg"; type.endsWith("flac") -> "flac"
            type.startsWith("audio/") -> "m4a"
            else -> fallback
        }
    }

    fun duration(name: String): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(file(name).absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        }
    }.getOrDefault(0L)

    /** 按显示宽度缩小解码，省内存 */
    fun bitmap(name: String, maxW: Int = 1080): Bitmap? = runCatching {
        val f = file(name)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxW) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun delete(name: String?) { name?.let { runCatching { file(it).delete() } } }

    fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}

/** 录音：AAC，存成 m4a */
class NoteRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    var name: String? = null; private set
    var startedAt = 0L; private set

    fun start(): Boolean = runCatching {
        val n = "${UUID.randomUUID()}.m4a"
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(NoteMedia.file(n).absolutePath)
        r.prepare()
        r.start()
        rec = r; name = n; startedAt = Time.now()
        true
    }.getOrDefault(false)

    /** 停止并返回文件名；太短（不到半秒）就丢掉 */
    fun stop(): String? {
        val r = rec ?: return null
        rec = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val n = name
        if (!ok || Time.now() - startedAt < 500) { NoteMedia.delete(n); return null }
        return n
    }

    fun cancel() {
        rec?.let { runCatching { it.stop() }; it.release() }
        rec = null
        NoteMedia.delete(name)
    }
}

/** 全局只放一段：点别的音频会停掉上一段 */
object NotePlayer {
    data class State(val file: String? = null, val playing: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var mp: MediaPlayer? = null

    fun toggle(name: String) {
        val cur = _state.value
        if (cur.file == name && mp != null) {
            val p = mp!!
            if (p.isPlaying) { p.pause(); _state.value = cur.copy(playing = false) }
            else { p.start(); _state.value = cur.copy(playing = true) }
            return
        }
        stop()
        runCatching {
            val p = MediaPlayer()
            p.setDataSource(NoteMedia.file(name).absolutePath)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            mp = p
            _state.value = State(name, true)
        }
    }

    fun position(): Int = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)

    fun stop() {
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null
        _state.value = State()
    }
}
