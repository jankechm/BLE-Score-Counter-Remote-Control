package com.mj.blescorecounterremotecontroller

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log

class BLECentral {

    private val scanResults = mutableListOf<ScanResult>()

    // TODO add filtering on name
    private val scanFilter = ScanFilter.Builder()
        .build()

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        // TODO Enable after testing W/ and W/O
//        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .build()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (scanResults.none { it.device.address == result.device.address
                        // TODO remove the name comparison, when filtering on name applied
                        && it.device.name == result.device.name
                }) {
                with(result.device) {
                    var msg = "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address, UUIDS:"
                    // TODO Maybe change uuids to result.scanRecord.serviceUuids
                    uuids.forEachIndexed { i, parcelUUid ->
                        msg += "\n${i+1}: ${parcelUUid.uuid}"
                    }
                    Log.i(Constants.BT_TAG, msg)
                }
                scanResults.add(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(Constants.BT_TAG, "onScanFailed: code $errorCode")
        }
    }
}