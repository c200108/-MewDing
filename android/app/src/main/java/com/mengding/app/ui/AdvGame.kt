package com.mengding.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * 萌叮大冒险：横版跳跃闯关，玩法和超级马里奥一致。
 *   左右走，按住"跑"加速；按住"跳"越久跳得越高。
 *   顶礼物盒出萌点币 / 萌萌菇 / 魔力星；吃萌萌菇变大，变大后能顶碎饼干砖，受伤变回小个子；
 *   魔力星让宠物彩虹闪烁、无敌 10 秒，碰到敌人直接撞飞。
 *   毛栗球踩一下就扁；瓢瓢虫踩一下缩成壳，再碰一下把壳踢飞，飞出去的壳能撞倒别的敌人。
 *   掉进坑里、时间用完、小个子被敌人碰到都会掉一条命。碰到铃铛旗杆过关（越高分越多），剩余时间换分数。
 *   100 个萌点币换一条命。
 */

private const val VW = 16
private const val VH = 15
private const val EPS = 0.001f
internal enum class AP { READY, PLAY, GROW, DYING, CLEAR, OVER, WIN }

internal open class Body(var x: Float, var y: Float, var w: Float, var h: Float) {
    var vx = 0f; var vy = 0f
    var onGround = false
    fun overlaps(o: Body) = x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y
}

internal const val E_WALK = 0
internal const val E_FLAT = 1
internal const val E_SHELL = 2
internal const val E_SLIDE = 3
internal const val E_DEAD = 4

internal class Enemy(val kind: Char, x: Float, y: Float, w: Float) : Body(x, y, w, w) {
    var state = E_WALK
    var timer = 0f
    var active = false
    var grace = 0f
    init { vx = -1f }
}

internal class Item(val kind: Char, x: Float, val baseY: Float) : Body(x, baseY, 0.9f, 0.9f) {
    var emerge = 1f
}

internal class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var t: Float = 0f)
internal class Bump(val col: Int, val row: Int, var t: Float = 0f)
internal class APop(val x: Float, val y: Float, val text: String, var t: Float = 0f)

internal class AdvLevel(text: String) {
    val theme: String
    val name: String
    val g: Array<CharArray>
    val w: Int
    var startX = 2; var startY = 12
    var flagX = 0; var houseX = 0
    val spawns = ArrayList<Triple<Char, Int, Int>>()

    init {
        val lines = text.lines().filter { it.isNotEmpty() }
        val head = lines[0].split("|")
        theme = head[0]; name = head.getOrElse(1) { "" }
        g = lines.drop(1).take(VH).map { it.toCharArray() }.toTypedArray()
        w = g[0].size
        for (y in g.indices) for (x in 0 until w) {
            when (g[y][x]) {
                '@' -> { startX = x; startY = y; g[y][x] = '.' }
                'g', 'k' -> { spawns += Triple(g[y][x], x, y); g[y][x] = '.' }
                'F' -> flagX = x
                'H' -> { houseX = x; g[y][x] = '.' }
            }
        }
    }
}

internal class AdvGame(private val texts: List<String>) {
    var levelNo = 1
    lateinit var lv: AdvLevel
    val p = Body(0f, 0f, 0.7f, 0.9f)
    var big = false
    var face = 1
    var invuln = 0f
    var star = 0f
    var phase = AP.READY; var phaseT = 1.6f
    var camX = 0f
    var score = 0; var coins = 0; var lives = 3
    var time = 300; private var timeAcc = 0f
    val enemies = ArrayList<Enemy>()
    val items = ArrayList<Item>()
    val parts = ArrayList<Part>()
    val bumps = ArrayList<Bump>()
    val coinFx = ArrayList<Part>()
    val pops = ArrayList<APop>()
    var left = false; var right = false; var run = false; var jumpHeld = false
    private var jumpBuf = 0f; private var coyote = 0f
    private var stompCombo = 0
    var t = 0f
    var clearStep = 0; var hidden = false; var flagY = 2f
    private var tallyAcc = 0f
    var onClear: (Int) -> Unit = {}
    var onOver: (Int) -> Unit = {}
    /** 音效回调（GameAudio.play） */
    var sfx: (String) -> Unit = {}
    val levels get() = texts.size
    fun levelName(n: Int) = AdvLevel(texts[n - 1]).name

