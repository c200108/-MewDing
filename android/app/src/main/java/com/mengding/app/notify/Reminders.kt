package com.mengding.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mengding.app.data.ConfigState
import com.mengding.app.data.PrivateState
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Customs
import com.mengding.app.logic.Period
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import java.time.LocalDate
import java.time.LocalTime

/**
 * 提醒引擎。只排一个闹钟：最近的那一条提醒；闹钟响了就把到点的都发出去，再排下一个。
 * 所有时间按手机当地时区；手机改时区、重启后由 [BootReceiver] 重新排。
 */
object Reminders {
    /** 新的只能往后加：通知 id 按序号算（[Notifier.idFor]） */
    enum class Kind { WATER, MEAL, SLEEP_PRE, SLEEP, MORNING, PERIOD_SOON, PERIOD_CARE, CARE_SOON, CARE_TIP, NOTE, CUSTOM, TEST }

    data class Due(val kind: Kind, val at: Long, val extra: String = "")

    fun upcoming(st: ConfigState, p: PrivateState, from: Long, hours: Int = 30): List<Due> {
        val out = mutableListOf<Due>()
        val s = st.settings
        val today = Time.today()
        val until = from + hours * 3_600_000L
        val wake = Time.parse(st.profile.wake)
        val sleep = Time.parse(st.profile.sleep)
        val forecast = if (Period.enabled(st)) Period.forecast(p) else null

        for (offset in -1..1) {
            val day = today.plusDays(offset.toLong())
            val isToday = day == today

            if (s.waterOn) {
                val off = isToday && st.daily.date == day.toString() && st.daily.waterOff
                val reached = isToday && p.water.filter { Time.dateOf(it.t) == today }.sumOf { it.ml } >= s.waterGoalMl
                if (!off && !reached) {
                    var t = wake.plusMinutes(s.waterIntervalMin.toLong())
                    var guard = 0
                    while (guard++ < 48 && Time.inWindow(t, wake, sleep.minusMinutes(20))) {
                        val inQuiet = s.quietOn && Time.inWindow(t, Time.parse(s.quietStart), Time.parse(s.quietEnd))
                        if (!inQuiet) out += Due(Kind.WATER, Time.at(day, t))
                        val next = t.plusMinutes(s.waterIntervalMin.toLong())
                        if (next < t) break
                        t = next
                    }
                }
            }
            if (s.mealOn) {
                for (m in st.profile.meals) {
                    if (isToday && p.meals.any { it.day == day.toString() && it.label == m.label }) continue
                    out += Due(Kind.MEAL, Time.at(day, m.time), m.label)
                }
            }
            if (s.sleepOn) {
                val bedDone = p.sleeps.any { it.day == day.toString() && it.bedAt != null }
                if (!bedDone) {
                    // 睡觉时间如果在凌晨（例如 00:30），属于前一晚
                    val bedDay = if (sleep < LocalTime.of(5, 0)) day.plusDays(1) else day
                    out += Due(Kind.SLEEP_PRE, Time.at(bedDay, sleep.minusMinutes(30)))
                    out += Due(Kind.SLEEP, Time.at(bedDay, sleep))
                }
                val lastNight = day.minusDays(1).toString()
                if (p.sleeps.none { it.day == lastNight && it.wakeAt != null }) {
                    out += Due(Kind.MORNING, Time.at(day, wake.plusMinutes(10)))
                }
            }
            if (forecast?.nextStart != null) {
                val soon = forecast.nextStart.minusDays(s.periodAdvanceDays.toLong())
                if (soon == day) out += Due(Kind.PERIOD_SOON, Time.at(day, "09:00"))
                if (s.careTips && inPeriod(day, p, forecast)) out += Due(Kind.PERIOD_CARE, Time.at(day, "10:00"))
            }
            // 自定义提醒：固定时刻型做完的那个时刻不再响；间隔型和喝水一样，勿扰时段里不响；今天跳过了就都不响。
            // extra = "id|08:00"；间隔型跨午夜的时刻（早于开始时间）算到第二天
            for (r in p.customs) {
                if (!Customs.activeOn(r, day)) continue
                val start = Time.parse(r.from)
                for (t in Customs.pending(r, p, day)) {
                    val interval = r.mode == "interval"
                    if (interval && s.quietOn && Time.inWindow(t, Time.parse(s.quietStart), Time.parse(s.quietEnd))) continue
                    val at = if (interval && t < start) Time.at(day.plusDays(1), t) else Time.at(day, t)
                    out += Due(Kind.CUSTOM, at, "${r.id}|${Time.fmt(t)}")
                }
            }
            // 便签定时提醒
            for (n in Store.notesNow.notes) {
                val r = n.reminder ?: continue
                if (r.enabled && noteOn(r, day)) out += Due(Kind.NOTE, Time.at(day, r.time), n.id)
            }
            if (Period.careEnabled(st) && s.careModeOn) {
                for (c in p.care) {
                    val n = Period.careNext(c, day) ?: continue
                    if (n.minusDays(2) == day) out += Due(Kind.CARE_SOON, Time.at(day, "09:00"), c.id)
                    if (Period.careInPeriod(c, day)) out += Due(Kind.CARE_TIP, Time.at(day, "10:30"), c.id)
                }
            }
        }
        for (z in st.snoozes) {
            runCatching { Kind.valueOf(z.kind) }.getOrNull()?.let { out += Due(it, z.at, z.extra) }
        }
        return out.filter { it.at > from && it.at <= until }.sortedBy { it.at }
    }

