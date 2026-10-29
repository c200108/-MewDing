package com.mengding.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.mengding.app.pet.Outfits
import com.mengding.app.pet.PetFx
import com.mengding.app.pet.Sprites
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 首页动态背景：用户给的 2D 像素原画做底图，上面实时叠 5 层动态特效，按时间（和天气、节日）换。
 *
 * 底图由 美术/动态背景/compile_photo_scenes.py 从 美术/动态背景/原图/ 编出来（按 260×230 网格点采样、256 色），
 * 放在 assets/scenes/<场景>/：base.png 底图、water.png 水面遮罩、prop*.png 抠出来会动的物件、scene.json 描述。
 * 每帧在一张网格大小的小图上依次画：
 *   1. 底图（逐像素，不做任何平滑）；
 *   2. 水面：每 2 像素一条切片按 sin(y·0.2 + t·3.5)·2.5 水平错位，只在水面遮罩里取样，岸边不会被拉花；
 *   3. 动态物件：秋千绕顶端 ±4.5° 摆、天鹅船来回划并在船尾溅白色水花、大桥上车灯流动；
 *   4. 灯光：screen 叠加的彩色径向光晕随 sin(t·速度 + 相位) 呼吸缩放，亮到峰值时画像素十字星芒；
 *   5. 场景专属粒子：樱花瓣、雪花、旋转枫叶、雨丝、萤火虫、闪烁星星；
 * 然后整张小图按整像素点采样放大铺满相框（按场景焦点居中裁），宠物最后画在最上层。
 */
