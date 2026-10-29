package com.mengding.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import kotlin.math.roundToInt

/**
 * 新手轻提示（需求 F）：第一次进某个页面时，宠物从右下角探出头，小气泡 + 发光圈依次指 1–4 处，点任意处下一条，
 * 右上「跳过全部」。每个页面只出一次（记在 settings.tipsSeen）；「我的 → 重新显示新手提示」清空后再出。
 * 只盖一层在最上面，不改页面本身的功能。
 */
object Coach {
    /** 一条提示：target 是 [coachTarget] 登记的位置，null = 不指具体地方（气泡放中间） */
    data class Tip(val target: String?, val text: String)

    /** 页面上被指的东西现在在屏幕上的位置（根坐标） */
    val rects = mutableStateMapOf<String, Rect>()
    var page by mutableStateOf<String?>(null)
    var tips by mutableStateOf<List<Tip>>(emptyList())

    fun seen(page: String) = page in Store.cfg.settings.tipsSeen

    fun markSeen(vararg pages: String) =
        Store.update { it.copy(settings = it.settings.copy(tipsSeen = (it.settings.tipsSeen + pages).distinct())) }

    /** 所有页面的提示都不再出（「跳过全部」） */
    val ALL = listOf("home", "notes", "shop", "games", "room", "floating")
}

/** 登记一个可以被提示指着的位置 */
fun Modifier.coachTarget(key: String) = onGloballyPositioned { Coach.rects[key] = it.boundsInRoot() }

/** 页面里调：这个页面没看过提示就排上（等 0.6 秒，页面排好版、目标位置都登记了再出） */
@Composable
fun CoachFor(page: String, tips: List<Coach.Tip>) {
    LaunchedEffect(page) {
        kotlinx.coroutines.delay(600)
        if (!Coach.seen(page) && Coach.page == null && tips.isNotEmpty()) { Coach.tips = tips; Coach.page = page }
    }
}

/** App 根部画的那一层 */
@Composable
fun CoachLayer() {
    val page = Coach.page ?: return
    // 有解锁徽章的大动画在放，等它收起再出提示，两层不叠在一起
    if (BadgeFx.queue.isNotEmpty()) return
    val tips = Coach.tips
    var i by remember(page) { mutableIntStateOf(0) }
    val tip = tips.getOrNull(i)
    if (tip == null) { LaunchedEffect(page) { Coach.markSeen(page); Coach.page = null }; return }
    val target = tip.target?.let { Coach.rects[it] }
    val pulse by rememberInfiniteTransition(label = "coach").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val peek = remember(page) { Animatable(1f) }
    LaunchedEffect(page) { peek.animateTo(0f, tween(500)) }
    fun next() { if (i + 1 >= tips.size) { Coach.markSeen(page); Coach.page = null } else i++ }
    BoxWithConstraints(Modifier.fillMaxSize()
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() }) {
        val dens = LocalDensity.current
        // 轻轻压暗，目标处挖亮一圈发光
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0x40141432))
            target?.let { r ->
                val pad = 6.dp.toPx() + pulse * 4.dp.toPx()
                val tl = Offset(r.left - pad, r.top - pad); val sz = Size(r.width + pad * 2, r.height + pad * 2)
                drawRoundRect(Color(0x33FFF3B0), tl, sz, CornerRadius(18.dp.toPx()))
                drawRoundRect(Color(0xFFFFD45C).copy(alpha = .55f + .45f * pulse), tl, sz, CornerRadius(18.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
        }
        // 小气泡：目标下面放得下就放下面，否则放上面；没有目标放中间
        val maxW = 260.dp
        val bubbleY = with(dens) {
            when {
                target == null -> (maxHeight / 2 - 40.dp)
                target.bottom.toDp() + 110.dp < maxHeight -> target.bottom.toDp() + 16.dp
                else -> (target.top.toDp() - 110.dp).coerceAtLeast(60.dp)
            }
        }
        val bubbleX = with(dens) {
            val cx = target?.center?.x?.toDp() ?: (maxWidth / 2)
            (cx - maxW / 2).coerceIn(12.dp, maxWidth - maxW - 12.dp)
        }
        Column(Modifier.offset(bubbleX, bubbleY).widthIn(max = maxW).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFBF0)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(tip.text, fontSize = 15.sp, color = P.ink)
            Text("${i + 1} / ${tips.size} · 点任意处继续", Modifier.padding(top = 4.dp), fontSize = 11.sp, color = P.sub)
        }
        Text("跳过全部", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6FFFFFF)).clickable { Coach.markSeen(*Coach.ALL.toTypedArray()); Coach.page = null }
            .padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        // 宠物从右下角探出头
        val pet = PetSim.outing(Store.cfg) ?: Store.cfg.pets.firstOrNull()
        if (pet != null) Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
            .offset { IntOffset((24.dp.toPx() + peek.value * 120.dp.toPx()).roundToInt(), (18.dp.toPx() * (1 - pulse * .3f)).roundToInt()) }
            .graphicsLayer { rotationZ = -12f }) {
            PetSprite(pet.species, "energetic", 110.dp, outfits = pet.worn)
        }
    }
}
