package com.mengding.app.logic

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import androidx.core.content.FileProvider
import com.mengding.app.data.Block
import com.mengding.app.data.BlockType
import com.mengding.app.data.Note
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 便签分享：把一条便签转成别的格式，用系统分享面板发出去（微信、QQ、邮件、保存到文件……都在面板里选）。
 *   网页 HTML：一个文件，图片和音频都嵌在里面，用浏览器打开就能看、能播；排版、颜色、待办勾选、表格都保留
 *   长图 PNG：像便签纸一样的长图，适合发朋友圈、聊天
 *   Markdown 文档：note.md + 图片 / 音频放在同一个压缩包里，能导进 Obsidian、Typora、语雀这类笔记软件
 *   纯文字：直接分享文字，任何 App 都能收
 * 生成的文件放在 cache/share/，下次分享时清掉旧的。
 */
object NoteExport {
    enum class Kind(val label: String, val desc: String) {
        HTML("网页", "一个 .html 文件，图片和音频都在里面，浏览器打开就能看能听"),
        IMAGE("长图", "便签纸样式的长图片，适合发朋友圈、聊天"),
        MARKDOWN("Markdown 文档", "压缩包里有 .md 和图片音频，能导入笔记软件"),
        TEXT("纯文字", "只发文字，什么 App 都能收"),
    }

    private val NoteColorsHex = listOf("", "#E5606F", "#F08A3C", "#2FA37E", "#4A8FE0", "#9A72D8")
    private const val INK = "#3A3247"; private const val SUB = "#8C8496"
    /** 嵌进网页的单个音频最大字节数：太大的音频只写文件名，免得网页几十兆打不开 */
    private const val MAX_EMBED_AUDIO = 15L * 1024 * 1024

