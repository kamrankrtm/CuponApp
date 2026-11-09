package com.moez.QKSMS.feature.alarmguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiLanProbeTest {
    @Test fun validPrivateIpv4() {
        assertTrue(XiaomiLanProbe.isValidPrivateIpv4("192.168.1.178"))
        assertTrue(XiaomiLanProbe.isValidPrivateIpv4("10.0.0.2"))
        assertTrue(XiaomiLanProbe.isValidPrivateIpv4("172.17.0.1"))
        assertFalse(XiaomiLanProbe.isValidPrivateIpv4("8.8.8.8"))
        assertFalse(XiaomiLanProbe.isValidPrivateIpv4("192.168.01.3"))
        assertFalse(XiaomiLanProbe.isValidPrivateIpv4("192.168.1"))
        assertFalse(XiaomiLanProbe.isValidPrivateIpv4("localhost"))
        assertFalse(XiaomiLanProbe.isValidPrivateIpv4("192.168.1.999"))
    }

    @Test fun helloHasCorrectMagicAndLength() {
        val bytes = XiaomiLanProbe.helloPacket()
        assertEquals(32, bytes.size)
        assertEquals(0x21, bytes[0].toInt() and 0xff)
        assertEquals(0x31, bytes[1].toInt() and 0xff)
        assertEquals(0, bytes[2].toInt() and 0xff)
        assertEquals(32, bytes[3].toInt() and 0xff)
        assertEquals(0xff, bytes[31].toInt() and 0xff)
    }

    @Test fun validReplyNeedsRealHeaderAndLength() {
        val hello = XiaomiLanProbe.helloPacket()
        assertTrue(XiaomiLanProbe.isValidHelloReply(hello, hello.size))
        assertFalse(XiaomiLanProbe.isValidHelloReply(hello, 12))
        hello[3] = 0x21
        assertFalse(XiaomiLanProbe.isValidHelloReply(hello, 32))
        hello[3] = 0x20
        hello[0] = 0
        assertFalse(XiaomiLanProbe.isValidHelloReply(hello, 32))
    }
}
