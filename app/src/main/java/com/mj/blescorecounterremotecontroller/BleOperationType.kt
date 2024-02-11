package com.mj.blescorecounterremotecontroller

import android.bluetooth.BluetoothDevice
import android.content.Context

sealed class BleOperationType {
    abstract val device: BluetoothDevice
}

data class Connect(override val device: BluetoothDevice, val context: Context) : BleOperationType()

data class Disconnect(override val device: BluetoothDevice) : BleOperationType()

data class MtuRequest(override val device: BluetoothDevice, val mtu: Int) : BleOperationType()