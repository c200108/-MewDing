package com.mengding.app.logic

import com.mengding.app.data.CarePerson
import com.mengding.app.data.ConfigState
import com.mengding.app.data.Gender
import com.mengding.app.data.PrivateState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 经期预测：最近 6 个周期加权平均，越近的权重越大。预测仅供参考。 */
object Period {
    data class Cycle(val start: LocalDate, val end: LocalDate)

    data class Forecast(
        val cycleLen: Int,
        val periodLen: Int,
        val nextStart: LocalDate?,
        val ovulation: LocalDate?,
        val fertileFrom: LocalDate?,
        val fertileTo: LocalDate?,
        val confidence: String,
        val current: Cycle?,
    )

    /** 关心 TA 功能是否打开（便签入口 + 相关提醒） */
    fun careEnabled(st: ConfigState): Boolean =
        st.settings.careOn ?: (st.profile.gender == Gender.FEMALE)

    /** 自己的经期功能：只有女性有（可以在提醒设置里关掉）；男性、不愿透露都不显示。改性别不删数据 */
    fun enabled(st: ConfigState): Boolean =
        st.profile.gender == Gender.FEMALE && (st.settings.periodOn ?: true)

    fun cycles(days: List<String>): List<Cycle> {
        val sorted = days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.distinct().sorted()
        val out = mutableListOf<Cycle>()
        for (d in sorted) {
            val last = out.lastOrNull()
            // 相隔 2 天以内算同一次经期
            if (last != null && ChronoUnit.DAYS.between(last.end, d) <= 2) out[out.size - 1] = last.copy(end = d)
            else out += Cycle(d, d)
        }
        return out
    }

    fun forecast(p: PrivateState, today: LocalDate = Time.today()): Forecast {
        val cs = cycles(p.periodDays)
        if (cs.isEmpty()) return Forecast(28, 5, null, null, null, null, "还没有记录", null)
        val gaps = cs.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.start, b.start).toInt() }.filter { it in 15..60 }
        val recent = gaps.takeLast(6)
        val cycleLen = if (recent.isEmpty()) 28 else {
            var sum = 0.0; var w = 0.0
            recent.forEachIndexed { i, g -> sum += g * (i + 1); w += (i + 1) }
            Math.round(sum / w).toInt()
        }
        // 只标了开始那一天的不算经期长度，没有完整记录时按 5 天估
        val lens = cs.takeLast(6).map { ChronoUnit.DAYS.between(it.start, it.end).toInt() + 1 }.filter { it >= 2 }
        val periodLen = lens.average().let { if (it.isNaN()) 5 else Math.round(it).toInt() }.coerceIn(2, 10)
        val last = cs.last()
        val current = if (!today.isBefore(last.start) && !today.isAfter(last.start.plusDays(periodLen.toLong() - 1))) last else null
        var next = last.start.plusDays(cycleLen.toLong())
        while (next.isBefore(today.minusDays(periodLen.toLong() - 1)) || (current != null && next == last.start)) {
            next = next.plusDays(cycleLen.toLong())
        }
        val ov = next.minusDays(14)
        val conf = when (recent.size) {
            0 -> "只记录了一次，先按 28 天估算"
            in 1..2 -> "记录较少，仅供参考"
            else -> "根据最近 ${recent.size} 个周期推算"
        }
        return Forecast(cycleLen, periodLen, next, ov, ov.minusDays(5), ov.plusDays(1), conf, current)
    }

    fun daysUntil(d: LocalDate?, today: LocalDate = Time.today()): Long? = d?.let { ChronoUnit.DAYS.between(today, it) }

    /** 关心 TA：按最近一次开始日期和周期推下一次 */
    fun careNext(c: CarePerson, today: LocalDate = Time.today()): LocalDate? {
        val s = c.lastStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        var n = s
        while (n.plusDays(c.length.toLong() - 1).isBefore(today)) n = n.plusDays(c.cycle.toLong())
        return n
    }

    fun careInPeriod(c: CarePerson, today: LocalDate = Time.today()): Boolean {
        val n = careNext(c, today) ?: return false
        return !today.isBefore(n) && today.isBefore(n.plusDays(c.length.toLong()))
    }

    val careTips = listOf(
        "多喝温水，少喝冰饮",
        "小腹不舒服可以用暖水袋热敷",
        "今天别太累，早点休息",
        "吃点温热的食物，少吃生冷辛辣",
        "情绪起伏是正常的，对自己温柔一点",
        "适当散步比久坐更舒服",
        "记得按时更换卫生用品",
    )

    val careTaTips = listOf(
        "给 TA 倒一杯温水吧",
        "TA 这几天可能没精神，多体谅一点",
        "备点红糖姜茶或者暖宝宝",
        "今天少惹 TA 生气，多说几句好听的",
        "家务多做一点，让 TA 好好休息",
    )

    fun tipOfDay(list: List<String>, day: LocalDate = Time.today()) = list[(day.toEpochDay() % list.size).toInt()]
}
