package com.mj.blescorecounterremotecontroller

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt

/** A listener containing callback methods to be registered with [ConnectionManager].*/
class ConnectionEventListener {
    var onConnect: ((BluetoothDevice) -> Unit)? = null
    var onDisconnect: ((BluetoothDevice) -> Unit)? = null
    var onServicesDiscovered: ((BluetoothGatt) -> Unit)? = null
    var onMtuChanged: ((BluetoothDevice, Int) -> Unit)? = null
}