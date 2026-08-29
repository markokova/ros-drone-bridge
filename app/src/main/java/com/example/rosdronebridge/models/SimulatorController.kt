package com.example.rosdronebridge.models

import android.util.Log
import com.example.rosdronebridge.di.ApplicationScope
import com.example.rosdronebridge.util.DroneStateTracker
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.simulator.InitializationSettings
import dji.v5.manager.aircraft.simulator.SimulatorManager
import dji.v5.manager.aircraft.simulator.SimulatorStatusListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulatorController @Inject constructor(
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val stateTracker : DroneStateTracker,
    private val rosBridgeManager: ROSBridgeManager
) {

    private val simulatorManager = SimulatorManager.getInstance()

    private val _isSimulatorActive = MutableStateFlow(false)
    val isSimulatorActive: StateFlow<Boolean> = _isSimulatorActive

    init {
        coroutineScope.launch { 
            // Only trigger when the connection status actually CHANGES
            stateTracker.droneState
                .map { it.connected }
                .distinctUntilChanged()
                .collect { connected -> // TODO - nekad se simulator ne upali jer se mozda vec dron povezao ili nesto slicno => errorType=CORE, errorCode=REQUEST_HANDLER_NOT_FOUND
                    if (connected) {
                        // Wait for SDK handlers to register
                        rosBridgeManager.logToRos("logs", "SimulatorController", "Waiting for SDK to settle...")
                        // delay(5000)
                        // startSimulator()
                        rosBridgeManager.logToRos("logs", "SimulatorController", "Drone connected")
                    } else {
                        _isSimulatorActive.value = false
                        rosBridgeManager.logToRos("logs", "SimulatorController", "Drone not connected")
                    }
                }
        }
    }

    private val simulatorStatusListener = SimulatorStatusListener { state ->
        // This state comes directly from the simulator physics engine
        val lat = state.location.latitude
        val lon = state.location.longitude
        val positionX = state.positionX
        val positionY = state.positionY
        val positionZ = -state.positionZ

        stateTracker.setSimulatorState(lat, lon, positionX, positionY, positionZ)

        // rosBridgeManager.logToRos("SimulatorEngine", "Engine Lat: $lat, Lon: $lon")

        // Log occasionally so we don't flood ROS
        if (System.currentTimeMillis() % 2000 < 100) {
            rosBridgeManager.logToRos("logs", "SimulatorEngine", "Lat: $lat, Lon: $lon, Alt: $positionZ m")
        }
    }

    private fun startSimulator(
        latitude: Double = 47.397742,
        longitude: Double = 8.545594,
        satelliteCount: Int = 20
    ) {
        if (simulatorManager.isSimulatorEnabled) {
            _isSimulatorActive.value = true
            return
        }

        // 1. Clean up to prevent internal SDK conflicts
        simulatorManager.clearAllSimulatorStateListener()
        simulatorManager.addSimulatorStateListener(simulatorStatusListener)

        val coordinates = LocationCoordinate2D(latitude, longitude)
        val settings = InitializationSettings.createInstance(coordinates, satelliteCount)

        simulatorManager.enableSimulator(settings,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {

                    // Start a coroutine to check if the engine actually starts ticking
                    coroutineScope.launch {
                        delay(2000)
                        if (!_isSimulatorActive.value) {
                            rosBridgeManager.logToRos("logs", "Simulator", "Command success, but Engine not ticking. Retrying...")
                            // Sometimes calling it twice or after a delay helps SDK v5
                            delay(1000)
                            // Note: Don't recurse infinitely, just one retry or a manual button
                        }
                    }
                    _isSimulatorActive.value = true
                    rosBridgeManager.logToRos("logs", "Simulator", "SUCCESS: Simulator command accepted.")

                    stateTracker.setCoordinates(latitude, longitude)
                    stateTracker.setSatelliteCount(satelliteCount)

                    // TODO - old functionality:
                    _isSimulatorActive.value = true
                    Log.i("Simulator", "Simulator enabled at $latitude, $longitude")
                    rosBridgeManager.logToRos("logs", "Simulator", "SUCCESS: Simulator enabled: $latitude, $longitude")
                }

                override fun onFailure(error: IDJIError) {
                    _isSimulatorActive.value = false
                    // If it's the specific "Not Found" error, try one more time after a delay
                    if (error.errorCode() == "REQUEST_HANDLER_NOT_FOUND") {
                        coroutineScope.launch {
                            rosBridgeManager.logToRos("logs", "Simulator", "Handler not ready, retrying in 3s...")
                            delay(3000)
                            startSimulator(latitude, longitude, satelliteCount)
                        }
                    } else {
                        rosBridgeManager.logToRos("logs", "Simulator", "ERROR: $error")
                    }
//                    Log.e("Simulator", "Failed to enable simulator: $error")
//                    rosBridgeManager.logToRos("Simulator", "ERROR: Failed to enable simulator: $error")
                }
            })
    }

    fun stopSimulator() {
        simulatorManager.disableSimulator(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                _isSimulatorActive.value = false
                Log.i("Simulator", "Simulator disabled")
            }

            override fun onFailure(error: IDJIError) {
                Log.e("Simulator", "Failed to disable simulator: $error")
            }
        })
    }
}
