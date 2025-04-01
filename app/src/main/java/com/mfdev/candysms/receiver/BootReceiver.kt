package com.mfdev.candysms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mfdev.candysms.service.RetryFailedForwardService
import com.mfdev.candysms.service.SmsForwardingService

class BootReceiver : BroadcastReceiver() {
    private val SMS_SERVICE_PREFERENCE_FILENAME = "sms_receiver_service_pref"
    private val FAILED_SMS_SERVICE_PREFERENCE_FILENAME = "failed_sms_receiver_service_pref"
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(this::class.simpleName, "Device Booted")
            val sharedPreferences = context.getSharedPreferences(SMS_SERVICE_PREFERENCE_FILENAME, Context.MODE_PRIVATE)
            val startTime = sharedPreferences.getString("start_time", null)
            if (startTime != null) {
                Log.d(this::class.simpleName, "Service was running before reboot, restarting...")
                val serviceIntent = Intent(context, SmsForwardingService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(this::class.simpleName, "Service was not running before reboot")
            }

            val failedSmsSharedPreferences = context.getSharedPreferences(FAILED_SMS_SERVICE_PREFERENCE_FILENAME, Context.MODE_PRIVATE)
            val failedStartTime = failedSmsSharedPreferences.getString("start_time", null)
            if (failedStartTime != null) {
                Log.d(this::class.simpleName, "Failed SMS Retry Service was running before reboot, restarting...")
                val serviceIntent = Intent(context, RetryFailedForwardService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(this::class.simpleName, "Retry SMS Service was not running before reboot")
            }
        }
    }
}