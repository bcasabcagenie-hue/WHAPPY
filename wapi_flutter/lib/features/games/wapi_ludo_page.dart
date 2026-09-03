import 'dart:math';

import 'package:flutter/material.dart';

class WapiLudoPage extends StatefulWidget {
  const WapiLudoPage({super.key});

  @override
  State<WapiLudoPage> createState() => _WapiLudoPageState();
}

class _WapiLudoPageState extends State<WapiLudoPage> {
  final _random = Random();
  final List<int> _positions = List<int>.filled(16, -1);
  final _playerNames = const ['Vous', 'IA bleue', 'IA verte', 'IA jaune'];
  int _activePlayer = 0;
  int _dieOne = 0;
  int _dieTwo = 0;
  int _pendingRoll = 0;
  bool _canLeaveHome = false;
  bool _rolling = false;
  int? _winner;
  String _message = 'Lancez les deux dés. Un 6 permet de sortir un pion.';

  List<int> get _movable {
    if (_pendingRoll == 0) return const [];
    return List<int>.generate(4, (slot) => _activePlayer * 4 + slot).where((
      index,
    ) {
      final position = _positions[index];
      if (position < 0) return _canLeaveHome;
      return position + _pendingRoll <= 57;
    }).toList();
  }

  Future<void> _roll() async {
    if (_rolling || _pendingRoll != 0 || _winner != null) return;
    setState(() {
      _rolling = true;
      _dieOne = 0;
      _dieTwo = 0;
      _message = _playerNames[_activePlayer] + ' lance les dés…';
    });
    for (var tick = 0; tick < 8; tick++) {
      await Future<void>.delayed(const Duration(milliseconds: 80));
      if (!mounted) return;
      setState(() {
        _dieOne = _random.nextInt(6) + 1;
        _dieTwo = _random.nextInt(6) + 1;
      });
    }
    if (!mounted) return;
    final total = _dieOne + _dieTwo;
    final mayLeave = _dieOne == 6 || _dieTwo == 6;
    setState(() {
      _rolling = false;
      _pendingRoll = total;
      _canLeaveHome = mayLeave;
    });
    if (_movable.isEmpty) {
      _advanceWithoutMove();
      return;
    }
    if (_activePlayer == 0) {
      setState(
        () => _message =
            'Total ' + total.toString() + ' : choisissez un de vos pions.',
      );
    } else {
      await Future<void>.delayed(const Duration(milliseconds: 450));
      if (mounted && _pendingRoll > 0 && _winner == null) {
        _move(_movable[_random.nextInt(_movable.length)]);
      }
    }
  }

  void _advanceWithoutMove() {
    final extra = _dieOne == _dieTwo || _canLeaveHome;
    setState(() {
      _pendingRoll = 0;
      _canLeaveHome = false;
      _message =
          _playerNames[_activePlayer] +
          ' ne peut pas jouer.' +
          (extra ? ' Relance.' : '');
      if (!extra) _activePlayer = (_activePlayer + 1) % 4;
    });
    _scheduleAi();
  }

  void _move(int pawn) {
    if (!_movable.contains(pawn) || _winner != null) return;
    final player = pawn ~/ 4;
    final old = _positions[pawn];
    final next = old < 0 ? 0 : old + _pendingRoll;
    final all = List<int>.from(_positions);
    all[pawn] = next;
    var capture = '';
    if (next < 52) {
      final absolute = _absolute(player, next);
      for (var index = 0; index < all.length; index++) {
        final opponent = index ~/ 4;
        final opponentPosition = all[index];
        if (opponent != player &&
            opponentPosition >= 0 &&
            opponentPosition < 52 &&
            _absolute(opponent, opponentPosition) == absolute &&
            !_safeSquares.contains(absolute)) {
          all[index] = -1;
          capture = ' Capture !';
        }
      }
    }
    final playerWon = List<int>.generate(
      4,
      (slot) => all[player * 4 + slot],
    ).every((position) => position == 57);
    final extra = _dieOne == _dieTwo || _canLeaveHome;
    setState(() {
      for (var index = 0; index < all.length; index++) {
        _positions[index] = all[index];
      }
      _pendingRoll = 0;
      _canLeaveHome = false;
      _message =
          _playerNames[player] +
          ' avance le pion ' +
          ((pawn % 4) + 1).toString() +
          '.' +
          capture;
      if (playerWon) {
        _winner = player;
        _message = 'Victoire de ' + _playerNames[player] + ' !';
      } else if (!extra) {
        _activePlayer = (_activePlayer + 1) % 4;
      }
    });
    _scheduleAi();
  }

