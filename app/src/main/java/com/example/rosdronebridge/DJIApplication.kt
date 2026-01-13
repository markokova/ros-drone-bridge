package com.example.rosdronebridge

import android.app.Application
import android.util.Log
import android.widget.Toast

open class DJIApplication : Application() {

    private val msdkManagerVM: MSDKManagerVM by globalViewModels()

    override fun onCreate() {
        super.onCreate()

        msdkManagerVM.initMobileSDK(this)
        Log.d("Marko", "onCreate")
        Toast.makeText(this, "I was here", Toast.LENGTH_LONG).show()
    }
}