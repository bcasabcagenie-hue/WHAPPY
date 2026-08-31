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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal val poolPlayerIcons = linkedMapOf("cue" to "🎱", "crown" to "👑", "fox" to "🦊", "lion" to "🦁", "robot" to "🤖")
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
    Row(modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PoolPlayerScore(left, leftGroup, balls, leftActive, leftSeconds, Modifier.weight(1f), onEditProfile)
        Text(status, Modifier.widthIn(max = 112.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        PoolPlayerScore(right, rightGroup, balls, rightActive, rightSeconds, Modifier.weight(1f), null)
    }
}

@Composable private fun PoolPlayerScore(player: PoolPlayerCard, group: WapiPoolGroup, balls: List<WapiPoolBall>, active: Boolean, seconds: Int, modifier: Modifier, onClick: (() -> Unit)?) {
    val ids = poolRemainingIds(group, balls)
    Surface(modifier.then(if (onClick != null) Modifier.wapiClickable(onClick = onClick) else Modifier),
        color = Color(0xED071C31), shape = RoundedCornerShape(14.dp), border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Color(0xFF51C3FF) else Color(0xFF24425C))) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PoolPlayerAvatar(player, 34)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(player.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (group == WapiPoolGroup.OPEN) "Groupe à attribuer" else "${ids.size} restante${if (ids.size != 1) "s" else ""} · ${if (group == WapiPoolGroup.SOLIDS) "pleines" else "rayées"}", color = Color(0xFFB8D5E9), fontSize = 10.sp)
                if (active) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LinearProgressIndicator(progress = { seconds.coerceIn(0, 30) / 30f }, modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF47E390), trackColor = Color.White.copy(alpha = .14f))
                    Text("${seconds.coerceAtLeast(0)} s", color = Color(0xFF8FF1BA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (group != WapiPoolGroup.OPEN) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (if (ids.isEmpty()) listOf(8) else ids).forEach { id ->
                        PoolScoreBall(id)
                    }
                }
            }
        }
    }
}

/** Miniature of the same numbered balls used on the physical table. Keeping
 * the stripe, number plate and specular highlight in the score makes the
 * players' remaining balls readable at a glance instead of looking like dots. */
@Composable private fun PoolScoreBall(id: Int) {
    val base = when ((id - 1).mod(8)) {
        0 -> Color(0xFFF7C818); 1 -> Color(0xFF2468D7); 2 -> Color(0xFFD62D32); 3 -> Color(0xFF7940AE)
        4 -> Color(0xFFE97920); 5 -> Color(0xFF1E9B59); 6 -> Color(0xFF8B202B); else -> Color(0xFF11141C)
    }
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
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
    tableTheme: String, cueStyle: String, onTableTheme: (String) -> Unit, onCueStyle: (String) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Table & queue") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Le choix s’applique immédiatement à votre table locale.", color = WhappyMuted, fontSize = 13.sp)
            Text("Tapis", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("competitionBlue" to "Bleu compétition", "navy" to "Bleu nuit", "emerald" to "Vert tournoi").forEach { (id, label) ->
                    FilterChip(selected = tableTheme == id, onClick = { onTableTheme(id) }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            Text("Queue", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("maple" to "Érable", "walnut" to "Noyer", "carbon" to "Carbone").forEach { (id, label) ->
                    FilterChip(selected = cueStyle == id, onClick = { onCueStyle(id) }, label = { Text(label, fontSize = 11.sp) })
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
    )
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
