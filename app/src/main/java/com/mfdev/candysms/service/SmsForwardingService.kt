package com.mfdev.candysms.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mfdev.candysms.AppController
import com.mfdev.candysms.R
import com.mfdev.candysms.model.ForwardTask
import com.mfdev.candysms.model.LogEntry
import com.mfdev.candysms.util.TaskStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class SmsForwardingService : Service() {
    private val APP_PREFERENCE = "candy_sms_pref"
    private val PREFERENCE_FILENAME = "sms_receiver_service_pref"
    private val START_TIME_KEY = "start_time"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var smsReceiver: SmsReceiver
    private lateinit var notificationManager: NotificationManager
    private lateinit var taskStorage: TaskStorage

    companion object {
        private const val CHANNEL_ID = "185248187"
        private const val NOTIFICATION_ID = 1
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
        fun getService(): SmsForwardingService = this@SmsForwardingService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        smsReceiver = SmsReceiver()
        taskStorage = TaskStorage(this) // Initialize TaskStorage
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setStartTime(this, Calendar.getInstance().time)
        createNotificationChannel()
        val notification = createNotification("Service Started")
        startForeground(NOTIFICATION_ID, notification)
        registerReceiver(smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
        isServiceRunning = true

        AppController.addLog("SMS Service", "Service Started", LogEntry.Type.Info)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeStartTime(this)
        unregisterReceiver(smsReceiver)
        updateNotificationStatus("Service Stopped")
        AppController.addLog("SMS Service", "Service Stopped", LogEntry.Type.Info)
        isServiceRunning = false
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarding Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarding Service")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
            .build()
    }

    fun isRunning(): Boolean {
        return isServiceRunning
    }

    @SuppressLint("NotificationPermission")
    private fun updateNotificationStatus(statusText: String) {
        val notification = createNotification(statusText)
        val newNotificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(newNotificationId, notification)
    }

    inner class SmsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                // Handle SMS Body & From
                var smsFrom = ""
                var smsBody = ""
                var pdu = ""
                for (message in smsMessages) {
                    smsBody += message.messageBody
                    pdu += message.pdu
                    smsFrom = message.originatingAddress.toString()
                }

                // Handle SMS Timestamp
                val timestamp = smsMessages[0].timestampMillis
                val date = Date(timestamp)
                val formattedDate = formatDate(date)

                // Generate Unique Id And Random
                val random = timestamp + (1000..9999).random()
                val uniqueId = UUID.randomUUID().toString()

                // Log the SMS details
                AppController.addLog(
                    "SMS Received from $smsFrom",
                    smsBody,
                    LogEntry.Type.Info
                )

                forwardSms(
                    smsFrom,
                    smsBody,
                    timestamp,
                    formattedDate,
                    pdu,
                    random.toString(),
                    uniqueId
                )
            }
        }

        private fun formatDate(date: Date): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(date)
        }

        private fun getCurrentInstant(): String {
            val now = Instant.now()
            val formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
            return formatter.format(now)
        }

        private fun forwardSms(
            smsFrom: String,
            smsBody: String,
            timestamp: Long,
            date: String,
            pdu: String,
            random: String,
            uniqueId: String
        ) {
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
                        if (secretKey.isNotEmpty())
                            connection.setRequestProperty("X-Api-Key", secretKey)
                    }

                    val jsonObject = JSONObject()
                    jsonObject.put("id", random)
                    jsonObject.put("from", smsFrom)
                    jsonObject.put("to", "")
                    jsonObject.put("message", smsBody)
                    jsonObject.put("pdu", pdu)
                    jsonObject.put("source", "app")
                    jsonObject.put("action", "message_in")
                    jsonObject.put("user_id", sharedPreferences.getString("device_id", ""))
                    jsonObject.put("send_time", getCurrentInstant())
                    jsonObject.put("receive_time", date)
                    jsonObject.put("timestamp", timestamp)
                    jsonObject.put("uid", uniqueId)

                    val outputStreamWriter = OutputStreamWriter(connection.outputStream)
                    outputStreamWriter.write(jsonObject.toString())
                    outputStreamWriter.flush()
                    outputStreamWriter.close()

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Handle success

                        AppController.addLog(
                            "SMS Forward to $url",
                            "SMS forwarded successfully. Response code: ${connection.responseMessage}",
                            LogEntry.Type.Success
                        )

                        Log.d(
                            this::class.simpleName,
                            "SMS forwarded successfully. Response code: ${connection.responseMessage}"
                        )
                        println("SMS forwarded successfully")
                    } else {
                        // Handle error
                        AppController.addLog(
                            "SMS Forward to $url",
                            "Failed to forward SMS. Response code: ${connection.responseMessage}",
                            LogEntry.Type.Error
                        )

                        Log.d(
                            this::class.simpleName,
                            "Failed to forward SMS. Response code: ${connection.responseMessage}"
                        )

                        // Handle the exception, and store the task for retry
                        val task =
                            ForwardTask(smsBody, smsFrom, timestamp, random, pdu, date, uniqueId)
                        val currentTasks = taskStorage.getTasks().toMutableList()
                        currentTasks.add(task)
                        taskStorage.saveTasks(currentTasks)

                        println("Failed to forward SMS. Response code: $responseCode")
                    }
                    connection.disconnect()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}