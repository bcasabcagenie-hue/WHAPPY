import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/wapi_app.dart';
import 'services/wapi_notifications.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await Firebase.initializeApp();
    FirebaseMessaging.onBackgroundMessage(wapiFirebaseBackgroundHandler);
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
