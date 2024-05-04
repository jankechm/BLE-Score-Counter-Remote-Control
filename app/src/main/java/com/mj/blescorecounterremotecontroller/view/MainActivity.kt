package com.mj.blescorecounterremotecontroller.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.mj.blescorecounterremotecontroller.AppCfgManager
import com.mj.blescorecounterremotecontroller.BleScoreCounterApp
import com.mj.blescorecounterremotecontroller.ConnectionManager
import com.mj.blescorecounterremotecontroller.ConnectionManager.isConnected
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.R
import com.mj.blescorecounterremotecontroller.ScoreManager
import com.mj.blescorecounterremotecontroller.broadcastreceiver.BtStateChangedReceiver
import com.mj.blescorecounterremotecontroller.databinding.ActivityMainBinding
import com.mj.blescorecounterremotecontroller.listener.BtBroadcastListener
import com.mj.blescorecounterremotecontroller.listener.ConnectionEventListener
import com.mj.blescorecounterremotecontroller.viewmodel.DisplayViewModel
import kotlinx.coroutines.launch
import timber.log.Timber


class MainActivity : AppCompatActivity() {
    private val btAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothManager.adapter
    }

    private val app: BleScoreCounterApp by lazy {
        application as BleScoreCounterApp
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onMtuChanged = { _, _ ->
                runOnUiThread {
                    val btMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.bluetooth_menu_item)
                    btMenuItem?.let {
                        it.iconTintList = ContextCompat.getColorStateList(
                            this@MainActivity, R.color.bt_connected
                        )
                        it.setIcon(R.drawable.bluetooth_connected)
                    }

                    handleBondState()

                    // TODO startForegroundService (Intent service) && startForeground(int, android.app.Notification) || JobScheduler || WorkManager
                }
            }
            onDisconnect = { _ ->
                runOnUiThread {
                    val btMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.bluetooth_menu_item)
                    btMenuItem?.let {
                        if (app.manuallyDisconnected) {
                            it.iconTintList = ContextCompat.getColorStateList(
                                this@MainActivity, R.color.bt_disconnected
                            )
                            it.setIcon(R.drawable.bluetooth_disabled)
                        }
                        else {
                            it.iconTintList = ContextCompat.getColorStateList(
                                this@MainActivity, R.color.black
                            )
                            it.setIcon(R.drawable.bluetooth)
                        }
                    }

                    val encryptionMenuItem = mainBinding.topAppBar.menu.
                        findItem(R.id.encryption_menu_item)
                    encryptionMenuItem?.let {
                        it.isVisible = false
                    }
                }
            }
            onCharacteristicWrite = { _, _ ->
                runOnUiThread {
                    mainBinding.okBtn.visibility = View.INVISIBLE
                }
            }
        }
    }

    private val btBroadcastListener by lazy {
        BtBroadcastListener().apply {
            onBondStateChanged = { bondState, _ ->
                runOnUiThread {
                    handleBondState(bondState)
                }
            }
        }
    }


    private val btStateChangedReceiver = BtStateChangedReceiver()

    private var permissionsPermanentlyDenied = false

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var mainBinding: ActivityMainBinding

    private val displayViewModel: DisplayViewModel by viewModels()

    private var allLEDsOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.mainBinding = ActivityMainBinding.inflate(layoutInflater)
        val view = mainBinding.root

        setContentView(view)

        this.registerActivityForResult()

        this.setSupportActionBar(mainBinding.topAppBar)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScoreManager.score.collect {
                    mainBinding.leftScore.text = it.left.toString()
                    mainBinding.rightScore.text = it.right.toString()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                displayViewModel.isHeadingToTheReferee.collect {
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
                    handleBondState()
                    true
                }

                R.id.settings_menu_item -> {
                    val cfgActivityIntent = Intent(this,
                        ConfigurationActivity::class.java)
                    startActivity(cfgActivityIntent)

                    true
                }

                else -> { false }
            }
        }

        mainBinding.okBtn.setOnClickListener {
            if (app.bleDisplay != null && app.bleDisplay!!.isConnected()) {
                if (app.writableDisplayChar != null) {
                    val score = ScoreManager.score.value

                    val scoreToSend = if (!displayViewModel.isHeadingToTheReferee.value) {
                        "${score.right}:${score.left}"
                    } else {
                        "${score.left}:${score.right}"
                    }

                    val updateScoreCmd = Constants.SET_SCORE_CMD_PREFIX + scoreToSend +
                            Constants.CRLF
                    ConnectionManager.writeCharacteristic(
                        app.bleDisplay!!, app.writableDisplayChar!!,
                        updateScoreCmd.toByteArray(Charsets.US_ASCII)
                    )
                }
            }
            else {
                mainBinding.okBtn.visibility = View.INVISIBLE
            }

            displayViewModel.confirmOrientation()
            ScoreManager.confirmNewScore()

            mainBinding.cancelBtn.visibility = View.INVISIBLE
            mainBinding.moveBtn.visibility = View.INVISIBLE
            mainBinding.swapBtn.visibility = View.INVISIBLE
            mainBinding.resetBtn.visibility = View.INVISIBLE
            mainBinding.allLedsOnBtn.visibility = View.INVISIBLE
        }

        mainBinding.cancelBtn.setOnClickListener {
            displayViewModel.revertOrientation()
            ScoreManager.revertScore()

            mainBinding.cancelBtn.visibility = View.INVISIBLE
            mainBinding.okBtn.visibility = View.INVISIBLE
        }

        mainBinding.moveBtn.setOnClickListener {
            displayViewModel.toggleOrientation()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.swapBtn.setOnClickListener {
            ScoreManager.swapScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.swapBtn.setOnLongClickListener {
            mainBinding.allLedsOnBtn.visibility = View.VISIBLE
            true
        }

        mainBinding.incrLeftScoreBtn.setOnClickListener {
            ScoreManager.incrementLeftScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.decrLeftScoreBtn.setOnClickListener {
            ScoreManager.decrementLeftScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.incrRightScoreBtn.setOnClickListener {
            ScoreManager.incrementRightScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.decrRightScoreBtn.setOnClickListener {
            ScoreManager.decrementRightScore()

            mainBinding.cancelBtn.visibility = View.VISIBLE
            mainBinding.okBtn.visibility = View.VISIBLE
        }

        mainBinding.resetBtn.setOnClickListener {
            ScoreManager.resetScore()

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

        mainBinding.allLedsOnBtn.setOnClickListener {
            if (app.bleDisplay != null) {
                if (app.writableDisplayChar != null) {
                    var value = 1
                    if (allLEDsOn) {
                        value = 0
                        mainBinding.allLedsOnBtn.backgroundTintList = ContextCompat.getColorStateList(
                            this@MainActivity, R.color.all_leds_on_0
                        )
                        allLEDsOn = false
                    }
                    else {
                        mainBinding.allLedsOnBtn.backgroundTintList = ContextCompat.getColorStateList(
                            this@MainActivity, R.color.all_leds_on_1
                        )
                        allLEDsOn = true
                    }
                    val updateScoreCmd = Constants.SET_ALL_LEDS_ON_CMD_PREFIX + value +
                            Constants.CRLF
                    ConnectionManager.writeCharacteristic(
                        app.bleDisplay!!, app.writableDisplayChar!!,
                        updateScoreCmd.toByteArray(Charsets.US_ASCII)
                    )
                }
            }
        }

        if (app.bleDisplay == null || !app.bleDisplay!!.isConnected()) {
            app.startConnectionToPersistedDeviceCoroutine()
        }
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

        ConnectionManager.registerListener(connectionEventListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.registerReceiver(btStateChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            this.registerReceiver(btStateChangedReceiver, filter)
        }

        btStateChangedReceiver.registerListener(btBroadcastListener)
    }

    override fun onResume() {
        super.onResume()

        if (app.bleDisplay != null) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                handleBondState(app.bleDisplay!!.bondState)
            }
        }
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

            if (app.hasBtPermissions()) {
//                    this.promptEnableBluetooth()
                this.showBtFragment()
            }
            else {
                app.requestBtPermissions(this)
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
//        val moveBtn = mainBinding.moveBtn
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

//            moveBtn.setIconResource(R.drawable.arrow_up)
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

//            moveBtn.setIconResource(R.drawable.arrow_down)
        }
    }

    private fun makeSpecialButtonsVisible() {
        mainBinding.moveBtn.visibility = View.VISIBLE
        mainBinding.swapBtn.visibility = View.VISIBLE
        mainBinding.resetBtn.visibility = View.VISIBLE
    }

    private fun showBtFragment() {
        val btFragment = BluetoothFragment.newInstance(
            app.bleDisplay != null && app.bleDisplay!!.isConnected())
        btFragment.show(supportFragmentManager, "BluetoothFragment")
    }

    private fun isBleSupported(): Boolean {
        return this.btAdapter != null
                && packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private fun registerActivityForResult() {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Timber.i("Enable Bluetooth activity result OK")
            } else {
                Timber.i("Enable Bluetooth activity result DENIED")
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



    private fun showRequestBtPermissionsRationale() {
        val alertBuilder = AlertDialog.Builder(this)

        with(alertBuilder)
        {
            setTitle("Bluetooth permission required")
            setMessage("Starting from Android 12, the system requires apps to be granted " +
                    "Bluetooth access in order to scan for and connect to BLE devices.")
            setPositiveButton("OK") { _,_ -> app.requestBtPermissions(this@MainActivity) }
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
        if (app.bleDisplay != null) {
            mainBinding.topAppBar.menu.findItem(R.id.encryption_menu_item)?.let {
                it.isVisible = true
                if (app.bleDisplay!!.bondState == BluetoothDevice.BOND_BONDED) {
                    it.setIcon(R.drawable.encryption)
                    it.iconTintList = ContextCompat.getColorStateList(
                        this@MainActivity, R.color.encryption_on)
                } else {
                    it.setIcon(R.drawable.no_encryption)
                    it.iconTintList = ContextCompat.getColorStateList(
                        this@MainActivity, R.color.black)
                    if (AppCfgManager.appCfg.askToBond) {
                        app.bleDisplay!!.createBond()
                    }
                }
            }
        }
    }

    private fun handleBondState(bondState: Int) {
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