package com.mj.blescorecounterremotecontroller

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.mj.blescorecounterremotecontroller.ConnectionManager.isConnected
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BleScoreCounterApp : Application() {
    companion object {
        const val PREFS_NAME = "Prefs"
        const val PREF_LAST_DEVICE_ADDRESS = "lastDeviceAddress"
        const val maxRetries = 3
        const val connectionDelayMillis = 2_000L
        const val retryDelayMillis = 24_000L
    }

    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothManager.adapter
    }

    var shouldTryConnect = false

    private var isSomeConnectionCoroutineRunning = false

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

    @OptIn(DelicateCoroutinesApi::class)
    fun startConnectionToLastDeviceCoroutine() {
        if (isSomeConnectionCoroutineRunning) {
            Log.i(Constants.BT_TAG, "Some connection coroutine already running!")
            return
        }

        isSomeConnectionCoroutineRunning = true

        val lastDeviceAddress = getLastDeviceAddress()

        var connectionAttempts = 0

        shouldTryConnect = true

        if (btAdapter != null && lastDeviceAddress != null) {
            val lastDevice = btAdapter!!.getRemoteDevice(lastDeviceAddress)

            if (lastDevice != null) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                        lastDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    GlobalScope.launch(Dispatchers.IO) {
                        while (shouldTryConnect && btAdapter!!.isEnabled) {
                            ConnectionManager.connect(lastDevice, this@BleScoreCounterApp)
                            connectionAttempts++
                            delay(connectionDelayMillis)

                            if (lastDevice.isConnected()) {
                                Log.i(Constants.BT_TAG, "Auto-connection to " +
                                        "${lastDevice.address} successful!")
                                break
                            }

                            if (connectionAttempts % maxRetries == 0) {
                                delay(retryDelayMillis)
                            }
                        }
                    }
                } else {
                    Log.i(Constants.BT_TAG, "Last BLE device was not bonded, " +
                            "auto-connection canceled!")
                }
            }
        }

        isSomeConnectionCoroutineRunning = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startReconnectionCoroutine(bleDevice: BluetoothDevice) {
        if (isSomeConnectionCoroutineRunning) {
            Log.i(Constants.BT_TAG, "Some connection coroutine already running!")
            return
        }

        isSomeConnectionCoroutineRunning = true

        shouldTryConnect = true

        var connectionAttempts = 0

        GlobalScope.launch(Dispatchers.IO) {
            while (shouldTryConnect && btAdapter != null && btAdapter!!.isEnabled) {
                ConnectionManager.connect(bleDevice, this@BleScoreCounterApp)
                connectionAttempts++
                delay(connectionDelayMillis)

                if (bleDevice.isConnected()) {
                    Log.i(Constants.BT_TAG, "Reconnected to ${bleDevice.address}")
                    break
                }

                if (connectionAttempts % maxRetries == 0) {
                    delay(retryDelayMillis)
                }
            }
        }

        isSomeConnectionCoroutineRunning = false
    }

}