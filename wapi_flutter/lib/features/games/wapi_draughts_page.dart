import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
        if ((row + col).isOdd) {
          _board[row * 10 + col] = const _DraughtPiece(false);
        }
      }
    }
    for (var row = 6; row < 10; row++) {
      for (var col = 0; col < 10; col++) {
        if ((row + col).isOdd) {
          _board[row * 10 + col] = const _DraughtPiece(true);
        }
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
      HapticFeedback.mediumImpact();
      _play(chosenMove);
      return;
    }
    final piece = _board[square];
    if (piece != null && piece.white == _whiteTurn) {
      HapticFeedback.selectionClick();
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

  int get _whitePieces =>
      _board.whereType<_DraughtPiece>().where((p) => p.white).length;
  int get _blackPieces =>
      _board.whereType<_DraughtPiece>().where((p) => !p.white).length;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFF071016),
    appBar: AppBar(
      backgroundColor: const Color(0xFF071016),
      foregroundColor: Colors.white,
      elevation: 0,
      title: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Dames WAPI', style: TextStyle(fontWeight: FontWeight.w900)),
          Text(
            'Table classique · 10 × 10',
            style: TextStyle(fontSize: 11, color: Color(0xFF9BB4C6)),
          ),
        ],
      ),
      actions: [
        IconButton(
          tooltip: 'Nouvelle partie',
          onPressed: () => setState(_reset),
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFF0B1A25), Color(0xFF071016)],
        ),
      ),
      child: SafeArea(
        top: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            _DraughtsPlayerStrip(
              icon: Icons.person_rounded,
              name: 'Vous',
              detail: 'Pions ivoire',
              count: _whitePieces,
              active: _whiteTurn && !_finished,
              light: true,
            ),
            const SizedBox(height: 8),
            _DraughtsPlayerStrip(
              icon: _versusAi ? Icons.smart_toy_rounded : Icons.person_rounded,
              name: _versusAi ? 'WAPI IA' : 'Joueur noir',
              detail: _thinking ? 'Analyse du coup…' : 'Pions ébène',
              count: _blackPieces,
              active: !_whiteTurn && !_finished,
              light: false,
            ),
            const SizedBox(height: 18),
            AspectRatio(
              aspectRatio: 1,
              child: _DraughtsBoard(
                board: _board,
                selected: _selected,
                moves: _moves,
                onTap: _tap,
              ),
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(15),
              decoration: BoxDecoration(
                color: const Color(0xFF10232F),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: const Color(0xFF214153)),
              ),
              child: Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: _finished
                          ? const Color(0xFFB98528)
                          : const Color(0xFF167E78),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      _finished
                          ? Icons.emoji_events_rounded
                          : Icons.touch_app_rounded,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _status,
                      style: const TextStyle(
                        color: Color(0xFFE6F0F5),
                        height: 1.25,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFF0D1B25),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: const Color(0xFF1B3443)),
              ),
              child: Row(
                children: [
                  const Icon(
                    Icons.smart_toy_outlined,
                    color: Color(0xFF63D6C9),
                  ),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text(
                      'Partie contre l’IA',
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
                    activeThumbColor: const Color(0xFF69E0D2),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _DraughtsPlayerStrip extends StatelessWidget {
  const _DraughtsPlayerStrip({
    required this.icon,
    required this.name,
    required this.detail,
    required this.count,
    required this.active,
    required this.light,
  });

  final IconData icon;
  final String name;
  final String detail;
  final int count;
  final bool active;
  final bool light;

  @override
  Widget build(BuildContext context) => AnimatedContainer(
    duration: const Duration(milliseconds: 220),
    padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
    decoration: BoxDecoration(
      color: active ? const Color(0xFF163744) : const Color(0xFF0D1C27),
      borderRadius: BorderRadius.circular(18),
      border: Border.all(
        color: active ? const Color(0xFF47D5C6) : const Color(0xFF1D3544),
        width: active ? 1.5 : 1,
      ),
      boxShadow: active
          ? const [BoxShadow(color: Color(0x3347D5C6), blurRadius: 14)]
          : null,
    ),
    child: Row(
      children: [
        Container(
          width: 34,
          height: 34,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: light
                  ? const [Color(0xFFFFFFFF), Color(0xFFC8D1D7)]
                  : const [Color(0xFF314754), Color(0xFF071218)],
            ),
            border: Border.all(color: const Color(0x558DE0D8)),
          ),
          child: Icon(
            icon,
            size: 18,
            color: light ? const Color(0xFF31434B) : const Color(0xFFE2F3F7),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                name,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w900,
                ),
              ),
              Text(
                detail,
                style: const TextStyle(color: Color(0xFF9FB9C7), fontSize: 12),
              ),
            ],
          ),
        ),
        Text(
          '$count',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(width: 3),
        const Text(
          'pions',
          style: TextStyle(color: Color(0xFF9FB9C7), fontSize: 11),
        ),
      ],
    ),
  );
}

