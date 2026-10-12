package com.mengding.app.logic

import android.content.Context
import com.mengding.app.data.ConfigState
import com.mengding.app.data.MealLog
import com.mengding.app.data.Pet
import com.mengding.app.data.SleepLog
import com.mengding.app.data.Snooze
import com.mengding.app.data.Store
import com.mengding.app.data.WaterLog
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.PetFx
import com.mengding.app.widget.Refresh
import java.util.UUID

/**
 * 用户的各种操作。界面、通知按钮、悬浮宠物、桌面快捷方式都调这里，保证效果一致。
 * 每个函数返回一句给用户看的反馈。
 */
object Actions {
    /** 喂食 / 喂水动画时长：捧着实际喂的东西，一口一口吃完（5 段） */
    const val FEED_ANIM_MS = 4500L

    private fun outingUpdate(st: ConfigState, f: (Pet) -> Pet): ConfigState {
        val id = PetSim.outing(st)?.id ?: return st
        return st.copy(pets = st.pets.map { if (it.id == id) f(it) else it })
    }

    private fun after(ctx: Context) {
        Reminders.reschedule(ctx)
        Refresh.all(ctx)
    }

    fun todayWater(): Int {
        val today = Time.today()
        return Store.priv.water.filter { Time.dateOf(it.t) == today }.sumOf { it.ml }
    }

    /** 每天喝水上限：目标的两倍（超过目标一倍以上不让记，喝太多水对身体不好） */
    fun waterCap(st: ConfigState = Store.cfg): Int = st.settings.waterGoalMl * 2

    /** 今天还能记多少毫升 */
    fun waterRoomLeft(): Int = (waterCap() - todayWater()).coerceAtLeast(0)

    /** 记一杯水。ml 默认是上次在转轮上选的量（100~400ml）；会超过今天上限就不记 */
    fun drink(ctx: Context, ml0: Int = Store.cfg.settings.cupMl, fromReminder: Boolean = false): String {
        val ml = ml0.coerceIn(100, 400)
        val before = todayWater()
        val cap = waterCap()
        if (before >= cap) return "今天已经喝了 ${before}ml，是目标的两倍了，喝太多水对身体不好，明天再喝吧"
        if (before + ml > cap) return "这一杯 ${ml}ml 会超过今天的上限 ${cap}ml（目标的两倍），最多还能记 ${cap - before}ml，选小一点吧"
        val now = Time.now()
        Store.updatePrivate { it.copy(water = (it.water + WaterLog(now, ml)).takeLast(5000)) }
        var gained = 0
        Store.update { st0 ->
            var st = Economy.freshDaily(st0)
            if (fromReminder) Economy.reminderDone(st).let { st = it.first; gained += it.second }
            if (todayWater() >= st.settings.waterGoalMl) Economy.waterGoal(st).let { st = it.first; gained += it.second }
            st
        }
        after(ctx)
        val total = todayWater()
        val goal = Store.cfg.settings.waterGoalMl
        return buildString {
            append("记下了：喝了 ${ml}ml，今天共 ${total}ml")
            if (total >= goal) append("，已达标") else append("，还差 ${goal - total}ml")
            if (gained > 0) append("。萌点 +$gained")
        }
    }

    fun mealLogged(label: String): Boolean =
        Store.priv.meals.any { it.day == Time.todayStr() && it.label == label }

    fun eat(ctx: Context, label: String, note: String = "", fromReminder: Boolean = false): String {
        if (mealLogged(label)) return "$label 已经记过了"
        Store.updatePrivate { it.copy(meals = (it.meals + MealLog(Time.todayStr(), label, Time.now(), note)).takeLast(3000)) }
        var gained = 0
        Store.update { st0 ->
            // 这里记录的是用户本人吃饭，不再改变宠物的饱食或经验。
            var st = st0
            if (fromReminder) Economy.reminderDone(st).let { st = it.first; gained += it.second }
            st
        }
        after(ctx)
        return "$label 记下了" + if (gained > 0) "，萌点 +$gained" else ""
    }

    /** 离现在最近的一餐（还没记的） */
    fun currentMeal(st: ConfigState = Store.cfg): String? {
        val now = Time.dateTimeOf(Time.now()).toLocalTime()
        return st.profile.meals.filterNot { mealLogged(it.label) }
            .minByOrNull { kotlin.math.abs(java.time.Duration.between(Time.parse(it.time), now).toMinutes()) }?.label
    }

