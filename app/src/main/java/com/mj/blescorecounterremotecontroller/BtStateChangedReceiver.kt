package com.mj.blescorecounterremotecontroller

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast


class BtStateChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO
        if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            Log.i(Constants.BT_TAG, "Bluetooth state changed")

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    Toast.makeText(context, "Bluetooth is off", Toast.LENGTH_LONG).show()
                    Log.i(Constants.BT_TAG, "Bluetooth is off")
                }

                BluetoothAdapter.STATE_ON -> {
                    Toast.makeText(context, "Bluetooth is on", Toast.LENGTH_LONG).show()
                    Log.i(Constants.BT_TAG, "Bluetooth is on")
                }
            }
        }
    }
}