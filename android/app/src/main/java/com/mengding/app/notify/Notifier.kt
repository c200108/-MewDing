package com.mengding.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mengding.app.MainActivity
import com.mengding.app.R
import com.mengding.app.data.SoundMode
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.Period
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders.Kind
import com.mengding.app.pet.Sprites

object Notifier {
    const val CH_SOFT = "remind_soft"
    const val CH_DING = "remind_ding"
    const val CH_SILENT = "remind_silent"
    const val CH_PET = "pet_status"
    const val CH_NOTE_QUIET = "note_quiet"

    /** 每种宠物一个"叫声"提醒渠道（安卓规定渠道的提示音建好后不能改） */
    fun petChannel(species: String) = "remind_pet_$species"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val ding = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${ctx.packageName}/${R.raw.ding}")
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_SOFT, "提醒（轻柔提示音）", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "喝水、吃饭、睡觉、经期提醒"
                enableVibration(true)
            },
            NotificationChannel(CH_DING, "提醒（萌叮铃铛声）", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "喝水、吃饭、睡觉、经期提醒"
                setSound(ding, attrs)
                enableVibration(true)
            },
            NotificationChannel(CH_SILENT, "提醒（静音）", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "只震动，不出声"
                setSound(null, null)
                enableVibration(true)
            },
            NotificationChannel(CH_PET, "通知栏宠物", NotificationManager.IMPORTANCE_LOW).apply {
                description = "通知栏里常驻的小宠物"
                setShowBadge(false)
            },
            NotificationChannel(CH_NOTE_QUIET, "便签提醒（仅气泡）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "设成「仅弹气泡」的便签提醒，在通知栏留一条，不出声"
                setSound(null, null)
                enableVibration(false)
            },
        ))
        val names = mapOf("cat" to "小猫", "dog" to "小狗", "guinea_pig" to "豚鼠", "sugar_glider" to "蜜袋鼯", "snake" to "小蛇", "lizard" to "蜥蜴")
        nm.createNotificationChannels(names.map { (k, n) ->
            NotificationChannel(petChannel(k), "提醒（${n}叫声）", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "用${n}叫声 + 震动提醒"
                setSound(Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${ctx.packageName}/${com.mengding.app.pet.PetVoice.raw(k)}"), attrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 140, 90, 200)
            }
        })
    }

    private fun channelFor(quiet: Boolean): String = if (quiet) CH_SILENT else when (Store.cfg.settings.sound) {
        SoundMode.SOFT -> CH_SOFT
        SoundMode.DING -> CH_DING
        SoundMode.PET -> petChannel(PetSim.outing(Store.cfg)?.species ?: "cat")
        SoundMode.SILENT -> CH_SILENT
    }

    fun openApp(ctx: Context, tab: String? = null): PendingIntent = PendingIntent.getActivity(
        ctx, (tab ?: "home").hashCode(),
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("tab", tab),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun openNote(ctx: Context, noteId: String): PendingIntent = PendingIntent.getActivity(
        ctx, ("note" + noteId).hashCode(),
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("tab", "notes").putExtra("note", noteId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 便签提醒：桌面宠物冒气泡说出提醒内容；选了"气泡 + 叫声 + 震动"就再叫一声、震一下。
     * 桌面宠物没开时，改用通知（"叫声"模式用宠物叫声渠道，"仅气泡"模式静音）。
     */
    private fun noteRemind(ctx: Context, noteId: String) {
        val note = Store.notesNow.notes.firstOrNull { it.id == noteId } ?: return
        val r = note.reminder ?: return
        val text = r.text.ifBlank { note.title }.ifBlank {
            note.blocks.firstOrNull { it.text.isNotBlank() }?.text?.take(40) ?: "便签提醒"
        }
        val sound = r.mode == com.mengding.app.data.RemindMode.BUBBLE_SOUND
        val bubbled = com.mengding.app.pet.PetService.instance?.speak(text, sound = sound, noteId = note.id) ?: false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val pet = PetSim.outing(Store.cfg)
        val channel = if (sound && !bubbled) petChannel(pet?.species ?: "cat") else CH_NOTE_QUIET
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setColor(0xFF2FB38C.toInt())
            .setAutoCancel(true)
            .setContentTitle("${pet?.name ?: "萌叮"} 提醒你")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openNote(ctx, note.id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        pet?.let { runCatching { b.setLargeIcon(Sprites.frame(ctx, it.species, "energetic", 0, 2, it.worn)) } }
        runCatching { NotificationManagerCompat.from(ctx).notify(idFor(Kind.NOTE, note.id), b.build()) }
    }

    fun action(ctx: Context, act: String, notifId: Int, extra: String = "", req: Int = act.hashCode()): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req + extra.hashCode(),
            Intent(ctx, ActionReceiver::class.java).setAction(act)
                .putExtra("id", notifId).putExtra("extra", extra),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun idFor(kind: Kind, extra: String = "") = 100 + kind.ordinal * 50 + (extra.hashCode() and 31)

    fun remind(ctx: Context, due: Reminders.Due) {
        if (due.kind == Kind.NOTE) { noteRemind(ctx, due.extra); return }
        if (due.kind == Kind.MEAL) mealBubble(ctx, due)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val st = Store.cfg
        val pet = PetSim.outing(st)
        val petName = pet?.name ?: "萌叮"
        val disguise = st.settings.disguise
        val quiet = st.settings.quietOn && Time.inWindow(due.at, st.settings.quietStart, st.settings.quietEnd)
        val id = idFor(due.kind, due.extra)
        val cup = st.settings.cupMl.coerceIn(100, 400)
        var petAction = "idle"
        val b = NotificationCompat.Builder(ctx, channelFor(quiet))
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setColor(0xFF2FB38C.toInt())
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        when (due.kind) {
            Kind.WATER -> {
                val total = Actions.todayWater()
                petAction = "drink"
                b.setContentTitle("$petName 提醒你喝水啦")
                    .setContentText("今天喝了 ${total}ml，目标 ${st.settings.waterGoalMl}ml")
                    .addAction(0, "喝了 ${cup}ml", action(ctx, ActionReceiver.DRINK, id))
                    .addAction(0, "稍后 10 分钟", action(ctx, ActionReceiver.SNOOZE, id, Kind.WATER.name))
                    .addAction(0, "今天不提醒", action(ctx, ActionReceiver.WATER_OFF, id))
            }
            Kind.MEAL -> {
                petAction = "eat"
                // 需求 V8a：宠物饿了渴了，合并到你的饭点一起说
                val petNeed = when {
                    pet == null -> ""
                    pet.satiety < 50 && pet.hydration < 50 -> "${pet.name} 又饿又渴，"
                    pet.satiety < 50 -> "${pet.name} 饿啦，"
                    pet.hydration < 50 -> "${pet.name} 渴啦，"
                    else -> ""
                }
                b.setContentTitle("${due.extra}时间到")
                    .setContentText("${petNeed}你也该吃饭了")
                    .addAction(0, "吃了", action(ctx, ActionReceiver.ATE, id, due.extra))
                    .addAction(0, "稍后 15 分钟", action(ctx, ActionReceiver.SNOOZE, id, Kind.MEAL.name + "|" + due.extra))
            }
            Kind.SLEEP_PRE -> {
                petAction = "tired"
                b.setContentTitle("还有 30 分钟就该睡觉啦")
                    .setContentText("$petName 开始打哈欠了，放下手机准备洗漱吧")
            }
            Kind.SLEEP -> {
                petAction = "sleep"
                b.setContentTitle("该睡觉啦")
                    .setContentText("按时睡觉，$petName 今晚体力恢复翻倍")
                    .addAction(0, "去睡了", action(ctx, ActionReceiver.SLEEP, id))
                    .addAction(0, "再等 15 分钟", action(ctx, ActionReceiver.SNOOZE, id, Kind.SLEEP.name))
            }
            Kind.MORNING -> {
                petAction = "energetic"
                b.setContentTitle("早上好")
                    .setContentText("昨晚睡得怎么样？")
                    .addAction(0, "睡得好", action(ctx, ActionReceiver.WAKE, id, "3"))
                    .addAction(0, "一般", action(ctx, ActionReceiver.WAKE, id, "2"))
                    .addAction(0, "没睡好", action(ctx, ActionReceiver.WAKE, id, "1"))
            }
            Kind.PERIOD_SOON -> {
                petAction = "period"
                val days = st.settings.periodAdvanceDays
                if (disguise) b.setContentTitle("该照顾自己啦").setContentText("最近几天注意休息")
                else b.setContentTitle("大姨妈大概还有 $days 天").setContentText("提前备好用品，少吃生冷")
            }
            Kind.PERIOD_CARE -> {
                petAction = "period"
                val tip = Period.tipOfDay(Period.careTips)
                b.setContentTitle(if (disguise) "该照顾自己啦" else "经期关怀").setContentText(tip)
            }
            Kind.NOTE -> return
            Kind.CARE_SOON, Kind.CARE_TIP -> {
                petAction = "pet"
                val person = Store.priv.care.firstOrNull { it.id == due.extra } ?: return
                if (due.kind == Kind.CARE_SOON) {
                    b.setContentTitle(if (disguise) "关心一下 ${person.name}" else "${person.name} 的经期大概还有 2 天")
                        .setContentText("记得备点红糖，少惹 TA 生气")
                } else {
                    b.setContentTitle("关心一下 ${person.name}").setContentText(Period.tipOfDay(Period.careTaTips))
                }
            }
        }
        if (disguise && due.kind in setOf(Kind.PERIOD_SOON, Kind.PERIOD_CARE, Kind.CARE_SOON, Kind.CARE_TIP)) {
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        }
        pet?.let { runCatching { b.setLargeIcon(Sprites.frame(ctx, it.species, petAction, 0, 2, it.worn)) } }
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) }
    }

    /**
     * 饭点到了：桌面宠物做吃饭的动作，冒一个气泡提醒（躲在边上的话先探出来）。点气泡 = 记下这一餐。
     * 通知照常发；桌面宠物没开时就只有通知。
     */
    private fun mealBubble(ctx: Context, due: Reminders.Due) {
        val st = Store.cfg
        if (!st.settings.mealBubble) return
        val pet = PetSim.outing(st)
        val quiet = st.settings.quietOn && Time.inWindow(due.at, st.settings.quietStart, st.settings.quietEnd)
        val need = when {
            pet == null -> ""
            pet.satiety < 50 -> "我肚子也饿了，"
            else -> ""
        }
        val text = "${due.extra}时间到啦！${need}一起去吃饭吧～\n（吃完点我记一下）"
        com.mengding.app.pet.PetService.instance?.speak(text, sound = !quiet, durationMs = 10 * 60_000L, action = "eat", onTap = {
            val msg = Actions.eat(ctx, due.extra, fromReminder = true)
            cancel(ctx, idFor(Kind.MEAL, due.extra))
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        })
    }

    fun cancel(ctx: Context, id: Int) {
        if (id != 0) NotificationManagerCompat.from(ctx).cancel(id)
    }
}