    fun newGame(n: Int) { score = 0; coins = 0; lives = 3; big = false; load(n) }

    fun load(n: Int) {
        levelNo = n
        lv = AdvLevel(texts[n - 1])
        enemies.clear(); items.clear(); parts.clear(); bumps.clear(); coinFx.clear(); pops.clear()
        lv.spawns.forEach { (k, x, y) ->
            val w = if (k == 'g') 0.8f else 0.85f
            enemies += Enemy(k, x + (1 - w) / 2, y + 1 - w, w)
        }
        p.h = if (big) 1.75f else 0.9f
        p.x = lv.startX + 0.15f; p.y = lv.startY + 1 - p.h
        p.vx = 0f; p.vy = 0f; face = 1
        invuln = 0f; star = 0f; camX = 0f
        time = 300; timeAcc = 0f
        clearStep = 0; hidden = false; flagY = 2f
        phase = AP.READY; phaseT = 1.6f
    }

    fun jumpPress(down: Boolean) {
        if (down && !jumpHeld) jumpBuf = 0.12f
        jumpHeld = down
    }

    fun cell(x: Int, y: Int): Char = if (y < 0 || y >= VH || x < 0 || x >= lv.w) '.' else lv.g[y][x]
    private fun solid(x: Int, y: Int): Boolean {
        if (x < 0 || x >= lv.w) return true
        if (y < 0 || y >= VH) return false
        return lv.g[y][x] in "#SB?M*XPF"
    }

    private fun moveX(b: Body, dx: Float): Boolean {
        b.x += dx
        val y0 = floor(b.y + EPS).toInt(); val y1 = floor(b.y + b.h - EPS).toInt()
        if (dx > 0) {
            val c = floor(b.x + b.w - EPS).toInt()
            for (r in y0..y1) if (solid(c, r)) { b.x = c - b.w; return true }
        } else if (dx < 0) {
            val c = floor(b.x + EPS).toInt()
            for (r in y0..y1) if (solid(c, r)) { b.x = (c + 1).toFloat(); return true }
        }
        return false
    }

    /** 竖直移动；向上撞到东西时返回撞到的那一格 (列, 行) */
    private fun moveY(b: Body, dy: Float): Pair<Int, Int>? {
        val prevBottom = b.y + b.h
        b.y += dy
        b.onGround = false
        val x0 = floor(b.x + EPS).toInt(); val x1 = floor(b.x + b.w - EPS).toInt()
        if (dy > 0) {
            val r = floor(b.y + b.h - EPS).toInt()
            for (c in x0..x1) {
                if (solid(c, r) || (cell(c, r) == 'u' && prevBottom <= r + 0.05f)) {
                    b.y = r - b.h; b.vy = 0f; b.onGround = true
                    return null
                }
            }
        } else if (dy < 0) {
            val r = floor(b.y + EPS).toInt()
            val hits = (x0..x1).filter { solid(it, r) }
            if (hits.isNotEmpty()) {
                b.y = (r + 1).toFloat(); b.vy = 0f
                val mid = floor(b.x + b.w / 2).toInt()
                return (if (mid in hits) mid else hits.first()) to r
            }
        }
        return null
    }

