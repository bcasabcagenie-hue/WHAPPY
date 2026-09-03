import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../app/wapi_theme.dart';
import '../wia/wia_chat_page.dart';
import 'wapi_billing_page.dart';

/// Unified Flutter surface for WAPI Business and Marketplace. The server owns
/// publication, offers and boost state; the client only presents real data.
class WapiCommercePage extends StatefulWidget {
  const WapiCommercePage({super.key, required this.user, this.initialTab = 0});
  final User user;
  final int initialTab;

  @override
  State<WapiCommercePage> createState() => _WapiCommercePageState();
}

class _WapiCommercePageState extends State<WapiCommercePage> {
  final _api = _CommerceApi();
  late int _tab = widget.initialTab.clamp(0, 1);
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _pages = const [];
  List<Map<String, dynamic>> _listings = const [];

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
      final result = await Future.wait([
        _api.call('ownPages'),
        _api.call('marketplaceListings'),
      ]);
      if (!mounted) return;
      setState(() {
        _pages = _maps(result[0]['pages']);
        _listings = _maps(result[1]['listings']);
      });
    } catch (error) {
      if (mounted) setState(() => _error = _errorText(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createBusiness() async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => _BusinessIdentity(user: widget.user),
    );
    if (created == true) await _refresh();
  }

  Future<void> _createListing() async {
    if (_pages.isEmpty) {
      _toast(
        'Créez d’abord votre compte Business pour publier une annonce.',
        error: true,
      );
      await _createBusiness();
      return;
    }
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => _ListingForm(api: _api, pages: _pages),
      ),
    );
    if (created == true) await _refresh();
  }

  Future<void> _openBilling() async {
    if (_pages.isEmpty) {
      await _createBusiness();
      return;
    }
    await Navigator.of(
      context,
    ).push(MaterialPageRoute(builder: (_) => WapiBillingPage(pages: _pages)));
    await _refresh();
  }

  Future<void> _listingDetails(Map<String, dynamic> listing) async {
    final mine = _string(listing['ownerId']) == widget.user.uid;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => DraggableScrollableSheet(
        initialChildSize: .78,
        minChildSize: .45,
        maxChildSize: .94,
        builder: (_, controller) => Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          ),
          child: ListView(
            controller: controller,
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
            children: [
              Center(
                child: Container(
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                    color: WapiColors.line,
                    borderRadius: BorderRadius.circular(9),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              _ListingImage(
                url: _firstPhoto(listing),
                label: _string(listing['title']),
                height: 230,
              ),
              const SizedBox(height: 18),
              Text(
                _modeLabel(_string(listing['mode'])).toUpperCase(),
                style: const TextStyle(
                  color: Color(0xFF087D62),
                  fontWeight: FontWeight.w900,
                  fontSize: 11,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                _string(listing['title']),
                style: const TextStyle(
                  fontSize: 27,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 7),
              Text(
                _string(listing['priceText'], fallback: 'Prix à discuter'),
                style: const TextStyle(
                  color: Color(0xFF00856A),
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 10),
              _CommerceInfo(
                icon: Icons.storefront_outlined,
                value: _string(listing['pageName'], fallback: 'Business WAPI'),
              ),
              _CommerceInfo(
                icon: Icons.location_on_outlined,
                value: _string(listing['place'], fallback: 'WAPI'),
              ),
              if (_string(listing['description']).isNotEmpty) ...[
                const SizedBox(height: 17),
                Text(
                  _string(listing['description']),
                  style: const TextStyle(
                    height: 1.45,
                    color: Color(0xFF43515B),
                  ),
                ),
              ],
              if (_string(listing['tradeWish']).isNotEmpty) ...[
                const SizedBox(height: 17),
                const Text(
                  'Échange recherché',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 6),
                Text(
                  _string(listing['tradeWish']),
                  style: const TextStyle(color: Color(0xFF43515B)),
                ),
              ],
              const SizedBox(height: 24),
              if (mine)
                FilledButton.icon(
                  onPressed: () => _boost(listing),
                  icon: const Icon(Icons.rocket_launch_outlined),
                  label: const Text('Préparer un boost'),
                )
              else
                FilledButton.icon(
                  onPressed: () => _respond(listing),
                  icon: Icon(_intentIcon(_string(listing['mode']))),
                  label: Text(_intentLabel(_string(listing['mode']))),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _respond(Map<String, dynamic> listing) async {
    final mode = _string(listing['mode']);
    final result = await showModalBottomSheet<Map<String, String>>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _IntentForm(mode: mode),
    );
    if (result == null) return;
    try {
      await _api.call('respondToMarketplaceListing', {
        'listingId': _string(listing['id']),
        'kind': _intentKind(mode),
        'offerText': result['offer'] ?? '',
        'note': result['note'] ?? '',
      });
      _toast('Votre réponse a été transmise à l’activité.');
    } catch (error) {
      _toast(_errorText(error), error: true);
    }
  }

  Future<void> _boost(Map<String, dynamic> listing) async {
    try {
      final result = await _api.call('boostMarketplaceListing', {
        'listingId': _string(listing['id']),
        'objective': 'visibility',
      });
      _toast(_string(result['notice'], fallback: 'Le plan de boost est prêt.'));
      await _refresh();
    } catch (error) {
      _toast(_errorText(error), error: true);
    }
  }

  void _toast(String value, {bool error = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: error
            ? const Color(0xFFB42318)
            : const Color(0xFF087D62),
        content: Text(value),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(
        _tab == 0 ? 'WAPI Business' : 'Marketplace',
        style: const TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: _loading ? null : _refresh,
          icon: const Icon(Icons.refresh_rounded),
        ),
        IconButton(
          onPressed: _tab == 0 ? _createBusiness : _createListing,
          icon: const Icon(Icons.add_rounded),
        ),
      ],
    ),
    body: IndexedStack(
      index: _tab,
      children: [
        _BusinessTab(
          pages: _pages,
          listings: _listings,
          loading: _loading,
          error: _error,
          onRefresh: _refresh,
          onCreatePage: _createBusiness,
          onCreateListing: _createListing,
          onOpenBilling: _openBilling,
          onOpenWia: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => WiaChatPage(user: widget.user)),
          ),
        ),
        _MarketplaceTab(
          listings: _listings,
          loading: _loading,
          error: _error,
          onRefresh: _refresh,
          onCreate: _createListing,
          onOpen: _listingDetails,
        ),
      ],
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: _tab,
      onDestinationSelected: (value) => setState(() => _tab = value),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.business_center_outlined),
          selectedIcon: Icon(Icons.business_center),
          label: 'Business',
        ),
        NavigationDestination(
          icon: Icon(Icons.storefront_outlined),
          selectedIcon: Icon(Icons.storefront),
          label: 'Marketplace',
        ),
      ],
    ),
  );
}

class _BusinessTab extends StatelessWidget {
  const _BusinessTab({
    required this.pages,
    required this.listings,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onCreatePage,
    required this.onCreateListing,
    required this.onOpenBilling,
    required this.onOpenWia,
  });
  final List<Map<String, dynamic>> pages;
  final List<Map<String, dynamic>> listings;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreatePage;
  final Future<void> Function() onCreateListing;
  final Future<void> Function() onOpenBilling;
  final VoidCallback onOpenWia;
  @override
  Widget build(BuildContext context) {
    final mine = listings
        .where(
          (item) => pages.any(
            (page) => _string(page['id']) == _string(item['pageId']),
          ),
        )
        .toList();
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF062233), Color(0xFF0B6A78)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.business_center_rounded,
                      color: Color(0xFF7CE5FF),
                    ),
                    SizedBox(width: 8),
                    Text(
                      'CENTRE BUSINESS',
                      style: TextStyle(
                        color: Color(0xFFB7D1DD),
                        fontWeight: FontWeight.w900,
                        letterSpacing: .8,
                        fontSize: 11,
                      ),
                    ),
                  ],
                ),
                SizedBox(height: 16),
                Text(
                  'Pilotez votre activité\ndepuis WAPI',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w900,
                    fontSize: 25,
                    height: 1.08,
                  ),
                ),
                SizedBox(height: 8),
                Text(
                  'Pages, catalogue, annonces et créances : les données affichées viennent de votre compte Business.',
                  style: TextStyle(color: Color(0xFFD8EFF3), height: 1.35),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          if (loading && pages.isEmpty)
            const Padding(
              padding: EdgeInsets.all(40),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (error != null && pages.isEmpty)
            _CommerceState(
              icon: Icons.cloud_off_outlined,
              title: 'Business indisponible',
              body: error!,
              action: onRefresh,
            )
          else if (pages.isEmpty)
            _CommerceState(
              icon: Icons.add_business_outlined,
              title: 'Créez votre espace Business',
              body:
                  'Publiez une activité, une boutique, un catalogue et des annonces depuis un profil séparé de votre compte personnel.',
              action: onCreatePage,
              actionLabel: 'Créer ma page',
            )
          else ...[
            _BusinessIdentityBanner(page: pages.first, onOpenWia: onOpenWia),
            const SizedBox(height: 14),
            _BusinessMetrics(
              pageCount: pages.length,
              listingCount: mine.length,
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onOpenBilling,
                    icon: const Icon(Icons.receipt_long_outlined),
                    label: const Text('Facturation'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: onCreateListing,
                    icon: const Icon(Icons.add_photo_alternate_outlined),
                    label: const Text('Annonce'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _BusinessBenefits(onBilling: onOpenBilling, onWia: onOpenWia),
            const SizedBox(height: 18),
            const Text(
              'MES ACTIVITÉS',
              style: TextStyle(
                color: WapiColors.muted,
                fontSize: 11,
                letterSpacing: .7,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 8),
            ...pages.map(
              (page) => _BusinessCard(
                page: page,
                listingCount: mine
                    .where(
                      (item) => _string(item['pageId']) == _string(page['id']),
                    )
                    .length,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _BusinessIdentityBanner extends StatelessWidget {
  const _BusinessIdentityBanner({required this.page, required this.onOpenWia});
  final Map<String, dynamic> page;
  final VoidCallback onOpenWia;
  @override
  Widget build(BuildContext context) {
    final name = _string(page['name'], fallback: 'Business WAPI');
    final logo = _string(page['logoUrl']);
    final trimmedName = name.trim();
    final initials = trimmedName.length >= 2
        ? trimmedName.substring(0, 2).toUpperCase()
        : trimmedName.toUpperCase();
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFEAF8F4),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 29,
            backgroundColor: const Color(0xFF087D62),
            backgroundImage: logo.isEmpty ? null : NetworkImage(logo),
            child: logo.isEmpty
                ? Text(
                    initials,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                    ),
                  )
                : null,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                Text(
                  _string(
                        page['category'],
                        fallback: 'Activité professionnelle',
                      ) +
                      ' · ' +
                      _string(page['city'], fallback: 'WAPI'),
                  style: const TextStyle(
                    color: Color(0xFF176451),
                    fontSize: 12,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Logo, page et réponses clients centralisés',
                  style: TextStyle(color: Color(0xFF176451), fontSize: 11),
                ),
              ],
            ),
          ),
          IconButton(
            onPressed: onOpenWia,
            tooltip: 'Ouvrir WIA',
            icon: const Icon(
              Icons.auto_awesome_rounded,
              color: Color(0xFF087D62),
            ),
          ),
        ],
      ),
    );
  }
}

class _BusinessBenefits extends StatelessWidget {
  const _BusinessBenefits({required this.onBilling, required this.onWia});
  final VoidCallback onBilling;
  final VoidCallback onWia;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const Text(
        'VOS OUTILS BUSINESS',
        style: TextStyle(
          color: WapiColors.muted,
          fontSize: 11,
          letterSpacing: .7,
          fontWeight: FontWeight.w900,
        ),
      ),
      const SizedBox(height: 8),
      Row(
        children: [
          Expanded(
            child: _Benefit(
              icon: Icons.receipt_long_rounded,
              title: 'Comptabilité',
              detail: 'Produits, factures et créances',
              onTap: onBilling,
            ),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: _Benefit(
              icon: Icons.notifications_active_rounded,
              title: 'Relances',
              detail: 'Messages courtois à valider',
              onTap: onBilling,
            ),
          ),
        ],
      ),
      const SizedBox(height: 9),
      _Benefit(
        icon: Icons.smart_toy_rounded,
        title: 'WIA répond avec vous',
        detail:
            'Préparez vos réponses clients, annonces et offres avec l’IA sécurisée.',
        onTap: onWia,
      ),
    ],
  );
}

