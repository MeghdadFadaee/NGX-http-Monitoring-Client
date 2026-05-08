package net.rodakot.ngxhttpmonitoringclient

import android.app.Application
import net.rodakot.ngxhttpmonitoringclient.background.MonitorJobScheduler
import net.rodakot.ngxhttpmonitoringclient.background.MonitorNotifications

class NgxMonitorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MonitorNotifications.ensureChannel(this)
        MonitorJobScheduler.schedule(this)
    }
}
