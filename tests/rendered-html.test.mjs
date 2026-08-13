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

test("affiche l'application Whappy côté serveur", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Whappy — Connectés, simplement<\/title>/i);
  assert.match(html, /Logo Whappy/);
  assert.match(html, /Rechercher une discussion/);
  assert.match(html, /Équipe Whappy/);
  assert.match(html, /Écrire un message/);
  assert.doesNotMatch(html, /Fusioniox|site-creator-vinext-starter/i);
});

test("conserve l'identité et la configuration autonome de Whappy", async () => {
  const [page, layout, packageJson, readme, logo, firebase, rules] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../README.md", import.meta.url), "utf8"),
    readFile(new URL("../public/whappy-logo.svg", import.meta.url), "utf8"),
    readFile(new URL("../lib/firebase.ts", import.meta.url), "utf8"),
    readFile(new URL("../firestore.rules", import.meta.url), "utf8"),
  ]);

  assert.match(page, /src="\/whappy-logo\.svg"/);
  assert.match(page, /localStorage\.setItem\("whappy-theme"/);
  assert.match(layout, /title:\s*"Whappy — Connectés, simplement"/);
  assert.match(packageJson, /"name": "whappy"/);
  assert.match(readme, /projet autonome pour Visual Studio Code/i);
  assert.match(logo, /#13d713/i);
  assert.match(firebase, /getFirestore/);
  assert.match(firebase, /getAuth/);
  assert.match(rules, /request\.auth\.uid/);
  assert.doesNotMatch(`${page}${layout}${packageJson}${readme}`, /Fusioniox/i);
});
