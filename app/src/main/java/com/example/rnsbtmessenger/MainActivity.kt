package com.example.rnsbtmessenger

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.rnsbtmessenger.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MessagingViewModel
    private var rnsService: RnsService? = null
    private var serviceBound = false
    
    // RNode MAC address (set via UI or settings)
    private var rnodeMacAddress: String = ""
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            connectToRNode()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }
    
    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelection(it) }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RnsService.LocalBinder
            rnsService = binder.getService()
            serviceBound = true
            
            // Setup callbacks
            rnsService?.onConnectionStatus = { connected ->
                runOnUiThread {
                    binding.connectionStatus.text = if (connected) "Connected" else "Disconnected"
                    binding.connectionStatus.setTextColor(
                        ContextCompat.getColor(this@MainActivity, 
                            if (connected) android.R.color.holo_green_dark 
                            else android.R.color.holo_red_dark)
                    )
                }
            }
            
            rnsService?.onResourceProgress = { progress ->
                runOnUiThread {
                    binding.uploadProgress.progress = (progress * 100).toInt()
                }
            }
            
            // Initialize if we have RNode MAC
            if (rnodeMacAddress.isNotEmpty()) {
                rnsService?.initialize(rnodeMacAddress)
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            rnsService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[MessagingViewModel::class.java]
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        // RNode MAC input
        binding.rnodeMacInput.setText("F4:12:73:29:4E:89") // Default example
        
        // Connect button
        binding.connectButton.setOnClickListener {
            rnodeMacAddress = binding.rnodeMacInput.text.toString().trim()
            if (rnodeMacAddress.isNotEmpty()) {
                checkPermissions()
            } else {
                Toast.makeText(this, "Enter RNode MAC address", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Send text button
        binding.sendTextButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isNotEmpty() && serviceBound) {
                // For demo, broadcast to all (in real app, use specific destination hash)
                rnsService?.sendTextMessage(ByteArray(16), message)
                binding.messageInput.text.clear()
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Send image button
        binding.sendImageButton.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }
    
    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            connectToRNode()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun connectToRNode() {
        val intent = Intent(this, RnsService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun handleImageSelection(uri: Uri) {
        if (!serviceBound) {
            Toast.makeText(this, "Not connected to RNode", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Copy to app storage
            val imageFile = File(filesDir, "temp_image_${System.currentTimeMillis()}")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Send via RNS
            rnsService?.sendImage(ByteArray(16), imageFile)
            
            Toast.makeText(this, "Image sending...", Toast.LENGTH_SHORT).show()
            binding.uploadProgress.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }
}