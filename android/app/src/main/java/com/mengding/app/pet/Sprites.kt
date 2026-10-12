package com.mengding.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

/**
 * 像素宠物精灵图：assets/pets/<宠物>.png，每行一个动作、每列一帧，每格 48×48。
 * 动作顺序和帧间隔读 assets/pets/meta.json（由 美术/像素宠物/export_android.py 生成）。
 */
object Sprites {
    data class Action(val key: String, val label: String, val delay: Int, val row: Int)

    var size = 48; private set
    var frames = 8; private set
    private val actions = LinkedHashMap<String, Action>()
    /** 每种宠物、每个动作、每一帧的锚点 [头顶中线 x, 头顶 y, 眼睛上沿 y]，用来戴服装 */
    private val anchors = HashMap<String, List<List<IntArray>>>()
    private val sheets = HashMap<String, Bitmap>()
    /** 吃饭 / 喝水时捧着的食物：每种宠物、每个动作、每一帧 [中心 x, 中心 y, 边长]（宠物像素） */
    private val holds = HashMap<String, Map<String, List<IntArray>>>()
    /** 爪子层里的行顺序（例如 drink、eat） */
    private var pawsRows = listOf<String>()
    /** 食物吃掉一点点的分段数（assets/foods/bite/<食物>_<0..4>.png） */
    const val BITE_STAGES = 5
    /** 放大后的帧只给通知、小组件用，留最近 96 张 */
    private val scaled = object : LinkedHashMap<String, Bitmap>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 96
    }

    fun init(ctx: Context) {
        if (actions.isNotEmpty()) return
        val meta = JSONObject(ctx.assets.open("pets/meta.json").bufferedReader().readText())
        size = meta.getInt("size")
        frames = meta.getInt("frames")
        val arr = meta.getJSONArray("actions")
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            actions[a.getString("key")] = Action(a.getString("key"), a.getString("label"), a.getInt("delay"), i)
        }
        meta.optJSONArray("paws_rows")?.let { r -> pawsRows = (0 until r.length()).map { r.getString(it) } }
        meta.optJSONObject("hold")?.let { h ->
            h.keys().forEach { sp ->
                val o = h.getJSONObject(sp)
                holds[sp] = o.keys().asSequence().associateWith { act ->
                    val fr = o.getJSONArray(act)
                    (0 until fr.length()).map { fi -> fr.getJSONArray(fi).let { intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2)) } }
                }
            }
        }
        meta.optJSONObject("anchors")?.let { an ->
            an.keys().forEach { sp ->
                val acts = an.getJSONArray(sp)
                anchors[sp] = (0 until acts.length()).map { ai ->
                    val fr = acts.getJSONArray(ai)
                    (0 until fr.length()).map { fi -> fr.getJSONArray(fi).let { intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2)) } }
                }
            }
        }
    }

    fun action(key: String): Action = actions[key] ?: actions.getValue("idle")

    @Synchronized
    fun sheet(ctx: Context, species: String): Bitmap = sheets.getOrPut(species) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        ctx.assets.open("pets/$species.png").use { BitmapFactory.decodeStream(it, null, opts)!! }
    }

    /** 这个动作是不是"捧着吃 / 喝"的动作 */
    fun holds(action: String) = action in pawsRows

    /** 没指定吃什么时（提醒通知、小组件、动作预览）默认捧的东西 */
    fun defaultFood(action: String) = if (action == "drink") "water" else "rice_ball"

    /** 没有喂食进度时，按帧号循环"吃掉一点点"：一轮 8 帧从完整吃到快吃完 */
    fun loopStage(frame: Int) = (frame % frames) * BITE_STAGES / frames

    private val pawSheets = HashMap<String, Bitmap?>()
    private val bites = HashMap<String, Bitmap?>()

    @Synchronized
    private fun pawSheet(ctx: Context, species: String): Bitmap? = pawSheets.getOrPut(species) {
        runCatching { ctx.assets.open("pets/paws/$species.png").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }) } }.getOrNull()
    }

    @Synchronized
    fun bite(ctx: Context, food: String, stage: Int): Bitmap? = bites.getOrPut("$food/$stage") {
        runCatching {
            ctx.assets.open("foods/bite/${food}_${stage.coerceIn(0, BITE_STAGES - 1)}.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
            }
        }.recoverCatching { Foods.iconBitmap(ctx, food) }.getOrNull()
    }

    private val pixelPaint = android.graphics.Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    /**
     * 吃饭 / 喝水时：把实际喂的食物（吃到第 stage 段）画在宠物怀里，再把爪子盖上去。
     * dst 是这一帧宠物在画布上的位置；画在宠物和服装之后。
     */
    fun drawHeld(ctx: Context, canvas: android.graphics.Canvas, species: String, action: String, frame: Int,
                 food: String?, stage: Int, dst: android.graphics.RectF) {
        if (!holds(action)) return
        val h = holds[species]?.get(action)?.getOrNull(frame % frames) ?: return
        val k = dst.width() / size
        bite(ctx, food ?: defaultFood(action), stage)?.let { b ->
            val half = h[2] / 2f
            canvas.drawBitmap(b, null, android.graphics.RectF(dst.left + (h[0] - half) * k, dst.top + (h[1] - half) * k,
                dst.left + (h[0] + half) * k, dst.top + (h[1] + half) * k), pixelPaint)
        }
        pawSheet(ctx, species)?.let { p ->
            val row = pawsRows.indexOf(action)
            val fx = (frame % frames) * size
            canvas.drawBitmap(p, android.graphics.Rect(fx, row * size, fx + size, (row + 1) * size), dst, pixelPaint)
        }
    }

    private val wearSheets = HashMap<String, Bitmap?>()

    /** 某只宠物穿某件服装的差异层精灵图（和宠物精灵图同样排布）；没有就返回 null */
    @Synchronized
    fun wearSheet(ctx: Context, species: String, outfit: String): Bitmap? = wearSheets.getOrPut("$species/$outfit") {
        runCatching {
            ctx.assets.open("pets/wear/${species}__$outfit.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
            }
        }.getOrNull()
    }

    /**
     * 取一帧（穿上 outfits），按整数倍放大（最近邻，保持像素清晰）。结果会缓存。
     * 吃饭 / 喝水动作会把食物（默认饭团 / 清水，按帧号一点点吃掉）和爪子一起画进去。
     */
    @Synchronized
    fun frame(ctx: Context, species: String, action: String, index: Int, scale: Int = 1, outfits: List<String> = emptyList(),
              food: String? = null): Bitmap {
        val a = action(action)
        val worn = Outfits.ordered(ctx, outfits)
        val held = holds(a.key)
        val k = "$species/${a.key}/$index/$scale/${worn.joinToString(",")}/${if (held) food ?: defaultFood(a.key) else ""}"
        return scaled.getOrPut(k) {
            val fx = (index % frames) * size
            val fy = a.row * size
            if (worn.isEmpty() && !held) {
                val src = Bitmap.createBitmap(sheet(ctx, species), fx, fy, size, size)
                return@getOrPut if (scale == 1) src else Bitmap.createScaledBitmap(src, size * scale, size * scale, false)
            }
            // 放大后再画食物：食物图标比宠物像素细，先放大才不会糊
            val n = size * scale
            val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(out)
            val r = android.graphics.Rect(fx, fy, fx + size, fy + size)
            val dst = android.graphics.RectF(0f, 0f, n.toFloat(), n.toFloat())
            cv.drawBitmap(sheet(ctx, species), r, dst, pixelPaint)
            worn.forEach { o -> wearSheet(ctx, species, o)?.let { cv.drawBitmap(it, r, dst, pixelPaint) } }
            if (held) drawHeld(ctx, cv, species, a.key, index, food, loopStage(index), dst)
            out
        }
    }
}
