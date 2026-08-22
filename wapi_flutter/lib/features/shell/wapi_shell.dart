import 'dart:async';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:file_picker/file_picker.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:image_picker/image_picker.dart';
import 'package:just_audio/just_audio.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:path_provider/path_provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:record/record.dart';
import 'package:video_player/video_player.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/wapi_theme.dart';
import '../../data/wapi_repository.dart';
import '../../services/wapi_notifications.dart';
import '../calls/wapi_call_page.dart';

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
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _setPresence(true);
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
    _setPresence(state == AppLifecycleState.resumed);
    if (state == AppLifecycleState.resumed) {
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
    return Scaffold(
      body: IndexedStack(index: _index, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
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
        ],
      ),
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
        Image.asset('assets/branding/wapi_mark.png', width: 38, height: 38),
        const SizedBox(width: 10),
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
    appBar: const _WapiAppBar(
      title: 'WAPI',
      subtitle: 'Vos échanges, simplement',
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: WapiColors.blue,
            borderRadius: BorderRadius.circular(24),
          ),
          child: const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Votre univers WAPI',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 23,
                  fontWeight: FontWeight.w800,
                ),
              ),
              SizedBox(height: 7),
              Text(
                'Discuter, publier, créer et développer votre activité depuis une seule application.',
                style: TextStyle(color: Colors.white70, height: 1.35),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        Text(
          'Accès rapide',
          style: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 10),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 10,
          crossAxisSpacing: 10,
          childAspectRatio: 1.35,
          children: [
            _HomeShortcut(
              icon: Icons.person_add_alt_1_outlined,
              title: 'Contacts',
              detail: 'Trouver et écrire',
              onTap: () => _push(
                context,
                _ContactsPage(user: user, repository: repository),
              ),
            ),
            _HomeShortcut(
              icon: Icons.business_center_outlined,
              title: 'Business',
              detail: 'Pages et offres',
              onTap: () => _push(context, _BusinessPage(user: user)),
            ),
            _HomeShortcut(
              icon: Icons.live_tv_outlined,
              title: 'En direct',
              detail: 'Lives disponibles',
              onTap: () => _push(context, const _LivePage()),
            ),
            _HomeShortcut(
              icon: Icons.storefront_outlined,
              title: 'Marché',
              detail: 'Annonces réelles',
              onTap: () => _push(
                context,
                const _FeedPage(
                  title: 'Marché',
                  collection: 'listings',
                  icon: Icons.storefront_outlined,
                ),
              ),
            ),
            _HomeShortcut(
              icon: Icons.notifications_outlined,
              title: 'Chaînes',
              detail: 'Médias et créateurs',
              onTap: () => _push(context, _ChannelsPage(user: user)),
            ),
            _HomeShortcut(
              icon: Icons.radio_outlined,
              title: 'Radio',
              detail: 'Émissions publiées',
              onTap: () => _push(context, _RadioPage(user: user)),
            ),
            _HomeShortcut(
              icon: Icons.auto_awesome_outlined,
              title: 'Jumeau numérique',
              detail: 'Consentements et studio',
              onTap: () => _push(context, _TwinPage(user: user)),
            ),
            _HomeShortcut(
              icon: Icons.person_outline,
              title: 'Profil',
              detail: 'Compte et réglages',
              onTap: () => _push(
                context,
                _ProfilePage(user: user, repository: repository),
              ),
            ),
          ],
        ),
      ],
    ),
  );

  void _push(BuildContext context, Widget page) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
}

class _HomeShortcut extends StatelessWidget {
  const _HomeShortcut({
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
  Widget build(BuildContext context) => Card(
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.all(13),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: WapiColors.blue),
            const Spacer(),
            Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 2),
            Text(
              detail,
              style: const TextStyle(color: WapiColors.muted, fontSize: 11),
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    ),
  );
}

