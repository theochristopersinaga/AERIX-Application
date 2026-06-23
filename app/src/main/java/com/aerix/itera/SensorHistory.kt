package com.aerix.itera

data class SensorHistory(
    val timeStr: String = "",
    val CO_PPM: Float = 0f,
    val PM25: Float = 0f,
    val VIN_INA: Float = 2f,
    val ARUS_INA: Float = 2f,
    val POWER: Float = 2f
)

