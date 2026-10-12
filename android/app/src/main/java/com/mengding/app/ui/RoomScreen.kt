package com.mengding.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mengding.app.data.Pet
import com.mengding.app.data.Store
import com.mengding.app.R
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Catalog
import com.mengding.app.logic.PetSim
import com.mengding.app.pet.PetFx
import com.mengding.app.pet.Outfits

/** 宠物部屋（需求 E3f/E3g）：所有宠物都在这里，同一时间只有一只出门 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomScreen() {
    val cfg by Store.config.collectAsState()
    PageScaffold("宠物部屋") {
        // 屋子：所有宠物在地板上各自待着
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(26.dp)),
        ) {
            Image(painterResource(R.drawable.room_background), null, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().height(170.dp).background(Color.White.copy(alpha = .12f)))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(46.dp).background(Color(0xFFE9C9A0)))
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                cfg.pets.filter { it.id != cfg.outingId }.take(5).forEach { p ->
                    PetSprite(p.species, PetFx.actionFor(p, cfg, isOuting = false), 80.dp, outfits = p.worn, modifier =
                        Modifier.clickable { Nav.go("pet:${p.id}") })
                }
            }
            Text(
                if (cfg.pets.size <= 1) "屋里空空的，领养一只新伙伴吧" else "出门的那只不在屋里",
                Modifier.align(Alignment.TopStart).padding(14.dp), fontSize = 13.sp, color = Color(0xFF8C6A4A),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("我的宠物（${cfg.pets.size}）", Modifier.weight(1f), fontSize = 14.sp, color = P.sub, fontWeight = FontWeight.SemiBold)
            GhostButton("换装商店", icon = Ic.Gift) { Nav.go("shop") }
            Spacer(Modifier.width(8.dp))
            GhostButton("领养", icon = Ic.Heart) { Nav.go("adopt") }
        }
        Gap(6.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cfg.pets.forEach { p -> PetCard(p, p.id == cfg.outingId) { Nav.go("pet:${p.id}") } }
        }
        Gap(14.dp)
        Text("部屋里的宠物也会饿、会渴，进来喂一喂就好。体力耗尽的宠物会沉睡，不愿意出门。",
            fontSize = 12.sp, color = P.sub)
    }
}

@Composable
private fun PetCard(p: Pet, outing: Boolean, onClick: () -> Unit) {
    val cfg = Store.cfg
    Column(
        Modifier.width(164.dp).clip(RoundedCornerShape(20.dp)).background(P.card)
            .border(if (outing) 2.dp else 1.dp, if (outing) P.mint else P.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = P.ink, maxLines = 1)
            Text("Lv.${Catalog.level(p.exp)}", fontSize = 12.sp, color = P.mintDeep)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PetSprite(p.species, PetFx.actionFor(p, cfg, isOuting = outing), 96.dp, outfits = p.worn)
        }
        if (outing) Text("出门陪你中", fontSize = 12.sp, color = P.mint, fontWeight = FontWeight.SemiBold)
        else Text(PetSim.describe(p), fontSize = 12.sp, color = P.sub)
        Gap(6.dp)
        StatBar("体力", p.stamina, P.mint)
        Gap(4.dp)
        StatBar("饱食", p.satiety, P.gold)
        Gap(4.dp)
        StatBar("水分", p.hydration, P.blue)
    }
}

@Composable
fun PetDetailPage(id: String) {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val p = cfg.pets.firstOrNull { it.id == id } ?: run { Nav.back(); return }
    val outing = p.id == cfg.outingId
    var renaming by remember { mutableStateOf(false) }
    PageScaffold(p.name) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PetSprite(p.species, PetFx.actionFor(p, cfg, isOuting = outing), 200.dp, outfits = p.worn, modifier =
                Modifier.clickable { com.mengding.app.pet.PetVoice.play(ctx, p.species); toast(Actions.petPet(ctx, p.id)) })
        }
        Text("${Catalog.of(p.species).name} · ${Catalog.of(p.species).intro}", fontSize = 13.sp, color = P.sub)
        Gap()
        MCard {
            StatBar("体力", p.stamina, P.mint); Gap(8.dp)
            StatBar("饱食", p.satiety, P.gold); Gap(8.dp)
            StatBar("水分", p.hydration, P.blue)
        }
        Gap()
        HRow {
            if (outing) PrimaryButton("召回", Modifier.weight(1f)) { toast(Actions.recall(ctx, p.id)) }
            else PrimaryButton("让它出门", Modifier.weight(1f)) { toast(Actions.setOuting(ctx, p.id)) }
            GhostButton("改名", icon = Ic.Edit) { renaming = true }
        }
        Gap(10.dp)
        if (outing) Text("它现在正在出门，召回后会回到部屋。人的喝水和吃饭记录不会改变宠物状态。", fontSize = 13.sp, color = P.sub)
        MCard {
            HRow {
                Text("萌度", fontSize = 14.sp, color = P.sub)
                Spacer(Modifier.weight(1f))
                Text("${Outfits.cuteness(ctx, p.worn)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P.pink)
            }
            Text("根据当前穿着的服装价格计算，穿得越贵萌度越高。", fontSize = 12.sp, color = P.sub)
        }
        SectionTitle("喂食")
        FoodPicker(p)
        if (p.sleeping) {
            Gap(8.dp)
            GhostButton("叫醒它", icon = Ic.Bell) { toast(Actions.wakePet(ctx, p.id)) }
        }

        SectionTitle("等级")
        MCard {
            val lv = Catalog.level(p.exp)
            val (lo, hi) = Catalog.expRange(p.exp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lv.$lv", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P.mintDeep)
                Spacer(Modifier.width(10.dp))
                Text(if (hi > lo) "经验 ${p.exp - lo} / ${hi - lo}" else "已满级", fontSize = 13.sp, color = P.sub)
            }
            Gap(8.dp)
            StatBar("经验", if (hi > lo) (p.exp - lo) * 100f / (hi - lo) else 100f, P.purple)
            Gap(10.dp)
            Text("陪伴、被摸、宠物喂食都会涨经验；人的喝水和吃饭只记录在首页。", fontSize = 12.sp, color = P.sub)
            Gap(8.dp)
            Catalog.unlocks.forEach { (need, name) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (lv >= need) Ic.Check else Ic.Lock, null, Modifier.size(16.dp), tint = if (lv >= need) P.mint else P.sub)
                    Spacer(Modifier.width(6.dp))
                    Text("Lv.$need 解锁「$name」", fontSize = 14.sp, color = if (lv >= need) P.ink else P.sub)
                }
            }
            Text("已解锁动作可以在桌面长按菜单中手动触发，空闲时也会随机出现。", fontSize = 12.sp, color = P.sub)
        }

        SectionTitle("换装")
        WardrobeCard(p)
    }
    if (renaming) {
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("改名") },
            text = { OutlinedTextField(name, { name = it.take(8) }, singleLine = true) },
            confirmButton = { TextButton({
                val msg = Actions.rename(ctx, p.id, name)
                toast(msg)
                if (msg.startsWith("改名成功")) renaming = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ renaming = false }) { Text("取消") } },
        )
    }
}

@Composable
fun AdoptPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    var picking by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val price = Catalog.price(cfg.pets.size)
    PageScaffold("领养新伙伴") {
        MCard {
            HRow {
                PointsIcon(24.dp)
                Text("我的萌点：${cfg.economy.points}", Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
                Text("这一只 $price 萌点", fontSize = 13.sp, color = P.sub)
            }
            Gap(8.dp)
            Text("打卡领养：连续 7 天喝水达标，或连续 7 天按时睡觉，各送一次免费领养。", fontSize = 12.sp, color = P.sub)
            Text("喝水连续 ${cfg.economy.waterStreak} 天 · 按时睡觉连续 ${cfg.economy.sleepStreak} 天 · 免费次数 ${cfg.economy.freeAdopts}",
                fontSize = 12.sp, color = P.mintDeep)
        }
        Gap()
        Catalog.all.forEach { sp ->
            val owned = cfg.pets.count { it.species == sp.key }
            MCard(Modifier.padding(bottom = 10.dp)) {
                HRow(gap = 12.dp) {
                    PetSprite(sp.key, "idle", 72.dp)
                    Column(Modifier.weight(1f)) {
                        Text(sp.name + if (owned > 0) "（已有 $owned 只）" else "", fontWeight = FontWeight.SemiBold, color = P.ink)
                        Text(sp.intro, fontSize = 12.sp, color = P.sub)
                    }
                }
                Gap(8.dp)
                HRow {
                    GhostButton("$price 萌点领养", Modifier.weight(1f), enabled = cfg.economy.points >= price) { picking = sp.key to false }
                    if (cfg.economy.freeAdopts > 0) PrimaryButton("免费领养", Modifier.weight(1f)) { picking = sp.key to true }
                }
            }
        }
    }
    picking?.let { (key, free) ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("给它起个名字") },
            text = { OutlinedTextField(name, { name = it.take(8) }, placeholder = { Text(Catalog.of(key).defaultName) }, singleLine = true) },
            confirmButton = { TextButton({ toast(Actions.adopt(ctx, key, name, free)); picking = null }) { Text("领养") } },
            dismissButton = { TextButton({ picking = null }) { Text("取消") } },
        )
    }
}

/** 宠物详情里的"衣柜"：按分类列出已买的服装，点一下穿上（同分类换下旧的），再点一下脱下 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WardrobeCard(p: Pet) {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    val all = com.mengding.app.pet.Outfits.all(ctx)
    val owned = all.filter { it.key in cfg.economy.ownedOutfits }
    MCard {
        if (owned.isEmpty()) Text("衣柜还是空的，去换装商店用萌点买吧。", fontSize = 14.sp, color = P.sub)
        com.mengding.app.pet.Outfits.CATEGORIES.reversed().forEach { cat ->
            val list = owned.filter { it.category == cat }
            if (list.isEmpty()) return@forEach
            Text(cat, Modifier.padding(top = 6.dp, bottom = 6.dp), fontSize = 13.sp, color = P.sub, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { o ->
                    val on = o.key in p.worn
                    Column(
                        Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).background(if (on) P.mintSoft else P.card)
                            .border(if (on) 2.dp else 1.dp, if (on) P.mint else P.line, RoundedCornerShape(14.dp))
                            .clickable { Actions.wear(ctx, p.id, o.key, !on) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PixelIcon(o.key, 48.dp)
                        Text(if (on) "穿着中" else o.name, fontSize = 11.sp, color = if (on) P.mintDeep else P.ink, maxLines = 1)
                    }
                }
            }
        }
        Gap(10.dp)
        HRow {
            GhostButton("去换装商店", icon = Ic.Gift) { Nav.go("shop") }
            if (p.worn.isNotEmpty()) GhostButton("全部脱下") { Actions.undress(ctx, p.id) }
        }
    }
}

/** 32×32 像素图标（服装或食物），最近邻放大 */
@Composable
fun PixelIcon(key: String, size: androidx.compose.ui.unit.Dp, food: Boolean = false) {
    val ctx = LocalContext.current
    val img = remember(key) {
        (if (food) com.mengding.app.pet.Foods.iconBitmap(ctx, key) else com.mengding.app.pet.Outfits.iconBitmap(ctx, key)).asImageBitmap()
    }
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        drawImage(img, dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
    }
}

