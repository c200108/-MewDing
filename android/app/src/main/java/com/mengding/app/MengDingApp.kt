package com.mengding.app

import android.app.Application
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.notify.Notifier
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.Sprites

class MengDingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Sprites.init(this)
        com.mengding.app.pet.PetVoice.warmUp(this)
        Notifier.channels(this)
        Store.update { PetSim.simulate(it, Time.now()) }
        Reminders.reschedule(this)
    }
}
