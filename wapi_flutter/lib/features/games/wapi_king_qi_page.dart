import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../../app/wapi_theme.dart';

class WapiKingQiPage extends StatefulWidget {
  const WapiKingQiPage({super.key, required this.user, required this.roomId});
  final User user;
  final String roomId;

  @override
  State<WapiKingQiPage> createState() => _WapiKingQiPageState();
}

class _WapiKingQiPageState extends State<WapiKingQiPage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  Timer? _clock;
  bool _busy = false;
  int? _chosenAnswer;

  @override
  void initState() {
    super.initState();
    _clock = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _clock?.cancel();
    super.dispose();
  }

  Future<void> _call(String name, Map<String, dynamic> data) async {
    await _functions
        .httpsCallable(name)
        .call<Map<String, dynamic>>(data)
        .timeout(const Duration(seconds: 20));
  }

  void _notice(String text, {bool error = false}) {
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

  Future<void> _start() async {
    setState(() => _busy = true);
    try {
      await _call('kingQiStartTournament', {'roomId': widget.roomId});
    } catch (error) {
      _notice(
        wapiErrorText(
          error is FirebaseFunctionsException ? error.message : error,
          fallback: 'Le tournoi ne peut pas démarrer. Réessayez.',
        ),
        error: true,
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _answer(int index) async {
    if (_busy || _chosenAnswer != null) return;
    setState(() {
      _busy = true;
      _chosenAnswer = index;
    });
    try {
      final result = await _functions
          .httpsCallable('kingQiSubmitAnswer')
          .call<Map<String, dynamic>>({
            'roomId': widget.roomId,
            'optionIndex': index,
          })
          .timeout(const Duration(seconds: 20));
      final correct = result.data['correct'] == true;
      final points = result.data['points'] is num
          ? (result.data['points'] as num).toInt()
          : 0;
      _notice(
        correct
            ? 'Bonne réponse · ' + points.toString() + ' points'
            : 'Réponse enregistrée.',
      );
    } catch (error) {
      if (mounted) setState(() => _chosenAnswer = null);
      _notice(
        wapiErrorText(
          error is FirebaseFunctionsException ? error.message : error,
          fallback: 'Réponse impossible. Réessayez.',
        ),
        error: true,
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _next() async {
    setState(() => _busy = true);
    try {
      await _call('kingQiAdvanceTournament', {'roomId': widget.roomId});
      if (mounted) setState(() => _chosenAnswer = null);
    } catch (error) {
      _notice(
        wapiErrorText(
          error is FirebaseFunctionsException ? error.message : error,
          fallback: 'La manche est encore en cours.',
        ),
        error: true,
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final room = FirebaseFirestore.instance
        .collection('kingQiRooms')
        .doc(widget.roomId);
    return StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: room.snapshots(),
      builder: (context, snapshot) {
        final data = snapshot.data?.data();
        if (data == null) {
          return Scaffold(
            appBar: AppBar(title: const Text('King QI')),
            body: const Center(child: CircularProgressIndicator()),
          );
        }
        final status = _kingText(data['status'], 'waiting');
        final ids = _kingStrings(data['playerIds']);
        final host = _kingText(data['hostId']);
        final scores = _kingNumbers(data['scores']);
        final answered = _kingStrings(data['answeredIds']);
        final deadline = _kingMillis(data['roundDeadline']);
        final remaining = deadline > 0
            ? ((deadline - DateTime.now().millisecondsSinceEpoch) / 1000)
                  .ceil()
                  .clamp(0, 20)
            : 0;
        final current = _kingMap(data['currentQuestion']);
        final myAnswered = answered.contains(widget.user.uid);
        final isHost = host == widget.user.uid;
        return Scaffold(
          backgroundColor: const Color(0xFF0C1427),
          appBar: AppBar(
            backgroundColor: const Color(0xFF0C1427),
            foregroundColor: Colors.white,
            title: const Text(
              'KING QI',
              style: TextStyle(fontWeight: FontWeight.w900),
            ),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 28),
              children: [
                _KingHeader(
                  code: _kingText(data['code']),
                  cup: _kingText(data['cupName'], 'Coupe King QI'),
                  status: status,
                  playerCount: ids.length,
                  maxPlayers: data['maxPlayers'] is num
                      ? (data['maxPlayers'] as num).toInt()
                      : 2,
                  credits: data['potCredits'] is num
                      ? (data['potCredits'] as num).toInt()
                      : 0,
                ),
                const SizedBox(height: 14),
                _Scoreboard(
                  ids: ids,
                  names: _kingMap(data['playerNames']),
                  scores: scores,
                  activeUid: widget.user.uid,
                ),
                const SizedBox(height: 18),
                if (status == 'waiting')
                  _WaitingCard(
                    code: _kingText(data['code']),
                    canStart: isHost && ids.length >= 2,
                    busy: _busy,
                    onStart: _start,
                  )
                else if (status == 'playing')
                  _QuestionCard(
                    question: current,
                    seconds: remaining,
                    selected: _chosenAnswer,
                    answered: myAnswered,
                    canAnswer: !myAnswered && remaining > 0 && !_busy,
                    onAnswer: _answer,
                  )
                else
                  _FinishedCard(
                    winners: _kingStrings(data['winners']),
                    names: _kingMap(data['playerNames']),
                    me: widget.user.uid,
                    prize: data['prizePerWinner'] is num
                        ? (data['prizePerWinner'] as num).toInt()
                        : 0,
                  ),
                if (status == 'playing' && (myAnswered || remaining == 0)) ...[
                  const SizedBox(height: 14),
                  FilledButton.icon(
                    onPressed: _busy ? null : _next,
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(50),
                      backgroundColor: const Color(0xFFFFA800),
                    ),
                    icon: const Icon(Icons.skip_next_rounded),
                    label: const Text('Passer à la question suivante'),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}

class _KingHeader extends StatelessWidget {
  const _KingHeader({
    required this.code,
    required this.cup,
    required this.status,
    required this.playerCount,
    required this.maxPlayers,
    required this.credits,
  });
  final String code;
  final String cup;
  final String status;
  final int playerCount;
  final int maxPlayers;
  final int credits;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(22),
      gradient: const LinearGradient(
        colors: [Color(0xFF46230B), Color(0xFF9E5B07)],
      ),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(
          Icons.emoji_events_rounded,
          color: Color(0xFFFFD166),
          size: 38,
        ),
        const SizedBox(height: 10),
        Text(
          cup,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 22,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          status == 'waiting'
              ? 'Code ' +
                    code +
                    ' · ' +
                    playerCount.toString() +
                    '/' +
                    maxPlayers.toString() +
                    ' joueurs'
              : 'Manche en direct · cagnotte ' +
                    credits.toString() +
                    ' crédits',
          style: const TextStyle(color: Color(0xFFFFE7B0)),
        ),
      ],
    ),
  );
}

class _Scoreboard extends StatelessWidget {
  const _Scoreboard({
    required this.ids,
    required this.names,
    required this.scores,
    required this.activeUid,
  });
  final List<String> ids;
  final Map<String, dynamic> names;
  final Map<String, int> scores;
  final String activeUid;

  @override
  Widget build(BuildContext context) {
    final ordered = [...ids]
      ..sort((a, b) => (scores[b] ?? 0).compareTo(scores[a] ?? 0));
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF17223A),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        children: ordered.map((uid) {
          final isMe = uid == activeUid;
          return ListTile(
            leading: CircleAvatar(
              backgroundColor: isMe
                  ? const Color(0xFFFFC83D)
                  : const Color(0xFF334461),
              child: Text(
                (ordered.indexOf(uid) + 1).toString(),
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
            title: Text(
              _kingText(names[uid], 'Joueur WAPI'),
              style: TextStyle(
                color: Colors.white,
                fontWeight: isMe ? FontWeight.w900 : FontWeight.w600,
              ),
            ),
            trailing: Text(
              (scores[uid] ?? 0).toString(),
              style: const TextStyle(
                color: Color(0xFFFFD166),
                fontSize: 18,
                fontWeight: FontWeight.w900,
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _WaitingCard extends StatelessWidget {
  const _WaitingCard({
    required this.code,
    required this.canStart,
    required this.busy,
    required this.onStart,
  });
  final String code;
  final bool canStart;
  final bool busy;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: const Color(0xFF17223A),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Column(
      children: [
        const Icon(
          Icons.people_alt_rounded,
          color: Color(0xFF7DD3FC),
          size: 42,
        ),
        const SizedBox(height: 12),
        const Text(
          'Invitez votre adversaire',
          style: TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Partagez le code ' +
              code +
              '. Le créateur démarre dès que deux joueurs sont présents.',
          textAlign: TextAlign.center,
          style: const TextStyle(color: Color(0xFFB9C8E4)),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: canStart && !busy ? onStart : null,
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(50),
            backgroundColor: const Color(0xFFFFA800),
          ),
          child: const Text('Démarrer le duel'),
        ),
      ],
    ),
  );
}

class _QuestionCard extends StatelessWidget {
  const _QuestionCard({
    required this.question,
    required this.seconds,
    required this.selected,
    required this.answered,
    required this.canAnswer,
    required this.onAnswer,
  });
  final Map<String, dynamic> question;
  final int seconds;
  final int? selected;
  final bool answered;
  final bool canAnswer;
  final ValueChanged<int> onAnswer;

  @override
  Widget build(BuildContext context) {
    final options = _kingStrings(question['options']);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: const Color(0xFF17223A),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Chip(
                label: Text(_kingText(question['category'], 'King QI')),
                backgroundColor: const Color(0xFF263B61),
                labelStyle: const TextStyle(color: Color(0xFFB8D6FF)),
              ),
              const Spacer(),
              Text(
                seconds.toString() + ' s',
                style: const TextStyle(
                  color: Color(0xFFFFD166),
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                ),
              ),
            ],
          ),
          LinearProgressIndicator(
            value: seconds / 20,
            minHeight: 7,
            color: const Color(0xFFFFA800),
            backgroundColor: const Color(0xFF2B3B57),
          ),
          const SizedBox(height: 18),
          Text(
            _kingText(question['text'], 'Chargement de la question…'),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 22,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 16),
          for (var index = 0; index < options.length; index++) ...[
            OutlinedButton(
              onPressed: canAnswer ? () => onAnswer(index) : null,
              style: OutlinedButton.styleFrom(
                alignment: Alignment.centerLeft,
                minimumSize: const Size.fromHeight(54),
                foregroundColor: Colors.white,
                backgroundColor: selected == index
                    ? const Color(0xFF0A6D58)
                    : const Color(0xFF202E49),
                side: BorderSide(
                  color: selected == index
                      ? const Color(0xFF5EE7B7)
                      : const Color(0xFF41516E),
                ),
              ),
              child: Text(
                String.fromCharCode(65 + index) + ' · ' + options[index],
              ),
            ),
            const SizedBox(height: 8),
          ],
          if (answered)
            const Padding(
              padding: EdgeInsets.only(top: 4),
              child: Center(
                child: Text(
                  'Réponse enregistrée. Attendez les autres joueurs.',
                  style: TextStyle(color: Color(0xFF8DE2C0)),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _FinishedCard extends StatelessWidget {
  const _FinishedCard({
    required this.winners,
    required this.names,
    required this.me,
    required this.prize,
  });
  final List<String> winners;
  final Map<String, dynamic> names;
  final String me;
  final int prize;

  @override
  Widget build(BuildContext context) {
    final won = winners.contains(me);
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: won ? const Color(0xFF2D4D1F) : const Color(0xFF17223A),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        children: [
          Icon(
            won ? Icons.emoji_events_rounded : Icons.flag_rounded,
            color: const Color(0xFFFFD166),
            size: 52,
          ),
          const SizedBox(height: 12),
          Text(
            won ? 'Vous remportez la Coupe King QI' : 'Tournoi terminé',
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 21,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Vainqueur : ' +
                winners
                    .map((uid) => _kingText(names[uid], 'Joueur WAPI'))
                    .join(', '),
            textAlign: TextAlign.center,
            style: const TextStyle(color: Color(0xFFD8E6F5)),
          ),
          if (prize > 0)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                prize.toString() + ' crédits promotionnels attribués',
                style: const TextStyle(
                  color: Color(0xFFFFD166),
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

Map<String, dynamic> _kingMap(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : const {};
List<String> _kingStrings(Object? value) =>
    value is List ? value.map((item) => item.toString()).toList() : const [];
Map<String, int> _kingNumbers(Object? value) => value is Map
    ? value.map(
        (key, item) => MapEntry(key.toString(), item is num ? item.toInt() : 0),
      )
    : const {};
String _kingText(Object? value, [String fallback = '']) =>
    value is String && value.isNotEmpty ? value : fallback;
int _kingMillis(Object? value) => value is Timestamp
    ? value.millisecondsSinceEpoch
    : value is num
    ? value.toInt()
    : 0;
