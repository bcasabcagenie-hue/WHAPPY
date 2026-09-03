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

class WapiApp extends ConsumerWidget {
  const WapiApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authStateProvider);
    return MaterialApp(
      navigatorKey: wapiNavigatorKey,
      title: 'WAPI',
      debugShowCheckedModeBanner: false,
      theme: WapiTheme.light(),
      home: session.when(
        loading: () => const _LaunchScreen(),
        error: (error, _) => _StartupIssue(error: error),
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

class _AuthenticatedWapiState extends State<_AuthenticatedWapi> {
  String? _conversationFromNotification;
  StreamSubscription<QuerySnapshot<Map<String, dynamic>>>? _incomingCalls;
  String? _activeIncomingCallId;

  @override
  void initState() {
    super.initState();
    WapiNotifications.initialize(
      onTap: _openNotification,
    ).then((_) => WapiNotifications.register(widget.user));
    _watchIncomingCalls();
  }

  @override
  void dispose() {
    _incomingCalls?.cancel();
    super.dispose();
  }

  void _watchIncomingCalls() {
    _incomingCalls = FirebaseFirestore.instance
        .collection('directCallSessions')
        .where('calleeId', isEqualTo: widget.user.uid)
        .limit(25)
        .snapshots()
        .listen((snapshot) {
          for (final call in snapshot.docs) {
            final data = call.data();
            if (data['status'] == 'ringing') {
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
          .call<Map<String, dynamic>>({'callId': callId, 'action': 'decline'});
      return;
    }
    Map<String, dynamic> data;
    try {
      final result = await functions
          .httpsCallable('getDirectCallSession')
          .call<Map<String, dynamic>>({'callId': callId});
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
        data['status'] == 'declined') {
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

  @override
  Widget build(BuildContext context) => WapiShell(
    key: ValueKey(_conversationFromNotification ?? 'wapi-shell'),
    user: widget.user,
    initialConversationId: _conversationFromNotification,
  );
}

class _LaunchScreen extends StatelessWidget {
  const _LaunchScreen();

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            'WAPI',
            style: TextStyle(
              color: Color(0xFF062233),
              fontSize: 34,
              letterSpacing: 1.2,
              fontWeight: FontWeight.w900,
            ),
          ),
          SizedBox(height: 10),
          SizedBox(width: 24, child: LinearProgressIndicator(minHeight: 3)),
        ],
      ),
    ),
  );
}

class _StartupIssue extends StatelessWidget {
  const _StartupIssue({required this.error});

  final Object error;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('WAPI')),
    body: Padding(
      padding: const EdgeInsets.all(24),
      child: Text('La session ne peut pas être restaurée.\n$error'),
    ),
  );
}
