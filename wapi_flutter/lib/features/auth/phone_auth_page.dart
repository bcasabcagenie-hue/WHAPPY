import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../../app/wapi_theme.dart';

class PhoneAuthPage extends StatefulWidget {
  const PhoneAuthPage({super.key});

  @override
  State<PhoneAuthPage> createState() => _PhoneAuthPageState();
}

class _PhoneAuthPageState extends State<PhoneAuthPage> {
  final _phone = TextEditingController(text: '+242');
  final _code = TextEditingController();
  String? _verificationId;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _requestCode() async {
    final number = _phone.text.replaceAll(RegExp(r'\s+'), '');
    if (!RegExp(r'^\+\d{8,16}$').hasMatch(number)) {
      setState(() => _error = 'Entrez un numéro international valide.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    await FirebaseAuth.instance.verifyPhoneNumber(
      phoneNumber: number,
      verificationCompleted: (credential) async {
        await FirebaseAuth.instance.signInWithCredential(credential);
      },
      verificationFailed: (exception) {
        if (mounted) {
          setState(() {
            _busy = false;
            _error = exception.message ?? 'Vérification impossible.';
          });
        }
      },
      codeSent: (id, _) {
        if (mounted) {
          setState(() {
            _busy = false;
            _verificationId = id;
          });
        }
      },
      codeAutoRetrievalTimeout: (id) {
        if (mounted) {
          setState(() {
            _busy = false;
            _verificationId = id;
          });
        }
      },
    );
  }

  Future<void> _confirmCode() async {
    final id = _verificationId;
    if (id == null || _code.text.trim().length != 6) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await FirebaseAuth.instance.signInWithCredential(
        PhoneAuthProvider.credential(
          verificationId: id,
          smsCode: _code.text.trim(),
        ),
      );
    } on FirebaseAuthException catch (error) {
      if (mounted) setState(() => _error = error.message ?? 'Code incorrect.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final codeStep = _verificationId != null;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 460),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          'WAPI',
                          style: TextStyle(
                            color: WapiColors.ink,
                            fontSize: 34,
                            fontWeight: FontWeight.w900,
                            letterSpacing: -1.4,
                          ),
                        ),
                        Padding(
                          padding: EdgeInsets.only(left: 4, bottom: 7),
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: WapiColors.blue,
                              shape: BoxShape.circle,
                            ),
                            child: SizedBox(width: 8, height: 8),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  Text(
                    codeStep ? 'Vérifiez votre numéro' : 'Bienvenue dans WAPI',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    codeStep
                        ? 'Entrez le code SMS à 6 chiffres.'
                        : 'Un numéro. Un compte. Vos échanges sur cet appareil.',
                    style: const TextStyle(color: WapiColors.muted),
                  ),
                  const SizedBox(height: 24),
                  if (!codeStep)
                    TextField(
                      controller: _phone,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                        labelText: 'Téléphone',
                        prefixIcon: Icon(Icons.phone_outlined),
                      ),
                    )
                  else
                    TextField(
                      controller: _code,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      decoration: const InputDecoration(
                        labelText: 'Code SMS',
                        prefixIcon: Icon(Icons.lock_outline),
                      ),
                    ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 12),
                      child: Text(
                        _error!,
                        style: const TextStyle(color: Colors.red),
                      ),
                    ),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: _busy
                        ? null
                        : codeStep
                        ? _confirmCode
                        : _requestCode,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: _busy
                          ? const SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : Text(codeStep ? 'Entrer dans WAPI' : 'Continuer'),
                    ),
                  ),
                  if (codeStep)
                    TextButton(
                      onPressed: _busy
                          ? null
                          : () => setState(() => _verificationId = null),
                      child: const Text('Modifier le numéro'),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
