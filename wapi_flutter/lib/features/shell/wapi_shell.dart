import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:file_picker/file_picker.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:image_picker/image_picker.dart';
import 'package:just_audio/just_audio.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:record/record.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/wapi_theme.dart';
import '../../data/wapi_repository.dart';
import '../../services/wapi_notifications.dart';
import '../calls/wapi_call_page.dart';
import '../commerce/wapi_commerce_page.dart';
import '../games/wapi_games_page.dart';
import '../live/wapi_live_page.dart';
import '../services/wapi_services_page.dart';
import '../ticketbulk/wapi_ticketbulk_page.dart';
import '../wia/wia_chat_page.dart';

class WapiShell extends StatefulWidget {
  const WapiShell({super.key, required this.user, this.initialConversationId});
  final User user;
  final String? initialConversationId;
  @override
  State<WapiShell> createState() => _WapiShellState();
}

class _WapiShellState extends State<WapiShell> with WidgetsBindingObserver {
  final _repository = WapiRepository(FirebaseFirestore.instance);
  int _index = 0;
  Timer? _presenceHeartbeat;
  bool _isForeground = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _setPresence(true);
    _presenceHeartbeat = Timer.periodic(const Duration(seconds: 45), (_) {
      if (_isForeground) unawaited(_setPresence(true));
    });
    _syncNotifications();
    final conversationId = widget.initialConversationId;
    if (conversationId != null && conversationId.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _openConversationFromNotification(conversationId),
      );
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _isForeground = state == AppLifecycleState.resumed;
    _setPresence(_isForeground);
    if (_isForeground) {
      _syncNotifications();
    }
  }

  Future<void> _setPresence(bool isOnline) async {
    try {
      await _repository.updatePresence(user: widget.user, isOnline: isOnline);
    } catch (_) {
      // La présence ne doit jamais empêcher l’ouverture de l’application.
    }
  }

  Future<void> _syncNotifications() async {
    try {
      await WapiNotifications.syncUnreadBadge(widget.user.uid);
    } catch (_) {
      // Le badge ne doit jamais empêcher WAPI de s’ouvrir.
    }
  }

  Future<void> _openConversationFromNotification(String conversationId) async {
    try {
      final snapshot = await FirebaseFirestore.instance
          .collection('conversations')
          .doc(conversationId)
          .get();
      final data = snapshot.data();
      if (!mounted ||
          data == null ||
          !(data['memberIds'] as List? ?? const []).contains(widget.user.uid)) {
        return;
      }
      final conversation = WapiConversation.fromDoc(snapshot, widget.user.uid);
      setState(() => _index = 1);
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => _ChatPage(
            user: widget.user,
            repository: _repository,
            conversation: conversation,
          ),
        ),
      );
    } catch (_) {
      // Une notification périmée ne doit pas interrompre l’ouverture de WAPI.
    }
  }

  @override
  void dispose() {
    _presenceHeartbeat?.cancel();
    _setPresence(false);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      _HomePage(user: widget.user, repository: _repository),
      _InboxPage(user: widget.user, repository: _repository),
      _CallsPage(user: widget.user),
      _UpdatesPage(user: widget.user, repository: _repository),
      _AssistantPage(user: widget.user),
    ];
    const destinations = [
      NavigationDestination(
        icon: Icon(Icons.home_outlined),
        selectedIcon: Icon(Icons.home),
        label: 'Accueil',
      ),
      NavigationDestination(
        icon: Icon(Icons.chat_bubble_outline),
        selectedIcon: Icon(Icons.chat_bubble),
        label: 'Messages',
      ),
      NavigationDestination(
        icon: Icon(Icons.call_outlined),
        selectedIcon: Icon(Icons.call),
        label: 'Appels',
      ),
      NavigationDestination(
        icon: Icon(Icons.auto_awesome_outlined),
        selectedIcon: Icon(Icons.auto_awesome),
        label: 'Actus',
      ),
      NavigationDestination(
        icon: Icon(Icons.smart_toy_outlined),
        selectedIcon: Icon(Icons.smart_toy),
        label: 'Assistant',
      ),
    ];
    return LayoutBuilder(
      builder: (context, constraints) {
        final body = IndexedStack(index: _index, children: pages);
        if (constraints.maxWidth >= 840) {
          return Scaffold(
            body: Row(
              children: [
                SafeArea(
                  child: NavigationRail(
                    selectedIndex: _index,
                    extended: constraints.maxWidth >= 1120,
                    minExtendedWidth: 196,
                    groupAlignment: -.72,
                    useIndicator: true,
                    backgroundColor: Colors.white,
                    leading: Padding(
                      padding: const EdgeInsets.only(bottom: 18),
                      child: Image.asset(
                        'assets/branding/wapi_mark.png',
                        width: 42,
                        height: 42,
                      ),
                    ),
                    onDestinationSelected: (value) =>
                        setState(() => _index = value),
                    destinations: destinations
                        .map(
                          (destination) => NavigationRailDestination(
                            icon: destination.icon,
                            selectedIcon: destination.selectedIcon,
                            label: Text(destination.label),
                          ),
                        )
                        .toList(growable: false),
                  ),
                ),
                const VerticalDivider(width: 1),
                Expanded(child: body),
              ],
            ),
          );
        }
        return Scaffold(
          body: body,
          bottomNavigationBar: NavigationBar(
            selectedIndex: _index,
            onDestinationSelected: (value) => setState(() => _index = value),
            destinations: destinations,
          ),
        );
      },
    );
  }
}

class _WapiAppBar extends StatelessWidget implements PreferredSizeWidget {
  const _WapiAppBar({
    required this.title,
    this.subtitle,
    this.actions = const [],
  });
  final String title;
  final String? subtitle;
  final List<Widget> actions;
  @override
  Size get preferredSize => const Size.fromHeight(64);
  @override
  Widget build(BuildContext context) => AppBar(
    titleSpacing: 16,
    title: Row(
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18),
            ),
            if (subtitle != null)
              Text(
                subtitle!,
                style: const TextStyle(color: WapiColors.muted, fontSize: 11),
              ),
          ],
        ),
      ],
    ),
    actions: actions,
  );
}

class _HomePage extends StatelessWidget {
  const _HomePage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: 'WAPI',
      subtitle: 'Votre espace',
      actions: [
        IconButton(
          tooltip: 'Mon profil',
          onPressed: () =>
              _push(context, _ProfilePage(user: user, repository: repository)),
          icon: const Icon(Icons.account_circle_outlined),
        ),
        const SizedBox(width: 6),
      ],
    ),
    body: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: repository.profile(user.uid),
      builder: (context, snapshot) {
        final profile = snapshot.data?.data() ?? const <String, dynamic>{};
        final name =
            (profile['displayName'] as String?)?.trim().isNotEmpty == true
            ? profile['displayName'] as String
            : user.displayName?.trim().isNotEmpty == true
            ? user.displayName!
            : 'Mon compte WAPI';
        final phone =
            (profile['phoneNumber'] as String?) ?? user.phoneNumber ?? '';
        final photo = (profile['photoUrl'] as String?) ?? user.photoURL ?? '';
        final verified = profile['verified'] == true;
        return ListView(
          padding: const EdgeInsets.only(bottom: 30),
          children: [
            _HomeIdentity(
              name: name,
              phone: phone,
              photoUrl: photo,
              verified: verified,
              onTap: () => _showAccountSwitcher(
                context,
                name: name,
                phone: phone,
                photoUrl: photo,
                verified: verified,
              ),
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Expanded(
                    child: _HomeAction(
                      icon: Icons.person_add_alt_1_rounded,
                      label: 'Ajouter',
                      onTap: () => _push(
                        context,
                        _ContactsPage(user: user, repository: repository),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _HomeAction(
                      icon: Icons.videocam_rounded,
                      label: 'Live',
                      onTap: () => _push(context, WapiLivePage(user: user)),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _HomeAction(
                      icon: Icons.qr_code_scanner_rounded,
                      label: 'Mon QR',
                      onTap: () => _push(
                        context,
                        _ProfilePage(user: user, repository: repository),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const _HomeSectionTitle('Communiquer'),
            _HomeSection(
              children: [
                _HomeRow(
                  icon: Icons.contacts_rounded,
                  title: 'Contacts',
                  detail: 'Ajouter, scanner et retrouver vos proches',
                  onTap: () => _push(
                    context,
                    _ContactsPage(user: user, repository: repository),
                  ),
                ),
                _HomeRow(
                  icon: Icons.live_tv_rounded,
                  title: 'En direct',
                  detail: 'Regarder ou lancer une diffusion',
                  accent: const Color(0xFFF0445A),
                  onTap: () => _push(context, WapiLivePage(user: user)),
                ),
                _HomeRow(
                  icon: Icons.campaign_rounded,
                  title: 'Chaînes',
                  detail: 'Créateurs, médias et publications',
                  onTap: () => _push(context, _ChannelsPage(user: user)),
                ),
              ],
            ),
            const _HomeSectionTitle('Découvrir'),
            _HomeSection(
              children: [
                _HomeRow(
                  icon: Icons.radio_rounded,
                  title: 'Radio et podcasts',
                  detail: 'Émissions audio et épisodes publiés',
                  accent: const Color(0xFF7D5CF5),
                  onTap: () => _push(context, _RadioPage(user: user)),
                ),
                _HomeRow(
                  icon: Icons.storefront_rounded,
                  title: 'Marché',
                  detail: 'Produits et annonces WAPI',
                  accent: const Color(0xFF00A77A),
                  onTap: () => _push(
                    context,
                    WapiCommercePage(user: user, initialTab: 1),
                  ),
                ),
                _HomeRow(
                  icon: Icons.confirmation_number_rounded,
                  title: 'TicketBulk',
                  detail: 'Événements, billets QR et contrôle d’accès',
                  accent: const Color(0xFF087D62),
                  onTap: () => _push(context, WapiTicketBulkPage(user: user)),
                ),
                _HomeRow(
                  icon: Icons.sports_esports_rounded,
                  title: 'WAPI Games',
                  detail: 'King QI, Wapi Pool et profils joueurs',
                  accent: const Color(0xFF00A884),
                  onTap: () => _push(context, WapiGamesPage(user: user)),
                ),
                _HomeRow(
                  icon: Icons.volunteer_activism_rounded,
                  title: 'Services WAPI',
                  detail: 'Transport, livraison et assistance',
                  accent: const Color(0xFFF29B22),
                  onTap: () => _push(context, WapiServicesPage(user: user)),
                ),
              ],
            ),
            const _HomeSectionTitle('Créer et gérer'),
            _HomeSection(
              children: [
                _HomeRow(
                  icon: Icons.business_center_rounded,
                  title: 'WAPI Business',
                  detail: 'Pages, catalogue, offres et publicité',
                  accent: const Color(0xFFF29B22),
                  onTap: () => _push(context, WapiCommercePage(user: user)),
                ),
                _HomeRow(
                  icon: Icons.auto_awesome_rounded,
                  title: 'Jumeau numérique',
                  detail: 'Identité, consentements et studio IA',
                  accent: const Color(0xFF4355D6),
                  onTap: () => _push(context, _TwinPage(user: user)),
                ),
                _HomeRow(
                  icon: Icons.settings_rounded,
                  title: 'Compte et réglages',
                  detail: 'Profil, confidentialité et stockage',
                  accent: WapiColors.muted,
                  onTap: () => _push(
                    context,
                    _ProfilePage(user: user, repository: repository),
                  ),
                ),
              ],
            ),
          ],
        );
      },
    ),
  );

  void _push(BuildContext context, Widget page) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));

  Future<void> _showAccountSwitcher(
    BuildContext context, {
    required String name,
    required String phone,
    required String photoUrl,
    required bool verified,
  }) async {
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (sheetContext) => StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
        stream: FirebaseFirestore.instance
            .collection('businessPages')
            .where('ownerId', isEqualTo: user.uid)
            .snapshots(),
        builder: (context, snapshot) {
          final pages = snapshot.data?.docs ?? const [];
          return ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
            children: [
              const Text(
                'Choisir un compte',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 5),
              const Text(
                'Passez de votre profil personnel à une page Business sans mélanger vos identités.',
                style: TextStyle(color: WapiColors.muted),
              ),
              const SizedBox(height: 16),
              _AccountChoice(
                name: name,
                detail: phone.isEmpty ? 'Compte personnel WAPI' : phone,
                photoUrl: photoUrl,
                verified: verified,
                personal: true,
                onTap: () {
                  Navigator.pop(sheetContext);
                  _push(
                    context,
                    _ProfilePage(user: user, repository: repository),
                  );
                },
              ),
              const SizedBox(height: 9),
              ...pages.map((document) {
                final page = document.data();
                return Padding(
                  padding: const EdgeInsets.only(bottom: 9),
                  child: _AccountChoice(
                    name: (page['name'] as String?)?.trim().isNotEmpty == true
                        ? page['name'] as String
                        : 'Business WAPI',
                    detail:
                        ((page['category'] as String?) ?? 'Business') +
                        ' · ' +
                        ((page['city'] as String?) ?? 'WAPI'),
                    photoUrl: (page['logoUrl'] as String?) ?? '',
                    verified: page['verified'] == true,
                    personal: false,
                    onTap: () {
                      Navigator.pop(sheetContext);
                      _push(context, WapiCommercePage(user: user));
                    },
                  ),
                );
              }),
              if (pages.isEmpty)
                OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(sheetContext);
                    _push(context, WapiCommercePage(user: user));
                  },
                  icon: const Icon(Icons.add_business_outlined),
                  label: const Text('Créer une page Business'),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _HomeIdentity extends StatelessWidget {
  const _HomeIdentity({
    required this.name,
    required this.phone,
    required this.photoUrl,
    required this.verified,
    required this.onTap,
  });

  final String name;
  final String phone;
  final String photoUrl;
  final bool verified;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 17, 14, 17),
        child: Row(
          children: [
            CircleAvatar(
              radius: 31,
              backgroundColor: WapiColors.blueSoft,
              foregroundColor: WapiColors.blue,
              backgroundImage: photoUrl.isEmpty ? null : NetworkImage(photoUrl),
              child: photoUrl.isEmpty
                  ? Text(
                      name.characters.first.toUpperCase(),
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w900,
                      ),
                    )
                  : null,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                      if (verified) ...[
                        const SizedBox(width: 5),
                        const Icon(
                          Icons.verified_rounded,
                          color: Color(0xFF2088D6),
                          size: 18,
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(
                    (phone.isEmpty ? 'Compte personnel WAPI' : phone) +
                        ' · toucher pour changer',
                    style: const TextStyle(
                      color: WapiColors.muted,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
              decoration: BoxDecoration(
                color: WapiColors.blueSoft,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Row(
                children: [
                  CircleAvatar(radius: 4, backgroundColor: Color(0xFF00A77A)),
                  SizedBox(width: 6),
                  Text(
                    'Connecté',
                    style: TextStyle(
                      color: WapiColors.blueDark,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 4),
            const Icon(Icons.chevron_right_rounded, color: WapiColors.muted),
          ],
        ),
      ),
    ),
  );
}

class _AccountChoice extends StatelessWidget {
  const _AccountChoice({
    required this.name,
    required this.detail,
    required this.photoUrl,
    required this.verified,
    required this.personal,
    required this.onTap,
  });
  final String name;
  final String detail;
  final String photoUrl;
  final bool verified;
  final bool personal;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    child: ListTile(
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 13, vertical: 7),
      leading: CircleAvatar(
        backgroundColor: personal
            ? WapiColors.blueSoft
            : const Color(0xFFEAF8F4),
        backgroundImage: photoUrl.isEmpty ? null : NetworkImage(photoUrl),
        child: photoUrl.isEmpty
            ? Icon(
                personal ? Icons.person_rounded : Icons.storefront_rounded,
                color: personal ? WapiColors.blue : const Color(0xFF087D62),
              )
            : null,
      ),
      title: Row(
        children: [
          Flexible(
            child: Text(
              name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w900),
            ),
          ),
          if (verified) ...[
            const SizedBox(width: 4),
            const Icon(
              Icons.verified_rounded,
              color: Color(0xFF2088D6),
              size: 17,
            ),
          ],
        ],
      ),
      subtitle: Text(detail, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Icon(
        personal ? Icons.person_outline_rounded : Icons.business_center_rounded,
        color: personal ? WapiColors.blue : const Color(0xFF087D62),
      ),
    ),
  );
}

class _HomeAction extends StatelessWidget {
  const _HomeAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(15),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(15),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 13),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: WapiColors.blue, size: 21),
            const SizedBox(width: 7),
            Flexible(
              child: Text(
                label,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w800),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _HomeSectionTitle extends StatelessWidget {
  const _HomeSectionTitle(this.title);

  final String title;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(18, 22, 18, 8),
    child: Text(
      title.toUpperCase(),
      style: const TextStyle(
        color: WapiColors.muted,
        fontSize: 11,
        fontWeight: FontWeight.w800,
        letterSpacing: .7,
      ),
    ),
  );
}

class _HomeSection extends StatelessWidget {
  const _HomeSection({required this.children});

  final List<_HomeRow> children;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    child: Column(
      children: [
        for (var index = 0; index < children.length; index++) ...[
          children[index],
          if (index < children.length - 1)
            const Divider(height: 1, indent: 70, endIndent: 14),
        ],
      ],
    ),
  );
}

class _HomeRow extends StatelessWidget {
  const _HomeRow({
    required this.icon,
    required this.title,
    required this.detail,
    required this.onTap,
    this.accent = WapiColors.blue,
  });

  final IconData icon;
  final String title;
  final String detail;
  final VoidCallback onTap;
  final Color accent;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: accent.withValues(alpha: .11),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: accent, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  detail,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: WapiColors.muted, fontSize: 12),
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

class _CallsPage extends StatelessWidget {
  const _CallsPage({required this.user});
  final User user;

  void _openCall(
    BuildContext context, {
    required String peerId,
    required String peerName,
    required String peerPhotoUrl,
    required bool video,
    String? incomingCallId,
  }) {
    if (peerId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Ce contact WAPI n’est plus disponible.')),
      );
      return;
    }
    openWapiCall(
      context,
      incomingCallId == null
          ? WapiCallPage.outgoing(
              user: user,
              peerId: peerId,
              peerName: peerName,
              peerPhotoUrl: peerPhotoUrl,
              video: video,
            )
          : WapiCallPage.incoming(
              user: user,
              incomingCallId: incomingCallId,
              peerId: peerId,
              peerName: peerName,
              peerPhotoUrl: peerPhotoUrl,
              video: video,
            ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Appels',
      subtitle: 'Historique audio et vidéo',
    ),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('directCallSessions')
          .where('memberIds', arrayContains: user.uid)
          .limit(50)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Historique indisponible',
            body: 'WAPI ne peut pas charger les appels entrants.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final calls = snapshot.data!.docs.toList()
          ..sort((left, right) {
            final leftDate = _callDate(left.data()['createdAt']);
            final rightDate = _callDate(right.data()['createdAt']);
            return (rightDate?.millisecondsSinceEpoch ?? 0).compareTo(
              leftDate?.millisecondsSinceEpoch ?? 0,
            );
          });
        if (calls.isEmpty) {
          return const _StateMessage(
            icon: Icons.call_outlined,
            title: 'Aucun appel récent',
            body:
                'Lancez un appel audio ou vidéo depuis une conversation WAPI.',
          );
        }
        return ListView.separated(
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 28),
          itemCount: calls.length + 1,
          separatorBuilder: (_, index) => SizedBox(height: index == 0 ? 12 : 8),
          itemBuilder: (context, index) {
            if (index == 0) {
              return Row(
                children: [
                  Text(
                    'RÉCENTS',
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: WapiColors.muted,
                      fontWeight: FontWeight.w900,
                      letterSpacing: .8,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    '${calls.length} appel${calls.length > 1 ? 's' : ''}',
                    style: const TextStyle(
                      color: WapiColors.muted,
                      fontSize: 12,
                    ),
                  ),
                ],
              );
            }
            final document = calls[index - 1];
            final call = document.data();
            final incoming = call['calleeId'] == user.uid;
            final peerId = incoming
                ? (call['callerId'] as String?) ?? ''
                : (call['calleeId'] as String?) ?? '';
            final peerName = incoming
                ? (call['callerName'] as String?) ?? 'Contact WAPI'
                : (call['calleeName'] as String?) ?? 'Contact WAPI';
            final peerPhotoUrl = incoming
                ? (call['callerPhotoUrl'] as String?) ?? ''
                : (call['calleePhotoUrl'] as String?) ?? '';
            final video = call['video'] == true;
            final status = (call['status'] as String?) ?? '';
            final ringing = status == 'ringing';
            final acceptedAt = _callDate(call['acceptedAt']);
            final endedAt = _callDate(call['endedAt']);
            final createdAt = _callDate(call['createdAt']);
            final duration = acceptedAt != null && endedAt != null
                ? endedAt.difference(acceptedAt).inSeconds.clamp(0, 86400)
                : 0;
            final missed = incoming && acceptedAt == null && !ringing;
            final stateColor = missed || status == 'declined'
                ? const Color(0xFFC4323F)
                : ringing
                ? const Color(0xFF087D62)
                : WapiColors.muted;
            return Card(
              child: ListTile(
                minTileHeight: 76,
                contentPadding: const EdgeInsets.fromLTRB(12, 6, 8, 6),
                onTap: incoming && ringing
                    ? () => _openCall(
                        context,
                        peerId: peerId,
                        peerName: peerName,
                        peerPhotoUrl: peerPhotoUrl,
                        video: video,
                        incomingCallId: document.id,
                      )
                    : null,
                leading: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    _LiveProfileAvatar(
                      userId: peerId,
                      fallbackUrl: peerPhotoUrl,
                      name: peerName,
                      radius: 25,
                    ),
                    Positioned(
                      right: -3,
                      bottom: -3,
                      child: Container(
                        width: 21,
                        height: 21,
                        decoration: BoxDecoration(
                          color: video ? WapiColors.blue : WapiColors.emerald,
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 2),
                        ),
                        child: Icon(
                          video ? Icons.videocam_rounded : Icons.call_rounded,
                          color: Colors.white,
                          size: 11,
                        ),
                      ),
                    ),
                  ],
                ),
                title: Text(
                  peerName,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: Row(
                  children: [
                    Icon(
                      incoming
                          ? Icons.call_received_rounded
                          : Icons.call_made_rounded,
                      size: 15,
                      color: stateColor,
                    ),
                    const SizedBox(width: 5),
                    Expanded(
                      child: Text(
                        '${_callState(status, incoming: incoming, accepted: acceptedAt != null)} · ${_callMoment(createdAt)}${duration > 0 ? ' · ${_callDuration(duration)}' : ''}',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(color: stateColor, fontSize: 12),
                      ),
                    ),
                  ],
                ),
                trailing: IconButton(
                  tooltip: ringing && incoming ? 'Répondre' : 'Rappeler',
                  onPressed: () => _openCall(
                    context,
                    peerId: peerId,
                    peerName: peerName,
                    peerPhotoUrl: peerPhotoUrl,
                    video: video,
                    incomingCallId: ringing && incoming ? document.id : null,
                  ),
                  icon: Icon(
                    ringing && incoming
                        ? Icons.call_rounded
                        : video
                        ? Icons.videocam_outlined
                        : Icons.call_outlined,
                    color: ringing && incoming
                        ? WapiColors.emerald
                        : WapiColors.blueDark,
                  ),
                ),
              ),
            );
          },
        );
      },
    ),
  );
}

DateTime? _callDate(Object? value) =>
    value is Timestamp ? value.toDate() : null;

String _callState(
  String status, {
  required bool incoming,
  required bool accepted,
}) {
  if (status == 'ringing') return incoming ? 'Appel entrant' : 'En attente';
  if (status == 'declined') return incoming ? 'Appel refusé' : 'Refusé';
  if (!accepted) return incoming ? 'Appel manqué' : 'Sans réponse';
  return incoming ? 'Appel reçu' : 'Appel émis';
}

String _callMoment(DateTime? value) {
  if (value == null) return 'heure inconnue';
  final local = value.toLocal();
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(local.year, local.month, local.day);
  final clock =
      '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  if (day == today) return 'Aujourd’hui $clock';
  if (day == today.subtract(const Duration(days: 1))) return 'Hier $clock';
  return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')} $clock';
}

String _callDuration(int seconds) {
  final minutes = seconds ~/ 60;
  final remaining = seconds % 60;
  return minutes > 0
      ? '${minutes} min ${remaining.toString().padLeft(2, '0')} s'
      : '$remaining s';
}

class _AssistantPage extends StatelessWidget {
  const _AssistantPage({required this.user});
  final User user;

  @override
  Widget build(BuildContext context) => WiaChatPage(user: user);
}

