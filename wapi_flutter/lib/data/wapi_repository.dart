import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:crypto/crypto.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class WapiConversation {
  const WapiConversation({
    required this.id,
    required this.title,
    required this.preview,
    required this.updatedAt,
    required this.unread,
    required this.isGroup,
    required this.peerId,
    required this.avatarUrl,
    required this.memberNames,
    required this.memberPhotoUrls,
    required this.ownerId,
  });

  final String id;
  final String title;
  final String preview;
  final DateTime? updatedAt;
  final bool unread;
  final bool isGroup;
  final String peerId;
  final String avatarUrl;
  final Map<String, String> memberNames;
  final Map<String, String> memberPhotoUrls;
  final String ownerId;

  factory WapiConversation.fromDoc(
    DocumentSnapshot<Map<String, dynamic>> doc,
    String userId,
  ) {
    final data = doc.data() ?? const <String, dynamic>{};
    final memberIds = List<String>.from(data['memberIds'] as List? ?? const []);
    final members = List<Map<String, dynamic>>.from(
      (data['members'] as List? ?? const []).whereType<Map>().map(
        (member) => Map<String, dynamic>.from(member),
      ),
    );
    final isGroup = data['conversationType'] == 'group' || memberIds.length > 2;
    final peer = members.where((member) => member['uid'] != userId).firstOrNull;
    final peerId = (peer?['uid'] as String?)?.trim().isNotEmpty == true
        ? peer!['uid'] as String
        : memberIds.where((id) => id != userId).firstOrNull ?? '';
    final readBy = Map<String, dynamic>.from(
      data['readBy'] as Map? ?? const {},
    );
    final updatedAt = data['updatedAt'] as Timestamp?;
    final readAt = readBy[userId] as Timestamp?;
    final stamp = data['updatedAt'];
    return WapiConversation(
      id: doc.id,
      title: isGroup
          ? ((data['title'] as String?)?.trim().isNotEmpty == true
                ? data['title'] as String
                : 'Groupe WAPI')
          : ((peer?['displayName'] as String?)?.trim().isNotEmpty == true
                ? peer!['displayName'] as String
                : (data['contactName'] as String?) ?? 'Contact WAPI'),
      preview: (data['lastMessage'] as String?) ?? '',
      updatedAt: stamp is Timestamp ? stamp.toDate() : null,
      unread:
          updatedAt != null &&
          (readAt == null || updatedAt.compareTo(readAt) > 0) &&
          data['lastSenderId'] != userId,
      isGroup: isGroup,
      peerId: isGroup ? '' : peerId,
      avatarUrl: isGroup
          ? (data['groupPhotoUrl'] as String?) ?? ''
          : (peer?['photoUrl'] as String?) ??
                (data['contactPhotoUrl'] as String?) ??
                '',
      memberNames: {
        for (final member in members)
          if ((member['uid'] as String?)?.isNotEmpty == true)
            member['uid'] as String:
                (member['displayName'] as String?) ?? 'Membre WAPI',
      },
      memberPhotoUrls: {
        for (final member in members)
          if ((member['uid'] as String?)?.isNotEmpty == true)
            member['uid'] as String: (member['photoUrl'] as String?) ?? '',
      },
      ownerId: (data['ownerId'] as String?) ?? '',
    );
  }
}

class WapiMessage {
  const WapiMessage({
    required this.id,
    required this.text,
    required this.senderId,
    required this.createdAt,
    required this.kind,
    required this.mediaUrl,
    required this.mediaName,
    required this.contentType,
    required this.mediaSizeBytes,
    required this.mediaSha256,
    required this.durationSeconds,
    required this.replyToId,
    required this.replyText,
    required this.viewOnce,
    required this.viewedBy,
    required this.reactions,
    required this.voiceTranslations,
  });

  final String id;
  final String text;
  final String senderId;
  final DateTime? createdAt;
  final String kind;
  final String mediaUrl;
  final String mediaName;
  final String contentType;
  final int mediaSizeBytes;
  final String mediaSha256;
  final int durationSeconds;
  final String replyToId;
  final String replyText;
  final bool viewOnce;
  final Map<String, dynamic> viewedBy;
  final Map<String, String> reactions;
  final Map<String, WapiVoiceTranslation> voiceTranslations;

  factory WapiMessage.fromDoc(DocumentSnapshot<Map<String, dynamic>> doc) {
    return WapiMessage.fromMap(
      id: doc.id,
      data: doc.data() ?? const <String, dynamic>{},
    );
  }

