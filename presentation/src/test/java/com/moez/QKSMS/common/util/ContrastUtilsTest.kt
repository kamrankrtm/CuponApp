package com.moez.QKSMS.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down the readability rules for list text.
 *
 * The values here are not invented: the background and snippet colours were sampled from a
 * screenshot of the app running in the dark theme, where the message preview rendered at about
 * 2.5:1 and was hard to read.
 */
class ContrastUtilsTest {

    private val backgroundDark = 0xFF192025.toInt()
    private val backgroundLight = 0xFFFFFFFF.toInt()

    /** The snippet colour actually painted on screen, sampled from the reported screenshot. */
    private val measuredSnippet = 0xFF625D59.toInt()

    @Test
    fun `known wcag ratios`() {
        assertEquals(21.0, ContrastUtils.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ContrastUtils.contrastRatio(backgroundDark, backgroundDark), 0.01)
    }

    @Test
    fun `alpha is composited before measuring`() {
        // The old guard used a luminance function that ignores alpha, so a translucent black
        // was judged by its opaque colour and left on a dark background unchanged.
        val translucentBlack = 0x8A000000.toInt()
        assertTrue(ContrastUtils.contrastRatio(translucentBlack, backgroundDark) < 2.0)
        assertTrue(
            ContrastUtils.contrastRatio(
                ContrastUtils.ensureContrast(translucentBlack, backgroundDark),
                backgroundDark
            ) >= ContrastUtils.MIN_CONTRAST_BODY
        )
    }

    @Test
    fun `readable colours are returned untouched`() {
        val secondaryOnDark = 0xCCFFFFFF.toInt()
        assertTrue(ContrastUtils.contrastRatio(secondaryOnDark, backgroundDark) >= ContrastUtils.MIN_CONTRAST_BODY)
        assertEquals(secondaryOnDark, ContrastUtils.ensureContrast(secondaryOnDark, backgroundDark))
    }

    @Test
    fun `the reported snippet colour is repaired`() {
        assertTrue(
            "should have been failing",
            ContrastUtils.contrastRatio(measuredSnippet, backgroundDark) < ContrastUtils.MIN_CONTRAST_BODY
        )
        val fixed = ContrastUtils.ensureContrast(measuredSnippet, backgroundDark)
        assertTrue(ContrastUtils.contrastRatio(fixed, backgroundDark) >= ContrastUtils.MIN_CONTRAST_BODY)
    }

    @Test
    fun `repair keeps secondary text softer than primary`() {
        // Jumping straight to full white would flatten the difference between a conversation
        // title and its preview, which is the whole point of the secondary colour.
        val fixed = ContrastUtils.ensureContrast(measuredSnippet, backgroundDark)
        val fixedLuminance = ContrastUtils.relativeLuminance(ContrastUtils.composite(fixed, backgroundDark))
        val whiteLuminance = ContrastUtils.relativeLuminance(0xFFFFFFFF.toInt())
        assertTrue(fixedLuminance < whiteLuminance)
    }

    @Test
    fun `the decisive repair returns plain white on a dark background`() {
        // What the list actually uses. A colour that merely clears 4.5:1 still reads as grey
        // on a dark screen, so a failing colour goes all the way to white.
        assertEquals(0xFFFFFFFF.toInt(), ContrastUtils.ensureReadable(measuredSnippet, backgroundDark))
        assertEquals(0xFFFFFFFF.toInt(), ContrastUtils.ensureReadable(0x4DFFFFFF.toInt(), backgroundDark))
        assertEquals(0xFFFFFFFF.toInt(), ContrastUtils.ensureReadable(0x8A000000.toInt(), 0xFF000000.toInt()))
    }

    @Test
    fun `the decisive repair leaves a readable colour alone`() {
        val secondaryOnDark = 0xCCFFFFFF.toInt()
        assertEquals(secondaryOnDark, ContrastUtils.ensureReadable(secondaryOnDark, backgroundDark))
    }

    @Test
    fun `the decisive repair returns black on a light background`() {
        assertEquals(0xFF000000.toInt(), ContrastUtils.ensureReadable(0xFFE8E8E8.toInt(), backgroundLight))
    }

    @Test
    fun `translucent white is what the old alpha blind check let through`() {
        // 30% white composites to roughly 2.9:1 here, which is what the device was showing,
        // yet its opaque form is pure white and so passed a luminance-only test.
        val thirtyPercentWhite = 0x4DFFFFFF.toInt()
        assertTrue(ContrastUtils.contrastRatio(thirtyPercentWhite, backgroundDark) < 3.5)
        assertEquals(1.0, ContrastUtils.relativeLuminance(0xFFFFFFFF.toInt()), 0.001)
    }

    @Test
    fun `light backgrounds are darkened instead`() {
        val tooLight = 0xFFE8E8E8.toInt()
        val fixed = ContrastUtils.ensureContrast(tooLight, backgroundLight)
        assertTrue(ContrastUtils.contrastRatio(fixed, backgroundLight) >= ContrastUtils.MIN_CONTRAST_BODY)
        assertTrue(
            "should darken, not lighten",
            ContrastUtils.relativeLuminance(ContrastUtils.composite(fixed, backgroundLight)) <
                ContrastUtils.relativeLuminance(tooLight)
        )
    }
}
