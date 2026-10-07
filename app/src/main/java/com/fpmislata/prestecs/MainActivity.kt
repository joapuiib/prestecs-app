package com.fpmislata.prestecs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fpmislata.prestecs.ui.navigation.PrestecsNavHost
import com.fpmislata.prestecs.ui.theme.PrestecsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrestecsTheme {
                PrestecsNavHost()
            }
        }
    }
}
