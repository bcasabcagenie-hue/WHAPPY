import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/wapi_app.dart';
import 'services/wapi_notifications.dart';

const _wapiLocalTestMode = bool.fromEnvironment('WAPI_LOCAL_TEST');
const _wapiLocalTestConversationId = 'wapi-local-voice-test';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  PaintingBinding.instance.imageCache
    ..maximumSize = 320
    ..maximumSizeBytes = 128 << 20;
  ErrorWidget.builder = (details) => const _WapiRenderIssue();
  try {
    await Firebase.initializeApp(
      options: kIsWeb
          ? const FirebaseOptions(
              apiKey: 'AIzaSyCSUoJq_UksJekdGo7IFXhuS2A1BNDf8pM',
              appId: '1:400270565891:web:e0f5fb3ebfe35878fee436',
              messagingSenderId: '400270565891',
              projectId: 'whappy-d97e7',
              authDomain: 'whappy-d97e7.firebaseapp.com',
              storageBucket: 'whappy-d97e7.firebasestorage.app',
              measurementId: 'G-XBXTG5353N',
            )
          : null,
    );
    if (!kIsWeb) {
      if (_wapiLocalTestMode) {
        await _configureWapiLocalTest();
      } else {
        FirebaseFirestore.instance.settings = const Settings(
          persistenceEnabled: true,
          cacheSizeBytes: Settings.CACHE_SIZE_UNLIMITED,
        );
        FirebaseMessaging.onBackgroundMessage(wapiFirebaseBackgroundHandler);
      }
    }
  } catch (error, stackTrace) {
    if (_wapiLocalTestMode) {
      debugPrint('WAPI_LOCAL_TEST bootstrap failed: $error');
      debugPrintStack(stackTrace: stackTrace);
    }
    runApp(const WapiBootstrapError());
    return;
  }
  runApp(const ProviderScope(child: WapiApp()));
}

Future<void> _configureWapiLocalTest() async {
  final host = defaultTargetPlatform == TargetPlatform.android
      ? '10.0.2.2'
      : '127.0.0.1';
  await FirebaseAuth.instance.useAuthEmulator(host, 9099);
  FirebaseFirestore.instance
    ..useFirestoreEmulator(host, 8080)
    ..settings = const Settings(persistenceEnabled: false);
  await FirebaseStorage.instance.useStorageEmulator(host, 9199);
  FirebaseFunctions.instanceFor(
    region: 'europe-west1',
  ).useFunctionsEmulator(host, 5001);

  final credential = await FirebaseAuth.instance.signInAnonymously();
  final user = credential.user!;
  await user.updateDisplayName('Test vocal WAPI');
  const peerId = 'wapi-local-peer';
  final now = FieldValue.serverTimestamp();
  final database = FirebaseFirestore.instance;
  await database.collection('users').doc(user.uid).set({
    'uid': user.uid,
    'displayName': 'Test vocal WAPI',
    'phoneNumber': '',
    'photoUrl': '',
    'verified': false,
    'verificationStatus': 'unverified',
    'createdAt': now,
    'updatedAt': now,
  });
  await database
      .collection('conversations')
      .doc(_wapiLocalTestConversationId)
      .set({
        'ownerId': user.uid,
        'conversationType': 'direct',
        'title': '',
        'memberIds': [user.uid, peerId],
        'members': [
          {
            'uid': user.uid,
            'displayName': 'Test vocal WAPI',
            'phoneNumber': '',
            'photoUrl': '',
          },
          {
            'uid': peerId,
            'displayName': 'Contact de test',
            'phoneNumber': '',
            'photoUrl': '',
          },
        ],
        'typingBy': <String, bool>{},
        'readBy': <String, dynamic>{},
        'contactId': peerId,
        'contactName': 'Contact de test',
        'lastMessage': 'Conversation locale de validation',
        'lastSenderId': peerId,
        'createdAt': now,
        'updatedAt': now,
      });
}

class _WapiRenderIssue extends StatelessWidget {
  const _WapiRenderIssue();

  @override
  Widget build(BuildContext context) => const ColoredBox(
    color: Color(0xFFF4F7F9),
    child: Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.refresh_rounded, size: 34, color: Color(0xFF008EE8)),
            SizedBox(height: 10),
            Text(
              'Cette section doit être actualisée.',
              textAlign: TextAlign.center,
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
          ],
        ),
      ),
    ),
  );
}

class WapiBootstrapError extends StatelessWidget {
  const WapiBootstrapError({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    home: Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: const [
              Icon(Icons.cloud_off_outlined, size: 42),
              SizedBox(height: 16),
              Text(
                'WAPI ne peut pas démarrer pour le moment.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
              SizedBox(height: 8),
              Text(
                'Vérifiez votre connexion puis relancez WAPI.',
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
