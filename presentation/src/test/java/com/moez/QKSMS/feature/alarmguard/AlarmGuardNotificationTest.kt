package com.moez.QKSMS.feature.alarmguard

import com.moez.QKSMS.util.AlarmGuardNotification
import com.moez.QKSMS.util.AlarmGuardParser
import org.junit.Assert.*
import org.junit.Test

class AlarmGuardNotificationTest {
    private val configured = "+98 903 222 7190"
    private val arm = "فعال شد,فعال گردید,روشن شد"
    private val disarm = "غیر فعال شد,غیرفعال شد,خاموش شد"

    private fun event(
        sender: String? = "09032227190",
        body: String = "کل بخش ها فعال شد از طریق (ریموت۱)",
        enabled: Boolean = true,
        number: String? = configured
    ) = AlarmGuardNotification.fromSms(enabled, sender, number, body, arm, disarm)

    @Test fun armedNotificationRecognisesConfiguredNumberAndRemote() {
        val found = event()!!
        assertEquals(AlarmGuardParser.Status.ARMED, found.status)
        assertTrue(found.detail!!.contains("ریموت"))
        assertEquals("کل بخش ها فعال شد از طریق (ریموت۱)", found.body)
    }

    @Test fun disarmHasPriorityOverArmedSubstring() {
        val found = event(body = "کل بخش ها غیرفعال شد از طریق (پیامک)")!!
        assertEquals(AlarmGuardParser.Status.DISARMED, found.status)
    }

    @Test fun foreignSenderNeverMasqueradesAsAlarm() {
        assertNull(event(sender = "09121234567"))
        assertNull(event(sender = "ALARM", body = "کل بخش ها فعال شد"))
        assertNull(event(number = "1234", sender = "1234"))
    }

    @Test fun inactiveGuardOrUnknownTextUsesNormalSms() {
        assertNull(event(enabled = false))
        assertNull(event(body = "وضعیت دستگاه بررسی شد"))
        assertNull(event(number = ""))
    }

    @Test fun internationalAndPersianDigitsResolveSameDevice() {
        assertNotNull(event(sender = "0098 903 222 7190"))
        assertNotNull(event(sender = "۰۹۰۳۲۲۲۷۱۹۰"))
    }

    @Test fun customKeywordsWorkForConfiguredDeviceOnly() {
        val found = AlarmGuardNotification.fromSms(
            true, "09032227190", configured, "سیستم در حالت شب قرار گرفت",
            "حالت شب", "حالت روز"
        )
        assertEquals(AlarmGuardParser.Status.ARMED, found?.status)
        assertNull(AlarmGuardNotification.fromSms(
            true, "09121234567", configured, "سیستم در حالت شب قرار گرفت",
            "حالت شب", "حالت روز"
        ))
    }
}
