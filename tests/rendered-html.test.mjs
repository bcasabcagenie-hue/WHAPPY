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

test("affiche la connexion téléphonique Whappy côté serveur", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Whappy — Tout peut devenir une opportunité<\/title>/i);
  assert.match(html, /Logo Whappy/);
  assert.match(html, /UN NUMÉRO\. UN COMPTE\./);
  assert.match(html, /Entrez votre numéro/);
  assert.match(html, /Congo \(\+242\)/);
  assert.match(html, /Continuer par SMS/);
  assert.match(html, /Un numéro = un compte Whappy/);
  assert.doesNotMatch(html, /Fusioniox|site-creator-vinext-starter/i);
});

test("conserve l'identité et la configuration autonome de Whappy", async () => {
  const [page, layout, packageJson, readme, logo, firebase, rules, dataLayer, commerce, calls] = await Promise.all([
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
  ]);

  assert.match(page, /src="\/whappy-logo\.svg"/);
  assert.match(page, /type Space = "orbit" \| "live" \| "market" \| "barter" \| "seek" \| "inbox" \| "twin"/);
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
  assert.match(page, /UN NUMÉRO\. UN COMPTE/);
  assert.match(page, /signInWithPhoneNumber/);
  assert.match(page, /RecaptchaVerifier/);
  assert.match(page, /appVerificationDisabledForTesting/);
  assert.match(page, /hasPhone && hasProfile/);
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
  assert.match(commerce, /ProductPanel/);
  assert.match(commerce, /CartPanel/);
  assert.match(commerce, /Paiement à la livraison/);
  assert.match(calls, /getUserMedia/);
  assert.doesNotMatch(`${page}${layout}${packageJson}${readme}`, /Fusioniox/i);
});
