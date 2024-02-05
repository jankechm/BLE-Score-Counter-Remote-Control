package com.mj.blescorecounterremotecontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


/**
 * A simple [Fragment] subclass.
 * Use the [BluetoothFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 * It is supposed to be used only from [MainActivity]!
 */
class BluetoothFragment : DialogFragment(), BtDeviceClickListener {

    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager? = context?.getSystemService(BluetoothManager::class.java)
        bluetoothManager?.adapter
    }

    private val scanResults = mutableListOf<ScanResult>()

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var scanBtn: Button
    private lateinit var connectBtn: Button
    private lateinit var disconnectBtn: Button
    private lateinit var foundDevices: RecyclerView

    private lateinit var btDevicesAdapter: BtDevicesAdapter

//    private var mainActivity: MainActivity? = null

    private var alreadyConnected: Boolean = false
    private var deviceToConnectIdx: Int = -1

    /**
     * Do not set this property before the view is created!
     */
    private var isScanning = false
        set(value) {
            field = value
            scanBtn.text = if (value) "Stop Scan" else "Start Scan"
        }

    // TODO add filtering on name
    private val scanFilter = ScanFilter.Builder()
        .build()

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        // TODO Enable after testing W/ and W/O
//        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .build()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (scanResults.none { it.device.address == result.device.address
                        // TODO remove the name comparison, when filtering on name applied
                        && it.device.name == result.device.name
                }) {
                with(result.device) {
                    var msg = "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address"
                    // TODO Maybe change uuids to result.scanRecord.serviceUuids
                    uuids?.let {
                        msg += ", UUIDS:"
                        it.forEachIndexed { i, parcelUuid ->
                            msg += ", UUIDS:\n${i+1}: ${parcelUuid.uuid}"
                        }
                    }

                    Log.i(Constants.BT_TAG, msg)
                }
                scanResults.add(result)

                val devicesNames = scanResults.map { res -> res.device.name ?: "" }
                val devicesAddresses = scanResults.map { res -> res.device.address }

                btDevicesAdapter = BtDevicesAdapter(devicesNames, devicesAddresses,
                    this@BluetoothFragment)
                foundDevices.layoutManager = LinearLayoutManager(context)
                foundDevices.adapter = btDevicesAdapter

            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(Constants.BT_TAG, "onScanFailed: code $errorCode")
        }
    }


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
        foundDevices = view.findViewById(R.id.found_devices_view)

//        mainActivity = this.activity as MainActivity

        this.registerForActivityResult()

        // The Connect button should be only visible when a device is selected
        connectBtn.visibility = View.INVISIBLE

        if (this.alreadyConnected) {
            disconnectBtn.visibility = View.VISIBLE
        }

//        foundDevices.layoutManager = LinearLayoutManager(context)

        scanBtn.setOnClickListener {
            if (!this.isScanning) {
                if (this.btAdapter != null) {
                    connectBtn.visibility = View.INVISIBLE

                    if (this.btAdapter!!.isEnabled) {
                        this.runScan()
                    }
                    else {
                        this.promptEnableBluetooth()
                    }
                }
                else {
                    Log.i(Constants.BT_TAG, "BluetoothAdapter is null")
                }
            }
            else {
                this.isScanning = false
                this.stopBleScan()
            }
        }

        connectBtn.setOnClickListener {
            // TODO
            var deviceToConnect = this.scanResults[this.deviceToConnectIdx].device

            dialog?.dismiss()
        }

        disconnectBtn.setOnClickListener {
            // TODO

            dialog?.dismiss()
        }

        return view
    }

    private fun runScan() {
        Handler(Looper.getMainLooper()).postDelayed({
            this.isScanning = false
            this.stopBleScan()
        }, Constants.SCAN_PERIOD)

        this.isScanning = true
        this.startBleScan()
    }

//    override fun onDestroy() {
//        super.onDestroy()
//
//        mainActivity = null
//    }

    /**
     * It is assumed that the required permissions are already granted and bluetooth is enabled
     * before calling this method.
     */
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        scanResults.clear()
        // TODO
//        scanResultAdapter.notifyDataSetChanged()
        if (btAdapter != null) {
            btAdapter!!.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        }
        else {
            Log.i(Constants.BT_TAG, "BluetoothAdapter is null!")
        }
    }

    /**
     * It is assumed that the required permissions are already granted and bluetooth is enabled
     * before calling this method.
     */
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (btAdapter != null) {
            btAdapter!!.bluetoothLeScanner.stopScan(scanCallback)
        }
    }

    private fun registerForActivityResult() {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                Log.i(Constants.BT_TAG, "Enable Bluetooth activity result OK")
                this.runScan()
            } else {
                Log.i(Constants.BT_TAG, "Enable Bluetooth activity result DENIED")
                Toast.makeText(context, "Bluetooth was not enabled!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptEnableBluetooth() {
        if (!this.btAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            this.activityResultLauncher.launch(enableBtIntent)
        }
    }

    override fun onBtDeviceClicked(position: Int) {
        this.connectBtn.visibility = View.VISIBLE

        this.deviceToConnectIdx = position
    }
}