package com.example.rnsbtmessenger

import java.io.ByteArrayOutputStream

class KissFramer {
    companion object {
        const val FEND: Byte = 0xC0
        const val FESC: Byte = 0xDB
        const val TFEND: Byte = 0xDC
        const val TFESC: Byte = 0xDD
        const val KISS_DATA_FRAME: Byte = 0x00
    }
    
    fun encode(packetBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(FEND.toInt()); out.write(KISS_DATA_FRAME.toInt())
        for (b in packetBytes) when (b) { FEND -> { out.write(FESC.toInt()); out.write(TFEND.toInt()) }; FESC -> { out.write(FESC.toInt()); out.write(TFESC.toInt()) }; else -> out.write(b.toInt()) }
        out.write(FEND.toInt())
        return out.toByteArray()
    }
    
    fun decodeStream(inputBytes: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>(); val buf = ByteArrayOutputStream(); var inFrame = false; var escaped = false
        for (b in inputBytes) when {
            escaped -> { when (b) { TFEND -> buf.write(FEND.toInt()); TFESC -> buf.write(FESC.toInt()); else -> buf.write(b.toInt()) }; escaped = false }
            b == FEND -> { if (inFrame && buf.size() > 0) { packets.add(buf.toByteArray()); buf.reset(); inFrame = false } else { inFrame = true; buf.reset() } }
            b == FESC -> escaped = true
            inFrame -> buf.write(b.toInt())
        }
        return packets
    }
    fun reset() {}
}