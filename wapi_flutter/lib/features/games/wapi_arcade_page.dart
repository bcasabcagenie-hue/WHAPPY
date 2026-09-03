import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';

enum WapiArcadeMode { poker, dailyChallenge, words }

class WapiArcadePage extends StatefulWidget {
  const WapiArcadePage({super.key, required this.mode});
  final WapiArcadeMode mode;

  @override
  State<WapiArcadePage> createState() => _WapiArcadePageState();
}

class _WapiArcadePageState extends State<WapiArcadePage> {
  final _random = Random();
  final _answer = TextEditingController();
  Timer? _timer;
  int _score = 0;
  int _round = 0;
  int _seconds = 30;
  List<int> _hand = const [];
  List<int> _opponentHand = const [];
  String _message = '';

  static const _questions = <_ChallengeQuestion>[
    _ChallengeQuestion('Quel continent abrite le Congo ?', [
      'Afrique',
      'Europe',
      'Asie',
      'Amérique',
    ], 0),
    _ChallengeQuestion('Combien font 9 × 8 ?', ['63', '72', '81', '64'], 1),
    _ChallengeQuestion('Quelle planète est surnommée la planète rouge ?', [
      'Mars',
      'Vénus',
      'Jupiter',
      'Mercure',
    ], 0),
    _ChallengeQuestion('Quel est le plus grand océan ?', [
      'Atlantique',
      'Indien',
      'Pacifique',
      'Arctique',
    ], 2),
    _ChallengeQuestion('Combien de côtés a un hexagone ?', [
      '5',
      '6',
      '7',
      '8',
    ], 1),
  ];
  static const _words = <String>[
    'WAPI',
    'BRAZZAVILLE',
    'TOURNOI',
    'BILLARD',
    'AMITIE',
    'MOBILE',
    'CREATIF',
    'CHAMPION',
  ];

