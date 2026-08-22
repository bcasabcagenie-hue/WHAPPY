import 'dart:ui';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

const _channelId = 'wapi_messages';
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

final _notifications = FlutterLocalNotificationsPlugin();
typedef WapiNotificationTap = Future<void> Function(String payload);

@pragma('vm:entry-point')
Future<void> wapiFirebaseBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  await WapiNotifications.show(message);
}

class WapiNotifications {
  static bool _initialized = false;

  static Future<void> initialize({WapiNotificationTap? onTap}) async {
    await _ensureInitialized(onTap: onTap);
    FirebaseMessaging.onMessage.listen(show);
  }

  static Future<void> _ensureInitialized({WapiNotificationTap? onTap}) async {
    if (_initialized) return;
    const android = AndroidInitializationSettings('@drawable/ic_stat_wapi');
    await _notifications.initialize(
      const InitializationSettings(android: android),
      onDidReceiveNotificationResponse: (response) {
        final payload = response.payload;
        if (payload != null && onTap != null) {
          onTap(payload);
        }
      },
    );
    final androidPlugin = _notifications
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >();
    await androidPlugin?.createNotificationChannel(_channel);
    _initialized = true;

    final launchDetails = await _notifications
        .getNotificationAppLaunchDetails();
    final payload = launchDetails?.notificationResponse?.payload;
    if (launchDetails?.didNotificationLaunchApp == true &&
        payload != null &&
        onTap != null) {
      await onTap(payload);
    }
  }

  static Future<void> register(User user) async {
    await FirebaseMessaging.instance.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );
    final token = await FirebaseMessaging.instance.getToken();
    if (token != null) await _saveToken(user.uid, token);
    FirebaseMessaging.instance.onTokenRefresh.listen(
      (token) => _saveToken(user.uid, token),
    );
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
    final badgeCount = int.tryParse(data['badgeCount']?.toString() ?? '') ?? 1;
    final isMessage = type == 'message' && conversationId.isNotEmpty;
    final id = isMessage
        ? _stableId(conversationId)
        : _stableId(data['callId']?.toString() ?? body);
    final payload = data['callId'] != null
        ? 'call:${data['callId']}'
        : conversationId.isNotEmpty
        ? 'conversation:$conversationId'
        : null;

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
          icon: '@drawable/ic_stat_wapi',
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
                ? '$badgeCount conversations non lues'
                : '1 conversation non lue',
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
    await _ensureInitialized();
    final conversations = await FirebaseFirestore.instance
        .collection('conversations')
        .where('memberIds', arrayContains: userId)
        .get();
    var unread = 0;
    for (final document in conversations.docs) {
      final data = document.data();
      final updatedAt = data['updatedAt'];
      final lastSenderId = data['lastSenderId'];
      final readBy = Map<String, dynamic>.from(
        data['readBy'] as Map? ?? const {},
      );
      final readAt = readBy[userId];
      if (updatedAt is Timestamp &&
          lastSenderId != userId &&
          (readAt is! Timestamp || updatedAt.compareTo(readAt) > 0)) {
        unread++;
      }
    }
    await _showMessageSummary(unread);
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
          icon: '@drawable/ic_stat_wapi',
          color: const Color(0xFF0094F0),
          number: count.clamp(0, 99),
          groupKey: _groupKey,
          setAsGroupSummary: true,
          groupAlertBehavior: GroupAlertBehavior.children,
          onlyAlertOnce: true,
          styleInformation: InboxStyleInformation(
            const [],
            contentTitle: 'Messages WAPI',
            summaryText: '$count conversations non lues',
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

  static Future<void> _saveToken(String userId, String token) =>
      FirebaseFirestore.instance
          .collection('users')
          .doc(userId)
          .collection('devices')
          .doc(token.replaceAll('/', '_'))
          .set({
            'token': token,
            'platform': 'android',
            'enabled': true,
            'updatedAt': FieldValue.serverTimestamp(),
          }, SetOptions(merge: true));
}
