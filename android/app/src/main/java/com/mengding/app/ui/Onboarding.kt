package com.mengding.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mengding.app.R
import com.mengding.app.data.Gender
import com.mengding.app.data.Meal
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Catalog
import com.mengding.app.logic.Perms
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.PetService
import java.time.Instant
import java.time.ZoneOffset

/** 首次使用的引导（需求 L1/L5/L6）。每一步都可以跳过，以后在"我的"里补。 */
@Composable
fun Onboarding() {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var nickname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.UNSET) }
    var birthday by remember { mutableStateOf<String?>(null) }
    var weight by remember { mutableStateOf<Int?>(null) }
    var wake by remember { mutableStateOf("07:30") }
    var sleep by remember { mutableStateOf("23:00") }
    var meals by remember { mutableStateOf(listOf(Meal("早餐", "08:00"), Meal("午餐", "12:00"), Meal("晚餐", "18:30"))) }
    var species by remember { mutableStateOf("cat") }
    var petName by remember { mutableStateOf("") }
    val total = 7

    fun finish() {
        val goal = weight?.let { (it * 33 / 50 * 50).coerceIn(1200, 3500) } ?: 2000
        Store.update { st ->
            st.copy(
                profile = st.profile.copy(
                    nickname = nickname.trim(), gender = gender, birthday = birthday, weightKg = weight,
                    wake = wake, sleep = sleep, meals = meals, onboarded = true,
                ),
                settings = st.settings.copy(waterGoalMl = goal),
            )
        }
        if (Store.cfg.pets.isEmpty()) Actions.adopt(ctx, species, petName, free = false)
        PetService.sync(ctx)
        Reminders.reschedule(ctx)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        // 进度点
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(total) { i ->
                Box(Modifier.padding(3.dp).size(if (i == step) 18.dp else 7.dp, 7.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (i <= step) P.mint else P.line))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 20.dp)) {
            when (step) {
                0 -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Gap(30.dp)
                    Image(painterResource(LocalUiTheme.current.logo), null, Modifier.size(168.dp).clip(RoundedCornerShape(42.dp)))
                    Gap(24.dp)
                    Text("欢迎来到萌叮", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Gap(10.dp)
                    Text("让一只小宠物陪着你，按时喝水、吃饭、睡觉，好好照顾自己。", color = P.sub, fontSize = 15.sp)
                    Gap(10.dp)
                    Text("所有记录默认只保存在这台手机上。", color = P.sub, fontSize = 13.sp)
                }
                1 -> {
                    StepTitle("怎么称呼你？", "昵称和性别。性别决定是否显示经期功能，以后可以改")
                    OutlinedTextField(nickname, { nickname = it.take(12) }, Modifier.fillMaxWidth(), label = { Text("昵称") }, singleLine = true)
                    Gap(20.dp)
                    Text("性别", color = P.sub, fontSize = 14.sp)
                    Gap(8.dp)
                    HRow {
                        listOf(Gender.FEMALE, Gender.MALE, Gender.PRIVATE).forEach { g ->
                            Pill(g.label, gender == g) { gender = g }
                        }
                    }
                }
                2 -> {
                    StepTitle("一点基本信息", "都可以不填。体重用来自动算每天该喝多少水（约 33ml/kg）")
                    BirthdayRow(birthday) { birthday = it }
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("体重", Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
                        if (weight == null) GhostButton("填写") { weight = 55 }
                        else {
                            RoundIcon(Ic.Minus) { weight = (weight!! - 1).coerceAtLeast(30) }
                            Text("${weight}kg", Modifier.width(70.dp), color = P.mintDeep, fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            RoundIcon(Ic.Plus) { weight = (weight!! + 1).coerceAtMost(150) }
                        }
                    }
                    weight?.let {
                        Text("建议每天喝 ${(it * 33 / 50 * 50).coerceIn(1200, 3500)}ml", color = P.sub, fontSize = 13.sp)
                    }
                }
                3 -> {
                    StepTitle("你的作息", "提醒只在起床到睡觉之间出现；睡前 30 分钟会先提醒你准备")
                    TimeRow("起床时间", wake) { wake = it }
                    Divider()
                    TimeRow("睡觉时间", sleep) { sleep = it }
                }
                4 -> {
                    StepTitle("三餐时间", "到点提醒你吃饭；宠物饿了也会在这时候一起告诉你")
                    meals.forEachIndexed { i, m ->
                        TimeRow(m.label, m.time) { t -> meals = meals.toMutableList().also { it[i] = m.copy(time = t) } }
                        if (i < meals.lastIndex) Divider()
                    }
                }
                5 -> {
                    StepTitle("选第一只宠物", "它会出现在通知栏和桌面上陪你。以后还能用萌点领养更多")
                    PetChooser(species) { species = it; if (petName.isBlank()) petName = "" }
                    Gap(16.dp)
                    OutlinedTextField(petName, { petName = it.take(8) }, Modifier.fillMaxWidth(),
                        label = { Text("给它起个名字") }, placeholder = { Text(Catalog.of(species).defaultName) }, singleLine = true)
                }
                6 -> {
                    StepTitle("最后一步：让提醒准时", "下面这些权限都是为了提醒能按时弹出来。不开也能用，只是可能不准时")
                    PermissionList(includeOptional = false)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton({ step-- }) { Text("上一步", color = P.sub) }
            Spacer(Modifier.weight(1f))
            if (step in 2..4) TextButton({ step++ }) { Text("跳过", color = P.sub) }
            Spacer(Modifier.width(8.dp))
            val label = when (step) { 0 -> "开始"; total - 1 -> "完成"; else -> "下一步" }
            PrimaryButton(label, Modifier.width(140.dp), enabled = step != 1 || gender != Gender.UNSET) {
                if (step == total - 1) finish() else step++
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, desc: String) {
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = P.ink)
    Gap(6.dp)
    Text(desc, color = P.sub, fontSize = 14.sp)
    Gap(20.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetChooser(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Catalog.all.forEach { sp ->
            val sel = sp.key == selected
            Column(
                Modifier.width(100.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) P.mintSoft else P.card)
                    .border(2.dp, if (sel) P.mint else P.line, RoundedCornerShape(18.dp))
                    .clickable { onPick(sp.key) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PetSprite(sp.key, if (sel) "energetic" else "idle", 72.dp)
                Text(sp.name, fontSize = 14.sp, color = P.ink, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayRow(value: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow("生日", value ?: "不填") { open = true }
    if (open) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ onChange(null); open = false }) { Text("不填") } },
        ) { DatePicker(state) }
    }
}

/** 权限列表：引导页和"权限与提醒保障"页共用 */
@Composable
fun PermissionList(includeOptional: Boolean) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    androidx.compose.runtime.key(tick) { PermCard(ctx, includeOptional, notifLauncher, activityLauncher) }
}

@Composable
private fun PermCard(
    ctx: android.content.Context,
    includeOptional: Boolean,
    notifLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    activityLauncher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    MCard {
        PermRow("通知", "所有提醒和通知栏宠物都靠它（必需）", Perms.notifications(ctx)) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.startActivity(Perms.notifSettingsIntent(ctx))
        }
        Divider()
        PermRow("精确闹钟", "让提醒准点响。不开的话可能晚几分钟到十几分钟", Perms.exactAlarm(ctx)) {
            runCatching { ctx.startActivity(Perms.exactAlarmIntent(ctx)) }
        }
        Divider()
        PermRow("忽略电池优化", "防止系统省电时把提醒和宠物关掉", Perms.battery(ctx)) {
            runCatching { ctx.startActivity(Perms.batteryIntent(ctx)) }
        }
        Perms.vendorAutostart(ctx)?.let { intent ->
            Divider()
            PermRow("${Perms.vendorName()}自启动", "这个牌子的手机还要允许萌叮自启动和后台运行", null) {
                runCatching { ctx.startActivity(intent) }
            }
        }
        if (includeOptional) {
            Divider()
            PermRow("悬浮窗（可选）", "打开「桌面悬浮宠物」时需要", Perms.overlay(ctx)) {
                runCatching { ctx.startActivity(Perms.overlayIntent(ctx)) }
            }
            Divider()
            PermRow("身体活动（可选）", "打开「陪我走路」时需要：你走路它也跟着走", Perms.activity(ctx)) {
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            Divider()
            PermRow("麦克风（可选）", "在便签里录音时需要", Perms.mic(ctx)) {
                activityLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = ok != true, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = P.ink)
            Text(desc, fontSize = 12.sp, color = P.sub)
        }
        Spacer(Modifier.width(10.dp))
        when (ok) {
            true -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Check, null, Modifier.size(18.dp), tint = P.mint)
                Text("已开启", fontSize = 13.sp, color = P.mint)
            }
            false -> Text("去开启", fontSize = 13.sp, color = P.pink, fontWeight = FontWeight.SemiBold)
            null -> Text("去设置", fontSize = 13.sp, color = P.sub)
        }
    }
    Spacer(Modifier.height(0.dp))
}
