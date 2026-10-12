package com.mengding.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.Catalog
import com.mengding.app.logic.GameItems
import com.mengding.app.pet.Sprites
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/*
 * 萌宠连连看：玩法和常见的连连看一样。
 *   点两只一样的萌宠，如果能用不超过两个拐弯的折线连起来（线可以绕到棋盘外面一圈），两只就一起消掉；
 *   全部消完过关，时间用完失败；没有能连的就自动打乱。
 *   难度越高，萌宠种类越多、时间越短：简单 6 种 180 秒 → 无人生还 15 种 100 秒。
 *   道具：提示（标出一对能连的）、打乱、炸弹（直接炸掉一对）、冻结时间（10 秒不走表）。
 *   道具在商店"游戏"里用萌点买；游戏里道具用完可以临时买，价格是商店的 1.2 倍。
 */

internal enum class LinkDiff(val key: String, val label: String, val cols: Int, val rows: Int, val kinds: Int, val time: Int, val reward: Int) {
    EASY("easy", "简单", 6, 8, 6, 180, 2),
    NORMAL("normal", "普通", 7, 10, 9, 150, 4),
    HARD("hard", "困难", 8, 10, 12, 120, 6),
    DOOM("doom", "无人生还", 8, 12, 15, 100, 10),
}

internal enum class LinkState { PLAY, WIN, LOSE }

internal data class Cell(val r: Int, val c: Int)

internal class LinkFx(val path: List<Cell>, val removed: List<Pair<Cell, String>>, val bomb: Boolean = false, var t: Float = 0f)

internal class LinkGame(val d: LinkDiff, kinds: List<String>, private val rnd: Random = Random.Default) {
    val R = d.rows
    val C = d.cols
    val grid: Array<Array<String?>> = Array(R) { arrayOfNulls<String>(C) }
    var sel: Cell? = null
    var timeLeft = d.time.toFloat()
    var frozen = 0f
    var score = 0
    var combo = 0
    private var lastMatch = -10f
    var t = 0f
    var state = LinkState.PLAY
    val fx = ArrayList<LinkFx>()
    var hint: Pair<Cell, Cell>? = null
    var hintT = 0f
    /** 刚刚连错扣掉的秒数，显示 1 秒 */
    var penalty = 0f
    var penaltyT = 0f
    var message: String? = null
    var messageT = 0f
    var sfx: (String) -> Unit = {}

    init {
        val pairs = R * C / 2
        val tiles = ArrayList<String>()
        for (i in 0 until pairs) { val k = kinds[i % kinds.size]; tiles += k; tiles += k }
        tiles.shuffle(rnd)
        var i = 0
        for (r in 0 until R) for (c in 0 until C) grid[r][c] = tiles[i++]
        if (findPair() == null) shuffle(free = true)
    }

    val left: Int get() = grid.sumOf { row -> row.count { it != null } }

    private fun empty(r: Int, c: Int) = r < 0 || r >= R || c < 0 || c >= C || grid[r][c] == null

    /** 同一行或同一列，中间（不含两端）全空 */
    private fun clear(a: Cell, b: Cell): Boolean {
        if (a.r == b.r) {
            for (c in minOf(a.c, b.c) + 1 until maxOf(a.c, b.c)) if (!empty(a.r, c)) return false
            return true
        }
        if (a.c == b.c) {
            for (r in minOf(a.r, b.r) + 1 until maxOf(a.r, b.r)) if (!empty(r, a.c)) return false
            return true
        }
        return false
    }

