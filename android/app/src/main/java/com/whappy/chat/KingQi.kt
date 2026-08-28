package com.whappy.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

private val KingNavy = Color(0xFF071B3E)
private val KingGold = Color(0xFFFFC83D)
private val KingGreen = Color(0xFF18B981)
private val KingRed = Color(0xFFE84B5B)

private data class KingQiQuestion(
    val category: String,
    val difficulty: String,
    val prompt: String,
    val options: List<String>,
    val answer: Int,
)

private val kingQiSoloQuestions = listOf(
    KingQiQuestion("GÉOGRAPHIE", "FACILE", "Quelle est la capitale du Japon ?", listOf("Séoul", "Tokyo", "Pékin", "Bangkok"), 1),
    KingQiQuestion("SCIENCES", "FACILE", "Quelle est la formule chimique de l'eau ?", listOf("CO2", "O2", "H2O", "NaCl"), 2),
    KingQiQuestion("LOGIQUE", "FACILE", "Quel est le premier nombre premier ?", listOf("0", "1", "2", "3"), 2),
    KingQiQuestion("GÉOGRAPHIE", "MOYEN", "Quel est le plus grand océan du monde ?", listOf("Atlantique", "Indien", "Arctique", "Pacifique"), 3),
    KingQiQuestion("SCIENCES", "MOYEN", "Quel processus permet aux plantes de transformer la lumière en énergie ?", listOf("Respiration", "Photosynthèse", "Fermentation", "Osmose"), 1),
    KingQiQuestion("CULTURE", "MOYEN", "Combien de continents compte le modèle géographique le plus courant ?", listOf("Cinq", "Six", "Sept", "Huit"), 2),
    KingQiQuestion("TECHNOLOGIE", "EXPERT", "Que signifie l'acronyme GPS ?", listOf("Global Positioning System", "General Public Signal", "Geo Personal Service", "Global Phone Sync"), 0),
    KingQiQuestion("HISTOIRE", "EXPERT", "Dans quelle civilisation les pyramides de Gizeh ont-elles été construites ?", listOf("Romaine", "Maya", "Égyptienne", "Perse"), 2),
    KingQiQuestion("SCIENCES", "EXPERT", "Quel élément chimique porte le symbole Fe ?", listOf("Fluor", "Fer", "Francium", "Fermium"), 1),
    KingQiQuestion("GÉOGRAPHIE", "EXPERT", "Quel fleuve traverse Brazzaville et Kinshasa ?", listOf("Nil", "Congo", "Niger", "Zambèze"), 1),
    KingQiQuestion("GÉOGRAPHIE", "FACILE", "Quelle est la capitale du Brésil ?", listOf("Lima", "Brasília", "Bogota", "Quito"), 1),
    KingQiQuestion("CULTURE", "FACILE", "Combien de côtés possède un hexagone ?", listOf("Cinq", "Six", "Sept", "Huit"), 1),
    KingQiQuestion("SCIENCES", "MOYEN", "Quel organe pompe le sang dans le corps humain ?", listOf("Le foie", "Le poumon", "Le cœur", "Le rein"), 2),
    KingQiQuestion("HISTOIRE", "MOYEN", "Qui fut le premier humain à marcher sur la Lune ?", listOf("Neil Armstrong", "Youri Gagarine", "Buzz Aldrin", "John Glenn"), 0),
    KingQiQuestion("TECHNOLOGIE", "MOYEN", "Quel langage structure principalement une page web ?", listOf("CSS", "HTML", "SQL", "Kotlin"), 1),
    KingQiQuestion("LOGIQUE", "MOYEN", "Quelle est la suite : 2, 4, 8, 16, ... ?", listOf("20", "24", "30", "32"), 3),
    KingQiQuestion("GÉOGRAPHIE", "EXPERT", "Quel est le plus grand désert du monde ?", listOf("Sahara", "Gobi", "Antarctique", "Kalahari"), 2),
    KingQiQuestion("SCIENCES", "EXPERT", "À quelle température l’eau bout-elle au niveau de la mer ?", listOf("50 °C", "80 °C", "90 °C", "100 °C"), 3),
    KingQiQuestion("HISTOIRE", "FACILE", "Quel mur historique se trouve en Chine ?", listOf("Mur d'Hadrien", "Grande Muraille", "Mur des Lamentations", "Mur de Berlin"), 1),
    KingQiQuestion("LANGUES", "FACILE", "Quel mot signifie « bonjour » en espagnol ?", listOf("Ciao", "Hello", "Hola", "Olá"), 2),
    KingQiQuestion("NATURE", "FACILE", "Quel animal est le plus grand mammifère du monde ?", listOf("Éléphant", "Baleine bleue", "Girafe", "Requin-baleine"), 1),
    KingQiQuestion("SPORT", "FACILE", "Combien de joueurs une équipe de football aligne-t-elle sur le terrain ?", listOf("9", "10", "11", "12"), 2),
    KingQiQuestion("LOGIQUE", "MOYEN", "Quel nombre complète la suite : 3, 6, 12, 24, ... ?", listOf("36", "42", "48", "54"), 2),
    KingQiQuestion("SCIENCES", "MOYEN", "Quelle planète est connue comme la planète rouge ?", listOf("Mars", "Vénus", "Jupiter", "Mercure"), 0),
    KingQiQuestion("GÉOGRAPHIE", "MOYEN", "Sur quel continent se trouve le Congo ?", listOf("Asie", "Afrique", "Europe", "Amérique du Sud"), 1),
    KingQiQuestion("CULTURE", "MOYEN", "Combien de cordes possède une guitare classique ?", listOf("4", "5", "6", "7"), 2),
    KingQiQuestion("TECHNOLOGIE", "MOYEN", "Quel composant stocke temporairement les données d'un téléphone ?", listOf("RAM", "Écran", "Micro", "Haut-parleur"), 0),
    KingQiQuestion("LANGUES", "MOYEN", "Quelle langue est majoritaire au Brésil ?", listOf("Espagnol", "Portugais", "Français", "Anglais"), 1),
    KingQiQuestion("HISTOIRE", "MOYEN", "Quelle ville était ensevelie par le Vésuve en 79 ?", listOf("Pompéi", "Athènes", "Carthage", "Rome"), 0),
    KingQiQuestion("SPORT", "MOYEN", "Combien de cases compte un échiquier ?", listOf("36", "49", "64", "81"), 2),
    KingQiQuestion("NATURE", "MOYEN", "Quel gaz les plantes absorbent-elles principalement ?", listOf("Oxygène", "Dioxyde de carbone", "Hélium", "Azote"), 1),
    KingQiQuestion("LOGIQUE", "EXPERT", "Si 5 machines produisent 5 pièces en 5 minutes, combien de minutes faut-il à 100 machines pour produire 100 pièces ?", listOf("5", "20", "100", "500"), 0),
    KingQiQuestion("SCIENCES", "EXPERT", "Quelle unité mesure une fréquence ?", listOf("Watt", "Pascal", "Hertz", "Joule"), 2),
    KingQiQuestion("GÉOGRAPHIE", "EXPERT", "Quel détroit sépare l'Europe et l'Afrique ?", listOf("Béring", "Gibraltar", "Malacca", "Ormuz"), 1),
    KingQiQuestion("TECHNOLOGIE", "EXPERT", "Quelle pratique chiffre des données sans pouvoir les modifier ?", listOf("Hachage", "Compression", "Indexation", "Cache"), 0),
    KingQiQuestion("CULTURE", "EXPERT", "Qui a écrit « Le Petit Prince » ?", listOf("Victor Hugo", "Albert Camus", "Antoine de Saint-Exupéry", "Jules Verne"), 2),
    KingQiQuestion("AFRIQUE", "EXPERT", "Quel fleuve est le deuxième plus long d'Afrique après le Nil ?", listOf("Congo", "Niger", "Zambèze", "Orange"), 0),
)