    /** 便签提醒在这一天要不要响。工作日 = 周一到周五，休息日 = 周六周日（暂不含法定节假日调休） */
    fun noteOn(r: com.mengding.app.data.NoteReminder, day: LocalDate): Boolean = when (r.repeat) {
        com.mengding.app.data.Repeat.ONCE -> r.date == day.toString()
        com.mengding.app.data.Repeat.DAILY -> true
        com.mengding.app.data.Repeat.WORKDAYS -> day.dayOfWeek.value <= 5
        com.mengding.app.data.Repeat.WEEKENDS -> day.dayOfWeek.value >= 6
        com.mengding.app.data.Repeat.DATES -> day.toString() in r.dates
    }

    /** 便签提醒下一次什么时候响（往后找一年） */
    fun noteNext(r: com.mengding.app.data.NoteReminder, from: Long = Time.now()): Long? {
        if (!r.enabled) return null
        var d = Time.dateOf(from)
        repeat(370) {
            if (noteOn(r, d)) {
                val t = Time.at(d, r.time)
                if (t > from) return t
            }
            d = d.plusDays(1)
        }
        return null
    }

    private fun inPeriod(day: LocalDate, p: PrivateState, f: Period.Forecast): Boolean {
        if (day.toString() in p.periodDays) return true
        val c = f.current ?: return false
        return !day.isBefore(c.start) && day.isBefore(c.start.plusDays(f.periodLen.toLong()))
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = Time.now()
        val next = upcoming(Store.cfg, Store.priv, now).firstOrNull()?.at
        val pi = pending(ctx)
        // 最多隔 12 小时醒一次重新看，这样很久以后的便签提醒也不会漏
        val at = minOf(next ?: Long.MAX_VALUE, now + 12 * 3_600_000L)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        Store.update { it.copy(alarmAt = at, scheduledAt = now) }
    }

    /** 首页顶部「刚才有提醒没响」：打开 App 时 [checkMissed] 发现了就亮，点进体检或关掉后熄 */
    val missedNotice = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * 打开 App 时先查有没有漏响（要在 reschedule 之前调，reschedule 会把「排过的时间」更新到现在，下次就不会重复提示），有就亮提示并重排。
     * 不动 lastAlarmCheck：进程如果是被晚到的闹钟拉起来的，fire 还要靠它补发最近 10 分钟的提醒。
     */
    fun checkMissed(ctx: Context) {
        if (missed(Store.cfg, Store.priv, Time.now()).isNotEmpty()) {
            missedNotice.value = true
            reschedule(ctx)
        }
    }

    /** 闹钟还挂在系统里吗（被清理、强停后会没了） */
    fun alarmPending(ctx: Context): Boolean = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    /** 发一条测试提醒：走和真提醒完全一样的路径（排进闹钟 → AlarmReceiver → fire → 通知 + 桌面气泡） */
    fun test(ctx: Context, seconds: Int) {
        Store.update { st -> st.copy(snoozes = (st.snoozes + com.mengding.app.data.Snooze(Kind.TEST.name, Time.now() + seconds * 1000L)).takeLast(20)) }
        reschedule(ctx)
    }

    /**
     * 应该响却没响的提醒：上次闹钟检查之后、到现在 15 分钟之前就该响的（手机把闹钟清掉、App 被强停时会这样）。
     * 打开 App 时检查，有就在首页顶部提示去体检。
     */
    fun missed(st: ConfigState, p: PrivateState, now: Long): List<Due> {
        // 从「最后一次闹钟响过」和「最后一次排闹钟」里较晚的那个往后看：期间新加的、改过时间的提醒不算漏响
        val from = maxOf(st.lastAlarmCheck, st.scheduledAt)
        if (from <= 0) return emptyList()
        return upcoming(st, p, from, hours = 48).filter { it.at <= now - 15 * 60_000L && it.kind != Kind.TEST }
    }

    fun nextTime(): Long? = upcoming(Store.cfg, Store.priv, Time.now()).firstOrNull()?.at

    /** 闹钟响了：把到点的提醒都发出去 */
    fun fire(ctx: Context) {
        val now = Time.now()
        Store.update { PetSim.simulate(it, now) }
        val st = Store.cfg
        // 太久没检查（关机、被杀），只补发最近 10 分钟的，免得一下子弹一堆
        val from = maxOf(st.lastAlarmCheck, now - 10 * 60_000L)
        val due = upcoming(st, Store.priv, from).filter { it.at <= now + 60_000L }
        // 同类只发最新的一条
        due.groupBy { it.kind to it.extra }.values.map { it.last() }.forEach { Notifier.remind(ctx, it) }
        Store.update { s ->
            s.copy(lastAlarmCheck = now, snoozes = s.snoozes.filter { it.at > now + 60_000L })
        }
        reschedule(ctx)
    }
}
