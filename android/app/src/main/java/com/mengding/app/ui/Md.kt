package com.mengding.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mengding.app.logic.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 萌叮自绘控件（替换安卓 / Material 原生的弹窗、提示、按钮、开关、输入框、日期时间选择）。
 * 4 套画风，「我的 → 界面主题 → 界面风格」切换，设置字段 settings.uiStyle：
 *   奶油气泡 cream：大圆角、奶油到樱花粉的渐变、薄荷描边、柔和投影，弹窗下面带两颗小气泡尾巴
 *   漫画贴纸 comic：粗咖啡色描边、实心硬投影、标题像斜贴的黄色贴纸，右上角爆炸星
 *   清新线条 line：大留白、1px 细线、几乎没阴影，弹窗顶上探出一对线条猫耳朵
 *   像素游戏 pixel：RPG 对话框，深浅两层木框 + 羊皮纸底（深色模式是夜蓝底），切角、硬投影、▶ 光标
 * 面板、按钮、开关这些要随内容伸缩的部件用代码画（照 美术/UI重构/<风格>/X_图1~4 素材表取色和造型，任意尺寸都清晰）；
 * 素材表里裁出来的插画（空状态小猫、提示条宠物头像）放在 res/drawable-nodpi/md_<风格>_*.webp，见 MdArt。
 * 同包里的 AlertDialog / TextButton / OutlinedTextField 同名函数让旧代码去掉 material3 的 import 就自动换成这套。
 */
enum class MdKind { CREAM, COMIC, LINE, PIXEL }

data class MdStyle(val key: String, val name: String, val desc: String, val kind: MdKind)

object MdStyles {
    val cream = MdStyle("cream", "奶油气泡", "软软的大圆角和奶油渐变，弹窗像宠物说话的气泡", MdKind.CREAM)
    val all = listOf(
        cream,
        MdStyle("comic", "漫画贴纸", "粗描边、硬投影、黄色贴纸标题，活泼的卡通漫画感", MdKind.COMIC),
        MdStyle("line", "清新线条", "大留白、细线条、几乎没有阴影，弹窗顶上探出猫耳朵", MdKind.LINE),
        MdStyle("pixel", "像素游戏", "像 RPG 游戏的对话框：木框、羊皮纸、像素切角", MdKind.PIXEL),
    )
    fun of(key: String) = all.firstOrNull { it.key == key } ?: cream
}

val LocalMdStyle = staticCompositionLocalOf { MdStyles.cream }
val Md: MdStyle @Composable get() = LocalMdStyle.current

private val ComicInk = Color(0xFF3B2A2A)
private val ComicYellow = Color(0xFFFFE27A)
private val WoodDark = Color(0xFF5B3A26)
private val WoodLight = Color(0xFFC08A5B)
private val Parchment = Color(0xFFF3E2C3)
private val NightBlue = Color(0xFF2E3A5C)
private val PixelGold = Color(0xFFF4C24B)

@Composable
fun mdShape(r: Dp = 22.dp): Shape = when (Md.kind) {
    MdKind.CREAM -> RoundedCornerShape(r + 4.dp)
    MdKind.COMIC -> RoundedCornerShape(r * 0.65f)
    MdKind.LINE -> RoundedCornerShape(r * 0.55f)
    MdKind.PIXEL -> CutCornerShape(5.dp)
}

/** 面板底色（照素材表取色）：像素风羊皮纸 / 夜蓝，漫画奶油底，线条米白；深色模式用卡片色 */
@Composable
fun mdSurface(): Color {
    val dark = isSystemInDarkTheme()
    return when (Md.kind) {
        MdKind.PIXEL -> if (dark) NightBlue else Parchment
        MdKind.COMIC -> if (dark) P.card else Color(0xFFFFF7E6)
        MdKind.LINE -> if (dark) P.card else Color(0xFFFBFAF7)
        MdKind.CREAM -> P.card
    }
}

/** 奶油气泡的招牌薄荷描边（A 素材表 #8FD3C1） */
private val CreamMint = Color(0xFF8FD3C1)

@Composable
fun mdOutline(): Color = when (Md.kind) {
    MdKind.CREAM -> P.mint.copy(alpha = 0.5f)
    MdKind.COMIC -> if (isSystemInDarkTheme()) Color(0xFFEDE3D6) else ComicInk
    MdKind.LINE -> P.line
    MdKind.PIXEL -> WoodDark
}

/** 实心硬投影（漫画、像素用） */
fun Modifier.hardShadow(shape: Shape, color: Color, dx: Dp, dy: Dp): Modifier = drawBehind {
    val o = shape.createOutline(size, layoutDirection, this)
    translate(dx.toPx(), dy.toPx()) { drawOutline(o, color) }
}

