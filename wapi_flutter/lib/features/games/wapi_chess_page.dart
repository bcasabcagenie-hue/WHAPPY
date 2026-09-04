import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'wapi_tabletop_3d.dart';

class WapiChessPage extends StatefulWidget {
  const WapiChessPage({super.key});

  @override
  State<WapiChessPage> createState() => _WapiChessPageState();
}

class _WapiChessPageState extends State<WapiChessPage> {
  final _random = Random();
  late List<_ChessPiece?> _board;
  bool _whiteTurn = true;
  bool _versusAi = true;
  bool _aiThinking = false;
  bool _finished = false;
  int? _selected;
  List<int> _moves = const [];
  int? _enPassantTarget;
  String _status = 'À vous de jouer · choisissez une pièce blanche.';

  @override
  void initState() {
    super.initState();
    _reset();
  }

  void _reset() {
    _board = List<_ChessPiece?>.filled(64, null);
    const back = ['r', 'n', 'b', 'q', 'k', 'b', 'n', 'r'];
    for (var col = 0; col < 8; col++) {
      _board[col] = _ChessPiece(back[col], false);
      _board[8 + col] = _ChessPiece('p', false);
      _board[48 + col] = _ChessPiece('p', true);
      _board[56 + col] = _ChessPiece(back[col], true);
    }
    _whiteTurn = true;
    _aiThinking = false;
    _finished = false;
    _selected = null;
    _moves = const [];
    _enPassantTarget = null;
    _status = 'À vous de jouer · choisissez une pièce blanche.';
  }

  void _tapSquare(int square) {
    if (_finished || _aiThinking || (_versusAi && !_whiteTurn)) return;
    final piece = _board[square];
    if (_selected != null && _moves.contains(square)) {
      _playMove(_selected!, square);
      return;
    }
    if (piece != null && piece.white == _whiteTurn) {
      setState(() {
        _selected = square;
        _moves = _legalMoves(square);
        _status = _pieceName(piece) + ' sélectionné.';
      });
    } else {
      setState(() {
        _selected = null;
        _moves = const [];
      });
    }
  }

  void _playMove(int from, int to) {
    setState(() {
      _applyMove(_board, from, to);
      _selected = null;
      _moves = const [];
      _whiteTurn = !_whiteTurn;
      _updateStateAfterMove();
    });
    if (_versusAi && !_whiteTurn && !_finished) _playAi();
  }

  void _playAi() {
    setState(() {
      _aiThinking = true;
      _status = 'L’IA prépare son coup…';
    });
    Future<void>.delayed(const Duration(milliseconds: 760), () {
      if (!mounted || _finished || _whiteTurn) return;
      final candidates = _allMovesOn(_board, false);
      if (candidates.isEmpty) {
        setState(() {
          _aiThinking = false;
          _updateStateAfterMove();
        });
        return;
      }
      final scored = candidates.map((move) {
        final board = _copyBoard(_board);
        _applyMove(board, move.from, move.to, updateEnPassant: false);
        // Two complete plies make the IA defend pieces and anticipate the
        // player's reply instead of blindly taking the largest target.
        return (move: move, score: _search(board, 2, true, -1000000, 1000000));
      }).toList()..sort((a, b) => b.score.compareTo(a.score));
      final topScore = scored.first.score;
      final equivalent = scored
          .takeWhile((entry) => entry.score >= topScore - 4)
          .toList(growable: false);
      final chosen = equivalent[_random.nextInt(equivalent.length)].move;
      setState(() {
        _applyMove(_board, chosen.from, chosen.to);
        _whiteTurn = true;
        _aiThinking = false;
        _updateStateAfterMove();
      });
      HapticFeedback.selectionClick();
    });
  }

  int _search(
    List<_ChessPiece?> board,
    int depth,
    bool whiteTurn,
    int alpha,
    int beta,
  ) {
    if (depth == 0) return _evaluate(board);
    final moves = _allMovesOn(board, whiteTurn);
    if (moves.isEmpty) {
      if (_inCheck(board, whiteTurn)) return whiteTurn ? 100000 : -100000;
      return 0;
    }
    var low = alpha;
    var high = beta;
    if (!whiteTurn) {
      var best = -1000000;
      for (final move in moves) {
        final next = _copyBoard(board);
        _applyMove(next, move.from, move.to, updateEnPassant: false);
        best = max(best, _search(next, depth - 1, true, low, high));
        low = max(low, best);
        if (high <= low) break;
      }
      return best;
    }
    var best = 1000000;
    for (final move in moves) {
      final next = _copyBoard(board);
      _applyMove(next, move.from, move.to, updateEnPassant: false);
      best = min(best, _search(next, depth - 1, false, low, high));
      high = min(high, best);
      if (high <= low) break;
    }
    return best;
  }

