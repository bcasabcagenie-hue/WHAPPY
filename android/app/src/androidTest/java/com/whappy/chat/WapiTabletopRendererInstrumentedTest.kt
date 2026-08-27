package com.whappy.chat

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WapiTabletopRendererInstrumentedTest {
    @Test
    fun poolCheckersAndChessShadersRenderOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        lateinit var renderer: WapiTabletop3DView
        instrumentation.runOnMainSync {
            renderer = WapiTabletop3DView(activity)
            activity.setContentView(renderer)
            renderer.setPoolScene(
                balls = listOf(floatArrayOf(.23f, .5f, 0f, 0f, 0f)),
                aimAngle = 0f,
                power = 70,
                moving = false,
            )
        }
        SystemClock.sleep(900L)
        instrumentation.runOnMainSync {
            renderer.setStrategyScene(
                WapiTabletop3DView.Scene.CHECKERS,
                board = List(64) { index -> if (index == 17) "b" else if (index == 46) "w" else "" },
                selected = 46,
                legalTargets = setOf(37),
            )
        }
        SystemClock.sleep(450L)
        instrumentation.runOnMainSync {
            renderer.setStrategyScene(
                WapiTabletop3DView.Scene.CHESS,
                board = listOf("♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜") +
                    List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } +
                    listOf("♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"),
                selected = 52,
                legalTargets = setOf(44, 36),
            )
        }
        SystemClock.sleep(450L)
        assertTrue(renderer.width > 0 && renderer.height > 0)
        instrumentation.runOnMainSync { activity.finish() }
    }
}
