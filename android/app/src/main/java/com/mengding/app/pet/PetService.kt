package com.mengding.app.pet

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mengding.app.R
import com.mengding.app.data.Store
import com.mengding.app.logic.Actions
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.notify.ActionReceiver
import com.mengding.app.notify.Notifier
import com.mengding.app.notify.Reminders
import com.mengding.app.widget.PetWidget

/**
 * 常驻服务（前台服务）：通知栏会动的宠物、桌面悬浮宠物、电量彩蛋、陪我走路。
 * 三个开关任意一个打开就运行；都关了就停。每分钟心跳一次补算宠物数值。
 */
class PetService : Service(), SensorEventListener {
    companion object {
        const val NID = 7
        @Volatile var instance: PetService? = null

        fun wanted(): Boolean = Store.cfg.settings.let { it.persistentPet || it.floatingPet || it.stepWalk } &&
            Store.cfg.pets.isNotEmpty()

        fun sync(ctx: Context) {
            val i = Intent(ctx, PetService::class.java)
            if (wanted()) runCatching { ContextCompat.startForegroundService(ctx, i) }
            else ctx.stopService(i)
        }

        val frameIds = intArrayOf(R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5, R.id.f6, R.id.f7)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var floating: FloatingPet? = null
    private var lastSig = ""
    private var screenOn = true
    private var stepSensorOn = false

    private var lastSpeech = 0L

    private val tick = object : Runnable {
        override fun run() {
            Store.update { PetSim.simulate(it, Time.now()) }
            refresh()
            maybeSpeak()
            handler.postDelayed(this, 60_000)
        }
    }

    /** 每隔一段时间说一句鼓励的话（屏幕亮着、不在你的睡觉时段） */
    private fun maybeSpeak() {
        val s = Store.cfg.settings
        val now = Time.now()
        if (!s.speechOn || !screenOn || floating == null) return
        if (Time.inWindow(now, Store.cfg.profile.sleep, Store.cfg.profile.wake)) return
        if (lastSpeech == 0L) { lastSpeech = now - s.speechIntervalMin * 60_000L + 30_000L; return }
        if (now - lastSpeech < s.speechIntervalMin * 60_000L) return
        lastSpeech = now
        floating?.speak(Speech.phrase())
    }

    /**
     * 让桌面宠物说话。便签提醒走这里：sound = true 时再叫一声、震一下。
     * 桌面宠物没显示时返回 false，由调用方改用通知提醒。
     */
    fun speak(text: String, sound: Boolean, noteId: String? = null, onTap: (() -> Unit)? = null,
              durationMs: Long? = null, action: String? = null): Boolean {
        val f = floating ?: return false
        val ok = f.speak(text, durationMs ?: if (noteId != null) 60_000 else 6000, noteId, onTap,
            popOut = noteId != null || onTap != null, action = action)
        if (ok && sound) {
            PetSim.outing(Store.cfg)?.let { PetVoice.play(this, it.species, force = true) }
            PetVoice.vibrate(this)
        }
        if (ok) lastSpeech = Time.now()
        return ok
    }

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val pct = level * 100 / scale
            val changed = PetFx.charging != plugged || PetFx.batteryLow != (pct < 15 && !plugged)
            PetFx.charging = plugged
            PetFx.batteryLow = pct < 15 && !plugged
            if (changed) refresh()
        }
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            screenOn = i.action != Intent.ACTION_SCREEN_OFF
            floating?.setPaused(!screenOn)
            if (screenOn) refresh()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Sprites.init(this)
        registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NID, n)
        handler.removeCallbacks(tick)
        handler.post(tick)
        applySettings()
        return START_STICKY
    }

    /** 根据设置开关悬浮宠物和计步 */
    fun applySettings() {
        val s = Store.cfg.settings
        if (!wanted()) { stopSelf(); return }
        val canOverlay = Settings.canDrawOverlays(this)
        if (s.floatingPet && canOverlay && !FloatingPet.hiddenByUser) {
            if (floating == null) floating = FloatingPet(this).also { it.show() }
        } else {
            floating?.hide(); floating = null
        }
        val sm = getSystemService(SensorManager::class.java)
        val granted = checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val wantSteps = s.stepWalk || (s.floatingPet && s.petRunMode != "off")
        if (wantSteps && granted && sensor != null) {
            if (!stepSensorOn) { sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL); stepSensorOn = true }
        } else if (stepSensorOn) {
            sm.unregisterListener(this); stepSensorOn = false
        }
        refresh(force = true)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val walkingBefore = PetFx.walkingUntil > Time.now()
        PetFx.walkingUntil = Time.now() + 4000
        if (!walkingBefore) refresh()
        handler.removeCallbacks(walkEnd)
        handler.postDelayed(walkEnd, 4500)
    }

    private val walkEnd = Runnable { refresh() }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 刷新通知和悬浮宠物；内容没变就不重发通知 */
    fun refresh(force: Boolean = false) {
        floating?.refresh()
        if (!screenOn && !force) return
        val st = Store.cfg
        val pet = PetSim.outing(st) ?: return
        val action = PetFx.actionFor(pet, st)
        val sig = "${pet.species}|${pet.name}|${pet.worn}|$action|${line(st)}|${PetSim.describe(pet)}"
        if (sig == lastSig && !force) return
        lastSig = sig
        getSystemService(android.app.NotificationManager::class.java).notify(NID, buildNotification())
    }

    private fun line(st: com.mengding.app.data.ConfigState): String {
        val cups = Actions.todayWater() / st.settings.cupMl.coerceAtLeast(1)
        val goalCups = st.settings.waterGoalMl / st.settings.cupMl.coerceAtLeast(1)
        val next = Reminders.nextTime()?.let { " · 下次提醒 ${Time.hm(it)}" } ?: ""
        return "喝水 $cups/$goalCups 杯$next"
    }

    private fun buildNotification(): Notification {
        val st = Store.cfg
        val pet = PetSim.outing(st)
        val b = NotificationCompat.Builder(this, Notifier.CH_PET)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setColor(0xFF2FB38C.toInt())
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notifier.openApp(this))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (pet == null) return b.setContentTitle("萌叮").build()
        val action = PetFx.actionFor(pet, st)
        val a = Sprites.action(action)
        val title = "${pet.name} 正在陪你 · ${PetSim.describe(pet)}"
        val rv = RemoteViews(packageName, R.layout.notif_pet)
        frameIds.forEachIndexed { i, id -> rv.setImageViewBitmap(id, Sprites.frame(this, pet.species, action, i, 2, pet.worn)) }
        rv.setInt(R.id.flipper, "setFlipInterval", a.delay)
        rv.setTextViewText(R.id.title, title)
        rv.setTextViewText(R.id.line, line(st))
        return b.setContentTitle(title)
            .setContentText(line(st))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .addAction(0, "喝一杯", Notifier.action(this, ActionReceiver.DRINK, 0, req = 7001))
            .addAction(0, "摸摸它", Notifier.action(this, ActionReceiver.PET, 0, req = 7002))
            .build()
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        floating?.hide()
        runCatching { unregisterReceiver(battery) }
        runCatching { unregisterReceiver(screen) }
        if (stepSensorOn) getSystemService(SensorManager::class.java).unregisterListener(this)
        super.onDestroy()
    }
}
