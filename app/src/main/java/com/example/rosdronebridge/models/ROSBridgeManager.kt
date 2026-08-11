package com.example.rosdronebridge.models

import android.util.Log
import com.example.rosdronebridge.data.DroneState
import com.example.rosdronebridge.data.ROSMessage
import com.example.rosdronebridge.di.ApplicationScope
import com.example.rosdronebridge.util.ROSMessageParser
import com.example.rosdronebridge.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ROSBridgeManager @Inject constructor(
    private val parser: ROSMessageParser,
    private val settingsManager: SettingsManager,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val _message = MutableSharedFlow<ROSMessage?>()
    val message = _message.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect() {
        if (_isConnected.value) return

        Log.d("ROSBridgeManager", "Connecting to ROS bridge")
        val ip = settingsManager.getRosIp()
        val request = Request.Builder()
            .url("ws://$ip:9090") // ws://192.168.1.225:9090
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                subscribeToRosTopic("/drone/basic_command", "std_msgs/String")
                subscribeToRosTopic("/drone/velocity_command", "geometry_msgs/Twist")
                advertiseRosTopic("/drone/telemetry", "std_msgs/String")
                advertiseRosTopic("/drone/state", "std_msgs/String")
                advertiseRosTopic("/drone/logs", "std_msgs/String")
                advertiseRosTopic("/drone/receivedMsg", "std_msgs/String")
                advertiseRosTopic("/camera/image_raw/compressed", "h264")
                advertiseRosTopic("/drone/gimbal/feedback", "std_msgs/String")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val rosMessage = parser.parseRosCommand(text)
                appScope.launch {
                    _message.emit(rosMessage)
                }
                if (rosMessage?.topic == "/drone/basic_command") {
                    logToRos("receivedMsg", "ROSBridgeManager", "msg: ${rosMessage?.payload}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                Log.d("ROSBridgeManager", "WebSocket failure: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }
        })
    }

    private fun subscribeToRosTopic(topic: String, type: String) {
        if (!_isConnected.value) return

        val subscribeCommand = """
            {
              "op": "subscribe",
              "topic": "$topic",
              "type": "$type"
            }
        """

        if (webSocket?.send(subscribeCommand) == true) {
            Log.d("ROSBridgeManager", "Subscribed to ROS topic: drone_commands")
        } else {
            Log.d("ROSBridgeManager", "Failed to subscribe to ROS topic: drone_commands")
        }
    }

    // Used to create a ROS topic which will enable publishing of drone telemetry/state to ROS
    private fun advertiseRosTopic(topic: String, type: String) {
        val advertiseCommand = """
            {
              "op": "advertise",
              "topic": "$topic",
              "type": "$type"
            }
            """
        webSocket?.send(advertiseCommand)
    }

    fun publish(topic: String, message: String) {
        if (!_isConnected.value) return

        val json = """
            {
              "op": "publish",
              "topic": "$topic",
              "msg": $message
            }
            """

        webSocket?.send(json)
    }

    fun publishTelemetry(droneState: DroneState) {
        if (!_isConnected.value) {
            Log.d("ROSBridgeManager fail", "Failed to publish telemetry data")
            return
        }

        logToRos("telemetry", "TelemetryPublisher",
            "Publishing telemetry data, droneState.altitude: ${droneState.altitude}")

        webSocket?.send(parser.parseTelemetryData(droneState))
        webSocket?.send(parser.parseDroneState(droneState))
    }

    fun logToRos(topic: String, level: String, message: String) {
//        val logMsg = """{"level": "$level", "msg": "$message"}"""
//        publish("/drone/logs", logMsg)
        // Wrap in "data" field to match std_msgs/String structure
        val logContent = "${System.currentTimeMillis()}: [$level] $message"
        val jsonMsg = """{ "data": "$logContent" }"""
        publish("/drone/$topic", jsonMsg)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
    }
}