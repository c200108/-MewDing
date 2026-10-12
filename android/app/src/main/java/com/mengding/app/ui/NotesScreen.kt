package com.mengding.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.BlockType
import com.mengding.app.data.Note
import com.mengding.app.data.NoteReminder
import com.mengding.app.data.Repeat
import com.mengding.app.data.Store
import com.mengding.app.logic.NoteMedia
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders
import java.time.format.DateTimeFormatter
import java.util.UUID

/** 便签列表（需求：便签模块） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen() {
    val ctx = LocalContext.current
    val state by Store.notes.collectAsState()
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Note?>(null) }
    val list = state.notes
        .filter { query.isBlank() || noteText(it).contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Nav.stack.isNotEmpty()) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { Nav.back() }, contentAlignment = Alignment.Center) {
                        Icon(Ic.Back, "返回", Modifier.size(22.dp), tint = P.ink)
                    }
                }
                Text("便签", Modifier.padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.ink)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Search, null, Modifier.size(18.dp), tint = P.sub)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("搜索便签", color = P.sub, fontSize = 15.sp)
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = P.ink), cursorBrush = SolidColor(P.mint))
                }
            }
            Gap(10.dp)
            // 关心 TA：在「我的 → 提醒设置」里打开后才出现
            if (com.mengding.app.logic.Period.careEnabled(Store.config.collectAsState().value)) {
                CareEntry()
                Gap(10.dp)
            }
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.Note, null, Modifier.size(48.dp), tint = P.line)
                    Gap(10.dp)
                    Text(if (query.isBlank()) "还没有便签，点右下角写一条" else "没有找到", color = P.sub)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { n ->
                    NoteCard(n, Modifier.combinedClickable(onClick = { Nav.go("note:${n.id}") }, onLongClick = { menuFor = n }))
                    Gap(10.dp)
                }
                item { Gap(80.dp) }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(58.dp).clip(RoundedCornerShape(29.dp))
                .background(P.mint).clickable { Nav.go("note:new") },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.Plus, "新建便签", Modifier.size(28.dp), tint = Color.White) }
    }

    menuFor?.let { n ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(n.title.ifBlank { "便签" }) },
            text = {
                Column {
                    Text(if (n.pinned) "取消置顶" else "置顶", Modifier.fillMaxWidth().clickable {
                        Store.updateNotes { s -> s.copy(notes = s.notes.map { if (it.id == n.id) it.copy(pinned = !it.pinned) else it }) }
                        menuFor = null
                    }.padding(vertical = 12.dp), fontSize = 16.sp, color = P.ink)
                    Text("删除", Modifier.fillMaxWidth().clickable {
                        deleteNote(n); Reminders.reschedule(ctx); menuFor = null
                    }.padding(vertical = 12.dp), fontSize = 16.sp, color = P.danger)
                }
            },
            confirmButton = { TextButton({ menuFor = null }) { Text("取消") } },
        )
    }
}

fun deleteNote(n: Note) {
    n.blocks.forEach { NoteMedia.delete(it.file) }
    Store.updateNotes { s -> s.copy(notes = s.notes.filterNot { it.id == n.id }) }
}

fun noteText(n: Note): String = buildString {
    append(n.title).append('\n')
    n.blocks.forEach { b ->
        if (b.type == BlockType.TEXT) append(b.text).append('\n')
        if (b.type == BlockType.TABLE) b.table.forEach { r -> append(r.joinToString(" ")).append('\n') }
    }
}

@Composable
private fun NoteCard(n: Note, modifier: Modifier) {
    val preview = n.blocks.filter { it.type == BlockType.TEXT && it.text.isNotBlank() }.joinToString("  ") { it.text.replace('\n', ' ') }
    val images = n.blocks.count { it.type == BlockType.IMAGE }
    val audios = n.blocks.count { it.type == BlockType.AUDIO }
    val tables = n.blocks.count { it.type == BlockType.TABLE }
    MCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.pinned) { Icon(Ic.Pin, null, Modifier.size(16.dp), tint = P.pink); Spacer(Modifier.width(4.dp)) }
            Text(n.title.ifBlank { preview.take(20).ifBlank { "无标题" } }, Modifier.weight(1f), fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Time.dateTimeOf(n.updatedAt).format(DateTimeFormatter.ofPattern("M/d HH:mm")), fontSize = 12.sp, color = P.sub)
        }
        if (preview.isNotBlank()) {
            Gap(4.dp)
            Text(preview, fontSize = 14.sp, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (images + audios + tables > 0 || n.reminder?.enabled == true) {
            Gap(8.dp)
            HRow(gap = 10.dp) {
                if (images > 0) Tag(Ic.Image, "$images")
                if (audios > 0) Tag(Ic.Music, "$audios")
                if (tables > 0) Tag(Ic.Table, "$tables")
                n.reminder?.takeIf { it.enabled }?.let { Tag(Ic.Bell, reminderLabel(it)) }
            }
        }
    }
}

@Composable
private fun Tag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = P.mintDeep)
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 12.sp, color = P.mintDeep)
    }
}

fun reminderLabel(r: NoteReminder): String = when (r.repeat) {
    Repeat.ONCE -> (r.date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        ?.let { "${it.monthValue}/${it.dayOfMonth} " } ?: "") + r.time
    Repeat.DATES -> "${r.dates.size} 个日期 ${r.time}"
    else -> "${r.repeat.label} ${r.time}"
}

/** 新建便签时先建一个空的，退出时如果什么都没写就删掉 */
fun newNote(): Note {
    val now = Time.now()
    val n = Note(UUID.randomUUID().toString(), createdAt = now, updatedAt = now,
        blocks = listOf(com.mengding.app.data.Block(UUID.randomUUID().toString())))
    Store.updateNotes { it.copy(notes = it.notes + n) }
    return n
}

/** 便签页顶上固定的「关心 TA」入口：显示最近要来的那位，点进去管理 */
@Composable
private fun CareEntry() {
    val priv by Store.private.collectAsState()
    val soon = priv.care.mapNotNull { c -> com.mengding.app.logic.Period.careNext(c)?.let { c to it } }.minByOrNull { it.second }
    val inPeriod = priv.care.firstOrNull { com.mengding.app.logic.Period.careInPeriod(it) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.pinkSoft).clickable { Nav.go("care") }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Heart, null, Modifier.size(22.dp), tint = P.pink)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("关心 TA", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = P.ink)
            Text(
                when {
                    inPeriod != null -> "${inPeriod.name} 正在经期，多照顾 TA 一点"
                    soon != null -> "${soon.first.name} 大约 ${com.mengding.app.logic.Period.daysUntil(soon.second)} 天后"
                    else -> "帮伴侣、家人、朋友记经期，到时提醒你多照顾 TA"
                }, fontSize = 12.sp, color = P.sub, maxLines = 1,
            )
        }
        Icon(Ic.Next, null, Modifier.size(18.dp), tint = P.sub)
    }
}
