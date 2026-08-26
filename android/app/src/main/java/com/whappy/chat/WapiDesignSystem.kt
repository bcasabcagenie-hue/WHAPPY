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
internal val WhappyBlue = Color(0xFF0094F0)
internal val WhappyDark = Color(0xFF101828)
internal val WhappyInk = Color(0xFF172033)
internal val WhappyMuted = Color(0xFF667085)
internal val WhappyBackground = Color(0xFFF7F9FC)
internal val WhappySurface = Color(0xFFEEF4F9)
internal val WhappyNavy = Color(0xFF07345F)
internal val WhappyLine = Color(0xFFDCE5EE)
internal val WhappyDeepBlue = Color(0xFF0066CF)
internal val WhappySky = Color(0xFF00A2E6)
internal val WapiVerifiedGray = Color(0xFF858D96)
internal val WapiChatAccent = WhappyBlue
internal val WapiBubbleOutgoing = WhappyBlue
internal val WapiChatBackground = Color(0xFFF5F8FB)
internal val WapiToolbar = Color.White
internal val WapiActionPanel = Color(0xFF0A3557)
internal val WapiCanvas = Color(0xFFF5F7FA)
internal val WapiElevated = Color(0xFFFFFFFF)
internal val WapiSoftBlue = Color(0xFFE9F5FD)
internal val WapiBluePressed = Color(0xFF007FD1)
internal val WapiBlueMist = Color(0xFFF1F9FF)
internal val WapiUnreadSurface = Color(0xFFF4FAFF)
internal val WapiSuccess = Color(0xFF14875A)
internal val WapiDanger = Color(0xFFD14343)
internal val WapiSheet = Color(0xFFFCFDFE)
internal val WhappyAurora = Brush.linearGradient(listOf(Color(0xFF00A7F4), WhappyBlue, WhappyDeepBlue))
internal val WhappyAuroraSoft = Brush.linearGradient(listOf(Color(0xFFE7F7FF), Color(0xFFF8FCFF), Color(0xFFEDF5FF)))

// Use Android's neutral system family for a more premium, information-dense
// product feel. It keeps rendering identical offline on every device and
// avoids the toy-like proportions of a rounded display face in dense screens.
private val WapiTextFontFamily = FontFamily(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL))
private val WapiDisplayFontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))

internal object WapiMobile {
    val screen = 16.dp
    val row = 14.dp
    val compactRadius = 13.dp
    val panelRadius = 20.dp
    val dockRadius = 22.dp
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
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(26.dp),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.25).sp, color = WhappyDark),
            headlineMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.15).sp, color = WhappyDark),
            titleLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, color = WhappyDark),
            titleMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = WhappyDark),
            bodyLarge = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal, color = WhappyInk),
            bodyMedium = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, color = WhappyInk),
            labelLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