/** 按当前画风画一块面板（底色、描边、阴影）。strong = 弹窗这类浮在最上面的 */
@Composable
fun Modifier.mdPanel(shape: Shape = mdShape(), fill: Color = mdSurface(), strong: Boolean = false): Modifier {
    val p = P
    val dark = isSystemInDarkTheme()
    return when (Md.kind) {
        MdKind.CREAM -> {
            val bg: Brush = if (fill == p.card && !dark) Brush.verticalGradient(listOf(Color(0xFFFFF8EC), Color(0xFFFFF0F4))) else SolidColor(fill)
            this.shadow(if (strong) 16.dp else 3.dp, shape, ambientColor = p.pink, spotColor = p.pink)
                .clip(shape).background(bg)
                .border(if (strong) 2.5.dp else 1.5.dp, if (dark) p.mint.copy(alpha = 0.5f) else CreamMint.copy(alpha = if (strong) 1f else 0.7f), shape)
        }
        MdKind.COMIC -> {
            val ink = mdOutline()
            this.hardShadow(shape, ink, if (strong) 5.dp else 3.dp, if (strong) 5.dp else 3.dp)
                .clip(shape).background(fill).border(if (strong) 3.dp else 2.dp, ink, shape)
        }
        MdKind.LINE -> this.clip(shape).background(fill)
            .border(1.dp, if (strong) Color(0xFF71BCA8) else (if (dark) p.line else Color(0xFF71BCA8).copy(alpha = 0.45f)), shape)
        MdKind.PIXEL -> this.hardShadow(shape, Color.Black.copy(alpha = 0.35f), 4.dp, 4.dp)
            .clip(shape).background(fill).border(3.dp, WoodDark, shape)
            .padding(3.dp).border(2.dp, WoodLight, CutCornerShape(3.dp))
    }
}

// ---------------- 按钮 ----------------

enum class MdRole { PLAIN, SECONDARY, PRIMARY, SOLID }

/** 弹窗把确认按钮设成 PRIMARY、取消按钮设成 SECONDARY，里面的 TextButton 自动换样子 */
val LocalMdRole = compositionLocalOf { MdRole.PLAIN }

@Composable
fun MdButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, role: MdRole = MdRole.SOLID,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val k = Md.kind
    val p = P
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = when (k) {
        MdKind.CREAM -> RoundedCornerShape(50)
        MdKind.COMIC -> RoundedCornerShape(12.dp)
        MdKind.LINE -> RoundedCornerShape(10.dp)
        MdKind.PIXEL -> CutCornerShape(3.dp)
    }
    val ink = mdOutline()
    val bg = when (role) {
        MdRole.PLAIN -> Color.Transparent
        MdRole.SECONDARY -> if (k == MdKind.LINE || k == MdKind.PIXEL) Color.Transparent else p.line.copy(alpha = 0.6f)
        MdRole.PRIMARY -> if (k == MdKind.COMIC) ComicYellow else p.mintSoft
        MdRole.SOLID -> if (k == MdKind.LINE) Color.Transparent else p.mint
    }
    val fg = when (role) {
        MdRole.PLAIN -> p.mintDeep
        MdRole.SECONDARY -> if (k == MdKind.PIXEL && !isSystemInDarkTheme()) WoodDark else p.sub
        MdRole.PRIMARY -> if (k == MdKind.COMIC) ComicInk else p.mintDeep
        MdRole.SOLID -> if (k == MdKind.LINE) p.mintDeep else Color.White
    }
    val (bw, bc) = when {
        role == MdRole.PLAIN -> 0.dp to Color.Transparent
        k == MdKind.COMIC -> 2.dp to ink
        k == MdKind.PIXEL -> 2.dp to (if (role == MdRole.SECONDARY) WoodLight else WoodDark)
        k == MdKind.LINE -> 1.dp to (if (role == MdRole.SECONDARY) p.line else p.mint)
        else -> 0.dp to Color.Transparent
    }
    val hard = role != MdRole.PLAIN && role != MdRole.SECONDARY && (k == MdKind.COMIC || k == MdKind.PIXEL)
    val gloss = k == MdKind.CREAM && (role == MdRole.SOLID || role == MdRole.PRIMARY)
    Row(
        modifier
            .graphicsLayer { if (pressed && k == MdKind.CREAM) { scaleX = 0.95f; scaleY = 0.95f } }
            .offset { if (pressed && hard) IntOffset(2.dp.roundToPx(), 2.dp.roundToPx()) else IntOffset.Zero }
            .then(if (hard && !pressed) Modifier.hardShadow(shape, if (k == MdKind.PIXEL) Color.Black.copy(alpha = 0.35f) else ink, 2.dp, 2.dp) else Modifier)
            .clip(shape).background(bg)
            .then(if (bw > 0.dp) Modifier.border(bw, bc, shape) else Modifier)
            .then(if (gloss) Modifier.drawWithContent {
                drawContent()
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(size.width * 0.1f, size.height * 0.14f),
                    Size(size.width * 0.28f, size.height * 0.2f), CornerRadius(size.height))
            } else Modifier)
            .clickable(src, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = if (role == MdRole.PLAIN) 36.dp else 40.dp)
            .padding(if (role == MdRole.PLAIN) PaddingValues(horizontal = 10.dp, vertical = 6.dp) else contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides LocalTextStyle.current.copy(color = Color.Unspecified, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        ) { content() }
    }
}

