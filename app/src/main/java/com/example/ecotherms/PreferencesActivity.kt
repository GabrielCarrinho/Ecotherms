package com.example.ecotherms

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat

class PreferencesActivity : BaseActivity() {

    companion object {
        const val ACTION_TEMP_UNIT_CHANGED = "com.example.ecotherms.TEMP_UNIT_CHANGED"
        const val EXTRA_USE_FAHRENHEIT     = "use_fahrenheit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        val swNotifications = findViewById<SwitchCompat>(R.id.sw_notifications)
        val swFahrenheit    = findViewById<SwitchCompat>(R.id.sw_fahrenheit)

        swNotifications.isChecked = prefs.getBoolean("notifications", true)
        swFahrenheit.isChecked    = prefs.getBoolean("fahrenheit", false)

        swNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }

        swFahrenheit.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("fahrenheit", isChecked).apply()

            // Broadcast para todas as activities ouvindo
            val intent = Intent(ACTION_TEMP_UNIT_CHANGED).apply {
                putExtra(EXTRA_USE_FAHRENHEIT, isChecked)
                setPackage(packageName) // segurança: só para o próprio app
            }
            sendBroadcast(intent)
        }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }
    }
}