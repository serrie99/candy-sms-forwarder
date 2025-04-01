package com.mfdev.candysms

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mfdev.candysms.model.LogEntry
import java.util.Calendar

class AppController : Application() {

    private val TAG = AppController::class.java.simpleName
    private val APP_PREFERENCE = "candy_sms_pref"
    private val LOG_KEY = "log_messages"
    private val gson = Gson()

    companion object {
        private var mInstance: AppController? = null
        private val logListType = object : TypeToken<List<LogEntry>>() {}.type

        @Synchronized
        fun getInstance(): AppController {
            return mInstance!!
        }
        fun addLog(title: String, summary: String?, type: LogEntry.Type) {
            val logEntry = LogEntry(title, summary, Calendar.getInstance().time, type)
            mInstance?.saveLog(logEntry)
        }

        fun clearLog() {
            mInstance?.ResetLogs()
        }
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.d(TAG, "attachBaseContext")
    }

    override fun onCreate() {
        super.onCreate()
        mInstance = this
        sharedPreferences = getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: " + newConfig.locale.language)
    }

    private fun loadLogs(): List<LogEntry> {
        val json = sharedPreferences.getString(LOG_KEY, null)
        return if (json != null) {
            gson.fromJson(json, logListType) ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun ResetLogs() {
        sharedPreferences.edit().remove(LOG_KEY).apply()
    }

    private fun saveLogs(logs: List<LogEntry>) {
        val json = gson.toJson(logs)
        sharedPreferences.edit().putString(LOG_KEY, json).apply()
    }

    private fun saveLog(logEntry: LogEntry) {
        val currentLogs = loadLogs().toMutableList()
        currentLogs.add(0, logEntry) // Insert at index 0

        // Trim the list to a maximum of 100 entries
        if (currentLogs.size > 100) {
            currentLogs.subList(100, currentLogs.size).clear()
        }
        saveLogs(currentLogs)
    }
}