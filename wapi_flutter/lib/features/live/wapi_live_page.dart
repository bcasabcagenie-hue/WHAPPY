import 'dart:async';
import 'dart:convert';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:livekit_client/livekit_client.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../app/wapi_theme.dart';
import 'wapi_live_models.dart';

class WapiLivePage extends StatefulWidget {
  const WapiLivePage({super.key, required this.user});

  final User user;

  @override
  State<WapiLivePage> createState() => _WapiLivePageState();
}

class _WapiLivePageState extends State<WapiLivePage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  bool _opening = false;
  late Future<List<WapiLiveListing>> _lives;
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _lives = _loadLives();
    _refreshTimer = Timer.periodic(const Duration(seconds: 20), (_) {
      if (mounted && !_opening) _refreshLives();
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      titleSpacing: 8,
      title: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('En direct', style: TextStyle(fontWeight: FontWeight.w800)),
          Text(
            'Diffusions WAPI',
            style: TextStyle(fontSize: 12, color: WapiColors.muted),
          ),
        ],
      ),
      actions: [
        IconButton(
          tooltip: 'Actualiser',
          onPressed: _opening ? null : _refreshLives,
          icon: const Icon(Icons.refresh_rounded),
        ),
        Padding(
          padding: const EdgeInsets.only(right: 12),
          child: FilledButton.icon(
            onPressed: _opening ? null : _createLive,
            icon: const Icon(Icons.videocam_rounded, size: 19),
            label: const Text('Créer'),
          ),
        ),
      ],
    ),
    body: FutureBuilder<List<WapiLiveListing>>(
      future: _lives,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return _LiveState(
            icon: Icons.wifi_off_rounded,
            title: 'Connexion aux directs impossible',
            body:
                'Le service sécurisé des directs ne répond pas. Vérifiez votre réseau puis réessayez.',
            action: TextButton(
              onPressed: _refreshLives,
              child: const Text('Réessayer'),
            ),
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final lives = snapshot.data!;
        return RefreshIndicator(
          onRefresh: _refreshLives,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(child: _LiveHero(onCreate: _createLive)),
              if (lives.isEmpty)
                const SliverFillRemaining(
                  hasScrollBody: false,
                  child: _LiveState(
                    icon: Icons.live_tv_rounded,
                    title: 'Soyez le premier en direct',
                    body:
                        'Lancez une émission avec votre caméra et votre micro. Vos spectateurs rejoindront la salle en temps réel.',
                  ),
                )
              else ...[
                const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(18, 22, 18, 10),
                    child: Text(
                      'Maintenant sur WAPI',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ),
                SliverList.builder(
                  itemCount: lives.length,
                  itemBuilder: (context, index) => _LiveListTile(
                    live: lives[index],
                    currentUserId: widget.user.uid,
                    onTap: () => _join(lives[index]),
                  ),
                ),
                const SliverToBoxAdapter(child: SizedBox(height: 28)),
              ],
            ],
          ),
        );
      },
    ),
  );

  Future<List<WapiLiveListing>> _loadLives() async {
    final result = await _functions
        .httpsCallable('listVisibleLiveSessions')
        .call<Map<String, dynamic>>();
    final values = result.data['lives'] as List<dynamic>? ?? const [];
    return values
        .whereType<Map>()
        .map(
          (value) => WapiLiveListing.fromMap(Map<String, dynamic>.from(value)),
        )
        .toList(growable: false);
  }

  Future<void> _refreshLives() async {
    if (!mounted) return;
    setState(() => _lives = _loadLives());
    try {
      await _lives;
    } catch (_) {
      // FutureBuilder présente l'erreur et l'action de nouvelle tentative.
    }
  }

  Future<void> _createLive() async {
    final draft = await showModalBottomSheet<_LiveDraft>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const _CreateLiveSheet(),
    );
    if (draft == null || !mounted) return;
    setState(() => _opening = true);
    try {
      final result = await _functions
          .httpsCallable('createLiveSession')
          .call<Map<String, dynamic>>({
            'title': draft.title,
            'category': draft.category,
            'visibility': draft.visibility,
            'hostMode': draft.hostMode,
          });
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => _WapiLiveRoomPage(
            user: widget.user,
            liveId: result.data['liveId'] as String,
            title: draft.title,
            hostName:
                widget.user.displayName ?? widget.user.phoneNumber ?? 'WAPI',
            hostPhotoUrl: widget.user.photoURL ?? '',
            credentials: _LiveCredentials.fromMap(result.data),
          ),
        ),
      );
      await _refreshLives();
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  Future<void> _join(WapiLiveListing live) async {
    if (_opening) return;
    final isHost = live.hostId == widget.user.uid;
    if (!isHost && live.status != 'live') {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Ce direct n’a pas encore commencé.')),
      );
      return;
    }
    setState(() => _opening = true);
    try {
      final result = await _functions
          .httpsCallable('joinLiveSession')
          .call<Map<String, dynamic>>({'liveId': live.id});
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => _WapiLiveRoomPage(
            user: widget.user,
            liveId: live.id,
            title: live.title,
            hostName: live.hostName,
            hostPhotoUrl: live.hostPhotoUrl,
            credentials: _LiveCredentials.fromMap(result.data),
          ),
        ),
      );
      await _refreshLives();
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  void _showError(Object error) {
    final message = error is FirebaseFunctionsException
        ? error.message ?? 'Le service Live WAPI est indisponible.'
        : 'Impossible d’ouvrir le direct. Réessayez.';
    showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 22, 24, 30),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(
              Icons.info_outline_rounded,
              color: WapiColors.blue,
              size: 32,
            ),
            const SizedBox(height: 14),
            const Text(
              'Live WAPI',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            Text(message, style: const TextStyle(height: 1.4)),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Compris'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LiveHero extends StatelessWidget {
  const _LiveHero({required this.onCreate});

  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [WapiColors.blueDark, WapiColors.blue],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ),
      borderRadius: BorderRadius.circular(26),
      boxShadow: [
        BoxShadow(
          color: WapiColors.blue.withValues(alpha: .22),
          blurRadius: 24,
          offset: const Offset(0, 12),
        ),
      ],
    ),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const _LivePill(label: 'WAPI LIVE', color: Colors.white),
              const SizedBox(height: 14),
              const Text(
                'Votre scène.\nVotre communauté.',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 25,
                  height: 1.08,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 12),
              FilledButton.tonalIcon(
                onPressed: onCreate,
                icon: const Icon(Icons.video_call_rounded),
                label: const Text('Passer en direct'),
                style: FilledButton.styleFrom(
                  foregroundColor: WapiColors.blueDark,
                  backgroundColor: Colors.white,
                ),
              ),
            ],
          ),
        ),
        Container(
          width: 92,
          height: 122,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: .14),
            borderRadius: BorderRadius.circular(46),
            border: Border.all(color: Colors.white24),
          ),
          child: const Icon(
            Icons.podcasts_rounded,
            color: Colors.white,
            size: 48,
          ),
        ),
      ],
    ),
  );
}

