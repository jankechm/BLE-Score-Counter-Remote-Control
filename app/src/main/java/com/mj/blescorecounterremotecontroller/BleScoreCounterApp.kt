package com.mj.blescorecounterremotecontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mj.blescorecounterremotecontroller.ConnectionManager.isConnected
import com.mj.blescorecounterremotecontroller.broadcastreceiver.BtStateChangedReceiver
import com.mj.blescorecounterremotecontroller.listener.BtBroadcastListener
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BleScoreCounterApp : Application() {
    companion object {
        const val PREFS_NAME = "Prefs"
        const val PREF_LAST_DEVICE_ADDRESS = "lastDeviceAddress"
        const val maxRetries = 3
        const val initialDelayMillis = 100L
        const val connectionDelayMillis = 2_000L
        const val retryDelayMillis = 24_000L
    }

    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothManager.adapter
    }

    var bleDisplay: BluetoothDevice? = null
    var writableDisplayChar: BluetoothGattCharacteristic? = null

    var manuallyDisconnected = false
    var shouldTryConnect = false

    private var isSomeConnectionCoroutineRunning = false

    private val btStateChangedReceiver = BtStateChangedReceiver()

    private val handler: Handler = Handler(Looper.getMainLooper())


    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onMtuChanged = { btDevice, mtu ->
                bleDisplay = btDevice
                writableDisplayChar = ConnectionManager.findCharacteristic(
                    btDevice, Constants.DISPLAY_WRITABLE_CHARACTERISTIC_UUID
                )

                handleBonding(btDevice)

                writableDisplayChar?.let {
                    ConnectionManager.enableNotifications(btDevice, it)
                    sendDayTime(btDevice, it)
                }

                saveLastDeviceAddress(btDevice.address)

                handler.post {
                    Toast.makeText(applicationContext,
                        "Connected to ${btDevice.address}", Toast.LENGTH_SHORT).show()
                }

                manuallyDisconnected = false
                shouldTryConnect = false
            }
            onNotificationsEnabled = { _,_ -> Log.i(Constants.BT_TAG, "Enabled notification") }
            onDisconnect = { bleDevice ->
                ConnectionManager.teardownConnection(bleDevice)

                writableDisplayChar = null

                if (!manuallyDisconnected) {
                    startReconnectionCoroutine()
                }

                handler.post {
                    Toast.makeText(this@BleScoreCounterApp,
                        "Disconnected from ${bleDevice.address}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val btBroadcastListener by lazy {
        BtBroadcastListener().apply {
            onBluetoothOff = {
                ConnectionManager.disconnectAllDevices()
            }
            onBluetoothOn = {
                if (!manuallyDisconnected) {
                    if (bleDisplay != null) {
                        startReconnectionCoroutine()
                    } else {
                        startConnectionToPersistedDeviceCoroutine()
                    }
                }
            }
            onBondStateChanged = { bondState, bleDevice ->
                if (bondState == BluetoothDevice.BOND_BONDED && bleDevice != null &&
                        !bleDevice.isConnected() &&
                        ConnectionManager.pendingOperation !is Connect) {
                    ConnectionManager.connect(bleDevice, this@BleScoreCounterApp)
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }

        ConnectionManager.registerListener(connectionEventListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.registerReceiver(btStateChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            this.registerReceiver(btStateChangedReceiver, filter)
        }

        btStateChangedReceiver.registerListener(btBroadcastListener)
    }


    fun saveLastDeviceAddress(deviceAddress: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_LAST_DEVICE_ADDRESS, deviceAddress)
            apply()
        }
    }

    private fun getLastDeviceAddress(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LAST_DEVICE_ADDRESS, null)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startConnectionToPersistedDeviceCoroutine() {
        if (isSomeConnectionCoroutineRunning) {
            Log.i(Constants.BT_TAG, "Some connection coroutine already running!")
            return
        }

        isSomeConnectionCoroutineRunning = true

        val lastDeviceAddress = getLastDeviceAddress()

        var connectionAttempts = 0

        shouldTryConnect = true

        if (btAdapter != null && lastDeviceAddress != null) {
            val lastDevice: BluetoothDevice? = btAdapter!!.getRemoteDevice(lastDeviceAddress)

            if (lastDevice != null) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                        lastDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    GlobalScope.launch(Dispatchers.IO) {
                        delay(initialDelayMillis)
                        while (shouldTryConnect && btAdapter!!.isEnabled) {
                            if (ConnectionManager.pendingOperation !is Connect) {
                                ConnectionManager.connect(lastDevice, this@BleScoreCounterApp)
                            }
                            connectionAttempts++
                            delay(connectionDelayMillis)

                            if (lastDevice.isConnected()) {
                                Log.i(Constants.BT_TAG, "Auto-connection to " +
                                        "${lastDevice.address} successful!")
                                break
                            }

                            if (connectionAttempts % maxRetries == 0) {
                                delay(retryDelayMillis)
                            }
                        }
                    }
                } else {
                    Log.i(Constants.BT_TAG, "Last BLE device was not bonded, " +
                            "auto-connection canceled!")
                }
            }
        }

        isSomeConnectionCoroutineRunning = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startReconnectionCoroutine() {
        if (isSomeConnectionCoroutineRunning) {
            Log.i(Constants.BT_TAG, "Some connection coroutine already running!")
            return
        }
        if (bleDisplay == null) {
            Log.i(Constants.BT_TAG, "BluetoothDevice is null!")
            return
        }

        isSomeConnectionCoroutineRunning = true

        shouldTryConnect = true

        var connectionAttempts = 0

        GlobalScope.launch(Dispatchers.IO) {
            delay(initialDelayMillis)
            while (shouldTryConnect && btAdapter != null && btAdapter!!.isEnabled) {
                if (ConnectionManager.pendingOperation !is Connect) {
                    ConnectionManager.connect(bleDisplay!!, this@BleScoreCounterApp)
                }
                connectionAttempts++
                delay(connectionDelayMillis)

                if (bleDisplay!!.isConnected()) {
                    Log.i(Constants.BT_TAG, "Reconnected to ${bleDisplay!!.address}")
                    break
                }

                if (connectionAttempts % maxRetries == 0) {
                    delay(retryDelayMillis)
                }
            }
        }

        isSomeConnectionCoroutineRunning = false
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED

    fun hasBtPermissions(): Boolean =
        hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    fun requestBtPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            Constants.BT_PERMISSIONS_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    fun handleBonding(btDevice: BluetoothDevice) {
        if (AppCfgManager.appCfg.askToBond) {
            btDevice.createBond()
        }
    }

    /**
     * Send daytime to the BLE display
     */
    fun sendDayTime(btDevice: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        val currDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("e d.M.yy H:m:s")

        Log.i(Constants.BT_TAG,
            Constants.SET_TIME_CMD_PREFIX + currDateTime.format(formatter))
        ConnectionManager.writeCharacteristic(
            btDevice, characteristic,
            (Constants.SET_TIME_CMD_PREFIX + currDateTime.format(formatter) +
                    Constants.CRLF).
            toByteArray(Charsets.US_ASCII)
        )
    }
}