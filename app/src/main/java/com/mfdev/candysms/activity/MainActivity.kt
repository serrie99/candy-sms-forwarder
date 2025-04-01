package com.mfdev.candysms.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import java.util.concurrent.TimeUnit
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mfdev.candysms.R
import com.mfdev.candysms.service.RetryFailedForwardService
import com.mfdev.candysms.service.SmsForwardingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName
    private val APP_PREFERENCE = "candy_sms_pref"

    private val SMS_PERMISSION_CODE = 100
    private val SMS_AND_POST_NOTIFICATIONS_REQUEST_CODE = 101

    // SMS Forwarding Service variable
    private var smsService: SmsForwardingService? = null
    private var isServiceBound by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var statusUpdateJob: Job? = null
    private var receiverStatus by mutableStateOf("")
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            val binder = service as SmsForwardingService.LocalBinder
            smsService = binder.getService()
            isServiceBound = true
            updateServiceStatus()
            startUpdatingReceiverStatusAsync()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Disconnected")
            isServiceBound = false
            smsService = null
            stopUpdatingReceiverStatusAsync()
        }
    }

    // Failed SMS Service variable
    private var retryFailedSmsService: RetryFailedForwardService? = null
    private var isFailedServiceBound by mutableStateOf(false)
    private var retryFailedSmsServiceRunning by mutableStateOf(false)
    private var failedStatusUpdateJob: Job? = null
    private var failedServiceStatus by mutableStateOf("")
    private val failedServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Failed Service Connected")
            val binder = service as RetryFailedForwardService.LocalBinder
            retryFailedSmsService = binder.getService()
            isFailedServiceBound = true
            updateFailedServiceStatus()
            startUpdatingFailedServiceStatusAsync()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Failed SMS Service Disconnected")
            isFailedServiceBound = false
            retryFailedSmsService = null
            stopUpdatingFailedServiceStatusAsync()
        }
    }

    // Declare Views
    private lateinit var postUrlInputLayout: TextInputLayout
    private lateinit var postUrlEditText: TextInputEditText
    private lateinit var savePostUrlButton: MaterialButton
    private lateinit var statusTextView: TextView
    private lateinit var smsServiceFab: FloatingActionButton
    private lateinit var failedStatusTextView: TextView
    private lateinit var failedSmsServiceFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        getDeviceId(this)

        postUrlInputLayout = findViewById(R.id.postUrlInputLayout)
        postUrlEditText = findViewById(R.id.postUrlEditText)
        savePostUrlButton = findViewById(R.id.savePostUrlButton)

        statusTextView = findViewById(R.id.statusTextView)
        smsServiceFab = findViewById(R.id.actionButton)
        smsServiceFab.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                R.color.colorWhite
            )
        )

        failedStatusTextView = findViewById(R.id.failedStatusTextView)
        failedSmsServiceFab = findViewById(R.id.failedSmsActionButton)
        failedSmsServiceFab.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                R.color.colorWhite
            )
        )

        failedSmsServiceFab.setOnClickListener {
            if (retryFailedSmsServiceRunning) {
                stopFailedSmsForwardingService()
            } else {
                checkAndRequestPermission {
                    startFailedSmsForwardingService()
                }
            }
        }

        postUrlEditText.setText(getPostUrl(this))
        savePostUrlButton.setOnClickListener {
            val sharedPreferences: SharedPreferences =
                getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("post_url", postUrlEditText.text.toString()).apply()
            Toast.makeText(this, "URL Saved Successfully", Toast.LENGTH_SHORT).show()
        }
        smsServiceFab.setOnClickListener {
            if (isServiceRunning) {
                stopSmsForwardingService()
            } else {
                checkAndRequestPermission {
                    startSmsForwardingService()
                }
            }
        }

        // Check if the SMS service is running and bind to it if it is
        if (SmsForwardingService.isServiceRunning(this)) {
            val serviceIntent = Intent(this, SmsForwardingService::class.java)
            bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        } else {
            updateServiceStatus()
        }

        // Check if the Failed service is running and bind to it if it is
        if (RetryFailedForwardService.isServiceRunning(this)) {
            val serviceIntent = Intent(this, RetryFailedForwardService::class.java)
            bindService(serviceIntent, failedServiceConnection, BIND_AUTO_CREATE)
        } else {
            updateFailedServiceStatus()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getDeviceId(context: Context): String {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
        var deviceId = sharedPreferences.getString("device_id", null)

        if (deviceId == null) {
            deviceId = generateGUID()
            sharedPreferences.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    private fun getPostUrl(context: Context): String? {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
        val postUrl = sharedPreferences.getString("post_url", "")
        return postUrl
    }

    private fun generateGUID(): String {
        return UUID.randomUUID().toString()
    }

    private fun checkAndRequestPermission(onPermissionGranted: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECEIVE_SMS
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Requesting SMS And Notification Permission")

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    SMS_AND_POST_NOTIFICATIONS_REQUEST_CODE
                )

            } else {
                onPermissionGranted()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECEIVE_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Requesting SMS Permission")

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECEIVE_SMS),
                    SMS_PERMISSION_CODE
                )

            } else {
                onPermissionGranted()
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "SMS Permission Granted")
                    startSmsForwardingService()
                } else {
                    Log.d(TAG, "SMS Permission Denied")
                }
            }

            SMS_AND_POST_NOTIFICATIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty()) {
                    var allPermissionsGranted = true
                    for (result in grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            allPermissionsGranted = false
                            break
                        }
                    }
                    if (allPermissionsGranted) {
                        Log.d(TAG, "All permissions granted")
                        startSmsForwardingService()
                    } else {
                        Log.d(TAG, "Some permissions denied")
                    }
                }
            }
        }
    }

    private fun startSmsForwardingService() {
        val serviceIntent = Intent(this, SmsForwardingService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun stopSmsForwardingService() {
        isServiceRunning = false // Update state immediately
        val serviceIntent = Intent(this, SmsForwardingService::class.java)
        stopService(serviceIntent)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateServiceStatus() {
        isServiceRunning = smsService?.isRunning() ?: false
        if (isServiceRunning) {
            val startTime = smsService?.getStartTime(this)
            if (startTime != null) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val days = TimeUnit.MILLISECONDS.toDays(elapsedTime)
                val hours = TimeUnit.MILLISECONDS.toHours(elapsedTime) - TimeUnit.DAYS.toHours(days)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) -
                        TimeUnit.DAYS.toMinutes(days) - TimeUnit.HOURS.toMinutes(hours)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) -
                        TimeUnit.DAYS.toSeconds(days) - TimeUnit.HOURS.toSeconds(hours) -
                        TimeUnit.MINUTES.toSeconds(minutes)
                val elapsedTimeStr = (if (days > 0) days.toString() + "d " else "") +
                        (if (days > 0 || hours > 0) hours.toString() + "h " else "") +
                        (if (days > 0 || hours > 0 || minutes > 0) minutes.toString() + "min " else "") +
                        seconds.toString() + "s"
                receiverStatus = "Running for: $elapsedTimeStr"
                smsServiceFab.setImageDrawable(getDrawable(R.drawable.ic_stop))
            } else {
                receiverStatus = "Service is running"
                smsServiceFab.setImageDrawable(getDrawable(R.drawable.ic_stop))
            }
        } else {
            receiverStatus = "Service is stopped"
            smsServiceFab.setImageDrawable(getDrawable(R.drawable.ic_play_arrow))
        }
        statusTextView.text = receiverStatus


    }

    private fun startUpdatingReceiverStatusAsync() {
        statusUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateServiceStatus()
                delay(2000)
            }
        }
    }

    private fun stopUpdatingReceiverStatusAsync() {
        statusUpdateJob?.cancel()
        statusUpdateJob = null
    }

    private fun startFailedSmsForwardingService() {
        val serviceIntent = Intent(this, RetryFailedForwardService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, failedServiceConnection, BIND_AUTO_CREATE)
    }

    private fun stopFailedSmsForwardingService() {
        retryFailedSmsServiceRunning = false // Update state immediately
        val serviceIntent = Intent(this, RetryFailedForwardService::class.java)
        stopService(serviceIntent)
        if (isFailedServiceBound) {
            unbindService(failedServiceConnection)
            isFailedServiceBound = false
        }
    }

    private fun startUpdatingFailedServiceStatusAsync() {
        failedStatusUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateFailedServiceStatus()
                delay(2000)
            }
        }
    }

    private fun stopUpdatingFailedServiceStatusAsync() {
        failedStatusUpdateJob?.cancel()
        failedStatusUpdateJob = null
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateFailedServiceStatus() {
        retryFailedSmsServiceRunning = retryFailedSmsService?.isRunning() ?: false

        var taskCount = 0
        if (retryFailedSmsService != null) {
            taskCount = retryFailedSmsService?.getTaskCount() ?: 0
        }

        if (retryFailedSmsServiceRunning) {
            val startTime = retryFailedSmsService?.getStartTime(this)
            if (startTime != null) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val days = TimeUnit.MILLISECONDS.toDays(elapsedTime)
                val hours = TimeUnit.MILLISECONDS.toHours(elapsedTime) - TimeUnit.DAYS.toHours(days)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) -
                        TimeUnit.DAYS.toMinutes(days) - TimeUnit.HOURS.toMinutes(hours)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) -
                        TimeUnit.DAYS.toSeconds(days) - TimeUnit.HOURS.toSeconds(hours) -
                        TimeUnit.MINUTES.toSeconds(minutes)
                val elapsedTimeStr = (if (days > 0) days.toString() + "d " else "") +
                        (if (days > 0 || hours > 0) hours.toString() + "h " else "") +
                        (if (days > 0 || hours > 0 || minutes > 0) minutes.toString() + "min " else "") +
                        seconds.toString() + "s"
                failedServiceStatus = "Running for: $elapsedTimeStr , Tasks: $taskCount"
                failedSmsServiceFab.setImageDrawable(getDrawable(R.drawable.ic_stop))
            } else {
                failedServiceStatus = "Service is running, Tasks: $taskCount"
                failedSmsServiceFab.setImageDrawable(getDrawable(R.drawable.ic_stop))
            }
        } else {
            failedServiceStatus = "Service is stopped, Tasks: $taskCount"
            failedSmsServiceFab.setImageDrawable(getDrawable(R.drawable.ic_play_arrow))
        }

        failedStatusTextView.text = failedServiceStatus
    }
}




