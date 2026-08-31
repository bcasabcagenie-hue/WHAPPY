package com.whappy.chat

import com.google.firebase.auth.FirebaseUser

enum class WhappyTab(val label: String) {
    MOMENTS("Accueil"),
    MESSAGES("Messages"),
    STORIES("Actus"),
    WEPI("Assistant"),
    CONTACTS("Contacts"),
    CHANNELS("Chaînes"),
    CALLS("Appels"),
    MARKET("Marché"),
    LIVE("Live"),
    RADIO("Radio"),
    PODCASTS("Podcasts"),
    GAMES("Jeux"),
    SERVICES("Services"),
    BUSINESS("Business"),
    PROFILE("Profil"),
}

data class WhappyMember(
    val uid: String,
    val displayName: String,
    val phoneNumber: String = "",
    val photoUrl: String = "",
    val verified: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeenAt: Long = 0L,
    /** Present only when this member is displayed through a Business page. */
    val businessPageId: String = "",
)

data class WhappyConversation(
    val id: String,
    val peer: WhappyMember,
    val lastMessage: String,
    val updatedAt: Long,
    val unread: Boolean,
    val peerTyping: Boolean = false,
    val peerReadAt: Long = 0L,
    val isGroup: Boolean = false,
    val memberCount: Int = 2,
    val source: String = "conversations",
    val groupOwnerId: String = "",
    val groupAdminIds: List<String> = emptyList(),
    val groupMembers: List<WhappyMember> = emptyList(),
    val groupReadAt: Map<String, Long> = emptyMap(),
    val groupDescription: String = "",
    val groupInviteToken: String = "",
    val groupEditInfoByMembers: Boolean = true,
    val groupOnlyAdminsCanSend: Boolean = false,
    /** Distinguishes the personal inbox from a Business inbox owned by the same user. */
    val profileType: String = "personal",
    val businessPageId: String = "",
    val businessPageName: String = "",
)

data class WhappyContact(
    val member: WhappyMember,
    val addedAt: Long = 0L,
)

data class WhappyMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val createdAt: Long,
    val kind: String = "text",
    val mediaUrl: String = "",
    val mediaName: String = "",
    val durationSeconds: Int = 0,
    /** Waphsare keeps the exact source bytes; this checksum lets the recipient verify it. */
    val mediaSizeBytes: Long = 0L,
    val mediaSha256: String = "",
    val viewOnce: Boolean = false,
    val viewedByIds: Set<String> = emptySet(),
    val replyToId: String = "",
    val replyText: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val deleted: Boolean = false,
    val edited: Boolean = false,
    val deliveryState: String = "sent",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
)

data class WhappyChannel(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val ownerId: String,
    val ownerName: String,
    val memberIds: List<String> = emptyList(),
    val memberCount: Int = 1,
    val postCount: Int = 0,
    val lastPost: String = "",
    val updatedAt: Long = 0L,
    val verified: Boolean = false,
)

data class WhappyChannelPost(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val createdAt: Long,
    val reactions: Map<String, String> = emptyMap(),
    val pinned: Boolean = false,
    val deleted: Boolean = false,
)

data class WhappyListing(
    val id: String,
    val title: String,
    val price: String,
    val place: String,
    val seller: String,
    val ownerId: String,
    val mode: String = "sale",
    val description: String = "",
    val category: String = "Autre",
    val businessPageId: String = "",
    val photoUrls: List<String> = emptyList(),
    val acceptsOffers: Boolean = true,
    val boostStatus: String = "none",
)

data class WhappyBusinessPage(
    val id: String,
    val name: String,
    val handle: String,
    val category: String,
    val bio: String,
    val city: String,
    val ownerId: String,
    val phone: String = "",
    val website: String = "",
    val logoUrl: String = "",
    val verified: Boolean = false,
    val onboardingComplete: Boolean = true,
)

