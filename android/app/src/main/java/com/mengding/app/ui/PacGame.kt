package com.mengding.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/*
 * 萌宠吃豆：玩法和吃豆人一致。
 *   宠物在迷宫里吃豆豆，吃完全部豆豆过关；四朵"捣蛋云"追你，碰到就掉一条命。
 *   吃到铃铛豆，捣蛋云会害怕变紫，这时可以反过来抓它们（连抓 200 / 400 / 800 / 1600 分），
 *   被抓的云变成小水滴飞回云朵窝。四朵云各有性格：
 *     雷雷（红）直接追你；棉棉（粉）埋伏在你前面 4 格；雨雨（蓝）和雷雷包抄；晴晴（橙）离近了就跑开。
 *   云朵会在"散开"和"追击"两种模式之间切换；左右两边的通道可以穿到另一边，云在通道里会变慢。
 *   每关吃掉 50 / 110 颗豆后，云朵窝下面会出现奖励食物。
 */

private const val PW = 19
private const val PH = 21
internal enum class GS { HOUSE, LEAVING, ACTIVE, EATEN, ENTERING }
internal enum class Phase { READY, PLAY, DYING, CLEAR, OVER }

internal class Mover(var tx: Int, var ty: Int, var dx: Int = 0, var dy: Int = 0, var p: Float = 0f) {
    val x get() = tx + dx * p
    val y get() = ty + dy * p
    val moving get() = dx != 0 || dy != 0
    fun reverse() {
        if (!moving) return
        tx += dx; ty += dy; dx = -dx; dy = -dy; p = 1f - p
    }
    fun set(x: Int, y: Int, ddx: Int, ddy: Int) { tx = x; ty = y; dx = ddx; dy = ddy; p = 0f }
}

internal class Ghost(val id: Int) {
    var st = GS.HOUSE
    val m = Mover(9, 7)
    var fx = 9f; var fy = 9f
    var scared = false
    var pendingRev = false
    val x get() = if (st == GS.ACTIVE || st == GS.EATEN) m.x else fx
    val y get() = if (st == GS.ACTIVE || st == GS.EATEN) m.y else fy
}

internal class Pop(val x: Float, val y: Float, val text: String, var t: Float = 0f)

/** 上、左、下、右：方向相同时按这个顺序选，和原版一致 */
private val DIR4 = listOf(0 to -1, -1 to 0, 0 to 1, 1 to 0)
private val MODES = floatArrayOf(7f, 20f, 7f, 20f, 5f, 20f, 5f, Float.MAX_VALUE)   // 散开 / 追击 交替
private val CORNERS = listOf(17 to -2, 1 to -2, 18 to 22, 0 to 22)
private val HOME = listOf(9f to 9f, 9f to 9f, 8f to 9f, 10f to 9f)
private val RELEASE = floatArrayOf(0f, 1f, 4f, 8f)
private val BONUS = listOf("carrot" to 100, "apple" to 300, "rice_ball" to 500, "dried_fish" to 700,
    "strawberry_cake" to 1000, "honey_water" to 2000)

internal class PacGame(private val rows: List<String>) {
    val bean = Array(PH) { IntArray(PW) }
    var left = 0
    var eaten = 0
    val pet = Mover(9, 15)
    var wantDx = -1; var wantDy = 0
    var face = -1
    val ghosts = List(4) { Ghost(it) }
    var level = 1; var score = 0; var lives = 3
    private var extraLife = false
    var phase = Phase.READY; var phaseT = 2f
    private var modeIdx = 0; private var modeT = 0f
    var fright = 0f; private var combo = 0
    private var roundT = 0f
    private var freeze = 0f
    var bonusT = 0f
    var t = 0f
    val pops = ArrayList<Pop>()
    var onOver: (Int) -> Unit = {}
    /** 音效回调（GameAudio.play） */
    var sfx: (String) -> Unit = {}

    init { resetLevel() }

    fun restart() { level = 1; score = 0; lives = 3; extraLife = false; resetLevel() }

    fun resetLevel() {
        left = 0; eaten = 0
        for (y in 0 until PH) for (x in 0 until PW) {
            bean[y][x] = when (rows[y][x]) { '.' -> 1; 'o' -> 2; else -> 0 }
            if (bean[y][x] > 0) left++
        }
        bonusT = 0f
        resetRound()
    }

