package com.mengding.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.MainActivity
import com.mengding.app.R
import com.mengding.app.data.Store
import com.mengding.app.logic.Period

/** 简单的页面导航：底部 5 个标签 + 一个子页面栈 */
object Nav {
    var tab by mutableStateOf("home")
    /** 商店标签页当前分类：food / drink / outfit / game（首页快捷按钮会改它） */
    var shopCategory by mutableStateOf("food")
    val stack = mutableStateListOf<String>()
    fun go(page: String) { stack.add(page) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

/** art：界面主题的彩色图标；没有就用线条图标 icon */
private data class Tab(val key: String, val label: String, val icon: ImageVector, val art: Int? = null)

/** 冷启动时只放一次启动页 */
private var splashDone = false

@Composable
fun AppRoot(act: MainActivity) {
    val cfg by Store.config.collectAsState()
    var splash by remember { mutableStateOf(!splashDone) }
    Box(Modifier.fillMaxSize().background(P.cream)) {
        when {
            !cfg.profile.onboarded -> Onboarding()
            cfg.settings.privacyLock && !act.unlocked -> LockScreen { act.authenticate() }
            else -> Main()
        }
        if (splash) SplashScreen { splashDone = true; splash = false }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(LocalUiTheme.current.logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)))
        Gap(20.dp)
        Text("萌叮已上锁", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
        Gap(8.dp)
        Text("验证身份后查看你的记录", color = P.sub)
        Gap(28.dp)
        PrimaryButton("解锁", Modifier.width(180.dp), icon = Ic.Lock, onClick = onUnlock)
    }
}

@Composable
private fun Main() {
    val cfg by Store.config.collectAsState()
    val showPeriod = Period.enabled(cfg)
    val ui = LocalUiTheme.current
    val tabs = buildList {
        add(Tab("home", "今日", Ic.Home, ui.today))
        if (showPeriod) add(Tab("period", "经期", Ic.Calendar))
        add(Tab("shop", "商店", Ic.Bag))
        add(Tab("me", "我的", Ic.Me))
    }
    if (tabs.none { it.key == Nav.tab }) Nav.tab = "home"
    BackHandler(Nav.stack.isNotEmpty()) { Nav.back() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val page = Nav.stack.lastOrNull()
            if (page != null) SubPage(page)
            else when (Nav.tab) {
                "home" -> HomeScreen()
                "room" -> RoomScreen()
                "period" -> PeriodScreen()
                "notes" -> NotesScreen()
                "games" -> GamesScreen()
                "shop" -> ShopScreen()
                else -> MeScreen()
            }
        }
        if (Nav.stack.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(P.card).navigationBarsPadding().height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { t ->
                    val sel = Nav.tab == t.key
                    Column(
                        Modifier.weight(1f).clickable { Nav.tab = t.key }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) P.mintSoft else P.card)
                                .padding(horizontal = if (tabs.size > 6) 10.dp else 16.dp, vertical = 4.dp),
                        ) {
                            if (t.art != null) Image(
                                painterResource(t.art), t.label,
                                Modifier.size(24.dp).graphicsLayer { alpha = if (sel) 1f else 0.6f; scaleX = if (sel) 1.12f else 1f; scaleY = scaleX },
                            )
                            else Icon(t.icon, t.label, Modifier.size(22.dp), tint = if (sel) P.mintDeep else P.sub)
                        }
                        Text(t.label, fontSize = 11.sp, color = if (sel) P.mintDeep else P.sub)
                    }
                }
            }
        }
    }
}

/** 子页面统一的标题栏 + 可滚动内容 */
@Composable
fun PageScaffold(title: String, scroll: Boolean = true, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().then(if (Nav.stack.isNotEmpty()) Modifier.navigationBarsPadding() else Modifier)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Nav.stack.isNotEmpty()) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { Nav.back() }, contentAlignment = Alignment.Center) {
                    Icon(Ic.Back, "返回", Modifier.size(22.dp), tint = P.ink)
                }
            } else Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
        }
        val m = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        Column(if (scroll) m.verticalScroll(rememberScrollState()) else m) {
            content()
            Gap(28.dp)
        }
    }
}

@Composable
private fun SubPage(page: String) {
    when {
        page == "profile" -> ProfilePage()
        page == "reminders" -> ReminderSettingsPage()
        page == "display" -> PetDisplayPage()
        page == "icon" -> IconPage()
        page == "stats" -> StatsPage()
        page == "privacy" -> PrivacyPage()
        page == "perms" -> PermissionsPage()
        page == "about" -> AboutPage()
        page == "adopt" -> AdoptPage()
        page == "font" -> FontPage()
        page == "room" -> RoomScreen()
        page == "notes" -> NotesScreen()
        page == "games" -> GamesScreen()
        page == "care" -> CarePage()
        page == "game_pac" -> PacGamePage()
        page == "game_adv" -> AdvGamePage()
        page == "game_link" -> LinkGamePage()
        page == "speech" -> SpeechPage()
        page == "theme" -> ThemePage()
        page == "shop" -> ShopPage()
        page.startsWith("note:") -> NoteEditorPage(page.removePrefix("note:"))
        page.startsWith("pet:") -> PetDetailPage(page.removePrefix("pet:"))
        else -> PageScaffold("页面不存在") {}
    }
}
