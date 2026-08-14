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

test("affiche l’accès direct Whappy côté serveur", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Whappy — Tout peut devenir une opportunité<\/title>/i);
  assert.match(html, /Logo Whappy/);
  assert.match(html, /ACCÈS DIRECT\. ZÉRO CAPTCHA\./);
  assert.match(html, /Connexion à votre compte/);
  assert.match(html, /Aucun CAPTCHA/);
  assert.match(html, /Votre session reste connectée/);
  assert.doesNotMatch(html, /recaptcha|Continuer par SMS/i);
  assert.doesNotMatch(html, /Fusioniox|site-creator-vinext-starter/i);
});

test("conserve l'identité et la configuration autonome de Whappy", async () => {
  const [page, layout, packageJson, readme, logo, firebase, rules, dataLayer, commerce, calls, profile, seller, orders, superHub, groupActivities, realTimeInbox, callData, businessStudio, businessData, twinEngine, twinData, storageRules, expressionHub] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../README.md", import.meta.url), "utf8"),
    readFile(new URL("../public/whappy-logo.svg", import.meta.url), "utf8"),
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
  ]);

  assert.match(page, /src="\/whappy-logo\.svg"/);
  assert.match(page, /type Space = .*"business"/);
  assert.match(page, /useState<Space>\("inbox"\)/);
  assert.match(page, /Votre image, votre contrôle/i);
  assert.match(page, /ma propre image/i);
  assert.match(page, /WHAPPY LIVE SHIFT/i);
  assert.match(page, /Vous commencez.*Votre Double continue/is);
  assert.match(page, /Passage de relais/);
  assert.match(page, /Publication sponsorisée/);
  assert.match(page, /Ce qui se passe/);
  assert.match(page, /Tout le monde peut.*ouvrir sa boutique/is);
  assert.match(page, /Mobile Money/);
  assert.match(page, /Carte bancaire/);
  assert.match(page, /Whappy Marketplace/i);
  assert.match(page, /ACHETEUSE FIABLE/);
  assert.match(page, /ÉCHANGES RÉUSSIS/);
  assert.match(page, /signInAnonymously/);
  assert.match(page, /ACCÈS DIRECT\. ZÉRO CAPTCHA/);
  assert.match(page, /WH-\$\{user\.uid\.slice/);
  assert.doesNotMatch(page, /RecaptchaVerifier|signInWithPhoneNumber|appVerificationDisabledForTesting/);
  assert.match(layout, /title:\s*"Whappy — Tout peut devenir une opportunité"/);
  assert.match(packageJson, /"name": "whappy"/);
  assert.match(readme, /réseau d'opportunités autonome/i);
  assert.match(readme, /prêt à être développé dans Visual Studio Code/i);
  assert.match(logo, /#13d713/i);
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
  assert.match(superHub, /WHAPPY PAY/);
  assert.match(superHub, /Mobile Money/);
  assert.match(superHub, /MINI-SERVICES WHAPPY/);
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
  assert.match(realTimeInbox, /watchCallHistory/);
  assert.match(realTimeInbox, /embedded/);
  assert.match(dataLayer, /uploadBytes/);
  assert.match(dataLayer, /kind:"image"\|"audio"\|"video"/);
  assert.match(realTimeInbox, /Entrée pour envoyer/);
  assert.match(realTimeInbox, /spellCheck/);
  assert.match(realTimeInbox, /WhappyExpressionHub/);
  assert.match(expressionHub, /WHAPPY EXPRESSION HUB/);
  assert.match(expressionHub, /WHAPPIES/);
  assert.match(expressionHub, /translateTextOnDevice/);
  assert.match(expressionHub, /SpeechRecognition/);
  assert.match(expressionHub, /Proofreader/);
  assert.match(expressionHub, /WHAPPY MEME LAB/);
  assert.match(storageRules, /contentType\.matches\('video\/\.\*'\)/);
  assert.match(callData, /watchIncomingCalls/);
  assert.match(callData, /watchCallHistory/);
  assert.match(callData, /addCallCandidate/);
  assert.match(businessStudio, /WHAPPY BUSINESS SUITE/);
  assert.match(businessStudio, /Créer une page/);
  assert.match(businessStudio, /Campagnes publicitaires/);
  assert.match(businessStudio, /IMPRESSIONS RÉELLES/);
  assert.match(businessData, /watchActiveCampaigns/);
  assert.match(businessData, /recordAdEvent/);
  assert.match(businessData, /createAdCampaign/);
  assert.match(page, /TwinEngineStudio/);
  assert.match(twinEngine, /MOTION CORE 2\.0/);
  assert.match(twinEngine, /Ma voix IA/);
  assert.match(twinEngine, /Signature de mouvements/);
  assert.match(twinEngine, /speechSynthesis/);
  assert.match(twinEngine, /MISSIONS DU DOUBLE/);
  assert.match(twinEngine, /Vendeur 24\/7/);
  assert.match(twinEngine, /Portrait vidéo/);
  assert.match(twinEngine, /Aperçu complet/);
  assert.match(twinData, /twinAutomations/);
  assert.match(twinData, /twinRenders/);
  assert.match(rules, /match \/twinProfiles\/\{profileId\}/);
  assert.match(storageRules, /match \/twins\/\{userId\}/);
  assert.match(groupActivities, /Votre vote est enregistré/);
  assert.match(groupActivities, /Votre participation est confirmée/);
  assert.doesNotMatch(`${page}${layout}${packageJson}${readme}`, /Fusioniox/i);
});
