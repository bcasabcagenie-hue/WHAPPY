package com.whappy.chat

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

enum class WhappyDeliveryResult { SENT, QUEUED }

object WhappyMessageSync {
    private const val UNIQUE_WORK = "whappy-message-outbox"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<WhappyMessageSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}

class WhappyMessageSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (WhappyRepository().flushPendingMessages()) Result.success() else Result.retry()
    }.getOrElse { Result.retry() }
}
