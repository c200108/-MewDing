package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import com.mengding.app.data.Daily

/**
 * 萌点规则（需求 2.5）。所有函数都是纯函数：传入旧状态，返回新状态和这次实际得到的萌点。
 */
object Economy {
    fun freshDaily(st: ConfigState): ConfigState {
        val today = Time.todayStr()
        return if (st.daily.date == today) st else st.copy(daily = Daily(date = today))
    }

    private fun add(st: ConfigState, pts: Int) = st.copy(economy = st.economy.copy(points = st.economy.points + pts))

    fun canCheckIn(st: ConfigState) = st.economy.lastCheckIn != Time.todayStr()

    /** 商店一次买 n 个的总价：买 5 个打八折（四舍五入，至少 1 萌点） */
    fun bulkPrice(price: Int, n: Int): Int = if (n >= 5) Math.round(price * n * 0.8f).coerceAtLeast(1) else price * n

    /** 签到 +10；连续第 7 天额外 +30，然后重新计数 */
    fun checkIn(st0: ConfigState): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        if (!canCheckIn(st)) return st to 0
        val yesterday = Time.today().minusDays(1).toString()
        val streak = if (st.economy.lastCheckIn == yesterday) st.economy.checkInStreak + 1 else 1
        val bonus = if (streak % 7 == 0) 30 else 0
        val gain = 10 + bonus
        val e = st.economy.copy(points = st.economy.points + gain, lastCheckIn = Time.todayStr(), checkInStreak = streak)
        return st.copy(economy = e) to gain
    }

    /** 提醒弹出后点"完成"：每次 +2，每天上限 30 */
    fun reminderDone(st0: ConfigState): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        val gain = (30 - st.daily.reminderPts).coerceIn(0, 2)
        if (gain == 0) return st to 0
        return add(st, gain).let { it.copy(daily = it.daily.copy(reminderPts = it.daily.reminderPts + gain)) } to gain
    }

    /** 当天喝水达标 +10；连续 7 天达标送一次免费领养 */
    fun waterGoal(st0: ConfigState): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        if (st.daily.waterGoalAwarded) return st to 0
        val today = Time.todayStr()
        val y = Time.today().minusDays(1).toString()
        val streak = if (st.economy.lastWaterGoalDay == y) st.economy.waterStreak + 1 else 1
        var e = st.economy.copy(points = st.economy.points + 10, waterStreak = streak, lastWaterGoalDay = today)
        if (streak >= 7 && !e.waterAdoptClaimed) e = e.copy(freeAdopts = e.freeAdopts + 1, waterAdoptClaimed = true)
        return st.copy(economy = e, daily = st.daily.copy(waterGoalAwarded = true)) to 10
    }

    /** 按时睡觉 +10；连续 7 天送一次免费领养 */
    fun sleepOnTime(st0: ConfigState): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        if (st.daily.sleepAwarded) return st to 0
        val today = Time.todayStr()
        val y = Time.today().minusDays(1).toString()
        val streak = if (st.economy.lastSleepDay == y) st.economy.sleepStreak + 1 else 1
        var e = st.economy.copy(points = st.economy.points + 10, sleepStreak = streak, lastSleepDay = today)
        if (streak >= 7 && !e.sleepAdoptClaimed) e = e.copy(freeAdopts = e.freeAdopts + 1, sleepAdoptClaimed = true)
        return st.copy(economy = e, daily = st.daily.copy(sleepAwarded = true)) to 10
    }

    /** 记录经期 / 关心 TA：每天 +5 */
    fun record(st0: ConfigState): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        if (st.daily.recordAwarded) return st to 0
        return add(st, 5).let { it.copy(daily = it.daily.copy(recordAwarded = true)) } to 5
    }

    /** 小游戏：每玩满 60 秒 +3，每天上限 15 */
    fun gameSeconds(st0: ConfigState, seconds: Int): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        val total = st.daily.gameSeconds + seconds
        val earnedBefore = st.daily.gameSeconds / 60 * 3
        val earnedNow = (total / 60 * 3).coerceAtMost(15)
        val gain = (earnedNow - earnedBefore).coerceAtLeast(0).coerceAtMost(15 - st.daily.gamePts)
        val s2 = st.copy(daily = st.daily.copy(gameSeconds = total, gamePts = st.daily.gamePts + gain))
        return add(s2, gain) to gain
    }

    /** 宠物陪伴：每满 1 小时 +2，每天上限 12。睡觉时段不计时（由调用方判断） */
    fun companion(st0: ConfigState, minutes: Int): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        val total = st.daily.companionMinutes + minutes
        val hoursBefore = st.daily.companionMinutes / 60
        val hoursNow = total / 60
        val gain = ((hoursNow - hoursBefore) * 2).coerceAtMost(12 - st.daily.companionPts).coerceAtLeast(0)
        val s2 = st.copy(daily = st.daily.copy(companionMinutes = total, companionPts = st.daily.companionPts + gain))
        return add(s2, gain) to gain
    }

    fun spend(st: ConfigState, pts: Int): ConfigState? =
        if (st.economy.points < pts) null else add(st, -pts)
}
