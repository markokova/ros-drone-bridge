package com.example.rosdronebridge.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("bridge_prefs", Context.MODE_PRIVATE)
    private val udpPort = prefs.getInt("udp_port", 5000)

    private var speedLevel = prefs.getInt("speed_level", 1)

    private var obstacleAvoidanceEnabled = prefs.getBoolean("obstacle_avoidance_enabled", false)

    private var failSafeAction = prefs.getInt("failsafe_action", 0)


    fun getRosIp(): String = prefs.getString("ros_ip", "192.168.1.15") ?: "192.168.1.15"

    fun setRosIp(ip: String) {
        prefs.edit().putString("ros_ip", ip).apply()
    }

    fun getUdpPort(): Int = udpPort

    fun setUdpPort(port: Int) {
        prefs.edit().putInt("udp_port", port).apply()
    }

    fun getSpeedLevel(): Int = speedLevel

    fun setSpeedLevel(level: Int) {
        speedLevel = level
        prefs.edit().putInt("speed_level", level).apply()
    }

    fun getObstacleAvoidanceEnabled(): Boolean = obstacleAvoidanceEnabled

    fun setObstacleAvoidanceEnabled(enabled: Boolean) {
        obstacleAvoidanceEnabled = enabled
        prefs.edit().putBoolean("obstacle_avoidance_enabled", enabled).apply()
    }

    fun getFailsafeAction(): Int = failSafeAction

    fun setFailsafeAction(action: Int) {
        failSafeAction = action
        prefs.edit().putInt("failsafe_action", action).apply()
    }
}