    private fun resetRound() {
        pet.set(9, 15, 0, 0); wantDx = -1; wantDy = 0; face = -1; held = false; steps = 0
        ghosts.forEach { g ->
            g.scared = false; g.pendingRev = false
            if (g.id == 0) { g.st = GS.ACTIVE; g.m.set(9, 7, -1, 0) }
            else { g.st = GS.HOUSE; g.fx = HOME[g.id].first; g.fy = HOME[g.id].second }
        }
        modeIdx = 0; modeT = 0f; fright = 0f; roundT = 0f; freeze = 0f
        phase = Phase.READY; phaseT = 2f
        sfx("pac_start")
    }

    /** 方向键按住中：按住就一直走，松开走到下一格就停 */
    var held = false
    /** 点一下方向键要走的格数（点一下走一格） */
    private var steps = 0

    /** 按下方向键（或在迷宫上按住滑动） */
    fun press(dx: Int, dy: Int) {
        steps = if (dx == wantDx && dy == wantDy) (steps + 1).coerceAtMost(3) else 1
        wantDx = dx; wantDy = dy; held = true
        // 往回走随时可以掉头
        if (pet.moving && dx == -pet.dx && dy == -pet.dy) pet.reverse()
    }

    /** 松开方向键 */
    fun release() { held = false }

    /** 兼容旧调用：等于点一下 */
    fun want(dx: Int, dy: Int) { press(dx, dy); release() }

    fun wall(x: Int, y: Int): Boolean {
        if (y == 9 && (x < 0 || x >= PW)) return false
        if (x < 0 || x >= PW || y < 0 || y >= PH) return true
        val c = rows[y][x]
        return c == '#' || c == '-'
    }

    private val petSpeed get() = minOf(5.4f + 0.25f * (level - 1), 7f) * if (fright > 0) 1.08f else 1f
    private val ghostSpeed get() = minOf(4.8f + 0.3f * (level - 1), 6.6f)
    private val frightDur get() = maxOf(1.5f, 7f - (level - 1))

    fun update(dt: Float) {
        t += dt
        pops.forEach { it.t += dt }
        pops.removeAll { it.t > 1f }
        when (phase) {
            Phase.READY -> { phaseT -= dt; if (phaseT <= 0) phase = Phase.PLAY }
            Phase.DYING -> {
                phaseT -= dt
                if (phaseT <= 0) {
                    lives--
                    if (lives <= 0) { phase = Phase.OVER; onOver(score) } else resetRound()
                }
            }
            Phase.CLEAR -> { phaseT -= dt; if (phaseT <= 0) { level++; resetLevel() } }
            Phase.OVER -> {}
            Phase.PLAY -> if (freeze > 0) freeze -= dt else play(dt)
        }
    }

    private fun move(m: Mover, dist0: Float, atCenter: () -> Unit) {
        var dist = dist0
        var guard = 0
        while (dist > 0 && guard++ < 6) {
            if (!m.moving) {
                atCenter()
                if (!m.moving) return
                continue
            }
            val rem = 1f - m.p
            if (dist < rem) { m.p += dist; return }
            dist -= rem
            m.tx += m.dx; m.ty += m.dy; m.p = 0f
            if (m.tx < 0) m.tx += PW
            if (m.tx >= PW) m.tx -= PW
            atCenter()
        }
    }

    private fun play(dt: Float) {
        roundT += dt
        if (fright > 0) {
            fright -= dt
            if (fright <= 0) ghosts.forEach { it.scared = false }
        } else {
            modeT += dt
            if (modeT >= MODES[modeIdx]) {
                modeT = 0f; modeIdx = minOf(modeIdx + 1, MODES.size - 1)
                ghosts.forEach { if (it.st == GS.ACTIVE) it.pendingRev = true }
            }
        }
        move(pet, petSpeed * dt) { petCenter() }
        if (phase != Phase.PLAY) return
        if (pet.dx != 0) face = pet.dx
        ghosts.forEach { stepGhost(it, dt) }
        if (bonusT > 0) {
            bonusT -= dt
            if (hypot(pet.x - 9f, pet.y - 11f) < 0.6f) {
                val (_, pts) = BONUS[minOf(level - 1, BONUS.size - 1)]
                score += pts; pops += Pop(9f, 11f, "+$pts"); bonusT = 0f
                sfx("pac_bonus")
            }
        }
        for (g in ghosts) {
            if (g.st != GS.ACTIVE) continue
            if (hypot(pet.x - g.x, pet.y - g.y) < 0.6f) {
                if (g.scared) {
                    g.st = GS.EATEN; g.scared = false
                    combo++
                    val pts = 200 shl minOf(combo - 1, 3)
                    score += pts; pops += Pop(g.x, g.y, "+$pts"); freeze = 0.4f
                    sfx("pac_eat_cloud")
                } else {
                    phase = Phase.DYING; phaseT = 2f
                    sfx("pac_die")
                    return
                }
            }
        }
        if (!extraLife && score >= 10000) { extraLife = true; lives++; pops += Pop(pet.x, pet.y, "+1 命") }
    }

