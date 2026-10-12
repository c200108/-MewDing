package com.mengding.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.data.Block
import com.mengding.app.data.BlockType
import com.mengding.app.data.Note
import com.mengding.app.data.NoteReminder
import com.mengding.app.data.RemindMode
import com.mengding.app.data.Repeat
import com.mengding.app.data.Store
import com.mengding.app.data.TextSpec
import com.mengding.app.logic.NoteMedia
import com.mengding.app.logic.NotePlayer
import com.mengding.app.logic.NoteRecorder
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.PetService
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.UUID

private val NoteColors = listOf(
    Color.Unspecified, Color(0xFFE5606F), Color(0xFFF08A3C), Color(0xFF2FA37E), Color(0xFF4A8FE0), Color(0xFF9A72D8),
)

private fun newId() = UUID.randomUUID().toString()

private fun isEmpty(n: Note) = n.title.isBlank() && n.reminder == null &&
    n.blocks.all { it.type == BlockType.TEXT && it.text.isBlank() }

/** 便签编辑：按段落（块）编辑，每段可以单独设字号、粗斜体、下划线、删除线、颜色、对齐、列表、引用 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorPage(id: String) {
    val ctx = LocalContext.current
    val toast = toaster()
    if (id == "new") {
        LaunchedEffect(Unit) { val n = newNote(); Nav.back(); Nav.go("note:${n.id}") }
        return
    }
    val stored = remember(id) { Store.notesNow.notes.firstOrNull { it.id == id } }
    if (stored == null) { LaunchedEffect(Unit) { Nav.back() }; return }
    var note by remember(id) { mutableStateOf(stored) }
    var focused by remember { mutableStateOf<String?>(null) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    var showFmt by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val recorder = remember { NoteRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(note)

    fun edit(f: (Note) -> Note) { note = f(note).copy(updatedAt = Time.now()) }
    fun save(n: Note) = Store.updateNotes { s -> s.copy(notes = s.notes.map { if (it.id == n.id) n else it }) }
    fun setBlock(bid: String, f: (Block) -> Block) = edit { n -> n.copy(blocks = n.blocks.map { if (it.id == bid) f(it) else it }) }

    /** 插到当前段落后面；插入的不是文字时，后面再补一个空段落方便继续写 */
    fun insert(b: Block) = edit { n ->
        val list = n.blocks.toMutableList()
        val idx = list.indexOfFirst { it.id == focused }.let { if (it < 0) list.lastIndex else it }
        list.add(idx + 1, b)
        if (b.type != BlockType.TEXT && (idx + 2 > list.lastIndex || list[idx + 2].type != BlockType.TEXT)) {
            val t = Block(newId()); list.add(idx + 2, t); pendingFocus = t.id
        }
        n.copy(blocks = list)
    }

    LaunchedEffect(note) { delay(500); save(note) }
    DisposableEffect(id) {
        onDispose {
            NotePlayer.stop()
            if (recording) recorder.cancel()
            val n = latest
            if (isEmpty(n)) deleteNote(n) else save(n)
            Reminders.reschedule(ctx)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        NoteMedia.import(ctx, uri, NoteMedia.extOf(ctx, uri, "jpg"))?.let { insert(Block(newId(), BlockType.IMAGE, file = it)) }
            ?: toast("图片读取失败")
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = NoteMedia.import(ctx, uri, NoteMedia.extOf(ctx, uri, "m4a"))
        if (name == null) toast("音频读取失败")
        else insert(Block(newId(), BlockType.AUDIO, file = name, durationMs = NoteMedia.duration(name), text = "插入的音频"))
    }
    fun startRec() {
        if (recorder.start()) recording = true else toast("录音启动失败")
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else toast("没有麦克风权限，录不了音")
    }

    val focusedBlock = note.blocks.firstOrNull { it.id == focused && it.type == BlockType.TEXT }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        // 顶栏
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { Nav.back() }, contentAlignment = Alignment.Center) {
                Icon(Ic.Back, "返回", Modifier.size(22.dp), tint = P.ink)
            }
            Spacer(Modifier.weight(1f))
            TopIcon(Ic.Bell, if (note.reminder?.enabled == true) P.mint else P.sub) { showReminder = true }
            TopIcon(Ic.Pin, if (note.pinned) P.pink else P.sub) { edit { it.copy(pinned = !it.pinned) } }
            TopIcon(Ic.Trash, P.sub) { confirmDelete = true }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp)) {
            item {
                Box {
                    if (note.title.isEmpty()) Text("标题", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P.sub)
                    BasicTextField(note.title, { t -> edit { it.copy(title = t.replace("\n", "").take(60)) } },
                        Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P.ink),
                        cursorBrush = SolidColor(P.mint))
                }
                Text(Time.dateTimeOf(note.updatedAt).format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")),
                    Modifier.padding(top = 4.dp, bottom = 8.dp), fontSize = 12.sp, color = P.sub)
                note.reminder?.takeIf { it.enabled }?.let { r ->
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(P.mintSoft).clickable { showReminder = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Bell, null, Modifier.size(14.dp), tint = P.mintDeep)
                        Spacer(Modifier.width(4.dp))
                        Text("${reminderLabel(r)} · ${r.mode.label}", fontSize = 12.sp, color = P.mintDeep)
                    }
                    Gap(8.dp)
                }
            }
            itemsIndexed(note.blocks, key = { _, b -> b.id }) { i, b ->
                when (b.type) {
                    BlockType.TEXT -> {
                        val number = run {
                            var k = 1; var j = i - 1
                            while (j >= 0 && note.blocks[j].type == BlockType.TEXT && note.blocks[j].spec.list == 2) { k++; j-- }
                            k
                        }
                        TextBlock(
                            b, number, pendingFocus == b.id,
                            onFocus = { focused = b.id; if (pendingFocus == b.id) pendingFocus = null },
                            onText = { t -> setBlock(b.id) { it.copy(text = t) } },
                            onCheck = { setBlock(b.id) { it.copy(checked = !it.checked) } },
                            onSplit = { before, after ->
                                val nb = Block(newId(), text = after,
                                    spec = b.spec.copy(size = if (b.spec.size == 3) 1 else b.spec.size))
                                edit { n ->
                                    val l = n.blocks.toMutableList()
                                    val idx = l.indexOfFirst { it.id == b.id }
                                    l[idx] = l[idx].copy(text = before)
                                    l.add(idx + 1, nb)
                                    n.copy(blocks = l)
                                }
                                pendingFocus = nb.id
                            },
                            onDeleteEmpty = {
                                if (note.blocks.size > 1) {
                                    val prev = note.blocks.getOrNull(i - 1)?.takeIf { it.type == BlockType.TEXT }
                                    edit { n -> n.copy(blocks = n.blocks.filterNot { it.id == b.id }) }
                                    prev?.let { pendingFocus = it.id }
                                }
                            },
                        )
                    }
                    BlockType.IMAGE -> ImageBlock(b) { NoteMedia.delete(b.file); edit { n -> n.copy(blocks = n.blocks - b) } }
                    BlockType.AUDIO -> AudioBlock(b) { NotePlayer.stop(); NoteMedia.delete(b.file); edit { n -> n.copy(blocks = n.blocks - b) } }
                    BlockType.TABLE -> TableBlock(b, onChange = { t -> setBlock(b.id) { it.copy(table = t) } },
                        onDelete = { edit { n -> n.copy(blocks = n.blocks - b) } })
                }
            }
            item {
                // 点空白处在末尾接着写
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).clickable {
                    val last = note.blocks.lastOrNull()
                    if (last != null && last.type == BlockType.TEXT && last.text.isEmpty()) pendingFocus = last.id
                    else { val t = Block(newId()); edit { n -> n.copy(blocks = n.blocks + t) }; pendingFocus = t.id }
                })
            }
        }

        if (recording) RecordBar(recorder,
            onStop = {
                recording = false
                recorder.stop()?.let { insert(Block(newId(), BlockType.AUDIO, file = it, durationMs = NoteMedia.duration(it), recorded = true, text = "录音")) }
                    ?: toast("录音太短，没保存")
            },
            onCancel = { recording = false; recorder.cancel() })

        if (showFmt) {
            if (focusedBlock != null) FormatPanel(focusedBlock.spec) { sp -> setBlock(focusedBlock.id) { it.copy(spec = sp) } }
            else Text("先点一下要调整的那一段文字", Modifier.fillMaxWidth().background(P.card).padding(14.dp), fontSize = 13.sp, color = P.sub)
        }
        // 工具栏
        Row(
            Modifier.fillMaxWidth().background(P.card).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolBtn(Ic.TextFmt, "格式", showFmt) { showFmt = !showFmt }
            ToolBtn(Ic.Bullets, "列表") {
                focusedBlock?.let { fb -> setBlock(fb.id) { it.copy(spec = it.spec.copy(list = (it.spec.list + 1) % 4)) } }
                    ?: toast("先点一下要变成列表的那一段")
            }
            ToolBtn(Ic.Image, "图片") { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            ToolBtn(Ic.Mic, "录音", recording) {
                if (recording) return@ToolBtn
                if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
                else micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
            ToolBtn(Ic.Music, "音频") { pickAudio.launch(arrayOf("audio/*")) }
            ToolBtn(Ic.Table, "表格") { insert(Block(newId(), BlockType.TABLE, table = List(3) { List(3) { "" } })) }
            ToolBtn(Ic.Bell, "提醒", note.reminder?.enabled == true) { showReminder = true }
        }
    }

    if (showReminder) ReminderDialog(note.reminder, note.title, onDismiss = { showReminder = false }) { r ->
        edit { it.copy(reminder = r) }
        save(note.copy(reminder = r))
        Reminders.reschedule(ctx)
        showReminder = false
        r?.let { rr -> Reminders.noteNext(rr)?.let { toast("下次提醒：${Time.dateTimeOf(it).format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm"))}") } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除这条便签？") },
        text = { Text("里面的图片和录音也会一起删掉。") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                note = note.copy(title = "", blocks = listOf(Block(newId())), reminder = null)
                deleteNote(stored); Nav.back()
            }) { Text("删除", color = P.danger) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun TopIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
private fun ToolBtn(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) P.mintSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (active) P.mintDeep else P.ink)
        Text(label, fontSize = 10.sp, color = P.sub)
    }
}