/** 悬浮按钮：奶油是带光泽的圆、漫画是粗描边加硬投影、线条是描边圆加猫耳、像素是切角金币块 */
@Composable
fun MdFab(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val k = Md.kind
    val p = P
    val ink = mdOutline()
    val shape = if (k == MdKind.PIXEL) CutCornerShape(6.dp) else CircleShape
    val fill = when (k) { MdKind.LINE -> mdSurface(); MdKind.PIXEL -> PixelGold; else -> p.mint }
    val tint = when (k) { MdKind.LINE -> p.mint; MdKind.PIXEL -> WoodDark; else -> Color.White }
    Box(
        modifier.size(58.dp)
            .drawWithContent {
                drawContent()
                if (k == MdKind.LINE) {
                    // 顶上一对线条猫耳
                    val w = 10.dp.toPx(); val h = 8.dp.toPx(); val cx = size.width / 2
                    listOf(cx - 16.dp.toPx(), cx + 6.dp.toPx()).forEach { x ->
                        val ear = Path().apply { moveTo(x, h * 0.9f); lineTo(x + w / 2, -h * 0.2f); lineTo(x + w, h * 0.9f) }
                        drawPath(ear, p.mint, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            .then(when (k) {
                MdKind.CREAM -> Modifier.shadow(8.dp, shape, ambientColor = p.pink, spotColor = p.pink)
                MdKind.COMIC -> Modifier.hardShadow(shape, ink, 3.dp, 3.dp)
                MdKind.PIXEL -> Modifier.hardShadow(shape, Color.Black.copy(alpha = 0.35f), 3.dp, 3.dp)
                else -> Modifier
            })
            .clip(shape).background(fill)
            .then(when (k) {
                MdKind.COMIC -> Modifier.border(2.5.dp, ink, shape)
                MdKind.LINE -> Modifier.border(1.5.dp, p.mint, shape)
                MdKind.PIXEL -> Modifier.border(3.dp, WoodDark, shape)
                else -> Modifier
            })
            .then(if (k == MdKind.CREAM) Modifier.drawWithContent {
                drawContent()
                drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(size.width * 0.22f, size.height * 0.14f),
                    Size(size.width * 0.3f, size.height * 0.14f), CornerRadius(size.height))
            } else Modifier)
            .clickable(role = Role.Button, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, desc, Modifier.size(28.dp), tint = tint) }
}

/** 替换 Material 的 TextButton：在弹窗里会按位置变成主按钮 / 次按钮 */
@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    MdButton(onClick, modifier, enabled, LocalMdRole.current, content = content)

// ---------------- 弹窗 ----------------

/** 替换 Material 的 AlertDialog（参数一样） */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) = MdDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text)

@Composable
fun MdDialog(
    onDismissRequest: () -> Unit,
    confirmButton: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissOnOutside: Boolean = true,
) {
    val k = Md.kind
    val p = P
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissOnOutside)) {
        // 背后压暗（线条风浅一点）
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.setDimAmount(if (k == MdKind.LINE) 0.28f else 0.45f)
            }
        }
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            appear.animateTo(1f, when (k) {
                MdKind.CREAM -> spring(dampingRatio = 0.55f, stiffness = 520f)
                MdKind.PIXEL -> tween(180, easing = { t -> (t * 4).toInt() / 4f })
                else -> tween(200)
            })
        }
        val surface = mdSurface()
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 30.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(max = maxH)
                    .graphicsLayer {
                        val a = appear.value
                        alpha = a.coerceIn(0f, 1f)
                        val s = 0.86f + 0.14f * a
                        scaleX = s; scaleY = s
                        if (k == MdKind.COMIC) rotationZ = (1f - a) * -5f
                        if (k == MdKind.LINE) translationY = (1f - a) * 20.dp.toPx()
                    }
                    .drawBehind { if (k == MdKind.CREAM) bubbleTail(p.mint, Color(0xFFFFF1F5)) }
                    .drawWithContent {
                        drawContent()
                        when (k) {
                            MdKind.COMIC -> starBurst(Offset(size.width - 8.dp.toPx(), 6.dp.toPx()), 15.dp.toPx(), ComicYellow, ComicInk)
                            MdKind.LINE -> catEars(p.mint, surface, p.danger, PixelGold)
                            MdKind.PIXEL -> rivets(PixelGold)
                            else -> {}
                        }
                    }
                    .mdPanel(strong = true)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides p.ink) {
                    icon?.let { Box(Modifier.align(Alignment.CenterHorizontally)) { it() }; Spacer(Modifier.height(10.dp)) }
                    title?.let { MdTitle(it); Spacer(Modifier.height(12.dp)) }
                    text?.let {
                        Box(Modifier.weight(1f, fill = false)) {
                            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = p.ink)) { it() }
                        }
                    }
                    if (confirmButton != null || dismissButton != null) {
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically) {
                            dismissButton?.let { CompositionLocalProvider(LocalMdRole provides MdRole.SECONDARY) { it() } }
                            confirmButton?.let { CompositionLocalProvider(LocalMdRole provides MdRole.PRIMARY) { it() } }
                        }
                    }
                }
            }
            // 弹窗是单独的窗口，盖住了页面上的提示，这里再放一个
            MdToastHost()
        }
    }
}

