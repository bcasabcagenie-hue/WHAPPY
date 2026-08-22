import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/phone_auth_page.dart';
import '../features/calls/wapi_call_page.dart';
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

  @override
  void initState() {
    super.initState();
    WapiNotifications.initialize(
      onTap: _openNotification,
    ).then((_) => WapiNotifications.register(widget.user));
  }

  Future<void> _openNotification(String payload) async {
    if (payload.startsWith('conversation:')) {
      final conversationId = payload.substring('conversation:'.length);
      if (conversationId.isNotEmpty && mounted) {
        setState(() => _conversationFromNotification = conversationId);
      }
      return;
    }
    if (!payload.startsWith('call:')) return;
    final callId = payload.substring('call:'.length);
    final call = await FirebaseFirestore.instance
        .collection('calls')
        .doc(callId)
        .get();
    final data = call.data();
    if (!call.exists ||
        data == null ||
        data['calleeId'] != widget.user.uid ||
        data['status'] != 'ringing') {
      return;
    }
    final navigator = wapiNavigatorKey.currentState;
    if (navigator == null) {
      return;
    }
    await navigator.push(
      MaterialPageRoute(
        builder: (_) => WapiCallPage.incoming(
          user: widget.user,
          incomingCallId: callId,
          peerId: (data['callerId'] as String?) ?? '',
          peerName: (data['callerName'] as String?) ?? 'Contact WAPI',
          video: data['video'] == true,
        ),
      ),
    );
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
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
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
