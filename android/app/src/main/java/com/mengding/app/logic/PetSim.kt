package com.mengding.app.logic

import com.mengding.app.data.ConfigState
import com.mengding.app.data.Pet

/**
 * 体力、饱食、水分（需求 2.6）。不在后台一直算：每次打开 App、服务每分钟心跳时，
 * 按"距离上次过了多久"一次性补算（需求 V6）。
 */
object PetSim {
    private const val STEP_H = 0.25

    fun simulate(st0: ConfigState, now: Long): ConfigState {
        var st = st0
        if (st.lastSimAt == 0L) return st.copy(lastSimAt = now)
        var hours = ((now - st.lastSimAt) / 3_600_000.0).coerceAtMost(72.0)
        if (hours <= 0.0) return st.copy(lastSimAt = now)
        var t = st.lastSimAt
        val sleepStart = st.profile.sleep
        val sleepEnd = st.profile.wake
        var companionMin = 0.0
        while (hours > 1e-6) {
            val h = minOf(STEP_H, hours)
            t += (h * 3_600_000).toLong()
            hours -= h
            val userAsleep = Time.inWindow(t, sleepStart, sleepEnd)
            val doubled = t < st.doubleRecoveryUntil
            st = st.copy(pets = st.pets.map { step(it, h, it.id == st.outingId, userAsleep, doubled) })
            if (st.outingId != null && !userAsleep) companionMin += h * 60
        }
        st = st.copy(lastSimAt = now)
        if (companionMin >= 1) {
            st = Economy.companion(st, companionMin.toInt()).first
            // 陪伴经验：每 6 分钟 +1，即每小时 +10
            val exp = (companionMin / 6).toInt()
            if (exp > 0) st = st.copy(pets = st.pets.map { if (it.id == st.outingId) it.copy(exp = it.exp + exp) else it })
        }
        return st
    }

    private fun step(p: Pet, h: Double, outing: Boolean, userAsleep: Boolean, doubled: Boolean): Pet {
        var stamina = p.stamina.toDouble()
        var satiety = p.satiety.toDouble()
        var hydration = p.hydration.toDouble()
        var sleeping = p.sleeping
        val half = if (userAsleep) 0.5 else 1.0
        if (sleeping) {
            stamina += 12 * h * (if (doubled) 2 else 1)
            satiety -= 1.5 * h
            hydration -= 2 * h
            if (stamina >= 30) sleeping = false
        } else {
            val mult = when {
                stamina >= 70 -> 0.7
                stamina >= 30 -> 1.0
                stamina >= 10 -> 1.4
                else -> 1.8
            }
            // 出门的宠物在你睡觉的时段也跟着休息
            var ds = if (outing && !userAsleep) -6.0 else 4.0 * (if (doubled) 2 else 1)
            satiety -= 3 * mult * half * h
            hydration -= 4 * mult * half * h
            if (satiety < 20 || hydration < 20) ds = minOf(ds, 0.0) - 2
            stamina += ds * h
            if (stamina < 10) sleeping = true
        }
        return p.copy(
            stamina = stamina.coerceIn(0.0, 100.0).toFloat(),
            satiety = satiety.coerceIn(0.0, 100.0).toFloat(),
            hydration = hydration.coerceIn(0.0, 100.0).toFloat(),
            sleeping = sleeping,
        )
    }

    /** 当前出门的宠物；召回后为空，部屋里的宠物不会自动重新出门。 */
    fun outing(st: ConfigState): Pet? = st.pets.firstOrNull { it.id == st.outingId }

    fun feed(p: Pet, satiety: Float = 0f, hydration: Float = 0f, exp: Int = 0) = p.copy(
        satiety = (p.satiety + satiety).coerceIn(0f, 100f),
        hydration = (p.hydration + hydration).coerceIn(0f, 100f),
        exp = p.exp + exp,
    )

    /** 宠物状态的中文描述 */
    fun describe(p: Pet): String = when {
        p.sleeping -> "体力耗尽，正在沉睡"
        p.hydration < 30 -> "口渴了"
        p.satiety < 30 -> "肚子饿了"
        p.stamina < 30 -> "有点犯困"
        p.stamina >= 80 && p.satiety >= 60 && p.hydration >= 60 -> "精神很好"
        else -> "状态不错"
    }
}
