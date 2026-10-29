package com.mengding.app

import android.app.Application
import com.mengding.app.data.Store
import com.mengding.app.logic.PetSim
import com.mengding.app.logic.Time
import com.mengding.app.notify.Notifier
import com.mengding.app.notify.Reminders
import com.mengding.app.pet.Sprites

class MengDingApp : Application() {
    companion object {
        /** 没有界面 Context 的地方（小游戏结算等）响「叮」用 */
        lateinit var app: android.content.Context
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        Store.init(this)
        Sprites.init(this)
        com.mengding.app.pet.PetVoice.warmUp(this)
        Notifier.channels(this)
        Store.update { PetSim.simulate(it, Time.now()) }
        // 先看有没有该响没响的（进程被杀、闹钟被清理），再重排
        if (Store.cfg.profile.onboarded) Reminders.checkMissed(this)
        Reminders.reschedule(this)
        // 在线更新：已经装上的安装包缓存删掉
        com.mengding.app.logic.Updater.cleanup(this)
        // 便签视频：清掉没被任何便签用到的残留文件（后台做，不拖慢启动）
        Thread({
            runCatching {
                val used = Store.notesNow.notes.flatMap { n -> n.blocks.mapNotNull { it.file } }.toSet()
                com.mengding.app.logic.NoteVideo.files.cleanOrphans(used)
            }
        }, "video-clean").start()
    }
}