  factory WapiMessage.fromMap({
    required String id,
    required Map<String, dynamic> data,
  }) {
    String firstString(List<String> keys) {
      for (final key in keys) {
        final value = data[key];
        if (value is String && value.trim().isNotEmpty) return value.trim();
      }
      return '';
    }

    num? firstNumber(List<String> keys) {
      for (final key in keys) {
        final value = data[key];
        if (value is num) return value;
        if (value is String) {
          final parsed = num.tryParse(value);
          if (parsed != null) return parsed;
        }
      }
      return null;
    }

    final text = firstString(const ['text', 'message', 'caption']);
    final mediaUrl = firstString(const [
      'mediaUrl',
      'audioUrl',
      'voiceUrl',
      'downloadUrl',
      'fileUrl',
      'url',
    ]);
    final contentType = firstString(const [
      'contentType',
      'mimeType',
      'mediaMimeType',
    ]).toLowerCase();
    final rawKind = firstString(const [
      'kind',
      'messageType',
      'mediaType',
      'type',
    ]).toLowerCase();
    final normalizedText = text.toLowerCase();
    final normalizedUrl = mediaUrl.toLowerCase().split('?').first;
    final looksLikeAudio =
        contentType.startsWith('audio/') ||
        normalizedUrl.endsWith('.m4a') ||
        normalizedUrl.endsWith('.aac') ||
        normalizedUrl.endsWith('.mp3') ||
        normalizedUrl.endsWith('.ogg') ||
        normalizedUrl.endsWith('.opus') ||
        normalizedUrl.endsWith('.wav') ||
        normalizedText == 'message vocal' ||
        normalizedText == 'note vocale';
    final kind = switch (rawKind) {
      'audio' || 'voice' || 'voice_note' || 'voice-note' || 'vocal' => 'audio',
      'image' || 'photo' || 'picture' => 'image',
      'video' => 'video',
      'document' || 'file' => 'document',
      _ => looksLikeAudio ? 'audio' : 'text',
    };
    final stamp = data['createdAt'] ?? data['timestamp'] ?? data['sentAt'];
    final rawDuration = firstNumber(const [
      'duration',
      'durationSeconds',
      'audioDuration',
      'voiceDuration',
    ]);
    final explicitMilliseconds = firstNumber(const [
      'durationMs',
      'audioDurationMs',
    ]);
    final durationSeconds = explicitMilliseconds != null
        ? (explicitMilliseconds / 1000).round()
        : rawDuration != null && rawDuration > 600
        ? (rawDuration / 1000).round()
        : rawDuration?.round() ?? 0;
    final nestedReply = data['replyTo'] is Map
        ? Map<String, dynamic>.from(data['replyTo'] as Map)
        : const <String, dynamic>{};
    return WapiMessage(
      id: id,
      text: text,
      senderId: firstString(const ['senderId', 'authorId', 'userId']),
      createdAt: switch (stamp) {
        Timestamp value => value.toDate(),
        DateTime value => value,
        num value => DateTime.fromMillisecondsSinceEpoch(value.toInt()),
        String value => DateTime.tryParse(value),
        _ => null,
      },
      kind: kind,
      mediaUrl: mediaUrl,
      mediaName: firstString(const ['mediaName', 'fileName', 'name']),
      contentType: contentType,
      mediaSizeBytes:
          firstNumber(const ['mediaSizeBytes', 'fileSize', 'size'])?.toInt() ??
          0,
      mediaSha256: firstString(const ['mediaSha256', 'sha256']),
      durationSeconds: durationSeconds.clamp(0, 600).toInt(),
      replyToId: firstString(const ['replyToId']).isNotEmpty
          ? firstString(const ['replyToId'])
          : (nestedReply['id'] as String?) ?? '',
      replyText: firstString(const ['replyText']).isNotEmpty
          ? firstString(const ['replyText'])
          : (nestedReply['text'] as String?) ?? '',
      viewOnce: data['viewOnce'] == true,
      viewedBy: Map<String, dynamic>.from(data['viewedBy'] as Map? ?? const {}),
      reactions: Map<String, String>.from(
        (data['reactions'] as Map? ?? const {}).map(
          (key, value) => MapEntry(key.toString(), value.toString()),
        ),
      ),
      voiceTranslations: {
        for (final entry
            in (data['voiceTranslations'] as Map? ?? const {}).entries)
          if (entry.value is Map)
            entry.key.toString(): WapiVoiceTranslation.fromMap(
              Map<String, dynamic>.from(entry.value as Map),
            ),
      },
    );
  }
}

class WapiVoiceTranslation {
  const WapiVoiceTranslation({
    required this.transcript,
    required this.translation,
    required this.detectedLanguage,
    required this.targetLanguage,
    required this.translatedAudioUrl,
  });

  final String transcript;
  final String translation;
  final String detectedLanguage;
  final String targetLanguage;
  final String translatedAudioUrl;

  factory WapiVoiceTranslation.fromMap(Map<String, dynamic> data) {
    return WapiVoiceTranslation(
      transcript: data['transcript']?.toString().trim() ?? '',
      translation: data['translation']?.toString().trim() ?? '',
      detectedLanguage: data['detectedLanguage']?.toString().trim() ?? 'auto',
      targetLanguage: data['targetLanguage']?.toString().trim() ?? '',
      translatedAudioUrl: data['translatedAudioUrl']?.toString().trim() ?? '',
    );
  }
}

class WapiStory {
  const WapiStory({
    required this.id,
    required this.authorId,
    required this.authorName,
    required this.authorPhotoUrl,
    required this.text,
    required this.createdAt,
    required this.mediaUrl,
    required this.mediaType,
    required this.viewCount,
    required this.viewedByCurrentUser,
  });

  final String id;
  final String authorId;
  final String authorName;
  final String authorPhotoUrl;
  final String text;
  final DateTime? createdAt;
  final String mediaUrl;
  final String mediaType;
  final int viewCount;
  final bool viewedByCurrentUser;

  factory WapiStory.fromDoc(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? const <String, dynamic>{};
    final stamp = data['createdAt'];
    return WapiStory(
      id: doc.id,
      authorId: (data['authorId'] as String?) ?? '',
      authorName: (data['authorName'] as String?) ?? 'Contact WAPI',
      authorPhotoUrl: (data['authorPhotoUrl'] as String?) ?? '',
      text: (data['caption'] as String?) ?? '',
      createdAt: stamp is Timestamp ? stamp.toDate() : null,
      mediaUrl: (data['mediaUrl'] as String?) ?? '',
      mediaType: (data['mediaType'] as String?) ?? 'text',
      viewCount: (data['viewCount'] as num?)?.toInt() ?? 0,
      viewedByCurrentUser: data['viewedByCurrentUser'] == true,
    );
  }

  factory WapiStory.fromMap(Map<String, dynamic> data) {
    final rawCreatedAt = data['createdAt'];
    final createdAtMillis = (data['createdAtMillis'] as num?)?.toInt() ?? 0;
    return WapiStory(
      id: (data['id'] as String?) ?? '',
      authorId: (data['authorId'] as String?) ?? '',
      authorName: (data['authorName'] as String?) ?? 'Contact WAPI',
      authorPhotoUrl: (data['authorPhotoUrl'] as String?) ?? '',
      text: (data['caption'] as String?) ?? '',
      createdAt: rawCreatedAt is String
          ? DateTime.tryParse(rawCreatedAt)
          : createdAtMillis > 0
          ? DateTime.fromMillisecondsSinceEpoch(createdAtMillis)
          : null,
      mediaUrl: (data['mediaUrl'] as String?) ?? '',
      mediaType: (data['mediaType'] as String?) ?? 'text',
      viewCount: (data['viewCount'] as num?)?.toInt() ?? 0,
      viewedByCurrentUser: data['viewedByCurrentUser'] == true,
    );
  }
}

