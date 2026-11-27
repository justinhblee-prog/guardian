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
                val update = DevUpdateManager.checkForUpdate() ?: return Result.success()
                "개발 버전 ${update.versionName}"
            } else if (BuildConfig.GUARDIAN_STABLE_UPDATER_ENABLED) {
                val update = GuardianStableUpdateManager.checkForUpdate() ?: return Result.success()
                "정식 버전 ${update.versionName}"
            } else {
                return Result.success()
            }
            postUpdateNotification(updateDescription)
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
                    description = "새로운 Guardian 앱 버전을 알려드립니다."
                }
            )
        }
        val openUpdates = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_UPDATES, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
            .setContentText("$description을 받을 수 있습니다. 눌러서 다운로드하세요.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$description이 배포되었습니다. 알림을 누르면 Guardian 업데이트 화면이 열리고, 다운로드 및 무결성 검증이 자동으로 시작됩니다."
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
        const val EXTRA_OPEN_UPDATES = "com.guardian.adblock.OPEN_APP_UPDATES"
        private const val INITIAL_CHECK_KEY = "guardian_app_update_initial_check_enqueued_v1"

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

            val prefs = context.getSharedPreferences("guardian_app_update_scheduler_v1", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(INITIAL_CHECK_KEY, false)) {
                val initial = OneTimeWorkRequestBuilder<GuardianAppUpdateCheckWorker>()
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "guardian-app-update-initial-check-$channel",
                    ExistingWorkPolicy.KEEP,
                    initial
                )
                prefs.edit().putBoolean(INITIAL_CHECK_KEY, true).apply()
            }
        }
    }
}
