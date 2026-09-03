import 'dart:math';

import 'package:flutter/material.dart';

class WapiDraughtsPage extends StatefulWidget {
  const WapiDraughtsPage({super.key});

  @override
  State<WapiDraughtsPage> createState() => _WapiDraughtsPageState();
}

class _WapiDraughtsPageState extends State<WapiDraughtsPage> {
  final _random = Random();
  late List<_DraughtPiece?> _board;
  bool _whiteTurn = true;
  bool _versusAi = true;
  bool _thinking = false;
  bool _finished = false;
  int? _selected;
  List<_DraughtMove> _moves = const [];
  String _status = 'À vous de jouer · les prises sont obligatoires.';

  @override
  void initState() {
    super.initState();
    _reset();
  }

  void _reset() {
    _board = List<_DraughtPiece?>.filled(100, null);
    for (var row = 0; row < 4; row++) {
      for (var col = 0; col < 10; col++) {
        if ((row + col).isOdd)
          _board[row * 10 + col] = const _DraughtPiece(false);
      }
    }
    for (var row = 6; row < 10; row++) {
      for (var col = 0; col < 10; col++) {
        if ((row + col).isOdd)
          _board[row * 10 + col] = const _DraughtPiece(true);
      }
    }
    _whiteTurn = true;
    _thinking = false;
    _finished = false;
    _selected = null;
    _moves = const [];
    _status = 'À vous de jouer · les prises sont obligatoires.';
  }

  List<_DraughtMove> _movesForSide(bool white, {int? onlyFrom}) {
    final captures = <_DraughtMove>[];
    final quiet = <_DraughtMove>[];
    for (var index = 0; index < 100; index++) {
      if (onlyFrom != null && index != onlyFrom) continue;
      final piece = _board[index];
      if (piece == null || piece.white != white) continue;
      for (final move in _rawMoves(index)) {
        if (move.captured != null) {
          captures.add(move);
        } else {
          quiet.add(move);
        }
      }
    }
    return captures.isNotEmpty ? captures : quiet;
  }

  List<_DraughtMove> _rawMoves(int from) {
    final piece = _board[from];
    if (piece == null) return const [];
    final row = from ~/ 10;
    final col = from % 10;
    final moves = <_DraughtMove>[];
    final rowSteps = piece.queen ? [-1, 1] : [piece.white ? -1 : 1];
    for (final rowStep in rowSteps) {
      for (final colStep in [-1, 1]) {
        final nextRow = row + rowStep;
        final nextCol = col + colStep;
        if (!_inside(nextRow, nextCol)) continue;
        final next = nextRow * 10 + nextCol;
        final other = _board[next];
        if (other == null) {
          moves.add(_DraughtMove(from, next));
          continue;
        }
        final landingRow = nextRow + rowStep;
        final landingCol = nextCol + colStep;
        if (other.white != piece.white &&
            _inside(landingRow, landingCol) &&
            _board[landingRow * 10 + landingCol] == null) {
          moves.add(
            _DraughtMove(from, landingRow * 10 + landingCol, captured: next),
          );
        }
      }
    }
    return moves;
  }

  bool _inside(int row, int col) =>
      row >= 0 && row < 10 && col >= 0 && col < 10;

  void _tap(int square) {
    if (_finished || _thinking || (_versusAi && !_whiteTurn)) return;
    final chosenMove = _moves.where((move) => move.to == square).firstOrNull;
    if (chosenMove != null) {
      _play(chosenMove);
      return;
    }
    final piece = _board[square];
    if (piece != null && piece.white == _whiteTurn) {
      final candidates = _movesForSide(_whiteTurn);
      setState(() {
        _selected = square;
        _moves = candidates.where((move) => move.from == square).toList();
        _status = _moves.isEmpty
            ? 'Cette pièce ne peut pas jouer : une autre prise est obligatoire.'
            : (candidates.first.captured != null
                  ? 'Prise obligatoire · choisissez votre saut.'
                  : 'Choisissez la case d’arrivée.');
      });
    }
  }