class _DraughtsBoard extends StatelessWidget {
  const _DraughtsBoard({
    required this.board,
    required this.selected,
    required this.moves,
    required this.onTap,
  });

  final List<_DraughtPiece?> board;
  final int? selected;
  final List<_DraughtMove> moves;
  final ValueChanged<int> onTap;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(11),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFD1A366), Color(0xFF77411F), Color(0xFF2A160F)],
        stops: [.0, .46, 1],
      ),
      borderRadius: BorderRadius.circular(24),
      border: Border.all(color: const Color(0xFFE7C58B), width: 1.5),
      boxShadow: const [
        BoxShadow(
          color: Color(0xA8000000),
          blurRadius: 24,
          offset: Offset(0, 14),
        ),
        BoxShadow(color: Color(0x553B1B0C), blurRadius: 2, spreadRadius: 3),
      ],
    ),
    child: DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xFF140F0C),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: const Color(0xFF382519), width: 3),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final pieceSize = constraints.maxWidth / 10 * .78;
          return ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: GridView.builder(
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 10,
              ),
              itemCount: 100,
              itemBuilder: (context, index) {
                final dark = ((index ~/ 10) + index % 10).isOdd;
                final isSelected = selected == index;
                final canMove = moves.any((move) => move.to == index);
                final piece = board[index];
                return InkWell(
                  onTap: dark ? () => onTap(index) : null,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 160),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: isSelected
                            ? const [Color(0xFFF5C45D), Color(0xFFC67822)]
                            : canMove
                            ? const [Color(0xFF54806E), Color(0xFF284B40)]
                            : dark
                            ? const [Color(0xFF3A2119), Color(0xFF1B100E)]
                            : const [Color(0xFFE5BF82), Color(0xFFB97B45)],
                      ),
                    ),
                    child: Center(
                      child: piece == null
                          ? canMove
                                ? Container(
                                    width: pieceSize * .22,
                                    height: pieceSize * .22,
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: const Color(0xFFF6DBA6),
                                      boxShadow: const [
                                        BoxShadow(
                                          color: Color(0x99000000),
                                          blurRadius: 4,
                                        ),
                                      ],
                                    ),
                                  )
                                : null
                          : _DraughtChecker(
                              piece: piece,
                              size: pieceSize,
                              selected: isSelected,
                            ),
                    ),
                  ),
                );
              },
            ),
          );
        },
      ),
    ),
  );
}

class _DraughtChecker extends StatelessWidget {
  const _DraughtChecker({
    required this.piece,
    required this.size,
    required this.selected,
  });

  final _DraughtPiece piece;
  final double size;
  final bool selected;

  @override
  Widget build(BuildContext context) => AnimatedScale(
    duration: const Duration(milliseconds: 160),
    scale: selected ? 1.13 : 1,
    child: SizedBox(
      width: size,
      height: size,
      child: DecoratedBox(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: piece.white
                ? const [
                    Color(0xFFFFFFFF),
                    Color(0xFFD6D5CB),
                    Color(0xFF828C92),
                  ]
                : const [
                    Color(0xFF4F6068),
                    Color(0xFF152129),
                    Color(0xFF020608),
                  ],
          ),
          border: Border.all(
            color: piece.white
                ? const Color(0xFFF9F1D9)
                : const Color(0xFF78909A),
            width: size * .055,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: .7),
              blurRadius: size * .13,
              offset: Offset(size * .05, size * .11),
            ),
            BoxShadow(
              color: piece.white
                  ? const Color(0x77FFFFFF)
                  : const Color(0x3349D5DF),
              blurRadius: size * .06,
              offset: Offset(-size * .05, -size * .05),
            ),
          ],
        ),
        child: Center(
          child: Container(
            width: size * .63,
            height: size * .63,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                color: piece.white
                    ? const Color(0x8872797B)
                    : const Color(0xAA9CB4BE),
                width: size * .035,
              ),
              gradient: RadialGradient(
                center: const Alignment(-.3, -.38),
                colors: piece.white
                    ? const [Color(0xFFFFFFFF), Color(0x00FFFFFF)]
                    : const [Color(0xFF6C858F), Color(0x00000000)],
              ),
            ),
            child: piece.queen
                ? Icon(
                    Icons.workspace_premium_rounded,
                    size: size * .42,
                    color: piece.white
                        ? const Color(0xFF9E6815)
                        : const Color(0xFFF7C95A),
                    shadows: const [
                      Shadow(
                        color: Color(0x99000000),
                        blurRadius: 3,
                        offset: Offset(0, 1),
                      ),
                    ],
                  )
                : null,
          ),
        ),
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