    private fun petCenter() {
        val x = pet.tx; val y = pet.ty
        if (y in 0 until PH && x in 0 until PW && bean[y][x] > 0) {
            if (bean[y][x] == 1) { score += 10; sfx("pac_bean") }
            else {
                sfx("pac_bell")
                score += 50; fright = frightDur; combo = 0
                ghosts.forEach { g ->
                    if (g.st != GS.EATEN && g.st != GS.ENTERING) g.scared = true
                    if (g.st == GS.ACTIVE) g.pendingRev = true
                }
            }
            bean[y][x] = 0; left--; eaten++
            if (eaten == 50 || eaten == 110) bonusT = 9f
            if (left == 0) { phase = Phase.CLEAR; phaseT = 2.4f; sfx("pac_clear"); return }
        }
        // 按住方向键才连续走；点一下只走一格；什么都没按就停在格子中间
        val wantOpen = (wantDx != 0 || wantDy != 0) && !wall(x + wantDx, y + wantDy)
        when {
            (held || steps > 0) && wantOpen -> { pet.dx = wantDx; pet.dy = wantDy; if (steps > 0) steps-- }
            // 按着的方向是墙：先顺着原来的方向走到能拐弯的地方
            held && pet.moving && !wall(x + pet.dx, y + pet.dy) -> {}
            else -> { pet.dx = 0; pet.dy = 0; steps = 0 }
        }
    }

    private fun target(g: Ghost): Pair<Int, Int> {
        if (g.st == GS.EATEN) return 9 to 7
        val chase = modeIdx % 2 == 1
        if (!chase) return CORNERS[g.id]
        val px = pet.tx; val py = pet.ty
        val fdx = if (pet.moving) pet.dx else wantDx
        val fdy = if (pet.moving) pet.dy else wantDy
        return when (g.id) {
            0 -> px to py
            1 -> (px + 4 * fdx) to (py + 4 * fdy)
            2 -> {
                val vx = px + 2 * fdx; val vy = py + 2 * fdy
                val r = ghosts[0].m
                (2 * vx - r.tx) to (2 * vy - r.ty)
            }
            else -> if (hypot((g.m.tx - px).toFloat(), (g.m.ty - py).toFloat()) > 8f) px to py else CORNERS[3]
        }
    }

    private fun ghostCenter(g: Ghost) {
        val m = g.m
        if (g.st == GS.EATEN && m.tx == 9 && m.ty == 7) {
            g.st = GS.ENTERING; g.fx = 9f; g.fy = 7f; m.dx = 0; m.dy = 0
            return
        }
        val opts = DIR4.filter { (dx, dy) -> !(dx == -m.dx && dy == -m.dy && m.moving) && !wall(m.tx + dx, m.ty + dy) }
            .ifEmpty { listOf(-m.dx to -m.dy) }
        val pick = if (g.scared) opts[Random.nextInt(opts.size)] else {
            val (tx, ty) = target(g)
            opts.minBy { (dx, dy) ->
                val nx = m.tx + dx - tx; val ny = m.ty + dy - ty
                nx * nx + ny * ny
            }
        }
        m.dx = pick.first; m.dy = pick.second
    }

