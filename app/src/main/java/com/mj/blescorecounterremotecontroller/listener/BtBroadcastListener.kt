package com.mj.blescorecounterremotecontroller.listener

import android.bluetooth.BluetoothDevice
import com.mj.blescorecounterremotecontroller.broadcastreceiver.BtStateChangedReceiver

/** A listener containing callback methods to be registered with [BtStateChangedReceiver].*/
class BtBroadcastListener {
    var onBluetoothOff: (() -> Unit)? = null
    var onBluetoothOn: (() -> Unit)? = null
    var onBondStateChanged: ((Int, BluetoothDevice?) -> Unit)? = null
}
