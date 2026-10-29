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

    /** 今天的小任务计数 +1（摸摸、游戏、便签、喂部屋宠物、自定义提醒） */
    private fun count(st: ConfigState, f: (com.mengding.app.data.Daily) -> com.mengding.app.data.Daily) =
        Economy.freshDaily(st).let { it.copy(daily = f(it.daily)) }

    /** 打卡、领萌点、完成任务：叮一声，首页宠物项圈的铃铛闪一下 */
    private fun ding(ctx: Context) = com.mengding.app.pet.Ding.ring(ctx)

    // ---------------- 每日小任务 / 成就徽章 ----------------

    /** 今天还没抽小任务就抽（首页打开时调） */
    fun ensureTasks() = Store.update { Tasks.ensure(it, Store.priv.customs.isNotEmpty(), Time.dateTimeOf(Time.now()).hour) }

    /** 领一条小任务；3 条都领了顺带发全勤奖（叮叮叮连响一串） */
    fun claimTask(ctx: Context, key: String): String {
        var gain = 0; var all = false
        Store.update { st -> Tasks.claim(st, Store.priv, key).also { gain = it.second; all = it.third }.first }
        if (gain <= 0) return "这条还没完成哦"
        if (all) com.mengding.app.pet.Ding.ring(ctx, times = 3) else ding(ctx)
        Refresh.all(ctx)
        return if (all) "三条都完成啦！萌点 +${gain - Tasks.BONUS}，全勤奖 +${Tasks.BONUS}" else "领到萌点 +$gain"
    }

    /** 检查徽章有没有新解锁，有就发萌点；返回这次解锁的（界面拿去放大徽章动画） */
    fun settleBadges(ctx: Context): List<Badges.Unlock> {
        var got = emptyList<Badges.Unlock>()
        Store.update { st -> Badges.settle(st, Store.priv, Store.notesNow.notes.size).also { got = it.second }.first }
        if (got.isNotEmpty()) Refresh.all(ctx)
        return got
    }

    /** 一局小游戏结束（不管领没领到萌点都算玩了一局）；gained > 0 时徽章的「领到萌点次数」+1 */
    fun gamePlayed(ctx: Context, gained: Int) {
        Store.update { st ->
            count(st) { it.copy(games = it.games + 1) }
                .let { if (gained > 0) it.copy(economy = it.economy.copy(gameClaims = it.economy.gameClaims + 1)) else it }
        }
        if (gained > 0) ding(ctx)
    }

    /** 写或改了一条便签 */
    fun noteTouched() = Store.update { st -> count(st) { it.copy(notes = it.notes + 1) } }

    // ---------------- 自定义提醒 ----------------

    fun saveCustom(ctx: Context, r: com.mengding.app.data.CustomReminder) {
        Store.updatePrivate { p -> p.copy(customs = if (p.customs.any { it.id == r.id }) p.customs.map { if (it.id == r.id) r else it } else p.customs + r) }
        after(ctx)
    }

    fun deleteCustom(ctx: Context, id: String) {
        Store.updatePrivate { p -> p.copy(customs = p.customs.filterNot { it.id == id }, customLog = p.customLog.filterNot { it.id == id }) }
        after(ctx)
    }

    /**
     * 完成一次自定义提醒：和喝水吃饭共用「提醒完成 +2、每天上限 30」，宠物冒夸奖气泡，不加宠物体力饱食。
     * slot = 到点的时刻；首页提前打勾时传 null，固定型销掉最早没完成的那个
     */
    fun customDone(ctx: Context, id: String, slot: String? = null, fromReminder: Boolean = false): String {
        val r = Store.priv.customs.firstOrNull { it.id == id } ?: return ""
        val day = Time.today()
        val mark = Customs.slotToMark(r, Store.priv, day, slot)
        // 固定时刻型：这个时刻已经打过勾（比如通知栏和首页各点了一次）就不重复记
        if (r.mode != "interval" && (mark.isEmpty() || Customs.logsOn(Store.priv, id, day).any { !it.skipped && it.slot == mark }))
            return "${r.name} 这次已经打过勾了"
        Store.updatePrivate { p -> p.copy(customLog = (p.customLog + com.mengding.app.data.CustomLog(id, day.toString(), Time.now(), mark)).takeLast(3000)) }
        var gained = 0
        Store.update { st0 -> Economy.reminderDone(count(st0) { it.copy(customs = it.customs + 1) }).also { gained = it.second }.first }
        ding(ctx)
        val praise = listOf("真棒！${r.name}完成啦", "做得好，奖励一个摸摸", "好习惯又 +1", "你今天也在好好照顾自己呢").random()
        PetFx.say(praise)
        com.mengding.app.pet.PetService.instance?.speak(praise, sound = false)
        after(ctx)
        return "${r.name} 完成" + if (gained > 0) "，萌点 +$gained" else ""
    }

    /** 今天跳过这条自定义提醒：今天剩下的都不响了 */
    fun customSkip(ctx: Context, id: String): String {
        val r = Store.priv.customs.firstOrNull { it.id == id } ?: return ""
        Store.updatePrivate { p -> p.copy(customLog = (p.customLog + com.mengding.app.data.CustomLog(id, Time.todayStr(), Time.now(), skipped = true)).takeLast(3000)) }
        after(ctx)
        return "今天不再提醒「${r.name}」"
    }

    fun todayWater(): Int {
        val today = Time.today()
        return Store.priv.water.filter { Time.dateOf(it.t) == today }.sumOf { it.ml }
    }

    /** 每天喝水上限：目标的两倍（超过目标一倍以上不让记，喝太多水对身体不好） */
    fun waterCap(st: ConfigState = Store.cfg): Int = st.settings.waterGoalMl * 2

    /** 这一杯能不能记：已经到上限、或这一杯会超过上限时返回不让记的原因，能记返回 null */
    fun waterRejection(before: Int, ml: Int, cap: Int): String? = when {
        before >= cap -> "今天已经喝了 ${before}ml，是目标的两倍了，喝太多水对身体不好，明天再喝吧"
        before + ml > cap -> "这一杯 ${ml}ml 会超过今天的上限 ${cap}ml（目标的两倍），最多还能记 ${cap - before}ml，选小一点吧"
        else -> null
    }

    /** 今天还能记多少毫升 */
    fun waterRoomLeft(): Int = (waterCap() - todayWater()).coerceAtLeast(0)

    /** 记一杯水。ml 默认是上次在转轮上选的量（100~400ml）；会超过今天上限就不记 */
    fun drink(ctx: Context, ml0: Int = Store.cfg.settings.cupMl, fromReminder: Boolean = false): String {
        val ml = ml0.coerceIn(100, 400)
        val before = todayWater()
        val cap = waterCap()
        waterRejection(before, ml, cap)?.let { return it }
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
        ding(ctx)
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
        ding(ctx)
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
        ding(ctx)
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
        ding(ctx)
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
        if (gained <= 0) return "今天已经签过到了"
        ding(ctx)
        val streak = Store.cfg.economy.checkInStreak
        val extra = gained - Economy.CHECK_IN
        val next = Economy.checkInBonus.keys.firstOrNull { it > streak }
        return "签到成功，萌点 +$gained" + (if (extra > 0) "（联签 $streak 天奖励 +$extra）" else "（联签 $streak 天）") +
            (next?.let { "，再签 ${it - streak} 天额外送 ${Economy.checkInBonus[it]}" } ?: "，明天起重新记联签")
    }

    /** 摸摸宠物：每天前 10 次加经验 */
    fun petPet(ctx: Context, petId: String? = null): String {
        Store.update { st0 ->
            val st = Economy.freshDaily(st0)
            val id = petId ?: PetSim.outing(st)?.id
            // 小任务的「摸摸 3 次」和爱心徽章的摸摸次数每次都记；经验每天只加前 10 次
            val counted = st.copy(daily = st.daily.copy(pets = st.daily.pets + 1), economy = st.economy.copy(petCount = st.economy.petCount + 1))
            if (counted.daily.petExp >= 20) counted
            else counted.copy(
                pets = counted.pets.map { if (it.id == id) it.copy(exp = it.exp + 2) else it },
                daily = counted.daily.copy(petExp = counted.daily.petExp + 2),
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

    /** 3D 小屋里摸它 / 点地面把它叫醒：不管多累都先醒来陪主人 90 秒（和 Unity 那边的「陪玩时间」一样长），之后还累会自己再睡 */
    fun wakeByTouch(ctx: Context) {
        val p = PetSim.outing(Store.cfg) ?: return
        com.mengding.app.pet.PetFx.wokenUntil = Time.now() + 90_000L
        if (p.sleeping) Store.update { st -> st.copy(pets = st.pets.map { if (it.id == p.id) it.copy(sleeping = false) else it }) }
        Refresh.all(ctx)
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
    fun feedItem(ctx: Context, petId: String, key: String, inventoryOnly: Boolean = false): String {
        val f = com.mengding.app.pet.Foods.of(ctx, key) ?: return ""
        var msg = ""
        var fav = false
        var ok = false
        Store.update { st ->
            val pet = st.pets.firstOrNull { it.id == petId } ?: return@update st
            val stock = st.economy.pantry[key] ?: 0
            if (inventoryOnly && stock <= 0) {
                msg = "库存不足，请先到商店补充"
                return@update st
            }
            val paid = if (stock > 0) st.copy(economy = st.economy.copy(pantry = st.economy.pantry + (key to stock - 1)))
                else Economy.spend(st, f.price) ?: run { msg = "萌点不够了"; return@update st }
            fav = pet.species in f.favorite
            val bonus = if (fav) 10f else 0f
            ok = true
            msg = "${pet.name} 吃了${f.name}" + (if (stock > 0) "（库存还剩 ${stock - 1}）" else "（-${f.price} 萌点）") +
                if (fav) "，是它最爱的！" else ""
            paid.copy(pets = paid.pets.map {
                if (it.id != petId) it
                else PetSim.feed(it, satiety = f.satiety + if (f.drink) 0f else bonus, hydration = f.hydration + if (f.drink) bonus else 0f, exp = if (fav) 8 else 5)
                    .let { p -> p.copy(stamina = (p.stamina + f.stamina + if (fav) 2f else 0f).coerceIn(0f, 100f)) }
            })
        }
        if (ok) Store.update { st -> count(st) { it.copy(feeds = it.feeds + 1) } }
        if (msg.isNotEmpty() && !msg.startsWith("萌点") && !msg.startsWith("库存不足")) {
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
            if (on && (key !in st.economy.ownedOutfits || com.mengding.app.pet.Outfits.of(ctx, key) == null)) return@update st
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
