package com.example.rnsbtmessenger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rnsbtmessenger.stubs.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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
    
    var onConnectionStatus: ((Boolean) -> Unit)? = null
    var onResourceProgress: ((Float) -> Unit)? = null
    
    inner class LocalBinder : Binder() { fun getService(): RnsService = this@RnsService }
    
    override fun onCreate() { super.onCreate(); createNotificationChannel(); startForeground(NOTIFICATION_ID, createNotification()) }
    override fun onBind(intent: Intent?): IBinder = binder
    
    fun initialize(rnodeMacAddress: String) {
        scope.launch {
            try {
                val config = ReticulumConfig(storagePath = filesDir.resolve("rns_storage").absolutePath, enableTransport = false, enableAnnounces = true)
                rns = Reticulum.start(config)
                localIdentity = loadOrCreateIdentity()
                btInterface = BluetoothSppInterface(rnodeMacAddress) { packetBytes -> Log.d(TAG, "Packet: ${packetBytes.size} bytes") }
                val connected = btInterface?.open() ?: false
                onConnectionStatus?.invoke(connected)
                if (connected) {
                    rns?.addInterface(btInterface!!)
                    messageDestination = Destination.create(identity = localIdentity!!, direction = DestinationDirection.IN, type = DestinationType.SINGLE, appName = "rnsbt", appData = "messenger")
                    rns?.registerDestination(messageDestination!!)
                }
            } catch (e: Exception) { Log.e(TAG, "Init failed: ${e.message}"); onConnectionStatus?.invoke(false) }
        }
    }
    
    private fun loadOrCreateIdentity(): Identity {
        val f = File(filesDir, "rns_storage/identity")
        return if (f.exists()) Identity.fromFile(f.absolutePath) else { val i = Identity.create(); i.toFile(f.absolutePath); i }
    }
    
    fun sendTextMessage(destinationHash: ByteArray, message: String) {
        scope.launch { try { val dest = messageDestination ?: return@launch; dest.transmit(message.encodeToByteArray()) } catch (e: Exception) { Log.e(TAG, "Send failed: ${e.message}") } }
    }
    
    fun sendImage(destinationHash: ByteArray, imageFile: File) {
        scope.launch {
            try {
                val dest = messageDestination ?: return@launch
                val link = dest.requestLink() ?: return@launch
                Resource(data = imageFile.readBytes(), link = link, advertise = true, autoCompress = false).advertise()
            } catch (e: Exception) { Log.e(TAG, "Image send failed: ${e.message}") }
        }
    }
    
    fun disconnect() { scope.launch { btInterface?.close(); rns?.let { Reticulum.stop() } } }
    override fun onDestroy() { disconnect(); scope.cancel(); super.onDestroy() }
    
    private fun createNotificationChannel() {
        val ch = NotificationChannel(NOTIFICATION_CHANNEL_ID, "RNS Service", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NotificationManager::class.java))?.createNotificationChannel(ch)
    }
    private fun createNotification(): Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("RNS Messenger").setContentText("Connected").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
}