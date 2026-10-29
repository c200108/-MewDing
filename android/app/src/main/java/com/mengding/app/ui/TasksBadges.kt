package com.mengding.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.R
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Badges
import com.mengding.app.logic.Tasks
import kotlinx.coroutines.launch

/** 徽章图：名字 → 资源（写死映射，打包缩资源时不会被当成没用的删掉） */
private fun badgeRes(name: String): Int = when (name) {
    "badge_collection_1" -> R.drawable.badge_collection_1
    "badge_collection_2" -> R.drawable.badge_collection_2
    "badge_collection_3" -> R.drawable.badge_collection_3
    "badge_collection_4" -> R.drawable.badge_collection_4
    "badge_collection_5" -> R.drawable.badge_collection_5
    "badge_collection_6" -> R.drawable.badge_collection_6
    "badge_collection_7" -> R.drawable.badge_collection_7
    "badge_collection_8" -> R.drawable.badge_collection_8
    "badge_day_2" -> R.drawable.badge_day_2
    "badge_day_3" -> R.drawable.badge_day_3
    "badge_day_4" -> R.drawable.badge_day_4
    else -> R.drawable.badge_day_1
}

/** 某个徽章在 level 级时显示哪张图（铃铛奖章每级一张，没解锁时显示第 1 级的灰图） */
private fun badgeImg(d: Badges.Def, level: Int) = badgeRes(d.img[(level - 1).coerceIn(0, d.img.size - 1)])

private val Gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val Gold = Color(0xFFF2B83B)

/** 解锁了新徽章：排队一个个放大展示（App 根部的 [BadgeUnlockLayer] 负责画） */
object BadgeFx {
    val queue = mutableStateListOf<Badges.Unlock>()
}

/** 一只小铃铛：亮的金色，没亮的灰色描边 */
@Composable
private fun TaskBell(lit: Boolean, size: androidx.compose.ui.unit.Dp = 20.dp) {
    val s = remember { Animatable(1f) }
    LaunchedEffect(lit) { if (lit) { s.snapTo(1.5f); s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) } }
    Icon(Ic.Bell, if (lit) "已完成" else "未完成", Modifier.size(size).graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = (s.value - 1f) * 30f },
        tint = if (lit) Gold else P.line)
}

/**
 * 首页签到下面的「今日小任务」：收起时一行（3 只小铃铛，领一条亮一只），点开看 3 条任务和「领取」。
 * 3 条都领了：叮叮叮连响一串，再发全勤奖。
 */
@Composable
fun TodayTasksCard() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    LaunchedEffect(cfg.daily.date, priv.customs.isNotEmpty()) { Actions.ensureTasks() }
    val tasks = cfg.daily.tasks
    if (tasks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val claimed = cfg.daily.tasksClaimed
    MCard(padding = 12.dp, modifier = Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日小任务", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Spacer(Modifier.width(8.dp))
            val ready = tasks.count { it !in claimed && Tasks.done(it, cfg, priv) }
            Text(if (cfg.daily.tasksBonus) "全部完成，明天再来" else if (ready > 0) "有 $ready 条可以领啦" else "完成 ${claimed.size}/3，全勤再送 ${Tasks.BONUS}",
                Modifier.weight(1f), fontSize = 12.sp, color = if (ready > 0) P.pink else P.sub)
            tasks.forEach { TaskBell(it in claimed); Spacer(Modifier.width(2.dp)) }
            Icon(if (open) Ic.Minus else Ic.Next, null, Modifier.size(16.dp), tint = P.sub)
        }
        if (open) tasks.forEach { key ->
            val d = Tasks.def(key)
            val prog = Tasks.progress(key, cfg, priv).coerceAtMost(d.goal)
            val done = prog >= d.goal
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskBell(key in claimed, 18.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, fontSize = 14.sp, color = P.ink)
                    Text(if (d.goal > 1) "$prog / ${d.goal}" else if (done) "已完成" else "还没完成", fontSize = 12.sp, color = P.sub)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { PointsIcon(16.dp); Text("+${d.pts}", fontSize = 13.sp, color = P.ink) }
                Spacer(Modifier.width(8.dp))
                val can = done && key !in claimed
                MdButton({ toast(Actions.claimTask(ctx, key)) }, Modifier.height(32.dp), enabled = can, role = MdRole.SOLID,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                    Text(if (key in claimed) "已领" else "领取", fontSize = 13.sp)
                }
            }
        }
    }
}

