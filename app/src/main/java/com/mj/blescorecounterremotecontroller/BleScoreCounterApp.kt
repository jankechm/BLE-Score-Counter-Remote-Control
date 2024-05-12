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
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mj.blescorecounterremotecontroller.ble.Connect
import com.mj.blescorecounterremotecontroller.ble.ConnectionManager
import com.mj.blescorecounterremotecontroller.ble.ConnectionManager.isConnected
import com.mj.blescorecounterremotecontroller.broadcastreceiver.BtStateChangedReceiver
import com.mj.blescorecounterremotecontroller.data.manager.AppCfgManager
import com.mj.blescorecounterremotecontroller.listener.BtBroadcastListener
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener
import com.mj.blescorecounterremotecontroller.service.BleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BleScoreCounterApp : Application() {
    companion object {
        const val PREFS_NAME = "Prefs"
        const val PREF_LAST_DEVICE_ADDRESS = "lastDeviceAddress"
    }

    enum class ReconnectionType {
        PERSISTED_DEVICE,
        LAST_DEVICE
    }

    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothManager.adapter
    }

    private val applicationScope = CoroutineScope(SupervisorJob())

    var bleDisplay: BluetoothDevice? = null
    var writableDisplayChar: BluetoothGattCharacteristic? = null

    var manuallyDisconnected = false
    var isShuttingDown = false
    var shouldTryConnect = false
    private var isSomeConnectionCoroutineRunning = false

    private val btStateChangedReceiver = BtStateChangedReceiver()

    private val handler: Handler = Handler(Looper.getMainLooper())


    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onMtuChanged = { btDevice, _ ->
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



                // TODO start only if btDevice is a smartwatch
                val intent = Intent(this@BleScoreCounterApp, BleService::class.java)
                startForegroundService(intent)

            }
            onNotificationsEnabled = { _,_ -> Timber.i( "Enabled notification") }
            onDisconnect = { bleDevice ->
                ConnectionManager.teardownConnection(bleDevice)

                writableDisplayChar = null

                if (!manuallyDisconnected && !isShuttingDown) {
                    startReconnectionCoroutine()
                }
                // TODO should stopService only when the bleDevice is a smartwatch?
                else {
//                    val intent = Intent(this@BleScoreCounterApp, BleService::class.java)
//                    stopService(intent)
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
                        ConnectionManager.pendingOperation !is Connect
                ) {
                    ConnectionManager.connect(bleDevice, this@BleScoreCounterApp)
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "(${element.fileName}:${element.lineNumber})#${element.methodName}"
                }
            })
        }

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


    private fun saveLastDeviceAddress(deviceAddress: String) {
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

    fun startConnectionToPersistedDeviceCoroutine() {
        if (isSomeConnectionCoroutineRunning) {
            Timber.i( "Some connection coroutine already running!")
            return
        }

        val lastDeviceAddress = getLastDeviceAddress()

        if (btAdapter != null && lastDeviceAddress != null) {
            val lastDevice: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                btAdapter!!.getRemoteLeDevice(lastDeviceAddress, BluetoothDevice.ADDRESS_TYPE_PUBLIC)
            } else {
                btAdapter!!.getRemoteDevice(lastDeviceAddress)
            }

            if (lastDevice != null) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                        lastDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    applicationScope.launch(Dispatchers.IO) {
                        tryConnect(lastDevice, ReconnectionType.PERSISTED_DEVICE)
                    }
                } else {
                    Timber.i( "Last BLE device was not bonded, auto-connection canceled!")
                }
            }
        }

        isSomeConnectionCoroutineRunning = false
    }

    fun startReconnectionCoroutine() {
        if (bleDisplay == null) {
            Timber.i( "BluetoothDevice is null!")
            return
        }

        applicationScope.launch(Dispatchers.IO) {
            tryConnect(bleDisplay!!, ReconnectionType.LAST_DEVICE)
        }
    }

    private suspend fun tryConnect(bleDevice: BluetoothDevice, reconnectionType: ReconnectionType) {
        if (isSomeConnectionCoroutineRunning) {
            Timber.i( "Some connection coroutine already running!")
            return
        }

        val maxImmediateRetries = 3
        val initialDelayMillis = 100L
        val connectionDelayMillis = 2_000L
        val retryDelayMillis = 24_000L

        var connectionAttempts = 0

        isSomeConnectionCoroutineRunning = true
        shouldTryConnect = true

        delay(initialDelayMillis)
        while (shouldTryConnect && btAdapter != null && btAdapter!!.isEnabled && !isShuttingDown) {
            if (ConnectionManager.pendingOperation !is Connect) {
                ConnectionManager.connect(bleDevice, this@BleScoreCounterApp)
            }
            connectionAttempts++
            delay(connectionDelayMillis)

            if (bleDevice.isConnected()) {
                if (reconnectionType == ReconnectionType.PERSISTED_DEVICE) {
                    Timber.i( "Auto-connection to persisted device " +
                            "${bleDevice.address} successful!")
                } else {
                    Timber.i( "Reconnected to last device ${bleDevice.address}!")
                }
                break
            }

            if (connectionAttempts % maxImmediateRetries == 0) {
                delay(retryDelayMillis)
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun hasNotificationsPermission(): Boolean =
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    fun requestBtPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            Constants.BT_PERMISSIONS_REQUEST_CODE
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestNotificationsPermission(activity: Activity) {
        ActivityCompat.requestPermissions(activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            Constants.NOTIFICATIONS_PERMISSIONS_REQUEST_CODE)
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

        Timber.i(Constants.SET_TIME_CMD_PREFIX + currDateTime.format(formatter))
        ConnectionManager.writeCharacteristic(
            btDevice, characteristic,
            (Constants.SET_TIME_CMD_PREFIX + currDateTime.format(formatter) +
                    Constants.CRLF).
            toByteArray(Charsets.US_ASCII)
        )
    }
}