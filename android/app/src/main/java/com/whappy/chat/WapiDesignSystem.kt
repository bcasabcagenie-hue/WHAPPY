package com.whappy.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Adaptateur Android du fichier design-system/wapi.tokens.json.
internal val WhappyBlue = Color(0xFF0094F0)
internal val WhappyDark = Color(0xFF191919)
internal val WhappyInk = Color(0xFF202020)
internal val WhappyMuted = Color(0xFF6F7780)
internal val WhappyBackground = Color(0xFFF5F7F9)
internal val WhappySurface = Color(0xFFF1F4F7)
internal val WhappyNavy = Color(0xFF0066CF)
internal val WhappyLine = Color(0xFFE3E8ED)
internal val WhappyDeepBlue = Color(0xFF0066CF)
internal val WhappySky = Color(0xFF00A2E6)
internal val WapiVerifiedGray = Color(0xFF858D96)
internal val WapiChatAccent = WhappyBlue
internal val WapiBubbleOutgoing = WhappyBlue
internal val WapiChatBackground = Color(0xFFF4F9FC)
internal val WapiToolbar = Color.White
internal val WapiActionPanel = Color(0xFF0A3557)
internal val WhappyAurora = Brush.linearGradient(listOf(WhappyBlue, WhappySky, WhappyDeepBlue))
internal val WhappyAuroraSoft = Brush.linearGradient(listOf(Color(0xFFE4F7FF), Color(0xFFF6FCFF), Color(0xFFEAF5FF)))

internal object WapiMobile {
    val screen = 16.dp
    val row = 14.dp
    val compactRadius = 12.dp
    val panelRadius = 22.dp
}

@Composable
fun WhappyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WhappyBlue,
            secondary = WhappyDeepBlue,
            tertiary = WhappySky,
            onPrimary = Color.White,
            background = WhappyBackground,
            onBackground = WhappyInk,
            surface = Color.White,
            surfaceVariant = WhappySurface,
            onSurface = WhappyInk,
            outline = WhappyLine,
            error = Color(0xFFF0445A),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
