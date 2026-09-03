import 'dart:convert';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../app/wapi_theme.dart';

/// Native Flutter TicketBulk. The server remains authoritative for QR tokens,
/// reservations and entry counters; this screen never fabricates a ticket.
class WapiTicketBulkPage extends StatefulWidget {
  const WapiTicketBulkPage({super.key, required this.user});
  final User user;

  @override
  State<WapiTicketBulkPage> createState() => _WapiTicketBulkPageState();
}

class _WapiTicketBulkPageState extends State<WapiTicketBulkPage> {
  final _api = _CommerceApi();
  int _tab = 0;
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _events = const [];
  List<Map<String, dynamic>> _tickets = const [];
  List<Map<String, dynamic>> _pages = const [];

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final values = await Future.wait([
        _api.call('events'),
        _api.call('myTickets'),
        _api.call('ownPages'),
      ]);
      if (!mounted) return;
      setState(() {
        _events = _list(values[0]['events']);
        _tickets = _list(values[1]['tickets']);
        _pages = _list(values[2]['pages']);
      });
    } catch (error) {
      if (mounted) setState(() => _error = _errorText(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _organize() async {
    if (_pages.isEmpty) {
      final saved = await showModalBottomSheet<bool>(
        context: context,
        isScrollControlled: true,
        builder: (_) => _BusinessPageForm(user: widget.user),
      );
      if (saved == true) await _refresh();
      return;
    }
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => _EventForm(api: _api, pages: _pages),
      ),
    );
    if (saved == true) await _refresh();
  }

  Future<void> _reserve(Map<String, dynamic> event) async {
    try {
      final result = await _api.call('reserveTicket', {
        'eventId': _string(event['id']),
      });
      if (!mounted) return;
      await _showTicket(_map(result['ticket']));
      await _refresh();
    } catch (error) {
      _toast(_errorText(error), error: true);
    }
  }

  Future<void> _showTicket(Map<String, dynamic> ticket) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) => _TicketView(
        ticket: ticket,
        onCancel: _string(ticket['status']) == 'issued'
            ? () async {
                await _cancelTicket(ticket);
                if (sheetContext.mounted) Navigator.pop(sheetContext);
              }
            : null,
      ),
    );
  }

  Future<void> _cancelTicket(Map<String, dynamic> ticket) async {
    try {
      await _api.call('cancelTicket', {'code': _string(ticket['code'])});
      _toast('Billet annulé. La place est de nouveau disponible.');
      await _refresh();
    } catch (error) {
      _toast(_errorText(error), error: true);
    }
  }

  Future<void> _checkIn(Map<String, dynamic> event) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => _TicketCheckInPage(api: _api, event: event),
      ),
    );
    if (changed == true) await _refresh();
  }

  void _openReservedTicket(
    BuildContext sheetContext,
    Map<String, dynamic> event,
  ) {
    Navigator.pop(sheetContext);
    final eventId = _string(event['id']);
    Map<String, dynamic>? match;
    for (final ticket in _tickets) {
      if (_string(ticket['eventId']) == eventId &&
          _string(ticket['status']) == 'issued') {
        match = ticket;
        break;
      }
    }
    if (match != null) {
      _showTicket(match);
    } else {
      setState(() => _tab = 1);
      _toast('Votre billet est disponible dans Mes billets.');
    }
  }

  Future<void> _showEvent(Map<String, dynamic> event) {
    final remaining =
        (_integer(event['capacity']) - _integer(event['reserved'])).clamp(
          0,
          99999,
        );
    final owner = _string(event['ownerId']) == widget.user.uid;
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => DraggableScrollableSheet(
        initialChildSize: .78,
        minChildSize: .44,
        maxChildSize: .93,
        builder: (_, scroll) => Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          ),
          child: ListView(
            controller: scroll,
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
            children: [
              Center(
                child: Container(
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                    color: WapiColors.line,
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              _Poster(event: event, height: 205),
              const SizedBox(height: 18),
              Text(
                _string(event['category'], fallback: 'ÉVÉNEMENT').toUpperCase(),
                style: const TextStyle(
                  color: Color(0xFF00856A),
                  fontSize: 11,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                _string(event['title']),
                style: const TextStyle(
                  fontSize: 27,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 12),
              _Detail(
                icon: Icons.calendar_month_rounded,
                value: _date(_integer(event['startsAt'])),
              ),
              if (_integer(event['doorsAt']) > 0)
                _Detail(
                  icon: Icons.door_front_door_outlined,
                  value: 'Ouverture · ${_date(_integer(event['doorsAt']))}',
                ),
              _Detail(
                icon: Icons.location_on_outlined,
                value: _string(event['venue']),
              ),
              _Detail(
                icon: Icons.business_outlined,
                value: _string(
                  event['organizer'],
                  fallback: 'Organisateur WAPI',
                ),
              ),
              if (_string(event['contact']).isNotEmpty)
                _Detail(
                  icon: Icons.contact_phone_outlined,
                  value: _string(event['contact']),
                ),
              if (_string(event['description']).isNotEmpty) ...[
                const SizedBox(height: 18),
                const Text(
                  'À propos',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 6),
                Text(
                  _string(event['description']),
                  style: const TextStyle(
                    height: 1.45,
                    color: Color(0xFF43515B),
                  ),
                ),
              ],
              if (_string(event['agenda']).isNotEmpty) ...[
                const SizedBox(height: 18),
                const Text(
                  'Programme',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 6),
                Text(
                  _string(event['agenda']),
                  style: const TextStyle(
                    height: 1.45,
                    color: Color(0xFF43515B),
                  ),
                ),
              ],
              if (_string(event['terms']).isNotEmpty) ...[
                const SizedBox(height: 18),
                const Text(
                  'Conditions d’accès',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 6),
                Text(
                  _string(event['terms']),
                  style: const TextStyle(
                    height: 1.45,
                    color: Color(0xFF43515B),
                  ),
                ),
              ],
              const SizedBox(height: 18),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFEAF8F4),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.confirmation_number_outlined,
                      color: Color(0xFF087D62),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        _string(
                              event['ticketLabel'],
                              fallback: 'Accès général',
                            ) +
                            ' · gratuit',
                        style: const TextStyle(
                          fontWeight: FontWeight.w900,
                          color: Color(0xFF176451),
                        ),
                      ),
                    ),
                    Text(
                      remaining.toString() + ' places',
                      style: const TextStyle(
                        fontWeight: FontWeight.w900,
                        color: Color(0xFF176451),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 22),
              if (owner)
                FilledButton.icon(
                  onPressed: () {
                    Navigator.pop(sheetContext);
                    _checkIn(event);
                  },
                  icon: const Icon(Icons.qr_code_scanner_rounded),
                  label: Text(
                    'Contrôler les entrées · ' +
                        _integer(event['checkedIn']).toString() +
                        '/' +
                        _integer(event['reserved']).toString(),
                  ),
                )
              else if (_string(event['myTicketStatus']) == 'issued')
                OutlinedButton.icon(
                  onPressed: () => _openReservedTicket(sheetContext, event),
                  icon: Icon(Icons.verified_outlined),
                  label: Text('Afficher mon billet'),
                )
              else
                FilledButton.icon(
                  onPressed: remaining > 0
                      ? () {
                          Navigator.pop(sheetContext);
                          _reserve(event);
                        }
                      : null,
                  icon: const Icon(Icons.confirmation_number_rounded),
                  label: Text(
                    remaining > 0
                        ? 'Réserver mon billet gratuit'
                        : 'Événement complet',
                  ),
                ),
            ],
          ),
        ),
      ),
    );
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
  Widget build(BuildContext context) {
    final ownEvents = _events
        .where((event) => _string(event['ownerId']) == widget.user.uid)
        .toList();
    return Theme(
      data: Theme.of(context).copyWith(
        colorScheme: Theme.of(
          context,
        ).colorScheme.copyWith(primary: const Color(0xFF00A884)),
      ),
      child: Scaffold(
        appBar: AppBar(
          backgroundColor: const Color(0xFF061D2D),
          foregroundColor: Colors.white,
          title: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.confirmation_number_rounded, color: Color(0xFF7CE5FF)),
              SizedBox(width: 9),
              Text(
                'TICKETBULK',
                style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 1),
              ),
            ],
          ),
          actions: [
            IconButton(
              onPressed: _loading ? null : _refresh,
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
        ),
        body: IndexedStack(
          index: _tab,
          children: [
            _EventList(
              events: _events,
              loading: _loading,
              error: _error,
              onRefresh: _refresh,
              onOpen: _showEvent,
            ),
            _TicketList(
              tickets: _tickets,
              loading: _loading,
              error: _error,
              onRefresh: _refresh,
              onOpen: _showTicket,
            ),
            _OrganizerList(
              pages: _pages,
              events: ownEvents,
              loading: _loading,
              error: _error,
              onRefresh: _refresh,
              onCreate: _organize,
              onOpen: _showEvent,
            ),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _tab,
          onDestinationSelected: (value) => setState(() => _tab = value),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.explore_outlined),
              selectedIcon: Icon(Icons.explore),
              label: 'Découvrir',
            ),
            NavigationDestination(
              icon: Icon(Icons.confirmation_number_outlined),
              selectedIcon: Icon(Icons.confirmation_number),
              label: 'Mes billets',
            ),
            NavigationDestination(
              icon: Icon(Icons.add_business_outlined),
              selectedIcon: Icon(Icons.add_business),
              label: 'Organiser',
            ),
          ],
        ),
      ),
    );
  }
}

