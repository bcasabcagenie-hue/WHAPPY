package com.whappy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

private data class WapiSaleProduct(
    val id: String,
    val title: String,
    val description: String,
    val price: Long,
    val originalPrice: Long,
    val stock: Int,
    val sold: Int,
    val status: String,
)

private data class WapiSaleRoom(
    val id: String,
    val ownerId: String,
    val pageId: String,
    val pageName: String,
    val pageCategory: String,
    val pageCity: String,
    val title: String,
    val description: String,
    val visibility: String,
    val status: String,
    val code: String,
    val viewerCount: Int,
    val reservationCount: Int,
    val endsAt: Long,
    val products: List<WapiSaleProduct>,
)

private fun parseSaleRooms(raw: Any?): List<WapiSaleRoom> {
    val root = raw as? Map<*, *> ?: return emptyList()
    return (root["rooms"] as? List<*>)?.mapNotNull { item ->
        val value = item as? Map<*, *> ?: return@mapNotNull null
        val id = value["id"]?.toString().orEmpty()
        if (id.isBlank()) return@mapNotNull null
        val products = (value["products"] as? List<*>)?.mapNotNull { rawProduct ->
            val product = rawProduct as? Map<*, *> ?: return@mapNotNull null
            WapiSaleProduct(
                id = product["id"]?.toString().orEmpty(),
                title = product["title"]?.toString() ?: "Produit WAPI",
                description = product["description"]?.toString().orEmpty(),
                price = (product["price"] as? Number)?.toLong() ?: 0L,
                originalPrice = (product["originalPrice"] as? Number)?.toLong() ?: 0L,
                stock = (product["stock"] as? Number)?.toInt() ?: 0,
                sold = (product["sold"] as? Number)?.toInt() ?: 0,
                status = product["status"]?.toString() ?: "active",
            )
        }.orEmpty()
        WapiSaleRoom(
            id = id,
            ownerId = value["ownerId"]?.toString().orEmpty(),
            pageId = value["pageId"]?.toString().orEmpty(),
            pageName = value["pageName"]?.toString() ?: "WAPI Business",
            pageCategory = value["pageCategory"]?.toString() ?: "Commerce",
            pageCity = value["pageCity"]?.toString().orEmpty(),
            title = value["title"]?.toString() ?: "Vente privée WAPI",
            description = value["description"]?.toString().orEmpty(),
            visibility = value["visibility"]?.toString() ?: "public",
            status = value["status"]?.toString() ?: "scheduled",
            code = value["code"]?.toString().orEmpty(),
            viewerCount = (value["viewerCount"] as? Number)?.toInt() ?: 0,
            reservationCount = (value["reservationCount"] as? Number)?.toInt() ?: 0,
            endsAt = (value["endsAtMillis"] as? Number)?.toLong() ?: 0L,
            products = products,
        )
    }.orEmpty()
}

private suspend fun loadVisibleSaleRooms(
    functions: FirebaseFunctions,
    firestore: FirebaseFirestore,
    currentUserId: String,
): List<WapiSaleRoom> = runCatching {
    parseSaleRooms(functions.getHttpsCallable("listVisibleBusinessSaleRooms").call().await().data)
}.recoverCatching { failure ->
    val code = (failure as? FirebaseFunctionsException)?.code
    if (code !in setOf(
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        )
    ) throw failure
    loadVisibleSaleRoomsFromFirestore(firestore, currentUserId)
}.getOrThrow()

/**
 * Read-only compatibility path used when the matching callable has not reached
 * production yet. Both queries are constrained so Firestore rules can prove
 * that every returned room is public or owned by the signed-in account.
 */
