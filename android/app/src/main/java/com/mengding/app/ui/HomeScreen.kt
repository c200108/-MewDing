package com.mengding.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.R
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Economy
import com.mengding.app.logic.Period
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.PetFx
import com.mengding.app.pet.PetVoice
import com.mengding.app.pet.Outfits
import com.mengding.app.pet.Speech
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    val fx by PetFx.transient.collectAsState()
    var now by remember { mutableLongStateOf(Time.now()) }
    LaunchedEffect(fx) {
        // 临时动作结束后刷新一下
        while (true) { delay(1000); now = Time.now() }
    }
    val pet = PetSim.outing(cfg)
    val action = remember(cfg, priv, fx, now / 2000) { PetFx.actionFor(pet, cfg) }
    var menu by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf<String?>(null) }
    // 本地天气：用户没同意之前不读位置，只显示一个"看本地天气"的入口
    var weather by remember { mutableStateOf<String?>(null) }
    var askLocation by remember { mutableStateOf(false) }
    val locationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Store.update { it.copy(settings = it.settings.copy(localWeather = true)) }
        else toast("没有位置权限，就不显示本地天气了")
    }
    val weatherOn = cfg.settings.localWeather && Weather.permitted(ctx)
    LaunchedEffect(weatherOn) {
        weather = if (weatherOn) { weather ?: "定位中…" ; Weather.local(ctx) } else null
    }
    LaunchedEffect(bubble) { if (bubble != null) { delay(3500); bubble = null } }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // 顶部：时间、天气与问候
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val h = Time.dateTimeOf(now).hour
                val hello = when (h) { in 5..10 -> "早上好"; in 11..13 -> "中午好"; in 14..17 -> "下午好"; else -> "晚上好" }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Time.hm(now), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Spacer(Modifier.width(12.dp))
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    Text(weather ?: "☁ 看本地天气", fontSize = 12.sp, color = if (weather == null) P.mintDeep else P.sub,
                        modifier = Modifier.padding(bottom = 3.dp).clip(RoundedCornerShape(8.dp)).clickable {
                            if (weatherOn) scope.launch { weather = "刷新中…"; weather = Weather.local(ctx, force = true) }
                            else askLocation = true
                        }.padding(horizontal = 4.dp))
                }
                Text("$hello，${cfg.profile.nickname.ifBlank { "朋友" }} · ${Time.today().monthValue}月${Time.today().dayOfMonth}日", fontSize = 13.sp, color = P.sub)
            }
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(P.pinkSoft).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PointsIcon(20.dp)
                Spacer(Modifier.width(4.dp))
                Text("${cfg.economy.points}", fontWeight = FontWeight.Bold, color = P.ink)
            }
            Spacer(Modifier.width(8.dp))
            val can = Economy.canCheckIn(cfg)
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (can) P.mint else P.line)
                    .clickable(enabled = can) { toast(Actions.checkIn(ctx)) }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(if (can) "签到" else "已签到", color = if (can) Color.White else P.sub, fontSize = 14.sp) }
        }
        Gap(14.dp)

        // 宠物舞台
        if (pet != null) PetStage(pet.name, pet.species, action, PetSim.describe(pet), bubble, pet.worn, Outfits.cuteness(ctx, pet.worn),
            onPress = { PetVoice.play(ctx, pet.species) },
            onTap = { Actions.petPet(ctx); bubble = Speech.phrase() }, onLong = { menu = true })
        // 体力 / 饱食 / 水分 和 食物 / 饮料 / 服装 快捷按钮包在同一个框里
        Gap(10.dp)
        MCard(padding = 14.dp) {
            pet?.let {
                HRow(gap = 12.dp) {
                    StatBar("体力", it.stamina, P.mint, Modifier.weight(1f))
                    StatBar("饱食", it.satiety, P.gold, Modifier.weight(1f))
                    StatBar("水分", it.hydration, P.blue, Modifier.weight(1f))
                }
                Gap(12.dp)
            }
            QuickShopRow()
        }
        Gap(8.dp)
        ModuleRow()

        SectionTitle("今天")
        WaterCard(ctx)
        Gap(10.dp)
        MealCard(ctx)
        Gap(10.dp)
        SleepCard(ctx)
        if (Period.enabled(cfg)) {
            Gap(10.dp)
            val f = Period.forecast(priv)
            MCard(Modifier.clickable { Nav.tab = "period" }) {
                HRow {
                    Icon(Ic.Calendar, null, Modifier.size(22.dp), tint = P.pink)
                    Text(
                        when {
                            f.current != null -> "经期第 ${java.time.temporal.ChronoUnit.DAYS.between(f.current.start, Time.today()) + 1} 天，注意保暖休息"
                            f.nextStart == null -> "还没有经期记录，点这里开始记录"
                            else -> "距离下次经期大约 ${Period.daysUntil(f.nextStart)} 天"
                        }, fontSize = 15.sp, color = P.ink,
                    )
                }
            }
        }
        Gap(10.dp)
        Reminders.nextTime()?.let {
            Text("下一次提醒：${Time.hm(it)}", fontSize = 13.sp, color = P.sub, modifier = Modifier.padding(start = 4.dp))
        }
        Gap(24.dp)
    }

    if (askLocation) {
        AlertDialog(
            onDismissRequest = { askLocation = false },
            title = { Text("看看你那里的天气") },
            text = {
                Text("萌叮需要知道你的大致位置（精确到约 1 公里）来查当地天气。\n" +
                    "· 位置只用来查天气，发给公开天气服务 Open-Meteo，不保存、不上传到别处\n" +
                    "· 接下来手机会弹出位置权限提示，选「仅在使用该应用时允许」就够了\n" +
                    "· 随时可以在「我的 → 隐私与数据」里关掉")
            },
            confirmButton = {
                TextButton({
                    askLocation = false
                    if (Weather.permitted(ctx)) Store.update { it.copy(settings = it.settings.copy(localWeather = true)) }
                    else locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("同意，获取位置") }
            },
            dismissButton = { TextButton({ askLocation = false }) { Text("暂不") } },
        )
    }

    if (menu && pet != null) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("和 ${pet.name} 玩") },
            text = {
                Column {
                    listOf(
                        "喝水打卡（记录自己）" to { toast(Actions.drink(ctx)) },
                        "吃饭打卡（记录自己）" to { toast(Actions.currentMeal()?.let { Actions.eat(ctx, it) } ?: "今天三餐都记过啦") },
                        "哄睡" to { toast(Actions.lull(ctx)) },
                        "摸摸它" to { toast(Actions.petPet(ctx)) },
                        "去玩解压游戏" to { Nav.go("games") },
                    ).forEach { (label, run) ->
                        Text(label, Modifier.fillMaxWidth().clickable { menu = false; run() }.padding(vertical = 12.dp),
                            fontSize = 16.sp, color = P.ink)
                    }
                }
            },
            confirmButton = { TextButton({ menu = false }) { Text("收起") } },
        )
    }
}

