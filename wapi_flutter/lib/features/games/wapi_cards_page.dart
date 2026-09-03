import 'dart:math';

import 'package:flutter/material.dart';

class WapiCardsPage extends StatefulWidget {
  const WapiCardsPage({super.key});

  @override
  State<WapiCardsPage> createState() => _WapiCardsPageState();
}

class _WapiCardsPageState extends State<WapiCardsPage> {
  final _random = Random();
  List<int> _deck = const [];
  bool _versusAi = true;
  bool _busy = false;
  int _round = 0;
  int _playerScore = 0;
  int _opponentScore = 0;
  int? _playerCard;
  int? _opponentCard;
  String _status = 'Tirez une carte pour démarrer la partie.';

  @override
  void initState() {
    super.initState();
    _newGame();
  }

  void _newGame() {
    _deck = List<int>.generate(52, (index) => index)..shuffle(_random);
    _round = 0;
    _playerScore = 0;
    _opponentScore = 0;
    _playerCard = null;
    _opponentCard = null;
    _busy = false;
    _status = 'Tirez une carte pour démarrer la partie.';
  }

  Future<void> _draw() async {
    if (_busy || _round >= 26 || _deck.length < 2) return;
    setState(() {
      _busy = true;
      _playerCard = _deck[_round * 2];
      _opponentCard = null;
      _status = _versusAi ? 'L’IA tire sa carte…' : 'Le second joueur tire sa carte.';
    });
    await Future<void>.delayed(const Duration(milliseconds: 520));
    if (!mounted) return;
    final player = _playerCard!;
    final opponent = _deck[_round * 2 + 1];
    final playerValue = player % 13;
    final opponentValue = opponent % 13;
    var status = '';
    var playerScore = _playerScore;
    var opponentScore = _opponentScore;
    if (playerValue > opponentValue) {
      playerScore += 1;
      status = 'Point pour vous.';
    } else if (opponentValue > playerValue) {
      opponentScore += 1;
      status = _versusAi ? 'Point pour l’IA.' : 'Point pour le joueur 2.';
    } else {
      status = 'Égalité : aucune carte ne marque ce tour.';
    }
    final nextRound = _round + 1;
    if (nextRound == 26) {
      status = playerScore == opponentScore
          ? 'Partie nulle · ' + playerScore.toString() + ' à ' + opponentScore.toString() + '.'
          : playerScore > opponentScore
          ? 'Victoire WAPI · ' + playerScore.toString() + ' à ' + opponentScore.toString() + ' !'
          : 'Défaite · ' + playerScore.toString() + ' à ' + opponentScore.toString() + '.';
    }
    setState(() {
      _opponentCard = opponent;
      _playerScore = playerScore;
      _opponentScore = opponentScore;
      _round = nextRound;
      _busy = false;
      _status = status;
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFF071C28),
    appBar: AppBar(
      backgroundColor: const Color(0xFF071C28),
      foregroundColor: Colors.white,
      title: const Text('Cartes WAPI', style: TextStyle(fontWeight: FontWeight.w900)),
      actions: [
        IconButton(
          onPressed: () => setState(_newGame),
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Nouvelle partie',
        ),
      ],
    ),
    body: SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          children: [
            _ScoreLine(
              left: 'Vous',
              right: _versusAi ? 'IA WAPI' : 'Joueur 2',
              leftScore: _playerScore,
              rightScore: _opponentScore,
              round: _round,
            ),
            const SizedBox(height: 18),
            Expanded(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF0B614B), Color(0xFF063D32)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(color: const Color(0xFF39D2A4).withValues(alpha: .55)),
                ),
                child: Stack(
                  children: [
                    const Positioned(
                      left: 20,
                      right: 20,
                      top: 20,
                      child: Text(
                        'BATAILLE',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          letterSpacing: 4,
                          color: Color(0xFFBCEEDD),
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                    Center(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          _PlayingCard(value: _playerCard, label: 'VOUS'),
                          _PlayingCard(value: _opponentCard, label: _versusAi ? 'IA' : 'JOUEUR 2'),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 18),
            Text(
              _status,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFFE6FFF7), fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _busy
                        ? null
                        : () => setState(() {
                            _versusAi = !_versusAi;
                            _newGame();
                          }),
                    style: OutlinedButton.styleFrom(foregroundColor: Colors.white),
                    icon: Icon(_versusAi ? Icons.smart_toy_outlined : Icons.people_alt_outlined),
                    label: Text(_versusAi ? 'Contre l’IA' : '2 joueurs'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _busy || _round >= 26 ? null : _draw,
                    style: FilledButton.styleFrom(backgroundColor: const Color(0xFF18B884)),
                    icon: const Icon(Icons.style_rounded),
                    label: Text(_busy ? 'Jeu…' : 'Tirer une carte'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              '26 manches · la valeur la plus forte gagne le point',
              style: TextStyle(color: Colors.white.withValues(alpha: .66), fontSize: 12),
            ),
          ],
        ),
      ),
    ),
  );
}

class _ScoreLine extends StatelessWidget {
  const _ScoreLine({
    required this.left,
    required this.right,
    required this.leftScore,
    required this.rightScore,
    required this.round,
  });
  final String left;
  final String right;
  final int leftScore;
  final int rightScore;
  final int round;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Expanded(child: _Score(name: left, score: leftScore, align: CrossAxisAlignment.start)),
      Text(
        'MANCHE ' + (round + 1).clamp(1, 26).toString() + ' / 26',
        style: const TextStyle(color: Color(0xFF8DDDC5), fontWeight: FontWeight.w800, fontSize: 12),
      ),
      Expanded(child: _Score(name: right, score: rightScore, align: CrossAxisAlignment.end)),
    ],
  );
}

