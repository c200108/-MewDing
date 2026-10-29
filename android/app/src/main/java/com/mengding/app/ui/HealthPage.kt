package com.mengding.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mengding.app.data.SoundMode
import com.mengding.app.data.Store
import com.mengding.app.logic.Perms
import com.mengding.app.logic.Time
import com.mengding.app.notify.Notifier
import com.mengding.app.notify.Reminders
import com.mengding.app.notify.Reminders.Kind
import com.mengding.app.pet.PetService

/** 提醒类型的中文名（体检里「下一个闹钟是什么提醒」） */
private fun kindName(k: Kind, extra: String): String = when (k) {
    Kind.WATER -> "喝水"
    Kind.MEAL -> extra.ifBlank { "吃饭" }
    Kind.SLEEP_PRE -> "睡前 30 分钟"
    Kind.SLEEP -> "睡觉"
    Kind.MORNING -> "早上补记睡眠"
    Kind.PERIOD_SOON, Kind.PERIOD_CARE -> "经期"
    Kind.CARE_SOON, Kind.CARE_TIP -> "关心 TA"
    Kind.NOTE -> "便签"
    Kind.CUSTOM -> Store.priv.customs.firstOrNull { it.id == extra.substringBefore('|') }?.name ?: "自定义"
    Kind.TEST -> "测试"
}

/**
 * 提醒体检（需求 A）：逐项红绿灯 +「去打开」。能查的都现查；自启动 / 后台锁定系统不给查，只能让用户去设好后自己打勾。
 * 底部可以发一条测试提醒，走和真提醒完全一样的闹钟 + 通知 + 桌面气泡路径。
 */
