import 'dart:async';
import 'dart:ui';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

// A versioned channel ensures devices that already had an older WAPI build
// receive the current sound, vibration and badge configuration. Android keeps
// channel settings immutable after their first creation.
const _channelId = 'wapi_messages_v3';
const _callChannelId = 'wapi_calls_v1';
const _groupKey = 'wapi_message_group';
const _summaryId = 2;

const _channel = AndroidNotificationChannel(
  _channelId,
  'Messages WAPI',
  description: 'Messages, appels et activités WAPI',
  importance: Importance.max,
  playSound: true,
  enableVibration: true,
  showBadge: true,
);

const _callChannel = AndroidNotificationChannel(
  _callChannelId,
  'Appels WAPI',
  description: 'Appels audio et vidéo entrants WAPI',
  importance: Importance.max,
  playSound: true,
  enableVibration: true,
  showBadge: false,
  audioAttributesUsage: AudioAttributesUsage.notificationRingtone,
);

final _notifications = FlutterLocalNotificationsPlugin();
typedef WapiNotificationTap =
    Future<void> Function(String payload, String? actionId);

@pragma('vm:entry-point')
Future<void> wapiFirebaseBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  // Android displays notification payloads itself while WAPI is terminated.
  // Data-only events (notably incoming calls and cancellation events) still
  // need the Dart background isolate to build their actionable notification.
  if (message.notification == null) {
    await WapiNotifications.show(message);
  }
}

class WapiNotifications {
  static bool _initialized = false;
  static WapiNotificationTap? _onTap;
  static StreamSubscription<RemoteMessage>? _foregroundSubscription;
  static StreamSubscription<RemoteMessage>? _openedSubscription;
  static StreamSubscription<String>? _tokenSubscription;
  static String? _registeredUserId;
  static bool _initialRemoteHandled = false;

  static Future<void> initialize({WapiNotificationTap? onTap}) async {
    if (onTap != null) _onTap = onTap;
    await _ensureInitialized(onTap: onTap);
    _foregroundSubscription ??= FirebaseMessaging.onMessage.listen(show);
    _openedSubscription ??= FirebaseMessaging.onMessageOpenedApp.listen(
      _openRemoteMessage,
    );
    if (!_initialRemoteHandled) {
      _initialRemoteHandled = true;
      final initial = await FirebaseMessaging.instance.getInitialMessage();
      if (initial != null) await _openRemoteMessage(initial);
    }
  }

  static Future<void> _ensureInitialized({WapiNotificationTap? onTap}) async {
    if (onTap != null) _onTap = onTap;
    if (_initialized) return;
    const android = AndroidInitializationSettings('ic_stat_wapi');
    await _notifications.initialize(
      const InitializationSettings(android: android),
      onDidReceiveNotificationResponse: (response) {
        final payload = response.payload;
        final handler = _onTap;
        if (payload != null && handler != null) {
          unawaited(handler(payload, response.actionId));
        }
      },
    );
    final androidPlugin = _notifications
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >();
    await androidPlugin?.createNotificationChannel(_channel);
    await androidPlugin?.createNotificationChannel(_callChannel);
    _initialized = true;

    final launchDetails = await _notifications
        .getNotificationAppLaunchDetails();
    final payload = launchDetails?.notificationResponse?.payload;
    if (launchDetails?.didNotificationLaunchApp == true &&
        payload != null &&
        _onTap != null) {
      await _onTap!(payload, launchDetails?.notificationResponse?.actionId);
    }
  }

  static Future<void> register(User user) async {
    try {
      await FirebaseMessaging.instance.setAutoInitEnabled(true);
      await FirebaseMessaging.instance.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
      final androidPlugin = _notifications
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >();
      await androidPlugin?.requestFullScreenIntentPermission();
      final token = await FirebaseMessaging.instance.getToken().timeout(
        const Duration(seconds: 12),
      );
      if (token != null) {
        await _saveToken(user.uid, token).timeout(const Duration(seconds: 12));
      }
      if (_registeredUserId != user.uid || _tokenSubscription == null) {
        await _tokenSubscription?.cancel();
        _registeredUserId = user.uid;
        _tokenSubscription = FirebaseMessaging.instance.onTokenRefresh.listen(
          (token) => unawaited(_saveToken(user.uid, token).catchError((_) {})),
        );
      }
    } catch (_) {
      // Messaging registration retries when WAPI resumes. It must never block
      // the authenticated application when the network is temporarily absent.
    }
  }

  static Future<void> dismissCall(String callId) =>
      _notifications.cancel(_stableId(callId));

  static Future<void> _openRemoteMessage(RemoteMessage message) async {
    final payload = _payloadFor(message.data);
    final handler = _onTap;
    if (payload != null && handler != null) {
      await handler(payload, null);
    }
  }

  static String? _payloadFor(Map<String, dynamic> data) {
    final callId = data['callId']?.toString() ?? '';
    if (callId.isNotEmpty) return 'call:$callId';
    final conversationId = data['conversationId']?.toString() ?? '';
    if (conversationId.isNotEmpty) return 'conversation:$conversationId';
    final liveId = data['liveId']?.toString() ?? '';
    if (liveId.isNotEmpty) return 'live:$liveId';
    return null;
  }

