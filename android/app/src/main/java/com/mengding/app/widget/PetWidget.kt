package com.mengding.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.mengding.app.R
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.PetSim
import com.mengding.app.notify.Notifier
import com.mengding.app.pet.PetFx
import com.mengding.app.pet.PetService
import com.mengding.app.pet.Sprites

/** 桌面小组件：会动的宠物 + 今天喝了几杯。缩成 1×1 就是"会动的图标" */
class PetWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.update { PetSim.simulate(it, System.currentTimeMillis()) }
        ids.forEach { mgr.updateAppWidget(it, views(ctx)) }
    }

    companion object {
        fun views(ctx: Context): RemoteViews {
            Sprites.init(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_pet)
            val st = Store.cfg
            val pet = PetSim.outing(st)
            if (pet != null) {
                val action = PetFx.actionFor(pet, st)
                PetService.frameIds.forEachIndexed { i, id ->
                    rv.setImageViewBitmap(id, Sprites.frame(ctx, pet.species, action, i, 2, pet.worn))
                }
                rv.setInt(R.id.flipper, "setFlipInterval", Sprites.action(action).delay)
                val cups = Actions.todayWater() / st.settings.cupMl.coerceAtLeast(1)
                rv.setTextViewText(R.id.line, "${pet.name} · 喝水 $cups 杯")
            } else {
                rv.setTextViewText(R.id.line, "萌叮")
            }
            rv.setOnClickPendingIntent(R.id.root, Notifier.openApp(ctx))
            return rv
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PetWidget::class.java))
            if (ids.isNotEmpty()) {
                val rv = views(ctx)
                ids.forEach { mgr.updateAppWidget(it, rv) }
            }
        }
    }
}

/** 数据变了以后，统一刷新通知栏宠物、悬浮宠物和小组件 */
object Refresh {
    fun all(ctx: Context) {
        PetService.instance?.refresh()
        runCatching { PetWidget.updateAll(ctx) }
    }
}
