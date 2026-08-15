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
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LiveTv
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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

private val WhappyBlue = Color(0xFF2D2E83)
private val WhappyDark = Color(0xFF1B1C58)
private val WhappyInk = Color(0xFF152D37)
private val WhappyMuted = Color(0xFF717D82)
private val WhappyBackground = Color(0xFFF5F9FA)
private val WhappyLine = Color(0xFFE0E8EC)

private val demoConversations = listOf(
    WhappyConversation("demo-amina", WhappyMember("amina", "Amina M.", "+242 06 555 01 01"), "Le troc est accepté pour le canapé ?", System.currentTimeMillis(), true),
    WhappyConversation("demo-junior", WhappyMember("junior", "Junior K.", "+242 05 884 21 60"), "Je peux livrer cet après-midi.", System.currentTimeMillis() - 3_600_000, false),
    WhappyConversation("demo-mokabi", WhappyMember("mokabi", "Mokabi Studio"), "Votre commande est prête ✦", System.currentTimeMillis() - 7_200_000, false),
)

private val whappyFounderDisplayName = WhappyIdentity.founderName
private val whappyFounderChannelName = WhappyIdentity.founderChannelName
private val whappyFounderChannelTagline = WhappyIdentity.founderChannelTagline

private val demoMessages = listOf(
    WhappyMessage("1", "Bonjour Cyril, le canapé est toujours disponible.", "amina", System.currentTimeMillis() - 180_000),
    WhappyMessage("2", "Bonjour Amina. Le troc est accepté ?", "demo-user", System.currentTimeMillis() - 120_000),
    WhappyMessage("3", "Oui, envoyez-moi votre proposition.", "amina", System.currentTimeMillis() - 60_000),
)

private val demoListings = listOf(
    WhappyListing("demo-1", "MacBook Air M3 · Comme neuf", "750 000 FCFA", "Poto-Poto", "Junior K.", "junior"),
    WhappyListing("demo-2", "Canapé modulable en velours", "Échange accepté", "Bacongo", "Maison Noki", "noki", "troc"),
    WhappyListing("demo-3", "Sneakers édition limitée", "85 000 FCFA", "Centre-ville", "Mokabi Store", "mokabi"),
)

private val demoBusinessPages = listOf(
    WhappyBusinessPage("demo-page", "Mokabi Studio", "mokabi-studio", "Mode & création", "Création contemporaine inspirée de Brazzaville.", "Brazzaville", "demo-user", "+242 06 555 01 01", "mokabi.studio"),
)

private val demoCampaigns = listOf(
    WhappyCampaign("demo-campaign", "demo-page", "Mokabi Studio", "messages", "Nouvelle collection N’Tela", 2_500, 7, "active"),
)

private val demoLives = listOf(
    WhappyLive("demo-live-1", "mokabi", "Mokabi Studio", "Nouvelle collection N’Tela", "Mode", "Chemise N’Tela", "live", 1284, System.currentTimeMillis() - 1_200_000),
    WhappyLive("demo-live-2", "junior", "Junior Tech", "Les bonnes affaires smartphones", "Tech", "Galaxy S25", "live", 438, System.currentTimeMillis() - 420_000),
    WhappyLive("demo-live-3", "demo-user", whappyFounderDisplayName, "Mon prochain direct Business", "Business", "", "scheduled", 0, System.currentTimeMillis() + 3_600_000),
)

private val demoChannels = listOf(
    WhappyChannel("demo-channel-city", "Brazzaville Maintenant", "Actualités utiles, sorties et opportunités de la ville.", "Actualités", "city-owner", "WHAPPY Local", listOf("demo-user"), 12_480, 128, "Les bons plans du week-end sont publiés.", System.currentTimeMillis() - 240_000, true),
    WhappyChannel("demo-channel-market", "Bons plans WHAPPY", "Promotions vérifiées et nouvelles offres du Marché WHAPPY.", "Shopping", "market-owner", "WHAPPY Marché", emptyList(), 8_205, 74, "Nouvelle sélection tech à moins de 100 000 FCFA.", System.currentTimeMillis() - 3_600_000, true),
    WhappyChannel("demo-channel-me", whappyFounderChannelName, whappyFounderChannelTagline, "Créateurs", "demo-user", whappyFounderDisplayName, listOf("demo-user"), 1, 1, "Bienvenue dans ma chaîne WHAPPY.", System.currentTimeMillis() - 86_400_000),
)

private val demoChannelPosts = listOf(
    WhappyChannelPost("demo-post-1", "Bienvenue dans notre chaîne. Activez les notifications pour ne manquer aucune publication.", "city-owner", "WHAPPY Local", System.currentTimeMillis() - 86_400_000, mapOf("amina" to "❤️", "junior" to "👍"), pinned = true),
    WhappyChannelPost("demo-post-2", "Ce week-end : marché des créateurs samedi à Poto-Poto, de 10 h à 18 h.", "city-owner", "WHAPPY Local", System.currentTimeMillis() - 240_000, mapOf("amina" to "🔥")),
)

private val demoDeals = listOf(
    WhappyDeal("demo-deal-1", "demo-page", "Mokabi Studio", "demo-user", "Drop N’Tela · 48 h", "Livraison offerte à Brazzaville.", 65_000, 49_000, 30, 12, System.currentTimeMillis() + 172_800_000, "active"),
    WhappyDeal("demo-deal-2", "demo-page", "Mokabi Studio", "demo-user", "Pack créateur Business", "Identité visuelle et kit réseaux sociaux.", 150_000, 99_000, 10, 4, System.currentTimeMillis() + 432_000_000, "active"),
)

private val demoPaymentNotices = listOf(
    WhappyPaymentNotice("demo-payment-1", "demo-page", "demo-deal-1", "Amina M.", 49_000, "FCFA", "Mobile Money", "paid", System.currentTimeMillis() - 180_000, false),
    WhappyPaymentNotice("demo-payment-2", "demo-page", "demo-deal-2", "Junior K.", 99_000, "FCFA", "Carte", "paid", System.currentTimeMillis() - 3_600_000, true),
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
fun WhappyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WhappyBlue,
            onPrimary = Color.White,
            background = WhappyBackground,
            onBackground = WhappyInk,
            surface = Color.White,
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
    onSendMedia: (Uri, String, String, String, Int) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCloseChannel: () -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
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
        onSendMedia = onSendMedia,
        onReactMessage = onReactMessage,
        onDeleteMessage = onDeleteMessage,
        onEditMessage = onEditMessage,
        onTyping = onTyping,
        onHandleWhappyLink = onHandleWhappyLink,
        onOpenChannel = onOpenChannel,
        onCloseChannel = onCloseChannel,
        onCreateChannel = onCreateChannel,
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
            Image(painterResource(R.drawable.whappy_icon), "Logo WHAPPY", Modifier.size(92.dp))
            Text("WHAPPY", Modifier.padding(top = 18.dp), color = WhappyBlue, fontWeight = FontWeight.Black, fontSize = 28.sp)
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
                                AuthStage.PHONE -> "Votre numéro WHAPPY"
                                AuthStage.CODE -> "Vérification rapide"
                                AuthStage.PROFILE -> "Finalisez votre profil"
                            },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = WhappyDark,
                        )
                        Text(
                            when (state.stage) {
                                AuthStage.PHONE -> "Une seule vérification. Ensuite, WHAPPY s’ouvre directement sur cet appareil."
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
                                    OutlinedTextField(phone, { phone = it }, label = { Text("Téléphone") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { controller.sendCode(country, phone) }))
                                }
                                PrimaryAction("Continuer", state.busy) { controller.sendCode(country, phone) }
                                Text("Compte existant : vos conversations et votre profil seront restaurés automatiquement.", Modifier.padding(top = 12.dp), color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            AuthStage.CODE -> {
                                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Code à 6 chiffres") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { controller.verifyCode(code) }))
                                PrimaryAction("Vérifier et entrer", state.busy) { controller.verifyCode(code) }
                                TextButton(onClick = controller::resendCode, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Renvoyer le SMS") }
                                TextButton(onClick = controller::back, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Modifier le numéro") }
                            }
                            AuthStage.PROFILE -> {
                                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Votre nom") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { controller.saveProfile(name, onProfileSaved) }))
                                PrimaryAction("Entrer dans WHAPPY", state.busy) { controller.saveProfile(name, onProfileSaved) }
                            }
                        }
                        if (state.status.isNotBlank()) Text(state.status, Modifier.padding(top = 12.dp), color = WhappyBlue, fontSize = 13.sp)
                        if (state.error.isNotBlank()) Text(state.error, Modifier.padding(top = 12.dp), color = Color(0xFFB3261E), fontSize = 13.sp)
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
    onSendMedia: (Uri, String, String, String, Int) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
    onOpenChannel: (WhappyChannel) -> Unit,
    onCloseChannel: () -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
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
    BackHandler(enabled = selected != null || selectedChannel != null || showTwinStudio || showActivityCenter) {
        if (showActivityCenter) showActivityCenter = false
        else if (showTwinStudio) showTwinStudio = false
        else if (preview && selectedChannel != null) previewChannel = null
        else if (preview) previewConversation = null
        else if (selectedChannel != null) onCloseChannel()
        else onCloseConversation()
    }
    if (state.error != null) AlertDialog(onDismissRequest = onDismissError, confirmButton = { TextButton(onClick = onDismissError) { Text("Fermer") } }, title = { Text("WHAPPY") }, text = { Text(state.error) })
    if (showActivityCenter) ActivityCenterDialog(
        notices = activityNotices,
        lives = activityLives,
        locallyRead = locallyReadNotices,
        onRead = { id -> locallyReadNotices = locallyReadNotices + id; if (!preview) onMarkPaymentRead(id) },
        onOpenBusiness = { showActivityCenter = false; onTab(WhappyTab.BUSINESS) },
        onOpenLive = { showActivityCenter = false; onTab(WhappyTab.LIVE) },
        onDismiss = { showActivityCenter = false },
    )
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WhappyBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selected == null && selectedChannel == null && !showTwinStudio) WhappyBottomBar(currentTab, onTab)
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
                        if (preview) previewMessages = previewMessages + WhappyMessage("local-${System.currentTimeMillis()}", if (kind == "audio") "Note vocale" else "Photo", "demo-user", System.currentTimeMillis(), kind, uri.toString(), name, duration)
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
                AnimatedContent(currentTab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "whappy-tab") { tab ->
            when (tab) {
                WhappyTab.MOMENTS -> MomentsScreen(state.twinProfile?.readiness ?: 0, onTab, onOpenWhappies = { showTwinStudio = true })
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
                            onSubscribeChannel = onSubscribeChannel,
                            onHandleWhappyLink = onHandleWhappyLink,
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
                        WhappyTab.GAMES -> GamesScreen(onBack = { onTab(WhappyTab.MOMENTS) })
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(subtitle: String, avatar: Boolean, name: String = "", photoUrl: String = "", unread: Int = 0, onActivity: () -> Unit = {}, onProfile: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.whappy_icon), "Logo WHAPPY", Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("WHAPPY", color = WhappyBlue, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Text(subtitle, color = WhappyMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onActivity) { Box(contentAlignment = Alignment.TopEnd) { Icon(Icons.Rounded.Notifications, "Centre d’activité", tint = WhappyDark); if (unread > 0) Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFFF3B5C)), contentAlignment = Alignment.Center) { Text(unread.coerceAtMost(9).toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black) } } }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Text("Centre d’activité", fontWeight = FontWeight.Black); Text("Paiements, Deals et directs", color = WhappyMuted, fontSize = 11.sp) } },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(430.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val liveNow = lives.filter { it.status == "live" }.take(2)
                if (liveNow.isNotEmpty()) {
                    item { Text("EN DIRECT", color = Color(0xFFFF3B5C), fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    items(liveNow, key = { "activity-live-${it.id}" }) { live ->
                        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenLive), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F3))) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LiveTv, null, tint = Color(0xFFFF3B5C)); Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(live.title, color = WhappyDark, fontWeight = FontWeight.Bold, maxLines = 1); Text("${live.hostName} · ${live.viewerCount} spectateurs", color = WhappyMuted, fontSize = 10.sp) }; Text("›", color = Color(0xFFFF3B5C), fontSize = 22.sp) } }
                    }
                }
                item { Text("PAIEMENTS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp)) }
                if (notices.isEmpty()) item { Text("Aucune nouvelle transaction.", color = WhappyMuted, fontSize = 12.sp) }
                items(notices.take(8), key = { "activity-payment-${it.id}" }) { notice ->
                    val unread = !notice.read && notice.id !in locallyRead
                    Card(Modifier.fillMaxWidth().clickable { if (unread) onRead(notice.id) else onOpenBusiness() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (unread) Color(0xFFEAF7FC) else Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Payments, null, tint = Color(0xFF12824B)); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text("Paiement de ${notice.buyerName}", color = WhappyDark, fontWeight = FontWeight.Bold); Text(notice.provider, color = WhappyMuted, fontSize = 10.sp) }; Text("+${formatMoney(notice.amount)}", color = Color(0xFF12824B), fontSize = 11.sp, fontWeight = FontWeight.Black) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenBusiness) { Text("Ouvrir Business") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun WhappyBottomBar(selected: WhappyTab, onTab: (WhappyTab) -> Unit) {
    val icons = mapOf(
        WhappyTab.MOMENTS to Icons.Rounded.Home,
        WhappyTab.MESSAGES to Icons.Rounded.ChatBubble,
        WhappyTab.CONTACTS to Icons.Rounded.Person,
        WhappyTab.CALLS to Icons.Rounded.Phone,
        WhappyTab.MARKET to Icons.Rounded.Storefront,
        WhappyTab.LIVE to Icons.Rounded.LiveTv,
        WhappyTab.GAMES to Icons.Rounded.Bolt,
        WhappyTab.SERVICES to Icons.Rounded.Payments,
        WhappyTab.BUSINESS to Icons.Rounded.BusinessCenter,
        WhappyTab.PROFILE to Icons.Rounded.Person,
    )
    val visibleTabs = listOf(WhappyTab.MOMENTS, WhappyTab.CONTACTS, WhappyTab.MESSAGES, WhappyTab.CALLS, WhappyTab.MARKET, WhappyTab.LIVE, WhappyTab.GAMES, WhappyTab.SERVICES)
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
        visibleTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onTab(tab) },
                icon = { Icon(icons.getValue(tab), tab.label) },
                label = { Text(tab.label, fontSize = 9.sp, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = WhappyBlue, selectedTextColor = WhappyDark, indicatorColor = Color(0xFFE1F3FB)),
            )
        }
    }
}