@Composable
private fun TextBlock(
    b: Block, number: Int, wantFocus: Boolean,
    onFocus: () -> Unit, onText: (String) -> Unit, onCheck: () -> Unit,
    onSplit: (String, String) -> Unit, onDeleteEmpty: () -> Unit,
) {
    val sp = b.spec
    var tfv by remember(b.id) { mutableStateOf(TextFieldValue(b.text, TextRange(b.text.length))) }
    if (tfv.text != b.text) tfv = TextFieldValue(b.text, TextRange(b.text.length))
    val fr = remember(b.id) { FocusRequester() }
    LaunchedEffect(wantFocus) { if (wantFocus) runCatching { fr.requestFocus() } }
    val color = if (b.checked) P.sub else NoteColors.getOrElse(sp.color) { Color.Unspecified }.takeIf { it != Color.Unspecified } ?: P.ink
    val deco = listOfNotNull(
        TextDecoration.Underline.takeIf { sp.underline },
        TextDecoration.LineThrough.takeIf { sp.strike || b.checked },
    )
    val style = TextStyle(
        fontSize = listOf(13, 16, 19, 23)[sp.size.coerceIn(0, 3)].sp,
        fontWeight = if (sp.bold || sp.size == 3) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (sp.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
        color = color,
        textAlign = when (sp.align) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start },
        lineHeight = (listOf(13, 16, 19, 23)[sp.size.coerceIn(0, 3)] * 1.5f).sp,
    )
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .then(if (sp.quote) Modifier.background(P.mintSoft.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (sp.quote) { Box(Modifier.width(3.dp).fillMaxHeight().background(P.mint)); Spacer(Modifier.width(8.dp)) }
        when (sp.list) {
            1 -> Text("•", Modifier.width(18.dp), style = style.copy(textAlign = TextAlign.Start, textDecoration = null))
            2 -> Text("$number.", Modifier.widthIn(min = 22.dp).padding(end = 4.dp), style = style.copy(textAlign = TextAlign.Start, textDecoration = null))
            3 -> Box(
                Modifier.padding(top = 3.dp, end = 8.dp).size(18.dp).clip(RoundedCornerShape(5.dp))
                    .border(2.dp, P.mint, RoundedCornerShape(5.dp)).background(if (b.checked) P.mint else Color.Transparent)
                    .clickable(onClick = onCheck),
                contentAlignment = Alignment.Center,
            ) { if (b.checked) Icon(Ic.Check, null, Modifier.size(14.dp), tint = Color.White) }
        }
        Box(Modifier.weight(1f)) {
            if (tfv.text.isEmpty() && sp.list == 0 && !sp.quote) Text("写点什么…", style = style.copy(color = P.line))
            BasicTextField(
                tfv,
                onValueChange = { v ->
                    val cut = v.selection.start
                    if (v.selection.collapsed && cut > 0 && v.text.length == tfv.text.length + 1 && v.text[cut - 1] == '\n') {
                        val before = v.text.substring(0, cut - 1)
                        val after = v.text.substring(cut)
                        tfv = TextFieldValue(before, TextRange(before.length))
                        onSplit(before, after)
                        return@BasicTextField
                    }
                    tfv = v
                    if (v.text != b.text) onText(v.text)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(fr)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Backspace && it.type == KeyEventType.KeyDown && tfv.text.isEmpty()) { onDeleteEmpty(); true } else false
                    },
                textStyle = style,
                cursorBrush = SolidColor(P.mint),
            )
        }
    }
}