    fun tonight(): String {
        // 凌晨 5 点前点"去睡了"，算前一天晚上
        val dt = Time.dateTimeOf(Time.now())
        return (if (dt.hour < 5) dt.toLocalDate().minusDays(1) else dt.toLocalDate()).toString()
    }

    fun goSleep(ctx: Context, fromReminder: Boolean = false): String {
        val now = Time.now()
        val night = tonight()
        Store.updatePrivate { p ->
            val others = p.sleeps.filterNot { it.day == night }
            p.copy(sleeps = (others + SleepLog(night, bedAt = now)).takeLast(1000))
        }
        val st0 = Store.cfg
        val bed = Time.parse(st0.profile.sleep)
        val diff = java.time.Duration.between(bed, Time.dateTimeOf(now).toLocalTime()).toMinutes().let {
            if (it > 720) it - 1440 else if (it < -720) it + 1440 else it
        }
        val onTime = kotlin.math.abs(diff) <= 30
        var gained = 0
        Store.update { s0 ->
            var st = s0
            if (onTime) {
                Economy.sleepOnTime(st).let { st = it.first; gained += it.second }
                // 按时睡觉：宠物当晚体力恢复翻倍，到起床时间为止
                val wake = Time.parse(st.profile.wake)
                var end = Time.at(Time.today(), wake)
                if (end < now) end = Time.at(Time.today().plusDays(1), wake)
                st = st.copy(doubleRecoveryUntil = end)
            }
            if (fromReminder) Economy.reminderDone(st).let { st = it.first; gained += it.second }
            st
        }
        after(ctx)
        return (if (onTime) "按时睡觉，真棒！宠物今晚体力恢复翻倍" else "晚安，记下了") +
            if (gained > 0) "。萌点 +$gained" else ""
    }

    fun wakeUp(ctx: Context, quality: Int): String {
        val now = Time.now()
        val night = Time.today().minusDays(1).toString()
        Store.updatePrivate { p ->
            val old = p.sleeps.firstOrNull { it.day == night } ?: SleepLog(night)
            p.copy(sleeps = p.sleeps.filterNot { it.day == night } + old.copy(wakeAt = now, quality = quality))
        }
        Store.update { Economy.reminderDone(it).first }
        after(ctx)
        return "早上好，记下了"
    }

    fun snooze(ctx: Context, kind: String, minutes: Int, extra: String = "") {
        Store.update { st ->
            st.copy(snoozes = (st.snoozes + Snooze(kind, Time.now() + minutes * 60_000L, extra)).takeLast(20))
        }
        Reminders.reschedule(ctx)
    }

    fun waterOffToday(ctx: Context) {
        Store.update { st -> Economy.freshDaily(st).let { it.copy(daily = it.daily.copy(waterOff = true)) } }
        Reminders.reschedule(ctx)
    }

    fun checkIn(ctx: Context): String {
        var gained = 0
        Store.update { st -> Economy.checkIn(st).also { gained = it.second }.first }
        Refresh.all(ctx)
        return if (gained > 0) "签到成功，萌点 +$gained（连续 ${Store.cfg.economy.checkInStreak} 天）" else "今天已经签过到了"
    }

    /** 摸摸宠物：每天前 10 次加经验 */
    fun petPet(ctx: Context, petId: String? = null): String {
        Store.update { st0 ->
            val st = Economy.freshDaily(st0)
            val id = petId ?: PetSim.outing(st)?.id
            if (st.daily.petExp >= 20) st
            else st.copy(
                pets = st.pets.map { if (it.id == id) it.copy(exp = it.exp + 2) else it },
                daily = st.daily.copy(petExp = st.daily.petExp + 2),
            )
        }
        PetFx.play("pet", 2000)
        Refresh.all(ctx)
        return "它很开心"
    }