private suspend fun loadVisibleSaleRoomsFromFirestore(
    firestore: FirebaseFirestore,
    currentUserId: String,
): List<WapiSaleRoom> {
    val documents = linkedMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
    firestore.collection("businessSaleRooms")
        .whereEqualTo("visibility", "public")
        .limit(100)
        .get().await().documents.forEach { documents[it.id] = it }
    if (currentUserId.isNotBlank()) {
        firestore.collection("businessSaleRooms")
            .whereEqualTo("ownerId", currentUserId)
            .limit(100)
            .get().await().documents.forEach { documents[it.id] = it }
    }
    val now = System.currentTimeMillis()
    val visible = documents.values.filter { document ->
        document.getString("status") in setOf("scheduled", "live") &&
            (document.getTimestamp("endsAt")?.toDate()?.time ?: Long.MAX_VALUE) > now
    }
    val dealIds = visible.flatMap { document ->
        (document.get("dealIds") as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
    }.distinct()
    val deals = linkedMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
    dealIds.chunked(30).forEach { chunk ->
        firestore.collection("businessDeals")
            .whereIn(FieldPath.documentId(), chunk)
            .get().await().documents.forEach { deals[it.id] = it }
    }
    return visible.map { document ->
        val products = (document.get("dealIds") as? List<*>).orEmpty().mapNotNull { rawId ->
            val deal = deals[rawId?.toString().orEmpty()] ?: return@mapNotNull null
            WapiSaleProduct(
                id = deal.id,
                title = deal.getString("title") ?: "Produit WAPI",
                description = deal.getString("description").orEmpty(),
                price = deal.getLong("dealPrice") ?: 0L,
                originalPrice = deal.getLong("originalPrice") ?: 0L,
                stock = deal.getLong("stock")?.toInt() ?: 0,
                sold = deal.getLong("sold")?.toInt() ?: 0,
                status = deal.getString("status") ?: "active",
            )
        }
        WapiSaleRoom(
            id = document.id,
            ownerId = document.getString("ownerId").orEmpty(),
            pageId = document.getString("pageId").orEmpty(),
            pageName = document.getString("pageName") ?: "WAPI Business",
            pageCategory = document.getString("pageCategory") ?: "Commerce",
            pageCity = document.getString("pageCity").orEmpty(),
            title = document.getString("title") ?: "Vente privée WAPI",
            description = document.getString("description").orEmpty(),
            visibility = document.getString("visibility") ?: "public",
            status = document.getString("status") ?: "scheduled",
            code = document.getString("code").orEmpty(),
            viewerCount = document.getLong("viewerCount")?.toInt() ?: 0,
            reservationCount = document.getLong("reservationCount")?.toInt() ?: 0,
            endsAt = document.getTimestamp("endsAt")?.toDate()?.time ?: 0L,
            products = products,
        )
    }.sortedWith(compareByDescending<WapiSaleRoom> { it.status == "live" }.thenBy { it.endsAt })
}

private fun saleRoomFailureMessage(failure: Throwable): String {
    val remote = failure as? FirebaseFunctionsException
    return when (remote?.code) {
        FirebaseFunctionsException.Code.NOT_FOUND -> "La mise à jour Ventes privées n’est pas encore active sur le serveur WAPI."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Reconnectez-vous à WAPI pour accéder aux ventes privées."
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Votre compte n’est pas autorisé à effectuer cette opération."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> remote.message?.takeIf(String::isNotBlank)
            ?: "Cette vente n’est plus disponible."
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "Connexion momentanément indisponible. Touchez Actualiser."
        else -> "Les ventes privées sont momentanément indisponibles. Touchez Actualiser."
    }
}

@Composable
internal fun WapiPublicSaleRoomsRail() {
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    var rooms by remember { mutableStateOf(emptyList<WapiSaleRoom>()) }
    var selected by remember { mutableStateOf<WapiSaleRoom?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        runCatching { loadVisibleSaleRooms(functions, firestore, currentUserId) }
            .onSuccess { rooms = it; error = null }
            .onFailure { error = saleRoomFailureMessage(it) }
        loading = false
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WhappyBlue.copy(alpha = .14f))) {
        Column(Modifier.padding(vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(WhappyBlue.copy(alpha = .11f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Language, null, tint = WhappyBlue) }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text("Ventes privées en direct", color = WhappyDark, fontWeight = FontWeight.Black); Text("Boutiques visibles dans tout WAPI · aucun numéro requis", color = WhappyMuted, fontSize = 10.sp) }
                IconButton(onClick = { refresh += 1 }, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, "Actualiser") }
            }
            when {
                error != null -> Text(error.orEmpty(), Modifier.padding(horizontal = 15.dp), color = Color(0xFFD43D51), fontSize = 11.sp)
                !loading && rooms.isEmpty() -> Text("Aucune vente ouverte pour le moment.", Modifier.padding(horizontal = 15.dp), color = WhappyMuted, fontSize = 12.sp)
                else -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rooms.take(12).forEach { room ->
                        Card(Modifier.width(238.dp).wapiClickable { selected = room }, colors = CardDefaults.cardColors(containerColor = Color(0xFF071B3E)), shape = RoundedCornerShape(19.dp)) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row { Text(if (room.status == "live") "● EN COURS" else "BIENTÔT", color = if (room.status == "live") Color(0xFFFF6274) else Color(0xFFFFC83D), fontSize = 9.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(if (room.visibility == "public") "MONDIAL" else "CONTACTS", color = Color.White.copy(alpha = .65f), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                Text(room.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${room.pageName} · ${room.pageCity.ifBlank { "WAPI" }}", color = Color.White.copy(alpha = .65f), fontSize = 11.sp, maxLines = 1)
                                Text("${room.products.size} produit(s) · ${room.viewerCount} visiteur(s)", color = Color(0xFF7DD3FC), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { room -> WapiSaleRoomDialog(room, functions, onDismiss = { selected = null }, onChanged = { refresh += 1 }) }
}

@Composable
private fun WapiSaleRoomDialog(room: WapiSaleRoom, functions: FirebaseFunctions, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var joining by remember { mutableStateOf(true) }
    var busyProduct by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(room.id) {
        if (room.status == "live") runCatching { functions.getHttpsCallable("joinBusinessSaleRoom").call(mapOf("roomId" to room.id)).await() }.onFailure { feedback = saleRoomFailureMessage(it) }
        joining = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F8FB)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer") }; Column(Modifier.weight(1f)) { Text(room.pageName, fontWeight = FontWeight.Black, color = WhappyDark); Text("VENTE PRIVÉE · ${if (room.visibility == "public") "PUBLIQUE MONDIALE" else "CONTACTS"}", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) } }
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF071B3E), Color(0xFF006DD6))), RoundedCornerShape(25.dp)).padding(21.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (room.status == "live") "● EN COURS" else "PROGRAMMÉE", color = Color(0xFFFFC83D), fontSize = 10.sp, fontWeight = FontWeight.Black); Text(room.title, color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black); if (room.description.isNotBlank()) Text(room.description, color = Color.White.copy(alpha = .75f), fontSize = 12.sp); Text("${room.viewerCount} visiteurs · ${room.reservationCount} articles réservés", color = Color.White.copy(alpha = .62f), fontSize = 10.sp) }
                }
                feedback?.let { Text(it, Modifier.fillMaxWidth().background(if (it.startsWith("Réservation")) Color(0xFFE7FAF3) else Color(0xFFFFE9EC), RoundedCornerShape(13.dp)).padding(12.dp), color = WhappyDark, fontWeight = FontWeight.Bold) }
                Text("Sélection de la boutique", color = WhappyDark, fontSize = 20.sp, fontWeight = FontWeight.Black)
                room.products.forEach { product ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(product.title, color = WhappyDark, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            if (product.description.isNotBlank()) Text(product.description, color = WhappyMuted, fontSize = 11.sp, maxLines = 3)
                            Row(verticalAlignment = Alignment.Bottom) { Text(saleMoney(product.price), color = WhappyBlue, fontSize = 20.sp, fontWeight = FontWeight.Black); if (product.originalPrice > product.price) Text(saleMoney(product.originalPrice), Modifier.padding(start = 8.dp), color = WhappyMuted, fontSize = 10.sp); Spacer(Modifier.weight(1f)); Text("${(product.stock - product.sold).coerceAtLeast(0)} restant(s)", color = WhappyMuted, fontSize = 10.sp) }
                            Button(onClick = {
                                busyProduct = product.id; feedback = null
                                scope.launch { runCatching { functions.getHttpsCallable("reserveBusinessSaleProduct").call(mapOf("roomId" to room.id, "dealId" to product.id, "quantity" to 1)).await() }.onSuccess { feedback = "Réservation confirmée. La boutique a reçu votre commande."; onChanged() }.onFailure { feedback = saleRoomFailureMessage(it) }; busyProduct = null }
                            }, enabled = room.status == "live" && !joining && busyProduct == null && product.status == "active" && product.sold < product.stock, modifier = Modifier.fillMaxWidth()) { if (busyProduct == product.id) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else { Icon(Icons.Rounded.ShoppingBag, null); Spacer(Modifier.width(6.dp)); Text("Réserver maintenant", fontWeight = FontWeight.Black) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WapiBusinessSaleRoomManager(pages: List<WhappyBusinessPage>, deals: List<WhappyDeal>) {
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf(emptyList<WapiSaleRoom>()) }
    var creating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        runCatching { loadVisibleSaleRooms(functions, firestore, currentUserId).filter { it.ownerId == currentUserId } }.onSuccess { rooms = it; feedback = null }.onFailure { feedback = saleRoomFailureMessage(it) }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Salons de vente", color = WhappyDark, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Ventes événementielles visibles sans partager votre numéro", color = WhappyMuted, fontSize = 11.sp) }; Button(onClick = { creating = true }, enabled = pages.isNotEmpty() && deals.any { it.status in listOf("active", "paused") }) { Text("+ Créer") } }
        feedback?.let { Text(it, color = Color(0xFFD43D51), fontSize = 11.sp) }
        if (loading) CircularProgressIndicator()
        else if (rooms.isEmpty()) Text("Aucun salon actif. Sélectionnez des produits du catalogue pour lancer votre première vente.", Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(17.dp)).padding(16.dp), color = WhappyMuted, fontSize = 12.sp)
        rooms.forEach { room ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row { Text(if (room.status == "live") "● EN COURS" else room.status.uppercase(), color = if (room.status == "live") Color(0xFFE64258) else WhappyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(if (room.visibility == "public") "🌍 MONDIAL" else "CONTACTS", color = WhappyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) }; Text(room.title, color = WhappyDark, fontWeight = FontWeight.Black); Text("${room.viewerCount} visiteurs · ${room.reservationCount} réservations · code ${room.code}", color = WhappyMuted, fontSize = 10.sp); if (room.status != "ended") OutlinedButton(onClick = { scope.launch { runCatching { functions.getHttpsCallable("endBusinessSaleRoom").call(mapOf("roomId" to room.id)).await() }.onSuccess { refresh += 1 }.onFailure { feedback = saleRoomFailureMessage(it) } } }, modifier = Modifier.fillMaxWidth()) { Text("Terminer la vente") } } }
        }
    }
    if (creating) WapiCreateSaleRoomDialog(pages, deals, functions, onDismiss = { creating = false }) { creating = false; feedback = "Salon publié dans le Marché WAPI mondial."; refresh += 1 }
}

