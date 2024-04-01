package com.mj.blescorecounterremotecontroller.view

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.mj.blescorecounterremotecontroller.ConnectionManager
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.databinding.ActivityConfigurationBinding
import com.mj.blescorecounterremotecontroller.viewmodel.ConfigViewModel

class ConfigurationActivity : AppCompatActivity() {

    private lateinit var  activityBinding: ActivityConfigurationBinding
    private var bleDisplay: BluetoothDevice? = null
    private var writableDisplayChar: BluetoothGattCharacteristic? = null

    private val configViewModel: ConfigViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.activityBinding = ActivityConfigurationBinding.inflate(layoutInflater)

        val view = activityBinding.root
        setContentView(view)

        bleDisplay = intent.getParcelableExtra(Constants.PARCELABLE_BLE_DISPLAY)
        if (bleDisplay != null) {
            writableDisplayChar = ConnectionManager.findCharacteristic(
                bleDisplay!!, Constants.DISPLAY_WRITABLE_CHARACTERISTIC_UUID
            )
        }

        activityBinding.brightnessSlider.addOnChangeListener { slider, value, fromUser ->
            val intVal = value.toInt()
            configViewModel.config.value.brightness = intVal

            if (bleDisplay != null && writableDisplayChar != null) {
                ConnectionManager.writeCharacteristic(
                    bleDisplay!!, writableDisplayChar!!,
                    (Constants.SET_BRIGHTNESS_CMD_PREFIX + intVal + Constants.CRLF).
                    toByteArray(Charsets.US_ASCII)
                )
            }
        }

        activityBinding.saveCfgBtn.setOnClickListener {
            // TODO Persist config in non-volatile memory
            this.finish()
        }
    }
}