class _EventList extends StatefulWidget {
  const _EventList({
    required this.events,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onOpen,
  });
  final List<Map<String, dynamic>> events;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final ValueChanged<Map<String, dynamic>> onOpen;
  @override
  State<_EventList> createState() => _EventListState();
}

class _EventListState extends State<_EventList> {
  String _search = '';
  @override
  Widget build(BuildContext context) {
    final visible = widget.events.where((event) {
      final text =
          _string(event['title']) +
          ' ' +
          _string(event['venue']) +
          ' ' +
          _string(event['organizer']);
      return _search.isEmpty ||
          text.toLowerCase().contains(_search.toLowerCase());
    }).toList();
    return RefreshIndicator(
      onRefresh: widget.onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
        children: [
          const Text(
            'À vivre près de vous',
            style: TextStyle(
              fontSize: 26,
              fontWeight: FontWeight.w900,
              color: Color(0xFF062233),
            ),
          ),
          const SizedBox(height: 5),
          const Text(
            'Événements vérifiés, billets personnels et accès QR sécurisé.',
            style: TextStyle(color: WapiColors.muted),
          ),
          const SizedBox(height: 16),
          TextField(
            onChanged: (value) => setState(() => _search = value.trim()),
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search_rounded),
              hintText: 'Concert, formation, lieu…',
            ),
          ),
          const SizedBox(height: 16),
          if (widget.loading && widget.events.isEmpty)
            const Padding(
              padding: EdgeInsets.all(42),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (widget.error != null && widget.events.isEmpty)
            _State(
              icon: Icons.cloud_off_outlined,
              title: 'Impossible de charger',
              body: widget.error!,
              action: widget.onRefresh,
            )
          else if (visible.isEmpty)
            const _State(
              icon: Icons.event_busy_outlined,
              title: 'Aucun événement',
              body: 'Revenez bientôt découvrir les prochaines dates.',
            )
          else
            ...visible.map(
              (event) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _EventCard(
                  event: event,
                  onTap: () => widget.onOpen(event),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _TicketList extends StatelessWidget {
  const _TicketList({
    required this.tickets,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onOpen,
  });
  final List<Map<String, dynamic>> tickets;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final ValueChanged<Map<String, dynamic>> onOpen;
  @override
  Widget build(BuildContext context) => RefreshIndicator(
    onRefresh: onRefresh,
    child: ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
      children: [
        const Text(
          'Mes billets',
          style: TextStyle(
            fontSize: 26,
            fontWeight: FontWeight.w900,
            color: Color(0xFF062233),
          ),
        ),
        const SizedBox(height: 5),
        const Text(
          'Chaque QR est personnel. Ne le partagez pas.',
          style: TextStyle(color: WapiColors.muted),
        ),
        const SizedBox(height: 18),
        if (loading && tickets.isEmpty)
          const Padding(
            padding: EdgeInsets.all(42),
            child: Center(child: CircularProgressIndicator()),
          )
        else if (error != null && tickets.isEmpty)
          _State(
            icon: Icons.cloud_off_outlined,
            title: 'Impossible de charger',
            body: error!,
            action: onRefresh,
          )
        else if (tickets.isEmpty)
          const _State(
            icon: Icons.confirmation_number_outlined,
            title: 'Aucun billet',
            body:
                'Réservez un événement depuis Découvrir : votre QR apparaîtra ici immédiatement.',
          )
        else
          ...tickets.map(
            (ticket) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _TicketCard(ticket: ticket, onTap: () => onOpen(ticket)),
            ),
          ),
      ],
    ),
  );
}

class _OrganizerList extends StatelessWidget {
  const _OrganizerList({
    required this.pages,
    required this.events,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onCreate,
    required this.onOpen,
  });
  final List<Map<String, dynamic>> pages;
  final List<Map<String, dynamic>> events;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreate;
  final ValueChanged<Map<String, dynamic>> onOpen;
  @override
  Widget build(BuildContext context) => RefreshIndicator(
    onRefresh: onRefresh,
    child: ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
      children: [
        Row(
          children: [
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Organiser',
                    style: TextStyle(
                      fontSize: 26,
                      fontWeight: FontWeight.w900,
                      color: Color(0xFF062233),
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'Créez, publiez et pilotez vos accès.',
                    style: TextStyle(color: WapiColors.muted),
                  ),
                ],
              ),
            ),
            FilledButton.icon(
              onPressed: onCreate,
              icon: const Icon(Icons.add_rounded),
              label: const Text('Créer'),
            ),
          ],
        ),
        const SizedBox(height: 18),
        if (pages.isEmpty)
          _State(
            icon: Icons.verified_user_outlined,
            title: 'Créez votre identité Business',
            body:
                'Un organisateur identifié protège les participants et rend possible le contrôle QR.',
            action: onCreate,
            actionLabel: 'Créer mon espace Business',
          )
        else if (loading && events.isEmpty)
          const Padding(
            padding: EdgeInsets.all(42),
            child: Center(child: CircularProgressIndicator()),
          )
        else if (error != null && events.isEmpty)
          _State(
            icon: Icons.cloud_off_outlined,
            title: 'Impossible de charger',
            body: error!,
            action: onRefresh,
          )
        else if (events.isEmpty)
          _State(
            icon: Icons.add_business_outlined,
            title: 'Votre premier événement commence ici',
            body:
                'Préparez une affiche, les accès, le lieu et le nombre de places.',
            action: onCreate,
            actionLabel: 'Créer un événement',
          )
        else
          ...events.map(
            (event) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _EventCard(
                event: event,
                organizer: true,
                onTap: () => onOpen(event),
              ),
            ),
          ),
      ],
    ),
  );
}