class WapiRepository {
  WapiRepository(this._db);

  final FirebaseFirestore _db;
  final FirebaseStorage _storage = FirebaseStorage.instance;
  static List<WapiStory> _storyCache = const [];
  static String _storyCacheUserId = '';

  static String _storyCacheKey(String userId) => 'wapi.story-cache.v1.$userId';

  static Future<List<WapiStory>> _restoreStoryCache(String userId) async {
    try {
      final encoded = (await SharedPreferences.getInstance()).getString(
        _storyCacheKey(userId),
      );
      if (encoded == null || encoded.isEmpty) return const [];
      final decoded = jsonDecode(encoded);
      if (decoded is! List) return const [];
      final now = DateTime.now().millisecondsSinceEpoch;
      return decoded
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .where((item) {
            final expires = item['expiresAtMillis'];
            if (expires is num) return expires.toInt() > now;
            final created = item['createdAtMillis'];
            return created is num &&
                now - created.toInt() <
                    const Duration(hours: 24).inMilliseconds;
          })
          .map(WapiStory.fromMap)
          .toList(growable: false);
    } catch (_) {
      return const [];
    }
  }

  static Future<void> _persistStoryCache(
    String userId,
    List<Map<String, dynamic>> stories,
  ) async {
    try {
      final compact = stories.take(80).toList(growable: false);
      await (await SharedPreferences.getInstance()).setString(
        _storyCacheKey(userId),
        jsonEncode(compact),
      );
    } catch (_) {
      // The in-memory cache remains available for this application session.
    }
  }

  Stream<List<WapiConversation>> conversations(String userId) => _db
      .collection('conversations')
      .where('memberIds', arrayContains: userId)
      .orderBy('updatedAt', descending: true)
      .snapshots()
      .map(
        (snapshot) => snapshot.docs
            .map((doc) => WapiConversation.fromDoc(doc, userId))
            .toList(),
      );

  Stream<List<WapiMessage>> messages(String conversationId) => _db
      .collection('conversations')
      .doc(conversationId)
      .collection('messages')
      .orderBy('createdAt')
      .snapshots()
      .map((snapshot) => snapshot.docs.map(WapiMessage.fromDoc).toList());

  Stream<List<WapiStory>> stories(String userId) async* {
    if (userId.isEmpty) {
      yield const [];
      return;
    }
    final callable = FirebaseFunctions.instanceFor(
      region: 'europe-west1',
    ).httpsCallable('listVisibleStories');
    if (_storyCacheUserId != userId) {
      _storyCacheUserId = userId;
      _storyCache = await _restoreStoryCache(userId);
    }
    var latest = _storyCache;
    if (latest.isNotEmpty) yield latest;
    while (true) {
      try {
        final result = await callable.call<Map<String, dynamic>>().timeout(
          const Duration(seconds: 15),
        );
        final rawStories = List<Map<String, dynamic>>.from(
          (result.data['stories'] as List? ?? const []).map(
            (story) => Map<String, dynamic>.from(story as Map),
          ),
        );
        latest = rawStories.map(WapiStory.fromMap).toList(growable: false);
        _storyCache = latest;
        unawaited(_persistStoryCache(userId, rawStories));
        yield latest;
      } catch (_) {
        // A temporary mobile-network failure must not remove the complete
        // Stories rail or leave the screen permanently in an error state.
        if (latest.isNotEmpty) yield latest;
      }
      await Future<void>.delayed(const Duration(seconds: 8));
    }
  }

  Future<void> recordStoryView(String storyId) async {
    await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('recordStoryView')
        .call<Map<String, dynamic>>({'storyId': storyId})
        .timeout(const Duration(seconds: 12));
  }

  Future<List<Map<String, dynamic>>> storyViewers(String storyId) async {
    final result = await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('listStoryViewers')
        .call<Map<String, dynamic>>({'storyId': storyId})
        .timeout(const Duration(seconds: 15));
    return (result.data['viewers'] as List? ?? const [])
        .whereType<Map>()
        .map((value) => Map<String, dynamic>.from(value))
        .toList(growable: false);
  }

  Future<void> deleteStory(String storyId) async {
    await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('deleteStory')
        .call<Map<String, dynamic>>({'storyId': storyId})
        .timeout(const Duration(seconds: 15));
  }

  Stream<DocumentSnapshot<Map<String, dynamic>>> profile(String userId) =>
      _db.collection('users').doc(userId).snapshots();

  /// Resolves only an existing WAPI account. A QR code carries the opaque
  /// Firebase uid, while manual entry accepts the verified E.164 phone number
  /// stored on the WAPI profile.
  Future<Map<String, String>> resolveWapiContact(String value) async {
    final input = value.trim();
    if (input.isEmpty) {
      throw ArgumentError('Saisissez un numéro WAPI ou scannez un code QR.');
    }
    const prefix = 'wapi://contact/';
    DocumentSnapshot<Map<String, dynamic>>? profile;
    if (input.startsWith(prefix)) {
      final uid = input.substring(prefix.length).trim();
      if (uid.isEmpty) throw ArgumentError('Ce code QR WAPI est invalide.');
      profile = await _db.collection('users').doc(uid).get();
    } else {
      final phone = input.replaceAll(RegExp(r'[^0-9+]'), '');
      if (phone.length < 8) {
        throw ArgumentError('Utilisez le code QR ou le numéro international.');
      }
      final result = await _db
          .collection('users')
          .where('phoneNumber', isEqualTo: phone)
          .limit(1)
          .get();
      if (result.docs.isNotEmpty) profile = result.docs.first;
    }
    if (profile == null || !profile.exists) {
      throw StateError('Aucun compte WAPI correspondant n’a été trouvé.');
    }
    final data = profile.data() ?? const <String, dynamic>{};
    final name = (data['displayName'] as String?)?.trim();
    return {
      'uid': profile.id,
      'displayName': name?.isNotEmpty == true
          ? name!
          : (data['phoneNumber'] as String?) ?? 'Membre WAPI',
      'phoneNumber': (data['phoneNumber'] as String?) ?? '',
      'photoUrl': (data['photoUrl'] as String?) ?? '',
    };
  }

