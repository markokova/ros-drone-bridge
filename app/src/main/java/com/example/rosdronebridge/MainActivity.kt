package com.example.rosdronebridge

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val msdkManagerVM: MSDKManagerVM by globalViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        observeMSDKManager()
    }

    private fun observeMSDKManager() {

        val statusText = findViewById<TextView>(R.id.statusText)

        msdkManagerVM.registerState.observe(this) { resultPair ->
            if (resultPair.first) {
                statusText.text = "Register Success"
                Toast.makeText(this, "Register Success", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Register Failed"
                Toast.makeText(this, "Register Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}