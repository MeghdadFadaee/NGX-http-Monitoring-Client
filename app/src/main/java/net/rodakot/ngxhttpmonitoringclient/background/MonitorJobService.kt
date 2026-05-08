package net.rodakot.ngxhttpmonitoringclient.background

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.rodakot.ngxhttpmonitoringclient.data.MonitorRepository

class MonitorJobService : JobService() {
    private var scope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = jobScope
        jobScope.launch {
            runChecks()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope?.cancel()
        scope = null
        return true
    }

    private fun runChecks() {
        val repository = MonitorRepository(applicationContext)
        repository.pruneHistory()
        repository.servers().filter { it.enabled }.forEach { server ->
            val result = repository.refreshServer(server, forcePersist = true)
            result.alerts.forEach { alert ->
                MonitorNotifications.showAlert(applicationContext, server, alert)
            }
        }
    }
}
