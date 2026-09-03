package com.whappy.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal val poolPlayerIcons = linkedMapOf("cue" to "🎱", "crown" to "👑", "fox" to "🦊", "lion" to "🦁", "robot" to "🤖")

/** Wapi Pool's own compact game identity.  It deliberately uses WAPI's blue
 * language, real numbered balls and a cue rather than borrowing a competitor's
 * logo or menu treatment. */
@Composable internal fun WapiPoolLogo(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF071D35),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF2E83BF)),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(Modifier.size(66.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    drawLine(Color(0xFFE5B570), Offset(4f, size.height - 5f), Offset(size.width - 3f, 6f), 3.5f)
                    drawLine(Color(0xFF51351D), Offset(4f, size.height - 1.8f), Offset(size.width - 3f, 9f), 1.4f)
                }
                PoolLogoBall("8", Color(0xFF151923), Modifier.align(Alignment.CenterStart).offset(x = 1.dp, y = 8.dp))
                PoolLogoBall("9", Color(0xFFF4A92B), Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 1.dp))
                PoolLogoBall("2", Color(0xFF1977D3), Modifier.align(Alignment.BottomEnd).offset(x = (-1).dp, y = (-1).dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("WAPI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("POOL", color = Color(0xFF54CBFF), fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("BILLARD SOCIAL", color = Color(0xFF94B9D5), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
            }
        }
    }
}