    fun share(ctx: Context, note: Note, kind: Kind): String? = runCatching {
        val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val base = safeName(note.title.ifBlank { "萌叮便签" })
        val intent = Intent(Intent.ACTION_SEND)
        when (kind) {
            Kind.TEXT -> {
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, toText(note))
            }
            else -> {
                val f = when (kind) {
                    Kind.HTML -> File(dir, "$base.html").apply { writeText(toHtml(ctx, note)) }
                    Kind.IMAGE -> File(dir, "$base.png").apply { outputStream().use { toImage(ctx, note).compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    else -> File(dir, "$base.zip").also { writeMarkdownZip(note, it) }
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", f)
                intent.type = when (kind) { Kind.HTML -> "text/html"; Kind.IMAGE -> "image/png"; else -> "application/zip" }
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.clipData = ClipData.newRawUri(f.name, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "萌叮便签" })
        ctx.startActivity(Intent.createChooser(intent, "分享便签").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    }.exceptionOrNull()?.let { "分享失败：${it.message ?: it.javaClass.simpleName}" }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").take(40).ifBlank { "萌叮便签" }

    private fun dateOf(note: Note) = Time.dateTimeOf(if (note.updatedAt > 0) note.updatedAt else Time.now())
        .format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm"))

    /** 列表前缀：圆点、数字（同类连续计数）、待办 */
    private fun prefixes(blocks: List<Block>): List<String> {
        var n = 0
        return blocks.map { b ->
            if (b.type != BlockType.TEXT || b.spec.list != 2) n = 0
            when {
                b.type != BlockType.TEXT -> ""
                b.spec.list == 1 -> "• "
                b.spec.list == 2 -> "${++n}. "
                b.spec.list == 3 -> if (b.checked) "☑ " else "☐ "
                else -> ""
            }
        }
    }

    // ---------------- 纯文字 ----------------

    fun toText(note: Note): String = buildString {
        if (note.title.isNotBlank()) appendLine(note.title).appendLine()
        val pre = prefixes(note.blocks)
        note.blocks.forEachIndexed { i, b ->
            when (b.type) {
                BlockType.TEXT -> appendLine((if (b.spec.quote) "「" else "") + pre[i] + b.text + (if (b.spec.quote) "」" else ""))
                BlockType.IMAGE -> appendLine("[图片]")
                BlockType.VIDEO -> appendLine("[视频 ${NoteMedia.fmt(b.durationMs)}]")
                BlockType.AUDIO -> appendLine("[${if (b.recorded) "录音" else "音频"} ${NoteMedia.fmt(b.durationMs)}]")
                BlockType.TABLE -> b.table.forEach { row -> appendLine(row.joinToString(" | ")) }
            }
        }
        appendLine().append("—— 来自萌叮便签 · ${dateOf(note)}")
    }

    // ---------------- 网页 ----------------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "jpg", "jpeg" -> "image/jpeg"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg", "opus" -> "audio/ogg"; "flac" -> "audio/flac"
        "aac" -> "audio/aac"; "amr" -> "audio/amr"; "webm" -> "audio/webm"; "mka" -> "audio/x-matroska"
        else -> "audio/mp4"
    }

    /** 图片按 1600 宽压一下再嵌（原图十几兆的照片嵌进网页太大）；gif 原样嵌（保留动图） */
    private fun imageDataUri(name: String): String? {
        val f = NoteMedia.file(name); if (!f.exists()) return null
        if (name.endsWith(".gif")) return "data:image/gif;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        val bmp = NoteMedia.bitmap(name, 1600) ?: return null
        val out = ByteArrayOutputStream()
        val png = name.endsWith(".png") || name.endsWith(".webp")
        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 88, out)
        return "data:${if (png) "image/png" else "image/jpeg"};base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun toHtml(ctx: Context, note: Note): String {
        val pre = prefixes(note.blocks)
        val body = StringBuilder()
        note.blocks.forEachIndexed { i, b ->
            when (b.type) {
                BlockType.TEXT -> {
                    val sp = b.spec
                    val css = mutableListOf<String>()
                    css += "font-size:" + listOf("14px", "17px", "21px", "26px").getOrElse(sp.size) { "17px" }
                    if (sp.bold || sp.size == 3) css += "font-weight:700"
                    if (sp.italic) css += "font-style:italic"
                    val deco = listOfNotNull("underline".takeIf { sp.underline }, "line-through".takeIf { sp.strike || b.checked })
                    if (deco.isNotEmpty()) css += "text-decoration:" + deco.joinToString(" ")
                    NoteColorsHex.getOrNull(sp.color)?.takeIf { it.isNotEmpty() }?.let { css += "color:$it" }
                    if (b.checked) css += "color:$SUB"
                    css += "text-align:" + listOf("left", "center", "right").getOrElse(sp.align) { "left" }
                    val text = esc(b.text).ifEmpty { "&nbsp;" }
                    val p = "<p style=\"${css.joinToString(";")}\">${esc(pre[i])}$text</p>"
                    body.append(if (sp.quote) "<blockquote>$p</blockquote>" else p).append('\n')
                }
                BlockType.IMAGE -> b.file?.let { imageDataUri(it) }?.let { body.append("<figure><img src=\"$it\" alt=\"图片\"></figure>\n") }
                BlockType.VIDEO -> {
                    val f = b.file?.let { NoteVideo.files.file(it) }
                    val label = "视频 · ${NoteMedia.fmt(b.durationMs)}"
                    if (f != null && f.exists() && f.length() <= MAX_EMBED_AUDIO * 2) {
                        val src = "data:${if (f.name.endsWith(".webm")) "video/webm" else "video/mp4"};base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                        body.append("<figure><video controls preload=\"metadata\" style=\"width:100%;border-radius:14px\" src=\"$src\"></video></figure>\n")
                    } else {
                        b.file?.let { NoteVideoFiles.posterOf(it) }?.takeIf { NoteVideo.files.file(it).exists() }
                            ?.let { imageDataUri(it) }?.let { body.append("<figure><img src=\"$it\" alt=\"视频封面\"></figure>\n") }
                        body.append("<div class=\"audio\"><div class=\"al\">▶ $label（视频太大，没有放进网页）</div></div>\n")
                    }
                }
                BlockType.AUDIO -> {
                    val f = b.file?.let { NoteMedia.file(it) }
                    val label = "${if (b.recorded) "录音" else esc(b.text.ifBlank { "音频" })} · ${NoteMedia.fmt(b.durationMs)}"
                    if (f != null && f.exists() && f.length() <= MAX_EMBED_AUDIO) {
                        val src = "data:${mimeOf(f.name)};base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                        body.append("<div class=\"audio\"><div class=\"al\">♪ $label</div><audio controls preload=\"metadata\" src=\"$src\"></audio></div>\n")
                    } else body.append("<div class=\"audio\"><div class=\"al\">♪ $label（文件太大，没有放进网页）</div></div>\n")
                }
                BlockType.TABLE -> {
                    body.append("<table>")
                    b.table.forEachIndexed { r, row ->
                        body.append("<tr>")
                        row.forEach { c -> body.append(if (r == 0) "<th>${esc(c)}</th>" else "<td>${esc(c)}</td>") }
                        body.append("</tr>")
                    }
                    body.append("</table>\n")
                }
            }
        }
        val title = esc(note.title.ifBlank { "萌叮便签" })
        return """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
:root{--bg:#FFF8F2;--paper:#FFFFFF;--ink:$INK;--sub:$SUB;--mint:#2FB38C;--line:#F0E4DA}
@media (prefers-color-scheme:dark){:root{--bg:#1E1A24;--paper:#2A2531;--ink:#F2ECF6;--sub:#A79FB0;--line:#3A3442}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:-apple-system,"PingFang SC","Noto Sans SC","Microsoft YaHei",sans-serif;line-height:1.7}
.card{max-width:720px;margin:24px auto;background:var(--paper);border-radius:22px;padding:28px 26px 22px;box-shadow:0 6px 24px rgba(80,50,60,.08);border:1px solid var(--line)}
h1{font-size:26px;margin:0 0 4px}.date{color:var(--sub);font-size:13px;margin-bottom:18px}
p{margin:.35em 0;white-space:pre-wrap;word-break:break-word}
blockquote{margin:.6em 0;padding:.2em 0 .2em 14px;border-left:4px solid var(--mint);color:var(--sub)}
figure{margin:12px 0}img{max-width:100%;border-radius:14px;display:block}
.audio{background:var(--bg);border-radius:14px;padding:10px 12px;margin:10px 0}.al{font-size:14px;color:var(--sub);margin-bottom:6px}audio{width:100%}
table{border-collapse:collapse;width:100%;margin:12px 0;font-size:15px}th,td{border:1px solid var(--line);padding:6px 8px;text-align:left}th{background:var(--bg)}
.foot{color:var(--sub);font-size:12px;text-align:center;margin-top:22px}
@media (max-width:480px){.card{margin:0;border-radius:0;border:0;min-height:100vh;padding:22px 16px}}
</style></head>
<body><main class="card">
<h1>$title</h1><div class="date">${dateOf(note)}</div>
$body<div class="foot">来自 萌叮便签</div>
</main></body></html>
"""
    }