class _LiveListTile extends StatelessWidget {
  const _LiveListTile({
    required this.live,
    required this.currentUserId,
    required this.onTap,
  });

  final WapiLiveListing live;
  final String currentUserId;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final isLive = live.status == 'live';
    final isMine = live.hostId == currentUserId;
    final photo = live.hostPhotoUrl;
    final host = live.hostName;
    return Material(
      color: Colors.white,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 13, 14, 13),
          child: Row(
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  CircleAvatar(
                    radius: 29,
                    backgroundColor: WapiColors.blueSoft,
                    backgroundImage: photo.isEmpty ? null : NetworkImage(photo),
                    child: photo.isEmpty
                        ? Text(host.characters.first.toUpperCase())
                        : null,
                  ),
                  Positioned(
                    left: -2,
                    right: -2,
                    bottom: -7,
                    child: Center(
                      child: _LivePill(
                        label: isLive ? 'LIVE' : 'PRÊT',
                        color: isLive
                            ? const Color(0xFFF0445A)
                            : WapiColors.blue,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      live.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '$host · ${live.category}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: WapiColors.muted,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.visibility_outlined,
                        size: 15,
                        color: WapiColors.muted,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '${live.viewerCount}',
                        style: const TextStyle(
                          color: WapiColors.muted,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                  const Icon(
                    Icons.chevron_right_rounded,
                    color: WapiColors.muted,
                  ),
                  if (live.aiGenerated)
                    const Text(
                      'Animé par IA',
                      style: TextStyle(fontSize: 9, color: WapiColors.blue),
                    ),
                  if (isMine)
                    const Text(
                      'Mon live',
                      style: TextStyle(fontSize: 10, color: WapiColors.blue),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LiveDraft {
  const _LiveDraft({
    required this.title,
    required this.category,
    required this.visibility,
    required this.hostMode,
  });

  final String title;
  final String category;
  final String visibility;
  final String hostMode;
}

class _CreateLiveSheet extends StatefulWidget {
  const _CreateLiveSheet();

  @override
  State<_CreateLiveSheet> createState() => _CreateLiveSheetState();
}

class _CreateLiveSheetState extends State<_CreateLiveSheet> {
  final _title = TextEditingController();
  String _category = 'Discussion';
  String _visibility = 'public';
  String _hostMode = 'personal';

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      padding: EdgeInsets.fromLTRB(22, 16, 22, 24 + bottom),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 42,
                height: 4,
                decoration: BoxDecoration(
                  color: WapiColors.line,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'Préparer le direct',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 6),
            const Text(
              'La caméra et le micro seront activés après votre confirmation.',
              style: TextStyle(color: WapiColors.muted, height: 1.35),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: _title,
              autofocus: true,
              maxLength: 120,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Titre du direct',
                hintText: 'Ex. Questions-réponses avec Cyril',
                prefixIcon: Icon(Icons.title_rounded),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _category,
              decoration: const InputDecoration(
                labelText: 'Catégorie',
                prefixIcon: Icon(Icons.category_outlined),
              ),
              items:
                  const [
                        'Discussion',
                        'Actualité',
                        'Business',
                        'Musique',
                        'Sport',
                        'Éducation',
                        'Divertissement',
                      ]
                      .map(
                        (value) =>
                            DropdownMenuItem(value: value, child: Text(value)),
                      )
                      .toList(),
              onChanged: (value) =>
                  setState(() => _category = value ?? _category),
            ),
            const SizedBox(height: 14),
            const Text(
              'Audience',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(
                  value: 'public',
                  label: Text('Public'),
                  icon: Icon(Icons.public),
                ),
                ButtonSegment(
                  value: 'contacts',
                  label: Text('Contacts'),
                  icon: Icon(Icons.people_outline),
                ),
                ButtonSegment(
                  value: 'private',
                  label: Text('Privé'),
                  icon: Icon(Icons.lock_outline),
                ),
              ],
              selected: {_visibility},
              showSelectedIcon: false,
              onSelectionChanged: (value) =>
                  setState(() => _visibility = value.first),
            ),
            const SizedBox(height: 14),
            DropdownButtonFormField<String>(
              initialValue: _hostMode,
              decoration: const InputDecoration(
                labelText: 'Diffuser en tant que',
                prefixIcon: Icon(Icons.account_circle_outlined),
              ),
              items: const [
                DropdownMenuItem(
                  value: 'personal',
                  child: Text('Profil personnel'),
                ),
                DropdownMenuItem(value: 'creator', child: Text('Créateur')),
                DropdownMenuItem(
                  value: 'business',
                  child: Text('Compte Business'),
                ),
              ],
              onChanged: (value) =>
                  setState(() => _hostMode = value ?? _hostMode),
            ),
            const SizedBox(height: 22),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: _title.text.trim().length < 3
                    ? null
                    : () => Navigator.pop(
                        context,
                        _LiveDraft(
                          title: _title.text.trim(),
                          category: _category,
                          visibility: _visibility,
                          hostMode: _hostMode,
                        ),
                      ),
                icon: const Icon(Icons.videocam_rounded),
                label: const Padding(
                  padding: EdgeInsets.symmetric(vertical: 4),
                  child: Text('Ouvrir le studio Live'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LiveCredentials {
  const _LiveCredentials({
    required this.serverUrl,
    required this.participantToken,
    required this.isHost,
  });

  factory _LiveCredentials.fromMap(Map<String, dynamic> data) =>
      _LiveCredentials(
        serverUrl: data['serverUrl'] as String? ?? '',
        participantToken: data['participantToken'] as String? ?? '',
        isHost: data['role'] == 'host',
      );

  final String serverUrl;
  final String participantToken;
  final bool isHost;
}

class _WapiLiveRoomPage extends StatefulWidget {
  const _WapiLiveRoomPage({
    required this.user,
    required this.liveId,
    required this.title,
    required this.hostName,
    required this.hostPhotoUrl,
    required this.credentials,
  });

  final User user;
  final String liveId;
  final String title;
  final String hostName;
  final String hostPhotoUrl;
  final _LiveCredentials credentials;

  @override
  State<_WapiLiveRoomPage> createState() => _WapiLiveRoomPageState();
}

class _WapiLiveRoomPageState extends State<_WapiLiveRoomPage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  final _comment = TextEditingController();
  late final Room _room;
  EventsListener<RoomEvent>? _listener;
  bool _connecting = true;
  bool _closing = false;
  bool _microphone = true;
  bool _camera = true;
  bool _speaker = true;
  CameraPosition _cameraPosition = CameraPosition.front;
  ConnectionQuality _connectionQuality = ConnectionQuality.unknown;
  String _connectionLabel = 'Connexion au Live WAPI…';
  String? _fatalError;
  int _viewerCount = 0;
  int _reactionCount = 0;
  int _reactionSequence = 0;
  final List<_FloatingReaction> _floatingReactions = [];
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>? _liveSubscription;

  bool get _isHost => widget.credentials.isHost;

  @override
  void initState() {
    super.initState();
    _room = Room(
      roomOptions: const RoomOptions(
        adaptiveStream: true,
        dynacast: true,
        defaultCameraCaptureOptions: CameraCaptureOptions(
          cameraPosition: CameraPosition.front,
          params: VideoParametersPresets.h720_169,
          maxFrameRate: 30,
        ),
      ),
    );
    _liveSubscription = FirebaseFirestore.instance
        .collection('liveSessions')
        .doc(widget.liveId)
        .snapshots()
        .listen((snapshot) {
          final data = snapshot.data();
          if (!mounted || data == null) return;
          setState(() {
            _viewerCount = (data['viewerCount'] as num?)?.toInt() ?? 0;
            _reactionCount = (data['reactionCount'] as num?)?.toInt() ?? 0;
          });
          if (!_isHost && data['status'] == 'ended' && !_closing) {
            setState(() => _fatalError = 'Ce direct est maintenant terminé.');
          }
        });
    _connect();
  }

  Future<void> _connect() async {
    try {
      if (_isHost) {
        final permissions = await [
          Permission.camera,
          Permission.microphone,
        ].request();
        if (permissions.values.any((status) => !status.isGranted)) {
          throw StateError(
            'Autorisez la caméra et le micro pour démarrer le direct.',
          );
        }
      }
      _listener = _room.createListener()
        ..on<RoomReconnectingEvent>(
          (_) => _setConnection('Reconnexion en cours…'),
        )
        ..on<RoomReconnectedEvent>((_) => _setConnection('En direct'))
        ..on<ParticipantConnectionQualityUpdatedEvent>((event) {
          if (event.participant.sid == _room.localParticipant?.sid && mounted) {
            setState(() => _connectionQuality = event.connectionQuality);
          }
        })
        ..on<DataReceivedEvent>(_handleLiveData)
        ..on<ParticipantConnectedEvent>((_) => _refreshRoom())
        ..on<ParticipantDisconnectedEvent>((_) => _refreshRoom())
        ..on<TrackSubscribedEvent>((_) => _refreshRoom())
        ..on<TrackUnsubscribedEvent>((_) => _refreshRoom())
        ..on<RoomDisconnectedEvent>((event) {
          if (!_isHost) unawaited(_setPresence(false));
          if (!_closing) {
            _setConnection('Connexion interrompue');
            if (!_isHost && mounted) {
              setState(
                () => _fatalError =
                    'La diffusion a été interrompue ou terminée par l’animateur.',
              );
            }
          }
        });
      await _room.prepareConnection(
        widget.credentials.serverUrl,
        widget.credentials.participantToken,
      );
      await _room.connect(
        widget.credentials.serverUrl,
        widget.credentials.participantToken,
      );
      await AudioManager.instance.setSpeakerOutputPreferred(true);
      if (_isHost) {
        await _room.localParticipant?.setMicrophoneEnabled(true);
        await _room.localParticipant?.setCameraEnabled(true);
        await _functions
            .httpsCallable('setLiveSessionState')
            .call<Map<String, dynamic>>({
              'liveId': widget.liveId,
              'action': 'start',
            });
      } else {
        await _setPresence(true);
      }
      if (mounted) {
        setState(() {
          _connecting = false;
          _connectionLabel = 'En direct';
        });
      }
    } catch (error) {
      try {
        await _room.disconnect();
      } catch (_) {
        // La fermeture locale est tentée même si la signalisation est coupée.
      }
      if (_isHost) {
        try {
          await _functions
              .httpsCallable('setLiveSessionState')
              .call<Map<String, dynamic>>({
                'liveId': widget.liveId,
                'action': 'end',
              });
        } catch (_) {
          // Le webhook terminera aussi la session si la salle a été créée.
        }
      }
      if (!mounted) return;
      setState(() {
        _connecting = false;
        _fatalError = error is FirebaseFunctionsException
            ? error.message
            : error.toString().replaceFirst('Bad state: ', '');
      });
    }
  }

  void _setConnection(String label) {
    if (mounted) setState(() => _connectionLabel = label);
  }

  void _refreshRoom() {
    if (mounted) setState(() {});
  }

  void _handleLiveData(DataReceivedEvent event) {
    if (event.topic != 'wapi.reaction') return;
    try {
      final value = jsonDecode(utf8.decode(event.data)) as Map<String, dynamic>;
      if (value['type'] != 'reaction' || value['senderId'] == widget.user.uid) {
        return;
      }
      _addFloatingReaction(value['reaction'] as String? ?? 'heart');
    } catch (_) {
      // Un paquet de réaction incomplet ne doit jamais interrompre la vidéo.
    }
  }

  Future<void> _setPresence(bool connected) async {
    if (_isHost) return;
    try {
      await _functions
          .httpsCallable('setLivePresence')
          .call<Map<String, dynamic>>({
            'liveId': widget.liveId,
            'action': connected ? 'connected' : 'disconnected',
          });
    } catch (_) {
      // Le webhook LiveKit réconcilie également la présence côté serveur.
    }
  }

  VideoTrack? get _activeVideo {
    if (_isHost) {
      final publications =
          _room.localParticipant?.videoTrackPublications ?? const [];
      for (final publication in publications) {
        if (publication.source == TrackSource.camera && !publication.muted) {
          return publication.track;
        }
      }
      return null;
    }
    for (final participant in _room.remoteParticipants.values) {
      for (final publication in participant.videoTrackPublications) {
        if (publication.source == TrackSource.camera &&
            publication.subscribed &&
            !publication.muted) {
          return publication.track;
        }
      }
    }
    return null;
  }

  String get _qualityLabel => switch (_connectionQuality) {
    ConnectionQuality.excellent => 'Excellent',
    ConnectionQuality.good => 'Stable',
    ConnectionQuality.poor => 'Faible',
    ConnectionQuality.lost => 'Perdu',
    ConnectionQuality.unknown => 'Mesure…',
  };

  Color get _qualityColor => switch (_connectionQuality) {
    ConnectionQuality.excellent => const Color(0xFF42D392),
    ConnectionQuality.good => const Color(0xFF62C7FF),
    ConnectionQuality.poor => const Color(0xFFFFB020),
    ConnectionQuality.lost => const Color(0xFFF0445A),
    ConnectionQuality.unknown => Colors.white60,
  };

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: false,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) _requestLeave();
    },
    child: Scaffold(
      backgroundColor: const Color(0xFF080B10),
      resizeToAvoidBottomInset: true,
      body: Stack(
        fit: StackFit.expand,
        children: [
          _VideoStage(
            track: _activeVideo,
            hostName: widget.hostName,
            hostPhotoUrl: widget.hostPhotoUrl,
          ),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Color(0xB8000000),
                  Colors.transparent,
                  Color(0xD9000000),
                ],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                stops: [0, .45, 1],
              ),
            ),
          ),
          SafeArea(
            child: Column(
              children: [
                _buildTopBar(),
                const Spacer(),
                if (_fatalError == null && !_connecting) _buildComments(),
                if (_fatalError == null && !_connecting) _buildComposer(),
                if (_fatalError == null && !_connecting) _buildControls(),
              ],
            ),
          ),
          Positioned(
            right: 18,
            bottom: 178,
            child: IgnorePointer(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: _floatingReactions
                    .map(
                      (reaction) => _FloatingReactionBubble(
                        key: ValueKey(reaction.id),
                        reaction: reaction.reaction,
                      ),
                    )
                    .toList(growable: false),
              ),
            ),
          ),
          if (_connecting)
            const Center(child: CircularProgressIndicator(color: Colors.white)),
          if (_fatalError != null)
            _LiveFatalError(message: _fatalError!, onClose: _leaveNow),
        ],
      ),
    ),
  );

