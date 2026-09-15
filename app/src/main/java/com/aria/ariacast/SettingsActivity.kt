package com.aria.ariacast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private var packetLogClickCount = 0
    private var lastClickTime: Long = 0
    private lateinit var themeStatusText: TextView
    private lateinit var languageStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferences = getSharedPreferences(AudioCastService.PREFS_NAME, Context.MODE_PRIVATE)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        themeStatusText = findViewById(R.id.themeStatusText)
        languageStatusText = findViewById(R.id.languageStatusText)

        findViewById<View>(R.id.themeLayout).setOnClickListener {
            showThemeSelectionDialog()
        }


        findViewById<View>(R.id.languageLayout).setOnClickListener {
            showLanguageSelectionDialog()
        }


        findViewById<View>(R.id.protocolsLayout).setOnClickListener {
            startActivity(Intent(this, ProtocolsActivity::class.java))
        }

        findViewById<View>(R.id.airplayPinsLayout).setOnClickListener {
            startActivity(Intent(this, AirPlayPinsActivity::class.java))
        }

        findViewById<View>(R.id.writeNfcTagLayout).setOnClickListener {
            startActivity(Intent(this, NfcWriteActivity::class.java))
        }

        findViewById<View>(R.id.notificationAccessLayout).setOnClickListener {
            showNotificationAccessExplanationDialog()
        }


        findViewById<View>(R.id.packetLogLayout).setOnClickListener {
            startActivity(Intent(this, PacketLogActivity::class.java))
        }

        findViewById<View>(R.id.resetServerLayout).setOnClickListener {
            resetLastServer()
        }

        findViewById<View>(R.id.resetServerLayout).setOnLongClickListener {
            handlePacketLogClick()
            true
        }




        updateThemeStatusText()
        updateLanguageStatusText()
    }


    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_follow_system)
        )
        val sharedPreferences = getSharedPreferences(AudioCastService.PREFS_NAME, Context.MODE_PRIVATE)
        val currentTheme = sharedPreferences.getInt(KEY_THEME, ThemeUtils.MODE_NIGHT_FOLLOW_SYSTEM)

        val checkedItem = when(currentTheme) {
            ThemeUtils.MODE_NIGHT_NO -> 0
            ThemeUtils.MODE_NIGHT_YES -> 1
            else -> 2
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_appearance))
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selectedTheme = when(which) {
                    0 -> ThemeUtils.MODE_NIGHT_NO
                    1 -> ThemeUtils.MODE_NIGHT_YES
                    else -> ThemeUtils.MODE_NIGHT_FOLLOW_SYSTEM
                }

                sharedPreferences.edit().putInt(KEY_THEME, selectedTheme).apply()
                ThemeUtils.applyTheme(selectedTheme)
                updateThemeStatusText()
                dialog.dismiss()
            }
            .show()
    }


    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(
            getString(R.string.language_default),
            "English",
            "Deutsch",
            "Español",
            "Français",
            "Italiano",
            "Nederlands",
            "Polski",
            "தமிழ்",
            "日本語",
            "简体中文"
        )
        val codes = arrayOf("", "en", "de", "es", "fr", "it", "nl", "pl", "ta", "ja", "zh")

        val currentLocaleCode = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: ""
        val checkedItem = codes.indexOf(currentLocaleCode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedCode = codes[which]
                val appLocale: LocaleListCompat = if (selectedCode.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(selectedCode)
                }
                AppCompatDelegate.setApplicationLocales(appLocale)
                dialog.dismiss()
            }
            .show()
    }

    private fun updateThemeStatusText() {
        val sharedPreferences = getSharedPreferences(AudioCastService.PREFS_NAME, Context.MODE_PRIVATE)
        val currentTheme = sharedPreferences.getInt(KEY_THEME, ThemeUtils.MODE_NIGHT_FOLLOW_SYSTEM)
        themeStatusText.text = when(currentTheme) {
            ThemeUtils.MODE_NIGHT_NO -> getString(R.string.theme_light)
            ThemeUtils.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_follow_system)
        }
    }


    private fun updateLanguageStatusText() {
        val currentLocaleCode = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: ""
        languageStatusText.text = when(currentLocaleCode) {
            "en" -> "English"
            "de" -> "Deutsch"
            "es" -> "Español"
            "fr" -> "Français"
            "it" -> "Italiano"
            "nl" -> "Nederlands"
            "pl" -> "Polski"
            "ta" -> "தமிழ்"
            "ja" -> "日本語"
            "zh" -> "简体中文"
            else -> getString(R.string.language_default)
        }
    }

    private fun handlePacketLogClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 2000) {
            packetLogClickCount = 1
        } else {
            packetLogClickCount++
        }
        lastClickTime = currentTime

        if (packetLogClickCount >= 3) {
            packetLogClickCount = 0
            startActivity(Intent(this, PacketLogActivity::class.java))
        } else {
            val remaining = 3 - packetLogClickCount
            val message = if (remaining == 1) {
                getString(R.string.secret_logs_one_tap)
            } else {
                getString(R.string.secret_logs_multiple_taps, remaining)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetLastServer() {
        val sharedPreferences = getSharedPreferences(AudioCastService.PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            remove(AudioCastService.KEY_LAST_SERVER_HOST)
            remove(AudioCastService.KEY_LAST_SERVER_PORT)
            remove(AudioCastService.KEY_LAST_SERVER_NAME)
            apply()
        }
        Toast.makeText(this, getString(R.string.server_cleared), Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val KEY_THEME = "prefs_theme"
    }
}