data class WhappyCampaign(
    val id: String,
    val pageId: String,
    val pageName: String,
    val objective: String,
    val title: String,
    val dailyBudget: Long,
    val days: Int,
    val status: String,
    val ownerId: String = "",
    val placement: String = "profile_story",
    val destination: String = "message",
    val creative: String = "",
    val cta: String = "Nous contacter",
    val audience: String = "Public local",
    val city: String = "",
    val phone: String = "",
    val link: String = "",
    val estimatedReach: Long = 0L,
    val countryCode: String = "",
    val pageCategory: String = "Business",
    val rankReasons: List<String> = emptyList(),
    val deliveryMode: String = "budget",
    val targetImpressions: Long = 0L,
    val pricePerThousand: Long = 0L,
    val totalBudget: Long = 0L,
    val dailyDeliveryCap: Long = 0L,
    val rankingEngine: String = "ELEPHANT",
    val rankingVersion: String = "1.0",
)

data class WhappyCampaignDraft(
    val pageId: String,
    val pageName: String,
    val objective: String,
    val title: String,
    val creative: String,
    val cta: String,
    val audience: String,
    val city: String,
    val dailyBudget: Long,
    val days: Int,
    val targetImpressions: Long = 0L,
    val placement: String = "profile_story",
    val destination: String = "message",
    val phone: String = "",
    val link: String = "",
    val countryCode: String = "",
)

data class WhappyAdMetrics(
    val impressions: Int = 0,
    val clicks: Int = 0,
) {
    val clickThroughRate: Double
        get() = if (impressions <= 0) 0.0 else clicks.toDouble() * 100.0 / impressions.toDouble()
}

data class WhappyLive(
    val id: String,
    val hostId: String,
    val hostName: String,
    val title: String,
    val category: String,
    val productTitle: String,
    val status: String,
    val viewerCount: Int,
    val startedAt: Long,
    val hostMode: String = "personal",
    val visibility: String = "public",
    /** A session is joinable only when a real media transport is provisioned. */
    val streamProvider: String = "unconfigured",
    val streamRoomId: String = "",
    val audioOnly: Boolean = false,
    val allowGiftWearables: Boolean = false,
    val giftCount: Int = 0,
    val hostPhotoUrl: String = "",
)

data class WhappyStatus(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val tone: String,
    val createdAt: Long,
    val mediaUrl: String = "",
    val mediaKind: String = "",
    val mediaName: String = "",
    val expiresAt: Long = 0L,
    val viewCount: Int = 0,
    val viewedByCurrentUser: Boolean = false,
    val authorPhotoUrl: String = "",
)

data class WapiStoryViewer(
    val userId: String,
    val displayName: String,
    val photoUrl: String = "",
    val viewedAt: Long = 0L,
)

data class WapiGroupCallInvitation(
    val callId: String,
    val groupId: String,
    val groupName: String,
    val video: Boolean,
    val participantCount: Int = 0,
)

data class WapiRadioEpisode(
    val id: String,
    val ownerId: String,
    val authorName: String,
    val stationName: String,
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long,
    val createdAt: Long,
)

data class WhappyDeal(
    val id: String,
    val pageId: String,
    val pageName: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val stock: Int,
    val sold: Int,
    val endsAt: Long,
    val status: String,
)

data class WhappyPaymentNotice(
    val id: String,
    val pageId: String,
    val dealId: String,
    val buyerName: String,
    val amount: Long,
    val currency: String,
    val provider: String,
    val status: String,
    val createdAt: Long,
    val read: Boolean,
)