  Widget _buildTopBar() => Padding(
    padding: const EdgeInsets.fromLTRB(10, 8, 12, 8),
    child: Row(
      children: [
        IconButton.filledTonal(
          onPressed: _requestLeave,
          icon: const Icon(Icons.close_rounded),
          style: IconButton.styleFrom(
            backgroundColor: Colors.black38,
            foregroundColor: Colors.white,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const _LivePill(label: 'LIVE', color: Color(0xFFF0445A)),
                  const SizedBox(width: 7),
                  Flexible(
                    child: Text(
                      widget.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 2),
              Text(
                '$_connectionLabel · $_viewerCount spectateur${_viewerCount > 1 ? 's' : ''} · $_reactionCount réaction${_reactionCount > 1 ? 's' : ''}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Colors.white70, fontSize: 11),
              ),
            ],
          ),
        ),
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            color: _qualityColor,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 5),
        Text(
          _qualityLabel,
          style: const TextStyle(color: Colors.white70, fontSize: 10),
        ),
        const SizedBox(width: 8),
        if (_isHost)
          InkWell(
            onTap: _showAudience,
            borderRadius: BorderRadius.circular(20),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
              decoration: BoxDecoration(
                color: Colors.black38,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Row(
                children: [
                  Icon(
                    Icons.admin_panel_settings_outlined,
                    size: 16,
                    color: Colors.white,
                  ),
                  SizedBox(width: 5),
                  Text(
                    'Studio',
                    style: TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ],
              ),
            ),
          )
        else
          PopupMenuButton<String>(
            color: Colors.white,
            iconColor: Colors.white,
            onSelected: (value) {
              if (value == 'report') _reportLive();
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'report',
                child: Row(
                  children: [
                    Icon(Icons.flag_outlined),
                    SizedBox(width: 10),
                    Text('Signaler ce direct'),
                  ],
                ),
              ),
            ],
          ),
      ],
    ),
  );

  Widget _buildComments() => SizedBox(
    height: 170,
    child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('liveSessions')
          .doc(widget.liveId)
          .collection('comments')
          .orderBy('createdAt', descending: true)
          .limit(30)
          .snapshots(),
      builder: (context, snapshot) {
        final comments = snapshot.data?.docs ?? const [];
        return ListView.builder(
          reverse: true,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          itemCount: comments.length,
          itemBuilder: (_, index) {
            final data = comments[index].data();
            return Align(
              alignment: Alignment.centerLeft,
              child: Container(
                margin: const EdgeInsets.only(top: 6, right: 52),
                padding: const EdgeInsets.symmetric(
                  horizontal: 11,
                  vertical: 7,
                ),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: .42),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(
                        text: '${data['authorName'] ?? 'WAPI'}  ',
                        style: const TextStyle(
                          color: Color(0xFF62C7FF),
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      TextSpan(text: data['text'] as String? ?? ''),
                    ],
                  ),
                  style: const TextStyle(color: Colors.white, height: 1.25),
                ),
              ),
            );
          },
        );
      },
    ),
  );

  Widget _buildComposer() => Padding(
    padding: const EdgeInsets.fromLTRB(12, 5, 12, 8),
    child: Row(
      children: [
        Expanded(
          child: TextField(
            controller: _comment,
            maxLength: 280,
            style: const TextStyle(color: Colors.white),
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(
              counterText: '',
              hintText: 'Écrire dans le direct…',
              hintStyle: const TextStyle(color: Colors.white60),
              filled: true,
              fillColor: Colors.black.withValues(alpha: .44),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 12,
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(24),
                borderSide: BorderSide.none,
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(24),
                borderSide: const BorderSide(color: Colors.white24),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(24),
                borderSide: const BorderSide(
                  color: WapiColors.blue,
                  width: 1.5,
                ),
              ),
            ),
            onSubmitted: (_) => _sendComment(),
          ),
        ),
        const SizedBox(width: 8),
        IconButton.filled(
          onPressed: _sendComment,
          icon: const Icon(Icons.send_rounded),
        ),
      ],
    ),
  );

  Widget _buildControls() => Padding(
    padding: const EdgeInsets.fromLTRB(12, 0, 12, 10),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: _isHost
          ? [
              _LiveControl(
                icon: _microphone ? Icons.mic_rounded : Icons.mic_off_rounded,
                label: 'Micro',
                active: _microphone,
                onTap: _toggleMicrophone,
              ),
              _LiveControl(
                icon: _camera
                    ? Icons.videocam_rounded
                    : Icons.videocam_off_rounded,
                label: 'Caméra',
                active: _camera,
                onTap: _toggleCamera,
              ),
              _LiveControl(
                icon: Icons.cameraswitch_rounded,
                label: 'Retourner',
                active: true,
                onTap: _switchCamera,
              ),
              _LiveControl(
                icon: Icons.people_alt_rounded,
                label: 'Audience',
                active: true,
                onTap: _showAudience,
              ),
              _LiveControl(
                icon: Icons.stop_rounded,
                label: 'Terminer',
                active: false,
                destructive: true,
                onTap: _requestLeave,
              ),
            ]
          : [
              _LiveControl(
                icon: _speaker
                    ? Icons.volume_up_rounded
                    : Icons.volume_off_rounded,
                label: 'Son',
                active: _speaker,
                onTap: _toggleSpeaker,
              ),
              _LiveControl(
                icon: Icons.favorite_rounded,
                label: 'Réagir',
                active: true,
                onTap: _showReactionPicker,
              ),
              _LiveControl(
                icon: Icons.logout_rounded,
                label: 'Quitter',
                active: true,
                onTap: _requestLeave,
              ),
            ],
    ),
  );

  Future<void> _sendComment() async {
    final text = _comment.text.trim();
    if (text.isEmpty) return;
    _comment.clear();
    try {
      await FirebaseFirestore.instance
          .collection('liveSessions')
          .doc(widget.liveId)
          .collection('comments')
          .add({
            'authorId': widget.user.uid,
            'authorName':
                widget.user.displayName ??
                widget.user.phoneNumber ??
                'Membre WAPI',
            'text': text,
            'createdAt': FieldValue.serverTimestamp(),
          });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Commentaire non envoyé.')),
        );
      }
    }
  }

  Future<void> _toggleMicrophone() async {
    final next = !_microphone;
    await _room.localParticipant?.setMicrophoneEnabled(next);
    if (mounted) setState(() => _microphone = next);
  }

  Future<void> _toggleCamera() async {
    final next = !_camera;
    await _room.localParticipant?.setCameraEnabled(next);
    if (mounted) setState(() => _camera = next);
  }

  Future<void> _switchCamera() async {
    final next = _cameraPosition.switched();
    final track = _room.localParticipant?.videoTrackPublications
        .where((publication) => publication.source == TrackSource.camera)
        .firstOrNull
        ?.track;
    await track?.setCameraPosition(next);
    if (mounted) setState(() => _cameraPosition = next);
  }

  Future<void> _toggleSpeaker() async {
    final next = !_speaker;
    await AudioManager.instance.setSpeakerOutputPreferred(next);
    if (mounted) setState(() => _speaker = next);
  }

  void _addFloatingReaction(String reaction) {
    if (!mounted) return;
    final item = _FloatingReaction(++_reactionSequence, reaction);
    setState(() {
      _floatingReactions.add(item);
      if (_floatingReactions.length > 6) _floatingReactions.removeAt(0);
    });
    Timer(const Duration(milliseconds: 2100), () {
      if (mounted) setState(() => _floatingReactions.remove(item));
    });
  }

  Future<void> _showReactionPicker() async {
    final reaction = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: const Color(0xFF151A22),
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 16, 22, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Réagir au direct',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 18),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: const [
                  _ReactionChoice(
                    value: 'heart',
                    glyph: '❤️',
                    label: 'J’adore',
                  ),
                  _ReactionChoice(
                    value: 'applause',
                    glyph: '👏',
                    label: 'Bravo',
                  ),
                  _ReactionChoice(value: 'fire', glyph: '🔥', label: 'Fort'),
                  _ReactionChoice(value: 'wow', glyph: '🤩', label: 'Waouh'),
                ],
              ),
            ],
          ),
        ),
      ),
    );
    if (reaction != null) await _sendReaction(reaction);
  }

  Future<void> _sendReaction(String reaction) async {
    HapticFeedback.lightImpact();
    _addFloatingReaction(reaction);
    try {
      await _functions
          .httpsCallable('sendLiveReaction')
          .call<Map<String, dynamic>>({
            'liveId': widget.liveId,
            'reaction': reaction,
          });
    } on FirebaseFunctionsException catch (error) {
      if (!mounted || error.code == 'resource-exhausted') return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? 'Réaction non envoyée.')),
      );
    }
  }

  Future<void> _showAudience() async {
    if (!_isHost) return;
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      builder: (sheetContext) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: .62,
        maxChildSize: .9,
        minChildSize: .38,
        builder: (_, controller) => Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 12, 12),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      'Audience en direct',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(sheetContext),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
            ),
            Expanded(
              child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
                stream: FirebaseFirestore.instance
                    .collection('liveSessions')
                    .doc(widget.liveId)
                    .collection('viewers')
                    .where('active', isEqualTo: true)
                    .snapshots(),
                builder: (context, snapshot) {
                  if (!snapshot.hasData) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  final viewers = snapshot.data!.docs;
                  if (viewers.isEmpty) {
                    return const _LiveState(
                      icon: Icons.people_outline_rounded,
                      title: 'Personne pour le moment',
                      body: 'Les spectateurs connectés apparaîtront ici.',
                    );
                  }
                  return ListView.separated(
                    controller: controller,
                    itemCount: viewers.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (_, index) {
                      final viewer = viewers[index];
                      final data = viewer.data();
                      final name =
                          data['displayName'] as String? ?? 'Membre WAPI';
                      final photo = data['photoUrl'] as String? ?? '';
                      return ListTile(
                        leading: CircleAvatar(
                          backgroundColor: WapiColors.blueSoft,
                          backgroundImage: photo.isEmpty
                              ? null
                              : NetworkImage(photo),
                          child: photo.isEmpty
                              ? Text(name.characters.first.toUpperCase())
                              : null,
                        ),
                        title: Text(
                          name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: const Text('Connecté maintenant'),
                        trailing: PopupMenuButton<String>(
                          onSelected: (action) => _moderateViewer(
                            targetUserId: viewer.id,
                            displayName: name,
                            action: action,
                          ),
                          itemBuilder: (_) => const [
                            PopupMenuItem(
                              value: 'remove',
                              child: Text('Expulser 10 minutes'),
                            ),
                            PopupMenuItem(
                              value: 'block',
                              child: Text('Bloquer du direct'),
                            ),
                          ],
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _moderateViewer({
    required String targetUserId,
    required String displayName,
    required String action,
  }) async {
    try {
      await _functions
          .httpsCallable('moderateLiveParticipant')
          .call<Map<String, dynamic>>({
            'liveId': widget.liveId,
            'targetUserId': targetUserId,
            'action': action,
          });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            action == 'block'
                ? '$displayName a été bloqué.'
                : '$displayName a été expulsé pour 10 minutes.',
          ),
        ),
      );
    } on FirebaseFunctionsException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? 'Modération impossible.')),
      );
    }
  }

  Future<void> _reportLive() async {
    const reasons = [
      'Usurpation ou fraude',
      'Harcèlement ou violence',
      'Contenu sexuel non consenti',
      'Discours haineux',
      'Autre contenu interdit',
    ];
    final reason = await showModalBottomSheet<String>(
      context: context,
      useSafeArea: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Signaler ce direct',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 8),
            const Text(
              'Le signalement est confidentiel et sera examiné.',
              style: TextStyle(color: WapiColors.muted),
            ),
            const SizedBox(height: 12),
            ...reasons.map(
              (value) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.flag_outlined),
                title: Text(value),
                onTap: () => Navigator.pop(context, value),
              ),
            ),
          ],
        ),
      ),
    );
    if (reason == null) return;
    try {
      await _functions
          .httpsCallable('reportLiveSession')
          .call<Map<String, dynamic>>({
            'liveId': widget.liveId,
            'reason': reason,
          });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Signalement transmis. Merci.')),
      );
    } on FirebaseFunctionsException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? 'Signalement non transmis.')),
      );
    }
  }

  Future<void> _requestLeave() async {
    if (_closing) return;
    if (_isHost && _fatalError == null) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Terminer le direct ?'),
          content: const Text(
            'La diffusion sera arrêtée pour tous les spectateurs.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Continuer'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Terminer'),
            ),
          ],
        ),
      );
      if (confirmed != true) return;
    }
    await _leaveNow();
  }

  Future<void> _leaveNow() async {
    if (_closing) return;
    _closing = true;
    try {
      if (_isHost) {
        await _functions
            .httpsCallable('setLiveSessionState')
            .call<Map<String, dynamic>>({
              'liveId': widget.liveId,
              'action': 'end',
            });
      } else {
        await _setPresence(false);
      }
    } catch (_) {
      // La salle média est quand même quittée, même si le statut réseau tarde.
    }
    await _room.disconnect();
    if (mounted) Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _comment.dispose();
    unawaited(_liveSubscription?.cancel());
    unawaited(_listener?.dispose());
    unawaited(_room.dispose());
    super.dispose();
  }
}

