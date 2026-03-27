package com.example.rosdronebridge.factories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rosdronebridge.models.ROSBridgeClientVM
import com.example.rosdronebridge.util.ROSMessageParser

class ROSBridgeVMFactory(
    private val parser: ROSMessageParser
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ROSBridgeClientVM(parser) as T
    }
}