    /** 兼容旧入口：宠物喂食应从部屋宠物详情页进入。 */
    fun feedRoom(ctx: Context, petId: String, food: Boolean): String {
        val cost = if (food) 2 else 1
        var ok = false
        Store.update { st ->
            val paid = Economy.spend(st, cost) ?: return@update st
            ok = true
            paid.copy(pets = paid.pets.map {
                if (it.id != petId) it
                else if (food) PetSim.feed(it, satiety = 30f, exp = 5) else PetSim.feed(it, hydration = 25f, exp = 5)
            })
        }
        Refresh.all(ctx)
        return if (!ok) "萌点不够了" else if (food) "喂了一份饭（-2 萌点）" else "喂了一份水（-1 萌点）"
    }

    /** 哄睡：体力不满 80 才肯睡，睡到 30 以上自然醒 */
    fun lull(ctx: Context): String {
        val p = PetSim.outing(Store.cfg) ?: return ""
        if (p.stamina >= 80) return "${p.name} 精神很好，还不想睡"
        Store.update { st -> st.copy(pets = st.pets.map { if (it.id == p.id) it.copy(sleeping = true) else it }) }
        Refresh.all(ctx)
        return "${p.name} 睡着了"
    }

    fun wakePet(ctx: Context, petId: String): String {
        val p = Store.cfg.pets.firstOrNull { it.id == petId } ?: return ""
        if (p.stamina < 30) return "${p.name} 还太累了，叫不醒"
        Store.update { st -> st.copy(pets = st.pets.map { if (it.id == petId) it.copy(sleeping = false) else it }) }
        Refresh.all(ctx)
        return "${p.name} 醒了"
    }

    fun setOuting(ctx: Context, petId: String): String {
        val p = Store.cfg.pets.firstOrNull { it.id == petId } ?: return ""
        if (p.sleeping || p.stamina < 10) return "${p.name} 体力耗尽，沉睡中，不愿意出门"
        Store.update { it.copy(outingId = petId) }
        Refresh.all(ctx)
        return "${p.name} 出门陪你啦"
    }

    /** 把当前出门的宠物召回部屋。 */
    fun recall(ctx: Context, petId: String): String {
        if (Store.cfg.outingId != petId) return "它现在不在出门"
        Store.update { it.copy(outingId = null) }
        Refresh.all(ctx)
        return "${Store.cfg.pets.firstOrNull { it.id == petId }?.name ?: "宠物"} 回到部屋了"
    }

    fun adopt(ctx: Context, species: String, name: String, free: Boolean): String {
        var msg = ""
        Store.update { st ->
            val pet = Pet(UUID.randomUUID().toString(), species, name.ifBlank { Catalog.of(species).defaultName }, adoptedAt = Time.now())
            if (st.pets.isEmpty()) return@update st.copy(pets = listOf(pet), outingId = pet.id).also { msg = "领养成功" }
            val paid = if (free) {
                if (st.economy.freeAdopts <= 0) null else st.copy(economy = st.economy.copy(freeAdopts = st.economy.freeAdopts - 1))
            } else Economy.spend(st, Catalog.price(st.pets.size))
            if (paid == null) { msg = if (free) "没有免费领养次数" else "萌点不够了"; st }
            else { msg = "欢迎 ${pet.name} 住进部屋"; paid.copy(pets = paid.pets + pet) }
        }
        Refresh.all(ctx)
        return msg
    }

    /** 在商店买食物 / 饮料放进库存 */
    fun buyFood(ctx: Context, key: String, n: Int): String {
        val f = com.mengding.app.pet.Foods.of(ctx, key) ?: return ""
        var msg = ""
        Store.update { st ->
            val cost = Economy.bulkPrice(f.price, n)
            val paid = Economy.spend(st, cost) ?: run { msg = "萌点不够了，还差 ${cost - st.economy.points}"; return@update st }
            val have = (paid.economy.pantry[key] ?: 0) + n
            msg = "买了 $n 个${f.name}（-$cost 萌点），库存 $have 个"
            paid.copy(economy = paid.economy.copy(pantry = paid.economy.pantry + (key to have)))
        }
        return msg
    }