  void _play(_DraughtMove move) {
    setState(() {
      final piece = _board[move.from]!;
      _board[move.from] = null;
      if (move.captured != null) _board[move.captured!] = null;
      final row = move.to ~/ 10;
      _board[move.to] = _DraughtPiece(
        piece.white,
        queen: piece.queen || row == 0 || row == 9,
      );
      if (move.captured != null) {
        final continuation = _movesForSide(
          _whiteTurn,
          onlyFrom: move.to,
        ).where((item) => item.captured != null).toList();
        if (continuation.isNotEmpty) {
          _selected = move.to;
          _moves = continuation;
          _status = 'Continuez la prise avec la même pièce.';
          return;
        }
      }
      _selected = null;
      _moves = const [];
      _whiteTurn = !_whiteTurn;
      _updateState();
    });
    if (_versusAi && !_whiteTurn && !_finished) _playAi();
  }

  void _updateState() {
    final available = _movesForSide(_whiteTurn);
    if (available.isEmpty) {
      _finished = true;
      _status = _whiteTurn
          ? 'Victoire des noirs · plus aucun coup blanc.'
          : 'Victoire des blancs · plus aucun coup noir.';
      return;
    }
    _status = _whiteTurn
        ? (available.first.captured != null
              ? 'À vous · une prise est obligatoire.'
              : 'À vous de jouer.')
        : 'Tour des noirs.';
  }

  void _playAi() {
    setState(() {
      _thinking = true;
      _status = 'L’IA analyse le damier…';
    });
    Future<void>.delayed(const Duration(milliseconds: 460), () {
      if (!mounted || _finished || _whiteTurn) return;
      final moves = _movesForSide(false);
      if (moves.isEmpty) {
        setState(() {
          _thinking = false;
          _updateState();
        });
        return;
      }
      final bestCaptures = moves
          .where((move) => move.captured != null)
          .toList();
      final chosen =
          (bestCaptures.isNotEmpty ? bestCaptures : moves)[_random.nextInt(
            bestCaptures.isNotEmpty ? bestCaptures.length : moves.length,
          )];
      setState(() {
        final piece = _board[chosen.from]!;
        _board[chosen.from] = null;
        if (chosen.captured != null) _board[chosen.captured!] = null;
        final row = chosen.to ~/ 10;
        _board[chosen.to] = _DraughtPiece(
          piece.white,
          queen: piece.queen || row == 0 || row == 9,
        );
        final continuation = chosen.captured == null
            ? const <_DraughtMove>[]
            : _movesForSide(
                false,
                onlyFrom: chosen.to,
              ).where((move) => move.captured != null).toList();
        if (continuation.isNotEmpty) {
          _thinking = false;
          final next = continuation[_random.nextInt(continuation.length)];
          _playAiContinuation(next);
          return;
        }
        _whiteTurn = true;
        _thinking = false;
        _updateState();
      });
    });
  }