class _ChannelsPage extends StatelessWidget {
  const _ChannelsPage({required this.user});
  final User user;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Chaînes',
      subtitle: 'Créateurs, médias et informations',
    ),
    floatingActionButton: FloatingActionButton.extended(
      onPressed: () => _createChannel(context),
      icon: const Icon(Icons.add),
      label: const Text('Créer'),
    ),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('channels')
          .orderBy('updatedAt', descending: true)
          .limit(80)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Chaînes indisponibles',
            body: 'La liste ne peut pas être chargée pour le moment.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final channels = snapshot.data!.docs;
        if (channels.isEmpty) {
          return _StateMessage(
            icon: Icons.campaign_outlined,
            title: 'Créez la première chaîne',
            body:
                'Publiez des informations, épisodes et annonces suivies par votre audience.',
            actionLabel: 'Créer une chaîne',
            onAction: () => _createChannel(context),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
          itemCount: channels.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final doc = channels[index];
            final data = doc.data();
            final count = (data['memberCount'] as num?)?.toInt() ?? 0;
            return Card(
              child: ListTile(
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) =>
                        _ChannelDetailPage(user: user, channelId: doc.id),
                  ),
                ),
                leading: const CircleAvatar(
                  backgroundColor: WapiColors.blueSoft,
                  foregroundColor: WapiColors.blue,
                  child: Icon(Icons.campaign_outlined),
                ),
                title: Text(
                  (data['name'] as String?) ?? 'Chaîne WAPI',
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: Text(
                  '${(data['category'] as String?)?.isNotEmpty == true ? '${data['category']} · ' : ''}$count abonné${count > 1 ? 's' : ''}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: const Icon(Icons.chevron_right),
              ),
            );
          },
        );
      },
    ),
  );

  Future<void> _createChannel(BuildContext context) async {
    final name = TextEditingController();
    final description = TextEditingController();
    final category = TextEditingController(text: 'Actualités');
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          20,
          20,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Nouvelle chaîne',
              style: Theme.of(
                sheetContext,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: name,
              maxLength: 80,
              decoration: const InputDecoration(labelText: 'Nom de la chaîne'),
            ),
            TextField(
              controller: category,
              maxLength: 40,
              decoration: const InputDecoration(labelText: 'Catégorie'),
            ),
            TextField(
              controller: description,
              minLines: 2,
              maxLines: 4,
              maxLength: 300,
              decoration: const InputDecoration(labelText: 'Présentation'),
            ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: () async {
                try {
                  final id = await WapiRepository(FirebaseFirestore.instance)
                      .createChannel(
                        user: user,
                        name: name.text,
                        description: description.text,
                        category: category.text,
                      );
                  if (!sheetContext.mounted) return;
                  Navigator.of(sheetContext).pop();
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) =>
                          _ChannelDetailPage(user: user, channelId: id),
                    ),
                  );
                } catch (error) {
                  if (sheetContext.mounted) {
                    ScaffoldMessenger.of(sheetContext).showSnackBar(
                      SnackBar(
                        content: Text(
                          wapiErrorText(
                            error,
                            fallback: 'La chaîne n’a pas pu être créée.',
                          ),
                        ),
                      ),
                    );
                  }
                }
              },
              child: const Text('Créer la chaîne'),
            ),
          ],
        ),
      ),
    );
    name.dispose();
    description.dispose();
    category.dispose();
  }
}

class _ChannelDetailPage extends StatelessWidget {
  const _ChannelDetailPage({required this.user, required this.channelId});
  final User user;
  final String channelId;

  @override
  Widget build(
    BuildContext context,
  ) => StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
    stream: FirebaseFirestore.instance
        .collection('channels')
        .doc(channelId)
        .snapshots(),
    builder: (context, channelSnapshot) {
      if (!channelSnapshot.hasData) {
        return const Scaffold(body: Center(child: CircularProgressIndicator()));
      }
      final data = channelSnapshot.data!.data();
      if (data == null) {
        return const Scaffold(
          body: _StateMessage(
            icon: Icons.error_outline,
            title: 'Chaîne introuvable',
            body: 'Cette chaîne a été supprimée.',
          ),
        );
      }
      final owner = data['ownerId'] == user.uid;
      final members = List<String>.from(data['memberIds'] as List? ?? const []);
      final joined = members.contains(user.uid);
      return Scaffold(
        appBar: AppBar(title: Text((data['name'] as String?) ?? 'Chaîne WAPI')),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    (data['description'] as String?) ?? '',
                    style: const TextStyle(
                      color: WapiColors.muted,
                      height: 1.35,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Text(
                        '${(data['memberCount'] as num?)?.toInt() ?? 0} abonné(s)',
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      const Spacer(),
                      if (!owner && !joined)
                        FilledButton.icon(
                          onPressed: () async {
                            try {
                              await WapiRepository(
                                FirebaseFirestore.instance,
                              ).joinChannel(
                                channelId: channelId,
                                userId: user.uid,
                              );
                            } catch (error) {
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      'Abonnement impossible : $error',
                                    ),
                                  ),
                                );
                              }
                            }
                          },
                          icon: const Icon(Icons.add),
                          label: const Text('Suivre'),
                        )
                      else
                        const Chip(label: Text('Abonné')),
                    ],
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
                stream: FirebaseFirestore.instance
                    .collection('channels')
                    .doc(channelId)
                    .collection('posts')
                    .orderBy('createdAt', descending: true)
                    .snapshots(),
                builder: (context, postsSnapshot) {
                  if (postsSnapshot.hasError) {
                    return const _StateMessage(
                      icon: Icons.cloud_off_outlined,
                      title: 'Publications indisponibles',
                      body: 'Réessayez dans un instant.',
                    );
                  }
                  if (!postsSnapshot.hasData) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  final posts = postsSnapshot.data!.docs;
                  if (posts.isEmpty) {
                    return const _StateMessage(
                      icon: Icons.edit_note_outlined,
                      title: 'Aucune publication',
                      body:
                          'Le créateur peut publier le premier message de cette chaîne.',
                    );
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.all(16),
                    itemCount: posts.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final post = posts[index].data();
                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                (post['authorName'] as String?) ??
                                    'Créateur WAPI',
                                style: const TextStyle(
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Text((post['text'] as String?) ?? ''),
                            ],
                          ),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
            if (owner) _ChannelComposer(user: user, channelId: channelId),
          ],
        ),
      );
    },
  );
}

class _ChannelComposer extends StatefulWidget {
  const _ChannelComposer({required this.user, required this.channelId});
  final User user;
  final String channelId;
  @override
  State<_ChannelComposer> createState() => _ChannelComposerState();
}

class _ChannelComposerState extends State<_ChannelComposer> {
  final _controller = TextEditingController();
  bool _sending = false;
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    top: false,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _controller,
              minLines: 1,
              maxLines: 4,
              decoration: const InputDecoration(
                hintText: 'Publier dans la chaîne',
              ),
            ),
          ),
          IconButton(
            onPressed: _sending
                ? null
                : () async {
                    setState(() => _sending = true);
                    try {
                      await WapiRepository(
                        FirebaseFirestore.instance,
                      ).publishChannelPost(
                        user: widget.user,
                        channelId: widget.channelId,
                        text: _controller.text,
                      );
                      _controller.clear();
                    } catch (error) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              wapiErrorText(
                                error,
                                fallback:
                                    'La publication n’a pas pu être envoyée.',
                              ),
                            ),
                          ),
                        );
                      }
                    } finally {
                      if (mounted) setState(() => _sending = false);
                    }
                  },
            icon: _sending
                ? const CircularProgressIndicator()
                : const Icon(Icons.send),
            color: WapiColors.blue,
          ),
        ],
      ),
    ),
  );
}

class _RadioPage extends StatefulWidget {
  const _RadioPage({required this.user});
  final User user;
  @override
  State<_RadioPage> createState() => _RadioPageState();
}

class _RadioPageState extends State<_RadioPage> {
  final _repository = WapiRepository(FirebaseFirestore.instance);
  final _player = AudioPlayer();
  String _playingId = '';
  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Radio & podcasts',
      subtitle: 'Studio, direct et épisodes WAPI',
    ),
    floatingActionButton: FloatingActionButton.extended(
      onPressed: _publish,
      icon: const Icon(Icons.mic_rounded),
      label: const Text('Studio'),
    ),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('radioEpisodes')
          .orderBy('createdAt', descending: true)
          .limit(80)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Radio indisponible',
            body: 'Les épisodes ne peuvent pas être chargés pour le moment.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final episodes = snapshot.data!.docs;
        if (episodes.isEmpty) {
          return _StateMessage(
            icon: Icons.radio_outlined,
            title: 'Publiez le premier épisode',
            body:
                'Enregistrez directement avec le studio WAPI ou importez votre production.',
            actionLabel: 'Ouvrir le studio',
            onAction: _publish,
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
          itemCount: episodes.length + 1,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            if (index == 0) {
              return _RadioStudioBanner(onOpen: _publish);
            }
            final doc = episodes[index - 1];
            final data = doc.data();
            final isPlaying = _playingId == doc.id;
            final duration = Duration(
              seconds: (data['durationSeconds'] as num?)?.toInt() ?? 0,
            );
            return Card(
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: WapiColors.blueSoft,
                  foregroundColor: WapiColors.blue,
                  child: Icon(
                    isPlaying ? Icons.graphic_eq : Icons.radio_outlined,
                  ),
                ),
                title: Text(
                  (data['title'] as String?) ?? 'Épisode WAPI',
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: Text(
                  '${(data['stationName'] as String?) ?? 'Radio WAPI'} · ${duration.inMinutes}:${(duration.inSeconds % 60).toString().padLeft(2, '0')}',
                ),
                trailing: IconButton(
                  onPressed: () async {
                    final url = (data['audioUrl'] as String?) ?? '';
                    if (url.isEmpty) return;
                    if (isPlaying) {
                      await _player.stop();
                      if (mounted) setState(() => _playingId = '');
                      return;
                    }
                    try {
                      await _player.setUrl(url);
                      await _player.play();
                      if (mounted) setState(() => _playingId = doc.id);
                    } catch (_) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Lecture audio impossible.'),
                          ),
                        );
                      }
                    }
                  },
                  icon: Icon(
                    isPlaying
                        ? Icons.stop_circle_outlined
                        : Icons.play_circle_outline,
                  ),
                  color: WapiColors.blue,
                ),
              ),
            );
          },
        );
      },
    ),
  );

  Future<void> _publish() => Navigator.of(context).push(
    MaterialPageRoute(
      fullscreenDialog: true,
      builder: (_) =>
          _RadioStudioPage(user: widget.user, repository: _repository),
    ),
  );
}

class _RadioStudioBanner extends StatelessWidget {
  const _RadioStudioBanner({required this.onOpen});
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onOpen,
    borderRadius: BorderRadius.circular(24),
    child: Ink(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF071E2B), Color(0xFF0A7080)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        children: [
          Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: .12),
              shape: BoxShape.circle,
              border: Border.all(color: Colors.white24),
            ),
            child: const Icon(
              Icons.mic_rounded,
              color: Color(0xFF72E8FF),
              size: 28,
            ),
          ),
          const SizedBox(width: 14),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'STUDIO WAPI',
                  style: TextStyle(
                    color: Color(0xFF9DDDE8),
                    fontSize: 10,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1,
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  'Enregistrez votre émission',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                SizedBox(height: 3),
                Text(
                  'Son HD, traitements voix et préécoute',
                  style: TextStyle(color: Color(0xFFD5EEF2), fontSize: 12),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: Colors.white),
        ],
      ),
    ),
  );
}

class _RadioStudioPage extends StatefulWidget {
  const _RadioStudioPage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;

  @override
  State<_RadioStudioPage> createState() => _RadioStudioPageState();
}

class _RadioStudioPageState extends State<_RadioStudioPage> {
  final _station = TextEditingController(text: 'Ma radio WAPI');
  final _title = TextEditingController();
  final _recorder = AudioRecorder();
  final _previewPlayer = AudioPlayer();
  StreamSubscription<Amplitude>? _meterSubscription;
  StreamSubscription<PlayerState>? _previewSubscription;
  Timer? _timer;
  String _profile = 'studio';
  String? _audioPath;
  String _audioName = '';
  int _durationSeconds = 0;
  double _level = 0;
  bool _recording = false;
  bool _previewing = false;
  bool _publishing = false;

  @override
  void initState() {
    super.initState();
    _previewSubscription = _previewPlayer.playerStateStream.listen((state) {
      if (!mounted) return;
      if (state.processingState == ProcessingState.completed ||
          !state.playing && _previewing) {
        setState(() => _previewing = false);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _meterSubscription?.cancel();
    _previewSubscription?.cancel();
    _recorder.dispose();
    _previewPlayer.dispose();
    _station.dispose();
    _title.dispose();
    super.dispose();
  }

  RecordConfig get _recordConfig => switch (_profile) {
    'interview' => const RecordConfig(
      encoder: AudioEncoder.aacLc,
      bitRate: 160000,
      sampleRate: 48000,
      numChannels: 1,
      autoGain: true,
      echoCancel: true,
      noiseSuppress: true,
    ),
    'ambience' => const RecordConfig(
      encoder: AudioEncoder.aacLc,
      bitRate: 192000,
      sampleRate: 48000,
      numChannels: 2,
      autoGain: false,
      echoCancel: false,
      noiseSuppress: false,
    ),
    _ => const RecordConfig(
      encoder: AudioEncoder.aacLc,
      bitRate: 192000,
      sampleRate: 48000,
      numChannels: 1,
      autoGain: true,
      echoCancel: false,
      noiseSuppress: true,
    ),
  };

  String get _processingLabel => switch (_profile) {
    'interview' => 'Anti-écho · réduction du bruit · gain automatique',
    'ambience' => 'Stéréo 48 kHz · ambiance naturelle',
    _ => 'Voix HD · réduction du bruit · gain automatique',
  };

  String _clock(int seconds) =>
      '${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}';

  Future<void> _startRecording() async {
    if (!await _recorder.hasPermission()) {
      _snack('Autorisez le microphone pour ouvrir le studio.', error: true);
      return;
    }
    final directory = await getTemporaryDirectory();
    final path =
        '${directory.path}/wapi-radio-${DateTime.now().millisecondsSinceEpoch}.m4a';
    try {
      await _previewPlayer.stop();
      await _recorder.start(_recordConfig, path: path);
      _timer?.cancel();
      _meterSubscription?.cancel();
      setState(() {
        _recording = true;
        _previewing = false;
        _durationSeconds = 0;
        _audioPath = null;
        _audioName = '';
      });
      _timer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted && _recording) {
          setState(() => _durationSeconds++);
        }
      });
      _meterSubscription = _recorder
          .onAmplitudeChanged(const Duration(milliseconds: 100))
          .listen((amplitude) {
            if (!mounted || !_recording) return;
            setState(() {
              _level = ((amplitude.current + 60) / 60).clamp(0.02, 1.0);
            });
          });
    } catch (_) {
      _snack('Le studio audio n’a pas pu démarrer.', error: true);
    }
  }

  Future<void> _stopRecording() async {
    _timer?.cancel();
    await _meterSubscription?.cancel();
    final path = await _recorder.stop();
    if (!mounted) return;
    if (path == null || _durationSeconds < 1) {
      setState(() {
        _recording = false;
        _level = 0;
      });
      _snack('L’enregistrement est trop court.', error: true);
      return;
    }
    setState(() {
      _recording = false;
      _level = 0;
      _audioPath = path;
      _audioName = 'emission-wapi-${DateTime.now().millisecondsSinceEpoch}.m4a';
    });
  }

  Future<void> _importAudio() async {
    final audio = await FilePicker.pickFile(type: FileType.audio);
    if (audio?.path == null) return;
    try {
      final duration = await _previewPlayer.setFilePath(audio!.path!);
      if (!mounted) return;
      setState(() {
        _audioPath = audio.path;
        _audioName = audio.name;
        _durationSeconds = duration?.inSeconds ?? 0;
        _previewing = false;
      });
    } catch (_) {
      _snack('Ce fichier audio ne peut pas être utilisé.', error: true);
    }
  }

  Future<void> _togglePreview() async {
    final path = _audioPath;
    if (path == null) return;
    try {
      if (_previewing) {
        await _previewPlayer.pause();
        if (mounted) setState(() => _previewing = false);
        return;
      }
      await _previewPlayer.setFilePath(path);
      await _previewPlayer.play();
      if (mounted) setState(() => _previewing = true);
    } catch (_) {
      _snack('La préécoute est indisponible.', error: true);
    }
  }

  Future<void> _publish() async {
    final path = _audioPath;
    if (path == null || _durationSeconds < 1) {
      _snack('Enregistrez ou importez d’abord un épisode.', error: true);
      return;
    }
    if (_station.text.trim().length < 2 || _title.text.trim().length < 2) {
      _snack('Ajoutez le nom de la radio et le titre.', error: true);
      return;
    }
    setState(() => _publishing = true);
    try {
      final extension = _audioName.toLowerCase().split('.').last;
      final contentType = extension == 'm4a'
          ? 'audio/mp4'
          : extension == 'wav'
          ? 'audio/wav'
          : extension == 'ogg'
          ? 'audio/ogg'
          : 'audio/mpeg';
      await widget.repository.publishRadioEpisode(
        user: widget.user,
        file: File(path),
        fileName: _audioName,
        stationName: _station.text,
        title: _title.text,
        durationSeconds: _durationSeconds,
        contentType: contentType,
      );
      if (mounted) Navigator.of(context).pop();
    } catch (_) {
      _snack('L’épisode n’a pas pu être publié. Réessayez.', error: true);
    } finally {
      if (mounted) setState(() => _publishing = false);
    }
  }

  void _snack(String message, {bool error = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: error ? const Color(0xFFB42318) : null,
        content: Text(message),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'Studio Radio WAPI',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 30),
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFF071923), Color(0xFF0B6572)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(26),
          ),
          child: Column(
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 9,
                      vertical: 5,
                    ),
                    decoration: BoxDecoration(
                      color: _recording
                          ? const Color(0xFFE14352)
                          : Colors.white12,
                      borderRadius: BorderRadius.circular(99),
                    ),
                    child: Text(
                      _recording ? '●  EN DIRECT' : 'PRÊT À ENREGISTRER',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        fontWeight: FontWeight.w900,
                        letterSpacing: .7,
                      ),
                    ),
                  ),
                  const Spacer(),
                  Text(
                    _clock(_durationSeconds),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.w900,
                      fontFeatures: [FontFeature.tabularFigures()],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              _RadioLevelMeter(level: _level, active: _recording),
              const SizedBox(height: 18),
              GestureDetector(
                onTap: _recording ? _stopRecording : _startRecording,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 78,
                  height: 78,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _recording ? const Color(0xFFE14352) : Colors.white,
                    border: Border.all(color: Colors.white38, width: 5),
                    boxShadow: [
                      BoxShadow(
                        color:
                            (_recording
                                    ? const Color(0xFFE14352)
                                    : const Color(0xFF72E8FF))
                                .withValues(alpha: .3),
                        blurRadius: 24,
                        spreadRadius: 2,
                      ),
                    ],
                  ),
                  child: Icon(
                    _recording ? Icons.stop_rounded : Icons.mic_rounded,
                    color: _recording ? Colors.white : WapiColors.blueDark,
                    size: 36,
                  ),
                ),
              ),
              const SizedBox(height: 13),
              Text(
                _recording
                    ? 'Touchez pour terminer l’enregistrement'
                    : 'Touchez pour enregistrer votre podcast',
                style: const TextStyle(color: Color(0xFFD5EEF2)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        const Text(
          'PROFIL AUDIO',
          style: TextStyle(
            color: WapiColors.muted,
            fontSize: 11,
            fontWeight: FontWeight.w900,
            letterSpacing: .8,
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children:
              const {
                'studio': 'Studio HD',
                'interview': 'Interview',
                'ambience': 'Ambiance',
              }.entries.map((entry) {
                return ChoiceChip(
                  label: Text(entry.value),
                  selected: _profile == entry.key,
                  onSelected: _recording
                      ? null
                      : (_) => setState(() => _profile = entry.key),
                );
              }).toList(),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            const Icon(
              Icons.auto_fix_high_rounded,
              size: 16,
              color: WapiColors.blue,
            ),
            const SizedBox(width: 7),
            Expanded(
              child: Text(
                _processingLabel,
                style: const TextStyle(color: WapiColors.muted, fontSize: 12),
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TextField(
          controller: _station,
          maxLength: 60,
          enabled: !_recording,
          decoration: const InputDecoration(
            labelText: 'Nom de la radio',
            prefixIcon: Icon(Icons.radio_rounded),
          ),
        ),
        TextField(
          controller: _title,
          maxLength: 100,
          enabled: !_recording,
          decoration: const InputDecoration(
            labelText: 'Titre de l’épisode',
            prefixIcon: Icon(Icons.title_rounded),
          ),
        ),
        if (_audioPath != null) ...[
          const SizedBox(height: 4),
          Card(
            child: ListTile(
              leading: IconButton.filledTonal(
                onPressed: _togglePreview,
                icon: Icon(
                  _previewing ? Icons.pause_rounded : Icons.play_arrow_rounded,
                ),
              ),
              title: const Text(
                'Épisode prêt',
                style: TextStyle(fontWeight: FontWeight.w900),
              ),
              subtitle: Text('${_clock(_durationSeconds)} · $_audioName'),
              trailing: IconButton(
                onPressed: () => setState(() {
                  _audioPath = null;
                  _audioName = '';
                  _durationSeconds = 0;
                }),
                icon: const Icon(Icons.close_rounded),
              ),
            ),
          ),
        ],
        const SizedBox(height: 10),
        OutlinedButton.icon(
          onPressed: _recording ? null : _importAudio,
          icon: const Icon(Icons.audio_file_outlined),
          label: const Text('Importer une production audio'),
        ),
        const SizedBox(height: 10),
        FilledButton.icon(
          onPressed: _recording || _publishing ? null : _publish,
          icon: _publishing
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.podcasts_rounded),
          label: Text(_publishing ? 'Publication…' : 'Publier l’épisode'),
        ),
      ],
    ),
  );
}

class _RadioLevelMeter extends StatelessWidget {
  const _RadioLevelMeter({required this.level, required this.active});
  final double level;
  final bool active;

  @override
  Widget build(BuildContext context) => Row(
    children: List.generate(28, (index) {
      final threshold = index / 27;
      final lit = active && threshold <= level;
      final color = threshold > .82
          ? const Color(0xFFE95D64)
          : threshold > .62
          ? const Color(0xFFFFC857)
          : const Color(0xFF63E6BE);
      return Expanded(
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 90),
          height: 12 + (index % 4) * 3,
          margin: const EdgeInsets.symmetric(horizontal: 1.2),
          decoration: BoxDecoration(
            color: lit ? color : Colors.white12,
            borderRadius: BorderRadius.circular(99),
          ),
        ),
      );
    }),
  );
}

// ignore: unused_element
class _FeedPage extends StatelessWidget {
  const _FeedPage({
    required this.title,
    required this.collection,
    required this.icon,
  });
  final String title;
  final String collection;
  final IconData icon;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(title: title),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection(collection)
          .limit(80)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Contenu indisponible',
            body: 'WAPI ne peut pas charger ce flux maintenant.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final items = snapshot.data!.docs;
        if (items.isEmpty) {
          return _StateMessage(
            icon: icon,
            title: 'Rien à afficher',
            body: 'Les contenus réellement publiés sur WAPI apparaîtront ici.',
          );
        }
        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index].data();
            final title =
                (item['title'] ??
                        item['name'] ??
                        item['stationName'] ??
                        'Publication WAPI')
                    .toString();
            final detail =
                (item['description'] ??
                        item['lastPost'] ??
                        item['price'] ??
                        item['category'] ??
                        '')
                    .toString();
            return Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: WapiColors.blueSoft,
                  foregroundColor: WapiColors.blue,
                  child: Icon(icon),
                ),
                title: Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: Text(
                  detail.isEmpty ? 'Publié sur WAPI' : detail,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            );
          },
        );
      },
    ),
  );
}