@Composable
private fun MomentsScreen(twinReadiness: Int, onTab: (WhappyTab) -> Unit, onOpenWhappies: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whappy_consumer", Context.MODE_PRIVATE) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var momentTitle by rememberSaveable { mutableStateOf("") }
    var momentBody by rememberSaveable { mutableStateOf("") }
    var personalMoments by remember { mutableStateOf(prefs.getStringSet("moments", emptySet()).orEmpty().toList().sortedDescending()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                Column(Modifier.padding(24.dp)) {
                    Text("AUJOURD’HUI · BRAZZAVILLE", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("Tout peut devenir\nune opportunité.", Modifier.padding(top = 10.dp), color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                    Text("Messages, directs, services, ventes et projets réunis dans votre WHAPPY.", Modifier.padding(top = 10.dp), color = Color(0xFFBECED5), lineHeight = 20.sp)
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onTab(WhappyTab.LIVE) }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.LiveTv, null); Text("Live", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = onOpenWhappies, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(16.dp)) { Text("Mon WHAPPY", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable { onTab(WhappyTab.LIVE) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC)), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = Color.White, modifier = Modifier.size(29.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text("EN DIRECT MAINTENANT", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("WHAPPY Live", color = WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.Black); Text("Créateurs, ventes et communautés", color = WhappyMuted, fontSize = 11.sp) }
                    Text("VOIR ›", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWhappies), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(29.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text("MON WHAPPY · DOUBLE NUMÉRIQUE", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Votre clone créatif", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Image, voix, mouvements et missions", color = WhappyMuted, fontSize = 11.sp) }
                    Text("OUVRIR ›", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item { Text("Espaces essentiels", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
        item {
            BoxWithConstraints {
                val cell = (maxWidth - 12.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaceCard("Messages", "Temps réel", Icons.Rounded.ChatBubble, cell) { onTab(WhappyTab.MESSAGES) }
                        SpaceCard("Marketplace", "Acheter et vendre", Icons.Rounded.Storefront, cell) { onTab(WhappyTab.MARKET) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Live", "Voir les directs", Icons.Rounded.LiveTv, cell) { onTab(WhappyTab.LIVE) }; SpaceCard("Business", "Deals et paiements", Icons.Rounded.BusinessCenter, cell) { onTab(WhappyTab.BUSINESS) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Jeux", "Défis et duels", Icons.Rounded.Bolt, cell) { onTab(WhappyTab.GAMES) }; SpaceCard("Services", "Payer et demander", Icons.Rounded.Payments, cell) { onTab(WhappyTab.SERVICES) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SpaceCard("Profil", "Compte et sécurité", Icons.Rounded.Person, cell) { onTab(WhappyTab.PROFILE) }; SpaceCard("Mon WHAPPY", "Votre double créatif", Icons.Rounded.AutoAwesome, cell) { onOpenWhappies() } }
                }
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Moments", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Black, color = WhappyDark); Button(onClick = { composing = true }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Add, null); Text("Publier", Modifier.padding(start = 5.dp)) } } }
        items(personalMoments, key = { it }) { raw ->
            val parts = raw.split("|", limit = 3)
            MomentCard("Vous", "MON MOMENT", parts.getOrElse(1) { "Nouveau moment" }, parts.getOrElse(2) { "" })
        }
        item { MomentCard("Mokabi Studio", "PUBLICATION SPONSORISÉE", "Porter son histoire. Vivre son style.", "Découvrez la nouvelle collection N’Tela et commandez directement dans WHAPPY.") }
        item { MomentCard("Amina M.", "RECHERCHE ACTIVE", "Je cherche une table artisanale locale", "Budget raisonnable ou échange possible · Poto-Poto") }
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
private fun GamesScreen(onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("Défi du jour") }
    var score by rememberSaveable { mutableStateOf(0) }
    var streak by rememberSaveable { mutableStateOf(1) }
    val games = listOf(
        Triple("Défi du jour", "Quiz rapide · 60 secondes", "⚡"),
        Triple("Duel WHAPPY", "Affrontez un ami en direct", "♟"),
        Triple("Mots & idées", "Trouvez la solution ensemble", "✦"),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Bolt, null, tint = WhappyBlue); Text("  WHAPPY PLAY", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                    Text("Jouez. Progressez.\nRestez connecté.", Modifier.padding(top = 10.dp), color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                    Text("Des mini-jeux simples à lancer seul ou avec votre communauté.", Modifier.padding(top = 8.dp), color = Color(0xFFBECED5), lineHeight = 19.sp)
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Série", "$streak jour${if (streak > 1) "s" else ""}")
                        StatPill("Score", "$score XP")
                    }
                }
            }
        }
        item { Text("Choisir un jeu", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black) }
        items(games, key = { it.first }) { game ->
            Card(Modifier.fillMaxWidth().clickable { selected = game.first }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (selected == game.first) Color(0xFFEAF7FC) else Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (selected == game.first) WhappyBlue else Color(0xFFE4F2F7)), contentAlignment = Alignment.Center) { Text(game.third, fontSize = 22.sp, color = if (selected == game.first) Color.White else WhappyBlue) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(game.first, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp); Text(game.second, color = WhappyMuted, fontSize = 11.sp) }
                    Text(if (selected == game.first) "PRÊT" else "JOUER ›", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(selected, color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("Votre partie est prête", color = WhappyDark, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("Lancez une manche locale maintenant. Les duels en temps réel seront synchronisés avec vos contacts WHAPPY.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 17.sp)
                    Button(onClick = { score += 25; streak += 1 }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text("  Lancer une manche", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Retour à l’accueil") } }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(label.uppercase(), color = Color(0xFF9BC4D1), fontSize = 8.sp, fontWeight = FontWeight.Black); Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ServicesScreen(
    userName: String,
    phone: String,
    onOpenMarket: () -> Unit,
    onOpenBusiness: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whappy_native_services", Context.MODE_PRIVATE) }
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
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                Column(Modifier.padding(22.dp)) {
                    Text("WHAPPY WALLET", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(if (walletActive) formatMoney(balance.toLong()) else "Portefeuille non activé", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Mode démonstration sécurisé · aucun débit bancaire réel", Modifier.padding(top = 5.dp), color = Color(0xFFBECED5), fontSize = 11.sp)
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
            item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC)), shape = RoundedCornerShape(16.dp)) { Text(message, Modifier.padding(14.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } }
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
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF12824B))
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
                    Icon(Icons.Rounded.Payments, null, tint = if (value >= 0) Color(0xFF12824B) else WhappyBlue)
                    Text(parts.getOrElse(1) { "Transaction" }, Modifier.weight(1f).padding(horizontal = 11.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold)
                    Text((if (value > 0) "+" else "") + formatMoney(value), color = if (value >= 0) Color(0xFF12824B) else WhappyDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }

    if (dialog == "pay") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Envoyer un paiement test", fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(recipient, { recipient = it }, label = { Text("Bénéficiaire") }, singleLine = true); OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Montant FCFA") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); feedback?.let { Text(it, color = Color(0xFFC53D4B), fontSize = 12.sp) } } },
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
            title = { Text("Recevoir sur WHAPPY", fontWeight = FontWeight.Black) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(receiveQr.asImageBitmap(), "QR de paiement de $userName", Modifier.size(210.dp).clip(RoundedCornerShape(18.dp))); Text(userName, Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold); Text(phone, color = WhappyMuted); Text("Ce code ouvre une demande de paiement WHAPPY.", Modifier.padding(top = 8.dp), color = WhappyMuted, fontSize = 11.sp) } },
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
    var script by remember { mutableStateOf("Bonjour, bienvenue sur WHAPPY. Aujourd’hui je vous présente une opportunité pensée pour vous.") }
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
        val file = File(context.cacheDir, "whappy-$kind-${System.currentTimeMillis()}.mp4")
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
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SmartToy, null, tint = WhappyBlue) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("MON WHAPPY", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Double numérique · sous votre contrôle", color = WhappyMuted, fontSize = 11.sp)
            }
            if (state.twinBusy) CircularProgressIndicator(Modifier.size(22.dp), color = WhappyBlue, strokeWidth = 2.dp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("WHAPPY DOUBLE ENGINE", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("Votre présence,\nmultipliée.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                        Text("Préparez votre image, votre voix et vos missions. Vous gardez le dernier mot sur chaque production.", Modifier.padding(top = 9.dp), color = Color(0xFFB8CAD1), lineHeight = 19.sp)
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text("$readiness%", color = Color.White, fontWeight = FontWeight.Black) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(if (readiness == 100) "PRÊT À PRODUIRE" else "EN APPRENTISSAGE", color = Color.White, fontWeight = FontWeight.Black)
                                Text("Identité · voix · mouvements · missions", color = Color(0xFF9BB5BF), fontSize = 10.sp)
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
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (section == item) Color(0xFFE1F3FB) else Color.White, contentColor = if (section == item) WhappyBlue else WhappyMuted),
                        ) { Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            when (section) {
                WhappyStudioSection.MOTION -> {
                    item { StudioTitle("MOTION CORE 2.0", "Donnez-lui votre langage corporel", "Composez une séquence de gestes pour les ventes, directs et réponses vidéo.") }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2330))) {
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
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (gesture.first in selectedGestures) Color(0xFFE1F3FB) else Color.White),
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
                                    Text("J’autorise mon image, ma voix et mes mouvements pour mon WHAPPY.", Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp)
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
                                Box(Modifier.align(Alignment.CenterHorizontally).size(100.dp).clip(CircleShape).background(if (recordingVoice) Color(0xFFFFE3E7) else Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) {
                                    Icon(if (recordingVoice) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = if (recordingVoice) Color(0xFFD72C46) else WhappyBlue, modifier = Modifier.size(42.dp))
                                }
                                Text("« Bonjour, je suis $userName. Cette voix est la mienne et je contrôle son utilisation par mon Double WHAPPY. »", Modifier.padding(top = 18.dp), color = WhappyInk, lineHeight = 21.sp)
                                Button(
                                    enabled = profile.identityConsent && !state.twinBusy,
                                    onClick = {
                                        if (recordingVoice) stopVoice(true)
                                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
                                ) { Text(if (recordingVoice) "Terminer et sécuriser" else if (profile.voiceUrl.isNotBlank()) "Réenregistrer ma voix" else "Enregistrer mon empreinte vocale") }
                                if (recordingVoice) Text("Enregistrement en cours depuis ${recordingSeconds}s", Modifier.padding(top = 8.dp), color = Color(0xFFD72C46), fontSize = 11.sp)
                                if (profile.voiceUrl.isNotBlank()) Text("✓ Échantillon vocal sécurisé", Modifier.padding(top = 8.dp), color = WhappyBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                WhappyStudioSection.MISSIONS -> {
                    item { StudioTitle("03 / ORCHESTRATEUR", "Choisissez une mission pour votre WHAPPY", "Chaque action produit un brouillon ou demande votre validation avant publication.") }
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
                    if (automations.isNotEmpty()) item { Text("Missions du WHAPPY", fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
                    items(automations, key = { it.id }) { automation ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(automation.name, fontWeight = FontWeight.Bold); Text("${automation.trigger} → ${automation.channel}", color = WhappyMuted, fontSize = 10.sp) }
                                Switch(checked = automation.enabled, onCheckedChange = { enabled -> if (preview) localAutomations = localAutomations.map { if (it.id == automation.id) it.copy(enabled = enabled) else it } else onToggleAutomation(automation.id, enabled) })
                                IconButton(onClick = { if (preview) localAutomations = localAutomations.filterNot { it.id == automation.id } else onDeleteAutomation(automation.id) }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = Color(0xFFD72C46)) }
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
                                        OutlinedButton(onClick = { language = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (language == option.first) Color(0xFFE1F3FB) else Color.Transparent)) { Text(option.second, fontSize = 10.sp) }
                                    }
                                }
                                OutlinedButton(onClick = {
                                    tts.language = when (language) { "ln-CD" -> Locale("ln", "CD"); "en-US" -> Locale.US; else -> Locale.FRANCE }
                                    tts.speak(script, TextToSpeech.QUEUE_FLUSH, null, "whappy-preview")
                                }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("▶ Aperçu avec voix système") }
                                Button(
                                    enabled = profile.identityConsent && selectedGestures.isNotEmpty() && script.isNotBlank() && !state.twinBusy,
                                    onClick = {
                                        if (preview) localRenders = listOf(WhappyTwinRender("local-${System.currentTimeMillis()}", "Séquence commerciale WHAPPY", script, language, "prepared")) + localRenders
                                        else onCreateRender("Séquence commerciale WHAPPY", script, language, selectedGestures)
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
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Icon(if (complete) Icons.Rounded.Verified else icon, null, tint = WhappyBlue) }
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
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Text(initials(author), color = WhappyDark, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(author, fontWeight = FontWeight.Bold); Icon(Icons.Rounded.Verified, null, tint = WhappyBlue, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }
                    Text(badge, color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(title, Modifier.padding(top = 18.dp), color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(body, Modifier.padding(top = 8.dp), color = WhappyMuted, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun CallsScreen(conversations: List<WhappyConversation>, onOpenConversation: (WhappyConversation) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whappy_consumer", Context.MODE_PRIVATE) }
    var recentCalls by remember { mutableStateOf(prefs.getStringSet("recent_calls", emptySet()).orEmpty().toList().sortedDescending()) }

    fun startCall(name: String, phone: String) {
        val entry = "${System.currentTimeMillis()}|${name.replace("|", " ")}|${phone.replace("|", " ")}"
        recentCalls = (listOf(entry) + recentCalls).take(30)
        prefs.edit().putStringSet("recent_calls", recentCalls.toSet()).apply()
        uriHandler.openUri("tel:$phone")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                Column(Modifier.padding(23.dp)) {
                    Text("WHAPPY CALLS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("Appelez vos contacts\nen un seul geste.", Modifier.padding(top = 8.dp), color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                    Text("Vos contacts vérifiés et vos conversations restent réunis dans Whappy.", Modifier.padding(top = 8.dp), color = Color(0xFFBECED5), fontSize = 12.sp, lineHeight = 18.sp)
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        CallMetric("Contacts", conversations.size.toString(), Modifier.weight(1f))
                        CallMetric("Disponibilité", "En ligne", Modifier.weight(1f))
                    }
                }
            }
        }
        item { Text("Appels récents", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp)) }
        if (recentCalls.isEmpty()) item { Text("Aucun appel lancé depuis WHAPPY pour le moment.", color = WhappyMuted, modifier = Modifier.padding(vertical = 6.dp)) }
        items(recentCalls.take(8), key = { it }) { raw ->
            val parts = raw.split("|", limit = 3)
            val timestamp = parts.firstOrNull()?.toLongOrNull() ?: 0L
            val name = parts.getOrElse(1) { "Contact" }
            val phone = parts.getOrElse(2) { "" }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Phone, null, tint = Color(0xFF12824B)); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(name, color = WhappyDark, fontWeight = FontWeight.Bold); Text("Sortant · ${formatShortDate(timestamp)} à ${formatTime(timestamp)}", color = WhappyMuted, fontSize = 10.sp) }; FilledIconButton(enabled = phone.isNotBlank(), onClick = { startCall(name, phone) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.Rounded.Phone, "Rappeler $name", tint = Color.White) } }
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
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Text(initials(conversation.peer.displayName), color = WhappyBlue, fontWeight = FontWeight.Black) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(conversation.peer.displayName, color = WhappyDark, fontWeight = FontWeight.Black)
                            Text(if (callable) "☎ ${conversation.peer.phoneNumber}" else "Numéro protégé", color = WhappyMuted, fontSize = 11.sp)
                            Text(formatTime(conversation.updatedAt), color = WhappyMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { onOpenConversation(conversation) }) { Icon(Icons.Rounded.ChatBubble, "Écrire à ${conversation.peer.displayName}", tint = WhappyBlue) }
                        FilledIconButton(enabled = callable, onClick = { startCall(conversation.peer.displayName, conversation.peer.phoneNumber) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { Icon(Icons.Rounded.Phone, "Appeler ${conversation.peer.displayName}", tint = Color.White) }
                    }
                }
            }
        }
        item { Text("Les appels téléphoniques utilisent le réseau de votre opérateur. Les appels Whappy audio et vidéo sécurisés seront regroupés ici.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) }
    }
}

@Composable
private fun CallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = .08f)).padding(12.dp)) {
        Text(label.uppercase(), color = Color(0xFF9EC9DB), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    onSubscribeChannel: (String, Boolean) -> Unit,
    onHandleWhappyLink: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var searchingBusiness by remember { mutableStateOf(false) }
    var messageSection by rememberSaveable { mutableStateOf(if (initialSection == 1) 1 else if (initialSection == 2) 2 else 0) }
    var creatingChannel by remember { mutableStateOf(false) }
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
                    scanError = "Ce QR n’est pas un code WHAPPY valide."
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
                scanError = "Aucun QR lisible trouvé dans cette image. Choisissez une capture nette du code WHAPPY."
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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(when (messageSection) { 1 -> "Contacts"; 2 -> "Chaînes"; else -> "Messages" }, fontSize = 28.sp, fontWeight = FontWeight.Black, color = WhappyDark); Text(when (messageSection) { 1 -> "Vos personnes sur WHAPPY"; 2 -> "Suivez ce qui compte pour vous"; else -> "Vos conversations instantanées" }, color = WhappyMuted) }
            TextButton(onClick = { if (messageSection == 2) creatingChannel = true else { phone = ""; resetContactSearch(); adding = true } }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.textButtonColors(contentColor = WhappyBlue)) {
                Icon(Icons.Rounded.Add, if (messageSection == 2) "Créer une chaîne" else "Ajouter un contact", modifier = Modifier.size(18.dp))
                Text(if (messageSection == 2) " Créer" else " Ajouter", fontWeight = FontWeight.Black)
            }
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE7F1F5)).padding(4.dp)) {
            listOf("Discussions", "Contacts", "Chaînes").forEachIndexed { index, label ->
                TextButton(onClick = { messageSection = index; conversationSearch = "" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(containerColor = if (messageSection == index) Color.White else Color.Transparent, contentColor = if (messageSection == index) WhappyDark else WhappyMuted)) { Text(label, fontSize = 11.sp, fontWeight = if (messageSection == index) FontWeight.Black else FontWeight.Medium) }
            }
        }
        OutlinedTextField(conversationSearch, { conversationSearch = it.take(120) }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), placeholder = { Text(when (messageSection) { 1 -> "Rechercher un contact"; 2 -> "Rechercher une chaîne ou une catégorie"; else -> "Rechercher une discussion" }) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        if (messageSection == 2) {
            ChannelDirectory(channels, currentUserId, conversationSearch, onOpenChannel, onSubscribeChannel)
        } else if (messageSection == 1) {
            val conversationContacts = conversations.map { WhappyContact(it.peer, it.updatedAt) }
            val visibleContacts = (contacts + conversationContacts).distinctBy { it.member.uid }.filter { SearchNormalizer.matches(conversationSearch, it.member.displayName, it.member.phoneNumber) }.sortedBy { SearchNormalizer.normalize(it.member.displayName) }
            if (loading && visibleContacts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
            else if (visibleContacts.isEmpty()) {
                Card(Modifier.padding(18.dp).fillMaxWidth().clickable { phone = ""; resetContactSearch(); adding = true }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = WhappyBlue) }
                        Text("Ajoutez votre premier contact", color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Avec son numéro ou son code QR WHAPPY. Vous le retrouverez ici à tout moment.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                        Text("AJOUTER UN CONTACT", color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item { Text("CONTACTS WHAPPY", Modifier.padding(start = 5.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                items(visibleContacts, key = { it.member.uid }) { contact ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !contactBusy) { onOpenContact(contact) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text(initials(contact.member.displayName), color = Color.White, fontWeight = FontWeight.Black) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(contact.member.displayName, fontWeight = FontWeight.Black, color = WhappyDark)
                                Text(contact.member.phoneNumber.ifBlank { "Contact WHAPPY" }, color = WhappyMuted, fontSize = 11.sp)
                            }
                            Text("Message", color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (loading && conversations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else if (filteredConversations.isEmpty()) EmptyState(if (conversationSearch.isBlank()) "Aucune conversation" else "Aucun résultat", if (conversationSearch.isBlank()) "Ouvrez l’onglet Contacts pour ajouter une personne sur WHAPPY." else "Essayez un autre nom ou un mot du dernier message.")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            items(filteredConversations, key = { it.id }) { conversation ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(conversation) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(if (conversation.unread) WhappyBlue else Color(0xFFE5EDF1)), contentAlignment = Alignment.Center) { Text(initials(conversation.peer.displayName), color = if (conversation.unread) Color.White else WhappyDark, fontWeight = FontWeight.Black) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row { Text(conversation.peer.displayName, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = WhappyDark); Text(formatTime(conversation.updatedAt), color = WhappyMuted, fontSize = 11.sp) }
                        Text(conversation.lastMessage, Modifier.padding(top = 4.dp), color = WhappyMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (conversation.unread) Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(WhappyBlue))
                }
                HorizontalDivider(color = WhappyLine, modifier = Modifier.padding(start = 76.dp))
            }
        }
        if (adding) AlertDialog(
            onDismissRequest = { if (!contactBusy) { adding = false; resetContactSearch() } },
            title = { Text(if (contactSearchResult == null) "Ajouter sur WHAPPY" else "Compte WHAPPY trouvé") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3FAFD)),
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
                    Text(if (contactSearchResult == null) "Saisissez le numéro complet ou scannez le code personnel de votre contact. Si le compte existe, WHAPPY ouvre directement la discussion." else "Compte vérifié : la discussion s’ouvre automatiquement.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
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
                    if (contactBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(18.dp), color = WhappyBlue, strokeWidth = 2.dp); Text(if (contactSearchResult == null) "Recherche du compte WHAPPY…" else "Ajout du contact…", color = WhappyMuted, fontSize = 12.sp) }
                    if (contactSearchResult != null) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC))) {
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
                                        tint = if (isFounderContact) Color(0xFF8F979E) else WhappyBlue,
                                    )
                                }
                                Text(contactSearchResult.phoneNumber.ifBlank { contactSearchPhone }, color = WhappyMuted, fontSize = 11.sp)
                                if (isFounderContact) {
                                    Text(WhappyIdentity.founderBadgeLabel, color = Color(0xFF8F979E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Compte WHAPPY vérifié", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (contactSearchMessage != null) Text(
                        contactSearchMessage,
                        color = if (contactSearchMessage.startsWith("Contact ajouté") || contactSearchMessage.startsWith("Ce contact existe déjà"))
                            Color(0xFF0F8A54) else if (contactSearchResult == null) WhappyMuted else WhappyBlue,
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
            title = { Text("Scanner WHAPPY") },
            text = { Text(scanError.orEmpty()) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text("Compris") } },
        )
        if (creatingChannel) AlertDialog(
            onDismissRequest = { if (!channelBusy) creatingChannel = false },
            title = { Text("Créer une chaîne", fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Une chaîne est un espace de diffusion public. Vous seul pourrez publier ; vos abonnés pourront réagir.", color = WhappyMuted, fontSize = 12.sp, lineHeight = 17.sp)
                OutlinedTextField(channelName, { channelName = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Nom de la chaîne") }, singleLine = true)
                OutlinedTextField(channelDescription, { channelDescription = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, supportingText = { Text("${channelDescription.length}/300") })
                Text("CATÉGORIE", color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Communauté", "Actualités", "Créateurs", "Shopping", "Sport", "Tech").forEach { category -> TextButton(onClick = { channelCategory = category }, colors = ButtonDefaults.textButtonColors(containerColor = if (channelCategory == category) WhappyBlue else Color(0xFFE8F0F3), contentColor = if (channelCategory == category) Color.White else WhappyDark)) { Text(category, fontSize = 11.sp) } } }
            } },
            confirmButton = { Button(enabled = !channelBusy && channelName.trim().length >= 3 && channelDescription.trim().length >= 10, onClick = { onCreateChannel(channelName, channelDescription, channelCategory); creatingChannel = false; channelName = ""; channelDescription = "" }) { if (channelBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp) else Text("Créer") } },
            dismissButton = { TextButton(enabled = !channelBusy, onClick = { creatingChannel = false }) { Text("Annuler") } },
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
    val visible = channels.filter { SearchNormalizer.matches(search, it.name, it.description, it.category, it.ownerName) }
        .sortedWith(compareByDescending<WhappyChannel> { currentUserId in it.memberIds }.thenByDescending { it.memberCount })
    if (visible.isEmpty()) {
        EmptyState(if (search.isBlank()) "Aucune chaîne" else "Aucune chaîne trouvée", if (search.isBlank()) "Créez la première chaîne WHAPPY et commencez à publier." else "Essayez un nom, une catégorie ou un autre mot-clé.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WhappyDark).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = Color.White) }
                Column(Modifier.padding(start = 12.dp)) { Text("CHAÎNES WHAPPY", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Des publications utiles, sans bruit", color = Color.White, fontWeight = FontWeight.Black); Text("${channels.size} chaîne${if (channels.size > 1) "s" else ""} à découvrir", color = Color.White.copy(alpha = .68f), fontSize = 11.sp) }
            }
        }
        items(visible, key = { "channel-${it.id}" }) { channel ->
            val subscribed = currentUserId in channel.memberIds
            val owner = channel.ownerId == currentUserId
            Card(Modifier.fillMaxWidth().clickable { onOpen(channel) }, shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(if (subscribed) WhappyBlue else Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = if (subscribed) Color.White else WhappyBlue) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(channel.name, color = WhappyDark, fontWeight = FontWeight.Black, fontSize = 16.sp); if (channel.verified) Icon(Icons.Rounded.Verified, "Chaîne vérifiée", tint = WhappyBlue, modifier = Modifier.padding(start = 4.dp).size(16.dp)) }
                            Text("${channel.category} · ${formatCompactCount(channel.memberCount)} abonnés", color = WhappyMuted, fontSize = 11.sp)
                        }
                        if (owner) Text("PROPRIÉTAIRE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        else OutlinedButton(onClick = { onSubscribe(channel.id, !subscribed) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text(if (subscribed) "Suivie" else "Suivre", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text(channel.description, color = WhappyInk, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (channel.lastPost.isNotBlank()) Text(channel.lastPost, Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFF2F7F9)).padding(9.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    LaunchedEffect(posts.size) { if (posts.isNotEmpty() && search.isBlank()) listState.animateScrollToItem(visible.lastIndex.coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize().background(WhappyBackground).imePadding()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Notifications, null, tint = Color.White) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(channel.name, fontWeight = FontWeight.Black, color = WhappyDark); if (channel.verified) Icon(Icons.Rounded.Verified, null, tint = WhappyBlue, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }; Text("${formatCompactCount(channel.memberCount)} abonnés · ${channel.postCount} publications", color = WhappyMuted, fontSize = 10.sp) }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Icon(Icons.Rounded.Search, "Rechercher", tint = if (searchOpen) WhappyBlue else WhappyDark) }
            IconButton(onClick = { showingChannelCode = true }) { Icon(Icons.Rounded.Share, "Partager la chaîne", tint = WhappyDark) }
        }
        if (searchOpen) OutlinedTextField(search, { search = it.take(120) }, Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), placeholder = { Text("Rechercher dans la chaîne") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Column(Modifier.fillMaxWidth().background(WhappyDark).padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(channel.description, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) { Text("Par ${channel.ownerName} · ${channel.category}", Modifier.weight(1f), color = Color.White.copy(alpha = .64f), fontSize = 10.sp); if (!owner) Button(onClick = { onSubscribe(!subscribed) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text(if (subscribed) "Abonné ✓" else "S’abonner", fontSize = 11.sp) } }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyState(if (search.isBlank()) "Aucune publication" else "Aucun résultat", if (owner && search.isBlank()) "Publiez la première actualité de votre chaîne." else "Les prochaines publications apparaîtront ici.") }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { "post-${it.id}" }) { post ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !post.deleted) { selectedPost = post }, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = if (post.pinned) Color(0xFFEAF7FC) else Color.White), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { if (post.pinned) Text("📌 ÉPINGLÉ", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(formatShortDate(post.createdAt) + " · " + formatTime(post.createdAt), color = WhappyMuted, fontSize = 9.sp) }
                        Text(if (post.deleted) "Publication supprimée" else post.text, color = if (post.deleted) WhappyMuted else WhappyInk, lineHeight = 21.sp, fontSize = 14.sp)
                        if (post.reactions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { post.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) -> Text("$emoji ${if (count > 1) count else ""}", Modifier.clip(CircleShape).background(Color(0xFFF0F5F7)).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp) } }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(post.authorName, Modifier.weight(1f), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(if (subscribed || owner) "Appuyez pour réagir" else "Abonnez-vous pour réagir", color = WhappyMuted, fontSize = 9.sp) }
                    }
                }
            }
        }
        if (owner) Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(text, { text = it.take(4_000) }, Modifier.weight(1f), placeholder = { Text("Nouvelle publication…") }, maxLines = 6, supportingText = { Text("${text.length}/4000") }, shape = RoundedCornerShape(18.dp))
            FilledIconButton(enabled = text.isNotBlank() && !sending, onClick = { onPublish(text); text = "" }, modifier = Modifier.padding(start = 8.dp).size(50.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) { if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Rounded.Send, "Publier", tint = Color.White) }
        } else if (!subscribed) Button(onClick = { onSubscribe(true) }, Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Notifications, null); Text("  S’abonner à cette chaîne", fontWeight = FontWeight.Bold) }
    }
    selectedPost?.let { post ->
        AlertDialog(onDismissRequest = { selectedPost = null }, title = { Text("Publication", fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(post.text, color = WhappyMuted, maxLines = 4)
            if (subscribed || owner) { Text("Réagir", fontWeight = FontWeight.Bold); Row { listOf("❤️", "👍", "🔥", "👏", "💡").forEach { emoji -> TextButton(onClick = { onReact(post, emoji); selectedPost = null }, contentPadding = PaddingValues(7.dp)) { Text(emoji, fontSize = 20.sp) } } } }
            OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Publication WHAPPY", post.text)); selectedPost = null }, Modifier.fillMaxWidth()) { Text("Copier la publication") }
            if (owner) { OutlinedButton(onClick = { onPin(post); selectedPost = null }, Modifier.fillMaxWidth()) { Text(if (post.pinned) "Désépingler" else "Épingler en haut") }; OutlinedButton(onClick = { onDelete(post); selectedPost = null }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD72C46))) { Text("Supprimer") } }
        } }, confirmButton = { TextButton(onClick = { selectedPost = null }) { Text("Fermer") } })
    }
    if (showingChannelCode) {
        val qr = remember(channelLink) { createWhappyPayloadQr("whappy://channel/${channel.id}") }
        AlertDialog(onDismissRequest = { showingChannelCode = false }, title = { Text("Partager la chaîne", fontWeight = FontWeight.Black) }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(qr.asImageBitmap(), "QR de la chaîne ${channel.name}", Modifier.size(210.dp).clip(RoundedCornerShape(18.dp)))
            Text(channel.name, fontWeight = FontWeight.Black, color = WhappyDark)
            Text(channelLink, color = WhappyMuted, fontSize = 10.sp)
            OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Lien WHAPPY", channelLink)) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.QrCode, null); Text("  Copier le lien") }
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
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue, modifier = Modifier.size(20.dp)) }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(page.name, fontWeight = FontWeight.Black, color = WhappyDark)
                                    Text(listOf(page.category, page.city).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Business WHAPPY" }, color = WhappyMuted, fontSize = 11.sp)
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
) {
    val context = LocalContext.current
    val draftPrefs = remember { context.getSharedPreferences("whappy_chat_drafts", Context.MODE_PRIVATE) }
    var text by remember(conversation.id) { mutableStateOf(draftPrefs.getString(conversation.id, "").orEmpty()) }
    var showEmoji by remember(conversation.id) { mutableStateOf(false) }
    var recording by remember(conversation.id) { mutableStateOf(false) }
    var recordStartedAt by remember(conversation.id) { mutableStateOf(0L) }
    var recorder by remember(conversation.id) { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember(conversation.id) { mutableStateOf<File?>(null) }
    var searchOpen by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(conversation.id) { mutableStateOf("") }
    var replyTo by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    var selectedMessage by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    var editingMessage by remember(conversation.id) { mutableStateOf<WhappyMessage?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    val visibleMessages = remember(messages, searchQuery) { messages.filter { SearchNormalizer.matches(searchQuery, it.text, it.mediaName, it.replyText) } }
    var previewImage by remember(conversation.id) { mutableStateOf<String?>(null) }

    fun submitText() {
        val value = text.trim()
        if (value.isBlank()) return
        editingMessage?.let { onEdit(it, value) } ?: onSend(value, replyTo)
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
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            onSendMedia(uri, "image", type, displayName(context, uri), 0)
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
    LaunchedEffect(visibleMessages.size) { if (visibleMessages.isNotEmpty() && searchQuery.isBlank()) listState.animateScrollToItem(visibleMessages.lastIndex) }

    Column(Modifier.fillMaxSize().background(WhappyBackground).imePadding()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(Modifier.size(42.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) { Text(initials(conversation.peer.displayName), color = Color.White, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(conversation.peer.displayName, fontWeight = FontWeight.Bold, color = WhappyDark); Text(if (conversation.peerTyping) "écrit…" else "WHAPPY · en ligne", color = WhappyBlue, fontSize = 11.sp, fontWeight = if (conversation.peerTyping) FontWeight.Bold else FontWeight.Normal) }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) { Icon(Icons.Rounded.Search, "Rechercher dans la discussion", tint = if (searchOpen) WhappyBlue else WhappyDark) }
            if (conversation.peer.phoneNumber.isNotBlank()) IconButton(onClick = { uriHandler.openUri("tel:${conversation.peer.phoneNumber}") }) { Icon(Icons.Rounded.Phone, "Appeler ${conversation.peer.displayName}") }
        }
        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it.take(120) }, Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp), placeholder = { Text("Rechercher un message") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WhappyBlue) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visibleMessages.isEmpty() && searchQuery.isNotBlank()) item { Text("Aucun message ne correspond à « $searchQuery ».", Modifier.padding(24.dp), color = WhappyMuted) }
            itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, message ->
                val mine = message.senderId == currentUserId
                Column(Modifier.fillMaxWidth()) {
                    if (index == 0 || !isSameDay(message.createdAt, visibleMessages[index - 1].createdAt)) {
                        Text(formatMessageDay(message.createdAt), Modifier.align(Alignment.CenterHorizontally).padding(vertical = 7.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F0F3)).padding(horizontal = 10.dp, vertical = 4.dp), color = WhappyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (mine) WhappyBlue else Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = if (mine) 0.dp else 1.dp, modifier = Modifier.fillMaxWidth(0.78f).clickable(enabled = !message.deleted) { selectedMessage = message }) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                if (message.replyText.isNotBlank()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (mine) Color.White.copy(alpha = .16f) else Color(0xFFEAF7FC)).padding(8.dp)) { Text("↩ ${message.replyText}", color = if (mine) Color.White.copy(alpha = .9f) else WhappyMuted, fontSize = 10.sp, maxLines = 2) }
                                if (message.replyText.isNotBlank()) {
                                    val targetIndex = visibleMessages.indexOfFirst { it.id == message.replyToId }
                                    if (targetIndex >= 0) {
                                        TextButton(
                                            onClick = {
                                                chatScope.launch { listState.animateScrollToItem(targetIndex) }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        ) {
                                            Text("Voir le message d’origine", color = if (mine) Color.White else WhappyBlue, fontSize = 9.sp)
                                        }
                                    }
                                }
                                val actions = remember(message.text) { detectMessageActions(message.text) }
                                when (message.kind) {
                                    "audio" -> MediaMessageRow(Icons.Rounded.AudioFile, "Note vocale · ${message.durationSeconds}s", mine) { runCatching { uriHandler.openUri(message.mediaUrl) } }
                                    "image" -> MediaMessageRow(Icons.Rounded.Photo, message.mediaName.ifBlank { "Photo" }, mine) { previewImage = message.mediaUrl }
                                    "deleted" -> Text("Message supprimé", color = if (mine) Color.White.copy(alpha = .7f) else WhappyMuted)
                                    else -> MessageLinkText(message.text, mine, actions = actions, onAction = { openMessageAction(uriHandler, it) })
                                }
                                if (actions.isNotEmpty()) {
                                    FlowRow(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        actions.take(2).forEach { action ->
                                            OutlinedButton(
                                                onClick = { openMessageAction(uriHandler, action) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                            ) { Text(if (action.type == MessageActionType.Phone) "📞 ${action.title}" else "🔗 ${action.title}") }
                                        }
                                    }
                                }
                                if (message.reactions.isNotEmpty()) Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { message.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) -> Text("$emoji${if (count > 1) " $count" else ""}", modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (mine) Color.White.copy(alpha = .18f) else Color(0xFFEAF7FC)).padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 11.sp) } }
                                Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { if (message.edited) Text("modifié · ", color = if (mine) Color.White.copy(alpha = .68f) else WhappyMuted, fontSize = 9.sp); Text(formatTime(message.createdAt), color = if (mine) Color.White.copy(alpha = .75f) else WhappyMuted, fontSize = 9.sp); if (mine) { val read = message.createdAt > 0L && conversation.peerReadAt >= message.createdAt; Text(if (read) " · Lu" else " · Envoyé", color = Color.White.copy(alpha = if (read) .95f else .68f), fontSize = 9.sp); Icon(Icons.Rounded.CheckCircle, null, tint = Color.White.copy(alpha = if (read) 1f else .65f), modifier = Modifier.padding(start = 3.dp).size(12.dp)) } }
                            }
                        }
                    }
                }
            }
        }
        if (showEmoji) EmojiTray { text = (text + it).take(4_000) }
        if (recording) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFF2F3)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE7354F)))
                Text("Note vocale en cours… appuyez sur Stop pour envoyer", Modifier.weight(1f).padding(start = 9.dp), color = Color(0xFF9D1E32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                TextButton(onClick = { finishRecording(false) }) { Text("Annuler") }
            }
        }
        replyTo?.let { message -> Row(Modifier.fillMaxWidth().background(Color(0xFFEAF7FC)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = WhappyBlue, modifier = Modifier.size(17.dp)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text("Répondre", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(message.text.ifBlank { message.mediaName.ifBlank { "Média" } }, color = WhappyDark, fontSize = 11.sp, maxLines = 1) }; TextButton(onClick = { replyTo = null }) { Text("Annuler") } } }
        editingMessage?.let { message -> Row(Modifier.fillMaxWidth().background(Color(0xFFFFF7E8)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.MoreVert, null, tint = Color(0xFFE9781A), modifier = Modifier.size(17.dp)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text("Modifier le message", color = Color(0xFFE9781A), fontSize = 10.sp, fontWeight = FontWeight.Black); Text(message.text, color = WhappyDark, fontSize = 11.sp, maxLines = 1) }; TextButton(onClick = { editingMessage = null; text = ""; draftPrefs.edit().remove(conversation.id).apply() }) { Text("Annuler") } } }
        if (previewImage != null) {
            ImageZoomViewer(
                imageSource = previewImage.orEmpty(),
                onDismiss = { previewImage = null },
            )
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(enabled = !sending && !recording, onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Rounded.AttachFile, "Joindre une photo", tint = WhappyMuted) }
            IconButton(enabled = !recording, onClick = { showEmoji = !showEmoji; if (showEmoji) keyboard?.hide() }) { Icon(Icons.Rounded.EmojiEmotions, "Émojis", tint = if (showEmoji) WhappyBlue else WhappyMuted) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(4_000); draftPrefs.edit().putString(conversation.id, text).apply() },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (recording) "Enregistrement…" else "Message…") },
                enabled = !recording,
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = true),
                keyboardActions = KeyboardActions(onSend = { submitText(); keyboard?.hide() }),
            )
            IconButton(
                enabled = !sending,
                onClick = {
                    if (text.isNotBlank()) submitText()
                    else if (recording) finishRecording(true)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.padding(start = 6.dp).size(52.dp).clip(CircleShape).background(if (text.isNotBlank() || recording) WhappyBlue else Color(0xFFE1F3FB)),
            ) {
                when {
                    sending -> CircularProgressIndicator(Modifier.size(20.dp), color = WhappyBlue, strokeWidth = 2.dp)
                    text.isNotBlank() -> Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer", tint = Color.White)
                    recording -> Icon(Icons.Rounded.Stop, "Arrêter et envoyer", tint = Color.White)
                    else -> Icon(Icons.Rounded.Mic, "Enregistrer une note vocale", tint = WhappyBlue)
                }
            }
        }
    }
    selectedMessage?.let { message ->
        val mine = message.senderId == currentUserId
        val messageActions = remember(message.text) { detectMessageActions(message.text) }
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Actions du message", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message.text.ifBlank { message.mediaName.ifBlank { "Média" } }, color = WhappyMuted, maxLines = 3)
                    if (messageActions.isNotEmpty()) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            messageActions.forEach { action ->
                                OutlinedButton(
                                    onClick = { openMessageAction(uriHandler, action); selectedMessage = null },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        if (action.type == MessageActionType.Phone) "Appeler ${action.title}"
                                        else "Ouvrir ${action.title}",
                                        color = WhappyDark,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("❤️", "👍", "😂", "😮", "🙏").forEach { emoji -> TextButton(onClick = { onReact(message, emoji); selectedMessage = null }, contentPadding = PaddingValues(6.dp)) { Text(emoji, fontSize = 20.sp) } } }
                    OutlinedButton(onClick = { replyTo = message; editingMessage = null; selectedMessage = null }, Modifier.fillMaxWidth()) { Text("Répondre") }
                    if (message.text.isNotBlank()) OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message WHAPPY", message.text)); selectedMessage = null }, Modifier.fillMaxWidth()) { Text("Copier le texte") }
                    if (mine && message.kind == "text") OutlinedButton(onClick = { editingMessage = message; replyTo = null; text = message.text; selectedMessage = null }, Modifier.fillMaxWidth()) { Text("Modifier") }
                    if (mine) OutlinedButton(onClick = { onDelete(message); selectedMessage = null }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD72C46))) { Text("Supprimer pour tous") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun MessageLinkText(
    text: String,
    mine: Boolean,
    actions: List<MessageAction>,
    onAction: (MessageAction) -> Unit,
) {
    if (actions.isEmpty()) {
        Text(text, color = if (mine) Color.White else WhappyInk, lineHeight = 20.sp)
        return
    }
    val annotated = buildAnnotatedString {
        append(text)
        actions.forEach { action ->
            if (action.start >= action.end || action.start < 0 || action.end > text.length) return@forEach
            addStringAnnotation("message-action", action.target, action.start, action.end)
            addStyle(
                SpanStyle(
                    color = if (mine) Color.White.copy(alpha = .92f) else WhappyBlue,
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
        style = TextStyle(color = if (mine) Color.White else WhappyInk, lineHeight = 20.sp),
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
        Icon(icon, null, tint = if (mine) Color.White else WhappyBlue, modifier = Modifier.size(28.dp))
        Text(label, Modifier.padding(start = 9.dp), color = if (mine) Color.White else WhappyInk, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun EmojiTray(onEmoji: (String) -> Unit) {
    val emojis = listOf("😀", "😂", "🥰", "😍", "🤩", "😎", "🥳", "😭", "😡", "👍", "🙏", "👏", "💪", "🤝", "❤️", "💙", "🔥", "✨", "🎉", "💯", "✅", "📍", "🎁", "🛍️", "💼", "🇨🇬")
    FlowRow(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        emojis.forEach { emoji -> TextButton(onClick = { onEmoji(emoji) }, contentPadding = PaddingValues(5.dp)) { Text(emoji, fontSize = 22.sp) } }
    }
}

@Suppress("DEPRECATION")
private fun createVoiceRecorder(context: Context): Pair<MediaRecorder, File> {
    val file = File(context.cacheDir, "whappy-voice-${System.currentTimeMillis()}.m4a")
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
    val prefs = remember { context.getSharedPreferences("whappy_consumer", Context.MODE_PRIVATE) }
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
                Box(contentAlignment = Alignment.TopEnd) { IconButton(onClick = { showingCart = true }) { Icon(Icons.Rounded.ShoppingCart, "Panier", tint = WhappyDark) }; if (cart.values.sum() > 0) Box(Modifier.size(17.dp).clip(CircleShape).background(Color(0xFFFF3B5C)), contentAlignment = Alignment.Center) { Text(cart.values.sum().coerceAtMost(9).toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black) } }
                Button(onClick = { creating = true }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)) { Icon(Icons.Rounded.Add, null); Text("Vendre", Modifier.padding(start = 4.dp)) }
            }
        }
        marketFeedback?.let { value -> item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC)), shape = RoundedCornerShape(15.dp)) { Text(value, Modifier.padding(13.dp), color = WhappyDark, fontWeight = FontWeight.SemiBold) } } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Rechercher un produit ou une boutique") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true) }
        if (products.isEmpty()) item { EmptyState("Aucune annonce", "Publiez la première offre de cette catégorie.") }
        items(products, key = { it.id }) { product ->
            Card(Modifier.clickable { selected = product }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE2F1F8)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(product.title, fontWeight = FontWeight.Bold, color = WhappyDark); Text(product.price, Modifier.padding(top = 5.dp), color = WhappyBlue, fontWeight = FontWeight.Bold); Text("${product.place} · ${product.seller}", Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp); if(product.mode=="troc") Text("TROC ACCEPTÉ", Modifier.padding(top=5.dp), color=WhappyBlue, fontSize=9.sp, fontWeight=FontWeight.Black) }
                    IconButton(onClick = { savedIds = if (product.id in savedIds) savedIds - product.id else savedIds + product.id; prefs.edit().putStringSet("market_favorites", savedIds).apply() }) { Icon(if (product.id in savedIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", tint = if (product.id in savedIds) Color(0xFFFF3B5C) else WhappyMuted) }
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
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFE2F1F8)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Storefront, null, tint = WhappyBlue, modifier = Modifier.size(62.dp)) }; Text(product.price, color = WhappyBlue, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Vendu par ${product.seller} · ${product.place}", color = WhappyMuted); Text(if (product.mode == "troc") "Cette annonce accepte les propositions d’échange." else "Ajoutez cet article au panier pour préparer votre commande.", color = WhappyDark) } },
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
                cart.forEach { (id, quantity) -> val product = allProducts.firstOrNull { it.id == id }; if (product != null) item(key = "cart-$id") { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9FA)), shape = RoundedCornerShape(15.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(product.title, fontWeight = FontWeight.Bold, color = WhappyDark, maxLines = 2); Text(product.price, color = WhappyBlue, fontSize = 11.sp) }; TextButton(onClick = { saveCart(cart + (id to quantity - 1)) }) { Text("−") }; Text(quantity.toString(), fontWeight = FontWeight.Black); TextButton(onClick = { saveCart(cart + (id to (quantity + 1).coerceAtMost(9))) }) { Text("+") } } } } }
                if (cart.isNotEmpty()) item { OutlinedTextField(delivery, { delivery = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("Adresse ou point de rendez-vous") }, minLines = 2) }
            } },
            confirmButton = { Button(enabled = cart.isNotEmpty() && delivery.trim().length >= 5, onClick = { val reference = "WH-${UUID.randomUUID().toString().take(6).uppercase()}"; val titles = cart.mapNotNull { (id, quantity) -> allProducts.firstOrNull { it.id == id }?.let { "$quantity × ${it.title.replace("|", " ")}" } }.joinToString(", "); val entry = "${System.currentTimeMillis()}|$reference|$titles · ${delivery.trim().replace("|", " ")}"; orders = (listOf(entry) + orders).take(30); prefs.edit().putStringSet("market_orders", orders.toSet()).apply(); saveCart(emptyMap()); delivery = ""; showingCart = false; marketFeedback = "Commande $reference enregistrée." }) { Text("Commander") } },
            dismissButton = { TextButton(onClick = { showingCart = false }) { Text("Fermer") } },
        )
    }
    if (showingOrders) AlertDialog(
        onDismissRequest = { showingOrders = false },
        title = { Text("Mes commandes", fontWeight = FontWeight.Black) },
        text = { LazyColumn(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (orders.isEmpty()) item { Text("Aucune commande enregistrée.", color = WhappyMuted) }; items(orders, key = { it }) { raw -> val parts = raw.split("|", limit = 3); Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9FA)), shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(13.dp)) { Row { Text(parts.getOrElse(1) { "Commande" }, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Black); Text("À confirmer", color = Color(0xFFE9781A), fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text(parts.getOrElse(2) { "" }, Modifier.padding(top = 6.dp), color = WhappyMuted, fontSize = 11.sp) } } } } },
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
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ listOf("vente" to "Vendre","troc" to "Troquer").forEach{ option -> OutlinedButton(onClick={mode=option.first},colors=ButtonDefaults.outlinedButtonColors(containerColor=if(mode==option.first) Color(0xFFE1F3FB) else Color.Transparent)){Text(option.second)} } }
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
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B5C)).padding(horizontal = 9.dp, vertical = 5.dp)) { Text("● LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                        Text("$liveNow directs maintenant", Modifier.padding(start = 10.dp), color = Color(0xFFBECED5), fontSize = 12.sp)
                    }
                    Text("Vivez, vendez et créez\nen direct.", Modifier.padding(top = 13.dp), color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                    Text("Lancez un salon pour votre communauté, vos produits ou vos événements.", Modifier.padding(top = 9.dp), color = Color(0xFFBECED5), lineHeight = 19.sp)
                    Button(onClick = { creating = true }, Modifier.fillMaxWidth().padding(top = 17.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Videocam, null); Text("Démarrer mon Live", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold) }
                    if (permissionError) Text("Autorisez la caméra et le micro pour démarrer le direct.", Modifier.padding(top = 10.dp), color = Color(0xFFFFB9C5), fontSize = 11.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenTwin), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC))) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = WhappyBlue, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("ANIMER AVEC MON WHAPPY", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Préparez votre clone, sa voix et ses mouvements", color = WhappyDark, fontWeight = FontWeight.Bold) }
                    Text("›", color = WhappyBlue, fontSize = 25.sp)
                }
            }
        }
        item { Text("En direct et programmés", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WhappyDark) }
        if (visibleLives.isEmpty()) item { EmptyState("Aucun Live en cours", "Préparez le premier direct de votre communauté.") }
        items(visibleLives, key = { it.id }) { live ->
            Card(Modifier.fillMaxWidth().clickable { selectedLive = live }, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(128.dp).background(if (live.status == "live") WhappyDark else Color(0xFFE2F1F8)), contentAlignment = Alignment.Center) {
                        Icon(if (live.status == "live") Icons.Rounded.PlayArrow else Icons.Rounded.Schedule, null, tint = if (live.status == "live") Color.White else WhappyBlue, modifier = Modifier.size(46.dp))
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(if (live.status == "live") Color(0xFFFF3B5C) else WhappyBlue).padding(horizontal = 9.dp, vertical = 5.dp)) { Text(if (live.status == "live") "● EN DIRECT" else "PROGRAMMÉ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        if (live.status == "live") Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" ${live.viewerCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text(live.title, color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${live.hostName} · ${live.category}", Modifier.weight(1f), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEAF7FC)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(liveModeLabel(live.hostMode), color = WhappyDark, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        }
                        if (live.productTitle.isNotBlank()) Text("Deal présenté : ${live.productTitle}", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp)
                        if (live.hostId == currentUserId && live.status == "scheduled") Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { startWithPermissions { pendingStudioTitle = live.title; if (preview) previewStatuses = previewStatuses + (live.id to "live") else onUpdateLiveStatus(live.id, "live") } }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Démarrer") }
                            OutlinedButton(onClick = { if (preview) previewStatuses = previewStatuses + (live.id to "ended") else onEndLive(live.id) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Annuler") }
                        }
                        else if (live.hostId == currentUserId) OutlinedButton(onClick = { if (preview) previewStatuses = previewStatuses + (live.id to "ended") else onUpdateLiveStatus(live.id, "ended") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Text("Terminer le direct") }
                        else Button(onClick = { selectedLive = live }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text(if (live.status == "live") "Rejoindre le Live" else "Voir le programme", Modifier.padding(start = 6.dp)) }
                    }
                }
            }
        }
        item { Text("Chaque profil peut créer un salon. Le statut, l’audience et les réactions sont synchronisés en temps réel; la vidéo multi-appareils utilise l’infrastructure média WHAPPY.", color = WhappyMuted, fontSize = 10.sp, lineHeight = 15.sp) }
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
    selectedLive?.let { live -> LiveRoomDialog(live, isOwner = live.hostId == currentUserId, onDismiss = { selectedLive = null }, onOpenTwin = { selectedLive = null; onOpenTwin() }) }
}

@Composable
private fun LiveRoomDialog(live: WhappyLive, isOwner: Boolean, onDismiss: () -> Unit, onOpenTwin: () -> Unit) {
    var reactionCount by remember(live.id) { mutableStateOf(live.viewerCount + 24) }
    var message by remember(live.id) { mutableStateOf("") }
    var comments by remember(live.id) { mutableStateOf(listOf("Amina : Très belle présentation 👏", "Junior : Le Deal est encore disponible ?")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (live.status == "live") Color(0xFFFF3B5C) else WhappyBlue).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(if (live.status == "live") "● LIVE" else "PROGRAMMÉ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }; Text(if (isOwner) " Studio créateur" else " ${live.hostName}", color = WhappyDark, fontWeight = FontWeight.Black) }; Text(live.title, Modifier.padding(top = 7.dp), color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Black) } },
        text = { Column {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(54.dp)); Text(if (isOwner && live.status == "live") "Studio ouvert · vous êtes en direct" else if (live.status == "live") "Salon Live ouvert" else "Direct à venir", Modifier.align(Alignment.BottomCenter).padding(15.dp), color = Color.White, fontWeight = FontWeight.Bold) }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("👀 ${live.viewerCount}", color = WhappyMuted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); TextButton(onClick = { reactionCount += 1 }) { Text("💙 $reactionCount") } }
            if (live.productTitle.isNotBlank()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC)), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue); Column(Modifier.padding(start = 9.dp)) { Text("DEAL DU LIVE", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(live.productTitle, color = WhappyDark, fontWeight = FontWeight.Bold) } } }
            LazyColumn(Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp)) { items(comments) { comment -> Text(comment, Modifier.padding(vertical = 3.dp), color = WhappyMuted, fontSize = 11.sp) } }
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(message, { message = it.take(180) }, Modifier.weight(1f), placeholder = { Text("Écrire dans le Live") }, singleLine = true, shape = RoundedCornerShape(15.dp)); IconButton(onClick = { if (message.isNotBlank()) { comments = comments + "Vous : ${message.trim()}"; message = "" } }) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer", tint = WhappyBlue) } }
        } },
        confirmButton = { TextButton(onClick = onOpenTwin) { Text("Animer avec mon WHAPPY") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun LiveDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, Boolean, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Communauté") }; var product by remember { mutableStateOf("") }; var hostMode by remember { mutableStateOf("personal") }; var visibility by remember { mutableStateOf("public") }
    val valid = title.trim().length >= 3 && category.isNotBlank() && !busy
    AlertDialog(onDismissRequest = onDismiss, title = { Column { Text("Créer un Live", fontWeight = FontWeight.Black); Text("Tout le monde peut passer en direct", color = WhappyBlue, fontSize = 11.sp) } }, text = { LazyColumn(Modifier.fillMaxWidth().height(470.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Text("Je passe en direct comme", color = WhappyDark, fontWeight = FontWeight.Black) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("personal" to "👤 Personnel", "creator" to "✦ Créateur", "business" to "▣ Business").forEach { option -> OutlinedButton(onClick = { hostMode = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (hostMode == option.first) WhappyDark else Color.White, contentColor = if (hostMode == option.first) Color.White else WhappyDark), shape = RoundedCornerShape(13.dp)) { Text(option.second, fontWeight = FontWeight.Bold) } } } }
        item { Text(when (hostMode) { "business" -> "Vendez, présentez un Deal et recevez des commandes."; "creator" -> "Animez votre communauté et utilisez votre WHAPPY."; else -> "Discutez, partagez un moment ou organisez un événement." }, color = WhappyMuted, fontSize = 11.sp) }
        item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Titre du direct") }, singleLine = true) }
        item { OutlinedTextField(category, { category = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Catégorie") }, singleLine = true) }
        if (hostMode == "business") item { OutlinedTextField(product, { product = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Produit ou Deal (facultatif)") }, singleLine = true) }
        item { Text("Audience", color = WhappyDark, fontWeight = FontWeight.Black) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("public" to "🌍 Public", "contacts" to "👥 Contacts", "private" to "🔒 Privé").forEach { option -> OutlinedButton(onClick = { visibility = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (visibility == option.first) Color(0xFFE1F3FB) else Color.White), shape = RoundedCornerShape(13.dp)) { Text(option.second) } } } }
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
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
                Column(Modifier.padding(24.dp)) {
                    Text("WHAPPY BUSINESS SUITE", color = WhappyBlue, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text("Tout votre Business,\ndans une seule app.", Modifier.padding(top = 9.dp), color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                    Text("Catalogue, commandes, Deals, publicité, paiements et performances en temps réel.", Modifier.padding(top = 9.dp), color = Color(0xFFBECED5), lineHeight = 20.sp)
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
                BusinessSection.entries.forEach { item -> OutlinedButton(onClick = { section = item }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (section == item) Color(0xFFE1F3FB) else Color.White, contentColor = if (section == item) WhappyDark else WhappyMuted), shape = RoundedCornerShape(14.dp)) { Text(item.label, fontWeight = if (section == item) FontWeight.Bold else FontWeight.Medium) } }
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
                item { BusinessFeatureCard(Icons.Rounded.AutoAwesome, "Mon WHAPPY pour Business", "Créez des contenus et préparez vos directs") { onOpenTwin() } }
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
                item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = WhappyDark), shape = RoundedCornerShape(22.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("COMMANDES CONFIRMÉES", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(paymentNotices.size.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black); Text("$soldUnits article(s) vendu(s)", color = Color(0xFFBECED5), fontSize = 11.sp) }; Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = WhappyBlue, modifier = Modifier.size(42.dp)) } } }
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
                item { BusinessFeatureCard(Icons.Rounded.AutoAwesome, "Créer avec mon WHAPPY", "Préparez une vidéo, un Live ou une campagne") { onOpenTwin() } }
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
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WhappyBlue) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Black, color = WhappyDark); Text(body, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyBlue, fontSize = 24.sp) } }
}

@Composable
private fun CatalogCard(deal: WhappyDeal, onAddVariant: () -> Unit) {
    val remaining = (deal.stock - deal.sold).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
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
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFDFF6EA)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF12824B)) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Commande de ${notice.buyerName}", color = WhappyDark, fontWeight = FontWeight.Black); Text("${notice.provider} · ${formatTime(notice.createdAt)}", color = WhappyMuted, fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.End) { Text(formatMoney(notice.amount), color = Color(0xFF12824B), fontWeight = FontWeight.Black); Text(if (notice.status == "paid") "PAYÉE" else notice.status.uppercase(), color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun InsightCard(title: String, value: Long, target: Long, body: String) {
    val progress = if (target <= 0) 0f else (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp)) {
            Row { Text(title, Modifier.weight(1f), color = WhappyDark, fontWeight = FontWeight.Black); Text("${(progress * 100).toInt()} %", color = WhappyBlue, fontWeight = FontWeight.Black) }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(CircleShape), color = WhappyBlue, trackColor = Color(0xFFE1F3FB))
            Text(body, Modifier.padding(top = 9.dp), color = WhappyMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BusinessPageCard(page: WhappyBusinessPage, onEdit: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Text(initials(page.name), color = WhappyBlue, fontWeight = FontWeight.Black) }; Column(Modifier.weight(1f).padding(start = 12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(page.name, fontWeight = FontWeight.Black, color = WhappyDark); Icon(Icons.Rounded.Verified, null, Modifier.padding(start = 5.dp).size(15.dp), tint = WhappyBlue) }; Text("@${page.handle} · ${page.category}", color = WhappyBlue, fontSize = 11.sp); Text(page.bio.ifBlank { page.city }, Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp, maxLines = 2) }; TextButton(onClick = onEdit) { Text("Modifier") } }; FlowRow(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("📍 ${page.city}", color = WhappyMuted, fontSize = 10.sp); if (page.phone.isNotBlank()) Text("☎ ${page.phone}", color = WhappyMuted, fontSize = 10.sp); if (page.website.isNotBlank()) Text("↗ ${page.website}", color = WhappyBlue, fontSize = 10.sp) } } } }

