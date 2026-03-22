package com.example.rnsbtmessenger

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import network.reticulum.interfaces.RnsInterface
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic (SPP/RFCOMM) Interface for RNode
 * 
 * SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
 * 
 * Connection Flow:
 * 1. Pair RNode via Android Bluetooth settings
 * 2. Connect using RFCOMM socket
 * 3. Wrap I/O with KISS framing
 * 4. Pass decoded packets to RNS core
 */
class BluetoothSppInterface(
    private val macAddress: String,
    private val onPacketReceived: (ByteArray) -> Unit
) : RnsInterface {
    
    companion object {
        private const val TAG = "BtSppInterface"
        val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CONNECT_TIMEOUT_MS = 10000L
        const val READ_BUFFER_SIZE = 1024
    }
    
    private var socket: BluetoothSocket? = null
    private var inputStream: java.io.InputStream? = null
    private var outputStream: java.io.OutputStream? = null
    private var kissFramer = KissFramer()
    private var job: Job? = null
    private var isConnected = false
    
    override val name: String = "BT-SPP-$macAddress"
    override val mtu: Int = 500  // RNS default MTU
    override val isReady: Boolean get() = isConnected
    
    /**
     * Open Bluetooth connection to RNode
     */
    override fun open(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            
            // Create RFCOMM socket (blocking)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            // Connect with timeout
            job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(CONNECT_TIMEOUT_MS) {
                        socket?.connect()
                    }
                    
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                    isConnected = true
                    
                    Log.i(TAG, "Connected to RNode: $macAddress")
                    
                    // Start read loop
                    startReadLoop()
                    
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Connection timeout")
                    close()
                } catch (e: IOException) {
                    Log.e(TAG, "Connection failed: ${e.message}")
                    close()
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open: ${e.message}")
            false
        }
    }
    
    /**
     * Read loop - decode KISS frames and pass to RNS
     */
    private fun startReadLoop() {
        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            
            try {
                while (isConnected && inputStream != null) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    
                    if (bytesRead > 0) {
                        val receivedBytes = buffer.copyOf(bytesRead)
                        
                        // Decode KISS frames
                        val packets = kissFramer.decodeStream(receivedBytes)
                        
                        for (packet in packets) {
                            Log.d(TAG, "Received RNS packet: ${packet.size} bytes")
                            onPacketReceived(packet)
                        }
                    } else if (bytesRead == -1) {
                        Log.w(TAG, "End of stream - RNode disconnected")
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Read error: ${e.message}")
            } finally {
                isConnected = false
                close()
            }
        }
    }
    
    /**
     * Write RNS packet to RNode (with KISS framing)
     */
    override fun write(packetBytes: ByteArray): Int {
        if (!isConnected || outputStream == null) {
            Log.w(TAG, "Cannot write - not connected")
            return -1
        }
        
        return try {
            // Encode with KISS framing
            val kissFrame = kissFramer.encode(packetBytes)
            
            outputStream?.write(kissFrame)
            outputStream?.flush()
            
            Log.d(TAG, "Sent RNS packet: ${packetBytes.size} bytes (${kissFrame.size} with KISS)")
            packetBytes.size
        } catch (e: IOException) {
            Log.e(TAG, "Write error: ${e.message}")
            -1
        }
    }
    
    /**
     * Close Bluetooth connection
     */
    override fun close() {
        try {
            isConnected = false
            job?.cancel()
            inputStream?.close()
            outputStream?.close()
            socket?.close()
            
            Log.i(TAG, "Bluetooth connection closed")
        } catch (e: IOException) {
            Log.e(TAG, "Close error: ${e.message}")
        } finally {
            socket = null
            inputStream = null
            outputStream = null
            kissFramer.reset()
        }
    }
    
    override fun getInterfaceStats(): Map<String, Any> {
        return mapOf(
            "type" to "Bluetooth SPP",
            "mac" to macAddress,
            "connected" to isConnected,
            "mtu" to mtu
        )
    }
}