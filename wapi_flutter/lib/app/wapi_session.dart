import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';

/// Keeps WAPI usable from its local cache during a temporary outage, while
/// rejecting credentials that can no longer authenticate any WAPI service.
Stream<User?> validatedWapiAuthStates(
  FirebaseAuth auth, {
  bool localTestMode = false,
}) async* {
  await for (final user in auth.idTokenChanges()) {
    if (user == null || localTestMode) {
      yield user;
      continue;
    }
    try {
      // Without forceRefresh this is immediate for a healthy cached session.
      // An expired token is refreshed before the authenticated UI starts.
      await user.getIdToken().timeout(const Duration(seconds: 10));
      yield user;
    } on FirebaseAuthException catch (error) {
      if (!wapiSessionNeedsSignIn(error.code, error.message)) {
        // Network failures must not eject users: Firestore can continue from
        // its encrypted local cache and refresh when connectivity returns.
        yield user;
        continue;
      }
      await auth.signOut();
      yield null;
    } on TimeoutException {
      yield user;
    } catch (_) {
      yield user;
    }
  }
}

/// Firebase SDKs do not expose the same code on every Android/iOS version.
/// Matching both the normalized code and message covers refresh-token errors
/// without treating ordinary connectivity failures as an invalid account.
bool wapiSessionNeedsSignIn(String code, String? message) {
  final signal = '$code ${message ?? ''}'
      .toLowerCase()
      .replaceAll('_', '-')
      .replaceAll(' ', '-');
  return const <String>{
    'invalid-user-token',
    'user-token-expired',
    'invalid-refresh-token',
    'user-disabled',
    'user-not-found',
  }.any(signal.contains);
}
