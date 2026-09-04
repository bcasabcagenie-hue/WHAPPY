import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:livekit_client/livekit_client.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../app/wapi_theme.dart';

Future<T?> openWapiCall<T>(BuildContext context, WapiCallPage page) =>
    Navigator.of(context, rootNavigator: true).push<T>(
      PageRouteBuilder<T>(
        settings: const RouteSettings(name: 'wapi-call'),
        opaque: true,
        fullscreenDialog: true,
        transitionDuration: const Duration(milliseconds: 240),
        reverseTransitionDuration: const Duration(milliseconds: 180),
        pageBuilder: (_, animation, _) => FadeTransition(
          opacity: CurvedAnimation(parent: animation, curve: Curves.easeOut),
          child: page,
        ),
        transitionsBuilder: (_, animation, _, child) => SlideTransition(
          position: Tween(begin: const Offset(0, .035), end: Offset.zero)
              .animate(
                CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
              ),
          child: child,
        ),
      ),
    );

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

class _WapiCallPageState extends State<WapiCallPage>
    with SingleTickerProviderStateMixin {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  late final Room _room;
  EventsListener<RoomEvent>? _listener;
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>? _session;
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>? _peerProfile;
  String _callId = '';
  bool _accepted = false;
  bool _connecting = false;
  bool _closing = false;
  bool _muted = false;
  bool _speaker = true;
  bool _cameraEnabled = true;
  CameraPosition _cameraPosition = CameraPosition.front;
  late final AnimationController _pulseController;
  Timer? _callTimer;
  int _callSeconds = 0;
  DateTime? _connectedAt;
  String _status = 'Préparation de l’appel…';
  String? _error;
  bool _translationBusy = false;
  bool _translationEnabled = false;
  String _translationLanguage = 'fr';
  String _livePeerName = '';
  String _livePeerPhotoUrl = '';
  bool _peerVerified = false;

  static const _translationLanguages = <String, String>{
    'fr': 'Français',
    'en': 'English',
    'ln': 'Lingála',
    'pt': 'Português',
    'es': 'Español',
    'sw': 'Kiswahili',
    'ar': 'العربية',
    'zh-CN': '中文',
  };

  bool get _incoming => widget.incomingCallId != null;
  String get _peerName => _livePeerName.trim().isNotEmpty
      ? _livePeerName.trim()
      : widget.peerName.trim().isNotEmpty
      ? widget.peerName.trim()
      : 'Contact WAPI';
  String get _peerPhotoUrl => _livePeerPhotoUrl.trim().isNotEmpty
      ? _livePeerPhotoUrl.trim()
      : widget.peerPhotoUrl.trim();

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat();
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
    _watchPeerProfile();
    if (_incoming) {
      _callId = widget.incomingCallId!;
      _status = 'Appel entrant';
      _watchCall();
    } else {
      unawaited(_startOutgoing());
    }
  }

  void _watchPeerProfile() {
    if (widget.peerId.isEmpty) return;
    _peerProfile = FirebaseFirestore.instance
        .collection('users')
        .doc(widget.peerId)
        .snapshots()
        .listen((snapshot) {
          final data = snapshot.data() ?? const <String, dynamic>{};
          final name = (data['displayName'] as String?)?.trim() ?? '';
          final photoUrl = (data['photoUrl'] as String?)?.trim() ?? '';
          final verified = data['verified'] == true;
          if (!mounted ||
              (name == _livePeerName &&
                  photoUrl == _livePeerPhotoUrl &&
                  verified == _peerVerified)) {
            return;
          }
          setState(() {
            _livePeerName = name;
            _livePeerPhotoUrl = photoUrl;
            _peerVerified = verified;
          });
        });
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
      _status = 'Appel de $_peerName…';
    });
    try {
      final result = await _functions
          .httpsCallable(
            'createDirectCallSession',
            options: HttpsCallableOptions(timeout: const Duration(seconds: 20)),
          )
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
      _status = 'Connexion à $_peerName…';
    });
    try {
      final result = await _functions
          .httpsCallable(
            'joinDirectCallSession',
            options: HttpsCallableOptions(timeout: const Duration(seconds: 20)),
          )
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
      _cameraEnabled = true;
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
            ? 'En attente de $_peerName…'
            : 'En appel';
      });
    }
  }

  void _refreshCallMedia() {
    if (!mounted || _closing) return;
    setState(() {
      _status = _room.remoteParticipants.isEmpty
          ? 'En attente de $_peerName…'
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
    final message = wapiErrorText(
      error is FirebaseFunctionsException ? error.message : error,
      fallback:
          'L’appel n’a pas pu être établi. Vérifiez votre connexion puis réessayez.',
    );
    setState(() {
      _connecting = false;
      _status = 'Appel indisponible';
      _error = message;
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
          .httpsCallable(
            'joinDirectCallSession',
            options: HttpsCallableOptions(timeout: const Duration(seconds: 20)),
          )
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
    if (!_cameraEnabled) return;
    final next = _cameraPosition == CameraPosition.front
        ? CameraPosition.back
        : CameraPosition.front;
    await _localVideo?.setCameraPosition(next);
    if (mounted) setState(() => _cameraPosition = next);
  }

  Future<void> _toggleCamera() async {
    if (!widget.video) return;
    final next = !_cameraEnabled;
    await _room.localParticipant?.setCameraEnabled(next);
    if (mounted) setState(() => _cameraEnabled = next);
  }

  Future<void> _toggleTranslation() async {
    if (_translationBusy || _callId.isEmpty || _connectedAt == null) {
      if (mounted && _connectedAt == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'La traduction devient disponible une fois l’appel connecté.',
            ),
          ),
        );
      }
      return;
    }
    var targetLanguage = _translationLanguage;
    if (!_translationEnabled) {
      final selected = await showModalBottomSheet<String>(
        context: context,
        useSafeArea: true,
        showDragHandle: true,
        builder: (sheetContext) => Padding(
          padding: const EdgeInsets.fromLTRB(18, 0, 18, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Traduction vocale WAPI',
                style: Theme.of(
                  sheetContext,
                ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 6),
              const Text(
                'Choisissez la langue à entendre. La restitution de la voix exige l’accord des deux participants.',
              ),
              const SizedBox(height: 12),
              Flexible(
                child: RadioGroup<String>(
                  groupValue: targetLanguage,
                  onChanged: (value) {
                    if (value != null) Navigator.pop(sheetContext, value);
                  },
                  child: ListView(
                    shrinkWrap: true,
                    children: _translationLanguages.entries
                        .map(
                          (language) => RadioListTile<String>(
                            value: language.key,
                            title: Text(language.value),
                          ),
                        )
                        .toList(growable: false),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
      if (selected == null || !mounted) return;
      targetLanguage = selected;
    }
    setState(() => _translationBusy = true);
    try {
      await _functions
          .httpsCallable(
            'configureDirectCallTranslation',
            options: HttpsCallableOptions(timeout: const Duration(seconds: 20)),
          )
          .call<Map<String, dynamic>>({
            'callId': _callId,
            'enabled': !_translationEnabled,
            'targetLanguage': targetLanguage,
            'voiceConsent': true,
            'preserveVoice': true,
          });
      if (!mounted) return;
      setState(() {
        _translationLanguage = targetLanguage;
        _translationEnabled = !_translationEnabled;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _translationEnabled
                ? 'Traduction vocale activée en ${_translationLanguages[targetLanguage]}.'
                : 'Traduction vocale arrêtée.',
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        final serverMessage =
            error is FirebaseFunctionsException &&
                {
                  'failed-precondition',
                  'permission-denied',
                }.contains(error.code)
            ? error.message
            : null;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              serverMessage?.trim().isNotEmpty == true
                  ? serverMessage!
                  : wapiErrorText(
                      error is FirebaseFunctionsException
                          ? error.message
                          : error,
                      fallback:
                          'La traduction vocale WAPI n’est pas disponible pour le moment.',
                    ),
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _translationBusy = false);
    }
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
            .httpsCallable(
              'closeDirectCallSession',
              options: HttpsCallableOptions(
                timeout: const Duration(seconds: 12),
              ),
            )
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
    _pulseController.dispose();
    unawaited(_session?.cancel());
    unawaited(_peerProfile?.cancel());
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
            const Positioned.fill(
              child: DecoratedBox(
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
            ),
            if (widget.video && _localVideo != null)
              Positioned(
                top: 18,
                right: 16,
                width: 112,
                height: 158,
                child: Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFF0A1720),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white54, width: 1.2),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x99000000),
                        blurRadius: 20,
                        offset: Offset(0, 8),
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(19),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        VideoTrackRenderer(
                          _localVideo!,
                          fit: VideoViewFit.cover,
                        ),
                        const Positioned(
                          left: 8,
                          bottom: 7,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: Color(0x99000000),
                              borderRadius: BorderRadius.all(
                                Radius.circular(99),
                              ),
                            ),
                            child: Padding(
                              padding: EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 3,
                              ),
                              child: Text(
                                'VOUS',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 9,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            Positioned(
              top: 14,
              left: 12,
              child: IconButton.filledTonal(
                onPressed: () => _finish(
                  action: _incoming && !_accepted ? 'decline' : 'end',
                ),
                style: IconButton.styleFrom(
                  backgroundColor: Colors.black38,
                  foregroundColor: Colors.white,
                ),
                icon: const Icon(Icons.keyboard_arrow_down_rounded),
                tooltip: 'Fermer l’appel',
              ),
            ),
            Positioned(
              top: 24,
              left: 72,
              right: widget.video ? 144 : 72,
              child: Column(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 11,
                      vertical: 5,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: .12),
                      borderRadius: BorderRadius.circular(99),
                    ),
                    child: Text(
                      widget.video ? 'APPEL VIDÉO' : 'APPEL AUDIO',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 10,
                        fontWeight: FontWeight.w900,
                        letterSpacing: .8,
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Flexible(
                        child: Text(
                          _peerName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 24,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      if (_peerVerified) ...[
                        const SizedBox(width: 6),
                        const Icon(
                          Icons.verified_rounded,
                          color: Color(0xFF59C8FF),
                          size: 21,
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    _status,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: .76),
                    ),
                  ),
                  if (_connecting) ...[
                    const SizedBox(height: 10),
                    const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    ),
                  ],
                  if (_connectedAt != null) ...[
                    const SizedBox(height: 7),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 11,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xB20B202A),
                        borderRadius: BorderRadius.circular(99),
                        border: Border.all(color: Colors.white12),
                      ),
                      child: Text(
                        '$_durationLabel  ·  $_callStartedLabel',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: .9),
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                          fontFeatures: const [FontFeature.tabularFigures()],
                        ),
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
            Positioned(
              left: 14,
              right: 14,
              bottom: 18,
              child: Container(
                padding: const EdgeInsets.fromLTRB(12, 13, 12, 11),
                decoration: BoxDecoration(
                  color: const Color(0xDC0A1B25),
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(color: Colors.white12),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x99000000),
                      blurRadius: 28,
                      offset: Offset(0, 12),
                    ),
                  ],
                ),
                child: _controls(),
              ),
            ),
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
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B3445), Color(0xFF061922), Color(0xFF071117)],
        ),
      ),
      child: Center(
        child: AnimatedBuilder(
          animation: _pulseController,
          builder: (context, child) {
            final pulse = Curves.easeOut.transform(_pulseController.value);
            return Stack(
              alignment: Alignment.center,
              clipBehavior: Clip.none,
              children: [
                Container(
                  width: 196 + pulse * 34,
                  height: 196 + pulse * 34,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: const Color(
                        0xFF56D9B1,
                      ).withValues(alpha: (_muted ? .08 : .28) * (1 - pulse)),
                      width: 2,
                    ),
                  ),
                ),
                Container(
                  width: 174,
                  height: 174,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: const LinearGradient(
                      colors: [Color(0xFF5EE7B7), Color(0xFF28A8E8)],
                    ),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x5535D5B0),
                        blurRadius: 34,
                        spreadRadius: 4,
                      ),
                    ],
                  ),
                  padding: const EdgeInsets.all(4),
                  child: ClipOval(
                    child: ColoredBox(
                      color: WapiColors.blueDark,
                      child: _peerPhotoUrl.isEmpty
                          ? Center(
                              child: Text(
                                _peerName.substring(0, 1).toUpperCase(),
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 52,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            )
                          : Image.network(
                              _peerPhotoUrl,
                              fit: BoxFit.cover,
                              gaplessPlayback: true,
                              errorBuilder: (_, _, _) => Center(
                                child: Text(
                                  _peerName.substring(0, 1).toUpperCase(),
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 52,
                                    fontWeight: FontWeight.w900,
                                  ),
                                ),
                              ),
                              loadingBuilder: (context, child, progress) =>
                                  progress == null
                                  ? child
                                  : const Center(
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white70,
                                      ),
                                    ),
                            ),
                    ),
                  ),
                ),
                if (_peerVerified)
                  const Positioned(
                    right: 10,
                    bottom: 10,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                      ),
                      child: Padding(
                        padding: EdgeInsets.all(2),
                        child: Icon(
                          Icons.verified_rounded,
                          color: WapiColors.blue,
                          size: 28,
                        ),
                      ),
                    ),
                  ),
                Positioned(
                  top: 205,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _muted
                            ? Icons.mic_off_rounded
                            : Icons.graphic_eq_rounded,
                        color: _muted
                            ? const Color(0xFFFF8B8B)
                            : const Color(0xFF65E4BC),
                        size: 18,
                      ),
                      const SizedBox(width: 7),
                      Text(
                        _muted ? 'Micro coupé' : 'Audio WAPI actif',
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
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
    return Wrap(
      alignment: WrapAlignment.spaceEvenly,
      runAlignment: WrapAlignment.center,
      spacing: 8,
      runSpacing: 10,
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
            icon: _cameraEnabled
                ? Icons.videocam_rounded
                : Icons.videocam_off_rounded,
            label: _cameraEnabled ? 'Vidéo' : 'Vidéo coupée',
            emphasized: _cameraEnabled,
            onTap: _toggleCamera,
          ),
        if (widget.video && _cameraEnabled)
          _CallAction(
            icon: Icons.cameraswitch_rounded,
            label: 'Retourner',
            onTap: _switchCamera,
          ),
        _CallAction(
          icon: _translationBusy
              ? Icons.hourglass_top_rounded
              : _translationEnabled
              ? Icons.translate_rounded
              : Icons.record_voice_over_outlined,
          label: _translationEnabled
              ? _translationLanguages[_translationLanguage] ?? 'Traduction'
              : 'Traduire',
          emphasized: _translationEnabled,
          onTap: _toggleTranslation,
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
          minimumSize: const Size.square(52),
          backgroundColor: destructive
              ? const Color(0xFFE74646)
              : emphasized
              ? const Color(0xFF0FBF8A)
              : const Color(0xFF243944),
          foregroundColor: Colors.white,
          side: BorderSide(
            color: destructive || emphasized
                ? Colors.transparent
                : Colors.white12,
          ),
        ),
        icon: Icon(icon),
      ),
      const SizedBox(height: 4),
      Text(label, style: const TextStyle(color: Colors.white, fontSize: 11)),
    ],
  );
}
