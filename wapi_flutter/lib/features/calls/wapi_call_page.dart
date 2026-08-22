import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../app/wapi_theme.dart';

/// Appel WebRTC WAPI : Firebase ne transporte que la signalisation chiffrée.
/// Le média ne passe ni par Firebase, ni par un fournisseur vidéo tiers.
class WapiCallPage extends StatefulWidget {
  const WapiCallPage.outgoing({
    super.key,
    required this.user,
    required this.peerId,
    required this.peerName,
    required this.video,
  }) : incomingCallId = null;

  const WapiCallPage.incoming({
    super.key,
    required this.user,
    required this.incomingCallId,
    required this.peerId,
    required this.peerName,
    required this.video,
  });

  final User user;
  final String peerId;
  final String peerName;
  final bool video;
  final String? incomingCallId;

  @override
  State<WapiCallPage> createState() => _WapiCallPageState();
}

class _WapiCallPageState extends State<WapiCallPage> {
  final _db = FirebaseFirestore.instance;
  final _localRenderer = RTCVideoRenderer();
  final _remoteRenderer = RTCVideoRenderer();
  final _candidateIds = <String>{};
  final _pendingCandidates = <Map<String, dynamic>>[];
  final _pendingRemoteCandidates = <Map<String, dynamic>>[];
  final _subscriptions = <StreamSubscription<dynamic>>[];
  List<Map<String, dynamic>> _iceServers = const [];

  RTCPeerConnection? _peer;
  MediaStream? _localStream;
  DocumentReference<Map<String, dynamic>>? _call;
  bool _muted = false;
  bool _speaker = true;
  bool _ended = false;
  bool _hasRemoteDescription = false;
  String _status = 'Connexion sécurisée…';

  bool get _incoming => widget.incomingCallId != null;

  @override
  void initState() {
    super.initState();
    _prepare();
  }

  Future<void> _prepare() async {
    try {
      await _localRenderer.initialize();
      await _remoteRenderer.initialize();
      _iceServers = await _loadIceServers();
      await _openLocalMedia();
      await _openPeerConnection();
      if (_incoming) {
        await _answerIncoming();
      } else {
        await _placeOutgoing();
      }
    } catch (error) {
      if (mounted) {
        setState(() => _status = 'Appel impossible : $error');
      }
    }
  }

  Future<void> _openLocalMedia() async {
    _localStream = await navigator.mediaDevices.getUserMedia({
      'audio': true,
      'video': widget.video
          ? {
              'facingMode': 'user',
              'width': 720,
              'height': 1280,
              'frameRate': 30,
            }
          : false,
    });
    _localRenderer.srcObject = _localStream;
  }

  Future<void> _openPeerConnection() async {
    final peer = await createPeerConnection({
      'sdpSemantics': 'unified-plan',
      'iceTransportPolicy': 'all',
      'iceCandidatePoolSize': 8,
      'iceServers': _iceServers,
    });
    _peer = peer;
    for (final track
        in _localStream?.getTracks() ?? const <MediaStreamTrack>[]) {
      await peer.addTrack(track, _localStream!);
    }
    peer.onTrack = (event) {
      if (event.streams.isNotEmpty) {
        _remoteRenderer.srcObject = event.streams.first;
        if (mounted) setState(() => _status = 'En appel');
      }
    };
    peer.onIceCandidate = (candidate) {
      if (candidate.candidate == null || candidate.candidate!.isEmpty) return;
      final value = <String, dynamic>{
        'candidate': candidate.candidate,
        'sdpMid': candidate.sdpMid,
        'sdpMLineIndex': candidate.sdpMLineIndex,
        'createdAt': FieldValue.serverTimestamp(),
      };
      if (_call == null) {
        _pendingCandidates.add(value);
      } else {
        _writeCandidate(value);
      }
    };
    peer.onConnectionState = (state) {
      if (!mounted || _ended) return;
      setState(() {
        _status = switch (state) {
          RTCPeerConnectionState.RTCPeerConnectionStateConnected => 'En appel',
          RTCPeerConnectionState.RTCPeerConnectionStateFailed =>
            'Connexion perdue',
          RTCPeerConnectionState.RTCPeerConnectionStateDisconnected =>
            'Reconnexion…',
          _ => _status,
        };
      });
    };
    await Helper.setSpeakerphoneOn(true);
  }

