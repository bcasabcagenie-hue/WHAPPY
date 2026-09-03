import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/phone_auth_page.dart';
import '../features/calls/wapi_call_page.dart';
import '../features/live/wapi_live_page.dart';
import '../features/shell/wapi_shell.dart';
import '../services/wapi_notifications.dart';
import 'wapi_theme.dart';

final authStateProvider = StreamProvider<User?>((ref) {
  return FirebaseAuth.instance.authStateChanges();
});
final wapiNavigatorKey = GlobalKey<NavigatorState>();
const _wapiLocalTestMode = bool.fromEnvironment('WAPI_LOCAL_TEST');
const _wapiLocalTestConversationId = 'wapi-local-voice-test';

class WapiApp extends ConsumerWidget {
  const WapiApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authStateProvider);
    return MaterialApp(
      navigatorKey: wapiNavigatorKey,
      title: 'WAPI',
      debugShowCheckedModeBanner: false,
      restorationScopeId: 'wapi',
      theme: WapiTheme.light(),
      themeAnimationDuration: const Duration(milliseconds: 220),
      builder: (context, child) => MediaQuery.withClampedTextScaling(
        minScaleFactor: .9,
        maxScaleFactor: 1.5,
        child: child ?? const SizedBox.shrink(),
      ),
      home: session.when(
        loading: () => const _LaunchScreen(),
        error: (_, _) => const _StartupIssue(),
        data: (user) => user == null
            ? const PhoneAuthPage()
            : _AuthenticatedWapi(user: user),
      ),
    );
  }
}

class _AuthenticatedWapi extends StatefulWidget {
  const _AuthenticatedWapi({required this.user});
  final User user;
  @override
  State<_AuthenticatedWapi> createState() => _AuthenticatedWapiState();
}

