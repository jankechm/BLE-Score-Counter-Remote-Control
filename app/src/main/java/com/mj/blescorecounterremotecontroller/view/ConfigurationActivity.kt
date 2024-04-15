package com.mj.blescorecounterremotecontroller.view

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.mj.blescorecounterremotecontroller.ConnectionManager
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.databinding.ActivityConfigurationBinding
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener
import com.mj.blescorecounterremotecontroller.model.BleDisplayCfg
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ConfigurationActivity : AppCompatActivity() {

    private lateinit var  activityBinding: ActivityConfigurationBinding
    private var bleDisplay: BluetoothDevice? = null
    private var writableDisplayChar: BluetoothGattCharacteristic? = null

    private var msgBuffer: String = ""

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onCharacteristicChanged = { bleDevice, characteristic, value ->
                runOnUiThread {
//                    val valSize = value.size
//                    if (valSize > 2 && value[valSize-2].toInt() == '\r'.code &&
//                        value[valSize-1].toInt() == '\n'.code) {
//                        val decoded = value.copyOf(valSize - 2)
//                            .toString(Charsets.US_ASCII)

                    val decoded = value.toString(Charsets.US_ASCII)

//                    Log.d(Constants.BT_TAG, "Received: $decoded")
//                    Toast.makeText(this@ConfigurationActivity, "Received: $decoded",
//                        Toast.LENGTH_SHORT).show()

                    msgBuffer += decoded
                    val msgBufferLen = msgBuffer.length
                    if (msgBufferLen >= 2 && msgBuffer[msgBufferLen-2] == '\r' &&
                        msgBuffer[msgBufferLen-1] == '\n') {
                        // Full message received, process it
                        val msg = msgBuffer.removeSuffix(Constants.CRLF)
                        Log.d(Constants.BT_TAG, "Full message: $msg")
                        Snackbar.make(activityBinding.cfgActivityMainCl,
                            "Full msg: $msg", Snackbar.LENGTH_SHORT).show()

                        if (msg.startsWith(Constants.CONFIG_CMD_PREFIX)) {
                            val jsonStr = msg.removePrefix(Constants.CONFIG_CMD_PREFIX)
                            try {
                                val bleDisplayCfg = Json.decodeFromString<BleDisplayCfg>(jsonStr)

                                // Prevent calling the listeners and sending commands back
                                // to the BLE display.
                                activityBinding.showScoreSwitch.setOnCheckedChangeListener(null)
                                activityBinding.showDateSwitch.setOnCheckedChangeListener(null)
                                activityBinding.showTimeSwitch.setOnCheckedChangeListener(null)
                                activityBinding.scrollRb.setOnClickListener(null)
                                activityBinding.alternateRb.setOnClickListener(null)

                                // Update the UI.
                                activityBinding.brightnessSlider.value = bleDisplayCfg.brightness
                                    .toFloat()
                                activityBinding.showScoreSwitch.isChecked = bleDisplayCfg.useScore
                                activityBinding.showDateSwitch.isChecked = bleDisplayCfg.useDate
                                activityBinding.showTimeSwitch.isChecked = bleDisplayCfg.useTime
                                if (bleDisplayCfg.scroll) {
                                    activityBinding.scrollRb.isChecked = true
                                }
                                else {
                                    activityBinding.alternateRb.isChecked = true
                                }

                                // Activate the listeners again.
                                activityBinding.showScoreSwitch.setOnCheckedChangeListener(
                                    onShowScoreCheckedChangeListener)
                                activityBinding.showDateSwitch.setOnCheckedChangeListener(
                                    onShowDateCheckedChangeListener)
                                activityBinding.showTimeSwitch.setOnCheckedChangeListener(
                                    onShowTimeCheckedChangeListener)
                                activityBinding.scrollRb.setOnClickListener(onScrollClickedListener)
                                activityBinding.alternateRb.setOnClickListener(
                                    onAlternateClickedListener)
                            }
                            catch (ex: Exception) {
                                when (ex) {
                                    is SerializationException,
                                    is IllegalArgumentException -> {
                                        Log.e(Constants.BT_TAG, "Problem decoding JSON from " +
                                                Constants.CONFIG_CMD_PREFIX, ex
                                        )
                                    }
                                    else -> {
                                        Log.e(Constants.BT_TAG, "Problem with " +
                                                Constants.CONFIG_CMD_PREFIX, ex
                                        )
                                    }
                                }
                            }
                        }

                        // Reset the buffer
                        msgBuffer = ""
                    }


//                        when {
//                            decoded.startsWith(Constants.CONFIG_BRIGHTNESS_CMD_PREFIX) -> {
//                                val brightLvlStr = decoded.removePrefix(
//                                    Constants.CONFIG_BRIGHTNESS_CMD_PREFIX)
//                                try {
//                                    val brightLvlInt = brightLvlStr.toInt()
//                                    configViewModel.bleDisplayCfg.value.brightness = brightLvlInt
//                                    activityBinding.brightnessSlider.value = brightLvlInt.toFloat()
//                                }
//                                catch (ex: NumberFormatException) {
//                                    Log.e(Constants.BT_TAG, "Unable to parse float value for " +
//                                            "brightness level ", ex)
//                                }
//                            }
//
//                            decoded.startsWith(Constants.CONFIG_SHOW_SCORE_CMD_PREFIX) -> {
//                                val showScoreStr = decoded.removePrefix(
//                                    Constants.CONFIG_SHOW_SCORE_CMD_PREFIX)
//
//                                // Prevent calling the listener and sending a command
//                                // to the BLE display
//                                activityBinding.showScoreSwitch.setOnCheckedChangeListener(null)
//
//                                if (showScoreStr.contentEquals("1")) {
//                                    configViewModel.bleDisplayCfg.value.useScore = true
//                                    activityBinding.showScoreSwitch.isChecked = true
//                                }
//                                else if (showScoreStr.contentEquals("0")) {
//                                    configViewModel.bleDisplayCfg.value.useScore = false
//                                    activityBinding.showScoreSwitch.isChecked = false
//                                }
//                                activityBinding.showScoreSwitch.setOnCheckedChangeListener(
//                                    onShowScoreCheckedChangeListener)
//                            }
//
//                            decoded.startsWith(Constants.CONFIG_SHOW_DATE_CMD_PREFIX) -> {
//                                val showDateStr = decoded.removePrefix(
//                                    Constants.CONFIG_SHOW_DATE_CMD_PREFIX)
//
//                                // Prevent calling the listener and sending a command
//                                // to the BLE display
//                                activityBinding.showDateSwitch.setOnCheckedChangeListener(null)
//
//                                if (showDateStr.contentEquals("1")) {
//                                    configViewModel.bleDisplayCfg.value.useDate = true
//                                    activityBinding.showDateSwitch.isChecked = true
//                                }
//                                else if (showDateStr.contentEquals("0")) {
//                                    configViewModel.bleDisplayCfg.value.useDate = false
//                                    activityBinding.showDateSwitch.isChecked = false
//                                }
//
//                                activityBinding.showDateSwitch.setOnCheckedChangeListener(
//                                    onShowDateCheckedChangeListener)
//                            }
//
//                            decoded.startsWith(Constants.CONFIG_SHOW_TIME_CMD_PREFIX) -> {
//                                val showTimeStr = decoded.removePrefix(
//                                    Constants.CONFIG_SHOW_TIME_CMD_PREFIX)
//
//                                // Prevent calling the listener and sending a command
//                                // to the BLE display
//                                activityBinding.showTimeSwitch.setOnCheckedChangeListener(null)
//
//                                if (showTimeStr.contentEquals("1")) {
//                                    configViewModel.bleDisplayCfg.value.useTime = true
//                                    activityBinding.showTimeSwitch.isChecked = true
//                                }
//                                else if (showTimeStr.contentEquals("0")) {
//                                    configViewModel.bleDisplayCfg.value.useTime = false
//                                    activityBinding.showTimeSwitch.isChecked = false
//                                }
//
//                                activityBinding.showTimeSwitch.setOnCheckedChangeListener(
//                                    onShowTimeCheckedChangeListener)
//                            }
//
//                            decoded.startsWith(Constants.CONFIG_SCROLL_CMD_PREFIX) -> {
//                                val scrollStr = decoded.removePrefix(
//                                    Constants.CONFIG_SCROLL_CMD_PREFIX)
//
//                                if (scrollStr.contentEquals("1")) {
//                                    // Prevent calling the listener and sending a command
//                                    // to the BLE display
//                                    activityBinding.scrollRb.setOnClickListener(null)
//
//                                    configViewModel.bleDisplayCfg.value.scroll = true
//                                    activityBinding.scrollRb.isChecked = true
//
//                                    activityBinding.scrollRb.setOnClickListener(
//                                        onScrollClickedListener)
//                                }
//                                else if (scrollStr.contentEquals("0")) {
//                                    // Prevent calling the listener and sending a command
//                                    // to the BLE display
//                                    activityBinding.alternateRb.setOnClickListener(null)
//
//                                    configViewModel.bleDisplayCfg.value.scroll = false
//                                    activityBinding.alternateRb.isChecked = true
//
//                                    activityBinding.alternateRb.setOnClickListener(
//                                        onAlternateClickedListener)
//                                }
//                            }
//                        }
//                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.activityBinding = ActivityConfigurationBinding.inflate(layoutInflater)

        val view = activityBinding.root
        setContentView(view)

        ConnectionManager.registerListener(connectionEventListener)

        bleDisplay = intent.getParcelableExtra(Constants.PARCELABLE_BLE_DISPLAY)
        if (bleDisplay != null) {
            writableDisplayChar = ConnectionManager.findCharacteristic(
                bleDisplay!!, Constants.DISPLAY_WRITABLE_CHARACTERISTIC_UUID
            )
        }

        if (bleDisplay != null && writableDisplayChar != null) {
            ConnectionManager.writeCharacteristic(
                bleDisplay!!, writableDisplayChar!!,
                (Constants.GET_CONFIG_CMD + Constants.CRLF).toByteArray(Charsets.US_ASCII)
            )
        }

        activityBinding.brightnessSlider.addOnChangeListener { slider, value, fromUser ->
            // Send command through BLE only when the change was initiated by the user.
            // Do not send it if it was changed programmatically.
            if (fromUser) {
                if (bleDisplay != null && writableDisplayChar != null) {
                    ConnectionManager.writeCharacteristic(
                        bleDisplay!!, writableDisplayChar!!,
                        (Constants.SET_BRIGHTNESS_CMD_PREFIX + value.toInt() + Constants.CRLF).
                        toByteArray(Charsets.US_ASCII)
                    )
                }
            }
        }

        activityBinding.showScoreSwitch.setOnCheckedChangeListener(onShowScoreCheckedChangeListener)
        activityBinding.showDateSwitch.setOnCheckedChangeListener(onShowDateCheckedChangeListener)
        activityBinding.showTimeSwitch.setOnCheckedChangeListener(onShowTimeCheckedChangeListener)
        activityBinding.alternateRb.setOnClickListener(onAlternateClickedListener)
        activityBinding.scrollRb.setOnClickListener(onScrollClickedListener)

        activityBinding.persistCfgBtn.setOnClickListener {
            // TODO Persist config in non-volatile memory of the BLE display (flash memory)
            this.finish()
        }

        activityBinding.returnFromCfgBtn.setOnClickListener {
            this.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        ConnectionManager.unregisterListener(connectionEventListener)
    }

    private val onShowScoreCheckedChangeListener =
            CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (bleDisplay != null && writableDisplayChar != null) {
            val showScoreVal = if (isChecked) 1 else 0
            ConnectionManager.writeCharacteristic(bleDisplay!!, writableDisplayChar!!,
                (Constants.SET_SHOW_SCORE_CMD_PREFIX + showScoreVal + Constants.CRLF).toByteArray(
                    Charsets.US_ASCII
                )
            )
        }
    }

    private val onShowDateCheckedChangeListener =
            CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (bleDisplay != null && writableDisplayChar != null) {
            val showDateVal = if (isChecked) 1 else 0
            ConnectionManager.writeCharacteristic(
                bleDisplay!!, writableDisplayChar!!,
                (Constants.SET_SHOW_DATE_CMD_PREFIX + showDateVal + Constants.CRLF).
                toByteArray(Charsets.US_ASCII)
            )
        }
    }

    private val onShowTimeCheckedChangeListener =
            CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (bleDisplay != null && writableDisplayChar != null) {
            val showTimeVal = if (isChecked) 1 else 0
            ConnectionManager.writeCharacteristic(
                bleDisplay!!, writableDisplayChar!!,
                (Constants.SET_SHOW_TIME_CMD_PREFIX + showTimeVal + Constants.CRLF).
                toByteArray(Charsets.US_ASCII)
            )
        }
    }

    private val onAlternateClickedListener = View.OnClickListener {
        if (bleDisplay != null && writableDisplayChar != null) {
            ConnectionManager.writeCharacteristic(
                bleDisplay!!, writableDisplayChar!!,
                (Constants.SET_SCROLL_CMD_PREFIX + 0 + Constants.CRLF).toByteArray(Charsets.US_ASCII)
            )
        }
    }

    private val onScrollClickedListener = View.OnClickListener {
        if (bleDisplay != null && writableDisplayChar != null) {
            ConnectionManager.writeCharacteristic(
                bleDisplay!!, writableDisplayChar!!,
                (Constants.SET_SCROLL_CMD_PREFIX + 1 + Constants.CRLF).toByteArray(Charsets.US_ASCII)
            )
        }
    }
}