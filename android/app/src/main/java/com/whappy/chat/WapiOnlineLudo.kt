package com.whappy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    var roomCode by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var players by remember { mutableStateOf(emptyList<WapiRoomPlayer>()) }
    var positions by remember { mutableStateOf(listOf(-1, -1, -1, -1)) }
    var turnUid by remember { mutableStateOf("") }
    var roomStatus by remember { mutableStateOf("") }
    var dice by remember { mutableStateOf(0) }
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
            positions = (snapshot.get("positions") as? List<*>)?.map { (it as? Number)?.toInt() ?: -1 }?.let { it + List((4 - it.size).coerceAtLeast(0)) { -1 } }?.take(4) ?: listOf(-1, -1, -1, -1)
            turnUid = snapshot.getString("turnUid").orEmpty()
            roomStatus = snapshot.getString("status").orEmpty()
            dice = snapshot.getLong("lastDice")?.toInt() ?: 0
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
                        "positions" to listOf(-1, -1, -1, -1),
                        "turnUid" to userId,
                        "lastDice" to 0,
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
        if (roomStatus != "playing" || turnUid != userId || busy) return
        val index = players.indexOfFirst { it.id == userId }
        if (index < 0) return
        scope.launch {
            busy = true
            val roll = Random.nextInt(1, 7)
            val next = positions.toMutableList()
            next[index] = if (next[index] < 0) if (roll == 6) 0 else -1 else (next[index] + roll).coerceAtMost(52)
            val nextPlayer = players[(index + 1) % players.size]
            runCatching {
                firestore.collection("gameRooms").document(roomCode).update(
                    mapOf(
                        "positions" to next,
                        "turnUid" to if (roll == 6) userId else nextPlayer.id,
                        "lastDice" to roll,
                        "lastAction" to "${players[index].name} a obtenu $roll.",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                WhappySounds.dice()
            }.onFailure { message = "Le coup n’a pas été synchronisé." }
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("SALLE $roomCode", color = WhappyDark, fontWeight = androidx.compose.ui.text.font.FontWeight.Black); Text("${players.size}/4 joueurs · ${if (roomStatus == "waiting") "En attente" else "Partie active"}", color = WhappyMuted, fontSize = 11.sp) }
                    Button(onClick = ::roll, enabled = roomStatus == "playing" && turnUid == userId && !busy, colors = ButtonDefaults.buttonColors(containerColor = WhappyBlue), modifier = Modifier.size(104.dp, 46.dp), shape = RoundedCornerShape(13.dp)) { Text(if (dice == 0) "DÉ" else "DÉ $dice") }
                }
                Text(players.joinToString(" · ") { it.name }, color = WhappyDark, fontSize = 11.sp)
                Text(message, color = WhappyMuted, fontSize = 11.sp)
                OutlinedButton(onClick = { roomCode = ""; players = emptyList(); positions = listOf(-1, -1, -1, -1); roomStatus = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("QUITTER LA SALLE") }
            }
        }
    }
}
