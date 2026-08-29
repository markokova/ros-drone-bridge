package com.example.rosdronebridge

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rosdronebridge.data.StringPayload
import com.example.rosdronebridge.data.VelocityPayload
import com.example.rosdronebridge.models.BasicAircraftControlManager
import com.example.rosdronebridge.models.VirtualStickController
import com.example.rosdronebridge.models.PerceptionController
import com.example.rosdronebridge.models.ROSBridgeManager
import com.example.rosdronebridge.models.ROSMessageHandler
import com.example.rosdronebridge.models.SimulatorController
import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.SettingsManager
import com.example.rosdronebridge.util.UDPVideoStreamer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import dji.sdk.keyvalue.key.ProductKey
import dji.v5.et.create
import dji.v5.et.listen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.RoundingMode
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var onLongClickListener: () -> Boolean
    @Inject lateinit var msdkManager: MSDKManager
    @Inject lateinit var simulatorController: SimulatorController
    @Inject lateinit var rosBridgeManager: ROSBridgeManager
    @Inject lateinit var droneStateTracker: DroneStateTracker
    @Inject lateinit var basicAircraftControlManager: BasicAircraftControlManager

    @Inject lateinit var virtualStickController: VirtualStickController
    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var udpVideoStreamer: UDPVideoStreamer
    @Inject lateinit var perceptionController: PerceptionController

    @Inject lateinit var rosMessageHandler: ROSMessageHandler


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.statusBarColor = getColor(R.color.white)

        setupSettingsButton()
        setupVirtualStickButton()
        setupGoHomeButton()
        setupVirtualStickInfoButton()
        observeDroneState()

        rosBridgeManager.connect()

        lifecycleScope.launch {
            rosBridgeManager.isConnected.collect { isConnected ->
                if (isConnected) {
                    udpVideoStreamer.start()
                } else {
                    udpVideoStreamer.stop()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        virtualStickController.onAppBackgrounded()
    }

    private fun observeMessages() {
        val velocityMsgTopic = findViewById<TextView>(R.id.ROSMessageTopic)
        val velocityMsgData = findViewById<TextView>(R.id.ROSMessageData)
        val velocityMsgTimestamp = findViewById<TextView>(R.id.ROSMessageTimestamp)

        val basicMsgTopic = findViewById<TextView>(R.id.ROSBasicTopic)
        val basicMsgData = findViewById<TextView>(R.id.ROSBasicData)
        val basicMsgTimestamp = findViewById<TextView>(R.id.ROSBasicTimestamp)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rosBridgeManager.message.collect { rosMessage ->
                    val formattedContent = when (val payload = rosMessage?.payload) {
                        is StringPayload -> payload.message
                        is VelocityPayload ->
                            "vx: ${payload.x.toBigDecimal().setScale(2, RoundingMode.HALF_UP)}\n" +
                            "vy: ${payload.y.toBigDecimal().setScale(2, RoundingMode.HALF_UP)}\n" +
                            "vz: ${payload.z.toBigDecimal().setScale(2, RoundingMode.HALF_UP)}\n" +
                            "yaw: ${payload.yaw.toBigDecimal().setScale(2, RoundingMode.HALF_UP)}\n" +
                            "gimbalPitch: ${payload.gimbalPitch.toBigDecimal().setScale(2, RoundingMode.HALF_UP)}\n" +
                            "gimbalYaw: ${payload.gimbalYaw.toBigDecimal().setScale(2, RoundingMode.HALF_UP)}"
                        else -> "Waiting for data..."
                    }

                    val timeOnly = rosMessage?.timestamp.toString().substringAfter(" ")

                    if (rosMessage?.topic == "/drone/cmd_vel") {
                        velocityMsgTopic.text = rosMessage?.topic ?: "N/A"
                        velocityMsgData.text = formattedContent
                        velocityMsgTimestamp.text = timeOnly ?: "--"
                    }
                    else if (rosMessage?.topic == "/drone/basic_command") {
                        basicMsgTopic.text = rosMessage.topic ?: "N/A"
                        basicMsgData.text = formattedContent
                        basicMsgTimestamp.text = timeOnly ?: "--"
                    }
                }
            }
        }
    }


    private fun observeMSDKManager() {
        val statusText = findViewById<TextView>(R.id.statusText)

        msdkManager.registerState.observe(this) { resultPair ->
            if (resultPair.first) {
                msdkManager.loginAccount(this)
                statusText.text = "REGISTER SUCCESS"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.light_blue))
            } else {
                val errorMsg = resultPair.second?.toString() ?: "Unknown Error"
                statusText.text = "REGISTER FAILED: $errorMsg"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
        }

        msdkManager.loginState.observe(this) { resultPair ->
            if (resultPair.first) {
                statusText.text = "APP LOGIN SUCCESS"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.green))
                ProductKey.KeyConnection.create().listen(this) { connected ->
                    if (connected == true) {
                        droneStateTracker.initStateListeners()
                    }
                }
            } else {
                val errorMsg = resultPair.second?.toString() ?: "Unknown Error"
                statusText.text = "REGISTER & LOGIN FAILED: $errorMsg"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
        }
    }

    private fun observeDroneConnection() {
        val droneStatusText = findViewById<TextView>(R.id.droneStatusText)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                droneStateTracker.droneState
                    .map { it.connected }
                    .distinctUntilChanged()
                    .collect { isConnected ->
                        if (isConnected) {
                            droneStatusText.text = "CONNECTED"
                            droneStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.green))
                        } else {
                            droneStatusText.text = "DISCONNECTED"
                            droneStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                        }
                    }
            }
        }
    }

    private fun observeFlightMode() {
        val flightModeText = findViewById<TextView>(R.id.flightModeText)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                droneStateTracker.droneState.collect { state ->
                    flightModeText.text = state.flightMode?.name ?: "NOT DETECTED"
                }
            }
        }
    }

    private fun observeSimulatorStatus() {
        val simulatorStatusText = findViewById<TextView>(R.id.simulatorStatusText)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                simulatorController.isSimulatorActive.collect { isActive ->
                    if (isActive) {
                        simulatorStatusText.text = "ON"
                        simulatorStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.green))
                    } else {
                        simulatorStatusText.text = "OFF"
                        simulatorStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.red))
                    }
                }
            }
        }
    }

    private fun observeBatteryState() {
        val batteryText = findViewById<TextView>(R.id.batteryText)
        val batteryIcon = findViewById<ImageView>(R.id.batteryIcon)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                droneStateTracker.droneState
                    .map { it.batteryChargeRemaining }
                    .distinctUntilChanged()
                    .collect { charge ->
                        if (charge != null) {
                            batteryText.text = "$charge%"
                            val colorRes = when {
                                charge > 50 -> R.color.green
                                charge > 20 -> R.color.light_blue // Use light blue for medium since we lack yellow/orange
                                else -> R.color.red
                            }
                            batteryIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, colorRes))
                        } else {
                            batteryText.text = "--%"
                            batteryIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.gray))
                        }
                    }
            }
        }
    }

    private fun setupVirtualStickButton() {
        val vsButton = findViewById<FloatingActionButton>(R.id.btnEnableVirtualStick)
        vsButton.setOnClickListener {
            if (virtualStickController.isReadyForVirtualStick()) {
                virtualStickController.enableVirtualStick()
                Toast.makeText(this, "Requesting Virtual Stick Authority...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Drone not ready for Virtual Stick", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupGoHomeButton() {
        val vsButton = findViewById<FloatingActionButton>(R.id.btnGoHome)
        vsButton.setOnClickListener {
            if (droneStateTracker.droneState.value.isFlying) {
                basicAircraftControlManager.goHome()
            }
        }
    }

    private fun setupVirtualStickInfoButton() {
        val vsButton = findViewById<FloatingActionButton>(R.id.btnVirtualStickInfo)
        vsButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Virtual Stick")
                .setMessage(
                    "Activate Virtual Stick.\n\n" +
                            "Activating Virtual Stick gives the app direct control over the drone.\n" +
                            "If the remote controller takes control, you can use this button to regain authority.\n" +
                            "Virtual Stick works only when the RC is in NORMAL mode."

                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setupSettingsButton() {
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeDroneState() {
        observeMessages()
        observeMSDKManager()
        observeDroneConnection()
        observeFlightMode()
        observeSimulatorStatus()
        observeBatteryState()
    }
}
