import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../../app/wapi_theme.dart';
import 'wapi_cards_page.dart';
import 'wapi_arcade_page.dart';
import 'wapi_chess_page.dart';
import 'wapi_draughts_page.dart';
import 'wapi_king_qi_page.dart';
import 'wapi_ludo_page.dart';
import 'wapi_pool_table_page.dart';

/// Flutter lobby for server-owned game profiles and real WAPI rooms.
class WapiGamesPage extends StatefulWidget {
  const WapiGamesPage({super.key, required this.user});
  final User user;
  @override
  State<WapiGamesPage> createState() => _WapiGamesPageState();
}

class _WapiGamesPageState extends State<WapiGamesPage> {
  final _api = FirebaseFunctions.instanceFor(region: 'europe-west1');
  bool _loading = true;
  bool _busy = false;
  String? _error;
  Map<String, dynamic> _king = const {};
  Map<String, dynamic> _pool = const {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await Future.wait([
        _call('kingQiGetProfile'),
        _call('getGameProfile', {'gameId': 'billard'}),
      ]);
      if (mounted)
        setState(() {
          _king = result[0];
          _pool = _map(result[1]['profile']);
        });
    } catch (error) {
      if (mounted) setState(() => _error = _errorText(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createKing() async {
    final result = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      builder: (_) => const _KingRoomForm(),
    );
    if (result == null) return;
    await _run(() async {
      final created = await _call('kingQiCreateTournament', result);
      final roomId = _string(created['roomId']);
      _toast('Tournoi créé · code ' + _string(created['code']));
      if (mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WapiKingQiPage(user: widget.user, roomId: roomId),
          ),
        );
      }
    });
  }

