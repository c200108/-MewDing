package com.mengding.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** 尾巴从气泡哪条边伸出去：DOWN 气泡在头顶，LEFT 气泡在宠物右边，RIGHT 气泡在宠物左边，UP 气泡在宠物下面 */
enum class TailDir { DOWN, LEFT, RIGHT, UP }

/** 气泡放在哪：左上角、尾巴方向、尾巴根部在那条边上的坐标（DOWN / UP 是 x，LEFT / RIGHT 是 y） */
data class BubblePlace(val left: Float, val top: Float, val tail: TailDir, val tailAt: Float)

/**
 * 气泡摆放规则（纯计算，单元测试直接测）。坐标都是画布像素。
 * 先试头顶、往脸朝的那边开；放不下就往背后那边开；头顶没地方就放到脸那侧旁边、再试背那侧；
 * 都不行退回头顶并压在可放范围的上沿（宠物太靠上时会盖住一点头，但不会出框）。
 */
object BubbleLayout {
    fun place(
        headX: Float, headY: Float, petW: Float, petH: Float, faceRight: Boolean,
        w: Float, h: Float, tail: Float, gap: Float, corner: Float,
        minX: Float, minY: Float, maxX: Float, maxY: Float,
    ): BubblePlace {
        fun clampX(x: Float) = x.coerceIn(minX, max(minX, maxX - w))
        fun clampY(y: Float) = y.coerceIn(minY, max(minY, maxY - h))
        val top = headY - gap - tail - h
        val tipX = headX
        // 头顶：气泡大部分落在脸朝的那一侧
        if (top >= minY) {
            val pref = if (faceRight) headX - w * .28f else headX - w * .72f
            val left = clampX(pref)
            return BubblePlace(left, top, TailDir.DOWN, tipX.coerceIn(left + corner, left + w - corner))
        }
        // 旁边：和头差不多高，尾巴横着指向嘴
        val midY = headY + petH * .22f
        val sideTop = clampY(midY - h / 2)
        val tailY = midY.coerceIn(sideTop + corner, sideTop + h - corner)
        val rightLeft = headX + petW / 2 + gap + tail
        val leftLeft = headX - petW / 2 - gap - tail - w
        val fitsRight = rightLeft + w <= maxX
        val fitsLeft = leftLeft >= minX
        if (faceRight && fitsRight || !faceRight && !fitsLeft && fitsRight) return BubblePlace(rightLeft, sideTop, TailDir.LEFT, tailY)
        if (fitsLeft) return BubblePlace(leftLeft, sideTop, TailDir.RIGHT, tailY)
        val left = clampX(if (faceRight) headX - w * .28f else headX - w * .72f)
        return BubblePlace(left, minY, TailDir.DOWN, tipX.coerceIn(left + corner, left + w - corner))
    }
}

/** 一套气泡外观。尺寸都按 dp 写，画的时候乘 density */
interface BubbleSkin {
    val padH: Float
    val padV: Float
    val radius: Float
    val tailLen: Float
    val textColor: Int
    fun drawBody(c: Canvas, r: RectF, d: Float)
    /** 尾巴：根部中点 (bx, by) 在气泡边上，朝 dir 伸出去；leanLeft = 朝下 / 朝上时尾巴尖往左歪（尾巴在气泡左半边时） */
    fun drawTail(c: Canvas, bx: Float, by: Float, dir: TailDir, d: Float, leanLeft: Boolean = true)
}

/** 奶油信笺（原来首页气泡的样子）：奶油到浅粉渐变、薄荷描边、圆角尾巴 */
object CreamSkin : BubbleSkin {
    override val padH = 11f
    override val padV = 7f
    override val radius = 13f
    override val tailLen = 8f
    override val textColor = 0xFF493347.toInt()
    private const val STROKE = 0xFF71BCA8.toInt()
    private const val TOP = 0xFFFFFDF5.toInt()
    private const val BOTTOM = 0xFFFFF0F4.toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = STROKE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val path = Path()

