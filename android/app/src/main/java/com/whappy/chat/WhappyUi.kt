@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.whappy.chat
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.rounded.PanTool

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Dashboard
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.unit.Velocity
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
import kotlin.math.ceil
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

private data class WapiAdminAdReview(
    val id: String,
    val pageName: String,
    val title: String,
    val creative: String,
    val city: String,
    val countryCode: String,
    val totalBudget: Long,
    val targetImpressions: Long,
    val days: Int,
    val createdAt: Long,
)

/** Founder dashboard values always come from the signed-in account's data. */
private data class WapiAdminMetrics(
    val paidRevenue: Long = 0L,
    val paidOrders: Int = 0,
    val issuedInvoices: Int = 0,
    val outstandingReceivables: Long = 0L,
    val subscribers: Int = 0,
    val radioEpisodes: Int = 0,
    val activeLives: Int = 0,
    val users: Int = 0,
    val activeInstallations: Int = 0,
    val businessPages: Int = 0,
    val stories: Int = 0,
    val channels: Int = 0,
    val adCampaigns: Int = 0,
    val activeAdCampaigns: Int = 0,
    val pendingAdCampaigns: Int = 0,
    val adImpressions: Int = 0,
    val adClicks: Int = 0,
    val officialDownloads: Int? = null,
    val storePlayConnected: Boolean = false,
    val storeIosConnected: Boolean = false,
    val serverReady: Boolean = false,
    val downloadsMeasured: Boolean = false,
    val generatedAt: Long = 0L,
    val pendingReviews: List<WapiAdminAdReview> = emptyList(),
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
    onPublishListing: (String, String, String, String, String, String, Uri?) -> Unit,
    onRespondToMarketplaceListing: (WhappyListing, String) -> Unit,
    onPrepareMarketplaceBoost: (WhappyListing) -> Unit,
    onCreateBusinessPage: (String, String, String, String, String, String) -> Unit,
    onUpdateBusinessPage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onUpdateBusinessLogo: (WhappyBusinessPage, Uri, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onUpdateCampaignDelivery: (String, String) -> Unit,
    onSponsoredCampaignClick: (WhappyCampaign) -> Unit,
    onSponsoredCampaignDismiss: (WhappyCampaign) -> Unit,
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
        onRespondToMarketplaceListing = onRespondToMarketplaceListing,
        onPrepareMarketplaceBoost = onPrepareMarketplaceBoost,
        onCreateBusinessPage = onCreateBusinessPage,
        onUpdateBusinessPage = onUpdateBusinessPage,
        onUpdateBusinessLogo = onUpdateBusinessLogo,
        onCreateCampaign = onCreateCampaign,
        onUpdateCampaignDelivery = onUpdateCampaignDelivery,
        onSponsoredCampaignClick = onSponsoredCampaignClick,
        onSponsoredCampaignDismiss = onSponsoredCampaignDismiss,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WAPI",
                    color = WhappyDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    letterSpacing = (-1).sp,
                )
                Box(
                    Modifier
                        .padding(start = 7.dp, top = 17.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WhappySky),
                )
            }
            Text(
                "Vos échanges, simplement.",
                Modifier.padding(top = 8.dp),
                color = WhappyMuted,
                fontSize = 14.sp,
            )
            CircularProgressIndicator(
                Modifier.padding(top = 30.dp).size(24.dp),
                color = WhappyBlue,
                strokeWidth = 2.5.dp,
            )
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
                modifier = Modifier.fillMaxSize().padding(horizontal = maxWidth * horizontal, vertical = 18.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                BrandHeader(subtitle = "Simple, privé, connecté", avatar = false)
                Column(Modifier.padding(horizontal = 4.dp, vertical = 24.dp)) {
                    Text("Vos échanges, simplement.", color = WhappyDark, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.55).sp)
                    Text("Une identité WAPI pour vos messages, appels et activités.", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 12.sp)
                }
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            when (state.stage) {
                                AuthStage.PHONE -> "Votre numéro WAPI"
                                AuthStage.CODE -> "Vérification rapide"
                                AuthStage.PROFILE -> "Finalisez votre profil"
                            },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
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
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(54.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WhappyBlue, contentColor = Color.White, disabledContainerColor = WhappySurface, disabledContentColor = WhappyMuted),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(label, color = Color.White, fontWeight = FontWeight.Bold)
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
    onPublishListing: (String, String, String, String, String, String, Uri?) -> Unit,
    onRespondToMarketplaceListing: (WhappyListing, String) -> Unit,
    onPrepareMarketplaceBoost: (WhappyListing) -> Unit,
    onCreateBusinessPage: (String, String, String, String, String, String) -> Unit,
    onUpdateBusinessPage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onUpdateBusinessLogo: (WhappyBusinessPage, Uri, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onUpdateCampaignDelivery: (String, String) -> Unit,
    onSponsoredCampaignClick: (WhappyCampaign) -> Unit,
    onSponsoredCampaignDismiss: (WhappyCampaign) -> Unit,
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
    val callController = LocalWhappyCalls.current
    LaunchedEffect(isBusinessAccount, activeBusinessPage?.id, accountDisplayName, activePhotoUrl) {
        callController?.setActiveIdentity(
            businessPageId = activeBusinessPage?.id?.takeIf { isBusinessAccount }.orEmpty(),
            displayName = accountDisplayName,
            photoUrl = activePhotoUrl,
        )
    }
    val visibleConversations = if (isBusinessAccount) {
        state.conversations.filter { it.profileType == "business" && it.businessPageId == activeBusinessPage.id }
    } else {
        state.conversations.filter { it.profileType != "business" }
    }
    val mainContext = LocalContext.current
    val founderActionScope = rememberCoroutineScope()
    val recentSpacesPreferences = remember { WhappyFastStorage.preferences(mainContext, "wapi_recent_spaces") }
    val discoverableSpaces = remember {
        setOf(
            WhappyTab.MOMENTS, WhappyTab.STORIES, WhappyTab.WEPI, WhappyTab.MARKET,
            WhappyTab.LIVE, WhappyTab.RADIO, WhappyTab.PODCASTS, WhappyTab.GAMES,
            WhappyTab.SERVICES, WhappyTab.BUSINESS, WhappyTab.PROFILE,
        )
    }
    var recentSpaces by remember {
        mutableStateOf(
            recentSpacesPreferences.getString("tabs", "").orEmpty().split(',')
                .mapNotNull { value -> runCatching { WhappyTab.valueOf(value) }.getOrNull() }
                .filter { it in discoverableSpaces }
                .distinct()
                .take(6),
        )
    }
    LaunchedEffect(currentTab) {
        if (currentTab in discoverableSpaces) {
            recentSpaces = (listOf(currentTab) + recentSpaces.filterNot { it == currentTab }).take(6)
            recentSpacesPreferences.edit().putString("tabs", recentSpaces.joinToString(",") { it.name }).apply()
        }
    }
    var founderMetrics by remember(isFounderAccount, preview) { mutableStateOf<WapiAdminMetrics?>(null) }
    var founderMetricsLoading by remember(isFounderAccount, preview) { mutableStateOf(false) }
    var founderMetricsError by remember(isFounderAccount, preview) { mutableStateOf<String?>(null) }
    var founderMetricsRefresh by remember(isFounderAccount, preview) { mutableIntStateOf(0) }
    var founderReviewBusyId by remember(isFounderAccount, preview) { mutableStateOf("") }
    var founderReviewError by remember(isFounderAccount, preview) { mutableStateOf<String?>(null) }
    LaunchedEffect(isFounderAccount, preview, founderMetricsRefresh) {
        if (!isFounderAccount || preview) {
            founderMetrics = null
            founderMetricsLoading = false
            founderMetricsError = null
            return@LaunchedEffect
        }
        founderMetricsLoading = true
        founderMetricsError = null
        runCatching {
            val payload = FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("getFounderDashboard")
                .call()
                .await()
                .data as? Map<*, *> ?: error("Réponse du tableau fondateur invalide")
            val stores = payload["storeIntegrations"] as? Map<*, *>
            val ads = payload["ads"] as? Map<*, *>
            val billing = payload["billing"] as? Map<*, *>
            val pendingReviews = (payload["pendingAdReviews"] as? List<*>).orEmpty().mapNotNull { raw ->
                val value = raw as? Map<*, *> ?: return@mapNotNull null
                val id = value["id"]?.toString().orEmpty()
                if (id.isBlank()) return@mapNotNull null
                WapiAdminAdReview(
                    id = id,
                    pageName = value["pageName"]?.toString().orEmpty().ifBlank { "Business WAPI" },
                    title = value["title"]?.toString().orEmpty().ifBlank { "Campagne WAPI" },
                    creative = value["creative"]?.toString().orEmpty(),
                    city = value["city"]?.toString().orEmpty(),
                    countryCode = value["countryCode"]?.toString().orEmpty(),
                    totalBudget = (value["totalBudget"] as? Number)?.toLong() ?: 0L,
                    targetImpressions = (value["targetImpressions"] as? Number)?.toLong() ?: 0L,
                    days = (value["days"] as? Number)?.toInt() ?: 1,
                    createdAt = (value["createdAt"] as? Number)?.toLong() ?: 0L,
                )
            }
            WapiAdminMetrics(
                paidRevenue = (payload["paidRevenue"] as? Number)?.toLong() ?: 0L,
                paidOrders = (payload["paidOrders"] as? Number)?.toInt() ?: 0,
                issuedInvoices = (billing?.get("issuedInvoices") as? Number)?.toInt() ?: 0,
                outstandingReceivables = (billing?.get("outstandingReceivables") as? Number)?.toLong() ?: 0L,
                radioEpisodes = (payload["radioEpisodes"] as? Number)?.toInt() ?: 0,
                activeLives = (payload["activeLives"] as? Number)?.toInt() ?: 0,
                users = (payload["users"] as? Number)?.toInt() ?: 0,
                activeInstallations = (payload["activeInstallations"] as? Number)?.toInt() ?: 0,
                businessPages = (payload["businessPages"] as? Number)?.toInt() ?: 0,
                stories = (payload["stories"] as? Number)?.toInt() ?: 0,
                channels = (payload["channels"] as? Number)?.toInt() ?: 0,
                adCampaigns = (ads?.get("campaigns") as? Number)?.toInt() ?: 0,
                activeAdCampaigns = (ads?.get("activeCampaigns") as? Number)?.toInt() ?: 0,
                pendingAdCampaigns = (ads?.get("pendingCampaigns") as? Number)?.toInt() ?: 0,
                adImpressions = (ads?.get("impressions") as? Number)?.toInt() ?: 0,
                adClicks = (ads?.get("clicks") as? Number)?.toInt() ?: 0,
                officialDownloads = (stores?.get("totalDownloads") as? Number)?.toInt(),
                storePlayConnected = stores?.get("playStore") == true,
                storeIosConnected = stores?.get("appStore") == true,
                serverReady = true,
                downloadsMeasured = stores?.get("totalDownloads") is Number,
                generatedAt = (payload["generatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                pendingReviews = pendingReviews,
            )
        }.onSuccess { founderMetrics = it }
            .onFailure {
                founderMetrics = null
                founderMetricsError = when (it) {
                    is FirebaseFunctionsException -> it.message ?: "Le serveur administrateur est indisponible."
                    else -> "Connexion au serveur administrateur impossible."
                }
            }
        founderMetricsLoading = false
    }
    val localAdminMetrics = WapiAdminMetrics(
        paidRevenue = state.paymentNotices.filter { it.status == "paid" }.sumOf { it.amount },
        subscribers = state.channels.filter { it.ownerId == accountUserId }.sumOf { it.memberCount },
        radioEpisodes = state.radioEpisodes.count { it.ownerId == accountUserId },
        activeLives = state.lives.count { it.hostId == accountUserId && it.status == "live" },
    )
    val adminMetrics = if (isFounderAccount) founderMetrics ?: WapiAdminMetrics() else localAdminMetrics
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
        recentTabs = recentSpaces,
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
                            Text("!", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(data.visuals.message, Modifier.weight(1f).padding(horizontal = 11.dp), color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                        TextButton(onClick = data::dismiss) { Text("OK", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        bottomBar = {
            if (selected == null && selectedChannel == null && !showTwinStudio && !gameSessionActive) {
                WhappyBottomBar(currentTab, isBusinessAccount, onTab) { showAppHub = true }
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
                    online = preview || state.online,
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
                    onOpenMember = { member ->
                        if (!preview) onOpenContact(WhappyContact(member))
                    },
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
                        subtitle = if (preview) "Aperçu" else if (state.online) "Connecté" else "Hors connexion",
                        avatar = true,
                        name = accountDisplayName,
                        photoUrl = activePhotoUrl,
                        businessMode = isBusinessAccount,
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
                    sponsoredCampaigns = if (preview) demoCampaigns.filter { it.status == "active" } else state.sponsoredCampaigns,
                    onTab = onTab,
                    onOpenWhappies = { showTwinStudio = true },
                    onSponsoredCampaignClick = onSponsoredCampaignClick,
                    onSponsoredCampaignDismiss = onSponsoredCampaignDismiss,
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
                    accountName = accountDisplayName,
                    accountPhotoUrl = activePhotoUrl,
                    businessMode = isBusinessAccount,
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
                    recentTabs = recentSpaces,
                    onOpenRecent = onTab,
                )
                WhappyTab.MESSAGES -> MessagesScreen(
                    conversations = if (preview) demoConversations else visibleConversations,
                    accountName = accountDisplayName,
                    accountPhotoUrl = activePhotoUrl,
                    businessMode = isBusinessAccount,
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
                            recentTabs = recentSpaces,
                            onOpenRecent = onTab,
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
                            businessPageId = activeBusinessPage?.id?.takeIf { isBusinessAccount }.orEmpty(),
                            businessName = activeBusinessPage?.name?.takeIf { isBusinessAccount }.orEmpty(),
                            businessPhotoUrl = activeBusinessPage?.logoUrl?.takeIf { isBusinessAccount }.orEmpty(),
                            onOpenConversation = { if (preview) previewConversation = it else onOpenConversation(it) },
                        )
                        WhappyTab.MARKET -> MarketScreen(
                            listings = if (preview) demoListings else state.listings,
                            businessPages = if (preview) demoBusinessPages else state.businessPages,
                            preview = preview,
                            busy = state.actionBusy,
                            accountDisplayName = accountDisplayName,
                            accountPhotoUrl = activePhotoUrl,
                            activeBusinessPageId = state.activeBusinessPageId,
                            onPublish = onPublishListing,
                            onOpenBusiness = { onTab(WhappyTab.BUSINESS) },
                            onContactBusiness = onContactBusiness,
                            onRespond = onRespondToMarketplaceListing,
                            onPrepareBoost = onPrepareMarketplaceBoost,
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
                            onContactBusiness = onContactBusiness,
                            pages = if (preview) demoBusinessPages else state.businessPages,
                            activeBusinessPageId = state.activeBusinessPageId,
                            conversations = if (preview) demoConversations else state.conversations,
                            campaigns = if (preview) demoCampaigns else state.campaigns,
                            adMetrics = if (preview) emptyMap() else state.adMetrics,
                            deals = if (preview) demoDeals else state.deals,
                            paymentNotices = if (preview) demoPaymentNotices else state.paymentNotices,
                            preview = preview,
                            busy = state.actionBusy,
                            onCreatePage = onCreateBusinessPage,
                            onUpdatePage = onUpdateBusinessPage,
                            onUpdateLogo = onUpdateBusinessLogo,
                            onCreateCampaign = onCreateCampaign,
                            onUpdateCampaignDelivery = onUpdateCampaignDelivery,
                            onCreateDeal = onCreateDeal,
                            onUpdateDealStatus = onUpdateDealStatus,
                            onMarkPaymentRead = onMarkPaymentRead,
                            onEnableNotifications = onEnableNotifications,
                            onActivatePage = onSelectAccountProfile,
                            onOpenTwin = { onTab(WhappyTab.WEPI) },
                            onOpenMessages = { onTab(WhappyTab.MESSAGES) },
                            onOpenCalls = { onTab(WhappyTab.CALLS) },
                            onOpenClient = { conversation ->
                                if (!preview && conversation.businessPageId.isNotBlank()) onSelectAccountProfile(conversation.businessPageId)
                                if (preview) previewConversation = conversation else onOpenConversation(conversation)
                            },
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
                            adminMetricsLoading = founderMetricsLoading,
                            adminMetricsError = founderMetricsError,
                            adminReviewBusyId = founderReviewBusyId,
                            adminReviewError = founderReviewError,
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
                            onReloadAdminMetrics = { founderMetricsRefresh += 1 },
                            onReviewAdCampaign = { campaignId, action, paymentReference, reviewNote ->
                                if (founderReviewBusyId.isBlank()) {
                                    founderReviewBusyId = campaignId
                                    founderReviewError = null
                                    founderActionScope.launch {
                                        runCatching {
                                            FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("reviewAdCampaign").call(
                                                mapOf(
                                                    "campaignId" to campaignId,
                                                    "action" to action,
                                                    "paymentReference" to paymentReference,
                                                    "reviewNote" to reviewNote,
                                                ),
                                            ).await()
                                        }.onSuccess { founderMetricsRefresh += 1 }
                                            .onFailure { founderReviewError = it.message ?: "La campagne n’a pas été traitée." }
                                        founderReviewBusyId = ""
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun BrandHeader(subtitle: String, avatar: Boolean, name: String = "", photoUrl: String = "", businessMode: Boolean = false, unread: Int = 0, founder: Boolean = false, onActivity: () -> Unit = {}, onProfile: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WhappyBackground,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = WapiMobile.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("WAPI", color = WhappyDark, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.65).sp)
                    Box(Modifier.padding(start = 5.dp).size(6.dp).clip(CircleShape).background(WhappySky))
                    if (businessMode) {
                        Text(
                            "BUSINESS",
                            Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(WapiSoftBlue).padding(horizontal = 7.dp, vertical = 3.dp),
                            color = WhappyDeepBlue,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .55.sp,
                        )
                    }
                }
                Text(
                    if (businessMode) name.ifBlank { "Compte professionnel" } else subtitle,
                    color = WhappyMuted,
                    fontSize = 9.sp,
                    fontWeight = if (businessMode) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (avatar) {
                IconButton(
                    onClick = onActivity,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(Icons.Rounded.Notifications, "Notifications", tint = WhappyNavy, modifier = Modifier.size(21.dp))
                        if (unread > 0) {
                            Box(Modifier.size(17.dp).clip(CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                                Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(9.dp))
                UserAvatar(photoUrl, name, 42.dp, Modifier.wapiClickable(onClick = onProfile), RoundedCornerShape(13.dp))
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
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Notifications", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Activité importante, sans bruit", color = Color.White.copy(alpha = .76f), fontSize = 11.sp) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFFF5FAFD)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TOUT", Modifier.clip(CircleShape).background(WhappyBlue).padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("BUSINESS", Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("LIVE", Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 520.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val liveNow = lives.filter { it.status == "live" }.take(2)
                if (liveNow.isNotEmpty()) {
                    item { Text("EN DIRECT", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    items(liveNow, key = { "activity-live-${it.id}" }) { live ->
                        Card(Modifier.fillMaxWidth().wapiClickable(onClick = onOpenLive), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5FAFD)), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .10f))) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { UserAvatar(live.hostPhotoUrl, live.hostName, 40.dp, shape = CircleShape); Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(live.title, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1); Text("${live.hostName} · ${live.viewerCount} spectateurs", color = WhappyMuted, fontSize = 10.sp) }; Text("›", color = WhappyBlue, fontSize = 22.sp) } }
                    }
                }
                item { Text("PAIEMENTS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                if (notices.isEmpty()) item { Text("Aucune nouvelle transaction.", color = WhappyMuted, fontSize = 12.sp) }
                items(notices.take(8), key = { "activity-payment-${it.id}" }) { notice ->
                    val unread = !notice.read && notice.id !in locallyRead
                    Card(Modifier.fillMaxWidth().wapiClickable { if (unread) onRead(notice.id) else onOpenBusiness() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (unread) Color(0xFFF2F9FF) else Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, if (unread) WhappyBlue.copy(alpha = .18f) else WhappyLine)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (unread) WhappyBlue else WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Payments, null, tint = if (unread) Color.White else WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Paiement de ${notice.buyerName}", color = WhappyDark, fontWeight = FontWeight.Bold); if (unread) Box(Modifier.padding(start = 6.dp).size(7.dp).clip(CircleShape).background(WhappyBlue)) }; Text(notice.provider, color = WhappyMuted, fontSize = 10.sp) }; Text("+${formatMoney(notice.amount)}", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
                }
            }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Plus tard") }; Button(onClick = onOpenBusiness, shape = RoundedCornerShape(13.dp)) { Text("Ouvrir Business") } }
            }
        }
    }
}

@Composable
private fun WhappyBottomBar(selected: WhappyTab, businessMode: Boolean, onTab: (WhappyTab) -> Unit, onMore: () -> Unit) {
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
    val visibleTabs = if (businessMode) {
        listOf(WhappyTab.MESSAGES, WhappyTab.CALLS, WhappyTab.BUSINESS, WhappyTab.WEPI)
    } else {
        listOf(WhappyTab.MESSAGES, WhappyTab.CALLS, WhappyTab.STORIES, WhappyTab.WEPI)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
    Column(Modifier.navigationBarsPadding()) {
        NavigationBar(modifier = Modifier.height(68.dp), containerColor = Color.Transparent, tonalElevation = 0.dp) {
        visibleTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { WhappySounds.haptic(context); onTab(tab) },
                icon = { Icon(icons.getValue(tab), mobileTabLabel(tab, LocalWhappyLanguage.current), modifier = Modifier.size(21.dp)) },
                label = { Text(mobileTabLabel(tab, LocalWhappyLanguage.current), fontSize = 9.sp, maxLines = 1, fontWeight = if (selected == tab) FontWeight.SemiBold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyDeepBlue, selectedTextColor = WhappyDeepBlue, unselectedIconColor = WhappyMuted, unselectedTextColor = WhappyMuted, indicatorColor = WapiSoftBlue),
            )
        }
        NavigationBarItem(
            selected = selected !in visibleTabs,
            onClick = { WhappySounds.haptic(context); onMore() },
            icon = { Icon(Icons.Rounded.GridView, whappyText(LocalWhappyLanguage.current, "Tout", "All", "Nyonso"), modifier = Modifier.size(21.dp)) },
            label = { Text(whappyText(LocalWhappyLanguage.current, "Tout", "All", "Nyonso"), fontSize = 9.sp, maxLines = 1, fontWeight = if (selected !in visibleTabs) FontWeight.SemiBold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyDeepBlue, selectedTextColor = WhappyDeepBlue, unselectedIconColor = WhappyMuted, unselectedTextColor = WhappyMuted, indicatorColor = WapiSoftBlue),
        )
        }
    }
}

}

private data class WhappyFeatureShortcut(val tab: WhappyTab?, val title: String, val subtitle: String, val icon: ImageVector)

private fun wapiFeatureShortcuts() = listOf(
    WhappyFeatureShortcut(WhappyTab.MOMENTS, "Accueil", "Vue générale WAPI", Icons.Rounded.Home),
    WhappyFeatureShortcut(WhappyTab.CONTACTS, "Contacts", "Personnes et QR", Icons.Rounded.PersonAdd),
    WhappyFeatureShortcut(WhappyTab.CHANNELS, "Chaînes", "Médias et créateurs", Icons.Rounded.Notifications),
    WhappyFeatureShortcut(WhappyTab.CALLS, "Appels", "Audio et vidéo", Icons.Rounded.Phone),
    WhappyFeatureShortcut(WhappyTab.WEPI, "WIA", "Mémoire et assistance", Icons.Rounded.SmartToy),
    WhappyFeatureShortcut(WhappyTab.MARKET, "Marché", "Acheter et vendre", Icons.Rounded.Storefront),
    WhappyFeatureShortcut(WhappyTab.RADIO, "Radio", "Créer une émission", Icons.Rounded.Radio),
    WhappyFeatureShortcut(WhappyTab.PODCASTS, "Podcasts", "Écouter et reprendre", Icons.Rounded.AudioFile),
    WhappyFeatureShortcut(WhappyTab.LIVE, "Direct", "Diffuser maintenant", Icons.Rounded.LiveTv),
    WhappyFeatureShortcut(WhappyTab.GAMES, "Jeux", "Défis et tournois", Icons.Rounded.Bolt),
    WhappyFeatureShortcut(WhappyTab.SERVICES, "Services", "Paiements et outils", Icons.Rounded.Payments),
    WhappyFeatureShortcut(WhappyTab.BUSINESS, "Business", "Pages, Deals et Ads", Icons.Rounded.BusinessCenter),
    WhappyFeatureShortcut(null, "Jumeau numérique", "Identité, voix et studio", Icons.Rounded.SmartToy),
    WhappyFeatureShortcut(WhappyTab.PROFILE, "Profil", "Compte et sécurité", Icons.Rounded.Person),
)

@Composable
private fun WhappyFeatureHubDialog(recentTabs: List<WhappyTab>, onOpen: (WhappyTab) -> Unit, onOpenTwin: () -> Unit, onDismiss: () -> Unit) {
    val shortcuts = wapiFeatureShortcuts()
    val recentShortcuts = recentTabs.mapNotNull { tab -> shortcuts.firstOrNull { it.tab == tab } }.ifEmpty { shortcuts.filter { it.tab in listOf(WhappyTab.LIVE, WhappyTab.WEPI, WhappyTab.GAMES) } }.take(6)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WapiSheet, dragHandle = { Box(Modifier.padding(top = 12.dp).width(42.dp).height(4.dp).clip(CircleShape).background(WhappyMuted.copy(alpha = .28f))) }) {
            Column(Modifier.padding(horizontal = WapiMobile.screen, vertical = 8.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(WhappyAurora), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Schedule, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Récents", color = WhappyDark, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Vos derniers espaces WAPI", color = WhappyMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = WhappyMuted) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    recentShortcuts.forEach { item ->
                        Surface(
                            modifier = Modifier.width(104.dp).height(88.dp).wapiClickable { item.tab?.let(onOpen) ?: onOpenTwin() },
                            color = WapiBlueMist,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WhappySky.copy(alpha = .24f)),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = WhappyBlue, modifier = Modifier.size(20.dp)) }
                                    Spacer(Modifier.weight(1f))
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF1FA971)))
                                }
                                Text(item.title, color = WhappyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
                Text("Toutes les apps WAPI", color = WhappyDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                shortcuts.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        rowItems.forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f).height(88.dp).wapiClickable { item.tab?.let(onOpen) ?: onOpenTwin() },
                                color = WapiElevated,
                                shape = RoundedCornerShape(19.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .68f)),
                            ) {
                                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Box(Modifier.size(35.dp).clip(RoundedCornerShape(12.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = WhappyDeepBlue, modifier = Modifier.size(20.dp)) }
                                    Text(item.title, color = WhappyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
    }
}

private fun mobileTabLabel(tab: WhappyTab, language: WhappyLanguage): String = when (tab) {
    WhappyTab.MOMENTS -> whappyText(language, "Accueil", "Home", "Ndako")
    WhappyTab.STORIES -> whappyText(language, "Actus", "Updates", "Sango")
    WhappyTab.MESSAGES -> whappyText(language, "Messages", "Messages", "Nsango")
    WhappyTab.WEPI -> "WIA"
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
                // Do not open the local source URI: its EXIF orientation and
                // dimensions can briefly produce the wrong shape. The viewer
                // opens only after the normalized server Story is confirmed.
                openOwnStoryAfterCount = statuses.count {
                    it.authorId == currentUserId && it.mediaName != "Publication en cours"
                }
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
    val confirmedMyStories = myStories.filterNot { it.mediaName == "Publication en cours" }
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

    LaunchedEffect(confirmedMyStories.size, openOwnStoryAfterCount) {
        if (openOwnStoryAfterCount >= 0 && confirmedMyStories.size > openOwnStoryAfterCount) {
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
                    Text("Stories, directs et chaînes", color = WhappyMuted, fontSize = 12.sp)
                }
                Surface(
                    modifier = Modifier.size(44.dp).clip(CircleShape).wapiClickable {
                        mediaUri = null; mediaType = ""; mediaName = ""
                        showComposer = true
                    },
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("Aa", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
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
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.padding(vertical = 15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Stories", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        if (myStories.isNotEmpty()) {
                            TextButton(onClick = { onOpenSpace(WhappyTab.BUSINESS) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)) {
                                Icon(Icons.Rounded.Bolt, "Booster une Story", tint = WhappyBlue, modifier = Modifier.size(15.dp))
                                Text(" Booster", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("24 H", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StoryCircle(
                        name = "Ma Story",
                        subtitle = when { myStories.isEmpty() -> "Ajouter"; confirmedMyStories.isEmpty() -> "Publication…"; confirmedMyStories.size == 1 -> formatStoryTime(confirmedMyStories.last().createdAt); else -> "${confirmedMyStories.size} Stories · ${formatStoryTime(confirmedMyStories.last().createdAt)}" },
                        active = myStories.any { !it.viewedByCurrentUser && it.id !in locallyOpenedStoryIds },
                        story = confirmedMyStories.lastOrNull(),
                        profilePhotoUrl = currentUserPhotoUrl,
                        publishing = myStories.any { it.mediaName == "Publication en cours" },
                        onClick = {
                            if (myStories.isEmpty()) openStoryGallery()
                            else if (confirmedMyStories.isNotEmpty()) {
                                locallyOpenedStoryIds = locallyOpenedStoryIds + confirmedMyStories.map { it.id }
                                selectedStoryAuthorId = currentUserId
                            }
                        },
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
        item { ActusSectionTitle("À suivre", "Chaînes, radios et podcasts") }
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
        item { ActusSectionTitle("Découvrir", "Services WAPI") }
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
                        Column(Modifier.weight(1f).padding(start = 11.dp)) { Text("Nouvelle Story", color = WhappyDark, fontWeight = FontWeight.Medium, fontSize = 20.sp); Text("Visible pendant 24 h", color = WhappyMuted, fontSize = 10.sp) }
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
                            Modifier.fillMaxWidth().wapiClickable { stopStoryRecording(keep = true) },
                            color = Color(0xFFFFF2F3),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE33D4E).copy(alpha = .25f)),
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE33D4E)))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(if (pendingStoryRecordingKind == "radio") "Chronique radio en cours" else "Note vocale en cours", color = Color(0xFF9E2432), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("${formatVoiceDuration(storyRecordingSeconds)} · touchez pour terminer", color = Color(0xFFB65A64), fontSize = 9.sp)
                                }
                                Icon(Icons.Rounded.Stop, "Terminer", tint = Color(0xFFE33D4E))
                            }
                        }
                    }
                    OutlinedTextField(draft, { draft = it.take(600) }, Modifier.fillMaxWidth(), placeholder = { Text("Ajoutez un texte, une légende ou un contexte…") }, minLines = 3, maxLines = 7, shape = RoundedCornerShape(17.dp))
                    Surface(Modifier.fillMaxWidth().wapiClickable(enabled = !busy) {
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
                            openOwnStoryAfterCount = confirmedMyStories.size
                            if (preview) previewStatuses = listOf(WhappyStatus("local-${System.currentTimeMillis()}", currentUserId, currentUserName, value, "personal", System.currentTimeMillis(), mediaUri?.toString().orEmpty(), if (mediaType.startsWith("audio/")) "audio" else if (mediaType.startsWith("video/")) "video" else if (mediaUri != null) "image" else "text", mediaName, authorPhotoUrl = currentUserPhotoUrl)) + previewStatuses
                            else onPublish(value, "personal", mediaUri, mediaType)
                            draft = ""; mediaUri = null; mediaType = ""; mediaName = ""; showComposer = false
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp) else Text("Publier ma Story", fontWeight = FontWeight.Bold) }
                }
        }
    }

    selectedStoryAuthorId?.let { authorId ->
        val selectedStories = storyGroups[authorId].orEmpty().filterNot { it.mediaName == "Publication en cours" }
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
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(18.dp)).wapiClickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).clip(CircleShape).background(WhappyAurora))
        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(title, color = WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.15).sp); Text(subtitle, color = WhappyMuted, fontSize = 11.sp) }
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
        color = WapiElevated,
        shape = RoundedCornerShape(WapiMobile.panelRadius),
        shadowElevation = 7.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .62f)),
    ) {
        Column(Modifier.padding(vertical = 5.dp)) {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).wapiClickable { onOpen(item.destination) }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(item.accent.copy(alpha = .18f), WapiSoftBlue))), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = item.accent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(item.subtitle, Modifier.padding(top = 2.dp), color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(item.accent.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = item.accent, modifier = Modifier.size(18.dp))
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
            title = { Text("Vues de la Story", fontWeight = FontWeight.Bold) },
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
        "UNAUTHENTICATED", "PERMISSION_DENIED" -> "Votre session WAPI doit être actualisée avant de continuer avec WIA."
        "FAILED_PRECONDITION" -> "La liaison sécurisée entre WIA et Pilotis doit être renouvelée. Votre conversation reste enregistrée."
        "RESOURCE_EXHAUSTED" -> "WIA traite beaucoup de demandes. Patientez quelques secondes puis renvoyez votre message."
        "DEADLINE_EXCEEDED", "UNAVAILABLE" -> "La connexion avec WIA a été interrompue. Votre message est conservé : renvoyez-le lorsque le réseau revient."
        "NOT_FOUND" -> "WIA se met à jour. Réessayez dans quelques instants."
        else -> "WIA n’a pas pu répondre pour le moment. Votre conversation reste conservée dans WAPI."
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
                "Bonjour ${userName.substringBefore(' ').ifBlank { "Cyril" }}. Je suis WIA, le même moteur conversationnel que dans Pilotis, intégré à WAPI. Votre mémoire est privée et aucune action n’est exécutée sans votre confirmation.",
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
                    Icon(Icons.Rounded.AutoAwesome, "WIA", tint = Color.White, modifier = Modifier.size(23.dp))
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("WIA", color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (activeSettings.enabled && !memoryUnavailable) Color(0xFF1FA971) else Color(0xFFE09A24)))
                        Text(
                            if (!activeSettings.enabled) "WIA en pause" else if (memoryUnavailable) "Mémoire locale active · synchronisation à reprendre" else "WIA Chat · mémoire privée synchronisée",
                            Modifier.padding(start = 6.dp),
                            color = WhappyMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
                IconButton(onClick = onOpenMessages) { Icon(Icons.Rounded.ChatBubble, "Ouvrir les messages", tint = WhappyMuted) }
                IconButton(onClick = onOpenBusiness) { Icon(Icons.Rounded.BusinessCenter, "Ouvrir Business", tint = WhappyMuted) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.MoreVert, "Régler WIA", tint = WhappyDark) }
            }
        }
        if (!activeSettings.enabled) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFFFFF7E7)) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("WIA est en pause.", Modifier.weight(1f), color = Color(0xFF8A5A00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text("Que voulez-vous faire ?", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                            Icon(Icons.Rounded.AutoAwesome, "Réponse WIA", tint = Color.White, modifier = Modifier.size(16.dp))
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
                                Text("Réessayer", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text("WIA prépare sa réponse…", Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 11.sp)
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
                    placeholder = { Text("Écrivez à WIA…") },
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
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer à WIA", tint = Color.White) }
            }
        }
    }
    if (showSettings) WapiAssistantSettingsDialog(activeSettings, busy, onDismiss = { showSettings = false }, onSave = { onSaveSettings(it); showSettings = false })
}