  void _scheduleAi() {
    if (_winner != null || _activePlayer == 0) return;
    Future<void>.delayed(const Duration(milliseconds: 650), () {
      if (mounted &&
          _activePlayer != 0 &&
          _pendingRoll == 0 &&
          !_rolling &&
          _winner == null)
        _roll();
    });
  }

  void _restart() {
    setState(() {
      for (var index = 0; index < _positions.length; index++) {
        _positions[index] = -1;
      }
      _activePlayer = 0;
      _dieOne = 0;
      _dieTwo = 0;
      _pendingRoll = 0;
      _canLeaveHome = false;
      _winner = null;
      _message = 'Nouvelle partie. Lancez les deux dés.';
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F7FB),
    appBar: AppBar(
      title: const Text(
        'Ludo WAPI',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: _restart,
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Nouvelle partie',
        ),
      ],
    ),
    body: SafeArea(
      top: false,
      child: Column(
        children: [
          _LudoPlayers(
            activePlayer: _activePlayer,
            names: _playerNames,
            positions: _positions,
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: AspectRatio(
                aspectRatio: 1,
                child: GestureDetector(
                  onTapUp: (details) {
                    if (_activePlayer != 0 || _pendingRoll == 0) return;
                    final pawn = _hitPawn(details.localPosition, context);
                    if (pawn != null) _move(pawn);
                  },
                  child: CustomPaint(
                    painter: _LudoBoardPainter(
                      positions: _positions,
                      activePlayer: _activePlayer,
                      selectable: _movable.toSet(),
                    ),
                  ),
                ),
              ),
            ),
          ),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(18, 14, 18, 24),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(top: BorderSide(color: Color(0xFFE3E8F0))),
            ),
            child: Column(
              children: [
                Text(
                  _message,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF334155),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _Die(value: _dieOne, color: const Color(0xFFE53935)),
                    const SizedBox(width: 12),
                    _Die(value: _dieTwo, color: const Color(0xFF1E88E5)),
                    const SizedBox(width: 18),
                    FilledButton.icon(
                      onPressed:
                          _activePlayer == 0 &&
                              _pendingRoll == 0 &&
                              !_rolling &&
                              _winner == null
                          ? _roll
                          : null,
                      style: FilledButton.styleFrom(
                        minimumSize: const Size(150, 52),
                        backgroundColor: const Color(0xFF304FFE),
                      ),
                      icon: const Icon(Icons.casino_rounded),
                      label: Text(_rolling ? 'Lancement…' : 'Lancer les dés'),
                    ),
                  ],
                ),
                if (_winner != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: FilledButton(
                      onPressed: _restart,
                      child: const Text('Rejouer'),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    ),
  );

  int? _hitPawn(Offset point, BuildContext context) {
    final size = MediaQuery.sizeOf(context).width - 28;
    final cell = size / 15;
    for (final pawn in _movable) {
      final center = _pawnCenter(pawn, cell);
      if ((point - center).distance < cell * .55) return pawn;
    }
    return null;
  }

  Offset _pawnCenter(int pawn, double cell) {
    final player = pawn ~/ 4;
    final position = _positions[pawn];
    if (position < 0) return _homeSlot(player, pawn % 4, cell);
    if (position >= 52) return _finishSlot(player, position - 52, cell);
    final path = _boardPath(cell);
    return path[_absolute(player, position)];
  }
}

class _LudoPlayers extends StatelessWidget {
  const _LudoPlayers({
    required this.activePlayer,
    required this.names,
    required this.positions,
  });
  final int activePlayer;
  final List<String> names;
  final List<int> positions;

  @override
  Widget build(BuildContext context) {
    const colors = [
      Color(0xFFE53935),
      Color(0xFF1E88E5),
      Color(0xFF43A047),
      Color(0xFFF9A825),
    ];
    return Container(
      padding: const EdgeInsets.all(10),
      color: const Color(0xFF111827),
      child: Row(
        children: List.generate(4, (index) {
          final home = List<int>.generate(
            4,
            (slot) => positions[index * 4 + slot],
          ).where((value) => value >= 0).length;
          return Expanded(
            child: Column(
              children: [
                Container(
                  width: 9,
                  height: 9,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: colors[index],
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  names[index],
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: index == activePlayer
                        ? Colors.white
                        : const Color(0xFF9CA3AF),
                    fontWeight: index == activePlayer
                        ? FontWeight.w900
                        : FontWeight.w600,
                    fontSize: 11,
                  ),
                ),
                Text(
                  home.toString() + '/4',
                  style: const TextStyle(
                    color: Color(0xFF9CA3AF),
                    fontSize: 10,
                  ),
                ),
              ],
            ),
          );
        }),
      ),
    );
  }
}

class _Die extends StatelessWidget {
  const _Die({required this.value, required this.color});
  final int value;
  final Color color;
  @override
  Widget build(BuildContext context) => Container(
    width: 50,
    height: 50,
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: color, width: 2),
      boxShadow: const [
        BoxShadow(
          color: Color(0x1A0F172A),
          blurRadius: 7,
          offset: Offset(0, 3),
        ),
      ],
    ),
    alignment: Alignment.center,
    child: Text(
      value == 0 ? '•' : value.toString(),
      style: TextStyle(fontSize: 25, fontWeight: FontWeight.w900, color: color),
    ),
  );
}