@Composable
private fun DealCard(deal: WhappyDeal, onStatus: (String) -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (deal.status == "active") Color(0xFFFFEEF1) else Color(0xFFE9EEF0)).padding(horizontal = 8.dp, vertical = 5.dp)) { Text(if (deal.status == "active") "DEAL ACTIF" else deal.status.uppercase(), color = if (deal.status == "active") Color(0xFFD81B42) else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black) }; Text(deal.pageName, Modifier.padding(start = 8.dp).weight(1f), color = WhappyMuted, fontSize = 11.sp); Text("${deal.sold}/${deal.stock} vendus", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text(deal.title, Modifier.padding(top = 10.dp), color = WhappyDark, fontSize = 18.sp, fontWeight = FontWeight.Black); Text(deal.description, Modifier.padding(top = 4.dp), color = WhappyMuted, fontSize = 11.sp); Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) { Text(formatMoney(deal.dealPrice), color = WhappyBlue, fontSize = 20.sp, fontWeight = FontWeight.Black); if (deal.originalPrice > deal.dealPrice) Text(formatMoney(deal.originalPrice), Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("Expire ${formatShortDate(deal.endsAt)}", color = WhappyMuted, fontSize = 10.sp) }; Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (deal.status == "active") OutlinedButton(onClick = { onStatus("paused") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Suspendre") } else if (deal.status == "paused") Button(onClick = { onStatus("active") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Réactiver") }; OutlinedButton(onClick = { onStatus("ended") }, enabled = deal.status != "ended", modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Terminer") } } } } }

@Composable
private fun PaymentNoticeCard(notice: WhappyPaymentNotice, locallyRead: Boolean, onRead: () -> Unit) { val unread = !notice.read && !locallyRead; Card(Modifier.fillMaxWidth().clickable(enabled = unread, onClick = onRead), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (unread) Color(0xFFEAF7FC) else Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(if (notice.status == "paid") Color(0xFFDFF6EA) else Color(0xFFFFF1D6)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Payments, null, tint = if (notice.status == "paid") Color(0xFF12824B) else Color(0xFFB56A00)) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(if (notice.status == "paid") "Paiement reçu" else notice.status.replaceFirstChar { it.uppercase() }, color = WhappyDark, fontWeight = FontWeight.Black); Text("${notice.buyerName} · ${notice.provider}", color = WhappyMuted, fontSize = 11.sp); Text(formatTime(notice.createdAt), color = WhappyMuted, fontSize = 10.sp) }; Column(horizontalAlignment = Alignment.End) { Text("+${formatMoney(notice.amount)}", color = Color(0xFF12824B), fontWeight = FontWeight.Black); if (unread) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(WhappyBlue)) } } } }

