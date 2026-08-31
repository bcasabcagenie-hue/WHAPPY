export type WapiAdCandidate = {
  id: string;
  ownerId: string;
  pageId: string;
  category: string;
  placement: string;
  countryCode: string;
  city: string;
  createdAtMillis: number;
  impressions: number;
  clicks: number;
  dismissals: number;
  conversions: number;
  dailyDelivered: number;
  dailyCap: number;
};

export type WapiAdSignal = {
  campaignId: string;
  type: "impression" | "click" | "dismiss" | "conversion";
  category: string;
  createdAtMillis: number;
};

export type WapiAdRankingInput = {
  candidates: WapiAdCandidate[];
  signals: WapiAdSignal[];
  userId: string;
  placement: string;
  countryCode: string;
  city: string;
  nowMillis: number;
  limit: number;
};

export type WapiRankedAd = WapiAdCandidate & {
  score: number;
  reasons: string[];
};

const DAY = 24 * 60 * 60 * 1_000;
export const ELEPHANT_ALGORITHM_NAME = "ELEPHANT";
export const ELEPHANT_ALGORITHM_VERSION = "1.1";
export const WAPI_AD_PRICE_PER_THOUSAND = 2_500;

export function priceWapiAdDelivery(targetImpressions: number) {
  if (!Number.isSafeInteger(targetImpressions) || targetImpressions <= 0) return 0;
  return Math.ceil(targetImpressions / 1_000) * WAPI_AD_PRICE_PER_THOUSAND;
}

export function isWapiDeliveryComplete(deliveredImpressions: number, targetImpressions: number) {
  return Number.isFinite(targetImpressions) && targetImpressions > 0 && deliveredImpressions >= targetImpressions;
}

function normalized(value: string) {
  return value.trim().toLocaleLowerCase("fr");
}

/** Stable exploration prevents the same large advertiser from monopolising a feed. */
function explorationNoise(seed: string) {
  let hash = 2166136261;
  for (let index = 0; index < seed.length; index += 1) {
    hash ^= seed.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return ((hash >>> 0) % 1_000) / 1_000;
}

/**
 * ELEPHANT: WAPI's privacy-conscious, equitable advertising ranker.
 *
 * The scorer intentionally excludes protected or sensitive characteristics.
 * It uses only coarse region, requested placement, campaign freshness,
 * aggregate quality and the signed-in user's own advertising interactions.
 */
export function rankElephantAds(input: WapiAdRankingInput): WapiRankedAd[] {
  const country = input.countryCode.trim().toUpperCase();
  const city = normalized(input.city);
  const cutoff30Days = input.nowMillis - 30 * DAY;
  const cutoff7Days = input.nowMillis - 7 * DAY;
  const recentSignals = input.signals.filter((signal) => signal.createdAtMillis >= cutoff30Days);
  const categoryAffinity = new Map<string, number>();
  const categoryDisinterest = new Map<string, number>();
  const impressions7Days = new Map<string, number>();
  const dismissed7Days = new Set<string>();

  recentSignals.forEach((signal) => {
    if (signal.type === "click" || signal.type === "conversion") {
      const category = normalized(signal.category);
      if (category) categoryAffinity.set(category, (categoryAffinity.get(category) || 0) + (signal.type === "conversion" ? 3 : 1));
    }
    if (signal.type === "dismiss") {
      const category = normalized(signal.category);
      if (category) categoryDisinterest.set(category, (categoryDisinterest.get(category) || 0) + 1);
    }
    if (signal.createdAtMillis >= cutoff7Days && signal.type === "impression") {
      impressions7Days.set(signal.campaignId, (impressions7Days.get(signal.campaignId) || 0) + 1);
    }
    if (signal.createdAtMillis >= cutoff7Days && signal.type === "dismiss") dismissed7Days.add(signal.campaignId);
  });

  const dayBucket = Math.floor(input.nowMillis / DAY);
  const ranked = input.candidates.flatMap<WapiRankedAd>((candidate) => {
    if (!candidate.id || candidate.ownerId === input.userId || candidate.placement !== input.placement) return [];
    if (candidate.countryCode && country && candidate.countryCode.toUpperCase() !== country) return [];
    if (dismissed7Days.has(candidate.id) || (impressions7Days.get(candidate.id) || 0) >= 3) return [];
    if (candidate.dailyCap > 0 && candidate.dailyDelivered >= candidate.dailyCap) return [];

    let score = 35;
    const reasons = ["emplacement compatible"];
    if (candidate.countryCode && country && candidate.countryCode.toUpperCase() === country) {
      score += 24;
      reasons.push("pays ciblé");
    }
    if (candidate.city && city && normalized(candidate.city) === city) {
      score += 14;
      reasons.push("ville ciblée");
    }
    const affinity = categoryAffinity.get(normalized(candidate.category)) || 0;
    if (affinity > 0) {
      score += Math.min(18, affinity * 4);
      reasons.push("activité pertinente");
    }
    const disinterest = categoryDisinterest.get(normalized(candidate.category)) || 0;
    if (disinterest > 0) score -= Math.min(10, disinterest * 3);
    const ageDays = Math.max(0, (input.nowMillis - candidate.createdAtMillis) / DAY);
    if (ageDays <= 7) {
      score += Math.max(0, 9 - ageDays);
      reasons.push("campagne récente");
    }
    // Bayesian smoothing prevents one accidental click or conversion from
    // overpowering proven campaigns. Dismissals are a first-class quality
    // signal, so aggressive creatives lose reach instead of winning it.
    const impressions = Math.max(0, candidate.impressions);
    const clicks = Math.max(0, candidate.clicks);
    const clickQuality = (clicks + 2) / (impressions + 80);
    const conversionQuality = (Math.max(0, candidate.conversions) + 1) / (clicks + 40);
    const dismissalRate = (Math.max(0, candidate.dismissals) + 1) / (impressions + 100);
    score += Math.min(18, clickQuality * 140);
    score += Math.min(12, conversionQuality * 80);
    score -= Math.min(24, dismissalRate * 120);
    if (candidate.conversions > 0 && conversionQuality >= .035) reasons.push("performance commerciale");
    if (impressions < 100) {
      score += 7 * (1 - impressions / 100);
      reasons.push("nouvel annonceur");
    }
    score -= (impressions7Days.get(candidate.id) || 0) * 9;
    if (candidate.dailyCap > 0) {
      const dailyFill = candidate.dailyDelivered / candidate.dailyCap;
      score += Math.max(0, 8 * (1 - dailyFill));
      reasons.push("diffusion cadencée");
    }
    score += explorationNoise(`${input.userId}:${candidate.id}:${dayBucket}`) * 8;
    return [{ ...candidate, score, reasons }];
  }).sort((left, right) => right.score - left.score || left.id.localeCompare(right.id));

  const pageIds = new Set<string>();
  const ownerIds = new Set<string>();
  const diverse: WapiRankedAd[] = [];
  for (const ad of ranked) {
    if (pageIds.has(ad.pageId) || ownerIds.has(ad.ownerId)) continue;
    pageIds.add(ad.pageId);
    ownerIds.add(ad.ownerId);
    diverse.push(ad);
    if (diverse.length >= Math.max(1, Math.min(input.limit, 20))) break;
  }
  return diverse;
}

/** Compatibility alias for older server code; new code should use ELEPHANT. */
export const rankWapiAds = rankElephantAds;
