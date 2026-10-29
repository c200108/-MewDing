package com.mengding.app.logic

import com.mengding.app.data.CustomLog
import com.mengding.app.data.CustomReminder
import com.mengding.app.data.PrivateState
import java.time.LocalDate
import java.time.LocalTime

/**
 * 自定义提醒（需求 C）的纯计算：哪天响、几点响、今天还剩哪些没完成。排程并进 Reminders 的「最近一个闹钟」。
 * 固定时刻型：每个时刻完成一次就不再响；间隔型：只记今天完成了几次，每个到点照响（勿扰时段里不响，和喝水一样）。
 */
object Customs {
    /** 新建时可选的模板：名字、图标、默认时间方式 */
    data class Template(val name: String, val icon: String, val make: (String) -> CustomReminder)

    val templates = listOf(
        Template("吃药", "pill") { CustomReminder(it, "吃药", "pill", times = listOf("08:30", "20:30")) },
        Template("久坐起来动一动", "stretch") { CustomReminder(it, "久坐起来动一动", "stretch", mode = "interval", everyMin = 60, from = "09:00", to = "18:00", repeat = "workdays") },
        Template("护眼休息", "eye") { CustomReminder(it, "护眼休息", "eye", mode = "interval", everyMin = 45, from = "09:00", to = "22:00") },
        Template("护肤", "skin") { CustomReminder(it, "护肤", "skin", times = listOf("07:50", "22:30")) },
    )

    val icons = listOf("pill", "stretch", "eye", "skin", "bell")

    /** 这一天要不要提醒：开着、没过结束日期、重复规则命中 */
    fun activeOn(r: CustomReminder, day: LocalDate): Boolean {
        if (!r.enabled) return false
        r.endDate?.let { e -> runCatching { LocalDate.parse(e) }.getOrNull()?.let { if (day.isAfter(it)) return false } }
        val dow = day.dayOfWeek.value
        return when (r.repeat) {
            "workdays" -> dow <= 5
            "weekdays" -> dow in r.weekdays
            else -> true
        }
    }

    /** 这一天的所有到点时刻（从早到晚）。间隔型从 from 起每隔 everyMin 分钟，到 to 为止（可以跨午夜） */
    fun slots(r: CustomReminder): List<LocalTime> {
        if (r.mode != "interval") return r.times.map { Time.parse(it) }.distinct().sorted()
        val step = r.everyMin.coerceIn(10, 720).toLong()
        val start = Time.parse(r.from); val end = Time.parse(r.to)
        // 时段长度（分钟）：to 早于 from 就是跨午夜；两头都算（09:00–18:00 每小时 → 9 点到 18 点共 10 次）
        val span = java.time.Duration.between(start, end).toMinutes().let { if (it <= 0) it + 1440 else it }
        return (0..span / step).filter { it * step < 1440 }.map { start.plusMinutes(it * step) }.distinct()
    }

    fun logsOn(p: PrivateState, id: String, day: LocalDate): List<CustomLog> =
        p.customLog.filter { it.id == id && it.day == day.toString() }

    fun skipped(p: PrivateState, id: String, day: LocalDate) = logsOn(p, id, day).any { it.skipped }

    /** 今天完成了几次 */
    fun doneCount(p: PrivateState, id: String, day: LocalDate) = logsOn(p, id, day).count { !it.skipped }

    /** 固定时刻型：今天还没完成的时刻；间隔型：今天跳过了就是空，否则全部（间隔型不按时刻销账） */
    fun pending(r: CustomReminder, p: PrivateState, day: LocalDate): List<LocalTime> {
        if (!activeOn(r, day) || skipped(p, r.id, day)) return emptyList()
        if (r.mode == "interval") return slots(r)
        val done = logsOn(p, r.id, day).filter { !it.skipped }.map { it.slot }.toSet()
        return slots(r).filter { Time.fmt(it) !in done }
    }

    /** 首页「今天的其他提醒」：今天要提醒、还没做完的（固定型还有没打勾的时刻；间隔型今天没跳过） */
    fun todayList(p: PrivateState, day: LocalDate): List<CustomReminder> =
        p.customs.filter { activeOn(it, day) && pending(it, p, day).isNotEmpty() }

    /** 通知标题：短名字说「该吃药啦」，长的直接用名字 */
    fun title(r: CustomReminder) = if (r.name.length <= 4) "叮～该${r.name}啦" else "叮～${r.name}"

    /** 提前打勾 / 完成：固定型销掉最早没完成的那个时刻（或指定的 slot），间隔型记一次 */
    fun slotToMark(r: CustomReminder, p: PrivateState, day: LocalDate, slot: String?): String {
        if (r.mode == "interval") return slot ?: ""
        // 通知上点的就是那个时刻；首页提前打勾没有时刻，销最早没完成的；都完成了返回空
        return slot ?: pending(r, p, day).firstOrNull()?.let { Time.fmt(it) } ?: ""
    }
}