    fun update(dt: Float) {
        t += dt
        pops.forEach { it.t += dt }; pops.removeAll { it.t > 0.9f }
        bumps.forEach { it.t += dt }; bumps.removeAll { it.t > 0.2f }
        parts.forEach { it.t += dt; it.vy += 40f * dt; it.x += it.vx * dt; it.y += it.vy * dt }; parts.removeAll { it.y > VH + 2 }
        coinFx.forEach { it.t += dt; it.vy += 40f * dt; it.y += it.vy * dt }; coinFx.removeAll { it.t > 0.5f }
        when (phase) {
            AP.READY -> { phaseT -= dt; if (phaseT <= 0) phase = AP.PLAY }
            AP.GROW -> {
                phaseT -= dt
                if (phaseT <= 0) { phase = AP.PLAY }
            }
            AP.DYING -> {
                phaseT -= dt
                if (phaseT < 2.1f) { p.vy += 40f * dt; p.y += p.vy * dt }
                if (phaseT <= 0) {
                    lives--
                    if (lives <= 0) { phase = AP.OVER; onOver(score) } else { big = false; load(levelNo) }
                }
            }
            AP.CLEAR -> clear(dt)
            AP.PLAY -> {
                var rest = dt
                while (rest > 0 && phase == AP.PLAY) {
                    val h = minOf(rest, 1f / 120f)
                    step(h)
                    rest -= h
                }
                if (phase == AP.PLAY) interact()
            }
            AP.OVER, AP.WIN -> {}
        }
    }

    private fun step(h: Float) {
        // ---- 宠物
        val maxV = if (run) 8.5f else 5.2f
        val acc = if (p.onGround) 24f else 15f
        when {
            // 只在没到上限时加速；松开"跑"之后超出的速度在下面慢慢降下来
            right && !left -> { if (p.vx < maxV) p.vx = minOf(p.vx + (if (p.vx < 0) acc * 2f else acc) * h, maxV); face = 1 }
            left && !right -> { if (p.vx > -maxV) p.vx = maxOf(p.vx - (if (p.vx > 0) acc * 2f else acc) * h, -maxV); face = -1 }
            else -> {
                val dec = (if (p.onGround) 22f else 5f) * h
                p.vx = if (abs(p.vx) <= dec) 0f else p.vx - dec * Math.signum(p.vx)
            }
        }
        if (abs(p.vx) > maxV) p.vx -= Math.signum(p.vx) * minOf(abs(p.vx) - maxV, 20f * h)
        coyote = if (p.onGround) 0.08f else coyote - h
        jumpBuf -= h
        if (jumpBuf > 0 && coyote > 0) {
            p.vy = -(15.2f + abs(p.vx) * 0.2f)
            jumpBuf = 0f; coyote = 0f; p.onGround = false
            sfx("adv_jump")
        }
        p.vy = minOf(p.vy + (if (p.vy < 0 && jumpHeld) 26f else 62f) * h, 16f)
        if (moveX(p, p.vx * h)) p.vx = 0f
        if (p.x < camX) { p.x = camX; if (p.vx < 0) p.vx = 0f }
        moveY(p, p.vy * h)?.let { (c, r) -> bump(c, r) }
        if (p.onGround) stompCombo = 0
        // 吃币
        for (r in floor(p.y + EPS).toInt()..floor(p.y + p.h - EPS).toInt())
            for (c in floor(p.x + EPS).toInt()..floor(p.x + p.w - EPS).toInt())
                if (cell(c, r) == 'c') { lv.g[r][c] = '.'; addCoin() }
        if (p.y > VH + 0.5f) { die(); return }
        camX = (p.x + p.w / 2 - VW * 0.42f).coerceAtLeast(camX).coerceIn(0f, (lv.w - VW).toFloat())

        // ---- 敌人
        for (e in enemies) {
            if (!e.active) { if (e.x < camX + VW + 1) e.active = true else continue }
            e.grace -= h
            when (e.state) {
                E_FLAT -> { e.timer -= h; continue }
                E_DEAD -> { e.vy += 40f * h; e.x += e.vx * h; e.y += e.vy * h; continue }
                E_SHELL -> { e.timer += h; e.vx = 0f; if (e.timer > 8f) { e.state = E_WALK; e.vx = -1f } }
                E_WALK -> e.vx = Math.signum(if (e.vx == 0f) -1f else e.vx) * if (e.kind == 'g') 1.6f else 1.4f
                E_SLIDE -> e.vx = Math.signum(e.vx) * 10f
            }
            e.vy = minOf(e.vy + 50f * h, 16f)
            if (moveX(e, e.vx * h)) e.vx = -e.vx
            moveY(e, e.vy * h)
        }
        enemies.removeAll { (it.state == E_FLAT && it.timer <= 0f) || it.y > VH + 2 }
        // 滑行的壳撞倒别的敌人；走路的敌人互相碰到就掉头
        for (a in enemies) {
            if (!a.active || a.state == E_DEAD) continue
            for (b in enemies) {
                if (a === b || !b.active || b.state == E_DEAD || b.state == E_FLAT || !a.overlaps(b)) continue
                if (a.state == E_SLIDE) { knock(b); score += 200; pops += APop(b.x, b.y, "200") }
                else if (a.state == E_WALK && b.state == E_WALK && a.x < b.x) {
                    a.vx = -abs(a.vx); b.vx = abs(b.vx)
                }
            }
        }

        // ---- 道具
        for (it in items) {
            if (it.emerge > 0f) {
                it.emerge -= h / 0.6f
                it.y = it.baseY - (1f - it.emerge.coerceAtLeast(0f)) * 0.95f
                if (it.emerge <= 0f) it.vx = if (it.kind == 'M') 2.6f else 3.2f
                continue
            }
            it.vy = minOf(it.vy + (if (it.kind == '*') 30f else 50f) * h, 14f)
            if (moveX(it, it.vx * h)) it.vx = -it.vx
            moveY(it, it.vy * h)
            if (it.kind == '*' && it.onGround) it.vy = -11f
        }
        items.removeAll { it.y > VH + 1 }

        // ---- 时间
        timeAcc += h
        if (timeAcc >= 0.5f) {
            timeAcc -= 0.5f; time--
            if (time <= 0) { time = 0; die(); return }
        }
        if (invuln > 0) invuln -= h
        if (star > 0) star -= h

        // ---- 终点
        if (p.x + p.w >= lv.flagX + 0.45f) {
            val bottom = p.y + p.h
            val pts = (((12f - bottom) / 9f * 5000f).toInt() / 100 * 100).coerceIn(100, 5000)
            score += pts; pops += APop(lv.flagX.toFloat(), p.y, "$pts")
            p.x = lv.flagX + 0.45f - p.w; p.vx = 0f; p.vy = 0f
            phase = AP.CLEAR; clearStep = 0
            sfx("adv_clear")
        }
    }

