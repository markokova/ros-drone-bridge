package com.example.rosdronebridge

import android.app.Application
import com.example.rosdronebridge.models.ROSBridgeManager
import javax.inject.Inject

open class DJIApplication : Application() {

    @Inject lateinit var rosBridgeManager: ROSBridgeManager
    @Inject lateinit var msdkManager: MSDKManager

    override fun onCreate() {
        super.onCreate()

        msdkManager.initMobileSDK(this)
    }
}