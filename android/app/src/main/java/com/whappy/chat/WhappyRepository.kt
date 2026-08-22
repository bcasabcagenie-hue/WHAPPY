package com.whappy.chat

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.UUID

class WhappyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val appContext: Context = FirebaseApp.getInstance().applicationContext,
) {
    private val messageOutbox = WhappyMessageOutbox(appContext)
    private val messageCache = WhappyMessageCache(appContext)

    fun currentUser() = auth.currentUser

    fun schedulePendingMessageSync() = WhappyMessageSync.schedule(appContext)

    suspend fun pendingMessages(conversationId: String, userId: String): List<WhappyMessage> =
        messageOutbox.pending().asSequence()
            .filter { it.conversationId == conversationId && it.senderId == userId }
            .map { pending ->
                WhappyMessage(
                    id = pending.id,
                    text = pending.text,
                    senderId = pending.senderId,
                    createdAt = pending.createdAt,
                    replyToId = pending.replyToId,
                    replyText = pending.replyText,
                    kind = pending.kind,
                    mediaUrl = pending.localMediaPath,
                    mediaName = pending.mediaName,
                    durationSeconds = pending.durationSeconds,
                    deliveryState = if (pending.attempts > 1) "retrying" else "queued",
                )
            }
            .toList()

    fun cachedMessages(conversationId: String, source: String = "conversations"): List<WhappyMessage> =
        messageCache.read(conversationId, source)

    suspend fun syncAccountRecord(user: FirebaseUser, displayName: String = user.displayName.orEmpty()) {
        val phone = user.phoneNumber.orEmpty()
        val normalized = PhoneNumberFormatter.normalize("+242", phone).orEmpty()
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to displayName.trim(),
                "phoneNumber" to phone,
                "phoneLookup" to normalized,
                "phoneDigits" to phone.filter(Char::isDigit),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

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

    suspend fun restoreAccountPhotoUrl(user: FirebaseUser): String {
        val reference = db.collection("users").document(user.uid)
        val storedUrl = runCatching { reference.get().await().getString("photoUrl").orEmpty().trim() }
            .getOrDefault("")
        val firebaseUrl = user.photoUrl?.toString().orEmpty().trim()
        val resolvedUrl = storedUrl.ifBlank { firebaseUrl }
        if (resolvedUrl.isNotBlank() && resolvedUrl != firebaseUrl) {
            val update = UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(resolvedUrl)).build()
            runCatching { user.updateProfile(update).await() }
        }
        return resolvedUrl
    }

    suspend fun updateProfilePhoto(userId: String, uri: Uri, contentType: String): String {
        require(auth.currentUser?.uid == userId)
        val safeContentType = normalizeImageContentType(contentType)
        require(safeContentType in setOf("image/jpeg", "image/png", "image/webp"))
        val extension = when (safeContentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val objectRef = storage.reference.child("profiles/$userId/avatar-${UUID.randomUUID()}.$extension")
        val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(safeContentType).build()
        objectRef.putFile(uri, metadata).await()
        val downloadUrl = objectRef.downloadUrl.await().toString()
        db.collection("users").document(userId).set(
            mapOf(
                "uid" to userId,
                "photoUrl" to downloadUrl,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
        auth.currentUser?.let { user ->
            val update = UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(downloadUrl)).build()
            runCatching { user.updateProfile(update).await() }
        }
        return downloadUrl
    }

    private fun normalizeImageContentType(type: String): String = when (type.lowercase(Locale.ROOT)) {
        "image/png", "image/webp", "image/jpeg", "image/jpg", "image/pjpeg" -> when (type.lowercase(Locale.ROOT)) {
            "image/jpg", "image/pjpeg" -> "image/jpeg"
            else -> type.lowercase(Locale.ROOT)
        }
        "image/heic", "image/heif", "image/heics", "image/heifs", "image/avif" -> "image/jpeg"
        else -> if (type.startsWith("image/")) "image/jpeg" else "image/jpeg"
    }

    fun observeConversations(
        userId: String,
        onChange: (List<WhappyConversation>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration {
        val profileListeners = mutableMapOf<String, ListenerRegistration>()
        var conversations = emptyList<WhappyConversation>()
        var webGroups = emptyList<WhappyConversation>()
        var removed = false
        fun emit() {
            if (!removed) onChange(
                (conversations + webGroups)
                    .distinctBy { "${it.source}:${it.id}" }
                    .sortedByDescending { it.updatedAt },
            )
        }
        val conversationListener = db.collection("conversations")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            conversations = snapshot?.documents.orEmpty()
                .mapNotNull { it.toConversation(userId) }
                .sortedByDescending { it.updatedAt }
            emit()

            val peerIds = conversations.asSequence()
                .filterNot { it.isGroup }
                .map { it.peer.uid }
                .filter { it.isNotBlank() && it != userId }
                .toSet()
            (profileListeners.keys - peerIds).forEach { peerId ->
                profileListeners.remove(peerId)?.remove()
            }
            (peerIds - profileListeners.keys).forEach { peerId ->
                profileListeners[peerId] = db.collection("users").document(peerId)
                    .addSnapshotListener { profile, _ ->
                        if (removed || profile == null || !profile.exists()) return@addSnapshotListener
                        val liveMember = profile.toMember()
                        conversations = conversations.map { conversation ->
                            if (!conversation.isGroup && conversation.peer.uid == peerId) {
                                conversation.copy(peer = liveMember)
                            } else conversation
                        }
                        emit()
                    }
            }
        }
        val groupListener = db.collection("groups")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                webGroups = snapshot?.documents.orEmpty().mapNotNull { it.toGroupConversation(userId) }
                emit()
            }
        return object : ListenerRegistration {
            override fun remove() {
                removed = true
                conversationListener.remove()
                groupListener.remove()
                profileListeners.values.forEach { it.remove() }
                profileListeners.clear()
            }
        }
    }

    fun observeContacts(
        userId: String,
        onChange: (List<WhappyContact>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("users").document(userId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val contacts = snapshot?.get("contacts") as? Map<String, Map<String, Any?>> ?: emptyMap()
            onChange(contacts.mapNotNull { (uid, value) ->
                val name = value["displayName"]?.toString()?.trim().orEmpty()
                val phone = value["phoneNumber"]?.toString().orEmpty()
                if (uid.isBlank() || name.isBlank()) null else WhappyContact(
                    member = WhappyMember(
                        uid = uid,
                        displayName = WhappyIdentity.resolveAccountName(name, phone),
                        phoneNumber = phone,
                        photoUrl = value["photoUrl"]?.toString().orEmpty(),
                    ),
                    addedAt = (value["addedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                )
            }.sortedBy { it.member.displayName.lowercase() })
        }

    fun observeMessages(
        conversationId: String,
        source: String = "conversations",
        onChange: (List<WhappyMessage>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection(if (source == "groups") "groups" else "conversations")
        .document(conversationId)
        .collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val messages = snapshot?.documents.orEmpty().map { document ->
                val reply = document.get("replyTo") as? Map<*, *>
                WhappyMessage(
                    id = document.id,
                    text = document.getString("text").orEmpty(),
                    senderId = document.getString("senderId").orEmpty(),
                    createdAt = document.timestampMillis("createdAt"),
                    kind = document.getString("kind") ?: "text",
                    mediaUrl = document.getString("mediaUrl").orEmpty(),
                    mediaName = document.getString("mediaName").orEmpty(),
                    durationSeconds = document.getLong("duration")?.toInt() ?: 0,
                    replyToId = document.getString("replyToId") ?: reply?.get("id")?.toString().orEmpty(),
                    replyText = document.getString("replyText") ?: reply?.get("text")?.toString().orEmpty(),
                    reactions = (document.get("reactions") as? Map<*, *>)?.mapNotNull { (key, value) -> if (key != null && value != null) key.toString() to value.toString() else null }?.toMap().orEmpty(),
                    deleted = document.getBoolean("deleted") == true,
                    edited = document.getBoolean("edited") == true,
                    deliveryState = "sent",
                    senderName = document.getString("senderName").orEmpty(),
                )
            }
            messageCache.write(conversationId, source, messages)
            onChange(messages)
        }

    fun observeChannels(
        onChange: (List<WhappyChannel>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("channels")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .limit(100)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().mapNotNull { document ->
                val name = document.getString("name").orEmpty()
                val ownerId = document.getString("ownerId").orEmpty()
                if (name.isBlank() || ownerId.isBlank()) null else WhappyChannel(
                    id = document.id,
                    name = name,
                    description = document.getString("description").orEmpty(),
                    category = document.getString("category") ?: "Communauté",
                    ownerId = ownerId,
                    ownerName = document.getString("ownerName") ?: "Créateur WAPI",
                    memberIds = (document.get("memberIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty(),
                    memberCount = document.getLong("memberCount")?.toInt() ?: 1,
                    postCount = document.getLong("postCount")?.toInt() ?: 0,
                    lastPost = document.getString("lastPost").orEmpty(),
                    updatedAt = document.timestampMillis("updatedAt"),
                    verified = document.getBoolean("verified") == true,
                )
            })
        }

    fun observeChannelPosts(
        channelId: String,
        onChange: (List<WhappyChannelPost>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("channels").document(channelId).collection("posts")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .limit(300)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().map { document ->
                WhappyChannelPost(
                    id = document.id,
                    text = document.getString("text").orEmpty(),
                    authorId = document.getString("authorId").orEmpty(),
                    authorName = document.getString("authorName") ?: "WAPI",
                    createdAt = document.timestampMillis("createdAt"),
                    reactions = (document.get("reactions") as? Map<*, *>)?.mapNotNull { (key, value) -> if (key != null && value != null) key.toString() to value.toString() else null }?.toMap().orEmpty(),
                    pinned = document.getBoolean("pinned") == true,
                    deleted = document.getBoolean("deleted") == true,
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
                title = document.getString("title") ?: "Annonce WAPI",
                price = document.getString("price") ?: "Prix à discuter",
                place = document.getString("place") ?: "Brazzaville",
                seller = document.getString("seller") ?: "Vendeur WAPI",
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
            onChange(snapshot?.documents.orEmpty().map { it.toBusinessPage() })
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
                    pageName = document.getString("pageName") ?: "Page WAPI",
                    objective = document.getString("objective") ?: "reach",
                    title = document.getString("title") ?: "Campagne WAPI",
                    dailyBudget = document.getLong("dailyBudget") ?: 0L,
                    days = document.getLong("days")?.toInt() ?: 1,
                    status = document.getString("status") ?: "active",
                    ownerId = document.getString("ownerId").orEmpty(),
                    placement = document.getString("placement") ?: "profile_story",
                    destination = document.getString("destination") ?: "message",
                    creative = document.getString("creative").orEmpty(),
                    cta = document.getString("cta") ?: "Nous contacter",
                    audience = document.getString("audience") ?: "Public local",
                    city = document.getString("city").orEmpty(),
                    phone = document.getString("phone").orEmpty(),
                    link = document.getString("link").orEmpty(),
                    estimatedReach = document.getLong("estimatedReach") ?: 0L,
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
                    hostName = document.getString("hostName") ?: "Créateur WAPI",
                    title = document.getString("title") ?: "Direct WAPI",
                    category = document.getString("category") ?: "Communauté",
                    productTitle = document.getString("productTitle").orEmpty(),
                    status = document.getString("status") ?: "scheduled",
                    viewerCount = document.getLong("viewerCount")?.toInt() ?: 0,
                    startedAt = document.timestampMillis("startedAt"),
                    hostMode = document.getString("hostMode") ?: "personal",
                    visibility = document.getString("visibility") ?: "public",
                    streamProvider = document.getString("streamProvider") ?: "unconfigured",
                    streamRoomId = document.getString("streamRoomId").orEmpty(),
                )
            }.sortedWith(compareByDescending<WhappyLive> { it.status == "live" }.thenByDescending { it.startedAt }))
        }

    fun observeStatuses(
        onChange: (List<WhappyStatus>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration {
        val viewerId = auth.currentUser?.uid.orEmpty()
        if (viewerId.isBlank()) return db.collection("stories").limit(1).addSnapshotListener { _, _ -> onChange(emptyList()) }
        return db.collection("stories")
        .whereArrayContains("audienceIds", viewerId)
        .whereGreaterThan("expiresAt", com.google.firebase.Timestamp.now())
        .limit(80)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().mapNotNull { document ->
                val authorId = document.getString("authorId").orEmpty()
                val caption = document.getString("caption").orEmpty()
                val mediaUrl = document.getString("mediaUrl").orEmpty()
                if (authorId.isBlank() || (caption.isBlank() && mediaUrl.isBlank())) null else WhappyStatus(
                    id = document.id,
                    authorId = authorId,
                    authorName = document.getString("authorName") ?: WhappyIdentity.fallbackAccountName,
                    text = caption,
                    tone = "personal",
                    createdAt = document.timestampMillis("createdAt"),
                    mediaUrl = mediaUrl,
                    mediaKind = document.getString("mediaType").orEmpty(),
                    mediaName = when (document.getString("mediaType")) { "audio" -> "Podcast WAPI"; "video" -> "Vidéo WAPI"; else -> "Image WAPI" },
                )
            }.sortedByDescending { it.createdAt })
        }
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
                    pageName = document.getString("pageName") ?: "Page WAPI",
                    ownerId = document.getString("ownerId").orEmpty(),
                    title = document.getString("title") ?: "Deal WAPI",
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
                    buyerName = document.getString("buyerName") ?: "Client WAPI",
                    amount = document.getLong("amount") ?: 0L,
                    currency = document.getString("currency") ?: "XAF",
                    provider = document.getString("provider") ?: "Paiement WAPI",
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

    fun observeWepiSettings(
        userId: String,
        onChange: (WapiWepiSettings?) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("users").document(userId)
        .collection("wepi").document("settings")
        .addSnapshotListener { document, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (document == null || !document.exists()) {
                onChange(null)
            } else {
                onChange(WapiWepiSettings(
                    ownerId = userId,
                    enabled = document.getBoolean("enabled") ?: false,
                    autoReply = document.getBoolean("autoReply") ?: true,
                    assistantName = document.getString("assistantName") ?: "WEPI",
                    businessName = document.getString("businessName").orEmpty(),
                    tone = document.getString("tone") ?: "chaleureux",
                    welcomeMessage = document.getString("welcomeMessage") ?: "Bonjour et merci pour votre message.",
                    instructions = document.getString("instructions") ?: "Répondre clairement aux questions commerciales et proposer un échange humain si nécessaire.",
                ))
            }
        }

    fun observeRadioEpisodes(
        onChange: (List<WapiRadioEpisode>) -> Unit,
        onError: (Throwable) -> Unit,
    ): ListenerRegistration = db.collection("radioEpisodes")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(80)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(snapshot?.documents.orEmpty().mapNotNull { document ->
                val ownerId = document.getString("ownerId").orEmpty()
                val audioUrl = document.getString("audioUrl").orEmpty()
                if (ownerId.isBlank() || audioUrl.isBlank()) null else WapiRadioEpisode(
                    id = document.id,
                    ownerId = ownerId,
                    authorName = document.getString("authorName") ?: WhappyIdentity.fallbackAccountName,
                    stationName = document.getString("stationName") ?: "WAPI Radio",
                    title = document.getString("title") ?: "Émission WAPI",
                    audioUrl = audioUrl,
                    durationSeconds = document.getLong("durationSeconds") ?: 0L,
                    createdAt = document.timestampMillis("createdAt"),
                )
            })
        }

    suspend fun publishRadioEpisode(userId: String, authorName: String, stationName: String, title: String, fileUri: Uri, durationSeconds: Long) {
        require(auth.currentUser?.uid == userId)
        require(stationName.trim().length in 2..60)
        require(title.trim().length in 2..100)
        require(durationSeconds in 1..3_600)
        val path = "radio/$userId/${System.currentTimeMillis()}-${UUID.randomUUID()}.m4a"
        val mediaRef = storage.reference.child(path)
        mediaRef.putFile(fileUri, com.google.firebase.storage.StorageMetadata.Builder().setContentType("audio/mp4").build()).await()
        val audioUrl = mediaRef.downloadUrl.await().toString()
        db.collection("radioEpisodes").add(mapOf(
            "ownerId" to userId,
            "authorName" to authorName.trim().take(80),
            "stationName" to stationName.trim().take(60),
            "title" to title.trim().take(100),
            "audioUrl" to audioUrl,
            "storagePath" to path,
            "durationSeconds" to durationSeconds,
            "status" to "published",
            "createdAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    suspend fun saveWepiSettings(settings: WapiWepiSettings) {
        require(auth.currentUser?.uid == settings.ownerId)
        val safe = settings.copy(
            assistantName = settings.assistantName.trim().take(60).ifBlank { "WEPI" },
            businessName = settings.businessName.trim().take(100),
            tone = settings.tone.takeIf { it in setOf("chaleureux", "expert", "direct") } ?: "chaleureux",
            welcomeMessage = settings.welcomeMessage.trim().take(240),
            instructions = settings.instructions.trim().take(600),
        )
        db.collection("users").document(settings.ownerId).collection("wepi").document("settings").set(
            mapOf(
                "ownerId" to safe.ownerId,
                "enabled" to safe.enabled,
                "autoReply" to safe.autoReply,
                "assistantName" to safe.assistantName,
                "businessName" to safe.businessName,
                "tone" to safe.tone,
                "welcomeMessage" to safe.welcomeMessage,
                "instructions" to safe.instructions,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        db.collection("users").document(settings.ownerId).set(
            mapOf("wepiEnabled" to safe.enabled, "wepiName" to safe.assistantName, "wepiBusinessName" to safe.businessName),
            SetOptions.merge(),
        ).await()
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
                    name = document.getString("name") ?: "Mission WAPI",
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
                    title = document.getString("title") ?: "Production WAPI",
                    script = document.getString("script").orEmpty(),
                    language = document.getString("language") ?: "fr-FR",
                    status = document.getString("status") ?: "prepared",
                )
            })
        }

    suspend fun sendMessage(conversationId: String, userId: String, text: String, replyToId: String = "", replyText: String = "", source: String = "conversations", senderName: String = "Membre WAPI"): WhappyDeliveryResult {
        val value = text.trim()
        require(value.isNotEmpty() && value.length <= 4_000)
        require(auth.currentUser?.uid == userId)
        if (source == "groups") {
            withTimeout(4_500L) {
                val group = db.collection("groups").document(conversationId)
                group.collection("messages").add(
                    buildMap<String, Any> {
                        put("text", value)
                        put("senderId", userId)
                        put("senderName", senderName.trim().take(80).ifBlank { "Membre WAPI" })
                        put("createdAt", FieldValue.serverTimestamp())
                        if (replyToId.isNotBlank()) {
                            put("replyToId", replyToId)
                            put("replyText", replyText.take(240))
                            put("replyTo", mapOf("id" to replyToId, "senderName" to "Membre", "text" to replyText.take(240)))
                        }
                    }
                ).await()
                group.update(mapOf("lastMessage" to value, "updatedAt" to FieldValue.serverTimestamp())).await()
            }
            return WhappyDeliveryResult.SENT
        }
        val pending = WhappyPendingMessage(
            id = db.collection("conversations").document(conversationId).collection("messages").document().id,
            conversationId = conversationId,
            senderId = userId,
            text = value,
            replyToId = replyToId,
            replyText = replyText.take(240),
            createdAt = System.currentTimeMillis(),
        )
        messageOutbox.enqueue(pending)
        return runCatching {
            withTimeout(4_500L) { deliverPendingMessage(pending) }
            messageOutbox.remove(pending.id)
            WhappyDeliveryResult.SENT
        }.getOrElse {
            messageOutbox.markAttempt(pending.id)
            WhappyMessageSync.schedule(appContext)
            WhappyDeliveryResult.QUEUED
        }
    }

    suspend fun flushPendingMessages(): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        val pendingMessages = messageOutbox.pending()
        for (pending in pendingMessages) {
            if (pending.senderId != currentUserId) continue
            val delivered = runCatching { withTimeout(4_500L) { deliverPendingMessage(pending) } }.isSuccess
            if (!delivered) {
                messageOutbox.markAttempt(pending.id)
                return false
            }
            messageOutbox.remove(pending.id)
            if (pending.localMediaPath.isNotBlank()) File(pending.localMediaPath).delete()
        }
        return true
    }

    private suspend fun deliverPendingMessage(message: WhappyPendingMessage) {
        val root = if (message.source == "groups") "groups" else "conversations"
        val conversation = db.collection(root).document(message.conversationId)
        val mediaUrl = if (message.kind in setOf("image", "audio", "video")) {
            val file = File(message.localMediaPath)
            require(file.isFile && file.length() > 0L) { "attachment-missing" }
            val extension = message.mediaName.substringAfterLast('.', "bin").take(8)
            val objectName = "${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
            val objectRef = storage.reference.child("$root/${message.conversationId}/${message.senderId}/$objectName")
            objectRef.putFile(
                Uri.fromFile(file),
                com.google.firebase.storage.StorageMetadata.Builder().setContentType(message.contentType).build(),
            ).await()
            objectRef.downloadUrl.await().toString()
        } else ""
        conversation.collection("messages").document(message.id).set(
            buildMap<String, Any> {
                put("text", message.text)
                put("senderId", message.senderId)
                if (message.source == "groups") put("senderName", message.senderName)
                put("createdAt", Timestamp(Date(message.createdAt)))
                put("clientMessageId", message.id)
                if (message.kind != "text") {
                    put("kind", message.kind)
                    put("mediaUrl", mediaUrl)
                    put("mediaName", message.mediaName)
                    put("duration", message.durationSeconds)
                }
                if (message.replyToId.isNotBlank()) {
                    put("replyToId", message.replyToId)
                    put("replyText", message.replyText)
                }
            },
        ).await()
        conversation.update(buildMap<String, Any> {
            put("lastMessage", message.text)
            put("updatedAt", FieldValue.serverTimestamp())
            if (message.source != "groups") {
                put("lastSenderId", message.senderId)
                put("typingBy.${message.senderId}", false)
            }
        }).await()
    }

    suspend fun reactToMessage(conversationId: String, messageId: String, userId: String, emoji: String, source: String = "conversations") {
        require(emoji in setOf("❤️", "👍", "😂", "😮", "🙏"))
        db.collection(if (source == "groups") "groups" else "conversations").document(conversationId).collection("messages").document(messageId)
            .update("reactions.$userId", emoji).await()
    }

    suspend fun deleteMessage(conversationId: String, messageId: String, userId: String, source: String = "conversations") {
        val reference = db.collection(if (source == "groups") "groups" else "conversations").document(conversationId).collection("messages").document(messageId)
        val snapshot = reference.get().await()
        require(snapshot.getString("senderId") == userId)
        reference.update(mapOf("text" to "Message supprimé", "kind" to "deleted", "mediaUrl" to "", "mediaName" to "", "deleted" to true)).await()
    }

    suspend fun editMessage(conversationId: String, messageId: String, userId: String, text: String, source: String = "conversations") {
        val value = text.trim()
        require(value.isNotEmpty() && value.length <= 4_000)
        val reference = db.collection(if (source == "groups") "groups" else "conversations").document(conversationId).collection("messages").document(messageId)
        val snapshot = reference.get().await()
        require(snapshot.getString("senderId") == userId && (snapshot.getString("kind") ?: "text") == "text")
        reference.update(mapOf("text" to value, "edited" to true)).await()
    }

    suspend fun setTyping(conversationId: String, userId: String, typing: Boolean, source: String = "conversations") {
        if (source == "groups") return
        db.collection("conversations").document(conversationId).update("typingBy.$userId", typing).await()
    }

    suspend fun createChannel(userId: String, ownerName: String, name: String, description: String, category: String): WhappyChannel {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        require(cleanName.length in 3..80 && cleanDescription.length in 10..300)
        val reference = db.collection("channels").document()
        reference.set(
            mapOf(
                "name" to cleanName,
                "description" to cleanDescription,
                "category" to category.take(40),
                "ownerId" to userId,
                "ownerName" to ownerName.take(80),
                "memberIds" to listOf(userId),
                "memberCount" to 1,
                "postCount" to 0,
                "lastPost" to "Bienvenue sur $cleanName",
                "verified" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return WhappyChannel(
            id = reference.id,
            name = cleanName,
            description = cleanDescription,
            category = category.take(40),
            ownerId = userId,
            ownerName = ownerName.take(80),
            memberIds = listOf(userId),
            memberCount = 1,
            postCount = 0,
            lastPost = "Bienvenue sur $cleanName",
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun createGroup(
        current: WhappyMember,
        name: String,
        selectedMembers: List<WhappyMember>,
        photoUri: Uri? = null,
        photoContentType: String = "image/jpeg",
    ): WhappyConversation {
        val cleanName = name.trim()
        val members = (listOf(current) + selectedMembers)
            .distinctBy { it.uid }
            .take(64)
        require(cleanName.length in 2..80 && members.size >= 3)
        val reference = db.collection("groups").document()
        var groupPhotoUrl = ""
        try {
            reference.set(
                mapOf(
                    "name" to cleanName,
                    "description" to "Groupe WAPI créé depuis l’application mobile",
                    "mark" to cleanName.split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifBlank { "WG" },
                    "ownerId" to current.uid,
                    "memberIds" to listOf(current.uid),
                    "memberNames" to selectedMembers.map { it.displayName.take(80) }.distinct(),
                    "kind" to "community",
                    "inviteToken" to UUID.randomUUID().toString().replace("-", ""),
                    "lastMessage" to "Groupe créé",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            if (photoUri != null) {
                val safeContentType = normalizeImageContentType(photoContentType)
                val extension = when (safeContentType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val photoRef = storage.reference.child("groups/${reference.id}/${current.uid}/cover-${UUID.randomUUID()}.$extension")
                val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(safeContentType).build()
                photoRef.putFile(photoUri, metadata).await()
                groupPhotoUrl = photoRef.downloadUrl.await().toString()
            }
            reference.update(
                buildMap<String, Any> {
                    put("memberIds", members.map { it.uid })
                    put("memberNames", members.map { it.displayName.take(80) })
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (groupPhotoUrl.isNotBlank()) put("photoUrl", groupPhotoUrl)
                },
            ).await()
        } catch (error: Throwable) {
            runCatching { reference.delete().await() }
            throw error
        }
        return WhappyConversation(
            id = reference.id,
            peer = WhappyMember(reference.id, cleanName, photoUrl = groupPhotoUrl),
            lastMessage = "Groupe créé",
            updatedAt = System.currentTimeMillis(),
            unread = false,
            isGroup = true,
            memberCount = members.size,
            source = "groups",
            groupOwnerId = current.uid,
        )
    }

    suspend fun updateGroup(
        groupId: String,
        userId: String,
        actorName: String,
        name: String,
        photoUri: Uri? = null,
        photoContentType: String = "image/jpeg",
        removePhoto: Boolean = false,
    ): WhappyConversation {
        val group = db.collection("groups").document(groupId)
        val snapshot = group.get().await()
        val ownerId = snapshot.getString("ownerId").orEmpty()
        val memberIds = (snapshot.get("memberIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        require(ownerId == userId && userId in memberIds)
        val cleanName = name.trim()
        require(cleanName.length in 2..80)
        val oldName = snapshot.getString("name").orEmpty()
        val changed = buildList {
            if (cleanName != oldName) add("nom")
            if (photoUri != null) add("photo")
            if (removePhoto && snapshot.getString("photoUrl").orEmpty().isNotBlank()) add("photo")
        }
        require(changed.isNotEmpty())
        var photoUrl = snapshot.getString("photoUrl").orEmpty()
        if (photoUri != null) {
            val safeContentType = normalizeImageContentType(photoContentType)
            val extension = when (safeContentType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val photoRef = storage.reference.child("groups/$groupId/$userId/cover-${UUID.randomUUID()}.$extension")
            val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(safeContentType).build()
            photoRef.putFile(photoUri, metadata).await()
            photoUrl = photoRef.downloadUrl.await().toString()
        } else if (removePhoto) photoUrl = ""
        val action = when {
            changed.contains("nom") && changed.contains("photo") -> "a modifié le nom et la photo du groupe"
            changed.contains("nom") -> "a renommé le groupe en « $cleanName »"
            removePhoto -> "a supprimé la photo du groupe"
            else -> "a changé la photo du groupe"
        }
        val eventText = "${actorName.trim().take(80).ifBlank { "Un administrateur" }} $action"
        val message = group.collection("messages").document()
        val batch = db.batch()
        batch.update(group, mapOf("name" to cleanName, "photoUrl" to photoUrl, "lastMessage" to eventText, "updatedAt" to FieldValue.serverTimestamp()))
        batch.set(message, mapOf("text" to eventText, "senderId" to userId, "senderName" to actorName.trim().take(80).ifBlank { "Administrateur" }, "kind" to "system", "createdAt" to FieldValue.serverTimestamp(), "deleted" to false))
        batch.commit().await()
        return WhappyConversation(groupId, WhappyMember(groupId, cleanName, photoUrl = photoUrl), eventText, System.currentTimeMillis(), false, isGroup = true, memberCount = memberIds.size, source = "groups", groupOwnerId = ownerId)
    }

    suspend fun setChannelSubscription(channelId: String, userId: String, subscribed: Boolean) {
        val reference = db.collection("channels").document(channelId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val members = (snapshot.get("memberIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
            val ownerId = snapshot.getString("ownerId").orEmpty()
            require(userId != ownerId || subscribed)
            val next = if (subscribed) (members + userId).distinct() else members.filterNot { it == userId }
            if (next != members) transaction.update(reference, mapOf("memberIds" to next, "memberCount" to next.size, "updatedAt" to FieldValue.serverTimestamp()))
            null
        }.await()
    }

    suspend fun publishChannelPost(channelId: String, userId: String, authorName: String, text: String) {
        val value = text.trim()
        require(value.length in 1..4_000)
        val channel = db.collection("channels").document(channelId)
        val post = channel.collection("posts").document()
        val batch = db.batch()
        batch.set(post, mapOf("text" to value, "authorId" to userId, "authorName" to authorName.take(80), "createdAt" to FieldValue.serverTimestamp(), "reactions" to emptyMap<String, String>(), "pinned" to false, "deleted" to false))
        batch.update(channel, mapOf("lastPost" to value.take(160), "postCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
    }

    suspend fun reactToChannelPost(channelId: String, postId: String, userId: String, emoji: String) {
        require(emoji in setOf("❤️", "👍", "🔥", "👏", "💡"))
        db.collection("channels").document(channelId).collection("posts").document(postId).update("reactions.$userId", emoji).await()
    }

    suspend fun pinChannelPost(channelId: String, postId: String, pinned: Boolean) {
        db.collection("channels").document(channelId).collection("posts").document(postId).update("pinned", pinned).await()
    }

    suspend fun deleteChannelPost(channelId: String, postId: String) {
        db.collection("channels").document(channelId).collection("posts").document(postId).update(mapOf("text" to "Publication supprimée", "deleted" to true, "pinned" to false)).await()
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        userId: String,
        uri: Uri,
        kind: String,
        contentType: String,
        mediaName: String,
        durationSeconds: Int = 0,
        source: String = "conversations",
        senderName: String = "Membre WAPI",
    ): WhappyDeliveryResult {
        require(kind in setOf("image", "audio", "video"))
        require(if (kind == "image") contentType.startsWith("image/") else if (kind == "video") contentType.startsWith("video/") else contentType.startsWith("audio/"))
        val mediaSize = runCatching { appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        val maximumSize = when (kind) { "image" -> 20L * 1024L * 1024L; "video" -> 60L * 1024L * 1024L; else -> 12L * 1024L * 1024L }
        require(mediaSize < 0L || mediaSize in 1..maximumSize)
        val extension = when (kind) {
            "audio" -> "m4a"
            "video" -> "mp4"
            else -> contentType.substringAfter('/', "jpg").substringBefore('+').take(8)
        }
        val safeName = mediaName.trim().take(120).ifBlank { "whappy-${kind}.${extension}" }
        val label = when (kind) {
            "audio" -> "Note vocale"
            "video" -> "Vidéo"
            else -> "Photo"
        }
        val localFile = WapiMediaStore.copyToOutbox(appContext, uri, kind, safeName)
            ?: error("attachment-unreadable")
        val pending = WhappyPendingMessage(
            id = db.collection(if (source == "groups") "groups" else "conversations")
                .document(conversationId).collection("messages").document().id,
            conversationId = conversationId,
            senderId = userId,
            text = label,
            replyToId = "",
            replyText = "",
            createdAt = System.currentTimeMillis(),
            kind = kind,
            localMediaPath = localFile.absolutePath,
            contentType = contentType,
            mediaName = safeName,
            durationSeconds = durationSeconds.coerceIn(0, 600),
            source = source,
            senderName = senderName.trim().take(80).ifBlank { "Membre WAPI" },
        )
        messageOutbox.enqueue(pending)
        return runCatching {
            withTimeout(15_000L) { deliverPendingMessage(pending) }
            messageOutbox.remove(pending.id)
            localFile.delete()
            WhappyDeliveryResult.SENT
        }.getOrElse {
            messageOutbox.markAttempt(pending.id)
            WhappyMessageSync.schedule(appContext)
            WhappyDeliveryResult.QUEUED
        }
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
                "searchName" to value.lowercase(),
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
                "searchName" to name.trim().lowercase(),
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
                "placement" to draft.placement,
                "destination" to draft.destination,
                "title" to draft.title.trim(),
                "creative" to draft.creative.trim(),
                "cta" to draft.cta.trim().take(40),
                "audience" to draft.audience.trim().take(120),
                "city" to draft.city.trim().take(80),
                "phone" to draft.phone.trim().take(40),
                "link" to draft.link.trim().take(180),
                "dailyBudget" to draft.dailyBudget,
                "days" to draft.days,
                "totalBudget" to draft.dailyBudget * draft.days,
                "estimatedReach" to ((draft.dailyBudget / 500L) * draft.days * if (draft.placement == "profile_story") 180L else 120L).coerceAtLeast(120L),
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun createLive(userId: String, hostName: String, title: String, category: String, productTitle: String, startNow: Boolean, hostMode: String, visibility: String) {
        require(title.trim().length in 2..120)
        require(hostMode in setOf("personal", "creator", "business"))
        require(visibility in setOf("public", "contacts", "private"))
        db.collection("liveSessions").add(
            mapOf(
                "hostId" to userId,
                "hostName" to hostName.trim().take(80),
                "title" to title.trim(),
                "category" to category.trim().take(60),
                "productTitle" to productTitle.trim().take(120),
                "status" to if (startNow) "live" else "scheduled",
                "viewerCount" to 0,
                "streamProvider" to "unconfigured",
                "hostMode" to hostMode,
                "visibility" to visibility,
                "createdAt" to FieldValue.serverTimestamp(),
                "startedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun publishStatus(userId: String, authorName: String, text: String, tone: String, mediaUri: Uri? = null, mediaContentType: String = ""): WhappyStatus {
        require(auth.currentUser?.uid == userId)
        val value = text.trim()
        require(value.length <= 600)
        require(value.isNotBlank() || mediaUri != null)
        // Stories are personal by design. Keep legacy values accepted for old callers,
        // but never expose them as a community/category in the Story UI.
        require(tone == "personal" || tone in setOf("hope", "action", "community", "warning"))
        val mediaKind = when {
            mediaUri == null -> "text"
            mediaContentType.startsWith("image/") -> "image"
            mediaContentType.startsWith("video/") -> "video"
            mediaContentType.startsWith("audio/") -> "audio"
            else -> error("invalid-status-media")
        }
        val mediaSize = mediaUri?.let { uri -> runCatching { appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L) } ?: 0L
        val maximumSize = when (mediaKind) { "video" -> 50L * 1024L * 1024L; "audio" -> 25L * 1024L * 1024L; else -> 12L * 1024L * 1024L }
        require(mediaUri == null || mediaSize < 0L || mediaSize in 1..maximumSize)
        var storagePath = ""
        val mediaUrl = if (mediaUri == null) "" else {
            val extension = mediaContentType.substringAfter('/', when (mediaKind) { "video" -> "mp4"; "audio" -> "m4a"; else -> "jpg" }).substringBefore('+').replace("quicktime", "mov").take(8)
            storagePath = "stories/$userId/${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
            val mediaRef = storage.reference.child(storagePath)
            mediaRef.putFile(mediaUri, com.google.firebase.storage.StorageMetadata.Builder().setContentType(mediaContentType).build()).await()
            mediaRef.downloadUrl.await().toString()
        }
        val createdAt = System.currentTimeMillis()
        val directContacts = db.collection("conversations")
            .whereArrayContains("memberIds", userId)
            .get()
            .await()
            .documents
            .filter { document ->
                val members = (document.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                document.getString("conversationType") == "direct" || members.size == 2
            }
            .flatMap { document -> (document.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty() }
            .filter { it.isNotBlank() }
            .toSet()
        val audienceIds = (directContacts + userId).take(500)
        val story = db.collection("stories").document()
        story.set(
            mapOf(
                "authorId" to userId,
                "authorName" to authorName.trim().take(80),
                "caption" to value,
                "mediaUrl" to mediaUrl,
                "mediaType" to mediaKind,
                "storagePath" to storagePath,
                "audienceIds" to audienceIds,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to com.google.firebase.Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L)),
            ),
        ).await()
        return WhappyStatus(
            id = story.id,
            authorId = userId,
            authorName = authorName.trim().take(80),
            text = value,
            tone = "personal",
            createdAt = createdAt,
            mediaUrl = mediaUrl,
            mediaKind = mediaKind,
            mediaName = when (mediaKind) { "audio" -> "Podcast WAPI"; "video" -> "Vidéo WAPI"; "image" -> "Image WAPI"; else -> "" },
        )
    }

    suspend fun deleteStatus(userId: String, statusId: String) {
        val reference = db.collection("stories").document(statusId)
        val document = reference.get().await()
        require(document.getString("authorId") == userId)
        document.getString("storagePath").orEmpty().takeIf { it.isNotBlank() }?.let { path ->
            runCatching { storage.reference.child(path).delete().await() }
        }
        reference.delete().await()
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
                "title" to title.trim().ifBlank { "Séquence WAPI" },
                "script" to value,
                "language" to language,
                "voiceMode" to "owner-voice-sample",
                "sequence" to sequence,
                "status" to "prepared",
                "disclosure" to "Créé avec le Jumeau numérique IA de son propriétaire",
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
        val candidates = PhoneNumberFormatter.lookupCandidates(phone)
        if (candidates.isEmpty()) return null
        for (candidate in candidates) {
            val candidateDigits = candidate.filter(Char::isDigit)
            listOf("phoneNumber", "phoneLookup", "phoneDigits").forEach { field ->
                val value = if (field == "phoneDigits") candidateDigits else candidate
                val document = db.collection("users")
                    .whereEqualTo(field, value)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                if (document != null) return document.toMember()
            }
        }
        return null
    }

    suspend fun saveContact(userId: String, peer: WhappyMember) {
        db.collection("users").document(userId).set(
            mapOf(
                "contacts" to mapOf(
                    peer.uid to mapOf(
                        "displayName" to peer.displayName,
                        "phoneNumber" to peer.phoneNumber,
                        "photoUrl" to peer.photoUrl,
                        "addedAt" to FieldValue.serverTimestamp(),
                    ),
                ),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun addContactAndEnsureConversation(current: WhappyMember, peer: WhappyMember): WhappyConversation {
        require(current.uid != peer.uid)
        val conversationId = "direct-${listOf(current.uid, peer.uid).sorted().joinToString("-")}"
        val conversation = db.collection("conversations").document(conversationId)
        val user = db.collection("users").document(current.uid)
        // Do not read the conversation before writing it. A new direct document
        // cannot be read by either member until it exists, so a transaction's
        // initial get is rejected by Firestore rules. A merge batch is atomic,
        // creates the document when absent, and preserves an existing thread's
        // messages when the contact is added again.
        val conversationData = mapOf(
            "ownerId" to current.uid,
            "memberIds" to listOf(current.uid, peer.uid).sorted(),
            "members" to listOf(
                mapOf("uid" to current.uid, "displayName" to current.displayName, "phoneNumber" to current.phoneNumber, "photoUrl" to current.photoUrl),
                mapOf("uid" to peer.uid, "displayName" to peer.displayName, "phoneNumber" to peer.phoneNumber, "photoUrl" to peer.photoUrl),
            ),
            "typingBy" to emptyMap<String, Boolean>(),
            "readBy" to emptyMap<String, Any>(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        db.batch().apply {
            set(conversation, conversationData, com.google.firebase.firestore.SetOptions.merge())
            set(
                user,
                mapOf(
                    "contacts" to mapOf(
                        peer.uid to mapOf(
                            "displayName" to peer.displayName,
                            "phoneNumber" to peer.phoneNumber,
                            "photoUrl" to peer.photoUrl,
                            "addedAt" to FieldValue.serverTimestamp(),
                        ),
                    ),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
        }.commit().await()
        return WhappyConversation(conversationId, peer, "Nouvelle conversation", System.currentTimeMillis(), false)
    }

    suspend fun findUserById(userId: String): WhappyMember? {
        val document = db.collection("users").document(userId).get().await()
        return document.takeIf { it.exists() }?.toMember()
    }

    suspend fun searchBusinessPages(query: String): List<WhappyBusinessPage> {
        val needle = SearchNormalizer.normalize(query)
        if (needle.length < 2) return emptyList()
        return db.collection("businessPages")
            .limit(100)
            .get()
            .await()
            .documents
            .map { it.toBusinessPage() }
            .filter { page ->
                SearchNormalizer.matches(query, page.name, page.handle, page.category, page.city, page.bio)
            }
            .sortedWith(compareBy<WhappyBusinessPage> { !SearchNormalizer.normalize(it.name).startsWith(needle) }.thenBy { SearchNormalizer.normalize(it.name) })
            .take(20)
    }

    suspend fun ensureDirectConversation(current: WhappyMember, peer: WhappyMember): WhappyConversation {
        require(current.uid != peer.uid)
        val id = "direct-${listOf(current.uid, peer.uid).sorted().joinToString("-")}" 
        val reference = db.collection("conversations").document(id)
        // The document may not exist yet. An initial get is denied for a
        // non-member, while an atomic merge is allowed to create the direct
        // conversation and is harmless when it already exists.
        reference.set(
            mapOf(
                "ownerId" to current.uid,
                "memberIds" to listOf(current.uid, peer.uid).sorted(),
                "members" to listOf(
                    mapOf("uid" to current.uid, "displayName" to current.displayName, "phoneNumber" to current.phoneNumber, "photoUrl" to current.photoUrl),
                    mapOf("uid" to peer.uid, "displayName" to peer.displayName, "phoneNumber" to peer.phoneNumber, "photoUrl" to peer.photoUrl),
                ),
                "typingBy" to emptyMap<String, Boolean>(),
                "readBy" to emptyMap<String, Any>(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
        return WhappyConversation(id, peer, "Nouvelle conversation", System.currentTimeMillis(), false)
    }

    fun signOut() = auth.signOut()

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toConversation(userId: String): WhappyConversation? {
        val ids = get("memberIds") as? List<String> ?: return null
        if (userId !in ids) return null
        val rawMembers = get("members") as? List<Map<String, Any?>>
        val isGroup = getString("conversationType") == "group" || ids.size > 2
        val peerMap = rawMembers?.firstOrNull { it["uid"] != userId }
        val peer = if (isGroup) {
            WhappyMember(
                uid = id,
                displayName = getString("title")?.trim()?.ifBlank { "Groupe WAPI" } ?: "Groupe WAPI",
                photoUrl = rawMembers?.firstNotNullOfOrNull { it["groupPhotoUrl"]?.toString()?.takeIf(String::isNotBlank) }.orEmpty(),
            )
        } else if (peerMap != null) {
            val phone = peerMap["phoneNumber"]?.toString().orEmpty()
            WhappyMember(
                uid = peerMap["uid"]?.toString().orEmpty(),
                displayName = WhappyIdentity.resolveAccountName(peerMap["displayName"]?.toString().orEmpty(), phone),
                phoneNumber = phone,
                photoUrl = peerMap["photoUrl"]?.toString().orEmpty(),
            )
        } else {
            WhappyMember(
                uid = getString("contactId").orEmpty(),
                displayName = getString("contactName")?.ifBlank { "Contact WAPI" } ?: "Contact WAPI",
            )
        }
        val readBy = get("readBy") as? Map<String, Any?>
        val readAt = (readBy?.get(userId) as? Timestamp)?.toDate()?.time ?: 0L
        val peerReadAt = if (isGroup) 0L else (readBy?.get(peer.uid) as? Timestamp)?.toDate()?.time ?: 0L
        val updatedAt = timestampMillis("updatedAt")
        val typingBy = get("typingBy") as? Map<String, Any?>
        return WhappyConversation(
            id = id,
            peer = peer,
            lastMessage = getString("lastMessage") ?: "Nouvelle conversation",
            updatedAt = updatedAt,
            unread = updatedAt > readAt && getString("lastSenderId") != userId,
            peerTyping = if (isGroup) typingBy?.any { (uid, value) -> uid != userId && value == true } == true else typingBy?.get(peer.uid) == true,
            peerReadAt = peerReadAt,
            isGroup = isGroup,
            memberCount = ids.size,
            source = "conversations",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toGroupConversation(userId: String): WhappyConversation? {
        val ids = (get("memberIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        if (userId !in ids) return null
        val name = getString("name")?.trim().orEmpty()
        if (name.isBlank()) return null
        val updatedAt = timestampMillis("updatedAt").takeIf { it > 0L } ?: timestampMillis("createdAt")
        return WhappyConversation(
            id = id,
            peer = WhappyMember(id, name, photoUrl = getString("photoUrl").orEmpty()),
            lastMessage = getString("lastMessage") ?: "Groupe créé",
            updatedAt = updatedAt,
            unread = false,
            isGroup = true,
            memberCount = ids.size,
            source = "groups",
            groupOwnerId = getString("ownerId").orEmpty(),
        )
    }

    private fun DocumentSnapshot.timestampMillis(field: String): Long =
        getTimestamp(field)?.toDate()?.time ?: 0L

    private fun DocumentSnapshot.toMember(): WhappyMember {
        val phone = getString("phoneNumber").orEmpty()
        val presenceUpdatedAt = timestampMillis("presenceUpdatedAt")
        val lastSeenAt = timestampMillis("lastSeenAt").takeIf { it > 0L } ?: presenceUpdatedAt
        val isOnline = getString("presenceState") == "online" &&
            presenceUpdatedAt >= System.currentTimeMillis() - 90_000L
        return WhappyMember(
            uid = id,
            displayName = WhappyIdentity.resolveAccountName(getString("displayName").orEmpty(), phone),
            phoneNumber = phone,
            photoUrl = getString("photoUrl").orEmpty(),
            isOnline = isOnline,
            lastSeenAt = lastSeenAt,
        )
    }

    private fun DocumentSnapshot.toBusinessPage(): WhappyBusinessPage = WhappyBusinessPage(
        id = id,
        name = getString("name") ?: "Page WAPI",
        handle = getString("handle").orEmpty(),
        category = getString("category").orEmpty(),
        bio = getString("bio").orEmpty(),
        city = getString("city") ?: "Brazzaville",
        ownerId = getString("ownerId").orEmpty(),
        phone = getString("phone").orEmpty(),
        website = getString("website").orEmpty(),
    )

    private fun normalizePhone(value: String): String {
        return PhoneNumberFormatter.normalize("+242", value).orEmpty()
    }
}
