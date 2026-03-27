package com.example.rosdronebridge

import android.app.Application

open class DJIApplication : Application() {

    private val msdkManagerVM: MSDKManagerVM by globalViewModels()

    override fun onCreate() {
        super.onCreate()

        msdkManagerVM.initMobileSDK(this)
    }
}