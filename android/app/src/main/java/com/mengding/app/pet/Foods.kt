package com.mengding.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mengding.app.R
import org.json.JSONArray

/**
 * 喂宠物的食物和饮料。素材由 美术/食物饮料/make_food.py 生成：
 *   assets/foods/<key>_icon.png  32×32 像素图标；assets/foods/foods.json  数值
 *   res/drawable-nodpi/food_<key>.png  插画版图标
 * 喂到宠物"最爱"的那样，饱食 / 水分额外 +10、体力额外 +2，并且播放开心动画。
 */
object Foods {
    data class Item(
        val key: String, val name: String, val drink: Boolean,
        val satiety: Int, val hydration: Int, val stamina: Int, val price: Int, val favorite: List<String>,
    )

    private val items = ArrayList<Item>()
    private val icons = HashMap<String, Bitmap>()

    val art = mapOf(
        "rice_ball" to R.drawable.food_rice_ball, "dried_fish" to R.drawable.food_dried_fish,
        "carrot" to R.drawable.food_carrot, "apple" to R.drawable.food_apple,
        "bone_biscuit" to R.drawable.food_bone_biscuit, "worm_gummy" to R.drawable.food_worm_gummy,
        "strawberry_cake" to R.drawable.food_strawberry_cake, "water" to R.drawable.food_water,
        "milk" to R.drawable.food_milk, "honey_water" to R.drawable.food_honey_water,
        "orange_juice" to R.drawable.food_orange_juice, "hot_cocoa" to R.drawable.food_hot_cocoa,
    )

    @Synchronized
    fun all(ctx: Context): List<Item> {
        if (items.isEmpty()) {
            val arr = JSONArray(ctx.assets.open("foods/foods.json").bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fav = o.getJSONArray("favorite").let { a -> (0 until a.length()).map { a.getString(it) } }
                items += Item(
                    o.getString("key"), o.getString("name"), o.getString("kind") == "drink",
                    o.getInt("satiety"), o.getInt("hydration"), o.getInt("stamina"), o.getInt("price"), fav,
                )
            }
        }
        return items
    }

    fun of(ctx: Context, key: String) = all(ctx).firstOrNull { it.key == key }

    @Synchronized
    fun iconBitmap(ctx: Context, key: String): Bitmap = icons.getOrPut(key) {
        ctx.assets.open("foods/${key}_icon.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!
        }
    }

    /** 效果说明，例如"饱食 +30 · 体力 +10" */
    fun effect(i: Item, species: String? = null): String = buildList {
        val fav = species != null && species in i.favorite
        if (i.satiety > 0) add("饱食 +${i.satiety + if (fav && !i.drink) 10 else 0}")
        if (i.hydration > 0) add("水分 +${i.hydration + if (fav && i.drink) 10 else 0}")
        if (i.stamina > 0) add("体力 +${i.stamina}")
    }.joinToString(" · ")
}
