package com.example.rosdronebridge.models

import android.util.Log
import com.dji.wpmzsdk.common.utils.kml.createDroneInfoModel
import com.example.rosdronebridge.di.ApplicationScope
import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.RosLogger
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.v5.common.error.IDJIError
import dji.v5.et.action
import dji.v5.et.create
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BasicAircraftControlManager @Inject constructor(
    private val stateTracker : DroneStateTracker,
    private val simulatorController: SimulatorController,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val rosLogger: RosLogger
) {

    fun startTakeOff() {
        val droneState = stateTracker.droneState.value
        val isSimulatorEnabled = simulatorController.isSimulatorActive.value

        var latitude = droneState.latitude
        if (latitude == null) latitude = 0.0
        if (isSimulatorEnabled && abs(latitude) < 0.0001) {
            rosLogger.log(
                "logs",
                "DroneController",
                "Takeoff aborted: Simulator coordinates not synced yet."
            )
            return
        }

        val isReady = droneState.connected && !droneState.motorsOn && !droneState.isFlying
        rosLogger.log(
            "state",
            "DroneController:takeoff()",
            "Takeoff: $isReady. Connected: ${droneState.connected}, motorsOn: ${droneState.motorsOn}, " +
                    "isFlying: ${droneState.isFlying}, satelliteCount: ${droneState.satelliteCount}, " +
                    "isSimulatorEnabled:" + isSimulatorEnabled
        )

        rosLogger.log("logs",
            "DroneController",
            "Takeoff Check: Latt=${droneState.latitude}, Sats=${droneState.satelliteCount}, Sim=$isSimulatorEnabled"
        )

        if (!isReady) return


        FlightControllerKey.KeyStartTakeoff.create().action({ _ ->
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
        }, { error: IDJIError ->
            Log.e("DroneController", "Takeoff failed: \n$error")
            rosLogger.log("logs", "DroneController:takeoff()", "Takeoff failed: $error")
        })
    }

    fun startLanding() {
        FlightControllerKey.KeyStartAutoLanding.create().action({ _ ->
            rosLogger.log("logs", "DroneController:land()", "Landing initiated")

        }, { e: IDJIError ->
            rosLogger.log("logs", "DroneController:land()", "Landing failed: $e")
        })
    }

    fun goHome() {
        if (stateTracker.droneState.value.isHomeLocationSet) {
            FlightControllerKey.KeyStartGoHome.create().action({ _ ->
                rosLogger.log("logs", "DroneController:goHome()", "Going home initiated")
            }) { e: IDJIError ->
                rosLogger.log("logs", "DroneController:goHome()", "Going home failed: $e")
            }
        } else {
            rosLogger.log("logs", "basicAircraftController", "goHome() fail: ${stateTracker.droneState.value.isHomeLocationSet}")
        }
    }

    fun cancelGoHome() {
        FlightControllerKey.KeyStopGoHome.create().action({ _ ->
            rosLogger.log("logs", "DroneController:cancelGoHome()", "Going home cancelled")
        }) { e: IDJIError ->
            rosLogger.log("logs", "DroneController:cancelGoHome()", "Cancel failed: $e")
        }
    }
}