@Composable
private fun ImageBlock(b: Block, onDelete: () -> Unit) {
    val bmp = remember(b.file) { b.file?.let { NoteMedia.bitmap(it)?.asImageBitmap() } }
    var ask by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { ask = true },
            contentScale = ContentScale.FillWidth)
        else Text("图片丢失了", color = P.sub)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("这张图片") },
        confirmButton = { TextButton({ ask = false; onDelete() }) { Text("删除", color = P.danger) } },
        dismissButton = { TextButton({ ask = false }) { Text("取消") } },
    )
}

@Composable
private fun AudioBlock(b: Block, onDelete: () -> Unit) {
    val st by NotePlayer.state.collectAsState()
    val mine = st.file == b.file
    var pos by remember { mutableIntStateOf(0) }
    LaunchedEffect(mine, st.playing) {
        while (mine && st.playing) { pos = NotePlayer.position(); delay(200) }
        if (!mine) pos = 0
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(P.mintSoft).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(if (mine && st.playing) Ic.Pause else Ic.Play, 40.dp) { b.file?.let { NotePlayer.toggle(it) } }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (b.recorded) "录音" else b.text.ifBlank { "音频" }, fontSize = 14.sp, color = P.ink, fontWeight = FontWeight.Medium)
            Gap(4.dp)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.line)) {
                val f = if (b.durationMs > 0) (pos.toFloat() / b.durationMs).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth(f).height(4.dp).background(P.mint))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(if (mine) "${NoteMedia.fmt(pos.toLong())}/${NoteMedia.fmt(b.durationMs)}" else NoteMedia.fmt(b.durationMs),
            fontSize = 12.sp, color = P.sub)
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Ic.Close, "删除", Modifier.size(16.dp), tint = P.sub)
        }
    }
}

