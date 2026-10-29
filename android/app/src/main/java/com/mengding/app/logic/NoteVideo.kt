package com.mengding.app.logic

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * 便签里的视频文件：插入时把选中的视频复制成 App 私有目录里的新文件，便签只记相对路径
 * （videos/<便签 id>/video_<时间>.mp4，相对 Store.mediaDir）。
 * 从便签里删掉视频块、或删掉整条便签时，文件（和封面图）一起删掉。
 * 这里只管文件，不碰 Android 接口，方便单元测试（NoteVideoTest）。
 */
class NoteVideoFiles(private val root: File) {
    companion object {
        const val MAX_BYTES = 500L * 1024 * 1024
        val EXTS = setOf("mp4", "m4v", "3gp", "webm", "mkv", "mov")
        fun isVideo(rel: String?) = rel != null && rel.startsWith("videos/") && rel.substringAfterLast('.').lowercase() in EXTS
        /** 封面图跟视频同名，扩展名换成 .jpg */
        fun posterOf(rel: String) = rel.substringBeforeLast('.') + ".jpg"
    }

    class Cancelled : IOException("cancelled")

    fun file(rel: String): File = File(root, rel)

    /** 防止便签数据被改坏后删到媒体目录外面去 */
    private fun safe(rel: String): File? {
        if (rel.isBlank() || rel.contains("..") || rel.startsWith("/") || rel.contains('\\')) return null
        val f = File(root, rel)
        return f.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    fun noteDir(noteId: String): File = File(root, "videos/" + noteId.filter { it.isLetterOrDigit() || it == '-' })

    /** 新视频的相对路径；同一毫秒重复插入时加序号 */
    fun newRel(noteId: String, ext: String, now: Long): String {
        val dir = noteDir(noteId)
        val e = ext.lowercase().takeIf { it in EXTS } ?: "mp4"
        var n = 0
        while (true) {
            val name = if (n == 0) "video_$now.$e" else "video_${now}_$n.$e"
            if (!File(dir, name).exists()) return "videos/${dir.name}/$name"
            n++
        }
    }

    /**
     * 复制视频：先写 .part，完成后改名；取消或出错时把半截文件删掉。
     * onProgress 回传 0..1（总大小未知时传 -1）。
     */
    fun copy(input: InputStream, rel: String, total: Long, onProgress: (Float) -> Unit, cancelled: () -> Boolean): File {
        val dst = safe(rel) ?: throw IOException("bad path")
        dst.parentFile?.mkdirs()
        val part = File(dst.path + ".part")
        try {
            part.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var done = 0L
                var lastReport = -1
                while (true) {
                    if (cancelled()) throw Cancelled()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done > MAX_BYTES) throw IOException("too big")
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    if (pct != lastReport) { lastReport = pct; onProgress(if (total > 0) pct / 100f else -1f) }
                }
            }
            if (!part.renameTo(dst)) throw IOException("rename failed")
            return dst
        } catch (e: Throwable) {
            part.delete(); dst.delete()
            deleteEmptyDir(dst.parentFile)
            throw e
        }
    }

    /** 删一个视频（连同封面）；便签目录空了也删掉 */
    fun delete(rel: String?) {
        if (!isVideo(rel)) return
        val f = safe(rel!!) ?: return
        f.delete()
        safe(posterOf(rel))?.delete()
        deleteEmptyDir(f.parentFile)
    }

    /** 删整条便签的视频目录 */
    fun deleteNote(noteId: String) {
        val d = noteDir(noteId)
        if (d.canonicalPath.startsWith(File(root, "videos").canonicalPath + File.separator)) d.deleteRecursively()
    }

    /**
     * 启动时清理：删掉没有任何便签引用的视频（比如插入后还没保存 App 就被杀了），封面跟着视频走，
     * 复制到一半留下的 .part 也删。最近 10 分钟内的文件不动（可能正在插入）。返回删掉的文件数。
     */
    fun cleanOrphans(referenced: Set<String>, keepNewerThan: Long = System.currentTimeMillis() - 10 * 60_000L): Int {
        val videos = File(root, "videos")
        if (!videos.isDirectory) return 0
        var n = 0
        videos.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) { if (dir.delete()) n++; return@forEach }
            dir.listFiles()?.forEach { f ->
                val rel = "videos/${dir.name}/${f.name}"
                val keep = f.lastModified() > keepNewerThan || when {
                    f.name.endsWith(".jpg") -> referenced.any { posterOf(it) == rel }
                    else -> rel in referenced
                }
                if (!keep && f.delete()) n++
            }
            deleteEmptyDir(dir)
        }
        return n
    }

    private fun deleteEmptyDir(d: File?) {
        if (d != null && d.isDirectory && d.list().isNullOrEmpty() && d != root) d.delete()
    }
}

