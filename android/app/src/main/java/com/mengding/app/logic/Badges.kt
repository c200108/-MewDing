package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import com.mengding.app.data.PrivateState

/**
 * 成就徽章（需求 D）：每个徽章 4 级，到门槛自动解锁并发萌点（按难度，每级最多 50）。
 * 数值大多从已有记录现算（喝水、睡觉、三餐、便签、宠物、衣服），所以上线时自然补算；
 * 签到天数、摸摸次数、小游戏领奖次数是计数器（签到天数上线时按联签天数补一次，其余从现在开始记）。
 */
object Badges {
    /** img：徽章图（res/drawable-nodpi），铃铛奖章每级一张 */
    data class Def(val key: String, val name: String, val what: String, val unit: String,
                   val levels: List<Int>, val rewards: List<Int>, val img: List<String>)

    val bell = Def("checkin", "铃铛奖章", "累计签到", "天", listOf(1, 7, 30, 100), listOf(5, 15, 30, 50),
        listOf("badge_day_1", "badge_day_2", "badge_day_3", "badge_day_4"))

    private fun col(i: Int) = List(4) { "badge_collection_$i" }

    val collection = listOf(
        Def("water", "水杯", "喝水达标", "天", listOf(1, 7, 30, 100), listOf(10, 20, 35, 50), col(1)),
        Def("sleep", "月亮太阳", "按时睡觉", "天", listOf(1, 7, 30, 100), listOf(10, 20, 35, 50), col(2)),
        Def("meals", "饭碗", "三餐全打卡", "天", listOf(1, 7, 30, 100), listOf(10, 20, 35, 50), col(3)),
        Def("notes", "便签本", "便签", "条", listOf(1, 10, 50, 200), listOf(5, 15, 30, 50), col(4)),
        Def("pets", "彩色爪印", "拥有宠物", "种", listOf(2, 5, 10, 15), listOf(10, 25, 40, 50), col(5)),
        Def("outfits", "围巾衣架", "拥有衣服", "件", listOf(1, 3, 6, 10), listOf(5, 15, 30, 50), col(6)),
        Def("games", "游戏手柄", "小游戏领到萌点", "次", listOf(1, 10, 50, 200), listOf(5, 15, 30, 50), col(7)),
        Def("love", "爱心", "摸摸宠物", "次", listOf(10, 100, 500, 2000), listOf(5, 15, 30, 50), col(8)),
    )

    val all = listOf(bell) + collection

    data class Unlock(val def: Def, val level: Int, val pts: Int)

    /** 现在的各项数值 */
    fun values(st: ConfigState, p: PrivateState, notes: Int): Map<String, Int> {
        val goal = st.settings.waterGoalMl
        val waterDays = p.water.groupBy { Time.dateOf(it.t) }.count { (_, l) -> l.sumOf { it.ml } >= goal }
        val sleepDays = p.sleeps.map { it.day }.distinct().count { Tasks.sleptOnTime(st, p, it) }
        val labels = st.profile.meals.map { it.label }.toSet()
        val mealDays = if (labels.isEmpty()) 0 else p.meals.groupBy { it.day }.count { (_, l) -> l.map { it.label }.toSet().containsAll(labels) }
        return mapOf(
            "checkin" to st.economy.checkInDays,
            "water" to waterDays, "sleep" to sleepDays, "meals" to mealDays,
            "notes" to notes,
            "pets" to st.pets.map { it.species }.distinct().size,
            "outfits" to st.economy.ownedOutfits.size,
            "games" to st.economy.gameClaims,
            "love" to st.economy.petCount,
        )
    }

    fun level(d: Def, v: Int) = d.levels.count { v >= it }

    /** 还差多少到下一级；满级返回 null */
    fun toNext(d: Def, v: Int): Int? = d.levels.firstOrNull { v < it }?.let { it - v }

    /** 上线补算：累计签到天数以前没记，按现在的联签天数补一次 */
    fun seed(st: ConfigState): ConfigState {
        if (st.economy.badgesSeeded) return st
        val days = if (st.economy.lastCheckIn != null) st.economy.checkInStreak.coerceAtLeast(1) else 0
        return st.copy(economy = st.economy.copy(badgesSeeded = true, checkInDays = maxOf(st.economy.checkInDays, days)))
    }

    /** 把新到的等级都解锁、发萌点。返回新状态和这次解锁的（一个徽章一次升好几级就逐级列出） */
    fun settle(st0: ConfigState, p: PrivateState, notes: Int): Pair<ConfigState, List<Unlock>> {
        val st = seed(st0)
        val v = values(st, p, notes)
        val got = mutableListOf<Unlock>()
        var badges = st.economy.badges
        all.forEach { d ->
            val had = badges[d.key] ?: 0
            val now = level(d, v[d.key] ?: 0)
            for (lv in had + 1..now) got += Unlock(d, lv, d.rewards[lv - 1].coerceAtMost(50))
            if (now > had) badges = badges + (d.key to now)
        }
        if (got.isEmpty()) return st to got
        val pts = got.sumOf { it.pts }
        return st.copy(economy = st.economy.copy(points = st.economy.points + pts, badges = badges)) to got
    }
}
