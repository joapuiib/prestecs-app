package com.fpmislata.prestecs

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fpmislata.prestecs.ui.root.PrestecsRoot
import com.fpmislata.prestecs.ui.theme.PrestecsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Screens draw their own bottom bars down to the screen edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            PrestecsTheme {
                PrestecsRoot()
            }
        }
    }
}
