import 'package:flutter_test/flutter_test.dart';
import 'package:wapi_flutter/features/live/wapi_live_models.dart';

void main() {
  test(
    'le contrat Live convertit les données Firebase sans valeurs fictives',
    () {
      final live = WapiLiveListing.fromMap({
        'id': 'live-1',
        'hostId': 'host-1',
        'hostName': 'Cyril Bokilo',
        'title': 'Le direct WAPI',
        'category': 'Business',
        'status': 'live',
        'viewerCount': 12,
        'reactionCount': 31,
        'aiGenerated': false,
      });

      expect(live.id, 'live-1');
      expect(live.hostName, 'Cyril Bokilo');
      expect(live.isLive, isTrue);
      expect(live.viewerCount, 12);
      expect(live.reactionCount, 31);
      expect(live.aiGenerated, isFalse);
    },
  );

  test('les réactions Live restent limitées au vocabulaire approuvé', () {
    expect(wapiLiveReactionGlyph('heart'), '❤️');
    expect(wapiLiveReactionGlyph('applause'), '👏');
    expect(wapiLiveReactionGlyph('inconnue'), '❤️');
  });
}
