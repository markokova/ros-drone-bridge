package com.example.rosdronebridge.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.rosdronebridge.models.ROSBridgeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.et.create
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.camera.CameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UDPVideoStreamer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val rosBridgeManager: ROSBridgeManager
    ) {

    private var socket: DatagramSocket? = null
    private var networkExecutor: ExecutorService? = null
    private var isStreaming = false

    // Maintain a local variable to cache network metadata and avoid high-speed disk reads
    private var targetIp: InetAddress? = null
    private var targetPort: Int = -1

    private val streamListener = ICameraStreamManager.ReceiveStreamListener { data, offset, length, info ->
        if (!isStreaming) return@ReceiveStreamListener

        // SAFETY CHECK: Prevent Android from dropping or crashing on huge I-frames
        if (length > 65507) {
            Log.w("UDPVideoStreamer", "Frame size ($length bytes) exceeds UDP packet limits. Dropping frame.")
            return@ReceiveStreamListener
        }

        // Push the raw H.264 data frames straight to the background thread without allocation overhead
        networkExecutor?.execute {
            try {
                val ip = targetIp ?: return@execute
                if (targetPort <= 0) return@execute

                // Zero memory allocation: stream directly from DJI's native array structure
                val packet = DatagramPacket(data, offset, length, ip, targetPort)

                socket?.send(packet)
            } catch (e: Exception) {
                Log.e("UDPVideoStreamer", "Packet streaming execution drop: ${e.message}")
            }
        }
    }

    private var cameraUpdatedListener: ICameraStreamManager.AvailableCameraUpdatedListener? = null

    fun start() {
        if (isStreaming) return

        try {
            // Initialize infrastructure on dedicated background contexts
            socket = DatagramSocket()
            networkExecutor = Executors.newSingleThreadExecutor()

            // Pre-cache network endpoints to avoid overhead inside the video frame thread
            val rawIp = settingsManager.getRosIp()
            targetPort = settingsManager.getUdpPort()

            rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Initializing Stream Target -> IP: $rawIp, Port: $targetPort")

            Toast.makeText(context, "Initializing Stream Target -> IP: $rawIp, Port: $targetPort", Toast.LENGTH_SHORT).show()

            networkExecutor?.execute {
                try {
                    targetIp = InetAddress.getByName(rawIp)
                } catch (e: Exception) {
                    rosBridgeManager.logToRos("logs","UDPVideoStreamer", "IP Address compilation error: ${e.message}")
                }
            }

            isStreaming = true
            val cameraStreamManager = CameraStreamManager.getInstance()

            // 1. Build the lifecycle listener using an anonymous object to avoid AbstractMethodError
            cameraUpdatedListener = object : ICameraStreamManager.AvailableCameraUpdatedListener {

                override fun onAvailableCameraUpdated(cameraIndices: List<ComponentIndexType>) {
                    if (cameraIndices.isNotEmpty()) {
                        val mainCamera = cameraIndices[0]

                        rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Camera detected: $mainCamera. Engaging Stream source...")

                        // 2. Safely instruct KeyManager to pipe the video feed
                        val videoSourceKey = CameraKey.KeyCameraVideoStreamSource.create(mainCamera)
                        KeyManager.getInstance().setValue(videoSourceKey, CameraVideoStreamSourceType.DEFAULT_CAMERA, object : CommonCallbacks.CompletionCallback {
                            override fun onSuccess() {
                                rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Stream source configured to DEFAULT_CAMERA.")

                                // 3. Bind the data flow pipe safely inside the success callback
                                cameraStreamManager.removeReceiveStreamListener(streamListener)
                                cameraStreamManager.addReceiveStreamListener(mainCamera, streamListener)
                            }

                            override fun onFailure(error: dji.v5.common.error.IDJIError) {
                                rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Failed to switch video source: ${error.description()}")
                            }
                        })
                    } else {
                        rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "No active camera interfaces detected on the aircraft.")
                    }
                }

                // MANDATORY V5 METHOD: This must be overridden to satisfy the interface blueprint
                override fun onCameraStreamEnableUpdate(cameraStreamEnableMap: Map<ComponentIndexType, Boolean>) {
                    rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Camera stream enable map modified: $cameraStreamEnableMap")
                }
            }

            cameraStreamManager.addAvailableCameraUpdatedListener(cameraUpdatedListener!!)

            rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Video stream decoding channel engaged successfully.")
        } catch (e: Exception) {
            rosBridgeManager.logToRos("logs","UDPVideoStreamer", "Initialization breakdown: ${e.message}")
            stop()
        }
    }

    fun stop() {
        isStreaming = false
        rosBridgeManager.logToRos("logs", "UDPVideoStreamer", "Terminating video stream...")

        try {
            CameraStreamManager.getInstance().removeReceiveStreamListener(streamListener)
        } catch (e: Exception) {
            // Frame listener state fallback
        }

        networkExecutor?.execute {
            try {
                socket?.close()
            } catch (e: Exception) { /* No-op */ }
            socket = null
            targetIp = null
        }

        networkExecutor?.shutdown()
        networkExecutor = null
    }
}

