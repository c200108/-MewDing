package com.mengding.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 在线更新：版本比较、从 GitHub Releases 列表里挑最新安装包 */
class UpdateCheckTest {
    private fun rel(tag: String, body: String = "", assets: String = """[{"name":"MewDing-${tag.removePrefix("v")}.apk","size":123,"browser_download_url":"https://x/$tag.apk"}]""",
                    draft: Boolean = false, pre: Boolean = true) =
        """{"tag_name":"$tag","name":"萌叮 $tag","draft":$draft,"prerelease":$pre,"published_at":"2026-10-29T10:00:00Z","body":${quote(body)},"assets":$assets}"""

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test fun versionNumbers() {
        assertEquals(listOf(4, 0, 7), UpdateCheck.numbers("v4.0.7-test"))
        assertEquals(listOf(0, 3, 22), UpdateCheck.numbers("0.3.22-beta02"))
        assertTrue(UpdateCheck.compareNames("4.0.0-test", "0.3.22-beta02") > 0)
        assertTrue(UpdateCheck.compareNames("0.3.10-test", "0.3.9-test") > 0)   // 按数字比，不按字符串
        assertEquals(0, UpdateCheck.compareNames("v4.0.6-test", "4.0.6-test"))
    }

    @Test fun picksNewestWithApk() {
        val json = "[" + listOf(
            rel("v0.3.9-test"), rel("v4.0.6-test", "versionCode: 33"), rel("v0.3.22-beta02"),
            rel("v4.0.7-test", "更新说明\nversionCode: 34\nsha256: " + "ab".repeat(32)),
            rel("v0.3.4", assets = "[]"),                        // 没安装包
            rel("v9.9.9", draft = true),                          // 草稿不算
        ).joinToString(",") + "]"
        val list = UpdateCheck.parse(json)
        assertEquals(4, list.size)
        val latest = UpdateCheck.latest(list)!!
        assertEquals("4.0.7-test", latest.versionName)
        assertEquals(34, latest.versionCode)
        assertEquals("ab".repeat(32), latest.sha256)
        assertEquals("MewDing-4.0.7-test.apk", latest.apkName)
    }

    @Test fun ignoresUnityDebugAndX86Apks() {
        val assets = """[{"name":"MewDing-Unity-0.3.10-test.apk","size":1,"browser_download_url":"u"},
            {"name":"MewDing-0.3.10-test.apk","size":2,"browser_download_url":"a"}]"""
        val r = UpdateCheck.parse("[" + rel("v0.3.10-test", assets = assets) + "]").single()
        assertEquals("MewDing-0.3.10-test.apk", r.apkName)
        assertFalse(UpdateCheck.isAppApk("MewDing-0.3.11-test-debug-x86_64.apk"))
        assertTrue(UpdateCheck.parse("[" + rel("v1.0.0", assets = """[{"name":"MewDing-Unity-1.apk","size":1,"browser_download_url":"u"}]""") + "]").isEmpty())
    }

    @Test fun newerThanCurrent() {
        val withCode = UpdateCheck.parse("[" + rel("v4.0.7-test", "versionCode: 34") + "]").single()
        assertTrue(UpdateCheck.isNewer(withCode, "4.0.6-test", 33))
        assertFalse(UpdateCheck.isNewer(withCode, "4.0.7-test", 34))
        val noCode = UpdateCheck.parse("[" + rel("v0.3.13-test") + "]").single()
        assertNull(noCode.versionCode)
        assertFalse(UpdateCheck.isNewer(noCode, "4.0.6-test", 33))   // 老版本不提示
    }

    @Test fun badJsonIsEmpty() {
        assertTrue(UpdateCheck.parse("not json").isEmpty())
        assertTrue(UpdateCheck.parse("{\"message\":\"API rate limit exceeded\"}").isEmpty())
        assertNull(UpdateCheck.latest(emptyList()))
    }

    @Test fun readableNotesDropsMachineLines() {
        val n = UpdateCheck.readableNotes("新增在线更新\nversionCode: 34\nsha256: ${"c".repeat(64)}\n修复若干问题")
        assertEquals("新增在线更新\n修复若干问题", n)
    }
}
