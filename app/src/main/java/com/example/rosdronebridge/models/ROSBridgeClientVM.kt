package com.example.rosdronebridge.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rosdronebridge.data.DroneState
import com.example.rosdronebridge.data.Message
import com.example.rosdronebridge.data.ROSMessage
import com.example.rosdronebridge.data.StringPayload
import com.example.rosdronebridge.util.ROSMessageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.sql.Timestamp

class ROSBridgeClientVM(
    private val parser: ROSMessageParser
) : ViewModel() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val _message = MutableSharedFlow<ROSMessage?>()
    val message = _message.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect() {
        val request = Request.Builder()
            .url("ws://192.168.1.15:9090") //ws://192.168.1.225:9090
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                viewModelScope.launch {
                    subscribeToDroneCommands()
                    advertiseTelemetry()
                    advertiseDroneState()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val rosMessage = parser.parseRosCommand(text)
                viewModelScope.launch {
                    _message.emit(rosMessage)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                viewModelScope.launch {
                    _isConnected.value = false
//                    addMessage("Connection failed ${t.message}\"", false)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }
        })
    }

    private suspend fun subscribeToDroneCommands() {
        val text = "{\n" +
                "  \"op\": \"subscribe\",\n" +
                "  \"topic\": \"drone_commands\",\n" +
                "  \"type\": \"std_msgs/msg/String\"\n" +
                "}"

        if (_isConnected.value) {
            if (webSocket?.send(text) == true) {
                _message.emit(ROSMessage(
                    "subscribe",
                    "drone_commands",
                    StringPayload("Subscribed to ROS topic: drone_commands"),
                    Timestamp(System.currentTimeMillis())
                ))
            } else {
                _message.emit(ROSMessage(
                    "subscribe",
                    "drone_commands",
                    StringPayload("Subscribe to ROS topic: fail"),
                    Timestamp(System.currentTimeMillis())
                ))
            }
        } else {
            _message.emit(ROSMessage(
                "subscribe",
                "drone_commands",
                StringPayload("Subscribe to ROS topic: fail - not connected to WebSocket"),
                Timestamp(System.currentTimeMillis())
            ))
        }
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
            Log.d("ROSBridgeClientVM fail", "Failed to publish telemetry data")
            return
        }

        webSocket?.send(parser.parseTelemetryData(droneState))
        webSocket?.send(parser.parseDroneState(droneState))
    }

    // Used to create a ROS topic which will enable publishing of drone telemetry to ROS
    fun advertiseTelemetry() {
        val json = """
            {
              "op": "advertise",
              "topic": "/drone/telemetry",
              "type": "std_msgs/String"
            }
            """
        webSocket?.send(json)
    }

    // Used to create a ROS topic which will enable publishing of drone state to ROS
    fun advertiseDroneState() {
        val json = """
            {
              "op": "advertise",
              "topic": "/drone/state",
              "type": "std_msgs/String"
            }
            """
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        client.dispatcher.executorService.shutdown()
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}