package com.example.rosdronebridge.util

import com.example.rosdronebridge.data.DroneState
import com.example.rosdronebridge.models.ROSBridgeManager
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.flightcontroller.FCConfigCompassCheckStatus
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DroneStateTracker @Inject constructor(
    private val rosBridgeManager: ROSBridgeManager
    ) {
    private val _droneState = MutableStateFlow(DroneState())
    val droneState: StateFlow<DroneState> = _droneState

//    init {
//        initStateListeners()
//    }

    fun initStateListeners() {
        ProductKey.KeyConnection.create().listen(this) { connected ->
            updateState { it.copy(connected = connected == true) }
            rosBridgeManager.logToRos("logs",
                "DroneStateTracker.initStateListeners()", "connected: $connected")
        }

        FlightControllerKey.KeyAircraftLocation3D.create().listen(this) { location ->
            // TODO - log somehow: Log.d("DroneStateTracker", "RAW FC Location: ${location?.latitude}")
            location?.let {
                updateState {
                    it.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude
                    )
                }
            }
        }

        FlightControllerKey.KeyAircraftVelocity.create().listen(this) { velocity ->
            velocity?.let {
                updateState {
                    it.copy(
                        velocityX = velocity.x,
                        velocityY = velocity.y,
                        velocityZ = velocity.z
                    )
                }
            }
        }

        FlightControllerKey.KeyVirtualStickEnabled.create()
            .listen(this) { vsControlMode ->
                updateState { it.copy(isVirtualStickEnabled = vsControlMode == true) }
        }

        FlightControllerKey.KeyFlightMode.create()
            .listen(this) { flightMode ->
                updateState { it.copy(flightMode = flightMode) }
                rosBridgeManager.logToRos("state", "StateTracker", "Flight mode: ${flightMode?.value()}")
            }

        FlightControllerKey.KeyGPSSatelliteCount.create().listen(this) { satelliteCount ->
            updateState { it.copy(satelliteCount = satelliteCount) }
            rosBridgeManager.logToRos("state", "StateTracker", "satCount:$satelliteCount")
        }

        FlightControllerKey.KeyIsHomeLocationSet.create().listen(this) { isHomeLocationSet ->
            updateState { it.copy(isHomeLocationSet = isHomeLocationSet == true) }
        }

        FlightControllerKey.KeyCompassCheckStatus.create().listen(this) { compassStatus ->
            updateState {
                it.copy(isCompassInNormalState = compassStatus == FCConfigCompassCheckStatus.NORMAL)
            }
        }

        FlightControllerKey.KeyAreMotorsOn.create().listen(this) { motorsOn ->
            updateState { it.copy(motorsOn = motorsOn == true) }
            rosBridgeManager.logToRos("state", "StateTracker", "motorsOn:$motorsOn")
        }

        FlightControllerKey.KeyIsFlying.create().listen(this) { flying ->
            updateState { it.copy(isFlying = flying == true) }
        }

        // NEW: Listen for takeoff/start motor errors
        FlightControllerKey.KeyMotorStartFailureError.create().listen(this) { error ->
            updateState { it.copy(takeoffError = error) }
        }

        GimbalKey.KeyGimbalAttitude.create().listen(this) { attitude ->
            attitude?.let {
                updateState {
                    it.copy(
                        gimbalPitch = attitude.pitch,
                        gimbalRoll = attitude.roll,
                        gimbalYaw = attitude.yaw
                    )
                }

//                rosBridgeManager.logToRos(
//                    "logs",
//                    "DroneStateTracker",
//                    "Gimbal attitude: pitch=${attitude.pitch}, roll=${attitude.roll}, yaw=${attitude.yaw}"
//                )
            }
        }

        FlightControllerKey.KeyFlightControlCurrentAuthority.create().listen(this) { authority ->
            rosBridgeManager.logToRos("state", "StateTracker", "authority:$authority")
        }

        FlightControllerKey.KeyFlightControlAuthorityChangeReason.create().listen(this) { reason ->
            rosBridgeManager.logToRos("state", "StateTracker", "authChangeReason:$reason")
        }

        FlightControllerKey.KeyVirtualStickControlModeEnabled.create().listen(this) { enabled ->
            rosBridgeManager.logToRos("state", "StateTracker", "vsEnabled:$enabled")
        }

        FlightControllerKey.KeyFCFlightMode.create().listen(this) { rawMode ->
            rosBridgeManager.logToRos("state", "StateTracker", "rawFlightMode:$rawMode")
        }

        FlightControllerKey.KeyGPSSignalLevel.create().listen(this) { gpsLevel ->
            rosBridgeManager.logToRos("state", "StateTracker", "gpsLevel:$gpsLevel")
        }

        FlightControllerKey.KeyGPSModeFailureReason.create().listen(this) { gpsFailReas ->
            rosBridgeManager.logToRos("state", "StateTracker", "gpsFailReas:$gpsFailReas")
        }

        FlightControllerKey.KeyIsVisionSensorUsed.create().listen(this) { used ->
            rosBridgeManager.logToRos("state", "StateTracker", "visionUsed:$used")
        }

//        FlightControllerKey.KeyUltrasonicHeight.create().listen(this) { height ->
//            rosBridgeManager.logToRos("state", "StateTracker", "ultraHeight:$height")
//        }

        FlightControllerKey.KeyUltrasonicHasError.create().listen(this) { err ->
            rosBridgeManager.logToRos("state", "StateTracker", "ultraError:$err")
        }

        FlightControllerKey.KeyCompassHasError.create().listen(this) { err ->
            rosBridgeManager.logToRos("state", "StateTracker", "compassError:$err")
        }

        BatteryKey.KeyChargeRemainingInPercent.create().listen(this) { charge ->
            updateState { it.copy(batteryChargeRemaining = charge) }
        }
    }

    private fun updateState(transform: (DroneState) -> DroneState) {
        _droneState.update(transform)
    }

    fun setConnected(connected: Boolean) {
        updateState { it.copy(connected = connected) }
    }

    fun clear() {
        KeyManager.getInstance().cancelListen(this)
    }

    fun setCoordinates(lat: Double, lon: Double) {
        updateState { it.copy(latitude = lat, longitude = lon) }
    }

    fun setSatelliteCount(count: Int) {
        updateState { it.copy(satelliteCount = count) }
    }

    fun setSimulatorState(
        lat: Double,
        lon: Double,
        positionX: Float,
        positionY: Float,
        positionZ: Float
    ) {
        updateState {
            it.copy(
                latitude = lat,
                longitude = lon,
                positionX = positionX,
                positionY = positionY,
                altitude = positionZ.toDouble()
            )
        }
    }
}
