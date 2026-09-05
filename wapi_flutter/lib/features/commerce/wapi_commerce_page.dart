import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:cached_network_image/cached_network_image.dart';
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

  Future<void> _createAdCampaign() async {
    if (_pages.isEmpty) {
      await _createBusiness();
      return;
    }
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => _AdCampaignForm(api: _api, pages: _pages),
      ),
    );
    if (created == true) await _refresh();
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
          userId: widget.user.uid,
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
          onCreateAd: _createAdCampaign,
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
    required this.userId,
    required this.pages,
    required this.listings,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onCreatePage,
    required this.onCreateListing,
    required this.onOpenBilling,
    required this.onOpenWia,
    required this.onCreateAd,
  });
  final String userId;
  final List<Map<String, dynamic>> pages;
  final List<Map<String, dynamic>> listings;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreatePage;
  final Future<void> Function() onCreateListing;
  final Future<void> Function() onOpenBilling;
  final VoidCallback onOpenWia;
  final Future<void> Function() onCreateAd;
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
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: onCreateAd,
              icon: const Icon(Icons.campaign_rounded),
              label: const Text('Créer une campagne publicitaire'),
            ),
            const SizedBox(height: 18),
            _BusinessCampaigns(userId: userId, onCreate: onCreateAd),
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

class _BusinessCampaigns extends StatelessWidget {
  const _BusinessCampaigns({required this.userId, required this.onCreate});

  final String userId;
  final Future<void> Function() onCreate;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('adCampaigns')
          .where('ownerId', isEqualTo: userId)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return _CampaignEmpty(
            icon: Icons.sync_problem_rounded,
            title: 'Suivi momentanément indisponible',
            body: 'WAPI réessaie automatiquement dès que le réseau revient.',
            onCreate: onCreate,
          );
        }
        if (!snapshot.hasData) {
          return const SizedBox(
            height: 96,
            child: Center(child: CircularProgressIndicator()),
          );
        }
        final campaigns =
            snapshot.data!.docs
                .map((document) => {'id': document.id, ...document.data()})
                .toList()
              ..sort(
                (a, b) => _campaignMillis(
                  b['createdAt'],
                ).compareTo(_campaignMillis(a['createdAt'])),
              );
        if (campaigns.isEmpty) {
          return _CampaignEmpty(
            icon: Icons.campaign_outlined,
            title: 'Lancez votre première diffusion',
            body:
                'Ajoutez une image, choisissez votre audience et suivez les résultats depuis ce tableau.',
            onCreate: onCreate,
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'MES CAMPAGNES',
                    style: TextStyle(
                      color: WapiColors.muted,
                      fontSize: 11,
                      letterSpacing: .7,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                TextButton.icon(
                  onPressed: onCreate,
                  icon: const Icon(Icons.add_rounded, size: 18),
                  label: const Text('Nouvelle'),
                ),
              ],
            ),
            const SizedBox(height: 4),
            ...campaigns.take(5).map(_CampaignCard.new),
          ],
        );
      },
    );
  }
}

class _CampaignEmpty extends StatelessWidget {
  const _CampaignEmpty({
    required this.icon,
    required this.title,
    required this.body,
    required this.onCreate,
  });

