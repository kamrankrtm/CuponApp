package com.moez.QKSMS.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CI tags each release v<version>.<run> (v3.0.5.61) and builds the app with the same version
 * name, so the installed app can tell whether the latest release is newer than itself.
 *
 * If these compare the wrong way round the app either never offers an update (as it did while
 * releases were tagged v2.0.2-N and the app called itself 3.0.5) or offers one forever.
 */
class AppUpdateCheckerTest {

    @Test
    fun `a later build of the same version is an update`() {
        assertTrue(AppUpdateChecker.isNewerVersion("v3.0.5.62", "3.0.5.61"))
    }

    @Test
    fun `the installed build itself is not an update`() {
        assertFalse(AppUpdateChecker.isNewerVersion("v3.0.5.61", "3.0.5.61"))
    }

    @Test
    fun `builds from before build numbers are offered the first numbered release`() {
        assertTrue(AppUpdateChecker.isNewerVersion("v3.0.5.61", "3.0.5"))
    }

    @Test
    fun `the old v2 tags are older than any 3 build`() {
        assertFalse(AppUpdateChecker.isNewerVersion("v2.0.2-60", "3.0.5.61"))
        assertFalse(AppUpdateChecker.isNewerVersion("v2.0.2-60", "3.0.5"))
    }

    @Test
    fun `a new version wins over a higher build number`() {
        assertTrue(AppUpdateChecker.isNewerVersion("v3.1.0.70", "3.0.5.69"))
        assertFalse(AppUpdateChecker.isNewerVersion("v3.0.5.99", "3.1.0.70"))
    }
}