    private fun interact() {
        for (e in enemies) {
            if (!e.active || e.state == E_DEAD || e.state == E_FLAT || !p.overlaps(e)) continue
            when {
                star > 0 -> { knock(e); score += 200; pops += APop(e.x, e.y, "200"); sfx("adv_stomp") }
                e.state == E_SHELL -> {
                    sfx("adv_kick")
                    e.state = E_SLIDE; e.grace = 0.3f
                    e.vx = if (p.x + p.w / 2 < e.x + e.w / 2) 10f else -10f
                    score += 400; pops += APop(e.x, e.y, "400")
                }
                p.vy > 0 && p.y + p.h - e.y < 0.6f -> {
                    stompCombo++
                    val pts = 100 shl minOf(stompCombo - 1, 5)
                    score += pts; pops += APop(e.x, e.y, "$pts")
                    if (e.kind == 'g') { e.state = E_FLAT; e.timer = 0.5f }
                    else { e.state = E_SHELL; e.timer = 0f; e.vx = 0f }
                    p.y = e.y - p.h
                    p.vy = if (jumpHeld) -13.5f else -9f
                    sfx("adv_stomp")
                }
                e.state == E_SLIDE && e.grace > 0 -> {}
                else -> hurt()
            }
            if (phase != AP.PLAY) return
        }
        for (it in items) {
            if (it.emerge > 0f || !p.overlaps(it)) continue
            it.y = 99f
            if (it.kind == 'M') {
                if (!big) {
                    big = true; p.y -= 1.75f - p.h; p.h = 1.75f
                    phase = AP.GROW; phaseT = 0.7f
                }
                sfx("adv_grow")
                score += 1000; pops += APop(p.x, p.y, "1000")
            } else {
                star = 10f; score += 1000; pops += APop(p.x, p.y, "1000")
                sfx("adv_star")
            }
        }
        items.removeAll { it.y > 50f }
    }

