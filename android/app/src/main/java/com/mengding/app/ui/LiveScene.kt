package com.mengding.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.PowerManager
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mengding.app.pet.Outfits
import com.mengding.app.pet.PetFx
import com.mengding.app.pet.Sprites
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 首页动态背景：千禧年 Blingee 像素风场景，按时间（和天气、节日）换，场景里的东西都在动。
 *
 * 场景由 美术/动态背景/s_*.py 画好，导出到 assets/scenes/<场景>/（静态层 PNG + scene.json 描述会动的东西），
 * 这里按 scene.json 每帧画到一张 240×166 的小图上再整像素放大，所以永远是清晰的像素块。
 * 规则和 美术/动态背景/preview.py 一一对应。宠物和背景同一像素密度、站在场景的站位上，带影子和场景灯光色，
 * 前景（花丛、栏杆……）压在宠物前面；隔一会儿慢慢走到另一个站位。
 */
object Scenes {
    const val W = 240
    const val H = 166

    /** 场景 key → 名字（设置里手动选用） */
    val ALL = linkedMapOf(
        "dawn_cottage" to "清晨·春日小屋", "river_spring" to "上午·樱花河畔", "village" to "下午·雨后小街",
        "autumn" to "傍晚·秋日林荫道", "night_river" to "夜晚·河畔摩天轮", "bedroom" to "深夜·少女房间",
        "snow_park" to "雪夜公园", "christmas" to "圣诞客厅",
    )

    /** 自动：下雪天 → 雪夜公园；12 月傍晚到深夜 → 圣诞客厅；其余按钟点 */
    fun auto(now: LocalDateTime, snow: Boolean): String {
        val h = now.hour + now.minute / 60.0
        if (snow) return "snow_park"
        if (now.monthValue == 12 && (h >= 17 || h < 5)) return "christmas"
        return when {
            h >= 5 && h < 8 -> "dawn_cottage"
            h >= 8 && h < 12 -> "river_spring"
            h >= 12 && h < 16 -> "village"
            h >= 16 && h < 19 -> "autumn"
            h >= 19 && h < 22 -> "night_river"
            else -> "bedroom"
        }
    }

