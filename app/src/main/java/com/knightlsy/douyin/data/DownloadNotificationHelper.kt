package com.knightlsy.douyin.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.knightlsy.douyin.R

class DownloadNotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "下载任务"
        private const val MIN_UPDATE_INTERVAL = 800L
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastUpdateTime = HashMap<String, Long>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示下载进度"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showProgress(taskId: String, title: String, progress: Int, total: Int) {
        val now = System.currentTimeMillis()
        val last = lastUpdateTime[taskId] ?: 0
        if (now - last < MIN_UPDATE_INTERVAL && progress < 100) return
        lastUpdateTime[taskId] = now

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle(title)
            .setContentText("下载中 $progress%")
            .setProgress(total, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        mainHandler.post {
            notificationManager.notify(taskId.hashCode(), notification)
        }
    }

    fun showCompleted(taskId: String, title: String, count: Int) {
        lastUpdateTime.remove(taskId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle("下载完成")
            .setContentText("$title ($count 个文件)")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        mainHandler.post {
            notificationManager.notify(taskId.hashCode(), notification)
        }
    }

    fun showError(taskId: String, error: String) {
        lastUpdateTime.remove(taskId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle("下载失败")
            .setContentText(error)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        mainHandler.post {
            notificationManager.notify(taskId.hashCode(), notification)
        }
    }

    fun cancel(taskId: String) {
        lastUpdateTime.remove(taskId)
        mainHandler.post { notificationManager.cancel(taskId.hashCode()) }
    }
}
