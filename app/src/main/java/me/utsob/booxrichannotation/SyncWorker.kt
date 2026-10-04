package me.utsob.booxrichannotation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Background job that pushes new NeoReader highlights to Notion. */
class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(NotionSync.PREFS, Context.MODE_PRIVATE)
        return try {
            val status = NotionSync.run(applicationContext)
            prefs.edit().putString(NotionSync.KEY_LAST_STATUS, status).apply()
            Result.success()
        } catch (e: NotionClient.NotionException) {
            prefs.edit().putString(NotionSync.KEY_LAST_STATUS, "Last sync failed: ${e.message}").apply()
            // Auth/setup errors won't fix themselves; the next hourly run will try again anyway.
            if (e.code == 429 || e.code >= 500) Result.retry() else Result.failure()
        } catch (e: IOException) {
            prefs.edit().putString(NotionSync.KEY_LAST_STATUS, "Last sync failed (network): ${e.message}").apply()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "notion-sync"

        /** Schedules the hourly sync if setup is complete. Safe to call repeatedly. */
        fun schedule(context: Context) {
            if (!NotionSync.isConfigured(context)) return
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