    private fun stepGhost(g: Ghost, dt: Float) {
        when (g.st) {
            GS.HOUSE -> {
                g.fy = 9f + sin(t * 6f + g.id) * 0.2f
                if (roundT >= RELEASE[g.id]) g.st = GS.LEAVING
            }
            GS.LEAVING -> {
                val sp = 3f * dt
                if (abs(g.fx - 9f) > 0.01f) g.fx += (9f - g.fx).coerceIn(-sp, sp)
                else {
                    g.fx = 9f; g.fy -= sp
                    if (g.fy <= 7f) { g.st = GS.ACTIVE; g.m.set(9, 7, -1, 0); g.pendingRev = false }
                }
            }
            GS.ENTERING -> {
                g.fy += 6f * dt
                if (g.fy >= 9f) { g.fy = 9f; g.st = GS.LEAVING }
            }
            GS.ACTIVE, GS.EATEN -> {
                if (g.pendingRev) { g.m.reverse(); g.pendingRev = false }
                val inTunnel = g.m.ty == 9 && (g.m.tx <= 3 || g.m.tx >= 15)
                val sp = when {
                    g.st == GS.EATEN -> 11f
                    inTunnel -> ghostSpeed * 0.5f
                    g.scared -> 3f
                    g.id == 0 && left < 20 -> ghostSpeed * 1.08f
                    else -> ghostSpeed
                }
                move(g.m, sp * dt) { ghostCenter(g) }
            }
        }
    }
}

private fun DrawScope.drawPac(g: PacGame, art: ArtSrc, look: PetLook) {
    val t = size.width / PW
    val bw = PW * t; val bh = PH * t
    art["pac_maze_${(g.level - 1) % 3}"].let { pix(it, 0f, 0f, bw, bh) }
    if (g.phase == Phase.CLEAR && (g.t * 6).toInt() % 2 == 0) drawRect(Color.White.copy(alpha = 0.45f), Offset.Zero, Size(bw, bh))
    val bean = art["pac_bean"]
    val bell = art["pac_bell_${(g.t * 3).toInt() % 2}"]
    for (y in 0 until PH) for (x in 0 until PW) {
        when (g.bean[y][x]) {
            1 -> pix(bean, x * t, y * t, t, t)
            2 -> pix(bell, x * t - t * 0.1f, y * t - t * 0.1f, t * 1.2f, t * 1.2f)
        }
    }
    if (g.bonusT > 0) {
        val key = BONUS[minOf(g.level - 1, BONUS.size - 1)].first
        pix(art["foods/$key"], 9 * t - t * 0.15f, 11 * t - t * 0.15f, t * 1.3f, t * 1.3f)
    }
    val f = (g.t * 6).toInt() % 2
    val showGhosts = g.phase != Phase.DYING || g.phaseT > 1.4f
    if (showGhosts && g.phase != Phase.CLEAR) g.ghosts.forEach { gh ->
        val dir = when {
            gh.st == GS.ACTIVE || gh.st == GS.EATEN -> when {
                gh.m.dx < 0 -> "l"; gh.m.dx > 0 -> "r"; gh.m.dy < 0 -> "u"; else -> "d"
            }
            gh.st == GS.HOUSE -> if (gh.fy < 9f) "u" else "d"
            else -> "u"
        }
        val name = when {
            gh.st == GS.EATEN || gh.st == GS.ENTERING -> "pac_drop_$dir"
            gh.scared -> if (g.fright < 2f && (g.t * 8).toInt() % 2 == 0) "pac_flash_$f" else "pac_scared_$f"
            else -> "pac_cloud_${gh.id}_${dir}_$f"
        }
        val s = t * 1.35f
        pix(art[name], (gh.x + 0.5f) * t - s / 2, (gh.y + 0.5f) * t - s / 2, s, s)
    }
    val ps = t * 1.85f
    val action = when (g.phase) {
        Phase.DYING -> if (g.phaseT > 1.4f) "idle" else "lift"
        Phase.CLEAR -> "energetic"
        Phase.READY, Phase.OVER -> "idle"
        Phase.PLAY -> if (g.pet.moving) "walk" else "idle"
    }
    val cx = (g.pet.x + 0.5f) * t; val cy = (g.pet.y + 0.5f) * t
    look.draw(this, action, g.t, cx - ps / 2, cy - ps * 0.6f, ps, flipX = g.face > 0,
        alpha = if (g.phase == Phase.DYING && g.phaseT < 0.5f) g.phaseT * 2 else 1f)
    // 穿过通道时另一侧也画一份
    if (g.pet.x < 0.5f || g.pet.x > PW - 1.5f) {
        val ox = if (g.pet.x < 0.5f) bw else -bw
        look.draw(this, action, g.t, cx - ps / 2 + ox, cy - ps * 0.6f, ps, flipX = g.face > 0)
    }
    g.pops.forEach { p ->
        gameText(p.text, (p.x + 0.5f) * t, (p.y + 0.3f) * t - p.t * t, t * 0.62f, 0xFFFFD45C.toInt())
    }
}