object Scenes {
    /** 场景 key → 名字（设置里手动选用；相框铭牌也显示这个） */
    val ALL = linkedMapOf(
        "dawn_cottage" to "清晨·春日小屋", "river_spring" to "上午·樱花天鹅游船", "village" to "下午·雨天小街",
        "autumn" to "傍晚·红枫大道", "night_bridge" to "夜晚·霓虹跨海大桥", "night_river" to "夜晚·铁塔摩天轮",
        "moon_swing" to "月夜·花拱秋千", "bedroom" to "深夜·千禧少女房间",
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
            h >= 19 && h < 20.5 -> "night_bridge"
            h >= 20.5 && h < 22 -> "night_river"
            h >= 22 -> "moon_swing"
            else -> "bedroom"
        }
    }

    /** 一盏灯：网格坐标、光晕半径、颜色、呼吸相位和速度、要不要十字星芒、亮度范围 */
    class Light(val x: Float, val y: Float, val r: Int, val color: Int, val phase: Double, val speed: Double, val star: Boolean,
                val lo: Double, val hi: Double, val glow: Bitmap)

    sealed class Prop
    class Swing(val bmp: Bitmap, val x: Float, val y: Float, val ax: Float, val ay: Float, val amp: Double, val period: Double) : Prop()
    class Boat(val bmp: Bitmap, val x: Int, val y: Int, val range: Double, val period: Double, val phase: Double) : Prop()
    class Cars(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val lanes: IntArray, val n: Int, val speed: Double) : Prop()

    class Particles(val kind: String, val n: Int, val area: DoubleArray, val speed: Double, val wind: Double, val cols: IntArray)

    /** 水面：从第 y0 行开始 rows 行，mask[r * w + x] 表示这个像素是水 */
    class Water(val y0: Int, val rows: Int, val mask: BooleanArray, val amp: Double, val freq: Double, val speed: Double, val strip: Int)

    class Scene(
        val key: String, val name: String, val w: Int, val h: Int,
        /** 相框比底图窄 / 矮时，以这个点为中心裁 */
        val focusX: Float, val focusY: Float,
        val base: IntArray, val water: Water?, val props: List<Prop>, val lights: List<Light>, val particles: List<Particles>,
        /** 宠物站位：[x, 脚底 y, 缩放] */
        val spots: List<DoubleArray>,
        /** 宠物受场景灯光影响的颜色（乘到宠物上） */
        val tint: Int, val shadow: Int, val lightText: Boolean, val indoor: Boolean,
    ) {
        /** 所有站位上宠物占的范围（网格坐标），裁相框时要整只露出来 */
        val petLeft = spots.minOf { it[0] - 24 * it[2] * PET_SCALE }.toFloat()
        val petRight = spots.maxOf { it[0] + 24 * it[2] * PET_SCALE }.toFloat()
        val petTop = spots.minOf { it[1] - 44 * it[2] * PET_SCALE }.toFloat()
        val petBottom = spots.maxOf { it[1] + 3 }.toFloat()
    }

    // 只留最近用过的两个场景（底图解码后每个约 0.3–0.4 MB），切场景时不反复解码，也不会越攒越多
    private val cache = object : LinkedHashMap<String, Scene>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Scene>?) = size > 2
    }

    @Synchronized
    fun load(ctx: Context, key: String): Scene = cache.getOrPut(key) { read(ctx, key) }

    private fun read(ctx: Context, key: String): Scene {
        val dir = "scenes/$key"
        val j = JSONObject(ctx.assets.open("$dir/scene.json").bufferedReader().use { it.readText() })
        fun bmp(name: String): Bitmap = ctx.assets.open("$dir/$name").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
            })!!
        }
        fun col(s: String) = android.graphics.Color.parseColor(s)
        val w = j.getInt("w"); val h = j.getInt("h")
        val base = IntArray(w * h)
        bmp(j.getString("base")).let { it.getPixels(base, 0, w, 0, 0, w, h); it.recycle() }

        val water = j.optJSONObject("water")?.let { o ->
            val m = bmp(o.getString("src"))
            val px = IntArray(m.width * m.height).also { m.getPixels(it, 0, m.width, 0, 0, m.width, m.height) }
            val rows = m.height
            m.recycle()
            Water(o.getInt("y"), rows, BooleanArray(px.size) { (px[it] and 0xFF) > 127 }, o.getDouble("amp"), o.getDouble("freq"),
                o.getDouble("speed"), o.optInt("strip", 2))
        }

        val props = ArrayList<Prop>()
        j.optJSONArray("props")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                props += when (o.getString("type")) {
                    "swing" -> Swing(bmp(o.getString("src")), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                        o.getDouble("ax").toFloat(), o.getDouble("ay").toFloat(), o.getDouble("amp"), o.getDouble("period"))
                    "boat" -> Boat(bmp(o.getString("src")), o.getInt("x"), o.getInt("y"), o.getDouble("range"), o.getDouble("period"),
                        o.optDouble("phase", 0.0))
                    "cars" -> {
                        val l = o.getJSONArray("line"); val ln = o.getJSONArray("lanes")
                        Cars(l.getJSONArray(0).getDouble(0), l.getJSONArray(0).getDouble(1), l.getJSONArray(1).getDouble(0),
                            l.getJSONArray(1).getDouble(1), IntArray(ln.length()) { ln.getInt(it) }, o.getInt("n"), o.getDouble("speed"))
                    }
                    else -> continue
                }
            }
        }

        val glows = HashMap<String, Bitmap>()
        val lights = ArrayList<Light>()
        j.optJSONArray("lights")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                val r = a.getInt(2)
                val c = col(a.getString(3))
                val glow = glows.getOrPut("$r/$c") { glowBitmap(r, c) }
                lights += Light(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), r, c, a.getDouble(4), a.getDouble(5), a.getInt(6) == 1,
                    a.getDouble(7), a.getDouble(8), glow)
            }
        }

        val particles = ArrayList<Particles>()
        j.optJSONArray("particles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.getJSONArray("area"); val c = o.getJSONArray("colors")
                particles += Particles(o.getString("kind"), o.getInt("n"), DoubleArray(4) { a.getDouble(it) }, o.getDouble("speed"),
                    o.optDouble("wind", 0.0), IntArray(c.length()) { col(c.getString(it)) })
            }
        }

        val spots = j.getJSONArray("spots").let { s ->
            (0 until s.length()).map { i -> s.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1), it.getDouble(2)) } }
        }
        val t = j.optJSONArray("tint") ?: JSONArray(listOf(1, 1, 1))
        val tint = android.graphics.Color.rgb((t.getDouble(0) * 255).roundToInt().coerceIn(0, 255),
            (t.getDouble(1) * 255).roundToInt().coerceIn(0, 255), (t.getDouble(2) * 255).roundToInt().coerceIn(0, 255))
        val focus = j.getJSONArray("focus")
        return Scene(key, j.optString("name"), w, h, focus.getDouble(0).toFloat(), focus.getDouble(1).toFloat(), base, water, props,
            lights, particles, spots, tint, col(j.optString("shadow", "#40000000")), j.optString("text") == "light",
            j.optBoolean("indoor"))
    }

    /** 一张彩色径向渐变光晕小图（中心亮、边缘透明），画的时候按呼吸缩放 */
    private fun glowBitmap(r: Int, color: Int): Bitmap {
        val d = 2 * r + 1
        val b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val c = color and 0xFFFFFF
        val p = Paint().apply {
            shader = RadialGradient(r + .5f, r + .5f, r + .5f,
                intArrayOf(0xFF000000.toInt() or c, 0x99000000.toInt() or c, 0x33000000 or c, c),
                floatArrayOf(0f, .25f, .6f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(b).drawCircle(r + .5f, r + .5f, r + .5f, p)
        return b
    }
}

/** 每帧把场景画到一张网格大小的小图上 */
class SceneRenderer(val scene: Scenes.Scene) {
    val frame: Bitmap = Bitmap.createBitmap(scene.w, scene.h, Bitmap.Config.ARGB_8888)
    private val px = IntArray(scene.w * scene.h)
    private val c = Canvas(frame)
    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val glowPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = false; xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val dst = RectF()
    private val rainFx = Scenes.Particles("rain", 70, doubleArrayOf(0.0, -8.0, scene.w + 20.0, scene.h.toDouble()), 150.0, -30.0,
        intArrayOf(0xFFC6DCFF.toInt()))

    /** t：动画时间（秒）；rain：外面在下雨；shadow：宠物脚下影子 [x, y, 缩放] */
    fun render(t: Double, rain: Boolean, shadow: DoubleArray?) {
        val s = scene
        // 1 + 2：底图和水面波纹直接在像素数组里做
        System.arraycopy(s.base, 0, px, 0, px.size)
        s.water?.let { wt ->
            val w = s.w
            for (r in 0 until wt.rows) {
                val y = wt.y0 + r
                if (y >= s.h) break
                val dx = (sin((y / wt.strip * wt.strip) * wt.freq + t * wt.speed) * wt.amp).roundToInt()
                if (dx == 0) continue
                val row = r * w
                val off = y * w
                for (x in 0 until w) {
                    if (!wt.mask[row + x]) continue
                    val sx = x + dx
                    if (sx in 0 until w && wt.mask[row + sx]) px[off + x] = s.base[off + sx]
                }
            }
        }
        frame.setPixels(px, 0, s.w, 0, 0, s.w, s.h)

        // 3：会动的物件
        for (it in s.props) prop(it, t)

        // 宠物脚下的影子
        shadow?.let { sh ->
            p.color = s.shadow
            val rx = 11 * sh[2]
            val ri = rx.toInt()
            for (dy in -2..2) for (dx in -ri..ri) {
                if ((dx / rx) * (dx / rx) + (dy / 2.2) * (dy / 2.2) <= 1) {
                    val x = floor(sh[0] + dx).toFloat(); val y = floor(sh[1] + dy).toFloat()
                    c.drawRect(x, y, x + 1, y + 1, p)
                }
            }
        }

        // 4：灯光呼吸光晕（screen 叠加）+ 峰值十字星芒
        for (l in s.lights) {
            val v = (sin(t * l.speed + l.phase) + 1) / 2
            val a = l.lo + (l.hi - l.lo) * v
            if (a > .02) {
                val rr = l.r * (.7f + .3f * v.toFloat())
                dst.set(l.x - rr, l.y - rr, l.x + rr + 1, l.y + rr + 1)
                glowPaint.alpha = (min(1.0, a) * 255).toInt()
                c.drawBitmap(l.glow, null, dst, glowPaint)
            }
            if (l.star && v > .82) starburst(l.x.toDouble(), l.y.toDouble(), 2 + ((v - .82) / .18 * 5).roundToInt(), l.color)
        }

        // 5：天气粒子
        for (it in s.particles) particles(it, t)
        if (rain && !s.indoor) {
            c.drawColor(android.graphics.Color.argb(40, 24, 34, 66))
            particles(rainFx, t)
        }
    }

    private fun dot(x: Double, y: Double, color: Int, a: Double = 1.0) {
        if (a <= .02) return
        p.color = color
        p.alpha = (min(1.0, a) * 255).toInt()
        val fx = floor(x).toFloat(); val fy = floor(y).toFloat()
        c.drawRect(fx, fy, fx + 1, fy + 1, p)
    }

    /** 像素十字星芒：白心，四条臂往外渐淡，够大时带四个斜角小点 */
    private fun starburst(x: Double, y: Double, arm: Int, color: Int) {
        dot(x, y, android.graphics.Color.WHITE)
        for (k in 1..arm) {
            val a = 1 - k / (arm + 1.0)
            val col = if (k == 1) android.graphics.Color.WHITE else color
            dot(x + k, y, col, a); dot(x - k, y, col, a); dot(x, y + k, col, a); dot(x, y - k, col, a)
        }
        if (arm >= 4) { dot(x + 1, y + 1, color, .5); dot(x - 1, y + 1, color, .5); dot(x + 1, y - 1, color, .5); dot(x - 1, y - 1, color, .5) }
    }

    private fun prop(it: Scenes.Prop, t: Double) {
        p.alpha = 255
        when (it) {
            is Scenes.Swing -> {
                val ang = it.amp * sin(2 * PI * t / it.period)
                c.save()
                c.rotate(ang.toFloat(), it.ax, it.ay)
                c.drawBitmap(it.bmp, it.x, it.y, p)
                c.restore()
            }
            is Scenes.Boat -> {
                val ph = 2 * PI * t / it.period + it.phase
                val off = (it.range * sin(ph)).roundToInt()
                val goingRight = cos(ph) > 0
                val x = it.x + off
                val bw = it.bmp.width; val bh = it.bmp.height
                // 原画里天鹅头朝左；往右划的时候水平翻过来
                if (goingRight) {
                    c.save(); c.scale(-1f, 1f, x + bw / 2f, 0f); c.drawBitmap(it.bmp, x.toFloat(), it.y.toFloat(), p); c.restore()
                } else c.drawBitmap(it.bmp, x.toFloat(), it.y.toFloat(), p)
                // 船尾白色像素水花
                val tailX = if (goingRight) x - 1.0 else x + bw.toDouble()
                val dir = if (goingRight) -1 else 1
                val wy = it.y + bh * .78
                val f = (t * 6).toInt()
                for (k in 0..3) {
                    val a = .85 - k * .2
                    dot(tailX + dir * k, wy + ((f + k) % 2), android.graphics.Color.WHITE, a)
                    if ((f + k) % 3 == 0) dot(tailX + dir * (k + 1), wy - 1, 0xFFE6F6FF.toInt(), a * .7)
                }
            }
            is Scenes.Cars -> {
                val dx = it.x1 - it.x0; val dy = it.y1 - it.y0
                val len = hypot(dx, dy)
                it.lanes.forEachIndexed { li, lane ->
                    val forward = li % 2 == 0
                    val col = if (forward) 0xFFFFF4C8.toInt() else 0xFFFF5A4A.toInt()
                    for (i in 0 until it.n) {
                        val d = mod(i * len / it.n + it.speed * t * (1 + .15 * ((i * 7) % 3)), len)
                        val q = if (forward) d / len else 1 - d / len
                        val x = it.x0 + dx * q; val y = it.y0 + dy * q + lane
                        val sx = if (forward) -dx / len else dx / len
                        val sy = if (forward) -dy / len else dy / len
                        dot(x, y, col); dot(x + sx, y + sy, col, .75); dot(x + 2 * sx, y + 2 * sy, col, .35)
                    }
                }
            }
        }
    }

    private fun frac(v: Double) = v - floor(v)
    private fun mod(a: Double, b: Double) = ((a % b) + b) % b
    private fun rnd(i: Int, k: Int, seed: Double) = frac(sin(i * 12.9898 + k * 78.233 + seed * 1.7) * 43758.5453)

    private fun particles(it: Scenes.Particles, t: Double) {
        val x0 = it.area[0]; val y0 = it.area[1]; val x1 = it.area[2]; val y1 = it.area[3]
        val wd = x1 - x0; val hd = y1 - y0
        val seed = it.kind.length + it.n * .37
        val sway = when (it.kind) { "snow" -> 3.0; "petal" -> 6.0; "leaf" -> 7.0; else -> 0.0 }
        for (i in 0 until it.n) {
            val a = rnd(i, 1, seed); val b = rnd(i, 2, seed); val cc = rnd(i, 3, seed); val d = rnd(i, 4, seed)
            val col = it.cols[i % it.cols.size]
            val sp = it.speed * (.6 + .8 * b)
            var x: Double; var y: Double; var al = 1.0
            if (it.kind == "firefly" || it.kind == "twinkle") {
                y = y0 + a * hd + (if (it.kind == "firefly") 5 * sin(t * .7 + d * 6.28) else 0.0)
                x = x0 + mod(cc * wd + it.wind * t + (if (it.kind == "firefly") 6 * sin(t * .5 + a * 6.28) else 0.0), wd)
                al = (sin(t * ((if (it.kind == "twinkle") it.speed else 1.5) + b) + d * 6.28) + 1) / 2
            } else {
                y = y0 + mod(a * hd + sp * t, hd)
                x = x0 + mod(cc * wd + it.wind * (.6 + .8 * b) * t + sway * sin(t * (1 + b) + d * 6.28), wd)
            }
            x = floor(x); y = floor(y)
            when (it.kind) {
                "snow" -> {
                    dot(x, y, col, .92)
                    if (b < .3) { dot(x + 1, y, col, .6); dot(x - 1, y, col, .6); dot(x, y + 1, col, .6); dot(x, y - 1, col, .6) }
                }
                // 双色樱花瓣：粉色瓣身 + 浅粉高光，翻飞时横竖交替
                "petal" -> {
                    val flip = sin(t * 3 + d * 9) > 0
                    dot(x, y, 0xFFFFB7CE.toInt()); dot(if (flip) x + 1 else x, if (flip) y else y + 1, 0xFFFFB7CE.toInt())
                    if (b < .6) dot(if (flip) x + 1 else x + 1, if (flip) y + 1 else y + 1, 0xFFFFF0F5.toInt())
                }
                // 枫叶：转着落，四个朝向轮换
                "leaf" -> {
                    when (((t * (2 + b * 2) + d * 4).toInt()) and 3) {
                        0 -> { dot(x, y, col); dot(x + 1, y, col); dot(x + 2, y, col); dot(x + 1, y + 1, col) }
                        1 -> { dot(x, y, col); dot(x + 1, y + 1, col); dot(x + 2, y + 2, col); dot(x, y + 2, col, .8) }
                        2 -> { dot(x + 1, y, col); dot(x + 1, y + 1, col); dot(x + 1, y + 2, col); dot(x, y + 1, col) }
                        else -> { dot(x + 2, y, col); dot(x + 1, y + 1, col); dot(x, y + 2, col); dot(x + 2, y + 2, col, .8) }
                    }
                }
                "rain" -> for (j in 0..3) dot(x - j * it.wind / max(1.0, it.speed), y - j, col, .6)
                "firefly" -> {
                    dot(x, y, col, al)
                    if (al > .7) { val q = al * .35; dot(x + 1, y, col, q); dot(x - 1, y, col, q); dot(x, y + 1, col, q); dot(x, y - 1, col, q) }
                }
                "twinkle" -> if (al > .55) {
                    dot(x, y, col, al)
                    if (al > .9) { val q = .5; dot(x + 1, y, col, q); dot(x - 1, y, col, q); dot(x, y + 1, col, q); dot(x, y - 1, col, q) }
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
    /** 脸朝右（精灵图本身朝左，朝右时镜像）；停下后保持最后走的方向 */
    var faceRight = false
        private set

    fun step(t: Double, dt: Double, canWalk: Boolean) {
        if (target >= 0) {
            val g = spots[target]
            val dx = g[0] - x; val dy = g[1] - y
            val dist = hypot(dx, dy)
            val v = 12.0 * dt
            if (dist <= v || !canWalk) {
                if (dist <= v) { x = g[0]; y = g[1]; s = g[2]; at = target }
                target = -1
                nextAt = t + 15 + rng.nextDouble() * 25
            } else {
                val k = v / dist
                if (kotlin.math.abs(dx) > .5) faceRight = dx > 0
                x += dx * k; y += dy * k
                s += (g[2] - s) * k
            }
        } else if (canWalk && t >= nextAt && spots.size > 1) {
            target = (at + 1 + rng.nextInt(spots.size - 1)) % spots.size
        } else if (!canWalk) nextAt = max(nextAt, t + 6)
    }
}

/** 场景里可以点的地方（画的时候每帧更新位置，外层点按时来查）：现在只有宠物项圈上的铃铛 */
class SceneTap {
    val bell = RectF()
}

/** 宠物比原画网格放大一点，手机上看得清、好点中 */
internal const val PET_SCALE = 1.25

/**
 * 动态场景 + 宠物。action 是宠物现在该播的动作（PetFx.actionFor 算好的）。
 * 摸摸 / 喂食的点按由外层 PetStage 接收；宠物画在最上层。离开首页、锁屏时自动停；省电模式下降到 15 帧。
 */
@Composable
fun LiveScene(sceneKey: String, species: String, action: String, outfits: List<String>, rain: Boolean, modifier: Modifier = Modifier,
              bubble: String? = null, bubbleStyle: String = "cream", tap: SceneTap? = null) {
    val ctx = LocalContext.current
    val scene = remember(sceneKey) { Scenes.load(ctx, sceneKey) }
    val renderer = remember(scene) { SceneRenderer(scene) }
    val walk = remember(scene) { PetWalk(scene.spots) }
    val tintFilter = remember(scene) { LightingColorFilter(scene.tint, 0) }
    val sheet = remember(species) { Sprites.sheet(ctx, species) }
    val emoteSheet = remember(species) { com.mengding.app.pet.Emotes.sheet(ctx, species) }
    // 气泡可能带像素表情标记（BubbleText）：拆成表情 + 正文；这个物种没有表情图就只画字
    val talkParts = remember(bubble) { com.mengding.app.logic.BubbleText.parse(bubble) }
    val wear = remember(species, outfits) { Outfits.ordered(ctx, outfits).mapNotNull { Sprites.wearSheet(ctx, species, it) } }
    val start = remember { System.nanoTime() }
    var tick by remember { mutableLongStateOf(start) }
    var last by remember { mutableLongStateOf(start) }
    val power = remember { ctx.getSystemService(PowerManager::class.java) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // 每帧取一次时间后歇一会儿，不在每个 vsync 都请求重画：平时约 30 帧，省电模式约 15 帧
            while (true) {
                tick = withFrameNanos { it }
                delay(if (power?.isPowerSaveMode == true) 60L else 28L)
            }
        }
    }
    val paint = remember { Paint().apply { isFilterBitmap = false; isAntiAlias = false } }
    val dstF = remember { RectF() }
    val heldF = remember { RectF() }
    val talk = remember { SceneBubble() }
    val talkBounds = remember { RectF() }
    val glow = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val srcR = remember { Rect() }
    ComposeCanvas(modifier) {
        val now = tick
        val t = (now - start) / 1e9
        val dt = ((now - last) / 1e9).coerceIn(0.0, .2)
        last = now
        val feed = PetFx.transient.value?.takeIf { it.until > com.mengding.app.logic.Time.now() }
        walk.step(t, dt, feed == null && action in setOf("idle", "energetic"))
        val act = if (walk.walking) "walk" else action
        renderer.render(t, rain, doubleArrayOf(walk.x, walk.y, walk.s * PET_SCALE))

        // 点采样放大铺满相框：多出来的部分以场景焦点为中心裁掉
        val w = scene.w; val h = scene.h
        val k = max(size.width / w, size.height / h)
        val vw = size.width / k; val vh = size.height / k
        // 以焦点为中心裁，但保证每个站位上的宠物整只都在框里，脚不压到底部铭牌（底下留 10%）
        var gx = scene.focusX - vw / 2
        var gy = scene.focusY - vh / 2
        gx = max(gx, scene.petRight - vw * .96f); gx = min(gx, scene.petLeft - vw * .04f)
        gy = max(gy, scene.petBottom - vh * .9f); gy = min(gy, scene.petTop - vh * .06f)
        gx = gx.coerceIn(0f, max(0f, w - vw))
        gy = gy.coerceIn(0f, max(0f, h - vh))
        val ox = -gx * k; val oy = -gy * k
        clipRect {
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                dstF.set(ox, oy, ox + w * k, oy + h * k)
                nc.drawBitmap(renderer.frame, null, dstF, paint)
                // 宠物：最上层，脚底对准站位
                val s = walk.s * PET_SCALE
                val a = Sprites.action(act)
                val frame = ((t * 1000 / a.delay).toInt()) % Sprites.frames
                val left = walk.x - 24 * s; val top = walk.y - 44 * s
                dstF.set((ox + left * k).toFloat(), (oy + top * k).toFloat(), (ox + (left + 48 * s) * k).toFloat(), (oy + (top + 48 * s) * k).toFloat())
                val sz = Sprites.size
                srcR.set(frame * sz, a.row * sz, frame * sz + sz, a.row * sz + sz)
                paint.colorFilter = tintFilter
                // 精灵图本身朝左，往右走时整只（连衣服、捧着的食物）镜像
                nc.save()
                if (walk.faceRight) nc.scale(-1f, 1f, dstF.centerX(), 0f)
                nc.drawBitmap(sheet, srcR, dstF, paint)
                wear.forEach { nc.drawBitmap(it, srcR, dstF, paint) }
                if (Sprites.holds(a.key)) {
                    val fd = feed?.takeIf { it.action == a.key }
                    heldF.set(dstF)
                    Sprites.drawHeld(ctx, nc, species, a.key, frame, fd?.foodKey,
                        fd?.biteStage(com.mengding.app.logic.Time.now()) ?: Sprites.loopStage(frame), heldF)
                }
                nc.restore()
                paint.colorFilter = null
                val ps = dstF.width() / Sprites.size
                val hd = Sprites.head(species, a.key, frame)
                val hx = hd?.get(0)?.plus(.5f) ?: (Sprites.size / 2f)
                val headX = if (walk.faceRight) dstF.right - hx * ps else dstF.left + hx * ps
                // 项圈铃铛：在头顶中线上、眼睛下面约 10 个宠物像素。点到这附近会叮一声（外层 PetStage 判断）
                val bellY = dstF.top + ((hd?.get(2) ?: 25) + 10) * ps
                tap?.bell?.set(headX - 9 * ps, bellY - 8 * ps, headX + 9 * ps, bellY + 8 * ps)
                // 刚「叮」过：铃铛上闪一圈金光，0.7 秒淡掉
                val since = com.mengding.app.logic.Time.now() - com.mengding.app.pet.Ding.flash.value
                if (since in 0..700) {
                    val al = 1f - since / 700f
                    glow.shader = android.graphics.RadialGradient(headX, bellY, 10 * ps,
                        intArrayOf(android.graphics.Color.argb((230 * al).toInt(), 255, 236, 140), android.graphics.Color.argb(0, 255, 210, 80)),
                        null, android.graphics.Shader.TileMode.CLAMP)
                    nc.drawCircle(headX, bellY, 10 * ps * (1.2f - .2f * al), glow)
                }
                // 说话气泡：跟着这一帧的头顶走，尾巴指向嘴边
                if (bubble != null) {
                    val headY = dstF.top + (hd?.get(1) ?: 8) * ps
                    // 左上名字牌、右上萌度牌盖在场景上面，气泡不往那一条里放
                    talkBounds.set(6.dp.toPx(), 50.dp.toPx(), size.width - 6.dp.toPx(), size.height - 26.dp.toPx())
                    val emo = talkParts.first?.takeIf { emoteSheet != null }
                    talk.draw(nc, talkParts.second, t, headX, headY, 30f * ps, 36f * ps, walk.faceRight, talkBounds,
                        density, fontScale * density, BubbleSkins.of(ctx, bubbleStyle),
                        emote = if (emo != null) emoteSheet else null, emoteCol = emo?.col ?: 0)
                } else talk.draw(nc, null, t, 0f, 0f, 0f, 0f, false, talkBounds, density, density)
            }
        }
    }
}
