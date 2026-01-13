package com.example.rosdronebridge

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback

class MSDKManagerVM : ViewModel() {

    val registerState = MutableLiveData<Pair<Boolean, IDJIError?>>()
    fun initMobileSDK(appContext: Context) {
        SDKManager.getInstance().init(appContext, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                registerState.postValue(Pair(true, null))
                Log.d("MSDKManagerVM", "onRegisterSuccess")
            }
            override fun onRegisterFailure(error: IDJIError?) {
                registerState.postValue(Pair(false, null))
                Log.d("MSDKManagerVM", "onRegisterFailure")
            }
            override fun onProductDisconnect(productId: Int) {

            }
            override fun onProductConnect(productId: Int) {

            }
            override fun onProductChanged(productId: Int) {
            }
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                }
            }
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
            }

            fun onDatabaseDownloadSuccess() {
            }
        })
    }
}