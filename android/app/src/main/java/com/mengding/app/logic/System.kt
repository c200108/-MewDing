package com.mengding.app.logic

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.mengding.app.data.ConfigState
import com.mengding.app.data.PrivateState
import com.mengding.app.data.Store
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 换桌面图标（需求 I1）：启用一个 activity-alias，停用其余 */
object IconSwitcher {
    val options = listOf(
        "default" to "默认（白猫）",
        "cat" to "像素小猫", "dog" to "像素小狗", "guinea_pig" to "像素豚鼠",
        "sugar_glider" to "像素蜜袋鼯", "snake" to "像素小蛇", "lizard" to "像素蜥蜴",
        "chinchilla" to "像素龙猫",
        "hamster" to "像素仓鼠",
        "turtle" to "像素小乌龟",
        "bunny" to "像素小兔",
        "clownfish" to "像素小丑鱼",
        "hedgehog" to "像素刺猬",
        "swan" to "像素天鹅",
        "pig" to "像素小香猪",
        "tarantula" to "像素毒蛛",
    )

    /** 宠物 key -> activity-alias 名字：cat -> LauncherCat，guinea_pig -> LauncherGuineaPig */
    private fun alias(key: String) = if (key == "default") "LauncherDefault"
        else "Launcher" + key.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    fun apply(ctx: Context, key: String) {
        val pm = ctx.packageManager
        val target = alias(key)
        // 先启用新的再停用旧的，避免桌面上短暂没有图标
        pm.setComponentEnabledSetting(
            ComponentName(ctx, "${ctx.packageName}.$target"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        options.map { alias(it.first) }.distinct().filter { it != target }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(ctx, "${ctx.packageName}.$it"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
            )
        }
        Store.update { it.copy(settings = it.settings.copy(iconAlias = key)) }
    }
}

/** 权限与提醒保障（需求 7A） */
object Perms {
    fun notifications(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun exactAlarm(ctx: Context) = com.mengding.app.notify.Reminders.canExact(ctx)
    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun battery(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    fun activity(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun mic(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun pkg(ctx: Context): Uri = Uri.parse("package:${ctx.packageName}")

    fun exactAlarmIntent(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(ctx))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx))

    fun overlayIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg(ctx))

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(ctx: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(ctx))

    fun notifSettingsIntent(ctx: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(ctx))

    /** 国产系统的自启动设置页（没有统一接口，按机型试） */
    fun vendorAutostart(ctx: Context): Intent? {
        val cands = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        return cands.map { Intent().setComponent(it) }
            .firstOrNull { ctx.packageManager.resolveActivity(it, 0) != null }
    }

    fun vendorName(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi" -> "小米"
        "huawei", "honor" -> "华为/荣耀"
        "oppo", "realme", "oneplus" -> "OPPO/一加/真我"
        "vivo", "iqoo" -> "vivo/iQOO"
        else -> Build.MANUFACTURER
    }
}

/** 本地加密备份（需求 A6）：用户自己设的密码 → PBKDF2 → AES-GCM。换手机时导入。 */
object Backup {
    @Serializable
    data class Payload(
        val version: Int = 1,
        val config: ConfigState,
        val private: PrivateState,
        /** 便签只备份文字，图片和录音不在备份里 */
        val notes: com.mengding.app.data.NotesState = com.mengding.app.data.NotesState(),
    )

    private const val MAGIC = "MDBK1"

    private fun key(pw: String, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)).encoded, "AES",
    )

    fun export(pw: String): ByteArray {
        val plain = Store.json.encodeToString(Payload.serializer(), Payload(config = Store.cfg, private = Store.priv, notes = Store.notesNow)).toByteArray()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + c.doFinal(plain)
    }

    /** 成功返回 null，失败返回原因 */
    fun import(data: ByteArray, pw: String): String? {
        if (data.size < 5 + 28 || String(data, 0, 5) != MAGIC) return "这不是萌叮的备份文件"
        val salt = data.copyOfRange(5, 21)
        val iv = data.copyOfRange(21, 33)
        val plain = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
            c.doFinal(data, 33, data.size - 33)
        }.getOrNull() ?: return "密码不对"
        val p = runCatching { Store.json.decodeFromString(Payload.serializer(), String(plain)) }.getOrNull()
            ?: return "备份文件损坏"
        Store.replaceAll(p.config.copy(lastAlarmCheck = 0, snoozes = emptyList()), p.private)
        if (p.notes.notes.isNotEmpty()) Store.updateNotes { old ->
            val ids = old.notes.map { it.id }.toSet()
            old.copy(notes = old.notes + p.notes.notes.filter { it.id !in ids })
        }
        return null
    }
}
