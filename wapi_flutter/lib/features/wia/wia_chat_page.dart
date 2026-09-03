import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../../app/wapi_theme.dart';

/// WIA uses the same account-owned Pilotis thread on every WAPI device.
/// No local fallback is generated when the secure provider is unavailable.
class WiaChatPage extends StatefulWidget {
  const WiaChatPage({super.key, required this.user});
  final User user;

  @override
  State<WiaChatPage> createState() => _WiaChatPageState();
}

class _WiaChatPageState extends State<WiaChatPage> {
  final _functions = FirebaseFunctions.instanceFor(region: 'europe-west1');
  final _composer = TextEditingController();
  final _scroll = ScrollController();
  List<_WiaMessage> _messages = const [];
  bool _loading = true;
  bool _sending = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _composer.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await _functions
          .httpsCallable('getWepiHistory')
          .call<Map<String, dynamic>>({'threadId': 'main'})
          .timeout(const Duration(seconds: 15));
      final data = result.data;
      final items = (data['messages'] as List? ?? const [])
          .whereType<Map>()
          .map((raw) => Map<String, dynamic>.from(raw))
          .map(_WiaMessage.fromMap)
          .toList(growable: false);
      if (mounted) setState(() => _messages = items);
    } catch (error) {
      if (mounted) setState(() => _error = _errorText(error));
    } finally {
      if (mounted) setState(() => _loading = false);
      _scrollToBottom();
    }
  }

  Future<void> _send() async {
    final prompt = _composer.text.trim();
    if (prompt.isEmpty || _sending) return;
    final localUser = _WiaMessage(
      id: 'local-' + DateTime.now().microsecondsSinceEpoch.toString(),
      fromUser: true,
      text: prompt,
      pending: true,
    );
    final previous = _messages;
    setState(() {
      _messages = [...previous, localUser];
      _sending = true;
      _error = null;
      _composer.clear();
    });
    _scrollToBottom();
    try {
      final history = previous
          .takeLast(6)
          .map(
            (message) => {'fromUser': message.fromUser, 'text': message.text},
          )
          .toList(growable: false);
      final result = await _functions
          .httpsCallable('askWepi')
          .call<Map<String, dynamic>>({
            'threadId': 'main',
            'messageId':
                'wia-' + DateTime.now().microsecondsSinceEpoch.toString(),
            'prompt': prompt,
            'history': history,
          })
          .timeout(const Duration(seconds: 30));
      final answer = (result.data['text'] as String? ?? '').trim();
      if (answer.isEmpty) throw StateError('Réponse WIA invalide.');
      if (!mounted) return;
      setState(() {
        _messages = [
          ...previous,
          _WiaMessage(id: localUser.id, fromUser: true, text: prompt),
          _WiaMessage(
            id: 'assistant-' + DateTime.now().microsecondsSinceEpoch.toString(),
            fromUser: false,
            text: answer,
          ),
        ];
      });
    } catch (error) {
      if (mounted) {
        setState(() {
          _messages = previous;
          _error = _errorText(error);
          _composer.text = prompt;
          _composer.selection = TextSelection.collapsed(
            offset: _composer.text.length,
          );
        });
      }
    } finally {
      if (mounted) setState(() => _sending = false);
      _scrollToBottom();
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.animateTo(
        _scroll.position.maxScrollExtent,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('WIA', style: TextStyle(fontWeight: FontWeight.w900)),
          Text(
            'Même mémoire que Pilotis',
            style: TextStyle(fontSize: 11, color: WapiColors.muted),
          ),
        ],
      ),
      actions: [
        IconButton(
          onPressed: _loading ? null : _loadHistory,
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Actualiser',
        ),
      ],
    ),
    body: Column(
      children: [
        Container(
          width: double.infinity,
          margin: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          padding: const EdgeInsets.all(13),
          decoration: BoxDecoration(
            color: const Color(0xFFEAF8F4),
            borderRadius: BorderRadius.circular(16),
          ),
          child: const Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.auto_awesome_rounded, color: Color(0xFF087D62)),
              SizedBox(width: 9),
              Expanded(
                child: Text(
                  'WIA utilise la conversation de votre compte Pilotis. Il ne simule pas de réponse quand le service sécurisé est indisponible.',
                  style: TextStyle(
                    color: Color(0xFF176451),
                    fontSize: 12,
                    height: 1.35,
                  ),
                ),
              ),
            ],
          ),
        ),
        Expanded(child: _conversation()),
        if (_error != null)
          Container(
            width: double.infinity,
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFFFFEDEB),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Text(
              _error!,
              style: const TextStyle(color: Color(0xFFB42318)),
            ),
          ),
        _composerBar(),
      ],
    ),
  );

  Widget _conversation() {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_messages.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: const [
          SizedBox(height: 70),
          Icon(Icons.auto_awesome_rounded, size: 48, color: Color(0xFF087D62)),
          SizedBox(height: 16),
          Text(
            'Bonjour, je suis WIA.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
          ),
          SizedBox(height: 7),
          Text(
            'Je peux vous aider à écrire, organiser votre Business, préparer une annonce et utiliser WAPI.',
            textAlign: TextAlign.center,
            style: TextStyle(color: WapiColors.muted, height: 1.4),
          ),
        ],
      );
    }
    return ListView.builder(
      controller: _scroll,
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      itemCount: _messages.length + (_sending ? 1 : 0),
      itemBuilder: (context, index) {
        if (index == _messages.length) return const _WiaTyping();
        return _WiaBubble(message: _messages[index]);
      },
    );
  }

  Widget _composerBar() => SafeArea(
    top: false,
    child: Container(
      padding: const EdgeInsets.fromLTRB(12, 9, 12, 10),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: WapiColors.line)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: TextField(
              controller: _composer,
              minLines: 1,
              maxLines: 5,
              maxLength: 4000,
              textInputAction: TextInputAction.newline,
              decoration: const InputDecoration(
                counterText: '',
                hintText: 'Écrire à WIA…',
              ),
            ),
          ),
          const SizedBox(width: 8),
          IconButton.filled(
            onPressed: _sending ? null : _send,
            icon: _sending
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.arrow_upward_rounded),
            tooltip: 'Envoyer',
          ),
        ],
      ),
    ),
  );
}

