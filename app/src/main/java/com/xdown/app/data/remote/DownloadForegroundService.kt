package com.xdown.app.data.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.xdown.app.MainActivity
import com.xdown.app.R
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://x.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "download"
        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, System.currentTimeMillis().toInt())

        startForeground(notificationId, createNotification("Starting download...", 0))

        scope.launch {
            try {
                val outputFile = File(savePath, fileName)
                downloadFile(url, outputFile, notificationId)
                sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).apply {
                    putExtra(EXTRA_FILE_PATH, outputFile.absolutePath)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    setPackage(packageName)
                })
            } catch (e: Exception) {
                sendBroadcast(Intent(ACTION_DOWNLOAD_FAILED).apply {
                    putExtra(EXTRA_ERROR, e.message ?: "Unknown error")
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    setPackage(packageName)
                })
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        notificationId: Int
    ) {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response")
        val contentLength = body.contentLength()
        var downloaded = 0L

        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastNotificationTime = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastNotificationTime > 500) {
                        val progress = if (contentLength > 0) {
                            (downloaded * 100 / contentLength).toInt()
                        } else {
                            -1
                        }

                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(notificationId, createNotification("Downloading...", progress))
                        lastNotificationTime = now
                    }
                }
            }
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, createNotification("Download complete!", 100))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("XDown")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (progress in 0..99) {
            builder.setProgress(100, progress, false)
        } else if (progress >= 100) {
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
            builder.setAutoCancel(true)
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
        }

        return builder.build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "xdown::download"
        ).apply {
            acquire(60 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    companion object {
        const val CHANNEL_ID = "xdown_downloads"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_SAVE_PATH = "extra_save_path"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_ERROR = "extra_error"

        const val ACTION_DOWNLOAD_COMPLETE = "com.xdown.app.DOWNLOAD_COMPLETE"
        const val ACTION_DOWNLOAD_FAILED = "com.xdown.app.DOWNLOAD_FAILED"

        fun start(
            context: Context,
            url: String,
            fileName: String,
            savePath: String,
            notificationId: Int
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_SAVE_PATH, savePath)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
    }
}
