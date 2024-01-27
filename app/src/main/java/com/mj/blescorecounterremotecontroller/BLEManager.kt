package com.mj.blescorecounterremotecontroller

import android.content.Context

interface BLEManager {
    fun log(msg: String) : Unit
    fun onConnectionStateChange(newState: Int) : Unit
    fun getContext() : Context
    fun enableBluetooth() : Unit
    fun onConnected() : Unit
    fun onDisconnected() : Unit
    fun checkPermission() : Unit
}