@Composable private fun PoolLogoBall(number: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = .44f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFF9FBFF)), contentAlignment = Alignment.Center) {
            Text(number, color = Color(0xFF102238), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
/** Real gameplay characteristics, shared by the equipment selector and the
 * shot engine.  They are intentionally modest: a cosmetic item must never
 * decide a match on its own. */
internal data class WapiPoolCue(
    val id: String,
    val name: String,
    val tier: String,
    val priceTokens: Int,
    val powerMultiplier: Float,
    val aimBonus: Int,
    val spinBonus: Int,
    val tempoBonus: Int,
)

internal val wapiPoolCues = listOf(
    WapiPoolCue("maple", "Érable Atelier", "Classique", 0, 1.00f, 0, 0, 0),
    WapiPoolCue("walnut", "Noyer Signature", "Précision", 250, 1.04f, 2, 1, 1),
    WapiPoolCue("carbon", "Carbone Vector", "Performance", 700, 1.07f, 3, 3, 2),
    WapiPoolCue("obsidian", "Obsidienne WAPI", "Fondateur", 1_500, 1.09f, 4, 4, 3),
)
internal fun wapiPoolCue(id: String) = wapiPoolCues.firstOrNull { it.id == id } ?: wapiPoolCues.first()

/** Local collection cache. Purchases use only WAPI game tokens, never money. */
internal class PoolCueCollection(context: Context, private val founder: Boolean) {
    private val prefs = context.getSharedPreferences("wapi_pool_collection_${FirebaseAuth.getInstance().currentUser?.uid.orEmpty()}", Context.MODE_PRIVATE)
    var tokens by mutableIntStateOf(prefs.getInt("tokens", 1_000))
    var owned by mutableStateOf(prefs.getStringSet("owned", setOf("maple")).orEmpty())
    fun has(cue: WapiPoolCue) = founder || cue.id in owned
    fun acquire(cue: WapiPoolCue): Boolean {
        if (has(cue)) return true
        if (tokens < cue.priceTokens) return false
        tokens -= cue.priceTokens
        owned = owned + cue.id
        prefs.edit().putInt("tokens", tokens).putStringSet("owned", owned).apply()
        return true
    }
}

@Composable internal fun rememberPoolCueCollection(founder: Boolean): PoolCueCollection {
    val context = LocalContext.current.applicationContext
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    return remember(context, uid, founder) { PoolCueCollection(context, founder) }
}
internal data class PoolPlayerCard(val name: String, val photo: String = "", val icon: String = "cue", val points: Int = 0,
    val victories: Int = 0, val defeats: Int = 0, val bestRun: Int = 0, val avatarMode: String = "account")
internal fun poolPlayerCard(raw: Map<*, *>, fallback: String) = PoolPlayerCard(
    raw["displayName"]?.toString()?.takeIf { it.isNotBlank() } ?: fallback, raw["photoUrl"]?.toString().orEmpty(),
    raw["avatarIcon"]?.toString() ?: "cue", (raw["points"] as? Number)?.toInt() ?: 0,
    (raw["victories"] as? Number)?.toInt() ?: 0, (raw["defeats"] as? Number)?.toInt() ?: 0,
    (raw["bestRun"] as? Number)?.toInt() ?: 0, raw["avatarMode"]?.toString() ?: "account",
)

internal class PoolProfileState(val context: Context, val uid: String, fallback: PoolPlayerCard) {
    private val prefs = context.getSharedPreferences("wapi_pool_player_$uid", Context.MODE_PRIVATE)
    var player by mutableStateOf(runCatching {
        val j = JSONObject(prefs.getString("profile", "{}")!!)
        if (!j.has("displayName")) fallback else poolPlayerCard(j.keys().asSequence().associateWith { j.get(it) }, fallback.name)
    }.getOrDefault(fallback))
    var verified by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var localBest by mutableIntStateOf(prefs.getInt("localBest", 0))
    var localVictories by mutableIntStateOf(prefs.getInt("localVictories", 0))
    var localDefeats by mutableIntStateOf(prefs.getInt("localDefeats", 0))
    var localCups by mutableIntStateOf(prefs.getInt("localCups", 0))
    suspend fun load() {
        if (uid.isBlank()) return
        runCatching {
            val data = FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("getGameProfile")
                .call(mapOf("gameId" to "billard")).await().data as? Map<*, *>
            accept(data?.get("profile") as? Map<*, *> ?: error("Profil absent"))
        }.onFailure { error = "Profil en ligne indisponible. Les records d’entraînement restent sur cet appareil." }
    }
    suspend fun save(name: String, mode: String, icon: String, photo: Uri?) {
        check(uid.isNotBlank()) { "Connectez-vous à WAPI pour enregistrer votre profil joueur." }
        val payload = mutableMapOf<String, Any>("gameId" to "billard", "displayName" to name.trim(), "avatarMode" to mode, "avatarIcon" to icon)
        if (photo != null && mode == "photo") payload["photoBase64"] = withContext(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(photo).use { BitmapFactory.decodeStream(it) } ?: error("Photo illisible")
            val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
            val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            val bytes = ByteArrayOutputStream().use { out -> resized.compress(Bitmap.CompressFormat.JPEG, 88, out); out.toByteArray() }
            if (resized !== bitmap) resized.recycle(); bitmap.recycle()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        val data = FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("saveGameProfile").call(payload).await().data as? Map<*, *>
        accept(data?.get("profile") as? Map<*, *> ?: error("Le profil n’a pas été enregistré."))
    }
    private fun accept(raw: Map<*, *>) {
        player = poolPlayerCard(raw, player.name); verified = true; error = null
        prefs.edit().putString("profile", JSONObject(raw.mapKeys { it.key.toString() }).toString()).apply()
    }
    fun recordPractice(score: Int) {
        if (score > localBest) { localBest = score; prefs.edit().putInt("localBest", score).apply() }
    }
    fun recordPracticeResult(score: Int, won: Boolean) {
        recordPractice(score)
        if (won) localVictories++ else localDefeats++
        prefs.edit().putInt("localVictories", localVictories).putInt("localDefeats", localDefeats).apply()
    }
    fun awardLocalCup() { localCups++; prefs.edit().putInt("localCups", localCups).apply() }
}

@Composable internal fun rememberPoolProfile(): PoolProfileState {
    val context = LocalContext.current.applicationContext
    val user = FirebaseAuth.getInstance().currentUser
    val state = remember(user?.uid) { PoolProfileState(context, user?.uid.orEmpty(), PoolPlayerCard(user?.displayName?.takeIf { it.isNotBlank() } ?: "Vous", user?.photoUrl?.toString().orEmpty())) }
    LaunchedEffect(state.uid) { state.load() }
    return state
}

@Composable internal fun PoolPlayerAvatar(player: PoolPlayerCard, size: Int = 40) {
    if (player.photo.isNotBlank()) UserAvatar(player.photo, player.name, size.dp, shape = RoundedCornerShape(12.dp))
    else Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF163657)), contentAlignment = Alignment.Center) {
        Text(poolPlayerIcons[player.icon] ?: "🎱", fontSize = (size * .52f).sp)
    }
}

