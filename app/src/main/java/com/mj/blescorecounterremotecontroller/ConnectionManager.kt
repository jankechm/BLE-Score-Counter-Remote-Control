package com.mj.blescorecounterremotecontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


private const val GATT_MIN_MTU_SIZE = 23
private const val GATT_MAX_MTU_SIZE = 517
private const val GATT_CUSTOM_MTU_SIZE = 46

object ConnectionManager {

    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = ConcurrentHashMap.newKeySet()
    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt>()
    private val deviceConnectAttemptsMap = ConcurrentHashMap<BluetoothDevice, Int>()
    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    private var pendingOperation: BleOperationType? = null


    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() }.none { it?.equals(listener) == true }) {
            listeners.add(WeakReference(listener))
            listeners.removeIf { it.get() == null }

            Log.d(Constants.BT_TAG, "Added a listener, ${listeners.size} listeners total")
        }
    }

    fun unregisterListener(listener: ConnectionEventListener) {
        listeners.removeIf { it.get() == listener || it.get() == null }
        Log.d(Constants.BT_TAG, "Removed a listener, ${listeners.size} listeners total")
    }

    fun connect(device: BluetoothDevice, context: Context) {
        enqueueOperation(Connect(device, context.applicationContext))
    }

    fun teardownConnection(device: BluetoothDevice) {
        enqueueOperation(Disconnect(device))
    }

    fun requestMtu(device: BluetoothDevice, mtu: Int) {
        enqueueOperation(MtuRequest(device, mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE)))
    }

    @Synchronized
    private fun enqueueOperation(operation: BleOperationType) {
        operationQueue.add(operation)
        if (pendingOperation == null) {
            doNextOperation()
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        Log.e(Constants.BT_TAG, "End of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            doNextOperation()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            Log.e(Constants.BT_TAG, "doNextOperation() called when an operation is pending! Aborting.")
            return
        }

        val operation = operationQueue.poll() ?: run {
            Log.i(Constants.BT_TAG, "Operation queue empty, returning")
            return
        }
        pendingOperation = operation

        // Handle Connect separately from other operations that require device to be connected
        if (operation is Connect) {
            with(operation) {
                Log.i(Constants.BT_TAG, "Connecting to ${device.address}")
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            }
            return
        }

        // Check BluetoothGatt availability for other operations
        val gatt = deviceGattMap[operation.device]
            ?: this@ConnectionManager.run {
                Log.e(Constants.BT_TAG, "Not connected to ${operation.device.address}! " +
                        "Aborting $operation operation.")
                signalEndOfOperation()
                return
            }

        when (operation) {
            is Disconnect -> with(operation) {
                Log.i(Constants.BT_TAG, "Disconnecting from ${device.address}")
                gatt.close()
                deviceGattMap.remove(device)
                listeners.forEach { it.get()?.onDisconnect?.invoke(device) }
                signalEndOfOperation()
            }

            is MtuRequest -> with(operation) {
                gatt.requestMtu(mtu)
            }

            else -> {}
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            val deviceAddress = device.address

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(Constants.BT_TAG, "onConnectionStateChange: connected " +
                                "to $deviceAddress")
                        deviceGattMap[device] = gatt
                        deviceConnectAttemptsMap.remove(device)
                        listeners.forEach { it.get()?.onConnect?.invoke(device) }
                        Handler(Looper.getMainLooper()).post {
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(Constants.BT_TAG, "onConnectionStateChange: disconnected " +
                                "from $deviceAddress")
                        teardownConnection(device)
                    }
                }
                BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> {
                    // TODO bond device first
                }
                // Random, sporadic errors
                133, 128 -> {
                    if (pendingOperation is Connect) {
                        var connectAttempt = deviceConnectAttemptsMap[device] ?: 0
                        if (connectAttempt < Constants.MAX_CONNECT_ATTEMPTS) {
                            // Retry to connect
                            connectAttempt++
                            Log.e(Constants.BT_TAG, "Connect operation was not successful " +
                                    "for $deviceAddress, trying again. Attempt #$connectAttempt")
                            deviceConnectAttemptsMap[device] = connectAttempt
                            enqueueOperation(pendingOperation as Connect)
                        }
                        else {
                            Log.e(Constants.BT_TAG, "Max connect attempts reached " +
                                    "for $deviceAddress, giving up :(")
                            deviceConnectAttemptsMap.remove(device)
                        }
                    }
                    signalEndOfOperation()
                }
                else -> {
                    Log.e(Constants.BT_TAG, "onConnectionStateChange: status $status " +
                            "encountered for $deviceAddress!")
                    if (pendingOperation is Connect) {
                        signalEndOfOperation()
                    }
                    teardownConnection(device)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(Constants.BT_TAG, "Discovered ${services.size} services " +
                            "for ${device.address}.")
                    printGattTable()
                    requestMtu(device, GATT_CUSTOM_MTU_SIZE)
                    listeners.forEach { it.get()?.onServicesDiscovered?.invoke(this) }
                } else {
                    Log.e(Constants.BT_TAG, "Service discovery failed due to status $status")
                    teardownConnection(device)
                }
            }

            if (pendingOperation is Connect) {
                signalEndOfOperation()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(Constants.BT_TAG, "ATT MTU changed to $mtu, " +
                    "success: ${status == BluetoothGatt.GATT_SUCCESS}")
            listeners.forEach { it.get()?.onMtuChanged?.invoke(gatt.device, mtu) }

            if (pendingOperation is MtuRequest) {
                signalEndOfOperation()
            }
        }
    }
}