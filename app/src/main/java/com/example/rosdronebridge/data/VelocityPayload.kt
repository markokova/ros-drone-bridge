package com.example.rosdronebridge.data

sealed interface ROSPayload

data class GimbalPayload(
    val pitch : Double,
    val yaw : Double
) : ROSPayload

data class VelocityPayload(
    val x : Double,
    val y : Double,
    val z : Double,
    val yaw : Double,
    val gimbalPitch : Double,
    val gimbalYaw : Double
) : ROSPayload

data class StringPayload(
    val message : String
) : ROSPayload
