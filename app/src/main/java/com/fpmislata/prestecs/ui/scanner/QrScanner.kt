package com.fpmislata.prestecs.ui.scanner

import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fpmislata.prestecs.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors
import kotlinx.coroutines.awaitCancellation

/**
 * Back-camera viewfinder that reads QR codes. Needs the CAMERA permission:
 * wrap it in [CameraPermissionGate].
 *
 * [onScan] gets the trimmed text of each new code, on the main thread. The same
 * code is reported once while it stays in view (see [ScanDebouncer]). While
 * [enabled] is false, codes are ignored but the preview keeps running.
 */
@Composable
fun QrScanner(onScan: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScan by rememberUpdatedState(onScan)
    val currentEnabled by rememberUpdatedState(enabled)
    val debouncer = remember { ScanDebouncer() }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    KeepScreenOn()

    // Bound to the lifecycle owner: the camera stops when the screen is left
    // or the app goes to the background, and resumes when it comes back.
    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(
            analysisExecutor,
            MlKitAnalyzer(
                listOf(barcodeScanner),
                COORDINATE_SYSTEM_ORIGINAL,
                ContextCompat.getMainExecutor(context),
            ) { result ->
                val value = result.getValue(barcodeScanner)
                    ?.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                    ?: return@MlKitAnalyzer
                if (currentEnabled && debouncer.accept(value)) currentOnScan(value)
            },
        )

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            // No back camera on this device.
            Log.w(TAG, "Can't open the back camera", e)
            cameraFailed = true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Can't bind the camera", e)
            cameraFailed = true
        } finally {
            provider.unbind(preview, analysis)
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
            barcodeScanner.close()
            camera = null
            surfaceRequest = null
            torchOn = false
        }
    }

    val viewfinderDescription = stringResource(R.string.scanner_viewfinder_description)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .semantics { contentDescription = viewfinderDescription },
        contentAlignment = Alignment.Center,
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
        }

        if (cameraFailed) {
            Text(
                stringResource(R.string.scanner_camera_unavailable),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            // Aiming guide.
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(1f)
                    .border(3.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
            )
        }

        val activeCamera = camera
        if (activeCamera != null && activeCamera.cameraInfo.hasFlashUnit()) {
            FilledIconToggleButton(
                checked = torchOn,
                onCheckedChange = { on ->
                    torchOn = on
                    activeCamera.cameraControl.enableTorch(on)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off,
                    ),
                    contentDescription = stringResource(R.string.scanner_torch),
                )
            }
        }
    }
}

/** Keeps the screen on while composed: scanning many codes takes a while. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private const val TAG = "QrScanner"
