package com.mj.blescorecounterremotecontroller.broadcastreceiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.listener.BtBroadcastListener
import com.mj.blescorecounterremotecontroller.toBondStateDescription
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap


class BtStateChangedReceiver : BroadcastReceiver() {

    private var listeners: MutableSet<WeakReference<BtBroadcastListener>> = ConcurrentHashMap.newKeySet()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            Log.i(Constants.BT_TAG, "Bluetooth state changed")

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.i(Constants.BT_TAG, "Bluetooth is off")

                    listeners.forEach { it.get()?.onBluetoothOff?.invoke() }
                }

                BluetoothAdapter.STATE_ON -> {
                    Log.i(Constants.BT_TAG, "Bluetooth is on")

                    listeners.forEach { it.get()?.onBluetoothOn?.invoke() }
                }
            }
        }
        else if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            val device = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            }
            else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
            }
            val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            val bondTransition = "${previousBondState.toBondStateDescription()} to " +
                    bondState.toBondStateDescription()
            Log.i(Constants.BT_TAG, "${device?.address} bond state changed | $bondTransition")

            if (bondState != BluetoothDevice.BOND_BONDING) {
                // Invoke callback only if "bonded" or "not bonded". Miss "bonding" state.
                listeners.forEach { it.get()?.onBondStateChanged?.invoke(bondState, device) }
            }
        }
    }

    fun registerListener(listener: BtBroadcastListener) {
        if (listeners.map { it.get() }.none { it?.equals(listener) == true }) {
            listeners.add(WeakReference(listener))
            listeners.removeIf { it.get() == null }

            Log.d(Constants.BT_TAG, "Added a BtBroadcastListener, " +
                    "${listeners.size} listeners total")
        }
    }

    fun unregisterListener(listener: BtBroadcastListener) {
        listeners.removeIf { it.get() == listener || it.get() == null }
        Log.d(Constants.BT_TAG, "Removed a BtBroadcastListener, " +
                "${listeners.size} listeners total")
    }
}