class _EventCard extends StatelessWidget {
  const _EventCard({
    required this.event,
    required this.onTap,
    this.organizer = false,
  });
  final Map<String, dynamic> event;
  final VoidCallback onTap;
  final bool organizer;
  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Row(
        children: [
          SizedBox(
            width: 105,
            height: 112,
            child: _Poster(event: event, height: 112),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _string(
                      event['category'],
                      fallback: 'ÉVÉNEMENT',
                    ).toUpperCase(),
                    style: const TextStyle(
                      color: Color(0xFF00856A),
                      fontWeight: FontWeight.w900,
                      fontSize: 10,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _string(event['title']),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _date(_integer(event['startsAt'])),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: WapiColors.muted,
                      fontSize: 12,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    organizer
                        ? _integer(event['reserved']).toString() +
                              ' billets · ' +
                              _integer(event['checkedIn']).toString() +
                              ' entrées'
                        : (_integer(event['capacity']) -
                                      _integer(event['reserved']))
                                  .clamp(0, 99999)
                                  .toString() +
                              ' places',
                    style: const TextStyle(
                      color: Color(0xFF087D62),
                      fontWeight: FontWeight.w800,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _Poster extends StatelessWidget {
  const _Poster({required this.event, required this.height});
  final Map<String, dynamic> event;
  final double height;
  @override
  Widget build(BuildContext context) {
    final url = _string(event['posterUrl']);
    if (url.isNotEmpty)
      return Image.network(
        url,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => _fallback(),
      );
    return _fallback();
  }

  Widget _fallback() => Container(
    height: height,
    decoration: const BoxDecoration(
      gradient: LinearGradient(colors: [Color(0xFF03283C), Color(0xFF00A884)]),
    ),
    child: const Center(
      child: Icon(Icons.event_rounded, color: Colors.white, size: 38),
    ),
  );
}

class _TicketCard extends StatelessWidget {
  const _TicketCard({required this.ticket, required this.onTap});
  final Map<String, dynamic> ticket;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      onTap: onTap,
      contentPadding: const EdgeInsets.all(14),
      leading: const CircleAvatar(
        backgroundColor: Color(0xFFEAF8F4),
        foregroundColor: Color(0xFF087D62),
        child: Icon(Icons.confirmation_number_rounded),
      ),
      title: Text(
        _string(ticket['title']),
        style: const TextStyle(fontWeight: FontWeight.w900),
      ),
      subtitle: Text(
        _date(_integer(ticket['startsAt'])) + '\n' + _string(ticket['venue']),
      ),
      isThreeLine: true,
      trailing: const Icon(Icons.qr_code_rounded, color: Color(0xFF062233)),
    ),
  );
}

class _TicketView extends StatelessWidget {
  const _TicketView({required this.ticket, this.onCancel});
  final Map<String, dynamic> ticket;
  final Future<void> Function()? onCancel;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 42,
            height: 4,
            decoration: BoxDecoration(
              color: WapiColors.line,
              borderRadius: BorderRadius.circular(8),
            ),
          ),
          const SizedBox(height: 16),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: const Color(0xFF061D2D),
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Text(
              'TICKETBULK · Billet personnel',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w900,
              ),
            ),
          ),
          const SizedBox(height: 18),
          Text(
            _string(ticket['title']),
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 6),
          Text(
            _date(_integer(ticket['startsAt'])) +
                ' · ' +
                _string(ticket['venue']),
            textAlign: TextAlign.center,
            style: const TextStyle(color: WapiColors.muted),
          ),
          const SizedBox(height: 18),
          if (_string(ticket['status']) == 'issued')
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                border: Border.all(color: WapiColors.line),
                borderRadius: BorderRadius.circular(20),
              ),
              child: QrImageView(data: _string(ticket['code']), size: 220),
            )
          else
            const Text(
              'Ce billet n’est plus valide.',
              style: TextStyle(color: WapiColors.muted),
            ),
          const SizedBox(height: 10),
          const Text(
            'Ne partagez pas ce QR : il donne accès à votre place.',
            textAlign: TextAlign.center,
            style: TextStyle(color: WapiColors.muted, fontSize: 12),
          ),
          if (onCancel != null) ...[
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: () async {
                final cancel = await showDialog<bool>(
                  context: context,
                  builder: (dialogContext) => AlertDialog(
                    title: const Text('Annuler ce billet ?'),
                    content: const Text(
                      'Votre QR sera invalidé et la place redeviendra disponible.',
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(dialogContext, false),
                        child: const Text('Garder'),
                      ),
                      FilledButton(
                        onPressed: () => Navigator.pop(dialogContext, true),
                        child: const Text('Annuler le billet'),
                      ),
                    ],
                  ),
                );
                if (cancel == true) await onCancel!();
              },
              icon: const Icon(Icons.cancel_outlined),
              label: const Text('Annuler mon billet'),
            ),
          ],
        ],
      ),
    ),
  );
}

