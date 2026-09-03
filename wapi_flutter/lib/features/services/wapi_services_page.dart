import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../../app/wapi_theme.dart';

class WapiServicesPage extends StatefulWidget {
  const WapiServicesPage({super.key, required this.user});
  final User user;

  @override
  State<WapiServicesPage> createState() => _WapiServicesPageState();
}

class _WapiServicesPageState extends State<WapiServicesPage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  bool _loading = true;
  bool _sending = false;
  String? _error;
  List<Map<String, dynamic>> _requests = const [];

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
      final result = await _functions
          .httpsCallable('myServiceRequests')
          .call<Map<String, dynamic>>();
      final raw = result.data['requests'];
      if (mounted) {
        setState(() {
          _requests = raw is List
              ? raw
                    .whereType<Map>()
                    .map((item) => Map<String, dynamic>.from(item))
                    .toList()
              : const [];
        });
      }
    } on FirebaseFunctionsException catch (error) {
      if (mounted)
        setState(
          () => _error = error.message ?? 'Services WAPI indisponibles.',
        );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _request(_ServiceKind kind) async {
    final result = await showModalBottomSheet<Map<String, String>>(
      context: context,
      isScrollControlled: true,
      builder: (context) => _ServiceRequestSheet(kind: kind),
    );
    if (result == null || _sending) return;
    setState(() => _sending = true);
    try {
      await _functions
          .httpsCallable('createServiceRequest')
          .call<Map<String, dynamic>>({
            'type': kind.id,
            'details': result['details'] ?? '',
            'city': result['city'] ?? '',
          });
      _notice('Demande envoyée. Vous la retrouvez dans vos activités.');
      await _load();
    } on FirebaseFunctionsException catch (error) {
      _notice(
        error.message ?? 'La demande n’a pas pu être envoyée.',
        error: true,
      );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _notice(String message, {bool error = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: error
            ? const Color(0xFFB42318)
            : const Color(0xFF087D62),
        content: Text(message),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'Services WAPI',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: _loading ? null : _load,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
        children: [
          Container(
            padding: const EdgeInsets.all(19),
            decoration: BoxDecoration(
              color: const Color(0xFFF6F0E8),
              borderRadius: BorderRadius.circular(24),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  Icons.workspace_premium_rounded,
                  color: Color(0xFFF29B22),
                  size: 34,
                ),
                SizedBox(height: 10),
                Text(
                  'Vos services, au même endroit',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                    color: Color(0xFF1D2633),
                  ),
                ),
                SizedBox(height: 5),
                Text(
                  'Demandez un déplacement, une livraison ou une assistance. La mise en relation et tout paiement restent confirmés dans WAPI.',
                  style: TextStyle(color: WapiColors.muted),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            'Demander un service',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 10),
          _ServiceCard(
            kind: _ServiceKind.transport,
            disabled: _sending,
            onTap: () => _request(_ServiceKind.transport),
          ),
          const SizedBox(height: 10),
          _ServiceCard(
            kind: _ServiceKind.delivery,
            disabled: _sending,
            onTap: () => _request(_ServiceKind.delivery),
          ),
          const SizedBox(height: 10),
          _ServiceCard(
            kind: _ServiceKind.assistance,
            disabled: _sending,
            onTap: () => _request(_ServiceKind.assistance),
          ),
          const SizedBox(height: 22),
          const Text(
            'Mes demandes',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 10),
          if (_loading)
            const Padding(
              padding: EdgeInsets.all(28),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (_error != null)
            _ServiceError(text: _error!, onRetry: _load)
          else if (_requests.isEmpty)
            const _EmptyRequests()
          else
            ..._requests.map(_RequestCard.new),
        ],
      ),
    ),
  );
}

enum _ServiceKind {
  transport(
    'transport',
    'Transport',
    'Déplacement, prise en charge ou course',
    Icons.directions_car_filled,
    Color(0xFF087DCE),
  ),
  delivery(
    'delivery',
    'Livraison',
    'Colis, repas et courses à faire livrer',
    Icons.local_shipping_rounded,
    Color(0xFF00A884),
  ),
  assistance(
    'assistance',
    'Assistance',
    'Aide à domicile, dépannage ou besoin ponctuel',
    Icons.support_agent_rounded,
    Color(0xFFF29B22),
  );

  const _ServiceKind(this.id, this.title, this.detail, this.icon, this.color);
  final String id;
  final String title;
  final String detail;
  final IconData icon;
  final Color color;
}

class _ServiceCard extends StatelessWidget {
  const _ServiceCard({
    required this.kind,
    required this.disabled,
    required this.onTap,
  });
  final _ServiceKind kind;
  final bool disabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      enabled: !disabled,
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      leading: CircleAvatar(
        backgroundColor: kind.color.withValues(alpha: .14),
        foregroundColor: kind.color,
        child: Icon(kind.icon),
      ),
      title: Text(
        kind.title,
        style: const TextStyle(fontWeight: FontWeight.w900),
      ),
      subtitle: Text(kind.detail),
      trailing: const Icon(Icons.add_circle_outline_rounded),
    ),
  );
}

