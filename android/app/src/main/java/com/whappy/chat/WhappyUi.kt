@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.whappy.chat

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.LruCache
import android.util.Patterns
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun chatWallpaperBrush(style: String): Brush = when (style) {
    "azure" -> Brush.linearGradient(listOf(Color(0xFFF3FAFF), Color(0xFFE8F5FD), Color(0xFFF8FCFF)))
    "night" -> Brush.linearGradient(listOf(Color(0xFFEFF3FA), Color(0xFFE4EAF4), Color(0xFFF6F8FC)))
    "warm" -> Brush.linearGradient(listOf(Color(0xFFFFFAF4), Color(0xFFFFF3E6), Color(0xFFFBFAF7)))
    "mint" -> Brush.linearGradient(listOf(Color(0xFFF2FCF8), Color(0xFFE5F7F0), Color(0xFFF9FDFC)))
    else -> Brush.linearGradient(listOf(Color(0xFFF7FAFC), Color(0xFFF1F7FB), Color(0xFFF8FAFC)))
}

private val chatWallpaperNames = linkedMapOf(
    "cloud" to "Nuage",
    "azure" to "Bleu WAPI",
    "night" to "Minéral",
    "warm" to "Sable",
    "mint" to "Menthe",
)
private data class VoiceNoteDraft(
    val file: File,
    val durationSeconds: Int,
)

private enum class WhappyLanguage(val code: String, val label: String) {
    AUTOMATIC("auto", "Automatique (région)"),
    FRENCH("fr", "Français"),
    ENGLISH("en", "English"),
    LINGALA("ln", "Lingála"),
}

private val LocalWhappyLanguage = staticCompositionLocalOf { WhappyLanguage.FRENCH }

private fun resolvedWhappyLanguage(preference: WhappyLanguage, context: Context): WhappyLanguage {
    if (preference != WhappyLanguage.AUTOMATIC) return preference
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION") context.resources.configuration.locale
    }
    return when (locale.language.lowercase(Locale.ROOT)) {
        "en" -> WhappyLanguage.ENGLISH
        "ln" -> WhappyLanguage.LINGALA
        else -> WhappyLanguage.FRENCH
    }
}

private fun whappyText(language: WhappyLanguage, french: String, english: String, lingala: String): String = when (language) {
    WhappyLanguage.AUTOMATIC -> french
    WhappyLanguage.FRENCH -> french
    WhappyLanguage.ENGLISH -> english
    WhappyLanguage.LINGALA -> lingala
}

@Composable
private fun t(french: String, english: String, lingala: String): String = whappyText(LocalWhappyLanguage.current, french, english, lingala)

private fun lingwapErrorText(language: WhappyLanguage, code: String): String = when (code) {
    "NOT_FOUND" -> when (language) {
        WhappyLanguage.ENGLISH -> "Lingwap is not enabled on this WAPI server yet."
        WhappyLanguage.LINGALA -> "Lingwap ezali naino kosala te na serveur oyo ya WAPI."
        else -> "Lingwap n’est pas encore activé sur ce serveur WAPI."
    }
    "FAILED_PRECONDITION" -> when (language) {
        WhappyLanguage.ENGLISH -> "Lingwap is configured, but its translation relay has not been provisioned yet."
        WhappyLanguage.LINGALA -> "Lingwap ebongisami, kasi relais ya bobongoli etyami naino te."
        else -> "Lingwap est configuré, mais son relais de traduction n’est pas encore provisionné."
    }
    "UNAUTHENTICATED", "PERMISSION_DENIED" -> when (language) {
        WhappyLanguage.ENGLISH -> "Lingwap is not authorized to translate this message."
        WhappyLanguage.LINGALA -> "Lingwap ezali na ndingisa te ya kobongola message oyo."
        else -> "Lingwap n’a pas l’autorisation de traduire ce message."
    }
    else -> when (language) {
        WhappyLanguage.ENGLISH -> "Lingwap is temporarily unavailable. Try again in a moment."
        WhappyLanguage.LINGALA -> "Lingwap ezali kosala te mwa ntango. Zongela komeka mwa moke."
        else -> "Lingwap est momentanément indisponible. Réessayez dans un instant."
    }
}

private data class WapiTranslationLanguage(
    val code: String,
    val label: String,
    val nativeLabel: String,
)

private val wapiTranslationLanguages = listOf(
    WapiTranslationLanguage("fr", "Français", "Français"),
    WapiTranslationLanguage("en", "Anglais", "English"),
    WapiTranslationLanguage("zh-CN", "Chinois simplifié", "中文"),
    WapiTranslationLanguage("ar", "Arabe", "العربية"),
    WapiTranslationLanguage("ru", "Russe", "Русский"),
    WapiTranslationLanguage("es", "Espagnol", "Español"),
    WapiTranslationLanguage("pt", "Portugais", "Português"),
    WapiTranslationLanguage("tr", "Turc", "Türkçe"),
    WapiTranslationLanguage("ja", "Japonais", "日本語"),
    WapiTranslationLanguage("it", "Italien", "Italiano"),
    WapiTranslationLanguage("nl", "Néerlandais", "Nederlands"),
    WapiTranslationLanguage("de", "Allemand", "Deutsch"),
    WapiTranslationLanguage("ko", "Coréen", "한국어"),
    WapiTranslationLanguage("hi", "Hindi", "हिन्दी"),
    WapiTranslationLanguage("sw", "Swahili", "Kiswahili"),
    WapiTranslationLanguage("ln", "Lingála", "Lingála"),
)

// The emulator bypasses phone authentication, but must never invent product
// data. It shows the same empty states as a newly connected account.
private val demoConversations = emptyList<WhappyConversation>()

private val whappyFounderDisplayName = WhappyIdentity.founderName
private val whappyFounderChannelName = WhappyIdentity.founderChannelName
private val whappyFounderChannelTagline = WhappyIdentity.founderChannelTagline

private val demoMessages = emptyList<WhappyMessage>()

private val demoListings = emptyList<WhappyListing>()

private val demoBusinessPages = emptyList<WhappyBusinessPage>()

private val demoCampaigns = emptyList<WhappyCampaign>()

private val demoLives = emptyList<WhappyLive>()

private val demoChannels = emptyList<WhappyChannel>()

private val demoChannelPosts = emptyList<WhappyChannelPost>()

private val demoDeals = emptyList<WhappyDeal>()

private val demoPaymentNotices = emptyList<WhappyPaymentNotice>()

/** Founder dashboard values always come from the signed-in account's data. */
private data class WapiAdminMetrics(
    val paidRevenue: Long = 0L,
    val subscribers: Int = 0,
    val radioEpisodes: Int = 0,
    val activeLives: Int = 0,
    val users: Int = 0,
    val activeInstallations: Int = 0,
    val businessPages: Int = 0,
    val stories: Int = 0,
    val channels: Int = 0,
    val storePlayConnected: Boolean = false,
    val storeIosConnected: Boolean = false,
    val serverReady: Boolean = false,
    val downloadsMeasured: Boolean = false,
)

private enum class MessageActionType { Link, Phone }

private data class MessageAction(
    val title: String,
    val target: String,
    val type: MessageActionType,
    val start: Int,
    val end: Int,
)

@Composable
fun WhappyRoot(
    state: WhappyUiState,
    preview: Boolean,
    phoneAuth: PhoneAuthController,
    onTab: (WhappyTab) -> Unit,
    onSelectAccountProfile: (String) -> Unit,
    onOpenConversation: (WhappyConversation) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String) -> Unit,
    onRetryMessages: () -> Unit,
    onSendMedia: (Uri, String, String, String, Int, Boolean) -> Unit,
    onMarkViewOnce: (String) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCloseChannel: () -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
    onCreateGroup: (String, List<WhappyMember>, Uri?, String) -> Unit,
    onUpdateGroup: (String, String, Uri?, String, Boolean) -> Unit,
    onSetGroupAdministrator: (String, String, Boolean) -> Unit,
    onManageGroupMembers: (String, List<String>, String) -> Unit,
    onUpdateGroupSettings: (String, String, Boolean, Boolean) -> Unit,
    onSubscribeChannel: (String, Boolean) -> Unit,
    onSetLiveSubscription: (String, Boolean) -> Unit,
    onPublishChannelPost: (String) -> Unit,
    onReactChannelPost: (String, String) -> Unit,
    onPinChannelPost: (String, Boolean) -> Unit,
    onDeleteChannelPost: (String) -> Unit,
    onSearchContact: (String) -> Unit,
    onAddSearchedContact: () -> Unit,
    onClearContactSearch: () -> Unit,
    onOpenContact: (WhappyContact) -> Unit,
    onSearchBusinesses: (String) -> Unit,
    onContactBusiness: (WhappyBusinessPage) -> Unit,
    onPublishListing: (String, String, String, String) -> Unit,
    onCreateBusinessPage: (String, String, String, String, String, String) -> Unit,
    onUpdateBusinessPage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onUpdateBusinessLogo: (WhappyBusinessPage, Uri, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onCreateLive: (String, String, String, Boolean, String, String) -> Unit,
    onPublishStatus: (String, String, Uri?, String) -> Unit,
    onMarkStoryViewed: (String) -> Unit,
    onLoadStoryViewers: (String) -> Unit,
    onSaveWepiSettings: (WapiWepiSettings) -> Unit,
    onPublishRadioEpisode: (String, String, Uri, Long) -> Unit,
    onCreateRadioLive: (String, String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onEndLive: (String) -> Unit,
    onUpdateLiveStatus: (String, String) -> Unit,
    onConsumeLiveLink: () -> Unit,
    onConsumeGroupCallLink: () -> Unit,
    onCreateDeal: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit,
    onUpdateDealStatus: (String, String) -> Unit,
    onMarkPaymentRead: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onSaveTwinConsent: (Boolean) -> Unit,
    onUploadTwinAsset: (Uri, String, String) -> Unit,
    onCreateTwinAutomation: (String, String, String, String, String) -> Unit,
    onToggleTwinAutomation: (String, Boolean) -> Unit,
    onDeleteTwinAutomation: (String) -> Unit,
    onCreateTwinRender: (String, String, String, List<String>) -> Unit,
    onDismissError: () -> Unit,
    onSignOut: () -> Unit,
    onUpdateProfilePhoto: (Uri, String) -> Unit,
    onProfileSaved: () -> Unit,
) {
    val user = state.user
    if (!preview && state.sessionRestoring) {
        SessionRestoringScreen()
        return
    }
    if (!preview && user == null) {
        PhoneAuthScreen(phoneAuth, onProfileSaved)
        return
    }
    if (!preview && state.accountDisplayName.isBlank()) {
        LaunchedEffect(user?.uid) { phoneAuth.requireProfile() }
        PhoneAuthScreen(phoneAuth, onProfileSaved)
        return
    }
    WhappyMain(
        state = if (preview) state.copy(user = null, loading = false, online = true) else state,
        preview = preview,
        onTab = onTab,
        onSelectAccountProfile = onSelectAccountProfile,
        onOpenConversation = onOpenConversation,
        onCloseConversation = onCloseConversation,
        onSendMessage = onSendMessage,
        onRetryMessages = onRetryMessages,
        onSendMedia = onSendMedia,
        onMarkViewOnce = onMarkViewOnce,
        onReactMessage = onReactMessage,
        onDeleteMessage = onDeleteMessage,
        onEditMessage = onEditMessage,
        onTyping = onTyping,
        onHandleWhappyLink = onHandleWhappyLink,
        onOpenChannel = onOpenChannel,
        onCloseChannel = onCloseChannel,
        onCreateChannel = onCreateChannel,
        onCreateGroup = onCreateGroup,
        onUpdateGroup = onUpdateGroup,
        onSetGroupAdministrator = onSetGroupAdministrator,
        onManageGroupMembers = onManageGroupMembers,
        onUpdateGroupSettings = onUpdateGroupSettings,
        onSubscribeChannel = onSubscribeChannel,
        onSetLiveSubscription = onSetLiveSubscription,
        onPublishChannelPost = onPublishChannelPost,
        onReactChannelPost = onReactChannelPost,
        onPinChannelPost = onPinChannelPost,
        onDeleteChannelPost = onDeleteChannelPost,
        onSearchContact = onSearchContact,
        onAddSearchedContact = onAddSearchedContact,
        onClearContactSearch = onClearContactSearch,
        onOpenContact = onOpenContact,
        onSearchBusinesses = onSearchBusinesses,
        onContactBusiness = onContactBusiness,
        onPublishListing = onPublishListing,
        onCreateBusinessPage = onCreateBusinessPage,
        onUpdateBusinessPage = onUpdateBusinessPage,
        onUpdateBusinessLogo = onUpdateBusinessLogo,
        onCreateCampaign = onCreateCampaign,
        onCreateLive = onCreateLive,
        onPublishStatus = onPublishStatus,
        onMarkStoryViewed = onMarkStoryViewed,
        onLoadStoryViewers = onLoadStoryViewers,
        onSaveWepiSettings = onSaveWepiSettings,
        onPublishRadioEpisode = onPublishRadioEpisode,
        onCreateRadioLive = onCreateRadioLive,
        onDeleteStatus = onDeleteStatus,
        onEndLive = onEndLive,
        onUpdateLiveStatus = onUpdateLiveStatus,
        onConsumeLiveLink = onConsumeLiveLink,
        onConsumeGroupCallLink = onConsumeGroupCallLink,
        onCreateDeal = onCreateDeal,
        onUpdateDealStatus = onUpdateDealStatus,
        onMarkPaymentRead = onMarkPaymentRead,
        onEnableNotifications = onEnableNotifications,
        onSaveTwinConsent = onSaveTwinConsent,
        onUploadTwinAsset = onUploadTwinAsset,
        onCreateTwinAutomation = onCreateTwinAutomation,
        onToggleTwinAutomation = onToggleTwinAutomation,
        onDeleteTwinAutomation = onDeleteTwinAutomation,
        onCreateTwinRender = onCreateTwinRender,
        onDismissError = onDismissError,
        onSignOut = onSignOut,
        onUpdateProfilePhoto = onUpdateProfilePhoto,
    )
}

@Composable
private fun SessionRestoringScreen() {
    Surface(Modifier.fillMaxSize(), color = WhappyBackground) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.wapi_identity), "Logo WAPI", Modifier.size(76.dp))
            Text("WAPI", Modifier.padding(top = 18.dp), color = WhappyBlue, fontWeight = FontWeight.Black, fontSize = 28.sp)
            CircularProgressIndicator(Modifier.padding(top = 28.dp).size(30.dp), color = WhappyBlue, strokeWidth = 3.dp)
            Text("Ouverture de votre compte…", Modifier.padding(top = 14.dp), color = WhappyMuted)
        }
    }
}

private data class AuthCountry(val name: String, val flag: String, val code: String)

private data class PhoneEditorParts(val countryCode: String, val nationalNumber: String)

private fun phoneEditorParts(rawValue: String, fallbackCountryCode: String): PhoneEditorParts {
    val raw = rawValue.trim()
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return PhoneEditorParts(fallbackCountryCode, "")
    val internationalDigits = when {
        raw.startsWith("+") -> digits
        raw.startsWith("00") -> digits.drop(2)
        else -> null
    }
    if (internationalDigits != null) {
        val country = authCountries
            .map { it.code }
            .distinct()
            .sortedByDescending { it.length }
            .firstOrNull { internationalDigits.startsWith(it.drop(1)) }
        if (country != null) {
            return PhoneEditorParts(country, internationalDigits.drop(country.length - 1).take(15))
        }
    }
    return PhoneEditorParts(fallbackCountryCode, digits.take(15))
}

private fun stablePhoneFieldValue(value: TextFieldValue): TextFieldValue {
    val cursorDigits = value.text.take(value.selection.end.coerceIn(0, value.text.length)).count(Char::isDigit)
    val digits = value.text.filter(Char::isDigit).take(15)
    return TextFieldValue(digits, TextRange(cursorDigits.coerceAtMost(digits.length)))
}

private object PhoneNumberSpacingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = text.text.chunked(2).joinToString(" ")
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                return (offset + (offset - 1) / 2).coerceAtMost(transformed.length)
            }

            override fun transformedToOriginal(offset: Int): Int = transformed
                .take(offset.coerceIn(0, transformed.length))
                .count(Char::isDigit)
                .coerceAtMost(text.length)
        }
        return TransformedText(AnnotatedString(transformed), mapping)
    }
}

private fun flagFor(isoCode: String): String {
    val normalized = isoCode.uppercase(Locale.US)
    if (normalized.length != 2) return "🏳️"
    val base = 0x1F1E6
    return normalized.fold(StringBuilder()) { acc, ch ->
        val v = ch.code - 'A'.code
        if (v !in 0..25) return "🏳️"
        acc.appendCodePoint(base + v)
        acc
    }.toString()
}

private val authCountries = listOf(
    AuthCountry("Afghanistan", flagFor("AF"), "+93"),
    AuthCountry("Albania", flagFor("AL"), "+355"),
    AuthCountry("Algeria", flagFor("DZ"), "+213"),
    AuthCountry("AmericanSamoa", flagFor("AS"), "+1684"),
    AuthCountry("Andorra", flagFor("AD"), "+376"),
    AuthCountry("Angola", flagFor("AO"), "+244"),
    AuthCountry("Anguilla", flagFor("AI"), "+1264"),
    AuthCountry("Antarctica", flagFor("AQ"), "+672"),
    AuthCountry("Antigua and Barbuda", flagFor("AG"), "+1268"),
    AuthCountry("Argentina", flagFor("AR"), "+54"),
    AuthCountry("Armenia", flagFor("AM"), "+374"),
    AuthCountry("Aruba", flagFor("AW"), "+297"),
    AuthCountry("Australia", flagFor("AU"), "+61"),
    AuthCountry("Austria", flagFor("AT"), "+43"),
    AuthCountry("Azerbaijan", flagFor("AZ"), "+994"),
    AuthCountry("Bahamas", flagFor("BS"), "+1242"),
    AuthCountry("Bahrain", flagFor("BH"), "+973"),
    AuthCountry("Bangladesh", flagFor("BD"), "+880"),
    AuthCountry("Barbados", flagFor("BB"), "+1246"),
    AuthCountry("Belarus", flagFor("BY"), "+375"),
    AuthCountry("Belgium", flagFor("BE"), "+32"),
    AuthCountry("Belize", flagFor("BZ"), "+501"),
    AuthCountry("Benin", flagFor("BJ"), "+229"),
    AuthCountry("Bermuda", flagFor("BM"), "+1441"),
    AuthCountry("Bhutan", flagFor("BT"), "+975"),
    AuthCountry("Bolivia, Plurinational State of Bolivia", flagFor("BO"), "+591"),
    AuthCountry("Bosnia and Herzegovina", flagFor("BA"), "+387"),
    AuthCountry("Botswana", flagFor("BW"), "+267"),
    AuthCountry("Bouvet Island", flagFor("BV"), "+55"),
    AuthCountry("Brazil", flagFor("BR"), "+55"),
    AuthCountry("British Indian Ocean Territory", flagFor("IO"), "+246"),
    AuthCountry("Brunei Darussalam", flagFor("BN"), "+673"),
    AuthCountry("Bulgaria", flagFor("BG"), "+359"),
    AuthCountry("Burkina Faso", flagFor("BF"), "+226"),
    AuthCountry("Burundi", flagFor("BI"), "+257"),
    AuthCountry("Cambodia", flagFor("KH"), "+855"),
    AuthCountry("Cameroon", flagFor("CM"), "+237"),
    AuthCountry("Canada", flagFor("CA"), "+1"),
    AuthCountry("Cape Verde", flagFor("CV"), "+238"),
    AuthCountry("Cayman Islands", flagFor("KY"), "+1345"),
    AuthCountry("Central African Republic", flagFor("CF"), "+236"),
    AuthCountry("Chad", flagFor("TD"), "+235"),
    AuthCountry("Chile", flagFor("CL"), "+56"),
    AuthCountry("China", flagFor("CN"), "+86"),
    AuthCountry("Christmas Island", flagFor("CX"), "+61"),
    AuthCountry("Cocos (Keeling) Islands", flagFor("CC"), "+61"),
    AuthCountry("Colombia", flagFor("CO"), "+57"),
    AuthCountry("Comoros", flagFor("KM"), "+269"),
    AuthCountry("Congo", flagFor("CG"), "+242"),
    AuthCountry("Congo, The Democratic Republic of the", flagFor("CD"), "+243"),
    AuthCountry("Cook Islands", flagFor("CK"), "+682"),
    AuthCountry("Costa Rica", flagFor("CR"), "+506"),
    AuthCountry("Ivory Coast", flagFor("CI"), "+225"),
    AuthCountry("Croatia", flagFor("HR"), "+385"),
    AuthCountry("Cuba", flagFor("CU"), "+53"),
    AuthCountry("Cyprus", flagFor("CY"), "+357"),
    AuthCountry("Czech Republic", flagFor("CZ"), "+420"),
    AuthCountry("Denmark", flagFor("DK"), "+45"),
    AuthCountry("Djibouti", flagFor("DJ"), "+253"),
    AuthCountry("Dominica", flagFor("DM"), "+1767"),
    AuthCountry("Dominican Republic", flagFor("DO"), "+1849"),
    AuthCountry("Ecuador", flagFor("EC"), "+593"),
    AuthCountry("Egypt", flagFor("EG"), "+20"),
    AuthCountry("El Salvador", flagFor("SV"), "+503"),
    AuthCountry("Equatorial Guinea", flagFor("GQ"), "+240"),
    AuthCountry("Eritrea", flagFor("ER"), "+291"),
    AuthCountry("Estonia", flagFor("EE"), "+372"),
    AuthCountry("Ethiopia", flagFor("ET"), "+251"),
    AuthCountry("Falkland Islands", flagFor("FK"), "+500"),
    AuthCountry("Faroe Islands", flagFor("FO"), "+298"),
    AuthCountry("Fiji", flagFor("FJ"), "+679"),
    AuthCountry("Finland", flagFor("FI"), "+358"),
    AuthCountry("France", flagFor("FR"), "+33"),
    AuthCountry("French Polynesia", flagFor("PF"), "+689"),
    AuthCountry("French Southern and Antarctic Lands", flagFor("TF"), "+262"),
    AuthCountry("Gabon", flagFor("GA"), "+241"),
    AuthCountry("Gambia", flagFor("GM"), "+220"),
    AuthCountry("Georgia", flagFor("GE"), "+995"),
    AuthCountry("Germany", flagFor("DE"), "+49"),
    AuthCountry("Ghana", flagFor("GH"), "+233"),
    AuthCountry("Gibraltar", flagFor("GI"), "+350"),
    AuthCountry("Greece", flagFor("EL"), "+30"),
    AuthCountry("Greenland", flagFor("GL"), "+299"),
    AuthCountry("Grenada", flagFor("GD"), "+1473"),
    AuthCountry("Guadeloupe", flagFor("GP"), "+590"),
    AuthCountry("Guam", flagFor("GU"), "+1671"),
    AuthCountry("Guatemala", flagFor("GT"), "+502"),
    AuthCountry("Guernsey", flagFor("GG"), "+44"),
    AuthCountry("Guinea", flagFor("GN"), "+224"),
    AuthCountry("Guinea-Bissau", flagFor("GW"), "+245"),
    AuthCountry("Guyana", flagFor("GY"), "+592"),
    AuthCountry("Haiti", flagFor("HT"), "+509"),
    AuthCountry("Heard Island and McDonald Islands", flagFor("HM"), "+672"),
    AuthCountry("Vatican City State (Holy See)", flagFor("VA"), "+379"),
    AuthCountry("Honduras", flagFor("HN"), "+504"),
    AuthCountry("Hong Kong", flagFor("HK"), "+852"),
    AuthCountry("Hungary", flagFor("HU"), "+36"),
    AuthCountry("Iceland", flagFor("IS"), "+354"),
    AuthCountry("India", flagFor("IN"), "+91"),
    AuthCountry("Indonesia", flagFor("ID"), "+62"),
    AuthCountry("Iran, Islamic Republic of", flagFor("IR"), "+98"),
    AuthCountry("Iraq", flagFor("IQ"), "+964"),
    AuthCountry("Ireland", flagFor("IE"), "+353"),
    AuthCountry("Isle of Man", flagFor("IM"), "+44"),
    AuthCountry("Israel", flagFor("IL"), "+972"),
    AuthCountry("Italy", flagFor("IT"), "+39"),
    AuthCountry("Jamaica", flagFor("JM"), "+1876"),
    AuthCountry("Japan", flagFor("JP"), "+81"),
    AuthCountry("Jersey", flagFor("JE"), "+44"),
    AuthCountry("Jordan", flagFor("JO"), "+962"),
    AuthCountry("Kazakhstan", flagFor("KZ"), "+7"),
    AuthCountry("Kenya", flagFor("KE"), "+254"),
    AuthCountry("Kiribati", flagFor("KI"), "+686"),
    AuthCountry("North Korea", flagFor("KP"), "+850"),
    AuthCountry("South Korea", flagFor("KR"), "+82"),
    AuthCountry("Kuwait", flagFor("KW"), "+965"),
    AuthCountry("Kyrgyzstan", flagFor("KG"), "+996"),
    AuthCountry("Laos", flagFor("LA"), "+856"),
    AuthCountry("Latvia", flagFor("LV"), "+371"),
    AuthCountry("Lebanon", flagFor("LB"), "+961"),
    AuthCountry("Lesotho", flagFor("LS"), "+266"),
    AuthCountry("Liberia", flagFor("LR"), "+231"),
    AuthCountry("Libyan Arab Jamahiriya", flagFor("LY"), "+218"),
    AuthCountry("Liechtenstein", flagFor("LI"), "+423"),
    AuthCountry("Lithuania", flagFor("LT"), "+370"),
    AuthCountry("Luxembourg", flagFor("LU"), "+352"),
    AuthCountry("Macau", flagFor("MO"), "+853"),
    AuthCountry("Macedonia, The Former Yugoslav Republic of", flagFor("MK"), "+389"),
    AuthCountry("Madagascar", flagFor("MG"), "+261"),
    AuthCountry("Malawi", flagFor("MW"), "+265"),
    AuthCountry("Malaysia", flagFor("MY"), "+60"),
    AuthCountry("Maldives", flagFor("MV"), "+960"),
    AuthCountry("Mali", flagFor("ML"), "+223"),
    AuthCountry("Malta", flagFor("MT"), "+356"),
    AuthCountry("Marshall Islands", flagFor("MH"), "+692"),
    AuthCountry("Martinique", flagFor("MQ"), "+596"),
    AuthCountry("Mauritania", flagFor("MR"), "+222"),
    AuthCountry("Mauritius", flagFor("MU"), "+230"),
    AuthCountry("Mayotte", flagFor("YT"), "+262"),
    AuthCountry("Mexico", flagFor("MX"), "+52"),
    AuthCountry("Micronesia, Federated States of", flagFor("FM"), "+691"),
    AuthCountry("Moldova, Republic of", flagFor("MD"), "+373"),
    AuthCountry("Monaco", flagFor("MC"), "+377"),
    AuthCountry("Mongolia", flagFor("MN"), "+976"),
    AuthCountry("Montenegro", flagFor("ME"), "+382"),
    AuthCountry("Montserrat", flagFor("MS"), "+1664"),
    AuthCountry("Morocco", flagFor("MA"), "+212"),
    AuthCountry("Mozambique", flagFor("MZ"), "+258"),
    AuthCountry("Myanmar", flagFor("MM"), "+95"),
    AuthCountry("Namibia", flagFor("NA"), "+264"),
    AuthCountry("Nauru", flagFor("NR"), "+674"),
    AuthCountry("Nepal", flagFor("NP"), "+977"),
    AuthCountry("Netherlands", flagFor("NL"), "+31"),
    AuthCountry("Netherlands Antilles", flagFor("AN"), "+599"),
    AuthCountry("New Caledonia", flagFor("NC"), "+687"),
    AuthCountry("New Zealand", flagFor("NZ"), "+64"),
    AuthCountry("Nicaragua", flagFor("NI"), "+505"),
    AuthCountry("Niger", flagFor("NE"), "+227"),
    AuthCountry("Nigeria", flagFor("NG"), "+234"),
    AuthCountry("Niue", flagFor("NU"), "+683"),
    AuthCountry("Norfolk Island", flagFor("NF"), "+672"),
    AuthCountry("Northern Mariana Islands", flagFor("MP"), "+1670"),
    AuthCountry("Norway", flagFor("NO"), "+47"),
    AuthCountry("Oman", flagFor("OM"), "+968"),
    AuthCountry("Pakistan", flagFor("PK"), "+92"),
    AuthCountry("Palau", flagFor("PW"), "+680"),
    AuthCountry("Palestinian Territory, Occupied", flagFor("PS"), "+970"),
    AuthCountry("Panama", flagFor("PA"), "+507"),
    AuthCountry("Papua New Guinea", flagFor("PG"), "+675"),
    AuthCountry("Paraguay", flagFor("PY"), "+595"),
    AuthCountry("Peru", flagFor("PE"), "+51"),
    AuthCountry("Philippines", flagFor("PH"), "+63"),
    AuthCountry("Pitcairn", flagFor("PN"), "+870"),
    AuthCountry("Poland", flagFor("PL"), "+48"),
    AuthCountry("Portugal", flagFor("PT"), "+351"),
    AuthCountry("Puerto Rico", flagFor("PR"), "+1939"),
    AuthCountry("Qatar", flagFor("QA"), "+974"),
    AuthCountry("Réunion", flagFor("RE"), "+262"),
    AuthCountry("Romania", flagFor("RO"), "+40"),
    AuthCountry("Russia", flagFor("RU"), "+7"),
    AuthCountry("Rwanda", flagFor("RW"), "+250"),
    AuthCountry("Saint Helena, Ascension and Tristan Da Cunha", flagFor("SH"), "+290"),
    AuthCountry("Saint Kitts and Nevis", flagFor("KN"), "+1869"),
    AuthCountry("Saint Lucia", flagFor("LC"), "+1758"),
    AuthCountry("Saint Pierre and Miquelon", flagFor("PM"), "+508"),
    AuthCountry("Saint Vincent and the Grenadines", flagFor("VC"), "+1784"),
    AuthCountry("Samoa", flagFor("WS"), "+685"),
    AuthCountry("San Marino", flagFor("SM"), "+378"),
    AuthCountry("Sao Tome and Principe", flagFor("ST"), "+239"),
    AuthCountry("Saudi Arabia", flagFor("SA"), "+966"),
    AuthCountry("Senegal", flagFor("SN"), "+221"),
    AuthCountry("Serbia", flagFor("RS"), "+381"),
    AuthCountry("Seychelles", flagFor("SC"), "+248"),
    AuthCountry("Sierra Leone", flagFor("SL"), "+232"),
    AuthCountry("Singapore", flagFor("SG"), "+65"),
    AuthCountry("Slovakia", flagFor("SK"), "+421"),
    AuthCountry("Slovenia", flagFor("SI"), "+386"),
    AuthCountry("Solomon Islands", flagFor("SB"), "+677"),
    AuthCountry("Somalia", flagFor("SO"), "+252"),
    AuthCountry("South Africa", flagFor("ZA"), "+27"),
    AuthCountry("South Georgia and the South Sandwich Islands", flagFor("GS"), "+500"),
    AuthCountry("Spain", flagFor("ES"), "+34"),
    AuthCountry("Sri Lanka", flagFor("LK"), "+94"),
    AuthCountry("Sudan", flagFor("SD"), "+249"),
    AuthCountry("Suriname", flagFor("SR"), "+597"),
    AuthCountry("Svalbard and Jan Mayen", flagFor("SJ"), "+47"),
    AuthCountry("Swaziland", flagFor("SZ"), "+268"),
    AuthCountry("Sweden", flagFor("SE"), "+46"),
    AuthCountry("Switzerland", flagFor("CH"), "+41"),
    AuthCountry("Syria", flagFor("SY"), "+963"),
    AuthCountry("Taiwan", flagFor("TW"), "+886"),
    AuthCountry("Tajikistan", flagFor("TJ"), "+992"),
    AuthCountry("Tanzania, United Republic of", flagFor("TZ"), "+255"),
    AuthCountry("Thailand", flagFor("TH"), "+66"),
    AuthCountry("Timor-Leste", flagFor("TL"), "+670"),
    AuthCountry("Togo", flagFor("TG"), "+228"),
    AuthCountry("Tokelau", flagFor("TK"), "+690"),
    AuthCountry("Tonga", flagFor("TO"), "+676"),
    AuthCountry("Trinidad and Tobago", flagFor("TT"), "+1868"),
    AuthCountry("Tunisia", flagFor("TN"), "+216"),
    AuthCountry("Turkey", flagFor("TR"), "+90"),
    AuthCountry("Turkmenistan", flagFor("TM"), "+993"),
    AuthCountry("Turks and Caicos Islands", flagFor("TC"), "+1649"),
    AuthCountry("Tuvalu", flagFor("TV"), "+688"),
    AuthCountry("Uganda", flagFor("UG"), "+256"),
    AuthCountry("Ukraine", flagFor("UA"), "+380"),
    AuthCountry("United Arab Emirates", flagFor("AE"), "+971"),
    AuthCountry("United Kingdom", flagFor("GB"), "+44"),
    AuthCountry("United States", flagFor("US"), "+1"),
    AuthCountry("United States Minor Outlying Islands", flagFor("UM"), "+1581"),
    AuthCountry("Uruguay", flagFor("UY"), "+598"),
    AuthCountry("Uzbekistan", flagFor("UZ"), "+998"),
    AuthCountry("Vanuatu", flagFor("VU"), "+678"),
    AuthCountry("Venezuela, Bolivarian Republic of", flagFor("VE"), "+58"),
    AuthCountry("Vietnam", flagFor("VN"), "+84"),
    AuthCountry("Virgin Islands, British", flagFor("VG"), "+1284"),
    AuthCountry("Virgin Islands, U.S.", flagFor("VI"), "+1340"),
    AuthCountry("Wallis and Futuna", flagFor("WF"), "+681"),
    AuthCountry("Western Sahara", flagFor("EH"), "+732"),
    AuthCountry("Yemen", flagFor("YE"), "+967"),
    AuthCountry("Zambia", flagFor("ZM"), "+260"),
    AuthCountry("Zimbabwe", flagFor("ZW"), "+263"),
)

@Composable
private fun PhoneAuthScreen(controller: PhoneAuthController, onProfileSaved: () -> Unit) {
    val context = LocalContext.current
    val state = controller.state
    var country by rememberSaveable { mutableStateOf(controller.savedCountryCode) }
    var phone by rememberSaveable { mutableStateOf(controller.savedPhoneNumber.removePrefix(controller.savedCountryCode)) }
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var countryMenu by remember { mutableStateOf(false) }
    val selectedCountry = authCountries.firstOrNull { it.code == country } ?: authCountries.first()

    LaunchedEffect(code) {
        if (state.stage == AuthStage.CODE && code.length == 6 && !state.busy) controller.verifyCode(code)
    }
    Surface(Modifier.fillMaxSize(), color = WhappyBackground) {
        BoxWithConstraints(Modifier.statusBarsPadding()) {
            val horizontal = if (maxWidth > 700.dp) 0.24f else 0.07f
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = maxWidth * horizontal, vertical = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                BrandHeader(subtitle = "Un numéro. Un compte. Votre univers.", avatar = false)
                Spacer(Modifier.height(34.dp))
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            when (state.stage) {
                                AuthStage.PHONE -> "Votre numéro WAPI"
                                AuthStage.CODE -> "Vérification rapide"
                                AuthStage.PROFILE -> "Finalisez votre profil"
                            },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = WhappyDark,
                        )
                        Text(
                            when (state.stage) {
                                AuthStage.PHONE -> "Une seule vérification. Ensuite, WAPI s’ouvre directement sur cet appareil."
                                AuthStage.CODE -> "Entrez les 6 chiffres envoyés au ${state.phoneNumber.ifBlank { "numéro indiqué" }}."
                                AuthStage.PROFILE -> "Nécessaire uniquement pour un nouveau compte."
                            },
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                            color = WhappyMuted,
                            lineHeight = 20.sp,
                        )
                        when (state.stage) {
                            AuthStage.PHONE -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box {
                                        OutlinedButton(onClick = { countryMenu = true }, modifier = Modifier.width(142.dp).height(56.dp), shape = RoundedCornerShape(12.dp)) {
                                            Text("${selectedCountry.flag} ${selectedCountry.code} ▾", maxLines = 1)
                                        }
                                        DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                                            authCountries.forEach { item ->
                                                DropdownMenuItem(
                                                    text = { Text("${item.flag} ${item.name}  ${item.code}") },
                                                    onClick = { country = item.code; countryMenu = false },
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(phone, { value -> if (value.length > phone.length) WhappySounds.typing(context); phone = value }, label = { Text("Téléphone") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { controller.sendCode(country, phone) }))
                                }
                                PrimaryAction("Continuer", state.busy) { controller.sendCode(country, phone) }
                                Text("Compte existant : vos conversations et votre profil seront restaurés automatiquement.", Modifier.padding(top = 12.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            AuthStage.CODE -> {
                                OutlinedTextField(code, { value -> if (value.length > code.length) WhappySounds.typing(context); code = value.filter(Char::isDigit).take(6) }, label = { Text("Code à 6 chiffres") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { controller.verifyCode(code) }))
                                PrimaryAction("Vérifier et entrer", state.busy) { controller.verifyCode(code) }
                                TextButton(onClick = controller::resendCode, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Renvoyer le SMS") }
                                TextButton(onClick = controller::back, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Modifier le numéro") }
                            }
                            AuthStage.PROFILE -> {
                                OutlinedTextField(name, { value -> if (value.length > name.length) WhappySounds.typing(context); name = value.take(60) }, label = { Text("Votre nom") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { controller.saveProfile(name, onProfileSaved) }))
                                PrimaryAction("Entrer dans WAPI", state.busy) { controller.saveProfile(name, onProfileSaved) }
                            }
                        }
                        if (state.status.isNotBlank()) Text(state.status, Modifier.padding(top = 12.dp), color = WhappyBlue, fontSize = 13.sp)
                        if (state.error.isNotBlank()) Text(state.error, Modifier.padding(top = 12.dp), color = WhappyBlue, fontSize = 13.sp)
                        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, null, tint = WhappyMuted, modifier = Modifier.size(16.dp))
                            Text("Session protégée · aucun mot de passe", Modifier.padding(start = 7.dp), color = WhappyMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WhappyMain(
    state: WhappyUiState,
    preview: Boolean,
    onTab: (WhappyTab) -> Unit,
    onSelectAccountProfile: (String) -> Unit,
    onOpenConversation: (WhappyConversation) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String) -> Unit,
    onRetryMessages: () -> Unit,
    onSendMedia: (Uri, String, String, String, Int, Boolean) -> Unit,
    onMarkViewOnce: (String) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCloseChannel: () -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
    onCreateGroup: (String, List<WhappyMember>, Uri?, String) -> Unit,
    onUpdateGroup: (String, String, Uri?, String, Boolean) -> Unit,
    onSetGroupAdministrator: (String, String, Boolean) -> Unit,
    onManageGroupMembers: (String, List<String>, String) -> Unit,
    onUpdateGroupSettings: (String, String, Boolean, Boolean) -> Unit,
    onSubscribeChannel: (String, Boolean) -> Unit,
    onSetLiveSubscription: (String, Boolean) -> Unit,
    onPublishChannelPost: (String) -> Unit,
    onReactChannelPost: (String, String) -> Unit,
    onPinChannelPost: (String, Boolean) -> Unit,
    onDeleteChannelPost: (String) -> Unit,
    onSearchContact: (String) -> Unit,
    onAddSearchedContact: () -> Unit,
    onClearContactSearch: () -> Unit,
    onOpenContact: (WhappyContact) -> Unit,
    onSearchBusinesses: (String) -> Unit,
    onContactBusiness: (WhappyBusinessPage) -> Unit,
    onPublishListing: (String, String, String, String) -> Unit,
    onCreateBusinessPage: (String, String, String, String, String, String) -> Unit,
    onUpdateBusinessPage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onUpdateBusinessLogo: (WhappyBusinessPage, Uri, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onCreateLive: (String, String, String, Boolean, String, String) -> Unit,
    onPublishStatus: (String, String, Uri?, String) -> Unit,
    onMarkStoryViewed: (String) -> Unit,
    onLoadStoryViewers: (String) -> Unit,
    onSaveWepiSettings: (WapiWepiSettings) -> Unit,
    onPublishRadioEpisode: (String, String, Uri, Long) -> Unit,
    onCreateRadioLive: (String, String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onEndLive: (String) -> Unit,
    onUpdateLiveStatus: (String, String) -> Unit,
    onConsumeLiveLink: () -> Unit,
    onConsumeGroupCallLink: () -> Unit,
    onCreateDeal: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit,
    onUpdateDealStatus: (String, String) -> Unit,
    onMarkPaymentRead: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onSaveTwinConsent: (Boolean) -> Unit,
    onUploadTwinAsset: (Uri, String, String) -> Unit,
    onCreateTwinAutomation: (String, String, String, String, String) -> Unit,
    onToggleTwinAutomation: (String, Boolean) -> Unit,
    onDeleteTwinAutomation: (String) -> Unit,
    onCreateTwinRender: (String, String, String, List<String>) -> Unit,
    onDismissError: () -> Unit,
    onSignOut: () -> Unit,
    onUpdateProfilePhoto: (Uri, String) -> Unit,
) {
    var previewConversation by remember { mutableStateOf<WhappyConversation?>(null) }
    var previewChannel by remember { mutableStateOf<WhappyChannel?>(null) }
    var previewChannelPosts by remember { mutableStateOf(demoChannelPosts) }
    var previewMessages by remember { mutableStateOf(emptyList<WhappyMessage>()) }
    var requestedStoryAuthorId by remember { mutableStateOf<String?>(null) }
    var showTwinStudio by remember { mutableStateOf(false) }
    var showActivityCenter by remember { mutableStateOf(false) }
    var showAppHub by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var gameSessionActive by remember { mutableStateOf(false) }
    var locallyReadNotices by remember { mutableStateOf(emptySet<String>()) }
    val noticeHost = remember { SnackbarHostState() }
    val selected = if (preview) previewConversation else state.selectedConversation
    val selectedChannel = if (preview) previewChannel else state.selectedChannel
    val currentTab = state.tab
    fun openStoryFromConversation(authorId: String) {
        if (authorId.isBlank()) return
        requestedStoryAuthorId = authorId
        if (preview) previewConversation = null else onCloseConversation()
        onTab(WhappyTab.STORIES)
    }
    val activityNotices = if (preview) demoPaymentNotices else state.paymentNotices
    val activityLives = if (preview) demoLives else state.lives
    val unreadActivity = activityNotices.count { !it.read && it.id !in locallyReadNotices }
    val accountPhone = state.user?.phoneNumber.orEmpty()
    val accountUserId = state.user?.uid.orEmpty()
    val isFounderAccount = WhappyIdentity.isFounder(accountPhone)
    val personalDisplayName = if (preview && state.user == null) {
        WhappyIdentity.founderName
    } else {
        WhappyIdentity.resolveAccountName(state.accountDisplayName, accountPhone)
    }
    val activeBusinessPage = state.businessPages.firstOrNull { it.id == state.activeBusinessPageId }
    val isBusinessAccount = state.activeProfileType == "business" && activeBusinessPage != null
    val accountDisplayName = activeBusinessPage?.name?.takeIf { isBusinessAccount } ?: personalDisplayName
    val activePhotoUrl = activeBusinessPage?.logoUrl?.takeIf { isBusinessAccount }.orEmpty().ifBlank { state.accountPhotoUrl }
    val visibleConversations = if (isBusinessAccount) {
        state.conversations.filter { it.profileType == "business" && it.businessPageId == activeBusinessPage?.id }
    } else {
        state.conversations.filter { it.profileType != "business" }
    }
    val mainContext = LocalContext.current
    var founderMetrics by remember(isFounderAccount, preview) { mutableStateOf<WapiAdminMetrics?>(null) }
    LaunchedEffect(isFounderAccount, preview) {
        if (!isFounderAccount || preview) {
            founderMetrics = null
            return@LaunchedEffect
        }
        founderMetrics = runCatching {
            val payload = FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("getFounderDashboard")
                .call()
                .await()
                .data as? Map<*, *> ?: error("Réponse du tableau fondateur invalide")
            val stores = payload["storeIntegrations"] as? Map<*, *>
            WapiAdminMetrics(
                paidRevenue = (payload["paidRevenue"] as? Number)?.toLong() ?: 0L,
                radioEpisodes = (payload["radioEpisodes"] as? Number)?.toInt() ?: 0,
                activeLives = (payload["activeLives"] as? Number)?.toInt() ?: 0,
                users = (payload["users"] as? Number)?.toInt() ?: 0,
                activeInstallations = (payload["activeInstallations"] as? Number)?.toInt() ?: 0,
                businessPages = (payload["businessPages"] as? Number)?.toInt() ?: 0,
                stories = (payload["stories"] as? Number)?.toInt() ?: 0,
                channels = (payload["channels"] as? Number)?.toInt() ?: 0,
                storePlayConnected = stores?.get("playStore") == true,
                storeIosConnected = stores?.get("appStore") == true,
                serverReady = true,
                downloadsMeasured = (payload["activeInstallations"] as? Number)?.toInt()?.let { it >= 0 } == true,
            )
        }.getOrNull()
    }
    val localAdminMetrics = WapiAdminMetrics(
        paidRevenue = state.paymentNotices.filter { it.status == "paid" }.sumOf { it.amount },
        subscribers = state.channels.filter { it.ownerId == accountUserId }.sumOf { it.memberCount },
        radioEpisodes = state.radioEpisodes.count { it.ownerId == accountUserId },
        activeLives = state.lives.count { it.hostId == accountUserId && it.status == "live" },
    )
    val adminMetrics = founderMetrics ?: localAdminMetrics
    val languagePrefs = remember { WhappyFastStorage.preferences(mainContext, "whappy_language") }
    val radioController = remember { WapiRadioController() }
    var languagePreference by rememberSaveable {
        mutableStateOf(WhappyLanguage.entries.firstOrNull { it.code == languagePrefs.getString("code", "auto") } ?: WhappyLanguage.AUTOMATIC)
    }
    val appLanguage = resolvedWhappyLanguage(languagePreference, mainContext)
    DisposableEffect(radioController) {
        onDispose { radioController.release() }
    }
    BackHandler(enabled = selected != null || selectedChannel != null || showTwinStudio || showActivityCenter || showAppHub || showAccountSwitcher) {
        if (showActivityCenter) showActivityCenter = false
        else if (showAppHub) showAppHub = false
        else if (showAccountSwitcher) showAccountSwitcher = false
        else if (showTwinStudio) showTwinStudio = false
        else if (preview && selectedChannel != null) previewChannel = null
        else if (preview) previewConversation = null
        else if (selectedChannel != null) onCloseChannel()
        else onCloseConversation()
    }
    LaunchedEffect(preview, state.error) {
        val message = state.error?.trim().orEmpty()
        if (!preview && message.isNotBlank()) {
            noticeHost.currentSnackbarData?.dismiss()
            noticeHost.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
            onDismissError()
        }
    }
    if (showActivityCenter) ActivityCenterDialog(
        notices = activityNotices,
        lives = activityLives,
        locallyRead = locallyReadNotices,
        onRead = { id -> locallyReadNotices = locallyReadNotices + id; if (!preview) onMarkPaymentRead(id) },
        onOpenBusiness = { showActivityCenter = false; onTab(WhappyTab.BUSINESS) },
        onOpenLive = { showActivityCenter = false; onTab(WhappyTab.LIVE) },
        onDismiss = { showActivityCenter = false },
    )
    if (showAppHub) WhappyFeatureHubDialog(
        onOpen = { tab -> showAppHub = false; onTab(tab) },
        onOpenTwin = { showAppHub = false; showTwinStudio = true },
        onDismiss = { showAppHub = false },
    )
    if (showAccountSwitcher) AccountSwitcherDialog(
        personalName = personalDisplayName,
        personalPhotoUrl = state.accountPhotoUrl,
        phone = accountPhone,
        businessPages = state.businessPages,
        activeBusinessPageId = state.activeBusinessPageId,
        onSelect = { pageId -> showAccountSwitcher = false; onSelectAccountProfile(pageId) },
        onOpenBusiness = { showAccountSwitcher = false; onTab(WhappyTab.BUSINESS) },
        onOpenProfile = { showAccountSwitcher = false; onTab(WhappyTab.PROFILE) },
        onDismiss = { showAccountSwitcher = false },
    )
    CompositionLocalProvider(
        LocalWhappyLanguage provides appLanguage,
        LocalWapiRadio provides radioController,
    ) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WhappyBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(noticeHost) { data ->
                Surface(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color(0xFF10283A),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 10.dp,
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 7.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Text("!", color = Color.White, fontWeight = FontWeight.Black)
                        }
                        Text(data.visuals.message, Modifier.weight(1f).padding(horizontal = 11.dp), color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                        TextButton(onClick = data::dismiss) { Text("OK", color = Color.White, fontWeight = FontWeight.Black) }
                    }
                }
            }
        },
        bottomBar = {
            if (selected == null && selectedChannel == null && !showTwinStudio && !gameSessionActive) {
                WhappyBottomBar(currentTab, onTab) { showAppHub = true }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .then(if (gameSessionActive) Modifier else Modifier.statusBarsPadding()),
        ) {
            if (selectedChannel != null) {
                ChannelScreen(
                    channel = selectedChannel,
                    posts = if (preview) previewChannelPosts else state.channelPosts,
                    currentUserId = state.user?.uid ?: "demo-user",
                    loading = state.loading,
                    sending = state.sending,
                    onBack = { if (preview) previewChannel = null else onCloseChannel() },
                    onSubscribe = { subscribed -> if (preview) previewChannel = selectedChannel.copy(memberIds = if (subscribed) (selectedChannel.memberIds + "demo-user").distinct() else selectedChannel.memberIds - "demo-user", memberCount = (selectedChannel.memberCount + if (subscribed) 1 else -1).coerceAtLeast(1)) else onSubscribeChannel(selectedChannel.id, subscribed) },
                    onPublish = { value -> if (preview) previewChannelPosts = previewChannelPosts + WhappyChannelPost("local-${System.currentTimeMillis()}", value, "demo-user", accountDisplayName, System.currentTimeMillis()) else onPublishChannelPost(value) },
                    onReact = { post, emoji -> if (preview) previewChannelPosts = previewChannelPosts.map { if (it.id == post.id) it.copy(reactions = it.reactions + ("demo-user" to emoji)) else it } else onReactChannelPost(post.id, emoji) },
                    onPin = { post -> if (preview) previewChannelPosts = previewChannelPosts.map { if (it.id == post.id) it.copy(pinned = !it.pinned) else it } else onPinChannelPost(post.id, !post.pinned) },
                    onDelete = { post -> if (preview) previewChannelPosts = previewChannelPosts.map { if (it.id == post.id) it.copy(text = "Publication supprimée", deleted = true, pinned = false) else it } else onDeleteChannelPost(post.id) },
                )
            } else if (selected != null) {
                ChatScreen(
                    conversation = selected,
                    messages = if (preview) demoMessages + previewMessages else state.messages,
                    currentUserId = state.user?.uid ?: "demo-user",
                    loading = state.loading,
                    sending = state.sending,
                    groupBusy = state.actionBusy,
                    groupUpdate = state.groupUpdate,
                    onBack = { if (preview) previewConversation = null else onCloseConversation() },
                    storyStatuses = state.statuses,
                    onOpenStory = ::openStoryFromConversation,
                    onSend = { value, reply ->
                        if (preview) previewMessages = previewMessages + WhappyMessage("local-${System.currentTimeMillis()}", value, "demo-user", System.currentTimeMillis(), replyToId = reply?.id.orEmpty(), replyText = reply?.text.orEmpty())
                        else onSendMessage(value, reply?.id.orEmpty(), reply?.text.orEmpty())
                    },
                    onSendMedia = { uri, kind, type, name, duration, viewOnce ->
                        if (preview) previewMessages = previewMessages + WhappyMessage("local-${System.currentTimeMillis()}", when (kind) { "audio" -> "Note vocale"; "video" -> "Vidéo"; else -> "Photo" }, "demo-user", System.currentTimeMillis(), kind, uri.toString(), name, duration, viewOnce = viewOnce)
                        else onSendMedia(uri, kind, type, name, duration, viewOnce)
                    },
                    onMarkViewOnce = { message -> if (!preview) onMarkViewOnce(message.id) },
                    onReact = { message, emoji ->
                        if (preview) previewMessages = previewMessages.map { if (it.id == message.id) it.copy(reactions = it.reactions + ("demo-user" to emoji)) else it }
                        else onReactMessage(message.id, emoji)
                    },
                    onDelete = { message ->
                        if (preview) previewMessages = previewMessages.map { if (it.id == message.id) it.copy(text = "Message supprimé", kind = "deleted", deleted = true) else it }
                        else onDeleteMessage(message.id)
                    },
                    onEdit = { message, value ->
                        if (preview) previewMessages = previewMessages.map { if (it.id == message.id) it.copy(text = value, edited = true) else it }
                        else onEditMessage(message.id, value)
                    },
                    onTyping = { if (!preview) onTyping(it) },
                    onRetryPending = { if (!preview) onRetryMessages() },
                    onUpdateGroup = { name, uri, contentType, remove -> if (!preview) onUpdateGroup(selected.id, name, uri, contentType, remove) },
                    onSetGroupAdministrator = { memberId, administrator -> if (!preview) onSetGroupAdministrator(selected.id, memberId, administrator) },
                    onManageGroupMembers = { memberIds, action -> if (!preview) onManageGroupMembers(selected.id, memberIds, action) },
                    onUpdateGroupSettings = { description, editInfo, adminsOnly -> if (!preview) onUpdateGroupSettings(selected.id, description, editInfo, adminsOnly) },
                    availableContacts = state.contacts,
                    onSetLiveSubscription = { targetUserId, subscribed -> if (!preview) onSetLiveSubscription(targetUserId, subscribed) },
                    publicRadioEpisodes = state.radioEpisodes,
                    requestedGroupCall = state.requestedGroupCall,
                    onConsumeGroupCallLink = onConsumeGroupCallLink,
                )
            } else if (showTwinStudio) {
                WhappyStudioScreen(
                    state = state,
                    preview = preview,
                    userName = accountDisplayName,
                    onBack = { showTwinStudio = false },
                    onSaveConsent = onSaveTwinConsent,
                    onUploadAsset = onUploadTwinAsset,
                    onCreateAutomation = onCreateTwinAutomation,
                    onToggleAutomation = onToggleTwinAutomation,
                    onDeleteAutomation = onDeleteTwinAutomation,
                    onCreateRender = onCreateTwinRender,
                )
            } else {
                if (!gameSessionActive) {
                    BrandHeader(
                        subtitle = if (preview) "Mode aperçu" else if (state.online) "Vos échanges, simplement" else "Connexion limitée",
                        avatar = true,
                        name = accountDisplayName,
                        photoUrl = activePhotoUrl,
                        unread = unreadActivity,
                        founder = isFounderAccount,
                        onActivity = { showActivityCenter = true },
                        onProfile = { showAccountSwitcher = true },
                    )
                }
                // Compose exactly one destination so avatars and messages never
                // shift while two media-heavy screens cross-fade.
                key(currentTab) {
            when (currentTab) {
                WhappyTab.MOMENTS -> MomentsScreen(
                    twinReadiness = state.twinProfile?.readiness ?: 0,
                    lives = if (preview) demoLives else state.lives,
                    radioEpisodes = state.radioEpisodes,
                    onTab = onTab,
                    onOpenWhappies = { showTwinStudio = true },
                )
                WhappyTab.STORIES -> StoriesScreen(
                    statuses = state.statuses,
                    currentUserId = state.user?.uid ?: "demo-user",
                    currentUserName = accountDisplayName,
                    currentUserPhotoUrl = activePhotoUrl,
                    busy = state.actionBusy,
                    preview = preview,
                    onPublish = onPublishStatus,
                    onDelete = onDeleteStatus,
                    onOpenSpace = onTab,
                    storyViewers = state.storyViewers,
                    storyViewersLoading = state.storyViewersLoading,
                    onMarkViewed = { if (!preview) onMarkStoryViewed(it) },
                    onLoadViewers = { if (!preview) onLoadStoryViewers(it) },
                    requestedStoryAuthorId = requestedStoryAuthorId,
                    onConsumeRequestedStory = { requestedStoryAuthorId = null },
                )
                WhappyTab.CONTACTS, WhappyTab.CHANNELS -> MessagesScreen(
                    conversations = if (preview) demoConversations else visibleConversations,
                    loading = state.loading,
                    preview = preview,
                    contactBusy = state.contactBusy,
                    contacts = if (preview) demoConversations.map { WhappyContact(it.peer) } else state.contacts,
                    contactSearchResult = state.contactSearchResult,
                    contactSearchPhone = state.contactSearchPhone,
                    contactSearchMessage = state.contactSearchMessage,
                    storyStatuses = state.statuses,
                    onOpenStory = ::openStoryFromConversation,
                    businessResults = if (preview) demoBusinessPages else state.businessSearchResults,
                    businessSearchBusy = state.businessSearchBusy,
                    channels = if (preview) demoChannels else state.channels,
                    currentUserId = state.user?.uid ?: "demo-user",
                    channelBusy = state.actionBusy,
                    initialQuery = state.discoveryQuery,
                    initialSection = if (currentTab == WhappyTab.CHANNELS) 2 else 1,
                    onSearchContact = onSearchContact,
                    onAddSearchedContact = onAddSearchedContact,
                    onClearContactSearch = onClearContactSearch,
                    onOpenContact = onOpenContact,
                    onSearchBusinesses = onSearchBusinesses,
                    onContactBusiness = onContactBusiness,
                    onOpen = { if (preview) previewConversation = it else onOpenConversation(it) },
                    onOpenChannel = { if (preview) { previewChannel = it; previewChannelPosts = demoChannelPosts } else onOpenChannel(it) },
                    onCreateChannel = onCreateChannel,
                    onCreateGroup = onCreateGroup,
                    onSubscribeChannel = onSubscribeChannel,
                    onHandleWhappyLink = onHandleWhappyLink,
                )
                WhappyTab.MESSAGES -> MessagesScreen(
                    conversations = if (preview) demoConversations else visibleConversations,
                    loading = state.loading,
                    preview = preview,
                    contactBusy = state.contactBusy,
                            contacts = if (preview) demoConversations.map { WhappyContact(it.peer) } else state.contacts,
                            contactSearchResult = state.contactSearchResult,
                            contactSearchPhone = state.contactSearchPhone,
                            contactSearchMessage = state.contactSearchMessage,
                            storyStatuses = state.statuses,
                            onOpenStory = ::openStoryFromConversation,
                            businessResults = if (preview) demoBusinessPages else state.businessSearchResults,
                            businessSearchBusy = state.businessSearchBusy,
                    channels = if (preview) demoChannels else state.channels,
                    currentUserId = state.user?.uid ?: "demo-user",
                    channelBusy = state.actionBusy,
                    initialQuery = state.discoveryQuery,
                    initialSection = 0,
                    onSearchContact = onSearchContact,
                    onAddSearchedContact = onAddSearchedContact,
                    onClearContactSearch = onClearContactSearch,
                    onOpenContact = onOpenContact,
                    onSearchBusinesses = onSearchBusinesses,
                            onContactBusiness = onContactBusiness,
                            onOpen = { if (preview) previewConversation = it else onOpenConversation(it) },
                            onOpenChannel = { if (preview) { previewChannel = it; previewChannelPosts = demoChannelPosts } else onOpenChannel(it) },
                            onCreateChannel = onCreateChannel,
                            onCreateGroup = onCreateGroup,
                            onSubscribeChannel = onSubscribeChannel,
                            onHandleWhappyLink = onHandleWhappyLink,
                        )
                        WhappyTab.WEPI -> WapiAssistantScreen(
                            userName = accountDisplayName,
                            userId = state.user?.uid.orEmpty(),
                            settings = state.wepiSettings,
                            busy = state.actionBusy,
                            onSaveSettings = onSaveWepiSettings,
                            onOpenMessages = { onTab(WhappyTab.MESSAGES) },
                            onOpenBusiness = { onTab(WhappyTab.BUSINESS) },
                        )
                        WhappyTab.CALLS -> CallsScreen(
                            conversations = if (preview) demoConversations else visibleConversations,
                            onOpenConversation = { if (preview) previewConversation = it else onOpenConversation(it) },
                        )
                        WhappyTab.MARKET -> MarketScreen(
                            listings = if (preview) demoListings else state.listings,
                            businessPages = if (preview) demoBusinessPages else state.businessPages,
                            preview = preview,
                            busy = state.actionBusy,
                            accountDisplayName = accountDisplayName,
                            accountPhotoUrl = activePhotoUrl,
                            onPublish = onPublishListing,
                            onContactBusiness = onContactBusiness,
                        )
                        WhappyTab.LIVE -> LiveScreen(
                            lives = if (preview) demoLives else state.lives,
                            currentUserId = state.user?.uid ?: "demo-user",
                            preview = preview,
                            busy = state.actionBusy,
                            accountDisplayName = accountDisplayName,
                            accountPhotoUrl = activePhotoUrl,
                            onCreateLive = onCreateLive,
                            onEndLive = onEndLive,
                            onUpdateLiveStatus = onUpdateLiveStatus,
                            requestedLiveId = state.requestedLiveId,
                            onConsumeLiveLink = onConsumeLiveLink,
                            onOpenTwin = { showTwinStudio = true },
                            onOpenMessages = { onTab(WhappyTab.MESSAGES) },
                        )
                        WhappyTab.RADIO, WhappyTab.PODCASTS -> RadioScreen(
                            accountDisplayName = accountDisplayName,
                            currentUserId = state.user?.uid.orEmpty(),
                            episodes = state.radioEpisodes,
                            busy = state.actionBusy,
                            initialSection = if (currentTab == WhappyTab.PODCASTS) 1 else 0,
                            onPublishEpisode = onPublishRadioEpisode,
                            onCreateRadioLive = onCreateRadioLive,
                        )
                        WhappyTab.GAMES -> GamesScreen(
                            currentUserId = state.user?.uid.orEmpty(),
                            accountName = accountDisplayName,
                            onBack = { onTab(WhappyTab.MOMENTS) },
                            onOpenLive = { onTab(WhappyTab.LIVE) },
                            onSessionChanged = { gameSessionActive = it },
                        )
                        WhappyTab.SERVICES -> ServicesScreen(
                            onOpenMarket = { onTab(WhappyTab.MARKET) },
                            onOpenBusiness = { onTab(WhappyTab.BUSINESS) },
                        )
                        WhappyTab.BUSINESS -> BusinessScreen(
                            pages = if (preview) demoBusinessPages else state.businessPages,
                            campaigns = if (preview) demoCampaigns else state.campaigns,
                            deals = if (preview) demoDeals else state.deals,
                            paymentNotices = if (preview) demoPaymentNotices else state.paymentNotices,
                            preview = preview,
                            busy = state.actionBusy,
                            onCreatePage = onCreateBusinessPage,
                            onUpdatePage = onUpdateBusinessPage,
                            onUpdateLogo = onUpdateBusinessLogo,
                            onCreateCampaign = onCreateCampaign,
                            onCreateDeal = onCreateDeal,
                            onUpdateDealStatus = onUpdateDealStatus,
                            onMarkPaymentRead = onMarkPaymentRead,
                            onEnableNotifications = onEnableNotifications,
                            onOpenTwin = { onTab(WhappyTab.WEPI) },
                            onOpenMessages = { onTab(WhappyTab.MESSAGES) },
                        )
                        WhappyTab.PROFILE -> ProfileScreen(
                            name = accountDisplayName,
                            founder = isFounderAccount,
                            verified = state.accountVerified,
                            phone = state.user?.phoneNumber.orEmpty(),
                            accountId = state.user?.uid.orEmpty(),
                            online = state.online,
                            photoUrl = activePhotoUrl,
                            businessPages = if (preview) demoBusinessPages else state.businessPages,
                            activeBusinessPageId = state.activeBusinessPageId,
                            twinReadiness = state.twinProfile?.readiness ?: 0,
                            adminMetrics = adminMetrics,
                            preview = preview,
                            busy = state.actionBusy,
                            onUpdatePhoto = onUpdateProfilePhoto,
                            onOpenWhappies = { showTwinStudio = true },
                            onSignOut = onSignOut,
                            onEnableNotifications = onEnableNotifications,
                            onOpenSpace = onTab,
                            language = languagePreference,
                            detectedLanguage = appLanguage,
                            onLanguageChange = { next -> languagePreference = next; languagePrefs.edit().putString("code", next.code).apply() },
                            onSwitchAccount = onSelectAccountProfile,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun BrandHeader(subtitle: String, avatar: Boolean, name: String = "", photoUrl: String = "", unread: Int = 0, founder: Boolean = false, onActivity: () -> Unit = {}, onProfile: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WapiCanvas,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .42f)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(WhappyAurora))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = WhappyBlue, shadowElevation = 3.dp) {
                    Image(
                        painterResource(R.drawable.wapi_identity),
                        "Logo WAPI",
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).padding(2.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WAPI", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 0.2.sp)
                        Box(Modifier.padding(start = 7.dp).clip(CircleShape).background(WapiSoftBlue).padding(horizontal = 6.dp, vertical = 3.dp)) { Text("PRIVÉ", color = WhappyBlue, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp) }
                        if (founder) {
                            Box(Modifier.padding(start = 5.dp).clip(CircleShape).background(WapiVerifiedGray.copy(alpha = .13f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                                Text("FONDATEUR", color = WapiVerifiedGray, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .4.sp)
                            }
                        }
                    }
                    Text(subtitle, color = WhappyMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (avatar) {
                    Surface(shape = CircleShape, color = WapiElevated, border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine)) {
                        IconButton(onClick = onActivity) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(Icons.Rounded.Notifications, "Centre d’activité", tint = WhappyDark)
                                if (unread > 0) {
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) {
                                        Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    UserAvatar(photoUrl, name, 38.dp, Modifier.clickable(onClick = onProfile), RoundedCornerShape(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityCenterDialog(
    notices: List<WhappyPaymentNotice>,
    lives: List<WhappyLive>,
    locallyRead: Set<String>,
    onRead: (String) -> Unit,
    onOpenBusiness: () -> Unit,
    onOpenLive: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f), color = Color.White, shape = RoundedCornerShape(28.dp), shadowElevation = 18.dp) {
            Column {
                Row(Modifier.fillMaxWidth().background(WhappyAurora).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .17f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Notifications", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("Activité importante, sans bruit", color = Color.White.copy(alpha = .76f), fontSize = 11.sp) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFFF5FAFD)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TOUT", Modifier.clip(CircleShape).background(WhappyBlue).padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text("BUSINESS", Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("LIVE", Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 520.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val liveNow = lives.filter { it.status == "live" }.take(2)
                if (liveNow.isNotEmpty()) {
                    item { Text("EN DIRECT", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    items(liveNow, key = { "activity-live-${it.id}" }) { live ->
                        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenLive), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5FAFD)), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .10f))) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { UserAvatar(live.hostPhotoUrl, live.hostName, 40.dp, shape = CircleShape); Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(live.title, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1); Text("${live.hostName} · ${live.viewerCount} spectateurs", color = WhappyMuted, fontSize = 10.sp) }; Text("›", color = WhappyBlue, fontSize = 22.sp) } }
                    }
                }
                item { Text("PAIEMENTS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp)) }
                if (notices.isEmpty()) item { Text("Aucune nouvelle transaction.", color = WhappyMuted, fontSize = 12.sp) }
                items(notices.take(8), key = { "activity-payment-${it.id}" }) { notice ->
                    val unread = !notice.read && notice.id !in locallyRead
                    Card(Modifier.fillMaxWidth().clickable { if (unread) onRead(notice.id) else onOpenBusiness() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (unread) Color(0xFFF2F9FF) else Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, if (unread) WhappyBlue.copy(alpha = .18f) else WhappyLine)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (unread) WhappyBlue else WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Payments, null, tint = if (unread) Color.White else WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Paiement de ${notice.buyerName}", color = WhappyDark, fontWeight = FontWeight.Bold); if (unread) Box(Modifier.padding(start = 6.dp).size(7.dp).clip(CircleShape).background(WhappyBlue)) }; Text(notice.provider, color = WhappyMuted, fontSize = 10.sp) }; Text("+${formatMoney(notice.amount)}", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black) } }
                }
            }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Plus tard") }; Button(onClick = onOpenBusiness, shape = RoundedCornerShape(13.dp)) { Text("Ouvrir Business") } }
            }
        }
    }
}

@Composable
private fun WhappyBottomBar(selected: WhappyTab, onTab: (WhappyTab) -> Unit, onMore: () -> Unit) {
    val context = LocalContext.current
    val icons = mapOf(
        WhappyTab.MOMENTS to Icons.Rounded.Home,
        WhappyTab.STORIES to Icons.Rounded.AutoAwesome,
        WhappyTab.MESSAGES to Icons.Rounded.ChatBubble,
        WhappyTab.WEPI to Icons.Rounded.SmartToy,
        WhappyTab.CONTACTS to Icons.Rounded.Person,
        WhappyTab.CHANNELS to Icons.Rounded.Notifications,
        WhappyTab.CALLS to Icons.Rounded.Phone,
        WhappyTab.MARKET to Icons.Rounded.Storefront,
        WhappyTab.LIVE to Icons.Rounded.LiveTv,
        WhappyTab.RADIO to Icons.Rounded.Radio,
        WhappyTab.PODCASTS to Icons.Rounded.AudioFile,
        WhappyTab.GAMES to Icons.Rounded.Bolt,
        WhappyTab.SERVICES to Icons.Rounded.Payments,
        WhappyTab.BUSINESS to Icons.Rounded.BusinessCenter,
        WhappyTab.PROFILE to Icons.Rounded.Person,
    )
    val visibleTabs = listOf(WhappyTab.MESSAGES, WhappyTab.CALLS, WhappyTab.STORIES, WhappyTab.WEPI)
    Surface(
        modifier = Modifier.fillMaxWidth().background(WapiCanvas).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(WapiMobile.dockRadius),
        color = WapiElevated,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .75f)),
    ) {
    Column {
        Box(Modifier.align(Alignment.CenterHorizontally).width(34.dp).height(3.dp).clip(CircleShape).background(WhappyAurora))
        NavigationBar(modifier = Modifier.height(68.dp), containerColor = Color.Transparent, tonalElevation = 0.dp) {
        visibleTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { WhappySounds.haptic(context); onTab(tab) },
                icon = { Icon(icons.getValue(tab), mobileTabLabel(tab, LocalWhappyLanguage.current)) },
                label = { Text(mobileTabLabel(tab, LocalWhappyLanguage.current), fontSize = 10.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyBlue, selectedTextColor = WhappyDark, unselectedIconColor = WhappyMuted, unselectedTextColor = WhappyMuted, indicatorColor = WapiSoftBlue),
            )
        }
        NavigationBarItem(
            selected = selected !in visibleTabs,
            onClick = { WhappySounds.haptic(context); onMore() },
            icon = { Icon(Icons.Rounded.GridView, whappyText(LocalWhappyLanguage.current, "Tout", "All", "Nyonso")) },
            label = { Text(whappyText(LocalWhappyLanguage.current, "Tout", "All", "Nyonso"), fontSize = 10.sp, maxLines = 1) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyBlue, selectedTextColor = WhappyDark, unselectedIconColor = WhappyMuted, unselectedTextColor = WhappyMuted, indicatorColor = WapiSoftBlue),
        )
        }
    }
}

}

private data class WhappyFeatureShortcut(val tab: WhappyTab?, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
private fun WhappyFeatureHubDialog(onOpen: (WhappyTab) -> Unit, onOpenTwin: () -> Unit, onDismiss: () -> Unit) {
    val shortcuts = listOf(
        WhappyFeatureShortcut(WhappyTab.MOMENTS, "Accueil", "Vue générale WAPI", Icons.Rounded.Home),
        WhappyFeatureShortcut(WhappyTab.CONTACTS, "Contacts", "Personnes et QR", Icons.Rounded.PersonAdd),
        WhappyFeatureShortcut(WhappyTab.CHANNELS, "Chaînes", "Médias et créateurs", Icons.Rounded.Notifications),
        WhappyFeatureShortcut(WhappyTab.CALLS, "Appels", "Audio et vidéo", Icons.Rounded.Phone),
        WhappyFeatureShortcut(WhappyTab.MARKET, "Marché", "Acheter et vendre", Icons.Rounded.Storefront),
        WhappyFeatureShortcut(WhappyTab.RADIO, "Radio", "Créer une émission", Icons.Rounded.Radio),
        WhappyFeatureShortcut(WhappyTab.PODCASTS, "Podcasts", "Écouter et reprendre", Icons.Rounded.AudioFile),
        WhappyFeatureShortcut(WhappyTab.GAMES, "Jeux", "Défis et tournois", Icons.Rounded.Bolt),
        WhappyFeatureShortcut(WhappyTab.SERVICES, "Services", "Paiements et outils", Icons.Rounded.Payments),
        WhappyFeatureShortcut(WhappyTab.BUSINESS, "Business", "Pages, Deals et Ads", Icons.Rounded.BusinessCenter),
        WhappyFeatureShortcut(null, "Jumeau numérique", "Identité, voix et studio", Icons.Rounded.SmartToy),
        WhappyFeatureShortcut(WhappyTab.PROFILE, "Profil", "Compte et sécurité", Icons.Rounded.Person),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WapiSheet, dragHandle = { Box(Modifier.padding(top = 12.dp).width(42.dp).height(4.dp).clip(CircleShape).background(WhappyMuted.copy(alpha = .28f))) }) {
            Column(Modifier.padding(horizontal = WapiMobile.screen, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GridView, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Tout WAPI", color = WhappyDark, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text("Vos espaces, pensés pour le mobile", color = WhappyMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = WhappyMuted) }
                }
                shortcuts.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        rowItems.forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f).height(100.dp).clickable { item.tab?.let(onOpen) ?: onOpenTwin() },
                                color = WapiElevated,
                                shape = RoundedCornerShape(WapiMobile.compactRadius),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .86f)),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Icon(item.icon, null, tint = WhappyBlue, modifier = Modifier.size(23.dp))
                                    Column {
                                        Text(item.title, color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                        Text(item.subtitle, color = WhappyMuted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Messages · Groupes · Chaînes · Live · Commerce · IA", Modifier.fillMaxWidth(), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
    }
}

private fun mobileTabLabel(tab: WhappyTab, language: WhappyLanguage): String = when (tab) {
    WhappyTab.MOMENTS -> whappyText(language, "Accueil", "Home", "Ndako")
    WhappyTab.STORIES -> whappyText(language, "Actus", "Updates", "Sango")
    WhappyTab.MESSAGES -> whappyText(language, "Messages", "Messages", "Nsango")
    WhappyTab.WEPI -> "Assistant"
    WhappyTab.CHANNELS -> whappyText(language, "Chaînes", "Channels", "Ba chaîne")
    WhappyTab.PODCASTS -> "Podcasts"
    WhappyTab.LIVE -> whappyText(language, "Live", "Live", "Na bomoi")
    WhappyTab.PROFILE -> whappyText(language, "Profil", "Profile", "Profil")
    else -> tab.label
}

@Composable
private fun StoriesScreen(
    statuses: List<WhappyStatus>,
    currentUserId: String,
    currentUserName: String,
    currentUserPhotoUrl: String,
    busy: Boolean,
    preview: Boolean,
    onPublish: (String, String, Uri?, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSpace: (WhappyTab) -> Unit,
    storyViewers: Map<String, List<WapiStoryViewer>>,
    storyViewersLoading: Set<String>,
    onMarkViewed: (String) -> Unit,
    onLoadViewers: (String) -> Unit,
    requestedStoryAuthorId: String? = null,
    onConsumeRequestedStory: () -> Unit = {},
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("") }
    var mediaName by remember { mutableStateOf("") }
    var previewStatuses by remember { mutableStateOf(emptyList<WhappyStatus>()) }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var selectedStoryAuthorId by remember { mutableStateOf<String?>(null) }
    var locallyOpenedStoryIds by remember { mutableStateOf(emptySet<String>()) }
    var openOwnStoryAfterCount by rememberSaveable { mutableIntStateOf(-1) }
    var storyRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var storyRecordingFile by remember { mutableStateOf<File?>(null) }
    var storyRecording by rememberSaveable { mutableStateOf(false) }
    var storyRecordingStartedAt by remember { mutableLongStateOf(0L) }
    var storyRecordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var pendingStoryRecordingKind by rememberSaveable { mutableStateOf("voice") }
    var pendingStoryCaptureUri by remember { mutableStateOf<Uri?>(null) }
    // The main + button follows the fast mobile Story flow: picking a photo
    // or video publishes it straight away.  The Studio remains available for
    // captions, text, audio and richer composition.
    var publishPickedMediaImmediately by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun selectStoryMedia(uri: Uri) {
        val selectedName = displayName(context, uri)
        val extension = selectedName.substringAfterLast('.', "").lowercase()
        val contentType = context.contentResolver.getType(uri).orEmpty().ifBlank {
            when (extension) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                else -> "image/jpeg"
            }
        }
        if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
            mediaUri = uri
            mediaType = contentType
            mediaName = selectedName
            WhappySounds.mediaAdded()
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectStoryMedia(uri)
            if (publishPickedMediaImmediately && mediaUri != null) {
                val selectedUri = mediaUri
                val selectedType = mediaType
                val selectedName = mediaName
                publishPickedMediaImmediately = false
                // The optimistic Story is rendered immediately; selecting the
                // author is enough to open it without waiting for the rail's
                // later derived `myStories` value.
                openOwnStoryAfterCount = -1
                selectedStoryAuthorId = currentUserId
                if (preview) {
                    previewStatuses = listOf(
                        WhappyStatus(
                            "local-${System.currentTimeMillis()}",
                            currentUserId,
                            currentUserName,
                            "",
                            "personal",
                            System.currentTimeMillis(),
                            selectedUri.toString(),
                            if (selectedType.startsWith("audio/")) "audio" else if (selectedType.startsWith("video/")) "video" else "image",
                            selectedName,
                            authorPhotoUrl = currentUserPhotoUrl,
                        ),
                    ) + previewStatuses
                } else {
                    onPublish("", "personal", selectedUri, selectedType)
                }
                mediaUri = null; mediaType = ""; mediaName = ""; showComposer = false
            } else {
                showComposer = true
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectStoryMedia(it)
            showComposer = true
        }
    }
    val storyCameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingStoryCaptureUri
        if (captured && uri != null) {
            selectStoryMedia(uri)
            showComposer = true
        }
        if (!captured) pendingStoryCaptureUri = null
    }

    fun openStoryGallery(publishImmediately: Boolean = true) {
        publishPickedMediaImmediately = publishImmediately
        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    fun launchStoryCamera() {
        val file = File(WapiMediaStore.cacheDirectory(context), "wapi-story-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingStoryCaptureUri = uri
        storyCameraCapture.launch(uri)
    }

    val storyCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchStoryCamera()
    }

    fun requestStoryCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchStoryCamera()
        else storyCameraPermission.launch(Manifest.permission.CAMERA)
    }

    fun startStoryRecording(kind: String) {
        runCatching { createVoiceRecorder(context) }
            .onSuccess { (recorder, file) ->
                storyRecorder = recorder
                storyRecordingFile = file
                pendingStoryRecordingKind = kind
                storyRecordingStartedAt = System.currentTimeMillis()
                storyRecordingSeconds = 0
                storyRecording = true
                WhappySounds.voiceRecordingStarted()
                WhappySounds.haptic(context)
            }
            .onFailure {
                storyRecording = false
                storyRecordingFile = null
            }
    }

    fun stopStoryRecording(keep: Boolean) {
        val recorder = storyRecorder
        val file = storyRecordingFile
        val duration = ((System.currentTimeMillis() - storyRecordingStartedAt) / 1_000L).toInt().coerceAtLeast(1)
        val stopped = runCatching { recorder?.stop() }.isSuccess
        recorder?.release()
        storyRecorder = null
        storyRecordingFile = null
        storyRecording = false
        storyRecordingSeconds = 0
        if (recorder != null) WhappySounds.voiceRecordingStopped()
        if (keep && stopped && file != null && file.exists() && file.length() > 0L) {
            mediaUri = Uri.fromFile(file)
            mediaType = "audio/mp4"
            mediaName = if (pendingStoryRecordingKind == "radio") "Chronique radio · ${formatVoiceDuration(duration)}" else "Note vocale · ${formatVoiceDuration(duration)}"
            WhappySounds.mediaAdded()
        } else {
            file?.delete()
            if (!keep) WhappySounds.voiceRecordingCancelled()
        }
    }

    val storyMicrophonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startStoryRecording(pendingStoryRecordingKind)
    }

    fun requestStoryRecording(kind: String) {
        pendingStoryRecordingKind = kind
        if (storyRecording) {
            stopStoryRecording(keep = true)
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startStoryRecording(kind)
        } else {
            storyMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(storyRecording, storyRecordingStartedAt) {
        while (storyRecording) {
            storyRecordingSeconds = ((System.currentTimeMillis() - storyRecordingStartedAt) / 1_000L).toInt().coerceAtLeast(0)
            delay(250)
        }
    }
    val recorderForDisposal by rememberUpdatedState(storyRecorder)
    val recordingFileForDisposal by rememberUpdatedState(storyRecordingFile)
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorderForDisposal?.stop() }
            runCatching { recorderForDisposal?.release() }
            recordingFileForDisposal?.delete()
        }
    }
    val visibleStatuses = if (preview) previewStatuses else statuses
    val storyGroups = visibleStatuses
        .filter { it.authorId.isNotBlank() }
        .groupBy { it.authorId }
        .mapValues { (_, stories) -> stories.sortedBy { it.createdAt } }
    val myStories = storyGroups[currentUserId].orEmpty()
    val contactStoryGroups = visibleStatuses
        .filterNot { it.authorId == currentUserId }
        .groupBy { it.authorId }
        .values
        .map { stories -> stories.sortedBy { it.createdAt } }
        .sortedByDescending { stories -> stories.lastOrNull()?.createdAt ?: 0L }

    LaunchedEffect(requestedStoryAuthorId, storyGroups) {
        val authorId = requestedStoryAuthorId ?: return@LaunchedEffect
        if (storyGroups[authorId].orEmpty().isNotEmpty()) {
            selectedStoryAuthorId = authorId
            onConsumeRequestedStory()
        }
    }

    LaunchedEffect(myStories.size, openOwnStoryAfterCount) {
        if (openOwnStoryAfterCount >= 0 && myStories.size > openOwnStoryAfterCount) {
            selectedStoryAuthorId = currentUserId
            openOwnStoryAfterCount = -1
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(WapiCanvas),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Actus", style = MaterialTheme.typography.headlineLarge)
                    Text("Stories, directs et créations que vous suivez", color = WhappyMuted, fontSize = 12.sp)
                }
                Surface(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable {
                        mediaUri = null; mediaType = ""; mediaName = ""
                        showComposer = true
                    },
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("Aa", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 15.sp) }
                }
                FilledIconButton(
                    onClick = {
                        openStoryGallery()
                    },
                    modifier = Modifier.padding(start = 8.dp).size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
                ) { Icon(Icons.Rounded.Add, "Ouvrir la galerie pour ajouter une Story", tint = Color.White) }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen),
                color = Color.White,
                shape = RoundedCornerShape(WapiMobile.panelRadius),
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(vertical = 15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Stories", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        if (myStories.isNotEmpty()) {
                            TextButton(onClick = { onOpenSpace(WhappyTab.BUSINESS) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)) {
                                Icon(Icons.Rounded.Bolt, "Booster une Story", tint = WhappyBlue, modifier = Modifier.size(15.dp))
                                Text(" Booster", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Text("24 H", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StoryCircle(
                        name = "Ma Story",
                        subtitle = when { myStories.isEmpty() -> "Ajouter"; myStories.size == 1 -> formatStoryTime(myStories.last().createdAt); else -> "${myStories.size} Stories · ${formatStoryTime(myStories.last().createdAt)}" },
                        active = myStories.any { !it.viewedByCurrentUser && it.id !in locallyOpenedStoryIds },
                        story = myStories.lastOrNull(),
                        profilePhotoUrl = currentUserPhotoUrl,
                        publishing = myStories.any { it.mediaName == "Publication en cours" },
                        onClick = { if (myStories.isEmpty()) openStoryGallery() else { locallyOpenedStoryIds = locallyOpenedStoryIds + myStories.map { it.id }; selectedStoryAuthorId = currentUserId } },
                        add = myStories.isEmpty(),
                    )
                    contactStoryGroups.forEach { stories ->
                        val latest = stories.last()
                        StoryCircle(
                            name = latest.authorName,
                            subtitle = if (stories.size == 1) formatStoryTime(latest.createdAt) else "${stories.size} Stories · ${formatStoryTime(latest.createdAt)}",
                            active = stories.any { !it.viewedByCurrentUser && it.id !in locallyOpenedStoryIds },
                            story = latest,
                            profilePhotoUrl = latest.authorPhotoUrl,
                            onClick = { locallyOpenedStoryIds = locallyOpenedStoryIds + stories.map { it.id }; selectedStoryAuthorId = latest.authorId },
                        )
                    }
                    if (contactStoryGroups.isEmpty()) {
                        Text("Les Stories de vos contacts apparaîtront ici.", Modifier.width(190.dp).padding(top = 17.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                }
            }
        }
        item { ActusSectionTitle("À suivre", "Vos médias et créateurs") }
        item {
            ActusQuickRow(
                listOf(
                    ActusShortcut(Icons.Rounded.Notifications, "Chaînes", "Publications et créateurs", WhappyTab.CHANNELS, Color(0xFF6750A4)),
                    ActusShortcut(Icons.Rounded.Radio, "Radios", "Écouter ou prendre l’antenne", WhappyTab.RADIO, Color(0xFFE14B7B)),
                    ActusShortcut(Icons.Rounded.AudioFile, "Podcasts", "Émissions à reprendre partout", WhappyTab.PODCASTS, Color(0xFFF59E0B)),
                ),
                onOpenSpace,
            )
        }
        item { ActusSectionTitle("Explorer", "Ouvrez directement un espace WAPI") }
        item {
            ActusQuickRow(
                listOf(
                    ActusShortcut(Icons.Rounded.LiveTv, "En direct", "Voir et lancer un direct", WhappyTab.LIVE, Color(0xFFE33D4E)),
                    ActusShortcut(Icons.Rounded.Bolt, "Jeux", "Parties, IA et tournois", WhappyTab.GAMES, Color(0xFF283593)),
                    ActusShortcut(Icons.Rounded.Storefront, "Près de vous", "Marché et adresses locales", WhappyTab.MARKET, Color(0xFF148A67)),
                ),
                onOpenSpace,
            )
        }
    }

    if (showComposer) {
        ModalBottomSheet(onDismissRequest = { if (!busy) { if (storyRecording) stopStoryRecording(keep = false); showComposer = false } }, containerColor = Color(0xFFFCFDFE), dragHandle = { Box(Modifier.padding(top = 10.dp).width(38.dp).height(4.dp).clip(CircleShape).background(WhappyMuted.copy(alpha = .32f))) }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = WapiMobile.screen, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(currentUserPhotoUrl, currentUserName, 46.dp, shape = CircleShape)
                        Column(Modifier.weight(1f).padding(start = 11.dp)) { Text("Studio Story", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("Créez puis publiez pour 24 h", color = WhappyMuted, fontSize = 10.sp) }
                        IconButton(onClick = { if (storyRecording) stopStoryRecording(keep = false); showComposer = false }) { Icon(Icons.Rounded.Close, "Fermer") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Icons.Rounded.Photo to "Galerie",
                            Icons.Rounded.CameraAlt to "Caméra",
                            Icons.Rounded.Edit to "Texte",
                            Icons.Rounded.AudioFile to "Musique",
                            Icons.Rounded.Radio to "Podcast",
                            Icons.Rounded.Mic to "Radio",
                            Icons.Rounded.Mic to "Vocale",
                            Icons.Rounded.Movie to "Vidéo",
                        ).forEach { option ->
                            OutlinedButton(
                                onClick = {
                                    when (option.second) {
                                        "Galerie" -> openStoryGallery(publishImmediately = false)
                                        "Caméra" -> requestStoryCamera()
                                        "Texte" -> { mediaUri = null; mediaType = ""; mediaName = "" }
                                        "Musique", "Podcast" -> audioPicker.launch(arrayOf("audio/*"))
                                        "Radio" -> requestStoryRecording("radio")
                                        "Vocale" -> requestStoryRecording("voice")
                                        "Vidéo" -> {
                                            publishPickedMediaImmediately = false
                                            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (storyRecording && option.second in setOf("Radio", "Vocale")) Color(0xFFFFEBEE) else Color.Transparent,
                                    contentColor = if (storyRecording && option.second in setOf("Radio", "Vocale")) Color(0xFFD32F2F) else WhappyDark,
                                ),
                            ) { Icon(option.first, null, modifier = Modifier.size(17.dp)); Text("  ${option.second}", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (storyRecording) {
                        Surface(
                            Modifier.fillMaxWidth().clickable { stopStoryRecording(keep = true) },
                            color = Color(0xFFFFF2F3),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE33D4E).copy(alpha = .25f)),
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE33D4E)))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(if (pendingStoryRecordingKind == "radio") "Chronique radio en cours" else "Note vocale en cours", color = Color(0xFF9E2432), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    Text("${formatVoiceDuration(storyRecordingSeconds)} · touchez pour terminer", color = Color(0xFFB65A64), fontSize = 9.sp)
                                }
                                Icon(Icons.Rounded.Stop, "Terminer", tint = Color(0xFFE33D4E))
                            }
                        }
                    }
                    OutlinedTextField(draft, { draft = it.take(600) }, Modifier.fillMaxWidth(), placeholder = { Text("Ajoutez un texte, une légende ou un contexte…") }, minLines = 3, maxLines = 7, shape = RoundedCornerShape(17.dp))
                    Surface(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        publishPickedMediaImmediately = false
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }, shape = RoundedCornerShape(16.dp), color = Color(0xFFF0F8FD), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .18f))) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (mediaType.startsWith("audio/")) Icons.Rounded.AudioFile else if (mediaType.startsWith("video/")) Icons.Rounded.Movie else Icons.Rounded.Photo, null, tint = WhappyBlue)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(if (mediaUri == null) "Photo, vidéo ou audio" else mediaName, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1); Text(if (mediaUri == null) "Choisir depuis votre téléphone" else "Prêt à publier", color = WhappyMuted, fontSize = 9.sp) }
                            if (mediaUri != null) IconButton(onClick = { mediaUri = null; mediaType = ""; mediaName = "" }) { Icon(Icons.Rounded.Close, "Retirer") }
                        }
                    }
                    Button(
                        enabled = (draft.trim().isNotEmpty() || mediaUri != null) && !busy && !storyRecording,
                        onClick = {
                            val value = draft.trim()
                            openOwnStoryAfterCount = myStories.size
                            // Select the owner before the upload begins.  The
                            // optimistic Story added by the view model then
                            // opens as soon as it is rendered, rather than
                            // leaving the user on an apparently empty rail.
                            selectedStoryAuthorId = currentUserId
                            if (preview) previewStatuses = listOf(WhappyStatus("local-${System.currentTimeMillis()}", currentUserId, currentUserName, value, "personal", System.currentTimeMillis(), mediaUri?.toString().orEmpty(), if (mediaType.startsWith("audio/")) "audio" else if (mediaType.startsWith("video/")) "video" else if (mediaUri != null) "image" else "text", mediaName, authorPhotoUrl = currentUserPhotoUrl)) + previewStatuses
                            else onPublish(value, "personal", mediaUri, mediaType)
                            draft = ""; mediaUri = null; mediaType = ""; mediaName = ""; showComposer = false
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp) else Text("Publier ma Story", fontWeight = FontWeight.Black) }
                }
        }
    }

    selectedStoryAuthorId?.let { authorId ->
        val selectedStories = storyGroups[authorId].orEmpty()
        if (selectedStories.isNotEmpty()) {
            StoryViewerDialog(
                stories = selectedStories,
                own = authorId == currentUserId,
                fallbackAuthorPhotoUrl = if (authorId == currentUserId) currentUserPhotoUrl else selectedStories.lastOrNull()?.authorPhotoUrl.orEmpty(),
                onDelete = { story -> if (preview) previewStatuses = previewStatuses.filterNot { it.id == story.id } else onDelete(story.id); selectedStoryAuthorId = null },
                onDismiss = { selectedStoryAuthorId = null },
                viewers = storyViewers,
                viewersLoading = storyViewersLoading,
                onMarkViewed = { storyId -> locallyOpenedStoryIds = locallyOpenedStoryIds + storyId; onMarkViewed(storyId) },
                onLoadViewers = onLoadViewers,
            )
        }
    }
}

@Composable
private fun StoryCircle(
    name: String,
    subtitle: String,
    active: Boolean,
    story: WhappyStatus? = null,
    profilePhotoUrl: String = "",
    publishing: Boolean = false,
    add: Boolean = false,
    onClick: () -> Unit,
) {
    val storyAccent = Brush.sweepGradient(listOf(Color(0xFF075DE6), WhappySky, Color(0xFF0A3FCC), Color(0xFF075DE6)))
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(70.dp)
                .border(if (active) 3.dp else 1.dp, if (active) storyAccent else Brush.linearGradient(listOf(WhappyLine, WhappyLine)), CircleShape)
                .padding(if (active) 3.dp else 2.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.BottomEnd,
        ) {
            StoryCircleContent(story = story, profilePhotoUrl = profilePhotoUrl, name = name)
            if (story?.mediaKind == "video" || story?.mediaKind == "audio") {
                Box(
                    Modifier.align(Alignment.Center).size(27.dp).clip(CircleShape).background(Color(0xFF071527).copy(alpha = .72f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (story.mediaKind == "video") Icons.Rounded.PlayArrow else Icons.Rounded.AudioFile, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
            if (publishing) Box(Modifier.size(24.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(13.dp), color = Color.White, strokeWidth = 1.5.dp) }
            else if (add) Box(Modifier.size(22.dp).clip(CircleShape).background(WhappyBlue).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        }
        Text(name.substringBefore(" ").ifBlank { name }, Modifier.padding(top = 7.dp), color = WhappyDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (publishing) "Envoi…" else subtitle, color = if (publishing) WhappyBlue else WhappyMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StoryCircleContent(story: WhappyStatus?, profilePhotoUrl: String, name: String) {
    when {
        profilePhotoUrl.isNotBlank() -> UserAvatar(profilePhotoUrl, name, 62.dp, Modifier.clip(CircleShape), CircleShape)
        story?.mediaKind == "image" && story.mediaUrl.isNotBlank() ->
            UserAvatar(story.mediaUrl, "Contenu de la Story de $name", 62.dp, Modifier.clip(CircleShape), CircleShape)
        story != null && story.mediaKind.isBlank() && story.text.isNotBlank() ->
            Box(
                Modifier.size(62.dp).clip(CircleShape).background(Brush.linearGradient(storyGradient(story.tone))),
                contentAlignment = Alignment.Center,
            ) {
                Text(story.text, Modifier.padding(8.dp), color = Color.White, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        else -> UserAvatar("", name, 62.dp, Modifier.clip(CircleShape), CircleShape)
    }
}

private fun storyGradient(tone: String): List<Color> = when (tone.lowercase(Locale.ROOT)) {
    "sunset", "warm" -> listOf(Color(0xFFFF7A59), Color(0xFFCB3CF2))
    "night", "dark" -> listOf(Color(0xFF111827), Color(0xFF315AA9))
    "mint", "green" -> listOf(Color(0xFF0FBF9F), Color(0xFF0075D9))
    else -> listOf(WhappyBlue, Color(0xFF00B7F4))
}

@Composable
private fun ActusSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.Black); Text(subtitle, color = WhappyMuted, fontSize = 11.sp) }
    }
}

private data class ActusShortcut(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val destination: WhappyTab,
    val accent: Color,
)

@Composable
private fun ActusQuickRow(items: List<ActusShortcut>, onOpen: (WhappyTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen),
        color = Color.White,
        shape = RoundedCornerShape(WapiMobile.panelRadius),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(vertical = 5.dp)) {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen(item.destination) }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(item.accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = item.accent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(item.subtitle, Modifier.padding(top = 2.dp), color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(30.dp).clip(CircleShape).background(item.accent.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                        Text("›", color = item.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryViewerDialog(
    stories: List<WhappyStatus>,
    own: Boolean,
    fallbackAuthorPhotoUrl: String,
    onDelete: (WhappyStatus) -> Unit,
    onDismiss: () -> Unit,
    viewers: Map<String, List<WapiStoryViewer>>,
    viewersLoading: Set<String>,
    onMarkViewed: (String) -> Unit,
    onLoadViewers: (String) -> Unit,
) {
    if (stories.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    var activeIndex by remember(stories.map { it.id }) { mutableIntStateOf(0) }
    var dismissDragPixels by remember(stories.map { it.id }) { mutableFloatStateOf(0f) }
    var showViewersFor by remember(stories.map { it.id }) { mutableStateOf<String?>(null) }
    val story = stories[activeIndex.coerceIn(0, stories.lastIndex)]
    val progress = remember { Animatable(0f) }

    fun previous() {
        if (activeIndex > 0) activeIndex -= 1
    }

    fun next() {
        if (activeIndex < stories.lastIndex) activeIndex += 1 else onDismiss()
    }

    val uploading = own && story.mediaName == "Publication en cours"
    LaunchedEffect(story.id, own, uploading) {
        if (!uploading) onMarkViewed(story.id)
    }
    LaunchedEffect(story.id, uploading) {
        if (uploading) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = if (story.mediaKind in setOf("video", "audio")) 10_000 else 6_000),
        )
        next()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dismissOffset = with(density) { dismissDragPixels.toDp() }
        Column(
            Modifier
                .fillMaxSize()
                .offset(y = dismissOffset)
                .background(Color(0xFF070B10))
                .pointerInput(story.id) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, amount ->
                            if (amount > 0f || dismissDragPixels > 0f) dismissDragPixels = (dismissDragPixels + amount).coerceIn(0f, 420f * density.density)
                        },
                        onDragEnd = {
                            if (dismissDragPixels >= 132f * density.density) onDismiss() else dismissDragPixels = 0f
                        },
                        onDragCancel = { dismissDragPixels = 0f },
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.forEachIndexed { index, _ ->
                    val segment = when {
                        index < activeIndex -> 1f
                        index == activeIndex -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f))) {
                        if (segment > 0f) Box(Modifier.fillMaxWidth(segment).fillMaxHeight().background(Color.White))
                    }
                }
            }
            if (dismissDragPixels > 0f) {
                Text(
                    if (dismissDragPixels >= 132f * density.density) "Relâchez pour fermer" else "Glissez vers le bas pour fermer",
                    Modifier.fillMaxWidth().padding(top = 7.dp),
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(story.authorPhotoUrl.ifBlank { fallbackAuthorPhotoUrl }, story.authorName, 38.dp, Modifier.clip(CircleShape))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${formatStoryTime(story.createdAt)} · ${activeIndex + 1}/${stories.size}", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                }
                if (own) IconButton(onClick = { onDelete(story) }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = Color.White) }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (story.mediaUrl.isNotBlank()) StoryMediaPreview(story.mediaUrl, story.mediaKind, story.mediaName) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.mediaUrl))) } }
                    if (story.text.isNotBlank()) Text(story.text, Modifier.padding(22.dp), color = Color.White, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
                if (activeIndex > 0) FilledIconButton(
                    onClick = ::previous,
                    modifier = Modifier.align(Alignment.CenterStart),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .34f), contentColor = Color.White),
                ) { Text("‹", color = Color.White, fontSize = 30.sp) }
                FilledIconButton(
                    onClick = ::next,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .34f), contentColor = Color.White),
                ) { Text("›", color = Color.White, fontSize = 30.sp) }
            }
            if (own && !uploading) {
                TextButton(
                    onClick = {
                        showViewersFor = story.id
                        onLoadViewers(story.id)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Text("  ${story.viewCount} vue${if (story.viewCount > 1) "s" else ""} · Voir les spectateurs", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else Text(
                if (uploading) "Publication sécurisée en cours…" else "Story personnelle · visible 24 h",
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                color = Color.White.copy(alpha = .58f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
    showViewersFor?.let { storyId ->
        val currentViewers = viewers[storyId].orEmpty()
        AlertDialog(
            onDismissRequest = { showViewersFor = null },
            icon = { Icon(Icons.Rounded.Visibility, null, tint = WhappyBlue) },
            title = { Text("Vues de la Story", fontWeight = FontWeight.Black) },
            text = {
                when {
                    storyId in viewersLoading -> Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
                    currentViewers.isEmpty() -> Text("Personne n’a encore vu cette Story.", color = WhappyMuted)
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(currentViewers, key = { it.userId }) { viewer ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(viewer.photoUrl, viewer.displayName, 40.dp)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(viewer.displayName, color = WhappyDark, fontWeight = FontWeight.Bold)
                                    Text("Vu ${formatStoryTime(viewer.viewedAt)}", color = WhappyMuted, fontSize = 10.sp)
                                }
                                Icon(Icons.Rounded.DoneAll, null, tint = WhappyBlue, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showViewersFor = null }) { Text("Fermer") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
}

private data class WapiAssistantMessage(
    val text: String,
    val fromUser: Boolean,
    val failed: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
)

private fun wepiFailureMessage(error: Throwable): String {
    val remote = error as? FirebaseFunctionsException
    return when (remote?.code?.name) {
        "UNAUTHENTICATED", "PERMISSION_DENIED" -> "Votre session WAPI doit être actualisée avant de continuer avec WEPI."
        "RESOURCE_EXHAUSTED" -> "WEPI traite beaucoup de demandes. Patientez quelques secondes puis renvoyez votre message."
        "DEADLINE_EXCEEDED", "UNAVAILABLE" -> "La connexion avec WEPI a été interrompue. Votre message est conservé : renvoyez-le lorsque le réseau revient."
        "NOT_FOUND" -> "WEPI se met à jour. Réessayez dans quelques instants."
        else -> "WEPI n’a pas pu répondre pour le moment. Votre conversation reste conservée dans WAPI."
    }
}

@Composable
private fun WapiAssistantScreen(
    userName: String,
    userId: String,
    settings: WapiWepiSettings?,
    busy: Boolean,
    onSaveSettings: (WapiWepiSettings) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenBusiness: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var waitingForReply by rememberSaveable { mutableStateOf(false) }
    val activeSettings = settings ?: WapiWepiSettings(ownerId = userId)
    var messages by remember(userId) {
        val stored = WapiAssistantMemory.read(context, userId).map { value ->
            WapiAssistantMessage(value.text, value.fromUser, value.failed, value.id)
        }
        mutableStateOf(stored.ifEmpty {
            listOf(WapiAssistantMessage(
                "Bonjour ${userName.substringBefore(' ').ifBlank { "Cyril" }}. Je suis ${activeSettings.assistantName.ifBlank { "l’Assistant WAPI" }}. Vos questions restent dans WAPI et aucune action n’est exécutée sans votre confirmation.",
                false,
                id = "welcome-$userId",
            ))
        })
    }
    var memoryLoaded by remember(userId) { mutableStateOf(false) }
    var memoryUnavailable by remember(userId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
    fun persist(values: List<WapiAssistantMessage>) {
        WapiAssistantMemory.write(context, userId, values.map { value ->
            WapiStoredAssistantMessage(value.id, value.text, value.fromUser, value.failed)
        })
    }
    fun submit(value: String) {
        val clean = value.trim()
        if (clean.isBlank() || waitingForReply || !activeSettings.enabled) return
        val previousMessages = messages.map { it.fromUser to it.text }
        val messageId = "android-${UUID.randomUUID()}"
        val withPrompt = messages + WapiAssistantMessage(clean, true, id = messageId)
        messages = withPrompt
        persist(withPrompt)
        prompt = ""
        waitingForReply = true
        scope.launch {
            val response = WapiAssistantGateway.ask(clean, activeSettings, previousMessages, messageId)
            val answer = response.getOrElse(::wepiFailureMessage)
            val updated = messages + WapiAssistantMessage(answer, false, response.isFailure, "assistant-$messageId")
            messages = updated
            persist(updated)
            waitingForReply = false
        }
    }
    LaunchedEffect(userId) {
        if (userId.isBlank() || memoryLoaded) return@LaunchedEffect
        WapiAssistantGateway.loadHistory().fold(
            onSuccess = { history ->
                if (history.isNotEmpty()) {
                    val cloud = history.map { WapiAssistantMessage(it.text, it.fromUser, id = it.id) }
                    val cloudIds = cloud.mapTo(mutableSetOf()) { it.id }
                    val localOnly = messages.filter { it.id !in cloudIds && !it.id.startsWith("welcome-") }
                    val merged = (cloud + localOnly).takeLast(100)
                    messages = merged
                    persist(merged)
                }
                memoryLoaded = true
            },
            onFailure = {
                memoryLoaded = true
                memoryUnavailable = true
            },
        )
    }
    LaunchedEffect(messages.size, waitingForReply, imeBottom) {
        if (messages.isNotEmpty()) {
            // Wait for the keyboard/input layout pass so the latest message stays visible.
            delay(80)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    Column(Modifier.fillMaxSize().background(WapiChatBackground).imePadding()) {
        Surface(color = Color.White, shadowElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, "WEPI", tint = Color.White, modifier = Modifier.size(23.dp))
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(activeSettings.assistantName.ifBlank { "WEPI" }, color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (activeSettings.enabled && !memoryUnavailable) Color(0xFF1FA971) else Color(0xFFE09A24)))
                        Text(
                            if (!activeSettings.enabled) "Assistant en pause" else if (memoryUnavailable) "Mémoire locale active · cloud à resynchroniser" else "Disponible · mémoire privée synchronisée",
                            Modifier.padding(start = 6.dp),
                            color = WhappyMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
                IconButton(onClick = onOpenMessages) { Icon(Icons.Rounded.ChatBubble, "Ouvrir les messages", tint = WhappyMuted) }
                IconButton(onClick = onOpenBusiness) { Icon(Icons.Rounded.BusinessCenter, "Ouvrir Business", tint = WhappyMuted) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.MoreVert, "Régler WEPI", tint = WhappyDark) }
            }
        }
        if (!activeSettings.enabled) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFFFFF7E7)) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("WEPI est en pause.", Modifier.weight(1f), color = Color(0xFF8A5A00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showSettings = true }) { Text("Activer") }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.size <= 1 && !imeVisible) item(key = "wepi-shortcuts") {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Que voulez-vous faire ?", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Répondre à un client", "Préparer une vente", "Organiser un groupe", "Créer une publication").forEach { suggestion ->
                            OutlinedButton(
                                onClick = { submit(suggestion) },
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                            ) { Text(suggestion, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            itemsIndexed(messages, key = { index, _ -> "wepi-message-$index" }) { index, message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                    if (!message.fromUser) {
                        Box(Modifier.padding(end = 7.dp, bottom = 2.dp).size(29.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, "Réponse WEPI", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Column(Modifier.widthIn(max = 310.dp), horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
                        Surface(
                            color = if (message.fromUser) WapiBubbleOutgoing else if (message.failed) Color(0xFFFFF2F0) else Color.White,
                            shape = if (message.fromUser) RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp),
                            shadowElevation = if (message.fromUser) 0.dp else 1.dp,
                        ) {
                            Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = if (message.fromUser) Color.White else WhappyInk, lineHeight = 20.sp)
                        }
                        if (message.failed) {
                            val retryPrompt = messages.take(index).lastOrNull { it.fromUser }?.text.orEmpty()
                            TextButton(enabled = retryPrompt.isNotBlank() && !waitingForReply, onClick = { submit(retryPrompt) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp)) {
                                Text("Réessayer", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            if (waitingForReply) item(key = "wepi-typing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(29.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    Surface(Modifier.padding(start = 7.dp), color = Color.White, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp), shadowElevation = 1.dp) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(15.dp), color = WhappyBlue, strokeWidth = 2.dp)
                            Text("WEPI prépare sa réponse…", Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 10.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { value -> if (value.length > prompt.length) WhappySounds.typing(context); prompt = value.take(1200) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Écrivez à WEPI…") },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit(prompt) }),
                )
                FilledIconButton(
                    onClick = { submit(prompt) },
                    enabled = prompt.isNotBlank() && !waitingForReply && activeSettings.enabled,
                    modifier = Modifier.padding(start = 7.dp, bottom = 2.dp).size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer à WEPI", tint = Color.White) }
            }
        }
    }
    if (showSettings) WapiAssistantSettingsDialog(activeSettings, busy, onDismiss = { showSettings = false }, onSave = { onSaveSettings(it); showSettings = false })
}

@Composable
private fun WapiAssistantSettingsDialog(settings: WapiWepiSettings, busy: Boolean, onDismiss: () -> Unit, onSave: (WapiWepiSettings) -> Unit) {
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var autoReply by remember(settings) { mutableStateOf(settings.autoReply) }
    var assistantName by remember(settings) { mutableStateOf(settings.assistantName) }
    var businessName by remember(settings) { mutableStateOf(settings.businessName) }
    var tone by remember(settings) { mutableStateOf(settings.tone) }
    var welcome by remember(settings) { mutableStateOf(settings.welcomeMessage) }
    var instructions by remember(settings) { mutableStateOf(settings.instructions) }
    var salesAutomation by remember(settings) { mutableStateOf(settings.salesAutomation) }
    var captureOrderRequests by remember(settings) { mutableStateOf(settings.captureOrderRequests) }
    var humanHandoff by remember(settings) { mutableStateOf(settings.humanHandoff) }
    var deliveryPolicy by remember(settings) { mutableStateOf(settings.deliveryPolicy) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Text("Assistant WAPI", fontWeight = FontWeight.Black); Text("Configuration privée du compte", color = WhappyMuted, fontSize = 10.sp) } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Activer l’assistant", fontWeight = FontWeight.Bold); Text("Assistant disponible dans WAPI", color = WhappyMuted, fontSize = 10.sp) }; Switch(enabled, { enabled = it }) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Réponse automatique", fontWeight = FontWeight.Bold); Text("Toujours sous vos consignes", color = WhappyMuted, fontSize = 10.sp) }; Switch(autoReply, { autoReply = it }, enabled = enabled) } }
                item { Text("VENTE ASSISTÉE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Conseiller les produits", fontWeight = FontWeight.Bold); Text("WEPI utilise uniquement le catalogue actif", color = WhappyMuted, fontSize = 10.sp) }; Switch(salesAutomation, { salesAutomation = it }, enabled = enabled) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Préparer les demandes", fontWeight = FontWeight.Bold); Text("Produit, quantité et livraison, sans simuler un paiement", color = WhappyMuted, fontSize = 10.sp) }; Switch(captureOrderRequests, { captureOrderRequests = it }, enabled = salesAutomation) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Transfert humain", fontWeight = FontWeight.Bold); Text("Proposé pour validation, doute ou litige", color = WhappyMuted, fontSize = 10.sp) }; Switch(humanHandoff, { humanHandoff = it }, enabled = enabled) } }
                item { OutlinedTextField(assistantName, { assistantName = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Nom de l’assistant") }, singleLine = true) }
                item { OutlinedTextField(businessName, { businessName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Nom du business") }, singleLine = true) }
                item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("chaleureux", "expert", "direct").forEach { option -> OutlinedButton(onClick = { tone = option }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (tone == option) WhappyBlue else Color.Transparent, contentColor = if (tone == option) Color.White else WhappyBlue)) { Text(option.replaceFirstChar { it.uppercase() }) } } } }
                item { OutlinedTextField(welcome, { welcome = it.take(240) }, Modifier.fillMaxWidth(), label = { Text("Message d’accueil") }, minLines = 2) }
                item { OutlinedTextField(instructions, { instructions = it.take(600) }, Modifier.fillMaxWidth(), label = { Text("Consignes de l’IA") }, minLines = 3) }
                item { OutlinedTextField(deliveryPolicy, { deliveryPolicy = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("Livraison et retrait") }, minLines = 2) }
            }
        },
        confirmButton = { Button(enabled = !busy && assistantName.trim().length >= 2, onClick = { onSave(settings.copy(enabled = enabled, autoReply = autoReply, assistantName = assistantName, businessName = businessName, tone = tone, welcomeMessage = welcome, instructions = instructions, salesAutomation = salesAutomation, captureOrderRequests = captureOrderRequests, humanHandoff = humanHandoff, deliveryPolicy = deliveryPolicy)) }) { Text(if (busy) "Synchronisation…" else "Enregistrer") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annuler") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun MomentsScreen(
    twinReadiness: Int,
    lives: List<WhappyLive>,
    radioEpisodes: List<WapiRadioEpisode>,
    onTab: (WhappyTab) -> Unit,
    onOpenWhappies: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)), contentPadding = PaddingValues(WapiMobile.screen, 8.dp, WapiMobile.screen, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Accueil", color = WhappyDark, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Vos espaces WAPI", color = WhappyMuted, fontSize = 12.sp)
                }
                FilledIconButton(onClick = { onTab(WhappyTab.MESSAGES) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.Rounded.ChatBubble, "Messages", tint = Color.White) }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable { onTab(WhappyTab.MESSAGES) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = WhappyNavy),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Row(Modifier.padding(horizontal = 17.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ChatBubble, "Messages", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                        Text("Messages", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Retrouvez immédiatement toutes vos conversations", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                    }
                    Text("OUVRIR  ›", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(WapiMobile.panelRadius), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine)) {
                Row(Modifier.padding(WapiMobile.row), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Assistant WAPI", color = WhappyDark, fontWeight = FontWeight.Bold); Text("Préparez une action, sans automatisme caché", color = WhappyMuted, fontSize = 11.sp) }
                    TextButton(onClick = { onTab(WhappyTab.WEPI) }) { Text("Ouvrir") }
                }
            }
        }
        val activeLives = lives.filter { it.status == "live" }
        item {
            Card(Modifier.fillMaxWidth().clickable { onTab(WhappyTab.LIVE) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = WhappyBlue, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("WAPI Live", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(if (activeLives.isEmpty()) "Aucun direct pour l’instant · démarrez le vôtre" else "${activeLives.size} direct${if (activeLives.size > 1) "s" else ""} en cours · ${activeLives.sumOf { it.viewerCount }} spectateurs", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
                }
            }
        }
        if (activeLives.isNotEmpty()) {
            item { Text("En direct maintenant", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhappyDark, modifier = Modifier.padding(top = 4.dp)) }
            items(activeLives.take(3), key = { it.id }) { live ->
                Surface(Modifier.fillMaxWidth().clickable { onTab(WhappyTab.LIVE) }, color = WhappyNavy, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE23B4A)), contentAlignment = Alignment.Center) { Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(live.title.ifBlank { "Direct WAPI" }, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1); Text("${live.hostName} · ${live.viewerCount} spectateurs", color = Color.White.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1) }
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable { onTab(WhappyTab.BUSINESS) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = WhappyBlue), elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("WAPI ADS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black); Text("Créer une publicité régionale depuis Business", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("CRÉER ›", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWhappies), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Mon Jumeau numérique", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Créer votre Jumeau numérique", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
                }
            }
        }
        item {
            val newestEpisode = radioEpisodes.maxByOrNull { it.createdAt }
            Surface(Modifier.fillMaxWidth().clickable { onTab(WhappyTab.RADIO) }, color = Color(0xFFF0F8FF), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .14f))) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Radio & podcasts", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(newestEpisode?.let { "${it.stationName} · ${it.title}" } ?: "Vos émissions et podcasts dans WAPI", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1) }
                    Text("ÉCOUTER", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item { Text("Accès rapide", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhappyDark, modifier = Modifier.padding(top = 5.dp)) }
        item {
            BoxWithConstraints {
                val cell = (maxWidth - 12.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaceCard("Messages", "Temps réel", Icons.Rounded.ChatBubble, cell) { onTab(WhappyTab.MESSAGES) }
                        SpaceCard("Contacts", "Trouver et ajouter", Icons.Rounded.PersonAdd, cell) { onTab(WhappyTab.CONTACTS) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Appels", "Audio et vidéo", Icons.Rounded.Phone, cell) { onTab(WhappyTab.CALLS) }; SpaceCard("Marketplace", "Acheter et vendre", Icons.Rounded.Storefront, cell) { onTab(WhappyTab.MARKET) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Live", "Monter sur scène", Icons.Rounded.LiveTv, cell) { onTab(WhappyTab.LIVE) }; SpaceCard("Radio", "Créer une émission", Icons.Rounded.Radio, cell) { onTab(WhappyTab.RADIO) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Business", "Deals et paiements", Icons.Rounded.BusinessCenter, cell) { onTab(WhappyTab.BUSINESS) }; SpaceCard("Services", "Payer et demander", Icons.Rounded.Payments, cell) { onTab(WhappyTab.SERVICES) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Jeux", "Défis et duels", Icons.Rounded.Bolt, cell) { onTab(WhappyTab.GAMES) }; SpaceCard("Profil", "Compte et sécurité", Icons.Rounded.Person, cell) { onTab(WhappyTab.PROFILE) } }
                }
            }
        }
    }
}

@Composable
private fun StoryBubble(name: String, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(color).padding(3.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text(label, color = color, fontSize = if (label.length > 1) 14.sp else 20.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(name, color = WhappyDark, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RadioScreen(
    accountDisplayName: String,
    currentUserId: String,
    episodes: List<WapiRadioEpisode>,
    busy: Boolean,
    initialSection: Int = 0,
    onPublishEpisode: (String, String, Uri, Long) -> Unit,
    onCreateRadioLive: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_native_radio") }
    var stationName by rememberSaveable { mutableStateOf(prefs.getString("station_name", "Radio de $accountDisplayName").orEmpty()) }
    var topic by rememberSaveable { mutableStateOf(prefs.getString("station_topic", "Actualité, culture et communauté").orEmpty()) }
    var broadcasting by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var totalPausedMillis by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var lastRecording by remember { mutableStateOf<File?>(null) }
    var lastDuration by rememberSaveable { mutableLongStateOf(0L) }
    var micProfile by rememberSaveable { mutableStateOf(WapiMicProfile.entries.firstOrNull { it.name == prefs.getString("mic_profile", WapiMicProfile.BROADCAST.name) } ?: WapiMicProfile.BROADCAST) }
    var inputLevel by remember { mutableFloatStateOf(0f) }
    var monitoring by rememberSaveable { mutableStateOf(false) }
    val localPlayback = remember { WapiRadioPlayback() }
    val radio = LocalWapiRadio.current ?: error("Le lecteur radio WAPI doit être fourni par l’application.")
    val radioState = radio.state.value
    val effectSupport = remember { detectRadioEffectSupport() }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var showScheduleDialog by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableIntStateOf(initialSection.coerceIn(0, 1)) }
    var scheduleTitle by rememberSaveable { mutableStateOf("") }
    var scheduleTime by rememberSaveable { mutableStateOf("") }
    var programmes by remember {
        mutableStateOf(prefs.getStringSet("programmes", emptySet()).orEmpty().toList().sorted())
    }

    fun saveIdentity() {
        prefs.edit().putString("station_name", stationName.trim()).putString("station_topic", topic.trim()).apply()
        feedback = "Les informations de la radio sont enregistrées."
    }

    fun startBroadcast() {
        localPlayback.stop(); radio.stop(); monitoring = false
        runCatching { createRadioRecorder(context, micProfile) }
            .onSuccess { session ->
                recorder = session.recorder
                recordingFile = session.file
                startedAt = System.currentTimeMillis()
                pausedAt = 0L
                totalPausedMillis = 0L
                elapsedSeconds = 0L
                broadcasting = true
                paused = false
                feedback = "Chaîne ${micProfile.label} active · réduction de bruit et niveau automatique selon les capacités du téléphone."
            }
            .onFailure { feedback = "Le microphone n’a pas pu démarrer. Vérifiez son autorisation puis réessayez." }
    }

    fun finishBroadcast() {
        val active = recorder
        val file = recordingFile
        val stopped = runCatching { active?.stop() }.isSuccess
        active?.release()
        recorder = null
        recordingFile = null
        broadcasting = false
        paused = false
        if (stopped && file != null && file.exists() && file.length() > 0L) {
            lastRecording = file
            lastDuration = elapsedSeconds.coerceAtLeast(1L)
            feedback = "Émission terminée et enregistrée. Publiez-la comme podcast pour qu’elle apparaisse sur les autres appareils."
        } else {
            file?.delete()
            feedback = "L’émission est terminée, mais l’enregistrement était trop court pour être conservé."
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startBroadcast() else feedback = "Le microphone est nécessaire pour diffuser une émission."
    }

    LaunchedEffect(broadcasting, paused) {
        while (broadcasting) {
            if (!paused) elapsedSeconds = (System.currentTimeMillis() - startedAt - totalPausedMillis).coerceAtLeast(0L) / 1_000
            delay(1_000)
        }
    }

    LaunchedEffect(broadcasting, paused, recorder) {
        while (broadcasting && !paused) {
            val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            inputLevel = (amplitude / 24_000f).coerceIn(0f, 1f)
            delay(90)
        }
        if (!broadcasting || paused) inputLevel = 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release()
            localPlayback.release()
        }
    }

    Column(Modifier.fillMaxSize().background(WapiChatBackground)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Radio en direct", 1 to "Podcasts").forEach { destination ->
                Button(
                    onClick = { section = destination.first },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (section == destination.first) WhappyBlue else Color(0xFFF0F4F7),
                        contentColor = if (section == destination.first) Color.White else WhappyDark,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(destination.second, fontWeight = FontWeight.Black, fontSize = 11.sp) }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        if (section == 0) {
        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = WhappyNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("WAPI RADIO", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(if (broadcasting) if (paused) "Émission en pause" else "En direct maintenant" else "Studio prêt", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (broadcasting && !paused) WhappyBlue else Color.White).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(if (broadcasting && !paused) "LIVE" else "PRÊT", color = if (broadcasting && !paused) Color.White else WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    OutlinedTextField(stationName, { value -> if (value.length > stationName.length) WhappySounds.typing(context); stationName = value.take(60) }, Modifier.fillMaxWidth().padding(top = 20.dp), label = { Text("Nom de la radio") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = WhappySky, unfocusedBorderColor = Color.White.copy(alpha = .24f), focusedLabelColor = WhappySky, unfocusedLabelColor = Color.White.copy(alpha = .68f)))
                    OutlinedTextField(topic, { value -> if (value.length > topic.length) WhappySounds.typing(context); topic = value.take(100) }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Sujet de l’émission") }, minLines = 2, shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = WhappySky, unfocusedBorderColor = Color.White.copy(alpha = .24f), focusedLabelColor = WhappySky, unfocusedLabelColor = Color.White.copy(alpha = .68f)))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = ::saveIdentity, colors = ButtonDefaults.textButtonColors(contentColor = WhappySky)) { Text("Enregistrer") }
                        Button(
                            onClick = { saveIdentity(); onCreateRadioLive(stationName, topic) },
                            enabled = !busy && stationName.trim().length >= 2 && topic.trim().length >= 2,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue),
                            shape = RoundedCornerShape(14.dp),
                        ) { Icon(Icons.Rounded.LiveTv, null, modifier = Modifier.size(17.dp)); Text("  Diffuser en direct", fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RadioMetric("STATUT", if (broadcasting) if (paused) "Pause" else "Direct" else "Hors ligne", Modifier.weight(1f))
                RadioMetric("DURÉE", "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), Modifier.weight(1f))
                RadioMetric("QUALITÉ", "48 kHz", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF071D31)), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("MIC PROCESSOR", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp); Text(micProfile.detail, color = Color.White, fontWeight = FontWeight.Bold) }; Text("AAC 192K", color = Color.White.copy(alpha = .65f), fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { WapiMicProfile.entries.forEach { profile -> OutlinedButton(enabled = !broadcasting, onClick = { micProfile = profile; prefs.edit().putString("mic_profile", profile.name).apply(); WhappySounds.haptic(context) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (micProfile == profile) WhappyBlue else Color.White.copy(alpha = .05f), contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, if (micProfile == profile) WhappySky else Color.White.copy(alpha = .18f)), shape = RoundedCornerShape(14.dp)) { Text(profile.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
                    WapiAudioMeter(inputLevel = inputLevel, active = broadcasting && !paused)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(Triple("NR", "Bruit", effectSupport.noiseReduction), Triple("AGC", "Niveau", effectSupport.automaticGain), Triple("AEC", "Écho", effectSupport.echoCancellation)).forEach { effect -> Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .07f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (broadcasting && effect.third) Color(0xFF4ADE80) else WhappyMuted)); Column(Modifier.padding(start = 7.dp)) { Text(effect.first, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(if (effect.third) effect.second else "N/D", color = Color.White.copy(alpha = .55f), fontSize = 8.sp) } } } }
                    Text("Les traitements d’entrée dépendent du DSP audio disponible sur l’appareil. Le profil sélectionné est aussi appliqué à l’écoute de contrôle.", color = Color.White.copy(alpha = .52f), fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = WhappyBlue)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(if (broadcasting) "Votre studio est actif" else "Lancez votre émission", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(if (broadcasting) "Le son est capturé en qualité AAC et sera disponible à la fin." else "Le microphone démarre uniquement après votre autorisation.", Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!broadcasting) {
                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startBroadcast()
                                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue),
                                shape = RoundedCornerShape(17.dp),
                            ) { Icon(Icons.Rounded.Mic, null); Text("  Enregistrer l’émission", fontWeight = FontWeight.Black) }
                        } else {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        if (paused) runCatching { recorder?.resume() }.onSuccess { totalPausedMillis += (System.currentTimeMillis() - pausedAt).coerceAtLeast(0L); paused = false; feedback = "Le direct reprend." }
                                        else runCatching { recorder?.pause() }.onSuccess { pausedAt = System.currentTimeMillis(); paused = true; feedback = "Le direct est en pause." }
                                    } else {
                                        feedback = "La pause nécessite Android 7 ou plus. Vous pouvez terminer puis publier cet enregistrement."
                                    }
                                },
                                Modifier.weight(1f).height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue),
                                shape = RoundedCornerShape(17.dp),
                            ) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Mic, null); Text(if (paused) "  Reprendre" else "  Pause", fontWeight = FontWeight.Black) }
                            Button(
                                onClick = ::finishBroadcast,
                                Modifier.weight(1f).height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue),
                                shape = RoundedCornerShape(17.dp),
                            ) { Icon(Icons.Rounded.Stop, null); Text("  Terminer", fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }
        }
        feedback?.let { message ->
            item { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Text(message, Modifier.padding(16.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } }
        }
        lastRecording?.let { file ->
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AudioFile, null, tint = Color.White) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Dernière émission", color = WhappyDark, fontWeight = FontWeight.Black); Text("${file.length() / 1024} Ko · ${formatRadioDuration(lastDuration)} · ${micProfile.label}", color = WhappyMuted, fontSize = 11.sp) }
                        IconButton(onClick = { if (monitoring) { localPlayback.stop(); monitoring = false } else { monitoring = true; localPlayback.play(file, micProfile) { monitoring = false } } }) { Icon(if (monitoring) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (monitoring) "Arrêter l’écoute" else "Écouter", tint = WhappyBlue) }
                        IconButton(onClick = { shareRadioRecording(context, file, stationName) }) { Icon(Icons.Rounded.Share, "Partager", tint = WhappyBlue) }
                    }
                    Button(
                        enabled = !busy && currentUserId.isNotBlank() && topic.trim().length >= 2,
                        onClick = { onPublishEpisode(stationName.trim(), topic.trim(), Uri.fromFile(file), lastDuration.coerceAtLeast(1L)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 0.dp).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) { Text(if (busy) "Publication cloud…" else "Publier le podcast sur WAPI", fontWeight = FontWeight.Black) }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        }
        if (section == 1) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                    Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AudioFile, null, tint = Color.White, modifier = Modifier.size(29.dp)) }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("WAPI PODCASTS", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text("Écoutez sans quitter WAPI", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                            Text("Reprenez un épisode et passez au suivant depuis cette bibliothèque.", color = Color.White.copy(alpha = .68f), fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Radios & podcasts", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Émissions réellement publiées sur WAPI", color = WhappyMuted, fontSize = 11.sp) }
                Text("${episodes.size} EN LIGNE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
        if (episodes.isEmpty()) item { EmptyState("Aucun podcast publié", "Enregistrez une émission puis publiez-la pour créer la première chaîne WAPI.") }
        items(episodes, key = { it.id }) { episode ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = {
                            monitoring = false
                            radio.play(episode, micProfile)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
                    ) { Icon(if (radioState.episode?.id == episode.id && (radioState.playing || radioState.preparing)) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Écouter ${episode.title}", tint = Color.White) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(episode.title, color = WhappyDark, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${episode.stationName} · ${episode.authorName}", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${formatRadioDuration(episode.durationSeconds)} · publication cloud", color = WhappyMuted, fontSize = 9.sp)
                    }
                    if (episode.ownerId == currentUserId) Text("VOUS", color = WapiVerifiedGray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (section == 0) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Programmation", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Préparez les prochaines émissions", color = WhappyMuted, fontSize = 11.sp) }
                Button(onClick = { showScheduleDialog = true }, shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Add, null); Text("  Ajouter") }
            }
        }
        if (section == 0 && programmes.isEmpty()) {
            item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Schedule, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)); Text("Aucune émission programmée", Modifier.padding(top = 9.dp), color = WhappyDark, fontWeight = FontWeight.Bold); Text("Ajoutez un titre et une heure pour commencer.", color = WhappyMuted, fontSize = 11.sp) } } }
        } else if (section == 0) {
            items(programmes, key = { it }) { raw ->
                val parts = raw.split("|", limit = 3)
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Schedule, null, tint = Color.White) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(parts.getOrElse(1) { "Émission" }, color = WhappyDark, fontWeight = FontWeight.Black); Text(parts.getOrElse(2) { "Heure à définir" }, color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        IconButton(onClick = { programmes = programmes.filterNot { it == raw }; prefs.edit().putStringSet("programmes", programmes.toSet()).apply() }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = WhappyBlue) }
                    }
                }
            }
        }
        }
    }

    if (showScheduleDialog) AlertDialog(
        onDismissRequest = { showScheduleDialog = false },
        title = { Text("Programmer une émission", fontWeight = FontWeight.Black) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(scheduleTitle, { scheduleTitle = it.take(70) }, Modifier.fillMaxWidth(), label = { Text("Titre") }, singleLine = true); OutlinedTextField(scheduleTime, { scheduleTime = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Jour et heure") }, placeholder = { Text("Ex. samedi, 18:30") }, singleLine = true) } },
        confirmButton = { Button(enabled = scheduleTitle.trim().length >= 2 && scheduleTime.trim().length >= 2, onClick = { val entry = "${System.currentTimeMillis()}|${scheduleTitle.trim().replace("|", " ")}|${scheduleTime.trim().replace("|", " ")}"; programmes = (programmes + entry).sorted(); prefs.edit().putStringSet("programmes", programmes.toSet()).apply(); scheduleTitle = ""; scheduleTime = ""; showScheduleDialog = false; feedback = "L’émission a été ajoutée à votre programmation." }) { Text("Programmer") } },
        dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text("Annuler") } },
    )
}

@Composable
private fun RadioMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = WhappyMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(value, Modifier.padding(top = 4.dp), color = WhappyBlue, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

private fun formatRadioDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60L, seconds % 60L)

@Composable
private fun WapiAudioMeter(inputLevel: Float, active: Boolean) {
    val smoothed by animateFloatAsState(targetValue = if (active) inputLevel else 0f, animationSpec = tween(85), label = "radio-meter")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("NIVEAU MICRO", Modifier.weight(1f), color = Color.White.copy(alpha = .65f), fontSize = 9.sp, fontWeight = FontWeight.Black); Text(if (!active) "—∞ dB" else "${(-42 + smoothed * 42).toInt()} dB", color = if (smoothed > .86f) Color(0xFFFF806B) else WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(18) { index ->
                val threshold = (index + 1) / 18f
                val color = when { index >= 15 -> Color(0xFFFF655B); index >= 11 -> Color(0xFFFFC247); else -> Color(0xFF4ADE80) }
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (smoothed >= threshold) color else Color.White.copy(alpha = .08f)))
            }
        }
    }
}

private fun game3dScene(gameName: String): String = when (gameName) {
    "Billard WAPI" -> "pool"
    "Échecs" -> "chess"
    "Jeu de dames" -> "checkers"
    "Cartes WAPI", "Poker WAPI" -> if (gameName == "Poker WAPI") "poker" else "cards"
    "WAPI Sky" -> "sky"
    else -> "ludo"
}

private fun gameIcon(gameName: String): ImageVector = when (gameName) {
    "King QI" -> Icons.Rounded.SmartToy
    "Ludo WAPI" -> Icons.Rounded.GridView
    "WAPI Sky" -> Icons.Rounded.Bolt
    "Billard WAPI" -> Icons.Rounded.Radio
    "Échecs", "Jeu de dames" -> Icons.Rounded.GridView
    "Cartes WAPI", "Poker WAPI" -> Icons.Rounded.Favorite
    "Défi du jour" -> Icons.Rounded.AutoAwesome
    "Duel WAPI" -> Icons.Rounded.Groups
    else -> Icons.Rounded.Bolt
}

@Composable
private fun GamesScreen(
    currentUserId: String,
    accountName: String,
    onBack: () -> Unit,
    onOpenLive: () -> Unit,
    onSessionChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val gameScope = rememberCoroutineScope()
    val gamePrefs = remember { WhappyFastStorage.preferences(context, "wapi_play") }
    var selected by rememberSaveable { mutableStateOf("Ludo WAPI") }
    var gameOpen by rememberSaveable { mutableStateOf(false) }
    var gameFilter by rememberSaveable { mutableStateOf("Tous") }
    var xp by rememberSaveable { mutableIntStateOf(gamePrefs.getInt("xp", 0)) }
    var wins by rememberSaveable { mutableIntStateOf(gamePrefs.getInt("wins", 0)) }
    var dice by rememberSaveable { mutableIntStateOf(0) }
    var activePlayer by rememberSaveable { mutableIntStateOf(0) }
    // Four real pawns per player. Progress -1 = house, 0..51 = shared
    // circuit, 52..57 = coloured arrival lane.
    var ludoPositions by rememberSaveable { mutableStateOf(List(16) { -1 }) }
    var pendingLudoRoll by rememberSaveable { mutableIntStateOf(0) }
    var movableLudoPawns by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var ludoLog by rememberSaveable { mutableStateOf("Lancez le dé. Un 6 fait sortir votre pion de la maison.") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    var round by rememberSaveable { mutableIntStateOf(1) }
    var diceRolling by rememberSaveable { mutableStateOf(false) }
    var celebrating by rememberSaveable { mutableStateOf(false) }
    var showGameGuide by rememberSaveable { mutableStateOf(false) }
    val games = listOf(
        Triple("King QI", "Quiz vocal · contacts · trophées · direct", "♛"),
        Triple("Ludo WAPI", "Table 3D · dé, pions, captures et arrivée", "🎲"),
        Triple("WAPI Sky", "Course 3D · réflexes et progression", "🚀"),
        Triple("Billard WAPI", "Table 3D · visée, force et collisions", "🎱"),
        Triple("Échecs", "Table 3D · stratégie et IA", "♚"),
        Triple("Jeu de dames", "Table 3D · prises et couronnement", "⛀"),
        Triple("Cartes WAPI", "Bataille rapide · manches et score", "🂡"),
        Triple("Poker WAPI", "Texas Hold’em · IA locale · sans argent réel", "♠"),
        Triple("Défi du jour", "Quiz rapide · gagnez de l’XP", "⚡"),
        Triple("Duel WAPI", "Défi local entre joueurs", "♟"),
        Triple("Mots & idées", "Trouvez la solution ensemble", "✦"),
    )
    val gameCategories = listOf("Tous", "3D", "Stratégie", "Arcade", "Cartes")
    fun categoryForGame(name: String): String = when {
        name in listOf("Échecs", "Jeu de dames") -> "Stratégie"
        name in listOf("Cartes WAPI", "Poker WAPI") -> "Cartes"
        name in listOf("WAPI Sky", "Défi du jour", "Duel WAPI", "Mots & idées") -> "Arcade"
        else -> "3D"
    }
    val visibleGames = games.filter { gameFilter == "Tous" || categoryForGame(it.first) == gameFilter }
    val ludoPlayers = listOf(
        Triple("Vous", Color(0xFFE53935), "🔴"),
        Triple("Joueur 2", Color(0xFF1E88E5), "🔵"),
        Triple("Joueur 3", Color(0xFF43A047), "🟢"),
        Triple("Joueur 4", Color(0xFFF9A825), "🟡"),
    )

    LaunchedEffect(Unit) { WhappySounds.preloadGames(context) }
    LaunchedEffect(xp, wins) { gamePrefs.edit().putInt("xp", xp).putInt("wins", wins).apply() }
    LaunchedEffect(gameOpen) { onSessionChanged(gameOpen) }
    DisposableEffect(Unit) { onDispose { onSessionChanged(false) } }
    val gameActivity = remember(context) { context.findActivity() }
    DisposableEffect(gameOpen, gameActivity) {
        val activity = gameActivity
        val previousOrientation = activity?.requestedOrientation
        if (gameOpen && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (activity != null && gameOpen) {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                activity.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun nextPlayer(from: Int = activePlayer): Int = (from + 1) % ludoPlayers.size

    fun resetLudo() {
        ludoPositions = List(16) { -1 }
        dice = 0
        activePlayer = 0
        pendingLudoRoll = 0
        movableLudoPawns = emptyList()
        ludoLog = "Nouvelle partie. Sortez sur 6, capturez les adversaires et atteignez l’arrivée."
    }

    fun playLudoTurn(roll: Int, requestedPawn: Int? = null) {
        WhappySounds.haptic(context)
        dice = roll
        val player = activePlayer
        val playerName = ludoPlayers[player].first
        val starts = listOf(0, 13, 26, 39)
        val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)
        val pawns = (player * 4 until player * 4 + 4).filter { index ->
            val progress = ludoPositions[index]
            (progress < 0 && roll == 6) || (progress in 0..56 && progress + roll <= 57)
        }
        if (pawns.isEmpty()) {
            pendingLudoRoll = 0
            movableLudoPawns = emptyList()
            ludoLog = "$playerName lance $roll, mais aucun pion ne peut avancer. Tour suivant : ${ludoPlayers[nextPlayer(player)].first}."
            activePlayer = nextPlayer(player)
            return
        }
        if (player == 0 && requestedPawn == null && pawns.size > 1) {
            pendingLudoRoll = roll
            movableLudoPawns = pawns
            ludoLog = "Vous avez lancé $roll. Choisissez le pion à déplacer."
            WhappySounds.pieceSelected(context)
            return
        }
        fun wouldCapture(index: Int): Boolean {
            val progress = ludoPositions[index]
            val target = if (progress < 0) 0 else progress + roll
            if (target !in 0..51) return false
            val absolute = (starts[player] + target) % 52
            return absolute !in safeSquares && ludoPositions.indices.any { opponent ->
                val opponentPlayer = opponent / 4
                opponentPlayer != player && ludoPositions[opponent] in 0..51 &&
                    (starts[opponentPlayer] + ludoPositions[opponent]) % 52 == absolute
            }
        }
        val pawnIndex = requestedPawn?.takeIf { it in pawns } ?: pawns.maxByOrNull { index ->
            val progress = ludoPositions[index]
            (if (progress >= 0 && progress + roll == 57) 10_000 else 0) +
                (if (wouldCapture(index)) 5_000 else 0) + progress
        } ?: pawns.first()
        val current = ludoPositions[pawnIndex]
        val nextPositions = ludoPositions.toMutableList()
        var message: String
        var extraTurn = roll == 6
        var captured = false

        if (current == -1) {
            nextPositions[pawnIndex] = 0
            message = "$playerName sort le pion ${(pawnIndex % 4) + 1} avec un 6."
        } else {
            val target = current + roll
            nextPositions[pawnIndex] = target
            message = if (target == 57) "$playerName place le pion ${(pawnIndex % 4) + 1} à l’arrivée !" else "$playerName avance le pion ${(pawnIndex % 4) + 1} de $roll case${if (roll > 1) "s" else ""}."
            if (target in 0..51) {
                val absolute = (starts[player] + target) % 52
                ludoPositions.forEachIndexed { index, position ->
                    val opponentPlayer = index / 4
                    if (opponentPlayer != player && position in 0..51) {
                        val opponentAbsolute = (starts[opponentPlayer] + position) % 52
                        if (opponentAbsolute == absolute && absolute !in safeSquares) {
                            nextPositions[index] = -1
                            message += " Capture du pion ${(index % 4) + 1} de ${ludoPlayers[opponentPlayer].first} !"
                            captured = true
                            extraTurn = true
                            if (player == 0) xp += 40
                        }
                    }
                }
            }
        }
        ludoPositions = nextPositions
        pendingLudoRoll = 0
        movableLudoPawns = emptyList()
        if (captured) WhappySounds.capture(context) else WhappySounds.move(context)
        if (player == 0 && roll == 6) xp += 10
        val playerWon = (player * 4 until player * 4 + 4).all { nextPositions[it] == 57 }
        if (playerWon) {
            if (player == 0) { wins += 1; xp += 150 }
            celebrating = true
            WhappySounds.reward(context)
            WhappySounds.haptic(context, strong = true)
            ludoLog = "VICTOIRE DE ${playerName.uppercase()} · les quatre pions sont arrivés."
            return
        }
        activePlayer = if (extraTurn) player else nextPlayer(player)
        ludoLog = message + if (extraTurn) " Rejouez." else " Tour suivant : ${ludoPlayers[activePlayer].first}."
    }

    fun rollLudo() {
        if (diceRolling) return
        diceRolling = true
        WhappySounds.dice(context)
        gameScope.launch {
            repeat(9) { frame ->
                dice = ((System.nanoTime() / (frame + 3L)) % 6L).toInt() + 1
                delay(48L + frame * 5L)
            }
            val finalRoll = ((System.nanoTime() / 37L) % 6L).toInt() + 1
            dice = finalRoll
            playLudoTurn(finalRoll)
            diceRolling = false
        }
    }

    fun celebrateWin() {
        wins += 1
        celebrating = true
    }

    BackHandler(enabled = gameOpen) { gameOpen = false }
    if (!gameOpen) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF071426), Color(0xFF103D70), Color(0xFF087FCC))), RoundedCornerShape(30.dp))
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(WhappyAurora), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("WAPI PLAY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text("ARÈNE DE JEU", color = Color.White.copy(alpha = .68f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                            Text("3D NATIF", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Text("Une vraie partie.\nUn vrai rythme.", Modifier.padding(top = 18.dp), color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                    Text("Des modules séparés, des sons de jeu, des mouvements physiques et une progression qui reste avec vous.", Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = .82f), lineHeight = 19.sp)
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Victoires", wins.toString())
                        StatPill("XP", "$xp")
                        StatPill("Modules", "${games.size}")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Choisir un jeu", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Chaque module s’ouvre en plein écran", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = WhappyBlue, modifier = Modifier.size(22.dp))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gameCategories.forEach { category ->
                    FilterChip(
                        selected = gameFilter == category,
                        onClick = { gameFilter = category },
                        label = { Text(category, fontSize = 11.sp, fontWeight = FontWeight.Black) },
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${visibleGames.size} modules", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFE7F5FF), shape = RoundedCornerShape(10.dp)) {
                    Text("3D NATIF · SONS", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        items(visibleGames, key = { it.first }) { game ->
            GameModeCard(game.first, game.second, categoryForGame(game.first), gameIcon(game.first), selected == game.first) {
                selected = game.first
                gameOpen = true
                WhappySounds.gameOpen()
                WhappySounds.haptic(context)
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Retour à l’accueil") } }
    }
    } else {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF030812)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    WapiGameArenaHeader(
                        title = selected,
                        subtitle = if (selected == "King QI") "ARÈNE VOCALE" else "MOTEUR 3D NATIF · PARTIE EN COURS",
                        xp = xp,
                        onClose = { gameOpen = false },
                        onGuide = { showGameGuide = true },
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (selected) {
                            "King QI" -> KingQiArena(currentUserId = currentUserId, accountName = accountName, onStartLive = { gameOpen = false; onOpenLive() })
                            "Ludo WAPI" -> {
                                WapiLudoTabletop3D(
                                    positions = ludoPositions,
                                    activePlayer = activePlayer,
                                    die = dice.coerceAtLeast(1),
                                    rolling = diceRolling,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
                                    color = Color(0xE6121A28),
                                    shape = RoundedCornerShape(18.dp),
                                    shadowElevation = 14.dp,
                                ) {
                                    Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(ludoPlayers[activePlayer].second))
                                        Column(Modifier.padding(horizontal = 10.dp)) {
                                            Text("TOUR DE ${ludoPlayers[activePlayer].first.uppercase()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            Text(ludoLog, color = Color.White.copy(alpha = .70f), fontSize = 9.sp, maxLines = 2)
                                        }
                                        WapiRollingDie(value = dice.coerceAtLeast(1), rolling = diceRolling)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                                    color = Color(0xE6121A28),
                                    shape = RoundedCornerShape(22.dp),
                                    shadowElevation = 18.dp,
                                ) {
                                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (pendingLudoRoll > 0 && movableLudoPawns.isNotEmpty()) {
                                            movableLudoPawns.forEach { pawnIndex ->
                                                OutlinedButton(
                                                    onClick = { playLudoTurn(pendingLudoRoll, pawnIndex) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                ) { Text("PION ${(pawnIndex % 4) + 1}", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                            }
                                        } else {
                                            Button(onClick = ::rollLudo, enabled = !diceRolling, modifier = Modifier.width(190.dp).height(48.dp)) {
                                                Text(if (diceRolling) "LE DÉ ROULE…" else "LANCER LE DÉ", fontWeight = FontWeight.Black)
                                            }
                                        }
                                        OutlinedButton(onClick = ::resetLudo, enabled = !diceRolling, modifier = Modifier.height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("REJOUER") }
                                    }
                                }
                            }
                            "WAPI Sky" -> SkyRun3D(onXp = { xp += it }, onWin = ::celebrateWin)
                            "Billard WAPI" -> Billiards3D(onXp = { xp += it }, onWin = ::celebrateWin)
                            "Échecs" -> StrategyBoardGame(checkers = false, onXp = { xp += it }, onWin = ::celebrateWin)
                            "Jeu de dames" -> StrategyBoardGame(checkers = true, onXp = { xp += it }, onWin = ::celebrateWin)
                            "Cartes WAPI" -> WapiCardDuel(onXp = { xp += it }, onWin = ::celebrateWin)
                            "Poker WAPI" -> WapiPokerTable(onXp = { xp += it }, onWin = ::celebrateWin)
                            else -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { ArcadeChallengeCard(selected, round, answer, onAnswer = { option -> answer = option; if (option == "Le Live") xp += 25 }, onNext = { round += 1; answer = null }) }
                        }
                    }
                }
                if (celebrating) WapiVictoryCelebration(onFinished = { celebrating = false })
                if (showGameGuide) {
                    ModalBottomSheet(
                        onDismissRequest = { showGameGuide = false },
                        containerColor = Color(0xFFF7FAFD),
                        dragHandle = { Box(Modifier.padding(top = 10.dp).width(42.dp).height(4.dp).clip(CircleShape).background(WhappyMuted.copy(alpha = .28f))) },
                    ) {
                        Column(
                            Modifier.padding(horizontal = 18.dp, vertical = 8.dp).navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Guide de partie", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("Les règles et les commandes de $selected", color = WhappyMuted, fontSize = 12.sp)
                            GameInstructorCard(selected)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WapiGameArenaHeader(title: String, subtitle: String, xp: Int, onClose: () -> Unit, onGuide: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF06101D), Color(0xFF0D2F55), Color(0xFF07111F))))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onClose,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .09f), contentColor = Color.White),
        ) { Icon(Icons.Rounded.Close, "Quitter la partie") }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = WhappySky, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        }
        FilledIconButton(
            onClick = onGuide,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .09f), contentColor = WhappySky),
        ) { Icon(Icons.Rounded.Info, "Voir les règles") }
        Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(13.dp)) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4DE1A3)))
                Text("  XP $xp", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun WapiRollingDie(value: Int, rolling: Boolean) {
    val rotation by animateFloatAsState(if (rolling) 405f else 0f, tween(if (rolling) 440 else 180), label = "wapi-die")
    Box(
        Modifier.size(54.dp).graphicsLayer { rotationX = rotation; rotationY = rotation * .72f; shadowElevation = 18f; cameraDistance = 14f }
            .clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, WhappyLine, RoundedCornerShape(13.dp)),
    ) {
        val pips = when (value.coerceIn(1, 6)) {
            1 -> listOf(.5f to .5f)
            2 -> listOf(.28f to .28f, .72f to .72f)
            3 -> listOf(.28f to .28f, .5f to .5f, .72f to .72f)
            4 -> listOf(.28f to .28f, .72f to .28f, .28f to .72f, .72f to .72f)
            5 -> listOf(.28f to .28f, .72f to .28f, .5f to .5f, .28f to .72f, .72f to .72f)
            else -> listOf(.28f to .24f, .72f to .24f, .28f to .5f, .72f to .5f, .28f to .76f, .72f to .76f)
        }
        ComposeCanvas(Modifier.fillMaxSize()) { pips.forEach { (x, y) -> drawCircle(WhappyNavy, radius = size.minDimension * .075f, center = Offset(size.width * x, size.height * y)) } }
    }
}

@Composable
private fun WapiVictoryCelebration(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600)); onFinished() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
        ComposeCanvas(Modifier.fillMaxSize()) {
            val colors = listOf(Color(0xFFFFC928), WhappyBlue, Color(0xFFE43D4F), Color(0xFF12B981), Color(0xFF8B5CF6))
            repeat(90) { index ->
                val x = ((index * 73) % 101) / 100f * size.width
                val drift = sin(index * 1.7f) * 42f
                val y = -40f + (size.height + 100f) * progress.value + ((index * 47) % 180) - 90f
                drawRect(colors[index % colors.size], topLeft = Offset(x + drift * progress.value, y), size = androidx.compose.ui.geometry.Size(9f + index % 7, 18f + index % 9))
            }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = WhappyNavy.copy(alpha = .94f)) {
            Column(Modifier.padding(horizontal = 34.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VICTOIRE", color = Color(0xFFFFD54F), fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("Bravo — partie remportée !", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GameInstructorCard(gameName: String) {
    var expanded by rememberSaveable(gameName) { mutableStateOf(false) }
    val lesson = when (gameName) {
        "King QI" -> "Écoutez la voix King QI puis répondez au micro ou touchez une des quatre barres. En tournoi, les scores, crédits promotionnels et trophées sont validés côté serveur."
        "Billard WAPI" -> "Visez en faisant glisser depuis la bille blanche, dosez la force, puis relâchez pour frapper. Les rebonds et les poches sont calculés pendant le tir."
        "Jeu de dames" -> "Sélectionnez un pion puis une case diagonale. Les prises obligatoires sont signalées. Atteignez la dernière rangée pour couronner votre pion en dame."
        "Échecs" -> "Touchez une pièce puis sa destination. Les coups illégaux et la mise en échec sont refusés. Utilisez Rejouer pour recommencer la partie."
        "Poker WAPI" -> "Distribuez votre main, observez le flop, le turn et la river, puis choisissez suivre, relancer ou vous coucher. Les jetons sont virtuels."
        "Cartes WAPI" -> "Tirez une carte à chaque manche. La carte la plus forte gagne ; le premier à cinq manches remporte le duel."
        "Ludo WAPI" -> "Lancez le dé. Un six sort un pion, les cases sûres protègent vos pions et une capture renvoie l’adversaire à la maison."
        "WAPI Sky" -> "Démarrez la course puis touchez la moitié gauche ou droite pour changer de voie et éviter les obstacles."
        else -> "Lisez la consigne, choisissez une réponse puis passez à la manche suivante. Chaque action validée produit un retour sonore et visuel."
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FC)), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .16f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SmartToy, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) { Text("INSTRUCTEUR WAPI", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black); Text("Apprendre · $gameName", color = WhappyDark, fontSize = 14.sp, fontWeight = FontWeight.Black) }
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(if (expanded) "Masquer" else "Apprendre", color = WhappyBlue, fontWeight = FontWeight.Black, fontSize = 10.sp) }
            }
            if (expanded) Text(lesson, color = WhappyDark, fontSize = 12.sp, lineHeight = 18.sp)
            else Text("Guide rapide disponible avant chaque partie.", color = WhappyMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun GameModeCard(title: String, subtitle: String, category: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val colors = when (category) {
        "Stratégie" -> listOf(Color(0xFF101A39), Color(0xFF3458B8))
        "Cartes" -> listOf(Color(0xFF3A1759), Color(0xFF8A3EC2))
        "Arcade" -> listOf(Color(0xFF064E4A), Color(0xFF0BAA83))
        else -> listOf(Color(0xFF083B69), Color(0xFF0C9DDF))
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) WhappyBlue.copy(alpha = .55f) else WhappyLine),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 7.dp else 1.dp),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Surface(color = colors.last().copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                        Text(category.uppercase(), Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = colors.first(), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(subtitle, color = WhappyMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = WhappyMuted, modifier = Modifier.size(13.dp))
                    Text("Sons", color = WhappyMuted, fontSize = 9.sp)
                    Text("•", color = WhappyLine, fontSize = 10.sp)
                    Text(if (category == "Stratégie") "IA" else "Solo / duel", color = WhappyMuted, fontSize = 9.sp)
                }
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (active) WhappyBlue else Color(0xFFEAF4FA)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Ouvrir $title", tint = if (active) Color.White else WhappyBlue, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun WapiLudoTabletop3D(
    positions: List<Int>,
    activePlayer: Int,
    die: Int,
    rolling: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1.05f),
) {
    AndroidView(
        factory = { context -> WapiTabletop3DView(context) },
        update = { view -> view.setLudoScene(positions, activePlayer, die, rolling) },
        modifier = modifier.background(Color(0xFF08111D)),
    )
}

@Composable
private fun WapiStrategyTabletop3D(
    checkers: Boolean,
    board: List<String>,
    selected: Int,
    legalTargets: Set<Int>,
    onSquareTapped: (Int) -> Unit,
) {
    AndroidView(
        factory = { context -> WapiTabletop3DView(context) },
        update = { view ->
            view.onSquareTapped = onSquareTapped
            view.setStrategyScene(
                scene = if (checkers) WapiTabletop3DView.Scene.CHECKERS else WapiTabletop3DView.Scene.CHESS,
                board = board,
                selected = selected,
                legalTargets = legalTargets,
            )
        },
        modifier = Modifier.fillMaxSize().background(Color(0xFF08111D)),
    )
}

@Composable
private fun WapiPoolTabletop3D(
    balls: List<WapiPoolBall>,
    aimAngle: Float,
    power: Int,
    moving: Boolean,
    onAim: (Float, Float) -> Unit,
    onRelease: () -> Unit,
) {
    AndroidView(
        factory = { context -> WapiTabletop3DView(context) },
        update = { view ->
            view.setPoolScene(
                // Feed physics velocity to the native renderer so each ball
                // rolls according to the actual shot, not a decorative loop.
                balls.map { ball ->
                    floatArrayOf(
                        ball.x,
                        ball.y,
                        if (ball.pocketed) -1f else ball.id.toFloat(),
                        ball.vx,
                        ball.vy,
                    )
                },
                aimAngle,
                power,
                moving,
            )
            view.onPoolGesture = { x, y, released -> if (!moving) { onAim(x, y); if (released) onRelease() } }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LudoBoard(players: List<Triple<String, Color, String>>, positions: List<Int>) {
    val path = remember {
        listOf(
            6 to 0, 6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5, 5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6, 0 to 7,
            0 to 8, 1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8, 6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14, 7 to 14,
            8 to 14, 8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9, 9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8, 14 to 7,
            14 to 6, 13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6, 8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0, 7 to 0,
        )
    }
    val starts = listOf(0, 13, 26, 39)
    val base = listOf(2 to 2, 2 to 12, 12 to 12, 12 to 2)
    val finish = listOf(7 to 6, 6 to 7, 7 to 8, 8 to 7)
    val safe = setOf(0, 8, 13, 21, 26, 34, 39, 47)
    BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { rotationX = 7f; rotationY = -2f; shadowElevation = 24f; cameraDistance = 24f }.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(8.dp)) {
        val cell = maxWidth / 15
        Box(Modifier.size(maxWidth)) {
            Column(Modifier.fillMaxSize()) {
                repeat(15) { row ->
                    Row(Modifier.weight(1f)) {
                        repeat(15) { col ->
                            val pathIndex = path.indexOf(row to col)
                            val baseIndex = base.indexOf(row to col)
                            val finishIndex = finish.indexOf(row to col)
                            val color = when {
                                baseIndex >= 0 -> players[baseIndex].second.copy(alpha = .22f)
                                finishIndex >= 0 -> players[finishIndex].second.copy(alpha = .38f)
                                row in 6..8 && col in 6..8 -> WhappyBlue.copy(alpha = .92f)
                                pathIndex >= 0 && pathIndex in safe -> WhappyBlue.copy(alpha = .12f)
                                pathIndex >= 0 -> Color(0xFFF4F6FF)
                                (row < 5 && col < 5) -> players[0].second.copy(alpha = .10f)
                                (row < 5 && col > 9) -> players[1].second.copy(alpha = .10f)
                                (row > 9 && col > 9) -> players[2].second.copy(alpha = .10f)
                                (row > 9 && col < 5) -> players[3].second.copy(alpha = .10f)
                                else -> Color.White
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(4.dp)).background(color), contentAlignment = Alignment.Center) {
                                if (row in 6..8 && col in 6..8) Text("★", color = Color.White, fontSize = 11.sp)
                                else if (pathIndex in safe) Text("◆", color = WhappyBlue, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
            positions.forEachIndexed { index, position ->
                val coord = when {
                    position < 0 -> base[index]
                    position >= 52 -> finish[index]
                    else -> path[(starts[index] + position) % path.size]
                }
                Box(
                    Modifier
                        .offset(x = cell * coord.second, y = cell * coord.first)
                        .size(cell)
                        .padding(2.dp)
                        .graphicsLayer { rotationX = -10f; rotationY = 14f; shadowElevation = 12f; cameraDistance = 16f }
                        .clip(CircleShape)
                        .background(players[index].second),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((index + 1).toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun LudoStatus(players: List<Triple<String, Color, String>>, positions: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        players.forEachIndexed { index, player ->
            val pawns = if (positions.size >= 16) positions.subList(index * 4, index * 4 + 4) else listOf(positions.getOrElse(index) { -1 })
            val home = pawns.count { it < 0 }
            val arrived = pawns.count { it == 57 }
            val label = "$arrived arrivé${if (arrived > 1) "s" else ""} · $home maison"
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(player.second.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(player.third, fontSize = 18.sp)
                Text(player.first, Modifier.weight(1f).padding(start = 9.dp), color = WhappyDark, fontWeight = FontWeight.Bold)
                Text(label, color = player.second, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SkyRun3D(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var lane by rememberSaveable { mutableIntStateOf(1) }
    var obstacleLane by rememberSaveable { mutableIntStateOf(0) }
    var distance by rememberSaveable { mutableIntStateOf(0) }
    var energy by rememberSaveable { mutableIntStateOf(3) }
    var running by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("Appuyez sur Démarrer puis changez de voie pour éviter les blocs.") }

    LaunchedEffect(running) {
        while (running) {
            val nextObstacle = ((System.currentTimeMillis() / 317L) % 3L).toInt()
            obstacleLane = nextObstacle
            delay((720L - distance * 6L).coerceAtLeast(330L))
            distance += 1
            if (lane == nextObstacle) {
                energy -= 1
                message = "Impact ! Changez de voie plus tôt."
                WhappySounds.impact()
                WhappySounds.haptic(context, strong = true)
                if (energy <= 0) {
                    running = false
                    message = "Mission terminée · $distance portes franchies."
                }
            } else {
                onXp(5)
                WhappySounds.move(context)
                message = if (distance >= 20) "Vitesse MAX · gardez le cap !" else "Parfait · +5 XP"
                if (distance == 20) { onWin(); WhappySounds.reward(context) }
            }
        }
    }

    Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WAPI SKY ENGINE", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("Course WAPI", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(14.dp)) { Text("⚡ $energy  ·  $distance m", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Black) }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).graphicsLayer { rotationX = 5f; cameraDistance = 24f; shadowElevation = 22f }
                    .clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF62D5FF), Color(0xFF087ECC), Color(0xFF04233E))))
                    .pointerInput(running, lane) { detectTapGestures { offset -> if (running) { lane = if (offset.x < size.width / 2f) (lane - 1).coerceAtLeast(0) else (lane + 1).coerceAtMost(2); WhappySounds.haptic(context) } } },
            ) {
                Text("WAPI CITY", Modifier.align(Alignment.TopCenter).padding(top = 18.dp), color = Color.White.copy(alpha = .74f), fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { rotationX = 13f; rotationY = (index - 1) * -5f; cameraDistance = 20f }.clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color.White.copy(alpha = if (index == lane) .19f else .08f))) {
                            if (running && obstacleLane == index) Box(Modifier.align(Alignment.Center).size(50.dp).graphicsLayer { rotationX = 24f; rotationY = distance * 19f; shadowElevation = 20f }.clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFB629)), contentAlignment = Alignment.Center) { Text("◆", color = Color.White, fontSize = 22.sp) }
                            if (lane == index) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(58.dp).graphicsLayer { rotationX = -12f; rotationY = if (running) distance * 7f else 0f; shadowElevation = 28f }.clip(RoundedCornerShape(20.dp)).background(WhappyAurora), contentAlignment = Alignment.Center) { Text("W", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                }
                Text(if (running) "TOUCHEZ À GAUCHE OU À DROITE" else "PRÊT POUR LA MISSION", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Text(message, color = Color.White.copy(alpha = .86f), fontSize = 12.sp, lineHeight = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(enabled = running && lane > 0, onClick = { lane -= 1; WhappySounds.haptic(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp)) { Text("← GAUCHE") }
                Button(onClick = { if (running) running = false else { if (energy <= 0) { energy = 3; distance = 0 }; running = true; message = "Mission lancée · évitez les blocs."; WhappySounds.reward(context) } }, Modifier.weight(1.2f), shape = RoundedCornerShape(14.dp)) { Text(if (running) "PAUSE" else "DÉMARRER", fontWeight = FontWeight.Black) }
                OutlinedButton(enabled = running && lane < 2, onClick = { lane += 1; WhappySounds.haptic(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp)) { Text("DROITE →") }
            }
        }
    }
}

@Composable
private fun BilliardsLegacy(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var aim by rememberSaveable { mutableIntStateOf(0) }
    var power by rememberSaveable { mutableIntStateOf(2) }
    var shots by rememberSaveable { mutableIntStateOf(0) }
    var balls by rememberSaveable { mutableIntStateOf(9) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("Touchez la table pour placer le tir, puis frappez la bille blanche.") }
    val ballRotation by animateFloatAsState(targetValue = shots * 115f, animationSpec = spring(stiffness = 180f), label = "pool-ball")
    fun shoot() {
        shots += 1
        WhappySounds.dice(context); WhappySounds.haptic(context)
        val pocketed = ((aim + power * 3 + shots) % 4 == 0) || power == 4
        if (pocketed && balls > 0) {
            balls -= 1; score += 100; onXp(15); WhappySounds.reward(context); message = "Bille empochée · +100 points"
            if (balls == 0) { onWin(); onXp(150); message = "TABLE NETTOYÉE · victoire !" }
        } else { score = (score - 10).coerceAtLeast(0); message = "La bille touche la bande. Ajustez votre angle." }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF072D25))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("WAPI BILLIARDS", color = Color(0xFF72F2C8), fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Table interactive", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black) }; Text("$score pts · $balls billes", color = Color.White, fontWeight = FontWeight.Bold) }
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(300.dp)
                    .graphicsLayer { rotationX = 9f; rotationY = -2f; cameraDistance = 22f; shadowElevation = 28f }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF07845F), Color(0xFF034C3B))))
                    .pointerInput(Unit) {
                        detectTapGestures { point ->
                            aim = (((point.x / size.width) - .5f) * 8f).toInt().coerceIn(-4, 4)
                            power = ((1f - point.y / size.height) * 4f).toInt().coerceIn(1, 4)
                            message = "Visée ${aim * 6}° · puissance $power/4. Appuyez sur Frapper quand vous êtes prêt."
                            WhappySounds.haptic(context)
                        }
                    },
            ) {
                listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd, Alignment.TopCenter, Alignment.BottomCenter).forEach { alignment -> Box(Modifier.align(alignment).padding(3.dp).size(24.dp).clip(CircleShape).background(Color(0xFF021D17))) }
                repeat(balls) { index ->
                    val row = index / 4; val col = index % 4
                    Box(Modifier.offset(x = maxWidth * (.50f + col * .075f), y = (98 + row * 34).dp).size(27.dp).graphicsLayer { rotationX = ballRotation + index * 9f; rotationY = ballRotation; shadowElevation = 14f }.clip(CircleShape).background(listOf(Color(0xFFFFC928), Color(0xFFE53935), Color(0xFF236DE8), Color(0xFF7C3AED))[index % 4]), contentAlignment = Alignment.Center) { Text((index + 1).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                }
                Box(Modifier.offset(x = maxWidth * (.15f + aim * .035f), y = (190 - power * 12).dp).size(30.dp).graphicsLayer { rotationX = ballRotation; rotationY = ballRotation * .7f; shadowElevation = 18f }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("W", color = WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                Box(
                    Modifier
                        .offset(x = maxWidth * (.02f + aim * .024f), y = (202 - power * 12).dp)
                        .width(154.dp)
                        .height(9.dp)
                        .graphicsLayer { rotationZ = 180f + aim * 13f; translationX = (-power * 3).toFloat(); shadowElevation = 12f }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF5A3014), Color(0xFFD89343), Color(0xFFF8D897), Color(0xFF603018)))),
                )
                Text("ANGLE ${aim * 6}°  ·  PUISSANCE $power/4", Modifier.align(Alignment.TopCenter).padding(top = 16.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = .30f)).padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Text(message, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { aim = (aim - 1).coerceAtLeast(-4) }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("← VISER") }; OutlinedButton(onClick = { power = if (power == 4) 1 else power + 1 }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("FORCE $power") }; OutlinedButton(onClick = { aim = (aim + 1).coerceAtMost(4) }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("VISER →") } }
            Button(onClick = ::shoot, enabled = balls > 0, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("FRAPPER", fontWeight = FontWeight.Black) }
        }
    }
}

internal data class WapiPoolBall(
    val id: Int,
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val pocketed: Boolean = false,
)

private fun initialPoolBalls(): List<WapiPoolBall> = buildList {
    add(WapiPoolBall(0, .23f, .50f))
    var id = 1
    for (row in 0..4) for (column in 0..row) {
        add(WapiPoolBall(id++, .69f + row * .041f, .50f + (column - row / 2f) * .078f))
    }
}

/** Native top-down pool physics: aim by dragging the cue, then release to strike. */
@Composable
private fun Billiards3D(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var poolBalls by remember { mutableStateOf(initialPoolBalls()) }
    var aimAngle by rememberSaveable { mutableFloatStateOf(0f) }
    var power by rememberSaveable { mutableIntStateOf(2) }
    var shots by rememberSaveable { mutableIntStateOf(0) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    var physicsRunning by remember { mutableStateOf(false) }
    var lastCollisionSoundAt by remember { mutableLongStateOf(0L) }
    var message by rememberSaveable { mutableStateOf("Glissez sur la table pour déplacer le bâton, puis relâchez pour frapper.") }
    val remainingBalls = poolBalls.count { it.id != 0 && !it.pocketed }
    val pockets = listOf(.055f to .08f, .5f to .08f, .945f to .08f, .055f to .92f, .5f to .92f, .945f to .92f)
    val ballColors = listOf(Color(0xFFFFC928), Color(0xFFE53935), Color(0xFF236DE8), Color(0xFF7C3AED), Color(0xFF12B981), Color(0xFFF97316))

    fun resetTable() {
        poolBalls = initialPoolBalls()
        physicsRunning = false
        shots = 0
        score = 0
        message = "Nouvelle table · glissez dans la direction du tir."
    }

    fun strike() {
        if (physicsRunning || remainingBalls == 0) return
        val speed = .55f + power * .25f
        poolBalls = poolBalls.map { ball -> if (ball.id == 0) ball.copy(vx = cos(aimAngle) * speed, vy = sin(aimAngle) * speed) else ball }
        shots += 1
        physicsRunning = true
        message = "Tir en cours · puissance $power/4"
        WhappySounds.billiardCue(context)
        WhappySounds.haptic(context)
    }

    LaunchedEffect(physicsRunning) {
        if (!physicsRunning) return@LaunchedEffect
        val radius = .034f
        while (physicsRunning) {
            val before = poolBalls
            val next = before.map { ball ->
                if (ball.pocketed) ball else ball.copy(
                    x = ball.x + ball.vx * .018f,
                    y = ball.y + ball.vy * .018f,
                    vx = ball.vx * .991f,
                    vy = ball.vy * .991f,
                )
            }.toMutableList()
            var collisionEnergy = 0f
            next.indices.forEach { index ->
                val ball = next[index]
                if (ball.pocketed) return@forEach
                val hitPocket = pockets.any { (px, py) ->
                    val dx = ball.x - px; val dy = ball.y - py
                    sqrt(dx * dx + dy * dy) < .065f
                }
                if (hitPocket) {
                    next[index] = if (ball.id == 0) ball.copy(x = .23f, y = .50f, vx = 0f, vy = 0f)
                    else ball.copy(vx = 0f, vy = 0f, pocketed = true)
                    return@forEach
                }
                val minX = .105f; val maxX = .895f; val minY = .135f; val maxY = .865f
                var x = ball.x; var y = ball.y; var vx = ball.vx; var vy = ball.vy
                if (x < minX) { x = minX; collisionEnergy = max(collisionEnergy, kotlin.math.abs(vx)); vx = kotlin.math.abs(vx) * .94f }
                if (x > maxX) { x = maxX; collisionEnergy = max(collisionEnergy, kotlin.math.abs(vx)); vx = -kotlin.math.abs(vx) * .94f }
                if (y < minY) { y = minY; collisionEnergy = max(collisionEnergy, kotlin.math.abs(vy)); vy = kotlin.math.abs(vy) * .94f }
                if (y > maxY) { y = maxY; collisionEnergy = max(collisionEnergy, kotlin.math.abs(vy)); vy = -kotlin.math.abs(vy) * .94f }
                next[index] = ball.copy(x = x, y = y, vx = vx, vy = vy)
            }
            for (first in next.indices) for (second in first + 1 until next.size) {
                val a = next[first]; val b = next[second]
                if (a.pocketed || b.pocketed) continue
                val dx = b.x - a.x; val dy = b.y - a.y; val distance = sqrt(dx * dx + dy * dy)
                if (distance > 0f && distance < radius * 2f) {
                    val nx = dx / distance; val ny = dy / distance
                    val relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                    // Separate overlapping balls first; this removes sticking and
                    // repeated jitter when several balls collide in the break.
                    val correction = ((radius * 2f - distance) * .5f + .0002f)
                    val separatedA = a.copy(x = a.x - nx * correction, y = a.y - ny * correction)
                    val separatedB = b.copy(x = b.x + nx * correction, y = b.y + ny * correction)
                    if (relative < 0f) {
                        val impulse = -relative * .97f
                        collisionEnergy = max(collisionEnergy, impulse)
                        next[first] = separatedA.copy(vx = a.vx - impulse * nx, vy = a.vy - impulse * ny)
                        next[second] = separatedB.copy(vx = b.vx + impulse * nx, vy = b.vy + impulse * ny)
                    } else {
                        next[first] = separatedA
                        next[second] = separatedB
                    }
                }
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (collisionEnergy > .055f && now - lastCollisionSoundAt > 72L) {
                lastCollisionSoundAt = now
                WhappySounds.billiardCollision(context, (.22f + collisionEnergy * .45f).coerceAtMost(.66f))
            }
            poolBalls = next
            val newlyPocketed = next.count { it.pocketed } - before.count { it.pocketed }
            if (newlyPocketed > 0) {
                score += newlyPocketed * 100
                onXp(newlyPocketed * 15)
                WhappySounds.billiardPocket(context)
                message = "$newlyPocketed bille${if (newlyPocketed > 1) "s" else ""} empochée${if (newlyPocketed > 1) "s" else ""} · +${newlyPocketed * 100} points"
            }
            val moving = next.any { !it.pocketed && (kotlin.math.abs(it.vx) > .025f || kotlin.math.abs(it.vy) > .025f) }
            if (!moving) {
                physicsRunning = false
                if (next.none { it.id != 0 && !it.pocketed }) {
                    onWin()
                    onXp(150)
                    message = "TABLE NETTOYÉE · victoire réelle"
                }
            }
            delay(16L)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF03120F))) {
        WapiPoolTabletop3D(
            balls = poolBalls,
            aimAngle = aimAngle,
            power = power,
            moving = physicsRunning,
            onAim = { x, y ->
                poolBalls.firstOrNull { it.id == 0 }?.let { cue ->
                    val dx = x - cue.x
                    val dy = y - cue.y
                    aimAngle = atan2(dy, dx)
                    power = (((sqrt(dx * dx + dy * dy) - .03f) * 9f).toInt() + 1).coerceIn(1, 4)
                    message = "Bâton orienté · puissance $power/4. Relâchez pour frapper."
                }
            },
            onRelease = ::strike,
        )
        Surface(
            Modifier.align(Alignment.TopStart).padding(18.dp),
            color = Color(0xE607241E),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 16.dp,
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column { Text("TABLE PROFESSIONNELLE", color = Color(0xFF72F2C8), fontSize = 9.sp, fontWeight = FontWeight.Black); Text(message, color = Color.White, fontSize = 11.sp, maxLines = 2) }
                Column(Modifier.padding(start = 20.dp), horizontalAlignment = Alignment.End) { Text("$score PTS", color = Color.White, fontWeight = FontWeight.Black); Text("$remainingBalls BILLES · $shots TIRS", color = Color.White.copy(alpha = .64f), fontSize = 9.sp) }
            }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(18.dp),
            color = Color(0xE607241E),
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 20.dp,
        ) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !physicsRunning, onClick = { aimAngle -= .12f }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("← VISER") }
                OutlinedButton(enabled = !physicsRunning, onClick = { power = if (power == 4) 1 else power + 1 }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("FORCE $power") }
                Button(onClick = ::strike, enabled = !physicsRunning && remainingBalls > 0, modifier = Modifier.width(150.dp).height(48.dp), shape = RoundedCornerShape(15.dp)) { Text("FRAPPER", fontWeight = FontWeight.Black) }
                OutlinedButton(enabled = !physicsRunning, onClick = { aimAngle += .12f }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("VISER →") }
                TextButton(onClick = ::resetTable, enabled = !physicsRunning) { Text("REJOUER", color = Color.White) }
            }
        }
    }
}

private fun initialStrategyBoard(checkers: Boolean, checkersSize: Int = 8): List<String> = if (checkers) {
    val size = checkersSize.takeIf { it == 10 } ?: 8
    val occupiedRows = if (size == 10) 4 else 3
    List(size * size) { index ->
        val row = index / size; val col = index % size
        if ((row + col) % 2 == 1 && row < occupiedRows) "b"
        else if ((row + col) % 2 == 1 && row >= size - occupiedRows) "w"
        else ""
    }
} else listOf("♜","♞","♝","♛","♚","♝","♞","♜") + List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } + listOf("♖","♘","♗","♕","♔","♗","♘","♖")

private fun whitePiece(piece: String) = WapiGameRules.isWhite(piece)
private fun blackPiece(piece: String) = WapiGameRules.isBlack(piece)

@Composable
private fun StrategyBoardGame(checkers: Boolean, onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var checkersRulesPreset by rememberSaveable(checkers) { mutableStateOf("international") }
    var board by rememberSaveable(checkers) { mutableStateOf(initialStrategyBoard(checkers, if (checkers) 10 else 8)) }
    var selected by rememberSaveable(checkers) { mutableIntStateOf(-1) }
    var whiteTurn by rememberSaveable(checkers) { mutableStateOf(true) }
    var versusAi by rememberSaveable(checkers) { mutableStateOf(true) }
    var aiDifficulty by rememberSaveable(checkers) { mutableStateOf("medium") }
    var aiThinking by rememberSaveable(checkers) { mutableStateOf(false) }
    val checkersRules = when (checkersRulesPreset) {
        "english" -> WapiCheckersRules(mandatoryCapture = true, backwardCapture = false, flyingKings = false)
        "free" -> WapiCheckersRules(mandatoryCapture = false, backwardCapture = true, flyingKings = false)
        else -> WapiCheckersRules(mandatoryCapture = true, backwardCapture = true, flyingKings = true)
    }
    var message by rememberSaveable(checkers) { mutableStateOf(if (checkers) "Vous jouez les blancs contre l’IA. Capturez en diagonale." else "Vous jouez les blancs contre l’IA. Sélectionnez une pièce puis une case.") }
    fun choose(index: Int) {
        if ((versusAi && !whiteTurn) || aiThinking) return
        val piece = board[index]
        if (selected < 0) {
            if ((whiteTurn && whitePiece(piece)) || (!whiteTurn && blackPiece(piece))) { selected = index; WhappySounds.pieceSelected(context); WhappySounds.haptic(context) }
            return
        }
        val from = selected
        if ((whiteTurn && whitePiece(piece)) || (!whiteTurn && blackPiece(piece))) { selected = index; WhappySounds.pieceSelected(context); return }
        val result = if (checkers) WapiGameRules.checkersMove(board, from, index, whiteTurn, checkersRules) else WapiGameRules.chessMove(board, from, index, whiteTurn)
        if (result == null) { message = if (checkers && checkersRules.mandatoryCapture && WapiGameRules.hasCheckersCapture(board, whiteTurn, checkersRules)) "Une capture est disponible et devient prioritaire." else "Mouvement non autorisé ou roi exposé."; WhappySounds.impact(); selected = -1; return }
        board = result.board
        onXp(if (result.captured) 12 else 3)
        when { result.promoted -> WhappySounds.crowned(context); result.captured -> WhappySounds.capture(context); else -> WhappySounds.move(context) }
        val mustContinue = checkers && result.captured && WapiGameRules.hasCheckersCaptureFrom(result.board, index, whiteTurn, checkersRules)
        if (mustContinue) {
            selected = index
            message = "Prise en chaîne obligatoire · continuez avec le même pion."
        } else {
            selected = -1
            whiteTurn = !whiteTurn
            message = when { result.promoted -> "Dame couronnée · +12 XP"; result.captured -> "Capture réussie · +12 XP"; else -> "À ${if (whiteTurn) "Blanc" else "Noir"} de jouer" }
        }
        if (!mustContinue) {
            val nextSide = whiteTurn
            val noReply = !WapiGameRules.hasAnyLegalMove(result.board, nextSide, checkers, checkersRules)
            if (result.board.none(::blackPiece) || result.board.none(::whitePiece) || noReply) {
                if (!checkers && noReply && !WapiGameRules.isChessKingInCheck(result.board, nextSide)) {
                    message = "PAT · partie nulle"
                } else {
                    onWin(); onXp(120); WhappySounds.reward(context)
                    message = if (checkers) "VICTOIRE · aucun mouvement adverse" else "ÉCHEC ET MAT · victoire"
                }
            }
        }
    }
    LaunchedEffect(board, whiteTurn, versusAi, aiDifficulty, checkersRulesPreset) {
        if (versusAi && !whiteTurn && !aiThinking) {
            aiThinking = true
            message = "L’IA analyse le plateau…"
            delay(420L)
            val move = WapiGameRules.bestMove(board, whiteTurn = false, checkers = checkers, difficulty = aiDifficulty, checkersRules = checkersRules)
            if (move != null) {
                var landing = move.second
                var result = if (checkers) WapiGameRules.checkersMove(board, move.first, landing, false, checkersRules) else WapiGameRules.chessMove(board, move.first, landing, false)
                if (result != null) {
                    var aiBoard = result.board
                    var captured = result.captured
                    var promoted = result.promoted
                    while (checkers && result?.captured == true) {
                        val continuation = WapiGameRules.checkersCaptureTargets(aiBoard, landing, false, checkersRules)
                        if (continuation.isEmpty()) break
                        delay(230L)
                        val nextLanding = continuation.maxByOrNull { target ->
                            WapiGameRules.checkersMove(aiBoard, landing, target, false, checkersRules)?.let {
                                (if (it.promoted) 1000 else 0) + target
                            } ?: Int.MIN_VALUE
                        } ?: break
                        result = WapiGameRules.checkersMove(aiBoard, landing, nextLanding, false, checkersRules)
                        val continuationResult = result ?: break
                        aiBoard = continuationResult.board
                        landing = nextLanding
                        captured = captured || continuationResult.captured
                        promoted = promoted || continuationResult.promoted
                    }
                    board = aiBoard
                    whiteTurn = true
                    when { promoted -> WhappySounds.crowned(context); captured -> WhappySounds.capture(context); else -> WhappySounds.move(context) }
                    val noReply = !WapiGameRules.hasAnyLegalMove(aiBoard, true, checkers, checkersRules)
                    message = when {
                        !checkers && noReply && !WapiGameRules.isChessKingInCheck(aiBoard, true) -> "PAT · partie nulle"
                        noReply -> if (checkers) "L’IA gagne · aucun mouvement disponible" else "ÉCHEC ET MAT · l’IA gagne"
                        captured -> "L’IA termine sa prise. À vous de jouer."
                        else -> "À vous de jouer."
                    }
                }
            }
            aiThinking = false
        }
    }
    val legalTargets = remember(board, selected, whiteTurn, checkers, checkersRules) {
        if (selected !in board.indices) emptySet() else board.indices.filterTo(mutableSetOf()) { target ->
            if (checkers) WapiGameRules.checkersMove(board, selected, target, whiteTurn, checkersRules) != null
            else WapiGameRules.chessMove(board, selected, target, whiteTurn) != null
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF03070D))) {
        WapiStrategyTabletop3D(
            checkers = checkers,
            board = board,
            selected = selected,
            legalTargets = legalTargets,
            onSquareTapped = ::choose,
        )
        Surface(
            Modifier.align(Alignment.TopStart).padding(18.dp),
            color = Color(0xE60A1320),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 16.dp,
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (checkers) "WAPI DAMES · TABLE 3D" else "WAPI ÉCHECS · TABLE 3D", color = WhappySky, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(message, color = Color.White, fontSize = 11.sp, maxLines = 2)
                }
                Text(if (aiThinking) "IA ANALYSE…" else if (whiteTurn) "À VOUS" else "ADVERSAIRE", Modifier.padding(start = 18.dp), color = if (whiteTurn) Color(0xFF72F2C8) else Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(18.dp),
            color = Color(0xE60A1320),
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 20.dp,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checkers) {
                    listOf("international" to "INTERNATIONAL", "english" to "ANGLAIS", "free" to "LIBRE").forEach { option ->
                        OutlinedButton(
                            onClick = {
                                checkersRulesPreset = option.first
                                board = initialStrategyBoard(true, if (option.first == "international") 10 else 8)
                                selected = -1
                                whiteTurn = true
                                aiThinking = false
                                message = "Nouvelle partie · règles ${option.second.lowercase()}"
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (checkersRulesPreset == option.first) WhappyBlue else Color.Transparent, contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (checkersRulesPreset == option.first) WhappyBlue else Color.White.copy(alpha = .28f)),
                        ) { Text(option.second, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }
                if (versusAi) {
                    listOf("easy" to "IA FACILE", "medium" to "IA MOYENNE", "hard" to "IA DIFFICILE", "ultra" to "IA ULTRA").forEach { option ->
                        OutlinedButton(
                            onClick = { aiDifficulty = option.first },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (aiDifficulty == option.first) WhappyBlue else Color.Transparent, contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (aiDifficulty == option.first) WhappyBlue else Color.White.copy(alpha = .28f)),
                        ) { Text(option.second, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }
                OutlinedButton(onClick = { versusAi = !versusAi; board = initialStrategyBoard(checkers, if (checkersRulesPreset == "international") 10 else 8); selected = -1; whiteTurn = true; aiThinking = false; message = if (!versusAi) "Deux joueurs sur cet appareil. Les blancs commencent." else "Vous jouez les blancs contre l’IA." }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(if (versusAi) "2 JOUEURS" else "CONTRE IA", fontSize = 9.sp) }
                Button(onClick = { board = initialStrategyBoard(checkers, if (checkersRulesPreset == "international") 10 else 8); selected = -1; whiteTurn = true; aiThinking = false; message = if (versusAi) "Nouvelle partie contre l’IA." else "Nouvelle partie locale." }) { Text("REJOUER", fontSize = 9.sp, fontWeight = FontWeight.Black) }
            }
        }
        Text("TOUCHEZ UNE PIÈCE · GLISSEZ POUR ORIENTER LA CAMÉRA", Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 92.dp), color = Color.White.copy(alpha = .62f), fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WapiCheckersPiece(piece: String, selected: Boolean, size: Dp) {
    val light = whitePiece(piece)
    val king = piece == "W" || piece == "B"
    val edge = if (light) Color(0xFFC7D5E4) else Color(0xFF071827)
    val center = if (light) Color(0xFFFDFEFF) else Color(0xFF26394B)
    Box(
        Modifier.size(size).graphicsLayer { rotationX = -12f; rotationY = 8f; shadowElevation = if (selected) 28f else 18f }
            .clip(CircleShape).background(Brush.radialGradient(listOf(center, edge))).border(if (selected) 3.dp else 2.dp, if (selected) Color(0xFFFFD54F) else edge.copy(alpha = .75f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(.72f).border(2.dp, if (light) Color.White.copy(alpha = .78f) else Color.White.copy(alpha = .18f), CircleShape))
        if (king) Text("♛", color = if (light) Color(0xFFB8860B) else Color(0xFFFFD54F), fontSize = (size.value * .54f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WapiCardDuel(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var round by rememberSaveable { mutableIntStateOf(0) }; var player by rememberSaveable { mutableIntStateOf(0) }; var rival by rememberSaveable { mutableIntStateOf(0) }; var playerScore by rememberSaveable { mutableIntStateOf(0) }; var rivalScore by rememberSaveable { mutableIntStateOf(0) }; var message by rememberSaveable { mutableStateOf("Tirez une carte. La plus forte remporte la manche.") }
    val names = listOf("2","3","4","5","6","7","8","9","10","V","D","R","A")
    fun draw() { round += 1; player = ((System.currentTimeMillis() / 31L) % 13L).toInt() + 2; rival = ((System.currentTimeMillis() / 47L + round) % 13L).toInt() + 2; WhappySounds.cardFlip(context); when { player > rival -> { playerScore++; onXp(10); WhappySounds.reward(context); message = "Manche gagnée · +10 XP" }; rival > player -> { rivalScore++; WhappySounds.impact(); message = "L’adversaire gagne cette manche." }; else -> message = "Égalité parfaite." }; WhappySounds.haptic(context); if (playerScore == 5) { onWin(); onXp(100); message = "VICTOIRE DU DUEL · +100 XP" } }
    Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121A47))) { Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("WAPI CARDS", color = WhappySky, fontWeight = FontWeight.Black, fontSize = 10.sp); Text("$playerScore  —  $rivalScore", Modifier.padding(vertical = 8.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { listOf(player to "VOUS", rival to "RIVAL").forEachIndexed { index, card -> Card(Modifier.size(112.dp, 164.dp).graphicsLayer { rotationY = if (round == 0) 180f else if (index == 0) -8f else 8f; rotationX = 4f; shadowElevation = 28f; cameraDistance = 18f }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(card.second, color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(if (card.first == 0) "W" else names[(card.first - 2).coerceIn(0, 12)], color = if (index == 0) WhappyBlue else Color(0xFFE53935), fontSize = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally)); Text(if (index == 0) "◆" else "♥", color = if (index == 0) WhappyBlue else Color(0xFFE53935), fontSize = 22.sp) } } } }; Text(message, Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = .84f), fontSize = 12.sp); Button(onClick = ::draw, enabled = playerScore < 5, modifier = Modifier.width(320.dp).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("TIRER LES CARTES", fontWeight = FontWeight.Black) }; OutlinedButton(onClick = { round = 0; player = 0; rival = 0; playerScore = 0; rivalScore = 0; message = "Nouvelle partie." }, Modifier.padding(top = 8.dp).width(320.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("REJOUER") } } }
}

private data class WapiPokerCard(val rank: Int, val suit: String) {
    val label: String get() = when (rank) { 14 -> "A"; 13 -> "R"; 12 -> "D"; 11 -> "V"; else -> rank.toString() }
}

private fun wapiPokerDeck(): List<WapiPokerCard> = listOf("♠", "♥", "♦", "♣").flatMap { suit -> (2..14).map { rank -> WapiPokerCard(rank, suit) } }

private fun wapiPokerScore(cards: List<WapiPokerCard>): List<Int> {
    val counts = cards.groupingBy { it.rank }.eachCount()
    val groups = counts.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
    val unique = counts.keys.sortedDescending()
    val straightHigh = when {
        14 in unique && 2 in unique && 3 in unique && 4 in unique && 5 in unique -> 5
        else -> unique.windowed(5).firstOrNull { window -> window.zipWithNext().all { (a, b) -> a - b == 1 } }?.firstOrNull() ?: 0
    }
    val flush = cards.groupingBy { it.suit }.eachCount().values.any { it >= 5 }
    return when {
        flush && straightHigh > 0 -> listOf(8, straightHigh)
        groups.firstOrNull()?.value == 4 -> listOf(7, groups.first().key, groups.getOrNull(1)?.key ?: 0)
        groups.getOrNull(0)?.value == 3 && groups.getOrNull(1)?.value == 2 -> listOf(6, groups[0].key, groups[1].key)
        flush -> listOf(5) + cards.filter { it.suit == cards.groupingBy { card -> card.suit }.eachCount().maxBy { it.value }.key }.map { it.rank }.sortedDescending().take(5)
        straightHigh > 0 -> listOf(4, straightHigh)
        groups.firstOrNull()?.value == 3 -> listOf(3, groups[0].key) + groups.drop(1).map { it.key }.sortedDescending().take(2)
        groups.getOrNull(0)?.value == 2 && groups.getOrNull(1)?.value == 2 -> listOf(2, groups[0].key, groups[1].key, groups.getOrNull(2)?.key ?: 0)
        groups.firstOrNull()?.value == 2 -> listOf(1, groups[0].key) + groups.drop(1).map { it.key }.sortedDescending().take(3)
        else -> listOf(0) + unique.take(5)
    }
}

private fun wapiPokerHandName(score: List<Int>): String = when (score.firstOrNull()) {
    8 -> "Quinte flush"
    7 -> "Carré"
    6 -> "Full"
    5 -> "Couleur"
    4 -> "Quinte"
    3 -> "Brelan"
    2 -> "Double paire"
    1 -> "Paire"
    else -> "Carte haute"
}

private fun compareWapiPokerScores(left: List<Int>, right: List<Int>): Int {
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = (left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}

@Composable
private fun WapiPokerTable(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf(emptyList<WapiPokerCard>()) }
    var rival by remember { mutableStateOf(emptyList<WapiPokerCard>()) }
    var board by remember { mutableStateOf(emptyList<WapiPokerCard>()) }
    var stage by remember { mutableIntStateOf(0) }
    var pot by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Distribuez une main pour commencer.") }

    fun deal() {
        val deck = wapiPokerDeck().shuffled()
        player = deck.take(2)
        rival = deck.drop(2).take(2)
        board = deck.drop(4).take(5)
        stage = 0
        pot = 100
        finished = false
        message = "Pré-flop · choisissez votre action."
        WhappySounds.cardFlip(context)
        WhappySounds.haptic(context)
    }

    fun act(action: String) {
        if (finished) return
        WhappySounds.pokerAction()
        WhappySounds.haptic(context)
        if (action == "fold") {
            finished = true
            message = "Vous vous couchez · l’IA remporte le pot."
            return
        }
        pot += if (action == "raise") 100 else 50
        stage += 1
        if (stage >= 4) {
            val playerScore = wapiPokerScore(player + board)
            val rivalScore = wapiPokerScore(rival + board)
            val result = playerScore.zip(rivalScore).firstOrNull { it.first != it.second }
            finished = true
            when {
                result == null -> message = "Égalité · ${wapiPokerHandName(playerScore)}. Pot partagé."
                compareWapiPokerScores(playerScore, rivalScore) > 0 -> { message = "VICTOIRE · ${wapiPokerHandName(playerScore)} · +150 XP"; onXp(150); onWin(); WhappySounds.reward(context) }
                else -> { message = "L’IA gagne avec ${wapiPokerHandName(rivalScore)}."; WhappySounds.impact() }
            }
        } else {
            message = when (stage) { 1 -> "Flop révélé · l’IA suit."; 2 -> "Turn révélé · l’IA suit."; else -> "River révélée · choisissez votre action." }
        }
    }

    LaunchedEffect(Unit) { deal() }
    Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF092E2A))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("WAPI POKER", color = Color(0xFF68F0C1), fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Texas Hold’em", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black); Text("Contre l’IA · jetons virtuels uniquement", color = Color.White.copy(alpha = .68f), fontSize = 10.sp) }
                Text("POT $pot", color = Color(0xFFFFD166), fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { player.forEach { card -> PokerCardView(card, false, Modifier.weight(1f)) }; if (player.isEmpty()) Text("Votre main sera distribuée…", color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(10.dp)) }
            val revealed = when (stage) { 0 -> 0; 1 -> 3; 2 -> 4; else -> 5 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { board.forEachIndexed { index, card -> PokerCardView(card, index >= revealed, Modifier.weight(1f)) } }
            Text(message, color = Color.White.copy(alpha = .88f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { act("fold") }, enabled = !finished && player.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("COUCHER", fontSize = 10.sp) }
                Button(onClick = { act("call") }, enabled = !finished && player.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (stage == 0) "SUIVRE" else "CHECK", fontSize = 10.sp) }
                Button(onClick = { act("raise") }, enabled = !finished && player.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB703))) { Text("RELANCER", fontSize = 10.sp, color = WhappyDark) }
            }
            if (finished) OutlinedButton(onClick = ::deal, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("NOUVELLE MAIN") }
        }
    }
}

@Composable
private fun PokerCardView(card: WapiPokerCard, hidden: Boolean, modifier: Modifier = Modifier) {
    val red = card.suit == "♥" || card.suit == "♦"
    Card(modifier.height(78.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (hidden) Color(0xFF183B64) else Color.White)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (hidden) "W" else "${card.label}${card.suit}", color = if (hidden) Color.White else if (red) Color(0xFFD7263D) else WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun ArcadeChallengeCard(selected: String, round: Int, answer: String?, onAnswer: (String) -> Unit, onNext: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(selected, color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("Manche $round · question 1/3", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Quel espace WAPI permet de diffuser en direct ?", color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 23.sp)
            listOf("Le Live", "Le Marché", "Les Services").forEach { option ->
                OutlinedButton(onClick = { onAnswer(option) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = if (answer == option) WhappyBlue else WhappyDark)) {
                    Text(option, Modifier.weight(1f), textAlign = TextAlign.Start)
                    if (answer == option) Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(17.dp))
                }
            }
            if (answer != null) Text(if (answer == "Le Live") "Bonne réponse · +25 XP" else "Pas grave. Rejouez pour progresser.", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Button(enabled = answer != null, onClick = onNext, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text("  Question suivante", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(label.uppercase(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ServicesScreen(
    onOpenMarket: () -> Unit,
    onOpenBusiness: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_native_services") }
    var requests by remember { mutableStateOf(prefs.getStringSet("requests", emptySet()).orEmpty().toList().sortedDescending()) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }

    fun saveRequests(next: List<String>) {
        requests = next.sortedDescending()
        prefs.edit().putStringSet("requests", requests.toSet()).apply()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                Column(Modifier.padding(22.dp)) {
                    Text("WAPI PAY", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("Paiements non activés", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Aucun opérateur de paiement vérifié n’est encore connecté à ce compte.", Modifier.padding(top = 7.dp), color = Color.White.copy(alpha = .74f), fontSize = 11.sp, lineHeight = 16.sp)
                    Surface(Modifier.padding(top = 16.dp), color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Verified, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Aucun solde fictif, aucun débit simulé", Modifier.padding(start = 9.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        feedback?.let { message ->
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) { Text(message, Modifier.padding(14.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } }
        }
        item { Text("Préparer une demande", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black) }
        item {
            BoxWithConstraints {
                val cell = (maxWidth - 12.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaceCard("Transport", "Créer un brouillon", Icons.Rounded.Schedule, cell) { details = ""; dialog = "Transport" }
                        SpaceCard("Livraison", "Préparer un colis", Icons.AutoMirrored.Rounded.ReceiptLong, cell) { details = ""; dialog = "Livraison" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaceCard("Assistance", "Demander de l’aide", Icons.Rounded.Verified, cell) { details = ""; dialog = "Assistance" }
                        SpaceCard("Marketplace", "Acheter local", Icons.Rounded.Storefront, cell, onOpenMarket)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenBusiness), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BusinessCenter, null, tint = WhappyBlue)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Espace Business", color = WhappyDark, fontWeight = FontWeight.Black); Text("Pages, campagnes, deals et suivi des paiements", color = WhappyMuted, fontSize = 11.sp) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
                }
            }
        }
        item { Text("Brouillons récents", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black) }
        if (requests.isEmpty()) item { Text("Aucun brouillon. Choisissez un service pour préparer une demande.", color = WhappyMuted) }
        items(requests.take(5), key = { it }) { raw ->
            val parts = raw.split("|", limit = 3)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = WhappyBlue)
                    Column(Modifier.padding(start = 11.dp)) { Text(parts.getOrElse(1) { "Service" }, color = WhappyDark, fontWeight = FontWeight.Bold); Text(parts.getOrElse(2) { "Brouillon enregistré" }, color = WhappyMuted, fontSize = 11.sp, maxLines = 2) }
                }
            }
        }
    }
    if (dialog in listOf("Transport", "Livraison", "Assistance")) {
        val service = dialog.orEmpty()
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Demande · $service", fontWeight = FontWeight.Black) },
            text = { OutlinedTextField(details, { details = it }, label = { Text(if (service == "Transport") "Départ et destination" else "Décrivez votre besoin") }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = {
                val value = details.trim()
                if (value.length >= 5) {
                    saveRequests(listOf("${System.currentTimeMillis()}|$service|$value") + requests)
                    feedback = "Brouillon $service enregistré sur cet appareil. Aucun prestataire n’a encore été contacté."
                    details = ""; dialog = null
                } else feedback = "Ajoutez suffisamment de détails pour traiter la demande."
            }) { Text("Enregistrer le brouillon") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } },
        )
    }
}

private enum class WhappyStudioSection(val label: String) {
    MOTION("Mouvements"),
    IDENTITY("Mon image"),
    STYLE("Mes tenues"),
    VOICE("Ma voix"),
    MISSIONS("Missions"),
    PRODUCE("Studio vidéo"),
}

@Composable
private fun WhappyStudioScreen(
    state: WhappyUiState,
    preview: Boolean,
    userName: String,
    onBack: () -> Unit,
    onSaveConsent: (Boolean) -> Unit,
    onUploadAsset: (Uri, String, String) -> Unit,
    onCreateAutomation: (String, String, String, String, String) -> Unit,
    onToggleAutomation: (String, Boolean) -> Unit,
    onDeleteAutomation: (String) -> Unit,
    onCreateRender: (String, String, String, List<String>) -> Unit,
) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(WhappyStudioSection.MOTION) }
    var localProfile by remember {
        mutableStateOf(
            WhappyTwinProfile(
                displayName = userName,
                identityConsent = preview,
                voiceConsent = preview,
                movementConsent = preview,
                videoUrl = if (preview) "demo-portrait" else "",
            ),
        )
    }
    var localAutomations by remember {
        mutableStateOf(
            if (preview) listOf(WhappyTwinAutomation("demo-auto", "Vendeur 24/7", "incoming-message", "inbox", "reply-video", true)) else emptyList(),
        )
    }
    var localRenders by remember { mutableStateOf(emptyList<WhappyTwinRender>()) }
    val profile = if (preview) localProfile else state.twinProfile ?: WhappyTwinProfile(displayName = userName)
    val automations = if (preview) localAutomations else state.twinAutomations
    val renders = if (preview) localRenders else state.twinRenders
    val readiness = (profile.readiness + if (automations.isNotEmpty()) 20 else 0).coerceAtMost(100)
    var selectedGestures by remember { mutableStateOf(listOf("welcome", "show", "explain", "point")) }
    var script by remember { mutableStateOf("Bonjour, bienvenue sur WAPI. Aujourd’hui je vous présente une opportunité pensée pour vous.") }
    var language by remember { mutableStateOf("fr-FR") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingVoice by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoKind by remember { mutableStateOf("video") }
    val tts = remember { TextToSpeech(context) { _ -> } }

    fun acceptAsset(uri: Uri, kind: String, type: String) {
        if (preview) {
            localProfile = when (kind) {
                "voice" -> localProfile.copy(voiceUrl = uri.toString(), voiceStatus = "sampled")
                "movement" -> localProfile.copy(movementUrl = uri.toString(), movementStatus = "sampled")
                "outfit" -> localProfile.copy(outfitUrl = uri.toString())
                else -> localProfile.copy(videoUrl = uri.toString())
            }
        } else onUploadAsset(uri, kind, type)
    }

    fun launchVideoCapture(kind: String) {
        val file = File(WapiMediaStore.cacheDirectory(context), "whappy-$kind-${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingVideoKind = kind
        pendingVideoUri = uri
    }

    val videoCapture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { captured ->
        val uri = pendingVideoUri
        if (captured && uri != null) acceptAsset(uri, pendingVideoKind, "video/mp4")
    }
    fun openVideoCamera(kind: String) {
        launchVideoCapture(kind)
        pendingVideoUri?.let(videoCapture::launch)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openVideoCamera(pendingVideoKind)
    }

    fun startVoice() {
        runCatching { createVoiceRecorder(context) }.onSuccess { (activeRecorder, file) ->
            recorder = activeRecorder
            recordingFile = file
            recordingStartedAt = System.currentTimeMillis()
            recordingSeconds = 0L
            recordingVoice = true
            WhappySounds.voiceRecordingStarted()
        }
    }
    fun stopVoice(save: Boolean) {
        val active = recorder
        val file = recordingFile
        runCatching { active?.stop() }
        active?.release()
        recorder = null
        recordingFile = null
        recordingVoice = false
        if (active != null) WhappySounds.voiceRecordingStopped()
        if (save && file != null && file.exists() && file.length() > 0L) acceptAsset(Uri.fromFile(file), "voice", "audio/mp4")
        else {
            file?.delete()
            if (!save) WhappySounds.voiceRecordingCancelled()
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startVoice() }
    val outfitPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { acceptAsset(it, "outfit", context.contentResolver.getType(it) ?: "image/jpeg") }
    }

    LaunchedEffect(recordingVoice) {
        while (recordingVoice) {
            recordingSeconds = (System.currentTimeMillis() - recordingStartedAt) / 1000
            delay(1_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release()
            tts.shutdown()
        }
    }

    Column(Modifier.fillMaxSize().background(WhappyBackground)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SmartToy, null, tint = WhappyBlue) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("MON WAPI", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Jumeau numérique · sous votre contrôle", color = WhappyMuted, fontSize = 11.sp)
            }
            if (state.twinBusy) CircularProgressIndicator(Modifier.size(22.dp), color = WhappyBlue, strokeWidth = 2.dp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("WAPI JUMEAU NUMÉRIQUE", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("Votre présence,\nmultipliée.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                        Text("Préparez votre image, votre voix et vos missions. Vous gardez le dernier mot sur chaque production.", Modifier.padding(top = 9.dp), color = Color.White, lineHeight = 19.sp)
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text("$readiness%", color = Color.White, fontWeight = FontWeight.Black) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(if (profile.isRenderReady) "CAPTURES SÉCURISÉES" else "CONFIGURATION EN COURS", color = Color.White, fontWeight = FontWeight.Black)
                                Text(if (profile.isRenderReady) "Portrait · voix · mouvements prêts" else profile.nextRequiredCapture, color = Color.White, fontSize = 10.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .16f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE7F5FF)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, null, tint = WhappyBlue) }
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text("Centre de contrôle du Jumeau", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("Autorisation révocable à tout moment", color = WhappyMuted, fontSize = 10.sp)
                            }
                            Switch(
                                checked = profile.identityConsent,
                                onCheckedChange = { enabled ->
                                    if (preview) localProfile = localProfile.copy(identityConsent = enabled, voiceConsent = enabled, movementConsent = enabled)
                                    else onSaveConsent(enabled)
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(
                                "Identité" to profile.identityConsent,
                                "Voix" to profile.voiceUrl.isNotBlank(),
                                "Mouvements" to profile.movementUrl.isNotBlank(),
                            ).forEach { status ->
                                Text(
                                    if (status.second) "✓ ${status.first}" else "○ ${status.first}",
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (status.second) Color(0xFFE7F5FF) else WhappySurface).padding(vertical = 9.dp),
                                    color = if (status.second) WhappyBlue else WhappyMuted,
                                    textAlign = TextAlign.Center,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text("Validation humaine obligatoire · label IA permanent · aucune publication automatique sans votre accord", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WhappyStudioSection.entries.forEach { item ->
                        OutlinedButton(
                            onClick = { section = item },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (section == item) Color.White else Color.White, contentColor = if (section == item) WhappyBlue else WhappyMuted),
                        ) { Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            when (section) {
                WhappyStudioSection.MOTION -> {
                    item { StudioTitle("MOTION CORE 2.0", "Donnez-lui votre langage corporel", "Composez une séquence de gestes pour les ventes, directs et réponses vidéo.") }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                UserAvatar(state.accountPhotoUrl, userName, 110.dp, shape = CircleShape)
                                Text("JUMEAU NUMÉRIQUE DE ${userName.uppercase()}", Modifier.padding(top = 13.dp), color = Color.White, fontWeight = FontWeight.Black)
                                Text("Créé avec mon Jumeau numérique IA", Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
                            }
                        }
                    }
                    item {
                        val gestures = listOf("welcome" to "Accueil", "explain" to "Expliquer", "point" to "Pointer", "show" to "Présenter", "wave" to "Saluer", "walk" to "Déplacement")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            gestures.forEach { gesture ->
                                OutlinedButton(
                                    onClick = { selectedGestures = if (gesture.first in selectedGestures) selectedGestures - gesture.first else selectedGestures + gesture.first },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (gesture.first in selectedGestures) Color.White else Color.White),
                                ) { Text(if (gesture.first in selectedGestures) "✓ ${gesture.second}" else "+ ${gesture.second}") }
                            }
                        }
                    }
                }

                WhappyStudioSection.IDENTITY -> {
                    item { StudioTitle("01 / IDENTITÉ SOUVERAINE", "Apprenez-lui votre visage et vos mouvements", "Enregistrez uniquement votre propre identité. Les autorisations peuvent être révoquées.") }
                    item {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Je suis la personne enregistrée", fontWeight = FontWeight.Bold, color = WhappyDark)
                                    Text("J’autorise mon image, ma voix et mes mouvements pour mon WAPI.", Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp)
                                }
                                Switch(checked = profile.identityConsent, onCheckedChange = { consent -> if (preview) localProfile = localProfile.copy(identityConsent = consent, voiceConsent = consent, movementConsent = consent) else onSaveConsent(consent) })
                            }
                        }
                    }
                    item {
                        Surface(Modifier.fillMaxWidth(), color = if (profile.isRenderReady) WhappyBlue.copy(alpha = .08f) else Color(0xFFFFF7E8), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (profile.isRenderReady) Icons.Rounded.Verified else Icons.Rounded.Lock, null, tint = WhappyBlue)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(if (profile.isRenderReady) "Identité prête pour la production" else "Étape suivante", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(profile.nextRequiredCapture, color = WhappyMuted, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                    item {
                        CaptureAssetCard("Scan facial vidéo", "Regard, sourire et expressions · 15 à 30 secondes", profile.videoUrl.isNotBlank(), Icons.Rounded.Videocam, state.twinBusy || !profile.identityConsent) {
                            pendingVideoKind = "video"
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openVideoCamera("video") else cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                    item {
                        CaptureAssetCard("Signature de mouvements", "Gestes, posture et présentation · 30 à 60 secondes", profile.movementUrl.isNotBlank(), Icons.Rounded.Bolt, state.twinBusy || !profile.identityConsent) {
                            pendingVideoKind = "movement"
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openVideoCamera("movement") else cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                }

                WhappyStudioSection.STYLE -> {
                    item { StudioTitle("STYLE & PRÉSENCE", "Choisissez une tenue réelle", "Ajoutez une photo de votre tenue. Elle servira uniquement de référence visuelle à un rendu autorisé de votre Jumeau numérique.") }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                if (profile.outfitUrl.isNotBlank()) {
                                    UserAvatar(profile.outfitUrl, "Tenue de $userName", 104.dp, Modifier.align(Alignment.CenterHorizontally))
                                    Text("Tenue de référence enregistrée", Modifier.align(Alignment.CenterHorizontally), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Box(Modifier.align(Alignment.CenterHorizontally).size(104.dp).clip(RoundedCornerShape(28.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Photo, null, tint = WhappyBlue, modifier = Modifier.size(38.dp)) }
                                }
                                Text("Utilisez une image de votre propre tenue, nette et prise avec votre accord. La tenue n’est jamais appliquée à l’image d’une autre personne.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                                Button(enabled = profile.identityConsent && !state.twinBusy, onClick = { outfitPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Rounded.Photo, null); Text(if (profile.outfitUrl.isBlank()) "  Choisir depuis la galerie" else "  Changer de tenue") }
                            }
                        }
                    }
                }

                WhappyStudioSection.VOICE -> {
                    item { StudioTitle("02 / VOICE DNA", "Votre ton, même quand vous travaillez ailleurs", "L’échantillon prépare votre empreinte. Le rendu neuronal nécessite encore un fournisseur de synthèse vocale autorisée.") }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(20.dp)) {
                                Box(Modifier.align(Alignment.CenterHorizontally).size(100.dp).clip(CircleShape).background(if (recordingVoice) Color.White else Color.White), contentAlignment = Alignment.Center) {
                                    Icon(if (recordingVoice) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = if (recordingVoice) WhappyBlue else WhappyBlue, modifier = Modifier.size(42.dp))
                                }
                                Text("« Bonjour, je suis $userName. Cette voix est la mienne et je contrôle son utilisation par mon Jumeau numérique WAPI. »", Modifier.padding(top = 18.dp), color = WhappyInk, lineHeight = 21.sp)
                                Button(
                                    enabled = profile.identityConsent && !state.twinBusy,
                                    onClick = {
                                        if (recordingVoice) stopVoice(true)
                                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
                                ) { Text(if (recordingVoice) "Terminer et sécuriser" else if (profile.voiceUrl.isNotBlank()) "Réenregistrer ma voix" else "Enregistrer mon empreinte vocale") }
                                if (recordingVoice) Text("Enregistrement en cours depuis ${recordingSeconds}s", Modifier.padding(top = 8.dp), color = WhappyBlue, fontSize = 11.sp)
                                if (profile.voiceUrl.isNotBlank()) Text("✓ Échantillon vocal sécurisé", Modifier.padding(top = 8.dp), color = WhappyBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                WhappyStudioSection.MISSIONS -> {
                    item { StudioTitle("03 / ORCHESTRATEUR", "Choisissez une mission pour votre WAPI", "Chaque action produit un brouillon ou demande votre validation avant publication.") }
                    val presets = listOf(
                        listOf("Vendeur 24/7", "incoming-message", "inbox", "reply-video", "Prépare une réponse vidéo aux questions clients."),
                        listOf("Annonce en mouvement", "new-listing", "market", "prepare-video", "Transforme chaque annonce en présentation."),
                        listOf("Relance panier", "cart-abandoned", "inbox", "notify-owner", "Prépare une relance et demande votre validation."),
                    )
                    items(presets) { preset ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SmartToy, null, tint = WhappyBlue)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(preset[0], fontWeight = FontWeight.Bold, color = WhappyDark); Text(preset[4], color = WhappyMuted, fontSize = 11.sp) }
                                Button(enabled = profile.identityConsent && profile.voiceConsent && profile.movementConsent && !state.twinBusy, onClick = {
                                    if (preview) localAutomations = localAutomations + WhappyTwinAutomation("local-${System.currentTimeMillis()}", preset[0], preset[1], preset[2], preset[3], true)
                                    else onCreateAutomation(preset[0], preset[1], preset[2], preset[3], script)
                                }) { Text("Activer") }
                            }
                        }
                    }
                    if (automations.isNotEmpty()) item { Text("Missions du WAPI", fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                    items(automations, key = { it.id }) { automation ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(automation.name, fontWeight = FontWeight.Bold); Text("${automation.trigger} → ${automation.channel}", color = WhappyMuted, fontSize = 10.sp) }
                                Switch(checked = automation.enabled, onCheckedChange = { enabled -> if (preview) localAutomations = localAutomations.map { if (it.id == automation.id) it.copy(enabled = enabled) else it } else onToggleAutomation(automation.id, enabled) })
                                IconButton(onClick = { if (preview) localAutomations = localAutomations.filterNot { it.id == automation.id } else onDeleteAutomation(automation.id) }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = WhappyBlue) }
                            }
                        }
                    }
                }

                WhappyStudioSection.PRODUCE -> {
                    item { StudioTitle("04 / DIRECTOR", "Écrivez, chorégraphiez, préparez", "La file de production conserve le scénario, la langue, les gestes et la divulgation IA.") }
                    item {
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp)) {
                                if (!profile.isRenderReady) {
                                    Surface(Modifier.fillMaxWidth(), color = Color(0xFFFFF7E8), shape = RoundedCornerShape(14.dp)) {
                                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Lock, null, tint = WhappyBlue, modifier = Modifier.size(18.dp))
                                            Text(profile.nextRequiredCapture, Modifier.padding(start = 8.dp), color = WhappyDark, fontSize = 10.sp, lineHeight = 14.sp)
                                        }
                                    }
                                }
                                OutlinedTextField(script, { script = it.take(4_000) }, Modifier.fillMaxWidth(), label = { Text("Scénario") }, minLines = 5)
                                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("fr-FR" to "Français", "ln-CD" to "Lingala", "en-US" to "Anglais").forEach { option ->
                                        OutlinedButton(onClick = { language = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (language == option.first) Color.White else Color.Transparent)) { Text(option.second, fontSize = 10.sp) }
                                    }
                                }
                                OutlinedButton(onClick = {
                                    tts.language = when (language) { "ln-CD" -> Locale("ln", "CD"); "en-US" -> Locale.US; else -> Locale.FRANCE }
                                    tts.speak(script, TextToSpeech.QUEUE_FLUSH, null, "whappy-preview")
                                }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("▶ Aperçu avec voix système") }
                                Button(
                                    enabled = profile.isRenderReady && selectedGestures.isNotEmpty() && script.isNotBlank() && !state.twinBusy,
                                    onClick = {
                                        if (preview) localRenders = listOf(WhappyTwinRender("local-${System.currentTimeMillis()}", "Séquence commerciale WAPI", script, language, "prepared")) + localRenders
                                        else onCreateRender("Séquence commerciale WAPI", script, language, selectedGestures)
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                                ) { Icon(Icons.Rounded.Movie, null); Text("Préparer le rendu sécurisé", Modifier.padding(start = 7.dp)) }
                                Text("Label permanent : Créé avec le Jumeau numérique IA de son propriétaire. La génération faciale et vocale est exécutée uniquement par un moteur serveur autorisé, jamais en secret sur le téléphone.", Modifier.padding(top = 9.dp), color = WhappyMuted, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                    if (renders.isNotEmpty()) item { Text("Productions préparées", fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                    items(renders, key = { it.id }) { render ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Movie, null, tint = WhappyBlue)
                                Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(render.title, fontWeight = FontWeight.Bold); Text("${render.language} · ${if (render.status == "prepared") "Prêt pour le moteur sécurisé" else render.status}", color = WhappyMuted, fontSize = 10.sp) }
                                Text("IA", color = WhappyBlue, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTitle(kicker: String, title: String, body: String) {
    Column {
        Text(kicker, color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(title, Modifier.padding(top = 5.dp), color = WhappyDark, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
        Text(body, Modifier.padding(top = 6.dp), color = WhappyMuted, lineHeight = 19.sp)
    }
}

@Composable
private fun CaptureAssetCard(
    title: String,
    body: String,
    complete: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    disabled: Boolean,
    onCapture: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(if (complete) Icons.Rounded.Verified else icon, null, tint = WhappyBlue) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Bold, color = WhappyDark); Text(body, Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp) }
            Button(enabled = !disabled, onClick = onCapture) { Text(if (complete) "Refaire" else "Capturer") }
        }
    }
}

@Composable
private fun SpaceCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, width: Dp, onClick: () -> Unit) {
    Card(Modifier.width(width).height(104.dp).clickable(onClick = onClick), shape = RoundedCornerShape(WapiMobile.compactRadius), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = WhappyBlue)
            Spacer(Modifier.weight(1f))
            Text(title, fontWeight = FontWeight.Bold, color = WhappyDark)
            Text(subtitle, fontSize = 11.sp, color = WhappyMuted)
        }
    }
}

@Composable
private fun MomentCard(author: String, badge: String, title: String, body: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text(initials(author), color = WhappyDark, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(author, fontWeight = FontWeight.Bold); Icon(Icons.Rounded.Verified, null, tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }
                    Text(badge, color = WapiVerifiedGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(title, Modifier.padding(top = 18.dp), color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(body, Modifier.padding(top = 8.dp), color = WhappyMuted, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun CallsScreen(conversations: List<WhappyConversation>, onOpenConversation: (WhappyConversation) -> Unit) {
    val calls = LocalWhappyCalls.current
    val context = LocalContext.current
    var recentCalls by remember { mutableStateOf(WapiCallHistory.entries(context)) }
    val callVisible = calls?.state?.visible == true
    LaunchedEffect(callVisible) {
        if (!callVisible) recentCalls = WapiCallHistory.entries(context)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                Column(Modifier.padding(23.dp)) {
                    Text("WAPI CALLS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("Appelez vos contacts\nen un seul geste.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                    Text("Vos contacts vérifiés et vos conversations restent réunis dans Wapi.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        CallMetric("Contacts", conversations.size.toString(), Modifier.weight(1f))
                        CallMetric("Audio + vidéo", "Wapi", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFFEFF8FE), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .14f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PersonAdd, null, tint = WhappyBlue)
                    Column(Modifier.padding(start = 10.dp)) { Text("Vos contacts WAPI", color = WhappyDark, fontWeight = FontWeight.Black); Text("Ouvrez un contact pour écrire, appeler en audio ou lancer la vidéo — sans saisir de numéro.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 15.sp) }
                }
            }
        }
        item { Text("Appels récents", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp)) }
        if (recentCalls.isEmpty()) item { Text("Aucun appel lancé depuis WAPI pour le moment.", color = WhappyMuted, modifier = Modifier.padding(vertical = 6.dp)) }
        items(recentCalls.take(8), key = { it }) { raw ->
            val parts = raw.split("|", limit = 7)
            val timestamp = parts.firstOrNull()?.toLongOrNull() ?: 0L
            val name = parts.getOrElse(1) { "Contact" }
            val recentPhone = parts.getOrElse(2) { "" }
            val video = parts.getOrElse(3) { "audio" } == "video"
            val direction = parts.getOrElse(4) { "outgoing" }
            val recentUserId = parts.getOrElse(5) { "" }
            val recentPhoto = parts.getOrElse(6) { "" }.replace("%7C", "|")
            val matchedConversation = conversations.firstOrNull { conversation -> recentUserId.isNotBlank() && conversation.peer.uid == recentUserId }
                ?: conversations.firstOrNull { conversation -> recentPhone.isNotBlank() && conversation.peer.phoneNumber == recentPhone }
                ?: conversations.filter { conversation -> conversation.peer.displayName.equals(name, ignoreCase = true) }.singleOrNull()
            val resolvedUserId = recentUserId.ifBlank { matchedConversation?.peer?.uid.orEmpty() }
            val resolvedPhone = recentPhone.ifBlank { matchedConversation?.peer?.phoneNumber.orEmpty() }
            val resolvedPhoto = recentPhoto.ifBlank { matchedConversation?.peer?.photoUrl.orEmpty() }
            val missed = direction == "missed"
            val callTint = if (missed) Color(0xFFE05252) else WhappyBlue
            val callLabel = when (direction) {
                "missed" -> "Appel manqué"
                "incoming" -> "Appel reçu"
                else -> "Appel émis"
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(resolvedPhoto, name, 42.dp, shape = CircleShape)
                        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                            Text(name, color = WhappyDark, fontWeight = FontWeight.Black)
                            Text("$callLabel · ${if (video) "Vidéo" else "Audio"}", color = callTint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(formatCallMoment(timestamp), color = WhappyMuted, fontSize = 10.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        val reachable = calls != null && (resolvedUserId.isNotBlank() || resolvedPhone.isNotBlank())
                        WapiCallAction(Icons.Rounded.Phone, "Audio", reachable, Modifier.weight(1f)) {
                            if (resolvedUserId.isNotBlank()) calls?.start(WhappyMember(resolvedUserId, name, resolvedPhone, resolvedPhoto), false) else calls?.startByPhone(resolvedPhone, false)
                        }
                        WapiCallAction(Icons.Rounded.Videocam, "Vidéo", reachable, Modifier.weight(1f), emphasized = true) {
                            if (resolvedUserId.isNotBlank()) calls?.start(WhappyMember(resolvedUserId, name, resolvedPhone, resolvedPhoto), true) else calls?.startByPhone(resolvedPhone, true)
                        }
                    }
                }
            }
        }
        item { Text("Tous les contacts", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 7.dp)) }
        if (conversations.isEmpty()) {
            item { EmptyState("Aucun contact", "Ajoutez un contact dans Messages pour pouvoir l’appeler.") }
        } else {
            items(conversations, key = { "call-${it.id}" }) { conversation ->
                // A WAPI-to-WAPI call is addressed by the account uid; users who
                // hide their phone number must still be reachable in audio/video.
                val callable = conversation.peer.uid.isNotBlank()
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 54.dp, shape = RoundedCornerShape(14.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(conversation.peer.displayName, color = WhappyDark, fontWeight = FontWeight.Black)
                                Text(if (conversation.isGroup) "${conversation.memberCount} participants" else conversation.peer.phoneNumber.ifBlank { "Numéro privé" }, color = WhappyMuted, fontSize = 11.sp)
                                Text(if (conversation.isGroup) "Audio et vidéo depuis le groupe" else "Disponible pour les appels WAPI", color = WhappyMuted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onOpenConversation(conversation) }) { Icon(Icons.Rounded.ChatBubble, "Écrire à ${conversation.peer.displayName}", tint = WhappyBlue) }
                        }
                        if (conversation.isGroup) {
                            WapiCallAction(Icons.Rounded.Groups, "Ouvrir les appels du groupe", true, Modifier.fillMaxWidth(), emphasized = true) { onOpenConversation(conversation) }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                WapiCallAction(Icons.Rounded.Phone, "Appel audio", callable && calls != null, Modifier.weight(1f)) {
                                    calls?.start(conversation.peer, false)
                                }
                                WapiCallAction(Icons.Rounded.Videocam, "Appel vidéo", callable && calls != null, Modifier.weight(1f), emphasized = true) {
                                    calls?.start(conversation.peer, true)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("Les appels audio et vidéo individuels utilisent WAPI entre comptes inscrits. Un numéro absent de WAPI n’est jamais appelé via votre opérateur sans votre accord.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) }
    }
}

@Composable
private fun CallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = .08f)).padding(12.dp)) {
        Text(label.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WapiCallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(15.dp)).clickable(enabled = enabled, onClick = onClick),
        color = when {
            !enabled -> Color(0xFFF0F2F4)
            emphasized -> WhappyBlue
            else -> Color(0xFFEAF5FD)
        },
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled && emphasized) Color.White else if (enabled) WhappyBlue else WhappyMuted, modifier = Modifier.size(19.dp))
            Text(label, Modifier.padding(start = 7.dp), color = if (enabled && emphasized) Color.White else if (enabled) WhappyDark else WhappyMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MessagesScreen(
    conversations: List<WhappyConversation>,
    loading: Boolean,
    preview: Boolean,
    contactBusy: Boolean,
    contacts: List<WhappyContact>,
    contactSearchResult: WhappyMember?,
    contactSearchPhone: String,
    contactSearchMessage: String?,
    storyStatuses: List<WhappyStatus>,
    businessResults: List<WhappyBusinessPage>,
    businessSearchBusy: Boolean,
    channels: List<WhappyChannel>,
    currentUserId: String,
    channelBusy: Boolean,
    initialQuery: String,
    initialSection: Int,
    onSearchContact: (String) -> Unit,
    onAddSearchedContact: () -> Unit,
    onClearContactSearch: () -> Unit,
    onOpenContact: (WhappyContact) -> Unit,
    onSearchBusinesses: (String) -> Unit,
    onContactBusiness: (WhappyBusinessPage) -> Unit,
    onOpen: (WhappyConversation) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
    onCreateGroup: (String, List<WhappyMember>, Uri?, String) -> Unit,
    onSubscribeChannel: (String, Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenStory: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var searchingBusiness by remember { mutableStateOf(false) }
    var messageSection by rememberSaveable { mutableIntStateOf(if (initialSection == 1) 1 else if (initialSection == 2) 2 else 0) }
    var creatingChannel by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupMembers by remember { mutableStateOf(emptySet<String>()) }
    var selectedContactProfile by remember { mutableStateOf<WhappyContact?>(null) }
    var groupPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var channelName by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var channelCategory by remember { mutableStateOf("Communauté") }
    var conversationSearch by rememberSaveable { mutableStateOf(initialQuery) }
    var phoneField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val phone = phoneField.text
    var contactCountry by rememberSaveable { mutableStateOf("+242") }
    var contactCountryMenu by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scannerOpen by remember { mutableStateOf(false) }
    var imageScanInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val normalizedContactPhone = PhoneNumberFormatter.normalize(contactCountry, phone)
        ?: PhoneNumberFormatter.normalizeAny(phone)
    val isPhoneComplete = normalizedContactPhone != null
    val filteredConversations = conversations.filter { SearchNormalizer.matches(conversationSearch, it.peer.displayName, it.lastMessage, it.peer.phoneNumber) }
    val groupConversations = conversations.filter { it.isGroup }
    val directConversations = conversations.filterNot { it.isGroup }
    val availableGroupContacts = (contacts + directConversations.map { WhappyContact(it.peer, it.updatedAt) })
        .distinctBy { it.member.uid }
        .filter { it.member.uid != currentUserId }
        .sortedBy { SearchNormalizer.normalize(it.member.displayName) }
    val contactSuccess = contactSearchMessage != null && (
        contactSearchMessage.startsWith("Contact ajouté") ||
            contactSearchMessage.startsWith("Ce contact existe déjà")
    )
    val resetContactSearch: () -> Unit = { if (!preview) onClearContactSearch() }

    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) { conversationSearch = initialQuery; messageSection = 2 } }
    LaunchedEffect(initialSection) { if (initialSection == 1 || initialSection == 0 || initialSection == 2) messageSection = initialSection }
    LaunchedEffect(contactSearchPhone) {
        if (contactSearchPhone.isNotBlank()) {
            val parts = phoneEditorParts(contactSearchPhone, "+242")
            contactCountry = parts.countryCode
            phoneField = TextFieldValue(parts.nationalNumber, TextRange(parts.nationalNumber.length))
            adding = true
        }
    }
    LaunchedEffect(contactSuccess, contactBusy) {
        if (contactSuccess && !contactBusy && !preview) {
            delay(820)
            if (adding) {
                adding = false
                resetContactSearch()
            }
        }
    }

    fun applyContactInputValue(value: String, autoSearch: Boolean = false) {
        val candidate = when (val link = WhappyLink.parse(value)) {
            is WhappyLink.Contact -> link.phone
            else -> PhoneNumberFormatter.normalize(contactCountry, value)
                ?: PhoneNumberFormatter.normalizeAny(value)
                ?: PhoneNumberFormatter.lookupCandidates(value, contactCountry).firstOrNull()
                ?: PhoneNumberFormatter.lookupCandidates(value).firstOrNull()
        }
        if (candidate != null) {
            val parts = phoneEditorParts(candidate, contactCountry)
            contactCountry = parts.countryCode
            phoneField = TextFieldValue(parts.nationalNumber, TextRange(parts.nationalNumber.length))
            scanError = null
            if (autoSearch && !preview) onSearchContact(candidate)
        } else {
            val parts = phoneEditorParts(value, contactCountry)
            contactCountry = parts.countryCode
            phoneField = TextFieldValue(parts.nationalNumber, TextRange(parts.nationalNumber.length))
            if (autoSearch && value.isNotBlank() && !preview) {
                scanError = "Ce format n’est pas reconnu. Utilisez un numéro valide (ex : 06 12 34 56 78)."
            }
        }
        if (!autoSearch) resetContactSearch()
    }

    fun startContactSearch(rawValue: String, markBusy: Boolean = true) {
        if (preview) return
        if (contactBusy) return
        val normalized = PhoneNumberFormatter.normalize(contactCountry, rawValue)
            ?: PhoneNumberFormatter.normalizeAny(rawValue)
            ?: PhoneNumberFormatter.lookupCandidates(rawValue, contactCountry).firstOrNull()
            ?: PhoneNumberFormatter.lookupCandidates(rawValue).firstOrNull()
        if (normalized == null) {
            scanError = "Ce numéro est incomplet ou invalide. Vérifiez le format puis réessayez."
            return
        }
        if (markBusy && !adding) {
            adding = true
            resetContactSearch()
        }
        scanError = null
        onSearchContact(normalized)
    }

    fun processScannedContact(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            scanError = "Le QR ne contient pas de données valides."
            return
        }
        when (val link = WhappyLink.parse(trimmed)) {
            is WhappyLink.Contact -> {
                applyContactInputValue(link.phone, autoSearch = true)
            }
            is WhappyLink.Channel -> {
                if (preview) {
                    scanError = "Le lien de chaîne sera accessible après connexion."
                } else {
                    adding = false
                    onHandleWhappyLink(trimmed)
                }
            }
            is WhappyLink.Search -> {
                if (preview) {
                    scanError = "Le lien de recherche sera accessible après connexion."
                } else {
                    adding = false
                    onHandleWhappyLink(trimmed)
                }
            }
            is WhappyLink.Live -> {
                if (preview) {
                    scanError = "Le lien de direct sera accessible après connexion."
                } else {
                    adding = false
                    onHandleWhappyLink(trimmed)
                }
            }
            is WhappyLink.GroupCall -> {
                if (preview) {
                    scanError = "Le lien d’appel sera accessible après connexion."
                } else {
                    adding = false
                    onHandleWhappyLink(trimmed)
                }
            }
            null -> {
                if (trimmed.isNotBlank()) {
                    startContactSearch(trimmed, markBusy = true)
                } else {
                    scanError = "Ce QR n’est pas un code WAPI valide."
                }
            }
        }
    }

    val qrImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        imageScanInProgress = true
        scope.launch {
            val decoded = decodeQrFromImage(context, uri)
            imageScanInProgress = false
            if (decoded == null) {
                scanError = "Aucun QR lisible trouvé dans cette image. Choisissez une capture nette du code WAPI."
            } else {
                processScannedContact(decoded)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scannerOpen = true
        else scanError = "Autorisez la caméra pour numériser un code WAPI."
    }

    fun openWapiScanner() {
        if (preview) {
            scanError = "La numérisation est disponible dans l’application connectée."
            return
        }
        scanError = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scannerOpen = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

        Column(Modifier.fillMaxSize().background(WapiCanvas)) {
        Row(Modifier.padding(horizontal = WapiMobile.screen, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when (messageSection) { 1 -> "Contacts"; 2 -> "Chaînes"; else -> "Messages" }, style = MaterialTheme.typography.headlineLarge)
                Text(when (messageSection) { 1 -> "Vos personnes sur WAPI"; 2 -> "Les publications que vous choisissez"; else -> "Vos échanges, sans distraction" }, color = WhappyMuted, fontSize = 12.sp)
            }
            FilledIconButton(
                onClick = { when (messageSection) { 2 -> creatingChannel = true; 0 -> creatingGroup = true; else -> { phoneField = TextFieldValue(""); resetContactSearch(); adding = true } } },
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
            ) {
                Icon(Icons.Rounded.Add, when (messageSection) { 2 -> "Créer une chaîne"; 0 -> "Créer un groupe"; else -> "Ajouter un contact" }, tint = Color.White)
            }
        }
        Row(Modifier.padding(horizontal = WapiMobile.screen).clip(RoundedCornerShape(15.dp)).background(WhappySurface).padding(4.dp)) {
            listOf("Discussions", "Contacts", "Chaînes").forEachIndexed { index, label ->
                TextButton(
                    onClick = { messageSection = index; conversationSearch = "" },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = if (messageSection == index) Color.White else Color.Transparent, contentColor = if (messageSection == index) WhappyDark else WhappyMuted),
                ) { Text(label, fontSize = 11.sp, fontWeight = if (messageSection == index) FontWeight.Black else FontWeight.SemiBold) }
            }
        }
        OutlinedTextField(
            conversationSearch,
            { conversationSearch = it.take(120) },
            Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 10.dp).height(52.dp),
            placeholder = { Text(when (messageSection) { 1 -> "Rechercher un contact"; 2 -> "Rechercher une chaîne"; else -> "Rechercher dans les messages" }, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = WhappyMuted, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WhappyBlue.copy(alpha = .45f),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
        if (messageSection == 2) {
            ChannelDirectory(channels, currentUserId, conversationSearch, onOpenChannel, onSubscribeChannel)
        } else if (messageSection == 1) {
            val conversationContacts = conversations.map { WhappyContact(it.peer, it.updatedAt) }
            val visibleContacts = (contacts + conversationContacts).distinctBy { it.member.uid }.filter { SearchNormalizer.matches(conversationSearch, it.member.displayName, it.member.phoneNumber) }.sortedBy { SearchNormalizer.normalize(it.member.displayName) }
            if (loading && visibleContacts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
            else if (visibleContacts.isEmpty()) {
                Card(Modifier.padding(18.dp).fillMaxWidth().clickable { phoneField = TextFieldValue(""); resetContactSearch(); adding = true }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = WhappyBlue) }
                        Text("Ajoutez votre premier contact", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Avec son numéro ou son code QR WAPI. Vous le retrouverez ici à tout moment.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                        Text("AJOUTER UN CONTACT", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item { Text("CONTACTS WAPI", Modifier.padding(start = 5.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                items(visibleContacts, key = { it.member.uid }) { contact ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !contactBusy) { selectedContactProfile = contact }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            val contactHasUnseenStory = storyStatuses.any { it.authorId == contact.member.uid && !it.viewedByCurrentUser }
                            StoryRingAvatar(
                                photoUrl = contact.member.photoUrl,
                                name = contact.member.displayName,
                                size = 48.dp,
                                hasUnseenStory = contactHasUnseenStory,
                                onClick = { if (contactHasUnseenStory) onOpenStory(contact.member.uid) else selectedContactProfile = contact },
                                shape = RoundedCornerShape(14.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(contact.member.displayName, fontWeight = FontWeight.Black, color = WhappyDark)
                                Text(contact.member.phoneNumber.ifBlank { "Contact WAPI" }, color = WhappyMuted, fontSize = 11.sp)
                            }
                            Text("Profil ›", color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (loading && conversations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else if (filteredConversations.isEmpty()) EmptyState(if (conversationSearch.isBlank()) "Aucune conversation" else "Aucun résultat", if (conversationSearch.isBlank()) "Ouvrez l’onglet Contacts pour ajouter une personne sur WAPI." else "Essayez un autre nom ou un mot du dernier message.")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = WapiMobile.screen, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (conversationSearch.isBlank()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Conversations", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("${directConversations.size} privées · ${groupConversations.size} groupes", color = WhappyMuted, fontSize = 10.sp)
                        }
                        Surface(
                            modifier = Modifier.clip(CircleShape).clickable { creatingGroup = true },
                            color = WapiSoftBlue,
                            shape = CircleShape,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Groups, null, tint = WhappyBlue, modifier = Modifier.size(17.dp))
                                Text("  Nouveau groupe", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            items(filteredConversations, key = { it.id }) { conversation ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(conversation) },
                    color = if (conversation.unread) WapiUnreadSurface else Color.White,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = if (conversation.unread) 1.dp else 0.dp,
                ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isGroup && conversation.peer.photoUrl.isBlank()) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, null, tint = Color.White)
                        }
            } else {
                val peerHasUnseenStory = storyStatuses.any { it.authorId == conversation.peer.uid && !it.viewedByCurrentUser }
                StoryRingAvatar(
                    photoUrl = conversation.peer.photoUrl,
                    name = conversation.peer.displayName,
                    size = 52.dp,
                    hasUnseenStory = peerHasUnseenStory,
                    onClick = { if (peerHasUnseenStory) onOpenStory(conversation.peer.uid) else onOpen(conversation) },
                    shape = RoundedCornerShape(14.dp),
                )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(conversation.peer.displayName, Modifier.weight(1f, fill = false), fontWeight = if (conversation.unread) FontWeight.Black else FontWeight.Bold, color = WhappyDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (conversation.profileType == "business") {
                                Spacer(Modifier.width(6.dp))
                                Surface(color = WhappyBlue.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) { Text("BUSINESS", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = WhappyBlue, fontSize = 7.sp, fontWeight = FontWeight.Black) }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(formatConversationMoment(conversation.updatedAt), color = if (conversation.unread) WhappyBlue else WhappyMuted, fontSize = 10.sp, fontWeight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal)
                        }
                        Text(
                            if (conversation.isGroup) "${conversation.memberCount} membres · ${conversation.lastMessage}" else if (conversation.profileType == "business") "Messagerie Business · ${conversation.lastMessage}" else conversation.lastMessage,
                            Modifier.padding(top = 4.dp),
                            color = if (conversation.unread) WhappyInk else WhappyMuted,
                            fontSize = 12.sp,
                            fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (conversation.unread) Box(Modifier.padding(start = 8.dp).size(9.dp).clip(CircleShape).background(WhappyBlue))
                }
                }
            }
        }
        if (adding) AlertDialog(
            onDismissRequest = { if (!contactBusy) { adding = false; resetContactSearch() } },
            title = { Text(if (contactSearchResult == null) "Ajouter sur WAPI" else "Compte WAPI trouvé") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PersonAdd, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text("Ajouter une personne", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    Text("Retrouvez-la et ouvrez une discussion instantanément.", color = WhappyMuted, fontSize = 10.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("1  Trouver", "2  Ouvrir", "3  Écrire").forEach { step ->
                                    Text(step, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(vertical = 7.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Text(if (contactSearchResult == null) "Saisissez le numéro ou numérisez le code personnel WAPI. Si le compte existe, la discussion s’ouvre directement." else "Compte WAPI trouvé : la discussion peut maintenant être ouverte.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            val selectedCountry = authCountries.firstOrNull { it.code == contactCountry }
                            OutlinedButton(
                                onClick = { contactCountryMenu = true },
                                modifier = Modifier.width(116.dp).height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("${selectedCountry?.flag.orEmpty()} $contactCountry", fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                Text(" ▾", color = WhappyMuted, fontSize = 10.sp)
                            }
                            DropdownMenu(contactCountryMenu, { contactCountryMenu = false }) {
                                authCountries.forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text("${country.flag} ${country.name}  ${country.code}") },
                                        onClick = {
                                            contactCountry = country.code
                                            contactCountryMenu = false
                                            scanError = null
                                            resetContactSearch()
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = phoneField,
                            onValueChange = { value ->
                                phoneField = stablePhoneFieldValue(value)
                                scanError = null
                                resetContactSearch()
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Téléphone") },
                            placeholder = { Text("06 54 65 80 8") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (!contactBusy && !preview) startContactSearch(phone) }),
                            visualTransformation = PhoneNumberSpacingTransformation,
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                        )
                    }
                    TextButton(
                        enabled = !contactBusy,
                        modifier = Modifier.padding(top = 1.dp),
                        onClick = {
                            val clipboardText = runCatching {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .primaryClip
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    ?.trim()
                                    .orEmpty()
                            }.getOrDefault("")
                            if (clipboardText.isBlank()) {
                                scanError = "Le presse-papiers est vide. Saisissez le numéro puis recherchez."
                            } else {
                                applyContactInputValue(clipboardText, autoSearch = true)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Text("  Coller le code / numéro")
                    }
                    if (phone.isNotBlank() && !isPhoneComplete) Text("Vérifiez l’indicatif et la longueur du numéro. Vous pouvez aussi coller un numéro international complet.", color = WhappyMuted, fontSize = 11.sp)
                    if (normalizedContactPhone != null) Text("Numéro reconnu : $normalizedContactPhone", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(enabled = !contactBusy && !scannerOpen && !imageScanInProgress, onClick = ::openWapiScanner, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(18.dp))
                            Text("  Numériser", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(enabled = !contactBusy && !scannerOpen && !imageScanInProgress, onClick = { if (preview) scanError = "L’import est disponible dans l’application connectée" else qrImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Photo, null, modifier = Modifier.size(18.dp))
                            Text(if (imageScanInProgress) "  Lecture…" else "  Image QR", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (contactBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(18.dp), color = WhappyBlue, strokeWidth = 2.dp); Text(if (contactSearchResult == null) "Recherche du compte WAPI…" else "Ajout du contact…", color = WhappyMuted, fontSize = 12.sp) }
                    if (contactSearchResult != null) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isFounderContact = WhappyIdentity.isFounder(contactSearchResult.phoneNumber)
                            UserAvatar(contactSearchResult.photoUrl, contactSearchResult.displayName, 44.dp, shape = CircleShape)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(contactSearchResult.displayName, color = WhappyDark, fontWeight = FontWeight.Black)
                                    if (contactSearchResult.verified || isFounderContact) Icon(
                                        Icons.Rounded.Verified,
                                        "Compte certifié",
                                        modifier = Modifier.padding(start = 4.dp).size(15.dp),
                                        tint = WapiVerifiedGray,
                                    )
                                }
                                Text(contactSearchResult.phoneNumber.ifBlank { contactSearchPhone }, color = WhappyMuted, fontSize = 11.sp)
                                if (isFounderContact) {
                                    Text(WhappyIdentity.founderBadgeLabel, color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else if (contactSearchResult.verified) {
                                    Text("Compte WAPI vérifié", color = WapiVerifiedGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Compte WAPI", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (contactSearchMessage != null) Text(
                        contactSearchMessage,
                        color = if (contactSearchMessage.startsWith("Contact ajouté") || contactSearchMessage.startsWith("Ce contact existe déjà"))
                            WhappyBlue else if (contactSearchResult == null) WhappyMuted else WhappyBlue,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    TextButton(onClick = { adding = false; resetContactSearch(); searchingBusiness = true }, modifier = Modifier.fillMaxWidth()) { Text("Trouver un Business à la place") }
                }
            },
            confirmButton = {
                if (contactSearchResult != null) Button(enabled = !contactBusy, onClick = { if (!preview) onAddSearchedContact() }) {
                    if (contactBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (preview) "Disponible après connexion" else "Ajouter et écrire")
                } else Button(enabled = !contactBusy, onClick = { if (!preview) startContactSearch(phone) }) {
                    if (contactBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (preview) "Disponible après connexion" else "Rechercher")
                }
            },
            dismissButton = { TextButton(enabled = !contactBusy, onClick = { adding = false; resetContactSearch() }) { Text("Annuler") } },
        )
        if (scannerOpen) WapiQrScannerDialog(
            onDismiss = { scannerOpen = false },
            onResult = { payload ->
                scannerOpen = false
                processScannedContact(payload)
            },
            onPickImage = {
                scannerOpen = false
                qrImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        selectedContactProfile?.let { contact ->
            val calls = LocalWhappyCalls.current
            AlertDialog(
                onDismissRequest = { selectedContactProfile = null },
                title = { Text(contact.member.displayName, fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        UserAvatar(contact.member.photoUrl, contact.member.displayName, 74.dp, shape = RoundedCornerShape(16.dp))
                        val isVerifiedProfile = contact.member.verified || WhappyIdentity.isFounder(contact.member.phoneNumber)
                        if (isVerifiedProfile) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.size(16.dp)); Text(if (WhappyIdentity.isFounder(contact.member.phoneNumber)) " ${WhappyIdentity.founderBadgeLabel}" else " Compte WAPI vérifié", color = WapiVerifiedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Text(contact.member.phoneNumber.ifBlank { "Numéro protégé" }, color = WhappyMuted, fontSize = 12.sp)
                        Text(if (contact.member.isOnline) "En ligne maintenant" else if (contact.member.lastSeenAt > 0L) formatLastSeen(contact.member.lastSeenAt) else "Dernière présence indisponible", color = WhappyMuted, fontSize = 11.sp)
                    }
                },
                confirmButton = { Button(enabled = !contactBusy, onClick = { selectedContactProfile = null; onOpenContact(contact) }) { Icon(Icons.Rounded.ChatBubble, null); Text(" Écrire") } },
                dismissButton = {
                    Row {
                        TextButton(enabled = calls != null && contact.member.phoneNumber.isNotBlank(), onClick = { calls?.start(contact.member, false) }) { Icon(Icons.Rounded.Phone, null); Text(" Appeler") }
                        TextButton(enabled = calls != null && contact.member.phoneNumber.isNotBlank(), onClick = { calls?.start(contact.member, true) }) { Icon(Icons.Rounded.Videocam, null); Text(" Vidéo") }
                    }
                },
            )
        }
        if (searchingBusiness) BusinessSearchDialog(
            results = businessResults,
            busy = businessSearchBusy,
            contactBusy = contactBusy,
            onSearch = onSearchBusinesses,
            onContact = { page ->
                searchingBusiness = false
                if (!preview) onContactBusiness(page)
            },
            onDismiss = { searchingBusiness = false },
        )
        if (scanError != null) AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Code WAPI") },
            text = { Text(scanError.orEmpty()) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text("Compris") } },
        )
        if (creatingGroup) CreateGroupDialog(
            groupName = groupName,
            contacts = availableGroupContacts,
            selectedIds = groupMembers,
            photoUri = groupPhotoUri,
            busy = channelBusy,
            onNameChange = { groupName = it.take(80) },
            onToggle = { id -> groupMembers = if (id in groupMembers) groupMembers - id else groupMembers + id },
            onPhotoChange = { groupPhotoUri = it },
            onDismiss = { creatingGroup = false; groupPhotoUri = null },
            onCreate = {
                val photo = groupPhotoUri
                onCreateGroup(
                    groupName.trim(),
                    availableGroupContacts
                        .map { it.member }
                        .filter { it.uid in groupMembers }
                        .distinctBy { it.uid },
                    photo,
                    photo?.let { context.contentResolver.getType(it) }.orEmpty().ifBlank { "image/jpeg" },
                )
            },
        )
        if (creatingChannel) AlertDialog(
            onDismissRequest = { if (!channelBusy) creatingChannel = false },
            title = { Text("Créer une chaîne", fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Une chaîne est un espace de diffusion public. Vous seul pourrez publier ; vos abonnés pourront réagir.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                OutlinedTextField(channelName, { channelName = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Nom de la chaîne") }, singleLine = true)
                OutlinedTextField(channelDescription, { channelDescription = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, supportingText = { Text("${channelDescription.length}/300") })
                Text("CATÉGORIE", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Communauté", "Actualités", "Créateurs", "Shopping", "Sport", "Tech").forEach { category -> TextButton(onClick = { channelCategory = category }, colors = ButtonDefaults.textButtonColors(containerColor = if (channelCategory == category) WhappyBlue else Color.White, contentColor = if (channelCategory == category) Color.White else WhappyDark)) { Text(category, fontSize = 11.sp) } } }
            } },
            confirmButton = { Button(enabled = !channelBusy && channelName.trim().length >= 3 && channelDescription.trim().length >= 10, onClick = { onCreateChannel(channelName, channelDescription, channelCategory); creatingChannel = false; channelName = ""; channelDescription = "" }) { if (channelBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp) else Text("Créer") } },
            dismissButton = { TextButton(enabled = !channelBusy, onClick = { creatingChannel = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun CreateGroupDialog(
    groupName: String,
    contacts: List<WhappyContact>,
    selectedIds: Set<String>,
    photoUri: Uri?,
    busy: Boolean,
    onNameChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onPhotoChange: (Uri?) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val members = contacts.distinctBy { it.member.uid }
    val ready = groupName.trim().length >= 2 && selectedIds.isNotEmpty()
    var photoToCrop by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoToCrop = uri
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 20.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (photoUri != null) UserAvatar(photoUri.toString(), groupName.ifBlank { "Groupe WAPI" }, 58.dp)
                        else Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, null, tint = Color.White)
                        }
                        IconButton(
                            enabled = !busy,
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White),
                        ) { Icon(Icons.Rounded.Photo, "Ajouter la photo du groupe", tint = WhappyBlue, modifier = Modifier.size(15.dp)) }
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text("Nouveau groupe", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Une conversation privée pour votre cercle", color = WhappyMuted, fontSize = 11.sp)
                    }
                    IconButton(enabled = !busy, onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = WhappyMuted) }
                }
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nom du groupe") },
                    placeholder = { Text("Ex. Équipe WAPI") },
                    supportingText = { Text("${groupName.length}/80") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Photo, null, modifier = Modifier.size(17.dp))
                        Text(if (photoUri == null) "  Ajouter une photo de groupe" else "  Changer la photo", fontWeight = FontWeight.Bold)
                    }
                    if (photoUri != null) TextButton(enabled = !busy, onClick = { onPhotoChange(null) }) { Text("Retirer", color = WhappyMuted) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MEMBRES", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("${selectedIds.size} sélectionné${if (selectedIds.size > 1) "s" else ""} · minimum 1", color = WhappyMuted, fontSize = 10.sp)
                    }
                    Box(Modifier.clip(CircleShape).background(WhappyBlue.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("${selectedIds.size}/64", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (members.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WhappyBlue.copy(alpha = .05f)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = WhappyBlue)
                        Text("Ajoutez d’abord des contacts WAPI", Modifier.padding(top = 8.dp), color = WhappyDark, fontWeight = FontWeight.Bold)
                        Text("Un contact minimum est nécessaire.", color = WhappyMuted, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(members, key = { "group-member-${it.member.uid}" }) { contact ->
                            val selected = contact.member.uid in selectedIds
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onToggle(contact.member.uid) },
                                shape = RoundedCornerShape(17.dp),
                                color = if (selected) WhappyBlue.copy(alpha = .08f) else Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) WhappyBlue else WhappyBlue.copy(alpha = .12f)),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(contact.member.photoUrl, contact.member.displayName, 42.dp, shape = RoundedCornerShape(11.dp))
                                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                        Text(contact.member.displayName, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(contact.member.phoneNumber, color = WhappyMuted, fontSize = 10.sp)
                                    }
                                    Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.PersonAdd, null, tint = if (selected) WhappyBlue else WhappyMuted)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("Annuler") }
                    Button(enabled = !busy && ready, onClick = onCreate, modifier = Modifier.weight(1.25f).height(50.dp), shape = RoundedCornerShape(15.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (ready) "Créer le groupe" else "1 membre requis", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    photoToCrop?.let { source ->
        WapiSquareCropDialog(
            source = source,
            title = "Recadrer la photo du groupe",
            onDismiss = { photoToCrop = null },
            onConfirm = { cropped ->
                onPhotoChange(cropped)
                photoToCrop = null
            },
        )
    }
}

@Composable
private fun ChannelDirectory(
    channels: List<WhappyChannel>,
    currentUserId: String,
    search: String,
    onOpen: (WhappyChannel) -> Unit,
    onSubscribe: (String, Boolean) -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf("Toutes") }
    val categories = remember(channels) { listOf("Toutes", "Suivies", "Mes chaînes") + channels.map { it.category.ifBlank { "Communauté" } }.distinct().sorted() }
    val visible = channels.filter { channel ->
        val categoryOk = when (selectedCategory) {
            "Toutes" -> true
            "Suivies" -> currentUserId in channel.memberIds
            "Mes chaînes" -> channel.ownerId == currentUserId
            else -> channel.category == selectedCategory
        }
        categoryOk && SearchNormalizer.matches(search, channel.name, channel.description, channel.category, channel.ownerName)
    }
        .sortedWith(compareByDescending<WhappyChannel> { currentUserId in it.memberIds }.thenByDescending { it.memberCount })
    if (visible.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                categories.forEach { category -> TextButton(onClick = { selectedCategory = category }, colors = ButtonDefaults.textButtonColors(containerColor = if (selectedCategory == category) WhappyBlue else Color.White, contentColor = if (selectedCategory == category) Color.White else WhappyDark), shape = RoundedCornerShape(14.dp)) { Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
            }
            EmptyState(if (search.isBlank()) "Aucune chaîne" else "Aucune chaîne trouvée", if (search.isBlank()) "Créez la première chaîne WAPI et commencez à publier." else "Essayez un nom, une catégorie ou un autre mot-clé.")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WhappyNavy).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = Color.White) }
                Column(Modifier.padding(start = 12.dp)) { Text("CHAÎNES WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Des publications utiles, sans bruit", color = Color.White, fontWeight = FontWeight.Black); Text("${visible.size}/${channels.size} chaîne${if (channels.size > 1) "s" else ""} · filtres rapides", color = Color.White.copy(alpha = .68f), fontSize = 11.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                categories.forEach { category ->
                    TextButton(onClick = { selectedCategory = category }, colors = ButtonDefaults.textButtonColors(containerColor = if (selectedCategory == category) WhappyBlue else Color.White, contentColor = if (selectedCategory == category) Color.White else WhappyDark), shape = RoundedCornerShape(14.dp)) { Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        items(visible, key = { "channel-${it.id}" }) { channel ->
            val subscribed = currentUserId in channel.memberIds
            val owner = channel.ownerId == currentUserId
            Card(Modifier.fillMaxWidth().clickable { onOpen(channel) }, shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(if (subscribed) WhappyBlue else Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = if (subscribed) Color.White else WhappyBlue) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(channel.name, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp); if (channel.verified) Icon(Icons.Rounded.Verified, "Chaîne vérifiée", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(16.dp)) }
                            Text("${channel.category} · ${formatCompactCount(channel.memberCount)} abonnés", color = WhappyMuted, fontSize = 11.sp)
                        }
                        if (owner) Text("PROPRIÉTAIRE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        else OutlinedButton(onClick = { onSubscribe(channel.id, !subscribed) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text(if (subscribed) "Suivie" else "Suivre", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text(channel.description, color = WhappyInk, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (channel.lastPost.isNotBlank()) Text(channel.lastPost, Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color.White).padding(9.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${channel.postCount} publication${if (channel.postCount > 1) "s" else ""}", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Ouvrir ›", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelScreen(
    channel: WhappyChannel,
    posts: List<WhappyChannelPost>,
    currentUserId: String,
    loading: Boolean,
    sending: Boolean,
    onBack: () -> Unit,
    onSubscribe: (Boolean) -> Unit,
    onPublish: (String) -> Unit,
    onReact: (WhappyChannelPost, String) -> Unit,
    onPin: (WhappyChannelPost) -> Unit,
    onDelete: (WhappyChannelPost) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val owner = channel.ownerId == currentUserId
    val subscribed = currentUserId in channel.memberIds
    var text by rememberSaveable(channel.id) { mutableStateOf("") }
    var searchOpen by rememberSaveable(channel.id) { mutableStateOf(false) }
    var search by rememberSaveable(channel.id) { mutableStateOf("") }
    var selectedPost by remember(channel.id) { mutableStateOf<WhappyChannelPost?>(null) }
    var showingChannelCode by remember(channel.id) { mutableStateOf(false) }
    val channelLink = remember(channel.id) { "https://whappy.chat/channel/${channel.id}" }
    val visible = remember(posts, search) { posts.filter { SearchNormalizer.matches(search, it.text, it.authorName) }.sortedWith(compareByDescending<WhappyChannelPost> { it.pinned }.thenBy { it.createdAt }) }
    var channelPositioned by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id, visible.size, search) {
        if (visible.isNotEmpty() && search.isBlank()) {
            val nearBottom = !channelPositioned || listState.firstVisibleItemIndex >= (visible.size - 4).coerceAtLeast(0)
            if (nearBottom) {
                if (channelPositioned) listState.animateScrollToItem(visible.lastIndex) else listState.scrollToItem(visible.lastIndex)
                channelPositioned = true
            }
        }
    }

    Column(Modifier.fillMaxSize().background(WhappyBackground).imePadding()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = Color.White) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(channel.name, fontWeight = FontWeight.Black, color = WhappyDark); if (channel.verified) Icon(Icons.Rounded.Verified, null, tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }; Text("${formatCompactCount(channel.memberCount)} abonnés · ${channel.postCount} publications", color = WhappyMuted, fontSize = 10.sp) }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Icon(Icons.Rounded.Search, "Rechercher", tint = if (searchOpen) WhappyBlue else WhappyDark) }
            IconButton(onClick = { showingChannelCode = true }) { Icon(Icons.Rounded.Share, "Partager la chaîne", tint = WhappyDark) }
        }
        if (searchOpen) OutlinedTextField(search, { search = it.take(120) }, Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), placeholder = { Text("Rechercher dans la chaîne") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Column(Modifier.fillMaxWidth().background(WhappyNavy).padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(channel.description, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) { Text("Par ${channel.ownerName} · ${channel.category}", Modifier.weight(1f), color = Color.White.copy(alpha = .64f), fontSize = 10.sp); if (!owner) Button(onClick = { onSubscribe(!subscribed) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text(if (subscribed) "Abonné ✓" else "S’abonner", fontSize = 11.sp) } }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Abonnés", formatCompactCount(channel.memberCount))
                StatPill("Posts", channel.postCount.toString())
                StatPill("Statut", if (owner) "Owner" else if (subscribed) "Suivie" else "Public")
            }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyState(if (search.isBlank()) "Aucune publication" else "Aucun résultat", if (owner && search.isBlank()) "Publiez la première actualité de votre chaîne." else "Les prochaines publications apparaîtront ici.") }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { "post-${it.id}" }) { post ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !post.deleted) { selectedPost = post }, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = if (post.pinned) Color.White else Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { if (post.pinned) Text("📌 ÉPINGLÉ", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(formatShortDate(post.createdAt) + " · " + formatTime(post.createdAt), color = WhappyMuted, fontSize = 9.sp) }
                        Text(if (post.deleted) "Publication supprimée" else post.text, color = if (post.deleted) WhappyMuted else WhappyInk, lineHeight = 21.sp, fontSize = 14.sp)
                        if (post.reactions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { post.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) -> Text("$emoji ${if (count > 1) count else ""}", Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp) } }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(post.authorName, Modifier.weight(1f), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(if (subscribed || owner) "Appuyez pour réagir" else "Abonnez-vous pour réagir", color = WhappyMuted, fontSize = 9.sp) }
                    }
                }
            }
        }
        if (owner) Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "📌 Épingler une info" to "📌 À retenir : ",
                    "🛍️ Offre" to "🛍️ Offre du jour : ",
                    "📅 Programme" to "📅 Prochain rendez-vous : ",
                ).forEach { option ->
                    OutlinedButton(onClick = { text = (option.second + text.removePrefix(option.second)).take(4_000) }, shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text(option.first, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(text, { text = it.take(4_000) }, Modifier.weight(1f), placeholder = { Text("Nouvelle publication…") }, maxLines = 6, supportingText = { Text("${text.length}/4000") }, shape = RoundedCornerShape(18.dp))
                FilledIconButton(enabled = text.isNotBlank() && !sending, onClick = { onPublish(text); text = "" }, modifier = Modifier.padding(start = 8.dp).size(50.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Rounded.Send, "Publier", tint = Color.White) }
            }
        } else if (!subscribed) Button(onClick = { onSubscribe(true) }, Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Notifications, null); Text("  S’abonner à cette chaîne", fontWeight = FontWeight.Bold) }
    }
    selectedPost?.let { post ->
        AlertDialog(onDismissRequest = { selectedPost = null }, title = { Text("Publication", fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(post.text, color = WhappyMuted, maxLines = 4)
            if (subscribed || owner) { Text("Réagir", fontWeight = FontWeight.Bold); Row { listOf("❤️", "👍", "🔥", "👏", "💡").forEach { emoji -> TextButton(onClick = { onReact(post, emoji); selectedPost = null }, contentPadding = PaddingValues(7.dp)) { Text(emoji, fontSize = 20.sp) } } } }
            OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Publication WAPI", post.text)); selectedPost = null }, Modifier.fillMaxWidth()) { Text("Copier la publication") }
            if (owner) { OutlinedButton(onClick = { onPin(post); selectedPost = null }, Modifier.fillMaxWidth()) { Text(if (post.pinned) "Désépingler" else "Épingler en haut") }; OutlinedButton(onClick = { onDelete(post); selectedPost = null }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = WhappyBlue)) { Text("Supprimer") } }
        } }, confirmButton = { TextButton(onClick = { selectedPost = null }) { Text("Fermer") } })
    }
    if (showingChannelCode) {
        val qr = remember(channelLink) { createWhappyPayloadQr("whappy://channel/${channel.id}") }
        AlertDialog(onDismissRequest = { showingChannelCode = false }, title = { Text("Partager la chaîne", fontWeight = FontWeight.Black) }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(qr.asImageBitmap(), "QR de la chaîne ${channel.name}", Modifier.size(210.dp).clip(RoundedCornerShape(18.dp)))
            Text(channel.name, fontWeight = FontWeight.Black, color = WhappyDark)
            Text(channelLink, color = WhappyMuted, fontSize = 10.sp)
            OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Lien WAPI", channelLink)) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.QrCode, null); Text("  Copier le lien") }
            Button(onClick = { shareWhappyLink(context, channel.name, channelLink) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Share, null); Text("  Partager") }
        } }, confirmButton = { TextButton(onClick = { showingChannelCode = false }) { Text("Terminé") } })
    }
}

private fun formatCompactCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.FRANCE, "%.1f M", value / 1_000_000f).replace(",0", "")
    value >= 1_000 -> String.format(Locale.FRANCE, "%.1f k", value / 1_000f).replace(",0", "")
    else -> value.toString()
}

@Composable
private fun BusinessSearchDialog(
    results: List<WhappyBusinessPage>,
    busy: Boolean,
    contactBusy: Boolean,
    onSearch: (String) -> Unit,
    onContact: (WhappyBusinessPage) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val hasSearched = query.trim().length >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trouver un Business") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cherchez une marque, une activité, un @handle ou une ville.", color = WhappyMuted)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    label = { Text("Ex. Mokabi, mode, Brazzaville") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onSearch = { if (hasSearched) onSearch(query) }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(enabled = hasSearched && !busy, onClick = { onSearch(query) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Rechercher", fontWeight = FontWeight.Bold)
                }
                if (hasSearched && !busy && results.isEmpty()) Text("Aucun Business trouvé. Essayez un nom, une catégorie ou une ville.", color = WhappyMuted, fontSize = 12.sp)
                if (results.isNotEmpty()) LazyColumn(Modifier.height(230.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { page ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue, modifier = Modifier.size(20.dp)) }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(page.name, fontWeight = FontWeight.Black, color = WhappyDark)
                                    Text(listOf(page.category, page.city).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Business WAPI" }, color = WhappyMuted, fontSize = 11.sp)
                                    if (page.handle.isNotBlank()) Text("@${page.handle}", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(enabled = !contactBusy, onClick = { onContact(page) }) { Text("Contacter") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun ChatScreen(
    conversation: WhappyConversation,
    messages: List<WhappyMessage>,
    storyStatuses: List<WhappyStatus>,
    currentUserId: String,
    loading: Boolean,
    sending: Boolean,
    groupBusy: Boolean,
    groupUpdate: WapiGroupUpdateState,
    onBack: () -> Unit,
    onSend: (String, WhappyMessage?) -> Unit,
    onSendMedia: (Uri, String, String, String, Int, Boolean) -> Unit,
    onMarkViewOnce: (WhappyMessage) -> Unit,
    onReact: (WhappyMessage, String) -> Unit,
    onDelete: (WhappyMessage) -> Unit,
    onEdit: (WhappyMessage, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onRetryPending: () -> Unit,
    onUpdateGroup: (String, Uri?, String, Boolean) -> Unit,
    onSetGroupAdministrator: (String, Boolean) -> Unit,
    onManageGroupMembers: (List<String>, String) -> Unit,
    onUpdateGroupSettings: (String, Boolean, Boolean) -> Unit,
    availableContacts: List<WhappyContact>,
    onSetLiveSubscription: (String, Boolean) -> Unit,
    publicRadioEpisodes: List<WapiRadioEpisode>,
    requestedGroupCall: WapiGroupCallInvitation?,
    onConsumeGroupCallLink: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val context = LocalContext.current
    val draftPrefs = remember { WhappyFastStorage.preferences(context, "whappy_chat_drafts") }
    val wallpaperPrefs = remember { WhappyFastStorage.preferences(context, "wapi_chat_wallpapers") }
    var text by remember(conversation.id) { mutableStateOf(draftPrefs.getString(conversation.id, "").orEmpty()) }
    var wallpaper by remember(conversation.id) { mutableStateOf(wallpaperPrefs.getString(conversation.id, "cloud").orEmpty()) }
    var showWallpaperPicker by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var showChatMenu by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var showEmoji by remember(conversation.id) { mutableStateOf(false) }
    var showMore by remember(conversation.id) { mutableStateOf(false) }
    var nextMediaViewOnce by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var recording by remember(conversation.id) { mutableStateOf(false) }
    var recordingPaused by remember(conversation.id) { mutableStateOf(false) }
    var recordStartedAt by remember(conversation.id) { mutableLongStateOf(0L) }
    var recordPausedAt by remember(conversation.id) { mutableLongStateOf(0L) }
    var recordPausedDuration by remember(conversation.id) { mutableLongStateOf(0L) }
    var recordingSeconds by remember(conversation.id) { mutableIntStateOf(0) }
    var recordingAmplitude by remember(conversation.id) { mutableFloatStateOf(0f) }
    var recorder by remember(conversation.id) { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember(conversation.id) { mutableStateOf<File?>(null) }
    var voiceDraft by remember(conversation.id) { mutableStateOf<VoiceNoteDraft?>(null) }
    var voiceNoteError by remember(conversation.id) { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(conversation.id) { mutableStateOf("") }
    var replyTo by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    var selectedMessage by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    var editingMessage by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    var offerOpen by remember(conversation.id) { mutableStateOf(false) }
    var offerValue by remember(conversation.id) { mutableStateOf("") }
    var offerDetails by remember(conversation.id) { mutableStateOf("") }
    var offerMedia by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    var offerMediaType by remember(conversation.id) { mutableStateOf("") }
    var offerMediaName by remember(conversation.id) { mutableStateOf("") }
    var memeSource by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    var memeTopText by rememberSaveable(conversation.id) { mutableStateOf("") }
    var memeBottomText by rememberSaveable(conversation.id) { mutableStateOf("") }
    var memeOpen by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var memeBusy by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var lingwapMessage by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    var lingwapTarget by rememberSaveable(conversation.id) { mutableStateOf("en") }
    var lingwapLanguageMenuOpen by remember(conversation.id) { mutableStateOf(false) }
    var lingwapTranslation by remember(conversation.id) { mutableStateOf<String?>(null) }
    var lingwapError by remember(conversation.id) { mutableStateOf<String?>(null) }
    var lingwapBusy by remember(conversation.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val calls = LocalWhappyCalls.current
    val radio = LocalWapiRadio.current
    val radioState = radio?.state?.value
    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    val visibleMessages = remember(messages, searchQuery) {
        messages
            .filter { SearchNormalizer.matches(searchQuery, it.text, it.mediaName, it.replyText) }
            .sortedWith(compareBy<WhappyMessage> { if (it.createdAt > 0L) it.createdAt else Long.MAX_VALUE }.thenBy { it.id })
    }
    val messageIndexById = remember(visibleMessages) { visibleMessages.mapIndexed { index, message -> message.id to index }.toMap() }
    // Warm the disk cache as soon as the thread opens. This keeps the first
    // visible photos available from the previous session and preloads the
    // next messages before the user scrolls to them, without decoding every
    // image into memory at once.
    LaunchedEffect(visibleMessages.map { "${it.id}:${it.kind}:${it.mediaUrl}" }) {
        visibleMessages.asSequence()
            .filter { it.kind == "image" && it.mediaUrl.startsWith("http") }
            .map { it.mediaUrl }
            .distinct()
            .toList()
            .takeLast(12)
            .forEach { source -> launch { WapiStableImageLoader.prefetch(context, source) } }
    }
    var chatPositioned by remember(conversation.id) { mutableStateOf(false) }
    var followLatest by remember(conversation.id) { mutableStateOf(true) }
    var unseenWhileReading by remember(conversation.id) { mutableIntStateOf(0) }
    var observedLatestMessageId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var previewImage by remember(conversation.id) { mutableStateOf<String?>(null) }
    var showPeerProfile by remember(conversation.id) { mutableStateOf(false) }
    var selectedGroupMember by remember(conversation.id) { mutableStateOf<WhappyMember?>(null) }
    var showGroupEditor by remember(conversation.id) { mutableStateOf(false) }
    var showGroupAdministrators by remember(conversation.id) { mutableStateOf(false) }
    var showGroupMembers by remember(conversation.id) { mutableStateOf(false) }
    var showGroupSettings by remember(conversation.id) { mutableStateOf(false) }
    var showGroupMemberManager by remember(conversation.id) { mutableStateOf(false) }
    var showGroupMedia by remember(conversation.id) { mutableStateOf(false) }
    var groupMemberAction by remember(conversation.id) { mutableStateOf("add") }
    var selectedManagedMembers by remember(conversation.id) { mutableStateOf(emptySet<String>()) }
    var groupDescriptionDraft by remember(conversation.id) { mutableStateOf(conversation.groupDescription) }
    var groupEditInfoDraft by remember(conversation.id) { mutableStateOf(conversation.groupEditInfoByMembers) }
    var groupAdminsOnlyDraft by remember(conversation.id) { mutableStateOf(conversation.groupOnlyAdminsCanSend) }
    var groupCallOpen by remember(conversation.id) { mutableStateOf(false) }
    var groupCallVideo by remember(conversation.id) { mutableStateOf(false) }
    var groupCallRadio by remember(conversation.id) { mutableStateOf(false) }
    var groupCallInvitation by remember(conversation.id) { mutableStateOf<WapiGroupCallInvitation?>(null) }
    var liveAlertEnabled by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var showRadioPicker by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var groupNameDraft by remember(conversation.id) { mutableStateOf(conversation.peer.displayName) }
    var groupPhotoUri by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    var groupPhotoToCrop by remember(conversation.id) { mutableStateOf<Uri?>(null) }
    var removeGroupPhoto by remember(conversation.id) { mutableStateOf(false) }
    var groupSavePending by remember(conversation.id) { mutableStateOf(false) }
    val groupPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        groupPhotoToCrop = uri
    }
    val selectedGroupPhotoContentType = remember(groupPhotoUri) {
        groupPhotoUri?.let { context.contentResolver.getType(it) }?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    }
    val isGroupAdministrator = conversation.groupOwnerId == currentUserId || currentUserId in conversation.groupAdminIds
    val maySendGroupMessage = !conversation.isGroup || !conversation.groupOnlyAdminsCanSend || isGroupAdministrator
    LaunchedEffect(groupUpdate.groupId, groupUpdate.status, groupSavePending) {
        if (groupSavePending && groupUpdate.groupId == conversation.id && groupUpdate.status == "saved") {
            groupSavePending = false
            showGroupEditor = false
            groupPhotoUri = null
            removeGroupPhoto = false
        }
        if (groupSavePending && groupUpdate.groupId == conversation.id && groupUpdate.status == "failed") {
            groupSavePending = false
        }
    }
    fun openWapiMedia(message: WhappyMessage) {
        val mime = when (message.kind) {
            "video" -> "video/mp4"
            "audio" -> "audio/mp4"
            "document" -> "application/octet-stream"
            else -> "image/jpeg"
        }
        chatScope.launch {
            WapiMediaOpener.open(context, message.mediaUrl, message.mediaName, mime)
                .onFailure { voiceNoteError = "Ce média WAPI n’a pas pu être préparé sur cet appareil." }
        }
    }

    LaunchedEffect(conversation.id) {
        WhappyNotifications.markConversationOpened(context, conversation.id)
    }
    LaunchedEffect(requestedGroupCall?.callId, conversation.id) {
        val invitation = requestedGroupCall ?: return@LaunchedEffect
        if (invitation.groupId != conversation.id) return@LaunchedEffect
        groupCallInvitation = invitation
        groupCallVideo = invitation.video
        groupCallRadio = false
        groupCallOpen = true
        onConsumeGroupCallLink()
    }

    fun updateDraft(value: String) {
        if (value.length > text.length) WhappySounds.typing(context)
        text = value.take(4_000)
        if (text.isBlank()) draftPrefs.edit().remove(conversation.id).apply()
        else draftPrefs.edit().putString(conversation.id, text).apply()
    }

    fun handleMessageAction(action: MessageAction) {
        if (action.type == MessageActionType.Phone) calls?.startByPhone(action.target, false)
        else openMessageAction(uriHandler, action)
    }

    fun submitText() {
        val value = text.trim()
        if (value.isBlank()) return
        editingMessage?.let { onEdit(it, value) } ?: onSend(value, replyTo)
        WhappySounds.sent()
        WhappySounds.haptic(context)
        text = ""
        replyTo = null
        editingMessage = null
        draftPrefs.edit().remove(conversation.id).apply()
        onTyping(false)
    }

    fun startRecording() {
        voiceDraft?.file?.delete()
        voiceDraft = null
        runCatching { createVoiceRecorder(context) }.onSuccess { (activeRecorder, file) ->
            recorder = activeRecorder
            recordingFile = file
            recordStartedAt = System.currentTimeMillis()
            recordPausedAt = 0L
            recordPausedDuration = 0L
            recordingSeconds = 0
            recordingAmplitude = 0f
            recording = true
            recordingPaused = false
            voiceNoteError = null
            showEmoji = false
            keyboard?.hide()
            WhappySounds.voiceRecordingStarted()
            WhappySounds.haptic(context)
        }.onFailure {
            voiceNoteError = "Le micro n’a pas démarré. Vérifiez l’autorisation puis réessayez."
        }
    }

    fun stopRecording(keepDraft: Boolean) {
        val active = recorder
        val file = recordingFile
        val now = System.currentTimeMillis()
        val activePause = if (recordingPaused && recordPausedAt > 0L) now - recordPausedAt else 0L
        val duration = ((now - recordStartedAt - recordPausedDuration - activePause) / 1_000L).toInt().coerceAtLeast(1)
        val stopped = runCatching { active?.stop() }.isSuccess
        active?.release()
        recorder = null
        recording = false
        recordingPaused = false
        recordingFile = null
        recordingSeconds = 0
        recordingAmplitude = 0f
        if (active != null) WhappySounds.voiceRecordingStopped()
        if (keepDraft && stopped && file != null && file.exists() && file.length() > 0L) {
            voiceDraft = VoiceNoteDraft(file, duration)
        } else {
            file?.delete()
            if (keepDraft) voiceNoteError = "La note est trop courte ou n’a pas pu être enregistrée. Réessayez en parlant un peu plus longtemps."
            else WhappySounds.voiceRecordingCancelled()
        }
    }

    fun toggleRecordingPause() {
        val active = recorder ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            if (recordingPaused) {
                active.resume()
                recordPausedDuration += (System.currentTimeMillis() - recordPausedAt).coerceAtLeast(0L)
                recordPausedAt = 0L
                recordingPaused = false
                WhappySounds.voiceRecordingStarted()
            } else {
                active.pause()
                recordPausedAt = System.currentTimeMillis()
                recordingPaused = true
                recordingAmplitude = 0f
                WhappySounds.voiceRecordingStopped()
            }
            WhappySounds.haptic(context)
        }.onFailure {
            voiceNoteError = "La pause du microphone n’est pas prise en charge sur cet appareil."
        }
    }

    fun discardVoiceDraft() {
        voiceDraft?.file?.delete()
        voiceDraft = null
        WhappySounds.voiceRecordingCancelled()
    }

    fun sendVoiceDraft() {
        val draft = voiceDraft ?: return
        onSendMedia(
            Uri.fromFile(draft.file),
            "audio",
            "audio/mp4",
            "note-vocale-${System.currentTimeMillis()}.m4a",
            draft.durationSeconds,
            nextMediaViewOnce,
        )
        voiceDraft = null
        nextMediaViewOnce = false
        WhappySounds.sent()
        WhappySounds.haptic(context)
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else voiceNoteError = "L’autorisation du microphone est nécessaire pour enregistrer une note vocale."
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            val kind = if (type.startsWith("video/")) "video" else "image"
            onSendMedia(uri, kind, type, displayName(context, uri), 0, nextMediaViewOnce)
            nextMediaViewOnce = false
            WhappySounds.mediaAdded()
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/pdf" }
            onSendMedia(uri, "document", type, displayName(context, uri), 0, false)
            WhappySounds.mediaAdded()
        }
    }
    val offerMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            offerMedia = uri
            offerMediaType = context.contentResolver.getType(uri) ?: "image/jpeg"
            offerMediaName = displayName(context, uri)
            WhappySounds.mediaAdded()
        }
    }
    val memePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            memeSource = uri
            memeTopText = ""
            memeBottomText = ""
            memeOpen = true
            WhappySounds.mediaAdded()
        }
    }

    DisposableEffect(conversation.id) {
        onDispose {
            onTyping(false)
            runCatching { recorder?.stop() }
            recorder?.release()
            recordingFile?.delete()
            voiceDraft?.file?.delete()
        }
    }
    LaunchedEffect(recording, recordingPaused) {
        while (recording) {
            val now = System.currentTimeMillis()
            val activePause = if (recordingPaused && recordPausedAt > 0L) now - recordPausedAt else 0L
            recordingSeconds = ((now - recordStartedAt - recordPausedDuration - activePause) / 1_000L).toInt().coerceAtLeast(0)
            recordingAmplitude = if (recordingPaused) 0f else runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(.04f, 1f)
            delay(120)
        }
    }
    LaunchedEffect(text, editingMessage?.id) {
        if (editingMessage == null && text.isNotBlank()) { onTyping(true); delay(1_400); onTyping(false) }
        else onTyping(false)
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(listState.isScrollInProgress, lastVisible, layout.totalItemsCount)
        }.distinctUntilChanged().collect { (scrolling, lastVisible, total) ->
            if (scrolling) {
                followLatest = total == 0 || lastVisible >= total - 2
                if (followLatest) unseenWhileReading = 0
            }
        }
    }
    LaunchedEffect(conversation.id, visibleMessages.lastOrNull()?.id, searchQuery) {
        if (visibleMessages.isNotEmpty() && searchQuery.isBlank()) {
            val latestId = visibleMessages.last().id
            when {
                !chatPositioned -> {
                    listState.scrollToItem(visibleMessages.lastIndex)
                    chatPositioned = true
                    followLatest = true
                }
                followLatest -> listState.animateScrollToItem(visibleMessages.lastIndex)
                observedLatestMessageId != null && observedLatestMessageId != latestId -> unseenWhileReading += 1
            }
            observedLatestMessageId = latestId
        }
    }

    Column(Modifier.fillMaxSize().background(WapiChatBackground).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            if (conversation.isGroup && conversation.peer.photoUrl.isBlank()) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(WhappyAurora).clickable { showPeerProfile = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Groups, null, tint = Color.White)
                }
            } else {
                val peerHasUnseenStory = storyStatuses.any { it.authorId == conversation.peer.uid && !it.viewedByCurrentUser }
                StoryRingAvatar(
                    photoUrl = conversation.peer.photoUrl,
                    name = conversation.peer.displayName,
                    size = 40.dp,
                    hasUnseenStory = peerHasUnseenStory,
                    onClick = { if (peerHasUnseenStory) onOpenStory(conversation.peer.uid) else showPeerProfile = true },
                    shape = RoundedCornerShape(11.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp).clickable { showPeerProfile = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.peer.displayName, fontWeight = FontWeight.SemiBold, color = WhappyDark, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (conversation.profileType == "business") {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = WhappyBlue.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) { Text("BUSINESS", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = WhappyBlue, fontSize = 7.sp, fontWeight = FontWeight.Black) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val activelyPresent = conversation.peerTyping || conversation.peer.isOnline
                    if (activelyPresent) Box(Modifier.size(6.dp).clip(CircleShape).background(WapiChatAccent))
                    Text(
                        when {
                            conversation.isGroup -> "  ${conversation.memberCount} membres"
                            conversation.profileType == "business" -> "  Messagerie Business"
                            conversation.peerTyping -> "  écrit…"
                            conversation.peer.isOnline -> "  en ligne"
                            conversation.peer.lastSeenAt > 0L -> "  ${formatLastSeen(conversation.peer.lastSeenAt)}"
                            else -> "  hors ligne"
                        },
                        color = if (activelyPresent) WapiChatAccent else WhappyMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) { Icon(Icons.Rounded.Search, "Rechercher dans la discussion", tint = if (searchOpen) WapiChatAccent else WhappyDark) }
            if (conversation.peer.uid.isNotBlank() && !conversation.isGroup) {
                IconButton(enabled = calls != null, onClick = { calls?.start(conversation.peer, false) }) { Icon(Icons.Rounded.Phone, "Appel audio ${conversation.peer.displayName}") }
                IconButton(enabled = calls != null, onClick = { calls?.start(conversation.peer, true) }) { Icon(Icons.Rounded.Videocam, "Appel vidéo ${conversation.peer.displayName}") }
            }
            if (conversation.isGroup) {
                IconButton(onClick = { groupCallInvitation = null; groupCallVideo = false; groupCallOpen = true }) { Icon(Icons.Rounded.Phone, "Appel audio du groupe") }
                IconButton(onClick = { groupCallInvitation = null; groupCallVideo = true; groupCallOpen = true }) { Icon(Icons.Rounded.Videocam, "Appel vidéo du groupe") }
            }
            Box {
                IconButton(onClick = { showChatMenu = true }) { Icon(Icons.Rounded.MoreVert, "Options de la conversation", tint = WhappyDark) }
                DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.isGroup) "Informations du groupe" else "Voir le profil") },
                        leadingIcon = { Icon(if (conversation.isGroup) Icons.Rounded.Groups else Icons.Rounded.Person, null, tint = WapiChatAccent) },
                        onClick = { showChatMenu = false; showPeerProfile = true },
                    )
                    if (conversation.isGroup) DropdownMenuItem(
                        text = { Text("Modifier le nom ou la photo") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = WapiChatAccent) },
                        onClick = {
                            showChatMenu = false
                            groupNameDraft = conversation.peer.displayName
                            groupPhotoUri = null
                            removeGroupPhoto = false
                            showGroupEditor = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (radioState?.playing == true) "Changer la radio en lecture" else "Radios et podcasts") },
                        leadingIcon = { Icon(Icons.Rounded.Radio, null, tint = WapiChatAccent) },
                        onClick = { showChatMenu = false; showRadioPicker = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Fond de conversation") },
                        leadingIcon = { Icon(Icons.Rounded.Photo, null, tint = WapiChatAccent) },
                        onClick = { showChatMenu = false; showWallpaperPicker = true },
                    )
                }
            }
        }
        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it.take(120) }, Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 12.dp, vertical = 6.dp), placeholder = { Text("Rechercher un message") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        radioState?.episode?.let { episode ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showRadioPicker = true },
                color = WhappyBlue.copy(alpha = .07f),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .13f)),
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(episode.stationName, color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(episode.title, color = WhappyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (radioState.preparing) "Connexion…" else if (radioState.playing) "EN LECTURE" else "PAUSE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = { if (radioState.playing) radio.pause() else radio.play(episode) }, modifier = Modifier.size(38.dp)) { Icon(if (radioState.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (radioState.playing) "Mettre en pause" else "Reprendre", tint = WhappyBlue) }
                }
            }
        }
        if (conversation.isGroup) {
            Row(Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "📌 Annonce" to "📌 Annonce : ",
                    "🗳️ Sondage" to "🗳️ Sondage : ",
                    "📅 Événement" to "📅 Événement : ",
                    "📎 Fichier" to "📎 Pièce jointe : ",
                ).forEach { option ->
                    OutlinedButton(
                        onClick = {
                            updateDraft(option.second + text.removePrefix(option.second))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = WhappyDark),
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp),
                    ) { Text(option.first, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        val pendingCount = messages.count { it.deliveryState != "sent" }
        if (pendingCount > 0) {
            Row(
                Modifier.fillMaxWidth().background(WhappyBlue.copy(alpha = .08f)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = WhappyBlue, modifier = Modifier.size(17.dp))
                Text(
                    "$pendingCount message${if (pendingCount > 1) "s" else ""} enregistré${if (pendingCount > 1) "s" else ""} hors connexion · envoi automatique",
                    Modifier.weight(1f).padding(horizontal = 9.dp),
                    color = WhappyBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onRetryPending) { Text("Réessayer maintenant") }
            }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else Box(Modifier.weight(1f).fillMaxWidth().background(chatWallpaperBrush(wallpaper))) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (visibleMessages.isEmpty() && searchQuery.isNotBlank()) item { Text("Aucun message ne correspond à « $searchQuery ».", Modifier.padding(24.dp), color = WhappyMuted) }
            itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, message ->
                    val mine = message.senderId == currentUserId && message.kind != "system"
                Column(Modifier.fillMaxWidth()) {
                    if (index == 0 || !isSameDay(message.createdAt, visibleMessages[index - 1].createdAt)) {
                        Text(
                            formatMessageDay(message.createdAt),
                            Modifier.align(Alignment.CenterHorizontally).padding(vertical = 7.dp).clip(CircleShape).background(Color.White.copy(alpha = .88f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = WhappyMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!mine && message.kind != "system") {
                            val senderMember = if (conversation.isGroup) conversation.groupMembers.firstOrNull { it.uid == message.senderId } else conversation.peer
                            val senderName = message.senderName.ifBlank { senderMember?.displayName ?: conversation.peer.displayName }
                            val senderPhoto = message.senderPhotoUrl.ifBlank { senderMember?.photoUrl.orEmpty() }
                            UserAvatar(
                                senderPhoto,
                                senderName,
                                30.dp,
                                Modifier.padding(top = 3.dp).clickable {
                                    if (conversation.isGroup) {
                                        selectedGroupMember = senderMember
                                            ?: WhappyMember(message.senderId, senderName.ifBlank { "Membre WAPI" }, photoUrl = senderPhoto)
                                    } else showPeerProfile = true
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Surface(
                            color = if (message.kind == "system") Color(0xFFF0F3F6) else if (mine) WapiBubbleOutgoing else Color.White,
                            shape = if (mine) RoundedCornerShape(topStart = 18.dp, topEnd = 5.dp, bottomEnd = 18.dp, bottomStart = 18.dp) else RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(0.76f).pointerInput(message.id, message.deleted, message.deliveryState) {
                                    detectTapGestures(onLongPress = { if (message.kind != "system" && !message.deleted && message.deliveryState == "sent") selectedMessage = message })
                        }) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                val messageMetaColor = if (mine) Color.White.copy(alpha = .78f) else WhappyMuted
                                val messageAccentColor = if (mine) Color.White else WapiChatAccent
                                if (conversation.isGroup && !mine && message.senderName.isNotBlank()) {
                                    Text(message.senderName, color = WapiChatAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                if (message.replyText.isNotBlank()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (mine) Color.White.copy(alpha = .17f) else Color.Black.copy(alpha = .06f)).padding(7.dp)) { Text("↩ ${message.replyText}", color = if (mine) Color.White.copy(alpha = .92f) else Color(0xFF666666), fontSize = 10.sp, maxLines = 2) }
                                if (message.replyText.isNotBlank()) {
                                    val targetIndex = messageIndexById[message.replyToId] ?: -1
                                    if (targetIndex >= 0) {
                                        TextButton(
                                            onClick = {
                                                chatScope.launch { listState.animateScrollToItem(targetIndex) }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        ) {
                                            Text("Voir le message d’origine", color = messageAccentColor, fontSize = 9.sp)
                                        }
                                    }
                                }
                                val actions = remember(message.text) { detectMessageActions(message.text) }
                                val recipientOpenedOnce = currentUserId in message.viewedByIds
                                if (message.viewOnce) {
                                    ViewOnceMediaRow(
                                        kind = message.kind,
                                        mine = mine,
                                        opened = recipientOpenedOnce,
                                        onOpen = if (mine || recipientOpenedOnce) null else {
                                            {
                                                onMarkViewOnce(message)
                                                if (message.kind == "image") previewImage = message.mediaUrl
                                                else openWapiMedia(message)
                                            }
                                        },
                                    )
                                } else when (message.kind) {
                                    "audio" -> VoiceNoteMessage(message.mediaUrl, message.durationSeconds, mine)
                                    "image" -> InlineImageMessage(message.mediaUrl, message.mediaName.ifBlank { "Photo" }, mine) { previewImage = message.mediaUrl }
                                    "video" -> MediaMessageRow(Icons.Rounded.Movie, message.mediaName.ifBlank { "Vidéo WAPI" }, mine) { openWapiMedia(message) }
                                    "document" -> WaphsareDocumentMessage(message.mediaUrl, message.mediaName, message.mediaSizeBytes, message.mediaSha256, mine) { openWapiMedia(message) }
                                    "deleted" -> Text("Message supprimé", color = messageMetaColor)
                                    "system" -> Text(message.text, color = WhappyMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    else -> MessageLinkText(message.text, mine, actions = actions, onAction = ::handleMessageAction)
                                }
                                if (actions.isNotEmpty()) {
                                    FlowRow(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        actions.take(2).forEach { action ->
                                            OutlinedButton(
                                                onClick = { handleMessageAction(action) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = messageAccentColor),
                                            ) { Text(if (action.type == MessageActionType.Phone) "📞 ${action.title}" else "🔗 ${action.title}") }
                                        }
                                    }
                                }
                                if (message.reactions.isNotEmpty()) Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { message.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) -> Text("$emoji${if (count > 1) " $count" else ""}", modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (mine) Color.White.copy(alpha = .18f) else Color.Black.copy(alpha = .06f)).padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 11.sp) } }
                                Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (message.edited) Text("modifié · ", color = messageMetaColor, fontSize = 9.sp)
                                    Text(formatTime(message.createdAt), color = messageMetaColor, fontSize = 9.sp)
                                    if (mine) {
                                        val pending = message.deliveryState != "sent"
                                        val groupReadCount = if (conversation.isGroup && message.createdAt > 0L) conversation.groupReadAt.count { (memberId, readAt) -> memberId != currentUserId && readAt >= message.createdAt } else 0
                                        val read = !pending && message.createdAt > 0L && if (conversation.isGroup) groupReadCount > 0 else conversation.peerReadAt >= message.createdAt
                                        val label = when {
                                            message.deliveryState == "retrying" -> " · Nouvelle tentative…"
                                            pending -> " · En attente…"
                                            read && conversation.isGroup -> " · Lu par $groupReadCount"
                                            read -> " · Lu"
                                            else -> " · Envoyé"
                                        }
                                        Text(label, color = messageMetaColor, fontSize = 9.sp)
                                        Icon(
                                            when { pending -> Icons.Rounded.Schedule; read -> Icons.Rounded.DoneAll; else -> Icons.Rounded.Check },
                                            null,
                                            tint = if (read) Color(0xFFDAF3FF) else Color.White.copy(alpha = .72f),
                                            modifier = Modifier.padding(start = 3.dp).size(if (read) 14.dp else 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!followLatest && visibleMessages.isNotEmpty() && searchQuery.isBlank()) {
            FilledIconButton(
                onClick = {
                    chatScope.launch {
                        listState.animateScrollToItem(visibleMessages.lastIndex)
                        followLatest = true
                        unseenWhileReading = 0
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = WapiChatAccent),
            ) { Text(if (unseenWhileReading > 0) "↓ $unseenWhileReading" else "↓", fontSize = 12.sp, fontWeight = FontWeight.Black) }
        }
        }
        if (showEmoji) EmojiTray(onEmoji = { updateDraft(text + it) }, onClose = { showEmoji = false })
        if (showWallpaperPicker) {
            AlertDialog(
                onDismissRequest = { showWallpaperPicker = false },
                title = { Text("Fond de conversation", fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choisissez un fond léger, fixé pendant le défilement des messages.", color = WhappyMuted, fontSize = 12.sp)
                        chatWallpaperNames.forEach { (key, name) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(chatWallpaperBrush(key)).clickable {
                                    wallpaper = key
                                    wallpaperPrefs.edit().putString(conversation.id, key).apply()
                                    showWallpaperPicker = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (wallpaper == key) WhappyBlue else WhappyLine),
                            ) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Bold); if (wallpaper == key) Icon(Icons.Rounded.CheckCircle, null, tint = WhappyBlue) } }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showWallpaperPicker = false }) { Text("Fermer") } },
            )
        }
        if (showRadioPicker) {
            AlertDialog(
                onDismissRequest = { showRadioPicker = false },
                icon = { Icon(Icons.Rounded.Radio, null, tint = WhappyBlue) },
                title = { Text("Radios & podcasts", color = WhappyDark, fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Changez de chaîne sans quitter cette conversation.", color = WhappyMuted, fontSize = 11.sp)
                        if (conversation.isGroup) {
                            Button(
                                onClick = {
                                    showRadioPicker = false
                                    groupCallInvitation = null
                                    groupCallVideo = false
                                    groupCallRadio = true
                                    groupCallOpen = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) { Icon(Icons.Rounded.Radio, null); Text("  Démarrer la radio du groupe") }
                        }
                        if (publicRadioEpisodes.isEmpty()) Text("Aucune émission publiée n’est encore disponible.", color = WhappyMuted, fontSize = 12.sp)
                        else publicRadioEpisodes.sortedByDescending { it.createdAt }.take(12).forEach { episode ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable { radio?.play(episode); showRadioPicker = false },
                                color = if (radioState?.episode?.id == episode.id) WhappyBlue.copy(alpha = .09f) else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(15.dp),
                            ) {
                                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(if (radioState?.episode?.id == episode.id && radioState.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(episode.title, color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${episode.stationName} · ${formatRadioDuration(episode.durationSeconds)}", color = WhappyMuted, fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { radio?.stop(); showRadioPicker = false }) { Text("Arrêter") } },
                dismissButton = { TextButton(onClick = { showRadioPicker = false }) { Text("Fermer") } },
                containerColor = Color.White,
                shape = RoundedCornerShape(26.dp),
            )
        }
        if (recording || voiceDraft != null || voiceNoteError != null) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (recording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE33D4E)))
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text(if (recordingPaused) "Enregistrement en pause · ${formatVoiceDuration(recordingSeconds)}" else "Enregistrement en cours · ${formatVoiceDuration(recordingSeconds)}", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row(Modifier.padding(top = 5.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(20) { index ->
                                    val pattern = .30f + kotlin.math.abs(kotlin.math.sin(index * .77f)) * .70f
                                    Box(
                                        Modifier.width(3.dp)
                                            .height((5f + 19f * recordingAmplitude * pattern).dp)
                                            .clip(CircleShape)
                                            .background(if (index < 14) WhappyBlue else WhappyBlue.copy(alpha = .42f)),
                                    )
                                }
                            }
                            Text(if (recordingPaused) "Reprenez quand vous êtes prêt, ou terminez pour écouter." else "Vous pouvez mettre en pause ou terminer pour écouter.", color = WhappyMuted, fontSize = 10.sp)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            IconButton(onClick = ::toggleRecordingPause) {
                                Icon(if (recordingPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (recordingPaused) "Reprendre" else "Pause", tint = WhappyBlue)
                            }
                        }
                        TextButton(onClick = { stopRecording(false) }) { Text("Annuler") }
                    }
                }
                voiceDraft?.let { draft ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(WhappyBlue.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VoiceNoteMessage(
                            source = Uri.fromFile(draft.file).toString(),
                            durationSeconds = draft.durationSeconds,
                            mine = false,
                            modifier = Modifier.weight(1f),
                            title = "Note vocale prête",
                        )
                        TextButton(onClick = ::discardVoiceDraft) { Text("Supprimer") }
                        Button(onClick = ::sendVoiceDraft, enabled = !sending && maySendGroupMessage, shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(16.dp)); Text("  Envoyer", fontSize = 11.sp) }
                    }
                }
                voiceNoteError?.let { error ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF2F2)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = Color(0xFFB3261E), fontSize = 10.sp, lineHeight = 14.sp)
                        TextButton(onClick = { voiceNoteError = null }) { Text("OK") }
                    }
                }
            }
        }
        replyTo?.let { message -> Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = WhappyBlue, modifier = Modifier.size(17.dp)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text("Répondre", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(message.text.ifBlank { message.mediaName.ifBlank { "Média" } }, color = WhappyDark, fontSize = 11.sp, maxLines = 1) }; TextButton(onClick = { replyTo = null }) { Text("Annuler") } } }
        editingMessage?.let { message -> Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.MoreVert, null, tint = WhappyBlue, modifier = Modifier.size(17.dp)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text("Modifier le message", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(message.text, color = WhappyDark, fontSize = 11.sp, maxLines = 1) }; TextButton(onClick = { editingMessage = null; text = ""; draftPrefs.edit().remove(conversation.id).apply() }) { Text("Annuler") } } }
        if (previewImage != null) {
            ImageZoomViewer(
                imageSource = previewImage.orEmpty(),
                onDismiss = { previewImage = null },
            )
        }
        if (!maySendGroupMessage) {
            Surface(Modifier.fillMaxWidth(), color = WhappyBlue.copy(alpha = .07f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = WhappyBlue, modifier = Modifier.size(18.dp))
                    Text("Mode annonces · seuls les administrateurs peuvent envoyer des messages", Modifier.padding(start = 9.dp), color = WhappyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(WapiToolbar).navigationBarsPadding().imePadding().padding(horizontal = 5.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(
                enabled = maySendGroupMessage && !sending && voiceDraft == null,
                onClick = {
                    if (recording) stopRecording(true)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                },
            ) { Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording) "Arrêter l’enregistrement" else "Note vocale", tint = if (recording) Color(0xFFE33D4E) else WhappyDark) }
            OutlinedTextField(
                value = text,
                onValueChange = { updateDraft(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (recording) "Enregistrement en cours…" else "Message…") },
                enabled = maySendGroupMessage && !recording,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WhappyDark,
                    unfocusedTextColor = WhappyDark,
                    disabledTextColor = WhappyMuted,
                    cursorColor = WapiChatAccent,
                    focusedBorderColor = WapiChatAccent.copy(alpha = .45f),
                    unfocusedBorderColor = WhappyLine,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFF9FCFE),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = true),
                keyboardActions = KeyboardActions(onSend = { submitText(); keyboard?.hide() }),
            )
            IconButton(enabled = maySendGroupMessage && !recording, onClick = { showEmoji = !showEmoji; showMore = false; if (showEmoji) keyboard?.hide() }) { Icon(Icons.Rounded.EmojiEmotions, "Émojis", tint = WhappyDark) }
            if (text.isNotBlank()) Button(
                enabled = maySendGroupMessage && !sending,
                onClick = { submitText() },
                modifier = Modifier.padding(start = 2.dp).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WapiChatAccent),
                contentPadding = PaddingValues(horizontal = 13.dp),
            ) { Text("Envoyer", color = Color.White, fontSize = 12.sp) }
            else IconButton(enabled = maySendGroupMessage && !recording, onClick = { showMore = !showMore; showEmoji = false; keyboard?.hide() }) { Icon(Icons.Rounded.Add, "Plus", tint = WhappyDark, modifier = Modifier.size(28.dp)) }
        }
        if (showMore && maySendGroupMessage) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(WapiToolbar).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showMore = false; mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Photo, "Photos et vidéos", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Album", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showMore = false; offerOpen = true }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, "Faire une offre", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Offre", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showMore = false; memePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, "Créer un mème", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Mème", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showMore = false; documentPicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain")) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AttachFile, "Document original", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Waphsare", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { nextMediaViewOnce = !nextMediaViewOnce; WhappySounds.haptic(context) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(if (nextMediaViewOnce) WhappyBlue else WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Visibility, "Vue unique", tint = if (nextMediaViewOnce) Color.White else WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text(if (nextMediaViewOnce) "1 vue active" else "1 vue", Modifier.padding(top = 6.dp), color = if (nextMediaViewOnce) WhappyBlue else WhappyMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (offerOpen) {
        AlertDialog(
            onDismissRequest = { if (!sending) offerOpen = false },
            icon = {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocalOffer, null, tint = Color.White, modifier = Modifier.size(29.dp))
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("WAPI ACTION · OFFRE SÉCURISÉE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text("Faire une offre", color = WhappyDark, fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("Proposez un prix ou un échange. Ajoutez une image ou une vidéo pour clarifier votre proposition.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    LinearProgressIndicator(
                        progress = { if (offerValue.isBlank()) .32f else if (offerDetails.isBlank() && offerMedia == null) .68f else 1f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = WhappyBlue,
                        trackColor = WhappyBlue.copy(alpha = .1f),
                    )
                    OutlinedTextField(
                        value = offerValue,
                        onValueChange = { offerValue = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Votre proposition") },
                        placeholder = { Text("Prix ou échange proposé") },
                        leadingIcon = { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = offerDetails,
                        onValueChange = { offerDetails = it.take(1_000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Détails facultatifs") },
                        placeholder = { Text("Disponibilité, conditions, livraison…") },
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !sending) { offerMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        color = WhappyBlue.copy(alpha = .055f),
                        shape = RoundedCornerShape(17.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .22f)),
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                                Icon(if (offerMediaType.startsWith("video/")) Icons.Rounded.Movie else Icons.Rounded.Photo, null, tint = Color.White)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                Text(if (offerMedia == null) "Ajouter une image ou une vidéo" else offerMediaName, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                Text(if (offerMedia == null) "JPG, PNG, WEBP, MP4 ou MOV" else "Média prêt à envoyer · son confirmé", color = WhappyMuted, fontSize = 9.sp)
                            }
                            if (offerMedia != null) IconButton(onClick = { offerMedia = null; offerMediaType = ""; offerMediaName = "" }) { Icon(Icons.Rounded.Close, "Retirer le média", tint = WhappyBlue) }
                            else Icon(Icons.Rounded.Add, null, tint = WhappyBlue)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(WhappyBlue.copy(alpha = .045f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = WhappyBlue, modifier = Modifier.size(18.dp))
                        Text("L’offre reste dans cette conversation jusqu’à validation.", Modifier.padding(start = 8.dp), color = WhappyDark, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = offerValue.trim().isNotEmpty() && !sending,
                    onClick = {
                        val message = "◇ Offre : ${offerValue.trim()}${offerDetails.trim().takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()}"
                        onSend(message, null)
                        offerMedia?.let { uri ->
                            val kind = if (offerMediaType.startsWith("video/")) "video" else "image"
                            onSendMedia(uri, kind, offerMediaType.ifBlank { if (kind == "video") "video/mp4" else "image/jpeg" }, offerMediaName.ifBlank { "offre-whappy" }, 0, false)
                        }
                        WhappySounds.offerSent()
                        offerOpen = false
                        offerValue = ""
                        offerDetails = ""
                        offerMedia = null
                        offerMediaType = ""
                        offerMediaName = ""
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Envoyer l’offre  ↗", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { offerOpen = false }) { Text("Annuler") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp),
        )
    }
    if (memeOpen && memeSource != null) {
        var memePreview by remember(memeSource) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(memeSource) {
            memePreview = withContext(Dispatchers.IO) { runCatching { loadImageBitmap(context, memeSource.toString()) }.getOrNull() }
        }
        AlertDialog(
            onDismissRequest = { if (!memeBusy) memeOpen = false },
            icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue) },
            title = { Text("Atelier de mèmes", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ajoutez votre texte à l’image. Le mème est créé sur votre téléphone puis envoyé comme une photo dans cette discussion.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    memePreview?.let { preview -> Image(preview, "Aperçu du mème", Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
                    OutlinedTextField(memeTopText, { memeTopText = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Texte du haut") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                    OutlinedTextField(memeBottomText, { memeBottomText = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Texte du bas") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                }
            },
            confirmButton = {
                Button(
                    enabled = !memeBusy && (memeTopText.isNotBlank() || memeBottomText.isNotBlank()),
                    onClick = {
                        val source = memeSource ?: return@Button
                        memeBusy = true
                        chatScope.launch {
                            runCatching { withContext(Dispatchers.IO) { renderWapiMeme(context, source, memeTopText, memeBottomText) } }
                                .onSuccess { file ->
                                    onSendMedia(Uri.fromFile(file), "image", "image/jpeg", file.name, 0, false)
                                    WhappySounds.sent()
                                    WhappySounds.haptic(context)
                                    memeOpen = false
                                    memeSource = null
                                }
                                .onFailure { voiceNoteError = "Le mème n’a pas pu être créé. Choisissez une autre image puis réessayez." }
                            memeBusy = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (memeBusy) "Création…" else "Créer et envoyer") }
            },
            dismissButton = { TextButton(enabled = !memeBusy, onClick = { memeOpen = false; memeSource = null }) { Text("Annuler") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showPeerProfile) {
        AlertDialog(
            onDismissRequest = { showPeerProfile = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 58.dp, shape = RoundedCornerShape(14.dp))
                    Column(Modifier.padding(start = 13.dp)) {
                        Text(conversation.peer.displayName, color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Text(if (conversation.isGroup) "Groupe · ${conversation.memberCount} membres" else "Profil WAPI", color = WhappyMuted, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (conversation.peer.phoneNumber.isNotBlank()) Text(conversation.peer.phoneNumber, color = WhappyDark, fontWeight = FontWeight.SemiBold)
                    Text(if (conversation.isGroup) "Ouvrez les informations du groupe, ses membres et ses médias depuis cette fiche." else "Photo, identité et moyens de contact de ce compte.", color = WhappyMuted, lineHeight = 19.sp)
                    if (conversation.isGroup) {
                        if (conversation.groupDescription.isNotBlank()) {
                            Text(conversation.groupDescription, color = WhappyDark, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Surface(color = WhappyBlue.copy(alpha = .06f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("MEMBRES", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        Text(
                                            when {
                                                conversation.groupOwnerId == currentUserId -> "Vous êtes le créateur du groupe"
                                                currentUserId in conversation.groupAdminIds -> "Vous êtes administrateur"
                                                else -> "Vous êtes membre"
                                            },
                                            color = WhappyMuted,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Text("${conversation.memberCount}", color = WhappyDark, fontWeight = FontWeight.Black)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    conversation.groupMembers.take(7).forEach { member ->
                                        UserAvatar(member.photoUrl, member.displayName, 34.dp, shape = RoundedCornerShape(9.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (conversation.isGroup) {
                        OutlinedButton(onClick = { groupNameDraft = conversation.peer.displayName; groupPhotoUri = null; removeGroupPhoto = false; showGroupEditor = true; showPeerProfile = false }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Edit, null)
                            Text("  Modifier le nom ou la photo")
                        }
                        OutlinedButton(onClick = { showPeerProfile = false; showGroupMembers = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Groups, null)
                            Text("  Voir tous les membres")
                        }
                        val sharedMediaCount = messages.count { it.kind in setOf("image", "video", "audio", "document") && !it.deleted }
                        OutlinedButton(onClick = { showPeerProfile = false; showGroupMedia = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Photo, null)
                            Text("  Médias, liens et documents · $sharedMediaCount")
                        }
                        if (isGroupAdministrator) {
                            OutlinedButton(onClick = { showPeerProfile = false; groupMemberAction = "add"; selectedManagedMembers = emptySet(); showGroupMemberManager = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.PersonAdd, null)
                                Text("  Ajouter des membres")
                            }
                            OutlinedButton(onClick = {
                                showPeerProfile = false
                                groupDescriptionDraft = conversation.groupDescription
                                groupEditInfoDraft = conversation.groupEditInfoByMembers
                                groupAdminsOnlyDraft = conversation.groupOnlyAdminsCanSend
                                showGroupSettings = true
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Lock, null)
                                Text("  Permissions et sécurité")
                            }
                        }
                    }
                    if (conversation.isGroup && conversation.groupOwnerId == currentUserId) {
                        OutlinedButton(onClick = { showPeerProfile = false; showGroupAdministrators = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Groups, null)
                            Text("  Gérer les administrateurs")
                        }
                    }
                    if (conversation.isGroup) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(onClick = { showPeerProfile = false; groupCallInvitation = null; groupCallRadio = false; groupCallVideo = false; groupCallOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Phone, null); Text(" Audio") }
                            OutlinedButton(onClick = { showPeerProfile = false; groupCallInvitation = null; groupCallRadio = false; groupCallVideo = true; groupCallOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Videocam, null); Text(" Vidéo") }
                        }
                    }
                    if (!conversation.isGroup) {
                        Text("Activité publique", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        val peerEpisodes = publicRadioEpisodes.filter { it.ownerId == conversation.peer.uid }
                        if (peerEpisodes.isEmpty()) Text("Aucune radio ni playlist publique partagée.", color = WhappyMuted, fontSize = 11.sp)
                        else peerEpisodes.take(4).forEach { episode ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { radio?.play(episode) },
                                color = WhappyBlue.copy(alpha = .06f),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Radio, null, tint = WhappyBlue)
                                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                        Text(episode.title, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                        Text(episode.stationName, color = WhappyMuted, fontSize = 9.sp, maxLines = 1)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, "Écouter", tint = WhappyBlue)
                                }
                            }
                        }
                    }
                    if (!conversation.isGroup && conversation.peer.uid.isNotBlank() && conversation.peer.uid != currentUserId) {
                        OutlinedButton(
                            onClick = {
                                val next = !liveAlertEnabled
                                liveAlertEnabled = next
                                onSetLiveSubscription(conversation.peer.uid, next)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (liveAlertEnabled) WhappyBlue else WhappyDark),
                        ) {
                            Icon(Icons.Rounded.Notifications, null)
                            Text(if (liveAlertEnabled) "  Alertes Live activées" else "  Me prévenir de ses Lives")
                        }
                    }
                    if (!conversation.isGroup && conversation.peer.phoneNumber.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(onClick = { calls?.start(conversation.peer, false); showPeerProfile = false }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Phone, null); Text(" Appeler") }
                            OutlinedButton(onClick = { calls?.start(conversation.peer, true); showPeerProfile = false }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Videocam, null); Text(" Vidéo") }
                            OutlinedButton(onClick = { showPeerProfile = false }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.ChatBubble, null); Text(" Message") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPeerProfile = false }) { Text("Fermer") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    selectedGroupMember?.let { member ->
        val memberRadio = publicRadioEpisodes.filter { it.ownerId == member.uid }
        AlertDialog(
            onDismissRequest = { selectedGroupMember = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(member.photoUrl, member.displayName, 58.dp, shape = RoundedCornerShape(14.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(member.displayName, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(if (member.uid in conversation.groupAdminIds) "Administrateur du groupe" else "Membre du groupe", color = WhappyMuted, fontSize = 10.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (member.phoneNumber.isNotBlank()) Text(member.phoneNumber, color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text("Activité publique", color = WhappyDark, fontWeight = FontWeight.Black)
                    if (memberRadio.isEmpty()) Text("Aucune radio ni playlist publique partagée.", color = WhappyMuted, fontSize = 11.sp)
                    else memberRadio.take(3).forEach { episode ->
                        Surface(Modifier.fillMaxWidth().clickable { radio?.play(episode) }, color = WhappyBlue.copy(alpha = .06f), shape = RoundedCornerShape(13.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Radio, null, tint = WhappyBlue); Text(episode.title, Modifier.weight(1f).padding(start = 9.dp), color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp); Icon(Icons.Rounded.PlayArrow, "Écouter", tint = WhappyBlue) }
                        }
                    }
                    if (member.phoneNumber.isNotBlank()) OutlinedButton(onClick = { calls?.start(member, false); selectedGroupMember = null }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Phone, null); Text("  Appeler") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedGroupMember = null }) { Text("Fermer") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showGroupMembers && conversation.isGroup) {
        AlertDialog(
            onDismissRequest = { showGroupMembers = false },
            icon = { UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 58.dp, shape = RoundedCornerShape(14.dp)) },
            title = { Text("${conversation.memberCount} membres", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(conversation.groupMembers, key = { "group-profile-${it.uid}" }) { member ->
                        val owner = member.uid == conversation.groupOwnerId
                        val administrator = owner || member.uid in conversation.groupAdminIds
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { showGroupMembers = false; selectedGroupMember = member },
                            color = if (administrator) WhappyBlue.copy(alpha = .06f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(member.photoUrl, member.displayName, 42.dp, shape = RoundedCornerShape(11.dp))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(if (member.uid == currentUserId) "${member.displayName} · Vous" else member.displayName, color = WhappyDark, fontWeight = FontWeight.Bold)
                                    Text(if (owner) "Créateur" else if (administrator) "Administrateur" else "Membre", color = if (administrator) WhappyBlue else WhappyMuted, fontSize = 10.sp)
                                }
                                if (isGroupAdministrator && !owner && member.uid != currentUserId) {
                                    IconButton(onClick = {
                                        groupMemberAction = "remove"
                                        selectedManagedMembers = setOf(member.uid)
                                        showGroupMembers = false
                                        showGroupMemberManager = true
                                    }) { Icon(Icons.Rounded.Delete, "Retirer du groupe", tint = Color(0xFFC62828)) }
                                }
                                Text("›", color = WhappyMuted, fontSize = 24.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupMembers = false }) { Text("Fermer") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showGroupMemberManager && conversation.isGroup && isGroupAdministrator) {
        val existingIds = conversation.groupMembers.map { it.uid }.toSet()
        val candidates = if (groupMemberAction == "add") {
            availableContacts.map { it.member }.filter { it.uid !in existingIds }.distinctBy { it.uid }
        } else {
            conversation.groupMembers.filter { it.uid != conversation.groupOwnerId && it.uid != currentUserId }
        }
        AlertDialog(
            onDismissRequest = { if (!groupBusy) showGroupMemberManager = false },
            icon = { Icon(if (groupMemberAction == "add") Icons.Rounded.PersonAdd else Icons.Rounded.Delete, null, tint = WhappyBlue) },
            title = { Text(if (groupMemberAction == "add") "Ajouter des membres" else "Retirer des membres", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                if (candidates.isEmpty()) Text(if (groupMemberAction == "add") "Tous vos contacts WAPI sont déjà dans ce groupe." else "Aucun membre ne peut être retiré.", color = WhappyMuted)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(candidates, key = { "manage-${groupMemberAction}-${it.uid}" }) { member ->
                        val selected = member.uid in selectedManagedMembers
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !groupBusy) { selectedManagedMembers = if (selected) selectedManagedMembers - member.uid else selectedManagedMembers + member.uid },
                            color = if (selected) WhappyBlue.copy(alpha = .08f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(member.photoUrl, member.displayName, 42.dp, shape = RoundedCornerShape(11.dp))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(member.displayName, color = WhappyDark, fontWeight = FontWeight.Bold); Text(member.phoneNumber.ifBlank { "Compte WAPI" }, color = WhappyMuted, fontSize = 10.sp) }
                                Checkbox(checked = selected, onCheckedChange = { checked -> selectedManagedMembers = if (checked) selectedManagedMembers + member.uid else selectedManagedMembers - member.uid }, enabled = !groupBusy)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = selectedManagedMembers.isNotEmpty() && !groupBusy, onClick = {
                    onManageGroupMembers(selectedManagedMembers.toList(), groupMemberAction)
                    showGroupMemberManager = false
                    selectedManagedMembers = emptySet()
                }) { Text(if (groupMemberAction == "add") "Ajouter" else "Retirer") }
            },
            dismissButton = { TextButton(enabled = !groupBusy, onClick = { showGroupMemberManager = false; selectedManagedMembers = emptySet() }) { Text("Annuler") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showGroupSettings && conversation.isGroup && isGroupAdministrator) {
        AlertDialog(
            onDismissRequest = { if (!groupBusy) showGroupSettings = false },
            icon = { Icon(Icons.Rounded.Lock, null, tint = WhappyBlue) },
            title = { Text("Permissions du groupe", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(groupDescriptionDraft, { groupDescriptionDraft = it.take(300) }, Modifier.fillMaxWidth(), enabled = !groupBusy, label = { Text("Description") }, supportingText = { Text("${groupDescriptionDraft.length}/300") }, minLines = 3, shape = RoundedCornerShape(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Modifier les informations", color = WhappyDark, fontWeight = FontWeight.Bold); Text("Autoriser les membres à changer le nom et la photo", color = WhappyMuted, fontSize = 10.sp) }
                        Switch(groupEditInfoDraft, { groupEditInfoDraft = it }, enabled = !groupBusy)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Mode annonces", color = WhappyDark, fontWeight = FontWeight.Bold); Text("Seuls les administrateurs peuvent envoyer", color = WhappyMuted, fontSize = 10.sp) }
                        Switch(groupAdminsOnlyDraft, { groupAdminsOnlyDraft = it }, enabled = !groupBusy)
                    }
                    Text("Chaque changement est ajouté à l’historique du groupe.", color = WhappyMuted, fontSize = 10.sp)
                }
            },
            confirmButton = { Button(enabled = !groupBusy, onClick = { onUpdateGroupSettings(groupDescriptionDraft, groupEditInfoDraft, groupAdminsOnlyDraft); showGroupSettings = false }) { Text("Enregistrer") } },
            dismissButton = { TextButton(enabled = !groupBusy, onClick = { showGroupSettings = false }) { Text("Annuler") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showGroupMedia && conversation.isGroup) {
        val sharedItems = messages.filter { it.kind in setOf("image", "video", "audio", "document") && !it.deleted }.sortedByDescending { it.createdAt }
        AlertDialog(
            onDismissRequest = { showGroupMedia = false },
            icon = { Icon(Icons.Rounded.GridView, null, tint = WhappyBlue) },
            title = { Text("Médias et documents", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                if (sharedItems.isEmpty()) Text("Aucun média partagé dans ce groupe.", color = WhappyMuted)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sharedItems, key = { "shared-${it.id}" }) { message ->
                        Surface(Modifier.fillMaxWidth().clickable { openWapiMedia(message) }, color = WhappyBlue.copy(alpha = .05f), shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when (message.kind) { "image" -> Icons.Rounded.Photo; "video" -> Icons.Rounded.Videocam; "audio" -> Icons.Rounded.AudioFile; else -> Icons.Rounded.AttachFile }, null, tint = WhappyBlue)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(message.mediaName.ifBlank { when (message.kind) { "image" -> "Photo WAPI"; "video" -> "Vidéo WAPI"; "audio" -> "Note vocale"; else -> "Document Waphsare" } }, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1); Text("${message.senderName.ifBlank { "Membre WAPI" }} · ${formatTime(message.createdAt)}", color = WhappyMuted, fontSize = 10.sp) }
                                Text("Ouvrir", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupMedia = false }) { Text("Fermer") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showGroupEditor && conversation.isGroup) {
        AlertDialog(
            onDismissRequest = { if (!groupBusy) showGroupEditor = false },
            icon = { Icon(Icons.Rounded.Groups, null, tint = WhappyBlue) },
            title = { Text("Informations du groupe", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UserAvatar(groupPhotoUri?.toString() ?: conversation.peer.photoUrl, groupNameDraft.ifBlank { conversation.peer.displayName }, 82.dp, Modifier.align(Alignment.CenterHorizontally))
                    OutlinedButton(
                        enabled = !groupBusy,
                        onClick = { groupPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Rounded.Photo, null)
                        Text(if (groupPhotoUri == null) "  Choisir dans la galerie" else "  Changer la photo sélectionnée")
                    }
                    if (conversation.peer.photoUrl.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(enabled = !groupBusy, checked = removeGroupPhoto, onCheckedChange = { removeGroupPhoto = it; if (it) groupPhotoUri = null })
                            Text("Supprimer la photo actuelle", color = WhappyMuted, fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = groupNameDraft,
                        onValueChange = { groupNameDraft = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !groupBusy,
                        label = { Text("Nom du groupe") },
                        supportingText = { Text("${groupNameDraft.length}/80") },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                    )
                    Surface(color = WhappyBlue.copy(alpha = .06f), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Notifications, null, tint = WhappyBlue, modifier = Modifier.size(18.dp))
                            Text("Tous les membres seront informés du changement.", Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    if (groupUpdate.groupId == conversation.id && groupUpdate.status == "failed") {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                            Text(
                                groupUpdate.message.ifBlank { "La photo n’a pas été enregistrée. Réessayez." },
                                Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val changed = groupNameDraft.trim() != conversation.peer.displayName || groupPhotoUri != null || removeGroupPhoto
                Button(
                    enabled = !groupBusy && changed && groupNameDraft.trim().length in 2..80,
                    onClick = {
                        groupSavePending = true
                        onUpdateGroup(groupNameDraft.trim(), groupPhotoUri, selectedGroupPhotoContentType, removeGroupPhoto)
                    },
                ) {
                    if (groupBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Enregistrer")
                }
            },
            dismissButton = { TextButton(enabled = !groupBusy, onClick = { showGroupEditor = false }) { Text("Annuler") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    groupPhotoToCrop?.let { source ->
        WapiSquareCropDialog(
            source = source,
            title = "Recadrer la photo du groupe",
            onDismiss = { groupPhotoToCrop = null },
            onConfirm = { cropped ->
                groupPhotoUri = cropped
                groupPhotoToCrop = null
                removeGroupPhoto = false
            },
        )
    }
    if (showGroupAdministrators && conversation.isGroup) {
        AlertDialog(
            onDismissRequest = { showGroupAdministrators = false },
            icon = { Icon(Icons.Rounded.Groups, null, tint = WhappyBlue) },
            title = { Text("Administrateurs du groupe", color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Le créateur reste propriétaire. Il peut nommer ou retirer d’autres administrateurs.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    conversation.groupMembers.forEach { member ->
                        val owner = member.uid == conversation.groupOwnerId
                        val administrator = owner || member.uid in conversation.groupAdminIds
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (administrator) WhappyBlue.copy(alpha = .07f) else Color.Transparent).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(member.photoUrl, member.displayName, 38.dp, shape = RoundedCornerShape(10.dp))
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(member.displayName, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(if (owner) "Créateur · administrateur permanent" else if (administrator) "Administrateur" else "Membre", color = if (administrator) WhappyBlue else WhappyMuted, fontSize = 9.sp)
                            }
                            if (!owner && member.uid != currentUserId) Checkbox(
                                checked = administrator,
                                onCheckedChange = { onSetGroupAdministrator(member.uid, it) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupAdministrators = false }) { Text("Terminé") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (groupCallOpen && conversation.isGroup) {
        WapiGroupCallDialog(
            group = conversation,
            invitation = groupCallInvitation,
            requestedVideo = groupCallVideo,
            canEnd = conversation.groupOwnerId == currentUserId || currentUserId in conversation.groupAdminIds,
            radioMode = groupCallRadio,
            onDismiss = { groupCallOpen = false; groupCallInvitation = null; groupCallRadio = false },
        )
    }
    selectedMessage?.let { message ->
        val mine = message.senderId == currentUserId
        val messageActions = remember(message.text) { detectMessageActions(message.text) }
        Dialog(onDismissRequest = { selectedMessage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth(.86f), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = Color(0xFFF2F9FF), shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp, border = androidx.compose.foundation.BorderStroke(1.dp, WapiChatAccent.copy(alpha = .16f))) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        listOf("❤️", "👍", "😂", "😮", "🙏").forEach { emoji ->
                            TextButton(onClick = { onReact(message, emoji); selectedMessage = null }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)) { Text(emoji, fontSize = 21.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Surface(color = WapiActionPanel, shape = RoundedCornerShape(18.dp), shadowElevation = 10.dp) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            WapiMessageAction("↩", "Répondre", Modifier.weight(1f)) { replyTo = message; editingMessage = null; selectedMessage = null }
                            WapiMessageAction("▣", "Copier", Modifier.weight(1f), enabled = message.text.isNotBlank()) {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message WAPI", message.text))
                                selectedMessage = null
                            }
                            WapiMessageAction("↗", "Transférer", Modifier.weight(1f)) { forwardWhappyMessage(context, message); selectedMessage = null }
                            WapiMessageAction("A", "Lingwap", Modifier.weight(1f), enabled = message.text.isNotBlank()) {
                                lingwapMessage = message
                                lingwapTranslation = null
                                lingwapError = null
                                selectedMessage = null
                            }
                            WapiMessageAction("⋯", "Plus", Modifier.weight(1f)) {
                                messageActions.firstOrNull()?.let(::handleMessageAction)
                                selectedMessage = null
                            }
                        }
                        if (mine) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .10f)))
                            Row(Modifier.fillMaxWidth()) {
                                if (message.kind == "text") WapiMessageAction("✎", "Modifier", Modifier.weight(1f)) { editingMessage = message; replyTo = null; text = message.text; selectedMessage = null }
                                WapiMessageAction("⌫", "Supprimer", Modifier.weight(1f), destructive = true) { onDelete(message); selectedMessage = null }
                                Spacer(Modifier.weight(if (message.kind == "text") 2f else 3f))
                            }
                        }
                    }
                }
                Text("Appui long sur un message", Modifier.padding(top = 9.dp), color = Color.White.copy(alpha = .86f), fontSize = 10.sp)
            }
        }
    }
    lingwapMessage?.let { message ->
        val interfaceLanguage = LocalWhappyLanguage.current
        AlertDialog(
            onDismissRequest = { if (!lingwapBusy) lingwapMessage = null },
            icon = { Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Language, null, tint = Color.White) } },
            title = { Text(t("Lingwap", "Lingwap", "Lingwap"), color = WhappyDark, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(message.text, color = WhappyMuted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(t("Langue de traduction du message", "Message translation language", "Lokota ya kobongola message"), color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val selectedLanguage = wapiTranslationLanguages.firstOrNull { it.code == lingwapTarget }
                        ?: wapiTranslationLanguages.first()
                    Box {
                        OutlinedButton(
                            onClick = { lingwapLanguageMenuOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = WhappyBlue.copy(alpha = .07f)),
                        ) {
                            Icon(Icons.Rounded.Language, null, modifier = Modifier.size(16.dp), tint = WhappyBlue)
                            Text("  ${selectedLanguage.nativeLabel} · ${selectedLanguage.label}", color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = lingwapLanguageMenuOpen,
                            onDismissRequest = { lingwapLanguageMenuOpen = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            wapiTranslationLanguages.forEach { language ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(language.nativeLabel, color = WhappyDark, fontWeight = FontWeight.SemiBold)
                                            Text(language.label, color = WhappyMuted, fontSize = 11.sp)
                                        }
                                    },
                                    trailingIcon = {
                                        if (language.code == lingwapTarget) Icon(Icons.Rounded.Check, null, tint = WhappyBlue, modifier = Modifier.size(17.dp))
                                    },
                                    onClick = {
                                        lingwapTarget = language.code
                                        lingwapTranslation = null
                                        lingwapError = null
                                        lingwapLanguageMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    if (lingwapBusy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  ${t("Traduction sécurisée en cours…", "Secure translation in progress…", "Bobongoli ya libateli ezali kosalema…")}", color = WhappyMuted, fontSize = 11.sp) }
                    lingwapTranslation?.let { Text(it, color = WhappyDark, fontWeight = FontWeight.SemiBold) }
                    lingwapError?.let { Text(it, color = Color(0xFFB42318), fontSize = 11.sp, lineHeight = 16.sp) }
                    Text(t("La langue de l’application se règle dans Profil > Paramètres > Langue. Lingwap traduit uniquement ce message via le relais sécurisé WAPI.", "The app language is set in Profile > Settings > Language. Lingwap translates this message through WAPI’s secure relay.", "Lokota ya application ebongisamaka na Profil > Paramètres > Lokota. Lingwap ebongolaka kaka message oyo na nzela ya relais ya libateli ya WAPI."), color = WhappyMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = !lingwapBusy,
                    onClick = {
                        lingwapBusy = true
                        lingwapError = null
                        chatScope.launch {
                            runCatching {
                                @Suppress("UNCHECKED_CAST")
                                val payload = FirebaseFunctions.getInstance("europe-west1")
                                    .getHttpsCallable("lingwapTranslateText")
                                    .call(mapOf("text" to message.text, "sourceLanguage" to "auto", "targetLanguage" to lingwapTarget))
                                    .await().data as? Map<String, Any?> ?: error("Réponse Lingwap invalide.")
                                payload["translation"]?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                                    ?: error("Réponse Lingwap invalide.")
                            }.onSuccess { lingwapTranslation = it }
                                .onFailure { failure ->
                                    val code = (failure as? FirebaseFunctionsException)?.code?.name.orEmpty()
                                    lingwapError = when (code) {
                                        else -> lingwapErrorText(interfaceLanguage, code)
                                    }
                                }
                            lingwapBusy = false
                        }
                    },
                ) { Text(if (lingwapBusy) t("Traduction…", "Translating…", "Bobongoli…") else t("Traduire", "Translate", "Bongola")) }
            },
            dismissButton = { TextButton(enabled = !lingwapBusy, onClick = { lingwapMessage = null }) { Text(t("Fermer", "Close", "Kanga")) } },
            containerColor = Color.White,
            shape = RoundedCornerShape(25.dp),
        )
    }
}

@Composable
private fun WapiMessageAction(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier.height(67.dp).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (!enabled) Color.White.copy(alpha = .28f) else if (destructive) Color(0xFFFF8A80) else Color.White
        Text(symbol, color = color, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Text(label, Modifier.padding(top = 3.dp), color = color, fontSize = 10.sp)
    }
}

private fun forwardWhappyMessage(context: Context, message: WhappyMessage) {
    val value = message.text.ifBlank { message.mediaUrl.ifBlank { message.mediaName.ifBlank { "Message WAPI" } } }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, value)
    }
    context.startActivity(Intent.createChooser(intent, "Transférer le message…"))
}

@Composable
private fun MessageLinkText(
    text: String,
    mine: Boolean,
    actions: List<MessageAction>,
    onAction: (MessageAction) -> Unit,
) {
    val bodyColor = if (mine) Color.White else WhappyInk
    val linkColor = if (mine) Color.White else Color(0xFF1769AA)
    if (actions.isEmpty()) {
        Text(text, color = bodyColor, lineHeight = 20.sp)
        return
    }
    val annotated = buildAnnotatedString {
        append(text)
        actions.forEach { action ->
            if (action.start >= action.end || action.start < 0 || action.end > text.length) return@forEach
            addStringAnnotation("message-action", action.target, action.start, action.end)
            addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold,
                ),
                action.start,
                action.end,
            )
        }
    }
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        text = annotated,
        modifier = Modifier.pointerInput(annotated, actions) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                val clicked = annotated.getStringAnnotations("message-action", offset, offset).firstOrNull()
                if (clicked != null) {
                    val action = actions.firstOrNull { it.start <= offset && offset < it.end && it.target == clicked.item }
                    if (action != null) onAction(action)
                }
            }
        },
        style = TextStyle(color = bodyColor, lineHeight = 20.sp),
        onTextLayout = { layoutResult = it },
    )
}

private fun openMessageAction(uriHandler: androidx.compose.ui.platform.UriHandler, action: MessageAction) {
    when (action.type) {
        MessageActionType.Link -> uriHandler.openUri(action.target)
        MessageActionType.Phone -> uriHandler.openUri("tel:${action.target}")
    }
}

private fun detectMessageActions(text: String): List<MessageAction> {
    if (text.isBlank()) return emptyList()
    val actions = mutableListOf<MessageAction>()
    val covered = mutableListOf<IntRange>()
    val matcherText = text

    val linkMatcher = Patterns.WEB_URL.matcher(matcherText)
    while (linkMatcher.find()) {
        val range = sanitizeMessageRange(linkMatcher.start(), linkMatcher.end(), matcherText)
        if (range.first > range.last) continue
        if (covered.any { range.first <= it.last && range.last + 1 >= it.first }) continue
        val raw = matcherText.substring(range)
        val target = normalizeMessageLink(raw) ?: continue
        val label = target.removePrefix("https://").removePrefix("http://").take(38)
        actions.add(MessageAction(label, target, MessageActionType.Link, range.first, range.last + 1))
        covered.add(range)
    }

    val phoneMatcher = Patterns.PHONE.matcher(matcherText)
    while (phoneMatcher.find()) {
        val range = phoneMatcher.start() until phoneMatcher.end()
        if (range.first > range.last) continue
        if (covered.any { range.first <= it.last && range.last >= it.first }) continue
        val raw = matcherText.substring(range)
        val normalized = PhoneNumberFormatter.lookupCandidates(raw).firstOrNull() ?: continue
        actions.add(MessageAction(normalized, normalized, MessageActionType.Phone, range.first, range.last + 1))
        covered.add(range)
    }
    return actions
}

private fun normalizeMessageLink(value: String): String? {
    val raw = value.trim().trimEnd(')', ']', '}', ',', ';', '.', ':', '!', '?')
    val parsed = runCatching { android.net.Uri.parse(raw) }.getOrNull() ?: return null
    parsed.scheme?.lowercase()?.let { scheme ->
        if (scheme == "whappy") return raw
        if (scheme == "http" || scheme == "https") {
            val host = parsed.host?.lowercase()
            if (host == "whappy.chat" || host == "www.whappy.chat") {
                return raw.replace(Regex("^https?://(?:www\\.)?whappy\\.chat/"), "whappy://")
            }
            return raw
        }
        return when (scheme) {
            "mailto", "sms", "tel", "facetime", "whatsapp" -> raw
            else -> null
        }
    }
    return when {
        raw.startsWith("www.") -> "https://$raw"
        raw.contains(".") && !raw.contains(" ") -> "https://$raw"
        else -> null
    }
}

private fun sanitizeMessageRange(start: Int, end: Int, text: String): IntRange {
    var safeStart = start
    var safeEnd = end
    while (safeStart < safeEnd && safeEnd <= text.length && text[safeEnd - 1] in setOf(')', ']', '}', ',', ';', '.', ':', '!', '?')) {
        safeEnd -= 1
    }
    return safeStart until safeEnd
}

@Composable
private fun MediaMessageRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, mine: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (mine) Color.White else WapiChatAccent, modifier = Modifier.size(28.dp))
        Text(label, Modifier.padding(start = 9.dp), color = if (mine) Color.White else WhappyInk, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The recipient opens the original object; Waphsare exposes its integrity metadata. */
@Composable
private fun WaphsareDocumentMessage(
    source: String,
    name: String,
    bytes: Long,
    sha256: String,
    mine: Boolean,
    onOpen: () -> Unit,
) {
    val foreground = if (mine) Color.White else WhappyInk
    val subdued = if (mine) Color.White.copy(alpha = .76f) else WhappyMuted
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = source.isNotBlank(), onClick = onOpen),
        shape = RoundedCornerShape(13.dp),
        color = if (mine) Color.White.copy(alpha = .14f) else WapiChatAccent.copy(alpha = .07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (mine) Color.White.copy(alpha = .22f) else WapiChatAccent.copy(alpha = .18f)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (mine) Color.White.copy(alpha = .18f) else WapiChatAccent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AttachFile, null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                Text(name.ifBlank { "Document Waphsare" }, color = foreground, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Original conservé · ${if (bytes > 0L) formatStorageBytes(bytes) else "taille non disponible"}", Modifier.padding(top = 2.dp), color = subdued, fontSize = 10.sp)
                if (sha256.length == 64) Text("Intégrité vérifiable", Modifier.padding(top = 2.dp), color = if (mine) Color.White.copy(alpha = .9f) else WapiChatAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ViewOnceMediaRow(kind: String, mine: Boolean, opened: Boolean, onOpen: (() -> Unit)?) {
    val foreground = if (mine) Color.White else WhappyInk
    val muted = if (mine) Color.White.copy(alpha = .76f) else WhappyMuted
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onOpen != null) { onOpen?.invoke() },
        shape = RoundedCornerShape(14.dp),
        color = if (mine) Color.White.copy(alpha = .15f) else WapiSoftBlue,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (mine) Color.White.copy(alpha = .22f) else WhappyBlue.copy(alpha = .14f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (mine) Color.White.copy(alpha = .18f) else WhappyBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (opened) "Média à vue unique ouvert" else "Média à vue unique", color = foreground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    when {
                        mine && opened -> "Ouvert par le destinataire"
                        mine -> "En attente d’ouverture"
                        opened -> "Ce média n’est plus disponible dans cette discussion"
                        else -> "Touchez pour ouvrir une seule fois"
                    },
                    Modifier.padding(top = 2.dp), color = muted, fontSize = 10.sp,
                )
            }
            if (!opened && !mine) Text("OUVRIR", color = if (mine) Color.White else WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            else Text(if (kind == "audio") "AUDIO" else if (kind == "video") "VIDÉO" else "PHOTO", color = muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun formatVoiceDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, safe / 60, safe % 60)
}

private fun voiceMessageUri(source: String): Uri? {
    if (source.isBlank()) return null
    return if (source.startsWith('/')) Uri.fromFile(File(source)) else Uri.parse(source)
}

/** Native, in-place playback keeps a voice note inside the conversation instead of opening a browser. */
@Composable
private fun VoiceNoteMessage(
    source: String,
    durationSeconds: Int,
    mine: Boolean,
    modifier: Modifier = Modifier,
    title: String = "Note vocale",
) {
    val context = LocalContext.current
    var player by remember(source) { mutableStateOf<MediaPlayer?>(null) }
    var playerPrepared by remember(source) { mutableStateOf(false) }
    var playing by remember(source) { mutableStateOf(false) }
    var preparing by remember(source) { mutableStateOf(false) }
    var positionMillis by remember(source) { mutableIntStateOf(0) }
    var actualDurationMillis by remember(source) { mutableIntStateOf(0) }
    var playbackError by remember(source) { mutableStateOf(false) }
    var playbackSpeed by remember(source) { mutableFloatStateOf(1f) }
    var scrubFraction by remember(source) { mutableStateOf<Float?>(null) }
    val totalMillis = (actualDurationMillis.takeIf { it > 0 } ?: durationSeconds.coerceAtLeast(1) * 1_000).coerceAtLeast(1_000)
    val liveProgress = (positionMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    val displayedProgress = scrubFraction ?: liveProgress
    val displayedPositionMillis = (displayedProgress * totalMillis).toInt().coerceIn(0, totalMillis)
    val foreground = if (mine) Color.White else WhappyDark
    val secondary = if (mine) Color.White.copy(alpha = .76f) else WhappyMuted
    val controlBackground = if (mine) Color.White.copy(alpha = .19f) else WhappyBlue.copy(alpha = .1f)

    fun applySpeed(target: MediaPlayer, speed: Float) {
        runCatching {
            target.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
        }
    }

    fun preparePlayback(autoStart: Boolean, initialSeekFraction: Float? = null) {
        if (preparing || playerPrepared) return
        val uri = voiceMessageUri(source) ?: run {
            playbackError = true
            return
        }
        preparing = true
        playbackError = false
        runCatching {
            @Suppress("DEPRECATION")
            MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(context, uri)
                setOnPreparedListener { prepared ->
                    actualDurationMillis = prepared.duration.coerceAtLeast(1_000)
                    playerPrepared = true
                    preparing = false
                    applySpeed(prepared, playbackSpeed)
                    initialSeekFraction?.let { fraction ->
                        val target = (prepared.duration * fraction.coerceIn(0f, 1f)).toInt()
                        prepared.seekTo(target)
                        positionMillis = target
                    }
                    if (autoStart) {
                        prepared.start()
                        playing = true
                    }
                }
                setOnSeekCompleteListener { sought ->
                    positionMillis = runCatching { sought.currentPosition }.getOrDefault(positionMillis)
                }
                setOnCompletionListener { completed ->
                    playing = false
                    positionMillis = 0
                    scrubFraction = null
                    runCatching { completed.seekTo(0) }
                }
                setOnErrorListener { failed, _, _ ->
                    playing = false
                    preparing = false
                    playerPrepared = false
                    playbackError = true
                    if (player === failed) player = null
                    failed.release()
                    true
                }
                prepareAsync()
            }
        }.onSuccess { created -> player = created }
            .onFailure {
                preparing = false
                playerPrepared = false
                playbackError = true
            }
    }

    fun togglePlayback() {
        val current = player
        if (current != null && playerPrepared) {
            if (current.isPlaying) {
                current.pause()
                playing = false
            } else {
                runCatching {
                    applySpeed(current, playbackSpeed)
                    current.start()
                }
                    .onSuccess { playing = true }
                    .onFailure { playbackError = true }
            }
            return
        }
        preparePlayback(autoStart = true)
    }

    fun seekToFraction(fraction: Float) {
        val safeFraction = fraction.coerceIn(0f, 1f)
        val current = player
        if (current != null && playerPrepared) {
            val duration = runCatching { current.duration }.getOrDefault(totalMillis).coerceAtLeast(1_000)
            val target = (duration * safeFraction).toInt().coerceIn(0, duration)
            runCatching { current.seekTo(target) }
                .onSuccess { positionMillis = target }
                .onFailure { playbackError = true }
        } else {
            preparePlayback(autoStart = false, initialSeekFraction = safeFraction)
        }
    }

    fun cyclePlaybackSpeed() {
        val next = when (playbackSpeed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        playbackSpeed = next
        player?.takeIf { playerPrepared }?.let { applySpeed(it, next) }
    }

    LaunchedEffect(playing, player) {
        while (playing) {
            positionMillis = runCatching { player?.currentPosition ?: 0 }.getOrDefault(positionMillis)
            delay(150)
        }
    }
    DisposableEffect(source) {
        onDispose {
            runCatching { player?.release() }
        }
    }

    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(controlBackground).padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = ::togglePlayback,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (mine) Color.White.copy(alpha = .19f) else WhappyBlue, contentColor = Color.White),
        ) {
            if (preparing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Mettre en pause" else "Lire la note vocale", modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (playbackError) "Lecture indisponible" else title, color = foreground, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Surface(
                    color = if (mine) Color.White.copy(alpha = .17f) else WhappyBlue.copy(alpha = .12f),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(enabled = !preparing, onClick = ::cyclePlaybackSpeed),
                ) {
                    Text(
                        when (playbackSpeed) { 1.5f -> "1,5×"; 2f -> "2×"; else -> "1×" },
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = foreground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Slider(
                value = displayedProgress,
                onValueChange = { scrubFraction = it.coerceIn(0f, 1f) },
                onValueChangeFinished = {
                    val destination = scrubFraction ?: liveProgress
                    scrubFraction = null
                    seekToFraction(destination)
                },
                enabled = source.isNotBlank() && !preparing && !playbackError,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (mine) Color.White else WhappyBlue,
                    activeTrackColor = if (mine) Color.White else WhappyBlue,
                    inactiveTrackColor = if (mine) Color.White.copy(alpha = .28f) else WhappyBlue.copy(alpha = .16f),
                    disabledThumbColor = secondary,
                    disabledActiveTrackColor = secondary.copy(alpha = .5f),
                    disabledInactiveTrackColor = secondary.copy(alpha = .22f),
                ),
            )
            Text(
                "${formatVoiceDuration(displayedPositionMillis / 1_000)} / ${formatVoiceDuration(totalMillis / 1_000)} · glissez pour avancer",
                color = secondary,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun InlineImageMessage(source: String, label: String, mine: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val cachedBitmap = remember(source) { WapiBitmapMemoryCache.get(source) }
    var bitmap by remember(source) { mutableStateOf(cachedBitmap) }
    var loading by remember(source) { mutableStateOf(cachedBitmap == null && source.isNotBlank()) }
    LaunchedEffect(source) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = WapiStableImageLoader.load(context, source)
        loading = false
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 92.dp, max = 280.dp).clip(RoundedCornerShape(14.dp)).background(if (mine) Color.White.copy(alpha = .35f) else WhappySurface).clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap!!, contentDescription = label, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Crop)
            loading -> CircularProgressIndicator(Modifier.size(25.dp), color = WapiChatAccent, strokeWidth = 2.dp)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) { Icon(Icons.Rounded.BrokenImage, null, tint = WhappyMuted, modifier = Modifier.size(28.dp)); Text("Photo indisponible · toucher pour réessayer", color = WhappyMuted, fontSize = 10.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun StoryMediaPreview(source: String, kind: String, label: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    if (kind == "image") {
        val cachedBitmap = remember(source) { WapiBitmapMemoryCache.get(source) }
        var bitmap by remember(source) { mutableStateOf(cachedBitmap) }
        var loading by remember(source) { mutableStateOf(cachedBitmap == null && source.isNotBlank()) }
        LaunchedEffect(source) {
            if (bitmap != null) return@LaunchedEffect
            bitmap = WapiStableImageLoader.load(context, source)
            loading = false
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 140.dp, max = 330.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(bitmap!!, contentDescription = label.ifBlank { "Image Story" }, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                loading -> CircularProgressIndicator(color = WhappyBlue, strokeWidth = 2.dp)
                else -> Text("Image Story indisponible · toucher pour ouvrir", color = WhappyMuted, fontSize = 11.sp)
            }
        }
    } else {
        Surface(Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onOpen), color = WhappyBlue.copy(alpha = .06f), shape = RoundedCornerShape(15.dp)) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (kind == "audio") Icons.Rounded.AudioFile else Icons.Rounded.Movie, null, tint = WhappyBlue)
                Column(Modifier.padding(start = 10.dp)) { Text(if (kind == "audio") "Podcast WAPI" else "Vidéo WAPI", color = WhappyDark, fontWeight = FontWeight.Bold); Text("Appuyez pour ouvrir", color = WhappyMuted, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun ImageZoomViewer(
    imageSource: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val cachedBitmap = remember(imageSource) { WapiBitmapMemoryCache.get(imageSource) }
    var bitmap by remember(imageSource) { mutableStateOf(cachedBitmap) }
    var loading by remember(imageSource) { mutableStateOf(cachedBitmap == null && imageSource.isNotBlank()) }
    var failed by remember(imageSource) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(imageSource) {
        loading = true
        failed = false
        bitmap = WapiStableImageLoader.load(context, imageSource)
        loading = false
        failed = bitmap == null
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { container = it },
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(imageSource, container.width, container.height) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val updatedScale = (scale * zoom).coerceIn(1f, 5f)
                                    val maxX = max(0f, container.width * (updatedScale - 1f) / 2f)
                                    val maxY = max(0f, container.height * (updatedScale - 1f) / 2f)
                                    scale = updatedScale
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                }
                            }
                            .pointerInput(imageSource) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.1f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            scale = 2.5f
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                )
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
                loading -> CircularProgressIndicator(color = Color.White)
                failed -> Text("Impossible de charger l’image", color = Color.White, fontWeight = FontWeight.Black)
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun EmojiTray(onEmoji: (String) -> Unit, onClose: () -> Unit) {
    val emojiGroups = remember {
        linkedMapOf(
            "🙂" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫡 🤫 🤭 🫢 🫣 😶 🫠 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕",
            "👋" to "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🙏 ✍️ 💅 🤳 💪 🦾 🫶 🫱 🫲 🤝 🙇 💁 🙋 🧏 🤦 🤷 🙎 🙍 💇 💆 🧖 💃 🕺 🕴️ 👯 🧑‍🤝‍🧑 👀 🧠 🫀 🫁",
            "❤️" to "❤️ 🩷 🩵 💙 💚 💛 🧡 💜 🖤 🩶 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 🔥 💯 ✨ ⭐ 🌟 💫 ⚡ 🎉 🎊 ✅ ☑️ ☀️ 🌙 🌈 🌍",
            "🐾" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🦍 🦧 🐔 🐧 🐦 🦉 🦅 🦋 🐝 🐢 🐍 🦎 🐙 🦑 🐠 🐟 🐬 🐳 🦈 🐘 🦒 🦓 🐆 🦜 🌿 🌺 🌻 🌴 🍀 🌊",
            "🍜" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥑 🍅 🥕 🌽 🥔 🍞 🥐 🧀 🍗 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🍜 🍝 🍣 🍤 🍙 🍛 🍰 🧁 🍫 🍿 ☕ 🍵 🥤 🧃 🍾 🍷",
            "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🥏 🏓 🏸 🥊 🥋 🛹 🛼 ⛸️ 🎿 🏆 🥇 🥈 🥉 🎮 🕹️ 🎲 ♟️ 🃏 🎯 🎳 🎸 🎹 🥁 🎤 🎧 🎬 📸 🎨",
            "🚘" to "🚗 🚕 🚌 🚎 🏎️ 🚓 🚑 🚒 🚚 🚜 🛵 🚲 🛴 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🛳️ 🚢 🚉 🗺️ 🗽 🏠 🏖️ 🏙️ 🏟️ 📍",
            "💼" to "📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💼 📞 ☎️ 🎥 📹 🎙️ 💬 📎 📄 📁 🗂️ 🧾 💳 💰 💸 🛍️ 🎁 🔒 🔑 🔔 ⚙️ 🛠️ 📈 📊 📉 🧮 🔍 💡 🧑‍💻",
            "🔣" to "✅ ❌ ⭕ ❗ ❓ ‼️ ⁉️ ♻️ ⚠️ 🚫 🔞 🔜 🔝 🔙 🔚 ©️ ®️ ™️ #️⃣ *️⃣ 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ ➕ ➖ ✖️ ➗ =️⃣ ♾️",
            "🌍" to "🇨🇬 🇨🇩 🇫🇷 🇬🇦 🇨🇲 🇳🇬 🇿🇦 🇺🇸 🇨🇦 🇧🇷 🇦🇪 🇸🇦 🇨🇳 🇯🇵 🇰🇷 🇮🇳 🇹🇷 🇮🇹 🇪🇸 🇵🇹 🇩🇪 🇳🇱 🇬🇧 🇲🇦 🇪🇬 🇰🇪 🇸🇳 🇦🇴",
        ).mapValues { (_, value) -> value.split(" ") }
    }
    var selectedGroup by rememberSaveable { mutableStateOf("🙂") }
    var recentEmojis by remember { mutableStateOf(emptyList<String>()) }
    val emojis = if (selectedGroup == "🕘") recentEmojis else emojiGroups[selectedGroup].orEmpty()
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EMOJIS", Modifier.weight(1f), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("Clavier système : tous les emoji", color = WhappyMuted, fontSize = 9.sp)
            TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("Fermer", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (listOf("🕘") + emojiGroups.keys).forEach { group ->
                TextButton(onClick = { selectedGroup = group }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp), colors = ButtonDefaults.textButtonColors(contentColor = if (selectedGroup == group) WhappyBlue else WhappyMuted)) { Text(group, fontSize = 17.sp) }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            emojis.forEach { emoji ->
                TextButton(
                    onClick = {
                        recentEmojis = listOf(emoji) + recentEmojis.filterNot { it == emoji }.take(23)
                        onEmoji(emoji)
                    },
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(emoji, fontSize = 24.sp) }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun createVoiceRecorder(context: Context): Pair<MediaRecorder, File> {
    val file = File(WapiMediaStore.cacheDirectory(context), "whappy-voice-${System.currentTimeMillis()}.m4a")
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    recorder.setAudioChannels(1)
    recorder.setAudioEncodingBitRate(128_000)
    recorder.setAudioSamplingRate(48_000)
    recorder.setMaxDuration(600_000)
    recorder.setOutputFile(file.absolutePath)
    recorder.prepare()
    recorder.start()
    return recorder to file
}

private fun displayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) return it.getString(0)?.take(120) ?: "photo-whappy.jpg" }
    return uri.lastPathSegment?.takeLast(120) ?: "photo-whappy.jpg"
}

@Composable
private fun MarketScreen(
    listings: List<WhappyListing>,
    businessPages: List<WhappyBusinessPage>,
    preview: Boolean,
    busy: Boolean,
    accountDisplayName: String,
    accountPhotoUrl: String,
    onPublish: (String, String, String, String) -> Unit,
    onContactBusiness: (WhappyBusinessPage) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_consumer") }
    var search by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var restaurantCity by rememberSaveable { mutableStateOf(prefs.getString("market_restaurant_city", "Brazzaville").orEmpty()) }
    var localItems by remember { mutableStateOf(emptyList<WhappyListing>()) }
    var selected by remember { mutableStateOf<WhappyListing?>(null) }
    var selectedRestaurant by remember { mutableStateOf<WhappyBusinessPage?>(null) }
    var showingCart by rememberSaveable { mutableStateOf(false) }
    var showingOrders by rememberSaveable { mutableStateOf(false) }
    var delivery by rememberSaveable { mutableStateOf("") }
    var marketFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var savedIds by remember { mutableStateOf(prefs.getStringSet("market_favorites", emptySet()).orEmpty().toSet()) }
    var cart by remember {
        mutableStateOf(prefs.getString("market_cart", "").orEmpty().split(";").mapNotNull { token ->
            val pieces = token.split("=", limit = 2); val count = pieces.getOrNull(1)?.toIntOrNull(); if (pieces.firstOrNull().isNullOrBlank() || count == null || count <= 0) null else pieces[0] to count
        }.toMap())
    }
    var orders by remember { mutableStateOf(prefs.getStringSet("market_orders", emptySet()).orEmpty().toList().sortedDescending()) }
    val products = (localItems + listings).filter { SearchNormalizer.matches(search, it.title, it.seller, it.place) }
    val restaurants = businessPages.filter { page ->
        page.category.contains("restaurant", ignoreCase = true)
            || page.category.contains("restauration", ignoreCase = true)
            || page.category.contains("café", ignoreCase = true)
            || page.category.contains("food", ignoreCase = true)
    }.filter { page ->
        restaurantCity.isBlank() || page.city.contains(restaurantCity.trim(), ignoreCase = true)
    }.filter { page -> SearchNormalizer.matches(search, page.name, page.category, page.city, page.bio) }

    fun saveCart(next: Map<String, Int>) {
        cart = next.filterValues { it > 0 }
        prefs.edit().putString("market_cart", cart.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(WhappyAurora).padding(20.dp)) {
                Column(Modifier.padding(end = 46.dp)) {
                    Text("MARCHÉ WAPI", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("Acheter local,\nvendre avec style.", Modifier.padding(top = 7.dp), color = Color.White, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
                    Text("Des annonces claires, vos favoris et vos commandes au même endroit.", Modifier.padding(top = 7.dp), color = Color.White.copy(alpha = .80f), fontSize = 11.sp, lineHeight = 16.sp)
                    Button(onClick = { creating = true }, Modifier.padding(top = 13.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue)) { Icon(Icons.Rounded.Add, null); Text("Publier une annonce", Modifier.padding(start = 5.dp), fontWeight = FontWeight.Bold) }
                }
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { showingOrders = true }) { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, "Mes commandes", tint = Color.White) }
                    Box(contentAlignment = Alignment.TopEnd) { IconButton(onClick = { showingCart = true }) { Icon(Icons.Rounded.ShoppingCart, "Panier", tint = Color.White) }; if (cart.values.sum() > 0) Box(Modifier.size(17.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text(cart.values.sum().coerceAtMost(9).toString(), color = WhappyBlue, fontSize = 8.sp, fontWeight = FontWeight.Black) } }
                }
            }
        }
        item { WapiPublicSaleRoomsRail() }
        item { Text("Annonces près de vous", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Black) }
        marketFeedback?.let { value -> item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Text(value, Modifier.padding(13.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Rechercher un produit ou une boutique") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true) }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .13f)),
            ) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Restaurants Business WAPI", color = WhappyDark, fontWeight = FontWeight.Black)
                            Text("Établissements déclarés par leurs propriétaires", color = WhappyMuted, fontSize = 10.sp)
                        }
                        Text("${restaurants.size}", color = WhappyBlue, fontWeight = FontWeight.Black)
                    }
                    OutlinedTextField(
                        value = restaurantCity,
                        onValueChange = { value -> restaurantCity = value.take(60); prefs.edit().putString("market_restaurant_city", value.take(60)).apply() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("Ville recherchée") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                    )
                    if (restaurants.isEmpty()) {
                        Text("Aucun restaurant Business WAPI déclaré dans cette ville pour le moment.", Modifier.padding(top = 10.dp), color = WhappyMuted, fontSize = 11.sp)
                    } else {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            restaurants.take(8).forEach { page ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedRestaurant = page },
                                    shape = RoundedCornerShape(13.dp),
                                    color = WhappyBlue.copy(alpha = .045f),
                                ) {
                                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                            Text(page.name, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${page.category} · ${page.city}", color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Icon(Icons.Rounded.ChatBubble, null, tint = WhappyBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (products.isEmpty()) item { EmptyState("Aucune annonce", "Publiez la première offre de cette catégorie.") }
        items(products, key = { it.id }) { product ->
            Card(Modifier.clickable { selected = product }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(product.title, fontWeight = FontWeight.Bold, color = WhappyDark); Text(product.price, Modifier.padding(top = 5.dp), color = WhappyBlue, fontWeight = FontWeight.Bold); Text("${product.place} · ${product.seller}", Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp); if(product.mode=="troc") Text("TROC ACCEPTÉ", Modifier.padding(top=5.dp), color=WhappyBlue, fontSize=9.sp, fontWeight=FontWeight.Black) }
                    IconButton(onClick = { savedIds = if (product.id in savedIds) savedIds - product.id else savedIds + product.id; prefs.edit().putStringSet("market_favorites", savedIds).apply() }) { Icon(if (product.id in savedIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", tint = if (product.id in savedIds) WhappyBlue else WhappyMuted) }
                }
            }
        }
    }
    if (creating) ListingDialog(busy, onDismiss = { creating = false }) { title, price, place, mode ->
            if (preview) localItems = listOf(WhappyListing("local-${System.currentTimeMillis()}", title, price.ifBlank { "Prix à discuter" }, place.ifBlank { "Brazzaville" }, accountDisplayName, "demo-user", mode)) + localItems
        else onPublish(title, price, place, mode)
        creating = false
    }
    selected?.let { product ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(product.title, fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue, modifier = Modifier.size(62.dp)) }; Text(product.price, color = WhappyBlue, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Vendu par ${product.seller} · ${product.place}", color = WhappyMuted); Text(if (product.mode == "troc") "Cette annonce accepte les propositions d’échange." else "Ajoutez cet article au panier pour préparer votre commande.", color = WhappyDark) } },
            confirmButton = { Button(onClick = { saveCart(cart + (product.id to ((cart[product.id] ?: 0) + 1).coerceAtMost(9))); marketFeedback = "${product.title} ajouté au panier."; selected = null }) { Icon(Icons.Rounded.ShoppingCart, null); Text("Ajouter", Modifier.padding(start = 5.dp)) } },
            dismissButton = { Row { TextButton(onClick = { savedIds = if (product.id in savedIds) savedIds - product.id else savedIds + product.id; prefs.edit().putStringSet("market_favorites", savedIds).apply() }) { Text(if (product.id in savedIds) "Retirer des favoris" else "Favori") }; TextButton(onClick = { selected = null }) { Text("Fermer") } } },
        )
    }
    selectedRestaurant?.let { page ->
        AlertDialog(
            onDismissRequest = { selectedRestaurant = null },
            title = { Text(page.name, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(page.category.uppercase(Locale.FRANCE), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(page.city.ifBlank { "Ville non renseignée" }, color = WhappyMuted)
                    if (page.bio.isNotBlank()) Text(page.bio, color = WhappyDark)
                    Text("Cette fiche vient du compte Business de l’établissement. WAPI n’invente ni distance ni disponibilité.", color = WhappyMuted, fontSize = 11.sp)
                }
            },
            confirmButton = { Button(onClick = { onContactBusiness(page); selectedRestaurant = null }) { Icon(Icons.Rounded.ChatBubble, null); Text(" Écrire") } },
            dismissButton = { TextButton(onClick = { selectedRestaurant = null }) { Text("Fermer") } },
        )
    }
    if (showingCart) {
        val allProducts = localItems + listings
        AlertDialog(
            onDismissRequest = { showingCart = false },
            title = { Text("Mon panier · ${cart.values.sum()} article(s)", fontWeight = FontWeight.Black) },
            text = { LazyColumn(Modifier.fillMaxWidth().height(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cart.isEmpty()) item { Text("Votre panier est vide. Ouvrez une annonce pour ajouter un article.", color = WhappyMuted) }
                cart.forEach { (id, quantity) -> val product = allProducts.firstOrNull { it.id == id }; if (product != null) item(key = "cart-$id") { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(product.title, fontWeight = FontWeight.Bold, color = WhappyDark, maxLines = 2); Text(product.price, color = WhappyBlue, fontSize = 11.sp) }; TextButton(onClick = { saveCart(cart + (id to quantity - 1)) }) { Text("−") }; Text(quantity.toString(), fontWeight = FontWeight.Black); TextButton(onClick = { saveCart(cart + (id to (quantity + 1).coerceAtMost(9))) }) { Text("+") } } } } }
                if (cart.isNotEmpty()) item { OutlinedTextField(delivery, { delivery = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("Adresse ou point de rendez-vous") }, minLines = 2) }
            } },
            confirmButton = { Button(enabled = cart.isNotEmpty() && delivery.trim().length >= 5, onClick = { val reference = "WH-${UUID.randomUUID().toString().take(6).uppercase()}"; val titles = cart.mapNotNull { (id, quantity) -> allProducts.firstOrNull { it.id == id }?.let { "$quantity × ${it.title.replace("|", " ")}" } }.joinToString(", "); val entry = "${System.currentTimeMillis()}|$reference|$titles · ${delivery.trim().replace("|", " ")}"; orders = (listOf(entry) + orders).take(30); prefs.edit().putStringSet("market_orders", orders.toSet()).apply(); saveCart(emptyMap()); delivery = ""; showingCart = false; marketFeedback = "Commande $reference enregistrée." }) { Text("Commander") } },
            dismissButton = { TextButton(onClick = { showingCart = false }) { Text("Fermer") } },
        )
    }
    if (showingOrders) AlertDialog(
        onDismissRequest = { showingOrders = false },
        title = { Text("Mes commandes", fontWeight = FontWeight.Black) },
        text = { LazyColumn(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (orders.isEmpty()) item { Text("Aucune commande enregistrée.", color = WhappyMuted) }; items(orders, key = { it }) { raw -> val parts = raw.split("|", limit = 3); Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(13.dp)) { Row { Text(parts.getOrElse(1) { "Commande" }, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Black); Text("À confirmer", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text(parts.getOrElse(2) { "" }, Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp) } } } } },
        confirmButton = { TextButton(onClick = { showingOrders = false }) { Text("Fermer") } },
    )
}

@Composable
private fun ListingDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var place by remember { mutableStateOf("") }; var mode by remember { mutableStateOf("vente") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nouvelle annonce") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title,{title=it.take(120)},Modifier.fillMaxWidth(),label={Text("Titre")},singleLine=true)
        OutlinedTextField(price,{price=it},Modifier.fillMaxWidth(),label={Text("Prix ou échange")},singleLine=true)
        OutlinedTextField(place,{place=it},Modifier.fillMaxWidth(),label={Text("Lieu")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ listOf("vente" to "Vendre","troc" to "Troquer").forEach{ option -> OutlinedButton(onClick={mode=option.first},colors=ButtonDefaults.outlinedButtonColors(containerColor=if(mode==option.first) Color.White else Color.Transparent)){Text(option.second)} } }
    } }, confirmButton = { Button(enabled=title.trim().length>=2&&!busy,onClick={onSave(title,price,place,mode)}){Text(if(busy)"Publication…" else "Publier") } }, dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})
}

@Composable
private fun LiveScreen(
    lives: List<WhappyLive>,
    currentUserId: String,
    preview: Boolean,
    busy: Boolean,
    accountDisplayName: String,
    accountPhotoUrl: String,
    onCreateLive: (String, String, String, Boolean, String, String) -> Unit,
    onEndLive: (String) -> Unit,
    onUpdateLiveStatus: (String, String) -> Unit,
    requestedLiveId: String,
    onConsumeLiveLink: () -> Unit,
    onOpenTwin: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    var localLives by remember { mutableStateOf(emptyList<WhappyLive>()) }
    var selectedLive by remember { mutableStateOf<WhappyLive?>(null) }
    var previewStatuses by remember { mutableStateOf(emptyMap<String, String>()) }
    var pendingDirectStart by remember { mutableStateOf(false) }
    var pendingDirectWasBusy by remember { mutableStateOf(false) }
    var pendingLiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingLiveRequiresCamera by remember { mutableStateOf(true) }
    var permissionError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val livePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.RECORD_AUDIO] == true && (!pendingLiveRequiresCamera || result[Manifest.permission.CAMERA] == true)
        if (granted) pendingLiveAction?.invoke() else permissionError = true
        pendingLiveAction = null
    }
    val startWithPermissions: (Boolean, () -> Unit) -> Unit = { requireCamera, action ->
        val alreadyGranted = preview || (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            && (!requireCamera || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
        if (alreadyGranted) action()
        else {
            permissionError = false
            pendingLiveAction = action
            pendingLiveRequiresCamera = requireCamera
            livePermissions.launch(if (requireCamera) arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
    val launchDirect = {
        pendingDirectStart = true
        if (preview) {
            val now = System.currentTimeMillis()
            val local = WhappyLive(
                id = "local-$now",
                hostId = currentUserId,
                hostName = accountDisplayName,
                title = "En direct avec $accountDisplayName",
                category = "Discussion",
                productTitle = "",
                status = "scheduled",
                viewerCount = 0,
                startedAt = now,
                hostMode = "personal",
                visibility = "public",
                streamProvider = "wapi-webrtc-p2p",
                streamRoomId = "preview-$now",
                hostPhotoUrl = accountPhotoUrl,
            )
            localLives = listOf(local) + localLives
        } else {
            onCreateLive("", "Discussion", "", true, "personal", "public")
        }
    }
    // A public card is only rendered when it has a real WebRTC transport. A
    // half-created “studio” is never presented as a live to the user.
    val visibleLives = (localLives + lives)
        .map { live -> previewStatuses[live.id]?.let { live.copy(status = it) } ?: live }
        .filter { it.status != "ended" && it.streamProvider == "wapi-webrtc-p2p" && it.streamRoomId.isNotBlank() }
    val liveNow = visibleLives.count { it.status == "live" }
    LaunchedEffect(visibleLives, pendingDirectStart) {
        if (!pendingDirectStart) return@LaunchedEffect
        visibleLives.filter { it.hostId == currentUserId && it.status != "ended" }.maxByOrNull { it.startedAt }?.let {
            selectedLive = it
            pendingDirectStart = false
        }
    }
    LaunchedEffect(busy, pendingDirectStart) {
        if (!pendingDirectStart) {
            pendingDirectWasBusy = false
        } else if (busy) {
            pendingDirectWasBusy = true
        } else if (pendingDirectWasBusy && visibleLives.none { it.hostId == currentUserId && it.status != "ended" }) {
            // The callable failed before a media session was created. Restore
            // the action immediately so the user is never left with a spinner.
            pendingDirectStart = false
            pendingDirectWasBusy = false
        }
    }
    LaunchedEffect(requestedLiveId, visibleLives) {
        if (requestedLiveId.isBlank()) return@LaunchedEffect
        val requested = visibleLives.firstOrNull { it.id == requestedLiveId } ?: return@LaunchedEffect
        if (requested.streamProvider == "wapi-webrtc-p2p" && requested.streamRoomId.isNotBlank()) selectedLive = requested
        onConsumeLiveLink()
    }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)), contentPadding = PaddingValues(WapiMobile.screen, 10.dp, WapiMobile.screen, 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("En direct", color = WhappyDark, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text(if (liveNow > 0) "$liveNow direct(s) public(s) dans WAPI" else "Un direct public est visible mondialement, sans être contact", color = WhappyMuted, fontSize = 12.sp)
                }
                FilledIconButton(
                    onClick = { startWithPermissions(true, launchDirect) },
                    enabled = !busy && !pendingDirectStart,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
                ) {
                    if (pendingDirectStart) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Videocam, "Passer en direct", tint = Color.White)
                }
            }
        }
        if (permissionError) item { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(WapiMobile.compactRadius), color = Color(0xFFFFF5F3)) { Text(t("Autorisez la caméra et le micro pour démarrer le direct.", "Allow camera and microphone to start.", "Pesa ndingisa na caméra mpe micro."), Modifier.padding(12.dp), color = WhappyDark, fontSize = 11.sp) } }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenTwin), shape = RoundedCornerShape(WapiMobile.compactRadius), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(WapiMobile.row), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t("OPTION CRÉATIVE · FACULTATIVE", "OPTIONAL CREATIVE TOOL", "OPTION YA CRÉATION"), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(t("Animer avec Mon WAPI", "Animate with My WAPI", "Sala animation na Mon WAPI"), color = WhappyDark, fontWeight = FontWeight.Bold) }
                    Text("›", color = WhappyBlue, fontSize = 25.sp)
                }
            }
        }
        item { Text("En direct maintenant", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
        if (visibleLives.isEmpty()) item { EmptyState("Aucun direct en cours", "Votre direct apparaîtra ici dès que la caméra est connectée.") }
        items(visibleLives, key = { it.id }) { live ->
            val liveReady = live.streamProvider == "wapi-webrtc-p2p"
            Card(Modifier.fillMaxWidth().clickable { if (live.hostId == currentUserId && live.status != "live") startWithPermissions(!live.audioOnly) { selectedLive = live } else selectedLive = live }, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(128.dp).background(if (live.status == "live") WhappyDark else Color.White), contentAlignment = Alignment.Center) {
                        Icon(if (live.status == "live") Icons.Rounded.PlayArrow else Icons.Rounded.Schedule, null, tint = if (live.status == "live") Color.White else WhappyBlue, modifier = Modifier.size(46.dp))
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(if (live.status == "live") WhappyBlue else Color(0xFF6B7280)).padding(horizontal = 9.dp, vertical = 5.dp)) { Text(if (live.status == "live") "● EN DIRECT" else "PRÊT À DÉMARRER", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        if (liveReady && live.status == "live") Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" ${live.viewerCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(live.title, color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(live.hostPhotoUrl, live.hostName, 30.dp, shape = CircleShape)
                            Text("${live.hostName} · ${live.category}", Modifier.weight(1f).padding(start = 8.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(liveModeLabel(live.hostMode), color = WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        }
                        if (live.productTitle.isNotBlank()) Text("Deal présenté : ${live.productTitle}", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp)
                        if (live.hostId == currentUserId && live.status == "scheduled") {
                            Button(onClick = { startWithPermissions(!live.audioOnly) { selectedLive = live } }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Icon(if (live.audioOnly) Icons.Rounded.Radio else Icons.Rounded.Videocam, null, modifier = Modifier.size(17.dp)); Text("  Démarrer le direct") }
                        }
                        else if (live.hostId == currentUserId) OutlinedButton(onClick = { if (preview) previewStatuses = previewStatuses + (live.id to "ended") else onUpdateLiveStatus(live.id, "ended") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Text("Terminer le direct") }
                        else if (liveReady && live.status == "live") Button(onClick = { selectedLive = live }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text("Rejoindre le Live", Modifier.padding(start = 6.dp)) }
                        else if (live.status == "scheduled") Text("Connexion de la caméra en cours…", Modifier.padding(top = 10.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        item { Text("Un bouton suffit pour démarrer. Les spectateurs rejoignent le flux dès qu’il est actif.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp) }
    }
    selectedLive?.let { live ->
        if (live.streamProvider == "wapi-webrtc-p2p" && live.streamRoomId.isNotBlank()) {
            WapiNativeLiveRoomDialog(
                live = live,
                expectedHost = live.hostId == currentUserId,
                onDismiss = { selectedLive = null },
            )
        } else if (live.hostId == currentUserId) {
            LivePreflightDialog(live = live, onDismiss = { selectedLive = null })
        }
    }
}

/**
 * A useful and honest first step before an SFU is connected.  It verifies the
 * camera, microphone and speaker on the creator's device, but is never shown
 * to viewers as a public broadcast.
 */
@Composable
private fun LivePreflightDialog(live: WhappyLive, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var frontCamera by rememberSaveable(live.id) { mutableStateOf(true) }
    var speakerOn by rememberSaveable(live.id) { mutableStateOf(true) }
    var microphoneTesting by rememberSaveable(live.id) { mutableStateOf(false) }
    var microphoneRecorder by remember(live.id) { mutableStateOf<MediaRecorder?>(null) }
    var microphoneFile by remember(live.id) { mutableStateOf<File?>(null) }
    var feedback by rememberSaveable(live.id) { mutableStateOf("Caméra prête · le studio est privé tant que la diffusion sécurisée n’est pas activée.") }

    fun stopMicrophoneTest(notify: Boolean) {
        val active = microphoneRecorder
        val file = microphoneFile
        runCatching { active?.stop() }
        active?.release()
        microphoneRecorder = null
        microphoneFile = null
        microphoneTesting = false
        file?.delete()
        if (notify && active != null) {
            WhappySounds.voiceRecordingStopped()
            feedback = "Test du micro terminé. Votre voix reste sur cet appareil et n’a pas été diffusée."
        }
    }

    fun toggleMicrophoneTest() {
        if (microphoneTesting) {
            stopMicrophoneTest(notify = true)
            return
        }
        runCatching { createVoiceRecorder(context) }
            .onSuccess { (recorder, file) ->
                microphoneRecorder = recorder
                microphoneFile = file
                microphoneTesting = true
                WhappySounds.voiceRecordingStarted()
                feedback = "Micro actif · parlez quelques secondes puis appuyez sur Arrêter le test."
            }
            .onFailure {
                feedback = "Le micro n’a pas pu démarrer. Vérifiez l’autorisation puis relancez le studio."
            }
    }

    LaunchedEffect(speakerOn) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeCommunicationAudio(audioManager, speakerOn)
    }
    DisposableEffect(live.id) {
        onDispose {
            stopMicrophoneTest(notify = false)
            routeCommunicationAudio(audioManager, false)
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                LiveCameraPreview(frontCamera)
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(onClick = onDismiss, modifier = Modifier.size(42.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .38f), contentColor = Color.White)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Fermer le studio")
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                        Text("STUDIO LIVE PRIVÉ", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(live.title, color = Color.White.copy(alpha = .86f), fontSize = 12.sp, maxLines = 1)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC202020)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text("NON DIFFUSÉ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
                    color = Color.White.copy(alpha = .97f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Vérification avant diffusion", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(feedback, color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { frontCamera = !frontCamera }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.FlipCameraAndroid, null, modifier = Modifier.size(18.dp)); Text("  Caméra")
                            }
                            OutlinedButton(onClick = { speakerOn = !speakerOn }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Icon(if (speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, null, modifier = Modifier.size(18.dp)); Text(if (speakerOn) "  Son actif" else "  Son coupé")
                            }
                        }
                        Button(onClick = ::toggleMicrophoneTest, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = if (microphoneTesting) Color(0xFFE33D4E) else WhappyBlue)) {
                            Icon(if (microphoneTesting) Icons.Rounded.Stop else Icons.Rounded.Mic, null)
                            Text(if (microphoneTesting) "  Arrêter le test micro" else "  Tester le microphone", fontWeight = FontWeight.Bold)
                        }
                        Text("La diffusion publique nécessite un SFU/WebRTC WAPI provisionné. Cette étape ne prétend jamais envoyer votre caméra à des spectateurs.", color = WhappyMuted, fontSize = 9.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun routeCommunicationAudio(audioManager: AudioManager, speakerEnabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!speakerEnabled) {
            audioManager.clearCommunicationDevice()
            return
        }
        val speaker = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        if (speaker != null) audioManager.setCommunicationDevice(speaker)
    } else {
        audioManager.isSpeakerphoneOn = speakerEnabled
    }
}

@Composable
private fun LiveCameraPreview(frontCamera: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    AndroidView(
        factory = { currentContext ->
            PreviewView(currentContext).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                runCatching { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview) }
            }, ContextCompat.getMainExecutor(context))
        },
    )
    DisposableEffect(Unit) { onDispose { if (cameraProviderFuture.isDone) runCatching { cameraProviderFuture.get().unbindAll() } } }
}

private fun liveModeLabel(mode: String): String = when (mode) { "business" -> "BUSINESS"; "creator" -> "CRÉATEUR"; else -> "PERSONNEL" }

private enum class BusinessSection(val label: String) { DASHBOARD("Aperçu"), PAGES("Pages"), CATALOG("Catalogue"), DEALS("Deals"), SALES("Salons de vente"), ORDERS("Commandes"), PAYMENTS("Paiements"), ADS("Publicité"), INSIGHTS("Performances") }

@Composable
private fun BusinessScreen(
    pages: List<WhappyBusinessPage>,
    campaigns: List<WhappyCampaign>,
    deals: List<WhappyDeal>,
    paymentNotices: List<WhappyPaymentNotice>,
    preview: Boolean,
    busy: Boolean,
    onCreatePage: (String, String, String, String, String, String) -> Unit,
    onUpdatePage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onUpdateLogo: (WhappyBusinessPage, Uri, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onCreateDeal: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit,
    onUpdateDealStatus: (String, String) -> Unit,
    onMarkPaymentRead: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenTwin: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    var section by remember { mutableStateOf(BusinessSection.DASHBOARD) }
    var creatingPage by remember { mutableStateOf(false) }
    var creatingCampaignMode by remember { mutableStateOf<String?>(null) }
    var boostedProduct by remember { mutableStateOf<WhappyDeal?>(null) }
    var creatingDeal by remember { mutableStateOf(false) }
    var editingPage by remember { mutableStateOf<WhappyBusinessPage?>(null) }
    var localPages by remember { mutableStateOf(emptyList<WhappyBusinessPage>()) }
    var localCampaigns by remember { mutableStateOf(emptyList<WhappyCampaign>()) }
    var localDeals by remember { mutableStateOf(emptyList<WhappyDeal>()) }
    var previewPageUpdates by remember { mutableStateOf(emptyMap<String, WhappyBusinessPage>()) }
    var previewDealStatuses by remember { mutableStateOf(emptyMap<String, String>()) }
    var locallyRead by remember { mutableStateOf(emptySet<String>()) }
    val visiblePages = (localPages + pages).map { previewPageUpdates[it.id] ?: it }
    val visibleCampaigns = localCampaigns + campaigns
    val visibleDeals = (localDeals + deals).map { deal -> previewDealStatuses[deal.id]?.let { deal.copy(status = it) } ?: deal }
    val unread = paymentNotices.count { !it.read && it.id !in locallyRead }
    val paidTotal = paymentNotices.filter { it.status == "paid" }.sumOf { it.amount }
    val soldUnits = visibleDeals.sumOf { it.sold }
    val availableStock = visibleDeals.sumOf { (it.stock - it.sold).coerceAtLeast(0) }
    val advertisingBudget = visibleCampaigns.filter { it.status == "active" }.sumOf { it.dailyBudget * it.days }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Business", color = WhappyDark, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text(if (visiblePages.isEmpty()) "Votre profil professionnel séparé" else visiblePages.first().name, color = WhappyMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onEnableNotifications) { Box(contentAlignment = Alignment.TopEnd) { Icon(Icons.Rounded.Notifications, "Alertes Business", tint = WhappyDark); if (unread > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(WhappyBlue)) } }
                FilledIconButton(onClick = { if (visiblePages.isEmpty()) creatingPage = true else creatingDeal = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(if (visiblePages.isEmpty()) Icons.Rounded.Add else Icons.Rounded.LocalOffer, if (visiblePages.isEmpty()) "Créer une page" else "Créer une offre", tint = Color.White) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Deals actifs", visibleDeals.count { it.status == "active" }.toString(), Modifier.weight(1f))
                MetricCard("Paiements", formatMoney(paidTotal), Modifier.weight(1f))
                MetricCard("Nouveaux", unread.toString(), Modifier.weight(1f))
            }
        }
        item {
            BusinessIdentityCard(
                page = visiblePages.firstOrNull(),
                onCreate = { creatingPage = true },
                onOpenInbox = { /* The inbox is the same Messages tab, filtered by profile identity. */ },
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusinessSection.entries.forEach { item -> OutlinedButton(onClick = { section = item }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (section == item) WhappyBlue else Color.Transparent, contentColor = if (section == item) Color.White else WhappyMuted), border = androidx.compose.foundation.BorderStroke(1.dp, if (section == item) WhappyBlue else WhappyLine), shape = RoundedCornerShape(12.dp)) { Text(item.label, fontWeight = if (section == item) FontWeight.Bold else FontWeight.Medium) } }
            }
        }
        when (section) {
            BusinessSection.DASHBOARD -> {
                item { Text("Centre Business", fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                item { BusinessFeatureCard(Icons.Rounded.Storefront, "Profil Business", if (visiblePages.isEmpty()) "Créez une page publique professionnelle" else "${visiblePages.first().name} · @${visiblePages.first().handle}") { section = BusinessSection.PAGES } }
                item { BusinessFeatureCard(Icons.AutoMirrored.Rounded.ReceiptLong, "Catalogue", if (visibleDeals.isEmpty()) "Ajoutez vos produits et services" else "${visibleDeals.size} offre(s) · $availableStock unité(s) disponibles") { section = BusinessSection.CATALOG } }
                item { BusinessFeatureCard(Icons.Rounded.LocalOffer, "Deals", "Offres limitées, stock et ventes en un coup d’œil") { section = BusinessSection.DEALS } }
                item { BusinessFeatureCard(Icons.Rounded.LiveTv, "Salons de vente", "Lancez une vente événementielle visible dans tout WAPI") { section = BusinessSection.SALES } }
                item { BusinessFeatureCard(Icons.Rounded.BusinessCenter, "Commandes", if (paymentNotices.isEmpty()) "Centralisez vos prochaines ventes" else "${paymentNotices.size} commande(s) à suivre") { section = BusinessSection.ORDERS } }
                item { BusinessFeatureCard(Icons.Rounded.Payments, "Paiements", if (unread > 0) "$unread nouvelle(s) notification(s)" else "Historique et alertes de transactions") { section = BusinessSection.PAYMENTS } }
                item { BusinessFeatureCard(Icons.Rounded.Visibility, "Performances", "Revenus, stock, ventes et budget publicitaire") { section = BusinessSection.INSIGHTS } }
                item { BusinessFeatureCard(Icons.Rounded.AutoAwesome, "Mon WAPI pour Business", "Créez des contenus et préparez vos directs") { onOpenTwin() } }
            }
            BusinessSection.PAGES -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Mes pages", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark); TextButton(onClick = { creatingPage = true }) { Text("+ Nouvelle") } } }
                if (visiblePages.isEmpty()) item { EmptyState("Aucune page Business", "Créez une identité professionnelle pour votre activité ou votre contenu.") }
                items(visiblePages, key = { it.id }) { page -> BusinessPageCard(page) { editingPage = page } }
            }
            BusinessSection.DEALS -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Deals en cours", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark); if (visiblePages.isNotEmpty()) TextButton(onClick = { creatingDeal = true }) { Text("+ Créer") } } }
                if (visiblePages.isEmpty()) item { EmptyState("Page requise", "Créez d’abord votre page Business pour publier des Deals.") }
                else if (visibleDeals.isEmpty()) item { EmptyState("Aucun Deal", "Publiez une offre limitée pour activer vos ventes.") }
                items(visibleDeals, key = { it.id }) { deal -> DealCard(deal) { status -> if (preview) previewDealStatuses = previewDealStatuses + (deal.id to status) else onUpdateDealStatus(deal.id, status) } }
            }
            BusinessSection.CATALOG -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Catalogue", fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text("Produits, services et offres vendables", color = WhappyMuted, fontSize = 11.sp) }; if (visiblePages.isNotEmpty()) Button(onClick = { creatingDeal = true }, shape = RoundedCornerShape(13.dp)) { Text("+ Ajouter") } } }
                if (visiblePages.isEmpty()) item { EmptyState("Page requise", "Créez votre page Business avant de composer le catalogue.") }
                else if (visibleDeals.isEmpty()) item { EmptyState("Catalogue vide", "Ajoutez votre premier produit ou service avec une offre attractive.") }
                items(visibleDeals, key = { "catalog-${it.id}" }) { deal ->
                    CatalogCard(
                        deal = deal,
                        onAddVariant = { creatingDeal = true },
                        onBoost = { boostedProduct = deal; creatingCampaignMode = "product" },
                    )
                }
            }
            BusinessSection.SALES -> {
                item { WapiBusinessSaleRoomManager(visiblePages, visibleDeals) }
            }
            BusinessSection.ORDERS -> {
                item { Text("Centre de commandes", fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                if (paymentNotices.isEmpty()) item { EmptyState("Aucune commande", "Les commandes confirmées par paiement apparaîtront ici.") }
                items(paymentNotices, key = { "order-${it.id}" }) { notice -> OrderCard(notice) }
            }
            BusinessSection.PAYMENTS -> {
                item { Text("Notifications de paiement", fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                item { OutlinedButton(onClick = onEnableNotifications, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Notifications, null); Text("Recevoir les alertes sur ce téléphone", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold) } }
                if (paymentNotices.isEmpty()) item { EmptyState("Aucun paiement", "Les paiements confirmés apparaîtront ici dès que votre fournisseur sera connecté.") }
                items(paymentNotices, key = { it.id }) { notice -> PaymentNoticeCard(notice, notice.id in locallyRead) { locallyRead = locallyRead + notice.id; if (!preview) onMarkPaymentRead(notice.id) } }
                item { Text("Sécurité : une alerte n’est créée qu’après confirmation du fournisseur de paiement. La réception réelle nécessite son webhook serveur.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp) }
            }
            BusinessSection.ADS -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("WAPI Ads", fontSize = 23.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text("Développez une activité locale avec un budget contrôlé", color = WhappyMuted, fontSize = 11.sp) } } }
                if (visiblePages.isNotEmpty()) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(Modifier.weight(1f).clickable { creatingCampaignMode = "boost" }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = WhappyBlue)) {
                            Column(Modifier.padding(16.dp)) { Icon(Icons.Rounded.Bolt, null, tint = Color.White); Text("Booster mon activité", Modifier.padding(top = 12.dp), color = Color.White, fontWeight = FontWeight.Black); Text("Visibilité locale et visites du profil", Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .78f), fontSize = 10.sp, lineHeight = 14.sp) }
                        }
                        Card(Modifier.weight(1f).clickable { creatingCampaignMode = "campaign" }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                            Column(Modifier.padding(16.dp)) { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappySky); Text("Créer une publicité", Modifier.padding(top = 12.dp), color = Color.White, fontWeight = FontWeight.Black); Text("Créatif, audience, région et objectif", Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .78f), fontSize = 10.sp, lineHeight = 14.sp) }
                        }
                    }
                }
                if (visiblePages.isEmpty()) item { EmptyState("Page requise", "Créez une page Business avant de lancer une publicité.") }
                else if (visibleCampaigns.isEmpty()) item { EmptyState("Aucune campagne", "Développez votre audience avec une campagne ciblée.") }
                items(visibleCampaigns, key = { it.id }) { campaign -> CampaignCard(campaign) }
            }
            BusinessSection.INSIGHTS -> {
                item { Text("Performances Business", fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Revenus", formatMoney(paidTotal), Modifier.weight(1f)); MetricCard("Ventes", soldUnits.toString(), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Stock", availableStock.toString(), Modifier.weight(1f)); MetricCard("Publicité", formatMoney(advertisingBudget), Modifier.weight(1f)) } }
                item { InsightCard("Objectif mensuel", paidTotal, 500_000L, "Continuez avec des Lives et Deals réguliers") }
                item { InsightCard("Écoulement du stock", soldUnits.toLong(), (soldUnits + availableStock).coerceAtLeast(1).toLong(), "Produits vendus sur le catalogue actif") }
                item { BusinessFeatureCard(Icons.Rounded.AutoAwesome, "Créer avec mon WAPI", "Préparez une vidéo, un Live ou une campagne") { onOpenTwin() } }
            }
        }
    }
    if (creatingPage) BusinessPageDialog(busy, onDismiss = { creatingPage = false }) { name, category, bio, city, phone, website ->
        if (preview) localPages = listOf(WhappyBusinessPage("local-${System.currentTimeMillis()}", name, name.lowercase().replace(" ", "-"), category, bio, city.ifBlank { "Brazzaville" }, "demo-user", phone = phone, website = website)) + localPages
        else onCreatePage(name, category, bio, city, phone, website)
        creatingPage = false
    }
    creatingCampaignMode?.let { mode -> CampaignDialog(visiblePages, mode, busy, boostedProduct, onDismiss = { creatingCampaignMode = null; boostedProduct = null }) { draft ->
        if (preview) localCampaigns = listOf(WhappyCampaign("local-${System.currentTimeMillis()}", draft.pageId, draft.pageName, draft.objective, draft.title, draft.dailyBudget, draft.days, "pending_payment")) + localCampaigns
        else onCreateCampaign(draft)
        creatingCampaignMode = null
        boostedProduct = null
    } }
    if (creatingDeal && visiblePages.isNotEmpty()) DealDialog(visiblePages, busy, onDismiss = { creatingDeal = false }) { page, title, description, original, price, stock, days ->
        if (preview) localDeals = listOf(WhappyDeal("local-${System.currentTimeMillis()}", page.id, page.name, page.ownerId, title, description, original, price, stock, 0, System.currentTimeMillis() + days * 86_400_000L, "active")) + localDeals
        else onCreateDeal(page, title, description, original, price, stock, days)
        creatingDeal = false
    }
    editingPage?.let { page ->
        EditBusinessPageDialog(
            page = page,
            busy = busy,
            onDismiss = { editingPage = null },
            onLogo = { uri, contentType ->
                if (preview) previewPageUpdates = previewPageUpdates + (page.id to (previewPageUpdates[page.id] ?: page).copy(logoUrl = uri.toString()))
                else onUpdateLogo(page, uri, contentType)
            },
            onSave = { name, category, bio, city, phone, website ->
                if (preview) previewPageUpdates = previewPageUpdates + (page.id to (previewPageUpdates[page.id] ?: page).copy(name = name, category = category, bio = bio, city = city, phone = phone, website = website))
                else onUpdatePage(page, name, category, bio, city, phone, website)
                editingPage = null
            },
        )
    }
}

@Composable
private fun BusinessFeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(WapiMobile.compactRadius), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(WapiMobile.row), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Black, color = WhappyDark); Text(body, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 22.sp) } }
}

@Composable
private fun CatalogCard(deal: WhappyDeal, onAddVariant: () -> Unit, onBoost: () -> Unit) {
    val remaining = (deal.stock - deal.sold).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(deal.title, color = WhappyDark, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMoney(deal.dealPrice), color = WhappyBlue, fontWeight = FontWeight.Black)
                Text("$remaining disponible(s) · ${deal.sold} vendu(s)", color = WhappyMuted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onBoost) { Icon(Icons.Rounded.Bolt, null, Modifier.size(16.dp)); Text(" Booster") }
                TextButton(onClick = onAddVariant) { Text("Variante +") }
            }
        }
    }
}

@Composable
private fun OrderCard(notice: WhappyPaymentNotice) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = WhappyBlue) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Commande de ${notice.buyerName}", color = WhappyDark, fontWeight = FontWeight.Black); Text("${notice.provider} · ${formatTime(notice.createdAt)}", color = WhappyMuted, fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.End) { Text(formatMoney(notice.amount), color = WhappyBlue, fontWeight = FontWeight.Black); Text(if (notice.status == "paid") "PAYÉE" else notice.status.uppercase(), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun InsightCard(title: String, value: Long, target: Long, body: String) {
    val progress = if (target <= 0) 0f else (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp)) {
            Row { Text(title, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Black); Text("${(progress * 100).toInt()} %", color = WhappyBlue, fontWeight = FontWeight.Black) }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(CircleShape), color = WhappyBlue, trackColor = Color.White)
            Text(body, Modifier.padding(top = 9.dp), color = WhappyMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BusinessPageCard(page: WhappyBusinessPage, onEdit: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { UserAvatar(page.logoUrl, page.name, 56.dp); Column(Modifier.weight(1f).padding(start = 12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(page.name, fontWeight = FontWeight.Black, color = WhappyDark); if (page.verified) Icon(Icons.Rounded.Verified, "Page certifiée", Modifier.padding(start = 5.dp).size(15.dp), tint = WapiVerifiedGray) }; Text("@${page.handle} · ${page.category}", color = WhappyBlue, fontSize = 11.sp); Text(page.bio.ifBlank { page.city }, Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2) }; TextButton(onClick = onEdit) { Text("Modifier") } }; FlowRow(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("📍 ${page.city}", color = WhappyMuted, fontSize = 10.sp); if (page.phone.isNotBlank()) Text("☎ ${page.phone}", color = WhappyMuted, fontSize = 10.sp); if (page.website.isNotBlank()) Text("↗ ${page.website}", color = WhappyBlue, fontSize = 10.sp) } } } }

@Composable
private fun DealCard(deal: WhappyDeal, onStatus: (String) -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (deal.status == "active") Color.White else Color.White).padding(horizontal = 8.dp, vertical = 5.dp)) { Text(if (deal.status == "active") "DEAL ACTIF" else deal.status.uppercase(), color = if (deal.status == "active") WhappyBlue else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black) }; Text(deal.pageName, Modifier.padding(start = 8.dp).weight(1f), color = WhappyMuted, fontSize = 11.sp); Text("${deal.sold}/${deal.stock} vendus", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text(deal.title, Modifier.padding(top = 10.dp), color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black); Text(deal.description, Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp); Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) { Text(formatMoney(deal.dealPrice), color = WhappyBlue, fontSize = 20.sp, fontWeight = FontWeight.Black); if (deal.originalPrice > deal.dealPrice) Text(formatMoney(deal.originalPrice), Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("Expire ${formatShortDate(deal.endsAt)}", color = WhappyMuted, fontSize = 10.sp) }; Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (deal.status == "active") OutlinedButton(onClick = { onStatus("paused") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Suspendre") } else if (deal.status == "paused") Button(onClick = { onStatus("active") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Réactiver") }; OutlinedButton(onClick = { onStatus("ended") }, enabled = deal.status != "ended", modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Terminer") } } } } }

@Composable
private fun PaymentNoticeCard(notice: WhappyPaymentNotice, locallyRead: Boolean, onRead: () -> Unit) { val unread = !notice.read && !locallyRead; Card(Modifier.fillMaxWidth().clickable(enabled = unread, onClick = onRead), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (unread) Color.White else Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(if (notice.status == "paid") Color.White else Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Payments, null, tint = if (notice.status == "paid") WhappyBlue else WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(if (notice.status == "paid") "Paiement reçu" else notice.status.replaceFirstChar { it.uppercase() }, color = WhappyDark, fontWeight = FontWeight.Black); Text("${notice.buyerName} · ${notice.provider}", color = WhappyMuted, fontSize = 11.sp); Text(formatTime(notice.createdAt), color = WhappyMuted, fontSize = 10.sp) }; Column(horizontalAlignment = Alignment.End) { Text("+${formatMoney(notice.amount)}", color = WhappyBlue, fontWeight = FontWeight.Black); if (unread) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(WhappyBlue)) } } } }

@Composable
private fun CampaignCard(campaign: WhappyCampaign) {
    val placement = when (campaign.placement) { "profile_story" -> "Story du profil"; "market" -> "Marché"; "live" -> "Live"; else -> "Découverte Business" }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(campaign.title, Modifier.weight(1f), fontWeight = FontWeight.Black, color = WhappyDark)
                Surface(color = if (campaign.status == "active") WhappyBlue.copy(alpha = .10f) else Color(0xFFFFF8E8), shape = RoundedCornerShape(8.dp)) { Text(if (campaign.status == "active") "EN DIFFUSION" else if (campaign.status == "pending_payment") "PAIEMENT REQUIS" else campaign.status.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (campaign.status == "active") WhappyBlue else Color(0xFF8A6114), fontSize = 9.sp, fontWeight = FontWeight.Black) }
            }
            Text("${campaign.pageName} · $placement · ${campaign.city.ifBlank { "Zone nationale" }}", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
            if (campaign.audience.isNotBlank()) Text(campaign.audience, Modifier.padding(top = 4.dp), color = WhappyDark, fontSize = 11.sp)
            Text("${formatMoney(campaign.dailyBudget)}/jour · ${campaign.days} jours · portée estimée ${campaign.estimatedReach.coerceAtLeast(120)}", Modifier.padding(top = 6.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DealDialog(pages: List<WhappyBusinessPage>, busy: Boolean, onDismiss: () -> Unit, onSave: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(pages.first()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("10") }
    var days by remember { mutableStateOf("3") }
    val originalValue = original.toLongOrNull() ?: 0
    val priceValue = price.toLongOrNull() ?: 0
    val stockValue = stock.toIntOrNull() ?: 0
    val daysValue = days.toIntOrNull() ?: 0
    val identityValid = title.trim().length >= 3 && description.trim().length >= 3
    val offerValid = originalValue > 0 && priceValue in 1..originalValue && stockValue > 0 && daysValue in 1..30
    val currentValid = when (step) { 0 -> identityValid; 1 -> offerValid; else -> identityValid && offerValid }
    val discount = if (originalValue > 0 && priceValue in 1..originalValue) (((originalValue - priceValue) * 100) / originalValue).toInt() else 0
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFCFDFE),
        contentColor = WhappyDark,
        dragHandle = { Box(Modifier.padding(top = 10.dp).width(38.dp).height(4.dp).clip(CircleShape).background(WhappyMuted.copy(alpha = .32f))) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Créer une offre", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("Étape ${step + 1} sur 3 · glissez vers le bas pour fermer", color = WhappyMuted, fontSize = 11.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { index -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index <= step) WhappyBlue else WhappyLine)) } }
            AnimatedContent(targetState = step, label = "deal-creator") { current ->
                when (current) {
                    0 -> Column(Modifier.fillMaxWidth().heightIn(min = 330.dp, max = 420.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("Publié par", color = WhappyDark, fontWeight = FontWeight.Black)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { pages.forEach { candidate -> OutlinedButton(onClick = { page = candidate }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (page.id == candidate.id) WhappyBlue else Color.Transparent, contentColor = if (page.id == candidate.id) Color.White else WhappyDark), shape = RoundedCornerShape(13.dp)) { Text(candidate.name) } } }
                        OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nom de l’offre") }, supportingText = { Text("Ex. Pack rentrée · 48 heures") }, singleLine = true)
                        OutlinedTextField(description, { description = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("Pourquoi cette offre est exceptionnelle ?") }, minLines = 4)
                    }
                    1 -> Column(Modifier.fillMaxWidth().heightIn(min = 330.dp, max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = WhappyBlue.copy(alpha = .08f), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text("RÉDUCTION", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(if (discount > 0) "-$discount %" else "À calculer", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(original, { original = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Prix normal") }, suffix = { Text("F") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(price, { price = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Prix Deal") }, suffix = { Text("F") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(stock, { stock = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Durée") }, suffix = { Text("j") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                        Text("WAPI affichera automatiquement le stock restant et la date de fin pour créer un sentiment d’urgence.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    else -> Column(Modifier.fillMaxWidth().heightIn(min = 330.dp, max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Voici ce que vos clients verront", color = WhappyDark, fontWeight = FontWeight.Black)
                        DealPreviewCard(page.name, title, description, originalValue, priceValue, stockValue, daysValue, discount)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("⚡ Urgence", "✓ Stock réel", "🔔 Alertes").forEach { tag -> Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 9.dp, vertical = 6.dp)) { Text(tag, color = WhappyDark, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (step == 0) onDismiss() else step -= 1 }) { Text(if (step == 0) "Annuler" else "Retour") }
                Spacer(Modifier.weight(1f))
                Button(enabled = currentValid && !busy, onClick = { if (step < 2) step += 1 else onSave(page, title.trim(), description.trim(), originalValue, priceValue, stockValue, daysValue) }, shape = RoundedCornerShape(15.dp)) { Text(if (busy) "Publication…" else if (step < 2) "Continuer" else "Publier", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun DealPreviewCard(pageName: String, title: String, description: String, original: Long, price: Long, stock: Int, days: Int, discount: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(9.dp)).background(WhappyBlue).padding(horizontal = 9.dp, vertical = 5.dp)) { Text("DEAL · -$discount %", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp) }; Spacer(Modifier.weight(1f)); Text("$days JOURS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            Text(title.ifBlank { "Votre Deal exceptionnel" }, Modifier.padding(top = 14.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(description.ifBlank { "Une description claire qui donne envie d’acheter maintenant." }, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 11.sp, maxLines = 3)
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) { Text(formatMoney(price), color = WhappyBlue, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("  ${formatMoney(original)}", color = Color.White, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("$stock restant(s)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Text(pageName, Modifier.padding(top = 10.dp), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BusinessPageDialog(busy:Boolean,onDismiss:()->Unit,onSave:(String,String,String,String,String,String)->Unit){
    var name by remember{mutableStateOf("")};var category by remember{mutableStateOf("")};var bio by remember{mutableStateOf("")};var city by remember{mutableStateOf("Brazzaville")};var phone by remember{mutableStateOf("")};var website by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Column{Text("Créer votre profil Business",fontWeight=FontWeight.Black);Text("Un profil professionnel séparé de votre compte personnel",color=WhappyMuted,fontSize=11.sp)}},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Répondez à ces questions pour préparer votre vitrine et vos ventes.",color=WhappyDark,fontSize=12.sp);OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),label={Text("Quel est le nom de l'entreprise ?")},singleLine=true);OutlinedTextField(category,{category=it.take(80)},Modifier.fillMaxWidth(),label={Text("Quelle est votre activité ?")},singleLine=true);OutlinedTextField(bio,{bio=it.take(400)},Modifier.fillMaxWidth(),label={Text("Que proposez-vous aux clients ?")},minLines=3);OutlinedTextField(city,{city=it.take(80)},Modifier.fillMaxWidth(),label={Text("Dans quelle ville ou région ?")},singleLine=true);OutlinedTextField(phone,{phone=it.take(30)},Modifier.fillMaxWidth(),label={Text("Quel numéro Business utiliser ?")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true);OutlinedTextField(website,{website=it.take(180)},Modifier.fillMaxWidth(),label={Text("Site, catalogue ou lien de commande (facultatif)")},singleLine=true);Text("Votre numéro peut rester le même : l'identité, les conversations, les réglages WEPI et les statistiques sont séparés par page Business.",color=WhappyMuted,fontSize=10.sp,lineHeight=14.sp)}},confirmButton={Button(enabled=name.trim().length>=2&&category.isNotBlank()&&!busy,onClick={onSave(name.trim(),category.trim(),bio.trim(),city.trim(),phone.trim(),website.trim())}){Text(if(busy)"Création…" else "Créer le profil")}},dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})
}

@Composable
private fun BusinessIdentityCard(page: WhappyBusinessPage?, onCreate: () -> Unit, onOpenInbox: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .18f))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(page?.logoUrl.orEmpty(), page?.name ?: "B", 48.dp, shape = RoundedCornerShape(14.dp))
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(if (page == null) "Profil Business à créer" else page.name, color = WhappyDark, fontWeight = FontWeight.Black)
                    Text(if (page == null) "Compte personnel inchangé" else "@${page.handle} · identité professionnelle", color = WhappyMuted, fontSize = 10.sp)
                }
                Surface(color = WhappyBlue.copy(alpha = .10f), shape = RoundedCornerShape(9.dp)) { Text("BUSINESS", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) }
            }
            Text(if (page == null) "Créez une page Business pour vendre, recevoir des demandes et configurer WEPI sans mélanger vos messages personnels." else "Même numéro, deux identités : vos clients écrivent à cette page et non à votre profil personnel.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (page == null) onCreate else onOpenInbox, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(if (page == null) Icons.Rounded.BusinessCenter else Icons.Rounded.ChatBubble, null); Text(if (page == null) "Créer le Business" else "Messagerie Business", Modifier.padding(start = 6.dp)) }
                if (page != null) OutlinedButton(onClick = onCreate, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Edit, null); Text("Gérer") }
            }
        }
    }
}

@Composable
private fun EditBusinessPageDialog(
    page: WhappyBusinessPage,
    busy: Boolean,
    onDismiss: () -> Unit,
    onLogo: (Uri, String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(page.id) { mutableStateOf(page.name) }
    var category by remember(page.id) { mutableStateOf(page.category) }
    var bio by remember(page.id) { mutableStateOf(page.bio) }
    var city by remember(page.id) { mutableStateOf(page.city) }
    var phone by remember(page.id) { mutableStateOf(page.phone) }
    var website by remember(page.id) { mutableStateOf(page.website) }
    var localLogo by remember(page.id, page.logoUrl) { mutableStateOf(page.logoUrl) }
    var logoToCrop by remember(page.id) { mutableStateOf<Uri?>(null) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) logoToCrop = uri
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Column { Text("Profil Business", fontWeight = FontWeight.Black); Text("@${page.handle}", color = WhappyBlue, fontSize = 11.sp) } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserAvatar(localLogo, name.ifBlank { page.name }, 82.dp)
                        OutlinedButton(enabled = !busy, onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Photo, null)
                            Text(if (localLogo.isBlank()) "  Ajouter un logo" else "  Changer et rogner le logo")
                        }
                    }
                }
                item { OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nom public") }, singleLine = true) }
                item { OutlinedTextField(category, { category = it.take(80) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Catégorie") }, singleLine = true) }
                item { OutlinedTextField(bio, { bio = it.take(400) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Présentation") }, minLines = 3) }
                item { OutlinedTextField(city, { city = it.take(80) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Ville") }, singleLine = true) }
                item { OutlinedTextField(phone, { phone = it.take(30) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Téléphone professionnel") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true) }
                item { OutlinedTextField(website, { website = it.take(180) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Site web ou réseau social") }, singleLine = true) }
                item { Text("Le logo recadré et ces informations seront visibles sur votre page publique Business.", color = WhappyMuted, fontSize = 10.sp) }
            }
        },
        confirmButton = { Button(enabled = name.trim().length >= 2 && category.isNotBlank() && !busy, onClick = { onSave(name.trim(), category.trim(), bio.trim(), city.trim(), phone.trim(), website.trim()) }) { Text(if (busy) "Enregistrement…" else "Enregistrer") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annuler") } },
    )
    logoToCrop?.let { source ->
        WapiSquareCropDialog(
            source = source,
            title = "Ajuster le logo Business",
            onDismiss = { logoToCrop = null },
            onConfirm = { cropped ->
                localLogo = cropped.toString()
                logoToCrop = null
                onLogo(cropped, context.contentResolver.getType(cropped) ?: "image/jpeg")
            },
        )
    }
}

@Composable
private fun CampaignDialog(
    pages: List<WhappyBusinessPage>,
    mode: String,
    busy: Boolean,
    product: WhappyDeal? = null,
    onDismiss: () -> Unit,
    onSave: (WhappyCampaignDraft) -> Unit,
) {
    var selectedPage by remember(product?.pageId) { mutableStateOf(pages.firstOrNull { it.id == product?.pageId } ?: pages.first()) }
    var objective by remember(mode) { mutableStateOf(if (mode == "product") "sales" else if (mode == "boost") "reach" else "messages") }
    var placement by remember(mode) { mutableStateOf(if (mode == "boost" || mode == "product") "profile_story" else "inbox") }
    var destination by remember(mode) { mutableStateOf(if (mode == "boost" || mode == "product") "page" else "message") }
    var title by remember(product?.id) { mutableStateOf(product?.let { "Découvrez ${it.title}" }.orEmpty()) }
    var creative by remember(product?.id) { mutableStateOf(product?.let { "${it.description}\n${formatMoney(it.dealPrice)} · stock limité" }.orEmpty()) }
    var audience by remember { mutableStateOf("Public local") }
    var city by remember { mutableStateOf("Brazzaville") }
    var budget by remember { mutableStateOf("2500") }
    var days by remember { mutableStateOf("7") }
    val dailyBudget = budget.toLongOrNull() ?: 0L
    val duration = days.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Text(if (mode == "product") "Booster ce produit" else if (mode == "boost") "Booster mon activité" else "Créer une publicité", fontWeight = FontWeight.Black); Text("WAPI Ads · contrôle avant paiement", color = WhappyMuted, fontSize = 10.sp) } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Page à promouvoir", color = WhappyMuted, fontWeight = FontWeight.Bold) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pages.forEach { page ->
                            OutlinedButton(onClick = { selectedPage = page }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedPage.id == page.id) Color.White else Color.Transparent)) { Text(page.name) }
                        }
                    }
                }
                item { Text("Objectif", color = WhappyMuted, fontWeight = FontWeight.Bold) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("reach" to "Visibilité", "messages" to "Messages", "traffic" to "Trafic", "sales" to "Ventes").forEach { option ->
                            OutlinedButton(onClick = { objective = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (objective == option.first) Color.White else Color.Transparent)) { Text(option.second) }
                        }
                    }
                }
                item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Titre de la campagne") }, singleLine = true) }
                item { Text("Diffusion", color = WhappyMuted, fontWeight = FontWeight.Bold) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("profile_story" to "Story profil", "inbox" to "Découverte", "market" to "Marché", "live" to "Live").forEach { option -> OutlinedButton(onClick = { placement = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (placement == option.first) WhappyBlue.copy(alpha = .08f) else Color.Transparent)) { Text(option.second) } } } }
                item { Text("Destination", color = WhappyMuted, fontWeight = FontWeight.Bold) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("message" to "Message", "page" to "Page", "call" to "Appel", "website" to "Lien").forEach { option -> OutlinedButton(onClick = { destination = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (destination == option.first) WhappyBlue.copy(alpha = .08f) else Color.Transparent)) { Text(option.second) } } } }
                item { OutlinedTextField(creative, { creative = it.take(600) }, Modifier.fillMaxWidth(), label = { Text("Message publicitaire") }, minLines = 3) }
                item { OutlinedTextField(audience, { audience = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Audience") }, singleLine = true) }
                item { OutlinedTextField(city, { city = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Ville") }, singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(budget, { budget = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("FCFA/jour") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Jours") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
                item { Text("Budget total : ${dailyBudget * duration} FCFA", color = WhappyBlue, fontWeight = FontWeight.Bold) }
                item { Surface(color = Color(0xFFFFF8E8), shape = RoundedCornerShape(14.dp)) { Text("La campagne restera en attente jusqu’à la confirmation sécurisée du paiement. Aucun budget ne sera débité automatiquement.", Modifier.padding(12.dp), color = Color(0xFF7A5610), fontSize = 10.sp, lineHeight = 15.sp) } }
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().length >= 2 && creative.trim().length >= 2 && dailyBudget >= 500 && duration in 1..90 && !busy,
                onClick = { onSave(WhappyCampaignDraft(selectedPage.id, selectedPage.name, objective, title, creative, if (destination == "message") "Envoyer un message" else "Découvrir", audience, city, dailyBudget, duration, placement, destination)) },
            ) { Text(if (busy) "Enregistrement…" else "Enregistrer le plan média") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(WapiMobile.compactRadius), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(12.dp)) { Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text(label, color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
}

@Composable
private fun FounderConnector(label: String, connected: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White.copy(alpha = .07f), shape = RoundedCornerShape(11.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) Color(0xFF4DE1A3) else Color(0xFFFFC857)))
            Text("  $label · ${if (connected) "connecté" else "à connecter"}", color = Color.White.copy(alpha = .84f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountContextCard(
    personalName: String,
    personalPhotoUrl: String,
    businessPages: List<WhappyBusinessPage>,
    activeBusinessPageId: String,
    onSelect: (String) -> Unit,
) {
    val activePage = businessPages.firstOrNull { it.id == activeBusinessPageId }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9FD)),
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .16f)),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, null, tint = WhappyBlue)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Identité active", color = WhappyDark, fontWeight = FontWeight.Black)
                    Text("Personnel et Business restent séparés, avec le même numéro WAPI.", color = WhappyMuted, fontSize = 10.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(activePage?.logoUrl ?: personalPhotoUrl, activePage?.name ?: personalName, 42.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(activePage?.name ?: personalName, color = WhappyDark, fontWeight = FontWeight.Black)
                    Text(if (activePage == null) "Compte personnel · Messages et appels personnels" else "Compte Business · Messages, appels, catalogue et publicités", color = WhappyMuted, fontSize = 10.sp)
                }
                if (activePage != null) TextButton(onClick = { onSelect("") }) { Text("Personnel") }
            }
            if (businessPages.isEmpty()) {
                Text("Créez votre page Business pour activer une seconde identité et son espace de vente.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 15.sp)
            } else {
                Text("Vos comptes Business", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                businessPages.forEach { page ->
                    OutlinedButton(
                        onClick = { onSelect(page.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WhappyDark),
                    ) {
                        UserAvatar(page.logoUrl, page.name, 28.dp, shape = RoundedCornerShape(8.dp))
                        Text(page.name, Modifier.weight(1f).padding(start = 9.dp), textAlign = TextAlign.Start)
                        if (page.id == activeBusinessPageId) Icon(Icons.Rounded.Check, "Compte actif", tint = WhappyBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSwitcherDialog(
    personalName: String,
    personalPhotoUrl: String,
    phone: String,
    businessPages: List<WhappyBusinessPage>,
    activeBusinessPageId: String,
    onSelect: (String) -> Unit,
    onOpenBusiness: () -> Unit,
    onOpenProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth(.94f), color = Color.White, shape = RoundedCornerShape(28.dp), shadowElevation = 20.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Changer de compte", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Le profil choisi pilote l’identité des messages, appels et actions Business.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer") }
                }
                AccountSwitcherRow(
                    photoUrl = personalPhotoUrl,
                    name = personalName,
                    subtitle = phone.ifBlank { "Compte personnel WAPI" },
                    selected = activeBusinessPageId.isBlank(),
                    onClick = { onSelect("") },
                )
                if (businessPages.isNotEmpty()) {
                    Text("COMPTES BUSINESS", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                    businessPages.forEach { page ->
                        AccountSwitcherRow(
                            photoUrl = page.logoUrl,
                            name = page.name,
                            subtitle = "Business · ${page.category} · ${page.city}",
                            selected = page.id == activeBusinessPageId,
                            onClick = { onSelect(page.id) },
                        )
                    }
                }
                OutlinedButton(onClick = onOpenBusiness, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.BusinessCenter, null)
                    Text(if (businessPages.isEmpty()) "Créer mon compte Business" else "Gérer mes comptes Business", Modifier.padding(start = 7.dp))
                }
                TextButton(onClick = onOpenProfile, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ouvrir les paramètres du profil") }
            }
        }
    }
}

@Composable
private fun AccountSwitcherRow(photoUrl: String, name: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) WapiSoftBlue else Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) WhappyBlue else WhappyLine),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl, name, 46.dp, shape = RoundedCornerShape(13.dp))
            Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(name, color = WhappyDark, fontWeight = FontWeight.Black); Text(subtitle, color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (selected) Icon(Icons.Rounded.CheckCircle, "Compte actif", tint = WhappyBlue)
        }
    }
}

@Composable
private fun ProfileScreen(
    name: String,
    founder: Boolean,
    verified: Boolean,
    phone: String,
    accountId: String,
    online: Boolean,
    photoUrl: String,
    businessPages: List<WhappyBusinessPage>,
    activeBusinessPageId: String,
    twinReadiness: Int,
    adminMetrics: WapiAdminMetrics,
    preview: Boolean,
    busy: Boolean,
    onUpdatePhoto: (Uri, String) -> Unit,
    onOpenWhappies: () -> Unit,
    onSignOut: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenSpace: (WhappyTab) -> Unit,
    language: WhappyLanguage,
    detectedLanguage: WhappyLanguage,
    onLanguageChange: (WhappyLanguage) -> Unit,
    onSwitchAccount: (String) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_consumer") }
    var localPhoto by remember(photoUrl) { mutableStateOf(photoUrl) }
    var showingMyCode by remember { mutableStateOf(false) }
    var settingDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var privacy by rememberSaveable { mutableStateOf(prefs.getString("privacy", "contacts") ?: "contacts") }
    var messageNotifications by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_messages", true)) }
    var callNotifications by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_calls", true)) }
    var dataSaver by rememberSaveable { mutableStateOf(prefs.getBoolean("data_saver", false)) }
    var compactMode by rememberSaveable { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var protectPreview by rememberSaveable { mutableStateOf(prefs.getBoolean("protect_preview", true)) }
    var typingSounds by rememberSaveable { mutableStateOf(prefs.getBoolean("typing_sounds", true)) }
    var hapticFeedback by rememberSaveable { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }
    var storageUsage by remember { mutableStateOf(WapiMediaStore.usage(context)) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    var profilePhotoToCrop by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        profilePhotoToCrop = uri
    }
    profilePhotoToCrop?.let { source ->
        WapiSquareCropDialog(
            source = source,
            title = "Ajuster la photo du profil",
            onDismiss = { profilePhotoToCrop = null },
            onConfirm = { cropped ->
                localPhoto = cropped.toString()
                profilePhotoToCrop = null
                if (!preview) onUpdatePhoto(cropped, context.contentResolver.getType(cropped) ?: "image/jpeg")
            },
        )
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(localPhoto, name, 78.dp, modifier = if (localPhoto.isBlank()) Modifier else Modifier.clickable { previewPhoto = localPhoto }, shape = RoundedCornerShape(16.dp))
                    IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.size(32.dp).clip(CircleShape).background(WhappyBlue)) { Icon(Icons.Rounded.Photo, t("Changer la photo", "Change photo", "Bongola foto"), tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
                Column(Modifier.weight(1f).padding(start = 15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (founder) WhappyIdentity.founderName else name, Modifier.weight(1f, fill = false), fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (verified || founder) Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 5.dp).size(17.dp))
                    }
                    Text(if (preview) t("Mode démonstration", "Demo mode", "Mode ya komeka") else phone, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp)
                    Text(if (founder) WhappyIdentity.founderBadgeLabel else if (verified) t("Compte vérifié", "Verified account", "Compte endimami") else t("Compte personnel", "Personal account", "Compte ya moto"), Modifier.padding(top = 5.dp), color = if (verified || founder) WapiVerifiedGray else WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { if (localPhoto.isNotBlank()) previewPhoto = localPhoto }, enabled = localPhoto.isNotBlank()) { Text(t("Voir", "View", "Tala"), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { showingMyCode = true }) { Text(t("Mon code", "My code", "Code na ngai"), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        Text(t("La photo reste fixe. Touchez-la pour l’agrandir.", "Your photo stays fixed. Tap it to zoom.", "Foto etikali fixe. Finá yango mpo na kokómisa monene."), Modifier.padding(horizontal = 20.dp, vertical = 2.dp), color = WhappyMuted, fontSize = 10.sp)
        AccountContextCard(
            personalName = if (founder) WhappyIdentity.founderName else name,
            personalPhotoUrl = if (activeBusinessPageId.isBlank()) localPhoto else "",
            businessPages = businessPages,
            activeBusinessPageId = activeBusinessPageId,
            onSelect = onSwitchAccount,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9FD)),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .14f)),
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, null, tint = Color.White) }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text("Informations du compte", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(if (founder) "Compte officiel WAPI · Fondateur" else "Compte personnel WAPI", color = WhappyMuted, fontSize = 10.sp)
                        }
                        Text(if (online) "CLOUD ACTIF" else "HORS LIGNE", color = if (online) WhappyBlue else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) { Text("NUMÉRO", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(phone.ifBlank { "Non renseigné" }, color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Text("WAPI ID", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(if (accountId.isBlank()) "Aperçu local" else accountId.take(12), color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text("Session protégée · synchronisation chiffrée · paramètres propres à cet appareil", color = WhappyMuted, fontSize = 10.sp)
                }
            }
        }
        if (founder) item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = WhappyNavy),
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Visibility, null, tint = Color.White) }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) { Text("Tableau de bord administrateur", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp); Text("Compte fondateur · accès réservé", color = Color.White.copy(alpha = .72f), fontSize = 10.sp) }
                    }
                    Text(
                        if (adminMetrics.serverReady) "Données agrégées en direct par le serveur WAPI."
                        else "Connexion au tableau fondateur en cours — aucune estimation affichée.",
                        color = Color.White.copy(alpha = .82f), fontSize = 11.sp, lineHeight = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("Utilisateurs WAPI", formatCompactCount(adminMetrics.users), Modifier.weight(1f))
                        MetricCard("CA encaissé", formatMoney(adminMetrics.paidRevenue), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("Installations actives", formatCompactCount(adminMetrics.activeInstallations), Modifier.weight(1f))
                        MetricCard("Pages Business", formatCompactCount(adminMetrics.businessPages), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("Stories publiées", formatCompactCount(adminMetrics.stories), Modifier.weight(1f))
                        MetricCard("Chaînes", formatCompactCount(adminMetrics.channels), Modifier.weight(1f))
                    }
                    Surface(color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("CONNECTEURS DE DISTRIBUTION", color = WhappySky, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                            Text("Les téléchargements des stores ne sont jamais inventés : ils apparaissent uniquement après connexion des consoles officielles.", color = Color.White.copy(alpha = .70f), fontSize = 10.sp, lineHeight = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FounderConnector("Google Play", adminMetrics.storePlayConnected, Modifier.weight(1f))
                                FounderConnector("App Store", adminMetrics.storeIosConnected, Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Podcasts ${adminMetrics.radioEpisodes} · Lives actifs ${adminMetrics.activeLives} · source : WAPI Cloud", color = Color.White.copy(alpha = .58f), fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpenSpace(WhappyTab.BUSINESS) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Business", fontSize = 11.sp) }
                        OutlinedButton(onClick = { onOpenSpace(WhappyTab.LIVE) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("En direct", fontSize = 11.sp) }
                        OutlinedButton(onClick = { onOpenSpace(WhappyTab.MESSAGES) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Messages", fontSize = 11.sp) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWhappies), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text("MON WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Mon Jumeau numérique", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Image, voix, mouvements et missions", color = Color.White, fontSize = 11.sp) }; Text("›", color = WhappyBlue, fontSize = 26.sp) } }
        }
        item { ProfileControlCenter(onOpenSpace = onOpenSpace) }
        item {
            ProfileQuickSettings(
                messageNotifications = messageNotifications,
                onMessageNotifications = { enabled ->
                    messageNotifications = enabled
                    prefs.edit().putBoolean("notify_messages", enabled).apply()
                    if (enabled) onEnableNotifications()
                },
                callNotifications = callNotifications,
                onCallNotifications = { enabled ->
                    callNotifications = enabled
                    prefs.edit().putBoolean("notify_calls", enabled).apply()
                    if (enabled) onEnableNotifications()
                },
                dataSaver = dataSaver,
                onDataSaver = { enabled ->
                    dataSaver = enabled
                    prefs.edit().putBoolean("data_saver", enabled).apply()
                },
                compactMode = compactMode,
                onCompactMode = { enabled ->
                    compactMode = enabled
                    prefs.edit().putBoolean("compact_mode", enabled).apply()
                },
                protectPreview = protectPreview,
                onProtectPreview = { enabled ->
                    protectPreview = enabled
                    prefs.edit().putBoolean("protect_preview", enabled).apply()
                },
                typingSounds = typingSounds,
                onTypingSounds = { enabled -> typingSounds = enabled; prefs.edit().putBoolean("typing_sounds", enabled).apply(); if (enabled) WhappySounds.typing(context) },
                hapticFeedback = hapticFeedback,
                onHapticFeedback = { enabled -> hapticFeedback = enabled; prefs.edit().putBoolean("haptic_feedback", enabled).apply(); if (enabled) WhappySounds.haptic(context) },
            )
        }
        item { Card(Modifier.fillMaxWidth().clickable { settingDialog = "Langue" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Language, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(t("Langue de l’application", "App language", "Lokota ya application"), fontWeight = FontWeight.Bold, color = WhappyDark); Text(if (language == WhappyLanguage.AUTOMATIC) "Automatique · ${detectedLanguage.label}" else language.label, color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 23.sp) } } }
        items(listOf("Confidentialité" to "Contrôlez qui peut vous contacter", "Notifications" to "Messages, appels et commandes", "Stockage et données" to "Médias et utilisation réseau", "Aide et sécurité" to "Assistance et appareils connectés")) { setting ->
            Card(Modifier.fillMaxWidth().clickable { settingDialog = setting.first }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Lock, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(setting.first, fontWeight = FontWeight.Bold, color = WhappyDark); Text(setting.second, color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 23.sp) } }
        }
        if (!preview) item { OutlinedButton(onClick = onSignOut, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Se déconnecter de cet appareil") } }
        }
    }
    if (previewPhoto != null) {
        ImageZoomViewer(
            imageSource = previewPhoto.orEmpty(),
            onDismiss = { previewPhoto = null },
        )
    }
    if (showingMyCode) WhappyCodeDialog(name = name, phone = phone, photoUrl = localPhoto, onDismiss = { showingMyCode = false })
    settingDialog?.let { section ->
        AlertDialog(
            onDismissRequest = { settingDialog = null },
            title = { Text(section, fontWeight = FontWeight.Black) },
            text = {
                when (section) {
                   "Langue" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Choisissez la langue utilisée dans la navigation et les espaces principaux. En mode automatique, WAPI utilise la région et la langue de votre téléphone.", "Choose the language used in navigation and main spaces. In automatic mode, WAPI uses your phone language and region.", "Pona lokota ya navigation mpe bisika ya ntina. Na mode automatique, WAPI elandi région mpe lokota ya telefone na yo."), color = WhappyMuted)
                        WhappyLanguage.entries.forEach { option ->
                            Button(
                                onClick = { onLanguageChange(option); settingDialog = null },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (language == option) WhappyBlue else Color.White, contentColor = if (language == option) Color.White else WhappyBlue),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(if (option == WhappyLanguage.AUTOMATIC) "${option.label} · ${detectedLanguage.label}" else option.label, fontWeight = FontWeight.Bold) }
                        }
                    }
                    "Confidentialité" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Qui peut vous contacter ?", color = WhappyMuted); listOf("contacts" to "Mes contacts", "everyone" to "Tous les utilisateurs", "nobody" to "Personne").forEach { option -> OutlinedButton(onClick = { privacy = option.first; prefs.edit().putString("privacy", privacy).apply() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (privacy == option.first) Color.White else Color.Transparent), shape = RoundedCornerShape(13.dp)) { Text(option.second) } } }
                    "Notifications" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Nouveaux messages", fontWeight = FontWeight.Bold); Text("Aperçu privé et badge de conversation", color = WhappyMuted, fontSize = 10.sp) }
                            Switch(messageNotifications, { enabled -> messageNotifications = enabled; prefs.edit().putBoolean("notify_messages", enabled).apply(); if (enabled) onEnableNotifications() })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Appels entrants", fontWeight = FontWeight.Bold); Text("Sonnerie plein écran, décrocher ou refuser", color = WhappyMuted, fontSize = 10.sp) }
                            Switch(callNotifications, { enabled -> callNotifications = enabled; prefs.edit().putBoolean("notify_calls", enabled).apply(); if (enabled) onEnableNotifications() })
                        }
                        OutlinedButton(onClick = onEnableNotifications, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Notifications, null); Text("  Vérifier l’autorisation Android") }
                        Text("Les appels utilisent une alerte prioritaire ; les messages restent masqués sur l’écran verrouillé.", color = WhappyMuted, fontSize = 11.sp)
                    }
                    "Stockage et données" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Économiseur de données", fontWeight = FontWeight.Bold); Text("Réduit le chargement automatique des médias", color = WhappyMuted, fontSize = 11.sp) }; Switch(dataSaver, { dataSaver = it; prefs.edit().putBoolean("data_saver", it).apply() }) }
                        Text("Cache temporaire : ${formatStorageBytes(storageUsage.cacheBytes)}", color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Médias conservés : ${formatStorageBytes(storageUsage.mediaBytes)} · Envois en attente : ${formatStorageBytes(storageUsage.outboxBytes)}", color = WhappyMuted, fontSize = 11.sp)
                        OutlinedButton(onClick = { WapiMediaStore.clearRebuildableCache(context); storageUsage = WapiMediaStore.usage(context) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Delete, null); Text("  Vider uniquement le cache") }
                        Text("Le cache peut être recréé depuis le cloud. Les messages, les pièces jointes en attente et les médias conservés ne sont jamais supprimés par cette action.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("WAPI réunit vos conversations, appels, achats, directs et services. En cas de problème, contactez l’assistance depuis cet appareil.", color = WhappyDark); OutlinedButton(onClick = { uriHandler.openUri("mailto:support@whappy.chat?subject=Aide%20WAPI") }, Modifier.fillMaxWidth()) { Text("Contacter l’assistance") } }
                }
            },
            confirmButton = { TextButton(onClick = { settingDialog = null }) { Text("Terminé") } },
        )
    }
}

@Composable
private fun ProfileControlCenter(onOpenSpace: (WhappyTab) -> Unit) {
    val options = listOf(
        Triple(WhappyTab.MESSAGES, "Messages", "Discussions et demandes"),
        Triple(WhappyTab.CALLS, "Appels", "Voix, vidéo et historique"),
        Triple(WhappyTab.STORIES, "Ma Story", "Texte, photo, vidéo et podcast"),
        Triple(WhappyTab.WEPI, "Assistant", "Assistant privé et actions intelligentes"),
        Triple(WhappyTab.CHANNELS, "Chaînes", "Médias et créateurs suivis"),
        Triple(WhappyTab.LIVE, "Live", "Directs, audio et cadeaux"),
        Triple(WhappyTab.GAMES, "Jeux", "Ludo et défis WAPI"),
        Triple(WhappyTab.BUSINESS, "Business", "Pages, deals et campagnes"),
        Triple(WhappyTab.SERVICES, "Services", "Paiements et outils utiles"),
        Triple(WhappyTab.MARKET, "Marché", "Boutiques et offres"),
    )
    val icons = mapOf<WhappyTab, ImageVector>(
        WhappyTab.STORIES to Icons.Rounded.AutoAwesome,
        WhappyTab.WEPI to Icons.Rounded.SmartToy,
        WhappyTab.MESSAGES to Icons.Rounded.ChatBubble,
        WhappyTab.CALLS to Icons.Rounded.Phone,
        WhappyTab.CHANNELS to Icons.Rounded.Notifications,
        WhappyTab.LIVE to Icons.Rounded.LiveTv,
        WhappyTab.GAMES to Icons.Rounded.Bolt,
        WhappyTab.BUSINESS to Icons.Rounded.BusinessCenter,
        WhappyTab.SERVICES to Icons.Rounded.Payments,
        WhappyTab.MARKET to Icons.Rounded.Storefront,
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8FF)),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("Centre de contrôle", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Toutes les options internes au même endroit", color = WhappyMuted, fontSize = 11.sp)
                }
            }
            options.chunked(2).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowOptions.forEach { option ->
                        ProfileOptionTile(
                            title = option.second,
                            subtitle = option.third,
                            icon = icons.getValue(option.first),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenSpace(option.first) },
                        )
                    }
                    if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileOptionTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEDEEFF)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = WhappyBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("›", color = WhappyMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = WhappyMuted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileQuickSettings(
    messageNotifications: Boolean,
    onMessageNotifications: (Boolean) -> Unit,
    callNotifications: Boolean,
    onCallNotifications: (Boolean) -> Unit,
    dataSaver: Boolean,
    onDataSaver: (Boolean) -> Unit,
    compactMode: Boolean,
    onCompactMode: (Boolean) -> Unit,
    protectPreview: Boolean,
    onProtectPreview: (Boolean) -> Unit,
    typingSounds: Boolean,
    onTypingSounds: (Boolean) -> Unit,
    hapticFeedback: Boolean,
    onHapticFeedback: (Boolean) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("Réglages rapides", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 17.sp)
            QuickSwitchRow(
                icon = Icons.Rounded.Notifications,
                title = "Messages",
                subtitle = "Badges et alertes de conversation",
                checked = messageNotifications,
                onCheckedChange = onMessageNotifications,
            )
            QuickSwitchRow(
                icon = Icons.Rounded.Phone,
                title = "Appels entrants",
                subtitle = "Sonnerie et alerte prioritaire",
                checked = callNotifications,
                onCheckedChange = onCallNotifications,
            )
            QuickSwitchRow(
                icon = Icons.Rounded.AudioFile,
                title = "Sons de saisie",
                subtitle = "Clic discret pendant l’écriture",
                checked = typingSounds,
                onCheckedChange = onTypingSounds,
            )
            QuickSwitchRow(
                icon = Icons.Rounded.Bolt,
                title = "Réponse tactile",
                subtitle = "Vibrations légères pour les actions et les jeux",
                checked = hapticFeedback,
                onCheckedChange = onHapticFeedback,
            )
            QuickSwitchRow(
                icon = Icons.Rounded.Visibility,
                title = "Aperçu privé",
                subtitle = "Masque les contenus sensibles dans les aperçus",
                checked = protectPreview,
                onCheckedChange = onProtectPreview,
            )
            QuickSwitchRow(
                icon = Icons.Rounded.Bolt,
                title = "Mode compact",
                subtitle = "Interface plus serrée pour petits écrans",
                checked = compactMode,
                onCheckedChange = onCompactMode,
            )
            QuickSwitchRow(
                icon = Icons.Rounded.AudioFile,
                title = "Économiseur de données",
                subtitle = "Charge moins de médias en arrière-plan",
                checked = dataSaver,
                onCheckedChange = onDataSaver,
            )
        }
    }
}

@Composable
private fun QuickSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFEDEEFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = WhappyBlue, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(title, color = WhappyDark, fontWeight = FontWeight.Bold)
            Text(subtitle, color = WhappyMuted, fontSize = 10.sp, lineHeight = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WhappyCodeDialog(name: String, phone: String, photoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val normalized = remember(phone) { PhoneNumberFormatter.normalize("+242", phone).orEmpty() }
    val qrCode = remember(normalized) { normalized.takeIf { it.isNotBlank() }?.let(::createWhappyQr) }
    val contactLink = remember(normalized) { normalized.takeIf { it.isNotBlank() }?.let { "https://whappy.chat/contact/${Uri.encode(it)}" }.orEmpty() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).widthIn(max = 430.dp),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.QrCode, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text("Mon identité WAPI", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("À montrer uniquement aux personnes de confiance", color = WhappyMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer") }
                }
                UserAvatar(photoUrl, name, 66.dp, modifier = Modifier.padding(top = 18.dp), shape = RoundedCornerShape(15.dp))
                Text(name.ifBlank { "Compte WAPI" }, Modifier.padding(top = 9.dp), color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 17.sp)
                if (qrCode != null) {
                    Surface(
                        Modifier.padding(top = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(7.dp, WapiSoftBlue),
                    ) {
                        Image(
                            bitmap = qrCode.asImageBitmap(),
                            contentDescription = "Code QR WAPI de $name",
                            modifier = Modifier.padding(13.dp).size(218.dp),
                        )
                    }
                    Text(normalized, Modifier.padding(top = 12.dp), color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text("Scannez pour ajouter ce compte", Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mon lien WAPI", contactLink)) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(15.dp)) { Text("Copier") }
                        Button(onClick = { shareWhappyLink(context, "Contact WAPI", contactLink) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Share, null); Text("  Partager") }
                    }
                } else {
                    Text("Votre code apparaîtra dès que le numéro du profil sera synchronisé.", Modifier.padding(top = 20.dp), color = WhappyMuted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun shareWhappyLink(context: Context, title: String, link: String) {
    val appLink = link.replace("https://whappy.chat/", "whappy://")
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, "$title\n$appLink\n$link") }
    context.startActivity(Intent.createChooser(intent, "Partager avec…"))
}

private fun shareRadioRecording(context: Context, file: File, stationName: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, stationName.ifBlank { "WAPI Radio" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Partager l’émission…"))
}

private fun createWhappyQr(phone: String): Bitmap = QRCodeWriter().encode(
    "whappy://contact/$phone",
    BarcodeFormat.QR_CODE,
    600,
    600,
    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 2),
).let { matrix ->
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        val ink = android.graphics.Color.rgb(0, 102, 207)
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            setPixel(x, y, if (matrix[x, y]) ink else android.graphics.Color.WHITE)
        }
        val canvas = Canvas(this)
        val center = width / 2f
        val tile = width * .165f
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawRoundRect(center - tile / 2f, center - tile / 2f, center + tile / 2f, center + tile / 2f, tile * .23f, tile * .23f, background)
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        canvas.drawRoundRect(center - tile * .39f, center - tile * .39f, center + tile * .39f, center + tile * .39f, tile * .20f, tile * .20f, brand)
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = tile * .48f
            canvas.drawText("W", center, center - (ascent() + descent()) / 2f, this)
        }
    }
}

private fun createWhappyPayloadQr(payload: String): Bitmap = QRCodeWriter().encode(
    payload,
    BarcodeFormat.QR_CODE,
    600,
    600,
).let { matrix ->
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.rgb(16, 46, 59) else android.graphics.Color.WHITE)
        }
    }
}

private suspend fun decodeQrFromImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
            ?: return@withContext null
        try {
            if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
            ).text
        } finally {
            bitmap.recycle()
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadImageBitmap(context: Context, source: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (source.isBlank()) return null
    WapiBitmapMemoryCache.get(source)?.let { return it }
    val bytes = when {
        source.startsWith("http://") || source.startsWith("https://") -> loadRemoteImageBytes(context, source)
        source.startsWith("content://") || source.startsWith("file://") -> context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
        else -> runCatching { File(source).takeIf { it.exists() }?.readBytes() }.getOrNull()
    } ?: return null
    val decoded = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largestEdge = max(info.size.width, info.size.height)
            if (largestEdge > 1_600) {
                val scale = 1_600f / largestEdge.toFloat()
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > 1_600) sampleSize *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }
    return decoded?.asImageBitmap()?.also { WapiBitmapMemoryCache.put(source, it) }
}

/**
 * One image request per source, shared by every avatar and media preview.
 * A short retry absorbs temporary Firebase/CDN handovers without making photos flicker.
 */
private object WapiStableImageLoader {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<androidx.compose.ui.graphics.ImageBitmap?>>()

    suspend fun prefetch(context: Context, source: String) {
        if (!source.startsWith("http")) return
        withContext(Dispatchers.IO) { loadRemoteImageBytes(context, source) }
    }

    suspend fun load(context: Context, source: String): androidx.compose.ui.graphics.ImageBitmap? {
        if (source.isBlank()) return null
        WapiBitmapMemoryCache.get(source)?.let { return it }
        val request = CompletableDeferred<androidx.compose.ui.graphics.ImageBitmap?>()
        val existing = inFlight.putIfAbsent(source, request)
        if (existing != null) return existing.await()
        try {
            var result: androidx.compose.ui.graphics.ImageBitmap? = null
            val attempts = if (source.startsWith("http")) 2 else 1
            for (attempt in 0 until attempts) {
                result = withContext(Dispatchers.IO) { runCatching { loadImageBitmap(context, source) }.getOrNull() }
                if (result != null) break
                if (attempt == 0) delay(180)
            }
            result?.let { WapiBitmapMemoryCache.put(source, it) }
            request.complete(result)
            return result
        } catch (error: Throwable) {
            request.complete(null)
            return null
        } finally {
            inFlight.remove(source, request)
        }
    }
}

private object WapiBitmapMemoryCache {
    private val items = object : LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun get(source: String): androidx.compose.ui.graphics.ImageBitmap? = if (source.isBlank()) null else items.get(source)
    fun put(source: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        if (source.isNotBlank()) items.put(source, bitmap)
    }
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes o"
    bytes < 1_024L * 1_024L -> "%.0f Ko".format(Locale.FRANCE, bytes / 1_024.0)
    bytes < 1_024L * 1_024L * 1_024L -> "%.1f Mo".format(Locale.FRANCE, bytes / (1_024.0 * 1_024.0))
    else -> "%.2f Go".format(Locale.FRANCE, bytes / (1_024.0 * 1_024.0 * 1_024.0))
}

private fun loadRemoteImageBytes(context: Context, source: String): ByteArray? {
    val key = WapiMediaStore.keyFor(source)
    val maximumImageBytes = 20L * 1024L * 1024L
    WapiMediaStore.readCache(context, key, maxBytes = maximumImageBytes)?.let { return it }
    return runCatching {
        val connection = URL(source).openConnection().apply {
            connectTimeout = 7_000
            readTimeout = 10_000
            useCaches = true
        }
        val advertisedLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connection.contentLengthLong
        } else {
            @Suppress("DEPRECATION")
            connection.contentLength.toLong()
        }
        if (advertisedLength > maximumImageBytes) return@runCatching null
        val downloaded = connection.getInputStream().use { input -> readBoundedBytes(input, maximumImageBytes) } ?: return@runCatching null
        if (downloaded.size.toLong() in 1L..maximumImageBytes) {
            WapiMediaStore.writeCache(context, key, downloaded, maxBytes = maximumImageBytes)
            downloaded
        } else null
    }.getOrNull()
}

private fun readBoundedBytes(input: java.io.InputStream, maximumBytes: Long): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > maximumBytes) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@Composable
private fun WapiSquareCropDialog(
    source: Uri,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var panX by remember(source) { mutableFloatStateOf(0f) }
    var panY by remember(source) { mutableFloatStateOf(0f) }
    var busy by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        bitmap = WapiStableImageLoader.load(context, source.toString())
        if (bitmap == null) error = "Cette image ne peut pas être ouverte."
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, color = WhappyDark, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF071827))
                        .pointerInput(source, zoom) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                panX = (panX + pan.x / size.width.coerceAtLeast(1)).coerceIn(-.45f, .45f)
                                panY = (panY + pan.y / size.height.coerceAtLeast(1)).coerceIn(-.45f, .45f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let { image ->
                        Image(
                            image,
                            contentDescription = "Photo à recadrer",
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom; scaleY = zoom
                                translationX = panX * size.width
                                translationY = panY * size.height
                            },
                            contentScale = ContentScale.Crop,
                        )
                    } ?: CircularProgressIndicator(color = Color.White)
                    Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = .92f), RoundedCornerShape(24.dp)))
                }
                Text("Pincez pour zoomer et glissez pour cadrer. La photo sera enregistrée au format carré.", color = WhappyMuted, fontSize = 11.sp)
                error?.let { Text(it, color = Color(0xFFC62828), fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(enabled = bitmap != null && !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { renderSquareCrop(context, source, zoom, panX, panY) } }
                    result.onSuccess { onConfirm(Uri.fromFile(it)) }.onFailure { error = "Le recadrage a échoué. Réessayez avec une autre image." }
                    busy = false
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("Utiliser") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annuler") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}

private fun renderSquareCrop(context: Context, source: Uri, zoom: Float, panX: Float, panY: Float): File {
    val decoded = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
        ?: error("unreadable-image")
    val minimum = min(decoded.width, decoded.height).coerceAtLeast(1)
    val side = (minimum / zoom.coerceIn(1f, 4f)).toInt().coerceIn(1, minimum)
    val centerX = decoded.width / 2f - panX.coerceIn(-.45f, .45f) * minimum
    val centerY = decoded.height / 2f - panY.coerceIn(-.45f, .45f) * minimum
    val left = (centerX - side / 2f).toInt().coerceIn(0, (decoded.width - side).coerceAtLeast(0))
    val top = (centerY - side / 2f).toInt().coerceIn(0, (decoded.height - side).coerceAtLeast(0))
    val cropped = Bitmap.createBitmap(decoded, left, top, side, side)
    val output = if (side == 1_024) cropped else Bitmap.createScaledBitmap(cropped, 1_024, 1_024, true)
    return File(WapiMediaStore.cacheDirectory(context), "wapi-group-${System.currentTimeMillis()}.jpg").also { file ->
        file.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (output !== cropped) cropped.recycle()
        if (cropped !== decoded) decoded.recycle()
        output.recycle()
    }
}

/** Creates a shareable JPEG locally; the original picked image is never uploaded until send. */
private fun renderWapiMeme(context: Context, source: Uri, topText: String, bottomText: String): File {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "invalid-image" }
    var sample = 1
    while (bounds.outWidth / sample > 1_600 || bounds.outHeight / sample > 1_600) sample *= 2
    val decoded = context.contentResolver.openInputStream(source)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("unreadable-image")
    val scale = min(1f, 1_600f / max(decoded.width, decoded.height).toFloat())
    val width = (decoded.width * scale).toInt().coerceAtLeast(1)
    val height = (decoded.height * scale).toInt().coerceAtLeast(1)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(decoded, null, android.graphics.Rect(0, 0, width, height), null)
    if (decoded !== output) decoded.recycle()
    val textSize = (width * 0.083f).coerceIn(30f, 104f)
    drawMemeCaption(canvas, topText, width, height, textSize, atTop = true)
    drawMemeCaption(canvas, bottomText, width, height, textSize, atTop = false)
    return File(WapiMediaStore.cacheDirectory(context), "wapi-meme-${System.currentTimeMillis()}.jpg").also { file ->
        file.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        output.recycle()
    }
}

private fun drawMemeCaption(canvas: Canvas, raw: String, width: Int, height: Int, textSize: Float, atTop: Boolean) {
    val text = raw.trim().uppercase(Locale.FRANCE)
    if (text.isBlank()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }
    val maximumWidth = width * .88f
    val words = text.split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (paint.measureText(candidate) <= maximumWidth || current.isBlank()) current = candidate
        else {
            lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    val visibleLines = lines.take(3)
    val lineHeight = textSize * 1.08f
    val blockHeight = lineHeight * visibleLines.size
    var baseline = if (atTop) textSize + width * .045f else height - width * .045f - blockHeight + textSize
    val stroke = Paint(paint).apply { style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; strokeWidth = (textSize * .12f).coerceAtLeast(5f) }
    val fill = Paint(paint).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE }
    visibleLines.forEach { line ->
        canvas.drawText(line, width / 2f, baseline, stroke)
        canvas.drawText(line, width / 2f, baseline, fill)
        baseline += lineHeight
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun UserAvatar(photoUrl: String, name: String, size: Dp, modifier: Modifier = Modifier, shape: Shape? = null) {
    val context = LocalContext.current
    // Keep the last valid frame while a refreshed Firebase URL is resolving.
    var bitmap by remember { mutableStateOf(WapiBitmapMemoryCache.get(photoUrl)) }
    LaunchedEffect(photoUrl) {
        if (photoUrl.isBlank()) {
            // Firestore can briefly emit an incomplete profile while a contact or
            // group document is being merged. Keep the last decoded frame instead
            // of flashing the initials and making the photo appear to disappear.
            return@LaunchedEffect
        }
        WapiBitmapMemoryCache.get(photoUrl)?.let { cached -> bitmap = cached; return@LaunchedEffect }
        val loaded = WapiStableImageLoader.load(context, photoUrl)
        if (loaded != null) {
            bitmap = loaded
        }
    }
    Box(modifier.size(size).clip(shape ?: RoundedCornerShape((size.value * .22f).dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap = bitmap!!, contentDescription = "Photo de profil de $name", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(initials(name), color = Color.White, fontSize = (size.value * 0.31f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StoryRingAvatar(
    photoUrl: String,
    name: String,
    size: Dp,
    hasUnseenStory: Boolean,
    onClick: (() -> Unit)? = null,
    shape: Shape = CircleShape,
) {
    val storyAccent = Brush.sweepGradient(listOf(WhappyBlue, WhappySky, Color(0xFF0066CF), WhappyBlue))
    val inset = if (hasUnseenStory) 4.dp else 0.dp
    Box(
        Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .border(if (hasUnseenStory) 3.dp else 0.dp, storyAccent, shape)
            .padding(inset),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(
            photoUrl = photoUrl,
            name = name,
            size = if (hasUnseenStory) size - 8.dp else size,
            shape = shape,
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(WapiSoftBlue), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChatBubble, null, tint = WhappyBlue, modifier = Modifier.size(28.dp))
        }
        Text(title, Modifier.padding(top = 18.dp), fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark, textAlign = TextAlign.Center)
        Text(body, Modifier.padding(top = 7.dp), color = WhappyMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "WH" }

private fun formatTime(timestamp: Long): String = if (timestamp <= 0) "" else SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(timestamp))

private fun formatConversationMoment(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    return when {
        isSameDay(timestamp, now) -> formatTime(timestamp)
        isSameDay(timestamp, now - 86_400_000L) -> "Hier"
        else -> SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(timestamp))
    }
}

private fun formatStoryTime(timestamp: Long): String {
    if (timestamp <= 0L) return "à l’instant"
    val now = System.currentTimeMillis()
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "à l’instant"
        elapsed < 3_600_000L -> "il y a ${elapsed / 60_000L} min"
        isSameDay(timestamp, now) -> "Aujourd’hui · ${formatTime(timestamp)}"
        isSameDay(timestamp, now - 86_400_000L) -> "Hier · ${formatTime(timestamp)}"
        else -> "${SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(timestamp))} · ${formatTime(timestamp)}"
    }
}

private fun formatLastSeen(timestamp: Long): String {
    if (timestamp <= 0L) return "hors ligne"
    val now = Calendar.getInstance()
    val seen = Calendar.getInstance().apply { timeInMillis = timestamp }
    val time = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(timestamp))
    return if (now.get(Calendar.YEAR) == seen.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == seen.get(Calendar.DAY_OF_YEAR)) {
        "vu à $time"
    } else {
        "vu le ${SimpleDateFormat("d MMM", Locale.FRANCE).format(Date(timestamp))} à $time"
    }
}

private fun isSameDay(first: Long, second: Long): Boolean {
    if (first <= 0L || second <= 0L) return first == second
    val left = Calendar.getInstance().apply { timeInMillis = first }
    val right = Calendar.getInstance().apply { timeInMillis = second }
    return left.get(Calendar.ERA) == right.get(Calendar.ERA) && left.get(Calendar.YEAR) == right.get(Calendar.YEAR) && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
}

private fun formatMessageDay(timestamp: Long): String {
    if (timestamp <= 0L) return "Envoi en cours"
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    if (isSameDay(timestamp, today.timeInMillis)) return "Aujourd’hui"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (isSameDay(timestamp, today.timeInMillis)) return "Hier"
    return SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(target.time).replaceFirstChar { it.titlecase(Locale.FRANCE) }
}

private fun formatCallMoment(timestamp: Long): String {
    if (timestamp <= 0L) return "Date inconnue"
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val time = formatTime(timestamp)
    return when {
        isSameDay(timestamp, today.timeInMillis) -> "Aujourd’hui à $time"
        isSameDay(timestamp, yesterday.timeInMillis) -> "Hier à $time"
        else -> "${SimpleDateFormat("d MMMM", Locale.FRANCE).format(Date(timestamp))} à $time"
    }
}

private fun formatMoney(amount: Long): String = String.format(Locale.FRANCE, "%,d FCFA", amount).replace('\u202f', ' ')

private fun formatShortDate(timestamp: Long): String = if (timestamp <= 0) "—" else SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(timestamp))
