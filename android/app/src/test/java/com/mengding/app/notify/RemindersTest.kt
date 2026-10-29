package com.mengding.app.notify

import com.mengding.app.data.ConfigState
import com.mengding.app.data.CustomLog
import com.mengding.app.data.CustomReminder
import com.mengding.app.data.Gender
import com.mengding.app.data.PrivateState
import com.mengding.app.data.Snooze
import com.mengding.app.logic.Customs
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders.Kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/** 提醒排程：勿扰、跨午夜、换时区、只排最近一个、自定义提醒、漏响判断（需求 B / A / C） */
class RemindersTest {
    private val savedZone = TimeZone.getDefault()
    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private val today = Time.today()
    /** 从今天 0 点起看 30 小时 */
    private val midnight = Time.at(today, "00:00")
    private fun due(st: ConfigState, p: PrivateState = PrivateState(), from: Long = midnight) = Reminders.upcoming(st, p, from)
    private fun at(day: LocalDate, hhmm: String) = Time.at(day, hhmm)
    private fun base(f: (ConfigState) -> ConfigState = { it }) = f(ConfigState())

    @Test fun sortedSoTheFirstIsTheOnlyAlarm() {
        val list = due(base())
        assertTrue(list.isNotEmpty())
        assertEquals(list.sortedBy { it.at }, list)
        // 只排最近一个：reschedule 用的就是 upcoming 的第一个
        assertEquals(list.minOf { it.at }, list.first().at)
    }

    @Test fun quietSkipsWaterButKeepsMeals() {
        val st = base { it.copy(settings = it.settings.copy(quietOn = true, quietStart = "11:00", quietEnd = "13:00")) }
        val list = due(st).filter { Time.dateOf(it.at) == today }
        val water = list.filter { it.kind == Kind.WATER }.map { Time.dateTimeOf(it.at).toLocalTime() }
        assertTrue(water.isNotEmpty())
        assertTrue("勿扰时段里不提醒喝水", water.none { it >= LocalTime.of(11, 0) && it < LocalTime.of(13, 0) })
        assertTrue("午饭照常提醒（只是静音）", list.any { it.kind == Kind.MEAL && it.extra == "午餐" })
    }

    @Test fun quietWindowAcrossMidnight() {
        // 勿扰 22:00–08:00 跨午夜；喝水间隔 30 分钟，从 07:30 起床开始
        val st = base { it.copy(settings = it.settings.copy(quietOn = true, quietStart = "22:00", quietEnd = "08:00", waterIntervalMin = 30)) }
        val water = due(st).filter { it.kind == Kind.WATER }.map { Time.dateTimeOf(it.at).toLocalTime() }
        assertTrue(water.none { it < LocalTime.of(8, 0) || it >= LocalTime.of(22, 0) })
        assertTrue(water.contains(LocalTime.of(8, 0)))
    }

    @Test fun bedtimeAfterMidnightBelongsToTheNightBefore() {
        val st = base { it.copy(profile = it.profile.copy(sleep = "00:30", wake = "08:00")) }
        val sleeps = due(st, from = at(today, "12:00")).filter { it.kind == Kind.SLEEP }
        // 今晚 00:30 = 明天日期的 00:30
        assertTrue(sleeps.any { it.at == at(today.plusDays(1), "00:30") })
        assertTrue(due(st, from = at(today, "12:00")).any { it.kind == Kind.SLEEP_PRE && it.at == at(today, "23:59").plusMinutes(1) })
    }

    private fun Long.plusMinutes(m: Int) = this + m * 60_000L

