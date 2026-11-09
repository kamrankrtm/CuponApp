package com.moez.QKSMS.feature.alarmguard

import com.moez.QKSMS.util.AlarmCameraAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmCameraAutomationTest {
    private fun action(
        sender: String? = "09032227190",
        body: String = "کل بخش ها فعال شد از طریق (ریموت۱)",
        timestamp: Long = 1800000000000L,
        previous: Long = 0L,
        enabled: Boolean = true
    ) = AlarmCameraAutomation.sceneForSms(
        enabled, sender, "+989032227190", body,
        "فعال شد,فعال گردید,روشن شد",
        "غیر فعال شد,غیرفعال شد,خاموش شد",
        timestamp, previous
    )

    @Test fun armedWakesCamera() {
        assertEquals(AlarmCameraAutomation.Scene.WAKE, action())
    }

    @Test fun disarmedSleepsCamera() {
        assertEquals(AlarmCameraAutomation.Scene.SLEEP,
            action(body = "کل بخش ها غیرفعال شد از طریق (ریموت۱)"))
    }

    @Test fun ignoresForeignSenderAndWarnings() {
        assertNull(action(sender = "09121234567"))
        assertNull(action(body = "باتری سنسور بررسی شود"))
    }

    @Test fun ignoresOldDuplicatesAndDisabledEvents() {
        assertNull(action(previous = 1800000000000L))
        assertNull(action(previous = 1900000000000L))
        assertNull(action(enabled = false))
        assertNull(action(timestamp = 0L))
    }

    @Test fun acceptsPersianDigits() {
        assertEquals(AlarmCameraAutomation.Scene.WAKE, action(sender = "۰۹۰۳۲۲۲۷۱۹۰"))
    }
}
