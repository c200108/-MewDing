package com.mengding.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.R
import kotlinx.coroutines.delay

/**
 * 第二批风格化部件（照 美术/UI重构/<风格>/X_图3 容器表、X_图4 便签表）：
 * 图标、底部导航、顶栏、分组标题、分割线、列表行图标、便签卡片、便签内的块、页签、徽标、状态条、底部弹层。
 * 颜色常量和 mdPanel 等基础件在 Md.kt。
 */

internal val MdWoodDark = Color(0xFF5B3A26)
internal val MdWoodLight = Color(0xFFC08A5B)
internal val MdParchment = Color(0xFFF3E2C3)
internal val MdNight = Color(0xFF2E3A5C)
internal val MdGold = Color(0xFFF4C24B)
internal val MdInk = Color(0xFF3B2A2A)
internal val MdYellow = Color(0xFFFFE27A)

// ---------------- 图标 ----------------

/** 风格化图标：像素风先把线条图标画进 18×18 小图、去掉半透明边，再按最近邻放大成硬边像素图标 */
@Composable
fun MdIcon(icon: ImageVector, desc: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    if (Md.kind != MdKind.PIXEL) { Icon(icon, desc, modifier, tint = tint); return }
    val painter = rememberVectorPainter(icon)
    val dir = LocalLayoutDirection.current
    val bmp = remember(icon, tint) {
        val n = 18
        val img = ImageBitmap(n, n)
        CanvasDrawScope().draw(Density(1f), dir, Canvas(img), Size(n.toFloat(), n.toFloat())) {
            with(painter) { draw(Size(n.toFloat(), n.toFloat()), colorFilter = ColorFilter.tint(tint)) }
        }
        val src = img.asAndroidBitmap()
        val px = IntArray(n * n)
        src.getPixels(px, 0, n, 0, 0, n, n)
        for (i in px.indices) px[i] = if ((px[i] ushr 24) >= 90) px[i] or (0xFF shl 24) else 0
        android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bmp, desc, modifier, filterQuality = FilterQuality.None)
}

// ---------------- 条形底板（底部导航、编辑工具栏） ----------------

/** 贴在屏幕上下边的条：top = true 表示贴底（上边有边线） */
@Composable
fun Modifier.mdBar(top: Boolean = true): Modifier {
    val p = P
    val dark = isSystemInDarkTheme()
    fun Modifier.edge(c: Color, w: Dp, dash: Boolean = false) = drawBehind {
        val y = if (top) 0f else size.height
        drawLine(c, Offset(0f, y), Offset(size.width, y), w.toPx(),
            pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null)
    }
    return when (Md.kind) {
        MdKind.CREAM -> {
            val shape = if (top) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            this.shadow(10.dp, shape, ambientColor = p.pink, spotColor = p.pink).clip(shape)
                .background(if (dark) SolidColor(p.card) else Brush.verticalGradient(listOf(Color(0xFFFFF8EC), Color(0xFFFFF0F4))))
                .border(1.5.dp, if (dark) p.mint.copy(alpha = 0.4f) else Color(0xFF8FD3C1), shape)
        }
        MdKind.COMIC -> this.background(mdSurface()).edge(mdOutline(), 2.5.dp)
        MdKind.LINE -> this.background(mdSurface()).edge(Color(0xFF71BCA8).copy(alpha = 0.6f), 1.dp)
        MdKind.PIXEL -> this.background(MdWoodDark).edge(MdWoodLight, 3.dp)
    }
}