    @Test fun followsPhoneTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val sh = Time.at(LocalDate.of(2026, 10, 25), "12:00")
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val ny = Time.at(LocalDate.of(2026, 10, 25), "12:00")
        assertEquals("同样是中午 12 点，纽约比上海晚 12 小时", 12 * 3_600_000L, ny - sh)
        assertEquals(ZoneId.of("America/New_York"), Time.zone())
        // 换了时区后，饭点仍然是当地的 12:00
        val lunch = Reminders.upcoming(ConfigState(), PrivateState(), Time.at(Time.today(), "00:00"))
            .first { it.kind == Kind.MEAL && it.extra == "午餐" }
        assertEquals(LocalTime.of(12, 0), Time.dateTimeOf(lunch.at).toLocalTime())
    }

    @Test fun mealsAlreadyLoggedAreSkipped() {
        val p = PrivateState(meals = listOf(com.mengding.app.data.MealLog(today.toString(), "午餐", Time.now())))
        val meals = due(base(), p).filter { it.kind == Kind.MEAL && Time.dateOf(it.at) == today }
        assertFalse(meals.any { it.extra == "午餐" })
        assertTrue(meals.any { it.extra == "晚餐" })
    }

    @Test fun snoozeIsScheduled() {
        val st = base { it.copy(snoozes = listOf(Snooze(Kind.TEST.name, midnight + 5_000))) }
        assertEquals(Kind.TEST, due(st).first().kind)
    }

    @Test fun periodAdvanceReminderDate() {
        val st = base { it.copy(profile = it.profile.copy(gender = Gender.FEMALE), settings = it.settings.copy(periodAdvanceDays = 2)) }
        // 上次 26 天前开始、5 天 → 下次在 2 天后，提前 2 天 = 今天 09:00 提醒
        val days = (0L until 5L).map { today.minusDays(26).plusDays(it).toString() } + (0L until 5L).map { today.minusDays(54).plusDays(it).toString() }
        val list = due(st, PrivateState(periodDays = days))
        assertTrue(list.any { it.kind == Kind.PERIOD_SOON && it.at == at(today, "09:00") })
    }

    // ---------------- 自定义提醒 ----------------

    private val pill = CustomReminder("pill", "吃药", "pill", times = listOf("08:30", "20:30"))

    @Test fun customFixedTimesAndDoneSlot() {
        val p0 = PrivateState(customs = listOf(pill))
        val all = due(base(), p0).filter { it.kind == Kind.CUSTOM && Time.dateOf(it.at) == today }
        assertEquals(listOf("pill|08:30", "pill|20:30"), all.map { it.extra })
        val done = p0.copy(customLog = listOf(CustomLog("pill", today.toString(), Time.now(), "08:30")))
        assertEquals("08:30 打过勾就不响了", listOf("pill|20:30"), due(base(), done).filter { it.kind == Kind.CUSTOM && Time.dateOf(it.at) == today }.map { it.extra })
        val skipped = p0.copy(customLog = listOf(CustomLog("pill", today.toString(), Time.now(), skipped = true)))
        assertTrue("今天跳过", due(base(), skipped).none { it.kind == Kind.CUSTOM && Time.dateOf(it.at) == today })
        assertEquals(1, Customs.todayList(done, today).size)
        val allDone = done.copy(customLog = done.customLog + CustomLog("pill", today.toString(), Time.now(), "20:30"))
        assertTrue("都做完了首页不再列", Customs.todayList(allDone, today).isEmpty())
    }

    @Test fun customIntervalWindowRepeatAndQuiet() {
        val r = CustomReminder("eye", "护眼休息", "eye", mode = "interval", everyMin = 60, from = "09:00", to = "18:00")
        assertEquals(10, Customs.slots(r).size)   // 9 点到 18 点每小时，两头都算
        val st = base { it.copy(settings = it.settings.copy(quietOn = true, quietStart = "12:00", quietEnd = "14:00")) }
        val list = due(st, PrivateState(customs = listOf(r))).filter { it.kind == Kind.CUSTOM && Time.dateOf(it.at) == today }
            .map { Time.dateTimeOf(it.at).toLocalTime() }
        assertEquals(8, list.size)
        assertFalse(list.contains(LocalTime.of(12, 0)) || list.contains(LocalTime.of(13, 0)))
        // 跨午夜的时段：22:00–01:00 每 30 分钟
        val night = r.copy(from = "22:00", to = "01:00", everyMin = 30)
        assertEquals(7, Customs.slots(night).size)
    }

    @Test fun customRepeatAndEndDate() {
        val mon = LocalDate.of(2026, 10, 26); val sat = LocalDate.of(2026, 10, 31)
        val work = pill.copy(repeat = "workdays")
        assertTrue(Customs.activeOn(work, mon)); assertFalse(Customs.activeOn(work, sat))
        val pick = pill.copy(repeat = "weekdays", weekdays = listOf(6))
        assertFalse(Customs.activeOn(pick, mon)); assertTrue(Customs.activeOn(pick, sat))
        val ended = pill.copy(endDate = "2026-10-27")
        assertTrue(Customs.activeOn(ended, LocalDate.of(2026, 10, 27))); assertFalse(Customs.activeOn(ended, LocalDate.of(2026, 10, 28)))
        assertFalse(Customs.activeOn(pill.copy(enabled = false), mon))
        assertEquals("叮～该吃药啦", Customs.title(pill))
        assertEquals("叮～久坐起来动一动", Customs.title(pill.copy(name = "久坐起来动一动")))
    }

    // ---------------- 应响未响 ----------------

    @Test fun missedOnlyCountsDueMoreThan15MinutesAgo() {
        val st = base { it.copy(lastAlarmCheck = at(today, "07:00"), scheduledAt = at(today, "07:00")) }
        val noon = at(today, "12:10")
        val missed = Reminders.missed(st, PrivateState(), noon)
        assertTrue("12:00 的午饭才过 10 分钟不算", missed.none { it.kind == Kind.MEAL && it.extra == "午餐" })
        assertTrue("08:00 的早餐算漏响", missed.any { it.kind == Kind.MEAL && it.extra == "早餐" })
        // 中途重排过（比如改了时间、App 打开过），排之前的不算
        val later = st.copy(scheduledAt = at(today, "11:00"))
        assertTrue(Reminders.missed(later, PrivateState(), noon).none { it.at < at(today, "11:00") })
        assertTrue("没排过就不判断", Reminders.missed(ConfigState(), PrivateState(), noon).isEmpty())
    }
}
