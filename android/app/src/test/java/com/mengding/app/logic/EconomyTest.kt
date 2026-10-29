package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import org.junit.Assert.assertEquals
import org.junit.Test

class EconomyTest {
    private fun yesterday() = Time.today().minusDays(1).toString()

    /** 昨天签过、联签 prev 天的状态 */
    private fun streak(prev: Int) = ConfigState().let { it.copy(economy = it.economy.copy(lastCheckIn = yesterday(), checkInStreak = prev)) }

    @Test fun singleCheckInIs20() {
        val (st, gain) = Economy.checkIn(ConfigState())
        assertEquals(20, gain); assertEquals(1, st.economy.checkInStreak); assertEquals(20, st.economy.points)
    }

    @Test fun streakBonuses() {
        assertEquals(20 + 50, Economy.checkIn(streak(6)).second)
        assertEquals(20, Economy.checkIn(streak(7)).second)
        assertEquals(20 + 100, Economy.checkIn(streak(13)).second)
        assertEquals(20 + 150, Economy.checkIn(streak(20)).second)
        assertEquals(20 + 250, Economy.checkIn(streak(29)).second)
    }

    @Test fun streakResetsAfter30() {
        val (st, gain) = Economy.checkIn(streak(30))
        assertEquals(20, gain); assertEquals(1, st.economy.checkInStreak)
    }

    @Test fun brokenStreakStartsOver() {
        val st0 = ConfigState().let { it.copy(economy = it.economy.copy(lastCheckIn = Time.today().minusDays(3).toString(), checkInStreak = 6)) }
        val (st, gain) = Economy.checkIn(st0)
        assertEquals(20, gain); assertEquals(1, st.economy.checkInStreak)
    }

    @Test fun onlyOncePerDay() = assertEquals(0, Economy.checkIn(Economy.checkIn(ConfigState()).first).second)

    @Test fun gameRewardOncePerGamePerDay() {
        val (s1, g1) = Economy.gameReward(ConfigState(), "adv", 37 / 2)
        assertEquals(18, g1)
        assertEquals(-1, Economy.gameReward(s1, "adv", 50).second)          // 同一个游戏今天领过了
        val (s2, g2) = Economy.gameReward(s1, "pac", 100 / 3)               // 别的游戏还能领
        assertEquals(33, g2); assertEquals(18 + 33, s2.economy.points)
    }

    @Test fun zeroRewardDoesNotUseTodaysChance() {
        val (s1, g1) = Economy.gameReward(ConfigState(), "pac", 2 / 3)
        assertEquals(0, g1)
        assertEquals(25, Economy.gameReward(s1, "pac", 25).second)
    }
}