  static Future<void> show(RemoteMessage message) async {
    await _ensureInitialized();
    final data = message.data;
    final type = data['type']?.toString() ?? '';
    final title =
        data['title']?.toString() ?? message.notification?.title ?? 'WAPI';
    final body =
        data['body']?.toString() ??
        message.notification?.body ??
        'Nouvelle activité';
    final conversationId = data['conversationId']?.toString() ?? '';
    final liveId = data['liveId']?.toString() ?? '';
    final badgeCount = int.tryParse(data['badgeCount']?.toString() ?? '') ?? 1;
    final isMessage = type == 'message' && conversationId.isNotEmpty;
    final isIncomingCall =
        (type == 'incoming_call' || type == 'direct_call') &&
        data['callId'] != null;
    final id = isMessage
        ? _stableId(conversationId)
        : liveId.isNotEmpty
        ? _stableId('live:$liveId')
        : _stableId(data['callId']?.toString() ?? body);
    final payload = _payloadFor(data);
    if ((type == 'call_answered' || type == 'call_cancel') &&
        data['callId'] != null) {
      await _notifications.cancel(id);
      return;
    }

    if (isIncomingCall) {
      await _notifications.show(
        id,
        title,
        body,
        NotificationDetails(
          android: AndroidNotificationDetails(
            _callChannelId,
            'Appels WAPI',
            channelDescription: 'Appels audio et vidéo entrants WAPI',
            importance: Importance.max,
            priority: Priority.max,
            category: AndroidNotificationCategory.call,
            fullScreenIntent: true,
            ongoing: true,
            autoCancel: true,
            icon: 'ic_stat_wapi',
            color: const Color(0xFF0094F0),
            actions: const [
              AndroidNotificationAction(
                'answer_call',
                'Répondre',
                showsUserInterface: true,
              ),
              AndroidNotificationAction('decline_call', 'Refuser'),
            ],
          ),
        ),
        payload: payload,
      );
      return;
    }

    await _notifications.show(
      id,
      title,
      body,
      NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          'Messages WAPI',
          channelDescription: 'Messages, appels et activités WAPI',
          importance: Importance.max,
          priority: Priority.high,
          icon: 'ic_stat_wapi',
          color: const Color(0xFF0094F0),
          number: badgeCount.clamp(0, 99),
          groupKey: isMessage ? _groupKey : null,
          groupAlertBehavior: isMessage
              ? GroupAlertBehavior.children
              : GroupAlertBehavior.all,
          styleInformation: InboxStyleInformation(
            [body],
            contentTitle: title,
            summaryText: badgeCount > 1
                ? '$badgeCount messages non lus'
                : '1 message non lu',
          ),
        ),
      ),
      payload: payload,
    );

    if (isMessage) {
      await _showMessageSummary(badgeCount);
    }
  }

  static Future<void> syncUnreadBadge(String userId) async {
    try {
      await _ensureInitialized();
      final inbox = await FirebaseFirestore.instance
          .collection('users')
          .doc(userId)
          .collection('notificationState')
          .doc('inbox')
          .get()
          .timeout(const Duration(seconds: 12));
      final unread = (inbox.data()?['unreadMessages'] as num?)?.toInt() ?? 0;
      await _showMessageSummary(unread);
    } catch (_) {
      // The next inbox snapshot or app resume refreshes the badge.
    }
  }

  static Future<void> clearConversation(
    String conversationId,
    String userId,
  ) async {
    await _notifications.cancel(_stableId(conversationId));
    await syncUnreadBadge(userId);
  }

  static Future<void> _showMessageSummary(int count) async {
    if (count <= 0) {
      await _notifications.cancel(_summaryId);
      return;
    }
    await _notifications.show(
      _summaryId,
      'WAPI',
      '$count message${count > 1 ? 's non lus' : ' non lu'}',
      NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          'Messages WAPI',
          channelDescription: 'Messages, appels et activités WAPI',
          importance: Importance.max,
          priority: Priority.high,
          icon: 'ic_stat_wapi',
          color: const Color(0xFF0094F0),
          number: count.clamp(0, 99),
          groupKey: _groupKey,
          setAsGroupSummary: true,
          groupAlertBehavior: GroupAlertBehavior.children,
          onlyAlertOnce: true,
          styleInformation: InboxStyleInformation(
            const [],
            contentTitle: 'Messages WAPI',
            summaryText: '$count messages non lus',
          ),
        ),
      ),
      payload: 'inbox',
    );
  }

  static int _stableId(String value) {
    var hash = 17;
    for (final unit in value.codeUnits) {
      hash = 37 * hash + unit;
    }
    return hash.abs() % 2000000000 + 100;
  }

  static Future<void> _saveToken(String userId, String token) async {
    final platform = defaultTargetPlatform == TargetPlatform.iOS
        ? 'ios'
        : 'android';
    try {
      await FirebaseFunctions.instanceFor(region: 'europe-west1')
          .httpsCallable(
            'registerPushDevice',
            options: HttpsCallableOptions(timeout: const Duration(seconds: 8)),
          )
          .call<Map<String, dynamic>>({'token': token, 'platform': platform});
      return;
    } catch (_) {
      // Keep compatibility with a backend that has not yet received the new
      // callable. Firestore remains protected by the signed-in user's path.
    }
    await FirebaseFirestore.instance
        .collection('users')
        .doc(userId)
        .collection('devices')
        .doc(token.replaceAll('/', '_'))
        .set({
          'token': token,
          'platform': platform,
          'enabled': true,
          'updatedAt': FieldValue.serverTimestamp(),
        }, SetOptions(merge: true))
        .timeout(const Duration(seconds: 8));
  }
}
