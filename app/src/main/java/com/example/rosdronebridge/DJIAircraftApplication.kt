package com.example.rosdronebridge

import android.content.Context

class DJIAircraftApplication : DJIApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        com.cySdkyc.clx.Helper.install(this)
    }
}