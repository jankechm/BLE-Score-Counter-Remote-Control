package com.mj.blescorecounterremotecontroller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment


const val SCAN_PERIOD: Long = 7000

/**
 * A simple [Fragment] subclass.
 * Use the [BluetoothFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 * It is supposed to be used only from [MainActivity]!
 */
class BluetoothFragment : DialogFragment() {

    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager? = context?.getSystemService(BluetoothManager::class.java)
        bluetoothManager?.adapter
    }

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var scanBtn: Button
    private lateinit var connectBtn: Button
    private lateinit var disconnectBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var foundDevices: TextView

//    private var mainActivity: MainActivity? = null

    private var alreadyConnected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            alreadyConnected = it.getBoolean(Constants.ALREADY_CONNECTED_PARAM)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val view: View = inflater.inflate(R.layout.fragment_bluetooth, container, false)

        scanBtn = view.findViewById(R.id.scan_btn)
        connectBtn = view.findViewById(R.id.connect_btn)
        disconnectBtn = view.findViewById(R.id.disconnect_btn)
        cancelBtn = view.findViewById(R.id.cancel_btn)
        foundDevices = view.findViewById(R.id.found_devices_textview)

//        mainActivity = this.activity as MainActivity

        this.registerForActivityResult()

        if (this.alreadyConnected) {
            connectBtn.isVisible = false
            disconnectBtn.isVisible = true
        }

        scanBtn.setOnClickListener {
            // TODO
            this.promptEnableBluetooth()
        }

        connectBtn.setOnClickListener {
            // TODO
            if (this.alreadyConnected) {

            }
            else {

            }

            dialog?.dismiss()
        }

        cancelBtn.setOnClickListener { dialog?.dismiss() }

        foundDevices.setOnClickListener {
            // TODO
        }

        return view
    }

//    override fun onDestroy() {
//        super.onDestroy()
//
//        mainActivity = null
//    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment BluetoothFragment.
         */
        @JvmStatic
        fun newInstance(alreadyConnected: Boolean) =
            BluetoothFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(Constants.ALREADY_CONNECTED_PARAM, alreadyConnected)
                }
            }
    }

    private fun registerForActivityResult() {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                Log.i(Constants.BT_TAG, "Enable Bluetooth activity result OK")
            } else {
                Log.i(Constants.BT_TAG, "Enable Bluetooth activity result DENIED")
            }
        }
    }

    private fun promptEnableBluetooth() {
        if (this.btAdapter == null) {
            Log.i(Constants.BT_TAG, "BluetoothAdapter is null")
        }
        else {
            if (!this.btAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                this.activityResultLauncher.launch(enableBtIntent)
            }
        }
    }
}