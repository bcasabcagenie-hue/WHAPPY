package com.whappy.chat

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val ScannerBlue = Color(0xFF0B9FE8)

@Composable
internal fun WapiQrScannerDialog(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    onPickImage: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentResult by rememberUpdatedState(onResult)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val scannerMotion = rememberInfiniteTransition(label = "wapi-qr-scan")
    val scanProgress by scannerMotion.animateFloat(
        initialValue = .10f,
        targetValue = .90f,
        animationSpec = infiniteRepeatable(tween(1_650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scan-line",
    )

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analyzer = WapiQrAnalyzer { payload ->
            mainExecutor.execute {
                WhappySounds.haptic(context, strong = true)
                currentResult(payload)
            }
        }
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, analyzer) }
            runCatching {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, mainExecutor)
        onDispose {
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            analyzerExecutor.shutdownNow()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(46.dp).background(Color.Black.copy(alpha = .58f), CircleShape)) {
                    Icon(Icons.Rounded.Close, "Fermer", tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Scanner WAPI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("Ajout instantané et sécurisé", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                }
                IconButton(
                    onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    },
                    modifier = Modifier.size(46.dp).background(if (torchEnabled) ScannerBlue else Color.Black.copy(alpha = .58f), CircleShape),
                ) {
                    Icon(Icons.Rounded.FlashlightOn, "Lampe", tint = Color.White)
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val frameSize = (maxWidth * .76f).coerceAtMost(330.dp)
                Box(
                    Modifier.size(frameSize).border(1.dp, Color.White.copy(alpha = .32f), RoundedCornerShape(30.dp)),
                ) {
                    ScannerCorners(scanProgress)
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Cadrez le code — aucune photo n’est conservée", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Détection automatique sur l’appareil", color = Color.White.copy(alpha = .70f), fontSize = 11.sp)
                Button(
                    onClick = onPickImage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF102B3A)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Photo, null)
                    Text("  Choisir dans la galerie")
                }
            }
        }
    }
}

@Composable
private fun ScannerCorners(progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val length = 46.dp.toPx()
        val inset = 2.dp.toPx()
        val stroke = 5.dp.toPx()
        val right = size.width - inset
        val bottom = size.height - inset
        val blue = ScannerBlue
        drawLine(blue, Offset(inset, length), Offset(inset, inset), stroke, StrokeCap.Round)
        drawLine(blue, Offset(inset, inset), Offset(length, inset), stroke, StrokeCap.Round)
        drawLine(blue, Offset(right - length, inset), Offset(right, inset), stroke, StrokeCap.Round)
        drawLine(blue, Offset(right, inset), Offset(right, length), stroke, StrokeCap.Round)
        drawLine(blue, Offset(inset, bottom - length), Offset(inset, bottom), stroke, StrokeCap.Round)
        drawLine(blue, Offset(inset, bottom), Offset(length, bottom), stroke, StrokeCap.Round)
        drawLine(blue, Offset(right - length, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
        drawLine(blue, Offset(right, bottom - length), Offset(right, bottom), stroke, StrokeCap.Round)
        val lineY = size.height * progress
        drawLine(blue.copy(alpha = .24f), Offset(28.dp.toPx(), lineY - 7.dp.toPx()), Offset(size.width - 28.dp.toPx(), lineY - 7.dp.toPx()), 7.dp.toPx(), StrokeCap.Round)
        drawLine(blue, Offset(22.dp.toPx(), lineY), Offset(size.width - 22.dp.toPx(), lineY), 2.4.dp.toPx(), StrokeCap.Round)
    }
}

private class WapiQrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val delivered = AtomicBoolean(false)
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    override fun analyze(image: ImageProxy) {
        if (delivered.get()) {
            image.close()
            return
        }
        try {
            val frame = image.luminanceFrame() ?: return
            val source = PlanarYUVLuminanceSource(frame.bytes, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
            val payload = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text?.trim().orEmpty()
            if (payload.isNotBlank() && delivered.compareAndSet(false, true)) onDecoded(payload)
        } catch (_: Exception) {
            // A frame without QR is normal; CameraX submits the next one immediately.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

private data class LuminanceFrame(val bytes: ByteArray, val width: Int, val height: Int)

@SuppressLint("UnsafeOptInUsageError")
private fun ImageProxy.luminanceFrame(): LuminanceFrame? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val source = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * plane.rowStride + x * plane.pixelStride
            if (index < buffer.limit()) source[y * width + x] = buffer.get(index)
        }
    }
    return when (imageInfo.rotationDegrees) {
        90 -> {
            val rotated = ByteArray(source.size)
            for (y in 0 until height) for (x in 0 until width) rotated[x * height + (height - y - 1)] = source[y * width + x]
            LuminanceFrame(rotated, height, width)
        }
        180 -> LuminanceFrame(ByteArray(source.size) { source[source.lastIndex - it] }, width, height)
        270 -> {
            val rotated = ByteArray(source.size)
            for (y in 0 until height) for (x in 0 until width) rotated[(width - x - 1) * height + y] = source[y * width + x]
            LuminanceFrame(rotated, height, width)
        }
        else -> LuminanceFrame(source, width, height)
    }
}