  final IconData icon;
  final String title;
  final String body;
  final Future<void> Function() onCreate;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: const Color(0xFFF3F7F8),
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: const Color(0xFFDCE7EA)),
    ),
    child: Row(
      children: [
        CircleAvatar(
          backgroundColor: const Color(0xFFDDF7F1),
          foregroundColor: const Color(0xFF087D62),
          child: Icon(icon),
        ),
        const SizedBox(width: 13),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
              const SizedBox(height: 3),
              Text(
                body,
                style: const TextStyle(color: WapiColors.muted, height: 1.3),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _CampaignCard extends StatelessWidget {
  const _CampaignCard(this.campaign);

  final Map<String, dynamic> campaign;

  @override
  Widget build(BuildContext context) {
    final imageUrl = _string(campaign['creativeImageUrl']);
    final impressions = _int(campaign['impressionCount']);
    final clicks = _int(campaign['clickCount']);
    final conversions = _int(campaign['conversionCount']);
    final target = _int(campaign['targetImpressions']);
    final progress = target <= 0 ? 0.0 : (impressions / target).clamp(0.0, 1.0);
    final status = _string(campaign['status'], fallback: 'pending_payment');
    final color = switch (status) {
      'active' => const Color(0xFF087D62),
      'paused' => const Color(0xFFF29900),
      'completed' => const Color(0xFF246BCE),
      _ => const Color(0xFF6B7280),
    };
    final statusLabel = switch (status) {
      'active' => 'En diffusion',
      'paused' => 'En pause',
      'completed' => 'Terminée',
      _ => 'Paiement à finaliser',
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE2EAED)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0C102A43),
            blurRadius: 14,
            offset: Offset(0, 5),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(13),
            child: SizedBox(
              width: 74,
              height: 74,
              child: imageUrl.isEmpty
                  ? const ColoredBox(
                      color: Color(0xFFE7F4F2),
                      child: Icon(
                        Icons.campaign_rounded,
                        color: Color(0xFF087D62),
                      ),
                    )
                  : CachedNetworkImage(
                      imageUrl: imageUrl,
                      fit: BoxFit.cover,
                      memCacheWidth: 220,
                      fadeInDuration: const Duration(milliseconds: 100),
                      errorWidget: (_, _, _) => const ColoredBox(
                        color: Color(0xFFE7F4F2),
                        child: Icon(
                          Icons.image_not_supported_outlined,
                          color: Color(0xFF087D62),
                        ),
                      ),
                    ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        _string(campaign['title'], fallback: 'Campagne WAPI'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w900),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: .1),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        statusLabel,
                        style: TextStyle(
                          color: color,
                          fontSize: 10,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 9),
                ClipRRect(
                  borderRadius: BorderRadius.circular(20),
                  child: LinearProgressIndicator(
                    value: progress,
                    minHeight: 5,
                    backgroundColor: const Color(0xFFE8EEF0),
                    color: color,
                  ),
                ),
                const SizedBox(height: 9),
                Wrap(
                  spacing: 12,
                  runSpacing: 4,
                  children: [
                    _CampaignMetric(label: 'Vues', value: '$impressions'),
                    _CampaignMetric(label: 'Clics', value: '$clicks'),
                    _CampaignMetric(label: 'Résultats', value: '$conversions'),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CampaignMetric extends StatelessWidget {
  const _CampaignMetric({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Text.rich(
    TextSpan(
      text: '$value ',
      style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 12),
      children: [
        TextSpan(
          text: label,
          style: const TextStyle(
            color: WapiColors.muted,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    ),
  );
}

int _campaignMillis(Object? value) => value is Timestamp
    ? value.millisecondsSinceEpoch
    : value is num
    ? value.toInt()
    : 0;

class _AdCampaignForm extends StatefulWidget {
  const _AdCampaignForm({required this.api, required this.pages});
  final _CommerceApi api;
  final List<Map<String, dynamic>> pages;

  @override
  State<_AdCampaignForm> createState() => _AdCampaignFormState();
}

class _AdCampaignFormState extends State<_AdCampaignForm> {
  final _title = TextEditingController();
  final _creative = TextEditingController();
  final _cta = TextEditingController(text: 'Découvrir');
  final _audience = TextEditingController(
    text: 'Clients intéressés par mon activité',
  );
  final _city = TextEditingController(text: 'Brazzaville');
  final _website = TextEditingController();
  String? _pageId;
  String _objective = 'messages';
  String _placement = 'inbox';
  String _destination = 'message';
  double _dailyBudget = 2500;
  double _days = 7;
  bool _submitting = false;
  XFile? _creativeImage;
  Uint8List? _creativeImageBytes;

  @override
  void initState() {
    super.initState();
    _pageId = _string(widget.pages.first['id']);
  }

  @override
  void dispose() {
    _title.dispose();
    _creative.dispose();
    _cta.dispose();
    _audience.dispose();
    _city.dispose();
    _website.dispose();
    super.dispose();
  }

  Future<void> _pickCreativeImage() async {
    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 88,
      maxWidth: 1600,
      maxHeight: 1600,
    );
    if (picked == null) return;
    final bytes = await picked.readAsBytes();
    if (!mounted) return;
    setState(() {
      _creativeImage = picked;
      _creativeImageBytes = bytes;
    });
  }

  Future<void> _submit() async {
    final pageId = _pageId;
    if (pageId == null ||
        _title.text.trim().length < 2 ||
        _creative.text.trim().length < 2) {
      _snack(
        'Ajoutez un titre et un message pour votre publicité.',
        error: true,
      );
      return;
    }
    setState(() => _submitting = true);
    Reference? uploadedImage;
    try {
      String imageUrl = '';
      String storagePath = '';
      final user = FirebaseAuth.instance.currentUser;
      if (_creativeImage != null &&
          _creativeImageBytes != null &&
          user != null) {
        final rawType = _creativeImage!.mimeType ?? 'image/jpeg';
        final contentType = rawType.startsWith('image/')
            ? rawType
            : 'image/jpeg';
        final extension = contentType.split('/').last == 'jpeg'
            ? 'jpg'
            : contentType.split('/').last;
        storagePath =
            'businessAds/${user.uid}/$pageId/creative-${DateTime.now().microsecondsSinceEpoch}.$extension';
        uploadedImage = FirebaseStorage.instance.ref(storagePath);
        await uploadedImage.putData(
          _creativeImageBytes!,
          SettableMetadata(
            contentType: contentType,
            cacheControl: 'public,max-age=86400,immutable',
          ),
        );
        imageUrl = await uploadedImage.getDownloadURL();
      }
      final result = await widget.api.createAdCampaign({
        'pageId': pageId,
        'objective': _objective,
        'placement': _placement,
        'destination': _destination,
        'title': _title.text,
        'creative': _creative.text,
        'cta': _cta.text,
        'creativeImageUrl': imageUrl,
        'creativeStoragePath': storagePath,
        'audience': _audience.text,
        'city': _city.text,
        'website': _website.text,
        'days': _days.round(),
        'dailyBudget': _dailyBudget.round(),
      });
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Campagne préparée'),
          content: Text(
            'Budget estimé : ${_money(result['totalBudget'])} FCFA\n\nVotre campagne sera contrôlée avant sa diffusion.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Compris'),
            ),
          ],
        ),
      );
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (uploadedImage != null) {
        await uploadedImage.delete().catchError((_) {});
      }
      _snack(_errorText(error), error: true);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _snack(String text, {bool error = false}) {
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
        'Nouvelle publicité',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 30),
      children: [
        const Text(
          'Faites connaître votre activité',
          style: TextStyle(fontSize: 25, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 5),
        const Text(
          'Choisissez qui voir, où diffuser et combien investir. WAPI vous montre le coût avant validation.',
          style: TextStyle(color: WapiColors.muted, height: 1.35),
        ),
        const SizedBox(height: 18),
        _section('1. Identité et message'),
        DropdownButtonFormField<String>(
          value: _pageId,
          decoration: const InputDecoration(
            labelText: 'Page Business',
            prefixIcon: Icon(Icons.business_rounded),
          ),
          items: widget.pages
              .map(
                (page) => DropdownMenuItem(
                  value: _string(page['id']),
                  child: Text(_string(page['name'], fallback: 'Business WAPI')),
                ),
              )
              .toList(),
          onChanged: (value) => setState(() => _pageId = value),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _title,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            labelText: 'Titre de la publicité',
            hintText: 'Ex. Nouvelle collection disponible',
            prefixIcon: Icon(Icons.title_rounded),
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _creative,
          onChanged: (_) => setState(() {}),
          maxLines: 4,
          decoration: const InputDecoration(
            labelText: 'Texte de l’annonce',
            hintText: 'Expliquez votre offre et ce que le client doit faire',
            alignLabelWithHint: true,
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _cta,
          maxLength: 32,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            labelText: 'Texte du bouton',
            hintText: 'Découvrir, Commander, Réserver…',
            prefixIcon: Icon(Icons.touch_app_rounded),
          ),
        ),
        const SizedBox(height: 4),
        InkWell(
          onTap: _pickCreativeImage,
          borderRadius: BorderRadius.circular(18),
          child: Container(
            height: 112,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFFF1F7F5),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: const Color(0xFFC7DED7)),
            ),
            child: _creativeImageBytes == null
                ? const Row(
                    children: [
                      CircleAvatar(
                        backgroundColor: Color(0xFFDDF5ED),
                        child: Icon(Icons.add_photo_alternate_rounded),
                      ),
                      SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Ajouter un visuel',
                              style: TextStyle(fontWeight: FontWeight.w900),
                            ),
                            SizedBox(height: 3),
                            Text(
                              'Photo carrée ou paysage, nette et sans surcharge.',
                              style: TextStyle(
                                color: WapiColors.muted,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  )
                : ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        Image.memory(_creativeImageBytes!, fit: BoxFit.cover),
                        Positioned(
                          right: 8,
                          top: 8,
                          child: IconButton.filledTonal(
                            onPressed: () => setState(() {
                              _creativeImage = null;
                              _creativeImageBytes = null;
                            }),
                            icon: const Icon(Icons.close_rounded),
                          ),
                        ),
                      ],
                    ),
                  ),
          ),
        ),
        const SizedBox(height: 14),
        _AdPreview(
          title: _title.text.isEmpty ? 'Votre publicité' : _title.text,
          creative: _creative.text.isEmpty
              ? 'Votre message apparaîtra ici.'
              : _creative.text,
          cta: _cta.text.trim().isEmpty ? 'Découvrir' : _cta.text.trim(),
          imageBytes: _creativeImageBytes,
        ),
        const SizedBox(height: 18),
        _section('2. Objectif'),
        _chips(
          {
            'reach': 'Visibilité',
            'messages': 'Messages',
            'traffic': 'Visites',
            'sales': 'Ventes',
          },
          _objective,
          (value) => setState(() => _objective = value),
        ),
        const SizedBox(height: 18),
        _section('3. Audience et diffusion'),
        TextField(
          controller: _audience,
          decoration: const InputDecoration(
            labelText: 'Audience souhaitée',
            hintText: 'Ex. Femmes 18–45 intéressées par la mode',
            prefixIcon: Icon(Icons.people_alt_outlined),
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _city,
          decoration: const InputDecoration(
            labelText: 'Ville ou zone',
            prefixIcon: Icon(Icons.location_on_outlined),
          ),
        ),
        const SizedBox(height: 10),
        _chips(
          {
            'inbox': 'Messages',
            'profile_story': 'Stories',
            'market': 'Marketplace',
            'live': 'Lives',
          },
          _placement,
          (value) => setState(() => _placement = value),
        ),
        const SizedBox(height: 10),
        DropdownButtonFormField<String>(
          value: _destination,
          decoration: const InputDecoration(
            labelText: 'Action du bouton',
            prefixIcon: Icon(Icons.touch_app_outlined),
          ),
          items: const [
            DropdownMenuItem(
              value: 'message',
              child: Text('Recevoir des messages'),
            ),
            DropdownMenuItem(
              value: 'page',
              child: Text('Voir ma page Business'),
            ),
            DropdownMenuItem(
              value: 'call',
              child: Text('Appeler mon Business'),
            ),
            DropdownMenuItem(
              value: 'website',
              child: Text('Visiter mon site HTTPS'),
            ),
          ],
          onChanged: (value) =>
              setState(() => _destination = value ?? 'message'),
        ),
        if (_destination == 'website') ...[
          const SizedBox(height: 10),
          TextField(
            controller: _website,
            keyboardType: TextInputType.url,
            decoration: const InputDecoration(
              labelText: 'Site HTTPS de destination',
              prefixIcon: Icon(Icons.link_rounded),
            ),
          ),
        ],
        const SizedBox(height: 18),
        _section('4. Budget et durée'),
        Text(
          'Budget quotidien : ${_money(_dailyBudget.round())} FCFA',
          style: const TextStyle(fontWeight: FontWeight.w800),
        ),
        Slider(
          value: _dailyBudget,
          min: 500,
          max: 50000,
          divisions: 99,
          label: '${_dailyBudget.round()} FCFA',
          onChanged: (value) => setState(() => _dailyBudget = value),
        ),
        Text(
          'Durée : ${_days.round()} jour${_days.round() > 1 ? 's' : ''}',
          style: const TextStyle(fontWeight: FontWeight.w800),
        ),
        Slider(
          value: _days,
          min: 1,
          max: 90,
          divisions: 89,
          label: '${_days.round()} jours',
          onChanged: (value) => setState(() => _days = value),
        ),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: const Color(0xFFEAF8F4),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Text(
            'Total estimé : ${_money(_dailyBudget.round() * _days.round())} FCFA\nLa campagne reste en attente de paiement et de contrôle avant diffusion.',
            style: const TextStyle(
              color: Color(0xFF176451),
              fontWeight: FontWeight.w700,
              height: 1.35,
            ),
          ),
        ),
        const SizedBox(height: 18),
        FilledButton.icon(
          onPressed: _submitting ? null : _submit,
          icon: _submitting
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Colors.white,
                  ),
                )
              : const Icon(Icons.check_circle_outline),
          label: Text(_submitting ? 'Préparation…' : 'Préparer ma campagne'),
        ),
      ],
    ),
  );

  Widget _section(String title) => Padding(
    padding: const EdgeInsets.only(bottom: 9),
    child: Text(
      title,
      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
    ),
  );

  Widget _chips(
    Map<String, String> labels,
    String selected,
    ValueChanged<String> onSelected,
  ) => Wrap(
    spacing: 8,
    runSpacing: 8,
    children: labels.entries
        .map(
          (entry) => ChoiceChip(
            label: Text(entry.value),
            selected: selected == entry.key,
            onSelected: (_) => onSelected(entry.key),
          ),
        )
        .toList(),
  );
}

class _AdPreview extends StatelessWidget {
  const _AdPreview({
    required this.title,
    required this.creative,
    required this.cta,
    this.imageBytes,
  });
  final String title;
  final String creative;
  final String cta;
  final Uint8List? imageBytes;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(15),
    decoration: BoxDecoration(
      color: const Color(0xFF062233),
      borderRadius: BorderRadius.circular(18),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            CircleAvatar(
              radius: 15,
              backgroundColor: Color(0xFF0B8068),
              child: Icon(Icons.storefront_rounded, size: 16),
            ),
            SizedBox(width: 9),
            Expanded(
              child: Text(
                'APERÇU DE LA PUBLICITÉ',
                style: TextStyle(
                  color: Color(0xFF7CE5FF),
                  fontSize: 10,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1,
                ),
              ),
            ),
            Text(
              'Sponsorisé',
              style: TextStyle(color: Color(0xFF9FC2C7), fontSize: 10),
            ),
          ],
        ),
        if (imageBytes != null) ...[
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: Image.memory(imageBytes!, fit: BoxFit.cover),
            ),
          ),
        ],
        const SizedBox(height: 8),
        Text(
          title,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 5),
        Text(
          creative,
          style: const TextStyle(color: Color(0xFFD8EFF3), height: 1.3),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: null,
            icon: const Icon(Icons.arrow_forward_rounded),
            label: Text(cta),
          ),
        ),
      ],
    ),
  );
}

