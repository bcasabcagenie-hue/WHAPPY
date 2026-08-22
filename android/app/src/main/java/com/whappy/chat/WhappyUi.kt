@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.whappy.chat

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
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
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BinaryBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

private val WhappyBlue = Color(0xFF0094F0)
private val WhappyDark = Color(0xFF191919)
private val WhappyInk = Color(0xFF202020)
private val WhappyMuted = Color(0xFF7A7A7A)
private val WhappyBackground = Color(0xFFF5F5F5)
private val WhappySurface = Color(0xFFF7F7F7)
private val WhappyNavy = Color(0xFF063A65)
private val WhappyLine = Color(0xFFE5E5E5)
private val WhappyDeepBlue = Color(0xFF0068C9)
private val WhappySky = Color(0xFF7BD9FF)
private val WapiVerifiedGray = Color(0xFF858D96)
private val WapiChatAccent = Color(0xFF0094F0)
private val WapiBubbleOutgoing = Color(0xFFD8F1FF)
private val WapiChatBackground = Color(0xFFF4F9FC)
private val WapiToolbar = Color(0xFFFFFFFF)
private val WapiActionPanel = Color(0xFF0A3557)
private val WhappyAurora = Brush.linearGradient(listOf(WhappyBlue, Color(0xFF00B7F4), WhappyDeepBlue))
private val WhappyAuroraSoft = Brush.linearGradient(listOf(Color(0xFFE5F7FF), Color(0xFFF4FBFF), Color(0xFFDDF2FF)))

private enum class WhappyLanguage(val code: String, val label: String) {
    FRENCH("fr", "Français"),
    ENGLISH("en", "English"),
    LINGALA("ln", "Lingála"),
}

private val LocalWhappyLanguage = staticCompositionLocalOf { WhappyLanguage.FRENCH }

private fun whappyText(language: WhappyLanguage, french: String, english: String, lingala: String): String = when (language) {
    WhappyLanguage.FRENCH -> french
    WhappyLanguage.ENGLISH -> english
    WhappyLanguage.LINGALA -> lingala
}

@Composable
private fun t(french: String, english: String, lingala: String): String = whappyText(LocalWhappyLanguage.current, french, english, lingala)

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

private enum class MessageActionType { Link, Phone }

private data class MessageAction(
    val title: String,
    val target: String,
    val type: MessageActionType,
    val start: Int,
    val end: Int,
)

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
        ),
        content = content,
    )
}

@Composable
fun WhappyRoot(
    state: WhappyUiState,
    preview: Boolean,
    phoneAuth: PhoneAuthController,
    onTab: (WhappyTab) -> Unit,
    onOpenConversation: (WhappyConversation) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String) -> Unit,
    onRetryMessages: () -> Unit,
    onSendMedia: (Uri, String, String, String, Int) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCloseChannel: () -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
    onCreateGroup: (String, List<WhappyMember>, Uri?, String) -> Unit,
    onSubscribeChannel: (String, Boolean) -> Unit,
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
    onCreateBusinessPage: (String, String, String, String) -> Unit,
    onUpdateBusinessPage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onCreateLive: (String, String, String, Boolean, String, String) -> Unit,
    onPublishStatus: (String, String, Uri?, String) -> Unit,
    onSaveWepiSettings: (WapiWepiSettings) -> Unit,
    onPublishRadioEpisode: (String, String, Uri, Long) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onEndLive: (String) -> Unit,
    onUpdateLiveStatus: (String, String) -> Unit,
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
        onOpenConversation = onOpenConversation,
        onCloseConversation = onCloseConversation,
        onSendMessage = onSendMessage,
        onRetryMessages = onRetryMessages,
        onSendMedia = onSendMedia,
        onReactMessage = onReactMessage,
        onDeleteMessage = onDeleteMessage,
        onEditMessage = onEditMessage,
        onTyping = onTyping,
        onHandleWhappyLink = onHandleWhappyLink,
        onOpenChannel = onOpenChannel,
        onCloseChannel = onCloseChannel,
        onCreateChannel = onCreateChannel,
        onCreateGroup = onCreateGroup,
        onSubscribeChannel = onSubscribeChannel,
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
        onCreateCampaign = onCreateCampaign,
        onCreateLive = onCreateLive,
        onPublishStatus = onPublishStatus,
        onSaveWepiSettings = onSaveWepiSettings,
        onPublishRadioEpisode = onPublishRadioEpisode,
        onDeleteStatus = onDeleteStatus,
        onEndLive = onEndLive,
        onUpdateLiveStatus = onUpdateLiveStatus,
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
            Image(painterResource(R.drawable.wapi_icon), "Logo WAPI", Modifier.size(92.dp))
            Text("WAPI", Modifier.padding(top = 18.dp), color = WhappyBlue, fontWeight = FontWeight.Black, fontSize = 28.sp)
            CircularProgressIndicator(Modifier.padding(top = 28.dp).size(30.dp), color = WhappyBlue, strokeWidth = 3.dp)
            Text("Ouverture de votre compte…", Modifier.padding(top = 14.dp), color = WhappyMuted)
        }
    }
}

private data class AuthCountry(val name: String, val flag: String, val code: String)

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
    onOpenConversation: (WhappyConversation) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String, String) -> Unit,
    onRetryMessages: () -> Unit,
    onSendMedia: (Uri, String, String, String, Int) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCloseChannel: () -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
    onCreateGroup: (String, List<WhappyMember>, Uri?, String) -> Unit,
    onSubscribeChannel: (String, Boolean) -> Unit,
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
    onCreateBusinessPage: (String, String, String, String) -> Unit,
    onUpdateBusinessPage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onCreateLive: (String, String, String, Boolean, String, String) -> Unit,
    onPublishStatus: (String, String, Uri?, String) -> Unit,
    onSaveWepiSettings: (WapiWepiSettings) -> Unit,
    onPublishRadioEpisode: (String, String, Uri, Long) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onEndLive: (String) -> Unit,
    onUpdateLiveStatus: (String, String) -> Unit,
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
    var showTwinStudio by remember { mutableStateOf(false) }
    var showActivityCenter by remember { mutableStateOf(false) }
    var showAppHub by remember { mutableStateOf(false) }
    var locallyReadNotices by remember { mutableStateOf(emptySet<String>()) }
    val selected = if (preview) previewConversation else state.selectedConversation
    val selectedChannel = if (preview) previewChannel else state.selectedChannel
    val currentTab = state.tab
    val activityNotices = if (preview) demoPaymentNotices else state.paymentNotices
    val activityLives = if (preview) demoLives else state.lives
    val unreadActivity = activityNotices.count { !it.read && it.id !in locallyReadNotices }
    val accountPhone = state.user?.phoneNumber.orEmpty()
    val isFounderAccount = WhappyIdentity.isFounder(accountPhone)
    val accountDisplayName = if (preview && state.user == null) {
        WhappyIdentity.founderName
    } else {
        WhappyIdentity.resolveAccountName(state.accountDisplayName, accountPhone)
    }
    val mainContext = LocalContext.current
    val languagePrefs = remember { WhappyFastStorage.preferences(mainContext, "whappy_language") }
    var appLanguage by rememberSaveable {
        mutableStateOf(WhappyLanguage.entries.firstOrNull { it.code == languagePrefs.getString("code", "fr") } ?: WhappyLanguage.FRENCH)
    }
    BackHandler(enabled = selected != null || selectedChannel != null || showTwinStudio || showActivityCenter || showAppHub) {
        if (showActivityCenter) showActivityCenter = false
        else if (showAppHub) showAppHub = false
        else if (showTwinStudio) showTwinStudio = false
        else if (preview && selectedChannel != null) previewChannel = null
        else if (preview) previewConversation = null
        else if (selectedChannel != null) onCloseChannel()
        else onCloseConversation()
    }
    if (!preview && state.error != null) AlertDialog(onDismissRequest = onDismissError, confirmButton = { TextButton(onClick = onDismissError) { Text("Fermer") } }, title = { Text("WAPI") }, text = { Text(state.error) })
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
    CompositionLocalProvider(LocalWhappyLanguage provides appLanguage) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WhappyBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selected == null && selectedChannel == null && !showTwinStudio) WhappyBottomBar(currentTab, onTab) { showAppHub = true }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
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
                    onBack = { if (preview) previewConversation = null else onCloseConversation() },
                    onSend = { value, reply ->
                        if (preview) previewMessages = previewMessages + WhappyMessage("local-${System.currentTimeMillis()}", value, "demo-user", System.currentTimeMillis(), replyToId = reply?.id.orEmpty(), replyText = reply?.text.orEmpty())
                        else onSendMessage(value, reply?.id.orEmpty(), reply?.text.orEmpty())
                    },
                    onSendMedia = { uri, kind, type, name, duration ->
                        if (preview) previewMessages = previewMessages + WhappyMessage("local-${System.currentTimeMillis()}", when (kind) { "audio" -> "Note vocale"; "video" -> "Vidéo"; else -> "Photo" }, "demo-user", System.currentTimeMillis(), kind, uri.toString(), name, duration)
                        else onSendMedia(uri, kind, type, name, duration)
                    },
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
                BrandHeader(
                    subtitle = if (preview) "Mode démonstration" else if (state.online) "Synchronisé en temps réel" else "Connexion limitée",
                    avatar = true,
                    name = accountDisplayName,
                    photoUrl = state.accountPhotoUrl,
                    unread = unreadActivity,
                    onActivity = { showActivityCenter = true },
                    onProfile = { onTab(WhappyTab.PROFILE) },
                )
                // Compose exactly one destination so avatars and messages never
                // shift while two media-heavy screens cross-fade.
                key(currentTab) {
            when (currentTab) {
                WhappyTab.MOMENTS -> MomentsScreen(state.twinProfile?.readiness ?: 0, onTab, onOpenWhappies = { showTwinStudio = true })
                WhappyTab.STORIES -> StoriesScreen(
                    statuses = state.statuses,
                    currentUserId = state.user?.uid ?: "demo-user",
                    currentUserName = accountDisplayName,
                    busy = state.actionBusy,
                    preview = preview,
                    onPublish = onPublishStatus,
                    onDelete = onDeleteStatus,
                )
                WhappyTab.CONTACTS -> MessagesScreen(
                    conversations = if (preview) demoConversations else state.conversations,
                    loading = state.loading,
                    preview = preview,
                    contactBusy = state.contactBusy,
                    contacts = if (preview) demoConversations.map { WhappyContact(it.peer) } else state.contacts,
                    contactSearchResult = state.contactSearchResult,
                    contactSearchPhone = state.contactSearchPhone,
                    contactSearchMessage = state.contactSearchMessage,
                    businessResults = if (preview) demoBusinessPages else state.businessSearchResults,
                    businessSearchBusy = state.businessSearchBusy,
                    channels = if (preview) demoChannels else state.channels,
                    currentUserId = state.user?.uid ?: "demo-user",
                    channelBusy = state.actionBusy,
                    initialQuery = state.discoveryQuery,
                    initialSection = 1,
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
                    conversations = if (preview) demoConversations else state.conversations,
                    loading = state.loading,
                    preview = preview,
                    contactBusy = state.contactBusy,
                            contacts = if (preview) demoConversations.map { WhappyContact(it.peer) } else state.contacts,
                            contactSearchResult = state.contactSearchResult,
                            contactSearchPhone = state.contactSearchPhone,
                            contactSearchMessage = state.contactSearchMessage,
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
                        WhappyTab.WEPI -> WepiScreen(
                            userName = accountDisplayName,
                            userId = state.user?.uid.orEmpty(),
                            settings = state.wepiSettings,
                            busy = state.actionBusy,
                            onSaveSettings = onSaveWepiSettings,
                            onOpenMessages = { onTab(WhappyTab.MESSAGES) },
                            onOpenBusiness = { onTab(WhappyTab.BUSINESS) },
                        )
                        WhappyTab.CALLS -> CallsScreen(
                            conversations = if (preview) demoConversations else state.conversations,
                            onOpenConversation = { if (preview) previewConversation = it else onOpenConversation(it) },
                        )
                        WhappyTab.MARKET -> MarketScreen(if (preview) demoListings else state.listings, preview, state.actionBusy, accountDisplayName, onPublishListing)
                        WhappyTab.LIVE -> LiveScreen(
                            lives = if (preview) demoLives else state.lives,
                            currentUserId = state.user?.uid ?: "demo-user",
                            preview = preview,
                            busy = state.actionBusy,
                            accountDisplayName = accountDisplayName,
                            onCreateLive = onCreateLive,
                            onEndLive = onEndLive,
                            onUpdateLiveStatus = onUpdateLiveStatus,
                            onOpenTwin = { showTwinStudio = true },
                        )
                        WhappyTab.RADIO -> RadioScreen(
                            accountDisplayName = accountDisplayName,
                            currentUserId = state.user?.uid.orEmpty(),
                            episodes = state.radioEpisodes,
                            busy = state.actionBusy,
                            onPublishEpisode = onPublishRadioEpisode,
                        )
                        WhappyTab.GAMES -> GamesScreen(currentUserId = state.user?.uid.orEmpty(), accountName = accountDisplayName, onBack = { onTab(WhappyTab.MOMENTS) })
                        WhappyTab.SERVICES -> ServicesScreen(
                            userName = accountDisplayName,
                            phone = state.user?.phoneNumber.orEmpty().ifBlank { "+242 06 000 00 00" },
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
                            onCreateCampaign = onCreateCampaign,
                            onCreateDeal = onCreateDeal,
                            onUpdateDealStatus = onUpdateDealStatus,
                            onMarkPaymentRead = onMarkPaymentRead,
                            onEnableNotifications = onEnableNotifications,
                            onOpenTwin = { showTwinStudio = true },
                        )
                        WhappyTab.PROFILE -> ProfileScreen(
                            name = accountDisplayName,
                            founder = isFounderAccount,
                            phone = state.user?.phoneNumber.orEmpty(),
                            photoUrl = state.accountPhotoUrl,
                            twinReadiness = state.twinProfile?.readiness ?: 0,
                            preview = preview,
                            busy = state.actionBusy,
                            onUpdatePhoto = onUpdateProfilePhoto,
                            onOpenWhappies = { showTwinStudio = true },
                            onSignOut = onSignOut,
                            onEnableNotifications = onEnableNotifications,
                            onOpenSpace = onTab,
                            language = appLanguage,
                            onLanguageChange = { next -> appLanguage = next; languagePrefs.edit().putString("code", next.code).apply() },
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun BrandHeader(subtitle: String, avatar: Boolean, name: String = "", photoUrl: String = "", unread: Int = 0, onActivity: () -> Unit = {}, onProfile: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().background(WhappyAurora).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.wapi_icon),
            "Logo WAPI",
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("WAPI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onActivity) { Box(contentAlignment = Alignment.TopEnd) { Icon(Icons.Rounded.Notifications, "Centre d’activité", tint = Color.White); if (unread > 0) Box(Modifier.size(16.dp).clip(CircleShape).background(WhappySky), contentAlignment = Alignment.Center) { Text(unread.coerceAtMost(9).toString(), color = WhappyDark, fontSize = 8.sp, fontWeight = FontWeight.Black) } } }
        if (avatar) UserAvatar(photoUrl, name, 44.dp, Modifier.clickable(onClick = onProfile))
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
                        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenLive), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5FAFD)), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .10f))) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(live.title, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1); Text("${live.hostName} · ${live.viewerCount} spectateurs", color = WhappyMuted, fontSize = 10.sp) }; Text("›", color = WhappyBlue, fontSize = 22.sp) } }
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
        WhappyTab.CALLS to Icons.Rounded.Phone,
        WhappyTab.MARKET to Icons.Rounded.Storefront,
        WhappyTab.LIVE to Icons.Rounded.LiveTv,
        WhappyTab.RADIO to Icons.Rounded.Radio,
        WhappyTab.GAMES to Icons.Rounded.Bolt,
        WhappyTab.SERVICES to Icons.Rounded.Payments,
        WhappyTab.BUSINESS to Icons.Rounded.BusinessCenter,
        WhappyTab.PROFILE to Icons.Rounded.Person,
    )
    val visibleTabs = listOf(WhappyTab.MOMENTS, WhappyTab.MESSAGES, WhappyTab.WEPI, WhappyTab.BUSINESS)
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        visibleTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { WhappySounds.haptic(context); onTab(tab) },
                icon = { Icon(icons.getValue(tab), mobileTabLabel(tab, LocalWhappyLanguage.current)) },
                label = { Text(mobileTabLabel(tab, LocalWhappyLanguage.current), fontSize = 10.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyBlue, selectedTextColor = WhappyDark, unselectedIconColor = WhappyMuted, unselectedTextColor = WhappyMuted, indicatorColor = WhappyBlue.copy(alpha = .10f)),
            )
        }
        NavigationBarItem(
            selected = selected !in visibleTabs,
            onClick = { WhappySounds.haptic(context); onMore() },
            icon = { Icon(Icons.Rounded.GridView, whappyText(LocalWhappyLanguage.current, "Tout", "All", "Nyonso")) },
            label = { Text(whappyText(LocalWhappyLanguage.current, "Tout", "All", "Nyonso"), fontSize = 10.sp, maxLines = 1) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyBlue, selectedTextColor = WhappyDark, unselectedIconColor = WhappyMuted, unselectedTextColor = WhappyMuted, indicatorColor = WhappyBlue.copy(alpha = .10f)),
        )
    }
}

private data class WhappyFeatureShortcut(val tab: WhappyTab?, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
private fun WhappyFeatureHubDialog(onOpen: (WhappyTab) -> Unit, onOpenTwin: () -> Unit, onDismiss: () -> Unit) {
    val shortcuts = listOf(
        WhappyFeatureShortcut(WhappyTab.CONTACTS, "Contacts", "Personnes et QR", Icons.Rounded.PersonAdd),
        WhappyFeatureShortcut(WhappyTab.CALLS, "Appels", "Audio et vidéo", Icons.Rounded.Phone),
        WhappyFeatureShortcut(WhappyTab.MARKET, "Marché", "Acheter et vendre", Icons.Rounded.Storefront),
        WhappyFeatureShortcut(WhappyTab.RADIO, "Radio", "Créer une émission", Icons.Rounded.Radio),
        WhappyFeatureShortcut(WhappyTab.GAMES, "Jeux", "Défis et tournois", Icons.Rounded.Bolt),
        WhappyFeatureShortcut(WhappyTab.SERVICES, "Services", "Paiements et outils", Icons.Rounded.Payments),
        WhappyFeatureShortcut(WhappyTab.BUSINESS, "Business", "Pages, Deals et Ads", Icons.Rounded.BusinessCenter),
        WhappyFeatureShortcut(null, "Mon WAPI", "Double et studio", Icons.Rounded.SmartToy),
        WhappyFeatureShortcut(WhappyTab.PROFILE, "Profil", "Compte et sécurité", Icons.Rounded.Person),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 22.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GridView, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Tout WAPI", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Les mêmes espaces essentiels que sur le Web", color = WhappyMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = WhappyMuted) }
                }
                shortcuts.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        rowItems.forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f).height(108.dp).clickable { item.tab?.let(onOpen) ?: onOpenTwin() },
                                color = Color.White,
                                shape = RoundedCornerShape(19.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .14f)),
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
}