@Composable
private fun WapiAssistantSettingsDialog(settings: WapiWepiSettings, busy: Boolean, onDismiss: () -> Unit, onSave: (WapiWepiSettings) -> Unit) {
    var enabled by rememberSaveable(settings) { mutableStateOf(settings.enabled) }
    var autoReply by rememberSaveable(settings) { mutableStateOf(settings.autoReply) }
    var assistantName by rememberSaveable(settings) { mutableStateOf(settings.assistantName) }
    var businessName by rememberSaveable(settings) { mutableStateOf(settings.businessName) }
    var tone by rememberSaveable(settings) { mutableStateOf(settings.tone) }
    var welcome by rememberSaveable(settings) { mutableStateOf(settings.welcomeMessage) }
    var instructions by rememberSaveable(settings) { mutableStateOf(settings.instructions) }
    var salesAutomation by rememberSaveable(settings) { mutableStateOf(settings.salesAutomation) }
    var captureOrderRequests by rememberSaveable(settings) { mutableStateOf(settings.captureOrderRequests) }
    var humanHandoff by rememberSaveable(settings) { mutableStateOf(settings.humanHandoff) }
    var deliveryPolicy by rememberSaveable(settings) { mutableStateOf(settings.deliveryPolicy) }
    WapiEditorScreen(
        title = "Réglages de WIA", action = "Enregistrer", busy = busy,
        ready = assistantName.trim().length >= 2, onDismiss = onDismiss,
        onSubmit = { onSave(settings.copy(enabled = enabled, autoReply = autoReply, assistantName = assistantName, businessName = businessName, tone = tone, welcomeMessage = welcome, instructions = instructions, salesAutomation = salesAutomation, captureOrderRequests = captureOrderRequests, humanHandoff = humanHandoff, deliveryPolicy = deliveryPolicy)) },
    ) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Activer WIA", fontWeight = FontWeight.Bold); Text("WIA Chat disponible dans WAPI", color = WhappyMuted, fontSize = 13.sp) }; Switch(enabled, { enabled = it }, enabled = !busy) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Réponse automatique", fontWeight = FontWeight.Bold); Text("Toujours sous vos consignes", color = WhappyMuted, fontSize = 13.sp) }; Switch(autoReply, { autoReply = it }, enabled = enabled && !busy) } }
                item { Text("VENTE ASSISTÉE", color = WhappyBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Conseiller les produits", fontWeight = FontWeight.Bold); Text("WIA utilise uniquement le catalogue actif", color = WhappyMuted, fontSize = 13.sp) }; Switch(salesAutomation, { salesAutomation = it }, enabled = enabled && !busy) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Préparer les demandes", fontWeight = FontWeight.Bold); Text("Produit, quantité et livraison, sans simuler un paiement", color = WhappyMuted, fontSize = 13.sp) }; Switch(captureOrderRequests, { captureOrderRequests = it }, enabled = enabled && salesAutomation && !busy) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Transfert humain", fontWeight = FontWeight.Bold); Text("Proposé pour validation, doute ou litige", color = WhappyMuted, fontSize = 13.sp) }; Switch(humanHandoff, { humanHandoff = it }, enabled = enabled && !busy) } }
                item { OutlinedTextField(assistantName, { assistantName = it.take(60) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nom d’affichage de WIA") }, singleLine = true) }
                item { OutlinedTextField(businessName, { businessName = it.take(100) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nom du business") }, singleLine = true) }
                item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("chaleureux", "expert", "direct").forEach { option -> OutlinedButton(onClick = { tone = option }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (tone == option) WhappyBlue else Color.Transparent, contentColor = if (tone == option) Color.White else WhappyBlue)) { Text(option.replaceFirstChar { it.uppercase() }) } } } }
                item { OutlinedTextField(welcome, { welcome = it.take(240) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Message d’accueil") }, minLines = 2) }
                item { OutlinedTextField(instructions, { instructions = it.take(600) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Consignes de l’IA") }, minLines = 3) }
                item { OutlinedTextField(deliveryPolicy, { deliveryPolicy = it.take(400) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Livraison et retrait") }, minLines = 2) }
    }
}

@Composable
private fun MomentsScreen(
    twinReadiness: Int,
    lives: List<WhappyLive>,
    radioEpisodes: List<WapiRadioEpisode>,
    sponsoredCampaigns: List<WhappyCampaign>,
    onTab: (WhappyTab) -> Unit,
    onOpenWhappies: () -> Unit,
    onSponsoredCampaignClick: (WhappyCampaign) -> Unit,
    onSponsoredCampaignDismiss: (WhappyCampaign) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().background(WapiCanvas), contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = WapiMobile.screen, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Accueil", color = WhappyDark, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("WAPI", color = WhappyMuted, fontSize = 12.sp)
                }
                FilledIconButton(onClick = { onTab(WhappyTab.MESSAGES) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.Rounded.ChatBubble, "Messages", tint = Color.White) }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(top = 9.dp).wapiClickable { onTab(WhappyTab.MESSAGES) }, color = Color.White) {
                Row(Modifier.padding(horizontal = 17.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ChatBubble, "Messages", tint = WhappyBlue, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                        Text("Messages", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text("Toutes vos discussions", color = WhappyMuted, fontSize = 11.sp)
                    }
                    Text("›", color = WhappyMuted, fontSize = 24.sp)
                }
            }
        }
        item {
            HorizontalDivider(color = WhappyLine, modifier = Modifier.padding(start = 72.dp))
            Surface(Modifier.fillMaxWidth(), color = Color.White) {
                Row(Modifier.padding(WapiMobile.row), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("WIA", color = WhappyDark, fontWeight = FontWeight.Medium); Text("Assistant personnel", color = WhappyMuted, fontSize = 11.sp) }
                    TextButton(onClick = { onTab(WhappyTab.WEPI) }) { Text("Ouvrir") }
                }
            }
        }
        val activeLives = lives.filter { it.status == "live" }
        item {
            HorizontalDivider(color = WhappyLine, modifier = Modifier.padding(start = 72.dp))
            Surface(Modifier.fillMaxWidth().wapiClickable { onTab(WhappyTab.LIVE) }, color = Color.White) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = WhappyBlue, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("En direct", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(if (activeLives.isEmpty()) "Aucun direct" else "${activeLives.size} en cours · ${activeLives.sumOf { it.viewerCount }} spectateurs", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
                }
            }
        }
        if (activeLives.isNotEmpty()) {
            item { Text("En direct maintenant", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhappyDark, modifier = Modifier.padding(top = 4.dp)) }
            items(activeLives.take(3), key = { it.id }) { live ->
                Surface(Modifier.fillMaxWidth().wapiClickable { onTab(WhappyTab.LIVE) }, color = WhappyNavy, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE23B4A)), contentAlignment = Alignment.Center) { Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(live.title.ifBlank { "Direct WAPI" }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text("${live.hostName} · ${live.viewerCount} spectateurs", color = Color.White.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1) }
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().wapiClickable { onTab(WhappyTab.BUSINESS) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = WhappyBlue), elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("WAPI ADS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Créer une publicité régionale depuis Business", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("CRÉER ›", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        sponsoredCampaigns.firstOrNull { it.placement in setOf("inbox", "profile_story") }?.let { campaign ->
            item(key = "sponsored-${campaign.id}") {
                SponsoredCampaignCard(
                    campaign = campaign,
                    onDismiss = { onSponsoredCampaignDismiss(campaign) },
                ) {
                    onSponsoredCampaignClick(campaign)
                    onTab(if (campaign.placement == "market") WhappyTab.MARKET else WhappyTab.BUSINESS)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().wapiClickable(onClick = onOpenWhappies), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Mon Jumeau numérique", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Créer votre Jumeau numérique", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
                }
            }
        }
        item {
            val newestEpisode = radioEpisodes.maxByOrNull { it.createdAt }
            Surface(Modifier.fillMaxWidth().wapiClickable { onTab(WhappyTab.RADIO) }, color = Color(0xFFF0F8FF), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .14f))) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Radio & podcasts", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(newestEpisode?.let { "${it.stationName} · ${it.title}" } ?: "Vos émissions et podcasts dans WAPI", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1) }
                    Text("ÉCOUTER", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("Découvrir", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WhappyDark, modifier = Modifier.padding(horizontal = WapiMobile.screen, vertical = 14.dp)) }
        item {
            BoxWithConstraints(Modifier.padding(horizontal = WapiMobile.screen)) {
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
private fun SponsoredCampaignCard(
    campaign: WhappyCampaign,
    onDismiss: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 8.dp).wapiClickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WhappyAurora), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.BusinessCenter, null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                    Text(campaign.pageName, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("SPONSORISÉ · ${campaign.city.ifBlank { "DIFFUSION NATIONALE" }.uppercase()}", color = WhappyMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .55.sp)
                }
                Surface(color = WapiSoftBlue, shape = RoundedCornerShape(9.dp)) {
                    Text("${campaign.rankingEngine} · ANNONCE", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = WhappyBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Close, "Masquer cette publicité", tint = WhappyMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(campaign.title, color = WhappyDark, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
            Text(campaign.creative, color = WhappyMuted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (campaign.rankReasons.isNotEmpty()) {
                Surface(color = WapiSoftBlue.copy(alpha = .58f), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = WhappyBlue, modifier = Modifier.size(14.dp))
                        Text(
                            "Pourquoi cette publicité ? ${campaign.rankReasons.take(2).joinToString(" · ")}",
                            color = WhappyMuted,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(campaign.audience, color = WhappyMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text(campaign.cta.ifBlank { "Découvrir" }.uppercase() + "  ›", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StoryBubble(name: String, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).wapiClickable(onClick = onClick)) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(color).padding(3.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text(label, color = color, fontSize = if (label.length > 1) 14.sp else 20.sp, fontWeight = FontWeight.Bold)
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
                ) { Text(destination.second, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
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
                            Text("WAPI RADIO", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(if (broadcasting) if (paused) "Émission en pause" else "En direct maintenant" else "Studio prêt", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (broadcasting && !paused) WhappyBlue else Color.White).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(if (broadcasting && !paused) "LIVE" else "PRÊT", color = if (broadcasting && !paused) Color.White else WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        ) { Icon(Icons.Rounded.LiveTv, null, modifier = Modifier.size(17.dp)); Text("  Diffuser en direct", fontWeight = FontWeight.Bold) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("MIC PROCESSOR", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Text(micProfile.detail, color = Color.White, fontWeight = FontWeight.Bold) }; Text("AAC 192K", color = Color.White.copy(alpha = .65f), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { WapiMicProfile.entries.forEach { profile -> OutlinedButton(enabled = !broadcasting, onClick = { micProfile = profile; prefs.edit().putString("mic_profile", profile.name).apply(); WhappySounds.haptic(context) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (micProfile == profile) WhappyBlue else Color.White.copy(alpha = .05f), contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, if (micProfile == profile) WhappySky else Color.White.copy(alpha = .18f)), shape = RoundedCornerShape(14.dp)) { Text(profile.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
                    WapiAudioMeter(inputLevel = inputLevel, active = broadcasting && !paused)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(Triple("NR", "Bruit", effectSupport.noiseReduction), Triple("AGC", "Niveau", effectSupport.automaticGain), Triple("AEC", "Écho", effectSupport.echoCancellation)).forEach { effect -> Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .07f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (broadcasting && effect.third) Color(0xFF4ADE80) else WhappyMuted)); Column(Modifier.padding(start = 7.dp)) { Text(effect.first, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(if (effect.third) effect.second else "N/D", color = Color.White.copy(alpha = .55f), fontSize = 8.sp) } } } }
                    Text("Les traitements d’entrée dépendent du DSP audio disponible sur l’appareil. Le profil sélectionné est aussi appliqué à l’écoute de contrôle.", color = Color.White.copy(alpha = .52f), fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = WhappyBlue)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(if (broadcasting) "Votre studio est actif" else "Lancez votre émission", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                            ) { Icon(Icons.Rounded.Mic, null); Text("  Enregistrer l’émission", fontWeight = FontWeight.Bold) }
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
                            ) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Mic, null); Text(if (paused) "  Reprendre" else "  Pause", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = ::finishBroadcast,
                                Modifier.weight(1f).height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue),
                                shape = RoundedCornerShape(17.dp),
                            ) { Icon(Icons.Rounded.Stop, null); Text("  Terminer", fontWeight = FontWeight.Bold) }
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
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Dernière émission", color = WhappyDark, fontWeight = FontWeight.Bold); Text("${file.length() / 1024} Ko · ${formatRadioDuration(lastDuration)} · ${micProfile.label}", color = WhappyMuted, fontSize = 11.sp) }
                        IconButton(onClick = { if (monitoring) { localPlayback.stop(); monitoring = false } else { monitoring = true; localPlayback.play(file, micProfile) { monitoring = false } } }) { Icon(if (monitoring) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (monitoring) "Arrêter l’écoute" else "Écouter", tint = WhappyBlue) }
                        IconButton(onClick = { shareRadioRecording(context, file, stationName) }) { Icon(Icons.Rounded.Share, "Partager", tint = WhappyBlue) }
                    }
                    Button(
                        enabled = !busy && currentUserId.isNotBlank() && topic.trim().length >= 2,
                        onClick = { onPublishEpisode(stationName.trim(), topic.trim(), Uri.fromFile(file), lastDuration.coerceAtLeast(1L)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 0.dp).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) { Text(if (busy) "Publication cloud…" else "Publier le podcast sur WAPI", fontWeight = FontWeight.Bold) }
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
                            Text("WAPI PODCASTS", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Écoutez sans quitter WAPI", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("Reprenez un épisode et passez au suivant depuis cette bibliothèque.", color = Color.White.copy(alpha = .68f), fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Radios & podcasts", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Émissions réellement publiées sur WAPI", color = WhappyMuted, fontSize = 11.sp) }
                Text("${episodes.size} EN LIGNE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                        Text(episode.title, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${episode.stationName} · ${episode.authorName}", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${formatRadioDuration(episode.durationSeconds)} · publication cloud", color = WhappyMuted, fontSize = 9.sp)
                    }
                    if (episode.ownerId == currentUserId) Text("VOUS", color = WapiVerifiedGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (section == 0) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Programmation", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Préparez les prochaines émissions", color = WhappyMuted, fontSize = 11.sp) }
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
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(parts.getOrElse(1) { "Émission" }, color = WhappyDark, fontWeight = FontWeight.Bold); Text(parts.getOrElse(2) { "Heure à définir" }, color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        IconButton(onClick = { programmes = programmes.filterNot { it == raw }; prefs.edit().putStringSet("programmes", programmes.toSet()).apply() }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = WhappyBlue) }
                    }
                }
            }
        }
        }
    }

    if (showScheduleDialog) AlertDialog(
        onDismissRequest = { showScheduleDialog = false },
        title = { Text("Programmer une émission", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(scheduleTitle, { scheduleTitle = it.take(70) }, Modifier.fillMaxWidth(), label = { Text("Titre") }, singleLine = true); OutlinedTextField(scheduleTime, { scheduleTime = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Jour et heure") }, placeholder = { Text("Ex. samedi, 18:30") }, singleLine = true) } },
        confirmButton = { Button(enabled = scheduleTitle.trim().length >= 2 && scheduleTime.trim().length >= 2, onClick = { val entry = "${System.currentTimeMillis()}|${scheduleTitle.trim().replace("|", " ")}|${scheduleTime.trim().replace("|", " ")}"; programmes = (programmes + entry).sorted(); prefs.edit().putStringSet("programmes", programmes.toSet()).apply(); scheduleTitle = ""; scheduleTime = ""; showScheduleDialog = false; feedback = "L’émission a été ajoutée à votre programmation." }) { Text("Programmer") } },
        dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text("Annuler") } },
    )
}

@Composable
private fun RadioMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = WhappyMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, Modifier.padding(top = 4.dp), color = WhappyBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private fun formatRadioDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60L, seconds % 60L)

@Composable
private fun WapiAudioMeter(inputLevel: Float, active: Boolean) {
    val smoothed by animateFloatAsState(targetValue = if (active) inputLevel else 0f, animationSpec = tween(85), label = "radio-meter")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("NIVEAU MICRO", Modifier.weight(1f), color = Color.White.copy(alpha = .65f), fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(if (!active) "—∞ dB" else "${(-42 + smoothed * 42).toInt()} dB", color = if (smoothed > .86f) Color(0xFFFF806B) else WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
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
    "Wapi Pool" -> "pool"
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
    "Wapi Pool" -> Icons.Rounded.Radio
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
    var ludoMode by rememberSaveable { mutableStateOf("ai") }
    var ludoAiDifficulty by rememberSaveable { mutableStateOf("medium") }
    var ludoSessionStarted by rememberSaveable { mutableStateOf(false) }
    var poolMode by rememberSaveable { mutableStateOf("training") }
    var poolAiDifficulty by rememberSaveable { mutableStateOf("medium") }
    var poolSessionStarted by rememberSaveable { mutableStateOf(false) }
    var gameFilter by rememberSaveable { mutableStateOf("Tous") }
    var xp by rememberSaveable { mutableIntStateOf(gamePrefs.getInt("xp", 0)) }
    var wins by rememberSaveable { mutableIntStateOf(gamePrefs.getInt("wins", 0)) }
    var dieOne by rememberSaveable { mutableIntStateOf(0) }
    var dieTwo by rememberSaveable { mutableIntStateOf(0) }
    var activePlayer by rememberSaveable { mutableIntStateOf(0) }
    // Four real pawns per player. Progress -1 = house, 0..51 = shared
    // circuit, 52..57 = coloured arrival lane.
    var ludoPositions by rememberSaveable { mutableStateOf(List(16) { -1 }) }
    var pendingLudoRoll by rememberSaveable { mutableIntStateOf(0) }
    var pendingLudoExit by rememberSaveable { mutableStateOf(false) }
    var pendingLudoExtraTurn by rememberSaveable { mutableStateOf(false) }
    var movableLudoPawns by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var ludoLog by rememberSaveable { mutableStateOf("Lancez les deux dés. Un 6 sur un dé permet de sortir un pion ; un double fait rejouer.") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    var round by rememberSaveable { mutableIntStateOf(1) }
    var diceRolling by remember { mutableStateOf(false) }
    var ludoRollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var ludoWinner by rememberSaveable { mutableStateOf<Int?>(null) }
    var celebrating by rememberSaveable { mutableStateOf(false) }
    var showGameGuide by rememberSaveable { mutableStateOf(false) }
    val games = listOf(
        Triple("King QI", "Quiz vocal · contacts · trophées · direct", "♛"),
        Triple("Ludo WAPI", "Table 3D · deux dés, pions, captures et arrivée", "🎲"),
        Triple("WAPI Sky", "Course 3D · réflexes et progression", "🚀"),
        Triple("Wapi Pool", "Table 3D · visée, force et collisions", "🎱"),
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
        if (gameOpen && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else if (activity != null) {
            // A landscape request can recreate the Activity. Never reuse the
            // recreated Activity's "previous" value because it is landscape;
            // explicitly restore the portrait WAPI shell when the game closes.
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        onDispose {
            if (activity != null && gameOpen) {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }

    fun nextPlayer(from: Int = activePlayer): Int = (from + 1) % ludoPlayers.size

    fun resetLudo() {
        ludoRollJob?.cancel()
        diceRolling = false
        ludoWinner = null
        ludoPositions = List(16) { -1 }
        dieOne = 0
        dieTwo = 0
        activePlayer = 0
        pendingLudoRoll = 0
        pendingLudoExit = false
        pendingLudoExtraTurn = false
        movableLudoPawns = emptyList()
        ludoLog = "Nouvelle partie. Sortez sur 6, capturez les adversaires et atteignez l’arrivée."
    }

    fun playLudoTurn(
        roll: Int,
        requestedPawn: Int? = null,
        mayLeaveHome: Boolean = roll == 6,
        grantsExtraTurn: Boolean = roll == 6,
    ) {
        WhappySounds.haptic(context)
        val player = activePlayer
        val playerName = ludoPlayers[player].first
        val starts = listOf(0, 13, 26, 39)
        val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)
        val pawns = (player * 4 until player * 4 + 4).filter { index ->
            val progress = ludoPositions[index]
            (progress < 0 && mayLeaveHome) || (progress in 0..56 && progress + roll <= 57)
        }
        if (pawns.isEmpty()) {
            pendingLudoRoll = 0
            pendingLudoExit = false
            pendingLudoExtraTurn = false
            movableLudoPawns = emptyList()
            activePlayer = if (grantsExtraTurn) player else nextPlayer(player)
            ludoLog = "$playerName obtient $roll, mais aucun pion ne peut avancer." + if (grantsExtraTurn) " Double : relancez." else " Tour suivant : ${ludoPlayers[activePlayer].first}."
            return
        }
        val controlledByHuman = ludoMode != "ai" || player == 0
        if (controlledByHuman && requestedPawn == null) {
            pendingLudoRoll = roll
            pendingLudoExit = mayLeaveHome
            pendingLudoExtraTurn = grantsExtraTurn
            movableLudoPawns = pawns
            ludoLog = "Total $roll. Choisissez le pion à déplacer."
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
        val pawnIndex = requestedPawn?.takeIf { it in pawns } ?: when {
            ludoMode == "ai" && player != 0 && ludoAiDifficulty == "easy" -> pawns.random()
            ludoMode == "ai" && player != 0 && ludoAiDifficulty == "medium" -> pawns.maxByOrNull { ludoPositions[it] }
            else -> pawns.maxByOrNull { index ->
                val progress = ludoPositions[index]
                (if (progress >= 0 && progress + roll == 57) 10_000 else 0) +
                    (if (wouldCapture(index)) 5_000 else 0) +
                    (if (ludoAiDifficulty == "ultra" && progress in 0..51 && progress + roll in setOf(0, 8, 13, 21, 26, 34, 39, 47)) 1_500 else 0) +
                    progress
            }
        } ?: pawns.first()
        val current = ludoPositions[pawnIndex]
        val nextPositions = ludoPositions.toMutableList()
        var message: String
        var extraTurn = grantsExtraTurn
        var captured = false

        if (current == -1) {
            nextPositions[pawnIndex] = 0
            message = "$playerName sort le pion ${(pawnIndex % 4) + 1}."
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
        pendingLudoExit = false
        pendingLudoExtraTurn = false
        movableLudoPawns = emptyList()
        if (captured) WhappySounds.capture(context) else WhappySounds.move(context)
        if (player == 0 && grantsExtraTurn) xp += 10
        val playerWon = (player * 4 until player * 4 + 4).all { nextPositions[it] == 57 }
        if (playerWon) {
            ludoWinner = player
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
        if (diceRolling || pendingLudoRoll > 0 || ludoWinner != null) return
        diceRolling = true
        WhappySounds.dice(context)
        ludoRollJob = gameScope.launch {
            try {
            repeat(9) { frame ->
                dieOne = ((System.nanoTime() / (frame + 3L)) % 6L).toInt() + 1
                dieTwo = ((System.nanoTime() / (frame + 7L) + frame * 3L) % 6L).toInt() + 1
                delay(48L + frame * 5L)
            }
            val finalOne = ((System.nanoTime() / 37L) % 6L).toInt() + 1
            val finalTwo = ((System.nanoTime() / 53L + 11L) % 6L).toInt() + 1
            dieOne = finalOne
            dieTwo = finalTwo
            val roll = WapiGameRules.ludoDiceRoll(finalOne, finalTwo)
            playLudoTurn(
                roll = roll.total,
                mayLeaveHome = roll.mayLeaveHome,
                grantsExtraTurn = roll.grantsExtraTurn,
            )
            } finally { diceRolling = false }
        }
    }

    LaunchedEffect(gameOpen, selected) {
        if (!gameOpen || selected != "Ludo WAPI") {
            ludoRollJob?.cancel()
            diceRolling = false
        }
    }

    LaunchedEffect(gameOpen, selected, ludoSessionStarted, ludoMode, activePlayer, diceRolling, pendingLudoRoll) {
        if (
            gameOpen && selected == "Ludo WAPI" && ludoSessionStarted && ludoMode == "ai" &&
            activePlayer != 0 && !diceRolling && pendingLudoRoll == 0 && ludoWinner == null
        ) {
            ludoLog = "${ludoPlayers[activePlayer].first} réfléchit…"
            delay(520L)
            rollLudo()
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
                            Text("WAPI PLAY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text("ARÈNE DE JEU", color = Color.White.copy(alpha = .68f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                "À VOUS DE JOUER",
                                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text("Une vraie partie.\nUn vrai rythme.", Modifier.padding(top = 18.dp), color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
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
                    Text("Choisir un jeu", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                        label = { Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${visibleGames.size} modules", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFE7F5FF), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "CHOISISSEZ VOTRE PARTIE",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = WhappyBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        items(visibleGames, key = { it.first }) { game ->
            GameModeCard(game.first, game.second, categoryForGame(game.first), gameIcon(game.first), selected == game.first) {
                selected = game.first
                if (game.first == "Ludo WAPI") ludoSessionStarted = false
                if (game.first == "Wapi Pool") poolSessionStarted = false
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
                        subtitle = if (selected == "King QI") "ARÈNE VOCALE" else "PARTIE EN COURS",
                        xp = xp,
                        onClose = { gameOpen = false },
                        onGuide = { showGameGuide = true },
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (selected) {
                            "King QI" -> KingQiArena(currentUserId = currentUserId, accountName = accountName, onStartLive = { gameOpen = false; onOpenLive() })
                            "Ludo WAPI" -> {
                                if (!ludoSessionStarted) {
                                    WapiLudoModePicker(
                                        selectedMode = ludoMode,
                                        selectedDifficulty = ludoAiDifficulty,
                                        onModeSelected = { ludoMode = it },
                                        onDifficultySelected = { ludoAiDifficulty = it },
                                        onStart = {
                                            resetLudo()
                                            ludoSessionStarted = true
                                        },
                                    )
                                } else if (ludoMode == "friends" || ludoMode == "tournament") {
                                    Box(Modifier.fillMaxSize().background(Color(0xFF07111F)).padding(18.dp)) {
                                        Column(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Surface(color = Color(0xFF0B2B4A), shape = RoundedCornerShape(18.dp)) {
                                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(if (ludoMode == "tournament") "TOURNOIS WAPI" else "AMIS WAPI", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(if (ludoMode == "tournament") "Rejoignez une table de tournoi avec son code d’invitation." else "Créez une table privée ou rejoignez vos amis.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    TextButton(onClick = { ludoSessionStarted = false }) { Text("CHANGER", color = Color.White) }
                                                }
                                            }
                                            WapiOnlineLudoCard(userId = currentUserId, userName = accountName)
                                        }
                                    }
                                } else {
                                WapiLudoTabletop3D(
                                    positions = ludoPositions,
                                    activePlayer = activePlayer,
                                    dieOne = dieOne.coerceAtLeast(1),
                                    dieTwo = dieTwo.coerceAtLeast(1),
                                    rolling = diceRolling,
                                    selectablePawns = movableLudoPawns.toSet(),
                                    onPawnTapped = { pawnIndex ->
                                        if (pendingLudoRoll > 0 && pawnIndex in movableLudoPawns) {
                                            playLudoTurn(pendingLudoRoll, pawnIndex, pendingLudoExit, pendingLudoExtraTurn)
                                        }
                                    },
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
                                            Text("TOUR DE ${ludoPlayers[activePlayer].first.uppercase()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(ludoLog, color = Color.White.copy(alpha = .70f), fontSize = 9.sp, maxLines = 2)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            WapiRollingDie(value = dieOne.coerceAtLeast(1), rolling = diceRolling, dieSize = 28.dp)
                                            WapiRollingDie(value = dieTwo.coerceAtLeast(1), rolling = diceRolling, dieSize = 28.dp)
                                        }
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
                                            Text("TOUCHEZ UN PION", color = Color(0xFFFFD65A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            movableLudoPawns.forEach { pawnIndex ->
                                                OutlinedButton(
                                                    onClick = { playLudoTurn(pendingLudoRoll, pawnIndex, pendingLudoExit, pendingLudoExtraTurn) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                ) { Text("PION ${(pawnIndex % 4) + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                            }
                                        } else {
                                            Button(onClick = ::rollLudo, enabled = !diceRolling && activePlayer == 0 && ludoWinner == null, modifier = Modifier.width(190.dp).height(48.dp)) {
                                                Text(if (ludoWinner != null) "PARTIE TERMINÉE" else if (diceRolling || activePlayer != 0) "ADVERSAIRE…" else "LANCER LES 2 DÉS", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        OutlinedButton(onClick = ::resetLudo, enabled = !diceRolling, modifier = Modifier.height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("REJOUER") }
                                    }
                                }
                                }
                            }
                            "WAPI Sky" -> SkyRun3D(onXp = { xp += it }, onWin = ::celebrateWin)
                            "Wapi Pool" -> {
                                if (!poolSessionStarted) {
                                    WapiPoolModePicker(
                                        selectedMode = poolMode,
                                        selectedDifficulty = poolAiDifficulty,
                                        onModeSelected = { poolMode = it },
                                        onDifficultySelected = { poolAiDifficulty = it },
                                        onStart = { poolSessionStarted = true },
                                    )
                                } else if (poolMode == "wapi") {
                                    WapiOnlinePoolArena(userId = currentUserId, userName = accountName)
                                } else {
                                    Billiards3D(mode = poolMode, aiDifficulty = poolAiDifficulty, onXp = { xp += it }, onWin = ::celebrateWin)
                                }
                            }
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
                            Text("Guide de partie", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = WhappySky, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        }
        FilledIconButton(
            onClick = onGuide,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .09f), contentColor = WhappySky),
        ) { Icon(Icons.Rounded.Info, "Voir les règles") }
        Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(13.dp)) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4DE1A3)))
                Text("  XP $xp", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WapiRollingDie(value: Int, rolling: Boolean, dieSize: Dp = 42.dp) {
    val rotation by animateFloatAsState(if (rolling) 405f else 0f, tween(if (rolling) 440 else 180), label = "wapi-die")
    Box(
        Modifier.size(dieSize).graphicsLayer { rotationX = rotation; rotationY = rotation * .72f; shadowElevation = 14f; cameraDistance = 14f }
            .clip(RoundedCornerShape(dieSize * .23f)).background(Color.White).border(1.dp, WhappyLine, RoundedCornerShape(dieSize * .23f)),
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
                Text("VICTOIRE", color = Color(0xFFFFD54F), fontSize = 29.sp, fontWeight = FontWeight.Bold)
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
        "Wapi Pool" -> "Orientez la queue sur la table. Réglez le point rouge sur la bille blanche, tirez la jauge latérale vers le bas puis relâchez-la."
        "Jeu de dames" -> "Sélectionnez un pion puis une case diagonale. Les prises obligatoires sont signalées. Atteignez la dernière rangée pour couronner votre pion en dame."
        "Échecs" -> "Touchez une pièce puis sa destination. Les coups illégaux et la mise en échec sont refusés. Utilisez Rejouer pour recommencer la partie."
        "Poker WAPI" -> "Distribuez votre main, observez le flop, le turn et la river, puis choisissez suivre, relancer ou vous coucher. Les jetons sont virtuels."
        "Cartes WAPI" -> "Tirez une carte à chaque manche. La carte la plus forte gagne ; le premier à cinq manches remporte le duel."
        "Ludo WAPI" -> "Lancez les deux dés. Leur total déplace un pion ; un 6 sur un dé permet de sortir de la maison et un double donne une nouvelle lancée. Les cases sûres protègent vos pions."
        "WAPI Sky" -> "Démarrez la course puis touchez la moitié gauche ou droite pour changer de voie et éviter les obstacles."
        else -> "Lisez la consigne, choisissez une réponse puis passez à la manche suivante. Chaque action validée produit un retour sonore et visuel."
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FC)), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .16f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SmartToy, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) { Text("INSTRUCTEUR WAPI", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text("Apprendre · $gameName", color = WhappyDark, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(if (expanded) "Masquer" else "Apprendre", color = WhappyBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
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
        Modifier.fillMaxWidth().wapiClickable(onClick = onClick),
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
                    Text(title, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Surface(color = colors.last().copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                        Text(category.uppercase(), Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = colors.first(), fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
private fun WapiLudoModePicker(
    selectedMode: String,
    selectedDifficulty: String,
    onModeSelected: (String) -> Unit,
    onDifficultySelected: (String) -> Unit,
    onStart: () -> Unit,
) {
    val modes = listOf(
        Triple("ai", "CONTRE L’IA", "Choisissez ensuite Facile, Moyen, Difficile ou Ultra."),
        Triple("friends", "AMIS WAPI", "Créez une table privée et partagez son code."),
        Triple("tournament", "TOURNOIS", "Rejoignez la table créée par un organisateur WAPI."),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF124E7E), Color(0xFF071525), Color(0xFF02070E))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("LUDO WAPI", color = WhappySky, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Préparez la partie", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Vous lancez les deux dés et vous touchez vous-même le pion à déplacer. WAPI ne joue jamais à votre place.", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                modes.forEach { mode ->
                    val active = selectedMode == mode.first
                    Surface(
                        onClick = { onModeSelected(mode.first) },
                        modifier = Modifier.weight(1f).height(128.dp),
                        color = if (active) Color(0xFF0A8EEA) else Color.White.copy(alpha = .08f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color(0xFF71D5FF) else Color.White.copy(alpha = .14f)),
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(mode.second, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(mode.third, color = Color.White.copy(alpha = .72f), fontSize = 10.sp, lineHeight = 14.sp)
                            if (active) Text("SÉLECTIONNÉ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (selectedMode == "ai") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("easy" to "FACILE", "medium" to "MOYEN", "hard" to "DIFFICILE", "ultra" to "ULTRA").forEach { option ->
                        FilterChip(
                            selected = selectedDifficulty == option.first,
                            onClick = { onDifficultySelected(option.first) },
                            label = { Text(option.second, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = .06f),
                                labelColor = Color.White.copy(alpha = .72f),
                                selectedContainerColor = Color(0xFF0A8EEA),
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (selectedMode == "ai") "COMMENCER CONTRE L’IA" else "OUVRIR LE LOBBY", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WapiPoolModePicker(
    selectedMode: String,
    selectedDifficulty: String,
    onModeSelected: (String) -> Unit,
    onDifficultySelected: (String) -> Unit,
    onStart: () -> Unit,
) {
    var launching by remember { mutableStateOf(false) }
    LaunchedEffect(launching) {
        if (launching) {
            delay(650L)
            onStart()
        }
    }
    val modes = listOf(
        Triple("training", "ENTRAÎNEMENT", "Table libre, coups illimités et remise en place instantanée."),
        Triple("ai", "CONTRE L’IA", "Une vraie alternance des tours avec quatre niveaux."),
        Triple("wapi", "JOUEURS WAPI", "Créez ou rejoignez une table synchronisée en temps réel."),
    )
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF095E4C), Color(0xFF06231E), Color(0xFF010806)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF101923)).border(2.dp, Color(0xFF5BE3FF), CircleShape), contentAlignment = Alignment.Center) {
                    Text("8", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Column {
                    Text("WAPI POOL", color = Color(0xFF72F2C8), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Text("La table des compétiteurs", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
            Surface(color = Color.White.copy(alpha = .06f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🎱", fontSize = 30.sp)
                    Column(Modifier.weight(1f)) {
                        Text(if (launching) "Préparation de votre table" else "Moteur de table Wapi Pool", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Visée précise, puissance progressive, poches physiques et retour de billes compact.", color = Color.White.copy(alpha = .70f), fontSize = 11.sp)
                    }
                    if (launching) CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFF72F2C8), strokeWidth = 3.dp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                modes.forEach { mode ->
                    val active = selectedMode == mode.first
                    Surface(
                        onClick = { onModeSelected(mode.first) },
                        modifier = Modifier.weight(1f).height(136.dp),
                        color = if (active) Color(0xFF087D62) else Color.White.copy(alpha = .075f),
                        shape = RoundedCornerShape(21.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color(0xFF72F2C8) else Color.White.copy(alpha = .14f)),
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(if (mode.first == "training") Icons.Rounded.Bolt else if (mode.first == "ai") Icons.Rounded.SmartToy else Icons.Rounded.Groups, null, tint = if (active) Color(0xFF72F2C8) else Color.White.copy(alpha = .72f))
                            Text(mode.second, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(mode.third, color = Color.White.copy(alpha = .68f), fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
            if (selectedMode == "ai") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("easy" to "FACILE", "medium" to "MOYEN", "hard" to "DIFFICILE", "ultra" to "ULTRA").forEach { option ->
                        FilterChip(
                            selected = selectedDifficulty == option.first,
                            onClick = { onDifficultySelected(option.first) },
                            label = { Text(option.second, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = .06f),
                                labelColor = Color.White.copy(alpha = .72f),
                                selectedContainerColor = Color(0xFF087D62),
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }
            Button(onClick = { launching = true }, enabled = !launching, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A9C78))) {
                Text(if (launching) "CHARGEMENT…" else if (selectedMode == "wapi") "OUVRIR LE LOBBY WAPI" else "ENTRER SUR LA TABLE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WapiLudoTabletop3D(
    positions: List<Int>,
    activePlayer: Int,
    dieOne: Int,
    dieTwo: Int,
    rolling: Boolean,
    selectablePawns: Set<Int>,
    onPawnTapped: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1.05f),
) {
    AndroidView(
        factory = { context -> WapiTabletop3DView(context) },
        update = { view ->
            view.onLudoPawnTapped = onPawnTapped
            view.setLudoScene(positions, activePlayer, dieOne, dieTwo, rolling, selectablePawns)
        },
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxSize().background(Color(0xFF08111D)),
    )
}

@Composable
internal fun WapiPoolTabletop3D(
    balls: List<WapiPoolBall>,
    aimAngle: Float,
    power: Int,
    sideSpin: Float = 0f,
    followSpin: Float = 0f,
    moving: Boolean,
    onAim: (Float, Float) -> Unit,
    onRelease: () -> Unit,
    cueStroke: Float = 0f,
    cueInHand: Boolean = false,
    tableTheme: String = "competitionBlue",
    cueStyle: String = "maple",
    modifier: Modifier = Modifier,
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
                        ball.id.toFloat(),
                        ball.vx,
                        ball.vy,
                        if (ball.pocketed) 1f else 0f,
                    )
                },
                aimAngle,
                power,
                sideSpin,
                followSpin,
                moving,
                cueStroke,
                cueInHand,
                tableTheme,
                cueStyle,
            )
            view.onPoolGesture = { x, y, released -> if (!moving) { onAim(x, y); if (released) onRelease() } }
        },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Competition-style power rail. The player pulls the handle down and releases
 * it to strike; there is deliberately no decorative "FRAPPER" button.
 */
@Composable
internal fun WapiPoolPowerRail(
    power: Int,
    enabled: Boolean,
    onPowerChange: (Int) -> Unit,
    onStrike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pull by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val currentPower by rememberUpdatedState(power)
    val changePower by rememberUpdatedState(onPowerChange)
    val strike by rememberUpdatedState(onStrike)
    LaunchedEffect(enabled) { if (!enabled) { dragging = false; pull = 0f } }
    val displayedPull = if (dragging) pull else .10f
    Box(
        modifier
            .width(66.dp)
            .height(282.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xEA061B3B), Color(0xF0020A16))))
            .border(1.dp, Color(0xFF3D9CFF).copy(alpha = if (dragging) .72f else .30f), RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Puissance du tir"; stateDescription = if (enabled) "Prêt" else "Patientez" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var stroke = WapiPoolStroke(size.height.toFloat())
                var originalPower = currentPower
                detectVerticalDragGestures(
                    onDragStart = {
                        dragging = true
                        pull = .10f
                        originalPower = currentPower
                        stroke = WapiPoolStroke(size.height.toFloat())
                        WhappySounds.haptic(context)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        stroke.move(dragAmount, insideRail = change.position.x in -size.width * .5f..size.width * 1.5f)
                        pull = stroke.fraction
                        changePower(stroke.power)
                    },
                    onDragCancel = { dragging = false; pull = 0f; changePower(originalPower) },
                    onDragEnd = {
                        val committed = stroke.finish()
                        dragging = false
                        pull = 0f
                        if (committed != null) {
                            changePower(committed)
                            WhappySounds.haptic(context, strong = true)
                            strike()
                        } else changePower(originalPower)
                    },
                )
            },
    ) {
        ComposeCanvas(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 30.dp)) {
            val top = 8f
            val bottom = size.height - 8f
            val travel = (bottom - top) * .42f
            val tipY = top + displayedPull * travel
            val centreX = size.width / 2f
            drawLine(Color.Black.copy(alpha = .44f), Offset(centreX, top), Offset(centreX, bottom), 18f, StrokeCap.Round)
            val railColor = when {
                power >= 82 -> Color(0xFFFF5C6C)
                power >= 58 -> Color(0xFFFFB547)
                else -> Color(0xFF25A7FF)
            }
            drawLine(railColor.copy(alpha = .20f), Offset(centreX, top + 2f), Offset(centreX, bottom - 2f), 10f, StrokeCap.Round)
            drawLine(railColor.copy(alpha = .78f), Offset(centreX, top), Offset(centreX, top + (bottom - top) * power / 100f), 5f, StrokeCap.Round)
            repeat(5) { index ->
                val y = top + (bottom - top) * index / 4f
                drawLine(Color.White.copy(alpha = .15f), Offset(size.width * .15f, y), Offset(size.width * .85f, y), 1.2f)
            }
            val shaftEnd = (tipY + size.height * .48f).coerceAtMost(bottom - 24f)
            val buttEnd = (tipY + size.height * .76f).coerceAtMost(bottom)
            drawLine(Color.Black.copy(alpha = .40f), Offset(centreX + 2f, tipY + 4f), Offset(centreX + 2f, buttEnd), 12f, StrokeCap.Round)
            drawLine(if (enabled) Color(0xFFE8C891) else Color(0xFF8B8275), Offset(centreX, tipY + 6f), Offset(centreX, shaftEnd), 7f, StrokeCap.Round)
            drawLine(if (enabled) Color(0xFF6B250F) else Color(0xFF54413A), Offset(centreX, shaftEnd), Offset(centreX, buttEnd), 11f, StrokeCap.Round)
            drawLine(Color(0xFFD9B35D), Offset(centreX, shaftEnd - 3f), Offset(centreX, shaftEnd + 5f), 12f, StrokeCap.Butt)
            drawLine(Color(0xFF1F78A7), Offset(centreX, tipY), Offset(centreX, tipY + 7f), 9f, StrokeCap.Round)
            if (dragging) {
                drawCircle(Color.White.copy(alpha = .16f), size.width * .29f, Offset(centreX, buttEnd))
                drawCircle(Color.White.copy(alpha = .88f), size.width * .11f, Offset(centreX, buttEnd))
            }
        }
        Text("${power.coerceIn(0, 100)}%", Modifier.align(Alignment.TopCenter).padding(top = 8.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text("TIREZ", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), color = Color.White.copy(alpha = .72f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
    }
}

/** Two-axis contact point on the cue ball: left/right English and draw/follow. */
@Composable
internal fun WapiPoolSpinPad(
    sideSpin: Float,
    followSpin: Float,
    enabled: Boolean,
    onSpinChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun normalized(offset: Offset, width: Float, height: Float): Pair<Float, Float> {
        val x = ((offset.x / width) * 2f - 1f).coerceIn(-1f, 1f)
        val y = (1f - (offset.y / height) * 2f).coerceIn(-1f, 1f)
        val length = sqrt(x * x + y * y).coerceAtLeast(1f)
        return (x / length) to (y / length)
    }
    Box(
        modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color(0xD908151B))
            .border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> normalized(offset, size.width.toFloat(), size.height.toFloat()).let { onSpinChanged(it.first, it.second) } },
                    onDrag = { change, _ ->
                        change.consume()
                        normalized(change.position, size.width.toFloat(), size.height.toFloat()).let { onSpinChanged(it.first, it.second) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ComposeCanvas(Modifier.size(76.dp)) {
            val radius = size.minDimension * .46f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = .28f), radius + 2f, center + Offset(0f, 3f))
            drawCircle(if (enabled) Color(0xFFF8FAFC) else Color(0xFF9BA7AF), radius, center)
            drawLine(Color(0xFF89949C).copy(alpha = .34f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.2f)
            drawLine(Color(0xFF89949C).copy(alpha = .34f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.2f)
            val dot = Offset(center.x + sideSpin * radius * .72f, center.y - followSpin * radius * .72f)
            drawCircle(Color.Black.copy(alpha = .24f), 8.5f, dot + Offset(0f, 2f))
            drawCircle(Color(0xFFE73545), 7.5f, dot)
            drawCircle(Color.White.copy(alpha = .78f), 2.2f, dot - Offset(2f, 2f))
        }
    }
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
                    Text((index + 1).toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                Text(label, color = player.second, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                WhappySounds.gameInvalid(context)
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
                    Text("WAPI SKY ENGINE", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("Course WAPI", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(14.dp)) { Text("⚡ $energy  ·  $distance m", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).graphicsLayer { rotationX = 5f; cameraDistance = 24f; shadowElevation = 22f }
                    .clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF62D5FF), Color(0xFF087ECC), Color(0xFF04233E))))
                    .pointerInput(running, lane) { detectTapGestures { offset -> if (running) { lane = if (offset.x < size.width / 2f) (lane - 1).coerceAtLeast(0) else (lane + 1).coerceAtMost(2); WhappySounds.haptic(context) } } },
            ) {
                Text("WAPI CITY", Modifier.align(Alignment.TopCenter).padding(top = 18.dp), color = Color.White.copy(alpha = .74f), fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer { rotationX = 13f; rotationY = (index - 1) * -5f; cameraDistance = 20f }.clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color.White.copy(alpha = if (index == lane) .19f else .08f))) {
                            if (running && obstacleLane == index) Box(Modifier.align(Alignment.Center).size(50.dp).graphicsLayer { rotationX = 24f; rotationY = distance * 19f; shadowElevation = 20f }.clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFB629)), contentAlignment = Alignment.Center) { Text("◆", color = Color.White, fontSize = 22.sp) }
                            if (lane == index) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(58.dp).graphicsLayer { rotationX = -12f; rotationY = if (running) distance * 7f else 0f; shadowElevation = 28f }.clip(RoundedCornerShape(20.dp)).background(WhappyAurora), contentAlignment = Alignment.Center) { Text("W", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Text(if (running) "TOUCHEZ À GAUCHE OU À DROITE" else "PRÊT POUR LA MISSION", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(message, color = Color.White.copy(alpha = .86f), fontSize = 12.sp, lineHeight = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(enabled = running && lane > 0, onClick = { lane -= 1; WhappySounds.haptic(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp)) { Text("← GAUCHE") }
                Button(onClick = { if (running) running = false else { if (energy <= 0) { energy = 3; distance = 0 }; running = true; message = "Mission lancée · évitez les blocs."; WhappySounds.reward(context) } }, Modifier.weight(1.2f), shape = RoundedCornerShape(14.dp)) { Text(if (running) "PAUSE" else "DÉMARRER", fontWeight = FontWeight.Bold) }
                OutlinedButton(enabled = running && lane < 2, onClick = { lane += 1; WhappySounds.haptic(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp)) { Text("DROITE →") }
            }
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
    // Spin belongs to the ball state so local, AI and synchronized games use
    // exactly the same deterministic physics. Values are normalized -1...1.
    val sideSpin: Float = 0f,
    val followSpin: Float = 0f,
)

internal const val WAPI_POOL_WORLD_WIDTH = 10.2f
internal const val WAPI_POOL_WORLD_HEIGHT = 5.10f
// A 57.15 mm billiard ball is roughly 1/44 of a competition table length.
// Keep that ratio close enough for realism while retaining mobile legibility.
internal const val WAPI_POOL_BALL_RADIUS = .145f

internal fun wapiPoolDistance(a: WapiPoolBall, b: WapiPoolBall): Float {
    val dx = (b.x - a.x) * WAPI_POOL_WORLD_WIDTH
    val dy = (b.y - a.y) * WAPI_POOL_WORLD_HEIGHT
    return sqrt(dx * dx + dy * dy)
}

internal fun initialPoolBalls(): List<WapiPoolBall> = buildList {
    add(WapiPoolBall(0, .23f, .50f))
    var id = 1
    for (row in 0..4) for (column in 0..row) {
        // Equilateral rack in physical table units: no initial overlap and no
        // explosive separation on the first physics frame.
        add(WapiPoolBall(id++, .700f + row * .0295f, .50f + (column - row / 2f) * .0575f))
    }
}

internal data class WapiPoolFrame(
    val balls: List<WapiPoolBall>,
    val collisionEnergy: Float,
    val railEnergy: Float,
    val newlyPocketed: Int,
    val cueScratch: Boolean,
    val cueContactBallId: Int?,
    val newlyPocketedIds: Set<Int>,
)

internal data class WapiPoolAiPlan(
    val angle: Float,
    val power: Int,
    val targetBallId: Int,
    val pocketIndex: Int,
)

private val wapiPoolPockets = listOf(
    .055f to .07f, .5f to .07f, .945f to .07f,
    .055f to .93f, .5f to .93f, .945f to .93f,
)

internal fun isValidWapiCuePlacement(x: Float, y: Float, balls: List<WapiPoolBall>): Boolean {
    if (!x.isFinite() || !y.isFinite()) return false
    // Ball-in-hand is intentionally limited behind the head string, matching
    // the training/ranked rule used by WAPI pool.
    if (x < .085f || x > .445f || y < .115f || y > .885f) return false
    val candidate = WapiPoolBall(0, x, y)
    if (wapiPoolPockets.any { (px, py) ->
            val dx = (x - px) * WAPI_POOL_WORLD_WIDTH
            val dy = (y - py) * WAPI_POOL_WORLD_HEIGHT
            sqrt(dx * dx + dy * dy) < .40f
        }) return false
    return balls.none { it.id != 0 && !it.pocketed && wapiPoolDistance(candidate, it) < WAPI_POOL_BALL_RADIUS * 2.08f }
}

private fun wapiPoolPathIsClear(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    balls: List<WapiPoolBall>,
    ignoredIds: Set<Int>,
): Boolean {
    val ax = fromX * WAPI_POOL_WORLD_WIDTH
    val ay = fromY * WAPI_POOL_WORLD_HEIGHT
    val bx = toX * WAPI_POOL_WORLD_WIDTH
    val by = toY * WAPI_POOL_WORLD_HEIGHT
    val abx = bx - ax
    val aby = by - ay
    val lengthSquared = abx * abx + aby * aby
    if (lengthSquared < .0001f) return false
    return balls.none { ball ->
        if (ball.pocketed || ball.id in ignoredIds) false else {
            val px = ball.x * WAPI_POOL_WORLD_WIDTH
            val py = ball.y * WAPI_POOL_WORLD_HEIGHT
            val t = (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0f, 1f)
            val dx = px - (ax + abx * t)
            val dy = py - (ay + aby * t)
            t in .025f..975f && sqrt(dx * dx + dy * dy) < WAPI_POOL_BALL_RADIUS * 2.12f
        }
    }
}

/** Selects a playable target-to-pocket route, rather than merely aiming at
 * the nearest ball. The same planner is covered by unit tests and can later be
 * moved server-side for ranked play without changing the table physics. */
internal fun planWapiPoolAiShot(balls: List<WapiPoolBall>, allowedBallIds: Set<Int>? = null): WapiPoolAiPlan? {
    val cue = balls.firstOrNull { it.id == 0 && !it.pocketed } ?: return null
    data class Candidate(val plan: WapiPoolAiPlan, val score: Float)
    return balls.asSequence().filter { it.id != 0 && !it.pocketed && (allowedBallIds == null || it.id in allowedBallIds) }.flatMap { target ->
        wapiPoolPockets.asSequence().mapIndexedNotNull { pocketIndex, pocket ->
            val pocketDx = (pocket.first - target.x) * WAPI_POOL_WORLD_WIDTH
            val pocketDy = (pocket.second - target.y) * WAPI_POOL_WORLD_HEIGHT
            val targetToPocket = sqrt(pocketDx * pocketDx + pocketDy * pocketDy)
            if (targetToPocket < .01f) return@mapIndexedNotNull null
            val ux = pocketDx / targetToPocket
            val uy = pocketDy / targetToPocket
            val ghostX = target.x - ux * WAPI_POOL_BALL_RADIUS * 2f / WAPI_POOL_WORLD_WIDTH
            val ghostY = target.y - uy * WAPI_POOL_BALL_RADIUS * 2f / WAPI_POOL_WORLD_HEIGHT
            if (ghostX < .075f || ghostX > .925f || ghostY < .105f || ghostY > .895f) return@mapIndexedNotNull null
            if (!wapiPoolPathIsClear(cue.x, cue.y, ghostX, ghostY, balls, setOf(0, target.id))) return@mapIndexedNotNull null
            if (!wapiPoolPathIsClear(target.x, target.y, pocket.first, pocket.second, balls, setOf(target.id))) return@mapIndexedNotNull null
            val cueDx = (ghostX - cue.x) * WAPI_POOL_WORLD_WIDTH
            val cueDy = (ghostY - cue.y) * WAPI_POOL_WORLD_HEIGHT
            val cueDistance = sqrt(cueDx * cueDx + cueDy * cueDy)
            val alignment = kotlin.math.abs((cueDx * ux + cueDy * uy) / cueDistance.coerceAtLeast(.01f))
            val power = (32f + cueDistance * 5.3f + targetToPocket * 4.2f).toInt().coerceIn(34, 92)
            Candidate(
                WapiPoolAiPlan(atan2(ghostY - cue.y, ghostX - cue.x), power, target.id, pocketIndex),
                cueDistance + targetToPocket * .78f + (1f - alignment) * 2.4f,
            )
        }
    }.minByOrNull(Candidate::score)?.plan
}

internal enum class WapiPoolGroup { OPEN, SOLIDS, STRIPES }

internal fun wapiPoolGroupForBall(ballId: Int): WapiPoolGroup = when (ballId) {
    in 1..7 -> WapiPoolGroup.SOLIDS
    in 9..15 -> WapiPoolGroup.STRIPES
    else -> WapiPoolGroup.OPEN
}

internal fun wapiPoolTargetIds(group: WapiPoolGroup, balls: List<WapiPoolBall>): Set<Int> {
    val ownRemaining = balls.filter { !it.pocketed && wapiPoolGroupForBall(it.id) == group }.map(WapiPoolBall::id).toSet()
    return when {
        group == WapiPoolGroup.OPEN -> balls.filter { !it.pocketed && it.id in 1..15 && it.id != 8 }.map(WapiPoolBall::id).toSet()
        ownRemaining.isEmpty() -> setOf(8)
        else -> ownRemaining
    }
}

internal data class WapiPoolShotResolution(
    val shooterGroup: WapiPoolGroup,
    val opponentGroup: WapiPoolGroup,
    val foul: Boolean,
    val keepTurn: Boolean,
    /** true: shooter wins, false: shooter loses, null: match continues. */
    val shooterWon: Boolean?,
    val message: String,
)

/** WPA-inspired eight-ball resolution shared by AI and online rooms. */
internal fun resolveWapiPoolShot(
    ballsBefore: List<WapiPoolBall>,
    shooterGroupBefore: WapiPoolGroup,
    opponentGroupBefore: WapiPoolGroup,
    firstContactBallId: Int?,
    scratched: Boolean,
    pocketedBallIds: Set<Int>,
): WapiPoolShotResolution {
    val legalTargets = wapiPoolTargetIds(shooterGroupBefore, ballsBefore)
    val wrongFirstContact = firstContactBallId == null || firstContactBallId !in legalTargets
    val foul = scratched || wrongFirstContact
    var shooterGroup = shooterGroupBefore
    var opponentGroup = opponentGroupBefore
    if (!foul && shooterGroupBefore == WapiPoolGroup.OPEN) {
        pocketedBallIds.firstOrNull { it in 1..7 || it in 9..15 }?.let { firstAssigned ->
            shooterGroup = wapiPoolGroupForBall(firstAssigned)
            opponentGroup = if (shooterGroup == WapiPoolGroup.SOLIDS) WapiPoolGroup.STRIPES else WapiPoolGroup.SOLIDS
        }
    }
    if (8 in pocketedBallIds) {
        val clearedBeforeShot = shooterGroupBefore != WapiPoolGroup.OPEN &&
            ballsBefore.none { !it.pocketed && wapiPoolGroupForBall(it.id) == shooterGroupBefore }
        val won = clearedBeforeShot && !foul && firstContactBallId == 8
        return WapiPoolShotResolution(
            shooterGroup, opponentGroup, foul, keepTurn = false, shooterWon = won,
            message = if (won) "Noire empochée légalement · victoire." else "Noire empochée trop tôt ou avec faute · défaite.",
        )
    }
    val pocketedOwnBall = pocketedBallIds.any { id ->
        id != 8 && (shooterGroup == WapiPoolGroup.OPEN || wapiPoolGroupForBall(id) == shooterGroup)
    }
    val message = when {
        scratched -> "Faute : blanche empochée · l’adversaire a bille en main."
        firstContactBallId == null -> "Faute : aucune bille touchée."
        wrongFirstContact -> "Faute : mauvaise bille touchée en premier."
        shooterGroupBefore == WapiPoolGroup.OPEN && shooterGroup != WapiPoolGroup.OPEN -> if (shooterGroup == WapiPoolGroup.SOLIDS) "Groupe attribué : billes pleines." else "Groupe attribué : billes rayées."
        pocketedOwnBall -> "Bille correcte empochée · le joueur continue."
        else -> "Tir terminé · changement de joueur."
    }
    return WapiPoolShotResolution(
        shooterGroup = shooterGroup,
        opponentGroup = opponentGroup,
        foul = foul,
        keepTurn = !foul && pocketedOwnBall,
        shooterWon = null,
        message = message,
    )
}

/** A single physics integration step. Public callers use [advanceWapiPoolFrame],
 * which divides a display frame into smaller deterministic steps so a powerful
 * break cannot tunnel through another ball or jump across a pocket mouth. */
private fun advanceWapiPoolSubstep(before: List<WapiPoolBall>, deltaSeconds: Float): WapiPoolFrame {
    val next = before.map { ball ->
        if (ball.pocketed) ball else {
            val worldVx = ball.vx * WAPI_POOL_WORLD_WIDTH
            val worldVy = ball.vy * WAPI_POOL_WORLD_HEIGHT
            val speed = sqrt(worldVx * worldVx + worldVy * worldVy)
            val curve = if (ball.id == 0) ball.sideSpin * (speed / 8f).coerceIn(0f, 1f) * .0026f else 0f
            val curvedVx = worldVx * cos(curve) - worldVy * sin(curve)
            val curvedVy = worldVx * sin(curve) + worldVy * cos(curve)
            // Cloth resistance is almost constant on a real slate table. A
            // fixed percentage per frame made slow balls glide forever and
            // changed with refresh rate. This deceleration is time-based.
            val nextSpeed = (speed - .72f * deltaSeconds).coerceAtLeast(0f)
            val rollingFactor = if (speed > .0001f) nextSpeed / speed else 0f
            val spinFactor = (1f - .74f * deltaSeconds).coerceIn(0f, 1f)
            ball.copy(
                x = ball.x + curvedVx / WAPI_POOL_WORLD_WIDTH * deltaSeconds,
                y = ball.y + curvedVy / WAPI_POOL_WORLD_HEIGHT * deltaSeconds,
                vx = curvedVx / WAPI_POOL_WORLD_WIDTH * rollingFactor,
                vy = curvedVy / WAPI_POOL_WORLD_HEIGHT * rollingFactor,
                sideSpin = ball.sideSpin * spinFactor,
                followSpin = ball.followSpin * spinFactor,
            )
        }
    }.toMutableList()
    var collisionEnergy = 0f
    var railEnergy = 0f
    var cueScratch = false
    var cueContactBallId: Int? = null
    next.indices.forEach { index ->
        val ball = next[index]
        if (ball.pocketed) return@forEach
        val nearestPocket = wapiPoolPockets.minByOrNull { (px, py) ->
            val dx = (ball.x - px) * WAPI_POOL_WORLD_WIDTH
            val dy = (ball.y - py) * WAPI_POOL_WORLD_HEIGHT
            dx * dx + dy * dy
        }
        val pocketDx = nearestPocket?.let { (it.first - ball.x) * WAPI_POOL_WORLD_WIDTH } ?: 0f
        val pocketDy = nearestPocket?.let { (it.second - ball.y) * WAPI_POOL_WORLD_HEIGHT } ?: 0f
        val pocketDistance = sqrt(pocketDx * pocketDx + pocketDy * pocketDy)
        val pocketThreshold = if (nearestPocket != null && kotlin.math.abs(nearestPocket.first - .5f) < .05f) .34f else .325f
        val hitPocket = nearestPocket != null && pocketDistance < pocketThreshold
        if (hitPocket) {
            if (ball.id == 0) {
                cueScratch = true
                next[index] = ball.copy(x = .23f, y = .50f, vx = 0f, vy = 0f, sideSpin = 0f, followSpin = 0f)
            } else next[index] = ball.copy(vx = 0f, vy = 0f, pocketed = true, sideSpin = 0f, followSpin = 0f)
            return@forEach
        }
        val minX = .064f; val maxX = .936f; val minY = .093f; val maxY = .907f
        var x = ball.x; var y = ball.y; var vx = ball.vx; var vy = ball.vy
        val inPocketJaw = pocketDistance < .46f
        if (inPocketJaw && pocketDistance > .001f) {
            // Gravity-like shelf pull makes a slow ball rattle towards the
            // leather throat instead of disappearing at a hard radius.
            val attraction = ((.46f - pocketDistance) / .135f).coerceIn(0f, 1f)
            vx += pocketDx / pocketDistance * attraction * 1.28f * deltaSeconds / WAPI_POOL_WORLD_WIDTH
            vy += pocketDy / pocketDistance * attraction * 1.28f * deltaSeconds / WAPI_POOL_WORLD_HEIGHT
        } else {
            if (x < minX) { x = minX; railEnergy = max(railEnergy, kotlin.math.abs(vx)); vy += ball.sideSpin * kotlin.math.abs(vx) * .035f; vx = kotlin.math.abs(vx) * .89f }
            if (x > maxX) { x = maxX; railEnergy = max(railEnergy, kotlin.math.abs(vx)); vy -= ball.sideSpin * kotlin.math.abs(vx) * .035f; vx = -kotlin.math.abs(vx) * .89f }
            if (y < minY) { y = minY; railEnergy = max(railEnergy, kotlin.math.abs(vy)); vx -= ball.sideSpin * kotlin.math.abs(vy) * .035f; vy = kotlin.math.abs(vy) * .89f }
            if (y > maxY) { y = maxY; railEnergy = max(railEnergy, kotlin.math.abs(vy)); vx += ball.sideSpin * kotlin.math.abs(vy) * .035f; vy = -kotlin.math.abs(vy) * .89f }
        }
        next[index] = ball.copy(x = x, y = y, vx = vx, vy = vy)
    }
    for (first in next.indices) for (second in first + 1 until next.size) {
        val a = next[first]; val b = next[second]
        if (a.pocketed || b.pocketed) continue
        val dx = (b.x - a.x) * WAPI_POOL_WORLD_WIDTH
        val dy = (b.y - a.y) * WAPI_POOL_WORLD_HEIGHT
        val distance = wapiPoolDistance(a, b)
        if (distance > 0f && distance < WAPI_POOL_BALL_RADIUS * 2f) {
            val nx = dx / distance; val ny = dy / distance
            val relative = (b.vx - a.vx) * WAPI_POOL_WORLD_WIDTH * nx +
                (b.vy - a.vy) * WAPI_POOL_WORLD_HEIGHT * ny
            val correction = ((WAPI_POOL_BALL_RADIUS * 2f - distance) * .5f + .001f)
            val separatedA = a.copy(x = a.x - nx * correction / WAPI_POOL_WORLD_WIDTH, y = a.y - ny * correction / WAPI_POOL_WORLD_HEIGHT)
            val separatedB = b.copy(x = b.x + nx * correction / WAPI_POOL_WORLD_WIDTH, y = b.y + ny * correction / WAPI_POOL_WORLD_HEIGHT)
            if (relative < 0f) {
                val impulse = -relative * .97f
                collisionEnergy = max(collisionEnergy, impulse)
                var nextA = separatedA.copy(vx = a.vx - impulse * nx / WAPI_POOL_WORLD_WIDTH, vy = a.vy - impulse * ny / WAPI_POOL_WORLD_HEIGHT)
                var nextB = separatedB.copy(vx = b.vx + impulse * nx / WAPI_POOL_WORLD_WIDTH, vy = b.vy + impulse * ny / WAPI_POOL_WORLD_HEIGHT)
                if (a.id == 0 || b.id == 0) {
                    cueContactBallId = if (a.id == 0) b.id else a.id
                    val cue = if (a.id == 0) a else b
                    val hitX = if (a.id == 0) nx else -nx
                    val hitY = if (a.id == 0) ny else -ny
                    val followX = cue.followSpin * impulse * .58f * hitX
                    val followY = cue.followSpin * impulse * .58f * hitY
                    val englishX = -cue.sideSpin * impulse * .13f * hitY
                    val englishY = cue.sideSpin * impulse * .13f * hitX
                    if (a.id == 0) nextA = nextA.copy(
                        vx = nextA.vx + (followX + englishX) / WAPI_POOL_WORLD_WIDTH,
                        vy = nextA.vy + (followY + englishY) / WAPI_POOL_WORLD_HEIGHT,
                        sideSpin = cue.sideSpin * .52f,
                        followSpin = cue.followSpin * .34f,
                    ) else nextB = nextB.copy(
                        vx = nextB.vx + (followX + englishX) / WAPI_POOL_WORLD_WIDTH,
                        vy = nextB.vy + (followY + englishY) / WAPI_POOL_WORLD_HEIGHT,
                        sideSpin = cue.sideSpin * .52f,
                        followSpin = cue.followSpin * .34f,
                    )
                }
                next[first] = nextA
                next[second] = nextB
            } else {
                next[first] = separatedA
                next[second] = separatedB
            }
        }
    }
    return WapiPoolFrame(
        balls = next,
        collisionEnergy = collisionEnergy,
        railEnergy = railEnergy,
        newlyPocketed = next.count { it.pocketed } - before.count { it.pocketed },
        cueScratch = cueScratch,
        cueContactBallId = cueContactBallId,
        newlyPocketedIds = next.filter { current ->
            current.pocketed && before.firstOrNull { previous -> previous.id == current.id }?.pocketed != true
        }.map(WapiPoolBall::id).toSet(),
    )
}

/** One deterministic billiards frame, shared by training, AI and WAPI rooms.
 *
 * Physics runs at up to 166 Hz internally while UI rendering remains at the
 * device refresh rate. This is the important difference between a moving 3D
 * mock-up and a stable game: fast breaks, thin cuts and pocket jaws produce the
 * same result on a 60, 90 or 120 Hz phone. */
internal fun advanceWapiPoolFrame(before: List<WapiPoolBall>, deltaSeconds: Float = .018f): WapiPoolFrame {
    val safeDelta = deltaSeconds.coerceIn(0f, .05f)
    val substeps = ceil(safeDelta / .006f).toInt().coerceIn(1, 9)
    val stepDelta = if (substeps > 0) safeDelta / substeps else 0f
    var balls = before
    var collisionEnergy = 0f
    var railEnergy = 0f
    var cueScratch = false
    var cueContactBallId: Int? = null
    val pocketedIds = linkedSetOf<Int>()
    repeat(substeps) {
        val frame = advanceWapiPoolSubstep(balls, stepDelta)
        balls = frame.balls
        collisionEnergy = max(collisionEnergy, frame.collisionEnergy)
        railEnergy = max(railEnergy, frame.railEnergy)
        cueScratch = cueScratch || frame.cueScratch
        if (cueContactBallId == null) cueContactBallId = frame.cueContactBallId
        pocketedIds += frame.newlyPocketedIds
    }
    return WapiPoolFrame(
        balls = balls,
        collisionEnergy = collisionEnergy,
        railEnergy = railEnergy,
        newlyPocketed = pocketedIds.size,
        cueScratch = cueScratch,
        cueContactBallId = cueContactBallId,
        newlyPocketedIds = pocketedIds,
    )
}

internal fun wapiPoolBallsMoving(balls: List<WapiPoolBall>): Boolean = balls.any { ball ->
    !ball.pocketed && sqrt(
        ball.vx * ball.vx * WAPI_POOL_WORLD_WIDTH * WAPI_POOL_WORLD_WIDTH +
            ball.vy * ball.vy * WAPI_POOL_WORLD_HEIGHT * WAPI_POOL_WORLD_HEIGHT,
    ) > .09f
}

/** Native top-down pool physics: aim by dragging the cue, then release to strike. */
@Composable
internal fun Billiards3D(mode: String, aiDifficulty: String, onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    val profile = rememberPoolProfile()
    var editingPlayer by remember { mutableStateOf(false) }
    val strokeScope = rememberCoroutineScope()
    var strokeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var cueStriking by remember { mutableStateOf(false) }
    var cueStroke by remember { mutableFloatStateOf(0f) }
    var poolBalls by remember { mutableStateOf(initialPoolBalls()) }
    var aimAngle by remember { mutableFloatStateOf(0f) }
    var power by remember { mutableIntStateOf(55) }
    var sideSpin by remember { mutableFloatStateOf(0f) }
    var followSpin by remember { mutableFloatStateOf(0f) }
    var shots by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var physicsRunning by remember { mutableStateOf(false) }
    var playerTurn by remember(mode) { mutableStateOf(true) }
    var pocketedAtShotStart by remember { mutableIntStateOf(0) }
    var ballsAtShotStart by remember { mutableStateOf(initialPoolBalls()) }
    var pocketedIdsThisShot by remember { mutableStateOf(emptySet<Int>()) }
    var scratchThisShot by remember { mutableStateOf(false) }
    var firstContactThisShot by remember { mutableStateOf<Int?>(null) }
    var cueBallInHand by remember { mutableStateOf(false) }
    var lastCollisionSoundAt by remember { mutableLongStateOf(0L) }
    var lastRailSoundAt by remember { mutableLongStateOf(0L) }
    var playerGroup by remember(mode) { mutableStateOf(WapiPoolGroup.OPEN) }
    var aiGroup by remember(mode) { mutableStateOf(WapiPoolGroup.OPEN) }
    var matchWinner by remember(mode) { mutableStateOf<String?>(null) }
    val equipmentPrefs = remember(context) { context.getSharedPreferences("wapi_pool_equipment", android.content.Context.MODE_PRIVATE) }
    var tableTheme by rememberSaveable { mutableStateOf(equipmentPrefs.getString("tableTheme", "competitionBlue") ?: "competitionBlue") }
    var cueStyle by rememberSaveable { mutableStateOf(equipmentPrefs.getString("cueStyle", "maple") ?: "maple") }
    var editingEquipment by remember { mutableStateOf(false) }
    var groupAnnouncement by remember { mutableStateOf<String?>(null) }
    var cupActive by rememberSaveable { mutableStateOf(false) }
    var cupWins by rememberSaveable { mutableIntStateOf(0) }
    var cupJustCompleted by rememberSaveable { mutableStateOf(false) }
    var turnSeconds by remember(mode) { mutableIntStateOf(30) }
    var message by remember { mutableStateOf("Orientez la queue sur la table, réglez le point d’impact puis tirez la jauge à gauche.") }
    val remainingBalls = poolBalls.count { it.id != 0 && !it.pocketed }

    fun resetTable() {
        strokeJob?.cancel(); cueStriking = false; cueStroke = 0f
        poolBalls = initialPoolBalls()
        physicsRunning = false
        shots = 0
        score = 0
        playerTurn = true
        playerGroup = WapiPoolGroup.OPEN
        aiGroup = WapiPoolGroup.OPEN
        matchWinner = null
        sideSpin = 0f
        followSpin = 0f
        cueBallInHand = false
        turnSeconds = 30
        cupJustCompleted = false
        message = "Nouvelle table · visez, puis tirez et relâchez la jauge à gauche."
    }

    fun strike(aiShot: Boolean = false) {
        if (physicsRunning || cueStriking || remainingBalls == 0 || cueBallInHand || matchWinner != null || (mode == "ai" && aiShot == playerTurn)) return
        cueStriking = true
        strokeJob = strokeScope.launch {
        try {
        Animatable(0f).animateTo(1f, tween(120)) { cueStroke = value }
        val speed = 3.0f + power / 100f * 8.6f
        pocketedAtShotStart = poolBalls.count { it.pocketed }
        ballsAtShotStart = poolBalls
        pocketedIdsThisShot = emptySet()
        scratchThisShot = false
        firstContactThisShot = null
        poolBalls = poolBalls.map { ball ->
            if (ball.id == 0) ball.copy(
                vx = cos(aimAngle) * speed / WAPI_POOL_WORLD_WIDTH,
                vy = sin(aimAngle) * speed / WAPI_POOL_WORLD_HEIGHT,
                sideSpin = sideSpin.coerceIn(-1f, 1f),
                followSpin = followSpin.coerceIn(-1f, 1f),
            ) else ball
        }
        shots += 1
        physicsRunning = true
        message = if (aiShot) "L’IA exécute son tir · puissance $power %" else "Tir en cours · puissance $power %"
        WhappySounds.billiardCue(context, power)
        WhappySounds.haptic(context)
        } finally { cueStriking = false; cueStroke = 0f }
        }
    }

    LaunchedEffect(physicsRunning) {
        if (!physicsRunning) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (physicsRunning) {
            val frameTime = withFrameNanos { it }
            val elapsed = ((frameTime - previousFrame) / 1_000_000_000f).coerceIn(0f, .05f)
            previousFrame = frameTime
            val before = poolBalls
            val frame = advanceWapiPoolFrame(before, elapsed)
            val next = frame.balls
            if (frame.cueScratch) scratchThisShot = true
            if (frame.newlyPocketedIds.isNotEmpty()) pocketedIdsThisShot = pocketedIdsThisShot + frame.newlyPocketedIds
            if (firstContactThisShot == null && frame.cueContactBallId != null) firstContactThisShot = frame.cueContactBallId
            val now = android.os.SystemClock.elapsedRealtime()
            if (frame.collisionEnergy > .28f && now - lastCollisionSoundAt > 72L) {
                lastCollisionSoundAt = now
                WhappySounds.billiardCollision(context, (.20f + frame.collisionEnergy * .07f).coerceAtMost(.66f))
            }
            if (frame.railEnergy > .025f && now - lastRailSoundAt > 95L) {
                lastRailSoundAt = now
                WhappySounds.billiardRail(context, (.18f + frame.railEnergy * 1.8f).coerceAtMost(.55f))
            }
            poolBalls = next
            val newlyPocketed = frame.newlyPocketed
            if (newlyPocketed > 0) {
                if (mode != "ai" || playerTurn) {
                    score += newlyPocketed * 100
                    onXp(newlyPocketed * 15)
                    profile.recordPractice(score)
                }
                WhappySounds.billiardPocket(context)
                val names = frame.newlyPocketedIds.sorted().joinToString(" · ") { "Bille $it empochée" }
                message = "$names · +${newlyPocketed * 100} points"
            }
            val moving = wapiPoolBallsMoving(next)
            if (!moving) {
                poolBalls = next.map { ball -> if (ball.pocketed) ball else ball.copy(vx = 0f, vy = 0f) }
                physicsRunning = false
                val madeBall = next.count { it.pocketed } > pocketedAtShotStart
                val missedEverything = firstContactThisShot == null
                val foul = scratchThisShot || missedEverything
                if (mode == "ai") {
                    val shooterWasPlayer = playerTurn
                    val resolution = resolveWapiPoolShot(
                        ballsBefore = ballsAtShotStart,
                        shooterGroupBefore = if (shooterWasPlayer) playerGroup else aiGroup,
                        opponentGroupBefore = if (shooterWasPlayer) aiGroup else playerGroup,
                        firstContactBallId = firstContactThisShot,
                        scratched = scratchThisShot,
                        pocketedBallIds = pocketedIdsThisShot,
                    )
                    if (shooterWasPlayer) {
                        val oldGroup = playerGroup
                        playerGroup = resolution.shooterGroup
                        aiGroup = resolution.opponentGroup
                        if (oldGroup == WapiPoolGroup.OPEN && resolution.shooterGroup != WapiPoolGroup.OPEN) {
                            groupAnnouncement = "Vous avez les ${if (resolution.shooterGroup == WapiPoolGroup.SOLIDS) "pleines" else "rayées"} !"
                        }
                    } else {
                        aiGroup = resolution.shooterGroup
                        playerGroup = resolution.opponentGroup
                    }
                    if (resolution.shooterWon != null) {
                        val playerWon = if (shooterWasPlayer) resolution.shooterWon else !resolution.shooterWon
                        matchWinner = if (playerWon) "VICTOIRE" else "DÉFAITE"
                        message = resolution.message
                        profile.recordPracticeResult(score, playerWon)
                        if (playerWon) {
                            onWin(); onXp(250); WhappySounds.reward(context)
                            if (cupActive) {
                                cupWins += 1
                                if (cupWins >= 3) { profile.awardLocalCup(); cupActive = false; cupWins = 0; cupJustCompleted = true }
                            }
                        } else if (cupActive) cupWins = 0
                    } else {
                        if (!resolution.keepTurn) playerTurn = !playerTurn
                        cueBallInHand = resolution.foul && playerTurn
                        message = resolution.message + if (cueBallInHand) " Placez la blanche." else ""
                    }
                } else if (foul) {
                    cueBallInHand = true
                    message = if (scratchThisShot) "Faute · replacez la blanche derrière la ligne puis relâchez." else "Aucune bille touchée · replacez la blanche."
                }
                if (mode != "ai" && next.none { it.id != 0 && !it.pocketed }) {
                    onWin()
                    onXp(150)
                    message = "TABLE NETTOYÉE · victoire réelle"
                }
            }
        }
    }

    LaunchedEffect(mode, playerTurn, physicsRunning, remainingBalls, aiDifficulty) {
        if (mode != "ai" || playerTurn || physicsRunning || remainingBalls == 0 || matchWinner != null) return@LaunchedEffect
        if (cueBallInHand) cueBallInHand = false
        delay(620L)
        val cue = poolBalls.firstOrNull { it.id == 0 } ?: return@LaunchedEffect
        val snapshot = poolBalls
        val plan = withContext(Dispatchers.Default) { planWapiPoolAiShot(snapshot, wapiPoolTargetIds(aiGroup, snapshot)) }
        val target = poolBalls.filter { it.id != 0 && !it.pocketed }.minByOrNull { wapiPoolDistance(cue, it) } ?: return@LaunchedEffect
        val perfect = plan?.angle ?: WapiPoolPresentation.aimAngle(target.x - cue.x, target.y - cue.y)
        val error = when (aiDifficulty) {
            "easy" -> .20f
            "hard" -> .025f
            "ultra" -> .006f
            else -> .075f
        }
        aimAngle = perfect + (((System.nanoTime() % 2001L).toFloat() / 1000f) - 1f) * error
        val plannedPower = plan?.power ?: 58
        power = when (aiDifficulty) {
            "easy" -> (plannedPower * .72f).toInt().coerceIn(34, 64)
            "hard" -> (plannedPower * 1.03f).toInt().coerceIn(42, 92)
            "ultra" -> plannedPower.coerceIn(45, 94)
            else -> plannedPower.coerceIn(38, 82)
        }
        sideSpin = if (aiDifficulty == "ultra") (((plan?.pocketIndex ?: 0) - 2.5f) / 8f).coerceIn(-.32f, .32f) else 0f
        followSpin = when (aiDifficulty) { "hard" -> .18f; "ultra" -> .28f; else -> 0f }
        message = if (plan != null) "L’IA prépare la bille ${plan.targetBallId} vers la poche ${plan.pocketIndex + 1}…" else "L’IA joue un coup de sécurité…"
        delay(520L)
        strike(aiShot = true)
    }

    // A real turn clock is part of the match state, not a decorative count.
    // It only runs while the human can actually line up and release a shot.
    LaunchedEffect(mode, playerTurn, physicsRunning, cueBallInHand, matchWinner, shots) {
        if (mode != "ai" || !playerTurn || physicsRunning || cueBallInHand || matchWinner != null) {
            turnSeconds = 30
            return@LaunchedEffect
        }
        turnSeconds = 30
        repeat(30) {
            delay(1_000L)
            if (!playerTurn || physicsRunning || cueBallInHand || matchWinner != null) return@LaunchedEffect
            turnSeconds -= 1
        }
        if (playerTurn && !physicsRunning && matchWinner == null) {
            message = "Temps écoulé · tour de l’IA."
            playerTurn = false
        }
    }

    LaunchedEffect(groupAnnouncement) {
        if (groupAnnouncement != null) { delay(2200L); groupAnnouncement = null }
    }
    LaunchedEffect(tableTheme, cueStyle) {
        equipmentPrefs.edit().putString("tableTheme", tableTheme).putString("cueStyle", cueStyle).apply()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF03120F)).semantics {
        contentDescription = "Table de billard"
        stateDescription = "$shots tirs · " + when {
            matchWinner != null -> matchWinner!!
            physicsRunning || cueStriking -> "Billes en mouvement"
            !playerTurn && mode == "ai" -> "L’IA joue"
            cueBallInHand -> "Placez la blanche"
            else -> "À vous"
        }
    }) {
        WapiPoolTabletop3D(
            balls = poolBalls,
            aimAngle = aimAngle,
            power = power,
            sideSpin = sideSpin,
            followSpin = followSpin,
            moving = physicsRunning,
            cueStroke = cueStroke,
            cueInHand = cueBallInHand,
            tableTheme = tableTheme,
            cueStyle = cueStyle,
            modifier = Modifier.padding(top = 66.dp, bottom = 40.dp),
            onAim = { x, y ->
                if (cueStriking || (mode == "ai" && !playerTurn)) return@WapiPoolTabletop3D
                if (cueBallInHand) {
                    val candidateX = x.coerceIn(.085f, if (shots == 0) .445f else .915f)
                    val candidateY = y.coerceIn(.115f, .885f)
                    if (isValidWapiCuePlacement(candidateX, candidateY, poolBalls)) {
                        poolBalls = poolBalls.map { if (it.id == 0) it.copy(x = candidateX, y = candidateY, vx = 0f, vy = 0f) else it }
                        message = "Blanche bien placée · touchez Poser la blanche."
                    } else message = "Placement impossible : éloignez la blanche d’une bille ou d’une poche."
                } else poolBalls.firstOrNull { it.id == 0 }?.let { cue ->
                    val dx = x - cue.x
                    val dy = y - cue.y
                    aimAngle = WapiPoolPresentation.aimAngle(dx, dy)
                    message = "Visée ${Math.toDegrees(aimAngle.toDouble()).toInt()}° · ajustez l’effet ou dosez le tir à gauche."
                }
            },
            onRelease = { },
        )
        val canPlay = !physicsRunning && !cueStriking && matchWinner == null && (mode != "ai" || playerTurn)
        PoolAimWheel(aimAngle, canPlay && !cueBallInHand, { aimAngle = it }, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, top = 44.dp))
        PoolMatchScoreboard(
            left = profile.player,
            right = if (mode == "ai") PoolPlayerCard("IA · " + when (aiDifficulty) {
                "easy" -> "Facile"; "hard" -> "Difficile"; "ultra" -> "Expert"; else -> "Intermédiaire"
            }, icon = "robot") else PoolPlayerCard("Entraînement", icon = "cue"),
            leftGroup = playerGroup, rightGroup = aiGroup, balls = poolBalls,
            leftActive = playerTurn && matchWinner == null, rightActive = mode == "ai" && !playerTurn && matchWinner == null,
            leftSeconds = if (playerTurn) turnSeconds else 30, rightSeconds = if (!playerTurn && mode == "ai") turnSeconds else 30,
            status = matchWinner ?: when { cueBallInHand -> "Placez la blanche"; physicsRunning -> "Tir en cours"; mode == "training" -> "$score pts · local"; playerTurn -> "À vous"; else -> "Tour IA" },
            onEditProfile = { editingPlayer = true },
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (cueBallInHand) {
            val cue = poolBalls.first { it.id == 0 }
            PoolPlacementControl(canPlay, isValidWapiCuePlacement(cue.x, cue.y, poolBalls), {
                cueBallInHand = false; WhappySounds.pieceSelected(context)
                message = "Blanche posée · visez puis tirez la jauge."
            }, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        } else if (matchWinner != null) {
            PoolVictoryOverlay(
                winner = matchWinner == "VICTOIRE", player = profile.player, score = score,
                localVictories = profile.localVictories, localDefeats = profile.localDefeats, localCups = profile.localCups,
                cupCompleted = cupJustCompleted,
                onReplay = ::resetTable,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (canPlay && (mode == "training" || shots == 0)) {
            FilledTonalButton(onClick = { cueBallInHand = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                Icon(Icons.Rounded.PanTool, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Placer la blanche", fontSize = 12.sp)
            }
        }
        WapiPoolSpinPad(
            sideSpin = sideSpin, followSpin = followSpin,
            enabled = canPlay && !cueBallInHand,
            onSpinChanged = { side, follow -> sideSpin = side; followSpin = follow },
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(64.dp),
        )
        WapiPoolPowerRail(
            power = power, enabled = canPlay && !cueBallInHand && remainingBalls > 0,
            onPowerChange = { power = it }, onStrike = { strike() },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp, top = 48.dp),
        )
        if (groupAnnouncement != null) {
            Surface(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), color = Color(0xF8071C31), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF63D8FF))) {
                Text(groupAnnouncement.orEmpty(), Modifier.padding(horizontal = 22.dp, vertical = 14.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (mode == "ai" && matchWinner == null && !physicsRunning) {
            TextButton(onClick = { cupActive = !cupActive; cupWins = 0; resetTable() }, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                Text(if (cupActive) "Coupe IA · $cupWins/3" else "Lancer la Coupe IA", color = Color.White)
            }
        }
        TextButton(onClick = { editingEquipment = true }, Modifier.align(Alignment.TopEnd).padding(top = 66.dp, end = 8.dp)) {
            Text("Table & queue", color = Color.White, fontSize = 11.sp)
        }
        FilledIconButton(
            onClick = ::resetTable, enabled = !physicsRunning && !cueStriking,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xD908151B), contentColor = Color.White),
        ) { Icon(Icons.Rounded.RestartAlt, contentDescription = "Recommencer") }
    }
    if (editingPlayer) PoolPlayerEditor(profile) { editingPlayer = false }
    if (editingEquipment) PoolEquipmentSheet(tableTheme, cueStyle, { tableTheme = it }, { cueStyle = it }) { editingEquipment = false }
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
internal fun StrategyBoardGame(checkers: Boolean, onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var checkersRulesPreset by rememberSaveable(checkers) { mutableStateOf("international") }
    var board by rememberSaveable(checkers) { mutableStateOf(initialStrategyBoard(checkers, if (checkers) 10 else 8)) }
    var selected by rememberSaveable(checkers) { mutableIntStateOf(-1) }
    var whiteTurn by rememberSaveable(checkers) { mutableStateOf(true) }
    var versusAi by rememberSaveable(checkers) { mutableStateOf(true) }
    var aiDifficulty by rememberSaveable(checkers) { mutableStateOf("medium") }
    var difficultyMenu by remember { mutableStateOf(false) }
    var rulesMenu by remember { mutableStateOf(false) }
    var gameOver by rememberSaveable(checkers) { mutableStateOf(false) }
    val aiThinking = versusAi && !whiteTurn && !gameOver
    val checkersRules = when (checkersRulesPreset) {
        "english" -> WapiCheckersRules(mandatoryCapture = true, backwardCapture = false, flyingKings = false)
        "free" -> WapiCheckersRules(mandatoryCapture = false, backwardCapture = true, flyingKings = false)
        else -> WapiCheckersRules(mandatoryCapture = true, backwardCapture = true, flyingKings = true)
    }
    var message by rememberSaveable(checkers) { mutableStateOf(if (checkers) "Vous jouez les blancs contre l’IA. Capturez en diagonale." else "Vous jouez les blancs contre l’IA. Sélectionnez une pièce puis une case.") }
    fun choose(index: Int) {
        if (gameOver || aiThinking || index !in board.indices) return
        val piece = board[index]
        if (selected < 0) {
            if ((whiteTurn && whitePiece(piece)) || (!whiteTurn && blackPiece(piece))) { selected = index; WhappySounds.pieceSelected(context); WhappySounds.haptic(context) }
            return
        }
        val from = selected
        if ((whiteTurn && whitePiece(piece)) || (!whiteTurn && blackPiece(piece))) { selected = index; WhappySounds.pieceSelected(context); return }
        val result = if (checkers) WapiGameRules.checkersMove(board, from, index, whiteTurn, checkersRules) else WapiGameRules.chessMove(board, from, index, whiteTurn)
        if (result == null) { message = if (checkers && checkersRules.mandatoryCapture && WapiGameRules.hasCheckersCapture(board, whiteTurn, checkersRules)) "Une capture est disponible et devient prioritaire." else "Mouvement non autorisé ou roi exposé."; WhappySounds.gameInvalid(context); selected = -1; return }
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
                gameOver = true
                if (!checkers && noReply && !WapiGameRules.isChessKingInCheck(result.board, nextSide)) {
                    message = "PAT · partie nulle"
                } else {
                    onWin(); onXp(120); WhappySounds.reward(context)
                    message = if (checkers) "VICTOIRE · aucun mouvement adverse" else "ÉCHEC ET MAT · victoire"
                }
            }
        }
    }
    LaunchedEffect(board, whiteTurn, versusAi, aiDifficulty, checkersRulesPreset, gameOver) {
        if (aiThinking) {
            message = "L’IA analyse le plateau…"
            delay(420L)
            val move = withContext(Dispatchers.Default) {
                WapiGameRules.bestMove(board, whiteTurn = false, checkers = checkers, difficulty = aiDifficulty, checkersRules = checkersRules)
            }
            if (move == null) { gameOver = true; message = "Partie terminée · aucun coup disponible"; return@LaunchedEffect }
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
                    gameOver = noReply
                    message = when {
                        !checkers && noReply && !WapiGameRules.isChessKingInCheck(aiBoard, true) -> "PAT · partie nulle"
                        noReply -> if (checkers) "L’IA gagne · aucun mouvement disponible" else "ÉCHEC ET MAT · l’IA gagne"
                        captured -> "L’IA termine sa prise. À vous de jouer."
                        else -> "À vous de jouer."
                    }
                }
            }
        }
    }
    val legalTargets = remember(board, selected, whiteTurn, checkers, checkersRules) {
        if (selected !in board.indices) emptySet() else board.indices.filterTo(mutableSetOf()) { target ->
            if (checkers) WapiGameRules.checkersMove(board, selected, target, whiteTurn, checkersRules) != null
            else WapiGameRules.chessMove(board, selected, target, whiteTurn) != null
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF03070D))) {
        val wide = maxWidth > maxHeight
        val header: @Composable () -> Unit = {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (checkers) "WAPI DAMES" else "WAPI ÉCHECS", color = WhappySky, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (gameOver) "TERMINÉ" else if (aiThinking) "IA ANALYSE…" else if (whiteTurn) "À VOUS" else "ADVERSAIRE",
                    color = if (whiteTurn) Color(0xFF72F2C8) else Color(0xFFFFC857), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(message, color = Color.White, fontSize = 11.sp, maxLines = if (wide) 5 else 2)
            }
        }
        val controls: @Composable () -> Unit = {
            if (checkers) {
                val presets = listOf("international" to "INTERNATIONAL", "english" to "ANGLAIS", "free" to "LIBRE")
                Box {
                    OutlinedButton(onClick = { rulesMenu = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text(presets.first { it.first == checkersRulesPreset }.second + " ▾", fontSize = 9.sp)
                    }
                    DropdownMenu(rulesMenu, { rulesMenu = false }) {
                        presets.forEach { option ->
                            DropdownMenuItem(text = { Text(option.second) }, onClick = {
                                rulesMenu = false
                                checkersRulesPreset = option.first
                                board = initialStrategyBoard(true, if (option.first == "international") 10 else 8)
                                selected = -1
                                whiteTurn = true
                                gameOver = false
                                message = "Nouvelle partie · règles ${option.second.lowercase()}"
                            })
                        }
                    }
                }
            }
            if (versusAi) {
                val levels = listOf("easy" to "IA FACILE", "medium" to "IA MOYENNE", "hard" to "IA DIFFICILE", "ultra" to "IA ULTRA")
                Box {
                    OutlinedButton(onClick = { difficultyMenu = true }, colors = ButtonDefaults.outlinedButtonColors(containerColor = WhappyBlue, contentColor = Color.White)) {
                        Text(levels.first { it.first == aiDifficulty }.second + " ▾", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(difficultyMenu, { difficultyMenu = false }) {
                        levels.forEach { option ->
                            DropdownMenuItem(text = { Text(option.second) }, onClick = { aiDifficulty = option.first; difficultyMenu = false })
                        }
                    }
                }
            }
            OutlinedButton(onClick = { versusAi = !versusAi; board = initialStrategyBoard(checkers, if (checkersRulesPreset == "international") 10 else 8); selected = -1; whiteTurn = true; gameOver = false; message = if (!versusAi) "Deux joueurs sur cet appareil. Les blancs commencent." else "Vous jouez les blancs contre l’IA." }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(if (versusAi) "2 JOUEURS" else "CONTRE IA", fontSize = 9.sp) }
            Button(onClick = { board = initialStrategyBoard(checkers, if (checkersRulesPreset == "international") 10 else 8); selected = -1; whiteTurn = true; gameOver = false; message = if (versusAi) "Nouvelle partie contre l’IA." else "Nouvelle partie locale." }) { Text("REJOUER", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
        WapiStrategyTabletop3D(
            checkers = checkers, board = board, selected = selected,
            legalTargets = legalTargets, onSquareTapped = ::choose,
            modifier = if (wide) Modifier.padding(start = 206.dp) else Modifier.padding(top = 114.dp, bottom = 80.dp),
        )
        if (wide) {
            Column(
                Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(color = Color(0xE60A1320), shape = RoundedCornerShape(18.dp)) { header() }
                Surface(color = Color(0xE60A1320), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { controls() }
                }
                Text("Touchez une pièce, puis sa destination. Glissez pour orienter le plateau.", color = Color.White.copy(alpha = .62f), fontSize = 10.sp)
            }
        } else {
            Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color(0xE60A1320), shape = RoundedCornerShape(18.dp)) { header() }
            Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Color(0xE60A1320), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { controls() }
            }
        }
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
        if (king) Text("♛", color = if (light) Color(0xFFB8860B) else Color(0xFFFFD54F), fontSize = (size.value * .54f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WapiCardDuel(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var round by rememberSaveable { mutableIntStateOf(0) }; var player by rememberSaveable { mutableIntStateOf(0) }; var rival by rememberSaveable { mutableIntStateOf(0) }; var playerScore by rememberSaveable { mutableIntStateOf(0) }; var rivalScore by rememberSaveable { mutableIntStateOf(0) }; var message by rememberSaveable { mutableStateOf("Tirez une carte. La plus forte remporte la manche.") }
    val names = listOf("2","3","4","5","6","7","8","9","10","V","D","R","A")
    fun draw() { round += 1; player = ((System.currentTimeMillis() / 31L) % 13L).toInt() + 2; rival = ((System.currentTimeMillis() / 47L + round) % 13L).toInt() + 2; WhappySounds.cardFlip(context); when { player > rival -> { playerScore++; onXp(10); WhappySounds.reward(context); message = "Manche gagnée · +10 XP" }; rival > player -> { rivalScore++; WhappySounds.gameInvalid(context); message = "L’adversaire gagne cette manche." }; else -> message = "Égalité parfaite." }; WhappySounds.haptic(context); if (playerScore == 5) { onWin(); onXp(100); message = "VICTOIRE DU DUEL · +100 XP" } }
    Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121A47))) { Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("WAPI CARDS", color = WhappySky, fontWeight = FontWeight.Bold, fontSize = 10.sp); Text("$playerScore  —  $rivalScore", Modifier.padding(vertical = 8.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { listOf(player to "VOUS", rival to "RIVAL").forEachIndexed { index, card -> Card(Modifier.size(112.dp, 164.dp).graphicsLayer { rotationY = if (round == 0) 180f else if (index == 0) -8f else 8f; rotationX = 4f; shadowElevation = 28f; cameraDistance = 18f }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(card.second, color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(if (card.first == 0) "W" else names[(card.first - 2).coerceIn(0, 12)], color = if (index == 0) WhappyBlue else Color(0xFFE53935), fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text(if (index == 0) "◆" else "♥", color = if (index == 0) WhappyBlue else Color(0xFFE53935), fontSize = 22.sp) } } } }; Text(message, Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = .84f), fontSize = 12.sp); Button(onClick = ::draw, enabled = playerScore < 5, modifier = Modifier.width(320.dp).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("TIRER LES CARTES", fontWeight = FontWeight.Bold) }; OutlinedButton(onClick = { round = 0; player = 0; rival = 0; playerScore = 0; rivalScore = 0; message = "Nouvelle partie." }, Modifier.padding(top = 8.dp).width(320.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("REJOUER") } } }
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
                else -> { message = "L’IA gagne avec ${wapiPokerHandName(rivalScore)}."; WhappySounds.gameInvalid(context) }
            }
        } else {
            message = when (stage) { 1 -> "Flop révélé · l’IA suit."; 2 -> "Turn révélé · l’IA suit."; else -> "River révélée · choisissez votre action." }
        }
    }

    LaunchedEffect(Unit) { deal() }
    Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF092E2A))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("WAPI POKER", color = Color(0xFF68F0C1), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("Texas Hold’em", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Contre l’IA · jetons virtuels uniquement", color = Color.White.copy(alpha = .68f), fontSize = 10.sp) }
                Text("POT $pot", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (hidden) "W" else "${card.label}${card.suit}", color = if (hidden) Color.White else if (red) Color(0xFFD7263D) else WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ArcadeChallengeCard(selected: String, round: Int, answer: String?, onAnswer: (String) -> Unit, onNext: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(selected, color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Manche $round · question 1/3", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Quel espace WAPI permet de diffuser en direct ?", color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
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
    Column(Modifier.clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(label.uppercase(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold); Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                    Text("WAPI PAY", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Paiements non activés", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
        item { Text("Préparer une demande", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
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
            Card(Modifier.fillMaxWidth().wapiClickable(onClick = onOpenBusiness), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BusinessCenter, null, tint = WhappyBlue)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Espace Business", color = WhappyDark, fontWeight = FontWeight.Bold); Text("Pages, campagnes, deals et suivi des paiements", color = WhappyMuted, fontSize = 11.sp) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
                }
            }
        }
        item { Text("Brouillons récents", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
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
            title = { Text("Demande · $service", fontWeight = FontWeight.Bold) },
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
                Text("MON WAPI", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Jumeau numérique · sous votre contrôle", color = WhappyMuted, fontSize = 11.sp)
            }
            if (state.twinBusy) CircularProgressIndicator(Modifier.size(22.dp), color = WhappyBlue, strokeWidth = 2.dp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("WAPI JUMEAU NUMÉRIQUE", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Votre présence,\nmultipliée.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                        Text("Préparez votre image, votre voix et vos missions. Vous gardez le dernier mot sur chaque production.", Modifier.padding(top = 9.dp), color = Color.White, lineHeight = 19.sp)
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text("$readiness%", color = Color.White, fontWeight = FontWeight.Bold) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(if (profile.isRenderReady) "CAPTURES SÉCURISÉES" else "CONFIGURATION EN COURS", color = Color.White, fontWeight = FontWeight.Bold)
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
                                Text("Centre de contrôle du Jumeau", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                Text("JUMEAU NUMÉRIQUE DE ${userName.uppercase()}", Modifier.padding(top = 13.dp), color = Color.White, fontWeight = FontWeight.Bold)
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
                    item { StudioTitle("02 / EMPREINTE VOCALE", "Votre ton, même quand vous travaillez ailleurs", "L’échantillon prépare votre voix numérique. Sa génération reste soumise à votre consentement et à un service vocal autorisé.") }
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
                    if (automations.isNotEmpty()) item { Text("Missions du WAPI", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhappyDark) }
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
                    if (renders.isNotEmpty()) item { Text("Productions préparées", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhappyDark) }
                    items(renders, key = { it.id }) { render ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Movie, null, tint = WhappyBlue)
                                Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(render.title, fontWeight = FontWeight.Bold); Text("${render.language} · ${if (render.status == "prepared") "Prêt pour le moteur sécurisé" else render.status}", color = WhappyMuted, fontSize = 10.sp) }
                                Text("IA", color = WhappyBlue, fontWeight = FontWeight.Bold)
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
        Text(kicker, color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(title, Modifier.padding(top = 5.dp), color = WhappyDark, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
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
    Card(Modifier.width(width).height(112.dp).wapiClickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappySky.copy(alpha = .18f)), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(WhappyAuroraSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WhappyDeepBlue, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.weight(1f))
            Text(title, fontWeight = FontWeight.SemiBold, color = WhappyDark)
            Text(subtitle, fontSize = 11.sp, color = WhappyMuted)
        }
    }
}

@Composable
private fun MomentCard(author: String, badge: String, title: String, body: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .65f)), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WhappyAuroraSoft), contentAlignment = Alignment.Center) { Text(initials(author), color = WhappyDeepBlue, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(author, fontWeight = FontWeight.Bold); Icon(Icons.Rounded.Verified, null, tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }
                    Text(badge, color = WapiVerifiedGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(title, Modifier.padding(top = 18.dp), color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(body, Modifier.padding(top = 8.dp), color = WhappyMuted, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun CallsScreen(
    conversations: List<WhappyConversation>,
    businessPageId: String = "",
    businessName: String = "",
    businessPhotoUrl: String = "",
    onOpenConversation: (WhappyConversation) -> Unit,
) {
    val calls = LocalWhappyCalls.current
    val context = LocalContext.current
    val businessMode = businessPageId.isNotBlank()
    var recentCalls by remember(businessPageId) { mutableStateOf(WapiCallHistory.entries(context, businessPageId)) }
    val callVisible = calls?.state?.visible == true
    LaunchedEffect(callVisible, businessPageId) {
        if (!callVisible) recentCalls = WapiCallHistory.entries(context, businessPageId)
    }

    LazyColumn(Modifier.fillMaxSize().background(WapiCanvas), contentPadding = PaddingValues(WapiMobile.screen, 14.dp, WapiMobile.screen, 30.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, WhappyLine, RoundedCornerShape(20.dp))) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (businessMode) UserAvatar(businessPhotoUrl, businessName, 48.dp, shape = RoundedCornerShape(14.dp))
                    else Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Phone, null, tint = WhappyDeepBlue, modifier = Modifier.size(23.dp)) }
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text(if (businessMode) "Appels Business" else "Appels", style = MaterialTheme.typography.headlineLarge)
                        Text(if (businessMode) businessName.ifBlank { "Identité professionnelle" } else "Audio et vidéo, simplement", color = WhappyMuted, fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = if (businessMode) Color(0xFFEAF7F1) else WapiSoftBlue) {
                        Text(if (businessMode) "ESPACE SÉPARÉ" else "WEBRTC", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = if (businessMode) WapiSuccess else WhappyDeepBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
                    }
                }
            }
        }
        item { ActusSectionTitle("Récents", if (recentCalls.isEmpty()) "Aucun appel pour le moment" else "Reprendre une conversation en un geste") }
        if (recentCalls.isEmpty()) item { EmptyState("Aucun appel récent", "Vos appels audio et vidéo apparaîtront ici.") }
        items(recentCalls.take(8), key = { it }) { raw ->
            val parts = raw.split("|", limit = 8)
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
            // The live profile always wins over the immutable call-history
            // snapshot so a changed avatar or certification appears at once.
            val resolvedPhoto = matchedConversation?.peer?.photoUrl?.takeIf(String::isNotBlank) ?: recentPhoto
            val resolvedVerified = matchedConversation?.peer?.let { it.verified || WhappyIdentity.isFounder(it.phoneNumber) } == true
            val missed = direction == "missed"
            val callTint = if (missed) Color(0xFFE05252) else WhappyBlue
            val callLabel = when (direction) {
                "missed" -> "Appel manqué"
                "incoming" -> "Appel reçu"
                else -> "Appel émis"
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, if (missed) WapiDanger.copy(alpha = .14f) else WhappyLine.copy(alpha = .62f)), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            UserAvatar(resolvedPhoto, name, 50.dp, shape = RoundedCornerShape(15.dp))
                            Box(Modifier.size(17.dp).clip(RoundedCornerShape(6.dp)).background(if (missed) WapiDanger else WapiSuccess), contentAlignment = Alignment.Center) {
                                Icon(if (missed) Icons.Rounded.CallEnd else if (video) Icons.Rounded.Videocam else Icons.Rounded.Phone, null, tint = Color.White, modifier = Modifier.size(10.dp))
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = WhappyDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (resolvedVerified) Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp))
                            }
                            Text("$callLabel · ${if (video) "Vidéo" else "Audio"}", color = callTint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(formatCallMoment(timestamp), color = WhappyMuted, fontSize = 10.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        val reachable = calls != null && (resolvedUserId.isNotBlank() || resolvedPhone.isNotBlank())
                        WapiCallAction(Icons.Rounded.Phone, "Audio", reachable, Modifier.weight(1f)) {
                            if (resolvedUserId.isNotBlank()) calls?.start(WhappyMember(resolvedUserId, name, resolvedPhone, resolvedPhoto, resolvedVerified), false) else calls?.startByPhone(resolvedPhone, false)
                        }
                        WapiCallAction(Icons.Rounded.Videocam, "Vidéo", reachable, Modifier.weight(1f), emphasized = true) {
                            if (resolvedUserId.isNotBlank()) calls?.start(WhappyMember(resolvedUserId, name, resolvedPhone, resolvedPhoto, resolvedVerified), true) else calls?.startByPhone(resolvedPhone, true)
                        }
                    }
                }
            }
        }
        item { ActusSectionTitle("Contacts", "Joignables sur WAPI") }
        if (conversations.isEmpty()) {
            item { EmptyState("Aucun contact", "Ajoutez un contact dans Messages pour pouvoir l’appeler.") }
        } else {
            items(conversations, key = { "call-${it.id}" }) { conversation ->
                // A WAPI-to-WAPI call is addressed by the account uid; users who
                // hide their phone number must still be reachable in audio/video.
                val callable = conversation.peer.uid.isNotBlank()
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .62f)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 54.dp, shape = RoundedCornerShape(14.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(conversation.peer.displayName, color = WhappyDark, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!conversation.isGroup && (conversation.peer.verified || WhappyIdentity.isFounder(conversation.peer.phoneNumber))) {
                                        Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp))
                                    }
                                }
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
        item { Text("Les appels WAPI utilisent votre connexion Internet et adaptent automatiquement la qualité au réseau disponible.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
    }
}

@Composable
private fun CallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = .08f)).padding(12.dp)) {
        Text(label.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(15.dp)).wapiClickable(enabled = enabled, onClick = onClick),
        color = when {
            !enabled -> Color(0xFFF0F2F4)
            emphasized -> WhappyBlue
            else -> Color(0xFFEAF5FD)
        },
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled && emphasized) Color.White else if (enabled) WhappyBlue else WhappyMuted, modifier = Modifier.size(19.dp))
            Text(label, Modifier.padding(start = 7.dp), color = if (enabled && emphasized) Color.White else if (enabled) WhappyDark else WhappyMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WapiProfileAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> WhappySurface
        emphasized -> WhappyBlue
        else -> WapiSoftBlue
    }
    Surface(
        modifier = modifier.height(70.dp).wapiClickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = if (!emphasized && enabled) androidx.compose.foundation.BorderStroke(1.dp, WhappySky.copy(alpha = .18f)) else null,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = if (enabled && emphasized) Color.White else if (enabled) WhappyDeepBlue else WhappyMuted, modifier = Modifier.size(21.dp))
            Text(label, Modifier.padding(top = 5.dp), color = if (enabled && emphasized) Color.White else if (enabled) WhappyDark else WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WapiProfileInfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WhappyDeepBlue, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(label.uppercase(), color = WhappyMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .45.sp)
            Text(value, Modifier.padding(top = 2.dp), color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun MessagesScreen(
    conversations: List<WhappyConversation>,
    accountName: String,
    accountPhotoUrl: String,
    businessMode: Boolean,
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
    recentTabs: List<WhappyTab>,
    onOpenRecent: (WhappyTab) -> Unit,
    onOpenStory: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var searchingBusiness by remember { mutableStateOf(false) }
    var messageSection by rememberSaveable { mutableIntStateOf(if (initialSection == 1) 1 else if (initialSection == 2) 2 else 0) }
    var creatingChannel by rememberSaveable { mutableStateOf(false) }
    var creatingGroup by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupMembers by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Set<String>, ArrayList<String>>(
        save = { ArrayList(it) }, restore = { it.toSet() },
    )) { mutableStateOf(emptySet<String>()) }
    var selectedContactProfile by remember { mutableStateOf<WhappyContact?>(null) }
    var contactPhotoPreview by remember { mutableStateOf<String?>(null) }
    var groupPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var channelName by rememberSaveable { mutableStateOf("") }
    var channelDescription by rememberSaveable { mutableStateOf("") }
    var channelCategory by rememberSaveable { mutableStateOf("Communauté") }
    var conversationSearch by rememberSaveable { mutableStateOf(initialQuery) }
    var phoneField by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val phone = phoneField.text
    var contactCountry by rememberSaveable { mutableStateOf("+242") }
    var contactCountryMenu by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scannerOpen by remember { mutableStateOf(false) }
    var imageScanInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val compactMessages = WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("compact_mode", false)
    val scope = rememberCoroutineScope()
    val conversationListState = rememberLazyListState()
    val emptyConversationScrollState = rememberScrollState()
    val density = LocalDensity.current
    var recentFullscreen by rememberSaveable { mutableStateOf(false) }
    var recentCloseDragPx by remember { mutableFloatStateOf(0f) }
    var recentPullProgress by remember { mutableFloatStateOf(0f) }
    val recentPull = remember(density) { WapiRecentPull(with(density) { 72.dp.toPx() }) }
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

    val canRevealRecents = messageSection == 0 && conversationSearch.isBlank() && when {
        filteredConversations.isEmpty() -> !emptyConversationScrollState.canScrollBackward
        else -> conversationListState.firstVisibleItemIndex == 0 && conversationListState.firstVisibleItemScrollOffset == 0
    }
    val currentCanReveal by rememberUpdatedState(canRevealRecents && !recentFullscreen)
    val recentNestedScroll = remember(recentPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f) {
                    recentPull.move(available.y, currentCanReveal)
                    recentPullProgress = recentPull.progress
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || !currentCanReveal) return Offset.Zero
                val opened = recentPull.move(available.y, true)
                recentPullProgress = recentPull.progress
                if (opened) {
                    recentFullscreen = true
                    WhappySounds.haptic(context)
                }
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                recentPull.reset()
                recentPullProgress = 0f
                // Let Compose keep its natural bounce/inertia. Earlier code
                // consumed every fling after a pull, making Messages feel heavy.
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(messageSection, conversationSearch, recentFullscreen) {
        recentPull.reset()
        recentPullProgress = 0f
    }

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

    val visibleAvatarSources = remember(conversations, contacts) {
        (conversations.asSequence().map { it.peer.photoUrl } + contacts.asSequence().map { it.member.photoUrl })
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .take(24)
            .toList()
    }
    LaunchedEffect(visibleAvatarSources) {
        visibleAvatarSources.forEach { source ->
            launch { WapiStableImageLoader.prefetch(context, source) }
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
        Row(Modifier.padding(horizontal = WapiMobile.screen, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when (messageSection) { 1 -> "Contacts"; 2 -> "Chaînes"; else -> if (businessMode) "Messages Business" else "Messages" }, color = WhappyDark, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.35).sp)
                Text(when (messageSection) { 1 -> "Contacts WAPI"; 2 -> "Chaînes suivies"; else -> if (businessMode) "Clients et équipe" else "Discussions" }, color = WhappyMuted, fontSize = 12.sp)
            }
            FilledIconButton(
                onClick = { when (messageSection) { 2 -> creatingChannel = true; 0 -> creatingGroup = true; else -> { phoneField = TextFieldValue(""); resetContactSearch(); adding = true } } },
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(13.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
            ) {
                Icon(Icons.Rounded.Add, when (messageSection) { 2 -> "Créer une chaîne"; 0 -> "Créer un groupe"; else -> "Ajouter un contact" }, tint = Color.White)
            }
        }
        if (messageSection == 0 && businessMode) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 2.dp),
                color = WhappyNavy,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(accountPhotoUrl, accountName, 42.dp, shape = RoundedCornerShape(13.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(accountName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("  BUSINESS", color = WhappySky, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Vous répondez au nom de l’entreprise", color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
                    }
                    Icon(Icons.Rounded.BusinessCenter, "Identité Business active", tint = WhappySky, modifier = Modifier.size(21.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(WapiCanvas).padding(horizontal = WapiMobile.screen)) {
            listOf("Discussions", "Contacts", "Chaînes").forEachIndexed { index, label ->
                Column(
                    Modifier.weight(1f).height(43.dp).wapiClickable { messageSection = index; conversationSearch = "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(label, color = if (messageSection == index) WhappyDark else WhappyMuted, fontSize = 12.sp, fontWeight = if (messageSection == index) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(28.dp).height(2.dp).background(if (messageSection == index) WhappyBlue else Color.Transparent, CircleShape))
                }
            }
        }
        HorizontalDivider(color = WhappyLine.copy(alpha = .62f))
        OutlinedTextField(
            conversationSearch,
            { conversationSearch = it.take(120) },
            Modifier.fillMaxWidth().padding(horizontal = WapiMobile.screen, vertical = 9.dp).height(48.dp),
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
                Card(Modifier.padding(18.dp).fillMaxWidth().wapiClickable { phoneField = TextFieldValue(""); resetContactSearch(); adding = true }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = WhappyBlue) }
                        Text("Ajoutez votre premier contact", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Avec son numéro ou son code QR WAPI. Vous le retrouverez ici à tout moment.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                        Text("AJOUTER UN CONTACT", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item { Text("CONTACTS WAPI", Modifier.padding(start = 5.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                items(visibleContacts, key = { it.member.uid }) { contact ->
                    Card(Modifier.fillMaxWidth().wapiClickable(enabled = !contactBusy) { selectedContactProfile = contact }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(contact.member.displayName, fontWeight = FontWeight.Bold, color = WhappyDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (contact.member.verified || WhappyIdentity.isFounder(contact.member.phoneNumber)) {
                                        Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp))
                                    }
                                }
                                Text(contact.member.phoneNumber.ifBlank { "Contact WAPI" }, color = WhappyMuted, fontSize = 11.sp)
                            }
                            Text("Profil ›", color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().nestedScroll(recentNestedScroll),
        ) {
            Box(Modifier.fillMaxSize()) {
            if (loading && conversations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
            else if (filteredConversations.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(emptyConversationScrollState), horizontalAlignment = Alignment.CenterHorizontally) {
                    WapiPullRecentsHint(recentPullProgress)
                    Box(Modifier.fillMaxWidth().heightIn(min = 360.dp)) {
                        EmptyState(if (conversationSearch.isBlank()) "Aucune conversation" else "Aucun résultat", if (conversationSearch.isBlank()) "Ouvrez l’onglet Contacts pour ajouter une personne sur WAPI." else "Essayez un autre nom ou un mot du dernier message.")
                    }
                }
            }
            else LazyColumn(state = conversationListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = WapiMobile.screen, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (conversationSearch.isBlank()) item(key = "wapi-recents-hint") { WapiPullRecentsHint(recentPullProgress) }
                if (conversationSearch.isBlank()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Conversations", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${directConversations.size} privées · ${groupConversations.size} groupes", color = WhappyMuted, fontSize = 10.sp)
                            }
                            Surface(
                                modifier = Modifier.clip(CircleShape).wapiClickable { creatingGroup = true },
                                color = WapiSoftBlue,
                                shape = CircleShape,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Groups, null, tint = WhappyBlue, modifier = Modifier.size(17.dp))
                                    Text("  Nouveau groupe", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                itemsIndexed(filteredConversations, key = { _, item -> item.id }) { index, conversation ->
                Column(Modifier.fillMaxWidth().background(if (conversation.unread) WapiUnreadSurface else Color.White)) {
                Row(
                    Modifier.fillMaxWidth().wapiClickable { onOpen(conversation) }.padding(horizontal = 4.dp, vertical = if (compactMessages) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (conversation.isGroup && conversation.peer.photoUrl.isBlank()) {
                        Box(Modifier.size(if (compactMessages) 46.dp else 52.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, null, tint = Color.White)
                        }
            } else {
                val peerHasUnseenStory = storyStatuses.any { it.authorId == conversation.peer.uid && !it.viewedByCurrentUser }
                StoryRingAvatar(
                    photoUrl = conversation.peer.photoUrl,
                    name = conversation.peer.displayName,
                    size = if (compactMessages) 46.dp else 52.dp,
                    hasUnseenStory = peerHasUnseenStory,
                    onClick = { if (peerHasUnseenStory) onOpenStory(conversation.peer.uid) else onOpen(conversation) },
                    shape = RoundedCornerShape(14.dp),
                )
                    }
                    Column(Modifier.weight(1f).padding(start = if (compactMessages) 10.dp else 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(conversation.peer.displayName, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, color = WhappyDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!conversation.isGroup && (conversation.peer.verified || WhappyIdentity.isFounder(conversation.peer.phoneNumber))) {
                                Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp))
                            }
                            if (conversation.profileType == "business") {
                                Spacer(Modifier.width(6.dp))
                                Surface(color = WhappyBlue.copy(alpha = .11f), shape = RoundedCornerShape(6.dp)) { Text("CLIENT", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = WhappyBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
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
                    if (conversation.unread) {
                        Surface(Modifier.padding(start = 8.dp), color = WhappyBlue, shape = CircleShape) {
                            Text("N", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (index < filteredConversations.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp).background(WhappyLine.copy(alpha = .72f)))
                }
            }
            }
        }
        }
        if (recentFullscreen) Dialog(
            onDismissRequest = { recentFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            WapiPullDownRecentPanel(
                recentTabs = recentTabs,
                revealProgress = 1f,
                modifier = Modifier.fillMaxSize().background(Color(0xFF071827)).statusBarsPadding().navigationBarsPadding()
                    .graphicsLayer {
                        translationY = recentCloseDragPx.coerceAtMost(0f)
                        alpha = (1f + recentCloseDragPx / with(density) { 340.dp.toPx() }).coerceIn(.72f, 1f)
                    }
                    .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { recentCloseDragPx = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount < 0f) {
                                change.consume()
                                recentCloseDragPx += dragAmount
                            }
                        },
                        onDragEnd = {
                            if (recentCloseDragPx < -with(density) { 88.dp.toPx() }) recentFullscreen = false
                            recentCloseDragPx = 0f
                        },
                        onDragCancel = { recentCloseDragPx = 0f },
                    )
                },
                onOpen = { tab -> recentFullscreen = false; onOpenRecent(tab) },
                onClose = { recentFullscreen = false },
            )
        }
        if (adding) Dialog(
            onDismissRequest = { if (!contactBusy) { adding = false; resetContactSearch() } },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(enabled = !contactBusy, onClick = { adding = false; resetContactSearch() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = WhappyDark)
                        }
                        Text(
                            if (contactSearchResult == null) "Ajouter un contact" else "Contact trouvé",
                            modifier = Modifier.weight(1f),
                            color = WhappyDark,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    HorizontalDivider(color = WhappyLine.copy(alpha = .70f))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Numéro de téléphone", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Le numéro sert uniquement à retrouver le compte WAPI.", color = WhappyMuted, fontSize = 12.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                val selectedCountry = authCountries.firstOrNull { it.code == contactCountry }
                                OutlinedButton(
                                    onClick = { contactCountryMenu = true },
                                    modifier = Modifier.width(108.dp).height(56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 9.dp),
                                ) {
                                    Text("${selectedCountry?.flag.orEmpty()} $contactCountry", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("  ▾", color = WhappyMuted, fontSize = 9.sp)
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
                                placeholder = { Text("Numéro") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { if (!contactBusy && !preview && isPhoneComplete) startContactSearch(phone) }),
                                visualTransformation = PhoneNumberSpacingTransformation,
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                            )
                        }
                        when {
                            phone.isNotBlank() && !isPhoneComplete -> Text("Vérifiez l’indicatif et le numéro.", color = Color(0xFFB54747), fontSize = 11.sp)
                            normalizedContactPhone != null -> Text("Numéro reconnu  ·  $normalizedContactPhone", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            enabled = !contactBusy && !preview && isPhoneComplete,
                            onClick = { startContactSearch(phone) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WhappyBlue),
                        ) {
                            if (contactBusy && contactSearchResult == null) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Rechercher sur WAPI", fontWeight = FontWeight.Bold)
                        }

                        if (contactSearchResult != null) {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = WapiBlueMist,
                                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .16f)),
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isFounderContact = WhappyIdentity.isFounder(contactSearchResult.phoneNumber)
                                    UserAvatar(contactSearchResult.photoUrl, contactSearchResult.displayName, 48.dp, shape = RoundedCornerShape(14.dp))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(contactSearchResult.displayName, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (contactSearchResult.verified || isFounderContact) Icon(Icons.Rounded.Verified, "Compte certifié", Modifier.padding(start = 4.dp).size(15.dp), tint = WapiVerifiedGray)
                                        }
                                        Text(contactSearchResult.phoneNumber.ifBlank { contactSearchPhone }, color = WhappyMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                            Button(
                                enabled = !contactBusy && !preview,
                                onClick = { onAddSearchedContact() },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                if (contactBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Ajouter et ouvrir la discussion", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (contactSearchMessage != null) Text(contactSearchMessage, color = if (contactSearchResult == null) WhappyMuted else WhappyBlue, fontSize = 12.sp, lineHeight = 17.sp)

                        Text("Autres moyens", Modifier.padding(top = 8.dp), color = WhappyMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                        WapiContactMethodRow(Icons.Rounded.QrCode, "Scanner un code QR", "Ouvrir directement le profil WAPI", !contactBusy && !scannerOpen && !imageScanInProgress, ::openWapiScanner)
                        WapiContactMethodRow(Icons.Rounded.Photo, if (imageScanInProgress) "Lecture de l’image…" else "Choisir une image QR", "Importer une image depuis la galerie", !contactBusy && !scannerOpen && !imageScanInProgress) {
                            if (preview) scanError = "L’import est disponible dans l’application connectée"
                            else qrImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        WapiContactMethodRow(Icons.Rounded.Share, "Coller un numéro", "Utiliser le contenu du presse-papiers", !contactBusy) {
                            val clipboardText = runCatching {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                            }.getOrDefault("")
                            if (clipboardText.isBlank()) scanError = "Le presse-papiers est vide."
                            else applyContactInputValue(clipboardText, autoSearch = true)
                        }
                        WapiContactMethodRow(Icons.Rounded.Storefront, "Rechercher un Business", "Trouver une entreprise enregistrée sur WAPI", !contactBusy) {
                            adding = false
                            resetContactSearch()
                            searchingBusiness = true
                        }
                    }
                }
            }
        }
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
            val isVerifiedProfile = contact.member.verified || WhappyIdentity.isFounder(contact.member.phoneNumber)
            Dialog(
                onDismissRequest = { selectedContactProfile = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxWidth(.94f).heightIn(max = 700.dp), shape = RoundedCornerShape(30.dp), color = WapiSheet, shadowElevation = 20.dp) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().background(WhappyAuroraSoft).padding(20.dp)) {
                            Box(Modifier.align(Alignment.TopEnd).size(110.dp).offset(x = 36.dp, y = (-42).dp).clip(CircleShape).background(WhappySky.copy(alpha = .12f)))
                            IconButton(onClick = { selectedContactProfile = null }, modifier = Modifier.align(Alignment.TopEnd).size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .86f))) { Icon(Icons.Rounded.Close, "Fermer", tint = WhappyDark) }
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(shape = RoundedCornerShape(25.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(3.dp, WhappyBlue.copy(alpha = .20f)), shadowElevation = 10.dp) {
                                    UserAvatar(contact.member.photoUrl, contact.member.displayName, 104.dp, Modifier.wapiClickable(enabled = contact.member.photoUrl.isNotBlank()) { contactPhotoPreview = contact.member.photoUrl; selectedContactProfile = null }, RoundedCornerShape(22.dp))
                                }
                                Text(contact.member.displayName, Modifier.padding(top = 14.dp), color = WhappyDark, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                if (isVerifiedProfile) Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.size(16.dp)); Text(if (WhappyIdentity.isFounder(contact.member.phoneNumber)) " ${WhappyIdentity.founderBadgeLabel}" else " Compte WAPI vérifié", color = WapiVerifiedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                Text(if (contact.member.isOnline) "● En ligne maintenant" else if (contact.member.lastSeenAt > 0L) formatLastSeen(contact.member.lastSeenAt) else "Dernière présence indisponible", Modifier.padding(top = 6.dp), color = if (contact.member.isOnline) WapiSuccess else WhappyMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                if (contact.member.photoUrl.isNotBlank()) Text("Touchez la photo pour l’ouvrir", Modifier.padding(top = 5.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                WapiProfileAction(Icons.Rounded.ChatBubble, "Message", true, Modifier.weight(1f), emphasized = true) { selectedContactProfile = null; onOpenContact(contact) }
                                WapiProfileAction(Icons.Rounded.Phone, "Audio", calls != null && contact.member.uid.isNotBlank(), Modifier.weight(1f)) { calls?.start(contact.member, false); selectedContactProfile = null }
                                WapiProfileAction(Icons.Rounded.Videocam, "Vidéo", calls != null && contact.member.uid.isNotBlank(), Modifier.weight(1f)) { calls?.start(contact.member, true); selectedContactProfile = null }
                            }
                            Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .70f))) {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    WapiProfileInfoRow(Icons.Rounded.Phone, "Numéro", contact.member.phoneNumber.ifBlank { "Protégé par la confidentialité" })
                                    WapiProfileInfoRow(Icons.Rounded.Person, "Identifiant WAPI", contact.member.uid.ifBlank { "En cours de synchronisation" }.take(18))
                                    WapiProfileInfoRow(Icons.Rounded.Lock, "Confidentialité", "Seules les informations autorisées sont visibles")
                                }
                            }
                            Text("Activité publique", color = WhappyDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Surface(color = WapiBlueMist, shape = RoundedCornerShape(18.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = WhappyDeepBlue) }
                                    Column(Modifier.padding(start = 11.dp)) { Text("Radios, playlists et Lives", color = WhappyDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("Les contenus publics apparaissent ici lorsqu’ils sont disponibles.", color = WhappyMuted, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
        contactPhotoPreview?.let { source -> ImageZoomViewer(source) { contactPhotoPreview = null } }
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
        if (creatingChannel) WapiEditorScreen(
            title = "Nouvelle chaîne", action = "Créer la chaîne", busy = channelBusy,
            ready = channelName.trim().length >= 3 && channelDescription.trim().length >= 10,
            onDismiss = { creatingChannel = false },
            onSubmit = { onCreateChannel(channelName, channelDescription, channelCategory); creatingChannel = false; channelName = ""; channelDescription = "" },
        ) {
            item { WapiEditorSection("Un espace de diffusion public", "Vous publiez les actualités. Vos abonnés les suivent et peuvent réagir.") }
            item { WapiEditorField(channelName, { channelName = it }, "Nom de la chaîne", 80, enabled = !channelBusy) }
            item { WapiEditorField(channelDescription, { channelDescription = it }, "Description", 300, enabled = !channelBusy, multiline = true) }
            item { WapiEditorSection("Catégorie") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Communauté", "Actualités", "Créateurs", "Shopping", "Sport", "Tech").forEach { category ->
                        FilterChip(selected = channelCategory == category, enabled = !channelBusy,
                            onClick = { channelCategory = category }, label = { Text(category) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateGroupDialog(
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
    var search by rememberSaveable { mutableStateOf("") }
    val members = remember(contacts, search) {
        contacts.distinctBy { it.member.uid }.filter {
            search.isBlank() || it.member.displayName.contains(search, ignoreCase = true) || it.member.phoneNumber.contains(search)
        }
    }
    val ready = groupName.trim().length in 2..80 && selectedIds.size in 1..64
    var photoToCrop by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoToCrop = uri
    }
    WapiEditorScreen("Nouveau groupe", "Créer le groupe", busy, ready, onDismiss, onCreate) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UserAvatar(photoUri?.toString().orEmpty(), groupName.ifBlank { "Groupe" }, 72.dp, shape = RoundedCornerShape(18.dp))
                Column {
                    Text("Photo du groupe", color = WhappyInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    TextButton(enabled = !busy, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Photo, null, Modifier.size(18.dp))
                        Text(if (photoUri == null) "  Choisir dans la galerie" else "  Changer et recadrer")
                    }
                    if (photoUri != null) TextButton(enabled = !busy, onClick = { onPhotoChange(null) }) { Text("Retirer la photo") }
                }
            }
        }
        item { WapiEditorField(groupName, onNameChange, "Nom du groupe", 80, enabled = !busy) }
        item { WapiEditorSection("Membres · ${selectedIds.size}/64", "Choisissez au moins une personne. Vous serez administrateur du groupe.") }
        item {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Rechercher un contact") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(12.dp))
        }
        if (members.isEmpty()) {
            item { Text(if (contacts.isEmpty()) "Ajoutez un contact WAPI pour créer votre groupe." else "Aucun contact ne correspond à cette recherche.", color = WhappyMuted, fontSize = 14.sp) }
        }
        items(members, key = { it.member.uid }) { contact ->
            val selected = contact.member.uid in selectedIds
            val selectable = !busy && (selected || selectedIds.size < 64)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (selected) WhappyBlue.copy(alpha = .06f) else Color.Transparent)
                    .toggleable(value = selected, enabled = selectable, role = androidx.compose.ui.semantics.Role.Checkbox) { onToggle(contact.member.uid) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(contact.member.photoUrl, contact.member.displayName, 46.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(contact.member.displayName, color = WhappyInk, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (contact.member.phoneNumber.isNotBlank()) Text(contact.member.phoneNumber, color = WhappyMuted, fontSize = 13.sp)
                }
                Checkbox(checked = selected, onCheckedChange = null, enabled = selectable)
            }
        }
    }
    photoToCrop?.let { source ->
        WapiSquareCropDialog(source = source, title = "Recadrer la photo du groupe",
            onDismiss = { photoToCrop = null },
            onConfirm = { cropped -> onPhotoChange(cropped); photoToCrop = null })
    }
}

@Composable
private fun WapiPullDownRecentPanel(
    recentTabs: List<WhappyTab>,
    revealProgress: Float,
    modifier: Modifier = Modifier,
    onOpen: (WhappyTab) -> Unit,
    onClose: () -> Unit,
) {
    val shortcuts = wapiFeatureShortcuts()
    val defaults = shortcuts.filter { it.tab in listOf(WhappyTab.LIVE, WhappyTab.WEPI, WhappyTab.GAMES, WhappyTab.RADIO, WhappyTab.MARKET, WhappyTab.BUSINESS) }
    val recent = (recentTabs.mapNotNull { tab -> shortcuts.firstOrNull { it.tab == tab } } + defaults)
        .distinctBy { it.tab }
        .take(6)
    Column(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF071827), Color(0xFF0B2940))))
            .padding(horizontal = WapiMobile.screen, vertical = 13.dp)
            .graphicsLayer {
                alpha = (.28f + revealProgress * .72f).coerceIn(0f, 1f)
                scaleX = .96f + revealProgress * .04f
                scaleY = .96f + revealProgress * .04f
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Schedule, null, tint = WhappySky, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Récents WAPI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Reprenez instantanément là où vous étiez", color = Color.White.copy(alpha = .62f), fontSize = 9.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, "Refermer Récents", tint = Color.White.copy(alpha = .72f), modifier = Modifier.size(18.dp))
            }
        }
        recent.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f).height(67.dp).clip(RoundedCornerShape(17.dp)).wapiClickable { item.tab?.let(onOpen) },
                        color = Color.White.copy(alpha = .09f),
                        shape = RoundedCornerShape(17.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .10f)),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                                Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Text(item.title, Modifier.padding(start = 8.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.width(42.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .32f)))
            Text("Glissez vers le haut pour revenir aux Messages", color = Color.White.copy(alpha = .62f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun WapiContactMethodRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp).wapiClickable(enabled = enabled, onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .72f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (enabled) WhappyBlue else WhappyMuted, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = if (enabled) WhappyDark else WhappyMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = WhappyMuted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun WapiPullRecentsHint(progress: Float = 0f) {
    if (progress <= .03f) return
    val ready = progress >= .86f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            null,
            tint = if (ready) WhappyBlue else WhappyMuted,
            modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = if (ready) 180f else 0f },
        )
        Text(
            if (ready) "Ouverture de Récents…" else "Continuez pour ouvrir Récents",
            color = if (ready) WhappyBlue else WhappyMuted,
            fontSize = 10.sp,
            fontWeight = if (ready) FontWeight.Bold else FontWeight.SemiBold,
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
                Column(Modifier.padding(start = 12.dp)) { Text("CHAÎNES WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("Des publications utiles, sans bruit", color = Color.White, fontWeight = FontWeight.Bold); Text("${visible.size}/${channels.size} chaîne${if (channels.size > 1) "s" else ""} · filtres rapides", color = Color.White.copy(alpha = .68f), fontSize = 11.sp) }
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
            Card(Modifier.fillMaxWidth().wapiClickable { onOpen(channel) }, shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(if (subscribed) WhappyBlue else Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = if (subscribed) Color.White else WhappyBlue) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(channel.name, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp); if (channel.verified) Icon(Icons.Rounded.Verified, "Chaîne vérifiée", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(16.dp)) }
                            Text("${channel.category} · ${formatCompactCount(channel.memberCount)} abonnés", color = WhappyMuted, fontSize = 11.sp)
                        }
                        if (owner) Text("PROPRIÉTAIRE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        else OutlinedButton(onClick = { onSubscribe(channel.id, !subscribed) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text(if (subscribed) "Suivie" else "Suivre", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text(channel.description, color = WhappyInk, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (channel.lastPost.isNotBlank()) Text(channel.lastPost, Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color.White).padding(9.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${channel.postCount} publication${if (channel.postCount > 1) "s" else ""}", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Ouvrir ›", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            Column(Modifier.weight(1f).padding(start = 10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(channel.name, fontWeight = FontWeight.Bold, color = WhappyDark); if (channel.verified) Icon(Icons.Rounded.Verified, null, tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }; Text("${formatCompactCount(channel.memberCount)} abonnés · ${channel.postCount} publications", color = WhappyMuted, fontSize = 10.sp) }
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
                Card(Modifier.fillMaxWidth().wapiClickable(enabled = !post.deleted) { selectedPost = post }, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = if (post.pinned) Color.White else Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { if (post.pinned) Text("📌 ÉPINGLÉ", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(formatShortDate(post.createdAt) + " · " + formatTime(post.createdAt), color = WhappyMuted, fontSize = 9.sp) }
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
        AlertDialog(onDismissRequest = { selectedPost = null }, title = { Text("Publication", fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(post.text, color = WhappyMuted, maxLines = 4)
            if (subscribed || owner) { Text("Réagir", fontWeight = FontWeight.Bold); Row { listOf("❤️", "👍", "🔥", "👏", "💡").forEach { emoji -> TextButton(onClick = { onReact(post, emoji); selectedPost = null }, contentPadding = PaddingValues(7.dp)) { Text(emoji, fontSize = 20.sp) } } } }
            OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Publication WAPI", post.text)); selectedPost = null }, Modifier.fillMaxWidth()) { Text("Copier la publication") }
            if (owner) { OutlinedButton(onClick = { onPin(post); selectedPost = null }, Modifier.fillMaxWidth()) { Text(if (post.pinned) "Désépingler" else "Épingler en haut") }; OutlinedButton(onClick = { onDelete(post); selectedPost = null }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = WhappyBlue)) { Text("Supprimer") } }
        } }, confirmButton = { TextButton(onClick = { selectedPost = null }) { Text("Fermer") } })
    }
    if (showingChannelCode) {
        val qr = remember(channelLink) { createWhappyPayloadQr("whappy://channel/${channel.id}") }
        AlertDialog(onDismissRequest = { showingChannelCode = false }, title = { Text("Partager la chaîne", fontWeight = FontWeight.Bold) }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(qr.asImageBitmap(), "QR de la chaîne ${channel.name}", Modifier.size(210.dp).clip(RoundedCornerShape(18.dp)))
            Text(channel.name, fontWeight = FontWeight.Bold, color = WhappyDark)
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
                                    Text(page.name, fontWeight = FontWeight.Bold, color = WhappyDark)
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
internal fun ChatScreen(
    conversation: WhappyConversation,
    messages: List<WhappyMessage>,
    storyStatuses: List<WhappyStatus>,
    currentUserId: String,
    online: Boolean,
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
    onOpenMember: (WhappyMember) -> Unit,
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
    val bubbleMaxWidth = (LocalConfiguration.current.screenWidthDp.dp * .76f).coerceAtMost(360.dp)
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
    var showGroupMuteOptions by remember(conversation.id) { mutableStateOf(false) }
    var groupNotificationsMuted by rememberSaveable(conversation.id) {
        mutableStateOf(WhappyNotifications.isConversationMuted(context, conversation.id))
    }
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
        if (editingMessage != null) return // Editing a sent message must preserve the unsent draft.
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
        val wasEditing = editingMessage != null
        text = if (wasEditing) draftPrefs.getString(conversation.id, "").orEmpty() else ""
        replyTo = null
        editingMessage = null
        if (!wasEditing) draftPrefs.edit().remove(conversation.id).apply()
        onTyping(false)
    }

    fun startRecording() {
        wapiVoicePlayback.pauseCurrent()
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

    // Follow the bottom edge when IME/composer height changes, not only when a
    // new message arrives. Reading older messages must never trigger this.
    LaunchedEffect(conversation.id, listState, visibleMessages.size, searchQuery) {
        snapshotFlow { listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset }
            .distinctUntilChanged().collect { height ->
                if (height > 0 && followLatest && visibleMessages.isNotEmpty() && searchQuery.isBlank()) {
                    listState.scrollToItem(visibleMessages.lastIndex)
                    val layout = listState.layoutInfo
                    layout.visibleItemsInfo.lastOrNull()?.let { last ->
                        val overflow = last.offset + last.size - layout.viewportEndOffset + layout.afterContentPadding
                        if (overflow > 0) listState.scroll { scrollBy(overflow.toFloat()) }
                    }
                }
            }
    }

    Column(Modifier.fillMaxSize().background(WapiChatBackground).imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            if (conversation.isGroup && conversation.peer.photoUrl.isBlank()) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(WhappyAurora).wapiClickable { showPeerProfile = true }, contentAlignment = Alignment.Center) {
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
            Column(Modifier.weight(1f).padding(start = 10.dp).wapiClickable { showPeerProfile = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.peer.displayName, fontWeight = FontWeight.SemiBold, color = WhappyDark, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!conversation.isGroup && (conversation.peer.verified || WhappyIdentity.isFounder(conversation.peer.phoneNumber))) {
                        Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 4.dp).size(15.dp))
                    }
                    if (conversation.profileType == "business") {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = WhappyBlue.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) { Text("BUSINESS", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = WhappyBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
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
                modifier = Modifier.fillMaxWidth().wapiClickable { showRadioPicker = true },
                color = WhappyBlue.copy(alpha = .07f),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .13f)),
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(episode.stationName, color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(episode.title, color = WhappyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (radioState.preparing) "Connexion…" else if (radioState.playing) "EN LECTURE" else "PAUSE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        if (pendingCount > 0 && !online) {
            Row(
                Modifier.fillMaxWidth().background(WhappyBlue.copy(alpha = .06f)).padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = WhappyBlue, modifier = Modifier.size(17.dp))
                Text(
                    "Connexion en attente",
                    Modifier.weight(1f).padding(horizontal = 9.dp),
                    color = WhappyBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onRetryPending) { Text("Réessayer") }
            }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else Box(Modifier.weight(1f).fillMaxWidth().background(chatWallpaperBrush(wallpaper))) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            val senderPhoto = senderMember?.photoUrl?.takeIf(String::isNotBlank) ?: message.senderPhotoUrl
                            UserAvatar(
                                senderPhoto,
                                senderName,
                                30.dp,
                                Modifier.padding(top = 3.dp).wapiClickable {
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
                            modifier = Modifier.widthIn(max = bubbleMaxWidth).pointerInput(message.id, message.deleted, message.deliveryState) {
                                    detectTapGestures(onLongPress = { if (message.kind != "system" && !message.deleted && message.deliveryState == "sent") selectedMessage = message })
                        }) {
                            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
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
            ) { Text(if (unseenWhileReading > 0) "↓ $unseenWhileReading" else "↓", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        }
        if (showEmoji) EmojiTray(onEmoji = { updateDraft(text + it) }, onClose = { showEmoji = false })
        if (showWallpaperPicker) {
            AlertDialog(
                onDismissRequest = { showWallpaperPicker = false },
                title = { Text("Fond de conversation", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choisissez un fond léger, fixé pendant le défilement des messages.", color = WhappyMuted, fontSize = 12.sp)
                        chatWallpaperNames.forEach { (key, name) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(chatWallpaperBrush(key)).wapiClickable {
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
                title = { Text("Radios & podcasts", color = WhappyDark, fontWeight = FontWeight.Bold) },
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
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).wapiClickable { radio?.play(episode); showRadioPicker = false },
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
        replyTo?.let { message -> Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = WhappyBlue, modifier = Modifier.size(17.dp)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text("Répondre", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(message.text.ifBlank { message.mediaName.ifBlank { "Média" } }, color = WhappyDark, fontSize = 11.sp, maxLines = 1) }; TextButton(onClick = { replyTo = null }) { Text("Annuler") } } }
        editingMessage?.let { message -> Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.MoreVert, null, tint = WhappyBlue, modifier = Modifier.size(17.dp)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text("Modifier le message", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(message.text, color = WhappyDark, fontSize = 11.sp, maxLines = 1) }; TextButton(onClick = { editingMessage = null; text = draftPrefs.getString(conversation.id, "").orEmpty() }) { Text("Annuler") } } }
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
        Row(Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 5.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
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
                keyboardActions = KeyboardActions(onSend = { submitText() }),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wapiClickable { showMore = false; mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Photo, "Photos et vidéos", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Album", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wapiClickable { showMore = false; offerOpen = true }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, "Faire une offre", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Offre", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wapiClickable { showMore = false; memePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, "Créer un mème", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Mème", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wapiClickable { showMore = false; documentPicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain")) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AttachFile, "Document original", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Waphsare", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.wapiClickable { nextMediaViewOnce = !nextMediaViewOnce; WhappySounds.haptic(context) }) {
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
                    Text("WAPI ACTION · OFFRE SÉCURISÉE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("Faire une offre", color = WhappyDark, fontSize = 27.sp, fontWeight = FontWeight.Bold)
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
                        modifier = Modifier.fillMaxWidth().wapiClickable(enabled = !sending) { offerMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
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
                ) { Text("Envoyer l’offre  ↗", fontWeight = FontWeight.Bold) }
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
            title = { Text("Atelier de mèmes", color = WhappyDark, fontWeight = FontWeight.Bold) },
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
        val peerIsVerified = conversation.peer.verified || WhappyIdentity.isFounder(conversation.peer.phoneNumber)
        val peerCallable = calls != null && conversation.peer.uid.isNotBlank() && conversation.peer.uid != currentUserId
        Dialog(
            onDismissRequest = { showPeerProfile = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(.94f).heightIn(max = 760.dp),
                shape = RoundedCornerShape(30.dp),
                color = WapiSheet,
                shadowElevation = 24.dp,
            ) {
                Column(
                ) {
                    Box(Modifier.fillMaxWidth().background(WhappyAuroraSoft).padding(horizontal = 20.dp, vertical = 22.dp)) {
                        Box(Modifier.align(Alignment.TopEnd).size(112.dp).offset(x = 38.dp, y = (-46).dp).clip(CircleShape).background(WhappySky.copy(alpha = .12f)))
                        IconButton(
                            onClick = { showPeerProfile = false },
                            modifier = Modifier.align(Alignment.TopEnd).size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .88f)),
                        ) { Icon(Icons.Rounded.Close, "Fermer le profil", tint = WhappyDark) }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(25.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(3.dp, WhappyBlue.copy(alpha = .18f)),
                                shadowElevation = 10.dp,
                            ) {
                                UserAvatar(
                                    conversation.peer.photoUrl,
                                    conversation.peer.displayName,
                                    104.dp,
                                    Modifier.wapiClickable(enabled = conversation.peer.photoUrl.isNotBlank()) {
                                        previewImage = conversation.peer.photoUrl
                                        showPeerProfile = false
                                    },
                                    RoundedCornerShape(22.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 14.dp, start = 42.dp, end = 42.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    conversation.peer.displayName.ifBlank { "Compte WAPI" },
                                    color = WhappyDark,
                                    fontSize = 24.sp,
                                    lineHeight = 29.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (peerIsVerified) Icon(
                                    Icons.Rounded.Verified,
                                    "Compte certifié",
                                    tint = WapiVerifiedGray,
                                    modifier = Modifier.padding(start = 6.dp).size(17.dp),
                                )
                            }
                            Text(
                                if (conversation.isGroup) "Groupe · ${conversation.memberCount} membres"
                                else if (WhappyIdentity.isFounder(conversation.peer.phoneNumber)) WhappyIdentity.founderBadgeLabel
                                else if (conversation.peer.isOnline) "● En ligne maintenant"
                                else if (conversation.peer.lastSeenAt > 0L) formatLastSeen(conversation.peer.lastSeenAt)
                                else "Présence privée",
                                Modifier.padding(top = 6.dp),
                                color = if (!conversation.isGroup && conversation.peer.isOnline) WapiSuccess else WhappyMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                            if (!conversation.isGroup) {
                                Row(Modifier.fillMaxWidth().padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    WapiProfileAction(Icons.Rounded.ChatBubble, "Message", true, Modifier.weight(1f), emphasized = true) { showPeerProfile = false }
                                    WapiProfileAction(Icons.Rounded.Phone, "Audio", peerCallable, Modifier.weight(1f)) {
                                        calls?.start(conversation.peer, false)
                                        showPeerProfile = false
                                    }
                                    WapiProfileAction(Icons.Rounded.Videocam, "Vidéo", peerCallable, Modifier.weight(1f)) {
                                        calls?.start(conversation.peer, true)
                                        showPeerProfile = false
                                    }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    if (conversation.peer.phoneNumber.isNotBlank()) Text(conversation.peer.phoneNumber, color = WhappyDark, fontWeight = FontWeight.SemiBold)
                    Text(if (conversation.isGroup) "Ouvrez les informations du groupe, ses membres et ses médias depuis cette fiche." else "Photo, identité et moyens de contact de ce compte.", color = WhappyMuted, lineHeight = 19.sp)
                    if (!conversation.isGroup) {
                        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .70f))) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                WapiProfileInfoRow(Icons.Rounded.Phone, "Numéro", conversation.peer.phoneNumber.ifBlank { "Protégé par la confidentialité" })
                                WapiProfileInfoRow(Icons.Rounded.Person, "Identifiant WAPI", conversation.peer.uid.ifBlank { "En cours de synchronisation" })
                                WapiProfileInfoRow(Icons.Rounded.Lock, "Confidentialité", "Seules les informations autorisées par ce compte sont affichées")
                            }
                        }
                    }
                    if (conversation.isGroup) {
                        if (conversation.groupDescription.isNotBlank()) {
                            Text(conversation.groupDescription, color = WhappyDark, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Surface(color = WhappyBlue.copy(alpha = .06f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("MEMBRES", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                    Text("${conversation.memberCount}", color = WhappyDark, fontWeight = FontWeight.Bold)
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
                        OutlinedButton(
                            onClick = {
                                if (groupNotificationsMuted) {
                                    WhappyNotifications.setConversationMuted(context, conversation.id, false)
                                    groupNotificationsMuted = false
                                } else {
                                    showPeerProfile = false
                                    showGroupMuteOptions = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Notifications, null)
                            Text(if (groupNotificationsMuted) "  Réactiver les notifications" else "  Mettre le groupe en silencieux")
                        }
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
                        OutlinedButton(
                            onClick = {
                                val transcript = buildString {
                                    append("Discussion WAPI · ${conversation.peer.displayName}\n")
                                    append("Exportée le ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())}\n\n")
                                    messages.filterNot { it.deleted }.takeLast(250).forEach { message ->
                                        append("[${formatTime(message.createdAt)}] ${message.senderName.ifBlank { if (message.senderId == currentUserId) "Vous" else "Membre WAPI" }} : ")
                                        append(message.text.ifBlank { message.mediaName.ifBlank { "Pièce jointe ${message.kind}" } })
                                        append('\n')
                                    }
                                }
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Discussion ${conversation.peer.displayName}")
                                    putExtra(Intent.EXTRA_TEXT, transcript)
                                }, "Exporter la discussion"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Text("  Exporter les 250 derniers messages")
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
                        OutlinedButton(
                            onClick = { showPeerProfile = false; groupCallInvitation = null; groupCallRadio = true; groupCallVideo = false; groupCallOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Radio, null)
                            Text("  Ouvrir une session radio du groupe")
                        }
                    }
                    if (!conversation.isGroup) {
                        Text("Activité publique", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val peerEpisodes = publicRadioEpisodes.filter { it.ownerId == conversation.peer.uid }
                        if (peerEpisodes.isEmpty()) Text("Aucune radio ni playlist publique partagée.", color = WhappyMuted, fontSize = 11.sp)
                        else peerEpisodes.take(4).forEach { episode ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().wapiClickable { radio?.play(episode) },
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
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPeerProfile = false }) { Text("Fermer", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
    selectedGroupMember?.let { member ->
        val memberRadio = publicRadioEpisodes.filter { it.ownerId == member.uid }
        val memberCallable = calls != null && member.uid.isNotBlank() && member.uid != currentUserId
        val memberVerified = member.verified || WhappyIdentity.isFounder(member.phoneNumber)
        Dialog(
            onDismissRequest = { selectedGroupMember = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxWidth(.94f).heightIn(max = 700.dp), shape = RoundedCornerShape(30.dp), color = WapiSheet, shadowElevation = 22.dp) {
                Column {
                    Box(Modifier.fillMaxWidth().background(WhappyAuroraSoft).padding(20.dp)) {
                        IconButton(onClick = { selectedGroupMember = null }, modifier = Modifier.align(Alignment.TopEnd).size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .88f))) {
                            Icon(Icons.Rounded.Close, "Fermer le profil", tint = WhappyDark)
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(23.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(3.dp, WhappyBlue.copy(alpha = .18f)), shadowElevation = 9.dp) {
                                UserAvatar(
                                    member.photoUrl,
                                    member.displayName,
                                    96.dp,
                                    Modifier.wapiClickable(enabled = member.photoUrl.isNotBlank()) {
                                        previewImage = member.photoUrl
                                        selectedGroupMember = null
                                    },
                                    RoundedCornerShape(20.dp),
                                )
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 13.dp, start = 40.dp, end = 40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(member.displayName.ifBlank { "Membre WAPI" }, color = WhappyDark, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                if (memberVerified) Icon(Icons.Rounded.Verified, "Compte certifié", tint = WapiVerifiedGray, modifier = Modifier.padding(start = 6.dp).size(17.dp))
                            }
                            Text(
                                if (member.uid in conversation.groupAdminIds) "Administrateur du groupe" else "Membre du groupe",
                                Modifier.padding(top = 5.dp),
                                color = WhappyMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                WapiProfileAction(Icons.Rounded.ChatBubble, "Message", member.uid.isNotBlank(), Modifier.weight(1f), emphasized = true) {
                                    selectedGroupMember = null
                                    onOpenMember(member)
                                }
                                WapiProfileAction(Icons.Rounded.Phone, "Audio", memberCallable, Modifier.weight(1f)) {
                                    calls?.start(member, false)
                                    selectedGroupMember = null
                                }
                                WapiProfileAction(Icons.Rounded.Videocam, "Vidéo", memberCallable, Modifier.weight(1f)) {
                                    calls?.start(member, true)
                                    selectedGroupMember = null
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .70f))) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                WapiProfileInfoRow(Icons.Rounded.Phone, "Numéro", member.phoneNumber.ifBlank { "Protégé par la confidentialité" })
                                WapiProfileInfoRow(Icons.Rounded.Person, "Identifiant WAPI", member.uid.ifBlank { "En cours de synchronisation" })
                            }
                        }
                        Text("Activité publique", color = WhappyDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (memberRadio.isEmpty()) Text("Aucune radio ni playlist publique partagée.", color = WhappyMuted, fontSize = 11.sp)
                        else memberRadio.take(3).forEach { episode ->
                            Surface(Modifier.fillMaxWidth().wapiClickable { radio?.play(episode) }, color = WapiBlueMist, shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Radio, null, tint = WhappyBlue)
                                    Text(episode.title, Modifier.weight(1f).padding(start = 10.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Rounded.PlayArrow, "Écouter", tint = WhappyBlue)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selectedGroupMember = null }) { Text("Fermer", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
    if (showGroupMuteOptions && conversation.isGroup) {
        AlertDialog(
            onDismissRequest = { showGroupMuteOptions = false },
            icon = { Icon(Icons.Rounded.Notifications, null, tint = WhappyBlue) },
            title = { Text("Notifications du groupe", color = WhappyDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choisissez une durée. Les messages restent disponibles et le badge du groupe reprend automatiquement à la fin.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    listOf(
                        "Pendant 8 heures" to 8L * 60L * 60L * 1_000L,
                        "Pendant 7 jours" to 7L * 24L * 60L * 60L * 1_000L,
                    ).forEach { option ->
                        OutlinedButton(
                            onClick = {
                                WhappyNotifications.muteConversationFor(context, conversation.id, option.second)
                                groupNotificationsMuted = true
                                showGroupMuteOptions = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(option.first) }
                    }
                    Button(
                        onClick = {
                            WhappyNotifications.muteConversationFor(context, conversation.id, null)
                            groupNotificationsMuted = true
                            showGroupMuteOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Jusqu’à réactivation") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGroupMuteOptions = false }) { Text("Annuler") } },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
    if (showGroupMembers && conversation.isGroup) {
        AlertDialog(
            onDismissRequest = { showGroupMembers = false },
            icon = { UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 58.dp, shape = RoundedCornerShape(14.dp)) },
            title = { Text("${conversation.memberCount} membres", color = WhappyDark, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(conversation.groupMembers, key = { "group-profile-${it.uid}" }) { member ->
                        val owner = member.uid == conversation.groupOwnerId
                        val administrator = owner || member.uid in conversation.groupAdminIds
                        Surface(
                            modifier = Modifier.fillMaxWidth().wapiClickable { showGroupMembers = false; selectedGroupMember = member },
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
            title = { Text(if (groupMemberAction == "add") "Ajouter des membres" else "Retirer des membres", color = WhappyDark, fontWeight = FontWeight.Bold) },
            text = {
                if (candidates.isEmpty()) Text(if (groupMemberAction == "add") "Tous vos contacts WAPI sont déjà dans ce groupe." else "Aucun membre ne peut être retiré.", color = WhappyMuted)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(candidates, key = { "manage-${groupMemberAction}-${it.uid}" }) { member ->
                        val selected = member.uid in selectedManagedMembers
                        Surface(
                            modifier = Modifier.fillMaxWidth().wapiClickable(enabled = !groupBusy) { selectedManagedMembers = if (selected) selectedManagedMembers - member.uid else selectedManagedMembers + member.uid },
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
            title = { Text("Permissions du groupe", color = WhappyDark, fontWeight = FontWeight.Bold) },
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
            title = { Text("Médias et documents", color = WhappyDark, fontWeight = FontWeight.Bold) },
            text = {
                if (sharedItems.isEmpty()) Text("Aucun média partagé dans ce groupe.", color = WhappyMuted)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sharedItems, key = { "shared-${it.id}" }) { message ->
                        Surface(Modifier.fillMaxWidth().wapiClickable { openWapiMedia(message) }, color = WhappyBlue.copy(alpha = .05f), shape = RoundedCornerShape(14.dp)) {
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
            title = { Text("Informations du groupe", color = WhappyDark, fontWeight = FontWeight.Bold) },
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
            title = { Text("Administrateurs du groupe", color = WhappyDark, fontWeight = FontWeight.Bold) },
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
                            WapiMessageAction("↩", "Répondre", Modifier.weight(1f)) { if (editingMessage != null) text = draftPrefs.getString(conversation.id, "").orEmpty(); replyTo = message; editingMessage = null; selectedMessage = null }
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
            title = { Text(t("Lingwap", "Lingwap", "Lingwap"), color = WhappyDark, fontWeight = FontWeight.Bold) },
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
        modifier.height(67.dp).wapiClickable(enabled = enabled, onClick = onClick),
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).wapiClickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        modifier = Modifier.fillMaxWidth().wapiClickable(enabled = source.isNotBlank(), onClick = onOpen),
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
                if (sha256.length == 64) Text("Intégrité vérifiable", Modifier.padding(top = 2.dp), color = if (mine) Color.White.copy(alpha = .9f) else WapiChatAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ViewOnceMediaRow(kind: String, mine: Boolean, opened: Boolean, onOpen: (() -> Unit)?) {
    val foreground = if (mine) Color.White else WhappyInk
    val muted = if (mine) Color.White.copy(alpha = .76f) else WhappyMuted
    Surface(
        modifier = Modifier.fillMaxWidth().wapiClickable(enabled = onOpen != null) { onOpen?.invoke() },
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
            if (!opened && !mine) Text("OUVRIR", color = if (mine) Color.White else WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            else Text(if (kind == "audio") "AUDIO" else if (kind == "video") "VIDÉO" else "PHOTO", color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
internal fun VoiceNoteMessage(
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
    var playWhenReady by remember(source) { mutableStateOf(false) }
    val playbackOwner = remember(source) { Any() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioFocus = remember(source) { WapiVoiceAudioFocus(context) }
    val totalMillis = (actualDurationMillis.takeIf { it > 0 } ?: durationSeconds.coerceAtLeast(1) * 1_000).coerceAtLeast(1_000)
    val liveProgress = (positionMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    val displayedProgress = scrubFraction ?: liveProgress
    val displayedPositionMillis = (displayedProgress * totalMillis).toInt().coerceIn(0, totalMillis)
    val foreground = if (mine) Color.White else WhappyDark
    val secondary = if (mine) Color.White.copy(alpha = .76f) else WhappyMuted
    val controlBackground = if (mine) Color.White.copy(alpha = .19f) else WhappyBlue.copy(alpha = .1f)

    fun pausePlayback() {
        playWhenReady = false
        runCatching { if (playerPrepared && player?.isPlaying == true) player?.pause() }
        playing = false
        wapiVoicePlayback.release(playbackOwner)
        audioFocus.release()
    }
    audioFocus.onLoss = ::pausePlayback

    fun applySpeed(target: MediaPlayer, speed: Float) {
        // A non-zero PlaybackParams speed starts MediaPlayer. Never call this
        // while merely preparing, seeking a paused note or selecting a speed.
        runCatching {
            target.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
        }
    }

    fun startPrepared(target: MediaPlayer) {
        if (!playWhenReady || !wapiVoicePlayback.owns(playbackOwner)) return
        runCatching { applySpeed(target, playbackSpeed); target.start() }
            .onSuccess { playing = true; playbackError = false }
            .onFailure { pausePlayback(); playbackError = true }
    }

    fun preparePlayback(initialSeekFraction: Float? = null) {
        if (preparing || playerPrepared) return
        val uri = voiceMessageUri(source) ?: run {
            pausePlayback()
            playbackError = true
            return
        }
        preparing = true
        playbackError = false
        var candidate: MediaPlayer? = null
        runCatching {
            @Suppress("DEPRECATION")
            MediaPlayer().apply {
                candidate = this
                player = this
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(context, uri)
                setOnPreparedListener { prepared ->
                    if (player !== prepared) return@setOnPreparedListener
                    actualDurationMillis = prepared.duration.coerceAtLeast(1_000)
                    playerPrepared = true
                    preparing = false
                    initialSeekFraction?.let { fraction ->
                        val target = (prepared.duration * fraction.coerceIn(0f, 1f)).toInt()
                        prepared.seekTo(target)
                        positionMillis = target
                    }
                    startPrepared(prepared)
                }
                setOnSeekCompleteListener { sought ->
                    positionMillis = runCatching { sought.currentPosition }.getOrDefault(positionMillis)
                }
                setOnCompletionListener { completed ->
                    pausePlayback()
                    positionMillis = runCatching { completed.duration }.getOrDefault(actualDurationMillis).coerceAtLeast(0)
                    scrubFraction = null
                }
                setOnErrorListener { failed, _, _ ->
                    if (player !== failed) return@setOnErrorListener true
                    pausePlayback()
                    preparing = false
                    playerPrepared = false
                    playbackError = true
                    if (player === failed) player = null
                    failed.release()
                    true
                }
                prepareAsync()
            }
        }.onFailure {
                candidate?.let { runCatching { it.release() } }
                player = null
                pausePlayback()
                preparing = false
                playerPrepared = false
                playbackError = true
            }
    }

    fun togglePlayback() {
        if (playWhenReady || playing) { pausePlayback(); return }
        playWhenReady = true
        wapiVoicePlayback.claim(playbackOwner, ::pausePlayback)
        if (!audioFocus.acquire()) { pausePlayback(); playbackError = true; return }
        val current = player
        if (current != null && playerPrepared) {
            if (positionMillis >= totalMillis - 50) { current.seekTo(0); positionMillis = 0 }
            startPrepared(current)
            return
        }
        preparePlayback()
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
            preparePlayback(initialSeekFraction = safeFraction)
        }
    }

    fun cyclePlaybackSpeed() {
        val next = when (playbackSpeed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        playbackSpeed = next
        player?.takeIf { playerPrepared && playing }?.let { applySpeed(it, next) }
    }

    LaunchedEffect(playing, player) {
        while (playing) {
            positionMillis = runCatching { player?.currentPosition ?: 0 }.getOrDefault(positionMillis)
            delay(150)
        }
    }
    DisposableEffect(source, lifecycleOwner) {
        val pauseForThisSource = ::pausePlayback
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) pauseForThisSource()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pauseForThisSource()
            audioFocus.onLoss = {}
            runCatching { player?.release() }
            player = null
        }
    }

    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(controlBackground).padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = ::togglePlayback,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (mine) Color.White.copy(alpha = .19f) else WhappyBlue, contentColor = Color.White),
        ) {
            if (preparing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Mettre en pause · $title" else "Lire · $title", modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (playbackError) "Lecture indisponible" else title, color = foreground, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Surface(
                    color = if (mine) Color.White.copy(alpha = .17f) else WhappyBlue.copy(alpha = .12f),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).wapiClickable(enabled = !preparing, onClick = ::cyclePlaybackSpeed),
                ) {
                    Text(
                        when (playbackSpeed) { 1.5f -> "1,5×"; 2f -> "2×"; else -> "1×" },
                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        color = foreground,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
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
                "${formatVoiceDuration(displayedPositionMillis / 1_000)} / ${formatVoiceDuration(totalMillis / 1_000)}",
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
    var retry by remember(source) { mutableIntStateOf(0) }
    LaunchedEffect(source, retry) {
        if (bitmap != null) return@LaunchedEffect
        loading = source.isNotBlank()
        bitmap = WapiStableImageLoader.load(context, source)
        loading = false
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(if (mine) Color.White.copy(alpha = .35f) else WhappySurface).wapiClickable(onClick = { if (bitmap != null) onOpen() else if (!loading) retry++ }),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap!!, contentDescription = label, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
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
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 140.dp, max = 330.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface).wapiClickable(onClick = onOpen), contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(bitmap!!, contentDescription = label.ifBlank { "Image Story" }, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                loading -> CircularProgressIndicator(color = WhappyBlue, strokeWidth = 2.dp)
                else -> Text("Image Story indisponible · toucher pour ouvrir", color = WhappyMuted, fontSize = 11.sp)
            }
        }
    } else {
        Surface(Modifier.fillMaxWidth().padding(top = 12.dp).wapiClickable(onClick = onOpen), color = WhappyBlue.copy(alpha = .06f), shape = RoundedCornerShape(15.dp)) {
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
                failed -> Text("Impossible de charger l’image", color = Color.White, fontWeight = FontWeight.Bold)
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
            Text("EMOJIS", Modifier.weight(1f), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    activeBusinessPageId: String,
    onPublish: (String, String, String, String, String, String, Uri?) -> Unit,
    onOpenBusiness: () -> Unit,
    onContactBusiness: (WhappyBusinessPage) -> Unit,
    onRespond: (WhappyListing, String) -> Unit,
    onPrepareBoost: (WhappyListing) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_consumer") }
    var search by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var localItems by remember { mutableStateOf(emptyList<WhappyListing>()) }
    var selected by remember { mutableStateOf<WhappyListing?>(null) }
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

    fun saveCart(next: Map<String, Int>) {
        cart = next.filterValues { it > 0 }
        prefs.edit().putString("market_cart", cart.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marketplace", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = WhappyDark)
                    Text("Boutiques, menus et événements", color = WhappyMuted, fontSize = 13.sp)
                }
                IconButton(onClick = { showingOrders = true }) { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, "Mes listes") }
                IconButton(onClick = { showingCart = true }) { Icon(Icons.Rounded.ShoppingCart, "Ma sélection") }
            }
        }
        item { WapiCommerceLaunchers(onContactBusiness) }
        item {
            Button(
                onClick = {
                    if (preview || activeBusinessPageId.isNotBlank()) creating = true
                    else { marketFeedback = "Créez et activez d’abord votre compte Business : les annonces restent visibles pour tous, mais seules les pages Business peuvent publier."; onOpenBusiness() }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Rounded.Add, null); Text(" Publier avec mon Business") }
        }
        item { WapiPublicSaleRoomsRail() }
        item { Text("Annonces WAPI", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        marketFeedback?.let { value -> item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Text(value, Modifier.padding(13.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Rechercher un produit ou une boutique") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true) }
        if (products.isEmpty()) item { EmptyState("Aucune annonce", "Publiez la première offre de cette catégorie.") }
        items(products, key = { it.id }) { product ->
            Card(Modifier.wapiClickable { selected = product }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(product.photoUrls.firstOrNull().orEmpty(), product.title, 76.dp, shape = RoundedCornerShape(18.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(product.title, fontWeight = FontWeight.Bold, color = WhappyDark); Text(product.price, Modifier.padding(top = 5.dp), color = WhappyBlue, fontWeight = FontWeight.Bold); Text("${product.place} · ${product.seller}", Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp); if(product.mode=="trade") Text("TROC ACCEPTÉ", Modifier.padding(top=5.dp), color=WhappyBlue, fontSize=9.sp, fontWeight=FontWeight.Bold); if (product.boostStatus == "active") Text("SPONSORISÉ", Modifier.padding(top = 4.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { savedIds = if (product.id in savedIds) savedIds - product.id else savedIds + product.id; prefs.edit().putStringSet("market_favorites", savedIds).apply() }) { Icon(if (product.id in savedIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", tint = if (product.id in savedIds) WhappyBlue else WhappyMuted) }
                }
            }
        }
    }
    if (creating) ListingDialog(busy, onDismiss = { creating = false }) { title, price, place, mode, description, category, photoUri ->
            if (preview) localItems = listOf(WhappyListing("local-${System.currentTimeMillis()}", title, price.ifBlank { "Prix à discuter" }, place.ifBlank { "Brazzaville" }, accountDisplayName, "demo-user", mode, description, category)) + localItems
        else onPublish(title, price, place, mode, description, category, photoUri)
        creating = false
    }
    selected?.let { product ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(product.title, fontWeight = FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { UserAvatar(product.photoUrls.firstOrNull().orEmpty(), product.title, 150.dp, shape = RoundedCornerShape(22.dp)); Text(product.price, color = WhappyBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Publié par ${product.seller} · ${product.place}", color = WhappyMuted); if (product.description.isNotBlank()) Text(product.description, color = WhappyDark); Text(if (product.mode == "trade") "Cette annonce accepte les propositions d’échange." else "Contactez le Business avant toute transaction. Les paiements Mobile Money seront proposés lorsque le moyen régional sera configuré.", color = WhappyDark) } },
            confirmButton = { Button(enabled = !busy, onClick = { val kind = when (product.mode) { "trade" -> "trade"; "auction" -> "bid"; "job" -> "apply"; "service" -> "message"; else -> "offer" }; onRespond(product, kind); marketFeedback = when (kind) { "trade" -> "Votre proposition de troc est envoyée au Business."; "bid" -> "Votre intention d’enchérir est envoyée au Business."; "apply" -> "Votre candidature est envoyée au Business."; else -> "Votre demande est envoyée au Business." }; selected = null }) { Icon(Icons.AutoMirrored.Rounded.Send, null); Text(when (product.mode) { "trade" -> " Proposer un troc"; "auction" -> " Enchérir"; "job" -> " Postuler"; "service" -> " Discuter"; else -> " Faire une offre" }) } },
            dismissButton = { Row { TextButton(onClick = { savedIds = if (product.id in savedIds) savedIds - product.id else savedIds + product.id; prefs.edit().putStringSet("market_favorites", savedIds).apply() }) { Text(if (product.id in savedIds) "Retirer l’étoile" else "Étoile") }; if (product.businessPageId == activeBusinessPageId) TextButton(enabled = !busy, onClick = { onPrepareBoost(product); marketFeedback = "Plan de boost préparé. Configurez ensuite Mobile Money pour l’activer."; selected = null }) { Text("Booster") }; TextButton(onClick = { selected = null }) { Text("Fermer") } } },
        )
    }
    if (showingCart) {
        val allProducts = localItems + listings
        AlertDialog(
            onDismissRequest = { showingCart = false },
            title = { Text("Ma sélection · ${cart.values.sum()} article(s)", fontWeight = FontWeight.Bold) },
            text = { LazyColumn(Modifier.fillMaxWidth().height(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cart.isEmpty()) item { Text("Votre panier est vide. Ouvrez une annonce pour ajouter un article.", color = WhappyMuted) }
                cart.forEach { (id, quantity) -> val product = allProducts.firstOrNull { it.id == id }; if (product != null) item(key = "cart-$id") { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(product.title, fontWeight = FontWeight.Bold, color = WhappyDark, maxLines = 2); Text(product.price, color = WhappyBlue, fontSize = 11.sp) }; TextButton(onClick = { saveCart(cart + (id to quantity - 1)) }) { Text("−") }; Text(quantity.toString(), fontWeight = FontWeight.Bold); TextButton(onClick = { saveCart(cart + (id to (quantity + 1).coerceAtMost(9))) }) { Text("+") } } } } }
                if (cart.isNotEmpty()) item { OutlinedTextField(delivery, { delivery = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("Adresse ou point de rendez-vous") }, minLines = 2) }
            } },
            confirmButton = { Button(enabled = cart.isNotEmpty() && delivery.trim().length >= 5, onClick = { val reference = "WH-${UUID.randomUUID().toString().take(6).uppercase()}"; val titles = cart.mapNotNull { (id, quantity) -> allProducts.firstOrNull { it.id == id }?.let { "$quantity × ${it.title.replace("|", " ")}" } }.joinToString(", "); val entry = "${System.currentTimeMillis()}|$reference|$titles · ${delivery.trim().replace("|", " ")}"; orders = (listOf(entry) + orders).take(30); prefs.edit().putStringSet("market_orders", orders.toSet()).apply(); saveCart(emptyMap()); delivery = ""; showingCart = false; marketFeedback = "Sélection enregistrée sur cet appareil. Elle n’a pas été transmise au vendeur." }) { Text("Enregistrer la sélection") } },
            dismissButton = { TextButton(onClick = { showingCart = false }) { Text("Fermer") } },
        )
    }
    if (showingOrders) AlertDialog(
        onDismissRequest = { showingOrders = false },
        title = { Text("Mes sélections locales", fontWeight = FontWeight.Bold) },
        text = { LazyColumn(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (orders.isEmpty()) item { Text("Aucune sélection enregistrée sur cet appareil.", color = WhappyMuted) }; items(orders, key = { it }) { raw -> val parts = raw.split("|", limit = 3); Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(13.dp)) { Row { Text(parts.getOrElse(1) { "Commande" }, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Bold); Text("Non transmise", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text(parts.getOrElse(2) { "" }, Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp) } } } } },
        confirmButton = { TextButton(onClick = { showingOrders = false }) { Text("Fermer") } },
    )
}

@Composable
private fun ListingDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String, Uri?) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Autre") }
    var mode by rememberSaveable { mutableStateOf("sale") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cropUri = uri }
    WapiEditorScreen("Nouvelle annonce", "Publier l’annonce", busy, title.trim().length >= 2, onDismiss,
        onSubmit = { onSave(title.trim(), price.trim(), place.trim(), mode, description.trim(), category.trim(), photoUri) },
    ) {
        item { WapiEditorSection("Annonce Business", "Une publication est liée à votre page Business. Ajoutez une photo, une description et le type d’action attendu.") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(photoUri?.toString().orEmpty(), title.ifBlank { "Annonce" }, 64.dp, shape = RoundedCornerShape(16.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (photoUri == null) "Ajoutez une photo de galerie" else "Photo prête à publier", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Photo carrée, optimisée avant envoi", color = WhappyMuted, fontSize = 10.sp)
                }
                OutlinedButton(enabled = !busy, onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galerie") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sale" to "Vendre", "trade" to "Troc", "auction" to "Enchère", "job" to "Emploi", "service" to "Service").forEach { (value, label) ->
                    FilterChip(selected = mode == value, enabled = !busy, onClick = { mode = value }, label = { Text(label) })
                }
            }
        }
        item { WapiEditorField(title, { title = it }, "Titre de l’annonce", 120, enabled = !busy) }
        item { WapiEditorField(description, { description = it }, "Description", 1200, enabled = !busy, multiline = true) }
        item { WapiEditorField(category, { category = it }, "Catégorie", 60, enabled = !busy) }
        item { WapiEditorField(price, { price = it }, if (mode == "trade") "Échange souhaité" else if (mode == "job") "Contrat ou rémunération (facultatif)" else "Prix, base d’enchère ou devise", 120, enabled = !busy) }
        item { WapiEditorField(place, { place = it }, "Lieu", 120, enabled = !busy) }
    }
    cropUri?.let { source -> WapiSquareCropDialog(source, "Recadrer la photo de l’annonce", { cropUri = null }) { cropped -> photoUri = cropped; cropUri = null } }
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
                    Text("En direct", color = WhappyDark, fontSize = 25.sp, fontWeight = FontWeight.Bold)
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
            Card(Modifier.fillMaxWidth().wapiClickable(onClick = onOpenTwin), shape = RoundedCornerShape(WapiMobile.compactRadius), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(WapiMobile.row), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t("OPTION CRÉATIVE · FACULTATIVE", "OPTIONAL CREATIVE TOOL", "OPTION YA CRÉATION"), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(t("Animer avec Mon WAPI", "Animate with My WAPI", "Sala animation na Mon WAPI"), color = WhappyDark, fontWeight = FontWeight.Bold) }
                    Text("›", color = WhappyBlue, fontSize = 25.sp)
                }
            }
        }
        item { Text("En direct maintenant", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhappyDark) }
        if (visibleLives.isEmpty()) item { EmptyState("Aucun direct en cours", "Votre direct apparaîtra ici dès que la caméra est connectée.") }
        items(visibleLives, key = { it.id }) { live ->
            val liveReady = live.streamProvider == "wapi-webrtc-p2p"
            Card(Modifier.fillMaxWidth().wapiClickable { if (live.hostId == currentUserId && live.status != "live") startWithPermissions(!live.audioOnly) { selectedLive = live } else selectedLive = live }, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(128.dp).background(if (live.status == "live") WhappyDark else Color.White), contentAlignment = Alignment.Center) {
                        Icon(if (live.status == "live") Icons.Rounded.PlayArrow else Icons.Rounded.Schedule, null, tint = if (live.status == "live") Color.White else WhappyBlue, modifier = Modifier.size(46.dp))
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(if (live.status == "live") WhappyBlue else Color(0xFF6B7280)).padding(horizontal = 9.dp, vertical = 5.dp)) { Text(if (live.status == "live") "● EN DIRECT" else "PRÊT À DÉMARRER", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        if (liveReady && live.status == "live") Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" ${live.viewerCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(live.title, color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(live.hostPhotoUrl, live.hostName, 30.dp, shape = CircleShape)
                            Text("${live.hostName} · ${live.category}", Modifier.weight(1f).padding(start = 8.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(liveModeLabel(live.hostMode), color = WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
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
                        Text("STUDIO LIVE PRIVÉ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(live.title, color = Color.White.copy(alpha = .86f), fontSize = 12.sp, maxLines = 1)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC202020)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text("NON DIFFUSÉ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
                    color = Color.White.copy(alpha = .97f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Vérification avant diffusion", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                        Text("La diffusion publique n’est pas encore prête pour ce direct. Vous pouvez vérifier la caméra, le microphone et le haut-parleur sans être visible par les spectateurs.", color = WhappyMuted, fontSize = 9.sp, lineHeight = 13.sp)
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
    onContactBusiness: (WhappyBusinessPage) -> Unit,
    pages: List<WhappyBusinessPage>,
    activeBusinessPageId: String,
    conversations: List<WhappyConversation>,
    campaigns: List<WhappyCampaign>,
    adMetrics: Map<String, WhappyAdMetrics>,
    deals: List<WhappyDeal>,
    paymentNotices: List<WhappyPaymentNotice>,
    preview: Boolean,
    busy: Boolean,
    onCreatePage: (String, String, String, String, String, String) -> Unit,
    onUpdatePage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onUpdateLogo: (WhappyBusinessPage, Uri, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onUpdateCampaignDelivery: (String, String) -> Unit,
    onCreateDeal: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit,
    onUpdateDealStatus: (String, String) -> Unit,
    onMarkPaymentRead: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onActivatePage: (String) -> Unit,
    onOpenTwin: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenClient: (WhappyConversation) -> Unit,
) {
    var section by remember { mutableStateOf(BusinessSection.DASHBOARD) }
    var creatingPage by rememberSaveable { mutableStateOf(false) }
    var creatingCampaignMode by remember { mutableStateOf<String?>(null) }
    var boostedProduct by remember { mutableStateOf<WhappyDeal?>(null) }
    var creatingDeal by rememberSaveable { mutableStateOf(false) }
    var editingPage by remember { mutableStateOf<WhappyBusinessPage?>(null) }
    var localPages by remember { mutableStateOf(emptyList<WhappyBusinessPage>()) }
    var localCampaigns by remember { mutableStateOf(emptyList<WhappyCampaign>()) }
    var localDeals by remember { mutableStateOf(emptyList<WhappyDeal>()) }
    var previewPageUpdates by remember { mutableStateOf(emptyMap<String, WhappyBusinessPage>()) }
    var previewDealStatuses by remember { mutableStateOf(emptyMap<String, String>()) }
    var locallyRead by remember { mutableStateOf(emptySet<String>()) }
    var selectedPageId by rememberSaveable { mutableStateOf(activeBusinessPageId) }
    val visiblePages = (localPages + pages).map { previewPageUpdates[it.id] ?: it }
    val visibleCampaigns = localCampaigns + campaigns
    val visibleDeals = (localDeals + deals).map { deal -> previewDealStatuses[deal.id]?.let { deal.copy(status = it) } ?: deal }
    LaunchedEffect(visiblePages.map { it.id }, selectedPageId) {
        if (selectedPageId.isBlank() || visiblePages.none { it.id == selectedPageId }) {
            selectedPageId = visiblePages.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(activeBusinessPageId) {
        if (activeBusinessPageId.isNotBlank() && visiblePages.any { it.id == activeBusinessPageId }) {
            selectedPageId = activeBusinessPageId
        }
    }
    val activePage = visiblePages.firstOrNull { it.id == selectedPageId } ?: visiblePages.firstOrNull()
    val recentClients = activePage?.let { page ->
        conversations.asSequence()
            .filter { it.profileType == "business" && it.businessPageId == page.id }
            .sortedByDescending { it.updatedAt }
            .take(3)
            .toList()
    }.orEmpty()
    val pageDeals = activePage?.let { page -> visibleDeals.filter { it.pageId == page.id } }.orEmpty()
    val pageCampaigns = activePage?.let { page -> visibleCampaigns.filter { it.pageId == page.id } }.orEmpty()
    val pageAdMetrics = pageCampaigns.map { adMetrics[it.id] ?: WhappyAdMetrics() }
    val advertisingViews = pageAdMetrics.sumOf { it.impressions }
    val advertisingClicks = pageAdMetrics.sumOf { it.clicks }
    val pagePayments = activePage?.let { page -> paymentNotices.filter { it.pageId == page.id } }.orEmpty()
    val unread = pagePayments.count { !it.read && it.id !in locallyRead }
    val paidTotal = pagePayments.filter { it.status == "paid" }.sumOf { it.amount }
    val soldUnits = pageDeals.sumOf { it.sold }
    val availableStock = pageDeals.sumOf { (it.stock - it.sold).coerceAtLeast(0) }
    val advertisingBudget = pageCampaigns.filter { it.status == "active" }.sumOf { campaign ->
        campaign.totalBudget.takeIf { it > 0L } ?: (campaign.dailyBudget * campaign.days)
    }
    val profileCompletion = activePage?.let { page ->
        listOf(page.logoUrl, page.name, page.category, page.bio, page.city, page.phone, page.website)
            .count { it.isNotBlank() } * 100 / 7
    } ?: 0
    LazyColumn(Modifier.fillMaxSize().background(WapiCanvas), contentPadding = PaddingValues(WapiMobile.screen, 10.dp, WapiMobile.screen, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WapiCommerceLaunchers(onContactBusiness) }
        item {
            BusinessWorkspaceHero(
                page = activePage,
                profileCompletion = profileCompletion,
                unread = unread,
                revenue = paidTotal,
                orders = pagePayments.size,
                onNotifications = onEnableNotifications,
                onEdit = { if (activePage == null) creatingPage = true else editingPage = activePage },
            )
        }
        if (visiblePages.size > 1) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ESPACE ACTIF", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visiblePages.forEach { page ->
                        val selected = page.id == activePage?.id
                        Surface(
                            modifier = Modifier.wapiClickable {
                                selectedPageId = page.id
                            },
                            color = if (selected) WhappyNavy else Color.White,
                            shape = RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) WhappyNavy else WhappyLine),
                        ) {
                            Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(page.logoUrl, page.name, 34.dp, shape = RoundedCornerShape(9.dp))
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(page.name, color = if (selected) Color.White else WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(page.category.ifBlank { "Business WAPI" }, color = if (selected) Color.White.copy(alpha = .66f) else WhappyMuted, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            BusinessQuickActions(
                enabled = activePage != null,
                onInbox = {
                    activePage?.id?.takeUnless { preview }?.let(onActivatePage)
                    onOpenMessages()
                },
                onCalls = {
                    activePage?.id?.takeUnless { preview }?.let(onActivatePage)
                    onOpenCalls()
                },
                onProduct = { if (activePage == null) creatingPage = true else creatingDeal = true },
                onAdvertise = { if (activePage == null) creatingPage = true else { creatingCampaignMode = "campaign" } },
            )
        }
        if (activePage != null && (profileCompletion < 100 || pageDeals.isEmpty() || pageCampaigns.isEmpty())) item {
            BusinessReadinessCard(
                profileCompletion = profileCompletion,
                hasCatalog = pageDeals.isNotEmpty(),
                hasCampaign = pageCampaigns.isNotEmpty(),
                onProfile = { editingPage = activePage },
                onCatalog = { section = BusinessSection.CATALOG },
                onCampaign = { section = BusinessSection.ADS },
            )
        }
        item {
            BusinessSectionRail(selected = section, onSelect = { section = it })
        }
        when (section) {
            BusinessSection.DASHBOARD -> {
                item {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text("Aujourd’hui", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark)
                        Text("Vos opérations importantes, sans chiffres fictifs", color = WhappyMuted, fontSize = 11.sp)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Offres actives", pageDeals.count { it.status == "active" }.toString(), Modifier.weight(1f))
                        MetricCard("Stock", availableStock.toString(), Modifier.weight(1f))
                        MetricCard("Alertes", unread.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    BusinessRecentClientsCard(
                        clients = recentClients,
                        onOpenAll = {
                            activePage?.id?.takeUnless { preview }?.let(onActivatePage)
                            onOpenMessages()
                        },
                        onOpenClient = onOpenClient,
                    )
                }
                item {
                    BusinessCommandGrid(
                        commands = listOf(
                            BusinessCommand(Icons.Rounded.Storefront, "Identité", activePage?.name ?: "Créer la page", WapiSoftBlue) { section = BusinessSection.PAGES },
                            BusinessCommand(Icons.AutoMirrored.Rounded.ReceiptLong, "Catalogue", "${pageDeals.size} offre(s)", Color(0xFFEAF7F1)) { section = BusinessSection.CATALOG },
                            BusinessCommand(Icons.Rounded.LiveTv, "Vente en direct", "Public ou contacts", Color(0xFFFFF1E7)) { section = BusinessSection.SALES },
                            BusinessCommand(Icons.Rounded.BusinessCenter, "Commandes", "${pagePayments.size} à suivre", Color(0xFFF2EDFF)) { section = BusinessSection.ORDERS },
                            BusinessCommand(Icons.Rounded.Payments, "Paiements", if (unread > 0) "$unread nouvelle(s)" else "À jour", Color(0xFFEAF6FF)) { section = BusinessSection.PAYMENTS },
                            BusinessCommand(Icons.Rounded.Visibility, "Performances", "$advertisingViews vues Ads", Color(0xFFF2F4F7)) { section = BusinessSection.INSIGHTS },
                        ),
                    )
                }
                item { BusinessFeatureCard(Icons.Rounded.AutoAwesome, "WIA Business", "Préparer une campagne, un catalogue ou un direct") { onOpenTwin() } }
            }
            BusinessSection.PAGES -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Mes pages", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark); TextButton(onClick = { creatingPage = true }) { Text("+ Nouvelle") } } }
                if (visiblePages.isEmpty()) item { EmptyState("Aucune page Business", "Créez une identité professionnelle pour votre activité ou votre contenu.") }
                items(visiblePages, key = { it.id }) { page -> BusinessPageCard(page) { editingPage = page } }
            }
            BusinessSection.DEALS -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Deals en cours", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark); if (visiblePages.isNotEmpty()) TextButton(onClick = { creatingDeal = true }) { Text("+ Créer") } } }
                if (visiblePages.isEmpty()) item { EmptyState("Page requise", "Créez d’abord votre page Business pour publier des Deals.") }
                else if (pageDeals.isEmpty()) item { EmptyState("Aucun Deal", "Publiez une offre limitée pour activer vos ventes.") }
                items(pageDeals, key = { it.id }) { deal -> DealCard(deal) { status -> if (preview) previewDealStatuses = previewDealStatuses + (deal.id to status) else onUpdateDealStatus(deal.id, status) } }
            }
            BusinessSection.CATALOG -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Catalogue", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark); Text("Produits, services et offres vendables", color = WhappyMuted, fontSize = 11.sp) }; if (visiblePages.isNotEmpty()) Button(onClick = { creatingDeal = true }, shape = RoundedCornerShape(13.dp)) { Text("+ Ajouter") } } }
                if (visiblePages.isEmpty()) item { EmptyState("Page requise", "Créez votre page Business avant de composer le catalogue.") }
                else if (pageDeals.isEmpty()) item { EmptyState("Catalogue vide", "Ajoutez votre premier produit ou service avec une offre attractive.") }
                items(pageDeals, key = { "catalog-${it.id}" }) { deal ->
                    CatalogCard(
                        deal = deal,
                        onAddVariant = { creatingDeal = true },
                        onBoost = { boostedProduct = deal; creatingCampaignMode = "product" },
                    )
                }
            }
            BusinessSection.SALES -> {
                item { WapiBusinessSaleRoomManager(activePage?.let(::listOf).orEmpty(), pageDeals) }
            }
            BusinessSection.ORDERS -> {
                item { Text("Centre de commandes", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark) }
                if (pagePayments.isEmpty()) item { EmptyState("Aucune commande", "Les commandes confirmées par paiement apparaîtront ici.") }
                items(pagePayments, key = { "order-${it.id}" }) { notice -> OrderCard(notice) }
            }
            BusinessSection.PAYMENTS -> {
                item { Text("Notifications de paiement", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark) }
                item { OutlinedButton(onClick = onEnableNotifications, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Notifications, null); Text("Recevoir les alertes sur ce téléphone", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold) } }
                if (pagePayments.isEmpty()) item { EmptyState("Aucun paiement", "Les paiements confirmés apparaîtront ici dès que votre fournisseur sera connecté.") }
                items(pagePayments, key = { it.id }) { notice -> PaymentNoticeCard(notice, notice.id in locallyRead) { locallyRead = locallyRead + notice.id; if (!preview) onMarkPaymentRead(notice.id) } }
                item { Text("Sécurité : une alerte n’est créée qu’après confirmation du fournisseur de paiement. La réception réelle nécessite son webhook serveur.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp) }
            }
            BusinessSection.ADS -> {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("WAPI Ads", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = WhappyDark); Text("Commandez des diffusions réelles et suivez chaque affichage", color = WhappyMuted, fontSize = 11.sp) } } }
                if (visiblePages.isNotEmpty()) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(Modifier.weight(1f).wapiClickable { creatingCampaignMode = "boost" }, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine)) {
                            Column(Modifier.padding(16.dp)) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = WhappyBlue) }; Text("Booster mon activité", Modifier.padding(top = 12.dp), color = WhappyDark, fontWeight = FontWeight.Bold); Text("Visibilité locale et visites du profil", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 10.sp, lineHeight = 14.sp) }
                        }
                        Card(Modifier.weight(1f).wapiClickable { creatingCampaignMode = "campaign" }, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine)) {
                            Column(Modifier.padding(16.dp)) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFEAF7F1)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = WapiSuccess) }; Text("Créer une publicité", Modifier.padding(top = 12.dp), color = WhappyDark, fontWeight = FontWeight.Bold); Text("Créatif, audience, région et objectif", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 10.sp, lineHeight = 14.sp) }
                        }
                    }
                }
                if (visiblePages.isEmpty()) item { EmptyState("Page requise", "Créez une page Business avant de lancer une publicité.") }
                else if (pageCampaigns.isEmpty()) item { EmptyState("Aucune campagne", "Développez votre audience avec une campagne ciblée.") }
                items(pageCampaigns, key = { it.id }) { campaign ->
                    CampaignCard(campaign, adMetrics[campaign.id] ?: WhappyAdMetrics(), busy) { action ->
                        if (!preview) onUpdateCampaignDelivery(campaign.id, action)
                    }
                }
            }
            BusinessSection.INSIGHTS -> {
                item { Text("Performances Business", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Revenus", formatMoney(paidTotal), Modifier.weight(1f)); MetricCard("Ventes", soldUnits.toString(), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Stock", availableStock.toString(), Modifier.weight(1f)); MetricCard("Publicité", formatMoney(advertisingBudget), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Vues Ads", advertisingViews.toString(), Modifier.weight(1f)); MetricCard("Clics Ads", advertisingClicks.toString(), Modifier.weight(1f)) } }
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
private fun BusinessWorkspaceHero(
    page: WhappyBusinessPage?,
    profileCompletion: Int,
    unread: Int,
    revenue: Long,
    orders: Int,
    onNotifications: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, WhappyLine, RoundedCornerShape(20.dp)),
    ) {
        Box(Modifier.width(5.dp).height(84.dp).align(Alignment.TopStart).background(WhappyBlue))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(page?.logoUrl.orEmpty(), page?.name ?: "WAPI Business", 58.dp, shape = RoundedCornerShape(15.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("COMPTE BUSINESS", color = WhappyDeepBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                        Surface(color = Color(0xFFEAF7F1), shape = RoundedCornerShape(7.dp)) {
                            Text("SÉPARÉ", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = WapiSuccess, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(page?.name ?: "Créez votre entreprise", color = WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (page == null) "Identité, appels et clients professionnels" else "@${page.handle} · ${page.category}", color = WhappyMuted, fontSize = 10.sp)
                }
                FilledIconButton(onClick = onNotifications, shape = RoundedCornerShape(12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = WapiSoftBlue, contentColor = WhappyDeepBlue)) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(Icons.Rounded.Notifications, "Notifications Business")
                        if (unread > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFC857)))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusinessHeroMetric("Revenus confirmés", formatMoney(revenue), Modifier.weight(1.35f))
                BusinessHeroMetric("Commandes", orders.toString(), Modifier.weight(1f))
                BusinessHeroMetric("Profil", "$profileCompletion %", Modifier.weight(1f))
            }
            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WhappyDark, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(if (page == null) Icons.Rounded.Add else Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = Color.White)
                Text(if (page == null) "  Configurer mon Business" else "  Gérer l’identité professionnelle", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BusinessHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(WapiCanvas).border(1.dp, WhappyLine.copy(alpha = .7f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(label.uppercase(), color = WhappyMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = WhappyDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun BusinessSection.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    BusinessSection.DASHBOARD -> Icons.Rounded.Dashboard
    BusinessSection.PAGES -> Icons.Rounded.Storefront
    BusinessSection.CATALOG -> Icons.AutoMirrored.Rounded.ReceiptLong
    BusinessSection.DEALS -> Icons.Rounded.LocalOffer
    BusinessSection.SALES -> Icons.Rounded.LiveTv
    BusinessSection.ORDERS -> Icons.Rounded.BusinessCenter
    BusinessSection.PAYMENTS -> Icons.Rounded.Payments
    BusinessSection.ADS -> Icons.Rounded.Bolt
    BusinessSection.INSIGHTS -> Icons.Rounded.Visibility
}

@Composable
private fun BusinessSectionRail(selected: BusinessSection, onSelect: (BusinessSection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BusinessSection.entries.forEach { section ->
            val active = section == selected
            Column(
                Modifier.width(78.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (active) WapiSoftBlue else Color.White)
                    .border(1.dp, if (active) WhappyBlue.copy(alpha = .35f) else WhappyLine.copy(alpha = .70f), RoundedCornerShape(15.dp))
                    .wapiClickable { onSelect(section) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(section.icon(), null, tint = if (active) WhappyBlue else WhappyDeepBlue, modifier = Modifier.size(20.dp))
                Text(section.label, color = if (active) WhappyDeepBlue else WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private data class BusinessCommand(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val detail: String,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun BusinessCommandGrid(commands: List<BusinessCommand>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth - 10.dp) / 2
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2,
        ) {
            commands.forEach { command ->
                Column(
                    Modifier.width(itemWidth).heightIn(min = 112.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color.White)
                        .border(1.dp, WhappyLine.copy(alpha = .62f), RoundedCornerShape(17.dp))
                        .wapiClickable(onClick = command.onClick)
                        .padding(15.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(command.tint), contentAlignment = Alignment.Center) {
                        Icon(command.icon, null, tint = WhappyDeepBlue, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(command.title, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        Text(command.detail, color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessQuickActions(
    enabled: Boolean,
    onInbox: () -> Unit,
    onCalls: () -> Unit,
    onProduct: () -> Unit,
    onAdvertise: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BusinessQuickAction(Icons.Rounded.ChatBubble, "Clients", enabled, onInbox)
        BusinessQuickAction(Icons.Rounded.Phone, "Appels", enabled, onCalls)
        BusinessQuickAction(Icons.Rounded.LocalOffer, "Produit", true, onProduct)
        BusinessQuickAction(Icons.Rounded.Bolt, "Publicité", true, onAdvertise)
    }
}

@Composable
private fun BusinessRecentClientsCard(
    clients: List<WhappyConversation>,
    onOpenAll: () -> Unit,
    onOpenClient: (WhappyConversation) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .65f)),
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Clients récents", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Messagerie de cette page Business", color = WhappyMuted, fontSize = 9.sp)
                }
                TextButton(onClick = onOpenAll) { Text("Tout voir", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
            if (clients.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WhappySurface.copy(alpha = .68f))
                        .wapiClickable(onClick = onOpenAll).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ChatBubble, null, tint = WhappyBlue, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text("Votre boîte clients est prête", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Les nouvelles demandes apparaîtront ici.", color = WhappyMuted, fontSize = 9.sp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = WhappyDeepBlue)
                }
            } else {
                clients.forEachIndexed { index, conversation ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).wapiClickable { onOpenClient(conversation) }
                            .padding(vertical = 9.dp, horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 45.dp, shape = RoundedCornerShape(14.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(conversation.peer.displayName, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatConversationMoment(conversation.updatedAt), color = if (conversation.unread) WhappyBlue else WhappyMuted, fontSize = 9.sp, fontWeight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal)
                            }
                            Text(conversation.lastMessage.ifBlank { "Nouvelle conversation" }, color = if (conversation.unread) WhappyInk else WhappyMuted, fontSize = 10.sp, fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (conversation.unread) Box(Modifier.size(9.dp).clip(CircleShape).background(WhappyBlue))
                    }
                    if (index < clients.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 59.dp).height(1.dp).background(WhappyLine.copy(alpha = .65f)))
                }
            }
        }
    }
}

@Composable
private fun BusinessReadinessCard(
    profileCompletion: Int,
    hasCatalog: Boolean,
    hasCampaign: Boolean,
    onProfile: () -> Unit,
    onCatalog: () -> Unit,
    onCampaign: () -> Unit,
) {
    val completed = listOf(profileCompletion >= 70, hasCatalog, hasCampaign).count { it }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Bolt, null, tint = WhappyBlue)
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("Centre de lancement", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$completed sur 3 étapes prêtes pour vendre", color = WhappyMuted, fontSize = 10.sp)
                }
                Text("${completed * 100 / 3} %", color = WhappyBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            LinearProgressIndicator(
                progress = { completed / 3f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = WhappyBlue,
                trackColor = WhappySurface,
            )
            listOf(
                Triple(profileCompletion >= 70, "Identité professionnelle", onProfile),
                Triple(hasCatalog, "Premier produit ou service", onCatalog),
                Triple(hasCampaign, "Première campagne régionale", onCampaign),
            ).forEach { step ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).wapiClickable(onClick = step.third).padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (step.first) Icons.Rounded.CheckCircle else Icons.Rounded.Add, null, tint = if (step.first) WapiSuccess else WhappyBlue, modifier = Modifier.size(20.dp))
                    Text(step.second, Modifier.weight(1f).padding(start = 9.dp), color = WhappyDark, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Text(if (step.first) "Prêt" else "Configurer", color = if (step.first) WapiSuccess else WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BusinessQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(17.dp)).wapiClickable(enabled = enabled, onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(if (enabled) WapiSoftBlue else WhappySurface),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (enabled) WhappyBlue else WhappyMuted, modifier = Modifier.size(21.dp)) }
        Text(label, color = if (enabled) WhappyDark else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BusinessFeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().wapiClickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine.copy(alpha = .62f)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(WhappyAuroraSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WhappyDeepBlue, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = WhappyDark); Text(body, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Box(Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = WhappyDeepBlue, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun CatalogCard(deal: WhappyDeal, onAddVariant: () -> Unit, onBoost: () -> Unit) {
    val remaining = (deal.stock - deal.sold).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(deal.title, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMoney(deal.dealPrice), color = WhappyBlue, fontWeight = FontWeight.Bold)
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
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Commande de ${notice.buyerName}", color = WhappyDark, fontWeight = FontWeight.Bold); Text("${notice.provider} · ${formatTime(notice.createdAt)}", color = WhappyMuted, fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.End) { Text(formatMoney(notice.amount), color = WhappyBlue, fontWeight = FontWeight.Bold); Text(if (notice.status == "paid") "PAYÉE" else notice.status.uppercase(), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun InsightCard(title: String, value: Long, target: Long, body: String) {
    val progress = if (target <= 0) 0f else (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp)) {
            Row { Text(title, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Bold); Text("${(progress * 100).toInt()} %", color = WhappyBlue, fontWeight = FontWeight.Bold) }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(CircleShape), color = WhappyBlue, trackColor = Color.White)
            Text(body, Modifier.padding(top = 9.dp), color = WhappyMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BusinessPageCard(page: WhappyBusinessPage, onEdit: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { UserAvatar(page.logoUrl, page.name, 56.dp); Column(Modifier.weight(1f).padding(start = 12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(page.name, fontWeight = FontWeight.Bold, color = WhappyDark); if (page.verified) Icon(Icons.Rounded.Verified, "Page certifiée", Modifier.padding(start = 5.dp).size(15.dp), tint = WapiVerifiedGray) }; Text("@${page.handle} · ${page.category}", color = WhappyBlue, fontSize = 11.sp); Text(page.bio.ifBlank { page.city }, Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2) }; TextButton(onClick = onEdit) { Text("Modifier") } }; FlowRow(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("📍 ${page.city}", color = WhappyMuted, fontSize = 10.sp); if (page.phone.isNotBlank()) Text("☎ ${page.phone}", color = WhappyMuted, fontSize = 10.sp); if (page.website.isNotBlank()) Text("↗ ${page.website}", color = WhappyBlue, fontSize = 10.sp) } } } }

@Composable
private fun DealCard(deal: WhappyDeal, onStatus: (String) -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (deal.status == "active") Color.White else Color.White).padding(horizontal = 8.dp, vertical = 5.dp)) { Text(if (deal.status == "active") "DEAL ACTIF" else deal.status.uppercase(), color = if (deal.status == "active") WhappyBlue else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }; Text(deal.pageName, Modifier.padding(start = 8.dp).weight(1f), color = WhappyMuted, fontSize = 11.sp); Text("${deal.sold}/${deal.stock} vendus", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text(deal.title, Modifier.padding(top = 10.dp), color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(deal.description, Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp); Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) { Text(formatMoney(deal.dealPrice), color = WhappyBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold); if (deal.originalPrice > deal.dealPrice) Text(formatMoney(deal.originalPrice), Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("Expire ${formatShortDate(deal.endsAt)}", color = WhappyMuted, fontSize = 10.sp) }; Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (deal.status == "active") OutlinedButton(onClick = { onStatus("paused") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Suspendre") } else if (deal.status == "paused") Button(onClick = { onStatus("active") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Réactiver") }; OutlinedButton(onClick = { onStatus("ended") }, enabled = deal.status != "ended", modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Terminer") } } } } }

@Composable
private fun PaymentNoticeCard(notice: WhappyPaymentNotice, locallyRead: Boolean, onRead: () -> Unit) { val unread = !notice.read && !locallyRead; Card(Modifier.fillMaxWidth().wapiClickable(enabled = unread, onClick = onRead), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (unread) Color.White else Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(if (notice.status == "paid") Color.White else Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Payments, null, tint = if (notice.status == "paid") WhappyBlue else WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(if (notice.status == "paid") "Paiement reçu" else notice.status.replaceFirstChar { it.uppercase() }, color = WhappyDark, fontWeight = FontWeight.Bold); Text("${notice.buyerName} · ${notice.provider}", color = WhappyMuted, fontSize = 11.sp); Text(formatTime(notice.createdAt), color = WhappyMuted, fontSize = 10.sp) }; Column(horizontalAlignment = Alignment.End) { Text("+${formatMoney(notice.amount)}", color = WhappyBlue, fontWeight = FontWeight.Bold); if (unread) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(WhappyBlue)) } } } }

@Composable
private fun CampaignCard(campaign: WhappyCampaign, metrics: WhappyAdMetrics, busy: Boolean, onAction: (String) -> Unit) {
    val placement = when (campaign.placement) { "profile_story" -> "Story du profil"; "market" -> "Marché"; "live" -> "Live"; else -> "Découverte Business" }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(campaign.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = WhappyDark)
                Surface(color = if (campaign.status == "active") WhappyBlue.copy(alpha = .10f) else Color(0xFFFFF8E8), shape = RoundedCornerShape(8.dp)) { Text(if (campaign.status == "active") "EN DIFFUSION" else if (campaign.status == "pending_payment") "PAIEMENT REQUIS" else campaign.status.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (campaign.status == "active") WhappyBlue else Color(0xFF8A6114), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            Text("${campaign.pageName} · $placement · ${campaign.city.ifBlank { "Zone nationale" }}", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
            if (campaign.audience.isNotBlank()) Text(campaign.audience, Modifier.padding(top = 4.dp), color = WhappyDark, fontSize = 11.sp)
            if (campaign.deliveryMode == "impressions" && campaign.targetImpressions > 0L) {
                val delivered = metrics.impressions.coerceAtMost(campaign.targetImpressions.toInt())
                val deliveryProgress = (delivered.toFloat() / campaign.targetImpressions.toFloat()).coerceIn(0f, 1f)
                Text("$delivered / ${campaign.targetImpressions} diffusions livrées", Modifier.padding(top = 6.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { deliveryProgress }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(7.dp).clip(CircleShape), color = WhappyBlue, trackColor = WapiSoftBlue)
                Text("Reste ${campaign.targetImpressions - delivered} · cadence ${campaign.dailyDeliveryCap.coerceAtLeast(1L)}/jour", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 9.sp)
            } else {
                Text("${formatMoney(campaign.dailyBudget)}/jour · ${campaign.days} jours · portée estimée ${campaign.estimatedReach.coerceAtLeast(120)}", Modifier.padding(top = 6.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampaignMetric("Vues réelles", metrics.impressions.toString(), Modifier.weight(1f))
                CampaignMetric("Clics", metrics.clicks.toString(), Modifier.weight(1f))
                CampaignMetric("Taux", String.format(Locale.getDefault(), "%.1f %%", metrics.clickThroughRate), Modifier.weight(1f))
            }
            if (campaign.status in setOf("active", "paused")) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = { onAction(if (campaign.status == "active") "pause" else "resume") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (campaign.status == "active") "Mettre en pause" else "Reprendre", fontSize = 10.sp) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { onAction("complete") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Terminer", fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun CampaignMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(WapiSoftBlue).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(value, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, Modifier.padding(top = 2.dp), color = WhappyMuted, fontSize = 8.sp)
    }
}

@Composable
internal fun DealDialog(pages: List<WhappyBusinessPage>, busy: Boolean, onDismiss: () -> Unit, onSave: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit) {
    if (pages.isEmpty()) return
    var pageId by rememberSaveable { mutableStateOf(pages.first().id) }
    val page = pages.firstOrNull { it.id == pageId } ?: pages.first()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var original by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("10") }
    var days by rememberSaveable { mutableStateOf("3") }
    val originalValue = original.toLongOrNull() ?: 0
    val priceValue = price.toLongOrNull() ?: 0
    val stockValue = stock.toIntOrNull() ?: 0
    val daysValue = days.toIntOrNull() ?: 0
    val valid = title.trim().length >= 3 && description.trim().length >= 3 &&
        originalValue > 0 && priceValue in 1..originalValue && stockValue > 0 && daysValue in 1..30
    WapiEditorScreen("Nouvelle offre", "Publier l’offre", busy, valid, onDismiss,
        onSubmit = { onSave(page, title.trim(), description.trim(), originalValue, priceValue, stockValue, daysValue) },
    ) {
        item { WapiEditorSection("Votre offre", "Choisissez la page et les informations présentées à vos clients.") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.forEach { candidate ->
                    FilterChip(selected = page.id == candidate.id, enabled = !busy,
                        onClick = { pageId = candidate.id }, label = { Text(candidate.name) })
                }
            }
        }
        item { WapiEditorField(title, { title = it }, "Nom de l’offre", 120, enabled = !busy) }
        item { WapiEditorField(description, { description = it }, "Description", 400, enabled = !busy, multiline = true) }
        item { WapiEditorSection("Prix et disponibilité", "Montants en FCFA. La durée doit être comprise entre 1 et 30 jours.") }
        item { WapiEditorField(original, { original = it.filter(Char::isDigit) }, "Prix habituel · FCFA", 9, enabled = !busy, keyboard = KeyboardType.Number) }
        item { WapiEditorField(price, { price = it.filter(Char::isDigit) }, "Prix de l’offre · FCFA", 9, enabled = !busy, keyboard = KeyboardType.Number) }
        if (priceValue > originalValue && original.isNotBlank()) {
            item { Text("Le prix de l’offre ne peut pas dépasser le prix habituel.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        }
        item { WapiEditorField(stock, { stock = it.filter(Char::isDigit) }, "Quantité disponible", 5, enabled = !busy, keyboard = KeyboardType.Number) }
        item { WapiEditorField(days, { days = it.filter(Char::isDigit) }, "Durée · jours", 2, enabled = !busy, keyboard = KeyboardType.Number) }
        if (valid) {
            item {
                WapiEditorSection("Avant de publier")
                Spacer(Modifier.height(12.dp))
                DealPreviewCard(page.name, title, description, originalValue, priceValue, stockValue, daysValue,
                    (((originalValue - priceValue) * 100) / originalValue).toInt())
            }
        }
    }
}

@Composable
private fun DealPreviewCard(pageName: String, title: String, description: String, original: Long, price: Long, stock: Int, days: Int, discount: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(9.dp)).background(WhappyBlue).padding(horizontal = 9.dp, vertical = 5.dp)) { Text("DEAL · -$discount %", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp) }; Spacer(Modifier.weight(1f)); Text("$days JOURS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Text(title.ifBlank { "Votre Deal exceptionnel" }, Modifier.padding(top = 14.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(description.ifBlank { "Une description claire qui donne envie d’acheter maintenant." }, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 11.sp, maxLines = 3)
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) { Text(formatMoney(price), color = WhappyBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("  ${formatMoney(original)}", color = Color.White, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("$stock restant(s)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Text(pageName, Modifier.padding(top = 10.dp), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun BusinessPageDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }
    WapiEditorScreen(
        title = "Créer un profil Business", action = "Créer le profil", busy = busy,
        ready = name.trim().length >= 2 && category.isNotBlank(), onDismiss = onDismiss,
        onSubmit = { onSave(name.trim(), category.trim(), bio.trim(), city.trim(), phone.trim(), website.trim()) },
    ) {
        item { WapiEditorSection("Votre entreprise", "Une identité professionnelle distincte de votre compte personnel.") }
        item { WapiEditorField(name, { name = it }, "Nom de l’entreprise", 80, enabled = !busy) }
        item { WapiEditorField(category, { category = it }, "Activité", 80, enabled = !busy) }
        item { WapiEditorField(bio, { bio = it }, "Présentation · facultatif", 400, enabled = !busy, multiline = true) }
        item { WapiEditorSection("Coordonnées publiques", "Ces informations permettent à vos clients de vous retrouver.") }
        item { WapiEditorField(city, { city = it }, "Ville ou région", 80, enabled = !busy) }
        item { WapiEditorField(phone, { phone = it }, "Téléphone professionnel", 30, enabled = !busy, keyboard = KeyboardType.Phone) }
        item { WapiEditorField(website, { website = it }, "Site ou catalogue · facultatif", 180, enabled = !busy, keyboard = KeyboardType.Uri) }
        item { Text("Vous pouvez garder le même numéro. Messages, catalogue et statistiques restent liés à votre profil Business.", color = WhappyMuted, fontSize = 14.sp, lineHeight = 20.sp) }
    }
}

@Composable
private fun BusinessIdentityCard(page: WhappyBusinessPage?, onCreate: () -> Unit, onOpenInbox: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .18f))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(page?.logoUrl.orEmpty(), page?.name ?: "B", 48.dp, shape = RoundedCornerShape(14.dp))
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(if (page == null) "Profil Business à créer" else page.name, color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text(if (page == null) "Compte personnel inchangé" else "@${page.handle} · identité professionnelle", color = WhappyMuted, fontSize = 10.sp)
                }
                Surface(color = WhappyBlue.copy(alpha = .10f), shape = RoundedCornerShape(9.dp)) { Text("BUSINESS", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            Text(if (page == null) "Créez une page Business pour vendre, recevoir des demandes et configurer WIA sans mélanger vos messages personnels." else "Même numéro, deux identités : vos clients écrivent à cette page et non à votre profil personnel.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (page == null) onCreate else onOpenInbox, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(if (page == null) Icons.Rounded.BusinessCenter else Icons.Rounded.ChatBubble, null); Text(if (page == null) "Créer le Business" else "Messagerie Business", Modifier.padding(start = 6.dp)) }
                if (page != null) OutlinedButton(onClick = onCreate, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Edit, null); Text("Gérer") }
            }
        }
    }
}

@Composable
internal fun EditBusinessPageDialog(
    page: WhappyBusinessPage,
    busy: Boolean,
    onDismiss: () -> Unit,
    onLogo: (Uri, String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable(page.id) { mutableStateOf(page.name) }
    var category by rememberSaveable(page.id) { mutableStateOf(page.category) }
    var bio by rememberSaveable(page.id) { mutableStateOf(page.bio) }
    var city by rememberSaveable(page.id) { mutableStateOf(page.city) }
    var phone by rememberSaveable(page.id) { mutableStateOf(page.phone) }
    var website by rememberSaveable(page.id) { mutableStateOf(page.website) }
    var localLogo by remember(page.id, page.logoUrl) { mutableStateOf(page.logoUrl) }
    var logoToCrop by remember(page.id) { mutableStateOf<Uri?>(null) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) logoToCrop = uri
    }
    WapiEditorScreen(
        title = "Modifier le profil Business", action = "Enregistrer", busy = busy,
        ready = name.trim().length >= 2 && category.isNotBlank(), onDismiss = onDismiss,
        onSubmit = { onSave(name.trim(), category.trim(), bio.trim(), city.trim(), phone.trim(), website.trim()) },
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UserAvatar(localLogo, name.ifBlank { page.name }, 72.dp, shape = RoundedCornerShape(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("@${page.handle}", color = WhappyMuted, fontSize = 14.sp)
                    TextButton(enabled = !busy, onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Photo, null, Modifier.size(18.dp))
                        Text(if (localLogo.isBlank()) "  Ajouter un logo" else "  Changer et recadrer")
                    }
                }
            }
        }
        item { WapiEditorSection("Identité publique") }
        item { WapiEditorField(name, { name = it }, "Nom de l’entreprise", 80, enabled = !busy) }
        item { WapiEditorField(category, { category = it }, "Activité", 80, enabled = !busy) }
        item { WapiEditorField(bio, { bio = it }, "Présentation", 400, enabled = !busy, multiline = true) }
        item { WapiEditorSection("Coordonnées publiques") }
        item { WapiEditorField(city, { city = it }, "Ville ou région", 80, enabled = !busy) }
        item { WapiEditorField(phone, { phone = it }, "Téléphone professionnel", 30, enabled = !busy, keyboard = KeyboardType.Phone) }
        item { WapiEditorField(website, { website = it }, "Site ou catalogue · facultatif", 180, enabled = !busy, keyboard = KeyboardType.Uri) }
        item { Text("Votre logo et ces informations sont visibles sur votre page Business.", color = WhappyMuted, fontSize = 14.sp, lineHeight = 20.sp) }
    }
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
    val initialPage = pages.firstOrNull { it.id == product?.pageId } ?: pages.firstOrNull() ?: return
    var selectedPage by remember(product?.pageId) { mutableStateOf(initialPage) }
    var objective by remember(mode) { mutableStateOf(if (mode == "product") "sales" else if (mode == "boost") "reach" else "messages") }
    var placement by remember(mode) { mutableStateOf(if (mode == "boost" || mode == "product") "profile_story" else "inbox") }
    var destination by remember(mode) { mutableStateOf(if (mode == "boost" || mode == "product") "page" else "message") }
    var title by remember(product?.id) { mutableStateOf(product?.let { "Découvrez ${it.title}" }.orEmpty()) }
    var creative by remember(product?.id) { mutableStateOf(product?.let { "${it.description}\n${formatMoney(it.dealPrice)} · stock limité" }.orEmpty()) }
    var audience by remember { mutableStateOf("Public local") }
    var city by remember { mutableStateOf("Brazzaville") }
    var countryCode by remember { mutableStateOf(Locale.getDefault().country.uppercase(Locale.ROOT).ifBlank { "CG" }) }
    var diffusions by remember { mutableStateOf("10000") }
    var days by remember { mutableStateOf("7") }
    val targetImpressions = diffusions.toLongOrNull() ?: 0L
    val duration = days.toIntOrNull() ?: 0
    val totalBudget = ((targetImpressions + 999L) / 1_000L) * 2_500L
    val dailyBudget = if (duration > 0) maxOf(500L, (totalBudget + duration - 1L) / duration) else 0L
    val valid = title.trim().length >= 2 && creative.trim().length >= 2 && targetImpressions in 1_000L..20_000_000L && duration in 1..90
    val screenTitle = if (mode == "product") "Booster ce produit" else if (mode == "boost") "Booster mon activité" else "Nouvelle campagne"

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = WapiCanvas) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !busy, onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = WhappyDark)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(screenTitle, color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("ELEPHANT · diffusion WAPI expliquée et contrôlée", color = WhappyMuted, fontSize = 10.sp)
                    }
                    Surface(color = WapiSoftBlue, shape = RoundedCornerShape(10.dp)) {
                        Text("BROUILLON", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = WhappyBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = WhappyLine.copy(alpha = .68f))

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        CampaignSectionHeader("1", "Identité et objectif", "Choisissez qui parle et ce que la campagne doit accomplir")
                    }
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("PAGE À PROMOUVOIR", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    pages.forEach { page ->
                                        FilterChip(
                                            selected = selectedPage.id == page.id,
                                            onClick = { selectedPage = page },
                                            label = { Text(page.name, maxLines = 1) },
                                            leadingIcon = if (selectedPage.id == page.id) { { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) } } else null,
                                        )
                                    }
                                }
                                Text("OBJECTIF", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("reach" to "Visibilité", "messages" to "Messages", "traffic" to "Trafic", "sales" to "Ventes").forEach { option ->
                                        FilterChip(selected = objective == option.first, onClick = { objective = option.first }, label = { Text(option.second) })
                                    }
                                }
                            }
                        }
                    }

                    item { CampaignSectionHeader("2", "Création", "Un message clair, lisible et adapté au mobile") }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nom de la campagne") }, supportingText = { Text("Visible uniquement par vous") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                            OutlinedTextField(creative, { creative = it.take(600) }, Modifier.fillMaxWidth(), label = { Text("Message publicitaire") }, supportingText = { Text("${creative.length}/600") }, minLines = 4, shape = RoundedCornerShape(15.dp))
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("APERÇU UTILISATEUR", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                            SponsoredCampaignCard(
                                campaign = WhappyCampaign(
                                    id = "preview",
                                    pageId = selectedPage.id,
                                    pageName = selectedPage.name,
                                    objective = objective,
                                    title = title.trim().ifBlank { "Votre titre apparaîtra ici" },
                                    dailyBudget = dailyBudget,
                                    days = duration.coerceAtLeast(1),
                                    status = "draft",
                                    placement = placement,
                                    destination = destination,
                                    creative = creative.trim().ifBlank { "Prévisualisez ici le message que les utilisateurs WAPI verront." },
                                    cta = if (destination == "message") "Envoyer un message" else "Découvrir",
                                    audience = audience.trim(),
                                    city = city.trim(),
                                    countryCode = countryCode,
                                ),
                                onOpen = {},
                            )
                        }
                    }

                    item { CampaignSectionHeader("3", "Diffusion", "Choisissez l’emplacement et l’action attendue") }
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyLine)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("EMPLACEMENT", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("profile_story" to "Story", "inbox" to "Découverte", "market" to "Marché", "live" to "Direct").forEach { option ->
                                        FilterChip(selected = placement == option.first, onClick = { placement = option.first }, label = { Text(option.second) })
                                    }
                                }
                                Text("ACTION APRÈS LE CLIC", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("message" to "Écrire", "page" to "Voir la page", "call" to "Appeler", "website" to "Ouvrir le lien").forEach { option ->
                                        FilterChip(selected = destination == option.first, onClick = { destination = option.first }, label = { Text(option.second) })
                                    }
                                }
                            }
                        }
                    }

                    item { CampaignSectionHeader("4", "Audience et diffusions", "Commandez un volume réel d’affichages, avec arrêt automatique au quota") }
                    item { OutlinedTextField(audience, { audience = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Audience") }, singleLine = true, shape = RoundedCornerShape(15.dp)) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("PACKS DE DIFFUSION", color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf("1000" to "1K", "5000" to "5K", "10000" to "10K", "50000" to "50K", "100000" to "100K").forEach { pack ->
                                    FilterChip(selected = diffusions == pack.first, onClick = { diffusions = pack.first }, label = { Text(pack.second) })
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(city, { city = it.take(80) }, Modifier.weight(1f), label = { Text("Ville ou région") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                            OutlinedTextField(countryCode, { countryCode = it.filter(Char::isLetter).uppercase(Locale.ROOT).take(2) }, Modifier.width(94.dp), label = { Text("Pays") }, singleLine = true, shape = RoundedCornerShape(15.dp))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(diffusions, { diffusions = it.filter(Char::isDigit).take(8) }, Modifier.weight(1f), label = { Text("Diffusions") }, supportingText = { Text("1 000 minimum") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(15.dp))
                            OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Durée") }, suffix = { Text("jours", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(15.dp))
                        }
                    }
                    item {
                        Surface(color = Color(0xFFFFF7E4), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Rounded.Lock, null, tint = Color(0xFF8A5B00), modifier = Modifier.size(19.dp))
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text("Paiement sous votre contrôle", color = Color(0xFF684500), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("2 500 FCFA par tranche de 1 000 diffusions. Seuls les affichages réellement enregistrés comptent et la campagne s’arrête au quota.", color = Color(0xFF7A5610), fontSize = 10.sp, lineHeight = 15.sp)
                                    Text("Cadence prévue : ${if (duration > 0) (targetImpressions + duration - 1L) / duration else 0L} diffusions par jour.", Modifier.padding(top = 4.dp), color = Color(0xFF7A5610), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Surface(color = Color.White, shadowElevation = 12.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatCompactCount(targetImpressions.toInt())} diffusions commandées", color = WhappyMuted, fontSize = 9.sp)
                            Text("${formatMoney(totalBudget)}", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            enabled = valid && !busy,
                            onClick = {
                                onSave(
                                    WhappyCampaignDraft(
                                        pageId = selectedPage.id,
                                        pageName = selectedPage.name,
                                        objective = objective,
                                        title = title.trim(),
                                        creative = creative.trim(),
                                        cta = if (destination == "message") "Envoyer un message" else "Découvrir",
                                        audience = audience.trim(),
                                        city = city.trim(),
                                        dailyBudget = dailyBudget,
                                        days = duration,
                                        targetImpressions = targetImpressions,
                                        placement = placement,
                                        destination = destination,
                                        countryCode = countryCode,
                                    ),
                                )
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Continuer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignSectionHeader(number: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = WhappyMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = WapiBlueMist), border = androidx.compose.foundation.BorderStroke(1.dp, WhappySky.copy(alpha = .20f)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(13.dp)) {
            Box(Modifier.width(26.dp).height(3.dp).clip(CircleShape).background(WhappyAurora))
            Text(value, Modifier.padding(top = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = WhappyDark)
            Text(label, color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
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
                    Text("Identité active", color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text("Personnel et Business restent séparés, avec le même numéro WAPI.", color = WhappyMuted, fontSize = 10.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(activePage?.logoUrl ?: personalPhotoUrl, activePage?.name ?: personalName, 42.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(activePage?.name ?: personalName, color = WhappyDark, fontWeight = FontWeight.Bold)
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
                        Text("Changer de compte", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                    Text("COMPTES BUSINESS", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
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
        modifier = Modifier.fillMaxWidth().wapiClickable(onClick = onClick),
        color = if (selected) WapiSoftBlue else Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) WhappyBlue else WhappyLine),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl, name, 46.dp, shape = RoundedCornerShape(13.dp))
            Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(name, color = WhappyDark, fontWeight = FontWeight.Bold); Text(subtitle, color = WhappyMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
    adminMetricsLoading: Boolean,
    adminMetricsError: String?,
    adminReviewBusyId: String,
    adminReviewError: String?,
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
    onReloadAdminMetrics: () -> Unit,
    onReviewAdCampaign: (String, String, String, String) -> Unit,
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
    var callEndSounds by rememberSaveable { mutableStateOf(prefs.getBoolean("call_end_sounds", true)) }
    var dataSaver by rememberSaveable { mutableStateOf(prefs.getBoolean("data_saver", false)) }
    var compactMode by rememberSaveable { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var protectPreview by rememberSaveable { mutableStateOf(prefs.getBoolean("protect_preview", true)) }
    var typingSounds by rememberSaveable { mutableStateOf(prefs.getBoolean("typing_sounds", false)) }
    var hapticFeedback by rememberSaveable { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }
    var storageUsage by remember { mutableStateOf(WapiMediaStore.usage(context)) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    var selectedAdReview by remember { mutableStateOf<WapiAdminAdReview?>(null) }
    var adReviewAction by remember { mutableStateOf("approve") }
    var adPaymentReference by remember { mutableStateOf("") }
    var adReviewNote by remember { mutableStateOf("") }
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
    selectedAdReview?.let { campaign ->
        val approving = adReviewAction == "approve"
        AlertDialog(
            onDismissRequest = { if (adminReviewBusyId.isBlank()) selectedAdReview = null },
            title = { Text(if (approving) "Valider la diffusion" else "Refuser la campagne") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${campaign.pageName} · ${campaign.title}", color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text(
                        if (campaign.targetImpressions > 0L) "${campaign.targetImpressions} diffusions · ${formatMoney(campaign.totalBudget)}"
                        else "Budget ${formatMoney(campaign.totalBudget)} · ${campaign.days} jour(s)",
                        color = WhappyMuted,
                        fontSize = 11.sp,
                    )
                    if (approving) {
                        OutlinedTextField(
                            value = adPaymentReference,
                            onValueChange = { adPaymentReference = it.take(120) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Référence de paiement vérifiée") },
                            singleLine = true,
                        )
                    } else {
                        OutlinedTextField(
                            value = adReviewNote,
                            onValueChange = { adReviewNote = it.take(300) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Motif du refus") },
                            minLines = 2,
                        )
                    }
                    Text("WAPI n’active aucune campagne sans contrôle explicite.", color = WhappyMuted, fontSize = 10.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = adminReviewBusyId.isBlank() && (!approving || adPaymentReference.trim().length >= 6),
                    onClick = {
                        onReviewAdCampaign(campaign.id, adReviewAction, adPaymentReference.trim(), adReviewNote.trim())
                        selectedAdReview = null
                        adPaymentReference = ""
                        adReviewNote = ""
                    },
                ) { Text(if (approving) "Activer" else "Refuser") }
            },
            dismissButton = { TextButton(onClick = { selectedAdReview = null }) { Text("Annuler") } },
        )
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(localPhoto, name, 78.dp, modifier = if (localPhoto.isBlank()) Modifier else Modifier.wapiClickable { previewPhoto = localPhoto }, shape = RoundedCornerShape(16.dp))
                    IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.size(32.dp).clip(CircleShape).background(WhappyBlue)) { Icon(Icons.Rounded.Photo, t("Changer la photo", "Change photo", "Bongola foto"), tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
                Column(Modifier.weight(1f).padding(start = 15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (founder) WhappyIdentity.founderName else name, Modifier.weight(1f, fill = false), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = WhappyDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(t("Touchez la photo pour l’agrandir.", "Tap the photo to enlarge it.", "Finá foto mpo na kokómisa monene."), Modifier.padding(horizontal = 20.dp, vertical = 2.dp), color = WhappyMuted, fontSize = 10.sp)
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
                            Text("Informations du compte", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(if (founder) "Compte officiel WAPI · Fondateur" else "Compte personnel WAPI", color = WhappyMuted, fontSize = 10.sp)
                        }
                        Text(if (online) "CLOUD ACTIF" else "HORS LIGNE", color = if (online) WhappyBlue else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                        Column(Modifier.weight(1f).padding(start = 11.dp)) { Text("Tableau de bord administrateur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Compte fondateur · accès réservé", color = Color.White.copy(alpha = .72f), fontSize = 10.sp) }
                    }
                    Text(
                        when {
                            adminMetricsLoading -> "Connexion sécurisée aux données WAPI Cloud…"
                            adminMetrics.serverReady -> "Données réelles agrégées par le serveur WAPI."
                            else -> adminMetricsError ?: "Le tableau serveur n’est pas encore disponible."
                        },
                        color = Color.White.copy(alpha = .82f), fontSize = 11.sp, lineHeight = 16.sp,
                    )
                    if (adminMetricsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = WhappySky, trackColor = Color.White.copy(alpha = .12f))
                    } else if (!adminMetrics.serverReady) {
                        OutlinedButton(
                            onClick = onReloadAdminMetrics,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Icon(Icons.Rounded.RestartAlt, null); Text("Réessayer la connexion", Modifier.padding(start = 7.dp)) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Utilisateurs WAPI", formatCompactCount(adminMetrics.users), Modifier.weight(1f))
                            MetricCard("CA encaissé", formatMoney(adminMetrics.paidRevenue), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Installations actives", formatCompactCount(adminMetrics.activeInstallations), Modifier.weight(1f))
                            MetricCard("Téléchargements stores", adminMetrics.officialDownloads?.let(::formatCompactCount) ?: "Non relié", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Pages Business", formatCompactCount(adminMetrics.businessPages), Modifier.weight(1f))
                            MetricCard("Commandes payées", formatCompactCount(adminMetrics.paidOrders), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Factures émises", formatCompactCount(adminMetrics.issuedInvoices), Modifier.weight(1f))
                            MetricCard("Créances ouvertes", formatMoney(adminMetrics.outstandingReceivables), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Campagnes Ads", formatCompactCount(adminMetrics.adCampaigns), Modifier.weight(1f))
                            MetricCard("Ads actives", formatCompactCount(adminMetrics.activeAdCampaigns), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Impressions Ads", formatCompactCount(adminMetrics.adImpressions), Modifier.weight(1f))
                            MetricCard("Clics Ads", formatCompactCount(adminMetrics.adClicks), Modifier.weight(1f))
                        }
                    }
                    if (adminReviewError != null) {
                        Text(adminReviewError, color = Color(0xFFFFB4AB), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    if (adminMetrics.serverReady && adminMetrics.pendingReviews.isNotEmpty()) {
                        Surface(color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("VALIDATIONS WAPI ADS · ${adminMetrics.pendingReviews.size}", color = WhappySky, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                                adminMetrics.pendingReviews.take(5).forEach { campaign ->
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(campaign.pageName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(campaign.title, color = Color.White.copy(alpha = .82f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            buildString {
                                                if (campaign.targetImpressions > 0L) append("${campaign.targetImpressions} diffusions · ")
                                                append("${formatMoney(campaign.totalBudget)} · ${campaign.city.ifBlank { campaign.countryCode.ifBlank { "Zone WAPI" } }}")
                                            },
                                            color = Color.White.copy(alpha = .58f),
                                            fontSize = 9.sp,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                enabled = adminReviewBusyId.isBlank(),
                                                onClick = { selectedAdReview = campaign; adReviewAction = "reject" },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            ) { Text("Refuser", fontSize = 10.sp) }
                                            Button(
                                                enabled = adminReviewBusyId.isBlank(),
                                                onClick = { selectedAdReview = campaign; adReviewAction = "approve" },
                                                modifier = Modifier.weight(1f),
                                            ) { Text(if (adminReviewBusyId == campaign.id) "Contrôle…" else "Vérifier", fontSize = 10.sp) }
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = .10f))
                                }
                            }
                        }
                    }
                    Surface(color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("CONNECTEURS DE DISTRIBUTION", color = WhappySky, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                            Text("Les téléchargements des stores ne sont jamais inventés : ils apparaissent uniquement après connexion des consoles officielles.", color = Color.White.copy(alpha = .70f), fontSize = 10.sp, lineHeight = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FounderConnector("Google Play", adminMetrics.storePlayConnected, Modifier.weight(1f))
                                FounderConnector("App Store", adminMetrics.storeIosConnected, Modifier.weight(1f))
                            }
                        }
                    }
                    if (adminMetrics.serverReady) {
                        Text(
                            "Les créances Business sont suivies séparément du CA encaissé : aucune facture émise n’est comptée comme un paiement.",
                            color = Color.White.copy(alpha = .70f), fontSize = 10.sp, lineHeight = 14.sp,
                        )
                        Text(
                            "Stories ${adminMetrics.stories} · Chaînes ${adminMetrics.channels} · Podcasts ${adminMetrics.radioEpisodes} · Lives ${adminMetrics.activeLives} · Ads en attente ${adminMetrics.pendingAdCampaigns}",
                            color = Color.White.copy(alpha = .58f), fontSize = 10.sp, lineHeight = 14.sp,
                        )
                        Text(
                            "Actualisé à ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(adminMetrics.generatedAt))} · source WAPI Cloud",
                            color = Color.White.copy(alpha = .48f), fontSize = 9.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpenSpace(WhappyTab.BUSINESS) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Business", fontSize = 11.sp) }
                        OutlinedButton(onClick = { onOpenSpace(WhappyTab.LIVE) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("En direct", fontSize = 11.sp) }
                        OutlinedButton(onClick = { onOpenSpace(WhappyTab.MESSAGES) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Messages", fontSize = 11.sp) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().wapiClickable(onClick = onOpenWhappies), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text("MON WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("Mon Jumeau numérique", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Image, voix, mouvements et missions", color = Color.White, fontSize = 11.sp) }; Text("›", color = WhappyBlue, fontSize = 26.sp) } }
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
                callEndSounds = callEndSounds,
                onCallEndSounds = { enabled ->
                    callEndSounds = enabled
                    prefs.edit().putBoolean("call_end_sounds", enabled).apply()
                    if (enabled) WhappySounds.callEnded(context)
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
        item { Card(Modifier.fillMaxWidth().wapiClickable { settingDialog = "Langue" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Language, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(t("Langue de l’application", "App language", "Lokota ya application"), fontWeight = FontWeight.Bold, color = WhappyDark); Text(if (language == WhappyLanguage.AUTOMATIC) "Automatique · ${detectedLanguage.label}" else language.label, color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 23.sp) } } }
        items(listOf(
            Triple("Confidentialité", "Contrôlez qui peut vous contacter", Icons.Rounded.Lock),
            Triple("Notifications", "Messages, sonnerie et appels", Icons.Rounded.Notifications),
            Triple("Discussions et apparence", "Saisie, vibration et densité", Icons.Rounded.ChatBubble),
            Triple("Stockage et données", "Médias et utilisation réseau", Icons.Rounded.AudioFile),
            Triple("Aide et sécurité", "Assistance et appareils connectés", Icons.Rounded.Info),
        )) { setting ->
            Card(Modifier.fillMaxWidth().wapiClickable { settingDialog = setting.first }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(setting.third, null, tint = WhappyBlue) }; Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(setting.first, fontWeight = FontWeight.Bold, color = WhappyDark); Text(setting.second, color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 23.sp) } }
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
        ModalBottomSheet(
            onDismissRequest = { settingDialog = null },
            containerColor = WapiSheet,
            dragHandle = { Box(Modifier.padding(top = 10.dp).width(38.dp).height(4.dp).clip(CircleShape).background(WhappyMuted.copy(alpha = .28f))) },
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(WapiSoftBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, null, tint = WhappyBlue) }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text(section, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = WhappyDark)
                        Text("Réglages appliqués immédiatement sur cet appareil", color = WhappyMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { settingDialog = null }) { Icon(Icons.Rounded.Close, "Fermer") }
                }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Fin d’appel", fontWeight = FontWeight.Bold); Text("Confirmation sonore quand la communication est coupée", color = WhappyMuted, fontSize = 10.sp) }
                            Switch(callEndSounds, { enabled -> callEndSounds = enabled; prefs.edit().putBoolean("call_end_sounds", enabled).apply(); if (enabled) WhappySounds.callEnded(context) })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { WhappySounds.previewIncomingRingtone(context) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Phone, null); Text("  Tester") }
                            OutlinedButton(onClick = {
                                onEnableNotifications()
                                runCatching {
                                    val notificationSettings = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    } else {
                                        Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                    }
                                    context.startActivity(notificationSettings)
                                }
                            }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Notifications, null); Text("  Système") }
                        }
                        Text("Les appels utilisent une alerte prioritaire ; les messages restent masqués sur l’écran verrouillé.", color = WhappyMuted, fontSize = 11.sp)
                    }
                    "Discussions et apparence" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Sons de saisie", fontWeight = FontWeight.Bold); Text("Clic court lié au volume multimédia", color = WhappyMuted, fontSize = 10.sp) }; Switch(typingSounds, { typingSounds = it; prefs.edit().putBoolean("typing_sounds", it).apply(); if (it) WhappySounds.typing(context) }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Réponse tactile", fontWeight = FontWeight.Bold); Text("Vibration légère sur les actions importantes", color = WhappyMuted, fontSize = 10.sp) }; Switch(hapticFeedback, { hapticFeedback = it; prefs.edit().putBoolean("haptic_feedback", it).apply(); if (it) WhappySounds.haptic(context) }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Aperçu privé", fontWeight = FontWeight.Bold); Text("Masque les contenus sensibles hors de WAPI", color = WhappyMuted, fontSize = 10.sp) }; Switch(protectPreview, { protectPreview = it; prefs.edit().putBoolean("protect_preview", it).apply() }) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Mode compact", fontWeight = FontWeight.Bold); Text("Plus de conversations visibles sur les petits écrans", color = WhappyMuted, fontSize = 10.sp) }; Switch(compactMode, { compactMode = it; prefs.edit().putBoolean("compact_mode", it).apply() }) }
                    }
                    "Stockage et données" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Économiseur de données", fontWeight = FontWeight.Bold); Text("Réduit le chargement automatique des médias", color = WhappyMuted, fontSize = 11.sp) }; Switch(dataSaver, { dataSaver = it; prefs.edit().putBoolean("data_saver", it).apply() }) }
                        Text("Cache temporaire : ${formatStorageBytes(storageUsage.cacheBytes)}", color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Médias conservés : ${formatStorageBytes(storageUsage.mediaBytes)} · Envois en attente : ${formatStorageBytes(storageUsage.outboxBytes)}", color = WhappyMuted, fontSize = 11.sp)
                        OutlinedButton(onClick = { WapiMediaStore.clearRebuildableCache(context); storageUsage = WapiMediaStore.usage(context) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Delete, null); Text("  Vider uniquement le cache") }
                        Text("Le cache peut être recréé depuis le cloud. Les messages, les pièces jointes en attente et les médias conservés ne sont jamais supprimés par cette action.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    "Aide et sécurité" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.fillMaxWidth(), color = WapiSoftBlue, shape = RoundedCornerShape(17.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Diagnostic de cet appareil", color = WhappyDark, fontWeight = FontWeight.Bold)
                                Text("WAPI ${BuildConfig.VERSION_NAME} · ${if (online) "Cloud joignable" else "Mode hors ligne"}", color = WhappyMuted, fontSize = 11.sp)
                                Text("Compte ${accountId.take(12).ifBlank { "non synchronisé" }} · cache ${formatStorageBytes(storageUsage.cacheBytes)}", color = WhappyMuted, fontSize = 10.sp)
                            }
                        }
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Lock, null); Text("  Permissions et sécurité Android") }
                        OutlinedButton(onClick = { uriHandler.openUri("mailto:support@whappy.chat?subject=Diagnostic%20WAPI%20${BuildConfig.VERSION_NAME}") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Info, null); Text("  Contacter l’assistance") }
                        Text("WAPI n’affiche jamais les clés techniques, les jetons Firebase ou les secrets de session dans cette page.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("WAPI réunit vos conversations, appels, achats, directs et services. En cas de problème, contactez l’assistance depuis cet appareil.", color = WhappyDark); OutlinedButton(onClick = { uriHandler.openUri("mailto:support@whappy.chat?subject=Aide%20WAPI") }, Modifier.fillMaxWidth()) { Text("Contacter l’assistance") } }
                }
                Button(onClick = { settingDialog = null }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("Terminé") }
            }
        }
    }
}

@Composable
private fun ProfileControlCenter(onOpenSpace: (WhappyTab) -> Unit) {
    val options = listOf(
        Triple(WhappyTab.MESSAGES, "Messages", "Discussions et demandes"),
        Triple(WhappyTab.CALLS, "Appels", "Voix, vidéo et historique"),
        Triple(WhappyTab.STORIES, "Ma Story", "Texte, photo, vidéo et podcast"),
        Triple(WhappyTab.WEPI, "WIA", "WIA Chat, mémoire privée et actions intelligentes"),
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
                    Text("Centre de contrôle", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
        modifier.wapiClickable(onClick = onClick),
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
            Text(title, color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    callEndSounds: Boolean,
    onCallEndSounds: (Boolean) -> Unit,
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
            Text("Réglages rapides", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                icon = Icons.Rounded.CallEnd,
                title = "Son de fin d’appel",
                subtitle = "Confirmation nette après avoir raccroché",
                checked = callEndSounds,
                onCheckedChange = onCallEndSounds,
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
                        Text("Mon identité WAPI", color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("À montrer uniquement aux personnes de confiance", color = WhappyMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer") }
                }
                UserAvatar(photoUrl, name, 66.dp, modifier = Modifier.padding(top = 18.dp), shape = RoundedCornerShape(15.dp))
                Text(name.ifBlank { "Compte WAPI" }, Modifier.padding(top = 9.dp), color = WhappyDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
    val maximumEdge = if (WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("data_saver", false)) 960 else 1_600
    val decoded = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largestEdge = max(info.size.width, info.size.height)
            if (largestEdge > maximumEdge) {
                val scale = maximumEdge.toFloat() / largestEdge.toFloat()
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maximumEdge) sampleSize *= 2
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
    val dataSaver = WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("data_saver", false)
    val maximumImageBytes = (if (dataSaver) 8L else 20L) * 1024L * 1024L
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
internal fun WapiSquareCropDialog(
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
        title = { Text(title, color = WhappyDark, fontWeight = FontWeight.Bold) },
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
    // A 768 px avatar remains crisp on high-density phones while keeping the
    // callable request small enough for unstable mobile networks.
    val targetSide = 768
    val output = if (side == targetSide) cropped else Bitmap.createScaledBitmap(cropped, targetSide, targetSide, true)
    return File(WapiMediaStore.cacheDirectory(context), "wapi-group-${System.currentTimeMillis()}.jpg").also { file ->
        var quality = 88
        var encoded = ByteArrayOutputStream()
        do {
            encoded.reset()
            output.compress(Bitmap.CompressFormat.JPEG, quality, encoded)
            quality -= 8
        } while (encoded.size() > 1_500_000 && quality >= 56)
        file.writeBytes(encoded.toByteArray())
        encoded.close()
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
    // Keep the last valid frame while a refreshed Firebase URL is resolving,
    // but reset it when a recycled list slot starts representing another
    // person. This avoids both the initials flash and a wrong person's photo.
    var bitmap by remember(name) { mutableStateOf(WapiBitmapMemoryCache.get(photoUrl)) }
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
        else Text(initials(name), color = Color.White, fontSize = (size.value * 0.31f).sp, fontWeight = FontWeight.Bold)
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
            .then(if (onClick != null) Modifier.wapiClickable { onClick() } else Modifier)
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
        Text(title, Modifier.padding(top = 18.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = WhappyDark, textAlign = TextAlign.Center)
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
