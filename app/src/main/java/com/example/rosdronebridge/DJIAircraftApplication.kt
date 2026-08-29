package com.example.rosdronebridge

import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DJIAircraftApplication : DJIApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        com.cySdkyc.clx.Helper.install(this)
    }
}