@Composable
private fun WapiCreateSaleRoomDialog(pages: List<WhappyBusinessPage>, deals: List<WhappyDeal>, functions: FirebaseFunctions, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pageId by remember { mutableStateOf(pages.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var worldwide by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val available = deals.filter { it.pageId == pageId && it.status in listOf("active", "paused") }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Nouveau salon de vente", color = WhappyDark, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("Événement commercial WAPI", color = WhappyBlue, fontSize = 10.sp, fontWeight = FontWeight.Black) }; IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Rounded.Close, "Fermer") } }
                Text("Page Business", color = WhappyDark, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { pages.forEach { page -> OutlinedButton(onClick = { pageId = page.id; selectedIds = emptySet() }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (pageId == page.id) WhappyBlue.copy(alpha = .12f) else Color.White)) { Text(page.name, maxLines = 1) } } }
                OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Nom de la vente") }, placeholder = { Text("Ex. Vente privée rentrée") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Présentation") }, minLines = 2, maxLines = 4)
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("Durée en minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (worldwide) Icons.Rounded.Language else Icons.Rounded.People, null, tint = WhappyBlue); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(if (worldwide) "Visible dans tout WAPI" else "Réservé à vos contacts", fontWeight = FontWeight.Bold); Text(if (worldwide) "Aucun numéro de téléphone nécessaire" else "Seuls vos contacts retrouveront le salon", color = WhappyMuted, fontSize = 10.sp) }; Switch(worldwide, { worldwide = it }) }
                Text("Produits de la vente", color = WhappyDark, fontWeight = FontWeight.Bold)
                if (available.isEmpty()) Text("Ajoutez d'abord un produit actif à cette page.", color = WhappyMuted, fontSize = 11.sp)
                available.forEach { deal ->
                    Row(
                        Modifier.fillMaxWidth().border(1.dp, if (deal.id in selectedIds) WhappyBlue else WhappyLine, RoundedCornerShape(14.dp))
                            .wapiClickable { selectedIds = if (deal.id in selectedIds) selectedIds - deal.id else selectedIds + deal.id }
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(30.dp).background(if (deal.id in selectedIds) WhappyBlue else Color(0xFFF0F3F7), CircleShape), contentAlignment = Alignment.Center) {
                            if (deal.id in selectedIds) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(deal.title, color = WhappyDark, fontWeight = FontWeight.Bold)
                            Text("${saleMoney(deal.dealPrice)} · ${(deal.stock - deal.sold).coerceAtLeast(0)} disponible(s)", color = WhappyMuted, fontSize = 10.sp)
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFD43D51), fontSize = 11.sp) }
                Button(onClick = {
                    busy = true; error = null
                    scope.launch { runCatching { functions.getHttpsCallable("createBusinessSaleRoom").call(mapOf("pageId" to pageId, "title" to title, "description" to description, "durationMinutes" to (duration.toIntOrNull() ?: 60), "visibility" to if (worldwide) "public" else "contacts", "dealIds" to selectedIds.toList(), "startNow" to true)).await() }.onSuccess { onCreated() }.onFailure { error = saleRoomFailureMessage(it) }; busy = false }
                }, enabled = !busy && pageId.isNotBlank() && selectedIds.isNotEmpty() && (duration.toIntOrNull() ?: 0) in 15..10_080, modifier = Modifier.fillMaxWidth().height(52.dp)) { if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp) else { Icon(Icons.Rounded.Storefront, null); Spacer(Modifier.width(7.dp)); Text("Ouvrir la vente maintenant", fontWeight = FontWeight.Black) } }
            }
        }
    }
}

private fun saleMoney(value: Long): String = NumberFormat.getNumberInstance(Locale.FRANCE).format(value) + " FCFA"