internal fun poolRemainingIds(group: WapiPoolGroup, balls: List<WapiPoolBall>): List<Int> =
    if (group == WapiPoolGroup.OPEN) emptyList() else balls.filter { !it.pocketed && wapiPoolGroupForBall(it.id) == group }.map { it.id }.sorted()

@Composable internal fun PoolMatchScoreboard(
    left: PoolPlayerCard, right: PoolPlayerCard, leftGroup: WapiPoolGroup, rightGroup: WapiPoolGroup,
    balls: List<WapiPoolBall>, leftActive: Boolean, rightActive: Boolean, leftSeconds: Int, rightSeconds: Int, status: String,
    onEditProfile: () -> Unit, modifier: Modifier = Modifier,
) {
    Row(
        // The table is the game.  Player identity must stay visible without
        // covering the upper third of the cloth on a phone or a tablet.
        modifier.widthIn(max = 620.dp).fillMaxWidth().semantics { stateDescription = status },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PoolPlayerScore(left, leftGroup, balls, leftActive, leftSeconds, Modifier.weight(1f), onEditProfile)
        // The play state remains available to accessibility and the game
        // engine, but no longer sits as a third text block over the table.
        // Active player rings provide the immediate in-game signal.
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(
                when {
                    status.contains("Victoire", ignoreCase = true) -> Color(0xFFFFD36B)
                    leftActive || rightActive -> Color(0xFF51C3FF)
                    else -> Color(0xFF5E778D)
                },
            ),
        )
        PoolPlayerScore(right, rightGroup, balls, rightActive, rightSeconds, Modifier.weight(1f), null)
    }
}

