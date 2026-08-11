package com.example.rosdronebridge.data

import java.sql.Timestamp

/*
TODO
    idea:
    have an abstract ROSTopic class:
    abstract class ROSTopic(djiViewModel: DJIViewModel)
    when receiving ROS message in ROSBridgeClient, based on topic name create different
    subclass of ROSTopic (e.g. Velocity, Takeoff, Land)

    Each of those classes can have appropriate DJIViewModel injected in ROSBridgeClient

    After that, in DroneController, we can just listen to new messages(topics) being added to
    a list from ROSBridgeManager and call an appropriate method in this way:
    ROSTopic.execute("1,2,3,4")
    which will execute
    ROSTopic
        VirtualStickVM.setVelocity(1,2,3,4)

    For now, one ROSTopic class is used for all topics

* */
data class ROSMessage(
    val operation : String,
    val topic : String,
    val payload : ROSPayload,
    val timestamp: Timestamp
)