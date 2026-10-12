package com.mengding.app.ui

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengding.app.logic.Time
import com.mengding.app.pet.Sprites
import com.mengding.app.pet.Foods
import com.mengding.app.pet.PetFx
import kotlinx.coroutines.delay
import java.time.LocalTime

val P @Composable get() = LocalPalette.current

/** 会动的像素宠物。按整数倍放大、不做平滑，保持像素清晰 */
@Composable
fun PetSprite(species: String, action: String, size: Dp, modifier: Modifier = Modifier, outfits: List<String> = emptyList()) {
    val ctx = LocalContext.current
    val sheet = remember(species) { Sprites.sheet(ctx, species).asImageBitmap() }
    val wear = remember(species, outfits) {
        com.mengding.app.pet.Outfits.ordered(ctx, outfits).mapNotNull { Sprites.wearSheet(ctx, species, it)?.asImageBitmap() }
    }
    var frame by remember { mutableIntStateOf(0) }
    val transient by PetFx.transient.collectAsState()
    var clock by remember { mutableLongStateOf(Time.now()) }
    val a = Sprites.action(action)
    LaunchedEffect(action) {
        frame = 0
        while (true) {
            delay(a.delay.toLong())
            frame = (frame + 1) % Sprites.frames
        }
    }
    LaunchedEffect(transient) {
        while (transient != null) {
            clock = Time.now()
            delay(80)
        }
    }
    // 吃饭 / 喝水：捧着实际喂的东西一口口吃掉；不是喂食触发的（比如动作预览）就捧默认的饭团 / 清水循环吃
    val feeding = transient?.takeIf { it.action == action && Sprites.holds(action) && it.until > clock }
    val heldFood = feeding?.foodKey
    val heldStage = feeding?.biteStage(clock) ?: Sprites.loopStage(frame)
    Canvas(modifier.size(size)) {
        val s = Sprites.size
        drawImage(
            sheet,
            srcOffset = IntOffset(frame * s, a.row * s), srcSize = IntSize(s, s),
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.None,
        )
        wear.forEach { w ->
            drawImage(
                w,
                srcOffset = IntOffset(frame * s, a.row * s), srcSize = IntSize(s, s),
                dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
        if (Sprites.holds(action)) drawIntoCanvas { c ->
            Sprites.drawHeld(ctx, c.nativeCanvas, species, action, frame, heldFood, heldStage,
                android.graphics.RectF(0f, 0f, this.size.width.toInt().toFloat(), this.size.height.toInt().toFloat()))
        }
    }
}

@Composable
fun MCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(P.card)
            .border(1.dp, P.line, RoundedCornerShape(22.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.sub)
}

@Composable
fun StatBar(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = P.sub)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}", fontSize = 12.sp, color = P.ink, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.line)) {
            Box(Modifier.fillMaxWidth((value / 100f).coerceIn(0f, 1f)).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(if (value < 30) P.danger else color))
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null,
                  pixelIcon: String? = null, onClick: () -> Unit) {
    Button(onClick, modifier.height(48.dp), enabled = enabled, shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.mint)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        pixelIcon?.let { PixelImg(it, 20.dp); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** 商店价格按钮：前面可加一小段字（例如"5个 八折"），后面是萌点图标和价格；永远一行显示 */
@Composable
fun PriceButton(price: Int, modifier: Modifier = Modifier, label: String? = null, primary: Boolean = true, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            label?.let {
                Text(it, color = if (primary) Color.White else P.ink, fontSize = 13.sp, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(4.dp))
            }
            PixelImg("adv_coin_0", 18.dp)
            Spacer(Modifier.width(3.dp))
            Text("$price", fontWeight = FontWeight.SemiBold, color = if (primary) Color.White else P.ink,
                fontSize = 15.sp, maxLines = 1, softWrap = false)
        }
    }
    if (primary) Button(onClick, modifier.height(44.dp), shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.mint), contentPadding = PaddingValues(horizontal = 8.dp)) { content() }
    else OutlinedButton(onClick, modifier.height(40.dp), shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)) { content() }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.height(44.dp), enabled = enabled, shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 14.dp)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = P.mint); Spacer(Modifier.width(6.dp)) }
        Text(text, color = P.ink)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) P.mint else P.mintSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) Color.White else P.ink, fontSize = 14.sp, maxLines = 1, softWrap = false) }
}

@Composable
fun ToggleRow(title: String, desc: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (enabled) P.ink else P.sub)
            desc?.let { Text(it, fontSize = 12.sp, color = P.sub) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = P.mint))
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, desc: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(P.mintSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = P.mintDeep)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = P.ink)
            desc?.let { Text(it, fontSize = 12.sp, color = P.sub) }
        }
        Icon(Ic.Next, null, Modifier.size(18.dp), tint = P.sub)
    }
}

@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
        Text(value, fontSize = 15.sp, color = P.mintDeep, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(Ic.Next, null, Modifier.size(16.dp), tint = P.sub)
    }
}

/** 选时间的对话框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: String, title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = Time.parse(initial)
    val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton({ onPick(Time.fmt(LocalTime.of(state.hour, state.minute))) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

/** 点一下弹出时间选择的一行 */
@Composable
fun TimeRow(title: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow(title, value) { open = true }
    if (open) TimeDialog(value, title, { open = false }) { open = false; onChange(it) }
}

/** 数字步进 */
@Composable
fun StepperRow(title: String, value: Int, step: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp, color = P.ink)
        RoundIcon(Ic.Minus) { onChange((value - step).coerceAtLeast(min)) }
        Text("$value$unit", Modifier.width(86.dp), fontSize = 15.sp, color = P.mintDeep,
            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundIcon(Ic.Plus) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, size: Dp = 32.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(P.mintSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.55f), tint = P.mintDeep) }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
}

@Composable
fun toaster(): (String) -> Unit {
    val ctx = LocalContext.current
    return { if (it.isNotEmpty()) Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HRow(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically, content = content)
