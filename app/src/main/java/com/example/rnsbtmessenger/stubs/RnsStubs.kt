package com.example.rnsbtmessenger.stubs

data class Identity(val hash: ByteArray, val publicKey: ByteArray, val privateKey: ByteArray) {
    companion object {
        fun create(): Identity = Identity(ByteArray(16), ByteArray(32), ByteArray(64))
        fun fromFile(path: String): Identity = create()
    }
    fun toFile(path: String) {}
}

enum class DestinationDirection { IN, OUT, INOUT }
enum class DestinationType { SINGLE, GROUP, PLAIN, LINK }

data class Destination(val hash: ByteArray, val identity: Identity) {
    companion object {
        fun create(identity: Identity, direction: DestinationDirection, type: DestinationType, appName: String, appData: String): Destination = Destination(ByteArray(16), identity)
    }
    fun transmit(data: ByteArray) {}
    fun announce() {}
    fun requestLink(): Link? = Link(this)
}

class Link(val destination: Destination) { fun teardown() {} }

class Resource(val data: ByteArray, val link: Link, val advertise: Boolean = false, val autoCompress: Boolean = false, val callback: ((Resource) -> Unit)? = null, val progressCallback: ((Resource) -> Unit)? = null) {
    enum class Status { QUEUED, SENDING, COMPLETE, FAILED }
    val status: Status = Status.QUEUED
    val hash: ByteArray = ByteArray(32)
    val progress: Float = 0f
    fun advertise() {}
}

class Reticulum private constructor() {
    companion object {
        fun start(config: ReticulumConfig = ReticulumConfig()): Reticulum = Reticulum()
        fun stop() {}
    }
    fun addInterface(iface: RnsInterface) {}
    fun registerDestination(dest: Destination) {}
}

data class ReticulumConfig(val storagePath: String = "", val enableTransport: Boolean = true, val enableAnnounces: Boolean = true)

interface RnsInterface {
    val name: String
    val mtu: Int
    val isReady: Boolean
    fun open(): Boolean
    fun write(packetBytes: ByteArray): Int
    fun close()
    fun getInterfaceStats(): Map<String, Any> = emptyMap()
}