private fun mobileTabLabel(tab: WhappyTab, language: WhappyLanguage): String = when (tab) {
    WhappyTab.MOMENTS -> whappyText(language, "Accueil", "Home", "Ndako")
    WhappyTab.STORIES -> whappyText(language, "Stories", "Stories", "Ba Stories")
    WhappyTab.MESSAGES -> whappyText(language, "Messages", "Messages", "Nsango")
    WhappyTab.WEPI -> "WEPI"
    WhappyTab.LIVE -> whappyText(language, "Live", "Live", "Na bomoi")
    WhappyTab.PROFILE -> whappyText(language, "Profil", "Profile", "Profil")
    else -> tab.label
}

@Composable
private fun StoriesScreen(
    statuses: List<WhappyStatus>,
    currentUserId: String,
    currentUserName: String,
    busy: Boolean,
    preview: Boolean,
    onPublish: (String, String, Uri?, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf("community") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("") }
    var mediaName by remember { mutableStateOf("") }
    var previewStatuses by remember { mutableStateOf(emptyList<WhappyStatus>()) }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val selectedName = displayName(context, uri)
            val extension = selectedName.substringAfterLast('.', "").lowercase()
            val contentType = context.contentResolver.getType(uri).orEmpty().ifBlank {
                when (extension) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
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
    }
    val visibleStatuses = if (preview) previewStatuses else statuses
    val toneOptions = listOf(
        "community" to t("Communauté", "Community", "Lisanga"),
        "hope" to t("Positif", "Positive", "Elikya"),
        "action" to t("Action", "Action", "Mosala"),
        "warning" to t("Important", "Important", "Ntina"),
    )

    LazyColumn(
        Modifier.fillMaxSize().background(WhappySurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(WhappyAurora).padding(22.dp)) {
                Column {
                    Text("WAPI STORIES", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text(t("Partagez votre Story.", "Share your Story.", "Kabola Story na yo."), Modifier.padding(top = 7.dp), color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                    Text(t("Une photo, une vidéo, un moment. Tout reste synchronisé avec vos contacts.", "A photo, a video, a moment. Everything stays synced with your contacts.", "Elilingi, video, ntango. Nyonso ekotikala synchronisé na ba contacts na yo."), Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = .82f), lineHeight = 18.sp, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar("", currentUserName, 46.dp)
                        Column(Modifier.padding(start = 11.dp)) { Text(currentUserName, color = WhappyDark, fontWeight = FontWeight.Black); Text(t("Nouvelle Story", "New Story", "Story ya sika"), color = WhappyMuted, fontSize = 10.sp) }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(600) },
                        modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                        placeholder = { Text(t("Que voulez-vous partager ?", "What would you like to share?", "Olingi kokabola nini?")) },
                        minLines = 4,
                        shape = RoundedCornerShape(17.dp),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable(enabled = !busy) { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) },
                        shape = RoundedCornerShape(16.dp),
                        color = WhappyBlue.copy(alpha = .055f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .18f)),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (mediaType.startsWith("audio/")) Icons.Rounded.AudioFile else if (mediaType.startsWith("video/")) Icons.Rounded.Movie else Icons.Rounded.Photo, null, tint = WhappyBlue)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(if (mediaUri == null) t("Ajouter photo, vidéo ou podcast", "Add photo, video or podcast", "Bakisa elilingi, video to podcast") else mediaName, color = WhappyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(if (mediaUri == null) "Images · vidéos · audio · lien dans le texte" else t("Prêt à publier", "Ready to publish", "Ebongi mpo na kotinda"), color = WhappyMuted, fontSize = 9.sp)
                            }
                            if (mediaUri != null) IconButton(onClick = { mediaUri = null; mediaType = ""; mediaName = "" }) { Icon(Icons.Rounded.Close, t("Retirer", "Remove", "Longola"), tint = WhappyBlue) }
                            else Icon(Icons.Rounded.Add, null, tint = WhappyBlue)
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        toneOptions.forEach { option ->
                            OutlinedButton(
                                onClick = { tone = option.first },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (tone == option.first) WhappyBlue else Color.White, contentColor = if (tone == option.first) Color.White else WhappyBlue),
                                shape = RoundedCornerShape(13.dp),
                            ) { Text(option.second, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Button(
                        enabled = (draft.trim().isNotEmpty() || mediaUri != null) && !busy,
                        onClick = {
                            val value = draft.trim()
                            if (preview) previewStatuses = listOf(WhappyStatus("local-${System.currentTimeMillis()}", currentUserId, currentUserName, value, tone, System.currentTimeMillis(), mediaUri?.toString().orEmpty(), if (mediaType.startsWith("audio/")) "audio" else if (mediaType.startsWith("video/")) "video" else if (mediaUri != null) "image" else "text", mediaName)) + previewStatuses
                            else onPublish(value, tone, mediaUri, mediaType)
                            draft = ""
                            mediaUri = null
                            mediaType = ""
                            mediaName = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                        else { Icon(Icons.AutoMirrored.Rounded.Send, null); Text(t("  Publier la Story", "  Publish Story", "  Tinda Story"), fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
        item { Text(t("Stories récentes", "Recent Stories", "Ba Stories ya sika"), color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black) }
        if (visibleStatuses.isEmpty()) item { EmptyState(t("Aucune Story publiée", "No Story yet", "Story moko te"), t("Votre première publication apparaîtra ici.", "Your first post will appear here.", "Story na yo ya liboso ekomonana awa.")) }
        items(visibleStatuses, key = { it.id }) { status ->
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar("", status.authorName, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(status.authorName, color = WhappyDark, fontWeight = FontWeight.Black); Text(formatTime(status.createdAt), color = WhappyMuted, fontSize = 10.sp) }
                        if (status.authorId == currentUserId) IconButton(onClick = { if (preview) previewStatuses = previewStatuses.filterNot { it.id == status.id } else onDelete(status.id) }, enabled = !busy) { Icon(Icons.Rounded.Delete, t("Supprimer", "Delete", "Longola"), tint = WhappyBlue) }
                    }
                    if (status.text.isNotBlank()) Text(status.text, Modifier.padding(top = 14.dp), color = WhappyDark, fontSize = 16.sp, lineHeight = 23.sp)
                    if (status.mediaUrl.isNotBlank()) {
                        StoryMediaPreview(status.mediaUrl, status.mediaKind, status.mediaName) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(status.mediaUrl))) }
                        }
                    }
                    Text(toneOptions.firstOrNull { it.first == status.tone }?.second.orEmpty().uppercase(), Modifier.padding(top = 12.dp), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private data class WepiChatMessage(val text: String, val fromUser: Boolean)

@Composable
private fun WepiScreen(
    userName: String,
    userId: String,
    settings: WapiWepiSettings?,
    busy: Boolean,
    onSaveSettings: (WapiWepiSettings) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenBusiness: () -> Unit,
) {
    val context = LocalContext.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val activeSettings = settings ?: WapiWepiSettings(ownerId = userId)
    var messages by remember {
        mutableStateOf(listOf(WepiChatMessage("Bonjour ${userName.substringBefore(' ').ifBlank { "Cyril" }}. Je suis ${activeSettings.assistantName}, le moteur WEPI Pilotis intégré à WAPI.", false)))
    }
    val listState = rememberLazyListState()
    fun submit(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        messages = messages + WepiChatMessage(clean, true) + WepiChatMessage(WapiPilotis.reply(clean, activeSettings, userName), false)
        prompt = ""
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().background(WapiChatBackground)) {
        Column(Modifier.fillMaxWidth().background(WhappyAurora).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SmartToy, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(activeSettings.assistantName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("Moteur Pilotis · Intelligence privée WAPI", color = Color.White.copy(alpha = .76f), fontSize = 11.sp) }
                Text(if (activeSettings.enabled) "ACTIF" else "EN PAUSE", color = if (activeSettings.enabled) Color.White else Color.White.copy(alpha = .62f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Text("Un assistant pour passer de l’idée à l’action.", Modifier.padding(top = 14.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Surface(Modifier.fillMaxWidth().clickable { showSettings = true }, color = Color.White, shadowElevation = 2.dp) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SmartToy, null, tint = WhappyBlue)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text("Pilotis synchronisé", color = WhappyDark, fontWeight = FontWeight.Black); Text("Même configuration WEPI sur le Web et Android", color = WhappyMuted, fontSize = 10.sp) }
                Text("RÉGLER ›", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Répondre à un message", "Créer une campagne", "Organiser un groupe", "Préparer une Story").forEach { suggestion ->
                OutlinedButton(onClick = { submit(suggestion) }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)) { Text(suggestion, fontSize = 10.sp) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            itemsIndexed(messages) { _, message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                    if (!message.fromUser) Box(Modifier.padding(end = 7.dp).size(30.dp).clip(RoundedCornerShape(10.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Text("W", color = Color.White, fontWeight = FontWeight.Black) }
                    Surface(color = if (message.fromUser) WapiBubbleOutgoing else Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth(.78f)) {
                        Text(message.text, Modifier.padding(13.dp), color = WhappyInk, lineHeight = 19.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(prompt, { value -> if (value.length > prompt.length) WhappySounds.typing(context); prompt = value.take(1200) }, Modifier.weight(1f), placeholder = { Text("Demandez à WEPI…") }, maxLines = 4, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit(prompt) }))
            FilledIconButton(onClick = { submit(prompt) }, enabled = prompt.isNotBlank(), modifier = Modifier.padding(start = 7.dp).size(50.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onOpenMessages) { Text("Messages") }
            TextButton(onClick = onOpenBusiness) { Text("Business") }
        }
    }
    if (showSettings) WepiPilotisSettingsDialog(activeSettings, busy, onDismiss = { showSettings = false }, onSave = { onSaveSettings(it); showSettings = false })
}

@Composable
private fun WepiPilotisSettingsDialog(settings: WapiWepiSettings, busy: Boolean, onDismiss: () -> Unit, onSave: (WapiWepiSettings) -> Unit) {
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var autoReply by remember(settings) { mutableStateOf(settings.autoReply) }
    var assistantName by remember(settings) { mutableStateOf(settings.assistantName) }
    var businessName by remember(settings) { mutableStateOf(settings.businessName) }
    var tone by remember(settings) { mutableStateOf(settings.tone) }
    var welcome by remember(settings) { mutableStateOf(settings.welcomeMessage) }
    var instructions by remember(settings) { mutableStateOf(settings.instructions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Text("WEPI Pilotis", fontWeight = FontWeight.Black); Text("Configuration cloud du compte", color = WhappyMuted, fontSize = 10.sp) } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Activer WEPI", fontWeight = FontWeight.Bold); Text("Assistant disponible dans WAPI", color = WhappyMuted, fontSize = 10.sp) }; Switch(enabled, { enabled = it }) } }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Réponse automatique", fontWeight = FontWeight.Bold); Text("Toujours sous vos consignes", color = WhappyMuted, fontSize = 10.sp) }; Switch(autoReply, { autoReply = it }, enabled = enabled) } }
                item { OutlinedTextField(assistantName, { assistantName = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Nom de l’assistant") }, singleLine = true) }
                item { OutlinedTextField(businessName, { businessName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Nom du business") }, singleLine = true) }
                item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("chaleureux", "expert", "direct").forEach { option -> OutlinedButton(onClick = { tone = option }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (tone == option) WhappyBlue else Color.Transparent, contentColor = if (tone == option) Color.White else WhappyBlue)) { Text(option.replaceFirstChar { it.uppercase() }) } } } }
                item { OutlinedTextField(welcome, { welcome = it.take(240) }, Modifier.fillMaxWidth(), label = { Text("Message d’accueil") }, minLines = 2) }
                item { OutlinedTextField(instructions, { instructions = it.take(600) }, Modifier.fillMaxWidth(), label = { Text("Consignes Pilotis") }, minLines = 3) }
            }
        },
        confirmButton = { Button(enabled = !busy && assistantName.trim().length >= 2, onClick = { onSave(settings.copy(enabled = enabled, autoReply = autoReply, assistantName = assistantName, businessName = businessName, tone = tone, welcomeMessage = welcome, instructions = instructions)) }) { Text(if (busy) "Synchronisation…" else "Enregistrer") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annuler") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun MomentsScreen(twinReadiness: Int, onTab: (WhappyTab) -> Unit, onOpenWhappies: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_consumer") }
    var composing by rememberSaveable { mutableStateOf(false) }
    var momentTitle by rememberSaveable { mutableStateOf("") }
    var momentBody by rememberSaveable { mutableStateOf("") }
    var personalMoments by remember { mutableStateOf(prefs.getStringSet("moments", emptySet()).orEmpty().toList().sortedDescending()) }
    LazyColumn(Modifier.fillMaxSize().background(WhappySurface), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(WhappyAurora).padding(22.dp)) {
                Column {
                    Text("WAPI BLUE", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                    Text("Votre monde,\nplus vivant.", color = Color.White, fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    Text("Messages, appels, Business et intelligence WAPI dans une expérience stable et lumineuse.", color = Color.White.copy(alpha = .82f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onTab(WhappyTab.MESSAGES) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.ChatBubble, null, modifier = Modifier.size(17.dp)); Text(" Messages", fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = { onTab(WhappyTab.WEPI) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .65f)), shape = RoundedCornerShape(14.dp)) { Text("WEPI IA", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable { onTab(WhappyTab.LIVE) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = WhappyBlue, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("WAPI Live", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Découvrir les directs en cours", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
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
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Mon Double", color = WhappyDark, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Créer votre double virtuel", color = WhappyMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                    Text("›", color = WhappyBlue, fontSize = 24.sp)
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
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Moments personnels", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhappyDark); TextButton(onClick = { composing = true }) { Icon(Icons.Rounded.Add, null); Text("Publier") } } }
        items(personalMoments, key = { it }) { raw ->
            val parts = raw.split("|", limit = 3)
            MomentCard("Vous", "MON MOMENT", parts.getOrElse(1) { "Nouveau moment" }, parts.getOrElse(2) { "" })
        }
        if (personalMoments.isEmpty()) item { EmptyState("Aucun moment publié", "Vos publications personnelles apparaîtront ici.") }
    }
    if (composing) AlertDialog(
        onDismissRequest = { composing = false },
        title = { Text("Créer un Moment", fontWeight = FontWeight.Black) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(momentTitle, { momentTitle = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Titre") }, singleLine = true); OutlinedTextField(momentBody, { momentBody = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Votre moment") }, minLines = 4); Text("Votre publication restera disponible dans l’accueil de cette application.", color = WhappyMuted, fontSize = 11.sp) } },
        confirmButton = { Button(enabled = momentTitle.trim().length >= 2 && momentBody.trim().length >= 3, onClick = { val entry = "${System.currentTimeMillis()}|${momentTitle.trim().replace("|", " ")}|${momentBody.trim().replace("|", " ")}"; personalMoments = (listOf(entry) + personalMoments).take(20); prefs.edit().putStringSet("moments", personalMoments.toSet()).apply(); momentTitle = ""; momentBody = ""; composing = false }) { Text("Publier") } },
        dismissButton = { TextButton(onClick = { composing = false }) { Text("Annuler") } },
    )
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
    onPublishEpisode: (String, String, Uri, Long) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_native_radio") }
    var stationName by rememberSaveable { mutableStateOf(prefs.getString("station_name", "Radio de $accountDisplayName").orEmpty()) }
    var topic by rememberSaveable { mutableStateOf(prefs.getString("station_topic", "Actualité, culture et communauté").orEmpty()) }
    var broadcasting by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var pausedAt by remember { mutableStateOf(0L) }
    var totalPausedMillis by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var lastRecording by remember { mutableStateOf<File?>(null) }
    var lastDuration by rememberSaveable { mutableStateOf(0L) }
    var activeEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var micProfile by rememberSaveable { mutableStateOf(WapiMicProfile.entries.firstOrNull { it.name == prefs.getString("mic_profile", WapiMicProfile.BROADCAST.name) } ?: WapiMicProfile.BROADCAST) }
    var inputLevel by remember { mutableStateOf(0f) }
    var monitoring by rememberSaveable { mutableStateOf(false) }
    val playback = remember { WapiRadioPlayback() }
    val effectSupport = remember { detectRadioEffectSupport() }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var showScheduleDialog by rememberSaveable { mutableStateOf(false) }
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
        playback.stop(); monitoring = false; activeEpisodeId = null
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
            playback.release()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(WapiChatBackground),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 34.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    TextButton(onClick = ::saveIdentity, modifier = Modifier.align(Alignment.End), colors = ButtonDefaults.textButtonColors(contentColor = WhappySky)) { Text("Enregistrer") }
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
                                    if (paused) runCatching { recorder?.resume() }.onSuccess { totalPausedMillis += (System.currentTimeMillis() - pausedAt).coerceAtLeast(0L); paused = false; feedback = "Le direct reprend." }
                                    else runCatching { recorder?.pause() }.onSuccess { pausedAt = System.currentTimeMillis(); paused = true; feedback = "Le direct est en pause." }
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
                        IconButton(onClick = { if (monitoring) { playback.stop(); monitoring = false } else { monitoring = true; playback.play(file, micProfile) { monitoring = false } } }) { Icon(if (monitoring) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (monitoring) "Arrêter l’écoute" else "Écouter", tint = WhappyBlue) }
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
                            if (activeEpisodeId == episode.id) { playback.stop(); activeEpisodeId = null }
                            else { activeEpisodeId = episode.id; monitoring = false; playback.playUrl(episode.audioUrl, micProfile, onFinished = { activeEpisodeId = null }) }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue),
                    ) { Icon(if (activeEpisodeId == episode.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "Écouter ${episode.title}", tint = Color.White) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(episode.title, color = WhappyDark, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${episode.stationName} · ${episode.authorName}", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${formatRadioDuration(episode.durationSeconds)} · publication cloud", color = WhappyMuted, fontSize = 9.sp)
                    }
                    if (episode.ownerId == currentUserId) Text("VOUS", color = WapiVerifiedGray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Programmation", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Préparez les prochaines émissions", color = WhappyMuted, fontSize = 11.sp) }
                Button(onClick = { showScheduleDialog = true }, shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Add, null); Text("  Ajouter") }
            }
        }
        if (programmes.isEmpty()) {
            item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Schedule, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)); Text("Aucune émission programmée", Modifier.padding(top = 9.dp), color = WhappyDark, fontWeight = FontWeight.Bold); Text("Ajoutez un titre et une heure pour commencer.", color = WhappyMuted, fontSize = 11.sp) } } }
        } else {
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

@Composable
private fun GamesScreen(currentUserId: String, accountName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val gamePrefs = remember { WhappyFastStorage.preferences(context, "wapi_play") }
    var selected by rememberSaveable { mutableStateOf("Ludo WAPI") }
    var gameOpen by rememberSaveable { mutableStateOf(false) }
    var xp by rememberSaveable { mutableStateOf(gamePrefs.getInt("xp", 0)) }
    var wins by rememberSaveable { mutableStateOf(gamePrefs.getInt("wins", 0)) }
    var dice by rememberSaveable { mutableStateOf(0) }
    var activePlayer by rememberSaveable { mutableStateOf(0) }
    var ludoPositions by rememberSaveable { mutableStateOf(listOf(-1, -1, -1, -1)) }
    var ludoLog by rememberSaveable { mutableStateOf("Lancez le dé. Un 6 fait sortir votre pion de la maison.") }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    var round by rememberSaveable { mutableStateOf(1) }
    val games = listOf(
        Triple("Ludo WAPI", "Partie locale · dé, pions et captures", "🎲"),
        Triple("WAPI Sky", "Course interactive · réflexes et progression", "🚀"),
        Triple("Billard WAPI", "Table physique · visée, puissance et collisions", "🎱"),
        Triple("Échecs", "Duel stratégique · plateau interactif", "♚"),
        Triple("Jeu de dames", "Captures diagonales et couronnement", "⛀"),
        Triple("Cartes WAPI", "Bataille rapide · manches et score", "🂡"),
        Triple("Poker WAPI", "Texas Hold’em · IA locale · sans argent réel", "♠"),
        Triple("Défi du jour", "Quiz rapide · gagnez de l’XP", "⚡"),
        Triple("Duel WAPI", "Défi local entre joueurs", "♟"),
        Triple("Mots & idées", "Trouvez la solution ensemble", "✦"),
    )
    val ludoPlayers = listOf(
        Triple("Vous", Color(0xFFE53935), "🔴"),
        Triple("Joueur 2", Color(0xFF1E88E5), "🔵"),
        Triple("Joueur 3", Color(0xFF43A047), "🟢"),
        Triple("Joueur 4", Color(0xFFF9A825), "🟡"),
    )

    LaunchedEffect(xp, wins) { gamePrefs.edit().putInt("xp", xp).putInt("wins", wins).apply() }

    fun nextPlayer(from: Int = activePlayer): Int = (from + 1) % ludoPlayers.size

    fun resetLudo() {
        ludoPositions = listOf(-1, -1, -1, -1)
        dice = 0
        activePlayer = 0
        ludoLog = "Nouvelle partie. Sortez sur 6, capturez les adversaires et atteignez l’arrivée."
    }

    fun playLudoTurn() {
        WhappySounds.dice()
        WhappySounds.haptic(context)
        val roll = ((System.currentTimeMillis() / 37L) % 6L).toInt() + 1
        dice = roll
        val player = activePlayer
        val playerName = ludoPlayers[player].first
        val current = ludoPositions[player]
        val starts = listOf(0, 13, 26, 39)
        val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)
        val nextPositions = ludoPositions.toMutableList()
        var message: String
        var extraTurn = roll == 6

        if (current == -1) {
            if (roll == 6) {
                nextPositions[player] = 0
                message = "$playerName sort de la maison avec un 6."
            } else {
                message = "$playerName lance $roll. Il faut un 6 pour sortir."
                extraTurn = false
            }
        } else {
            val target = current + roll
            if (target > 52) {
                message = "$playerName lance $roll, mais doit tomber exactement sur l’arrivée."
                extraTurn = false
            } else {
                nextPositions[player] = target
                message = if (target == 52) "$playerName atteint l’arrivée !" else "$playerName avance de $roll case${if (roll > 1) "s" else ""}."
                if (target == 52 && player == 0) {
                    wins += 1
                    xp += 150
                    WhappySounds.reward()
                    WhappySounds.haptic(context, strong = true)
                    extraTurn = false
                }
                if (target in 0..51) {
                    val absolute = (starts[player] + target) % 52
                    ludoPositions.forEachIndexed { index, position ->
                        if (index != player && position in 0..51) {
                            val opponentAbsolute = (starts[index] + position) % 52
                            if (opponentAbsolute == absolute && absolute !in safeSquares) {
                                nextPositions[index] = -1
                                message += " Capture de ${ludoPlayers[index].first} !"
                                if (player == 0) xp += 40
                                WhappySounds.impact()
                            }
                        }
                    }
                }
            }
        }
        ludoPositions = nextPositions
        if (player == 0 && roll == 6) xp += 10
        activePlayer = if (extraTurn && nextPositions[player] != 52) player else nextPlayer(player)
        ludoLog = message + if (extraTurn && nextPositions[player] != 52) " Rejouez." else " Tour suivant : ${ludoPlayers[activePlayer].first}."
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Bolt, null, tint = WhappyBlue); Text("  WAPI PLAY", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                    Text("Jouez pour de vrai.\nAvec vos proches.", Modifier.padding(top = 10.dp), color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                    Text("Ludo local, défis et mini-jeux prêts à évoluer vers le multijoueur WAPI.", Modifier.padding(top = 8.dp), color = Color.White, lineHeight = 19.sp)
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Victoires", wins.toString())
                        StatPill("XP", "$xp")
                        StatPill("Mode", if (selected.startsWith("Ludo")) "Ludo" else "Arcade")
                    }
                }
            }
        }
        item { Text("Choisir un jeu", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black) }
        items(games, key = { it.first }) { game ->
            GameModeCard(game.first, game.second, game.third, selected == game.first) { selected = game.first; gameOpen = true }
        }
        if (selected == "Ludo WAPI") {
            item { WapiOnlineLudoCard(userId = currentUserId, userName = accountName) }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("LUDO WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                Text("Tour de ${ludoPlayers[activePlayer].first}", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                Text("Dé : ${if (dice == 0) "—" else dice} · 6 = sortie/rejouer", color = WhappyMuted, fontSize = 11.sp)
                            }
                            val diceRotation by animateFloatAsState(targetValue = dice * 74f, animationSpec = spring(dampingRatio = .58f, stiffness = 420f), label = "dice")
                            Box(Modifier.size(62.dp).graphicsLayer { rotationX = diceRotation; rotationY = diceRotation * .72f; shadowElevation = 18f; cameraDistance = 18f }.clip(RoundedCornerShape(18.dp)).background(WhappyAurora), contentAlignment = Alignment.Center) {
                                Text(if (dice == 0) "🎲" else dice.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        LudoBoard(players = ludoPlayers, positions = ludoPositions)
                        LudoStatus(players = ludoPlayers, positions = ludoPositions)
                        Text(ludoLog, color = WhappyInk, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = ::playLudoTurn, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Lancer le dé", fontWeight = FontWeight.Black) }
                            OutlinedButton(onClick = ::resetLudo, modifier = Modifier.weight(.75f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Rejouer") }
                        }
                    }
                }
            }
        } else if (selected == "WAPI Sky") {
            item { SkyRun3D(onXp = { gained -> xp += gained }, onWin = { wins += 1 }) }
        } else if (selected == "Billard WAPI") {
            item { Billiards3D(onXp = { xp += it }, onWin = { wins += 1 }) }
        } else if (selected == "Échecs") {
            item { StrategyBoardGame(checkers = false, onXp = { xp += it }, onWin = { wins += 1 }) }
        } else if (selected == "Jeu de dames") {
            item { StrategyBoardGame(checkers = true, onXp = { xp += it }, onWin = { wins += 1 }) }
        } else if (selected == "Cartes WAPI") {
            item { WapiCardDuel(onXp = { xp += it }, onWin = { wins += 1 }) }
        } else if (selected == "Poker WAPI") {
            item { WapiPokerTable(onXp = { xp += it }, onWin = { wins += 1 }) }
        } else {
            item {
                ArcadeChallengeCard(
                    selected = selected,
                    round = round,
                    answer = answer,
                    onAnswer = { option ->
                        answer = option
                        if (option == "Le Live") { xp += 25; WhappySounds.reward() } else WhappySounds.impact()
                        WhappySounds.haptic(context, strong = option == "Le Live")
                    },
                    onNext = {
                        round += 1
                        answer = null
                    },
                )
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Retour à l’accueil") } }
    }
    if (gameOpen) {
        Dialog(onDismissRequest = { gameOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = WapiChatBackground) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { gameOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quitter le jeu") }
                        Column(Modifier.weight(1f)) { Text(selected, color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black); Text("PARTIE LOCALE WAPI", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        Text("XP $xp", color = WhappyBlue, fontWeight = FontWeight.Black)
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (selected) {
                            "Ludo WAPI" -> item {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Tour de ${ludoPlayers[activePlayer].first}", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Dé : ${if (dice == 0) "—" else dice}", color = WhappyMuted) }; Text(if (dice == 0) "🎲" else dice.toString(), fontSize = 31.sp) }
                                        LudoBoard(players = ludoPlayers, positions = ludoPositions)
                                        LudoStatus(players = ludoPlayers, positions = ludoPositions)
                                        Text(ludoLog, color = WhappyInk, lineHeight = 19.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { Button(onClick = ::playLudoTurn, Modifier.weight(1f).height(52.dp)) { Text("Lancer le dé", fontWeight = FontWeight.Black) }; OutlinedButton(onClick = ::resetLudo, Modifier.weight(.7f).height(52.dp)) { Text("Rejouer") } }
                                    }
                                }
                            }
                            "WAPI Sky" -> item { SkyRun3D(onXp = { xp += it }, onWin = { wins += 1 }) }
                            "Billard WAPI" -> item { Billiards3D(onXp = { xp += it }, onWin = { wins += 1 }) }
                            "Échecs" -> item { StrategyBoardGame(checkers = false, onXp = { xp += it }, onWin = { wins += 1 }) }
                            "Jeu de dames" -> item { StrategyBoardGame(checkers = true, onXp = { xp += it }, onWin = { wins += 1 }) }
                            "Cartes WAPI" -> item { WapiCardDuel(onXp = { xp += it }, onWin = { wins += 1 }) }
                            "Poker WAPI" -> item { WapiPokerTable(onXp = { xp += it }, onWin = { wins += 1 }) }
                            else -> item { ArcadeChallengeCard(selected, round, answer, onAnswer = { option -> answer = option; if (option == "Le Live") xp += 25 }, onNext = { round += 1; answer = null }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameModeCard(title: String, subtitle: String, icon: String, active: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (active) WhappyBlue else Color.White), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 22.sp, color = if (active) Color.White else WhappyBlue)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(subtitle, color = WhappyMuted, fontSize = 11.sp)
            }
            Text(if (active) "EN COURS" else "JOUER ›", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
            val position = positions[index]
            val label = when {
                position < 0 -> "Maison"
                position >= 52 -> "Arrivé"
                else -> "Case $position/52"
            }
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
    var lane by rememberSaveable { mutableStateOf(1) }
    var obstacleLane by rememberSaveable { mutableStateOf(0) }
    var distance by rememberSaveable { mutableStateOf(0) }
    var energy by rememberSaveable { mutableStateOf(3) }
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
                WhappySounds.move()
                message = if (distance >= 20) "Vitesse MAX · gardez le cap !" else "Parfait · +5 XP"
                if (distance == 20) { onWin(); WhappySounds.reward() }
            }
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WAPI SKY ENGINE", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("Course WAPI", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(14.dp)) { Text("⚡ $energy  ·  $distance m", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Black) }
            }
            Box(
                Modifier.fillMaxWidth().height(310.dp).graphicsLayer { rotationX = 5f; cameraDistance = 24f; shadowElevation = 22f }
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
                Button(onClick = { if (running) running = false else { if (energy <= 0) { energy = 3; distance = 0 }; running = true; message = "Mission lancée · évitez les blocs."; WhappySounds.reward() } }, Modifier.weight(1.2f), shape = RoundedCornerShape(14.dp)) { Text(if (running) "PAUSE" else "DÉMARRER", fontWeight = FontWeight.Black) }
                OutlinedButton(enabled = running && lane < 2, onClick = { lane += 1; WhappySounds.haptic(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp)) { Text("DROITE →") }
            }
        }
    }
}

@Composable
private fun Billiards3D(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var aim by rememberSaveable { mutableStateOf(0) }
    var power by rememberSaveable { mutableStateOf(2) }
    var shots by rememberSaveable { mutableStateOf(0) }
    var balls by rememberSaveable { mutableStateOf(9) }
    var score by rememberSaveable { mutableStateOf(0) }
    var message by rememberSaveable { mutableStateOf("Réglez l’angle et la puissance, puis frappez la bille blanche.") }
    val ballRotation by animateFloatAsState(targetValue = shots * 115f, animationSpec = spring(stiffness = 180f), label = "pool-ball")
    fun shoot() {
        shots += 1
        WhappySounds.dice(); WhappySounds.haptic(context)
        val pocketed = ((aim + power * 3 + shots) % 4 == 0) || power == 4
        if (pocketed && balls > 0) {
            balls -= 1; score += 100; onXp(15); WhappySounds.reward(); message = "Bille empochée · +100 points"
            if (balls == 0) { onWin(); onXp(150); message = "TABLE NETTOYÉE · victoire !" }
        } else { score = (score - 10).coerceAtLeast(0); message = "La bille touche la bande. Ajustez votre angle." }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF072D25))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("WAPI BILLIARDS", color = Color(0xFF72F2C8), fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Table interactive", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black) }; Text("$score pts · $balls billes", color = Color.White, fontWeight = FontWeight.Bold) }
            BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).graphicsLayer { rotationX = 9f; rotationY = -2f; cameraDistance = 22f; shadowElevation = 28f }.clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF07845F), Color(0xFF034C3B))))) {
                listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd, Alignment.TopCenter, Alignment.BottomCenter).forEach { alignment -> Box(Modifier.align(alignment).padding(3.dp).size(24.dp).clip(CircleShape).background(Color(0xFF021D17))) }
                repeat(balls) { index ->
                    val row = index / 4; val col = index % 4
                    Box(Modifier.offset(x = maxWidth * (.50f + col * .075f), y = (98 + row * 34).dp).size(27.dp).graphicsLayer { rotationX = ballRotation + index * 9f; rotationY = ballRotation; shadowElevation = 14f }.clip(CircleShape).background(listOf(Color(0xFFFFC928), Color(0xFFE53935), Color(0xFF236DE8), Color(0xFF7C3AED))[index % 4]), contentAlignment = Alignment.Center) { Text((index + 1).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                }
                Box(Modifier.offset(x = maxWidth * (.15f + aim * .035f), y = (190 - power * 12).dp).size(30.dp).graphicsLayer { rotationX = ballRotation; rotationY = ballRotation * .7f; shadowElevation = 18f }.clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("W", color = WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                Text("ANGLE ${aim * 6}°  ·  PUISSANCE $power/4", Modifier.align(Alignment.TopCenter).padding(top = 16.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = .30f)).padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Text(message, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { aim = (aim - 1).coerceAtLeast(-4) }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("← VISER") }; OutlinedButton(onClick = { power = if (power == 4) 1 else power + 1 }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("FORCE $power") }; OutlinedButton(onClick = { aim = (aim + 1).coerceAtMost(4) }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("VISER →") } }
            Button(onClick = ::shoot, enabled = balls > 0, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("FRAPPER", fontWeight = FontWeight.Black) }
        }
    }
}

private fun initialStrategyBoard(checkers: Boolean): List<String> = if (checkers) List(64) { index ->
    val row = index / 8; val col = index % 8
    if ((row + col) % 2 == 1 && row < 3) "b" else if ((row + col) % 2 == 1 && row > 4) "w" else ""
} else listOf("♜","♞","♝","♛","♚","♝","♞","♜") + List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } + listOf("♖","♘","♗","♕","♔","♗","♘","♖")

