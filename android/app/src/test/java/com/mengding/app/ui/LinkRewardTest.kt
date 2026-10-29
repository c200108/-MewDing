package com.mengding.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 连连看过关萌点：简单 4 / 普通 8 / 困难 15 / 无人生还 25 */
class LinkRewardTest {
    @Test fun rewardsByDifficulty() = assertEquals(listOf(4, 8, 15, 25), LinkDiff.entries.map { it.reward })
}