@Composable
private fun TableBlock(b: Block, onChange: (List<List<String>>) -> Unit, onDelete: () -> Unit) {
    val t = b.table.ifEmpty { listOf(listOf("")) }
    val cols = t.maxOf { it.size }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, P.mint.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))) {
            t.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    for (c in 0 until cols) {
                        val v = row.getOrElse(c) { "" }
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (r == 0) P.mintSoft else Color.Transparent)
                                .border(0.5.dp, P.mint.copy(alpha = 0.35f)).padding(6.dp),
                        ) {
                            BasicTextField(v, { nv ->
                                onChange(t.mapIndexed { ri, rr -> if (ri != r) rr else List(cols) { ci -> if (ci == c) nv else rr.getOrElse(ci) { "" } } })
                            }, Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 14.sp, color = P.ink, fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal),
                                cursorBrush = SolidColor(P.mint))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
            SmallText("+ 行") { onChange(t + listOf(List(cols) { "" })) }
            SmallText("+ 列") { if (cols < 6) onChange(t.map { it + "" }) }
            SmallText("- 行") { if (t.size > 1) onChange(t.dropLast(1)) }
            SmallText("- 列") { if (cols > 1) onChange(t.map { it.take(cols - 1) }) }
            SmallText("删除表格", P.danger, onDelete)
        }
    }
}