/** 换装商店：按分类（帽子 / 发饰 / 眼镜 / 围巾 / 披风）列出，插画版 + 像素版图标，萌点购买，买完直接给出门的宠物穿上 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val pet = PetSim.outing(cfg)
    var cat by remember { mutableStateOf("全部") }
    val cats = listOf("全部") + com.mengding.app.pet.Outfits.CATEGORIES.reversed()
    PageScaffold("换装商店") {
        MCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                pet?.let { PetSprite(it.species, "energetic", 96.dp, outfits = it.worn) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pet?.let { "${it.name} 在试衣镜前" } ?: "试衣镜", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text("我的萌点：${cfg.economy.points}", fontSize = 13.sp, color = P.sub)
                    Text("每个分类可以穿一件，几件可以一起穿", fontSize = 12.sp, color = P.sub)
                }
            }
        }
        Gap()
        OutfitShopContent()
    }
}

/** 服装货架：分类筛选 + 插画 / 像素图标 + 购买、穿上、脱下 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutfitShopContent() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    val pet = PetSim.outing(cfg)
    var cat by remember { mutableStateOf("全部") }
    val cats = listOf("全部") + com.mengding.app.pet.Outfits.CATEGORIES.reversed()
    run {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c -> Pill(c, cat == c) { cat = c } }
        }
        Gap()
        val shown = com.mengding.app.pet.Outfits.all(ctx).filter { cat == "全部" || it.category == cat }
        shown.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { o ->
                val owned = o.key in cfg.economy.ownedOutfits
                val wearing = pet != null && o.key in pet.worn
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(P.card)
                        .border(if (wearing) 2.dp else 1.dp, if (wearing) P.mint else P.line, RoundedCornerShape(20.dp)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        com.mengding.app.pet.Outfits.art[o.key]?.let {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(it), o.name, Modifier.size(88.dp))
                        }
                        Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(8.dp)).background(P.cream).padding(2.dp)) {
                            PixelIcon(o.key, 34.dp)
                        }
                        Text(o.category, Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(8.dp)).background(P.mintSoft)
                            .padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 10.sp, color = P.mintDeep)
                    }
                    Gap(6.dp)
                    Text(o.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Gap(6.dp)
                    when {
                        !owned -> PrimaryButton("${o.price} 萌点", Modifier.fillMaxWidth(), pixelIcon = "adv_coin_0") {
                            val m = Actions.buyOutfit(ctx, o.key); toast(m)
                            if (m.startsWith("买到了") && pet != null) Actions.wear(ctx, pet.id, o.key, true)
                        }
                        pet == null -> Text("已拥有", color = P.sub)
                        wearing -> GhostButton("脱下", Modifier.fillMaxWidth()) { Actions.wear(ctx, pet.id, o.key, false) }
                        else -> GhostButton("穿上", Modifier.fillMaxWidth()) { Actions.wear(ctx, pet.id, o.key, true) }
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        } }
    }
}

/** 喂食：7 种食物 + 5 种饮料，点一下就喂；带爱心的是这只宠物最爱的 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodPicker(p: Pet) {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    var drink by remember { mutableStateOf(false) }
    MCard {
        HRow {
            Pill("食物", !drink) { drink = false }
            Pill("饮料", drink) { drink = true }
            Spacer(Modifier.weight(1f))
            PointsIcon(18.dp)
            Text("${cfg.economy.points}", fontSize = 13.sp, color = P.ink)
        }
        Gap(10.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.mengding.app.pet.Foods.all(ctx).filter { it.drink == drink }.forEach { f ->
                val fav = p.species in f.favorite
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(16.dp)).background(if (fav) P.pinkSoft else P.cream)
                        .clickable { toast(Actions.feedItem(ctx, p.id, f.key)) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        PixelIcon(f.key, 48.dp, food = true)
                        if (fav) Icon(Ic.Heart, "最爱", Modifier.align(Alignment.TopEnd).size(14.dp), tint = P.pink)
                    }
                    Text(f.name, fontSize = 12.sp, color = P.ink, maxLines = 1)
                    Text(com.mengding.app.pet.Foods.effect(f, p.species), fontSize = 10.sp, color = P.sub, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    val have = cfg.economy.pantry[f.key] ?: 0
                    Text(if (have > 0) "库存 $have" else "${f.price} 萌点", fontSize = 11.sp, color = P.mintDeep)
                }
            }
        }
    }
}
