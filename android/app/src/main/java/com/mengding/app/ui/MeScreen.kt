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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            NavRow(Ic.Chart, "统计", "最近 7 天的喝水和睡觉") { Nav.go("stats") }
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
    val s = cfg.settings
    fun set(f: (Settings) -> Settings) { setS(f); Reminders.reschedule(ctx); Refresh.all(ctx) }
    PageScaffold("提醒设置") {
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
                    Modifier.width(100.dp).clip(RoundedCornerShape(18.dp))
                        .border(2.dp, if (sel) P.mint else P.line, RoundedCornerShape(18.dp))
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
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    val days = (6 downTo 0).map { Time.today().minusDays(it.toLong()) }
    val water = days.map { d -> priv.water.filter { Time.dateOf(it.t) == d }.sumOf { it.ml } }
    val goal = cfg.settings.waterGoalMl
    PageScaffold("统计") {
        SectionTitle("最近 7 天喝水")
        MCard {
            val max = maxOf(goal, water.maxOrNull() ?: 0).toFloat()
            val bar = P.blue; val line = P.pink; val bgc = P.line
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width / 7
                water.forEachIndexed { i, ml ->
                    val h = size.height * ml / max
                    drawRoundRect(bgc, Offset(i * w + w * 0.2f, 0f), Size(w * 0.6f, size.height),
                        androidx.compose.ui.geometry.CornerRadius(12f))
                    drawRoundRect(bar, Offset(i * w + w * 0.2f, size.height - h), Size(w * 0.6f, h),
                        androidx.compose.ui.geometry.CornerRadius(12f))
                }
                val gy = size.height - size.height * goal / max
                drawLine(line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f)
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { d -> Text("${d.monthValue}/${d.dayOfMonth}", Modifier.weight(1f), fontSize = 11.sp, color = P.sub,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Gap(6.dp)
            Text("粉线是每天目标 ${goal}ml；7 天平均 ${water.average().toInt()}ml，达标 ${water.count { it >= goal }} 天",
                fontSize = 12.sp, color = P.sub)
        }
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
        SectionTitle("吃饭")
        MCard {
            val cnt = days.sumOf { d -> priv.meals.count { it.day == d.toString() } }
            Text("最近 7 天记了 $cnt 餐（计划 ${cfg.profile.meals.size * 7} 餐）", fontSize = 14.sp, color = P.ink)
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
        SectionTitle("小游戏音乐音效（CC0 开源素材，致谢）")
        MCard {
            listOf(
                "音效：Juhani Junkala《The Essential Retro Video Game Sound Effects Collection》",
                "萌宠吃豆背景音乐：congusbongus《Cunning plan》",
                "萌叮大冒险背景音乐：LordZintick《The Arplands》",
                "萌宠连连看背景音乐：cosmac《8 Bit Concentration Loop》",
                "以上均来自 OpenGameArt.org",
            ).forEach { Text("· $it", fontSize = 12.sp, color = P.sub, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(1.dp))
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

/** 界面主题选择：6 套（经典 + 5 套插画主题），点一下立即生效 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ThemePage() {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    PageScaffold("界面主题") {
        SectionTitle("首页动态背景")
        Text("「自动」按时间换：清晨小屋、上午樱花河畔、下午雨后小街、傍晚秋日林荫道、夜晚河畔摩天轮、深夜少女房间；" +
            "开了本地天气的话下雨会下雨、下雪换雪夜公园；12 月晚上是圣诞客厅。也可以固定一个喜欢的。", fontSize = 12.sp, color = P.sub)
        Gap(8.dp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("auto" to "自动") + Scenes.ALL.toList()).forEach { (k, label) ->
                Pill(label, cfg.settings.homeScene == k) { setS { it.copy(homeScene = k) } }
            }
        }
        SectionTitle("界面主题")
        Text("主题会换掉主色、底部标签和首页功能按钮的图标、Logo 和启动页。宠物本身还是像素画风。", fontSize = 13.sp, color = P.sub)
        Gap()
        Themes.all.forEach { t ->
            val sel = cfg.settings.uiTheme == t.key
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(22.dp)).background(P.card)
                    .border(if (sel) 2.dp else 1.dp, if (sel) t.primary else P.line, RoundedCornerShape(22.dp))
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
