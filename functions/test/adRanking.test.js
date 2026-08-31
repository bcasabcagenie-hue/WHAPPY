const test = require("node:test");
const assert = require("node:assert/strict");
const { ELEPHANT_ALGORITHM_NAME, isWapiDeliveryComplete, priceWapiAdDelivery, rankElephantAds } = require("../lib/adRanking.js");

const now = Date.UTC(2026, 7, 29, 12);
const candidate = (id, values = {}) => ({
  id,
  ownerId: `owner-${id}`,
  pageId: `page-${id}`,
  category: "Restaurant",
  placement: "inbox",
  countryCode: "CG",
  city: "Brazzaville",
  createdAtMillis: now - 86_400_000,
  impressions: 100,
  clicks: 10,
  dismissals: 0,
  conversions: 0,
  dailyDelivered: 0,
  dailyCap: 1_000,
  ...values,
});

test("WAPI Ads prioritises compatible regional campaigns", () => {
  const ranked = rankElephantAds({
    candidates: [
      candidate("local"),
      candidate("national", { city: "" }),
      candidate("foreign", { countryCode: "FR" }),
      candidate("wrong-placement", { placement: "market" }),
    ],
    signals: [],
    userId: "viewer",
    placement: "inbox",
    countryCode: "CG",
    city: "Brazzaville",
    nowMillis: now,
    limit: 5,
  });
  assert.equal(ranked[0].id, "local");
  assert.ok(ranked[0].reasons.includes("ville ciblée"));
  assert.ok(ranked[0].reasons.includes("pays ciblé"));
  assert.deepEqual(ranked.map((ad) => ad.id).sort(), ["local", "national"]);
});

test("WAPI Ads applies frequency caps and dismissals", () => {
  const signals = [
    ...Array.from({ length: 3 }, (_, index) => ({ campaignId: "seen", type: "impression", category: "Restaurant", createdAtMillis: now - index * 86_400_000 })),
    { campaignId: "dismissed", type: "dismiss", category: "Restaurant", createdAtMillis: now - 1_000 },
  ];
  const ranked = rankElephantAds({
    candidates: [candidate("seen"), candidate("dismissed"), candidate("fresh")],
    signals,
    userId: "viewer",
    placement: "inbox",
    countryCode: "CG",
    city: "Brazzaville",
    nowMillis: now,
    limit: 5,
  });
  assert.deepEqual(ranked.map((ad) => ad.id), ["fresh"]);
});

test("WAPI Ads keeps advertiser and page diversity", () => {
  const ranked = rankElephantAds({
    candidates: [
      candidate("one", { ownerId: "same-owner", pageId: "page-one", clicks: 30 }),
      candidate("two", { ownerId: "same-owner", pageId: "page-two", clicks: 20 }),
      candidate("three", { ownerId: "another-owner", pageId: "page-three" }),
    ],
    signals: [],
    userId: "viewer",
    placement: "inbox",
    countryCode: "CG",
    city: "Brazzaville",
    nowMillis: now,
    limit: 5,
  });
  assert.equal(ranked.filter((ad) => ad.ownerId === "same-owner").length, 1);
  assert.equal(ranked.length, 2);
});

test("WAPI Ads never recommends an advertiser's own campaign", () => {
  const ranked = rankElephantAds({
    candidates: [candidate("mine", { ownerId: "viewer" }), candidate("other")],
    signals: [],
    userId: "viewer",
    placement: "inbox",
    countryCode: "CG",
    city: "Brazzaville",
    nowMillis: now,
    limit: 5,
  });
  assert.deepEqual(ranked.map((ad) => ad.id), ["other"]);
});

test("WAPI Ads prices purchased delivery in transparent thousand-view blocks", () => {
  assert.equal(priceWapiAdDelivery(1_000), 2_500);
  assert.equal(priceWapiAdDelivery(10_000), 25_000);
  assert.equal(priceWapiAdDelivery(10_001), 27_500);
  assert.equal(priceWapiAdDelivery(0), 0);
});

test("WAPI Ads completes impression campaigns exactly at their delivery quota", () => {
  assert.equal(isWapiDeliveryComplete(9_999, 10_000), false);
  assert.equal(isWapiDeliveryComplete(10_000, 10_000), true);
  assert.equal(isWapiDeliveryComplete(12, 0), false);
});

test("WAPI Ads respects a campaign's daily pacing cap", () => {
  const ranked = rankElephantAds({
    candidates: [candidate("full-today", { dailyDelivered: 100, dailyCap: 100 }), candidate("available", { dailyDelivered: 99, dailyCap: 100 })],
    signals: [],
    userId: "viewer",
    placement: "inbox",
    countryCode: "CG",
    city: "Brazzaville",
    nowMillis: now,
    limit: 5,
  });
  assert.deepEqual(ranked.map((ad) => ad.id), ["available"]);
});

test("ELEPHANT rewards conversions and penalises campaigns users dismiss", () => {
  const ranked = rankElephantAds({
    candidates: [
      candidate("trusted", { conversions: 12, clicks: 40, impressions: 1_000 }),
      candidate("rejected", { dismissals: 180, clicks: 40, impressions: 1_000 }),
    ],
    signals: [],
    userId: "viewer",
    placement: "inbox",
    countryCode: "CG",
    city: "Brazzaville",
    nowMillis: now,
    limit: 5,
  });
  assert.equal(ELEPHANT_ALGORITHM_NAME, "ELEPHANT");
  assert.equal(ranked[0].id, "trusted");
  assert.ok(ranked[0].reasons.includes("performance commerciale"));
});