class _ContactsPage extends StatelessWidget {
  const _ContactsPage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: 'Contacts',
      subtitle: 'Personnes sur WAPI',
      actions: [
        IconButton(
          onPressed: () => _showMyQr(context),
          icon: const Icon(Icons.qr_code_2_outlined),
          tooltip: 'Mon code WAPI',
        ),
        IconButton(
          onPressed: () => _showAddContact(context),
          icon: const Icon(Icons.person_add_alt_1_outlined),
          tooltip: 'Ajouter un contact',
        ),
      ],
    ),
    body: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: repository.profile(user.uid),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Contacts indisponibles',
            body: 'WAPI ne peut pas charger votre carnet pour le moment.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final contactMap = Map<String, dynamic>.from(
          snapshot.data?.data()?['contacts'] as Map? ?? const {},
        );
        final contacts =
            contactMap.entries.map((entry) {
              final value = Map<String, dynamic>.from(
                entry.value as Map? ?? const {},
              );
              value['uid'] = entry.key;
              return value;
            }).toList()..sort(
              (left, right) => (left['displayName'] ?? '').toString().compareTo(
                (right['displayName'] ?? '').toString(),
              ),
            );
        return ListView.builder(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: contacts.length + 1,
          itemBuilder: (context, index) {
            if (index == 0) {
              return Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 10),
                child: FilledButton.icon(
                  onPressed: () => _showAddContact(context),
                  icon: const Icon(Icons.person_add_alt_1_outlined),
                  label: const Text('Ajouter un contact WAPI'),
                ),
              );
            }
            final item = contacts[index - 1];
            final uid = item['uid'] as String;
            final name =
                ((item['displayName'] as String?)?.trim().isNotEmpty == true
                ? item['displayName'] as String
                : (item['phoneNumber'] as String?) ?? 'Membre WAPI');
            final phone = (item['phoneNumber'] as String?) ?? '';
            return ListTile(
              leading: _LiveProfileAvatar(
                userId: uid,
                fallbackUrl: (item['photoUrl'] as String?) ?? '',
                name: name,
              ),
              title: Text(
                name,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              subtitle: Text(phone.isEmpty ? 'Profil WAPI' : phone),
              trailing: const Icon(Icons.chat_bubble_outline),
              onTap: () => _openContact(
                context,
                uid: uid,
                name: name,
                phone: phone,
                photoUrl: (item['photoUrl'] as String?) ?? '',
              ),
            );
          },
        );
      },
    ),
  );

  Future<void> _openContact(
    BuildContext context, {
    required String uid,
    required String name,
    required String phone,
    required String photoUrl,
  }) async {
    try {
      final id = await repository.ensureDirectConversation(
        user: user,
        peerId: uid,
        peerName: name,
        peerPhone: phone,
        peerPhotoUrl: photoUrl,
      );
      if (!context.mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => _ChatPage(
            user: user,
            repository: repository,
            conversation: WapiConversation(
              id: id,
              title: name,
              preview: '',
              updatedAt: null,
              unread: false,
              isGroup: false,
              peerId: uid,
              avatarUrl: photoUrl,
              memberNames: {user.uid: user.displayName ?? 'Vous', uid: name},
              memberPhotoUrls: {user.uid: user.photoURL ?? '', uid: photoUrl},
              ownerId: user.uid,
            ),
          ),
        ),
      );
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wapiErrorText(
                error,
                fallback: 'La conversation n’a pas pu être ouverte.',
              ),
            ),
          ),
        );
      }
    }
  }

  Future<void> _showMyQr(BuildContext context) => showModalBottomSheet<void>(
    context: context,
    builder: (sheetContext) {
      final code = 'wapi://contact/${user.uid}';
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Mon code WAPI',
                style: Theme.of(
                  sheetContext,
                ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              const Text(
                'Présentez ce QR à votre contact : il ouvre directement votre profil WAPI.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 18),
              DecoratedBox(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: WapiColors.line),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: QrImageView(
                    data: code,
                    version: QrVersions.auto,
                    size: 218,
                    eyeStyle: const QrEyeStyle(
                      eyeShape: QrEyeShape.square,
                      color: WapiColors.blue,
                    ),
                    dataModuleStyle: const QrDataModuleStyle(
                      dataModuleShape: QrDataModuleShape.square,
                      color: WapiColors.ink,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Text(
                user.phoneNumber?.isNotEmpty == true
                    ? user.phoneNumber!
                    : 'Code personnel WAPI',
                style: const TextStyle(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 6),
              TextButton.icon(
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: code));
                  if (sheetContext.mounted) {
                    ScaffoldMessenger.of(sheetContext).showSnackBar(
                      const SnackBar(content: Text('Code WAPI copié.')),
                    );
                  }
                },
                icon: const Icon(Icons.copy_rounded),
                label: const Text('Copier mon code'),
              ),
            ],
          ),
        ),
      );
    },
  );

  Future<void> _showAddContact(BuildContext context) async {
    final controller = TextEditingController();
    final choice = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          20,
          20,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Ajouter un contact',
              style: Theme.of(
                sheetContext,
              ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: () => Navigator.of(sheetContext).pop('scan'),
              icon: const Icon(Icons.qr_code_scanner_outlined),
              label: const Text('Numériser un code WAPI'),
            ),
            const SizedBox(height: 6),
            TextButton.icon(
              onPressed: () => _showMyQr(sheetContext),
              icon: const Icon(Icons.qr_code_2_rounded),
              label: const Text('Afficher mon code personnel'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Numéro international',
                hintText: '+242 06 000 00 00',
              ),
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () => Navigator.of(sheetContext).pop(controller.text),
              child: const Text('Ajouter ce contact'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    if (!context.mounted || choice == null || choice.trim().isEmpty) return;
    final code = choice == 'scan'
        ? await Navigator.of(context).push<String>(
            MaterialPageRoute(builder: (_) => const _ContactQrScanner()),
          )
        : choice;
    if (!context.mounted || code == null || code.trim().isEmpty) return;
    try {
      final contact = await repository.resolveWapiContact(code);
      if (!context.mounted) return;
      if (contact['uid'] == user.uid) {
        throw ArgumentError('C’est votre propre code WAPI.');
      }
      await _openContact(
        context,
        uid: contact['uid']!,
        name: contact['displayName']!,
        phone: contact['phoneNumber']!,
        photoUrl: contact['photoUrl']!,
      );
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wapiErrorText(
                error,
                fallback: 'Ce contact n’a pas pu être ajouté.',
              ),
            ),
          ),
        );
      }
    }
  }
}

class _ContactQrScanner extends StatefulWidget {
  const _ContactQrScanner();
  @override
  State<_ContactQrScanner> createState() => _ContactQrScannerState();
}

class _ContactQrScannerState extends State<_ContactQrScanner> {
  bool _handled = false;
  late final MobileScannerController _scanner = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
    formats: const [BarcodeFormat.qrCode, BarcodeFormat.code128],
  );

  @override
  void dispose() {
    _scanner.dispose();
    super.dispose();
  }

  Future<void> _openSettings() async {
    await openAppSettings();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    appBar: _WapiAppBar(
      title: 'Numériser un code WAPI',
      subtitle: 'Cadrez le QR personnel de votre contact',
      actions: [
        ValueListenableBuilder<MobileScannerState>(
          valueListenable: _scanner,
          builder: (context, state, _) => IconButton(
            onPressed: state.torchState == TorchState.unavailable
                ? null
                : _scanner.toggleTorch,
            tooltip: 'Lampe',
            icon: Icon(
              state.torchState == TorchState.on
                  ? Icons.flash_on_rounded
                  : Icons.flash_off_rounded,
            ),
          ),
        ),
      ],
    ),
    body: Stack(
      fit: StackFit.expand,
      children: [
        MobileScanner(
          controller: _scanner,
          errorBuilder: (context, error) => Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.no_photography_outlined,
                    color: Colors.white,
                    size: 48,
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'La caméra est nécessaire pour numériser un code WAPI.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white, fontSize: 16),
                  ),
                  const SizedBox(height: 14),
                  FilledButton(
                    onPressed: _openSettings,
                    child: const Text('Autoriser la caméra'),
                  ),
                ],
              ),
            ),
          ),
          overlayBuilder: (context, constraints) => Center(
            child: Container(
              width: constraints.maxWidth * .70,
              height: constraints.maxWidth * .70,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: const Color(0xFF19A5FF), width: 3),
              ),
            ),
          ),
          onDetect: (capture) {
            if (_handled) return;
            final value = capture.barcodes
                .map((barcode) => barcode.rawValue?.trim() ?? '')
                .firstWhere(
                  (candidate) => candidate.startsWith('wapi://contact/'),
                  orElse: () => '',
                );
            if (value.isEmpty) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Ce code ne correspond pas à un contact WAPI.'),
                ),
              );
              return;
            }
            _handled = true;
            Navigator.of(context).pop(value);
          },
        ),
        const Align(
          alignment: Alignment.bottomCenter,
          child: SafeArea(
            minimum: EdgeInsets.all(24),
            child: Text(
              'Le code est scanné automatiquement.',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

class _TwinPage extends StatelessWidget {
  const _TwinPage({required this.user});
  final User user;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Jumeau numérique',
      subtitle: 'Création avec consentement',
    ),
    body: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('users')
          .doc(user.uid)
          .collection('twinProfiles')
          .doc('main')
          .snapshots(),
      builder: (context, snapshot) {
        final profile = snapshot.data?.data();
        final allowed =
            profile?['identityConsent'] == true &&
            profile?['voiceConsent'] == true &&
            profile?['movementConsent'] == true;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.auto_awesome,
                      color: WapiColors.blue,
                      size: 34,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      allowed ? 'Studio prêt' : 'Consentement requis',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      allowed
                          ? 'Vos consentements vérifiés autorisent la préparation de contenus. Chaque contenu devra être identifié comme généré par IA.'
                          : 'Avant toute capture ou génération, WAPI exige un consentement explicite pour l’identité, la voix et le mouvement.',
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: () => _saveConsent(context),
                      child: Text(
                        allowed
                            ? 'Mettre à jour mes consentements'
                            : 'Configurer mes consentements',
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    ),
  );
  Future<void> _saveConsent(BuildContext context) async {
    try {
      await FirebaseFirestore.instance
          .collection('users')
          .doc(user.uid)
          .collection('twinProfiles')
          .doc('main')
          .set({
            'userId': user.uid,
            'identityConsent': true,
            'voiceConsent': true,
            'movementConsent': true,
            'updatedAt': FieldValue.serverTimestamp(),
          }, SetOptions(merge: true));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Consentements enregistrés.')),
        );
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wapiErrorText(
                error,
                fallback: 'Les consentements n’ont pas été enregistrés.',
              ),
            ),
          ),
        );
      }
    }
  }
}

class _InboxPage extends StatefulWidget {
  const _InboxPage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;

  @override
  State<_InboxPage> createState() => _InboxPageState();
}

class _InboxPageState extends State<_InboxPage> {
  bool _showRecents = false;
  bool _searching = false;
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    // The conversation stream is live. Keeping a short refresh affordance makes
    // pull-to-refresh predictable without replacing the active Firestore stream.
    await Future<void>.delayed(const Duration(milliseconds: 280));
  }

  void _openConversation(WapiConversation conversation) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _ChatPage(
          user: widget.user,
          repository: widget.repository,
          conversation: conversation,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: _showRecents ? 'Récents' : 'Messages',
      subtitle: _showRecents ? 'Vos échanges récents' : 'Vos échanges',
      actions: [
        IconButton(
          onPressed: () => setState(() {
            _searching = !_searching;
            if (!_searching) _search.clear();
          }),
          icon: Icon(_searching ? Icons.close_rounded : Icons.search_rounded),
          tooltip: _searching ? 'Fermer la recherche' : 'Rechercher',
        ),
        IconButton(
          onPressed: () => setState(() => _showRecents = !_showRecents),
          icon: Icon(
            _showRecents ? Icons.forum_outlined : Icons.history_rounded,
          ),
          tooltip: _showRecents ? 'Discussions' : 'Afficher Récents',
        ),
        IconButton(
          onPressed: () => _createGroup(context),
          icon: const Icon(Icons.group_add_outlined),
          tooltip: 'Nouveau groupe',
        ),
      ],
    ),
    body: StreamBuilder<List<WapiConversation>>(
      stream: widget.repository.conversations(widget.user.uid),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off,
            title: 'Messages indisponibles',
            body: 'Vérifiez votre connexion. WAPI reprendra automatiquement.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final conversations = snapshot.data!;
        if (conversations.isEmpty) {
          return const _StateMessage(
            icon: Icons.forum_outlined,
            title: 'Aucune conversation',
            body: 'Ajoutez un contact pour démarrer une discussion WAPI.',
          );
        }
        final query = _search.text.trim().toLowerCase();
        final visible = query.isEmpty
            ? conversations
            : conversations
                  .where(
                    (conversation) =>
                        conversation.title.toLowerCase().contains(query) ||
                        conversation.preview.toLowerCase().contains(query),
                  )
                  .toList(growable: false);
        final content = visible.isEmpty
            ? const _StateMessage(
                icon: Icons.search_off_rounded,
                title: 'Aucun résultat',
                body: 'Essayez un autre nom ou un mot du dernier message.',
              )
            : AnimatedSwitcher(
                duration: const Duration(milliseconds: 240),
                reverseDuration: const Duration(milliseconds: 200),
                switchInCurve: Curves.easeOutCubic,
                switchOutCurve: Curves.easeInCubic,
                transitionBuilder: (child, animation) => SlideTransition(
                  position: Tween<Offset>(
                    begin: Offset(
                      _showRecents ? 0 : 0,
                      _showRecents ? -.06 : .06,
                    ),
                    end: Offset.zero,
                  ).animate(animation),
                  child: FadeTransition(opacity: animation, child: child),
                ),
                child: _showRecents
                    ? KeyedSubtree(
                        key: const ValueKey('recent-conversations'),
                        child: _recentPage(visible),
                      )
                    : KeyedSubtree(
                        key: const ValueKey('all-conversations'),
                        child: NotificationListener<OverscrollNotification>(
                          onNotification: (notification) {
                            if (notification.overscroll < -9) {
                              HapticFeedback.selectionClick();
                              setState(() => _showRecents = true);
                            }
                            return false;
                          },
                          child: RefreshIndicator(
                            onRefresh: _refresh,
                            child: _conversationList(
                              visible,
                              revealRecent: true,
                            ),
                          ),
                        ),
                      ),
              );
        return Column(
          children: [
            AnimatedSize(
              duration: const Duration(milliseconds: 180),
              curve: Curves.easeOutCubic,
              child: !_searching
                  ? const SizedBox.shrink()
                  : Padding(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 6),
                      child: TextField(
                        controller: _search,
                        autofocus: true,
                        textInputAction: TextInputAction.search,
                        onChanged: (_) => setState(() {}),
                        decoration: const InputDecoration(
                          hintText: 'Contact ou dernier message',
                          prefixIcon: Icon(Icons.search_rounded),
                        ),
                      ),
                    ),
            ),
            Expanded(child: content),
          ],
        );
      },
    ),
  );

  Widget _recentPage(List<WapiConversation> conversations) {
    final cutoff = DateTime.now().subtract(const Duration(days: 7));
    final recent = conversations
        .where(
          (item) => item.updatedAt == null || item.updatedAt!.isAfter(cutoff),
        )
        .toList();
    return NotificationListener<ScrollUpdateNotification>(
      onNotification: (notification) {
        final fingerDelta = notification.dragDetails?.primaryDelta ?? 0;
        if (fingerDelta < -7 && notification.metrics.pixels < 30) {
          HapticFeedback.selectionClick();
          setState(() => _showRecents = false);
          return true;
        }
        return false;
      },
      child: Column(
        children: [
          Container(
            width: double.infinity,
            color: WapiColors.blueSoft,
            padding: const EdgeInsets.fromLTRB(16, 11, 10, 11),
            child: Row(
              children: [
                const Icon(
                  Icons.history_rounded,
                  color: WapiColors.blue,
                  size: 20,
                ),
                const SizedBox(width: 9),
                Expanded(
                  child: Text(
                    '${recent.length} discussion${recent.length > 1 ? 's' : ''} active${recent.length > 1 ? 's' : ''} cette semaine',
                    style: const TextStyle(
                      color: WapiColors.blueDark,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                TextButton(
                  onPressed: () => setState(() => _showRecents = false),
                  child: const Text('Discussions'),
                ),
              ],
            ),
          ),
          Expanded(child: _conversationList(recent, revealRecent: false)),
        ],
      ),
    );
  }

  Widget _conversationList(
    List<WapiConversation> conversations, {
    required bool revealRecent,
  }) {
    final unread = conversations.where((item) => item.unread).length;
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(
        parent: BouncingScrollPhysics(),
      ),
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: conversations.length + (revealRecent ? 1 : 0),
      separatorBuilder: (_, index) => index == 0 && revealRecent
          ? const SizedBox(height: 0)
          : const Divider(height: 1, indent: 80),
      itemBuilder: (context, index) {
        if (revealRecent && index == 0) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 10),
            child: InkWell(
              onTap: () => setState(() => _showRecents = true),
              borderRadius: BorderRadius.circular(14),
              child: Ink(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: WapiColors.blueSoft,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.keyboard_arrow_down_rounded,
                      color: WapiColors.blue,
                    ),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text('Tirez vers le bas pour afficher Récents'),
                    ),
                    if (unread > 0)
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(99),
                        ),
                        child: Text(
                          '$unread non lu${unread > 1 ? 's' : ''}',
                          style: const TextStyle(
                            color: WapiColors.blueDark,
                            fontSize: 11,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      )
                    else
                      const Icon(Icons.history_rounded, color: WapiColors.blue),
                  ],
                ),
              ),
            ),
          );
        }
        final item = conversations[revealRecent ? index - 1 : index];
        return ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 3,
          ),
          leading: item.isGroup
              ? CircleAvatar(
                  radius: 26,
                  backgroundColor: WapiColors.blueSoft,
                  foregroundColor: WapiColors.blue,
                  backgroundImage: item.avatarUrl.isNotEmpty
                      ? NetworkImage(item.avatarUrl)
                      : null,
                  child: item.avatarUrl.isNotEmpty
                      ? null
                      : const Icon(Icons.groups_2_outlined),
                )
              : _LiveProfileAvatar(
                  userId: item.peerId,
                  fallbackUrl: item.avatarUrl,
                  name: item.title,
                  radius: 26,
                ),
          title: Text(
            item.title,
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          subtitle: Text(
            item.preview.isEmpty ? 'Aucun message' : item.preview,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              if (item.updatedAt != null)
                Text(
                  '${item.updatedAt!.hour.toString().padLeft(2, '0')}:${item.updatedAt!.minute.toString().padLeft(2, '0')}',
                  style: const TextStyle(color: WapiColors.muted, fontSize: 11),
                ),
              const SizedBox(height: 5),
              if (item.unread) const Badge(smallSize: 9),
            ],
          ),
          onTap: () => _openConversation(item),
        );
      },
    );
  }

  Future<void> _createGroup(BuildContext context) async {
    final title = TextEditingController();
    final selected = <String>{};
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(sheetContext).height * .78,
          child: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
            stream: widget.repository.profile(widget.user.uid),
            builder: (context, snapshot) {
              final contacts = _savedContacts(
                snapshot.data?.data()?['contacts'] as Map?,
              );
              return StatefulBuilder(
                builder: (context, setSheetState) => Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
                      child: TextField(
                        controller: title,
                        maxLength: 80,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(
                          labelText: 'Nom du groupe',
                        ),
                      ),
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 20),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'Ajouter des participants',
                          style: TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                    Expanded(
                      child: !snapshot.hasData
                          ? const Center(child: CircularProgressIndicator())
                          : ListView.builder(
                              itemCount: contacts.length,
                              itemBuilder: (_, index) {
                                final contact = contacts[index];
                                final name =
                                    contact['displayName'] ??
                                    contact['phoneNumber'] ??
                                    'Membre WAPI';
                                return CheckboxListTile(
                                  value: selected.contains(contact['uid']),
                                  onChanged: (value) => setSheetState(() {
                                    value == true
                                        ? selected.add(contact['uid']!)
                                        : selected.remove(contact['uid']);
                                  }),
                                  title: Text(name),
                                );
                              },
                            ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: selected.isEmpty
                              ? null
                              : () async {
                                  final members = contacts
                                      .where(
                                        (contact) =>
                                            selected.contains(contact['uid']),
                                      )
                                      .map(
                                        (contact) =>
                                            Map<String, String>.from(contact),
                                      )
                                      .toList();
                                  try {
                                    await widget.repository.createGroup(
                                      user: widget.user,
                                      title: title.text,
                                      invitedMembers: members,
                                    );
                                    if (sheetContext.mounted) {
                                      Navigator.pop(sheetContext);
                                    }
                                  } catch (error) {
                                    if (sheetContext.mounted) {
                                      ScaffoldMessenger.of(
                                        sheetContext,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            'Groupe non créé : $error',
                                          ),
                                        ),
                                      );
                                    }
                                  }
                                },
                          child: const Text('Créer le groupe'),
                        ),
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
    title.dispose();
  }
}