class _TicketCheckInPage extends StatefulWidget {
  const _TicketCheckInPage({required this.api, required this.event});
  final _CommerceApi api;
  final Map<String, dynamic> event;

  @override
  State<_TicketCheckInPage> createState() => _TicketCheckInPageState();
}

class _TicketCheckInPageState extends State<_TicketCheckInPage> {
  final _scanner = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
    returnImage: false,
  );
  final _manual = TextEditingController();
  bool _checking = false;
  bool _acceptedAny = false;
  String _headline = 'Scannez un billet';
  String _detail =
      'Le contrôle est sécurisé et chaque QR ne peut entrer qu’une fois.';
  Color _statusColor = const Color(0xFF0B8570);

  @override
  void dispose() {
    _scanner.dispose();
    _manual.dispose();
    super.dispose();
  }

  Future<void> _check(String code) async {
    if (_checking || code.trim().isEmpty) return;
    setState(() {
      _checking = true;
      _headline = 'Vérification…';
      _detail = 'Contrôle auprès du serveur WAPI.';
      _statusColor = const Color(0xFF356D8A);
    });
    try {
      final result = await widget.api.call('checkTicket', {
        'eventId': _string(widget.event['id']),
        'code': code.trim(),
      });
      final status = _string(result['status']);
      final accepted = status == 'accepted';
      setState(() {
        _acceptedAny = _acceptedAny || accepted;
        _headline = accepted
            ? 'Entrée validée'
            : status == 'already-used'
            ? 'Billet déjà utilisé'
            : 'Billet non valide';
        _detail = accepted
            ? _string(result['title'], fallback: 'Participant autorisé')
            : status == 'already-used'
            ? 'Ce QR a déjà été présenté à l’entrée.'
            : 'Aucune entrée n’a été enregistrée.';
        _statusColor = accepted
            ? const Color(0xFF0B8570)
            : const Color(0xFFB42318);
      });
    } catch (error) {
      setState(() {
        _headline = 'Entrée refusée';
        _detail = _errorText(error);
        _statusColor = const Color(0xFFB42318);
      });
    } finally {
      if (mounted) {
        _manual.clear();
        setState(() => _checking = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFF061D2D),
    appBar: AppBar(
      backgroundColor: const Color(0xFF061D2D),
      foregroundColor: Colors.white,
      title: const Text(
        'Contrôle des entrées',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      leading: IconButton(
        icon: const Icon(Icons.close_rounded),
        onPressed: () => Navigator.pop(context, _acceptedAny),
      ),
    ),
    body: SafeArea(
      top: false,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _string(widget.event['title'], fallback: 'Événement'),
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w900,
                    fontSize: 20,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  _integer(widget.event['checkedIn']).toString() +
                      ' entrées déjà contrôlées',
                  style: const TextStyle(color: Color(0xFFA5D8CB)),
                ),
              ],
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 16),
              clipBehavior: Clip.antiAlias,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(26),
                border: Border.all(color: const Color(0xFF1B7681), width: 2),
              ),
              child: MobileScanner(
                controller: _scanner,
                onDetect: (capture) {
                  final codes = capture.barcodes
                      .map((barcode) => barcode.rawValue)
                      .whereType<String>();
                  if (codes.isNotEmpty) _check(codes.first);
                },
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
            child: Column(
              children: [
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: _statusColor,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Column(
                    children: [
                      Icon(
                        _statusColor == const Color(0xFF0B8570)
                            ? Icons.verified_rounded
                            : Icons.info_outline_rounded,
                        color: Colors.white,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _headline,
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w900,
                          fontSize: 18,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        _detail,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.white),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _manual,
                  minLines: 1,
                  maxLines: 2,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    filled: true,
                    fillColor: Color(0xFF103145),
                    labelText: 'Saisir ou coller le code du billet',
                    labelStyle: TextStyle(color: Color(0xFFA5D8CB)),
                  ),
                ),
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: _checking ? null : () => _check(_manual.text),
                  style: FilledButton.styleFrom(
                    backgroundColor: const Color(0xFF00A884),
                    minimumSize: const Size.fromHeight(50),
                  ),
                  icon: const Icon(Icons.verified_user_rounded),
                  label: Text(_checking ? 'Vérification…' : 'Vérifier le code'),
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _Detail extends StatelessWidget {
  const _Detail({required this.icon, required this.value});
  final IconData icon;
  final String value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 8),
    child: Row(
      children: [
        Icon(icon, color: const Color(0xFF00856A), size: 19),
        const SizedBox(width: 9),
        Expanded(
          child: Text(value, style: const TextStyle(color: Color(0xFF43515B))),
        ),
      ],
    ),
  );
}

class _State extends StatelessWidget {
  const _State({
    required this.icon,
    required this.title,
    required this.body,
    this.action,
    this.actionLabel = 'Réessayer',
  });
  final IconData icon;
  final String title;
  final String body;
  final Future<void> Function()? action;
  final String actionLabel;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 48, horizontal: 18),
    child: Column(
      children: [
        Icon(icon, size: 42, color: const Color(0xFF65808B)),
        const SizedBox(height: 12),
        Text(
          title,
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 6),
        Text(
          body,
          textAlign: TextAlign.center,
          style: const TextStyle(color: WapiColors.muted, height: 1.35),
        ),
        if (action != null) ...[
          const SizedBox(height: 15),
          FilledButton(onPressed: () => action!(), child: Text(actionLabel)),
        ],
      ],
    ),
  );
}

