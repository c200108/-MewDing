package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import com.mengding.app.data.Pet
import com.mengding.app.data.PrivateState
import com.mengding.app.pet.PetFx
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 萌点规则补充、喝水上限、经期预测、宠物数值补算和动作优先级（需求 B） */
class RulesTest {
    @After fun clearFx() = PetFx.clear()

    // ---------------- 萌点 ----------------

    @Test fun reminderDoneCapsAt30PerDay() {
        var st = ConfigState(); var total = 0
        repeat(20) { val (s, g) = Economy.reminderDone(st); st = s; total += g }
        assertEquals(30, total); assertEquals(30, st.economy.points)
    }

    @Test fun spendNeedsEnoughPoints() {
        val st = ConfigState().let { it.copy(economy = it.economy.copy(points = 5)) }
        assertNull("不够不扣", Economy.spend(st, 6))
        assertEquals(0, Economy.spend(st, 5)!!.economy.points)
    }

    @Test fun gameRatios() {
        assertEquals(18, Economy.advPoints(37))   // 金币 : 萌点 = 2 : 1
        assertEquals(33, Economy.pacPoints(100))  // 豆 : 萌点 = 3 : 1
        assertEquals(0, Economy.pacPoints(2))
    }

    @Test fun checkInCountsTotalDaysForBadge() {
        val (s1, _) = Economy.checkIn(ConfigState())
        assertEquals(1, s1.economy.checkInDays)
        // 徽章上线前已经联签 5 天的老用户：今天签到后累计 6 天
        val old = ConfigState().let { it.copy(economy = it.economy.copy(lastCheckIn = Time.today().minusDays(1).toString(), checkInStreak = 5)) }
        assertEquals(6, Economy.checkIn(old).first.economy.checkInDays)
    }

    @Test fun bulkPriceIs80Percent() {
        assertEquals(8, Economy.bulkPrice(2, 5)); assertEquals(2 * 4, Economy.bulkPrice(2, 4))
    }

    // ---------------- 喝水 ----------------

    @Test fun waterCapIsTwiceGoalAndGoalAwardedOnce() {
        val st = ConfigState()
        val cap = Actions.waterCap(st)
        assertEquals(st.settings.waterGoalMl * 2, cap)
        assertNull(Actions.waterRejection(cap - 300, 300, cap))
        assertNotNull("这一杯会超", Actions.waterRejection(cap - 200, 300, cap))
        assertNotNull("已经到上限", Actions.waterRejection(cap, 100, cap))
        val (s1, g1) = Economy.waterGoal(st)
        assertEquals(10, g1)
        assertEquals("达标只奖一次", 0, Economy.waterGoal(s1).second)
    }

    // ---------------- 经期 ----------------

    @Test fun periodForecastAndAdvanceDay() {
        val today = LocalDate.of(2026, 10, 25)
        // 三次经期，间隔 28 天，每次 5 天
        val starts = listOf(today.minusDays(60), today.minusDays(32), today.minusDays(4))
        val days = starts.flatMap { s -> (0L until 5L).map { s.plusDays(it).toString() } }
        val f = Period.forecast(PrivateState(periodDays = days), today)
        assertEquals(28, f.cycleLen); assertEquals(5, f.periodLen)
        assertEquals(today.minusDays(4), f.current?.start)
        assertEquals(today.plusDays(24), f.nextStart)
        assertEquals(f.nextStart!!.minusDays(14), f.ovulation)
        // 不在经期时下一次从最近一次 +28 天算
        val later = Period.forecast(PrivateState(periodDays = days), today.plusDays(10))
        assertNull(later.current); assertEquals(today.plusDays(24), later.nextStart)
    }

    // ---------------- 宠物数值补算 ----------------

    /** 一个不在睡觉时段里的 2 小时窗口（今天 10–12 点，睡觉时段默认 23:00–07:30） */
    private val ten = Time.at(Time.today(), "10:00")

    @Test fun simulateOutingAwake() {
        val pet = Pet("p", "cat", "咪", stamina = 85f, satiety = 80f, hydration = 80f)
        val st = ConfigState(pets = listOf(pet), outingId = "p", lastSimAt = ten)
        val p = PetSim.simulate(st, ten + 2 * 3_600_000L).pets[0]
        // 体力 ≥ 70：消耗 ×0.7；出门醒着体力每小时 -6
        assertEquals(73f, p.stamina, .01f)
        assertEquals(80f - 3 * .7f * 2, p.satiety, .01f)
        assertEquals(80f - 4 * .7f * 2, p.hydration, .01f)
    }

    @Test fun exhaustedPetSleepsThenWakesAt30() {
        val pet = Pet("p", "cat", "咪", stamina = 9.9f, satiety = 80f, hydration = 80f)
        val st = ConfigState(pets = listOf(pet), outingId = "p", lastSimAt = ten)
        val s1 = PetSim.simulate(st, ten + 15 * 60_000L).pets[0]
        assertTrue("体力不到 10 就睡着", s1.sleeping)
        // 睡着每小时 +12，睡到 30 以上自己醒
        val s2 = PetSim.simulate(st.copy(pets = listOf(s1)), ten + 15 * 60_000L + 2 * 3_600_000L).pets[0]
        assertFalse(s2.sleeping); assertTrue(s2.stamina >= 30f)
    }

    @Test fun simulateCapsAt72Hours() {
        val pet = Pet("p", "cat", "咪", stamina = 50f, satiety = 50f, hydration = 50f)
        val st = ConfigState(pets = listOf(pet), lastSimAt = ten)
        val a = PetSim.simulate(st, ten + 72 * 3_600_000L).pets[0]
        val b = PetSim.simulate(st, ten + 200 * 3_600_000L).pets[0]
        assertEquals(a.satiety, b.satiety, .01f)
    }

    // ---------------- 动作优先级 ----------------

    /** 睡觉时段挪到现在之后，免得测试时刚好落在睡觉时段 */
    private fun awake(): ConfigState {
        val now = Time.dateTimeOf(Time.now()).toLocalTime()
        return ConfigState().let { it.copy(profile = it.profile.copy(sleep = Time.fmt(now.plusHours(2)), wake = Time.fmt(now.plusHours(3)))) }
    }

    @Test fun actionPriority() {
        val st = awake()
        fun act(p: Pet) = PetFx.actionFor(p, st)
        val ok = Pet("p", "cat", "咪", stamina = 90f, satiety = 90f, hydration = 90f)
        assertEquals("energetic", act(ok))
        assertEquals("idle", act(ok.copy(stamina = 60f)))
        assertEquals("tired", act(ok.copy(stamina = 20f)))
        assertEquals("饿比困优先", "hungry", act(ok.copy(stamina = 20f, satiety = 10f)))
        assertEquals("渴比饿优先", "thirsty", act(ok.copy(stamina = 20f, satiety = 10f, hydration = 10f)))
        assertEquals("沉睡最优先（除了临时动作）", "exhausted", act(ok.copy(hydration = 10f, sleeping = true)))
        PetFx.play("pet", 60_000)
        assertEquals("临时动作盖过一切", "pet", act(ok.copy(sleeping = true)))
    }

    @Test fun sleepWindowMakesPetSleep() {
        val now = Time.dateTimeOf(Time.now()).toLocalTime()
        val st = ConfigState().let { it.copy(profile = it.profile.copy(sleep = Time.fmt(now.minusHours(1)), wake = Time.fmt(now.plusHours(1)))) }
        assertEquals("sleep", PetFx.actionFor(Pet("p", "cat", "咪", stamina = 90f, satiety = 90f, hydration = 90f), st))
    }
}
