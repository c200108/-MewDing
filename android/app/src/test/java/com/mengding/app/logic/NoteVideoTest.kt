package com.mengding.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** 便签视频的文件生命周期：插入 → 复制成新文件 → 删块 / 删便签 → 文件消失 */
class NoteVideoTest {
    private fun root(): File = Files.createTempDirectory("notevideo").toFile()
    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun copyCreatesNewFileUnderNoteDir() {
        val r = root(); val fs = NoteVideoFiles(r)
        val src = bytes(700_000)
        val rel = fs.newRel("note-1", "mp4", 123L)
        assertEquals("videos/note-1/video_123.mp4", rel)
        val progress = mutableListOf<Float>()
        val f = fs.copy(ByteArrayInputStream(src), rel, src.size.toLong(), { progress += it }, { false })
        assertTrue(f.exists())
        assertTrue(f.readBytes().contentEquals(src))
        assertFalse(File(f.path + ".part").exists())
        assertEquals(1f, progress.last(), 0.001f)
        assertTrue(NoteVideoFiles.isVideo(rel))
    }

    @Test fun sameMillisecondGetsUniqueName() {
        val fs = NoteVideoFiles(root())
        val a = fs.newRel("n", "mp4", 5L)
        fs.copy(ByteArrayInputStream(bytes(10)), a, 10, {}, { false })
        val b = fs.newRel("n", "mp4", 5L)
        assertTrue(a != b)
    }

    @Test fun deleteBlockRemovesVideoPosterAndEmptyDir() {
        val r = root(); val fs = NoteVideoFiles(r)
        val rel = fs.newRel("n2", "mp4", 1L)
        fs.copy(ByteArrayInputStream(bytes(100)), rel, 100, {}, { false })
        fs.file(NoteVideoFiles.posterOf(rel)).writeBytes(bytes(10))
        fs.delete(rel)
        assertFalse(fs.file(rel).exists())
        assertFalse(fs.file(NoteVideoFiles.posterOf(rel)).exists())
        assertFalse(fs.noteDir("n2").exists())
    }

    @Test fun deleteNoteRemovesAllItsVideosOnly() {
        val r = root(); val fs = NoteVideoFiles(r)
        val a = fs.newRel("keep", "mp4", 1L); fs.copy(ByteArrayInputStream(bytes(10)), a, 10, {}, { false })
        val b = fs.newRel("gone", "mp4", 1L); fs.copy(ByteArrayInputStream(bytes(10)), b, 10, {}, { false })
        val c = fs.newRel("gone", "webm", 2L); fs.copy(ByteArrayInputStream(bytes(10)), c, 10, {}, { false })
        fs.deleteNote("gone")
        assertFalse(fs.file(b).exists()); assertFalse(fs.file(c).exists()); assertFalse(fs.noteDir("gone").exists())
        assertTrue(fs.file(a).exists())
    }

    @Test fun cancelLeavesNoPartialFile() {
        val r = root(); val fs = NoteVideoFiles(r)
        val rel = fs.newRel("n3", "mp4", 1L)
        var reads = 0
        try {
            fs.copy(ByteArrayInputStream(bytes(2_000_000)), rel, 2_000_000, {}, { ++reads > 2 })
            fail("should cancel")
        } catch (_: NoteVideoFiles.Cancelled) {}
        assertFalse(fs.file(rel).exists())
        assertFalse(File(fs.file(rel).path + ".part").exists())
        assertFalse(fs.noteDir("n3").exists())
    }

    @Test fun refusesPathsOutsideMediaDir() {
        val r = root(); val outside = File(r.parentFile, "outside_${System.nanoTime()}.mp4").apply { writeBytes(bytes(5)) }
        val fs = NoteVideoFiles(r)
        fs.delete("videos/../../${outside.name}")
        assertTrue(outside.exists())
        try { fs.copy(ByteArrayInputStream(bytes(5)), "videos/../x.mp4", 5, {}, { false }); fail() } catch (_: IOException) {}
        assertFalse(NoteVideoFiles.isVideo("abc.m4a"))
        assertFalse(NoteVideoFiles.isVideo("abc.mp4"))   // 旧的扁平媒体文件不走视频删除逻辑
        outside.delete()
    }

    @Test fun noteIdIsSanitized() {
        val fs = NoteVideoFiles(root())
        assertEquals("videos/abc/video_1.mp4", fs.newRel("../a/b\\c", "mp4", 1L))
    }

    @Test fun cleanOrphansKeepsReferencedAndRecent() {
        val r = root(); val fs = NoteVideoFiles(r)
        val used = fs.newRel("a", "mp4", 1L); fs.copy(ByteArrayInputStream(bytes(10)), used, 10, {}, { false })
        fs.file(NoteVideoFiles.posterOf(used)).writeBytes(bytes(3))
        val orphan = fs.newRel("b", "mp4", 1L); fs.copy(ByteArrayInputStream(bytes(10)), orphan, 10, {}, { false })
        fs.file(NoteVideoFiles.posterOf(orphan)).writeBytes(bytes(3))
        File(fs.file(orphan).path + ".part").writeBytes(bytes(3))
        // 刚写的文件默认不动
        assertEquals(0, fs.cleanOrphans(setOf(used)))
        val removed = fs.cleanOrphans(setOf(used), keepNewerThan = Long.MAX_VALUE)
        assertEquals(3, removed)
        assertTrue(fs.file(used).exists()); assertTrue(fs.file(NoteVideoFiles.posterOf(used)).exists())
        assertFalse(fs.noteDir("b").exists())
    }
}