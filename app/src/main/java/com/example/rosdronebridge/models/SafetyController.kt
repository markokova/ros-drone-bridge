package com.example.rosdronebridge.models

import android.content.Context
import android.widget.Toast
import com.example.rosdronebridge.util.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.flightcontroller.FailsafeAction
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.create
import dji.v5.manager.KeyManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
    ) {

    /**
     * Configures what the drone does if it loses connection with the remote/app.
     * @param action Options are FailsafeAction.GO_HOME,
     *                           FailsafeAction.HOVER, or
     *                           FailsafeAction.LAND
     */
    fun setConnectionLostAction(action: FailsafeAction) {
        val failsafeKey = FlightControllerKey.KeyFailsafeAction.create()

        KeyManager.getInstance().setValue(
            failsafeKey,
            action,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    settingsManager.setFailsafeAction(action.ordinal)
                }
                override fun onFailure(error: IDJIError) {
                    Toast.makeText(context, "Failed to set Failsafe Action: " +
                            "${error.description()}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}