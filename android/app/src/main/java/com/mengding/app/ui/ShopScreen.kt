package com.mengding.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Catalog
import com.mengding.app.logic.PetSim
import com.mengding.app.pet.Foods

/**
 * 商店（底部标签）：食物 / 饮料 / 服装三类，每类一个像素分类图标。
 * 食物和饮料买了放进"库存"，喂宠物时先用库存；服装买了放进衣柜。价格按实际价值：清水 1 萌点、蛋糕 12 萌点、披风 200 萌点。
 */
@Composable
fun ShopScreen() {
    val cfg by Store.config.collectAsState()
    var cat by rememberSaveable { mutableStateOf(Nav.shopCategory) }
    // 从首页"食物 / 饮料 / 服装"按钮进来时切到对应分类
    androidx.compose.runtime.LaunchedEffect(Nav.shopCategory) { cat = Nav.shopCategory }
    PageScaffold("商店") {
        HRow {
            Text("我的萌点", fontSize = 14.sp, color = P.sub)
            PixelImg("adv_coin_0", 20.dp)
            Text("${cfg.economy.points}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.ink)
            Spacer(Modifier.weight(1f))
            Text("签到、打卡、玩小游戏攒萌点", fontSize = 11.sp, color = P.sub)
        }
        Gap(10.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("food", "食物", "cat_food"), Triple("drink", "饮料", "cat_drink"), Triple("outfit", "服装", "cat_outfit"),
                Triple("game", "游戏", "cat_game"))
                .forEach { (k, label, icon) ->
                    val sel = cat == k
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (sel) P.mintSoft else P.card)
                            .border(if (sel) 2.dp else 1.dp, if (sel) P.mint else P.line, RoundedCornerShape(20.dp))
                            .clickable { cat = k; Nav.shopCategory = k }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ShopIcon(icon, 44.dp)
                        Text(label, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) P.mintDeep else P.ink)
                    }
                }
        }
        Gap(12.dp)
        when (cat) {
            "outfit" -> OutfitShopContent()
            "game" -> GameItemShop()
            else -> FoodShop(drink = cat == "drink")
        }
    }
}

@Composable
private fun ShopIcon(name: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val img = androidx.compose.runtime.remember(name) {
        ctx.assets.open("shop/$name.png").use { s ->
            android.graphics.BitmapFactory.decodeStream(s, null, android.graphics.BitmapFactory.Options().apply { inScaled = false })!!
        }.asImageBitmap()
    }
    Image(img, null, Modifier.size(size), filterQuality = FilterQuality.None)
}

/** 食物 / 饮料货架：像素图标、效果、谁最爱吃、价格、库存 */
@Composable
private fun FoodShop(drink: Boolean) {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val pet = PetSim.outing(cfg)
    Text(if (drink) "饮料补水分，有的还能补体力。" else "食物补饱食，蛋糕还能补体力。" +
        " 买了放进库存，在宠物部屋喂它时先用库存；一次买 5 个打八折。", fontSize = 12.sp, color = P.sub)
    Gap(8.dp)
    Foods.all(ctx).filter { it.drink == drink }.sortedBy { it.price }.chunked(2).forEach { pair ->
      Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pair.forEach { f ->
            val have = cfg.economy.pantry[f.key] ?: 0
            val fav = pet != null && pet.species in f.favorite
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (fav) P.pinkSoft else P.card)
                    .border(1.dp, P.line, RoundedCornerShape(20.dp)).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    PixelIcon(f.key, 64.dp, food = true)
                    if (have > 0) Text("×$have", Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(8.dp))
                        .background(P.mintSoft).padding(horizontal = 5.dp), fontSize = 11.sp, color = P.mintDeep)
                    if (fav) Icon(Ic.Heart, "最爱", Modifier.align(Alignment.TopEnd).size(16.dp), tint = P.pink)
                }
                Text(f.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text(Foods.effect(f, pet?.species), fontSize = 11.sp, color = P.sub, textAlign = TextAlign.Center, maxLines = 2)
                if (f.favorite.isNotEmpty()) {
                    Text(f.favorite.joinToString("、") { Catalog.of(it).name } + "最爱", fontSize = 10.sp, color = P.pink,
                        textAlign = TextAlign.Center, maxLines = 2)
                }
                Gap(6.dp)
                PriceButton(f.price, Modifier.fillMaxWidth()) { toast(Actions.buyFood(ctx, f.key, 1)) }
                Gap(4.dp)
                PriceButton(com.mengding.app.logic.Economy.bulkPrice(f.price, 5), Modifier.fillMaxWidth(), label = "5个 八折", primary = false) {
                    toast(Actions.buyFood(ctx, f.key, 5))
                }
            }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }
}

/** 游戏道具货架（萌宠连连看用）：在这里买是原价，游戏里临时买要 1.2 倍 */
@Composable
private fun GameItemShop() {
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    Text("萌宠连连看的道具。在这里买是原价，一次买 5 个打八折；游戏里用完了临时买要贵两成。", fontSize = 12.sp, color = P.sub)
    Gap(8.dp)
    com.mengding.app.logic.GameItems.all.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { it ->
                val have = cfg.economy.gameItems[it.key] ?: 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(P.card)
                        .border(1.dp, P.line, RoundedCornerShape(20.dp)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        PixelImg(it.icon, 56.dp)
                        if (have > 0) Text("×$have", Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(8.dp))
                            .background(P.mintSoft).padding(horizontal = 5.dp), fontSize = 11.sp, color = P.mintDeep)
                    }
                    Text(it.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text(it.desc, fontSize = 11.sp, color = P.sub, textAlign = TextAlign.Center, maxLines = 2)
                    Text("游戏里临时买 ${com.mengding.app.logic.GameItems.tempPrice(it)} 萌点", fontSize = 10.sp, color = P.pink)
                    Gap(6.dp)
                    PriceButton(it.price, Modifier.fillMaxWidth()) {
                        val m = com.mengding.app.logic.GameItems.buy(it, 1); toast(m)
                        if (m.startsWith("买了")) GameAudio.play("buy")
                    }
                    Gap(4.dp)
                    PriceButton(com.mengding.app.logic.Economy.bulkPrice(it.price, 5), Modifier.fillMaxWidth(), label = "5个 八折", primary = false) {
                        val m = com.mengding.app.logic.GameItems.buy(it, 5); toast(m)
                        if (m.startsWith("买了")) GameAudio.play("buy")
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