/** 底部导航整条（内容自己 weight 分格） */
@Composable
fun MdNavBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().mdBar(top = true).navigationBarsPadding().height(66.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/**
 * 底部导航一格。art 是界面主题的彩色图标（有就用它，没有用线条图标）。
 * 奶油：选中在下面垫一块粉色气泡；漫画：图标装进贴纸方块，选中变黄、跳起、歪一下；
 * 线条：选中变薄荷色，下面一个小圆点；像素：木条上的羊皮纸图标，选中套金框并抬高。
 */
@Composable
fun RowScope.MdNavItem(selected: Boolean, label: String, icon: ImageVector, art: Int?, onClick: () -> Unit) {
    val k = Md.kind
    val p = P
    val lift by animateDpAsState(if (selected && (k == MdKind.COMIC || k == MdKind.PIXEL)) (-5).dp else 0.dp,
        spring(dampingRatio = 0.5f), label = "lift")
    val fg = when {
        k == MdKind.PIXEL -> if (selected) MdGold else MdParchment
        k == MdKind.COMIC -> if (selected) mdOutline() else p.sub
        selected -> if (k == MdKind.CREAM) p.mintDeep else p.mint
        else -> p.sub
    }
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val box = when (k) {
            MdKind.CREAM -> Modifier.clip(RoundedCornerShape(50)).background(if (selected) p.pinkSoft else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp)
            MdKind.COMIC -> Modifier.offset(y = lift).graphicsLayer { rotationZ = if (selected) -5f else 0f }
                .then(if (selected) Modifier.hardShadow(RoundedCornerShape(10.dp), mdOutline(), 2.dp, 2.dp) else Modifier)
                .clip(RoundedCornerShape(10.dp)).background(if (selected) MdYellow else mdSurface())
                .border(2.dp, mdOutline().copy(alpha = if (selected) 1f else 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
            MdKind.LINE -> Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            MdKind.PIXEL -> Modifier.offset(y = lift)
                .then(if (selected) Modifier.clip(CutCornerShape(3.dp)).background(MdParchment).border(2.dp, MdGold, CutCornerShape(3.dp)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 3.dp)
        }
        Box(box, contentAlignment = Alignment.Center) {
            val tint = if (k == MdKind.PIXEL && selected) MdWoodDark else fg
            if (art != null) Image(painterResource(art), label,
                Modifier.size(24.dp).graphicsLayer { alpha = if (selected) 1f else 0.6f; scaleX = if (selected) 1.1f else 1f; scaleY = scaleX })
            else MdIcon(icon, label, Modifier.size(22.dp), tint)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (k == MdKind.LINE) Box(Modifier.padding(top = 1.dp).size(4.dp).clip(CircleShape).background(if (selected) p.mint else Color.Transparent))
    }
}

// ---------------- 顶栏 ----------------

/** 返回键：奶油薄荷圆、漫画贴纸方块、线条只有细箭头、像素切角木框 */
@Composable
fun MdBackButton(onClick: () -> Unit) {
    val k = Md.kind
    val p = P
    val shape: Shape = when (k) { MdKind.COMIC -> RoundedCornerShape(10.dp); MdKind.PIXEL -> CutCornerShape(3.dp); else -> CircleShape }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(if (k == MdKind.LINE) 44.dp else 38.dp)
                .then(if (k == MdKind.COMIC) Modifier.hardShadow(shape, mdOutline(), 2.dp, 2.dp) else Modifier)
                .clip(shape)
                .background(when (k) { MdKind.CREAM -> p.mintSoft; MdKind.COMIC -> mdSurface(); MdKind.PIXEL -> MdParchment; else -> Color.Transparent })
                .then(when (k) {
                    MdKind.COMIC -> Modifier.border(2.dp, mdOutline(), shape)
                    MdKind.PIXEL -> Modifier.border(2.dp, MdWoodDark, shape)
                    else -> Modifier
                })
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { MdIcon(Ic.Back, "返回", Modifier.size(if (k == MdKind.LINE) 22.dp else 20.dp), if (k == MdKind.PIXEL) MdWoodDark else p.ink) }
    }
}

/** 页面标题：漫画是斜贴的黄色贴纸，像素是木牌，线条细字下面一道细线，奶油圆润粗体 */
@Composable
fun MdPageTitle(title: String, modifier: Modifier = Modifier) {
    val k = Md.kind
    val p = P
    when (k) {
        MdKind.COMIC -> Box(
            modifier.padding(start = 4.dp).graphicsLayer { rotationZ = -2f }.hardShadow(RoundedCornerShape(8.dp), mdOutline(), 2.dp, 2.dp)
                .clip(RoundedCornerShape(8.dp)).background(MdYellow).border(2.dp, mdOutline(), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) { Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = MdInk) }
        MdKind.PIXEL -> Box(
            modifier.padding(start = 4.dp).hardShadow(CutCornerShape(3.dp), Color.Black.copy(alpha = 0.3f), 3.dp, 3.dp)
                .clip(CutCornerShape(3.dp)).background(MdWoodDark).border(2.dp, MdWoodLight, CutCornerShape(3.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MdParchment, letterSpacing = 1.sp) }
        MdKind.LINE -> Text(title, modifier.padding(start = 4.dp), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = p.ink)
        MdKind.CREAM -> Text(title, modifier.padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

/** 顶栏：返回键 + 标题 + 右侧按钮；线条风下面一道细线 */
@Composable
fun MdTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            .then(if (Md.kind == MdKind.LINE) Modifier.drawBehind {
                drawLine(Color(0xFF71BCA8).copy(alpha = 0.35f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            } else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) MdBackButton(onBack) else Spacer(Modifier.width(8.dp))
        Spacer(Modifier.width(4.dp))
        MdPageTitle(title)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** 顶栏右侧的小图标按钮 */
@Composable
fun MdTopIcon(icon: ImageVector, desc: String?, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(if (Md.kind == MdKind.PIXEL) CutCornerShape(3.dp) else CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { MdIcon(icon, desc, Modifier.size(22.dp), tint) }
}

// ---------------- 分组标题、分割线 ----------------

/** 分组标题：奶油带小铃铛，漫画是小黄贴纸，线条一条薄荷竖线，像素 ▶ 光标 */
@Composable
fun MdSectionTitle(text: String, modifier: Modifier = Modifier) {
    val k = Md.kind
    val p = P
    Row(modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (k) {
            MdKind.CREAM -> {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MdGold).border(1.5.dp, p.danger, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.sub)
            }
            MdKind.COMIC -> Box(
                Modifier.graphicsLayer { rotationZ = -1.5f }.clip(RoundedCornerShape(6.dp)).background(MdYellow)
                    .border(1.5.dp, mdOutline(), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
            ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MdInk) }
            MdKind.LINE -> {
                Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(2.dp)).background(p.mint))
                Spacer(Modifier.width(7.dp))
                Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = p.sub)
            }
            MdKind.PIXEL -> {
                Text("▶", fontSize = 11.sp, color = if (isSystemInDarkTheme()) MdGold else MdWoodDark)
                Spacer(Modifier.width(6.dp))
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.sub, letterSpacing = 1.sp)
            }
        }
    }
}

/**
 * 分割线三种：variant 0 列表里用的细线（奶油渐变线 / 漫画粗虚线 / 线条发丝线 / 像素点线），
 * 1 波浪线（漫画是折线），2 一排小爪印
 */
@Composable
fun MdDivider(variant: Int = 0, modifier: Modifier = Modifier) {
    val k = Md.kind
    val p = P
    val c = when (k) { MdKind.COMIC -> mdOutline().copy(alpha = 0.55f); MdKind.PIXEL -> MdWoodLight; MdKind.LINE -> Color(0xFF71BCA8).copy(alpha = 0.35f); else -> p.mint.copy(alpha = 0.5f) }
    Box(modifier.fillMaxWidth().height(if (variant == 0) 6.dp else 14.dp).drawBehind {
        val y = size.height / 2
        when (variant) {
            1 -> {
                val path = Path(); val step = 10.dp.toPx(); val amp = 3.dp.toPx(); var x = 0f
                path.moveTo(0f, y)
                while (x < size.width) {
                    if (k == MdKind.COMIC || k == MdKind.PIXEL) { path.lineTo(x + step / 2, y - amp); path.lineTo(x + step, y + amp) }
                    else path.quadraticBezierTo(x + step / 2, y + (if ((x / step).toInt() % 2 == 0) -amp else amp) * 2, x + step, y)
                    x += step
                }
                drawPath(path, c, style = Stroke(if (k == MdKind.COMIC) 2.5.dp.toPx() else 1.5.dp.toPx()))
            }
            2 -> {
                val gap = 22.dp.toPx(); var x = gap / 2
                while (x < size.width) { pawPrint(Offset(x, y), 4.dp.toPx(), if (k == MdKind.CREAM) p.pink.copy(alpha = 0.6f) else c); x += gap }
            }
            else -> when (k) {
                MdKind.CREAM -> drawLine(Brush.horizontalGradient(listOf(p.mint.copy(alpha = 0.15f), p.mint.copy(alpha = 0.45f), p.pink.copy(alpha = 0.45f), p.pink.copy(alpha = 0.15f))),
                    Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                MdKind.COMIC -> drawLine(c, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())))
                MdKind.LINE -> drawLine(c, Offset(0f, y), Offset(size.width, y), 1f)
                MdKind.PIXEL -> { val s = 2.dp.toPx(); var x = 0f; while (x < size.width) { drawRect(c, Offset(x, y - s / 2), Size(s, s)); x += s * 3 } }
            }
        }
    })
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pawPrint(c: Offset, r: Float, color: Color) {
    drawCircle(color, r, Offset(c.x, c.y + r * 0.4f))
    listOf(-1.25f to -1.1f, -0.42f to -1.65f, 0.42f to -1.65f, 1.25f to -1.1f).forEach { (dx, dy) ->
        drawCircle(color, r * 0.42f, Offset(c.x + dx * r, c.y + dy * r))
    }
}

// ---------------- 列表行图标、便签卡片、便签里的块 ----------------

/** 列表行左边的图标底座 */
@Composable
fun MdRowIcon(icon: ImageVector, accent: Color = P.mintDeep, size: Dp = 36.dp) {
    val k = Md.kind
    val p = P
    val shape: Shape = when (k) { MdKind.CREAM -> CircleShape; MdKind.COMIC -> RoundedCornerShape(size * 0.28f); MdKind.LINE -> CircleShape; MdKind.PIXEL -> CutCornerShape(size * 0.09f) }
    Box(
        Modifier.size(size)
            .then(if (k == MdKind.COMIC && size > 40.dp) Modifier.hardShadow(shape, mdOutline(), 3.dp, 3.dp) else Modifier)
            .clip(shape)
            .background(when (k) { MdKind.LINE -> Color.Transparent; MdKind.PIXEL -> MdParchment; MdKind.COMIC -> Color(0xFFFFEFA8); else -> p.mintSoft })
            .then(when (k) {
                MdKind.COMIC -> Modifier.border(2.dp, mdOutline(), shape)
                MdKind.LINE -> Modifier.border(1.dp, p.mint, shape)
                MdKind.PIXEL -> Modifier.border(2.dp, MdWoodDark, shape)
                else -> Modifier
            }),
        contentAlignment = Alignment.Center,
    ) { MdIcon(icon, null, Modifier.size(size * 0.55f), if (k == MdKind.PIXEL) MdWoodDark else if (k == MdKind.COMIC) MdInk else accent) }
}

/** 便签卡片 4 色（按便签 id 固定一种），奶油和像素顶上别一个小铃铛 */
@Composable
fun MdNoteCard(seed: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val k = Md.kind
    val p = P
    val dark = isSystemInDarkTheme()
    val i = (seed.hashCode() and 0x7fffffff) % 4
    val shape = when (k) { MdKind.CREAM -> RoundedCornerShape(24.dp); MdKind.COMIC -> RoundedCornerShape(14.dp); MdKind.LINE -> RoundedCornerShape(12.dp); MdKind.PIXEL -> CutCornerShape(5.dp) }
    val fill = if (dark) p.card else when (k) {
        MdKind.CREAM -> listOf(Color(0xFFFFF8EC), Color(0xFFFFE6EE), Color(0xFFE1F4EE), Color(0xFFFFF0C9))[i]
        MdKind.COMIC -> listOf(Color(0xFFFFEFA8), Color(0xFFCDE9FC), Color(0xFFFFD0D0), Color(0xFFD5F2D5))[i]
        MdKind.LINE -> Color.White
        MdKind.PIXEL -> listOf(MdParchment, Color(0xFFF7D2DD), Color(0xFFCDEBDD), Color(0xFFF6E2A6))[i]
    }
    val edge = listOf(Color(0xFF71BCA8), Color(0xFFF4A7B9), Color(0xFFD9C3A0), Color(0xFF8FC4E8))[i]
    val pin = k == MdKind.CREAM || k == MdKind.PIXEL
    Column(
        modifier.fillMaxWidth().padding(top = if (pin) 6.dp else 0.dp)
            .graphicsLayer { if (k == MdKind.COMIC) rotationZ = if (i % 2 == 0) -0.6f else 0.6f }
            .drawWithContent {
                drawContent()
                if (pin) {
                    // 小铃铛别针（像素风画成方块）
                    val cx = size.width / 2
                    if (k == MdKind.PIXEL) {
                        val s = 3.dp.toPx()
                        drawRect(MdGold, Offset(cx - s * 1.5f, -s), Size(s * 3, s * 2.5f))
                        drawRect(MdWoodDark, Offset(cx - s * 0.5f, s * 1.2f), Size(s, s))
                        drawRect(Color(0xFFE5606F), Offset(cx - s * 2f, -s * 1.6f), Size(s * 4, s * 0.7f))
                    } else {
                        drawCircle(Color(0xFFE5606F), 3.dp.toPx(), Offset(cx - 5.dp.toPx(), -1.dp.toPx()))
                        drawCircle(Color(0xFFE5606F), 3.dp.toPx(), Offset(cx + 5.dp.toPx(), -1.dp.toPx()))
                        drawCircle(MdGold, 5.dp.toPx(), Offset(cx, 3.dp.toPx()))
                        drawCircle(Color(0xFFB8860B), 1.2.dp.toPx(), Offset(cx, 5.dp.toPx()))
                    }
                }
            }
            .then(when (k) {
                MdKind.CREAM -> Modifier.shadow(3.dp, shape, ambientColor = p.pink, spotColor = p.pink)
                MdKind.COMIC -> Modifier.hardShadow(shape, mdOutline(), 3.dp, 3.dp)
                MdKind.PIXEL -> Modifier.hardShadow(shape, Color.Black.copy(alpha = 0.3f), 3.dp, 3.dp)
                else -> Modifier
            })
            .clip(shape).background(fill)
            .then(when (k) {
                MdKind.CREAM -> Modifier.border(1.5.dp, if (dark) p.mint.copy(alpha = 0.4f) else Color(0xFF8FD3C1), shape)
                MdKind.COMIC -> Modifier.border(2.5.dp, mdOutline(), shape)
                MdKind.LINE -> Modifier.border(1.dp, edge.copy(alpha = 0.5f), shape).drawBehind {
                    drawRect(edge, Offset.Zero, Size(size.width, 3.dp.toPx()))
                }
                MdKind.PIXEL -> Modifier.border(3.dp, MdWoodDark, shape)
            })
            .padding(start = 16.dp, end = 16.dp, top = if (pin) 18.dp else 16.dp, bottom = 16.dp),
        content = content,
    )
}

/** 便签里的语音 / 视频 / 文件 / 提醒等小块的底 */
@Composable
fun Modifier.mdBlock(accent: Color = P.mint): Modifier {
    val k = Md.kind
    val p = P
    val dark = isSystemInDarkTheme()
    val shape = when (k) { MdKind.CREAM -> RoundedCornerShape(18.dp); MdKind.COMIC -> RoundedCornerShape(12.dp); MdKind.LINE -> RoundedCornerShape(10.dp); MdKind.PIXEL -> CutCornerShape(3.dp) }
    return when (k) {
        MdKind.CREAM -> this.clip(shape).background(if (dark) p.mintSoft else accent.copy(alpha = 0.16f)).border(1.5.dp, accent.copy(alpha = 0.45f), shape)
        MdKind.COMIC -> this.hardShadow(shape, mdOutline(), 2.dp, 2.dp).clip(shape).background(if (dark) p.card else accent.copy(alpha = 0.22f)).border(2.dp, mdOutline(), shape)
        MdKind.LINE -> this.clip(shape).background(mdSurface()).border(1.dp, accent.copy(alpha = 0.6f), shape)
        MdKind.PIXEL -> this.clip(shape).background(if (dark) MdNight else MdParchment).border(2.dp, MdWoodDark, shape)
    }
}

/**
 * 可选中的格子（商店商品、宠物卡、换装格、主题卡、引导页选项、游戏入口……）：
 * 没选中是普通卡片，选中时加粗描边 / 换底色；漫画、像素带硬投影。radius 只影响奶油和线条。
 */
@Composable
fun Modifier.mdTile(selected: Boolean = false, accent: Color = P.mint, radius: Dp = 18.dp): Modifier {
    val k = Md.kind
    val p = P
    val dark = isSystemInDarkTheme()
    val shape: Shape = when (k) {
        MdKind.CREAM -> RoundedCornerShape(radius + 4.dp)
        MdKind.COMIC -> RoundedCornerShape(radius * 0.65f)
        MdKind.LINE -> RoundedCornerShape(radius * 0.6f)
        MdKind.PIXEL -> CutCornerShape(4.dp)
    }
    return when (k) {
        MdKind.CREAM -> this.shadow(if (selected) 6.dp else 2.dp, shape, ambientColor = p.pink, spotColor = p.pink).clip(shape)
            .background(if (selected) SolidColor(accent.copy(alpha = 0.16f)) else if (dark) SolidColor(p.card) else Brush.verticalGradient(listOf(Color(0xFFFFF8EC), Color(0xFFFFF0F4))))
            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) accent else (if (dark) p.line else Color(0xFF8FD3C1).copy(alpha = 0.55f)), shape)
        MdKind.COMIC -> this.hardShadow(shape, mdOutline(), if (selected) 4.dp else 2.dp, if (selected) 4.dp else 2.dp).clip(shape)
            .background(if (selected) (if (dark) accent.copy(alpha = 0.3f) else MdYellow) else mdSurface())
            .border(if (selected) 2.5.dp else 2.dp, mdOutline(), shape)
        MdKind.LINE -> this.clip(shape).background(if (selected) accent.copy(alpha = 0.08f) else mdSurface())
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else Color(0xFF71BCA8).copy(alpha = 0.35f), shape)
        MdKind.PIXEL -> this.hardShadow(shape, Color.Black.copy(alpha = 0.28f), 3.dp, 3.dp).clip(shape)
            .background(if (dark) MdNight else if (selected) Color(0xFFF8EBC4) else MdParchment)
            .border(if (selected) 3.dp else 2.dp, if (selected) MdGold else MdWoodDark, shape)
    }
}

/** 小标签底（「限定」「已拥有」「去设置」这类小字条） */
@Composable
fun Modifier.mdTag(accent: Color = P.mint): Modifier {
    val k = Md.kind
    val shape: Shape = when (k) { MdKind.PIXEL -> CutCornerShape(2.dp); MdKind.COMIC -> RoundedCornerShape(6.dp); MdKind.LINE -> RoundedCornerShape(4.dp); else -> RoundedCornerShape(50) }
    return when (k) {
        MdKind.CREAM -> this.clip(shape).background(accent.copy(alpha = 0.18f))
        MdKind.COMIC -> this.clip(shape).background(MdYellow).border(1.5.dp, mdOutline(), shape)
        MdKind.LINE -> this.clip(shape).border(1.dp, accent, shape)
        MdKind.PIXEL -> this.clip(shape).background(MdParchment).border(1.5.dp, MdWoodDark, shape)
    }
}

// ---------------- 页签、徽标、状态条、底部弹层 ----------------

/** 页签：奶油气泡槽里的胶囊、漫画斜贴纸、线条下划线、像素木头页签 */
@Composable
fun MdTabs(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val k = Md.kind
    val p = P
    Row(
        modifier.fillMaxWidth()
            .then(if (k == MdKind.CREAM) Modifier.clip(RoundedCornerShape(50)).background(p.mintSoft.copy(alpha = 0.6f)).padding(4.dp) else Modifier)
            .then(if (k == MdKind.LINE) Modifier.drawBehind {
                drawLine(p.line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
            } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(if (k == MdKind.CREAM || k == MdKind.LINE) 0.dp else 8.dp),
    ) {
        options.forEachIndexed { idx, (key, name) ->
            val on = key == selected
            val shape: Shape = when (k) {
                MdKind.CREAM -> RoundedCornerShape(50)
                MdKind.COMIC -> RoundedCornerShape(10.dp)
                MdKind.LINE -> RoundedCornerShape(0.dp)
                MdKind.PIXEL -> CutCornerShape(topStart = 4.dp, topEnd = 4.dp)
            }
            Box(
                Modifier.weight(1f)
                    .graphicsLayer { if (k == MdKind.COMIC) rotationZ = if (on) -2f else (if (idx % 2 == 0) 1f else -1f) }
                    .then(if (k == MdKind.COMIC && on) Modifier.hardShadow(shape, mdOutline(), 2.dp, 2.dp) else Modifier)
                    .clip(shape)
                    .background(when (k) {
                        MdKind.CREAM -> if (on) p.mint else Color.Transparent
                        MdKind.COMIC -> if (on) MdYellow else mdSurface()
                        MdKind.LINE -> Color.Transparent
                        MdKind.PIXEL -> if (on) MdParchment else MdWoodLight.copy(alpha = 0.55f)
                    })
                    .then(when (k) {
                        MdKind.COMIC -> Modifier.border(2.dp, mdOutline(), shape)
                        MdKind.PIXEL -> Modifier.border(2.dp, MdWoodDark, shape)
                        MdKind.LINE -> if (on) Modifier.drawBehind {
                            drawLine(p.mint, Offset(size.width * 0.2f, size.height - 1.dp.toPx()), Offset(size.width * 0.8f, size.height - 1.dp.toPx()), 2.dp.toPx())
                        } else Modifier
                        else -> Modifier
                    })
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp,
                    color = when {
                        k == MdKind.CREAM && on -> Color.White
                        k == MdKind.COMIC -> MdInk
                        k == MdKind.PIXEL -> MdWoodDark
                        on -> p.mintDeep
                        else -> p.sub
                    })
            }
        }
    }
}

/** 红点徽标（text 为空就是小红点） */
@Composable
fun MdBadge(text: String = "", modifier: Modifier = Modifier) {
    val k = Md.kind
    val shape: Shape = if (k == MdKind.PIXEL) CutCornerShape(2.dp) else RoundedCornerShape(50)
    Box(
        modifier.then(if (text.isEmpty()) Modifier.size(9.dp) else Modifier.height(18.dp))
            .clip(shape).background(Color(0xFFFF6B6B))
            .then(when (k) {
                MdKind.COMIC -> Modifier.border(1.5.dp, mdOutline(), shape)
                MdKind.PIXEL -> Modifier.border(1.5.dp, MdWoodDark, shape)
                else -> Modifier.border(1.5.dp, Color.White, shape)
            })
            .padding(horizontal = if (text.isEmpty()) 0.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) { if (text.isNotEmpty()) Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/** 数值条（0..100）：奶油带光泽、漫画斜纹加描边、线条细条、像素分段血条 */
@Composable
fun MdMeter(value: Float, color: Color, modifier: Modifier = Modifier, warnBelow: Float = 30f) {
    val k = Md.kind
    val p = P
    val f = (value / 100f).coerceIn(0f, 1f)
    val c = if (value < warnBelow) p.danger else color
    val h = when (k) { MdKind.LINE -> 4.dp; MdKind.PIXEL -> 10.dp; MdKind.COMIC -> 11.dp; else -> 9.dp }
    val shape: Shape = if (k == MdKind.PIXEL) CutCornerShape(2.dp) else RoundedCornerShape(50)
    Box(
        modifier.fillMaxWidth().height(h).clip(shape)
            .background(if (k == MdKind.PIXEL) MdNight else p.line)
            .then(when (k) {
                MdKind.COMIC -> Modifier.border(1.5.dp, mdOutline(), shape)
                MdKind.PIXEL -> Modifier.border(1.5.dp, MdWoodDark, shape)
                else -> Modifier
            })
            .drawBehind {
                when (k) {
                    MdKind.PIXEL -> {
                        val n = 10; val gap = 1.5.dp.toPx(); val inset = 2.dp.toPx()
                        val w = (size.width - inset * 2 - gap * (n - 1)) / n
                        for (s in 0 until n) if (s < (f * n + 0.001f).toInt().coerceAtLeast(if (f > 0f) 1 else 0))
                            drawRect(c, Offset(inset + s * (w + gap), inset), Size(w, size.height - inset * 2))
                    }
                    else -> {
                        val w = size.width * f
                        drawRoundRect(c, Offset.Zero, Size(w, size.height), CornerRadius(size.height / 2))
                        if (k == MdKind.COMIC) {
                            val step = 7.dp.toPx(); var x = -size.height
                            while (x < w) {
                                drawLine(Color.White.copy(alpha = 0.35f), Offset(x, size.height), Offset((x + size.height).coerceAtMost(w), 0f), 2.dp.toPx())
                                x += step
                            }
                        }
                        if (k == MdKind.CREAM && w > size.height)
                            drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(size.height * 0.4f, size.height * 0.18f),
                                Size((w - size.height * 0.8f).coerceAtLeast(0f) * 0.6f, size.height * 0.25f), CornerRadius(size.height))
                    }
                }
            },
    )
}

/** 底部弹层的底（我的小仓库等）：顶边圆角 / 切角 + 风格描边 + 抓手条 */
@Composable
fun Modifier.mdSheet(): Modifier {
    val k = Md.kind
    val p = P
    val dark = isSystemInDarkTheme()
    val shape = when (k) {
        MdKind.CREAM -> RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        MdKind.COMIC -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        MdKind.LINE -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        MdKind.PIXEL -> CutCornerShape(topStart = 6.dp, topEnd = 6.dp)
    }
    val bg = when (k) { MdKind.PIXEL -> if (dark) MdNight else MdParchment; MdKind.LINE -> mdSurface(); MdKind.COMIC -> mdSurface(); else -> p.cream }
    return this.shadow(if (k == MdKind.LINE) 4.dp else 12.dp, shape).clip(shape)
        .background(if (k == MdKind.CREAM && !dark) Brush.verticalGradient(listOf(Color(0xFFFFF8EC), Color(0xFFFFF0F4))) else SolidColor(bg))
        .then(when (k) {
            MdKind.CREAM -> Modifier.border(2.dp, if (dark) p.mint.copy(alpha = 0.4f) else Color(0xFF8FD3C1), shape)
            MdKind.COMIC -> Modifier.border(3.dp, mdOutline(), shape)
            MdKind.LINE -> Modifier.border(1.dp, Color(0xFF71BCA8).copy(alpha = 0.6f), shape)
            MdKind.PIXEL -> Modifier.border(3.dp, MdWoodDark, shape)
        })
}

@Composable
fun MdGrabber(modifier: Modifier = Modifier) {
    val k = Md.kind
    val p = P
    Box(modifier.padding(top = 8.dp).size(if (k == MdKind.PIXEL) 30.dp else 38.dp, if (k == MdKind.PIXEL) 6.dp else 5.dp)
        .clip(if (k == MdKind.PIXEL) CutCornerShape(1.dp) else RoundedCornerShape(3.dp))
        .background(when (k) { MdKind.PIXEL -> MdWoodLight; MdKind.COMIC -> mdOutline(); MdKind.CREAM -> Color(0xFF8FD3C1); else -> p.line }))
}

/** 像素风的「加载中」：4 帧奔跑小猫（D 控件表裁出） */
@Composable
fun MdPixelCatRunner(modifier: Modifier = Modifier) {
    var f by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(120); f = (f + 1) % 4 } }
    val ids = listOf(R.drawable.md_pixel_cat0, R.drawable.md_pixel_cat1, R.drawable.md_pixel_cat2, R.drawable.md_pixel_cat3)
    Image(ImageBitmap.imageResource(ids[f]), "加载中", modifier, filterQuality = FilterQuality.None)
}
