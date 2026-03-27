package com.example.rosdronebridge.util

import com.example.rosdronebridge.data.DroneState
import com.example.rosdronebridge.data.ROSMessage
import com.example.rosdronebridge.data.ROSPayload
import com.example.rosdronebridge.data.StringPayload
import com.example.rosdronebridge.data.VelocityPayload
import org.json.JSONObject
import java.sql.Timestamp

class ROSMessageParser {
    fun parseRosCommand(raw: String): ROSMessage? {

        return try {
            val root = JSONObject(raw)

            val operation = root.getString("op")
            val topic = root.getString("topic")

            val msgObject = root.getJSONObject("msg")

            val payload: ROSPayload

            when (topic) {
                "basic_command" -> {
                    val message = msgObject.getString("data")
                    payload = StringPayload(message)
                }
                "velocity_command" -> {
                    val message = root.getJSONObject("data")
                    payload = parseVelocityData(message)
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
                "  \"topic\": \"drone/state\",\n" +
                "  \"msg\": {\n" +
                "    \"connected\": ${droneState.connected},\n" +
                "    \"motorsOn\": ${droneState.motorsOn},\n" +
                "    \"isFlying\": ${droneState.isFlying},\n" +
                "    \"virtualStickAvailable\": ${droneState.virtualStickAvailable},\n" +
                "    \"flightMode\": \"${droneState.flightMode}\",\n" +
                "    \"satelliteCount\": ${droneState.satelliteCount},\n" +
                "    \"isHomeLocationSet\": ${droneState.isHomeLocationSet},\n" +
                "    \"isCompassInNormalState\": ${droneState.isCompassInNormalState},\n" +
                "  }\n" +
                "}"
    }
    fun parseTelemetryData(droneState: DroneState) : String {
         return "{\n" +
                "  \"op\": \"publish\",\n" +
                "  \"topic\": \"drone/telemetry\",\n" +
                "  \"msg\": {\n" +
                "    \"latitude\": ${droneState.latitude},\n" +
                "    \"longitude\": ${droneState.longitude},\n" +
                "    \"altitude\": ${droneState.altitude},\n" +
                "    \"velocityX\": ${droneState.velocityX},\n" +
                "    \"velocityY\": ${droneState.velocityY},\n" +
                "    \"velocityZ\": ${droneState.velocityZ}\n" +
                "  }\n" +
                "}"
    }

    fun parseVelocityData(message: JSONObject): VelocityPayload {
        val x = message.getString("x").toDouble()
        val y = message.getString("y").toDouble()
        val z = message.getString("z").toDouble()
        val yaw = message.getString("yaw").toDouble()

        return VelocityPayload(x, y, z, yaw)
    }
}