    private fun knock(e: Enemy) {
        e.state = E_DEAD; e.vy = -9f; e.vx = if (e.x > p.x) 2f else -2f
    }

    private fun hurt() {
        if (invuln > 0) return
        if (big) {
            big = false; p.y += p.h - 0.9f; p.h = 0.9f
            invuln = 2f
            phase = AP.GROW; phaseT = 0.6f
            sfx("adv_hurt")
        } else die()
    }

    private fun die() {
        if (phase == AP.DYING) return
        phase = AP.DYING; phaseT = 2.6f
        p.vy = -13f; big = false; star = 0f
        sfx("adv_die")
    }

    private fun addCoin() {
        coins++; score += 200
        sfx("adv_coin")
        if (coins >= 100) { coins -= 100; lives++; pops += APop(p.x, p.y, "+1 命"); sfx("adv_life") }
    }

    private fun bump(c: Int, r: Int) {
        if (r < 0) return
        when (lv.g[r][c]) {
            '?' -> { lv.g[r][c] = 'X'; addCoin(); coinFx += Part(c.toFloat(), r - 1f, 0f, -12f); bumps += Bump(c, r) }
            'M' -> { lv.g[r][c] = 'X'; items += Item('M', c + 0.05f, r.toFloat()); bumps += Bump(c, r); sfx("adv_item") }
            '*' -> { lv.g[r][c] = 'X'; items += Item('*', c + 0.05f, r.toFloat()); bumps += Bump(c, r); sfx("adv_item") }
            'B' -> if (big) {
                lv.g[r][c] = '.'; score += 50
                sfx("adv_break")
                for ((vx, vy) in listOf(-3f to -12f, 3f to -12f, -2.5f to -8f, 2.5f to -8f)) parts += Part(c + 0.25f, r + 0.25f, vx, vy)
            } else { bumps += Bump(c, r); sfx("adv_bump") }
            else -> { sfx("adv_bump"); return }
        }
        // 方块上面站着的敌人被顶飞，上面的币直接收下
        for (e in enemies) {
            if (e.state == E_DEAD || !e.active) continue
            if (abs(e.y + e.h - r) < 0.15f && e.x < c + 1 && e.x + e.w > c) { knock(e); score += 100; pops += APop(e.x, e.y, "100") }
        }
        if (cell(c, r - 1) == 'c') { lv.g[r - 1][c] = '.'; addCoin(); coinFx += Part(c.toFloat(), r - 1f, 0f, -12f) }
    }

    private fun clear(dt: Float) {
        when (clearStep) {
            0 -> {
                p.y = minOf(p.y + 8f * dt, 12f - p.h)
                flagY = minOf(flagY + 8f * dt, 11f)
                if (p.y >= 12f - p.h - 0.001f && flagY >= 11f) { clearStep = 1; face = 1 }
            }
            1 -> {
                p.vx = 3.5f
                p.vy = minOf(p.vy + 50f * dt, 16f)
                moveX(p, p.vx * dt); moveY(p, p.vy * dt)
                camX = (p.x + p.w / 2 - VW * 0.42f).coerceAtLeast(camX).coerceIn(0f, (lv.w - VW).toFloat())
                if (p.x >= lv.houseX + 2.1f) { hidden = true; clearStep = 2; sfx("adv_house") }
            }
            2 -> {
                tallyAcc += dt
                while (tallyAcc > 0.01f && time > 0) { tallyAcc -= 0.01f; time--; score += 50 }
                if (time <= 0) { clearStep = 3; phaseT = 1.4f }
            }
            3 -> {
                phaseT -= dt
                if (phaseT <= 0) {
                    onClear(levelNo)
                    if (levelNo < levels) load(levelNo + 1) else { phase = AP.WIN; onOver(score) }
                }
            }
        }
    }
}

