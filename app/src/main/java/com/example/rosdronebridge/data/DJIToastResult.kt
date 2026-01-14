package com.example.rosdronebridge.data

class DJIToastResult(var isSuccess: Boolean, var msg: String? = null) {

    companion object {
        fun success(msg: String? = null): DJIToastResult {
            return DJIToastResult(true, "success ${msg ?: ""}")
        }

        fun failed(msg: String): DJIToastResult {
            return DJIToastResult(false, msg)
        }
    }
}
