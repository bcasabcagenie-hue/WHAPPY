import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("affiche la connexion téléphonique Wapi côté serveur", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Wapi — Tout peut devenir une opportunité<\/title>/i);
  assert.match(html, /Logo Wapi App/);
  assert.match(html, /UN NUMÉRO\. UN COMPTE\./);
  assert.match(html, /Entrez votre numéro/);
  assert.match(html, /Congo \(\+242\)/);
  assert.match(html, /Continuer par SMS/);
  assert.match(html, /Un numéro = un compte Wapi App/);
  assert.match(html, /Télécharger l&#x27;application/);
  assert.match(html, /Android 8\.0\+/);
  assert.match(html, /Le téléchargement ne démarre pas/);
  assert.match(html, /WAPI-Android-1\.9\.4-native\.apk/);
  assert.doesNotMatch(html, /Fusioniox|site-creator-vinext-starter/i);
});

test("garde l’accueil et le studio WHAPPY natifs utilisables", async () => {
  const [activity, ui, repository, viewModel, models, manifest] = await Promise.all([
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/MainActivity.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyUi.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyRepository.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyViewModel.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyModels.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/AndroidManifest.xml", import.meta.url), "utf8"),
  ]);
  assert.match(activity, /setContent/);
  assert.match(activity, /PhoneNumberFormatter\.normalize/);
  assert.match(activity, /resendCode/);
  assert.match(activity, /onProfileSaved/);
  assert.match(ui, /Jetpack|WhappyBottomBar|LazyColumn|BrandHeader/);
  assert.match(ui, /Spacer\(Modifier\.width\(14\.dp\)\)/);
  assert.match(repository, /addSnapshotListener/);
  assert.match(repository, /collection\("messages"\)/);
  assert.match(repository, /sendMediaMessage/);
  assert.match(repository, /FirebaseStorage/);
  assert.match(ui, /EmojiTray/);
  assert.match(ui, /Vérifier l’autorisation Android/);
  assert.match(ui, /CreateGroupDialog/);
  assert.match(ui, /Ajouter une photo de groupe/);
  assert.doesNotMatch(ui, /HorizontalDivider\(color = WhappyLine/);
  assert.match(ui, /createVoiceRecorder/);
  assert.match(ui, /autoCorrectEnabled = true/);
  assert.match(ui, /WhappyStudioScreen/);
  assert.match(ui, /WAPI Live/);
  assert.match(ui, /BusinessSection/);
  assert.match(ui, /Notifications de paiement/);
  assert.match(ui, /Créer une offre/);
  assert.match(ui, /ModalBottomSheet/);
  assert.match(ui, /Centre d’activité/);
  assert.match(ui, /CallsScreen/);
  assert.match(ui, /WhappyTab\.CALLS/);
  assert.match(ui, /LiveRoomDialog/);
  assert.match(ui, /LiveCameraPreview/);
  assert.doesNotMatch(ui, /StatusScreen/);
  assert.match(ui, /Langue de l’application/);
  assert.match(ui, /La photo reste fixe/);
  assert.match(ui, /EditBusinessPageDialog/);
  assert.match(ui, /Suspendre/);
  assert.match(ui, /WAPI JUMEAU NUMÉRIQUE/);
  assert.match(ui, /IDENTITÉ SOUVERAINE/);
  assert.match(ui, /VOICE DNA/);
  assert.match(ui, /MOTION CORE 2\.0/);
  assert.match(ui, /ORCHESTRATEUR/);
  assert.match(ui, /Créé avec le Jumeau numérique IA/);
  assert.match(repository, /observeTwinProfile/);
  assert.match(repository, /uploadTwinAsset/);
  assert.match(repository, /createTwinAutomation/);
  assert.match(repository, /createTwinRender/);
  assert.match(repository, /createLive/);
  assert.match(repository, /updateProfilePhoto/);
  assert.match(repository, /collection\("groups"\)/);
  assert.match(repository, /source = "groups"/);
  assert.match(repository, /groups\/\$\{reference\.id\}/);
  assert.match(ui, /WhappyFeatureHubDialog/);
  assert.match(ui, /Tout WAPI/);
  assert.match(repository, /profiles\/\$userId\/avatar-/);
  assert.match(ui, /Changer la photo/);
  assert.match(ui, /Ouvrir le studio/);
  assert.match(ui, /Caméra, micro et diffusion sous votre contrôle/);
  assert.match(ui, /LivePreflightDialog/);
  assert.match(ui, /VoiceNoteMessage/);
  assert.match(ui, /deal-creator/);
  assert.match(ui, /Catalogue/);
  assert.match(ui, /Centre de commandes/);
  assert.match(ui, /Performances Business/);
  assert.match(ui, /WapiBitmapMemoryCache/);
  assert.match(ui, /LruCache/);
  assert.match(repository, /createDeal/);
  assert.match(repository, /registerDeviceToken/);
  assert.match(repository, /updateBusinessPage/);
  assert.match(repository, /updateLiveStatus/);
  assert.match(repository, /updateDealStatus/);
  assert.match(repository, /restoreAccountDisplayName/);
  assert.match(viewModel, /refreshSession/);
  assert.match(viewModel, /observeTwinProfile/);
  assert.match(viewModel, /observeTwinAutomations/);
  assert.match(viewModel, /observeTwinRenders/);
  assert.match(viewModel, /selectedMembers[\s\S]*filter \{ it\.uid\.isNotBlank\(\)/);
  assert.match(models, /WhappyTwinProfile/);
  assert.match(models, /WhappyTwinAutomation/);
  assert.match(models, /WhappyTwinRender/);
  assert.match(models, /WhappyLive/);
  assert.match(models, /WhappyStatus/);
  assert.match(models, /WhappyDeal/);
  assert.match(models, /WhappyPaymentNotice/);
  assert.match(models, /sessionRestoring/);
  assert.match(models, /accountDisplayName/);
  assert.match(manifest, /androidx\.core\.content\.FileProvider/);
  assert.match(manifest, /USE_FULL_SCREEN_INTENT/);
  const [notifications, calls, androidBuild, fastStorage, outbox, messageSync] = await Promise.all([
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyMessagingService.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyCalls.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/build.gradle", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyFastStorage.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyMessageOutbox.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyMessageSync.kt", import.meta.url), "utf8"),
  ]);
  assert.match(notifications, /NotificationCompat\.CallStyle\.forIncomingCall/);
  assert.match(notifications, /wapi_messages_v5/);
  assert.match(notifications, /whappy_calls_v3/);
  assert.match(notifications, /TYPE_NOTIFICATION/);
  assert.match(notifications, /USAGE_NOTIFICATION\)/);
  assert.match(calls, /Sonnerie…/);
  assert.match(calls, /Décrocher/);
  assert.match(calls, /0xFF22C55E/);
  assert.match(calls, /0xFFEF4444/);
  assert.match(calls, /ToneGenerator\.TONE_SUP_RINGTONE/);
  assert.match(androidBuild, /emoji2:1\.5\.0/);
  assert.match(androidBuild, /com\.tencent:mmkv:2\.4\.1/);
  assert.match(androidBuild, /work-runtime-ktx:2\.10\.1/);
  assert.match(androidBuild, /versionName "1\.9\.4-native"/);
  assert.match(fastStorage, /MMKV\.SINGLE_PROCESS_MODE, cryptKey/);
  assert.match(outbox, /WhappyCryptoVault\.encrypt/);
  assert.match(messageSync, /NetworkType\.CONNECTED/);
  assert.match(repository, /clientMessageId/);
  assert.match(repository, /pendingMessages/);
  assert.match(viewModel, /retryPendingMessages/);
  assert.match(ui, /en attente de connexion/);
  assert.match(ui, /Nouvelle tentative…/);
  assert.match(ui, /Faire une offre/);
  assert.match(ui, /image ou une vidéo/);
  assert.match(ui, /offerMediaPicker/);
  assert.doesNotMatch(ui, /Publier le statut/);
  assert.match(repository, /stories\/\$userId/);
});

