package com.mengding.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 喂食动画：一口口吃掉的分段，以及美术导出的分段图、爪子层、捧食物锚点是否齐全 */
class FeedAnimTest {
    private val assets = File("src/main/assets")

    @Test
    fun biteStagesGoFromWholeToAlmostGone() {
        val t = PetFx.Transient("eat", until = 5000, foodKey = "rice_ball", start = 0)
        val stages = (0..5000 step 250).map { t.biteStage(it.toLong()) }
        assertEquals(0, stages.first())
        assertEquals(Sprites.BITE_STAGES - 1, stages.last())
        assertTrue("只会越吃越少", stages.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("每一段都会出现", (0 until Sprites.BITE_STAGES).toSet(), stages.toSet())
    }

    @Test
    fun everyFoodHasBiteStages() {
        val keys = Regex("\"key\":\\s*\"(\\w+)\"").findAll(File(assets, "foods/foods.json").readText()).map { it.groupValues[1] }.toList()
        assertTrue(keys.size >= 12)
        keys.forEach { k ->
            (0 until Sprites.BITE_STAGES).forEach { s -> assertTrue("缺 $k 第 $s 段", File(assets, "foods/bite/${k}_$s.png").exists()) }
        }
    }

    @Test
    fun everyPetCanHoldFood() {
        val meta = File(assets, "pets/meta.json").readText()
        val pets = Regex("\\{\\s*\"key\":\\s*\"(\\w+)\",\\s*\"name\"").findAll(meta).map { it.groupValues[1] }.toList()
        assertTrue(pets.size >= 15)
        val hold = meta.substringAfter("\"hold\"").substringBefore("\"paws_rows\"")
        pets.forEach { p ->
            assertTrue("$p 没有捧食物锚点", hold.contains("\"$p\""))
            assertTrue("$p 没有爪子层", File(assets, "pets/paws/$p.png").exists())
        }
        assertTrue(meta.contains("\"paws_rows\"") && meta.substringAfter("\"paws_rows\"").substringBefore("]").let { "\"eat\"" in it && "\"drink\"" in it })
    }
}
