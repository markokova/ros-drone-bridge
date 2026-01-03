package com.example.rosbridgeprototype.model

data class Message(
    val text: String,
    val isSentByUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
