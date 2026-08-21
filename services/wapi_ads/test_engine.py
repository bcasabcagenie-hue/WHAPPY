import unittest

from services.wapi_ads.engine import AudienceContext, Campaign, rank_campaigns


class RegionalAdsTest(unittest.TestCase):
    def test_prefers_matching_city_and_rejects_wrong_placement(self) -> None:
        context = AudienceContext("Congo", "Brazzaville", "profile_story", ("mode",))
        campaigns = [
            Campaign("local", "a", "active", "Brazzaville", "mode locale", "profile_story", 2000, 4000),
            Campaign("remote", "b", "active", "Pointe-Noire", "mode", "profile_story", 9000, 12000),
            Campaign("wrong", "c", "active", "Brazzaville", "mode", "live", 9000, 12000),
        ]
        self.assertEqual([item.id for item in rank_campaigns(campaigns, context)], ["local", "remote"])

    def test_excludes_paused_campaigns(self) -> None:
        context = AudienceContext("Congo", "Brazzaville", "market")
        paused = Campaign("paused", "a", "paused", "Brazzaville", "", "market", 2000, 4000)
        self.assertEqual(rank_campaigns([paused], context), [])


if __name__ == "__main__":
    unittest.main()
