/*
 * Copyright (C) 2026 CuponApp
 * GPLv3 or later.
 */
package com.moez.QKSMS.feature.alarmguard

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Read-only MIIO v1 "hello" probe over the local Wi-Fi.
 * Does not authenticate, collect camera tokens, send any setting changes,
 * or claim that authenticated MIoT property writes will work.
 */
object XiaomiLanProbe {
    private const val MIIO_PORT = 54321
    private const val RESPONSE_TIMEOUT_MS = 2000

    data class ProbeResult(val responded: Boolean, val message: String)

    fun isValidPrivateIpv4(value: String): Boolean {
        val text = value.trim()
        val parts = text.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' } ||
                (part.length > 1 && part[0] == '0')) {
                null
            } else {
                part.toIntOrNull()?.takeIf { it in 0..255 }
            }
        }
        if (octets.any { it == null }) return false
        val a = octets[0]!!
        val b = octets[1]!!
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
    }

    fun helloPacket(): ByteArray =
        ByteArray(32) { 0xff.toByte() }.apply {
            this[0] = 0x21
            this[1] = 0x31
            this[2] = 0x00
            this[3] = 0x20
        }

    fun isValidHelloReply(data: ByteArray, length: Int): Boolean {
        if (length < 32 || length > data.size) return false
        val declared = ((data[2].toInt() and 0xff) shl 8) or
            (data[3].toInt() and 0xff)
        return data[0] == 0x21.toByte() &&
            data[1] == 0x31.toByte() &&
            declared == length &&
            declared >= 32
    }

    /** Must be called off the Android main thread. No device state is modified. */
    fun probe(host: String): ProbeResult {
        if (!isValidPrivateIpv4(host)) {
            return ProbeResult(false, "آدرس IP معتبر و خصوصی نیست")
        }
        return try {
            // Numeric IPv4 only: no DNS and no internet-facing targets.
            val raw = host.trim().split(".").map { it.toInt().toByte() }.toByteArray()
            val target = InetAddress.getByAddress(raw)
            DatagramSocket().use { socket ->
                socket.soTimeout = RESPONSE_TIMEOUT_MS
                val hello = helloPacket()
                socket.send(DatagramPacket(hello, hello.size, target, MIIO_PORT))
                val buffer = ByteArray(4096)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                if (reply.address == target && isValidHelloReply(buffer, reply.length)) {
                    ProbeResult(
                        true,
                        "دوربین به پیام اولیه MIIO پاسخ داد؛ ارتباط محلی اولیه برقرار است. " +
                            "قابلیت فرمان Wake/Sleep هنوز تأیید نشده."
                    )
                } else {
                    ProbeResult(false, "پاسخ معتبر از دوربین دریافت نشد")
                }
            }
        } catch (_: SocketTimeoutException) {
            ProbeResult(
                false,
                "دوربین در شبکه به پیام اولیه MIIO پاسخ نداد. " +
                    "احتمالاً این مدل اتصال محلی را محدود کرده یا شبکه دسترسی مستقیم نمی‌دهد."
            )
        } catch (_: Exception) {
            ProbeResult(false, "بررسی اتصال محلی ناموفق بود؛ Wi-Fi و IP را بررسی کنید.")
        }
    }
}