class _VideoStage extends StatelessWidget {
  const _VideoStage({
    required this.track,
    required this.hostName,
    required this.hostPhotoUrl,
  });

  final VideoTrack? track;
  final String hostName;
  final String hostPhotoUrl;

  @override
  Widget build(BuildContext context) {
    if (track != null) {
      return VideoTrackRenderer(
        track!,
        fit: VideoViewFit.cover,
        placeholderBuilder: (_) => const ColoredBox(color: Color(0xFF080B10)),
      );
    }
    return ColoredBox(
      color: const Color(0xFF111722),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircleAvatar(
              radius: 44,
              backgroundColor: WapiColors.blue,
              backgroundImage: hostPhotoUrl.isEmpty
                  ? null
                  : NetworkImage(hostPhotoUrl),
              child: hostPhotoUrl.isEmpty
                  ? Text(
                      hostName.characters.first.toUpperCase(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 30,
                        fontWeight: FontWeight.w900,
                      ),
                    )
                  : null,
            ),
            const SizedBox(height: 16),
            Text(
              hostName,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 5),
            const Text(
              'La vidéo arrive…',
              style: TextStyle(color: Colors.white60),
            ),
          ],
        ),
      ),
    );
  }
}

class _FloatingReaction {
  const _FloatingReaction(this.id, this.reaction);