private fun whitePiece(piece: String) = WapiGameRules.isWhite(piece)
private fun blackPiece(piece: String) = WapiGameRules.isBlack(piece)

@Composable
private fun StrategyBoardGame(checkers: Boolean, onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var board by rememberSaveable(checkers) { mutableStateOf(initialStrategyBoard(checkers)) }
    var selected by rememberSaveable(checkers) { mutableStateOf(-1) }
    var whiteTurn by rememberSaveable(checkers) { mutableStateOf(true) }
    var versusAi by rememberSaveable(checkers) { mutableStateOf(true) }
    var aiThinking by rememberSaveable(checkers) { mutableStateOf(false) }
    var message by rememberSaveable(checkers) { mutableStateOf(if (checkers) "Vous jouez les blancs contre l’IA. Capturez en diagonale." else "Vous jouez les blancs contre l’IA. Sélectionnez une pièce puis une case.") }
    fun choose(index: Int) {
        if (!whiteTurn || aiThinking) return
        val piece = board[index]
        if (selected < 0) {
            if ((whiteTurn && whitePiece(piece)) || (!whiteTurn && blackPiece(piece))) { selected = index; WhappySounds.haptic(context) }
            return
        }
        val from = selected
        if ((whiteTurn && whitePiece(piece)) || (!whiteTurn && blackPiece(piece))) { selected = index; return }
        val result = if (checkers) WapiGameRules.checkersMove(board, from, index, whiteTurn) else WapiGameRules.chessMove(board, from, index, whiteTurn)
        if (result == null) { message = if (checkers && WapiGameRules.hasCheckersCapture(board, whiteTurn)) "Une capture est disponible et devient prioritaire." else "Mouvement non autorisé ou roi exposé."; WhappySounds.impact(); selected = -1; return }
        board = result.board; selected = -1; whiteTurn = !whiteTurn
        onXp(if (result.captured) 12 else 3); WhappySounds.move(); message = when { result.promoted -> "Dame couronnée · +12 XP"; result.captured -> "Capture réussie · +12 XP"; else -> "À ${if (whiteTurn) "Blanc" else "Noir"} de jouer" }
        if (result.board.none(::blackPiece) || result.board.none(::whitePiece)) { onWin(); onXp(120); WhappySounds.reward(); message = "VICTOIRE · plateau maîtrisé" }
    }
    LaunchedEffect(board, whiteTurn, versusAi) {
        if (versusAi && !whiteTurn && !aiThinking) {
            aiThinking = true
            message = "L’IA analyse le plateau…"
            delay(420L)
            val move = WapiGameRules.bestMove(board, whiteTurn = false, checkers = checkers)
            if (move != null) {
                val result = if (checkers) WapiGameRules.checkersMove(board, move.first, move.second, false) else WapiGameRules.chessMove(board, move.first, move.second, false)
                if (result != null) {
                    board = result.board
                    whiteTurn = true
                    WhappySounds.move()
                    message = if (result.captured) "L’IA capture. À vous de jouer." else "À vous de jouer."
                }
            }
            aiThinking = false
        }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if (checkers) "WAPI DAMES" else "WAPI CHESS", color = WhappySky, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(if (checkers) "Jeu de dames" else "Échecs stratégiques", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black) }; Text(if (aiThinking) "IA…" else if (whiteTurn) "À VOUS" else "IA", color = Color.White, fontWeight = FontWeight.Black) }
        BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { rotationX = 6f; rotationY = -2f; cameraDistance = 24f; shadowElevation = 24f }.clip(RoundedCornerShape(16.dp))) { val cell = maxWidth / 8; Column { repeat(8) { row -> Row { repeat(8) { col -> val index = row * 8 + col; val piece = board[index]; Box(Modifier.size(cell).background(if (selected == index) WhappySky else if ((row + col) % 2 == 0) Color(0xFFEAF4FB) else Color(0xFF2875A7)).clickable { choose(index) }, contentAlignment = Alignment.Center) { if (piece.isNotBlank()) Text(if (piece == "w") "⛀" else if (piece == "b") "⛂" else if (piece == "W") "⛁" else if (piece == "B") "⛃" else piece, fontSize = (cell.value * .62f).sp, color = if (whitePiece(piece)) Color.White else Color(0xFF091D2E), modifier = Modifier.graphicsLayer { rotationX = -8f; rotationY = 12f; shadowElevation = 12f }) } } } } } }
        Text(message, color = Color.White.copy(alpha = .84f), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { versusAi = !versusAi; board = initialStrategyBoard(checkers); selected = -1; whiteTurn = true; aiThinking = false; message = if (!versusAi) "Deux joueurs sur cet appareil. Les blancs commencent." else "Vous jouez les blancs contre l’IA." }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(if (versusAi) "2 JOUEURS" else "CONTRE IA", fontSize = 10.sp) }
            OutlinedButton(onClick = { board = initialStrategyBoard(checkers); selected = -1; whiteTurn = true; aiThinking = false; message = if (versusAi) "Nouvelle partie contre l’IA." else "Nouvelle partie locale." }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("REJOUER", fontSize = 10.sp) }
        }
    } }
}

