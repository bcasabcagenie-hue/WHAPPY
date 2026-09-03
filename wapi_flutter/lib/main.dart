import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/wapi_app.dart';
import 'services/wapi_notifications.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
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
    if (!kIsWeb) FirebaseMessaging.onBackgroundMessage(wapiFirebaseBackgroundHandler);
  } catch (error) {
    runApp(WapiBootstrapError(error: error));
    return;
  }
  runApp(const ProviderScope(child: WapiApp()));
}

class WapiBootstrapError extends StatelessWidget {
  const WapiBootstrapError({super.key, required this.error});

  final Object error;

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    home: Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            'WAPI ne peut pas démarrer Firebase sur cet appareil.\n$error',
            textAlign: TextAlign.center,
          ),
        ),
      ),
    ),
  );
}
