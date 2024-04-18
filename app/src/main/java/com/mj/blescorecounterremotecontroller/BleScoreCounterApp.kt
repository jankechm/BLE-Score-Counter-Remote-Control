package com.mj.blescorecounterremotecontroller

import android.app.Application
import android.content.Context

class BleScoreCounterApp : Application() {
    companion object {
        private const val PREFS_NAME = "Prefs"
        private const val PREF_LAST_DEVICE_ADDRESS = "lastDeviceAddress"
    }

    fun saveLastDeviceAddress(deviceAddress: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_LAST_DEVICE_ADDRESS, deviceAddress)
            apply()
        }
    }

    fun getLastDeviceAddress(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LAST_DEVICE_ADDRESS, null)
    }
}