  final int id;
  final String reaction;
}

class _FloatingReactionBubble extends StatelessWidget {
  const _FloatingReactionBubble({super.key, required this.reaction});

  final String reaction;

  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
    tween: Tween(begin: 0, end: 1),
    duration: const Duration(milliseconds: 520),
    curve: Curves.easeOutBack,
    builder: (_, value, child) => Transform.translate(
      offset: Offset(0, 20 * (1 - value)),
      child: Opacity(opacity: value.clamp(0, 1), child: child),
    ),
    child: Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: .38),
          shape: BoxShape.circle,
        ),
        child: Padding(
          padding: const EdgeInsets.all(9),
          child: Text(
            wapiLiveReactionGlyph(reaction),
            style: const TextStyle(fontSize: 25),
          ),
        ),
      ),
    ),
  );
}

class _ReactionChoice extends StatelessWidget {
  const _ReactionChoice({
    required this.value,
    required this.glyph,
    required this.label,
  });

  final String value;
  final String glyph;
  final String label;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: () => Navigator.pop(context, value),
    borderRadius: BorderRadius.circular(18),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 10),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(glyph, style: const TextStyle(fontSize: 30)),
          const SizedBox(height: 5),
          Text(
            label,
            style: const TextStyle(color: Colors.white70, fontSize: 10),
          ),
        ],
      ),
    ),
  );
}

