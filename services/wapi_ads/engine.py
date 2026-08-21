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


def campaign_score(campaign: Campaign, context: AudienceContext) -> float:
    if campaign.status != "active" or campaign.placement != context.placement:
        return -1.0
    campaign_city = normalize(campaign.city)
    city = normalize(context.city)
    region = normalize(context.region)
    geographic = 4.0 if campaign_city and campaign_city == city else 2.0 if campaign_city and campaign_city in region else 0.6 if not campaign_city else 0.0
    audience = normalize(campaign.audience)
    interest = sum(0.45 for item in context.interests if normalize(item) and normalize(item) in audience)
    delivery = min(2.0, log1p(max(0, campaign.daily_budget)) / 5.0)
    reach = min(1.0, log1p(max(0, campaign.estimated_reach)) / 12.0)
    return geographic + interest + delivery + reach


def rank_campaigns(campaigns: Iterable[Campaign], context: AudienceContext, limit: int = 3) -> list[Campaign]:
    scored = ((campaign_score(campaign, context), campaign) for campaign in campaigns)
    eligible = ((score, campaign) for score, campaign in scored if score >= 0)
    ordered = sorted(eligible, key=lambda item: (-item[0], item[1].id))
    return [campaign for _, campaign in ordered[: max(0, min(limit, 10))]]
