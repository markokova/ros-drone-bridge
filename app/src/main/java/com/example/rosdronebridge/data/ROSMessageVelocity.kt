package com.example.rosdronebridge.data

data class ROSMessageVelocity(
    val operation : String,
    val topic : String,
    val x : Double,
    val y : Double,
    val z : Double
)
