package com.mengding.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 三个解压小游戏（不计萌点、不计分，随便玩）：
 *   捏云：揉软乎乎的云，揉够了下一场小雨，地上开出花；宠物在云上打滚
 *   理毛线：缠在一起的毛线团，拖动线结把它们理开，理到一根都不交叉就织成一件小东西
 *   墨池：往水面滴颜料、用手指划出大理石纹，可以存成手机壁纸
 */

private val Cream = Color(0xFFFFF8F2)

/** 游戏页里画宠物：用当前出门的宠物（没有就用小猫），带它穿着的衣服 */
@Composable
private fun rememberPetLook(): PetLook {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val pet = PetSim.outing(cfg) ?: cfg.pets.firstOrNull()
    val species = pet?.species ?: "cat"
    val worn = pet?.worn ?: emptyList()
    return remember(species, worn) { PetLook(ctx, species, worn) }
}

/** 每帧时间（秒），驱动动画 */
@Composable
private fun frameClock(onFrame: (Float) -> Unit): Long {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) onFrame(((now - last) / 1e9f).coerceAtMost(0.05f))
            last = now; tick = now
        }
    }
    return tick
}

// =====================================================================
// 捏云
// =====================================================================

private class CloudSim {
    /** 云由一圈软球组成：每个球有静止位置（相对云心）和当前偏移（弹簧） */
    class Puff(val bx: Float, val by: Float, val r: Float) { var ox = 0f; var oy = 0f; var vx = 0f; var vy = 0f }
    val puffs = listOf(
        Puff(0f, -0.18f, .30f), Puff(-.34f, -.02f, .24f), Puff(.34f, -.02f, .26f), Puff(-.60f, .12f, .18f), Puff(.60f, .12f, .19f),
        Puff(-.20f, .14f, .22f), Puff(.18f, .15f, .23f), Puff(-.42f, -.20f, .15f), Puff(.40f, -.22f, .16f),
    )
    var knead = 0f            // 0..1，揉够了下雨
    var rainT = 0f            // 剩余下雨时间
    var t = 0f
    val drops = mutableListOf<FloatArray>()   // x, y, vy（相对画布）
    val flowers = mutableListOf<FloatArray>() // x, 生长 0..1, 颜色序号
    var petAction = "idle"; var petActT = 0f

    fun press(px: Float, py: Float, dx: Float, dy: Float, cx: Float, cy: Float, s: Float) {
        // 手指附近的球被拽向手指方向（软软地变形）
        puffs.forEach { p ->
            val x = cx + (p.bx + p.ox) * s; val y = cy + (p.by + p.oy) * s
            val d = hypot(px - x, py - y)
            val w = max(0f, 1f - d / (s * .55f))
            p.vx += dx / s * w * 18f; p.vy += dy / s * w * 18f
        }
        if (rainT <= 0f) knead = min(1f, knead + hypot(dx, dy) / s * .06f)
        petAction = "roll"; petActT = 1.2f
    }

    fun update(dt: Float, w: Float, h: Float, groundY: Float) {
        t += dt
        puffs.forEach { p ->   // 弹簧回位，带一点晃
            p.vx += (-p.ox * 60f - p.vx * 6f) * dt; p.vy += (-p.oy * 60f - p.vy * 6f) * dt
            p.ox = (p.ox + p.vx * dt).coerceIn(-.35f, .35f); p.oy = (p.oy + p.vy * dt).coerceIn(-.35f, .35f)
        }
        if (knead >= 1f && rainT <= 0f) { rainT = 6f; knead = 0f; petAction = "energetic"; petActT = 2f }
        if (rainT > 0f) {
            rainT -= dt
            repeat(3) { drops += floatArrayOf(w * (.25f + Random.nextFloat() * .5f), h * .38f, h * (1.2f + Random.nextFloat() * .4f)) }
        }
        drops.forEach { it[1] += it[2] * dt }
        val landed = drops.filter { it[1] >= groundY }
        if (landed.isNotEmpty() && flowers.size < 14 && Random.nextFloat() < .04f)
            flowers += floatArrayOf(landed.first()[0], 0f, Random.nextInt(4).toFloat())
        drops.removeAll { it[1] >= groundY }
        flowers.forEach { it[1] = min(1f, it[1] + dt * .5f) }
        if (petActT > 0f) { petActT -= dt; if (petActT <= 0f) petAction = "idle" }
    }
}

