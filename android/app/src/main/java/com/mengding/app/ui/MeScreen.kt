package com.mengding.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.R
import com.mengding.app.data.Gender
import com.mengding.app.data.Settings
import com.mengding.app.data.SoundMode
import com.mengding.app.data.Store
import com.mengding.app.logic.Backup
import com.mengding.app.logic.IconSwitcher
import com.mengding.app.logic.Perms
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.PetService
import com.mengding.app.widget.Refresh

private fun setS(f: (Settings) -> Settings) = Store.update { it.copy(settings = f(it.settings)) }

@Composable
fun MeScreen() {
    val cfg by Store.config.collectAsState()
    PageScaffold("我的") {
        MCard(Modifier.clickable { Nav.go("profile") }) {
            HRow(gap = 14.dp) {
                Image(painterResource(LocalUiTheme.current.logo), null, Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.weight(1f)) {
                    Text(cfg.profile.nickname.ifBlank { "未设置昵称" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Text("${cfg.profile.gender.label} · 萌点 ${cfg.economy.points} · 签到连续 ${cfg.economy.checkInStreak} 天",
                        fontSize = 13.sp, color = P.sub)
                }
                androidx.compose.material3.Icon(Ic.Edit, null, Modifier.size(18.dp), tint = P.sub)
            }
        }
        SectionTitle("设置")
        MCard(padding = 12.dp) {
            NavRow(Ic.Bell, "提醒设置", if (cfg.profile.gender == Gender.FEMALE) "喝水、吃饭、睡觉、经期、勿扰时段、提示音"
                else "喝水、吃饭、睡觉、勿扰时段、提示音") { Nav.go("reminders") }
            Divider()
            NavRow(Ic.Room, "宠物显示", "通知栏宠物、桌面悬浮宠物、陪我走路") { Nav.go("display") }
            Divider()
            NavRow(Ic.Speech, "宠物说话与叫声", "气泡里的话、多久说一次、点宠物时叫一声") { Nav.go("speech") }
            Divider()
            NavRow(Ic.Paint, "界面主题", "当前：${Themes.of(cfg.settings.uiTheme).name}") { Nav.go("theme") }
            NavRow(Ic.TextFmt, "显示字体", "当前：${AppFonts.of(cfg.settings.fontKey).name}") { Nav.go("font") }
            Divider()
            NavRow(Ic.Image, "换桌面图标", "默认白猫或六只像素宠物") { Nav.go("icon") }
            Divider()
            NavRow(Ic.Chart, "统计", "喝水日历、吃饭记录、最近 7 晚睡觉") { Nav.go("stats") }
            Divider()
            NavRow(Ic.Medal, "成就徽章", "已点亮 ${cfg.economy.badges.count { it.value > 0 }} / ${com.mengding.app.logic.Badges.all.size} 个") { Nav.go("badges") }
            Divider()
            NavRow(Ic.Speech, "重新显示新手提示", "首页、便签、商店等页面的小提示再看一遍") {
                Store.update { it.copy(settings = it.settings.copy(tipsSeen = emptyList())) }
                com.mengding.app.pet.PetService.instance?.applySettings()
                MdToasts.show("好的，进到各个页面时会再提示一次")
            }
        }
        SectionTitle("安全")
        MCard(padding = 12.dp) {
            NavRow(Ic.Lock, "隐私与数据", "隐私锁、通知伪装、备份、云同步") { Nav.go("privacy") }
            Divider()
            NavRow(Ic.Shield, "权限与提醒保障", "提醒不准时、宠物消失看这里") { Nav.go("perms") }
            Divider()
            NavRow(Ic.Heart, "关于萌叮", "版本 ${com.mengding.app.BuildConfig.VERSION_NAME}") { Nav.go("about") }
        }
    }
}

@Composable
fun ProfilePage() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val p = cfg.profile
    var editName by remember { mutableStateOf(false) }
    fun setP(f: (com.mengding.app.data.Profile) -> com.mengding.app.data.Profile) {
        Store.update { it.copy(profile = f(it.profile)) }
        Reminders.reschedule(ctx)
    }
    PageScaffold("个人信息") {
        MCard(padding = 12.dp) {
            ValueRow("昵称", p.nickname.ifBlank { "未设置" }) { editName = true }
            Divider()
            Text("性别", Modifier.padding(top = 12.dp), fontSize = 15.sp, color = P.ink)
            Gap(8.dp)
            HRow { listOf(Gender.FEMALE, Gender.MALE, Gender.PRIVATE).forEach { g -> Pill(g.label, p.gender == g) { setP { it.copy(gender = g) } } } }
            Gap(8.dp)
            Divider()
            BirthdayRow(p.birthday) { b -> setP { it.copy(birthday = b) } }
            Divider()
            StepperRow("体重", p.weightKg ?: 55, 1, 30, 150, "kg") { w -> setP { it.copy(weightKg = w) } }
            Divider()
            TimeRow("起床时间", p.wake) { t -> setP { it.copy(wake = t) } }
            Divider()
            TimeRow("睡觉时间", p.sleep) { t -> setP { it.copy(sleep = t) } }
        }
        SectionTitle("三餐时间")
        MealTimesEditor()
    }
    if (editName) {
        var name by remember { mutableStateOf(p.nickname) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("昵称") },
            text = { OutlinedTextField(name, { name = it.take(12) }, singleLine = true) },
            confirmButton = { TextButton({ setP { it.copy(nickname = name.trim()) }; editName = false }) { Text("确定") } },
            dismissButton = { TextButton({ editName = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsPage() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    val s = cfg.settings
    fun set(f: (Settings) -> Settings) { setS(f); Reminders.reschedule(ctx); Refresh.all(ctx) }
    PageScaffold("提醒设置") {
        MCard(padding = 12.dp) {
            NavRow(Ic.Shield, "提醒体检", "一项项查提醒为什么不响，还能发一条测试提醒") { Nav.go("health") }
            Divider()
            NavRow(Ic.Pill, "自定义提醒", priv.customs.takeIf { it.isNotEmpty() }?.joinToString("、") { it.name } ?: "吃药、久坐、护眼、护肤，或者自己建") { Nav.go("customs") }
        }
        SectionTitle("喝水")
        MCard(padding = 12.dp) {
            ToggleRow("喝水提醒", checked = s.waterOn) { v -> set { it.copy(waterOn = v) } }
            StepperRow("每天目标", s.waterGoalMl, 100, 800, 5000, "ml") { v -> set { it.copy(waterGoalMl = v) } }
            StepperRow("默认一杯", s.cupMl.coerceIn(100, 400), 10, 100, 400, "ml") { v -> set { it.copy(cupMl = v) } }
            StepperRow("间隔", s.waterIntervalMin, 15, 30, 240, "分钟") { v -> set { it.copy(waterIntervalMin = v) } }
            Text("每天最多记到目标的两倍（${s.waterGoalMl * 2}ml），喝太多水对身体不好。",
                fontSize = 12.sp, color = P.sub)
        }
        SectionTitle("吃饭和睡觉")
        MCard(padding = 12.dp) {
            ToggleRow("吃饭提醒", "到你设置的饭点提醒；宠物饿了也一起告诉你", s.mealOn) { v -> set { it.copy(mealOn = v) } }
            Divider()
            ToggleRow("饭点桌面气泡", "到饭点桌面宠物做吃饭的动作、冒气泡提醒，点气泡就记下这一餐（要先打开桌面悬浮宠物）",
                s.mealBubble, enabled = s.mealOn) { v -> set { it.copy(mealBubble = v) } }
            Divider()
            ToggleRow("睡觉提醒", "睡前 30 分钟预提醒 + 到点提醒 + 早上补记", s.sleepOn) { v -> set { it.copy(sleepOn = v) } }
            Divider()
            NavRow(Ic.Me, "修改起床和睡觉时间") { Nav.go("profile") }
        }
        SectionTitle("关心 TA")
        MCard(padding = 12.dp) {
            ToggleRow("关心 TA", "帮伴侣、家人、朋友记经期，到时提醒你多照顾 TA。打开后「便签」里会出现「关心 TA」按钮",
                com.mengding.app.logic.Period.careEnabled(cfg)) { v -> set { it.copy(careOn = v) } }
        }
        SectionTitle("饭点")
        MealTimesEditor()
        if (cfg.profile.gender == Gender.FEMALE) {
            SectionTitle("经期")
            MCard(padding = 12.dp) {
                ToggleRow("我的经期记录和提醒", "女性默认打开；关掉后底部的「经期」页也会收起", s.periodOn ?: true) { v ->
                    set { it.copy(periodOn = v) }
                }
                if (s.periodOn != false) {
                    StepperRow("提前几天提醒", s.periodAdvanceDays, 1, 1, 7, "天") { v -> set { it.copy(periodAdvanceDays = v) } }
                    ToggleRow("经期每日关怀", "经期中每天一条小建议", s.careTips) { v -> set { it.copy(careTips = v) } }
                    ToggleRow("显示排卵期和易孕期", "默认不显示", s.ovulationShown) { v -> set { it.copy(ovulationShown = v) } }
                }
            }
        }
        SectionTitle("提示音和勿扰")
        MCard(padding = 12.dp) {
            Text("提示音", Modifier.padding(top = 6.dp), fontSize = 15.sp, color = P.ink)
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundMode.entries.forEach { m -> Pill(m.label, s.sound == m) { set { it.copy(sound = m) } } }
            }
            Text("系统设置里还可以单独调每种提醒的声音。", Modifier.padding(vertical = 8.dp), fontSize = 12.sp, color = P.sub)
            Divider()
            ToggleRow("勿扰时段", "这段时间不提醒喝水，其他提醒静音", s.quietOn) { v -> set { it.copy(quietOn = v) } }
            if (s.quietOn) {
                TimeRow("开始", s.quietStart) { t -> set { it.copy(quietStart = t) } }
                TimeRow("结束", s.quietEnd) { t -> set { it.copy(quietEnd = t) } }
            }
        }
        Gap(10.dp)
        Text("所有提醒按手机当地时间；出国或改时区后会自动重新安排。", fontSize = 12.sp, color = P.sub)
    }
}

@Composable
fun PetDisplayPage() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val s = cfg.settings
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        setS { it.copy(stepWalk = ok) }
        PetService.sync(ctx); PetService.instance?.applySettings()
    }
    fun apply() { PetService.sync(ctx); PetService.instance?.applySettings() }
    val runPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { apply() }
    PageScaffold("宠物显示") {
        MCard(padding = 12.dp) {
            ToggleRow("通知栏宠物", "下拉通知栏能看到会动的宠物和今日进度", s.persistentPet) { v -> setS { it.copy(persistentPet = v) }; apply() }
            Divider()
            ToggleRow("桌面悬浮宠物", if (Perms.overlay(ctx)) "浮在桌面最上层，能拖动；长按弹出菜单" else "需要先允许「悬浮窗」权限",
                s.floatingPet) { v ->
                if (v && !Perms.overlay(ctx)) runCatching { ctx.startActivity(Perms.overlayIntent(ctx)) }
                setS { it.copy(floatingPet = v) }; apply()
            }
            Divider()
            ToggleRow("陪我走路", "你走路时，宠物也跟着走（需要「身体活动」权限）", s.stepWalk) { v ->
                if (v && !Perms.activity(ctx)) activityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                else { setS { it.copy(stepWalk = v) }; apply() }
            }
        }
        if (s.floatingPet) {
            SectionTitle("桌面宠物跑步")
            MCard(padding = 12.dp) {
                HRow {
                    listOf("off" to "不跑", "cross" to "横穿屏幕", "edges" to "绕四边跑").forEach { (k, v) ->
                        Pill(v, s.petRunMode == k) {
                            setS { it.copy(petRunMode = k) }; apply()
                            if (k != "off" && !Perms.activity(ctx)) runPerm.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    }
                }
                if (s.petRunMode != "off") {
                    Text(if (Perms.activity(ctx)) "你走路的时候它才会跑；你停下来，它也停下歇着。"
                        else "宠物只在你走路时才跑，需要「身体活动」权限来知道你在走路（点上面的跑法会请求权限）。",
                        Modifier.padding(top = 8.dp), fontSize = 12.sp, color = if (Perms.activity(ctx)) P.sub else P.pink)
                }
                Gap(8.dp)
                when (s.petRunMode) {
                    "cross" -> {
                        Text("方向", fontSize = 14.sp, color = P.ink)
                        Gap(6.dp)
                        HRow {
                            listOf("auto" to "来回跑", "left" to "一直向左", "right" to "一直向右").forEach { (k, v) ->
                                Pill(v, s.petRunDir == k) { setS { it.copy(petRunDir = k) } }
                            }
                        }
                        Text("走路时从屏幕一边跑到另一边，到了就停下歇着。选单向时，它会从这边跑出屏幕、再从对面跑进来。",
                            Modifier.padding(top = 8.dp), fontSize = 12.sp, color = P.sub)
                    }
                    "edges" -> {
                        Text("方向", fontSize = 14.sp, color = P.ink)
                        Gap(6.dp)
                        HRow {
                            listOf("cw" to "顺时针", "ccw" to "逆时针").forEach { (k, v) ->
                                Pill(v, s.petLapDir == k) { setS { it.copy(petLapDir = k) } }
                            }
                        }
                        Text("走路时宠物站在屏幕边上，沿着四条边绕圈跑，过拐角时转身，脚始终踩着边；跑完一圈歇一会儿。",
                            Modifier.padding(top = 8.dp), fontSize = 12.sp, color = P.sub)
                    }
                    else -> Text("宠物待在原地不跑。", fontSize = 12.sp, color = P.sub)
                }
            }
            Text("小技巧：长按桌面宠物 →「躲起来」，它会侧身躲到屏幕边上只露出耳朵，点一下耳朵就探出来。",
                Modifier.padding(top = 8.dp), fontSize = 12.sp, color = P.sub)
        }
        Gap(10.dp)
        SectionTitle("桌面小组件")
        MCard { Text("长按手机桌面空白处 → 小组件 → 萌叮宠物。缩到 1×1 就是一个会动的宠物图标。", fontSize = 14.sp, color = P.sub) }
        SectionTitle("画风")
        MCard { Text("当前是像素风。萌系插画版宠物正在制作，做好后可以在这里切换。", fontSize = 14.sp, color = P.sub) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    PageScaffold("换桌面图标") {
        Text("宠物图标是透明底，桌面上只显示宠物本身（个别手机桌面会强制加一块底板，这是系统行为）。" +
            "桌面图标本身没法动；想要会动的，可以放一个 1×1 的萌叮小组件。换图标后，部分手机桌面要过几秒才刷新。",
            fontSize = 13.sp, color = P.sub)
        Gap()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconSwitcher.options.forEach { (key, label) ->
                val sel = cfg.settings.iconAlias == key
                Column(
                    Modifier.width(100.dp).padding(end = 3.dp, bottom = 3.dp).mdTile(sel)
                        .clickable { IconSwitcher.apply(ctx, key); toast("已换成「$label」") }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (key == "default") Image(painterResource(R.drawable.logo_cat), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                    else Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        PetSprite(key, "idle", 64.dp)
                    }
                    Gap(6.dp)
                    Text(label, fontSize = 12.sp, color = P.ink)
                }
            }
        }
    }
}

@Composable
fun StatsPage() {
    val priv by Store.private.collectAsState()
    val cfg by Store.config.collectAsState()
    val today = Time.today()
    val thisMonth = java.time.YearMonth.from(today)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    // 喝水日历和吃饭清单共用一个月份，左右切换；最早能翻到有记录的那个月
    var month by remember { mutableStateOf(thisMonth) }
    val firstDay = listOfNotNull(
        priv.water.minOfOrNull { it.t }?.let { Time.dateOf(it) },
        priv.meals.mapNotNull { runCatching { java.time.LocalDate.parse(it.day) }.getOrNull() }.minOrNull(),
    ).minOrNull()
    val first = firstDay?.let { java.time.YearMonth.from(it) }?.coerceAtMost(thisMonth) ?: thisMonth
    PageScaffold("统计") {
        MonthSwitcher(month, canPrev = month > first, canNext = month < thisMonth) { month = it }
        SectionTitle("喝水日历")
        WaterCalendar(month, cfg.settings.waterGoalMl)
        SectionTitle("吃饭记录")
        MealDays(month)
        SectionTitle("最近 7 晚睡觉")
        MCard(padding = 12.dp) {
            days.forEach { d ->
                val s = priv.sleeps.firstOrNull { it.day == d.toString() }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${d.monthValue}/${d.dayOfMonth}", Modifier.width(56.dp), fontSize = 14.sp, color = P.sub)
                    Text(
                        if (s?.bedAt == null) "没记录" else buildString {
                            append("${Time.hm(s.bedAt)} 睡")
                            s.wakeAt?.let { append(" · ${Time.hm(it)} 起") }
                            if (s.quality > 0) append(" · " + listOf("", "没睡好", "一般", "睡得好")[s.quality])
                        }, fontSize = 14.sp, color = P.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: java.time.YearMonth, canPrev: Boolean, canNext: Boolean, onChange: (java.time.YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹ 上个月", Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = canPrev) { onChange(month.minusMonths(1)) }
            .padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 14.sp, color = if (canPrev) P.mintDeep else P.line)
        Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = P.ink, textAlign = TextAlign.Center)
        Text("下个月 ›", Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = canNext) { onChange(month.plusMonths(1)) }
            .padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 14.sp, color = if (canNext) P.mintDeep else P.line)
    }
}

/** 喝水月历：一格一天，写当天喝了多少毫升；达标的格子满蓝，没达标按喝的比例浅蓝，今天描粉边 */
@Composable
private fun WaterCalendar(month: java.time.YearMonth, goal: Int) {
    val priv by Store.private.collectAsState()
    val today = Time.today()
    val byDay = remember(priv.water, month) {
        priv.water.groupBy { Time.dateOf(it.t) }.filterKeys { java.time.YearMonth.from(it) == month }
            .mapValues { (_, l) -> l.sumOf { it.ml } }
    }
    val lead = month.atDay(1).dayOfWeek.value - 1      // 周一开头
    val cells = lead + month.lengthOfMonth()
    MCard(padding = 10.dp) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), fontSize = 12.sp, color = P.sub, textAlign = TextAlign.Center)
            }
        }
        Gap(4.dp)
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val i = row * 7 + col - lead
                    Box(Modifier.weight(1f).padding(2.dp)) {
                        if (i in 0 until month.lengthOfMonth()) {
                            val d = month.atDay(i + 1)
                            val ml = byDay[d] ?: 0
                            val future = d.isAfter(today)
                            val met = ml >= goal
                            val bg = when {
                                future -> Color.Transparent
                                met -> P.blue
                                ml > 0 -> P.blue.copy(alpha = 0.12f + 0.4f * ml / goal.coerceAtLeast(1))
                                else -> P.line.copy(alpha = 0.45f)
                            }
                            Column(
                                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                                    .then(if (d == today) Modifier.border(2.dp, P.pink, RoundedCornerShape(8.dp)) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("${d.dayOfMonth}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (met) Color.White else if (future) P.sub.copy(alpha = 0.5f) else P.ink)
                                if (!future) Text(if (ml > 0) "$ml" else "-", fontSize = 10.sp, maxLines = 1,
                                    color = if (met) Color.White else P.sub)
                            }
                        }
                    }
                }
            }
        }
        Gap(6.dp)
        val counted = if (month == java.time.YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        Text("格子里是当天喝了多少 ml，满格蓝色 = 达到每天目标 ${goal}ml。这个月达标 ${byDay.count { it.value >= goal }} 天，" +
            "日均 ${byDay.values.sum() / counted.coerceAtLeast(1)}ml", fontSize = 12.sp, color = P.sub)
    }
}

