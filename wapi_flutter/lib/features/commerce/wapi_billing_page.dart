import 'package:cloud_functions/cloud_functions.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/wapi_theme.dart';

class WapiBillingPage extends StatefulWidget {
  const WapiBillingPage({super.key, required this.pages});
  final List<Map<String, dynamic>> pages;

  @override
  State<WapiBillingPage> createState() => _WapiBillingPageState();
}

class _WapiBillingPageState extends State<WapiBillingPage> {
  final _api = _BillingApi();
  late String _pageId = _text(widget.pages.first['id']);
  bool _loading = true;
  String? _error;
  List<Map<String, dynamic>> _products = const [];
  List<Map<String, dynamic>> _invoices = const [];
  Map<String, dynamic> _summary = const {};

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
      final result = await _api.call('billing', {'pageId': _pageId});
      if (!mounted) return;
      setState(() {
        _products = _maps(result['products']);
        _invoices = _maps(result['invoices']);
        _summary = _map(result['summary']);
      });
    } catch (error) {
      if (mounted) setState(() => _error = _errorText(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _newProduct() async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => _ProductForm(api: _api, pageId: _pageId),
    );
    if (saved == true) await _refresh();
  }

  Future<void> _newInvoice() async {
    if (_products.isEmpty) {
      _toast(
        'Ajoutez d’abord un produit ou service à votre catalogue.',
        error: true,
      );
      await _newProduct();
      return;
    }
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => _InvoiceForm(
          api: _api,
          pageId: _pageId,
          pageName: _text(
            widget.pages.firstWhere(
              (page) => _text(page['id']) == _pageId,
            )['name'],
            'Business WAPI',
          ),
          products: _products,
        ),
      ),
    );
    if (saved == true) await _refresh();
  }

  Future<void> _remind(Map<String, dynamic> invoice) async {
    try {
      final result = await _api.call('prepareInvoiceReminder', {
        'invoiceId': _text(invoice['id']),
        'tone': 'courtois',
      });
      final reminder = _map(result['reminder']);
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        useSafeArea: true,
        builder: (sheetContext) => Padding(
          padding: const EdgeInsets.fromLTRB(20, 22, 20, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Relance préparée',
                style: TextStyle(fontSize: 23, fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 10),
              Text(
                _text(
                  result['notice'],
                  'Vérifiez ce message avant de l’envoyer au client.',
                ),
                style: const TextStyle(color: WapiColors.muted),
              ),
              const SizedBox(height: 18),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFEAF8F4),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(_text(reminder['message'])),
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: () async {
                  await Clipboard.setData(
                    ClipboardData(text: _text(reminder['message'])),
                  );
                  if (sheetContext.mounted) Navigator.pop(sheetContext);
                  _toast(
                    'Relance copiée. Vous gardez la décision de l’envoyer.',
                  );
                },
                icon: const Icon(Icons.copy_rounded),
                label: const Text('Copier la relance'),
              ),
            ],
          ),
        ),
      );
    } catch (error) {
      _toast(_errorText(error), error: true);
    }
  }

  void _toast(String message, {bool error = false}) {
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
        'Facturation Business',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
      actions: [
        IconButton(
          onPressed: _loading ? null : _refresh,
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Actualiser',
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 30),
        children: [
          DropdownButtonFormField<String>(
            initialValue: _pageId,
            decoration: const InputDecoration(
              labelText: 'Compte Business',
              prefixIcon: Icon(Icons.storefront_outlined),
            ),
            items: widget.pages
                .map(
                  (page) => DropdownMenuItem(
                    value: _text(page['id']),
                    child: Text(_text(page['name'], 'Business WAPI')),
                  ),
                )
                .toList(),
            onChanged: _loading
                ? null
                : (value) {
                    if (value == null || value == _pageId) return;
                    setState(() => _pageId = value);
                    _refresh();
                  },
          ),
          const SizedBox(height: 18),
          const Text(
            'Créances et catalogue',
            style: TextStyle(
              fontSize: 25,
              fontWeight: FontWeight.w900,
              color: Color(0xFF062233),
            ),
          ),
          const SizedBox(height: 5),
          const Text(
            'Vos montants sont calculés par le serveur. Une relance reste toujours soumise à votre validation.',
            style: TextStyle(color: WapiColors.muted),
          ),
          const SizedBox(height: 18),
          if (_loading && _invoices.isEmpty)
            const Padding(
              padding: EdgeInsets.all(42),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (_error != null && _invoices.isEmpty)
            _BillingState(
              icon: Icons.cloud_off_outlined,
              title: 'Facturation indisponible',
              body: _error!,
              action: _refresh,
            )
          else ...[
            Row(
              children: [
                _BillingMetric(
                  label: 'À recevoir',
                  value: _money(
                    _number(_summary['outstandingMinor']),
                    _text(_invoices.firstOrNull?['currency'], 'XAF'),
                  ),
                  color: const Color(0xFF087D62),
                ),
                const SizedBox(width: 10),
                _BillingMetric(
                  label: 'En retard',
                  value: _money(
                    _number(_summary['overdueMinor']),
                    _text(_invoices.firstOrNull?['currency'], 'XAF'),
                  ),
                  color: const Color(0xFFB54708),
                ),
                const SizedBox(width: 10),
                _BillingMetric(
                  label: 'Factures',
                  value: _number(_summary['invoiceCount']).toString(),
                  color: const Color(0xFF2869AF),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _newProduct,
                    icon: const Icon(Icons.inventory_2_outlined),
                    label: const Text('Produit'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _newInvoice,
                    icon: const Icon(Icons.receipt_long_outlined),
                    label: const Text('Facture'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 22),
            Text(
              'Catalogue · ' + _products.length.toString(),
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 10),
            if (_products.isEmpty)
              _BillingState(
                icon: Icons.inventory_2_outlined,
                title: 'Votre catalogue est vide',
                body:
                    'Ajoutez vos produits ou services pour créer des factures fiables.',
                action: _newProduct,
                actionLabel: 'Ajouter un produit',
              )
            else
              ..._products.map((product) => _ProductCard(product: product)),
            const SizedBox(height: 22),
            const Text(
              'Factures',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 10),
            if (_invoices.isEmpty)
              _BillingState(
                icon: Icons.receipt_long_outlined,
                title: 'Aucune facture',
                body: 'Créez une première facture à partir de votre catalogue.',
                action: _newInvoice,
                actionLabel: 'Créer une facture',
              )
            else
              ..._invoices.map(
                (invoice) => _InvoiceCard(
                  invoice: invoice,
                  onRemind: _text(invoice['status']) == 'cancelled'
                      ? null
                      : () => _remind(invoice),
                ),
              ),
          ],
        ],
      ),
    ),
  );
}

class _ProductCard extends StatelessWidget {
  const _ProductCard({required this.product});
  final Map<String, dynamic> product;
  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 9),
    child: ListTile(
      leading: const CircleAvatar(
        backgroundColor: Color(0xFFEAF8F4),
        child: Icon(Icons.inventory_2_outlined, color: Color(0xFF087D62)),
      ),
      title: Text(
        _text(product['name'], 'Produit'),
        style: const TextStyle(fontWeight: FontWeight.w900),
      ),
      subtitle: Text(_text(product['category'], 'Service')),
      trailing: Text(
        _money(
          _number(product['priceMinor']),
          _text(product['currency'], 'XAF'),
        ),
        style: const TextStyle(
          color: Color(0xFF087D62),
          fontWeight: FontWeight.w900,
        ),
      ),
    ),
  );
}

class _InvoiceCard extends StatelessWidget {
  const _InvoiceCard({required this.invoice, this.onRemind});
  final Map<String, dynamic> invoice;
  final VoidCallback? onRemind;
  @override
  Widget build(BuildContext context) {
    final overdue = _text(invoice['status']) == 'overdue';
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    _text(invoice['number'], 'Facture WAPI'),
                    style: const TextStyle(fontWeight: FontWeight.w900),
                  ),
                ),
                _InvoiceState(status: _text(invoice['status'])),
              ],
            ),
            const SizedBox(height: 5),
            Text(
              _text(invoice['customerName'], 'Client WAPI'),
              style: const TextStyle(color: WapiColors.muted),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Solde · ' +
                        _money(
                          _number(invoice['balanceMinor']),
                          _text(invoice['currency'], 'XAF'),
                        ),
                    style: TextStyle(
                      fontWeight: FontWeight.w900,
                      color: overdue
                          ? const Color(0xFFB54708)
                          : const Color(0xFF087D62),
                    ),
                  ),
                ),
                if (onRemind != null)
                  TextButton.icon(
                    onPressed: onRemind,
                    icon: const Icon(
                      Icons.mark_email_unread_outlined,
                      size: 18,
                    ),
                    label: const Text('Relancer'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _InvoiceState extends StatelessWidget {
  const _InvoiceState({required this.status});
  final String status;
  @override
  Widget build(BuildContext context) {
    final color = status == 'overdue'
        ? const Color(0xFFB54708)
        : status == 'cancelled'
        ? const Color(0xFF667085)
        : const Color(0xFF087D62);
    final label = status == 'overdue'
        ? 'EN RETARD'
        : status == 'cancelled'
        ? 'ANNULÉE'
        : 'ÉMISE';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(9),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 10,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class _BillingMetric extends StatelessWidget {
  const _BillingMetric({
    required this.label,
    required this.value,
    required this.color,
  });
  final String label;
  final String value;
  final Color color;
  @override
  Widget build(BuildContext context) => Expanded(
    child: Container(
      padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .09),
        borderRadius: BorderRadius.circular(15),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 11,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w900,
              fontSize: 16,
            ),
          ),
        ],
      ),
    ),
  );
}

