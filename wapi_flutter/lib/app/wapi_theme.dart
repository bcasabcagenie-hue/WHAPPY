import 'package:flutter/material.dart';

String wapiErrorText(Object? error, {required String fallback}) {
  if (error == null) return fallback;
  var value = error.toString().trim();
  value = value
      .replaceFirst(RegExp(r'^Bad state:\s*', caseSensitive: false), '')
      .replaceFirst(RegExp(r'^Exception:\s*', caseSensitive: false), '')
      .replaceAll(
        RegExp(
          r'\[(firebase|cloud_functions|firebase_storage)[^\]]*\]',
          caseSensitive: false,
        ),
        '',
      )
      .trim();
  final technical = value.toLowerCase();
  if (value.isEmpty ||
      technical.contains('firebase') ||
      technical.contains('platformexception') ||
      technical.contains('stack trace') ||
      technical.contains('permission-denied') ||
      technical.contains('unauthorized') ||
      technical.contains('not authorized') ||
      technical.contains('timeout') ||
      technical.contains('deadline-exceeded') ||
      technical.contains('socketexception') ||
      technical.contains('failed host lookup')) {
    return fallback;
  }
  return value.length > 220 ? fallback : value;
}

abstract final class WapiColors {
  static const blue = Color(0xFF008EE8);
  static const blueDark = Color(0xFF075FAF);
  static const blueSoft = Color(0xFFE8F5FD);
  static const navy = Color(0xFF062233);
  static const emerald = Color(0xFF00856A);
  static const ink = Color(0xFF17212B);
  static const muted = Color(0xFF667580);
  static const canvas = Color(0xFFF4F7F9);
  static const surfaceSoft = Color(0xFFF8FAFB);
  static const line = Color(0xFFDCE4E9);
}

abstract final class WapiTheme {
  static ThemeData light() {
    final baseScheme = ColorScheme.fromSeed(
      seedColor: WapiColors.blue,
      brightness: Brightness.light,
      surface: Colors.white,
    );
    final scheme = baseScheme.copyWith(
      primary: WapiColors.blue,
      onPrimary: Colors.white,
      primaryContainer: WapiColors.blueSoft,
      onPrimaryContainer: WapiColors.navy,
      secondary: WapiColors.emerald,
      onSecondary: Colors.white,
      surface: Colors.white,
      onSurface: WapiColors.ink,
      outline: const Color(0xFFB8C5CD),
      outlineVariant: WapiColors.line,
      error: const Color(0xFFB42318),
    );
    final baseText = Typography.material2021().black.apply(
      bodyColor: WapiColors.ink,
      displayColor: WapiColors.ink,
    );
    return ThemeData(
      useMaterial3: true,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      visualDensity: VisualDensity.standard,
      colorScheme: scheme,
      textTheme: baseText.copyWith(
        headlineSmall: baseText.headlineSmall?.copyWith(
          fontWeight: FontWeight.w800,
          letterSpacing: -.35,
        ),
        titleLarge: baseText.titleLarge?.copyWith(
          fontWeight: FontWeight.w800,
          letterSpacing: -.2,
        ),
        titleMedium: baseText.titleMedium?.copyWith(
          fontWeight: FontWeight.w700,
        ),
        bodyLarge: baseText.bodyLarge?.copyWith(height: 1.35),
        bodyMedium: baseText.bodyMedium?.copyWith(height: 1.35),
        labelLarge: baseText.labelLarge?.copyWith(fontWeight: FontWeight.w800),
      ),
      scaffoldBackgroundColor: WapiColors.canvas,
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: WapiColors.ink,
        elevation: 0,
        scrolledUnderElevation: 1,
        shadowColor: Color(0x18062233),
        surfaceTintColor: Colors.transparent,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: WapiColors.ink,
          fontSize: 19,
          fontWeight: FontWeight.w800,
          letterSpacing: -.2,
        ),
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: WapiColors.line),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 70,
        elevation: 0,
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        indicatorColor: WapiColors.blueSoft,
        indicatorShape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        iconTheme: WidgetStateProperty.resolveWith(
          (states) => IconThemeData(
            size: 23,
            color: states.contains(WidgetState.selected)
                ? WapiColors.blueDark
                : WapiColors.muted,
          ),
        ),
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => TextStyle(
            color: states.contains(WidgetState.selected)
                ? WapiColors.blueDark
                : WapiColors.muted,
            fontSize: 11,
            fontWeight: states.contains(WidgetState.selected)
                ? FontWeight.w800
                : FontWeight.w600,
          ),
        ),
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        showDragHandle: true,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: WapiColors.surfaceSoft,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 15,
        ),
        hintStyle: const TextStyle(color: Color(0xFF83919A)),
        labelStyle: const TextStyle(color: WapiColors.muted),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: WapiColors.line),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: WapiColors.line),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: WapiColors.blue, width: 1.5),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(44, 48),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 13),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(15),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w800),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(44, 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          side: const BorderSide(color: WapiColors.line),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(15),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w800),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: const Size.square(44),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: Colors.white,
        selectedColor: WapiColors.blueSoft,
        side: const BorderSide(color: WapiColors.line),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        labelStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
      dividerTheme: const DividerThemeData(
        color: WapiColors.line,
        thickness: 1,
        space: 1,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: WapiColors.blue,
        linearTrackColor: WapiColors.line,
        circularTrackColor: WapiColors.line,
      ),
      tooltipTheme: TooltipThemeData(
        waitDuration: const Duration(milliseconds: 450),
        showDuration: const Duration(seconds: 3),
        decoration: BoxDecoration(
          color: WapiColors.navy,
          borderRadius: BorderRadius.circular(10),
        ),
        textStyle: const TextStyle(color: Colors.white, fontSize: 12),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: WapiColors.navy,
        contentTextStyle: const TextStyle(color: Colors.white),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      ),
    );
  }
}