    // ---------------- Markdown（压缩包） ----------------

    fun writeMarkdownZip(note: Note, out: File) {
        val pre = prefixes(note.blocks)
        val media = mutableListOf<File>()
        val md = buildString {
            appendLine("# ${note.title.ifBlank { "萌叮便签" }}").appendLine().appendLine("> ${dateOf(note)}").appendLine()
            note.blocks.forEachIndexed { i, b ->
                when (b.type) {
                    BlockType.TEXT -> {
                        var t = b.text.replace("\n", "  \n")
                        if (t.isNotEmpty()) {
                            if (b.spec.strike || b.checked) t = "~~$t~~"
                            if (b.spec.italic) t = "*$t*"
                            if (b.spec.bold) t = "**$t**"
                        }
                        val head = when (b.spec.size) { 3 -> "## "; 2 -> "### "; else -> "" }
                        val list = when (b.spec.list) { 1 -> "- "; 2 -> pre[i]; 3 -> if (b.checked) "- [x] " else "- [ ] "; else -> "" }
                        appendLine((if (b.spec.quote) "> " else "") + head + list + t)
                        if (b.spec.list == 0) appendLine()
                    }
                    BlockType.IMAGE -> b.file?.let { NoteMedia.file(it) }?.takeIf { it.exists() }?.let { f ->
                        media += f; appendLine("![图片](media/${f.name})").appendLine()
                    }
                    BlockType.VIDEO -> b.file?.let { NoteVideo.files.file(it) }?.takeIf { it.exists() }?.let { f ->
                        media += f; appendLine("[▶ 视频 ${NoteMedia.fmt(b.durationMs)}](media/${f.name})").appendLine()
                    }
                    BlockType.AUDIO -> b.file?.let { NoteMedia.file(it) }?.takeIf { it.exists() }?.let { f ->
                        media += f; appendLine("[♪ ${if (b.recorded) "录音" else b.text.ifBlank { "音频" }} ${NoteMedia.fmt(b.durationMs)}](media/${f.name})").appendLine()
                    }
                    BlockType.TABLE -> {
                        b.table.forEachIndexed { r, row ->
                            appendLine("| " + row.joinToString(" | ") { it.replace("|", "\\|") } + " |")
                            if (r == 0) appendLine("|" + row.joinToString("|") { " --- " } + "|")
                        }
                        appendLine()
                    }
                }
            }
            appendLine("---").append("来自萌叮便签")
        }
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("${safeName(note.title.ifBlank { "萌叮便签" })}.md")); z.write(md.toByteArray()); z.closeEntry()
            media.distinctBy { it.name }.forEach { f -> z.putNextEntry(ZipEntry("media/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }
    }

    // ---------------- 长图 ----------------

    /** 便签纸样式长图：宽 1080，奶油色底、圆角白卡片，文字按格式排版，图片按宽缩放，音频画成一条播放条，表格画格子 */
    fun toImage(ctx: Context, note: Note): Bitmap {
        val W = 1080; val pad = 64; val inner = W - pad * 2 - 64
        val ink = 0xFF3A3247.toInt(); val sub = 0xFF8C8496.toInt(); val mint = 0xFF2FB38C.toInt()
        val font = com.mengding.app.ui.AppFonts.typeface ?: Typeface.DEFAULT
        fun paint(size: Float, color: Int = ink, bold: Boolean = false, italic: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(font, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
        }
        fun layout(text: String, p: TextPaint, w: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, w).setAlignment(align).setLineSpacing(0f, 1.25f).build()

        // 先排一遍算总高度，再画
        data class Item(val h: Int, val draw: (Canvas, Float) -> Unit)
        val items = mutableListOf<Item>()
        val titleL = layout(note.title.ifBlank { "萌叮便签" }, paint(64f, bold = true), inner)
        items += Item(titleL.height + 12) { c, y -> c.save(); c.translate(0f, y); titleL.draw(c); c.restore() }
        val dateL = layout(dateOf(note), paint(32f, sub), inner)
        items += Item(dateL.height + 36) { c, y -> c.save(); c.translate(0f, y); dateL.draw(c); c.restore() }
        val pre = prefixes(note.blocks)
        note.blocks.forEachIndexed { i, b ->
            when (b.type) {
                BlockType.TEXT -> {
                    val sp = b.spec
                    val size = listOf(36f, 44f, 54f, 64f).getOrElse(sp.size) { 44f }
                    val color = if (b.checked) sub else NoteColorsHex.getOrNull(sp.color)?.takeIf { it.isNotEmpty() }?.let { android.graphics.Color.parseColor(it) } ?: ink
                    val p = paint(size, color, sp.bold || sp.size == 3, sp.italic).apply { isUnderlineText = sp.underline; isStrikeThruText = sp.strike || b.checked }
                    val quoteInset = if (sp.quote) 36 else 0
                    val align = when (sp.align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
                    val l = layout(pre[i] + b.text.ifEmpty { " " }, p, inner - quoteInset, align)
                    items += Item(l.height + 14) { c, y ->
                        if (sp.quote) c.drawRoundRect(RectF(0f, y + 4, 10f, y + l.height - 4), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = mint })
                        c.save(); c.translate(quoteInset.toFloat(), y); l.draw(c); c.restore()
                    }
                }
                BlockType.IMAGE -> b.file?.let { NoteMedia.bitmap(it, inner) }?.let { bmp ->
                    val h = (bmp.height * inner.toFloat() / bmp.width).toInt()
                    items += Item(h + 28) { c, y ->
                        val r = RectF(0f, y, inner.toFloat(), y + h)
                        c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(r, 28f, 28f, android.graphics.Path.Direction.CW) })
                        c.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
                    }
                }
                BlockType.VIDEO -> b.file?.let { NoteVideo.poster(it) }?.let { bmp ->
                    val h = (bmp.height * inner.toFloat() / bmp.width).toInt()
                    items += Item(h + 28) { c, y ->
                        val r = RectF(0f, y, inner.toFloat(), y + h)
                        c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(r, 28f, 28f, android.graphics.Path.Direction.CW) })
                        c.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
                        val cx = r.centerX(); val cy = r.centerY()
                        c.drawCircle(cx, cy, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
                        c.drawPath(android.graphics.Path().apply { moveTo(cx - 22f, cy - 36f); lineTo(cx + 38f, cy); lineTo(cx - 22f, cy + 36f); close() },
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                    }
                }
                BlockType.AUDIO -> {
                    val l = layout("♪  ${if (b.recorded) "录音" else b.text.ifBlank { "音频" }}  ${NoteMedia.fmt(b.durationMs)}", paint(38f, ink), inner - 60)
                    items += Item(120) { c, y ->
                        c.drawRoundRect(RectF(0f, y, inner.toFloat(), y + 100), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF1E8.toInt() })
                        c.save(); c.translate(30f, y + (100 - l.height) / 2f); l.draw(c); c.restore()
                    }
                }
                BlockType.TABLE -> {
                    val cols = b.table.maxOfOrNull { it.size } ?: 0
                    if (cols > 0) {
                        val cw = inner / cols
                        val rows = b.table.mapIndexed { r, row -> List(cols) { k -> layout(row.getOrElse(k) { "" }, paint(34f, ink, bold = r == 0), cw - 24) } }
                        val rh = rows.map { r -> (r.maxOf { it.height } + 24) }
                        items += Item(rh.sum() + 28) { c, y ->
                            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF0E4DA.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
                            var yy = y
                            rows.forEachIndexed { r, cells ->
                                if (r == 0) c.drawRect(0f, yy, (cw * cols).toFloat(), yy + rh[r], Paint().apply { color = 0xFFFFF8F2.toInt() })
                                cells.forEachIndexed { k, l ->
                                    c.drawRect((k * cw).toFloat(), yy, ((k + 1) * cw).toFloat(), yy + rh[r], line)
                                    c.save(); c.translate(k * cw + 12f, yy + 12f); l.draw(c); c.restore()
                                }
                                yy += rh[r]
                            }
                        }
                    }
                }
            }
        }
        val footL = layout("来自 萌叮便签", paint(30f, sub), inner, Layout.Alignment.ALIGN_CENTER)
        items += Item(footL.height + 10) { c, y -> c.save(); c.translate(0f, y + 20); footL.draw(c); c.restore() }

        val contentH = items.sumOf { it.h } + 20
        val H = (contentH + pad * 2 + 64).coerceAtMost(20000)   // 超长便签截到 2 万像素，免得爆内存
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFF8F2.toInt())
        val card = RectF(pad.toFloat(), pad.toFloat(), (W - pad).toFloat(), (H - pad).toFloat())
        c.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(18f, 0f, 6f, 0x22503240) })
        c.save(); c.clipRect(card); c.translate(pad + 32f, pad + 40f)
        var y = 0f
        items.forEach { it.draw(c, y); y += it.h }
        c.restore()
        return bmp
    }
}