    override fun drawBody(c: Canvas, r: RectF, d: Float) {
        val rr = radius * d
        c.drawRoundRect(r.left, r.top + 2 * d, r.right, r.bottom + 2 * d, rr, rr, shade)
        fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, TOP, BOTTOM, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rr, rr, fill)
        fill.shader = null
        line.strokeWidth = 1.5f * d
        c.drawRoundRect(r, rr, rr, line)
    }

    override fun drawTail(c: Canvas, bx: Float, by: Float, dir: TailDir, d: Float, leanLeft: Boolean) {
        val half = 6f * d; val len = tailLen * d
        // 尾巴尖往一边歪一点，像嘴边冒出来的
        val lean = if (leanLeft) -.4f else .4f
        val (ax, ay, tx, ty, cx, cy) = when (dir) {
            TailDir.DOWN -> Six(bx - half, by, bx + half * lean, by + len, bx + half, by)
            TailDir.UP -> Six(bx - half, by, bx + half * lean, by - len, bx + half, by)
            TailDir.LEFT -> Six(bx, by - half, bx - len, by + half * .4f, bx, by + half)
            TailDir.RIGHT -> Six(bx, by - half, bx + len, by + half * .4f, bx, by + half)
        }
        path.reset(); path.moveTo(ax, ay); path.quadTo((ax + tx) / 2, (ay + ty) / 2, tx, ty); path.lineTo(cx, cy); path.close()
        fill.color = BOTTOM
        c.drawPath(path, fill)
        // 只描两条外边，根部和气泡连成一体
        line.strokeWidth = 1.5f * d
        path.reset(); path.moveTo(ax, ay); path.quadTo((ax + tx) / 2, (ay + ty) / 2, tx, ty); path.lineTo(cx, cy)
        c.drawPath(path, line)
    }

    private data class Six(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float)
}

/**
 * 图片气泡：assets/bubbles/<key>/ 下一张九宫格气泡框 body.webp + 尾巴图 + skin.json（格式见 docs/宠物对话气泡框.md）。
 *  - body 四角 insets 不拉伸，中间和四条边拉伸；scale = 图上 1 像素是多少 dp
 *  - 尾巴 tail_down_l / tail_down_r（朝下、尖往左 / 往右）、tail_left、tail_right；朝上用朝下的上下翻转
 *  - 每个尾巴给根部中点 root 和尖 tip（图片像素）：根部对准气泡边，尾巴根压进边框 overlap 像素盖住接缝
 *  - pixel = true 时不做滤镜缩放，像素风保持硬边
 */
