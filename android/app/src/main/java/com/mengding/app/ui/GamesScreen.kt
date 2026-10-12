package com.mengding.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.Economy
import com.mengding.app.logic.PetSim
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GamesScreen() {
    PageScaffold("解压小游戏") {
        val best = Store.config.collectAsState().value.economy.gameBest
        GameCard("萌宠吃豆", "带着宠物在花园迷宫里吃豆豆，躲开捣蛋云；吃到铃铛豆就能反抓它们。" +
            (best["pac"]?.let { "  最高 $it" } ?: ""), true, pixel = "ui_icon_pac") { Nav.go("game_pac") }
        Gap(10.dp)
        GameCard("萌叮大冒险", "横版跳跃闯关：顶礼物盒、吃萌萌菇变大、拿魔力星无敌，跑到铃铛旗杆过关。" +
            (best["adv"]?.let { "  最高 $it" } ?: ""), true, pixel = "ui_icon_adv") { Nav.go("game_adv") }
        Gap(10.dp)
        GameCard("萌宠连连看", "把一样的萌宠连起来消掉；简单、普通、困难、无人生还四种难度，有提示、打乱、炸弹、冻结时间道具。",
            true, pixel = "ui_icon_link") { Nav.go("game_link") }
        Gap(10.dp)
        GameCard("理毛线", "把缠在一起的毛线一根根拉开理顺，织成宠物的小衣服。", false) {}
        Gap(10.dp)
        GameCard("墨池", "往水里滴颜料，用手指搅出大理石纹，可以存成壁纸。", false) {}
        Gap(14.dp)
        Text("理毛线和墨池会在后续版本上线，届时用萌点解锁。", fontSize = 12.sp, color = P.sub)
    }
}

@Composable
private fun GameCard(title: String, desc: String, open: Boolean, pixel: String? = null, onClick: () -> Unit) {
    MCard(Modifier.clickable(enabled = open, onClick = onClick)) {
        HRow(gap = 14.dp) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(if (open) P.mintSoft else P.line),
                contentAlignment = Alignment.Center) {
                if (pixel != null) PixelImg(pixel, 44.dp)
                else Icon(if (open) Ic.Cloud else Ic.Lock, null, Modifier.size(28.dp), tint = if (open) P.mintDeep else P.sub)
            }
            Column(Modifier.weight(1f)) {
                Text(title + if (open) "" else "（即将上线）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                Text(desc, fontSize = 13.sp, color = P.sub)
            }
        }
    }
}
