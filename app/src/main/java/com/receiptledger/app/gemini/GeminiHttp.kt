package com.receiptledger.app.gemini

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object GeminiHttp {
    const val CALL_TIMEOUT_SECONDS = 30L

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .dns(Ipv4FirstDns)
        .retryOnConnectionFailure(false)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(4, 10, TimeUnit.MINUTES))
        .build()
}

private object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return ipv4.ifEmpty { addresses }
    }
}