class _CallsPage extends StatelessWidget {
  const _CallsPage({required this.user});
  final User user;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(title: 'Appels', subtitle: 'Audio et vidéo'),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('calls')
          .where('calleeId', isEqualTo: user.uid)
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
        final calls = snapshot.data!.docs;
        if (calls.isEmpty) {
          return const _StateMessage(
            icon: Icons.call_outlined,
            title: 'Aucun appel récent',
            body:
                'Lancez un appel audio ou vidéo depuis une conversation WAPI.',
          );
        }
        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: calls.length,
          itemBuilder: (context, index) {
            final call = calls[index].data();
            return Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                onTap: call['status'] == 'ringing'
                    ? () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => WapiCallPage.incoming(
                            user: user,
                            incomingCallId: snapshot.data!.docs[index].id,
                            peerId: (call['callerId'] as String?) ?? '',
                            peerName:
                                (call['callerName'] as String?) ??
                                'Contact WAPI',
                            video: call['video'] == true,
                          ),
                        ),
                      )
                    : null,
                leading: const CircleAvatar(
                  backgroundColor: WapiColors.blueSoft,
                  foregroundColor: WapiColors.blue,
                  child: Icon(Icons.call_received),
                ),
                title: Text(
                  (call['callerName'] as String?) ?? 'Appel WAPI',
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: Text(
                  call['status'] == 'ringing'
                      ? 'Appuyez pour répondre'
                      : (call['status'] as String?) ?? 'Historique',
                ),
                trailing: Icon(
                  call['video'] == true
                      ? Icons.videocam_outlined
                      : Icons.call_outlined,
                ),
              ),
            );
          },
        );
      },
    ),
  );
}

class _AssistantPage extends StatelessWidget {
  const _AssistantPage({required this.user});
  final User user;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Assistant WAPI',
      subtitle: 'Privé et contrôlé',
    ),
    body: const _StateMessage(
      icon: Icons.smart_toy_outlined,
      title: 'Assistant en migration',
      body:
          'L’interface Flutter est prête. Le service assistant sera activé dès qu’une API POST JSON valide sera disponible, sans réponse locale inventée.',
    ),
  );
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
                      SnackBar(content: Text('Création impossible : $error')),
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
                            content: Text('Publication impossible : $error'),
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
      subtitle: 'Épisodes publiés sur WAPI',
    ),
    floatingActionButton: FloatingActionButton.extended(
      onPressed: _publish,
      icon: const Icon(Icons.upload_file_outlined),
      label: const Text('Publier'),
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
                'Importez un fichier audio : il sera stocké dans votre espace WAPI puis disponible à l’écoute.',
            actionLabel: 'Publier un épisode',
            onAction: _publish,
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
          itemCount: episodes.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final doc = episodes[index];
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

  Future<void> _publish() async {
    final station = TextEditingController(text: 'Ma radio WAPI');
    final title = TextEditingController();
    var publishing = false;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheetState) => Padding(
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
                'Publier un épisode',
                style: Theme.of(sheetContext).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: station,
                maxLength: 60,
                decoration: const InputDecoration(labelText: 'Nom de la radio'),
              ),
              TextField(
                controller: title,
                maxLength: 100,
                decoration: const InputDecoration(
                  labelText: 'Titre de l’épisode',
                ),
              ),
              const SizedBox(height: 8),
              FilledButton.icon(
                onPressed: publishing
                    ? null
                    : () async {
                        final audio = await FilePicker.pickFile(
                          type: FileType.audio,
                        );
                        if (audio?.path == null) return;
                        setSheetState(() => publishing = true);
                        try {
                          final probe = AudioPlayer();
                          final duration = await probe.setFilePath(
                            audio!.path!,
                          );
                          await probe.dispose();
                          await _repository.publishRadioEpisode(
                            user: widget.user,
                            file: File(audio.path!),
                            fileName: audio.name,
                            stationName: station.text,
                            title: title.text,
                            durationSeconds: duration?.inSeconds ?? 0,
                          );
                          if (sheetContext.mounted) {
                            Navigator.of(sheetContext).pop();
                          }
                        } catch (error) {
                          if (sheetContext.mounted) {
                            ScaffoldMessenger.of(sheetContext).showSnackBar(
                              SnackBar(
                                content: Text(
                                  'Publication impossible : $error',
                                ),
                              ),
                            );
                          }
                        } finally {
                          if (sheetContext.mounted) {
                            setSheetState(() => publishing = false);
                          }
                        }
                      },
                icon: publishing
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.audiotrack_outlined),
                label: const Text('Choisir le fichier audio'),
              ),
            ],
          ),
        ),
      ),
    );
    station.dispose();
    title.dispose();
  }
}

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
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('users')
          .limit(80)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Contacts indisponibles',
            body: 'WAPI ne peut pas charger les profils pour le moment.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final contacts = snapshot.data!.docs
            .where((doc) => doc.id != user.uid)
            .toList();
        if (contacts.isEmpty) {
          return const _StateMessage(
            icon: Icons.person_add_alt_1_outlined,
            title: 'Aucun contact trouvé',
            body: 'Les personnes présentes sur WAPI apparaîtront ici.',
          );
        }
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
            final doc = contacts[index - 1];
            final item = doc.data();
            final name =
                ((item['displayName'] as String?)?.trim().isNotEmpty == true
                ? item['displayName'] as String
                : (item['phoneNumber'] as String?) ?? 'Membre WAPI');
            final phone = (item['phoneNumber'] as String?) ?? '';
            return ListTile(
              leading: CircleAvatar(
                backgroundColor: WapiColors.blueSoft,
                foregroundColor: WapiColors.blue,
                backgroundImage:
                    (item['photoUrl'] as String?)?.isNotEmpty == true
                    ? NetworkImage(item['photoUrl'] as String)
                    : null,
                child: (item['photoUrl'] as String?)?.isNotEmpty == true
                    ? null
                    : Text(name.substring(0, 1).toUpperCase()),
              ),
              title: Text(
                name,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              subtitle: Text(phone.isEmpty ? 'Profil WAPI' : phone),
              trailing: const Icon(Icons.chat_bubble_outline),
              onTap: () => _openContact(
                context,
                uid: doc.id,
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
            ),
          ),
        ),
      );
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Conversation impossible : $error')),
        );
      }
    }
  }

  Future<void> _showMyQr(BuildContext context) => showModalBottomSheet<void>(
    context: context,
    builder: (sheetContext) => SafeArea(
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
            const Text('Scannez ce code pour ouvrir une discussion avec vous.'),
            const SizedBox(height: 20),
            QrImageView(
              data: 'wapi://contact/${user.uid}',
              version: QrVersions.auto,
              size: 236,
              eyeStyle: const QrEyeStyle(
                eyeShape: QrEyeShape.square,
                color: WapiColors.blue,
              ),
              dataModuleStyle: const QrDataModuleStyle(
                dataModuleShape: QrDataModuleShape.square,
                color: WapiColors.ink,
              ),
            ),
          ],
        ),
      ),
    ),
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
              label: const Text('Scanner un code WAPI'),
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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Ajout impossible : $error')));
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
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'Scanner un code WAPI',
      subtitle: 'Cadrez le QR de votre contact',
    ),
    body: MobileScanner(
      onDetect: (capture) {
        if (_handled) return;
        String? value;
        for (final barcode in capture.barcodes) {
          final candidate = barcode.rawValue;
          if (candidate != null && candidate.startsWith('wapi://contact/')) {
            value = candidate;
            break;
          }
        }
        if (value == null || !value.startsWith('wapi://contact/')) return;
        _handled = true;
        Navigator.of(context).pop(value);
      },
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
          SnackBar(content: Text('Consentement non enregistré : $error')),
        );
      }
    }
  }
}

