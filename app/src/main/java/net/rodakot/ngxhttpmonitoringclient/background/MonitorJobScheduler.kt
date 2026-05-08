package net.rodakot.ngxhttpmonitoringclient.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

object MonitorJobScheduler {
    private const val JobId = 50901

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val componentName = ComponentName(context, MonitorJobService::class.java)
        val job = JobInfo.Builder(JobId, componentName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(TimeUnit.MINUTES.toMillis(15))
            .build()
        scheduler.schedule(job)
    }
}