  Future<List<Map<String, dynamic>>> _loadIceServers() async {
    const fallback = [
      {'urls': 'stun:stun.l.google.com:19302'},
      {'urls': 'stun:stun1.l.google.com:19302'},
    ];
    try {
      final result = await FirebaseFunctions.instanceFor(
        region: 'europe-west1',
      ).httpsCallable('getWebRtcIceServers').call<Map<String, dynamic>>();
      final raw = result.data['iceServers'];
      if (raw is! List) return fallback;
      final servers = raw
          .whereType<Map>()
          .map((value) => Map<String, dynamic>.from(value))
          .where((value) => value['urls'] != null)
          .toList();
      return servers.isEmpty ? fallback : servers;
    } catch (_) {
      return fallback;
    }
  }

  Future<void> _placeOutgoing() async {
    final peer = _peer;
    if (peer == null) throw StateError('Moteur WebRTC indisponible.');
    final offer = await peer.createOffer({});
    await peer.setLocalDescription(offer);
    final local = await peer.getLocalDescription();
    if (local?.sdp == null || local?.type == null) {
      throw StateError('Offre WebRTC invalide.');
    }
    _call = _db.collection('calls').doc();
    await _call!.set({
      'callerId': widget.user.uid,
      'calleeId': widget.peerId,
      'callerName':
          widget.user.displayName ?? widget.user.phoneNumber ?? 'Membre WAPI',
      'calleeName': widget.peerName,
      'video': widget.video,
      'status': 'ringing',
      'offer': {'sdp': local!.sdp, 'type': local.type},
      'createdAt': FieldValue.serverTimestamp(),
      'updatedAt': FieldValue.serverTimestamp(),
    });
    await _flushPendingCandidates();
    _listenCall();
    _listenCandidates('calleeCandidates');
    if (mounted) {
      setState(() => _status = 'Appel de ${widget.peerName}…');
    }
  }

  Future<void> _answerIncoming() async {
    _call = _db.collection('calls').doc(widget.incomingCallId);
    final snapshot = await _call!.get();
    final data = snapshot.data();
    final offer = Map<String, dynamic>.from(data?['offer'] as Map? ?? const {});
    if (!snapshot.exists ||
        data?['status'] != 'ringing' ||
        offer['sdp'] is! String) {
      throw StateError('Cet appel n’est plus disponible.');
    }
    final peer = _peer;
    if (peer == null) throw StateError('Moteur WebRTC indisponible.');
    await _setRemoteDescription(
      RTCSessionDescription(
        offer['sdp'] as String,
        offer['type'] as String? ?? 'offer',
      ),
    );
    _listenCandidates('callerCandidates');
    final answer = await peer.createAnswer({});
    await peer.setLocalDescription(answer);
    final local = await peer.getLocalDescription();
    await _call!.update({
      'answer': {'sdp': local?.sdp, 'type': local?.type},
      'status': 'accepted',
      'updatedAt': FieldValue.serverTimestamp(),
    });
    await _flushPendingCandidates();
    _listenCall();
    if (mounted) {
      setState(() => _status = 'Connexion à ${widget.peerName}…');
    }
  }

  void _listenCall() {
    final call = _call;
    if (call == null) return;
    _subscriptions.add(
      call.snapshots().listen((snapshot) async {
        final data = snapshot.data();
        if (data == null || _ended) return;
        final status = data['status'] as String? ?? '';
        if (status == 'declined' || status == 'ended') {
          await _closeLocal(endRemote: false);
          return;
        }
        if (!_incoming && status == 'accepted' && data['answer'] is Map) {
          final answer = Map<String, dynamic>.from(data['answer'] as Map);
          if (answer['sdp'] is! String) return;
          final current = await _peer?.getRemoteDescription();
          if (current != null) {
            _hasRemoteDescription = true;
            await _flushPendingRemoteCandidates();
            return;
          }
          await _setRemoteDescription(
            RTCSessionDescription(
              answer['sdp'] as String,
              answer['type'] as String? ?? 'answer',
            ),
          );
          if (mounted) {
            setState(() => _status = 'Connexion à ${widget.peerName}…');
          }
        }
      }),
    );
  }

  void _listenCandidates(String collection) {
    final call = _call;
    if (call == null) return;
    _subscriptions.add(
      call.collection(collection).snapshots().listen((snapshot) {
        for (final change in snapshot.docChanges) {
          if (change.type != DocumentChangeType.added ||
              !_candidateIds.add(change.doc.id)) {
            continue;
          }
          final data = change.doc.data();
          final candidate = data?['candidate'] as String?;
          if (candidate == null) {
            continue;
          }
          _receiveRemoteCandidate(Map<String, dynamic>.from(data ?? const {}));
        }
      }),
    );
  }

  Future<void> _setRemoteDescription(RTCSessionDescription description) async {
    await _peer?.setRemoteDescription(description);
    _hasRemoteDescription = true;
    await _flushPendingRemoteCandidates();
  }