test("conserve l'identité et la configuration autonome de Wapi", async () => {
  const [page, layout, packageJson, readme, logo, firebase, rules, dataLayer, commerce, calls, profile, seller, orders, superHub, groupActivities, realTimeInbox, callData, businessStudio, businessData, twinEngine, twinData, storageRules, expressionHub, pulse, wepiAssistant, wepiData] = await Promise.all([
    readFile(new URL("../app/components/WhappyClientApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../README.md", import.meta.url), "utf8"),
    readFile(new URL("../public/wapi-logo.svg", import.meta.url), "utf8"),
    readFile(new URL("../lib/firebase.ts", import.meta.url), "utf8"),
    readFile(new URL("../firestore.rules", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-data.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/components/CommercePanels.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/CallRoom.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-profile.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/components/SellerDashboard.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/OrdersPanel.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/SuperHub.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/GroupActivities.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/RealTimeInbox.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-calls.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/components/BusinessStudio.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-business.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/components/TwinEngineStudio.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-twin.ts", import.meta.url), "utf8"),
    readFile(new URL("../storage.rules", import.meta.url), "utf8"),
    readFile(new URL("../app/components/WhappyExpressionHub.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/WhappyPulse.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/WepiAssistant.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-wepi.ts", import.meta.url), "utf8"),
  ]);

  assert.match(page, /src="\/whappy-app-icon\.png"/);
  assert.match(page, /type Space = .*"business"/);
  assert.match(page, /useState<Space>\("inbox"\)/);
  assert.match(page, /Votre image, votre contrôle/i);
  assert.match(page, /ma propre image/i);
  assert.match(page, /WAPI LIVE SHIFT/i);
  assert.match(page, /Vous commencez.*Votre Jumeau numérique continue/is);
  assert.match(page, /Passage de relais/);
  assert.match(page, /Publication sponsorisée/);
  assert.match(page, /Ce qui se passe/);
  assert.match(page, /Tout le monde peut.*ouvrir sa boutique/is);
  assert.match(page, /Mobile Money/);
  assert.match(page, /Carte bancaire/);
  assert.match(page, /WAPI MARKET/i);
  assert.match(page, /ACHETEUSE FIABLE/);
  assert.match(page, /ÉCHANGES RÉUSSIS/);
  assert.match(page, /UN NUMÉRO\. UN COMPTE/);
  assert.match(page, /signInWithPhoneNumber/);
  assert.match(page, /RecaptchaVerifier/);
  assert.match(page, /appVerificationDisabledForTesting/);
  assert.match(page, /hasPhone && hasProfile/);
  assert.match(layout, /title:\s*"Wapi — Tout peut devenir une opportunité"/);
  assert.match(packageJson, /"name": "whappy"/);
  assert.match(readme, /réseau d'opportunités autonome/i);
  assert.match(readme, /prêt à être développé dans Visual Studio Code/i);
  assert.match(logo, /#0094f0/i);
  assert.match(firebase, /getFirestore/);
  assert.match(firebase, /getAuth/);
  assert.match(rules, /request\.auth\.uid/);
  assert.match(rules, /match \/listings\/\{listingId\}/);
  assert.match(rules, /match \/requests\/\{requestId\}/);
  assert.match(dataLayer, /watchWhappyData/);
  assert.match(dataLayer, /publishListing/);
  assert.match(dataLayer, /uploadBytes/);
  assert.match(dataLayer, /updateListing/);
  assert.match(dataLayer, /removeListing/);
  assert.match(dataLayer, /watchConversationMessages/);
  assert.match(dataLayer, /sendConversationMessage/);
  assert.match(dataLayer, /collection\(conversation, "messages"\)/);
  assert.match(dataLayer, /watchUserOrders/);
  assert.match(dataLayer, /createOrder/);
  assert.match(dataLayer, /cancelOrder/);
  assert.match(dataLayer, /watchGroupMessages/);
  assert.match(dataLayer, /sendGroupMessage/);
  assert.match(dataLayer, /watchGroupActivities/);
  assert.match(dataLayer, /createGroupActivity/);
  assert.match(dataLayer, /watchGroupActivityResponses/);
  assert.match(dataLayer, /saveGroupActivityResponse/);
  assert.match(dataLayer, /watchUserGroups/);
  assert.match(dataLayer, /createGroup/);
  assert.match(rules, /request\.resource\.data\.text\.size\(\) <= 4000/);
  assert.match(rules, /match \/orders\/\{orderId\}/);
  assert.match(rules, /match \/groups\/\{groupId\}/);
  assert.match(rules, /match \/activities\/\{activityId\}/);
  assert.match(rules, /match \/calls\/\{callId\}/);
  assert.match(rules, /match \/responses\/\{responseId\}/);
  assert.match(rules, /match \/businessPages\/\{pageId\}/);
  assert.match(rules, /match \/adCampaigns\/\{campaignId\}/);
  assert.match(rules, /match \/adEvents\/\{eventId\}/);
  assert.match(commerce, /ProductPanel/);
  assert.match(commerce, /CartPanel/);
  assert.match(commerce, /Paiement à la livraison/);
  assert.match(calls, /getUserMedia/);
  assert.match(calls, /RTCPeerConnection/);
  assert.match(calls, /createOffer/);
  assert.match(calls, /createAnswer/);
  assert.match(profile, /setDoc/);
  assert.match(profile, /phoneNumber/);
  assert.match(seller, /Tableau de bord vendeur/);
  assert.match(seller, /boutique-whappy\.csv/);
  assert.match(seller, /Confirmer/);
  assert.match(page, /ContactsSpace/);
  assert.match(page, /SuperHub/);
  assert.match(orders, /Mes commandes/);
  assert.match(orders, /Télécharger le récapitulatif/);
  assert.match(orders, /Confirmer l’annulation/);
  assert.match(superHub, /Les bonnes personnes/);
  assert.match(superHub, /WAPI PAY/);
  assert.match(superHub, /Mobile Money/);
  assert.match(superHub, /MINI-SERVICES (?:WHAPPY|WAPI)/);
  assert.match(superHub, /QRCode\.toDataURL/);
  assert.match(superHub, /whappy-services/);
  assert.match(superHub, /Paiement test/);
  assert.match(superHub, /createServiceRequest/);
  assert.match(superHub, /Aucun service trouvé/);
  assert.match(page, /whappy-demo-workspace/);
  assert.match(page, /demoOffer/);
  assert.match(superHub, /Salon sécurisé/);
  assert.match(superHub, /Appel de groupe/);
  assert.match(superHub, /Créateur du groupe/);
  assert.match(superHub, /onCreateGroup/);
  assert.match(superHub, /Compte synchronisé/);
  assert.match(groupActivities, /Créer un sondage/);
  assert.match(groupActivities, /Planifier un événement/);
  assert.match(groupActivities, /ANNONCE ADMIN/);
  assert.match(realTimeInbox, /Messages en temps réel/);
  assert.match(realTimeInbox, /écrit en ce moment/);
  assert.match(realTimeInbox, /markDirectConversationRead/);
  assert.match(realTimeInbox, /MediaRecorder/);
  assert.match(realTimeInbox, /sendDirectAttachment/);
  assert.match(realTimeInbox, /Message vocal envoyé/);
  assert.match(realTimeInbox, /Historique des appels/);
  assert.match(realTimeInbox, /initialView/);
  assert.match(realTimeInbox, /watchCallHistory/);
  assert.match(realTimeInbox, /embedded/);
  assert.match(dataLayer, /uploadBytes/);
  assert.match(dataLayer, /kind:"image"\|"audio"\|"video"/);
  assert.match(realTimeInbox, /Entrée pour envoyer/);
  assert.match(realTimeInbox, /spellCheck/);
  assert.match(realTimeInbox, /WhappyExpressionHub/);
  assert.match(realTimeInbox, /watchWepiSettings/);
  assert.match(realTimeInbox, /buildWepiReply/);
  assert.match(expressionHub, /(?:WHAPPY|WAPI) EXPRESSION HUB/);
  assert.match(expressionHub, /WHAPPIES/);
  assert.match(expressionHub, /translateTextOnDevice/);
  assert.match(expressionHub, /SpeechRecognition/);
  assert.match(expressionHub, /Proofreader/);
  assert.match(expressionHub, /(?:WHAPPY|WAPI) MEME LAB/);
  assert.match(storageRules, /contentType\.matches\('video\/\.\*'\)/);
  assert.match(callData, /watchIncomingCalls/);
  assert.match(callData, /watchCallHistory/);
  assert.match(callData, /addCallCandidate/);
  assert.match(businessStudio, /(?:WHAPPY|WAPI) BUSINESS SUITE/);
  assert.match(businessStudio, /Créer une page/);
  assert.match(businessStudio, /Campagnes publicitaires/);
  assert.match(businessStudio, /IMPRESSIONS RÉELLES/);
  assert.match(businessStudio, /WEPI IA/);
  assert.match(businessStudio, /Répondez à vos utilisateurs avec WEPI/);
  assert.match(businessStudio, /WEPI IA pour votre Business/);
  assert.match(businessStudio, /Ouvrir l’interface WEPI/);
  assert.match(businessData, /watchActiveCampaigns/);
  assert.match(businessData, /recordAdEvent/);
  assert.match(businessData, /createAdCampaign/);
  assert.match(page, /TwinEngineStudio/);
  assert.match(page, /WhappyPulse/);
  assert.match(page, /label="Appels"/);
  assert.match(page, /CallsPreviewSpace/);
  assert.match(page, /metaKey \|\| event\.ctrlKey/);
  assert.match(pulse, /(?:WHAPPY|WAPI) PULSE/);
  assert.match(pulse, /Vos priorités/);
  assert.match(pulse, /MESSAGERIE PRIORITAIRE/);
  assert.match(pulse, /TOUT WAPI/);
  assert.match(page, /userId\|\|"local-preview"/);
  assert.match(page, /cloud=\{Boolean\(userId\)\}/);
  assert.match(twinEngine, /MOTION CORE 2\.0/);
  assert.match(twinEngine, /Ma voix IA/);
  assert.match(twinEngine, /Signature de mouvements/);
  assert.match(twinEngine, /speechSynthesis/);
  assert.match(twinEngine, /MISSIONS DU JUMEAU/);
  assert.match(twinEngine, /Vendeur 24\/7/);
  assert.match(twinEngine, /WEPI IA/);
  assert.match(twinEngine, /Portrait vidéo/);
  assert.match(twinEngine, /Aperçu complet/);
  assert.match(twinData, /twinAutomations/);
  assert.match(twinData, /twinRenders/);
  assert.match(rules, /match \/twinProfiles\/\{profileId\}/);
  assert.match(rules, /match \/wepi\/\{settingsId\}/);
  assert.match(wepiAssistant, /Activer WEPI/);
  assert.match(wepiAssistant, /Réponse automatique/);
  assert.match(wepiData, /buildWepiReply/);
  assert.match(wepiData, /saveWepiSettings/);
  assert.match(storageRules, /match \/twins\/\{userId\}/);
  assert.match(groupActivities, /Votre vote est enregistré/);
  assert.match(groupActivities, /Votre participation est confirmée/);
  assert.doesNotMatch(`${page}${layout}${packageJson}${readme}`, /Fusioniox/i);
});

test("sépare Actus, Stories privées et messagerie plein écran", async () => {
  const [ui, models, manifest, repository, viewModel, page, storyStudio, storyData, rules, indexes] = await Promise.all([
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyUi.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyModels.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/AndroidManifest.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyRepository.kt", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/whappy/chat/WhappyViewModel.kt", import.meta.url), "utf8"),
    readFile(new URL("../app/components/WhappyClientApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/StoryStudio.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-stories.ts", import.meta.url), "utf8"),
    readFile(new URL("../firestore.rules", import.meta.url), "utf8"),
    readFile(new URL("../firestore.indexes.json", import.meta.url), "utf8"),
  ]);
  assert.match(models, /STORIES\("Actus"\)/);
  assert.match(models, /CHANNELS\("Chaînes"\)/);
  assert.match(models, /PODCASTS\("Podcasts"\)/);
  assert.match(ui, /WhappyTab\.CALLS, WhappyTab\.STORIES, WhappyTab\.WEPI/);
  assert.match(ui, /StoryCircle/);
  assert.match(ui, /StoryViewerDialog/);
  assert.match(ui, /stories: List<WhappyStatus>/);
  assert.match(ui, /ActusQuickRow/);
  assert.match(ui, /"Chaînes"[\s\S]*"Radios"[\s\S]*"Podcasts"/);
  assert.match(manifest, /windowSoftInputMode="adjustResize"/);
  assert.match(ui, /background\(WapiChatBackground\)\.navigationBarsPadding\(\)\.imePadding\(\)/);
  assert.match(ui, /snapshotFlow/);
  assert.match(ui, /followLatest/);
  assert.match(viewModel, /pendingStories/);
  assert.match(repository, /whereArrayContains\("audienceIds", viewerId\)/);
  assert.match(repository, /"audienceIds" to audienceIds/);
  assert.match(rules, /request\.auth\.uid in resource\.data\.audienceIds/);
  assert.match(indexes, /"fieldPath": "audienceIds"/);
  assert.match(page, /function UpdatesSpace/);
  assert.match(page, /<ChannelsSpace/);
  assert.match(page, /space:"podcasts"/);
  assert.match(page, /label="Actus"/);
  assert.match(storyStudio, /setStories\(\(current\) => \[created/);
  assert.match(storyData, /where\("audienceIds", "array-contains", userId\)/);
  assert.match(storyData, /File \| null/);
});

test("sépare les Chaînes éditoriales des Salles et des Stories", async () => {
  const [page, channels, rooms, roomPilot, roomData, wepiData, rules] = await Promise.all([
    readFile(new URL("../app/components/WhappyClientApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/ChannelsSpace.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/RoomsSpace.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/WepiRoomPilot.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-rooms.ts", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-wepi.ts", import.meta.url), "utf8"),
    readFile(new URL("../firestore.rules", import.meta.url), "utf8"),
  ]);
  assert.match(page, /<ChannelsSpace/);
  assert.match(page, /label="Chaînes"/);
  assert.match(channels, /PUBLICATIONS OFFICIELLES/);
  assert.match(channels, /Pas de données de démonstration/);
  assert.doesNotMatch(channels, /demoRooms|demoPosts/);
  assert.match(page, /<RoomsSpace/);
  assert.match(rooms, /Salle Espérance/);
  assert.match(rooms, /Tech Congo/);
  assert.match(rooms, /Créer une salle/);
  assert.match(rooms, /Suivre cette salle/);
  assert.match(rooms, /RÈGLES DE LA SALLE/);
  assert.match(rooms, /WepiRoomPilot/);
  assert.match(roomPilot, /PILOTAGE/);
  assert.match(roomData, /export type RoomKind/);
  assert.match(roomData, /createWhappyRoom/);
  assert.match(roomData, /watchRoomPosts/);
  assert.match(roomData, /reactToRoomPost/);
  assert.match(wepiData, /buildWepiRoomSuggestion/);
  assert.match(wepiData, /saveWepiRoomPilot/);
  assert.match(rules, /match \/channels\/\{channelId\}/);
  assert.match(rules, /match \/pilot\/\{settingsId\}/);
  assert.match(rules, /match \/posts\/\{postId\}/);
});

test("prépare les notifications serveur sans les déployer", async () => {
  const [firebaseConfig, notificationFunctions, functionsPackage] = await Promise.all([
    readFile(new URL("../firebase.json", import.meta.url), "utf8"),
    readFile(new URL("../functions/src/index.ts", import.meta.url), "utf8"),
    readFile(new URL("../functions/package.json", import.meta.url), "utf8"),
  ]);
  assert.match(firebaseConfig, /"source": "functions"/);
  assert.match(notificationFunctions, /notifyNewMessage/);
  assert.match(notificationFunctions, /notifyIncomingCall/);
  assert.match(notificationFunctions, /sendEachForMulticast/);
  assert.match(notificationFunctions, /priority: "high"/);
  assert.match(functionsPackage, /"uuid": "\^11\.1\.1"/);
});

test("renforce les bases internationales, les profils, les groupes et les sons", async () => {
  const [page, countries, inbox, calls, sounds, groups, data, storageRules] = await Promise.all([
    readFile(new URL("../app/components/WhappyClientApp.tsx", import.meta.url), "utf8"),
    import(new URL("../lib/countries.ts", import.meta.url)),
    readFile(new URL("../app/components/RealTimeInbox.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/CallRoom.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-sounds.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/components/SuperHub.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-data.ts", import.meta.url), "utf8"),
    readFile(new URL("../storage.rules", import.meta.url), "utf8"),
  ]);
  assert.ok(countries.callingCountries.length >= 195);
  assert.ok(countries.callingCountries.some((country) => country.iso === "CG" && country.dialCode === "+242"));
  assert.ok(countries.callingCountries.some((country) => country.iso === "US" && country.dialCode === "+1"));
  assert.match(page, /callingCountries\.map/);
  assert.match(page, /profilePhotoUrl/);
  assert.match(page, /Photo de profil agrandie/);
  assert.match(inbox, /playMessageSound/);
  assert.match(calls, /startRingtone/);
  assert.match(calls, /playCallConnectedSound/);
  assert.match(sounds, /createOscillator/);
  assert.match(groups, /group-photo-picker/);
  assert.match(data, /photoUrl/);
  assert.match(storageRules, /match \/groups\/\{groupId\}/);
});

test("propose une offre premium avec image, vidéo, aperçu et sons", async () => {
  const [page, styles, sounds] = await Promise.all([
    readFile(new URL("../app/components/WhappyClientApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/studio.css", import.meta.url), "utf8"),
    readFile(new URL("../lib/whappy-sounds.ts", import.meta.url), "utf8"),
  ]);
  assert.match(page, /PremiumOfferModal/);
  assert.match(page, /name="offerMedia"/);
  assert.match(page, /accept="image\/\*,video\/\*"/);
  assert.match(page, /offer-media-preview/);
  assert.match(page, /DataTransfer/);
  assert.match(page, /playMediaAddedSound/);
  assert.match(page, /playOfferSuccessSound/);
  assert.match(styles, /offer-cinematic-in/);
  assert.match(styles, /offer-drop-pulse/);
  assert.match(styles, /prefers-reduced-motion/);
  assert.match(sounds, /export function playMediaAddedSound/);
  assert.match(sounds, /export function playOfferSuccessSound/);
});
