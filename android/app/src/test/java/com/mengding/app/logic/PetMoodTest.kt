package com.mengding.app.logic

import com.mengding.app.data.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 宠物心情：按数值分组、语句轮换不重复、表情映射、气泡表情标记 */
class PetMoodTest {
    private fun pet(st: Float = 85f, sa: Float = 80f, hy: Float = 80f, sleeping: Boolean = false) =
        Pet("p", "dog", "小蜜", stamina = st, satiety = sa, hydration = hy, sleeping = sleeping)

    @Test fun moodFromStats() {
        assertEquals(Mood.HAPPY, PetMood.of(pet()))
        assertEquals(Mood.CALM, PetMood.of(pet(st = 60f, sa = 55f, hy = 55f)))
        assertEquals(Mood.SAD, PetMood.of(pet(st = 45f, sa = 70f, hy = 70f)))
        assertEquals(Mood.TIRED, PetMood.of(pet(st = 20f)))
        assertEquals(Mood.TIRED, PetMood.of(pet(sleeping = true)))
        assertEquals(Mood.HUNGRY, PetMood.of(pet(sa = 20f)))
        assertEquals(Mood.THIRSTY, PetMood.of(pet(hy = 20f)))
        // 渴比饿先说；又饿又渴到见底是难受
        assertEquals(Mood.THIRSTY, PetMood.of(pet(sa = 20f, hy = 20f)))
        assertEquals(Mood.PAIN, PetMood.of(pet(sa = 5f, hy = 5f)))
        assertEquals(Mood.PAIN, PetMood.of(pet(st = 2f, sa = 12f)))
    }

    @Test fun everyMoodHasTenDistinctLines() {
        Mood.entries.forEach { m ->
            val l = PetMood.LINES.getValue(m)
            assertEquals("$m 句数", 10, l.size)
            assertEquals("$m 不重复", 10, l.toSet().size)
            assertTrue(l.all { it.isNotBlank() && it.length <= 16 })
        }
    }

    @Test fun deckCoversAllBeforeRepeatingAndNeverRepeatsBackToBack() {
        val deck = LineDeck(rnd = Random(7))
        val seen = (1..10).map { deck.next(Mood.HAPPY) }
        assertEquals("一副牌 10 句全出且不重复", PetMood.LINES.getValue(Mood.HAPPY).toSet(), seen.toSet())
        var last = seen.last()
        repeat(200) {
            val s = deck.next(Mood.HAPPY)
            assertNotEquals("不能连着两句一样", last, s)
            last = s
        }
    }

    @Test fun emoteMapping() {
        assertEquals(setOf(Emote.LAUGH, Emote.LOVE), PetMood.emotes(Mood.HAPPY).toSet())
        assertEquals(setOf(Emote.COOL, Emote.SMUG), PetMood.emotes(Mood.CALM).toSet())
        assertEquals(listOf(Emote.ANGRY), PetMood.emotes(Mood.HUNGRY))
        assertEquals(listOf(Emote.ANGRY), PetMood.emotes(Mood.THIRSTY))
        listOf(Mood.SAD, Mood.PAIN, Mood.TIRED).forEach { assertEquals(listOf(Emote.CRY), PetMood.emotes(it)) }
        // 精灵表列号和表情顺序一致：爱心、墨镜、生气、斜眼、大笑、大哭
        assertEquals(listOf(0, 1, 2, 3, 4, 5), Emote.entries.map { it.col })
    }

    @Test fun bubbleTextRoundTrip() {
        assertEquals(null to "你好", BubbleText.parse("你好"))
        assertEquals(Emote.CRY to "好渴", BubbleText.parse(BubbleText.encode("好渴", Emote.CRY)))
        assertEquals(Emote.LOVE to "", BubbleText.parse(BubbleText.encode("", Emote.LOVE)))
        assertEquals("好渴", BubbleText.plain(BubbleText.encode("好渴", Emote.CRY)))
        assertNull(BubbleText.parse(null).first)
        // 坏标记原样当文字
        assertEquals(null to "\u0001XX", BubbleText.parse("\u0001XX"))
    }

    @Test fun composeRates() {
        val deck = LineDeck(rnd = Random(1))
        val rnd = Random(42)
        var withEmote = 0; var onlyEmote = 0
        val n = 4000
        repeat(n) {
            val c = BubbleText.compose(pet(), deck, rnd)
            val (e, text) = BubbleText.parse(c.bubble)
            assertTrue(c.fallback in PetMood.LINES.getValue(Mood.HAPPY))
            if (e != null) {
                withEmote++
                assertTrue(e in PetMood.emotes(Mood.HAPPY))
                if (text.isEmpty()) onlyEmote++ else assertEquals(c.fallback, text)
            }
        }
        assertEquals(.45, withEmote / n.toDouble(), .03)
        assertEquals(.25, onlyEmote / withEmote.toDouble(), .04)
    }

    @Test fun speciesWithoutEmotesFallsBackToText() {
        val only = BubbleText.Composed(BubbleText.encode("", Emote.LAUGH), "今天心情超好！")
        assertEquals("今天心情超好！", BubbleText.forSpecies(only, hasEmotes = false))
        assertEquals(only.bubble, BubbleText.forSpecies(only, hasEmotes = true))
        val both = BubbleText.Composed(BubbleText.encode("好饿", Emote.ANGRY), "好饿")
        assertEquals("好饿", BubbleText.forSpecies(both, hasEmotes = false))
        val plain = BubbleText.Composed("嗯", "嗯")
        assertEquals("嗯", BubbleText.forSpecies(plain, hasEmotes = false))
    }
}
