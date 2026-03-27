package com.example.rosdronebridge.models

import android.util.Log
import com.example.rosdronebridge.data.ROSMessage
import com.example.rosdronebridge.data.StringPayload
import com.example.rosdronebridge.data.VelocityPayload
import com.example.rosdronebridge.util.DroneStateTracker
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.virtualstick.Stick
import dji.v5.common.callback.CommonCallbacks.CompletionCallbackWithParam
import dji.v5.manager.KeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class DroneController(
    private val basicAircraftControlVM: BasicAircraftControlVM,
    private val virtualStickVM: VirtualStickVM,
    private val rosBridgeClientVM: ROSBridgeClientVM,
    private val coroutineScope: CoroutineScope,
    private val stateTracker : DroneStateTracker
) {

    private val vsParam = VirtualStickFlightControlParam().apply {
        rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
        verticalControlMode = VerticalControlMode.VELOCITY
        yawControlMode = YawControlMode.ANGULAR_VELOCITY
        rollPitchControlMode = RollPitchControlMode.VELOCITY
    }

    private var virtualStickActive = false
    private var lastRosCommandTime = 0L

    companion object {
        private const val ROS_TIMEOUT_MS = 300L
        private const val MAX_VXY = 5.0      // m/s
        private const val MAX_VZ = 2.0       // m/s
        private const val MAX_YAW_RATE = 90.0 // deg/s
    }

    init {
        coroutineScope.launch {
            rosBridgeClientVM.message.collect { message ->
                handleRosMessage(message)
            }
        }

        coroutineScope.launch {
            watchdogLoop()
        }

        /**
        When Virtual Stick has flight authority ownership ROS drone control is enabled. Flight
        authority can change to Manual Remote Control/Automation controls motion(Go home/Auto
        landing triggered)/Intelligent flight mode etc. In that case Virtual stick control must be
        disabled.
        **/
        coroutineScope.launch {
            stateTracker.droneState.collect { state ->
                if (!state.virtualStickAvailable || state.flightMode != FlightMode.VIRTUAL_STICK)
                    emergencyStop("Flight mode changed to ${state.flightMode}")
            }
        }
    }

    fun takeoff() {
        val droneState = stateTracker.droneState.value
        val isReadyToTakeoff = droneState.connected && !droneState.motorsOn && !droneState.isFlying
                && droneState.satelliteCount!! >= 10 && droneState.isCompassInNormalState
                && droneState.isHomeLocationSet

        if (!isReadyToTakeoff) {
            Log.d("TakeOffNotReady",
                "TakeOff not ready\nconnected:${droneState.connected}\nmotorsOn:${droneState.motorsOn}\nisFlying:${droneState.isFlying}")
            return
        }

        basicAircraftControlVM.startTakeOff(object :
            CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                Log.d("start takeOff onSuccess:","")
            }

            override fun onFailure(error: IDJIError) {
                Log.d("start takeOff onFailure:", "$error")
            }
        })
    }

    fun land() {
        setZeroVelocity()
        disableVirtualStick()
        basicAircraftControlVM.startLanding(object :
            CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                Log.d("start landing onSuccess:","")
            }

            override fun onFailure(error: IDJIError) {
                Log.d("start landing onFailure:", "$error")
            }
        })
    }

    // on every new ROS message handleRosMessage(command) is called
    fun handleRosMessage(command: ROSMessage?) {
        when (command?.operation) {
            "subscribe" -> Log.d("handleRosMessage() -> subscribe:", command.message.toString())
            "publish" -> {
                when (command.topic) {
                    "basic_command" -> {
                        val payload = command.message as StringPayload
                        Log.d("handleRosMessage() -> payloadMessage:", payload.message)

                        when (payload.message) {
                            "takeoff" -> takeoff()
                            "land" -> land()
                        }
                    }
                    "velocity_command" -> {
                        val payload = command.message as VelocityPayload
                        Log.d("handleRosMessage() -> payloadMessage:", payload.toString())
                        setVelocity(payload.x, payload.y, payload.z, payload.yaw)
                    }
                }
            }
        }
    }

    fun enableVirtualStick() {
        if (virtualStickActive) return

        virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                virtualStickVM.enableVirtualStickAdvancedMode()
                virtualStickActive = true
                Log.d("droneController.enableVirtualStick()","onSuccess")
            }

            override fun onFailure(error: IDJIError) {
                Log.d("droneController.enableVirtualStick()","onFailure:$error")
            }
        })
    }

    fun disableVirtualStick() {
        if (!virtualStickActive) return

        virtualStickVM.disableVirtualStickAdvancedMode()
        virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback{
            override fun onSuccess() {}

            override fun onFailure(error: IDJIError) {
                Log.d("droneController.disableVirtualStick()","onFailure:$error")
            }
        })
        virtualStickActive = false
    }

    fun setVelocity(vx: Double, vy: Double, vz: Double, yawRateRad: Double) {
        val state = stateTracker.droneState.value
        val isConnected = rosBridgeClientVM.isConnected.value

        val isReadyForVelocityChange = isConnected && virtualStickActive && state.isFlying &&
                state.flightMode == FlightMode.VIRTUAL_STICK && state.virtualStickAvailable

        if (!isReadyForVelocityChange) return

        // remember last ROS command time, important for safety reasons;
        // Drone stops if commands are to rare
        lastRosCommandTime = System.currentTimeMillis()

        // Convert ROS → DJI
        val yawRateDeg = Math.toDegrees(yawRateRad)

        vsParam.pitch = vx.coerceIn(-MAX_VXY, MAX_VXY)          // forward m/s
        vsParam.roll = vy.coerceIn(-MAX_VXY, MAX_VXY)           // right m/s
        vsParam.verticalThrottle = vz.coerceIn(-MAX_VZ, MAX_VZ)
        vsParam.yaw = yawRateDeg.coerceIn(-MAX_YAW_RATE, MAX_YAW_RATE)

        virtualStickVM.sendVirtualStickAdvancedParam(vsParam)
    }

    private fun setZeroVelocity() {
        if (!virtualStickActive) return

        vsParam.pitch = 0.0
        vsParam.roll = 0.0
        vsParam.verticalThrottle = 0.0
        vsParam.yaw = 0.0

        virtualStickVM.sendVirtualStickAdvancedParam(vsParam)
    }

    private suspend fun watchdogLoop() {
        while (true) {
            delay(100)

            if (!virtualStickActive) continue

            val now = System.currentTimeMillis()
            if (now - lastRosCommandTime > ROS_TIMEOUT_MS) {
                setZeroVelocity()
            }
        }
    }

    private fun emergencyStop(reason: String) {
        setZeroVelocity()
        disableVirtualStick()
        Log.w("EMERGENCY_STOP", reason)
    }

    fun onAppBackgrounded() {
        emergencyStop("App backgrounded")
    }
}