  int _evaluate(List<_ChessPiece?> board) {
    var score = 0;
    for (var square = 0; square < board.length; square += 1) {
      final piece = board[square];
      if (piece == null) continue;
      final row = square ~/ 8;
      final col = square % 8;
      final centre = 7 - ((row - 3.5).abs() + (col - 3.5).abs()).round();
      final development = piece.type == 'p'
          ? (piece.white ? 6 - row : row - 1) * 2
          : centre;
      final value = _value(piece) * 100 + development;
      score += piece.white ? -value : value;
    }
    return score;
  }

  void _updateStateAfterMove() {
    final side = _whiteTurn;
    final moves = _allMoves(side);
    final checked = _inCheck(_board, side);
    if (moves.isEmpty) {
      _finished = true;
      _status = checked
          ? (side
                ? 'Échec et mat · les noirs gagnent.'
                : 'Échec et mat · les blancs gagnent.')
          : 'Partie nulle par pat.';
      return;
    }
    final sideName = side ? 'Blancs' : 'Noirs';
    _status = checked
        ? 'Échec aux ' + sideName.toLowerCase() + ' · protégez votre roi.'
        : 'Tour des ' + sideName.toLowerCase() + '.';
  }

  List<_ChessMove> _allMoves(bool white) {
    return _allMovesOn(_board, white);
  }

  List<_ChessMove> _allMovesOn(List<_ChessPiece?> board, bool white) {
    final moves = <_ChessMove>[];
    for (var square = 0; square < 64; square++) {
      final piece = board[square];
      if (piece != null && piece.white == white) {
        for (final target in _legalMovesOn(board, square)) {
          moves.add(_ChessMove(square, target));
        }
      }
    }
    return moves;
  }

  List<int> _legalMoves(int from) => _legalMovesOn(_board, from);

  List<int> _legalMovesOn(List<_ChessPiece?> board, int from) {
    final piece = board[from];
    if (piece == null) return const [];
    return _pseudoMoves(board, from, attacksOnly: false).where((to) {
      if (board[to]?.type == 'k') return false;
      final copy = _copyBoard(board);
      _applyMove(copy, from, to, updateEnPassant: false);
      return !_inCheck(copy, piece.white);
    }).toList();
  }