class _ChatPage extends StatefulWidget {
  const _ChatPage({
    required this.user,
    required this.repository,
    required this.conversation,
  });
  final User user;
  final WapiRepository repository;
  final WapiConversation conversation;
  @override
  State<_ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<_ChatPage> {
  static const _documentChannel = MethodChannel('wapi/documents');
  final _composer = TextEditingController();
  final _picker = ImagePicker();
  final _recorder = AudioRecorder();
  final _player = AudioPlayer();
  final _messageScrollController = ScrollController();
  final _composerFocusNode = FocusNode();
  StreamSubscription<List<WapiMessage>>? _messagesSubscription;
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>?
  _conversationReceiptSubscription;
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>?
  _peerPresenceSubscription;
  StreamSubscription<PlayerState>? _audioStateSubscription;
  StreamSubscription<Duration>? _audioPositionSubscription;
  StreamSubscription<Duration?>? _audioDurationSubscription;
  bool _sending = false;
  bool _recording = false;
  int _recordingSeconds = 0;
  Timer? _recordingTicker;
  Timer? _voiceOutboxRetryTimer;
  bool _showEmojiPanel = false;
  bool _markingRead = false;
  bool _markReadQueued = false;
  String? _latestIncomingMessageId;
  String? _lastRenderedMessageId;
  bool _positionedInitialMessages = false;
  WapiMessage? _replyingTo;
  String? _playingAudioMessageId;
  Duration _audioPosition = Duration.zero;
  Duration _audioDuration = Duration.zero;
  final List<WapiMessage> _pendingVoiceMessages = [];
  final Set<String> _failedVoiceMessageIds = {};
  final Set<String> _sendingVoiceMessageIds = {};
  final Set<String> _openingDocumentIds = {};
  String _sendingAttachmentName = '';
  String _chatWallpaperId = 'coffeeBlue';
  DateTime? _peerReadAt;
  bool _peerOnline = false;

  static const _composerEmojis = <String>[
    '😀',
    '😁',
    '😅',
    '😂',
    '🤣',
    '🥰',
    '😍',
    '😎',
    '🤩',
    '😘',
    '🤔',
    '🙌',
    '🤝',
    '🙏',
    '👍',
    '👎',
    '💪',
    '❤️',
    '💚',
    '💯',
    '🔥',
    '🎉',
    '👏',
    '😮',
    '😢',
    '😡',
    '😴',
    '🤗',
    '🤭',
    '✅',
    '❌',
    '⭐',
    '✨',
    '🎁',
    '🎤',
    '📷',
    '📹',
    '📍',
    '📞',
    '💸',
    '🎮',
    '💬',
    '🌍',
    '💙',
    '🚀',
    '☀️',
    '🌙',
    '🍀',
    '🎵',
    '🏆',
  ];

  @override
  void initState() {
    super.initState();
    unawaited(_loadChatWallpaper());
    unawaited(_restoreVoiceOutbox());
    _voiceOutboxRetryTimer = Timer.periodic(const Duration(seconds: 12), (_) {
      if (mounted) unawaited(_retryVoiceOutbox());
    });
    _watchMessageReceipts();
    _markConversationRead();
    _messagesSubscription = widget.repository
        .messages(widget.conversation.id)
        .listen((messages) {
          _markVisibleMessagesRead(messages);
          unawaited(_reconcileDeliveredVoiceMessages(messages));
        });
    _audioStateSubscription = _player.playerStateStream.listen((state) {
      if (!mounted || _playingAudioMessageId == null) return;
      if (state.processingState == ProcessingState.completed ||
          state.processingState == ProcessingState.idle) {
        setState(() => _playingAudioMessageId = null);
      }
    });
    _audioPositionSubscription = _player.positionStream.listen((position) {
      if (mounted && _playingAudioMessageId != null) {
        setState(() => _audioPosition = position);
      }
    });
    _audioDurationSubscription = _player.durationStream.listen((duration) {
      if (mounted && duration != null)
        setState(() => _audioDuration = duration);
    });
  }

  void _watchMessageReceipts() {
    final peerId = widget.conversation.peerId;
    if (widget.conversation.isGroup || peerId.isEmpty) return;
    _conversationReceiptSubscription = FirebaseFirestore.instance
        .collection('conversations')
        .doc(widget.conversation.id)
        .snapshots()
        .listen((snapshot) {
          final readBy = Map<String, dynamic>.from(
            snapshot.data()?['readBy'] as Map? ?? const {},
          );
          final value = readBy[peerId];
          final next = value is Timestamp
              ? value.toDate()
              : value is DateTime
              ? value
              : null;
          if (!mounted || next == _peerReadAt) return;
          setState(() => _peerReadAt = next);
        });
    _peerPresenceSubscription = widget.repository.profile(peerId).listen((doc) {
      final next = doc.data()?['isOnline'] == true;
      if (!mounted || next == _peerOnline) return;
      setState(() => _peerOnline = next);
    });
  }

  _MessageReceiptState _receiptFor(WapiMessage message) {
    final createdAt = message.createdAt;
    final readAt = _peerReadAt;
    if (createdAt != null && readAt != null && !readAt.isBefore(createdAt)) {
      return _MessageReceiptState.read;
    }
    if (_peerOnline && !message.id.startsWith('local-voice-')) {
      return _MessageReceiptState.delivered;
    }
    return _MessageReceiptState.sent;
  }

  Future<void> _loadChatWallpaper() async {
    final preferences = await SharedPreferences.getInstance();
    final stored = preferences.getString(
      'wapi.chatWallpaper.${widget.user.uid}',
    );
    if (!mounted || stored == null) return;
    if (_chatWallpaperPalettes.any((palette) => palette.id == stored)) {
      setState(() => _chatWallpaperId = stored);
    }
  }

  Future<void> _chooseChatWallpaper() async {
    final selected = await showModalBottomSheet<String>(
      context: context,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.fromLTRB(18, 4, 18, 22),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Arrière-plan de discussion',
              style: Theme.of(
                sheetContext,
              ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 4),
            const Text(
              'Choisissez une ambiance. Elle reste enregistrée sur cet appareil.',
              style: TextStyle(color: WapiColors.muted),
            ),
            const SizedBox(height: 16),
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                childAspectRatio: 1.42,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
              ),
              itemCount: _chatWallpaperPalettes.length,
              itemBuilder: (context, index) {
                final palette = _chatWallpaperPalettes[index];
                final selected = palette.id == _chatWallpaperId;
                return InkWell(
                  onTap: () => Navigator.pop(sheetContext, palette.id),
                  borderRadius: BorderRadius.circular(18),
                  child: Ink(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: palette.background,
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(
                        color: selected ? WapiColors.blue : WapiColors.line,
                        width: selected ? 2 : 1,
                      ),
                    ),
                    child: Stack(
                      children: [
                        if (palette.assetPath != null)
                          Positioned.fill(
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(17),
                              child: Image.asset(
                                palette.assetPath!,
                                fit: BoxFit.cover,
                                alignment: Alignment.bottomCenter,
                              ),
                            ),
                          ),
                        if (palette.pattern)
                          Positioned.fill(
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(17),
                              child: CustomPaint(
                                painter: _CoffeeChatWallpaperPainter(
                                  lineColor: palette.lineColor,
                                  accentColor: palette.accentColor,
                                  compact: true,
                                ),
                              ),
                            ),
                          ),
                        Positioned(
                          left: 12,
                          bottom: 10,
                          right: 10,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: palette.assetPath == null
                                  ? Colors.transparent
                                  : const Color(0xB8081720),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 5,
                              ),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      palette.label,
                                      style: TextStyle(
                                        color: palette.assetPath == null
                                            ? WapiColors.ink
                                            : Colors.white,
                                        fontWeight: FontWeight.w900,
                                      ),
                                    ),
                                  ),
                                  if (selected)
                                    const Icon(
                                      Icons.check_circle_rounded,
                                      color: WapiColors.blue,
                                      size: 19,
                                    ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
    if (!mounted || selected == null) return;
    setState(() => _chatWallpaperId = selected);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(
      'wapi.chatWallpaper.${widget.user.uid}',
      selected,
    );
  }

  void _markVisibleMessagesRead(List<WapiMessage> messages) {
    WapiMessage? latestIncoming;
    for (final message in messages.reversed) {
      if (message.senderId != widget.user.uid) {
        latestIncoming = message;
        break;
      }
    }
    if (latestIncoming == null ||
        latestIncoming.id == _latestIncomingMessageId) {
      return;
    }
    _latestIncomingMessageId = latestIncoming.id;
    unawaited(_markConversationRead());
  }

  Future<void> _reconcileDeliveredVoiceMessages(
    List<WapiMessage> messages,
  ) async {
    if (_pendingVoiceMessages.isEmpty) return;
    final committedIds = messages
        .where(
          (message) =>
              message.kind == 'audio' &&
              message.senderId == widget.user.uid &&
              (message.mediaUrl.startsWith('https://') ||
                  message.mediaUrl.startsWith('http://')),
        )
        .map((message) => message.id)
        .toSet();
    if (committedIds.isEmpty) return;
    final delivered = _pendingVoiceMessages
        .where((message) => committedIds.contains(message.id))
        .toList(growable: false);
    if (delivered.isEmpty || !mounted) return;
    final stillSending = delivered
        .where((message) => _sendingVoiceMessageIds.contains(message.id))
        .map((message) => message.id)
        .toSet();
    setState(() {
      _pendingVoiceMessages.removeWhere(
        (message) => committedIds.contains(message.id),
      );
      _failedVoiceMessageIds.removeAll(committedIds);
    });
    await _saveVoiceOutbox();
    for (final message in delivered) {
      if (stillSending.contains(message.id)) continue;
      try {
        await File(message.mediaUrl).delete();
      } catch (_) {
        // La copie serveur est déjà publiée ; ce nettoyage est opportuniste.
      }
    }
  }

  void _keepLatestMessageVisible(List<WapiMessage> messages) {
    final latestId = messages.isEmpty ? null : messages.last.id;
    if (latestId == null || latestId == _lastRenderedMessageId) return;
    final isNearBottom =
        !_messageScrollController.hasClients ||
        _messageScrollController.position.maxScrollExtent -
                _messageScrollController.offset <
            96;
    final shouldScroll = !_positionedInitialMessages || isNearBottom;
    _positionedInitialMessages = true;
    _lastRenderedMessageId = latestId;
    if (!shouldScroll) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_messageScrollController.hasClients) return;
      _messageScrollController.animateTo(
        _messageScrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
      );
    });
  }

  Future<void> _markConversationRead() async {
    if (_markingRead) {
      _markReadQueued = true;
      return;
    }
    _markingRead = true;
    try {
      await widget.repository.markConversationRead(
        conversationId: widget.conversation.id,
        userId: widget.user.uid,
      );
      await WapiNotifications.clearConversation(
        widget.conversation.id,
        widget.user.uid,
      );
    } catch (_) {
      // La conversation doit rester lisible même si le réseau est indisponible.
    } finally {
      _markingRead = false;
      if (_markReadQueued) {
        _markReadQueued = false;
        unawaited(_markConversationRead());
      }
    }
  }

  @override
  void dispose() {
    _messagesSubscription?.cancel();
    _conversationReceiptSubscription?.cancel();
    _peerPresenceSubscription?.cancel();
    _audioStateSubscription?.cancel();
    _audioPositionSubscription?.cancel();
    _audioDurationSubscription?.cancel();
    _recordingTicker?.cancel();
    _voiceOutboxRetryTimer?.cancel();
    _messageScrollController.dispose();
    _composerFocusNode.dispose();
    _composer.dispose();
    _recorder.dispose();
    _player.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    if (_sending || _composer.text.trim().isEmpty) return;
    setState(() => _sending = true);
    try {
      await widget.repository.sendMessage(
        conversationId: widget.conversation.id,
        user: widget.user,
        text: _composer.text,
        replyToId: _replyingTo?.id ?? '',
        replyText: _replyingTo?.text ?? _replyingTo?.mediaName ?? '',
      );
      _composer.clear();
      setState(() => _replyingTo = null);
    } catch (error) {
      _showSendIssue(label: 'Le message', error: error);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _pickMedia({required bool video, bool viewOnce = false}) async {
    final picked = video
        ? await _picker.pickVideo(source: ImageSource.gallery)
        : await _picker.pickImage(
            source: ImageSource.gallery,
            imageQuality: 92,
          );
    if (picked == null) return;
    setState(() => _sending = true);
    try {
      await widget.repository.sendMediaMessage(
        conversationId: widget.conversation.id,
        user: widget.user,
        file: File(picked.path),
        kind: video ? 'video' : 'image',
        contentType: video ? 'video/mp4' : 'image/jpeg',
        fileName: picked.name,
        caption: _composer.text,
        viewOnce: viewOnce,
      );
      _composer.clear();
    } catch (error) {
      _showSendIssue(label: 'Le média', error: error);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _pickDocument() async {
    final selected = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: _wapiDocumentExtensions,
    );
    final path = selected?.path;
    if (selected == null || path == null || path.isEmpty) return;

    final file = File(path);
    final sizeBytes = await file.length();
    if (sizeBytes <= 0 || sizeBytes > _wapiMaximumDocumentBytes) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Le document doit peser entre 1 octet et 50 Mo.'),
        ),
      );
      return;
    }

    setState(() {
      _sending = true;
      _sendingAttachmentName = selected.name;
    });
    try {
      await widget.repository.sendMediaMessage(
        conversationId: widget.conversation.id,
        user: widget.user,
        file: file,
        kind: 'document',
        contentType: _wapiDocumentContentType(selected.name),
        fileName: selected.name,
        caption: _composer.text,
      );
      _composer.clear();
    } catch (error) {
      _showSendIssue(label: 'Le document', error: error);
    } finally {
      if (mounted) {
        setState(() {
          _sending = false;
          _sendingAttachmentName = '';
        });
      }
    }
  }

  Future<void> _toggleRecording() async {
    if (_recording) {
      await _finishRecording();
      return;
    }
    await _startRecording();
  }

  Future<void> _startRecording() async {
    final permitted = await _recorder.hasPermission();
    if (!permitted) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Autorisez le micro pour enregistrer une note vocale.',
            ),
          ),
        );
      }
      return;
    }
    try {
      final support = await getApplicationSupportDirectory();
      final directory = Directory('${support.path}/voice-outbox');
      if (!await directory.exists()) await directory.create(recursive: true);
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 48000,
          sampleRate: 32000,
          numChannels: 1,
          autoGain: true,
          echoCancel: true,
          noiseSuppress: true,
        ),
        path:
            '${directory.path}/wapi-${DateTime.now().millisecondsSinceEpoch}.m4a',
      );
      if (!await _recorder.isRecording()) {
        throw StateError('Le microphone n’a pas démarré.');
      }
      _recordingTicker?.cancel();
      _recordingSeconds = 0;
      _recordingTicker = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted && _recording) setState(() => _recordingSeconds++);
      });
      if (mounted) setState(() => _recording = true);
      HapticFeedback.selectionClick();
    } catch (error) {
      _showSendIssue(label: 'L’enregistrement', error: error);
    }
  }

  Future<void> _cancelRecording() async {
    _recordingTicker?.cancel();
    try {
      await _recorder.cancel();
    } finally {
      if (mounted) {
        setState(() {
          _recording = false;
          _recordingSeconds = 0;
        });
      }
    }
  }

  Future<void> _finishRecording() async {
    _recordingTicker?.cancel();
    final seconds = _recordingSeconds;
    final path = await _recorder.stop();
    if (mounted) {
      setState(() {
        _recording = false;
        _recordingSeconds = 0;
      });
    }
    HapticFeedback.selectionClick();
    if (path == null) return;
    final file = File(path);
    if (!await file.exists() || await file.length() < 256) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('La note vocale est vide. Réessayez.')),
        );
      }
      return;
    }
    if (seconds < 1) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Maintenez le micro au moins une seconde.'),
          ),
        );
      }
      return;
    }
    final localMessage = WapiMessage(
      id: 'local-voice-${DateTime.now().microsecondsSinceEpoch}',
      text: 'Message vocal',
      senderId: widget.user.uid,
      createdAt: DateTime.now(),
      kind: 'audio',
      mediaUrl: file.path,
      mediaName: 'note-vocale-${DateTime.now().millisecondsSinceEpoch}.m4a',
      contentType: 'audio/mp4',
      mediaSizeBytes: await file.length(),
      mediaSha256: '',
      durationSeconds: seconds,
      replyToId: '',
      replyText: '',
      viewOnce: false,
      viewedBy: const {},
      reactions: const {},
    );
    setState(() => _pendingVoiceMessages.add(localMessage));
    await _saveVoiceOutbox();
    await _deliverVoiceMessage(localMessage);
  }

  String get _voiceOutboxKey =>
      'wapi.voiceOutbox.${widget.user.uid}.${widget.conversation.id}';

  Future<void> _saveVoiceOutbox() async {
    final preferences = await SharedPreferences.getInstance();
    final payload = _pendingVoiceMessages
        .map(
          (message) => {
            'id': message.id,
            'path': message.mediaUrl,
            'name': message.mediaName,
            'duration': message.durationSeconds,
            'createdAt': message.createdAt?.millisecondsSinceEpoch,
          },
        )
        .toList(growable: false);
    if (payload.isEmpty) {
      await preferences.remove(_voiceOutboxKey);
    } else {
      await preferences.setString(_voiceOutboxKey, jsonEncode(payload));
    }
  }

  Future<void> _restoreVoiceOutbox() async {
    final preferences = await SharedPreferences.getInstance();
    final encoded = preferences.getString(_voiceOutboxKey);
    if (encoded == null || encoded.isEmpty) return;
    final restored = <WapiMessage>[];
    try {
      final values = jsonDecode(encoded) as List<dynamic>;
      for (final raw in values.whereType<Map>()) {
        final value = Map<String, dynamic>.from(raw);
        final path = value['path']?.toString() ?? '';
        if (path.isEmpty || !await File(path).exists()) continue;
        restored.add(
          WapiMessage(
            id:
                value['id']?.toString() ??
                'local-voice-${DateTime.now().microsecondsSinceEpoch}',
            text: 'Message vocal',
            senderId: widget.user.uid,
            createdAt: DateTime.fromMillisecondsSinceEpoch(
              (value['createdAt'] as num?)?.toInt() ??
                  DateTime.now().millisecondsSinceEpoch,
            ),
            kind: 'audio',
            mediaUrl: path,
            mediaName: value['name']?.toString() ?? 'note-vocale.m4a',
            contentType: 'audio/mp4',
            mediaSizeBytes: await File(path).length(),
            mediaSha256: '',
            durationSeconds: (value['duration'] as num?)?.toInt() ?? 1,
            replyToId: '',
            replyText: '',
            viewOnce: false,
            viewedBy: const {},
            reactions: const {},
          ),
        );
      }
    } catch (_) {
      await preferences.remove(_voiceOutboxKey);
      return;
    }
    if (!mounted) return;
    setState(() => _pendingVoiceMessages.addAll(restored));
    await _saveVoiceOutbox();
    for (final message in restored) {
      if (!mounted) return;
      await _deliverVoiceMessage(message);
    }
  }

  Future<void> _retryVoiceOutbox() async {
    final retryable = _pendingVoiceMessages
        .where((message) => _failedVoiceMessageIds.contains(message.id))
        .toList(growable: false);
    for (final message in retryable) {
      if (!mounted) return;
      await _deliverVoiceMessage(message, silent: true);
    }
  }

  Future<void> _deliverVoiceMessage(
    WapiMessage localMessage, {
    bool silent = false,
  }) async {
    if (_sendingVoiceMessageIds.contains(localMessage.id)) return;
    final file = File(localMessage.mediaUrl);
    if (!await file.exists()) {
      if (mounted) {
        setState(() {
          _pendingVoiceMessages.removeWhere(
            (message) => message.id == localMessage.id,
          );
          _failedVoiceMessageIds.remove(localMessage.id);
        });
        await _saveVoiceOutbox();
        _showSendIssue(label: 'La note vocale');
      }
      return;
    }
    if (!mounted) return;
    setState(() {
      _sendingVoiceMessageIds.add(localMessage.id);
      _failedVoiceMessageIds.remove(localMessage.id);
    });
    try {
      await widget.repository.sendMediaMessage(
        conversationId: widget.conversation.id,
        user: widget.user,
        file: file,
        kind: 'audio',
        contentType: 'audio/mp4',
        fileName: localMessage.mediaName,
        durationSeconds: localMessage.durationSeconds,
        clientMessageId: localMessage.id,
      );
      void clearDeliveredVoice() {
        _pendingVoiceMessages.removeWhere(
          (message) => message.id == localMessage.id,
        );
        _sendingVoiceMessageIds.remove(localMessage.id);
        _failedVoiceMessageIds.remove(localMessage.id);
      }

      if (mounted) {
        setState(clearDeliveredVoice);
      } else {
        clearDeliveredVoice();
      }
      await _saveVoiceOutbox();
      try {
        await file.delete();
      } catch (_) {
        // Le nettoyage du brouillon ne doit jamais masquer un envoi réussi.
      }
    } catch (error) {
      final wasReconciled = !_pendingVoiceMessages.any(
        (message) => message.id == localMessage.id,
      );
      if (wasReconciled) {
        if (mounted) {
          setState(() {
            _sendingVoiceMessageIds.remove(localMessage.id);
            _failedVoiceMessageIds.remove(localMessage.id);
          });
        }
        try {
          await file.delete();
        } catch (_) {
          // Le message est déjà publié ; le brouillon sera purgé plus tard.
        }
        return;
      }
      if (mounted) {
        setState(() {
          _sendingVoiceMessageIds.remove(localMessage.id);
          _failedVoiceMessageIds.add(localMessage.id);
        });
      }
      if (!silent) _showSendIssue(label: 'La note vocale', error: error);
    }
  }

  void _showSendIssue({required String label, Object? error}) {
    if (!mounted) return;
    final detail = error?.toString().toLowerCase() ?? '';
    final message =
        detail.contains('unauthorized') || detail.contains('permission-denied')
        ? '$label est conservée dans WAPI. Touchez Réessayer : elle partira dès que la discussion sera synchronisée.'
        : '$label n’a pas encore été envoyée. Elle est conservée et WAPI réessaiera automatiquement.';
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  void _replyTo(WapiMessage message) {
    setState(() => _replyingTo = message);
    _composerFocusNode.requestFocus();
  }

  Future<void> _toggleAudio(WapiMessage message) async {
    if (message.mediaUrl.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Cette note vocale est indisponible.')),
      );
      return;
    }
    try {
      if (_playingAudioMessageId == message.id && _player.playing) {
        await _player.pause();
        if (mounted) setState(() => _playingAudioMessageId = null);
        return;
      }
      _audioPosition = Duration.zero;
      _audioDuration = Duration.zero;
      if (message.mediaUrl.startsWith('https://') ||
          message.mediaUrl.startsWith('http://')) {
        await _player.setUrl(message.mediaUrl);
      } else {
        await _player.setFilePath(message.mediaUrl);
      }
      if (!mounted) return;
      setState(() => _playingAudioMessageId = message.id);
      unawaited(_player.play());
    } catch (_) {
      if (mounted) {
        setState(() => _playingAudioMessageId = null);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Impossible de lire cette note vocale pour le moment.',
            ),
          ),
        );
      }
    }
  }

  Future<void> _openViewOnceMessage(WapiMessage message) async {
    if (message.viewedBy.containsKey(widget.user.uid)) return;
    if (message.mediaUrl.isEmpty) {
      _showSendIssue(label: 'Ce média', error: null);
      return;
    }
    if (message.kind == 'video') {
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => _WapiVideoPlayer(
            url: message.mediaUrl,
            title: 'Vidéo à vue unique',
          ),
        ),
      );
    } else {
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => Dialog.fullscreen(
          child: Stack(
            children: [
              Center(
                child: InteractiveViewer(
                  child: Image.network(message.mediaUrl, fit: BoxFit.contain),
                ),
              ),
              Positioned(
                top: 18,
                right: 18,
                child: IconButton.filled(
                  onPressed: () => Navigator.pop(dialogContext),
                  icon: const Icon(Icons.close),
                ),
              ),
            ],
          ),
        ),
      );
    }
    try {
      await widget.repository.markViewOnceSeen(
        conversationId: widget.conversation.id,
        messageId: message.id,
        userId: widget.user.uid,
      );
    } catch (_) {
      // L’affichage ne doit pas échouer si l’accusé de lecture est temporairement indisponible.
    }
  }

  Future<void> _openDocument(WapiMessage message) async {
    if (_openingDocumentIds.contains(message.id)) return;
    final uri = Uri.tryParse(message.mediaUrl);
    if (uri == null || uri.scheme != 'https') {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Ce document WAPI est indisponible.')),
      );
      return;
    }

    setState(() => _openingDocumentIds.add(message.id));
    HttpClient? client;
    File? partialFile;
    try {
      final cacheDirectory = await getTemporaryDirectory();
      final documentsDirectory = Directory(
        '${cacheDirectory.path}/wapi-documents',
      );
      await documentsDirectory.create(recursive: true);
      final localName = _wapiSafeDocumentName(
        message.mediaName.isEmpty ? 'document' : message.mediaName,
      );
      final file = File('${documentsDirectory.path}/${message.id}-$localName');
      partialFile = file;
      final cachedSize = await file.exists() ? await file.length() : 0;
      final expectedSize = message.mediaSizeBytes;
      final cacheIsValid =
          cachedSize > 0 &&
          cachedSize <= _wapiMaximumDocumentBytes &&
          (expectedSize <= 0 || cachedSize == expectedSize);

      if (!cacheIsValid) {
        if (await file.exists()) await file.delete();
        client = HttpClient()..connectionTimeout = const Duration(seconds: 20);
        final request = await client
            .getUrl(uri)
            .timeout(const Duration(seconds: 20));
        final response = await request.close().timeout(
          const Duration(seconds: 30),
        );
        if (response.statusCode != HttpStatus.ok) {
          throw HttpException(
            'Téléchargement refusé (${response.statusCode}).',
            uri: uri,
          );
        }
        final announcedSize = response.contentLength;
        if (announcedSize > _wapiMaximumDocumentBytes) {
          throw const FileSystemException('Document trop volumineux.');
        }
        final sink = file.openWrite();
        var downloadedBytes = 0;
        try {
          await for (final chunk in response.timeout(
            const Duration(seconds: 30),
          )) {
            downloadedBytes += chunk.length;
            if (downloadedBytes > _wapiMaximumDocumentBytes) {
              throw const FileSystemException('Document trop volumineux.');
            }
            sink.add(chunk);
          }
          await sink.flush();
        } finally {
          await sink.close();
        }
        if (downloadedBytes <= 0 ||
            (expectedSize > 0 && downloadedBytes != expectedSize)) {
          throw const FileSystemException('Document incomplet.');
        }
      }

      final opened = await _documentChannel.invokeMethod<bool>('openFile', {
        'path': file.path,
        'mimeType': message.contentType.isNotEmpty
            ? message.contentType
            : _wapiDocumentContentType(message.mediaName),
      });
      if (opened != true && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Installez une application compatible pour ouvrir ce document.',
            ),
          ),
        );
      }
    } catch (_) {
      try {
        if (partialFile != null && await partialFile.exists()) {
          await partialFile.delete();
        }
      } catch (_) {
        // Le nettoyage du cache ne doit pas masquer le message principal.
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Impossible d’ouvrir ce document. Vérifiez la connexion puis réessayez.',
            ),
          ),
        );
      }
    } finally {
      client?.close(force: true);
      if (mounted) setState(() => _openingDocumentIds.remove(message.id));
    }
  }

  String _formatMessageTime(DateTime? date) {
    if (date == null) return '';
    return '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }

  void _toggleEmojiPanel() {
    final showing = !_showEmojiPanel;
    setState(() => _showEmojiPanel = showing);
    if (showing) {
      _composerFocusNode.unfocus();
    } else {
      _composerFocusNode.requestFocus();
    }
  }

  void _insertEmoji(String emoji) {
    final selection = _composer.selection;
    final start = selection.isValid ? selection.start : _composer.text.length;
    final end = selection.isValid ? selection.end : _composer.text.length;
    final next = _composer.text.replaceRange(start, end, emoji);
    _composer.value = TextEditingValue(
      text: next,
      selection: TextSelection.collapsed(offset: start + emoji.length),
    );
    setState(() {});
  }

  void _openMemberProfile({
    required String userId,
    required String fallbackName,
    required String fallbackPhotoUrl,
  }) {
    if (userId.isEmpty) return;
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _ContactProfilePage(
          user: widget.user,
          repository: widget.repository,
          profileId: userId,
          fallbackName: fallbackName,
          fallbackPhotoUrl: fallbackPhotoUrl,
        ),
      ),
    );
  }

  Future<void> _messageActions(WapiMessage message) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Wrap(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
              child: Text(
                message.text.isEmpty ? 'Message média' : message.text,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: ['❤️', '👍', '😂', '😮', '🙏']
                    .map(
                      (emoji) => IconButton(
                        onPressed: () => Navigator.pop(sheetContext, emoji),
                        icon: Text(emoji, style: const TextStyle(fontSize: 24)),
                      ),
                    )
                    .toList(),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.reply_outlined),
              title: const Text('Répondre'),
              onTap: () => Navigator.pop(sheetContext, 'reply'),
            ),
            ListTile(
              leading: const Icon(Icons.forward_outlined),
              title: const Text('Transférer'),
              onTap: () => Navigator.pop(sheetContext, 'forward'),
            ),
          ],
        ),
      ),
    );
    if (!mounted || action == null) return;
    if (const {'❤️', '👍', '😂', '😮', '🙏'}.contains(action)) {
      try {
        await widget.repository.reactToMessage(
          conversationId: widget.conversation.id,
          messageId: message.id,
          userId: widget.user.uid,
          emoji: action,
        );
      } catch (error) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                wapiErrorText(
                  error,
                  fallback: 'La réaction n’a pas été enregistrée.',
                ),
              ),
            ),
          );
        }
      }
      return;
    }
    if (action == 'reply') {
      _replyTo(message);
      return;
    }
    if (action == 'forward') {
      await _forwardMessage(message);
    }
  }

  Future<void> _forwardMessage(WapiMessage message) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(sheetContext).height * .62,
          child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
            stream: FirebaseFirestore.instance
                .collection('users')
                .limit(100)
                .snapshots(),
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              final contacts = snapshot.data!.docs
                  .where((doc) => doc.id != widget.user.uid)
                  .toList();
              return Column(
                children: [
                  const Padding(
                    padding: EdgeInsets.fromLTRB(20, 18, 20, 10),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Transférer à',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ),
                  Expanded(
                    child: contacts.isEmpty
                        ? const _StateMessage(
                            icon: Icons.person_add_alt_1_outlined,
                            title: 'Aucun contact disponible',
                            body:
                                'Ajoutez un contact WAPI pour transférer ce message.',
                          )
                        : ListView.builder(
                            itemCount: contacts.length,
                            itemBuilder: (_, index) {
                              final contact = contacts[index];
                              final data = contact.data();
                              final name = (data['displayName'] as String?)
                                  ?.trim();
                              final displayName = name?.isNotEmpty == true
                                  ? name!
                                  : 'Membre WAPI';
                              return ListTile(
                                leading: CircleAvatar(
                                  backgroundImage:
                                      (data['photoUrl'] as String?)
                                              ?.isNotEmpty ==
                                          true
                                      ? NetworkImage(data['photoUrl'] as String)
                                      : null,
                                  child:
                                      (data['photoUrl'] as String?)
                                              ?.isNotEmpty ==
                                          true
                                      ? null
                                      : Text(
                                          displayName
                                              .substring(0, 1)
                                              .toUpperCase(),
                                        ),
                                ),
                                title: Text(displayName),
                                onTap: () async {
                                  try {
                                    final conversationId = await widget
                                        .repository
                                        .ensureDirectConversation(
                                          user: widget.user,
                                          peerId: contact.id,
                                          peerName: displayName,
                                          peerPhone:
                                              (data['phoneNumber']
                                                  as String?) ??
                                              '',
                                          peerPhotoUrl:
                                              (data['photoUrl'] as String?) ??
                                              '',
                                        );
                                    await widget.repository.forwardMessage(
                                      conversationId: conversationId,
                                      user: widget.user,
                                      source: message,
                                    );
                                    if (sheetContext.mounted) {
                                      Navigator.pop(sheetContext);
                                    }
                                    if (mounted) {
                                      ScaffoldMessenger.of(
                                        this.context,
                                      ).showSnackBar(
                                        const SnackBar(
                                          content: Text('Message transféré.'),
                                        ),
                                      );
                                    }
                                  } catch (error) {
                                    if (sheetContext.mounted) {
                                      ScaffoldMessenger.of(
                                        sheetContext,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            'Transfert impossible : $error',
                                          ),
                                        ),
                                      );
                                    }
                                  }
                                },
                              );
                            },
                          ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: InkWell(
        onTap: widget.conversation.isGroup
            ? _editGroup
            : () => _openMemberProfile(
                userId: widget.conversation.peerId,
                fallbackName: widget.conversation.title,
                fallbackPhotoUrl: widget.conversation.avatarUrl,
              ),
        borderRadius: BorderRadius.circular(28),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            children: [
              widget.conversation.isGroup
                  ? CircleAvatar(
                      radius: 19,
                      backgroundColor: WapiColors.blueSoft,
                      backgroundImage: widget.conversation.avatarUrl.isNotEmpty
                          ? NetworkImage(widget.conversation.avatarUrl)
                          : null,
                      child: widget.conversation.avatarUrl.isNotEmpty
                          ? null
                          : const Icon(
                              Icons.groups_2_outlined,
                              color: WapiColors.blue,
                            ),
                    )
                  : _LiveProfileAvatar(
                      userId: widget.conversation.peerId,
                      fallbackUrl: widget.conversation.avatarUrl,
                      name: widget.conversation.title,
                      radius: 19,
                    ),
              const SizedBox(width: 10),
              Expanded(child: _chatTitle()),
            ],
          ),
        ),
      ),
      actions: [
        IconButton(
          onPressed: _chooseChatWallpaper,
          icon: const Icon(Icons.wallpaper_rounded),
          tooltip: 'Personnaliser le fond',
        ),
        if (widget.conversation.isGroup)
          IconButton(
            onPressed: _editGroup,
            icon: const Icon(Icons.info_outline),
            tooltip: 'Informations du groupe',
          )
        else ...[
          IconButton(
            onPressed: () => _startCall(video: false),
            icon: const Icon(Icons.call_outlined),
            tooltip: 'Appel audio',
          ),
          IconButton(
            onPressed: () => _startCall(video: true),
            icon: const Icon(Icons.videocam_outlined),
            tooltip: 'Appel vidéo',
          ),
        ],
      ],
    ),
    body: Column(
      children: [
        Expanded(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: _chatWallpaperPalette(_chatWallpaperId).background,
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
            ),
            child: Stack(
              fit: StackFit.expand,
              children: [
                if (_chatWallpaperPalette(_chatWallpaperId).assetPath != null)
                  Positioned.fill(
                    child: IgnorePointer(
                      child: Image.asset(
                        _chatWallpaperPalette(_chatWallpaperId).assetPath!,
                        fit: BoxFit.cover,
                        alignment: Alignment.bottomCenter,
                      ),
                    ),
                  ),
                if (_chatWallpaperPalette(_chatWallpaperId).assetPath != null)
                  const Positioned.fill(
                    child: IgnorePointer(
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [Color(0x54020A0F), Color(0x23020A0F)],
                          ),
                        ),
                      ),
                    ),
                  ),
                if (_chatWallpaperPalette(_chatWallpaperId).pattern)
                  Positioned.fill(
                    child: IgnorePointer(
                      child: CustomPaint(
                        painter: _CoffeeChatWallpaperPainter(
                          lineColor: _chatWallpaperPalette(
                            _chatWallpaperId,
                          ).lineColor,
                          accentColor: _chatWallpaperPalette(
                            _chatWallpaperId,
                          ).accentColor,
                        ),
                      ),
                    ),
                  ),
                StreamBuilder<List<WapiMessage>>(
                  stream: widget.repository.messages(widget.conversation.id),
                  builder: (context, snapshot) {
                    if (snapshot.hasError && _pendingVoiceMessages.isEmpty) {
                      return const _StateMessage(
                        icon: Icons.cloud_off,
                        title: 'Conversation indisponible',
                        body: 'WAPI ne peut pas charger ces messages.',
                      );
                    }
                    if (!snapshot.hasData && _pendingVoiceMessages.isEmpty) {
                      return const Center(child: CircularProgressIndicator());
                    }
                    final messagesById = <String, WapiMessage>{
                      for (final message in _pendingVoiceMessages)
                        message.id: message,
                      for (final message
                          in snapshot.data ?? const <WapiMessage>[])
                        message.id: message,
                    };
                    final messages = messagesById.values.toList()
                      ..sort((left, right) {
                        final leftTime =
                            left.createdAt?.millisecondsSinceEpoch ?? 0;
                        final rightTime =
                            right.createdAt?.millisecondsSinceEpoch ?? 0;
                        final byTime = leftTime.compareTo(rightTime);
                        return byTime != 0
                            ? byTime
                            : left.id.compareTo(right.id);
                      });
                    _keepLatestMessageVisible(messages);
                    if (messages.isEmpty) {
                      return const _StateMessage(
                        icon: Icons.waving_hand_outlined,
                        title: 'Dites bonjour',
                        body: 'Le premier message apparaîtra ici.',
                      );
                    }
                    return ListView.builder(
                      controller: _messageScrollController,
                      physics: const BouncingScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
                      itemCount: messages.length,
                      itemBuilder: (context, index) {
                        final message = messages[index];
                        final mine = message.senderId == widget.user.uid;
                        final normalizedMessageText = message.text
                            .trim()
                            .toLowerCase();
                        final automaticDocumentLabel =
                            message.kind == 'document' &&
                            (normalizedMessageText == 'document' ||
                                normalizedMessageText.startsWith(
                                  'document · ',
                                ) ||
                                normalizedMessageText ==
                                    message.mediaName.toLowerCase());
                        final showMessageText =
                            message.kind == 'text' ||
                            (message.text.isNotEmpty &&
                                !automaticDocumentLabel &&
                                !const {
                                  'photo',
                                  'vidéo',
                                  'video',
                                  'message vocal',
                                  'note vocale',
                                  'vocal',
                                }.contains(normalizedMessageText));
                        final previous = index == 0
                            ? null
                            : messages[index - 1];
                        final showDay = !_sameChatDay(
                          previous?.createdAt,
                          message.createdAt,
                        );
                        return Column(
                          children: [
                            if (showDay)
                              _ChatDayDivider(date: message.createdAt),
                            Align(
                              alignment: mine
                                  ? Alignment.centerRight
                                  : Alignment.centerLeft,
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (!mine) ...[
                                    InkWell(
                                      onTap: () => _openMemberProfile(
                                        userId: message.senderId,
                                        fallbackName:
                                            widget
                                                .conversation
                                                .memberNames[message
                                                .senderId] ??
                                            widget.conversation.title,
                                        fallbackPhotoUrl:
                                            widget
                                                .conversation
                                                .memberPhotoUrls[message
                                                .senderId] ??
                                            '',
                                      ),
                                      borderRadius: BorderRadius.circular(20),
                                      child: _LiveProfileAvatar(
                                        userId: message.senderId,
                                        fallbackUrl:
                                            widget
                                                .conversation
                                                .memberPhotoUrls[message
                                                .senderId] ??
                                            '',
                                        name:
                                            widget
                                                .conversation
                                                .memberNames[message
                                                .senderId] ??
                                            widget.conversation.title,
                                        radius: 17,
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                  ],
                                  InkWell(
                                    onLongPress: () => _messageActions(message),
                                    onDoubleTap: () => _replyTo(message),
                                    borderRadius: BorderRadius.circular(20),
                                    child: Container(
                                      constraints: BoxConstraints(
                                        maxWidth: mine ? 320 : 276,
                                      ),
                                      margin: const EdgeInsets.only(bottom: 8),
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 13,
                                        vertical: 10,
                                      ),
                                      decoration: BoxDecoration(
                                        gradient: mine
                                            ? const LinearGradient(
                                                colors: [
                                                  WapiColors.blue,
                                                  WapiColors.blueDark,
                                                ],
                                                begin: Alignment.topLeft,
                                                end: Alignment.bottomRight,
                                              )
                                            : null,
                                        color: mine ? null : Colors.white,
                                        borderRadius: BorderRadius.only(
                                          topLeft: const Radius.circular(20),
                                          topRight: const Radius.circular(20),
                                          bottomLeft: Radius.circular(
                                            mine ? 20 : 6,
                                          ),
                                          bottomRight: Radius.circular(
                                            mine ? 6 : 20,
                                          ),
                                        ),
                                        border: mine
                                            ? null
                                            : Border.all(
                                                color: WapiColors.line,
                                              ),
                                        boxShadow: [
                                          BoxShadow(
                                            color: Colors.black.withValues(
                                              alpha: .045,
                                            ),
                                            blurRadius: 10,
                                            offset: const Offset(0, 3),
                                          ),
                                        ],
                                      ),
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          if (!mine &&
                                              widget.conversation.isGroup) ...[
                                            Text(
                                              widget
                                                      .conversation
                                                      .memberNames[message
                                                      .senderId] ??
                                                  'Membre WAPI',
                                              style: const TextStyle(
                                                color: WapiColors.muted,
                                                fontSize: 11,
                                                fontWeight: FontWeight.w700,
                                              ),
                                            ),
                                            const SizedBox(height: 4),
                                          ],
                                          if (message.replyText.isNotEmpty)
                                            Container(
                                              width: double.infinity,
                                              margin: const EdgeInsets.only(
                                                bottom: 8,
                                              ),
                                              padding: const EdgeInsets.all(8),
                                              decoration: BoxDecoration(
                                                color: mine
                                                    ? Colors.white.withValues(
                                                        alpha: .16,
                                                      )
                                                    : WapiColors.blueSoft,
                                                borderRadius:
                                                    BorderRadius.circular(10),
                                              ),
                                              child: Text(
                                                message.replyText,
                                                maxLines: 2,
                                                overflow: TextOverflow.ellipsis,
                                                style: TextStyle(
                                                  color: mine
                                                      ? Colors.white
                                                      : WapiColors.ink,
                                                  fontSize: 12,
                                                ),
                                              ),
                                            ),
                                          if (message.viewOnce)
                                            InkWell(
                                              onTap:
                                                  message.viewedBy.containsKey(
                                                    widget.user.uid,
                                                  )
                                                  ? null
                                                  : () => _openViewOnceMessage(
                                                      message,
                                                    ),
                                              borderRadius:
                                                  BorderRadius.circular(12),
                                              child: Container(
                                                padding:
                                                    const EdgeInsets.symmetric(
                                                      horizontal: 12,
                                                      vertical: 10,
                                                    ),
                                                decoration: BoxDecoration(
                                                  color: mine
                                                      ? Colors.white.withValues(
                                                          alpha: .14,
                                                        )
                                                      : WapiColors.blueSoft,
                                                  borderRadius:
                                                      BorderRadius.circular(12),
                                                ),
                                                child: Row(
                                                  mainAxisSize:
                                                      MainAxisSize.min,
                                                  children: [
                                                    Icon(
                                                      message.viewedBy
                                                              .containsKey(
                                                                widget.user.uid,
                                                              )
                                                          ? Icons
                                                                .visibility_off_outlined
                                                          : Icons
                                                                .remove_red_eye_outlined,
                                                      color: mine
                                                          ? Colors.white
                                                          : WapiColors.blue,
                                                    ),
                                                    const SizedBox(width: 8),
                                                    Text(
                                                      message.viewedBy
                                                              .containsKey(
                                                                widget.user.uid,
                                                              )
                                                          ? 'Média ouvert'
                                                          : 'Photo à vue unique',
                                                      style: TextStyle(
                                                        color: mine
                                                            ? Colors.white
                                                            : WapiColors.ink,
                                                        fontWeight:
                                                            FontWeight.w700,
                                                      ),
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            )
                                          else if (message.kind == 'image' &&
                                              message.mediaUrl.isNotEmpty)
                                            ClipRRect(
                                              borderRadius:
                                                  BorderRadius.circular(12),
                                              child: Image.network(
                                                message.mediaUrl,
                                                width: 250,
                                                fit: BoxFit.cover,
                                              ),
                                            ),
                                          if (!message.viewOnce &&
                                              message.kind == 'video')
                                            InkWell(
                                              onTap: () =>
                                                  Navigator.of(context).push(
                                                    MaterialPageRoute(
                                                      builder: (_) =>
                                                          _WapiVideoPlayer(
                                                            url: message
                                                                .mediaUrl,
                                                            title:
                                                                message
                                                                    .mediaName
                                                                    .isEmpty
                                                                ? 'Vidéo WAPI'
                                                                : message
                                                                      .mediaName,
                                                          ),
                                                    ),
                                                  ),
                                              borderRadius:
                                                  BorderRadius.circular(12),
                                              child: Row(
                                                mainAxisSize: MainAxisSize.min,
                                                children: [
                                                  const Icon(
                                                    Icons.play_circle_fill,
                                                    color: Colors.white,
                                                  ),
                                                  const SizedBox(width: 8),
                                                  Flexible(
                                                    child: Text(
                                                      message.mediaName.isEmpty
                                                          ? 'Lire la vidéo'
                                                          : message.mediaName,
                                                      style: const TextStyle(
                                                        color: Colors.white,
                                                      ),
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          if (message.kind == 'audio')
                                            _VoiceNotePlayer(
                                              mine: mine,
                                              playing:
                                                  _playingAudioMessageId ==
                                                  message.id,
                                              durationSeconds:
                                                  _playingAudioMessageId ==
                                                          message.id &&
                                                      _audioDuration.inSeconds >
                                                          0
                                                  ? _audioDuration.inSeconds
                                                  : message.durationSeconds,
                                              progress:
                                                  _playingAudioMessageId ==
                                                          message.id &&
                                                      _audioDuration
                                                              .inMilliseconds >
                                                          0
                                                  ? _audioPosition
                                                            .inMilliseconds /
                                                        _audioDuration
                                                            .inMilliseconds
                                                  : 0,
                                              // The immutable local id is also
                                              // kept by the server so retries
                                              // cannot create duplicates.  An
                                              // id prefix therefore does not
                                              // mean that an upload is still
                                              // pending once Firestore returns
                                              // the committed message.
                                              sending: _pendingVoiceMessages
                                                  .any(
                                                    (pending) =>
                                                        pending.id ==
                                                        message.id,
                                                  ),
                                              failed: _failedVoiceMessageIds
                                                  .contains(message.id),
                                              onPressed:
                                                  _failedVoiceMessageIds
                                                      .contains(message.id)
                                                  ? () => _deliverVoiceMessage(
                                                      message,
                                                    )
                                                  : () => _toggleAudio(message),
                                              onSeek:
                                                  _playingAudioMessageId ==
                                                          message.id &&
                                                      _audioDuration
                                                              .inMilliseconds >
                                                          0
                                                  ? (progress) => _player.seek(
                                                      Duration(
                                                        milliseconds:
                                                            (_audioDuration
                                                                        .inMilliseconds *
                                                                    progress)
                                                                .round(),
                                                      ),
                                                    )
                                                  : null,
                                            ),
                                          if (message.kind == 'document')
                                            _DocumentMessageCard(
                                              mine: mine,
                                              name: message.mediaName,
                                              sizeBytes: message.mediaSizeBytes,
                                              contentType: message.contentType,
                                              opening: _openingDocumentIds
                                                  .contains(message.id),
                                              onPressed: () =>
                                                  _openDocument(message),
                                            ),
                                          if (showMessageText) ...[
                                            if (message.kind != 'text')
                                              const SizedBox(height: 8),
                                            Text(
                                              message.text,
                                              style: TextStyle(
                                                color: mine
                                                    ? Colors.white
                                                    : WapiColors.ink,
                                              ),
                                            ),
                                          ],
                                          if (message.reactions.isNotEmpty) ...[
                                            const SizedBox(height: 7),
                                            Wrap(
                                              spacing: 4,
                                              children: message.reactions.values
                                                  .toSet()
                                                  .map(
                                                    (emoji) => DecoratedBox(
                                                      decoration: BoxDecoration(
                                                        color: mine
                                                            ? Colors.white
                                                                  .withValues(
                                                                    alpha: .18,
                                                                  )
                                                            : WapiColors
                                                                  .blueSoft,
                                                        borderRadius:
                                                            BorderRadius.circular(
                                                              10,
                                                            ),
                                                      ),
                                                      child: Padding(
                                                        padding:
                                                            const EdgeInsets.symmetric(
                                                              horizontal: 6,
                                                              vertical: 3,
                                                            ),
                                                        child: Text(
                                                          emoji,
                                                          style:
                                                              const TextStyle(
                                                                fontSize: 12,
                                                              ),
                                                        ),
                                                      ),
                                                    ),
                                                  )
                                                  .toList(),
                                            ),
                                          ],
                                          const SizedBox(height: 4),
                                          Align(
                                            alignment: Alignment.centerRight,
                                            child: Row(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                Text(
                                                  _formatMessageTime(
                                                    message.createdAt,
                                                  ),
                                                  style: TextStyle(
                                                    color: mine
                                                        ? Colors.white
                                                              .withValues(
                                                                alpha: .72,
                                                              )
                                                        : WapiColors.muted,
                                                    fontSize: 10,
                                                    fontWeight: FontWeight.w600,
                                                  ),
                                                ),
                                                if (mine) ...[
                                                  const SizedBox(width: 3),
                                                  _MessageReceipt(
                                                    state: _receiptFor(message),
                                                  ),
                                                ],
                                              ],
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        );
                      },
                    );
                  },
                ),
              ],
            ),
          ),
        ),
        if (_showEmojiPanel) _emojiPanel(),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_replyingTo != null)
                  Container(
                    width: double.infinity,
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.fromLTRB(12, 8, 6, 8),
                    decoration: BoxDecoration(
                      color: WapiColors.blueSoft,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.reply,
                          color: WapiColors.blue,
                          size: 18,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _replyingTo!.text.isEmpty
                                ? 'Message média'
                                : _replyingTo!.text,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        IconButton(
                          onPressed: () => setState(() => _replyingTo = null),
                          icon: const Icon(Icons.close, size: 18),
                        ),
                      ],
                    ),
                  ),
                if (_sendingAttachmentName.isNotEmpty)
                  Container(
                    width: double.infinity,
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 10,
                    ),
                    decoration: BoxDecoration(
                      color: WapiColors.blueSoft,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      children: [
                        const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                        const SizedBox(width: 10),
                        const Icon(
                          Icons.description_outlined,
                          color: WapiColors.blueDark,
                          size: 20,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Envoi de $_sendingAttachmentName…',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: WapiColors.blueDark,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                if (_recording)
                  Container(
                    width: double.infinity,
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 9,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFEEF0),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.fiber_manual_record_rounded,
                          color: Color(0xFFD92D3A),
                          size: 17,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Enregistrement  ${_recordingSeconds ~/ 60}:${(_recordingSeconds % 60).toString().padLeft(2, '0')}',
                            style: const TextStyle(
                              color: Color(0xFF9E1B2B),
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        TextButton(
                          onPressed: _cancelRecording,
                          child: const Text('Annuler'),
                        ),
                      ],
                    ),
                  ),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    IconButton(
                      onPressed: _sending
                          ? null
                          : () => showModalBottomSheet<void>(
                              context: context,
                              builder: (sheetContext) => SafeArea(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    ListTile(
                                      leading: const Icon(Icons.photo_outlined),
                                      title: const Text('Photo'),
                                      onTap: () {
                                        Navigator.pop(sheetContext);
                                        _pickMedia(video: false);
                                      },
                                    ),
                                    ListTile(
                                      leading: const Icon(
                                        Icons.visibility_off_outlined,
                                      ),
                                      title: const Text('Photo à vue unique'),
                                      subtitle: const Text(
                                        'Elle disparaît après ouverture.',
                                      ),
                                      onTap: () {
                                        Navigator.pop(sheetContext);
                                        _pickMedia(
                                          video: false,
                                          viewOnce: true,
                                        );
                                      },
                                    ),
                                    ListTile(
                                      leading: const Icon(
                                        Icons.videocam_outlined,
                                      ),
                                      title: const Text('Vidéo'),
                                      onTap: () {
                                        Navigator.pop(sheetContext);
                                        _pickMedia(video: true);
                                      },
                                    ),
                                    ListTile(
                                      leading: const Icon(
                                        Icons.description_outlined,
                                      ),
                                      title: const Text('Document'),
                                      subtitle: const Text(
                                        'PDF, Word, Excel, texte ou archive · 50 Mo max',
                                      ),
                                      onTap: () {
                                        Navigator.pop(sheetContext);
                                        _pickDocument();
                                      },
                                    ),
                                  ],
                                ),
                              ),
                            ),
                      icon: const Icon(Icons.add_rounded),
                      style: IconButton.styleFrom(
                        backgroundColor: WapiColors.blueSoft,
                        foregroundColor: WapiColors.blueDark,
                      ),
                      tooltip: 'Joindre',
                    ),
                    const SizedBox(width: 7),
                    Expanded(
                      child: TextField(
                        controller: _composer,
                        focusNode: _composerFocusNode,
                        minLines: 1,
                        maxLines: 5,
                        textInputAction: TextInputAction.send,
                        onSubmitted: (_) => _send(),
                        onTap: () {
                          if (_showEmojiPanel) {
                            setState(() => _showEmojiPanel = false);
                          }
                        },
                        decoration: InputDecoration(
                          hintText: 'Message',
                          prefixIcon: IconButton(
                            onPressed: _sending ? null : _toggleEmojiPanel,
                            icon: Icon(
                              _showEmojiPanel
                                  ? Icons.keyboard_alt_outlined
                                  : Icons.emoji_emotions_outlined,
                            ),
                            color: WapiColors.blueDark,
                            tooltip: _showEmojiPanel ? 'Clavier' : 'Emoji',
                          ),
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 11,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 7),
                    ValueListenableBuilder<TextEditingValue>(
                      valueListenable: _composer,
                      builder: (context, value, _) {
                        final hasText = value.text.trim().isNotEmpty;
                        return IconButton.filled(
                          onPressed: _sending
                              ? null
                              : hasText
                              ? _send
                              : _toggleRecording,
                          icon: _sending
                              ? const SizedBox.square(
                                  dimension: 19,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : Icon(
                                  hasText
                                      ? Icons.send_rounded
                                      : _recording
                                      ? Icons.stop_rounded
                                      : Icons.mic_rounded,
                                ),
                          style: IconButton.styleFrom(
                            backgroundColor: _recording
                                ? const Color(0xFFD92D3A)
                                : WapiColors.blue,
                            foregroundColor: Colors.white,
                            disabledBackgroundColor: WapiColors.blueSoft,
                          ),
                          tooltip: hasText
                              ? 'Envoyer'
                              : _recording
                              ? 'Arrêter et envoyer'
                              : 'Note vocale',
                        );
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );

  Widget _chatTitle() {
    if (widget.conversation.isGroup || widget.conversation.peerId.isEmpty) {
      return Text(widget.conversation.title);
    }
    return StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: widget.repository.profile(widget.conversation.peerId),
      builder: (context, snapshot) {
        final data = snapshot.data?.data() ?? const <String, dynamic>{};
        final online = data['isOnline'] == true;
        final verified = data['verified'] == true;
        final liveName = (data['displayName'] as String?)?.trim();
        final seen = data['lastSeenAt'] as Timestamp?;
        final label = online
            ? 'en ligne'
            : seen == null
            ? 'hors ligne'
            : 'vu ${_formatLastSeen(seen.toDate())}';
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Flexible(
                  child: Text(
                    liveName?.isNotEmpty == true
                        ? liveName!
                        : widget.conversation.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (verified) ...[
                  const SizedBox(width: 4),
                  const Icon(
                    Icons.verified_rounded,
                    color: WapiColors.blue,
                    size: 16,
                  ),
                ],
              ],
            ),
            Text(
              label,
              style: TextStyle(
                color: online ? WapiColors.blue : WapiColors.muted,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _emojiPanel() => Container(
    height: 244,
    width: double.infinity,
    decoration: const BoxDecoration(
      color: Colors.white,
      border: Border(top: BorderSide(color: WapiColors.line)),
    ),
    child: GridView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      itemCount: _composerEmojis.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 9,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
      ),
      itemBuilder: (context, index) {
        final emoji = _composerEmojis[index];
        return Semantics(
          button: true,
          label: 'Ajouter $emoji',
          child: InkWell(
            onTap: () => _insertEmoji(emoji),
            borderRadius: BorderRadius.circular(12),
            child: Center(
              child: Text(emoji, style: const TextStyle(fontSize: 25)),
            ),
          ),
        );
      },
    ),
  );

  void _startCall({required bool video}) {
    if (widget.conversation.peerId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Ce contact doit avoir un compte WAPI actif pour recevoir un appel.',
          ),
        ),
      );
      return;
    }
    openWapiCall(
      context,
      WapiCallPage.outgoing(
        user: widget.user,
        peerId: widget.conversation.peerId,
        peerName: widget.conversation.title,
        peerPhotoUrl: widget.conversation.avatarUrl,
        video: video,
      ),
    );
  }

  Future<void> _editGroup() async {
    final groupSnapshot = await FirebaseFirestore.instance
        .collection('conversations')
        .doc(widget.conversation.id)
        .get();
    if (!mounted || !groupSnapshot.exists) return;
    final group = groupSnapshot.data() ?? const <String, dynamic>{};
    final owner = group['ownerId'] == widget.user.uid;
    final members = List<Map<String, dynamic>>.from(
      (group['members'] as List? ?? const []).whereType<Map>().map(
        (member) => Map<String, dynamic>.from(member),
      ),
    );
    final title = TextEditingController(text: widget.conversation.title);
    final existingPhotoUrl = (group['groupPhotoUrl'] as String?) ?? '';
    File? photo;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheetState) => SafeArea(
          child: SizedBox(
            height: MediaQuery.sizeOf(sheetContext).height * .84,
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                20,
                20,
                20,
                MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
              ),
              child: Column(
                children: [
                  Text(
                    'Informations du groupe',
                    style: Theme.of(sheetContext).textTheme.headlineSmall
                        ?.copyWith(fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 16),
                  InkWell(
                    onTap: () async {
                      final image = await _picker.pickImage(
                        source: ImageSource.gallery,
                        imageQuality: 92,
                      );
                      if (image != null) {
                        setSheetState(() => photo = File(image.path));
                      }
                    },
                    borderRadius: BorderRadius.circular(42),
                    child: CircleAvatar(
                      radius: 40,
                      backgroundColor: WapiColors.blueSoft,
                      backgroundImage: photo != null
                          ? FileImage(photo!)
                          : existingPhotoUrl.isNotEmpty
                          ? NetworkImage(existingPhotoUrl)
                          : null,
                      child: photo == null && existingPhotoUrl.isEmpty
                          ? const Icon(
                              Icons.add_a_photo_outlined,
                              color: WapiColors.blue,
                            )
                          : null,
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: title,
                    maxLength: 80,
                    decoration: const InputDecoration(
                      labelText: 'Nom du groupe',
                    ),
                  ),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${members.length} membre${members.length > 1 ? 's' : ''}',
                          style: const TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                      if (owner)
                        TextButton.icon(
                          onPressed: () async {
                            final selected = await _selectGroupMembers(
                              members
                                  .map((member) => member['uid'].toString())
                                  .toSet(),
                            );
                            if (selected.isEmpty) return;
                            try {
                              await widget.repository.manageGroupMembers(
                                conversationId: widget.conversation.id,
                                action: 'add',
                                memberIds: selected
                                    .map((member) => member['uid']!)
                                    .toList(),
                              );
                              if (sheetContext.mounted) {
                                setSheetState(() => members.addAll(selected));
                              }
                            } catch (error) {
                              if (sheetContext.mounted) {
                                ScaffoldMessenger.of(sheetContext).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      wapiErrorText(
                                        error,
                                        fallback:
                                            'Le membre n’a pas pu être ajouté.',
                                      ),
                                    ),
                                  ),
                                );
                              }
                            }
                          },
                          icon: const Icon(Icons.person_add_alt_1_outlined),
                          label: const Text('Ajouter'),
                        ),
                    ],
                  ),
                  Expanded(
                    child: ListView.builder(
                      itemCount: members.length,
                      itemBuilder: (context, index) {
                        final member = members[index];
                        final uid = member['uid']?.toString() ?? '';
                        final name =
                            member['displayName']?.toString() ?? 'Membre WAPI';
                        final photoUrl = member['photoUrl']?.toString() ?? '';
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: CircleAvatar(
                            backgroundColor: WapiColors.blueSoft,
                            backgroundImage: photoUrl.isEmpty
                                ? null
                                : NetworkImage(photoUrl),
                            child: photoUrl.isEmpty
                                ? Text(name.substring(0, 1).toUpperCase())
                                : null,
                          ),
                          title: Text(name),
                          subtitle: Text(
                            uid == group['ownerId'] ? 'Propriétaire' : 'Membre',
                          ),
                          trailing: owner && uid != widget.user.uid
                              ? IconButton(
                                  onPressed: () async {
                                    try {
                                      await widget.repository
                                          .manageGroupMembers(
                                            conversationId:
                                                widget.conversation.id,
                                            action: 'remove',
                                            memberIds: [uid],
                                          );
                                      if (sheetContext.mounted) {
                                        setSheetState(
                                          () => members.removeAt(index),
                                        );
                                      }
                                    } catch (error) {
                                      if (sheetContext.mounted) {
                                        ScaffoldMessenger.of(
                                          sheetContext,
                                        ).showSnackBar(
                                          SnackBar(
                                            content: Text(
                                              'Retrait impossible : $error',
                                            ),
                                          ),
                                        );
                                      }
                                    }
                                  },
                                  icon: const Icon(
                                    Icons.person_remove_outlined,
                                  ),
                                  tooltip: 'Retirer du groupe',
                                )
                              : null,
                        );
                      },
                    ),
                  ),
                  Align(
                    alignment: Alignment.centerRight,
                    child: FilledButton(
                      onPressed: () async {
                        try {
                          await widget.repository.updateGroupDetails(
                            user: widget.user,
                            conversationId: widget.conversation.id,
                            title: title.text,
                            photo: photo,
                          );
                          if (sheetContext.mounted) Navigator.pop(sheetContext);
                        } catch (error) {
                          if (sheetContext.mounted) {
                            ScaffoldMessenger.of(sheetContext).showSnackBar(
                              SnackBar(
                                content: Text(
                                  'Modification impossible : $error',
                                ),
                              ),
                            );
                          }
                        }
                      },
                      child: const Text('Enregistrer'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    title.dispose();
  }

  Future<List<Map<String, String>>> _selectGroupMembers(
    Set<String> existingIds,
  ) async {
    final selected = <String>{};
    final result = await showModalBottomSheet<List<Map<String, String>>>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(sheetContext).height * .72,
          child: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
            stream: widget.repository.profile(widget.user.uid),
            builder: (context, snapshot) {
              final contacts =
                  _savedContacts(snapshot.data?.data()?['contacts'] as Map?)
                      .where((contact) => !existingIds.contains(contact['uid']))
                      .toList();
              return StatefulBuilder(
                builder: (context, setSheetState) => Column(
                  children: [
                    const Padding(
                      padding: EdgeInsets.fromLTRB(20, 20, 20, 10),
                      child: Text(
                        'Ajouter des membres',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    Expanded(
                      child: !snapshot.hasData
                          ? const Center(child: CircularProgressIndicator())
                          : contacts.isEmpty
                          ? const _StateMessage(
                              icon: Icons.people_outline,
                              title: 'Aucun nouveau contact',
                              body: 'Ajoutez d’abord des contacts WAPI.',
                            )
                          : ListView.builder(
                              itemCount: contacts.length,
                              itemBuilder: (context, index) {
                                final contact = contacts[index];
                                final name =
                                    contact['displayName'] ??
                                    contact['phoneNumber'] ??
                                    'Membre WAPI';
                                return CheckboxListTile(
                                  value: selected.contains(contact['uid']),
                                  onChanged: (checked) => setSheetState(() {
                                    checked == true
                                        ? selected.add(contact['uid']!)
                                        : selected.remove(contact['uid']);
                                  }),
                                  title: Text(name),
                                );
                              },
                            ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: selected.isEmpty
                              ? null
                              : () => Navigator.of(sheetContext).pop(
                                  contacts
                                      .where(
                                        (contact) =>
                                            selected.contains(contact['uid']),
                                      )
                                      .map(
                                        (contact) =>
                                            Map<String, String>.from(contact),
                                      )
                                      .toList(),
                                ),
                          child: const Text('Ajouter au groupe'),
                        ),
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
    return result ?? const <Map<String, String>>[];
  }

  String _formatLastSeen(DateTime date) {
    final local = date.toLocal();
    final now = DateTime.now();
    if (now.difference(local).inDays == 0) {
      return 'aujourd’hui à ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
    }
    return 'le ${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}';
  }
}

bool _sameChatDay(DateTime? left, DateTime? right) {
  if (left == null || right == null) return left == right;
  final a = left.toLocal();
  final b = right.toLocal();
  return a.year == b.year && a.month == b.month && a.day == b.day;
}

class _ChatWallpaperPalette {
  const _ChatWallpaperPalette({
    required this.id,
    required this.label,
    required this.background,
    required this.lineColor,
    required this.accentColor,
    this.assetPath,
    this.pattern = true,
  });

  final String id;
  final String label;
  final List<Color> background;
  final Color lineColor;
  final Color accentColor;
  final String? assetPath;
  final bool pattern;
}

const _chatWallpaperPalettes = <_ChatWallpaperPalette>[
  _ChatWallpaperPalette(
    id: 'coffeeBlue',
    label: 'Café bleu',
    background: [Color(0xFFF8FCFF), Color(0xFFEEF5F8)],
    lineColor: Color(0xFF0B5872),
    accentColor: Color(0xFF9A6A42),
  ),
  _ChatWallpaperPalette(
    id: 'coffeeSignature',
    label: 'Café signature',
    background: [Color(0xFF06131C), Color(0xFF102A3B)],
    lineColor: Color(0xFFE0B37B),
    accentColor: Color(0xFFFFD69D),
    assetPath: 'assets/wallpapers/wapi_cafe_premium.png',
    pattern: false,
  ),
  _ChatWallpaperPalette(
    id: 'latte',
    label: 'Latte crème',
    background: [Color(0xFFFFFCF7), Color(0xFFF2E7D9)],
    lineColor: Color(0xFF75482D),
    accentColor: Color(0xFFB77942),
  ),
  _ChatWallpaperPalette(
    id: 'mintCoffee',
    label: 'Menthe moka',
    background: [Color(0xFFF5FCF9), Color(0xFFE5F3EE)],
    lineColor: Color(0xFF11695E),
    accentColor: Color(0xFF8B5E3C),
  ),
  _ChatWallpaperPalette(
    id: 'clean',
    label: 'Minimal',
    background: [Color(0xFFFAFCFE), Color(0xFFF1F4F7)],
    lineColor: Color(0xFF526571),
    accentColor: Color(0xFF78909C),
    pattern: false,
  ),
];

_ChatWallpaperPalette _chatWallpaperPalette(String id) =>
    _chatWallpaperPalettes.firstWhere(
      (palette) => palette.id == id,
      orElse: () => _chatWallpaperPalettes.first,
    );

class _CoffeeChatWallpaperPainter extends CustomPainter {
  const _CoffeeChatWallpaperPainter({
    required this.lineColor,
    required this.accentColor,
    this.compact = false,
  });

  final Color lineColor;
  final Color accentColor;
  final bool compact;

  @override
  void paint(Canvas canvas, Size size) {
    final line = Paint()
      ..color = lineColor.withValues(alpha: compact ? .11 : .065)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.35
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final accent = Paint()
      ..color = accentColor.withValues(alpha: compact ? .1 : .055)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2
      ..strokeCap = StrokeCap.round;

    var row = 0;
    final rowStep = compact ? 72.0 : 92.0;
    final columnStep = compact ? 86.0 : 108.0;
    for (double y = -36; y < size.height + 70; y += rowStep) {
      final offset = row.isOdd ? columnStep / 2 : 0.0;
      var column = 0;
      for (double x = -34 + offset; x < size.width + 70; x += columnStep) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate((row + column).isEven ? -.075 : .075);
        canvas.scale(.86);

        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTWH(4, 13, 30, 21),
            const Radius.circular(6),
          ),
          line,
        );
        canvas.drawArc(
          const Rect.fromLTWH(5.5, 10.5, 27, 7),
          .08,
          3.0,
          false,
          accent,
        );
        canvas.drawArc(
          const Rect.fromLTWH(28, 17, 15, 13),
          -1.25,
          2.5,
          false,
          line,
        );
        canvas.drawArc(
          const Rect.fromLTWH(1, 30, 38, 9),
          .18,
          2.78,
          false,
          line,
        );

        final firstSteam = Path()
          ..moveTo(13, 9)
          ..cubicTo(9, 5, 17, 3, 13, -1);
        final secondSteam = Path()
          ..moveTo(24, 9)
          ..cubicTo(20, 5, 28, 3, 24, -1);
        final centerSteam = Path()
          ..moveTo(18.5, 8)
          ..cubicTo(23, 4, 15, 1, 19, -4);
        canvas.drawPath(firstSteam, accent);
        canvas.drawPath(secondSteam, accent);
        canvas.drawPath(centerSteam, line);

        final bean = Path()
          ..moveTo(52, 17)
          ..cubicTo(59, 10, 68, 16, 65, 24)
          ..cubicTo(62, 32, 51, 30, 50, 23)
          ..cubicTo(50, 20, 51, 18, 52, 17)
          ..close();
        canvas.drawPath(bean, accent);
        final beanLine = Path()
          ..moveTo(53, 27)
          ..cubicTo(57, 24, 58, 18, 64, 15);
        canvas.drawPath(beanLine, accent);
        canvas.restore();
        column++;
      }
      row++;
    }
  }

  @override
  bool shouldRepaint(covariant _CoffeeChatWallpaperPainter oldDelegate) =>
      lineColor != oldDelegate.lineColor ||
      accentColor != oldDelegate.accentColor ||
      compact != oldDelegate.compact;
}

class _ChatDayDivider extends StatelessWidget {
  const _ChatDayDivider({required this.date});
  final DateTime? date;

  String get _label {
    final value = date?.toLocal();
    if (value == null) return 'Aujourd’hui';
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final day = DateTime(value.year, value.month, value.day);
    if (day == today) return 'Aujourd’hui';
    if (day == today.subtract(const Duration(days: 1))) return 'Hier';
    const months = <String>[
      'janvier',
      'février',
      'mars',
      'avril',
      'mai',
      'juin',
      'juillet',
      'août',
      'septembre',
      'octobre',
      'novembre',
      'décembre',
    ];
    return '${value.day} ${months[value.month - 1]}${value.year == now.year ? '' : ' ${value.year}'}';
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 10),
    child: DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: .92),
        borderRadius: BorderRadius.circular(99),
        border: Border.all(color: WapiColors.line),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
        child: Text(
          _label,
          style: const TextStyle(
            color: WapiColors.muted,
            fontSize: 11,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    ),
  );
}

const _wapiMaximumDocumentBytes = 50 * 1024 * 1024;
const _wapiDocumentExtensions = <String>[
  'pdf',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'txt',
  'csv',
  'rtf',
  'md',
  'json',
  'xml',
  'odt',
  'ods',
  'odp',
  'zip',
  'rar',
  '7z',
  'gz',
];

String _wapiDocumentExtension(String name) {
  final clean = name.trim().toLowerCase();
  final separator = clean.lastIndexOf('.');
  return separator < 0 || separator == clean.length - 1
      ? ''
      : clean.substring(separator + 1);
}

String _wapiDocumentContentType(String name) => switch (_wapiDocumentExtension(
  name,
)) {
  'pdf' => 'application/pdf',
  'doc' => 'application/msword',
  'docx' =>
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'xls' => 'application/vnd.ms-excel',
  'xlsx' => 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'ppt' => 'application/vnd.ms-powerpoint',
  'pptx' =>
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'csv' => 'text/csv',
  'rtf' => 'application/rtf',
  'md' => 'text/markdown',
  'json' => 'application/json',
  'xml' => 'text/xml',
  'odt' => 'application/vnd.oasis.opendocument.text',
  'ods' => 'application/vnd.oasis.opendocument.spreadsheet',
  'odp' => 'application/vnd.oasis.opendocument.presentation',
  'zip' => 'application/zip',
  'rar' => 'application/vnd.rar',
  '7z' => 'application/x-7z-compressed',
  'gz' => 'application/gzip',
  _ => 'text/plain',
};

String _wapiSafeDocumentName(String name) {
  final cleaned = name
      .trim()
      .replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_')
      .replaceAll(RegExp(r'_+'), '_');
  if (cleaned.isEmpty) return 'document';
  return cleaned.length <= 100 ? cleaned : cleaned.substring(0, 100);
}

String _formatWapiDocumentSize(int bytes) {
  if (bytes <= 0) return 'Document WAPI';
  if (bytes < 1024) return '$bytes o';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} Ko';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} Mo';
}

class _DocumentMessageCard extends StatelessWidget {
  const _DocumentMessageCard({
    required this.mine,
    required this.name,
    required this.sizeBytes,
    required this.contentType,
    required this.opening,
    required this.onPressed,
  });

  final bool mine;
  final String name;
  final int sizeBytes;
  final String contentType;
  final bool opening;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final extension = _wapiDocumentExtension(name).toUpperCase();
    final (icon, accent) = switch (_wapiDocumentExtension(name)) {
      'pdf' => (Icons.picture_as_pdf_rounded, const Color(0xFFE84A5F)),
      'xls' ||
      'xlsx' ||
      'csv' => (Icons.table_chart_rounded, const Color(0xFF1E9D6C)),
      'doc' ||
      'docx' ||
      'odt' => (Icons.article_rounded, const Color(0xFF2387D9)),
      'ppt' ||
      'pptx' ||
      'odp' => (Icons.slideshow_rounded, const Color(0xFFF17A36)),
      'zip' ||
      'rar' ||
      '7z' ||
      'gz' => (Icons.folder_zip_rounded, const Color(0xFFE4A11B)),
      _ => (Icons.description_rounded, const Color(0xFF5C72D8)),
    };
    final foreground = mine ? Colors.white : WapiColors.ink;
    final safeName = name.trim().isEmpty ? 'Document WAPI' : name.trim();
    final typeLabel = extension.isNotEmpty
        ? extension
        : contentType.split('/').last.toUpperCase();

    return InkWell(
      onTap: opening ? null : onPressed,
      borderRadius: BorderRadius.circular(15),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 230, maxWidth: 270),
        child: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: mine
                ? Colors.white.withValues(alpha: .14)
                : const Color(0xFFF3F7FA),
            borderRadius: BorderRadius.circular(15),
            border: Border.all(
              color: mine
                  ? Colors.white.withValues(alpha: .2)
                  : WapiColors.line,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 46,
                height: 50,
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: mine ? .28 : .12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  icon,
                  color: mine ? Colors.white : accent,
                  size: 27,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      safeName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: foreground,
                        fontSize: 13,
                        fontWeight: FontWeight.w800,
                        height: 1.15,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '$typeLabel · ${_formatWapiDocumentSize(sizeBytes)}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: mine
                            ? Colors.white.withValues(alpha: .72)
                            : WapiColors.muted,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 7),
              if (opening)
                SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: mine ? Colors.white : WapiColors.blue,
                  ),
                )
              else
                Icon(
                  Icons.download_for_offline_outlined,
                  color: mine ? Colors.white : WapiColors.blue,
                  size: 25,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _VoiceNotePlayer extends StatelessWidget {
  const _VoiceNotePlayer({
    required this.mine,
    required this.playing,
    required this.durationSeconds,
    required this.progress,
    required this.sending,
    required this.failed,
    required this.onPressed,
    required this.onSeek,
  });

  final bool mine;
  final bool playing;
  final int durationSeconds;
  final double progress;
  final bool sending;
  final bool failed;
  final VoidCallback onPressed;
  final ValueChanged<double>? onSeek;

  String _clock(int seconds) {
    final safe = seconds.clamp(0, 35999);
    final minutes = safe ~/ 60;
    return '$minutes:${(safe % 60).toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final foreground = mine ? Colors.white : WapiColors.blueDark;
    final muted = mine ? Colors.white.withValues(alpha: .72) : WapiColors.muted;
    final safeProgress = progress.clamp(0.0, 1.0);
    final elapsed = (durationSeconds * safeProgress).round();
    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(16),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 224, maxWidth: 252),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 2),
          child: Row(
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: mine
                      ? Colors.white.withValues(alpha: playing ? .28 : .18)
                      : playing
                      ? WapiColors.blue
                      : WapiColors.blueSoft,
                  boxShadow: playing
                      ? [
                          BoxShadow(
                            color: WapiColors.blue.withValues(alpha: .22),
                            blurRadius: 12,
                            spreadRadius: 1,
                          ),
                        ]
                      : const [],
                ),
                child: Icon(
                  playing ? Icons.pause_rounded : Icons.play_arrow_rounded,
                  color: mine || playing ? Colors.white : WapiColors.blue,
                  size: 28,
                ),
              ),
              const SizedBox(width: 11),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.mic_rounded, size: 13, color: foreground),
                        const SizedBox(width: 4),
                        Text(
                          'VOCAL WAPI',
                          style: TextStyle(
                            color: foreground,
                            fontSize: 10,
                            fontWeight: FontWeight.w900,
                            letterSpacing: .55,
                          ),
                        ),
                        if (playing) ...[
                          const Spacer(),
                          Container(
                            width: 6,
                            height: 6,
                            decoration: BoxDecoration(
                              color: mine ? Colors.white : WapiColors.emerald,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 6),
                    _VoiceWaveform(
                      color: foreground,
                      progress: safeProgress,
                      onSeek: onSeek,
                    ),
                    const SizedBox(height: 5),
                    Row(
                      children: [
                        Text(
                          playing
                              ? '${_clock(elapsed)} / ${_clock(durationSeconds)}'
                              : _clock(durationSeconds),
                          style: TextStyle(
                            color: muted,
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            fontFeatures: const [FontFeature.tabularFigures()],
                          ),
                        ),
                        if (sending) ...[
                          const Spacer(),
                          if (failed)
                            Icon(
                              Icons.refresh_rounded,
                              size: 13,
                              color: foreground,
                            )
                          else
                            SizedBox.square(
                              dimension: 10,
                              child: CircularProgressIndicator(
                                strokeWidth: 1.5,
                                color: foreground,
                              ),
                            ),
                          const SizedBox(width: 4),
                          Text(
                            failed ? 'Réessayer' : 'Envoi…',
                            style: TextStyle(
                              color: foreground,
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ],
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
}

class _VoiceWaveform extends StatelessWidget {
  const _VoiceWaveform({
    required this.color,
    required this.progress,
    required this.onSeek,
  });

  final Color color;
  final double progress;
  final ValueChanged<double>? onSeek;

  @override
  Widget build(BuildContext context) {
    const bars = <double>[
      8,
      13,
      18,
      11,
      22,
      16,
      9,
      14,
      21,
      12,
      17,
      24,
      15,
      9,
      19,
      13,
      22,
      11,
      17,
      8,
      14,
      20,
      12,
      16,
    ];
    return SizedBox(
      height: 26,
      child: LayoutBuilder(
        builder: (context, constraints) {
          void seek(Offset position) {
            if (onSeek == null || constraints.maxWidth <= 0) return;
            onSeek!((position.dx / constraints.maxWidth).clamp(0.0, 1.0));
          }

          final gap = 2.0;
          final width =
              ((constraints.maxWidth - gap * (bars.length - 1)) / bars.length)
                  .clamp(1.5, 3.5);
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTapDown: onSeek == null
                ? null
                : (details) => seek(details.localPosition),
            onHorizontalDragUpdate: onSeek == null
                ? null
                : (details) => seek(details.localPosition),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: bars.indexed.map((entry) {
                final played = entry.$1 / (bars.length - 1) <= progress;
                return Container(
                  width: width,
                  height: entry.$2,
                  margin: EdgeInsets.only(
                    right: entry.$1 == bars.length - 1 ? 0 : gap,
                  ),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: played ? .96 : .3),
                    borderRadius: BorderRadius.circular(99),
                  ),
                );
              }).toList(),
            ),
          );
        },
      ),
    );
  }
}

class _ContactProfilePage extends StatelessWidget {
  const _ContactProfilePage({
    required this.user,
    required this.repository,
    required this.profileId,
    required this.fallbackName,
    required this.fallbackPhotoUrl,
  });

  final User user;
  final WapiRepository repository;
  final String profileId;
  final String fallbackName;
  final String fallbackPhotoUrl;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Profil',
      subtitle: 'Informations du contact',
    ),
    body: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: repository.profile(profileId),
      builder: (context, snapshot) {
        final data = snapshot.data?.data() ?? const <String, dynamic>{};
        final profileName = (data['displayName'] as String?)?.trim();
        final name = profileName?.isNotEmpty == true
            ? profileName!
            : fallbackName.isNotEmpty
            ? fallbackName
            : 'Membre WAPI';
        final profilePhoto = (data['photoUrl'] as String?)?.trim() ?? '';
        final photoUrl = profilePhoto.isNotEmpty
            ? profilePhoto
            : fallbackPhotoUrl;
        final phone = (data['phoneNumber'] as String?)?.trim() ?? '';
        final bio =
            (data['bio'] as String?)?.trim() ??
            (data['statusText'] as String?)?.trim() ??
            '';
        final verified = data['verified'] == true;
        final online = data['isOnline'] == true;
        return ListView(
          padding: const EdgeInsets.fromLTRB(20, 24, 20, 32),
          children: [
            Center(
              child: InkWell(
                onTap: photoUrl.isEmpty
                    ? null
                    : () => _showPhoto(context, photoUrl, name),
                borderRadius: BorderRadius.circular(58),
                child: CircleAvatar(
                  radius: 56,
                  backgroundColor: WapiColors.blueSoft,
                  backgroundImage: photoUrl.isEmpty
                      ? null
                      : NetworkImage(photoUrl),
                  child: photoUrl.isEmpty
                      ? Text(
                          name.substring(0, 1).toUpperCase(),
                          style: const TextStyle(
                            color: WapiColors.blue,
                            fontSize: 34,
                            fontWeight: FontWeight.w800,
                          ),
                        )
                      : null,
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Flexible(
                  child: Text(
                    name,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                if (verified) ...[
                  const SizedBox(width: 6),
                  const Icon(
                    Icons.verified_rounded,
                    size: 21,
                    color: WapiColors.blue,
                  ),
                ],
              ],
            ),
            const SizedBox(height: 5),
            Text(
              online ? 'En ligne' : 'Compte WAPI',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: online ? WapiColors.blue : WapiColors.muted,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.chat_bubble_outline),
                    label: const Text('Message'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () => _startCall(context, name, video: false),
                    icon: const Icon(Icons.call_outlined),
                    label: const Text('Appeler'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Card(
              child: Column(
                children: [
                  if (phone.isNotEmpty)
                    ListTile(
                      leading: const Icon(Icons.phone_outlined),
                      title: const Text('Téléphone'),
                      subtitle: Text(phone),
                    ),
                  ListTile(
                    leading: const Icon(Icons.info_outline),
                    title: const Text('À propos'),
                    subtitle: Text(
                      bio.isNotEmpty ? bio : 'Ce contact utilise WAPI.',
                    ),
                  ),
                  ListTile(
                    leading: const Icon(Icons.videocam_outlined),
                    title: const Text('Appel vidéo'),
                    subtitle: const Text('Démarrer un appel vidéo WAPI'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _startCall(context, name, video: true),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    ),
  );

  void _startCall(BuildContext context, String name, {required bool video}) {
    openWapiCall(
      context,
      WapiCallPage.outgoing(
        user: user,
        peerId: profileId,
        peerName: name,
        video: video,
      ),
    );
  }

  void _showPhoto(BuildContext context, String photoUrl, String name) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => Dialog.fullscreen(
        child: Stack(
          fit: StackFit.expand,
          children: [
            ColoredBox(
              color: Colors.black,
              child: InteractiveViewer(
                child: Center(
                  child: Image.network(photoUrl, fit: BoxFit.contain),
                ),
              ),
            ),
            SafeArea(
              child: Align(
                alignment: Alignment.topLeft,
                child: IconButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  icon: const Icon(Icons.close, color: Colors.white),
                  tooltip: 'Fermer la photo de $name',
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _UpdatesPage extends StatelessWidget {
  const _UpdatesPage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: 'Actus',
      subtitle: 'Statuts, créateurs et tendances',
      actions: [
        IconButton(
          onPressed: () => _composeStory(context),
          icon: const Icon(Icons.add_circle_outline),
          tooltip: 'Publier un statut',
        ),
      ],
    ),
    body: StreamBuilder<List<WapiStory>>(
      stream: repository.stories(user.uid),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Actus indisponibles',
            body: 'Vérifiez votre connexion puis réessayez.',
          );
        }
        if (!snapshot.hasData) {
          return const _StoriesLoadingState();
        }
        final stories = snapshot.data!;
        final latestByAuthor = <String, WapiStory>{};
        for (final story in stories) {
          latestByAuthor.putIfAbsent(story.authorId, () => story);
        }
        final storyRail = latestByAuthor.values.toList(growable: false);
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
          children: [
            _StoryPublisher(
              photoUrl: user.photoURL ?? '',
              onTap: () => _composeStory(context),
            ),
            if (storyRail.isNotEmpty) ...[
              const SizedBox(height: 20),
              SizedBox(
                height: 88,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: storyRail.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 10),
                  itemBuilder: (context, index) {
                    final story = storyRail[index];
                    return _StoryRailAvatar(
                      label: story.authorId == user.uid
                          ? 'Mon statut'
                          : story.authorName,
                      photoUrl: story.authorPhotoUrl,
                      viewed: story.viewedByCurrentUser,
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => _StoryViewer(
                            story: story,
                            user: user,
                            repository: repository,
                            stories: stories,
                            initialIndex: stories.indexOf(story),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
            const SizedBox(height: 18),
            Text(
              stories.isEmpty
                  ? 'Statuts de vos contacts'
                  : 'À regarder maintenant',
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            if (stories.isEmpty)
              const _StateMessage(
                icon: Icons.auto_awesome_outlined,
                title: 'Aucun statut à voir',
                body:
                    'Les statuts publiés par vos contacts apparaîtront ici pendant 24 heures.',
              )
            else
              ...stories.map(
                (story) => Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: WapiColors.line),
                  ),
                  child: ListTile(
                    contentPadding: const EdgeInsets.fromLTRB(10, 5, 8, 5),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => _StoryViewer(
                          story: story,
                          user: user,
                          repository: repository,
                          stories: stories,
                          initialIndex: stories.indexOf(story),
                        ),
                      ),
                    ),
                    leading: _StoryThumbnail(story: story),
                    title: Text(
                      story.authorName,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    subtitle: Text(
                      story.text.isEmpty ? 'Photo ou vidéo' : story.text,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          _storyTimeLabel(story.createdAt),
                          style: const TextStyle(
                            color: WapiColors.muted,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 3),
                        const Icon(Icons.chevron_right),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    ),
  );
  Future<void> _composeStory(BuildContext context) async {
    final media = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 92,
    );
    if (media == null || !context.mounted) return;
    await _showStoryEditor(context, media: media, video: false);
  }

  Future<void> _showStoryEditor(
    BuildContext context, {
    required XFile media,
    required bool video,
  }) async {
    final caption = TextEditingController();
    var selectedMedia = media;
    var selectedVideo = video;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      builder: (sheetContext) => StatefulBuilder(
        builder: (sheetContext, setSheetState) => Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
          ),
          child: SizedBox(
            height: MediaQuery.sizeOf(sheetContext).height * .88,
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(18, 12, 10, 8),
                  child: Row(
                    children: [
                      IconButton(
                        onPressed: () => Navigator.pop(sheetContext),
                        icon: const Icon(Icons.close),
                        tooltip: 'Annuler',
                      ),
                      const SizedBox(width: 4),
                      const Expanded(
                        child: Text(
                          'Votre Story',
                          style: TextStyle(
                            fontSize: 19,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      TextButton.icon(
                        onPressed: () async {
                          final picked = await ImagePicker().pickImage(
                            source: ImageSource.gallery,
                            imageQuality: 92,
                          );
                          if (picked != null) {
                            setSheetState(() {
                              selectedMedia = picked;
                              selectedVideo = false;
                            });
                          }
                        },
                        icon: const Icon(Icons.photo_outlined, size: 19),
                        label: const Text('Photo'),
                      ),
                      TextButton.icon(
                        onPressed: () async {
                          final picked = await ImagePicker().pickVideo(
                            source: ImageSource.gallery,
                          );
                          if (picked != null) {
                            setSheetState(() {
                              selectedMedia = picked;
                              selectedVideo = true;
                            });
                          }
                        },
                        icon: const Icon(Icons.videocam_outlined, size: 19),
                        label: const Text('Vidéo'),
                      ),
                    ],
                  ),
                ),
                Container(height: 1, color: WapiColors.line),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                  child: TextField(
                    controller: caption,
                    maxLength: 600,
                    minLines: 1,
                    maxLines: 3,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(
                      hintText: 'Ajouter du texte à votre Story…',
                      border: InputBorder.none,
                      counterText: '',
                    ),
                  ),
                ),
                Expanded(
                  child: Container(
                    width: double.infinity,
                    margin: const EdgeInsets.fromLTRB(14, 4, 14, 10),
                    clipBehavior: Clip.antiAlias,
                    decoration: BoxDecoration(
                      color: const Color(0xFF0B1724),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: selectedVideo
                        ? const Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.play_circle_fill_rounded,
                                color: Colors.white,
                                size: 58,
                              ),
                              SizedBox(height: 10),
                              Text(
                                'Vidéo sélectionnée',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          )
                        : Image.file(
                            File(selectedMedia.path),
                            fit: BoxFit.contain,
                          ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
                  child: SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: () {
                        Navigator.pop(sheetContext);
                        _publishStoryMedia(
                          context,
                          caption.text,
                          media: selectedMedia,
                          video: selectedVideo,
                        );
                      },
                      icon: const Icon(Icons.send_rounded),
                      label: const Text('Publier la Story'),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    caption.dispose();
  }

  Future<void> _publishStoryMedia(
    BuildContext context,
    String caption, {
    required XFile media,
    required bool video,
  }) async {
    final file = File(media.path);
    final bytes = await file.length();
    final maximumBytes = video ? 50 * 1024 * 1024 : 12 * 1024 * 1024;
    if (bytes <= 0 || bytes > maximumBytes) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              video
                  ? 'Cette vidéo dépasse la limite de 50 Mo pour une Story.'
                  : 'Cette photo dépasse la limite de 12 Mo pour une Story.',
            ),
          ),
        );
      }
      return;
    }
    if (!context.mounted) return;
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );
    try {
      final story = await repository.publishMediaStory(
        user: user,
        file: file,
        mediaType: video ? 'video' : 'image',
        contentType: media.mimeType?.trim().isNotEmpty == true
            ? media.mimeType!.trim()
            : video
            ? 'video/mp4'
            : 'image/jpeg',
        fileName: media.name,
        caption: caption,
      );
      if (context.mounted) Navigator.of(context, rootNavigator: true).pop();
      if (context.mounted) {
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) =>
                _StoryViewer(story: story, user: user, repository: repository),
          ),
        );
      }
    } catch (error) {
      if (context.mounted) Navigator.of(context, rootNavigator: true).pop();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'La Story n’a pas pu être publiée. Vérifiez votre connexion puis réessayez.',
            ),
          ),
        );
      }
    }
  }
}

class _StoryRailAvatar extends StatelessWidget {
  const _StoryRailAvatar({
    required this.label,
    required this.photoUrl,
    required this.onTap,
    this.viewed = false,
  });

  final String label;
  final String photoUrl;
  final VoidCallback onTap;
  final bool viewed;

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: 'Voir le statut de $label',
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(40),
      child: SizedBox(
        width: 62,
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(3),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: viewed
                      ? [WapiColors.line, WapiColors.line]
                      : const [
                          Color(0xFF00C2FF),
                          Color(0xFF7567FF),
                          Color(0xFFFF5CA8),
                        ],
                ),
              ),
              child: CircleAvatar(
                radius: 24,
                backgroundColor: WapiColors.blueSoft,
                child: ClipOval(
                  child: photoUrl.isEmpty
                      ? const Icon(
                          Icons.person_outline,
                          color: WapiColors.muted,
                        )
                      : _StableNetworkAvatarImage(
                          url: photoUrl,
                          size: 48,
                          fallback: const Icon(
                            Icons.person_outline,
                            color: WapiColors.muted,
                          ),
                        ),
                ),
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700),
            ),
          ],
        ),
      ),
    ),
  );
}

class _WapiVideoPlayer extends StatefulWidget {
  const _WapiVideoPlayer({
    super.key,
    required this.url,
    required this.title,
    this.embedded = false,
    this.paused = false,
    this.onProgress,
    this.onEnded,
  });
  final String url;
  final String title;
  final bool embedded;
  final bool paused;
  final ValueChanged<double>? onProgress;
  final VoidCallback? onEnded;

  @override
  State<_WapiVideoPlayer> createState() => _WapiVideoPlayerState();
}

class _WapiVideoPlayerState extends State<_WapiVideoPlayer> {
  late final VideoPlayerController _controller;
  late final Future<void> _ready;
  bool _ended = false;

  @override
  void initState() {
    super.initState();
    _controller = VideoPlayerController.networkUrl(Uri.parse(widget.url));
    _ready = _controller.initialize().then((_) {
      _controller.setLooping(false);
      _controller.addListener(_onPlaybackChanged);
      if (!widget.paused) _controller.play();
    });
  }

  @override
  void didUpdateWidget(covariant _WapiVideoPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.paused == oldWidget.paused || !_controller.value.isInitialized) {
      return;
    }
    widget.paused ? _controller.pause() : _controller.play();
  }

  void _onPlaybackChanged() {
    final duration = _controller.value.duration.inMilliseconds;
    if (duration <= 0) return;
    final position = _controller.value.position.inMilliseconds;
    widget.onProgress?.call((position / duration).clamp(0, 1));
    if (!_ended && position >= duration - 120) {
      _ended = true;
      widget.onEnded?.call();
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onPlaybackChanged);
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final player = FutureBuilder<void>(
      future: _ready,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StoryFallback(
            icon: Icons.error_outline,
            label: 'Lecture vidéo impossible',
          );
        }
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(
            child: CircularProgressIndicator(color: Colors.white),
          );
        }
        final size = _controller.value.size;
        final video = widget.embedded
            ? ColoredBox(
                color: Colors.black,
                child: Center(
                  child: FittedBox(
                    fit: BoxFit.contain,
                    child: SizedBox(
                      width: size.width,
                      height: size.height,
                      child: VideoPlayer(_controller),
                    ),
                  ),
                ),
              )
            : Center(
                child: AspectRatio(
                  aspectRatio: _controller.value.aspectRatio,
                  child: VideoPlayer(_controller),
                ),
              );
        return Stack(
          fit: StackFit.expand,
          alignment: Alignment.center,
          children: [
            video,
            Center(
              child: IconButton.filledTonal(
                onPressed: () => setState(() {
                  _controller.value.isPlaying
                      ? _controller.pause()
                      : _controller.play();
                }),
                icon: Icon(
                  _controller.value.isPlaying
                      ? Icons.pause_rounded
                      : Icons.play_arrow_rounded,
                ),
              ),
            ),
          ],
        );
      },
    );
    if (widget.embedded) return player;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(widget.title, maxLines: 1, overflow: TextOverflow.ellipsis),
      ),
      body: player,
    );
  }
}

class _StoryViewer extends StatefulWidget {
  const _StoryViewer({
    required this.story,
    required this.user,
    required this.repository,
    this.stories = const [],
    this.initialIndex = 0,
  });

  final WapiStory story;
  final User user;
  final WapiRepository repository;
  final List<WapiStory> stories;
  final int initialIndex;

  @override
  State<_StoryViewer> createState() => _StoryViewerState();
}

class _StoryViewerState extends State<_StoryViewer> {
  late final List<WapiStory> _stories;
  late int _currentIndex;
  WapiStory get story => _stories[_currentIndex];
  bool get _isAuthor => story.authorId == widget.user.uid;
  Timer? _progressTimer;
  double _progress = 0;
  double _videoProgress = 0;
  bool _paused = false;

  String get _publishedAt => _storyTimeLabel(story.createdAt);

  @override
  void initState() {
    super.initState();
    _stories = widget.stories.isEmpty ? [widget.story] : widget.stories;
    _currentIndex = widget.initialIndex.clamp(0, _stories.length - 1);
    _activateStory();
  }

  void _activateStory() {
    _progressTimer?.cancel();
    _progress = 0;
    _videoProgress = 0;
    if (!_isAuthor && !story.viewedByCurrentUser && story.id.isNotEmpty) {
      unawaited(widget.repository.recordStoryView(story.id).catchError((_) {}));
    }
    if (story.mediaType != 'video') _startImageTimer();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _currentIndex + 1 >= _stories.length) return;
      final next = _stories[_currentIndex + 1];
      if (next.mediaType == 'image' && next.mediaUrl.isNotEmpty) {
        unawaited(precacheImage(NetworkImage(next.mediaUrl), context));
      }
    });
  }

  void _startImageTimer() {
    if (_paused || story.mediaType == 'video') return;
    _progressTimer?.cancel();
    _progressTimer = Timer.periodic(const Duration(milliseconds: 100), (_) {
      if (!mounted || _paused) return;
      final next = _progress + .0125;
      if (next >= 1) {
        _progressTimer?.cancel();
        _nextStory();
        return;
      }
      setState(() => _progress = next);
    });
  }

  void _nextStory() => _moveStory(1);

  void _previousStory() => _moveStory(-1);

  void _moveStory(int direction) {
    final next = _currentIndex + direction;
    if (next < 0) return;
    if (next >= _stories.length) {
      Navigator.of(context).pop();
      return;
    }
    setState(() {
      _currentIndex = next;
      _paused = false;
    });
    _activateStory();
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    super.dispose();
  }

  Future<void> _showViewers() async {
    try {
      final viewers = await widget.repository.storyViewers(story.id);
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        backgroundColor: Colors.white,
        builder: (context) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 28),
            children: [
              Text(
                viewers.length.toString() + ' vues',
                style: const TextStyle(
                  fontSize: 21,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 10),
              if (viewers.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 28),
                  child: Text('Personne n’a encore vu ce statut.'),
                )
              else
                ...viewers.map(
                  (viewer) => ListTile(
                    leading: CircleAvatar(
                      backgroundImage: _imageProvider(
                        _text(viewer['photoUrl']),
                      ),
                      child: _text(viewer['photoUrl']).isEmpty
                          ? Text(_initial(_text(viewer['displayName'])))
                          : null,
                    ),
                    title: Text(
                      _text(viewer['displayName'], fallback: 'Contact WAPI'),
                    ),
                  ),
                ),
            ],
          ),
        ),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Les vues ne sont pas disponibles pour le moment.'),
          ),
        );
      }
    }
  }

  Future<void> _deleteStory() async {
    final remove = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Supprimer ce statut ?'),
        content: const Text('Il ne sera plus visible par vos contacts.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Annuler'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Supprimer'),
          ),
        ],
      ),
    );
    if (remove != true) return;
    try {
      await widget.repository.deleteStory(story.id);
      if (mounted) Navigator.of(context).pop();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Suppression impossible pour le moment.'),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => GestureDetector(
    onLongPressStart: (_) {
      _progressTimer?.cancel();
      setState(() => _paused = true);
    },
    onLongPressEnd: (_) {
      setState(() => _paused = false);
      _startImageTimer();
    },
    onTapUp: (details) {
      final width = MediaQuery.sizeOf(context).width;
      if (details.localPosition.dx < width * .30) {
        _previousStory();
      } else if (details.localPosition.dx > width * .70) {
        _nextStory();
      }
    },
    onHorizontalDragEnd: (details) {
      final velocity = details.primaryVelocity ?? 0;
      if (velocity < -250) _nextStory();
      if (velocity > 250) _previousStory();
    },
    onVerticalDragEnd: (details) {
      if ((details.primaryVelocity ?? 0) > 250) Navigator.of(context).pop();
    },
    child: Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            Positioned.fill(child: _storyBody()),
            Positioned(
              top: 5,
              left: 14,
              right: 14,
              child: Row(
                children: List.generate(_stories.length, (index) {
                  final value = index < _currentIndex
                      ? 1.0
                      : index > _currentIndex
                      ? 0.0
                      : story.mediaType == 'video'
                      ? _videoProgress
                      : _progress;
                  return Expanded(
                    child: Padding(
                      padding: EdgeInsets.only(
                        right: index == _stories.length - 1 ? 0 : 3,
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: value,
                          minHeight: 3,
                          backgroundColor: Colors.white24,
                          valueColor: const AlwaysStoppedAnimation(
                            Colors.white,
                          ),
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ),
            Positioned(
              top: 12,
              left: 16,
              right: 16,
              child: Row(
                children: [
                  CircleAvatar(
                    backgroundColor: Colors.white24,
                    foregroundColor: Colors.white,
                    backgroundImage: _imageProvider(story.authorPhotoUrl),
                    child: story.authorPhotoUrl.isEmpty
                        ? Text(_initial(story.authorName))
                        : null,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          story.authorName,
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 1),
                        Text(
                          _publishedAt,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: .72),
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_isAuthor)
                    IconButton(
                      onPressed: _deleteStory,
                      icon: const Icon(
                        Icons.delete_outline,
                        color: Colors.white,
                      ),
                      tooltip: 'Supprimer le statut',
                    ),
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close, color: Colors.white),
                    tooltip: 'Fermer',
                  ),
                ],
              ),
            ),
            if (story.text.isNotEmpty)
              Positioned(
                left: 24,
                right: 24,
                bottom: _isAuthor ? 72 : 58,
                child: Text(
                  story.text,
                  textAlign: TextAlign.center,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    shadows: [Shadow(color: Colors.black87, blurRadius: 8)],
                  ),
                ),
              ),
            Positioned(
              left: 24,
              right: 24,
              bottom: 32,
              child: _isAuthor
                  ? TextButton.icon(
                      onPressed: _showViewers,
                      icon: const Icon(
                        Icons.visibility_outlined,
                        color: Colors.white,
                      ),
                      label: Text(
                        story.viewCount.toString() + ' vues',
                        style: const TextStyle(color: Colors.white),
                      ),
                    )
                  : Text(
                      'Glissez vers le bas pour fermer',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: .75),
                        fontSize: 12,
                      ),
                    ),
            ),
          ],
        ),
      ),
    ),
  );

  Widget _storyBody() {
    if (story.mediaType == 'image' && story.mediaUrl.isNotEmpty) {
      return Stack(
        fit: StackFit.expand,
        children: [
          Image.network(
            story.mediaUrl,
            fit: BoxFit.cover,
            color: Colors.black.withValues(alpha: .5),
            colorBlendMode: BlendMode.darken,
            gaplessPlayback: true,
          ),
          Center(
            child: Image.network(
              story.mediaUrl,
              fit: BoxFit.contain,
              gaplessPlayback: true,
              frameBuilder: (context, child, frame, synchronouslyLoaded) {
                if (synchronouslyLoaded || frame != null) return child;
                return const Center(
                  child: CircularProgressIndicator(color: Colors.white),
                );
              },
              errorBuilder: (_, _, _) => const _StoryFallback(
                icon: Icons.broken_image_outlined,
                label: 'Image indisponible',
              ),
            ),
          ),
        ],
      );
    }
    if (story.mediaType == 'video') {
      return _WapiVideoPlayer(
        key: ValueKey(story.id),
        url: story.mediaUrl,
        title: story.text.isEmpty ? 'Statut vidéo' : story.text,
        embedded: true,
        paused: _paused,
        onProgress: (value) {
          if (mounted) setState(() => _videoProgress = value);
        },
        onEnded: _nextStory,
      );
    }
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [WapiColors.blueDark, WapiColors.blue, Color(0xFF52C8FF)],
        ),
      ),
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(36),
          child: Text(
            story.text,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 27,
              height: 1.25,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
    );
  }
}