@Composable
fun PetStage(
    name: String, species: String, action: String, desc: String, bubble: String?, outfits: List<String>, cuteness: Int,
    onPress: () -> Unit, onTap: () -> Unit, onLong: () -> Unit,
) {
    val cfg by Store.config.collectAsState()
    // 场景每分钟看一次该不该换（按时间 / 下雨下雪 / 12 月）
    var minute by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); minute = java.time.LocalDateTime.now() } }
    val code = if (cfg.settings.localWeather) Weather.code else null
    val key = cfg.settings.homeScene.takeIf { it in Scenes.ALL } ?: Scenes.auto(minute, Weather.snowy(code))
    val ctx = LocalContext.current
    val light = remember(key) { Scenes.load(ctx, key).lightText }
    Box(
        Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp))
            .pointerInput(species) { detectTapGestures(onPress = { onPress() }, onTap = { onTap() }, onLongPress = { onLong() }) },
    ) {
        LiveScene(key, species, action, outfits, rain = Weather.rainy(code), modifier = Modifier.fillMaxSize())
        // 名字和状态放在半透明小底上，什么背景都看得清
        Column(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(14.dp))
            .background(if (light) Color(0x66141432) else Color(0x99FFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (light) Color.White else P.ink)
            Text(desc, fontSize = 12.sp, color = if (light) Color(0xFFDDE2F6) else P.sub)
        }
        Text("萌度 $cuteness", Modifier.align(Alignment.TopEnd).padding(12.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .82f)).padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = P.pink)
        // 宠物说话的气泡
        bubble?.let {
            Text(
                it,
                Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 14.dp).widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .border(2.dp, P.mint, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 14.sp, color = Color(0xFF3A2C36),
            )
        }
        Text("轻点摸摸 · 长按更多", Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(10.dp))
            .background(if (light) Color(0x55141432) else Color(0x88FFFFFF)).padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp, color = if (light) Color(0xFFDDE2F6) else P.sub)
    }
}

