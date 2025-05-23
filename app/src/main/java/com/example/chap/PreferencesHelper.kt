package com.example.chap

import android.content.Context

object PreferencesHelper {
    private const val PREFS_NAME = "EqualizerPrefs"
    private const val KEY_BASS_BOOST = "bass_boost"
    private const val KEY_BAND1 = "band1"
    private const val KEY_BAND2 = "band2"
    private const val KEY_BAND3 = "band3"
    private const val KEY_BAND4 = "band4"
    private const val KEY_BAND5 = "band5"

    fun saveBassBoost(context: Context, value: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_BASS_BOOST, value).apply()
    }

    fun getBassBoost(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_BASS_BOOST, 0f)
    }

    fun saveBandLevel(context: Context, band: String, level: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(band, level).apply()
    }

    fun getBandLevel(context: Context, band: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(band, 0)
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}