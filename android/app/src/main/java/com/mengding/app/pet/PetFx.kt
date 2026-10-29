package com.mengding.app.pet

import com.mengding.app.data.ConfigState
import com.mengding.app.data.Pet
import com.mengding.app.data.Store
import com.mengding.app.logic.Period
import com.mengding.app.logic.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 设备和临时状态：电量、充电、走路、刚刚被摸/喝水的短动画 */
object PetFx {
    /** 临时动作；喂食时带上吃的是什么（foodKey），start..until 用来算吃到第几口 */
    data class Transient(val action: String, val until: Long, val foodKey: String? = null, val start: Long = until) {
        /** 0 = 刚开始，1 = 吃完 */
        fun progress(now: Long) = if (until <= start) 1f else ((now - start).toFloat() / (until - start)).coerceIn(0f, 1f)

        /** 现在该显示"吃掉一点点"的第几段 */
        fun biteStage(now: Long) = (progress(now) * Sprites.BITE_STAGES).toInt().coerceAtMost(Sprites.BITE_STAGES - 1)
    }

    private val _transient = MutableStateFlow<Transient?>(null)
    val transient: StateFlow<Transient?> = _transient

    /** 主人在 3D 小屋里把它叫醒（摸它、点地面）：这段时间里就算体力低也算醒着，到点了还累再睡 */
    @Volatile var wokenUntil = 0L

    @Volatile var charging = false
    @Volatile var batteryLow = false
    @Volatile var walkingUntil = 0L

    fun play(action: String, ms: Long, foodKey: String? = null) {
        val now = Time.now()
        _transient.value = Transient(action, now + ms, foodKey, now)
    }

    fun clear() {
        _transient.value = null
    }

    /** 宠物在首页冒一句话（夸奖等），带时间戳：同一句话说两次也能再冒一次 */
    private val _said = MutableStateFlow<Pair<String, Long>?>(null)
    val said: StateFlow<Pair<String, Long>?> = _said
    fun say(text: String) { _said.value = text to Time.now() }

    /**
     * 当前该播哪个动作。优先级（需求 2.6）：
     * 临时动作 > 沉睡 > 经期 > 充电 > 渴 > 饿 > 犯困（含手机低电量）> 走路 > 你的睡觉时段 > 精神好 > 待机
     */
    fun actionFor(pet: Pet?, st: ConfigState = Store.cfg, isOuting: Boolean = true): String {
        val now = Time.now()
        if (isOuting) _transient.value?.let { if (it.until > now) return it.action }
        if (pet == null) return "idle"
        val woken = now < wokenUntil
        if (!woken && (pet.sleeping || pet.stamina < 10)) return "exhausted"
        if (isOuting) {
            if (Period.enabled(st) && Period.forecast(Store.priv).current != null) return "period"
            if (charging) return "charge"
        }
        if (pet.hydration < 30) return "thirsty"
        if (pet.satiety < 30) return "hungry"
        if (pet.stamina < 30 || (isOuting && batteryLow)) return "tired"
        if (isOuting && st.settings.stepWalk && walkingUntil > now) return "walk"
        if (!woken && Time.inWindow(now, st.profile.sleep, st.profile.wake)) return "sleep"
        if (pet.stamina >= 80 && pet.satiety >= 60 && pet.hydration >= 60) return "energetic"
        return "idle"
    }
}