@Composable
fun PacGamePage() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val pet = PetSim.outing(cfg)
    val species = pet?.species ?: "cat"
    val worn = pet?.worn ?: emptyList()
    val look = remember(species, worn) { PetLook(ctx, species, worn) }
    val art = remember { ArtSrc(ctx) }
    val game = remember {
        PacGame(GameArt.text(ctx, "pac_maze.txt").lines().filter { it.isNotEmpty() })
    }
    var started by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    game.onOver = { newBest = GameScores.submit("pac", it) }
    game.sfx = { GameAudio.play(it) }
    GameMusic("pac_bgm.ogg", started && !paused && game.phase != Phase.OVER)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L && started && !paused) game.update(((now - last) / 1e9f).coerceAtMost(1 / 20f))
            last = now; tick = now
        }
    }
    PlayTimeReward(started && !paused && game.phase != Phase.OVER)
    PageScaffold("萌宠吃豆", scroll = false) {
        @Suppress("UNUSED_EXPRESSION") tick
        HRow {
            Text("分数 ${game.score}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
            Text("最高 ${maxOf(GameScores.best("pac"), game.score)}", fontSize = 13.sp, color = P.sub)
            Spacer(Modifier.weight(1f))
            repeat(game.lives.coerceAtMost(5)) { PixelImg("ui_heart", 18.dp) }
            Text("第 ${game.level} 关", fontSize = 13.sp, color = P.sub)
            PauseChip(paused, started && game.phase != Phase.OVER) { paused = !paused }
            VibrateToggle()
            SoundToggle()
        }
        Gap(6.dp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(PW.toFloat() / PH).clip(RoundedCornerShape(10.dp)).clipToBounds()) {
                var acc = Offset.Zero
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        // 在迷宫上滑：手指按着就一直往那边走，抬手停下
                        detectDragGestures(
                            onDragStart = { acc = Offset.Zero },
                            onDragEnd = { game.release() },
                            onDragCancel = { game.release() },
                        ) { ch, d ->
                            ch.consume()
                            acc += d
                            if (acc.getDistance() > 28f) {
                                if (abs(acc.x) > abs(acc.y)) game.press(sign(acc.x).toInt(), 0) else game.press(0, sign(acc.y).toInt())
                                acc = Offset.Zero
                            }
                        }
                    },
                ) {
                    @Suppress("UNUSED_EXPRESSION") tick
                    drawPac(game, art, look)
                }
                val msg = when {
                    !started -> null
                    paused -> "暂停中"
                    game.phase == Phase.READY -> if (game.level > 1 && game.eaten == 0) "第 ${game.level} 关 准备！" else "准备！"
                    game.phase == Phase.CLEAR -> "过关啦！"
                    else -> null
                }
                msg?.let {
                    Text(it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC4A2C3C))
                        .padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (!started || game.phase == Phase.OVER) {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(20.dp)).background(P.card).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (game.phase == Phase.OVER) {
                            Text("游戏结束", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                            Text("得分 ${game.score}" + if (newBest) "  新纪录！" else "", fontSize = 15.sp, color = P.pink)
                        } else {
                            PixelImg("ui_icon_pac", 56.dp)
                            Text("按住方向键一直走，点一下走一格；也可以在迷宫上按住滑动。\n吃光豆豆就过关；吃到铃铛豆可以反抓捣蛋云。",
                                fontSize = 13.sp, color = P.sub, textAlign = TextAlign.Center)
                        }
                        Gap(10.dp)
                        PrimaryButton(if (game.phase == Phase.OVER) "再来一局" else "开始") {
                            if (game.phase == Phase.OVER) { game.restart(); newBest = false }
                            started = true; paused = false
                        }
                    }
                }
            }
        }
        Gap(6.dp)
        // 十字方向键：四个键之间留空隙，按住连续走、点一下走一格
        val k = 50.dp
        val gap = 6.dp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PadButton("up", k) { if (it) game.press(0, -1) else game.release() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PadButton("left", k) { if (it) game.press(-1, 0) else game.release() }
                Spacer(Modifier.width(k + gap * 2))
                PadButton("right", k) { if (it) game.press(1, 0) else game.release() }
            }
            PadButton("down", k) { if (it) game.press(0, 1) else game.release() }
        }
    }
}
