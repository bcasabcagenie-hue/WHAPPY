import 'package:flutter/material.dart';

abstract final class WapiColors {
  static const blue = Color(0xFF0094F0);
  static const blueDark = Color(0xFF0066CF);
  static const blueSoft = Color(0xFFE7F5FF);
  static const ink = Color(0xFF17191C);
  static const muted = Color(0xFF69717A);
  static const canvas = Color(0xFFF7F8FA);
  static const line = Color(0xFFE4E8EC);
}

abstract final class WapiTheme {
  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: WapiColors.blue,
      brightness: Brightness.light,
      surface: Colors.white,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme.copyWith(
        primary: WapiColors.blue,
        onPrimary: Colors.white,
        surface: Colors.white,
        onSurface: WapiColors.ink,
        outlineVariant: WapiColors.line,
      ),
      scaffoldBackgroundColor: WapiColors.canvas,
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: WapiColors.ink,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        surfaceTintColor: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: WapiColors.line),
        ),
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: WapiColors.line),
        ),
      ),
    );
  }
}
