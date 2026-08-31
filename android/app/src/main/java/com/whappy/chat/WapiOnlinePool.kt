package com.whappy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.cos
import kotlin.math.sin

private data class WapiPoolPlayer(val id: String, val profile: PoolPlayerCard)
private data class WapiPoolShot(
    val revision: Long,
    val shooterId: String,
    val angle: Float,
    val power: Int,
    val sideSpin: Float,
    val followSpin: Float,
)

private fun remotePoolBalls(raw: Any?): List<WapiPoolBall> {
    val values = raw as? List<*> ?: return emptyList()
    return values.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = (map["id"] as? Number)?.toInt() ?: return@mapNotNull null
        WapiPoolBall(
            id = id,
            x = (map["x"] as? Number)?.toFloat() ?: return@mapNotNull null,
            y = (map["y"] as? Number)?.toFloat() ?: return@mapNotNull null,
            pocketed = map["pocketed"] as? Boolean ?: false,
        )
    }.takeIf { it.size == 16 } ?: emptyList()
}

/**
 * Server-authoritative two-player pool table. Callable Functions validate and
 * simulate every shot; devices replay the accepted command at 60 Hz, then
 * reconcile with the immutable official board returned by WAPI.
 */
@Composable
fun WapiOnlinePoolArena(userId: String, userName: String) {
    val context = LocalContext.current
    val profile = rememberPoolProfile()
    var editingPlayer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    var roomCode by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var players by remember { mutableStateOf(emptyList<WapiPoolPlayer>()) }
    var roomStatus by remember { mutableStateOf("") }
    var turnUid by remember { mutableStateOf("") }
    var balls by remember { mutableStateOf(initialPoolBalls()) }
    var aimAngle by rememberSaveable { mutableFloatStateOf(0f) }
    var power by rememberSaveable { mutableIntStateOf(55) }
    var sideSpin by rememberSaveable { mutableFloatStateOf(0f) }
    var followSpin by rememberSaveable { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var physicsRunning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Créez une table ou entrez le code d’un joueur WAPI.") }
    var pendingShot by remember { mutableStateOf<WapiPoolShot?>(null) }
    var observedShotRevision by remember { mutableLongStateOf(0L) }
    var boardRevision by remember { mutableLongStateOf(-1L) }
    var lastCollisionSoundAt by remember { mutableLongStateOf(0L) }
    var lastRailSoundAt by remember { mutableLongStateOf(0L) }
    var groups by remember { mutableStateOf<Map<String, WapiPoolGroup>>(emptyMap()) }
    var winnerUid by remember { mutableStateOf("") }
    var ballInHandUid by remember { mutableStateOf("") }
    var turnDeadlineMs by remember { mutableLongStateOf(0L) }
    var secondsLeft by remember { mutableIntStateOf(45) }
    var handledDeadline by remember { mutableLongStateOf(0L) }
    var authoritativeBalls by remember { mutableStateOf(initialPoolBalls()) }
    var authoritativeRevision by remember { mutableLongStateOf(0L) }
    var reconnectChecked by remember { mutableStateOf(false) }

    DisposableEffect(roomCode) {
        if (roomCode.isBlank()) return@DisposableEffect onDispose { }
        val registration = firestore.collection("gameRooms").document(roomCode).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                message = "Table introuvable ou connexion interrompue."
                return@addSnapshotListener
            }
            val ids = (snapshot.get("playerIds") as? List<*>)?.map { it.toString() }.orEmpty()
            val names = (snapshot.get("playerNames") as? List<*>)?.map { it.toString() }.orEmpty()
            val profiles = snapshot.get("playerProfiles") as? Map<*, *>
            players = ids.mapIndexed { index, id -> WapiPoolPlayer(id, poolPlayerCard(profiles?.get(id) as? Map<*, *> ?: emptyMap<String, Any>(), names.getOrNull(index) ?: "Joueur ${index + 1}")) }
            roomStatus = snapshot.getString("status").orEmpty()
            turnUid = snapshot.getString("turnUid").orEmpty()
            winnerUid = snapshot.getString("winnerUid").orEmpty()
            ballInHandUid = snapshot.getString("ballInHandUid").orEmpty()
            turnDeadlineMs = snapshot.getLong("turnDeadlineMs") ?: 0L
            groups = (snapshot.get("groups") as? Map<*, *>)?.mapNotNull { (key, value) ->
                val uid = key?.toString().orEmpty()
                val group = runCatching { WapiPoolGroup.valueOf(value?.toString().orEmpty().uppercase()) }.getOrNull()
                if (uid.isBlank() || group == null) null else uid to group
            }?.toMap().orEmpty()
            message = snapshot.getString("lastAction") ?: message
            val remoteBoardRevision = snapshot.getLong("ballStateRevision") ?: 0L
            remotePoolBalls(snapshot.get("balls")).takeIf { it.isNotEmpty() }?.let {
                authoritativeBalls = it
                authoritativeRevision = remoteBoardRevision
            }
            val shotRevision = snapshot.getLong("shotRevision") ?: 0L
            if (shotRevision > observedShotRevision) {
                observedShotRevision = shotRevision
                remotePoolBalls(snapshot.get("shotStartBalls")).takeIf { it.isNotEmpty() }?.let { balls = it }
                pendingShot = WapiPoolShot(
                    revision = shotRevision,
                    shooterId = snapshot.getString("shotBy").orEmpty(),
                    angle = (snapshot.getDouble("shotAngle") ?: 0.0).toFloat(),
                    power = (snapshot.getLong("shotPower") ?: 45L).toInt().coerceIn(10, 100),
                    sideSpin = (snapshot.getDouble("shotSideSpin") ?: 0.0).toFloat().coerceIn(-1f, 1f),
                    followSpin = (snapshot.getDouble("shotFollowSpin") ?: 0.0).toFloat().coerceIn(-1f, 1f),
                )
            } else if (remoteBoardRevision > boardRevision && !physicsRunning) {
                balls = authoritativeBalls
                boardRevision = remoteBoardRevision
            }
        }
        onDispose { registration.remove() }
    }

    LaunchedEffect(userId) {
        if (userId.isBlank() || reconnectChecked) return@LaunchedEffect
        reconnectChecked = true
        runCatching {
            val data = functions.getHttpsCallable("reconnectPoolMatch").call().await().data as? Map<*, *>
            if (data?.get("found") == true) {
                roomCode = data["roomId"]?.toString().orEmpty()
                message = "Partie WAPI récupérée automatiquement."
            }
        }
    }

    LaunchedEffect(roomStatus, winnerUid) {
        if (roomStatus == "finished" && winnerUid.isNotBlank()) profile.load()
    }

    LaunchedEffect(roomCode, roomStatus, turnUid, turnDeadlineMs) {
        while (roomCode.isNotBlank() && roomStatus == "playing" && winnerUid.isBlank()) {
            secondsLeft = ((turnDeadlineMs - System.currentTimeMillis() + 999L) / 1_000L).toInt().coerceIn(0, 45)
            if (secondsLeft == 0 && turnUid == userId && turnDeadlineMs > 0L && handledDeadline != turnDeadlineMs) {
                handledDeadline = turnDeadlineMs
                runCatching { functions.getHttpsCallable("expirePoolTurn").call(mapOf("roomId" to roomCode)).await() }
            }
            delay(250L)
        }
    }

    fun createRoom() {
        if (userId.isBlank() || busy) return
        scope.launch {
            busy = true
            runCatching {
                val data = functions.getHttpsCallable("createPoolMatch").call(mapOf("visibility" to "private")).await().data as? Map<*, *>
                    ?: error("invalid-server-response")
                roomCode = data["roomId"]?.toString().orEmpty()
                require(roomCode.length == 6)
            }.onFailure { message = "Impossible de créer la table. Vérifiez la connexion." }
            busy = false
        }
    }

    fun joinRoom() {
        val code = joinCode.trim().uppercase()
        if (code.length != 6 || userId.isBlank() || busy) return
        scope.launch {
            busy = true
            runCatching {
                functions.getHttpsCallable("joinPoolMatch").call(mapOf("roomId" to code)).await()
                roomCode = code
            }.onFailure { message = "Code invalide, table déjà complète ou indisponible." }
            busy = false
        }
    }

    fun findMatch() {
        if (userId.isBlank() || busy) return
        scope.launch {
            busy = true
            message = "Recherche d’un joueur WAPI disponible…"
            runCatching {
                val data = functions.getHttpsCallable("findPoolMatch").call().await().data as? Map<*, *>
                    ?: error("invalid-server-response")
                roomCode = data["roomId"]?.toString().orEmpty()
                require(roomCode.length == 6)
            }.onFailure { message = "Le matchmaking n’a pas abouti. Relancez la recherche." }
            busy = false
        }
    }

    fun commitCuePlacement() {
        if (roomCode.isBlank() || ballInHandUid != userId || physicsRunning || busy) return
        val cue = balls.firstOrNull { it.id == 0 } ?: return
        if (!isValidWapiCuePlacement(cue.x, cue.y, balls)) {
            message = "Placement impossible : éloignez la blanche d’une bille ou d’une poche."
            return
        }
        scope.launch {
            busy = true
            runCatching {
                functions.getHttpsCallable("placePoolCueBall").call(mapOf("roomId" to roomCode, "x" to cue.x, "y" to cue.y)).await()
                message = "Placement validé · visez puis dosez le tir à droite."
                WhappySounds.pieceSelected(context)
            }.onFailure { message = "Le placement n’a pas été synchronisé. Réessayez." }
            busy = false
        }
    }

    fun broadcastShot() {
        if (roomCode.isBlank() || roomStatus != "playing" || turnUid != userId || ballInHandUid == userId || physicsRunning || busy || winnerUid.isNotBlank()) return
        scope.launch {
            busy = true
            runCatching {
                functions.getHttpsCallable("submitPoolShot").call(
                    mapOf(
                        "roomId" to roomCode,
                        "angle" to aimAngle.toDouble(),
                        "power" to power,
                        "sideSpin" to sideSpin.toDouble(),
                        "followSpin" to followSpin.toDouble(),
                    ),
                ).await()
            }.onFailure { message = "Le tir n’a pas été envoyé. Réessayez." }
            busy = false
        }
    }

    LaunchedEffect(pendingShot?.revision) {
        val shot = pendingShot ?: return@LaunchedEffect
        if (physicsRunning || shot.shooterId.isBlank()) return@LaunchedEffect
        physicsRunning = true
        aimAngle = shot.angle
        power = shot.power
        val speed = 3.4f + shot.power / 100f * 7.4f
        balls = balls.map { ball ->
            if (ball.id == 0) ball.copy(
                vx = cos(shot.angle) * speed / WAPI_POOL_WORLD_WIDTH,
                vy = sin(shot.angle) * speed / WAPI_POOL_WORLD_HEIGHT,
                sideSpin = shot.sideSpin,
                followSpin = shot.followSpin,
            ) else ball
        }
        WhappySounds.billiardCue(context, power)
        while (physicsRunning) {
            val frame = advanceWapiPoolFrame(balls)
            balls = frame.balls
            val now = android.os.SystemClock.elapsedRealtime()
            if (frame.collisionEnergy > .28f && now - lastCollisionSoundAt > 72L) {
                lastCollisionSoundAt = now
                WhappySounds.billiardCollision(context, (.20f + frame.collisionEnergy * .07f).coerceAtMost(.66f))
            }
            if (frame.railEnergy > .025f && now - lastRailSoundAt > 95L) {
                lastRailSoundAt = now
                WhappySounds.billiardRail(context, (.18f + frame.railEnergy * 1.8f).coerceAtMost(.55f))
            }
            if (frame.newlyPocketed > 0) WhappySounds.billiardPocket(context)
            if (!wapiPoolBallsMoving(frame.balls)) {
                balls = frame.balls.map { if (it.pocketed) it else it.copy(vx = 0f, vy = 0f) }
                physicsRunning = false
            } else delay(16L)
        }
        // Reconcile with the server result after the local presentation has
        // finished. The phone animates; it never decides the official board.
        if (authoritativeRevision >= shot.revision) {
            balls = authoritativeBalls
            boardRevision = authoritativeRevision
        }
        pendingShot = null
    }

    if (roomCode.isBlank()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0B5A49), Color(0xFF061B17), Color(0xFF010806)))), contentAlignment = Alignment.Center) {
            Column(Modifier.width(590.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BILLARD EN LIGNE", color = Color(0xFF72F2C8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                OutlinedButton(onClick = { editingPlayer = true }) { Text("Mon profil joueur · ${profile.player.name}", color = Color.White) }
                Text("Une table. Deux joueurs WAPI.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Le code, les tours, la position des 16 billes et chaque tir sont synchronisés. Aucun adversaire fictif.", color = Color.White.copy(alpha = .70f), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::createRoom, enabled = !busy && userId.isNotBlank(), modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A9C78))) { Text("CRÉER UNE TABLE", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = ::joinRoom, enabled = !busy && joinCode.length == 6, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("REJOINDRE", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Button(onClick = ::findMatch, enabled = !busy && userId.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1478FF))) {
                    Text("TROUVER UN JOUEUR WAPI", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(joinCode, { joinCode = it.filter(Char::isLetterOrDigit).take(6).uppercase() }, Modifier.fillMaxWidth(), label = { Text("Code de table") }, placeholder = { Text("ABC123") }, singleLine = true)
                if (busy) CircularProgressIndicator(color = Color(0xFF72F2C8), modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("ARBITRAGE WAPI SERVEUR · ANTI-TRICHE", color = Color(0xFF72F2C8), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                Text(message, color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF062862), Color(0xFF020816))))) {
            WapiPoolTabletop3D(
                balls = balls,
                aimAngle = aimAngle,
                power = power,
                sideSpin = sideSpin,
                followSpin = followSpin,
                moving = physicsRunning,
                cueInHand = ballInHandUid == userId && !physicsRunning,
                modifier = Modifier.padding(top = 66.dp, bottom = 40.dp),
                onAim = { x, y ->
                    if (busy || winnerUid.isNotBlank()) return@WapiPoolTabletop3D
                    if (turnUid == userId && !physicsRunning && ballInHandUid == userId) {
                        val candidateX = x.coerceIn(.085f, .445f)
                        val candidateY = y.coerceIn(.115f, .885f)
                        if (isValidWapiCuePlacement(candidateX, candidateY, balls)) {
                            balls = balls.map { if (it.id == 0) it.copy(x = candidateX, y = candidateY, vx = 0f, vy = 0f) else it }
                            message = "Blanche en main · touchez Poser la blanche pour confirmer."
                        } else message = "Placement interdit : bille ou poche trop proche."
                    } else if (turnUid == userId && !physicsRunning) balls.firstOrNull { it.id == 0 }?.let { cue ->
                        val dx = x - cue.x; val dy = y - cue.y
                        aimAngle = WapiPoolPresentation.aimAngle(dx, dy)
                        message = "Visée réglée · tirez puis relâchez la jauge latérale."
                    }
                },
                onRelease = { },
            )
            val opponent = players.firstOrNull { it.id != userId }
            PoolAimWheel(aimAngle, roomStatus == "playing" && turnUid == userId && ballInHandUid != userId && !physicsRunning && !busy && winnerUid.isBlank(),
                { aimAngle = it }, Modifier.align(Alignment.CenterStart).padding(start = 20.dp, top = 44.dp))
            PoolMatchScoreboard(
                left = players.firstOrNull { it.id == userId }?.profile ?: profile.player,
                right = opponent?.profile ?: PoolPlayerCard("En attente", icon = "cue"),
                leftGroup = groups[userId] ?: WapiPoolGroup.OPEN,
                rightGroup = groups[opponent?.id] ?: WapiPoolGroup.OPEN,
                balls = balls, leftActive = turnUid == userId && winnerUid.isBlank(),
                rightActive = opponent != null && turnUid == opponent.id && winnerUid.isBlank(),
                leftSeconds = if (turnUid == userId) secondsLeft else 30,
                rightSeconds = if (opponent != null && turnUid == opponent.id) secondsLeft else 30,
                status = when { winnerUid.isNotBlank() -> if (winnerUid == userId) "Victoire" else "Défaite"
                    roomStatus != "playing" -> "Code $roomCode"
                    physicsRunning -> "Tir en cours"
                    else -> "${secondsLeft}s · " + if (turnUid == userId) "À vous" else "Adversaire" },
                onEditProfile = { editingPlayer = true },
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (ballInHandUid == userId && !physicsRunning && winnerUid.isBlank()) {
                val cue = balls.first { it.id == 0 }
                PoolPlacementControl(!busy && turnUid == userId, isValidWapiCuePlacement(cue.x, cue.y, balls), ::commitCuePlacement,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            } else if (roomStatus != "playing" || message.contains("pas été") || message.contains("interrompue")) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(horizontal = 100.dp, vertical = 8.dp), color = Color(0xE6071C31), shape = RoundedCornerShape(14.dp)) {
                    Text(message, Modifier.padding(12.dp), color = Color.White, fontSize = 12.sp)
                }
            }
            WapiPoolSpinPad(
                sideSpin = sideSpin,
                followSpin = followSpin,
                enabled = roomStatus == "playing" && turnUid == userId && ballInHandUid != userId && !physicsRunning && !busy && winnerUid.isBlank(),
                onSpinChanged = { side, follow -> sideSpin = side; followSpin = follow },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
            WapiPoolPowerRail(
                power = power,
                enabled = roomStatus == "playing" && turnUid == userId && ballInHandUid != userId && !physicsRunning && !busy && winnerUid.isBlank(),
                onPowerChange = { power = it },
                onStrike = ::broadcastShot,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 48.dp),
            )
        }
    }
    if (editingPlayer) PoolPlayerEditor(profile) { editingPlayer = false }
}