data class WhappyTwinProfile(
    val displayName: String = "",
    val identityConsent: Boolean = false,
    val voiceConsent: Boolean = false,
    val movementConsent: Boolean = false,
    val videoUrl: String = "",
    val voiceUrl: String = "",
    val movementUrl: String = "",
    val outfitUrl: String = "",
    val voiceStatus: String = "empty",
    val movementStatus: String = "empty",
) {
    val readiness: Int
        get() = listOf(identityConsent, videoUrl.isNotBlank(), voiceUrl.isNotBlank(), movementUrl.isNotBlank()).count { it } * 20

    val isRenderReady: Boolean
        get() = identityConsent && voiceConsent && movementConsent
            && videoUrl.isNotBlank() && voiceUrl.isNotBlank() && movementUrl.isNotBlank()

    val nextRequiredCapture: String
        get() = when {
            !identityConsent || !voiceConsent || !movementConsent -> "Confirmez l’autorisation de votre identité, de votre voix et de vos mouvements."
            videoUrl.isBlank() -> "Ajoutez le portrait vidéo de votre visage."
            voiceUrl.isBlank() -> "Enregistrez l’empreinte vocale du propriétaire."
            movementUrl.isBlank() -> "Ajoutez une séquence de mouvements."
            else -> "Toutes les captures sont prêtes."
        }
}

data class WhappyTwinAutomation(
    val id: String,
    val name: String,
    val trigger: String,
    val channel: String,
    val action: String,
    val enabled: Boolean,
)

data class WhappyTwinRender(
    val id: String,
    val title: String,
    val script: String,
    val language: String,
    val status: String,
)

data class WhappyUiState(
    val user: FirebaseUser? = null,
    val accountDisplayName: String = "",
    val accountPhotoUrl: String = "",
    val accountVerified: Boolean = false,
    /** Identity currently used by the visible inbox and outgoing messages. */
    val activeProfileType: String = "personal",
    val activeBusinessPageId: String = "",
    val sessionRestoring: Boolean = true,
    val tab: WhappyTab = WhappyTab.MESSAGES,
    val conversations: List<WhappyConversation> = emptyList(),
    val contacts: List<WhappyContact> = emptyList(),
    val contactSearchResult: WhappyMember? = null,
    val contactSearchPhone: String = "",
    val contactSearchMessage: String? = null,
    val selectedConversation: WhappyConversation? = null,
    val messages: List<WhappyMessage> = emptyList(),
    val channels: List<WhappyChannel> = emptyList(),
    val selectedChannel: WhappyChannel? = null,
    val requestedLiveId: String = "",
    val requestedGroupCall: WapiGroupCallInvitation? = null,
    val channelPosts: List<WhappyChannelPost> = emptyList(),
    val discoveryQuery: String = "",
    val listings: List<WhappyListing> = emptyList(),
    val businessPages: List<WhappyBusinessPage> = emptyList(),
    val campaigns: List<WhappyCampaign> = emptyList(),
    /** Campagnes payées et réellement diffusables dans les surfaces WAPI. */
    val sponsoredCampaigns: List<WhappyCampaign> = emptyList(),
    val adMetrics: Map<String, WhappyAdMetrics> = emptyMap(),
    val lives: List<WhappyLive> = emptyList(),
    val statuses: List<WhappyStatus> = emptyList(),
    val storyViewers: Map<String, List<WapiStoryViewer>> = emptyMap(),
    val storyViewersLoading: Set<String> = emptySet(),
    val wepiSettings: WapiWepiSettings? = null,
    val radioEpisodes: List<WapiRadioEpisode> = emptyList(),
    val deals: List<WhappyDeal> = emptyList(),
    val paymentNotices: List<WhappyPaymentNotice> = emptyList(),
    val businessSearchResults: List<WhappyBusinessPage> = emptyList(),
    val twinProfile: WhappyTwinProfile? = null,
    val twinAutomations: List<WhappyTwinAutomation> = emptyList(),
    val twinRenders: List<WhappyTwinRender> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val contactBusy: Boolean = false,
    val businessSearchBusy: Boolean = false,
    val actionBusy: Boolean = false,
    val groupUpdate: WapiGroupUpdateState = WapiGroupUpdateState(),
    val twinBusy: Boolean = false,
    val online: Boolean = true,
    val error: String? = null,
)

data class WapiGroupUpdateState(
    val groupId: String = "",
    val status: String = "idle",
    val message: String = "",
)