    /** 两只能不能连：返回折线经过的点（最多两个拐弯），连不上返回 null */
    fun path(a: Cell, b: Cell): List<Cell>? {
        if (a == b) return null
        val ka = grid.getOrNull(a.r)?.getOrNull(a.c) ?: return null
        if (ka != grid.getOrNull(b.r)?.getOrNull(b.c)) return null
        if ((a.r == b.r || a.c == b.c) && clear(a, b)) return listOf(a, b)
        for (k in listOf(Cell(a.r, b.c), Cell(b.r, a.c))) {
            if (empty(k.r, k.c) && clear(a, k) && clear(k, b)) return listOf(a, k, b)
        }
        var best: List<Cell>? = null
        fun consider(p1: Cell, p2: Cell) {
            if (p1 == a || p2 == b || !empty(p1.r, p1.c) || !empty(p2.r, p2.c)) return
            if (!clear(a, p1) || !clear(p1, p2) || !clear(p2, b)) return
            val len = abs(p1.r - a.r) + abs(p1.c - a.c) + abs(p2.r - p1.r) + abs(p2.c - p1.c) + abs(b.r - p2.r) + abs(b.c - p2.c)
            val cur = best
            if (cur == null || len < pathLen(cur)) best = listOf(a, p1, p2, b)
        }
        for (c in -1..C) consider(Cell(a.r, c), Cell(b.r, c))
        for (r in -1..R) consider(Cell(r, a.c), Cell(r, b.c))
        return best
    }

    private fun pathLen(p: List<Cell>) = p.zipWithNext().sumOf { (x, y) -> abs(x.r - y.r) + abs(x.c - y.c) }

    fun findPair(): Pair<Cell, Cell>? {
        val cells = ArrayList<Cell>()
        for (r in 0 until R) for (c in 0 until C) if (grid[r][c] != null) cells += Cell(r, c)
        for (i in cells.indices) for (j in i + 1 until cells.size) {
            if (grid[cells[i].r][cells[i].c] == grid[cells[j].r][cells[j].c] && path(cells[i], cells[j]) != null) return cells[i] to cells[j]
        }
        return null
    }

    fun shuffle(free: Boolean = false) {
        val cells = ArrayList<Cell>()
        val vals = ArrayList<String>()
        for (r in 0 until R) for (c in 0 until C) grid[r][c]?.let { cells += Cell(r, c); vals += it }
        for (n in 0 until 40) {
            vals.shuffle(rnd)
            cells.forEachIndexed { i, k -> grid[k.r][k.c] = vals[i] }
            if (vals.isEmpty() || findPair() != null) break
        }
        sel = null; hint = null
        if (free) say("没有能连的了，自动打乱")
    }

    private fun say(s: String) { message = s; messageT = 1.6f }

    fun tap(k: Cell) {
        if (state != LinkState.PLAY || grid[k.r][k.c] == null) return
        val s = sel
        if (s == null || s == k) { sel = if (s == k) null else k; sfx("link_select"); return }
        val p = path(s, k)
        if (p == null) {
            // 连错（不是同一种，或者连不上）：扣掉剩余时间的 2%，两只都取消选中
            val lost = timeLeft * 0.02f
            timeLeft -= lost
            penaltyT = 1f; penalty = lost
            sfx("link_wrong")
            sel = null
            return
        }
        remove(listOf(s, k), p)
        combo = if (t - lastMatch < 3f) combo + 1 else 0
        lastMatch = t
        score += 10 + combo * 5
        sfx("link_match")
    }

    private fun remove(cells: List<Cell>, p: List<Cell>, bomb: Boolean = false) {
        fx += LinkFx(p, cells.map { it to grid[it.r][it.c]!! }, bomb)
        cells.forEach { grid[it.r][it.c] = null }
        sel = null; hint = null
        if (left == 0) {
            state = LinkState.WIN
            score += timeLeft.toInt() * 5
            sfx("link_win")
        } else if (findPair() == null) {
            shuffle(free = true)
        }
    }

    fun useHint(): Boolean {
        val p = findPair() ?: return false
        hint = p; hintT = 4f
        sfx("link_hint")
        return true
    }

    fun useShuffle() { shuffle(); sfx("link_shuffle"); say("打乱啦") }

