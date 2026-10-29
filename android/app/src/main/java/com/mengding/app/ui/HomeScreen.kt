package com.mengding.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.mengding.app.unity.UnityHost
import com.mengding.app.widget.Refresh
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Dp

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
    var inventory by remember { mutableStateOf<String?>(null) }
    val homeScroll = rememberScrollState()
    LaunchedEffect(inventory) { homeScroll.animateScrollTo(0) }
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
    // 完成自定义提醒等：宠物冒一句夸奖
    val said by PetFx.said.collectAsState()
    LaunchedEffect(said) { said?.let { (text, at) -> if (Time.now() - at < 5000) bubble = text } }
    // 记录、萌点、宠物、衣服变了就看看有没有新徽章（停一下再结算，连点时不重复弹）
    val notesNow by Store.notes.collectAsState()
    LaunchedEffect(cfg.economy, cfg.pets.size, priv.water.size, priv.meals.size, priv.sleeps, notesNow.notes.size) {
        delay(800)
        BadgeFx.queue += Actions.settleBadges(ctx)
    }
    val missed by Reminders.missedNotice.collectAsState()
    // 新手轻提示：3D / HD-2D 首页多两条（缩放拖动、展开横屏）
    val use3dTips = UnityHost.supported && cfg.settings.home3d
    CoachFor("home", buildList {
        add(Coach.Tip("home_stage", "点一下宠物摸摸它；按住不放会弹出更多玩法"))
        if (use3dTips) {
            add(Coach.Tip("home_stage", "两根手指捏合可以缩放，拖动看看整个小镇"))
            add(Coach.Tip("home_more", "点「更多 ›」展开横屏，逛逛全屋"))
        } else add(Coach.Tip("home_stage", "宠物脖子上的小铃铛点一下会「叮」，它会抬头看你"))
        add(Coach.Tip("home_tasks", "这里是今日小任务：做完点「领取」，3 条全完成再送全勤奖"))
    })

    // 首页 3D 小屋：Kotlin 管数据，Unity 管场景（JSON 协议见 unity/UnityHost.kt）。
    // 首页 UI ↔ 房间家具：体力 ↔ 床，喂食 / 饮水 / 喝水打卡 ↔ 食盆水碗，换装 ↔ 衣柜，时间天气 ↔ 窗外和院子的天空，便签 / 解压游戏 ↔ 书桌电脑。
    val use3d = UnityHost.supported && cfg.settings.home3d
    var deskChooser by remember { mutableStateOf(false) }
    // 3D 小屋里换装要先去衣柜：小蜜走到衣柜前、打开衣柜（Unity 回 wardrobe_opened）后才弹衣柜面板，穿上 / 脱下都在衣柜前完成；
    // 面板关上时衣柜也关上，小蜜出来转一圈。喂食 / 饮水打开面板时它先走到食盆边等着。
    var wardrobeWaiting by remember { mutableStateOf(false) }
    val openPanel: (String) -> Unit = { cat ->
        when {
            !use3d || pet == null || !UnityHost.ready -> inventory = cat
            cat == "outfit" -> if (inventory != "outfit" && !wardrobeWaiting) { wardrobeWaiting = true; UnityHost.command("wardrobe_open") }
            else -> { inventory = cat; UnityHost.goTo("food_bowl") }
        }
    }
    LaunchedEffect(wardrobeWaiting) {
        // Unity 那边万一没回话（没起来、被打断），别让按钮点了没反应
        if (wardrobeWaiting) { delay(12_000); if (wardrobeWaiting) { wardrobeWaiting = false; inventory = "outfit" } }
    }
    var lastPanel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(inventory) {
        if (use3d && lastPanel == "outfit" && inventory != "outfit") UnityHost.wardrobeClose()
        lastPanel = inventory
    }
    // HD-2D 小镇的背景音乐（小喇叭开关在相框左下角）
    val hd2dHome = use3d && UnityHost.launchStyle == "hd2d"
    if (hd2dHome) HomeMusic(cfg.settings.homeMusic)
    // 没有宠物出门（都在宠物部屋里）：场景照常显示，只是不能操作 —— 告诉小镇「都在家」，它就把宠物放进部屋、只展示
    val homePet = cfg.pets.firstOrNull()
    if (use3d && pet == null && homePet != null) {
        val owned = remember(cfg.pets) { cfg.pets.map { it.species } }
        LaunchedEffect(homePet.species, owned, UnityHost.ready) { UnityHost.setPet(homePet.species, "idle", homePet.worn, owned, outing = false) }
    }
    if (use3d && pet != null) {
        val mood = remember(cfg, priv, now / 2000) { PetFx.actionFor(pet, cfg, isOuting = false) }
        // 一并告诉小镇：已拥有哪些宠物、这只是不是带出门的（只有带出门的能在场景里操作，其余已拥有的在宠物部屋里待着）
        val owned = remember(cfg.pets) { cfg.pets.map { it.species } }
        val outing = cfg.outingId == pet.id
        LaunchedEffect(pet.species, mood, pet.worn, owned, outing, UnityHost.ready) { UnityHost.setPet(pet.species, mood, pet.worn, owned, outing) }
        val coins = cfg.economy.points
        LaunchedEffect(pet.stamina.toInt(), pet.satiety.toInt(), pet.hydration.toInt(), coins, UnityHost.ready) {
            UnityHost.stats(pet.stamina.toInt(), pet.satiety.toInt(), pet.hydration.toInt(), coins)
        }
        // 金币涨了：房间里从食盆飞出金币（金币已经在 Kotlin 这边记好账，Unity 只演）
        var lastCoins by remember { mutableStateOf(coins) }
        LaunchedEffect(coins) { if (coins > lastCoins) UnityHost.coins(coins - lastCoins, UnityHost.nextId("coins")); lastCoins = coins }
        LaunchedEffect(now / 60_000, Weather.code, Weather.tempC, UnityHost.ready) {
            val t = Time.dateTimeOf(now)
            val code = Weather.code
            UnityHost.env(t.hour, t.minute, t.hour >= 19 || t.hour < 6, code?.let { Weather.name(it) } ?: "晴",
                Weather.tempC, Weather.rainy(code), Weather.snowy(code))
        }
        LaunchedEffect(fx) {
            val f = fx ?: return@LaunchedEffect
            val id = "fx-${f.start}" // 同一个临时动作只演一次，页面重建也不会重播
            when (f.action) {
                "eat" -> UnityHost.command("feed", id)
                "drink" -> UnityHost.command("drink", id)
                "spin", "roll", "bow", "easter" -> UnityHost.command("spin", id)
            }
        }
        // 换了衣服不用再单独发指令：setPet 带着 outfits 过去，Unity 发现变了就在衣柜前换（衣柜没开会先走过去开）
        LaunchedEffect(bubble) { bubble?.let { com.mengding.app.logic.BubbleText.plain(it).takeIf { s -> s.isNotBlank() }?.let { s -> UnityHost.say(s) } } }
        LaunchedEffect(pet.species) {
            UnityHost.events.collect { evt ->
                when (evt.optString("evt")) {
                    // 在房间里被摸醒 / 点地面叫醒：App 这边也把「沉睡」状态解除，首页文字跟着变
                    "woken" -> Actions.wakeByTouch(ctx)
                    "wardrobe_opened" -> if (wardrobeWaiting) { wardrobeWaiting = false; inventory = "outfit" }
                    "petted" -> {
                        Actions.petPet(ctx)
                        PetVoice.play(ctx, pet.species)
                        UnityHost.say(Speech.phrase())
                    }
                    // 点房间里的家具 = 点首页对应的按钮
                    "prop_tap" -> when (evt.optString("prop")) {
                        "food_bowl" -> openPanel("food")
                        "water_bowl" -> openPanel("drink")
                        "wardrobe" -> openPanel("outfit")
                        "desk" -> deskChooser = true
                        "bed" -> toast(Actions.lull(ctx))
                        "window", "sky" -> if (weather == null) askLocation = true else toast("现在 ${Time.hm(Time.now())} · $weather")
                    }
                }
            }
        }
    }
    BackHandler(use3d && UnityHost.expanded) { UnityHost.collapse() }
    // 全屋展开时横屏铺满（顺手藏掉状态栏），收起或离开首页恢复竖屏
    val activity = ctx as? android.app.Activity
    val roomExpanded = use3d && UnityHost.expanded
    LaunchedEffect(roomExpanded) {
        val a = activity ?: return@LaunchedEffect
        a.requestedOrientation = if (roomExpanded) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val bars = androidx.core.view.WindowCompat.getInsetsController(a.window, a.window.decorView)
        bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (roomExpanded) bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        else bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
        }
    }
    BackHandler(inventory != null) { inventory = null }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
    val expanded = use3d && UnityHost.expanded
    LaunchedEffect(expanded) { if (expanded) homeScroll.scrollTo(0) }
    val frameHeight = if (expanded) maxHeight - 16.dp else if (inventory != null) maxHeight * 0.39f else 286.dp
    Column(Modifier.fillMaxSize().verticalScroll(homeScroll, enabled = inventory == null && !expanded).padding(horizontal = 16.dp)) {
        // 顶部：有提醒该响没响（手机把闹钟清了）→ 提示去体检；打开 App 时已经顺手重排了
        if (missed && inventory == null && !expanded) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, end = 2.dp).mdBlock(P.pink)
                .clickable { Reminders.missedNotice.value = false; Nav.go("health") }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Bell, null, Modifier.size(18.dp), tint = P.pink)
                Spacer(Modifier.width(8.dp))
                Text("刚才有提醒没响，去体检一下", Modifier.weight(1f), fontSize = 14.sp, color = P.ink)
                Icon(Ic.Close, "关掉", Modifier.size(18.dp).clickable { Reminders.missedNotice.value = false }, tint = P.sub)
            }
        }
        // 顶部：时间、天气与问候
        if (inventory == null && !expanded) {
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
                Modifier.mdTag(P.pink).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PointsIcon(20.dp)
                Spacer(Modifier.width(4.dp))
                Text("${cfg.economy.points}", fontWeight = FontWeight.Bold, color = P.ink)
            }
            Spacer(Modifier.width(8.dp))
            val can = Economy.canCheckIn(cfg)
            MdButton({ toast(Actions.checkIn(ctx)) }, Modifier.height(36.dp), enabled = can, role = MdRole.SOLID,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)) { Text(if (can) "签到" else "已签到", fontSize = 14.sp) }
        }
        Gap(10.dp)
        Box(Modifier.coachTarget("home_tasks")) { TodayTasksCard() }
        Gap(10.dp)
        } else Gap(8.dp)

        // 宠物舞台
        if (pet != null) PetStage(pet.name, pet.species, action, PetSim.describe(pet), bubble, pet.worn, Outfits.cuteness(ctx, pet.worn),
            onPress = { PetVoice.play(ctx, pet.species) },
            onTap = { Actions.petPet(ctx); bubble = Speech.bubble(pet, com.mengding.app.pet.Emotes.has(ctx, pet.species)) }, onLong = { menu = true }, stageHeight = frameHeight, use3d = use3d,
            expanded = expanded, onInventory = openPanel)
        // 宠物都召回了：相框和场景照样在，只能看不能操作；提示去宠物部屋带一只出门
        else if (homePet != null) PetStage("没有宠物出门", homePet.species, "sleep", "都在宠物部屋，去部屋带一只出门吧", null, emptyList(), 0,
            onPress = {}, onTap = { toast("现在没有宠物出门，去宠物部屋选一只带出门才能互动") }, onLong = {}, stageHeight = frameHeight, use3d = use3d,
            expanded = expanded, onInventory = {}, showPet = false)
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
            QuickShopRow(openPanel)
        }
        Gap(8.dp)
        ModuleRow()

        SectionTitle("今天")
        WaterCard(ctx)
        Gap(10.dp)
        MealCard(ctx)
        OtherRemindersCard()
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
    val panelHeight = maxHeight * 0.5f
    AnimatedVisibility(inventory != null, Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        InventoryPanel(inventory ?: "food", Modifier.fillMaxWidth().height(panelHeight),
            onCategory = { if (it == "outfit" && inventory != "outfit") { inventory = null; openPanel(it) } else inventory = it }, onClose = { inventory = null },
            onFeedback = { bubble = it })
    }
    }

    if (deskChooser) {
        AlertDialog(
            onDismissRequest = { deskChooser = false },
            title = { Text("书桌电脑") },
            text = {
                Column {
                    listOf("写便签" to "notes", "玩解压游戏" to "games").forEach { (label, page) ->
                        Text(label, Modifier.fillMaxWidth().clickable { deskChooser = false; UnityHost.collapse(); Nav.go(page) }
                            .padding(vertical = 12.dp), fontSize = 16.sp, color = P.ink)
                    }
                }
            },
            confirmButton = { TextButton({ deskChooser = false }) { Text("收起") } },
        )
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
    stageHeight: Dp = 286.dp,
    use3d: Boolean = false,
    expanded: Boolean = false,
    onInventory: (String) -> Unit = {},
    showPet: Boolean = true,
) {
    val cfg by Store.config.collectAsState()
    // 场景每分钟看一次该不该换（按时间 / 下雨下雪 / 12 月）
    var minute by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); minute = java.time.LocalDateTime.now() } }
    val code = if (cfg.settings.localWeather) Weather.code else null
    val key = cfg.settings.homeScene.takeIf { it in Scenes.ALL } ?: Scenes.auto(minute, Weather.snowy(code))
    val ctx = LocalContext.current
    val light = remember(key) { Scenes.load(ctx, key).lightText }
    val sceneTap = remember { SceneTap() }
    // 相框式场景：外层负责稳定比例、边缘和阴影，内层才接收场景触摸。
    // 这样滚动首页时不会把边框误判成宠物操作区域。
    Box(
        Modifier.fillMaxWidth().height(stageHeight).coachTarget("home_stage")
            .clip(RoundedCornerShape(30.dp))
            .background(FrameWood)
            .border(2.dp, Color(0xFFE8B477), RoundedCornerShape(30.dp))
            .padding(7.dp),
    ) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(InnerRadius))
                .then(if (use3d) Modifier else Modifier.pointerInput(species, showPet) {
                    detectTapGestures(onPress = { onPress() }, onLongPress = { onLong() }, onTap = { off ->
                        // 点到项圈铃铛附近：叮一声，宠物抬头看你；其余地方照旧是摸摸
                        if (showPet && sceneTap.bell.contains(off.x, off.y)) {
                            com.mengding.app.pet.Ding.ring(ctx)
                            PetFx.play("lift", 1600)
                        } else onTap()
                    })
                }),
        ) {
        // 3D 小屋自己处理点击（摸宠物、点地板走过去）；2D 场景还是原来的点按 / 长按
        // 场景直接铺满相框内区，不再垫深色底和内边距（0.3.11 那圈深色就是"黑条"）；展开全屋时相框原地铺满首页
        if (use3d) UnityStage(Modifier.fillMaxSize())
        // 2D 场景没有宠物出门时，画家里那只在睡觉（只展示）
        else LiveScene(key, species, action, outfits, rain = Weather.rainy(code), modifier = Modifier.fillMaxSize(), bubble = bubble,
            bubbleStyle = cfg.settings.bubbleStyle, tap = sceneTap)
        FrameMask(Modifier.matchParentSize())
        // HD-2D 小镇：左下角的小喇叭，开关首页背景音乐
        if (use3d && UnityHost.launchStyle == "hd2d") {
            val musicOn = cfg.settings.homeMusic
            Box(Modifier.align(Alignment.BottomStart).padding(8.dp).size(34.dp)
                .clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xCCFFFFFF))
                .clickable { Store.update { it.copy(settings = it.settings.copy(homeMusic = !musicOn)) } },
                contentAlignment = Alignment.Center) {
                Icon(if (musicOn) Ic.Sound else Ic.Mute, if (musicOn) "关掉背景音乐" else "打开背景音乐", Modifier.size(20.dp),
                    tint = if (musicOn) P.mintDeep else P.sub)
            }
        }
        if (expanded) ExpandedChrome(name, onMenu = onLong, onInventory = onInventory)
        else {
        // 名字和状态放在半透明小底上，什么背景都看得清
        Column(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(14.dp))
            .background(if (light) Color(0x66141432) else Color(0x99FFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (light) Color.White else P.ink)
            Text(desc, fontSize = 12.sp, color = if (light) Color(0xFFDDE2F6) else P.sub)
        }
        Text("萌度 $cuteness", Modifier.align(Alignment.TopEnd).padding(12.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .82f)).padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = P.pink)
        // 2D 场景的说话气泡由 LiveScene 跟着宠物头顶画（SceneBubble），这里不再固定摆放
        if (use3d && showPet) Text("更多 ›", Modifier.align(Alignment.BottomEnd).padding(8.dp).coachTarget("home_more").clip(RoundedCornerShape(10.dp))
            .background(Color(0xCCFFFFFF)).clickable { UnityHost.expand("more") }.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp, color = P.sub)
        else if (!use3d) Text("轻点摸摸 · 长按更多", Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(10.dp))
            .background(if (light) Color(0x55141432) else Color(0x88FFFFFF)).padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp, color = if (light) Color(0xFFDDE2F6) else P.sub)
        }
        }
        // 相框铭牌把场景和首页其它卡片分开，也让当前场景始终可识别（全屋横屏时顶上已经有「某某的家」，不再挂）。
        if (!expanded) Text(
            if (use3d) "紫色卧室" else Scenes.ALL[key] ?: key,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xDDFFF3D8))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF704636),
        )
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
        MdMeter(total * 100f / goal.coerceAtLeast(1), P.blue, warnBelow = 0f)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(ctx: android.content.Context) {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    var noteFor by remember { mutableStateOf<String?>(null) }
    var editTimes by remember { mutableStateOf(false) }
    if (editTimes) AlertDialog(
        onDismissRequest = { editTimes = false; Refresh.all(ctx) },
        title = { Text("吃饭提醒时间") },
        // 和提醒设置页同一个编辑器：改完马上重排闹钟
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { MealTimesEditor() } },
        confirmButton = { TextButton({ editTimes = false; Refresh.all(ctx) }) { Text("好了") } },
    )
    MCard {
        HRow {
            Icon(Ic.Bowl, null, Modifier.size(24.dp), tint = P.gold)
            Text("吃饭", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text("改提醒时间", Modifier.mdTag()
                .clickable { editTimes = true }.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, color = P.mintDeep)
        }
        Gap(4.dp)
        Text("点一下记录这一餐，可以写一句吃了什么", fontSize = 12.sp, color = P.sub)
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
        // 「宠物部屋」始终进原来的部屋页；全屋场景从相框的「更多 ›」进
        ModuleButton("宠物部屋", ui.pet, Ic.Room) { Nav.go("room") }
        ModuleButton("提醒设置", ui.today, Ic.Bell) { Nav.go("reminders") }
    }
}

