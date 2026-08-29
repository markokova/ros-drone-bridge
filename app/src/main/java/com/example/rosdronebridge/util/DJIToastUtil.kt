package com.example.rosdronebridge.util

import androidx.lifecycle.MutableLiveData
import com.example.rosdronebridge.data.DJIToastResult

object DJIToastUtil {
    var dJIToastLD: MutableLiveData<DJIToastResult>? = null
}