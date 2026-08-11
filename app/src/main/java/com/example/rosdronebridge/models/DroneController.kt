//package com.example.rosdronebridge.models
//
//import android.util.Log
//import androidx.lifecycle.viewmodel.viewModelFactory
//import com.example.rosdronebridge.data.ROSMessage
//import com.example.rosdronebridge.data.StringPayload
//import com.example.rosdronebridge.data.VelocityPayload
//import com.example.rosdronebridge.di.ApplicationScope
//import com.example.rosdronebridge.util.DroneStateTracker
//import com.example.rosdronebridge.util.TelemetryPublisher
//import dji.sdk.keyvalue.key.FlightControllerKey
//import dji.sdk.keyvalue.value.common.EmptyMsg
//import dji.sdk.keyvalue.value.flightcontroller.FCFlightMode
//import dji.sdk.keyvalue.value.flightcontroller.FlightControlAuthority
//import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
//import dji.sdk.keyvalue.value.flightcontroller.FlightMode
//import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
//import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
//import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
//import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
//import dji.v5.common.callback.CommonCallbacks
//import dji.v5.common.error.IDJIError
//import dji.v5.common.callback.CommonCallbacks.CompletionCallbackWithParam
//import dji.v5.et.create
//import dji.v5.et.listen
//import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.distinctUntilChanged
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//import javax.inject.Singleton
//import kotlin.math.abs
//
//@Singleton
//class DroneController @Inject constructor(
//    private val basicAircraftControlManager: BasicAircraftControlManager,
//    private val virtualStickVM: VirtualStickVM,
//    private val rosBridgeManager: ROSBridgeManager,
//    private val telemetryPublisher: TelemetryPublisher,
//    @ApplicationScope private val coroutineScope: CoroutineScope,
//    private val stateTracker : DroneStateTracker,
//    private val simulatorController: SimulatorController,
//    private val gimbalController: GimbalController
//) {
////
////    private val vsParam = VirtualStickFlightControlParam().apply {
////        rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
////        verticalControlMode = VerticalControlMode.VELOCITY
////        yawControlMode = YawControlMode.ANGULAR_VELOCITY
////        rollPitchControlMode = RollPitchControlMode.VELOCITY
////    }
//
//    // Thread-safe variables acting as a global cache
//    @Volatile private var targetRoll = 0.0
//    @Volatile private var targetPitch = 0.0
//    @Volatile private var targetYawRate = 0.0
//    @Volatile private var targetVerticalThrottle = 0.0
//    private var virtualStickActivated = false
//    private var advancedModeReady = false
//    private var activationTime = 0L
//    private var lastRosCommandTime = 0L
//
//    companion object {
//        private const val ROS_TIMEOUT_MS = 150L
//        private const val GRACE_PERIOD = 1500L // To allow for DJI SDK to sync
//        private const val MAX_VXY = 5.0
//        private const val MAX_VZ = 2.0
//        private const val MAX_YAW_RATE = 90.0
//    }
//
//    init {
//        telemetryPublisher.start(coroutineScope)
//
//        coroutineScope.launch {
//            rigidFlightPipelineClock()
//        }
//
//        coroutineScope.launch {
//            rosBridgeManager.message.collect { message ->
//                handleRosMessage2(message)
//            }
//        }
//
//        coroutineScope.launch {
//            watchdogLoop()
//        }
//
//        // FlightControllerKey.getKeyList()[0]
//
////        FlightControllerKey.KeyFlightMode.create().listen(this) { mode ->
////            if (mode != null && mode != FlightMode.AUTO_TAKE_OFF && !virtualStickActivated) {
////                rosBridgeManager.logToRos("state", "DroneController", "Flight mode: ${mode.value()}")
////                enableVirtualStick()
////            }
////        }
//
//        FlightControllerKey.KeyFlightControlCurrentAuthority.create().listen(this) { authority ->
//            rosBridgeManager.logToRos("state", "DroneController", "authority:$authority")
//        }
//
//        FlightControllerKey.KeyVirtualStickControlModeEnabled.create().listen(this) { enabled ->
//            rosBridgeManager.logToRos("state", "DroneController", "vsEnabled:$enabled")
//        }
//
//        virtualStickVM.currentVirtualStickStateInfo.observeForever {
//            rosBridgeManager.logToRos("state", "DroneController", "vsState:${it.state}, reason:${it.reason}")
//        }
//
//        coroutineScope.launch {
//            stateTracker.droneState
//                .map { it.flightMode }
//                .distinctUntilChanged()
//                .collect { mode ->
//                    val isFlying = stateTracker.droneState.value.isFlying
//                    rosBridgeManager.logToRos("state", "DroneController","Flight mode:$mode, isFlying:$isFlying")
//                    if (isFlying && mode != FlightMode.AUTO_TAKE_OFF &&
//                        mode != FlightMode.AUTO_LANDING  && mode != FlightMode.GO_HOME &&
//                        !virtualStickActivated) {
//                        rosBridgeManager.logToRos("logs", "DroneController.enableVS0()",
//                            "Stable flight detected ($mode)")
//                        enableVirtualStick()
//                    }
//                }
//        }
//
//
//        /**
//        When Virtual Stick has flight authority ownership ROS drone control is enabled. Flight
//        authority can change to Manual Remote Control/Automation controls motion(Go home/Auto
//        landing triggered)/Intelligent flight mode etc. In that case Virtual stick control must be
//        disabled. GRACE_PERIOD gives time for SDK to sync so that emergencyStop isn't called to soon
//        which would cause VirtualStick never being activated.
//         **/
//        coroutineScope.launch {
//            stateTracker.droneState.collect { state ->
//                val now = System.currentTimeMillis()
//                if (virtualStickActivated && (now - activationTime > GRACE_PERIOD)) {
//                    val hasAuthority = state.isVirtualStickEnabled &&
//                            state.flightMode == FlightMode.VIRTUAL_STICK
//                    if (!hasAuthority) {
////                        rosBridgeManager.logToRos("logs", "DroneController", "Authority lost or failed to engage. Mode: ${state.flightMode}")
//                        // emergencyStop("Authority lost or failed to engage. Mode: ${state.flightMode}")
//                    }
//                }
//            }
//        }
//
////        coroutineScope.launch {
////            stateTracker.droneState
////                .map { it.takeoffError }
////                .collect { error ->
////                    if (error != null) {
////                        rosBridgeManager.logToRos("logs", "DroneController", "HARDWARE ERROR: $error")
////                    }
////                }
////        }
//    }
//
//    fun takeoff() {
//        val droneState = stateTracker.droneState.value
//        // TODO - does satelliteCount needs to be checked?
//
//        val isSimulatorEnabled = simulatorController.isSimulatorActive.value
//        val hasGpsLock = true //(droneState.satelliteCount ?: 0) >= 10 && abs(droneState.latitude) > 0.1
//
//        if (isSimulatorEnabled && abs(droneState.latitude) < 0.0001) {
//            rosBridgeManager.logToRos("logs", "DroneController", "Takeoff aborted: Simulator coordinates not synced yet.")
//            return
//        }
//
//        val isReady = droneState.connected && !droneState.motorsOn && !droneState.isFlying &&
//                (hasGpsLock || isSimulatorEnabled)
//        Log.d("DroneController", "Takeoff: $isReady. Connected: ${droneState.connected}, motorsOn: ${droneState.motorsOn}, isFlying: ${droneState.isFlying}, satelliteCount: ${droneState.satelliteCount}")
//        rosBridgeManager.logToRos("state", "DroneController:takeoff()","Takeoff: $isReady. Connected: ${droneState.connected}, motorsOn: ${droneState.motorsOn}, " +
//                "isFlying: ${droneState.isFlying}, satelliteCount: ${droneState.satelliteCount}, " +
//                "isSimulatorEnabled:" + isSimulatorEnabled + ", hasGpsLock: $hasGpsLock")
//        rosBridgeManager.logToRos("state", "DroneController:takeoff()2", "isSimulatorEnabled:" + isSimulatorEnabled + ", hasGpsLock: $hasGpsLock")
//        Log.d("DroneController", "Takeoff Check: Latt=${droneState.latitude}, Sats=${droneState.satelliteCount}, Sim=$isSimulatorEnabled")
//
//        if (!isReady) return
//
//        basicAircraftControlManager.startTakeOff(object : CompletionCallbackWithParam<EmptyMsg> {
//            override fun onSuccess(t: EmptyMsg?) {
//                rosBridgeManager.logToRos("logs", "DroneController:takeoff()","Takeoff successful, engaging Virtual Stick...")
//                coroutineScope.launch {
//                    // Wait for the drone to actually start flying
//                    while (!stateTracker.droneState.value.isFlying) {
//                        delay(200)
//                    }
//
////                    // Wait for flight mode to switch out of AUTO_TAKE_OFF
////                    while (stateTracker.droneState.value.flightMode == FlightMode.AUTO_TAKE_OFF ||
////                        stateTracker.droneState.value.flightMode == null) {
////                        delay(200)
////                    }
////
////                    enableVirtualStick()
//                }
//            }
//            override fun onFailure(error: IDJIError) {
//                Log.e("DroneController", "Takeoff failed: \n$error")
//                rosBridgeManager.logToRos("logs", "DroneController:takeoff()", "Takeoff failed: $error")
//
//            }
//        })
//    }
//
//    fun land() {
//        setZeroVelocity()
//        disableVirtualStick()
//        basicAircraftControlManager.startLanding(object : CompletionCallbackWithParam<EmptyMsg> {
//            override fun onSuccess(t: EmptyMsg?) {
//                Log.d("DroneController", "Landing initiated")
//                rosBridgeManager.logToRos("logs", "DroneController:land()", "Landing initiated")
//            }
//            override fun onFailure(error: IDJIError) {
//                Log.e("DroneController", "Landing failed: $error")
//                rosBridgeManager.logToRos("logs", "DroneController:land()", "Landing failed: $error")
//            }
//        })
//    }
//
//    private fun handleRosMessage2(command: ROSMessage?) {
//        when (command?.topic) {
//            "/drone/basic_command" -> {
//                val payload = command.payload as? StringPayload ?: return
//                when (payload.message) {
//                    "takeoff" -> takeoff()
//                    "land" -> land()
//                }
//            }
//            "/drone/velocity_command" -> {
//                if (command.payload is VelocityPayload) {
//                    lastRosCommandTime = System.currentTimeMillis()
//
//                    // DO NOT write to DJI SDK here. Simply cache the latest instructions.
//                    targetRoll = command.payload.x.coerceIn(-MAX_VXY, MAX_VXY)
//                    targetPitch = command.payload.y.coerceIn(-MAX_VXY, MAX_VXY)
//                    targetVerticalThrottle = command.payload.z.coerceIn(-MAX_VZ, MAX_VZ)
//                    targetYawRate = command.payload.yaw.coerceIn(-MAX_YAW_RATE, MAX_YAW_RATE)
//                }
//            }
//            "/gimbal/control" -> {
//                val payload = command.payload as? StringPayload ?: return
//                when (payload.message) {
//
//                }
//            }
//        }
//    }
//
//    fun handleRosMessage(command: ROSMessage?) {
//        when (command?.topic) {
//            "/drone/basic_command" -> {
//                val payload = command.payload as? StringPayload ?: return
//                when (payload.message) {
//                    "takeoff" -> takeoff()
//                    "land" -> land()
//                }
//                Log.d("DroneController", "Received ROS command: ${payload.message}")
//            }
//            "/drone/velocity_command" -> {
//                val payload = command.payload as? VelocityPayload ?: return
//                setVelocity(payload.x, payload.y, payload.z, payload.yaw)
//            }
//        }
//    }
//
//    fun enableVirtualStick() {
//        // Even if already enabled, reset activation time to extend grace period for state sync
//        activationTime = System.currentTimeMillis()
//
//        rosBridgeManager.logToRos("state", "DroneController",
//            "EnableVS123:${stateTracker.droneState.value.flightMode}, isFlying: ${stateTracker.droneState.value.isFlying}")
//
//        if (stateTracker.droneState.value.isVirtualStickEnabled) {
//            virtualStickActivated = true
//            return
//        }
//        if (!stateTracker.droneState.value.isFlying) return
//        if (stateTracker.droneState.value.flightMode != FlightMode.GPS_NORMAL) return
//        // TODO - do i need this VirtualStickManager init, should I use that or container class virtualStickVM?
//        // VirtualStickManager.getInstance().init()
//
//
//
//        virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
//            override fun onSuccess() {
//                activationTime = System.currentTimeMillis()
//                virtualStickActivated = true
//                rosBridgeManager.logToRos("state", "DroneController", "Virtual Stick authority granted")
//
//                virtualStickVM.enableVirtualStickAdvancedMode()
//
//                // 2. IMMEDIATE INJECTION: Send a blank advanced parameter right here
//                // to satisfy the firmware's strict immediate-packet check (+11ms window).
//                val bootstrapParam = VirtualStickFlightControlParam().apply {
//                    rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
//                    verticalControlMode = VerticalControlMode.VELOCITY
//                    yawControlMode = YawControlMode.ANGULAR_VELOCITY
//                    rollPitchControlMode = RollPitchControlMode.VELOCITY
//                    roll = 0.0
//                    pitch = 0.0
//                    yaw = 0.0
//                    verticalThrottle = 0.0
//                }
//                VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(bootstrapParam)
//
//                // Allow a small window for the MSDK thread context switch to finalize configuration
//                coroutineScope.launch {
//                    delay(200)
//                    advancedModeReady = true
//                    rosBridgeManager.logToRos("state", "DroneController", "VSAdvanced=ON")
//                }
//            }
//            override fun onFailure(error: IDJIError) {
//                Log.e("DroneController", "Failed to enable Virtual Stick: $error")
//                rosBridgeManager.logToRos("state", "DroneController", "Failed to enable VS: $error")
//                virtualStickActivated = false
//            }
//        })
//    }
//
//    fun disableVirtualStick() {
//        virtualStickActivated = false
//        virtualStickVM.disableVirtualStickAdvancedMode()
//        virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback {
//            override fun onSuccess() { Log.d("DroneController", "Virtual Stick disabled") }
//            override fun onFailure(error: IDJIError) { Log.e("DroneController", "Disable failure: $error") }
//        })
//    }
//
//    fun setVelocity(vx: Double, vy: Double, vz: Double, yawRateRad: Double) {
//        // rosBridgeManager.logToRos("logs", "set velocitz", "vx: $vx, vy: $vy, vz: $vz, yawRateRad: $yawRateRad")
//
//        val state = stateTracker.droneState.value
//        val isConnectedToRos = rosBridgeManager.isConnected.value
//
//        val velocityCmdNotApplicable = !isConnectedToRos || !state.isVirtualStickEnabled ||
//                !state.isFlying || state.flightMode != FlightMode.VIRTUAL_STICK
//        // val velocityCmdNotApplicable = !isConnectedToRos || !state.isVirtualStickEnabled
//
////        rosBridgeManager.logToRos("logs", "DroneController",
////            "isConnectedToRos: $isConnectedToRos, isFlying: ${state.isFlying}, " +
////                    "flightMode: ${state.flightMode}, " +
////                    "isVirtualStickEnabled: ${state.isVirtualStickEnabled}")
//
//        // Authority gate
//        if (velocityCmdNotApplicable) return
//
//        lastRosCommandTime = System.currentTimeMillis()
//
//        val vsParam = VirtualStickFlightControlParam().apply {
//            rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
//            verticalControlMode = VerticalControlMode.VELOCITY
//            yawControlMode = YawControlMode.ANGULAR_VELOCITY
//            rollPitchControlMode = RollPitchControlMode.VELOCITY
//
//            pitch = vx.coerceIn(-MAX_VXY, MAX_VXY)
//            roll = vy.coerceIn(-MAX_VXY, MAX_VXY)
//            verticalThrottle = vz.coerceIn(-MAX_VZ, MAX_VZ)
//            yaw = Math.toDegrees(yawRateRad*2).coerceIn(-MAX_YAW_RATE, MAX_YAW_RATE)
//        }
//
////        vsParam.pitch = vx.coerceIn(-MAX_VXY, MAX_VXY)
////        vsParam.roll = vy.coerceIn(-MAX_VXY, MAX_VXY)
////        vsParam.verticalThrottle = vz.coerceIn(-MAX_VZ, MAX_VZ)
////        vsParam.yaw = Math.toDegrees(yawRateRad).coerceIn(-MAX_YAW_RATE, MAX_YAW_RATE)
//
//        Log.d("DroneController123", "Setting velocity: $vsParam")
//
//        virtualStickVM.sendVirtualStickAdvancedParam(vsParam)
//
//        val vmSpeed = virtualStickVM.currentSpeedLevel.value
//        val vmStickVal = virtualStickVM.stickValue.value
////        rosBridgeManager.logToRos("logs", "DroneController", "VM STATS: $vmSpeed, $vmStickVal")
//    }
//
//    fun setZeroVelocity() {
////        if (!stateTracker.droneState.value.isVirtualStickEnabled) return
////        vsParam.pitch = 0.0; vsParam.roll = 0.0; vsParam.verticalThrottle = 0.0; vsParam.yaw = 0.0
////        virtualStickVM.sendVirtualStickAdvancedParam(vsParam)
//    }
//
//    private suspend fun watchdogLoop() {
//        while (true) {
//            delay(100)
//            if (!stateTracker.droneState.value.isVirtualStickEnabled || !advancedModeReady) continue
//            if (System.currentTimeMillis() - lastRosCommandTime > ROS_TIMEOUT_MS) {
//                setZeroVelocity()
//            }
//        }
//    }
//
//    fun emergencyStop(reason: String) {
//        setZeroVelocity()
//        disableVirtualStick()
//        Log.w("EMERGENCY_STOP", reason)
//    }
//
//    fun onAppBackgrounded() {
//        emergencyStop("App moved to background")
//    }
//
//    fun isReadyForVirtualStick() : Boolean {
//        return stateTracker.droneState.value.isFlying &&
//                (stateTracker.droneState.value.flightMode == FlightMode.GPS_NORMAL)
//    }
//
//    private suspend fun rigidFlightPipelineClock() {
//        while (true) {
//            try {
//                val now = System.currentTimeMillis()
//
//                if (virtualStickActivated && advancedModeReady) {
//                    // Network Safety Timeout: If Python stalls, cleanly default to a safe hover
//                    if (now - lastRosCommandTime > ROS_TIMEOUT_MS) {
//                        targetRoll = 0.0
//                        targetPitch = 0.0
//                        targetYawRate = 0.0
//                        targetVerticalThrottle = 0.0
//                    }
//
//                    // Generates an isolated immutable instance per hardware frame
//                    val controlPayload = VirtualStickFlightControlParam().apply {
//                        rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
//                        verticalControlMode = VerticalControlMode.VELOCITY
//                        yawControlMode = YawControlMode.ANGULAR_VELOCITY
//                        rollPitchControlMode = RollPitchControlMode.VELOCITY
//
//                        roll = targetRoll
//                        pitch = targetPitch
//                        yaw = targetYawRate
//                        verticalThrottle = targetVerticalThrottle
//                    }
//
//                    // Rigid hardware execution independent of ROS thread activity
//                    VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(controlPayload)
//                }
//            } catch (e: Exception) {
//                Log.e("DroneController", "Clock pipeline skip: ${e.message}")
//            }
//            delay(50) // Enforces a solid, unshakable 20Hz heartbeat
//        }
//    }
//}