@Composable
fun CloudGamePage() {
    val ctx = LocalContext.current
    val look = rememberPetLook()
    val sim = remember { CloudSim() }
    var size by remember { mutableStateOf(Size.Zero) }
    val tick = frameClock { dt -> if (size.width > 0) sim.update(dt, size.width, size.height, size.height * .9f) }
    PageScaffold("捏云", scroll = false) {
        @Suppress("UNUSED_EXPRESSION") tick
        Text(if (sim.rainT > 0) "下雨啦～地上会开出小花" else "揉一揉、捏一捏软乎乎的云，揉够了会下雨", fontSize = 13.sp, color = P.sub)
        Gap(6.dp)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.line)) {
            Box(Modifier.fillMaxWidth(sim.knead).height(4.dp).background(P.blue))
        }
        Gap(8.dp)
        Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                var lastBuzz = 0f
                detectDragGestures { ch, drag ->
                    val s = size.width * .42f
                    sim.press(ch.position.x, ch.position.y, drag.x, drag.y, size.width / 2, size.height * .32f, s)
                    if (sim.t - lastBuzz > .18f) { lastBuzz = sim.t; GameHaptics.tap(ctx) }
                }
            }
            .pointerInput(Unit) { detectTapGestures { o -> sim.press(o.x, o.y, 0f, -30f, size.width / 2, size.height * .32f, size.width * .42f) } }
        ) {
            @Suppress("UNUSED_EXPRESSION") tick
            size = this.size
            drawCloudScene(sim, look)
        }
    }
}

private fun DrawScope.drawCloudScene(sim: CloudSim, look: PetLook) {
    val w = size.width; val h = size.height
    val rainy = sim.rainT > 0f
    drawRect(if (rainy) Color(0xFFCFDDEB) else Color(0xFFDDEFFF))
    // 地面和花
    val gy = h * .9f
    drawRect(Color(0xFFA9D98B), Offset(0f, gy), Size(w, h - gy))
    val petals = listOf(Color(0xFFFF9EBB), Color(0xFFFFD45C), Color(0xFFB79CFF), Color(0xFFFFFFFF))
    sim.flowers.forEach { f ->
        val g = f[1]; val x = f[0]
        drawLine(Color(0xFF4FA36A), Offset(x, gy), Offset(x, gy - 34f * g), 5f)
        if (g > .5f) {
            val r = 10f * (g - .5f) * 2f
            for (k in 0 until 5) {
                val a = k * 2 * PI.toFloat() / 5 + sim.t * .2f
                drawCircle(petals[f[2].toInt()], r, Offset(x + cos(a) * r, gy - 34f * g + sin(a) * r))
            }
            drawCircle(Color(0xFFFFC94A), r * .7f, Offset(x, gy - 34f * g))
        }
    }
    // 雨
    sim.drops.forEach { drawLine(Color(0x996CA6D9), Offset(it[0], it[1]), Offset(it[0], it[1] + 16f), 4f, StrokeCap.Round) }
    // 云：先画浅色描边再画实心，一团软球
    val cx = w / 2; val cy = h * .32f; val s = w * .42f
    val grey = (sim.knead * .35f + if (rainy) .25f else 0f).coerceAtMost(.5f)
    val body = Color(1f - grey * .5f, 1f - grey * .45f, 1f - grey * .3f)
    sim.puffs.forEach { p -> drawCircle(Color(0xFFB8CDE3), p.r * s + 5f, Offset(cx + (p.bx + p.ox) * s, cy + (p.by + p.oy) * s)) }
    sim.puffs.forEach { p -> drawCircle(body, p.r * s, Offset(cx + (p.bx + p.ox) * s, cy + (p.by + p.oy) * s)) }
    // 腮红和眯眯眼，云也是可爱的
    val face = sim.puffs[0]; val fx = cx + face.ox * s; val fy = cy + .02f * s + face.oy * s
    drawCircle(Color(0x55FF8FA8), s * .05f, Offset(fx - s * .16f, fy + s * .03f)); drawCircle(Color(0x55FF8FA8), s * .05f, Offset(fx + s * .16f, fy + s * .03f))
    drawArc(Color(0xFF5B4B6B), 200f, 140f, false, Offset(fx - s * .12f, fy - s * .04f), Size(s * .07f, s * .05f), style = Stroke(4f, cap = StrokeCap.Round))
    drawArc(Color(0xFF5B4B6B), 200f, 140f, false, Offset(fx + s * .05f, fy - s * .04f), Size(s * .07f, s * .05f), style = Stroke(4f, cap = StrokeCap.Round))
    // 宠物在云顶上打滚
    val top = sim.puffs[0]; val ps = s * .55f
    look.draw(this, sim.petAction, sim.t, cx - ps / 2 + top.ox * s, cy + (top.by + top.oy) * s - top.r * s - ps * .78f, ps)
}

