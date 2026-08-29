package com.example.rosdronebridge.data

import dji.sdk.keyvalue.value.flightcontroller.FCFlightMode
import dji.sdk.keyvalue.value.flightcontroller.FCMotorStartFailureError
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.sdk.keyvalue.value.flightcontroller.HomeLocationType

data class DroneState(
    val connected: Boolean = false,
    val motorsOn: Boolean = false,
    val isFlying: Boolean = false,
    val isVirtualStickEnabled: Boolean = false,
    val flightMode: FlightMode? = null,
    val satelliteCount: Int? = 0, // Set to 0 to avoid false-positive checks
    val isHomeLocationSet: Boolean = false,
    val isCompassInNormalState: Boolean = false,
    val batteryChargeRemaining: Int? = 0,
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0,
    val altitude: Double? = 0.0,
    val velocityX: Double = 0.0,
    val velocityY: Double = 0.0,
    val velocityZ: Double = 0.0,
    val takeoffError: FCMotorStartFailureError? = null,
    val positionX: Float = 0.0F,
    val positionY: Float = 0.0F,
    val positionZ: Float = 0.0F,
    val gimbalPitch: Double = 0.0,
    val gimbalRoll: Double = 0.0,
    val gimbalYaw: Double = 0.0,
    val latitudeDistFromHome: Double? = 0.0,
    val longitudeDistFromHome: Double? = 0.0,
    val homeLocationType: HomeLocationType
)
