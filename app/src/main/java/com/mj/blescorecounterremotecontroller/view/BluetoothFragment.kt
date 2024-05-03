package com.mj.blescorecounterremotecontroller.view

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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
import com.mj.blescorecounterremotecontroller.BLEDevicesAdapter
import com.mj.blescorecounterremotecontroller.BLEScanner
import com.mj.blescorecounterremotecontroller.BleScoreCounterApp
import com.mj.blescorecounterremotecontroller.ConnectionManager
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.R
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener


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

    private val bleScanner: BLEScanner by lazy {
        BLEScanner(btAdapter, scanCallback)
    }

    private val bleDevicesAdapter: BLEDevicesAdapter by lazy {
        BLEDevicesAdapter(scanResults) { selectedResult ->
            selectedScanResult = selectedResult
            connectBtn.visibility = View.VISIBLE
        }
    }

    private val app: BleScoreCounterApp by lazy {
        activity?.application as BleScoreCounterApp
    }

    private val handler: Handler = Handler(Looper.getMainLooper())

    private val scanResults = mutableListOf<ScanResult>()

    private lateinit var enableBtActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var scanBtn: Button
    private lateinit var connectBtn: Button
    private lateinit var disconnectBtn: Button
    private lateinit var foundDevices: RecyclerView

//    private var mainActivity: MainActivity? = null

    private var alreadyConnected: Boolean = false
    private var selectedScanResult: ScanResult? = null

    /**
     * Do not set this property before the view is created!
     */
    private var isScanning = false
        set(value) {
            field = value
            scanBtn.text = if (value) "Stop Scan" else "Start Scan"
        }

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
                            msg += "\n${i+1}: ${parcelUuid.uuid}"
                        }
                    }

                    Log.i(Constants.BT_TAG, msg)
                }

                scanResults.add(result)
                bleDevicesAdapter.notifyItemChanged(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(Constants.BT_TAG, "onScanFailed: code $errorCode")
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnect = {
                handler.post {
                    dialog?.dismiss()
                }
            }
            onDisconnect = {
                handler.post {
                    dialog?.dismiss()
                }
            }
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

        this.enableBtRegisterForActivityResult()

        ConnectionManager.registerListener(connectionEventListener)

        // The Connect button should be only visible when a device is selected
        connectBtn.visibility = View.INVISIBLE

        if (this.alreadyConnected) {
            disconnectBtn.visibility = View.VISIBLE
        }
        else {
            disconnectBtn.visibility = View.INVISIBLE
        }

        foundDevices.layoutManager = LinearLayoutManager(context)
        foundDevices.adapter = bleDevicesAdapter

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
                    Log.e(Constants.BT_TAG, "Start Scan: BluetoothAdapter is null")
                }
            }
            else {
                this.isScanning = false
                bleScanner.stopBleScan(context)
            }
        }

        connectBtn.setOnClickListener {
            if (selectedScanResult != null) {
                this.isScanning = false
                bleScanner.stopBleScan(context)
                // TODO Allow only one connected device?
//                ConnectionManager.disconnectAllDevices()
                try {
                    ConnectionManager.connect(this.selectedScanResult!!.device, requireContext())
                } catch (e: IllegalStateException) {
                    Log.e(Constants.BT_TAG, "Missing context!", e)
                }
            }
            else {
                Log.w(Constants.BT_TAG, "Connect: ScanResult is null")
            }
        }

        disconnectBtn.setOnClickListener {
            this.isScanning = false
            bleScanner.stopBleScan(context)
            app.manuallyDisconnected = true
            if (app.bleDisplay != null) {
                ConnectionManager.teardownConnection(app.bleDisplay!!)
            }
        }

        return view
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        super.onDestroy()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun runScan() {
        handler.postDelayed({
            this.isScanning = false
            bleScanner.stopBleScan(context)
        }, Constants.SCAN_PERIOD)

        this.isScanning = true

        // The order of these calls is important
        scanResults.clear()
        bleDevicesAdapter.resetSelectedPosition()
        bleDevicesAdapter.notifyDataSetChanged()
        bleScanner.startBleScan(context)
    }

//    override fun onDestroy() {
//        super.onDestroy()
//
//        mainActivity = null
//    }

//    /**
//     * It is assumed that the required permissions are already granted and bluetooth is enabled
//     * before calling this method.
//     */
//    @SuppressLint("MissingPermission", "NotifyDataSetChanged")
//    private fun startBleScan() {
//        // The order of these calls is important
//        scanResults.clear()
//        bleDevicesAdapter.resetSelectedPosition()
//        bleDevicesAdapter.notifyDataSetChanged()
//
//        if (btAdapter != null) {
//            btAdapter!!.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
//        }
//        else {
//            Log.i(Constants.BT_TAG, "BluetoothAdapter is null!")
//        }
//    }
//
//    /**
//     * It is assumed that the required permissions are already granted and bluetooth is enabled
//     * before calling this method.
//     */
//    @SuppressLint("MissingPermission")
//    private fun stopBleScan() {
//        if (btAdapter != null) {
//            btAdapter!!.bluetoothLeScanner.stopScan(scanCallback)
//        }
//    }

    private fun enableBtRegisterForActivityResult() {
        this.enableBtActivityResultLauncher = registerForActivityResult(
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
            this.enableBtActivityResultLauncher.launch(enableBtIntent)
        }
    }
}