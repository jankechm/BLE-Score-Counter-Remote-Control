package com.mj.blescorecounterremotecontroller.viewmodel

import androidx.lifecycle.ViewModel
import com.mj.blescorecounterremotecontroller.model.AppCfg
import com.mj.blescorecounterremotecontroller.model.BleDisplayCfg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigViewModel : ViewModel() {
    private val _bleDisplayCfg = MutableStateFlow(BleDisplayCfg())
    val bleDisplayCfg: StateFlow<BleDisplayCfg> = _bleDisplayCfg.asStateFlow()

    private val _appCfg = MutableStateFlow(AppCfg())
    val appCfg: StateFlow<AppCfg> = _appCfg.asStateFlow()
}