class _AuthenticatedWapiState extends State<_AuthenticatedWapi>
    with WidgetsBindingObserver {
  String? _conversationFromNotification;
  StreamSubscription<QuerySnapshot<Map<String, dynamic>>>? _incomingCalls;
  String? _activeIncomingCallId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (!_wapiLocalTestMode) {
      WapiNotifications.initialize(onTap: _openNotification).then((_) async {
        await WapiNotifications.register(widget.user);
        await WapiNotifications.syncUnreadBadge(widget.user.uid);
      });
    }
    _watchIncomingCalls();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _incomingCalls?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(WapiNotifications.register(widget.user));
      unawaited(WapiNotifications.syncUnreadBadge(widget.user.uid));
    }
  }

  void _watchIncomingCalls() {
    _incomingCalls = FirebaseFirestore.instance
        .collection('directCallSessions')
        .where('calleeId', isEqualTo: widget.user.uid)
        .limit(25)
        .snapshots()
        .listen((snapshot) {
          for (final change in snapshot.docChanges) {
            final call = change.doc;
            final data = call.data();
            if (data == null) continue;
            final status = data['status']?.toString() ?? '';
            if (status != 'ringing' || _callExpired(data)) {
              unawaited(WapiNotifications.dismissCall(call.id));
              continue;
            }
            if (status == 'ringing') {
              unawaited(_presentIncomingCall(call.id, data));
              return;
            }
          }
        });
  }

  Future<void> _openNotification(String payload, String? actionId) async {
    if (payload.startsWith('conversation:')) {
      final conversationId = payload.substring('conversation:'.length);
      if (conversationId.isNotEmpty && mounted) {
        setState(() => _conversationFromNotification = conversationId);
      }
      return;
    }
    if (payload.startsWith('live:')) {
      final liveId = payload.substring('live:'.length);
      final navigator = wapiNavigatorKey.currentState;
      if (liveId.isNotEmpty && navigator != null) {
        await navigator.push(
          MaterialPageRoute(
            builder: (_) =>
                WapiLivePage(user: widget.user, initialLiveId: liveId),
          ),
        );
      }
      return;
    }
    if (!payload.startsWith('call:')) return;
    final callId = payload.substring('call:'.length);
    final functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
    if (actionId == 'decline_call') {
      await functions
          .httpsCallable('closeDirectCallSession')
          .call<Map<String, dynamic>>({'callId': callId, 'action': 'decline'})
          .timeout(const Duration(seconds: 12));
      return;
    }
    Map<String, dynamic> data;
    try {
      final result = await functions
          .httpsCallable('getDirectCallSession')
          .call<Map<String, dynamic>>({'callId': callId})
          .timeout(const Duration(seconds: 12));
      data = result.data;
    } catch (_) {
      return;
    }
    if (data['incoming'] != true) return;
    await _presentIncomingCall(callId, data);
  }

  Future<void> _presentIncomingCall(
    String callId,
    Map<String, dynamic> data,
  ) async {
    if (!mounted ||
        callId.isEmpty ||
        _activeIncomingCallId == callId ||
        data['status'] == 'ended' ||
        data['status'] == 'declined' ||
        _callExpired(data)) {
      unawaited(WapiNotifications.dismissCall(callId));
      return;
    }
    final incoming =
        data['incoming'] == true ||
        (data['calleeId'] == null || data['calleeId'] == widget.user.uid);
    if (!incoming) return;
    final navigator = wapiNavigatorKey.currentState;
    if (navigator == null) return;
    _activeIncomingCallId = callId;
    final callerName =
        (data['peerName'] as String?) ??
        (data['callerName'] as String?) ??
        'Contact WAPI';
    final callerPhotoUrl =
        (data['peerPhotoUrl'] as String?) ??
        (data['callerPhotoUrl'] as String?) ??
        '';
    try {
      await navigator.push(
        MaterialPageRoute(
          builder: (_) => WapiCallPage.incoming(
            user: widget.user,
            incomingCallId: callId,
            peerId:
                (data['peerId'] as String?) ??
                (data['callerId'] as String?) ??
                '',
            peerName: callerName,
            peerPhotoUrl: callerPhotoUrl,
            video: data['video'] == true,
          ),
        ),
      );
    } finally {
      if (mounted && _activeIncomingCallId == callId) {
        _activeIncomingCallId = null;
      }
    }
  }

  bool _callExpired(Map<String, dynamic> data) {
    final expiresAt = data['expiresAt'];
    if (expiresAt is Timestamp) {
      return expiresAt.millisecondsSinceEpoch <=
          DateTime.now().millisecondsSinceEpoch;
    }
    final createdAt = data['createdAt'];
    if (createdAt is Timestamp) {
      return DateTime.now().millisecondsSinceEpoch -
              createdAt.millisecondsSinceEpoch >
          const Duration(minutes: 3).inMilliseconds;
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final initialConversation =
        _conversationFromNotification ??
        (_wapiLocalTestMode ? _wapiLocalTestConversationId : null);
    return WapiShell(
      key: ValueKey(initialConversation ?? 'wapi-shell'),
      user: widget.user,
      initialConversationId: initialConversation,
    );
  }
}

class _LaunchScreen extends StatelessWidget {
  const _LaunchScreen();

  @override
  Widget build(BuildContext context) => Scaffold(
    body: DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          colors: [Color(0xFFF7FBFD), Color(0xFFEAF6FC)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
      ),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 92,
              height: 92,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(28),
                boxShadow: [
                  BoxShadow(
                    color: WapiColors.navy.withValues(alpha: .12),
                    blurRadius: 30,
                    offset: const Offset(0, 12),
                  ),
                ],
              ),
              child: Image.asset(
                'assets/branding/wapi_mark.png',
                fit: BoxFit.contain,
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'WAPI',
              style: TextStyle(
                color: WapiColors.navy,
                fontSize: 31,
                letterSpacing: 2.2,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 5),
            const Text(
              'Votre monde, réuni.',
              style: TextStyle(color: WapiColors.muted, fontSize: 13),
            ),
            const SizedBox(height: 22),
            const SizedBox(
              width: 92,
              child: LinearProgressIndicator(
                minHeight: 3,
                borderRadius: BorderRadius.all(Radius.circular(8)),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _StartupIssue extends StatelessWidget {
  const _StartupIssue();

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('WAPI')),
    body: const Padding(
      padding: EdgeInsets.all(24),
      child: Center(
        child: Text(
          'WAPI ne peut pas restaurer votre session pour le moment.\n\nFermez puis relancez WAPI.',
          textAlign: TextAlign.center,
        ),
      ),
    ),
  );
}
