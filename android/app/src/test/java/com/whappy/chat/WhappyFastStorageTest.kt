package com.whappy.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhappyFastStorageTest {
    @Test
    fun `uses MMKV only when its native library is packaged`() {
        assertTrue(WhappyFastStorage.supportsPackagedMmkv(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertTrue(WhappyFastStorage.supportsPackagedMmkv(arrayOf("x86_64", "x86")))
        assertFalse(WhappyFastStorage.supportsPackagedMmkv(arrayOf("armeabi-v7a")))
        assertFalse(WhappyFastStorage.supportsPackagedMmkv(arrayOf("x86")))
        assertFalse(WhappyFastStorage.supportsPackagedMmkv(emptyArray()))
    }
}