@Composable private fun PoolPlayerScore(player: PoolPlayerCard, group: WapiPoolGroup, balls: List<WapiPoolBall>, active: Boolean, seconds: Int, modifier: Modifier, onClick: (() -> Unit)?) {
    val ids = poolRemainingIds(group, balls)
    Surface(modifier.then(if (onClick != null) Modifier.wapiClickable(onClick = onClick) else Modifier),
        color = Color(0xF0071C31), shape = RoundedCornerShape(12.dp), border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) Color(0xFF51C3FF) else Color(0xFF24425C))) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PoolPlayerAvatar(player, 29)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.name, Modifier.weight(1f), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (active) Text("${seconds.coerceAtLeast(0)} s", color = Color(0xFF8FF1BA), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Text(if (group == WapiPoolGroup.OPEN) "Après la casse" else "${ids.size} · ${if (group == WapiPoolGroup.SOLIDS) "pleines" else "rayées"}", color = Color(0xFFB8D5E9), fontSize = 8.sp, maxLines = 1)
                if (active) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LinearProgressIndicator(progress = { seconds.coerceIn(0, 30) / 30f }, modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF47E390), trackColor = Color.White.copy(alpha = .14f))
                }
                if (group != WapiPoolGroup.OPEN) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (if (ids.isEmpty()) listOf(8) else ids).forEach { id ->
                        PoolScoreBall(id, Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** Miniature of the same numbered balls used on the physical table. Keeping
 * the stripe, number plate and specular highlight in the score makes the
 * players' remaining balls readable at a glance instead of looking like dots. */
@Composable private fun PoolScoreBall(id: Int, modifier: Modifier = Modifier.size(18.dp)) {
    val base = when ((id - 1).mod(8)) {
        0 -> Color(0xFFF7C818); 1 -> Color(0xFF2468D7); 2 -> Color(0xFFD62D32); 3 -> Color(0xFF7940AE)
        4 -> Color(0xFFE97920); 5 -> Color(0xFF1E9B59); 6 -> Color(0xFF8B202B); else -> Color(0xFF11141C)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            val center = Offset(r, r)
            drawCircle(Color.Black.copy(alpha = .52f), radius = r * .91f, center = Offset(center.x, center.y + r * .13f))
            drawCircle(base, radius = r * .91f, center = center)
            if (id >= 9) {
                val circle = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
                clipPath(circle) { drawRect(Color(0xFFF7FAFF), topLeft = Offset(0f, r * .63f), size = androidx.compose.ui.geometry.Size(size.width, r * .74f)) }
            }
            drawCircle(Color.White.copy(alpha = .48f), radius = r * .18f, center = Offset(r * .67f, r * .56f))
            drawCircle(Color(0xFFF8FAFD), radius = r * .42f, center = center)
        }
        Text(id.toString(), color = Color(0xFF102338), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable internal fun PoolPlacementControl(enabled: Boolean, valid: Boolean, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onConfirm, enabled = enabled && valid, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Rounded.PanTool, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(if (valid) "Poser la blanche" else "Choisir un emplacement libre")
    }
}

/** Fine aim is independent of power: 100 px moves the cue by 8.6 degrees. */
@Composable internal fun PoolAimWheel(angle: Float, enabled: Boolean, onAngleChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(angle)
    val change by rememberUpdatedState(onAngleChange)
    Canvas(modifier.size(40.dp, 148.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xED071C31))
        .semantics { contentDescription = "Visée précise"; stateDescription = if (enabled) "Glissez pour ajuster la queue" else "Patientez" }
        .pointerInput(enabled) {
            var draft = current
            detectDragGestures(onDragStart = { draft = current }) { event, drag ->
                if (enabled) {
                    event.consume(); draft += drag.y * .0015f
                    // A full turn must not drift beyond the server's angle range.
                    change(kotlin.math.atan2(kotlin.math.sin(draft), kotlin.math.cos(draft)))
                }
            }
        }) {
        val spacing = 12.dp.toPx()
        val shift = (angle * 90f) % spacing
        for (i in -8..8) {
            val y = size.height / 2 + i * spacing + shift
            if (y > 8 && y < size.height - 8) drawLine(Color.White.copy(alpha = if (enabled) .38f else .12f), Offset(size.width * .3f, y), Offset(size.width * .70f, y), 1.dp.toPx())
        }
        drawLine(Color(0xFF51C3FF).copy(alpha = if (enabled) 1f else .25f), Offset(size.width * .15f, size.height / 2), Offset(size.width * .85f, size.height / 2), 3.dp.toPx())
    }
}

@Composable internal fun PoolPlayerEditor(state: PoolProfileState, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.player.name) }
    var mode by rememberSaveable { mutableStateOf(state.player.avatarMode) }
    var icon by rememberSaveable { mutableStateOf(state.player.icon) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var crop by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { crop = it }
    WapiEditorScreen("Mon profil · Billard", "Enregistrer le profil", busy, name.trim().length in 2..40 && state.uid.isNotBlank(), onDismiss, {
        scope.launch {
            busy = true; failure = null
            runCatching { state.save(name, mode, icon, photo) }.onSuccess { onDismiss() }
                .onFailure { failure = "Profil non enregistré. Vérifiez la connexion et réessayez ; votre saisie est conservée." }
            busy = false
        }
    }) {
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PoolPlayerAvatar(state.player.copy(name = name, icon = icon, photo = if (mode == "icon") "" else photo?.toString() ?: state.player.photo), 64)
            TextButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Photo de la galerie") }
        } }
        item { WapiEditorField(name, { name = it }, "Pseudo joueur", 40, enabled = !busy) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { poolPlayerIcons.forEach { (id, emoji) ->
            FilterChip(selected = mode == "icon" && icon == id, enabled = !busy, onClick = { icon = id; mode = "icon" }, label = { Text(emoji, fontSize = 22.sp) })
        } } }
        item { WapiEditorSection("Résultats en ligne", if (state.verified) "Validés par le serveur WAPI." else "Dernière synchronisation disponible ; aucun point local n’est ajouté au classement.") }
        item { Text("${state.player.points} points · ${state.player.victories} victoires · ${state.player.defeats} défaites\nMeilleure série : ${state.player.bestRun} billes") }
        item { WapiEditorSection("Entraînement sur cet appareil", "${state.localVictories} victoires · ${state.localDefeats} défaites · ${state.localCups} coupe(s) · record ${state.localBest} points. Distinct du classement serveur.") }
        item { Text("Championnat avec dotation : aucun paiement ni gain en argent n’est activé dans cette version.", color = WhappyMuted) }
        if (failure != null || state.error != null) item { Text(failure ?: state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
    }
    crop?.let { source -> WapiSquareCropDialog(source, "Photo du joueur", { crop = null }) { uri -> photo = uri; mode = "photo"; crop = null } }
}

@Composable internal fun PoolEquipmentSheet(
    tableTheme: String, cueStyle: String, founder: Boolean,
    onTableTheme: (String) -> Unit, onCueStyle: (String) -> Unit, onDismiss: () -> Unit,
) {
    // AlertDialog was clipped on smaller Android screens.  It hid part of the
    // cue collection, which made the feature feel absent.  Equipment is now a
    // dedicated game surface that remains usable in portrait and landscape.
    val collection = rememberPoolCueCollection(founder)
    var purchaseFeedback by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.91f),
            color = Color(0xFF071A2B),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF318DC6)),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ATELIER WAPI POOL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = .5.sp)
                        Text(if (founder) "Accès administrateur · collection complète offerte" else "Choisis une queue et un tapis avant de jouer.", color = Color(0xFFA7C9DF), fontSize = 12.sp)
                    }
                    Surface(color = if (founder) Color(0xFF145D48) else Color(0xFF173F62), shape = RoundedCornerShape(12.dp)) {
                        Text(if (founder) "ADMIN · GRATUIT" else "${collection.tokens} JETONS", Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    TextButton(onClick = onDismiss) { Text("FERMER", color = Color(0xFF75D6FF), fontWeight = FontWeight.Bold) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("TAPIS DE TABLE", color = Color(0xFF70D8FF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("competitionBlue", "Bleu compétition", Color(0xFF0968B7)),
                            Triple("navy", "Bleu nuit", Color(0xFF07306E)),
                            Triple("emerald", "Vert tournoi", Color(0xFF08764F)),
                        ).forEach { (id, label, color) ->
                            val selected = tableTheme == id
                            Surface(
                                onClick = { onTableTheme(id) },
                                modifier = Modifier.weight(1f).height(76.dp),
                                color = color,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = .25f)),
                            ) {
                                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (selected) "ÉQUIPÉ" else "TAPIS", color = Color.White.copy(alpha = .9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 13.sp)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("COLLECTION DE QUEUES", color = Color(0xFF70D8FF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Text(if (founder) "${wapiPoolCues.size} déverrouillées" else "${wapiPoolCues.size} disponibles", color = Color(0xFF9BB7C9), fontSize = 10.sp)
                    }
                    wapiPoolCues.forEach { cue ->
                        val selected = cueStyle == cue.id
                        val owned = collection.has(cue)
                        Surface(
                            onClick = {
                                if (owned) {
                                    onCueStyle(cue.id)
                                    purchaseFeedback = null
                                } else if (collection.acquire(cue)) {
                                    onCueStyle(cue.id)
                                    purchaseFeedback = "${cue.name} ajoutée à votre collection."
                                } else purchaseFeedback = "Jetons insuffisants pour ${cue.name}."
                            },
                            color = if (selected) Color(0xFF0B4267) else if (owned) Color(0xFF0C263C) else Color(0xFF0A1A2A),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF72E6BD) else Color(0xFF254961)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cue.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                        Text(cue.tier.uppercase(), color = if (selected) Color(0xFF72E6BD) else Color(0xFF9FC1D5), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                                    }
                                    Surface(color = if (selected) Color(0xFF158765) else if (owned) Color(0xFF163D5C) else Color(0xFF127A21), shape = RoundedCornerShape(10.dp)) {
                                        Text(when {
                                            selected -> "ÉQUIPÉE"
                                            owned -> "ÉQUIPER"
                                            else -> "ACHETER ${cue.priceTokens}"
                                        }, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                PoolCuePreview(cue, selected)
                                PoolCueStats(cue, selected)
                            }
                        }
                    }
                    Text(
                        if (founder) "Compte administrateur vérifié : toutes les queues sont déverrouillées sans coût."
                        else "Les achats utilisent des jetons WAPI virtuels. Chaque queue améliore force, visée, effet et tempo.",
                        color = Color(0xFFB9D3E2), fontSize = 11.sp, lineHeight = 15.sp,
                    )
                    purchaseFeedback?.let { feedback ->
                        Text(feedback, color = if (feedback.startsWith("Jetons")) Color(0xFFFFA3A3) else Color(0xFF72E6BD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(18.dp).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0785C9)),
                ) { Text("JOUER AVEC ${wapiPoolCue(cueStyle).name.uppercase()}", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            }
        }
    }
}

/** Original cue visual and stat bars.  They describe real values consumed by
 * the shot engine; this intentionally never presents a fake price screen. */
@Composable private fun PoolCuePreview(cue: WapiPoolCue, selected: Boolean) {
    val shaft = when (cue.id) {
        "maple" -> Color(0xFFD5A566)
        "walnut" -> Color(0xFF77401F)
        "carbon" -> Color(0xFF394B5D)
        else -> Color(0xFF161A27)
    }
    val wrap = when (cue.id) {
        "carbon" -> Color(0xFF56C9FF)
        "obsidian" -> Color(0xFFFFD36B)
        else -> Color(0xFFE9EEF4)
    }
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val middle = size.height / 2f
        val inset = size.width * .055f
        drawRoundRect(Color.Black.copy(alpha = if (selected) .30f else .12f), topLeft = Offset(inset, middle + 3f), size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawRoundRect(shaft, topLeft = Offset(inset, middle - 2.2f), size = androidx.compose.ui.geometry.Size(size.width - inset * 2.2f, 4.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        drawRoundRect(wrap, topLeft = Offset(size.width * .38f, middle - 3.2f), size = androidx.compose.ui.geometry.Size(size.width * .17f, 6.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        drawCircle(Color(0xFFE8F6FF), radius = 3.4f, center = Offset(size.width - inset, middle))
        drawCircle(Color(0xFF203F58), radius = 2.15f, center = Offset(inset, middle))
    }
}

@Composable private fun PoolCueStats(cue: WapiPoolCue, selected: Boolean) {
    val tone = if (selected) Color(0xFF74E7B7) else Color(0xFF218BD5)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(
            "Force" to (((cue.powerMultiplier - .96f) * 80f).toInt().coerceIn(1, 10)),
            "Visée" to (4 + cue.aimBonus).coerceAtMost(10),
            "Effet" to (3 + cue.spinBonus).coerceAtMost(10),
            "Tempo" to (4 + cue.tempoBonus).coerceAtMost(10),
        ).forEach { (label, score) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(label, color = if (selected) Color(0xFFC9E5F6) else WhappyMuted, fontSize = 9.sp, modifier = Modifier.width(39.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    repeat(10) { slot ->
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(if (slot < score) tone else if (selected) Color.White.copy(alpha = .15f) else Color(0xFFDCE6ED)))
                    }
                }
            }
        }
    }
}

@Composable internal fun PoolVictoryOverlay(
    winner: Boolean, player: PoolPlayerCard, score: Int, localVictories: Int, localDefeats: Int, localCups: Int,
    cupCompleted: Boolean, onReplay: () -> Unit, modifier: Modifier = Modifier,
) {
    Surface(modifier.widthIn(max = 410.dp).padding(20.dp), color = Color(0xF9081D31), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, if (winner) Color(0xFFFFD36B) else Color(0xFF4E7B9B))) {
        Box {
            if (winner) Canvas(Modifier.matchParentSize()) {
                val colors = listOf(Color(0xFFFFD65C), Color(0xFF51D4FF), Color(0xFFFF6E88), Color(0xFF72E59A))
                repeat(34) { index ->
                    val x = (index * 67 % 100) / 100f * size.width
                    val y = ((index * 29) % 78) / 100f * size.height
                    drawCircle(colors[index % colors.size], radius = 4f + index % 4, center = Offset(x, y))
                }
            }
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PoolPlayerAvatar(player, 66)
                Text(if (winner) "Victoire" else "Défaite", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(player.name, color = Color(0xFFD9EAF6), fontSize = 15.sp)
                Text("$score points · $localVictories victoires · $localDefeats défaites", color = Color(0xFFB9D7E9), fontSize = 12.sp)
                if (winner) Text("+250 XP de partie", color = Color(0xFFFFD36B), fontWeight = FontWeight.Bold)
                if (cupCompleted) Text("Coupe IA remportée · $localCups coupe(s) locale(s)", color = Color(0xFFFFD36B), fontWeight = FontWeight.Bold)
                Text("Résultats locaux : aucun gain monétaire ni classement officiel n’est simulé.", color = Color(0xFF9BBACC), fontSize = 11.sp)
                Button(onClick = onReplay) { Text("Rejouer") }
            }
        }
    }
}
