package com.example.rosdronebridge.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rosdronebridge.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ROSBridgeClientVM : ViewModel() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect() {
        val request = Request.Builder()
            .url("ws://192.168.1.15:9090") //ws://192.168.1.225:9090
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                addMessage("Connected to Server", false)
                subscribeToDroneCommands()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                viewModelScope.launch {
                    addMessage(text, false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                viewModelScope.launch {
                    _isConnected.value = false
                    addMessage("Connection failed ${t.message}\"", false)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                addMessage("Connection closing $reason", false)
            }
        })
    }

    private fun subscribeToDroneCommands() {
        val text = "{\n" +
                "  \"op\": \"subscribe\",\n" +
                "  \"topic\": \"drone_commands\",\n" +
                "  \"type\": \"std_msgs/msg/String\"\n" +
                "}"

        if (_isConnected.value) {
            webSocket?.send(text)
            addMessage(text, true)
        } else {
            addMessage("Not connected", false)
        }
    }

    fun sendMessage(text: String) {
        if (_isConnected.value) {
            webSocket?.send(text)
            addMessage(text, true)
        } else {
            addMessage("Not connected", false)
        }
    }

    private fun addMessage(messageText: String, isSentByUser: Boolean) {
        _messages.update { current ->
            val nextId = (current.lastOrNull()?.id ?: 0) + 1
            val message = Message(nextId, messageText, isSentByUser)
            current + message
        }
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