@Composable
private fun SmallText(text: String, color: Color = P.mintDeep, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        fontSize = 13.sp, color = color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatPanel(sp: TextSpec, onChange: (TextSpec) -> Unit) {
    Column(Modifier.fillMaxWidth().background(P.card).padding(horizontal = 14.dp, vertical = 10.dp)) {
        HRow(gap = 6.dp) {
            listOf("小", "正文", "大", "标题").forEachIndexed { i, s -> Pill(s, sp.size == i) { onChange(sp.copy(size = i)) } }
        }
        Gap(8.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            FmtToggle("B", sp.bold, TextStyle(fontWeight = FontWeight.Bold)) { onChange(sp.copy(bold = !sp.bold)) }
            FmtToggle("I", sp.italic, TextStyle(fontStyle = FontStyle.Italic)) { onChange(sp.copy(italic = !sp.italic)) }
            FmtToggle("U", sp.underline, TextStyle(textDecoration = TextDecoration.Underline)) { onChange(sp.copy(underline = !sp.underline)) }
            FmtToggle("S", sp.strike, TextStyle(textDecoration = TextDecoration.LineThrough)) { onChange(sp.copy(strike = !sp.strike)) }
            Spacer(Modifier.width(8.dp))
            listOf(Ic.AlignL, Ic.AlignC, Ic.AlignR).forEachIndexed { i, ic ->
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (sp.align == i) P.mintSoft else Color.Transparent)
                    .clickable { onChange(sp.copy(align = i)) }, contentAlignment = Alignment.Center) {
                    Icon(ic, null, Modifier.size(20.dp), tint = if (sp.align == i) P.mintDeep else P.ink)
                }
            }
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (sp.quote) P.mintSoft else Color.Transparent)
                .clickable { onChange(sp.copy(quote = !sp.quote)) }, contentAlignment = Alignment.Center) {
                Icon(Ic.Quote, "引用", Modifier.size(20.dp), tint = if (sp.quote) P.mintDeep else P.ink)
            }
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("颜色", fontSize = 13.sp, color = P.sub)
            Spacer(Modifier.width(10.dp))
            NoteColors.forEachIndexed { i, c ->
                Box(Modifier.padding(end = 10.dp).size(26.dp).clip(CircleShape)
                    .background(if (c == Color.Unspecified) P.ink else c)
                    .border(3.dp, if (sp.color == i) P.mint.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                    .clickable { onChange(sp.copy(color = i)) })
            }
        }
    }
}

@Composable
private fun FmtToggle(label: String, on: Boolean, style: TextStyle, onClick: () -> Unit) {
    Box(Modifier.padding(end = 4.dp).size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) P.mintSoft else Color.Transparent)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = style.copy(fontSize = 17.sp, color = if (on) P.mintDeep else P.ink))
    }
}

