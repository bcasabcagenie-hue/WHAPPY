import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

const _channel = AndroidNotificationChannel(
  'wapi_messages',
  'Messages WAPI',
  description: 'Messages, appels et activités WAPI',
  importance: Importance.max,
  playSound: true,
);

final _notifications = FlutterLocalNotificationsPlugin();
typedef WapiNotificationTap = Future<void> Function(String payload);

@pragma('vm:entry-point')
Future<void> wapiFirebaseBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  await WapiNotifications.show(message);
}

class WapiNotifications {
  static Future<void> initialize({WapiNotificationTap? onTap}) async {
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
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
    await androidPlugin?.requestNotificationsPermission();
    FirebaseMessaging.onMessage.listen(show);
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
    final data = message.data;
    final title =
        data['title']?.toString() ?? message.notification?.title ?? 'WAPI';
    final body =
        data['body']?.toString() ??
        message.notification?.body ??
        'Nouvelle activité';
    await _notifications.show(
      DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
      title,
      body,
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'wapi_messages',
          'Messages WAPI',
          channelDescription: 'Messages, appels et activités WAPI',
          importance: Importance.max,
          priority: Priority.high,
          icon: '@mipmap/ic_launcher',
        ),
      ),
      payload: data['callId'] != null
          ? 'call:${data['callId']}'
          : data['conversationId'] != null
          ? 'conversation:${data['conversationId']}'
          : null,
    );
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
