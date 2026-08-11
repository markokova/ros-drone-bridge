package com.example.rosdronebridge.models

import android.util.Log
import com.example.rosdronebridge.di.ApplicationScope
import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.RosLogger
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.callback.CommonCallbacks.CompletionCallbackWithParam
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DroneControllerV2 @Inject constructor(
    private val basicAircraftControlManager: BasicAircraftControlManager,
    private val virtualStickVM: VirtualStickVM,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val stateTracker : DroneStateTracker,
    private val simulatorController: SimulatorController,
    private val rosLogger: RosLogger
) {
    var virtualStickActivated = false
    var advancedModeReady = false
    private var activationTime = 0L

    companion object {
        private const val GRACE_PERIOD = 1500L // To allow for DJI SDK to sync
        private const val MAX_VXY = 5.0
        private const val MAX_VZ = 2.0
        private const val MAX_YAW_RATE = 90.0
    }

    init {
        virtualStickVM.currentVirtualStickStateInfo.observeForever {
            rosLogger.log("state", "DroneController", "vsState:${it.state}, reason:${it.reason}")
        }

        coroutineScope.launch {
            stateTracker.droneState
                .map { it.flightMode }
                .distinctUntilChanged()
                .collect { mode ->
                    val isFlying = stateTracker.droneState.value.isFlying
                    rosLogger.log(
                        "state",
                        "DroneController",
                        "Flight mode:$mode, isFlying:$isFlying"
                    )
                    if (isFlying && mode != FlightMode.AUTO_TAKE_OFF &&
                        mode != FlightMode.AUTO_LANDING && mode != FlightMode.GO_HOME &&
                        !virtualStickActivated
                    ) {
                        rosLogger.log(
                            "logs", "DroneController.enableVS0()",
                            "Stable flight detected ($mode)"
                        )
                        enableVirtualStick()
                    }
                }
        }


        /**
        When Virtual Stick has flight authority ownership ROS drone control is enabled. Flight
        authority can change to Manual Remote Control/Automation controls motion(Go home/Auto
        landing triggered)/Intelligent flight mode etc. In that case Virtual stick control must be
        disabled. GRACE_PERIOD gives time for SDK to sync so that emergencyStop isn't called to soon
        which would cause VirtualStick never being activated.
         **/
        coroutineScope.launch {
            stateTracker.droneState.collect { state ->
                val now = System.currentTimeMillis()
                if (virtualStickActivated && (now - activationTime > GRACE_PERIOD)) {
                    val hasAuthority = state.isVirtualStickEnabled &&
                            state.flightMode == FlightMode.VIRTUAL_STICK
                    if (!hasAuthority) {
//                        rosLogger.logToRos("logs", "DroneController", "Authority lost or failed to engage. Mode: ${state.flightMode}")
                        // emergencyStop("Authority lost or failed to engage. Mode: ${state.flightMode}")
                    }
                }
            }
        }
    }


    fun takeoff() {
        rosLogger.log("logs", "DroneControllerV2", "takeoff()")
        val droneState = stateTracker.droneState.value
        // TODO - does satelliteCount needs to be checked?

        val isSimulatorEnabled = simulatorController.isSimulatorActive.value
        val hasGpsLock =
            true //(droneState.satelliteCount ?: 0) >= 10 && abs(droneState.latitude) > 0.1

        if (isSimulatorEnabled && abs(droneState.latitude) < 0.0001) {
            rosLogger.log(
                "logs",
                "DroneController",
                "Takeoff aborted: Simulator coordinates not synced yet."
            )
            return
        }

        val isReady = droneState.connected && !droneState.motorsOn && !droneState.isFlying &&
                (hasGpsLock || isSimulatorEnabled)
        Log.d(
            "DroneController",
            "Takeoff: $isReady. Connected: ${droneState.connected}, motorsOn: ${droneState.motorsOn}, isFlying: ${droneState.isFlying}, satelliteCount: ${droneState.satelliteCount}"
        )
        rosLogger.log(
            "state",
            "DroneController:takeoff()",
            "Takeoff: $isReady. Connected: ${droneState.connected}, motorsOn: ${droneState.motorsOn}, " +
                    "isFlying: ${droneState.isFlying}, satelliteCount: ${droneState.satelliteCount}, " +
                    "isSimulatorEnabled:" + isSimulatorEnabled + ", hasGpsLock: $hasGpsLock"
        )
        rosLogger.log(
            "state",
            "DroneController:takeoff()2",
            "isSimulatorEnabled:" + isSimulatorEnabled + ", hasGpsLock: $hasGpsLock"
        )
        Log.d(
            "DroneController",
            "Takeoff Check: Latt=${droneState.latitude}, Sats=${droneState.satelliteCount}, Sim=$isSimulatorEnabled"
        )

        if (!isReady) return

        basicAircraftControlManager.startTakeOff(object :
            CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                rosLogger.log(
                    "logs",
                    "DroneController:takeoff()",
                    "Takeoff successful, engaging Virtual Stick..."
                )
                coroutineScope.launch {
                    // Wait for the drone to actually start flying
                    while (!stateTracker.droneState.value.isFlying) {
                        delay(200)
                    }
                }
            }

            override fun onFailure(error: IDJIError) {
                Log.e("DroneController", "Takeoff failed: \n$error")
                rosLogger.log("logs", "DroneController:takeoff()", "Takeoff failed: $error")

            }
        })
    }

    fun land() {
        setZeroVelocity()
        disableVirtualStick()
        basicAircraftControlManager.startLanding(object :
            CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                Log.d("DroneController", "Landing initiated")
                rosLogger.log("logs", "DroneController:land()", "Landing initiated")
            }

            override fun onFailure(error: IDJIError) {
                Log.e("DroneController", "Landing failed: $error")
                rosLogger.log("logs", "DroneController:land()", "Landing failed: $error")
            }
        })
    }

    fun enableVirtualStick() {
        // Even if already enabled, reset activation time to extend grace period for state sync
        activationTime = System.currentTimeMillis()

        rosLogger.log(
            "state", "DroneController",
            "EnableVS123:${stateTracker.droneState.value.flightMode}, isFlying: ${stateTracker.droneState.value.isFlying}"
        )

        if (stateTracker.droneState.value.isVirtualStickEnabled) {
            virtualStickActivated = true
            return
        }
        if (!stateTracker.droneState.value.isFlying) return
        if (stateTracker.droneState.value.flightMode != FlightMode.GPS_NORMAL) return
        // TODO - do i need this VirtualStickManager init, should I use that or container class virtualStickVM?
        // VirtualStickManager.getInstance().init()


        virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                activationTime = System.currentTimeMillis()
                virtualStickActivated = true
                rosLogger.log("state", "DroneController", "Virtual Stick authority granted")

                virtualStickVM.enableVirtualStickAdvancedMode()

                // 2. IMMEDIATE INJECTION: Send a blank advanced parameter right here
                // to satisfy the firmware's strict immediate-packet check (+11ms window).
                val bootstrapParam = VirtualStickFlightControlParam().apply {
                    rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
                    verticalControlMode = VerticalControlMode.VELOCITY
                    yawControlMode = YawControlMode.ANGULAR_VELOCITY
                    rollPitchControlMode = RollPitchControlMode.VELOCITY
                    roll = 0.0
                    pitch = 0.0
                    yaw = 0.0
                    verticalThrottle = 0.0
                }
                VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(bootstrapParam)

                // Allow a small window for the MSDK thread context switch to finalize configuration
                coroutineScope.launch {
                    delay(200)
                    advancedModeReady = true
                    rosLogger.log("state", "DroneController", "VSAdvanced=ON")
                }
            }

            override fun onFailure(error: IDJIError) {
                Log.e("DroneController", "Failed to enable Virtual Stick: $error")
                rosLogger.log("state", "DroneController", "Failed to enable VS: $error")
                virtualStickActivated = false
            }
        })
    }

    fun disableVirtualStick() {
        virtualStickActivated = false
        virtualStickVM.disableVirtualStickAdvancedMode()
        virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                Log.d("DroneController", "Virtual Stick disabled")
            }

            override fun onFailure(error: IDJIError) {
                Log.e("DroneController", "Disable failure: $error")
            }
        })
    }

    fun sendVirtualStickAdvancedParam(param: VirtualStickFlightControlParam) {
        virtualStickVM.sendVirtualStickAdvancedParam(param)
    }

    fun setZeroVelocity() {
        if (!stateTracker.droneState.value.isVirtualStickEnabled) return

        val vsParam = VirtualStickFlightControlParam().apply {
            rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
            verticalControlMode = VerticalControlMode.VELOCITY
            yawControlMode = YawControlMode.ANGULAR_VELOCITY
            rollPitchControlMode = RollPitchControlMode.VELOCITY

            pitch = 0.0
            roll = 0.0
            verticalThrottle = 0.0
            yaw = 0.0
        }

        virtualStickVM.sendVirtualStickAdvancedParam(vsParam)
    }


    fun emergencyStop(reason: String) {
        setZeroVelocity()
        disableVirtualStick()
        Log.w("EMERGENCY_STOP", reason)
    }

    fun isReadyForVirtualStick(): Boolean {
        return stateTracker.droneState.value.isFlying &&
                (stateTracker.droneState.value.flightMode == FlightMode.GPS_NORMAL)
    }

    fun onAppBackgrounded() {
        emergencyStop("App moved to background")
    }
}