class _BusinessPageForm extends StatefulWidget {
  const _BusinessPageForm({required this.user});
  final User user;
  @override
  State<_BusinessPageForm> createState() => _BusinessPageFormState();
}

class _BusinessPageFormState extends State<_BusinessPageForm> {
  final _name = TextEditingController();
  final _category = TextEditingController();
  final _city = TextEditingController(text: 'Brazzaville');
  bool _saving = false;
  @override
  void dispose() {
    _name.dispose();
    _category.dispose();
    _city.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_name.text.trim().length < 2 || _category.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Indiquez le nom et la catégorie de votre activité.'),
        ),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      final ref = FirebaseFirestore.instance.collection('businessPages').doc();
      await ref.set({
        'ownerId': widget.user.uid,
        'name': _name.text.trim(),
        'searchName': _name.text.trim().toLowerCase(),
        'handle': 'wapi-' + ref.id.substring(0, 6),
        'type': 'business',
        'category': _category.text.trim(),
        'bio': '',
        'city': _city.text.trim().isEmpty ? 'Brazzaville' : _city.text.trim(),
        'phone': widget.user.phoneNumber ?? '',
        'website': '',
        'status': 'active',
        'followers': 0,
        // Ces deux états sont imposés par les règles Firestore. Ils restent
        // verrouillés côté client : seul le processus d’administration peut
        // ultérieurement attribuer un badge à une page Business.
        'verified': false,
        'verificationStatus': 'unverified',
        'createdAt': FieldValue.serverTimestamp(),
        'updatedAt': FieldValue.serverTimestamp(),
      });
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_errorText(error))));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      20,
      20,
      20,
      MediaQuery.viewInsetsOf(context).bottom + 24,
    ),
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Votre activité sur WAPI',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 6),
          const Text(
            'Cette identité sera affichée comme organisateur de vos événements.',
            style: TextStyle(color: WapiColors.muted),
          ),
          const SizedBox(height: 18),
          TextField(
            controller: _name,
            maxLength: 80,
            decoration: const InputDecoration(labelText: 'Nom de l’activité'),
          ),
          TextField(
            controller: _category,
            maxLength: 60,
            decoration: const InputDecoration(labelText: 'Catégorie'),
          ),
          TextField(
            controller: _city,
            maxLength: 80,
            decoration: const InputDecoration(labelText: 'Ville'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _saving ? null : _save,
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
            ),
            child: Text(_saving ? 'Création…' : 'Créer mon espace Business'),
          ),
        ],
      ),
    ),
  );
}