String _money(Object? value) => (int.tryParse(value?.toString() ?? '') ?? 0)
    .toString()
    .replaceAllMapped(RegExp(r'(?<!^)(?=(\d{3})+$)'), (match) => ' ');

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
            backgroundImage: logo.isEmpty
                ? null
                : CachedNetworkImageProvider(logo),
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
              backgroundImage: logo.isEmpty
                  ? null
                  : CachedNetworkImageProvider(logo),
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
  String _mode = 'all';
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
      final matchesText =
          _search.isEmpty || text.toLowerCase().contains(_search.toLowerCase());
      final matchesMode =
          _mode == 'all' || _string(item['mode'], fallback: 'sale') == _mode;
      return matchesText && matchesMode;
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
          const SizedBox(height: 12),
          SizedBox(
            height: 42,
            child: ListView(
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              children:
                  const <(String, String, IconData)>[
                    ('all', 'Tout', Icons.grid_view_rounded),
                    ('sale', 'À vendre', Icons.shopping_bag_outlined),
                    ('trade', 'Troc', Icons.swap_horiz_rounded),
                    ('auction', 'Enchères', Icons.gavel_rounded),
                    ('job', 'Emplois', Icons.work_outline_rounded),
                    ('service', 'Services', Icons.handyman_outlined),
                  ].map((filter) {
                    final selected = _mode == filter.$1;
                    return Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        selected: selected,
                        onSelected: (_) => setState(() => _mode = filter.$1),
                        avatar: Icon(filter.$3, size: 17),
                        label: Text(filter.$2),
                      ),
                    );
                  }).toList(),
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
            ...shown.indexed.map((entry) {
              final listing = entry.$2;
              return TweenAnimationBuilder<double>(
                key: ValueKey(_string(listing['id'])),
                tween: Tween(begin: 0, end: 1),
                duration: Duration(
                  milliseconds: 220 + entry.$1.clamp(0, 5) * 45,
                ),
                curve: Curves.easeOutCubic,
                builder: (context, value, child) => Opacity(
                  opacity: value,
                  child: Transform.translate(
                    offset: Offset(0, 12 * (1 - value)),
                    child: child,
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _ListingCard(
                    listing: listing,
                    onTap: () => widget.onOpen(listing),
                  ),
                ),
              );
            }),
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
      return CachedNetworkImage(
        imageUrl: url,
        height: height,
        fit: BoxFit.cover,
        fadeInDuration: const Duration(milliseconds: 120),
        placeholder: (_, _) => _fallback(),
        errorWidget: (_, _, _) => _fallback(),
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
  Future<Map<String, dynamic>> createAdCampaign(
    Map<String, dynamic> data,
  ) async {
    final result = await _functions
        .httpsCallable('createAdCampaign')
        .call<Map<String, dynamic>>(data)
        .timeout(const Duration(seconds: 25));
    return _map(result.data);
  }

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

List<Map<String, dynamic>> _maps(Object? value) => (value as List? ?? const [])
    .whereType<Map>()
    .map(_map)
    .toList(growable: false);
Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
String _string(Object? value, {String fallback = ''}) =>
    value is String && value.trim().isNotEmpty ? value.trim() : fallback;
int _int(Object? value) => value is num ? value.toInt() : 0;
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
String _errorText(Object error) => wapiErrorText(
  error is FirebaseFunctionsException ? error.message : error,
  fallback:
      'Cette action n’a pas pu être terminée. Vérifiez la connexion puis réessayez.',
);
