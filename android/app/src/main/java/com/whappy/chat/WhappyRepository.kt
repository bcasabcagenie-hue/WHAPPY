package com.whappy.chat

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class WhappyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    fun currentUser() = auth.currentUser

    suspend fun restoreAccountDisplayName(user: FirebaseUser): String {
        val reference = db.collection("users").document(user.uid)
        val storedName = runCatching { reference.get().await().getString("displayName").orEmpty().trim() }
            .getOrDefault("")
        val firebaseName = user.displayName.orEmpty().trim()
        val resolvedName = storedName.takeIf { it.length >= 2 }
            ?: firebaseName.takeIf { it.length >= 2 }
            ?: ""

        if (resolvedName.isNotBlank() && resolvedName != firebaseName) {
            val update = UserProfileChangeRequest.Builder().setDisplayName(resolvedName).build()
            runCatching { user.updateProfile(update).await() }
        }
        if (resolvedName.isNotBlank() && storedName.isBlank()) {
            runCatching {
                reference.set(
                    mapOf(
                        "uid" to user.uid,
                        "displayName" to resolvedName,
                        "phoneNumber" to user.phoneNumber.orEmpty(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
            }
        }
        return resolvedName
    }

    fun observeConversations(
        userId: String,
        onChange: (List<WhappyConversation>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("conversations")
        .whereArrayContains("memberIds", userId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val conversations = snapshot?.documents.orEmpty()
                .mapNotNull { it.toConversation(userId) }
                .sortedByDescending { it.updatedAt }
            onChange(conversations)
        }

    fun observeMessages(
        conversationId: String,
        onChange: (List<WhappyMessage>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyMessage(
                    id = document.id,
                    text = document.getString("text").orEmpty(),
                    senderId = document.getString("senderId").orEmpty(),
                    createdAt = document.timestampMillis("createdAt"),
                    kind = document.getString("kind") ?: "text",
                    mediaUrl = document.getString("mediaUrl").orEmpty(),
                    mediaName = document.getString("mediaName").orEmpty(),
                    durationSeconds = document.getLong("duration")?.toInt() ?: 0,
                )
            })
        }

    fun observeListings(
        onChange: (List<WhappyListing>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("listings").addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        onChange(snapshot?.documents.orEmpty().map { document ->
            WhappyListing(
                id = document.id,
                title = document.getString("title") ?: "Annonce WHAPPY",
                price = document.getString("price") ?: "Prix à discuter",
                place = document.getString("place") ?: "Brazzaville",
                seller = document.getString("seller") ?: "Vendeur WHAPPY",
                ownerId = document.getString("ownerId").orEmpty(),
                mode = document.getString("mode") ?: "vente",
            )
        }.filter { it.title.isNotBlank() })
    }

    fun observeBusinessPages(
        ownerId: String,
        onChange: (List<WhappyBusinessPage>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("businessPages")
        .whereEqualTo("ownerId", ownerId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyBusinessPage(
                    id = document.id,
                    name = document.getString("name") ?: "Page WHAPPY",
                    handle = document.getString("handle").orEmpty(),
                    category = document.getString("category").orEmpty(),
                    bio = document.getString("bio").orEmpty(),
                    city = document.getString("city") ?: "Brazzaville",
                    ownerId = document.getString("ownerId").orEmpty(),
                    phone = document.getString("phone").orEmpty(),
                    website = document.getString("website").orEmpty(),
                )
            })
        }

    fun observeCampaigns(
        ownerId: String,
        onChange: (List<WhappyCampaign>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("adCampaigns")
        .whereEqualTo("ownerId", ownerId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyCampaign(
                    id = document.id,
                    pageId = document.getString("pageId").orEmpty(),
                    pageName = document.getString("pageName") ?: "Page WHAPPY",
                    objective = document.getString("objective") ?: "reach",
                    title = document.getString("title") ?: "Campagne WHAPPY",
                    dailyBudget = document.getLong("dailyBudget") ?: 0L,
                    days = document.getLong("days")?.toInt() ?: 1,
                    status = document.getString("status") ?: "active",
                )
            })
        }

    fun observeLives(
        onChange: (List<WhappyLive>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("liveSessions")
        .whereIn("status", listOf("scheduled", "live"))
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyLive(
                    id = document.id,
                    hostId = document.getString("hostId").orEmpty(),
                    hostName = document.getString("hostName") ?: "Créateur WHAPPY",
                    title = document.getString("title") ?: "Direct WHAPPY",
                    category = document.getString("category") ?: "Communauté",
                    productTitle = document.getString("productTitle").orEmpty(),
                    status = document.getString("status") ?: "scheduled",
                    viewerCount = document.getLong("viewerCount")?.toInt() ?: 0,
                    startedAt = document.timestampMillis("startedAt"),
                )
            }.sortedWith(compareByDescending<WhappyLive> { it.status == "live" }.thenByDescending { it.startedAt }))
        }

    fun observeDeals(
        ownerId: String,
        onChange: (List<WhappyDeal>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("businessDeals")
        .whereEqualTo("ownerId", ownerId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyDeal(
                    id = document.id,
                    pageId = document.getString("pageId").orEmpty(),
                    pageName = document.getString("pageName") ?: "Page WHAPPY",
                    ownerId = document.getString("ownerId").orEmpty(),
                    title = document.getString("title") ?: "Deal WHAPPY",
                    description = document.getString("description").orEmpty(),
                    originalPrice = document.getLong("originalPrice") ?: 0L,
                    dealPrice = document.getLong("dealPrice") ?: 0L,
                    stock = document.getLong("stock")?.toInt() ?: 0,
                    sold = document.getLong("sold")?.toInt() ?: 0,
                    endsAt = document.timestampMillis("endsAt"),
                    status = document.getString("status") ?: "active",
                )
            }.sortedByDescending { it.endsAt })
        }

    fun observePaymentNotices(
        ownerId: String,
        onChange: (List<WhappyPaymentNotice>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("paymentNotifications")
        .whereEqualTo("ownerId", ownerId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyPaymentNotice(
                    id = document.id,
                    pageId = document.getString("pageId").orEmpty(),
                    dealId = document.getString("dealId").orEmpty(),
                    buyerName = document.getString("buyerName") ?: "Client WHAPPY",
                    amount = document.getLong("amount") ?: 0L,
                    currency = document.getString("currency") ?: "XAF",
                    provider = document.getString("provider") ?: "Paiement WHAPPY",
                    status = document.getString("status") ?: "pending",
                    createdAt = document.timestampMillis("createdAt"),
                    read = document.getBoolean("read") ?: false,
                )
            }.sortedByDescending { it.createdAt })
        }

    fun observeTwinProfile(
        userId: String,
        onChange: (WhappyTwinProfile?) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("users").document(userId)
        .collection("twinProfiles").document("main")
        .addSnapshotListener { document, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (document == null || !document.exists()) {
                onChange(null)
                return@addSnapshotListener
            }
            onChange(
                WhappyTwinProfile(
                    displayName = document.getString("displayName").orEmpty(),
                    identityConsent = document.getBoolean("identityConsent") ?: false,
                    voiceConsent = document.getBoolean("voiceConsent") ?: false,
                    movementConsent = document.getBoolean("movementConsent") ?: false,
                    videoUrl = document.getString("videoUrl").orEmpty(),
                    voiceUrl = document.getString("voiceUrl").orEmpty(),
                    movementUrl = document.getString("movementUrl").orEmpty(),
                    voiceStatus = document.getString("voiceStatus") ?: "empty",
                    movementStatus = document.getString("movementStatus") ?: "empty",
                ),
            )
        }

    fun observeTwinAutomations(
        userId: String,
        onChange: (List<WhappyTwinAutomation>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("users").document(userId)
        .collection("twinAutomations")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyTwinAutomation(
                    id = document.id,
                    name = document.getString("name") ?: "Mission WHAPPY",
                    trigger = document.getString("trigger").orEmpty(),
                    channel = document.getString("channel").orEmpty(),
                    action = document.getString("action").orEmpty(),
                    enabled = document.getBoolean("enabled") ?: false,
                )
            })
        }

    fun observeTwinRenders(
        userId: String,
        onChange: (List<WhappyTwinRender>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("users").document(userId)
        .collection("twinRenders")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyTwinRender(
                    id = document.id,
                    title = document.getString("title") ?: "Production WHAPPY",
                    script = document.getString("script").orEmpty(),
                    language = document.getString("language") ?: "fr-FR",
                    status = document.getString("status") ?: "prepared",
                )
            })
        }

    suspend fun sendMessage(conversationId: String, userId: String, text: String) {
        val value = text.trim()
        require(value.isNotEmpty() && value.length <= 4_000)
        val conversation = db.collection("conversations").document(conversationId)
        conversation.collection("messages").add(
            mapOf(
                "text" to value,
                "senderId" to userId,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        conversation.update(
            mapOf(
                "lastMessage" to value,
                "updatedAt" to FieldValue.serverTimestamp(),
                "typingBy.$userId" to false,
            ),
        ).await()
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        userId: String,
        uri: Uri,
        kind: String,
        contentType: String,
        mediaName: String,
        durationSeconds: Int = 0,
    ) {
        require(kind in setOf("image", "audio", "video"))
        val extension = when (kind) {
            "audio" -> "m4a"
            "video" -> "mp4"
            else -> contentType.substringAfter('/', "jpg").substringBefore('+').take(8)
        }
        val safeName = mediaName.trim().take(120).ifBlank { "whappy-${kind}.${extension}" }
        val objectName = "${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
        val objectRef = storage.reference.child("conversations/$conversationId/$userId/$objectName")
        objectRef.putFile(uri, com.google.firebase.storage.StorageMetadata.Builder().setContentType(contentType).build()).await()
        val downloadUrl = objectRef.downloadUrl.await().toString()
        val label = when (kind) {
            "audio" -> "Note vocale"
            "video" -> "Vidéo"
            else -> "Photo"
        }
        val conversation = db.collection("conversations").document(conversationId)
        conversation.collection("messages").add(
            mapOf(
                "text" to label,
                "senderId" to userId,
                "kind" to kind,
                "mediaUrl" to downloadUrl,
                "mediaName" to safeName,
                "duration" to durationSeconds.coerceIn(0, 600),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        conversation.update(
            mapOf(
                "lastMessage" to label,
                "updatedAt" to FieldValue.serverTimestamp(),
                "typingBy.$userId" to false,
            ),
        ).await()
    }

    suspend fun publishListing(user: WhappyMember, title: String, price: String, place: String, mode: String) {
        val value = title.trim()
        require(value.length in 2..120)
        db.collection("listings").add(
            mapOf(
                "ownerId" to user.uid,
                "title" to value,
                "price" to price.trim().ifBlank { "Prix à discuter" },
                "place" to place.trim().ifBlank { "Brazzaville" },
                "seller" to user.displayName,
                "category" to "Communauté",
                "mode" to mode,
                "status" to "active",
                "trust" to 100,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun createBusinessPage(userId: String, name: String, category: String, bio: String, city: String) {
        val value = name.trim()
        require(value.length in 2..80)
        val reference = db.collection("businessPages").document()
        val handle = value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(38) + "-" + reference.id.take(5)
        reference.set(
            mapOf(
                "ownerId" to userId,
                "name" to value,
                "handle" to handle,
                "type" to "business",
                "category" to category.trim(),
                "bio" to bio.trim().take(400),
                "city" to city.trim().ifBlank { "Brazzaville" },
                "phone" to "",
                "website" to "",
                "status" to "active",
                "followers" to 0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun updateBusinessPage(userId: String, page: WhappyBusinessPage, name: String, category: String, bio: String, city: String, phone: String, website: String) {
        require(page.ownerId == userId)
        require(name.trim().length in 2..80)
        db.collection("businessPages").document(page.id).update(
            mapOf(
                "name" to name.trim(),
                "type" to "business",
                "category" to category.trim().take(80),
                "bio" to bio.trim().take(400),
                "city" to city.trim().take(80).ifBlank { "Brazzaville" },
                "phone" to phone.trim().take(30),
                "website" to website.trim().take(180),
                "status" to "active",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun createCampaign(userId: String, draft: WhappyCampaignDraft) {
        require(draft.title.trim().length in 2..120)
        require(draft.creative.trim().length in 2..600)
        require(draft.dailyBudget >= 500L)
        require(draft.days in 1..90)
        db.collection("adCampaigns").add(
            mapOf(
                "ownerId" to userId,
                "pageId" to draft.pageId,
                "pageName" to draft.pageName,
                "objective" to draft.objective,
                "title" to draft.title.trim(),
                "creative" to draft.creative.trim(),
                "cta" to draft.cta.trim().take(40),
                "audience" to draft.audience.trim().take(120),
                "city" to draft.city.trim().take(80),
                "dailyBudget" to draft.dailyBudget,
                "days" to draft.days,
                "totalBudget" to draft.dailyBudget * draft.days,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun createLive(userId: String, hostName: String, title: String, category: String, productTitle: String) {
        require(title.trim().length in 2..120)
        db.collection("liveSessions").add(
            mapOf(
                "hostId" to userId,
                "hostName" to hostName.trim().take(80),
                "title" to title.trim(),
                "category" to category.trim().take(60),
                "productTitle" to productTitle.trim().take(120),
                "status" to "scheduled",
                "viewerCount" to 0,
                "streamProvider" to "unconfigured",
                "createdAt" to FieldValue.serverTimestamp(),
                "startedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun endLive(userId: String, liveId: String) {
        val reference = db.collection("liveSessions").document(liveId)
        val document = reference.get().await()
        require(document.getString("hostId") == userId)
        reference.update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun updateLiveStatus(userId: String, liveId: String, status: String) {
        require(status in setOf("live", "ended"))
        val reference = db.collection("liveSessions").document(liveId)
        val document = reference.get().await()
        require(document.getString("hostId") == userId)
        val values = mutableMapOf<String, Any>("status" to status, "updatedAt" to FieldValue.serverTimestamp())
        if (status == "live") values["startedAt"] = FieldValue.serverTimestamp()
        else values["endedAt"] = FieldValue.serverTimestamp()
        reference.update(values).await()
    }

    suspend fun createDeal(userId: String, page: WhappyBusinessPage, title: String, description: String, originalPrice: Long, dealPrice: Long, stock: Int, durationDays: Int) {
        require(title.trim().length in 2..120)
        require(dealPrice > 0 && originalPrice >= dealPrice)
        require(stock in 1..100_000 && durationDays in 1..90)
        val endsAt = com.google.firebase.Timestamp(Date(System.currentTimeMillis() + durationDays * 86_400_000L))
        db.collection("businessDeals").add(
            mapOf(
                "ownerId" to userId,
                "pageId" to page.id,
                "pageName" to page.name,
                "title" to title.trim(),
                "description" to description.trim().take(600),
                "originalPrice" to originalPrice,
                "dealPrice" to dealPrice,
                "stock" to stock,
                "sold" to 0,
                "endsAt" to endsAt,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun updateDealStatus(userId: String, dealId: String, status: String) {
        require(status in setOf("active", "paused", "ended"))
        val reference = db.collection("businessDeals").document(dealId)
        val document = reference.get().await()
        require(document.getString("ownerId") == userId)
        reference.update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun markPaymentNoticeRead(userId: String, noticeId: String) {
        val reference = db.collection("paymentNotifications").document(noticeId)
        val document = reference.get().await()
        require(document.getString("ownerId") == userId)
        reference.update(mapOf("read" to true, "readAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun registerDeviceToken(userId: String, token: String) {
        if (token.isBlank()) return
        val deviceId = token.takeLast(32).replace(Regex("[^A-Za-z0-9_-]"), "_")
        db.collection("users").document(userId).collection("devices").document(deviceId).set(
            mapOf("token" to token, "platform" to "android", "enabled" to true, "updatedAt" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun saveTwinConsent(userId: String, displayName: String, consent: Boolean) {
        db.collection("users").document(userId).collection("twinProfiles").document("main").set(
            mapOf(
                "userId" to userId,
                "displayName" to displayName,
                "identityConsent" to consent,
                "voiceConsent" to consent,
                "movementConsent" to consent,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun uploadTwinAsset(userId: String, uri: Uri, kind: String, contentType: String) {
        require(kind in setOf("video", "voice", "movement"))
        val extension = if (kind == "voice") "m4a" else "mp4"
        val objectRef = storage.reference.child("twins/$userId/$kind-${System.currentTimeMillis()}.$extension")
        objectRef.putFile(
            uri,
            com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType)
                .setCustomMetadata("ownerId", userId)
                .setCustomMetadata("kind", kind)
                .build(),
        ).await()
        val url = objectRef.downloadUrl.await().toString()
        val changes = when (kind) {
            "voice" -> mapOf("voiceUrl" to url, "voiceStatus" to "sampled")
            "movement" -> mapOf("movementUrl" to url, "movementStatus" to "sampled")
            else -> mapOf("videoUrl" to url)
        }
        db.collection("users").document(userId).collection("twinProfiles").document("main")
            .set(changes + mapOf("updatedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun createTwinAutomation(userId: String, name: String, trigger: String, channel: String, action: String, script: String) {
        db.collection("users").document(userId).collection("twinAutomations").add(
            mapOf(
                "userId" to userId,
                "name" to name.trim().take(120),
                "trigger" to trigger,
                "channel" to channel,
                "action" to action,
                "script" to script.take(4_000),
                "enabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun toggleTwinAutomation(userId: String, automationId: String, enabled: Boolean) {
        db.collection("users").document(userId).collection("twinAutomations").document(automationId)
            .update(mapOf("enabled" to enabled, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun deleteTwinAutomation(userId: String, automationId: String) {
        db.collection("users").document(userId).collection("twinAutomations").document(automationId).delete().await()
    }

    suspend fun createTwinRender(userId: String, title: String, script: String, language: String, gestures: List<String>) {
        val value = script.trim()
        require(value.isNotBlank() && value.length <= 4_000)
        val sequence = gestures.take(40).map { gesture -> mapOf("gesture" to gesture, "duration" to 3, "label" to gesture) }
        db.collection("users").document(userId).collection("twinRenders").add(
            mapOf(
                "userId" to userId,
                "title" to title.trim().ifBlank { "Séquence WHAPPY" },
                "script" to value,
                "language" to language,
                "voiceMode" to "owner-voice-sample",
                "sequence" to sequence,
                "status" to "prepared",
                "disclosure" to "Créé avec le Double IA de son propriétaire",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun markRead(conversationId: String, userId: String) {
        db.collection("conversations").document(conversationId)
            .update("readBy.$userId", FieldValue.serverTimestamp())
            .await()
    }

    suspend fun findUserByPhone(phone: String): WhappyMember? {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return null
        val snapshot = db.collection("users")
            .whereEqualTo("phoneNumber", normalized)
            .limit(1)
            .get()
            .await()
        val document = snapshot.documents.firstOrNull() ?: return null
        return WhappyMember(
            uid = document.id,
            displayName = document.getString("displayName")?.ifBlank { "Contact WHAPPY" } ?: "Contact WHAPPY",
            phoneNumber = document.getString("phoneNumber").orEmpty(),
        )
    }

    suspend fun ensureDirectConversation(current: WhappyMember, peer: WhappyMember): WhappyConversation {
        require(current.uid != peer.uid)
        val id = "direct-${listOf(current.uid, peer.uid).sorted().joinToString("-")}" 
        val reference = db.collection("conversations").document(id)
        if (!reference.get().await().exists()) {
            reference.set(
                mapOf(
                    "ownerId" to current.uid,
                    "memberIds" to listOf(current.uid, peer.uid).sorted(),
                    "members" to listOf(
                        mapOf("uid" to current.uid, "displayName" to current.displayName, "phoneNumber" to current.phoneNumber),
                        mapOf("uid" to peer.uid, "displayName" to peer.displayName, "phoneNumber" to peer.phoneNumber),
                    ),
                    "typingBy" to emptyMap<String, Boolean>(),
                    "readBy" to emptyMap<String, Any>(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        return WhappyConversation(id, peer, "Nouvelle conversation", System.currentTimeMillis(), false)
    }

    fun signOut() = auth.signOut()

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toConversation(userId: String): WhappyConversation? {
        val ids = get("memberIds") as? List<String> ?: return null
        if (userId !in ids) return null
        val rawMembers = get("members") as? List<Map<String, Any?>>
        val peerMap = rawMembers?.firstOrNull { it["uid"] != userId }
        val peer = if (peerMap != null) {
            WhappyMember(
                uid = peerMap["uid"]?.toString().orEmpty(),
                displayName = peerMap["displayName"]?.toString()?.ifBlank { "Contact WHAPPY" } ?: "Contact WHAPPY",
                phoneNumber = peerMap["phoneNumber"]?.toString().orEmpty(),
            )
        } else {
            WhappyMember(
                uid = getString("contactId").orEmpty(),
                displayName = getString("contactName")?.ifBlank { "Contact WHAPPY" } ?: "Contact WHAPPY",
            )
        }
        val readBy = get("readBy") as? Map<String, Any?>
        val readAt = (readBy?.get(userId) as? Timestamp)?.toDate()?.time ?: 0L
        val updatedAt = timestampMillis("updatedAt")
        return WhappyConversation(
            id = id,
            peer = peer,
            lastMessage = getString("lastMessage") ?: "Nouvelle conversation",
            updatedAt = updatedAt,
            unread = updatedAt > readAt && getString("lastSenderId") != userId,
        )
    }

    private fun DocumentSnapshot.timestampMillis(field: String): Long =
        getTimestamp(field)?.toDate()?.time ?: 0L

    private fun normalizePhone(value: String): String {
        return PhoneNumberFormatter.normalize("+242", value).orEmpty()
    }
}
