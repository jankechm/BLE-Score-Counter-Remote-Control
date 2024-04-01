package com.mj.blescorecounterremotecontroller.viewmodel

import androidx.lifecycle.ViewModel
import com.mj.blescorecounterremotecontroller.model.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigViewModel : ViewModel() {
    private val _config = MutableStateFlow(Configuration())
    val config: StateFlow<Configuration> = _config.asStateFlow()



}