class AssetSkin(
    private val body: Bitmap, private val insets: IntArray, private val scale: Float,
    override val padH: Float, override val padV: Float, override val radius: Float, override val tailLen: Float,
    override val textColor: Int, private val tails: Map<String, Tail>, pixel: Boolean,
    private val fill: Fill? = null,
) : BubbleSkin {
    class Tail(val bmp: Bitmap, val rootX: Float, val rootY: Float)
    /** 竖直渐变底色：图里只有边框和装饰，底色在框里面（frame 是框在 body 图里离四边多少像素）按高度画渐变，怎么拉都不分层 */
    class Fill(val top: Int, val bottom: Int, val frame: IntArray, val stroke: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = !pixel }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = android.graphics.Rect()
    private val dst = RectF()

    override fun drawBody(c: Canvas, r: RectF, d: Float) {
        val k = scale * d
        val (l, t, rr, b) = insets.toList()
        val w = body.width; val h = body.height
        // 四角按比例缩放；气泡很小时四角一起等比缩小（不单独压扁某个方向，免得圆角变形）
        val kk = minOf(k, r.width() / (l + rr).coerceAtLeast(1), r.height() / (t + b).coerceAtLeast(1))
        val kx = kk; val ky = kk
        fill?.let { f ->
            val half = f.stroke / 2f
            dst.set(r.left + (f.frame[0] + half) * kx, r.top + (f.frame[1] + half) * ky,
                r.right - (f.frame[2] + half) * kx, r.bottom - (f.frame[3] + half) * ky)
            fillPaint.shader = LinearGradient(0f, dst.top, 0f, dst.bottom, f.top, f.bottom, Shader.TileMode.CLAMP)
            val rad = radius * d
            c.drawRoundRect(dst, rad, rad, fillPaint)
        }
        val xs = floatArrayOf(r.left, r.left + l * kx, r.right - rr * kx, r.right)
        val ys = floatArrayOf(r.top, r.top + t * ky, r.bottom - b * ky, r.bottom)
        val sx = intArrayOf(0, l, w - rr, w); val sy = intArrayOf(0, t, h - b, h)
        for (i in 0..2) for (j in 0..2) {
            if (sx[i + 1] <= sx[i] || sy[j + 1] <= sy[j] || xs[i + 1] <= xs[i] || ys[j + 1] <= ys[j]) continue
            src.set(sx[i], sy[j], sx[i + 1], sy[j + 1])
            dst.set(xs[i], ys[j], xs[i + 1], ys[j + 1])
            c.drawBitmap(body, src, dst, paint)
        }
    }

    override fun drawTail(c: Canvas, bx: Float, by: Float, dir: TailDir, d: Float, leanLeft: Boolean) {
        val k = scale * d
        val key = when (dir) { TailDir.LEFT -> "left"; TailDir.RIGHT -> "right"; else -> if (leanLeft) "down_l" else "down_r" }
        val tl = tails[key] ?: tails["down_l"] ?: return
        val w = tl.bmp.width * k; val h = tl.bmp.height * k
        c.save()
        // 朝上：把朝下的尾巴绕根部上下翻过来
        if (dir == TailDir.UP) c.scale(1f, -1f, bx, by)
        dst.set(bx - tl.rootX * k, by - tl.rootY * k, bx - tl.rootX * k + w, by - tl.rootY * k + h)
        c.drawBitmap(tl.bmp, null, dst, paint)
        c.restore()
    }
}

/** 气泡样式表：五套都从 assets/bubbles/<key>/ 读；读不到（文件缺了、坏了）就退回代码画的奶油 CreamSkin */
object BubbleSkins {
    val styles = listOf("cream" to "奶油可爱", "comic" to "卡通漫画", "line" to "简约线条", "ink" to "国漫水墨", "pixel" to "像素复古")
    private val cache = HashMap<String, BubbleSkin>()

    fun of(ctx: android.content.Context, key: String): BubbleSkin = synchronized(cache) {
        cache.getOrPut(key) { runCatching { load(ctx, key) }.getOrNull() ?: CreamSkin }
    }

    private fun load(ctx: android.content.Context, key: String): BubbleSkin {
        val dir = "bubbles/$key"
        val j = org.json.JSONObject(ctx.assets.open("$dir/skin.json").bufferedReader().readText())
        val pixel = j.optBoolean("pixel", false)
        fun bmp(name: String) = ctx.assets.open("$dir/$name").use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inScaled = false })!!
        }
        val ins = j.getJSONArray("insets").let { a -> IntArray(4) { a.getInt(it) } }
        val tails = HashMap<String, AssetSkin.Tail>()
        j.getJSONObject("tails").let { o ->
            o.keys().forEach { k ->
                val t = o.getJSONObject(k)
                val root = t.getJSONArray("root")
                tails[k] = AssetSkin.Tail(bmp(t.getString("file")), root.getDouble(0).toFloat(), root.getDouble(1).toFloat())
            }
        }
        val fill = j.optJSONObject("fill")?.let { f ->
            val fr = f.getJSONArray("frame")
            AssetSkin.Fill(android.graphics.Color.parseColor(f.getString("top")), android.graphics.Color.parseColor(f.getString("bottom")),
                IntArray(4) { fr.getInt(it) }, f.optInt("stroke", 3))
        }
        return AssetSkin(
            bmp(j.optString("body", "body.webp")), ins, j.getDouble("scale").toFloat(),
            j.optDouble("padH", 11.0).toFloat(), j.optDouble("padV", 7.0).toFloat(), j.optDouble("radius", 13.0).toFloat(),
            j.optDouble("tailLen", 8.0).toFloat(), android.graphics.Color.parseColor(j.optString("textColor", "#493347")), tails, pixel, fill,
        )
    }
}

