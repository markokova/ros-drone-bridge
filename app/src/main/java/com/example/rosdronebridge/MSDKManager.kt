package com.example.rosdronebridge

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import com.example.rosdronebridge.models.ROSBridgeManager
import com.example.rosdronebridge.util.DroneStateTracker
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.account.UserAccountManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.network.DJINetworkManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MSDKManager @Inject constructor(
    private val rosBridgeManager: ROSBridgeManager,
    private val droneStateTracker: DroneStateTracker
) {

    val registerState = MutableLiveData<Pair<Boolean, IDJIError?>>()
    val loginState = MutableLiveData<Pair<Boolean, IDJIError?>>()

    var isInit = false

    fun initMobileSDK(appContext: Context) {
        SDKManager.getInstance().init(appContext, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                registerState.postValue(Pair(true, null))
                Log.d("MSDKManager", "onRegisterSuccess")
                rosBridgeManager.logToRos("logs", "MSDKManager", "onRegisterSuccess")
            }
            override fun onRegisterFailure(error: IDJIError?) {
                registerState.postValue(Pair(false, error))
                Log.d("MSDKManager", "onRegisterFailure")
            }
            override fun onProductDisconnect(productId: Int) {
                droneStateTracker.setConnected(false) // Explicitly set disconnected
                rosBridgeManager.logToRos("logs", "MSDKManager", "Product disconnected")
            }
            override fun onProductConnect(productId: Int) {
                Log.d("MSDKManager", "Product connected")
                rosBridgeManager.logToRos("logs", "MSDKManager", "Product connected")
                droneStateTracker.setConnected(true) // Explicitly set connected
            }

            override fun onProductChanged(productId: Int) {
                rosBridgeManager.logToRos("logs", "MSDKManager", "Product changed")
            }
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    isInit = true
                    SDKManager.getInstance().registerApp()
                }
            }
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
            }

            fun onDatabaseDownloadSuccess() {
            }
        })

        DJINetworkManager.getInstance().addNetworkStatusListener { isAvailable ->
            if (isInit && isAvailable && !SDKManager.getInstance().isRegistered) {
                SDKManager.getInstance().registerApp()
            }
        }
    }


    // In MSDKManager.kt
    fun loginAccount(activity: FragmentActivity) {
        val userAccountManager = UserAccountManager.getInstance() ?: return

        val account = userAccountManager.loginInfo?.account

        if (account.isNullOrEmpty()) {
            userAccountManager.logInDJIUserAccount(activity, false, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    loginState.postValue(Pair(true, null))
                    Log.d("MSDKManager", "Login Success")
                }
                override fun onFailure(error: IDJIError) {
                    loginState.postValue(Pair(false, error))
                    Log.e("MSDKManager", "Login Failure: $error")
                }
            })
        } else {
            loginState.postValue(Pair(true, null))
            Log.d("MSDKManager", "Already logged in as: $account")
        }
    }
}
