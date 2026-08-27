package com.whappy.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WapiVulkanCapabilitiesInstrumentedTest {
    @Test
    fun nativeVulkanProbeReturnsAUsableGraphicsDecision() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = WapiVulkanCapabilities.inspect(context)
        assertTrue(report.backend.isNotBlank())
        assertTrue(report.reason.isNotBlank())
        if (report.available) {
            assertTrue(report.apiVersion != "0.0.0")
            assertTrue(report.deviceName.isNotBlank())
        }
    }
}

