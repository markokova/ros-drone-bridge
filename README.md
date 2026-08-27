# ros-drone-bridge
This project enables communication between DJI drone and a ROS node through a mobile app which serves as a bridge.


# Mobile Phone and Robot Operating System (ROS2) Controlled Quadcopter

This repository contains the source code and documentation for a Master's thesis focused on achieving interoperability between commercial DJI drone, the Android mobile platform, and Robot Operating System (ROS2).

The primary objective of this project is to integrate the **DJI Mobile SDK** and the **ROS2** ecosystem by developing a dedicated Android application that acts as a communication bridge. The system enables remote quadcopter control via ROS2 nodes while simultaneously streaming real-time telemetry data and video stream from the aircraft back to the ROS2 environment.

---

## Key Features

- **Bidirectional Communication:** Seamless exchange of control commands and telemetry data between ROS2 and the drone.
- **Android Bridge Application:** A native mobile application developed in Kotlin that utilizes the DJI Mobile SDK to interface directly with the hardware.
- **Low-Latency WebSocket Protocol:** Real-time JSON packet exchange between the smartphone and the PC running ROS2 nodes over a shared Wi-Fi network.
- **Commercial Hardware Integration:** Fully implemented and validated using the **DJI Mini 3 Pro** quadcopter.

---

## System Architecture

The data flow and component dependency follow this pipeline:
`ROS2 Node (PC) <--- [WebSocket over Wi-Fi] ---> Android App (Kotlin/DJI SDK) <--- [USB/Protocol] ---> DJI Remote Controller <---> DJI Mini 3 Pro`

1. **ROS2 Environment:** Control nodes publish movement commands and subscribe to incoming telemetry topics. Also video stream is decoded and handled on this ROS2 side.
2. **WebSocket Server/Client:** Facilitates high-frequency data transmission with minimal network overhead.
3. **Android Application:** Parses incoming WebSocket messages into corresponding DJI SDK API calls while reading drone sensor arrays to stream data back to the PC.
4. **UDP:** Video streaming relies on UDP packet transmission.

---

## Technology Stack

The implementation relies on the following software and hardware components:
- **Operating System:** Linux Ubuntu 22.04 LTS (or newer)
- **Robotics Framework:** [ROS2 Humble Hawksbill](https://ros.org) (Recommended)
- **Android IDE:** Android Studio (Latest stable release)
- **Programming Languages:** Kotlin (Android), Python (ROS2)
- **SDK:** DJI Mobile SDK (MSDK) V5
- **Network Protocol:** WebSockets, Datagram

---

## Installation and Deployment

### 1. Android Application
1. Clone the repository and import the `android-app` directory into Android Studio.
2. Configure your **DJI Developer API Key** inside your `local.properties` or `AndroidManifest.xml` file.
3. Enable **Developer Options** and **USB Debugging** on your Android smartphone, then connect it to your PC.
4. Build and deploy the application to your mobile device.

### 2. ROS2 Nodes
1. Clone or move the `ros2_workspace` directory into your active ROS2 workspace.
2. Build the package workspace using the `colcon` build tool:
   ```bash
   cd ~/ros2_ws
   colcon build --packages-select dji_ros2_bridge
   source install/setup.bash
   ```
3. Execute the primary communication bridge node:
   ```bash
   ros2 run dji_ros2_bridge bridge_node --ros-args -p ip_address:="YOUR_PHONE_IP_ADDRESS"
   ```

---

## Repository Structure

```text
├── android-app/          # Android Studio project source code (Kotlin)
│   ├── app/src/main/     # Core activities and DJI SDK implementation
|      └── DJIAircraftApplication.kt
|      └── DJIApplication.kt
|      └── MainActivity.kt
|      └── MSDKManager.kt
|      └── SettingsActivity.kt
|      └── data/
|         └── DroneState.kt
|         └── ROSMessage.kt
|         └── VelocityPayload.kt
|      └── di/
|         └── DroneModule.kt
|      └── models/
|         └── BasicAircraftControlManager.kt
|         └── GimbalController.kt
|         └── PerceptionController.kt
|         └── ROSBridgeManager.kt
|         └── ROSMessageHandler.kt
|         └── SafetyController.kt
|         └── SimulatorController.kt
|         └── SpeedController.kt
|         └── VirtualStickController.kt
|      └── util/
|         └── DroneStateTracker.kt
|         └── RosLogger.kt
|         └── ROSMessageParser.kt
|         └── SettingsManager.kt
|         └── TelemetryPublisher.kt
|         └── UDPVideoStreamer.kt

│   └── build.gradle      # App build and dependency configurations
├── docs/                 # Technical documentation, schematics, and architecture diagrams
└── README.md             # Main repository overview documentation

├── ros2_workspace/       # Native ROS2 packages and custom nodes (C++/Python)
│   └── src/
│       └── dji_ros2_bridge/
```

---

## 📜 License and Disclaimer

This project was developed as part of a Master's thesis. Commercial usage of this code is subject to the licensing terms and conditions of the **DJI Mobile SDK**. When deploying this software on physical aircraft, always comply with local civil aviation regulations, safety guidelines, and airspace restrictions.
