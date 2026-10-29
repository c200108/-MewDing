package com.mengding.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.CustomReminder
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Customs
import com.mengding.app.logic.Time
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** 一条自定义提醒的时间说明：「08:30、20:30 · 每天」「09:00–18:00 每 60 分钟 · 工作日」 */
fun customSummary(r: CustomReminder): String {
    val time = if (r.mode == "interval") "${r.from}–${r.to} 每 ${r.everyMin} 分钟" else r.times.sorted().joinToString("、")
    val rep = when (r.repeat) {
        "workdays" -> "工作日"
        "weekdays" -> "每周" + r.weekdays.sorted().joinToString("") { WEEK[it - 1] }
        else -> "每天"
    }
    return "$time · $rep" + (r.endDate?.let { " · 到 $it" } ?: "")
}

private val WEEK = listOf("一", "二", "三", "四", "五", "六", "日")

/** 我的 → 提醒设置 → 自定义提醒：列表 + 从模板新建 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomRemindersPage() {
    val ctx = LocalContext.current
    val priv by Store.private.collectAsState()
    PageScaffold("自定义提醒") {
        Text("吃药、久坐、护眼、护肤……想让宠物提醒什么都行。到点和喝水吃饭一样弹提醒，点「完成了」+2 萌点（和喝水吃饭共用每天 30 的上限）。",
            fontSize = 13.sp, color = P.sub)
        Gap(10.dp)
        if (priv.customs.isNotEmpty()) MCard(padding = 12.dp) {
            priv.customs.forEachIndexed { i, r ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable { Nav.go("custom:${r.id}") }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CustomIcon(r.icon, r.enabled)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 15.sp, color = if (r.enabled) P.ink else P.sub)
                        Text(customSummary(r), fontSize = 12.sp, color = P.sub)
                    }
                    MdSwitch(r.enabled, { v -> Actions.saveCustom(ctx, r.copy(enabled = v)) })
                }
            }
        }
        SectionTitle("新建")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Customs.templates.forEachIndexed { i, t -> Pill(t.name, false) { Nav.go("custom:new$i") } }
            Pill("自己建一个", true) { Nav.go("custom:new") }
        }
    }
}

@Composable
fun CustomIcon(key: String, on: Boolean = true, size: androidx.compose.ui.unit.Dp = 36.dp) {
    MdRowIcon(Ic.custom(key), if (on) P.mintDeep else P.sub, size)
}

/** 编辑一条自定义提醒。arg：已有的 id，或 new（空白）/ new0..3（模板） */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomEditPage(arg: String) {
    val ctx = LocalContext.current
    val toast = toaster()
    val isNew = arg.startsWith("new")
    val initial = remember(arg) {
        val id = UUID.randomUUID().toString()
        when {
            arg == "new" -> CustomReminder(id, "")
            isNew -> Customs.templates.getOrNull(arg.removePrefix("new").toIntOrNull() ?: -1)?.make?.invoke(id) ?: CustomReminder(id, "")
            else -> Store.priv.customs.firstOrNull { it.id == arg }
        }
    }
    if (initial == null) { androidx.compose.runtime.LaunchedEffect(Unit) { Nav.back() }; return }
    var r by remember(arg) { mutableStateOf(initial) }
    var addTime by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    PageScaffold(if (isNew) "新建提醒" else "编辑提醒") {
        MCard(padding = 12.dp) {
            OutlinedTextField(r.name, { r = r.copy(name = it.take(12)) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("名字（如：吃药）") })
            Gap(10.dp)
            Text("小图标", fontSize = 14.sp, color = P.ink)
            Gap(6.dp)
            HRow {
                Customs.icons.forEach { k ->
                    Box(Modifier.clip(mdShape(10.dp)).border(2.dp, if (r.icon == k) P.mint else Color.Transparent, mdShape(10.dp))
                        .clickable { r = r.copy(icon = k) }.padding(3.dp)) { CustomIcon(k) }
                }
            }
        }
        SectionTitle("时间")
        MCard(padding = 12.dp) {
            HRow {
                Pill("几个固定时刻", r.mode != "interval") { r = r.copy(mode = "fixed") }
                Pill("每隔一段时间", r.mode == "interval") { r = r.copy(mode = "interval") }
            }
            Gap(8.dp)
            if (r.mode == "interval") {
                StepperRow("每隔", r.everyMin, 15, 15, 240, "分钟") { v -> r = r.copy(everyMin = v) }
                TimeRow("从", r.from) { t -> r = r.copy(from = t) }
                TimeRow("到", r.to) { t -> r = r.copy(to = t) }
                Text("这个时段里每隔 ${r.everyMin} 分钟提醒一次，共 ${Customs.slots(r).size} 次；勿扰时段里不响。",
                    fontSize = 12.sp, color = P.sub)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.times.sorted().forEach { t ->
                        Pill("$t  ×", true) { if (r.times.size > 1) r = r.copy(times = r.times - t) else toast("至少留一个时刻") }
                    }
                    Pill("+ 加一个时刻", false) { addTime = true }
                }
                Text("点时刻上的 × 删掉。每个时刻完成一次就不再响。", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = P.sub)
            }
        }
        SectionTitle("重复")
        MCard(padding = 12.dp) {
            HRow {
                listOf("daily" to "每天", "workdays" to "工作日", "weekdays" to "自选周几").forEach { (k, v) ->
                    Pill(v, r.repeat == k) { r = r.copy(repeat = k, weekdays = if (k == "weekdays" && r.weekdays.isEmpty()) listOf(1, 3, 5) else r.weekdays) }
                }
            }
            if (r.repeat == "weekdays") {
                Gap(8.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { d ->
                        val on = d in r.weekdays
                        Box(Modifier.size(38.dp).mdTile(on, radius = 19.dp)
                            .clickable { r = r.copy(weekdays = if (on) r.weekdays - d else r.weekdays + d) }, contentAlignment = Alignment.Center) {
                            Text(WEEK[d - 1], color = if (on) P.mintDeep else P.sub, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            Gap(6.dp)
            ValueRow("结束日期", r.endDate ?: "不结束") { pickEnd = true }
            ToggleRow("开启这条提醒", checked = r.enabled) { v -> r = r.copy(enabled = v) }
        }
        Gap(16.dp)
        PrimaryButton("保存", Modifier.fillMaxWidth()) {
            when {
                r.name.isBlank() -> toast("给提醒起个名字吧")
                r.repeat == "weekdays" && r.weekdays.isEmpty() -> toast("至少选一天")
                else -> { Actions.saveCustom(ctx, r.copy(name = r.name.trim())); toast("保存好了"); Nav.back() }
            }
        }
        if (!isNew) {
            Gap(8.dp)
            GhostButton("删除这条提醒", Modifier.fillMaxWidth(), icon = Ic.Trash) { confirmDelete = true }
        }
    }
    if (addTime) TimeDialog("12:00", "加一个时刻", { addTime = false }) { t -> addTime = false; if (t !in r.times) r = r.copy(times = r.times + t) }
    if (pickEnd) DateDialog({ pickEnd = false }, initial = r.endDate, title = "结束日期",
        extra = "不结束" to { r = r.copy(endDate = null); pickEnd = false }) { d -> r = r.copy(endDate = d); pickEnd = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除「${r.name}」？") },
        text = { Text("这条提醒和它的完成记录都会删掉。") },
        confirmButton = { TextButton({ Actions.deleteCustom(ctx, r.id); confirmDelete = false; Nav.back() }) { Text("删除", color = P.pink) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
    )
}

/**
 * 首页吃饭卡下面的「今天的其他提醒」：今天还没做完的自定义提醒，可以提前打勾；间隔型只显示今天完成几次。没有就整张不显示。
 */
@Composable
fun OtherRemindersCard() {
    val ctx = LocalContext.current
    val toast = toaster()
    val priv by Store.private.collectAsState()
    val today = Time.today()
    val list = remember(priv, today) { Customs.todayList(priv, today) }
    if (list.isEmpty()) return
    Gap(10.dp)
    MCard(padding = 14.dp) {
        Text("今天的其他提醒", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
        list.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CustomIcon(r.icon, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 15.sp, color = P.ink)
                    val sub = if (r.mode == "interval") "今天完成 ${Customs.doneCount(priv, r.id, today)} 次 · 每 ${r.everyMin} 分钟"
                        else "还没打勾：" + Customs.pending(r, priv, today).joinToString("、") { Time.fmt(it) }
                    Text(sub, fontSize = 12.sp, color = P.sub)
                }
                Box(Modifier.size(34.dp).mdTile(radius = 17.dp).clickable { toast(Actions.customDone(ctx, r.id)) },
                    contentAlignment = Alignment.Center) {
                    MdIcon(if (r.mode == "interval") Ic.Plus else Ic.Check, if (r.mode == "interval") "完成一次" else "提前打勾",
                        Modifier.size(20.dp), P.mintDeep)
                }
            }
        }
    }
}
