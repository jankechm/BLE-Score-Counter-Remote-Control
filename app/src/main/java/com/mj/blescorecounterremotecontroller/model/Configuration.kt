package com.mj.blescorecounterremotecontroller.model

data class Configuration(
    var brightness: Int = 3,
    var useScore: Boolean = true,
    var useDate: Boolean = false,
    var useTime: Boolean = true,
    var scroll: Boolean = false,
    var askToBond: Boolean = true
)
