package com.mfdev.candysms.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mfdev.candysms.AppController
import com.mfdev.candysms.R
import com.mfdev.candysms.adapter.LogAdapter
import com.mfdev.candysms.model.LogEntry


class LogActivity : AppCompatActivity() {
    companion object {
        var logAdapter: LogAdapter? = null
        private val logListType = object : TypeToken<List<LogEntry>>() {}.type
    }
    private val APP_PREFERENCE = "candy_sms_pref"
    private val LOG_KEY = "log_messages"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private val gson = Gson()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = "Logs"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        sharedPreferences =
            getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
        logRecyclerView = findViewById(R.id.logRecycleView)
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        val savedLogs = loadLogs()
        logAdapter = LogAdapter(savedLogs)

        val dividerItemDecoration =
            DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        val dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider)
        dividerItemDecoration.setDrawable(dividerDrawable!!)
        logRecyclerView.addItemDecoration(dividerItemDecoration)

        logRecyclerView.adapter = logAdapter
        LogActivity.logAdapter = logAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_log -> {
                clearLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadLogs(): List<LogEntry> {
        val json = sharedPreferences.getString(LOG_KEY, null)
        return if (json != null) {
            gson.fromJson(json, logListType) ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun clearLogs() {
        AppController.clearLog()
        logAdapter.updateLogs(emptyList())
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
    }
}
