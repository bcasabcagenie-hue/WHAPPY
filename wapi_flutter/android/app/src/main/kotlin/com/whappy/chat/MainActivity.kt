package com.whappy.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.media.AudioManager
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // Hardware volume keys control the pool/game mix while a table is
        // open, instead of changing the ringtone volume.
        volumeControlStream = AudioManager.STREAM_MUSIC
        flutterEngine.platformViewsController.registry.registerViewFactory(
            "wapi/pool-3d",
            WapiPool3DViewFactory(flutterEngine.dartExecutor.binaryMessenger),
        )
        flutterEngine.platformViewsController.registry.registerViewFactory(
            "wapi/tabletop-3d",
            WapiTabletop3DViewFactory(flutterEngine.dartExecutor.binaryMessenger),
        )
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "wapi/documents")
            .setMethodCallHandler { call, result ->
                if (call.method != "openFile") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                val path = call.argument<String>("path").orEmpty()
                val mimeType = call.argument<String>("mimeType")
                    ?.takeIf { it.isNotBlank() }
                    ?: "application/octet-stream"
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    result.error("missing_file", "Le document WAPI est introuvable.", null)
                    return@setMethodCallHandler
                }
                try {
                    val uri = FileProvider.getUriForFile(
                        this,
                        "$packageName.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri("Document WAPI", uri)
                    }
                    startActivity(Intent.createChooser(intent, "Ouvrir le document WAPI"))
                    result.success(true)
                } catch (_: ActivityNotFoundException) {
                    result.success(false)
                } catch (error: Exception) {
                    result.error("open_failed", error.message, null)
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "wapi/call-ui")
            .setMethodCallHandler { call, result ->
                if (call.method != "enterPictureInPicture") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    result.success(false)
                    return@setMethodCallHandler
                }
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(9, 16))
                        .build()
                    result.success(enterPictureInPictureMode(params))
                } catch (_: Exception) {
                    result.success(false)
                }
            }
    }
}
