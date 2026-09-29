package com.example.smartgardenplanner.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.smartgardenplanner.core.CarePlanner
import com.example.smartgardenplanner.core.Feature
import com.example.smartgardenplanner.core.RealSecurityKeyManager
import com.example.smartgardenplanner.core.currentAppTier
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Daily care reminders (FR-019, Pro). Once a day, checks every plot for watering and fertilizing that is
 * due and posts one notification. When online features are on and the plot has a location, the day's rain
 * is fetched first and watering is skipped if enough rain fell or is forecast.
 */
class CareReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val keyManager = RealSecurityKeyManager()
            keyManager.initializeKeyStore()
            val database = AppDatabase.getInstance(applicationContext, keyManager)
            val settings = SettingsRepository(SecurityRepositoryImpl(database.configDao())).load()
            if (!settings.careRemindersEnabled || !Feature.isEnabled(Feature.CARE_REMINDERS, settings.currentAppTier())) {
                return Result.success()
            }
            val online = OnlineSources(database)
            val now = System.currentTimeMillis()
            val lines = mutableListOf<String>()
            for (plot in database.plotDao().getAllPlots()) {
                val snapshot = PlotInsightsLoader.load(database, plot.id, settings) ?: continue
                val lat = plot.latitude
                val lon = plot.longitude
                val rain = if (lat != null && lon != null) {
                    (online.rainMm(lat, lon, settings.onlineFeaturesEnabled) as? OnlineResult.Success)?.value
                } else null
                val tasks = CarePlanner.dueTasks(
                    snapshot.context, database.careLogDao().getByPlotId(plot.id), settings.carePreferenceEnum,
                    now, rain, settings.rainSkipThresholdMm.toDouble()
                )
                tasks.filter { it.isDue }.forEach { lines += it.title }
            }
            if (lines.isNotEmpty()) notify(lines)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Data not readable (e.g. locked key) or storage error: try again tomorrow.
            Result.success()
        }
    }

    private fun notify(lines: List<String>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Garden care reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Garden tasks due")
            .setContentText(lines.first() + if (lines.size > 1) " (+${lines.size - 1} more)" else "")
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.take(6).forEach { style.addLine(it) } })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    companion object {
        private const val CHANNEL_ID = "care_reminders"
        private const val NOTIFICATION_ID = 4101
        private const val WORK_NAME = "care_reminders_daily"

        /** Starts or stops the daily check to match the setting. */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<CareReminderWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .build()
                wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
