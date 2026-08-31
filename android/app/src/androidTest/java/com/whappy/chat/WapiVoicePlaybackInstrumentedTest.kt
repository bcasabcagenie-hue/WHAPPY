package com.whappy.chat

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real local PCM + real MediaPlayer, no Firebase account or network fixture. */
@RunWith(AndroidJUnit4::class)
class WapiVoicePlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(80)
        assertTrue(message, condition())
    }
    private fun find(label: String): AccessibilityNodeInfo? = nodes(instrumentation.uiAutomation.rootInActiveWindow)
        .firstOrNull { it.contentDescription?.toString() == label || it.text?.toString() == label }
    private fun click(label: String) {
        waitFor("Missing control: $label") { find(label) != null }
        var node = find(label)!!
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue("Click failed: $label", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun pausedSpeedSeekExclusivePlaybackAndBackgroundAreRespected() {
        val context = instrumentation.targetContext
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.CommerceChatVisualTestActivity")
            .putExtra("screen", "audio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            click("Lire · Note test A")
            waitFor("First note did not start") { audio.isMusicActive && find("Mettre en pause · Note test A") != null }
            click("Mettre en pause · Note test A")
            waitFor("Pause did not stop audio") { !audio.isMusicActive }
            click("1×")
            SystemClock.sleep(400)
            assertTrue("Changing paused speed started playback", !audio.isMusicActive)

            val slider = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.rangeInfo != null }
            val range = slider.rangeInfo!!
            val arguments = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, range.min + (range.max - range.min) * .6f) }
            assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments))
            SystemClock.sleep(400)
            assertTrue("Seeking a paused note started playback", !audio.isMusicActive)

            click("Lire · Note test A")
            waitFor("Resume did not start") { audio.isMusicActive }
            click("Lire · Note test B")
            waitFor("Old note was not paused") { find("Lire · Note test A") != null && find("Mettre en pause · Note test B") != null }
            instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
            waitFor("Background playback was not stopped") { !audio.isMusicActive }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun seekingBeforeFirstPlayStaysSilentAndCompletedNoteCanReplay() {
        val context = instrumentation.targetContext
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.CommerceChatVisualTestActivity")
            .putExtra("screen", "audio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitFor("Audio controls missing") { find("Lire · Note test B") != null }
            val slider = nodes(instrumentation.uiAutomation.rootInActiveWindow).last { it.rangeInfo != null }
            val range = slider.rangeInfo!!
            val arguments = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, range.min + (range.max - range.min) * .8f) }
            assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments))
            waitFor("Initial seek not prepared") { find("0:20 / 0:25") != null }
            assertTrue("Preparation for seeking unexpectedly played audio", !audio.isMusicActive)
            click("Lire · Note test B")
            waitFor("Playback from seek did not start") { audio.isMusicActive }
            waitFor("Completion did not update the play control") { !audio.isMusicActive && find("Lire · Note test B") != null }
            click("Lire · Note test B")
            waitFor("Completed note cannot replay") { audio.isMusicActive }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun catalogueAvailabilityAndCategoryFiltersUseRealRows() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent().setClassName(context, "com.whappy.chat.CommerceChatVisualTestActivity")
            .putExtra("screen", "commerce").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            click("Café Atelier · TEST")
            waitFor("Catalogue did not open") { find("Tout · 2") != null }
            click("Filtrer les articles disponibles")
            waitFor("Availability filter did not change the real count") { find("Tout · 1") != null }
            click("Petits déjeuners · 0")
            waitFor("Filtered empty result missing") { find("Réinitialiser les filtres") != null }
            click("Réinitialiser les filtres")
            waitFor("Reset did not restore the two articles") { find("Tout · 2") != null }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
