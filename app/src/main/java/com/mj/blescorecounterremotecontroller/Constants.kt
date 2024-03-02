package com.mj.blescorecounterremotecontroller

class Constants {
    companion object {
        const val BT_TAG = "Bluetooth"

        const val BT_PERMISSIONS_REQUEST_CODE = 1

        const val ALREADY_CONNECTED_PARAM = "already_connected"

        const val SCAN_PERIOD: Long = 7000

        const val MAX_CONNECT_ATTEMPTS = 4

        /** UUID of the Client Characteristic Configuration Descriptor (0x2902). */
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"
        const val DISPLAY_SERVICE_3_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val DISPLAY_CHARACTERISTIC_TO_WRITE_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"

        const val GATT_MIN_MTU_SIZE = 23
        const val GATT_MAX_MTU_SIZE = 517
        const val GATT_CUSTOM_MTU_SIZE = 46

        const val SET_BOTH_SCORE_CMD_PREFIX = "SET_SCORE="
    }
}