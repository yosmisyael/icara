package com.example.icara.ui.utils

import android.content.Context
import androidx.core.content.edit

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(
        "icara_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val TAG = "PreferencesManager"
    }

    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        return isFirst
    }

    fun markOnboardingComplete() {
        prefs.edit {
            putBoolean(KEY_FIRST_LAUNCH, false)
        }
    }
}