package com.example.rnsbtmessenger.util

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object CryptoUtils {
    init { Security.addProvider(BouncyCastleProvider()) }
    fun sha256(data: ByteArray): ByteArray {
        val d = SHA256Digest(); val out = ByteArray(d.digestSize); d.update(data, 0, data.size); d.doFinal(out, 0); return out
    }
    fun sha256Truncated(data: ByteArray): ByteArray = sha256(data).copyOfRange(0, 16)
    fun deriveAddress(publicKey: ByteArray): ByteArray = sha256Truncated(publicKey)
    fun randomBytes(length: Int): ByteArray { val r = java.security.SecureRandom(); val b = ByteArray(length); r.nextBytes(b); return b }
    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}