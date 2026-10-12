package com.mengding.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mengding.app.data.Store
import com.mengding.app.logic.Economy
import com.mengding.app.pet.Outfits
import com.mengding.app.pet.Sprites
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 小游戏共用：像素素材（assets/games/，由 美术/小游戏/make_game_art.py 逐像素画出）、
 * 像素绘制、宠物角色、实体按键、最高分和游戏时长奖励。
 */
object GameArt {
    private val cache = HashMap<String, ImageBitmap>()

    @Synchronized
    fun img(ctx: Context, name: String): ImageBitmap = cache.getOrPut(name) {
        val path = if (name.startsWith("foods/")) "${name}_icon.png" else "games/$name.png"
        ctx.assets.open(path).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!.asImageBitmap()
        }
    }

    fun text(ctx: Context, name: String): String = ctx.assets.open("games/$name").bufferedReader().readText()
}

/** 把像素图（或它的一块）画到 (x, y, w, h)，最近邻放大；坐标取整避免拼缝 */
fun DrawScope.pix(
    img: ImageBitmap, x: Float, y: Float, w: Float, h: Float,
    flipX: Boolean = false, flipY: Boolean = false, alpha: Float = 1f,
    srcX: Int = 0, srcY: Int = 0, srcW: Int = img.width, srcH: Int = img.height,
    tint: ColorFilter? = null,
) {
    val x0 = x.roundToInt(); val y0 = y.roundToInt()
    val dw = (x + w).roundToInt() - x0; val dh = (y + h).roundToInt() - y0
    if (dw <= 0 || dh <= 0) return
    val draw: DrawScope.() -> Unit = {
        drawImage(img, IntOffset(srcX, srcY), IntSize(srcW, srcH), IntOffset(x0, y0), IntSize(dw, dh),
            alpha = alpha, colorFilter = tint, filterQuality = FilterQuality.None)
    }
    if (flipX || flipY) {
        withTransform({ scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, Offset(x0 + dw / 2f, y0 + dh / 2f)) }) { draw() }
    } else draw()
}

/** 在 DrawScope 里按名字取素材 */
class ArtSrc(private val ctx: Context) {
    operator fun get(name: String): ImageBitmap = GameArt.img(ctx, name)
}

private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    textAlign = android.graphics.Paint.Align.CENTER
}

/** 游戏里的飘字（得分、提示）：深色描边 + 彩色字 */
fun DrawScope.gameText(text: String, cx: Float, y: Float, size: Float, color: Int = 0xFFFFFFFF.toInt()) {
    val c = drawContext.canvas.nativeCanvas
    textPaint.textSize = size
    textPaint.typeface = AppFonts.typeface?.let { android.graphics.Typeface.create(it, android.graphics.Typeface.BOLD) }
        ?: android.graphics.Typeface.DEFAULT_BOLD
    textPaint.style = android.graphics.Paint.Style.STROKE
    textPaint.strokeWidth = size / 5f
    textPaint.color = 0xFF4A2C3C.toInt()
    c.drawText(text, cx, y, textPaint)
    textPaint.style = android.graphics.Paint.Style.FILL
    textPaint.color = color
    c.drawText(text, cx, y, textPaint)
}

/** 游戏里的宠物：精灵图 + 穿着的服装层 */
class PetLook(ctx: Context, val species: String, outfits: List<String>) {
    private val sheet = Sprites.sheet(ctx, species).asImageBitmap()
    private val wear = Outfits.ordered(ctx, outfits).mapNotNull { Sprites.wearSheet(ctx, species, it)?.asImageBitmap() }

    /** t 是游戏时间（秒），按动作自己的帧间隔选帧 */
    fun draw(ds: DrawScope, action: String, t: Float, x: Float, y: Float, size: Float, flipX: Boolean = false,
             flipY: Boolean = false, alpha: Float = 1f, tint: ColorFilter? = null) {
        val a = Sprites.action(action)
        val f = ((t * 1000 / a.delay).toInt()) % Sprites.frames
        val s = Sprites.size
        ds.pix(sheet, x, y, size, size, flipX, flipY, alpha, f * s, a.row * s, s, s, tint)
        wear.forEach { ds.pix(it, x, y, size, size, flipX, flipY, alpha, f * s, a.row * s, s, s, tint) }
    }
}

/** 像素按键：按下 / 松开都回调，支持多指同时按（每个键各自接收手指） */
@Composable
fun PadButton(name: String, size: Dp, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val up = remember(name) { GameArt.img(ctx, "ui_btn_$name") }
    val dn = remember(name) { GameArt.img(ctx, "ui_btn_${name}_down") }
    var down by remember { mutableStateOf(false) }
    val cb by rememberUpdatedState(onChange)
    Image(
        if (down) dn else up, null,
        modifier.size(size).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                GameHaptics.tap(ctx)
                down = true; cb(true)
                waitForUpOrCancellation()
                down = false; cb(false)
            }
        },
        filterQuality = FilterQuality.None,
    )
}

/** 游戏按键震动反馈：设置里 gameVibrate 打开时，按下方向键、操作键、道具时轻震一下 */
object GameHaptics {
    fun tap(ctx: Context) {
        if (!Store.cfg.settings.gameVibrate) return
        runCatching {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31)
                ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") ctx.getSystemService(android.os.Vibrator::class.java)
            v.vibrate(android.os.VibrationEffect.createOneShot(18, 120))
        }
    }
}

/** 游戏顶栏的小暂停键：放在顶上，离方向键、操作键远，不容易误按 */
@Composable
fun PauseChip(paused: Boolean, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Text(if (paused) "▶ 继续" else "Ⅱ 暂停",
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(if (enabled) P.mintSoft else P.line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 13.sp, color = if (enabled) P.mintDeep else P.sub)
}

/** 萌点图标：全 App 统一用像素金币小红心（商店里的那个） */
@Composable
fun PointsIcon(size: Dp, modifier: Modifier = Modifier) = PixelImg("adv_coin_0", size, modifier)

@Composable
fun PixelImg(name: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Image(remember(name) { GameArt.img(ctx, name) }, null, modifier.size(size), filterQuality = FilterQuality.None)
}

object GameScores {
    fun best(key: String): Int = Store.cfg.economy.gameBest[key] ?: 0

    /** 记最高分，返回是不是新纪录 */
    fun submit(key: String, score: Int): Boolean {
        if (score <= best(key)) return false
        Store.update { st -> st.copy(economy = st.economy.copy(gameBest = st.economy.gameBest + (key to score))) }
        return true
    }
}

/** 玩游戏的时长换萌点（和捏云同一套规则：每分钟 +3，每天上限 15） */
@Composable
fun PlayTimeReward(playing: Boolean) {
    val toast = toaster()
    val on by rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        var acc = 0
        while (true) {
            delay(1000)
            if (on) acc++
            if (acc >= 10) {
                acc = 0
                var gained = 0
                Store.update { st -> Economy.gameSeconds(st, 10).also { gained = it.second }.first }
                if (gained > 0) toast("玩了一会儿，萌点 +$gained")
            }
        }
    }
}