    /** 炸弹：随便挑一种还剩下的萌宠，直接炸掉其中两只（不管能不能连） */
    fun useBomb(): Boolean {
        val byKind = HashMap<String, ArrayList<Cell>>()
        for (r in 0 until R) for (c in 0 until C) grid[r][c]?.let { byKind.getOrPut(it) { ArrayList() } += Cell(r, c) }
        val pick = byKind.values.filter { it.size >= 2 }.randomOrNull(rnd) ?: return false
        sfx("link_bomb")
        remove(pick.take(2), emptyList(), bomb = true)
        return true
    }

    fun useFreeze() { frozen += 10f; sfx("link_freeze"); say("时间冻结 10 秒") }

    fun update(dt: Float) {
        t += dt
        fx.forEach { it.t += dt }
        fx.removeAll { it.t > 0.45f }
        if (hintT > 0) { hintT -= dt; if (hintT <= 0) hint = null }
        if (messageT > 0) messageT -= dt
        if (penaltyT > 0) penaltyT -= dt
        if (state != LinkState.PLAY) return
        if (frozen > 0) frozen = (frozen - dt).coerceAtLeast(0f)
        else {
            timeLeft -= dt
            if (timeLeft <= 0) { timeLeft = 0f; state = LinkState.LOSE; sfx("link_lose") }
        }
    }
}

private fun DrawScope.drawLink(g: LinkGame, tile: Float, ox: Float, oy: Float, art: ArtSrc, sheets: Map<String, ImageBitmap>) {
    val s = Sprites.size
    val idleRow = Sprites.action("idle").row
    val happyRow = Sprites.action("energetic").row
    val hintCells = g.hint?.toList().orEmpty()
    val blink = (g.t * 4).toInt() % 2 == 0
    for (r in 0 until g.R) for (c in 0 until g.C) {
        val k = g.grid[r][c] ?: continue
        val x = ox + c * tile; val y = oy + r * tile
        val cell = Cell(r, c)
        val bg = when {
            cell == g.sel -> "link_tile_sel"
            cell in hintCells && blink -> "link_tile_hint"
            else -> "link_tile"
        }
        pix(art[bg], x, y, tile, tile)
        if (cell in hintCells) {
            // 提示：粉色粗框一闪一闪
            drawRoundRect(Color(0xFFFF5C8A).copy(alpha = if (blink) 1f else .45f), Offset(x + 1.5f, y + 1.5f),
                androidx.compose.ui.geometry.Size(tile - 3f, tile - 3f), androidx.compose.ui.geometry.CornerRadius(tile * .18f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = tile * .1f))
        }
        val sheet = sheets[k] ?: continue
        val sel = cell == g.sel
        val f = if (sel) ((g.t * 8).toInt() % Sprites.frames) else 0
        val pad = tile * 0.06f
        pix(sheet, x + pad, y + pad - tile * 0.04f, tile - pad * 2, tile - pad * 2,
            srcX = f * s, srcY = (if (sel) happyRow else idleRow) * s, srcW = s, srcH = s)
    }
    // 消掉的效果：连线 + 缩小消失
    g.fx.forEach { e ->
        val a = 1f - e.t / 0.45f
        if (e.path.size >= 2) {
            val pts = e.path.map { Offset(ox + (it.c + .5f) * tile, oy + (it.r + .5f) * tile) }
            pts.zipWithNext().forEach { (p, q) ->
                drawLine(Color(0xFF4A2C3C).copy(alpha = a), p, q, strokeWidth = tile * 0.16f, cap = StrokeCap.Round)
                drawLine(Color(0xFFFF7B9C).copy(alpha = a), p, q, strokeWidth = tile * 0.09f, cap = StrokeCap.Round)
            }
        }
        e.removed.forEach { (cell, k) ->
            val sc = 1f - e.t / 0.45f
            val cx = ox + (cell.c + .5f) * tile; val cy = oy + (cell.r + .5f) * tile
            val sz = tile * sc
            sheets[k]?.let { pix(it, cx - sz / 2, cy - sz / 2, sz, sz, alpha = a, srcX = 0, srcY = happyRow * s, srcW = s, srcH = s) }
            if (e.bomb) drawCircle(Color(0xFFFFB43C).copy(alpha = a * .6f), tile * (0.3f + e.t * 1.5f), Offset(cx, cy))
        }
    }
}

