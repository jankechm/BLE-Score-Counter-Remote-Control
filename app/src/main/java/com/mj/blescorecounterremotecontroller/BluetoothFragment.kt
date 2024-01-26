package com.mj.blescorecounterremotecontroller

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment


const val SCAN_PERIOD: Long = 7000

/**
 * A simple [Fragment] subclass.
 * Use the [BluetoothFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class BluetoothFragment : DialogFragment() {

    private lateinit var scanBtn: Button
    private lateinit var connectDisconnectBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var foundDevices: TextView

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
        connectDisconnectBtn = view.findViewById(R.id.connect_disconnect_btn)
        cancelBtn = view.findViewById(R.id.cancel_btn)
        foundDevices = view.findViewById(R.id.found_devices_textview)

        if (this.alreadyConnected) {
            connectDisconnectBtn.text = R.string.disconnect_btn_text.toString()
        }

        scanBtn.setOnClickListener {
            // TODO
        }

        connectDisconnectBtn.setOnClickListener {
            // TODO
            if (this.alreadyConnected) {

            }
            else {

            }
        }

        cancelBtn.setOnClickListener { dialog!!.dismiss() }

        foundDevices.setOnClickListener {
            // TODO
        }

        return view
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
}