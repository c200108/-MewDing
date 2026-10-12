package com.mengding.app.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Catalog
import com.mengding.app.logic.PetSim
import com.mengding.app.notify.Notifier
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 桌面悬浮宠物（需求 D3/D7）：浮在最上层，能拖动，松手后贴到屏幕边上。
 * 你走路的时候它才会跑（靠计步传感器判断）：横穿屏幕（来回 / 向左 / 向右，跑到对面就歇着），
 * 或者绕屏幕四条边跑（脚踩着边，过拐角转身）；你停下来它也停下，静止时不乱跑。
 * 轻点 = 摸摸它；长按 = 弹出一圈菜单 [哄睡][玩游戏][躲起来][等级动作][收起]；拖动时是"被拎起来"的动作。
 * 躲起来：侧身贴在屏幕边上，只露出耳朵，点一下就探出来。
 * 按下时叫一声；会在旁边冒文字气泡说话（鼓励的话、便签提醒、饭点提醒）。
 */
class FloatingPet(private val ctx: Context) {
    companion object {
        /** 点了"收起"：本次不再显示，下次打开 App 时恢复 */
        @Volatile var hiddenByUser = false
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val petPx = (72 * density).toInt()
    private val menuPx = (232 * density).toInt()
    private val view = PetView(ctx)
    private val lp = WindowManager.LayoutParams(
        petPx, petPx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val dm = ctx.resources.displayMetrics
        x = dm.widthPixels - petPx
        y = dm.heightPixels * 2 / 3
    }
    private var attached = false

    fun show() {
        if (attached) return
        runCatching { wm.addView(view, lp); attached = true }
        view.start()
    }

    fun hide() {
        bubble.dismiss()
        view.stop()
        if (attached) runCatching { wm.removeView(view) }
        attached = false
    }

    fun refresh() { view.applyMode(); view.invalidate() }

    fun setPaused(p: Boolean) = if (p) view.stop() else view.start()

    private val bubble = Bubble(ctx)

    /** 屏幕大小（含状态栏、导航栏，悬浮窗可以盖到那里） */
    private fun screenSize(): Pair<Int, Int> {
        if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { return it.width() to it.height() }
        val dm = ctx.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    /** 用户正在走路（计步传感器最近 4 秒内报过步） */
    private fun walking() = PetFx.walkingUntil > System.currentTimeMillis()

    /**
     * 宠物说话：在宠物旁边冒一个文字气泡。noteId 不为空时，点气泡打开那条便签；onTap 是点气泡时要做的事。
     * 宠物躲在边上时：popOut = true（提醒类）先探出来再说；闲聊就不打扰它。action 是说话时顺便做的动作。
     */
    fun speak(text: String, durationMs: Long = 6000, noteId: String? = null, onTap: (() -> Unit)? = null,
              popOut: Boolean = false, action: String? = null): Boolean {
        if (!attached) return false
        if (view.peek != 0) {
            if (!popOut) return false
            view.comeOut { bubble.show(text, durationMs, noteId, onTap); action?.let { view.act(it, 4000) } }
            return true
        }
        bubble.show(text, durationMs, noteId, onTap)
        action?.let { view.act(it, 4000) }
        return true
    }

    /** 文字气泡：单独一个小悬浮窗，跟在宠物旁边，尖角指向宠物；点一下就收起 */
    @SuppressLint("ViewConstructor")
    private inner class Bubble(c: Context) : View(c) {
        private val h = Handler(Looper.getMainLooper())
        // 注意：TextPaint 自己也有 density 字段，这里要用外层的屏幕密度
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A2C36"); textSize = 15 * this@FloatingPet.density
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2 * density; color = Color.parseColor("#2FB38C")
            strokeJoin = Paint.Join.ROUND
        }
        private val pad = 12 * density
        private val tail = 10 * density
        private var layout: StaticLayout? = null
        private var tailRight = true
        /** 尖角在气泡里的纵坐标：对准宠物头部 */
        private var tailY = 0f
        private var noteId: String? = null
        private var onTap: (() -> Unit)? = null
        private var shown = false
        private val blp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        fun show(text: String, ms: Long, id: String?, tap: (() -> Unit)? = null) {
            com.mengding.app.ui.AppFonts.load(ctx)
            tp.typeface = com.mengding.app.ui.AppFonts.typeface ?: android.graphics.Typeface.DEFAULT
            noteId = id
            onTap = tap
            val w = kotlin.math.min((210 * density).toInt(), kotlin.math.ceil(tp.measureText(text)).toInt() + 2)
            val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, w).build()
            layout = l
            blp.width = (w + pad * 2 + tail).toInt()
            blp.height = (l.height + pad * 2).toInt()
            position()
            if (!shown) { runCatching { wm.addView(this, blp); shown = true } }
            else runCatching { wm.updateViewLayout(this, blp) }
            invalidate()
            h.removeCallbacksAndMessages(null)
            h.postDelayed({ dismiss() }, ms)
        }

        /** 按宠物当前头部的位置摆气泡：气泡放在头的一侧，尖角对准头。宠物每动一帧都调一次 */
        fun position() {
            val (sw, sh) = screenSize()
            val (hx, hy) = view.headPoint()
            tailRight = hx > sw / 2
            val reach = petPx * 0.3f          // 尖角尖端停在离头中心这么远的地方
            blp.x = (if (tailRight) hx - reach - blp.width else hx + reach).toInt()
            blp.y = (hy - blp.height / 2f).toInt()
            blp.x = blp.x.coerceIn(0, (sw - blp.width).coerceAtLeast(0))
            blp.y = blp.y.coerceIn(0, (sh - blp.height).coerceAtLeast(0))
            val margin = 14 * density + 7 * density
            tailY = (hy - blp.y).coerceIn(margin, (blp.height - margin).coerceAtLeast(margin))
            if (shown) runCatching { wm.updateViewLayout(this, blp) }
            invalidate()
        }

        fun dismiss() {
            onTap = null
            h.removeCallbacksAndMessages(null)
            if (shown) runCatching { wm.removeView(this) }
            shown = false
        }

        override fun onDraw(canvas: Canvas) {
            val l = layout ?: return
            val w = width.toFloat(); val hh = height.toFloat()
            val half = stroke.strokeWidth / 2
            val body = if (tailRight) RectF(half, half, w - tail, hh - half) else RectF(tail, half, w - half, hh - half)
            val r = 14 * density
            canvas.drawRoundRect(body, r, r, fill)
            canvas.drawRoundRect(body, r, r, stroke)
            // 尖角：先盖住圆角框在这里的描边，再描两条边
            val cy = if (tailY > 0f) tailY.coerceIn(half, hh - half) else hh / 2; val tw = 7 * density
            val tri = Path().apply {
                if (tailRight) { moveTo(body.right - half * 2, cy - tw); lineTo(w - half, cy); lineTo(body.right - half * 2, cy + tw) }
                else { moveTo(body.left + half * 2, cy - tw); lineTo(half, cy); lineTo(body.left + half * 2, cy + tw) }
            }
            canvas.drawPath(Path(tri).apply { close() }, fill)
            canvas.drawPath(tri, stroke)
            canvas.save()
            canvas.translate(body.left + pad - half, pad)
            l.draw(canvas)
            canvas.restore()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                noteId?.let { runCatching { Notifier.openNote(ctx, it).send() } }
                val t = onTap
                dismiss()
                t?.invoke()
            }
            return true
        }
    }

    private data class Item(val label: String, val run: () -> Unit)
    private class Pt(val x: Float, val y: Float, val jump: Boolean = false)

    @SuppressLint("ViewConstructor")
    private inner class PetView(c: Context) : View(c) {
        private val h = Handler(Looper.getMainLooper())
        private val paint = Paint().apply { isFilterBitmap = false }
        private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2FFFFFF") }
        private val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2 * density; color = Color.parseColor("#2FB38C")
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A2C36"); textSize = 12 * density; textAlign = Paint.Align.CENTER
        }
        private var frame = 0
        private var running = false
        private var localAction: String? = null
        private var menu = false
        /** 长按打开菜单的那一次触摸还没抬起 */
        private var menuGesture = false
        /** 打开菜单前宠物的位置，以及菜单窗口里宠物的偏移 */
        private var petX = 0; private var petY = 0
        private var offX = 0; private var offY = 0
        private var baseAngle = Math.PI
        private var downX = 0f; private var downY = 0f
        private var startX = 0; private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val slop = ViewConfiguration.get(c).scaledTouchSlop

        /** 宠物脚朝哪条边：0 底边、90 左边、180 顶边、270 右边（画的时候按这个角度转） */
        var rot = 0f
            private set
        /** 精灵图本身朝左（尾巴在右），往右跑时要镜像 */
        private var flip = false
        /** 躲在屏幕边上：0 没躲，-1 左边，1 右边 */
        var peek = 0
            private set
        private var moveTask: Runnable? = null
        private var mode = Store.cfg.settings.petRunMode

        private fun menuItems(): List<Item> {
            val pet = PetSim.outing(Store.cfg)
            val base = mutableListOf(
            Item("哄睡") { toast(Actions.lull(ctx)) },
            Item("玩游戏") { runCatching { Notifier.openApp(ctx, "games").send() } },
            Item("躲起来") { hideAtEdge() },
            Item("收起") { hiddenByUser = true; hide(); toast("收起来了，下次打开萌叮时再出来") },
            )
            if (pet != null) {
                val unlocked = Catalog.unlocks.filter { Catalog.level(pet.exp) >= it.first }
                // 每个已解锁动作单独显示，避免把五级的“转圈”隐藏在“等级动作”随机项里。
                unlocked.asReversed().forEach { (_, label) ->
                    base.add(3, Item(label) {
                        act(visualUnlockAction(label), 4000)
                        toast("${pet.name} 做了${label}")
                    })
                }
            }
            return base
        }

        private val anim = object : Runnable {
            override fun run() {
                frame = (frame + 1) % Sprites.frames
                invalidate()
                h.postDelayed(this, Sprites.action(currentAction()).delay.toLong())
            }
        }

        /** 定时跑步：横穿模式每 15~35 秒跑一趟；绕边模式跑完一圈歇 6 秒再跑 */
        private val stroll = object : Runnable {
            override fun run() {
                if (!dragging && !menu && peek == 0 && moveTask == null && localAction == null && walking()) {
                    val pet = PetSim.outing(Store.cfg)
                    if (pet != null && !pet.sleeping) when (mode) {
                        "cross" -> crossRun()
                        "edges" -> lapRun()
                    }
                }
                maybeRandomUnlockAction()
                // 走路时很快就接着跑；不走路就隔一会儿看一眼
                h.postDelayed(this, when {
                    !walking() -> 2_000L
                    mode == "edges" -> 1_500L
                    else -> (3_000..6_000).random().toLong()
                })
            }
        }

        private val longPress = Runnable {
            if (!dragging) { longPressed = true; menuGesture = true; openMenu() }
        }

        fun start() {
            if (running) return
            running = true
            h.post(anim)
            h.postDelayed(stroll, 2_000L)
        }

        fun stop() {
            running = false
            h.removeCallbacksAndMessages(null)
            moveTask = null
            if (localAction == "walk") localAction = null
        }

        /** 设置里换了跑步方式：停下当前的跑动，按新方式重新贴边 */
        fun applyMode() {
            val m = Store.cfg.settings.petRunMode
            if (m == mode) return
            mode = m
            stopMove()
            if (peek == 0 && !menu) snapToEdge()
            invalidate()
        }

        /** 临时做一个动作（例如饭点时吃饭） */
        fun act(action: String, ms: Long) {
            if (moveTask != null || peek != 0) return
            localAction = action
            invalidate()
            h.postDelayed({ if (localAction == action) { localAction = null; invalidate() } }, ms)
        }

        private fun toast(s: String) { if (s.isNotEmpty()) Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() }

        private var nextRandomActionAt = System.currentTimeMillis() + 300_000L

        /** 桌面空闲时随机做已经解锁的动作，避免打断跑步、提醒和拖动。 */
        private fun maybeRandomUnlockAction() {
            val now = System.currentTimeMillis()
            if (now < nextRandomActionAt || menu || dragging || moveTask != null || localAction != null || peek != 0) return
            val pet = PetSim.outing(Store.cfg) ?: return
            val unlocked = Catalog.unlocks.filter { Catalog.level(pet.exp) >= it.first }
            if (unlocked.isNotEmpty() && !pet.sleeping && pet.stamina >= 30f) {
                val chosen = unlocked.random().second
                act(visualUnlockAction(chosen), 4000)
            }
            nextRandomActionAt = now + (300_000L..720_000L).random()
        }

        private fun visualUnlockAction(label: String): String = when (label) {
            "打滚" -> "roll"
            "转圈" -> "spin"
            "坐下作揖" -> "bow"
            "专属彩蛋动作" -> "easter"
            else -> "idle"
        }

        private fun currentAction(): String {
            localAction?.let { return it }
            if (dragging) return "lift"
            return PetFx.actionFor(PetSim.outing(Store.cfg))
        }

        private fun screen(): Pair<Int, Int> = screenSize()

        /** 宠物头部中心在屏幕上的位置（随转身变：脚踩哪条边，头就在反方向） */
        fun headPoint(): Pair<Float, Float> {
            val cx = lp.x + offX + petPx / 2f
            val cy = lp.y + offY + petPx / 2f
            val d = petPx * 0.2f
            return when (rot) {
                0f -> cx to cy - d
                180f -> cx to cy + d
                90f -> cx + d to cy
                else -> cx - d to cy
            }
        }

        private fun update() { if (attached) runCatching { wm.updateViewLayout(this, lp) } }

        private fun stopMove() {
            moveTask?.let { h.removeCallbacks(it) }
            moveTask = null
            if (localAction == "walk") localAction = null
        }

        /**
         * 沿折线一帧一帧地跑（每 16 毫秒走一步，不会半路停掉）。
         * edges = true 时按所在的边自动转身，让脚始终踩在那条边上。jump 点是瞬移（跑出屏幕后从另一边出来）。
         */
        private fun runPath(points: List<Pt>, dpPerSec: Float, action: String = "walk", edges: Boolean = false,
                            needWalk: Boolean = false, onDone: () -> Unit = {}) {
            stopMove()
            val speed = dpPerSec * density
            var idx = 0
            var x = lp.x.toFloat(); var y = lp.y.toFloat()
            localAction = action
            val task = object : Runnable {
                var last = SystemClock.uptimeMillis()
                override fun run() {
                    // 跑步途中你停下来了：它也原地停下
                    if (needWalk && !walking()) {
                        moveTask = null
                        if (localAction == action) localAction = null
                        invalidate()
                        return
                    }
                    val now = SystemClock.uptimeMillis()
                    var step = speed * (now - last).coerceAtMost(100) / 1000f
                    last = now
                    while (step > 0 && idx < points.size) {
                        val p = points[idx]
                        if (p.jump) { x = p.x; y = p.y; idx++; continue }
                        val dx = p.x - x; val dy = p.y - y
                        val d = hypot(dx, dy)
                        if (d > 0.01f) face(dx, dy, edges, x, y)
                        if (d <= step) { x = p.x; y = p.y; step -= d; idx++ }
                        else { x += dx / d * step; y += dy / d * step; step = 0f }
                    }
                    lp.x = x.toInt(); lp.y = y.toInt()
                    update(); bubble.position()
                    if (idx >= points.size) {
                        moveTask = null
                        if (localAction == action) localAction = null
                        invalidate()
                        onDone()
                    } else h.postDelayed(this, 16)
                }
            }
            moveTask = task
            h.post(task)
        }

        /** 按移动方向转身：绕边跑时先定脚踩哪条边，再看往这条边的哪头跑 */
        private fun face(dx: Float, dy: Float, edges: Boolean, x: Float, y: Float) {
            if (edges) {
                val (w, hh) = screen()
                val a = (w - petPx).toFloat(); val b = (hh - petPx).toFloat()
                rot = if (abs(dx) >= abs(dy)) { if (y < b / 2) 180f else 0f } else { if (x < a / 2) 90f else 270f }
            }
            val r = Math.toRadians(rot.toDouble())
            val along = dx * cos(r) + dy * sin(r)
            if (abs(along) > 0.01) flip = along > 0
        }

        /** 横穿屏幕：从一边跑到另一边，到了就停下歇着。单向时先从这边跑出屏幕、再从对面跑进来 */
        private fun crossRun() {
            val (w, _) = screen()
            rot = 0f
            val atRight = lp.x + petPx / 2 > w / 2
            val dir = when (Store.cfg.settings.petRunDir) {
                "right" -> 1
                "left" -> -1
                else -> if (atRight) -1 else 1
            }
            val y = lp.y.toFloat()
            val target = if (dir > 0) (w - petPx).toFloat() else 0f
            val pts = ArrayList<Pt>()
            if ((dir > 0) == atRight) {
                pts += Pt(if (dir > 0) w.toFloat() else -petPx.toFloat(), y)
                pts += Pt(if (dir > 0) -petPx.toFloat() else w.toFloat(), y, jump = true)
            }
            pts += Pt(target, y)
            runPath(pts, 110f, needWalk = true) { snapToEdge() }
        }

        /** 绕屏幕四条边跑一圈：脚踩着边，过拐角时跟着转身 */
        private fun lapRun() {
            val (w, hh) = screen()
            val a = (w - petPx).toFloat(); val b = (hh - petPx).toFloat()
            snapToEdge()
            val x = lp.x.toFloat(); val y = lp.y.toFloat()
            val cw = Store.cfg.settings.petLapDir != "ccw"
            // 拐角依次是：左上、右上、右下、左下。顺时针：顶边往右 → 右边往下 → 底边往左 → 左边往上
            val corners = listOf(Pt(0f, 0f), Pt(a, 0f), Pt(a, b), Pt(0f, b))
            val edgeIdx = when (rot) { 180f -> 0; 270f -> 1; 0f -> 2; else -> 3 }   // 顶、右、底、左
            val order = if (cw) (1..4).map { corners[(edgeIdx + it) % 4] }
            else (0..3).map { corners[(edgeIdx - it + 4) % 4] }
            runPath(order + Pt(x, y), 120f, edges = true, needWalk = true)
        }

        private fun snapToEdge() {
            val (w, hh) = screen()
            val a = w - lp.width; val b = hh - lp.height
            if (mode == "edges") {
                val x = lp.x.coerceIn(0, a); val y = lp.y.coerceIn(0, b)
                val d = listOf(y to 180f, b - y to 0f, x to 90f, a - x to 270f).minBy { it.first }
                rot = d.second
                when (rot) {
                    180f -> { lp.x = x; lp.y = 0 }
                    0f -> { lp.x = x; lp.y = b }
                    90f -> { lp.x = 0; lp.y = y }
                    else -> { lp.x = a; lp.y = y }
                }
            } else {
                rot = 0f
                lp.x = if (lp.x + lp.width / 2 < w / 2) 0 else a
                lp.y = lp.y.coerceIn(0, b)
            }
            update()
            if (!menu) bubble.position()
            invalidate()
        }

        /** 躲起来：侧身贴到最近的左 / 右边，身子藏到屏幕外，只露出耳朵 */
        fun hideAtEdge() {
            stopMove(); bubble.dismiss()
            val (w, _) = screen()
            val right = lp.x + petPx / 2 > w / 2
            peek = if (right) 1 else -1
            rot = if (right) 270f else 90f
            // 精灵图头顶上方有空白，量出头顶在第几行，再多露 9 像素（耳朵 + 一点脑门）
            val vis = petPx * (headTop() + 9f) / Sprites.size
            val tx = if (right) w - vis else -(petPx - vis)
            // 躲着时一直用待机姿势（竖着耳朵），睡着、犯困的趴姿露不出耳朵
            runPath(listOf(Pt(tx, lp.y.toFloat())), 360f, action = "idle") { if (peek != 0) localAction = "idle" }
        }

        /** 待机第一帧里，从上往下第一行有像素的位置（头顶 / 耳尖） */
        private fun headTop(): Int {
            val pet = PetSim.outing(Store.cfg) ?: return 10
            val sheet = Sprites.sheet(ctx, pet.species)
            val s = Sprites.size
            val row = Sprites.action("idle").row
            // 只看头部那几列（中线左右 12 像素），免得量到翘起来的尾巴
            for (y in 0 until s) for (x in 12 until s - 12) {
                if ((sheet.getPixel(x, row * s + y) ushr 24) > 0) return y
            }
            return 10
        }

        /** 点一下躲着的宠物：探出来 */
        fun comeOut(onOut: () -> Unit = {}) {
            if (peek == 0) { onOut(); return }
            val (w, _) = screen()
            val right = peek > 0
            peek = 0
            localAction = null
            runPath(listOf(Pt(if (right) (w - petPx).toFloat() else 0f, lp.y.toFloat())), 360f, action = "energetic") {
                rot = 0f
                snapToEdge()
                localAction = "energetic"
                h.postDelayed({ if (localAction == "energetic") localAction = null }, 1500)
                onOut()
            }
        }

        /** 菜单窗口限制在屏幕内；按钮朝屏幕中间展开成半圆，宠物贴边时也不会被挤出屏幕 */
        private fun openMenu() {
            bubble.dismiss()
            stopMove()
            val (sw, sh) = screen()
            if (peek != 0) {
                lp.x = if (peek > 0) sw - petPx else 0
                peek = 0
                localAction = null
            }
            rot = 0f
            menu = true
            petX = lp.x; petY = lp.y
            val d = (menuPx - petPx) / 2
            val mx = (petX - d).coerceIn(0, (sw - menuPx).coerceAtLeast(0))
            val my = (petY - d).coerceIn(0, (sh - menuPx).coerceAtLeast(0))
            offX = petX - mx; offY = petY - my
            val pcx = petX + petPx / 2.0; val pcy = petY + petPx / 2.0
            // 宠物在右半边就往左展开，在左半边就往右展开
            baseAngle = if (pcx > sw / 2.0) Math.PI else 0.0
            if (pcy < sh * 0.2) baseAngle += if (baseAngle == 0.0) 0.5 else -0.5
            if (pcy > sh * 0.8) baseAngle += if (baseAngle == 0.0) -0.5 else 0.5
            lp.width = menuPx; lp.height = menuPx
            lp.x = mx; lp.y = my
            update(); invalidate()
        }

        private fun closeMenu() {
            menu = false
            lp.width = petPx; lp.height = petPx
            lp.x = petX; lp.y = petY
            offX = 0; offY = 0
            update(); snapToEdge(); invalidate()
        }

        private fun itemCenter(i: Int, count: Int): Pair<Float, Float> {
            val r = 86 * density
            val step = if (count <= 1) 0.0 else 37.0
            val ang = baseAngle + Math.toRadians((i - (count - 1) / 2.0) * step)
            val cx = offX + petPx / 2f; val cy = offY + petPx / 2f
            return (cx + r * cos(ang).toFloat()) to (cy + r * sin(ang).toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            val pet = PetSim.outing(Store.cfg) ?: return
            val a = Sprites.action(currentAction())
            val sheet = Sprites.sheet(ctx, pet.species)
            val s = Sprites.size
            val src = Rect((frame % Sprites.frames) * s, a.row * s, (frame % Sprites.frames + 1) * s, (a.row + 1) * s)
            val ox = offX.toFloat(); val oy = offY.toFloat()
            val cx = ox + petPx / 2f; val cy = oy + petPx / 2f
            canvas.save()
            canvas.rotate(rot, cx, cy)
            if (flip) canvas.scale(-1f, 1f, cx, cy)
            val dst = RectF(ox, oy, ox + petPx, oy + petPx)
            canvas.drawBitmap(sheet, src, dst, paint)
            Outfits.ordered(ctx, pet.worn).forEach { o ->
                Sprites.wearSheet(ctx, pet.species, o)?.let { canvas.drawBitmap(it, src, dst, paint) }
            }
            if (Sprites.holds(a.key)) {
                val now = com.mengding.app.logic.Time.now()
                val feed = PetFx.transient.value?.takeIf { it.action == a.key && it.until > now }
                Sprites.drawHeld(ctx, canvas, pet.species, a.key, frame, feed?.foodKey,
                    feed?.biteStage(now) ?: Sprites.loopStage(frame), dst)
            }
            canvas.restore()
            if (menu) {
                val items = menuItems()
                textPaint.typeface = com.mengding.app.ui.AppFonts.typeface ?: android.graphics.Typeface.DEFAULT
                val br = 25 * density
                items.forEachIndexed { i, it ->
                    val (bx, by) = itemCenter(i, items.size)
                    canvas.drawCircle(bx, by, br, btnPaint)
                    canvas.drawCircle(bx, by, br, btnStroke)
                    canvas.drawText(it.label, bx, by + textPaint.textSize / 3, textPaint)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (menu) {
                if (menuGesture) {
                    if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) menuGesture = false
                    return true
                }
                if (e.action == MotionEvent.ACTION_UP) {
                    val items = menuItems()
                    val hit = items.indices.firstOrNull { i ->
                        val (bx, by) = itemCenter(i, items.size)
                        hypot(e.x - bx, e.y - by) < 29 * density
                    }
                    closeMenu()
                    hit?.let { items[it].run() }
                }
                return true
            }
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    PetSim.outing(Store.cfg)?.let { PetVoice.play(ctx, it.species) }
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    dragging = false; longPressed = false
                    h.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop) && !longPressed) {
                        dragging = true; h.removeCallbacks(longPress); bubble.dismiss()
                        stopMove(); rot = 0f
                        if (peek != 0) { peek = 0; localAction = null }
                        invalidate()
                    }
                    if (dragging) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); update() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    h.removeCallbacks(longPress)
                    if (dragging) { dragging = false; snapToEdge(); invalidate() }
                    else if (!longPressed && e.action == MotionEvent.ACTION_UP) {
                        if (peek != 0) { comeOut { speak(Speech.phrase(), 3500) }; return true }
                        stopMove()
                        Actions.petPet(ctx)
                        speak(Speech.phrase(), 3500)
                        localAction = "pet"
                        h.postDelayed({ if (localAction == "pet") localAction = null }, 1600)
                    }
                }
            }
            return true
        }
    }
}