class _InboxPage extends StatelessWidget {
  const _InboxPage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: 'Messages',
      subtitle: 'Vos échanges',
      actions: [
        IconButton(
          onPressed: () => _createGroup(context),
          icon: const Icon(Icons.group_add_outlined),
          tooltip: 'Nouveau groupe',
        ),
      ],
    ),
    body: StreamBuilder<List<WapiConversation>>(
      stream: repository.conversations(user.uid),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off,
            title: 'Messages indisponibles',
            body: 'Vérifiez votre connexion ou les règles Firestore.',
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
        return ListView.separated(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: conversations.length,
          separatorBuilder: (_, _) => const Divider(height: 1, indent: 80),
          itemBuilder: (context, index) {
            final item = conversations[index];
            return ListTile(
              leading: CircleAvatar(
                radius: 26,
                backgroundColor: WapiColors.blueSoft,
                foregroundColor: WapiColors.blue,
                backgroundImage: item.avatarUrl.isNotEmpty
                    ? NetworkImage(item.avatarUrl)
                    : null,
                child: item.avatarUrl.isNotEmpty
                    ? null
                    : Icon(
                        item.isGroup
                            ? Icons.groups_2_outlined
                            : Icons.person_outline,
                      ),
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
                      style: const TextStyle(
                        color: WapiColors.muted,
                        fontSize: 11,
                      ),
                    ),
                  const SizedBox(height: 5),
                  if (item.unread) const Badge(smallSize: 9),
                ],
              ),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => _ChatPage(
                    user: user,
                    repository: repository,
                    conversation: item,
                  ),
                ),
              ),
            );
          },
        );
      },
    ),
  );

  Future<void> _createGroup(BuildContext context) async {
    final title = TextEditingController();
    final selected = <String>{};
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(sheetContext).height * .78,
          child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
            stream: FirebaseFirestore.instance
                .collection('users')
                .limit(100)
                .snapshots(),
            builder: (context, snapshot) {
              final contacts =
                  snapshot.data?.docs
                      .where((doc) => doc.id != user.uid)
                      .toList() ??
                  const <QueryDocumentSnapshot<Map<String, dynamic>>>[];
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
                                final data = contact.data();
                                final name =
                                    (data['displayName'] as String?)
                                            ?.trim()
                                            .isNotEmpty ==
                                        true
                                    ? data['displayName'] as String
                                    : (data['phoneNumber'] as String?) ??
                                          'Membre WAPI';
                                return CheckboxListTile(
                                  value: selected.contains(contact.id),
                                  onChanged: (value) => setSheetState(() {
                                    value == true
                                        ? selected.add(contact.id)
                                        : selected.remove(contact.id);
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
                                      .where((doc) => selected.contains(doc.id))
                                      .map((doc) {
                                        final data = doc.data();
                                        return <String, String>{
                                          'uid': doc.id,
                                          'displayName':
                                              (data['displayName']
                                                  as String?) ??
                                              (data['phoneNumber']
                                                  as String?) ??
                                              'Membre WAPI',
                                          'phoneNumber':
                                              (data['phoneNumber']
                                                  as String?) ??
                                              '',
                                          'photoUrl':
                                              (data['photoUrl'] as String?) ??
                                              '',
                                        };
                                      })
                                      .toList();
                                  try {
                                    await repository.createGroup(
                                      user: user,
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
  final _composer = TextEditingController();
  final _picker = ImagePicker();
  final _recorder = AudioRecorder();
  final _player = AudioPlayer();
  final _messageScrollController = ScrollController();
  StreamSubscription<List<WapiMessage>>? _messagesSubscription;
  bool _sending = false;
  bool _recording = false;
  bool _markingRead = false;
  bool _markReadQueued = false;
  String? _latestIncomingMessageId;
  String? _lastRenderedMessageId;
  bool _positionedInitialMessages = false;
  WapiMessage? _replyingTo;
  @override
  void initState() {
    super.initState();
    _markConversationRead();
    _messagesSubscription = widget.repository
        .messages(widget.conversation.id)
        .listen(_markVisibleMessagesRead);
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
    _messageScrollController.dispose();
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
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Message non envoyé : $error')));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _pickMedia({required bool video}) async {
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
      );
      _composer.clear();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Média non envoyé : $error')));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _toggleRecording() async {
    if (_recording) {
      final path = await _recorder.stop();
      if (mounted) setState(() => _recording = false);
      SystemSound.play(SystemSoundType.click);
      if (path == null) return;
      setState(() => _sending = true);
      try {
        await widget.repository.sendMediaMessage(
          conversationId: widget.conversation.id,
          user: widget.user,
          file: File(path),
          kind: 'audio',
          contentType: 'audio/mp4',
          fileName: 'note-vocale-${DateTime.now().millisecondsSinceEpoch}.m4a',
        );
      } catch (error) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Note vocale non envoyée : $error')),
          );
        }
      } finally {
        if (mounted) setState(() => _sending = false);
      }
      return;
    }
    if (!await _recorder.hasPermission()) {
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
    final directory = await getTemporaryDirectory();
    await _recorder.start(
      const RecordConfig(encoder: AudioEncoder.aacLc),
      path:
          '${directory.path}/wapi-${DateTime.now().millisecondsSinceEpoch}.m4a',
    );
    if (mounted) setState(() => _recording = true);
    SystemSound.play(SystemSoundType.click);
  }

  Future<void> _playAudio(String url) async {
    await _player.setUrl(url);
    await _player.play();
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
            SnackBar(content: Text('Réaction non enregistrée : $error')),
          );
        }
      }
      return;
    }
    if (action == 'reply') {
      setState(() => _replyingTo = message);
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
      title: Row(
        children: [
          CircleAvatar(
            radius: 19,
            backgroundColor: WapiColors.blueSoft,
            backgroundImage: widget.conversation.avatarUrl.isNotEmpty
                ? NetworkImage(widget.conversation.avatarUrl)
                : null,
            child: widget.conversation.avatarUrl.isNotEmpty
                ? null
                : Icon(
                    widget.conversation.isGroup
                        ? Icons.groups_2_outlined
                        : Icons.person_outline,
                    color: WapiColors.blue,
                  ),
          ),
          const SizedBox(width: 10),
          Expanded(child: _chatTitle()),
        ],
      ),
      actions: widget.conversation.isGroup
          ? [
              IconButton(
                onPressed: _editGroup,
                icon: const Icon(Icons.info_outline),
                tooltip: 'Informations du groupe',
              ),
            ]
          : [
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
    ),
    body: Column(
      children: [
        Expanded(
          child: StreamBuilder<List<WapiMessage>>(
            stream: widget.repository.messages(widget.conversation.id),
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return const _StateMessage(
                  icon: Icons.cloud_off,
                  title: 'Conversation indisponible',
                  body: 'WAPI ne peut pas charger ces messages.',
                );
              }
              if (!snapshot.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              final messages = snapshot.data!;
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
                  return Align(
                    alignment: mine
                        ? Alignment.centerRight
                        : Alignment.centerLeft,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (!mine) ...[
                          CircleAvatar(
                            radius: 17,
                            backgroundColor: WapiColors.blueSoft,
                            backgroundImage:
                                widget
                                        .conversation
                                        .memberPhotoUrls[message.senderId]
                                        ?.isNotEmpty ==
                                    true
                                ? NetworkImage(
                                    widget.conversation.memberPhotoUrls[message
                                        .senderId]!,
                                  )
                                : null,
                            child:
                                widget
                                        .conversation
                                        .memberPhotoUrls[message.senderId]
                                        ?.isNotEmpty ==
                                    true
                                ? null
                                : const Icon(
                                    Icons.person_outline,
                                    color: WapiColors.blue,
                                    size: 19,
                                  ),
                          ),
                          const SizedBox(width: 8),
                        ],
                        InkWell(
                          onLongPress: () => _messageActions(message),
                          borderRadius: BorderRadius.circular(16),
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
                              color: mine ? WapiColors.blue : Colors.white,
                              borderRadius: BorderRadius.circular(16),
                              border: mine
                                  ? null
                                  : Border.all(color: WapiColors.line),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                if (!mine && widget.conversation.isGroup) ...[
                                  Text(
                                    widget.conversation.memberNames[message
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
                                    margin: const EdgeInsets.only(bottom: 8),
                                    padding: const EdgeInsets.all(8),
                                    decoration: BoxDecoration(
                                      color: mine
                                          ? Colors.white.withValues(alpha: .16)
                                          : WapiColors.blueSoft,
                                      borderRadius: BorderRadius.circular(10),
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
                                if (message.kind == 'image' &&
                                    message.mediaUrl.isNotEmpty)
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(12),
                                    child: Image.network(
                                      message.mediaUrl,
                                      width: 250,
                                      fit: BoxFit.cover,
                                    ),
                                  ),
                                if (message.kind == 'video')
                                  InkWell(
                                    onTap: () => Navigator.of(context).push(
                                      MaterialPageRoute(
                                        builder: (_) => _WapiVideoPlayer(
                                          url: message.mediaUrl,
                                          title: message.mediaName.isEmpty
                                              ? 'Vidéo WAPI'
                                              : message.mediaName,
                                        ),
                                      ),
                                    ),
                                    borderRadius: BorderRadius.circular(12),
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
                                  InkWell(
                                    onTap: () => _playAudio(message.mediaUrl),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        Icon(
                                          Icons.play_arrow_rounded,
                                          color: mine
                                              ? Colors.white
                                              : WapiColors.blue,
                                        ),
                                        const SizedBox(width: 6),
                                        Text(
                                          message.durationSeconds > 0
                                              ? '${message.durationSeconds}s · Note vocale'
                                              : 'Écouter la note vocale',
                                          style: TextStyle(
                                            color: mine
                                                ? Colors.white
                                                : WapiColors.ink,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                if (message.kind == 'text' ||
                                    message.text.isNotEmpty &&
                                        message.text != 'Photo' &&
                                        message.text != 'Vidéo' &&
                                        message.text != 'Message vocal') ...[
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
                                                  ? Colors.white.withValues(
                                                      alpha: .18,
                                                    )
                                                  : WapiColors.blueSoft,
                                              borderRadius:
                                                  BorderRadius.circular(10),
                                            ),
                                            child: Padding(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    horizontal: 6,
                                                    vertical: 3,
                                                  ),
                                              child: Text(
                                                emoji,
                                                style: const TextStyle(
                                                  fontSize: 12,
                                                ),
                                              ),
                                            ),
                                          ),
                                        )
                                        .toList(),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              );
            },
          ),
        ),
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
                Row(
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
                                        Icons.videocam_outlined,
                                      ),
                                      title: const Text('Vidéo'),
                                      onTap: () {
                                        Navigator.pop(sheetContext);
                                        _pickMedia(video: true);
                                      },
                                    ),
                                  ],
                                ),
                              ),
                            ),
                      icon: const Icon(Icons.add_circle_outline),
                      tooltip: 'Joindre',
                    ),
                    Expanded(
                      child: TextField(
                        controller: _composer,
                        minLines: 1,
                        maxLines: 5,
                        textInputAction: TextInputAction.send,
                        onSubmitted: (_) => _send(),
                        decoration: const InputDecoration(hintText: 'Message'),
                      ),
                    ),
                    IconButton(
                      onPressed: _sending ? null : _toggleRecording,
                      icon: Icon(
                        _recording
                            ? Icons.stop_circle_outlined
                            : Icons.mic_none,
                      ),
                      color: _recording ? Colors.red : WapiColors.blue,
                      tooltip: _recording
                          ? 'Arrêter et envoyer'
                          : 'Note vocale',
                    ),
                    IconButton(
                      onPressed: _sending ? null : _send,
                      icon: _sending
                          ? const SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.send),
                      color: WapiColors.blue,
                      tooltip: 'Envoyer',
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
            Text(widget.conversation.title),
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
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => WapiCallPage.outgoing(
          user: widget.user,
          peerId: widget.conversation.peerId,
          peerName: widget.conversation.title,
          video: video,
        ),
      ),
    );
  }

  Future<void> _editGroup() async {
    final title = TextEditingController(text: widget.conversation.title);
    File? photo;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheetState) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            20,
            20,
            MediaQuery.viewInsetsOf(sheetContext).bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Informations du groupe',
                style: Theme.of(sheetContext).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
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
                  backgroundImage: photo == null ? null : FileImage(photo!),
                  child: photo == null
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
                decoration: const InputDecoration(labelText: 'Nom du groupe'),
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
                            content: Text('Modification impossible : $error'),
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
    );
    title.dispose();
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

