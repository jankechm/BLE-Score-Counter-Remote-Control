package com.mj.blescorecounterremotecontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


object ConnectionManager {

    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = ConcurrentHashMap.newKeySet()
    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt>()
    private val deviceConnectAttemptsMap = ConcurrentHashMap<BluetoothDevice, Int>()
    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    private var pendingOperation: BleOperationType? = null


    fun findCharacteristic(
        device: BluetoothDevice,
        characteristicId: String
    ): BluetoothGattCharacteristic? =
        deviceGattMap[device]?.findCharacteristic(UUID.fromString(characteristicId))

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
        if (device.isConnected()) {
            Log.w(Constants.BT_TAG,"Already connected to ${device.address}!")
        } else {
            enqueueOperation(Connect(device, context.applicationContext))
        }
    }

    fun teardownConnection(device: BluetoothDevice) {
        if (device.isConnected()) {
            enqueueOperation(Disconnect(device))
        } else {
            Log.w(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot teardown connection!")
        }
    }

    fun disconnectAllDevices() {
        val disconnectOps = deviceGattMap.keys.map { Disconnect(it) }.toList()
        disconnectOps.forEach { enqueueOperation(it) }
    }

    fun requestMtu(device: BluetoothDevice, mtu: Int) {
        if (device.isConnected()) {
            enqueueOperation(MtuRequest(device,
                mtu.coerceIn(Constants.GATT_MIN_MTU_SIZE, Constants.GATT_MAX_MTU_SIZE)))
        } else {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot request MTU update!")
        }
    }

    fun readCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() && characteristic.isReadable()) {
            enqueueOperation(CharacteristicRead(device, characteristic.uuid))
        } else if (!characteristic.isReadable()) {
            Log.e(Constants.BT_TAG,"Attempting to read ${characteristic.uuid} " +
                    "that isn't readable!")
        } else if (!device.isConnected()) {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot perform characteristic read!")
        }
    }

    fun writeCharacteristic(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> {
                Log.e(Constants.BT_TAG,"Characteristic ${characteristic.uuid} " +
                        "cannot be written to!")
                return
            }
        }
        if (device.isConnected()) {
            enqueueOperation(CharacteristicWrite(device, characteristic.uuid, writeType, payload))
        } else {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot perform characteristic write!")
        }
    }

    fun readDescriptor(device: BluetoothDevice, descriptor: BluetoothGattDescriptor) {
        if (device.isConnected() && descriptor.isReadable()) {
            enqueueOperation(DescriptorRead(device, descriptor.uuid))
        } else if (!descriptor.isReadable()) {
            Log.e(Constants.BT_TAG,"Attempting to read ${descriptor.uuid} that isn't readable!")
        } else if (!device.isConnected()) {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot perform descriptor read!")
        }
    }

    fun writeDescriptor(
        device: BluetoothDevice,
        descriptor: BluetoothGattDescriptor,
        payload: ByteArray
    ) {
        if (device.isConnected() && (descriptor.isWritable() || descriptor.isCccd())) {
            enqueueOperation(DescriptorWrite(device, descriptor.uuid, payload))
        } else if (!device.isConnected()) {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot perform descriptor write!")
        } else if (!descriptor.isWritable() && !descriptor.isCccd()) {
            Log.e(Constants.BT_TAG,"Descriptor ${descriptor.uuid} cannot be written to!")
        }
    }

    fun enableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() &&
            (characteristic.isIndicatable() || characteristic.isNotifiable())
        ) {
            enqueueOperation(EnableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot enable notifications!")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Log.e(Constants.BT_TAG,"Characteristic ${characteristic.uuid} " +
                    "doesn't support notifications/indications!")
        }
    }

    fun disableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() &&
            (characteristic.isIndicatable() || characteristic.isNotifiable())
        ) {
            enqueueOperation(DisableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Log.e(Constants.BT_TAG,"Not connected to ${device.address}, " +
                    "cannot disable notifications!")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Log.e(Constants.BT_TAG,"Characteristic ${characteristic.uuid} " +
                    "doesn't support notifications/indications!")
        }
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
            Log.e(Constants.BT_TAG, "doNextOperation() called when an operation is pending! " +
                    "Aborting.")
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
            is CharacteristicWrite -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        characteristic.writeType = writeType
                        characteristic.value = payload
                        gatt.writeCharacteristic(characteristic)
                    }
                    else {
                        gatt.writeCharacteristic(characteristic, payload, writeType)
                    }
                } ?: this@ConnectionManager.run {
                    Log.e(Constants.BT_TAG,"Cannot find $characteristicUuid to write to")
                    signalEndOfOperation()
                }
            }
            is CharacteristicRead -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    gatt.readCharacteristic(characteristic)
                } ?: this@ConnectionManager.run {
                    Log.e(Constants.BT_TAG,"Cannot find $characteristicUuid to read from")
                    signalEndOfOperation()
                }
            }
            is DescriptorWrite -> with(operation) {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        descriptor.value = payload
                        gatt.writeDescriptor(descriptor)
                    }
                    else {
                        gatt.writeDescriptor(descriptor, payload)
                    }
                } ?: this@ConnectionManager.run {
                    Log.e(Constants.BT_TAG,"Cannot find $descriptorUuid to write to")
                    signalEndOfOperation()
                }
            }
            is DescriptorRead -> with(operation) {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    gatt.readDescriptor(descriptor)
                } ?: this@ConnectionManager.run {
                    Log.e(Constants.BT_TAG,"Cannot find $descriptorUuid to read from")
                    signalEndOfOperation()
                }
            }
            is EnableNotifications -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val cccdUuid = UUID.fromString(Constants.CCC_DESCRIPTOR_UUID)
                    val payload = when {
                        characteristic.isIndicatable() ->
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        characteristic.isNotifiable() ->
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else ->
                            error("${characteristic.uuid} doesn't support notifications/indications")
                    }

                    characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, true)) {
                            Log.e(Constants.BT_TAG,"setCharacteristicNotification failed " +
                                    "for ${characteristic.uuid}")
                            signalEndOfOperation()
                            return
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            cccDescriptor.value = payload
                            gatt.writeDescriptor(cccDescriptor)
                        }
                        else {
                            gatt.writeDescriptor(cccDescriptor, payload)
                        }
                    } ?: this@ConnectionManager.run {
                        Log.e(Constants.BT_TAG,"${characteristic.uuid} doesn't contain " +
                                "the CCC descriptor!")
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run {
                    Log.e(Constants.BT_TAG,"Cannot find $characteristicUuid! " +
                            "Failed to enable notifications.")
                    signalEndOfOperation()
                }
            }
            is DisableNotifications -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val cccdUuid = UUID.fromString(Constants.CCC_DESCRIPTOR_UUID)
                    characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, false)) {
                            Log.e(Constants.BT_TAG,"setCharacteristicNotification failed " +
                                    "for ${characteristic.uuid}")
                            signalEndOfOperation()
                            return
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(cccDescriptor)
                        }
                        else {
                            gatt.writeDescriptor(cccDescriptor,
                                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                        }
                    } ?: this@ConnectionManager.run {
                        Log.e(Constants.BT_TAG,"${characteristic.uuid} doesn't contain " +
                                "the CCC descriptor!")
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run {
                    Log.e(Constants.BT_TAG,"Cannot find $characteristicUuid! " +
                            "Failed to disable notifications.")
                    signalEndOfOperation()
                }
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
                        // TODO test calling it not in UI thread few times.
//                        Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices()
//                        }
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
                        val operation = pendingOperation as Connect
                        var connectAttempt = deviceConnectAttemptsMap[device] ?: 0
                        if (connectAttempt < Constants.MAX_CONNECT_ATTEMPTS) {
                            // Retry to connect
                            connectAttempt++
                            Log.e(Constants.BT_TAG, "Connect operation was not successful " +
                                    "for $deviceAddress, trying again. Attempt #$connectAttempt")
                            deviceConnectAttemptsMap[device] = connectAttempt
                            enqueueOperation(operation)
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
                    requestMtu(device, Constants.GATT_CUSTOM_MTU_SIZE)
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

        @Deprecated("Deprecated in Java API 33",
            ReplaceWith("onCharacteristicRead(gatt, characteristic, value, status)")
        )
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            this.onCharacteristicRead(gatt,characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(Constants.BT_TAG,"Read characteristic $uuid | " +
                                "value: ${value.toHexString()}")
                        listeners.forEach {
                            it.get()?.onCharacteristicRead?.invoke(gatt.device, this)
                        }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e(Constants.BT_TAG,"Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(Constants.BT_TAG,"Characteristic read failed for $uuid, " +
                                "error: $status")
                    }
                }
            }

            if (pendingOperation is CharacteristicRead) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(Constants.BT_TAG,"Wrote to characteristic $uuid | " +
                                "value: ${value?.toHexString()}")
                        listeners.forEach {
                            it.get()?.onCharacteristicWrite?.invoke(gatt.device, this)
                        }
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e(Constants.BT_TAG,"Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(Constants.BT_TAG,"Characteristic write failed for $uuid, " +
                                "error: $status")
                    }
                }
            }

            if (pendingOperation is CharacteristicWrite) {
                signalEndOfOperation()
            }
        }

        @Deprecated("Deprecated in Java API 33",
            ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)")
        )
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            this.onCharacteristicChanged(gatt, characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            with(characteristic) {
                Log.i(Constants.BT_TAG,"Characteristic $uuid changed | " +
                        "value: ${value.toHexString()}")
                listeners.forEach { it.get()?.onCharacteristicChanged?.invoke(gatt.device, this) }
            }
        }

        @Deprecated("Deprecated in Java API 33",
            ReplaceWith("onDescriptorRead(gatt, descriptor, status, value)")
        )
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            this.onDescriptorRead(gatt, descriptor, status, descriptor.value)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            with(descriptor) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(Constants.BT_TAG,"Read descriptor $uuid | " +
                                "value: ${value.toHexString()}")
                        listeners.forEach { it.get()?.onDescriptorRead?.invoke(gatt.device, this) }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e(Constants.BT_TAG,"Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(Constants.BT_TAG,"Descriptor read failed for $uuid, " +
                                "error: $status")
                    }
                }
            }

            if (pendingOperation is DescriptorRead) {
                signalEndOfOperation()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            with(descriptor) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(Constants.BT_TAG,"Wrote to descriptor $uuid | " +
                                "value: ${value.toHexString()}")

                        if (isCccd()) {
                            onCccdWrite(gatt, value, characteristic)
                        } else {
                            listeners.forEach {
                                it.get()?.onDescriptorWrite?.invoke(gatt.device, this)
                            }
                        }
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e(Constants.BT_TAG,"Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(Constants.BT_TAG,"Descriptor write failed for $uuid, " +
                                "error: $status")
                    }
                }
            }

            if (descriptor.isCccd() &&
                (pendingOperation is EnableNotifications ||
                        pendingOperation is DisableNotifications)
            ) {
                signalEndOfOperation()
            } else if (!descriptor.isCccd() && pendingOperation is DescriptorWrite) {
                signalEndOfOperation()
            }
        }

        private fun onCccdWrite(
            gatt: BluetoothGatt,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic
        ) {
            val charUuid = characteristic.uuid
            val notificationsEnabled =
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            val notificationsDisabled =
                value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

            when {
                notificationsEnabled -> {
                    Log.w(Constants.BT_TAG,"Notifications or indications ENABLED on $charUuid")
                    listeners.forEach {
                        it.get()?.onNotificationsEnabled?.invoke(gatt.device, characteristic)
                    }
                }
                notificationsDisabled -> {
                    Log.w(Constants.BT_TAG,
                        "Notifications or indications DISABLED on $charUuid")
                    listeners.forEach {
                        it.get()?.onNotificationsDisabled?.invoke(gatt.device, characteristic)
                    }
                }
                else -> {
                    Log.e(Constants.BT_TAG,
                        "Unexpected value ${value.toHexString()} on CCCD of $charUuid")
                }
            }
        }
    }

    private fun BluetoothDevice.isConnected() = deviceGattMap.containsKey(this)
}