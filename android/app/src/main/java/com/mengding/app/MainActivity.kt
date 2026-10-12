package com.mengding.app

import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.FloatingPet
import com.mengding.app.pet.PetService
import com.mengding.app.ui.AppRoot
import com.mengding.app.ui.MengDingTheme
import com.mengding.app.ui.Nav
import com.mengding.app.widget.Refresh

class MainActivity : ComponentActivity() {
    var unlocked by mutableStateOf(false)
    private var pausedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { MengDingTheme { AppRoot(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra("tab")?.let {
            Nav.stack.clear()
            if (it in setOf("notes", "games", "room")) { Nav.tab = "home"; Nav.go(it) } else Nav.tab = it
        }
        i?.getStringExtra("note")?.let { if (Store.notesNow.notes.any { n -> n.id == it }) Nav.stack.add("note:$it") }
    }

    override fun onResume() {
        super.onResume()
        Store.update { PetSim.simulate(it, Time.now()) }
        // 打开 App 时，之前"收起"的悬浮宠物恢复显示
        if (FloatingPet.hiddenByUser) { FloatingPet.hiddenByUser = false; PetService.instance?.applySettings() }
        if (Store.cfg.profile.onboarded) {
            PetService.sync(this)
            Reminders.reschedule(this)
            Refresh.all(this)
        }
        if (Store.cfg.settings.privacyLock && unlocked && Time.now() - pausedAt > 60_000 && pausedAt > 0) unlocked = false
    }

    override fun onPause() {
        super.onPause()
        pausedAt = Time.now()
    }

    /** 隐私锁：指纹/面容，或者手机锁屏密码 */
    fun authenticate() {
        val bm = getSystemService(BiometricManager::class.java)
        val can = if (Build.VERSION.SDK_INT >= 30)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") bm.canAuthenticate()
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "手机没有设置锁屏密码，隐私锁暂时不起作用", Toast.LENGTH_LONG).show()
            unlocked = true
            return
        }
        val b = BiometricPrompt.Builder(this).setTitle("解锁萌叮").setSubtitle("验证身份后查看你的记录")
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        }
        b.build().authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { unlocked = true }
        })
    }
}
