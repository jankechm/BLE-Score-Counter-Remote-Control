package com.mj.blescorecounterremotecontroller.viewmodel

import androidx.lifecycle.ViewModel
import com.mj.blescorecounterremotecontroller.ScoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DisplayViewModel : ViewModel() {
    private val _isHeadingToTheReferee = MutableStateFlow(false)
    val isHeadingToTheReferee: StateFlow<Boolean> = _isHeadingToTheReferee.asStateFlow()

    /**
     * Previous BLE display orientation value
     */
    private var wasHeadingToTheReferee = _isHeadingToTheReferee.value


    fun toggleOrientation() {
        _isHeadingToTheReferee.value = !_isHeadingToTheReferee.value
        ScoreManager.swapScore()
    }

    fun confirmOrientation() {
        wasHeadingToTheReferee = _isHeadingToTheReferee.value
    }

    fun revertOrientation() {
        _isHeadingToTheReferee.value = wasHeadingToTheReferee
    }
}