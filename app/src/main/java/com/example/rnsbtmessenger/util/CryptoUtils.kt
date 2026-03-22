package com.example.rnsbtmessenger.util

import org.junit.Test
import org.junit.Assert.*

class CryptoUtilsTest {
    
    @Test
    fun sha256_producesCorrectLength() {
        val data = "test".encodeToByteArray()
        val hash = CryptoUtils.sha256(data)
        assertEquals(32, hash.size)
    }
    
    @Test
    fun sha256Truncated_produces16Bytes() {
        val data = "test".encodeToByteArray()
        val hash = CryptoUtils.sha256Truncated(data)
        assertEquals(16, hash.size)
    }
    
    @Test
    fun sha256_deterministic() {
        val data = "test".encodeToByteArray()
        val hash1 = CryptoUtils.sha256(data)
        val hash2 = CryptoUtils.sha256(data)
        assertArrayEquals(hash1, hash2)
    }
    
    @Test
    fun x25519KeyPair_correctSizes() {
        val keyPair = CryptoUtils.generateX25519KeyPair()
        assertEquals(32, keyPair.publicKey.size)
        assertEquals(32, keyPair.privateKey.size)
    }
    
    @Test
    fun x25519Agree_producesSharedSecret() {
        // Generate two key pairs
        val kp1 = CryptoUtils.generateX25519KeyPair()
        val kp2 = CryptoUtils.generateX25519KeyPair()
        
        // Both parties should derive same shared secret
        val secret1 = CryptoUtils.x25519Agree(kp1.privateKey, kp2.publicKey)
        val secret2 = CryptoUtils.x25519Agree(kp2.privateKey, kp1.publicKey)
        
        assertArrayEquals(secret1, secret2)
        assertEquals(32, secret1.size)
    }
    
    @Test
    fun ed25519KeyPair_correctSizes() {
        val keyPair = CryptoUtils.generateEd25519KeyPair()
        assertEquals(32, keyPair.publicKey.size)
        assertEquals(64, keyPair.privateKey.size)
    }
    
    @Test
    fun ed25519SignVerify_roundTrip() {
        val keyPair = CryptoUtils.generateEd25519KeyPair()
        val data = "test message".encodeToByteArray()
        
        val signature = CryptoUtils.ed25519Sign(keyPair.privateKey, data)
        assertEquals(64, signature.size)
        
        val valid = CryptoUtils.ed25519Verify(keyPair.publicKey, data, signature)
        assertTrue(valid)
    }
    
    @Test
    fun ed25519Verify_rejectsTamperedData() {
        val keyPair = CryptoUtils.generateEd25519KeyPair()
        val data = "test message".encodeToByteArray()
        val tamperedData = "tampered".encodeToByteArray()
        
        val signature = CryptoUtils.ed25519Sign(keyPair.privateKey, data)
        val valid = CryptoUtils.ed25519Verify(keyPair.publicKey, tamperedData, signature)
        
        assertFalse(valid)
    }
    
    @Test
    fun fernetEncryptDecrypt_roundTrip() {
        val encryptionKey = CryptoUtils.randomBytes(32)
        val hmacKey = CryptoUtils.randomBytes(32)
        val plaintext = "Hello RNS!".encodeToByteArray()
        
        val token = CryptoUtils.fernetEncrypt(plaintext, encryptionKey, hmacKey)
        val decrypted = CryptoUtils.fernetDecrypt(token, encryptionKey, hmacKey)
        
        assertArrayEquals(plaintext, decrypted)
    }
    
    @Test
    fun fernetToken_hasCorrectStructure() {
        val encryptionKey = CryptoUtils.randomBytes(32)
        val hmacKey = CryptoUtils.randomBytes(32)
        val plaintext = "test".encodeToByteArray()
        
        val token = CryptoUtils.fernetEncrypt(plaintext, encryptionKey, hmacKey)
        
        // Minimum size: version(1) + timestamp(8) + iv(16) + ciphertext(16+) + hmac(32)
        assertTrue(token.size >= 73)
        assertEquals(0x80.toByte(), token[0]) // Version byte
    }
    
    @Test
    fun fernetDecrypt_rejectsBadHmac() {
        val encryptionKey = CryptoUtils.randomBytes(32)
        val hmacKey = CryptoUtils.randomBytes(32)
        val plaintext = "test".encodeToByteArray()
        
        val token = CryptoUtils.fernetEncrypt(plaintext, encryptionKey, hmacKey)
        token[10] = (token[10].toInt() xor 0xFF).toByte() // Tamper with IV
        
        assertThrows(SecurityException::class.java) {
            CryptoUtils.fernetDecrypt(token, encryptionKey, hmacKey)
        }
    }
    
    @Test
    fun hkdfDerive_producesDeterministicOutput() {
        val ikm = "input-key-material".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        val info = "info".encodeToByteArray()
        
        val derived1 = CryptoUtils.hkdfDerive(ikm, salt, info, 32)
        val derived2 = CryptoUtils.hkdfDerive(ikm, salt, info, 32)
        
        assertArrayEquals(derived1, derived2)
    }
    
    @Test
    fun longToBigEndian_roundTrip() {
        val value = 12345678901234L
        val bytes = CryptoUtils.longToBigEndian(value)
        val recovered = CryptoUtils.bigEndianToLong(bytes)
        assertEquals(value, recovered)
    }
    
    @Test
    fun constantTimeEquals_worksCorrectly() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3)
        val c = byteArrayOf(1, 2, 4)
        
        assertTrue(CryptoUtils.constantTimeEquals(a, b))
        assertFalse(CryptoUtils.constantTimeEquals(a, c))
    }
    
    @Test
    fun identity_hashIs16Bytes() {
        val identity = CryptoUtils.createIdentity()
        assertEquals(16, identity.hash.size)
    }
    
    @Test
    fun identity_signVerify_roundTrip() {
        val identity = CryptoUtils.createIdentity()
        val data = "test".encodeToByteArray()
        
        val signature = identity.sign(data)
        val valid = identity.verify(data, signature)
        
        assertTrue(valid)
    }
}