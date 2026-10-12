package com.mengding.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mengding.app.logic.Actions
import com.mengding.app.notify.Reminders.Kind
import com.mengding.app.pet.PetService

/** 闹钟到点 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.fire(ctx)
    }
}

/** 开机、升级、改时区、改时间后重新排提醒，并恢复常驻宠物 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.reschedule(ctx)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            PetService.sync(ctx)
        }
    }
}

/** 通知上的按钮 */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val DRINK = "com.mengding.app.DRINK"
        const val SNOOZE = "com.mengding.app.SNOOZE"
        const val WATER_OFF = "com.mengding.app.WATER_OFF"
        const val ATE = "com.mengding.app.ATE"
        const val SLEEP = "com.mengding.app.SLEEP"
        const val WAKE = "com.mengding.app.WAKE"
        const val PET = "com.mengding.app.PET"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val extra = intent.getStringExtra("extra") ?: ""
        val msg: String? = when (intent.action) {
            DRINK -> Actions.drink(ctx, fromReminder = id != 0)
            SNOOZE -> {
                val parts = extra.split("|", limit = 2)
                val kind = parts[0]
                val minutes = if (kind == Kind.WATER.name) 10 else 15
                Actions.snooze(ctx, kind, minutes, parts.getOrElse(1) { "" })
                "$minutes 分钟后再提醒你"
            }
            WATER_OFF -> { Actions.waterOffToday(ctx); "今天不再提醒喝水" }
            ATE -> Actions.eat(ctx, extra, fromReminder = true)
            SLEEP -> Actions.goSleep(ctx, fromReminder = true)
            WAKE -> Actions.wakeUp(ctx, extra.toIntOrNull() ?: 2)
            PET -> Actions.petPet(ctx)
            else -> null
        }
        Notifier.cancel(ctx, id)
        msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }
}
