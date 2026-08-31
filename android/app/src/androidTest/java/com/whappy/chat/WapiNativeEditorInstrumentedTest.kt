package com.whappy.chat

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real editor destinations with debug-only local save callbacks. No production writes. */
@RunWith(AndroidJUnit4::class)
class WapiNativeEditorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun all(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        return nodes(automation.rootInActiveWindow)
    }
    private fun find(text: String) = all().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(80)
        assertTrue("$message: " + all().mapNotNull { it.text?.toString() }.joinToString(" / "), predicate())
    }
    private fun control(text: String): AccessibilityNodeInfo {
        var node = find(text) ?: error("Control absent: $text")
        while (!node.isClickable && node.parent != null) node = node.parent
        return node
    }
    private fun edit(label: String, text: String) {
        reveal(label)
        var node = find(label) ?: error(label)
        while (!node.isEditable && node.parent != null) node = node.parent
        assertTrue("Not a text field: $label", node.isEditable)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        // Bringing a lazy item above the IME may replace its accessibility node.
        node = find(label) ?: error(label)
        while (!node.isEditable && node.parent != null) node = node.parent
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
        await("Text was not applied to $label") { all().any { it.isEditable && it.text?.toString() == text } }
    }
    private fun reveal(text: String) {
        repeat(16) {
            if (find(text) != null) return
            all().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(180)
        }
        error("Could not scroll to $text")
    }
    private fun activity() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first() as CommerceChatVisualTestActivity
    private fun runEditor(screen: String, test: () -> Unit) {
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        instrumentation.startActivitySync(Intent().setClassName(instrumentation.targetContext, "com.whappy.chat.CommerceChatVisualTestActivity")
            .putExtra("screen", screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            await("Editor not shown") { find("Fermer l’éditeur") != null }
            test()
        } finally { instrumentation.runOnMainSync { activity().finish() } }
    }
    private fun keyboardTop(): Int? = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { Rect().apply { it.getBoundsInScreen(this) }.top }

    @Test fun businessEditorKeepsActionAboveKeyboardAndDraftAfterRotation() = runEditor("editor-business") {
        assertFalse(control("Créer le profil").isEnabled)
        edit("Nom de l’entreprise", "Atelier Test")
        edit("Activité", "Restaurant")
        await("Keyboard not visible") { keyboardTop() != null }
        await("Action overlaps keyboard") {
            val rect = Rect(); control("Créer le profil").getBoundsInScreen(rect)
            rect.bottom <= (keyboardTop() ?: 0) && rect.height() > 60 && control("Créer le profil").isEnabled
        }
        instrumentation.runOnMainSync { activity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        await("Landscape not ready") { instrumentation.targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        // Returning to portrait must restore both fields, not just their labels.
        instrumentation.runOnMainSync { activity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        await("Portrait not ready") { instrumentation.targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        await("Draft lost during rotation") { all().any { it.isEditable && it.text?.toString() == "Atelier Test" } }
        assertTrue(control("Créer le profil").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("Submission not received") {
            var value: String? = null; instrumentation.runOnMainSync { value = activity().editorLastSubmission }
            value == "Atelier Test|Restaurant"
        }
        await("Duplicate save not blocked") { find("Enregistrement en cours…") != null && !control("Enregistrement en cours…").isEnabled }
    }

    @Test fun groupSelectionSurvivesSearchAndKeyboard() = runEditor("editor-group") {
        edit("Nom du groupe", "Équipe Test")
        edit("Rechercher un contact", "Contact 24")
        reveal("Contact 24")
        // The search field has exactly the same text as the result. Target the
        // selectable row, not the editable search field.
        val row = all().first { it.isCheckable && nodes(it).any { child -> child.text?.toString() == "Contact 24" } }
        assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("Group creation still disabled") { control("Créer le groupe").isEnabled }
        assertTrue(control("Créer le groupe").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("Selected member was not submitted") {
            var value: String? = null; instrumentation.runOnMainSync { value = activity().editorLastSubmission }
            value == "Équipe Test|1"
        }
    }

    @Test fun offerFieldsRemainReachableAndValidationPreventsInvalidPrice() = runEditor("editor-offer") {
        edit("Nom de l’offre", "Menu du jour")
        edit("Description", "Plat et dessert maison")
        edit("Prix habituel · FCFA", "5000")
        edit("Prix de l’offre · FCFA", "6000")
        await("Invalid price accepted") { !control("Publier l’offre").isEnabled }
        edit("Prix de l’offre · FCFA", "4500")
        edit("Quantité disponible", "20")
        edit("Durée · jours", "31")
        await("Invalid duration accepted") { !control("Publier l’offre").isEnabled }
        edit("Durée · jours", "7")
        await("Offer not valid") { control("Publier l’offre").isEnabled }
        assertTrue(control("Publier l’offre").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("Offer was not submitted") {
            var value: String? = null; instrumentation.runOnMainSync { value = activity().editorLastSubmission }
            value == "Menu du jour"
        }
    }
}