class _BillingState extends StatelessWidget {
  const _BillingState({
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
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: const Color(0xFFF6FAF9),
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: WapiColors.line),
    ),
    child: Column(
      children: [
        Icon(icon, size: 32, color: const Color(0xFF087D62)),
        const SizedBox(height: 8),
        Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
        const SizedBox(height: 5),
        Text(
          body,
          textAlign: TextAlign.center,
          style: const TextStyle(color: WapiColors.muted),
        ),
        if (action != null) ...[
          const SizedBox(height: 12),
          OutlinedButton(onPressed: action, child: Text(actionLabel)),
        ],
      ],
    ),
  );
}

class _ProductForm extends StatefulWidget {
  const _ProductForm({required this.api, required this.pageId});
  final _BillingApi api;
  final String pageId;
  @override
  State<_ProductForm> createState() => _ProductFormState();
}

class _ProductFormState extends State<_ProductForm> {
  final _name = TextEditingController();
  final _category = TextEditingController(text: 'Service');
  final _price = TextEditingController();
  final _description = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _name.dispose();
    _category.dispose();
    _price.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final price = int.tryParse(_price.text.trim());
    if (_name.text.trim().length < 2 || price == null || price < 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Saisissez le nom et un prix valide.')),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      await widget.api.call('saveProduct', {
        'pageId': widget.pageId,
        'productId': 'prd-' + DateTime.now().microsecondsSinceEpoch.toString(),
        'name': _name.text.trim(),
        'category': _category.text.trim(),
        'description': _description.text.trim(),
        'priceMinor': price,
        'currency': 'XAF',
        'available': true,
      });
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_errorText(error))));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      20,
      22,
      20,
      MediaQuery.viewInsetsOf(context).bottom + 24,
    ),
    child: ListView(
      shrinkWrap: true,
      children: [
        const Text(
          'Ajouter au catalogue',
          style: TextStyle(fontSize: 23, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _name,
          maxLength: 100,
          decoration: const InputDecoration(labelText: 'Produit ou service'),
        ),
        TextField(
          controller: _category,
          maxLength: 60,
          decoration: const InputDecoration(labelText: 'Catégorie'),
        ),
        TextField(
          controller: _description,
          maxLength: 1000,
          minLines: 2,
          maxLines: 4,
          decoration: const InputDecoration(labelText: 'Description'),
        ),
        TextField(
          controller: _price,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Prix en XAF'),
        ),
        const SizedBox(height: 12),
        FilledButton(
          onPressed: _saving ? null : _save,
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
          child: Text(_saving ? 'Enregistrement…' : 'Ajouter au catalogue'),
        ),
      ],
    ),
  );
}