@Composable
private fun QuickShopRow(onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ctx = LocalContext.current
        listOf("喂食" to "food", "饮水" to "drink", "换装" to "outfit").forEach { (label, cat) ->
            // 专属像素按钮：底图左边是小图标，文字放在右半边
            val img = remember(cat) {
                ctx.assets.open("shop/home_$cat.png").use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, android.graphics.BitmapFactory.Options().apply { inScaled = false })!!
                }.asImageBitmap()
            }
            Box(Modifier.weight(1f).height(36.dp).clickable { onOpen(cat) }) {
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
        else MdRowIcon(icon, size = 54.dp)
        Gap(4.dp)
        Text(label, fontSize = 12.sp, color = P.ink)
    }
}

/** 首页 3D 小屋：把 Unity 的画面挂进相框（或展开后的全屏层）；离开首页时摘下并暂停，省电。 */
@Composable
fun UnityStage(modifier: Modifier) {
    DisposableEffect(Unit) {
        UnityHost.setVisible(true)
        onDispose { UnityHost.setVisible(false) }
    }
    // Unity 画面是独立的 SurfaceView：刚挂上时第一帧还没画出来会是黑的，先用房间底色盖住，画出来后再揭开
    var cover by remember { mutableStateOf(true) }
    LaunchedEffect(UnityHost.ready) { if (UnityHost.ready) { delay(350); cover = false } }
    Box(modifier) {
        // 包一层自己的容器：视口在相框和全屏之间搬家时，旧容器释放只会摘自己身上的画面
        AndroidView(
            factory = { c -> PinchFrame(c).also { it.addView(UnityHost.view(c), android.widget.FrameLayout.LayoutParams(-1, -1)) } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { UnityHost.detach(it) },
        )
        if (cover) Box(Modifier.fillMaxSize().background(if (UnityHost.launchStyle == "hd2d") Color(0xFFBFDDF2) else Color(0xFFF6E3EE)))
    }
}

/**
 * 装 Unity 画面的容器：第二根手指按下（双指缩放）时让外层的首页滚动别抢手势，两根手指的移动完整交给场景；
 * 单指拖动照旧可以滚动首页。
 */
private class PinchFrame(c: android.content.Context) : android.widget.FrameLayout(c) {
    override fun dispatchTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(e)
    }
}