class _Score extends StatelessWidget {
  const _Score({required this.name, required this.score, required this.align});
  final String name;
  final int score;
  final CrossAxisAlignment align;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: align,
    children: [
      Text(name.toUpperCase(), style: const TextStyle(color: Color(0xFFB9D8D2), fontSize: 11, fontWeight: FontWeight.w900)),
      Text(score.toString(), style: const TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w900)),
    ],
  );
}

class _PlayingCard extends StatelessWidget {
  const _PlayingCard({required this.value, required this.label});
  final int? value;
  final String label;
  @override
  Widget build(BuildContext context) {
    final card = value;
    final suit = card == null ? '' : const ['♠', '♥', '♦', '♣'][card ~/ 13];
    final rank = card == null ? '' : const ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'V', 'D', 'R', 'A'][card % 13];
    final red = suit == '♥' || suit == '♦';
    return Column(
      children: [
        Container(
          width: 122,
          height: 174,
          decoration: BoxDecoration(
            color: card == null ? const Color(0xFF133F4B) : Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFF0C772), width: 2),
            boxShadow: const [BoxShadow(color: Color(0x55000000), blurRadius: 12, offset: Offset(0, 7))],
          ),
          child: card == null
              ? const Center(child: Icon(Icons.style_rounded, color: Color(0xFF76D7C0), size: 38))
              : Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(rank, style: TextStyle(color: red ? const Color(0xFFD33C45) : const Color(0xFF122033), fontWeight: FontWeight.w900, fontSize: 27)),
                      Text(suit, style: TextStyle(color: red ? const Color(0xFFD33C45) : const Color(0xFF122033), fontSize: 23)),
                      const Spacer(),
                      Center(child: Text(suit, style: TextStyle(color: red ? const Color(0xFFD33C45) : const Color(0xFF122033), fontSize: 56))),
                    ],
                  ),
                ),
        ),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Color(0xFFC2E8DB), fontWeight: FontWeight.w900, fontSize: 11)),
      ],
    );
  }
}
