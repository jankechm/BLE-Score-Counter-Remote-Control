package com.mj.blescorecounterremotecontroller.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mj.blescorecounterremotecontroller.ConnectionManager
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.R
import com.mj.blescorecounterremotecontroller.broadcastreceiver.BtStateChangedReceiver
import com.mj.blescorecounterremotecontroller.databinding.ActivityMainBinding
import com.mj.blescorecounterremotecontroller.listener.BtBroadcastListener
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener
import com.mj.blescorecounterremotecontroller.viewmodel.ScoreViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {
    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothManager.adapter
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onMtuChanged = { btDevice, mtu ->
                runOnUiThread {
                    val btMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.bluetooth_menu_item)
                    btMenuItem?.let {
                        it.iconTintList = ContextCompat.getColorStateList(
                            this@MainActivity, R.color.bt_connected
                        )
                        it.setIcon(R.drawable.bluetooth_connected)
                    }

                    bleDisplay = btDevice
                    writableDisplayChar = ConnectionManager.findCharacteristic(
                        btDevice, Constants.DISPLAY_WRITABLE_CHARACTERISTIC_UUID
                    )
                    writableDisplayChar?.let {
                        enablingNotification = true
                        ConnectionManager.enableNotifications(btDevice, it)
                    }

                    handleBondState()

                    // Send daytime to the BLE display
                    val currDateTime = LocalDateTime.now()
                    val formatter = DateTimeFormatter.ofPattern("e d.M.yy H:m:s")

                    Log.i(Constants.BT_TAG,
                        Constants.SET_TIME_CMD_PREFIX + currDateTime.format(formatter))
                    ConnectionManager.writeCharacteristic(
                        bleDisplay!!, writableDisplayChar!!,
                        (Constants.SET_TIME_CMD_PREFIX + currDateTime.format(formatter) +
                                Constants.CRLF).
                            toByteArray(Charsets.US_ASCII)
                    )

                    Toast.makeText(this@MainActivity,
                        "Connected to ${btDevice.address}", Toast.LENGTH_SHORT).show()
                }
            }
            onCCCDWrite = { btDevice, descriptor ->
                runOnUiThread {
                    if (enablingNotification) {
                        Log.i(Constants.BT_TAG, "Enabled notification")
                        enablingNotification = false
                    }
                }
            }
            onDisconnect = {
                runOnUiThread {
                    val btMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.bluetooth_menu_item)
                    btMenuItem?.let {
                        it.iconTintList = ContextCompat.getColorStateList(
                            this@MainActivity, R.color.bt_disconnected
                        )
                        it.setIcon(R.drawable.bluetooth_disabled)
                    }

                    val encryptionMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.encryption_menu_item)
                    encryptionMenuItem?.let {
                        it.isVisible = false
                    }

                    bleDisplay = null
                    writableDisplayChar = null

                    Toast.makeText(this@MainActivity, "Disconnected from ${it.address}",
                        Toast.LENGTH_SHORT).show()
                }
            }
            onCharacteristicWrite = { bleDevice, characteristic ->
                runOnUiThread {
                    mainBinding.okBtn.visibility = View.INVISIBLE
                }
            }
            onCharacteristicChanged = { bleDevice, characteristic, value ->
                runOnUiThread {
                    val valSize = value.size
                    if (valSize > 2 && value[valSize-2].toInt() == '\r'.code &&
                        value[valSize-1].toInt() == '\n'.code) {
                            val decoded = value.copyOf(valSize - 2)
                                .toString(Charsets.US_ASCII)

                            Log.i(Constants.BT_TAG, "Received: $decoded")
                            Toast.makeText(this@MainActivity, "Received: $decoded",
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val btBroadcastListener by lazy {
        BtBroadcastListener().apply {
            onBluetoothOff = {
                runOnUiThread {
                    ConnectionManager.disconnectAllDevices()
                }
            }
            onBondStateChanged = { bondState ->
                runOnUiThread {
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        val encryptionMenuItem = mainBinding.topAppBar.menu.
                            findItem(R.id.encryption_menu_item)
                        encryptionMenuItem?.let {
                            it.setIcon(R.drawable.encryption)
                            it.iconTintList = ContextCompat.getColorStateList(
                                this@MainActivity, R.color.encryption_on)
                        }
                    }
                    else if (bondState == BluetoothDevice.BOND_NONE) {
                        val encryptionMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.encryption_menu_item)
                        encryptionMenuItem?.let {
                            it.setIcon(R.drawable.no_encryption)
                            it.iconTintList = ContextCompat.getColorStateList(
                                this@MainActivity, R.color.black)
                        }
                    }
                }
            }
        }
    }

    // TODO add filtering on name
//    private val scanFilter = ScanFilter.Builder()
//        .build()
//
//    private val scanSettings = ScanSettings.Builder()
//        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//        // TODO Enable after testing W/ and W/O
////        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
//        .build()
//
//    private val scanCallback = object : ScanCallback() {
//        @SuppressLint("MissingPermission")
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            super.onScanResult(callbackType, result)
//
//            if (scanResults.none { it.device.address == result.device.address
//                        // TODO remove the name comparison, when filtering on name applied
//                        && it.device.name == result.device.name
//            }) {
//                with(result.device) {
//                    var msg = "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address, UUIDS:"
//                    // TODO Maybe change uuids to result.scanRecord.serviceUuids
//                    uuids.forEachIndexed { i, parcelUUid ->
//                        msg += "\n${i+1}: ${parcelUUid.uuid}"
//                    }
//                    Log.i(Constants.BT_TAG, msg)
//                }
//                scanResults.add(result)
//            }
//        }
//
//        override fun onScanFailed(errorCode: Int) {
//            Log.e(Constants.BT_TAG, "onScanFailed: code $errorCode")
//        }
//    }

//    private val scanResults = mutableListOf<ScanResult>()
    private val btStateChangedReceiver = BtStateChangedReceiver()

    private var permissionsPermanentlyDenied = false
    private var enablingNotification = false
    private var bleDisplay: BluetoothDevice? = null
    private var writableDisplayChar: BluetoothGattCharacteristic? = null

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var mainBinding: ActivityMainBinding

    private val scoreViewModel: ScoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.mainBinding = ActivityMainBinding.inflate(layoutInflater)
        val view = mainBinding.root

        setContentView(view)
//        setContentView(R.layout.activity_main)

        this.registerActivityForResult()

        ConnectionManager.registerListener(connectionEventListener)
        btStateChangedReceiver.registerListener(btBroadcastListener)

        this.setSupportActionBar(mainBinding.topAppBar)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                scoreViewModel.score.collect {
                    mainBinding.leftScore.text = it.left.toString()
                    mainBinding.rightScore.text = it.right.toString()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                scoreViewModel.isHeadingToTheReferee.collect {
                    changeScoreOrientation(it)
                }
            }
        }

        mainBinding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bluetooth_menu_item -> {
                    this.onBluetoothBtnClick()
                    true
                }

                R.id.encryption_menu_item -> {
                    onEncryptionBtnClick()
                }

                R.id.settings_menu_item -> {
                    // TODO
                    true
                }

                else -> { false }
            }
        }

        mainBinding.okBtn.setOnClickListener {
            if (bleDisplay != null) {
//                if (writableDisplayChar == null) {
//                    writableDisplayChar = ConnectionManager.findCharacteristic(
//                        bleDisplay!!, Constants.DISPLAY_WRITABLE_CHARACTERISTIC_UUID)
//                }
                if (writableDisplayChar != null) {
                    val score = scoreViewModel.score.value

                    val scoreToSend = if (!scoreViewModel.isHeadingToTheReferee.value) {
                        "${score.right}:${score.left}"
                    } else {
                        "${score.left}:${score.right}"
                    }

                    val updateScoreCmd = Constants.SET_SCORE_CMD_PREFIX + scoreToSend +
                            Constants.CRLF
                    ConnectionManager.writeCharacteristic(
                        bleDisplay!!, writableDisplayChar!!,
                        updateScoreCmd.toByteArray(Charsets.US_ASCII)
                    )
                }
            }
            else {
                mainBinding.okBtn.visibility = View.INVISIBLE
            }

            scoreViewModel.confirmOrientation()
            scoreViewModel.confirmNewScore()

            mainBinding.cancelBtn.visibility = View.INVISIBLE
            mainBinding.moveBtn.visibility = View.INVISIBLE
            mainBinding.swapBtn.visibility = View.INVISIBLE
            mainBinding.resetBtn.visibility = View.INVISIBLE
        }

        mainBinding.cancelBtn.setOnClickListener {
            scoreViewModel.revertOrientation()
            scoreViewModel.revertScore()

            mainBinding.cancelBtn.visibility = View.INVISIBLE
            mainBinding.okBtn.visibility = View.INVISIBLE
        }

        mainBinding.moveBtn.setOnClickListener {
            scoreViewModel.toggleOrientation()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.swapBtn.setOnClickListener {
            scoreViewModel.swapScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.incrLeftScoreBtn.setOnClickListener {
            scoreViewModel.incrementLeftScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.decrLeftScoreBtn.setOnClickListener {
            scoreViewModel.decrementLeftScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.incrRightScoreBtn.setOnClickListener {
            scoreViewModel.incrementRightScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.decrRightScoreBtn.setOnClickListener {
            scoreViewModel.decrementRightScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.resetBtn.setOnClickListener {
            scoreViewModel.resetScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.scoreVerticalLinearLayout.setOnLongClickListener {
            makeSpecialButtonsVisible()
            true
        }

        mainBinding.leftScore.setOnLongClickListener {
            makeSpecialButtonsVisible()
            true
        }

        mainBinding.rightScore.setOnLongClickListener {
            makeSpecialButtonsVisible()
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun onEncryptionBtnClick(): Boolean {
        bleDisplay?.createBond()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return true
    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.settings_menu_item -> {
//            }
//        }
//
//        return true
//    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }

        this.registerReceiver(btStateChangedReceiver, filter)
    }

    override fun onStop() {
        super.onStop()

        btStateChangedReceiver.unregisterListener(btBroadcastListener)
        this.unregisterReceiver(btStateChangedReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()

        ConnectionManager.unregisterListener(connectionEventListener)
    }

    /**
     * If some permissions were denied, show a rationale with explanation why the permissions are
     * needed and ask for them again.
     *
     * If some permissions were permanently denied, explain to the users that the app cannot work
     * without them and navigate users to app settings to manually enable them.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == Constants.BT_PERMISSIONS_REQUEST_CODE) {
            val containsPermanentDenial = permissions
                .zip(grantResults.toTypedArray())
                .any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
            val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            when {
                containsPermanentDenial -> {
                    this.permissionsPermanentlyDenied = true
                    this.showStepsToManuallyEnablePermissions()
                }
                containsDenial -> {
                    this.showRequestBtPermissionsRationale()
                }
                allGranted -> {
                    this.showBtFragment()
                }
            }
        }
    }

    private fun onBluetoothBtnClick() {
        if (this.isBleSupported()) {

            if (this.hasBtPermissions()) {
//                    this.promptEnableBluetooth()
                this.showBtFragment()
            }
            else {
                this.requestBtPermissions()
//                if (this.hasBtPermissions()) {
//                    btFragment.show(supportFragmentManager, "BluetoothFragment")
//                }
//                    this.promptEnableBluetooth()
            }
        }
        else {
            Toast.makeText(this, "Bluetooth Low Energy is not supported on this " +
                    "device", Toast.LENGTH_LONG).show()
        }
    }

//    private fun onMoveBtnClick() {
//        val moveBtn = mainBinding.moveBtn
//        val scoreVerticalLL = mainBinding.scoreVerticalLinearLayout
//        val scoreHorizontalLL = mainBinding.scoreHorizontalLinearLayout
//        val scoreStand = mainBinding.scoreStand
//        val direction1 = mainBinding.scoreDirection1
//        val direction2 = mainBinding.scoreDirection2

//        val moveBtnLayoutParams = moveBtn.layoutParams
//                as ConstraintLayout.LayoutParams
//        val swapBtnLayoutParams = mainBinding.swapBtn.layoutParams
//                as ConstraintLayout.LayoutParams
//        val scoreVerticalLLParams = scoreVerticalLL.layoutParams
//                as ConstraintLayout.LayoutParams
//        val incDecLLParams =
//            mainBinding.incrementDecrementLinearLayout.layoutParams as ConstraintLayout.LayoutParams

//        if (isScoreFacingUp) {
//            moveBtnLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
//            moveBtnLayoutParams.topToBottom = ConstraintSet.UNSET
//
//            swapBtnLayoutParams.topToBottom = R.id.ok_cancel_linear_layout
//            swapBtnLayoutParams.bottomToTop = R.id.move_btn
//
//            scoreVerticalLLParams.bottomToBottom = ConstraintSet.UNSET
//            scoreVerticalLLParams.topToBottom = R.id.top_app_bar
//            scoreVerticalLLParams.bottomMargin = 0
//            scoreVerticalLLParams.topMargin = this.dpToPixels(10)
//
//            incDecLLParams.bottomToTop = ConstraintSet.UNSET
//            incDecLLParams.topToBottom = R.id.score_vertical_linear_layout
//            incDecLLParams.bottomMargin = 0
//            incDecLLParams.topMargin = this.dpToPixels(10)

//            isScoreFacingUp = false
//        }
//        else {
//            moveBtnLayoutParams.bottomToBottom = ConstraintSet.UNSET
//            moveBtnLayoutParams.topToBottom = R.id.top_app_bar
//
//            swapBtnLayoutParams.topToBottom = R.id.move_btn
//            swapBtnLayoutParams.bottomToTop = R.id.ok_cancel_linear_layout
//
//            scoreVerticalLLParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
//            scoreVerticalLLParams.topToBottom = ConstraintSet.UNSET
//            scoreVerticalLLParams.bottomMargin = this.dpToPixels(10)
//            scoreVerticalLLParams.topMargin = 0
//
//            incDecLLParams.bottomToTop = R.id.score_vertical_linear_layout
//            incDecLLParams.topToBottom = ConstraintSet.UNSET
//            incDecLLParams.bottomMargin = this.dpToPixels(10)
//            incDecLLParams.topMargin = 0

//            isScoreFacingUp = true
//        }

//        scoreViewModel.swapScore()
//    }

    private fun changeScoreOrientation(scoreShouldHeadToTheReferee: Boolean) {
        val moveBtn = mainBinding.moveBtn
        val scoreVerticalLL = mainBinding.scoreVerticalLinearLayout
        val scoreHorizontalLL = mainBinding.scoreHorizontalLinearLayout
        val scoreStand = mainBinding.scoreStand
        val direction1 = mainBinding.scoreDirection1
        val direction2 = mainBinding.scoreDirection2

        if (scoreShouldHeadToTheReferee) {
            // Swapping the score_horizontal_linear_layout with score_stand, which
            // basically means removing them from the outer LinearLayout and adding them
            // again in a reverse order.
            scoreVerticalLL.removeView(scoreHorizontalLL)
            scoreVerticalLL.removeView(scoreStand)
            scoreVerticalLL.addView(scoreStand)
            scoreVerticalLL.addView(scoreHorizontalLL)

            direction1.setImageResource(R.drawable.direct_down)
            direction2.setImageResource(R.drawable.direct_down)

            moveBtn.setIconResource(R.drawable.arrow_up)
        }
        else {
            // Swapping the score_horizontal_linear_layout with score_stand, which
            // basically means removing them from the outer LinearLayout and adding them
            // again in a reverse order.
            scoreVerticalLL.removeView(scoreStand)
            scoreVerticalLL.removeView(scoreHorizontalLL)
            scoreVerticalLL.addView(scoreHorizontalLL)
            scoreVerticalLL.addView(scoreStand)

            direction1.setImageResource(R.drawable.direct_up)
            direction2.setImageResource(R.drawable.direct_up)

            moveBtn.setIconResource(R.drawable.arrow_down)
        }
    }

    private fun makeSpecialButtonsVisible() {
        mainBinding.moveBtn.visibility = View.VISIBLE
        mainBinding.swapBtn.visibility = View.VISIBLE
        mainBinding.resetBtn.visibility = View.VISIBLE
    }

    private fun showBtFragment() {
        val btFragment = BluetoothFragment.newInstance(bleDisplay != null)
        btFragment.show(supportFragmentManager, "BluetoothFragment")
    }

    // TODO use when clicking on BT button
    private fun isBleSupported(): Boolean {
        return this.btAdapter != null
                && packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private fun registerActivityForResult() {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.i(Constants.BT_TAG, "Enable Bluetooth activity result OK")
            } else {
                Log.i(Constants.BT_TAG, "Enable Bluetooth activity result DENIED")
            }
        }
    }

//    private fun promptEnableBluetooth() {
//        if (this.btAdapter == null) {
//            Log.i(Constants.BT_TAG, "BluetoothAdapter is null")
//        }
//        else {
//            if (!this.btAdapter!!.isEnabled) {
//                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//
//                if (this.hasBtPermissions()) {
//                    this.activityResultLauncher.launch(enableBtIntent)
//                }
//                else {
//                    this.requestBtPermissions()
//                }
//            }
//        }
//    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasBtPermissions(): Boolean =
        hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    private fun requestBtPermissions() {
        ActivityCompat.requestPermissions(this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            Constants.BT_PERMISSIONS_REQUEST_CODE
        )
    }

    private fun showRequestBtPermissionsRationale() {
        val alertBuilder = AlertDialog.Builder(this)

        with(alertBuilder)
        {
            setTitle("Bluetooth permission required")
            setMessage("Starting from Android 12, the system requires apps to be granted " +
                    "Bluetooth access in order to scan for and connect to BLE devices.")
            setPositiveButton("OK") { _,_ -> requestBtPermissions() }
            setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            setCancelable(false)
            show()
        }
    }

    private fun showStepsToManuallyEnablePermissions() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth permissions were permanently denied!")
            .setMessage("Please, navigate to App Settings and manually grant Bluetooth " +
                    "permissions to allow connection to BLE Score Counter.")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    fun dpToPixels(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    @SuppressLint("MissingPermission")
    private fun handleBondState() {
        if (bleDisplay != null) {
            mainBinding.topAppBar.menu.findItem(R.id.encryption_menu_item)?.let {
                it.isVisible = true
                if (bleDisplay!!.bondState == BluetoothDevice.BOND_BONDED) {
                    it.setIcon(R.drawable.encryption)
                    it.iconTintList = ContextCompat.getColorStateList(
                        this@MainActivity, R.color.encryption_on)
                } else {
                    if (scoreViewModel.askToBond) {
                        bleDisplay!!.createBond()
                    }
                }
            }
        }
    }
}