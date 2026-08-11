package com.example.rosdronebridge.util

import com.example.rosdronebridge.models.ROSBridgeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryPublisher @Inject constructor(
    private val droneStateTracker: DroneStateTracker,
    private val rosBridgeManager: ROSBridgeManager
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            droneStateTracker.droneState.collect { state ->
                rosBridgeManager.publishTelemetry(state)
            }
        }
    }
}