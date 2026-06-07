package com.prplegryn.moe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prplegryn.moe.ui.MoeApp
import com.prplegryn.moe.ui.MoeViewModel
import com.prplegryn.moe.ui.theme.MoeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoeTheme {
                val viewModel: MoeViewModel = viewModel()
                MoeApp(viewModel)
            }
        }
    }
}