@Composable
fun LinkGamePage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val art = remember { ArtSrc(ctx) }
    val sheets = remember { Catalog.all.associate { it.key to Sprites.sheet(ctx, it.key).asImageBitmap() } }
    var game by remember { mutableStateOf<LinkGame?>(null) }
    var paused by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    var buying by remember { mutableStateOf<GameItems.Item?>(null) }
    var settled by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { GameAudio.init(ctx) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val g = game
            if (last != 0L && g != null && !paused && buying == null) g.update(((now - last) / 1e9f).coerceAtMost(0.1f))
            last = now; tick = now
        }
    }
    val g = game
    val playing = g != null && g.state == LinkState.PLAY && !paused
    PlayTimeReward(playing)
    GameMusic("link_bgm.mp3", playing)
    // 结束结算：记最高分，过关给萌点
    LaunchedEffect(g, g?.state) {
        if (g != null && g.state != LinkState.PLAY && !settled) {
            settled = true
            newBest = GameScores.submit("link_${g.d.key}", g.score)
            if (g.state == LinkState.WIN) {
                Store.update { it.copy(economy = it.economy.copy(points = it.economy.points + g.d.reward)) }
                toast("过关奖励 萌点 +${g.d.reward}")
            }
        }
    }

    fun start(d: LinkDiff) {
        val kinds = Catalog.all.map { it.key }.shuffled().take(d.kinds)
        game = LinkGame(d, kinds).also { it.sfx = { s -> GameAudio.play(s) } }
        paused = false; settled = false; newBest = false
    }

    fun useItem(item: GameItems.Item) {
        GameHaptics.tap(ctx)
        val gg = game ?: return
        if (gg.state != LinkState.PLAY) return
        if (!GameItems.take(item.key)) { buying = item; return }
        apply(gg, item, toast)
    }

    PageScaffold("萌宠连连看", scroll = false) {
        @Suppress("UNUSED_EXPRESSION") tick
        if (g == null) {
            DifficultyPicker(cfg.economy.gameBest) { start(it) }
            return@PageScaffold
        }
        HRow {
            Text("${g.d.label}", fontSize = 13.sp, color = P.sub)
            Text("分数 ${g.score}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
            Spacer(Modifier.weight(1f))
            if (g.penaltyT > 0) Text("-%.1f秒".format(g.penalty), fontSize = 12.sp, color = P.pink, fontWeight = FontWeight.Bold)
            Text(if (g.frozen > 0) "❄ ${ceil(g.frozen).toInt()}" else "⏱ ${ceil(g.timeLeft).toInt()}", fontSize = 15.sp,
                fontWeight = FontWeight.Bold, color = if (g.frozen > 0) P.blue else if (g.timeLeft < 20) P.pink else P.ink)
            VibrateToggle()
            SoundToggle()
        }
        Gap(4.dp)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.line)) {
            Box(Modifier.fillMaxWidth((g.timeLeft / g.d.time).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(if (g.frozen > 0) P.blue else if (g.timeLeft < 20) P.pink else P.mint))
        }
        Gap(6.dp)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            // 棋盘外面留半格给连线绕
            val tile = minOf(w / (g.C + 1f), h / (g.R + 1f))
            val ox = (w - tile * g.C) / 2; val oy = (h - tile * g.R) / 2
            Canvas(Modifier.fillMaxSize().pointerInput(g) {
                detectTapGestures { o ->
                    val c = ((o.x - ox) / tile).toInt(); val r = ((o.y - oy) / tile).toInt()
                    if (o.x >= ox && o.y >= oy && r in 0 until g.R && c in 0 until g.C) g.tap(Cell(r, c))
                }
            }) {
                @Suppress("UNUSED_EXPRESSION") tick
                drawLink(g, tile, ox, oy, art, sheets)
            }
            val msg = when {
                g.state == LinkState.PLAY && paused -> "暂停中"
                g.state == LinkState.PLAY && g.messageT > 0 -> g.message
                else -> null
            }
            msg?.let {
                Text(it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC4A2C3C))
                    .padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (g.state != LinkState.PLAY) {
                Column(
                    Modifier.align(Alignment.Center).padding(20.dp).clip(RoundedCornerShape(20.dp)).background(P.card).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (g.state == LinkState.WIN) "全部消完啦！" else "时间到", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Text("得分 ${g.score}" + if (newBest) "  新纪录！" else "", fontSize = 15.sp, color = P.pink)
                    if (g.state == LinkState.WIN) Text("过关奖励 萌点 +${g.d.reward}", fontSize = 13.sp, color = P.sub)
                    Gap(10.dp)
                    HRow {
                        PrimaryButton("再来一局") { start(g.d) }
                        GhostButton("换难度") { game = null }
                    }
                }
            }
        }
        Gap(6.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GameItems.all.forEach { it ->
                val have = cfg.economy.gameItems[it.key] ?: 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(16.dp))
                        .clickable { useItem(it) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        PixelImg(it.icon, 34.dp)
                        Text(if (have > 0) "$have" else "+", Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp))
                            .background(if (have > 0) P.mint else P.pink).padding(horizontal = 4.dp), fontSize = 10.sp, color = Color.White)
                    }
                    Text(it.name, fontSize = 12.sp, color = P.ink)
                }
            }
            GhostButton(if (paused) "继续" else "暂停", enabled = g.state == LinkState.PLAY) { paused = !paused }
        }
    }

    buying?.let { item ->
        val price = GameItems.tempPrice(item)
        AlertDialog(
            onDismissRequest = { buying = null },
            title = { Text("${item.name}用完了") },
            text = { Text("临时买一个马上用：$price 萌点（商店价 ${item.price} 的 1.2 倍）。\n去商店「游戏」里买更划算。\n我的萌点：${cfg.economy.points}") },
            confirmButton = {
                TextButton({
                    val gg = game
                    if (gg != null && GameItems.buyTemp(item)) { GameAudio.play("buy"); apply(gg, item, toast) }
                    else toast("萌点不够了")
                    buying = null
                }, enabled = cfg.economy.points >= price) { Text("买并使用") }
            },
            dismissButton = { TextButton({ buying = null }) { Text("算了") } },
        )
    }
}