  Future<void> markConversationRead({
    required String conversationId,
    required String userId,
  }) async {
    final conversation = _db.collection('conversations').doc(conversationId);
    final inbox = _db
        .collection('users')
        .doc(userId)
        .collection('notificationState')
        .doc('inbox');
    final counter = inbox.collection('conversations').doc(conversationId);

    await _db.runTransaction((transaction) async {
      final counterSnapshot = await transaction.get(counter);
      final inboxSnapshot = await transaction.get(inbox);
      final unreadInConversation =
          (counterSnapshot.data()?['unreadMessages'] as num?)?.toInt() ?? 0;
      final unreadTotal =
          (inboxSnapshot.data()?['unreadMessages'] as num?)?.toInt() ?? 0;

      transaction.update(conversation, {
        'readBy.$userId': FieldValue.serverTimestamp(),
      });
      if (counterSnapshot.exists || inboxSnapshot.exists) {
        transaction.set(inbox, {
          'unreadMessages': (unreadTotal - unreadInConversation)
              .clamp(0, 9999)
              .toInt(),
          'updatedAt': FieldValue.serverTimestamp(),
        }, SetOptions(merge: true));
        transaction.set(counter, {
          'unreadMessages': 0,
          'updatedAt': FieldValue.serverTimestamp(),
        }, SetOptions(merge: true));
      }
    });
  }