private enum class KingQiSection { HOME, SOLO, DUEL, ONLINE }

@Composable
internal fun KingQiArena(
    currentUserId: String,
    accountName: String,
    onStartLive: () -> Unit,
) {
    var section by remember { mutableStateOf(KingQiSection.HOME) }
    var profile by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var loadingProfile by remember { mutableStateOf(true) }
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            loadingProfile = false
        } else runCatching {
            @Suppress("UNCHECKED_CAST")
            functions.getHttpsCallable("kingQiGetProfile").call().await().data as? Map<String, Any?> ?: emptyMap()
        }.onSuccess { profile = it }.also { loadingProfile = false }
    }

    when (section) {
        KingQiSection.SOLO -> KingQiSoloGame(accountName = accountName, onExit = { section = KingQiSection.HOME })
        KingQiSection.DUEL -> KingQiOnlineArena(currentUserId, accountName, functions, initialMaxPlayers = 2, onBack = { section = KingQiSection.HOME }, onStartLive = onStartLive)
        KingQiSection.ONLINE -> KingQiOnlineArena(currentUserId, accountName, functions, initialMaxPlayers = 4, onBack = { section = KingQiSection.HOME }, onStartLive = onStartLive)
        KingQiSection.HOME -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KingQiHero(
                credits = (profile["credits"] as? Number)?.toInt() ?: 0,
                trophies = (profile["trophies"] as? Number)?.toInt() ?: 0,
                loading = loadingProfile,
            )
            KingQiPlayerProfileCard(
                displayName = accountName,
                photoUrl = ((profile["playerProfile"] as? Map<*, *>)?.get("photoUrl") ?: "").toString(),
                victories = (profile["victories"] as? Number)?.toInt() ?: 0,
                trophies = (profile["trophies"] as? Number)?.toInt() ?: 0,
            )
            Text("Choisissez votre arène", color = KingNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
            KingQiModeCard("SOLO IA", "La voix King QI pose 10 questions. Répondez au micro ou touchez une barre.", "♛", KingGold) { section = KingQiSection.SOLO }
            KingQiModeCard("DUEL WAPI", "Affrontez un seul contact : deux joueurs, une arène, un vainqueur.", "⚔", KingRed) { section = KingQiSection.DUEL }
            KingQiModeCard("TOURNOI AVEC CONTACTS", "Créez un code privé, invitez 2 à 4 proches et gagnez des trophées.", "👥", WhappyBlue) { section = KingQiSection.ONLINE }
            KingQiModeCard("KING QI EN DIRECT", "Créez le tournoi puis ouvrez le direct WAPI. Les scores restent validés par le serveur.", "●", KingRed) { section = KingQiSection.ONLINE }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E2)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("COMPÉTITION ÉQUITABLE", color = Color(0xFF956600), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("Les crédits King QI sont promotionnels, non achetables et non convertibles en argent. Les mises réelles restent verrouillées jusqu’aux autorisations légales, au contrôle d’âge et au KYC.", color = KingNavy, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            KingQiLeaderboard(profile["leaderboard"] as? List<*>)
        }
    }
}