  Future<void> _joinKing() async {
    final code = await _askCode('Code du tournoi King QI');
    if (code == null) return;
    await _run(() async {
      final room = await _call('kingQiJoinTournament', {'code': code});
      _toast('Vous avez rejoint le tournoi King QI.');
      if (mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WapiKingQiPage(
              user: widget.user,
              roomId: _string(room['roomId']),
            ),
          ),
        );
      }
    });
  }

  Future<void> _findPool() async {
    await _run(() async {
      final room = await _call('findPoolMatch');
      final code = _string(room['roomId']);
      final found = room['matched'] == true;
      _toast(
        found
            ? 'Adversaire trouvé · table ' + code
            : 'Table créée · partagez le code ' + code,
      );
      if (mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WapiPoolTablePage(user: widget.user, roomId: code),
          ),
        );
      }
    });
  }

  Future<void> _createPool() async {
    await _run(() async {
      final room = await _call('createPoolMatch', {'visibility': 'private'});
      final code = _string(room['roomId']);
      _toast('Table privée créée · code ' + code);
      if (mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WapiPoolTablePage(user: widget.user, roomId: code),
          ),
        );
      }
    });
  }

  Future<void> _playPoolAi() async {
    await _run(() async {
      final room = await _call('createPoolAiMatch');
      final code = _string(room['roomId']);
      _toast('Table IA prête · à vous de casser.');
      if (mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WapiPoolTablePage(user: widget.user, roomId: code),
          ),
        );
      }
    });
  }

  Future<void> _joinPool() async {
    final code = await _askCode('Code de table Wapi Pool');
    if (code == null) return;
    await _run(() async {
      await _call('joinPoolMatch', {'roomId': code});
      _toast('Vous avez rejoint la table ' + code + '.');
      if (mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WapiPoolTablePage(user: widget.user, roomId: code),
          ),
        );
      }
    });
  }

  Future<String?> _askCode(String title) async {
    final controller = TextEditingController();
    final value = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          20,
          20,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              maxLength: 24,
              decoration: const InputDecoration(labelText: 'Code'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(
                sheetContext,
                controller.text.trim().toUpperCase(),
              ),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(50),
              ),
              child: const Text('Rejoindre'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    return value == null || value.isEmpty ? null : value;
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      await _load();
    } catch (error) {
      _toast(_errorText(error), error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<Map<String, dynamic>> _call(
    String name, [
    Map<String, dynamic> data = const {},
  ]) async {
    final result = await _api
        .httpsCallable(
          name,
          options: HttpsCallableOptions(timeout: const Duration(seconds: 20)),
        )
        .call<Map<String, dynamic>>(data);
    return _map(result.data);
  }

  void _toast(String text, {bool error = false}) {
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

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'WAPI Games',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: _loading ? null : _load,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: Stack(
      children: [
        RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
            children: [
              const Text(
                'Votre profil joueur WAPI',
                style: TextStyle(
                  fontSize: 25,
                  fontWeight: FontWeight.w900,
                  color: Color(0xFF062233),
                ),
              ),
              const SizedBox(height: 5),
              const Text(
                'Vos records et vos salles sont synchronisés par le serveur WAPI.',
                style: TextStyle(color: WapiColors.muted),
              ),
              if (_error != null) _ErrorCard(text: _error!),
              const SizedBox(height: 18),
              _GameCard(
                title: 'KING QI',
                subtitle: 'Duels, tournois et Coupe King QI',
                icon: Icons.quiz_rounded,
                accent: const Color(0xFFFFC83D),
                stats: _string(_king['credits']).isEmpty
                    ? 'Profil indisponible'
                    : _string(_king['credits']) +
                          ' crédits · ' +
                          _string(_king['trophies']) +
                          ' coupes',
                actions: [
                  _GameAction(
                    label: 'Créer un tournoi',
                    icon: Icons.add_circle_outline_rounded,
                    onTap: _busy ? null : _createKing,
                  ),
                  _GameAction(
                    label: 'Rejoindre',
                    icon: Icons.login_rounded,
                    onTap: _busy ? null : _joinKing,
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'WAPI POOL',
                subtitle: 'Table réelle, adversaires WAPI et entraînement',
                icon: Icons.sports_bar_rounded,
                accent: const Color(0xFF00A884),
                stats:
                    _string(_pool['displayName'], fallback: 'Profil joueur') +
                    ' · rating ' +
                    _string(_pool['rating'], fallback: '1000'),
                actions: [
                  _GameAction(
                    label: 'Trouver un joueur',
                    icon: Icons.radar_rounded,
                    onTap: _busy ? null : _findPool,
                  ),
                  _GameAction(
                    label: 'Jouer contre l’IA',
                    icon: Icons.smart_toy_rounded,
                    onTap: _busy ? null : _playPoolAi,
                  ),
                  _GameAction(
                    label: 'Créer une table',
                    icon: Icons.add_circle_outline_rounded,
                    onTap: _busy ? null : _createPool,
                  ),
                  _GameAction(
                    label: 'Rejoindre par code',
                    icon: Icons.login_rounded,
                    onTap: _busy ? null : _joinPool,
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'ÉCHECS WAPI',
                subtitle: 'Vraie table, coups légaux et IA locale',
                icon: Icons.extension_rounded,
                accent: const Color(0xFF6C4AB6),
                stats: 'Roque, prise en passant, promotion et échec et mat',
                actions: [
                  _GameAction(
                    label: 'Jouer contre l’IA',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiChessPage(),
                            ),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              _GameCard(
                title: 'LUDO WAPI',
                subtitle: 'Deux dés, pions, captures et adversaires IA',
                icon: Icons.casino_rounded,
                accent: const Color(0xFF304FFE),
                stats: 'Partie locale complète · le joueur choisit ses pions',
                actions: [
                  _GameAction(
                    label: 'Jouer contre l’IA',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiLudoPage(),
                            ),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'DAMES WAPI',
                subtitle: 'Damier 10 × 10, prises obligatoires et dames',
                icon: Icons.grid_view_rounded,
                accent: const Color(0xFFC76A2B),
                stats: 'Jouable avec IA ou à deux sur le même appareil',
                actions: [
                  _GameAction(
                    label: 'Jouer maintenant',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiDraughtsPage(),
                            ),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'CARTES WAPI',
                subtitle: 'Bataille, score sur 26 manches et table premium',
                icon: Icons.style_rounded,
                accent: const Color(0xFF18B884),
                stats: 'Jouable contre l’IA ou à deux sur le même appareil',
                actions: [
                  _GameAction(
                    label: 'Ouvrir la table',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiCardsPage(),
                            ),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'POKER WAPI',
                subtitle: 'Mains de cinq cartes et classement des combinaisons',
                icon: Icons.casino_rounded,
                accent: const Color(0xFFB42F52),
                stats: 'Parties gratuites contre l’IA · aucun argent réel',
                actions: [
                  _GameAction(
                    label: 'Distribuer',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiArcadePage(
                                mode: WapiArcadeMode.poker,
                              ),
                            ),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'DÉFI DU JOUR',
                subtitle: 'Quiz express, chronomètre et expérience joueur',
                icon: Icons.bolt_rounded,
                accent: const Color(0xFFFFA000),
                stats: '5 questions · score local · sans attente',
                actions: [
                  _GameAction(
                    label: 'Relever le défi',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiArcadePage(
                                mode: WapiArcadeMode.dailyChallenge,
                              ),
                            ),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _GameCard(
                title: 'MOTS & IDÉES',
                subtitle:
                    'Recomposez les mots et enchaînez les bonnes réponses',
                icon: Icons.abc_rounded,
                accent: const Color(0xFF2B7CD3),
                stats: 'Défi local fluide, XP et validation instantanée',
                actions: [
                  _GameAction(
                    label: 'Jouer',
                    icon: Icons.play_arrow_rounded,
                    onTap: _busy
                        ? null
                        : () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const WapiArcadePage(
                                mode: WapiArcadeMode.words,
                              ),
                            ),
                          ),
                  ),
                ],
              ),
            ],
          ),
        ),
        if (_loading)
          const Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: LinearProgressIndicator(minHeight: 3),
          ),
      ],
    ),
  );
}

class _GameCard extends StatelessWidget {
  const _GameCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.accent,
    required this.stats,
    required this.actions,
  });
  final String title;
  final String subtitle;
  final IconData icon;
  final Color accent;
  final String stats;
  final List<_GameAction> actions;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(17),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              CircleAvatar(
                backgroundColor: accent.withValues(alpha: .16),
                foregroundColor: accent,
                child: Icon(icon),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 19,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    Text(
                      subtitle,
                      style: const TextStyle(
                        color: WapiColors.muted,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 13),
          Text(
            stats,
            style: TextStyle(
              color: accent,
              fontWeight: FontWeight.w900,
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: actions
                .map(
                  (action) => OutlinedButton.icon(
                    onPressed: action.onTap,
                    icon: Icon(action.icon, size: 17),
                    label: Text(action.label),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    ),
  );
}

class _GameAction {
  const _GameAction({
    required this.label,
    required this.icon,
    required this.onTap,
  });
  final String label;
  final IconData icon;
  final VoidCallback? onTap;
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.text});
  final String text;
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(top: 14),
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: const Color(0xFFFFEDEB),
      borderRadius: BorderRadius.circular(14),
    ),
    child: Text(text, style: const TextStyle(color: Color(0xFFB42318))),
  );
}

class _KingRoomForm extends StatefulWidget {
  const _KingRoomForm();
  @override
  State<_KingRoomForm> createState() => _KingRoomFormState();
}

class _KingRoomFormState extends State<_KingRoomForm> {
  int _players = 2;
  int _credits = 0;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(20),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Créer un tournoi King QI',
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<int>(
          value: _players,
          items: [2, 3, 4, 6, 8]
              .map(
                (value) => DropdownMenuItem(
                  value: value,
                  child: Text(value.toString() + ' joueurs maximum'),
                ),
              )
              .toList(),
          onChanged: (value) => setState(() => _players = value ?? 2),
          decoration: const InputDecoration(labelText: 'Joueurs'),
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<int>(
          value: _credits,
          items: [0, 10, 25, 50]
              .map(
                (value) => DropdownMenuItem(
                  value: value,
                  child: Text(value.toString() + ' crédits promotionnels'),
                ),
              )
              .toList(),
          onChanged: (value) => setState(() => _credits = value ?? 0),
          decoration: const InputDecoration(labelText: 'Mise'),
        ),
        const SizedBox(height: 14),
        const Text(
          'Les crédits sont promotionnels, non convertibles en argent.',
          style: TextStyle(color: WapiColors.muted, fontSize: 12),
        ),
        const SizedBox(height: 14),
        FilledButton(
          onPressed: () => Navigator.pop(context, {
            'maxPlayers': _players,
            'entryCredits': _credits,
            'visibility': 'private',
          }),
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50)),
          child: const Text('Créer le tournoi'),
        ),
      ],
    ),
  );
}

Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
String _string(Object? value, {String fallback = ''}) =>
    value is String && value.trim().isNotEmpty
    ? value.trim()
    : value is num
    ? value.toString()
    : fallback;
String _errorText(Object error) => wapiErrorText(
  error is FirebaseFunctionsException ? error.message : error,
  fallback: 'Le service Jeux est indisponible pour le moment.',
);
