package com.example.rnsbtmessenger

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.rnsbtmessenger.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceSpinner: Spinner
    private var rnsService: RnsService? = null
    private var serviceBound = false
    private var rnodeMacAddress: String = ""
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) connectToRNode()
        else Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
    }
    
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleImage(it) } }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RnsService.LocalBinder
            rnsService = binder.getService()
            serviceBound = true
            rnsService?.onConnectionStatus = { connected ->
                runOnUiThread {
                    binding.connectionStatus.text = if (connected) "Connected" else "Disconnected"
                }
            }
            rnsService?.onResourceProgress = { p ->
                runOnUiThread { binding.uploadProgress.progress = (p * 100).toInt() }
            }
            if (rnodeMacAddress.isNotEmpty()) rnsService?.initialize(rnodeMacAddress)
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
        deviceSpinner = binding.deviceSpinner
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.refreshDevicesButton.setOnClickListener { loadPairedDevices() }
        loadPairedDevices()
        
        binding.connectButton.setOnClickListener {
            val selectedDevice = pairedDevices.getOrNull(deviceSpinner.selectedItemPosition)
            if (selectedDevice != null) {
                rnodeMacAddress = selectedDevice.address
                checkPermissions()
            } else {
                Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.sendTextButton.setOnClickListener {
            val msg = binding.messageInput.text.toString()
            if (msg.isNotEmpty() && serviceBound) {
                rnsService?.sendTextMessage(ByteArray(16), msg)
                binding.messageInput.text.clear()
                Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.sendImageButton.setOnClickListener { imagePicker.launch("image/*") }
    }
    
    private fun loadPairedDevices() {
        pairedDevices.clear()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bonded = adapter.bondedDevices
        if (bonded != null && bonded.isNotEmpty()) {
            pairedDevices.addAll(bonded)
            val deviceNames = bonded.map { "${it.name ?: "Unknown"} (${it.address})" }.toTypedArray()
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceNames)
            deviceSpinner.adapter = spinnerAdapter
        } else {
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("No paired devices"))
            deviceSpinner.adapter = spinnerAdapter
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissions() {
        val req = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        val missing = req.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) connectToRNode()
        else permissionLauncher.launch(missing.toTypedArray())
    }
    
    private fun connectToRNode() {
        val intent = Intent(this, RnsService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun handleImage(uri: Uri) {
        if (!serviceBound) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val f = File(filesDir, "img_${System.currentTimeMillis()}")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(f).use { output -> input.copyTo(output) }
            }
            rnsService?.sendImage(ByteArray(16), f)
            Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
            binding.uploadProgress.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
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