package com.whappy.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat

/** Isolated debug fixtures. No fake data is part of the release application. */
class CommerceChatVisualTestActivity : ComponentActivity() {
    @Volatile var editorLastSubmission: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val audioFixture = if (intent.getStringExtra("screen") == "audio") {
            val samples = 24_000 * 25
            val wav = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            wav.putInt(16).putShort(1).putShort(1).putInt(24_000).putInt(48_000).putShort(2).putShort(16)
            wav.put("data".toByteArray()).putInt(samples * 2)
            repeat(samples) { wav.putShort((kotlin.math.sin(it * 2.0 * Math.PI * 220 / 24_000) * 300).toInt().toShort()) }
            java.io.File(cacheDir, "voice-qa.wav").apply { writeBytes(wav.array()) }.absolutePath
        } else ""
        setContent {
            WhappyTheme {
                if (intent.getStringExtra("screen")?.startsWith("editor-") == true) {
                    var name by rememberSaveable { mutableStateOf("") }
                    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
                    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
                    var busy by remember { mutableStateOf(false) }
                    val page = WhappyBusinessPage("qa-shop", "Atelier Business · TEST", "atelier", "Restaurant", "Cuisine maison", "Lomé", "qa-owner")
                    fun saved(value: String) { editorLastSubmission = value; busy = true }
                    when (intent.getStringExtra("screen")) {
                        "editor-group" -> CreateGroupDialog(name,
                            (1..24).map { WhappyContact(WhappyMember("qa-$it", "Contact $it", phoneNumber = "+2420600000$it")) },
                            selected.toSet(), photo, busy, { name = it },
                            { selected = if (it in selected) selected - it else selected + it }, { photo = it }, { finish() },
                            { saved("$name|${selected.size}") })
                        "editor-offer" -> DealDialog(listOf(page), busy, { finish() }) { _, title, _, _, _, _, _ -> saved(title) }
                        "editor-business-edit" -> EditBusinessPageDialog(page, busy, { finish() }, { _, _ -> }) { title, _, _, _, _, _ -> saved(title) }
                        else -> BusinessPageDialog(busy, { finish() }) { title, category, _, _, _, _ -> saved("$title|$category") }
                    }
                } else if (intent.getStringExtra("screen") == "recents") {
                    Box(Modifier.statusBarsPadding()) {
                        MessagesScreen(
                            conversations = listOf(WhappyConversation("qa-recent", WhappyMember("qa-peer", "Contact de test"), "Discussion conservée", 0, false)),
                            accountName = "Test local", accountPhotoUrl = "", businessMode = false,
                            loading = false, preview = false, contactBusy = false, contacts = emptyList(),
                            contactSearchResult = null, contactSearchPhone = "", contactSearchMessage = null,
                            storyStatuses = emptyList(), businessResults = emptyList(), businessSearchBusy = false,
                            channels = emptyList(), currentUserId = "qa-me", channelBusy = false,
                            initialQuery = "", initialSection = 0, onSearchContact = {}, onAddSearchedContact = {},
                            onClearContactSearch = {}, onOpenContact = {}, onSearchBusinesses = {},
                            onContactBusiness = {}, onOpen = {}, onOpenChannel = {}, onCreateChannel = { _, _, _ -> },
                            onCreateGroup = { _, _, _, _ -> }, onSubscribeChannel = { _, _ -> }, onHandleWhappyLink = {},
                            recentTabs = emptyList(), onOpenRecent = {}, onOpenStory = {},
                        )
                    }
                } else if (intent.getStringExtra("screen") == "audio") {
                    androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding()) {
                        VoiceNoteMessage(audioFixture, 25, false, title = "Note test A")
                        VoiceNoteMessage(audioFixture, 25, false, title = "Note test B")
                    }
                } else if (intent.getStringExtra("screen") in listOf("commerce", "tickets")) {
                    var ticketStatus by remember { mutableStateOf("") }
                    val date = remember { System.currentTimeMillis() + 86_400_000L }
                    var saveAttempts by rememberSaveable { mutableIntStateOf(0) }
                    WapiCommerceApp(if (intent.getStringExtra("screen") == "tickets") "events" else "directory", { finish() }, {}, gateway = { action, data ->
                        val cafe = mapOf("id" to "qa-cafe", "name" to "Café Atelier · TEST", "category" to "Restaurant & café", "city" to "Lomé", "bio" to "Une table de quartier, une cuisine de saison.", "address" to "12 avenue des Arts", "hours" to "Lun–sam · 09:00–20:00", "published" to true)
                        val ticket = mapOf("id" to "qa-ticket", "eventId" to "qa-concert", "title" to "Concert acoustique · TEST", "venue" to "Café Atelier · Lomé", "startsAt" to date, "status" to ticketStatus, "code" to "wapi://ticketbulk/qa-ticket?token=00000000-0000-0000-0000-000000000000")
                        when(action) {
                            "saveProduct", "saveStorefront", "createEvent" -> {
                                saveAttempts++
                                android.util.Log.i("WapiCommerceQA", "save=$saveAttempts action=$action id=${data["productId"] ?: data["eventId"]} name=${data["name"]}")
                                kotlinx.coroutines.delay(1200)
                                if (intent.getBooleanExtra("failFirstSave", false) && saveAttempts == 1) error("Connexion indisponible · test local. Votre saisie est conservée.")
                                mapOf("saved" to true)
                            }
                            "ownPages" -> mapOf("pages" to listOf(cafe))
                            "directory" -> mapOf("stores" to listOf(cafe))
                            "events" -> mapOf("events" to listOf(mapOf("id" to "qa-concert", "title" to "Concert acoustique · TEST", "venue" to "Café Atelier · Lomé", "organizer" to "Café Atelier", "ownerId" to "qa-organizer", "description" to "Une soirée musicale en petit comité.", "startsAt" to date, "capacity" to 40, "reserved" to if (ticketStatus == "issued") 1 else 0, "status" to "published", "myTicketStatus" to ticketStatus)))
                            "reserveTicket" -> { ticketStatus = "issued"; mapOf("ticket" to (ticket + ("status" to "issued"))) }
                            "myTickets" -> mapOf("tickets" to if (ticketStatus.isBlank()) emptyList() else listOf(ticket))
                            "cancelTicket" -> { ticketStatus = "cancelled"; mapOf("cancelled" to true) }
                            "storefront" -> mapOf("page" to cafe, "owner" to true, "products" to listOf(
                                mapOf("id" to "qa-1", "productId" to "qa-1", "name" to "Menu du déjeuner", "description" to "Plat du jour et dessert maison", "category" to "Menus", "priceMinor" to 5500, "currency" to "XOF", "available" to true),
                                mapOf("id" to "qa-2", "productId" to "qa-2", "name" to "Café & viennoiserie", "description" to "Café fraîchement moulu, viennoiserie du jour", "category" to "Petits déjeuners", "priceMinor" to 2500, "currency" to "XOF", "available" to false)))
                            else -> emptyMap()
                        }
                    })
                } else if (intent.getStringExtra("screen") == "chess") {
                    androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                        WapiTabletop3DView(context).apply {
                            val back = listOf("♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜")
                            val white = listOf("♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖")
                            setStrategyScene(WapiTabletop3DView.Scene.CHESS, back + List(8) { "♟" } + List(32) { "" } + List(8) { "♙" } + white, -1, emptySet())
                        }
                    })
                } else {
                    val peer = WhappyMember("qa-peer", "Contact de test")
                    val conversation = remember { WhappyConversation("qa-keyboard", peer, "Dernier message", System.currentTimeMillis(), false) }
                    var messages by remember {
                        mutableStateOf((1..18).map { index -> WhappyMessage("m$index", if (index == 18) "Dernier message : je reste visible au-dessus du clavier." else "Message $index · discussion de test", if (index % 3 == 0) "qa-me" else "qa-peer", System.currentTimeMillis() - (18 - index) * 60000L) })
                    }
                    Box(Modifier.statusBarsPadding()) {
                        ChatScreen(conversation, messages, emptyList(), "qa-me", true, false, false, false, WapiGroupUpdateState(),
                            onBack = { finish() }, onSend = { text, _ -> messages = messages + WhappyMessage("sent-${System.nanoTime()}", text, "qa-me", System.currentTimeMillis()) },
                            onSendMedia = { _, _, _, _, _, _ -> }, onMarkViewOnce = {}, onReact = { _, _ -> }, onDelete = {}, onEdit = { _, _ -> }, onTyping = {}, onRetryPending = {},
                            onUpdateGroup = { _, _, _, _ -> }, onSetGroupAdministrator = { _, _ -> }, onManageGroupMembers = { _, _ -> }, onUpdateGroupSettings = { _, _, _ -> }, availableContacts = emptyList(),
                            onOpenMember = {}, onSetLiveSubscription = { _, _ -> }, publicRadioEpisodes = emptyList(), requestedGroupCall = null, onConsumeGroupCallLink = {}, onOpenStory = {})
                    }
                }
            }
        }
    }
}