private fun apply(g: LinkGame, item: GameItems.Item, toast: (String) -> Unit) {
    when (item.key) {
        "hint" -> if (!g.useHint()) { g.useShuffle() }
        "shuffle" -> g.useShuffle()
        "bomb" -> g.useBomb()
        "freeze" -> g.useFreeze()
    }
}

@Composable
private fun DifficultyPicker(best: Map<String, Int>, onPick: (LinkDiff) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelImg("ui_icon_link", 52.dp)
            Spacer(Modifier.size(10.dp))
            Text("点两只一样的萌宠，能用不超过两个拐弯的线连起来就消掉；线可以绕到棋盘外面。连错会扣掉剩余时间的 2%。全部消完过关。",
                Modifier.weight(1f), fontSize = 13.sp, color = P.sub)
            SoundToggle()
        }
        Gap(12.dp)
        val colors = listOf(P.mintSoft, P.blue.copy(alpha = .18f), P.pinkSoft, P.ink.copy(alpha = .12f))
        LinkDiff.entries.forEachIndexed { i, d ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(colors[i])
                    .clickable { onPick(d) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.ink)
                    Text("${d.kinds} 种萌宠 · ${d.cols}×${d.rows} 格 · ${d.time} 秒 · 过关 +${d.reward} 萌点", fontSize = 12.sp, color = P.sub)
                }
                Text("最高\n${best["link_${d.key}"] ?: 0}", fontSize = 12.sp, color = P.sub, textAlign = TextAlign.End)
            }
        }
        Text("道具（提示、打乱、炸弹、冻结时间）在「商店 → 游戏」里买；游戏里用完了也能临时买，价格是商店的 1.2 倍。",
            fontSize = 12.sp, color = P.sub)
    }
}