private val SKY = mapOf(
    "town" to listOf(Color(0xFFA9DDF7), Color(0xFFE6F6FF)),
    "sweets" to listOf(Color(0xFFFFB38A), Color(0xFFFFE3C2)),
    "sky" to listOf(Color(0xFF8EC9F5), Color(0xFFDDF0FF)),
)
private val STAR_TINT = listOf(Color(0xFFFFD84A), Color(0xFFFF9EC4), Color(0xFF8FE3C8), Color(0xFFB9A6FF))

private fun DrawScope.drawAdv(g: AdvGame, art: ArtSrc, look: PetLook) {
    val lv = g.lv
    val t = size.width / VW
    val k = t / 16f
    drawRect(Brush.verticalGradient(SKY[lv.theme] ?: SKY.getValue("town")), Offset.Zero, Size(size.width, VH * t))
    for ((name, factor) in listOf("adv_far_${lv.theme}" to 0.2f, "adv_near_${lv.theme}" to 0.5f)) {
        val img = art[name]
        val w = img.width * k
        var x = -((g.camX * 16f * factor) % img.width) * k
        while (x < size.width) { pix(img, x, 0f, w, img.height * k); x += w }
    }
    val c0 = floor(g.camX).toInt(); val c1 = minOf(c0 + VW + 1, lv.w - 1)
    val ground = art["adv_ground_${lv.theme}"]
    val giftF = (g.t * 5).toInt() % 4
    for (col in c0..c1) for (row in 0 until VH) {
        val ch = lv.g[row][col]
        if (ch == '.') continue
        val x = (col - g.camX) * t
        var y = row * t
        g.bumps.firstOrNull { it.col == col && it.row == row }?.let { y -= sin(it.t / 0.2f * PI.toFloat()) * 0.35f * t }
        when (ch) {
            '#' -> {
                val m = (if (g.cell(col, row - 1) == '#') 1 else 0) or
                    (if (col + 1 >= lv.w || g.cell(col + 1, row) == '#') 2 else 0) or
                    (if (row + 1 >= VH || g.cell(col, row + 1) == '#') 4 else 0) or
                    (if (col - 1 < 0 || g.cell(col - 1, row) == '#') 8 else 0)
                pix(ground, x, y, t, t, srcX = m * 16, srcY = 0, srcW = 16, srcH = 16)
            }
            'S', 'F' -> pix(art["adv_stone"], x, y, t, t)
            'B' -> pix(art["adv_brick"], x, y, t, t)
            '?', 'M', '*' -> pix(art["adv_gift_$giftF"], x, y, t, t)
            'X' -> pix(art["adv_gift_used"], x, y, t, t)
            'c' -> pix(art["adv_coin_${(g.t * 6).toInt() % 4}"], x, y, t, t)
            'P' -> {
                var run = 0; var i = col - 1
                while (g.cell(i, row) == 'P') { run++; i-- }
                val idx = (if (g.cell(col, row - 1) == 'P') 2 else 0) + run % 2
                pix(art["adv_stump"], x, y, t, t, srcX = idx * 16, srcY = 0, srcW = 16, srcH = 16)
            }
            'u' -> {
                val l = g.cell(col - 1, row) == 'u'; val r = g.cell(col + 1, row) == 'u'
                val sx = if (!l) 0 else if (!r) 32 else 16
                pix(art["adv_cloud"], x, y, t, t, srcX = sx, srcY = 0, srcW = 16, srcH = 16)
            }
        }
    }
    // 终点：旗杆和小屋
    val fx = (lv.flagX - g.camX) * t
    if (fx > -2 * t && fx < size.width + t) {
        for (r in 2..11) pix(art["adv_pole"], fx, r * t, t, t)
        pix(art["adv_pole_top"], fx, t, t, t)
        pix(art["adv_flag"], fx - t * 15f / 16f, g.flagY * t, t, t)
    }
    val hx = (lv.houseX - g.camX) * t
    if (hx < size.width && hx > -5 * t) pix(art["adv_house"], hx, 13 * t - 5 * t, 5 * t, 5 * t)
    // 道具
    g.items.forEach { it ->
        val img = if (it.kind == 'M') art["adv_mush"] else art["adv_star_${(g.t * 8).toInt() % 4}"]
        pix(img, (it.x - 0.05f - g.camX) * t, (it.y + it.h - 1f) * t, t, t)
    }
    // 被盒子挡住的出场部分：用盒子再盖一次
    g.items.filter { it.emerge > 0f }.forEach { it ->
        val col = floor(it.x + 0.5f).toInt(); val row = it.baseY.toInt()
        pix(art["adv_gift_used"], (col - g.camX) * t, row * t, t, t)
    }
    // 敌人
    g.enemies.forEach { e ->
        if (!e.active) return@forEach
        val name = when {
            e.kind == 'g' && e.state == E_FLAT -> "adv_chestnut_flat"
            e.kind == 'g' -> "adv_chestnut_${(g.t * 5).toInt() % 2}"
            e.state == E_WALK -> "adv_bug_${(g.t * 5).toInt() % 2}"
            else -> "adv_shell"
        }
        pix(art[name], (e.x - (1 - e.w) / 2 - g.camX) * t, (e.y + e.h - 1f) * t, t, t,
            flipX = e.kind == 'k' && e.state == E_WALK && e.vx > 0, flipY = e.state == E_DEAD)
    }
    // 宠物
    if (!g.hidden) {
        val growing = g.phase == AP.GROW && (g.t * 10).toInt() % 2 == 0
        val bigNow = if (growing) !g.big else g.big
        val ps = if (bigNow) 2.3f * t else 1.45f * t
        val cx = (g.p.x + g.p.w / 2 - g.camX) * t
        val bottom = (g.p.y + g.p.h) * t + ps * 3f / 48f
        val action = when {
            g.phase == AP.DYING -> "lift"
            g.phase == AP.CLEAR && g.clearStep == 0 -> "lift"
            g.phase == AP.CLEAR -> "walk"
            !g.p.onGround && g.phase == AP.PLAY -> "energetic"
            abs(g.p.vx) > 0.3f -> "walk"
            else -> "idle"
        }
        val blink = g.invuln > 0 && (g.t * 12).toInt() % 2 == 0
        if (!blink) {
            look.draw(this, action, g.t, cx - ps / 2, bottom - ps, ps, flipX = g.face > 0)
            if (g.star > 0) {
                val tint = ColorFilter.tint(STAR_TINT[(g.t * 10).toInt() % 4], BlendMode.SrcAtop)
                look.draw(this, action, g.t, cx - ps / 2, bottom - ps, ps, flipX = g.face > 0, alpha = 0.5f, tint = tint)
            }
        }
    }
    g.parts.forEach { pt -> pix(art["adv_shard"], (pt.x - g.camX) * t, pt.y * t, t / 2, t / 2) }
    g.coinFx.forEach { c -> pix(art["adv_coin_${(c.t * 16).toInt() % 4}"], (c.x - g.camX) * t, c.y * t, t, t) }
    g.pops.forEach { p -> gameText(p.text, (p.x + 0.5f - g.camX) * t, p.y * t - p.t * t, t * 0.55f) }
}

