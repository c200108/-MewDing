package com.mengding.app.ui

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mengding.app.data.Block
import com.mengding.app.logic.NoteMedia
import com.mengding.app.logic.NotePlayer
import com.mengding.app.logic.NoteVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 便签里的视频块：封面 + 播放键 + 时长，点开全屏播放（Media3 ExoPlayer，Apache 2.0 开源） */
@Composable
fun VideoBlock(b: Block, onDelete: () -> Unit) {
    val poster by produceState<ImageBitmap?>(null, b.file) {
        value = b.file?.let { withContext(Dispatchers.IO) { NoteVideo.poster(it)?.asImageBitmap() } }
    }
    val exists = remember(b.file) { b.file?.let { NoteVideo.files.file(it).exists() } == true }
    var play by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).padding(end = 2.dp, bottom = 2.dp).mdBlock(P.blue).padding(4.dp)
            .clip(if (Md.kind == MdKind.PIXEL) androidx.compose.foundation.shape.CutCornerShape(2.dp) else RoundedCornerShape(12.dp)).background(Color(0xFF221C24))
            .heightIn(min = 120.dp)
            .pointerInput(exists) { detectTapGestures(onTap = { if (exists) play = true }, onLongPress = { ask = true }) },
        contentAlignment = Alignment.Center,
    ) {
        poster?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio((it.width.toFloat() / it.height.coerceAtLeast(1)).coerceAtLeast(0.8f)), contentScale = ContentScale.Crop) }
        if (exists) Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(Ic.Play, "播放", Modifier.size(28.dp), tint = Color.White)
        } else Text("视频丢失了", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Video, null, Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text(b.text.ifBlank { "视频" }, Modifier.weight(1f), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(NoteMedia.fmt(b.durationMs), color = Color.White, fontSize = 12.sp)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
            .clickable { ask = true }, contentAlignment = Alignment.Center) {
            Icon(Ic.Close, "删除视频", Modifier.size(16.dp), tint = Color.White)
        }
    }
    if (play) b.file?.let { VideoPlayerDialog(it) { play = false } }
    if (ask) MdConfirm(
        title = "删除这段视频？", message = "视频文件会从手机里一起删掉。", confirm = "删除", danger = true,
        onDismiss = { ask = false }, onConfirm = { ask = false; onDelete() },
    )
}

/** 全屏播放：点画面显示/隐藏控制条，可暂停、拖动进度 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(rel: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(rel) {
        NotePlayer.stop()
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(NoteVideo.files.file(rel))))
            prepare(); playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var controls by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(v: VideoSize) {
                if (v.width > 0 && v.height > 0) aspect = v.width * v.pixelWidthHeightRatio / v.height
            }
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_ENDED) { player.pause(); player.seekTo(0); controls = true }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error = "这段视频手机放不了（${e.errorCodeName}）" }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0); dur = player.duration.takeIf { it > 0 } ?: dur
            delay(200)
        }
    }
    LaunchedEffect(controls, playing) { if (controls && playing) { delay(3000); controls = false } }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { SurfaceView(it).also { sv -> player.setVideoSurfaceView(sv) } },
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(aspect.coerceIn(0.2f, 5f)),
                onRelease = { player.clearVideoSurface() },
            )
            // SurfaceView 会吃掉触摸，点击层要盖在它上面
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controls = !controls } })
            error?.let { Text(it, Modifier.align(Alignment.Center).padding(24.dp), color = Color.White, fontSize = 15.sp) }
            if (controls) {
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center) { Icon(Ic.Back, "返回", Modifier.size(22.dp), tint = Color.White) }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f))
                    // 弹窗窗口里拿不到导航栏高度，按手势条 / 三键导航留足
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 52.dp)) {
                    SeekBar(if (dur > 0) pos.toFloat() / dur else 0f) { f -> if (dur > 0) { player.seekTo((f * dur).toLong()); pos = (f * dur).toLong() } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { if (player.isPlaying) player.pause() else player.play() },
                            contentAlignment = Alignment.Center) {
                            Icon(if (playing) Ic.Pause else Ic.Play, if (playing) "暂停" else "播放", Modifier.size(26.dp), tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${NoteMedia.fmt(pos)} / ${NoteMedia.fmt(dur)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** 可拖动的进度条 */
@Composable
private fun SeekBar(value: Float, onSeek: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val v = (drag ?: value).coerceIn(0f, 1f)
    val d = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().height(28.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { o -> onSeek((o.x / w).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / w).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null },
                ) { ch, _ -> drag = (ch.position.x / w).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(v).height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.mint))
        Box(Modifier.offset(x = with(d) { (v * w).toDp() } - 8.dp).size(16.dp).clip(CircleShape).background(Color.White))
    }
}
