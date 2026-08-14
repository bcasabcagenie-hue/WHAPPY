package com.whappy.chat

import com.google.firebase.auth.FirebaseUser

enum class WhappyTab(val label: String) {
    MOMENTS("Moments"),
    MESSAGES("Messages"),
    MARKET("Marché"),
    BUSINESS("Business"),
    PROFILE("Profil"),
}

data class WhappyMember(
    val uid: String,
    val displayName: String,
    val phoneNumber: String = "",
)

data class WhappyConversation(
    val id: String,
    val peer: WhappyMember,
    val lastMessage: String,
    val updatedAt: Long,
    val unread: Boolean,
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
    val sessionRestoring: Boolean = true,
    val tab: WhappyTab = WhappyTab.MOMENTS,
    val conversations: List<WhappyConversation> = emptyList(),
    val selectedConversation: WhappyConversation? = null,
    val messages: List<WhappyMessage> = emptyList(),
    val listings: List<WhappyListing> = emptyList(),
    val businessPages: List<WhappyBusinessPage> = emptyList(),
    val campaigns: List<WhappyCampaign> = emptyList(),
    val twinProfile: WhappyTwinProfile? = null,
    val twinAutomations: List<WhappyTwinAutomation> = emptyList(),
    val twinRenders: List<WhappyTwinRender> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val contactBusy: Boolean = false,
    val actionBusy: Boolean = false,
    val twinBusy: Boolean = false,
    val online: Boolean = true,
    val error: String? = null,
)
