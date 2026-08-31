package com.whappy.chat

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WapiPoolAudioAssetTest {
    @Test fun impactsAreAudibleDistinctAndIdenticalOnBothPlatforms() {
        val samples = listOf("cue", "collision", "cushion").map { sound ->
            val name = "wapi_pool_$sound.wav"
            val bytes = File("src/main/res/raw/$name").readBytes()
            assertArrayEquals("Cross-platform sound mismatch", bytes, File("../../ios/Whappy/GameAudio/$name").readBytes())
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 12
            var audible = false
            while (offset + 8 <= bytes.size) {
                val size = buffer.getInt(offset + 4)
                require(size >= 0 && offset + 8L + size <= bytes.size)
                if (String(bytes, offset, 4, Charsets.US_ASCII) == "data") {
                    val values = (offset + 8 until offset + 8 + size step 2).map { buffer.getShort(it).toInt() }
                    assertTrue("Impact duration is invalid", values.size in 4000..10000)
                    assertTrue("Clipped sample", values.all { kotlin.math.abs(it) < 32767 })
                    val rms = kotlin.math.sqrt(values.map { it.toDouble() * it }.average())
                    audible = rms > 500
                    break
                }
                offset += 8 + size + size % 2
            }
            assertTrue("Silent or missing PCM in $name", audible)
            bytes
        }
        assertFalse("Cue and collision must be distinct recordings", samples[0].contentEquals(samples[1]))
    }
}
