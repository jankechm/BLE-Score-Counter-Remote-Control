package com.mj.blescorecounterremotecontroller.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BleDisplayCfg(
    @SerialName("bright_lvl")
    var brightness: Int = 3,
    @SerialName("use_score")
    var useScore: Boolean = true,
    @SerialName("use_date")
    var useDate: Boolean = false,
    @SerialName("use_time")
    var useTime: Boolean = true,
    @SerialName("scroll")
    var scroll: Boolean = false
)
