package com.whappy.chat

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.SurfaceViewRenderer

private data class WapiLiveComment(
    val id: String,
    val authorName: String,
    val text: String,
)

private data class WapiLiveGift(
    val id: String,
    val giftId: String,
    val label: String,
    val symbol: String,
    val senderName: String,
    val equipped: Boolean,
)

private data class WapiLiveViewer(
    val id: String,
    val name: String,
    val photoUrl: String,
)

/**
 * Studio et lecteur Live Android 100 % natifs. Le média est diffusé par le
 * moteur WebRTC P2P WAPI ; Firestore ne conserve que la signalisation, l'état,
 * les commentaires et la modération.
 */
@Composable
internal fun WapiNativeLiveRoomDialog(
    live: WhappyLive,
    expectedHost: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    var reconnectAttempt by remember(live.id) { mutableIntStateOf(0) }
    val rtcEngine = remember(live.id, reconnectAttempt) { WapiLiveRtcEngine(context, scope) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var connecting by remember { mutableStateOf(true) }
    var connectionLabel by remember { mutableStateOf("Connexion sécurisée…") }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var isHost by remember { mutableStateOf(expectedHost) }
    var microphoneEnabled by remember { mutableStateOf(expectedHost) }
    var cameraEnabled by remember { mutableStateOf(expectedHost && !live.audioOnly) }
    var viewerCount by remember { mutableIntStateOf(live.viewerCount) }
    var reactionCount by remember { mutableIntStateOf(0) }
    var giftCount by remember { mutableIntStateOf(live.giftCount) }
    var allowGiftWearables by remember { mutableStateOf(live.allowGiftWearables) }
    var showGiftPanel by remember { mutableStateOf(false) }
    var activeGift by remember { mutableStateOf<WapiLiveGift?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<WapiLiveComment>() }
    val activeViewers = remember { mutableStateListOf<WapiLiveViewer>() }
    var recentJoin by remember { mutableStateOf<String?>(null) }
    var liveListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var commentListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var giftListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var viewerListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var kingQiListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var kingQiRoom by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    fun close(endLive: Boolean) {
        scope.launch {
            runCatching {
                if (isHost && endLive) {
                    functions.getHttpsCallable("setLiveSessionState")
                        .call(mapOf("liveId" to live.id, "action" to "end"))
                        .await()
                } else if (!isHost) {
                    functions.getHttpsCallable("setLivePresence")
                        .call(mapOf("liveId" to live.id, "action" to "disconnected"))
                        .await()
                }
            }
            rtcEngine.close()
            onDismiss()
        }
    }

    LaunchedEffect(live.id, reconnectAttempt) {
        rtcEngine.onStateChanged = { label, isConnected, mediaError ->
            connectionLabel = label
            connecting = !isConnected && mediaError == null
            fatalError = mediaError
        }
        try {
            rtcEngine.start(live.id, expectedHost, live.audioOnly)
            isHost = expectedHost
            microphoneEnabled = expectedHost
            cameraEnabled = expectedHost && !live.audioOnly
            connecting = false
            connectionLabel = "En direct"
        } catch (error: Throwable) {
            connecting = false
            fatalError = wapiUserFacingError(error, "Le direct")
            rtcEngine.close()
        }
    }

    DisposableEffect(live.id, reconnectAttempt) {
        liveListener = firestore.collection("liveSessions").document(live.id)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data ?: return@addSnapshotListener
                viewerCount = (data["viewerCount"] as? Number)?.toInt() ?: 0
                reactionCount = (data["reactionCount"] as? Number)?.toInt() ?: 0
                giftCount = (data["giftCount"] as? Number)?.toInt() ?: 0
                allowGiftWearables = data["allowGiftWearables"] == true
                val linkedRoomId = data["kingQiRoomId"]?.toString().orEmpty()
                if (linkedRoomId.isNotBlank() && linkedRoomId != kingQiRoom["id"]?.toString()) {
                    kingQiListener?.remove()
                    kingQiListener = firestore.collection("kingQiRooms").document(linkedRoomId).addSnapshotListener { roomSnapshot, _ ->
                        kingQiRoom = (roomSnapshot?.data ?: emptyMap()) + ("id" to linkedRoomId)
                    }
                }
                if (!isHost && data["status"] == "ended") fatalError = "Ce direct est terminé."
            }
        commentListener = firestore.collection("liveSessions").document(live.id)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snapshot, _ ->
                val next = snapshot?.documents.orEmpty().reversed().map { document ->
                    WapiLiveComment(
                        document.id,
                        document.getString("authorName") ?: "Membre WAPI",
                        document.getString("text").orEmpty(),
                    )
                }
                comments.clear()
                comments.addAll(next)
            }
        giftListener = firestore.collection("liveSessions").document(live.id)
            .collection("gifts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                val document = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val createdAt = document.getTimestamp("createdAt")?.toDate()?.time ?: return@addSnapshotListener
                if (System.currentTimeMillis() - createdAt > 12_000L) return@addSnapshotListener
                activeGift = WapiLiveGift(
                    id = document.id,
                    giftId = document.getString("giftId").orEmpty(),
                    label = document.getString("label") ?: "Cadeau WAPI",
                    symbol = document.getString("symbol") ?: "🎁",
                    senderName = document.getString("senderName") ?: "Spectateur WAPI",
                    equipped = document.getBoolean("equipped") == true,
                )
            }
        if (expectedHost) viewerListener = firestore.collection("liveSessions").document(live.id)
            .collection("viewers")
            .addSnapshotListener { snapshot, _ ->
                val previousIds = activeViewers.map { it.id }.toSet()
                val next = snapshot?.documents.orEmpty().mapNotNull { document ->
                    if (document.getBoolean("active") != true) return@mapNotNull null
                    WapiLiveViewer(
                        id = document.id,
                        name = document.getString("displayName")?.takeIf(String::isNotBlank) ?: "Membre WAPI",
                        photoUrl = document.getString("photoUrl").orEmpty(),
                    )
                }.take(12)
                next.firstOrNull { it.id !in previousIds }?.let { recentJoin = "${it.name} vient de rejoindre" }
                activeViewers.clear()
                activeViewers.addAll(next)
            }
        onDispose {
            liveListener?.remove()
            commentListener?.remove()
            giftListener?.remove()
            viewerListener?.remove()
            kingQiListener?.remove()
            rtcEngine.onStateChanged = null
            rtcEngine.close()
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    fun sendComment() {
        val text = commentDraft.trim().take(280)
        if (text.isBlank()) return
        commentDraft = ""
        scope.launch {
            runCatching {
                firestore.collection("liveSessions").document(live.id).collection("comments").add(
                    mapOf(
                        "authorId" to auth.currentUser?.uid.orEmpty(),
                        "authorName" to (auth.currentUser?.displayName ?: auth.currentUser?.phoneNumber ?: "Membre WAPI"),
                        "text" to text,
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }
    }

    fun sendGift(giftId: String) {
        showGiftPanel = false
        scope.launch {
            runCatching {
                functions.getHttpsCallable("sendLiveGift").call(mapOf("liveId" to live.id, "giftId" to giftId)).await()
            }.onFailure { connectionLabel = wapiUserFacingError(it, "L’envoi du cadeau") }
        }
    }

    LaunchedEffect(activeGift?.id) {
        if (activeGift == null) return@LaunchedEffect
        delay(4_500)
        activeGift = null
    }

    LaunchedEffect(recentJoin) {
        if (recentJoin == null) return@LaunchedEffect
        delay(3_500)
        recentJoin = null
    }

    Dialog(
        onDismissRequest = { close(endLive = false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (!live.audioOnly) AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        SurfaceViewRenderer(viewContext).also { view ->
                            view.setMirror(isHost)
                            rtcEngine.attachRenderer(view, local = isHost)
                        }
                    },
                    update = { it.setMirror(isHost) },
                    onRelease = { rtcEngine.detachRenderer(it, local = isHost) },
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .68f), Color.Transparent, Color.Black.copy(alpha = .86f))),
                    ),
                )
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { close(endLive = false) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = .35f)),
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Fermer", tint = Color.White) }
                        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (live.audioOnly) "● RADIO" else "● LIVE", Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF0445A)).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 9.sp)
                                Text("  ${live.title}", color = Color.White, maxLines = 1)
                            }
                            Text("$connectionLabel · $viewerCount spectateur(s) · $reactionCount réaction(s) · $giftCount cadeau(x)", color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = {
                                val invite = Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, live.title)
                                        putExtra(Intent.EXTRA_TEXT, "Rejoignez ${live.title} sur WAPI\nwhappy://live/${live.id}")
                                    },
                                    "Inviter au Live WAPI",
                                )
                                context.startActivity(invite)
                            },
                        ) { Icon(Icons.Rounded.Share, "Inviter", tint = Color.White) }
                        if (isHost) Button(onClick = { close(endLive = true) }) { Icon(Icons.Rounded.Stop, null); Text(" Fin") }
                    }
                    if (kingQiRoom.isNotEmpty()) {
                        WapiKingQiLiveOverlay(
                            room = kingQiRoom,
                            currentUserId = auth.currentUser?.uid.orEmpty(),
                            isHost = isHost,
                            onAnswer = { option -> scope.launch { runCatching { functions.getHttpsCallable("kingQiSubmitAnswer").call(mapOf("roomId" to kingQiRoom["id"].toString(), "optionIndex" to option)).await() } } },
                            onAdvance = { scope.launch { runCatching { functions.getHttpsCallable("kingQiAdvanceTournament").call(mapOf("roomId" to kingQiRoom["id"].toString())).await() } } },
                        )
                    }
                    if (activeViewers.isNotEmpty()) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = .34f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            activeViewers.take(5).forEach { viewer ->
                                UserAvatar(viewer.photoUrl, viewer.name, 26.dp, Modifier.padding(end = 4.dp))
                            }
                            Text(
                                recentJoin ?: "${activeViewers.size} connecté${if (activeViewers.size > 1) "s" else ""} maintenant",
                                Modifier.padding(start = 5.dp),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (live.audioOnly) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(104.dp).clip(CircleShape).background(WhappyBlue.copy(alpha = .78f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
                        Text(live.hostName, Modifier.padding(top = 12.dp), color = Color.White, fontSize = 18.sp)
                        Text("Radio en direct · haute qualité", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    LazyColumn(
                        Modifier.fillMaxWidth().fillMaxHeight(.23f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(25.dp).clip(CircleShape).background(WhappyBlue), contentAlignment = Alignment.Center) {
                                    Text(comment.authorName.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    "${comment.authorName}  ${comment.text}",
                                    Modifier.padding(start = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .48f)).padding(horizontal = 11.dp, vertical = 8.dp),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    if (!isHost && showGiftPanel) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("crown" to "👑", "glasses" to "😎", "halo" to "✨", "trophy" to "🏆").forEach { gift ->
                                FilledIconButton(
                                    onClick = { sendGift(gift.first) },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = WhappyBlue),
                                ) { Text(gift.second, fontSize = 23.sp) }
                            }
                        }
                    }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), color = Color.Black.copy(alpha = .44f), shape = RoundedCornerShape(28.dp)) {
                        Row(Modifier.padding(start = 7.dp, end = 5.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(31.dp).clip(CircleShape).background(Color(0xFFF0445A)), contentAlignment = Alignment.Center) { Text("LIVE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black) }
                            TextField(
                                value = commentDraft,
                                onValueChange = { commentDraft = it.take(280) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Écrire à tout le direct…", color = Color.White.copy(alpha = .64f)) },
                                singleLine = true,
                                shape = RoundedCornerShape(22.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                            FilledIconButton(onClick = ::sendComment, modifier = Modifier.size(43.dp), enabled = commentDraft.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = WhappyBlue)) {
                                Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer", tint = Color.White)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (isHost) {
                            WapiLiveControl(if (microphoneEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff, "Micro", microphoneEnabled) {
                                microphoneEnabled = rtcEngine.setMicrophone(!microphoneEnabled)
                            }
                            if (!live.audioOnly) WapiLiveControl(if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", cameraEnabled) {
                                cameraEnabled = rtcEngine.setCamera(!cameraEnabled)
                            }
                            if (!live.audioOnly) WapiLiveControl(Icons.Rounded.Cameraswitch, "Retourner", true) { rtcEngine.switchCamera() }
                            WapiLiveControl(Icons.Rounded.AutoAwesome, if (allowGiftWearables) "Cadeaux portés" else "Cadeaux simples", allowGiftWearables) {
                                scope.launch {
                                    runCatching {
                                        functions.getHttpsCallable("setLiveGiftWearables").call(mapOf("liveId" to live.id, "enabled" to !allowGiftWearables)).await()
                                    }
                                }
                            }
                        } else {
                            WapiLiveControl(Icons.Rounded.Favorite, "Réagir", true) {
                                scope.launch {
                                    runCatching {
                                        functions.getHttpsCallable("sendLiveReaction")
                                            .call(mapOf("liveId" to live.id, "reaction" to "heart"))
                                            .await()
                                    }
                                }
                            }
                            WapiLiveControl(Icons.Rounded.AutoAwesome, "Cadeau", showGiftPanel) { showGiftPanel = !showGiftPanel }
                        }
                    }
                }
                activeGift?.let { gift ->
                    Column(Modifier.align(if (gift.equipped) Alignment.TopCenter else Alignment.Center).padding(top = if (gift.equipped) 86.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { giftContext -> WapiGift3DView(giftContext).also { it.setGift(gift.giftId) } },
                                update = { it.setGift(gift.giftId) },
                                onRelease = { it.onPause() },
                            )
                            Text(gift.symbol, fontSize = 42.sp)
                        }
                        Text("${gift.senderName} offre ${gift.label}", Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .62f)).padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
                        if (gift.equipped) Text("Effet portable autorisé par l’animateur", Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
                    }
                }
                if (connecting) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Connexion au Live WAPI…", Modifier.padding(top = 12.dp), color = Color.White)
                }
                fatalError?.let { message ->
                    Surface(Modifier.align(Alignment.Center).padding(28.dp), color = Color.White, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Direct indisponible", style = MaterialTheme.typography.titleLarge)
                            Text(message, Modifier.padding(vertical = 12.dp), color = WhappyMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    rtcEngine.close()
                                    fatalError = null
                                    connecting = true
                                    connectionLabel = "Reconnexion sécurisée…"
                                    reconnectAttempt += 1
                                }) { Text("Reprendre") }
                                OutlinedButton(onClick = { close(endLive = false) }) { Text("Fermer") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WapiKingQiLiveOverlay(
    room: Map<String, Any?>,
    currentUserId: String,
    isHost: Boolean,
    onAnswer: (Int) -> Unit,
    onAdvance: () -> Unit,
) {
    val status = room["status"]?.toString().orEmpty()
    val question = room["currentQuestion"] as? Map<*, *>
    val playerIds = (room["playerIds"] as? List<*>)?.map { it.toString() }.orEmpty()
    val answeredIds = (room["answeredIds"] as? List<*>)?.map { it.toString() }.orEmpty()
    val scores = room["scores"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val names = room["playerNames"] as? Map<*, *> ?: emptyMap<Any, Any>()
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE6071B3E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC83D).copy(alpha = .65f)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♛ KING QI", color = Color(0xFFFFC83D), fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("${room["potCredits"] ?: 0} CRÉDITS", color = Color.White.copy(alpha = .72f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (status == "waiting") {
                Text("Le tournoi va commencer · ${playerIds.size} joueur(s)", color = Color.White, fontWeight = FontWeight.Bold)
            } else if (status == "finished") {
                val winners = (room["winners"] as? List<*>)?.map { it.toString() }.orEmpty()
                Text(if (winners.contains(currentUserId)) "🏆 GRAND CHAMPION" else "Tournoi terminé", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            } else if (question != null) {
                Text(question["text"]?.toString() ?: "Question King QI", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 2)
                val canAnswer = playerIds.contains(currentUserId) && !answeredIds.contains(currentUserId)
                (question["options"] as? List<*>)?.chunked(2)?.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { raw ->
                            val all = question["options"] as? List<*> ?: emptyList<Any>()
                            val index = all.indexOf(raw)
                            Button(onClick = { onAnswer(index) }, enabled = canAnswer, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = WhappyBlue, disabledContainerColor = Color.White.copy(alpha = .13f))) {
                                Text(raw.toString(), fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
                val leader = playerIds.maxByOrNull { (scores[it] as? Number)?.toInt() ?: 0 }
                if (leader != null) Text("En tête : ${names[leader] ?: "Joueur WAPI"} · ${scores[leader] ?: 0} pts", color = Color.White.copy(alpha = .68f), fontSize = 10.sp)
                if (isHost) OutlinedButton(onClick = onAdvance, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) { Text("Manche suivante", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun WapiLiveControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) WhappyBlue else Color.White.copy(alpha = .18f),
                contentColor = Color.White,
            ),
        ) { Icon(icon, label) }
        Text(label, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 10.sp)
    }
}