  @override
  void initState() {
    super.initState();
    if (widget.mode == WapiArcadeMode.poker) {
      _dealPoker();
    } else if (widget.mode == WapiArcadeMode.dailyChallenge) {
      _startChallenge();
    } else {
      _newWord();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _answer.dispose();
    super.dispose();
  }

  String get _title => switch (widget.mode) {
    WapiArcadeMode.poker => 'Poker WAPI',
    WapiArcadeMode.dailyChallenge => 'Défi du jour',
    WapiArcadeMode.words => 'Mots & idées',
  };

  void _dealPoker() {
    final deck = List<int>.generate(52, (index) => index)..shuffle(_random);
    final user = deck.take(5).toList();
    final opponent = deck.skip(5).take(5).toList();
    final userRank = _pokerRank(user);
    final opponentRank = _pokerRank(opponent);
    setState(() {
      _hand = user;
      _opponentHand = opponent;
      _round += 1;
      if (_round == 1) {
        _message = 'Votre première main est distribuée.';
      } else if (userRank > opponentRank) {
        _score += 1;
        _message = 'Main gagnante : ' + _rankLabel(userRank) + '.';
      } else if (userRank < opponentRank) {
        _message = 'L’IA gagne avec ' + _rankLabel(opponentRank) + '.';
      } else {
        _message = 'Égalité de rang. Distribuez une nouvelle main.';
      }
    });
  }

  int _pokerRank(List<int> hand) {
    final counts = <int, int>{};
    for (final card in hand) {
      counts.update(card % 13, (count) => count + 1, ifAbsent: () => 1);
    }
    final amount = counts.values.toList()..sort();
    final flush = hand.map((card) => card ~/ 13).toSet().length == 1;
    final ranks = counts.keys.toList()..sort();
    final straight = ranks.length == 5 && ranks.last - ranks.first == 4;
    if (straight && flush) return 8;
    if (amount.last == 4) return 7;
    if (amount.length == 2 && amount.last == 3) return 6;
    if (flush) return 5;
    if (straight) return 4;
    if (amount.last == 3) return 3;
    if (amount.length == 3 && amount.last == 2) return 2;
    if (amount.last == 2) return 1;
    return 0;
  }

  String _rankLabel(int rank) => const [
    'carte haute',
    'paire',
    'deux paires',
    'brelan',
    'suite',
    'couleur',
    'full',
    'carré',
    'quinte flush',
  ][rank];

  void _startChallenge() {
    _timer?.cancel();
    _seconds = 30;
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      if (_seconds <= 1) {
        timer.cancel();
        setState(
          () => _message = 'Temps écoulé. Relancez le défi pour réessayer.',
        );
      } else {
        setState(() => _seconds -= 1);
      }
    });
    _message = 'Répondez avant la fin du chronomètre.';
  }

  void _answerChallenge(int index) {
    if (_seconds <= 0 || _round >= _questions.length) return;
    final question = _questions[_round];
    final correct = index == question.answer;
    setState(() {
      if (correct) _score += 100 + _seconds;
      _message = correct ? 'Bonne réponse !' : 'Pas cette fois.';
      _round += 1;
    });
    if (_round == _questions.length) {
      _timer?.cancel();
      setState(
        () => _message = 'Défi terminé · ' + _score.toString() + ' XP gagnée.',
      );
    } else {
      _startChallenge();
    }
  }

  String get _currentWord => _words[_round % _words.length];

  String _scramble(String word) {
    final letters = word.split('')..shuffle(Random(word.hashCode));
    final result = letters.join();
    return result == word && word.length > 1
        ? word.substring(1) + word[0]
        : result;
  }

  void _newWord() {
    _answer.clear();
    _message = 'Recomposez le mot. Les accents ne sont pas nécessaires.';
  }

  void _checkWord() {
    final value = _answer.text.trim().toUpperCase();
    if (value.isEmpty) return;
    final valid = value == _currentWord;
    setState(() {
      if (valid) {
        _score += 50;
        _round += 1;
        _message = 'Bien trouvé ! +50 XP';
      } else {
        _message = 'Ce n’est pas le mot attendu. Essayez encore.';
      }
    });
    if (valid) _newWord();
  }

  void _restart() {
    _timer?.cancel();
    setState(() {
      _score = 0;
      _round = 0;
      _hand = const [];
      _opponentHand = const [];
      _message = '';
    });
    if (widget.mode == WapiArcadeMode.poker) {
      _dealPoker();
    } else if (widget.mode == WapiArcadeMode.dailyChallenge) {
      _startChallenge();
    } else {
      _newWord();
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFF071C28),
    appBar: AppBar(
      title: Text(_title, style: const TextStyle(fontWeight: FontWeight.w900)),
      foregroundColor: Colors.white,
      backgroundColor: const Color(0xFF071C28),
      actions: [
        IconButton(
          onPressed: _restart,
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Recommencer',
        ),
      ],
    ),
    body: SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: widget.mode == WapiArcadeMode.poker
            ? _pokerBody()
            : widget.mode == WapiArcadeMode.dailyChallenge
            ? _challengeBody()
            : _wordsBody(),
      ),
    ),
  );

  Widget _pokerBody() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      _HeaderMetric(
        label: 'MANCHES',
        value: _round.toString(),
        detail: 'Score ' + _score.toString() + ' victoire(s)',
      ),
      const SizedBox(height: 18),
      const Text(
        'IA WAPI',
        textAlign: TextAlign.center,
        style: TextStyle(color: Color(0xFFBCEEDD), fontWeight: FontWeight.w900),
      ),
      const SizedBox(height: 10),
      _CardRow(cards: _opponentHand, hidden: false),
      const Spacer(),
      Text(
        _message,
        textAlign: TextAlign.center,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w800,
        ),
      ),
      const Spacer(),
      const Text(
        'VOTRE MAIN',
        textAlign: TextAlign.center,
        style: TextStyle(color: Color(0xFFBCEEDD), fontWeight: FontWeight.w900),
      ),
      const SizedBox(height: 10),
      _CardRow(cards: _hand, hidden: false),
      const SizedBox(height: 20),
      FilledButton.icon(
        onPressed: _dealPoker,
        style: FilledButton.styleFrom(
          backgroundColor: const Color(0xFF18B884),
          minimumSize: const Size.fromHeight(54),
        ),
        icon: const Icon(Icons.casino_rounded),
        label: const Text('Distribuer une main'),
      ),
      const SizedBox(height: 10),
      const Text(
        'Jeu gratuit · aucune mise, aucun achat, aucune conversion en argent.',
        textAlign: TextAlign.center,
        style: TextStyle(color: Color(0xFF8FB9AE), fontSize: 12),
      ),
    ],
  );

  Widget _challengeBody() {
    final finished = _round >= _questions.length;
    final question = _questions[min(_round, _questions.length - 1)];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _HeaderMetric(
          label: 'TEMPS',
          value: _seconds.toString() + ' s',
          detail:
              _score.toString() +
              ' XP · question ' +
              min(_round + 1, _questions.length).toString() +
              '/' +
              _questions.length.toString(),
        ),
        const SizedBox(height: 32),
        Text(
          finished ? 'Défi accompli' : question.text,
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 27,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 28),
        if (!finished)
          ...List.generate(
            question.options.length,
            (index) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: FilledButton(
                onPressed: _seconds > 0 ? () => _answerChallenge(index) : null,
                style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFF123B4B),
                  padding: const EdgeInsets.all(17),
                ),
                child: Text(question.options[index]),
              ),
            ),
          ),
        const Spacer(),
        Text(
          _message,
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Color(0xFFBCEEDD),
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }

  Widget _wordsBody() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      _HeaderMetric(
        label: 'XP',
        value: _score.toString(),
        detail: 'mot ' + (_round + 1).toString(),
      ),
      const SizedBox(height: 35),
      const Text(
        'MOTS & IDÉES',
        textAlign: TextAlign.center,
        style: TextStyle(
          letterSpacing: 3,
          color: Color(0xFF75DFC2),
          fontWeight: FontWeight.w900,
        ),
      ),
      const SizedBox(height: 18),
      Container(
        padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 14),
        decoration: BoxDecoration(
          color: const Color(0xFF0A5948),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Text(
          _scramble(_currentWord),
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 33,
            letterSpacing: 5,
            color: Colors.white,
            fontWeight: FontWeight.w900,
          ),
        ),
      ),
      const SizedBox(height: 22),
      TextField(
        controller: _answer,
        textCapitalization: TextCapitalization.characters,
        onSubmitted: (_) => _checkWord(),
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w800,
        ),
        decoration: const InputDecoration(
          labelText: 'Votre réponse',
          labelStyle: TextStyle(color: Color(0xFFACD5C8)),
        ),
      ),
      const SizedBox(height: 14),
      FilledButton.icon(
        onPressed: _checkWord,
        style: FilledButton.styleFrom(
          backgroundColor: const Color(0xFF18B884),
          minimumSize: const Size.fromHeight(52),
        ),
        icon: const Icon(Icons.check_circle_outline_rounded),
        label: const Text('Valider'),
      ),
      const SizedBox(height: 16),
      Text(
        _message,
        textAlign: TextAlign.center,
        style: const TextStyle(
          color: Color(0xFFBCEEDD),
          fontWeight: FontWeight.w700,
        ),
      ),
    ],
  );
}

