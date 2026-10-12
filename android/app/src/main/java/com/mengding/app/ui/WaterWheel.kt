package com.mengding.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 转轮选数：上下滑动，停下时自动对准中间那一格；中间那格就是选中的值。点上下的格子也能直接跳过去。
 */
@Composable
fun NumberWheel(values: List<Int>, initial: Int, unit: String, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemH = 44.dp
    val start = values.indexOf(initial).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: start
        }
    }
    LaunchedEffect(Unit) { snapshotFlow { center }.collect { onChange(values[it]) } }
    Box(modifier.fillMaxWidth().height(itemH * 5), contentAlignment = Alignment.Center) {
        // 中间的选中条
        Box(Modifier.fillMaxWidth().height(itemH).clip(RoundedCornerShape(14.dp)).background(P.mintSoft))
        LazyColumn(
            Modifier.fillMaxWidth().height(itemH * 5),
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemH * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(values) { i, v ->
                val d = abs(i - center)
                Box(
                    Modifier.fillMaxWidth().height(itemH).clickable { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$v $unit",
                        Modifier.alpha(when (d) { 0 -> 1f; 1 -> 0.55f; else -> 0.25f }),
                        fontSize = if (d == 0) 24.sp else 18.sp,
                        fontWeight = if (d == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (d == 0) P.mintDeep else P.ink,
                    )
                }
            }
        }
    }
}

/** 单次饮水量：100~400ml，每格 10ml */
val CUP_CHOICES = (100..400 step 10).toList()

/** 记一杯水：转轮选这一杯喝了多少；记住这次的量，下次默认就是它（通知、桌面宠物的"喝了"也用它） */
@Composable
fun DrinkDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val toast = toaster()
    val cfg = Store.config.value
    val ml = remember { intArrayOf(cfg.settings.cupMl.coerceIn(100, 400)) }
    val total = Actions.todayWater()
    val goal = cfg.settings.waterGoalMl
    val left = Actions.waterRoomLeft()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这一杯喝了多少？") },
        text = {
            Column {
                NumberWheel(CUP_CHOICES, ml[0], "ml", { ml[0] = it })
                Text(
                    "今天已喝 ${total}ml，目标 ${goal}ml。\n" +
                        if (left <= 0) "今天已经喝到目标的两倍，不能再记了。" else "今天最多还能记 ${left}ml（不超过目标的两倍）。",
                    Modifier.fillMaxWidth(), fontSize = 12.sp, color = P.sub, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton({
                val v = ml[0]
                Store.update { it.copy(settings = it.settings.copy(cupMl = v)) }
                toast(Actions.drink(ctx, v))
                onDismiss()
            }, enabled = left > 0) { Text("喝了") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
