package com.vincenthzr.locationspoofer.xposed.hooks.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleReportDelayPolicyTest {
    @Test
    fun `zero report delay dispatches individual results`() {
        assertFalse(shouldBatchBleResults(0L))
    }

    @Test
    fun `positive report delay dispatches one batch`() {
        assertTrue(shouldBatchBleResults(1L))
        assertTrue(shouldBatchBleResults(5_000L))
    }

    @Test
    fun `negative report delay is not treated as batch mode`() {
        assertFalse(shouldBatchBleResults(-1L))
    }
}
