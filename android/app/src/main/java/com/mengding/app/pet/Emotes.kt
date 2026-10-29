package com.mengding.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 气泡里的像素表情：assets/emotes/<物种>.png，6 列表情（顺序见 logic.Emote）× 4 行动画帧，每格 56×56。
 * 由美术/表情/<物种>/<物种>_emotes_56px_sheet.png 复制过来；没有这张图的物种气泡里只显示文字。
 */
object Emotes {
    const val SIZE = 56
    const val FRAMES = 4
    /** 每帧多少毫秒 */
    const val FRAME_MS = 180
    private val cache = HashMap<String, Bitmap?>()

    fun sheet(ctx: Context, species: String): Bitmap? = synchronized(cache) {
        cache.getOrPut(species) {
            runCatching {
                ctx.assets.open("emotes/$species.png").use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                }
            }.getOrNull()
        }
    }

    fun has(ctx: Context, species: String) = sheet(ctx, species) != null

    fun frame(tSec: Double) = ((tSec * 1000 / FRAME_MS).toInt()) % FRAMES
}
