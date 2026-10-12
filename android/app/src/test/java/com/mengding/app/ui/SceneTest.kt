package com.mengding.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** 首页动态背景：按时间 / 下雪 / 12 月选场景，以及每个场景导出的文件齐全 */
class SceneTest {
    private fun at(month: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, month, 11, h, m)

    @Test
    fun picksSceneByTime() {
        assertEquals("dawn_cottage", Scenes.auto(at(10, 5, 30), false))
        assertEquals("river_spring", Scenes.auto(at(10, 9), false))
        assertEquals("village", Scenes.auto(at(10, 14), false))
        assertEquals("autumn", Scenes.auto(at(10, 18, 59), false))
        assertEquals("night_river", Scenes.auto(at(10, 19), false))
        assertEquals("bedroom", Scenes.auto(at(10, 23), false))
        assertEquals("bedroom", Scenes.auto(at(10, 3), false))
    }

    @Test
    fun snowAndChristmasOverride() {
        assertEquals("snow_park", Scenes.auto(at(10, 14), true))
        assertEquals("christmas", Scenes.auto(at(12, 20), false))
        assertEquals("christmas", Scenes.auto(at(12, 2), false))
        assertEquals("village", Scenes.auto(at(12, 14), false))
        assertEquals("snow_park", Scenes.auto(at(12, 20), true))
    }

    @Test
    fun everySceneHasItsFiles() {
        Scenes.ALL.keys.forEach { k ->
            val dir = File("src/main/assets/scenes/$k")
            val json = File(dir, "scene.json").readText()
            assertTrue("$k 没有宠物站位", json.contains("\"spots\""))
            Regex("\"src\":\\s*\"([^\"]+)\"").findAll(json).forEach { m ->
                assertTrue("$k 缺 ${m.groupValues[1]}", File(dir, m.groupValues[1]).exists())
            }
        }
    }
}