/// Conversation documents deliberately carry a compact member snapshot so the
/// inbox opens instantly.  This small live lookup wins over that snapshot when
/// a contact replaces their avatar, avoiding an old image lingering while the
/// server synchronises every past conversation.
enum _MessageReceiptState { sent, delivered, read }

class _MessageReceipt extends StatelessWidget {
  const _MessageReceipt({required this.state});

  final _MessageReceiptState state;

  @override
  Widget build(BuildContext context) {
    final read = state == _MessageReceiptState.read;
    final delivered = state != _MessageReceiptState.sent;
    final label = switch (state) {
      _MessageReceiptState.sent => 'Envoyé',
      _MessageReceiptState.delivered => 'Distribué, non lu',
      _MessageReceiptState.read => 'Lu',
    };
    return Semantics(
      label: label,
      child: Tooltip(
        message: label,
        child: Icon(
          delivered ? Icons.done_all_rounded : Icons.done_rounded,
          size: 15,
          color: read
              ? const Color(0xFF65F0AD)
              : Colors.white.withValues(alpha: .72),
        ),
      ),
    );
  }
}

class _LiveProfileAvatar extends StatelessWidget {
  const _LiveProfileAvatar({
    required this.userId,
    required this.fallbackUrl,
    required this.name,
    this.radius = 20,
  });

