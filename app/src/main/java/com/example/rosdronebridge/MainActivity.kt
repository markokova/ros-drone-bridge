package com.example.rosdronebridge

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rosdronebridge.models.ROSBridgeClientVM
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val rosBridgeClientVM: ROSBridgeClientVM by viewModels()
    private lateinit var adapter: MessagesAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.messagesRecycler)
        adapter = MessagesAdapter()
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(this)


        observeMessages()
        observeMSDKManager()
        rosBridgeClientVM.connect()
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rosBridgeClientVM.messages.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
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
