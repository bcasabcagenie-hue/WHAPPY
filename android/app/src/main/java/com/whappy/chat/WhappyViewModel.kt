package com.whappy.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WhappyViewModel(
    private val repository: WhappyRepository = WhappyRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(WhappyUiState(user = repository.currentUser(), sessionRestoring = true))
    val uiState: StateFlow<WhappyUiState> = _uiState.asStateFlow()

    private var conversationsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var listingsListener: ListenerRegistration? = null
    private var businessListener: ListenerRegistration? = null
    private var campaignsListener: ListenerRegistration? = null
    private var twinProfileListener: ListenerRegistration? = null
    private var twinAutomationsListener: ListenerRegistration? = null
    private var twinRendersListener: ListenerRegistration? = null
    private val authListener = FirebaseAuth.AuthStateListener { refreshSession(it.currentUser) }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    fun selectTab(tab: WhappyTab) {
        _uiState.update { it.copy(tab = tab, selectedConversation = null, messages = emptyList(), error = null) }
        messagesListener?.remove()
        messagesListener = null
    }

    fun openConversation(conversation: WhappyConversation) {
        val user = _uiState.value.user ?: return
        messagesListener?.remove()
        _uiState.update { it.copy(selectedConversation = conversation, messages = emptyList(), loading = true, error = null) }
        messagesListener = repository.observeMessages(
            conversation.id,
            onChange = { messages ->
                _uiState.update { it.copy(messages = messages, loading = false, online = true) }
                viewModelScope.launch { runCatching { repository.markRead(conversation.id, user.uid) } }
            },
            onError = { _uiState.update { it.copy(loading = false, online = false, error = "Messages momentanément indisponibles") } },
        )
    }

    fun closeConversation() {
        messagesListener?.remove()
        messagesListener = null
        _uiState.update { it.copy(selectedConversation = null, messages = emptyList(), error = null) }
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        if (text.isBlank() || state.sending) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.sendMessage(conversation.id, user.uid, text) }
                .onSuccess { _uiState.update { current -> current.copy(sending = false, online = true) } }
                .onFailure { _uiState.update { current -> current.copy(sending = false, online = false, error = "Le message n’a pas été envoyé") } }
        }
    }

    fun sendMedia(uri: Uri, kind: String, contentType: String, mediaName: String, durationSeconds: Int) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        if (state.sending) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.sendMediaMessage(
                    conversationId = conversation.id,
                    userId = user.uid,
                    uri = uri,
                    kind = kind,
                    contentType = contentType,
                    mediaName = mediaName,
                    durationSeconds = durationSeconds,
                )
            }.onSuccess { _uiState.update { current -> current.copy(sending = false, online = true) } }
                .onFailure { _uiState.update { current -> current.copy(sending = false, online = false, error = "Le média n’a pas été envoyé") } }
        }
    }

    fun addContact(phone: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.contactBusy) return
        _uiState.update { it.copy(contactBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val peer = repository.findUserByPhone(phone) ?: error("not-found")
                if (peer.uid == user.uid) error("self")
                repository.ensureDirectConversation(
                    WhappyMember(user.uid, accountName(), user.phoneNumber.orEmpty()),
                    peer,
                )
            }.onSuccess { conversation ->
                _uiState.update { it.copy(contactBusy = false) }
                openConversation(conversation)
            }.onFailure { failure ->
                val message = when (failure.message) {
                    "not-found" -> "Aucun compte WHAPPY trouvé avec ce numéro"
                    "self" -> "C’est votre propre numéro WHAPPY"
                    else -> "La recherche du contact a échoué"
                }
                _uiState.update { it.copy(contactBusy = false, error = message) }
            }
        }
    }

    fun publishListing(title: String, price: String, place: String, mode: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.publishListing(
                    WhappyMember(user.uid, accountName(), user.phoneNumber.orEmpty()),
                    title,
                    price,
                    place,
                    mode,
                )
            }.onSuccess { _uiState.update { it.copy(actionBusy = false) } }
                .onFailure { _uiState.update { it.copy(actionBusy = false, error = "L’annonce n’a pas été publiée") } }
        }
    }

    fun createBusinessPage(name: String, category: String, bio: String, city: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.createBusinessPage(user.uid, name, category, bio, city) }
                .onSuccess { _uiState.update { it.copy(actionBusy = false) } }
                .onFailure { _uiState.update { it.copy(actionBusy = false, error = "La page Business n’a pas été créée") } }
        }
    }

    fun createCampaign(draft: WhappyCampaignDraft) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.createCampaign(user.uid, draft) }
                .onSuccess { _uiState.update { it.copy(actionBusy = false) } }
                .onFailure { _uiState.update { it.copy(actionBusy = false, error = "La campagne n’a pas été créée") } }
        }
    }

    fun saveTwinConsent(consent: Boolean) = runTwinAction("Les autorisations du WHAPPY n’ont pas été synchronisées") { user ->
        repository.saveTwinConsent(user.uid, accountName(), consent)
    }

    fun uploadTwinAsset(uri: Uri, kind: String, contentType: String) = runTwinAction("La capture du WHAPPY n’a pas été synchronisée") { user ->
        val profile = _uiState.value.twinProfile
        require(profile?.identityConsent == true && profile.voiceConsent && profile.movementConsent)
        repository.uploadTwinAsset(user.uid, uri, kind, contentType)
    }

    fun createTwinAutomation(name: String, trigger: String, channel: String, action: String, script: String) = runTwinAction("La mission du WHAPPY n’a pas été créée") { user ->
        require(_uiState.value.twinProfile?.identityConsent == true)
        repository.createTwinAutomation(user.uid, name, trigger, channel, action, script)
    }

    fun toggleTwinAutomation(automationId: String, enabled: Boolean) = runTwinAction("La mission du WHAPPY n’a pas été modifiée") { user ->
        repository.toggleTwinAutomation(user.uid, automationId, enabled)
    }

    fun deleteTwinAutomation(automationId: String) = runTwinAction("La mission du WHAPPY n’a pas été supprimée") { user ->
        repository.deleteTwinAutomation(user.uid, automationId)
    }

    fun createTwinRender(title: String, script: String, language: String, gestures: List<String>) = runTwinAction("La production du WHAPPY n’a pas été préparée") { user ->
        val profile = _uiState.value.twinProfile
        require(profile?.identityConsent == true && profile.voiceConsent && profile.movementConsent)
        repository.createTwinRender(user.uid, title, script, language, gestures)
    }

    private fun runTwinAction(errorMessage: String, action: suspend (com.google.firebase.auth.FirebaseUser) -> Unit) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.twinBusy) return
        _uiState.update { it.copy(twinBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { action(user) }
                .onSuccess { _uiState.update { it.copy(twinBusy = false, online = true) } }
                .onFailure { _uiState.update { it.copy(twinBusy = false, error = errorMessage) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun refreshAccountProfile() {
        val user = repository.currentUser() ?: return
        resolveAccountProfile(user)
    }

    fun signOut() {
        repository.signOut()
        selectTab(WhappyTab.MOMENTS)
    }

    private fun refreshSession(user: com.google.firebase.auth.FirebaseUser?) {
        conversationsListener?.remove()
        messagesListener?.remove()
        listingsListener?.remove()
        businessListener?.remove()
        campaignsListener?.remove()
        twinProfileListener?.remove()
        twinAutomationsListener?.remove()
        twinRendersListener?.remove()
        conversationsListener = null
        messagesListener = null
        listingsListener = null
        businessListener = null
        campaignsListener = null
        twinProfileListener = null
        twinAutomationsListener = null
        twinRendersListener = null
        _uiState.update { WhappyUiState(user = user, loading = user != null, sessionRestoring = user != null) }
        if (user == null) {
            _uiState.update { it.copy(sessionRestoring = false) }
            return
        }
        resolveAccountProfile(user)
        conversationsListener = repository.observeConversations(
            user.uid,
            onChange = { conversations -> _uiState.update { it.copy(conversations = conversations, loading = false, online = true) } },
            onError = { _uiState.update { it.copy(loading = false, online = false, error = "Synchronisation momentanément indisponible") } },
        )
        listingsListener = repository.observeListings(
            onChange = { listings -> _uiState.update { it.copy(listings = listings, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        businessListener = repository.observeBusinessPages(
            user.uid,
            onChange = { pages -> _uiState.update { it.copy(businessPages = pages, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        campaignsListener = repository.observeCampaigns(
            user.uid,
            onChange = { campaigns -> _uiState.update { it.copy(campaigns = campaigns, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        twinProfileListener = repository.observeTwinProfile(
            user.uid,
            onChange = { profile -> _uiState.update { it.copy(twinProfile = profile, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        twinAutomationsListener = repository.observeTwinAutomations(
            user.uid,
            onChange = { items -> _uiState.update { it.copy(twinAutomations = items, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        twinRendersListener = repository.observeTwinRenders(
            user.uid,
            onChange = { items -> _uiState.update { it.copy(twinRenders = items, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
    }

    private fun resolveAccountProfile(user: com.google.firebase.auth.FirebaseUser) {
        viewModelScope.launch {
            val resolvedName = runCatching { repository.restoreAccountDisplayName(user) }
                .getOrDefault(user.displayName.orEmpty().trim())
            val restoredName = AccountSessionPolicy.displayName(
                resolvedName = resolvedName,
                phoneNumber = user.phoneNumber.orEmpty(),
                creationTimestamp = user.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            )
            _uiState.update { current ->
                if (current.user?.uid != user.uid) current
                else current.copy(accountDisplayName = restoredName, sessionRestoring = false)
            }
        }
    }

    private fun accountName(): String = _uiState.value.accountDisplayName.ifBlank { "Utilisateur WHAPPY" }

    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        conversationsListener?.remove()
        messagesListener?.remove()
        listingsListener?.remove()
        businessListener?.remove()
        campaignsListener?.remove()
        twinProfileListener?.remove()
        twinAutomationsListener?.remove()
        twinRendersListener?.remove()
    }
}
