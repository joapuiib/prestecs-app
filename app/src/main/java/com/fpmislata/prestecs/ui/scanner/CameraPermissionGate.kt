package com.fpmislata.prestecs.ui.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fpmislata.prestecs.R

/**
 * Shows [content] once the CAMERA permission is granted.
 *
 * Asks for it as soon as the screen opens (the user came here to scan), but
 * only once per visit: if refused, it explains why it's needed and offers to
 * ask again, or to open the app settings when Android won't ask any more.
 * The grant is re-checked every time the screen resumes, never cached, so
 * granting it from the settings, or revoking it, takes effect on return.
 */
@Composable
fun CameraPermissionGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedThisVisit by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        granted = context.hasCameraPermission()
        onPauseOrDispose {}
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // After a refusal, no rationale means Android won't show the dialog
        // again ("don't ask again", or refused twice).
        permanentlyDenied = !ok &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    if (granted) {
        content()
        return
    }

    LaunchedEffect(Unit) {
        val needsRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
        if (!askedThisVisit && !needsRationale) {
            askedThisVisit = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.scanner_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Button(onClick = { context.openAppSettings() }) {
                Text(stringResource(R.string.scanner_permission_open_settings))
            }
        } else {
            Button(onClick = {
                askedThisVisit = true
                launcher.launch(Manifest.permission.CAMERA)
            }) {
                Text(stringResource(R.string.scanner_permission_allow))
            }
        }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