class _Benefit extends StatelessWidget {
  const _Benefit({
    required this.icon,
    required this.title,
    required this.detail,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String detail;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(15),
    child: Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: WapiColors.line),
        borderRadius: BorderRadius.circular(15),
      ),
      child: Row(
        children: [
          Icon(icon, color: const Color(0xFF087D62)),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 3),
                Text(
                  detail,
                  style: const TextStyle(
                    color: WapiColors.muted,
                    fontSize: 11,
                    height: 1.25,
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: WapiColors.muted),
        ],
      ),
    ),
  );
}

class _BusinessMetrics extends StatelessWidget {
  const _BusinessMetrics({required this.pageCount, required this.listingCount});
  final int pageCount;
  final int listingCount;
  @override
  Widget build(BuildContext context) => Row(
    children: [
      _Metric(value: pageCount.toString(), label: 'Pages'),
      const SizedBox(width: 10),
      _Metric(value: listingCount.toString(), label: 'Annonces'),
      const SizedBox(width: 10),
      const _Metric(value: '—', label: 'Créances'),
    ],
  );
}

class _Metric extends StatelessWidget {
  const _Metric({required this.value, required this.label});
  final String value;
  final String label;
  @override
  Widget build(BuildContext context) => Expanded(
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 14),
      decoration: BoxDecoration(
        color: const Color(0xFF062233),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: const TextStyle(
              color: Color(0xFF7CE5FF),
              fontWeight: FontWeight.w900,
              fontSize: 20,
            ),
          ),
          Text(
            label,
            style: const TextStyle(color: Color(0xFFB7D1DD), fontSize: 11),
          ),
        ],
      ),
    ),
  );
}