@Composable
private fun WapiCardDuel(onXp: (Int) -> Unit, onWin: () -> Unit) {
    val context = LocalContext.current
    var round by rememberSaveable { mutableStateOf(0) }; var player by rememberSaveable { mutableStateOf(0) }; var rival by rememberSaveable { mutableStateOf(0) }; var playerScore by rememberSaveable { mutableStateOf(0) }; var rivalScore by rememberSaveable { mutableStateOf(0) }; var message by rememberSaveable { mutableStateOf("Tirez une carte. La plus forte remporte la manche.") }
    val names = listOf("2","3","4","5","6","7","8","9","10","V","D","R","A")
    fun draw() { round += 1; player = ((System.currentTimeMillis() / 31L) % 13L).toInt() + 2; rival = ((System.currentTimeMillis() / 47L + round) % 13L).toInt() + 2; when { player > rival -> { playerScore++; onXp(10); WhappySounds.reward(); message = "Manche gagnée · +10 XP" }; rival > player -> { rivalScore++; WhappySounds.impact(); message = "L’adversaire gagne cette manche." }; else -> message = "Égalité parfaite." }; WhappySounds.haptic(context); if (playerScore == 5) { onWin(); onXp(100); message = "VICTOIRE DU DUEL · +100 XP" } }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121A47))) { Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("WAPI CARDS", color = WhappySky, fontWeight = FontWeight.Black, fontSize = 10.sp); Text("$playerScore  —  $rivalScore", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { listOf(player to "VOUS", rival to "RIVAL").forEachIndexed { index, card -> Card(Modifier.size(112.dp, 164.dp).graphicsLayer { rotationY = if (round == 0) 180f else if (index == 0) -8f else 8f; rotationX = 4f; shadowElevation = 28f; cameraDistance = 18f }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(card.second, color = WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(if (card.first == 0) "W" else names[(card.first - 2).coerceIn(0, 12)], color = if (index == 0) WhappyBlue else Color(0xFFE53935), fontSize = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally)); Text(if (index == 0) "◆" else "♥", color = if (index == 0) WhappyBlue else Color(0xFFE53935), fontSize = 22.sp) } } } }; Text(message, color = Color.White.copy(alpha = .84f), fontSize = 12.sp); Button(onClick = ::draw, enabled = playerScore < 5, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("TIRER LES CARTES", fontWeight = FontWeight.Black) }; OutlinedButton(onClick = { round = 0; player = 0; rival = 0; playerScore = 0; rivalScore = 0; message = "Nouvelle partie." }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("REJOUER") } } }
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
    var stage by remember { mutableStateOf(0) }
    var pot by remember { mutableStateOf(0) }
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
        WhappySounds.cardFlip()
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
                compareWapiPokerScores(playerScore, rivalScore) > 0 -> { message = "VICTOIRE · ${wapiPokerHandName(playerScore)} · +150 XP"; onXp(150); onWin(); WhappySounds.reward() }
                else -> { message = "L’IA gagne avec ${wapiPokerHandName(rivalScore)}."; WhappySounds.impact() }
            }
        } else {
            message = when (stage) { 1 -> "Flop révélé · l’IA suit."; 2 -> "Turn révélé · l’IA suit."; else -> "River révélée · choisissez votre action." }
        }
    }

    LaunchedEffect(Unit) { deal() }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF092E2A))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
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
    userName: String,
    phone: String,
    onOpenMarket: () -> Unit,
    onOpenBusiness: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_native_services") }
    var walletActive by rememberSaveable { mutableStateOf(prefs.getBoolean("wallet_active", false)) }
    var balance by rememberSaveable { mutableStateOf(prefs.getInt("wallet_balance", 0)) }
    var transactions by remember { mutableStateOf(prefs.getStringSet("transactions", emptySet()).orEmpty().toList().sortedDescending()) }
    var requests by remember { mutableStateOf(prefs.getStringSet("requests", emptySet()).orEmpty().toList().sortedDescending()) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val receivePayload = remember(phone) { "whappy://pay/${phone.filter { it.isDigit() }}" }
    val receiveQr = remember(receivePayload) { createWhappyPayloadQr(receivePayload) }

    fun saveTransactions(next: List<String>) {
        transactions = next.sortedDescending()
        prefs.edit().putStringSet("transactions", transactions.toSet()).apply()
    }

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
                    Text("WAPI WALLET", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(if (walletActive) formatMoney(balance.toLong()) else "Portefeuille non activé", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Mode démonstration sécurisé · aucun débit bancaire réel", Modifier.padding(top = 5.dp), color = Color.White, fontSize = 11.sp)
                    if (!walletActive) {
                        Button(
                            onClick = {
                                walletActive = true
                                balance = 25_000
                                prefs.edit().putBoolean("wallet_active", true).putInt("wallet_balance", balance).apply()
                                saveTransactions(listOf("${System.currentTimeMillis()}|Solde de démonstration|25000") + transactions)
                                feedback = "Portefeuille activé avec 25 000 FCFA de démonstration."
                            },
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text("Activer le portefeuille", fontWeight = FontWeight.Bold) }
                    } else {
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { feedback = null; dialog = "pay" }, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("Payer", fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = { dialog = "receive" }, Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(15.dp)) { Text("Recevoir", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        feedback?.let { message ->
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) { Text(message, Modifier.padding(14.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } }
        }
        item { Text("Services à la demande", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black) }
        item {
            BoxWithConstraints {
                val cell = (maxWidth - 12.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaceCard("Transport", "Commander un trajet", Icons.Rounded.Schedule, cell) { details = ""; dialog = "Transport" }
                        SpaceCard("Livraison", "Faire livrer un colis", Icons.AutoMirrored.Rounded.ReceiptLong, cell) { details = ""; dialog = "Livraison" }
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
        item { Text("Demandes récentes", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black) }
        if (requests.isEmpty()) item { Text("Aucune demande. Choisissez un service pour créer la première.", color = WhappyMuted) }
        items(requests.take(5), key = { it }) { raw ->
            val parts = raw.split("|", limit = 3)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = WhappyBlue)
                    Column(Modifier.padding(start = 11.dp)) { Text(parts.getOrElse(1) { "Service" }, color = WhappyDark, fontWeight = FontWeight.Bold); Text(parts.getOrElse(2) { "Demande enregistrée" }, color = WhappyMuted, fontSize = 11.sp, maxLines = 2) }
                }
            }
        }
        if (transactions.isNotEmpty()) {
            item { Text("Historique du portefeuille", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp)) }
            items(transactions.take(6), key = { it }) { raw ->
                val parts = raw.split("|", limit = 3)
                val value = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Payments, null, tint = if (value >= 0) WhappyBlue else WhappyBlue)
                    Text(parts.getOrElse(1) { "Transaction" }, Modifier.weight(1f).padding(horizontal = 11.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold)
                    Text((if (value > 0) "+" else "") + formatMoney(value), color = if (value >= 0) WhappyBlue else WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }

    if (dialog == "pay") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Envoyer un paiement test", fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(recipient, { recipient = it }, label = { Text("Bénéficiaire") }, singleLine = true); OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Montant FCFA") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); feedback?.let { Text(it, color = WhappyBlue, fontSize = 12.sp) } } },
            confirmButton = { Button(onClick = {
                val value = amount.toIntOrNull() ?: 0
                if (recipient.trim().isEmpty() || value <= 0 || value > balance) feedback = "Vérifiez le bénéficiaire, le montant et le solde disponible."
                else {
                    balance -= value
                    prefs.edit().putInt("wallet_balance", balance).apply()
                    saveTransactions(listOf("${System.currentTimeMillis()}|Paiement test · ${recipient.trim()}|-${value}") + transactions)
                    feedback = "Paiement test envoyé à ${recipient.trim()}."
                    recipient = ""; amount = ""; dialog = null
                }
            }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } },
        )
    }
    if (dialog == "receive") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Recevoir sur WAPI", fontWeight = FontWeight.Black) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(receiveQr.asImageBitmap(), "QR de paiement de $userName", Modifier.size(210.dp).clip(RoundedCornerShape(18.dp))); Text(userName, Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold); Text(phone, color = WhappyMuted); Text("Ce code ouvre une demande de paiement WAPI.", Modifier.padding(top = 8.dp), color = WhappyMuted, fontSize = 11.sp) } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Terminé") } },
        )
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
                    feedback = "Demande $service enregistrée. Un prestataire pourra la prendre en charge."
                    details = ""; dialog = null
                } else feedback = "Ajoutez suffisamment de détails pour traiter la demande."
            }) { Text("Envoyer") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } },
        )
    }
}

