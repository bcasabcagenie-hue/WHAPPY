import 'package:flutter_test/flutter_test.dart';
import 'package:wapi_flutter/data/wapi_repository.dart';

void main() {
  group('WapiMessage.fromMap', () {
    test('reconnaît une note vocale WAPI actuelle', () {
      final message = WapiMessage.fromMap(
        id: 'voice-current',
        data: const {
          'text': 'Message vocal',
          'senderId': 'member-a',
          'kind': 'audio',
          'mediaUrl': 'https://media.wapi.test/note.m4a',
          'duration': 17,
        },
      );

      expect(message.kind, 'audio');
      expect(message.mediaUrl, endsWith('note.m4a'));
      expect(message.durationSeconds, 17);
    });

    test('récupère les anciens champs voiceUrl et durationMs', () {
      final message = WapiMessage.fromMap(
        id: 'voice-legacy',
        data: const {
          'message': 'Note vocale',
          'authorId': 'member-b',
          'messageType': 'voice_note',
          'voiceUrl': 'https://media.wapi.test/ancienne-note.aac',
          'durationMs': 4250,
        },
      );

      expect(message.kind, 'audio');
      expect(message.senderId, 'member-b');
      expect(message.mediaUrl, endsWith('ancienne-note.aac'));
      expect(message.durationSeconds, 4);
    });

    test('détecte un vocal ancien même sans champ de type', () {
      final message = WapiMessage.fromMap(
        id: 'voice-label-only',
        data: const {
          'text': 'Message vocal',
          'senderId': 'member-c',
          'downloadUrl': 'https://media.wapi.test/voice.opus?token=abc',
        },
      );

      expect(message.kind, 'audio');
      expect(message.mediaUrl, contains('voice.opus'));
    });

    test('restaure une traduction vocale mise en cache par langue', () {
      final message = WapiMessage.fromMap(
        id: 'voice-translated',
        data: const {
          'text': 'Message vocal',
          'senderId': 'member-c',
          'kind': 'audio',
          'mediaUrl': 'https://media.wapi.test/voice.opus',
          'voiceTranslations': {
            'ln': {
              'transcript': 'Bonjour à tous',
              'translation': 'Mbote na bino nyonso',
              'detectedLanguage': 'fr',
              'targetLanguage': 'ln',
              'translatedAudioUrl':
                  'https://media.wapi.test/voice-translated.opus',
            },
          },
        },
      );

      expect(
        message.voiceTranslations['ln']?.translation,
        'Mbote na bino nyonso',
      );
      expect(message.voiceTranslations['ln']?.detectedLanguage, 'fr');
      expect(
        message.voiceTranslations['ln']?.translatedAudioUrl,
        endsWith('voice-translated.opus'),
      );
    });

    test('conserve les métadonnées d’un document WAPI', () {
      final message = WapiMessage.fromMap(
        id: 'document-current',
        data: const {
          'text': 'Document · facture-aout.pdf',
          'senderId': 'member-d',
          'kind': 'document',
          'mediaUrl': 'https://media.wapi.test/facture-aout.pdf',
          'mediaName': 'facture-aout.pdf',
          'contentType': 'application/pdf',
          'mediaSizeBytes': 128000,
        },
      );

      expect(message.kind, 'document');
      expect(message.mediaName, 'facture-aout.pdf');
      expect(message.contentType, 'application/pdf');
      expect(message.mediaSizeBytes, 128000);
    });
  });
}
