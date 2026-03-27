package com.example.rosdronebridge.models

import android.util.Log
import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.simulator.InitializationSettings
import dji.v5.manager.aircraft.simulator.SimulatorManager
import dji.v5.manager.aircraft.simulator.SimulatorState
import dji.v5.manager.aircraft.simulator.SimulatorStatusListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SimulatorController {

    private val simulatorManager = SimulatorManager.getInstance()

    private val _simulatorState = MutableStateFlow<SimulatorState?>(null)
    val simulatorState: StateFlow<SimulatorState?> = _simulatorState

    private val simulatorListener = SimulatorStatusListener { state ->
        _simulatorState.value = state
    }

    val simulatorStateSb = MutableLiveData(StringBuffer())

    init {
        startSimulator()
        addSimulatorListener() // TODO - should this go before enabling simulator?
    }

    private val simulatorStateListener = SimulatorStatusListener { state ->
        simulatorStateSb.value?.apply {
            setLength(0)
            append("Motor On : " + state.areMotorsOn())
            append("\n")
            append("In the Air : " + state.isFlying)
            append("\n")
            append("Roll : " + state.roll)
            append("\n")
            append("Pitch : " + state.pitch)
            append("\n")
            append("Yaw : " + state.yaw)
            append("\n")
            append("PositionX : " + state.positionX)
            append("\n")
            append("PositionY : " + state.positionY)
            append("\n")
            append("PositionZ : " + state.positionZ)
            append("\n")
            append("Latitude : " + state.location.latitude)
            append("\n")
            append("Longitude : " + state.location.longitude)
            append("\n")
        }
        simulatorStateSb.postValue(simulatorStateSb.value)
    }

    private fun startSimulator(
        latitude: Double = 47.397742,
        longitude: Double = 8.545594,
        satelliteCount: Int = 10
    ) {
        if (simulatorManager.isSimulatorEnabled) return

        val coordinates = LocationCoordinate2D(latitude, longitude)
        val settings = InitializationSettings.createInstance(coordinates, satelliteCount)

        simulatorManager.enableSimulator(settings,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    Log.i("Simulator", "Simulator enabled")
                }

                override fun onFailure(error: IDJIError) {
                    Log.e("Simulator", "Failed to enable simulator: $error")
                }
            })
    }

    fun stopSimulator() {
        if (!simulatorManager.isSimulatorEnabled) return

        simulatorManager.disableSimulator(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                Log.i("Simulator", "Simulator disabled")
            }

            override fun onFailure(error: IDJIError) {
                Log.e("Simulator", "Failed to disable simulator: $error")
            }
        })
        clear()
    }

    // TODO - potrebno pozvati startSimulator pa onda addSimulatorListener (vjv u mainActivityu)
    // TODO - zatim, trebam slati telemtriju nazad na ROS, publishati na neki topic. To će biti
    // TODO - telemtrija s drona (simulirana/stvarna).
    private fun addSimulatorListener() {
        simulatorManager.addSimulatorStateListener(simulatorStateListener)
    }

    private fun clear() {
        simulatorManager.clearAllSimulatorStateListener()
        simulatorManager.destroy()
    }

    fun isSimulatorEnabled() : Boolean {
        return simulatorManager.isSimulatorEnabled
    }
}
