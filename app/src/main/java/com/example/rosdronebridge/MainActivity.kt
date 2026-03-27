package com.example.rosdronebridge

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rosdronebridge.factories.ROSBridgeVMFactory
import com.example.rosdronebridge.models.BasicAircraftControlVM
import com.example.rosdronebridge.models.DroneController
import com.example.rosdronebridge.models.ROSBridgeClientVM
import com.example.rosdronebridge.models.SimulatorController
import com.example.rosdronebridge.models.VirtualStickVM
import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.ROSMessageParser
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val virtualStickVM: VirtualStickVM by globalViewModels()
    private val rosMessageParser = ROSMessageParser()
    private val rosBridgeClientVM: ROSBridgeClientVM by viewModels{
        ROSBridgeVMFactory(rosMessageParser)
    }
    private val basicAircraftControlVM: BasicAircraftControlVM by globalViewModels()
    private val droneStateTracker = DroneStateTracker() // TODO - should be singleton

    private lateinit var droneController: DroneController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        droneController = DroneController(
            basicAircraftControlVM,
            virtualStickVM,
            rosBridgeClientVM,
            lifecycleScope,
            droneStateTracker
        )

        val simulatorController = SimulatorController()

        observeMessages()
        observeMSDKManager()
        rosBridgeClientVM.connect()

        lifecycleScope.launch {
            droneStateTracker.droneState.collect { droneState ->
                // Comment out simulator check when intention is to actually fly.
                if (droneState.connected && simulatorController.isSimulatorEnabled())
                    droneController.enableVirtualStick()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        droneController.disableVirtualStick()
        // disable DJI Key listeners on app destroy
        droneStateTracker.clear()
    }

    override fun onPause() {
        super.onPause()
        droneController.onAppBackgrounded()
    }

    private fun observeMessages() {
        val messageView = findViewById<TextView>(R.id.ROSMessage)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rosBridgeClientVM.message.collect { rosMessage ->
                    messageView.text = rosMessage?.message as CharSequence?
                }
            }
        }
    }

    private fun observeMSDKManager() {
        val statusText = findViewById<TextView>(R.id.statusText)

        msdkManagerVM.registerState.observe(this) { resultPair ->
            if (resultPair.first) {
                statusText.text = "Register Success"
                Toast.makeText(this, "Register Success", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Register Failed"
                Toast.makeText(this, "Register Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
