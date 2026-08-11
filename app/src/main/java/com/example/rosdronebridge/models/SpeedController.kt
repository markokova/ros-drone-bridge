package com.example.rosdronebridge.models

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedController @Inject constructor() {
    private var speedLevel : Int = 1

    fun setSpeedLevel(updatedSpeedLevel : Int) {
        speedLevel = updatedSpeedLevel
    }

    fun getSpeedLevel() : Int {
        return speedLevel
    }

}