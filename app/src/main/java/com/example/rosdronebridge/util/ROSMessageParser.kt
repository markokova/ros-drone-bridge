package com.example.rosdronebridge.util

import android.util.Log
import com.example.rosdronebridge.data.DroneState
import com.example.rosdronebridge.data.GimbalPayload
import com.example.rosdronebridge.data.ROSMessage
import com.example.rosdronebridge.data.ROSPayload
import com.example.rosdronebridge.data.StringPayload
import com.example.rosdronebridge.data.VelocityPayload
import org.json.JSONObject
import java.sql.Timestamp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ROSMessageParser @Inject constructor() {
    fun parseRosCommand(raw: String): ROSMessage? {

        return try {
            val root = JSONObject(raw)

            val operation = root.getString("op")
            val topic = root.getString("topic")

            val msgObject = root.getJSONObject("msg")

            val payload: ROSPayload

            when (topic) {
                "/drone/basic_command" -> {
                    val message = msgObject.getString("data")
                    payload = StringPayload(message)
                    Log.d("PARSER:String", "payload: $payload")
                }
                "/drone/cmd_vel" -> {
                    //val message = root.getJSONObject("data")
                    val linearData = msgObject.getJSONObject("linear")
                    val angularData = msgObject.getJSONObject("angular")
                    payload = VelocityPayload(
                        linearData.getDouble("x"),
                        linearData.getDouble("y"),
                        linearData.getDouble("z"),
                        angularData.getDouble("z"),
                        angularData.getDouble("x"),
                        angularData.getDouble("y")
                    )
                    Log.d("PARSER:Velocity", "payload: $payload")
                }
                "/gimbal/control" -> {
                    val message = msgObject.getJSONObject("data")

                    payload = GimbalPayload(
                        message.getDouble("pitch"),
                        message.getDouble("yaw")
                    )
                    Log.d("PARSER:Gimbal", "payload: $payload")
                }
                else -> {
                    val message = msgObject.getString("data")
                    payload = StringPayload(message)
                }
            }

            ROSMessage(operation, topic, payload, Timestamp(System.currentTimeMillis()))

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseDroneState(droneState: DroneState) : String {
        return "{\n" +
                "  \"op\": \"publish\",\n" +
                "  \"topic\": \"/drone/state\",\n" +
                "  \"msg\": {\n" +
                "    \"connected\": ${droneState.connected},\n" +
                "    \"motorsOn\": ${droneState.motorsOn},\n" +
                "    \"isFlying\": ${droneState.isFlying},\n" +
                "    \"virtualStickAvailable\": ${droneState.isVirtualStickEnabled},\n" +
                "    \"flightMode\": \"${droneState.flightMode}\",\n" +
                "    \"satelliteCount\": ${droneState.satelliteCount},\n" +
                "    \"isHomeLocationSet\": ${droneState.isHomeLocationSet},\n" +
                "    \"isCompassInNormalState\": ${droneState.isCompassInNormalState}\n" +
                "    \"takeoffError\": \"${droneState.takeoffError}\""
                "  }\n" +
                "}"
    }
    fun parseTelemetryData(droneState: DroneState): String {
        val telemetryJson = JSONObject().apply {
            put("latitude", droneState.latitude)
            put("longitude", droneState.longitude)
            put("altitude", droneState.altitude)
            put("velocityX", droneState.velocityX)
            put("velocityY", droneState.velocityY)
            put("velocityZ", droneState.velocityZ)
            put("homeLocationType", droneState.homeLocationType.name)
            put("latitudeDistanceFromHome", droneState.latitudeDistFromHome)
            put("longitudeDistanceFromHome", droneState.longitudeDistFromHome)
        }

        return JSONObject().apply {
            put("op", "publish")
            put("topic", "/drone/telemetry")
            put("msg", JSONObject().apply {
                put("data", telemetryJson.toString())
            })
        }.toString()
    }

    fun parseVelocityData(message: JSONObject): VelocityPayload {
        val x = message.optDouble("x")
        val y = message.optDouble("y")
        val z = message.optDouble("z")
        val yaw = message.optDouble("yaw")
        val gimbalPitch = message.optDouble("gimbalPitch")
        val gimbalYaw = message.optDouble("gimbalYaw")

        return VelocityPayload(x, y, z, yaw, gimbalPitch, gimbalYaw)
    }
}