private enum class WhappyStudioSection(val label: String) {
    MOTION("Mouvements"),
    IDENTITY("Mon image"),
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
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var recordingSeconds by remember { mutableStateOf(0L) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoKind by remember { mutableStateOf("video") }
    val tts = remember { TextToSpeech(context) { _ -> } }

    fun acceptAsset(uri: Uri, kind: String, type: String) {
        if (preview) {
            localProfile = when (kind) {
                "voice" -> localProfile.copy(voiceUrl = uri.toString(), voiceStatus = "sampled")
                "movement" -> localProfile.copy(movementUrl = uri.toString(), movementStatus = "sampled")
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
        if (save && file != null && file.exists() && file.length() > 0L) acceptAsset(Uri.fromFile(file), "voice", "audio/mp4") else file?.delete()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startVoice() }

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
                Text("Double numérique · sous votre contrôle", color = WhappyMuted, fontSize = 11.sp)
            }
            if (state.twinBusy) CircularProgressIndicator(Modifier.size(22.dp), color = WhappyBlue, strokeWidth = 2.dp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("WAPI DOUBLE ENGINE", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("Votre présence,\nmultipliée.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                        Text("Préparez votre image, votre voix et vos missions. Vous gardez le dernier mot sur chaque production.", Modifier.padding(top = 9.dp), color = Color.White, lineHeight = 19.sp)
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text("$readiness%", color = Color.White, fontWeight = FontWeight.Black) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(if (readiness == 100) "PRÊT À PRODUIRE" else "EN APPRENTISSAGE", color = Color.White, fontWeight = FontWeight.Black)
                                Text("Identité · voix · mouvements · missions", color = Color.White, fontSize = 10.sp)
                            }
                        }
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
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = WhappyBlue)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(110.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text(initials(userName), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black) }
                                Text("DOUBLE DE ${userName.uppercase()}", Modifier.padding(top = 13.dp), color = Color.White, fontWeight = FontWeight.Black)
                                Text("Créé avec mon Double IA", Modifier.padding(top = 4.dp), color = WhappyBlue, fontSize = 10.sp)
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
                        CaptureAssetCard("Portrait vidéo", "Regard, sourire et expressions · 15 à 30 secondes", profile.videoUrl.isNotBlank(), Icons.Rounded.Videocam, state.twinBusy || !profile.identityConsent) {
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

                WhappyStudioSection.VOICE -> {
                    item { StudioTitle("02 / VOICE DNA", "Votre ton, même quand vous travaillez ailleurs", "L’échantillon prépare votre empreinte. Le rendu neuronal nécessite encore un fournisseur de clonage vocal.") }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(20.dp)) {
                                Box(Modifier.align(Alignment.CenterHorizontally).size(100.dp).clip(CircleShape).background(if (recordingVoice) Color.White else Color.White), contentAlignment = Alignment.Center) {
                                    Icon(if (recordingVoice) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = if (recordingVoice) WhappyBlue else WhappyBlue, modifier = Modifier.size(42.dp))
                                }
                                Text("« Bonjour, je suis $userName. Cette voix est la mienne et je contrôle son utilisation par mon Double WAPI. »", Modifier.padding(top = 18.dp), color = WhappyInk, lineHeight = 21.sp)
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
                                Button(enabled = profile.identityConsent && !state.twinBusy, onClick = {
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
                                    enabled = profile.identityConsent && selectedGestures.isNotEmpty() && script.isNotBlank() && !state.twinBusy,
                                    onClick = {
                                        if (preview) localRenders = listOf(WhappyTwinRender("local-${System.currentTimeMillis()}", "Séquence commerciale WAPI", script, language, "prepared")) + localRenders
                                        else onCreateRender("Séquence commerciale WAPI", script, language, selectedGestures)
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                                ) { Icon(Icons.Rounded.Movie, null); Text("Préparer la vidéo", Modifier.padding(start = 7.dp)) }
                                Text("Label permanent : Créé avec le Double IA de $userName", Modifier.padding(top = 9.dp), color = WhappyMuted, fontSize = 10.sp)
                            }
                        }
                    }
                    if (renders.isNotEmpty()) item { Text("Productions préparées", fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                    items(renders, key = { it.id }) { render ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Movie, null, tint = WhappyBlue)
                                Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(render.title, fontWeight = FontWeight.Bold); Text("${render.language} · ${render.status}", color = WhappyMuted, fontSize = 10.sp) }
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
    Card(Modifier.width(width).height(116.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
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
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_consumer") }
    var recentCalls by remember { mutableStateOf(prefs.getStringSet("recent_calls", emptySet()).orEmpty().toList().sortedDescending()) }
    var phone by rememberSaveable { mutableStateOf("") }

    fun rememberCall(name: String, phoneNumber: String, video: Boolean) {
        val entry = "${System.currentTimeMillis()}|${name.replace("|", " ")}|${phoneNumber.replace("|", " ")}|${if (video) "video" else "audio"}"
        recentCalls = (listOf(entry) + recentCalls).take(30)
        prefs.edit().putStringSet("recent_calls", recentCalls.toSet()).apply()
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
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appeler un numéro Wapi", color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text("Saisissez le numéro normal : s’il est inscrit, l’appel passe directement sur Wapi.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    OutlinedTextField(phone, { phone = it.take(28) }, Modifier.fillMaxWidth(), label = { Text("Numéro avec indicatif pays") }, placeholder = { Text("+242 06 123 45 67") }, leadingIcon = { Icon(Icons.Rounded.Phone, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(enabled = calls != null && phone.isNotBlank(), onClick = { rememberCall(phone, phone, false); calls?.startByPhone(phone, false) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Phone, null); Text("  Audio") }
                        Button(enabled = calls != null && phone.isNotBlank(), onClick = { rememberCall(phone, phone, true); calls?.startByPhone(phone, true) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Videocam, null); Text("  Vidéo") }
                    }
                }
            }
        }
        item { Text("Appels récents", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp)) }
        if (recentCalls.isEmpty()) item { Text("Aucun appel lancé depuis WAPI pour le moment.", color = WhappyMuted, modifier = Modifier.padding(vertical = 6.dp)) }
        items(recentCalls.take(8), key = { it }) { raw ->
            val parts = raw.split("|", limit = 4)
            val timestamp = parts.firstOrNull()?.toLongOrNull() ?: 0L
            val name = parts.getOrElse(1) { "Contact" }
            val recentPhone = parts.getOrElse(2) { "" }
            val video = parts.getOrElse(3) { "audio" } == "video"
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (video) Icons.Rounded.Videocam else Icons.Rounded.Phone, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(name, color = WhappyDark, fontWeight = FontWeight.Bold); Text("${if (video) "Vidéo" else "Audio"} · ${formatShortDate(timestamp)} à ${formatTime(timestamp)}", color = WhappyMuted, fontSize = 10.sp) }; FilledIconButton(enabled = recentPhone.isNotBlank() && calls != null, onClick = { calls?.startByPhone(recentPhone, video) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(if (video) Icons.Rounded.Videocam else Icons.Rounded.Phone, "Rappeler $name", tint = Color.White) } }
            }
        }
        item { Text("Tous les contacts", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 7.dp)) }
        if (conversations.isEmpty()) {
            item { EmptyState("Aucun contact", "Ajoutez un contact dans Messages pour pouvoir l’appeler.") }
        } else {
            items(conversations, key = { "call-${it.id}" }) { conversation ->
                val callable = conversation.peer.phoneNumber.isNotBlank()
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 52.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(conversation.peer.displayName, color = WhappyDark, fontWeight = FontWeight.Black)
                            Text(if (conversation.isGroup) "${conversation.memberCount} participants · appel de groupe" else if (callable) "☎ ${conversation.peer.phoneNumber}" else "Numéro protégé", color = WhappyMuted, fontSize = 11.sp)
                            Text(formatTime(conversation.updatedAt), color = WhappyMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { onOpenConversation(conversation) }) { Icon(Icons.Rounded.ChatBubble, "Écrire à ${conversation.peer.displayName}", tint = WhappyBlue) }
                        if (!conversation.isGroup) {
                            FilledIconButton(enabled = callable && calls != null, onClick = { rememberCall(conversation.peer.displayName, conversation.peer.phoneNumber, false); calls?.start(conversation.peer, false) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.Rounded.Phone, "Appeler ${conversation.peer.displayName}", tint = Color.White) }
                            FilledIconButton(enabled = callable && calls != null, onClick = { rememberCall(conversation.peer.displayName, conversation.peer.phoneNumber, true); calls?.start(conversation.peer, true) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.Rounded.Videocam, "Appel vidéo ${conversation.peer.displayName}", tint = Color.White) }
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
private fun MessagesScreen(
    conversations: List<WhappyConversation>,
    loading: Boolean,
    preview: Boolean,
    contactBusy: Boolean,
    contacts: List<WhappyContact>,
    contactSearchResult: WhappyMember?,
    contactSearchPhone: String,
    contactSearchMessage: String?,
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
) {
    var adding by remember { mutableStateOf(false) }
    var searchingBusiness by remember { mutableStateOf(false) }
    var messageSection by rememberSaveable { mutableStateOf(if (initialSection == 1) 1 else if (initialSection == 2) 2 else 0) }
    var creatingChannel by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupMembers by remember { mutableStateOf(emptySet<String>()) }
    var groupPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var channelName by remember { mutableStateOf("") }
    var channelDescription by remember { mutableStateOf("") }
    var channelCategory by remember { mutableStateOf("Communauté") }
    var conversationSearch by rememberSaveable { mutableStateOf(initialQuery) }
    var phone by remember { mutableStateOf("") }
    var contactCountry by rememberSaveable { mutableStateOf("+242") }
    var contactCountryMenu by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanInProgress by remember { mutableStateOf(false) }
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
    LaunchedEffect(contactSearchPhone) { if (contactSearchPhone.isNotBlank()) { phone = contactSearchPhone; contactCountry = authCountries.firstOrNull { contactSearchPhone.startsWith(it.code) }?.code ?: "+242"; adding = true } }
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
            phone = candidate
            contactCountry = authCountries.firstOrNull { candidate.startsWith(it.code) }?.code ?: contactCountry
            scanError = null
            if (autoSearch && !preview) onSearchContact(candidate)
        } else {
            phone = value
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
        phone = normalized
        contactCountry = authCountries.firstOrNull { normalized.startsWith(it.code) }?.code ?: contactCountry
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
            null -> {
                if (trimmed.isNotBlank()) {
                    startContactSearch(trimmed, markBusy = true)
                } else {
                    scanError = "Ce QR n’est pas un code WAPI valide."
                }
            }
        }
    }

    val qrImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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

    fun scanWhappyCode() {
        val activity = context.findActivity()
        if (activity == null) {
            scanError = "Le scanner n’est pas disponible sur cet appareil"
            return
        }
        scanError = null
        if (scanInProgress) return
        scanInProgress = true
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        try {
            GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { barcode ->
                    scanInProgress = false
                    processScannedContact(barcode.rawValue.orEmpty())
                }
                .addOnCanceledListener {
                    scanInProgress = false
                    scanError = "Scan annulé. Vous pouvez aussi saisir le numéro du contact."
                }
                .addOnFailureListener {
                    scanInProgress = false
                    scanError = "Le scanner n’a pas pu démarrer sur cet appareil. Saisissez le numéro ou utilisez un téléphone avec Google Play services."
                }
        } catch (error: Exception) {
            scanInProgress = false
            scanError = "Le scanner n’est pas disponible pour le moment. Saisissez le numéro du contact."
        }
    }

    Column(Modifier.fillMaxSize().background(WhappySurface)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(when (messageSection) { 1 -> "Contacts"; 2 -> "Chaînes"; else -> "Messages" }, fontSize = 26.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text(when (messageSection) { 1 -> "Vos personnes sur WAPI"; 2 -> "Suivez ce qui compte pour vous"; else -> "Vos conversations instantanées" }, color = WhappyMuted, fontSize = 12.sp) }
            TextButton(onClick = { when (messageSection) { 2 -> creatingChannel = true; 0 -> creatingGroup = true; else -> { phone = ""; resetContactSearch(); adding = true } } }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.textButtonColors(contentColor = WhappyBlue)) {
                Icon(Icons.Rounded.Add, when (messageSection) { 2 -> "Créer une chaîne"; 0 -> "Créer un groupe"; else -> "Ajouter un contact" }, modifier = Modifier.size(18.dp))
                Text(when (messageSection) { 2 -> " Chaîne"; 0 -> " Groupe"; else -> " Ajouter" }, fontWeight = FontWeight.Black)
            }
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp).clip(RoundedCornerShape(15.dp)).background(WhappySurface).padding(4.dp)) {
            listOf("Discussions", "Contacts", "Chaînes").forEachIndexed { index, label ->
                TextButton(onClick = { messageSection = index; conversationSearch = "" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(containerColor = if (messageSection == index) Color.White else Color.Transparent, contentColor = if (messageSection == index) WhappyDark else WhappyMuted)) { Text(label, fontSize = 11.sp, fontWeight = if (messageSection == index) FontWeight.Black else FontWeight.Medium) }
            }
        }
        OutlinedTextField(conversationSearch, { conversationSearch = it.take(120) }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), placeholder = { Text(when (messageSection) { 1 -> "Rechercher un contact"; 2 -> "Rechercher une chaîne ou une catégorie"; else -> "Rechercher une discussion" }) }, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = WhappyMuted) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = WhappyBlue, unfocusedBorderColor = WhappyLine, focusedContainerColor = Color.White, unfocusedContainerColor = WhappySurface))
        if (messageSection == 2) {
            ChannelDirectory(channels, currentUserId, conversationSearch, onOpenChannel, onSubscribeChannel)
        } else if (messageSection == 1) {
            val conversationContacts = conversations.map { WhappyContact(it.peer, it.updatedAt) }
            val visibleContacts = (contacts + conversationContacts).distinctBy { it.member.uid }.filter { SearchNormalizer.matches(conversationSearch, it.member.displayName, it.member.phoneNumber) }.sortedBy { SearchNormalizer.normalize(it.member.displayName) }
            if (loading && visibleContacts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
            else if (visibleContacts.isEmpty()) {
                Card(Modifier.padding(18.dp).fillMaxWidth().clickable { phone = ""; resetContactSearch(); adding = true }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
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
                    Card(Modifier.fillMaxWidth().clickable(enabled = !contactBusy) { onOpenContact(contact) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text(initials(contact.member.displayName), color = Color.White, fontWeight = FontWeight.Black) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(contact.member.displayName, fontWeight = FontWeight.Black, color = WhappyDark)
                                Text(contact.member.phoneNumber.ifBlank { "Contact WAPI" }, color = WhappyMuted, fontSize = 11.sp)
                            }
                            Text("Message", color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (loading && conversations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else if (filteredConversations.isEmpty()) EmptyState(if (conversationSearch.isBlank()) "Aucune conversation" else "Aucun résultat", if (conversationSearch.isBlank()) "Ouvrez l’onglet Contacts pour ajouter une personne sur WAPI." else "Essayez un autre nom ou un mot du dernier message.")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (conversationSearch.isBlank()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(WhappyBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Groups, null, tint = WhappyBlue) }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text("Groupes WAPI", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                    Text("${groupConversations.size} groupe${if (groupConversations.size > 1) "s" else ""} · ${directConversations.size} discussion${if (directConversations.size > 1) "s" else ""} directe${if (directConversations.size > 1) "s" else ""}", color = WhappyMuted, fontSize = 11.sp)
                                }
                                TextButton(onClick = { creatingGroup = true }, colors = ButtonDefaults.textButtonColors(contentColor = WhappyBlue), shape = RoundedCornerShape(13.dp)) { Text("+ Groupe", fontWeight = FontWeight.Black) }
                            }
                            Text("Créez un salon privé pour famille, équipe, communauté ou projet. Les messages, notes vocales et fichiers restent dans la même discussion.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            items(filteredConversations, key = { it.id }) { conversation ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(conversation) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isGroup && conversation.peer.photoUrl.isBlank()) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, null, tint = Color.White)
                        }
                    } else UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 52.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row { Text(conversation.peer.displayName, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = WhappyDark); Text(formatTime(conversation.updatedAt), color = WhappyMuted, fontSize = 11.sp) }
                        Text(if (conversation.isGroup) "${conversation.memberCount} membres · ${conversation.lastMessage}" else conversation.lastMessage, Modifier.padding(top = 4.dp), color = WhappyMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (conversation.isGroup) Text("GROUPE", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(9.dp)).background(Color.White).padding(horizontal = 7.dp, vertical = 4.dp), color = WhappyBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    if (conversation.unread) Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(WhappyBlue))
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
                    Text(if (contactSearchResult == null) "Saisissez le numéro complet ou scannez le code personnel de votre contact. Si le compte existe, WAPI ouvre directement la discussion." else "Compte vérifié : la discussion s’ouvre automatiquement.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { value ->
                            applyContactInputValue(value)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("Numéro de téléphone") },
                        leadingIcon = { Box { TextButton(onClick = { contactCountryMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(contactCountry + " ▾", fontSize = 11.sp, fontWeight = FontWeight.Bold) }; DropdownMenu(contactCountryMenu, { contactCountryMenu = false }) { authCountries.forEach { country -> DropdownMenuItem(text = { Text("${country.flag} ${country.name}  ${country.code}") }, onClick = { contactCountry = country.code; contactCountryMenu = false; resetContactSearch() }) } } } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (!contactBusy && !preview) startContactSearch(phone) }),
                        singleLine = true,
                    )
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
                        OutlinedButton(enabled = !contactBusy && !scanInProgress && !imageScanInProgress, onClick = { if (preview) scanError = "Le scan est disponible dans l’application connectée" else scanWhappyCode() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(18.dp))
                            Text(if (scanInProgress) "  Analyse…" else "  Scanner", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(enabled = !contactBusy && !scanInProgress && !imageScanInProgress, onClick = { if (preview) scanError = "L’import est disponible dans l’application connectée" else qrImagePicker.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Photo, null, modifier = Modifier.size(18.dp))
                            Text(if (imageScanInProgress) "  Lecture…" else "  Image QR", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (contactBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(18.dp), color = WhappyBlue, strokeWidth = 2.dp); Text(if (contactSearchResult == null) "Recherche du compte WAPI…" else "Ajout du contact…", color = WhappyMuted, fontSize = 12.sp) }
                    if (contactSearchResult != null) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isFounderContact = WhappyIdentity.isFounder(contactSearchResult.phoneNumber)
                            Box(Modifier.size(44.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text(initials(contactSearchResult.displayName), color = Color.White, fontWeight = FontWeight.Black) }
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(contactSearchResult.displayName, color = WhappyDark, fontWeight = FontWeight.Black)
                                    Icon(
                                        Icons.Rounded.Verified,
                                        null,
                                        modifier = Modifier.padding(start = 4.dp).size(15.dp),
                                        tint = WapiVerifiedGray,
                                    )
                                }
                                Text(contactSearchResult.phoneNumber.ifBlank { contactSearchPhone }, color = WhappyMuted, fontSize = 11.sp)
                                if (isFounderContact) {
                                    Text(WhappyIdentity.founderBadgeLabel, color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Compte WAPI vérifié", color = WapiVerifiedGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            title = { Text("Scanner WAPI") },
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
    val ready = groupName.trim().length >= 2 && selectedIds.size >= 2
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPhotoChange(uri)
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
                            onClick = { photoPicker.launch("image/*") },
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
                    TextButton(enabled = !busy, onClick = { photoPicker.launch("image/*") }) {
                        Icon(Icons.Rounded.Photo, null, modifier = Modifier.size(17.dp))
                        Text(if (photoUri == null) "  Ajouter une photo de groupe" else "  Changer la photo", fontWeight = FontWeight.Bold)
                    }
                    if (photoUri != null) TextButton(enabled = !busy, onClick = { onPhotoChange(null) }) { Text("Retirer", color = WhappyMuted) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MEMBRES", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("${selectedIds.size} sélectionné${if (selectedIds.size > 1) "s" else ""} · minimum 2", color = WhappyMuted, fontSize = 10.sp)
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
                        Text("Deux contacts minimum sont nécessaires.", color = WhappyMuted, fontSize = 11.sp)
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
                                    UserAvatar(contact.member.photoUrl, contact.member.displayName, 42.dp)
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
                        else Text(if (ready) "Créer le groupe" else "2 membres requis", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
                    "🎙️ Vocal à venir" to "🎙️ Note vocale à venir : ",
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
    currentUserId: String,
    loading: Boolean,
    sending: Boolean,
    onBack: () -> Unit,
    onSend: (String, WhappyMessage?) -> Unit,
    onSendMedia: (Uri, String, String, String, Int) -> Unit,
    onReact: (WhappyMessage, String) -> Unit,
    onDelete: (WhappyMessage) -> Unit,
    onEdit: (WhappyMessage, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onRetryPending: () -> Unit,
) {
    val context = LocalContext.current
    val draftPrefs = remember { WhappyFastStorage.preferences(context, "whappy_chat_drafts") }
    var text by remember(conversation.id) { mutableStateOf(draftPrefs.getString(conversation.id, "").orEmpty()) }
    var showEmoji by remember(conversation.id) { mutableStateOf(false) }
    var showMore by remember(conversation.id) { mutableStateOf(false) }
    var recording by remember(conversation.id) { mutableStateOf(false) }
    var recordStartedAt by remember(conversation.id) { mutableStateOf(0L) }
    var recorder by remember(conversation.id) { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember(conversation.id) { mutableStateOf<File?>(null) }
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
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val calls = LocalWhappyCalls.current
    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    val visibleMessages = remember(messages, searchQuery) { messages.filter { SearchNormalizer.matches(searchQuery, it.text, it.mediaName, it.replyText) } }
    val messageIndexById = remember(visibleMessages) { visibleMessages.mapIndexed { index, message -> message.id to index }.toMap() }
    var chatPositioned by remember(conversation.id) { mutableStateOf(false) }
    var previewImage by remember(conversation.id) { mutableStateOf<String?>(null) }
    var showPeerProfile by remember(conversation.id) { mutableStateOf(false) }

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
        runCatching { createVoiceRecorder(context) }.onSuccess { (activeRecorder, file) ->
            recorder = activeRecorder
            recordingFile = file
            recordStartedAt = System.currentTimeMillis()
            recording = true
            showEmoji = false
            keyboard?.hide()
        }
    }

    fun finishRecording(send: Boolean) {
        val active = recorder
        val file = recordingFile
        val duration = ((System.currentTimeMillis() - recordStartedAt) / 1_000L).toInt().coerceAtLeast(1)
        runCatching { active?.stop() }
        active?.release()
        recorder = null
        recording = false
        recordingFile = null
        if (send && file != null && file.exists() && file.length() > 0L) {
            onSendMedia(Uri.fromFile(file), "audio", "audio/mp4", "note-vocale-${System.currentTimeMillis()}.m4a", duration)
        } else {
            file?.delete()
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            val kind = if (type.startsWith("video/")) "video" else "image"
            onSendMedia(uri, kind, type, displayName(context, uri), 0)
            WhappySounds.mediaAdded()
        }
    }
    val offerMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            offerMedia = uri
            offerMediaType = context.contentResolver.getType(uri) ?: "image/jpeg"
            offerMediaName = displayName(context, uri)
            WhappySounds.mediaAdded()
        }
    }

    DisposableEffect(conversation.id) {
        onDispose {
            onTyping(false)
            runCatching { recorder?.stop() }
            recorder?.release()
            recordingFile?.delete()
        }
    }
    LaunchedEffect(text, editingMessage?.id) {
        if (editingMessage == null && text.isNotBlank()) { onTyping(true); delay(1_400); onTyping(false) }
        else onTyping(false)
    }
    LaunchedEffect(conversation.id, visibleMessages.size, searchQuery) {
        if (visibleMessages.isNotEmpty() && searchQuery.isBlank()) {
            val nearBottom = !chatPositioned || listState.firstVisibleItemIndex >= (visibleMessages.size - 5).coerceAtLeast(0)
            if (nearBottom) {
                listState.scrollToItem(visibleMessages.lastIndex)
                chatPositioned = true
            }
        }
    }

    Column(Modifier.fillMaxSize().background(WapiChatBackground).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            if (conversation.isGroup && conversation.peer.photoUrl.isBlank()) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(WhappyAurora).clickable { showPeerProfile = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Groups, null, tint = Color.White)
                }
            } else UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 40.dp, Modifier.clickable { showPeerProfile = true })
            Column(Modifier.weight(1f).padding(start = 10.dp).clickable { showPeerProfile = true }) {
                Text(conversation.peer.displayName, fontWeight = FontWeight.SemiBold, color = WhappyDark, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(WapiChatAccent))
                    Text(if (conversation.isGroup) "  ${conversation.memberCount} membres" else if (conversation.peerTyping) "  écrit…" else "  en ligne", color = if (conversation.peerTyping) WapiChatAccent else WhappyMuted, fontSize = 11.sp)
                }
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) { Icon(Icons.Rounded.Search, "Rechercher dans la discussion", tint = if (searchOpen) WapiChatAccent else WhappyDark) }
            if (conversation.peer.phoneNumber.isNotBlank() && !conversation.isGroup) {
                IconButton(enabled = calls != null, onClick = { calls?.start(conversation.peer, false) }) { Icon(Icons.Rounded.Phone, "Appel audio ${conversation.peer.displayName}") }
                IconButton(enabled = calls != null, onClick = { calls?.start(conversation.peer, true) }) { Icon(Icons.Rounded.Videocam, "Appel vidéo ${conversation.peer.displayName}") }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(WhappyLine))
        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it.take(120) }, Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 12.dp, vertical = 6.dp), placeholder = { Text("Rechercher un message") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
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
                        shape = RoundedCornerShape(6.dp),
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
                    "$pendingCount message${if (pendingCount > 1) "s" else ""} en attente de connexion · WAPI va réessayer",
                    Modifier.weight(1f).padding(horizontal = 9.dp),
                    color = WhappyBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onRetryPending) { Text("Réessayer maintenant") }
            }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (visibleMessages.isEmpty() && searchQuery.isNotBlank()) item { Text("Aucun message ne correspond à « $searchQuery ».", Modifier.padding(24.dp), color = WhappyMuted) }
            itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, message ->
                val mine = message.senderId == currentUserId
                Column(Modifier.fillMaxWidth()) {
                    if (index == 0 || !isSameDay(message.createdAt, visibleMessages[index - 1].createdAt)) {
                        Text(formatMessageDay(message.createdAt), Modifier.align(Alignment.CenterHorizontally).padding(vertical = 7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD3D3D3)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        if (conversation.isGroup && !mine) {
                            UserAvatar("", message.senderName.ifBlank { "Membre WAPI" }, 30.dp, Modifier.padding(top = 3.dp))
                            Spacer(Modifier.width(7.dp))
                        }
                        Surface(
                            color = if (mine) WapiBubbleOutgoing else Color.White,
                            shape = if (mine) RoundedCornerShape(topStart = 18.dp, topEnd = 5.dp, bottomEnd = 18.dp, bottomStart = 18.dp) else RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(0.76f).pointerInput(message.id, message.deleted, message.deliveryState) {
                            detectTapGestures(onLongPress = { if (!message.deleted && message.deliveryState == "sent") selectedMessage = message })
                        }) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                if (conversation.isGroup && !mine && message.senderName.isNotBlank()) {
                                    Text(message.senderName, color = WapiChatAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                if (message.replyText.isNotBlank()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .06f)).padding(7.dp)) { Text("↩ ${message.replyText}", color = Color(0xFF666666), fontSize = 10.sp, maxLines = 2) }
                                if (message.replyText.isNotBlank()) {
                                    val targetIndex = messageIndexById[message.replyToId] ?: -1
                                    if (targetIndex >= 0) {
                                        TextButton(
                                            onClick = {
                                                chatScope.launch { listState.animateScrollToItem(targetIndex) }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        ) {
                                            Text("Voir le message d’origine", color = WapiChatAccent, fontSize = 9.sp)
                                        }
                                    }
                                }
                                val actions = remember(message.text) { detectMessageActions(message.text) }
                                when (message.kind) {
                                    "audio" -> MediaMessageRow(Icons.Rounded.AudioFile, "Note vocale · ${message.durationSeconds}s", mine) { runCatching { uriHandler.openUri(message.mediaUrl) } }
                                    "image" -> InlineImageMessage(message.mediaUrl, message.mediaName.ifBlank { "Photo" }, mine) { previewImage = message.mediaUrl }
                                    "video" -> MediaMessageRow(Icons.Rounded.Movie, message.mediaName.ifBlank { "Vidéo WAPI" }, mine) { runCatching { uriHandler.openUri(message.mediaUrl) } }
                                    "deleted" -> Text("Message supprimé", color = WhappyMuted)
                                    else -> MessageLinkText(message.text, mine, actions = actions, onAction = ::handleMessageAction)
                                }
                                if (actions.isNotEmpty()) {
                                    FlowRow(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        actions.take(2).forEach { action ->
                                            OutlinedButton(
                                                onClick = { handleMessageAction(action) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                            ) { Text(if (action.type == MessageActionType.Phone) "📞 ${action.title}" else "🔗 ${action.title}") }
                                        }
                                    }
                                }
                                if (message.reactions.isNotEmpty()) Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { message.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) -> Text("$emoji${if (count > 1) " $count" else ""}", modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = .06f)).padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 11.sp) } }
                                Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (message.edited) Text("modifié · ", color = WhappyMuted, fontSize = 9.sp)
                                    Text(formatTime(message.createdAt), color = WhappyMuted, fontSize = 9.sp)
                                    if (mine) {
                                        val pending = message.deliveryState != "sent"
                                        val read = !pending && message.createdAt > 0L && conversation.peerReadAt >= message.createdAt
                                        val label = when {
                                            message.deliveryState == "retrying" -> " · Nouvelle tentative…"
                                            pending -> " · En attente…"
                                            read -> " · Lu"
                                            else -> " · Envoyé"
                                        }
                                        Text(label, color = Color(0xFF52748C), fontSize = 9.sp)
                                        Icon(
                                            if (pending) Icons.Rounded.Schedule else Icons.Rounded.CheckCircle,
                                            null,
                                            tint = WapiChatAccent.copy(alpha = if (read) 1f else .62f),
                                            modifier = Modifier.padding(start = 3.dp).size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showEmoji) EmojiTray(onEmoji = { updateDraft(text + it) }, onClose = { showEmoji = false })
        if (recording) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(WapiChatAccent))
                Text("Note vocale en cours… appuyez sur Stop pour envoyer", Modifier.weight(1f).padding(start = 9.dp), color = WapiChatAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                TextButton(onClick = { finishRecording(false) }) { Text("Annuler") }
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
        Row(Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 5.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(
                enabled = !sending,
                onClick = {
                    if (recording) finishRecording(true)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                },
            ) { Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording) "Envoyer la note vocale" else "Note vocale", tint = WhappyDark) }
            OutlinedTextField(
                value = text,
                onValueChange = { updateDraft(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (recording) "Enregistrement…" else "Message…") },
                enabled = !recording,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WapiChatAccent.copy(alpha = .45f),
                    unfocusedBorderColor = WhappyLine,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFF9FCFE),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = true),
                keyboardActions = KeyboardActions(onSend = { submitText(); keyboard?.hide() }),
            )
            IconButton(enabled = !recording, onClick = { showEmoji = !showEmoji; showMore = false; if (showEmoji) keyboard?.hide() }) { Icon(Icons.Rounded.EmojiEmotions, "Émojis", tint = WhappyDark) }
            if (text.isNotBlank()) Button(
                enabled = !sending,
                onClick = { submitText() },
                modifier = Modifier.padding(start = 2.dp).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WapiChatAccent),
                contentPadding = PaddingValues(horizontal = 13.dp),
            ) { Text("Envoyer", color = Color.White, fontSize = 12.sp) }
            else IconButton(enabled = !recording, onClick = { showMore = !showMore; showEmoji = false; keyboard?.hide() }) { Icon(Icons.Rounded.Add, "Plus", tint = WhappyDark, modifier = Modifier.size(28.dp)) }
        }
        if (showMore) {
            Row(Modifier.fillMaxWidth().background(WapiToolbar).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showMore = false; mediaPicker.launch(arrayOf("image/*", "video/*")) }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Photo, "Photos et vidéos", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Album", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showMore = false; offerOpen = true }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(WhappySurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, "Faire une offre", tint = WapiChatAccent, modifier = Modifier.size(25.dp)) }
                    Text("Offre", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
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
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !sending) { offerMediaPicker.launch(arrayOf("image/*", "video/*")) },
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
                            onSendMedia(uri, kind, offerMediaType.ifBlank { if (kind == "video") "video/mp4" else "image/jpeg" }, offerMediaName.ifBlank { "offre-whappy" }, 0)
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
    if (showPeerProfile) {
        AlertDialog(
            onDismissRequest = { showPeerProfile = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(conversation.peer.photoUrl, conversation.peer.displayName, 58.dp)
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
                    if (!conversation.isGroup && conversation.peer.phoneNumber.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(onClick = { calls?.start(conversation.peer, false); showPeerProfile = false }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Phone, null); Text(" Appeler") }
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
    if (actions.isEmpty()) {
        Text(text, color = WhappyInk, lineHeight = 20.sp)
        return
    }
    val annotated = buildAnnotatedString {
        append(text)
        actions.forEach { action ->
            if (action.start >= action.end || action.start < 0 || action.end > text.length) return@forEach
            addStringAnnotation("message-action", action.target, action.start, action.end)
            addStyle(
                SpanStyle(
                    color = Color(0xFF1769AA),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold,
                ),
                action.start,
                action.end,
            )
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = WhappyInk, lineHeight = 20.sp),
        onClick = { offset ->
            val clicked = annotated.getStringAnnotations("message-action", offset, offset).firstOrNull()
            if (clicked != null) {
                val action = actions.firstOrNull { it.start <= offset && offset < it.end && it.target == clicked.item }
                if (action != null) onAction(action)
            }
        },
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
        Icon(icon, null, tint = WapiChatAccent, modifier = Modifier.size(28.dp))
        Text(label, Modifier.padding(start = 9.dp), color = WhappyInk, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InlineImageMessage(source: String, label: String, mine: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(source) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    LaunchedEffect(source) {
        bitmap = withContext(Dispatchers.IO) { loadImageBitmap(context, source) }
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
        var bitmap by remember(source) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        var loading by remember(source) { mutableStateOf(true) }
        LaunchedEffect(source) {
            bitmap = withContext(Dispatchers.IO) { loadImageBitmap(context, source) }
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
    var bitmap by remember(imageSource) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember(imageSource) { mutableStateOf(true) }
    var failed by remember(imageSource) { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(imageSource) {
        loading = true
        failed = false
        bitmap = withContext(Dispatchers.IO) {
            loadImageBitmap(context, imageSource)
        }
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
    val emojis = listOf(
        "😀", "😄", "😂", "🤣", "😊", "🥰", "😍", "😘", "🤩", "😎", "🥳", "😭",
        "😡", "🤔", "😮", "😇", "👍", "👎", "🙏", "👏", "💪", "🤝", "🙌", "👌",
        "❤️", "💙", "🔥", "✨", "🎉", "💯", "✅", "📍", "🎁", "🛍️", "💼", "🚀",
        "📞", "🎥", "🎤", "🎵", "💰", "🧾", "⭐", "🇨🇬", "🇨🇩",
    )
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EMOJIS", Modifier.weight(1f), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("Fermer", fontSize = 10.sp) }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            emojis.forEach { emoji ->
                TextButton(
                    onClick = { onEmoji(emoji) },
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
    recorder.setAudioEncodingBitRate(96_000)
    recorder.setAudioSamplingRate(44_100)
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
    preview: Boolean,
    busy: Boolean,
    accountDisplayName: String,
    onPublish: (String, String, String, String) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Marketplace", fontSize = 28.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text("Achetez, vendez ou négociez localement", color = WhappyMuted) }
                IconButton(onClick = { showingOrders = true }) { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, "Mes commandes", tint = WhappyDark) }
                Box(contentAlignment = Alignment.TopEnd) { IconButton(onClick = { showingCart = true }) { Icon(Icons.Rounded.ShoppingCart, "Panier", tint = WhappyDark) }; if (cart.values.sum() > 0) Box(Modifier.size(17.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text(cart.values.sum().coerceAtMost(9).toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black) } }
                Button(onClick = { creating = true }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)) { Icon(Icons.Rounded.Add, null); Text("Vendre", Modifier.padding(start = 4.dp)) }
            }
        }
        marketFeedback?.let { value -> item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) { Text(value, Modifier.padding(13.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Rechercher un produit ou une boutique") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true) }
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
    onCreateLive: (String, String, String, Boolean, String, String) -> Unit,
    onEndLive: (String) -> Unit,
    onUpdateLiveStatus: (String, String) -> Unit,
    onOpenTwin: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var localLives by remember { mutableStateOf(emptyList<WhappyLive>()) }
    var selectedLive by remember { mutableStateOf<WhappyLive?>(null) }
    var previewStatuses by remember { mutableStateOf(emptyMap<String, String>()) }
    var pendingStudioTitle by remember { mutableStateOf<String?>(null) }
    var pendingLiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val livePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.CAMERA] == true && result[Manifest.permission.RECORD_AUDIO] == true
        if (granted) pendingLiveAction?.invoke() else permissionError = true
        pendingLiveAction = null
    }
    val startWithPermissions: (() -> Unit) -> Unit = { action ->
        val alreadyGranted = preview || (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (alreadyGranted) action()
        else {
            permissionError = false
            pendingLiveAction = action
            livePermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }
    val visibleLives = (localLives + lives).map { live -> previewStatuses[live.id]?.let { live.copy(status = it) } ?: live }.filter { it.status != "ended" }
    val liveNow = visibleLives.count { it.status == "live" }
    LaunchedEffect(visibleLives, pendingStudioTitle) {
        val title = pendingStudioTitle ?: return@LaunchedEffect
        visibleLives.firstOrNull { it.hostId == currentUserId && it.status == "live" && it.title == title }?.let {
            selectedLive = it
            pendingStudioTitle = null
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(WhappyBlue).padding(horizontal = 9.dp, vertical = 5.dp)) { Text("● LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                        Text("$liveNow directs maintenant", Modifier.padding(start = 10.dp), color = Color.White, fontSize = 12.sp)
                    }
                    Text(t("Montez sur scène\nen direct.", "Go live\non stage.", "Matá na scène\nna live."), Modifier.padding(top = 13.dp), color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                    Text(t("Votre caméra s’ouvre directement. Mon WAPI reste une option, jamais une obligation.", "Your camera opens directly. My WAPI is optional, never required.", "Caméra na yo ekofungwama mbala moko. Mon WAPI ezali kaka option."), Modifier.padding(top = 9.dp), color = Color.White, lineHeight = 19.sp)
                    Button(onClick = { creating = true }, Modifier.fillMaxWidth().padding(top = 17.dp).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WhappyBlue)) { Icon(Icons.Rounded.Videocam, null); Text(t("  Monter sur scène", "  Go on stage", "  Matá na scène"), fontWeight = FontWeight.Black) }
                    if (permissionError) Text(t("Autorisez la caméra et le micro pour démarrer le direct.", "Allow camera and microphone to start.", "Pesa ndingisa na caméra mpe micro."), Modifier.padding(top = 10.dp), color = Color.White, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenTwin), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t("OPTION CRÉATIVE · FACULTATIVE", "OPTIONAL CREATIVE TOOL", "OPTION YA CRÉATION"), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(t("Animer avec Mon WAPI", "Animate with My WAPI", "Sala animation na Mon WAPI"), color = WhappyDark, fontWeight = FontWeight.Bold) }
                    Text("›", color = WhappyBlue, fontSize = 25.sp)
                }
            }
        }
        item { Text("En direct et programmés", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
        if (visibleLives.isEmpty()) item { EmptyState("Aucun Live en cours", "Préparez le premier direct de votre communauté.") }
        items(visibleLives, key = { it.id }) { live ->
            val liveReady = live.streamProvider != "unconfigured" && live.streamRoomId.isNotBlank()
            Card(Modifier.fillMaxWidth().clickable { selectedLive = live }, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(128.dp).background(if (live.status == "live") WhappyDark else Color.White), contentAlignment = Alignment.Center) {
                        Icon(if (live.status == "live") Icons.Rounded.PlayArrow else Icons.Rounded.Schedule, null, tint = if (live.status == "live") Color.White else WhappyBlue, modifier = Modifier.size(46.dp))
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(if (liveReady && live.status == "live") WhappyBlue else Color(0xFF6B7280)).padding(horizontal = 9.dp, vertical = 5.dp)) { Text(if (liveReady && live.status == "live") "● EN DIRECT" else if (live.status == "live") "FLUX NON CONFIGURÉ" else "PROGRAMMÉ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        if (liveReady && live.status == "live") Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" ${live.viewerCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(live.title, color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${live.hostName} · ${live.category}", Modifier.weight(1f), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(liveModeLabel(live.hostMode), color = WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        }
                        if (live.productTitle.isNotBlank()) Text("Deal présenté : ${live.productTitle}", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp)
                        if (live.hostId == currentUserId && live.status == "scheduled" && liveReady) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { startWithPermissions { pendingStudioTitle = live.title; if (preview) previewStatuses = previewStatuses + (live.id to "live") else onUpdateLiveStatus(live.id, "live") } }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Démarrer") }
                            OutlinedButton(onClick = { if (preview) previewStatuses = previewStatuses + (live.id to "ended") else onEndLive(live.id) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Annuler") }
                        }
                        else if (live.hostId == currentUserId && live.status == "scheduled") Text("Direct non publié : le serveur média WAPI doit être configuré avant le démarrage.", Modifier.padding(top = 10.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        else if (live.hostId == currentUserId) OutlinedButton(onClick = { if (preview) previewStatuses = previewStatuses + (live.id to "ended") else onUpdateLiveStatus(live.id, "ended") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Text("Terminer le direct") }
                        else if (liveReady) Button(onClick = { selectedLive = live }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text(if (live.status == "live") "Rejoindre le Live" else "Voir le programme", Modifier.padding(start = 6.dp)) }
                        else Text("Ce salon sera visible ici dès que son flux audio/vidéo réel sera disponible.", Modifier.padding(top = 10.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        item { Text("Chaque profil peut créer un salon. Le statut, l’audience et les réactions sont synchronisés en temps réel; la vidéo multi-appareils utilise l’infrastructure média WAPI.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp) }
    }
    if (creating) LiveDialog(busy, onDismiss = { creating = false }) { title, category, product, startNow, hostMode, visibility ->
        val createAction = {
            val status = if (startNow) "live" else "scheduled"
            if (startNow) pendingStudioTitle = title
            if (preview) localLives = listOf(WhappyLive("local-${System.currentTimeMillis()}", currentUserId, accountDisplayName, title, category, product, status, 0, System.currentTimeMillis(), hostMode, visibility)) + localLives
            else onCreateLive(title, category, product, startNow, hostMode, visibility)
        }
        if (startNow) startWithPermissions(createAction) else createAction()
        creating = false
    }
    selectedLive?.let { live ->
        LiveRoomDialog(
            live = live,
            isOwner = live.hostId == currentUserId,
            busy = busy,
            onDismiss = { selectedLive = null },
            onEnd = {
                if (preview) previewStatuses = previewStatuses + (live.id to "ended") else onUpdateLiveStatus(live.id, "ended")
                selectedLive = null
            },
        )
    }
}

@Composable
private fun LiveRoomDialog(live: WhappyLive, isOwner: Boolean, busy: Boolean, onDismiss: () -> Unit, onEnd: () -> Unit) {
    if (live.streamProvider == "unconfigured" || live.streamRoomId.isBlank()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.Videocam, null, tint = WhappyBlue) },
            title = { Text("Flux média indisponible", fontWeight = FontWeight.Black) },
            text = { Text("Ce salon est bien enregistré dans WAPI, mais il ne possède pas encore de transport audio/vidéo réel. La caméra locale ne sera pas présentée comme un direct public et aucun spectateur fictif ne sera compté.") },
            confirmButton = { Button(onClick = onDismiss) { Text("Fermer") } },
        )
        return
    }
    val context = LocalContext.current
    val prefs = remember { WhappyFastStorage.preferences(context, "whappy_live_lab") }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val tts = remember { TextToSpeech(context) { _ -> } }
    var reactionCount by remember(live.id) { mutableStateOf(live.viewerCount) }
    var message by remember(live.id) { mutableStateOf("") }
    var liveEvents by remember(live.id) { mutableStateOf(prefs.getStringSet("events-${live.id}", emptySet()).orEmpty().toList().sorted()) }
    var giftPanel by rememberSaveable(live.id) { mutableStateOf(false) }
    var giftBalance by rememberSaveable(live.id) { mutableStateOf(prefs.getInt("gift-balance", 250)) }
    var giftSpent by rememberSaveable(live.id) { mutableStateOf(prefs.getInt("gift-spent", 0)) }
    var pendingGiftIndex by rememberSaveable(live.id) { mutableStateOf(-1) }
    var animatedGift by rememberSaveable(live.id) { mutableStateOf("") }
    var soundOn by rememberSaveable(live.id) { mutableStateOf(true) }
    var micOpen by rememberSaveable(live.id) { mutableStateOf(isOwner && live.status == "live") }
    var voiceFeedback by rememberSaveable(live.id) { mutableStateOf<String?>(null) }
    var liveRecorder by remember(live.id) { mutableStateOf<MediaRecorder?>(null) }
    var liveRecordingFile by remember(live.id) { mutableStateOf<File?>(null) }
    var frontCamera by rememberSaveable(live.id) { mutableStateOf(true) }
    val youLabel = t("Vous", "You", "Yo")
    val giftOptions = listOf(
        Triple("Rose", "🌹", 5),
        Triple("Étoile", "⭐", 15),
        Triple("Feu", "🔥", 25),
        Triple("Cœur", "💙", 50),
        Triple("Couronne", "👑", 120),
        Triple("WAPI Premium", "🎁", 250),
    )

    fun saveLiveEvents(next: List<String>) {
        liveEvents = next.sorted().takeLast(80)
        prefs.edit().putStringSet("events-${live.id}", liveEvents.toSet()).apply()
    }

    fun appendLiveEvent(type: String, author: String, value: String, label: String = "", points: Int = 0) {
        val entry = listOf(System.currentTimeMillis().toString(), type, author.replace("|", " "), value.replace("|", " "), label.replace("|", " "), points.toString()).joinToString("|")
        saveLiveEvents(liveEvents + entry)
    }

    fun speakLive(text: String) {
        if (!soundOn) return
        tts.language = Locale.FRANCE
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "whappy-live-${System.currentTimeMillis()}")
    }

    fun sendGift(index: Int) {
        val gift = giftOptions.getOrNull(index) ?: return
        if (isOwner) { voiceFeedback = "Un hôte ne peut pas s’envoyer un cadeau à lui-même."; return }
        if (giftBalance < gift.third) { voiceFeedback = "Solde W-Coins insuffisant. Rechargez votre portefeuille test."; return }
        if (giftSpent + gift.third > 1_000) { voiceFeedback = "Plafond de sécurité atteint pour cette session test."; return }
        giftBalance -= gift.third
        giftSpent += gift.third
        prefs.edit().putInt("gift-balance", giftBalance).putInt("gift-spent", giftSpent).apply()
        appendLiveEvent("gift", youLabel, gift.second, gift.first, gift.third)
        reactionCount += gift.third
        animatedGift = gift.second
        speakLive("$youLabel a envoyé ${gift.first}")
        WhappySounds.reward(); WhappySounds.haptic(context, strong = true)
        giftPanel = false; pendingGiftIndex = -1
    }

    LaunchedEffect(animatedGift) { if (animatedGift.isNotBlank()) { delay(1_500); animatedGift = "" } }

    fun startLiveVoice() {
        if (!isOwner || live.status != "live") return
        runCatching { createVoiceRecorder(context) }
            .onSuccess { (recorder, file) ->
                liveRecorder = recorder
                liveRecordingFile = file
                micOpen = true
                voiceFeedback = "Micro live activé · sortie audio en haut-parleur."
            }
            .onFailure {
                micOpen = false
                voiceFeedback = "Le micro n’a pas pu démarrer. Vérifiez l’autorisation audio."
            }
    }

    fun stopLiveVoice() {
        val recorder = liveRecorder
        val file = liveRecordingFile
        runCatching { recorder?.stop() }
        recorder?.release()
        liveRecorder = null
        liveRecordingFile = null
        micOpen = false
        if (file != null && file.exists() && file.length() > 0L) {
            voiceFeedback = "Voix live capturée pour ce test gratuit."
        } else {
            file?.delete()
            voiceFeedback = "Micro live coupé."
        }
    }

    LaunchedEffect(soundOn, live.id) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = soundOn
        if (soundOn) speakLive("Son du live activé. ${live.title}")
    }
    DisposableEffect(live.id) {
        onDispose {
            runCatching { liveRecorder?.stop() }
            liveRecorder?.release()
            liveRecordingFile = null
            tts.shutdown()
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Retour", "Back", "Zonga"), tint = WhappyDark) }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(7.dp)).background(WhappyBlue).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(if (live.status == "live") "● LIVE" else t("PROGRAMMÉ", "SCHEDULED", "EBONGISAMI"), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }; Text(if (isOwner) t("  Vous êtes sur scène", "  You are on stage", "  Ozali na scène") else "  ${live.hostName}", color = WhappyDark, fontWeight = FontWeight.Black) }
                        Text(live.title, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isOwner && live.status == "live") TextButton(onClick = onEnd, enabled = !busy) { Text(t("Terminer", "End", "Sukisa"), color = WhappyBlue, fontWeight = FontWeight.Black) }
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(WhappyNavy), contentAlignment = Alignment.Center) {
                    if (isOwner && live.status == "live") LiveCameraPreview(frontCamera)
                    else { Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(62.dp)); Text(if (live.status == "live") t("Le direct est en cours", "Live now", "Live ezali kotambola") else t("Direct à venir", "Upcoming live", "Live ekoya"), Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Color.White, fontWeight = FontWeight.Bold) }
                    Row(Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(10.dp)).background(WhappyBlue).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text("● LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("  👀 ${live.viewerCount}", color = Color.White, fontSize = 10.sp) }
                    Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { soundOn = !soundOn }, colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = .48f), contentColor = Color.White), shape = RoundedCornerShape(12.dp)) { Text(if (soundOn) "🔊 Son" else "🔇 Muet", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        if (isOwner && live.status == "live") TextButton(onClick = { if (micOpen) stopLiveVoice() else startLiveVoice() }, colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = .48f), contentColor = Color.White), shape = RoundedCornerShape(12.dp)) { Text(if (micOpen) "🎙️ Micro ON" else "🎙️ Micro", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (isOwner) Text(t("Caméra de votre téléphone", "Your phone camera", "Caméra ya telefone na yo"), Modifier.align(Alignment.BottomCenter).padding(bottom = 15.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (animatedGift.isNotBlank()) Box(Modifier.align(Alignment.Center).size(150.dp).graphicsLayer { rotationX = -12f; rotationY = reactionCount * 13f; shadowElevation = 40f; cameraDistance = 18f }.clip(RoundedCornerShape(42.dp)).background(Brush.radialGradient(listOf(Color.White.copy(alpha = .96f), WhappySky.copy(alpha = .82f), WhappyBlue.copy(alpha = .15f)))), contentAlignment = Alignment.Center) { Text(animatedGift, fontSize = 74.sp) }
                }
                voiceFeedback?.let { value -> Text(value, Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 7.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                if (isOwner && live.status == "live") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                        FilledIconButton(onClick = { frontCamera = !frontCamera }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue, contentColor = Color.White)) { Icon(Icons.Rounded.FlipCameraAndroid, t("Retourner la caméra", "Flip camera", "Balola caméra")) }
                    }
                }
                if (live.productTitle.isNotBlank()) Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(start = 9.dp)) { Text(t("DEAL DU LIVE · TEST GRATUIT", "FREE LIVE DEAL TEST", "DEAL YA LIVE"), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(live.productTitle, color = WhappyDark, fontWeight = FontWeight.Bold) }; Text("0 FCFA", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black) } }
                if (liveEvents.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 116.dp).padding(horizontal = 16.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(liveEvents.takeLast(10), key = { it }) { raw ->
                        val parts = raw.split("|", limit = 6)
                        val type = parts.getOrElse(1) { "comment" }
                        val author = parts.getOrElse(2) { youLabel }
                        val value = parts.getOrElse(3) { "" }
                        val label = parts.getOrElse(4) { "" }
                        val cost = parts.getOrElse(5) { "0" }
                        Text(if (type == "gift") "$value $author a envoyé $label · $cost W-Coins" else "$author : $value", color = if (type == "gift") WhappyBlue else WhappyMuted, fontSize = 11.sp, fontWeight = if (type == "gift") FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (giftPanel) Column(Modifier.fillMaxWidth().background(WhappyAuroraSoft).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("CADEAUX 3D", color = WhappyDark, fontWeight = FontWeight.Black); Text("Solde : $giftBalance W-Coins · dépensé : $giftSpent/1000", color = WhappyMuted, fontSize = 10.sp) }; TextButton(enabled = giftBalance < 1_000, onClick = { giftBalance = (giftBalance + 250).coerceAtMost(1_000); prefs.edit().putInt("gift-balance", giftBalance).apply(); voiceFeedback = "+250 W-Coins de démonstration" }) { Text("+ Recharger") } }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) { giftOptions.forEachIndexed { index, gift -> Card(Modifier.width(112.dp).clickable { pendingGiftIndex = index }.graphicsLayer { rotationX = 5f; rotationY = if (index % 2 == 0) -4f else 4f; shadowElevation = 18f }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(gift.second, fontSize = 38.sp); Text(gift.first, color = WhappyDark, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1); Text("${gift.third} W-Coins", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold) } } } }
                    Text("Aucun paiement bancaire réel n’est effectué dans cette version.", color = WhappyMuted, fontSize = 9.sp)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(message, { value -> if (value.length > message.length) WhappySounds.typing(context); message = value.take(180) }, Modifier.weight(1f), placeholder = { Text(t("Écrire dans le Live", "Write in the live", "Koma na Live")) }, singleLine = true, shape = RoundedCornerShape(15.dp)); if (!isOwner) IconButton(onClick = { giftPanel = !giftPanel }) { Text("🎁", fontSize = 22.sp) }; IconButton(onClick = { if (message.isNotBlank()) { val value = message.trim(); appendLiveEvent("comment", youLabel, value); speakLive(value); WhappySounds.sent(); message = "" } }) { Icon(Icons.AutoMirrored.Rounded.Send, t("Envoyer", "Send", "Tinda"), tint = WhappyBlue) }; TextButton(onClick = { reactionCount += 1; WhappySounds.haptic(context) }) { Text("💙 $reactionCount") } }
            }
        }
    }
    if (pendingGiftIndex >= 0) {
        val gift = giftOptions[pendingGiftIndex]
        AlertDialog(
            onDismissRequest = { pendingGiftIndex = -1 },
            icon = { Text(gift.second, fontSize = 54.sp, modifier = Modifier.graphicsLayer { rotationX = -8f; rotationY = 12f; shadowElevation = 22f }) },
            title = { Text("Envoyer ${gift.first} ?", fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Prix : ${gift.third} W-Coins"); Text("Solde après envoi : ${(giftBalance - gift.third).coerceAtLeast(0)} W-Coins", color = WhappyMuted); if (giftBalance < gift.third) Text("Solde insuffisant", color = Color(0xFFE53935), fontWeight = FontWeight.Bold) } },
            confirmButton = { Button(enabled = giftBalance >= gift.third, onClick = { sendGift(pendingGiftIndex) }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { pendingGiftIndex = -1 }) { Text("Annuler") } },
        )
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

@Composable
private fun LiveDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, Boolean, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Communauté") }; var product by remember { mutableStateOf("") }; var hostMode by remember { mutableStateOf("personal") }; var visibility by remember { mutableStateOf("public") }
    val valid = title.trim().length >= 3 && category.isNotBlank() && !busy
    AlertDialog(onDismissRequest = onDismiss, title = { Column { Text("Créer un Live", fontWeight = FontWeight.Black); Text("Tout le monde peut passer en direct", color = WhappyBlue, fontSize = 11.sp) } }, text = { LazyColumn(Modifier.fillMaxWidth().height(470.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Text("Je passe en direct comme", color = WhappyDark, fontWeight = FontWeight.Black) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("personal" to "👤 Personnel", "creator" to "✦ Créateur", "business" to "▣ Business").forEach { option -> OutlinedButton(onClick = { hostMode = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (hostMode == option.first) WhappyDark else Color.White, contentColor = if (hostMode == option.first) Color.White else WhappyDark), shape = RoundedCornerShape(13.dp)) { Text(option.second, fontWeight = FontWeight.Bold) } } } }
        item { Text(when (hostMode) { "business" -> "Vendez, présentez un Deal et recevez des commandes."; "creator" -> "Animez votre communauté et utilisez votre WAPI."; else -> "Discutez, partagez un moment ou organisez un événement." }, color = WhappyMuted, fontSize = 11.sp) }
        item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Titre du direct") }, singleLine = true) }
        item { OutlinedTextField(category, { category = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Catégorie") }, singleLine = true) }
        if (hostMode == "business") item { OutlinedTextField(product, { product = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Produit ou Deal (facultatif)") }, singleLine = true) }
        item { Text("Audience", color = WhappyDark, fontWeight = FontWeight.Black) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("public" to "🌍 Public", "contacts" to "👥 Contacts", "private" to "🔒 Privé").forEach { option -> OutlinedButton(onClick = { visibility = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (visibility == option.first) Color.White else Color.White), shape = RoundedCornerShape(13.dp)) { Text(option.second) } } } }
        item { Text("Démarrer maintenant ouvre le studio après autorisation de la caméra et du micro. Programmer conserve le direct pour plus tard.", color = WhappyMuted, fontSize = 11.sp) }
    } }, confirmButton = { Button(enabled = valid, onClick = { onSave(title.trim(), category.trim(), product.trim(), true, hostMode, visibility) }) { Text(if (busy) "Ouverture…" else "Démarrer maintenant") } }, dismissButton = { Row { TextButton(onClick = onDismiss) { Text("Annuler") }; TextButton(enabled = valid, onClick = { onSave(title.trim(), category.trim(), product.trim(), false, hostMode, visibility) }) { Text("Programmer") } } })
}

private fun liveModeLabel(mode: String): String = when (mode) { "business" -> "BUSINESS"; "creator" -> "CRÉATEUR"; else -> "PERSONNEL" }

private enum class BusinessSection(val label: String) { DASHBOARD("Aperçu"), PAGES("Pages"), CATALOG("Catalogue"), DEALS("Deals"), ORDERS("Commandes"), PAYMENTS("Paiements"), ADS("Publicité"), INSIGHTS("Performances") }

@Composable
private fun BusinessScreen(
    pages: List<WhappyBusinessPage>,
    campaigns: List<WhappyCampaign>,
    deals: List<WhappyDeal>,
    paymentNotices: List<WhappyPaymentNotice>,
    preview: Boolean,
    busy: Boolean,
    onCreatePage: (String, String, String, String) -> Unit,
    onUpdatePage: (WhappyBusinessPage, String, String, String, String, String, String) -> Unit,
    onCreateCampaign: (WhappyCampaignDraft) -> Unit,
    onCreateDeal: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit,
    onUpdateDealStatus: (String, String) -> Unit,
    onMarkPaymentRead: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenTwin: () -> Unit,
) {
    var section by remember { mutableStateOf(BusinessSection.DASHBOARD) }
    var creatingPage by remember { mutableStateOf(false) }
    var creatingCampaign by remember { mutableStateOf(false) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) {
                Column(Modifier.padding(24.dp)) {
                    Text("WAPI BUSINESS SUITE", color = WhappyBlue, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text("Tout votre Business,\ndans une seule app.", Modifier.padding(top = 9.dp), color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                    Text("Catalogue, commandes, Deals, publicité, paiements et performances en temps réel.", Modifier.padding(top = 9.dp), color = Color.White, lineHeight = 20.sp)
                    Button(onClick = { if (visiblePages.isEmpty()) creatingPage = true else creatingDeal = true }, Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp), shape = RoundedCornerShape(15.dp)) { Icon(if (visiblePages.isEmpty()) Icons.Rounded.Add else Icons.Rounded.LocalOffer, null); Text(if (visiblePages.isEmpty()) "Créer ma page Business" else "Créer un Deal", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Notifications, null); Text(if (unread > 0) "$unread paiement(s) à consulter" else "Activer les alertes de paiement", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold) }
                }
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusinessSection.entries.forEach { item -> OutlinedButton(onClick = { section = item }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (section == item) Color.White else Color.White, contentColor = if (section == item) WhappyDark else WhappyMuted), shape = RoundedCornerShape(14.dp)) { Text(item.label, fontWeight = if (section == item) FontWeight.Bold else FontWeight.Medium) } }
            }
        }
        when (section) {
            BusinessSection.DASHBOARD -> {
                item { Text("Centre Business", fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                item { BusinessFeatureCard(Icons.Rounded.Storefront, "Profil Business", if (visiblePages.isEmpty()) "Créez une page publique professionnelle" else "${visiblePages.first().name} · @${visiblePages.first().handle}") { section = BusinessSection.PAGES } }
                item { BusinessFeatureCard(Icons.AutoMirrored.Rounded.ReceiptLong, "Catalogue", if (visibleDeals.isEmpty()) "Ajoutez vos produits et services" else "${visibleDeals.size} offre(s) · $availableStock unité(s) disponibles") { section = BusinessSection.CATALOG } }
                item { BusinessFeatureCard(Icons.Rounded.LocalOffer, "Deals", "Offres limitées, stock et ventes en un coup d’œil") { section = BusinessSection.DEALS } }
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
                items(visibleDeals, key = { "catalog-${it.id}" }) { deal -> CatalogCard(deal) { creatingDeal = true } }
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
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Publicité", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Black, color = WhappyDark); if (visiblePages.isNotEmpty()) TextButton(onClick = { creatingCampaign = true }) { Text("+ Campagne") } } }
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
    if (creatingPage) BusinessPageDialog(busy, onDismiss = { creatingPage = false }) { name, category, bio, city ->
        if (preview) localPages = listOf(WhappyBusinessPage("local-${System.currentTimeMillis()}", name, name.lowercase().replace(" ", "-"), category, bio, city.ifBlank { "Brazzaville" }, "demo-user")) + localPages
        else onCreatePage(name, category, bio, city)
        creatingPage = false
    }
    if (creatingCampaign) CampaignDialog(visiblePages, busy, onDismiss = { creatingCampaign = false }) { draft ->
        if (preview) localCampaigns = listOf(WhappyCampaign("local-${System.currentTimeMillis()}", draft.pageId, draft.pageName, draft.objective, draft.title, draft.dailyBudget, draft.days, "active")) + localCampaigns
        else onCreateCampaign(draft)
        creatingCampaign = false
    }
    if (creatingDeal && visiblePages.isNotEmpty()) DealDialog(visiblePages, busy, onDismiss = { creatingDeal = false }) { page, title, description, original, price, stock, days ->
        if (preview) localDeals = listOf(WhappyDeal("local-${System.currentTimeMillis()}", page.id, page.name, page.ownerId, title, description, original, price, stock, 0, System.currentTimeMillis() + days * 86_400_000L, "active")) + localDeals
        else onCreateDeal(page, title, description, original, price, stock, days)
        creatingDeal = false
    }
    editingPage?.let { page -> EditBusinessPageDialog(page, busy, onDismiss = { editingPage = null }) { name, category, bio, city, phone, website ->
        if (preview) previewPageUpdates = previewPageUpdates + (page.id to page.copy(name = name, category = category, bio = bio, city = city, phone = phone, website = website))
        else onUpdatePage(page, name, category, bio, city, phone, website)
        editingPage = null
    } }
}

@Composable
private fun BusinessFeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Black, color = WhappyDark); Text(body, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyBlue, fontSize = 24.sp) } }
}

@Composable
private fun CatalogCard(deal: WhappyDeal, onAddVariant: () -> Unit) {
    val remaining = (deal.stock - deal.sold).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(deal.title, color = WhappyDark, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMoney(deal.dealPrice), color = WhappyBlue, fontWeight = FontWeight.Black)
                Text("$remaining disponible(s) · ${deal.sold} vendu(s)", color = WhappyMuted, fontSize = 10.sp)
            }
            TextButton(onClick = onAddVariant) { Text("Ajouter +") }
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
private fun BusinessPageCard(page: WhappyBusinessPage, onEdit: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)).background(WhappyNavy), contentAlignment = Alignment.Center) { Text(initials(page.name), color = WhappyBlue, fontWeight = FontWeight.Black) }; Column(Modifier.weight(1f).padding(start = 12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(page.name, fontWeight = FontWeight.Black, color = WhappyDark); Icon(Icons.Rounded.Verified, null, Modifier.padding(start = 5.dp).size(15.dp), tint = WapiVerifiedGray) }; Text("@${page.handle} · ${page.category}", color = WhappyBlue, fontSize = 11.sp); Text(page.bio.ifBlank { page.city }, Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2) }; TextButton(onClick = onEdit) { Text("Modifier") } }; FlowRow(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("📍 ${page.city}", color = WhappyMuted, fontSize = 10.sp); if (page.phone.isNotBlank()) Text("☎ ${page.phone}", color = WhappyMuted, fontSize = 10.sp); if (page.website.isNotBlank()) Text("↗ ${page.website}", color = WhappyBlue, fontSize = 10.sp) } } } }

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
                Surface(color = if (campaign.status == "active") WhappyBlue.copy(alpha = .10f) else WhappySurface, shape = RoundedCornerShape(8.dp)) { Text(if (campaign.status == "active") "EN DIFFUSION" else campaign.status.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (campaign.status == "active") WhappyBlue else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black) }
            }
            Text("${campaign.pageName} · $placement · ${campaign.city.ifBlank { "Zone nationale" }}", Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp)
            if (campaign.audience.isNotBlank()) Text(campaign.audience, Modifier.padding(top = 4.dp), color = WhappyDark, fontSize = 11.sp)
            Text("${formatMoney(campaign.dailyBudget)}/jour · ${campaign.days} jours · portée estimée ${campaign.estimatedReach.coerceAtLeast(120)}", Modifier.padding(top = 6.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DealDialog(pages: List<WhappyBusinessPage>, busy: Boolean, onDismiss: () -> Unit, onSave: (WhappyBusinessPage, String, String, Long, Long, Int, Int) -> Unit) {
    var step by remember { mutableStateOf(0) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { index -> Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (index <= step) WhappyBlue else Color.White)) } }
            }
        },
        text = {
            AnimatedContent(targetState = step, label = "deal-creator") { current ->
                when (current) {
                    0 -> Column(Modifier.fillMaxWidth().height(380.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("Publié par", color = WhappyDark, fontWeight = FontWeight.Black)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { pages.forEach { candidate -> OutlinedButton(onClick = { page = candidate }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (page.id == candidate.id) WhappyDark else Color.White, contentColor = if (page.id == candidate.id) Color.White else WhappyDark), shape = RoundedCornerShape(13.dp)) { Text(candidate.name) } } }
                        OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nom du Deal") }, supportingText = { Text("Ex. Pack rentrée · 48 heures") }, singleLine = true)
                        OutlinedTextField(description, { description = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("Pourquoi cette offre est exceptionnelle ?") }, minLines = 4)
                    }
                    1 -> Column(Modifier.fillMaxWidth().height(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text("RÉDUCTION", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(if (discount > 0) "-$discount %" else "À calculer", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(original, { original = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Prix normal") }, suffix = { Text("F") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(price, { price = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Prix Deal") }, suffix = { Text("F") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(stock, { stock = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Durée") }, suffix = { Text("j") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                        Text("WAPI affichera automatiquement le stock restant et la date de fin pour créer un sentiment d’urgence.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    else -> Column(Modifier.fillMaxWidth().height(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Voici ce que vos clients verront", color = WhappyDark, fontWeight = FontWeight.Black)
                        DealPreviewCard(page.name, title, description, originalValue, priceValue, stockValue, daysValue, discount)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("⚡ Urgence", "✓ Stock réel", "🔔 Alertes").forEach { tag -> Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 9.dp, vertical = 6.dp)) { Text(tag, color = WhappyDark, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = currentValid && !busy, onClick = { if (step < 2) step += 1 else onSave(page, title.trim(), description.trim(), originalValue, priceValue, stockValue, daysValue) }, shape = RoundedCornerShape(14.dp)) { Text(if (busy) "Publication…" else if (step < 2) "Continuer →" else "Publier le Deal", fontWeight = FontWeight.Bold) } },
        dismissButton = { Row { TextButton(onClick = onDismiss) { Text("Annuler") }; if (step > 0) TextButton(onClick = { step -= 1 }) { Text("← Retour") } } },
    )
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
private fun BusinessPageDialog(busy:Boolean,onDismiss:()->Unit,onSave:(String,String,String,String)->Unit){
    var name by remember{mutableStateOf("")};var category by remember{mutableStateOf("")};var bio by remember{mutableStateOf("")};var city by remember{mutableStateOf("Brazzaville")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Créer une page Business")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),label={Text("Nom public")},singleLine=true);OutlinedTextField(category,{category=it},Modifier.fillMaxWidth(),label={Text("Catégorie")},singleLine=true);OutlinedTextField(bio,{bio=it.take(400)},Modifier.fillMaxWidth(),label={Text("Présentation")},minLines=3);OutlinedTextField(city,{city=it},Modifier.fillMaxWidth(),label={Text("Ville")},singleLine=true)}},confirmButton={Button(enabled=name.trim().length>=2&&category.isNotBlank()&&!busy,onClick={onSave(name,category,bio,city)}){Text(if(busy)"Création…" else "Créer")}},dismissButton={TextButton(onClick=onDismiss){Text("Annuler")}})
}

@Composable
private fun EditBusinessPageDialog(page: WhappyBusinessPage, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String) -> Unit) {
    var name by remember(page.id) { mutableStateOf(page.name) }; var category by remember(page.id) { mutableStateOf(page.category) }; var bio by remember(page.id) { mutableStateOf(page.bio) }; var city by remember(page.id) { mutableStateOf(page.city) }; var phone by remember(page.id) { mutableStateOf(page.phone) }; var website by remember(page.id) { mutableStateOf(page.website) }
    AlertDialog(onDismissRequest = onDismiss, title = { Column { Text("Profil Business", fontWeight = FontWeight.Black); Text("@${page.handle}", color = WhappyBlue, fontSize = 11.sp) } }, text = { LazyColumn(Modifier.fillMaxWidth().height(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Nom public") }, singleLine = true) }
        item { OutlinedTextField(category, { category = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Catégorie") }, singleLine = true) }
        item { OutlinedTextField(bio, { bio = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("Présentation") }, minLines = 3) }
        item { OutlinedTextField(city, { city = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Ville") }, singleLine = true) }
        item { OutlinedTextField(phone, { phone = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Téléphone professionnel") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true) }
        item { OutlinedTextField(website, { website = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("Site web ou réseau social") }, singleLine = true) }
        item { Text("Ces informations seront visibles sur votre page publique Business.", color = WhappyMuted, fontSize = 10.sp) }
    } }, confirmButton = { Button(enabled = name.trim().length >= 2 && category.isNotBlank() && !busy, onClick = { onSave(name.trim(), category.trim(), bio.trim(), city.trim(), phone.trim(), website.trim()) }) { Text(if (busy) "Enregistrement…" else "Enregistrer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

@Composable
private fun CampaignDialog(
    pages: List<WhappyBusinessPage>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (WhappyCampaignDraft) -> Unit,
) {
    var selectedPage by remember { mutableStateOf(pages.first()) }
    var objective by remember { mutableStateOf("messages") }
    var placement by remember { mutableStateOf("profile_story") }
    var destination by remember { mutableStateOf("message") }
    var title by remember { mutableStateOf("") }
    var creative by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("Public local") }
    var city by remember { mutableStateOf("Brazzaville") }
    var budget by remember { mutableStateOf("2500") }
    var days by remember { mutableStateOf("7") }
    val dailyBudget = budget.toLongOrNull() ?: 0L
    val duration = days.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle campagne") },
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
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().length >= 2 && creative.trim().length >= 2 && dailyBudget >= 500 && duration in 1..90 && !busy,
                onClick = { onSave(WhappyCampaignDraft(selectedPage.id, selectedPage.name, objective, title, creative, if (destination == "message") "Envoyer un message" else "Découvrir", audience, city, dailyBudget, duration, placement, destination)) },
            ) { Text(if (busy) "Lancement…" else "Lancer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(14.dp)) { Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text(label, color = WhappyMuted, fontSize = 10.sp) } }
}

@Composable
private fun ProfileScreen(
    name: String,
    founder: Boolean,
    phone: String,
    photoUrl: String,
    twinReadiness: Int,
    preview: Boolean,
    busy: Boolean,
    onUpdatePhoto: (Uri, String) -> Unit,
    onOpenWhappies: () -> Unit,
    onSignOut: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenSpace: (WhappyTab) -> Unit,
    language: WhappyLanguage,
    onLanguageChange: (WhappyLanguage) -> Unit,
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
    var experimentalTools by rememberSaveable { mutableStateOf(prefs.getBoolean("experimental_tools", true)) }
    var storageUsage by remember { mutableStateOf(WapiMediaStore.usage(context)) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        localPhoto = uri.toString()
        if (!preview) onUpdatePhoto(uri, context.contentResolver.getType(uri) ?: "image/jpeg")
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(localPhoto, name, 78.dp, modifier = if (localPhoto.isBlank()) Modifier else Modifier.clickable { previewPhoto = localPhoto })
                    IconButton(onClick = { photoPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.size(32.dp).clip(CircleShape).background(WhappyBlue)) { Icon(Icons.Rounded.Photo, t("Changer la photo", "Change photo", "Bongola foto"), tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
                Column(Modifier.weight(1f).padding(start = 15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (founder) WhappyIdentity.founderName else name, Modifier.weight(1f, fill = false), fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.Verified, null, tint = WapiVerifiedGray, modifier = Modifier.padding(start = 5.dp).size(17.dp))
                    }
                    Text(if (preview) t("Mode démonstration", "Demo mode", "Mode ya komeka") else phone, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp)
                    Text(if (founder) WhappyIdentity.founderBadgeLabel else t("Compte vérifié", "Verified account", "Compte endimami"), Modifier.padding(top = 5.dp), color = WapiVerifiedGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { if (localPhoto.isNotBlank()) previewPhoto = localPhoto }, enabled = localPhoto.isNotBlank()) { Text(t("Voir", "View", "Tala"), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { showingMyCode = true }) { Text(t("Mon code", "My code", "Code na ngai"), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        Text(t("La photo reste fixe. Touchez-la pour l’agrandir.", "Your photo stays fixed. Tap it to zoom.", "Foto etikali fixe. Finá yango mpo na kokómisa monene."), Modifier.padding(horizontal = 20.dp, vertical = 2.dp), color = WhappyMuted, fontSize = 10.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWhappies), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = WhappyNavy)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text("MON WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Mon double numérique", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Image, voix, mouvements et missions", color = Color.White, fontSize = 11.sp) }; Text("›", color = WhappyBlue, fontSize = 26.sp) } }
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
        item { Card(Modifier.fillMaxWidth().clickable { settingDialog = "Langue" }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Language, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(t("Langue de l’application", "App language", "Lokota ya application"), fontWeight = FontWeight.Bold, color = WhappyDark); Text(language.label, color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 23.sp) } } }
        items(listOf("Confidentialité" to "Contrôlez qui peut vous contacter", "Notifications" to "Messages, appels et commandes", "Live & cadeaux test" to "Audio du direct, W-Coins et modération", "Stockage et données" to "Médias et utilisation réseau", "Aide et sécurité" to "Assistance et appareils connectés")) { setting ->
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
    if (showingMyCode) WhappyCodeDialog(name = name, phone = phone, onDismiss = { showingMyCode = false })
    settingDialog?.let { section ->
        AlertDialog(
            onDismissRequest = { settingDialog = null },
            title = { Text(section, fontWeight = FontWeight.Black) },
            text = {
                when (section) {
                    "Langue" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Choisissez la langue utilisée dans la navigation et les espaces principaux.", "Choose the language used in navigation and main spaces.", "Pona lokota ya navigation mpe bisika ya ntina."), color = WhappyMuted)
                        WhappyLanguage.entries.forEach { option ->
                            Button(
                                onClick = { onLanguageChange(option); settingDialog = null },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (language == option) WhappyBlue else Color.White, contentColor = if (language == option) Color.White else WhappyBlue),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(option.label, fontWeight = FontWeight.Bold) }
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
                    "Live & cadeaux test" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Mode expérimental gratuit", color = WhappyMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Portefeuille cadeaux test", fontWeight = FontWeight.Bold); Text("Les spectateurs utilisent des W-Coins de démonstration avec confirmation et plafond.", color = WhappyMuted, fontSize = 10.sp) }
                            Switch(experimentalTools, { enabled -> experimentalTools = enabled; prefs.edit().putBoolean("experimental_tools", enabled).apply() })
                        }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7FF))) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LiveTv, null, tint = WhappyBlue); Text("  Voix du live active", fontWeight = FontWeight.Bold, color = WhappyDark) }
                                Text("Le direct doit sortir sur le haut-parleur de l’appareil. Si Android bloque le son, vérifiez le volume média et les permissions audio.", color = WhappyMuted, fontSize = 11.sp)
                            }
                        }
                        Text("Cette option prépare l’interface avant le paiement réel : les cadeaux sont marqués TEST pour éviter toute confusion.", color = WhappyMuted, fontSize = 11.sp)
                    }
                    "Stockage et données" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Économiseur de données", fontWeight = FontWeight.Bold); Text("Réduit le chargement automatique des médias", color = WhappyMuted, fontSize = 11.sp) }; Switch(dataSaver, { dataSaver = it; prefs.edit().putBoolean("data_saver", it).apply() }) }
                        Text("Cache temporaire : ${formatStorageBytes(storageUsage.cacheBytes)}", color = WhappyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Médias conservés : ${formatStorageBytes(storageUsage.mediaBytes)} · Envois en attente : ${formatStorageBytes(storageUsage.outboxBytes)}", color = WhappyMuted, fontSize = 11.sp)
                        OutlinedButton(onClick = { WapiMediaStore.clearRebuildableCache(context); storageUsage = WapiMediaStore.usage(context) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Delete, null); Text("  Vider uniquement le cache") }
                        Text("Le cache peut être recréé depuis le cloud. Les messages, les pièces jointes en attente et les médias conservés ne sont jamais supprimés par cette action.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("WAPI réunit vos conversations, appels, achats, directs et services. En cas de problème, contactez l’assistance depuis cet appareil.", color = WhappyDark); OutlinedButton(onClick = { uriHandler.openUri("mailto:support@whappy.chat?subject=Aide%20WHAPPY") }, Modifier.fillMaxWidth()) { Text("Contacter l’assistance") } }
                }
            },
            confirmButton = { TextButton(onClick = { settingDialog = null }) { Text("Terminé") } },
        )
    }
}

@Composable
private fun ProfileControlCenter(onOpenSpace: (WhappyTab) -> Unit) {
    val options = listOf(
        Triple(WhappyTab.STORIES, "Ma Story", "Texte, photo, vidéo et podcast"),
        Triple(WhappyTab.WEPI, "WEPI", "Assistant privé et actions intelligentes"),
        Triple(WhappyTab.MESSAGES, "Messages", "Discussions et demandes"),
        Triple(WhappyTab.CALLS, "Appels", "Voix, vidéo et historique"),
        Triple(WhappyTab.CONTACTS, "Groupes & chaînes", "Communautés internes"),
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
        WhappyTab.CONTACTS to Icons.Rounded.Groups,
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
private fun WhappyCodeDialog(name: String, phone: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val normalized = remember(phone) { PhoneNumberFormatter.normalize("+242", phone).orEmpty() }
    val qrCode = remember(normalized) { normalized.takeIf { it.isNotBlank() }?.let(::createWhappyQr) }
    val contactLink = remember(normalized) { normalized.takeIf { it.isNotBlank() }?.let { "https://whappy.chat/contact/${Uri.encode(it)}" }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mon code WAPI") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Présentez ce code à ${name.ifBlank { "votre contact" }} pour être ajouté instantanément.", color = WhappyMuted)
                if (qrCode != null) {
                    Image(
                        bitmap = qrCode.asImageBitmap(),
                        contentDescription = "Code QR WAPI de $name",
                        modifier = Modifier.padding(top = 18.dp).size(210.dp).clip(RoundedCornerShape(18.dp)),
                    )
                    Text(normalized, Modifier.padding(top = 11.dp), color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text(contactLink, Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 9.sp)
                    OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mon lien WAPI", contactLink)) }, Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(13.dp)) { Text("Copier mon lien") }
                    Button(onClick = { shareWhappyLink(context, "Contact WAPI", contactLink) }, Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.Share, null); Text("  Partager mon contact") }
                } else {
                    Text("Votre numéro sécurisé sera disponible ici dès que votre profil sera synchronisé.", Modifier.padding(top = 16.dp), color = WhappyMuted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
    )
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
).let { matrix ->
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.rgb(16, 46, 59) else android.graphics.Color.WHITE)
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
    val bytes = when {
        source.startsWith("http://") || source.startsWith("https://") -> loadRemoteImageBytes(context, source)
        source.startsWith("content://") || source.startsWith("file://") -> context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
        else -> runCatching { File(source).takeIf { it.exists() }?.readBytes() }.getOrNull()
    } ?: return null
    val decoded = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return decoded?.asImageBitmap()
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes o"
    bytes < 1_024L * 1_024L -> "%.0f Ko".format(Locale.FRANCE, bytes / 1_024.0)
    bytes < 1_024L * 1_024L * 1_024L -> "%.1f Mo".format(Locale.FRANCE, bytes / (1_024.0 * 1_024.0))
    else -> "%.2f Go".format(Locale.FRANCE, bytes / (1_024.0 * 1_024.0 * 1_024.0))
}

private fun loadRemoteImageBytes(context: Context, source: String): ByteArray? {
    val key = WapiMediaStore.keyFor(source)
    WapiMediaStore.readCache(context, key, maxBytes = 8L * 1024L * 1024L)?.let { return it }
    return runCatching {
        val connection = URL(source).openConnection().apply {
            connectTimeout = 7_000
            readTimeout = 10_000
            useCaches = true
        }
        val downloaded = connection.getInputStream().use { it.readBytes() }
        if (downloaded.size in 1..(8 * 1024 * 1024)) {
            WapiMediaStore.writeCache(context, key, downloaded, maxBytes = 8L * 1024L * 1024L)
            downloaded
        } else null
    }.getOrNull()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun UserAvatar(photoUrl: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(photoUrl) { mutableStateOf(AvatarMemoryCache.items[photoUrl]) }
    LaunchedEffect(photoUrl) {
        if (photoUrl.isBlank()) return@LaunchedEffect
        AvatarMemoryCache.items[photoUrl]?.let { cached -> bitmap = cached; return@LaunchedEffect }
        val loaded = withContext(Dispatchers.IO) { runCatching { loadImageBitmap(context, photoUrl) }.getOrNull() }
        if (loaded != null) {
            AvatarMemoryCache.items[photoUrl] = loaded
            bitmap = loaded
        }
    }
    Box(modifier.size(size).clip(RoundedCornerShape((size.value * .22f).dp)).background(WhappyBlue), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap = bitmap!!, contentDescription = "Photo de profil de $name", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(initials(name), color = Color.White, fontSize = (size.value * 0.31f).sp, fontWeight = FontWeight.Black)
    }
}

private object AvatarMemoryCache {
    val items = ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(74.dp).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChatBubble, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
                Text(title, Modifier.padding(top = 7.dp), fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark, textAlign = TextAlign.Center)
                Text(body, color = WhappyMuted, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Text("WAPI · votre espace est prêt", Modifier.padding(top = 7.dp), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "WH" }

private fun formatTime(timestamp: Long): String = if (timestamp <= 0) "" else SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(timestamp))

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

private fun formatMoney(amount: Long): String = String.format(Locale.FRANCE, "%,d FCFA", amount).replace('\u202f', ' ')

private fun formatShortDate(timestamp: Long): String = if (timestamp <= 0) "—" else SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(timestamp))
