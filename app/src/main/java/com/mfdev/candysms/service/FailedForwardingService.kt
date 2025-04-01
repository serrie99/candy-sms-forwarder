package com.mfdev.candysms.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.mfdev.candysms.AppController
import com.mfdev.candysms.model.ForwardTask
import com.mfdev.candysms.model.LogEntry
import com.mfdev.candysms.util.TaskStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.io.OutputStreamWriter
import android.content.SharedPreferences
import android.os.Binder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mfdev.candysms.R
import com.mfdev.candysms.service.SmsForwardingService.Companion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date

class RetryFailedForwardService : Service() {

    private val APP_PREFERENCE = "candy_sms_pref"
    private val PREFERENCE_FILENAME = "failed_sms_receiver_service_pref"
    private val START_TIME_KEY = "start_time"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var taskStorage: TaskStorage
    private lateinit var notificationManager: NotificationManager

    private var periodicTaskJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "185248188"
        private const val NOTIFICATION_ID = 2
        private var isServiceRunning = false // Static variable to track service state

        fun isServiceRunning(context: Context): Boolean {
            return isServiceRunning
        }
    }

    private val binder = LocalBinder()
    private fun setStartTime(context: Context, time: Date) {
        val preferencesWriter =
            context.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        preferencesWriter.edit()
            .putString(START_TIME_KEY, time.time.toString())
            .apply()
    }

    private fun removeStartTime(context: Context) {
        val preferencesWriter =
            context.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        preferencesWriter.edit()
            .remove(START_TIME_KEY)
            .apply()
    }

    fun getStartTime(context: Context): Long? {
        val preferencesReader =
            context.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        val timeString = preferencesReader.getString(START_TIME_KEY, null)
        return timeString?.toLongOrNull()
    }

    inner class LocalBinder : Binder() {
        fun getService(): RetryFailedForwardService = this@RetryFailedForwardService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        taskStorage = TaskStorage(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setStartTime(this, Calendar.getInstance().time)
        createNotificationChannel()
        val notification = createNotification("Service Started")
        startForeground(NOTIFICATION_ID, notification)
        startPeriodicTask()
        isServiceRunning = true
        AppController.addLog("Retry SMS Service", "Service Started", LogEntry.Type.Info)
        return START_STICKY
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Retry Failed SMS Forwarding Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Retry SMS Forwarding Service")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
            .build()
    }

    @SuppressLint("NotificationPermission")
    private fun updateNotificationStatus(statusText: String) {
        val notification = createNotification(statusText)
        val newNotificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(newNotificationId, notification)
    }

    fun isRunning(): Boolean {
        return isServiceRunning
    }

    override fun onDestroy() {
        super.onDestroy()
        removeStartTime(this)
        periodicTaskJob?.cancel()
        updateNotificationStatus("Service Stopped")
        AppController.addLog("Retry SMS Service", "Service Stopped", LogEntry.Type.Info)
        isServiceRunning = false
    }

    private fun startPeriodicTask() {
        periodicTaskJob = coroutineScope.launch {
            while (isServiceRunning) {
                retryFailedTasks()
                delay(5 * 60 * 1000) // 5 minutes
            }
        }
    }

    private fun retryFailedTasks() {
        coroutineScope.launch {
            val tasks = taskStorage.getTasks()
            Log.d(this::class.simpleName,"Found ${tasks.size} in storage.")
            AppController.addLog(
                "Retry SMS Forward Triggered",
                "Found ${tasks.size} Tasks.",
                LogEntry.Type.Info
            )
            if (tasks.isNotEmpty()) {
                for (task in tasks) {
                    forwardSms(task)
                }
            }
        }
    }

    fun getTaskCount(): Int {
        return taskStorage.getTasks().size
    }

    private fun getCurrentInstant(): String {
        val now = Instant.now()
        val formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        return formatter.format(now)
    }

    private fun forwardSms(task: ForwardTask) {
        val sharedPreferences: SharedPreferences =
            getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
        val secretKey = sharedPreferences.getString("secret_key", "")
        coroutineScope.launch {
            try {
                val url =
                    URL(sharedPreferences.getString("post_url", ""))
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                // Attach SecretKey to Header if Set
                if (secretKey != null) {
                    if(secretKey.isNotEmpty())
                        connection.setRequestProperty("X-Api-Key", secretKey)
                }

                val jsonObject = JSONObject()
                jsonObject.put("id", task.timestamp.toString())
                jsonObject.put("from", task.from)
                jsonObject.put("to", "")
                jsonObject.put("message", task.messageBody)
                jsonObject.put("pdu", task.pdu)
                jsonObject.put("source", "app")
                jsonObject.put("action", "message_in")
                jsonObject.put("user_id", sharedPreferences.getString("device_id", ""))
                jsonObject.put("send_time", getCurrentInstant())
                jsonObject.put("receive_time", task.date)
                jsonObject.put("timestamp", task.timestamp)
                jsonObject.put("uid", task.uniqueId)

                val outputStreamWriter = OutputStreamWriter(connection.outputStream)
                outputStreamWriter.write(jsonObject.toString())
                outputStreamWriter.flush()
                outputStreamWriter.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Handle success
                    AppController.addLog(
                        "Retry SMS Forward to $url",
                        "SMS forwarded successfully. Response code: ${connection.responseMessage}",
                        LogEntry.Type.Success
                    )
                    // Remove task from storage if success
                    val currentTasks = taskStorage.getTasks().toMutableList()
                    currentTasks.remove(task)
                    taskStorage.saveTasks(currentTasks)

                } else {
                    // Handle error
                    AppController.addLog(
                        "Retry SMS Forward to $url",
                        "Failed to forward SMS. Response code: ${connection.responseMessage}",
                        LogEntry.Type.Error
                    )
                }
                connection.disconnect()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