class _BusinessCard extends StatelessWidget {
  const _BusinessCard({required this.page, required this.listingCount});
  final Map<String, dynamic> page;
  final int listingCount;
  @override
  Widget build(BuildContext context) {
    final name = _string(page['name'], fallback: 'Business WAPI');
    final logo = _string(page['logoUrl']);
    final trimmedName = name.trim();
    final initials = trimmedName.length >= 2
        ? trimmedName.substring(0, 2).toUpperCase()
        : trimmedName.toUpperCase();
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            CircleAvatar(
              radius: 25,
              backgroundColor: Color(0xFFEAF8F4),
              backgroundImage: logo.isEmpty ? null : NetworkImage(logo),
              child: logo.isEmpty
                  ? Text(
                      initials,
                      style: const TextStyle(
                        color: Color(0xFF087D62),
                        fontWeight: FontWeight.w900,
                      ),
                    )
                  : null,
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    name,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    _string(page['category'], fallback: 'Business') +
                        ' · ' +
                        _string(page['city'], fallback: 'WAPI'),
                    style: const TextStyle(color: WapiColors.muted),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    listingCount.toString() + ' annonces actives',
                    style: const TextStyle(
                      color: Color(0xFF087D62),
                      fontWeight: FontWeight.w800,
                      fontSize: 12,
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
}

class _MarketplaceTab extends StatefulWidget {
  const _MarketplaceTab({
    required this.listings,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onCreate,
    required this.onOpen,
  });
  final List<Map<String, dynamic>> listings;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreate;
  final ValueChanged<Map<String, dynamic>> onOpen;
  @override
  State<_MarketplaceTab> createState() => _MarketplaceTabState();
}

class _MarketplaceTabState extends State<_MarketplaceTab> {
  String _search = '';
  @override
  Widget build(BuildContext context) {
    final shown = widget.listings.where((item) {
      final text =
          _string(item['title']) +
          ' ' +
          _string(item['description']) +
          ' ' +
          _string(item['category']) +
          ' ' +
          _string(item['place']);
      return _search.isEmpty ||
          text.toLowerCase().contains(_search.toLowerCase());
    }).toList();
    return RefreshIndicator(
      onRefresh: widget.onRefresh,
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
                      'Marketplace',
                      style: TextStyle(
                        fontSize: 25,
                        fontWeight: FontWeight.w900,
                        color: Color(0xFF062233),
                      ),
                    ),
                    SizedBox(height: 5),
                    Text(
                      'Acheter, échanger, enchérir, postuler ou discuter avec les Business WAPI.',
                      style: TextStyle(color: WapiColors.muted),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: widget.onCreate,
                icon: const Icon(Icons.add_circle_outline_rounded),
                tooltip: 'Publier',
              ),
            ],
          ),
          const SizedBox(height: 16),
          TextField(
            onChanged: (value) => setState(() => _search = value.trim()),
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search_rounded),
              hintText: 'Rechercher une annonce',
            ),
          ),
          const SizedBox(height: 16),
          if (widget.loading && widget.listings.isEmpty)
            const Padding(
              padding: EdgeInsets.all(40),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (widget.error != null && widget.listings.isEmpty)
            _CommerceState(
              icon: Icons.cloud_off_outlined,
              title: 'Marketplace indisponible',
              body: widget.error!,
              action: widget.onRefresh,
            )
          else if (shown.isEmpty)
            _CommerceState(
              icon: Icons.storefront_outlined,
              title: 'Aucune annonce',
              body:
                  'Les annonces publiées par les Business WAPI apparaîtront ici.',
              action: widget.onCreate,
              actionLabel: 'Créer une annonce',
            )
          else
            ...shown.map(
              (listing) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _ListingCard(
                  listing: listing,
                  onTap: () => widget.onOpen(listing),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ListingCard extends StatelessWidget {
  const _ListingCard({required this.listing, required this.onTap});
  final Map<String, dynamic> listing;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Row(
        children: [
          SizedBox(
            width: 112,
            height: 126,
            child: _ListingImage(
              url: _firstPhoto(listing),
              label: _string(listing['title']),
              height: 126,
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _modeLabel(_string(listing['mode'])).toUpperCase(),
                    style: const TextStyle(
                      color: Color(0xFF087D62),
                      fontWeight: FontWeight.w900,
                      fontSize: 10,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _string(listing['title']),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _string(listing['priceText'], fallback: 'Prix à discuter'),
                    style: const TextStyle(
                      color: Color(0xFF00856A),
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _string(listing['pageName'], fallback: 'Business WAPI') +
                        ' · ' +
                        _string(listing['place'], fallback: 'WAPI'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: WapiColors.muted,
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

class _ListingImage extends StatelessWidget {
  const _ListingImage({
    required this.url,
    required this.label,
    required this.height,
  });
  final String url;
  final String label;
  final double height;
  @override
  Widget build(BuildContext context) {
    if (url.isNotEmpty)
      return Image.network(
        url,
        height: height,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => _fallback(),
      );
    return _fallback();
  }

  Widget _fallback() => Container(
    height: height,
    decoration: const BoxDecoration(
      gradient: LinearGradient(colors: [Color(0xFF062233), Color(0xFF00A884)]),
    ),
    child: Center(
      child: Text(
        label.isEmpty ? 'W' : label.substring(0, 1).toUpperCase(),
        style: const TextStyle(
          color: Colors.white,
          fontSize: 30,
          fontWeight: FontWeight.w900,
        ),
      ),
    ),
  );
}

class _CommerceInfo extends StatelessWidget {
  const _CommerceInfo({required this.icon, required this.value});
  final IconData icon;
  final String value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 8),
    child: Row(
      children: [
        Icon(icon, size: 19, color: const Color(0xFF087D62)),
        const SizedBox(width: 9),
        Expanded(
          child: Text(value, style: const TextStyle(color: Color(0xFF43515B))),
        ),
      ],
    ),
  );
}

class _CommerceState extends StatelessWidget {
  const _CommerceState({
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

class _BusinessIdentity extends StatefulWidget {
  const _BusinessIdentity({required this.user});
  final User user;
  @override
  State<_BusinessIdentity> createState() => _BusinessIdentityState();
}

class _BusinessIdentityState extends State<_BusinessIdentity> {
  final _name = TextEditingController();
  final _category = TextEditingController();
  final _city = TextEditingController(text: 'Brazzaville');
  final _bio = TextEditingController();
  XFile? _logo;
  Uint8List? _logoBytes;
  bool _saving = false;
  @override
  void dispose() {
    _name.dispose();
    _category.dispose();
    _city.dispose();
    _bio.dispose();
    super.dispose();
  }

  Future<void> _pickLogo() async {
    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 88,
      maxWidth: 900,
    );
    if (picked == null) return;
    final bytes = await picked.readAsBytes();
    if (!mounted) return;
    setState(() {
      _logo = picked;
      _logoBytes = bytes;
    });
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
        'bio': _bio.text.trim(),
        'city': _city.text.trim().isEmpty ? 'Brazzaville' : _city.text.trim(),
        'phone': widget.user.phoneNumber ?? '',
        'website': '',
        'status': 'active',
        'followers': 0,
        // Création non vérifiée obligatoire : les règles empêchent un client
        // de s’auto-attribuer un badge ou un niveau de vérification.
        'verified': false,
        'verificationStatus': 'unverified',
        'createdAt': FieldValue.serverTimestamp(),
        'updatedAt': FieldValue.serverTimestamp(),
      });
      if (_logo != null && _logoBytes != null) {
        final extension = (_logo!.mimeType ?? 'image/png')
            .split('/')
            .last
            .replaceAll(RegExp(r'[^a-zA-Z0-9]'), '');
        final imageRef = FirebaseStorage.instance.ref(
          'business/${widget.user.uid}/${ref.id}/logo-${DateTime.now().millisecondsSinceEpoch}.$extension',
        );
        await imageRef.putData(
          _logoBytes!,
          SettableMetadata(
            contentType: _logo!.mimeType ?? 'image/png',
            cacheControl: 'public,max-age=3600',
          ),
        );
        await ref.update({
          'logoUrl': await imageRef.getDownloadURL(),
          'updatedAt': FieldValue.serverTimestamp(),
        });
      }
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
            'Créer votre Business',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 6),
          const Text(
            'Cette identité reste distincte de votre profil personnel.',
            style: TextStyle(color: WapiColors.muted),
          ),
          const SizedBox(height: 18),
          Center(
            child: InkWell(
              onTap: _pickLogo,
              borderRadius: BorderRadius.circular(42),
              child: CircleAvatar(
                radius: 42,
                backgroundColor: const Color(0xFFEAF8F4),
                backgroundImage: _logoBytes == null
                    ? null
                    : MemoryImage(_logoBytes!),
                child: _logoBytes == null
                    ? const Icon(
                        Icons.add_a_photo_rounded,
                        color: Color(0xFF087D62),
                        size: 28,
                      )
                    : null,
              ),
            ),
          ),
          const SizedBox(height: 6),
          const Center(
            child: Text(
              'Ajouter le logo du Business',
              style: TextStyle(color: WapiColors.muted, fontSize: 12),
            ),
          ),
          const SizedBox(height: 12),
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
          TextField(
            controller: _bio,
            maxLength: 400,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(labelText: 'Présentation'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _saving ? null : _save,
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
            ),
            child: Text(_saving ? 'Création…' : 'Créer ma page Business'),
          ),
        ],
      ),
    ),
  );
}

class _ListingForm extends StatefulWidget {
  const _ListingForm({required this.api, required this.pages});
  final _CommerceApi api;
  final List<Map<String, dynamic>> pages;
  @override
  State<_ListingForm> createState() => _ListingFormState();
}

class _ListingFormState extends State<_ListingForm> {
  final _title = TextEditingController();
  final _description = TextEditingController();
  final _category = TextEditingController(text: 'Autre');
  final _price = TextEditingController(text: 'Prix à discuter');
  final _place = TextEditingController();
  final _trade = TextEditingController();
  late String _page = _string(widget.pages.first['id']);
  String _mode = 'sale';
  List<File> _photos = const [];
  bool _saving = false;
  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _category.dispose();
    _price.dispose();
    _place.dispose();
    _trade.dispose();
    super.dispose();
  }

  Future<void> _pickPhotos() async {
    final assets = await ImagePicker().pickMultiImage(
      imageQuality: 86,
      maxWidth: 1600,
    );
    if (!mounted) return;
    final files = assets.take(5).map((asset) => File(asset.path)).toList();
    if (files.any((file) => file.lengthSync() > 5 * 1024 * 1024)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Chaque photo doit faire moins de 5 Mo.')),
      );
      return;
    }
    setState(() => _photos = files);
  }

  Future<void> _save() async {
    if (_title.text.trim().length < 2) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Indiquez le titre de votre annonce.')),
      );
      return;
    }
    if (_mode == 'trade' && _trade.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Indiquez ce que vous acceptez en échange.'),
        ),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      final photos = <String>[];
      for (final file in _photos) {
        photos.add(
          'data:image/jpeg;base64,' + base64Encode(await file.readAsBytes()),
        );
      }
      await widget.api.call('createMarketplaceListing', {
        'listingId':
            'listing-' + DateTime.now().microsecondsSinceEpoch.toString(),
        'pageId': _page,
        'title': _title.text.trim(),
        'description': _description.text.trim(),
        'category': _category.text.trim(),
        'mode': _mode,
        'priceText': _mode == 'job' ? 'Prix à discuter' : _price.text.trim(),
        'place': _place.text.trim(),
        'tradeWish': _trade.text.trim(),
        'photoBase64s': photos,
        'acceptsOffers': true,
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
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'Publier une annonce',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 30),
      children: [
        DropdownButtonFormField<String>(
          value: _page,
          items: widget.pages
              .map(
                (page) => DropdownMenuItem(
                  value: _string(page['id']),
                  child: Text(_string(page['name'], fallback: 'Business WAPI')),
                ),
              )
              .toList(),
          onChanged: _saving
              ? null
              : (value) => setState(() => _page = value ?? _page),
          decoration: const InputDecoration(labelText: 'Business'),
        ),
        const SizedBox(height: 14),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: ['sale', 'trade', 'auction', 'job', 'service']
              .map(
                (mode) => ChoiceChip(
                  label: Text(_modeLabel(mode)),
                  selected: _mode == mode,
                  onSelected: _saving
                      ? null
                      : (_) => setState(() => _mode = mode),
                ),
              )
              .toList(),
        ),
        const SizedBox(height: 16),
        InkWell(
          onTap: _saving ? null : _pickPhotos,
          borderRadius: BorderRadius.circular(18),
          child: Ink(
            height: 150,
            decoration: BoxDecoration(
              color: const Color(0xFFEAF8F4),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: const Color(0xFFB7DED1)),
            ),
            child: _photos.isEmpty
                ? const Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.add_photo_alternate_outlined,
                        color: Color(0xFF087D62),
                        size: 34,
                      ),
                      SizedBox(height: 8),
                      Text(
                        'Ajouter jusqu’à 5 photos',
                        style: TextStyle(
                          fontWeight: FontWeight.w900,
                          color: Color(0xFF176451),
                        ),
                      ),
                    ],
                  )
                : ListView(
                    scrollDirection: Axis.horizontal,
                    children: _photos
                        .map(
                          (file) => Padding(
                            padding: const EdgeInsets.all(5),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: Image.file(
                                file,
                                width: 135,
                                fit: BoxFit.cover,
                              ),
                            ),
                          ),
                        )
                        .toList(),
                  ),
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _title,
          maxLength: 120,
          decoration: const InputDecoration(labelText: 'Titre'),
        ),
        TextField(
          controller: _description,
          maxLength: 1200,
          minLines: 3,
          maxLines: 5,
          decoration: const InputDecoration(labelText: 'Description'),
        ),
        TextField(
          controller: _category,
          maxLength: 60,
          decoration: const InputDecoration(labelText: 'Catégorie'),
        ),
        if (_mode != 'job')
          TextField(
            controller: _price,
            maxLength: 120,
            decoration: const InputDecoration(labelText: 'Prix ou condition'),
          ),
        if (_mode == 'trade')
          TextField(
            controller: _trade,
            maxLength: 180,
            decoration: const InputDecoration(labelText: 'Échange recherché'),
          ),
        TextField(
          controller: _place,
          maxLength: 120,
          decoration: const InputDecoration(labelText: 'Ville ou zone'),
        ),
        const SizedBox(height: 18),
        FilledButton(
          onPressed: _saving ? null : _save,
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(54)),
          child: Text(_saving ? 'Publication…' : 'Publier l’annonce'),
        ),
      ],
    ),
  );
}

