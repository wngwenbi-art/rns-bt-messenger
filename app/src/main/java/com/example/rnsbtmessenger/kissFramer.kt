package com.example.rnsbtmessenger

import java.io.ByteArrayOutputStream

/**
 * KISS Framing for RNode communication over Bluetooth Classic (SPP)
 * Reference: https://en.wikipedia.org/wiki/KISS_(amateur_radio_protocol)
 * 
 * Wire Format:
 * â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
 * â”‚ 0xC0                    â”‚ FEND      â”‚ 1 byte
 * â”‚ <KISS Type + Port>      â”‚ 0x00      â”‚ 1 byte (Data frame, port 0)
 * â”‚ <RNS Packet Bytes>      â”‚ payload   â”‚ N bytes (â‰¤500 MTU)
 * â”‚ 0xC0                    â”‚ FEND      â”‚ 1 byte
 * â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
 * 
 * Byte Escaping:
 * 0xC0 â†’ 0xDB 0xDC
 * 0xDB â†’ 0xDB 0xDD
 */
class KissFramer {
    
    companion object {
        const val FEND = 0xC0.toByte()
        const val FESC = 0xDB.toByte()
        const val TFEND = 0xDC.toByte()
        const val TFESC = 0xDD.toByte()
        const val KISS_DATA_FRAME = 0x00.toByte() // Type 0, Port 0
    }
    
    private val receiveBuffer = ByteArrayOutputStream()
    private var receiving = false
    
    /**
     * Encode RNS packet bytes into KISS frame
     */
    fun encode(packetBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Write FEND start
        output.write(FEND.toInt())
        
        // Write KISS type byte (Data frame, port 0)
        output.write(KISS_DATA_FRAME.toInt())
        
        // Escape and write payload
        for (byte in packetBytes) {
            when (byte) {
                FEND -> {
                    output.write(FESC.toInt())
                    output.write(TFEND.toInt())
                }
                FESC -> {
                    output.write(FESC.toInt())
                    output.write(TFESC.toInt())
                }
                else -> {
                    output.write(byte.toInt())
                }
            }
        }
        
        // Write FEND end
        output.write(FEND.toInt())
        
        return output.toByteArray()
    }
    
    /**
     * Decode incoming bytes, return complete RNS packets
     */
    fun decode(inputByte: Byte): ByteArray? {
        when {
            inputByte == FEND -> {
                if (receiving && receiveBuffer.size() > 0) {
                    // Complete frame received
                    val packet = receiveBuffer.toByteArray()
                    receiveBuffer.reset()
                    receiving = false
                    return packet
                } else {
                    // Start of new frame
                    receiving = true
                    receiveBuffer.reset()
                }
            }
            receiving -> {
                if (inputByte == FESC) {
                    // Next byte is escaped - handle in next call
                    // For simplicity, we'll handle escape sequences inline
                } else {
                    receiveBuffer.write(inputByte.toInt())
                }
            }
        }
        return null
    }
    
    /**
     * Decode with escape sequence handling (streaming)
     */
    fun decodeStream(inputBytes: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        val buffer = ByteArrayOutputStream()
        var inFrame = false
        var escaped = false
        
        for (byte in inputBytes) {
            when {
                escaped -> {
                    when (byte) {
                        TFEND -> buffer.write(FEND.toInt())
                        TFESC -> buffer.write(FESC.toInt())
                        else -> buffer.write(byte.toInt())
                    }
                    escaped = false
                }
                byte == FEND -> {
                    if (inFrame && buffer.size() > 0) {
                        // Complete frame
                        packets.add(buffer.toByteArray())
                        buffer.reset()
                        inFrame = false
                    } else {
                        inFrame = true
                        buffer.reset()
                    }
                }
                byte == FESC -> {
                    escaped = true
                }
                inFrame -> {
                    buffer.write(byte.toInt())
                }
            }
        }
        
        return packets
    }
    
    fun reset() {
        receiveBuffer.reset()
        receiving = false
    }
}