    sealed class Item(val front: Boolean, val onlyRain: Boolean?)
    class Img(val bmp: Bitmap, val x: Int, val y: Int, f: Boolean, r: Boolean?) : Item(f, r)
    class Stars(val pts: List<DoubleArray>, val cols: IntArray, val speed: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Sparkles(val pts: List<DoubleArray>, val cols: IntArray, val speed: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Blink(val bmp: Bitmap, val x: Int, val y: Int, val period: Double, val duty: Double, val phase: Double, val fade: Double,
                f: Boolean, r: Boolean?) : Item(f, r)
    class Pulse(val bmp: Bitmap, val x: Int, val y: Int, val period: Double, val lo: Double, val hi: Double, val phase: Double,
                f: Boolean, r: Boolean?) : Item(f, r)
    class Frames(val bmp: Bitmap, val x: Int, val y: Int, val n: Int, val fps: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Drift(val bmp: Bitmap, val x: Double, val y: Int, val speed: Double, val x0: Double, val x1: Double,
                val bobAmp: Double, val bobPeriod: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Walker(val bmp: Bitmap, val n: Int, val y: Int, val speed: Double, val x0: Double, val x1: Double, val fps: Double,
                 f: Boolean, r: Boolean?) : Item(f, r)
    class Particles(val kind: String, val n: Int, val area: DoubleArray, val speed: Double, val wind: Double, val cols: IntArray,
                    val seed: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Water(val bmp: Bitmap, val x: Int, val y: Int, val amp: Double, val speed: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Sun(val bmp: Bitmap, val path: DoubleArray, val h0: Double, val h1: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Timed(val bmp: Bitmap, val x: Int, val y: Int, val on: Double, val off: Double, val fadeH: Double, f: Boolean, r: Boolean?) : Item(f, r)
    class Clock(val x: Double, val y: Double, val r: Double, val color: Int, f: Boolean, rr: Boolean?) : Item(f, rr)

    class Scene(
        val key: String, val name: String,
        /** 宠物站位：[x, 脚底 y, 缩放] */
        val spots: List<DoubleArray>,
        /** 宠物受场景灯光影响的颜色（乘到宠物上） */
        val tint: Int, val shadow: Int, val lightText: Boolean, val indoor: Boolean, val items: List<Item>,
    )

    private val cache = HashMap<String, Scene>()

    @Synchronized
    fun load(ctx: Context, key: String): Scene = cache.getOrPut(key) {
        val dir = "scenes/$key"
        val j = JSONObject(ctx.assets.open("$dir/scene.json").bufferedReader().readText())
        val bmps = HashMap<String, Bitmap>()
        fun bmp(name: String) = bmps.getOrPut(name) {
            ctx.assets.open("$dir/$name").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!! }
        }
        fun col(s: String) = android.graphics.Color.parseColor(s)
        val items = ArrayList<Item>()
        val arr = j.getJSONArray("items")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val f = o.optString("z") == "front"
            val r = when (o.optString("when")) { "rain" -> true; "dry" -> false; else -> null }
            val x = o.optInt("x"); val y = o.optInt("y")
            items += when (o.getString("type")) {
                "image" -> Img(bmp(o.getString("src")), x, y, f, r)
                "stars" -> o.getJSONArray("pts").let { p ->
                    val pts = (0 until p.length()).map { p.getJSONArray(it).let { a -> doubleArrayOf(a.getDouble(0), a.getDouble(1), a.getDouble(2)) } }
                    Stars(pts, IntArray(p.length()) { col(p.getJSONArray(it).getString(3)) }, o.optDouble("speed", 2.0), f, r)
                }
                "sparkles" -> o.getJSONArray("pts").let { p ->
                    val pts = (0 until p.length()).map {
                        p.getJSONArray(it).let { a -> doubleArrayOf(a.getDouble(0), a.getDouble(1), a.getDouble(2), a.getDouble(4)) }
                    }
                    Sparkles(pts, IntArray(p.length()) { col(p.getJSONArray(it).getString(3)) }, o.optDouble("speed", 1.8), f, r)
                }
                "blink" -> Blink(bmp(o.getString("src")), x, y, o.getDouble("period"), o.optDouble("duty", .5), o.optDouble("phase", 0.0),
                    o.optDouble("fade", 0.0), f, r)
                "pulse" -> Pulse(bmp(o.getString("src")), x, y, o.getDouble("period"), o.getDouble("min"), o.getDouble("max"),
                    o.optDouble("phase", 0.0), f, r)
                "frames" -> Frames(bmp(o.getString("src")), x, y, o.getInt("n"), o.getDouble("fps"), f, r)
                "drift" -> {
                    val w = o.getJSONArray("wrap"); val b = o.optJSONArray("bob")
                    Drift(bmp(o.getString("src")), o.getDouble("x"), y, o.getDouble("speed"), w.getDouble(0), w.getDouble(1),
                        b?.getDouble(0) ?: 0.0, b?.getDouble(1) ?: 1.0, f, r)
                }
                "walker" -> o.getJSONArray("range").let { rg ->
                    Walker(bmp(o.getString("src")), o.getInt("n"), y, o.getDouble("speed"), rg.getDouble(0), rg.getDouble(1), o.getDouble("fps"), f, r)
                }
                "particles" -> {
                    val a = o.getJSONArray("area"); val c = o.getJSONArray("colors")
                    Particles(o.getString("kind"), o.getInt("n"), DoubleArray(4) { a.getDouble(it) }, o.getDouble("speed"),
                        o.optDouble("wind", 0.0), IntArray(c.length()) { col(c.getString(it)) }, o.optDouble("seed", 1.0), f, r)
                }
                "water" -> Water(bmp(o.getString("src")), x, y, o.getDouble("amp"), o.getDouble("speed"), f, r)
                "sun" -> {
                    val p = o.getJSONArray("path"); val h = o.getJSONArray("hours")
                    Sun(bmp(o.getString("src")), DoubleArray(6) { p.getJSONArray(it / 2).getDouble(it % 2) }, h.getDouble(0), h.getDouble(1), f, r)
                }
                "timed" -> o.getJSONArray("on").let { on ->
                    Timed(bmp(o.getString("src")), x, y, on.getDouble(0), on.getDouble(1), o.optDouble("fade", 30.0) / 60, f, r)
                }
                "clock" -> Clock(o.getDouble("x"), o.getDouble("y"), o.getDouble("r"), col(o.getString("color")), f, r)
                else -> continue
            }
        }
        val spots = j.getJSONArray("spots").let { s -> (0 until s.length()).map { i -> s.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1), it.getDouble(2)) } } }
        val t = j.optJSONArray("tint") ?: JSONArray(listOf(1, 1, 1))
        val tint = android.graphics.Color.rgb((t.getDouble(0) * 255).roundToInt().coerceIn(0, 255),
            (t.getDouble(1) * 255).roundToInt().coerceIn(0, 255), (t.getDouble(2) * 255).roundToInt().coerceIn(0, 255))
        Scene(key, j.optString("name"), spots, tint, col(j.optString("shadow", "#40000000")), j.optString("text") == "light",
            j.optBoolean("indoor"), items)
    }
}

/** 每帧把场景画到两张小图上：宠物后面的（back）和宠物前面的（front） */
class SceneRenderer(val scene: Scenes.Scene) {
    val back: Bitmap = Bitmap.createBitmap(Scenes.W, Scenes.H, Bitmap.Config.ARGB_8888)
    val front: Bitmap = Bitmap.createBitmap(Scenes.W, Scenes.H, Bitmap.Config.ARGB_8888)
    private val bc = Canvas(back)
    private val fc = Canvas(front)
    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val src = Rect()
    private val dst = Rect()
    private val rainFx = Scenes.Particles("rain", 80, doubleArrayOf(0.0, -6.0, Scenes.W + 20.0, Scenes.H.toDouble()), 150.0, -35.0,
        intArrayOf(0xFFC6DCFF.toInt()), 1.0, false, null)

    /** t：动画时间（秒）；clock：现在几点（小时，带小数）；shadow：宠物脚下影子 [x, y, 缩放] */
    fun render(t: Double, clock: Double, rain: Boolean, shadow: DoubleArray?) {
        bc.drawColor(0, PorterDuff.Mode.CLEAR)
        fc.drawColor(0, PorterDuff.Mode.CLEAR)
        for (it in scene.items) if (!it.front) draw(bc, it, t, clock, rain)
        shadow?.let { (sx, sy, s) ->
            p.color = scene.shadow
            val rx = 11 * s
            for (dy in -2..2) for (dx in -rx.toInt()..rx.toInt()) {
                if ((dx / rx) * (dx / rx) + (dy / 2.2) * (dy / 2.2) <= 1) bc.drawRect(floor(sx + dx).toFloat(), floor(sy + dy).toFloat(),
                    floor(sx + dx).toFloat() + 1, floor(sy + dy).toFloat() + 1, p)
            }
        }
        for (it in scene.items) if (it.front) draw(fc, it, t, clock, rain)
        if (rain && !scene.indoor) {
            fc.drawColor(android.graphics.Color.argb(46, 24, 34, 66))
            particles(fc, rainFx, t)
        }
    }

    private operator fun DoubleArray.component1() = this[0]
    private operator fun DoubleArray.component2() = this[1]
    private operator fun DoubleArray.component3() = this[2]

    private fun px(c: Canvas, x: Double, y: Double, color: Int, a: Double = 1.0) {
        if (a <= .02) return
        p.color = color
        p.alpha = (min(1.0, a) * 255).toInt()
        val fx = floor(x).toFloat(); val fy = floor(y).toFloat()
        c.drawRect(fx, fy, fx + 1, fy + 1, p)
    }

    private fun blit(c: Canvas, b: Bitmap, x: Int, y: Int, a: Double = 1.0, sx: Int = 0, sw: Int = b.width, sy: Int = 0, sh: Int = b.height,
                     flip: Boolean = false) {
        if (a <= .02) return
        p.alpha = (min(1.0, a) * 255).toInt()
        src.set(sx, sy, sx + sw, sy + sh)
        dst.set(x, y, x + sw, y + sh)
        if (flip) {
            c.save(); c.scale(-1f, 1f, x + sw / 2f, 0f)
            c.drawBitmap(b, src, dst, p); c.restore()
        } else c.drawBitmap(b, src, dst, p)
        p.alpha = 255
    }

    private fun frac(v: Double) = v - floor(v)
    private fun mod(a: Double, b: Double) = ((a % b) + b) % b
    private fun rnd(i: Int, k: Int, seed: Double) = frac(sin(i * 12.9898 + k * 78.233 + seed * 1.7) * 43758.5453)

    private fun draw(c: Canvas, it: Scenes.Item, t: Double, clock: Double, rain: Boolean) {
        if (it.onlyRain == true && !rain || it.onlyRain == false && rain) return
        when (it) {
            is Scenes.Img -> blit(c, it.bmp, it.x, it.y)
            is Scenes.Stars -> it.pts.forEachIndexed { i, q ->
                val v = (sin(t * it.speed + q[2]) + 1) / 2
                if (v > .3) px(c, q[0], q[1], it.cols[i], v)
            }
            is Scenes.Sparkles -> it.pts.forEachIndexed { i, q ->
                val v = (sin(t * it.speed + q[2]) + 1) / 2
                val rr = (v * q[3]).roundToInt()
                if (rr >= 1) {
                    px(c, q[0], q[1], android.graphics.Color.WHITE)
                    for (k in 1..rr) {
                        val a = 1 - k / (rr + 1.0)
                        px(c, q[0] + k, q[1], it.cols[i], a); px(c, q[0] - k, q[1], it.cols[i], a)
                        px(c, q[0], q[1] + k, it.cols[i], a); px(c, q[0], q[1] - k, it.cols[i], a)
                    }
                    if (rr >= 3) for ((dx, dy) in listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)) px(c, q[0] + dx, q[1] + dy, it.cols[i], .45)
                }
            }
            is Scenes.Blink -> {
                val cyc = frac(t / it.period + it.phase)
                val a = when {
                    cyc >= it.duty -> 0.0
                    it.fade <= 0 -> 1.0
                    else -> min(1.0, min(cyc / it.fade, max(0.0, (it.duty - cyc) / it.fade)))
                }
                blit(c, it.bmp, it.x, it.y, a)
            }
            is Scenes.Pulse -> blit(c, it.bmp, it.x, it.y, it.lo + (it.hi - it.lo) * (sin(2 * PI * t / it.period + it.phase) + 1) / 2)
            is Scenes.Frames -> {
                val fw = it.bmp.width / it.n
                val k = (t * it.fps).toInt() % it.n
                blit(c, it.bmp, it.x, it.y, sx = k * fw, sw = fw)
            }
            is Scenes.Drift -> {
                val x = it.x0 + mod(it.x - it.x0 + it.speed * t, it.x1 - it.x0)
                val dy = if (it.bobAmp != 0.0) (it.bobAmp * sin(2 * PI * t / it.bobPeriod)).roundToInt() else 0
                blit(c, it.bmp, x.roundToInt(), it.y + dy)
            }
            is Scenes.Walker -> {
                val fw = it.bmp.width / it.n
                val span = it.x1 - it.x0
                val d = mod(it.speed * t, 2 * span)
                val right = d < span
                val x = if (right) it.x0 + d else it.x1 - (d - span)
                blit(c, it.bmp, x.roundToInt(), it.y, sx = ((t * it.fps).toInt() % it.n) * fw, sw = fw, flip = !right)
            }
            is Scenes.Particles -> particles(c, it, t)
            is Scenes.Water -> for (r in 0 until it.bmp.height) {
                val dx = (it.amp * sin(t * it.speed + r * .9)).roundToInt()
                blit(c, it.bmp, it.x + dx, it.y + r, sy = r, sh = 1)
            }
            is Scenes.Sun -> {
                val span = mod(it.h1 - it.h0, 24.0)
                val rel = mod(clock - it.h0, 24.0)
                val q = if (rel <= span) rel / span else if (rel < 12 + span / 2) 1.0 else 0.0
                val a = it.path
                val x = (1 - q) * (1 - q) * a[0] + 2 * (1 - q) * q * a[2] + q * q * a[4]
                val y = (1 - q) * (1 - q) * a[1] + 2 * (1 - q) * q * a[3] + q * q * a[5]
                blit(c, it.bmp, (x - it.bmp.width / 2.0).roundToInt(), (y - it.bmp.height / 2.0).roundToInt())
            }
            is Scenes.Timed -> {
                val since = mod(clock - it.on, 24.0)
                val dur = mod(it.off - it.on, 24.0)
                val a = if (since < dur) (if (it.fadeH > 0) min(1.0, min(since / it.fadeH, (dur - since) / it.fadeH)) else 1.0) else 0.0
                blit(c, it.bmp, it.x, it.y, a)
            }
            is Scenes.Clock -> {
                val m = frac(clock) * 60
                for ((ang, len) in listOf((clock % 12) / 12 to it.r * .5, m / 60 to it.r * .85)) {
                    val a = ang * 2 * PI
                    for (i in 0..len.toInt()) px(c, it.x + sin(a) * i, it.y - kotlin.math.cos(a) * i, it.color)
                }
            }
        }
    }

    private fun particles(c: Canvas, it: Scenes.Particles, t: Double) {
        val (x0, y0, x1) = it.area
        val y1 = it.area[3]
        val wd = x1 - x0; val hd = y1 - y0
        val sway = when (it.kind) { "snow" -> 3.0; "petal" -> 5.0; "leaf" -> 6.0; "smoke" -> 2.0; else -> 0.0 }
        for (i in 0 until it.n) {
            val a = rnd(i, 1, it.seed); val b = rnd(i, 2, it.seed); val cc = rnd(i, 3, it.seed); val d = rnd(i, 4, it.seed)
            val col = it.cols[i % it.cols.size]
            val sp = it.speed * (.6 + .8 * b)
            var x: Double; var y: Double; var al = 1.0
            if (it.kind == "firefly" || it.kind == "bokeh") {
                y = y0 + a * hd + 5 * sin(t * .7 + d * 6.28)
                x = x0 + mod(cc * wd + it.wind * t + 6 * sin(t * .5 + a * 6.28), wd)
                al = (sin(t * (1.5 + b) + d * 6.28) + 1) / 2
            } else {
                y = y0 + mod(a * hd + sp * t, hd)
                x = x0 + mod(cc * wd + it.wind * (.6 + .8 * b) * t + sway * sin(t * (1 + b) + d * 6.28), wd)
            }
            x = floor(x); y = floor(y)
            when (it.kind) {
                "snow" -> {
                    px(c, x, y, col)
                    if (b < .25) { px(c, x + 1, y, col, .6); px(c, x - 1, y, col, .6); px(c, x, y + 1, col, .6); px(c, x, y - 1, col, .6) }
                }
                "petal" -> { px(c, x, y, col); if (sin(t * 3 + d * 9) > 0) px(c, x + 1, y, col) else px(c, x, y + 1, col) }
                "leaf" -> { px(c, x, y, col); px(c, x + 1, y, col); px(c, x + if (sin(t * 2.5 + d * 9) > 0) 1 else 0, y + 1, col) }
                "rain" -> for (j in 0..3) px(c, x - j * it.wind / max(1.0, it.speed), y - j, col, .65)
                "firefly" -> {
                    px(c, x, y, col, al)
                    if (al > .7) { val q = al * .35; px(c, x + 1, y, col, q); px(c, x - 1, y, col, q); px(c, x, y + 1, col, q); px(c, x, y - 1, col, q) }
                }
                "bokeh" -> {
                    val r = if (b < .5) 1 else 2
                    for (dy in -r..r) for (dx in -r..r) if (abs(dx) + abs(dy) <= r) px(c, x + dx, y + dy, col, al * .45)
                }
                "smoke" -> {
                    val prog = if (it.speed < 0) 1 - (y - y0) / hd else (y - y0) / hd
                    for (dy in 0..1) for (dx in 0..1) px(c, x + dx, y + dy, col, (1 - prog) * .6)
                }
            }
        }
    }
}

/** 宠物在场景里慢慢走：每个场景 2–3 个站位，闲着的时候隔 15–40 秒走去另一个 */
private class PetWalk(val spots: List<DoubleArray>) {
    var x = spots[0][0]; var y = spots[0][1]; var s = spots[0][2]
    private var target = -1
    private var nextAt = 12.0
    private var at = 0
    private val rng = java.util.Random()
    val walking get() = target >= 0

