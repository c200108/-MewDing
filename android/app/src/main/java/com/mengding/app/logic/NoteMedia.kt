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

    /**
     * 插入音频：先按文件头认出真实格式（文件管理器报的类型经常不准：.flac 报成 octet-stream、扩展名乱写），
     * 存成对应扩展名；认不出、或明显不是音频的，返回 "!原因" 给界面提示。
     * 支持：MP3、AAC / M4A / MP4、FLAC、OGG（Vorbis / Opus）、WAV、AMR、WebM / MKA、3GP、MIDI
     */
    fun importAudio(ctx: Context, uri: Uri): String? = runCatching {
        val head = ctx.contentResolver.openInputStream(uri)!!.use { s -> ByteArray(64).also { s.read(it) } }
        val ext = sniffAudio(head) ?: run {
            val t = ctx.contentResolver.getType(uri) ?: ""
            if (t.startsWith("audio/")) extOf(ctx, uri, "m4a") else return "!这个文件不是能播放的音频"
        }
        if (ext == "ape" || ext == "wma" || ext == "dsf") return "!暂不支持 ${ext.uppercase()} 格式，请转成 FLAC 或 MP3 再插入"
        import(ctx, uri, ext)
    }.getOrNull()

    /** 文件名（不带扩展名），插入的音频用它当标题 */
    fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

    /** 按文件头认格式 */
    fun sniffAudio(h: ByteArray): String? {
        fun at(i: Int, s: String) = h.size >= i + s.length && s.indices.all { h[i + it] == s[it].code.toByte() }
        fun u(i: Int) = if (i < h.size) h[i].toInt() and 0xFF else -1
        return when {
            at(0, "fLaC") -> "flac"
            at(0, "ID3") -> "mp3"
            u(0) == 0xFF && (u(1) and 0xE0) == 0xE0 -> if ((u(1) and 0x06) == 0) "aac" else "mp3"   // MPEG 帧头：layer 位为 0 是 ADTS AAC
            at(0, "OggS") -> if (String(h, Charsets.ISO_8859_1).contains("OpusHead")) "opus" else "ogg"
            at(0, "RIFF") && at(8, "WAVE") -> "wav"
            at(4, "ftyp") -> if (at(8, "3gp")) "3gp" else "m4a"
            at(0, "#!AMR") -> "amr"
            u(0) == 0x1A && u(1) == 0x45 && u(2) == 0xDF && u(3) == 0xA3 -> "webm"
            at(0, "MThd") -> "mid"
            at(0, "MAC ") -> "ape"
            u(0) == 0x30 && u(1) == 0x26 && u(2) == 0xB2 -> "wma"
            at(0, "DSD ") -> "dsf"
            else -> null
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

    fun delete(name: String?) {
        name ?: return
        if (NoteVideoFiles.isVideo(name)) { runCatching { NoteVideo.files.delete(name) }; return }
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return
        runCatching { file(name).delete() }
    }

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
    /** 系统播放器打不开时的后备：自己解码成 PCM 用 AudioTrack 放（高采样率 FLAC、部分 Opus / WebM 在一些手机上 MediaPlayer 不认） */
    private var pcm: PcmPlayer? = null
    /** 播放失败时给界面的提示（界面显示后清掉） */
    val error = MutableStateFlow<String?>(null)

    fun toggle(name: String) {
        val cur = _state.value
        if (cur.file == name && (mp != null || pcm != null)) {
            mp?.let { p ->
                if (p.isPlaying) { p.pause(); _state.value = cur.copy(playing = false) }
                else { p.start(); _state.value = cur.copy(playing = true) }
            }
            pcm?.let { p -> if (p.playing) p.pause() else p.resume(); _state.value = cur.copy(playing = p.playing) }
            return
        }
        stop()
        val f = NoteMedia.file(name)
        var p: MediaPlayer? = null
        val ok = runCatching {
            p = MediaPlayer().apply {
                setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                java.io.FileInputStream(f).use { setDataSource(it.fd) }   // 用文件描述符：不受扩展名影响
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); error.value = "这段音频播放出错了"; true }
                prepare()
                start()
            }
        }.isSuccess
        if (ok) mp = p
        else {
            runCatching { p?.release() }
            val pp = PcmPlayer(f) { stop() }
            if (pp.start()) pcm = pp
            else { error.value = "手机不支持这种音频格式，可以转成 MP3 或 FLAC（16 位）再插入"; return }
        }
        _state.value = State(name, true)
    }

    fun position(): Int = runCatching { mp?.currentPosition ?: pcm?.positionMs ?: 0 }.getOrDefault(0)

    fun stop() {
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null
        pcm?.release(); pcm = null
        _state.value = State()
    }
}

/** MediaExtractor + MediaCodec 解码 → AudioTrack 播放（后台线程），系统解码器能解的格式都能放 */
private class PcmPlayer(private val file: File, private val onEnd: () -> Unit) {
    @Volatile var playing = false; private set
    @Volatile private var alive = true
    @Volatile var positionMs = 0; private set
    private val lock = Object()

    fun start(): Boolean {
        val ex = android.media.MediaExtractor()
        return runCatching {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val codec = android.media.MediaCodec.createDecoderByType(fmt.getString(android.media.MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            playing = true
            Thread({ loop(ex, codec) }, "note-pcm").start()
            true
        }.getOrElse { runCatching { ex.release() }; false }
    }

    private fun loop(ex: android.media.MediaExtractor, codec: android.media.MediaCodec) {
        var track: android.media.AudioTrack? = null
        val info = android.media.MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (alive) {
                synchronized(lock) { while (alive && !playing) lock.wait() }
                if (!alive) break
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || (o >= 0 && track == null)) {
                    val of = codec.outputFormat
                    val rate = of.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    val ch = of.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    val enc = if (of.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)) of.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                        else android.media.AudioFormat.ENCODING_PCM_16BIT
                    val mask = if (ch == 1) android.media.AudioFormat.CHANNEL_OUT_MONO else android.media.AudioFormat.CHANNEL_OUT_STEREO
                    track?.release()
                    track = android.media.AudioTrack.Builder()
                        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                        .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(rate).setEncoding(enc).setChannelMask(mask).build())
                        .setBufferSizeInBytes(android.media.AudioTrack.getMinBufferSize(rate, mask, enc).coerceAtLeast(8192) * 2)
                        .setTransferMode(android.media.AudioTrack.MODE_STREAM).build().apply { play() }
                }
                if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    val bytes = ByteArray(info.size); buf.get(bytes); buf.clear()
                    track?.write(bytes, 0, bytes.size)
                    positionMs = (info.presentationTimeUs / 1000).toInt()
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { track?.stop(); track?.release() }
            runCatching { codec.stop(); codec.release() }
            runCatching { ex.release() }
            if (alive) android.os.Handler(android.os.Looper.getMainLooper()).post { onEnd() }
        }
    }

    fun pause() { playing = false }
    fun resume() { synchronized(lock) { playing = true; lock.notifyAll() } }
    fun release() { alive = false; synchronized(lock) { lock.notifyAll() } }
}
