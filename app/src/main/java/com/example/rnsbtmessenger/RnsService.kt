package com.example.rnsbtmessenger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import network.reticulum.Reticulum
import network.reticulum.common.DestinationDirection
import network.reticulum.common.DestinationType
import network.reticulum.destination.Destination
import network.reticulum.identity.Identity
import network.reticulum.link.Link
import network.reticulum.resource.Resource
import java.io.File

/**
 * Foreground service managing RNS core and Bluetooth interface
 * Runs in background to maintain RNode connection
 */
class RnsService : Service() {
    
    companion object {
        private const val TAG = "RnsService"
        private const val NOTIFICATION_CHANNEL_ID = "rns_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    private val binder = LocalBinder()
    private var rns: Reticulum? = null
    private var btInterface: BluetoothSppInterface? = null
    private var localIdentity: Identity? = null
    private var messageDestination: Destination? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Callbacks for UI
    var onMessageReceived: ((ByteArray) -> Unit)? = null
    var onConnectionStatus: ((Boolean) -> Unit)? = null
    var onResourceProgress: ((Float) -> Unit)? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): RnsService = this@RnsService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * Initialize RNS with Bluetooth interface
     */
    fun initialize(rnodeMacAddress: String) {
        scope.launch {
            try {
                // Create RNS instance (client-only mode for battery efficiency)
                val config = network.reticulum.ReticulumConfig(
                    storagePath = filesDir.resolve("rns_storage").absolutePath,
                    enableTransport = false,  // Disable routing - save battery
                    enableAnnounces = true
                )
                
                rns = Reticulum.start(config)
                Log.i(TAG, "Reticulum initialized")
                
                // Create local identity (load or generate)
                localIdentity = loadOrCreateIdentity()
                
                // Create Bluetooth interface
                btInterface = BluetoothSppInterface(rnodeMacAddress) { packetBytes ->
                    // Packet received from RNode
                    handleIncomingPacket(packetBytes)
                }
                
                // Connect to RNode
                val connected = btInterface?.open() ?: false
                onConnectionStatus?.invoke(connected)
                
                if (connected) {
                    // Register interface with RNS
                    rns?.addInterface(btInterface!!)
                    Log.i(TAG, "RNode interface added to RNS")
                    
                    // Create message destination
                    messageDestination = Destination.create(
                        identity = localIdentity!!,
                        direction = DestinationDirection.IN,
                        type = DestinationType.SINGLE,
                        appName = "rnsbt",
                        appData = "messenger"
                    )
                    
                    rns?.registerDestination(messageDestination!!)
                    Log.i(TAG, "Message destination registered")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}", e)
                onConnectionStatus?.invoke(false)
            }
        }
    }
    
    /**
     * Load existing identity or create new one
     */
    private fun loadOrCreateIdentity(): Identity {
        val identityFile = File(filesDir, "rns_storage/identity")
        
        return if (identityFile.exists()) {
            Identity.fromFile(identityFile.absolutePath)
        } else {
            val identity = Identity.create()
            identity.toFile(identityFile.absolutePath)
            identity
        }
    }
    
    /**
     * Handle incoming RNS packet
     */
    private fun handleIncomingPacket(packetBytes: ByteArray) {
        // RNS core handles packet parsing internally
        // This is called after KISS decoding, before RNS processing
        
        // For custom handling, you can inspect packet here
        // But normally RNS processes this automatically
        
        Log.d(TAG, "Packet received: ${packetBytes.size} bytes")
    }
    
    /**
     * Send text message
     */
    fun sendTextMessage(destinationHash: ByteArray, message: String) {
        scope.launch {
            try {
                val dest = messageDestination ?: return@launch
                
                // For small messages (<383 bytes encrypted), send directly
                val contentBytes = message.encodeToByteArray()
                
                if (contentBytes.size < 383) {
                    // Direct packet
                    dest.transmit(contentBytes)
                    Log.i(TAG, "Text message sent: ${message.length} chars")
                } else {
                    // Use Resource for larger messages
                    sendResource(destinationHash, contentBytes, "text/plain")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send text failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Send image via Resource (chunked, compressed)
     */
    fun sendImage(destinationHash: ByteArray, imageFile: File) {
        scope.launch {
            try {
                sendResource(destinationHash, imageFile.readBytes(), "image/${getFileExtension(imageFile)}")
            } catch (e: Exception) {
                Log.e(TAG, "Send image failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Send data as RNS Resource (handles chunking, compression, verification)
     */
    private suspend fun sendResource(destinationHash: ByteArray, data: ByteArray, mimeType: String) {
        val dest = messageDestination ?: return
        
        // Establish link first (required for Resource transfer)
        val link = dest.requestLink()
        
        if (link != null) {
            val resource = Resource(
                data = data,
                link = link,
                advertise = true,
                autoCompress = shouldCompress(mimeType),
                callback = { resource ->
                    Log.i(TAG, "Resource transfer: ${resource.status}")
                },
                progressCallback = { resource ->
                    onResourceProgress?.invoke(resource.progress)
                }
            )
            
            resource.advertise()
            Log.i(TAG, "Resource advertised: ${data.size} bytes, ${mimeType}")
        } else {
            Log.e(TAG, "Failed to establish link for resource transfer")
        }
    }
    
    /**
     * Determine if compression is beneficial
     */
    private fun shouldCompress(mimeType: String): Boolean {
        // Don't compress already-compressed formats
        return when {
            mimeType.contains("jpeg") || 
            mimeType.contains("jpg") || 
            mimeType.contains("png") || 
            mimeType.contains("gif") -> false
            else -> true
        }
    }
    
    private fun getFileExtension(file: File): String {
        val name = file.name
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) name.substring(lastDot + 1).lowercase() else "bin"
    }
    
    /**
     * Get local identity hash (for sharing with peers)
     */
    fun getLocalIdentityHash(): ByteArray? {
        return localIdentity?.hash
    }
    
    /**
     * Disconnect from RNode
     */
    fun disconnect() {
        scope.launch {
            btInterface?.close()
            rns?.stop()
            Log.i(TAG, "RNS service stopped")
        }
    }
    
    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }
    
    // Notification Management
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "RNS Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("RNS Messenger")
            .setContentText("Connected to RNode")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}