class _WiaBubble extends StatelessWidget {
  const _WiaBubble({required this.message});
  final _WiaMessage message;
  @override
  Widget build(BuildContext context) => Align(
    alignment: message.fromUser ? Alignment.centerRight : Alignment.centerLeft,
    child: Container(
      constraints: const BoxConstraints(maxWidth: 330),
      margin: const EdgeInsets.only(bottom: 9),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
      decoration: BoxDecoration(
        color: message.fromUser ? const Color(0xFF008E72) : Colors.white,
        borderRadius: BorderRadius.only(
          topLeft: const Radius.circular(18),
          topRight: const Radius.circular(18),
          bottomLeft: Radius.circular(message.fromUser ? 18 : 4),
          bottomRight: Radius.circular(message.fromUser ? 4 : 18),
        ),
        border: message.fromUser ? null : Border.all(color: WapiColors.line),
      ),
      child: Text(
        message.text,
        style: TextStyle(
          color: message.fromUser ? Colors.white : const Color(0xFF18252D),
          height: 1.35,
        ),
      ),
    ),
  );
}

class _WiaTyping extends StatelessWidget {
  const _WiaTyping();
  @override
  Widget build(BuildContext context) => const Align(
    alignment: Alignment.centerLeft,
    child: Padding(
      padding: EdgeInsets.only(top: 4),
      child: SizedBox(
        width: 42,
        height: 24,
        child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
      ),
    ),
  );
}

class _WiaMessage {
  const _WiaMessage({
    required this.id,
    required this.fromUser,
    required this.text,
    this.pending = false,
  });
  factory _WiaMessage.fromMap(Map<String, dynamic> value) => _WiaMessage(
    id: (value['id'] as String?) ?? '',
    fromUser: value['role'] == 'user',
    text: (value['content'] as String?) ?? '',
  );
  final String id;
  final bool fromUser;
  final String text;
  final bool pending;
}

extension _TakeLast<T> on List<T> {
  List<T> takeLast(int count) =>
      length <= count ? this : sublist(length - count);
}

String _errorText(Object error) => wapiErrorText(
  error is FirebaseFunctionsException ? error.message : error,
  fallback:
      'WIA ne peut pas répondre pour le moment. Réessayez dans un instant.',
);