/** 我的 → 成就徽章：铃铛奖章一组（累计签到），收藏徽章一组；没解锁灰色 + 还差多少，下面 1–4 颗星是等级 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesPage() {
    val cfg by Store.config.collectAsState()
    val priv by Store.private.collectAsState()
    val notes by Store.notes.collectAsState()
    val ctx = LocalContext.current
    // 进来时顺手结算一次（补算老记录）
    LaunchedEffect(Unit) { BadgeFx.queue += Actions.settleBadges(ctx) }
    val v = remember(cfg, priv, notes) { Badges.values(Badges.seed(cfg), priv, notes.notes.size) }
    PageScaffold("成就徽章") {
        Text("坚持打卡、照顾宠物就能点亮徽章，每升一级送萌点。摸摸次数和小游戏领奖次数从这个版本开始记。", fontSize = 13.sp, color = P.sub)
        SectionTitle("铃铛奖章 · 累计签到")
        MCard(padding = 12.dp) { BadgeCell(Badges.bell, v["checkin"] ?: 0, Modifier.fillMaxWidth(), big = true) }
        SectionTitle("收藏徽章")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 3) {
            Badges.collection.forEach { d -> BadgeCell(d, v[d.key] ?: 0, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BadgeCell(d: Badges.Def, value: Int, modifier: Modifier, big: Boolean = false) {
    val lv = Badges.level(d, value)
    val next = Badges.toNext(d, value)
    Column(modifier.padding(end = 3.dp, bottom = 3.dp).mdTile(lv > 0, P.gold).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(badgeImg(d, lv)), d.name, Modifier.size(if (big) 96.dp else 72.dp)
            .graphicsLayer { alpha = if (lv > 0) 1f else .45f }, colorFilter = if (lv > 0) null else Gray)
        Gap(4.dp)
        Text(d.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        Row { repeat(4) { i -> Icon(Ic.Star, null, Modifier.size(12.dp), tint = if (i < lv) Gold else P.line) } }
        Text(if (next == null) "${d.what} $value${d.unit} · 满级" else "${d.what} $value${d.unit}，还差 $next${d.unit}",
            fontSize = 11.sp, color = P.sub, textAlign = TextAlign.Center)
    }
}

/** 解锁徽章：大徽章弹出 + 叮一声，点任意处收起，排队的一个个来 */
@Composable
fun BadgeUnlockLayer() {
    val u = BadgeFx.queue.firstOrNull() ?: return
    val ctx = LocalContext.current
    val scale = remember(u) { Animatable(.2f) }
    val spin = remember(u) { Animatable(-25f) }
    LaunchedEffect(u) {
        com.mengding.app.pet.Ding.ring(ctx)
        launch { spin.animateTo(0f, tween(600)) }
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(Modifier.fillMaxSize().background(Color(0xAA141432))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { BadgeFx.queue.removeAt(0) },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("解锁新徽章", fontSize = 16.sp, color = Color.White)
            Gap(14.dp)
            Image(painterResource(badgeImg(u.def, u.level)), u.def.name, Modifier.size(200.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = spin.value })
            Gap(12.dp)
            Text("${u.def.name} · 第 ${u.level} 级", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(Modifier.padding(top = 6.dp)) { repeat(4) { i -> Icon(Ic.Star, null, Modifier.size(20.dp), tint = if (i < u.level) Gold else Color(0x55FFFFFF)) } }
            Gap(8.dp)
            Text("${u.def.what} ${u.def.levels[u.level - 1]}${u.def.unit}，萌点 +${u.pts}", fontSize = 15.sp, color = Color(0xFFFFE9A8))
            Gap(18.dp)
            Text("点任意处收起", fontSize = 12.sp, color = Color(0xCCFFFFFF))
        }
    }
}