class _LudoBoardPainter extends CustomPainter {
  const _LudoBoardPainter({
    required this.positions,
    required this.activePlayer,
    required this.selectable,
  });
  final List<int> positions;
  final int activePlayer;
  final Set<int> selectable;

  @override
  void paint(Canvas canvas, Size size) {
    final cell = size.width / 15;
    final board = Rect.fromLTWH(0, 0, size.width, size.height);
    canvas.drawRRect(
      RRect.fromRectAndRadius(board, const Radius.circular(20)),
      Paint()..color = Colors.white,
    );
    const colors = [
      Color(0xFFE53935),
      Color(0xFF1E88E5),
      Color(0xFF43A047),
      Color(0xFFF9A825),
    ];
    final homes = [
      Rect.fromLTWH(0, 0, cell * 6, cell * 6),
      Rect.fromLTWH(cell * 9, 0, cell * 6, cell * 6),
      Rect.fromLTWH(cell * 9, cell * 9, cell * 6, cell * 6),
      Rect.fromLTWH(0, cell * 9, cell * 6, cell * 6),
    ];
    for (var player = 0; player < 4; player++) {
      canvas.drawRect(
        homes[player],
        Paint()..color = colors[player].withValues(alpha: .22),
      );
      for (var slot = 0; slot < 4; slot++) {
        final center = _homeSlot(player, slot, cell);
        canvas.drawCircle(
          center,
          cell * .62,
          Paint()..color = colors[player].withValues(alpha: .26),
        );
      }
    }
    for (final center in _boardPath(cell)) {
      canvas.drawRect(
        Rect.fromCenter(center: center, width: cell, height: cell),
        Paint()..color = const Color(0xFFF7FAFC),
      );
      canvas.drawRect(
        Rect.fromCenter(center: center, width: cell, height: cell),
        Paint()
          ..color = const Color(0xFFE1E8F0)
          ..style = PaintingStyle.stroke,
      );
    }
    final lanes = [
      (Offset(cell * 7.5, cell * 6.5), Offset(0, cell)),
      (Offset(cell * 8.5, cell * 7.5), Offset(cell, 0)),
      (Offset(cell * 7.5, cell * 8.5), Offset(0, cell)),
      (Offset(cell * 6.5, cell * 7.5), Offset(cell, 0)),
    ];
    for (var player = 0; player < lanes.length; player++) {
      for (var step = 0; step < 6; step++) {
        final center = lanes[player].$1 + lanes[player].$2 * step.toDouble();
        canvas.drawRect(
          Rect.fromCenter(center: center, width: cell, height: cell),
          Paint()..color = colors[player].withValues(alpha: .43),
        );
      }
    }
    canvas.drawRect(
      Rect.fromCenter(
        center: Offset(cell * 7.5, cell * 7.5),
        width: cell,
        height: cell,
      ),
      Paint()..color = const Color(0xFF334155),
    );
    for (var pawn = 0; pawn < positions.length; pawn++) {
      final player = pawn ~/ 4;
      final position = positions[pawn];
      final center = position < 0
          ? _homeSlot(player, pawn % 4, cell)
          : position >= 52
          ? _finishSlot(player, position - 52, cell)
          : _boardPath(cell)[_absolute(player, position)];
      final paint = Paint()..color = colors[player];
      canvas.drawCircle(center, cell * .37, paint);
      canvas.drawCircle(
        center.translate(-cell * .10, -cell * .10),
        cell * .10,
        Paint()..color = Colors.white.withValues(alpha: .75),
      );
      if (selectable.contains(pawn)) {
        canvas.drawCircle(
          center,
          cell * .50,
          Paint()
            ..color = const Color(0xFF111827)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 3,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _LudoBoardPainter oldDelegate) =>
      oldDelegate.positions != positions ||
      oldDelegate.activePlayer != activePlayer ||
      oldDelegate.selectable != selectable;
}

const _safeSquares = {0, 8, 13, 21, 26, 34, 39, 47};
const _starts = [0, 13, 26, 39];

int _absolute(int player, int progress) => (_starts[player] + progress) % 52;

List<Offset> _boardPath(double cell) {
  final coordinates = <Offset>[];
  for (var x = 6; x <= 8; x++)
    coordinates.add(Offset((x + .5) * cell, 6.5 * cell));
  for (var y = 5; y >= 0; y--)
    coordinates.add(Offset(8.5 * cell, (y + .5) * cell));
  for (var x = 9; x <= 14; x++)
    coordinates.add(Offset((x + .5) * cell, .5 * cell));
  for (var y = 1; y <= 5; y++)
    coordinates.add(Offset(14.5 * cell, (y + .5) * cell));
  for (var x = 13; x >= 9; x--)
    coordinates.add(Offset((x + .5) * cell, 6.5 * cell));
  for (var x = 14; x >= 9; x--)
    coordinates.add(Offset((x + .5) * cell, 8.5 * cell));
  for (var y = 9; y <= 14; y++)
    coordinates.add(Offset(8.5 * cell, (y + .5) * cell));
  for (var x = 8; x >= 6; x--)
    coordinates.add(Offset((x + .5) * cell, 14.5 * cell));
  for (var y = 13; y >= 9; y--)
    coordinates.add(Offset(6.5 * cell, (y + .5) * cell));
  for (var y = 8; y >= 6; y--)
    coordinates.add(Offset(6.5 * cell, (y + .5) * cell));
  for (var x = 5; x >= 0; x--)
    coordinates.add(Offset((x + .5) * cell, 8.5 * cell));
  for (var y = 7; y >= 1; y--)
    coordinates.add(Offset(.5 * cell, (y + .5) * cell));
  return coordinates.take(52).toList();
}

Offset _homeSlot(int player, int slot, double cell) {
  final homes = [
    Offset(1.8, 1.8),
    Offset(10.8, 1.8),
    Offset(10.8, 10.8),
    Offset(1.8, 10.8),
  ];
  final base = homes[player];
  return Offset(
    (base.dx + (slot % 2) * 2.1) * cell,
    (base.dy + (slot ~/ 2) * 2.1) * cell,
  );
}

Offset _finishSlot(int player, int step, double cell) {
  final lanes = [
    (Offset(7.5, 6.5), Offset(0, 1)),
    (Offset(8.5, 7.5), Offset(-1, 0)),
    (Offset(7.5, 8.5), Offset(0, -1)),
    (Offset(6.5, 7.5), Offset(1, 0)),
  ];
  final lane = lanes[player];
  return Offset(
    (lane.$1.dx + lane.$2.dx * step) * cell,
    (lane.$1.dy + lane.$2.dy * step) * cell,
  );
}
