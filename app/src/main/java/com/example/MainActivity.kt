package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.kpzi.KpziContainer
import com.example.kpzi.KpziViewModel
import com.example.kpzi.notifications.KpziNotificationHelper
import com.example.kpzi.ui.KpziApp

class MainActivity : ComponentActivity() {

    private val container by lazy { KpziContainer.getInstance(applicationContext) }
    private val viewModel: KpziViewModel by viewModels { KpziViewModel.Factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            KpziApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val targetTab = intent?.getStringExtra(KpziNotificationHelper.EXTRA_TARGET_TAB)
        if (!targetTab.isNullOrBlank()) {
            viewModel.requestNavigateTab(targetTab)
        }
        val data = intent?.data ?: return
        if (data.scheme.equals("nxm", ignoreCase = true)) {
            viewModel.handleNxmDeepLink(data)
        }
    }
}
