package com.whappy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Native editor destination: one scroll owner, no fixed form height and no IME overlap. */
@Composable
internal fun WapiEditorScreen(
    title: String,
    action: String,
    busy: Boolean,
    ready: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val statusLight = controller?.isAppearanceLightStatusBars ?: false
            val navigationLight = controller?.isAppearanceLightNavigationBars ?: false
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
            onDispose {
                controller?.isAppearanceLightStatusBars = statusLight
                controller?.isAppearanceLightNavigationBars = navigationLight
            }
        }
        Column(
            Modifier.fillMaxSize().background(Color.White)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .testTag("wapi-native-editor"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 56.dp).padding(end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Fermer l’éditeur", tint = WhappyInk)
                }
                Text(title, color = WhappyInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
            HorizontalDivider(color = WhappyLine.copy(alpha = .6f))
            LazyColumn(
                Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().testTag("wapi-editor-content"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            HorizontalDivider(color = WhappyLine.copy(alpha = .6f))
            Button(
                onClick = { if (ready && !busy) onSubmit() },
                enabled = ready && !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp).testTag("wapi-editor-submit"),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Enregistrement en cours…")
                } else Text(action, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun WapiEditorSection(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = WhappyInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        if (description != null) Text(description, color = WhappyMuted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
internal fun WapiEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    limit: Int,
    enabled: Boolean = true,
    multiline: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = { onValueChange(it.take(limit)) }, enabled = enabled,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 6 else 1,
        supportingText = if (multiline) ({ Text("${value.length}/$limit") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
    )
}
