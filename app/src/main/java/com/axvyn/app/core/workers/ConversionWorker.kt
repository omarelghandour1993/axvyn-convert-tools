package com.axvyn.app.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Reserved for conversions that must survive process death. The foundation has
 * no engines, so this worker intentionally does nothing and reports that fact;
 * it is not scheduled by the UI.
 */
class ConversionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.failure()
}