class _HeaderMetric extends StatelessWidget {
  const _HeaderMetric({
    required this.label,
    required this.value,
    required this.detail,
  });
  final String label;
  final String value;
  final String detail;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: const Color(0xFF0C3341),
      borderRadius: BorderRadius.circular(22),
      border: Border.all(color: const Color(0xFF296477)),
    ),
    child: Row(
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: const TextStyle(
                color: Color(0xFF9BD7C5),
                fontSize: 11,
                fontWeight: FontWeight.w900,
              ),
            ),
            Text(
              value,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 27,
                fontWeight: FontWeight.w900,
              ),
            ),
          ],
        ),
        const SizedBox(width: 20),
        Expanded(
          child: Text(
            detail,
            textAlign: TextAlign.right,
            style: const TextStyle(
              color: Color(0xFFBCEBDD),
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    ),
  );
}

class _CardRow extends StatelessWidget {
  const _CardRow({required this.cards, required this.hidden});
  final List<int> cards;
  final bool hidden;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
    children: cards.map((card) {
      final suit = const ['♠', '♥', '♦', '♣'][card ~/ 13];
      final rank = const [
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        '10',
        'V',
        'D',
        'R',
        'A',
      ][card % 13];
      final red = suit == '♥' || suit == '♦';
      return Container(
        width: 55,
        height: 80,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(9),
          border: Border.all(color: const Color(0xFFF5C863)),
        ),
        child: Text(
          hidden ? 'W' : rank + '\n' + suit,
          textAlign: TextAlign.center,
          style: TextStyle(
            height: .95,
            color: red ? const Color(0xFFD4434A) : const Color(0xFF162133),
            fontWeight: FontWeight.w900,
            fontSize: 22,
          ),
        ),
      );
    }).toList(),
  );
}

class _ChallengeQuestion {
  const _ChallengeQuestion(this.text, this.options, this.answer);
  final String text;
  final List<String> options;
  final int answer;
}
