package com.mengding.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mengding.app.BuildConfig
import com.mengding.app.logic.UpdateCheck
import com.mengding.app.logic.UpdateRelease
import com.mengding.app.logic.Updater
import com.mengding.app.logic.Updater.State

/** 关于萌叮 → 在线更新：检查 → 下载（进度、可取消）→ 安装 */
@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val st by Updater.state.collectAsState()
    var showNotes by remember { mutableStateOf<UpdateRelease?>(null) }
    // 从系统设置打开「安装未知应用」回来后，接着装
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            (Updater.state.value as? State.NeedPermission)?.let { if (Updater.canInstall(ctx)) Updater.install(ctx, it.r, it.file) }
        }
    }
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MdRowIcon(Ic.Gift)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("在线更新", fontSize = 15.sp, color = P.ink, fontWeight = FontWeight.SemiBold)
                Text("当前 ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = P.sub)
            }
            when (st) {
                is State.Checking -> Text("检查中…", fontSize = 13.sp, color = P.sub)
                is State.Downloading, is State.Installing -> {}
                else -> MdButton({ Updater.check() }, Modifier.height(36.dp), role = MdRole.PRIMARY,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) { Text("检查更新", fontSize = 14.sp) }
            }
        }
        when (val s = st) {
            is State.UpToDate -> Hint(if (s.latest != null) "已经是最新版（GitHub 上最新是 ${s.latest}）" else "GitHub 上还没有可下载的版本")
            is State.Failed -> {
                Hint(s.msg, P.danger)
                s.r?.let { r -> Gap(8.dp); MdButton({ Updater.download(ctx, r) }, Modifier.fillMaxWidth(), role = MdRole.SOLID) { Text("重新下载") } }
            }
            is State.Available -> {
                Gap(10.dp)
                ReleaseInfo(s.r) { showNotes = s.r }
                Gap(10.dp)
                MdButton({ Updater.download(ctx, s.r) }, Modifier.fillMaxWidth(), role = MdRole.SOLID) { Text("下载并更新（${mb(s.r.size)}）") }
            }
            is State.Downloading -> {
                Gap(10.dp)
                Text("正在下载 ${s.r.versionName}" + if (s.viaMirror) "（通过加速镜像下载）" else "", fontSize = 13.sp, color = P.ink)
                Gap(6.dp)
                MdProgressBar(s.progress)
                Gap(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.progress >= 0) "${(s.progress * 100).toInt()}% · 共 ${mb(s.r.size)}" else "下载中…", Modifier.weight(1f), fontSize = 12.sp, color = P.sub)
                    TextButton({ Updater.cancelDownload() }) { Text("取消") }
                }
                Text("可以离开这个页面，下载会在后台继续；断了下次会接着下。", fontSize = 11.sp, color = P.sub)
            }
            is State.Ready -> {
                Gap(10.dp)
                Hint("${s.r.versionName} 下载好了，已校验签名和完整性")
                Gap(8.dp)
                MdButton({ Updater.install(ctx, s.r, s.file) }, Modifier.fillMaxWidth(), role = MdRole.SOLID) { Text("立即安装") }
                // 下载完自动接着装，少点一次
                LaunchedEffect(s.file) { if (Updater.consumeAutoInstall(s.file)) Updater.install(ctx, s.r, s.file) }
            }
            is State.Installing -> Hint("正在安装…系统会弹出确认，点「更新」就好。装完萌叮会自动重启，数据都在。")
            is State.NeedPermission -> {}
            else -> {}
        }
    }
    (st as? State.NeedPermission)?.let { s ->
        MdConfirm(
            title = "允许萌叮安装更新",
            message = "安卓要求先在系统设置里打开「允许安装未知应用」，萌叮才能装下载好的新版。打开后回到这里会自动继续安装。",
            confirm = "去打开",
            onDismiss = { Updater.dismissPermission(s) },
            onConfirm = { ctx.startActivity(Updater.permissionIntent(ctx)) },
        )
    }
    showNotes?.let { r ->
        MdDialog(
            onDismissRequest = { showNotes = null },
            title = { Text("${r.versionName} 更新说明") },
            text = { Text(UpdateCheck.readableNotes(r.notes, 4000).ifBlank { "这一版没有写说明" }, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ showNotes = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun ReleaseInfo(r: UpdateRelease, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(end = 2.dp, bottom = 2.dp).mdBlock(P.mint).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("新版本 ${r.versionName}", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.ink)
            Text(r.publishedAt.take(10), fontSize = 12.sp, color = P.sub)
        }
        Gap(4.dp)
        val notes = UpdateCheck.readableNotes(r.notes, 240)
        if (notes.isNotBlank()) Text(notes, Modifier.heightIn(max = 140.dp), fontSize = 12.sp, color = P.sub, lineHeight = 17.sp)
        TextButton(onMore) { Text("看完整说明", fontSize = 13.sp) }
    }
}

@Composable
private fun Hint(text: String, color: androidx.compose.ui.graphics.Color = P.sub) {
    Gap(8.dp)
    Text(text, fontSize = 13.sp, color = color)
}

private fun mb(b: Long) = if (b <= 0) "大小未知" else "%.1f MB".format(b / 1048576.0)
