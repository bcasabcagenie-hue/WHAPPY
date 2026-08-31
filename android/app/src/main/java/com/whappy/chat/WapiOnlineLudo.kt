package com.whappy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

private data class WapiRoomPlayer(val id: String, val name: String)

@Composable
fun WapiOnlineLudoCard(userId: String, userName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    var roomCode by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var players by remember { mutableStateOf(emptyList<WapiRoomPlayer>()) }
    var positions by remember { mutableStateOf(List(16) { -1 }) }
    var turnUid by remember { mutableStateOf("") }
    var roomStatus by remember { mutableStateOf("") }
    var dieOne by remember { mutableIntStateOf(0) }
    var dieTwo by remember { mutableIntStateOf(0) }
    var pendingRoll by remember { mutableIntStateOf(0) }
    var pendingMayLeave by remember { mutableStateOf(false) }
    var pendingExtraTurn by remember { mutableStateOf(false) }
    var pendingPlayerId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Créez une salle ou entrez le code d’un ami.") }

    DisposableEffect(roomCode) {
        if (roomCode.isBlank()) return@DisposableEffect onDispose { }
        val registration = firestore.collection("gameRooms").document(roomCode).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                message = "Salle introuvable ou connexion interrompue."
                return@addSnapshotListener
            }
            val ids = snapshot.get("playerIds") as? List<*> ?: emptyList<Any>()
            val names = snapshot.get("playerNames") as? List<*> ?: emptyList<Any>()
            players = ids.mapIndexed { index, id -> WapiRoomPlayer(id.toString(), names.getOrNull(index)?.toString() ?: "Joueur ${index + 1}") }
            val storedPositions = (snapshot.get("positions") as? List<*>)?.map { (it as? Number)?.toInt() ?: -1 }.orEmpty()
            positions = when (storedPositions.size) {
                16 -> storedPositions
                4 -> storedPositions.flatMap { progress -> listOf(progress, -1, -1, -1) }
                else -> List(16) { -1 }
            }
            turnUid = snapshot.getString("turnUid").orEmpty()
            roomStatus = snapshot.getString("status").orEmpty()
            val legacyDie = snapshot.getLong("lastDice")?.toInt() ?: 0
            dieOne = snapshot.getLong("lastDieOne")?.toInt() ?: legacyDie
            dieTwo = snapshot.getLong("lastDieTwo")?.toInt() ?: 0
            pendingRoll = snapshot.getLong("pendingRoll")?.toInt() ?: 0
            pendingMayLeave = snapshot.getBoolean("pendingMayLeave") ?: false
            pendingExtraTurn = snapshot.getBoolean("pendingExtraTurn") ?: false
            pendingPlayerId = snapshot.getString("pendingPlayerId").orEmpty()
            message = snapshot.getString("lastAction") ?: if (roomStatus == "waiting") "En attente d’un autre joueur…" else "À ${players.firstOrNull { it.id == turnUid }?.name ?: "un joueur"} de jouer."
        }
        onDispose { registration.remove() }
    }

    fun createRoom() {
        if (userId.isBlank() || busy) return
        scope.launch {
            busy = true
            runCatching {
                val code = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                firestore.collection("gameRooms").document(code).set(
                    mapOf(
                        "game" to "ludo",
                        "status" to "waiting",
                        "hostId" to userId,
                        "playerIds" to listOf(userId),
                        "playerNames" to listOf(userName.take(60).ifBlank { "Joueur WAPI" }),
                        "positions" to List(16) { -1 },
                        "turnUid" to userId,
                        "lastDice" to 0,
                        "lastDieOne" to 0,
                        "lastDieTwo" to 0,
                        "pendingRoll" to 0,
                        "pendingMayLeave" to false,
                        "pendingExtraTurn" to false,
                        "pendingPlayerId" to "",
                        "lastAction" to "Salle créée · partagez le code $code",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                roomCode = code
            }.onFailure { message = "Impossible de créer la salle. Vérifiez la connexion." }
            busy = false
        }
    }

    fun joinRoom() {
        val code = joinCode.trim().uppercase()
        if (code.length != 6 || userId.isBlank() || busy) return
        scope.launch {
            busy = true
            runCatching {
                val reference = firestore.collection("gameRooms").document(code)
                val snapshot = reference.get().await()
                require(snapshot.exists() && snapshot.getString("game") == "ludo")
                val ids = (snapshot.get("playerIds") as? List<*>)?.map { it.toString() }.orEmpty()
                val names = (snapshot.get("playerNames") as? List<*>)?.map { it.toString() }.orEmpty().toMutableList()
                require(userId !in ids && ids.size < 4)
                reference.update(
                    mapOf(
                        "playerIds" to ids + userId,
                        "playerNames" to names.apply { add(userName.take(60).ifBlank { "Joueur WAPI" }) },
                        "status" to "playing",
                        "lastAction" to "${userName.take(40)} a rejoint la salle.",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                roomCode = code
            }.onFailure { message = "Code invalide, salle pleine ou déjà rejoint." }
            busy = false
        }
    }

    fun roll() {
        if (roomStatus != "playing" || turnUid != userId || busy || pendingRoll > 0) return
        val index = players.indexOfFirst { it.id == userId }
        if (index < 0) return
        scope.launch {
            busy = true
            val firstDie = Random.nextInt(1, 7)
            val secondDie = Random.nextInt(1, 7)
            val roll = WapiGameRules.ludoDiceRoll(firstDie, secondDie)
            val legalPawns = (index * 4 until index * 4 + 4).filter { pawnIndex ->
                val progress = positions[pawnIndex]
                (progress < 0 && roll.mayLeaveHome) || (progress in 0..56 && progress + roll.total <= 57)
            }
            val nextPlayer = players[(index + 1) % players.size]
            runCatching {
                firestore.collection("gameRooms").document(roomCode).update(
                    mapOf(
                        "turnUid" to if (legalPawns.isEmpty() && !roll.grantsExtraTurn) nextPlayer.id else userId,
                        // Keep the total for older clients while new clients render
                        // the two physical dice independently.
                        "lastDice" to roll.total,
                        "lastDieOne" to firstDie,
                        "lastDieTwo" to secondDie,
                        "pendingRoll" to if (legalPawns.isEmpty()) 0 else roll.total,
                        "pendingMayLeave" to if (legalPawns.isEmpty()) false else roll.mayLeaveHome,
                        "pendingExtraTurn" to if (legalPawns.isEmpty()) false else roll.grantsExtraTurn,
                        "pendingPlayerId" to if (legalPawns.isEmpty()) "" else userId,
                        "lastAction" to if (legalPawns.isEmpty()) {
                            "${players[index].name} obtient ${roll.total}, sans déplacement possible."
                        } else {
                            "${players[index].name} obtient $firstDie + $secondDie = ${roll.total} · choisissez un pion."
                        },
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                WhappySounds.dice()
            }.onFailure { message = "Le coup n’a pas été synchronisé." }
            busy = false
        }
    }

    fun movePawn(pawnIndex: Int) {
        val playerIndex = players.indexOfFirst { it.id == userId }
        if (busy || pendingPlayerId != userId || pendingRoll <= 0 || pawnIndex !in playerIndex * 4 until playerIndex * 4 + 4) return
        val progress = positions.getOrElse(pawnIndex) { -1 }
        if (!((progress < 0 && pendingMayLeave) || (progress in 0..56 && progress + pendingRoll <= 57))) return
        scope.launch {
            busy = true
            val starts = listOf(0, 13, 26, 39)
            val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)
            val next = positions.toMutableList()
            val target = if (progress < 0) 0 else progress + pendingRoll
            next[pawnIndex] = target
            var captured = false
            if (target in 0..51) {
                val absolute = (starts[playerIndex] + target) % 52
                if (absolute !in safeSquares) {
                    next.indices.forEach { opponentPawn ->
                        val opponent = opponentPawn / 4
                        val opponentProgress = next[opponentPawn]
                        if (opponent != playerIndex && opponentProgress in 0..51 && (starts[opponent] + opponentProgress) % 52 == absolute) {
                            next[opponentPawn] = -1
                            captured = true
                        }
                    }
                }
            }
            val nextPlayer = players[(playerIndex + 1) % players.size]
            val extraTurn = pendingExtraTurn || captured
            runCatching {
                firestore.collection("gameRooms").document(roomCode).update(
                    mapOf(
                        "positions" to next,
                        "turnUid" to if (extraTurn) userId else nextPlayer.id,
                        "pendingRoll" to 0,
                        "pendingMayLeave" to false,
                        "pendingExtraTurn" to false,
                        "pendingPlayerId" to "",
                        "lastAction" to "${players[playerIndex].name} déplace le pion ${(pawnIndex % 4) + 1}${if (captured) " et réalise une capture" else ""}.",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                if (captured) WhappySounds.capture(context) else WhappySounds.move(context)
            }.onFailure { message = "Le déplacement n’a pas été synchronisé." }
            busy = false
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MULTIJOUEUR EN LIGNE", color = WhappyBlue, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            Text("Ludo avec de vrais joueurs", color = WhappyDark, fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            Text("Le code de salle, les tours et les positions sont synchronisés en temps réel. Aucun adversaire fictif.", color = WhappyMuted, fontSize = 11.sp, lineHeight = 16.sp)
            if (roomCode.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::createRoom, enabled = !busy && userId.isNotBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("CRÉER") }
                    OutlinedButton(onClick = ::joinRoom, enabled = !busy && joinCode.length == 6, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("REJOINDRE") }
                }
                OutlinedTextField(value = joinCode, onValueChange = { joinCode = it.filter(Char::isLetterOrDigit).take(6).uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Code de salle") }, placeholder = { Text("ABC123") }, singleLine = true)
            } else {
                val playerIndex = players.indexOfFirst { it.id == userId }
                val legalPawns = if (pendingPlayerId == userId && pendingRoll > 0 && playerIndex >= 0) {
                    (playerIndex * 4 until playerIndex * 4 + 4).filter { pawnIndex ->
                        val progress = positions.getOrElse(pawnIndex) { -1 }
                        (progress < 0 && pendingMayLeave) || (progress in 0..56 && progress + pendingRoll <= 57)
                    }
                } else emptyList()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("SALLE $roomCode", color = WhappyDark, fontWeight = androidx.compose.ui.text.font.FontWeight.Black); Text("${players.size}/4 joueurs · ${if (roomStatus == "waiting") "En attente" else "Partie active"}", color = WhappyMuted, fontSize = 11.sp) }
                    Button(onClick = ::roll, enabled = roomStatus == "playing" && turnUid == userId && pendingRoll == 0 && !busy, colors = ButtonDefaults.buttonColors(containerColor = WhappyBlue), modifier = Modifier.size(124.dp, 46.dp), shape = RoundedCornerShape(13.dp)) {
                        Text(if (dieOne == 0 || dieTwo == 0) "2 DÉS" else "$dieOne  +  $dieTwo")
                    }
                }
                AndroidView(
                    factory = { viewContext -> WapiTabletop3DView(viewContext) },
                    update = { view ->
                        view.onLudoPawnTapped = ::movePawn
                        view.setLudoScene(
                            positions = positions,
                            activePlayer = players.indexOfFirst { it.id == turnUid }.coerceAtLeast(0),
                            dieOne = dieOne.coerceAtLeast(1),
                            dieTwo = dieTwo.coerceAtLeast(1),
                            rolling = busy && pendingRoll == 0,
                            selectablePawns = legalPawns.toSet(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(310.dp).background(Color(0xFF08111D)),
                )
                if (pendingPlayerId == userId && pendingRoll > 0) {
                    Text("CHOISISSEZ VOTRE PION", color = WhappyBlue, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        legalPawns.forEach { pawnIndex ->
                            OutlinedButton(onClick = { movePawn(pawnIndex) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                Text("PION ${(pawnIndex % 4) + 1}", fontSize = 9.sp)
                            }
                        }
                    }
                }
                Text(players.joinToString(" · ") { it.name }, color = WhappyDark, fontSize = 11.sp)
                Text(message, color = WhappyMuted, fontSize = 11.sp)
                OutlinedButton(onClick = { roomCode = ""; players = emptyList(); positions = List(16) { -1 }; roomStatus = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("QUITTER LA SALLE") }
            }
        }
    }
}