@Composable
private fun MdTitle(content: @Composable () -> Unit) {
    val p = P
    val base = MaterialTheme.typography.titleMedium
    when (Md.kind) {
        MdKind.CREAM -> Row(verticalAlignment = Alignment.CenterVertically) {
            // 小铃铛：红项圈上的金铃
            Box(Modifier.size(10.dp).clip(CircleShape).background(PixelGold).border(1.5.dp, Color(0xFFE5606F), CircleShape))
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(LocalTextStyle provides base.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.ink)) { content() }
        }
        MdKind.COMIC -> Box(
            Modifier.graphicsLayer { rotationZ = -2f }.hardShadow(RoundedCornerShape(8.dp), ComicInk, 2.dp, 2.dp)
                .clip(RoundedCornerShape(8.dp)).background(ComicYellow).border(2.dp, ComicInk, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) { CompositionLocalProvider(LocalTextStyle provides base.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ComicInk)) { content() } }
        MdKind.LINE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(p.mint))
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(LocalTextStyle provides base.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = p.ink)) { content() }
        }
        MdKind.PIXEL -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("▶", color = if (isSystemInDarkTheme()) PixelGold else WoodDark, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            CompositionLocalProvider(LocalTextStyle provides base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.ink, letterSpacing = 1.sp)) { content() }
        }
    }
}

private fun DrawScope.bubbleTail(line: Color, fill: Color) {
    val x = 40.dp.toPx(); val y = size.height
    listOf(Triple(x, y + 12.dp.toPx(), 8.dp.toPx()), Triple(x - 14.dp.toPx(), y + 27.dp.toPx(), 5.dp.toPx())).forEach { (cx, cy, r) ->
        drawCircle(fill, r, Offset(cx, cy))
        drawCircle(line.copy(alpha = 0.6f), r, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
    }
}

private fun DrawScope.starBurst(c: Offset, r: Float, fill: Color, ink: Color) {
    val path = Path()
    for (i in 0 until 16) {
        val a = Math.PI * 2 * i / 16 - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.55f
        val pt = Offset(c.x + (rr * Math.cos(a)).toFloat(), c.y + (rr * Math.sin(a)).toFloat())
        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()
    drawPath(path, fill); drawPath(path, ink, style = Stroke(2.dp.toPx()))
}

/** 线条风：顶边探出一对猫耳朵，耳朵中间挂红项圈金铃 */
private fun DrawScope.catEars(line: Color, fill: Color, collar: Color, bell: Color) {
    val w = 16.dp.toPx(); val h = 12.dp.toPx(); val x0 = 30.dp.toPx(); val gap = 30.dp.toPx()
    listOf(x0, x0 + gap).forEach { x ->
        val ear = Path().apply { moveTo(x, 1f); lineTo(x + w / 2, -h); lineTo(x + w, 1f); close() }
        drawPath(ear, fill)
        val edge = Path().apply { moveTo(x, 0f); lineTo(x + w / 2, -h); lineTo(x + w, 0f) }
        drawPath(edge, line, style = Stroke(1.2.dp.toPx()))
    }
    val bx = x0 + (gap + w) / 2
    drawCircle(collar, 2.5.dp.toPx(), Offset(bx, 0f))
    drawCircle(bell, 3.5.dp.toPx(), Offset(bx, 5.dp.toPx()))
}

private fun DrawScope.rivets(gold: Color) {
    val s = 4.dp.toPx(); val m = 7.dp.toPx()
    listOf(Offset(m, m), Offset(size.width - m - s, m), Offset(m, size.height - m - s), Offset(size.width - m - s, size.height - m - s))
        .forEach { drawRect(gold, it, Size(s, s)) }
}

/** 确认框：标题 + 一句说明 + 取消 / 确认 */
@Composable
fun MdConfirm(title: String, message: String?, confirm: String, danger: Boolean = false, cancel: String = "取消",
              onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MdDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { if (message != null) Text(message) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = if (danger) P.danger else Color.Unspecified) } },
        dismissButton = { TextButton(onDismiss) { Text(cancel) } },
    )
}