// =====================================================================
// 理毛线
// =====================================================================

private class YarnPuzzle(val level: Int, w: Float, h: Float) {
    val nodes = mutableListOf<Offset>()
    val edges = mutableListOf<Pair<Int, Int>>()
    val colors = listOf(Color(0xFFFF8FA8), Color(0xFF7CC7E8), Color(0xFFFFC94A), Color(0xFF9ADB8C), Color(0xFFB79CFF), Color(0xFFFFA36C))
    val lineOf = mutableListOf<Int>()   // 每条边属于哪根线（上色）
    var solvedT = -1f

    init {
        // 「随机直线求交点」生成一定能理开的线团：每两条直线交一个结，同一条直线上相邻的结用毛线连起来
        val L = (3 + level).coerceAtMost(7)
        val lines = List(L) { val a = Random.nextFloat() * PI.toFloat(); floatArrayOf(cos(a), sin(a), Random.nextFloat() - .5f) }
        val ids = HashMap<Pair<Int, Int>, Int>()
        val pts = mutableListOf<Offset>()
        for (i in 0 until L) for (j in i + 1 until L) {
            val (a1, b1, c1) = lines[i].let { Triple(it[0], it[1], it[2]) }; val (a2, b2, c2) = lines[j].let { Triple(it[0], it[1], it[2]) }
            val det = a1 * b2 - a2 * b1
            if (abs(det) < 1e-3f) continue
            ids[i to j] = pts.size; pts += Offset((c1 * b2 - c2 * b1) / det, (a1 * c2 - a2 * c1) / det)
        }
        for (i in 0 until L) {
            val onLine = (0 until L).filter { it != i }.mapNotNull { j -> ids[min(i, j) to max(i, j)] }
            val dir = Offset(-lines[i][1], lines[i][0])
            val sorted = onLine.sortedBy { pts[it].x * dir.x + pts[it].y * dir.y }
            for (k in 0 until sorted.size - 1) { edges += sorted[k] to sorted[k + 1]; lineOf += i }
        }
        // 打乱：把结摆在一个圆上、顺序随机 —— 线全缠在一起
        val order = pts.indices.shuffled()
        val cx = w / 2; val cy = h * .45f; val r = min(w, h) * .38f
        repeat(pts.size) { nodes += Offset.Zero }
        order.forEachIndexed { k, id -> val a = k * 2 * PI.toFloat() / pts.size; nodes[id] = Offset(cx + cos(a) * r, cy + sin(a) * r) }
    }

