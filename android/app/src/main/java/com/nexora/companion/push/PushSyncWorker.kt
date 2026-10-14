package com.nexora.companion.push

import android.content.Context
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessaging
import com.nexora.companion.NexoraApplication
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException

class PushSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val c=(applicationContext as NexoraApplication).container
        if(!c.telemetry.available || c.repository.session.value==null)return Result.success()
        return try {
            if(c.preferences.consent.value.notifications) {
                FirebaseMessaging.getInstance().isAutoInitEnabled=true
                c.repository.registerDevice(FirebaseMessaging.getInstance().token.await())
            } else {
                c.repository.registerDevice(null)
                FirebaseMessaging.getInstance().isAutoInitEnabled=false
                FirebaseMessaging.getInstance().deleteToken().await()
            }
            Result.success()
        } catch(e: CancellationException) { throw e } catch (_: Exception) { if(runAttemptCount<5) Result.retry() else Result.failure() }
    }
    companion object {
        fun enqueue(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("push-sync",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<PushSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) }
    }
}
