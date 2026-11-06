package com.receiptledger.app.gemini

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

class GeminiCallTimer : EventListener() {
    var dnsMs: Long = 0
        private set
    var connectMs: Long = 0
        private set
    var tlsMs: Long = 0
        private set
    var uploadMs: Long = 0
        private set
    var serverMs: Long = 0
        private set
    var reusedConnection: Boolean = false
        private set

    private var markDns = 0L
    private var markConnect = 0L
    private var markTls = 0L
    private var markUpload = 0L
    private var markServer = 0L

    override fun dnsStart(call: Call, domainName: String) {
        markDns = now()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsMs = elapsed(markDns)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        markConnect = now()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        connectMs = elapsed(markConnect)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        connectMs = elapsed(markConnect)
    }

    override fun secureConnectStart(call: Call) {
        markTls = now()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        tlsMs = elapsed(markTls)
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        reusedConnection = dnsMs == 0L && connectMs == 0L
    }

    override fun requestBodyStart(call: Call) {
        markUpload = now()
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        uploadMs = elapsed(markUpload)
        markServer = now()
    }

    override fun responseHeadersStart(call: Call) {
        serverMs = elapsed(markServer)
    }

    fun summary(totalMs: Long, thoughtsTokens: Int?): String {
        val bits = mutableListOf("Gemini round-trip: ${fmt(totalMs)}")
        val setup = dnsMs + connectMs + tlsMs
        when {
            setup >= 1_500 -> bits += "connect ${fmt(setup)}"
            reusedConnection -> bits += "reused connection"
            setup > 0 -> bits += "connect ${fmt(setup)}"
        }
        if (uploadMs >= 800) bits += "upload ${fmt(uploadMs)}"
        if (serverMs >= 0 && markServer > 0) bits += "Google ${fmt(serverMs)}"
        if (thoughtsTokens != null && thoughtsTokens > 0) bits += "thoughts $thoughtsTokens"
        return bits.joinToString(" · ")
    }

    private fun now() = System.nanoTime()

    private fun elapsed(started: Long): Long {
        if (started == 0L) return 0
        return (now() - started) / 1_000_000
    }

    private fun fmt(ms: Long): String = "${ms / 1000}.${((ms % 1000) / 100)}s"
}