  final String userId;
  final String fallbackUrl;
  final String name;
  final double radius;

  @override
  Widget build(
    BuildContext context,
  ) => StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
    stream: userId.isEmpty
        ? null
        : FirebaseFirestore.instance
              .collection('users')
              .doc(userId)
              .snapshots(),
    builder: (context, snapshot) {
      final data = snapshot.data?.data() ?? const <String, dynamic>{};
      final current = (data['photoUrl'] as String?)?.trim();
      final verified = data['verified'] == true;
      final photoUrl = current?.isNotEmpty == true
          ? current!
          : fallbackUrl.trim();
      final initial = _initial(name);
      return Semantics(
        image: true,
        label: 'Photo de profil de $name${verified ? ', compte certifié' : ''}',
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            CircleAvatar(
              radius: radius,
              backgroundColor: WapiColors.blueSoft,
              child: ClipOval(
                child: photoUrl.isEmpty
                    ? Center(
                        child: Text(
                          initial,
                          style: TextStyle(
                            color: WapiColors.blue,
                            fontSize: radius,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      )
                    : _StableNetworkAvatarImage(
                        url: photoUrl,
                        size: radius * 2,
                        fallback: Center(
                          child: Text(
                            initial,
                            style: TextStyle(
                              color: WapiColors.blue,
                              fontSize: radius,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ),
              ),
            ),
            if (verified)
              Positioned(
                right: -2,
                bottom: -1,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 1.5),
                  ),
                  child: Icon(
                    Icons.verified_rounded,
                    color: WapiColors.blue,
                    size: radius < 20 ? 13 : 15,
                  ),
                ),
              ),
          ],
        ),
      );
    },
  );
}

