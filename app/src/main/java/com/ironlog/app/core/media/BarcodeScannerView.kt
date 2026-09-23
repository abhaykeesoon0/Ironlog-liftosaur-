package com.ironlog.app.core.media

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A live camera preview that reports the first product barcode it sees.
 *
 * Decoding happens on the phone: ML Kit's barcode model is bundled with the
 * app, so no frame ever leaves the device and the scanner works with no
 * signal. Only retail formats are enabled -- EAN and UPC -- because those are
 * what food carries, and enabling QR would have the scanner fire on a poster.
 *
 * The caller must already hold the camera permission; the rationale-first
 * request lives with the button that opens this, not in here.
 */
@Composable
fun BarcodeScannerView(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnBarcode by rememberUpdatedState(onBarcode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
        )
    }
    // Reported once per opening of the scanner: a code in view for a second
    // is thirty frames, and thirty lookups of the same product is a bug.
    val reported = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // A TextureView rather than a SurfaceView: inside a dialog a
                // surface is composited outside the window and can sit over
                // the controls drawn on top of it.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    decode(scanner, image) { code ->
                        if (reported.compareAndSet(false, true)) latestOnBarcode(code)
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/** Runs one frame through the scanner and closes it, found or not. */
@SuppressLint("UnsafeOptInUsageError")
private fun decode(scanner: BarcodeScanner, image: ImageProxy, onFound: (String) -> Unit) {
    val media = image.image
    if (media == null) {
        image.close()
        return
    }
    val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.length >= 8 } }?.let(onFound)
        }
        .addOnCompleteListener { image.close() }
}
