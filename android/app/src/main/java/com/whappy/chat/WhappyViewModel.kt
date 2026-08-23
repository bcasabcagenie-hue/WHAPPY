package com.whappy.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.StorageException
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
    private var contactsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var channelsListener: ListenerRegistration? = null
    private var channelPostsListener: ListenerRegistration? = null
    private var listingsListener: ListenerRegistration? = null
    private var businessListener: ListenerRegistration? = null
    private var campaignsListener: ListenerRegistration? = null
    private var livesListener: ListenerRegistration? = null
    private var statusesListener: ListenerRegistration? = null
    private var dealsListener: ListenerRegistration? = null
    private var paymentNoticesListener: ListenerRegistration? = null
    private var twinProfileListener: ListenerRegistration? = null
    private var wepiSettingsListener: ListenerRegistration? = null
    private var radioEpisodesListener: ListenerRegistration? = null
    private var twinAutomationsListener: ListenerRegistration? = null
    private var twinRendersListener: ListenerRegistration? = null
    private var contactSearchRequest = 0
    private var pendingChannelId: String? = null
    private var remoteConversationMessages: List<WhappyMessage> = emptyList()
    private val pendingStories = linkedMapOf<String, WhappyStatus>()
    private val confirmedStories = linkedMapOf<String, WhappyStatus>()
    private var typingState = false
    private val authListener = FirebaseAuth.AuthStateListener { refreshSession(it.currentUser) }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    fun selectTab(tab: WhappyTab) {
        stopTyping()
        _uiState.update { it.copy(tab = tab, selectedConversation = null, messages = emptyList(), selectedChannel = null, channelPosts = emptyList(), error = null) }
        messagesListener?.remove()
        messagesListener = null
        remoteConversationMessages = emptyList()
        channelPostsListener?.remove()
        channelPostsListener = null
    }

    fun openConversation(conversation: WhappyConversation) {
        val user = _uiState.value.user ?: return
        stopTyping()
        channelPostsListener?.remove()
        channelPostsListener = null
        messagesListener?.remove()
        remoteConversationMessages = emptyList()
        val cachedMessages = repository.cachedMessages(conversation.id, conversation.source)
        remoteConversationMessages = cachedMessages
        _uiState.update {
            it.copy(
                selectedConversation = conversation,
                messages = cachedMessages,
                selectedChannel = null,
                channelPosts = emptyList(),
                loading = cachedMessages.isEmpty(),
                error = null,
            )
        }
        messagesListener = repository.observeMessages(
            conversation.id,
            source = conversation.source,
            onChange = { messages ->
                remoteConversationMessages = messages
                _uiState.update { it.copy(messages = messages, loading = false, online = true) }
                if (conversation.source != "groups") {
                    refreshPendingMessages(conversation.id, user.uid)
                    viewModelScope.launch { runCatching { repository.markRead(conversation.id, user.uid) } }
                }
            },
            onError = {
                _uiState.update {
                    it.copy(
                        loading = false,
                        online = false,
                        error = if (it.messages.isEmpty()) "Messages momentanément indisponibles" else null,
                    )
                }
            },
        )
    }

    fun closeConversation() {
        stopTyping()
        messagesListener?.remove()
        messagesListener = null
        remoteConversationMessages = emptyList()
        _uiState.update { it.copy(selectedConversation = null, messages = emptyList(), error = null) }
    }

    fun openChannel(channel: WhappyChannel) {
        stopTyping()
        messagesListener?.remove()
        messagesListener = null
        channelPostsListener?.remove()
        _uiState.update { it.copy(selectedConversation = null, messages = emptyList(), selectedChannel = channel, channelPosts = emptyList(), loading = true, error = null) }
        channelPostsListener = repository.observeChannelPosts(
            channel.id,
            onChange = { posts -> _uiState.update { it.copy(channelPosts = posts, loading = false, online = true) } },
            onError = { _uiState.update { it.copy(loading = false, online = false, error = "Les publications de cette chaîne sont indisponibles") } },
        )
    }

    fun closeChannel() {
        channelPostsListener?.remove()
        channelPostsListener = null
        _uiState.update { it.copy(selectedChannel = null, channelPosts = emptyList(), error = null) }
    }

    fun createChannel(name: String, description: String, category: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.createChannel(user.uid, accountName(), name, description, category) }
                .onSuccess { channel ->
                    _uiState.update { it.copy(actionBusy = false, online = true) }
                    openChannel(channel)
                }
                .onFailure { _uiState.update { it.copy(actionBusy = false, error = "La chaîne n’a pas pu être créée") } }
        }
    }

    fun createGroup(name: String, selectedMembers: List<WhappyMember>, photoUri: Uri?, photoContentType: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        val selected = selectedMembers
            .filter { it.uid.isNotBlank() && it.uid != user.uid }
            .distinctBy { it.uid }
        if (selected.size < 2) {
            _uiState.update { it.copy(error = "Choisissez au moins deux contacts pour créer le groupe") }
            return
        }
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.createGroup(
                    current = currentMember(user),
                    name = name,
                    selectedMembers = selected,
                    photoUri = photoUri,
                    photoContentType = photoContentType,
                )
            }.onSuccess { conversation ->
                _uiState.update { it.copy(actionBusy = false, online = true) }
                openConversation(conversation)
            }.onFailure { error ->
                val message = when (error) {
                    is IllegalArgumentException -> "Vérifiez le nom et les membres du groupe"
                    else -> "Le groupe n’a pas pu être créé. Vérifiez votre connexion puis réessayez."
                }
                _uiState.update { it.copy(actionBusy = false, error = message) }
            }
        }
    }

    fun updateGroup(groupId: String, name: String, photoUri: Uri?, photoContentType: String, removePhoto: Boolean) {
        val user = _uiState.value.user ?: return
        val conversation = _uiState.value.selectedConversation?.takeIf { it.id == groupId && it.isGroup } ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.updateGroup(groupId, user.uid, accountName(), name, photoUri, photoContentType, removePhoto) }
                .onSuccess { updated ->
                    _uiState.update { current -> current.copy(actionBusy = false, selectedConversation = updated, conversations = current.conversations.map { if (it.id == updated.id) updated else it }, online = true) }
                }
                .onFailure { error ->
                    val message = if (error is IllegalArgumentException) "Le nom ou la photo du groupe est invalide" else "La modification du groupe n’a pas pu être synchronisée"
                    _uiState.update { it.copy(actionBusy = false, error = message) }
                }
        }
    }

    fun setChannelSubscription(channelId: String, subscribed: Boolean) {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            runCatching { repository.setChannelSubscription(channelId, user.uid, subscribed) }
                .onFailure { _uiState.update { it.copy(error = "L’abonnement n’a pas pu être mis à jour") } }
        }
    }

    fun publishChannelPost(text: String) {
        val state = _uiState.value
        val user = state.user ?: return
        val channel = state.selectedChannel ?: return
        if (channel.ownerId != user.uid || text.isBlank() || state.sending) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.publishChannelPost(channel.id, user.uid, accountName(), text) }
                .onSuccess { _uiState.update { it.copy(sending = false, online = true) } }
                .onFailure { _uiState.update { it.copy(sending = false, error = "La publication n’a pas été envoyée") } }
        }
    }

    fun reactToChannelPost(postId: String, emoji: String) {
        val state = _uiState.value
        val user = state.user ?: return
        val channel = state.selectedChannel ?: return
        viewModelScope.launch { runCatching { repository.reactToChannelPost(channel.id, postId, user.uid, emoji) }.onFailure { _uiState.update { it.copy(error = "La réaction n’a pas été enregistrée") } } }
    }

    fun pinChannelPost(postId: String, pinned: Boolean) {
        val channel = _uiState.value.selectedChannel ?: return
        viewModelScope.launch { runCatching { repository.pinChannelPost(channel.id, postId, pinned) }.onFailure { _uiState.update { it.copy(error = "L’épinglage a échoué") } } }
    }

    fun deleteChannelPost(postId: String) {
        val channel = _uiState.value.selectedChannel ?: return
        viewModelScope.launch { runCatching { repository.deleteChannelPost(channel.id, postId) }.onFailure { _uiState.update { it.copy(error = "La publication n’a pas pu être supprimée") } } }
    }

    fun sendMessage(text: String, replyToId: String = "", replyText: String = "") {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        if (text.isBlank()) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.sendMessage(conversation.id, user.uid, text, replyToId, replyText, conversation.source, accountName()) }
                .onSuccess { delivery ->
                    _uiState.update { current ->
                        current.copy(sending = false, online = delivery == WhappyDeliveryResult.SENT)
                    }
                    if (conversation.source != "groups") refreshPendingMessages(conversation.id, user.uid)
                }
                .onFailure { _uiState.update { current -> current.copy(sending = false, online = false, error = "Le message n’a pas été envoyé") } }
        }
    }

    fun retryPendingMessages() {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        if (conversation.source == "groups") return
        repository.schedulePendingMessageSync()
        viewModelScope.launch {
            val delivered = runCatching { repository.flushPendingMessages() }.getOrDefault(false)
            _uiState.update { current -> current.copy(online = delivered || current.online) }
            refreshPendingMessages(conversation.id, user.uid)
        }
    }

    private fun refreshPendingMessages(conversationId: String, userId: String) {
        viewModelScope.launch {
            val pending = runCatching { repository.pendingMessages(conversationId, userId) }.getOrDefault(emptyList())
            _uiState.update { current ->
                if (current.selectedConversation?.id != conversationId) current
                else {
                    val remoteIds = remoteConversationMessages.mapTo(hashSetOf()) { it.id }
                    current.copy(
                        messages = (remoteConversationMessages + pending.filterNot { it.id in remoteIds })
                            .sortedBy { it.createdAt },
                    )
                }
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        viewModelScope.launch { runCatching { repository.reactToMessage(conversation.id, messageId, user.uid, emoji, conversation.source) }.onFailure { _uiState.update { it.copy(error = "La réaction n’a pas été enregistrée") } } }
    }

    fun deleteMessage(messageId: String) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        viewModelScope.launch { runCatching { repository.deleteMessage(conversation.id, messageId, user.uid, conversation.source) }.onFailure { _uiState.update { it.copy(error = "Ce message ne peut pas être supprimé") } } }
    }

    fun editMessage(messageId: String, text: String) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        if (text.isBlank()) return
        viewModelScope.launch { runCatching { repository.editMessage(conversation.id, messageId, user.uid, text, conversation.source) }.onFailure { _uiState.update { it.copy(error = "Le message n’a pas été modifié") } } }
    }

    fun setTyping(typing: Boolean) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val user = state.user ?: return
        if (typingState == typing) return
        typingState = typing
        viewModelScope.launch { runCatching { repository.setTyping(conversation.id, user.uid, typing, conversation.source) } }
    }

    private fun stopTyping() {
        val state = _uiState.value
        val conversation = state.selectedConversation
        val user = state.user
        if (typingState && conversation != null && user != null) {
            viewModelScope.launch { runCatching { repository.setTyping(conversation.id, user.uid, false, conversation.source) } }
        }
        typingState = false
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
                    source = conversation.source,
                    senderName = accountName(),
                )
            }.onSuccess { delivery ->
                _uiState.update { current -> current.copy(sending = false, online = delivery == WhappyDeliveryResult.SENT) }
            }
                .onFailure { _uiState.update { current -> current.copy(sending = false, online = false, error = "Le média n’a pas été envoyé") } }
        }
    }

    fun searchContact(phone: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.contactBusy) return
        val normalizedPhone = PhoneNumberFormatter.normalizeAny(phone)
        if (normalizedPhone == null) {
            _uiState.update {
                it.copy(
                    contactSearchResult = null,
                    contactSearchPhone = phone,
                    contactSearchMessage = "Saisissez un numéro complet, par exemple +242 06 123 45 67.",
                )
            }
            return
        }
        val request = ++contactSearchRequest
        _uiState.update {
            it.copy(
                contactBusy = true,
                contactSearchResult = null,
                contactSearchPhone = normalizedPhone,
                contactSearchMessage = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                val peer = repository.findUserByPhone(normalizedPhone) ?: error("not-found")
                if (peer.uid == user.uid) error("self")
                peer
            }.onSuccess { peer ->
                if (request != contactSearchRequest) return@onSuccess
                val current = currentMember(user)
                // A registered number is an unambiguous destination: create (or
                // reuse) the direct thread and take the user there immediately.
                // This keeps the number flow as fast as messaging an existing contact.
                _uiState.update {
                    it.copy(
                        contactBusy = true,
                        contactSearchResult = peer,
                        contactSearchMessage = "Compte trouvé. Ouverture de la discussion…",
                        online = true,
                    )
                }
                viewModelScope.launch {
                    runCatching { repository.addContactAndEnsureConversation(current, peer) }
                        .onSuccess { conversation ->
                            if (request != contactSearchRequest) return@onSuccess
                            _uiState.update {
                                it.copy(
                                    contactBusy = false,
                                    contactSearchResult = null,
                                    contactSearchPhone = "",
                                    contactSearchMessage = null,
                                    online = true,
                                )
                            }
                            openConversation(conversation)
                        }
                        .onFailure { failure ->
                            if (request != contactSearchRequest) return@onFailure
                            val message = when ((failure as? FirebaseFirestoreException)?.code) {
                                FirebaseFirestoreException.Code.UNAVAILABLE -> "Connexion indisponible. Le contact est trouvé : relancez l’ouverture de la discussion."
                                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Ajout refusé par la sécurité. Vérifiez votre session puis réessayez."
                                else -> "Le compte est trouvé, mais la discussion n’a pas pu s’ouvrir. Réessayez."
                            }
                            _uiState.update { it.copy(contactBusy = false, contactSearchMessage = message) }
                        }
                }
            }.onFailure { failure ->
                if (request != contactSearchRequest) return@onFailure
                val message = when (failure.message) {
                    "not-found" -> "Aucun compte WAPI trouvé. Vérifiez le numéro ou demandez à la personne d’ouvrir WAPI une première fois."
                    "self" -> "C’est votre propre numéro WAPI"
                    else -> when ((failure as? FirebaseFirestoreException)?.code) {
                        FirebaseFirestoreException.Code.UNAVAILABLE -> "Connexion indisponible. Vérifiez Internet puis relancez la recherche."
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "La recherche est bloquée par les règles de sécurité. Mettez WAPI à jour puis réessayez."
                        else -> "La recherche n’a pas abouti. Vérifiez le numéro au format international puis réessayez."
                    }
                }
                _uiState.update {
                    it.copy(
                        contactBusy = false,
                        contactSearchResult = null,
                        contactSearchPhone = normalizedPhone,
                        contactSearchMessage = message,
                        online = failure !is FirebaseFirestoreException || failure.code != FirebaseFirestoreException.Code.UNAVAILABLE,
                    )
                }
            }
        }
    }

    fun clearContactSearch() {
        contactSearchRequest += 1
        _uiState.update { it.copy(contactSearchResult = null, contactSearchPhone = "", contactSearchMessage = null) }
    }

    fun handleDeepLink(value: String) {
        when (val link = WhappyLink.parse(value)) {
            is WhappyLink.Contact -> {
                selectTab(WhappyTab.CONTACTS)
                searchContact(link.phone)
            }
            is WhappyLink.Channel -> {
                selectTab(WhappyTab.MESSAGES)
                val channel = _uiState.value.channels.firstOrNull { it.id == link.id }
                if (channel != null) openChannel(channel) else pendingChannelId = link.id
            }
            is WhappyLink.Search -> {
                selectTab(WhappyTab.MESSAGES)
                _uiState.update { it.copy(discoveryQuery = link.query) }
            }
            null -> _uiState.update { it.copy(error = "Ce lien WAPI n’est pas valide ou a expiré") }
        }
    }

    fun addSearchedContact() {
        val state = _uiState.value
        val user = state.user ?: return
        val peer = state.contactSearchResult ?: return
        if (state.contactBusy) return

        val current = currentMember(user)
        if (state.contacts.any { it.member.uid == peer.uid }) {
            _uiState.update {
                it.copy(
                    contactBusy = true,
                    contactSearchMessage = "Ce contact existe déjà. Ouverture de la discussion…",
                    error = null,
                )
            }
            viewModelScope.launch {
                runCatching {
                    repository.ensureDirectConversation(current, peer)
                }.onSuccess { conversation ->
                    _uiState.update {
                        it.copy(
                            contactBusy = false,
                            contactSearchResult = null,
                            contactSearchPhone = "",
                            contactSearchMessage = null,
                            online = true,
                        )
                    }
                    openConversation(conversation)
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            contactBusy = false,
                            contactSearchMessage = "Le contact existe déjà. Impossible d’ouvrir la discussion pour le moment.",
                        )
                    }
                }
            }
            return
        }

        _uiState.update { it.copy(contactBusy = true, contactSearchMessage = null, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.addContactAndEnsureConversation(current, peer)
            }.onSuccess { conversation ->
                _uiState.update {
                    it.copy(
                        contactBusy = false,
                        contactSearchResult = null,
                        contactSearchPhone = "",
                        contactSearchMessage = "Contact ajouté avec succès. Ouverture de la conversation…",
                        online = true,
                    )
                }
                openConversation(conversation)
                }.onFailure { failure ->
                    val message = when ((failure as? FirebaseFirestoreException)?.code) {
                        FirebaseFirestoreException.Code.UNAVAILABLE -> "Connexion indisponible. Le contact n’a pas été perdu : relancez l’ajout."
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Ajout refusé par la sécurité. Vérifiez votre session puis réessayez."
                        else -> "Le contact n’a pas pu être ajouté. Vérifiez Internet puis réessayez."
                    }
                    _uiState.update { it.copy(contactBusy = false, contactSearchMessage = message, online = failure !is FirebaseFirestoreException || failure.code != FirebaseFirestoreException.Code.UNAVAILABLE) }
                }
        }
    }

    fun openContact(contact: WhappyContact) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.contactBusy) return
        _uiState.update { it.copy(contactBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.ensureDirectConversation(
                    currentMember(user),
                    contact.member,
                )
            }.onSuccess { conversation ->
                _uiState.update { it.copy(contactBusy = false) }
                openConversation(conversation)
            }.onFailure {
                _uiState.update { it.copy(contactBusy = false, error = "La conversation avec ce contact n’a pas pu être ouverte") }
            }
        }
    }

    fun searchBusinesses(query: String) {
        if (_uiState.value.businessSearchBusy) return
        _uiState.update { it.copy(businessSearchBusy = true, businessSearchResults = emptyList(), error = null) }
        viewModelScope.launch {
            runCatching { repository.searchBusinessPages(query) }
                .onSuccess { pages -> _uiState.update { it.copy(businessSearchBusy = false, businessSearchResults = pages, online = true) } }
                .onFailure { _uiState.update { it.copy(businessSearchBusy = false, error = "La recherche Business est momentanément indisponible") } }
        }
    }

    fun contactBusiness(page: WhappyBusinessPage) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.contactBusy) return
        _uiState.update { it.copy(contactBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val peer = repository.findUserById(page.ownerId) ?: error("not-found")
                if (peer.uid == user.uid) error("self")
                repository.ensureDirectConversation(
                    currentMember(user),
                    peer,
                )
            }.onSuccess { conversation ->
                _uiState.update { it.copy(contactBusy = false) }
                openConversation(conversation)
            }.onFailure { failure ->
                val message = when (failure.message) {
                    "not-found" -> "Ce Business n’est pas encore joignable sur WAPI"
                    "self" -> "Cette page Business vous appartient"
                    else -> "La conversation Business n’a pas pu être ouverte"
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
                    currentMember(user),
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

    fun updateBusinessPage(page: WhappyBusinessPage, name: String, category: String, bio: String, city: String, phone: String, website: String) = runBusinessAction("La page Business n’a pas été mise à jour") { user ->
        repository.updateBusinessPage(user.uid, page, name, category, bio, city, phone, website)
    }

    fun createLive(title: String, category: String, productTitle: String, startNow: Boolean, hostMode: String, visibility: String) = runBusinessAction("Le salon Live n’a pas été créé") { user ->
        repository.createLive(user.uid, accountName(), title, category, productTitle, startNow, hostMode, visibility)
        refreshVisibleLives()
    }

    fun publishStatus(text: String, tone: String, mediaUri: Uri? = null, mediaContentType: String = "") {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        val localId = "pending-story-${System.currentTimeMillis()}"
        val mediaKind = when {
            mediaUri == null -> "text"
            mediaContentType.startsWith("audio/") -> "audio"
            mediaContentType.startsWith("video/") -> "video"
            else -> "image"
        }
        pendingStories[localId] = WhappyStatus(
            id = localId,
            authorId = user.uid,
            authorName = accountName(),
            text = text.trim(),
            tone = "personal",
            createdAt = System.currentTimeMillis(),
            mediaUrl = mediaUri?.toString().orEmpty(),
            mediaKind = mediaKind,
            mediaName = if (mediaUri == null) "" else "Publication en cours",
        )
        _uiState.update { it.copy(statuses = mergeStories(it.statuses), actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.publishStatus(user.uid, accountName(), text, "personal", mediaUri, mediaContentType) }
                .onSuccess { story ->
                    pendingStories.remove(localId)
                    confirmedStories[story.id] = story
                    _uiState.update { current -> current.copy(statuses = mergeStories(current.statuses), actionBusy = false, online = true) }
                }
                .onFailure { error ->
                    pendingStories.remove(localId)
                    _uiState.update { current -> current.copy(statuses = mergeStories(current.statuses), actionBusy = false, error = "La Story n’a pas été publiée : ${error.localizedMessage ?: "vérifiez la connexion"}") }
                }
        }
    }

    private fun mergeStories(remote: List<WhappyStatus>): List<WhappyStatus> {
        val remoteIds = remote.mapTo(hashSetOf()) { it.id }
        remoteIds.forEach(confirmedStories::remove)
        return (pendingStories.values + confirmedStories.values + remote)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
    }

    fun deleteStatus(statusId: String) = runBusinessAction("La Story n’a pas été supprimée") { user ->
        repository.deleteStatus(user.uid, statusId)
    }

    fun saveWepiSettings(settings: WapiWepiSettings) = runBusinessAction("Les réglages WEPI n’ont pas été enregistrés") { user ->
        repository.saveWepiSettings(settings.copy(ownerId = user.uid))
    }

    fun publishRadioEpisode(stationName: String, title: String, uri: Uri, durationSeconds: Long) = runBusinessAction("Le podcast n’a pas été publié") { user ->
        repository.publishRadioEpisode(user.uid, accountName(), stationName, title, uri, durationSeconds)
    }

    fun updateProfilePhoto(uri: Uri, contentType: String) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.updateProfilePhoto(user.uid, uri, contentType) }
                .onSuccess { url -> _uiState.update { it.copy(accountPhotoUrl = url, actionBusy = false, online = true) } }
                .onFailure { _uiState.update { it.copy(actionBusy = false, error = "La photo de profil n’a pas été enregistrée") } }
        }
    }

    fun endLive(liveId: String) = runBusinessAction("Le direct n’a pas pu être terminé") { user ->
        repository.endLive(user.uid, liveId)
        refreshVisibleLives()
    }

    fun updateLiveStatus(liveId: String, status: String) = runBusinessAction("Le statut du Live n’a pas été mis à jour") { user ->
        repository.updateLiveStatus(user.uid, liveId, status)
        refreshVisibleLives()
    }

    private suspend fun refreshVisibleLives() {
        val lives = repository.loadVisibleLives()
        _uiState.update { it.copy(lives = lives, online = true) }
    }

    fun createDeal(page: WhappyBusinessPage, title: String, description: String, originalPrice: Long, dealPrice: Long, stock: Int, durationDays: Int) = runBusinessAction("Le Deal n’a pas été publié") { user ->
        repository.createDeal(user.uid, page, title, description, originalPrice, dealPrice, stock, durationDays)
    }

    fun updateDealStatus(dealId: String, status: String) = runBusinessAction("Le Deal n’a pas été mis à jour") { user ->
        repository.updateDealStatus(user.uid, dealId, status)
    }

    fun markPaymentNoticeRead(noticeId: String) {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            runCatching { repository.markPaymentNoticeRead(user.uid, noticeId) }
                .onFailure { _uiState.update { it.copy(error = "La notification n’a pas été mise à jour") } }
        }
    }

    @Suppress("DEPRECATION")
    fun registerPushNotifications() {
        val user = _uiState.value.user ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch { runCatching { repository.registerDeviceToken(user.uid, token) } }
        }
    }

    private fun runBusinessAction(errorMessage: String, action: suspend (com.google.firebase.auth.FirebaseUser) -> Unit) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.actionBusy) return
        _uiState.update { it.copy(actionBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { action(user) }
                .onSuccess { _uiState.update { it.copy(actionBusy = false, online = true) } }
                .onFailure { _uiState.update { it.copy(actionBusy = false, error = errorMessage) } }
        }
    }

    fun saveTwinConsent(consent: Boolean) = runTwinAction("Les autorisations du Jumeau numérique n’ont pas été synchronisées") { user ->
        repository.saveTwinConsent(user.uid, accountName(), consent)
    }

    fun uploadTwinAsset(uri: Uri, kind: String, contentType: String) = runTwinAction("La capture du Jumeau numérique n’a pas été synchronisée") { user ->
        val profile = _uiState.value.twinProfile
        require(profile?.identityConsent == true && profile.voiceConsent && profile.movementConsent) { "twin-consent-required" }
        repository.uploadTwinAsset(user.uid, uri, kind, contentType)
    }

    fun createTwinAutomation(name: String, trigger: String, channel: String, action: String, script: String) = runTwinAction("La mission du Jumeau numérique n’a pas été créée") { user ->
        val profile = _uiState.value.twinProfile
        require(profile?.identityConsent == true && profile.voiceConsent && profile.movementConsent) { "twin-consent-required" }
        repository.createTwinAutomation(user.uid, name, trigger, channel, action, script)
    }

    fun toggleTwinAutomation(automationId: String, enabled: Boolean) = runTwinAction("La mission du Jumeau numérique n’a pas été modifiée") { user ->
        repository.toggleTwinAutomation(user.uid, automationId, enabled)
    }

    fun deleteTwinAutomation(automationId: String) = runTwinAction("La mission du Jumeau numérique n’a pas été supprimée") { user ->
        repository.deleteTwinAutomation(user.uid, automationId)
    }

    fun createTwinRender(title: String, script: String, language: String, gestures: List<String>) = runTwinAction("La production du Jumeau numérique n’a pas été préparée") { user ->
        val profile = _uiState.value.twinProfile
        require(profile?.isRenderReady == true) { "twin-captures-required" }
        repository.createTwinRender(user.uid, title, script, language, gestures)
    }

    private fun runTwinAction(errorMessage: String, action: suspend (com.google.firebase.auth.FirebaseUser) -> Unit) {
        val user = _uiState.value.user ?: return
        if (_uiState.value.twinBusy) return
        _uiState.update { it.copy(twinBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { action(user) }
                .onSuccess { _uiState.update { it.copy(twinBusy = false, online = true) } }
                .onFailure { failure ->
                    val message = when {
                        failure.message == "twin-consent-required" -> "Activez d’abord les trois autorisations du Jumeau numérique, puis relancez la capture."
                        failure.message == "twin-captures-required" -> "Le Jumeau numérique a besoin du portrait, de la voix et des mouvements avant de préparer une vidéo."
                        failure is StorageException -> "Le fichier n’a pas été transféré. Vérifiez votre connexion et réessayez la capture."
                        failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Cette action a été bloquée par la sécurité. Vérifiez votre session et les autorisations du Jumeau numérique."
                        failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Connexion indisponible. Votre capture reste sur cet appareil : réessayez lorsque le réseau revient."
                        else -> errorMessage
                    }
                    _uiState.update { it.copy(twinBusy = false, error = message, online = failure !is FirebaseFirestoreException || failure.code != FirebaseFirestoreException.Code.UNAVAILABLE) }
                }
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
        contactsListener?.remove()
        messagesListener?.remove()
        channelsListener?.remove()
        channelPostsListener?.remove()
        listingsListener?.remove()
        businessListener?.remove()
        campaignsListener?.remove()
        livesListener?.remove()
        statusesListener?.remove()
        dealsListener?.remove()
        paymentNoticesListener?.remove()
        twinProfileListener?.remove()
        wepiSettingsListener?.remove()
        radioEpisodesListener?.remove()
        twinAutomationsListener?.remove()
        twinRendersListener?.remove()
        conversationsListener = null
        contactsListener = null
        messagesListener = null
        channelsListener = null
        channelPostsListener = null
        listingsListener = null
        businessListener = null
        campaignsListener = null
        livesListener = null
        statusesListener = null
        dealsListener = null
        paymentNoticesListener = null
        twinProfileListener = null
        wepiSettingsListener = null
        radioEpisodesListener = null
        twinAutomationsListener = null
        twinRendersListener = null
        _uiState.update { WhappyUiState(user = user, loading = user != null, sessionRestoring = user != null) }
        if (user == null) {
            _uiState.update { it.copy(sessionRestoring = false) }
            return
        }
        repository.schedulePendingMessageSync()
        resolveAccountProfile(user)
        conversationsListener = repository.observeConversations(
            user.uid,
            onChange = { conversations -> _uiState.update { current -> current.copy(conversations = conversations, selectedConversation = current.selectedConversation?.let { selected -> conversations.firstOrNull { it.id == selected.id } ?: selected }, loading = false, online = true) } },
            onError = { _uiState.update { it.copy(loading = false, online = false, error = "Synchronisation momentanément indisponible") } },
        )
        channelsListener = repository.observeChannels(
            onChange = { channels ->
                _uiState.update { current -> current.copy(channels = channels, selectedChannel = current.selectedChannel?.let { selected -> channels.firstOrNull { it.id == selected.id } ?: selected }, online = true) }
                pendingChannelId?.let { id -> channels.firstOrNull { it.id == id }?.let { channel -> pendingChannelId = null; openChannel(channel) } }
            },
            onError = { _uiState.update { it.copy(online = false, error = "Les chaînes sont momentanément indisponibles") } },
        )
        contactsListener = repository.observeContacts(
            user.uid,
            onChange = { contacts -> _uiState.update { it.copy(contacts = contacts, online = true) } },
            onError = { _uiState.update { it.copy(online = false, error = "Les Contacts sont momentanément indisponibles") } },
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
        livesListener = repository.observeLives(
            onChange = { items -> _uiState.update { it.copy(lives = items, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        statusesListener = repository.observeStatuses(
            onChange = { items -> _uiState.update { it.copy(statuses = mergeStories(items), online = true) } },
            // A feed listener must never block the whole app with a modal. Keep
            // the last stories on screen and let the Stories screen retry when
            // connectivity or Firestore catches up.
            onError = { _uiState.update { it.copy(online = false) } },
        )
        dealsListener = repository.observeDeals(
            user.uid,
            onChange = { items -> _uiState.update { it.copy(deals = items, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        paymentNoticesListener = repository.observePaymentNotices(
            user.uid,
            onChange = { items -> _uiState.update { it.copy(paymentNotices = items, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        registerPushNotifications()
        twinProfileListener = repository.observeTwinProfile(
            user.uid,
            onChange = { profile -> _uiState.update { it.copy(twinProfile = profile, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        wepiSettingsListener = repository.observeWepiSettings(
            user.uid,
            onChange = { settings -> _uiState.update { it.copy(wepiSettings = settings, online = true) } },
            onError = { _uiState.update { it.copy(online = false) } },
        )
        radioEpisodesListener = repository.observeRadioEpisodes(
            onChange = { episodes -> _uiState.update { it.copy(radioEpisodes = episodes, online = true) } },
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
            val resolvedPhotoUrl = runCatching { repository.restoreAccountPhotoUrl(user) }
                .getOrDefault(user.photoUrl?.toString().orEmpty())
            val restoredName = AccountSessionPolicy.displayName(
                resolvedName = resolvedName,
                phoneNumber = user.phoneNumber.orEmpty(),
                creationTimestamp = user.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            )
            runCatching { repository.syncAccountRecord(user, restoredName) }
            _uiState.update { current ->
                if (current.user?.uid != user.uid) current
                else current.copy(accountDisplayName = restoredName, accountPhotoUrl = resolvedPhotoUrl, sessionRestoring = false)
            }
        }
    }

    private fun accountName(): String {
        val phone = _uiState.value.user?.phoneNumber.orEmpty()
        return if (WhappyIdentity.isFounder(phone)) WhappyIdentity.founderName
        else _uiState.value.accountDisplayName.ifBlank { WhappyIdentity.fallbackAccountName }
    }

    private fun currentMember(user: com.google.firebase.auth.FirebaseUser): WhappyMember = WhappyMember(
        uid = user.uid,
        displayName = accountName(),
        phoneNumber = user.phoneNumber.orEmpty(),
        photoUrl = _uiState.value.accountPhotoUrl.ifBlank { user.photoUrl?.toString().orEmpty() },
    )

    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        conversationsListener?.remove()
        contactsListener?.remove()
        messagesListener?.remove()
        channelsListener?.remove()
        channelPostsListener?.remove()
        listingsListener?.remove()
        businessListener?.remove()
        campaignsListener?.remove()
        livesListener?.remove()
        statusesListener?.remove()
        dealsListener?.remove()
        paymentNoticesListener?.remove()
        twinProfileListener?.remove()
        wepiSettingsListener?.remove()
        radioEpisodesListener?.remove()
        twinAutomationsListener?.remove()
        twinRendersListener?.remove()
    }
}