class _StableNetworkAvatarImage extends StatelessWidget {
  const _StableNetworkAvatarImage({
    required this.url,
    required this.size,
    required this.fallback,
  });

  final String url;
  final double size;
  final Widget fallback;

  @override
  Widget build(BuildContext context) => Image.network(
    url,
    width: size,
    height: size,
    fit: BoxFit.cover,
    gaplessPlayback: true,
    cacheWidth: (size * MediaQuery.devicePixelRatioOf(context)).ceil(),
    frameBuilder: (context, child, frame, synchronouslyLoaded) {
      if (synchronouslyLoaded || frame != null) return child;
      return SizedBox.square(dimension: size, child: fallback);
    },
    errorBuilder: (_, _, _) =>
        SizedBox.square(dimension: size, child: fallback),
  );
}

ImageProvider? _imageProvider(String value) =>
    value.trim().isEmpty ? null : NetworkImage(value.trim());

String _storyTimeLabel(DateTime? value) {
  if (value == null) return 'À l’instant';
  final date = value.toLocal();
  final now = DateTime.now();
  final difference = now.difference(date);
  if (difference.inMinutes < 1) return 'À l’instant';
  if (difference.inMinutes < 60) return 'Il y a ${difference.inMinutes} min';
  final clock =
      '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  if (now.year == date.year && now.month == date.month && now.day == date.day) {
    return 'Aujourd’hui · $clock';
  }
  return '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')} · $clock';
}