  Future<void> updatePresence({required User user, required bool isOnline}) =>
      _db.collection('users').doc(user.uid).set({
        'uid': user.uid,
        'isOnline': isOnline,
        'lastSeenAt': FieldValue.serverTimestamp(),
        'updatedAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));

  Future<String> createChannel({
    required User user,
    required String name,
    required String description,
    required String category,
  }) async {
    final channelName = name.trim();
    final channelDescription = description.trim();
    if (channelName.length < 3 || channelName.length > 80) {
      throw ArgumentError(
        'Le nom de la chaîne doit contenir entre 3 et 80 caractères.',
      );
    }
    if (channelDescription.length < 10 || channelDescription.length > 300) {
      throw ArgumentError(
        'La présentation doit contenir entre 10 et 300 caractères.',
      );
    }
    final channel = _db.collection('channels').doc();
    await channel.set({
      'ownerId': user.uid,
      'ownerName': user.displayName?.trim().isNotEmpty == true
          ? user.displayName!.trim()
          : user.phoneNumber ?? 'Créateur WAPI',
      'name': channelName,
      'description': channelDescription,
      'category': category.trim().take(40),
      'memberIds': [user.uid],
      'memberCount': 1,
      'postCount': 0,
      'verified': false,
      'lastPost': '',
      'createdAt': FieldValue.serverTimestamp(),
      'updatedAt': FieldValue.serverTimestamp(),
    });
    return channel.id;
  }

  Future<void> joinChannel({
    required String channelId,
    required String userId,
  }) async {
    final channel = _db.collection('channels').doc(channelId);
    await _db.runTransaction((transaction) async {
      final snapshot = await transaction.get(channel);
      final data = snapshot.data();
      if (data == null) throw StateError('Cette chaîne n’existe plus.');
      final members = List<String>.from(data['memberIds'] as List? ?? const []);
      if (members.contains(userId)) return;
      members.add(userId);
      transaction.update(channel, {
        'memberIds': members,
        'memberCount': members.length,
        'updatedAt': FieldValue.serverTimestamp(),
      });
    });
  }

  Future<void> publishChannelPost({
    required User user,
    required String channelId,
    required String text,
  }) async {
    final value = text.trim();
    if (value.isEmpty || value.length > 4000) {
      throw ArgumentError(
        'La publication doit contenir entre 1 et 4000 caractères.',
      );
    }
    final channel = _db.collection('channels').doc(channelId);
    final post = channel.collection('posts').doc();
    final batch = _db.batch();
    batch.set(post, {
      'authorId': user.uid,
      'authorName': user.displayName?.trim().isNotEmpty == true
          ? user.displayName!.trim()
          : user.phoneNumber ?? 'Créateur WAPI',
      'text': value,
      'reactions': <String, String>{},
      'pinned': false,
      'deleted': false,
      'createdAt': FieldValue.serverTimestamp(),
    });
    batch.update(channel, {
      'lastPost': value.take(200),
      'postCount': FieldValue.increment(1),
      'updatedAt': FieldValue.serverTimestamp(),
    });
    await batch.commit();
  }

  Future<void> publishRadioEpisode({
    required User user,
    required File file,
    required String fileName,
    required String stationName,
    required String title,
    required int durationSeconds,
    String contentType = 'audio/mpeg',
  }) async {
    final station = stationName.trim();
    final episodeTitle = title.trim();
    if (station.length < 2 || station.length > 60) {
      throw ArgumentError(
        'Le nom de la station doit contenir entre 2 et 60 caractères.',
      );
    }
    if (episodeTitle.length < 2 || episodeTitle.length > 100) {
      throw ArgumentError('Le titre doit contenir entre 2 et 100 caractères.');
    }
    if (durationSeconds < 1 || durationSeconds > 3600) {
      throw ArgumentError(
        'Le fichier audio doit durer entre 1 seconde et 60 minutes.',
      );
    }
    final safeName = fileName
        .replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_')
        .take(120);
    final path =
        'radio/${user.uid}/${DateTime.now().millisecondsSinceEpoch}-$safeName';
    final ref = _storage.ref(path);
    final safeContentType = contentType.startsWith('audio/')
        ? contentType
        : 'audio/mpeg';
    await ref.putFile(file, SettableMetadata(contentType: safeContentType));
    final url = await ref.getDownloadURL();
    await _db.collection('radioEpisodes').add({
      'ownerId': user.uid,
      'authorName': user.displayName?.trim().isNotEmpty == true
          ? user.displayName!.trim()
          : user.phoneNumber ?? 'Créateur WAPI',
      'stationName': station,
      'title': episodeTitle,
      'audioUrl': url,
      'storagePath': path,
      'durationSeconds': durationSeconds,
      'status': 'published',
      'createdAt': FieldValue.serverTimestamp(),
    });
  }

  Future<String> createGroup({
    required User user,
    required String title,
    required List<Map<String, String>> invitedMembers,
  }) async {
    final value = title.trim();
    if (value.length < 2 || value.length > 80) {
      throw ArgumentError(
        'Le nom du groupe doit contenir entre 2 et 80 caractères.',
      );
    }
    final membersById = <String, Map<String, String>>{
      user.uid: {
        'uid': user.uid,
        'displayName': user.displayName?.trim().isNotEmpty == true
            ? user.displayName!.trim()
            : user.phoneNumber ?? 'Membre WAPI',
        'phoneNumber': user.phoneNumber ?? '',
        'photoUrl': user.photoURL ?? '',
      },
    };
    for (final member in invitedMembers) {
      final uid = member['uid'] ?? '';
      if (uid.isNotEmpty) membersById[uid] = member;
    }
    if (membersById.length < 2) {
      throw ArgumentError('Ajoutez au moins une personne au groupe.');
    }
    final conversation = _db.collection('conversations').doc();
    await conversation.set({
      'ownerId': user.uid,
      'conversationType': 'group',
      'title': value,
      'groupPhotoUrl': '',
      'memberIds': membersById.keys.toList(),
      'members': membersById.values.toList(),
      'typingBy': <String, bool>{},
      'readBy': <String, dynamic>{user.uid: FieldValue.serverTimestamp()},
      'contactId': '',
      'contactName': '',
      'lastMessage': 'Groupe créé',
      'lastSenderId': user.uid,
      'createdAt': FieldValue.serverTimestamp(),
      'updatedAt': FieldValue.serverTimestamp(),
    });
    return conversation.id;
  }

  Future<void> updateGroupDetails({
    required User user,
    required String conversationId,
    required String title,
    File? photo,
  }) async {
    final value = title.trim();
    if (value.length < 2 || value.length > 80) {
      throw ArgumentError(
        'Le nom du groupe doit contenir entre 2 et 80 caractères.',
      );
    }
    final conversation = _db.collection('conversations').doc(conversationId);
    String? photoUrl;
    if (photo != null) {
      final ref = _storage.ref(
        'conversations/$conversationId/${user.uid}/group-${DateTime.now().millisecondsSinceEpoch}.jpg',
      );
      await ref.putFile(photo, SettableMetadata(contentType: 'image/jpeg'));
      photoUrl = await ref.getDownloadURL();
    }
    final message = conversation.collection('messages').doc();
    final name = user.displayName?.trim().isNotEmpty == true
        ? user.displayName!.trim()
        : 'un membre';
    final notice = 'Informations du groupe mises à jour par $name';
    final batch = _db.batch();
    final groupUpdate = <String, dynamic>{
      'title': value,
      'lastMessage': notice,
      'lastSenderId': user.uid,
      'updatedAt': FieldValue.serverTimestamp(),
      'readBy.${user.uid}': FieldValue.serverTimestamp(),
    };
    if (photoUrl != null) {
      groupUpdate['groupPhotoUrl'] = photoUrl;
    }
    batch.update(conversation, groupUpdate);
    batch.set(message, {
      'text': notice,
      'senderId': user.uid,
      'clientMessageId': message.id,
      'createdAt': FieldValue.serverTimestamp(),
    });
    await batch.commit();
  }

  Future<void> manageGroupMembers({
    required String conversationId,
    required String action,
    required List<String> memberIds,
  }) async {
    if (!{'add', 'remove'}.contains(action) || memberIds.isEmpty) {
      throw ArgumentError('Action de groupe invalide.');
    }
    await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('manageGroupMembers')
        .call<Map<String, dynamic>>({
          'conversationId': conversationId,
          'action': action,
          'memberIds': memberIds,
        })
        .timeout(const Duration(seconds: 20));
  }

  Future<void> sendMessage({
    required String conversationId,
    required User user,
    required String text,
    String replyToId = '',
    String replyText = '',
  }) async {
    final value = text.trim();
    if (value.isEmpty) return;
    final conversation = _db.collection('conversations').doc(conversationId);
    final message = conversation.collection('messages').doc();
    final batch = _db.batch();
    final messageData = <String, dynamic>{
      'text': value,
      'senderId': user.uid,
      'clientMessageId': message.id,
      'createdAt': FieldValue.serverTimestamp(),
    };
    if (replyToId.isNotEmpty && replyText.isNotEmpty) {
      messageData['replyToId'] = replyToId;
      messageData['replyText'] = replyText.take(240);
    }
    batch.set(message, messageData);
    batch.update(conversation, {
      'lastMessage': value,
      'lastSenderId': user.uid,
      'updatedAt': FieldValue.serverTimestamp(),
      'typingBy.${user.uid}': false,
      'readBy.${user.uid}': FieldValue.serverTimestamp(),
    });
    await batch.commit();
  }

  Future<void> reactToMessage({
    required String conversationId,
    required String messageId,
    required String userId,
    required String emoji,
  }) async {
    const allowed = {'❤️', '👍', '😂', '😮', '🙏'};
    if (!allowed.contains(emoji)) {
      throw ArgumentError('Réaction non prise en charge.');
    }
    await _db
        .collection('conversations')
        .doc(conversationId)
        .collection('messages')
        .doc(messageId)
        .update({'reactions.$userId': emoji});
  }

  Future<void> forwardMessage({
    required String conversationId,
    required User user,
    required WapiMessage source,
  }) async {
    final conversation = _db.collection('conversations').doc(conversationId);
    final message = conversation.collection('messages').doc();
    final label = switch (source.kind) {
      'image' => 'Photo transférée',
      'video' => 'Vidéo transférée',
      'audio' => 'Note vocale transférée',
      'document' => 'Document transféré',
      _ => source.text,
    };
    final messageData = <String, dynamic>{
      'text': source.text.isEmpty ? label : source.text,
      'senderId': user.uid,
      'clientMessageId': message.id,
      'forwarded': true,
      'createdAt': FieldValue.serverTimestamp(),
    };
    if (source.kind != 'text') {
      if (source.mediaSizeBytes <= 0 ||
          !RegExp(r'^[0-9a-f]{64}$').hasMatch(source.mediaSha256)) {
        throw StateError(
          'Ce média ancien ne peut pas encore être transféré. Téléchargez-le puis envoyez-le à nouveau.',
        );
      }
      messageData.addAll({
        'kind': source.kind,
        'mediaUrl': source.mediaUrl,
        'mediaName': source.mediaName,
        'contentType': source.contentType,
        'mediaSizeBytes': source.mediaSizeBytes,
        'mediaSha256': source.mediaSha256,
        'duration': source.durationSeconds.clamp(0, 600),
      });
      if (source.kind == 'video') {
        messageData['effect'] = 'pop';
        messageData['caption'] = source.text.take(100);
      }
    }
    final batch = _db.batch();
    batch.set(message, messageData);
    batch.update(conversation, {
      'lastMessage': label.take(240),
      'lastSenderId': user.uid,
      'updatedAt': FieldValue.serverTimestamp(),
      'typingBy.${user.uid}': false,
      'readBy.${user.uid}': FieldValue.serverTimestamp(),
    });
    await batch.commit();
  }

  Future<void> sendMediaMessage({
    required String conversationId,
    required User user,
    required File file,
    required String kind,
    required String contentType,
    required String fileName,
    String caption = '',
    int durationSeconds = 0,
    bool viewOnce = false,
    String clientMessageId = '',
  }) async {
    if (!{'image', 'video', 'audio', 'document'}.contains(kind)) {
      throw ArgumentError('Type de média non pris en charge.');
    }
    final safeContentType = kind == 'audio' && !contentType.startsWith('audio/')
        ? 'audio/mp4'
        : contentType;
    final conversation = _db.collection('conversations').doc(conversationId);
    final stableMessageId = clientMessageId.trim();
    if (stableMessageId.isNotEmpty &&
        !RegExp(r'^[a-zA-Z0-9_-]{6,180}$').hasMatch(stableMessageId)) {
      throw ArgumentError('Référence de message invalide.');
    }
    final message = conversation
        .collection('messages')
        .doc(stableMessageId.isEmpty ? null : stableMessageId);
    final displayName = fileName
        .trim()
        .replaceAll(RegExp(r'[\u0000-\u001F\u007F]'), '')
        .take(120);
    if (displayName.isEmpty) {
      throw ArgumentError('Le nom du fichier est invalide.');
    }
    const acceptedDocumentExtensions = <String>{
      'pdf',
      'doc',
      'docx',
      'xls',
      'xlsx',
      'ppt',
      'pptx',
      'txt',
      'csv',
      'rtf',
      'md',
      'json',
      'xml',
      'odt',
      'ods',
      'odp',
      'zip',
      'rar',
      '7z',
      'gz',
    };
    final extension = displayName.contains('.')
        ? displayName.split('.').last.toLowerCase()
        : '';
    if (kind == 'document' && !acceptedDocumentExtensions.contains(extension)) {
      throw ArgumentError('Ce format de document n’est pas accepté par WAPI.');
    }
    final safeName = displayName
        .replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_')
        .take(100);
    final outboxPath =
        'conversationUploads/${user.uid}/$conversationId/${message.id}-$safeName';
    final canonicalPath =
        'conversations/$conversationId/${user.uid}/${message.id}-$safeName';
    final mediaSizeBytes = await file.length();
    if (mediaSizeBytes <= 0) {
      throw StateError('Le fichier média est vide.');
    }
    if (kind == 'document' && mediaSizeBytes > 50 * 1024 * 1024) {
      throw StateError('Le document dépasse la limite WAPI de 50 Mo.');
    }
    const inlineVoiceLimit = 5 * 1024 * 1024;
    final voiceBytes = kind == 'audio' && mediaSizeBytes <= inlineVoiceLimit
        ? await file.readAsBytes()
        : null;
    final mediaSha256 = voiceBytes == null
        ? (await sha256.bind(file.openRead()).first).toString()
        : sha256.convert(voiceBytes).toString();
    Future<bool> mediaAlreadyCommitted() async {
      try {
        final snapshot = await message
            .get(const GetOptions(source: Source.server))
            .timeout(const Duration(seconds: 3));
        final data = snapshot.data();
        return snapshot.exists &&
            data?['senderId'] == user.uid &&
            data?['mediaSha256'] == mediaSha256 &&
            data?['kind'] == kind &&
            (data?['mediaUrl'] as String?)?.isNotEmpty == true;
      } catch (_) {
        return false;
      }
    }

    Future<void> uploadTo(Reference target) async {
      final upload = target.putFile(
        file,
        SettableMetadata(
          contentType: safeContentType,
          customMetadata: {
            'wapiMediaSha256': mediaSha256,
            'wapiConversationId': conversationId,
            'wapiMessageId': message.id,
          },
        ),
      );
      await upload.timeout(
        Duration(
          seconds: kind == 'video'
              ? 120
              : kind == 'document'
              ? 150
              : 45,
        ),
        onTimeout: () {
          upload.cancel();
          throw TimeoutException('Le transfert du média WAPI a expiré.');
        },
      );
    }

    final callable = FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable(
          'commitConversationMedia',
          options: HttpsCallableOptions(
            timeout: Duration(
              seconds: kind == 'audio'
                  ? 15
                  : kind == 'document'
                  ? 180
                  : 120,
            ),
          ),
        );
    Future<void> finalize(
      String storagePath, {
      String inlineMediaBase64 = '',
    }) async {
      await callable.call<Map<String, dynamic>>({
        'conversationId': conversationId,
        'messageId': message.id,
        'kind': kind,
        'storagePath': storagePath,
        'mediaName': displayName,
        'contentType': safeContentType,
        'mediaSha256': mediaSha256,
        'durationSeconds': durationSeconds.clamp(0, 600),
        'caption': caption.trim(),
        'viewOnce': viewOnce,
        if (inlineMediaBase64.isNotEmpty) ...{
          'inlineMediaBase64': inlineMediaBase64,
          'inlineContentType': safeContentType,
        },
      });
    }

    // Short voice notes take the low-latency participant path first. Previous
    // builds waited for a callable cold start before attempting this upload,
    // which made a two-second note feel stuck even on a healthy connection.
    // The immutable message id makes every fallback below idempotent.
    const directVoiceLimit = 2 * 1024 * 1024;
    var canonicalVoiceUploaded = false;
    if (kind == 'audio' && mediaSizeBytes <= directVoiceLimit) {
      try {
        // Storage itself validates conversation membership, so a separate
        // preflight read would only add another network round-trip.
        final canonical = _storage.ref(canonicalPath);
        final upload = canonical.putData(
          voiceBytes ?? await file.readAsBytes(),
          SettableMetadata(
            contentType: safeContentType,
            cacheControl: 'private,max-age=31536000,immutable',
            customMetadata: {
              'wapiMediaSha256': mediaSha256,
              'wapiConversationId': conversationId,
              'wapiMessageId': message.id,
            },
          ),
        );
        await upload.timeout(
          const Duration(seconds: 12),
          onTimeout: () {
            upload.cancel();
            throw TimeoutException('Le transfert vocal WAPI a expiré.');
          },
        );
        canonicalVoiceUploaded = true;
        final mediaUrl = await canonical.getDownloadURL().timeout(
          const Duration(seconds: 5),
        );
        final batch = _db.batch();
        batch.set(message, {
          'text': caption.trim().isEmpty ? 'Message vocal' : caption.trim(),
          'senderId': user.uid,
          'clientMessageId': message.id,
          'kind': 'audio',
          'mediaUrl': mediaUrl,
          'mediaName': displayName,
          'contentType': safeContentType,
          'mediaSizeBytes': mediaSizeBytes,
          'mediaSha256': mediaSha256,
          'duration': durationSeconds.clamp(0, 600),
          'createdAt': FieldValue.serverTimestamp(),
        });
        batch.update(conversation, {
          'lastMessage': 'Message vocal',
          'lastSenderId': user.uid,
          'updatedAt': FieldValue.serverTimestamp(),
          'typingBy.${user.uid}': false,
        });
        await batch.commit().timeout(const Duration(seconds: 6));
        return;
      } catch (_) {
        // A timeout can happen after the write reached Firestore. Reconcile
        // briefly before using the idempotent outbox path with the same id.
        if (await mediaAlreadyCommitted()) return;
      }
    }

    // The trusted server path supports old conversations whose participant
    // maps were incomplete. If Storage already accepted the bytes, only the
    // tiny commit payload is sent; otherwise the compact recording is inlined
    // once so the user never has to record it again.
    if (voiceBytes != null) {
      try {
        await finalize(
          canonicalPath,
          inlineMediaBase64: canonicalVoiceUploaded
              ? ''
              : base64Encode(voiceBytes),
        );
        return;
      } on TimeoutException {
        if (await mediaAlreadyCommitted()) return;
      } on FirebaseFunctionsException catch (error) {
        if (error.code == 'deadline-exceeded' &&
            await mediaAlreadyCommitted()) {
          return;
        }
      } catch (_) {
        // Continue through the durable server outbox below.
      }
    }

    final outbox = _storage.ref(outboxPath);
    try {
      await uploadTo(outbox);
    } on FirebaseException {
      if (await mediaAlreadyCommitted()) return;
      // Some conversations created by older WAPI versions can briefly fail
      // the outbox rule while their membership fields are being normalized.
      // Try the canonical participant-only path before keeping the local note.
      final canonical = _storage.ref(canonicalPath);
      await uploadTo(canonical);
      try {
        await finalize(canonicalPath);
        return;
      } catch (_) {
        await canonical.delete().catchError((_) {});
        rethrow;
      }
    }
    try {
      await finalize(outboxPath);
      return;
    } on FirebaseFunctionsException catch (error) {
      if (await mediaAlreadyCommitted()) {
        await outbox.delete().catchError((_) {});
        return;
      }
      // If a transient server-side copy fails, retry through the canonical
      // participant-only path. The Storage rules still enforce membership;
      // this avoids leaving a recorded voice stuck in the private outbox.
      const retryable = {
        'internal',
        'unknown',
        'unavailable',
        'deadline-exceeded',
      };
      if (!retryable.contains(error.code)) {
        await outbox.delete().catchError((_) {});
        rethrow;
      }
      await outbox.delete().catchError((_) {});
      final canonical = _storage.ref(canonicalPath);
      try {
        await uploadTo(canonical);
        await finalize(canonicalPath);
        return;
      } catch (_) {
        await canonical.delete().catchError((_) {});
        rethrow;
      }
    } catch (error, stackTrace) {
      if (await mediaAlreadyCommitted()) {
        await outbox.delete().catchError((_) {});
        return;
      }
      // The upload outbox is intentionally unreadable from the phone.  The
      // server is the only component allowed to turn it into a shareable
      // media URL, so never request a download URL here before this commit.
      await outbox.delete().catchError((_) {});
      Error.throwWithStackTrace(error, stackTrace);
    }
  }

  Future<WapiVoiceTranslation> translateVoiceMessage({
    required String conversationId,
    required String messageId,
    required String targetLanguage,
  }) async {
    final result = await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable(
          'translateConversationVoice',
          options: HttpsCallableOptions(timeout: const Duration(seconds: 60)),
        )
        .call<Map<String, dynamic>>({
          'conversationId': conversationId,
          'messageId': messageId,
          'targetLanguage': targetLanguage,
        });
    final translation = WapiVoiceTranslation.fromMap(result.data);
    if (translation.translation.isEmpty) {
      throw StateError('La traduction vocale reçue est vide.');
    }
    return translation;
  }

  Future<void> markViewOnceSeen({
    required String conversationId,
    required String messageId,
    required String userId,
  }) {
    return _db
        .collection('conversations')
        .doc(conversationId)
        .collection('messages')
        .doc(messageId)
        .update({'viewedBy.$userId': FieldValue.serverTimestamp()});
  }

  Future<String> uploadProfilePhoto({
    required User user,
    required File file,
    required String contentType,
  }) async {
    final ref = _storage.ref(
      'profiles/${user.uid}/avatar-${DateTime.now().millisecondsSinceEpoch}.jpg',
    );
    await ref.putFile(
      file,
      SettableMetadata(
        contentType: contentType,
        // Every avatar has a new storage path. Keeping it immutable lets
        // Android display the new photo instantly without serving an older
        // bitmap from the network cache.
        cacheControl: 'public,max-age=31536000,immutable',
      ),
    );
    final url = await ref.getDownloadURL();
    await _db.collection('users').doc(user.uid).set({
      'uid': user.uid,
      'photoUrl': url,
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
    await user.updatePhotoURL(url);
    return url;
  }

  Future<void> updateProfileName({
    required User user,
    required String displayName,
  }) async {
    final value = displayName.trim();
    if (value.length < 2 || value.length > 80) {
      throw ArgumentError('Le nom doit contenir entre 2 et 80 caractères.');
    }
    await user.updateDisplayName(value);
    await _db.collection('users').doc(user.uid).set({
      'uid': user.uid,
      'displayName': value,
      'phoneNumber': user.phoneNumber ?? '',
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
  }

  Future<void> updateProfileDetails({
    required User user,
    required String displayName,
    required String bio,
    required String occupation,
    required String city,
    required String website,
    required String statusText,
  }) async {
    final name = displayName.trim();
    final safeBio = bio.trim();
    final safeOccupation = occupation.trim();
    final safeCity = city.trim();
    final safeWebsite = website.trim();
    final safeStatus = statusText.trim();
    if (name.length < 2 || name.length > 80) {
      throw ArgumentError('Le nom doit contenir entre 2 et 80 caractères.');
    }
    if (safeBio.length > 240 ||
        safeOccupation.length > 80 ||
        safeCity.length > 80 ||
        safeWebsite.length > 180 ||
        safeStatus.length > 80) {
      throw ArgumentError('Une information de profil est trop longue.');
    }
    if (safeWebsite.isNotEmpty &&
        !RegExp(r'^https://', caseSensitive: false).hasMatch(safeWebsite)) {
      throw ArgumentError('Le site doit commencer par https://');
    }
    await user.updateDisplayName(name);
    await _db.collection('users').doc(user.uid).set({
      'uid': user.uid,
      'displayName': name,
      'phoneNumber': user.phoneNumber ?? '',
      'bio': safeBio,
      'occupation': safeOccupation,
      'city': safeCity,
      'website': safeWebsite,
      'statusText': safeStatus,
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
  }

  Future<WapiStory> publishMediaStory({
    required User user,
    required File file,
    required String mediaType,
    required String contentType,
    required String fileName,
    String caption = '',
  }) async {
    final safeName = fileName.replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_');
    final storagePath =
        'stories/${user.uid}/${DateTime.now().millisecondsSinceEpoch}-$safeName';
    final ref = _storage.ref(storagePath);
    await ref.putFile(file, SettableMetadata(contentType: contentType));
    try {
      final url = await ref.getDownloadURL();
      return await _publishStory({
        'caption': caption.trim().take(600),
        'mediaType': mediaType,
        'mediaUrl': url,
        'storagePath': storagePath,
      });
    } catch (_) {
      await ref.delete().catchError((_) {});
      rethrow;
    }
  }

  Future<String> ensureDirectConversation({
    required User user,
    required String peerId,
    required String peerName,
    required String peerPhone,
    required String peerPhotoUrl,
  }) async {
    if (peerId == user.uid) {
      throw ArgumentError(
        'Vous ne pouvez pas ouvrir une conversation avec vous-même.',
      );
    }
    final members = [user.uid, peerId]..sort();
    final id = 'direct-${members.join('-')}';
    final conversation = _db.collection('conversations').doc(id);
    final selfName = user.displayName?.trim().isNotEmpty == true
        ? user.displayName!.trim()
        : user.phoneNumber ?? 'Membre WAPI';
    final batch = _db.batch();
    batch.set(conversation, {
      'ownerId': user.uid,
      'conversationType': 'direct',
      'title': '',
      'memberIds': members,
      'members': [
        {
          'uid': user.uid,
          'displayName': selfName,
          'phoneNumber': user.phoneNumber ?? '',
          'photoUrl': user.photoURL ?? '',
        },
        {
          'uid': peerId,
          'displayName': peerName,
          'phoneNumber': peerPhone,
          'photoUrl': peerPhotoUrl,
        },
      ],
      'typingBy': <String, bool>{},
      'readBy': <String, dynamic>{},
      'contactId': peerId,
      'contactName': peerName,
      'lastMessage': '',
      'lastSenderId': '',
      'createdAt': FieldValue.serverTimestamp(),
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
    batch.set(_db.collection('users').doc(user.uid), {
      'contacts': {
        peerId: {
          'displayName': peerName,
          'phoneNumber': peerPhone,
          'photoUrl': peerPhotoUrl,
          'addedAt': FieldValue.serverTimestamp(),
        },
      },
      'updatedAt': FieldValue.serverTimestamp(),
    }, SetOptions(merge: true));
    await batch.commit();
    return id;
  }

  Future<WapiStory> publishTextStory({
    required User user,
    required String text,
  }) async {
    final value = text.trim();
    if (value.isEmpty) {
      throw ArgumentError('Le texte de la Story ne peut pas être vide.');
    }
    return _publishStory({'caption': value, 'mediaType': 'text'});
  }

  Future<WapiStory> _publishStory(Map<String, dynamic> payload) async {
    final requestId =
        'story_${DateTime.now().microsecondsSinceEpoch}_${payload['mediaType'] ?? 'text'}';
    final result = await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('publishStory')
        .call<Map<String, dynamic>>({...payload, 'clientRequestId': requestId})
        .timeout(const Duration(seconds: 25));
    return WapiStory.fromMap(result.data);
  }
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}

extension _LimitString on String {
  String take(int maxLength) =>
      length <= maxLength ? this : substring(0, maxLength);
}