class _UpdatesPage extends StatelessWidget {
  const _UpdatesPage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: _WapiAppBar(
      title: 'Actus',
      subtitle: 'Statuts de vos contacts',
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
            body: 'Vérifiez votre connexion ou les règles Firestore.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final stories = snapshot.data!;
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
          children: [
            _StoryPublisher(onTap: () => _composeStory(context)),
            const SizedBox(height: 20),
            Text(
              'Récentes',
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
                (story) => Card(
                  margin: const EdgeInsets.only(bottom: 10),
                  child: ListTile(
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (_) => _StoryViewer(story: story),
                      ),
                    ),
                    leading: CircleAvatar(
                      backgroundColor: WapiColors.blueSoft,
                      foregroundColor: WapiColors.blue,
                      child: Text(
                        story.authorName.substring(0, 1).toUpperCase(),
                      ),
                    ),
                    title: Text(
                      story.authorName,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    subtitle: Text(
                      story.text.isEmpty ? 'Photo ou vidéo' : story.text,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: const Icon(Icons.chevron_right),
                  ),
                ),
              ),
          ],
        );
      },
    ),
  );
  void _composeStory(BuildContext context) {
    final controller = TextEditingController();
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
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Nouveau statut',
              style: Theme.of(
                sheetContext,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            const Text('Visible pendant 24 heures.'),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              maxLength: 600,
              minLines: 3,
              maxLines: 6,
              autofocus: true,
              decoration: const InputDecoration(
                hintText: 'Partagez une pensée…',
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(sheetContext);
                    _publishStoryMedia(context, controller.text, video: false);
                  },
                  icon: const Icon(Icons.photo_outlined),
                  label: const Text('Photo'),
                ),
                const SizedBox(width: 10),
                OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(sheetContext);
                    _publishStoryMedia(context, controller.text, video: true);
                  },
                  icon: const Icon(Icons.videocam_outlined),
                  label: const Text('Vidéo'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: () async {
                  try {
                    final story = await repository.publishTextStory(
                      user: user,
                      text: controller.text,
                    );
                    if (sheetContext.mounted) {
                      Navigator.pop(sheetContext);
                    }
                    if (context.mounted) {
                      await Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => _StoryViewer(story: story),
                        ),
                      );
                    }
                  } catch (error) {
                    if (sheetContext.mounted) {
                      ScaffoldMessenger.of(sheetContext).showSnackBar(
                        SnackBar(
                          content: Text('Publication impossible : $error'),
                        ),
                      );
                    }
                  }
                },
                icon: const Icon(Icons.send),
                label: const Text('Publier'),
              ),
            ),
          ],
        ),
      ),
    ).whenComplete(controller.dispose);
  }

  Future<void> _publishStoryMedia(
    BuildContext context,
    String caption, {
    required bool video,
  }) async {
    final picker = ImagePicker();
    final media = video
        ? await picker.pickVideo(source: ImageSource.gallery)
        : await picker.pickImage(source: ImageSource.gallery, imageQuality: 92);
    if (media == null) return;
    try {
      final story = await repository.publishMediaStory(
        user: user,
        file: File(media.path),
        mediaType: video ? 'video' : 'image',
        contentType: video ? 'video/mp4' : 'image/jpeg',
        fileName: media.name,
        caption: caption,
      );
      if (context.mounted) {
        await Navigator.of(
          context,
        ).push(MaterialPageRoute(builder: (_) => _StoryViewer(story: story)));
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Story non publiée : $error')));
      }
    }
  }
}

