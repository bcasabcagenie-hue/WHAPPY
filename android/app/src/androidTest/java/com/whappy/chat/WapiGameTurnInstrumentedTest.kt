package com.whappy.chat

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WapiGameTurnInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun find(text: String): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        return nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
    }
    private fun await(message: String, timeout: Long = 12000, predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + timeout
        while (!predicate() && SystemClock.uptimeMillis() < until) SystemClock.sleep(60)
        assertTrue(message + " | " + nodes(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.joinToString(" / "), predicate())
    }
    private fun click(text: String) {
        await("Missing control $text") { find(text) != null }
        var node = find(text) ?: error("Missing $text")
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun tabletop(view: View): WapiTabletop3DView? = if (view is WapiTabletop3DView) view else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { tabletop(view.getChildAt(it)) } else null
    // Landscape entry may recreate the Activity returned by startActivitySync.
    // Always drive the displayed activity rather than its detached predecessor.
    private fun activeActivity() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()

    private fun tapPieceOrTile(index: Int, dimension: Int, height: Float) {
        val point = FloatArray(2)
        await("Renderer camera not ready") {
        var ready = false
        instrumentation.runOnMainSync {
            val view = tabletop(activeActivity().window.decorView)!!
            // Test-only projection through the renderer's published camera.
            val renderer = view.javaClass.getDeclaredField("tabletopRenderer").apply { isAccessible = true }.get(view)!!
            val inverse = renderer.javaClass.getDeclaredField("inputInverseVp").apply { isAccessible = true }.get(renderer) as FloatArray
            val vp = FloatArray(16)
            if (!android.opengl.Matrix.invertM(vp, 0, inverse, 0)) return@runOnMainSync
            val projected = FloatArray(4)
            val cell = 8f / dimension
            android.opengl.Matrix.multiplyMV(projected, 0, vp, 0, floatArrayOf(-4f + (index % dimension + .5f) * cell, height, -4f + (index / dimension + .5f) * cell, 1f), 0)
            val location = IntArray(2); view.getLocationOnScreen(location)
            point[0] = location[0] + (projected[0] / projected[3] + 1f) * view.width * .5f
            point[1] = location[1] + (1f - projected[1] / projected[3]) * view.height * .5f
            // Full-screen insets can resize the Surface after its first frame.
            // Only inject the screen tap when projection and picking agree.
            val pick = renderer.javaClass.getDeclaredMethod("pickSquare", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(renderer, point[0] - location[0], point[1] - location[1]) as? Int
            ready = pick == index
        }
        ready
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
        }
    }

    @Test fun strategyDifficultyChangeDuringThinkingDoesNotLockPlayer() {
        val context = instrumentation.targetContext
        for (game in listOf("chess", "checkers")) {
            val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.PoolVisualTestActivity").putExtra("game", game).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                await("Board not ready") { find("À VOUS") != null }
                // A second landscape Activity can draw before its enter
                // transition accepts touches. Wait for the accessibility tree
                // to settle instead of sending a tap into that transition.
                instrumentation.uiAutomation.waitForIdle(500, 5000)
                // Real screen taps on the piece volume, then its destination.
                tapPieceOrTile(if (game == "chess") 52 else 61, if (game == "chess") 8 else 10, if (game == "chess") .94f else .40f)
                await("Piece was not selected: $game") {
                    var selected = -1
                    instrumentation.runOnMainSync {
                        val view = tabletop(activeActivity().window.decorView)!!
                        val renderer = view.javaClass.getDeclaredField("tabletopRenderer").apply { isAccessible = true }.get(view)!!
                        selected = renderer.javaClass.getDeclaredField("selected").apply { isAccessible = true }.getInt(renderer)
                    }
                    selected == if (game == "chess") 52 else 61
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(200)
                tapPieceOrTile(if (game == "chess") 36 else 50, if (game == "chess") 8 else 10, .18f)
                click("IA MOYENNE ▾")
                click("IA FACILE")
                await("Difficulty change stranded the AI turn") { find("À vous de jouer.") != null || find("L’IA termine sa prise. À vous de jouer.") != null }
                click("REJOUER")
                await("Restart did not restore player") { find("À VOUS") != null }
            } finally { instrumentation.runOnMainSync { activeActivity().finish() } }
        }
    }

    @Test fun poolRealPullReleasesAndAiReturnsControl() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.PoolVisualTestActivity").putExtra("mode", "ai").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await("Table not ready") { find("Puissance du tir") != null }
            // Deliberate miss, so the rules hand over to the AI deterministically.
            instrumentation.runOnMainSync { tabletop(activeActivity().window.decorView)!!.onPoolGesture!!.invoke(.23f, .15f, false) }
            SystemClock.sleep(200)
            val rect = Rect(); find("Puissance du tir")!!.getBoundsInScreen(rect)
            val x = rect.exactCenterX(); val y = rect.top + rect.height() * .15f
            val start = SystemClock.uptimeMillis()
            fun event(action: Int, yy: Float) {
                val e = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, yy, 0)
                instrumentation.uiAutomation.injectInputEvent(e, true); e.recycle()
            }
            event(MotionEvent.ACTION_DOWN, y)
            repeat(10) { SystemClock.sleep(25); event(MotionEvent.ACTION_MOVE, y + rect.height() * .025f * (it + 1)) }
            event(MotionEvent.ACTION_UP, y + rect.height() * .25f)
            await("Shot did not lock the rail") { find("Puissance du tir")?.stateDescription?.toString() == "Patientez" }
            await("AI never shot or gave control back", 45000) {
                val state = find("Table de billard")?.stateDescription?.toString().orEmpty()
                val shots = state.substringBefore(" tirs").toIntOrNull() ?: 0
                shots >= 2 && (state.endsWith("À vous") || state.endsWith("Placez la blanche") || state.endsWith("DÉFAITE"))
            }
        } finally { instrumentation.runOnMainSync { activeActivity().finish() } }
    }

    @Test fun cueHandRequiresValidPlacementAndExplicitConfirmation() {
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.PoolVisualTestActivity").putExtra("mode", "training").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            click("Placer la blanche")
            await("Cue hand did not disable shooting") { find("Puissance du tir")?.stateDescription?.toString() == "Patientez" }
            instrumentation.runOnMainSync {
                val view = tabletop(activeActivity().window.decorView)!!
                view.onPoolGesture!!.invoke(.20f, .40f, false)
                view.onPoolGesture!!.invoke(.20f, .40f, true)
            }
            await("Lifting the finger must keep the placement confirmation visible") { find("Poser la blanche") != null }
            assertTrue(find("Table de billard")?.stateDescription?.toString()?.endsWith("Placez la blanche") == true)
            click("Poser la blanche")
            await("Confirming cue placement did not restore shooting") { find("Puissance du tir")?.stateDescription?.toString() != "Patientez" }
            assertTrue(find("Table de billard")?.stateDescription?.toString()?.endsWith("À vous") == true)
        } finally { instrumentation.runOnMainSync { activeActivity().finish() } }
    }

    @Test fun fineAimWheelChangesDirectionWithoutFiring() {
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.PoolVisualTestActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await("Fine aim wheel not visible") { find("Visée précise") != null }
            val bounds = Rect(); find("Visée précise")!!.getBoundsInScreen(bounds)
            val start = SystemClock.uptimeMillis()
            for (step in 0..8) {
                val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), when (step) { 0 -> MotionEvent.ACTION_DOWN; 8 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE },
                    bounds.exactCenterX(), bounds.top + bounds.height() * (.25f + step * .05f), 0)
                instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle(); SystemClock.sleep(20)
            }
            await("Fine aiming must leave the shot unplayed") { find("Table de billard")?.stateDescription?.toString()?.startsWith("0 tirs") == true }
            await("Fine wheel did not change the actual cue direction") {
                var angle = 0f
                instrumentation.runOnMainSync {
                    val view = tabletop(activeActivity().window.decorView)!!
                    val renderer = view.javaClass.getDeclaredField("tabletopRenderer").apply { isAccessible = true }.get(view)!!
                    angle = renderer.javaClass.getDeclaredField("poolAim").apply { isAccessible = true }.getFloat(renderer)
                }
                angle > .05f
            }
            assertNotEquals("Patientez", find("Puissance du tir")?.stateDescription?.toString())
        } finally { instrumentation.runOnMainSync { activeActivity().finish() } }
    }

    @Test fun recentsOpensOnceFullScreenAndClosesWithReverseGesture() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.CommerceChatVisualTestActivity").putExtra("screen", "recents").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await("Messages not ready") { find("Contact de test") != null }
            val metrics = context.resources.displayMetrics
            fun swipe(from: Float, to: Float) {
                val start = SystemClock.uptimeMillis()
                for (step in 0..16) {
                    val e = MotionEvent.obtain(start, SystemClock.uptimeMillis(), when(step) { 0 -> MotionEvent.ACTION_DOWN; 16 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }, metrics.widthPixels * .48f, metrics.heightPixels * (from + (to - from) * step / 16f), 0)
                    instrumentation.uiAutomation.injectInputEvent(e, true); e.recycle(); SystemClock.sleep(20)
                }
            }
            repeat(2) {
                swipe(.52f, .80f)
                await("Récents not opened with one pull") { find("Récents WAPI") != null }
                val bounds = Rect(); instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(bounds)
                assertTrue("Récents must fill the window", bounds.height() > metrics.heightPixels * .92f)
                assertNull("Messages must not be the active window under Récents", find("Contact de test"))
                swipe(.80f, .45f)
                await("Reverse swipe did not restore Messages") { find("Contact de test") != null && find("Récents WAPI") == null }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
