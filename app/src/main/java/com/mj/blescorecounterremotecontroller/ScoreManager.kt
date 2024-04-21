package com.mj.blescorecounterremotecontroller

import com.mj.blescorecounterremotecontroller.model.Score
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ScoreManager {
    private val _score = MutableStateFlow(Score(0,0))
    val score: StateFlow<Score> = _score.asStateFlow()

    /**
     * Previous Score value
     */
    private var prevScore = _score.value.copy()


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
}