class _WapiVideoPlayer extends StatefulWidget {
  const _WapiVideoPlayer({
    required this.url,
    required this.title,
    this.embedded = false,
  });
  final String url;
  final String title;
  final bool embedded;

  @override
  State<_WapiVideoPlayer> createState() => _WapiVideoPlayerState();
}

class _WapiVideoPlayerState extends State<_WapiVideoPlayer> {
  late final VideoPlayerController _controller;
  late final Future<void> _ready;

  @override
  void initState() {
    super.initState();
    _controller = VideoPlayerController.networkUrl(Uri.parse(widget.url));
    _ready = _controller.initialize().then((_) {
      _controller.setLooping(false);
      _controller.play();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final player = Center(
      child: FutureBuilder<void>(
        future: _ready,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return const _StoryFallback(
              icon: Icons.error_outline,
              label: 'Lecture vidéo impossible',
            );
          }
          if (snapshot.connectionState != ConnectionState.done) {
            return const CircularProgressIndicator(color: Colors.white);
          }
          return AspectRatio(
            aspectRatio: _controller.value.aspectRatio,
            child: Stack(
              alignment: Alignment.center,
              children: [
                VideoPlayer(_controller),
                IconButton.filledTonal(
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
              ],
            ),
          );
        },
      ),
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

class _StoryViewer extends StatelessWidget {
  const _StoryViewer({required this.story});

  final WapiStory story;

  @override
  Widget build(BuildContext context) => GestureDetector(
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
              top: 12,
              left: 16,
              right: 16,
              child: Row(
                children: [
                  CircleAvatar(
                    backgroundColor: Colors.white24,
                    foregroundColor: Colors.white,
                    child: Text(story.authorName.substring(0, 1).toUpperCase()),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      story.authorName,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close, color: Colors.white),
                    tooltip: 'Fermer',
                  ),
                ],
              ),
            ),
            Positioned(
              left: 24,
              right: 24,
              bottom: 32,
              child: Text(
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
      return Image.network(
        story.mediaUrl,
        fit: BoxFit.contain,
        errorBuilder: (_, _, _) => const _StoryFallback(
          icon: Icons.broken_image_outlined,
          label: 'Image indisponible',
        ),
      );
    }
    if (story.mediaType == 'video') {
      return _WapiVideoPlayer(
        url: story.mediaUrl,
        title: story.text.isEmpty ? 'Statut vidéo' : story.text,
        embedded: true,
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

class _StoryPublisher extends StatelessWidget {
  const _StoryPublisher({required this.onTap});
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(18),
    child: Ink(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: WapiColors.blueSoft,
        borderRadius: BorderRadius.circular(18),
      ),
      child: const Row(
        children: [
          CircleAvatar(
            backgroundColor: WapiColors.blue,
            foregroundColor: Colors.white,
            child: Icon(Icons.add),
          ),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Mon statut',
                  style: TextStyle(fontWeight: FontWeight.w800),
                ),
                SizedBox(height: 2),
                Text(
                  'Publier un texte maintenant',
                  style: TextStyle(color: WapiColors.muted),
                ),
              ],
            ),
          ),
          Icon(Icons.chevron_right, color: WapiColors.blue),
        ],
      ),
    ),
  );
}

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
                            content: Text('Création impossible : $error'),
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

class _LivePage extends StatelessWidget {
  const _LivePage();
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const _WapiAppBar(
      title: 'En direct',
      subtitle: 'Lives réellement disponibles',
    ),
    body: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('liveSessions')
          .where('status', whereIn: const ['scheduled', 'live'])
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const _StateMessage(
            icon: Icons.cloud_off_outlined,
            title: 'Lives indisponibles',
            body: 'WAPI ne peut pas charger les directs pour le moment.',
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final lives = snapshot.data!.docs;
        if (lives.isEmpty) {
          return const _StateMessage(
            icon: Icons.live_tv_outlined,
            title: 'Aucun direct disponible',
            body:
                'Un live apparaîtra ici seulement lorsqu’un flux vidéo sécurisé aura été réellement provisionné.',
          );
        }
        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: lives.length,
          itemBuilder: (context, index) {
            final live = lives[index].data();
            final provisioned =
                live['streamProvider'] != 'unconfigured' &&
                (live['streamRoomId'] as String? ?? '').isNotEmpty;
            return Card(
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: provisioned
                      ? Colors.red.shade50
                      : WapiColors.blueSoft,
                  foregroundColor: provisioned ? Colors.red : WapiColors.blue,
                  child: Icon(provisioned ? Icons.live_tv : Icons.schedule),
                ),
                title: Text(
                  (live['title'] as String?) ?? 'Live WAPI',
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                subtitle: Text(
                  provisioned
                      ? 'Disponible maintenant'
                      : 'Programmation en cours',
                ),
              ),
            );
          },
        );
      },
    ),
  );
}

