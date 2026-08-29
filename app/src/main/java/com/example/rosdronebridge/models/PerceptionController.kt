package com.example.rosdronebridge.models

import android.content.Context
import android.widget.Toast
import com.example.rosdronebridge.util.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.perception.PerceptionManager
import dji.v5.manager.aircraft.perception.data.ObstacleAvoidanceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerceptionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    ) {

    fun setObstacleAvoidance(turnOn: Boolean) {
        val targetType = if (turnOn) {
            // Break when obstacle is detected
            ObstacleAvoidanceType.BYPASS
        } else {
            // No obstacle avoidance
            ObstacleAvoidanceType.CLOSE
        }

        PerceptionManager.getInstance().setObstacleAvoidanceType(
            targetType,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    settingsManager.setObstacleAvoidanceEnabled(turnOn)
                }
                override fun onFailure(error: IDJIError) {
                    Toast.makeText(context, "Obstacle avoidance state update failed: $error",
                        Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}