String _initial(String value) =>
    value.trim().isEmpty ? '?' : value.trim().substring(0, 1).toUpperCase();
String _text(Object? value, {String fallback = ''}) =>
    value is String && value.trim().isNotEmpty ? value.trim() : fallback;

class _StoryFallback extends StatelessWidget {
  const _StoryFallback({required this.icon, required this.label});
  final IconData icon;
  final String label;
  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: Colors.white70, size: 42),
        const SizedBox(height: 10),
        Text(label, style: const TextStyle(color: Colors.white70)),
      ],
    ),
  );
}

class _StoriesLoadingState extends StatelessWidget {
  const _StoriesLoadingState();

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
    children: [
      const LinearProgressIndicator(minHeight: 2),
      const SizedBox(height: 18),
      Row(
        children: List.generate(
          4,
          (index) => Container(
            width: 58,
            height: 58,
            margin: const EdgeInsets.only(right: 14),
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: Color(0xFFE9EEF4),
            ),
          ),
        ),
      ),
      const SizedBox(height: 24),
      ...List.generate(
        3,
        (index) => Container(
          height: 72,
          margin: const EdgeInsets.only(bottom: 10),
          decoration: BoxDecoration(
            color: const Color(0xFFF3F6F9),
            borderRadius: BorderRadius.circular(18),
          ),
        ),
      ),
    ],
  );
}

class _StoryThumbnail extends StatelessWidget {
  const _StoryThumbnail({required this.story});
  final WapiStory story;

  @override
  Widget build(BuildContext context) => Container(
    width: 48,
    height: 52,
    clipBehavior: Clip.antiAlias,
    decoration: BoxDecoration(
      color: WapiColors.blueSoft,
      borderRadius: BorderRadius.circular(14),
    ),
    child: story.mediaType == 'image' && story.mediaUrl.isNotEmpty
        ? Image.network(
            story.mediaUrl,
            fit: BoxFit.cover,
            cacheWidth: 180,
            errorBuilder: (_, _, _) => const Icon(
              Icons.image_not_supported_outlined,
              color: WapiColors.muted,
            ),
          )
        : DecoratedBox(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                colors: [WapiColors.blueDark, WapiColors.blue],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
            child: Icon(
              story.mediaType == 'video'
                  ? Icons.play_arrow_rounded
                  : Icons.format_quote_rounded,
              color: Colors.white,
            ),
          ),
  );
}

class _StoryPublisher extends StatelessWidget {
  const _StoryPublisher({required this.photoUrl, required this.onTap});
  final String photoUrl;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(18),
    child: Ink(
      padding: const EdgeInsets.fromLTRB(12, 11, 12, 11),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: WapiColors.line),
      ),
      child: Row(
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              CircleAvatar(
                radius: 25,
                backgroundColor: WapiColors.blueSoft,
                backgroundImage: _imageProvider(photoUrl),
                child: photoUrl.isEmpty
                    ? const Icon(Icons.person_outline, color: WapiColors.blue)
                    : null,
              ),
              const Positioned(
                right: -3,
                bottom: -2,
                child: CircleAvatar(
                  radius: 10,
                  backgroundColor: WapiColors.blue,
                  child: Icon(Icons.add, size: 15, color: Colors.white),
                ),
              ),
            ],
          ),
          const SizedBox(width: 13),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Mon statut',
                  style: TextStyle(fontWeight: FontWeight.w800),
                ),
                SizedBox(height: 2),
                Text(
                  'Ajoutez une photo ou une vidéo',
                  style: TextStyle(color: WapiColors.muted),
                ),
              ],
            ),
          ),
          const Icon(Icons.camera_alt_outlined, color: WapiColors.blue),
        ],
      ),
    ),
  );
}

// ignore: unused_element
class _BusinessPage extends StatelessWidget {
  const _BusinessPage({required this.user});
  final User user;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: 'Business',
      subtitle: 'Votre activité, clairement',
      actions: [
        IconButton(
          onPressed: () => _createPage(context),
          icon: const Icon(Icons.add_business_outlined),
          tooltip: 'Créer une page',
        ),
      ],
    ),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('businessPages')
          .where('ownerId', isEqualTo: user.uid)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Business indisponible',
            body: 'WAPI ne peut pas charger vos pages pour le moment.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final pages = snapshot.data!.docs;
        if (pages.isEmpty) {
          return _StateMessage(
            icon: Icons.storefront_outlined,
            title: 'Créez votre espace Business',
            body:
                'Gérez une page, des offres et vos campagnes sans données de démonstration.',
            actionLabel: 'Créer ma page',
            onAction: () => _createPage(context),
          );
        }
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Mes pages',
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 10),
            ...pages.map((doc) {
              final page = doc.data();
              return Card(
                margin: const EdgeInsets.only(bottom: 12),
                child: ListTile(
                  leading: const CircleAvatar(
                    backgroundColor: WapiColors.blueSoft,
                    foregroundColor: WapiColors.blue,
                    child: Icon(Icons.storefront_outlined),
                  ),
                  title: Text(
                    (page['name'] as String?) ?? 'Page WAPI',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  subtitle: Text(
                    '${(page['category'] as String?) ?? 'Business'} · ${(page['city'] as String?) ?? 'Brazzaville'}',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                ),
              );
            }),
          ],
        );
      },
    ),
  );
  void _createPage(BuildContext context) {
    final name = TextEditingController();
    final category = TextEditingController();
    final bio = TextEditingController();
    final city = TextEditingController(text: 'Brazzaville');
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          20,
          20,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  'Créer une page',
                  style: Theme.of(sheetContext).textTheme.headlineSmall
                      ?.copyWith(fontWeight: FontWeight.w800),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: name,
                maxLength: 80,
                decoration: const InputDecoration(labelText: 'Nom de la page'),
              ),
              TextField(
                controller: category,
                maxLength: 80,
                decoration: const InputDecoration(labelText: 'Catégorie'),
              ),
              TextField(
                controller: city,
                maxLength: 80,
                decoration: const InputDecoration(labelText: 'Ville'),
              ),
              TextField(
                controller: bio,
                maxLength: 400,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(labelText: 'Présentation'),
              ),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: () async {
                    final value = name.text.trim();
                    if (value.length < 2) return;
                    final ref = FirebaseFirestore.instance
                        .collection('businessPages')
                        .doc();
                    final slug = value
                        .toLowerCase()
                        .replaceAll(RegExp(r'[^a-z0-9]+'), '-')
                        .replaceAll(RegExp(r'^-|-$'), '');
                    try {
                      await ref.set({
                        'ownerId': user.uid,
                        'name': value,
                        'searchName': value.toLowerCase(),
                        'handle':
                            '${slug.isEmpty ? 'wapi' : slug}-${ref.id.substring(0, 5)}',
                        'type': 'business',
                        'category': category.text.trim(),
                        'bio': bio.text.trim(),
                        'city': city.text.trim().isEmpty
                            ? 'Brazzaville'
                            : city.text.trim(),
                        'phone': '',
                        'website': '',
                        'status': 'active',
                        'followers': 0,
                        'createdAt': FieldValue.serverTimestamp(),
                        'updatedAt': FieldValue.serverTimestamp(),
                      });
                      if (sheetContext.mounted) {
                        Navigator.pop(sheetContext);
                      }
                    } catch (error) {
                      if (sheetContext.mounted) {
                        ScaffoldMessenger.of(sheetContext).showSnackBar(
                          SnackBar(
                            content: Text(
                              wapiErrorText(
                                error,
                                fallback:
                                    'Le compte Business n’a pas pu être créé.',
                              ),
                            ),
                          ),
                        );
                      }
                    }
                  },
                  child: const Text('Créer la page'),
                ),
              ),
            ],
          ),
        ),
      ),
    ).whenComplete(() {
      name.dispose();
      category.dispose();
      bio.dispose();
      city.dispose();
    });
  }
}

class _ProfilePage extends StatefulWidget {
  const _ProfilePage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;
  @override
  State<_ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<_ProfilePage> {
  User get user => widget.user;
  WapiRepository get repository => widget.repository;
  File? _pendingPhoto;
  String? _uploadedPhotoUrl;
  bool _uploadingPhoto = false;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(title: 'Profil', subtitle: 'Compte et réglages'),
    body: StreamBuilder<DocumentSnapshot<Map<String, dynamic>>>(
      stream: repository.profile(user.uid),
      builder: (context, snapshot) {
        final data = snapshot.data?.data() ?? const <String, dynamic>{};
        final name = (data['displayName'] as String?)?.trim().isNotEmpty == true
            ? data['displayName'] as String
            : (user.displayName?.trim().isNotEmpty == true
                  ? user.displayName!
                  : 'Mon compte WAPI');
        final phone =
            (data['phoneNumber'] as String?) ?? user.phoneNumber ?? '';
        final photoUrl =
            _uploadedPhotoUrl ??
            (data['photoUrl'] as String?) ??
            user.photoURL ??
            '';
        final hasPhoto = _pendingPhoto != null || photoUrl.isNotEmpty;
        final isVerified = data['verified'] == true;
        final isFounder =
            phone.replaceAll(RegExp(r'[^0-9+]'), '') == '+242065465808';
        final bio = (data['bio'] as String?)?.trim() ?? '';
        final occupation = (data['occupation'] as String?)?.trim() ?? '';
        final city = (data['city'] as String?)?.trim() ?? '';
        final website = (data['website'] as String?)?.trim() ?? '';
        final statusText = (data['statusText'] as String?)?.trim() ?? '';
        final completedFields = [
          name,
          photoUrl,
          bio,
          occupation,
          city,
          statusText,
        ].where((value) => value.isNotEmpty).length;
        final completion = completedFields / 6;
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF06283B), Color(0xFF087F8C)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(26),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF06283B).withValues(alpha: .18),
                    blurRadius: 20,
                    offset: const Offset(0, 10),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      InkWell(
                        onTap: () => _changePhoto(context),
                        borderRadius: BorderRadius.circular(44),
                        child: Stack(
                          clipBehavior: Clip.none,
                          children: [
                            CircleAvatar(
                              radius: 38,
                              backgroundColor: Colors.white24,
                              foregroundColor: Colors.white,
                              backgroundImage: _pendingPhoto != null
                                  ? FileImage(_pendingPhoto!)
                                  : photoUrl.isEmpty
                                  ? null
                                  : NetworkImage(photoUrl),
                              child: !hasPhoto
                                  ? Text(
                                      name.substring(0, 1).toUpperCase(),
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w900,
                                        fontSize: 25,
                                      ),
                                    )
                                  : null,
                            ),
                            Positioned(
                              right: -2,
                              bottom: -2,
                              child: CircleAvatar(
                                radius: 13,
                                backgroundColor: Colors.white,
                                foregroundColor: WapiColors.blueDark,
                                child: _uploadingPhoto
                                    ? const SizedBox.square(
                                        dimension: 12,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const Icon(
                                        Icons.camera_alt_rounded,
                                        size: 15,
                                      ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Flexible(
                                  child: Text(
                                    name,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w900,
                                      fontSize: 21,
                                    ),
                                  ),
                                ),
                                if (isVerified) ...[
                                  const SizedBox(width: 5),
                                  const Icon(
                                    Icons.verified_rounded,
                                    color: Color(0xFF7CE5FF),
                                    size: 19,
                                  ),
                                ],
                              ],
                            ),
                            const SizedBox(height: 3),
                            Text(
                              statusText.isEmpty
                                  ? (phone.isEmpty ? 'Profil WAPI' : phone)
                                  : statusText,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: Color(0xFFD7EEF3),
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            if (occupation.isNotEmpty || city.isNotEmpty) ...[
                              const SizedBox(height: 7),
                              Text(
                                [occupation, city]
                                    .where((value) => value.isNotEmpty)
                                    .join(' · '),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: Color(0xFF9DD7DE),
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      IconButton(
                        onPressed: () => _editProfile(
                          context,
                          name: name,
                          bio: bio,
                          occupation: occupation,
                          city: city,
                          website: website,
                          statusText: statusText,
                        ),
                        icon: const Icon(Icons.edit_rounded),
                        color: Colors.white,
                        tooltip: 'Modifier le profil',
                      ),
                    ],
                  ),
                  if (bio.isNotEmpty) ...[
                    const SizedBox(height: 15),
                    Text(
                      bio,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, height: 1.35),
                    ),
                  ],
                  const SizedBox(height: 15),
                  Row(
                    children: [
                      const Icon(
                        Icons.person_rounded,
                        color: Color(0xFFB9E8ED),
                        size: 16,
                      ),
                      const SizedBox(width: 7),
                      Expanded(
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(99),
                          child: LinearProgressIndicator(
                            value: completion,
                            minHeight: 7,
                            backgroundColor: Colors.white12,
                            color: const Color(0xFF65E3BD),
                          ),
                        ),
                      ),
                      const SizedBox(width: 9),
                      Text(
                        '${(completion * 100).round()} %',
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w900,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            if (isFounder)
              _ProfileSection(
                title: 'Administration',
                icon: Icons.admin_panel_settings_outlined,
                detail: 'Tableau de bord du compte fondateur',
                onTap: () => _showAdministration(context, phone),
              ),
            _ProfileSection(
              title: 'Informations publiques',
              icon: Icons.manage_accounts_outlined,
              detail: 'Nom, bio, métier, ville, site et statut',
              onTap: () => _editProfile(
                context,
                name: name,
                bio: bio,
                occupation: occupation,
                city: city,
                website: website,
                statusText: statusText,
              ),
            ),
            _ProfileSection(
              title: 'Mon code WAPI',
              icon: Icons.qr_code_2_rounded,
              detail: 'Partager rapidement votre profil',
              onTap: () => _showProfileQr(context, name, photoUrl),
            ),
            _ProfileSection(
              title: 'Notifications',
              icon: Icons.notifications_none,
              detail: 'Messages, appels et sons',
              onTap: () => _showNotificationSettings(context),
            ),
            _ProfileSection(
              title: 'Stockage et données',
              icon: Icons.storage_outlined,
              detail: 'Médias, cache et sauvegarde',
              onTap: () => _showStorageInfo(context),
            ),
            _ProfileSection(
              title: 'Jumeau numérique',
              icon: Icons.auto_awesome_outlined,
              detail: 'Consentements, contrôle et sécurité',
              onTap: () => Navigator.of(
                context,
              ).push(MaterialPageRoute(builder: (_) => _TwinPage(user: user))),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: FirebaseAuth.instance.signOut,
              icon: const Icon(Icons.logout),
              label: const Text('Se déconnecter'),
            ),
          ],
        );
      },
    ),
  );

  Future<void> _changePhoto(BuildContext context) async {
    final image = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 92,
    );
    if (image == null) return;
    final selected = File(image.path);
    setState(() {
      _pendingPhoto = selected;
      _uploadingPhoto = true;
    });
    try {
      final url = await repository.uploadProfilePhoto(
        user: user,
        file: selected,
        contentType: 'image/jpeg',
      );
      if (mounted) {
        setState(() {
          _uploadedPhotoUrl = url;
          _pendingPhoto = null;
        });
      }
    } catch (_) {
      if (context.mounted) {
        setState(() => _pendingPhoto = null);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'La photo de profil n’a pas pu être enregistrée. Réessayez.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _uploadingPhoto = false);
    }
  }

  Future<void> _editProfile(
    BuildContext context, {
    required String name,
    required String bio,
    required String occupation,
    required String city,
    required String website,
    required String statusText,
  }) async {
    final nameController = TextEditingController(text: name);
    final bioController = TextEditingController(text: bio);
    final occupationController = TextEditingController(text: occupation);
    final cityController = TextEditingController(text: city);
    final websiteController = TextEditingController(text: website);
    final statusController = TextEditingController(text: statusText);
    var saving = false;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheetState) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            12,
            20,
            MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 42,
                    height: 4,
                    decoration: BoxDecoration(
                      color: WapiColors.line,
                      borderRadius: BorderRadius.circular(99),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Enrichir mon profil',
                  style: Theme.of(sheetContext).textTheme.headlineSmall
                      ?.copyWith(fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Ces informations permettent à vos contacts de mieux vous reconnaître.',
                  style: TextStyle(color: WapiColors.muted),
                ),
                const SizedBox(height: 18),
                TextField(
                  controller: nameController,
                  autofocus: true,
                  maxLength: 80,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(
                    labelText: 'Nom affiché',
                    prefixIcon: Icon(Icons.badge_outlined),
                  ),
                ),
                TextField(
                  controller: statusController,
                  maxLength: 80,
                  decoration: const InputDecoration(
                    labelText: 'Statut court',
                    hintText: 'Ex. Disponible, en réunion…',
                    prefixIcon: Icon(Icons.bolt_outlined),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: bioController,
                  maxLength: 240,
                  minLines: 2,
                  maxLines: 4,
                  decoration: const InputDecoration(
                    labelText: 'À propos',
                    alignLabelWithHint: true,
                    prefixIcon: Icon(Icons.notes_rounded),
                  ),
                ),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: occupationController,
                        maxLength: 80,
                        decoration: const InputDecoration(
                          labelText: 'Activité',
                          prefixIcon: Icon(Icons.work_outline_rounded),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: TextField(
                        controller: cityController,
                        maxLength: 80,
                        decoration: const InputDecoration(
                          labelText: 'Ville',
                          prefixIcon: Icon(Icons.location_on_outlined),
                        ),
                      ),
                    ),
                  ],
                ),
                TextField(
                  controller: websiteController,
                  maxLength: 180,
                  keyboardType: TextInputType.url,
                  decoration: const InputDecoration(
                    labelText: 'Site HTTPS',
                    prefixIcon: Icon(Icons.link_rounded),
                  ),
                ),
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: saving
                        ? null
                        : () async {
                            setSheetState(() => saving = true);
                            try {
                              await repository.updateProfileDetails(
                                user: user,
                                displayName: nameController.text,
                                bio: bioController.text,
                                occupation: occupationController.text,
                                city: cityController.text,
                                website: websiteController.text,
                                statusText: statusController.text,
                              );
                              if (sheetContext.mounted) {
                                Navigator.pop(sheetContext);
                              }
                            } catch (_) {
                              if (sheetContext.mounted) {
                                ScaffoldMessenger.of(sheetContext).showSnackBar(
                                  const SnackBar(
                                    content: Text(
                                      'Profil non enregistré. Vérifiez les informations puis réessayez.',
                                    ),
                                  ),
                                );
                              }
                            } finally {
                              if (sheetContext.mounted) {
                                setSheetState(() => saving = false);
                              }
                            }
                          },
                    icon: saving
                        ? const SizedBox.square(
                            dimension: 17,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.check_rounded),
                    label: Text(saving ? 'Enregistrement…' : 'Enregistrer'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    nameController.dispose();
    bioController.dispose();
    occupationController.dispose();
    cityController.dispose();
    websiteController.dispose();
    statusController.dispose();
  }

  Future<void> _showProfileQr(
    BuildContext context,
    String name,
    String photoUrl,
  ) => showModalBottomSheet<void>(
    context: context,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircleAvatar(
            radius: 28,
            backgroundColor: WapiColors.blueSoft,
            backgroundImage: _imageProvider(photoUrl),
            child: photoUrl.isEmpty ? Text(_initial(name)) : null,
          ),
          const SizedBox(height: 10),
          Text(
            name,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 4),
          const Text(
            'Code personnel WAPI',
            style: TextStyle(color: WapiColors.muted),
          ),
          const SizedBox(height: 18),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: WapiColors.line),
            ),
            child: QrImageView(
              data: 'wapi://profile/${user.uid}',
              size: 220,
              eyeStyle: const QrEyeStyle(
                eyeShape: QrEyeShape.square,
                color: WapiColors.blueDark,
              ),
              dataModuleStyle: const QrDataModuleStyle(
                dataModuleShape: QrDataModuleShape.circle,
                color: WapiColors.blueDark,
              ),
            ),
          ),
          const SizedBox(height: 14),
          const Text(
            'Un contact peut numériser ce code pour ouvrir votre profil et démarrer une discussion.',
            textAlign: TextAlign.center,
            style: TextStyle(color: WapiColors.muted, height: 1.35),
          ),
        ],
      ),
    ),
  );

  void _showNotificationSettings(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => SafeArea(
        child: Container(
          margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 20),
          decoration: BoxDecoration(
            color: const Color(0xFF0B1720),
            borderRadius: BorderRadius.circular(30),
            border: Border.all(color: const Color(0x334BD1FF)),
            boxShadow: const [
              BoxShadow(
                color: Color(0x66020A0F),
                blurRadius: 28,
                offset: Offset(0, 14),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: .22),
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: [Color(0xFF31C5FF), Color(0xFF1677FF)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                    ),
                    child: const Icon(
                      Icons.notifications_active_rounded,
                      color: Colors.white,
                      size: 27,
                    ),
                  ),
                  const SizedBox(width: 14),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Notifications WAPI',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 21,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        SizedBox(height: 3),
                        Text(
                          'Vos conversations restent visibles au bon moment.',
                          style: TextStyle(color: Color(0xFFB4C9D8)),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 22),
              const _NotificationPreviewRow(
                icon: Icons.chat_bubble_rounded,
                title: 'Messages',
                detail: 'Aperçu, son et badge non lu',
                color: Color(0xFF2AB7FF),
              ),
              const SizedBox(height: 10),
              const _NotificationPreviewRow(
                icon: Icons.call_rounded,
                title: 'Appels',
                detail: 'Alerte prioritaire avec actions rapides',
                color: Color(0xFF68D391),
              ),
              const SizedBox(height: 10),
              const _NotificationPreviewRow(
                icon: Icons.volume_up_rounded,
                title: 'Sons et vibration',
                detail: 'Réglés selon le profil de votre appareil',
                color: Color(0xFFFFB85C),
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () async {
                    await openAppSettings();
                  },
                  icon: const Icon(Icons.tune_rounded),
                  label: const Text('Gérer les alertes de cet appareil'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.white,
                    side: const BorderSide(color: Color(0x554BD1FF)),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showStorageInfo(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => const SafeArea(
        child: Padding(
          padding: EdgeInsets.fromLTRB(20, 20, 20, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Stockage et données',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
              ),
              SizedBox(height: 12),
              Text(
                'Les messages restent dans votre conversation. Les médias sont protégés et accessibles uniquement aux membres de la conversation. Le cache local est géré par Android et peut être vidé depuis les réglages de l’application.',
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showAdministration(BuildContext context, String phone) {
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Administration WAPI',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 10),
              Text('Compte fondateur vérifié : $phone'),
              const SizedBox(height: 8),
              const Text(
                'Les opérations sensibles restent protégées par les services WAPI et ne sont pas accessibles depuis un simple écran mobile.',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NotificationPreviewRow extends StatelessWidget {
  const _NotificationPreviewRow({
    required this.icon,
    required this.title,
    required this.detail,
    required this.color,
  });

  final IconData icon;
  final String title;
  final String detail;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 12),
    decoration: BoxDecoration(
      color: Colors.white.withValues(alpha: .055),
      borderRadius: BorderRadius.circular(18),
      border: Border.all(color: Colors.white.withValues(alpha: .075)),
    ),
    child: Row(
      children: [
        Container(
          width: 38,
          height: 38,
          decoration: BoxDecoration(
            color: color.withValues(alpha: .16),
            borderRadius: BorderRadius.circular(13),
          ),
          child: Icon(icon, color: color, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                detail,
                style: const TextStyle(color: Color(0xFFB4C9D8), fontSize: 12),
              ),
            ],
          ),
        ),
        Icon(Icons.check_circle_rounded, color: color, size: 19),
      ],
    ),
  );
}

List<Map<String, String>> _savedContacts(Map? rawContacts) {
  final source = Map<String, dynamic>.from(rawContacts ?? const {});
  final contacts = <Map<String, String>>[];
  for (final entry in source.entries) {
    if (entry.value is! Map) continue;
    final data = Map<String, dynamic>.from(entry.value as Map);
    contacts.add({
      'uid': entry.key,
      'displayName': (data['displayName'] as String?) ?? 'Membre WAPI',
      'phoneNumber': (data['phoneNumber'] as String?) ?? '',
      'photoUrl': (data['photoUrl'] as String?) ?? '',
    });
  }
  contacts.sort(
    (left, right) => left['displayName']!.compareTo(right['displayName']!),
  );
  return contacts;
}

class _ProfileSection extends StatelessWidget {
  const _ProfileSection({
    required this.title,
    required this.icon,
    required this.detail,
    this.onTap,
  });
  final String title;
  final IconData icon;
  final String detail;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 10),
    child: ListTile(
      onTap: onTap,
      leading: Icon(icon, color: WapiColors.blue),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(detail),
      trailing: const Icon(Icons.chevron_right),
    ),
  );
}

class _StateMessage extends StatelessWidget {
  const _StateMessage({
    required this.icon,
    required this.title,
    required this.body,
    this.actionLabel,
    this.onAction,
  });
  final IconData icon;
  final String title;
  final String body;
  final String? actionLabel;
  final VoidCallback? onAction;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: WapiColors.blue, size: 42),
          const SizedBox(height: 14),
          Text(
            title,
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(
            body,
            textAlign: TextAlign.center,
            style: const TextStyle(color: WapiColors.muted, height: 1.4),
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 16),
            FilledButton(onPressed: onAction, child: Text(actionLabel!)),
          ],
        ],
      ),
    ),
  );
}