class _EventForm extends StatefulWidget {
  const _EventForm({required this.api, required this.pages});
  final _CommerceApi api;
  final List<Map<String, dynamic>> pages;
  @override
  State<_EventForm> createState() => _EventFormState();
}

class _EventFormState extends State<_EventForm> {
  final _title = TextEditingController();
  final _venue = TextEditingController();
  final _description = TextEditingController();
  final _category = TextEditingController(text: 'Événement');
  final _ticket = TextEditingController(text: 'Accès général');
  final _capacity = TextEditingController(text: '100');
  final _agenda = TextEditingController();
  final _contact = TextEditingController();
  final _terms = TextEditingController();
  late String _page = _string(widget.pages.first['id']);
  DateTime _starts = DateTime.now().add(const Duration(days: 1, hours: 2));
  late DateTime _doors = _starts.subtract(const Duration(hours: 1));
  File? _poster;
  bool _saving = false;
  int _step = 0;
  @override
  void dispose() {
    _title.dispose();
    _venue.dispose();
    _description.dispose();
    _category.dispose();
    _ticket.dispose();
    _capacity.dispose();
    _agenda.dispose();
    _contact.dispose();
    _terms.dispose();
    super.dispose();
  }

  Future<void> _posterPick() async {
    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 88,
      maxWidth: 1600,
    );
    if (picked == null || !mounted) return;
    final file = File(picked.path);
    if (await file.length() > 5 * 1024 * 1024) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Choisissez une affiche de moins de 5 Mo.'),
          ),
        );
      return;
    }
    setState(() => _poster = file);
  }

  Future<void> _datePick() async {
    final day = await showDatePicker(
      context: context,
      initialDate: _starts,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 730)),
    );
    if (day == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_starts),
    );
    if (time != null && mounted) {
      setState(() {
        _starts = DateTime(
          day.year,
          day.month,
          day.day,
          time.hour,
          time.minute,
        );
        if (!_doors.isBefore(_starts) || _doors.day != _starts.day) {
          _doors = _starts.subtract(const Duration(hours: 1));
        }
      });
    }
  }

  Future<void> _doorsPick() async {
    final value = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_doors),
      helpText: 'HEURE D’OUVERTURE DES PORTES',
    );
    if (value == null || !mounted) return;
    final next = DateTime(
      _starts.year,
      _starts.month,
      _starts.day,
      value.hour,
      value.minute,
    );
    if (!next.isBefore(_starts)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('L’ouverture des portes doit précéder le début.'),
        ),
      );
      return;
    }
    setState(() => _doors = next);
  }

  void _continue() {
    if (_step == 0 && _title.text.trim().length < 2) {
      _notice('Ajoutez le nom de l’événement.');
      return;
    }
    if (_step == 1 && _venue.text.trim().length < 2) {
      _notice('Ajoutez le lieu ou les informations d’accès.');
      return;
    }
    setState(() => _step = (_step + 1).clamp(0, 2));
  }

  void _notice(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _save() async {
    final capacity = int.tryParse(_capacity.text) ?? 0;
    if (_title.text.trim().length < 2 ||
        _venue.text.trim().length < 2 ||
        capacity < 1) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Complétez le nom, le lieu et le nombre de places.'),
        ),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      String? photo;
      if (_poster != null)
        photo =
            'data:image/jpeg;base64,' +
            base64Encode(await _poster!.readAsBytes());
      await widget.api.call('createEvent', {
        'eventId': 'evt-' + DateTime.now().microsecondsSinceEpoch.toString(),
        'pageId': _page,
        'title': _title.text.trim(),
        'venue': _venue.text.trim(),
        'description': _description.text.trim(),
        'category': _category.text.trim(),
        'ticketLabel': _ticket.text.trim(),
        'agenda': _agenda.text.trim(),
        'contact': _contact.text.trim(),
        'terms': _terms.text.trim(),
        'capacity': capacity,
        'doorsAt': _doors.millisecondsSinceEpoch,
        'startsAt': _starts.millisecondsSinceEpoch,
        if (photo != null) 'posterBase64': photo,
      });
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_errorText(error))));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      ListView(
        key: const ValueKey('event-identity'),
        padding: const EdgeInsets.fromLTRB(18, 8, 18, 24),
        children: [
          const _EventStepTitle(
            icon: Icons.auto_awesome_rounded,
            title: 'Identité de l’événement',
            subtitle:
                'Une affiche claire et un titre précis inspirent confiance.',
          ),
          DropdownButtonFormField<String>(
            initialValue: _page,
            items: widget.pages
                .map(
                  (page) => DropdownMenuItem(
                    value: _string(page['id']),
                    child: Text(
                      _string(page['name'], fallback: 'Business WAPI'),
                    ),
                  ),
                )
                .toList(),
            onChanged: _saving
                ? null
                : (value) => setState(() => _page = value ?? _page),
            decoration: const InputDecoration(labelText: 'Organisateur'),
          ),
          const SizedBox(height: 14),
          InkWell(
            onTap: _saving ? null : _posterPick,
            borderRadius: BorderRadius.circular(20),
            child: Ink(
              height: 190,
              decoration: BoxDecoration(
                color: const Color(0xFFEAF8F4),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: const Color(0xFFB7DED1)),
              ),
              child: _poster == null
                  ? const Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.add_photo_alternate_outlined,
                          color: Color(0xFF087D62),
                          size: 38,
                        ),
                        SizedBox(height: 8),
                        Text(
                          'Ajouter l’affiche',
                          style: TextStyle(
                            fontWeight: FontWeight.w900,
                            color: Color(0xFF176451),
                          ),
                        ),
                        Text(
                          'Format vertical ou paysage · 5 Mo maximum',
                          style: TextStyle(
                            fontSize: 11,
                            color: Color(0xFF176451),
                          ),
                        ),
                      ],
                    )
                  : Stack(
                      fit: StackFit.expand,
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(20),
                          child: Image.file(_poster!, fit: BoxFit.cover),
                        ),
                        Positioned(
                          right: 10,
                          bottom: 10,
                          child: IconButton.filledTonal(
                            onPressed: _posterPick,
                            icon: const Icon(Icons.edit_rounded),
                            tooltip: 'Changer l’affiche',
                          ),
                        ),
                      ],
                    ),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _title,
            maxLength: 100,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(labelText: 'Nom de l’événement'),
          ),
          TextField(
            controller: _category,
            maxLength: 60,
            decoration: const InputDecoration(
              labelText: 'Catégorie · concert, sport, formation…',
            ),
          ),
        ],
      ),
      ListView(
        key: const ValueKey('event-details'),
        padding: const EdgeInsets.fromLTRB(18, 8, 18, 24),
        children: [
          const _EventStepTitle(
            icon: Icons.location_on_outlined,
            title: 'Lieu et programme',
            subtitle: 'Donnez toutes les informations utiles aux participants.',
          ),
          TextField(
            controller: _description,
            maxLength: 1200,
            minLines: 3,
            maxLines: 5,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(labelText: 'Description'),
          ),
          TextField(
            controller: _venue,
            maxLength: 160,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Lieu et adresse'),
          ),
          OutlinedButton.icon(
            onPressed: _saving ? null : _datePick,
            icon: const Icon(Icons.calendar_month_outlined),
            label: Text('Début · ${_date(_starts.millisecondsSinceEpoch)}'),
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            onPressed: _saving ? null : _doorsPick,
            icon: const Icon(Icons.door_front_door_outlined),
            label: Text(
              'Ouverture · ${_doors.hour.toString().padLeft(2, '0')}:${_doors.minute.toString().padLeft(2, '0')}',
            ),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _contact,
            maxLength: 120,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(
              labelText: 'Contact de l’organisateur',
              hintText: 'Téléphone ou adresse e-mail',
            ),
          ),
        ],
      ),
      ListView(
        key: const ValueKey('event-tickets'),
        padding: const EdgeInsets.fromLTRB(18, 8, 18, 24),
        children: [
          const _EventStepTitle(
            icon: Icons.confirmation_number_outlined,
            title: 'Billets et publication',
            subtitle: 'Configurez les accès puis vérifiez le résumé.',
          ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextField(
                  controller: _capacity,
                  keyboardType: TextInputType.number,
                  maxLength: 5,
                  decoration: const InputDecoration(labelText: 'Places'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                flex: 2,
                child: TextField(
                  controller: _ticket,
                  maxLength: 60,
                  decoration: const InputDecoration(labelText: 'Nom du billet'),
                ),
              ),
            ],
          ),
          TextField(
            controller: _agenda,
            maxLength: 1800,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Programme ou agenda',
              hintText: 'Accueil, première partie, intervenants…',
            ),
          ),
          TextField(
            controller: _terms,
            maxLength: 800,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Conditions d’accès',
              hintText: 'Âge minimum, tenue, objets interdits…',
            ),
          ),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFFEAF8F4),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(
                  Icons.verified_user_outlined,
                  color: Color(0xFF087D62),
                ),
                const SizedBox(width: 11),
                Expanded(
                  child: Text(
                    '${_title.text.trim().isEmpty ? 'Votre événement' : _title.text.trim()}\n${_date(_starts.millisecondsSinceEpoch)} · ${_venue.text.trim().isEmpty ? 'Lieu à confirmer' : _venue.text.trim()}\nUn QR personnel et vérifiable sera créé pour chaque réservation.',
                    style: const TextStyle(
                      color: Color(0xFF176451),
                      height: 1.4,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          const Text(
            'Les réservations restent gratuites jusqu’à l’activation sécurisée du Mobile Money régional.',
            style: TextStyle(color: WapiColors.muted, fontSize: 12),
          ),
        ],
      ),
    ];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Créer un événement'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: Text(
                '${_step + 1}/3',
                style: const TextStyle(
                  color: WapiColors.muted,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          LinearProgressIndicator(
            value: (_step + 1) / 3,
            minHeight: 4,
            color: const Color(0xFF00A884),
            backgroundColor: const Color(0xFFE0ECE8),
          ),
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 220),
              switchInCurve: Curves.easeOutCubic,
              child: pages[_step],
            ),
          ),
          SafeArea(
            top: false,
            child: Container(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(top: BorderSide(color: WapiColors.line)),
              ),
              child: Row(
                children: [
                  if (_step > 0) ...[
                    OutlinedButton(
                      onPressed: _saving ? null : () => setState(() => _step--),
                      child: const Text('Retour'),
                    ),
                    const SizedBox(width: 10),
                  ],
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: _saving
                          ? null
                          : _step < 2
                          ? _continue
                          : _save,
                      icon: _saving
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : Icon(
                              _step < 2
                                  ? Icons.arrow_forward_rounded
                                  : Icons.publish_rounded,
                            ),
                      label: Text(
                        _saving
                            ? 'Publication…'
                            : _step < 2
                            ? 'Continuer'
                            : 'Publier l’événement',
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EventStepTitle extends StatelessWidget {
  const _EventStepTitle({
    required this.icon,
    required this.title,
    required this.subtitle,
  });
  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(0, 12, 0, 20),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          backgroundColor: const Color(0xFFEAF8F4),
          foregroundColor: const Color(0xFF087D62),
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
                  fontSize: 21,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 3),
              Text(subtitle, style: const TextStyle(color: WapiColors.muted)),
            ],
          ),
        ),
      ],
    ),
  );
}