/** 视频插入、封面、时长（Android 部分） */
object NoteVideo {
    val files get() = NoteVideoFiles(com.mengding.app.data.Store.mediaDir)

    sealed class Result {
        data class Ok(val rel: String, val durationMs: Long, val title: String?) : Result()
        data class Fail(val msg: String) : Result()
        object Cancelled : Result()
    }

    fun sizeOf(ctx: Context, uri: Uri): Long = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun extOf(ctx: Context, uri: Uri): String {
        val t = ctx.contentResolver.getType(uri) ?: ""
        return when {
            t.endsWith("webm") -> "webm"; t.endsWith("3gpp") -> "3gp"; t.endsWith("matroska") -> "mkv"
            t.endsWith("quicktime") -> "mov"; else -> "mp4"
        }
    }

    /** 在后台线程调用 */
    fun import(ctx: Context, uri: Uri, noteId: String, onProgress: (Float) -> Unit, cancelled: () -> Boolean): Result {
        val size = sizeOf(ctx, uri)
        if (size > NoteVideoFiles.MAX_BYTES) return Result.Fail("视频超过 500MB，请先剪短或压缩再插入")
        val fs = files
        val rel = fs.newRel(noteId, extOf(ctx, uri), Time.now())
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return Result.Fail("视频读取失败")
            input.use { fs.copy(it, rel, size, onProgress, cancelled) }
            val dur = probe(fs.file(rel)) ?: run { fs.delete(rel); return Result.Fail("这个文件不是能播放的视频") }
            savePoster(fs, rel)
            // 相册选择器给的名字常是一串数字 id，这种就不当标题
            Result.Ok(rel, dur, NoteMedia.displayName(ctx, uri)?.takeUnless { n -> n.all { it.isDigit() } })
        } catch (_: NoteVideoFiles.Cancelled) {
            Result.Cancelled
        } catch (e: IOException) {
            if (e.message == "too big") Result.Fail("视频超过 500MB，请先剪短或压缩再插入")
            else Result.Fail(if (ctx.filesDir.usableSpace < 50L * 1024 * 1024) "手机空间不够了" else "视频复制失败")
        } catch (_: Throwable) {
            fs.delete(rel); Result.Fail("视频复制失败")
        }
    }

    /** 时长；没有视频轨道返回 null */
    private fun probe(f: File): Long? = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(f.absolutePath)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrNull()

    private fun savePoster(fs: NoteVideoFiles, rel: String) = runCatching {
        val bmp = MediaMetadataRetriever().use { r ->
            r.setDataSource(fs.file(rel).absolutePath)
            r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
                ?: r.getFrameAtTime(0)
        } ?: return@runCatching
        fs.file(NoteVideoFiles.posterOf(rel)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        bmp.recycle()
    }

    fun poster(rel: String): Bitmap? {
        val f = files.file(NoteVideoFiles.posterOf(rel))
        if (!f.exists()) savePoster(files, rel)
        return android.graphics.BitmapFactory.decodeFile(f.absolutePath)
    }
}
