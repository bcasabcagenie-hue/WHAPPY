import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:crypto/crypto.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_storage/firebase_storage.dart';

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
      peerId: isGroup ? '' : (peer?['uid'] as String?) ?? '',
      avatarUrl: isGroup
          ? (data['groupPhotoUrl'] as String?) ?? ''
          : (peer?['photoUrl'] as String?) ?? '',
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
    required this.mediaSizeBytes,
    required this.mediaSha256,
    required this.durationSeconds,
    required this.replyToId,
    required this.replyText,
    required this.reactions,
  });

  final String id;
  final String text;
  final String senderId;
  final DateTime? createdAt;
  final String kind;
  final String mediaUrl;
  final String mediaName;
  final int mediaSizeBytes;
  final String mediaSha256;
  final int durationSeconds;
  final String replyToId;
  final String replyText;
  final Map<String, String> reactions;

  factory WapiMessage.fromDoc(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? const <String, dynamic>{};
    final stamp = data['createdAt'];
    return WapiMessage(
      id: doc.id,
      text: (data['text'] as String?) ?? '',
      senderId: (data['senderId'] as String?) ?? '',
      createdAt: stamp is Timestamp ? stamp.toDate() : null,
      kind: (data['kind'] as String?) ?? 'text',
      mediaUrl: (data['mediaUrl'] as String?) ?? '',
      mediaName: (data['mediaName'] as String?) ?? '',
      mediaSizeBytes: (data['mediaSizeBytes'] as num?)?.toInt() ?? 0,
      mediaSha256: (data['mediaSha256'] as String?) ?? '',
      durationSeconds: (data['duration'] as num?)?.round() ?? 0,
      replyToId: (data['replyToId'] as String?) ?? '',
      replyText: (data['replyText'] as String?) ?? '',
      reactions: Map<String, String>.from(
        (data['reactions'] as Map? ?? const {}).map(
          (key, value) => MapEntry(key.toString(), value.toString()),
        ),
      ),
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
    var latest = const <WapiStory>[];
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
        .call<Map<String, dynamic>>({'storyId': storyId});
  }

  Future<List<Map<String, dynamic>>> storyViewers(String storyId) async {
    final result = await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('listStoryViewers')
        .call<Map<String, dynamic>>({'storyId': storyId});
    return (result.data['viewers'] as List? ?? const [])
        .whereType<Map>()
        .map((value) => Map<String, dynamic>.from(value))
        .toList(growable: false);
  }

  Future<void> deleteStory(String storyId) async {
    await FirebaseFunctions.instanceFor(region: 'europe-west1')
        .httpsCallable('deleteStory')
        .call<Map<String, dynamic>>({'storyId': storyId});
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
    await ref.putFile(file, SettableMetadata(contentType: 'audio/mpeg'));
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
    await FirebaseFunctions.instanceFor(
      region: 'europe-west1',
    ).httpsCallable('manageGroupMembers').call<Map<String, dynamic>>({
      'conversationId': conversationId,
      'action': action,
      'memberIds': memberIds,
    });
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
  }) async {
    if (!{'image', 'video', 'audio'}.contains(kind)) {
      throw ArgumentError('Type de média non pris en charge.');
    }
    final conversation = _db.collection('conversations').doc(conversationId);
    final message = conversation.collection('messages').doc();
    final safeName = fileName
        .replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_')
        .take(100);
    final path =
        'conversations/$conversationId/${user.uid}/${message.id}-$safeName';
    final object = _storage.ref(path);
    final mediaSizeBytes = await file.length();
    if (mediaSizeBytes <= 0) {
      throw StateError('Le fichier média est vide.');
    }
    final mediaSha256 = (await sha256.bind(file.openRead()).first).toString();
    await object.putFile(file, SettableMetadata(contentType: contentType));
    final url = await object.getDownloadURL();
    final label = switch (kind) {
      'image' => 'Photo',
      'video' => 'Vidéo',
      _ => 'Message vocal',
    };
    final messageData = <String, dynamic>{
      'text': caption.trim().isEmpty ? label : caption.trim(),
      'senderId': user.uid,
      'clientMessageId': message.id,
      'kind': kind,
      'mediaUrl': url,
      'mediaName': safeName,
      'mediaSizeBytes': mediaSizeBytes,
      'mediaSha256': mediaSha256,
      'duration': durationSeconds.clamp(0, 600),
      'createdAt': FieldValue.serverTimestamp(),
    };
    if (kind == 'video') {
      messageData['effect'] = 'pop';
      messageData['caption'] = caption.trim().take(100);
    }
    final batch = _db.batch();
    batch.set(message, messageData);
    batch.update(conversation, {
      'lastMessage': label,
      'lastSenderId': user.uid,
      'updatedAt': FieldValue.serverTimestamp(),
      'typingBy.${user.uid}': false,
      'readBy.${user.uid}': FieldValue.serverTimestamp(),
    });
    await batch.commit();
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
        .call<Map<String, dynamic>>({...payload, 'clientRequestId': requestId});
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
