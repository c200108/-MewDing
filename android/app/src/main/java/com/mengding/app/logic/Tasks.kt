package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import com.mengding.app.data.PrivateState
import java.time.LocalDate
import kotlin.random.Random

/**
 * 每日小任务（需求 D）：每天从池里抽 3 条，做完点「领取」；3 条都领了再发全勤奖。
 * 抽签按日期做种子，同一天怎么重开都是这 3 条。都是纯函数，单元测试直接测。
 */
object Tasks {
    data class Def(val key: String, val title: String, val pts: Int, val goal: Int)

    val pool = listOf(
        Def("pet", "摸摸宠物 3 次", 10, 3),
        Def("game", "玩一局任意小游戏", 10, 1),
        Def("note", "写或改一条便签", 10, 1),
        Def("feed", "喂部屋里的宠物一次", 15, 1),
        Def("custom", "完成一条自定义提醒", 15, 1),
        Def("meals", "三餐都打卡", 20, 3),
        Def("water", "喝水达标", 25, 1),
        Def("sleep", "昨晚按时睡觉（早上补记后算完成）", 25, 1),
    )
    const val BONUS = 30
    const val COUNT = 3

    fun def(key: String) = pool.first { it.key == key }

    /**
     * 抽 3 条：没设自定义提醒不抽「完成一条自定义提醒」；「昨晚按时睡觉」只在早上（12 点前）抽，
     * 下午才第一次打开的话早上补记的时间已经过了。
     */
    fun draw(day: LocalDate, hasCustom: Boolean, morning: Boolean): List<String> {
        val cands = pool.map { it.key }.filter { (it != "custom" || hasCustom) && (it != "sleep" || morning) }
        return cands.shuffled(Random(day.toEpochDay() * 7919 + 17)).take(COUNT)
    }

    /** 今天还没抽就抽一次（先换到今天的 daily） */
    fun ensure(st0: ConfigState, hasCustom: Boolean, hour: Int): ConfigState {
        val st = Economy.freshDaily(st0)
        if (st.daily.tasks.isNotEmpty()) return st
        return st.copy(daily = st.daily.copy(tasks = draw(Time.today(), hasCustom, hour < 12)))
    }

    /** 某条任务现在的进度（达到 def.goal 算完成） */
    fun progress(key: String, st: ConfigState, p: PrivateState, day: LocalDate = Time.today()): Int {
        val d = if (st.daily.date == day.toString()) st.daily else com.mengding.app.data.Daily(date = day.toString())
        return when (key) {
            "pet" -> d.pets
            "game" -> d.games
            "note" -> d.notes
            "feed" -> d.feeds
            "custom" -> d.customs
            "meals" -> st.profile.meals.count { m -> p.meals.any { it.day == day.toString() && it.label == m.label } }
            "water" -> if (p.water.filter { Time.dateOf(it.t) == day }.sumOf { it.ml } >= st.settings.waterGoalMl) 1 else 0
            "sleep" -> if (sleptOnTime(st, p, day.minusDays(1).toString(), needWake = true)) 1 else 0
            else -> 0
        }
    }

    /** 那一晚是不是按时睡（和「我去睡了」的判定一样：离计划睡觉时间前后 30 分钟内）；needWake = 早上也补记了 */
    fun sleptOnTime(st: ConfigState, p: PrivateState, night: String, needWake: Boolean = false): Boolean {
        val log = p.sleeps.firstOrNull { it.day == night } ?: return false
        val bed = log.bedAt ?: return false
        if (needWake && log.wakeAt == null) return false
        val plan = Time.parse(st.profile.sleep)
        val diff = java.time.Duration.between(plan, Time.dateTimeOf(bed).toLocalTime()).toMinutes().let {
            if (it > 720) it - 1440 else if (it < -720) it + 1440 else it
        }
        return kotlin.math.abs(diff) <= 30
    }

    fun done(key: String, st: ConfigState, p: PrivateState) = progress(key, st, p) >= def(key).goal

    /**
     * 领取一条：没做完、不是今天的任务、领过了都返回 0。
     * 返回 (新状态, 这次得到的萌点, 是不是顺带发了全勤奖)
     */
    fun claim(st0: ConfigState, p: PrivateState, key: String): Triple<ConfigState, Int, Boolean> {
        val st = Economy.freshDaily(st0)
        val d = st.daily
        if (key !in d.tasks || key in d.tasksClaimed || !done(key, st, p)) return Triple(st, 0, false)
        val claimed = d.tasksClaimed + key
        val all = d.tasks.all { it in claimed } && !d.tasksBonus
        val gain = def(key).pts.coerceAtMost(50) + if (all) BONUS else 0
        return Triple(st.copy(
            economy = st.economy.copy(points = st.economy.points + gain),
            daily = d.copy(tasksClaimed = claimed, tasksBonus = d.tasksBonus || all),
        ), gain, all)
    }
}
