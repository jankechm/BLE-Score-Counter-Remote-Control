/**
 * Based on https://github.com/PunchThrough/ble-starter-android
 */

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
import android.os.Handler
import android.os.Looper
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


object ConnectionManager {

    const val TIMEOUT_CONNECT_MS = 1500L

    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = ConcurrentHashMap.newKeySet()
    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt>()
    private val deviceConnectAttemptsMap = ConcurrentHashMap<BluetoothDevice, Int>()
    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    var pendingOperation: BleOperationType? = null
        private set

    private val handler: Handler = Handler(Looper.getMainLooper())


    fun findCharacteristic(
        device: BluetoothDevice,
        characteristicId: String
    ): BluetoothGattCharacteristic? =
        deviceGattMap[device]?.findCharacteristic(UUID.fromString(characteristicId))

    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() }.none { it?.equals(listener) == true }) {
            listeners.add(WeakReference(listener))
            listeners.removeIf { it.get() == null }

            Timber.d("Added a listener, ${listeners.size} listeners total")
        }
    }

    fun unregisterListener(listener: ConnectionEventListener) {
        listeners.removeIf { it.get() == listener || it.get() == null }
        Timber.d("Removed a listener, ${listeners.size} listeners total")
    }

    fun connect(device: BluetoothDevice, context: Context) {
        if (device.isConnected()) {
            Timber.w("Already connected to ${device.address}!")
        } else {
            enqueueOperation(Connect(device, context.applicationContext))
        }
    }

    fun teardownConnection(device: BluetoothDevice) {
        if (device.isConnected()) {
            enqueueOperation(Disconnect(device))
        } else {
            Timber.w("Not connected to ${device.address}, " +
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
            Timber.e("Not connected to ${device.address}, " +
                    "cannot request MTU update!")
        }
    }

    fun readCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() && characteristic.isReadable()) {
            enqueueOperation(CharacteristicRead(device, characteristic.uuid))
        } else if (!characteristic.isReadable()) {
            Timber.e("Attempting to read ${characteristic.uuid} " +
                    "that isn't readable!")
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, " +
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
                Timber.e("Characteristic ${characteristic.uuid} " +
                        "cannot be written to!")
                return
            }
        }
        if (device.isConnected()) {
            enqueueOperation(CharacteristicWrite(device, characteristic.uuid, writeType, payload))
        } else {
            Timber.e("Not connected to ${device.address}, " +
                    "cannot perform characteristic write!")
        }
    }

    fun readDescriptor(device: BluetoothDevice, descriptor: BluetoothGattDescriptor) {
        if (device.isConnected() && descriptor.isReadable()) {
            enqueueOperation(DescriptorRead(device, descriptor.uuid))
        } else if (!descriptor.isReadable()) {
            Timber.e("Attempting to read ${descriptor.uuid} that isn't readable!")
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, " +
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
            Timber.e("Not connected to ${device.address}, " +
                    "cannot perform descriptor write!")
        } else if (!descriptor.isWritable() && !descriptor.isCccd()) {
            Timber.e("Descriptor ${descriptor.uuid} cannot be written to!")
        }
    }

    fun enableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() &&
            (characteristic.isIndicatable() || characteristic.isNotifiable())
        ) {
            enqueueOperation(EnableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, " +
                    "cannot enable notifications!")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Timber.e("Characteristic ${characteristic.uuid} " +
                    "doesn't support notifications/indications!")
        }
    }

    fun disableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() &&
            (characteristic.isIndicatable() || characteristic.isNotifiable())
        ) {
            enqueueOperation(DisableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, " +
                    "cannot disable notifications!")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Timber.e("Characteristic ${characteristic.uuid} " +
                    "doesn't support notifications/indications!")
        }
    }

    @Synchronized
    private fun enqueueOperation(operation: BleOperationType) {
        if (operationQueue.size < Constants.MAX_OPS_QUEUE_SIZE) {
            if (!(pendingOperation is Connect && operation is Connect)) {
                operationQueue.add(operation)
            }
            if (pendingOperation == null) {
                doNextOperation()
            }
        } else {
            Timber.i("Queue is full! Last operation not enqueued!")
            if (pendingOperation is Connect) {
                Timber.i("Cancelling pending Connect operation!")
                signalEndOfOperation()
            }
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        Timber.i("End of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            doNextOperation()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            Timber.e("doNextOperation() called when an operation is pending! " +
                    "Aborting.")
            return
        }

        val operation = operationQueue.poll() ?: run {
            Timber.i("Operation queue empty, returning")
            return
        }
        pendingOperation = operation

        // Handle Connect separately from other operations that require device to be connected
        if (operation is Connect) {
            with(operation) {
                Timber.i("Connecting to ${device.address}")
                cancelConnectAfterTimeout(operation)
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            }
            return
        }

        // Check BluetoothGatt availability for other operations
        val gatt = deviceGattMap[operation.device]
            ?: this@ConnectionManager.run {
                Timber.e("Not connected to ${operation.device.address}! " +
                        "Aborting $operation operation.")
                signalEndOfOperation()
                return
            }

        when (operation) {
            is Disconnect -> with(operation) {
                Timber.i("Disconnecting from ${device.address}")
                gatt.disconnect()
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
                    Timber.e("Cannot find $characteristicUuid to write to")
                    signalEndOfOperation()
                }
            }
            is CharacteristicRead -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    gatt.readCharacteristic(characteristic)
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $characteristicUuid to read from")
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
                    Timber.e("Cannot find $descriptorUuid to write to")
                    signalEndOfOperation()
                }
            }
            is DescriptorRead -> with(operation) {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    gatt.readDescriptor(descriptor)
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $descriptorUuid to read from")
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
                            Timber.e("setCharacteristicNotification failed " +
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
                        Timber.e("${characteristic.uuid} doesn't contain " +
                                "the CCC descriptor!")
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $characteristicUuid! " +
                            "Failed to enable notifications.")
                    signalEndOfOperation()
                }
            }
            is DisableNotifications -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val cccdUuid = UUID.fromString(Constants.CCC_DESCRIPTOR_UUID)
                    characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, false)) {
                            Timber.e("setCharacteristicNotification failed " +
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
                        Timber.e("${characteristic.uuid} doesn't contain " +
                                "the CCC descriptor!")
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $characteristicUuid! " +
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
                        Timber.i("onConnectionStateChange: connected " +
                                "to $deviceAddress")
                        deviceGattMap[device] = gatt
                        deviceConnectAttemptsMap.remove(device)
                        listeners.forEach { it.get()?.onConnect?.invoke(device) }
                        handler.post {
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Timber.i("onConnectionStateChange: disconnected " +
                                "from $deviceAddress")
                        teardownConnection(device)
                    }
                }
                // Requires pairing/bonding
                BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> {
                    device.createBond()
                    signalEndOfOperation()
                }
                // Random, sporadic errors
                133, 128 -> {
                    if (pendingOperation is Connect) {
                        val operation = pendingOperation as Connect
                        var connectAttempt = deviceConnectAttemptsMap[device] ?: 0
                        if (connectAttempt < Constants.MAX_CONNECT_ATTEMPTS) {
                            // Retry to connect
                            connectAttempt++
                            Timber.e("Connect operation was not successful " +
                                    "for $deviceAddress, trying again. Attempt #$connectAttempt")
                            deviceConnectAttemptsMap[device] = connectAttempt
                            enqueueOperation(operation)
                        }
                        else {
                            Timber.e("Max connect attempts reached " +
                                    "for $deviceAddress, giving up :(")
                            deviceConnectAttemptsMap.remove(device)
                        }
                    }
                    else {
                        teardownConnection(device)
                    }
                    signalEndOfOperation()
                }
                else -> {
                    Timber.e("onConnectionStateChange: status $status " +
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
                    Timber.i("Discovered ${services.size} services " +
                            "for ${device.address}.")
                    printGattTable()
                    requestMtu(device, Constants.GATT_CUSTOM_MTU_SIZE)
                    listeners.forEach { it.get()?.onServicesDiscovered?.invoke(this) }
                } else {
                    Timber.e("Service discovery failed due to status $status")
                    teardownConnection(device)
                }
            }

            if (pendingOperation is Connect) {
                signalEndOfOperation()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.i("ATT MTU changed to $mtu, " +
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
                        Timber.i("Read characteristic $uuid | " +
                                "value: ${value.toHexString()}")
                        listeners.forEach {
                            it.get()?.onCharacteristicRead?.invoke(gatt.device, this)
                        }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Timber.e("Read not permitted for $uuid!")
                    }
                    else -> {
                        Timber.e("Characteristic read failed for $uuid, " +
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
                        Timber.i("Wrote to characteristic $uuid | " +
                                "value: ${value?.toHexString()}")
                        listeners.forEach {
                            it.get()?.onCharacteristicWrite?.invoke(gatt.device, this)
                        }
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Timber.e("Write not permitted for $uuid!")
                    }
                    else -> {
                        Timber.e("Characteristic write failed for $uuid, " +
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
                Timber.i("Characteristic $uuid changed | " +
                        "value: ${value.toHexString()}")
                listeners.forEach { it.get()?.onCharacteristicChanged?.invoke(
                    gatt.device, this, value
                ) }
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
                        Timber.i("Read descriptor $uuid | " +
                                "value: ${value.toHexString()}")
                        listeners.forEach { it.get()?.onDescriptorRead?.invoke(gatt.device, this) }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Timber.e("Read not permitted for $uuid!")
                    }
                    else -> {
                        Timber.e("Descriptor read failed for $uuid, " +
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
                        Timber.i("Wrote to descriptor $uuid")

                        if (isCccd() &&
                            (pendingOperation is EnableNotifications ||
                                    pendingOperation is DisableNotifications)) {
                            onCccdWrite(gatt, characteristic, pendingOperation)
//                            listeners.forEach {
//                                it.get()?.onCCCDWrite?.invoke(gatt.device, this)
//                            }
                        } else {
                            listeners.forEach {
                                it.get()?.onDescriptorWrite?.invoke(gatt.device, this)
                            }
                        }
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Timber.e("Write not permitted for $uuid!")
                    }
                    else -> {
                        Timber.e("Descriptor write failed for $uuid, " +
                                "error: $status")
                    }
                }
            }

            val isNotificationsOperation = descriptor.isCccd() &&
                    (pendingOperation is EnableNotifications ||
                            pendingOperation is DisableNotifications)
            val isManualWriteOperation = !descriptor.isCccd() &&
                    pendingOperation is DescriptorWrite
            if (isNotificationsOperation || isManualWriteOperation) {
                signalEndOfOperation()
            }
        }

        private fun onCccdWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            operationType: BleOperationType?
        ) {
            val charUuid = characteristic.uuid

            when (operationType) {
                is EnableNotifications -> {
                    Timber.i("Notifications or indications ENABLED on $charUuid")
                    listeners.forEach {
                        it.get()?.onNotificationsEnabled?.invoke(
                            gatt.device,
                            characteristic
                        )
                    }
                }
                is DisableNotifications -> {
                    Timber.i("Notifications or indications DISABLED on $charUuid")
                    listeners.forEach {
                        it.get()?.onNotificationsDisabled?.invoke(
                            gatt.device,
                            characteristic
                        )
                    }
                }
                else -> {
                    Timber.e(
                        "Unexpected operation type of $operationType on CCCD of $charUuid")
                }
            }
        }
    }

    fun BluetoothDevice.isConnected() = deviceGattMap.containsKey(this)

    /**
     * Cancel Connect operation after a specified timeout.
     * It runs a coroutine.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun cancelConnectAfterTimeout(operation: BleOperationType) {
        if (operation is Connect) {
            GlobalScope.launch(Dispatchers.IO) {
                delay(TIMEOUT_CONNECT_MS)
                if (pendingOperation == operation) {
                    Timber.i("Cancelling pending Connect operation after timeout!")
                    signalEndOfOperation()
                }
            }
        }
    }

}