/** 吃饭按天列：每一餐几点打的卡、记了吃什么（有备注才显示）；新的日子在上面 */
@Composable
private fun MealDays(month: java.time.YearMonth) {
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    val today = Time.today()
    val last = if (month == java.time.YearMonth.from(today)) today else month.atEndOfMonth()
    val dayList = (last.dayOfMonth downTo 1).map { month.atDay(it) }
    val byDay = remember(priv.meals) { priv.meals.groupBy { it.day } }
    val week = "一二三四五六日"
    MCard(padding = 12.dp) {
        val total = dayList.sumOf { byDay[it.toString()]?.size ?: 0 }
        Text("这个月记了 $total 餐（按现在的饭点算，计划 ${cfg.profile.meals.size * dayList.size} 餐）", fontSize = 13.sp, color = P.sub)
        dayList.forEach { d ->
            Gap(8.dp)
            Divider()
            Gap(8.dp)
            val logs = (byDay[d.toString()] ?: emptyList()).sortedBy { it.t }
            Text("${d.monthValue}月${d.dayOfMonth}日 周${week[d.dayOfWeek.value - 1]}" + if (d == today) " · 今天" else "",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            if (logs.isEmpty()) Text("这天没有吃饭记录", Modifier.padding(start = 8.dp, top = 3.dp), fontSize = 13.sp, color = P.sub)
            logs.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)) {
                    Text(m.label, Modifier.width(52.dp), fontSize = 13.sp, color = P.gold, fontWeight = FontWeight.SemiBold)
                    Text("${Time.hm(m.t)} 打卡", Modifier.width(78.dp), fontSize = 13.sp, color = P.sub)
                    if (m.note.isNotBlank()) Text(m.note, Modifier.weight(1f), fontSize = 13.sp, color = P.ink)
                }
            }
        }
    }
}