@Composable
private fun CampaignCard(campaign: WhappyCampaign) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(campaign.title, Modifier.weight(1f), fontWeight = FontWeight.Black, color = WhappyDark); Text(if (campaign.status == "active") "ACTIVE" else campaign.status.uppercase(), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black) }; Text("${campaign.pageName} · Objectif ${campaign.objective}", Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 11.sp); Text("${formatMoney(campaign.dailyBudget)}/jour · ${campaign.days} jours", Modifier.padding(top = 4.dp), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }

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
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WhappyDark), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalOffer, null, tint = WhappyBlue) }; Column(Modifier.padding(start = 11.dp)) { Text("Créer un Deal", fontWeight = FontWeight.Black, fontSize = 22.sp); Text(listOf("Identité de l’offre", "Prix et disponibilité", "Aperçu avant publication")[step], color = WhappyBlue, fontSize = 11.sp) } }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { index -> Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (index <= step) WhappyBlue else Color(0xFFE0E8EC))) } }
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
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7FC)), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text("RÉDUCTION", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(if (discount > 0) "-$discount %" else "À calculer", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(original, { original = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Prix normal") }, suffix = { Text("F") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(price, { price = it.filter(Char::isDigit).take(9) }, Modifier.weight(1f), label = { Text("Prix Deal") }, suffix = { Text("F") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(stock, { stock = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Durée") }, suffix = { Text("j") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                        Text("WHAPPY affichera automatiquement le stock restant et la date de fin pour créer un sentiment d’urgence.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    else -> Column(Modifier.fillMaxWidth().height(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Voici ce que vos clients verront", color = WhappyDark, fontWeight = FontWeight.Black)
                        DealPreviewCard(page.name, title, description, originalValue, priceValue, stockValue, daysValue, discount)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("⚡ Urgence", "✓ Stock réel", "🔔 Alertes").forEach { tag -> Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFEAF7FC)).padding(horizontal = 9.dp, vertical = 6.dp)) { Text(tag, color = WhappyDark, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFFF3B5C)).padding(horizontal = 9.dp, vertical = 5.dp)) { Text("DEAL · -$discount %", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp) }; Spacer(Modifier.weight(1f)); Text("$days JOURS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            Text(title.ifBlank { "Votre Deal exceptionnel" }, Modifier.padding(top = 14.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(description.ifBlank { "Une description claire qui donne envie d’acheter maintenant." }, Modifier.padding(top = 6.dp), color = Color(0xFFBECED5), fontSize = 11.sp, maxLines = 3)
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) { Text(formatMoney(price), color = WhappyBlue, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("  ${formatMoney(original)}", color = Color(0xFFBECED5), fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("$stock restant(s)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
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
                            OutlinedButton(onClick = { selectedPage = page }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedPage.id == page.id) Color(0xFFE1F3FB) else Color.Transparent)) { Text(page.name) }
                        }
                    }
                }
                item { Text("Objectif", color = WhappyMuted, fontWeight = FontWeight.Bold) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("reach" to "Visibilité", "messages" to "Messages", "traffic" to "Trafic", "sales" to "Ventes").forEach { option ->
                            OutlinedButton(onClick = { objective = option.first }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (objective == option.first) Color(0xFFE1F3FB) else Color.Transparent)) { Text(option.second) }
                        }
                    }
                }
                item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Titre de la campagne") }, singleLine = true) }
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
                onClick = { onSave(WhappyCampaignDraft(selectedPage.id, selectedPage.name, objective, title, creative, "Nous contacter", audience, city, dailyBudget, duration)) },
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
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("whappy_consumer", Context.MODE_PRIVATE) }
    var localPhoto by remember(photoUrl) { mutableStateOf(photoUrl) }
    var showingMyCode by remember { mutableStateOf(false) }
    var settingDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var privacy by rememberSaveable { mutableStateOf(prefs.getString("privacy", "contacts") ?: "contacts") }
    var messageNotifications by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_messages", true)) }
    var callNotifications by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_calls", true)) }
    var dataSaver by rememberSaveable { mutableStateOf(prefs.getBoolean("data_saver", false)) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        localPhoto = uri.toString()
        if (!preview) onUpdatePhoto(uri, context.contentResolver.getType(uri) ?: "image/jpeg")
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserAvatar(localPhoto, name, 92.dp, modifier = if (localPhoto.isBlank()) Modifier else Modifier.clickable { previewPhoto = localPhoto })
                        IconButton(
                            onClick = { photoPicker.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(WhappyDark),
                        ) { Icon(Icons.Rounded.Photo, "Changer la photo", tint = Color.White, modifier = Modifier.size(19.dp)) }
                    }
                    TextButton(onClick = { photoPicker.launch("image/*") }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Photo, null, Modifier.size(18.dp))
                        Text(if (busy) " Enregistrement…" else " Changer ma photo", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (founder) WhappyIdentity.founderBusinessName else name,
                        Modifier.padding(top = 14.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = WhappyDark,
                    )
                    if (founder) {
                        Text(WhappyIdentity.founderName, Modifier.padding(top = 3.dp), color = WhappyMuted, fontSize = 12.sp)
                    }
                    Text(if (preview) "Mode démonstration" else phone, Modifier.padding(top = 4.dp), color = WhappyMuted)
                    if (founder) {
                        Row(
                            Modifier
                                .padding(top = 10.dp)
                                .background(Color(0xFFF0F2F4), RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Verified, null, tint = Color(0xFF8F979E), modifier = Modifier.size(14.dp))
                            Text(WhappyIdentity.founderBadgeLabel, Modifier.padding(start = 6.dp), color = Color(0xFF8F979E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Verified, null, tint = WhappyBlue, modifier = Modifier.size(17.dp)); Text("Compte WHAPPY vérifié", Modifier.padding(start = 5.dp), color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    OutlinedButton(onClick = { showingMyCode = true }, Modifier.padding(top = 14.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Person, null, modifier = Modifier.size(18.dp))
                        Text("  Mon code WHAPPY", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWhappies), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = WhappyDark)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(WhappyBlue), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White) }; Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text("MON WHAPPY", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black); Text("Mon double numérique", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(if (twinReadiness > 0) "Profil prêt à $twinReadiness %" else "Image, voix, mouvements et missions", color = Color(0xFFBECED5), fontSize = 11.sp) }; Text("›", color = WhappyBlue, fontSize = 26.sp) } }
        }
        items(listOf("Confidentialité" to "Contrôlez qui peut vous contacter", "Notifications" to "Messages, appels et commandes", "Stockage et données" to "Médias et utilisation réseau", "Aide et sécurité" to "Assistance et appareils connectés")) { setting ->
            Card(Modifier.fillMaxWidth().clickable { settingDialog = setting.first }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Lock, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(setting.first, fontWeight = FontWeight.Bold, color = WhappyDark); Text(setting.second, color = WhappyMuted, fontSize = 11.sp) }; Text("›", color = WhappyMuted, fontSize = 23.sp) } }
        }
        if (!preview) item { OutlinedButton(onClick = onSignOut, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Se déconnecter de cet appareil") } }
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
                    "Confidentialité" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Qui peut vous contacter ?", color = WhappyMuted); listOf("contacts" to "Mes contacts", "everyone" to "Tous les utilisateurs", "nobody" to "Personne").forEach { option -> OutlinedButton(onClick = { privacy = option.first; prefs.edit().putString("privacy", privacy).apply() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (privacy == option.first) Color(0xFFE1F3FB) else Color.Transparent), shape = RoundedCornerShape(13.dp)) { Text(option.second) } } }
                    "Notifications" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Nouveaux messages", Modifier.weight(1f)); Switch(messageNotifications, { messageNotifications = it; prefs.edit().putBoolean("notify_messages", it).apply() }) }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Appels entrants", Modifier.weight(1f)); Switch(callNotifications, { callNotifications = it; prefs.edit().putBoolean("notify_calls", it).apply() }) }; Text("Les autorisations système restent contrôlées dans les réglages Android.", color = WhappyMuted, fontSize = 11.sp) }
                    "Stockage et données" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Économiseur de données", fontWeight = FontWeight.Bold); Text("Réduit le chargement automatique des médias", color = WhappyMuted, fontSize = 11.sp) }; Switch(dataSaver, { dataSaver = it; prefs.edit().putBoolean("data_saver", it).apply() }) }; Text("Les photos et notes vocales choisies restent accessibles depuis leurs conversations.", color = WhappyMuted, fontSize = 11.sp) }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("WHAPPY réunit vos conversations, appels, achats, directs et services. En cas de problème, contactez l’assistance depuis cet appareil.", color = WhappyDark); OutlinedButton(onClick = { uriHandler.openUri("mailto:support@whappy.chat?subject=Aide%20WHAPPY") }, Modifier.fillMaxWidth()) { Text("Contacter l’assistance") } }
                }
            },
            confirmButton = { TextButton(onClick = { settingDialog = null }) { Text("Terminé") } },
        )
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
        title = { Text("Mon code WHAPPY") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Présentez ce code à ${name.ifBlank { "votre contact" }} pour être ajouté instantanément.", color = WhappyMuted)
                if (qrCode != null) {
                    Image(
                        bitmap = qrCode.asImageBitmap(),
                        contentDescription = "Code QR WHAPPY de $name",
                        modifier = Modifier.padding(top = 18.dp).size(210.dp).clip(RoundedCornerShape(18.dp)),
                    )
                    Text(normalized, Modifier.padding(top = 11.dp), color = WhappyDark, fontWeight = FontWeight.Bold)
                    Text(contactLink, Modifier.padding(top = 5.dp), color = WhappyMuted, fontSize = 9.sp)
                    OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mon lien WHAPPY", contactLink)) }, Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(13.dp)) { Text("Copier mon lien") }
                    Button(onClick = { shareWhappyLink(context, "Contact WHAPPY", contactLink) }, Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.Share, null); Text("  Partager mon contact") }
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
        source.startsWith("http://") || source.startsWith("https://") -> runCatching { URL(source).openStream().use { it.readBytes() } }.getOrNull()
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun UserAvatar(photoUrl: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf(AvatarMemoryCache.items[photoUrl]) }
    LaunchedEffect(photoUrl) {
        if (photoUrl.isBlank()) return@LaunchedEffect
        AvatarMemoryCache.items[photoUrl]?.let { cached -> bitmap = cached; return@LaunchedEffect }
        val loaded = withContext(Dispatchers.IO) { runCatching { loadImageBitmap(context, photoUrl) }.getOrNull() }
        if (loaded != null) {
            AvatarMemoryCache.items[photoUrl] = loaded
            bitmap = loaded
        }
    }
    Box(modifier.size(size).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) {
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
                Box(Modifier.size(74.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFE1F3FB)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChatBubble, null, tint = WhappyBlue, modifier = Modifier.size(30.dp)) }
                Text(title, Modifier.padding(top = 7.dp), fontSize = 20.sp, fontWeight = FontWeight.Black, color = WhappyDark, textAlign = TextAlign.Center)
                Text(body, color = WhappyMuted, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Text("WHAPPY · votre espace est prêt", Modifier.padding(top = 7.dp), color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
