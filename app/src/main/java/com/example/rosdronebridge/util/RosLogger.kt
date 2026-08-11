package com.example.rosdronebridge.util

import com.example.rosdronebridge.models.ROSBridgeManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RosLogger @Inject constructor(
    private val rosBridgeManager: ROSBridgeManager
) {

    fun log(topic: String, component: String, message: String) {
        rosBridgeManager.logToRos(topic, component, message)
    }
}