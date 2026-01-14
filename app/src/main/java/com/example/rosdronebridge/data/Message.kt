package com.example.rosdronebridge.data

data class Message(
    val id: Int,
    val text: String,
    val isSentByUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