class _ProfilePage extends StatelessWidget {
  const _ProfilePage({required this.user, required this.repository});
  final User user;
  final WapiRepository repository;
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
        final photoUrl = (data['photoUrl'] as String?) ?? user.photoURL ?? '';
        final isFounder =
            phone.replaceAll(RegExp(r'[^0-9+]'), '') == '+242065465808';
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            Card(
              child: ListTile(
                contentPadding: const EdgeInsets.all(16),
                leading: InkWell(
                  onTap: () => _changePhoto(context),
                  borderRadius: BorderRadius.circular(30),
                  child: CircleAvatar(
                    radius: 28,
                    backgroundColor: WapiColors.blueSoft,
                    foregroundColor: WapiColors.blue,
                    backgroundImage: photoUrl.isEmpty
                        ? null
                        : NetworkImage(photoUrl),
                    child: photoUrl.isEmpty
                        ? Text(
                            name.substring(0, 1).toUpperCase(),
                            style: const TextStyle(fontWeight: FontWeight.w800),
                          )
                        : const Align(
                            alignment: Alignment.bottomRight,
                            child: CircleAvatar(
                              radius: 10,
                              child: Icon(Icons.camera_alt, size: 12),
                            ),
                          ),
                  ),
                ),
                title: Row(
                  children: [
                    Flexible(
                      child: Text(
                        name,
                        style: const TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 18,
                        ),
                      ),
                    ),
                    const SizedBox(width: 5),
                    const Icon(
                      Icons.verified,
                      color: Color(0xFF87909D),
                      size: 18,
                    ),
                  ],
                ),
                subtitle: Text(phone.isEmpty ? 'Profil à compléter' : phone),
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
              title: 'Compte',
              icon: Icons.manage_accounts_outlined,
              detail: 'Identité, confidentialité et appareils',
              onTap: () => _editName(context, name),
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
    try {
      await repository.uploadProfilePhoto(
        user: user,
        file: File(image.path),
        contentType: 'image/jpeg',
      );
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Photo non enregistrée : $error')),
        );
      }
    }
  }

  Future<void> _editName(BuildContext context, String currentName) async {
    final controller = TextEditingController(text: currentName);
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
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Modifier le profil',
              style: Theme.of(
                sheetContext,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              autofocus: true,
              maxLength: 80,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(labelText: 'Nom affiché'),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton(
                onPressed: () async {
                  try {
                    await repository.updateProfileName(
                      user: user,
                      displayName: controller.text,
                    );
                    if (sheetContext.mounted) Navigator.pop(sheetContext);
                  } catch (error) {
                    if (sheetContext.mounted) {
                      ScaffoldMessenger.of(sheetContext).showSnackBar(
                        SnackBar(content: Text('Nom non enregistré : $error')),
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
    );
    controller.dispose();
  }

  void _showNotificationSettings(BuildContext context) {
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
                'Notifications',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
              ),
              SizedBox(height: 12),
              Text(
                'Les messages et appels utilisent les permissions Android et le jeton sécurisé de cet appareil. Désactivez-les depuis les réglages Android si vous ne souhaitez plus les recevoir.',
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
                'Les messages restent dans votre conversation. Les médias sont stockés dans Firebase Storage avec accès limité aux membres de la conversation. Le cache local est géré par Android et peut être vidé depuis les réglages de l’application.',
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
                'Les opérations sensibles restent protégées côté Firebase et ne sont pas accessibles depuis un simple écran mobile.',
              ),
            ],
          ),
        ),
      ),
    );
  }
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
