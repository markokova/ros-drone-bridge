package com.example.rosdronebridge.models

import android.util.Log
import com.example.rosdronebridge.data.ROSMessage
import com.example.rosdronebridge.data.StringPayload
import com.example.rosdronebridge.data.VelocityPayload
import com.example.rosdronebridge.di.ApplicationScope
import com.example.rosdronebridge.util.TelemetryPublisher
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.v5.et.create
import dji.v5.et.listen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ROSMessageHandler @Inject constructor(
    private val rosBridgeManager: ROSBridgeManager,
    private val telemetryPublisher: TelemetryPublisher,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val basicAircraftControlManager: BasicAircraftControlManager,
    private val gimbalController: GimbalController,
    private val virtualStickController: VirtualStickController,
    private val speedController: SpeedController
) {

    // Thread-safe variables acting as a global cache
    @Volatile private var targetRoll = 0.0
    @Volatile private var targetPitch = 0.0
    @Volatile private var targetYawRate = 0.0
    @Volatile private var targetVerticalThrottle = 0.0

    @Volatile private var targetGimbalPitch = 0.0

    @Volatile private var targetGimbalYaw = 0.0

    private var activationTime = 0L
    private var lastRosCommandTime = 0L

    companion object {
        private const val ROS_TIMEOUT_MS = 150L
        private const val GRACE_PERIOD = 1500L // To allow for DJI SDK to sync
        private const val MAX_VXY = 5.0
        private const val MAX_VZ = 2.0
        private const val MAX_YAW_RATE = 90.0
    }

    init {
        telemetryPublisher.start(coroutineScope)

        coroutineScope.launch {
            flightPipelineClock()
        }

        coroutineScope.launch {
            rosBridgeManager.message.collect { message ->
                handleRosMessage(message)
            }
        }

        FlightControllerKey.KeyFlightControlCurrentAuthority.create().listen(this) { authority ->
            rosBridgeManager.logToRos("state", "VirtualStickController", "authority:$authority")
        }

        FlightControllerKey.KeyVirtualStickControlModeEnabled.create().listen(this) { enabled ->
            rosBridgeManager.logToRos("state", "VirtualStickController", "vsEnabled:$enabled")
        }
    }

    private fun handleRosMessage(command: ROSMessage?) {
        when (command?.topic) {

            // ********** DRONE CONTROL **********
            "/drone/basic_command" -> {
                val payload = command.payload as? StringPayload ?: return
                when (payload.message) {
                    "takeoff" -> basicAircraftControlManager.startTakeOff()
                    "land" -> basicAircraftControlManager.startLanding()
                }
            }
            "/drone/cmd_vel" -> {
                if (command.payload is VelocityPayload) {
                    lastRosCommandTime = System.currentTimeMillis()

                    // Cache latest drone flight instructions. Internal clock ticks every 50ms
                    // triggering latest instructions to be sent to drone.
                    targetRoll = command.payload.x.coerceIn(-MAX_VXY, MAX_VXY)
                    targetPitch = -command.payload.y.coerceIn(-MAX_VXY, MAX_VXY)
                    targetVerticalThrottle = command.payload.z.coerceIn(-MAX_VZ, MAX_VZ)
                    targetYawRate = Math.toDegrees(command.payload.yaw).coerceIn(-MAX_YAW_RATE, MAX_YAW_RATE)
                    targetGimbalPitch = command.payload.gimbalPitch
                    targetGimbalYaw = command.payload.gimbalYaw
                }
            }
        }
    }

    private suspend fun flightPipelineClock() {
        while (true) {
            try {
                val now = System.currentTimeMillis()

                if (virtualStickController.virtualStickActivated && virtualStickController.advancedModeReady) {
                    // Network Safety Timeout: If Python stalls, cleanly default to a safe hover
                    if (now - lastRosCommandTime > ROS_TIMEOUT_MS) {
                        targetRoll = 0.0
                        targetPitch = 0.0
                        targetYawRate = 0.0
                        targetVerticalThrottle = 0.0
                    }

                    val speedMultiplier = speedController.getSpeedLevel()

                    val controlPayload = VirtualStickFlightControlParam().apply {
                        rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
                        verticalControlMode = VerticalControlMode.VELOCITY
                        yawControlMode = YawControlMode.ANGULAR_VELOCITY
                        rollPitchControlMode = RollPitchControlMode.VELOCITY

                        roll = targetRoll * speedMultiplier
                        pitch = targetPitch * speedMultiplier
                        yaw = targetYawRate * speedMultiplier
                        verticalThrottle = targetVerticalThrottle * speedMultiplier
                    }

                    virtualStickController.sendVirtualStickAdvancedParam(controlPayload)
                    gimbalController.rotateGimbalContinuous(
                        targetGimbalPitch,
                        targetGimbalYaw
                    )
                }
            } catch (e: Exception) {
                Log.e("VirtualStickController", "Clock pipeline skip: ${e.message}")
            }
            delay(50) // Enforces a 20Hz drone control heartbeat
        }
    }

    fun onAppBackgrounded() {
        virtualStickController.emergencyStop("App moved to background")
    }

}