/**
 * 气泡里面的内容怎么排（纯计算，单元测试直接测）：表情在左、文字在右，两样都按高的那个竖直居中。
 * textW = 0 表示只有表情，emoSize = 0 表示只有文字。坐标相对内容区左上角（已扣掉内边距）。
 */
data class BubbleContent(val w: Float, val h: Float, val emoSize: Float, val emoY: Float, val textX: Float, val textY: Float) {
    companion object {
        fun of(textW: Float, textH: Float, emoSize: Float, gap: Float): BubbleContent {
            val hasText = textW > 0f
            val h = max(textH, emoSize)
            val w = when {
                emoSize <= 0f -> textW
                !hasText -> emoSize
                else -> emoSize + gap + textW
            }
            val textX = if (emoSize > 0f && hasText) emoSize + gap else 0f
            return BubbleContent(w, h, emoSize, (h - emoSize) / 2, textX, (h - textH) / 2)
        }
    }
}

/**
 * 场景里的宠物气泡：和宠物同一帧画，宠物走到哪跟到哪。
 * 文字排版按 (文字, 最大宽度) 缓存，只有换话或宠物远近变化时才重新排。
 */
class SceneBubble {
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var key = ""
    private var layout: StaticLayout? = null
    private var textW = 0f
    private val body = RectF()
    /** 这句话从什么时候开始显示（秒），用来做 0.16 秒的弹出 */
    private var shown: String? = null
    private var since = 0.0

    private val emoPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val emoSrc = android.graphics.Rect()
    private val emoDst = RectF()

    /**
     * headX/headY：宠物头顶；petW/petH：宠物实际身体大小；bounds：气泡能放的范围（已扣掉名字牌等）。
     * d = density，sp = 每 sp 多少像素，t = 当前时间（秒）。
     * emote / emoteCol：气泡里的像素表情（精灵表和第几列，见 pet.Emotes），放在文字左边按 4 帧循环播；text 为空时只显示表情。
     */
    fun draw(
        c: Canvas, text: String?, t: Double, headX: Float, headY: Float, petW: Float, petH: Float, faceRight: Boolean,
        bounds: RectF, d: Float, sp: Float, skin: BubbleSkin = CreamSkin, pop: Boolean = true,
        emote: Bitmap? = null, emoteCol: Int = 0,
    ) {
        val hasText = !text.isNullOrBlank()
        if (!hasText && emote == null) { shown = null; return }
        val showKey = "${text.orEmpty()}|${if (emote != null) emoteCol else -1}"
        if (showKey != shown) { shown = showKey; since = t }
        if (!hasText) {
            drawShape(c, t, headX, headY, petW, petH, faceRight, bounds, d, skin, pop,
                BubbleContent.of(0f, 0f, EMOTE_ALONE_DP * d, EMOTE_GAP_DP * d), null, emote, emoteCol)
            return
        }
        text!!
        tp.textSize = 13f * sp
        tp.color = skin.textColor
        tp.typeface = AppFonts.typeface ?: android.graphics.Typeface.DEFAULT
        val emoSize = if (emote != null) EMOTE_DP * d else 0f
        // 最宽约 3 个宠物身宽，但不小于 120dp、不超过可放范围的 62%；带表情时文字再让出表情那一块
        val maxW = min(max(petW * 3f, 120f * d), bounds.width() * .62f)
        val maxText = max(40f * d, maxW - 2 * skin.padH * d - (if (emote != null) emoSize + EMOTE_GAP_DP * d else 0f))
        val k = "$text|${maxText.toInt()}|${tp.textSize}"
        if (k != key) {
            key = k
            val full = ceil(maxText).toInt()
            val n = build(text, full, d).lineCount
            // 两三行时把每行排得差不多长，免得最后一行只剩一两个字；行数不变的最窄宽度用二分找
            var lo = full / n; var hi = full
            if (n > 1) while (lo < hi) {
                val mid = (lo + hi) / 2
                if (build(text, mid, d).let { it.lineCount <= n && it.getEllipsisCount(it.lineCount - 1) == 0 }) hi = mid else lo = mid + 1
            }
            val l = build(text, hi, d)
            textW = (0 until l.lineCount).maxOf { l.getLineWidth(it) }
            layout = build(text, ceil(textW).toInt().coerceAtLeast(1), d)
        }
        val l = layout ?: return
        drawShape(c, t, headX, headY, petW, petH, faceRight, bounds, d, skin, pop,
            BubbleContent.of(textW, l.height.toFloat(), emoSize, EMOTE_GAP_DP * d), l, emote, emoteCol)
    }

