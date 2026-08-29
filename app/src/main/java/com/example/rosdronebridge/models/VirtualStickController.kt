package com.example.rosdronebridge.models

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.rosdronebridge.di.ApplicationScope
import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.RosLogger
import dji.sdk.keyvalue.value.flightcontroller.FlightControlAuthority
import dji.sdk.keyvalue.value.flightcontroller.FlightControlAuthorityChangeReason
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickState
import dji.v5.manager.aircraft.virtualstick.VirtualStickStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualStickController @Inject constructor(
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val stateTracker : DroneStateTracker,
    private val rosLogger: RosLogger
) {
    val currentVirtualStickStateInfo = MutableLiveData(VirtualStickStateInfo())
    var virtualStickActivated = false
    var advancedModeReady = false
    private var activationTime = 0L

    init {
        currentVirtualStickStateInfo.observeForever {
            rosLogger.log("state", "DroneController", "vsState:${it.state}, reason:${it.reason}")
        }

        // Enable Virtual Stick if conditions are satisfied
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

        VirtualStickManager.getInstance().setVirtualStickStateListener(object :
            VirtualStickStateListener {
            override fun onVirtualStickStateUpdate(stickState: VirtualStickState) {
                currentVirtualStickStateInfo.postValue(currentVirtualStickStateInfo.value?.apply {
                    this.state = stickState
                })
            }

            override fun onChangeReasonUpdate(reason: FlightControlAuthorityChangeReason) {
                currentVirtualStickStateInfo.postValue(currentVirtualStickStateInfo.value?.apply {
                    this.reason = reason
                })
            }
        })

    }

    fun enableVirtualStick() {
        // Even if already enabled, reset activation time to extend grace period for state sync
        activationTime = System.currentTimeMillis()

        val vsActivationNotPossible = stateTracker.droneState.value.isVirtualStickEnabled ||
                !stateTracker.droneState.value.isFlying ||
                stateTracker.droneState.value.flightMode != FlightMode.GPS_NORMAL

        if (vsActivationNotPossible) return

        VirtualStickManager.getInstance().enableVirtualStick(
            object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                activationTime = System.currentTimeMillis()
                virtualStickActivated = true
                rosLogger.log("state", "DroneController", "Virtual Stick authority granted")

                VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(true)

                /* IMMEDIATE INJECTION: Send a blank advanced parameter to the drone
                 to satisfy the firmware's strict immediate-packet check. Without this the drone
                 keeps switching to safe mode. */
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
                rosLogger.log("state", "DroneController", "Failed to enable VS: $error")
                virtualStickActivated = false
            }
        })
    }

    fun disableVirtualStick() {
        virtualStickActivated = false
        VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(false)
        VirtualStickManager.getInstance().disableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                Log.d("DroneController", "Virtual Stick disabled")
            }

            override fun onFailure(error: IDJIError) {
                Log.e("DroneController", "Disable failure: $error")
            }
        })
    }

    fun sendVirtualStickAdvancedParam(param: VirtualStickFlightControlParam) {
        VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(param)
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

        sendVirtualStickAdvancedParam(vsParam)
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

    data class VirtualStickStateInfo(
        var state: VirtualStickState = VirtualStickState(false, FlightControlAuthority.UNKNOWN, false),
        var reason: FlightControlAuthorityChangeReason = FlightControlAuthorityChangeReason.UNKNOWN
    )

}
