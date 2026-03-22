package com.example.rnsbtmessenger

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.rnsbtmessenger.stubs.RnsInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

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
    override val mtu: Int = 500
    override val isReady: Boolean get() = isConnected
    
    override fun open(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(CONNECT_TIMEOUT_MS) { socket?.connect() }
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                    isConnected = true
                    Log.i(TAG, "Connected to RNode: $macAddress")
                    startReadLoop()
                } catch (e: Exception) {
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
    
    private fun startReadLoop() {
        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (isConnected && inputStream != null) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val packets = kissFramer.decodeStream(buffer.copyOf(bytesRead))
                        for (packet in packets) onPacketReceived(packet)
                    } else if (bytesRead == -1) break
                }
            } catch (e: IOException) { Log.e(TAG, "Read error: ${e.message}") }
            finally { isConnected = false; close() }
        }
    }
    
    override fun write(packetBytes: ByteArray): Int {
        if (!isConnected || outputStream == null) return -1
        return try {
            outputStream?.write(kissFramer.encode(packetBytes))
            outputStream?.flush()
            packetBytes.size
        } catch (e: IOException) { Log.e(TAG, "Write error: ${e.message}"); -1 }
    }
    
    override fun close() {
        try {
            isConnected = false; job?.cancel()
            inputStream?.close(); outputStream?.close(); socket?.close()
        } catch (e: IOException) { Log.e(TAG, "Close error: ${e.message}") }
        finally { socket = null; inputStream = null; outputStream = null; kissFramer.reset() }
    }
    
    override fun getInterfaceStats(): Map<String, Any> = mapOf("type" to "Bluetooth SPP", "mac" to macAddress, "connected" to isConnected)
}