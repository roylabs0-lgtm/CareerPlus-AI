package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CareerPlusViewModel
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    private val viewModel: CareerPlusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads component
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            // Silence initialization issues if play-services is missing on compiling JVM
        }

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}
