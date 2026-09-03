import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:livekit_client/livekit_client.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../app/wapi_theme.dart';

class WapiCallPage extends StatefulWidget {
  const WapiCallPage.outgoing({
    super.key,
    required this.user,
    required this.peerId,
    required this.peerName,
    required this.video,
    this.peerPhotoUrl = '',
  }) : incomingCallId = null;

  const WapiCallPage.incoming({
    super.key,
    required this.user,
    required this.incomingCallId,
    required this.peerId,
    required this.peerName,
    required this.video,
    this.peerPhotoUrl = '',
  });

  final User user;
  final String peerId;
  final String peerName;
  final String peerPhotoUrl;
  final bool video;
  final String? incomingCallId;

  @override
  State<WapiCallPage> createState() => _WapiCallPageState();
}

class _WapiCallPageState extends State<WapiCallPage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  late final Room _room;
  EventsListener<RoomEvent>? _listener;
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>? _session;
  String _callId = '';
  bool _accepted = false;
  bool _connecting = false;
  bool _closing = false;
  bool _muted = false;
  bool _speaker = true;
  CameraPosition _cameraPosition = CameraPosition.front;
  Timer? _callTimer;
  int _callSeconds = 0;
  DateTime? _connectedAt;
  String _status = 'Préparation de l’appel…';
  String? _error;

  bool get _incoming => widget.incomingCallId != null;

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
    if (_incoming) {
      _callId = widget.incomingCallId!;
      _status = 'Appel entrant';
      _watchCall();
    } else {
      unawaited(_startOutgoing());
    }
  }

  void _watchCall() {
    if (_callId.isEmpty) return;
    _session?.cancel();
    _session = FirebaseFirestore.instance
        .collection('directCallSessions')
        .doc(_callId)
        .snapshots()
        .listen((snapshot) {
          final status = snapshot.data()?['status'] as String? ?? '';
          if (!_closing && (status == 'declined' || status == 'ended')) {
            unawaited(_finish(notifyServer: false));
          }
        });
  }

  Future<void> _startOutgoing() async {
    if (_connecting || _closing) return;
    setState(() {
      _connecting = true;
      _status = 'Appel de ${widget.peerName}…';
    });
    try {
      final result = await _functions
          .httpsCallable('createDirectCallSession')
          .call<Map<String, dynamic>>({
            'calleeId': widget.peerId,
            'video': widget.video,
          });
      _callId = result.data['callId'] as String? ?? '';
      if (_callId.isEmpty) throw StateError('L’appel n’a pas pu être créé.');
      _watchCall();
      await _connect(_CallAccess.fromMap(result.data));
    } catch (error) {
      _setFailure(error);
    }
  }

  Future<void> _acceptIncoming() async {
    if (_accepted || _connecting || _closing) return;
    setState(() {
      _accepted = true;
      _connecting = true;
      _status = 'Connexion à ${widget.peerName}…';
    });
    try {
      final result = await _functions
          .httpsCallable('joinDirectCallSession')
          .call<Map<String, dynamic>>({'callId': _callId});
      await _connect(_CallAccess.fromMap(result.data));
    } catch (error) {
      _accepted = false;
      _setFailure(error);
    }
  }

  Future<void> _connect(_CallAccess access) async {
    if (access.serverUrl.isEmpty || access.token.isEmpty) {
      throw StateError('Le service d’appel ne répond pas.');
    }
    final permissions = <Permission>[Permission.microphone];
    if (widget.video) permissions.add(Permission.camera);
    final values = await permissions.request();
    if (values.values.any((value) => !value.isGranted)) {
      throw StateError('Autorisez le micro et la caméra pour cet appel.');
    }
    await _listener?.dispose();
    _listener = _room.createListener()
      ..on<ParticipantConnectedEvent>((_) => _refreshCallMedia())
      ..on<ParticipantDisconnectedEvent>((_) {
        if (_room.remoteParticipants.isEmpty) {
          _setStatus('Votre correspondant a quitté l’appel.');
        }
      })
      ..on<TrackSubscribedEvent>((_) => _refreshCallMedia())
      ..on<TrackUnsubscribedEvent>((_) => _refreshCallMedia())
      ..on<RoomReconnectingEvent>((_) => _setStatus('Reconnexion…'))
      ..on<RoomReconnectedEvent>((_) => _setStatus('En appel'))
      ..on<RoomDisconnectedEvent>((_) {
        if (!_closing) _setStatus('Connexion interrompue');
      });
    await _room
        .prepareConnection(access.serverUrl, access.token)
        .timeout(const Duration(seconds: 15));
    await _room
        .connect(access.serverUrl, access.token)
        .timeout(const Duration(seconds: 20));
    await AudioManager.instance.setSpeakerOutputPreferred(true);
    await _room.localParticipant?.setMicrophoneEnabled(true);
    if (widget.video) {
      await _room.localParticipant?.setCameraEnabled(true);
    }
    _callTimer?.cancel();
    _callSeconds = 0;
    _connectedAt = DateTime.now();
    _callTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted && !_closing) setState(() => _callSeconds++);
    });
    if (mounted) {
      setState(() {
        _connecting = false;
        _error = null;
        _status = _room.remoteParticipants.isEmpty
            ? 'En attente de ${widget.peerName}…'
            : 'En appel';
      });
    }
  }

  void _refreshCallMedia() {
    if (!mounted || _closing) return;
    setState(() {
      _status = _room.remoteParticipants.isEmpty
          ? 'En attente de ${widget.peerName}…'
          : 'En appel';
    });
  }

  void _setStatus(String value) {
    if (mounted && !_closing) setState(() => _status = value);
  }

  String get _callStartedLabel {
    final startedAt = _connectedAt;
    if (startedAt == null) return '';
    final local = startedAt.toLocal();
    return 'Aujourd’hui · ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  String get _durationLabel =>
      '${(_callSeconds ~/ 60).toString().padLeft(2, '0')}:${(_callSeconds % 60).toString().padLeft(2, '0')}';

  void _setFailure(Object error) {
    if (!mounted) return;
    final message = error is FirebaseFunctionsException
        ? error.message
        : error.toString().replaceFirst('Bad state: ', '');
    setState(() {
      _connecting = false;
      _status = 'Appel indisponible';
      _error = message == null || message.trim().isEmpty
          ? 'L’appel n’a pas pu être établi. Vérifiez votre connexion puis réessayez.'
          : message;
    });
  }

  Future<void> _retryConnection() async {
    if (_connecting || _closing) return;
    if (_incoming && !_accepted) {
      await _acceptIncoming();
      return;
    }
    if (_callId.isEmpty) {
      await _startOutgoing();
      return;
    }
    setState(() {
      _connecting = true;
      _error = null;
      _status = 'Nouvelle tentative de connexion…';
    });
    try {
      final result = await _functions
          .httpsCallable('joinDirectCallSession')
          .call<Map<String, dynamic>>({'callId': _callId});
      await _connect(_CallAccess.fromMap(result.data));
    } catch (error) {
      _setFailure(error);
    }
  }

  VideoTrack? get _remoteVideo {
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

  LocalVideoTrack? get _localVideo {
    final publications =
        _room.localParticipant?.videoTrackPublications ?? const [];
    for (final publication in publications) {
      if (publication.source == TrackSource.camera && !publication.muted) {
        return publication.track;
      }
    }
    return null;
  }

  Future<void> _toggleMute() async {
    final next = !_muted;
    await _room.localParticipant?.setMicrophoneEnabled(!next);
    if (mounted) setState(() => _muted = next);
  }

  Future<void> _toggleSpeaker() async {
    final next = !_speaker;
    await AudioManager.instance.setSpeakerOutputPreferred(next);
    if (mounted) setState(() => _speaker = next);
  }

  Future<void> _switchCamera() async {
    final next = _cameraPosition == CameraPosition.front
        ? CameraPosition.back
        : CameraPosition.front;
    await _localVideo?.setCameraPosition(next);
    if (mounted) setState(() => _cameraPosition = next);
  }

  Future<void> _finish({
    String action = 'end',
    bool notifyServer = true,
  }) async {
    if (_closing) return;
    _closing = true;
    _callTimer?.cancel();
    try {
      if (notifyServer && _callId.isNotEmpty) {
        await _functions
            .httpsCallable('closeDirectCallSession')
            .call<Map<String, dynamic>>({'callId': _callId, 'action': action});
      }
    } catch (_) {
      // Closing local media stays possible if the network drops.
    }
    await _room.disconnect();
    if (mounted) Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _callTimer?.cancel();
    unawaited(_session?.cancel());
    unawaited(_listener?.dispose());
    unawaited(_room.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: false,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) {
        unawaited(_finish(action: _incoming && !_accepted ? 'decline' : 'end'));
      }
    },
    child: Scaffold(
      backgroundColor: const Color(0xFF07141F),
      body: SafeArea(
        child: Stack(
          children: [
            Positioned.fill(child: _stage()),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    Color(0x88000000),
                    Colors.transparent,
                    Color(0xA8000000),
                  ],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
            ),
            if (widget.video && _localVideo != null)
              Positioned(
                top: 18,
                right: 16,
                width: 108,
                height: 152,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(18),
                  child: VideoTrackRenderer(
                    _localVideo!,
                    fit: VideoViewFit.cover,
                  ),
                ),
              ),
            Positioned(
              top: 34,
              left: 24,
              right: 24,
              child: Column(
                children: [
                  Text(
                    widget.peerName,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    _status,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: .76),
                    ),
                  ),
                  if (_connectedAt != null) ...[
                    const SizedBox(height: 7),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 5,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: .22),
                        borderRadius: BorderRadius.circular(99),
                      ),
                      child: Text(
                        _callStartedLabel,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: .8),
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                  if (_callSeconds > 0) ...[
                    const SizedBox(height: 7),
                    Text(
                      _durationLabel,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: .9),
                        fontWeight: FontWeight.w800,
                        fontFeatures: const [FontFeature.tabularFigures()],
                      ),
                    ),
                  ],
                ],
              ),
            ),
            if (_error != null)
              Positioned(
                left: 24,
                right: 24,
                bottom: 148,
                child: _CallError(text: _error!),
              ),
            Positioned(left: 20, right: 20, bottom: 34, child: _controls()),
          ],
        ),
      ),
    ),
  );

  Widget _stage() {
    final remote = _remoteVideo;
    if (widget.video && remote != null) {
      return VideoTrackRenderer(remote, fit: VideoViewFit.cover);
    }
    return Center(
      child: CircleAvatar(
        radius: 58,
        backgroundColor: WapiColors.blue,
        backgroundImage: widget.peerPhotoUrl.isEmpty
            ? null
            : NetworkImage(widget.peerPhotoUrl),
        child: widget.peerPhotoUrl.isEmpty
            ? Text(
                widget.peerName.characters.first.toUpperCase(),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 36,
                  fontWeight: FontWeight.w900,
                ),
              )
            : null,
      ),
    );
  }

  Widget _controls() {
    if (_incoming && !_accepted) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _CallAction(
            icon: Icons.call_end_rounded,
            label: 'Refuser',
            destructive: true,
            onTap: () => _finish(action: 'decline'),
          ),
          _CallAction(
            icon: widget.video ? Icons.videocam_rounded : Icons.call_rounded,
            label: widget.video ? 'Accepter en vidéo' : 'Accepter',
            emphasized: true,
            onTap: _acceptIncoming,
          ),
        ],
      );
    }
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _CallAction(
          icon: _muted ? Icons.mic_off_rounded : Icons.mic_rounded,
          label: _muted ? 'Micro coupé' : 'Micro',
          onTap: _toggleMute,
        ),
        _CallAction(
          icon: _speaker ? Icons.volume_up_rounded : Icons.volume_off_rounded,
          label: 'Haut-parleur',
          onTap: _toggleSpeaker,
        ),
        if (widget.video)
          _CallAction(
            icon: Icons.cameraswitch_rounded,
            label: 'Caméra',
            onTap: _switchCamera,
          ),
        if (_error != null)
          _CallAction(
            icon: Icons.refresh_rounded,
            label: 'Réessayer',
            emphasized: true,
            onTap: _retryConnection,
          ),
        _CallAction(
          icon: Icons.call_end_rounded,
          label: 'Raccrocher',
          destructive: true,
          onTap: _finish,
        ),
      ],
    );
  }
}

class _CallAccess {
  const _CallAccess({required this.serverUrl, required this.token});

  factory _CallAccess.fromMap(Map<String, dynamic> data) => _CallAccess(
    serverUrl: data['serverUrl'] as String? ?? '',
    token: data['participantToken'] as String? ?? '',
  );

  final String serverUrl;
  final String token;
}

class _CallError extends StatelessWidget {
  const _CallError({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: const Color(0xD9232A36),
      borderRadius: BorderRadius.circular(16),
    ),
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: Text(
        text,
        textAlign: TextAlign.center,
        style: const TextStyle(color: Colors.white, height: 1.3),
      ),
    ),
  );
}

class _CallAction extends StatelessWidget {
  const _CallAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.destructive = false,
    this.emphasized = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool destructive;
  final bool emphasized;

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      IconButton.filled(
        onPressed: onTap,
        style: IconButton.styleFrom(
          backgroundColor: destructive
              ? const Color(0xFFE74646)
              : emphasized
              ? const Color(0xFF0FBF8A)
              : Colors.white24,
          foregroundColor: Colors.white,
        ),
        icon: Icon(icon),
      ),
      const SizedBox(height: 4),
      Text(label, style: const TextStyle(color: Colors.white, fontSize: 11)),
    ],
  );
}
