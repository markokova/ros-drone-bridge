package com.example.rosdronebridge.util

import com.example.rosdronebridge.models.ROSBridgeClientVM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TelemetryPublisher(
    private val droneStateTracker: DroneStateTracker,
    private val rosBridgeClientVM: ROSBridgeClientVM
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            droneStateTracker.droneState.collect { state ->
                rosBridgeClientVM.publishTelemetry(state)
            }
        }
    }
}