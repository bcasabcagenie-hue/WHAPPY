import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wapi_flutter/app/wapi_theme.dart';

void main() {
  testWidgets('le thème WAPI expose une surface mobile claire', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: WapiTheme.light(),
        home: const Scaffold(body: Text('WAPI')),
      ),
    );

    expect(find.text('WAPI'), findsOneWidget);
    expect(
      Theme.of(tester.element(find.text('WAPI'))).colorScheme.primary,
      WapiColors.blue,
    );
  });

  test('les erreurs techniques restent invisibles pour les utilisateurs', () {
    expect(
      wapiErrorText(
        '[firebase_storage/unauthorized] User is not authorized',
        fallback: 'Envoi impossible. Réessayez.',
      ),
      'Envoi impossible. Réessayez.',
    );
    expect(
      wapiErrorText(
        '[cloud_functions/deadline-exceeded] timeout',
        fallback: 'Connexion trop lente.',
      ),
      'Connexion trop lente.',
    );
  });
}
