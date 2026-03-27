package com.example.rosdronebridge.util

import com.example.rosdronebridge.data.DroneState
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.flightcontroller.FCConfigCompassCheckStatus
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class DroneStateTracker {
        private val _droneState = MutableStateFlow(DroneState())
        val droneState: StateFlow<DroneState> = _droneState

    init {
        initStateListeners()
    }

    private fun initStateListeners() {
        ProductKey.KeyConnection.create().listen(this) { connected ->
            updateState { it.copy(connected = connected == true) }
        }

        FlightControllerKey.KeyAircraftLocation3D.create().listen(this) { location ->
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

        FlightControllerKey.KeyVirtualStickControlModeEnabled.create()
            .listen(this) { vsControlMode ->
                updateState { it.copy(virtualStickAvailable = vsControlMode == true) }
        }

        FlightControllerKey.KeyFlightMode.create()
            .listen(this) { flightMode ->
                updateState { it.copy(flightMode = flightMode) }
        }

        FlightControllerKey.KeyGPSSatelliteCount.create().listen(this) { satelliteCount ->
            updateState { it.copy(satelliteCount = satelliteCount) }
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
        }

        FlightControllerKey.KeyIsFlying.create().listen(this) { flying ->
            updateState { it.copy(isFlying = flying == true) }
        }
    }

    private fun updateState(transform: (DroneState) -> DroneState) {
        _droneState.update(transform)
    }

    fun clear() {
        KeyManager.getInstance().cancelListen(this)
    }
}