class _CommerceApi {
  final FirebaseFunctions _functions = FirebaseFunctions.instanceFor(
    region: 'europe-west1',
  );
  Future<Map<String, dynamic>> call(
    String action, [
    Map<String, dynamic> data = const {},
  ]) async {
    final result = await _functions
        .httpsCallable('wapiCommerce')
        .call<Map<String, dynamic>>({'action': action, ...data})
        .timeout(const Duration(seconds: 20));
    return _map(result.data);
  }
}

List<Map<String, dynamic>> _list(Object? value) => (value as List? ?? const [])
    .whereType<Map>()
    .map(_map)
    .toList(growable: false);
Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
String _string(Object? value, {String fallback = ''}) =>
    value is String && value.trim().isNotEmpty ? value.trim() : fallback;
int _integer(Object? value) => value is num ? value.toInt() : 0;
String _date(int epoch) {
  final date = DateTime.fromMillisecondsSinceEpoch(epoch);
  const names = [
    'janv.',
    'févr.',
    'mars',
    'avr.',
    'mai',
    'juin',
    'juil.',
    'août',
    'sept.',
    'oct.',
    'nov.',
    'déc.',
  ];
  return date.day.toString() +
      ' ' +
      names[date.month - 1] +
      ' ' +
      date.year.toString() +
      ' · ' +
      date.hour.toString().padLeft(2, '0') +
      ':' +
      date.minute.toString().padLeft(2, '0');
}

String _errorText(Object error) => wapiErrorText(
  error is FirebaseFunctionsException ? error.message : error,
  fallback:
      'Une action TicketBulk n’a pas pu être terminée. Vérifiez la connexion puis réessayez.',
);
