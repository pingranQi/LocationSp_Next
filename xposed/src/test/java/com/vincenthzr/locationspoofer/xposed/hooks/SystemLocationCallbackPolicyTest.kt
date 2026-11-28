package com.vincenthzr.locationspoofer.xposed.hooks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemLocationCallbackPolicyTest {
    @Test
    fun `persistent location listener is not released after first onLocation`() {
        assertFalse(shouldReleaseOneShotLocationCallback("onLocation", false))
    }

    @Test
    fun `getCurrentLocation callback is released after onLocation`() {
        assertTrue(shouldReleaseOneShotLocationCallback("onLocation", true))
    }

    @Test
    fun `other callback methods are not treated as one shot`() {
        assertFalse(shouldReleaseOneShotLocationCallback("onStatusChanged", true))
    }
}