  void _playAiContinuation(_DraughtMove move) {
    Future<void>.delayed(const Duration(milliseconds: 260), () {
      if (!mounted || _finished) return;
      setState(() {
        final piece = _board[move.from]!;
        _board[move.from] = null;
        if (move.captured != null) _board[move.captured!] = null;
        final row = move.to ~/ 10;
        _board[move.to] = _DraughtPiece(
          piece.white,
          queen: piece.queen || row == 0 || row == 9,
        );
        final more = _movesForSide(
          false,
          onlyFrom: move.to,
        ).where((item) => item.captured != null).toList();
        if (more.isNotEmpty) {
          final next = more[_random.nextInt(more.length)];
          _playAiContinuation(next);
          return;
        }
        _whiteTurn = true;
        _thinking = false;
        _updateState();
      });
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F7FB),
    appBar: AppBar(
      title: const Text(
        'Dames WAPI',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: () => setState(_reset),
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: SafeArea(
      top: false,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF13223C),
              borderRadius: BorderRadius.circular(22),
            ),
            child: Row(
              children: [
                const CircleAvatar(
                  backgroundColor: Color(0xFFF4E2B5),
                  child: Icon(Icons.person, color: Color(0xFF26374C)),
                ),
                const SizedBox(width: 10),
                const Expanded(
                  child: Text(
                    'Vous · Pions clairs',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                Switch(
                  value: _versusAi,
                  onChanged: (value) => setState(() {
                    _versusAi = value;
                    _reset();
                  }),
                  activeThumbColor: const Color(0xFF29D3AA),
                ),
                Text(
                  _versusAi ? 'IA' : '2 joueurs',
                  style: const TextStyle(color: Color(0xFFD1DDEC)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          AspectRatio(
            aspectRatio: 1,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: const Color(0xFF5D3928),
                borderRadius: BorderRadius.circular(12),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x33000000),
                    blurRadius: 18,
                    offset: Offset(0, 8),
                  ),
                ],
              ),
              child: Padding(
                padding: const EdgeInsets.all(6),
                child: GridView.builder(
                  physics: const NeverScrollableScrollPhysics(),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 10,
                  ),
                  itemCount: 100,
                  itemBuilder: (context, index) {
                    final dark = ((index ~/ 10) + index % 10).isOdd;
                    final selected = _selected == index;
                    final available = _moves.any((move) => move.to == index);
                    final piece = _board[index];
                    return InkWell(
                      onTap: dark ? () => _tap(index) : null,
                      child: Container(
                        decoration: BoxDecoration(
                          color: selected
                              ? const Color(0xFFF6C85E)
                              : available
                              ? const Color(0xFF8ECB7D)
                              : dark
                              ? const Color(0xFF74482E)
                              : const Color(0xFFF1D6A1),
                        ),
                        child: Center(
                          child: piece == null
                              ? (available
                                    ? const Icon(
                                        Icons.circle,
                                        size: 9,
                                        color: Color(0xAA264B2D),
                                      )
                                    : null)
                              : Container(
                                  width: 23,
                                  height: 23,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    gradient: LinearGradient(
                                      begin: Alignment.topLeft,
                                      end: Alignment.bottomRight,
                                      colors: piece.white
                                          ? const [
                                              Color(0xFFFFFFFF),
                                              Color(0xFFC7CDD6),
                                            ]
                                          : const [
                                              Color(0xFF344861),
                                              Color(0xFF0B1522),
                                            ],
                                    ),
                                    border: Border.all(
                                      color: piece.white
                                          ? Colors.white
                                          : const Color(0xFF07121E),
                                      width: 2,
                                    ),
                                    boxShadow: const [
                                      BoxShadow(
                                        color: Color(0x66000000),
                                        blurRadius: 2,
                                        offset: Offset(1, 2),
                                      ),
                                    ],
                                  ),
                                  child: piece.queen
                                      ? Icon(
                                          Icons.workspace_premium_rounded,
                                          size: 14,
                                          color: piece.white
                                              ? const Color(0xFFB58515)
                                              : const Color(0xFFFFD76A),
                                        )
                                      : null,
                                ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
          ),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Row(
              children: [
                Icon(
                  _finished
                      ? Icons.emoji_events_rounded
                      : Icons.info_outline_rounded,
                  color: const Color(0xFF0C8A69),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _status,
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF314257),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _DraughtPiece {
  const _DraughtPiece(this.white, {this.queen = false});
  final bool white;
  final bool queen;
}

class _DraughtMove {
  const _DraughtMove(this.from, this.to, {this.captured});
  final int from;
  final int to;
  final int? captured;
}

extension on Iterable<_DraughtMove> {
  _DraughtMove? get firstOrNull {
    for (final value in this) {
      return value;
    }
    return null;
  }
}