class _LiveControl extends StatelessWidget {
  const _LiveControl({
    required this.icon,
    required this.label,
    required this.active,
    required this.onTap,
    this.destructive = false,
  });

  final IconData icon;
  final String label;
  final bool active;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(28),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: destructive
                  ? const Color(0xFFF0445A)
                  : active
                  ? Colors.white
                  : Colors.white24,
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              color: destructive
                  ? Colors.white
                  : active
                  ? const Color(0xFF111722)
                  : Colors.white,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: const TextStyle(color: Colors.white, fontSize: 10),
          ),
        ],
      ),
    ),
  );
}

class _LivePill extends StatelessWidget {
  const _LivePill({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
    decoration: BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(7),
    ),
    child: Text(
      label,
      style: TextStyle(
        color: color == Colors.white ? WapiColors.blueDark : Colors.white,
        fontSize: 10,
        fontWeight: FontWeight.w900,
        letterSpacing: .7,
      ),
    ),
  );
}

class _LiveState extends StatelessWidget {
  const _LiveState({
    required this.icon,
    required this.title,
    required this.body,
    this.action,
  });

  final IconData icon;
  final String title;
  final String body;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(34),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(18),
            decoration: const BoxDecoration(
              color: WapiColors.blueSoft,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: WapiColors.blue, size: 34),
          ),
          const SizedBox(height: 18),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(
            body,
            textAlign: TextAlign.center,
            style: const TextStyle(color: WapiColors.muted, height: 1.4),
          ),
          if (action != null) ...[const SizedBox(height: 10), action!],
        ],
      ),
    ),
  );
}

class _LiveFatalError extends StatelessWidget {
  const _LiveFatalError({required this.message, required this.onClose});

  final String message;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: const Color(0xE6080B10),
    child: Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off_rounded, color: Colors.white, size: 42),
            const SizedBox(height: 16),
            const Text(
              'Impossible d’ouvrir le direct',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white,
                fontSize: 21,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 10),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white70, height: 1.4),
            ),
            const SizedBox(height: 22),
            FilledButton(
              onPressed: onClose,
              child: const Text('Revenir aux directs'),
            ),
          ],
        ),
      ),
    ),
  );
}