@Composable
private fun WaterCard(ctx: android.content.Context) {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    Store.private.collectAsState().value
    val total = Actions.todayWater()
    val goal = cfg.settings.waterGoalMl
    var drinking by remember { mutableStateOf(false) }
    if (drinking) DrinkDialog { drinking = false }
    MCard {
        HRow {
            Icon(Ic.Drop, null, Modifier.size(24.dp), tint = P.blue)
            Column(Modifier.weight(1f)) {
                Text("喝水", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text("$total / ${goal}ml" + if (total >= Actions.waterCap(cfg)) "（今天已到上限）" else "", fontSize = 13.sp, color = P.sub)
            }
            PrimaryButton("喝水", icon = Ic.Plus, enabled = total < Actions.waterCap(cfg)) { drinking = true }
        }
        Gap(10.dp)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(P.line)) {
            Box(Modifier.fillMaxWidth((total.toFloat() / goal).coerceIn(0f, 1f)).height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(P.blue))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(ctx: android.content.Context) {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    var noteFor by remember { mutableStateOf<String?>(null) }
    MCard {
        HRow {
            Icon(Ic.Bowl, null, Modifier.size(24.dp), tint = P.gold)
            Text("吃饭", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text("点一下记录，可以写一句备注", fontSize = 12.sp, color = P.sub)
        }
        Gap(10.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cfg.profile.meals.forEach { m ->
                val log = priv.meals.firstOrNull { it.day == Time.todayStr() && it.label == m.label }
                Pill(if (log != null) "${m.label} 已吃" else "${m.label} ${m.time}", log != null) {
                    if (log == null) noteFor = m.label else toast(log.note.ifBlank { "${m.label} 在 ${Time.hm(log.t)} 记过了" })
                }
            }
        }
    }
    noteFor?.let { label ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("记录$label") },
            text = { OutlinedTextField(note, { note = it.take(40) }, label = { Text("吃了什么（可不填）") }, singleLine = true) },
            confirmButton = { TextButton({ toast(Actions.eat(ctx, label, note.trim())); noteFor = null }) { Text("记下") } },
            dismissButton = { TextButton({ noteFor = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SleepCard(ctx: android.content.Context) {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    val night = Actions.tonight()
    val tonight = priv.sleeps.firstOrNull { it.day == night }
    val lastNight = priv.sleeps.firstOrNull { it.day == Time.today().minusDays(1).toString() }
    val hour = Time.dateTimeOf(Time.now()).hour
    MCard {
        HRow {
            Icon(Ic.Moon, null, Modifier.size(24.dp), tint = P.purple)
            Column(Modifier.weight(1f)) {
                Text("睡觉", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text(
                    when {
                        tonight?.bedAt != null -> "今晚 ${Time.hm(tonight.bedAt)} 睡下，晚安"
                        hour in 5..14 && lastNight?.wakeAt == null -> "早上好，昨晚睡得怎么样？"
                        else -> "计划 ${cfg.profile.sleep} 睡觉，按时睡有奖励"
                    }, fontSize = 13.sp, color = P.sub,
                )
            }
        }
        if (hour in 5..14 && lastNight?.wakeAt == null) {
            Gap(10.dp)
            HRow {
                Pill("睡得好", false) { toast(Actions.wakeUp(ctx, 3)) }
                Pill("一般", false) { toast(Actions.wakeUp(ctx, 2)) }
                Pill("没睡好", false) { toast(Actions.wakeUp(ctx, 1)) }
            }
        } else if (tonight?.bedAt == null) {
            Gap(10.dp)
            GhostButton("我去睡了", icon = Ic.Moon) { toast(Actions.goSleep(ctx)) }
        }
    }
}

/** 功能模块按钮：便签、解压、部屋、提醒设置。图标跟着界面主题换 */
@Composable
private fun ModuleRow() {
    val ui = LocalUiTheme.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ModuleButton("便签", ui.notes, Ic.Note) { Nav.go("notes") }
        ModuleButton("解压游戏", ui.games, Ic.Game) { Nav.go("games") }
        ModuleButton("宠物部屋", ui.pet, Ic.Room) { Nav.go("room") }
        ModuleButton("提醒设置", ui.today, Ic.Bell) { Nav.go("reminders") }
    }
}

@Composable
private fun QuickShopRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ctx = LocalContext.current
        listOf("食物" to "food", "饮料" to "drink", "服装" to "outfit").forEach { (label, cat) ->
            // 专属像素按钮：底图左边是小图标，文字放在右半边
            val img = remember(cat) {
                ctx.assets.open("shop/home_$cat.png").use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, android.graphics.BitmapFactory.Options().apply { inScaled = false })!!
                }.asImageBitmap()
            }
            Box(Modifier.weight(1f).height(36.dp).clickable { Nav.shopCategory = cat; Nav.stack.clear(); Nav.tab = "shop" }) {
                Image(img, label, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                Text(label, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, bottom = 2.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            }
        }
    }
}

@Composable
private fun ModuleButton(label: String, art: Int?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), label, Modifier.size(54.dp))
        else Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(P.mintSoft), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(28.dp), tint = P.mintDeep)
        }
        Gap(4.dp)
        Text(label, fontSize = 12.sp, color = P.ink)
    }
}
