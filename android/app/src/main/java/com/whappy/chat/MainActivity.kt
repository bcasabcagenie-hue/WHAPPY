package com.whappy.chat

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val phoneAuth = PhoneAuthController(this)
        val preview = BuildConfig.DEBUG && intent.getBooleanExtra("preview_home", false)
        setContent {
            val model: WhappyViewModel = viewModel()
            val state by model.uiState.collectAsStateWithLifecycle()
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    WhappyNotifications.ensureChannel(this)
                    model.registerPushNotifications()
                }
            }
            WhappyTheme {
                WhappyRoot(
                    state = state,
                    preview = preview,
                    phoneAuth = phoneAuth,
                    onTab = model::selectTab,
                    onOpenConversation = model::openConversation,
                    onCloseConversation = model::closeConversation,
                    onSendMessage = model::sendMessage,
                    onSendMedia = model::sendMedia,
                    onSearchContact = model::searchContact,
                    onAddSearchedContact = model::addSearchedContact,
                    onClearContactSearch = model::clearContactSearch,
                    onOpenContact = model::openContact,
                    onSearchBusinesses = model::searchBusinesses,
                    onContactBusiness = model::contactBusiness,
                    onPublishListing = model::publishListing,
                    onCreateBusinessPage = model::createBusinessPage,
                    onUpdateBusinessPage = model::updateBusinessPage,
                    onCreateCampaign = model::createCampaign,
                    onCreateLive = model::createLive,
                    onUpdateProfilePhoto = model::updateProfilePhoto,
                    onEndLive = model::endLive,
                    onUpdateLiveStatus = model::updateLiveStatus,
                    onCreateDeal = model::createDeal,
                    onUpdateDealStatus = model::updateDealStatus,
                    onMarkPaymentRead = model::markPaymentNoticeRead,
                    onEnableNotifications = {
                        WhappyNotifications.ensureChannel(this)
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else model.registerPushNotifications()
                    },
                    onSaveTwinConsent = model::saveTwinConsent,
                    onUploadTwinAsset = model::uploadTwinAsset,
                    onCreateTwinAutomation = model::createTwinAutomation,
                    onToggleTwinAutomation = model::toggleTwinAutomation,
                    onDeleteTwinAutomation = model::deleteTwinAutomation,
                    onCreateTwinRender = model::createTwinRender,
                    onDismissError = model::clearError,
                    onSignOut = model::signOut,
                    onProfileSaved = model::refreshAccountProfile,
                )
            }
        }
    }
}

enum class AuthStage { PHONE, CODE, PROFILE }

data class PhoneAuthUiState(
    val stage: AuthStage = AuthStage.PHONE,
    val busy: Boolean = false,
    val status: String = "",
    val error: String = "",
    val phoneNumber: String = "",
)

class PhoneAuthController(private val activity: Activity) {
    var state by androidx.compose.runtime.mutableStateOf(PhoneAuthUiState())
        private set
    private var verificationId = ""
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private val preferences = activity.getSharedPreferences("whappy_auth", Activity.MODE_PRIVATE)

    val savedCountryCode: String get() = preferences.getString("country_code", "+242") ?: "+242"
    val savedPhoneNumber: String get() = preferences.getString("phone_number", "") ?: ""

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signIn(credential)
        }

        override fun onVerificationFailed(error: FirebaseException) {
            state = state.copy(
                busy = false,
                status = "",
                error = error.localizedMessage ?: "Le numéro n’a pas pu être vérifié.",
            )
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            state = state.copy(stage = AuthStage.CODE, busy = false, status = "Code SMS envoyé", error = "")
        }
    }

    fun sendCode(countryCode: String, phone: String) {
        val formatted = PhoneNumberFormatter.normalize(countryCode, phone)
        if (formatted == null) {
            state = state.copy(
                busy = false,
                status = "",
                error = if (countryCode == "+242") "Entrez 9 chiffres, par exemple 06 123 45 67." else "Vérifiez le numéro et l’indicatif du pays.",
            )
            return
        }
        preferences.edit().putString("country_code", countryCode).putString("phone_number", formatted).apply()
        state = PhoneAuthUiState(stage = AuthStage.PHONE, busy = true, status = "Envoi sécurisé du code…", phoneNumber = formatted)
        requestVerification(formatted, null)
    }

    fun resendCode() {
        val phone = state.phoneNumber.ifBlank { savedPhoneNumber }
        val token = resendToken
        if (phone.isBlank() || token == null) {
            state = state.copy(error = "Patientez avant de demander un nouveau code.")
            return
        }
        state = state.copy(busy = true, status = "Nouvel envoi du code…", error = "")
        requestVerification(phone, token)
    }

    private fun requestVerification(phoneNumber: String, forceToken: PhoneAuthProvider.ForceResendingToken?) {
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (forceToken != null) options.setForceResendingToken(forceToken)
        PhoneAuthProvider.verifyPhoneNumber(options.build())
    }

    fun verifyCode(code: String) {
        if (verificationId.isBlank() || code.filter(Char::isDigit).length != 6) {
            state = state.copy(error = "Entrez le code SMS à 6 chiffres.")
            return
        }
        state = state.copy(busy = true, error = "", status = "Vérification…")
        signIn(PhoneAuthProvider.getCredential(verificationId, code))
    }

    fun saveProfile(name: String, onSaved: () -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val value = name.trim()
        if (user == null || value.length < 2) {
            state = state.copy(error = "Indiquez votre nom public.")
            return
        }
        state = PhoneAuthUiState(stage = AuthStage.PROFILE, busy = true, status = "Création du profil…")
        val change = UserProfileChangeRequest.Builder().setDisplayName(value).build()
        user.updateProfile(change).addOnSuccessListener {
            FirebaseFirestore.getInstance().collection("users").document(user.uid).set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to value,
                    "phoneNumber" to user.phoneNumber.orEmpty(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).addOnSuccessListener {
                state = PhoneAuthUiState(stage = AuthStage.PROFILE, status = "Profil prêt")
                preferences.edit().remove("phone_number").apply()
                onSaved()
            }.addOnFailureListener { state = state.copy(busy = false, error = "Le profil n’a pas pu être enregistré.") }
        }.addOnFailureListener { state = state.copy(busy = false, error = "Le profil n’a pas pu être créé.") }
    }

    fun back() {
        state = PhoneAuthUiState(stage = AuthStage.PHONE, phoneNumber = state.phoneNumber)
    }

    fun requireProfile() {
        if (state.stage != AuthStage.PROFILE) state = PhoneAuthUiState(stage = AuthStage.PROFILE)
    }

    private fun signIn(credential: PhoneAuthCredential) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { result ->
                state = PhoneAuthUiState(
                    stage = AuthStage.PHONE,
                    status = "Compte vérifié",
                    phoneNumber = result.user?.phoneNumber.orEmpty(),
                )
            }
            .addOnFailureListener { state = state.copy(busy = false, status = "", error = "Code incorrect ou expiré.") }
    }
}
