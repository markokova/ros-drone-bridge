package com.example.rosdronebridge.data

import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.flightcontroller.CompassState
import dji.sdk.keyvalue.value.flightcontroller.FCConfigCompassCheckStatus
import dji.sdk.keyvalue.value.flightcontroller.FlightMode

data class DroneState(
    val connected: Boolean = false,
    val motorsOn: Boolean = false,
    val isFlying: Boolean = false,
    val virtualStickAvailable: Boolean = false,
    val flightMode: FlightMode? = null,
    val satelliteCount: Int? = 10,
    val isHomeLocationSet: Boolean = false,
    val isCompassInNormalState: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val velocityX: Double = 0.0,
    val velocityY: Double = 0.0,
    val velocityZ: Double = 0.0
    )