class _IntentForm extends StatefulWidget {
  const _IntentForm({required this.mode});
  final String mode;
  @override
  State<_IntentForm> createState() => _IntentFormState();
}

class _IntentFormState extends State<_IntentForm> {
  final _offer = TextEditingController();
  final _note = TextEditingController();
  @override
  void dispose() {
    _offer.dispose();
    _note.dispose();
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
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _intentLabel(widget.mode),
            style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _offer,
            maxLength: 180,
            decoration: InputDecoration(labelText: _offerLabel(widget.mode)),
          ),
          TextField(
            controller: _note,
            maxLength: 800,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Message · facultatif',
            ),
          ),
          const SizedBox(height: 10),
          FilledButton(
            onPressed: () => Navigator.pop(context, {
              'offer': _offer.text.trim(),
              'note': _note.text.trim(),
            }),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
            ),
            child: const Text('Envoyer'),
          ),
        ],
      ),
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
        .call<Map<String, dynamic>>({'action': action, ...data});
    return _map(result.data);
  }
}

List<Map<String, dynamic>> _maps(Object? value) => (value as List? ?? const [])
    .whereType<Map>()
    .map(_map)
    .toList(growable: false);
Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
String _string(Object? value, {String fallback = ''}) =>
    value is String && value.trim().isNotEmpty ? value.trim() : fallback;
