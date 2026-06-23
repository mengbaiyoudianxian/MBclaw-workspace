package com.mbclaw.nonroot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MBclawNonRootApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        com.mbclaw.nonroot.data.Endpoints.warmUp(this)
        createNotificationChannels()
    }
    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mbclaw_agent", "MBclaw Agent", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "MBclaw 通知"
        })
    }
    companion object {
        lateinit var instance: MBclawNonRootApp
            private set
    }
}
