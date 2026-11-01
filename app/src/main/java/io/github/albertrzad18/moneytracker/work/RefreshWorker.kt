package io.github.albertrzad18.moneytracker.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refresher: Refresher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ok = refresher.refresh(force = inputData.getBoolean(KEY_FORCE, false))
        return when {
            ok -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MAX_RETRIES = 2
    }
}

object RefreshScheduler {
    private const val PERIODIC_WORK = "quotes-refresh-periodic"
    private const val ONE_TIME_WORK = "quotes-refresh-now"
    private const val INTERVAL_MINUTES = 30L

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Refresh right away (widget button, new widget), including stocks at the weekend. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(RefreshWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
