package com.mfdev.candysms.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.mfdev.candysms.BuildConfig
import com.mfdev.candysms.R
import com.mfdev.candysms.fragment.SecretKeyDialogFragment
import java.util.Locale

import kotlin.text.format

class SettingActivity: AppCompatActivity(), SecretKeyDialogFragment.OnSecretKeyUpdatedListener {
    private val APP_PREFERENCE = "candy_sms_pref"

    private lateinit var deviceIdBox: LinearLayout
    private lateinit var secretKeyBox: LinearLayout

    private lateinit var versionTextView: TextView
    private lateinit var deviceIdTextView: TextView
    private lateinit var secretKeyTextView: TextView

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = "Setting"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        deviceIdBox = findViewById(R.id.deviceIdBox)
        secretKeyBox = findViewById(R.id.secretKeyBox)
        versionTextView = findViewById(R.id.versionTextView)
        deviceIdTextView = findViewById(R.id.deviceIdTextView)
        secretKeyTextView = findViewById(R.id.secretKeyTextView)

        val versionString = String.format(
            Locale.getDefault(),
            "Version: %s",
            BuildConfig.VERSION_NAME
        )
        versionTextView.text = versionString

        sharedPreferences =
            getSharedPreferences(APP_PREFERENCE, Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("device_id", null)
        deviceIdTextView.text = deviceId
        deviceIdBox.setOnClickListener {
            copyDeviceIdToClipboard()
        }

        updateSecretKeyTextView()
        secretKeyBox.setOnClickListener {
            showSecretKeyDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.setting, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> {
                val intent = Intent(this, LogActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSecretKeyUpdated() {
        updateSecretKeyTextView()
        Toast.makeText(this, "Secret Key has been updated.", Toast.LENGTH_SHORT).show()
    }

    private fun updateSecretKeyTextView() {
        val secretKey = sharedPreferences.getString("secret_key", null)
        if (secretKey != null) {
            secretKeyTextView.text = "********"
        } else {
            secretKeyTextView.text = "Click to set"
        }
    }

    private fun copyDeviceIdToClipboard() {
        val deviceId = sharedPreferences.getString("device_id", null)
        if (deviceId != null) {
            val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
            val clip = ClipData.newPlainText("Device ID", deviceId)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Device ID copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Device ID not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSecretKeyDialog() {
        val dialog = SecretKeyDialogFragment()
        dialog.setOnSecretKeyUpdatedListener(this)
        dialog.show(supportFragmentManager, "SecretKeyDialog")
    }
}