/** 进度框（插入视频这类耗时操作），progress < 0 表示不知道总量 */
@Composable
fun MdProgress(title: String, progress: Float, hint: String? = null, onCancel: (() -> Unit)? = null) {
    MdDialog(
        onDismissRequest = {}, dismissOnOutside = false,
        title = { Text(title) },
        text = {
            Column {
                if (Md.kind == MdKind.PIXEL) { MdPixelCatRunner(Modifier.align(Alignment.CenterHorizontally).size(76.dp, 46.dp)); Spacer(Modifier.height(6.dp)) }
                MdProgressBar(progress)
                Spacer(Modifier.height(8.dp))
                Text(if (progress >= 0) "${(progress * 100).toInt()}%" else "处理中…", fontSize = 13.sp, color = P.sub)
                hint?.let { Text(it, fontSize = 12.sp, color = P.sub) }
            }
        },
        confirmButton = null,
        dismissButton = { if (onCancel != null) TextButton(onCancel) { Text("取消") } },
    )
}

@Composable
fun MdProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val k = Md.kind
    val p = P
    val shape = if (k == MdKind.PIXEL) CutCornerShape(2.dp) else RoundedCornerShape(50)
    // 不知道总量时来回跑的一小段
    val t by produceTick(progress < 0)
    Box(modifier.fillMaxWidth().height(if (k == MdKind.LINE) 4.dp else 12.dp).clip(shape)
        .background(if (k == MdKind.PIXEL) WoodLight.copy(alpha = 0.35f) else p.line)
        .then(if (k == MdKind.COMIC) Modifier.border(2.dp, mdOutline(), shape) else Modifier)) {
        val f = if (progress >= 0) progress.coerceIn(0f, 1f) else 0.3f
        val start = if (progress >= 0) 0f else t * 0.7f
        Box(Modifier.fillMaxWidth(start + f).height(if (k == MdKind.LINE) 4.dp else 12.dp)
            .drawBehind {
                val x0 = size.width * (start / (start + f).coerceAtLeast(0.001f))
                if (k == MdKind.PIXEL) {
                    val cell = 6.dp.toPx(); var x = x0
                    while (x < size.width) { drawRect(p.mint, Offset(x, 0f), Size((cell - 2f).coerceAtMost(size.width - x), size.height)); x += cell }
                } else drawRoundRect(p.mint, Offset(x0, 0f), Size(size.width - x0, size.height), CornerRadius(size.height / 2))
            })
    }
}

@Composable
private fun produceTick(on: Boolean): androidx.compose.runtime.State<Float> {
    val s = remember { mutableStateOf(0f) }
    LaunchedEffect(on) {
        var t = 0f
        while (on) { delay(40); t = (t + 0.02f) % 1f; s.value = if (t < 0.5f) t * 2 else (1 - t) * 2 }
    }
    return s
}

// ---------------- 素材表裁出的插画 ----------------

/** 每套风格的插画：空状态、提示条里的宠物头像（美术/UI重构/切图，由素材表裁出） */
object MdArt {
    fun empty(k: MdKind) = when (k) {
        MdKind.CREAM -> com.mengding.app.R.drawable.md_cream_empty
        MdKind.COMIC -> com.mengding.app.R.drawable.md_comic_empty
        MdKind.LINE -> com.mengding.app.R.drawable.md_line_empty
        MdKind.PIXEL -> com.mengding.app.R.drawable.md_pixel_empty
    }
    fun face(k: MdKind) = when (k) {
        MdKind.CREAM -> com.mengding.app.R.drawable.md_cream_face
        MdKind.COMIC -> com.mengding.app.R.drawable.md_comic_face
        MdKind.LINE -> com.mengding.app.R.drawable.md_line_face
        MdKind.PIXEL -> com.mengding.app.R.drawable.md_pixel_face
    }
}

