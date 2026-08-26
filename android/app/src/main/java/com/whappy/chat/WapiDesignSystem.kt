package com.whappy.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Adaptateur Android du fichier design-system/wapi.tokens.json.
internal val WhappyBlue = Color(0xFF148AF2)
internal val WhappyDark = Color(0xFF11243D)
internal val WhappyInk = Color(0xFF172B45)
internal val WhappyMuted = Color(0xFF64748B)
internal val WhappyBackground = Color(0xFFF1F5FA)
internal val WhappySurface = Color(0xFFEAF1F8)
internal val WhappyNavy = Color(0xFF123A63)
internal val WhappyLine = Color(0xFFD7E1EC)
internal val WhappyDeepBlue = Color(0xFF0B6FD8)
internal val WhappySky = Color(0xFF50C9EA)
internal val WapiViolet = Color(0xFF6675F5)
internal val WapiVerifiedGray = Color(0xFF858D96)
internal val WapiChatAccent = WhappyBlue
internal val WapiBubbleOutgoing = WhappyBlue
internal val WapiChatBackground = Color(0xFFF2F6FB)
internal val WapiToolbar = Color.White
internal val WapiActionPanel = Color(0xFF0A3557)
internal val WapiCanvas = Color(0xFFF1F5FA)
internal val WapiElevated = Color(0xFFFFFFFF)
internal val WapiSoftBlue = Color(0xFFE1F1FF)
internal val WapiBluePressed = Color(0xFF0B71CC)
internal val WapiBlueMist = Color(0xFFF0F8FF)
internal val WapiUnreadSurface = Color(0xFFEDF7FF)
internal val WapiSuccess = Color(0xFF14875A)
internal val WapiDanger = Color(0xFFD14343)
internal val WapiSheet = Color(0xFFFCFDFE)
internal val WhappyAurora = Brush.linearGradient(listOf(WhappySky, WhappyBlue, WapiViolet))
internal val WhappyAuroraSoft = Brush.linearGradient(listOf(Color(0xFFE8F8FF), Color(0xFFF5F8FF), Color(0xFFF0EEFF)))

// Use Android's neutral system family for a more premium, information-dense
// product feel. It keeps rendering identical offline on every device and
// avoids the toy-like proportions of a rounded display face in dense screens.
private val WapiTextFontFamily = FontFamily(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL))
private val WapiDisplayFontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))

internal object WapiMobile {
    val screen = 16.dp
    val row = 14.dp
    val compactRadius = 12.dp
    val panelRadius = 22.dp
    val dockRadius = 28.dp
    val controlHeight = 52.dp
    val avatar = 54.dp
    val touchTarget = 48.dp
}

internal object WapiMotion {
    const val quick = 140
    const val standard = 220
    const val expressive = 320
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
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.30).sp, color = WhappyDark),
            headlineMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.15).sp, color = WhappyDark),
            titleLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = WhappyDark),
            titleMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = WhappyDark),
            bodyLarge = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal, color = WhappyInk),
            bodyMedium = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, color = WhappyInk),
            labelLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
