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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.CarePerson
import com.mengding.app.data.DayNote
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Economy
import com.mengding.app.logic.Period
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun PeriodScreen() {
    val cfg by Store.config.collectAsState()
    PageScaffold("经期") {
        MyPeriod(cfg.settings.ovulationShown)
        Gap(10.dp)
        Text("想帮伴侣、家人或朋友记经期，可以用「关心 TA」（在「我的 → 提醒设置」里开关，打开后在「便签」里）。", fontSize = 12.sp, color = P.sub)
        Gap()
        Text("预测根据你的记录推算，仅供参考，不能用于避孕或医疗判断。", fontSize = 12.sp, color = P.sub)
    }
}

@Composable
private fun MyPeriod(showOvulation: Boolean) {
    val priv by Store.private.collectAsState()
    val f = Period.forecast(priv)
    var month by remember { mutableStateOf(YearMonth.now(Time.zone())) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val today = Time.today()

    MCard {
        Text(
            when {
                f.current != null -> "经期第 ${java.time.temporal.ChronoUnit.DAYS.between(f.current.start, today) + 1} 天"
                f.nextStart == null -> "还没有记录"
                else -> "距离下次经期约 ${Period.daysUntil(f.nextStart)} 天"
            }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P.ink,
        )
        Gap(4.dp)
        Text(
            if (f.nextStart == null) "在下面的日历上点一下经期开始的那天，标记为经期。"
            else "预计 ${f.nextStart.monthValue} 月 ${f.nextStart.dayOfMonth} 日开始 · 周期约 ${f.cycleLen} 天 · 经期约 ${f.periodLen} 天",
            fontSize = 13.sp, color = P.sub,
        )
        if (f.nextStart != null) Text(f.confidence, fontSize = 12.sp, color = P.sub)
        if (showOvulation && f.ovulation != null) {
            Text("预计排卵日 ${f.ovulation.monthValue} 月 ${f.ovulation.dayOfMonth} 日，易孕期 ${f.fertileFrom?.dayOfMonth} 日到 ${f.fertileTo?.dayOfMonth} 日",
                fontSize = 12.sp, color = P.purple)
        }
        if (f.current != null) {
            Gap(8.dp)
            Text("今天的关怀：${Period.tipOfDay(Period.careTips)}", fontSize = 13.sp, color = P.pink)
        }
    }
    Gap()
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Ic.Back) { month = month.minusMonths(1) }
            Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f), fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, color = P.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            RoundIcon(Ic.Next) { month = month.plusMonths(1) }
        }
        Gap(10.dp)
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), fontSize = 12.sp, color = P.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val predicted = mutableSetOf<LocalDate>()
        f.nextStart?.let { s -> repeat(f.periodLen) { predicted += s.plusDays(it.toLong()) } }
        val fertile = mutableSetOf<LocalDate>()
        if (showOvulation && f.fertileFrom != null && f.fertileTo != null) {
            var d: LocalDate = f.fertileFrom
            while (!d.isAfter(f.fertileTo)) { fertile += d; d = d.plusDays(1) }
        }
        val marked = priv.periodDays.toSet()
        val noted = priv.notes.map { it.day }.toSet()
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val idx = row * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 0 until month.lengthOfMonth()) {
                            val d = month.atDay(idx + 1)
                            val isMarked = d.toString() in marked
                            val bg = when {
                                isMarked -> P.pink
                                d in predicted -> P.pinkSoft
                                d in fertile -> P.purple.copy(alpha = 0.25f)
                                else -> Color.Transparent
                            }
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(bg)
                                    .then(if (d == today) Modifier.border(2.dp, P.mint, CircleShape) else Modifier)
                                    .clickable { day = d },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${d.dayOfMonth}", fontSize = 14.sp, color = if (isMarked) Color.White else P.ink)
                                if (d.toString() in noted) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                                    .size(4.dp).clip(CircleShape).background(if (isMarked) Color.White else P.pink))
                            }
                        }
                    }
                }
            }
        }
        Gap(8.dp)
        HRow(gap = 14.dp) {
            Legend(P.pink, "经期"); Legend(P.pinkSoft, "预测")
            if (showOvulation) Legend(P.purple.copy(alpha = 0.25f), "易孕期")
        }
    }
    day?.let { DaySheet(it) { day = null } }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = P.sub)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySheet(d: LocalDate, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val toast = toaster()
    val priv by Store.private.collectAsState()
    val key = d.toString()
    val note = priv.notes.firstOrNull { it.day == key } ?: DayNote(key)
    val marked = key in priv.periodDays
    fun save(n: DayNote) {
        Store.updatePrivate { p -> p.copy(notes = p.notes.filterNot { it.day == key } + n) }
        Store.update { Economy.record(it).first }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${d.monthValue} 月 ${d.dayOfMonth} 日") },
        text = {
            Column {
                ToggleRow("这天是经期", checked = marked) { toast(Actions.togglePeriodDay(ctx, key)) }
                if (marked) {
                    Text("流量", fontSize = 13.sp, color = P.sub)
                    HRow { listOf("少", "中", "多").forEachIndexed { i, s -> Pill(s, note.flow == i + 1) { save(note.copy(flow = i + 1)) } } }
                    Gap(8.dp)
                    Text("痛感", fontSize = 13.sp, color = P.sub)
                    HRow { listOf("不痛", "轻微", "明显", "很痛").forEachIndexed { i, s -> Pill(s, note.pain == i) { save(note.copy(pain = i)) } } }
                }
                Gap(8.dp)
                Text("心情", fontSize = 13.sp, color = P.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("开心", "平静", "烦躁", "低落", "敏感").forEach { m ->
                        Pill(m, m in note.moods) { save(note.copy(moods = if (m in note.moods) note.moods - m else note.moods + m)) }
                    }
                }
                Gap(8.dp)
                Text("身体", fontSize = 13.sp, color = P.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("腹痛", "腰酸", "头痛", "乳房胀痛", "长痘", "疲惫", "失眠", "食欲大").forEach { m ->
                        Pill(m, m in note.symptoms) {
                            save(note.copy(symptoms = if (m in note.symptoms) note.symptoms - m else note.symptoms + m))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("完成") } },
    )
}

/** 关心 TA：所有人都能用（入口在便签页顶上）。帮伴侣、家人、朋友记经期，到时提醒你多照顾 TA */
@Composable
fun CarePage() {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    PageScaffold("关心 TA") {
        MCard(padding = 12.dp) {
            ToggleRow("提醒我关心 TA", "TA 经期前 2 天提醒你备好红糖、少惹 TA 生气；经期中每天一条照顾 TA 的小建议",
                cfg.settings.careModeOn) { v ->
                Store.update { it.copy(settings = it.settings.copy(careModeOn = v)) }
                Reminders.reschedule(ctx)
            }
        }
        Gap(10.dp)
        CareSection()
        Gap()
        Text("预测根据你替 TA 记的日期推算，仅供参考。这些记录只存在你手机上，不会通知对方。", fontSize = 12.sp, color = P.sub)
    }
}

@Composable
private fun CareSection() {
    val ctx = LocalContext.current
    val toast = toaster()
    val priv by Store.private.collectAsState()
    var editing by remember { mutableStateOf<CarePerson?>(null) }
    if (priv.care.isEmpty()) {
        MCard { Text("帮伴侣或家人记经期，到时候提醒你多照顾 TA。这些记录只是你替 TA 记的，不会通知对方。", fontSize = 14.sp, color = P.sub) }
        Gap(10.dp)
    }
    priv.care.forEach { c ->
        val next = Period.careNext(c)
        val inP = Period.careInPeriod(c)
        MCard(Modifier.padding(bottom = 10.dp).clickable { editing = c }) {
            HRow {
                Icon(Ic.Heart, null, Modifier.size(22.dp), tint = P.pink)
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
                    Text(
                        when {
                            next == null -> "还没填最近一次开始日期"
                            inP -> "TA 正在经期 · ${Period.tipOfDay(Period.careTaTips)}"
                            else -> "下次大约 ${next.monthValue} 月 ${next.dayOfMonth} 日（${Period.daysUntil(next)} 天后）"
                        }, fontSize = 13.sp, color = P.sub,
                    )
                }
                Icon(Ic.Edit, null, Modifier.size(18.dp), tint = P.sub)
            }
        }
    }
    GhostButton("添加要关心的人", icon = Ic.Plus) { editing = CarePerson(UUID.randomUUID().toString(), "") }

    editing?.let { c0 ->
        var name by remember(c0.id) { mutableStateOf(c0.name) }
        var start by remember(c0.id) { mutableStateOf(c0.lastStart) }
        var cycle by remember(c0.id) { mutableStateOf(c0.cycle) }
        var len by remember(c0.id) { mutableStateOf(c0.length) }
        val isNew = priv.care.none { it.id == c0.id }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isNew) "添加要关心的人" else "编辑") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(10) }, label = { Text("称呼，比如老婆、妈妈") }, singleLine = true)
                    Gap(8.dp)
                    BirthdayRowLike("最近一次开始", start) { start = it }
                    StepperRow("周期", cycle, 1, 20, 45, "天") { cycle = it }
                    StepperRow("经期", len, 1, 2, 10, "天") { len = it }
                }
            },
            confirmButton = {
                TextButton({
                    if (name.isNotBlank()) {
                        val c = c0.copy(name = name.trim(), lastStart = start, cycle = cycle, length = len)
                        Store.updatePrivate { p -> p.copy(care = p.care.filterNot { it.id == c.id } + c) }
                        // 加第一个人时自动打开提醒
                        if (isNew && priv.care.isEmpty()) Store.update { it.copy(settings = it.settings.copy(careModeOn = true)) }
                        Store.update { Economy.record(it).first }
                        Reminders.reschedule(ctx)
                        toast("保存好了")
                    }
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                if (!isNew) TextButton({
                    Store.updatePrivate { p -> p.copy(care = p.care.filterNot { it.id == c0.id }) }
                    editing = null
                }) { Text("删除", color = P.danger) }
                else TextButton({ editing = null }) { Text("取消") }
            },
        )
    }
}

/** 选日期的一行（复用生日的日期选择框） */
@Composable
private fun BirthdayRowLike(title: String, value: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow(title, value ?: "选择日期") { open = true }
    if (open) DateDialog({ open = false }) { onChange(it); open = false }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState()
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let {
                    onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                } ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    ) { androidx.compose.material3.DatePicker(state) }
}