@Composable
private fun MdArtImage(id: Int, modifier: Modifier) {
    val bmp = androidx.compose.ui.graphics.ImageBitmap.imageResource(id)
    androidx.compose.foundation.Image(bmp, null, modifier,
        filterQuality = if (Md.kind == MdKind.PIXEL) androidx.compose.ui.graphics.FilterQuality.None else androidx.compose.ui.graphics.FilterQuality.Medium)
}

/** 空状态：当前风格的小猫插画 + 一句话 */
@Composable
fun MdEmpty(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MdArtImage(MdArt.empty(Md.kind), Modifier.width(170.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = P.sub, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

// ---------------- 轻提示（替换 Toast） ----------------

object MdToasts {
    data class Msg(val id: Long, val text: String)
    val current = MutableStateFlow<Msg?>(null)
    fun show(text: String) { if (text.isNotBlank()) current.value = Msg(System.nanoTime(), text) }
}

@Composable
fun MdToastHost() {
    val msg by MdToasts.current.collectAsState()
    var last by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(msg) {
        val m = msg ?: run { visible = false; return@LaunchedEffect }
        last = m.text; visible = true
        delay(2300)
        if (MdToasts.current.value == m) MdToasts.current.value = null
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f), exit = fadeOut(tween(200))) {
            MdToastBubble(last)
        }
    }
}

@Composable
fun MdToastBubble(text: String) {
    val k = Md.kind
    val p = P
    val dark = isSystemInDarkTheme()
    val fill = when (k) { MdKind.PIXEL -> NightBlue; MdKind.LINE -> if (dark) p.card else Color(0xFF4A4A4A); else -> mdSurface() }
    val fg = when (k) { MdKind.PIXEL -> Color(0xFFF7EBD3); MdKind.LINE -> if (dark) p.ink else Color.White; else -> p.ink }
    val shape = when (k) { MdKind.CREAM -> RoundedCornerShape(22.dp); MdKind.COMIC -> RoundedCornerShape(14.dp); MdKind.LINE -> RoundedCornerShape(50); MdKind.PIXEL -> CutCornerShape(4.dp) }
    Box(
        Modifier.graphicsLayer { if (k == MdKind.COMIC) rotationZ = -1.5f }
            .drawBehind {
                // 气泡、漫画对白框带一个朝下的小尾巴
                if (k == MdKind.CREAM || k == MdKind.COMIC) {
                    val cx = size.width / 2; val y = size.height - 2f; val w = 9.dp.toPx(); val h = 9.dp.toPx()
                    val tail = Path().apply { moveTo(cx - w, y); lineTo(cx + w * 0.2f, y + h); lineTo(cx + w * 0.4f, y); close() }
                    drawPath(tail, fill)
                    drawPath(Path().apply { moveTo(cx - w, y); lineTo(cx + w * 0.2f, y + h); lineTo(cx + w * 0.4f, y) },
                        if (k == MdKind.COMIC) mdOutlineStatic(dark) else p.mint.copy(alpha = 0.5f), style = Stroke((if (k == MdKind.COMIC) 2.dp else 1.5.dp).toPx()))
                }
            }
            .then(if (k == MdKind.LINE) Modifier.clip(shape).background(fill) else if (k == MdKind.PIXEL)
                Modifier.hardShadow(shape, Color.Black.copy(alpha = 0.35f), 3.dp, 3.dp).clip(shape).background(fill).border(2.dp, PixelGold, shape)
            else Modifier.mdPanel(shape, fill, strong = true))
            .padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MdArtImage(MdArt.face(k), Modifier.size(28.dp).then(if (k == MdKind.PIXEL) Modifier else Modifier.clip(CircleShape)))
            Spacer(Modifier.width(8.dp))
            Text(text, color = fg, fontSize = 14.sp, textAlign = TextAlign.Start,
                fontWeight = if (k == MdKind.COMIC) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

private fun mdOutlineStatic(dark: Boolean) = if (dark) Color(0xFFEDE3D6) else ComicInk

// ---------------- 开关 ----------------

@Composable
fun MdSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val k = Md.kind
    val p = P
    val trackW = 46.dp; val trackH = 26.dp; val thumb = if (k == MdKind.LINE) 18.dp else 20.dp
    val pad = (trackH - thumb) / 2
    val x by animateDpAsState(if (checked) trackW - thumb - pad else pad,
        if (k == MdKind.PIXEL) snap() else spring(dampingRatio = if (k == MdKind.CREAM) 0.6f else 0.9f), label = "sw")
    val shape = if (k == MdKind.PIXEL) CutCornerShape(3.dp) else RoundedCornerShape(50)
    val thumbShape = if (k == MdKind.PIXEL) CutCornerShape(2.dp) else CircleShape
    val ink = mdOutline()
    val track = when {
        k == MdKind.LINE -> if (checked) p.mint.copy(alpha = 0.18f) else Color.Transparent
        checked -> p.mint
        k == MdKind.PIXEL -> WoodLight.copy(alpha = 0.4f)
        else -> p.line
    }
    Box(
        Modifier.size(trackW, trackH).alpha(if (enabled) 1f else 0.45f)
            .then(if (k == MdKind.COMIC) Modifier.hardShadow(shape, ink, 2.dp, 2.dp) else Modifier)
            .clip(shape).background(track)
            .then(when (k) {
                MdKind.COMIC -> Modifier.border(2.dp, ink, shape)
                MdKind.LINE -> Modifier.border(1.dp, if (checked) p.mint else p.sub.copy(alpha = 0.5f), shape)
                MdKind.PIXEL -> Modifier.border(2.dp, WoodDark, shape)
                else -> Modifier
            })
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) },
    ) {
        Box(
            Modifier.offset(x = x, y = pad).size(thumb)
                .then(if (k == MdKind.CREAM) Modifier.shadow(2.dp, thumbShape) else Modifier)
                .clip(thumbShape)
                .background(when (k) { MdKind.LINE -> if (checked) p.mint else p.sub.copy(alpha = 0.6f); MdKind.PIXEL -> if (checked) Parchment else WoodDark; else -> Color.White })
                .then(if (k == MdKind.COMIC) Modifier.border(2.dp, ink, thumbShape) else Modifier),
        )
    }
}

// ---------------- 输入框（替换 OutlinedTextField，常用参数一样） ----------------

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val k = Md.kind
    val p = P
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = when (k) { MdKind.CREAM -> RoundedCornerShape(16.dp); MdKind.COMIC -> RoundedCornerShape(10.dp); MdKind.LINE -> RoundedCornerShape(0.dp); MdKind.PIXEL -> CutCornerShape(3.dp) }
    val ink = mdOutline()
    Column(modifier.defaultMinSize(minWidth = 240.dp)) {
        label?.let {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 12.sp, color = if (focused) p.mintDeep else p.sub)) { it() }
            Spacer(Modifier.height(4.dp))
        }
        val fieldBg = when (k) { MdKind.CREAM -> p.mintSoft.copy(alpha = 0.45f); MdKind.LINE -> Color.Transparent; MdKind.PIXEL -> if (isSystemInDarkTheme()) NightBlue else Color(0xFFFBF1DC); else -> p.card }
        Box(
            Modifier.fillMaxWidth().clip(shape).background(fieldBg)
                .then(when (k) {
                    MdKind.CREAM -> Modifier.border(if (focused) 2.dp else 1.dp, if (focused) p.mint else p.mint.copy(alpha = 0.3f), shape)
                    MdKind.COMIC -> Modifier.border(2.dp, if (focused) p.mintDeep else ink, shape)
                    MdKind.LINE -> Modifier.drawBehind {
                        val sw = (if (focused) 2.dp else 1.dp).toPx()
                        drawLine(if (focused) p.mint else p.line, Offset(0f, size.height - sw / 2), Offset(size.width, size.height - sw / 2), sw)
                    }
                    MdKind.PIXEL -> Modifier.border(2.dp, if (focused) PixelGold else WoodDark, shape)
                })
                .padding(horizontal = if (k == MdKind.LINE) 2.dp else 12.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty()) placeholder?.let {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 15.sp, color = p.sub.copy(alpha = 0.7f))) { it() }
            }
            BasicTextField(
                value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled,
                textStyle = TextStyle(fontSize = 15.sp, color = p.ink, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                singleLine = singleLine, maxLines = maxLines, visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions, interactionSource = src, cursorBrush = SolidColor(p.mint),
            )
        }
    }
}