String _firstPhoto(Map<String, dynamic> listing) {
  final photos = listing['photoUrls'];
  if (photos is List && photos.isNotEmpty) return _string(photos.first);
  return '';
}

String _modeLabel(String mode) {
  const labels = {
    'sale': 'À vendre',
    'trade': 'Troc',
    'auction': 'Enchères',
    'job': 'Emploi',
    'service': 'Service',
  };
  return labels[mode] ?? 'Annonce';
}

IconData _intentIcon(String mode) => mode == 'auction'
    ? Icons.gavel_rounded
    : mode == 'job'
    ? Icons.person_search_rounded
    : mode == 'trade'
    ? Icons.swap_horiz_rounded
    : Icons.send_rounded;
String _intentLabel(String mode) => mode == 'auction'
    ? 'Enchérir'
    : mode == 'job'
    ? 'Postuler'
    : mode == 'trade'
    ? 'Proposer un échange'
    : 'Faire une offre';
String _intentKind(String mode) => mode == 'auction'
    ? 'bid'
    : mode == 'job'
    ? 'apply'
    : mode == 'trade'
    ? 'trade'
    : 'offer';
String _offerLabel(String mode) => mode == 'auction'
    ? 'Votre enchère'
    : mode == 'job'
    ? 'Votre profil ou candidature'
    : mode == 'trade'
    ? 'Votre proposition d’échange'
    : 'Votre offre';
String _errorText(Object error) =>
    error is FirebaseFunctionsException &&
        error.message != null &&
        error.message!.isNotEmpty
    ? error.message!
    : 'Cette action n’a pas pu être terminée. Vérifiez la connexion puis réessayez.';
