package com.mj.blescorecounterremotecontroller

class Score(var left: Int, var right: Int) {

    enum class ChangeIn {
        NONE,
        LEFT,
        RIGHT,
        BOTH
    }


    fun copyScore(other: Score) {
        left = other.left
        right = other.right
    }

    fun detectChange(other: Score): ChangeIn {
        val isLeftDifferent = left != other.left
        val isRightDifferent = right != other.right

        return when {
            isLeftDifferent && isRightDifferent -> ChangeIn.BOTH
            isLeftDifferent -> ChangeIn.LEFT
            isRightDifferent -> ChangeIn.RIGHT
            else -> ChangeIn.NONE
        }
    }
}