@Composable
fun HealthPage() {
    val ctx = LocalContext.current
    val toast = toaster()
    val cfg by Store.config.collectAsState()
    // 从系统设置页回来时重新查一遍
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; Reminders.missedNotice.value = false; onPauseOrDispose { } }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    PageScaffold("提醒体检") { androidx.compose.runtime.key(tick) {
        Text("红灯的点「去打开」修一下；全绿了提醒就能准时响。", fontSize = 13.sp, color = P.sub)
        Gap(10.dp)
        MCard(padding = 12.dp) {
            val notif = Perms.notifications(ctx)
            Check("通知权限", if (notif) "已允许" else "关着的话所有提醒都看不到", notif) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { ctx.startActivity(Perms.notifSettingsIntent(ctx)) }
            }
            // 喝水、吃饭、睡觉、经期、自定义提醒都走「提示音」选的那个渠道（安卓规定渠道建好后声音不能改，所以按提示音分渠道）
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val chId = Notifier.reminderChannel()
            val ch = nm.getNotificationChannel(chId)
            val silentMode = cfg.settings.sound == SoundMode.SILENT
            val chOff = ch == null || ch.importance == NotificationManager.IMPORTANCE_NONE
            val chQuiet = !chOff && !silentMode && (ch!!.importance < NotificationManager.IMPORTANCE_DEFAULT || ch.sound == null)
            listOf("喝水", "吃饭", "睡觉", "经期", "自定义").forEach { k ->
                Divider()
                Check("$k 提醒渠道",
                    when {
                        chOff -> "「${ch?.name ?: "提醒"}」被关掉了"
                        chQuiet -> "「${ch!!.name}」被设成静音或低优先级"
                        else -> "「${ch!!.name}」正常" + if (silentMode) "（你选了静音，只震动）" else ""
                    }, !chOff && !chQuiet) {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, chId))
                    }.onFailure { runCatching { ctx.startActivity(Perms.notifSettingsIntent(ctx)) } }
                }
            }
            Divider()
            val exact = Perms.exactAlarm(ctx)
            Check("精确闹钟", if (exact) "已允许，提醒准点响" else "没开的话可能晚几分钟到十几分钟", exact) {
                runCatching { ctx.startActivity(Perms.exactAlarmIntent(ctx)) }
            }
            Divider()
            val battery = Perms.battery(ctx)
            Check("忽略电池优化", if (battery) "已允许" else "省电时系统可能把提醒关掉", battery) {
                runCatching { ctx.startActivity(Perms.batteryIntent(ctx)) }
            }
        }
        SectionTitle("后台和闹钟")
        MCard(padding = 12.dp) {
            val wanted = PetService.wanted()
            val running = PetService.instance != null
            Check("常驻宠物服务", when {
                !wanted -> "没开通知栏宠物 / 桌面宠物（不影响提醒）"
                running -> "正在运行"
                else -> "没在运行，桌面宠物和到点气泡出不来"
            }, if (!wanted) null else running, action = "一键重启") {
                PetService.sync(ctx); tick++; toast("已经重新启动")
            }
            Divider()
            val pending = Reminders.alarmPending(ctx)
            val next = Reminders.upcoming(cfg, Store.priv, Time.now()).firstOrNull()
            val ok = pending && cfg.alarmAt > Time.now()
            Check("下一个闹钟", when {
                !ok -> "没排上（可能被系统清理了）"
                next != null && next.at <= cfg.alarmAt + 60_000L -> "${Time.hm(next.at)} · ${kindName(next.kind, next.extra)}提醒"
                else -> "${Time.hm(cfg.alarmAt)} 醒来检查一次（12 小时内没有提醒）"
            }, ok, action = "一键重排") {
                Reminders.reschedule(ctx); tick++; toast("已经重新排好")
            }
            Divider()
            // 模拟器、部分小牌子读到的厂商是 unknown / 空，就只写「手机」
            val brand = Perms.vendorName().takeUnless { it.isBlank() || it.equals("unknown", true) } ?: "手机"
            Check("$brand 自启动 / 后台锁定", if (cfg.settings.autostartChecked) "你已确认设置好" else
                "系统不让 App 自己查，去设置页允许自启动、在最近任务里锁定萌叮，然后回来打勾",
                cfg.settings.autostartChecked, action = "去设置") {
                runCatching { ctx.startActivity(Perms.vendorAutostart(ctx) ?: Perms.appDetails(ctx)) }
                    .onFailure { runCatching { ctx.startActivity(Perms.appDetails(ctx)) } }
            }
            ToggleRow("我已经设置好自启动和后台锁定", checked = cfg.settings.autostartChecked) { v ->
                Store.update { it.copy(settings = it.settings.copy(autostartChecked = v)) }
            }
        }
        SectionTitle("发一条测试提醒")
        MCard(padding = 12.dp) {
            Text("和真提醒走同一条路：排进系统闹钟 → 到点发通知、桌面宠物冒气泡。锁屏、切到桌面等它响，就知道提醒通不通。",
                fontSize = 13.sp, color = P.sub)
            Gap(10.dp)
            HRow {
                Pill("10 秒后", false) { Reminders.test(ctx, 10); toast("10 秒后响，可以先回到桌面") }
                Pill("1 分钟后", false) { Reminders.test(ctx, 60); toast("1 分钟后响，可以先锁屏") }
            }
        }
    } }
}

/** 一项检查：左边红 / 绿灯（null = 灰，不影响），右边「去打开」 */
@Composable
private fun Check(title: String, desc: String, ok: Boolean?, action: String = "去打开", onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(when (ok) { true -> Color(0xFF3CC08A); false -> Color(0xFFE5606F); null -> P.line }))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = P.ink)
            Text(desc, fontSize = 12.sp, color = if (ok == false) P.pink else P.sub)
        }
        Spacer(Modifier.width(8.dp))
        if (ok != true) Text(action, Modifier.mdTag(if (ok == false) P.pink else P.mint)
            .clickable(onClick = onFix).padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (ok == false) P.pink else P.mintDeep)
        else Text(if (action == "一键重启" || action == "一键重排") action else "正常", Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = action == "一键重启" || action == "一键重排", onClick = onFix).padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 13.sp, color = P.mintDeep)
    }
}
