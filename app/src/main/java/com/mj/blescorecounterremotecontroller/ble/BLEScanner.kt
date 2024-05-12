package com.mj.blescorecounterremotecontroller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.widget.Toast
import com.mj.blescorecounterremotecontroller.Constants
import timber.log.Timber

class BLEScanner(private var btAdapter: BluetoothAdapter?, private var scanCallback: ScanCallback) {

    private val scanFilter = ScanFilter.Builder()
        .setDeviceName(Constants.BLE_DISPLAY_NAME)
        .build()

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .build()

    /**
     * It is assumed that the required permissions are already granted and bluetooth is enabled
     * before calling this method.
     */
    @SuppressLint("MissingPermission", "NotifyDataSetChanged")
    fun startBleScan(context: Context?) {
        if (btAdapter != null) {
            btAdapter!!.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        }
        else {
            Timber.i("startBleScan(): BluetoothAdapter is null!")
            context?.let {
                Toast.makeText(it, "Can't access Bluetooth Adapter! Is Bluetooth on?",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * It is assumed that the required permissions are already granted and bluetooth is enabled
     * before calling this method.
     */
    @SuppressLint("MissingPermission")
    fun stopBleScan(context: Context?) {
        if (btAdapter != null) {
            btAdapter!!.bluetoothLeScanner?.stopScan(scanCallback)
        }
        else {
            Timber.i("stopBleScan(): BluetoothAdapter is null!")
            context?.let {
                Toast.makeText(it, "Can't access Bluetooth Adapter! Is Bluetooth on?",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}