private val InnerRadius = 18.dp
private val FrameWood = Color(0xFF9B613F)
private val FrameLip = Color(0xFFD89469)

/**
 * 相框内沿：画在场景上面。Unity 的 SurfaceView 不吃 Compose 的圆角裁剪，四个角会是直角戳出圆角框；
 * 这里把圆角外面用木框色涂实、再描一圈内沿，场景边缘就完整收进框里，不留缝也不出界。
 */
@Composable
private fun FrameMask(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = InnerRadius.toPx()
        val lip = 3.dp.toPx()
        val corners = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height,
                androidx.compose.ui.geometry.CornerRadius(r)))
        }
        drawPath(corners, FrameWood)
        drawRoundRect(FrameLip, topLeft = androidx.compose.ui.geometry.Offset(lip / 2, lip / 2),
            size = androidx.compose.ui.geometry.Size(size.width - lip, size.height - lip),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r - lip / 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(lip))
    }
}

/** 全场景：卧室 + 客厅 + 庭院 / 露台的控件层。竖屏下一次看到一两个房间，左右拖动看全屋，双指缩放。 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ExpandedChrome(name: String, onMenu: () -> Unit, onInventory: (String) -> Unit) {
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(4000); hint = false }
    Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoomChip("‹ 收起") { UnityHost.collapse() }
        Spacer(Modifier.weight(1f))
        Text(if (UnityHost.inPetRoom) "${name}的宠物屋" else "${name}的家", Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCCFFFFFF)).padding(horizontal = 12.dp, vertical = 5.dp),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = P.ink)
        Spacer(Modifier.weight(1f))
        RoomChip("互动") { onMenu() }
    }
    if (hint) Text("点家具试试 · 点门厅的门进宠物屋 · 双指放大", Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        .clip(RoundedCornerShape(10.dp)).background(Color(0x99141432)).padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp, color = Color.White)
}

@Composable
private fun RoomChip(label: String, onClick: () -> Unit) {
    Text(label, Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xE6FFFFFF)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, color = P.ink)
}
