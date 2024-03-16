package com.mj.blescorecounterremotecontroller.viewmodel

import androidx.lifecycle.ViewModel
import com.mj.blescorecounterremotecontroller.Constants
import com.mj.blescorecounterremotecontroller.model.Score
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScoreViewModel : ViewModel() {
//    private val _leftScore = MutableStateFlow(0)
//    val leftScore: Flow<Int> = _leftScore.asStateFlow()
//
//    private val _rightScore = MutableStateFlow(0)
//    val rightScore: Flow<Int> = _rightScore.asStateFlow()
//
//    private var prevLeftScore = _leftScore.value
//    private var prevRightScore = _rightScore.value
    
    private val _score = MutableStateFlow(Score(0,0))
    val score: StateFlow<Score> = _score.asStateFlow()

    /**
     * Previous value
     */
    private var prevScore = _score.value.copy()

    private val _isHeadingToTheReferee = MutableStateFlow(false)
    val isHeadingToTheReferee: StateFlow<Boolean> = _isHeadingToTheReferee.asStateFlow()

    /**
     * Previous value
     */
    private var wasHeadingToTheReferee = _isHeadingToTheReferee.value


    fun incrementLeftScore() {
        _score.update {
            if (it.left < Constants.MAX_SCORE) {
                it.copy(left = it.left + 1)
            }
            else {
                it
            }
        }
    }

    fun incrementRightScore() {
        _score.update {
            if (it.right < Constants.MAX_SCORE) {
                it.copy(right = it.right + 1)
            }
            else {
                it
            }
        }
    }

    fun decrementLeftScore() {
        _score.update {
            if (it.left > Constants.MIN_SCORE) {
                it.copy(left = it.left - 1)
            }
            else {
                it
            }
        }
    }

    fun decrementRightScore() {
        _score.update {
            if (it.right > Constants.MIN_SCORE) {
                it.copy(right = it.right - 1)
            }
            else {
                it
            }
        }
    }

    fun resetScore() {
        _score.update {
            Score(0,0)
        }
    }

    fun swapScore() {
        _score.update {
            Score(it.right, it.left)
        }
    }

    @Synchronized
    fun confirmNewScore() {
        prevScore = _score.value.copy()
    }

    fun revertScore() {
        _score.update {
            prevScore.copy()
        }
    }

    fun toggleOrientation() {
        _isHeadingToTheReferee.value = !_isHeadingToTheReferee.value
        swapScore()
    }

    fun confirmOrientation() {
        wasHeadingToTheReferee = _isHeadingToTheReferee.value
    }

    fun revertOrientation() {
        _isHeadingToTheReferee.value = wasHeadingToTheReferee
    }
}