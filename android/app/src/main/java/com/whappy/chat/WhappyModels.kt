package com.whappy.chat

import com.google.firebase.auth.FirebaseUser

enum class WhappyTab(val label: String) {
    MOMENTS("Accueil"),
    STATUS("Statuts"),
    MESSAGES("Messages"),
    CONTACTS("Contacts"),
    CALLS("Appels"),
    MARKET("Marché"),
    LIVE("Live"),
    RADIO("Radio"),
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
    val replyToId: String = "",
    val replyText: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val deleted: Boolean = false,
    val edited: Boolean = false,
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
    val mode: String = "vente",
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
)

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
)

data class WhappyStatus(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val tone: String,
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
    val voiceStatus: String = "empty",
    val movementStatus: String = "empty",
) {
    val readiness: Int
        get() = listOf(identityConsent, videoUrl.isNotBlank(), voiceUrl.isNotBlank(), movementUrl.isNotBlank()).count { it } * 20
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
    val sessionRestoring: Boolean = true,
    val tab: WhappyTab = WhappyTab.MOMENTS,
    val conversations: List<WhappyConversation> = emptyList(),
    val contacts: List<WhappyContact> = emptyList(),
    val contactSearchResult: WhappyMember? = null,
    val contactSearchPhone: String = "",
    val contactSearchMessage: String? = null,
    val selectedConversation: WhappyConversation? = null,
    val messages: List<WhappyMessage> = emptyList(),
    val channels: List<WhappyChannel> = emptyList(),
    val selectedChannel: WhappyChannel? = null,
    val channelPosts: List<WhappyChannelPost> = emptyList(),
    val discoveryQuery: String = "",
    val listings: List<WhappyListing> = emptyList(),
    val businessPages: List<WhappyBusinessPage> = emptyList(),
    val campaigns: List<WhappyCampaign> = emptyList(),
    val lives: List<WhappyLive> = emptyList(),
    val statuses: List<WhappyStatus> = emptyList(),
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
    val twinBusy: Boolean = false,
    val online: Boolean = true,
    val error: String? = null,
)