  void _receiveRemoteCandidate(Map<String, dynamic> candidate) {
    if (!_hasRemoteDescription) {
      _pendingRemoteCandidates.add(candidate);
      return;
    }
    unawaited(_addRemoteCandidate(candidate));
  }

  Future<void> _flushPendingRemoteCandidates() async {
    final pending = List<Map<String, dynamic>>.from(_pendingRemoteCandidates);
    _pendingRemoteCandidates.clear();
    for (final candidate in pending) {
      await _addRemoteCandidate(candidate);
    }
  }

  Future<void> _addRemoteCandidate(Map<String, dynamic> data) async {
    final candidate = data['candidate'] as String?;
    if (candidate == null || candidate.isEmpty) return;
    try {
      await _peer?.addCandidate(
        RTCIceCandidate(
          candidate,
          data['sdpMid'] as String?,
          (data['sdpMLineIndex'] as num?)?.toInt(),
        ),
      );
    } catch (_) {
      // A transient ICE candidate failure is retried by the peer connection.
    }
  }

  void _writeCandidate(Map<String, dynamic> candidate) {
    final collection = _incoming ? 'calleeCandidates' : 'callerCandidates';
    _call?.collection(collection).add(candidate);
  }

  Future<void> _flushPendingCandidates() async {
    final pending = List<Map<String, dynamic>>.from(_pendingCandidates);
    _pendingCandidates.clear();
    for (final candidate in pending) {
      _writeCandidate(candidate);
    }
  }

  Future<void> _toggleMute() async {
    _muted = !_muted;
    for (final track
        in _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[]) {
      track.enabled = !_muted;
    }
    if (mounted) setState(() {});
  }

  Future<void> _toggleSpeaker() async {
    _speaker = !_speaker;
    await Helper.setSpeakerphoneOn(_speaker);
    if (mounted) setState(() {});
  }

  Future<void> _switchCamera() async {
    final tracks = _localStream?.getVideoTracks() ?? const <MediaStreamTrack>[];
    if (tracks.isNotEmpty) await Helper.switchCamera(tracks.first);
  }

  Future<void> _closeLocal({required bool endRemote}) async {
    if (_ended) return;
    _ended = true;
    if (endRemote) {
      try {
        await _call?.update({
          'status': 'ended',
          'updatedAt': FieldValue.serverTimestamp(),
        });
      } catch (_) {}
    }
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    _subscriptions.clear();
    for (final track
        in _localStream?.getTracks() ?? const <MediaStreamTrack>[]) {
      track.stop();
    }
    await _localStream?.dispose();
    await _peer?.close();
    if (mounted) Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _closeLocal(endRemote: true);
    _localRenderer.dispose();
    _remoteRenderer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFF07141F),
    body: SafeArea(
      child: Stack(
        children: [
          Positioned.fill(
            child: widget.video
                ? RTCVideoView(
                    _remoteRenderer,
                    objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                  )
                : const ColoredBox(color: Color(0xFF07141F)),
          ),
          if (widget.video)
            Positioned(
              top: 16,
              right: 16,
              width: 112,
              height: 156,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: RTCVideoView(_localRenderer, mirror: true),
              ),
            ),
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (!widget.video)
                  CircleAvatar(
                    radius: 52,
                    backgroundColor: WapiColors.blue,
                    child: Text(
                      widget.peerName.substring(0, 1).toUpperCase(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 34,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                const SizedBox(height: 16),
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
                  style: TextStyle(color: Colors.white.withValues(alpha: .72)),
                ),
              ],
            ),
          ),
          Positioned(
            left: 24,
            right: 24,
            bottom: 36,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _CallAction(
                  icon: _muted ? Icons.mic_off : Icons.mic,
                  label: _muted ? 'Micro coupé' : 'Micro',
                  onTap: _toggleMute,
                ),
                _CallAction(
                  icon: _speaker ? Icons.volume_up : Icons.volume_off,
                  label: 'Haut-parleur',
                  onTap: _toggleSpeaker,
                ),
                if (widget.video)
                  _CallAction(
                    icon: Icons.cameraswitch_outlined,
                    label: 'Caméra',
                    onTap: _switchCamera,
                  ),
                _CallAction(
                  icon: Icons.call_end,
                  label: 'Raccrocher',
                  destructive: true,
                  onTap: () => _closeLocal(endRemote: true),
                ),
              ],
            ),
          ),
        ],
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
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      IconButton.filled(
        onPressed: onTap,
        style: IconButton.styleFrom(
          backgroundColor: destructive
              ? const Color(0xFFE74646)
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