    /** 按内容大小摆气泡、画框和尾巴，再画表情和文字 */
    private fun drawShape(
        c: Canvas, t: Double, headX: Float, headY: Float, petW: Float, petH: Float, faceRight: Boolean,
        bounds: RectF, d: Float, skin: BubbleSkin, pop: Boolean, ct: BubbleContent, l: StaticLayout?, emote: Bitmap?, emoteCol: Int,
    ) {
        val w = ct.w + 2 * skin.padH * d
        val h = ct.h + 2 * skin.padV * d
        val tail = skin.tailLen * d
        val p = BubbleLayout.place(headX, headY, petW, petH, faceRight, w, h, tail, 2f * d, skin.radius * d + 6f * d,
            bounds.left, bounds.top, bounds.right, bounds.bottom)
        body.set(p.left, p.top, p.left + w, p.top + h)
        val (bx, by) = when (p.tail) {
            TailDir.DOWN -> p.tailAt to body.bottom
            TailDir.UP -> p.tailAt to body.top
            TailDir.LEFT -> body.left to p.tailAt
            TailDir.RIGHT -> body.right to p.tailAt
        }
        // 弹出：从尾巴尖附近放大到原大
        val e = if (!pop) 1f else ((t - since) / .16).coerceIn(0.0, 1.0).toFloat()
        val s = .8f + .2f * (1 - (1 - e) * (1 - e))
        c.save()
        c.scale(s, s, bx, by)
        skin.drawBody(c, body, d)
        skin.drawTail(c, bx, by, p.tail, d, leanLeft = p.tailAt < body.centerX())
        c.translate(body.left + skin.padH * d, body.top + skin.padV * d)
        if (emote != null && ct.emoSize > 0f) {
            val f = com.mengding.app.pet.Emotes.frame(t - since)
            val sz = com.mengding.app.pet.Emotes.SIZE
            emoSrc.set(emoteCol * sz, f * sz, emoteCol * sz + sz, f * sz + sz)
            emoDst.set(0f, ct.emoY, ct.emoSize, ct.emoY + ct.emoSize)
            c.drawBitmap(emote, emoSrc, emoDst, emoPaint)
        }
        if (l != null) { c.translate(ct.textX, ct.textY); l.draw(c) }
        c.restore()
    }

    companion object {
        /** 表情和文字同排时的边长、只有表情时的边长、表情和文字的间距（dp） */
        const val EMOTE_DP = 30f
        const val EMOTE_ALONE_DP = 40f
        const val EMOTE_GAP_DP = 5f
    }

    private fun build(text: String, width: Int, d: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f * d, 1f).setIncludePad(false)
            .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
}