@Composable
fun PrivacyPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val s = cfg.settings
    var pwFor by remember { mutableStateOf<String?>(null) }     // "export" / "import"
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingExportUri = uri; pwFor = "export" }
    }
    val weatherLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) setS { it.copy(localWeather = true) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (pendingImport != null) pwFor = "import" else toast("读不了这个文件")
        }
    }
    PageScaffold("隐私与数据") {
        MCard(padding = 12.dp) {
            ToggleRow("隐私锁", "打开萌叮要验证指纹、面容或锁屏密码", s.privacyLock) { v -> setS { it.copy(privacyLock = v) } }
            Divider()
            ToggleRow("通知伪装", "经期和关心 TA 的通知显示成「该照顾自己啦」，锁屏不出现经期字样", s.disguise) { v -> setS { it.copy(disguise = v) } }
            Divider()
            ToggleRow("本地天气", "首页显示你所在地的天气。需要「大致位置」权限，坐标约 1 公里精度、只发给公开天气服务 Open-Meteo，不保存",
                s.localWeather && Weather.permitted(ctx)) { v ->
                if (!v) { setS { it.copy(localWeather = false) }; Weather.clear() }
                else if (Weather.permitted(ctx)) setS { it.copy(localWeather = true) }
                else weatherLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        SectionTitle("数据存在哪里")
        MCard {
            Text("经期、喝水、吃饭、睡觉记录默认只存在这台手机上，并用手机系统的密钥加密；设置和宠物存在本机。",
                fontSize = 14.sp, color = P.ink)
        }
        SectionTitle("本地备份（换手机用）")
        MCard {
            Text("导出一个用你自己设的密码加密的备份文件，换手机后导入。忘了密码就打不开。", fontSize = 13.sp, color = P.sub)
            Gap(10.dp)
            HRow {
                GhostButton("导出备份", Modifier.weight(1f), icon = Ic.Box) { exportLauncher.launch("萌叮备份-${Time.todayStr()}.mdbk") }
                GhostButton("导入备份", Modifier.weight(1f), icon = Ic.Box) { importLauncher.launch(arrayOf("*/*")) }
            }
        }
        SectionTitle("云同步（服务器上线后开放）")
        MCard(padding = 12.dp) {
            Text("开启前会列清楚上传哪些内容；随时可以关，关的时候可以顺带删掉云端数据。敏感数据在手机上先加密再上传，服务器看不到内容。",
                fontSize = 13.sp, color = P.sub)
            ToggleRow("开启云同步", "暂未开放", false, enabled = false) {}
            ToggleRow("设置、宠物和萌点", null, s.cloud.config, enabled = false) {}
            ToggleRow("喝水记录", null, s.cloud.water, enabled = false) {}
            ToggleRow("睡觉记录", null, s.cloud.sleep, enabled = false) {}
            ToggleRow("吃饭记录", null, s.cloud.meals, enabled = false) {}
            if (cfg.profile.gender == Gender.FEMALE) ToggleRow("经期记录", null, s.cloud.period, enabled = false) {}
        }
    }
    pwFor?.let { mode ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pwFor = null },
            title = { Text(if (mode == "export") "给备份设个密码" else "输入备份密码") },
            text = {
                OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("密码（至少 4 位）") },
                    visualTransformation = PasswordVisualTransformation())
            },
            confirmButton = {
                TextButton({
                    if (pw.length < 4) { toast("密码至少 4 位"); return@TextButton }
                    if (mode == "export") {
                        val uri = pendingExportUri
                        val ok = uri != null && runCatching {
                            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(pw)) }
                        }.isSuccess
                        toast(if (ok) "备份已导出，请记住密码" else "导出失败")
                    } else {
                        val err = pendingImport?.let { Backup.import(it, pw) } ?: "读不了这个文件"
                        if (err == null) {
                            toast("导入成功")
                            Reminders.reschedule(ctx); PetService.sync(ctx); Refresh.all(ctx)
                        } else toast(err)
                    }
                    pwFor = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ pwFor = null }) { Text("取消") } },
        )
    }
}

