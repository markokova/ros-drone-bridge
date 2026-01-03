package com.example.rosbridgeprototype

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rosbridgeprototype.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketViewModel : ViewModel() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> get() = _messages
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect() {
        val request = Request.Builder()
            .url("ws://192.168.1.225:9090") //ws://192.168.1.225:9090
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                _messages.add(Message("Connected to server", false))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                viewModelScope.launch {
                    _messages.add(Message(text, false))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                viewModelScope.launch {
                    _isConnected.value = false
                    _messages.add(Message("Connection failed: ${t.message}", false))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                _messages.add(Message("Connection closing: $reason", false))
            }
        })
    }

    fun sendMessage(text: String) {
        if (_isConnected.value) {
            webSocket?.send(text)
            _messages.add(Message(text, true))
        } else {
            _messages.add(Message("Not connected", false))
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