@Composable
private fun KingQiHero(credits: Int, trophies: Int, loading: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = KingNavy), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(KingGold), contentAlignment = Alignment.Center) { Text("♛", fontSize = 34.sp, color = KingNavy) }
                Spacer(Modifier.width(13.dp))
                Column { Text("KING QI", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black); Text("La connaissance devient un spectacle.", color = Color.White.copy(alpha = .72f), fontSize = 13.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KingQiStat("CRÉDITS", if (loading) "…" else credits.toString(), Modifier.weight(1f))
                KingQiStat("TROPHÉES", if (loading) "…" else trophies.toString(), Modifier.weight(1f))
                KingQiStat("NIVEAU", "National", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KingQiStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .09f), RoundedCornerShape(14.dp)).padding(11.dp)) {
        Text(label, color = Color.White.copy(alpha = .54f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun KingQiPlayerProfileCard(displayName: String, photoUrl: String, victories: Int, trophies: Int) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4EBF4))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl, displayName, 48.dp, shape = CircleShape)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("PROFIL JOUEUR · KING QI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(displayName.ifBlank { "Joueur WAPI" }, color = KingNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("$victories victoire${if (victories == 1) "" else "s"} · $trophies coupe${if (trophies == 1) "" else "s"}", color = Color(0xFF64748B), fontSize = 11.sp)
            }
            Text("♛", color = KingGold, fontSize = 28.sp)
        }
    }
}

