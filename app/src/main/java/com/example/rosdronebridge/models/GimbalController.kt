package com.example.rosdronebridge.models

import com.example.rosdronebridge.util.RosLogger
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.gimbal.GimbalSpeedRotation
import dji.v5.et.action
import dji.v5.et.create
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GimbalController @Inject constructor(
    private val rosLogger: RosLogger
) {
    fun rotateGimbalContinuous(pitchSpeed: Double, yawSpeed: Double) {
        // 1. Create the velocity payload object
        // Speeds are measured in degrees per second (°/s). Positive/negative values change direction.
        val speedRotation = GimbalSpeedRotation().apply {
            this.pitch = pitchSpeed  // e.g., 15 means tilt up at 15°/s. -15 means tilt down.
            this.yaw = yawSpeed      // e.g., 20 means pan right at 20°/s. -20 means pan left.
            this.roll = 0.0          // Keep roll velocity at zero to prevent horizon tilting
        }

        // 2. Map to the specific speed execution action key
        val speedActionKey = GimbalKey.KeyRotateBySpeed.create(ComponentIndexType.LEFT_OR_MAIN)

        // 3. Fire the action down the pipeline
        speedActionKey.action(
            speedRotation,
            {
                if (speedRotation.pitch != 0.0 || speedRotation.yaw != 0.0) {
                    rosLogger.log("gimbal/feedback","GimbalControl",
                        "Gimbal successfully rotated to target speed.")
                }
            },
            { djiError ->
                rosLogger.log("gimbalControl","GimbalControl",
                    "Gimbal action pipeline error: ${djiError.description()}")
            }
        )
    }

}