  List<int> _pseudoMoves(
    List<_ChessPiece?> board,
    int from, {
    required bool attacksOnly,
  }) {
    final piece = board[from];
    if (piece == null) return const [];
    final row = from ~/ 8;
    final col = from % 8;
    final moves = <int>[];
    void add(int target, {bool attack = false}) {
      if (target < 0 || target >= 64) return;
      final targetRow = target ~/ 8;
      if ((target % 8 - col).abs() > 2 || (targetRow - row).abs() > 2) return;
      final other = board[target];
      if (other == null || other.white != piece.white || attack) {
        if (other == null || other.white != piece.white || attacksOnly)
          moves.add(target);
      }
    }

    void ray(int rowStep, int colStep) {
      var nextRow = row + rowStep;
      var nextCol = col + colStep;
      while (nextRow >= 0 && nextRow < 8 && nextCol >= 0 && nextCol < 8) {
        final target = nextRow * 8 + nextCol;
        final other = board[target];
        if (other == null) {
          moves.add(target);
        } else {
          if (other.white != piece.white || attacksOnly) moves.add(target);
          break;
        }
        nextRow += rowStep;
        nextCol += colStep;
      }
    }

    switch (piece.type) {
      case 'p':
        final direction = piece.white ? -1 : 1;
        final startRow = piece.white ? 6 : 1;
        final forward = (row + direction) * 8 + col;
        if (!attacksOnly &&
            row + direction >= 0 &&
            row + direction < 8 &&
            board[forward] == null) {
          moves.add(forward);
          final doubleForward = (row + 2 * direction) * 8 + col;
          if (row == startRow && board[doubleForward] == null)
            moves.add(doubleForward);
        }
        for (final delta in [-1, 1]) {
          final targetCol = col + delta;
          final targetRow = row + direction;
          if (targetCol < 0 || targetCol > 7 || targetRow < 0 || targetRow > 7)
            continue;
          final target = targetRow * 8 + targetCol;
          if (attacksOnly) {
            moves.add(target);
          } else if (board[target] != null &&
              board[target]!.white != piece.white) {
            moves.add(target);
          } else if (target == _enPassantTarget) {
            moves.add(target);
          }
        }
        break;
      case 'n':
        for (final offset in const [
          [-2, -1],
          [-2, 1],
          [-1, -2],
          [-1, 2],
          [1, -2],
          [1, 2],
          [2, -1],
          [2, 1],
        ]) {
          final nextRow = row + offset[0];
          final nextCol = col + offset[1];
          if (nextRow >= 0 && nextRow < 8 && nextCol >= 0 && nextCol < 8) {
            add(nextRow * 8 + nextCol, attack: attacksOnly);
          }
        }
        break;
      case 'b':
        ray(-1, -1);
        ray(-1, 1);
        ray(1, -1);
        ray(1, 1);
        break;
      case 'r':
        ray(-1, 0);
        ray(1, 0);
        ray(0, -1);
        ray(0, 1);
        break;
      case 'q':
        for (final direction in const [
          [-1, -1],
          [-1, 0],
          [-1, 1],
          [0, -1],
          [0, 1],
          [1, -1],
          [1, 0],
          [1, 1],
        ]) {
          ray(direction[0], direction[1]);
        }
        break;
      case 'k':
        for (var rowDelta = -1; rowDelta <= 1; rowDelta++) {
          for (var colDelta = -1; colDelta <= 1; colDelta++) {
            if (rowDelta == 0 && colDelta == 0) continue;
            final nextRow = row + rowDelta;
            final nextCol = col + colDelta;
            if (nextRow >= 0 && nextRow < 8 && nextCol >= 0 && nextCol < 8) {
              add(nextRow * 8 + nextCol, attack: attacksOnly);
            }
          }
        }
        if (!attacksOnly && !piece.moved && !_inCheck(board, piece.white)) {
          final rank = piece.white ? 7 : 0;
          final kingSideRook = rank * 8 + 7;
          if (board[kingSideRook]?.type == 'r' &&
              board[kingSideRook]?.moved == false &&
              board[rank * 8 + 5] == null &&
              board[rank * 8 + 6] == null &&
              !_squareAttacked(board, rank * 8 + 5, !piece.white) &&
              !_squareAttacked(board, rank * 8 + 6, !piece.white)) {
            moves.add(rank * 8 + 6);
          }
          final queenSideRook = rank * 8;
          if (board[queenSideRook]?.type == 'r' &&
              board[queenSideRook]?.moved == false &&
              board[rank * 8 + 1] == null &&
              board[rank * 8 + 2] == null &&
              board[rank * 8 + 3] == null &&
              !_squareAttacked(board, rank * 8 + 3, !piece.white) &&
              !_squareAttacked(board, rank * 8 + 2, !piece.white)) {
            moves.add(rank * 8 + 2);
          }
        }
        break;
    }
    return moves;
  }

  bool _inCheck(List<_ChessPiece?> board, bool white) {
    final king = board.indexWhere(
      (piece) => piece?.white == white && piece?.type == 'k',
    );
    return king >= 0 && _squareAttacked(board, king, !white);
  }

  bool _squareAttacked(List<_ChessPiece?> board, int square, bool byWhite) {
    for (var index = 0; index < 64; index++) {
      final piece = board[index];
      if (piece != null && piece.white == byWhite) {
        if (_pseudoMoves(board, index, attacksOnly: true).contains(square))
          return true;
      }
    }
    return false;
  }

  void _applyMove(
    List<_ChessPiece?> board,
    int from,
    int to, {
    bool updateEnPassant = true,
  }) {
    final piece = board[from];
    if (piece == null) return;
    final fromRow = from ~/ 8;
    final toRow = to ~/ 8;
    if (piece.type == 'p' && to == _enPassantTarget && board[to] == null) {
      final captured = to + (piece.white ? 8 : -8);
      board[captured] = null;
    }
    if (piece.type == 'k' && (to - from).abs() == 2) {
      final rookFrom = to > from ? fromRow * 8 + 7 : fromRow * 8;
      final rookTo = to > from ? to - 1 : to + 1;
      final rook = board[rookFrom];
      board[rookFrom] = null;
      board[rookTo] = rook?.copyWith(moved: true);
    }
    board[from] = null;
    var next = piece.copyWith(moved: true);
    if (piece.type == 'p' && (toRow == 0 || toRow == 7))
      next = _ChessPiece('q', piece.white, moved: true);
    board[to] = next;
    if (updateEnPassant) {
      _enPassantTarget = null;
      if (piece.type == 'p' && (toRow - fromRow).abs() == 2) {
        _enPassantTarget = ((fromRow + toRow) ~/ 2) * 8 + (from % 8);
      }
    }
  }