    /** 喂一样食物或饮料：先用库存，没有库存就当场花萌点买；喂到最爱的额外 +10 并播放开心动画 */
    fun feedItem(ctx: Context, petId: String, key: String): String {
        val f = com.mengding.app.pet.Foods.of(ctx, key) ?: return ""
        var msg = ""
        var fav = false
        Store.update { st ->
            val pet = st.pets.firstOrNull { it.id == petId } ?: return@update st
            val stock = st.economy.pantry[key] ?: 0
            val paid = if (stock > 0) st.copy(economy = st.economy.copy(pantry = st.economy.pantry + (key to stock - 1)))
                else Economy.spend(st, f.price) ?: run { msg = "萌点不够了"; return@update st }
            fav = pet.species in f.favorite
            val bonus = if (fav) 10f else 0f
            msg = "${pet.name} 吃了${f.name}" + (if (stock > 0) "（库存还剩 ${stock - 1}）" else "（-${f.price} 萌点）") +
                if (fav) "，是它最爱的！" else ""
            paid.copy(pets = paid.pets.map {
                if (it.id != petId) it
                else PetSim.feed(it, satiety = f.satiety + if (f.drink) 0f else bonus, hydration = f.hydration + if (f.drink) bonus else 0f, exp = if (fav) 8 else 5)
                    .let { p -> p.copy(stamina = (p.stamina + f.stamina + if (fav) 2f else 0f).coerceIn(0f, 100f)) }
            })
        }
        if (msg.isNotEmpty() && !msg.startsWith("萌点")) {
            val pet = Store.cfg.pets.firstOrNull { it.id == petId }
            if (pet != null && pet.id == PetSim.outing(Store.cfg)?.id) {
                PetFx.play(if (f.drink) "drink" else "eat", FEED_ANIM_MS, foodKey = key)
            }
        }
        Refresh.all(ctx)
        return msg
    }

    fun buyOutfit(ctx: Context, key: String): String {
        val item = com.mengding.app.pet.Outfits.of(ctx, key) ?: return ""
        var msg = ""
        Store.update { st ->
            if (key in st.economy.ownedOutfits) { msg = "已经有了"; return@update st }
            val paid = Economy.spend(st, item.price) ?: run { msg = "萌点不够了，还差 ${item.price - st.economy.points}"; return@update st }
            msg = "买到了「${item.name}」"
            paid.copy(economy = paid.economy.copy(ownedOutfits = paid.economy.ownedOutfits + key))
        }
        return msg
    }

    /** on = true 穿上（同分类的旧服装自动换下），false 脱下 */
    fun wear(ctx: Context, petId: String, key: String, on: Boolean) {
        Store.update { st ->
            st.copy(pets = st.pets.map {
                if (it.id != petId) it
                else it.copy(outfit = null, wearing = if (on) com.mengding.app.pet.Outfits.put(ctx, it.worn, key) else it.worn - key)
            })
        }
        Refresh.all(ctx)
    }

    fun undress(ctx: Context, petId: String) {
        Store.update { st -> st.copy(pets = st.pets.map { if (it.id == petId) it.copy(outfit = null, wearing = emptyList()) else it }) }
        Refresh.all(ctx)
    }

    /** 改名费用为实际字符数；名字没变化不收费。 */
    fun rename(ctx: Context, petId: String, name: String): String {
        val trimmed = name.trim()
        val count = trimmed.codePointCount(0, trimmed.length)
        val next = trimmed.substring(0, trimmed.offsetByCodePoints(0, minOf(8, count)))
        if (next.isEmpty()) return "名字不能为空"
        val pet = Store.cfg.pets.firstOrNull { it.id == petId } ?: return "找不到这只宠物"
        if (next == pet.name) return "名字没有变化"
        val cost = next.codePointCount(0, next.length)
        if (Store.cfg.economy.points < cost) return "萌点不够，需要 $cost 萌点"
        Store.update { st ->
            st.copy(
                economy = st.economy.copy(points = st.economy.points - cost),
                pets = st.pets.map { if (it.id == petId) it.copy(name = next) else it },
            )
        }
        Refresh.all(ctx)
        return "改名成功（-$cost 萌点）"
    }

    fun togglePeriodDay(ctx: Context, day: String): String {
        var added = false
        Store.updatePrivate { p ->
            added = day !in p.periodDays
            p.copy(periodDays = if (added) p.periodDays + day else p.periodDays - day)
        }
        var gained = 0
        if (added) Store.update { st -> Economy.record(st).also { gained = it.second }.first }
        after(ctx)
        return (if (added) "标记为经期" else "取消标记") + if (gained > 0) "，萌点 +$gained" else ""
    }
}
