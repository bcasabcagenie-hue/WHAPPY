import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Shared Flutter host for WAPI's native OpenGL tabletop renderer.
class WapiTabletop3D extends StatefulWidget {
  const WapiTabletop3D.strategy({
    super.key,
    required this.scene,
    required this.board,
    required this.selected,
    required this.legalTargets,
    required this.onSquare,
    required this.fallback,
  }) : positions = const [],
       activePlayer = 0,
       dieOne = 1,
       dieTwo = 1,
       rolling = false,
       selectablePawns = const {},
       onPawn = null;

  const WapiTabletop3D.ludo({
    super.key,
    required this.positions,
    required this.activePlayer,
    required this.dieOne,
    required this.dieTwo,
    required this.rolling,
    required this.selectablePawns,
    required this.onPawn,
    required this.fallback,
  }) : scene = 'ludo',
       board = const [],
       selected = -1,
       legalTargets = const {},
       onSquare = null;

  final String scene;
  final List<String> board;
  final int selected;
  final Set<int> legalTargets;
  final ValueChanged<int>? onSquare;
  final List<int> positions;
  final int activePlayer;
  final int dieOne;
  final int dieTwo;
  final bool rolling;
  final Set<int> selectablePawns;
  final ValueChanged<int>? onPawn;
  final Widget fallback;

  @override
  State<WapiTabletop3D> createState() => _WapiTabletop3DState();
}

class _WapiTabletop3DState extends State<WapiTabletop3D> {
  MethodChannel? _channel;

  Map<String, Object> get _state => {
    'scene': widget.scene,
    'board': widget.board,
    'selected': widget.selected,
    'legalTargets': widget.legalTargets.toList(growable: false),
    'positions': widget.positions,
    'activePlayer': widget.activePlayer,
    'dieOne': widget.dieOne.clamp(1, 6),
    'dieTwo': widget.dieTwo.clamp(1, 6),
    'rolling': widget.rolling,
    'selectablePawns': widget.selectablePawns.toList(growable: false),
  };

  @override
  void didUpdateWidget(covariant WapiTabletop3D oldWidget) {
    super.didUpdateWidget(oldWidget);
    _render();
  }

  Future<void> _render() async {
    await _channel?.invokeMethod<void>('render', _state);
  }

  Future<void> _message(MethodCall call) async {
    final value = call.arguments as int?;
    if (value == null) return;
    if (call.method == 'square') widget.onSquare?.call(value);
    if (call.method == 'pawn') widget.onPawn?.call(value);
  }

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform != TargetPlatform.android || kIsWeb) {
      return widget.fallback;
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(18),
      child: AndroidView(
        viewType: 'wapi/tabletop-3d',
        creationParams: _state,
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: (id) {
          _channel = MethodChannel('wapi/tabletop-3d/$id');
          _channel!.setMethodCallHandler(_message);
          _render();
        },
      ),
    );
  }

  @override
  void dispose() {
    _channel?.setMethodCallHandler(null);
    super.dispose();
  }
}