  List<_ChessPiece?> _copyBoard(List<_ChessPiece?> source) =>
      source.map((piece) => piece?.copyWith()).toList();

  int _value(_ChessPiece piece) => switch (piece.type) {
    'p' => 1,
    'n' || 'b' => 3,
    'r' => 5,
    'q' => 9,
    _ => 100,
  };

  String _pieceName(_ChessPiece piece) => switch (piece.type) {
    'p' => 'Pion',
    'n' => 'Cavalier',
    'b' => 'Fou',
    'r' => 'Tour',
    'q' => 'Dame',
    _ => 'Roi',
  };

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF4F7FB),
    appBar: AppBar(
      title: const Text(
        'Échecs WAPI',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: () => setState(_reset),
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Nouvelle partie',
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
              color: const Color(0xFF14213D),
              borderRadius: BorderRadius.circular(22),
            ),
            child: Row(
              children: [
                const CircleAvatar(
                  backgroundColor: Color(0xFF1FA2FF),
                  child: Icon(Icons.person, color: Colors.white),
                ),
                const SizedBox(width: 10),
                const Expanded(
                  child: Text(
                    'Vous · Blancs',
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
                  activeThumbColor: const Color(0xFF27D3A2),
                ),
                Text(
                  _versusAi ? 'IA' : '2 joueurs',
                  style: const TextStyle(color: Color(0xFFC7D6EA)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          AspectRatio(
            aspectRatio: 1,
            child: WapiTabletop3D.strategy(
              scene: 'chess',
              board: _board.map((piece) => piece?.symbol ?? '').toList(),
              selected: _selected ?? -1,
              legalTargets: _moves.toSet(),
              onSquare: _tapSquare,
              fallback: DecoratedBox(
                decoration: BoxDecoration(
                  color: const Color(0xFF8C5D3D),
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x330E1726),
                      blurRadius: 18,
                      offset: Offset(0, 9),
                    ),
                  ],
                ),
                child: Padding(
                  padding: const EdgeInsets.all(6),
                  child: GridView.builder(
                    physics: const NeverScrollableScrollPhysics(),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 8,
                        ),
                    itemCount: 64,
                    itemBuilder: (context, index) {
                      final light = ((index ~/ 8) + index % 8).isEven;
                      final selected = _selected == index;
                      final legal = _moves.contains(index);
                      final piece = _board[index];
                      return InkWell(
                        onTap: () => _tapSquare(index),
                        child: Container(
                          decoration: BoxDecoration(
                            color: selected
                                ? const Color(0xFFF5C451)
                                : legal
                                ? const Color(0xFF9BD77B)
                                : light
                                ? const Color(0xFFF0D9B5)
                                : const Color(0xFFB58863),
                          ),
                          child: Center(
                            child: piece == null
                                ? (legal
                                      ? const Icon(
                                          Icons.circle,
                                          size: 13,
                                          color: Color(0xCC255A38),
                                        )
                                      : null)
                                : Text(
                                    piece.symbol,
                                    style: TextStyle(
                                      fontSize: 35,
                                      color: piece.white
                                          ? Colors.white
                                          : const Color(0xFF16202B),
                                      shadows: const [
                                        Shadow(
                                          color: Color(0x88000000),
                                          blurRadius: 2,
                                          offset: Offset(1, 2),
                                        ),
                                      ],
                                    ),
                                  ),
                          ),
                        ),
                      );
                    },
                  ),
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
                  color: const Color(0xFF1677C8),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _status,
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF24364B),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          const Text(
            'Déplacez une pièce pour afficher ses coups légaux. Les roques, prises en passant, échecs et promotions sont gérés.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Color(0xFF64748B), fontSize: 12),
          ),
        ],
      ),
    ),
  );
}

class _ChessPiece {
  const _ChessPiece(this.type, this.white, {this.moved = false});
  final String type;
  final bool white;
  final bool moved;

  String get symbol {
    const whiteSymbols = {
      'p': '♙',
      'n': '♘',
      'b': '♗',
      'r': '♖',
      'q': '♕',
      'k': '♔',
    };
    const blackSymbols = {
      'p': '♟',
      'n': '♞',
      'b': '♝',
      'r': '♜',
      'q': '♛',
      'k': '♚',
    };
    return (white ? whiteSymbols : blackSymbols)[type] ?? '';
  }

  _ChessPiece copyWith({bool? moved}) =>
      _ChessPiece(type, white, moved: moved ?? this.moved);
}

class _ChessMove {
  const _ChessMove(this.from, this.to);
  final int from;
  final int to;
}