    fun step(t: Double, dt: Double, canWalk: Boolean) {
        if (target >= 0) {
            val g = spots[target]
            val dx = g[0] - x; val dy = g[1] - y
            val dist = hypot(dx, dy)
            val v = 9.0 * dt
            if (dist <= v || !canWalk) {
                if (dist <= v) { x = g[0]; y = g[1]; s = g[2]; at = target }
                target = -1
                nextAt = t + 15 + rng.nextDouble() * 25
            } else {
                val k = v / dist
                x += dx * k; y += dy * k
                s += (g[2] - s) * k
            }
        } else if (canWalk && t >= nextAt && spots.size > 1) {
            target = (at + 1 + rng.nextInt(spots.size - 1)) % spots.size
        } else if (!canWalk) nextAt = max(nextAt, t + 6)
    }
}

/**
 * 动态场景 + 宠物。action 是宠物现在该播的动作（PetFx.actionFor 算好的）。
 * 离开首页、锁屏时自动停；省电模式下降到 15 帧。
 */
@Composable
fun LiveScene(sceneKey: String, species: String, action: String, outfits: List<String>, rain: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scene = remember(sceneKey) { Scenes.load(ctx, sceneKey) }
    val renderer = remember(scene) { SceneRenderer(scene) }
    val walk = remember(scene) { PetWalk(scene.spots) }
    val sheet = remember(species) { Sprites.sheet(ctx, species) }
    val wear = remember(species, outfits) { Outfits.ordered(ctx, outfits).mapNotNull { Sprites.wearSheet(ctx, species, it) } }
    val start = remember { System.nanoTime() }
    var tick by remember { mutableLongStateOf(start) }
    var last by remember { mutableLongStateOf(start) }
    val power = remember { ctx.getSystemService(PowerManager::class.java) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var prev = 0L
            while (true) {
                withFrameNanos { now ->
                    val gap = if (power?.isPowerSaveMode == true) 66_000_000L else 33_000_000L
                    if (now - prev >= gap) { prev = now; tick = now }
                }
            }
        }
    }
    val petPaint = remember { Paint().apply { isFilterBitmap = false; isAntiAlias = false } }
    val dstF = remember { RectF() }
    val srcR = remember { Rect() }
    ComposeCanvas(modifier) {
        val now = tick
        val t = (now - start) / 1e9
        val dt = ((now - last) / 1e9).coerceIn(0.0, .2)
        last = now
        val time = LocalDateTime.now()
        val clock = time.hour + time.minute / 60.0 + time.second / 3600.0
        val feed = PetFx.transient.value?.takeIf { it.until > com.mengding.app.logic.Time.now() }
        walk.step(t, dt, feed == null && action in setOf("idle", "energetic"))
        val act = if (walk.walking) "walk" else action
        renderer.render(t, clock, rain, doubleArrayOf(walk.x, walk.y, walk.s))

        // 整像素放大：底边对齐（地面一定看得到），左右居中裁
        val k = max(size.width / Scenes.W, size.height / Scenes.H)
        val ox = (size.width - Scenes.W * k) / 2
        val oy = size.height - Scenes.H * k
        drawIntoCanvas { cv ->
            val nc = cv.nativeCanvas
            dstF.set(ox, oy, ox + Scenes.W * k, oy + Scenes.H * k)
            nc.drawBitmap(renderer.back, null, dstF, petPaint)
            // 宠物：和背景同一像素网格
            val s = walk.s
            val a = Sprites.action(act)
            val frame = ((t * 1000 / a.delay).toInt()) % Sprites.frames
            val left = (walk.x - 24 * s).roundToInt(); val top = (walk.y - 44 * s).roundToInt()
            dstF.set(ox + left * k, oy + top * k, ox + (left + 48 * s).toFloat() * k, oy + (top + 48 * s).toFloat() * k)
            val sz = Sprites.size
            srcR.set(frame * sz, a.row * sz, frame * sz + sz, a.row * sz + sz)
            petPaint.colorFilter = LightingColorFilter(scene.tint, 0)
            nc.drawBitmap(sheet, srcR, dstF, petPaint)
            wear.forEach { nc.drawBitmap(it, srcR, dstF, petPaint) }
            if (Sprites.holds(a.key)) {
                val fd = feed?.takeIf { it.action == a.key }
                Sprites.drawHeld(ctx, nc, species, a.key, frame, fd?.foodKey,
                    fd?.biteStage(com.mengding.app.logic.Time.now()) ?: Sprites.loopStage(frame), RectF(dstF))
            }
            petPaint.colorFilter = null
            dstF.set(ox, oy, ox + Scenes.W * k, oy + Scenes.H * k)
            nc.drawBitmap(renderer.front, null, dstF, petPaint)
        }
    }
}