class _InvoiceForm extends StatefulWidget {
  const _InvoiceForm({
    required this.api,
    required this.pageId,
    required this.pageName,
    required this.products,
  });
  final _BillingApi api;
  final String pageId;
  final String pageName;
  final List<Map<String, dynamic>> products;
  @override
  State<_InvoiceForm> createState() => _InvoiceFormState();
}

class _InvoiceFormState extends State<_InvoiceForm> {
  final _customer = TextEditingController();
  final _note = TextEditingController();
  late final List<_DraftInvoiceLine> _lines = [
    _DraftInvoiceLine(_text(widget.products.first['id'])),
  ];
  DateTime _dueAt = DateTime.now().add(const Duration(days: 7));
  bool _saving = false;

  @override
  void dispose() {
    _customer.dispose();
    _note.dispose();
    for (final line in _lines) {
      line.quantity.dispose();
    }
    super.dispose();
  }

  Future<void> _pickDueDate() async {
    final value = await showDatePicker(
      context: context,
      initialDate: _dueAt,
      firstDate: DateTime.now().add(const Duration(days: 1)),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (value != null && mounted) setState(() => _dueAt = value);
  }

  Future<void> _save() async {
    final items = _lines
        .map(
          (line) => {
            'productId': line.productId,
            'quantity': int.tryParse(line.quantity.text.trim()),
          },
        )
        .toList();
    final invalid = items.any(
      (item) =>
          item['productId'] is! String ||
          (item['productId'] as String).isEmpty ||
          item['quantity'] is! int ||
          (item['quantity'] as int) < 1,
    );
    final duplicateProducts =
        items.map((item) => item['productId']).toSet().length != items.length;
    if (_customer.text.trim().length < 2 || invalid || duplicateProducts) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            duplicateProducts
                ? 'Un produit ne peut figurer qu’une fois sur la facture.'
                : 'Ajoutez le client et des quantités valides.',
          ),
        ),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      await widget.api.call('createInvoice', {
        'invoiceId': 'inv-' + DateTime.now().microsecondsSinceEpoch.toString(),
        'pageId': widget.pageId,
        'customerName': _customer.text.trim(),
        'note': _note.text.trim(),
        'dueAt': _dueAt.millisecondsSinceEpoch,
        'items': items,
      });
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_errorText(error))));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        'Créer une facture',
        style: TextStyle(fontWeight: FontWeight.w900),
      ),
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 30),
      children: [
        Text(
          widget.pageName,
          style: const TextStyle(
            color: WapiColors.muted,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 14),
        TextField(
          controller: _customer,
          maxLength: 100,
          decoration: const InputDecoration(labelText: 'Nom du client'),
        ),
        const Text(
          'Articles',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 8),
        ..._lines.asMap().entries.map((entry) {
          final index = entry.key;
          final line = entry.value;
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: DropdownButtonFormField<String>(
                    value: line.productId,
                    decoration: InputDecoration(
                      labelText: 'Produit ' + (index + 1).toString(),
                    ),
                    items: widget.products
                        .map(
                          (product) => DropdownMenuItem(
                            value: _text(product['id']),
                            child: Text(_text(product['name'], 'Produit')),
                          ),
                        )
                        .toList(),
                    onChanged: _saving
                        ? null
                        : (value) => setState(
                            () => line.productId = value ?? line.productId,
                          ),
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  width: 74,
                  child: TextField(
                    controller: line.quantity,
                    enabled: !_saving,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Qté'),
                  ),
                ),
                if (_lines.length > 1)
                  IconButton(
                    onPressed: _saving
                        ? null
                        : () => setState(() {
                            final removed = _lines.removeAt(index);
                            removed.quantity.dispose();
                          }),
                    icon: const Icon(Icons.remove_circle_outline_rounded),
                    tooltip: 'Retirer la ligne',
                  ),
              ],
            ),
          );
        }),
        OutlinedButton.icon(
          onPressed: _saving || _lines.length >= 30
              ? null
              : () => setState(
                  () => _lines.add(
                    _DraftInvoiceLine(_text(widget.products.first['id'])),
                  ),
                ),
          icon: const Icon(Icons.add_circle_outline_rounded),
          label: const Text('Ajouter une ligne'),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _saving ? null : _pickDueDate,
          icon: const Icon(Icons.event_outlined),
          label: Text(
            'Échéance · ' +
                _dueAt.day.toString().padLeft(2, '0') +
                '/' +
                _dueAt.month.toString().padLeft(2, '0') +
                '/' +
                _dueAt.year.toString(),
          ),
        ),
        TextField(
          controller: _note,
          maxLength: 800,
          minLines: 2,
          maxLines: 4,
          decoration: const InputDecoration(labelText: 'Note pour le client'),
        ),
        const SizedBox(height: 10),
        const Text(
          'La facture est émise par votre Business. Aucun paiement n’est prélevé automatiquement.',
          style: TextStyle(color: WapiColors.muted),
        ),
        const SizedBox(height: 18),
        FilledButton(
          onPressed: _saving ? null : _save,
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(54)),
          child: Text(_saving ? 'Création…' : 'Émettre la facture'),
        ),
      ],
    ),
  );
}

class _DraftInvoiceLine {
  _DraftInvoiceLine(this.productId);
  String productId;
  final TextEditingController quantity = TextEditingController(text: '1');
}

class _BillingApi {
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

String _text(Object? value, [String fallback = '']) =>
    value is String && value.trim().isNotEmpty ? value.trim() : fallback;
int _number(Object? value) => value is num ? value.toInt() : 0;
Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
List<Map<String, dynamic>> _maps(Object? value) => (value as List? ?? const [])
    .whereType<Map>()
    .map(_map)
    .toList(growable: false);
String _money(int amount, String currency) =>
    amount.toString() + ' ' + currency;
String _errorText(Object error) => error is FirebaseFunctionsException
    ? error.message ?? 'L’action n’a pas pu être finalisée.'
    : 'L’action n’a pas pu être finalisée.';
