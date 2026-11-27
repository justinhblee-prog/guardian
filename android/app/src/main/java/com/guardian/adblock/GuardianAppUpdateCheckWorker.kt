package com.guardian.adblock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks the currently installed distribution channel in the background.
 * It never installs an APK silently: the notification opens Guardian's update
 * screen, where the existing SHA-256 and signer checks run before installation.
 */
internal class GuardianAppUpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {
    override fun doWork(): Result {
        return try {
            val updateDescription = if (BuildConfig.DEBUG) {
                val update = DevUpdateManager.checkForUpdate()
                update?.let { "개발 버전 ${it.versionName}" }
            } else if (BuildConfig.GUARDIAN_STABLE_UPDATER_ENABLED) {
                val update = GuardianStableUpdateManager.checkForUpdate()
                update?.let { "정식 버전 ${it.versionName}" }
            } else {
                return Result.success()
            }
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(LAST_CHECK_KEY, System.currentTimeMillis()).apply()
            if (updateDescription != null) postUpdateNotification(updateDescription)
            Result.success()
        } catch (error: IOException) {
            Log.w(TAG, "Update check temporarily unavailable; WorkManager will retry.", error)
            Result.retry()
        } catch (error: Exception) {
            Log.e(TAG, "Update check failed safely: ${SafeFailureSummary.codeFor(error)}", error)
            // Invalid metadata must not create an endless retry loop. The next
            // scheduled check will retry after the release metadata is corrected.
            Result.success()
        }
    }

    private fun postUpdateNotification(description: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Guardian 앱 업데이트",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    this.description = "새로운 Guardian 앱 버전을 알려드립니다."
                }
            )
        }
        val openUpdates = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_UPDATES, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            if (BuildConfig.DEBUG) 7101 else 7102,
            openUpdates,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Guardian 새 업데이트")
            .setContentText("${description}을 받을 수 있습니다. 눌러서 다운로드하세요.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${description}이 배포되었습니다. 알림을 누르면 Guardian 업데이트 화면이 열리고, 다운로드 및 무결성 검증이 자동으로 시작됩니다."
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(
                if (BuildConfig.DEBUG) 7101 else 7102,
                notification
            )
        } catch (error: SecurityException) {
            Log.i(TAG, "Update found but notification permission is not granted.")
        }
    }

    companion object {
        private const val TAG = "GuardianAppUpdater"
        private const val CHANNEL_ID = "guardian_app_updates_v1"
        private const val PREFS_NAME = "guardian_app_update_scheduler_v1"
        private const val LAST_CHECK_KEY = "last_successful_channel_check_at"
        const val EXTRA_OPEN_UPDATES = "com.guardian.adblock.OPEN_APP_UPDATES"

        fun sync(context: Context) {
            val enabled = BuildConfig.DEBUG || BuildConfig.GUARDIAN_STABLE_UPDATER_ENABLED
            if (!enabled) return

            val channel = if (BuildConfig.DEBUG) "dev" else "stable"
            val interval = if (BuildConfig.DEBUG) 6L else 24L
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<GuardianAppUpdateCheckWorker>(
                interval, TimeUnit.HOURS
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "guardian-app-update-check-$channel",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheckAt = prefs.getLong(LAST_CHECK_KEY, 0L)
            val now = System.currentTimeMillis()
            val intervalMillis = TimeUnit.HOURS.toMillis(interval)
            if (lastCheckAt <= 0L || now < lastCheckAt || now - lastCheckAt >= intervalMillis) {
                val initial = OneTimeWorkRequestBuilder<GuardianAppUpdateCheckWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "guardian-app-update-initial-check-$channel",
                    ExistingWorkPolicy.KEEP,
                    initial
                )
            }
        }
    }
}