@Composable
private fun KingQiModeCard(title: String, subtitle: String, symbol: String, accent: Color, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().wapiClickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4EBF4))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).background(accent.copy(alpha = .14f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Text(symbol, fontSize = 27.sp, color = accent) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, color = KingNavy, fontWeight = FontWeight.Black); Text(subtitle, color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 17.sp) }
            Text("›", color = accent, fontSize = 27.sp)
        }
    }
}

@Composable
private fun KingQiSoloGame(accountName: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val questions = remember { kingQiSoloQuestions.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(15) }
    var answer by remember { mutableStateOf<Int?>(null) }
    var finished by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val current = questions[index.coerceIn(0, questions.lastIndex)]

    DisposableEffect(Unit) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) engine.language = Locale.FRENCH }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false }
            override fun onResults(results: Bundle?) { listening = false; voiceText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty() }
            override fun onPartialResults(partialResults: Bundle?) { voiceText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty() }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer?.destroy() }
    }

    fun startListening() {
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startListening() }

    fun choose(option: Int) {
        if (answer != null || finished) return
        answer = option
        if (option == current.answer) { score += 500 + seconds * 25; WhappySounds.reward(context) } else WhappySounds.impact()
        recognizer?.stopListening()
    }

    LaunchedEffect(index, finished) {
        if (!finished) {
            seconds = 15; answer = null; voiceText = ""
            delay(350)
            tts?.speak(current.prompt, TextToSpeech.QUEUE_FLUSH, null, "king_qi_$index")
            while (seconds > 0 && answer == null && !finished) { delay(1_000); if (answer == null) seconds -= 1 }
            if (seconds == 0 && answer == null) answer = -1
        }
    }
    LaunchedEffect(voiceText) {
        if (voiceText.isNotBlank() && answer == null) {
            val heard = normalizeKingQi(voiceText)
            val match = current.options.indexOfFirst { option -> val normalized = normalizeKingQi(option); heard.contains(normalized) || normalized.contains(heard) }
            if (match >= 0) choose(match)
        }
    }

    if (finished) {
        KingQiResult(accountName, score, questions.size, onReplay = { index = 0; score = 0; finished = false }, onExit = onExit)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onExit) { Text("Quitter") }
            Spacer(Modifier.weight(1f)); Text("${index + 1}/${questions.size}", color = KingNavy, fontWeight = FontWeight.Black); Spacer(Modifier.width(12.dp)); KingQiTimer(seconds)
        }
        Card(colors = CardDefaults.cardColors(containerColor = KingNavy), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row { Text(current.category, color = KingGold, fontSize = 11.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(current.difficulty, color = Color.White.copy(alpha = .6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Text(current.prompt, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
                Text("SCORE  $score", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        current.options.forEachIndexed { optionIndex, option -> KingQiAnswerBar(optionIndex, option, answer, current.answer) { choose(optionIndex) } }
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = answer == null && recognizer != null,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) KingRed else WhappyBlue),
            shape = RoundedCornerShape(18.dp),
        ) { Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (listening) "Je vous écoute…" else "Répondre avec ma voix", fontWeight = FontWeight.Black) }
        AnimatedVisibility(answer != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (answer == current.answer) "Bonne réponse · +${500 + seconds * 25} points" else "Réponse : ${current.options[current.answer]}", color = if (answer == current.answer) KingGreen else KingRed, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { if (index == questions.lastIndex) finished = true else index += 1 }, colors = ButtonDefaults.buttonColors(containerColor = KingNavy), modifier = Modifier.fillMaxWidth()) { Text(if (index == questions.lastIndex) "Voir mon résultat" else "Question suivante", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun KingQiTimer(seconds: Int) {
    Box(Modifier.size(48.dp).background(if (seconds <= 5) KingRed else KingGold, CircleShape), contentAlignment = Alignment.Center) { Text(seconds.toString(), color = if (seconds <= 5) Color.White else KingNavy, fontSize = 18.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun KingQiAnswerBar(index: Int, text: String, selected: Int?, correct: Int, onClick: () -> Unit) {
    val letter = listOf("A", "B", "C", "D")[index]
    val color = when {
        selected == null -> Color.White
        index == correct -> Color(0xFFE7FAF3)
        index == selected -> Color(0xFFFFE9EC)
        else -> Color.White
    }
    val border = when { index == correct && selected != null -> KingGreen; index == selected -> KingRed; else -> Color(0xFFDCE5F0) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(color).border(1.5.dp, border, RoundedCornerShape(17.dp)).wapiClickable(enabled = selected == null, onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(if (selected == null) KingNavy else border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(letter, color = Color.White, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(12.dp)); Text(text, color = KingNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KingQiResult(accountName: String, score: Int, total: Int, onReplay: () -> Unit, onExit: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1800)) }
    Box(Modifier.fillMaxSize().background(KingNavy), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { repeat(80) { n -> rotate(n * 17f) { drawCircle(listOf(KingGold, WhappyBlue, KingRed, KingGreen)[n % 4], 5f + n % 7, center.copy(y = (size.height * progress.value + n * 31f) % size.height)) } } }
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("🏆", fontSize = 86.sp); Text("GRAND CHAMPION", color = KingGold, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(accountName, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("$score points · $total questions", color = Color.White.copy(alpha = .72f))
            Button(onClick = onReplay, colors = ButtonDefaults.buttonColors(containerColor = KingGold), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null, tint = KingNavy); Spacer(Modifier.width(7.dp)); Text("Rejouer", color = KingNavy, fontWeight = FontWeight.Black) }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Retour aux jeux", color = Color.White) }
        }
    }
}

@Composable
private fun KingQiOnlineArena(
    currentUserId: String,
    accountName: String,
    functions: FirebaseFunctions,
    initialMaxPlayers: Int,
    onBack: () -> Unit,
    onStartLive: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    var codeInput by remember { mutableStateOf("") }
    var entryCredits by remember { mutableIntStateOf(10) }
    var maxPlayers by rememberSaveable { mutableIntStateOf(initialMaxPlayers) }
    var visibility by remember { mutableStateOf("private") }
    var roomId by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }
    fun watch(id: String) {
        listener?.remove(); roomId = id
        listener = firestore.collection("kingQiRooms").document(id).addSnapshotListener { snapshot, exception ->
            if (exception != null) error = wapiUserFacingError(exception, "La synchronisation King QI")
            else room = snapshot?.data ?: emptyMap()
        }
    }
    DisposableEffect(Unit) { onDispose { listener?.remove() } }
    fun call(name: String, data: Map<String, Any?>, done: (Map<String, Any?>) -> Unit = {}) {
        if (busy) return; busy = true; error = null
        scope.launch {
            runCatching {
                @Suppress("UNCHECKED_CAST") functions.getHttpsCallable(name).call(data).await().data as? Map<String, Any?> ?: emptyMap()
            }.onSuccess(done).onFailure { error = wapiUserFacingError(it, "King QI") }
            busy = false
        }
    }

    if (roomId.isBlank()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = onBack) { Text("Retour") }; Spacer(Modifier.weight(1f)); Text("KING QI SOCIAL", color = KingNavy, fontWeight = FontWeight.Black) }
            KingQiModeCard(if (maxPlayers == 2) "Créer un duel privé" else "Créer un tournoi privé", if (maxPlayers == 2) "Invitez un contact : le premier à prendre l’avantage gagne l’arène." else "Le code peut être partagé à vos contacts WAPI.", if (maxPlayers == 2) "⚔" else "♛", KingGold) {}
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { maxPlayers = 2 }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (maxPlayers == 2) Color(0xFFFFE9EC) else Color.White)) { Text("Duel · 2", fontWeight = FontWeight.Black) }
                OutlinedButton(onClick = { maxPlayers = 4 }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (maxPlayers == 4) Color(0xFFE8F5FE) else Color.White)) { Text("Tournoi · 4", fontWeight = FontWeight.Black) }
            }
            Text("Crédits promotionnels par joueur", color = KingNavy, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 10, 25, 50).forEach { value -> OutlinedButton(onClick = { entryCredits = value }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (entryCredits == value) KingGold else Color.White)) { Text(value.toString(), color = KingNavy, fontWeight = FontWeight.Black) } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { visibility = "private" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (visibility == "private") Color(0xFFE8F5FE) else Color.White)) { Icon(Icons.Rounded.Groups, null); Spacer(Modifier.width(5.dp)); Text("Contacts") }
                OutlinedButton(onClick = { visibility = "live" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (visibility == "live") Color(0xFFFFE9EC) else Color.White)) { Icon(Icons.Rounded.LiveTv, null); Spacer(Modifier.width(5.dp)); Text("Direct") }
            }
            Button(onClick = { call("kingQiCreateTournament", mapOf("entryCredits" to entryCredits, "maxPlayers" to maxPlayers, "visibility" to visibility)) { watch((it["roomId"] ?: "").toString()) } }, enabled = currentUserId.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = KingNavy)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text(if (maxPlayers == 2) "Créer le duel" else "Créer le tournoi", fontWeight = FontWeight.Black) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Spacer(Modifier.weight(1f).height(1.dp).background(Color(0xFFDCE5F0))); Text("  OU REJOINDRE  ", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f).height(1.dp).background(Color(0xFFDCE5F0))) }
            OutlinedTextField(value = codeInput, onValueChange = { codeInput = it.filter(Char::isLetterOrDigit).uppercase().take(6) }, label = { Text("Code à 6 caractères") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth())
            Button(onClick = { call("kingQiJoinTournament", mapOf("code" to codeInput)) { watch((it["roomId"] ?: "").toString()) } }, enabled = codeInput.length == 6 && !busy, modifier = Modifier.fillMaxWidth()) { Text("Rejoindre l'arène", fontWeight = FontWeight.Black) }
            error?.let { Text(it, color = KingRed, fontSize = 13.sp) }
        }
        return
    }

    val status = (room["status"] ?: "waiting").toString()
    val players = (room["playerIds"] as? List<*>)?.map { it.toString() }.orEmpty()
    val names = room["playerNames"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val photos = room["playerPhotos"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val scores = room["scores"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val hostId = (room["hostId"] ?: "").toString()
    val code = (room["code"] ?: "").toString()
    val question = room["currentQuestion"] as? Map<*, *>
    val answeredIds = (room["answeredIds"] as? List<*>)?.map { it.toString() }.orEmpty()
    val answered = answeredIds.contains(currentUserId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = onBack) { Text("Quitter") }; Spacer(Modifier.weight(1f)); Text("CODE  $code", color = KingNavy, fontWeight = FontWeight.Black); IconButton(onClick = { val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Rejoins mon tournoi King QI sur WAPI avec le code $code") }; context.startActivity(Intent.createChooser(intent, "Inviter à King QI")) }) { Icon(Icons.Rounded.Share, "Partager le tournoi") } }
        val roomCapacity = (room["maxPlayers"] as? Number)?.toInt() ?: maxPlayers
        Card(colors = CardDefaults.cardColors(containerColor = KingNavy), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp)) { Text(if (status == "waiting") "L'arène se remplit" else if (status == "finished") if (roomCapacity == 2) "Duel terminé" else "Tournoi terminé" else "King QI en cours", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black); Text("${players.size}/$roomCapacity joueurs · cagnotte ${room["potCredits"] ?: 0} crédits", color = Color.White.copy(alpha = .68f)) } }
        KingQiPlayerStage(players, names, photos, scores, answeredIds, currentUserId, roomCapacity, accountName)
        when (status) {
            "waiting" -> {
                Text("Partagez le code. Le tournoi démarre à partir de deux joueurs.", color = Color(0xFF64748B), fontSize = 13.sp)
                if (hostId == currentUserId) Button(onClick = { call("kingQiStartTournament", mapOf("roomId" to roomId)) }, enabled = players.size >= 2 && !busy, modifier = Modifier.fillMaxWidth()) { Text("Démarrer King QI", fontWeight = FontWeight.Black) }
                if (room["visibility"] == "live" && hostId == currentUserId) OutlinedButton(onClick = { call("kingQiPrepareLive", mapOf("roomId" to roomId)) { onStartLive() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.LiveTv, null); Spacer(Modifier.width(7.dp)); Text("Ouvrir mon direct WAPI") }
            }
            "playing" -> if (question != null) {
                Text((question["category"] ?: "KING QI").toString(), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text((question["text"] ?: "Question").toString(), color = KingNavy, fontSize = 23.sp, fontWeight = FontWeight.Black)
                (question["options"] as? List<*>)?.forEachIndexed { optionIndex, option ->
                    KingQiAnswerBar(optionIndex, option.toString(), if (answered) -2 else null, -3) { call("kingQiSubmitAnswer", mapOf("roomId" to roomId, "optionIndex" to optionIndex)) }
                }
                if (answered) Text("Réponse verrouillée par le serveur.", color = KingGreen, fontWeight = FontWeight.Bold)
                Button(onClick = { call("kingQiAdvanceTournament", mapOf("roomId" to roomId)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Manche suivante", fontWeight = FontWeight.Black) }
            }
            "finished" -> {
                val winners = (room["winners"] as? List<*>)?.map { it.toString() }.orEmpty()
                Text(if (winners.contains(currentUserId)) "🏆 GRAND CHAMPION" else "Tournoi terminé", color = if (winners.contains(currentUserId)) Color(0xFF956600) else KingNavy, fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Prix par gagnant : ${room["prizePerWinner"] ?: 0} crédits promotionnels", color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        error?.let { Text(it, color = KingRed, fontSize = 13.sp) }
    }
}

@Composable
private fun KingQiLeaderboard(raw: List<*>?) {
    val leaders = raw?.mapNotNull { it as? Map<*, *> }.orEmpty().take(5)
    if (leaders.isEmpty()) return
    Text("Classement international", color = KingNavy, fontSize = 20.sp, fontWeight = FontWeight.Black)
    leaders.forEachIndexed { index, leader ->
        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (index < 3) listOf("🥇", "🥈", "🥉")[index] else "${index + 1}", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp)); Text((leader["displayName"] ?: "Joueur WAPI").toString(), Modifier.weight(1f), color = KingNavy, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.Star, null, tint = KingGold, modifier = Modifier.size(18.dp)); Text(" ${leader["trophies"] ?: 0}", color = KingNavy, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun KingQiPlayerStage(
    players: List<String>,
    names: Map<*, *>,
    photos: Map<*, *>,
    scores: Map<*, *>,
    answeredIds: List<String>,
    currentUserId: String,
    capacity: Int,
    accountName: String,
) {
    val slots = (0 until capacity.coerceIn(2, 4)).map { players.getOrNull(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (capacity == 2) "DUEL · 2 ÉCRANS" else "ARÈNE · 4 ÉCRANS", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
        val rows = slots.chunked(2)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { uid ->
                    val playerId = uid.orEmpty()
                    val playerName = (names[playerId] ?: if (playerId == currentUserId) accountName else "En attente").toString()
                    val photo = (photos[playerId] ?: "").toString()
                    val score = (scores[playerId] as? Number)?.toInt() ?: 0
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (playerId == currentUserId) Color(0xFFEAF6FF) else Color.White), shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (playerId == currentUserId) WhappyBlue.copy(alpha = .35f) else Color(0xFFE4EBF4))) {
                        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(photo, playerName, 38.dp, shape = CircleShape)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(playerName, color = KingNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("$score pts", color = WhappyBlue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text(if (playerId.isBlank()) "En attente" else if (answeredIds.contains(playerId)) "Réponse reçue" else "Réfléchit…", color = if (answeredIds.contains(playerId)) KingGreen else Color(0xFF64748B), fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun normalizeKingQi(value: String): String = Normalizer.normalize(value.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