    fun crosses(e: Pair<Int, Int>, f: Pair<Int, Int>): Boolean {
        if (e.first == f.first || e.first == f.second || e.second == f.first || e.second == f.second) return false
        fun ccw(a: Offset, b: Offset, c: Offset) = (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
        val a = nodes[e.first]; val b = nodes[e.second]; val c = nodes[f.first]; val d = nodes[f.second]
        return ccw(a, c, d) != ccw(b, c, d) && ccw(a, b, c) != ccw(a, b, d)
    }

    fun tangled(): Set<Int> {
        val bad = HashSet<Int>()
        for (i in edges.indices) for (j in i + 1 until edges.size) if (crosses(edges[i], edges[j])) { bad += i; bad += j }
        return bad
    }
}

@Composable
fun YarnGamePage() {
    val ctx = LocalContext.current
    val look = rememberPetLook()
    var level by remember { mutableIntStateOf(1) }
    var size by remember { mutableStateOf(Size.Zero) }
    var puzzle by remember { mutableStateOf<YarnPuzzle?>(null) }
    var t by remember { mutableStateOf(0f) }
    var dragging by remember { mutableIntStateOf(-1) }
    val tick = frameClock { dt -> t += dt }
    val crafts = listOf("小围巾", "毛线帽", "小手套", "毛线球", "小背心", "袜子", "小毯子")
    PageScaffold("理毛线", scroll = false) {
        @Suppress("UNUSED_EXPRESSION") tick
        val p = puzzle
        val bad = p?.tangled() ?: emptySet()
        if (p != null && bad.isEmpty() && p.solvedT < 0f) { p.solvedT = t; GameHaptics.tap(ctx) }
        HRow {
            Text("第 $level 团", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
            Spacer(Modifier.weight(1f))
            Text(if (p != null && bad.isEmpty()) "理好啦！" else "还有 ${bad.size} 处缠在一起", fontSize = 13.sp,
                color = if (p != null && bad.isEmpty()) P.mintDeep else P.sub)
        }
        Text("拖动线结，把毛线理到一根都不交叉", fontSize = 12.sp, color = P.sub)
        Gap(6.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                .pointerInput(level) {
                    detectDragGestures(
                        onDragStart = { o -> dragging = puzzle?.nodes?.indices?.minByOrNull { (puzzle!!.nodes[it] - o).getDistance() }
                            ?.takeIf { (puzzle!!.nodes[it] - o).getDistance() < 70f } ?: -1 },
                        onDragEnd = { dragging = -1 }, onDragCancel = { dragging = -1 },
                    ) { ch, d ->
                        val pz = puzzle ?: return@detectDragGestures
                        if (dragging >= 0 && pz.solvedT < 0f) {
                            val n = pz.nodes[dragging] + d
                            pz.nodes[dragging] = Offset(n.x.coerceIn(20f, size.width - 20f), n.y.coerceIn(20f, size.height - 20f))
                            ch.consume()
                        }
                    }
                }
            ) {
                @Suppress("UNUSED_EXPRESSION") tick
                if (this.size != size) { size = this.size }
                if (puzzle == null && size.width > 0) puzzle = YarnPuzzle(level, size.width, size.height * .82f)
                drawRect(Color(0xFFFFF1E4))
                val pz = puzzle ?: return@Canvas
                val solved = pz.solvedT >= 0f
                pz.edges.forEachIndexed { i, (a, b) ->
                    val pa = pz.nodes[a]; val pb = pz.nodes[b]
                    val mid = (pa + pb) / 2f + Offset(0f, 14f + 4f * sin(t * 2 + i))   // 毛线软软地往下垂
                    val path = Path().apply { moveTo(pa.x, pa.y); quadraticTo(mid.x, mid.y, pb.x, pb.y) }
                    val c = pz.colors[pz.lineOf[i] % pz.colors.size]
                    val tangled = i in bad
                    drawPath(path, Color.Black.copy(alpha = .12f), style = Stroke(14f, cap = StrokeCap.Round))
                    drawPath(path, if (tangled && (t * 3).toInt() % 2 == 0) c.copy(alpha = .55f) else c, style = Stroke(10f, cap = StrokeCap.Round))
                }
                pz.nodes.forEachIndexed { i, n ->
                    drawCircle(Color(0xFF8C6A5A), if (i == dragging) 22f else 17f, n)
                    drawCircle(Color(0xFFF7E3D2), if (i == dragging) 15f else 11f, n)
                }
                // 宠物在角落看着；理好了就开心地蹦
                val ps = this.size.minDimension * .26f
                look.draw(this, if (solved) "energetic" else if (dragging >= 0) "pet" else "idle", t, this.size.width - ps - 8f, this.size.height - ps, ps)
                if (solved) {
                    val k = ((t - pz.solvedT) / .6f).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = .7f * k), this.size.minDimension * .22f * k, Offset(this.size.width / 2, this.size.height * .42f))
                }
            }
            if (p != null && p.solvedT >= 0f) {
                Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(P.card).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("理好啦！", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Text("织成了一件「${crafts[(level - 1) % crafts.size]}」", fontSize = 14.sp, color = P.sub)
                    Gap(10.dp)
                    PrimaryButton("下一团") { level++; puzzle = null }
                }
            }
        }
        Gap(8.dp)
        HRow {
            GhostButton("重新打乱") { puzzle = null }
            if (level > 1) GhostButton("回到第 1 团") { level = 1; puzzle = null }
        }
    }
}

// =====================================================================
// 墨池（大理石纹）
// =====================================================================

private class InkPool {
    class Drop(val color: Color, val pts: MutableList<Offset>)
    val drops = mutableListOf<Drop>()