@Composable
fun PermissionsPage() {
    PageScaffold("权限与提醒保障") {
        Text("提醒不准时、宠物突然消失，大多是被手机省电功能关掉了。把下面的都打开就好。", fontSize = 13.sp, color = P.sub)
        Gap()
        PermissionList(includeOptional = true)

    }
}

@Composable
fun AboutPage() {
    PageScaffold("关于萌叮") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painterResource(LocalUiTheme.current.logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)))
        }
        Gap()
        Text("萌叮 ${com.mengding.app.BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Gap()
        MCard {
            Text("一只小宠物陪你按时喝水、吃饭、睡觉，照顾好自己。", fontSize = 14.sp, color = P.ink)
            Gap(8.dp)
            Text("这是给亲友试用的早期版本。经期预测仅供参考，不构成医疗建议。", fontSize = 13.sp, color = P.sub)
        }
        // 卡片之间留够空：奶油风有柔和投影、漫画 / 像素风有硬投影，挨着放会叠在一起
        Gap(14.dp)
        UpdateCard()
        Gap(14.dp)
        MCard {
            NavRow(Ic.Note, "用户协议", "原创素材使用须经作者许可") { Nav.go("terms") }
            NavRow(Ic.Shield, "开源许可", "用到的开源项目和素材") { Nav.go("licenses") }
            NavRow(Ic.Speech, "联系作者", "问题反馈、合作、素材使用许可") { Nav.go("contact") }
        }
        Spacer(Modifier.height(1.dp))
    }
}

