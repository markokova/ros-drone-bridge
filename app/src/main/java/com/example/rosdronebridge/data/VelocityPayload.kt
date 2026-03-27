package com.example.rosdronebridge.data

sealed interface ROSPayload

data class VelocityPayload(
    val x : Double,
    val y : Double,
    val z : Double,
    val yaw : Double
) : ROSPayload

data class StringPayload(
    val message : String
) : ROSPayload