    /** 滴一滴：新圆周围的颜料被均匀推开（大理石纹的经典公式 p' = c + (p-c)·√(1 + r²/|p-c|²)） */
    fun drip(c: Offset, r: Float, color: Color) {
        drops.forEach { d ->
            for (i in d.pts.indices) {
                val v = d.pts[i] - c; val l2 = max(v.x * v.x + v.y * v.y, 1e-3f)
                d.pts[i] = c + v * sqrt(1f + r * r / l2)
            }
        }
        drops += Drop(color, MutableList(96) { k -> val a = k * 2 * PI.toFloat() / 96; c + Offset(cos(a) * r, sin(a) * r) })
        if (drops.size > 60) drops.removeAt(0)
    }

    /** 手指划过：沿划动方向拖动颜料，离划线越近拖得越多 */
    fun comb(a: Offset, b: Offset) {
        val d = b - a; val len = d.getDistance(); if (len < .5f) return
        val dir = d / len; val nrm = Offset(-dir.y, dir.x)
        val lambda = 28f
        drops.forEach { dr ->
            for (i in dr.pts.indices) {
                val p = dr.pts[i]; val dist = abs((p - a).x * nrm.x + (p - a).y * nrm.y)
                dr.pts[i] = p + dir * (len * lambda / (dist + lambda))
            }
        }
    }

    fun path(d: Drop) = Path().apply { d.pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
}

@Composable
fun InkGamePage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val pool = remember { InkPool() }
    val palette = listOf(Color(0xFF2E5E8C), Color(0xFFE5606F), Color(0xFFFFC94A), Color(0xFF2FA37E), Color(0xFF9A72D8), Color(0xFFF7F1E8), Color(0xFF3A3247))
    var color by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(Size.Zero) }
    var version by remember { mutableIntStateOf(0) }
    PageScaffold("墨池", scroll = false) {
        Text("点一下滴颜料，手指划过水面拉出花纹", fontSize = 13.sp, color = P.sub)
        Gap(6.dp)
        Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, P.line, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = down.position; var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        if (ch.positionChange().getDistance() > 0f) {
                            if ((ch.position - down.position).getDistance() > 12f) moved = true
                            if (moved) { pool.comb(last, ch.position); version++ }
                            last = ch.position; ch.consume()
                        }
                    }
                    if (!moved) { pool.drip(down.position, 36f + Random.nextFloat() * 30f, palette[color]); version++; GameHaptics.tap(ctx) }
                }
            }
        ) {
            @Suppress("UNUSED_EXPRESSION") version
            size = this.size
            drawRect(Cream)
            pool.drops.forEach { drawPath(pool.path(it), it.color) }
        }
        Gap(8.dp)
        HRow(gap = 10.dp) {
            palette.forEachIndexed { i, c ->
                Box(Modifier.size(if (i == color) 34.dp else 28.dp).clip(CircleShape).background(c)
                    .border(if (i == color) 3.dp else 1.dp, if (i == color) P.ink else P.line, CircleShape).clickable { color = i })
            }
        }
        Gap(8.dp)
        HRow {
            GhostButton("清空") { pool.drops.clear(); version++ }
            PrimaryButton("存成壁纸") {
                val err = saveInk(ctx, pool, size)
                toast(err ?: "已存到相册「萌叮」里，可以设成手机壁纸")
            }
        }
    }
}

/** 按手机屏幕比例重画一张高清图存进相册（Pictures/萌叮） */
private fun saveInk(ctx: android.content.Context, pool: InkPool, view: Size): String? = runCatching {
    if (pool.drops.isEmpty()) return "先滴几滴颜料吧"
    val dm = ctx.resources.displayMetrics
    val W = dm.widthPixels; val H = dm.heightPixels
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(Cream.toArgb())
    // 画布内容放大铺满屏幕（按短边对齐，居中）
    val k = max(W / view.width, H / view.height)
    c.translate((W - view.width * k) / 2f, (H - view.height * k) / 2f); c.scale(k, k)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    pool.drops.forEach { d ->
        paint.color = d.color.toArgb()
        val p = android.graphics.Path(); d.pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }; p.close()
        c.drawPath(p, paint)
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "萌叮墨池_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/萌叮")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return "保存失败"
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    null
}.getOrElse { "保存失败：${it.message}" }
