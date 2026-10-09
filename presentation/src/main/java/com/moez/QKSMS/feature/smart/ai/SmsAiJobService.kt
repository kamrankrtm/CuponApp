package com.moez.QKSMS.feature.smart.ai

import android.app.job.JobParameters
import android.app.job.JobService
import com.moez.QKSMS.feature.smart.SmartDataManager
import com.moez.QKSMS.util.Preferences
import dagger.android.AndroidInjection
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Runs [SmsAiFallback] jobs off the main thread, outside any SMS receiver's lifetime. Each job
 * is one message; retries are scheduled as new jobs, so the count survives a restart.
 */
class SmsAiJobService : JobService() {

    @Inject lateinit var prefs: Preferences

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartJob(params: JobParameters): Boolean {
        SmartDataManager.init(applicationContext)
        executor.execute {
            try {
                SmsAiFallback.run(applicationContext, prefs, params.extras)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** Stopped by the system (network lost): the local reading stands; a retry is already queued if one is due. */
    override fun onStopJob(params: JobParameters): Boolean = false

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
