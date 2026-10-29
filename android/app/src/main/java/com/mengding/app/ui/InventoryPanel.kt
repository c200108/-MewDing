package com.mengding.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.pet.Foods
import com.mengding.app.pet.Outfits
import com.mengding.app.pet.PetFx
import kotlinx.coroutines.delay

/** 首页个人仓库：面板固定半屏，列表内部滚动，消费必须来自库存。 */
@Composable
fun InventoryPanel(category: String, modifier: Modifier, onCategory: (String) -> Unit,
                   onClose: () -> Unit, onFeedback: (String) -> Unit) {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val transient by PetFx.transient.collectAsState()
    val pet = PetSim.outing(cfg)
    var now by remember { mutableLongStateOf(Time.now()) }
    LaunchedEffect(transient) {
        now = Time.now()
        while (transient != null && now < transient!!.until) { delay(100); now = Time.now() }
    }
    val busy = transient?.let { it.foodKey != null && it.until > now } == true
    val food = Foods.all(ctx).filter { it.drink == (category == "drink") && (cfg.economy.pantry[it.key] ?: 0) > 0 }
    val clothes = Outfits.all(ctx).filter { it.key in cfg.economy.ownedOutfits }
    Column(modifier.mdSheet().padding(horizontal = 16.dp)) {
        MdGrabber(Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的小仓库", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.ink)
                Text(pet?.let { "给 ${it.name} 的小心意" } ?: "先到部屋选择一只出门宠物", fontSize = 12.sp, color = P.sub)
            }
            TextButton(onClick = onClose) { Text("收起") }
        }
        MdTabs(listOf("food" to "食物", "drink" to "饮料", "outfit" to "衣柜"), category, onSelect = onCategory)
        Gap(8.dp)
        if ((category == "outfit" && clothes.isEmpty()) || (category != "outfit" && food.isEmpty())) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (category == "outfit") "衣柜还是空的，去商店挑件衣服吧" else "这一格空啦，去商店补充后再来喂它吧", color = P.sub)
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (category == "outfit") items(clothes, key = { it.key }) { item ->
                    val worn = pet != null && item.key in pet.worn
                    Column(Modifier.padding(end = 2.dp, bottom = 2.dp).mdBlock(if (worn) P.mint else P.line)
                        .clickable(enabled = pet != null) {
                            pet?.let { Actions.wear(ctx, it.id, item.key, !worn)
                                onFeedback(if (worn) "换下${item.name}啦" else "穿上${item.name}啦，好看吗？") }
                        }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelIcon(item.key, 48.dp, food = false)
                        Text(item.name, color = P.ink, fontSize = 12.sp)
                        Text(if (worn) "已穿戴 · 点我脱下" else "已拥有 · 点我穿上", color = P.mintDeep, fontSize = 11.sp)
                    }
                } else items(food, key = { it.key }) { item ->
                    Column(Modifier.padding(end = 2.dp, bottom = 2.dp).mdBlock(P.gold)
                        .clickable(enabled = pet != null && !busy) {
                            pet?.let { onFeedback(Actions.feedItem(ctx, it.id, item.key, inventoryOnly = true)) }
                        }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelIcon(item.key, 48.dp, food = true)
                        Text("${item.name} ×${cfg.economy.pantry[item.key] ?: 0}", color = P.ink, fontSize = 12.sp)
                        Text(if (busy) "慢慢吃，等一等" else "点我喂一份", color = P.mintDeep, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
