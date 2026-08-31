package com.whappy.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Adaptateur Android du fichier design-system/wapi.tokens.json.
internal val WhappyBlue = Color(0xFF146FE8)
internal val WhappyDark = Color(0xFF0A1D32)
internal val WhappyInk = Color(0xFF14283D)
internal val WhappyMuted = Color(0xFF647588)
internal val WhappyBackground = Color(0xFFF7F9FC)
internal val WhappySurface = Color(0xFFEFF3F7)
internal val WhappyNavy = Color(0xFF062A4C)
internal val WhappyLine = Color(0xFFDDE4EC)
internal val WhappyDeepBlue = Color(0xFF084A9B)
internal val WhappySky = Color(0xFF19BFF2)
internal val WapiViolet = Color(0xFF00A7A0)
internal val WapiVerifiedGray = Color(0xFF858D96)
internal val WapiChatAccent = WhappyBlue
internal val WapiBubbleOutgoing = WhappyBlue
internal val WapiChatBackground = Color(0xFFF4F7FA)
internal val WapiToolbar = Color.White
internal val WapiActionPanel = Color(0xFF05243F)
internal val WapiCanvas = Color(0xFFF7F9FC)
internal val WapiElevated = Color(0xFFFFFFFF)
internal val WapiSoftBlue = Color(0xFFE2EFFF)
internal val WapiBluePressed = Color(0xFF0B56B8)
internal val WapiBlueMist = Color(0xFFF0F6FC)
internal val WapiUnreadSurface = Color(0xFFEAF4FF)
internal val WapiSuccess = Color(0xFF14875A)
internal val WapiDanger = Color(0xFFD14343)
internal val WapiSheet = Color(0xFFFCFDFE)
internal val WhappyAurora = Brush.linearGradient(listOf(WhappySky, WhappyBlue, WhappyDeepBlue))
internal val WhappyAuroraSoft = Brush.linearGradient(listOf(Color(0xFFE5F8FC), Color(0xFFEDF4FD), Color(0xFFF4F7FA)))

// Use Android's neutral system family for a more premium, information-dense
// product feel. It keeps rendering identical offline on every device and
// avoids the toy-like proportions of a rounded display face in dense screens.
private val WapiTextFontFamily = FontFamily(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL))
private val WapiDisplayFontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))

internal object WapiMobile {
    val screen = 18.dp
    val row = 12.dp
    val compactRadius = 8.dp
    val panelRadius = 16.dp
    val dockRadius = 18.dp
    val controlHeight = 50.dp
    val avatar = 52.dp
    val touchTarget = 48.dp
}

internal object WapiMotion {
    const val quick = 110
    const val standard = 190
    const val expressive = 280
}

/**
 * Interaction de base WAPI pour les cartes, lignes et avatars qui ne sont pas
 * des boutons Material. Elle garantit une cible accessible de 48 dp, un retour
 * visuel natif et une sémantique de bouton cohérente dans toute l'application.
 */
internal fun Modifier.wapiClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .985f else 1f,
        animationSpec = spring(dampingRatio = .72f, stiffness = 680f),
        label = "wapi-control-press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.minimumInteractiveComponentSize().clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            WhappySounds.uiTap(context)
            onClick()
        },
    )
}

@Composable
fun WhappyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WhappyBlue,
            secondary = WhappyDeepBlue,
            tertiary = WhappySky,
            onPrimary = Color.White,
            primaryContainer = WapiSoftBlue,
            onPrimaryContainer = WhappyDeepBlue,
            secondaryContainer = WhappySurface,
            onSecondaryContainer = WhappyDark,
            background = WhappyBackground,
            onBackground = WhappyInk,
            surface = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = WhappyBackground,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = WhappySurface,
            surfaceVariant = WhappySurface,
            onSurface = WhappyInk,
            outline = WhappyLine,
            outlineVariant = WhappyLine.copy(alpha = .72f),
            surfaceTint = Color.Transparent,
            error = Color(0xFFF0445A),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(22.dp),
        ),
        typography = Typography(
            // Inherit LocalContentColor from the component. Hard-coded text colors
            // leak through merged styles into filled buttons and selected controls.
            headlineLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.55).sp),
            headlineMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.35).sp),
            titleLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.10).sp),
            titleMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            bodyMedium = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
            bodySmall = TextStyle(fontFamily = WapiTextFontFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
            labelLarge = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontFamily = WapiDisplayFontFamily, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
