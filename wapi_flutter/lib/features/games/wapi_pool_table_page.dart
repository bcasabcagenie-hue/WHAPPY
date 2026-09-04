import 'dart:async';
import 'dart:math' as math;

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:just_audio/just_audio.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../app/wapi_theme.dart';

class WapiPoolTablePage extends StatefulWidget {
  const WapiPoolTablePage({
    super.key,
    required this.user,
    required this.roomId,
  });
  final User user;
  final String roomId;

  @override
  State<WapiPoolTablePage> createState() => _WapiPoolTablePageState();
}

class _WapiPoolTablePageState extends State<WapiPoolTablePage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  double _power = 48;
  double _angle = 0;
  double _sideSpin = 0;
  double _followSpin = 0;
  String _cueStyle = 'maple';
  String _tableTheme = 'competitionBlue';
  bool _sending = false;
  Timer? _replayTimer;
  Timer? _localStrokeTimer;
  Timer? _remoteStrokeTimer;
  Timer? _pocketNoticeTimer;
  List<_PoolBall>? _replayBalls;
  final ValueNotifier<List<_PoolBall>?> _replayBallsNotifier = ValueNotifier(
    null,
  );
  List<_PoolBall> _lastKnownBalls = const [];
  List<_PoolBall> _replayAuthoritative = const [];
  bool _physicsActive = false;
  bool _optimisticShot = false;
  bool _canShoot = false;
  bool _resultDismissed = false;
  int _poolTokens = 1000;
  Set<String> _ownedCues = {'maple'};
  bool _receivedInitialRoomState = false;
  bool _soundEnabled = true;
  int _lastShotRevision = -1;
  int _replayTicks = 0;
  final _poolStageKey = GlobalKey<_WapiPool3DStageState>();
  final _poolAudio = _PoolAudio();
  DateTime? _lastCollisionSound;
  DateTime? _lastRailSound;
  String _pocketNotice = '';

  @override
  void initState() {
    super.initState();
    unawaited(
      SystemChrome.setPreferredOrientations(const [
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]),
    );
    unawaited(
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky),
    );
    unawaited(_poolAudio.preload());
    unawaited(_loadEquipment());
  }

  bool get _isFounder =>
      (widget.user.phoneNumber ?? '').replaceAll(RegExp(r'\D'), '') ==
      '242065465808';

  String get _equipmentKey => 'wapi_pool_${widget.user.uid}';

  _CueOption get _selectedCue =>
      _cueOptions.firstWhere((cue) => cue.id == _cueStyle);

  Future<void> _loadEquipment() async {
    final preferences = await SharedPreferences.getInstance();
    final storedCue = preferences.getString('${_equipmentKey}_cue');
    final storedTheme = preferences.getString('${_equipmentKey}_theme');
    final owned = preferences.getStringList('${_equipmentKey}_owned');
    final soundEnabled = preferences.getBool('${_equipmentKey}_sound') ?? true;
    if (!mounted) return;
    setState(() {
      if (_cueOptions.any((cue) => cue.id == storedCue)) {
        _cueStyle = storedCue!;
      }
      if (_tableThemes.any((theme) => theme.id == storedTheme)) {
        _tableTheme = storedTheme!;
      }
      _poolTokens = preferences.getInt('${_equipmentKey}_tokens') ?? 1000;
      _ownedCues = _isFounder
          ? _cueOptions.map((cue) => cue.id).toSet()
          : {...?owned, 'maple'};
      if (!_ownedCues.contains(_cueStyle)) _cueStyle = 'maple';
      _soundEnabled = soundEnabled;
    });
    _poolAudio.setEnabled(soundEnabled);
  }

  Future<void> _saveEquipment() async {
    final preferences = await SharedPreferences.getInstance();
    await Future.wait([
      preferences.setString('${_equipmentKey}_cue', _cueStyle),
      preferences.setString('${_equipmentKey}_theme', _tableTheme),
      preferences.setStringList(
        '${_equipmentKey}_owned',
        _ownedCues.toList(growable: false),
      ),
      preferences.setInt('${_equipmentKey}_tokens', _poolTokens),
      preferences.setBool('${_equipmentKey}_sound', _soundEnabled),
    ]);
  }

  void _toggleSound() {
    final enabled = !_soundEnabled;
    setState(() => _soundEnabled = enabled);
    _poolAudio.setEnabled(enabled);
    unawaited(_saveEquipment());
    if (enabled) _previewPoolSound();
  }

  void _previewPoolSound() {
    _playPoolSound('cue', .55);
    Future<void>.delayed(
      const Duration(milliseconds: 130),
      () => _playPoolSound('collision', .65),
    );
  }

  void _playPoolSound(String kind, double intensity) {
    if (!_soundEnabled) return;
    unawaited(() async {
      final nativePlayed =
          await (_poolStageKey.currentState?.playSound(kind, intensity) ??
              Future<bool>.value(false));
      if (nativePlayed) return;
      switch (kind) {
        case 'cue':
          _poolAudio.cue(intensity * 100);
          break;
        case 'rail':
          _poolAudio.rail(intensity);
          break;
        case 'pocket':
          _poolAudio.pocket();
          break;
        default:
          _poolAudio.collision(intensity);
      }
    }());
  }

  Future<void> _showEquipmentWorkshop() async {
    var cueStyle = _cueStyle;
    var tableTheme = _tableTheme;
    var tokens = _poolTokens;
    var owned = _isFounder
        ? _cueOptions.map((cue) => cue.id).toSet()
        : {..._ownedCues};
    final selection = await showModalBottomSheet<_PoolEquipmentSelection>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => StatefulBuilder(
        builder: (sheetContext, updateSheet) => _PoolEquipmentWorkshop(
          cues: _cueOptions,
          themes: _tableThemes,
          cueStyle: cueStyle,
          tableTheme: tableTheme,
          tokens: tokens,
          founder: _isFounder,
          owned: owned,
          onCue: (cue) {
            if (!owned.contains(cue.id)) {
              if (tokens < cue.priceTokens) {
                ScaffoldMessenger.of(sheetContext).showSnackBar(
                  SnackBar(
                    content: Text('Jetons insuffisants pour ${cue.name}.'),
                  ),
                );
                return;
              }
              tokens -= cue.priceTokens;
              owned = {...owned, cue.id};
            }
            updateSheet(() => cueStyle = cue.id);
          },
          onTheme: (theme) => updateSheet(() => tableTheme = theme),
          onApply: () => Navigator.of(sheetContext).pop(
            _PoolEquipmentSelection(
              cueStyle: cueStyle,
              tableTheme: tableTheme,
              tokens: tokens,
              owned: owned,
            ),
          ),
        ),
      ),
    );
    if (selection == null || !mounted) return;
    setState(() {
      _cueStyle = selection.cueStyle;
      _tableTheme = selection.tableTheme;
      _poolTokens = selection.tokens;
      _ownedCues = selection.owned;
    });
    await _saveEquipment();
  }

  Future<void> _call(String name, Map<String, dynamic> data) async {
    await _functions
        .httpsCallable(name)
        .call<Map<String, dynamic>>(data)
        .timeout(const Duration(seconds: 20));
  }

  void _message(String text, {bool error = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: error
            ? const Color(0xFFB42318)
            : const Color(0xFF087D62),
        content: Text(text),
      ),
    );
  }

  Future<void> _leave() async {
    final leave = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Quitter la table ?'),
        content: const Text('La partie sera terminée pour vous.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Rester'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Quitter'),
          ),
        ],
      ),
    );
    if (leave != true) return;
    try {
      await _call('leavePoolMatch', {'roomId': widget.roomId});
      if (mounted) Navigator.pop(context);
    } catch (error) {
      _message(
        wapiErrorText(
          error is FirebaseFunctionsException ? error.message : error,
          fallback: 'Impossible de quitter la table. Réessayez.',
        ),
        error: true,
      );
    }
  }

  Future<void> _placeCueBall(Offset position) async {
    if (_sending) return;
    setState(() => _sending = true);
    try {
      await _call('placePoolCueBall', {
        'roomId': widget.roomId,
        'x': position.dx.clamp(.09, .91),
        'y': position.dy.clamp(.12, .88),
      });
      _message('Bille blanche placée.');
    } catch (error) {
      _message(
        wapiErrorText(
          error is FirebaseFunctionsException ? error.message : error,
          fallback: 'Cette position est impossible.',
        ),
        error: true,
      );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _shoot({double? angle, double? power}) async {
    if (_sending || _physicsActive) return;
    final shotAngle = angle ?? _angle;
    final selectedCue = _selectedCue;
    final basePower = (power ?? _power).clamp(10, 100).toDouble();
    final shotPower = (basePower * selectedCue.powerMultiplier)
        .clamp(10, 100)
        .toDouble();
    final spinMultiplier = 1 + selectedCue.spinBonus * .035;
    final shotSideSpin = (_sideSpin * spinMultiplier)
        .clamp(-1.0, 1.0)
        .toDouble();
    final shotFollowSpin = (_followSpin * spinMultiplier)
        .clamp(-1.0, 1.0)
        .toDouble();
    setState(() {
      _angle = shotAngle;
      _power = basePower;
    });
    // Animate the backswing immediately, then move the balls exactly when the
    // cue tip reaches the white ball. Network validation runs in parallel.
    unawaited(HapticFeedback.mediumImpact());
    unawaited(_poolStageKey.currentState?.stroke() ?? Future<void>.value());
    if (_lastKnownBalls.length == 16) {
      _optimisticShot = true;
      _physicsActive = true;
      _replayBalls = _lastKnownBalls;
      _replayAuthoritative = _lastKnownBalls;
      _replayBallsNotifier.value = _lastKnownBalls;
      _localStrokeTimer?.cancel();
      _localStrokeTimer = Timer(const Duration(milliseconds: 138), () {
        if (!mounted) return;
        _playPoolSound('cue', shotPower / 100);
        _startReplay(
          _lastKnownBalls,
          _replayAuthoritative,
          angle: shotAngle,
          power: shotPower,
          sideSpin: shotSideSpin,
          followSpin: shotFollowSpin,
        );
      });
    } else {
      _playPoolSound('cue', shotPower / 100);
    }
    setState(() => _sending = true);
    try {
      await _call('submitPoolShot', {
        'roomId': widget.roomId,
        'angle': shotAngle,
        'power': shotPower,
        'sideSpin': shotSideSpin,
        'followSpin': shotFollowSpin,
      });
    } catch (error) {
      _localStrokeTimer?.cancel();
      _replayTimer?.cancel();
      if (mounted) {
        setState(() {
          _optimisticShot = false;
          _physicsActive = false;
          _replayBalls = null;
          _replayBallsNotifier.value = null;
        });
      }
      _message(
        wapiErrorText(
          error is FirebaseFunctionsException ? error.message : error,
          fallback: 'Le tir n’a pas pu partir. Réessayez.',
        ),
        error: true,
      );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _pullCue(Offset start, Offset end, double power) {
    if (_sending || !_canShoot) return;
    final dx = (start.dx - end.dx) * _PoolPhysics.width;
    final dy = (start.dy - end.dy) * _PoolPhysics.height;
    if (math.sqrt(dx * dx + dy * dy) < .045) return;
    _shoot(angle: math.atan2(dy, dx), power: power);
  }

  static const _cueOptions = <_CueOption>[
    _CueOption(
      id: 'maple',
      name: 'Érable Atelier',
      tier: 'Classique',
      detail: 'Équilibrée · contrôle régulier',
      priceTokens: 0,
      powerMultiplier: 1,
      aimBonus: 0,
      spinBonus: 0,
      control: 4,
    ),
    _CueOption(
      id: 'walnut',
      name: 'Noyer Signature',
      tier: 'Précision',
      detail: 'Précision · poignée cuir',
      priceTokens: 250,
      powerMultiplier: 1.04,
      aimBonus: 2,
      spinBonus: 1,
      control: 5,
    ),
    _CueOption(
      id: 'carbon',
      name: 'Carbone Vector',
      tier: 'Performance',
      detail: 'Réactive · finition compétition',
      priceTokens: 700,
      powerMultiplier: 1.07,
      aimBonus: 3,
      spinBonus: 3,
      control: 6,
    ),
    _CueOption(
      id: 'obsidian',
      name: 'Obsidienne WAPI',
      tier: 'Fondateur',
      detail: 'Finition sombre · série fondateur',
      priceTokens: 1500,
      powerMultiplier: 1.09,
      aimBonus: 4,
      spinBonus: 4,
      control: 7,
    ),
  ];

  static const _tableThemes = <_PoolTableTheme>[
    _PoolTableTheme('competitionBlue', 'Bleu compétition', Color(0xFF0968B7)),
    _PoolTableTheme('navy', 'Bleu nuit', Color(0xFF07306E)),
    _PoolTableTheme('emerald', 'Vert tournoi', Color(0xFF08764F)),
  ];

  @override
  void dispose() {
    _replayTimer?.cancel();
    _localStrokeTimer?.cancel();
    _remoteStrokeTimer?.cancel();
    _pocketNoticeTimer?.cancel();
    _replayBallsNotifier.dispose();
    _poolAudio.dispose();
    unawaited(SystemChrome.setPreferredOrientations(DeviceOrientation.values));
    unawaited(SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge));
    super.dispose();
  }

  void _observeShot(
    int revision,
    List<_PoolBall> startBalls,
    List<_PoolBall> authoritativeBalls,
    double angle,
    double power,
    double sideSpin,
    double followSpin,
    String shotBy,
  ) {
    if (!_receivedInitialRoomState) {
      _receivedInitialRoomState = true;
      _lastShotRevision = revision;
      return;
    }
    if (revision <= _lastShotRevision || startBalls.length != 16) return;
    _lastShotRevision = revision;
    if (_physicsActive && _optimisticShot) {
      _replayAuthoritative = authoritativeBalls;
      _optimisticShot = false;
      _announcePocketed(startBalls, authoritativeBalls);
      return;
    }
    _announcePocketed(startBalls, authoritativeBalls);
    // Keep the rack at its pre-shot position while the opponent's cue moves.
    // Previously the authoritative final board was painted for one frame,
    // which looked like every IA ball had teleported before the replay began.
    if (shotBy.isNotEmpty && shotBy != widget.user.uid) {
      _remoteStrokeTimer?.cancel();
      _replayBalls = startBalls;
      _replayBallsNotifier.value = startBalls;
      _replayAuthoritative = authoritativeBalls;
      _physicsActive = true;
      if (mounted) setState(() {});
      unawaited(_poolStageKey.currentState?.stroke() ?? Future<void>.value());
      _remoteStrokeTimer = Timer(
        Duration(milliseconds: shotBy == 'wapi-pool-ai' ? 560 : 230),
        () {
          if (!mounted) return;
          _playPoolSound('cue', power / 100);
          _startReplay(
            startBalls,
            authoritativeBalls,
            angle: angle,
            power: power,
            sideSpin: sideSpin,
            followSpin: followSpin,
          );
        },
      );
      return;
    }
    _startReplay(
      startBalls,
      authoritativeBalls,
      angle: angle,
      power: power,
      sideSpin: sideSpin,
      followSpin: followSpin,
    );
  }

  void _announcePocketed(List<_PoolBall> start, List<_PoolBall> authoritative) {
    final before = {for (final ball in start) ball.id: ball.pocketed};
    final ids = authoritative
        .where(
          (ball) => ball.id > 0 && ball.pocketed && before[ball.id] != true,
        )
        .map((ball) => ball.id)
        .toList(growable: false);
    if (ids.isEmpty || !mounted) return;
    final label = ids.length == 1
        ? 'BILLE ${ids.first} EMPOCHÉE'
        : '${ids.length} BILLES EMPOCHÉES';
    _pocketNoticeTimer?.cancel();
    setState(() => _pocketNotice = label);
    _pocketNoticeTimer = Timer(const Duration(milliseconds: 1900), () {
      if (mounted) setState(() => _pocketNotice = '');
    });
  }

  void _startReplay(
    List<_PoolBall> start,
    List<_PoolBall> authoritative, {
    required double angle,
    required double power,
    required double sideSpin,
    required double followSpin,
  }) {
    _replayTimer?.cancel();
    _replayAuthoritative = authoritative;
    final speed = 3.4 + power / 100 * 7.4;
    _replayBalls = start
        .map(
          (ball) => ball.id == 0
              ? ball.copyWith(
                  vx: math.cos(angle) * speed / _PoolPhysics.width,
                  vy: math.sin(angle) * speed / _PoolPhysics.height,
                  sideSpin: sideSpin,
                  followSpin: followSpin,
                )
              : ball.copyWith(vx: 0, vy: 0, sideSpin: 0, followSpin: 0),
        )
        .toList(growable: false);
    _replayBallsNotifier.value = _replayBalls;
    _physicsActive = true;
    _replayTicks = 0;
    if (mounted) setState(() {});
    _replayTimer = Timer.periodic(const Duration(milliseconds: 16), (timer) {
      final current = _replayBalls;
      if (!mounted || current == null) {
        timer.cancel();
        return;
      }
      final frame = _PoolPhysics.advance(current, .018);
      _playFrameSounds(current, frame);
      _replayTicks += 1;
      if (!_PoolPhysics.isMoving(frame) || _replayTicks >= 1050) {
        timer.cancel();
        setState(() {
          _replayBalls = null;
          _replayBallsNotifier.value = null;
          _lastKnownBalls = _replayAuthoritative;
          _physicsActive = false;
          _optimisticShot = false;
        });
        return;
      }
      // At 60 fps only the table is repainted. Rebuilding the profiles,
      // score and controls every frame caused avoidable input latency.
      _replayBalls = frame;
      _replayBallsNotifier.value = frame;
    });
  }

  void _playFrameSounds(List<_PoolBall> before, List<_PoolBall> after) {
    final now = DateTime.now();
    final newlyPocketed = after.any(
      (ball) =>
          ball.pocketed &&
          !before.any(
            (previous) => previous.id == ball.id && previous.pocketed,
          ),
    );
    if (newlyPocketed) {
      _playPoolSound('pocket', .9);
      return;
    }
    final collisionIntensity = _PoolPhysics.ballContactIntensity(before);
    if (collisionIntensity > 0 &&
        (_lastCollisionSound == null ||
            now.difference(_lastCollisionSound!).inMilliseconds > 78)) {
      _lastCollisionSound = now;
      _playPoolSound('collision', collisionIntensity);
    }
    final railIntensity = _PoolPhysics.railContactIntensity(before, after);
    if (railIntensity > 0 &&
        (_lastRailSound == null ||
            now.difference(_lastRailSound!).inMilliseconds > 96)) {
      _lastRailSound = now;
      _playPoolSound('rail', railIntensity);
    }
  }

  @override
  Widget build(BuildContext context) {
    final room = FirebaseFirestore.instance
        .collection('gameRooms')
        .doc(widget.roomId);
    return StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: room.snapshots(),
      builder: (context, snapshot) {
        final data = snapshot.data?.data();
        if (data == null) {
          return Scaffold(
            appBar: AppBar(title: const Text('Wapi Pool')),
            body: const Center(child: CircularProgressIndicator()),
          );
        }
        final ids = _strings(data['playerIds']);
        final profiles = _maps(data['playerProfiles']);
        final groups = _stringMap(data['groups']);
        final authoritativeBalls = _balls(data['balls']);
        final shotStartBalls = _balls(data['shotStartBalls']);
        final shotRevision = (data['shotRevision'] as num?)?.toInt() ?? 0;
        final shotAngle = (data['shotAngle'] as num?)?.toDouble() ?? 0;
        final shotPower = (data['shotPower'] as num?)?.toDouble() ?? 48;
        final shotSideSpin = (data['shotSideSpin'] as num?)?.toDouble() ?? 0;
        final shotFollowSpin =
            (data['shotFollowSpin'] as num?)?.toDouble() ?? 0;
        final shotBy = _text(data['shotBy']);
        final incomingShot =
            _receivedInitialRoomState &&
            shotRevision > _lastShotRevision &&
            shotStartBalls.length == 16;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            _observeShot(
              shotRevision,
              shotStartBalls,
              authoritativeBalls,
              shotAngle,
              shotPower,
              shotSideSpin,
              shotFollowSpin,
              shotBy,
            );
          }
        });
        // A new server snapshot contains the final board.  Render the saved
        // pre-shot rack until _observeShot starts its local 60 fps replay.
        final balls =
            _replayBalls ??
            (incomingShot ? shotStartBalls : authoritativeBalls);
        final status = _text(data['status'], 'waiting');
        final turnUid = _text(data['turnUid']);
        final ballInHandUid = _text(data['ballInHandUid']);
        final active = status == 'playing';
        final myTurn = active && turnUid == widget.user.uid;
        final aiAiming =
            active &&
            turnUid == 'wapi-pool-ai' &&
            _text(data['aiState']) == 'aiming';
        final tableAngle = aiAiming
            ? (data['aiAimAngle'] as num?)?.toDouble() ?? _angle
            : _angle;
        final tablePower = aiAiming
            ? (data['aiAimPower'] as num?)?.toDouble() ?? _power
            : _power;
        final tableSideSpin = aiAiming
            ? (data['aiAimSideSpin'] as num?)?.toDouble() ?? 0
            : _sideSpin;
        final tableFollowSpin = aiAiming
            ? (data['aiAimFollowSpin'] as num?)?.toDouble() ?? 0
            : _followSpin;
        final ballInHand = myTurn && ballInHandUid == widget.user.uid;
        final winnerUid = _text(data['winnerUid']);
        final winner = _player(profiles, winnerUid, groups, balls);
        final bestRuns = data['bestRuns'] is Map
            ? Map<String, dynamic>.from(data['bestRuns'] as Map)
            : const <String, dynamic>{};
        final winnerRun = (bestRuns[winnerUid] as num?)?.toInt() ?? 0;
        if (!_physicsActive) _lastKnownBalls = authoritativeBalls;
        _canShoot = myTurn && !ballInHand && !_physicsActive;
        return Scaffold(
          backgroundColor: const Color(0xFF031610),
          appBar: AppBar(
            backgroundColor: const Color(0xFF031610),
            foregroundColor: Colors.white,
            toolbarHeight: 46,
            titleSpacing: 4,
            title: const Text(
              'WAPI POOL',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w900,
                letterSpacing: .6,
              ),
            ),
            actions: [
              IconButton(
                onPressed: _toggleSound,
                icon: Icon(
                  _soundEnabled
                      ? Icons.volume_up_rounded
                      : Icons.volume_off_rounded,
                ),
                tooltip: _soundEnabled ? 'Couper les sons' : 'Activer les sons',
              ),
              IconButton(
                onPressed: _showEquipmentWorkshop,
                icon: const Icon(Icons.tune_rounded),
                tooltip: 'Atelier des queues et tapis',
              ),
              IconButton(
                onPressed: _sending ? null : _leave,
                icon: const Icon(Icons.close_rounded),
                tooltip: 'Quitter la partie',
              ),
            ],
          ),
          body: SafeArea(
            top: false,
            child: Stack(
              children: [
                Column(
                  children: [
                    _PlayersBar(
                      left: _player(
                        profiles,
                        ids.isEmpty ? '' : ids.first,
                        groups,
                        balls,
                      ),
                      right: _player(
                        profiles,
                        ids.length < 2 ? '' : ids[1],
                        groups,
                        balls,
                      ),
                      activeUid: turnUid,
                      deadlineMs:
                          (data['turnDeadlineMs'] as num?)?.toInt() ?? 0,
                    ),
                    Expanded(
                      child: Center(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: AspectRatio(
                            aspectRatio: 2,
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                ValueListenableBuilder<List<_PoolBall>?>(
                                  valueListenable: _replayBallsNotifier,
                                  builder: (context, replay, _) =>
                                      _WapiPool3DStage(
                                        key: _poolStageKey,
                                        balls: replay ?? balls,
                                        angle: tableAngle,
                                        power: tablePower,
                                        sideSpin: tableSideSpin,
                                        followSpin: tableFollowSpin,
                                        aimBonus: aiAiming
                                            ? 3
                                            : _selectedCue.aimBonus,
                                        moving: _physicsActive,
                                        showAim:
                                            (myTurn || aiAiming) &&
                                            !ballInHand &&
                                            !_physicsActive,
                                        cueInHand: ballInHand,
                                        cueStyle: _cueStyle,
                                        tableTheme: _tableTheme,
                                        onTablePoint: (position, released) {
                                          if (ballInHand) {
                                            if (released) {
                                              _placeCueBall(position);
                                            }
                                            return;
                                          }
                                          if (!myTurn) return;
                                          final cue = _cue(balls);
                                          setState(() {
                                            _angle = math.atan2(
                                              (position.dy - cue.dy) *
                                                  _PoolPhysics.height,
                                              (position.dx - cue.dx) *
                                                  _PoolPhysics.width,
                                            );
                                          });
                                        },
                                        onCuePull: _pullCue,
                                      ),
                                ),
                                if (aiAiming)
                                  const Positioned(
                                    top: 14,
                                    left: 0,
                                    right: 0,
                                    child: Center(child: _PoolAiAimIndicator()),
                                  ),
                                if (_pocketNotice.isNotEmpty)
                                  Positioned(
                                    top: 14,
                                    left: 76,
                                    right: 76,
                                    child: Center(
                                      child: _PoolPocketNotice(
                                        label: _pocketNotice,
                                      ),
                                    ),
                                  ),
                                if (myTurn && !ballInHand)
                                  Positioned(
                                    left: 8,
                                    top: 18,
                                    bottom: 18,
                                    child: _PoolPowerRail(
                                      value: _power,
                                      enabled: !_sending && !_physicsActive,
                                      onChanged: (value) =>
                                          setState(() => _power = value),
                                      onRelease: () => _shoot(),
                                    ),
                                  ),
                                if (myTurn && !ballInHand && !_physicsActive)
                                  Positioned(
                                    right: 10,
                                    bottom: 10,
                                    child: _CueEnglishPad(
                                      sideSpin: _sideSpin,
                                      followSpin: _followSpin,
                                      onChanged: (side, follow) => setState(() {
                                        _sideSpin = side;
                                        _followSpin = follow;
                                      }),
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                    _PoolMatchHud(
                      status: status,
                      myTurn: myTurn,
                      ballInHand: ballInHand,
                      power: _power,
                    ),
                  ],
                ),
                if (status == 'finished' &&
                    winnerUid.isNotEmpty &&
                    !_resultDismissed)
                  Positioned.fill(
                    child: _PoolVictoryOverlay(
                      winner: winner,
                      currentUserWon: winnerUid == widget.user.uid,
                      bestRun: winnerRun,
                      onDismiss: () => setState(() => _resultDismissed = true),
                      onExit: () => Navigator.of(context).pop(),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Map<String, dynamic> _player(
    Map<String, Map<String, dynamic>> profiles,
    String uid,
    Map<String, String> groups,
    List<_PoolBall> balls,
  ) {
    final value = profiles[uid] ?? const <String, dynamic>{};
    final group = _text(groups[uid], 'open');
    final remaining = balls
        .where((ball) => !ball.pocketed && _isPoolGroupBall(ball.id, group))
        .map((ball) => ball.id)
        .toList();
    return {
      'uid': uid,
      'name': _text(
        value['displayName'],
        uid.isEmpty ? 'En attente' : 'Joueur WAPI',
      ),
      'photoUrl': _text(value['photoUrl']),
      'points': (value['points'] as num?)?.toInt() ?? 0,
      'victories': (value['victories'] as num?)?.toInt() ?? 0,
      'defeats': (value['defeats'] as num?)?.toInt() ?? 0,
      'group': group,
      'remaining': remaining,
    };
  }
}

class _PoolAiAimIndicator extends StatelessWidget {
  const _PoolAiAimIndicator();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xE6122028),
        borderRadius: BorderRadius.circular(99),
        border: Border.all(
          color: const Color(0xFF35D6FF).withValues(alpha: .72),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x88000000),
            blurRadius: 12,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: const Padding(
        padding: EdgeInsets.symmetric(horizontal: 13, vertical: 7),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 13,
              height: 13,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Color(0xFF35D6FF),
              ),
            ),
            SizedBox(width: 8),
            Text(
              'WAPI IA vise…',
              style: TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PoolPocketNotice extends StatelessWidget {
  const _PoolPocketNotice({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xED07131C),
        borderRadius: BorderRadius.circular(99),
        border: Border.all(color: const Color(0xFFFFD45F), width: 1.4),
        boxShadow: const [
          BoxShadow(
            color: Color(0x88000000),
            blurRadius: 14,
            offset: Offset(0, 5),
          ),
        ],
      ),
      child: Text(
        label,
        textAlign: TextAlign.center,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 12,
          fontWeight: FontWeight.w900,
          letterSpacing: .7,
        ),
      ),
    ),
  );
}

class _PoolMatchHud extends StatelessWidget {
  const _PoolMatchHud({
    required this.status,
    required this.myTurn,
    required this.ballInHand,
    required this.power,
  });

  final String status;
  final bool myTurn;
  final bool ballInHand;
  final double power;

  @override
  Widget build(BuildContext context) {
    final (icon, label, color) = switch ((status, myTurn, ballInHand)) {
      ('waiting', _, _) => (
        Icons.people_alt_outlined,
        'En attente d’un adversaire',
        const Color(0xFF8DA9A0),
      ),
      ('finished', _, _) => (
        Icons.emoji_events_outlined,
        'Partie terminée',
        const Color(0xFFFFC83D),
      ),
      (_, _, true) => (
        Icons.pan_tool_alt_outlined,
        'Placez la blanche',
        const Color(0xFF5EE7B7),
      ),
      (_, true, _) => (
        Icons.sports_golf_rounded,
        'À vous · ${power.round()} %',
        const Color(0xFFFFC83D),
      ),
      _ => (
        Icons.hourglass_bottom_rounded,
        'Tour adverse',
        const Color(0xFF8DA9A0),
      ),
    };
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
      decoration: const BoxDecoration(
        color: Color(0xFF071F18),
        border: Border(top: BorderSide(color: Color(0xFF163B30))),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              label,
              style: TextStyle(color: color, fontWeight: FontWeight.w800),
            ),
          ),
          if (myTurn && !ballInHand) ...[
            const SizedBox(width: 10),
            SizedBox(
              width: 54,
              child: LinearProgressIndicator(
                value: power / 100,
                minHeight: 5,
                borderRadius: BorderRadius.circular(99),
                backgroundColor: Colors.white12,
                valueColor: AlwaysStoppedAnimation(
                  power >= 72
                      ? const Color(0xFFE5484D)
                      : const Color(0xFFFFC83D),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _PoolVictoryOverlay extends StatefulWidget {
  const _PoolVictoryOverlay({
    required this.winner,
    required this.currentUserWon,
    required this.bestRun,
    required this.onDismiss,
    required this.onExit,
  });
  final Map<String, dynamic> winner;
  final bool currentUserWon;
  final int bestRun;
  final VoidCallback onDismiss;
  final VoidCallback onExit;

  @override
  State<_PoolVictoryOverlay> createState() => _PoolVictoryOverlayState();
}

class _PoolVictoryOverlayState extends State<_PoolVictoryOverlay>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 3200),
    )..repeat();
    unawaited(HapticFeedback.heavyImpact());
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final photo = _text(widget.winner['photoUrl']);
    final name = _text(widget.winner['name'], 'Joueur WAPI');
    final points = (widget.winner['points'] as num?)?.toInt() ?? 0;
    final victories = (widget.winner['victories'] as num?)?.toInt() ?? 0;
    final defeats = (widget.winner['defeats'] as num?)?.toInt() ?? 0;
    return ColoredBox(
      color: const Color(0xE600090F),
      child: Stack(
        children: [
          Positioned.fill(
            child: IgnorePointer(
              child: AnimatedBuilder(
                animation: _controller,
                builder: (_, _) => CustomPaint(
                  painter: _PoolConfettiPainter(_controller.value),
                ),
              ),
            ),
          ),
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 390),
              child: Container(
                margin: const EdgeInsets.all(22),
                padding: const EdgeInsets.fromLTRB(22, 14, 22, 22),
                decoration: BoxDecoration(
                  color: const Color(0xF7081D31),
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(
                    color: widget.currentUserWon
                        ? const Color(0xFFFFD36B)
                        : const Color(0xFF4E7B9B),
                  ),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x99000000),
                      blurRadius: 28,
                      offset: Offset(0, 14),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Align(
                      alignment: Alignment.centerRight,
                      child: IconButton(
                        onPressed: widget.onDismiss,
                        icon: const Icon(Icons.close, color: Colors.white70),
                        tooltip: 'Voir la table',
                      ),
                    ),
                    const Icon(
                      Icons.emoji_events_rounded,
                      color: Color(0xFFFFD36B),
                      size: 48,
                    ),
                    const SizedBox(height: 6),
                    CircleAvatar(
                      radius: 38,
                      backgroundColor: const Color(0xFF174B65),
                      backgroundImage: photo.isEmpty
                          ? null
                          : NetworkImage(photo),
                      child: photo.isEmpty
                          ? Text(
                              name.substring(0, 1).toUpperCase(),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 27,
                                fontWeight: FontWeight.w900,
                              ),
                            )
                          : null,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      widget.currentUserWon
                          ? 'VICTOIRE !'
                          : 'VICTOIRE DE $name',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w900,
                        letterSpacing: .6,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      name,
                      style: const TextStyle(
                        color: Color(0xFF72E6BD),
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Row(
                      children: [
                        _PoolResultMetric(value: '$points', label: 'POINTS'),
                        _PoolResultMetric(
                          value: '$victories',
                          label: 'VICTOIRES',
                        ),
                        _PoolResultMetric(
                          value: '${widget.bestRun}',
                          label: 'MEILLEURE SÉRIE',
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '$defeats défaite(s) enregistrée(s)',
                      style: const TextStyle(
                        color: Color(0xFF9BB7C9),
                        fontSize: 10,
                      ),
                    ),
                    const SizedBox(height: 18),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: widget.onExit,
                        icon: const Icon(Icons.arrow_back_rounded),
                        label: const Text(
                          'RETOUR AU SALON',
                          style: TextStyle(fontWeight: FontWeight.w900),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PoolResultMetric extends StatelessWidget {
  const _PoolResultMetric({required this.value, required this.label});
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Color(0xFF9BB7C9),
            fontSize: 8,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    ),
  );
}

class _PoolConfettiPainter extends CustomPainter {
  const _PoolConfettiPainter(this.progress);
  final double progress;

  static const _colors = [
    Color(0xFFFFD36B),
    Color(0xFF72E6BD),
    Color(0xFF51C3FF),
    Color(0xFFFF6B7A),
    Color(0xFFB185FF),
  ];

  @override
  void paint(Canvas canvas, Size size) {
    for (var index = 0; index < 54; index += 1) {
      final seedX = ((index * 47) % 101) / 101;
      final seedY = ((index * 29) % 97) / 97;
      final drift = math.sin(progress * math.pi * 2 + index) * 18;
      final x = seedX * size.width + drift;
      final y =
          ((seedY + progress * (1.05 + index % 4 * .08)) % 1.12) *
              (size.height + 60) -
          30;
      canvas.save();
      canvas.translate(x, y);
      canvas.rotate(progress * math.pi * 4 + index * .41);
      final paint = Paint()..color = _colors[index % _colors.length];
      if (index.isEven) {
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTWH(-3, -7, 6, 14),
            const Radius.circular(2),
          ),
          paint,
        );
      } else {
        canvas.drawCircle(Offset.zero, 4, paint);
      }
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(covariant _PoolConfettiPainter oldDelegate) =>
      oldDelegate.progress != progress;
}

class _PlayersBar extends StatelessWidget {
  const _PlayersBar({
    required this.left,
    required this.right,
    required this.activeUid,
    required this.deadlineMs,
  });
  final Map<String, dynamic> left;
  final Map<String, dynamic> right;
  final String activeUid;
  final int deadlineMs;

  @override
  Widget build(BuildContext context) => Container(
    color: const Color(0xFF071F18),
    padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
    child: Row(
      children: [
        Expanded(
          child: _PlayerTile(
            player: left,
            active: left['uid'] == activeUid,
            deadlineMs: left['uid'] == activeUid ? deadlineMs : 0,
          ),
        ),
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 8),
          child: Text(
            'VS',
            style: TextStyle(
              color: Color(0xFFFFC83D),
              fontWeight: FontWeight.w900,
            ),
          ),
        ),
        Expanded(
          child: _PlayerTile(
            player: right,
            active: right['uid'] == activeUid,
            deadlineMs: right['uid'] == activeUid ? deadlineMs : 0,
            right: true,
          ),
        ),
      ],
    ),
  );
}

class _PlayerTile extends StatelessWidget {
  const _PlayerTile({
    required this.player,
    required this.active,
    required this.deadlineMs,
    this.right = false,
  });
  final Map<String, dynamic> player;
  final bool active;
  final int deadlineMs;
  final bool right;

  @override
  Widget build(BuildContext context) {
    final photo = _text(player['photoUrl']);
    final avatar = CircleAvatar(
      radius: 20,
      backgroundColor: const Color(0xFF0D3A2E),
      backgroundImage: photo.isEmpty ? null : NetworkImage(photo),
      child: photo.isEmpty
          ? const Icon(Icons.person_rounded, color: Color(0xFF98F0C9))
          : null,
    );
    final text = Expanded(
      child: Column(
        crossAxisAlignment: right
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        children: [
          Text(
            _text(player['name']),
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w900,
            ),
          ),
          if (active)
            _PoolTurnCountdown(deadlineMs: deadlineMs)
          else
            Text(
              _text(player['group'], 'En attente'),
              style: const TextStyle(color: Color(0xFF8DA9A0), fontSize: 11),
            ),
          if ((player['remaining'] as List? ?? const []).isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 5),
              child: Wrap(
                alignment: right ? WrapAlignment.end : WrapAlignment.start,
                spacing: 3,
                children: (player['remaining'] as List)
                    .take(7)
                    .map((value) => _ScoreBall(id: value as int))
                    .toList(),
              ),
            ),
        ],
      ),
    );
    return Row(
      textDirection: right ? TextDirection.rtl : TextDirection.ltr,
      children: [avatar, const SizedBox(width: 8), text],
    );
  }
}

class _PoolTurnCountdown extends StatelessWidget {
  const _PoolTurnCountdown({required this.deadlineMs});
  final int deadlineMs;

  @override
  Widget build(BuildContext context) {
    final remaining = math.max(
      0,
      ((deadlineMs - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
    );
    return TweenAnimationBuilder<double>(
      key: ValueKey(deadlineMs),
      tween: Tween(begin: remaining.toDouble(), end: 0),
      duration: Duration(seconds: remaining),
      builder: (context, seconds, _) {
        final urgent = seconds <= 10;
        final color = urgent
            ? const Color(0xFFFF6B6B)
            : const Color(0xFF5EE7B7);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'À jouer · ${seconds.ceil()} s',
              style: TextStyle(
                color: color,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: (seconds / 45).clamp(0.0, 1.0),
                minHeight: 4,
                color: color,
                backgroundColor: const Color(0xFF174535),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ScoreBall extends StatelessWidget {
  const _ScoreBall({required this.id});
  final int id;
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 16,
    height: 16,
    child: CustomPaint(painter: _ScoreBallPainter(id)),
  );
}

class _ScoreBallPainter extends CustomPainter {
  const _ScoreBallPainter(this.id);
  final int id;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.shortestSide / 2 - .7;
    final bounds = Rect.fromCircle(center: center, radius: radius);
    canvas.drawCircle(
      center.translate(.7, 1.1),
      radius,
      Paint()..color = const Color(0x99000000),
    );
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..shader = RadialGradient(
          center: const Alignment(-.42, -.48),
          colors: id > 8
              ? const [Colors.white, Color(0xFFF1F3F5)]
              : [Colors.white, _ballColor(id)],
          stops: const [.02, 1],
        ).createShader(bounds),
    );
    if (id > 8) {
      canvas.save();
      canvas.clipPath(Path()..addOval(bounds));
      canvas.drawRect(
        Rect.fromCenter(
          center: center,
          width: radius * 2.2,
          height: radius * .85,
        ),
        Paint()..color = _ballColor(id),
      );
      canvas.restore();
    }
    canvas.drawCircle(center, radius * .44, Paint()..color = Colors.white);
    final number = TextPainter(
      text: TextSpan(
        text: '$id',
        style: TextStyle(
          color: const Color(0xFF111827),
          fontSize: id >= 10 ? 5.2 : 6,
          fontWeight: FontWeight.w900,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    number.paint(canvas, center - Offset(number.width / 2, number.height / 2));
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = .6
        ..color = Colors.white70,
    );
  }

  @override
  bool shouldRepaint(covariant _ScoreBallPainter oldDelegate) =>
      oldDelegate.id != id;
}

/// The contact point on the cue ball is not cosmetic: the native renderer and
/// the authoritative shot both receive these values.  Moving it left/right
/// adds English; moving it up/down applies follow or draw.
class _CueEnglishPad extends StatelessWidget {
  const _CueEnglishPad({
    required this.sideSpin,
    required this.followSpin,
    required this.onChanged,
  });

  final double sideSpin;
  final double followSpin;
  final void Function(double side, double follow) onChanged;

  void _update(Offset point, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.shortestSide * .34;
    final dx = ((point.dx - center.dx) / radius).clamp(-1.0, 1.0);
    final dy = ((center.dy - point.dy) / radius).clamp(-1.0, 1.0);
    if (dx * dx + dy * dy > 1) {
      final length = math.sqrt(dx * dx + dy * dy);
      onChanged(dx / length, dy / length);
      return;
    }
    onChanged(dx, dy);
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 70,
    height: 70,
    child: Semantics(
      label: 'Point d’impact sur la bille blanche',
      child: GestureDetector(
        onTapDown: (details) =>
            _update(details.localPosition, const Size(70, 70)),
        onPanUpdate: (details) =>
            _update(details.localPosition, const Size(70, 70)),
        child: CustomPaint(
          painter: _CueEnglishPainter(
            sideSpin: sideSpin,
            followSpin: followSpin,
          ),
        ),
      ),
    ),
  );
}

class _CueEnglishPainter extends CustomPainter {
  const _CueEnglishPainter({required this.sideSpin, required this.followSpin});
  final double sideSpin;
  final double followSpin;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.shortestSide * .34;
    final shadow = Paint()..color = Colors.black.withValues(alpha: .32);
    canvas.drawCircle(center.translate(2, 3), radius + 4, shadow);
    final ball = Rect.fromCircle(center: center, radius: radius);
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..shader = const RadialGradient(
          center: Alignment(-.38, -.48),
          colors: [Colors.white, Color(0xFFDCE7EB), Color(0xFF99AAB2)],
          stops: [0, .34, 1],
        ).createShader(ball)
        ..style = PaintingStyle.fill,
    );
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = const Color(0xFFEAF6F5),
    );
    final contact =
        center + Offset(sideSpin * radius * .63, -followSpin * radius * .63);
    canvas.drawCircle(
      contact,
      radius * .16,
      Paint()..color = const Color(0xFFE5484D),
    );
    canvas.drawCircle(
      contact,
      radius * .16,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.4
        ..color = Colors.white,
    );
    final label = TextPainter(
      text: const TextSpan(
        text: 'EFFET',
        style: TextStyle(
          color: Color(0xFFEAF6F2),
          fontWeight: FontWeight.w800,
          fontSize: 9,
          letterSpacing: .5,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    label.paint(canvas, Offset(center.dx - label.width / 2, size.height - 13));
  }

  @override
  bool shouldRepaint(covariant _CueEnglishPainter oldDelegate) =>
      oldDelegate.sideSpin != sideSpin || oldDelegate.followSpin != followSpin;
}

class _PoolPowerRail extends StatelessWidget {
  const _PoolPowerRail({
    required this.value,
    required this.enabled,
    required this.onChanged,
    required this.onRelease,
  });
  final double value;
  final bool enabled;
  final ValueChanged<double> onChanged;
  final VoidCallback onRelease;

  void _update(Offset position, double height) {
    if (!enabled) return;
    const trackTop = 20.0;
    final trackBottom = math.max(trackTop + 1, height - 20);
    final ratio =
        ((position.dy.clamp(trackTop, trackBottom) - trackTop) /
                (trackBottom - trackTop))
            .clamp(0.0, 1.0);
    onChanged(10 + ratio * 90);
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final height = constraints.hasBoundedHeight
          ? constraints.maxHeight
          : 178.0;
      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onVerticalDragStart: (details) {
          if (!enabled) return;
          onChanged(10);
          _update(details.localPosition, height);
          HapticFeedback.selectionClick();
        },
        onVerticalDragUpdate: (details) =>
            _update(details.localPosition, height),
        onVerticalDragEnd: (_) {
          if (!enabled) return;
          HapticFeedback.mediumImpact();
          onRelease();
        },
        child: CustomPaint(
          size: const Size(48, 178),
          painter: _PoolPowerRailPainter(value: value, enabled: enabled),
        ),
      );
    },
  );
}

class _PoolPowerRailPainter extends CustomPainter {
  const _PoolPowerRailPainter({required this.value, required this.enabled});
  final double value;
  final bool enabled;
  @override
  void paint(Canvas canvas, Size size) {
    final rail = RRect.fromRectAndRadius(
      Rect.fromLTWH(3, 2, size.width - 6, size.height - 4),
      const Radius.circular(21),
    );
    canvas.drawRRect(rail, Paint()..color = const Color(0xFF260E15));
    canvas.drawRRect(
      rail,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..color = const Color(0xFF8B3147),
    );
    final track = RRect.fromRectAndRadius(
      Rect.fromLTWH(size.width / 2 - 5, 20, 10, size.height - 40),
      const Radius.circular(6),
    );
    canvas.drawRRect(track, Paint()..color = const Color(0xFF15090D));
    final height = track.height * ((value - 10) / 90);
    final power = RRect.fromRectAndRadius(
      Rect.fromLTWH(track.left, track.top, track.width, height),
      const Radius.circular(6),
    );
    canvas.drawRRect(
      power,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFFFFD049), Color(0xFFE22535)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(power.outerRect),
    );
    for (var index = 0; index <= 10; index += 1) {
      final y = track.top + track.height * index / 10;
      final major = index % 5 == 0;
      canvas.drawLine(
        Offset(major ? 8 : 12, y),
        Offset(track.left - 3, y),
        Paint()
          ..color = enabled ? Colors.white54 : Colors.white24
          ..strokeWidth = major ? 1.5 : .8,
      );
    }
    final cueY = track.top + height;
    // The miniature shaft follows the player's finger and gives the rail the
    // same physical pull-to-shoot reading as a real cue stroke.
    canvas.drawLine(
      Offset(size.width / 2, track.top - 6),
      Offset(size.width / 2, cueY),
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFFE8C17E), Color(0xFF8E4E20)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(Rect.fromLTRB(0, track.top - 6, 0, cueY))
        ..strokeWidth = 4
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawLine(
      Offset(size.width / 2, track.top - 8),
      Offset(size.width / 2, track.top - 3),
      Paint()
        ..color = const Color(0xFF6FC6E9)
        ..strokeWidth = 5
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawLine(
      Offset(8, cueY),
      Offset(size.width - 8, cueY),
      Paint()
        ..color = enabled ? const Color(0xFFEAF6F2) : const Color(0xFF65756D)
        ..strokeWidth = 3
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawCircle(
      Offset(size.width / 2, cueY),
      6,
      Paint()
        ..color = value >= 75
            ? const Color(0xFFE22535)
            : const Color(0xFFFFC64B),
    );
  }

  @override
  bool shouldRepaint(covariant _PoolPowerRailPainter oldDelegate) =>
      oldDelegate.value != value || oldDelegate.enabled != enabled;
}

/// The Android version is an OpenGL platform view fed by the same
/// server-authoritative state as the rest of WAPI.  The fallback keeps the
/// table usable on a platform where the native 3D bridge is not yet loaded;
/// it deliberately preserves controls rather than showing a broken screen.
class _WapiPool3DStage extends StatefulWidget {
  const _WapiPool3DStage({
    super.key,
    required this.balls,
    required this.angle,
    required this.power,
    required this.sideSpin,
    required this.followSpin,
    required this.aimBonus,
    required this.moving,
    required this.showAim,
    required this.cueInHand,
    required this.cueStyle,
    required this.tableTheme,
    required this.onTablePoint,
    required this.onCuePull,
  });

  final List<_PoolBall> balls;
  final double angle;
  final double power;
  final double sideSpin;
  final double followSpin;
  final int aimBonus;
  final bool moving;
  final bool showAim;
  final bool cueInHand;
  final String cueStyle;
  final String tableTheme;
  final void Function(Offset position, bool released) onTablePoint;
  final void Function(Offset start, Offset end, double power) onCuePull;

  @override
  State<_WapiPool3DStage> createState() => _WapiPool3DStageState();
}

class _WapiPool3DStageState extends State<_WapiPool3DStage> {
  MethodChannel? _channel;
  Offset? _fallbackPullStart;
  Offset? _fallbackPullEnd;

  Map<String, dynamic> get _renderState => {
    'balls': widget.balls
        .map(
          (ball) => {
            'id': ball.id,
            'x': ball.x,
            'y': ball.y,
            'vx': ball.vx,
            'vy': ball.vy,
            'pocketed': ball.pocketed,
          },
        )
        .toList(),
    'aimAngle': widget.angle,
    'power': widget.power,
    'sideSpin': widget.sideSpin,
    'followSpin': widget.followSpin,
    'aimBonus': widget.aimBonus,
    'moving': widget.moving,
    'cueInHand': widget.cueInHand,
    'tableTheme': widget.tableTheme,
    'cueStyle': widget.cueStyle,
  };

  @override
  void didUpdateWidget(covariant _WapiPool3DStage oldWidget) {
    super.didUpdateWidget(oldWidget);
    _render();
  }

  Future<void> stroke() async {
    await _channel?.invokeMethod<void>('stroke');
  }

  Future<bool> playSound(String kind, double intensity) async {
    final channel = _channel;
    if (channel == null) return false;
    try {
      return await channel.invokeMethod<bool>('sound', {
            'kind': kind,
            'intensity': intensity.clamp(.12, 1.0),
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  Future<void> _render() async {
    await _channel?.invokeMethod<void>('render', _renderState);
  }

  Future<dynamic> _onNativeMessage(MethodCall call) async {
    if (call.method == 'cuePull') {
      final data = Map<Object?, Object?>.from(call.arguments as Map);
      final startX = (data['startX'] as num?)?.toDouble();
      final startY = (data['startY'] as num?)?.toDouble();
      final endX = (data['endX'] as num?)?.toDouble();
      final endY = (data['endY'] as num?)?.toDouble();
      final power = (data['power'] as num?)?.toDouble();
      if (startX != null &&
          startY != null &&
          endX != null &&
          endY != null &&
          power != null) {
        widget.onCuePull(
          Offset(startX, startY),
          Offset(endX, endY),
          power.clamp(10, 100),
        );
      }
      return null;
    }
    if (call.method != 'gesture') return null;
    final data = Map<Object?, Object?>.from(call.arguments as Map);
    final x = (data['x'] as num?)?.toDouble();
    final y = (data['y'] as num?)?.toDouble();
    if (x == null || y == null) return null;
    widget.onTablePoint(
      Offset(x.clamp(.055, .945), y.clamp(.065, .935)),
      data['released'] == true,
    );
    return null;
  }

  @override
  Widget build(BuildContext context) {
    if (Theme.of(context).platform == TargetPlatform.android) {
      return AndroidView(
        viewType: 'wapi/pool-3d',
        creationParams: _renderState,
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: (id) {
          _channel = MethodChannel('wapi/pool-3d/$id');
          _channel!.setMethodCallHandler(_onNativeMessage);
          _render();
        },
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => GestureDetector(
        onPanDown: (details) {
          _fallbackPullStart = _normalizedPoint(
            details.localPosition,
            constraints,
          );
          _fallbackPullEnd = _fallbackPullStart;
          _fallbackPoint(details.localPosition, constraints, false);
        },
        onPanUpdate: (details) {
          _fallbackPullEnd = _normalizedPoint(
            details.localPosition,
            constraints,
          );
          _fallbackPoint(details.localPosition, constraints, false);
        },
        onPanEnd: (_) {
          final start = _fallbackPullStart;
          final end = _fallbackPullEnd;
          if (start != null && end != null) {
            final distance = (start - end).distance;
            if (distance >= .045) {
              widget.onCuePull(
                start,
                end,
                (distance * 260 + 12).clamp(10, 100),
              );
            }
          }
          _fallbackPullStart = null;
          _fallbackPullEnd = null;
        },
        onTapUp: (details) =>
            _fallbackPoint(details.localPosition, constraints, true),
        child: CustomPaint(
          painter: _PoolTablePainter(
            balls: widget.balls,
            angle: widget.angle,
            showAim: widget.showAim,
            showHand: widget.cueInHand,
            cueStyle: widget.cueStyle,
          ),
        ),
      ),
    );
  }

  void _fallbackPoint(Offset point, BoxConstraints constraints, bool released) {
    if (constraints.maxWidth <= 0 || constraints.maxHeight <= 0) return;
    widget.onTablePoint(
      Offset(point.dx / constraints.maxWidth, point.dy / constraints.maxHeight),
      released,
    );
  }

  Offset _normalizedPoint(Offset point, BoxConstraints constraints) =>
      Offset(point.dx / constraints.maxWidth, point.dy / constraints.maxHeight);

  @override
  void dispose() {
    _channel?.setMethodCallHandler(null);
    super.dispose();
  }
}

class _PoolTablePainter extends CustomPainter {
  const _PoolTablePainter({
    required this.balls,
    required this.angle,
    required this.showAim,
    required this.showHand,
    required this.cueStyle,
  });
  final List<_PoolBall> balls;
  final double angle;
  final bool showAim;
  final bool showHand;
  final String cueStyle;

  @override
  void paint(Canvas canvas, Size size) {
    final outer = RRect.fromRectAndRadius(
      Offset.zero & size,
      const Radius.circular(20),
    );
    canvas.drawRRect(
      outer,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFF6B341C), Color(0xFF210F0B)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ).createShader(outer.outerRect),
    );
    final rail = RRect.fromRectAndRadius(
      Rect.fromLTWH(
        size.width * .035,
        size.height * .05,
        size.width * .93,
        size.height * .90,
      ),
      const Radius.circular(17),
    );
    canvas.drawRRect(
      rail,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFF9A4324), Color(0xFF3C160F), Color(0xFF7D321D)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ).createShader(rail.outerRect),
    );
    final felt = RRect.fromRectAndRadius(
      Rect.fromLTWH(
        size.width * .07,
        size.height * .105,
        size.width * .86,
        size.height * .79,
      ),
      const Radius.circular(10),
    );
    canvas.drawRRect(
      felt,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFF0077A8), Color(0xFF005B86)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ).createShader(felt.outerRect),
    );
    final boltPaint = Paint()
      ..color = const Color(0xFFC7D3D8)
      ..style = PaintingStyle.fill;
    for (var fraction = .16; fraction < .9; fraction += .16) {
      canvas.drawCircle(
        Offset(size.width * fraction, size.height * .074),
        size.shortestSide * .009,
        boltPaint,
      );
      canvas.drawCircle(
        Offset(size.width * fraction, size.height * .926),
        size.shortestSide * .009,
        boltPaint,
      );
    }
    // Low-profile ball-return: a recessed black tray with two polished tubes
    // and rounded ends.  It stays behind the head rail instead of becoming a
    // tall metallic fence across the table.
    final returnBay = RRect.fromRectAndRadius(
      Rect.fromLTWH(
        size.width * .25,
        size.height * .014,
        size.width * .50,
        size.height * .070,
      ),
      const Radius.circular(12),
    );
    canvas.drawRRect(returnBay, Paint()..color = const Color(0xFF08131C));
    canvas.drawRRect(
      returnBay,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.shortestSide * .008
        ..shader = const LinearGradient(
          colors: [Color(0xFFEDF7FA), Color(0xFF71858C), Color(0xFFEAF4F6)],
        ).createShader(returnBay.outerRect),
    );
    for (final offset in [.026, .051]) {
      final line = Offset(size.width * .28, size.height * offset);
      canvas.drawLine(
        line,
        Offset(size.width * .72, size.height * offset),
        Paint()
          ..color = const Color(0xFFD9E8EC)
          ..strokeWidth = size.shortestSide * .008
          ..strokeCap = StrokeCap.round,
      );
    }
    final pockets = <Offset>[
      Offset(size.width * .07, size.height * .105),
      Offset(size.width * .5, size.height * .095),
      Offset(size.width * .93, size.height * .105),
      Offset(size.width * .07, size.height * .895),
      Offset(size.width * .5, size.height * .905),
      Offset(size.width * .93, size.height * .895),
    ];
    for (final pocket in pockets) {
      // A real drop pocket has a polished steel ferrule, a leather throat and
      // a deep matte liner. Layering all three keeps it from reading as a
      // flat black circle on smaller phones.
      final metalRadius = size.shortestSide * .071;
      final metalRect = Rect.fromCircle(center: pocket, radius: metalRadius);
      canvas.drawCircle(
        pocket,
        metalRadius,
        Paint()
          ..shader = const RadialGradient(
            colors: [Color(0xFFF4FAFC), Color(0xFF9EADB3), Color(0xFF4D5B60)],
            stops: [.05, .44, 1],
          ).createShader(metalRect),
      );
      canvas.drawCircle(
        pocket,
        size.shortestSide * .061,
        Paint()
          ..shader =
              const RadialGradient(
                colors: [Color(0xFF8A5638), Color(0xFF2A110B)],
              ).createShader(
                Rect.fromCircle(
                  center: pocket,
                  radius: size.shortestSide * .061,
                ),
              ),
      );
      canvas.drawCircle(
        pocket,
        size.shortestSide * .055,
        Paint()
          ..shader =
              const RadialGradient(
                colors: [Color(0xFF6A3B24), Color(0xFF120B09)],
              ).createShader(
                Rect.fromCircle(
                  center: pocket,
                  radius: size.shortestSide * .055,
                ),
              ),
      );
      canvas.drawCircle(
        pocket,
        size.shortestSide * .044,
        Paint()..color = const Color(0xFF000000),
      );
    }
    final cue = _cue(balls);
    if (showAim) {
      final origin = Offset(cue.dx * size.width, cue.dy * size.height);
      final target =
          origin + Offset(math.cos(angle), math.sin(angle)) * size.width;
      canvas.drawLine(
        origin,
        target,
        Paint()
          ..color = const Color(0xFFEAFBFF).withValues(alpha: .72)
          ..strokeWidth = 2
          ..style = PaintingStyle.stroke,
      );
      final cueStart =
          origin - Offset(math.cos(angle), math.sin(angle)) * size.width * .30;
      final cueColor = switch (cueStyle) {
        'carbon' => const Color(0xFF2A3037),
        'obsidian' => const Color(0xFF281459),
        'walnut' => const Color(0xFF5B2712),
        _ => const Color(0xFFB97A46),
      };
      canvas.drawLine(
        cueStart,
        origin - Offset(math.cos(angle), math.sin(angle)) * 12,
        Paint()
          ..color = cueColor
          ..strokeWidth = size.shortestSide * .018
          ..strokeCap = StrokeCap.round,
      );
    }
    for (final ball in balls) {
      if (ball.pocketed) continue;
      final center = Offset(ball.x * size.width, ball.y * size.height);
      final radius = size.shortestSide * .026;
      final color = _ballColor(ball.id);
      canvas.drawCircle(
        center.translate(radius * .16, radius * .20),
        radius,
        Paint()..color = const Color(0xFF00111B).withValues(alpha: .35),
      );
      final ballRect = Rect.fromCircle(center: center, radius: radius);
      if (ball.id > 8) {
        canvas.drawCircle(
          center,
          radius,
          Paint()..color = const Color(0xFFF8F9F4),
        );
        canvas.save();
        canvas.clipPath(Path()..addOval(ballRect));
        canvas.drawRect(
          Rect.fromCenter(
            center: center,
            width: radius * 2.2,
            height: radius * .72,
          ),
          Paint()..color = color,
        );
        canvas.restore();
        canvas.drawCircle(
          center,
          radius,
          Paint()
            ..color = const Color(0x00000000)
            ..style = PaintingStyle.stroke
            ..strokeWidth = radius * .08
            ..shader = RadialGradient(
              colors: [
                Colors.white.withValues(alpha: .9),
                const Color(0xFF26313A),
              ],
            ).createShader(ballRect),
        );
      } else {
        canvas.drawCircle(
          center,
          radius,
          Paint()
            ..shader = RadialGradient(
              colors: [Colors.white, color],
              stops: const [.0, 1],
            ).createShader(ballRect),
        );
      }
      if (ball.id != 0) {
        canvas.drawCircle(center, radius * .44, Paint()..color = Colors.white);
        final painter = TextPainter(
          text: TextSpan(
            text: ball.id.toString(),
            style: TextStyle(
              color: Colors.black,
              fontWeight: FontWeight.w900,
              fontSize: radius,
            ),
          ),
          textDirection: TextDirection.ltr,
        )..layout();
        painter.paint(
          canvas,
          center - Offset(painter.width / 2, painter.height / 2),
        );
      }
    }
    if (showHand) {
      final marker = Offset(cue.dx * size.width, cue.dy * size.height);
      _drawGlovedHand(canvas, marker, size.shortestSide * .07);
    }
  }

  @override
  bool shouldRepaint(covariant _PoolTablePainter oldDelegate) =>
      oldDelegate.balls != balls ||
      oldDelegate.angle != angle ||
      oldDelegate.showAim != showAim ||
      oldDelegate.showHand != showHand ||
      oldDelegate.cueStyle != cueStyle;
}

void _drawGlovedHand(Canvas canvas, Offset center, double size) {
  final glove = Paint()
    ..shader = LinearGradient(
      colors: [const Color(0xFFF4F8FA), const Color(0xFF8FA9B4)],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ).createShader(Rect.fromCircle(center: center, radius: size));
  final outline = Paint()
    ..color = const Color(0xFF28434E)
    ..style = PaintingStyle.stroke
    ..strokeWidth = size * .07;
  canvas.save();
  canvas.translate(center.dx, center.dy);
  canvas.rotate(-.35);
  final palm = RRect.fromRectAndRadius(
    Rect.fromCenter(
      center: Offset.zero,
      width: size * 1.15,
      height: size * .86,
    ),
    Radius.circular(size * .32),
  );
  canvas.drawRRect(palm, glove);
  canvas.drawRRect(palm, outline);
  for (var index = 0; index < 4; index += 1) {
    final x = -size * .34 + index * size * .22;
    final finger = RRect.fromRectAndRadius(
      Rect.fromLTWH(x, -size * 1.03, size * .18, size * .73),
      Radius.circular(size * .1),
    );
    canvas.drawRRect(finger, glove);
    canvas.drawRRect(finger, outline);
  }
  final thumb = RRect.fromRectAndRadius(
    Rect.fromLTWH(size * .4, -size * .08, size * .55, size * .2),
    Radius.circular(size * .1),
  );
  canvas.drawRRect(thumb, glove);
  canvas.drawRRect(thumb, outline);
  canvas.restore();
}

class _PoolEquipmentWorkshop extends StatelessWidget {
  const _PoolEquipmentWorkshop({
    required this.cues,
    required this.themes,
    required this.cueStyle,
    required this.tableTheme,
    required this.tokens,
    required this.founder,
    required this.owned,
    required this.onCue,
    required this.onTheme,
    required this.onApply,
  });

  final List<_CueOption> cues;
  final List<_PoolTableTheme> themes;
  final String cueStyle;
  final String tableTheme;
  final int tokens;
  final bool founder;
  final Set<String> owned;
  final ValueChanged<_CueOption> onCue;
  final ValueChanged<String> onTheme;
  final VoidCallback onApply;

  @override
  Widget build(BuildContext context) => FractionallySizedBox(
    heightFactor: .94,
    child: DecoratedBox(
      decoration: const BoxDecoration(
        color: Color(0xFF071A2B),
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        border: Border(top: BorderSide(color: Color(0xFF318DC6))),
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 12, 10),
            child: Row(
              children: [
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'ATELIER WAPI POOL',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 19,
                          fontWeight: FontWeight.w900,
                          letterSpacing: .5,
                        ),
                      ),
                      Text(
                        'Équipement appliqué réellement à votre tir',
                        style: TextStyle(
                          color: Color(0xFFA7C9DF),
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 9,
                    vertical: 7,
                  ),
                  decoration: BoxDecoration(
                    color: founder
                        ? const Color(0xFF145D48)
                        : const Color(0xFF173F62),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    founder ? 'FONDATEUR · GRATUIT' : '$tokens JETONS',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 9,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded, color: Colors.white),
                  tooltip: 'Fermer',
                ),
              ],
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(18, 6, 18, 18),
              children: [
                const Text(
                  'TAPIS DE TABLE',
                  style: TextStyle(
                    color: Color(0xFF70D8FF),
                    fontSize: 10,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 9),
                Row(
                  children: themes
                      .map((theme) {
                        final selected = theme.id == tableTheme;
                        return Expanded(
                          child: Padding(
                            padding: EdgeInsets.only(
                              right: theme == themes.last ? 0 : 7,
                            ),
                            child: InkWell(
                              onTap: () => onTheme(theme.id),
                              borderRadius: BorderRadius.circular(15),
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 180),
                                height: 70,
                                padding: const EdgeInsets.all(9),
                                decoration: BoxDecoration(
                                  color: theme.color,
                                  borderRadius: BorderRadius.circular(15),
                                  border: Border.all(
                                    color: selected
                                        ? Colors.white
                                        : Colors.white24,
                                    width: selected ? 2 : 1,
                                  ),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceBetween,
                                  children: [
                                    Icon(
                                      selected
                                          ? Icons.check_circle_rounded
                                          : Icons.circle_outlined,
                                      color: Colors.white,
                                      size: 17,
                                    ),
                                    Text(
                                      theme.name,
                                      maxLines: 2,
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 10,
                                        height: 1.1,
                                        fontWeight: FontWeight.w800,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        );
                      })
                      .toList(growable: false),
                ),
                const SizedBox(height: 20),
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'COLLECTION DE QUEUES',
                        style: TextStyle(
                          color: Color(0xFF70D8FF),
                          fontSize: 10,
                          fontWeight: FontWeight.w900,
                          letterSpacing: 1,
                        ),
                      ),
                    ),
                    Text(
                      '${owned.length}/${cues.length} déverrouillées',
                      style: const TextStyle(
                        color: Color(0xFF9BB7C9),
                        fontSize: 10,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                ...cues.map(
                  (cue) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: _CueEquipmentCard(
                      cue: cue,
                      selected: cue.id == cueStyle,
                      owned: owned.contains(cue.id),
                      onTap: () => onCue(cue),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 10, 18, 16),
            child: SizedBox(
              width: double.infinity,
              height: 50,
              child: FilledButton.icon(
                onPressed: onApply,
                style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFF0785C9),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                icon: const Icon(Icons.sports_golf_rounded),
                label: Text(
                  'JOUER AVEC ${cues.firstWhere((cue) => cue.id == cueStyle).name.toUpperCase()}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w900),
                ),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _CueEquipmentCard extends StatelessWidget {
  const _CueEquipmentCard({
    required this.cue,
    required this.selected,
    required this.owned,
    required this.onTap,
  });

  final _CueOption cue;
  final bool selected;
  final bool owned;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: selected
        ? const Color(0xFF0B4267)
        : owned
        ? const Color(0xFF0C263C)
        : const Color(0xFF0A1A2A),
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(19),
      side: BorderSide(
        color: selected ? const Color(0xFF72E6BD) : const Color(0xFF254961),
        width: selected ? 2 : 1,
      ),
    ),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(19),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 13),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        cue.name,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 15,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      Text(
                        '${cue.tier.toUpperCase()} · ${cue.detail}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: selected
                              ? const Color(0xFF72E6BD)
                              : const Color(0xFF9FC1D5),
                          fontSize: 9,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 9,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: selected
                        ? const Color(0xFF158765)
                        : owned
                        ? const Color(0xFF163D5C)
                        : const Color(0xFF127A41),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    selected
                        ? 'ÉQUIPÉE'
                        : owned
                        ? 'ÉQUIPER'
                        : '${cue.priceTokens} JETONS',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 9,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 9),
            SizedBox(
              height: 25,
              width: double.infinity,
              child: CustomPaint(
                painter: _CuePreviewPainter(cue: cue, selected: selected),
              ),
            ),
            const SizedBox(height: 8),
            _CueStatBar(
              label: 'Force',
              value: cue.powerScore,
              selected: selected,
            ),
            _CueStatBar(
              label: 'Visée',
              value: cue.aimScore,
              selected: selected,
            ),
            _CueStatBar(
              label: 'Effet',
              value: cue.spinScore,
              selected: selected,
            ),
            _CueStatBar(
              label: 'Contrôle',
              value: cue.control,
              selected: selected,
            ),
          ],
        ),
      ),
    ),
  );
}

class _CueStatBar extends StatelessWidget {
  const _CueStatBar({
    required this.label,
    required this.value,
    required this.selected,
  });
  final String label;
  final int value;
  final bool selected;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 4),
    child: Row(
      children: [
        SizedBox(
          width: 48,
          child: Text(
            label,
            style: const TextStyle(color: Color(0xFF9FC1D5), fontSize: 9),
          ),
        ),
        ...List.generate(
          10,
          (index) => Expanded(
            child: Container(
              height: 4,
              margin: const EdgeInsets.only(right: 2),
              decoration: BoxDecoration(
                color: index < value
                    ? selected
                          ? const Color(0xFF74E7B7)
                          : const Color(0xFF218BD5)
                    : Colors.white12,
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

class _CuePreviewPainter extends CustomPainter {
  const _CuePreviewPainter({required this.cue, required this.selected});
  final _CueOption cue;
  final bool selected;

  @override
  void paint(Canvas canvas, Size size) {
    final shaft = switch (cue.id) {
      'walnut' => const Color(0xFF77401F),
      'carbon' => const Color(0xFF394B5D),
      'obsidian' => const Color(0xFF171A28),
      _ => const Color(0xFFD5A566),
    };
    final wrap = switch (cue.id) {
      'carbon' => const Color(0xFF56C9FF),
      'obsidian' => const Color(0xFFFFD36B),
      _ => const Color(0xFFE9EEF4),
    };
    final y = size.height / 2;
    final start = size.width * .035;
    final end = size.width * .965;
    canvas.drawLine(
      Offset(start, y + 3),
      Offset(end, y + 3),
      Paint()
        ..color = Colors.black.withValues(alpha: selected ? .38 : .22)
        ..strokeWidth = 6
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawLine(
      Offset(start, y),
      Offset(end, y),
      Paint()
        ..shader = LinearGradient(
          colors: [
            shaft.withValues(alpha: .72),
            shaft,
            const Color(0xFFE7D4AF),
          ],
        ).createShader(Rect.fromLTRB(start, y - 3, end, y + 3))
        ..strokeWidth = 5
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawLine(
      Offset(size.width * .38, y),
      Offset(size.width * .56, y),
      Paint()
        ..color = wrap
        ..strokeWidth = 7
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawCircle(
      Offset(end, y),
      3.5,
      Paint()..color = const Color(0xFFE8F6FF),
    );
    canvas.drawCircle(
      Offset(start, y),
      2.4,
      Paint()..color = const Color(0xFF203F58),
    );
  }

  @override
  bool shouldRepaint(covariant _CuePreviewPainter oldDelegate) =>
      oldDelegate.cue.id != cue.id || oldDelegate.selected != selected;
}

class _PoolEquipmentSelection {
  const _PoolEquipmentSelection({
    required this.cueStyle,
    required this.tableTheme,
    required this.tokens,
    required this.owned,
  });
  final String cueStyle;
  final String tableTheme;
  final int tokens;
  final Set<String> owned;
}

class _PoolTableTheme {
  const _PoolTableTheme(this.id, this.name, this.color);
  final String id;
  final String name;
  final Color color;
}

class _CueOption {
  const _CueOption({
    required this.id,
    required this.name,
    required this.tier,
    required this.detail,
    required this.priceTokens,
    required this.powerMultiplier,
    required this.aimBonus,
    required this.spinBonus,
    required this.control,
  });
  final String id;
  final String name;
  final String tier;
  final String detail;
  final int priceTokens;
  final double powerMultiplier;
  final int aimBonus;
  final int spinBonus;
  final int control;

  int get powerScore => ((powerMultiplier - .96) * 80).round().clamp(1, 10);
  int get aimScore => (4 + aimBonus).clamp(1, 10);
  int get spinScore => (3 + spinBonus).clamp(1, 10);
}

class _PoolBall {
  const _PoolBall({
    required this.id,
    required this.x,
    required this.y,
    required this.pocketed,
    this.vx = 0,
    this.vy = 0,
    this.sideSpin = 0,
    this.followSpin = 0,
  });
  final int id;
  final double x;
  final double y;
  final bool pocketed;
  final double vx;
  final double vy;
  final double sideSpin;
  final double followSpin;

  _PoolBall copyWith({
    double? x,
    double? y,
    bool? pocketed,
    double? vx,
    double? vy,
    double? sideSpin,
    double? followSpin,
  }) => _PoolBall(
    id: id,
    x: x ?? this.x,
    y: y ?? this.y,
    pocketed: pocketed ?? this.pocketed,
    vx: vx ?? this.vx,
    vy: vy ?? this.vy,
    sideSpin: sideSpin ?? this.sideSpin,
    followSpin: followSpin ?? this.followSpin,
  );
}

/// Short real-recorded pool samples.  The players are warmed up once so a
/// release on the power rail has an immediate cue strike on Android and web.
class _PoolAudio {
  final _cue = AudioPlayer();
  final _collisions = List.generate(4, (_) => AudioPlayer());
  final _rails = List.generate(2, (_) => AudioPlayer());
  final _pockets = List.generate(2, (_) => AudioPlayer());
  int _collisionIndex = 0;
  int _railIndex = 0;
  int _pocketIndex = 0;
  Future<void>? _preloadFuture;
  AudioSession? _session;
  bool _enabled = true;
  bool _ready = false;

  Future<void> preload() => _preloadFuture ??= _prepare();

  Future<void> _prepare() async {
    try {
      _session = await AudioSession.instance;
      // Pool effects are short, high-priority game sounds. Giving them a
      // dedicated game/sonification session avoids them being silenced by the
      // app's call or media session after the player comes back to a table.
      await _session!.configure(
        const AudioSessionConfiguration(
          avAudioSessionCategory: AVAudioSessionCategory.playback,
          androidAudioAttributes: AndroidAudioAttributes(
            contentType: AndroidAudioContentType.sonification,
            usage: AndroidAudioUsage.game,
          ),
          androidAudioFocusGainType: AndroidAudioFocusGainType.gainTransient,
          androidWillPauseWhenDucked: false,
        ),
      );
      await Future.wait([
        _cue.setAsset('assets/sounds/wapi_pool_cue.wav'),
        ..._collisions.map(
          (player) => player.setAsset('assets/sounds/wapi_pool_collision.wav'),
        ),
        ..._rails.map(
          (player) => player.setAsset('assets/sounds/wapi_pool_cushion.wav'),
        ),
        ..._pockets.map(
          (player) => player.setAsset('assets/sounds/wapi_pool_pocket.wav'),
        ),
      ]);
      _ready = true;
    } catch (_) {
      _ready = false;
      // Sound is an enhancement: the table stays playable if a platform
      // cannot preload one of the optional audio assets.
    }
  }

  void setEnabled(bool value) => _enabled = value;

  void preview() {
    cue(55);
    Future<void>.delayed(
      const Duration(milliseconds: 130),
      () => collision(.65),
    );
  }

  void cue(double power) => _play(_cue, .30 + power.clamp(10, 100) / 100 * .55);
  void collision(double intensity) {
    final index = _collisionIndex++ % _collisions.length;
    final force = intensity.clamp(.15, 1.0);
    _play(
      _collisions[index],
      .30 + force * .64,
      speed: .94 + force * .08 + (index.isEven ? -.012 : .012),
    );
  }

  void rail(double intensity) {
    final index = _railIndex++ % _rails.length;
    final force = intensity.clamp(.12, 1.0);
    _play(
      _rails[index],
      .24 + force * .58,
      speed: .93 + force * .07 + (index.isEven ? -.01 : .01),
    );
  }

  void pocket() {
    final index = _pocketIndex++ % _pockets.length;
    _play(_pockets[index], .80);
  }

  void _play(AudioPlayer player, double volume, {double speed = 1}) {
    // Android uses the native SoundPool owned by the table renderer.  It has
    // lower touch-to-sound latency and does not depend on an audio session
    // being resumed by the Flutter engine.  Keep just_audio as the fallback
    // for the other platforms.
    unawaited(() async {
      try {
        if (!_enabled) return;
        await preload();
        if (!_enabled) return;
        if (!_ready) {
          // Never leave a strike completely silent if a device rejected the
          // optional sample preload (for example after audio focus changed).
          await SystemSound.play(SystemSoundType.click);
          return;
        }
        await _session?.setActive(true);
        // Several preloaded voices preserve the natural cascade of a break
        // without cutting off the previous ball impact.
        if (player.playing) return;
        await player.setVolume(volume);
        await player.setSpeed(speed);
        await player.seek(Duration.zero);
        await player.play();
      } catch (_) {
        // Browsers can refuse a sound before their first user interaction.
      }
    }());
  }

  void dispose() {
    unawaited(_cue.dispose());
    for (final player in [..._collisions, ..._rails, ..._pockets]) {
      unawaited(player.dispose());
    }
  }
}

/// Local visual replay of the server physics.  The server remains the only
/// authority for the official board, score and winner; this makes a shot look
/// continuous for both players instead of replacing the balls with the final
/// position after a network round trip.
abstract final class _PoolPhysics {
  static const width = 10.2;
  static const height = 5.10;
  static const radius = .145;
  static const _pockets = <Offset>[
    Offset(.055, .07),
    Offset(.5, .07),
    Offset(.945, .07),
    Offset(.055, .93),
    Offset(.5, .93),
    Offset(.945, .93),
  ];

  static bool isMoving(List<_PoolBall> balls) => balls.any(
    (ball) => !ball.pocketed && _hypot(ball.vx * width, ball.vy * height) > .09,
  );

  static double ballContactIntensity(List<_PoolBall> balls) {
    var strongest = 0.0;
    for (var first = 0; first < balls.length; first += 1) {
      for (var second = first + 1; second < balls.length; second += 1) {
        final a = balls[first];
        final b = balls[second];
        if (a.pocketed || b.pocketed) continue;
        final dx = (b.x - a.x) * width;
        final dy = (b.y - a.y) * height;
        final distance = _hypot(dx, dy);
        if (distance > radius * 2.08 || distance <= .0001) continue;
        final nx = dx / distance;
        final ny = dy / distance;
        final relativeX = (a.vx - b.vx) * width;
        final relativeY = (a.vy - b.vy) * height;
        final closingSpeed = relativeX * nx + relativeY * ny;
        if (closingSpeed > .06) {
          strongest = math.max(strongest, (closingSpeed / 6.4).clamp(.12, 1.0));
        }
      }
    }
    return strongest;
  }

  static double railContactIntensity(
    List<_PoolBall> before,
    List<_PoolBall> after,
  ) {
    var strongest = 0.0;
    for (final ball in before) {
      if (ball.pocketed) continue;
      final next = after
          .where((candidate) => candidate.id == ball.id)
          .firstOrNull;
      if (next == null || next.pocketed) continue;
      final bounced =
          (ball.x <= .066 && ball.vx < 0 && next.vx > 0) ||
          (ball.x >= .934 && ball.vx > 0 && next.vx < 0) ||
          (ball.y <= .095 && ball.vy < 0 && next.vy > 0) ||
          (ball.y >= .905 && ball.vy > 0 && next.vy < 0);
      if (bounced) {
        strongest = math.max(
          strongest,
          (_hypot(ball.vx * width, ball.vy * height) / 7.0).clamp(.12, 1.0),
        );
      }
    }
    return strongest;
  }

  static List<_PoolBall> advance(List<_PoolBall> before, double delta) {
    final safeDelta = delta.clamp(0.0, .05);
    final substeps = (safeDelta / .006).ceil().clamp(1, 9);
    final stepDelta = safeDelta / substeps;
    var balls = before;
    for (var step = 0; step < substeps; step += 1) {
      balls = _advanceSubstep(balls, stepDelta);
    }
    return balls;
  }

  static List<_PoolBall> _advanceSubstep(List<_PoolBall> before, double delta) {
    final next = before.map((ball) {
      if (ball.pocketed) return ball;
      final worldVx = ball.vx * width;
      final worldVy = ball.vy * height;
      final speed = _hypot(worldVx, worldVy);
      final curve = ball.id == 0
          ? ball.sideSpin * (speed / 8).clamp(0, 1) * .0026
          : 0.0;
      final curvedVx = worldVx * math.cos(curve) - worldVy * math.sin(curve);
      final curvedVy = worldVx * math.sin(curve) + worldVy * math.cos(curve);
      final nextSpeed = math.max(0, speed - .72 * delta);
      final rolling = speed > .0001 ? nextSpeed / speed : 0.0;
      final spin = (1 - .74 * delta).clamp(0, 1);
      return ball.copyWith(
        x: ball.x + curvedVx / width * delta,
        y: ball.y + curvedVy / height * delta,
        vx: curvedVx / width * rolling,
        vy: curvedVy / height * rolling,
        sideSpin: ball.sideSpin * spin,
        followSpin: ball.followSpin * spin,
      );
    }).toList();

    for (var index = 0; index < next.length; index += 1) {
      final ball = next[index];
      if (ball.pocketed) continue;
      final nearest = _pockets
          .map(
            (pocket) =>
                (pocket: pocket, distance: _distanceToPocket(ball, pocket)),
          )
          .reduce((a, b) => a.distance < b.distance ? a : b);
      final pocketDx = (nearest.pocket.dx - ball.x) * width;
      final pocketDy = (nearest.pocket.dy - ball.y) * height;
      final threshold = (nearest.pocket.dx - .5).abs() < .05 ? .34 : .325;
      if (nearest.distance < threshold) {
        next[index] = ball.id == 0
            ? ball.copyWith(
                x: .23,
                y: .5,
                vx: 0,
                vy: 0,
                sideSpin: 0,
                followSpin: 0,
              )
            : ball.copyWith(
                pocketed: true,
                vx: 0,
                vy: 0,
                sideSpin: 0,
                followSpin: 0,
              );
        continue;
      }
      var x = ball.x;
      var y = ball.y;
      var vx = ball.vx;
      var vy = ball.vy;
      if (nearest.distance < .46 && nearest.distance > .001) {
        final attraction = ((.46 - nearest.distance) / .135).clamp(0, 1);
        vx += pocketDx / nearest.distance * attraction * 1.28 * delta / width;
        vy += pocketDy / nearest.distance * attraction * 1.28 * delta / height;
      } else {
        if (x < .064) {
          x = .064;
          vy += ball.sideSpin * vx.abs() * .035;
          vx = vx.abs() * .89;
        }
        if (x > .936) {
          x = .936;
          vy -= ball.sideSpin * vx.abs() * .035;
          vx = -vx.abs() * .89;
        }
        if (y < .093) {
          y = .093;
          vx -= ball.sideSpin * vy.abs() * .035;
          vy = vy.abs() * .89;
        }
        if (y > .907) {
          y = .907;
          vx += ball.sideSpin * vy.abs() * .035;
          vy = -vy.abs() * .89;
        }
      }
      next[index] = ball.copyWith(x: x, y: y, vx: vx, vy: vy);
    }

    for (var first = 0; first < next.length; first += 1) {
      for (var second = first + 1; second < next.length; second += 1) {
        final a = next[first];
        final b = next[second];
        if (a.pocketed || b.pocketed) continue;
        final dx = (b.x - a.x) * width;
        final dy = (b.y - a.y) * height;
        final distance = _hypot(dx, dy);
        if (distance <= 0 || distance >= radius * 2) continue;
        final nx = dx / distance;
        final ny = dy / distance;
        final relative =
            (b.vx - a.vx) * width * nx + (b.vy - a.vy) * height * ny;
        final correction = (radius * 2 - distance) * .5 + .001;
        var nextA = a.copyWith(
          x: a.x - nx * correction / width,
          y: a.y - ny * correction / height,
        );
        var nextB = b.copyWith(
          x: b.x + nx * correction / width,
          y: b.y + ny * correction / height,
        );
        if (relative < 0) {
          final impulse = -relative * .97;
          nextA = nextA.copyWith(
            vx: a.vx - impulse * nx / width,
            vy: a.vy - impulse * ny / height,
          );
          nextB = nextB.copyWith(
            vx: b.vx + impulse * nx / width,
            vy: b.vy + impulse * ny / height,
          );
          if (a.id == 0 || b.id == 0) {
            final cue = a.id == 0 ? a : b;
            final hitX = a.id == 0 ? nx : -nx;
            final hitY = a.id == 0 ? ny : -ny;
            final followX = cue.followSpin * impulse * .58 * hitX;
            final followY = cue.followSpin * impulse * .58 * hitY;
            final englishX = -cue.sideSpin * impulse * .13 * hitY;
            final englishY = cue.sideSpin * impulse * .13 * hitX;
            final adjusted = a.id == 0 ? nextA : nextB;
            final spun = adjusted.copyWith(
              vx: adjusted.vx + (followX + englishX) / width,
              vy: adjusted.vy + (followY + englishY) / height,
              sideSpin: cue.sideSpin * .52,
              followSpin: cue.followSpin * .34,
            );
            if (a.id == 0) {
              nextA = spun;
            } else {
              nextB = spun;
            }
          }
        }
        next[first] = nextA;
        next[second] = nextB;
      }
    }
    return next;
  }

  static double _distanceToPocket(_PoolBall ball, Offset pocket) =>
      _hypot((ball.x - pocket.dx) * width, (ball.y - pocket.dy) * height);

  static double _hypot(double x, double y) => math.sqrt(x * x + y * y);
}

List<_PoolBall> _balls(Object? raw) {
  if (raw is! List) return const [];
  return raw.whereType<Map>().map((value) {
    return _PoolBall(
      id: (value['id'] as num?)?.toInt() ?? 0,
      x: (value['x'] as num?)?.toDouble() ?? .5,
      y: (value['y'] as num?)?.toDouble() ?? .5,
      pocketed: value['pocketed'] == true,
      vx: (value['vx'] as num?)?.toDouble() ?? 0,
      vy: (value['vy'] as num?)?.toDouble() ?? 0,
      sideSpin: (value['sideSpin'] as num?)?.toDouble() ?? 0,
      followSpin: (value['followSpin'] as num?)?.toDouble() ?? 0,
    );
  }).toList();
}

Offset _cue(List<_PoolBall> balls) {
  final cue = balls
      .where((ball) => ball.id == 0)
      .cast<_PoolBall?>()
      .firstOrNull;
  return cue == null ? const Offset(.23, .5) : Offset(cue.x, cue.y);
}

List<String> _strings(Object? raw) =>
    raw is List ? raw.map((value) => value.toString()).toList() : const [];

Map<String, Map<String, dynamic>> _maps(Object? raw) {
  if (raw is! Map) return const {};
  return raw.map(
    (key, value) => MapEntry(
      key.toString(),
      value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{},
    ),
  );
}

Map<String, String> _stringMap(Object? raw) {
  if (raw is! Map) return const {};
  return raw.map((key, value) => MapEntry(key.toString(), value.toString()));
}

bool _isPoolGroupBall(int id, String group) {
  if (group == 'solids') return id >= 1 && id <= 7;
  if (group == 'stripes') return id >= 9 && id <= 15;
  return false;
}

String _text(Object? value, [String fallback = '']) =>
    value is String && value.isNotEmpty ? value : fallback;

Color _ballColor(int id) {
  const colors = <Color>[
    Color(0xFFF7F7F0),
    Color(0xFFF5C416),
    Color(0xFF1677D1),
    Color(0xFFC22F32),
    Color(0xFF6D388F),
    Color(0xFFF06A22),
    Color(0xFF177E42),
    Color(0xFF7C2629),
    Color(0xFF141518),
    Color(0xFFF4C219),
    Color(0xFF247FD1),
    Color(0xFFD3383D),
    Color(0xFF704297),
    Color(0xFFF17427),
    Color(0xFF23854B),
    Color(0xFF843033),
  ];
  return colors[id.clamp(0, 15)];
}