/** 气泡样式预览：用场景里同一套画法画一个「你好呀」，尾巴指向下方中间 */
@Composable
private fun BubblePreview(style: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bubble = remember { SceneBubble() }
    val bounds = remember { android.graphics.RectF() }
    androidx.compose.foundation.Canvas(modifier) {
        drawContext.canvas.nativeCanvas.let { nc ->
            bounds.set(2f, 2f, size.width - 2f, size.height - 2f)
            bubble.draw(nc, "你好呀", 0.0, size.width / 2, size.height - 2f, 12f * density, 12f * density, false,
                bounds, density, density * .85f, BubbleSkins.of(ctx, style), pop = false)
        }
    }
}

/** 宠物说话与叫声（需求：宠物文字气泡、点击叫声） */
@Composable
fun SpeechPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val s = cfg.settings
    var editing by remember { mutableStateOf<Int?>(null) }   // -1 = 新增
    PageScaffold("宠物说话与叫声") {
        MCard(padding = 12.dp) {
            ToggleRow("点宠物时叫一声", "点击或按住宠物，发出它独特的叫声", s.petSoundOn) { v -> setS { it.copy(petSoundOn = v) } }
            Divider()
            ToggleRow("桌面宠物说话", "隔一段时间在宠物旁边冒一个文字气泡", s.speechOn) { v -> setS { it.copy(speechOn = v) } }
            if (s.speechOn) {
                Text("多久说一次", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = P.sub)
                Gap(6.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(15, 30, 60, 120).forEach { m ->
                        Pill(if (m < 60) "${m}分钟" else "${m / 60}小时", s.speechIntervalMin == m,
                            modifier = Modifier.weight(1f)) { setS { it.copy(speechIntervalMin = m) } }
                    }
                }
                Gap(8.dp)
            }
        }
        Gap(8.dp)
        Text("需要打开「宠物显示 → 桌面悬浮宠物」才能在桌面上看到气泡；你设定的睡觉时段里不说话。", fontSize = 12.sp, color = P.sub)
        SectionTitle("气泡样式（首页 2D 场景）")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BubbleSkins.styles.forEach { (k, label) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .border(2.dp, if (s.bubbleStyle == k) P.mint else P.line, RoundedCornerShape(14.dp))
                    .background(Color(0xFFDDEFD9)).clickable { setS { it.copy(bubbleStyle = k) } }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    BubblePreview(k, Modifier.fillMaxWidth().height(54.dp))
                    Text(label, fontSize = 11.sp, color = if (s.bubbleStyle == k) P.mintDeep else P.ink,
                        fontWeight = if (s.bubbleStyle == k) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        SectionTitle("气泡里的话（${s.phrases.size} 句，随机说）")
        MCard(padding = 12.dp) {
            s.phrases.forEachIndexed { i, ph ->
                Row(Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ph, Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
                    androidx.compose.material3.Icon(Ic.Edit, null, Modifier.size(16.dp), tint = P.sub)
                }
                if (i < s.phrases.lastIndex) Divider()
            }
        }
        Gap(10.dp)
        HRow {
            GhostButton("加一句", icon = Ic.Plus) { editing = -1 }
            GhostButton("恢复默认") { setS { it.copy(phrases = com.mengding.app.data.DEFAULT_PHRASES) }; toast("已恢复默认的 9 句") }
        }
        Gap(10.dp)
        PrimaryButton("让它现在说一句", icon = Ic.Speech) {
            val ok = PetService.instance?.speak(com.mengding.app.pet.Speech.phrase(), sound = false) ?: false
            if (!ok) toast("桌面悬浮宠物没开，看不到气泡")
            else toast("看看桌面上的宠物")
        }
    }
    editing?.let { idx ->
        var text by remember(idx) { mutableStateOf(if (idx >= 0) s.phrases[idx] else "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (idx >= 0) "改这句话" else "加一句话") },
            text = { OutlinedTextField(text, { text = it.take(30) }, singleLine = true, label = { Text("最多 30 个字") }) },
            confirmButton = {
                TextButton({
                    val t = text.trim()
                    if (t.isNotEmpty()) setS { st ->
                        st.copy(phrases = if (idx >= 0) st.phrases.toMutableList().also { it[idx] = t } else st.phrases + t)
                    }
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                if (idx >= 0) TextButton({
                    if (s.phrases.size > 1) setS { st -> st.copy(phrases = st.phrases.filterIndexed { i, _ -> i != idx }) }
                    else toast("至少留一句")
                    editing = null
                }) { Text("删除", color = P.danger) }
                else TextButton({ editing = null }) { Text("取消") }
            },
        )
    }
}

/** 3D 小屋的美术风格。Unity 运行时一个进程只能起一次，已经进过首页的话要重启 App 才换得过来。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SceneStylePicker(current: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var askRestart by remember { mutableStateOf(false) }
    SectionTitle("3D 小屋风格")
    Text("「微体素」是一格一格的小方块；「HD-2D」是立体低模 + 像素贴图，带窗口光束和远景小镇。" +
        "首页只显示卧室，点「更多 ›」「宠物部屋」或卧室的门会展开客厅和院子。选好后重启应用生效。", fontSize = 12.sp, color = P.sub)
    Gap(8.dp)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("voxel" to "微体素", "hd2d" to "HD-2D").forEach { (k, label) ->
            Pill(label, current == k) {
                if (current == k) return@Pill
                setS { it.copy(sceneStyle = k) }
                if (com.mengding.app.unity.UnityHost.started && k != com.mengding.app.unity.UnityHost.launchStyle) askRestart = true
            }
        }
    }
    if (askRestart) {
        AlertDialog(
            onDismissRequest = { askRestart = false },
            title = { Text("重启后生效") },
            text = { Text("3D 小屋要重新启动应用才能换风格。现在重启吗？你的数据都已保存。") },
            confirmButton = { TextButton({ askRestart = false; com.mengding.app.unity.UnityHost.restartApp(ctx) }) { Text("立即重启") } },
            dismissButton = { TextButton({ askRestart = false }) { Text("稍后") } },
        )
    }
}

/** 界面主题选择：6 套（经典 + 5 套插画主题），点一下立即生效 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ThemePage() {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    PageScaffold("界面主题") {
        SectionTitle("首页动态背景")
        Text("「3D 小屋」是新的立体像素卧室，宠物会在里面走动、吃喝、换装（测试中，只支持 64 位手机）。" +
            "「自动」按时间换 2D 场景：清晨小屋、上午樱花河畔、下午雨后小街、傍晚秋日林荫道、夜晚河畔摩天轮、深夜少女房间；" +
            "开了本地天气的话下雨会下雨、下雪换雪夜公园；12 月晚上是圣诞客厅。也可以固定一个喜欢的。", fontSize = 12.sp, color = P.sub)
        Gap(8.dp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val on3d = com.mengding.app.unity.UnityHost.supported && cfg.settings.home3d
            if (com.mengding.app.unity.UnityHost.supported) Pill("3D 小屋", on3d) { setS { it.copy(home3d = true) } }
            (listOf("auto" to "自动") + Scenes.ALL.toList()).forEach { (k, label) ->
                Pill(label, !on3d && cfg.settings.homeScene == k) { setS { it.copy(homeScene = k, home3d = false) } }
            }
        }
        if (com.mengding.app.unity.UnityHost.supported) SceneStylePicker(cfg.settings.sceneStyle)
        StylePicker(cfg.settings.uiStyle)
        SectionTitle("界面主题")
        Text("主题会换掉主色、底部标签和首页功能按钮的图标、Logo 和启动页。宠物本身还是像素画风。", fontSize = 13.sp, color = P.sub)
        Gap()
        Themes.all.forEach { t ->
            val sel = cfg.settings.uiTheme == t.key
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp, end = 3.dp).mdTile(sel, t.primary, radius = 22.dp)
                    .clickable { setS { it.copy(uiTheme = t.key) }; toast("已换成「${t.name}」") }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(t.logo), null, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = P.ink)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(t.primary))
                            if (sel) { Spacer(Modifier.width(8.dp)); Text("使用中", fontSize = 12.sp, color = t.primaryDeep) }
                        }
                        Text(t.desc, fontSize = 12.sp, color = P.sub)
                    }
                }
                Gap(10.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf(t.today to Ic.Home, t.pet to Ic.Room, t.notes to Ic.Note, t.games to Ic.Game).forEach { (art, ic) ->
                        if (art != null) Image(painterResource(art), null, Modifier.size(40.dp))
                        else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.primarySoft), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(ic, null, Modifier.size(22.dp), tint = t.primaryDeep)
                        }
                    }
                }
            }
        }
    }
}

/** 饭点编辑：改时间、点笔改名字、加一餐 / 去掉最后一餐。个人资料页和提醒设置页共用 */
@Composable
fun MealTimesEditor() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val meals = cfg.profile.meals
    fun setMeals(f: (List<com.mengding.app.data.Meal>) -> List<com.mengding.app.data.Meal>) {
        Store.update { it.copy(profile = it.profile.copy(meals = f(it.profile.meals))) }
        Reminders.reschedule(ctx)
    }
    var renaming by remember { mutableStateOf(-1) }
    MCard(padding = 12.dp) {
        meals.forEachIndexed { i, m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TimeRow(m.label, m.time) { t -> setMeals { l -> l.toMutableList().also { it[i] = m.copy(time = t) } } }
                }
                RoundIcon(Ic.Edit, 30.dp) { renaming = i }
            }
            if (i < meals.lastIndex) Divider()
        }
    }
    Gap(8.dp)
    HRow {
        if (meals.size < 5) GhostButton("加一餐", icon = Ic.Plus) {
            setMeals { it + com.mengding.app.data.Meal(if (it.size == 3) "下午茶" else "夜宵", if (it.size == 3) "15:30" else "21:30") }
        }
        if (meals.size > 1) GhostButton("去掉最后一餐", icon = Ic.Minus) { setMeals { it.dropLast(1) } }
    }
    if (renaming in meals.indices) {
        var name by remember(renaming) { mutableStateOf(meals[renaming].label) }
        val taken = meals.withIndex().any { (j, m) -> j != renaming && m.label == name.trim() }
        AlertDialog(
            onDismissRequest = { renaming = -1 },
            title = { Text("这一餐叫什么") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(6) }, singleLine = true)
                    if (taken) Text("已经有一餐叫这个名字了", fontSize = 12.sp, color = P.pink)
                }
            },
            confirmButton = {
                TextButton({
                    val i = renaming
                    setMeals { l -> l.toMutableList().also { it[i] = it[i].copy(label = name.trim()) } }
                    renaming = -1
                }, enabled = name.isNotBlank() && !taken) { Text("确定") }
            },
            dismissButton = { TextButton({ renaming = -1 }) { Text("取消") } },
        )
    }
}