class _ServiceRequestSheet extends StatefulWidget {
  const _ServiceRequestSheet({required this.kind});
  final _ServiceKind kind;

  @override
  State<_ServiceRequestSheet> createState() => _ServiceRequestSheetState();
}

class _ServiceRequestSheetState extends State<_ServiceRequestSheet> {
  final _details = TextEditingController();
  final _city = TextEditingController();

  @override
  void dispose() {
    _details.dispose();
    _city.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      20,
      20,
      20,
      MediaQuery.viewInsetsOf(context).bottom + 24,
    ),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            CircleAvatar(
              backgroundColor: widget.kind.color.withValues(alpha: .14),
              foregroundColor: widget.kind.color,
              child: Icon(widget.kind.icon),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                widget.kind.title,
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TextField(
          controller: _city,
          maxLength: 80,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(
            labelText: 'Ville ou quartier (facultatif)',
          ),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _details,
          minLines: 3,
          maxLines: 6,
          maxLength: 800,
          textCapitalization: TextCapitalization.sentences,
          decoration: const InputDecoration(
            labelText: 'Décrivez précisément votre besoin',
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          'Vous confirmez le prestataire et le paiement avant toute intervention.',
          style: TextStyle(color: WapiColors.muted, fontSize: 12),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: () {
            if (_details.text.trim().length < 5) return;
            Navigator.pop(context, {
              'details': _details.text.trim(),
              'city': _city.text.trim(),
            });
          },
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(52),
            backgroundColor: widget.kind.color,
          ),
          child: const Text('Envoyer la demande'),
        ),
      ],
    ),
  );
}

class _RequestCard extends StatelessWidget {
  const _RequestCard(this.request);
  final Map<String, dynamic> request;

  @override
  Widget build(BuildContext context) {
    final type = request['type']?.toString() ?? 'assistance';
    final kind = _ServiceKind.values.firstWhere(
      (item) => item.id == type,
      orElse: () => _ServiceKind.assistance,
    );
    final status = request['status']?.toString() == 'requested'
        ? 'Demande envoyée'
        : request['status']?.toString() ?? 'En cours';
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        leading: Icon(kind.icon, color: kind.color),
        title: Text(
          kind.title,
          style: const TextStyle(fontWeight: FontWeight.w900),
        ),
        subtitle: Text(
          (request['details']?.toString() ?? '') +
              ((request['city']?.toString() ?? '').isEmpty
                  ? ''
                  : ' · ' + request['city'].toString()),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: Text(
          status,
          textAlign: TextAlign.end,
          style: TextStyle(
            color: kind.color,
            fontSize: 11,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _EmptyRequests extends StatelessWidget {
  const _EmptyRequests();
  @override
  Widget build(BuildContext context) => const Card(
    child: Padding(
      padding: EdgeInsets.all(22),
      child: Column(
        children: [
          Icon(Icons.assignment_outlined, color: WapiColors.muted, size: 34),
          SizedBox(height: 8),
          Text(
            'Aucune demande en cours',
            style: TextStyle(fontWeight: FontWeight.w900),
          ),
          SizedBox(height: 4),
          Text(
            'Vos demandes de service apparaîtront ici.',
            textAlign: TextAlign.center,
            style: TextStyle(color: WapiColors.muted),
          ),
        ],
      ),
    ),
  );
}

class _ServiceError extends StatelessWidget {
  const _ServiceError({required this.text, required this.onRetry});
  final String text;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Card(
    color: const Color(0xFFFFEDEB),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(text, style: const TextStyle(color: Color(0xFFB42318))),
          TextButton(onPressed: onRetry, child: const Text('Réessayer')),
        ],
      ),
    ),
  );
}
