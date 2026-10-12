package com.mengding.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mengding.app.R
import org.json.JSONArray

/**
 * 宠物服装（换装商店 / 衣柜）。
 *   assets/outfits/<key>_icon.png        32×32 像素商店图标（美术/商店服装/make_pixel.py）
 *   res/drawable-nodpi/outfit_<key>.png  插画版商店图标
 *   assets/pets/wear/<宠物>__<key>.png   穿在这只宠物身上的"差异层"精灵图（美术/像素宠物/outfit_draw.py）
 * 穿戴不是贴图：差异层是按宠物身形真正画上去的结果（帽子压住头顶、耳朵钻出来、披风在背后被身体挡住……），
 * 叠在宠物精灵图上面就是穿好的样子。每个分类同一时间只能穿一件，多件按 LAYER_ORDER 叠。
 */
object Outfits {
    data class Item(val key: String, val name: String, val price: Int, val category: String)

    /** 分类，也是叠放顺序：后面的盖住前面的（帽子最上面） */
    val CATEGORIES = listOf("披风", "围巾", "眼镜", "发饰", "帽子")

    private val items = ArrayList<Item>()
    private val icon = HashMap<String, Bitmap>()

    /** 插画版图标（显式列出，避免打包时被资源压缩删掉） */
    val art = mapOf(
        "strawberry_hat" to R.drawable.outfit_strawberry_hat, "bow" to R.drawable.outfit_bow,
        "scarf" to R.drawable.outfit_scarf, "glasses" to R.drawable.outfit_glasses,
        "nightcap" to R.drawable.outfit_nightcap, "rain_hat" to R.drawable.outfit_rain_hat,
        "chef_hat" to R.drawable.outfit_chef_hat, "sakura_clip" to R.drawable.outfit_sakura_clip,
        "cape" to R.drawable.outfit_cape, "santa_hat" to R.drawable.outfit_santa_hat,
    )

    @Synchronized
    fun all(ctx: Context): List<Item> {
        if (items.isEmpty()) {
            val arr = JSONArray(ctx.assets.open("outfits/outfits.json").bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += Item(o.getString("key"), o.getString("name"), o.getInt("price"), o.optString("category", "帽子"))
            }
        }
        return items
    }

    fun of(ctx: Context, key: String?): Item? = key?.let { k -> all(ctx).firstOrNull { it.key == k } }

    /** 按叠放顺序排好（披风在最下，帽子在最上） */
    fun ordered(ctx: Context, keys: List<String>): List<String> =
        keys.mapNotNull { of(ctx, it) }.sortedBy { CATEGORIES.indexOf(it.category) }.map { it.key }

    /** 穿上 key：同分类的旧服装换下来 */
    fun put(ctx: Context, wearing: List<String>, key: String): List<String> {
        val cat = of(ctx, key)?.category ?: return wearing
        return wearing.filter { of(ctx, it)?.category != cat } + key
    }

    /** 萌度：每 10 萌点价格折算为 1 萌度，当前穿着实时计算。 */
    fun cuteness(ctx: Context, keys: List<String>): Int =
        keys.mapNotNull { of(ctx, it) }.sumOf { it.price / 10 }

    @Synchronized
    fun iconBitmap(ctx: Context, key: String): Bitmap = icon.getOrPut(key) {
        ctx.assets.open("outfits/${key}_icon.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!
        }
    }
}
