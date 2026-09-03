import 'dart:async';
import 'dart:math' as math;

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
  List<_PoolBall>? _replayBalls;
  bool _physicsActive = false;
  bool _receivedInitialRoomState = false;
  int _lastShotRevision = -1;
  int _replayTicks = 0;
  final _poolStageKey = GlobalKey<_WapiPool3DStageState>();

  Future<void> _call(String name, Map<String, dynamic> data) async {
    await _functions.httpsCallable(name).call<Map<String, dynamic>>(data);
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
    } on FirebaseFunctionsException catch (error) {
      _message(error.message ?? 'Impossible de quitter la table.', error: true);
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
    } on FirebaseFunctionsException catch (error) {
      _message(error.message ?? 'Cette position est impossible.', error: true);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _shoot({double? angle, double? power}) async {
    if (_sending) return;
    final shotAngle = angle ?? _angle;
    final shotPower = (power ?? _power).clamp(10, 100).toDouble();
    setState(() {
      _angle = shotAngle;
      _power = shotPower;
    });
    setState(() => _sending = true);
    try {
      await _call('submitPoolShot', {
        'roomId': widget.roomId,
        'angle': shotAngle,
        'power': shotPower,
        'sideSpin': _sideSpin,
        'followSpin': _followSpin,
      });
      // Animate the cue only after the authoritative table accepts the
      // gesture.  A rejected swipe can no longer look like a real shot.
      await _poolStageKey.currentState?.stroke();
    } on FirebaseFunctionsException catch (error) {
      _message(error.message ?? 'Le tir n’a pas pu être envoyé.', error: true);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _pullCue(Offset start, Offset end, double power) {
    if (_sending) return;
    final dx = start.dx - end.dx;
    final dy = start.dy - end.dy;
    if (math.sqrt(dx * dx + dy * dy) < .045) return;
    _shoot(angle: math.atan2(dy, dx), power: power);
  }

  static const _cueOptions = <_CueOption>[
    _CueOption('maple', 'Érable classique', 'Équilibrée · contrôle régulier'),
    _CueOption('walnut', 'Noyer signature', 'Précision · poignée cuir'),
    _CueOption('carbon', 'Carbone Vector', 'Réactive · finition compétition'),
    _CueOption(
      'obsidian',
      'Obsidienne WAPI',
      'Finition sombre · série fondateur',
    ),
  ];

  _CueOption get _selectedCue =>
      _cueOptions.firstWhere((cue) => cue.id == _cueStyle);

  @override
  void dispose() {
    _replayTimer?.cancel();
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
  ) {
    if (!_receivedInitialRoomState) {
      _receivedInitialRoomState = true;
      _lastShotRevision = revision;
      return;
    }
    if (revision <= _lastShotRevision || startBalls.length != 16) return;
    _lastShotRevision = revision;
    _startReplay(
      startBalls,
      authoritativeBalls,
      angle: angle,
      power: power,
      sideSpin: sideSpin,
      followSpin: followSpin,
    );
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
      _replayTicks += 1;
      if (!_PoolPhysics.isMoving(frame) || _replayTicks >= 1050) {
        timer.cancel();
        setState(() {
          _replayBalls = authoritative;
          _physicsActive = false;
        });
        return;
      }
      setState(() => _replayBalls = frame);
    });
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
            );
          }
        });
        final balls = _replayBalls ?? authoritativeBalls;
        final status = _text(data['status'], 'waiting');
        final turnUid = _text(data['turnUid']);
        final ballInHandUid = _text(data['ballInHandUid']);
        final active = status == 'playing';
        final myTurn = active && turnUid == widget.user.uid;
        final ballInHand = myTurn && ballInHandUid == widget.user.uid;
        final message = _text(data['lastAction'], 'Table WAPI prête.');
        return Scaffold(
          backgroundColor: const Color(0xFF031610),
          appBar: AppBar(
            backgroundColor: const Color(0xFF031610),
            foregroundColor: Colors.white,
            title: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'WAPI POOL',
                  style: TextStyle(fontWeight: FontWeight.w900),
                ),
                Text(
                  'Table ' + widget.roomId,
                  style: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF8DA9A0),
                  ),
                ),
              ],
            ),
            actions: [
              PopupMenuButton<String>(
                tooltip: 'Choisir une queue',
                icon: const Icon(Icons.sports_golf_rounded),
                onSelected: (style) => setState(() => _cueStyle = style),
                itemBuilder: (context) => _cueOptions
                    .map(
                      (cue) => PopupMenuItem<String>(
                        value: cue.id,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              cue.name,
                              style: const TextStyle(
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            Text(
                              cue.detail,
                              style: const TextStyle(fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                    )
                    .toList(),
              ),
              PopupMenuButton<String>(
                tooltip: 'Choisir le tapis',
                icon: const Icon(Icons.palette_outlined),
                onSelected: (theme) => setState(() => _tableTheme = theme),
                itemBuilder: (context) => const [
                  PopupMenuItem(
                    value: 'competitionBlue',
                    child: Text('Bleu compétition'),
                  ),
                  PopupMenuItem(value: 'navy', child: Text('Bleu nuit')),
                  PopupMenuItem(value: 'emerald', child: Text('Vert tournoi')),
                ],
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
            child: Column(
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
                  seconds: _seconds(data['turnDeadlineMs']),
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
                            _WapiPool3DStage(
                              key: _poolStageKey,
                              balls: balls,
                              angle: _angle,
                              power: _power,
                              sideSpin: _sideSpin,
                              followSpin: _followSpin,
                              moving: _physicsActive,
                              showAim: myTurn && !ballInHand && !_physicsActive,
                              cueInHand: ballInHand,
                              cueStyle: _cueStyle,
                              tableTheme: _tableTheme,
                              onTablePoint: (position, released) {
                                if (ballInHand) {
                                  if (released) _placeCueBall(position);
                                  return;
                                }
                                if (!myTurn) return;
                                final cue = _cue(balls);
                                setState(() {
                                  _angle = math.atan2(
                                    position.dy - cue.dy,
                                    position.dx - cue.dx,
                                  );
                                });
                              },
                              onCuePull: _pullCue,
                            ),
                            if (myTurn && !ballInHand)
                              Positioned(
                                left: 8,
                                top: 18,
                                bottom: 18,
                                child: _PoolPowerRail(
                                  value: _power,
                                  enabled: !_sending,
                                  onChanged: (value) =>
                                      setState(() => _power = value),
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
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
                  decoration: const BoxDecoration(
                    color: Color(0xFF071F18),
                    border: Border(top: BorderSide(color: Color(0xFF163B30))),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        message,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Color(0xFFE5F7EF),
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 8),
                      if (status == 'waiting')
                        Text(
                          'En attente d’un adversaire. Partagez le code ' +
                              widget.roomId +
                              '.',
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Color(0xFF8DA9A0)),
                        )
                      else if (status == 'finished')
                        const Text(
                          'Partie terminée. Vos résultats sont enregistrés dans votre profil joueur.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Color(0xFF8DA9A0)),
                        )
                      else if (ballInHand)
                        const Text(
                          'Bille en main : touchez le tapis pour placer la blanche.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Color(0xFF5EE7B7),
                            fontWeight: FontWeight.w700,
                          ),
                        )
                      else if (!myTurn)
                        const Text(
                          'Attendez votre tour pour jouer.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Color(0xFF8DA9A0)),
                        ),
                      if (myTurn && !ballInHand) ...[
                        const SizedBox(height: 8),
                        Text(
                          _selectedCue.name +
                              ' · ' +
                              _power.round().toString() +
                              '% · ' +
                              _spinLabel(_sideSpin, _followSpin),
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: Color(0xFFFFC83D),
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 6),
                        const Text(
                          'Tirez la queue vers l’arrière sur la table, puis relâchez pour frapper.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Color(0xFF8DA9A0),
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ],
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
      'group': group,
      'remaining': remaining,
    };
  }
}

class _PlayersBar extends StatelessWidget {
  const _PlayersBar({
    required this.left,
    required this.right,
    required this.activeUid,
    required this.seconds,
  });
  final Map<String, dynamic> left;
  final Map<String, dynamic> right;
  final String activeUid;
  final int seconds;

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
            seconds: left['uid'] == activeUid ? seconds : 0,
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
            seconds: right['uid'] == activeUid ? seconds : 0,
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
    required this.seconds,
    this.right = false,
  });
  final Map<String, dynamic> player;
  final bool active;
  final int seconds;
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
          Text(
            active
                ? 'À jouer · ' + seconds.toString() + ' s'
                : _text(player['group'], 'En attente'),
            style: TextStyle(
              color: active ? const Color(0xFF5EE7B7) : const Color(0xFF8DA9A0),
              fontSize: 11,
            ),
          ),
          if (active)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: LinearProgressIndicator(
                value: (seconds / 45).clamp(0.0, 1.0),
                minHeight: 4,
                color: Color(0xFF5EE7B7),
                backgroundColor: Color(0xFF174535),
              ),
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

class _ScoreBall extends StatelessWidget {
  const _ScoreBall({required this.id});
  final int id;
  @override
  Widget build(BuildContext context) => Container(
    width: 12,
    height: 12,
    decoration: BoxDecoration(
      shape: BoxShape.circle,
      gradient: RadialGradient(
        center: const Alignment(-.35, -.42),
        colors: [
          Colors.white,
          _ballColor(id),
          _ballColor(id).withValues(alpha: .92),
        ],
        stops: const [0, .26, 1],
      ),
      border: Border.all(color: Colors.white, width: id > 8 ? 2.5 : .6),
      boxShadow: const [
        BoxShadow(
          color: Color(0x99000000),
          blurRadius: 2,
          offset: Offset(0, 1),
        ),
      ],
    ),
  );
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
  });
  final double value;
  final bool enabled;
  final ValueChanged<double> onChanged;

  void _update(Offset position) {
    if (!enabled) return;
    const trackTop = 18.0;
    const trackBottom = 142.0;
    final ratio =
        ((trackBottom - position.dy.clamp(trackTop, trackBottom)) /
                (trackBottom - trackTop))
            .clamp(0.0, 1.0);
    onChanged(10 + ratio * 90);
  }

  @override
  Widget build(BuildContext context) => GestureDetector(
    onTapDown: (details) => _update(details.localPosition),
    onVerticalDragUpdate: (details) => _update(details.localPosition),
    child: CustomPaint(
      size: const Size(42, 160),
      painter: _PoolPowerRailPainter(value: value, enabled: enabled),
    ),
  );
}

class _PoolPowerRailPainter extends CustomPainter {
  const _PoolPowerRailPainter({required this.value, required this.enabled});
  final double value;
  final bool enabled;
  @override
  void paint(Canvas canvas, Size size) {
    final rail = RRect.fromRectAndRadius(
      Rect.fromLTWH(4, 2, size.width - 8, size.height - 4),
      const Radius.circular(18),
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
      Rect.fromLTWH(17, 18, 8, size.height - 36),
      const Radius.circular(6),
    );
    canvas.drawRRect(track, Paint()..color = const Color(0xFF321A20));
    final height = track.height * ((value - 10) / 90);
    final power = RRect.fromRectAndRadius(
      Rect.fromLTWH(track.left, track.bottom - height, track.width, height),
      const Radius.circular(6),
    );
    canvas.drawRRect(
      power,
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFFFFD049), Color(0xFFE53A43)],
          begin: Alignment.bottomCenter,
          end: Alignment.topCenter,
        ).createShader(power.outerRect),
    );
    final cueY = track.bottom - height;
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
      5,
      Paint()..color = const Color(0xFFE5484D),
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
            if (distance >= .045)
              widget.onCuePull(
                start,
                end,
                (distance * 260 + 12).clamp(10, 100),
              );
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

class _CueOption {
  const _CueOption(this.id, this.name, this.detail);
  final String id;
  final String name;
  final String detail;
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

  static List<_PoolBall> advance(List<_PoolBall> before, double delta) {
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

String _spinLabel(double side, double follow) {
  if (side.abs() < .12 && follow.abs() < .12) return 'centre';
  if (follow > .32) return side.abs() > .25 ? 'coulé avec effet' : 'coulé';
  if (follow < -.32) return side.abs() > .25 ? 'rétro avec effet' : 'rétro';
  return side.isNegative ? 'effet gauche' : 'effet droit';
}

int _seconds(Object? raw) {
  final deadline = raw is num ? raw.toInt() : 0;
  if (deadline < 1) return 0;
  return math.max(
    0,
    ((deadline - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
  );
}

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
