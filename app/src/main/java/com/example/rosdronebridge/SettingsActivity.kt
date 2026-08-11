package com.example.rosdronebridge

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.rosdronebridge.models.PerceptionController
import com.example.rosdronebridge.models.ROSBridgeManager
import com.example.rosdronebridge.models.SafetyController
import com.example.rosdronebridge.models.SpeedController
import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.SettingsManager
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
import dji.sdk.keyvalue.value.flightcontroller.FailsafeAction
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var perceptionController: PerceptionController
    @Inject lateinit var safetyController: SafetyController

    @Inject lateinit var speedController: SpeedController

    @Inject lateinit var droneStateTracker: DroneStateTracker

    @Inject lateinit var settingsManager: SettingsManager

    @Inject lateinit var rosBridgeManager: ROSBridgeManager

    private var isInitialSelection = true
    private var speedLevel: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        setupAvoidanceToggle()
        setupFailsafeActionSpinner()
        setupSpeedMenu()
        setupIpAddressField()
    }

    override fun onResume() {
        super.onResume()

        val obstacleAvoidanceEnabled = settingsManager.getObstacleAvoidanceEnabled()
        val obstacleAvoidanceSwitch = findViewById<SwitchCompat>(R.id.avoidanceSwitch)
        obstacleAvoidanceSwitch.setChecked(obstacleAvoidanceEnabled)

        val speedLevel = settingsManager.getSpeedLevel()
        val speedToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.speedToggleGroup)
        speedToggleGroup.check(speedLevel)

        // TODO - check if setting works properly after resume, does it remember previous position?
        val failsafeAction = settingsManager.getFailsafeAction()
        val failsafeActionSpinner = findViewById<Spinner>(R.id.failsafeActionSpinner)
        failsafeActionSpinner.setSelection(failsafeAction)
    }

    private fun setupAvoidanceToggle() {
        val avoidanceSwitch = findViewById<SwitchCompat>(R.id.avoidanceSwitch)
        avoidanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            perceptionController.setObstacleAvoidance(isChecked)
            avoidanceSwitch.setChecked(settingsManager.getObstacleAvoidanceEnabled())
        }
    }

    // TODO - remember the set value after leaving the activity, the same as in other two attributes
    private fun setupFailsafeActionSpinner() {
        val spinner = findViewById<Spinner>(R.id.failsafeActionSpinner)
        val actions = listOf(FailsafeAction.GOHOME, FailsafeAction.HOVER, FailsafeAction.LANDING)
        
        val adapter = ArrayAdapter(this, R.layout.spinner_item, actions.map { it.name })
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }
                safetyController.setConnectionLostAction(actions[position])
                spinner.setSelection(settingsManager.getFailsafeAction())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSpeedMenu() {
        val speedToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.speedToggleGroup)
        speedToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                speedLevel = when (checkedId) {
                    R.id.speedLevel1 -> 1
                    R.id.speedLevel2 -> 2
                    R.id.speedLevel3 -> 3
                    else -> 1
                }
                speedController.setSpeedLevel(speedLevel)
                val checkedBtnId = speedToggleGroup.checkedButtonId
                settingsManager.setSpeedLevel(checkedBtnId)
            }
        }
    }

    private fun isValidIp(ip: String): Boolean {
        return ip.isNotEmpty() && ip.split(".").size == 4
    }

    private fun setupIpAddressField() {
        val ipEditText = findViewById<EditText>(R.id.rosIpEditText)
        val connectButton = findViewById<Button>(R.id.connectButton)

        ipEditText.setText(settingsManager.getRosIp())

        connectButton.setOnClickListener {
            val newIp = ipEditText.text.toString().trim()
            if (isValidIp(newIp)) {
                settingsManager.setRosIp(newIp)
                rosBridgeManager.disconnect()
                rosBridgeManager.connect()
            } else {
                Toast.makeText(this, "Invalid IP Address", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