@Composable
private fun RecordBar(rec: NoteRecorder, onStop: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableStateOf(Time.now()) }
    LaunchedEffect(Unit) { while (true) { delay(250); now = Time.now() } }
    Row(Modifier.fillMaxWidth().background(P.pinkSoft).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(P.danger))
        Spacer(Modifier.width(8.dp))
        Text("正在录音 ${NoteMedia.fmt(now - rec.startedAt)}", Modifier.weight(1f), color = P.ink)
        TextButton(onCancel) { Text("取消", color = P.sub) }
        PrimaryButton("完成", icon = Ic.Stop, onClick = onStop)
    }
}

/** 定时提醒设置：几时几分 + 重复（仅一次/每天/工作日/休息日/特定日期）+ 提醒方式 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(old: NoteReminder?, title: String, onDismiss: () -> Unit, onSave: (NoteReminder?) -> Unit) {
    val tomorrowOrToday = remember {
        val nowT = Time.dateTimeOf(Time.now()).toLocalTime()
        if (nowT < Time.parse(old?.time ?: "09:00")) Time.today() else Time.today().plusDays(1)
    }
    var r by remember { mutableStateOf(old ?: NoteReminder(date = tomorrowOrToday.toString())) }
    var pickDate by remember { mutableStateOf<String?>(null) }   // "once" / "add"
    val floatingOn = Store.cfg.settings.floatingPet && PetService.instance != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时提醒") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimeRow("提醒时间", r.time) { r = r.copy(time = it) }
                Text("重复", fontSize = 13.sp, color = P.sub)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeat.entries.forEach { rp -> Pill(rp.label, r.repeat == rp) { r = r.copy(repeat = rp) } }
                }
                when (r.repeat) {
                    Repeat.ONCE -> ValueRow("日期", r.date ?: "选择日期") { pickDate = "once" }
                    Repeat.WORKDAYS -> Text("周一到周五。暂不含法定节假日调休。", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = P.sub)
                    Repeat.WEEKENDS -> Text("周六、周日。暂不含法定节假日调休。", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = P.sub)
                    Repeat.DATES -> {
                        Gap(8.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.dates.sorted().forEach { d -> Pill("$d  ×", true) { r = r.copy(dates = r.dates - d) } }
                            Pill("+ 加日期", false) { pickDate = "add" }
                        }
                    }
                    Repeat.DAILY -> {}
                }
                Gap(12.dp)
                Text("提醒方式", fontSize = 13.sp, color = P.sub)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RemindMode.entries.forEach { m -> Pill(m.label, r.mode == m) { r = r.copy(mode = m) } }
                }
                Gap(12.dp)
                OutlinedTextField(r.text, { r = r.copy(text = it.take(40)) }, Modifier.fillMaxWidth(),
                    label = { Text("宠物要说的话") }, placeholder = { Text(title.ifBlank { "便签标题" }) }, singleLine = true)
                if (!floatingOn) Text("桌面悬浮宠物没开，到时会改用通知提醒（「叫声」模式用宠物叫声 + 震动）。",
                    Modifier.padding(top = 8.dp), fontSize = 12.sp, color = P.sub)
            }
        },
        confirmButton = {
            TextButton({
                val ok = when (r.repeat) {
                    Repeat.ONCE -> r.date != null
                    Repeat.DATES -> r.dates.isNotEmpty()
                    else -> true
                }
                if (ok) onSave(r.copy(enabled = true))
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (old != null) TextButton({ onSave(null) }) { Text("取消提醒", color = P.danger) }
                TextButton(onDismiss) { Text("关闭") }
            }
        },
    )
    pickDate?.let { mode ->
        DateDialog({ pickDate = null }) { d ->
            r = if (mode == "once") r.copy(date = d) else r.copy(dates = (r.dates + d).distinct())
            pickDate = null
        }
    }
}
