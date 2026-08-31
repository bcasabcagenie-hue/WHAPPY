"""Classement publicitaire WAPI sans localisation précise ni profil sensible.

Le moteur ne reçoit qu'une ville/région déclarée, le placement courant et des
centres d'intérêt généraux. Il n'utilise ni GPS, ni contacts, ni messages privés.
"""

from dataclasses import dataclass
from math import log1p
from typing import Iterable
import unicodedata


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value or "")
    return " ".join("".join(char for char in value if not unicodedata.combining(char)).lower().split())


@dataclass(frozen=True)
class AudienceContext:
    region: str
    city: str
    placement: str
    interests: tuple[str, ...] = ()
    user_id: str = ""


@dataclass(frozen=True)
class Campaign:
    id: str
    owner_id: str
    status: str
    city: str
    audience: str
    placement: str
    daily_budget: int
    estimated_reach: int
    page_id: str = ""
    category: str = ""
    country_code: str = ""
    impressions: int = 0
    clicks: int = 0
    dismissals: int = 0
    conversions: int = 0
    daily_delivered: int = 0
    daily_cap: int = 0


ELEPHANT_ALGORITHM_NAME = "ELEPHANT"
ELEPHANT_ALGORITHM_VERSION = "1.1"


def campaign_score(campaign: Campaign, context: AudienceContext) -> float:
    if campaign.status != "active" or campaign.placement != context.placement:
        return -1.0
    if context.user_id and campaign.owner_id == context.user_id:
        return -1.0
    if campaign.daily_cap > 0 and campaign.daily_delivered >= campaign.daily_cap:
        return -1.0
    campaign_city = normalize(campaign.city)
    city = normalize(context.city)
    region = normalize(context.region)
    geographic = 4.0 if campaign_city and campaign_city == city else 2.0 if campaign_city and campaign_city in region else 0.6 if not campaign_city else 0.0
    audience = normalize(campaign.audience)
    interest = sum(0.45 for item in context.interests if normalize(item) and normalize(item) in audience)
    # The budget only controls how much can be delivered; it cannot buy a
    # higher relevance score. Pacing favours campaigns that are still behind
    # their declared daily cap and avoids spending everything at once.
    delivery = 0.0
    if campaign.daily_cap > 0:
        delivery = max(0.0, 1.4 * (1.0 - campaign.daily_delivered / campaign.daily_cap))
    reach = min(1.0, log1p(max(0, campaign.estimated_reach)) / 12.0)
    impressions = max(0, campaign.impressions)
    clicks = max(0, campaign.clicks)
    click_quality = (clicks + 2) / (impressions + 80)
    conversion_quality = (max(0, campaign.conversions) + 1) / (clicks + 40)
    dismissal_rate = (max(0, campaign.dismissals) + 1) / (impressions + 100)
    quality = min(2.0, click_quality * 14) + min(1.5, conversion_quality * 10) - min(2.5, dismissal_rate * 12)
    newcomer = max(0.0, 0.8 * (1.0 - impressions / 100)) if impressions < 100 else 0.0
    return geographic + interest + delivery + reach + quality + newcomer


def rank_campaigns(campaigns: Iterable[Campaign], context: AudienceContext, limit: int = 3) -> list[Campaign]:
    scored = ((campaign_score(campaign, context), campaign) for campaign in campaigns)
    eligible = ((score, campaign) for score, campaign in scored if score >= 0)
    ordered = sorted(eligible, key=lambda item: (-item[0], item[1].id))
    selected: list[Campaign] = []
    owners: set[str] = set()
    pages: set[str] = set()
    for _, campaign in ordered:
        page = campaign.page_id or campaign.id
        if campaign.owner_id in owners or page in pages:
            continue
        owners.add(campaign.owner_id)
        pages.add(page)
        selected.append(campaign)
        if len(selected) >= max(0, min(limit, 10)):
            break
    return selected
