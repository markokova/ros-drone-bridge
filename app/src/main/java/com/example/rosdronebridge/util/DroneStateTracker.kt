package com.example.rosdronebridge.util

import android.location.Location
import com.example.rosdronebridge.data.DroneState
import com.example.rosdronebridge.models.ROSBridgeManager
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.flightcontroller.FCConfigCompassCheckStatus
import dji.sdk.keyvalue.value.flightcontroller.HomeLocationType
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class DroneStateTracker @Inject constructor(
    private val rosBridgeManager: ROSBridgeManager
    ) {
    private val _droneState = MutableStateFlow(DroneState())
    val droneState: StateFlow<DroneState> = _droneState

    fun initStateListeners() {
        ProductKey.KeyConnection.create().listen(this) { connected ->
            updateState { it.copy(connected = connected == true) }
            rosBridgeManager.logToRos("logs",
                "DroneStateTracker.initStateListeners()", "connected: $connected")
        }

        FlightControllerKey.KeyAltitude.create().listen(this) { currentAltitude ->
            updateState { it.copy(altitude = currentAltitude) }
        }

        FlightControllerKey.KeyAircraftLocation.create().listen(this) { location ->
            location?.let {
                updateState { currentState ->
                    currentState.copy(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
        }

        FlightControllerKey.KeyHomeLocationWithType.create().listen(this) { homeLocation ->
            updateState {

                val currentLat = droneState.value.latitude
                val currentLon = droneState.value.longitude

                if (currentLat == null || currentLon == null || currentLat.isNaN() || currentLon.isNaN()) {
                    return@updateState it.copy(
                        latitudeDistFromHome = 0.0,
                        longitudeDistFromHome = 0.0,
                        homeLocationType = homeLocation?.type ?: HomeLocationType.UNKNOWN
                    )
                }

                val homeLat = homeLocation?.value?.latitude
                val homeLon = homeLocation?.value?.longitude
                val homeLocationType = homeLocation?.type ?: HomeLocationType.UNKNOWN

                val (eastMeters, northMeters) = if (homeLat != null && homeLon != null
                    && !homeLat.isNaN() && !homeLon.isNaN()
                    && !(homeLat == 0.0 && homeLon == 0.0)
                ) {
                    calculateDistanceComponents(homeLat, homeLon, currentLat, currentLon)
                } else {
                    Pair(0.0, 0.0)
                }

                it.copy(
                    latitudeDistFromHome = northMeters,
                    longitudeDistFromHome = eastMeters,
                    homeLocationType = homeLocationType
                )
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
    private fun calculateDistanceComponents(
        homeLat: Double, homeLon: Double,
        currentLat: Double, currentLon: Double
    ): Pair<Double, Double> {
        val results = FloatArray(3)
        Location.distanceBetween(homeLat, homeLon, currentLat, currentLon, results)
        val distanceMeters = results[0].toDouble()
        val bearingDeg = results[1].toDouble()
        val bearingRad = Math.toRadians(bearingDeg)

        val dxEast  = distanceMeters * sin(bearingRad)
        val dyNorth = distanceMeters * cos(bearingRad)
        return Pair(dxEast, dyNorth)
    }
}