@Composable
fun AdvGamePage() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val pet = PetSim.outing(cfg)
    val species = pet?.species ?: "cat"
    val worn = pet?.worn ?: emptyList()
    val look = remember(species, worn) { PetLook(ctx, species, worn) }
    val art = remember { ArtSrc(ctx) }
    val game = remember { AdvGame((1..3).map { GameArt.text(ctx, "adv_$it.txt") }).also { it.load(1) } }
    var started by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    game.onOver = { newBest = GameScores.submit("adv", it) }
    game.sfx = { GameAudio.play(it) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L && started && !paused) game.update(((now - last) / 1e9f).coerceAtMost(1 / 20f))
            last = now; tick = now
        }
    }
    val ended = game.phase == AP.OVER || game.phase == AP.WIN
    PlayTimeReward(started && !paused && !ended)
    GameMusic("adv_bgm.ogg", started && !paused && !ended)
    PageScaffold("萌叮大冒险", scroll = false) {
        @Suppress("UNUSED_EXPRESSION") tick
        HRow {
            Text("${game.score}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
            PixelImg("adv_coin_0", 18.dp)
            Text("×${game.coins}", fontSize = 13.sp, color = P.ink)
            Spacer(Modifier.weight(1f))
            Text("1-${game.levelNo} ${game.lv.name}", fontSize = 13.sp, color = P.sub)
            Text("⏱${game.time}", fontSize = 13.sp, color = if (game.time < 60) P.pink else P.ink)
            VibrateToggle()
            SoundToggle()
        }
        HRow {
            repeat(game.lives.coerceAtMost(6)) { PixelImg("ui_heart", 16.dp) }
            Spacer(Modifier.weight(1f))
            Text("最高 ${maxOf(GameScores.best("adv"), game.score)}", fontSize = 12.sp, color = P.sub)
            PauseChip(paused, started && !ended) { paused = !paused }
        }
        Gap(6.dp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(VW.toFloat() / VH).clip(RoundedCornerShape(10.dp)).clipToBounds()) {
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") tick
                    drawAdv(game, art, look)
                }
                val msg = when {
                    !started || ended -> null
                    paused -> "暂停中"
                    game.phase == AP.READY -> "1-${game.levelNo} ${game.lv.name}"
                    game.phase == AP.CLEAR && game.clearStep >= 2 -> "过关啦！"
                    else -> null
                }
                msg?.let {
                    Text(it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC4A2C3C))
                        .padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (!started || ended) {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp).clip(RoundedCornerShape(20.dp)).background(P.card).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (game.phase) {
                            AP.OVER -> {
                                Text("游戏结束", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                                Text("得分 ${game.score}" + if (newBest) "  新纪录！" else "", fontSize = 15.sp, color = P.pink)
                            }
                            AP.WIN -> {
                                Text("全部通关！", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                                Text("得分 ${game.score}" + if (newBest) "  新纪录！" else "", fontSize = 15.sp, color = P.pink)
                            }
                            else -> {
                                PixelImg("ui_icon_adv", 52.dp)
                                Text("左右走，按住 » 跑，按 ▲ 跳（按得越久跳得越高）。\n顶礼物盒、踩敌人，跑到铃铛旗杆过关，一共 ${game.levels} 关。",
                                    fontSize = 12.sp, color = P.sub, textAlign = TextAlign.Center)
                            }
                        }
                        Gap(10.dp)
                        // 不选关：总是从第一关开始，过关自动进下一关；命用完了就从第一关重来
                        PrimaryButton(when (game.phase) { AP.OVER -> "从第一关重来"; AP.WIN -> "再玩一遍"; else -> "开始" }) {
                            game.newGame(1); newBest = false; started = true; paused = false
                        }
                    }
                }
            }
        }
        Gap(8.dp)
        // 方向键在左、跑跳在右，两组之间留出大空隙；键也比原来小一点，不容易按错
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PadButton("left", 56.dp) { game.left = it }
            Spacer(Modifier.width(14.dp))
            PadButton("right", 56.dp) { game.right = it }
            Spacer(Modifier.weight(1f))
            PadButton("b", 52.dp) { game.run = it }
            Spacer(Modifier.width(18.dp))
            PadButton("a", 60.dp) { game.jumpPress(it) }
        }
        Spacer(Modifier.width(1.dp))
    }
}