/** 界面风格：弹窗、提示、按钮、开关、输入框、日期时间选择的 4 套画风，每套画一个小弹窗当预览，点「试一下」弹出真的 */
@Composable
private fun StylePicker(current: String) {
    val toast = toaster()
    var demo by remember { mutableStateOf<String?>(null) }
    SectionTitle("界面风格（弹窗与控件）")
    Text("所有弹窗、提示条、按钮、开关、输入框和日期时间选择都会换成这套画风，主色跟着下面的界面主题。", fontSize = 13.sp, color = P.sub)
    Gap()
    MdStyles.all.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { st ->
                val sel = st.key == current
                androidx.compose.runtime.CompositionLocalProvider(LocalMdStyle provides st) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .border(if (sel) 2.dp else 1.dp, if (sel) P.mint else P.line, RoundedCornerShape(18.dp))
                            .background(P.cream).clickable { setS { it.copy(uiStyle = st.key) }; toast("已换成「${st.name}」") }
                            .padding(12.dp),
                    ) {
                        // 迷你弹窗
                        Column(Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp).mdPanel(strong = true).padding(10.dp)) {
                            Text("领取奖励？", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = P.ink)
                            Gap(6.dp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MdSwitch(true, {})
                                Spacer(Modifier.weight(1f))
                                MdButton({ demo = st.key }, Modifier.height(30.dp), role = MdRole.SOLID,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                                    Text("试一下", fontSize = 12.sp)
                                }
                            }
                        }
                        Gap(8.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(st.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
                            if (sel) { Spacer(Modifier.width(6.dp)); Text("使用中", fontSize = 11.sp, color = P.mintDeep) }
                        }
                        Text(st.desc, fontSize = 11.sp, color = P.sub, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
    demo?.let { key ->
        androidx.compose.runtime.CompositionLocalProvider(LocalMdStyle provides MdStyles.of(key)) {
            var on by remember { mutableStateOf(true) }
            var name by remember { mutableStateOf("") }
            var pickDate by remember { mutableStateOf(false) }
            MdDialog(
                onDismissRequest = { demo = null },
                title = { Text("领取奖励？") },
                text = {
                    Column {
                        Text("今天的喝水任务完成啦，可以领 20 萌点。")
                        Gap(10.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("同时提醒明天", Modifier.weight(1f)); MdSwitch(on, { on = it })
                        }
                        Gap(8.dp)
                        OutlinedTextField(name, { name = it.take(12) }, Modifier.fillMaxWidth(), label = { Text("给奖励起个名字") },
                            placeholder = { Text("比如：小鱼干") }, singleLine = true)
                        Gap(8.dp)
                        TextButton({ pickDate = true }) { Text("看看日期选择") }
                    }
                },
                confirmButton = { TextButton({ demo = null; MdToasts.show("领取成功，萌点 +20") }) { Text("领取") } },
                dismissButton = { TextButton({ demo = null }) { Text("取消") } },
            )
            if (pickDate) DateDialog({ pickDate = false }) { pickDate = false; MdToasts.show("选了 $it") }
        }
    }
}
