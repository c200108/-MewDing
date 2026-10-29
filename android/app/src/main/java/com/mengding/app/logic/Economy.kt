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

    /** 联签第几天额外送多少：7 天 +50、14 天 +100、21 天 +150、30 天 +250；满 30 天后联签天数清零重新记 */
    val checkInBonus = mapOf(7 to 50, 14 to 100, 21 to 150, 30 to 250)
    const val CHECK_IN = 20

    /** 签到 +20；联签到第 7 / 14 / 21 / 30 天另外加送（见 checkInBonus），第 30 天之后从第 1 天重新记 */
    fun checkIn(st0: ConfigState): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        if (!canCheckIn(st)) return st to 0
        val yesterday = Time.today().minusDays(1).toString()
        val prev = st.economy.checkInStreak
        val streak = if (st.economy.lastCheckIn == yesterday && prev < 30) prev + 1 else 1
        val bonus = checkInBonus[streak] ?: 0
        val gain = CHECK_IN + bonus
        // 先补算一次累计签到天数（徽章上线前签过的按联签天数算），再加上今天
        val seeded = Badges.seed(st).economy
        val e = seeded.copy(points = st.economy.points + gain, lastCheckIn = Time.todayStr(), checkInStreak = streak,
            checkInDays = seeded.checkInDays + 1)
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

    /**
     * 小游戏换萌点：每种游戏每天只能领一次（这一局换出来是 0 不算领过）。
     *   萌宠大冒险：这一局吃到的金币 ÷ 2；萌宠吃豆：这一局吃到的豆 ÷ 3；萌宠连连看：过关按难度 简单 4 / 普通 8 / 困难 15 / 无人生还 25
     * 返回实际得到的萌点；今天这个游戏已经领过返回 -1
     */
    fun gameReward(st0: ConfigState, game: String, pts: Int): Pair<ConfigState, Int> {
        val st = freshDaily(st0)
        if (game in st.daily.gamesAwarded) return st to -1
        if (pts <= 0) return st to 0
        return add(st, pts).let { it.copy(daily = it.daily.copy(gamesAwarded = it.daily.gamesAwarded + game)) } to pts
    }

    /** 萌叮大冒险：金币 ÷ 2；萌宠吃豆：豆 ÷ 3（连连看按难度给，见 LinkDiff.reward） */
    fun advPoints(coins: Int) = coins / 2
    fun pacPoints(beans: Int) = beans / 3

    fun gameAwardedToday(st: ConfigState, game: String) = st.daily.date == Time.todayStr() && game in st.daily.gamesAwarded

    /** 旧规则（已不用）：每玩满 60 秒 +3，每天上限 15 */
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