// ---------------- 时间、日期选择（替换 Material 的 TimePicker / DatePicker） ----------------

/** 滚轮：上下滑动选数字，停下会对齐到中间一格 */
@Composable
fun MdWheel(count: Int, value: Int, label: (Int) -> String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val itemH = 42.dp
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, count - 1))
    val sel by remember { derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, count - 1) } }
    LaunchedEffect(sel) { onChange(sel) }
    val k = Md.kind
    val p = P
    Box(modifier.width(72.dp).height(itemH * 5), contentAlignment = Alignment.Center) {
        // 中间选中带
        Box(Modifier.fillMaxWidth().height(itemH).then(when (k) {
            MdKind.LINE -> Modifier.drawBehind {
                drawLine(p.mint, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(p.mint, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            MdKind.PIXEL -> Modifier.clip(CutCornerShape(3.dp)).background(PixelGold.copy(alpha = 0.3f)).border(2.dp, WoodDark, CutCornerShape(3.dp))
            MdKind.COMIC -> Modifier.clip(RoundedCornerShape(10.dp)).background(ComicYellow).border(2.dp, mdOutline(), RoundedCornerShape(10.dp))
            MdKind.CREAM -> Modifier.clip(RoundedCornerShape(50)).background(p.mintSoft)
        }))
        LazyColumn(Modifier.fillMaxSize(), state = state, flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = itemH * 2)) {
            items(count) { i ->
                val on = i == sel
                Box(Modifier.fillMaxWidth().height(itemH), contentAlignment = Alignment.Center) {
                    Text(label(i), fontSize = if (on) 22.sp else 17.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) (if (k == MdKind.COMIC) ComicInk else p.ink) else p.sub.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** 选时间的对话框：时、分两个滚轮 */
@Composable
fun TimeDialog(initial: String, title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = Time.parse(initial)
    var h by remember { mutableStateOf(t.hour) }
    var m by remember { mutableStateOf(t.minute) }
    MdDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MdWheel(24, t.hour, { "%02d".format(it) }) { h = it }
                Text(":", Modifier.padding(horizontal = 10.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = P.ink)
                MdWheel(60, t.minute, { "%02d".format(it) }) { m = it }
            }
        },
        confirmButton = { TextButton({ onPick(Time.fmt(LocalTime.of(h, m))) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

/**
 * 选日期的对话框：月历格子（« » 换年，‹ › 换月），返回 yyyy-MM-dd。
 * extra：左下角额外的按钮，比如生日的「不填」、结束日期的「不结束」
 */
@Composable
fun DateDialog(
    onDismiss: () -> Unit,
    initial: String? = null,
    title: String = "选择日期",
    extra: Pair<String, () -> Unit>? = null,
    onPick: (String) -> Unit,
) {
    val start = remember { initial?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Time.today() }
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    var sel by remember { mutableStateOf(start) }
    val k = Md.kind
    val p = P
    val today = Time.today()
    MdDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CalArrow("«") { month = month.minusYears(1) }
                    CalArrow("‹") { month = month.minusMonths(1) }
                    Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    CalArrow("›") { month = month.plusMonths(1) }
                    CalArrow("»") { month = month.plusYears(1) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = p.sub)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val lead = month.atDay(1).dayOfWeek.value % 7
                val days = month.lengthOfMonth()
                val rows = (lead + days + 6) / 7
                val dayShape = when (k) { MdKind.PIXEL -> CutCornerShape(3.dp); MdKind.COMIC -> RoundedCornerShape(8.dp); MdKind.LINE -> CircleShape; MdKind.CREAM -> CircleShape }
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val d = r * 7 + c - lead + 1
                            Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                                if (d in 1..days) {
                                    val date = month.atDay(d)
                                    val on = date == sel
                                    Box(
                                        Modifier.size(34.dp).clip(dayShape)
                                            .background(if (on) (if (k == MdKind.COMIC) ComicYellow else p.mint) else Color.Transparent)
                                            .then(if (on && (k == MdKind.COMIC || k == MdKind.PIXEL)) Modifier.border(2.dp, mdOutline(), dayShape)
                                            else if (date == today && !on) Modifier.border(1.dp, p.mint, dayShape) else Modifier)
                                            .clickable { sel = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("$d", fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                            color = when { on && k == MdKind.COMIC -> ComicInk; on -> Color.White; c == 0 || c == 6 -> p.pink; else -> p.ink })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onPick(sel.toString()) }) { Text("确定") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                extra?.let { (label, act) -> TextButton(act) { Text(label) } }
                TextButton(onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun CalArrow(s: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(if (Md.kind == MdKind.PIXEL) CutCornerShape(3.dp) else CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(s, fontSize = 18.sp, color = P.mintDeep, fontWeight = FontWeight.Bold) }
}
