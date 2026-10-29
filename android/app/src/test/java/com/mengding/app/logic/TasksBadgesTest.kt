package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import com.mengding.app.data.MealLog
import com.mengding.app.data.Pet
import com.mengding.app.data.PrivateState
import com.mengding.app.data.SleepLog
import com.mengding.app.data.WaterLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 每日小任务和成就徽章（需求 D） */
class TasksBadgesTest {
    private val today = Time.today()
    private fun st(vararg tasks: String) = Economy.freshDaily(ConfigState()).let { it.copy(daily = it.daily.copy(tasks = tasks.toList())) }

    @Test fun drawIsStablePerDayAndRespectsPool() {
        val d = LocalDate.of(2026, 10, 25)
        assertEquals(Tasks.draw(d, true, true), Tasks.draw(d, true, true))
        repeat(60) { i ->
            val day = d.plusDays(i.toLong())
            val noCustom = Tasks.draw(day, hasCustom = false, morning = false)
            assertEquals(3, noCustom.distinct().size)
            assertFalse("没设自定义提醒不抽", "custom" in noCustom)
            assertFalse("下午不抽按时睡觉", "sleep" in noCustom)
        }
        // 早上、有自定义提醒时，两条都有机会抽到
        val seen = (0 until 200).flatMap { Tasks.draw(d.plusDays(it.toLong()), true, true) }.toSet()
        assertTrue("custom" in seen && "sleep" in seen)
    }

    @Test fun claimNeedsDoneAndOnlyOnce() {
        val s0 = st("pet", "game", "note")
        assertEquals(0, Tasks.claim(s0, PrivateState(), "pet").second)               // 还没摸够 3 次
        val s1 = s0.copy(daily = s0.daily.copy(pets = 3))
        val (s2, g, all) = Tasks.claim(s1, PrivateState(), "pet")
        assertEquals(10, g); assertFalse(all); assertEquals(10, s2.economy.points)
        assertEquals(0, Tasks.claim(s2, PrivateState(), "pet").second)               // 领过了
        assertEquals(0, Tasks.claim(s2, PrivateState(), "water").second)             // 不是今天的任务
    }

    @Test fun allThreeGiveBonusOnce() {
        var s = st("pet", "game", "note").let { it.copy(daily = it.daily.copy(pets = 5, games = 1, notes = 2)) }
        val p = PrivateState()
        s = Tasks.claim(s, p, "pet").first
        s = Tasks.claim(s, p, "game").first
        val (s3, g, all) = Tasks.claim(s, p, "note")
        assertTrue(all); assertEquals(10 + Tasks.BONUS, g)
        assertEquals(10 + 10 + 10 + 30, s3.economy.points)
        assertTrue(s3.daily.tasksBonus)
    }

    @Test fun recordBasedTasks() {
        val s = st("meals", "water", "sleep")
        val day = today.toString()
        val meals = s.profile.meals.map { MealLog(day, it.label, Time.now()) }
        val water = listOf(WaterLog(Time.now(), s.settings.waterGoalMl))
        val night = today.minusDays(1)
        val bed = Time.at(night, s.profile.sleep) + 10 * 60_000L
        val p = PrivateState(meals = meals, water = water, sleeps = listOf(SleepLog(night.toString(), bedAt = bed)))
        assertTrue(Tasks.done("meals", s, p)); assertTrue(Tasks.done("water", s, p))
        assertFalse("早上还没补记", Tasks.done("sleep", s, p))
        val woke = p.copy(sleeps = listOf(SleepLog(night.toString(), bedAt = bed, wakeAt = Time.now())))
        assertTrue(Tasks.done("sleep", s, woke))
        val late = woke.copy(sleeps = listOf(SleepLog(night.toString(), bedAt = bed + 50 * 60_000L, wakeAt = Time.now())))
        assertFalse("晚了 1 小时不算按时", Tasks.done("sleep", s, late))
    }

    @Test fun eachTaskAtMost50() = Tasks.pool.forEach { assertTrue(it.pts <= 50) }

    @Test fun badgesUnlockLevelsAndPayOnce() {
        val s0 = ConfigState().let { it.copy(economy = it.economy.copy(petCount = 120, gameClaims = 1, checkInDays = 7)) }
        val (s1, got) = Badges.settle(s0, PrivateState(), notes = 10)
        // 爱心 10/100 两级、手柄 1 级、便签 1/10 两级、铃铛 1/7 两级
        assertEquals(listOf(1, 2), got.filter { it.def.key == "love" }.map { it.level })
        assertEquals(5 + 15 + 5 + 5 + 15 + 5 + 15, s1.economy.points)
        assertEquals(2, s1.economy.badges["love"])
        assertTrue("同样的数值不再发", Badges.settle(s1, PrivateState(), 10).second.isEmpty())
        Badges.all.forEach { d -> d.rewards.forEach { assertTrue(it <= 50) } }
    }

    @Test fun badgeBackfillFromRecords() {
        val s = ConfigState().let {
            it.copy(pets = listOf(Pet("1", "cat", "a"), Pet("2", "dog", "b"), Pet("3", "dog", "c")),
                economy = it.economy.copy(ownedOutfits = listOf("a", "b", "c"), lastCheckIn = today.toString(), checkInStreak = 9))
        }
        val goal = s.settings.waterGoalMl
        val p = PrivateState(water = (0 until 7).map { WaterLog(Time.at(today.minusDays(it.toLong()), "10:00"), goal) })
        val v = Badges.values(Badges.seed(s), p, 0)
        assertEquals(7, v["water"]); assertEquals(2, v["pets"]); assertEquals(3, v["outfits"]); assertEquals(9, v["checkin"])
        assertEquals(2, Badges.level(Badges.collection.first { it.key == "water" }, 7))
        assertEquals(23, Badges.toNext(Badges.collection.first { it.key == "water" }, 7))
        assertEquals(null, Badges.toNext(Badges.bell, 100))
    }
}
